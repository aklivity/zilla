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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.kafka.internal.types.ProxyAddressProtocol.STREAM;
import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_BUDGET_ID;
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaServerConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.events.KafkaEventContext;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.RequestHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.ResponseErrorCodeFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.ResponseHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.api_versions.ApiVersionsResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.api_versions.ApiVersionsResponseKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaApiRequestBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class KafkaClientApiFactory implements BindingHandler
{
    private static final int ERROR_NONE = 0;
    private static final int ERROR_UNSUPPORTED_VERSION = 35;
    private static final int RESPONSE_HEADER_FIELD_SIZE_CORRELATION_ID = 4;
    private static final int RESPONSE_ERROR_CODE_SIZE = 2;
    private static final int NO_API_VERSION_RANGE = -1;
    private static final short API_VERSIONS_API_KEY = 18;
    private static final short API_VERSIONS_API_VERSION = 0;
    private static final int SIGNAL_RECONNECT = 1;

    private static final DirectBufferEx EMPTY_BUFFER = new UnsafeBufferEx();
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final SignalFW signalRO = new SignalFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ProxyBeginExFW.Builder proxyBeginExRW = new ProxyBeginExFW.Builder();
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaResetExFW.Builder kafkaResetExRW = new KafkaResetExFW.Builder();

    private final RequestHeaderFW.Builder requestHeaderRW = new RequestHeaderFW.Builder();
    private final ResponseHeaderFW responseHeaderRO = new ResponseHeaderFW();
    private final ResponseErrorCodeFW responseErrorCodeRO = new ResponseErrorCodeFW();
    private final ApiVersionsResponseFW apiVersionsResponseRO = new ApiVersionsResponseFW();
    private final ApiVersionsResponseKeyFW apiVersionsResponseKeyRO = new ApiVersionsResponseKeyFW();

    private final KafkaApiClientDecoder decodeResponseHeader = this::decodeResponseHeader;
    private final KafkaApiClientDecoder decodeResponseBody = this::decodeResponseBody;
    private final KafkaApiClientDecoder decodeResponseErrorCode = this::decodeResponseErrorCode;
    private final KafkaApiClientDecoder decodeApiVersionsResponse = this::decodeApiVersionsResponse;
    private final KafkaApiClientDecoder decodeApiVersionsResponseKeys = this::decodeApiVersionsResponseKeys;
    private final KafkaApiClientDecoder decodeApiVersionsResponseKey = this::decodeApiVersionsResponseKey;
    private final KafkaApiClientDecoder decodeIgnoreAll = this::decodeIgnoreAll;
    private final KafkaApiClientDecoder decodeReject = this::decodeReject;

    private final int kafkaTypeId;
    private final int proxyTypeId;
    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final Signaler signaler;
    private final BindingHandler streamFactory;
    private final LongFunction<KafkaBindingConfig> supplyBinding;
    private final LongFunction<BudgetDebitor> supplyDebitor;
    private final Long2ObjectHashMap<KafkaApiClient> clientsByAffinity;
    private final KafkaEventContext event;
    private final boolean apiVersionsEnabled;
    private final int reconnectDelay;

    public KafkaClientApiFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding,
        LongFunction<BudgetDebitor> supplyDebitor,
        Signaler signaler,
        BindingHandler streamFactory)
    {
        this.apiVersionsEnabled = config.clientApiVersions();
        this.reconnectDelay = config.clientReconnectDelay();
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.proxyTypeId = context.supplyTypeId("proxy");
        this.signaler = signaler;
        this.streamFactory = streamFactory;
        this.writeBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.event = new KafkaEventContext(context);
        this.supplyBinding = supplyBinding;
        this.supplyDebitor = supplyDebitor;
        this.clientsByAffinity = new Long2ObjectHashMap<>();
    }

    @FunctionalInterface
    private interface KafkaApiClientDecoder
    {
        int decode(
            KafkaApiClient client,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBufferEx buffer,
            int offset,
            int progress,
            int limit);
    }

    private int decodeResponseHeader(
        KafkaApiClient client,
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

        decode:
        if (length != 0)
        {
            final ResponseHeaderFW responseHeader = responseHeaderRO.tryWrap(buffer, progress, limit);
            if (responseHeader == null)
            {
                break decode;
            }

            final int correlationIdLimit = responseHeader.limit() + RESPONSE_HEADER_FIELD_SIZE_CORRELATION_ID;
            if (correlationIdLimit > limit)
            {
                break decode;
            }

            progress = correlationIdLimit;

            final int responseBodyLength = responseHeader.length() - RESPONSE_HEADER_FIELD_SIZE_CORRELATION_ID;

            client.onDecodeResponseHeader(traceId, authorization, responseBodyLength);
        }

        return progress;
    }

    private int decodeResponseErrorCode(
        KafkaApiClient client,
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

        decode:
        if (length != 0)
        {
            final ResponseErrorCodeFW responseErrorCode = responseErrorCodeRO.tryWrap(buffer, progress, limit);
            if (responseErrorCode == null)
            {
                break decode;
            }

            progress = responseErrorCode.limit();

            client.onDecodeResponseErrorCode(traceId, responseErrorCode.errorCode());
        }

        return progress;
    }

    private int decodeResponseBody(
        KafkaApiClient client,
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
            final int forwarded = client.onDecodeResponseBody(traceId, authorization, budgetId, buffer, progress, limit);
            progress += forwarded;
        }

        return progress;
    }

    private int decodeApiVersionsResponse(
        KafkaApiClient client,
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

        decode:
        if (length != 0)
        {
            final ResponseHeaderFW responseHeader = responseHeaderRO.tryWrap(buffer, progress, limit);
            if (responseHeader == null)
            {
                break decode;
            }

            final int correlationIdLimit = responseHeader.limit() + RESPONSE_HEADER_FIELD_SIZE_CORRELATION_ID;
            if (correlationIdLimit > limit)
            {
                break decode;
            }

            final ApiVersionsResponseFW apiVersionsResponse =
                apiVersionsResponseRO.tryWrap(buffer, correlationIdLimit, limit);
            if (apiVersionsResponse == null)
            {
                break decode;
            }

            progress = apiVersionsResponse.limit();

            final int responseBodyLength = responseHeader.length() - RESPONSE_HEADER_FIELD_SIZE_CORRELATION_ID;

            client.onDecodeApiVersionsResponse(traceId, buffer, correlationIdLimit, apiVersionsResponse.limit(),
                responseBodyLength, apiVersionsResponse.errorCode(), apiVersionsResponse.apiKeyCount());
        }

        return progress;
    }

    private int decodeApiVersionsResponseKeys(
        KafkaApiClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        client.onDecodeApiVersionsResponseKeys(traceId);

        return progress;
    }

    private int decodeApiVersionsResponseKey(
        KafkaApiClient client,
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

        decode:
        if (length != 0)
        {
            final int keyOffset = progress;
            final ApiVersionsResponseKeyFW apiVersionsResponseKey = apiVersionsResponseKeyRO.tryWrap(buffer, progress, limit);
            if (apiVersionsResponseKey == null)
            {
                break decode;
            }

            progress = apiVersionsResponseKey.limit();

            client.onDecodeApiVersionsResponseKey(traceId, buffer, keyOffset, progress,
                apiVersionsResponseKey.apiKey(), apiVersionsResponseKey.minVersion(), apiVersionsResponseKey.maxVersion());
        }

        return progress;
    }

    private int decodeReject(
        KafkaApiClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        client.cleanupNet(traceId);
        client.decoder = decodeIgnoreAll;
        return limit;
    }

    private int decodeIgnoreAll(
        KafkaApiClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return limit;
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
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long affinity = begin.affinity();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();
        final ExtensionFW beginEx = extensionRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
        final KafkaBeginExFW kafkaBeginEx = beginEx != null && beginEx.typeId() == kafkaTypeId ?
                kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit()) : null;

        assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_API_REQUEST;
        final KafkaApiRequestBeginExFW kafkaApiRequestBeginEx = kafkaBeginEx.apiRequest();

        MessageConsumer newStream = null;

        final KafkaBindingConfig binding = supplyBinding.apply(routedId);
        final KafkaRouteConfig resolved = binding != null
            ? binding.resolve(authorization, null)
            : null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;
            final short api = kafkaApiRequestBeginEx.api();
            final short version = kafkaApiRequestBeginEx.version();
            final int requestLength = kafkaApiRequestBeginEx.length();
            final String16FW clientId = kafkaApiRequestBeginEx.clientId().length() != -1
                ? new String16FW(kafkaApiRequestBeginEx.clientId().asString())
                : null;

            final KafkaApiClient client = clientsByAffinity.computeIfAbsent(affinity,
                a -> new KafkaApiClient(routedId, resolvedId, a, binding.servers()));

            newStream = new KafkaApiStream(
                    application,
                    originId,
                    routedId,
                    initialId,
                    affinity,
                    client,
                    api,
                    version,
                    clientId,
                    requestLength)::onApp;
        }

        return newStream;
    }

    private MessageConsumer newStream(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Consumer<OctetsFW.Builder> extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(extension)
                .build();

        final MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private final class KafkaApiStream
    {
        private final MessageConsumer application;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final KafkaApiClient client;
        private final short api;
        private final short version;
        private final String16FW clientId;
        private final int requestLength;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private long authorization;
        private int requestBytesSent;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;

        KafkaApiStream(
            MessageConsumer application,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            KafkaApiClient client,
            short api,
            short version,
            String16FW clientId,
            int requestLength)
        {
            this.application = application;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.client = client;
            this.api = api;
            this.version = version;
            this.clientId = clientId;
            this.requestLength = requestLength;
        }

        private void onApp(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onAppBegin(begin);
                break;
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
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAppWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAppReset(reset);
                break;
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            this.authorization = begin.authorization();
            state = KafkaState.openingInitial(state);

            client.enqueue(this, traceId, authorization);
        }

        private void onAppData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final OctetsFW payload = data.payload();

            requestBytesSent += payload.sizeof();

            client.doNetData(traceId, budgetId, payload.buffer(), payload.offset(), payload.limit());
        }

        private void onAppEnd(
            EndFW end)
        {
            state = KafkaState.closedInitial(state);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedInitial(state);

            if (!(client.pending.peek() == this && client.requestInFlight))
            {
                client.pending.remove(this);
            }

            client.cleanupNet(traceId);
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            this.replyAck = acknowledge;
            this.replyMax = maximum;
            this.replyPad = padding;

            state = KafkaState.openedReply(state);

            assert replyAck <= replySeq;

            flushEncodeSlot(window.traceId());
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedReply(state);

            if (!(client.pending.peek() == this && client.requestInFlight))
            {
                client.pending.remove(this);
            }

            client.cleanupNet(traceId);
        }

        private void doAppWindow(
            long traceId,
            long budgetId,
            int minInitialNoAck,
            int minInitialPad,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(initialSeq - minInitialNoAck, initialAck);

            if (newInitialAck > initialAck || minInitialMax > initialMax || !KafkaState.initialOpened(state))
            {
                initialAck = newInitialAck;
                assert initialAck <= initialSeq;

                initialMax = minInitialMax;

                state = KafkaState.openedInitial(state);

                doWindow(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, budgetId, minInitialPad);
            }
        }

        private void doAppBegin(
            long traceId,
            int responseLength)
        {
            if (!KafkaState.replyOpening(state))
            {
                state = KafkaState.openingReply(state);

                doBegin(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity,
                    ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                                                            .typeId(kafkaTypeId)
                                                            .apiResponse(ar -> ar
                                                                .length(responseLength)
                                                                .version(version))
                                                            .build()
                                                            .sizeof()));
            }
        }

        private void doAppData(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                encodeBuffer.putBytes(encodeSlotOffset, buffer, offset, limit - offset);
                encodeSlotOffset += limit - offset;

                buffer = encodeBuffer;
                offset = 0;
                limit = encodeSlotOffset;
            }

            encodeAppData(traceId, buffer, offset, limit);
        }

        private void encodeAppData(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int length = limit - offset;
            final int replyBudget = Math.max(replyMax - (int) (replySeq - replyAck), 0);
            final int reserved = Math.max(Math.min(length + replyPad, replyBudget), 0);

            flush:
            if (reserved > 0)
            {
                if (reserved < replyPad || reserved == replyPad && length > 0)
                {
                    break flush;
                }

                doData(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, NO_BUDGET_ID, reserved, buffer, offset, length, EMPTY_EXTENSION);

                replySeq += reserved;

                assert replyAck <= replySeq;
            }

            final int flushed = Math.max(reserved - replyPad, 0);
            final int remaining = length - flushed;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(replyId);
                }

                if (encodeSlot == NO_SLOT)
                {
                    client.cleanupNet(traceId);
                }
                else
                {
                    final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                    encodeBuffer.putBytes(0, buffer, offset + flushed, remaining);
                    encodeSlotOffset = remaining;
                }
            }
            else
            {
                cleanupEncodeSlot();

                if (KafkaState.replyClosing(state) && !KafkaState.replyClosed(state))
                {
                    doAppEnd(traceId);
                }
            }
        }

        private void flushEncodeSlot(
            long traceId)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx buffer = encodePool.buffer(encodeSlot);
                final int limit = encodeSlotOffset;

                encodeAppData(traceId, buffer, 0, limit);
            }
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
            }
        }

        private void doAppEnd(
            long traceId)
        {
            if (encodeSlot != NO_SLOT)
            {
                state = KafkaState.closingReply(state);
            }
            else if (!KafkaState.replyClosed(state))
            {
                state = KafkaState.closedReply(state);
                doEnd(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, EMPTY_EXTENSION);
            }
        }

        private void doAppAbort(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                state = KafkaState.closedReply(state);
                doAbort(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
            }
        }

        private void doAppReset(
            long traceId,
            Flyweight extension)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                state = KafkaState.closedInitial(state);

                doReset(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, extension);
            }
        }

        private void cleanupApp(
            long traceId,
            int error)
        {
            final KafkaResetExFW kafkaResetEx = kafkaResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .error(error)
                .build();

            cleanupApp(traceId, kafkaResetEx);
        }

        private void cleanupApp(
            long traceId,
            Flyweight extension)
        {
            doAppReset(traceId, extension);
            doAppAbort(traceId);

            cleanupEncodeSlot();
        }
    }

    private final class KafkaApiClient
    {
        private final long originId;
        private final long routedId;
        private final long affinity;
        private long initialId;
        private long replyId;
        private final KafkaServerConfig server;
        private final Deque<KafkaApiStream> pending;

        private MessageConsumer network;
        private int state;
        private long authorization;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private long initialBudgetId = NO_BUDGET_ID;
        private long initialDebIndex = NO_DEBITOR_INDEX;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;

        private int nextCorrelationId;
        private int responseBytesRemaining;
        private boolean attached;
        private boolean requestInFlight;

        private long reconnectAt = NO_CANCEL_ID;
        private int reconnectAttempt;

        private boolean apiVersionsResolved;
        private boolean apiVersionsRequestExplicit;
        private final Int2IntHashMap apiVersionRangeByApiKey;
        private int apiVersionKeysRemaining;

        private BudgetDebitor initialDeb;
        private KafkaApiClientDecoder decoder;

        KafkaApiClient(
            long originId,
            long routedId,
            long affinity,
            List<KafkaServerConfig> servers)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.affinity = affinity;
            this.server = servers != null && !servers.isEmpty() ? servers.get(0) : null;
            this.pending = new ArrayDeque<>();
            this.apiVersionRangeByApiKey = new Int2IntHashMap(NO_API_VERSION_RANGE);
            this.decoder = decodeReject;
        }

        private void enqueue(
            KafkaApiStream stream,
            long traceId,
            long authorization)
        {
            final boolean wasEmpty = pending.isEmpty();
            pending.offer(stream);

            if (wasEmpty)
            {
                if (!KafkaState.initialOpening(state))
                {
                    doNetBegin(traceId, authorization, affinity);
                }
                else if (KafkaState.replyOpened(state))
                {
                    doEncodeRequest(traceId);
                }
            }
        }

        private void doEncodeRequest(
            long traceId)
        {
            if (!apiVersionsEnabled)
            {
                final KafkaApiStream head = pending.peek();
                if (head != null)
                {
                    attached = true;
                    head.doAppWindow(traceId, 0L, 0, 0, decodePool.slotCapacity());
                    doEncodeRequestHeader(head, traceId, initialBudgetId);
                }
                return;
            }

            if (!apiVersionsResolved)
            {
                final KafkaApiStream head = pending.peek();
                if (head != null)
                {
                    attached = true;
                    if (head.api == API_VERSIONS_API_KEY && head.version == API_VERSIONS_API_VERSION)
                    {
                        apiVersionsRequestExplicit = true;
                        head.doAppWindow(traceId, 0L, 0, 0, decodePool.slotCapacity());
                        doEncodeApiVersionsRequestExplicit(head, traceId, initialBudgetId);
                    }
                    else
                    {
                        apiVersionsRequestExplicit = false;
                        doEncodeApiVersionsRequest(traceId, initialBudgetId);
                    }
                }
                return;
            }

            KafkaApiStream head;
            while ((head = pending.peek()) != null)
            {
                if (apiVersionSupported(head.api, head.version))
                {
                    attached = true;
                    head.doAppWindow(traceId, 0L, 0, 0, decodePool.slotCapacity());
                    doEncodeRequestHeader(head, traceId, initialBudgetId);
                    break;
                }

                pending.poll();
                event.apiVersionRejected(traceId, routedId, head.api, head.version);
                head.cleanupApp(traceId, ERROR_UNSUPPORTED_VERSION);
            }
        }

        private boolean apiVersionSupported(
            short api,
            short version)
        {
            final boolean supported;
            if (apiVersionRangeByApiKey.containsKey(api))
            {
                final int range = apiVersionRangeByApiKey.get(api);
                final short minVersion = (short)(range >> 16);
                final short maxVersion = (short)(range & 0xFFFF);
                supported = version >= minVersion && version <= maxVersion;
            }
            else
            {
                supported = false;
            }

            return supported;
        }

        private void doEncodeApiVersionsRequest(
            long traceId,
            long budgetId)
        {
            final MutableDirectBufferEx encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader = requestHeaderRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .length(0)
                .apiKey(API_VERSIONS_API_KEY)
                .apiVersion(API_VERSIONS_API_VERSION)
                .correlationId(0)
                .clientId(pending.peek().clientId)
                .build();

            encodeProgress = requestHeader.limit();

            final int correlationId = nextCorrelationId++;
            final int requestSize = encodeProgress - encodeOffset - RequestHeaderFW.FIELD_OFFSET_API_KEY;

            requestHeaderRW.wrap(encodeBuffer, requestHeader.offset(), requestHeader.limit())
                .length(requestSize)
                .apiKey(requestHeader.apiKey())
                .apiVersion(requestHeader.apiVersion())
                .correlationId(correlationId)
                .clientId(requestHeader.clientId())
                .build();

            doNetData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

            decoder = decodeApiVersionsResponse;
        }

        private void encodeRequestHeader(
            KafkaApiStream stream,
            long traceId,
            long budgetId)
        {
            requestInFlight = true;

            final MutableDirectBufferEx encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader = requestHeaderRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .length(0)
                .apiKey(stream.api)
                .apiVersion(stream.version)
                .correlationId(0)
                .clientId(stream.clientId)
                .build();

            encodeProgress = requestHeader.limit();

            final int correlationId = nextCorrelationId++;
            final int requestSize = encodeProgress - encodeOffset - RequestHeaderFW.FIELD_OFFSET_API_KEY +
                stream.requestLength;

            requestHeaderRW.wrap(encodeBuffer, requestHeader.offset(), requestHeader.limit())
                .length(requestSize)
                .apiKey(requestHeader.apiKey())
                .apiVersion(requestHeader.apiVersion())
                .correlationId(correlationId)
                .clientId(requestHeader.clientId())
                .build();

            doNetData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);
        }

        private void doEncodeRequestHeader(
            KafkaApiStream stream,
            long traceId,
            long budgetId)
        {
            encodeRequestHeader(stream, traceId, budgetId);

            decoder = decodeResponseHeader;
        }

        private void doEncodeApiVersionsRequestExplicit(
            KafkaApiStream stream,
            long traceId,
            long budgetId)
        {
            encodeRequestHeader(stream, traceId, budgetId);

            decoder = decodeApiVersionsResponse;
        }

        private void onNet(
            int msgTypeId,
            DirectBufferEx buffer,
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
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            authorization = begin.authorization();
            state = KafkaState.openingReply(state);

            doNetWindow(traceId, 0L, 0, 0, decodePool.slotCapacity());
        }

        private void onNetData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + data.reserved();
            authorization = data.authorization();

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                cleanupNet(traceId);
            }
            else
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNet(traceId);
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
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedReply(state);
            doNetEnd(traceId, authorization);

            apiVersionsResolved = false;
            apiVersionRangeByApiKey.clear();

            cleanupDecodeSlot();
            cleanupAppActive(traceId, EMPTY_OCTETS);

            doNetSignalReconnect(traceId);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedReply(state);

            cleanupNet(traceId);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedInitial(state);

            cleanupNet(traceId);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;

            this.initialAck = acknowledge;
            this.initialMax = maximum;
            this.initialPad = padding;
            this.initialBudgetId = budgetId;

            assert initialAck <= initialSeq;

            this.authorization = window.authorization();

            final boolean notYetAttached = !attached;

            state = KafkaState.openedInitial(state);

            if (initialBudgetId != NO_BUDGET_ID && initialDebIndex == NO_DEBITOR_INDEX)
            {
                initialDeb = supplyDebitor.apply(initialBudgetId);
                initialDebIndex = initialDeb.acquire(initialBudgetId, initialId, buf -> doNetData(traceId));
                assert initialDebIndex != NO_DEBITOR_INDEX;
            }

            doNetData(traceId);

            if (notYetAttached && !pending.isEmpty())
            {
                doEncodeRequest(traceId);
            }
        }

        private void doNetData(
            long traceId)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx buffer = encodePool.buffer(encodeSlot);
                final int limit = encodeSlotOffset;

                encodeNet(traceId, authorization, initialBudgetId, buffer, 0, limit);
            }
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            if (KafkaState.closed(state))
            {
                state = 0;

                initialSeq = 0;
                initialAck = 0;
                initialMax = 0;
                initialPad = 0;
                initialBudgetId = NO_BUDGET_ID;

                replySeq = 0;
                replyAck = 0;
                replyMax = 0;

                nextCorrelationId = 0;
                responseBytesRemaining = 0;
                apiVersionKeysRemaining = 0;

                attached = false;
                requestInFlight = false;
                apiVersionsRequestExplicit = false;

                decoder = decodeReject;
            }

            if (!KafkaState.initialOpening(state))
            {
                assert state == 0;

                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);

                state = KafkaState.openingInitial(state);

                Consumer<OctetsFW.Builder> extension = EMPTY_EXTENSION;

                if (server != null)
                {
                    extension = e -> e.set((b, o, l) -> proxyBeginExRW.wrap(b, o, l)
                        .typeId(proxyTypeId)
                        .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                            .source("0.0.0.0")
                            .destination(server.host)
                            .sourcePort(0)
                            .destinationPort(server.port)))
                        .infos(i -> i.item(ii -> ii.authority(server.host)))
                        .build()
                        .sizeof());
                }

                network = KafkaClientApiFactory.this.newStream(this::onNet, originId, routedId, initialId,
                        initialSeq, initialAck, initialMax, traceId, authorization, affinity, extension);
            }
        }

        private void doNetData(
            long traceId,
            long budgetId,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                encodeBuffer.putBytes(encodeSlotOffset, buffer, offset, limit - offset);
                encodeSlotOffset += limit - offset;

                buffer = encodeBuffer;
                offset = 0;
                limit = encodeSlotOffset;
            }

            encodeNet(traceId, authorization, budgetId, buffer, offset, limit);
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!KafkaState.initialClosed(state))
            {
                state = KafkaState.closedInitial(state);

                doEnd(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);
            }

            cleanupEncodeSlot();
            cleanupBudget();
        }

        private void doNetAbort(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                state = KafkaState.closedInitial(state);

                doAbort(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);
            }

            cleanupEncodeSlot();
            cleanupBudget();
        }

        private void doNetReset(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                state = KafkaState.closedReply(state);

                doReset(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }

            cleanupDecodeSlot();
        }

        private void doNetWindow(
            long traceId,
            long budgetId,
            int minReplyNoAck,
            int minReplyPad,
            int minReplyMax)
        {
            final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

            if (newReplyAck > replyAck || minReplyMax > replyMax || !KafkaState.replyOpened(state))
            {
                replyAck = newReplyAck;
                assert replyAck <= replySeq;

                replyMax = minReplyMax;

                state = KafkaState.openedReply(state);

                doWindow(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, minReplyPad);
            }
        }

        private void encodeNet(
            long traceId,
            long authorization,
            long budgetId,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int length = limit - offset;
            final int initialBudget = Math.max(initialMax - (int) (initialSeq - initialAck), 0);
            final int reservedMax = Math.max(Math.min(length + initialPad, initialBudget), 0);

            int reserved = reservedMax;

            flush:
            if (reserved > 0)
            {
                boolean claimed = false;

                if (initialDebIndex != NO_DEBITOR_INDEX)
                {
                    reserved = initialDeb.claim(traceId, initialDebIndex, initialId, reserved, reserved, 0);
                    claimed = reserved > 0;
                }

                if (reserved < initialPad || reserved == initialPad && length > 0)
                {
                    break flush;
                }

                doData(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, reserved, buffer, offset, length, EMPTY_EXTENSION);

                initialSeq += reserved;

                assert initialAck <= initialSeq;
            }

            final int flushed = Math.max(reserved - initialPad, 0);
            final int remaining = length - flushed;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(initialId);
                }

                if (encodeSlot == NO_SLOT)
                {
                    cleanupNet(traceId);
                }
                else
                {
                    final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                    encodeBuffer.putBytes(0, buffer, offset + flushed, remaining);
                    encodeSlotOffset = remaining;
                }
            }
            else
            {
                cleanupEncodeSlot();
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
            KafkaApiClientDecoder previous = null;
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
                    cleanupNet(traceId);
                }
                else
                {
                    final MutableDirectBufferEx decodeBuffer = decodePool.buffer(decodeSlot);
                    decodeBuffer.putBytes(0, buffer, progress, limit - progress);
                    decodeSlotOffset = limit - progress;
                    decodeSlotReserved = (limit - progress) * reserved / (limit - offset);
                }

                doNetWindow(traceId, budgetId, decodeSlotOffset, 0, replyMax);
            }
            else
            {
                cleanupDecodeSlot();

                if (reserved > 0)
                {
                    doNetWindow(traceId, budgetId, 0, 0, replyMax);
                }
            }
        }

        private void onDecodeApiVersionsResponse(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int limit,
            int responseBodyLength,
            short errorCode,
            int apiKeyCount)
        {
            if (errorCode != ERROR_NONE)
            {
                cleanupNetPending(traceId, errorCode);
            }
            else
            {
                apiVersionKeysRemaining = apiKeyCount;

                if (apiVersionsRequestExplicit)
                {
                    responseBytesRemaining = responseBodyLength;

                    final KafkaApiStream head = pending.peek();
                    if (head != null)
                    {
                        head.doAppBegin(traceId, responseBodyLength);
                    }
                }

                forwardApiVersionsResponseBytes(traceId, buffer, offset, limit);
                decoder = decodeApiVersionsResponseKeys;
            }
        }

        private void onDecodeApiVersionsResponseKeys(
            long traceId)
        {
            if (apiVersionKeysRemaining == 0)
            {
                apiVersionsResolved = true;

                if (apiVersionsRequestExplicit)
                {
                    apiVersionsRequestExplicit = false;
                    onResponseComplete(traceId);
                }
                else
                {
                    decoder = decodeResponseHeader;
                    doEncodeRequest(traceId);
                }
            }
            else
            {
                decoder = decodeApiVersionsResponseKey;
            }
        }

        private void onDecodeApiVersionsResponseKey(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int limit,
            short apiKey,
            short minVersion,
            short maxVersion)
        {
            final int range = (minVersion << Short.SIZE) | (maxVersion & 0xFFFF);
            apiVersionRangeByApiKey.put(apiKey, range);
            apiVersionKeysRemaining--;

            forwardApiVersionsResponseBytes(traceId, buffer, offset, limit);
            decoder = decodeApiVersionsResponseKeys;
        }

        private void forwardApiVersionsResponseBytes(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (apiVersionsRequestExplicit)
            {
                final KafkaApiStream head = pending.peek();
                if (head != null)
                {
                    head.doAppData(traceId, buffer, offset, limit);
                    responseBytesRemaining -= limit - offset;
                }
            }
        }

        private void onDecodeResponseHeader(
            long traceId,
            long authorization,
            int responseBodyLength)
        {
            if (responseBodyLength == RESPONSE_ERROR_CODE_SIZE)
            {
                decoder = decodeResponseErrorCode;
            }
            else
            {
                final KafkaApiStream head = pending.peek();
                if (head != null)
                {
                    responseBytesRemaining = responseBodyLength;
                    head.doAppBegin(traceId, responseBodyLength);

                    if (responseBodyLength == 0)
                    {
                        onResponseComplete(traceId);
                    }
                }

                decoder = decodeResponseBody;
            }
        }

        private void onDecodeResponseErrorCode(
            long traceId,
            short errorCode)
        {
            requestInFlight = false;

            final KafkaApiStream head = pending.poll();
            if (head != null)
            {
                if (errorCode == ERROR_UNSUPPORTED_VERSION)
                {
                    event.apiVersionRejected(traceId, routedId, head.api, head.version);
                    apiVersionsResolved = false;
                    apiVersionRangeByApiKey.clear();
                }

                head.cleanupApp(traceId, errorCode);
            }

            decoder = decodeResponseHeader;

            if (!pending.isEmpty())
            {
                doEncodeRequest(traceId);
            }
        }

        private int onDecodeResponseBody(
            long traceId,
            long authorization,
            long budgetId,
            DirectBufferEx buffer,
            int progress,
            int limit)
        {
            final KafkaApiStream head = pending.peek();
            final int available = Math.min(limit - progress, responseBytesRemaining);

            int forwarded = 0;
            if (head != null && available > 0)
            {
                head.doAppData(traceId, buffer, progress, progress + available);
                forwarded = available;
                responseBytesRemaining -= available;
            }

            if (responseBytesRemaining == 0)
            {
                onResponseComplete(traceId);
            }

            return forwarded;
        }

        private void onResponseComplete(
            long traceId)
        {
            requestInFlight = false;

            final KafkaApiStream head = pending.poll();
            if (head != null)
            {
                head.doAppEnd(traceId);
            }

            decoder = decodeResponseHeader;

            if (!pending.isEmpty())
            {
                doEncodeRequest(traceId);
            }
        }

        private void cleanupAppActive(
            long traceId,
            Flyweight extension)
        {
            if (requestInFlight)
            {
                requestInFlight = false;

                final KafkaApiStream head = pending.poll();
                if (head != null)
                {
                    head.cleanupApp(traceId, extension);
                }
            }
        }

        private void doNetSignalReconnect(
            long traceId)
        {
            if (!pending.isEmpty())
            {
                if (reconnectDelay != 0)
                {
                    if (reconnectAt != NO_CANCEL_ID)
                    {
                        signaler.cancel(reconnectAt);
                    }

                    this.reconnectAt = signaler.signalAt(
                        currentTimeMillis() + Math.min(50 << reconnectAttempt++, SECONDS.toMillis(reconnectDelay)),
                        SIGNAL_RECONNECT,
                        this::onNetSignalReconnect);
                }
                else
                {
                    doNetBegin(traceId, authorization, affinity);
                }
            }
        }

        private void onNetSignalReconnect(
            int signalId)
        {
            assert signalId == SIGNAL_RECONNECT;

            this.reconnectAt = NO_CANCEL_ID;

            if (!pending.isEmpty())
            {
                final long traceId = supplyTraceId.getAsLong();
                doNetBegin(traceId, authorization, affinity);
            }
        }

        private void cleanupNetPending(
            long traceId,
            int error)
        {
            doNetReset(traceId);
            doNetAbort(traceId);

            apiVersionsResolved = false;
            apiVersionRangeByApiKey.clear();
            apiVersionsRequestExplicit = false;

            final KafkaResetExFW kafkaResetEx = kafkaResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .error(error)
                .build();

            KafkaApiStream stream;
            while ((stream = pending.poll()) != null)
            {
                stream.cleanupApp(traceId, kafkaResetEx);
            }
        }

        private void cleanupNet(
            long traceId)
        {
            doNetReset(traceId);
            doNetAbort(traceId);

            apiVersionsResolved = false;
            apiVersionRangeByApiKey.clear();

            cleanupAppActive(traceId, EMPTY_OCTETS);
            doNetSignalReconnect(traceId);
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

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
            }
        }

        private void cleanupBudget()
        {
            if (initialDebIndex != NO_DEBITOR_INDEX)
            {
                initialDeb.release(initialDebIndex, initialId);
                initialDebIndex = NO_DEBITOR_INDEX;
            }
        }
    }

    private void doBegin(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Consumer<OctetsFW.Builder> extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(extension)
                .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer receiver,
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
        DirectBufferEx payload,
        int offset,
        int length,
        Consumer<OctetsFW.Builder> extension)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .budgetId(budgetId)
                .reserved(reserved)
                .payload(payload, offset, length)
                .extension(extension)
                .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Consumer<OctetsFW.Builder> extension)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .extension(extension)
                .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doAbort(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Consumer<OctetsFW.Builder> extension)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .extension(extension)
                .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doWindow(
        MessageConsumer sender,
        long originId,
        long routedId,
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
                .build();

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }
}
