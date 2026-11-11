/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.internal.bench;

import java.util.ArrayDeque;
import java.util.Queue;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.StreamState;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;

/**
 * Plays the network side of an http client binding: accepts each connection the binding opens, grants it
 * credit, hands the bytes it sends to a protocol {@link Session} and writes back what the session replies,
 * within the credit the binding grants. It does not allocate in steady state.
 */
abstract class NetPeer implements BindingHandler
{
    interface Session
    {
        void onOpen(
            NetStream stream);

        void onBytes(
            NetStream stream,
            DirectBufferEx buffer,
            int offset,
            int limit);
    }

    static final int WINDOW = 64 * 1024;

    private static final int FLAGS_INIT_FIN = 0x03;
    private static final int WRITE_BUFFER_CAPACITY = 64 * 1024;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final WindowFW windowRO = new WindowFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[WRITE_BUFFER_CAPACITY]);
    private final Queue<NetStream> idleStreams = new ArrayDeque<>();
    private final int writerCapacity;

    private int opened;
    private int received;

    NetPeer(
        int writerCapacity)
    {
        this.writerCapacity = writerCapacity;
    }

    final int opened()
    {
        return opened;
    }

    final int received()
    {
        return received;
    }

    final void countRequest()
    {
        received++;
    }

    abstract Session newSession();

    @Override
    public final MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        NetStream stream = idleStreams.poll();

        if (stream == null)
        {
            stream = new NetStream(newSession());
        }

        opened++;

        return stream.reset(sender);
    }

    final class NetStream implements WireWriter.Sink
    {
        private final MessageConsumer consumer = this::onMessage;
        private final Session session;

        private WireWriter writer;
        private MessageConsumer sender;
        private int state;
        private long originId;
        private long routedId;
        private long initialId;
        private long replyId;
        private long initialSeq;
        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private NetStream(
            Session session)
        {
            this.session = session;
        }

        void write(
            DirectBufferEx source)
        {
            write(source, 0, source.capacity());
        }

        void write(
            DirectBufferEx source,
            int offset,
            int length)
        {
            if (writer == null)
            {
                writer = new WireWriter(writerCapacity);
                writer.sink(this);
            }

            writer.write(source, offset, length);
        }

        @Override
        public int credit()
        {
            return (int) (replyAck + replyMax - replySeq) - replyPad;
        }

        @Override
        public void data(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(0)
                .flags(FLAGS_INIT_FIN)
                .budgetId(0L)
                .reserved(length + replyPad)
                .payload(buffer, offset, length)
                .build();

            replySeq += data.reserved();

            sender.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
        }

        private MessageConsumer reset(
            MessageConsumer sender)
        {
            this.sender = sender;
            this.state = 0;
            this.initialSeq = 0L;
            this.replySeq = 0L;
            this.replyAck = 0L;
            this.replyMax = 0;
            this.replyPad = 0;

            if (writer != null)
            {
                writer.sink(this);
            }

            return consumer;
        }

        private void onMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                this.originId = begin.originId();
                this.routedId = begin.routedId();
                this.initialId = begin.streamId();
                this.replyId = initialId & ~1L;
                state = StreamState.openInitial(state);
                doInitialWindow();
                doReplyBegin();
                session.onOpen(this);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                initialSeq += data.reserved();
                if (data.length() > 0)
                {
                    session.onBytes(this, data.payload().buffer(), data.payload().offset(), data.payload().limit());
                }
                doInitialWindow();
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                replyAck = window.acknowledge();
                replyMax = window.maximum();
                replyPad = window.padding();
                state = StreamState.openReply(state);
                if (writer != null)
                {
                    writer.drain();
                }
                break;
            case EndFW.TYPE_ID:
                doReplyEnd();
                closeInitial();
                break;
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                closeStream();
                break;
            default:
                break;
            }
        }

        private void doInitialWindow()
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialSeq)
                .state(state)
                .maximum(WINDOW)
                .budgetId(0L)
                .padding(0)
                .build();

            sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doReplyBegin()
        {
            state = StreamState.openingReply(state);

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(0)
                .affinity(0L)
                .build();

            sender.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        private void doReplyEnd()
        {
            state = StreamState.closeReply(state);

            final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(0)
                .build();

            sender.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
        }

        private void closeInitial()
        {
            final boolean closed = StreamState.closed(state);

            state = StreamState.closeInitial(state);

            release(closed);
        }

        private void closeStream()
        {
            final boolean closed = StreamState.closed(state);

            state = StreamState.closeReply(StreamState.closeInitial(state));

            release(closed);
        }

        private void release(
            boolean closed)
        {
            if (!closed && StreamState.closed(state))
            {
                idleStreams.offer(this);
            }
        }
    }
}
