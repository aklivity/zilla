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
package io.aklivity.zilla.runtime.binding.tcp.internal.bench;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import io.aklivity.zilla.runtime.binding.tcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.StreamState;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.test.EngineBench.FrameCounter;

/**
 * Application-side peer of a {@code tcp} server binding, driven from the {@code EngineBench} poll loop.
 * <p>
 * Every accepted connection opens an application stream; the peer begins the reply at once, grants request
 * credit as it consumes bytes, and then depending on the direction either discards the bytes
 * ({@code INBOUND}), pushes the bytes it is asked to with {@link #source(long)} within the reply credit it is
 * granted ({@code OUTBOUND}), or returns every byte it receives within the reply credit ({@code ECHO}).
 * The reply ends when the request ends. Stream state is pooled and frames are built into a reused buffer, so
 * the peer does not allocate in steady state.
 */
final class TcpBenchPeer implements BindingHandler
{
    private static final int WRITE_BUFFER_CAPACITY = 128 * 1024;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final WindowFW windowRO = new WindowFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[WRITE_BUFFER_CAPACITY]);
    private final Queue<PeerStream> idleStreams = new ArrayDeque<>();
    private final List<PeerStream> activeStreams = new ArrayList<>();
    private final FrameCounter counter = new FrameCounter();
    private final TcpBenchDirection direction;
    private final int window;
    private final DirectBufferEx sourcePayload;

    TcpBenchPeer(
        TcpBenchDirection direction,
        int window,
        int chunk)
    {
        final byte[] payload = new byte[chunk];
        new Random(0L).nextBytes(payload);

        this.direction = direction;
        this.window = window;
        this.sourcePayload = new UnsafeBufferEx(payload);
    }

    FrameCounter counter()
    {
        return counter;
    }

    void source(
        long bytes)
    {
        for (int index = 0; index < activeStreams.size(); index++)
        {
            activeStreams.get(index).source(bytes);
        }
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        PeerStream stream = idleStreams.poll();

        if (stream == null)
        {
            stream = new PeerStream();
        }

        return stream.reset(sender);
    }

    private final class PeerStream
    {
        private final MessageConsumer consumer = this::onMessage;
        private final MutableDirectBufferEx echoBuffer =
            direction == TcpBenchDirection.ECHO ? new UnsafeBufferEx(new byte[window]) : null;

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
        private int echoOffset;
        private int echoLimit;
        private long sourceRemaining;

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
            this.echoOffset = 0;
            this.echoLimit = 0;
            this.sourceRemaining = 0L;
            return consumer;
        }

        private void source(
            long bytes)
        {
            sourceRemaining += bytes;
            doSource();
        }

        private void onMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            counter.accept(msgTypeId, buffer, index, length);

            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                this.originId = begin.originId();
                this.routedId = begin.routedId();
                this.initialId = begin.streamId();
                this.replyId = initialId & ~1L;
                state = StreamState.openInitial(state);
                activeStreams.add(this);
                doReplyBegin();
                doInitialWindow();
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                initialSeq += data.reserved();
                onData(data);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW windowFW = windowRO.wrap(buffer, index, index + length);
                replyAck = windowFW.acknowledge();
                replyMax = windowFW.maximum();
                replyPad = windowFW.padding();
                state = StreamState.openReply(state);
                doSource();
                doEcho();
                doReplyEndIfInitialClosed();
                break;
            case EndFW.TYPE_ID:
                closeInitial();
                doEcho();
                doReplyEnd();
                break;
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                closeStream();
                break;
            default:
                break;
            }
        }

        private void onData(
            DataFW data)
        {
            if (direction == TcpBenchDirection.ECHO)
            {
                final int length = Math.max(data.length(), 0);

                if (echoLimit + length > echoBuffer.capacity())
                {
                    echoBuffer.putBytes(0, echoBuffer, echoOffset, echoLimit - echoOffset);
                    echoLimit -= echoOffset;
                    echoOffset = 0;
                }

                echoBuffer.putBytes(echoLimit, data.buffer(), data.payload().offset(), length);
                echoLimit += length;

                doEcho();
            }
            else
            {
                doInitialWindow();
            }
        }

        private void doEcho()
        {
            if (direction == TcpBenchDirection.ECHO && StreamState.replyOpened(state))
            {
                final int pending = echoLimit - echoOffset;
                final int length = Math.min(pending, replyCredit() - replyPad);

                if (length > 0)
                {
                    doReplyData(echoBuffer, echoOffset, length);
                    echoOffset += length;

                    if (echoOffset == echoLimit)
                    {
                        echoOffset = 0;
                        echoLimit = 0;
                    }

                    doInitialWindow();
                    doEcho();
                }
            }
        }

        private void doSource()
        {
            if (direction == TcpBenchDirection.OUTBOUND && StreamState.replyOpened(state))
            {
                final int length = (int) Math.min(Math.min(sourceRemaining, sourcePayload.capacity()), replyCredit() - replyPad);

                if (length > 0)
                {
                    doReplyData(sourcePayload, 0, length);
                    sourceRemaining -= length;
                    doSource();
                }
            }
        }

        private int replyCredit()
        {
            return (int) (replyAck + replyMax - replySeq);
        }

        private void doInitialWindow()
        {
            final long acknowledge = initialSeq - (echoLimit - echoOffset);

            final WindowFW windowFW = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(acknowledge)
                .state(state)
                .maximum(window)
                .budgetId(0L)
                .padding(0)
                .build();

            sender.accept(windowFW.typeId(), windowFW.buffer(), windowFW.offset(), windowFW.sizeof());
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

        private void doReplyData(
            DirectBufferEx payload,
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
                .budgetId(0L)
                .reserved(length + replyPad)
                .payload(payload, offset, length)
                .build();

            replySeq += data.reserved();

            sender.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
        }

        private void doReplyEndIfInitialClosed()
        {
            if (StreamState.initialClosed(state))
            {
                doReplyEnd();
            }
        }

        private void doReplyEnd()
        {
            if (echoLimit == echoOffset && !StreamState.replyClosed(state))
            {
                final boolean closed = StreamState.closed(state);

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

                release(closed);
            }
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
                activeStreams.remove(this);
                idleStreams.offer(this);
            }
        }
    }
}
