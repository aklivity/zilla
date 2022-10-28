/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.sse.internal.stream;

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.unmodifiableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.sse.internal.SseBinding;
import io.aklivity.zilla.runtime.binding.sse.internal.SseConfiguration;
import io.aklivity.zilla.runtime.binding.sse.internal.config.SseBindingConfig;
import io.aklivity.zilla.runtime.binding.sse.internal.config.SseRouteConfig;
import io.aklivity.zilla.runtime.binding.sse.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.sse.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.SseBeginExFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.SseDataExFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public class SseClientFactory implements SseStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";

    private static final String8FW HTTP_HEADER_METHOD = new String8FW(":method");
    private static final String8FW HTTP_HEADER_SCHEME = new String8FW(":scheme");
    private static final String8FW HTTP_HEADER_AUTHORITY = new String8FW(":authority");
    private static final String8FW HTTP_HEADER_PATH = new String8FW(":path");
    private static final String8FW HTTP_HEADER_ACCEPT = new String8FW("accept");
    private static final String8FW HTTP_HEADER_LAST_EVENT_ID = new String8FW("last-event-id");
    private static final String8FW HTTP_HEADER_STATUS = new String8FW(":status");

    private static final String16FW HTTP_HEADER_METHOD_GET = new String16FW("GET");
    private static final String16FW HTTP_HEADER_ACCEPT_TEXT_EVENT_STREAM = new String16FW("text/event-stream");
    private static final String16FW HTTP_HEADER_STATUS_200 = new String16FW("200");

    private static final byte[] STREAM_BOM_BYTES = "\ufeff".getBytes(UTF_8);
    private static final int STREAM_BOM_BYTES_LENGTH = STREAM_BOM_BYTES.length;

    private static final int LINE_CR_BYTE = 0x0d;
    private static final int LINE_LF_BYTE = 0x0a;
    private static final int LINE_COLON_BYTE = 0x3a;
    private static final int LINE_SPACE_BYTE = 0x20;

    private static final DirectBuffer FIELD_NAME_TYPE_BYTES = new UnsafeBuffer("event".getBytes(UTF_8));
    private static final DirectBuffer FIELD_NAME_ID_BYTES = new UnsafeBuffer("id".getBytes(UTF_8));
    private static final DirectBuffer FIELD_NAME_DATA_BYTES = new UnsafeBuffer("data".getBytes(UTF_8));

    private static final IntPredicate EOL_MATCHER = v -> v == LINE_CR_BYTE || v == LINE_LF_BYTE;
    private static final IntPredicate COLON_MATCHER = v -> v == LINE_COLON_BYTE;
    private static final IntPredicate EOF_MATCHER = COLON_MATCHER.or(EOL_MATCHER);

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);

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

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final SseBeginExFW sseBeginExRO = new SseBeginExFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();

    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final SseDataExFW.Builder sseDataExRW = new SseDataExFW.Builder();

    private final DirectBuffer nameRO = new UnsafeBuffer(0L, 0);
    private final DirectBuffer valueRO = new UnsafeBuffer(0L, 0);

    private final OctetsFW lineDataRO = new OctetsFW();
    private final OctetsFW valueDataRO = new OctetsFW();

    private final String8FW.Builder valueIdRW = new String8FW.Builder().wrap(new UnsafeBuffer(new byte[256]), 0, 256);
    private final String8FW.Builder valueTypeRW = new String8FW.Builder().wrap(new UnsafeBuffer(new byte[256]), 0, 256);

    private final Map<DirectBuffer, SseFieldName> decodeableFieldNames;
    {
        final HashMap<DirectBuffer, SseFieldName> fieldNames = new HashMap<>();
        fieldNames.put(FIELD_NAME_DATA_BYTES, SseFieldName.DATA);
        fieldNames.put(FIELD_NAME_ID_BYTES, SseFieldName.ID);
        fieldNames.put(FIELD_NAME_TYPE_BYTES, SseFieldName.TYPE);
        decodeableFieldNames = unmodifiableMap(fieldNames);
    }

    private final SseClientDecoder decodeBom = this::decodeBom;
    private final SseClientDecoder decodeEvent = this::decodeEvent;
    private final SseClientDecoder decodeLine = this::decodeLine;
    private final SseClientDecoder decodeLineEnding = this::decodeLineEnding;
    private final SseClientDecoder decodeLineEndingAfterCR = this::decodeLineEndingAfterCR;
    private final SseClientDecoder decodeLineEnded = this::decodeLineEnded;
    private final SseClientDecoder decodeIgnoreLine = this::decodeIgnoreLine;
    private final SseClientDecoder decodeFieldName = this::decodeFieldName;
    private final SseClientDecoder decodeFieldColon = this::decodeFieldColon;
    private final SseClientDecoder decodeFieldSpace = this::decodeFieldSpace;
    private final SseClientDecoder decodeFieldValue = this::decodeFieldValue;
    private final SseClientDecoder decodeFieldDataValue = this::decodeFieldDataValue;
    private final SseClientDecoder decodeEventEnding = this::decodeEventEnding;

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer lineBuffer;
    private final BufferPool decodePool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int sseTypeId;
    private final int decodeMax;

    private final Long2ObjectHashMap<SseBindingConfig> bindings;

    public SseClientFactory(
        SseConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.lineBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.sseTypeId = context.supplyTypeId(SseBinding.NAME);
        this.decodeMax = decodePool.slotCapacity();
        this.bindings = new Long2ObjectHashMap<>();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        SseBindingConfig sseBinding = new SseBindingConfig(binding);
        bindings.put(binding.id, sseBinding);
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer application)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final OctetsFW extension = begin.extension();
        final SseBeginExFW sseBeginEx = extension.get(sseBeginExRO::tryWrap);

        final long routeId = begin.routeId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final String16FW path = sseBeginEx.path();

        MessageConsumer newStream = null;

        final SseBindingConfig binding = bindings.get(routeId);
        final SseRouteConfig resolved = binding != null ?  binding.resolve(authorization, path.asString()) : null;

        if (resolved != null)
        {
            newStream = new SseClient(
                application,
                routeId,
                initialId,
                resolved.id)::onAppMessage;

        }

        return newStream;
    }

    private final class SseClient
    {
        private final MessageConsumer application;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final HttpClient delegate;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private int state;

        private SseClient(
            MessageConsumer application,
            long routeId,
            long initialId,
            long resolvedId)
        {
            this.application = application;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.delegate = new HttpClient(resolvedId, this);
        }

        private void onAppMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onAppBegin(begin);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onAppEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAppAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAppWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAppReset(reset);
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final long affinity = begin.affinity();
            final OctetsFW extension = begin.extension();

            final SseBeginExFW sseBeginEx = extension.get(sseBeginExRO::tryWrap);
            final String16FW scheme = sseBeginEx.scheme();
            final String16FW authority = sseBeginEx.authority();
            final String16FW path = sseBeginEx.path();
            final String8FW lastId = sseBeginEx.lastId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            initialMax = maximum;
            state = SseState.openingInitial(state);

            assert initialAck <= initialSeq;

            delegate.doNetBegin(traceId, affinity, acknowledge, scheme, authority, path, lastId);
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = SseState.closedInitial(state);

            delegate.doNetEnd(traceId, authorization);
            delegate.cleanupNet(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = SseState.closedInitial(state);

            delegate.cleanupNet(traceId, authorization);
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = padding;
            state = SseState.openedReply(state);

            assert replyAck <= replySeq;

            delegate.decodeNet(traceId, authorization, budgetId);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = SseState.closedReply(state);

            delegate.doNetReset(traceId, authorization);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            if (!SseState.replyOpening(state))
            {
                replySeq = delegate.replySeq;
                replyAck = delegate.replyAck;
                replyMax = delegate.replyMax;
                state = SseState.openingReply(state);

                doBegin(application, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, affinity);
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

            doData(application, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, flags, reserved, payload, extension);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (!SseState.replyClosed(state))
            {
                state = SseState.closedReply(state);

                doAbort(application, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization);
            }
        }

        private void doAppFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            doFlush(application, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, reserved);
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!SseState.replyClosed(state))
            {
                state = SseState.closedReply(state);

                doEnd(application, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization);
            }
        }

        private void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            state = SseState.openedInitial(state);

            doWindow(application, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding);
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (!SseState.initialClosed(state))
            {
                state = SseState.closedInitial(state);

                doReset(application, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void cleanupApp(
            long traceId,
            long authorization)
        {
            doAppReset(traceId, authorization);
            doAppBegin(traceId, authorization, 0L);
            doAppAbort(traceId, authorization);
        }
    }

    private final class HttpClient
    {
        private final SseClient delegate;

        private MessageConsumer network;
        private final long routeId;
        private final long initialId;
        private final long replyId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;

        private SseClientDecoder decoder;

        private int decodableLineAt;
        private int decodedLineEndBytes;
        private SseFieldName decodedFieldName;
        private int decodedDataLines;
        private int decodedDataFlags;

        private String8FW decodedId = FIELD_VALUE_NULL;
        private String8FW decodedType = FIELD_VALUE_NULL;
        private OctetsFW decodedData;

        private HttpClient(
            long routeId,
            SseClient delegate)
        {
            this.routeId = routeId;
            this.initialId = supplyInitialId.applyAsLong(routeId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.delegate = delegate;
            this.decoder = decodeBom;
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            long affinity,
            String16FW scheme,
            String16FW authority,
            String16FW path,
            String8FW lastId)
        {
            initialSeq = delegate.initialSeq;
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;
            state = SseState.openingInitial(state);

            network = newHttpStream(this::onNetMessage, routeId, initialId,
                    initialSeq, initialAck, initialMax, traceId, authorization, affinity,
                    scheme, authority, path, lastId);
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!SseState.initialClosed(state))
            {
                state = SseState.closedInitial(state);

                doEnd(network, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            if (!SseState.initialClosed(state))
            {
                state = SseState.closedInitial(state);

                doAbort(network, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            state = SseState.openedReply(state);

            doWindow(network, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, padding);
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            if (!SseState.replyClosed(state))
            {
                state = SseState.closedReply(state);

                doReset(network, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization);
            }

            cleanupDecodeSlot();
        }

        private void onNetMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onNetData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onNetFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();
            final int maximum = begin.maximum();
            final OctetsFW extension = begin.extension();
            final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

            String16FW status = HTTP_HEADER_STATUS_200;
            if (httpBeginEx != null)
            {
                final Array32FW<HttpHeaderFW> headers = httpBeginEx.headers();
                final HttpHeaderFW statusHeader = headers.matchFirst(h -> HTTP_HEADER_STATUS.equals(h.name()));

                if (statusHeader != null)
                {
                    status = statusHeader.value();
                }
            }

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            replyMax = maximum;
            state = SseState.openingReply(state);

            assert replyAck <= replySeq;

            if (!HTTP_HEADER_STATUS_200.equals(status))
            {
                delegate.doAppReset(traceId, authorization);
                doNetAbort(traceId, authorization);
            }
            else
            {
                delegate.doAppBegin(traceId, authorization, affinity);
            }
        }

        private void onNetData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + data.reserved();

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
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

                    final MutableDirectBuffer buffer = decodePool.buffer(decodeSlot);
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
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            state = SseState.closedReply(state);

            cleanupDecodeSlot();

            delegate.doAppEnd(traceId, authorization);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            state = SseState.closedReply(state);

            cleanupDecodeSlot();

            delegate.doAppAbort(traceId, authorization);
        }

        private void onNetFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + flush.reserved();

            assert replyAck <= replySeq;

            delegate.doAppFlush(traceId, authorization, budgetId, reserved);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = SseState.closedInitial(state);

            delegate.doAppReset(traceId, authorization);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;

            initialAck = acknowledge;
            initialMax = maximum;
            state = SseState.openedInitial(state);

            assert initialAck <= initialMax;

            delegate.doAppWindow(traceId, authorization, budgetId, padding);

            doNetEnd(traceId, authorization);
        }

        private void cleanupNet(
            long traceId,
            long authorization)
        {
            doNetReset(traceId, authorization);
            doNetAbort(traceId, authorization);

            delegate.cleanupApp(traceId, authorization);
        }

        private void decodeNet(
            long traceId,
            long authorization,
            long budgetId)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer buffer = decodePool.buffer(decodeSlot);
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
            MutableDirectBuffer buffer,
            int offset,
            int limit)
        {
            SseClientDecoder previous = null;
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
                    final MutableDirectBuffer decodeBuffer = decodePool.buffer(decodeSlot);
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

                if (SseState.replyClosing(state))
                {
                    delegate.doAppEnd(traceId, authorization);
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

            if (newReplyAck > replyAck || minReplyMax > replyMax || !SseState.replyOpened(state))
            {
                replyAck = newReplyAck;
                assert replyAck <= replySeq;

                replyMax = minReplyMax;

                doNetWindow(traceId, authorization, budgetId, minReplyPad);
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
            final Flyweight sseDataEx =
                id != FIELD_VALUE_NULL ||
                type != FIELD_VALUE_NULL
                    ? sseDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                            .typeId(sseTypeId)
                            .id(id)
                            .type(type)
                            .build()
                    : EMPTY_OCTETS;

            if (decodedDataLines > 1 && decodedData != null)
            {
                lineBuffer.putByte(0, (byte) LINE_LF_BYTE);
                lineBuffer.putBytes(1, data.buffer(), data.offset(), data.sizeof());
                data = lineDataRO.wrap(lineBuffer, 0, 1 + data.sizeof());
            }

            delegate.doAppData(traceId, authorization, budgetId, flags, data, sseDataEx);
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
                    final int flags = 0x02 & ~decodedDataFlags;

                    onDecodedEventFragment(traceId, authorization, budgetId, flags, id, type, data);

                    decodedType = FIELD_VALUE_NULL;
                    decodedId = FIELD_VALUE_NULL;
                    decodedData = null;
                    decodedDataFlags |= 0x02; // INIT
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

    private MessageConsumer newHttpStream(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        String16FW scheme,
        String16FW authority,
        String16FW path,
        String8FW lastId)
    {
        final HttpBeginExFW httpBeginEx = httpBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(httpTypeId)
                .headers(hs ->
                {
                    hs.item(h -> h
                        .name(HTTP_HEADER_METHOD)
                        .value(HTTP_HEADER_METHOD_GET));
                    hs.item(h -> h
                        .name(HTTP_HEADER_SCHEME)
                        .value(scheme));
                    hs.item(h -> h
                        .name(HTTP_HEADER_AUTHORITY)
                        .value(authority));
                    hs.item(h -> h
                        .name(HTTP_HEADER_PATH)
                        .value(path));
                    hs.item(h -> h
                        .name(HTTP_HEADER_ACCEPT)
                        .value(HTTP_HEADER_ACCEPT_TEXT_EVENT_STREAM));

                    final DirectBuffer lastIdBuf = lastId.value();
                    if (lastIdBuf != null)
                    {
                        hs.item(h -> h
                            .name(HTTP_HEADER_LAST_EVENT_ID)
                            .value(lastIdBuf, 0, lastIdBuf.capacity()));
                    }
                })
                .build();

        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(httpBeginEx.buffer(), httpBeginEx.offset(), httpBeginEx.sizeof())
                .build();

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private void doBegin(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer receiver,
        long routeId,
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
        final DataFW frame = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .flags(flags)
                .budgetId(budgetId)
                .reserved(reserved)
                .payload(payload)
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

        receiver.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());
    }

    private void doEnd(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .build();

        sender.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doAbort(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .build();

        sender.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doFlush(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int reserved)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .budgetId(budgetId)
                .reserved(reserved)
                .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doWindow(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int padding)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .budgetId(budgetId)
                .padding(padding)
                .build();

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    @FunctionalInterface
    private interface SseClientDecoder
    {
        int decode(
            HttpClient client,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
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
        DirectBuffer buffer,
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
        DirectBuffer buffer,
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
        DirectBuffer buffer,
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
        DirectBuffer buffer,
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
                client.decodedDataFlags != 0x03 &&
                client.decodedDataFlags != 0x00
                    ? FIELD_DATA_EMPTY
                    : client.decodedData;

            if (id != FIELD_VALUE_NULL ||
                type != FIELD_VALUE_NULL ||
                data != null)
            {
                final int flags = 0x03 & ~client.decodedDataFlags;

                client.onDecodedEventFragment(traceId, authorization, budgetId, flags, id, type, data);

                client.decodedType = FIELD_VALUE_NULL;
                client.decodedId = FIELD_VALUE_NULL;
                client.decodedData = null;
                client.decodedDataLines = 0;
                client.decodedDataFlags |= 0x01; // FIN
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
        DirectBuffer buffer,
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
        DirectBuffer buffer,
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
        DirectBuffer buffer,
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
        DirectBuffer buffer,
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
                DirectBuffer name = nameRO;
                name.wrap(buffer, progress, limitOfFieldName - progress);

                final SseFieldName fieldName = decodeableFieldNames.getOrDefault(name, SseFieldName.IGNORE);

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
        DirectBuffer buffer,
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
        DirectBuffer buffer,
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
        DirectBuffer buffer,
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
                DirectBuffer value = valueRO;
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
        DirectBuffer buffer,
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
        DirectBuffer buffer,
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
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        return indexOfByte(buffer, offset, limit, EOL_MATCHER);
    }

    private static int limitOfFieldName(
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        return indexOfByte(buffer, offset, limit, EOF_MATCHER);
    }

    private static int indexOfByte(
        DirectBuffer buffer,
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
        DirectBuffer buffer,
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

    private enum SseFieldName
    {
        DATA,
        ID,
        TYPE,
        IGNORE
    }
}
