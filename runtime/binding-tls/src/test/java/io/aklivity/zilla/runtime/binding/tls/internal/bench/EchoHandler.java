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
package io.aklivity.zilla.runtime.binding.tls.internal.bench;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Function;

import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.StreamState;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.test.EngineBench;

/**
 * Accepts every stream routed to an external binding and sends back whatever its {@link Endpoint} produces, within
 * the reply credit it is granted. Stream state is pooled and frames are built into a reused buffer.
 */
final class EchoHandler implements BindingHandler
{
    interface Sink
    {
        void write(
            DirectBufferEx buffer,
            int offset,
            int length);

        void end();
    }

    interface Endpoint
    {
        void onData(
            DirectBufferEx buffer,
            int offset,
            int length) throws Exception;

        void onEnd() throws Exception;

        void reset();
    }

    private static final DirectBufferEx EMPTY_EXTENSION = new UnsafeBufferEx(new byte[0]);
    private static final int FLAGS_INIT_FIN = 0x03;
    private static final int WRITE_BUFFER_CAPACITY = 128 * 1024;
    private static final int PENDING_CAPACITY = 256 * 1024;
    private static final int INITIAL_WINDOW = EngineBench.DEFAULT_WINDOW;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final WindowFW windowRO = new WindowFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[WRITE_BUFFER_CAPACITY]);
    private final Queue<EchoStream> idleStreams = new ArrayDeque<>();
    private final Queue<MutableDirectBufferEx> idleBuffers = new ArrayDeque<>();
    private final Function<Sink, Endpoint> endpoints;
    private final DirectBufferEx replyBeginEx;

    EchoHandler(
        Function<Sink, Endpoint> endpoints)
    {
        this(EMPTY_EXTENSION, endpoints);
    }

    EchoHandler(
        DirectBufferEx replyBeginEx,
        Function<Sink, Endpoint> endpoints)
    {
        this.replyBeginEx = replyBeginEx;
        this.endpoints = endpoints;
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        EchoStream stream = idleStreams.poll();

        if (stream == null)
        {
            stream = new EchoStream();
        }

        return stream.reset(sender);
    }

    private final class EchoStream implements Sink
    {
        private final MessageConsumer consumer = this::onMessage;
        private final Endpoint endpoint = endpoints.apply(this);

        private MutableDirectBufferEx pending;
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
        private int pendingOffset;
        private int pendingLimit;
        private boolean ended;

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
            this.pendingOffset = 0;
            this.pendingLimit = 0;
            this.ended = false;
            this.endpoint.reset();
            return consumer;
        }

        @Override
        public void write(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (pending == null)
            {
                pending = idleBuffers.poll();

                if (pending == null)
                {
                    pending = new UnsafeBufferEx(new byte[PENDING_CAPACITY]);
                }
            }

            pending.putBytes(pendingLimit, buffer, offset, length);
            pendingLimit += length;
        }

        @Override
        public void end()
        {
            ended = true;
            flush();
        }

        private void onMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            final boolean closed = StreamState.closed(state);

            try
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
                    break;
                case DataFW.TYPE_ID:
                    final DataFW data = dataRO.wrap(buffer, index, index + length);
                    initialSeq += data.reserved();
                    endpoint.onData(data.payload().buffer(), data.payload().offset(), data.payload().sizeof());
                    doInitialWindow();
                    flush();
                    break;
                case WindowFW.TYPE_ID:
                    final WindowFW window = windowRO.wrap(buffer, index, index + length);
                    replyAck = window.acknowledge();
                    replyMax = window.maximum();
                    replyPad = window.padding();
                    state = StreamState.openReply(state);
                    flush();
                    break;
                case EndFW.TYPE_ID:
                    state = StreamState.closeInitial(state);
                    endpoint.onEnd();
                    end();
                    break;
                case AbortFW.TYPE_ID:
                case ResetFW.TYPE_ID:
                    state = StreamState.closeReply(StreamState.closeInitial(state));
                    break;
                default:
                    break;
                }
            }
            catch (Exception ex)
            {
                throw new IllegalStateException(ex);
            }

            release(closed);
        }

        private void flush()
        {
            while (pendingOffset < pendingLimit && replyCredit() > replyPad)
            {
                final int length = Math.min(pendingLimit - pendingOffset, replyCredit() - replyPad);
                doReplyData(length);
                pendingOffset += length;
            }

            if (pendingOffset == pendingLimit)
            {
                pendingOffset = 0;
                pendingLimit = 0;

                if (pending != null)
                {
                    idleBuffers.offer(pending);
                    pending = null;
                }

                if (ended && !StreamState.replyClosed(state))
                {
                    doReplyEnd();
                }
            }
        }

        private int replyCredit()
        {
            return (int) (replyAck + replyMax - replySeq);
        }

        private void release(
            boolean closed)
        {
            if (!closed && StreamState.closed(state))
            {
                idleStreams.offer(this);
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
                .maximum(INITIAL_WINDOW)
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
                .extension(replyBeginEx, 0, replyBeginEx.capacity())
                .build();

            sender.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        private void doReplyData(
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
                .payload(pending, pendingOffset, length)
                .build();

            replySeq += data.reserved();

            sender.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
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
    }
}
