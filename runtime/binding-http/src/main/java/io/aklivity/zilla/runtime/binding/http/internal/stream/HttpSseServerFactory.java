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
package io.aklivity.zilla.runtime.binding.http.internal.stream;

import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpSseFraming.CAPABILITY_MASK;
import static io.aklivity.zilla.runtime.binding.http.internal.types.stream.FrameFW.FIELD_OFFSET_STREAM_ID;
import static io.aklivity.zilla.runtime.binding.http.internal.types.stream.WindowFW.FIELD_OFFSET_CAPABILITIES;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.util.Flags.COMPLETE;

import java.util.function.LongUnaryOperator;

import io.aklivity.zilla.runtime.binding.http.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.codec.HttpSseEventFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpDataExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class HttpSseServerFactory implements BindingHandler
{
    private static final String8FW HEADER_NAME_TYPE = new String8FW("type");
    private static final String8FW HEADER_NAME_ID = new String8FW("id");

    private static final int FIELD_VALUE_MAX = 255;

    private static final int DEFERRED_LENGTH_OFFSET = 0;
    private static final int DEFERRED_RESERVED_OFFSET = DEFERRED_LENGTH_OFFSET + Integer.BYTES;
    private static final int DEFERRED_FLAGS_OFFSET = DEFERRED_RESERVED_OFFSET + Integer.BYTES;
    private static final int DEFERRED_BUDGET_OFFSET = DEFERRED_FLAGS_OFFSET + Integer.BYTES;
    private static final int DEFERRED_TRACE_OFFSET = DEFERRED_BUDGET_OFFSET + Long.BYTES;
    private static final int DEFERRED_AUTHORIZATION_OFFSET = DEFERRED_TRACE_OFFSET + Long.BYTES;
    private static final int DEFERRED_PAYLOAD_OFFSET = DEFERRED_AUTHORIZATION_OFFSET + Long.BYTES;

    public static final int MAXIMUM_HEADER_SIZE =
            5 +         // data:
            3 +         // id:
            255 +       // id string
            6 +         // event:
            255 +       // event string
            3;          // \n for data:, id:, event

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final ChallengeFW challengeRO = new ChallengeFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();

    private final HttpBeginExFW beginExRO = new HttpBeginExFW();
    private final HttpDataExFW dataExRO = new HttpDataExFW();
    private final OctetsFW retainedRO = new OctetsFW();

    private final HttpSseEventFW.Builder eventRW = new HttpSseEventFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx networkBuffer;
    private final MutableDirectBufferEx eventBuffer;
    private final MutableDirectBufferEx idBuffer;
    private final MutableDirectBufferEx typeBuffer;
    private final UnsafeBufferEx idView;
    private final UnsafeBufferEx typeView;
    private final BufferPool bufferPool;
    private final BufferPool deferredPool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;

    public HttpSseServerFactory(
        EngineContext context,
        BindingHandler streamFactory)
    {
        this.writeBuffer = context.writeBuffer();
        this.networkBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.eventBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.idBuffer = new UnsafeBufferEx(new byte[FIELD_VALUE_MAX]);
        this.typeBuffer = new UnsafeBufferEx(new byte[FIELD_VALUE_MAX]);
        this.idView = new UnsafeBufferEx(new byte[0]);
        this.typeView = new UnsafeBufferEx(new byte[0]);
        this.bufferPool = context.bufferPool();
        this.deferredPool = bufferPool.duplicate();
        this.streamFactory = streamFactory;
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.httpTypeId = context.supplyTypeId("http");
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer network)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);

        final HttpSseServer server = new HttpSseServer(
            network,
            begin.originId(),
            begin.routedId(),
            begin.streamId());

        server.stream.doAppBegin(msgTypeId, buffer, index, length);

        return server::onNetMessage;
    }

    private final class HttpSseServer
    {
        private final MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final HttpStream stream;

        private int state;

        private long httpReplySeq;
        private long httpReplyAck;
        private int httpReplyMax;
        private long httpReplyBud;
        private int httpReplyPad;
        private int httpReplyMin;
        private int httpReplyCaps;
        private long httpReplyAuth;
        private long httpReplyAt;

        private int networkSlot = NO_SLOT;
        private int networkSlotOffset;
        private boolean deferredEnd;
        private OctetsFW deferredEndExtension;

        private HttpSseServer(
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.stream = new HttpStream(originId, routedId);
        }

        private void onNetMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            if (msgTypeId != BeginFW.TYPE_ID)
            {
                final long streamId = buffer.getLong(index + FIELD_OFFSET_STREAM_ID);

                if (streamId == initialId)
                {
                    relay(stream.application, writeBuffer, msgTypeId, buffer, index, length, stream.initialId);
                }
                else
                {
                    switch (msgTypeId)
                    {
                    case WindowFW.TYPE_ID:
                        final WindowFW window = windowRO.wrap(buffer, index, index + length);
                        onNetWindow(window, buffer, index, length);
                        break;
                    case ResetFW.TYPE_ID:
                        final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                        onNetReset(reset, buffer, index, length);
                        break;
                    case ChallengeFW.TYPE_ID:
                        relay(stream.application, writeBuffer, msgTypeId, buffer, index, length, stream.replyId);
                        break;
                    default:
                        break;
                    }
                }
            }
        }

        private void onNetReset(
            ResetFW reset,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            if (stream.framed)
            {
                state = HttpState.closeReply(state);

                stream.doAppReset(reset.traceId(), reset.extension());
                cleanupNet();
            }
            else
            {
                relay(stream.application, writeBuffer, ResetFW.TYPE_ID, buffer, index, length, stream.replyId);
            }
        }

        private void onNetWindow(
            WindowFW window,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();

            httpReplyAck = acknowledge;
            httpReplyMax = maximum;
            httpReplyPad = window.padding();
            httpReplyBud = window.budgetId();
            httpReplyMin = window.minimum();
            httpReplyCaps = window.capabilities();
            httpReplyAuth = window.authorization();
            state = HttpState.openReply(state);

            if (stream.framed)
            {
                flushNetwork(traceId);
            }
            else if (stream.appFraming && !stream.begun)
            {
                stream.flushAppWindow(traceId, true, true);
            }
            else
            {
                relayWindow(stream.application, writeBuffer, buffer, index, length, stream.replyId);
            }
        }

        private void doNetBegin(
            BeginFW begin,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            this.httpReplySeq = begin.sequence();

            state = HttpState.openingReply(state);

            relay(network, networkBuffer, BeginFW.TYPE_ID, buffer, index, length, replyId);
        }

        private void doNetData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            Flyweight payload)
        {
            final long sequence = httpReplySeq;

            httpReplySeq += reserved;

            assert httpReplySeq <= httpReplyAck + httpReplyMax;

            doHttpData(network, networkBuffer, originId, routedId, replyId, sequence, httpReplyAck, httpReplyMax,
                    traceId, authorization, budgetId, flags, reserved, payload, null);
        }

        private void doNetFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            doHttpFlush(network, networkBuffer, originId, routedId, replyId, httpReplySeq, httpReplyAck, httpReplyMax,
                    traceId, authorization, budgetId, reserved, extension);
        }

        private void doNetAbort(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!HttpState.replyClosed(state))
            {
                state = HttpState.closeReply(state);

                doHttpAbort(network, networkBuffer, originId, routedId, replyId, httpReplySeq, httpReplyAck, httpReplyMax,
                        traceId, authorization, extension);

                cleanupNet();
            }
        }

        private void doNetEnd(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!HttpState.replyClosed(state))
            {
                state = HttpState.closeReply(state);

                doHttpEnd(network, networkBuffer, originId, routedId, replyId, httpReplySeq, httpReplyAck, httpReplyMax,
                        traceId, authorization, extension);

                cleanupNet();
            }
        }

        private void doEncodeEvent(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            DirectBufferEx id,
            DirectBufferEx type)
        {
            final HttpSseEventFW event = eventRW.wrap(eventBuffer, 0, eventBuffer.capacity())
                    .flags(flags)
                    .id(id)
                    .type(type)
                    .data(payload)
                    .build();

            final int length = event.sizeof();
            final int claimed = Math.max(reserved, length + httpReplyPad);
            final int credit = (int) (httpReplyAck + httpReplyMax - httpReplySeq);

            if (networkSlotOffset == 0 && claimed <= credit)
            {
                doNetData(traceId, authorization, budgetId, claimed, flags, event);
            }
            else
            {
                final int immediate = networkSlotOffset == 0 ? Math.max(Math.min(length, credit - httpReplyPad), 0) : 0;

                if (immediate > 0)
                {
                    final OctetsFW first = retainedRO.wrap(event.buffer(), event.offset(), event.offset() + immediate);

                    doNetData(traceId, authorization, budgetId, immediate + httpReplyPad, COMPLETE, first);
                }

                final int deferred = length - immediate;

                if (deferred > 0)
                {
                    final OctetsFW rest = retainedRO.wrap(event.buffer(), event.offset() + immediate, event.limit());

                    deferNetData(traceId, authorization, budgetId, deferred + httpReplyPad, COMPLETE, rest);
                }
            }
        }

        private void deferNetData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            Flyweight event)
        {
            if (networkSlot == NO_SLOT)
            {
                networkSlot = bufferPool.acquire(replyId);
            }

            final int length = event.sizeof();

            if (networkSlot != NO_SLOT &&
                networkSlotOffset + DEFERRED_PAYLOAD_OFFSET + length <= bufferPool.slotCapacity())
            {
                final MutableDirectBufferEx slot = deferredPool.buffer(networkSlot);
                final int offset = networkSlotOffset;

                slot.putInt(offset + DEFERRED_LENGTH_OFFSET, length);
                slot.putInt(offset + DEFERRED_RESERVED_OFFSET, reserved);
                slot.putInt(offset + DEFERRED_FLAGS_OFFSET, flags);
                slot.putLong(offset + DEFERRED_BUDGET_OFFSET, budgetId);
                slot.putLong(offset + DEFERRED_TRACE_OFFSET, traceId);
                slot.putLong(offset + DEFERRED_AUTHORIZATION_OFFSET, authorization);
                slot.putBytes(offset + DEFERRED_PAYLOAD_OFFSET, event.buffer(), event.offset(), length);

                networkSlotOffset += DEFERRED_PAYLOAD_OFFSET + length;
            }
            else
            {
                stream.doAppReset(traceId, null);
                doNetAbort(traceId, authorization, null);
            }
        }

        private void flushDeferred(
            long traceId)
        {
            boolean progressed = networkSlotOffset > 0;

            while (progressed)
            {
                final MutableDirectBufferEx slot = deferredPool.buffer(networkSlot);
                final int length = slot.getInt(DEFERRED_LENGTH_OFFSET);
                final int reserved = slot.getInt(DEFERRED_RESERVED_OFFSET);
                final int credit = (int) (httpReplyAck + httpReplyMax - httpReplySeq);

                progressed = reserved <= credit;

                if (progressed)
                {
                    final OctetsFW payload = retainedRO.wrap(slot, DEFERRED_PAYLOAD_OFFSET, DEFERRED_PAYLOAD_OFFSET + length);
                    final int flags = slot.getInt(DEFERRED_FLAGS_OFFSET);
                    final long budgetId = slot.getLong(DEFERRED_BUDGET_OFFSET);
                    final long deferredTraceId = slot.getLong(DEFERRED_TRACE_OFFSET);
                    final long authorization = slot.getLong(DEFERRED_AUTHORIZATION_OFFSET);
                    final int consumed = DEFERRED_PAYLOAD_OFFSET + length;

                    doNetData(deferredTraceId, authorization, budgetId, reserved, flags, payload);

                    networkSlotOffset -= consumed;

                    if (networkSlotOffset > 0)
                    {
                        slot.putBytes(0, slot, consumed, networkSlotOffset);
                    }
                    else
                    {
                        bufferPool.release(networkSlot);
                        networkSlot = NO_SLOT;
                        progressed = false;
                    }
                }
            }

            if (deferredEnd && networkSlotOffset == 0)
            {
                deferredEnd = false;

                doNetEnd(traceId, httpReplyAuth, deferredEndExtension);
            }
        }

        private void flushNetwork(
            long traceId)
        {
            flushDeferred(traceId);
            stream.flushAppWindow(traceId, false, true);
        }

        private void cleanupNet()
        {
            networkSlotOffset = 0;

            if (networkSlot != NO_SLOT)
            {
                bufferPool.release(networkSlot);
                networkSlot = NO_SLOT;
            }
        }

        final class HttpStream
        {
            private MessageConsumer application;
            private final long originId;
            private final long routedId;
            private final long initialId;
            private final long replyId;

            private int state;
            private boolean appFraming;
            private boolean begun;
            private boolean framed;

            private long appReplySeq;
            private long appReplyAck;
            private int appReplyMax;
            private int appReplyPad;

            private HttpStream(
                long originId,
                long routedId)
            {
                this.originId = originId;
                this.routedId = routedId;
                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
            }

            private void doAppBegin(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                writeBuffer.putBytes(0, buffer, index, length);
                writeBuffer.putLong(FIELD_OFFSET_STREAM_ID, initialId);

                application = streamFactory.newStream(msgTypeId, writeBuffer, 0, length, this::onAppMessage);
                application.accept(msgTypeId, writeBuffer, 0, length);
            }

            private void doAppReset(
                long traceId,
                OctetsFW extension)
            {
                if (!HttpState.replyClosed(state))
                {
                    state = HttpState.closeReply(state);

                    doReset(application, writeBuffer, originId, routedId, replyId, appReplySeq, appReplyAck, appReplyMax,
                            traceId, extension);
                }
            }

            private void onAppMessage(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                final long streamId = buffer.getLong(index + FIELD_OFFSET_STREAM_ID);

                if (streamId == initialId)
                {
                    if (msgTypeId == WindowFW.TYPE_ID)
                    {
                        onAppWindow(windowRO.wrap(buffer, index, index + length));
                    }

                    relay(network, networkBuffer, msgTypeId, buffer, index, length, HttpSseServer.this.initialId);
                }
                else if (msgTypeId == BeginFW.TYPE_ID)
                {
                    onAppBegin(beginRO.wrap(buffer, index, index + length), buffer, index, length);
                }
                else if (!framed)
                {
                    relay(network, networkBuffer, msgTypeId, buffer, index, length, HttpSseServer.this.replyId);
                }
                else
                {
                    switch (msgTypeId)
                    {
                    case DataFW.TYPE_ID:
                        final DataFW data = dataRO.wrap(buffer, index, index + length);
                        onAppData(data);
                        break;
                    case EndFW.TYPE_ID:
                        final EndFW end = endRO.wrap(buffer, index, index + length);
                        onAppEnd(end);
                        break;
                    case AbortFW.TYPE_ID:
                        final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                        onAppAbort(abort);
                        break;
                    case FlushFW.TYPE_ID:
                        final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                        onAppFlush(flush);
                        break;
                    default:
                        break;
                    }
                }
            }

            private void onAppWindow(
                WindowFW window)
            {
                if (!appFraming && (window.capabilities() & CAPABILITY_MASK) != 0)
                {
                    appFraming = true;

                    if (!begun && httpReplyMax > 0)
                    {
                        flushAppWindow(window.traceId(), true, true);
                    }
                }
            }

            private void onAppBegin(
                BeginFW begin,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                final HttpBeginExFW beginEx = begin.extension().get(beginExRO::tryWrap);

                begun = true;
                framed = appFraming && beginEx != null && HttpSseFraming.isEventStream(beginEx);

                if (framed)
                {
                    appReplySeq = begin.sequence();
                    appReplyAck = httpReplyAck;
                    appReplyMax = httpReplyMax;
                    appReplyPad = httpReplyPad;
                    state = HttpState.openingReply(state);

                    doNetBegin(begin, buffer, index, length);

                    flushAppWindow(begin.traceId(), true, true);
                }
                else
                {
                    relay(network, networkBuffer, BeginFW.TYPE_ID, buffer, index, length, HttpSseServer.this.replyId);

                    if (appFraming)
                    {
                        flushAppWindow(begin.traceId(), true, false);
                    }
                }
            }

            private void onAppData(
                DataFW data)
            {
                final long sequence = data.sequence();
                final long traceId = data.traceId();
                final long authorization = data.authorization();
                final long budgetId = data.budgetId();
                final int reserved = data.reserved();

                appReplySeq = sequence + reserved;

                if (appReplySeq > appReplyAck + appReplyMax)
                {
                    doAppReset(traceId, null);
                    doNetAbort(traceId, authorization, null);
                }
                else
                {
                    final int flags = data.flags();
                    final OctetsFW payload = data.payload();
                    final OctetsFW extension = data.extension();

                    DirectBufferEx id = null;
                    DirectBufferEx type = null;

                    final HttpDataExFW dataEx = extension.sizeof() > 0 ? extension.get(dataExRO::tryWrap) : null;

                    if (dataEx != null)
                    {
                        id = fieldValue(dataEx, HEADER_NAME_ID, idBuffer, idView);
                        type = fieldValue(dataEx, HEADER_NAME_TYPE, typeBuffer, typeView);
                    }

                    doEncodeEvent(traceId, authorization, budgetId, reserved, flags, payload, id, type);

                    flushAppWindow(traceId, false, true);
                }
            }

            private void onAppEnd(
                EndFW end)
            {
                appReplySeq = end.sequence();
                state = HttpState.closeReply(state);

                if (networkSlotOffset > 0)
                {
                    final OctetsFW extension = end.extension();
                    final byte[] bytes = new byte[extension.sizeof()];

                    extension.buffer().getBytes(extension.offset(), bytes);

                    deferredEnd = true;
                    deferredEndExtension = new OctetsFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
                    flushNetwork(end.traceId());
                }
                else
                {
                    doNetEnd(end.traceId(), end.authorization(), end.extension());
                }
            }

            private void onAppFlush(
                FlushFW flush)
            {
                appReplySeq = flush.sequence();

                flushNetwork(flush.traceId());
                doNetFlush(flush.traceId(), flush.authorization(), flush.budgetId(), flush.reserved(), flush.extension());
            }

            private void onAppAbort(
                AbortFW abort)
            {
                appReplySeq = abort.sequence();
                state = HttpState.closeReply(state);

                doNetAbort(abort.traceId(), abort.authorization(), abort.extension());
            }

            private void flushAppWindow(
                long traceId,
                boolean forced,
                boolean padded)
            {
                final int httpReplyPendingAck = (int) (httpReplySeq - httpReplyAck) + networkSlotOffset;
                final int appReplyAckMax = Math.max((int) (appReplySeq - httpReplyPendingAck), 0);
                final int appReplyPadMax = httpReplyPad + (padded ? MAXIMUM_HEADER_SIZE : 0);

                if (forced || appReplyAckMax > appReplyAck || httpReplyMax > appReplyMax || appReplyPadMax != appReplyPad)
                {
                    appReplyAck = appReplyAckMax;

                    assert appReplyAck <= appReplySeq;

                    appReplyMax = httpReplyMax;
                    appReplyPad = appReplyPadMax;

                    doWindow(application, writeBuffer, originId, routedId, replyId, appReplySeq, appReplyAck, appReplyMax,
                            traceId, httpReplyAuth, httpReplyBud, appReplyPad, httpReplyMin, httpReplyCaps | CAPABILITY_MASK);
                }
            }
        }
    }

    private void relay(
        MessageConsumer target,
        MutableDirectBufferEx relayBuffer,
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        long streamId)
    {
        relayBuffer.putBytes(0, buffer, index, length);
        relayBuffer.putLong(FIELD_OFFSET_STREAM_ID, streamId);

        target.accept(msgTypeId, relayBuffer, 0, length);
    }

    private void relayWindow(
        MessageConsumer target,
        MutableDirectBufferEx relayBuffer,
        DirectBufferEx buffer,
        int index,
        int length,
        long streamId)
    {
        relayBuffer.putBytes(0, buffer, index, length);
        relayBuffer.putLong(FIELD_OFFSET_STREAM_ID, streamId);
        relayBuffer.putByte(FIELD_OFFSET_CAPABILITIES, (byte) (relayBuffer.getByte(FIELD_OFFSET_CAPABILITIES) | CAPABILITY_MASK));

        target.accept(WindowFW.TYPE_ID, relayBuffer, 0, length);
    }

    private DirectBufferEx fieldValue(
        HttpDataExFW dataEx,
        String8FW name,
        MutableDirectBufferEx scratch,
        UnsafeBufferEx view)
    {
        final HttpHeaderFW header = dataEx.headers().matchFirst(h -> name.equals(h.name()));

        DirectBufferEx field = null;

        if (header != null)
        {
            final DirectBufferEx value = header.value().value();
            final int length = value != null ? Math.min(value.capacity(), scratch.capacity()) : 0;

            if (length > 0)
            {
                value.getBytes(0, scratch, 0, length);
            }

            view.wrap(scratch, 0, length);
            field = view;
        }

        return field;
    }

    private void doHttpBegin(
        MessageConsumer receiver,
        MutableDirectBufferEx buffer,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        OctetsFW extension)
    {
        final BeginFW begin = beginRW.wrap(buffer, 0, buffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doHttpData(
        MessageConsumer receiver,
        MutableDirectBufferEx buffer,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int flags,
        int reserved,
        Flyweight payload,
        OctetsFW extension)
    {
        final DataFW.Builder builder = dataRW.wrap(buffer, 0, buffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .flags(flags)
                .budgetId(budgetId)
                .reserved(reserved);

        if (payload != null)
        {
            builder.payload(payload.buffer(), payload.offset(), payload.sizeof());
        }
        else
        {
            builder.payload((OctetsFW) null);
        }

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final DataFW data = builder.build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doHttpEnd(
        MessageConsumer receiver,
        MutableDirectBufferEx buffer,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        OctetsFW extension)
    {
        final EndFW end = endRW.wrap(buffer, 0, buffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doHttpAbort(
        MessageConsumer receiver,
        MutableDirectBufferEx buffer,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        OctetsFW extension)
    {
        final AbortFW.Builder builder = abortRW.wrap(buffer, 0, buffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final AbortFW abort = builder.build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doHttpFlush(
        MessageConsumer receiver,
        MutableDirectBufferEx buffer,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        OctetsFW extension)
    {
        final FlushFW.Builder builder = flushRW.wrap(buffer, 0, buffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .budgetId(budgetId)
                .reserved(reserved);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final FlushFW flush = builder.build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doWindow(
        MessageConsumer sender,
        MutableDirectBufferEx buffer,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int padding,
        int minimum,
        int capabilities)
    {
        final WindowFW window = windowRW.wrap(buffer, 0, buffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .budgetId(budgetId)
                .padding(padding)
                .minimum(minimum)
                .capabilities(capabilities)
                .build();

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer sender,
        MutableDirectBufferEx buffer,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        OctetsFW extension)
    {
        final ResetFW.Builder builder = resetRW.wrap(buffer, 0, buffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final ResetFW reset = builder.build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doChallenge(
        MessageConsumer sender,
        MutableDirectBufferEx buffer,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        OctetsFW extension)
    {
        final ChallengeFW challenge = challengeRW.wrap(buffer, 0, buffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

        sender.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
    }
}
