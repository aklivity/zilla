/*
 * Copyright 2021-2023 Aklivity Inc.
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
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.UnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.LongLongConsumer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaServerConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.RequestHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.ResponseHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.config.ConfigResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.config.ResourceRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.config.ResourceResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaCreateTopicsRequestBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class KafkaClientCreateTopicsFactory extends KafkaClientSaslHandshaker implements BindingHandler
{
    private static final int ERROR_NONE = 0;

    private static final int SIGNAL_NEXT_REQUEST = 1;

    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer();
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private static final short DESCRIBE_CONFIGS_API_KEY = 32;
    private static final short DESCRIBE_CONFIGS_API_VERSION = 0;
    private static final byte RESOURCE_TYPE_TOPIC = 2;

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
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final KafkaResetExFW.Builder kafkaResetExRW = new KafkaResetExFW.Builder();
    private final ProxyBeginExFW.Builder proxyBeginExRW = new ProxyBeginExFW.Builder();

    private final RequestHeaderFW.Builder requestHeaderRW = new RequestHeaderFW.Builder();
    private final CreateTopicsConfigsRequestFW.Builder createTopicsConfigsRequestRW = new CreateTopicsConfigsRequestFW.Builder();
    private final ResourceRequestFW.Builder resourceRequestRW = new ResourceRequestFW.Builder();
    private final String16FW.Builder configNameRW = new String16FW.Builder(ByteOrder.BIG_ENDIAN);

    private final ResponseHeaderFW responseHeaderRO = new ResponseHeaderFW();
    private final CreateTopicsConfigsResponseFW createTopicsConfigsResponseRO = new CreateTopicsConfigsResponseFW();
    private final ResourceResponseFW resourceResponseRO = new ResourceResponseFW();
    private final ConfigResponseFW configResponseRO = new ConfigResponseFW();

    private final Map<String, String> newConfigs = new LinkedHashMap<>();
    private final List<String> changedConfigs = new ArrayList<>();

    private final KafkaCreateTopicsClientDecoder decodeSaslHandshakeResponse = this::decodeSaslHandshakeResponse;
    private final KafkaCreateTopicsClientDecoder decodeSaslHandshake = this::decodeSaslHandshake;
    private final KafkaCreateTopicsClientDecoder decodeSaslHandshakeMechanisms = this::decodeSaslHandshakeMechanisms;
    private final KafkaCreateTopicsClientDecoder decodeSaslHandshakeMechanism = this::decodeSaslHandshakeMechanism;
    private final KafkaCreateTopicsClientDecoder decodeSaslAuthenticateResponse = this::decodeSaslAuthenticateResponse;
    private final KafkaCreateTopicsClientDecoder decodeSaslAuthenticate = this::decodeSaslAuthenticate;
    private final KafkaCreateTopicsClientDecoder decodeCreateTopicsResponse = this::decodeCreateTopicsResponse;
    private final KafkaCreateTopicsClientDecoder decodeIgnoreAll = this::decodeIgnoreAll;
    private final KafkaCreateTopicsClientDecoder decodeReject = this::decodeReject;

    private final int kafkaTypeId;
    private final int proxyTypeId;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final Signaler signaler;
    private final BindingHandler streamFactory;
    private final UnaryOperator<KafkaSaslConfig> resolveSasl;
    private final LongFunction<KafkaBindingConfig> supplyBinding;
    private final LongFunction<BudgetDebitor> supplyDebitor;

    public KafkaClientCreateTopicsFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding,
        LongFunction<BudgetDebitor> supplyDebitor,
        Signaler signaler,
        BindingHandler streamFactory,
        UnaryOperator<KafkaSaslConfig> resolveSasl)
    {
        super(config, context);
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.proxyTypeId = context.supplyTypeId("proxy");
        this.signaler = signaler;
        this.streamFactory = streamFactory;
        this.resolveSasl = resolveSasl;
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool();
        this.supplyBinding = supplyBinding;
        this.supplyDebitor = supplyDebitor;
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
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long affinity = begin.affinity();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();
        final ExtensionFW beginEx = extensionRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
        final KafkaBeginExFW kafkaBeginEx = beginEx != null && beginEx.typeId() == kafkaTypeId ?
                kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit()) : null;

        assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_REQUEST;
        final KafkaCreateTopicsRequestBeginExFW kafkaCreateTopicsBeginEx = kafkaBeginEx.request().createTopics();

        MessageConsumer newStream = null;

        final KafkaBindingConfig binding = supplyBinding.apply(routedId);
        final KafkaRouteConfig resolved = binding != null ? binding.resolve(authorization, null) : null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;
            final KafkaSaslConfig sasl = resolveSasl.apply(binding.sasl());

            List<Topic> topics = new ArrayList<>();
            kafkaCreateTopicsBeginEx.topics().forEach(t ->
            {
                String name = t.name().asString();
                int partitionCount = t.partitionCount();
                short replicas = t.replicas();
                List<Assignment> assignments = new ArrayList<>();
                t.assignments().forEach(a -> assignments.add(new Assignment()));
            });

            final CreateTopicsRequest request = new CreateTopicsRequest()
            newStream = new KafkaCreateTopicsStream(
                    application,
                    originId,
                    routedId,
                    initialId,
                    affinity,
                    resolvedId,
                    topicName,
                    configs,
                    binding.servers(),
                    sasl)::onApp;
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
        DirectBuffer payload,
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

    private void doDataNull(
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
        Flyweight extension)
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
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
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

    @FunctionalInterface
    private interface KafkaCreateTopicsClientDecoder
    {
        int decode(
            KafkaCreateTopicsStream.KafkaCreateTopicsClient client,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int progress,
            int limit);
    }

    private int decodeCreateTopicsResponse(
        KafkaCreateTopicsStream.KafkaCreateTopicsClient client,
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

        decode:
        if (length != 0)
        {
            final ResponseHeaderFW responseHeader = responseHeaderRO.tryWrap(buffer, progress, limit);
            if (responseHeader == null)
            {
                client.decoder = decodeIgnoreAll;
                break decode;
            }

            final int responseSize = responseHeader.length();

            if (length >= responseHeader.sizeof() + responseSize)
            {
                progress = responseHeader.limit();

                final CreateTopicsConfigsResponseFW createTopicsConfigsResponse =
                        createTopicsConfigsResponseRO.tryWrap(buffer, progress, limit);

                if (createTopicsConfigsResponse == null)
                {
                    client.decoder = decodeIgnoreAll;
                    break decode;
                }

                progress = createTopicsConfigsResponse.limit();

                final int resourceCount = createTopicsConfigsResponse.resourceCount();
                for (int resourceIndex = 0; resourceIndex < resourceCount; resourceIndex++)
                {
                    final ResourceResponseFW resource = resourceResponseRO.tryWrap(buffer, progress, limit);
                    if (resource == null)
                    {
                        client.decoder = decodeIgnoreAll;
                        break decode;
                    }

                    progress = resource.limit();

                    final String resourceName = resource.name().asString();
                    final int resourceError = resource.errorCode();

                    client.onDecodeResource(traceId, client.authorization, resourceError, resourceName);
                    // TODO: use different decoder for configs
                    if (resourceError != ERROR_NONE || !client.topic.equals(resourceName))
                    {
                        client.decoder = decodeIgnoreAll;
                        break decode;
                    }

                    final int configCount = resource.configCount();
                    newConfigs.clear();
                    for (int configIndex = 0; configIndex < configCount; configIndex++)
                    {
                        final ConfigResponseFW config = configResponseRO.tryWrap(buffer, progress, limit);
                        if (config == null)
                        {
                            client.decoder = decodeIgnoreAll;
                            break decode;
                        }

                        progress = config.limit();

                        final String name = config.name().asString();
                        final String value = config.value().asString();

                        newConfigs.put(name, value);
                    }

                    client.onDecodeCreateTopicsResponse(traceId, newConfigs);
                }
            }
        }

        if (client.decoder == decodeIgnoreAll)
        {
            client.cleanupNet(traceId);
        }

        return progress;
    }

    private int decodeReject(
        KafkaCreateTopicsStream.KafkaCreateTopicsClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        client.doNetResetIfNecessary(traceId);
        client.decoder = decodeIgnoreAll;
        return limit;
    }

    private int decodeIgnoreAll(
        KafkaCreateTopicsStream.KafkaCreateTopicsClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        return limit;
    }

    private final class KafkaCreateTopicsStream
    {
        private final MessageConsumer application;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final KafkaCreateTopicsClient client;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private long replyBudgetId;

        KafkaCreateTopicsStream(
            MessageConsumer application,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long resolvedId,
            String topic,
            List<String> configs,
            List<KafkaServerConfig> servers,
            KafkaSaslConfig sasl)
        {
            this.application = application;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.client = new KafkaCreateTopicsClient(routedId, resolvedId, topic, configs, servers, sasl);
        }

        private void onApp(
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
            final long authorization = begin.authorization();

            state = KafkaState.openingInitial(state);

            client.doNetBegin(traceId, authorization, affinity);
        }

        private void onAppData(
            DataFW data)
        {
            final long traceId = data.traceId();

            client.cleanupNet(traceId);
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = KafkaState.closedInitial(state);

            client.doNetEnd(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedInitial(state);

            client.doNetAbortIfNecessary(traceId);
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            this.replyAck = acknowledge;
            this.replyMax = maximum;
            this.replyPad = padding;
            this.replyBudgetId = budgetId;

            assert replyAck <= replySeq;
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedReply(state);

            client.doNetResetIfNecessary(traceId);
        }

        private boolean isAppReplyOpen()
        {
            return KafkaState.replyOpening(state);
        }

        private void doAppBeginIfNecessary(
            long traceId,
            long authorization,
            String topic,
            Set<String> configs)
        {
            if (!KafkaState.replyOpening(state))
            {
                doAppBegin(traceId, authorization, topic, configs);
            }
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            String topic,
            Set<String> configs)
        {
            state = KafkaState.openingReply(state);

            doBegin(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                                                        .typeId(kafkaTypeId)
                                                        .createTopics(m -> m.topic(topic)
                                                                        .configs(cs ->
                                                                            configs.forEach(n -> cs.item(i -> i.set(n, UTF_8)))))
                                                        .build()
                                                        .sizeof()));
        }

        private void doAppData(
            long traceId,
            long authorization,
            KafkaDataExFW extension)
        {
            final int reserved = replyPad;

            doDataNull(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, replyBudgetId, reserved, extension);

            replySeq += reserved;

            assert replyAck <= replySeq;
        }

        private void doAppEnd(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                state = KafkaState.closedReply(state);
                //client.stream = nullIfClosed(state, client.stream);
                doEnd(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, client.authorization, EMPTY_EXTENSION);
            }
        }

        private void doAppAbort(
            long traceId)
        {
            state = KafkaState.closedReply(state);
            //client.stream = nullIfClosed(state, client.stream);
            doAbort(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, client.authorization, EMPTY_EXTENSION);
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
                        traceId, client.authorization, budgetId, minInitialPad);
            }
        }

        private void doAppReset(
            long traceId,
            Flyweight extension)
        {
            state = KafkaState.closedInitial(state);
            //client.stream = nullIfClosed(state, client.stream);

            doReset(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, client.authorization, extension);
        }

        private void doAppAbortIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doAppAbort(traceId);
            }
        }

        private void doAppResetIfNecessary(
            long traceId,
            Flyweight extension)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                doAppReset(traceId, extension);
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
            doAppResetIfNecessary(traceId, extension);
            doAppAbortIfNecessary(traceId);
        }

        private final class KafkaCreateTopicsClient extends KafkaSaslClient
        {
            private final LongLongConsumer encodeSaslHandshakeRequest = this::doEncodeSaslHandshakeRequest;
            private final LongLongConsumer encodeSaslAuthenticateRequest = this::doEncodeSaslAuthenticateRequest;
            private final LongLongConsumer encodeCreateTopicsRequest = this::doEncodeCreateTopicsRequest;

            private MessageConsumer network;
            private final String topic;
            private final Map<String, String> configs;

            private int state;
            private long authorization;

            private long initialSeq;
            private long initialAck;
            private int initialMax;
            private int initialMin;
            private int initialPad;
            private long initialBudgetId = NO_BUDGET_ID;
            private long initialDebIndex = NO_DEBITOR_INDEX;

            private long replySeq;
            private long replyAck;
            private int replyMax;

            private int encodeSlot = NO_SLOT;
            private int encodeSlotOffset;
            private long encodeSlotTraceId;

            private int decodeSlot = NO_SLOT;
            private int decodeSlotOffset;
            private int decodeSlotReserved;

            private int nextResponseId;

            private KafkaCreateTopicsClientDecoder decoder;
            private LongLongConsumer encoder;
            private BudgetDebitor initialDeb;

            KafkaCreateTopicsClient(
                long originId,
                long routedId,
                String topic,
                List<String> configs,
                List<KafkaServerConfig> servers,
                KafkaSaslConfig sasl)
            {
                super(servers, sasl, originId, routedId);
                this.topic = requireNonNull(topic);
                this.configs = new LinkedHashMap<>(configs.size());
                configs.forEach(c -> this.configs.put(c, null));

                this.encoder = sasl != null ? encodeSaslHandshakeRequest : encodeCreateTopicsRequest;
                this.decoder = decodeReject;
            }

            public void onDecodeResource(
                long traceId,
                long authorization,
                int errorCode,
                String resource)
            {
                switch (errorCode)
                {
                case ERROR_NONE:
                    assert resource.equals(this.topic);
                    break;
                default:
                    onDecodeResponseErrorCode(traceId, originId, errorCode);
                    final KafkaResetExFW resetEx = kafkaResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                                                                 .typeId(kafkaTypeId)
                                                                 .error(errorCode)
                                                                 .build();
                    cleanupApp(traceId, resetEx);
                    doNetEnd(traceId, authorization);
                    break;
                }
            }

            private void onDecodeResponseErrorCode(
                long traceId,
                long originId,
                int errorCode)
            {
                super.onDecodeResponseErrorCode(traceId, originId, DESCRIBE_CONFIGS_API_KEY, DESCRIBE_CONFIGS_API_VERSION,
                    errorCode);
            }

            private void onNet(
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
                case ResetFW.TYPE_ID:
                    final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                    onNetReset(reset);
                    break;
                case WindowFW.TYPE_ID:
                    final WindowFW window = windowRO.wrap(buffer, index, index + length);
                    onNetWindow(window);
                    break;
                case SignalFW.TYPE_ID:
                    final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                    onNetSignal(signal);
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
                final long traceId = end.traceId();

                state = KafkaState.closedReply(state);

                cleanupDecodeSlotIfNecessary();

                if (!isAppReplyOpen())
                {
                    cleanupNet(traceId);
                }
                else if (decodeSlot == NO_SLOT)
                {
                    doAppEnd(traceId);
                }
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
                final int minimum = window.minimum();
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
                this.initialMin = minimum;
                this.initialBudgetId = budgetId;

                assert initialAck <= initialSeq;

                this.authorization = window.authorization();

                state = KafkaState.openedInitial(state);

                if (initialBudgetId != NO_BUDGET_ID && initialDebIndex == NO_DEBITOR_INDEX)
                {
                    initialDeb = supplyDebitor.apply(initialBudgetId);
                    initialDebIndex = initialDeb.acquire(initialBudgetId, initialId, this::doNetDataIfNecessary);
                    assert initialDebIndex != NO_DEBITOR_INDEX;
                }

                doNetDataIfNecessary(budgetId);

                doEncodeRequestIfNecessary(traceId, budgetId);
            }

            private void doNetDataIfNecessary(
                long traceId)
            {
                if (encodeSlot != NO_SLOT)
                {
                    final MutableDirectBuffer buffer = encodePool.buffer(encodeSlot);
                    final int limit = encodeSlotOffset;

                    encodeNet(traceId, authorization, initialBudgetId, buffer, 0, limit);
                }
            }

            private void onNetSignal(
                SignalFW signal)
            {
                final long traceId = signal.traceId();
                final int signalId = signal.signalId();

                if (signalId == SIGNAL_NEXT_REQUEST)
                {
                    doEncodeRequestIfNecessary(traceId, initialBudgetId);
                }
            }

            private void doNetBegin(
                long traceId,
                long authorization,
                long affinity)
            {
                state = KafkaState.openingInitial(state);

                Consumer<OctetsFW.Builder> extension = EMPTY_EXTENSION;

                if (server != null)
                {
                    extension =  e -> e.set((b, o, l) -> proxyBeginExRW.wrap(b, o, l)
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

                network = newStream(this::onNet, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, affinity, extension);
            }

            @Override
            protected void doNetData(
                long traceId,
                long budgetId,
                DirectBuffer buffer,
                int offset,
                int limit)
            {
                if (encodeSlot != NO_SLOT)
                {
                    final MutableDirectBuffer encodeBuffer = encodePool.buffer(encodeSlot);
                    encodeBuffer.putBytes(encodeSlotOffset, buffer, offset, limit - offset);
                    encodeSlotOffset += limit - offset;
                    encodeSlotTraceId = traceId;

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
                state = KafkaState.closedInitial(state);

                cleanupEncodeSlotIfNecessary();
                cleanupBudgetIfNecessary();

                doEnd(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, EMPTY_EXTENSION);
            }

            private void doNetAbortIfNecessary(
                long traceId)
            {
                if (!KafkaState.initialClosed(state))
                {
                    doAbort(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                            traceId, authorization, EMPTY_EXTENSION);
                    state = KafkaState.closedInitial(state);
                }

                cleanupEncodeSlotIfNecessary();
                cleanupBudgetIfNecessary();
            }

            private void doNetResetIfNecessary(
                long traceId)
            {
                if (!KafkaState.replyClosed(state))
                {
                    doReset(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                            traceId, authorization, EMPTY_OCTETS);
                    state = KafkaState.closedReply(state);
                }

                cleanupDecodeSlotIfNecessary();
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

            private void doEncodeRequestIfNecessary(
                long traceId,
                long budgetId)
            {
                if (nextRequestId == nextResponseId)
                {
                    encoder.accept(traceId, budgetId);
                }
            }

            private void doEncodeCreateTopicsRequest(
                long traceId,
                long budgetId)
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[client] %s DESCRIBE\n", topic);
                }

                final MutableDirectBuffer encodeBuffer = writeBuffer;
                final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
                final int encodeLimit = encodeBuffer.capacity();

                int encodeProgress = encodeOffset;

                final RequestHeaderFW requestHeader = requestHeaderRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .length(0)
                        .apiKey(DESCRIBE_CONFIGS_API_KEY)
                        .apiVersion(DESCRIBE_CONFIGS_API_VERSION)
                        .correlationId(0)
                        .clientId(clientId)
                        .build();

                encodeProgress = requestHeader.limit();

                final CreateTopicsConfigsRequestFW createTopicsConfigsRequest =
                        createTopicsConfigsRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                                                .resourceCount(1)
                                                .build();

                encodeProgress = createTopicsConfigsRequest.limit();

                final ResourceRequestFW resourceRequest = resourceRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .type(RESOURCE_TYPE_TOPIC)
                        .name(topic)
                        .configNamesCount(configs.size())
                        .build();

                encodeProgress = resourceRequest.limit();

                for (String config : configs.keySet())
                {
                    final String16FW configName = configNameRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                            .set(config, UTF_8)
                            .build();

                    encodeProgress = configName.limit();
                }

                final int requestId = nextRequestId++;
                final int requestSize = encodeProgress - encodeOffset - RequestHeaderFW.FIELD_OFFSET_API_KEY;

                requestHeaderRW.wrap(encodeBuffer, requestHeader.offset(), requestHeader.limit())
                        .length(requestSize)
                        .apiKey(requestHeader.apiKey())
                        .apiVersion(requestHeader.apiVersion())
                        .correlationId(requestId)
                        .clientId(requestHeader.clientId())
                        .build();

                doNetData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

                decoder = decodeCreateTopicsResponse;
            }

            private void encodeNet(
                long traceId,
                long authorization,
                long budgetId,
                DirectBuffer buffer,
                int offset,
                int limit)
            {
                final int length = limit - offset;
                final int initialBudget = Math.max(initialMax - (int)(initialSeq - initialAck), 0);
                final int reservedMax = Math.max(Math.min(length + initialPad, initialBudget), initialMin);

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
                        final MutableDirectBuffer encodeBuffer = encodePool.buffer(encodeSlot);
                        encodeBuffer.putBytes(0, buffer, offset + flushed, remaining);
                        encodeSlotOffset = remaining;
                    }
                }
                else
                {
                    cleanupEncodeSlotIfNecessary();
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
                KafkaCreateTopicsClientDecoder previous = null;
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
                        final MutableDirectBuffer decodeBuffer = decodePool.buffer(decodeSlot);
                        decodeBuffer.putBytes(0, buffer, progress, limit - progress);
                        decodeSlotOffset = limit - progress;
                        decodeSlotReserved = (limit - progress) * reserved / (limit - offset);
                    }

                    doNetWindow(traceId, budgetId, decodeSlotOffset, 0, replyMax);
                }
                else
                {
                    cleanupDecodeSlotIfNecessary();

                    if (KafkaState.replyClosing(state))
                    {
                        doAppEnd(traceId);
                    }
                    else if (reserved > 0)
                    {
                        doNetWindow(traceId, budgetId, 0, 0, replyMax);
                    }
                }
            }

            @Override
            protected void doDecodeSaslHandshakeResponse(
                long traceId)
            {
                decoder = decodeSaslHandshakeResponse;
            }

            @Override
            protected void doDecodeSaslHandshake(
                long traceId)
            {
                decoder = decodeSaslHandshake;
            }

            @Override
            protected void doDecodeSaslHandshakeMechanisms(
                long traceId)
            {
                decoder = decodeSaslHandshakeMechanisms;
            }

            @Override
            protected void doDecodeSaslHandshakeMechansim(
                long traceId)
            {
                decoder = decodeSaslHandshakeMechanism;
            }

            @Override
            protected void doDecodeSaslAuthenticateResponse(
                long traceId)
            {
                decoder = decodeSaslAuthenticateResponse;
            }

            @Override
            protected void doDecodeSaslAuthenticate(
                long traceId)
            {
                decoder = decodeSaslAuthenticate;
            }

            @Override
            protected void onDecodeSaslHandshakeResponse(
                long traceId,
                long authorization,
                int errorCode)
            {
                switch (errorCode)
                {
                case ERROR_NONE:
                    client.encoder = client.encodeSaslAuthenticateRequest;
                    client.decoder = decodeSaslAuthenticateResponse;
                    break;
                default:
                    cleanupApp(traceId, errorCode);
                    doNetEnd(traceId, authorization);
                    break;
                }
            }

            @Override
            protected void onDecodeSaslAuthenticateResponse(
                long traceId,
                long authorization,
                int errorCode)
            {
                switch (errorCode)
                {
                case ERROR_NONE:
                    client.encoder = client.encodeCreateTopicsRequest;
                    client.decoder = decodeCreateTopicsResponse;
                    break;
                default:
                    cleanupApp(traceId, errorCode);
                    doNetEnd(traceId, authorization);
                    break;
                }
            }

            @Override
            protected void onDecodeSaslResponse(
                long traceId)
            {
                nextResponseId++;
                signaler.signalNow(originId, routedId, initialId, traceId, SIGNAL_NEXT_REQUEST, 0);
            }

            private void onDecodeCreateTopicsResponse(
                long traceId,
                Map<String, String> newConfigs)
            {
                doAppWindow(traceId, 0L, 0, 0, 0);
                doAppBeginIfNecessary(traceId, authorization, topic, configs.keySet());

                changedConfigs.clear();
                for (Map.Entry<String, String> entry : configs.entrySet())
                {
                    final String configName = entry.getKey();
                    final String newConfigValue = newConfigs.get(configName);
                    final String oldConfigValue = entry.setValue(newConfigValue);

                    if (!Objects.equals(newConfigValue, oldConfigValue))
                    {
                        changedConfigs.add(configName);
                    }
                }

                if (!changedConfigs.isEmpty())
                {
                    final KafkaDataExFW kafkaDataEx = kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .createTopics(d -> changedConfigs.forEach(n -> d.configsItem(ci -> ci.name(n).value(configs.get(n)))))
                        .build();

                    doAppData(traceId, authorization, kafkaDataEx);
                }

                nextResponseId++;
                signaler.signalAt(currentTimeMillis() + maxAgeMillis, originId, routedId, initialId,
                    traceId, SIGNAL_NEXT_REQUEST, 0);
            }

            private void cleanupNet(
                long traceId)
            {
                doNetResetIfNecessary(traceId);
                doNetAbortIfNecessary(traceId);

                cleanupApp(traceId, EMPTY_OCTETS);
            }

            private void cleanupDecodeSlotIfNecessary()
            {
                if (decodeSlot != NO_SLOT)
                {
                    decodePool.release(decodeSlot);
                    decodeSlot = NO_SLOT;
                    decodeSlotOffset = 0;
                    decodeSlotReserved = 0;
                }
            }

            private void cleanupEncodeSlotIfNecessary()
            {
                if (encodeSlot != NO_SLOT)
                {
                    encodePool.release(encodeSlot);
                    encodeSlot = NO_SLOT;
                    encodeSlotOffset = 0;
                    encodeSlotTraceId = 0;
                }
            }

            private void cleanupBudgetIfNecessary()
            {
                if (initialDebIndex != NO_DEBITOR_INDEX)
                {
                    initialDeb.release(initialDebIndex, initialId);
                    initialDebIndex = NO_DEBITOR_INDEX;
                }
            }
        }
    }

    private final class CreateTopicsRequest
    {
        private List<Topic> topics;
        private int timeoutMs;
        private boolean validateOnly;

        private CreateTopicsRequest(
            List<Topic> topics,
            int timeoutMs,
            boolean validateOnly)
        {
            this.topics = topics;
            this.timeoutMs = timeoutMs;
            this.validateOnly = validateOnly;
        }
    }

    private final class Topic
    {
        private String name;
        private int numPartitions;
        private short replicationFactor;
        private List<Assignment> assignments;
        private List<Config> configs;

        private Topic(
            String name,
            int numPartitions,
            short replicationFactor,
            List<Assignment> assignments,
            List<Config> configs)
        {
            this.name = name;
            this.numPartitions = numPartitions;
            this.replicationFactor = replicationFactor;
            this.assignments = assignments;
            this.configs = configs;
        }
    }

    private final class Assignment
    {
        private int partitionIndex;
        private List<Integer> brokerIds;

        private Assignment(
            int partitionIndex,
            List<Integer> brokerIds)
        {
            this.partitionIndex = partitionIndex;
            this.brokerIds = brokerIds;
        }
    }

    private final class Config
    {
        private String name;
        private String value; // Nullable

        private Config(
            String name,
            String value)
        {
            this.name = name;
            this.value = value;
        }
    }
}
