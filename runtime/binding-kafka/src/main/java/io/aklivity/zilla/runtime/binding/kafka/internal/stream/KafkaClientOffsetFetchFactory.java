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
import static java.util.Objects.requireNonNull;

import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.UnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.IntHashSet;
import org.agrona.collections.LongLongConsumer;
import org.agrona.collections.ObjectHashSet;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.RequestHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.ResponseHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.OffsetFetchErrorResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.OffsetFetchPartitionResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.OffsetFetchRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.OffsetFetchResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.OffsetFetchTopicPartitionRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.OffsetFetchTopicRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.OffsetFetchTopicResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaOffsetFetchBeginExFW;
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

public final class KafkaClientOffsetFetchFactory extends KafkaClientSaslHandshaker implements BindingHandler
{
    private static final int ERROR_NONE = 0;
    private static final int ERROR_UNKNOWN_PARTITION = 3;
    private static final int ERROR_UNAUTHORIZED_PARTITION = 29;

    private static final int SIGNAL_NEXT_REQUEST = 1;

    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer();
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private static final short OFFSET_FETCH_API_KEY = 9;
    private static final short OFFSET_FETCH_API_VERSION = 5;

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
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final KafkaResetExFW.Builder kafkaResetExRW = new KafkaResetExFW.Builder();

    private final RequestHeaderFW.Builder requestHeaderRW = new RequestHeaderFW.Builder();
    private final OffsetFetchRequestFW.Builder offsetFetchRequestRW = new OffsetFetchRequestFW.Builder();
    private final OffsetFetchTopicRequestFW.Builder offsetFetchTopicRequestRW = new OffsetFetchTopicRequestFW.Builder();
    private final OffsetFetchTopicPartitionRequestFW.Builder partitionIndexRW =
        new OffsetFetchTopicPartitionRequestFW.Builder();

    private final ResponseHeaderFW responseHeaderRO = new ResponseHeaderFW();
    private final OffsetFetchResponseFW offsetFetchResponseRO = new OffsetFetchResponseFW();
    private final OffsetFetchTopicResponseFW offsetFetchTopicResponseRO = new OffsetFetchTopicResponseFW();
    private final OffsetFetchPartitionResponseFW offsetFetchPartitionResponseRO =
        new OffsetFetchPartitionResponseFW();
    private final OffsetFetchErrorResponseFW offsetFetchErrorResponseRO = new OffsetFetchErrorResponseFW();

    private final KafkaOffsetFetchClientDecoder decodeSaslHandshakeResponse = this::decodeSaslHandshakeResponse;
    private final KafkaOffsetFetchClientDecoder decodeSaslHandshake = this::decodeSaslHandshake;
    private final KafkaOffsetFetchClientDecoder decodeSaslHandshakeMechanisms = this::decodeSaslHandshakeMechanisms;
    private final KafkaOffsetFetchClientDecoder decodeSaslHandshakeMechanism = this::decodeSaslHandshakeMechanism;
    private final KafkaOffsetFetchClientDecoder decodeSaslAuthenticateResponse = this::decodeSaslAuthenticateResponse;
    private final KafkaOffsetFetchClientDecoder decodeSaslAuthenticate = this::decodeSaslAuthenticate;
    private final KafkaOffsetFetchClientDecoder decodeOffsetFetchResponse = this::decodeOffsetFetchResponse;
    private final KafkaOffsetFetchClientDecoder decodeOffsetFetchTopics = this::decodeOffsetFetchTopics;
    private final KafkaOffsetFetchClientDecoder decodeOffsetFetchTopic = this::decodeOffsetFetchTopic;
    private final KafkaOffsetFetchClientDecoder decodeOffsetFetchPartitions = this::decodeOffsetFetchPartitions;
    private final KafkaOffsetFetchClientDecoder decodeOffsetFetchPartition = this::decodeOffsetFetchPartition;
    private final KafkaOffsetFetchClientDecoder decodeOffsetFetchError = this::decodeOffsetFetchError;

    private final KafkaOffsetFetchClientDecoder decodeIgnoreAll = this::decodeIgnoreAll;
    private final KafkaOffsetFetchClientDecoder decodeReject = this::decodeReject;

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

    public KafkaClientOffsetFetchFactory(
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

        assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_OFFSET_FETCH;
        final KafkaOffsetFetchBeginExFW kafkaOffsetFetchBeginEx = kafkaBeginEx.offsetFetch();
        final String groupId = kafkaOffsetFetchBeginEx.groupId().asString();
        final String host = kafkaOffsetFetchBeginEx.host().asString();
        final int port = kafkaOffsetFetchBeginEx.port();
        final String topic = kafkaOffsetFetchBeginEx.topic().asString();
        IntHashSet partitions = new IntHashSet();
        kafkaOffsetFetchBeginEx.partitions().forEach(p -> partitions.add(p.partitionId()));

        MessageConsumer newStream = null;

        final KafkaBindingConfig binding = supplyBinding.apply(routedId);
        final KafkaRouteConfig resolved = binding != null ?
            binding.resolve(authorization, null, groupId) : null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;
            final KafkaSaslConfig sasl = resolveSasl.apply(binding.sasl());

            newStream = new KafkaOffsetFetchStream(
                    application,
                    originId,
                    routedId,
                    initialId,
                    affinity,
                    resolvedId,
                    groupId,
                    host,
                    port,
                    topic,
                    partitions,
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
    private interface KafkaOffsetFetchClientDecoder
    {
        int decode(
            KafkaOffsetFetchClient client,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int progress,
            int limit);
    }

    private int decodeOffsetFetchResponse(
        KafkaOffsetFetchClient client,
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

            client.decodeableResponseBytes = responseHeader.length();

            final OffsetFetchResponseFW offsetFetchResponse = offsetFetchResponseRO.tryWrap(buffer, progress, limit);
            if (offsetFetchResponse == null)
            {
                break decode;
            }

            progress = offsetFetchResponse.limit();

            client.decodeableResponseBytes -= offsetFetchResponse.sizeof();
            assert client.decodeableResponseBytes >= 0;

            client.decodeableTopics = offsetFetchResponse.topicCount();
            client.decoder = decodeOffsetFetchTopics;
        }

        return progress;
    }

    private int decodeOffsetFetchTopics(
        KafkaOffsetFetchClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        if (client.decodeableTopics == 0)
        {
            client.decoder = decodeOffsetFetchError;
        }
        else
        {
            client.decoder = decodeOffsetFetchTopic;
        }

        return progress;
    }

    private int decodeOffsetFetchTopic(
        KafkaOffsetFetchClient client,
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
            final OffsetFetchTopicResponseFW topicOffsetFetch = offsetFetchTopicResponseRO.tryWrap(buffer, progress, limit);
            if (topicOffsetFetch == null)
            {
                break decode;
            }

            final String topic = topicOffsetFetch.name().asString();

            progress = topicOffsetFetch.limit();

            if (topic.equals(client.topic))
            {
                client.onDecodeTopic(traceId);

                client.decodeableResponseBytes -= topicOffsetFetch.sizeof();
                assert client.decodeableResponseBytes >= 0;

                client.decodeablePartitions = topicOffsetFetch.partitionCount();
                client.decoder = decodeOffsetFetchPartitions;
            }
            else
            {
                client.decoder = decodeReject;
            }

        }

        return progress;
    }

    private int decodeOffsetFetchPartitions(
        KafkaOffsetFetchClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        if (client.decodeablePartitions == 0)
        {
            client.decodeableTopics--;
            assert client.decodeableTopics >= 0;

            client.decoder = decodeOffsetFetchTopics;
            client.onDecodeOffsetFetchResponse(traceId);
        }
        else
        {
            client.decoder = decodeOffsetFetchPartition;
        }

        return progress;
    }

    private int decodeOffsetFetchPartition(
        KafkaOffsetFetchClient client,
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
            final OffsetFetchPartitionResponseFW partition = offsetFetchPartitionResponseRO.tryWrap(buffer, progress, limit);
            if (partition == null)
            {
                break decode;
            }

            progress = partition.limit();

            final short errorCode = partition.errorCode();
            switch (errorCode)
            {
            case ERROR_NONE:
                client.onDecodePartition(partition);
                client.decoder = decodeOffsetFetchPartitions;
                break;
            case ERROR_UNAUTHORIZED_PARTITION:
            case ERROR_UNKNOWN_PARTITION:
                client.decoder = decodeOffsetFetchPartitions;
                break;
            default:
                client.errorCode = errorCode;
                client.decoder = decodeReject;
                break;
            }

            client.decodeableResponseBytes -= partition.sizeof();
            assert client.decodeableResponseBytes >= 0;

            client.decodeablePartitions--;
            assert client.decodeablePartitions >= 0;
        }

        return progress;
    }

    private int decodeOffsetFetchError(
        KafkaOffsetFetchClient client,
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
            final OffsetFetchErrorResponseFW error = offsetFetchErrorResponseRO.tryWrap(buffer, progress, limit);

            if (error == null)
            {
                break decode;
            }

            progress = error.limit();

            if (error.code() == ERROR_NONE)
            {
                client.decodeableResponseBytes -= error.sizeof();
                assert client.decodeableResponseBytes >= 0;

                client.decoder = decodeOffsetFetchResponse;
                client.nextResponseId++;
            }
            else
            {
                client.errorCode = error.code();
                client.decoder = decodeReject;
            }
        }

        return progress;
    }

    private int decodeReject(
        KafkaOffsetFetchClient client,
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
        KafkaOffsetFetchClient client,
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

    private final class KafkaOffsetFetchStream
    {
        private final MessageConsumer application;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final KafkaOffsetFetchClient client;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private long replyBudgetId;

        KafkaOffsetFetchStream(
            MessageConsumer application,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long resolvedId,
            String groupId,
            String host,
            int port,
            String topic,
            IntHashSet partitions,
            KafkaSaslConfig sasl)
        {
            this.application = application;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.client = new KafkaOffsetFetchClient(this, routedId, resolvedId, groupId, host, port,
                topic, partitions, sasl);
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

            doApplicationBeginIfNecessary(traceId, authorization);
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

            client.doNetworkAbortIfNecessary(traceId);
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

            state = KafkaState.closedInitial(state);

            client.doNetworkResetIfNecessary(traceId);
        }

        private boolean isApplicationReplyOpen()
        {
            return KafkaState.replyOpening(state);
        }

        private void doApplicationBeginIfNecessary(
            long traceId,
            long authorization)
        {
            if (!KafkaState.replyOpening(state))
            {
                doApplicationBegin(traceId, authorization);
            }
        }

        private void doApplicationBegin(
            long traceId,
            long authorization)
        {
            state = KafkaState.openingReply(state);

            doBegin(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, EMPTY_EXTENSION);
        }

        private void doApplicationData(
            long traceId,
            long authorization,
            KafkaDataExFW extension)
        {
            final int reserved = replyPad;

            doData(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, replyBudgetId, reserved, EMPTY_BUFFER, 0, 0,
                extension);

            replySeq += reserved;

            assert replyAck <= replySeq;
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
            state = KafkaState.closedReply(state);
            doAbort(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, client.authorization, EMPTY_EXTENSION);
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
            state = KafkaState.closedInitial(state);

            doReset(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, client.authorization, extension);
        }

        private void doApplicationAbortIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doApplicationAbort(traceId);
            }
        }

        private void doApplicationResetIfNecessary(
            long traceId,
            Flyweight extension)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                doApplicationReset(traceId, extension);
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
            doApplicationResetIfNecessary(traceId, extension);
            doApplicationAbortIfNecessary(traceId);
        }
    }

    private final class KafkaOffsetFetchClient extends KafkaSaslClient
    {
        private final LongLongConsumer encodeSaslHandshakeRequest = this::doEncodeSaslHandshakeRequest;
        private final LongLongConsumer encodeSaslAuthenticateRequest = this::doEncodeSaslAuthenticateRequest;
        private final LongLongConsumer encodeOffsetFetchRequest = this::doEncodeOffsetFetchRequest;

        private final KafkaOffsetFetchStream delegate;
        private final String groupId;
        private final String host;
        private final int port;
        private final String topic;
        private final IntHashSet partitions;
        private final ObjectHashSet<KafkaPartitionOffset> topicPartitions;
        private short errorCode;

        private MessageConsumer network;
        private int state;
        private int decodeableResponseBytes;
        private int decodeableTopics;
        private int decodeablePartitions;
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
        private KafkaOffsetFetchClientDecoder decoder;
        private LongLongConsumer encoder;

        KafkaOffsetFetchClient(
            KafkaOffsetFetchStream delegate,
            long originId,
            long routedId,
            String groupId,
            String host,
            int port,
            String topic,
            IntHashSet partitions,
            KafkaSaslConfig sasl)
        {
            super(sasl, originId, routedId);
            this.delegate = delegate;
            this.groupId = requireNonNull(groupId);
            this.host = host;
            this.port = port;
            this.topic = topic;
            this.partitions = partitions;
            this.topicPartitions = new ObjectHashSet<>();
            this.encoder = sasl != null ? encodeSaslHandshakeRequest : encodeOffsetFetchRequest;

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
                initialDebIndex = initialDeb.acquire(initialBudgetId, initialId, this::doNetworkDataIfNecessary);
                assert initialDebIndex != NO_DEBITOR_INDEX;
            }

            doNetworkDataIfNecessary(budgetId);

            doEncodeRequestIfNecessary(traceId, budgetId);
        }

        private void doNetworkDataIfNecessary(
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
                doEncodeRequestIfNecessary(traceId, initialBudgetId);
            }
        }

        private void doNetworkBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            state = KafkaState.openingInitial(state);

            Consumer<OctetsFW.Builder> extension =  e -> e.set((b, o, l) -> proxyBeginExRW.wrap(b, o, l)
                .typeId(proxyTypeId)
                .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                    .source("0.0.0.0")
                    .destination(host)
                    .sourcePort(0)
                    .destinationPort(port)))
                .infos(i -> i.item(ii -> ii.authority(host)))
                .build()
                .sizeof());

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

        private void doNetworkAbortIfNecessary(
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

        private void doNetworkResetIfNecessary(
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

        private void doEncodeRequestIfNecessary(
            long traceId,
            long budgetId)
        {
            if (nextRequestId == nextResponseId)
            {
                encoder.accept(traceId, budgetId);
            }
        }

        private void doEncodeOffsetFetchRequest(
            long traceId,
            long budgetId)
        {
            if (KafkaConfiguration.DEBUG)
            {
                System.out.format("[client] %s OFFSET FETCH\n", groupId);
            }

            final MutableDirectBuffer encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader = requestHeaderRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .length(0)
                .apiKey(OFFSET_FETCH_API_KEY)
                .apiVersion(OFFSET_FETCH_API_VERSION)
                .correlationId(0)
                .clientId(clientId)
                .build();

            encodeProgress = requestHeader.limit();

            final OffsetFetchRequestFW offsetFetchRequest =
                offsetFetchRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .groupId(groupId)
                    .topicCount(1)
                    .build();

            encodeProgress = offsetFetchRequest.limit();


            final OffsetFetchTopicRequestFW offsetFetchTopicRequest =
                offsetFetchTopicRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .name(topic)
                    .partitionsCount(partitions.size())
                    .build();
            encodeProgress = offsetFetchTopicRequest.limit();

            for (int partition : partitions)
            {
                final OffsetFetchTopicPartitionRequestFW partitionIndex =
                    partitionIndexRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .partitionIndex(partition)
                        .build();
                encodeProgress = partitionIndex.limit();
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

            doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

            decoder = decodeOffsetFetchResponse;
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
            final int lengthMin = Math.min(length, 1024);
            final int initialBudget = Math.max(initialMax - (int)(initialSeq - initialAck), 0);
            final int reservedMax = Math.max(Math.min(length + initialPad, initialBudget), initialMin);
            final int reservedMin = Math.max(Math.min(lengthMin + initialPad, reservedMax), initialMin);

            int reserved = reservedMax;

            flush:
            if (reserved > 0)
            {

                boolean claimed = false;

                if (initialDebIndex != NO_DEBITOR_INDEX)
                {
                    final int lengthMax = Math.min(reserved - initialPad, length);
                    final int deferredMax = length - lengthMax;
                    reserved = initialDeb.claim(traceId, initialDebIndex, initialId, reservedMin, reserved, deferredMax);
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
            KafkaOffsetFetchClientDecoder previous = null;
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
                encoder = encodeOffsetFetchRequest;
                decoder = decodeOffsetFetchResponse;
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

        private void onDecodeOffsetFetchResponse(
            long traceId)
        {
            if (!topicPartitions.isEmpty())
            {
                delegate.doApplicationWindow(traceId, 0L, 0, 0, 0);

                final KafkaDataExFW kafkaDataEx = kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .offsetFetch(m -> m
                        .partitions(o -> topicPartitions.forEach(tp ->
                            o.item(to -> to
                                .partitionId(tp.partitionId)
                                .partitionOffset(tp.partitionOffset)
                                .leaderEpoch(tp.leaderEpoch)
                                .metadata(tp.metadata)
                            ))))
                    .build();

                delegate.doApplicationData(traceId, authorization, kafkaDataEx);
            }
        }

        public void onDecodeTopic(
            long traceId)
        {
            topicPartitions.clear();
        }

        public void onDecodePartition(
            OffsetFetchPartitionResponseFW partition)
        {
            topicPartitions.add(new KafkaPartitionOffset(
                partition.partitionIndex(),
                partition.committedOffset(),
                0,
                partition.committedLeaderEpoch(),
                partition.metadata().asString()));
        }

        private void cleanupNetwork(
            long traceId)
        {
            doNetworkResetIfNecessary(traceId);
            doNetworkAbortIfNecessary(traceId);

            delegate.cleanupApplication(traceId, errorCode);
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
                initialDeb.release(initialDebIndex, initialBudgetId);
                initialDebIndex = NO_DEBITOR_INDEX;
            }
        }
    }

}
