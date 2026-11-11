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

import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
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
 * Accepts an {@code mcp} {@code tools/call} stream and replies once the whole request has arrived, as counted by
 * the content length of its begin extension, without waiting for the request to end. The reply is its begin,
 * a resumable flush and a progress flush when the backend reports progress, the result and its end. The result
 * is sent within the reply credit it is granted, in fragments of {@code fragmentLength} bytes when that is not
 * zero. A {@code null} result withholds the reply so the request stays in flight. It does not allocate in
 * steady state: stream state is pooled and frames are built into a reused buffer.
 */
final class McpToolsCallPeer implements BindingHandler
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final int WRITE_BUFFER_CAPACITY = 64 * 1024;

    private final BeginFW beginRO = new BeginFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final WindowFW windowRO = new WindowFW();
    private final DataFW dataRO = new DataFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[WRITE_BUFFER_CAPACITY]);
    private final Queue<CallStream> idleStreams = new ArrayDeque<>();
    private final DirectBufferEx beginEx;
    private final DirectBufferEx resumableEx;
    private final DirectBufferEx progressEx;
    private final DirectBufferEx result;
    private final int fragmentLength;

    McpToolsCallPeer(
        byte[] beginEx,
        byte[] resumableEx,
        byte[] progressEx,
        byte[] result,
        int fragmentLength)
    {
        this.beginEx = new UnsafeBufferEx(beginEx);
        this.resumableEx = resumableEx != null ? new UnsafeBufferEx(resumableEx) : null;
        this.progressEx = progressEx != null ? new UnsafeBufferEx(progressEx) : null;
        this.result = result != null ? new UnsafeBufferEx(result) : null;
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
        CallStream stream = idleStreams.poll();

        if (stream == null)
        {
            stream = new CallStream();
        }

        return stream.reset(sender);
    }

    private final class CallStream
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
        private int requestLength;
        private int requestReceived;
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
            this.requestLength = 0;
            this.requestReceived = 0;
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
                requestLength = mcpBeginExRO.wrap(begin.extension().buffer(), begin.extension().offset(),
                    begin.extension().limit()).toolsCall().contentLength();
                doReplyWhenRequestReceived();
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                initialSeq += data.reserved();
                requestReceived += Math.max(data.length(), 0);
                doInitialWindow();
                doReplyWhenRequestReceived();
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

        private void doReplyWhenRequestReceived()
        {
            if (result != null && requestReceived >= requestLength && !StreamState.replyOpening(state))
            {
                doReplyBegin();

                if (resumableEx != null)
                {
                    doReplyFlush(resumableEx);
                    doReplyFlush(progressEx);
                }

                replying = true;
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
                .extension(beginEx, 0, beginEx.capacity())
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
