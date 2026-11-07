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
package io.aklivity.zilla.runtime.engine.test;

import java.util.ArrayDeque;
import java.util.Queue;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.test.EngineBench.FrameCounter;

/**
 * Accepts streams routed to an external binding and reflects every request data frame back as a reply data
 * frame, so data flows in both directions at once for as long as the stream is open.
 * <p>
 * It never buffers. Request credit is granted only as far as the reply credit it has been granted, and the
 * request acknowledge follows each frame it reflects, so a frame it receives always fits the reply credit and
 * a slow reader of the reply slows the writer of the request. Like {@link Responder}, it does not allocate in
 * steady state: stream state is pooled and frames are built into a reused buffer.
 */
public final class Reflector implements BindingHandler
{
    private static final int WRITE_BUFFER_CAPACITY = 64 * 1024;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final WindowFW windowRO = new WindowFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[WRITE_BUFFER_CAPACITY]);
    private final Queue<ReflectorStream> idleStreams = new ArrayDeque<>();
    private final FrameCounter counter = new FrameCounter();

    public FrameCounter counter()
    {
        return counter;
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        ReflectorStream stream = idleStreams.poll();

        if (stream == null)
        {
            stream = new ReflectorStream();
        }

        return stream.reset(sender);
    }

    private final class ReflectorStream
    {
        private static final int INITIAL_CLOSED = 1;
        private static final int REPLY_CLOSED = 2;

        private final MessageConsumer consumer = this::onMessage;

        private MessageConsumer sender;
        private int state;
        private long originId;
        private long routedId;
        private long initialId;
        private long replyId;
        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private MessageConsumer reset(
            MessageConsumer sender)
        {
            this.sender = sender;
            this.state = 0;
            this.initialSeq = 0L;
            this.initialAck = 0L;
            this.initialMax = 0;
            this.replySeq = 0L;
            this.replyAck = 0L;
            this.replyMax = 0;
            this.replyPad = 0;
            return consumer;
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
                doReplyBegin();
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                initialSeq += data.reserved();
                assert replyCredit() >= data.reserved();
                doReplyData(data.flags(), data.reserved(), data.payload());
                doInitialWindow();
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                replyAck = window.acknowledge();
                replyMax = window.maximum();
                replyPad = window.padding();
                doInitialWindow();
                break;
            case EndFW.TYPE_ID:
                doReplyEnd();
                close(INITIAL_CLOSED | REPLY_CLOSED);
                break;
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                close(INITIAL_CLOSED | REPLY_CLOSED);
                break;
            default:
                break;
            }
        }

        private int replyCredit()
        {
            return (int) (replyAck + replyMax - replySeq);
        }

        private void doInitialWindow()
        {
            final int credit = replyCredit();
            final int maximum = Math.max(initialMax, credit);
            final long acknowledge = initialSeq - (maximum - credit);

            if (acknowledge > initialAck || maximum > initialMax)
            {
                initialAck = acknowledge;
                initialMax = maximum;

                final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(routedId)
                    .routedId(originId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .budgetId(0L)
                    .padding(replyPad)
                    .build();

                sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
            }
        }

        private void doReplyBegin()
        {
            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(0)
                .affinity(0L)
                .build();

            sender.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        private void doReplyData(
            int flags,
            int reserved,
            OctetsFW payload)
        {
            final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(0)
                .flags(flags)
                .budgetId(0L)
                .reserved(reserved)
                .payload(payload)
                .build();

            replySeq += data.reserved();

            sender.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
        }

        private void doReplyEnd()
        {
            final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(0)
                .build();

            sender.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
        }

        private void close(
            int closed)
        {
            final int opened = state;
            state |= closed;

            if (opened != (INITIAL_CLOSED | REPLY_CLOSED) && state == (INITIAL_CLOSED | REPLY_CLOSED))
            {
                idleStreams.offer(this);
            }
        }
    }
}
