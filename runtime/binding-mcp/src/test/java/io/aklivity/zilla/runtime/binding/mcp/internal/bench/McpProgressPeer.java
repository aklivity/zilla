/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.mcp.internal.bench;

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
import io.aklivity.zilla.runtime.engine.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.test.EngineBench;

/**
 * Accepts an {@code mcp} {@code tools/call} stream and replies with a resumable flush and a progress flush
 * before the result, as a backend that reports progress does, then ends the reply. The result is sent within
 * the reply credit it is granted, in fragments of {@code fragmentLength} bytes when that is not zero. It does
 * not allocate in steady state: stream state is pooled and frames are built into a reused buffer.
 */
final class McpProgressPeer implements BindingHandler
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final int WRITE_BUFFER_CAPACITY = 64 * 1024;

    private final BeginFW beginRO = new BeginFW();
    private final WindowFW windowRO = new WindowFW();
    private final DataFW dataRO = new DataFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[WRITE_BUFFER_CAPACITY]);
    private final Queue<ProgressStream> idleStreams = new ArrayDeque<>();
    private final DirectBufferEx resumableEx;
    private final DirectBufferEx progressEx;
    private final DirectBufferEx result;
    private final int fragmentLength;

    McpProgressPeer(
        byte[] resumableEx,
        byte[] progressEx,
        byte[] result,
        int fragmentLength)
    {
        this.resumableEx = new UnsafeBufferEx(resumableEx);
        this.progressEx = new UnsafeBufferEx(progressEx);
        this.result = new UnsafeBufferEx(result);
        this.fragmentLength = fragmentLength;
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        ProgressStream stream = idleStreams.poll();

        if (stream == null)
        {
            stream = new ProgressStream();
        }

        return stream.reset(sender);
    }

    private final class ProgressStream
    {
        private final MessageConsumer consumer = this::onMessage;

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
        private boolean replying;
        private int offset;

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
            this.replying = false;
            this.offset = 0;
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
                doReplyFlush(resumableEx);
                doReplyFlush(progressEx);
                replying = true;
                break;
            case DataFW.TYPE_ID:
                initialSeq += dataRO.wrap(buffer, index, index + length).reserved();
                doInitialWindow();
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                replyAck = window.acknowledge();
                replyMax = window.maximum();
                replyPad = window.padding();
                state = StreamState.openReply(state);
                if (replying)
                {
                    doReply();
                }
                break;
            case EndFW.TYPE_ID:
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

        private void doReply()
        {
            while (replying && replyCredit() > replyPad)
            {
                final int fragment = fragmentLength != 0 ? fragmentLength : result.capacity();
                final int length = Math.min(Math.min(result.capacity() - offset, fragment), replyCredit() - replyPad);

                doReplyData(length);

                offset += length;

                if (offset == result.capacity())
                {
                    replying = false;
                    doReplyEnd();
                }
            }
        }

        private int replyCredit()
        {
            return (int) (replyAck + replyMax - replySeq);
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
                .maximum(EngineBench.DEFAULT_WINDOW)
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

        private void doReplyFlush(
            DirectBufferEx extension)
        {
            final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(0)
                .budgetId(0L)
                .reserved(0)
                .extension(extension, 0, extension.capacity())
                .build();

            sender.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
        }

        private void doReplyData(
            int length)
        {
            final int first = offset == 0 ? FLAGS_INIT : 0;
            final int last = offset + length == result.capacity() ? FLAGS_FIN : 0;

            final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(0)
                .flags(first | last)
                .budgetId(0L)
                .reserved(length + replyPad)
                .payload(result, offset, length)
                .build();

            replySeq += data.reserved();

            sender.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
        }

        private void doReplyEnd()
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

        private void closeInitial()
        {
            final boolean closed = StreamState.closed(state);

            state = StreamState.closeInitial(state);

            release(closed);
        }

        private void closeStream()
        {
            final boolean closed = StreamState.closed(state);

            replying = false;
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
