/*
 * Copyright 2021-2024 Aklivity Inc.
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

import java.util.ArrayList;
import java.util.List;
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
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.RequestHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.ResponseHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_cluster.ClusterBrokerFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_cluster.DescribeClusterRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_cluster.DescribeClusterRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_cluster.DescribeClusterResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_cluster.DescribeClusterResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaDescribeClusterRequestBeginExFW;
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

public final class KafkaClientDescribeClusterFactory extends KafkaClientSaslHandshaker implements BindingHandler
{
    private static final int ERROR_NONE = 0;
    private static final int SIGNAL_NEXT_REQUEST = 1;

    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer();
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private static final short CREATE_TOPICS_API_KEY = 60;
    private static final short CREATE_TOPICS_API_VERSION = 0;

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
    private final DescribeClusterRequestFW.Builder describeClusterRequestRW = new DescribeClusterRequestFW.Builder();
    private final DescribeClusterRequestPart2FW.Builder describeClusterRequestPart2RW =
        new DescribeClusterRequestPart2FW.Builder();

    private final ResponseHeaderFW responseHeaderRO = new ResponseHeaderFW();
    private final DescribeClusterResponseFW describeClusterResponseRO = new DescribeClusterResponseFW();
    private final ClusterBrokerFW brokerResponseRO = new ClusterBrokerFW();
    private final DescribeClusterResponsePart2FW describeClusterResponsePart2RO = new DescribeClusterResponsePart2FW();

    private final KafkaDescribeClusterClientDecoder decodeSaslHandshakeResponse = this::decodeSaslHandshakeResponse;
    private final KafkaDescribeClusterClientDecoder decodeSaslHandshake = this::decodeSaslHandshake;
    private final KafkaDescribeClusterClientDecoder decodeSaslHandshakeMechanisms = this::decodeSaslHandshakeMechanisms;
    private final KafkaDescribeClusterClientDecoder decodeSaslHandshakeMechanism = this::decodeSaslHandshakeMechanism;
    private final KafkaDescribeClusterClientDecoder decodeSaslAuthenticateResponse = this::decodeSaslAuthenticateResponse;
    private final KafkaDescribeClusterClientDecoder decodeSaslAuthenticate = this::decodeSaslAuthenticate;
    private final KafkaDescribeClusterClientDecoder decodeDescribeClusterResponse = this::decodeDescribeClusterResponse;
    private final KafkaDescribeClusterClientDecoder decodeIgnoreAll = this::decodeIgnoreAll;
    private final KafkaDescribeClusterClientDecoder decodeReject = this::decodeReject;

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
    private final List<ClusterBrokerInfo> brokers;

    public KafkaClientDescribeClusterFactory(
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
        this.brokers = new ArrayList<>();
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
        final KafkaDescribeClusterRequestBeginExFW kafkaDescribeClusterBeginEx = kafkaBeginEx.request().describeCluster();

        MessageConsumer newStream = null;

        final KafkaBindingConfig binding = supplyBinding.apply(routedId);
        final KafkaRouteConfig resolved = binding != null
            ? binding.resolve(authorization, null)
            : null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;
            final KafkaSaslConfig sasl = resolveSasl.apply(binding.sasl());

            int authorizedOperations = kafkaDescribeClusterBeginEx.includeAuthorizedOperations();
            newStream = new KafkaDescribeClusterStream(
                    application,
                    originId,
                    routedId,
                    initialId,
                    affinity,
                    resolvedId,
                    authorizedOperations,
                    binding.servers(),
                    sasl)::onApplication;
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
            .payload(payload, offset, length)
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
    private interface KafkaDescribeClusterClientDecoder
    {
        int decode(
            KafkaDescribeClusterClient client,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int progress,
            int limit);
    }

    private int decodeDescribeClusterResponse(
        KafkaDescribeClusterClient client,
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
                break decode;
            }

            progress = responseHeader.limit();

            final DescribeClusterResponseFW describeClusterResponse = describeClusterResponseRO.tryWrap(buffer, progress, limit);
            if (describeClusterResponse == null)
            {
                break decode;
            }

            progress = describeClusterResponse.limit();

            final int throttle = describeClusterResponse.throttle();
            final short error = describeClusterResponse.error();
            final String message = describeClusterResponse.message().asString();
            final String clusterId = describeClusterResponse.clusterId().asString();
            final int controllerId = describeClusterResponse.controllerId();
            final int brokerCount = describeClusterResponse.brokerCount();

            brokers.clear();
            for (int brokerIndex = 0; brokerIndex < brokerCount; brokerIndex++)
            {
                final ClusterBrokerFW broker = brokerResponseRO.tryWrap(buffer, progress, limit);
                if (broker == null)
                {
                    client.decoder = decodeIgnoreAll;
                    break decode;
                }

                progress = broker.limit();

                brokers.add(new ClusterBrokerInfo(
                    broker.brokerId(), broker.host().asString(), broker.port(), broker.rack().asString()));
            }

            final DescribeClusterResponsePart2FW responsePart2 =
                describeClusterResponsePart2RO.wrap(buffer, progress, limit);
            final int authorizedOperations = responsePart2.clusterAuthorizedOperations();

            progress = responsePart2.limit();

            client.onDecodeDescribeClusterResponse(traceId, authorization, throttle,
                error, message, clusterId, controllerId, brokers, authorizedOperations);
        }

        return progress;
    }

    private int decodeReject(
        KafkaDescribeClusterClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        client.cleanupNetwork(traceId);
        client.decoder = decodeIgnoreAll;
        return limit;
    }

    private int decodeIgnoreAll(
        KafkaDescribeClusterClient client,
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

    private final class KafkaDescribeClusterStream
    {
        private final MessageConsumer application;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final KafkaDescribeClusterClient client;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private long replyBudgetId;

        KafkaDescribeClusterStream(
            MessageConsumer application,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long resolvedId,
            int authorizedOperations,
            List<KafkaServerConfig> servers,
            KafkaSaslConfig sasl)
        {
            this.application = application;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.client = new KafkaDescribeClusterClient(this, routedId, resolvedId, authorizedOperations, servers, sasl);
        }

        private void onApplication(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onApplicationBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onApplicationData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onApplicationEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onApplicationAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onApplicationWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onApplicationReset(reset);
                break;
            default:
                break;
            }
        }

        private void onApplicationBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            state = KafkaState.openingInitial(state);

            client.doNetworkBegin(traceId, authorization, affinity);

            doApplicationWindow(traceId,  0L, 0, 0, 0);
        }

        private void onApplicationData(
            DataFW data)
        {
            final long traceId = data.traceId();

            client.cleanupNetwork(traceId);
        }

        private void onApplicationEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = KafkaState.closedInitial(state);

            client.doNetworkEnd(traceId, authorization);
        }

        private void onApplicationAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedInitial(state);

            client.doNetworkAbort(traceId);
        }

        private void onApplicationWindow(
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

        private void onApplicationReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedReply(state);

            client.doNetworkReset(traceId);
        }

        private boolean isApplicationReplyOpen()
        {
            return KafkaState.replyOpening(state);
        }

        private void doApplicationBegin(
            long traceId,
            long authorization,
            int throttle,
            short error,
            String message,
            String clusterId,
            int controllerId,
            List<ClusterBrokerInfo> brokers,
            int authorizedOperations)
        {
            if (!KafkaState.replyOpening(state))
            {
                state = KafkaState.openingReply(state);

                doBegin(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity,
                    ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                                                            .typeId(kafkaTypeId)
                                                            .response(r -> r
                                                                .describeCluster(
                                                                    dc -> dc
                                                                        .throttle(throttle)
                                                                        .error(error)
                                                                        .message(message)
                                                                        .clusterId(clusterId)
                                                                        .controllerId(controllerId)
                                                                        .brokers(t ->
                                                                            brokers.forEach(bs ->
                                                                                t.item(i -> i
                                                                                    .brokerId(bs.brokerId)
                                                                                    .host(bs.host)
                                                                                    .port(bs.port)
                                                                                    .rack(bs.rack))))
                                                                        .authorizedOperations(authorizedOperations)))
                                                            .build()
                                                            .sizeof()));
            }
        }


        private void doApplicationEnd(
            long traceId)
        {
            state = KafkaState.closedReply(state);
            doEnd(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, client.authorization, EMPTY_EXTENSION);
        }

        private void doApplicationAbort(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                state = KafkaState.closedReply(state);
                doAbort(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, client.authorization, EMPTY_EXTENSION);
            }
        }

        private void doApplicationWindow(
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

        private void doApplicationReset(
            long traceId,
            Flyweight extension)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                state = KafkaState.closedInitial(state);

                doReset(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, client.authorization, extension);
            }
        }

        private void cleanupApplication(
            long traceId,
            int error)
        {
            final KafkaResetExFW kafkaResetEx = kafkaResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .error(error)
                .build();

            cleanupApplication(traceId, kafkaResetEx);
        }

        private void cleanupApplication(
            long traceId,
            Flyweight extension)
        {
            doApplicationReset(traceId, extension);
            doApplicationAbort(traceId);
        }
    }

    private final class KafkaDescribeClusterClient extends KafkaSaslClient
    {
        private final LongLongConsumer encodeSaslHandshakeRequest = this::doEncodeSaslHandshakeRequest;
        private final LongLongConsumer encodeSaslAuthenticateRequest = this::doEncodeSaslAuthenticateRequest;
        private final LongLongConsumer encodeDescribeClusterRequest = this::doEncodeDescribeClusterRequest;

        private final KafkaDescribeClusterStream delegate;
        private final int authorizedOperations;

        private MessageConsumer network;
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

        private BudgetDebitor initialDeb;
        private KafkaDescribeClusterClientDecoder decoder;
        private LongLongConsumer encoder;

        KafkaDescribeClusterClient(
            KafkaDescribeClusterStream delegate,
            long originId,
            long routedId,
            int authorizedOperations,
            List<KafkaServerConfig> servers,
            KafkaSaslConfig sasl)
        {
            super(servers, sasl, originId, routedId);
            this.delegate = delegate;
            this.authorizedOperations = authorizedOperations;
            this.encoder = sasl != null ? encodeSaslHandshakeRequest : encodeDescribeClusterRequest;

            this.decoder = decodeReject;
        }

        private void onNetwork(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetworkBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onNetworkData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetworkEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetworkAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetworkReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetworkWindow(window);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onNetworkSignal(signal);
                break;
            default:
                break;
            }
        }

        private void onNetworkBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            authorization = begin.authorization();
            state = KafkaState.openingReply(state);

            doNetworkWindow(traceId, 0L, 0, 0, decodePool.slotCapacity());
        }

        private void onNetworkData(
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
                cleanupNetwork(traceId);
            }
            else
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNetwork(traceId);
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

                    decodeNetwork(traceId, authorization, budgetId, reserved, buffer, offset, limit);
                }
            }
        }

        private void onNetworkEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedReply(state);

            cleanupDecodeSlotIfNecessary();

            if (!delegate.isApplicationReplyOpen())
            {
                cleanupNetwork(traceId);
            }
            else if (decodeSlot == NO_SLOT)
            {
                delegate.doApplicationEnd(traceId);
            }
        }

        private void onNetworkAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedReply(state);

            cleanupNetwork(traceId);
        }

        private void onNetworkReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedInitial(state);

            cleanupNetwork(traceId);
        }

        private void onNetworkWindow(
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
                initialDebIndex = initialDeb.acquire(initialBudgetId, initialId, this::doNetworkData);
                assert initialDebIndex != NO_DEBITOR_INDEX;
            }

            doNetworkData(budgetId);

            doEncodeRequest(traceId, budgetId);
        }

        private void doNetworkData(
            long traceId)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer buffer = encodePool.buffer(encodeSlot);
                final int limit = encodeSlotOffset;

                encodeNetwork(traceId, authorization, initialBudgetId, buffer, 0, limit);
            }
        }

        private void onNetworkSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final int signalId = signal.signalId();

            if (signalId == SIGNAL_NEXT_REQUEST)
            {
                doEncodeRequest(traceId, initialBudgetId);
            }
        }

        private void doNetworkBegin(
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

            network = newStream(this::onNetwork, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, extension);
        }

        @Override
        protected void doNetworkData(
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

            encodeNetwork(traceId, authorization, budgetId, buffer, offset, limit);
        }

        private void doNetworkEnd(
            long traceId,
            long authorization)
        {
            state = KafkaState.closedInitial(state);

            cleanupEncodeSlotIfNecessary();
            cleanupBudgetIfNecessary();

            doEnd(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, EMPTY_EXTENSION);
        }

        private void doNetworkAbort(
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

        private void doNetworkReset(
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

        private void doNetworkWindow(
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

        private void doEncodeRequest(
            long traceId,
            long budgetId)
        {
            if (nextRequestId == nextResponseId)
            {
                encoder.accept(traceId, budgetId);
            }
        }

        private void doEncodeDescribeClusterRequest(
            long traceId,
            long budgetId)
        {
            final MutableDirectBuffer encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader = requestHeaderRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .length(0)
                .apiKey(CREATE_TOPICS_API_KEY)
                .apiVersion(CREATE_TOPICS_API_VERSION)
                .correlationId(0)
                .clientId(clientId)
                .build();

            encodeProgress = requestHeader.limit();

            final DescribeClusterRequestFW describeClusterRequest =
                describeClusterRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .taggedFields(0)
                    .build();

            encodeProgress = describeClusterRequest.limit();

            DescribeClusterRequestPart2FW describeClusterRequestPart2 =
                describeClusterRequestPart2RW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .includeAuthorizedOperations(authorizedOperations)
                    .taggedFields(0)
                    .build();

            encodeProgress = describeClusterRequestPart2.limit();

            final int requestId = nextRequestId++;
            final int requestSize = encodeProgress - encodeOffset - RequestHeaderFW.FIELD_OFFSET_API_KEY;

            requestHeaderRW.wrap(encodeBuffer, requestHeader.offset(), requestHeader.limit())
                .length(requestSize)
                .apiKey(requestHeader.apiKey())
                .apiVersion(requestHeader.apiVersion())
                .correlationId(requestId)
                .clientId(requestHeader.clientId())
                .build();

            doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

            decoder = decodeDescribeClusterResponse;
        }

        private void encodeNetwork(
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
                    cleanupNetwork(traceId);
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

        private void decodeNetwork(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int limit)
        {
            KafkaDescribeClusterClientDecoder previous = null;
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
                    cleanupNetwork(traceId);
                }
                else
                {
                    final MutableDirectBuffer decodeBuffer = decodePool.buffer(decodeSlot);
                    decodeBuffer.putBytes(0, buffer, progress, limit - progress);
                    decodeSlotOffset = limit - progress;
                    decodeSlotReserved = (limit - progress) * reserved / (limit - offset);
                }

                doNetworkWindow(traceId, budgetId, decodeSlotOffset, 0, replyMax);
            }
            else
            {
                cleanupDecodeSlotIfNecessary();

                if (KafkaState.replyClosing(state))
                {
                    delegate.doApplicationEnd(traceId);
                }
                else if (reserved > 0)
                {
                    doNetworkWindow(traceId, budgetId, 0, 0, replyMax);
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
                encoder = encodeSaslAuthenticateRequest;
                decoder = decodeSaslAuthenticateResponse;
                break;
            default:
                delegate.cleanupApplication(traceId, errorCode);
                doNetworkEnd(traceId, authorization);
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
                encoder = encodeDescribeClusterRequest;
                decoder = decodeDescribeClusterResponse;
                break;
            default:
                delegate.cleanupApplication(traceId, errorCode);
                doNetworkEnd(traceId, authorization);
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

        private void onDecodeDescribeClusterResponse(
            long traceId,
            long authorization,
            int throttle,
            short error,
            String message,
            String clusterId,
            int controllerId,
            List<ClusterBrokerInfo> brokers,
            int authorizedOperations)
        {
            delegate.doApplicationBegin(traceId, authorization, throttle, error, message,
                clusterId, controllerId, brokers, authorizedOperations);
        }

        private void cleanupNetwork(
            long traceId)
        {
            doNetworkReset(traceId);
            doNetworkAbort(traceId);

            delegate.cleanupApplication(traceId, ERROR_NONE);
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

    private record ClusterBrokerInfo(
        int brokerId,
        String host,
        int port,
        String rack)
    {
    }
}
