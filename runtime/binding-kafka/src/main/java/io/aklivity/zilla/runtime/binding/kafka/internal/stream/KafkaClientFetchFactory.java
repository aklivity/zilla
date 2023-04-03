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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.kafka.internal.types.ProxyAddressProtocol.STREAM;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.offsets.IsolationLevel.READ_UNCOMMITTED;
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.util.Objects.requireNonNull;

import java.util.function.Consumer;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.LongLongConsumer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaIsolation;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaTransactionResult;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Varint32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.RequestHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.ResponseHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.fetch.ControlRecordKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.fetch.ControlRecordKeyType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.fetch.FetchRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.fetch.FetchResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.fetch.PartitionRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.fetch.PartitionResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.fetch.TopicRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.fetch.TopicResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.fetch.TransactionResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.message.MessageHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.message.RecordBatchFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.message.RecordHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.message.RecordSetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.message.RecordTrailerFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.offsets.OffsetsPartitionRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.offsets.OffsetsPartitionResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.offsets.OffsetsRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.offsets.OffsetsResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.offsets.OffsetsTopicRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.offsets.OffsetsTopicResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFetchBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFlushExFW;
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

public final class KafkaClientFetchFactory extends KafkaClientSaslHandshaker implements BindingHandler
{
    private static final int FIELD_LIMIT_RECORD_BATCH_LENGTH = RecordBatchFW.FIELD_OFFSET_LEADER_EPOCH;

    private static final int ERROR_NONE = 0;
    private static final int ERROR_OFFSET_OUT_OF_RANGE = 1;
    private static final int ERROR_NOT_LEADER_FOR_PARTITION = 6;

    private static final int FLAG_CONT = 0x00;
    private static final int FLAG_FIN = 0x01;
    private static final int FLAG_INIT = 0x02;
    private static final int FLAG_SKIP = 0x08;

    private static final long OFFSET_LIVE = KafkaOffsetType.LIVE.value();
    private static final long OFFSET_HISTORICAL = KafkaOffsetType.HISTORICAL.value();

    private static final int SIGNAL_NEXT_REQUEST = 1;

    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer();
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private static final short OFFSETS_API_KEY = 2;
    private static final short OFFSETS_API_VERSION = 2;

    private static final short FETCH_API_KEY = 1;
    private static final short FETCH_API_VERSION = 5;

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
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final KafkaFlushExFW.Builder kafkaFlushExRW = new KafkaFlushExFW.Builder();
    private final KafkaResetExFW.Builder kafkaResetExRW = new KafkaResetExFW.Builder();
    private final ProxyBeginExFW.Builder proxyBeginExRW = new ProxyBeginExFW.Builder();

    private final RequestHeaderFW.Builder requestHeaderRW = new RequestHeaderFW.Builder();
    private final OffsetsRequestFW.Builder offsetsRequestRW = new OffsetsRequestFW.Builder();
    private final OffsetsTopicRequestFW.Builder offsetsTopicRequestRW = new OffsetsTopicRequestFW.Builder();
    private final OffsetsPartitionRequestFW.Builder offsetsPartitionRequestRW = new OffsetsPartitionRequestFW.Builder();
    private final FetchRequestFW.Builder fetchRequestRW = new FetchRequestFW.Builder();
    private final TopicRequestFW.Builder fetchTopicRequestRW = new TopicRequestFW.Builder();
    private final PartitionRequestFW.Builder fetchPartitionRequestRW = new PartitionRequestFW.Builder();

    private final ResponseHeaderFW responseHeaderRO = new ResponseHeaderFW();
    private final OffsetsResponseFW offsetsResponseRO = new OffsetsResponseFW();
    private final OffsetsTopicResponseFW offsetsTopicResponseRO = new OffsetsTopicResponseFW();
    private final OffsetsPartitionResponseFW offsetsPartitionResponseRO = new OffsetsPartitionResponseFW();
    private final FetchResponseFW fetchResponseRO = new FetchResponseFW();
    private final TopicResponseFW topicResponseRO = new TopicResponseFW();
    private final PartitionResponseFW partitionResponseRO = new PartitionResponseFW();
    private final TransactionResponseFW transactionResponseRO = new TransactionResponseFW();
    private final RecordSetFW recordSetRO = new RecordSetFW();
    private final RecordBatchFW recordBatchRO = new RecordBatchFW();
    private final Varint32FW recordLengthRO = new Varint32FW();
    private final RecordHeaderFW recordHeaderRO = new RecordHeaderFW();
    private final RecordTrailerFW recordTrailerRO = new RecordTrailerFW();
    private final MessageHeaderFW messageHeaderRO = new MessageHeaderFW();
    private final ControlRecordKeyFW controlRecordKeyRO = new ControlRecordKeyFW();
    private final OctetsFW valueRO = new OctetsFW();
    private final DirectBuffer headersRO = new UnsafeBuffer();

    private final KafkaFetchClientDecoder decodeSaslHandshakeResponse = this::decodeSaslHandshakeResponse;
    private final KafkaFetchClientDecoder decodeSaslHandshake = this::decodeSaslHandshake;
    private final KafkaFetchClientDecoder decodeSaslHandshakeMechanisms = this::decodeSaslHandshakeMechanisms;
    private final KafkaFetchClientDecoder decodeSaslHandshakeMechanism = this::decodeSaslHandshakeMechanism;
    private final KafkaFetchClientDecoder decodeSaslAuthenticateResponse = this::decodeSaslAuthenticateResponse;
    private final KafkaFetchClientDecoder decodeSaslAuthenticate = this::decodeSaslAuthenticate;
    private final KafkaFetchClientDecoder decodeOffsetsResponse = this::decodeOffsetsResponse;
    private final KafkaFetchClientDecoder decodeOffsets = this::decodeOffsets;
    private final KafkaFetchClientDecoder decodeOffsetsTopics = this::decodeOffsetsTopics;
    private final KafkaFetchClientDecoder decodeOffsetsTopic = this::decodeOffsetsTopic;
    private final KafkaFetchClientDecoder decodeOffsetsPartitions = this::decodeOffsetsPartitions;
    private final KafkaFetchClientDecoder decodeOffsetsPartition = this::decodeOffsetsPartition;
    private final KafkaFetchClientDecoder decodeFetchResponse = this::decodeFetchResponse;
    private final KafkaFetchClientDecoder decodeFetch = this::decodeFetch;
    private final KafkaFetchClientDecoder decodeFetchTopic = this::decodeFetchTopic;
    private final KafkaFetchClientDecoder decodeFetchPartition = this::decodeFetchPartition;
    private final KafkaFetchClientDecoder decodeFetchTransaction = this::decodeFetchTransaction;
    private final KafkaFetchClientDecoder decodeFetchRecordSet = this::decodeFetchRecordSet;
    private final KafkaFetchClientDecoder decodeFetchRecordBatch = this::decodeFetchRecordBatch;
    private final KafkaFetchClientDecoder decodeFetchRecordLength = this::decodeFetchRecordLength;
    private final KafkaFetchClientDecoder decodeFetchRecord = this::decodeFetchRecord;
    private final KafkaFetchClientDecoder decodeFetchRecordInit = this::decodeFetchRecordInit;
    private final KafkaFetchClientDecoder decodeFetchRecordValue = this::decodeFetchRecordValue;
    private final KafkaFetchClientDecoder decodeIgnoreRecord = this::decodeIgnoreRecord;
    private final KafkaFetchClientDecoder decodeIgnoreRecordBatch = this::decodeIgnoreRecordBatch;
    private final KafkaFetchClientDecoder decodeIgnoreRecordSet = this::decodeIgnoreRecordSet;
    private final KafkaFetchClientDecoder decodeIgnoreAll = this::decodeIgnoreAll;
    private final KafkaFetchClientDecoder decodeReject = this::decodeReject;

    private final int fetchMaxBytes;
    private final int fetchMaxWaitMillis;
    private final int partitionMaxBytes;
    private final int kafkaTypeId;
    private final int proxyTypeId;
    private final MutableDirectBuffer extBuffer;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final Signaler signaler;
    private final BindingHandler streamFactory;
    private final LongFunction<MessageConsumer> supplyReceiver;
    private final LongFunction<KafkaBindingConfig> supplyBinding;
    private final LongFunction<BudgetDebitor> supplyDebitor;
    private final LongFunction<KafkaClientRoute> supplyClientRoute;
    private final int decodeMaxBytes;

    public KafkaClientFetchFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding,
        LongFunction<BudgetDebitor> supplyDebitor,
        LongFunction<KafkaClientRoute> supplyClientRoute)
    {
        super(config, context);
        this.fetchMaxBytes = config.clientFetchMaxBytes();
        this.fetchMaxWaitMillis = config.clientFetchMaxWaitMillis();
        this.partitionMaxBytes = config.clientFetchPartitionMaxBytes();
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.proxyTypeId = context.supplyTypeId("proxy");
        this.signaler = context.signaler();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool();
        this.streamFactory = context.streamFactory();
        this.supplyReceiver = context::supplyReceiver;
        this.supplyBinding = supplyBinding;
        this.supplyDebitor = supplyDebitor;
        this.supplyClientRoute = supplyClientRoute;
        this.decodeMaxBytes = decodePool.slotCapacity();
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
        final long leaderId = begin.affinity();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();
        final ExtensionFW beginEx = extensionRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
        final KafkaBeginExFW kafkaBeginEx = beginEx.typeId() == kafkaTypeId ? extension.get(kafkaBeginExRO::wrap) : null;
        assert kafkaBeginEx == null || kafkaBeginEx.kind() == KafkaBeginExFW.KIND_FETCH;
        final KafkaFetchBeginExFW kafkaFetchBeginEx = kafkaBeginEx != null ? kafkaBeginEx.fetch() : null;

        MessageConsumer newStream = null;

        if (beginEx != null && kafkaFetchBeginEx != null &&
            kafkaFetchBeginEx.filters().isEmpty())
        {
            final String16FW beginTopic = kafkaFetchBeginEx.topic();
            final String topicName = beginTopic.asString();

            final KafkaBindingConfig binding = supplyBinding.apply(routedId);
            final KafkaRouteConfig resolved = binding != null ? binding.resolve(authorization, topicName) : null;

            if (resolved != null)
            {
                final long resolvedId = resolved.id;
                final KafkaOffsetFW partition = kafkaFetchBeginEx.partition();
                final int partitionId = partition.partitionId();
                final long initialOffset = partition.partitionOffset();
                final long latestOffset = partition.latestOffset();
                final KafkaIsolation isolation = kafkaFetchBeginEx.isolation().get();
                final KafkaSaslConfig sasl = binding.sasl();

                newStream = new KafkaFetchStream(
                    application,
                    originId,
                    routedId,
                    initialId,
                    resolvedId,
                    topicName,
                    partitionId,
                    latestOffset,
                    leaderId,
                    initialOffset,
                    isolation,
                    sasl)::onApplication;
            }
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
        int flags,
        long budgetId,
        int reserved,
        OctetsFW payload,
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
                .flags(flags)
                .budgetId(budgetId)
                .reserved(reserved)
                .payload(payload)
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
        Flyweight extension)
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
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        Flyweight extension)
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
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doFlush(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        int reserved,
        Flyweight extension)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .budgetId(0L)
                .reserved(reserved)
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
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
    private interface KafkaFetchClientDecoder
    {
        int decode(
            KafkaFetchStream.KafkaFetchClient client,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int progress,
            int limit);
    }

    private int decodeOffsetsResponse(
        KafkaFetchStream.KafkaFetchClient client,
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
            final ResponseHeaderFW responseHeader = responseHeaderRO.tryWrap(buffer, progress, limit);
            if (responseHeader != null)
            {
                progress = responseHeader.limit();
                client.decodableResponseBytes = responseHeader.length();
                client.decoder = decodeOffsets;
            }
        }

        return progress;
    }

    private int decodeOffsets(
        KafkaFetchStream.KafkaFetchClient client,
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
            final OffsetsResponseFW offsetsResponse = offsetsResponseRO.tryWrap(buffer, progress, limit);

            if (offsetsResponse != null)
            {
                progress = offsetsResponse.limit();

                client.decodableResponseBytes -= offsetsResponse.sizeof();
                assert client.decodableResponseBytes >= 0;

                client.decodableTopics = offsetsResponse.topicCount();
                client.decoder = decodeOffsetsTopics;
            }
        }

        return progress;
    }

    private int decodeOffsetsTopics(
        KafkaFetchStream.KafkaFetchClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        if (client.decodableTopics == 0)
        {
            client.onDecodeFetchResponse(traceId);
            client.encoder = client.encodeFetchRequest;
            client.decoder = decodeFetchResponse;
        }
        else
        {
            client.decoder = decodeOffsetsTopic;
        }

        return progress;
    }

    private int decodeOffsetsTopic(
        KafkaFetchStream.KafkaFetchClient client,
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
            final OffsetsTopicResponseFW topic = offsetsTopicResponseRO.tryWrap(buffer, progress, limit);
            if (topic == null)
            {
                break decode;
            }

            final String topicName = topic.name().asString();
            assert client.topic.equals(topicName);

            progress = topic.limit();

            client.decodableResponseBytes -= topic.sizeof();
            assert client.decodableResponseBytes >= 0;

            client.decodablePartitions = topic.partitionCount();
            client.decoder = decodeOffsetsPartitions;
        }

        return progress;
    }

    private int decodeOffsetsPartitions(
        KafkaFetchStream.KafkaFetchClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        if (client.decodablePartitions == 0)
        {
            client.decodableTopics--;
            assert client.decodableTopics >= 0;

            client.decoder = decodeOffsetsTopics;
        }
        else
        {
            client.decoder = decodeOffsetsPartition;
        }

        return progress;
    }

    private int decodeOffsetsPartition(
        KafkaFetchStream.KafkaFetchClient client,
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
            final OffsetsPartitionResponseFW partition = offsetsPartitionResponseRO.tryWrap(buffer, progress, limit);
            if (partition == null)
            {
                break decode;
            }

            final int partitionId = partition.partitionId();
            final int errorCode = partition.errorCode();
            final long partitionOffset = partition.offset$();

            progress = partition.limit();

            client.decodableResponseBytes -= partition.sizeof();
            assert client.decodableResponseBytes >= 0;

            client.decodablePartitions--;
            assert client.decodablePartitions >= 0;

            client.onDecodeOffsetsPartition(traceId, authorization, errorCode, partitionId, partitionOffset);

            client.decoder = decodeOffsetsPartitions;
        }

        return progress;
    }

    private int decodeFetchResponse(
        KafkaFetchStream.KafkaFetchClient client,
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
            final ResponseHeaderFW responseHeader = responseHeaderRO.tryWrap(buffer, progress, limit);
            if (responseHeader != null)
            {
                progress = responseHeader.limit();
                client.decodableResponseBytes = responseHeader.length();
                client.decoder = decodeFetch;
            }
        }

        return progress;
    }

    private int decodeFetch(
        KafkaFetchStream.KafkaFetchClient client,
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
            final FetchResponseFW fetchResponse = fetchResponseRO.tryWrap(buffer, progress, limit);

            if (fetchResponse != null)
            {
                progress = fetchResponse.limit();

                client.decodableTopics = fetchResponse.topicCount();
                client.decodableResponseBytes -= fetchResponse.sizeof();
                assert client.decodableResponseBytes >= 0;
                client.decoder = decodeFetchTopic;
            }
        }

        return progress;
    }

    private int decodeFetchTopic(
        KafkaFetchStream.KafkaFetchClient client,
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
        if (client.decodableTopics == 0)
        {
            client.onDecodeFetchResponse(traceId);
            client.decoder = decodeFetchResponse;
            break decode;
        }
        else if (length != 0)
        {
            final TopicResponseFW topic = topicResponseRO.tryWrap(buffer, progress, limit);
            if (topic != null)
            {
                final String topicName = topic.name().asString();
                assert client.topic.equals(topicName);

                progress = topic.limit();

                client.decodableResponseBytes -= topic.sizeof();
                assert client.decodableResponseBytes >= 0;

                client.decodablePartitions = topic.partitionCount();
                client.decoder = decodeFetchPartition;
            }
        }

        return progress;
    }

    private int decodeFetchPartition(
        KafkaFetchStream.KafkaFetchClient client,
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
        if (client.decodablePartitions == 0)
        {
            client.decodableTopics--;
            assert client.decodableTopics >= 0;
            client.decoder = decodeFetchTopic;
            break decode;
        }
        else if (length != 0)
        {
            final PartitionResponseFW partition = partitionResponseRO.tryWrap(buffer, progress, limit);
            if (partition != null)
            {
                final int partitionId = partition.partitionId();
                final int errorCode = partition.errorCode();

                client.stableOffset = partition.lastStableOffset();
                client.latestOffset = partition.highWatermark() - 1;

                client.decodePartitionError = errorCode;
                client.decodePartitionId = partitionId;
                client.decodableTransactions = partition.abortedTransactionCount();

                progress = partition.limit();

                client.decodableResponseBytes -= partition.sizeof();
                assert client.decodableResponseBytes >= 0;

                client.onDecodeFetchPartition(traceId, authorization, partitionId, errorCode);

                client.decodeAbortedTransactions.clear();
                client.decoder = decodeFetchTransaction;
            }
        }

        return progress;
    }

    private int decodeFetchTransaction(
        KafkaFetchStream.KafkaFetchClient client,
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
        if (client.decodableTransactions <= 0)
        {
            client.decoder = decodeFetchRecordSet;
            break decode;
        }
        else if (length != 0)
        {
            final TransactionResponseFW transaction = transactionResponseRO.tryWrap(buffer, progress, limit);
            if (transaction != null)
            {
                progress = transaction.limit();

                final long producerId = transaction.producerId();
                final long firstOffset = transaction.firstOffset();

                client.decodeAbortedTransactions.put(firstOffset, producerId);

                client.decodableResponseBytes -= transaction.sizeof();
                assert client.decodableResponseBytes >= 0;
                client.decodableTransactions--;
                assert client.decodableTransactions >= 0;
            }
        }

        return progress;
    }

    private int decodeFetchRecordSet(
        KafkaFetchStream.KafkaFetchClient client,
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
            final RecordSetFW recordSet = recordSetRO.tryWrap(buffer, progress, limit);
            if (recordSet != null)
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[client] [0x%016x] %s[%d] FETCH RecordSet %d\n",
                            client.replyId, client.topic, client.partitionId, recordSet.length());
                }

                final int responseProgress = recordSet.sizeof();

                progress += responseProgress;

                client.decodableResponseBytes -= responseProgress;
                assert client.decodableResponseBytes >= 0;

                client.decodableRecordSetBytes = recordSet.length();
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[client] [0x%016x] %s[%d] FETCH Record Set Bytes %d\n",
                        client.replyId, client.topic, client.partitionId, client.decodableRecordSetBytes);
                }
                assert client.decodableRecordSetBytes >= 0 : "negative recordSetSize";
                assert client.decodableRecordSetBytes <= client.decodableResponseBytes : "record set overflows response";

                if (client.decodePartitionError != 0 || client.decodableRecordSetBytes == 0)
                {
                    client.decoder = decodeIgnoreRecordSet;
                }
                else
                {
                    client.decoder = decodeFetchRecordBatch;
                }
            }
        }

        return progress;
    }

    private int decodeFetchRecordBatch(
        KafkaFetchStream.KafkaFetchClient client,
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
        if (client.decodableRecordSetBytes == 0)
        {
            client.decodablePartitions--;
            assert client.decodablePartitions >= 0;
            client.decoder = decodeFetchPartition;
            break decode;
        }
        else if (length != 0)
        {
            final int maxLimit = Math.min(progress + client.decodableRecordSetBytes, limit);
            final RecordBatchFW recordBatch = recordBatchRO.tryWrap(buffer, progress, maxLimit);

            if (recordBatch == null && length >= client.decodableRecordSetBytes)
            {
                client.decoder = decodeIgnoreRecordSet;
                break decode;
            }

            if (recordBatch != null)
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[client] [0x%016x] %s[%d] FETCH RecordBatch %d %d %d\n",
                            client.replyId, client.topic, client.partitionId, recordBatch.baseOffset(),
                        recordBatch.lastOffsetDelta(), recordBatch.length());
                }

                final int attributes = recordBatch.attributes();
                final int recordSetProgress = recordBatch.sizeof();
                final int recordBatchProgress = recordSetProgress - FIELD_LIMIT_RECORD_BATCH_LENGTH;

                progress += recordSetProgress;

                final long baseOffset = recordBatch.baseOffset();
                final long producerId = recordBatch.producerId();

                client.decodableRecordBatchBytes = recordBatch.length();
                client.decodeRecordBatchOffset = baseOffset;
                client.decodeRecordBatchLastOffset = recordBatch.baseOffset() + recordBatch.lastOffsetDelta();
                client.decodeRecordBatchTimestamp = recordBatch.firstTimestamp();
                client.decodeRecordBatchProducerId = producerId;
                client.decodeRecordBatchAborted = client.decodeAbortedTransactions.get(baseOffset) == producerId;
                client.decodeRecordBatchAttributes = attributes;
                client.decodableRecords = recordBatch.recordCount();

                client.decodableResponseBytes -= recordSetProgress;
                assert client.decodableResponseBytes >= 0;

                client.decodableRecordSetBytes -= recordSetProgress;
                assert client.decodableRecordSetBytes >= 0;

                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[client] [0x%016x] %s[%d] FETCH Record Set Bytes %d\n",
                        client.replyId, client.topic, client.partitionId, client.decodableRecordSetBytes);
                }

                client.decodableRecordBatchBytes -= recordBatchProgress;
                assert client.decodableRecordBatchBytes >= 0;

                if (isCompressedBatch(attributes) || isControlBatch(attributes) && !isTransactionalBatch(attributes))
                {
                    client.decoder = decodeIgnoreRecordBatch;
                    break decode;
                }

                client.decoder = decodeFetchRecordLength;
            }
        }

        return progress;
    }

    private int decodeFetchRecordLength(
        KafkaFetchStream.KafkaFetchClient client,
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
        if (client.decodableRecords == 0)
        {
            client.nextOffset = Math.max(client.nextOffset, client.decodeRecordBatchLastOffset + 1);
            client.decoder = decodeFetchRecordBatch;
            break decode;
        }
        else if (length != 0)
        {
            final Varint32FW recordLength = recordLengthRO.tryWrap(buffer, progress, limit);

            // TODO: verify incremental decode vs truncated record set mid record length
            if (recordLength == null && length >= client.decodableRecordBatchBytes)
            {
                client.decoder = decodeIgnoreRecordBatch;
                break decode;
            }

            if (recordLength != null)
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[client] [0x%016x] %s[%d] FETCH Record length %d\n",
                            client.replyId, client.topic, client.partitionId, recordLength.value());
                }

                final int sizeofRecord = recordLength.sizeof() + recordLength.value();

                // TODO: verify incremental decode vs truncated record set post record length
                if (sizeofRecord > client.decodableRecordSetBytes)
                {
                    client.decoder = decodeIgnoreRecordSet;
                    break decode;
                }

                assert sizeofRecord <= client.decodableRecordBatchBytes : "truncated record batch not last in record set";

                if (length >= sizeofRecord)
                {
                    client.decoder = decodeFetchRecord;
                }
                else if (sizeofRecord > decodePool.slotCapacity())
                {
                    client.decoder = decodeFetchRecordInit;
                }
            }
        }

        return progress;
    }

    private int decodeFetchRecord(
        KafkaFetchStream.KafkaFetchClient client,
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
            final RecordHeaderFW recordHeader = recordHeaderRO.tryWrap(buffer, progress, limit);
            if (recordHeader != null)
            {
                final Varint32FW recordLength = recordLengthRO.tryWrap(buffer, progress, limit);
                assert recordHeader.length() == recordLength.value();

                final int sizeofRecord = recordLength.sizeof() + recordLength.value();
                final int recordLimit = recordHeader.offset() + sizeofRecord;

                final long offsetAbs = client.decodeRecordBatchOffset + recordHeader.offsetDelta();
                final long timestampAbs = client.decodeRecordBatchTimestamp + recordHeader.timestampDelta();
                final long producerId = client.decodeRecordBatchProducerId;
                final boolean aborted = client.decodeRecordBatchAborted;
                final OctetsFW key = recordHeader.key();

                client.decodeRecordOffset = offsetAbs;

                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[client] [0x%016x] %s[%d] FETCH Record %d\n",
                        client.replyId, client.topic, client.partitionId, client.decodeRecordOffset);
                }

                if (offsetAbs < client.nextOffset)
                {
                    client.decodableRecordBytes = sizeofRecord;
                    client.decoder = decodeIgnoreRecord;
                    break decode;
                }

                if (isControlBatch(client.decodeRecordBatchAttributes) &&
                    isTransactionalBatch(client.decodeRecordBatchAttributes))
                {
                    final ControlRecordKeyFW controlKey = key.get(controlRecordKeyRO::tryWrap);
                    if (controlKey != null && controlKey.version() == 0)
                    {
                        switch (ControlRecordKeyType.valueOf(controlKey.type()))
                        {
                        case ABORT:
                            client.onDecodeFetchTransactionAbort(traceId, authorization, offsetAbs, producerId);
                            break;
                        case COMMIT:
                            client.onDecodeFetchTransactionCommit(traceId, authorization, offsetAbs, producerId);
                            break;
                        }
                    }
                    client.decodableRecordBytes = sizeofRecord;
                    client.decoder = decodeIgnoreRecord;
                    break decode;
                }

                final int valueLength = recordHeader.valueLength();
                final int valueOffset = recordHeader.limit();
                final int valueSize = Math.max(valueLength, 0);
                final int valueReserved = valueSize + client.stream.replyPad;

                if (sizeofRecord <= length && valueReserved <= client.stream.replyBudget())
                {
                    final int maximum = valueReserved;
                    final int minimum = Math.min(maximum, 1024);

                    int valueClaimed = maximum;
                    if (valueClaimed != 0 && client.stream.replyDebitorIndex != NO_DEBITOR_INDEX)
                    {
                        valueClaimed = client.stream.replyDebitor.claim(traceId, client.stream.replyDebitorIndex,
                                client.stream.replyId, minimum, maximum, 0);

                        if (valueClaimed == 0)
                        {
                            break decode;
                        }
                    }

                    if (valueClaimed < valueReserved)
                    {
                        final int valueLengthMax = valueClaimed - client.stream.replyBudget();
                        final int limitMax = valueOffset + valueLengthMax;

                        // TODO: shared budget for fragmented decode too
                        progress = decodeFetchRecordInit(client, traceId, authorization, budgetId, valueReserved,
                                buffer, offset, progress, limitMax);
                    }
                    else
                    {
                        final OctetsFW value =
                                valueLength != -1 ? valueRO.wrap(buffer, valueOffset, valueOffset + valueLength) : null;

                        final int trailerOffset = valueOffset + Math.max(valueLength, 0);
                        final RecordTrailerFW recordTrailer = recordTrailerRO.wrap(buffer, trailerOffset, recordLimit);
                        final int headerCount = recordTrailer.headerCount();
                        final int headersOffset = recordTrailer.limit();
                        final int headersLength = recordLimit - headersOffset;
                        final DirectBuffer headers = wrapHeaders(buffer, headersOffset, headersLength);

                        progress += sizeofRecord;

                        client.onDecodeFetchRecord(traceId, aborted, valueReserved, offsetAbs, timestampAbs, producerId,
                                key, value, headerCount, headers);

                        client.decodableResponseBytes -= sizeofRecord;
                        assert client.decodableResponseBytes >= 0;

                        client.decodableRecordSetBytes -= sizeofRecord;
                        assert client.decodableRecordSetBytes >= 0;

                        if (KafkaConfiguration.DEBUG)
                        {
                            System.out.format("[client] [0x%016x] %s[%d] FETCH Record Set Bytes %d\n",
                                client.replyId, client.topic, client.partitionId, client.decodableRecordSetBytes);
                        }

                        client.decodableRecordBatchBytes -= sizeofRecord;
                        assert client.decodableRecordBatchBytes >= 0;

                        client.decodableRecords--;
                        assert client.decodableRecords >= 0;

                        client.decoder = decodeFetchRecordLength;
                    }
                }
            }
        }

        if (client.decoder == decodeIgnoreAll)
        {
            client.cleanupNetwork(traceId);
        }

        return progress;
    }

    private int decodeFetchRecordInit(
        KafkaFetchStream.KafkaFetchClient client,
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
        if (length >= decodeMaxBytes)
        {
            final RecordHeaderFW recordHeader = recordHeaderRO.tryWrap(buffer, progress, limit);
            if (recordHeader != null)
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[client] [0x%016x] %s[%d] FETCH Record (init) %d\n",
                            client.replyId, client.topic, client.partitionId, client.nextOffset);
                }

                final Varint32FW recordLength = recordLengthRO.tryWrap(buffer, progress, limit);
                assert recordHeader.length() == recordLength.value();

                final int sizeofRecord = recordLength.sizeof() + recordLength.value();
                final long offsetAbs = client.decodeRecordBatchOffset + recordHeader.offsetDelta();
                final long timestampAbs = client.decodeRecordBatchTimestamp + recordHeader.timestampDelta();
                final long producerId = client.decodeRecordBatchProducerId;
                final boolean aborted = client.decodeRecordBatchAborted;
                final OctetsFW key = recordHeader.key();

                if (offsetAbs < client.nextOffset)
                {
                    client.decodableRecordBytes = sizeofRecord;
                    client.decoder = decodeIgnoreRecord;
                    break decode;
                }

                final int valueLength = recordHeader.valueLength();
                final int valueOffset = recordHeader.limit();

                final int valueSize = Math.max(valueLength, 0);
                final int valueReserved = valueSize + client.stream.replyPad;
                final int valueReservedMax = Math.min(valueReserved, client.stream.replyBudget());

                final int maximum = valueReservedMax;
                final int minimum = Math.min(maximum, 1024);

                int valueClaimed = maximum;
                if (valueClaimed != 0 && client.stream.replyDebitorIndex != NO_DEBITOR_INDEX)
                {
                    valueClaimed = client.stream.replyDebitor.claim(traceId, client.stream.replyDebitorIndex,
                            client.stream.replyId, minimum, maximum, 0);

                    if (valueClaimed == 0)
                    {
                        break decode;
                    }
                }

                final int valueLengthMax = valueClaimed - client.stream.replyPad;
                final int valueLimitMax = Math.min(limit, valueOffset + valueLengthMax);
                final OctetsFW valueInit = valueLength != -1 ? valueRO.wrap(buffer, valueOffset, valueLimitMax) : null;
                final int valueProgress = valueInit != null ? valueInit.sizeof() : 0;
                final int recordProgress = recordHeader.sizeof() + valueProgress;

                final int valueDeferred = valueLength - valueProgress;
                final int headersLength = recordHeader.length() - (recordHeader.limit() - recordLength.limit()) - valueLength;
                final int headersSizeMax = headersLength + 3;

                progress += recordProgress;

                client.onDecodeFetchRecordValueInit(traceId, aborted, valueReservedMax, valueDeferred, offsetAbs,
                        timestampAbs, headersSizeMax, producerId, key, valueInit);

                client.decodeRecordOffset = offsetAbs;
                client.decodableRecordValueBytes = valueLength != -1 ? valueLength - valueProgress : -1;
                client.decodableRecordBytes = sizeofRecord;

                client.decodableResponseBytes -= recordProgress;
                assert client.decodableResponseBytes >= 0;

                client.decodableRecordSetBytes -= recordProgress;
                assert client.decodableRecordSetBytes >= 0;

                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[client] [0x%016x] %s[%d] FETCH Record Set Bytes %d\n",
                        client.replyId, client.topic, client.partitionId, client.decodableRecordSetBytes);
                }

                client.decodableRecordBatchBytes -= recordProgress;
                assert client.decodableRecordBatchBytes >= 0;

                client.decodableRecordBytes -= recordProgress;
                assert client.decodableRecordBytes >= 0;

                client.decoder = decodeFetchRecordValue;
            }
        }

        return progress;
    }

    private int decodeFetchRecordValue(
        KafkaFetchStream.KafkaFetchClient client,
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
        if (length >= Math.min(decodeMaxBytes, client.decodableRecordBytes))
        {
            final int valueReserved = Math.min(length, client.decodableRecordValueBytes) + client.stream.replyPad;
            final int valueReservedMax = Math.min(valueReserved, client.stream.replyBudget());

            final int maximum = valueReservedMax;
            final int minimum = Math.min(maximum, 1024);

            int valueClaimed = maximum;
            if (valueClaimed != 0 && client.stream.replyDebitorIndex != NO_DEBITOR_INDEX)
            {
                valueClaimed = client.stream.replyDebitor.claim(traceId, client.stream.replyDebitorIndex,
                        client.stream.replyId, minimum, maximum, 0);

                if (valueClaimed == 0)
                {
                    break decode;
                }
            }

            final int valueLengthMax = valueClaimed - client.stream.replyPad;

            if (length >= client.decodableRecordBytes && valueLengthMax >= client.decodableRecordValueBytes)
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[client] [0x%016x] %s[%d] FETCH Record (fin) %d\n",
                            client.replyId, client.topic, client.partitionId, client.nextOffset);
                }

                final int valueOffset = progress;
                final int valueProgress = valueLengthMax;
                final int valueSize = Math.max(valueProgress, 0);
                final int valueLimit = valueOffset + valueSize;
                final OctetsFW valueFin = valueProgress != -1 ? valueRO.wrap(buffer, valueOffset, valueLimit) : null;

                final int recordProgress = client.decodableRecordBytes;
                final int recordLimit = progress + recordProgress;
                final RecordTrailerFW recordTrailer = recordTrailerRO.wrap(buffer, valueLimit, recordLimit);
                final int headerCount = recordTrailer.headerCount();
                final int headersOffset = recordTrailer.limit();
                final int headersLength = recordLimit - headersOffset;
                final DirectBuffer headers = headersRO;
                headers.wrap(buffer, headersOffset, headersLength);

                progress += recordProgress;

                client.onDecodeFetchRecordValueFin(traceId, valueReservedMax, client.decodeRecordOffset, valueFin,
                        headerCount, headers);

                client.decodableResponseBytes -= recordProgress;
                assert client.decodableResponseBytes >= 0;

                client.decodableRecordSetBytes -= recordProgress;
                assert client.decodableRecordSetBytes >= 0;

                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[client] [0x%016x] %s[%d] FETCH Record Set Bytes %d\n",
                        client.replyId, client.topic, client.partitionId, client.decodableRecordSetBytes);
                }

                client.decodableRecordBatchBytes -= recordProgress;
                assert client.decodableRecordBatchBytes >= 0;

                client.decodableRecordBytes -= recordProgress;
                assert client.decodableRecordBytes == 0;

                client.decodableRecordValueBytes -= valueProgress;
                assert client.decodableRecordValueBytes == 0;

                client.decodableRecords--;
                assert client.decodableRecords >= 0;

                client.decoder = decodeFetchRecordLength;
            }
            else
            {
                assert client.decodableRecordValueBytes != -1 : "record headers overflow decode buffer";

                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[client] [0x%016x] %s[%d] FETCH Record (cont) %d\n",
                            client.replyId, client.topic, client.partitionId, client.nextOffset);
                }

                final int valueOffset = progress;
                final int valueProgress = Math.min(valueLengthMax, decodeMaxBytes);
                final int valueLimit = valueOffset + valueProgress;
                final OctetsFW value = valueRO.wrap(buffer, valueOffset, valueLimit);

                progress += valueProgress;

                client.onDecodeFetchRecordValueCont(traceId, valueReservedMax, value);

                client.decodableRecordBytes -= valueProgress;
                assert client.decodableRecordBytes >= 0;

                client.decodableRecordSetBytes -= valueProgress;
                assert client.decodableRecordSetBytes >= 0;

                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[client] [0x%016x] %s[%d] FETCH Record Set Bytes %d\n",
                        client.replyId, client.topic, client.partitionId, client.decodableRecordSetBytes);
                }

                client.decodableRecordValueBytes -= valueProgress;
                assert client.decodableRecordValueBytes >= 0;
            }
        }

        return progress;
    }

    private int decodeIgnoreRecord(
        KafkaFetchStream.KafkaFetchClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int maxLength = limit - progress;
        final int length = Math.min(maxLength, client.decodableRecordBytes);

        progress += length;

        client.decodableResponseBytes -= length;
        assert client.decodableResponseBytes >= 0;

        client.decodableRecordSetBytes -= length;
        assert client.decodableRecordSetBytes >= 0;

        if (KafkaConfiguration.DEBUG)
        {
            System.out.format("[client] [0x%016x] %s[%d] FETCH Record Set Bytes %d\n",
                client.replyId, client.topic, client.partitionId, client.decodableRecordSetBytes);
        }

        client.decodableRecordBatchBytes -= length;
        assert client.decodableRecordBatchBytes >= 0;

        client.decodableRecordBytes -= length;
        assert client.decodableRecordBytes >= 0;

        if (client.decodableRecordBytes == 0)
        {
            client.decodableRecords--;
            assert client.decodableRecords >= 0;
            client.decoder = decodeFetchRecordLength;
        }

        return progress;
    }

    private int decodeIgnoreRecordBatch(
        KafkaFetchStream.KafkaFetchClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int maxLength = limit - progress;
        final int length = Math.min(maxLength, client.decodableRecordBatchBytes);

        progress += length;

        client.decodableResponseBytes -= length;
        assert client.decodableResponseBytes >= 0;

        client.decodableRecordSetBytes -= length;
        assert client.decodableRecordSetBytes >= 0;

        if (KafkaConfiguration.DEBUG)
        {
            System.out.format("[client] [0x%016x] %s[%d] FETCH Record Set Bytes %d\n",
                client.replyId, client.topic, client.partitionId, client.decodableRecordSetBytes);
        }

        client.decodableRecordBatchBytes -= length;
        assert client.decodableRecordBatchBytes >= 0;

        if (client.decodableRecordBatchBytes == 0)
        {
            client.decoder = decodeFetchRecordBatch;
        }

        return progress;
    }

    private int decodeIgnoreRecordSet(
        KafkaFetchStream.KafkaFetchClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int maxLength = limit - progress;
        final int length = Math.min(maxLength, client.decodableRecordSetBytes);

        progress += length;

        client.decodableResponseBytes -= length;
        assert client.decodableResponseBytes >= 0;

        client.decodableRecordSetBytes -= length;
        assert client.decodableRecordSetBytes >= 0;

        if (KafkaConfiguration.DEBUG)
        {
            System.out.format("[client] [0x%016x] %s[%d] FETCH Record Set Bytes %d\n",
                client.replyId, client.topic, client.partitionId, client.decodableRecordSetBytes);
        }

        if (client.decodableRecordSetBytes == 0)
        {
            client.decodablePartitions--;
            assert client.decodablePartitions >= 0;
            client.decoder = decodeFetchPartition;
        }

        return progress;
    }

    private int decodeIgnoreAll(
        KafkaFetchStream.KafkaFetchClient client,
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

    private int decodeReject(
        KafkaFetchStream.KafkaFetchClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        client.doNetworkResetIfNecessary(traceId);
        client.decoder = decodeIgnoreAll;
        return limit;
    }

    private final class KafkaFetchStream
    {
        private final MessageConsumer application;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long leaderId;
        private final KafkaClientRoute clientRoute;
        private final KafkaFetchClient client;

        private int state;
        private int flushOrDataFramesSent = 0;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private long replyDebitorId;
        private BudgetDebitor replyDebitor;
        private long replyDebitorIndex = NO_DEBITOR_INDEX;

        KafkaFetchStream(
            MessageConsumer application,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            String topic,
            int partitionId,
            long latestOffset,
            long leaderId,
            long initialOffset,
            KafkaIsolation isolation,
            KafkaSaslConfig sasl)
        {
            this.application = application;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.leaderId = leaderId;
            this.clientRoute = supplyClientRoute.apply(resolvedId);
            this.client = new KafkaFetchClient(routedId, resolvedId, topic, partitionId,
                    initialOffset, latestOffset, isolation, sasl);
        }

        private int replyBudget()
        {
            return replyMax - (int)(replySeq - replyAck);
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

            if (client.topicPartitions.get(client.partitionId) != leaderId)
            {
                client.network = MessageConsumer.NOOP;
                cleanupApplication(traceId, ERROR_NOT_LEADER_FOR_PARTITION);
            }
            else
            {
                client.doNetworkBegin(traceId, authorization, leaderId);
            }
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

            client.doNetworkEndAfterFlush(traceId, authorization);
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
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            this.replyAck = acknowledge;
            this.replyMax = maximum;
            this.replyPad = padding;
            this.replyDebitorId = budgetId;

            assert replyAck <= replySeq;

            if (replyDebitorId != 0L && replyDebitor == null)
            {
                replyDebitor = supplyDebitor.apply(replyDebitorId);
                replyDebitorIndex = replyDebitor.acquire(replyDebitorId, replyId, client::decodeNetworkIfNecessary);
            }

            state = KafkaState.openedReply(state);

            client.decodeNetworkIfNecessary(traceId);
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
            long authorization,
            String topic,
            int partitionId,
            long partitionOffset,
            long stableOffset,
            long latestOffset,
            KafkaIsolation isolation)
        {
            if (!KafkaState.replyOpening(state))
            {
                doApplicationBegin(traceId, authorization, topic,
                        partitionId, partitionOffset, stableOffset, latestOffset, isolation);
            }
        }

        private void doApplicationBegin(
            long traceId,
            long authorization,
            String topic,
            int partitionId,
            long partitionOffset,
            long stableOffset,
            long latestOffset,
            KafkaIsolation isolation)
        {
            state = KafkaState.openingReply(state);

            doBegin(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, leaderId,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                                                        .typeId(kafkaTypeId)
                                                        .fetch(m -> m.topic(topic)
                                                                     .partition(p -> p.partitionId(partitionId)
                                                                                      .partitionOffset(partitionOffset)
                                                                                      .stableOffset(stableOffset)
                                                                                      .latestOffset(latestOffset))
                                                                     .isolation(i -> i.set(isolation)))
                                                        .build()
                                                        .sizeof()));
            client.initialLatestOffset = latestOffset;
            client.initialStableOffset = stableOffset;
        }

        private void doApplicationData(
            long traceId,
            long authorization,
            int flags,
            int reserved,
            OctetsFW payload,
            Flyweight extension)
        {
            flushOrDataFramesSent++;
            doData(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, flags, replyDebitorId, reserved, payload, extension);

            replySeq += reserved;

            assert replyAck <= replySeq;
        }

        private void doApplicationFlush(
            long traceId,
            long authorization,
            int reserved,
            Flyweight extension)
        {
            flushOrDataFramesSent++;
            doFlush(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, reserved, extension);

            replySeq += reserved;

            assert replyAck <= replySeq;
        }

        private void doApplicationFlushIfNecessary(
            long traceId,
            long authorization,
            int reserved)
        {
            if (client.decodeRecordBatchLastOffset >= client.initialLatestOffset &&
                    client.decodableRecords == 0 &&
                    flushOrDataFramesSent == 0)
            {
                final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .fetch(f -> f
                                .partition(p -> p
                                        .partitionId(client.partitionId)
                                        .partitionOffset(client.decodeRecordBatchLastOffset)
                                        .stableOffset(client.initialStableOffset)
                                        .latestOffset(client.initialLatestOffset)))
                        .build();

                doFlush(application, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, reserved, kafkaFlushEx);
            }

            replySeq += reserved;

            assert replyAck <= replySeq;
        }

        private void doApplicationEnd(
            long traceId)
        {
            cleanupApplicationDebitorIfNecessary();

            state = KafkaState.closedReply(state);
            //client.stream = nullIfClosed(state, client.stream);
            doEnd(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, client.authorization, EMPTY_OCTETS);
        }

        private void doApplicationAbort(
            long traceId)
        {
            cleanupApplicationDebitorIfNecessary();

            state = KafkaState.closedReply(state);
            //client.stream = nullIfClosed(state, client.stream);
            doAbort(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, client.authorization, EMPTY_OCTETS);
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
            //client.stream = nullIfClosed(state, client.stream);

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
            if (!KafkaState.initialClosed(state))
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

        private void cleanupApplicationDebitorIfNecessary()
        {
            if (replyDebitorIndex != NO_DEBITOR_INDEX)
            {
                replyDebitor.release(replyDebitorIndex, replyId);
                replyDebitorIndex = NO_DEBITOR_INDEX;
                replyDebitor = null;
            }
        }

        private final class KafkaFetchClient extends KafkaSaslClient
        {
            private final LongLongConsumer encodeSaslHandshakeRequest = this::doEncodeSaslHandshakeRequest;
            private final LongLongConsumer encodeSaslAuthenticateRequest = this::doEncodeSaslAuthenticateRequest;
            private final LongLongConsumer encodeOffsetsRequest = this::doEncodeOffsetsRequest;
            private final LongLongConsumer encodeFetchRequest = this::doEncodeFetchRequest;

            private MessageConsumer network;
            private final KafkaFetchStream stream;
            private final String topic;
            private final Int2IntHashMap topicPartitions;
            private final int partitionId;
            private final KafkaIsolation isolation;

            private long nextOffset;
            private long stableOffset;
            private long latestOffset;
            private long initialLatestOffset;
            private long initialStableOffset;

            private int state;
            private long authorization;

            private long initialSeq;
            private long initialAck;
            private int initialMax;
            private int initialPad;
            private long initialBudgetId;

            private long replySeq;
            private long replyAck;
            private int replyMax;

            private int encodeSlot = NO_SLOT;
            private int encodeSlotOffset;
            private long encodeSlotTraceId;

            private int decodeSlot = NO_SLOT;
            private int decodeSlotOffset;
            private int decodeSlotReserved;

            private int decodableResponseBytes;
            private int decodableTopics;
            private int decodableTransactions;
            private int decodablePartitions;
            private int decodePartitionError;
            private int decodePartitionId;
            private Long2LongHashMap decodeAbortedTransactions;
            private int decodableRecordSetBytes;
            private int decodableRecordBatchBytes;
            private long decodeRecordBatchOffset;
            private long decodeRecordBatchLastOffset;
            private long decodeRecordBatchTimestamp;
            private long decodeRecordBatchProducerId;
            private boolean decodeRecordBatchAborted;
            private int decodeRecordBatchAttributes;
            private int decodableRecords;
            private long decodeRecordOffset;
            private int decodableRecordBytes;
            private int decodableRecordValueBytes;

            private int nextResponseId;

            private KafkaFetchClientDecoder decoder;
            private LongLongConsumer encoder;

            KafkaFetchClient(
                long originId,
                long routedId,
                String topic,
                int partitionId,
                long initialOffset,
                long latestOffset,
                KafkaIsolation isolation,
                KafkaSaslConfig sasl)
            {
                super(sasl, originId, routedId);
                this.stream = KafkaFetchStream.this;
                this.topic = requireNonNull(topic);
                this.topicPartitions = clientRoute.supplyPartitions(topic);
                this.partitionId = partitionId;
                this.nextOffset = initialOffset;
                this.latestOffset = latestOffset;
                this.isolation = isolation;
                this.encoder = encodeFetchRequest;
                this.decoder = decodeReject;
                this.decodeAbortedTransactions = new Long2LongHashMap(Long.MIN_VALUE);
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

            private long networkBytesReceived;

            private void onNetworkData(
                DataFW data)
            {
                final long sequence = data.sequence();
                final long acknowledge = data.acknowledge();
                final long traceId = data.traceId();
                final long budgetId = data.budgetId();

                networkBytesReceived += Math.max(data.length(), 0);

                authorization = data.authorization();

                assert acknowledge <= sequence;
                assert sequence >= replySeq;

                replySeq = sequence + data.reserved();

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

                state = KafkaState.closingReply(state);

                if (!isApplicationReplyOpen())
                {
                    cleanupNetwork(traceId);
                }
                else if (decodeSlot == NO_SLOT)
                {
                    doApplicationEnd(traceId);
                }
            }

            private void onNetworkAbort(
                AbortFW abort)
            {
                final long traceId = abort.traceId();

                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[client] [0x%016x] %s[%s] FETCH aborted (%d bytes)\n",
                        replyId, topic, partitionId, networkBytesReceived);
                }

                state = KafkaState.closedReply(state);

                cleanupNetwork(traceId);
            }

            private void onNetworkReset(
                ResetFW reset)
            {
                final long traceId = reset.traceId();

                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[client] [0x%016x] %s[%d] FETCH reset (%d bytes)\n",
                        replyId, topic, partitionId, networkBytesReceived);
                }

                state = KafkaState.closedInitial(state);

                cleanupNetwork(traceId);
            }

            private void onNetworkWindow(
                WindowFW window)
            {
                final long sequence = window.sequence();
                final long acknowledge = window.acknowledge();
                final int maximum = window.maximum();
                final long traceId = window.traceId();
                final long budgetId = window.budgetId();
                final int padding = window.padding();

                authorization = window.authorization();

                assert acknowledge <= sequence;
                assert sequence <= initialSeq;
                assert acknowledge >= initialAck;
                assert maximum + acknowledge >= initialMax + initialAck;

                this.initialAck = acknowledge;
                this.initialMax = maximum;
                this.initialPad = padding;
                this.initialBudgetId = budgetId;

                assert initialAck <= initialSeq;

                state = KafkaState.openedInitial(state);

                if (encodeSlot != NO_SLOT)
                {
                    final MutableDirectBuffer buffer = encodePool.buffer(encodeSlot);
                    final int limit = encodeSlotOffset;

                    encodeNetwork(encodeSlotTraceId, authorization, budgetId, buffer, 0, limit);
                }

                doEncodeRequestIfNecessary(traceId, budgetId);
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

                if (client.sasl != null)
                {
                    client.encoder = client.encodeSaslHandshakeRequest;
                    client.decoder = decodeSaslHandshakeResponse;
                }
                else if (nextOffset == OFFSET_LIVE || nextOffset == OFFSET_HISTORICAL)
                {
                    client.encoder = client.encodeOffsetsRequest;
                    client.decoder = decodeOffsetsResponse;
                }

                Consumer<OctetsFW.Builder> extension = EMPTY_EXTENSION;

                final KafkaClientRoute clientRoute = supplyClientRoute.apply(routedId);
                final KafkaBrokerInfo broker = clientRoute.brokers.get(affinity);
                if (broker != null)
                {
                    extension = e -> e.set((b, o, l) -> proxyBeginExRW.wrap(b, o, l)
                                                                      .typeId(proxyTypeId)
                                                                      .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                                                                                 .source("0.0.0.0")
                                                                                                 .destination(broker.host)
                                                                                                 .sourcePort(0)
                                                                                                 .destinationPort(broker.port)))
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

            private void doNetworkEndAfterFlush(
                long traceId,
                long authorization)
            {
                state = KafkaState.closingInitial(state);

                if (encodeSlot == NO_SLOT)
                {
                    doNetworkEnd(traceId, authorization);
                }
            }

            private void doNetworkEnd(
                long traceId,
                long authorization)
            {
                state = KafkaState.closedInitial(state);
                doEnd(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, EMPTY_OCTETS);

                cleanupEncodeSlotIfNecessary();
            }

            private void doNetworkAbortIfNecessary(
                long traceId)
            {
                if (!KafkaState.initialClosed(state))
                {
                    doAbort(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                            traceId, authorization, EMPTY_OCTETS);
                    state = KafkaState.closedInitial(state);
                }

                cleanupEncodeSlotIfNecessary();
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

                    doWindow(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                            traceId, authorization, budgetId, minReplyPad);

                    state = KafkaState.openedReply(state);
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

            private void doEncodeOffsetsRequest(
                long traceId,
                long budgetId)
            {
                final MutableDirectBuffer encodeBuffer = writeBuffer;
                final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
                final int encodeLimit = encodeBuffer.capacity();

                int encodeProgress = encodeOffset;

                final RequestHeaderFW requestHeader = requestHeaderRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .length(0)
                        .apiKey(OFFSETS_API_KEY)
                        .apiVersion(OFFSETS_API_VERSION)
                        .correlationId(0)
                        .clientId((String) null)
                        .build();

                encodeProgress = requestHeader.limit();

                final OffsetsRequestFW offsetsRequest = offsetsRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .isolationLevel(i -> i.set(READ_UNCOMMITTED))
                        .topicCount(1)
                        .build();

                encodeProgress = offsetsRequest.limit();

                final OffsetsTopicRequestFW topicRequest = offsetsTopicRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .name(topic)
                        .partitionCount(1)
                        .build();

                encodeProgress = topicRequest.limit();

                final long timestamp = nextOffset;

                assert timestamp < 0;

                final OffsetsPartitionRequestFW partitionRequest = offsetsPartitionRequestRW
                        .wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .partitionId(partitionId)
                        .timestamp(timestamp)
                        .build();

                encodeProgress = partitionRequest.limit();

                final int requestId = nextRequestId++;
                final int requestSize = encodeProgress - encodeOffset - RequestHeaderFW.FIELD_OFFSET_API_KEY;

                requestHeaderRW.wrap(encodeBuffer, requestHeader.offset(), requestHeader.limit())
                        .length(requestSize)
                        .apiKey(requestHeader.apiKey())
                        .apiVersion(requestHeader.apiVersion())
                        .correlationId(requestId)
                        .clientId(requestHeader.clientId().asString())
                        .build();

                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[0x%016x] %s[%d] OFFSETS %d\n", replyId, topic, partitionId, timestamp);
                }

                doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

                this.decoder = decodeOffsetsResponse;
            }

            private void doEncodeFetchRequest(
                long traceId,
                long budgetId)
            {
                final MutableDirectBuffer encodeBuffer = writeBuffer;
                final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
                final int encodeLimit = encodeBuffer.capacity();

                int encodeProgress = encodeOffset;

                final RequestHeaderFW requestHeader = requestHeaderRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .length(0)
                        .apiKey(FETCH_API_KEY)
                        .apiVersion(FETCH_API_VERSION)
                        .correlationId(0)
                        .clientId((String) null)
                        .build();

                encodeProgress = requestHeader.limit();

                final FetchRequestFW fetchRequest = fetchRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .maxWaitTimeMillis(!KafkaState.replyOpened(stream.state) ? 0 : fetchMaxWaitMillis)
                        .minBytes(1)
                        .maxBytes(fetchMaxBytes)
                        .isolationLevel((byte) isolation.ordinal())
                        .topicCount(1)
                        .build();

                encodeProgress = fetchRequest.limit();

                final TopicRequestFW topicRequest = fetchTopicRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .name(topic)
                        .partitionCount(1)
                        .build();

                encodeProgress = topicRequest.limit();

                final PartitionRequestFW partitionRequest = fetchPartitionRequestRW
                        .wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .partitionId((int) partitionId)
                        .fetchOffset(nextOffset)
                        .maxBytes(partitionMaxBytes)
                        .build();

                encodeProgress = partitionRequest.limit();

                final int requestId = nextRequestId++;
                final int requestSize = encodeProgress - encodeOffset - RequestHeaderFW.FIELD_OFFSET_API_KEY;

                requestHeaderRW.wrap(encodeBuffer, requestHeader.offset(), requestHeader.limit())
                        .length(requestSize)
                        .apiKey(requestHeader.apiKey())
                        .apiVersion(requestHeader.apiVersion())
                        .correlationId(requestId)
                        .clientId(requestHeader.clientId().asString())
                        .build();

                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[0x%016x] %s[%d] FETCH %d\n", replyId, topic, partitionId, nextOffset);
                }

                doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

                this.decoder = decodeFetchResponse;
            }

            private void encodeNetwork(
                long traceId,
                long authorization,
                long budgetId,
                DirectBuffer buffer,
                int offset,
                int limit)
            {
                final int maxLength = limit - offset;
                final int initialWin = initialMax - (int)(initialSeq - initialAck);
                final int length = Math.max(Math.min(initialWin - initialPad, maxLength), 0);

                if (length > 0)
                {
                    final int reserved = length + initialPad;

                    doData(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                            traceId, authorization, budgetId, reserved, buffer, offset, length, EMPTY_OCTETS);

                    initialSeq += reserved;

                    assert initialAck <= initialSeq;
                }

                final int remaining = maxLength - length;
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
                        encodeBuffer.putBytes(0, buffer, offset + length, remaining);
                        encodeSlotOffset = remaining;
                    }
                }
                else
                {
                    cleanupEncodeSlotIfNecessary();

                    if (KafkaState.initialClosing(state))
                    {
                        doNetworkEnd(traceId, authorization);
                    }
                }
            }

            private void decodeNetworkIfNecessary(
                long traceId)
            {
                if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBuffer buffer = decodePool.buffer(decodeSlot);
                    final long budgetId = 0L; // TODO
                    final int offset = 0;
                    final int limit = decodeSlotOffset;
                    final int reserved = decodeSlotReserved;

                    decodeNetwork(traceId, authorization, budgetId, reserved, buffer, offset, limit);
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
                KafkaFetchClientDecoder previous = null;
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
                        decodeSlotReserved = (int) ((long) (limit - progress) * reserved / (limit - offset));
                        assert decodeSlotReserved >= 0;
                    }

                    doNetworkWindow(traceId, budgetId, decodeSlotOffset, 0, replyMax);
                }
                else
                {
                    cleanupDecodeSlotIfNecessary();

                    if (KafkaState.replyClosing(state))
                    {
                        doApplicationEnd(traceId);
                    }
                    else if (reserved > 0)
                    {
                        doNetworkWindow(traceId, budgetId, 0, 0, replyMax);
                    }
                }
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
                    cleanupApplication(traceId, errorCode);
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
                    if (nextOffset == OFFSET_LIVE || nextOffset == OFFSET_HISTORICAL)
                    {
                        client.encoder = client.encodeOffsetsRequest;
                        client.decoder = decodeOffsetsResponse;
                    }
                    else
                    {
                        client.encoder = client.encodeFetchRequest;
                        client.decoder = decodeFetchResponse;
                    }
                    break;
                default:
                    cleanupApplication(traceId, errorCode);
                    doNetworkEnd(traceId, authorization);
                    break;
                }
            }

            private void onDecodeOffsetsPartition(
                long traceId,
                long authorization,
                int errorCode,
                int partitionId,
                long partitionOffset)
            {
                switch (errorCode)
                {
                case ERROR_NONE:
                    assert partitionId == this.partitionId;
                    this.nextOffset = partitionOffset;
                    break;
                default:
                    cleanupApplication(traceId, errorCode);
                    doNetworkEnd(traceId, authorization);
                    break;
                }
            }

            private void onDecodeFetchPartition(
                long traceId,
                long authorization,
                int partitionId,
                int errorCode)
            {
                switch (errorCode)
                {
                case ERROR_NONE:
                    assert partitionId == this.partitionId;
                    doApplicationWindow(traceId, 0L, 0, 0, 0);
                    doApplicationBeginIfNecessary(traceId, authorization, topic, partitionId,
                            nextOffset, stableOffset, latestOffset, isolation);
                    break;
                case ERROR_OFFSET_OUT_OF_RANGE:
                    assert partitionId == this.partitionId;
                    // TODO: recover at EARLIEST or LATEST ?
                    nextOffset = OFFSET_HISTORICAL;
                    client.encoder = client.encodeOffsetsRequest;
                    doEncodeRequestIfNecessary(traceId, initialBudgetId);
                    break;
                default:
                    if (errorCode == ERROR_NOT_LEADER_FOR_PARTITION)
                    {
                        final long metaInitialId = clientRoute.metaInitialId;
                        if (metaInitialId != 0L)
                        {
                            final MessageConsumer metaInitial = supplyReceiver.apply(metaInitialId);
                            // TODO: improve coordination with meta stream
                            doFlush(metaInitial, originId, routedId, metaInitialId, 0, 0, 0,
                                    traceId, authorization, 0, EMPTY_OCTETS);
                        }
                    }

                    cleanupApplication(traceId, errorCode);
                    doNetworkEnd(traceId, authorization);
                    break;
                }
            }

            private void onDecodeFetchTransactionAbort(
                long traceId,
                long authorization,
                long offset,
                long producerId)
            {
                this.nextOffset = offset + 1;

                final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .fetch(f -> f
                            .partition(p -> p
                                .partitionId(decodePartitionId)
                                .partitionOffset(offset)
                                .stableOffset(stableOffset)
                                .latestOffset(latestOffset))
                            .transactionsItem(t -> t
                                .result(r -> r.set(KafkaTransactionResult.ABORT))
                                .producerId(producerId)))
                        .build();

                doApplicationFlush(traceId, authorization, 0, kafkaFlushEx);
            }

            private void onDecodeFetchTransactionCommit(
                long traceId,
                long authorization,
                long offset,
                long producerId)
            {
                this.nextOffset = offset + 1;

                final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .fetch(f -> f
                            .partition(p -> p
                                .partitionId(decodePartitionId)
                                .partitionOffset(offset)
                                .stableOffset(stableOffset)
                                .latestOffset(latestOffset))
                            .transactionsItem(t -> t
                                .result(r -> r.set(KafkaTransactionResult.COMMIT))
                                .producerId(producerId)))
                        .build();

                doApplicationFlush(traceId, authorization, 0, kafkaFlushEx);
            }

            private void onDecodeFetchRecord(
                long traceId,
                boolean aborted,
                int reserved,
                long offset,
                long timestamp,
                long producerId,
                OctetsFW key,
                OctetsFW value,
                int headerCount,
                DirectBuffer headers)
            {
                this.nextOffset = offset + 1;

                final KafkaDataExFW kafkaDataEx = kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .fetch(f ->
                        {
                            f.timestamp(timestamp);
                            f.producerId(producerId);
                            f.partition(p -> p
                                .partitionId(decodePartitionId)
                                .partitionOffset(offset)
                                .stableOffset(stableOffset)
                                .latestOffset(latestOffset));
                            f.key(k -> setKey(k, key));
                            final int headersLimit = headers.capacity();
                            int headerProgress = 0;
                            for (int headerIndex = 0; headerIndex < headerCount; headerIndex++)
                            {
                                final MessageHeaderFW header = messageHeaderRO.wrap(headers, headerProgress, headersLimit);
                                f.headersItem(i -> setHeader(i, header.key(), header.value()));
                                headerProgress = header.limit();
                            }
                        })
                        .build();
                final int flags = aborted ? FLAG_INIT | FLAG_FIN | FLAG_SKIP : FLAG_INIT | FLAG_FIN;
                doApplicationData(traceId, authorization, flags, reserved, value, kafkaDataEx);
            }

            private void onDecodeFetchRecordValueInit(
                long traceId,
                boolean aborted,
                int reserved,
                int deferred,
                long offset,
                long timestamp,
                int headersSizeMax,
                long producerId,
                OctetsFW key,
                OctetsFW valueInit)
            {
                final KafkaDataExFW kafkaDataEx = kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .fetch(f -> f.deferred(deferred)
                                     .timestamp(timestamp)
                                     .headersSizeMax(headersSizeMax)
                                     .producerId(producerId)
                                     .partition(p -> p.partitionId(decodePartitionId)
                                                      .partitionOffset(offset)
                                                      .stableOffset(stableOffset)
                                                      .latestOffset(latestOffset))
                                     .key(k -> setKey(k, key)))
                        .build();

                final int flags = aborted ? FLAG_INIT | FLAG_SKIP : FLAG_INIT;
                doApplicationData(traceId, authorization, flags, reserved, valueInit, kafkaDataEx);
            }

            private void onDecodeFetchRecordValueCont(
                long traceId,
                int reserved,
                OctetsFW value)
            {
                doApplicationData(traceId, authorization, FLAG_CONT, reserved, value, EMPTY_OCTETS);
            }

            private void onDecodeFetchRecordValueFin(
                long traceId,
                int reserved,
                long offset,
                OctetsFW value,
                int headerCount,
                DirectBuffer headers)
            {
                this.nextOffset = offset + 1;

                final KafkaDataExFW kafkaDataEx = kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .fetch(f ->
                        {
                            f.partition(p -> p
                                .partitionId(decodePartitionId)
                                .partitionOffset(offset)
                                .stableOffset(stableOffset)
                                .latestOffset(latestOffset));
                            final int headersLimit = headers.capacity();
                            int headerProgress = 0;
                            for (int headerIndex = 0; headerIndex < headerCount; headerIndex++)
                            {
                                final MessageHeaderFW header = messageHeaderRO.wrap(headers, headerProgress, headersLimit);
                                f.headersItem(i -> setHeader(i, header.key(), header.value()));
                                headerProgress = header.limit();
                            }
                        })
                        .build();

                doApplicationData(traceId, authorization, FLAG_FIN, reserved, value, kafkaDataEx);
            }

            @Override
            protected void onDecodeSaslResponse(
                long traceId)
            {
                nextResponseId++;
                signaler.signalNow(originId, routedId, initialId, SIGNAL_NEXT_REQUEST, 0);
            }

            private void onDecodeFetchResponse(
                long traceId)
            {
                nextResponseId++;

                if (topicPartitions.get(partitionId) == leaderId)
                {
                    doApplicationFlushIfNecessary(traceId, authorization, 0);
                    signaler.signalNow(originId, routedId, initialId, SIGNAL_NEXT_REQUEST, 0);
                }
                else
                {
                    cleanupApplication(traceId, ERROR_NOT_LEADER_FOR_PARTITION);
                    doNetworkEnd(traceId, authorization);
                }
            }

            private void cleanupNetwork(
                long traceId)
            {
                doNetworkResetIfNecessary(traceId);
                doNetworkAbortIfNecessary(traceId);

                cleanupApplication(traceId, EMPTY_OCTETS);
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
        }
    }

    private DirectBuffer wrapHeaders(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        DirectBuffer headers = EMPTY_BUFFER;
        if (length != 0)
        {
            headers = headersRO;
            headers.wrap(buffer, offset, length);
        }
        return headers;
    }

    private static KafkaKeyFW.Builder setKey(
        KafkaKeyFW.Builder key,
        OctetsFW value)
    {
        if (value == null)
        {
            key.length(-1).value((OctetsFW) null);
        }
        else
        {
            final int sizeOfValue = value.sizeof();
            key.length(sizeOfValue).value(value.buffer(), value.offset(), sizeOfValue);
        }

        return key;
    }

    private static KafkaHeaderFW.Builder setHeader(
        KafkaHeaderFW.Builder header,
        OctetsFW name,
        OctetsFW value)
    {
        if (name == null)
        {
            header.nameLen(-1).name((OctetsFW) null);
        }
        else
        {
            final int sizeOfName = name.sizeof();
            header.nameLen(sizeOfName).name(name.buffer(), name.offset(), sizeOfName);
        }

        if (value == null)
        {
            header.valueLen(-1).value((OctetsFW) null);
        }
        else
        {
            final int sizeOfValue = value.sizeof();
            header.valueLen(sizeOfValue).value(value.buffer(), value.offset(), sizeOfValue);
        }

        return header;
    }

    private static boolean isCompressedBatch(
        int attributes)
    {
        // 0 = NONE, 1 = GZIP, 2 = SNAPPY, 3 = LZ4
        return (attributes & 0x07) != 0;
    }

    private static boolean isControlBatch(
        int attributes)
    {
        // sixth lowest bit indicates whether the RecordBatch includes a control message
        return (attributes & 0x20) != 0;
    }

    private static boolean isTransactionalBatch(
        int attributes)
    {
        // fifth lowest bit indicates whether the RecordBatch includes is transactional message
        return (attributes & 0x10) != 0;
    }
}
