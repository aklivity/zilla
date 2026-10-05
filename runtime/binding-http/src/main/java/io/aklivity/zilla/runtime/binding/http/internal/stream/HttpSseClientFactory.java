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
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static io.aklivity.zilla.runtime.engine.util.Flags.NONE;
import static io.aklivity.zilla.runtime.engine.util.Flags.fin;
import static io.aklivity.zilla.runtime.engine.util.Flags.init;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.unmodifiableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.LongUnaryOperator;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.http.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.BeginFW;
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
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class HttpSseClientFactory implements HttpStreamFactory
{
    private static final String8FW HTTP_HEADER_STATUS = new String8FW(":status");
    private static final String8FW HTTP_HEADER_TYPE = new String8FW("type");
    private static final String8FW HTTP_HEADER_ID = new String8FW("id");

    private static final String16FW HTTP_HEADER_STATUS_200 = new String16FW("200");

    private static final int SIGNAL_NET_WINDOW = 1;

    private static final byte[] STREAM_BOM_BYTES = "\ufeff".getBytes(UTF_8);
    private static final int STREAM_BOM_BYTES_LENGTH = STREAM_BOM_BYTES.length;

    private static final int LINE_CR_BYTE = 0x0d;
    private static final int LINE_LF_BYTE = 0x0a;
    private static final int LINE_COLON_BYTE = 0x3a;
    private static final int LINE_SPACE_BYTE = 0x20;

    private static final DirectBufferEx FIELD_NAME_TYPE_BYTES = new UnsafeBufferEx("event".getBytes(UTF_8));
    private static final DirectBufferEx FIELD_NAME_ID_BYTES = new UnsafeBufferEx("id".getBytes(UTF_8));
    private static final DirectBufferEx FIELD_NAME_DATA_BYTES = new UnsafeBufferEx("data".getBytes(UTF_8));

    private static final IntPredicate EOL_MATCHER = v -> v == LINE_CR_BYTE || v == LINE_LF_BYTE;
    private static final IntPredicate COLON_MATCHER = v -> v == LINE_COLON_BYTE;
    private static final IntPredicate EOF_MATCHER = COLON_MATCHER.or(EOL_MATCHER);

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(0L, 0), 0, 0);

    private static final String8FW FIELD_VALUE_NULL = new String8FW(null);
    private static final String8FW FIELD_VALUE_EMPTY = new String8FW("");
    private static final OctetsFW FIELD_DATA_EMPTY = EMPTY_OCTETS;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();

    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final HttpDataExFW.Builder httpDataExRW = new HttpDataExFW.Builder();

    private final DirectBufferEx nameRO = new UnsafeBufferEx(0L, 0);
    private final DirectBufferEx valueRO = new UnsafeBufferEx(0L, 0);

    private final OctetsFW lineDataRO = new OctetsFW();
    private final OctetsFW valueDataRO = new OctetsFW();

    private final String8FW.Builder valueIdRW = new String8FW.Builder().wrap(new UnsafeBufferEx(new byte[256]), 0, 256);
    private final String8FW.Builder valueTypeRW = new String8FW.Builder().wrap(new UnsafeBufferEx(new byte[256]), 0, 256);

    private final Map<DirectBufferEx, HttpSseFieldName> decodeableFieldNames;
    {
        final HashMap<DirectBufferEx, HttpSseFieldName> fieldNames = new HashMap<>();
        fieldNames.put(FIELD_NAME_DATA_BYTES, HttpSseFieldName.DATA);
        fieldNames.put(FIELD_NAME_ID_BYTES, HttpSseFieldName.ID);
        fieldNames.put(FIELD_NAME_TYPE_BYTES, HttpSseFieldName.TYPE);
        decodeableFieldNames = unmodifiableMap(fieldNames);
    }

    private final HttpSseClientDecoder decodeBom = this::decodeBom;
    private final HttpSseClientDecoder decodeEvent = this::decodeEvent;
    private final HttpSseClientDecoder decodeLine = this::decodeLine;
    private final HttpSseClientDecoder decodeLineEnding = this::decodeLineEnding;
    private final HttpSseClientDecoder decodeLineEndingAfterCR = this::decodeLineEndingAfterCR;
    private final HttpSseClientDecoder decodeLineEnded = this::decodeLineEnded;
    private final HttpSseClientDecoder decodeIgnoreLine = this::decodeIgnoreLine;
    private final HttpSseClientDecoder decodeFieldName = this::decodeFieldName;
    private final HttpSseClientDecoder decodeFieldColon = this::decodeFieldColon;
    private final HttpSseClientDecoder decodeFieldSpace = this::decodeFieldSpace;
    private final HttpSseClientDecoder decodeFieldValue = this::decodeFieldValue;
    private final HttpSseClientDecoder decodeFieldDataValue = this::decodeFieldDataValue;
    private final HttpSseClientDecoder decodeEventEnding = this::decodeEventEnding;

    private final MutableDirectBufferEx appBuffer;
    private final MutableDirectBufferEx netBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final MutableDirectBufferEx lineBuffer;
    private final BufferPool decodePool;
    private final Signaler signaler;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int decodeMax;
    private final HttpStreamFactory next;

    public HttpSseClientFactory(
        EngineContext context,
        HttpStreamFactory next)
    {
        final int capacity = context.writeBuffer().capacity();

        this.appBuffer = new UnsafeBufferEx(new byte[capacity]);
        this.netBuffer = new UnsafeBufferEx(new byte[capacity]);
        this.extBuffer = new UnsafeBufferEx(new byte[capacity]);
        this.lineBuffer = new UnsafeBufferEx(new byte[capacity]);
        this.decodePool = context.bufferPool().duplicate();
        this.signaler = context.signaler();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.httpTypeId = context.supplyTypeId("http");
        this.decodeMax = decodePool.slotCapacity();
        this.next = next;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        next.attach(binding);
    }

    @Override
    public void detach(
        long bindingId)
    {
        next.detach(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer application)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);

        final HttpSseClient client = new HttpSseClient(
            application,
            begin.originId(),
            begin.routedId(),
            begin.streamId());

        client.delegate.doNetStream(msgTypeId, buffer, index, length);

        return client.delegate.network != null ? client::onAppMessage : null;
    }

    private final class HttpSseClient
    {
        private final MessageConsumer application;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final HttpClient delegate;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBud;

        private int state;

        private HttpSseClient(
            MessageConsumer application,
            long originId,
            long routedId,
            long initialId)
        {
            this.application = application;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.delegate = new HttpClient(originId, routedId, this);
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
                delegate.network.accept(msgTypeId, buffer, index, length);
            }
            else if (msgTypeId == WindowFW.TYPE_ID)
            {
                delegate.onAppWindow(windowRO.wrap(buffer, index, index + length), buffer, index, length);
            }
            else
            {
                if (msgTypeId == ResetFW.TYPE_ID)
                {
                    state = HttpState.closeReply(state);

                    delegate.cleanupDecodeSlot();
                }

                delegate.network.accept(msgTypeId, buffer, index, length);
            }
        }

        private void doAppData(
            long traceId,
            long authorization,
            long budgetId,
            int flags,
            OctetsFW payload,
            Flyweight extension)
        {
            final int length = payload != null ? payload.sizeof() : 0;
            final int reserved = length + replyPad;

            doData(application, appBuffer, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, flags, reserved, payload, extension);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doAppAbort(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!HttpState.replyClosed(state))
            {
                state = HttpState.closeReply(state);

                doAbort(application, appBuffer, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, extension);
            }
        }

        private void doAppEnd(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!HttpState.replyClosed(state))
            {
                state = HttpState.closeReply(state);

                doEnd(application, appBuffer, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, extension);
            }
        }

        private void doAppReset(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            doReset(application, appBuffer, originId, routedId, initialId, 0L, 0L, 0,
                    traceId, authorization, extension);
        }

        private void cleanupApp(
            long traceId,
            long authorization)
        {
            doAppReset(traceId, authorization, null);
            doAppAbort(traceId, authorization, null);
        }
    }

    private final class HttpClient
    {
        private final HttpSseClient delegate;
        private final IntConsumer onNetWindowSignal;

        private MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;
        private boolean begun;
        private boolean decided;
        private boolean eventStream;
        private boolean decoding;
        private byte[] heldWindow;

        private boolean windowPending;
        private long windowTraceId;
        private long windowAuthorization;
        private long windowBudgetId;
        private int windowPadding;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;

        private HttpSseClientDecoder decoder;

        private int decodableLineAt;
        private int decodedLineEndBytes;
        private HttpSseFieldName decodedFieldName;
        private int decodedDataLines;
        private int decodedDataFlags;

        private String8FW decodedId = FIELD_VALUE_NULL;
        private String8FW decodedType = FIELD_VALUE_NULL;
        private OctetsFW decodedData;

        private HttpClient(
            long originId,
            long routedId,
            HttpSseClient delegate)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = delegate.initialId;
            this.replyId = delegate.replyId;
            this.delegate = delegate;
            this.onNetWindowSignal = this::onNetWindowSignal;
            this.decoder = decodeBom;
        }

        private void doNetStream(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            network = next.newStream(msgTypeId, buffer, index, length, this::onNetMessage);
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            state = HttpState.openReply(state);

            doWindow(network, netBuffer, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, padding, 0, 0);
        }

        private void doNetReset(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!HttpState.replyClosed(state))
            {
                state = HttpState.closeReply(state);

                doReset(network, netBuffer, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, extension);
            }

            cleanupDecodeSlot();
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            doAbort(network, netBuffer, originId, routedId, initialId, 0L, 0L, 0, traceId, authorization, null);
        }

        private void scheduleNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            windowTraceId = traceId;
            windowAuthorization = authorization;
            windowBudgetId = budgetId;
            windowPadding = padding;

            if (!windowPending)
            {
                windowPending = true;
                signaler.signalAt(System.currentTimeMillis(), SIGNAL_NET_WINDOW, onNetWindowSignal);
            }
        }

        private void onNetWindowSignal(
            int signalId)
        {
            windowPending = false;

            if (!HttpState.replyClosed(state) && !HttpState.replyClosing(state))
            {
                doNetWindow(windowTraceId, windowAuthorization, windowBudgetId, windowPadding);
            }
        }

        private void onNetMessage(
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
                    relayWindow(delegate.application, appBuffer, buffer, index, length, delegate.initialId);
                }
                else
                {
                    delegate.application.accept(msgTypeId, buffer, index, length);
                }
            }
            else
            {
                switch (msgTypeId)
                {
                case BeginFW.TYPE_ID:
                    final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                    onNetBegin(begin, buffer, index, length);
                    break;
                case DataFW.TYPE_ID:
                    final DataFW data = dataRO.wrap(buffer, index, index + length);
                    onNetData(data, buffer, index, length);
                    break;
                case EndFW.TYPE_ID:
                    final EndFW end = endRO.wrap(buffer, index, index + length);
                    onNetEnd(end, buffer, index, length);
                    break;
                case AbortFW.TYPE_ID:
                    final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                    onNetAbort(abort, buffer, index, length);
                    break;
                case FlushFW.TYPE_ID:
                    final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                    onNetFlush(flush, buffer, index, length);
                    break;
                default:
                    delegate.application.accept(msgTypeId, buffer, index, length);
                    break;
                }
            }
        }

        private void onNetBegin(
            BeginFW begin,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            final HttpBeginExFW httpBeginEx = begin.extension().get(httpBeginExRO::tryWrap);

            replySeq = begin.sequence();
            replyAck = begin.acknowledge();
            replyMax = begin.maximum();
            delegate.replySeq = replySeq;
            delegate.replyAck = replyAck;
            delegate.replyMax = replyMax;
            delegate.state = HttpState.openingReply(delegate.state);
            state = HttpState.openingReply(state);
            begun = true;
            eventStream = httpBeginEx != null && isEventStream(httpBeginEx);

            delegate.application.accept(BeginFW.TYPE_ID, buffer, index, length);

            if (heldWindow != null)
            {
                final byte[] held = heldWindow;

                heldWindow = null;

                onAppWindow(windowRO.wrap(new UnsafeBufferEx(held), 0, held.length), new UnsafeBufferEx(held), 0, held.length);
            }
        }

        private void onAppWindow(
            WindowFW window,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            final boolean framing = (window.capabilities() & CAPABILITY_MASK) != 0;

            if (!begun && framing)
            {
                heldWindow = new byte[length];
                buffer.getBytes(index, heldWindow);
            }
            else if (!begun)
            {
                decided = true;
                network.accept(WindowFW.TYPE_ID, buffer, index, length);
            }
            else
            {
                if (!decided)
                {
                    decided = true;
                    decoding = eventStream && framing;
                }

                if (decoding)
                {
                    delegate.replyAck = window.acknowledge();
                    delegate.replyMax = window.maximum();
                    delegate.replyPad = window.padding();
                    delegate.replyBud = window.budgetId();

                    assert delegate.replyAck <= delegate.replySeq;

                    decodeNet(window.traceId(), window.authorization(), window.budgetId());

                    endReplyIfStalled(window.traceId(), window.authorization());
                }
                else
                {
                    network.accept(WindowFW.TYPE_ID, buffer, index, length);
                }
            }
        }

        private void onNetData(
            DataFW data,
            DirectBufferEx dataBuffer,
            int dataIndex,
            int dataLength)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + data.reserved();

            if (!decoding)
            {
                delegate.application.accept(DataFW.TYPE_ID, dataBuffer, dataIndex, dataLength);
            }
            else if (replySeq > replyAck + replyMax)
            {
                cleanupNet(traceId, authorization);
            }
            else
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNet(traceId, authorization);
                }
                else
                {
                    final OctetsFW payload = data.payload();
                    int reserved = data.reserved();
                    int offset = payload.offset();
                    int limit = payload.limit();

                    final MutableDirectBufferEx buffer = decodePool.buffer(decodeSlot);
                    buffer.putBytes(decodeSlotOffset, payload.buffer(), offset, limit - offset);
                    decodeSlotOffset += limit - offset;
                    decodeSlotReserved += reserved;

                    offset = 0;
                    limit = decodeSlotOffset;
                    reserved = decodeSlotReserved;

                    decodeNet(traceId, authorization, budgetId, reserved, buffer, offset, limit);
                }
            }
        }

        private void onNetEnd(
            EndFW end,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            final long sequence = end.sequence();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            replySeq = sequence;

            if (!decoding)
            {
                state = HttpState.closeReply(state);

                delegate.application.accept(EndFW.TYPE_ID, buffer, index, length);
            }
            else if (decodeSlot != NO_SLOT)
            {
                state = HttpState.closingReply(state);

                decodeNet(traceId, authorization, delegate.replyBud);

                endReplyIfStalled(traceId, authorization);
            }
            else
            {
                state = HttpState.closeReply(state);

                delegate.doAppEnd(traceId, authorization, null);
            }
        }

        private void endReplyIfStalled(
            long traceId,
            long authorization)
        {
            final int replyNoAck = (int) (delegate.replySeq - delegate.replyAck);
            final boolean credit = delegate.replyMax - replyNoAck - delegate.replyPad > 0;

            if (HttpState.replyClosing(state) && decodeSlot != NO_SLOT && credit)
            {
                state = HttpState.closeReply(state);

                cleanupDecodeSlot();

                delegate.doAppEnd(traceId, authorization, null);
            }
        }

        private void onNetAbort(
            AbortFW abort,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            replySeq = abort.sequence();
            state = HttpState.closeReply(state);

            cleanupDecodeSlot();

            delegate.application.accept(AbortFW.TYPE_ID, buffer, index, length);
        }

        private void onNetFlush(
            FlushFW flush,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            replySeq = flush.sequence() + flush.reserved();

            delegate.application.accept(FlushFW.TYPE_ID, buffer, index, length);
        }

        private void cleanupNet(
            long traceId,
            long authorization)
        {
            doNetReset(traceId, authorization, null);
            doNetAbort(traceId, authorization);

            delegate.cleanupApp(traceId, authorization);
        }

        private boolean isEventStream(
            HttpBeginExFW httpBeginEx)
        {
            final HttpHeaderFW status = httpBeginEx.headers().matchFirst(h -> HTTP_HEADER_STATUS.equals(h.name()));
            final boolean ok = status != null && HTTP_HEADER_STATUS_200.equals(status.value());

            return ok && HttpSseFraming.isEventStream(httpBeginEx);
        }

        private void decodeNet(
            long traceId,
            long authorization,
            long budgetId)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx buffer = decodePool.buffer(decodeSlot);
                final int offset = 0;
                final int limit = decodeSlotOffset;
                final int reserved = decodeSlotReserved;

                decodeNet(traceId, authorization, budgetId, reserved, buffer, offset, limit);
            }
            else
            {
                onNetDecodable(traceId, authorization, budgetId, decodeSlotOffset, delegate.replyPad, decodeMax);
            }
        }

        private void decodeNet(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBufferEx buffer,
            int offset,
            int limit)
        {
            HttpSseClientDecoder previous = null;
            int progress = offset;
            while (progress <= limit && previous != decoder)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, authorization, budgetId, reserved, buffer, offset, progress, limit);
            }

            if (progress < limit)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNet(traceId, authorization);
                }
                else
                {
                    final MutableDirectBufferEx decodeBuffer = decodePool.buffer(decodeSlot);
                    decodeBuffer.putBytes(0, buffer, progress, limit - progress);
                    decodeSlotOffset = limit - progress;
                    decodeSlotReserved = (int) ((long) (limit - progress) * reserved / (limit - offset));
                    assert decodeSlotReserved >= 0;
                }

                onNetDecodable(traceId, authorization, budgetId, decodeSlotOffset, delegate.replyPad, decodeMax);
            }
            else
            {
                doEncodeDataFragment(traceId, authorization, budgetId, reserved);

                cleanupDecodeSlot();

                if (HttpState.replyClosing(state))
                {
                    delegate.doAppEnd(traceId, authorization, null);
                }
                else if (reserved > 0)
                {
                    onNetDecodable(traceId, authorization, budgetId, 0, delegate.replyPad, decodeMax);
                }
            }
        }

        private void onNetDecodable(
            long traceId,
            long authorization,
            long budgetId,
            int minReplyNoAck,
            int minReplyPad,
            int minReplyMax)
        {
            final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

            if (!HttpState.replyClosing(state) &&
                (newReplyAck > replyAck || minReplyMax > replyMax || !HttpState.replyOpened(state)))
            {
                replyAck = newReplyAck;
                assert replyAck <= replySeq;

                replyMax = minReplyMax;

                scheduleNetWindow(traceId, authorization, budgetId, minReplyPad);
            }
        }

        private void onDecodedEventFragment(
            long traceId,
            long authorization,
            long budgetId,
            int flags,
            String8FW id,
            String8FW type,
            OctetsFW data)
        {
            final Flyweight httpDataEx =
                id != FIELD_VALUE_NULL ||
                type != FIELD_VALUE_NULL
                    ? eventDataEx(id, type)
                    : EMPTY_OCTETS;

            if (decodedDataLines > 1 && decodedData != null)
            {
                lineBuffer.putByte(0, (byte) LINE_LF_BYTE);
                lineBuffer.putBytes(1, data.buffer(), data.offset(), data.sizeof());
                data = lineDataRO.wrap(lineBuffer, 0, 1 + data.sizeof());
            }

            delegate.doAppData(traceId, authorization, budgetId, flags, data, httpDataEx);
        }

        private Flyweight eventDataEx(
            String8FW id,
            String8FW type)
        {
            httpDataExRW.wrap(extBuffer, 0, extBuffer.capacity()).typeId(httpTypeId);

            if (type != FIELD_VALUE_NULL)
            {
                final DirectBufferEx value = type.value();

                httpDataExRW.headersItem(h -> h.name(HTTP_HEADER_TYPE).value(value, 0, value != null ? value.capacity() : 0));
            }

            if (id != FIELD_VALUE_NULL)
            {
                final DirectBufferEx value = id.value();

                httpDataExRW.headersItem(h -> h.name(HTTP_HEADER_ID).value(value, 0, value != null ? value.capacity() : 0));
            }

            return httpDataExRW.build();
        }

        private void doEncodeDataFragment(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            final String8FW id = decodedId;
            final String8FW type = decodedType;
            final OctetsFW data = decodedData;

            if (id != FIELD_VALUE_NULL ||
                type != FIELD_VALUE_NULL ||
                data != null)
            {
                if (data != null)
                {
                    final int flags = INIT & ~decodedDataFlags;

                    onDecodedEventFragment(traceId, authorization, budgetId, flags, id, type, data);

                    decodedType = FIELD_VALUE_NULL;
                    decodedId = FIELD_VALUE_NULL;
                    decodedData = null;
                    decodedDataFlags = init(decodedDataFlags);
                }
                else
                {
                    if (decodedId == valueIdRW.flyweight())
                    {
                        decodedId = new String8FW(decodedId.asString());
                    }
                    if (decodedType == valueTypeRW.flyweight())
                    {
                        decodedType = new String8FW(decodedType.asString());
                    }
                }
            }
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
                decodeSlotReserved = 0;
            }
        }
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

    private void doData(
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
        OctetsFW payload,
        Flyweight extension)
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
                .reserved(reserved)
                .payload(payload);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final DataFW frame = builder.build();

        receiver.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());
    }

    private void doEnd(
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
        final EndFW.Builder builder = endRW.wrap(buffer, 0, buffer.capacity())
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

        final EndFW end = builder.build();

        sender.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doAbort(
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

        sender.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
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
        long authorization,
        OctetsFW extension)
    {
        final ResetFW.Builder builder = resetRW.wrap(buffer, 0, buffer.capacity())
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

        final ResetFW reset = builder.build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    @FunctionalInterface
    private interface HttpSseClientDecoder
    {
        int decode(
            HttpClient client,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBufferEx buffer,
            int offset,
            int progress,
            int limit);
    }

    private int decodeBom(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length >= STREAM_BOM_BYTES_LENGTH)
        {
            if (matchAllBytes(buffer, progress, limit, STREAM_BOM_BYTES))
            {
                progress += STREAM_BOM_BYTES_LENGTH;
            }

            client.decoder = decodeEvent;
        }

        return progress;
    }

    private int decodeEvent(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        client.decodedType = FIELD_VALUE_NULL;
        client.decodedId = FIELD_VALUE_NULL;
        client.decodedData = null;
        client.decodedDataLines = 0;
        client.decodedDataFlags = 0;

        client.decoder = decodeLine;

        return progress;
    }

    private int decodeLine(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length != 0)
        {
            final int lineStart = buffer.getByte(progress);

            client.decodableLineAt = progress;
            client.decodedLineEndBytes = 0;

            switch (lineStart)
            {
            case LINE_COLON_BYTE:
                client.decoder = decodeIgnoreLine;
                break;
            case LINE_CR_BYTE:
            case LINE_LF_BYTE:
                client.decoder = decodeEventEnding;
                break;
            default:
                client.decoder = decodeFieldName;
                break;
            }
        }

        return progress;
    }

    private int decodeEventEnding(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length != 0)
        {
            final String8FW id = client.decodedId;
            final String8FW type = client.decodedType;
            final OctetsFW data =
                client.decodedData == null &&
                client.decodedDataFlags != COMPLETE &&
                client.decodedDataFlags != NONE
                    ? FIELD_DATA_EMPTY
                    : client.decodedData;

            if (id != FIELD_VALUE_NULL ||
                type != FIELD_VALUE_NULL ||
                data != null)
            {
                final int flags = COMPLETE & ~client.decodedDataFlags;

                client.onDecodedEventFragment(traceId, authorization, budgetId, flags, id, type, data);

                client.decodedType = FIELD_VALUE_NULL;
                client.decodedId = FIELD_VALUE_NULL;
                client.decodedData = null;
                client.decodedDataLines = 0;
                client.decodedDataFlags = fin(client.decodedDataFlags);
            }

            client.decoder = decodeLineEnding;
        }

        return progress;
    }

    private int decodeLineEnding(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length != 0)
        {
            int endOfLineAt = indexOfEndOfLine(buffer, progress, progress + 1);

            if (endOfLineAt != -1)
            {
                final byte endOfLine = buffer.getByte(progress);

                progress++;

                client.decodedLineEndBytes++;
                client.decoder = endOfLine == LINE_CR_BYTE ? decodeLineEndingAfterCR : decodeLineEnded;
            }
        }

        return progress;
    }

    private int decodeLineEndingAfterCR(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length != 0)
        {
            if (buffer.getByte(progress) == LINE_LF_BYTE)
            {
                progress++;

                client.decodedLineEndBytes++;
            }

            client.decoder = decodeLineEnded;
        }

        return progress;
    }

    private int decodeLineEnded(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        boolean lineEmpty = progress - client.decodableLineAt == client.decodedLineEndBytes;

        client.decoder = lineEmpty ? decodeEvent : decodeLine;

        return progress;
    }

    private int decodeFieldName(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length != 0)
        {
            int limitOfFieldName = limitOfFieldName(buffer, progress, limit);

            if (limitOfFieldName != -1)
            {
                DirectBufferEx name = nameRO;
                name.wrap(buffer, progress, limitOfFieldName - progress);

                final HttpSseFieldName fieldName = decodeableFieldNames.getOrDefault(name, HttpSseFieldName.IGNORE);

                progress = limitOfFieldName;

                switch (fieldName)
                {
                case ID:
                    client.decodedId = FIELD_VALUE_EMPTY;
                    break;
                case TYPE:
                    client.decodedType = FIELD_VALUE_EMPTY;
                    break;
                case DATA:
                    client.doEncodeDataFragment(traceId, authorization, budgetId, reserved);
                    client.decodedData = FIELD_DATA_EMPTY;
                    break;
                default:
                    break;
                }

                client.decodedFieldName = fieldName;
                client.decoder = decodeFieldColon;
            }
        }

        return progress;
    }

    private int decodeFieldColon(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length > 0)
        {
            if (buffer.getByte(progress) == LINE_COLON_BYTE)
            {
                progress++;

                client.decoder = decodeFieldSpace;
            }
            else
            {
                client.decoder = decodeLineEnding;
            }
        }

        return progress;
    }

    private int decodeFieldSpace(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length > 0)
        {
            if (buffer.getByte(progress) == LINE_SPACE_BYTE)
            {
                progress++;
            }

            switch (client.decodedFieldName)
            {
            case DATA:
                client.decoder = decodeFieldDataValue;
                break;
            case ID:
            case TYPE:
                client.decoder = decodeFieldValue;
                break;
            case IGNORE:
                client.decoder = decodeIgnoreLine;
                break;
            }
        }

        return progress;
    }

    private int decodeFieldValue(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length != 0)
        {
            int limitOfField = indexOfEndOfLine(buffer, progress, limit);

            if (limitOfField != -1)
            {
                DirectBufferEx value = valueRO;
                value.wrap(buffer, progress, limitOfField - progress);

                switch (client.decodedFieldName)
                {
                case ID:
                    client.decodedId = valueIdRW
                        .set(buffer, progress, limitOfField - progress)
                        .build();
                    break;
                case TYPE:
                    client.decodedType = valueTypeRW
                        .set(buffer, progress, limitOfField - progress)
                        .build();
                    break;
                default:
                    break;
                }

                progress = limitOfField;

                client.decoder = decodeLineEnding;
            }
        }

        return progress;
    }

    private int decodeFieldDataValue(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length != 0)
        {
            final int replyNoAck = (int) (client.delegate.replySeq - client.delegate.replyAck);
            final int lengthMax = client.delegate.replyMax - replyNoAck - client.delegate.replyPad;

            if (lengthMax != 0)
            {
                int limitMax = Math.min(progress + lengthMax, limit);
                int endOfLineAt = indexOfEndOfLine(buffer, progress, limitMax);
                int limitOfData = endOfLineAt != -1 ? endOfLineAt : limitMax;

                client.decodedDataLines += Math.min(endOfLineAt, 0) + 1;
                client.decodedData = valueDataRO.wrap(buffer, progress, limitOfData);

                progress = limitOfData;

                if (endOfLineAt == -1)
                {
                    client.doEncodeDataFragment(traceId, authorization, budgetId, reserved);
                }
                else
                {
                    client.decoder = decodeLineEnding;
                }
            }
        }

        return progress;
    }

    private int decodeIgnoreLine(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length != 0)
        {
            while (!EOL_MATCHER.test(buffer.getByte(progress)))
            {
                progress++;
            }
        }

        return progress;
    }

    private static int indexOfEndOfLine(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return indexOfByte(buffer, offset, limit, EOL_MATCHER);
    }

    private static int limitOfFieldName(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return indexOfByte(buffer, offset, limit, EOF_MATCHER);
    }

    private static int indexOfByte(
        DirectBufferEx buffer,
        int offset,
        int limit,
        IntPredicate matcher)
    {
        for (int cursor = offset; cursor < limit; cursor++)
        {
            final int ch = buffer.getByte(cursor);

            if (matcher.test(ch))
            {
                return cursor;
            }
        }

        return -1;
    }

    private static boolean matchAllBytes(
        DirectBufferEx buffer,
        int offset,
        int limit,
        byte[] bytes)
    {
        boolean matchAll = true;

        for (int cursor = offset; matchAll && cursor < limit; cursor++)
        {
            matchAll &= buffer.getByte(cursor) == bytes[cursor - offset];
        }

        return matchAll;
    }

    private enum HttpSseFieldName
    {
        DATA,
        ID,
        TYPE,
        IGNORE
    }
}
