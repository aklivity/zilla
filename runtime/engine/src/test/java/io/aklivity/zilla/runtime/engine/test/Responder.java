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
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.test.EngineBench.FrameCounter;

/**
 * Accepts streams routed to an external binding, grants request credit, counts the frames it receives and
 * replays a pre-built {@link Response} once the {@link Trigger} frame arrives, within the reply credit it is
 * granted. A {@code null} response withholds the reply so the request stays in flight.
 * <p>
 * Extensions are opaque bytes, so the responder knows nothing about the protocol it replays. It does not
 * allocate in steady state: stream state is pooled and frames are built into a reused buffer.
 */
public final class Responder implements BindingHandler
{
    public enum Trigger
    {
        BEGIN,
        FLUSH,
        END
    }

    public static final class Response
    {
        public static final Response EMPTY = new Response(new byte[0], new byte[0][], new byte[0][], new byte[0], 0);

        private final DirectBufferEx beginEx;
        private final DirectBufferEx[] sectionEx;
        private final DirectBufferEx[] sectionPayload;
        private final DirectBufferEx endEx;
        private final int fragmentLength;

        public Response(
            byte[] beginEx,
            byte[][] sectionEx,
            byte[][] sectionPayload,
            byte[] endEx,
            int fragmentLength)
        {
            this.beginEx = new UnsafeBufferEx(beginEx);
            this.sectionEx = wrap(sectionEx);
            this.sectionPayload = wrap(sectionPayload);
            this.endEx = new UnsafeBufferEx(endEx);
            this.fragmentLength = fragmentLength;
        }

        private static DirectBufferEx[] wrap(
            byte[][] arrays)
        {
            final DirectBufferEx[] buffers = new DirectBufferEx[arrays.length];

            for (int i = 0; i < arrays.length; i++)
            {
                buffers[i] = new UnsafeBufferEx(arrays[i]);
            }

            return buffers;
        }
    }

    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final int WRITE_BUFFER_CAPACITY = 64 * 1024;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final WindowFW windowRO = new WindowFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[WRITE_BUFFER_CAPACITY]);
    private final Queue<ResponderStream> idleStreams = new ArrayDeque<>();
    private final FrameCounter counter = new FrameCounter();
    private final int window;
    private final Trigger trigger;
    private final Response response;

    public Responder(
        Trigger trigger,
        Response response)
    {
        this(EngineBench.DEFAULT_WINDOW, trigger, response);
    }

    public Responder(
        int window,
        Trigger trigger,
        Response response)
    {
        this.window = window;
        this.trigger = trigger;
        this.response = response;
    }

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
        ResponderStream stream = idleStreams.poll();

        if (stream == null)
        {
            stream = new ResponderStream();
        }

        return stream.reset(sender);
    }

    private final class ResponderStream
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
        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private int step;
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
            this.step = 0;
            this.offset = 0;
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
                doInitialWindow();
                onTrigger(Trigger.BEGIN);
                break;
            case DataFW.TYPE_ID:
                initialSeq += dataRO.wrap(buffer, index, index + length).reserved();
                doInitialWindow();
                break;
            case FlushFW.TYPE_ID:
                onTrigger(Trigger.FLUSH);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW windowFW = windowRO.wrap(buffer, index, index + length);
                replyAck = windowFW.acknowledge();
                replyMax = windowFW.maximum();
                replyPad = windowFW.padding();
                if (step != 0)
                {
                    doReply();
                }
                break;
            case EndFW.TYPE_ID:
                onTrigger(Trigger.END);
                close(INITIAL_CLOSED);
                break;
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                close(INITIAL_CLOSED | REPLY_CLOSED);
                break;
            default:
                break;
            }
        }

        private void onTrigger(
            Trigger received)
        {
            if (response != null && received == trigger)
            {
                doReply();
            }
        }

        private void doReply()
        {
            final int sections = response.sectionPayload.length;

            if (step == 0)
            {
                doReplyBegin();
                step++;
            }

            while (step <= sections && replyCredit() > replyPad)
            {
                final DirectBufferEx payload = response.sectionPayload[step - 1];
                final int fragmentLength = response.fragmentLength != 0 ? response.fragmentLength : payload.capacity();
                final int length = Math.min(Math.min(payload.capacity() - offset, fragmentLength), replyCredit() - replyPad);

                doReplyData(response.sectionEx[step - 1], payload, length);

                offset += length;
                if (offset == payload.capacity())
                {
                    step++;
                    offset = 0;
                }
            }

            if (step == sections + 1)
            {
                doReplyEnd();
                step++;
                close(REPLY_CLOSED);
            }
        }

        private int replyCredit()
        {
            return (int) (replyAck + replyMax - replySeq);
        }

        private void doInitialWindow()
        {
            final WindowFW windowFW = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialSeq)
                .maximum(window)
                .budgetId(0L)
                .padding(0)
                .build();

            sender.accept(windowFW.typeId(), windowFW.buffer(), windowFW.offset(), windowFW.sizeof());
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
                .extension(response.beginEx, 0, response.beginEx.capacity())
                .build();

            sender.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        private void doReplyData(
            DirectBufferEx extension,
            DirectBufferEx payload,
            int length)
        {
            final int first = offset == 0 ? FLAGS_INIT : 0;
            final int last = offset + length == payload.capacity() ? FLAGS_FIN : 0;
            final int extensionLength = first != 0 ? extension.capacity() : 0;

            final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(0)
                .flags(first | last)
                .budgetId(0L)
                .reserved(length + replyPad)
                .payload(payload, offset, length)
                .extension(extension, 0, extensionLength)
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
                .extension(response.endEx, 0, response.endEx.capacity())
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
