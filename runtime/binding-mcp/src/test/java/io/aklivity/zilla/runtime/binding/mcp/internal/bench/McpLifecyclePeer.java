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
import io.aklivity.zilla.runtime.engine.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.test.EngineBench;

/**
 * Accepts an {@code mcp} lifecycle stream, replies with the session identifier and leaves the stream open
 * until the binding ends it, as a backend that holds a session does. It does not allocate in steady state:
 * stream state is pooled and frames are built into a reused buffer.
 */
final class McpLifecyclePeer implements BindingHandler
{
    private static final int WRITE_BUFFER_CAPACITY = 4 * 1024;

    private final BeginFW beginRO = new BeginFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[WRITE_BUFFER_CAPACITY]);
    private final Queue<LifecycleStream> idleStreams = new ArrayDeque<>();
    private final DirectBufferEx beginEx;

    McpLifecyclePeer(
        byte[] beginEx)
    {
        this.beginEx = new UnsafeBufferEx(beginEx);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        LifecycleStream stream = idleStreams.poll();

        if (stream == null)
        {
            stream = new LifecycleStream();
        }

        return stream.reset(sender);
    }

    private final class LifecycleStream
    {
        private final MessageConsumer consumer = this::onMessage;

        private MessageConsumer sender;
        private int state;
        private long originId;
        private long routedId;
        private long initialId;
        private long replyId;

        private MessageConsumer reset(
            MessageConsumer sender)
        {
            this.sender = sender;
            this.state = 0;
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
                doReplyFlush();
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
                .sequence(0L)
                .acknowledge(0L)
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
                .sequence(0L)
                .acknowledge(0L)
                .state(state)
                .maximum(0)
                .affinity(0L)
                .extension(beginEx, 0, beginEx.capacity())
                .build();

            sender.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        private void doReplyFlush()
        {
            final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(0L)
                .acknowledge(0L)
                .state(state)
                .maximum(0)
                .budgetId(0L)
                .reserved(0)
                .build();

            sender.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
        }

        private void doReplyEnd()
        {
            state = StreamState.closeReply(state);

            final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(0L)
                .acknowledge(0L)
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
