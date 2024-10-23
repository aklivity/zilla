/*
 * Copyright 2021-2024 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal.stream;

import static io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaCapabilities.FETCH_ONLY;
import static io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaCapabilities.PRODUCE_ONLY;
import static io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcType.BASE64;
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.lang.System.currentTimeMillis;
import static java.time.Instant.now;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.KafkaGrpcConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config.KafkaGrpcBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config.KafkaGrpcConditionResult;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.queue.GrpcQueueMessageFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcAbortExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcMetadataFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcResetExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcType;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class KafkaGrpcRemoteServerFactory implements KafkaGrpcStreamFactory
{
    private static final String GRPC_TYPE_NAME = "grpc";
    private static final String KAFKA_TYPE_NAME = "kafka";
    private static final String META_PREFIX = "meta:";
    private static final String BIN_SUFFIX = "-bin";
    private static final int META_PREFIX_LENGTH = META_PREFIX.length();
    private static final int BIN_SUFFIX_LENGTH = BIN_SUFFIX.length();

    private static final int SIGNAL_INITIATE_KAFKA_STREAM = 1;
    private static final int GRPC_QUEUE_MESSAGE_PADDING = 3 * 256 + 33;

    private static final int DATA_FLAG_COMPLETE = 0x03;
    private static final int DATA_FLAG_INIT = 0x02;
    private static final int DATA_FLAG_FIN = 0x01;
    private static final int DATA_FLAG_CON = 0x00;

    private static final String16FW HEADER_VALUE_GRPC_OK = new String16FW("0");
    private static final String16FW HEADER_VALUE_GRPC_ABORTED = new String16FW("10");
    private static final String16FW HEADER_VALUE_GRPC_INTERNAL_ERROR = new String16FW("13");

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final GrpcBeginExFW grpcBeginExRO = new GrpcBeginExFW();
    private final GrpcDataExFW grpcDataExRO = new GrpcDataExFW();
    private final GrpcAbortExFW abortExRO = new GrpcAbortExFW();
    private final Array32FW<GrpcMetadataFW> metadataRO = new Array32FW<>(new GrpcMetadataFW());

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();
    private final KafkaFlushExFW.Builder kafkaFlushExRW = new KafkaFlushExFW.Builder();
    private final GrpcQueueMessageFW queueMessageRO = new GrpcQueueMessageFW();

    private final GrpcBeginExFW.Builder grpcBeginExRW = new GrpcBeginExFW.Builder();
    private final GrpcResetExFW.Builder grpcResetExRW = new GrpcResetExFW.Builder();
    private final GrpcAbortExFW.Builder grpcAbortExRW = new GrpcAbortExFW.Builder();
    private final GrpcDataExFW.Builder grpcDataExRW = new GrpcDataExFW.Builder();

    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final GrpcQueueMessageFW.Builder queueMessageRW = new GrpcQueueMessageFW.Builder();

    private final Long2ObjectHashMap<KafkaGrpcBindingConfig> bindings;
    private final List<KafkaRemoteServer> servers;
    private final BufferPool bufferPool;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer metaBuffer;
    private final ExpandableDirectByteBuffer nameBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final LongFunction<BudgetDebitor> supplyDebitor;
    private final Function<Long, String> supplyNamespace;
    private final LongPredicate activate;
    private final LongConsumer deactivate;
    private final Signaler signaler;
    private final String groupIdFormat;
    private final int grpcTypeId;
    private final int kafkaTypeId;
    private long reconnectAt = NO_CANCEL_ID;


    public KafkaGrpcRemoteServerFactory(
        KafkaGrpcConfiguration config,
        EngineContext context,
        LongPredicate activate,
        LongConsumer deactivate)
    {
        this.bufferPool = context.bufferPool();
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.metaBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.signaler = context.signaler();
        this.supplyDebitor = context::supplyDebitor;
        this.supplyTraceId = context::supplyTraceId;
        this.supplyNamespace = context::supplyNamespace;
        this.activate = activate;
        this.deactivate = deactivate;
        this.bindings = new Long2ObjectHashMap<>();
        this.servers = new ArrayList<>();
        this.grpcTypeId = context.supplyTypeId(GRPC_TYPE_NAME);
        this.kafkaTypeId = context.supplyTypeId(KAFKA_TYPE_NAME);
        this.groupIdFormat = config.groupIdFormat();
        this.nameBuffer = new ExpandableDirectByteBuffer();
        this.nameBuffer.putStringWithoutLengthAscii(0, META_PREFIX);
    }

    @Override
    public int originTypeId()
    {
        return kafkaTypeId;
    }

    @Override
    public int routedTypeId()
    {
        return grpcTypeId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        KafkaGrpcBindingConfig newBinding = new KafkaGrpcBindingConfig(binding, metaBuffer);
        bindings.put(binding.id, newBinding);

        if (activate.test(binding.id))
        {
            newBinding.routes.forEach(r ->
                r.when.forEach(c ->
                {
                    final String groupId = String.format(groupIdFormat, supplyNamespace.apply(binding.id), binding.name);
                    KafkaGrpcConditionResult condition = c.resolve();
                    servers.add(
                        new KafkaRemoteServer(newBinding.id, newBinding.entryId, r.id, condition,
                            newBinding.helper, groupId));
                }));

            this.reconnectAt = signaler.signalAt(
                currentTimeMillis(),
                SIGNAL_INITIATE_KAFKA_STREAM,
                this::onKafkaStreamInitializationSignal);
        }
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
        deactivate.accept(bindingId);

        signaler.cancel(reconnectAt);
        reconnectAt = NO_CANCEL_ID;
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer grpc)
    {
        return null;
    }

    private final class KafkaRemoteServer
    {
        private MessageConsumer kafka;
        private final KafkaGrpcConditionResult condition;
        private final KafkaErrorProducer errorProducer;
        private final Map<OctetsFW, GrpcClient> grpcClients;
        private final KafkaGrpcFetchHeaderHelper helper;
        private final String groupId;
        private final long originId;
        private final long routedId;
        private final long entryId;
        private final long initialId;
        private final long replyId;

        private OctetsFW lastCorrelationId;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyReserved;
        private long replyBud;
        private int replyPad;
        private int replyCap;
        private int grpcQueueSlot = NO_SLOT;
        private int grpcQueueSlotOffset;

        private KafkaRemoteServer(
            long originId,
            long routedId,
            long entryId,
            KafkaGrpcConditionResult condition,
            KafkaGrpcFetchHeaderHelper helper,
            String groupId)
        {
            this.entryId = entryId;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.replyAck = 0;
            this.replyMax = bufferPool.slotCapacity();
            this.replyBud = 0;
            this.replyPad = GRPC_QUEUE_MESSAGE_PADDING;
            this.replyCap = 0;
            this.errorProducer = new KafkaErrorProducer(originId, routedId, condition, this);
            this.grpcClients = new Object2ObjectHashMap<>();
            this.condition = condition;
            this.helper = helper;
            this.groupId = groupId;
        }
        private void initiate(
            long traceId)
        {
            doKafkaBegin(traceId, 0L, 0L);
        }

        private void removeIfClosed(
            OctetsFW correlationId)
        {
            GrpcClient grpcClient = grpcClients.get(correlationId);
            if (grpcClient != null && KafkaGrpcState.closed(grpcClient.state))
            {
                grpcClient.cleanupBudgetIfNecessary();
                grpcClients.remove(correlationId);
            }
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            if (!KafkaGrpcState.initialOpening(state))
            {
                state = KafkaGrpcState.openingInitial(state);

                kafka = newKafkaFetch(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, condition, groupId);
            }
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (!KafkaGrpcState.initialClosed(state) && kafka != null)
            {

                state = KafkaGrpcState.closeInitial(state);

                doAbort(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void onKafkaMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onKafkaBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onKafkaData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onKafkaEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onKafkaAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onKafkaReset(reset);
                break;
            }
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = KafkaGrpcState.openingReply(state);

            assert replyAck <= replySeq;

            doKafkaWindow(traceId, authorization);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();
            int flags = data.flags();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + reserved;
            replyReserved += reserved;

            assert replyAck <= replySeq;

            int deferred = 0;

            if ((flags & DATA_FLAG_INIT) != 0x00)
            {
                final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                final KafkaDataExFW kafkaDataEx = dataEx != null && dataEx.typeId() == kafkaTypeId ?
                    extension.get(kafkaDataExRO::tryWrap) : null;

                helper.visit(kafkaDataEx);
                deferred = kafkaDataEx.merged().fetch().deferred();
            }

            Array32FW<GrpcMetadataFW> metadata = helper.metadata;

            if ((flags & DATA_FLAG_INIT) != 0x00 && payload != null)
            {
                if (helper.resolved())
                {
                    GrpcClient grpcClient = grpcClients.get(helper.correlationId);
                    if (grpcClient == null)
                    {
                        final int correlationLength = helper.correlationId.sizeof();
                        OctetsFW newCorrelationId = new OctetsFW.Builder()
                            .wrap(new UnsafeBuffer(new byte[correlationLength]), 0, correlationLength)
                            .set(helper.correlationId)
                            .build();
                        lastCorrelationId = newCorrelationId;
                        grpcClient = newGrpcClient(traceId, authorization, helper.service, helper.method, metadata,
                            helper.replyTo, newCorrelationId);
                    }

                    flushGrpcClientData(grpcClient, traceId, authorization, helper.service, helper.method, metadata,
                        helper.partitionId, helper.partitionOffset, deferred, flags, reserved, payload);
                }
                else if (helper.correlationId != null)
                {
                    errorProducer.onKafkaError(traceId, authorization, helper.replyTo, helper.correlationId);
                }
            }
            else
            {
                GrpcClient grpcClient = lastCorrelationId == null ? null : grpcClients.get(lastCorrelationId);

                if (grpcClient == null)
                {
                    doKafkaCommitOffset(traceId, authorization, helper.partitionId, helper.partitionOffset);
                }
                else
                {
                    flushGrpcClientData(grpcClient, traceId, authorization, helper.service, helper.method, metadata,
                        helper.partitionId, helper.partitionOffset, deferred, flags, reserved, payload);
                }
            }

            doKafkaWindow(traceId, authorization);
        }

        private GrpcClient newGrpcClient(
            long traceId,
            long authorization,
            OctetsFW service,
            OctetsFW method,
            Array32FW<GrpcMetadataFW> metadata,
            OctetsFW replyTo,
            OctetsFW correlationId)
        {
            final GrpcClient grpcClient = new GrpcClient(originId, entryId, routedId, correlationId, replyTo, this);
            grpcClients.put(correlationId, grpcClient);

            grpcClient.doGrpcBegin(traceId, authorization, 0L, service, method, metadata);

            return grpcClient;
        }

        private void flushGrpcMessagesIfBuffered(
            long traceId,
            long authorization,
            OctetsFW correlationId)
        {
            int progressOffset = 0;

            flush:
            while (progressOffset < grpcQueueSlotOffset)
            {
                final MutableDirectBuffer grpcQueueBuffer = bufferPool.buffer(grpcQueueSlot);
                final GrpcQueueMessageFW queueMessage = queueMessageRO
                    .wrap(grpcQueueBuffer, progressOffset, grpcQueueSlotOffset);

                final OctetsFW messageCorrelationId = queueMessage.correlationId();
                final OctetsFW service = queueMessage.service();
                final OctetsFW method = queueMessage.method();
                final OctetsFW metadata = queueMessage.metadata();
                final long messageTraceId = queueMessage.traceId();
                final long messageAuthorization = queueMessage.authorization();
                final int partitionId = queueMessage.partitionId();
                final long partitionOffset = queueMessage.partitionOffset();
                final int deferred = queueMessage.deferred();
                final int flags = queueMessage.flags();
                final int reserved = queueMessage.reserved();
                final int valueLength = queueMessage.valueLength();
                final OctetsFW payload = queueMessage.value();

                final int queuedMessageSize = queueMessage.sizeof();
                final int oldProgressOffset = progressOffset;
                progressOffset = queueMessage.limit();

                if (correlationId.equals(messageCorrelationId))
                {
                    Array32FW<GrpcMetadataFW> meta = metadataRO.wrap(metadata.buffer(), metadata.offset(), metadata.limit());
                    GrpcClient grpcClient = grpcClients.get(messageCorrelationId);
                    grpcClient = grpcClient != null ? grpcClient :
                        newGrpcClient(traceId, authorization, service, method, meta, helper.replyTo, messageCorrelationId);

                    final int progress = grpcClient.onKafkaData(messageTraceId, messageAuthorization,
                        partitionId, partitionOffset, deferred, flags, payload);

                    if (payload == null || progress == valueLength)
                    {
                        replyReserved -= reserved;
                        final int remaining = grpcQueueSlotOffset - progressOffset;
                        grpcQueueBuffer.putBytes(oldProgressOffset, grpcQueueBuffer, progressOffset, remaining);

                        grpcQueueSlotOffset = oldProgressOffset + remaining;
                        progressOffset = oldProgressOffset;
                    }
                    else if (progress > 0)
                    {
                        final int remainingPayload = queuedMessageSize - progress;
                        queueGrpcMessage(traceId, authorization, partitionId, partitionOffset, messageCorrelationId,
                            service, method, meta, deferred, flags, reserved, payload, remainingPayload);
                        final int remainingMessageOffset = grpcQueueSlotOffset - progressOffset;
                        grpcQueueBuffer.putBytes(oldProgressOffset, grpcQueueBuffer, progressOffset, remainingMessageOffset);
                        grpcQueueSlotOffset -= queuedMessageSize;
                        break flush;
                    }
                    else
                    {
                        break flush;
                    }
                }
            }

            cleanupQueueSlotIfNecessary();

            doKafkaWindow(traceId, authorization);
        }

        private void flushGrpcClientData(
            GrpcClient grpcClient,
            long traceId,
            long authorization,
            OctetsFW service,
            OctetsFW method,
            Array32FW<GrpcMetadataFW> metadata,
            int partitionId,
            long partitionOffset,
            int deferred,
            int flags,
            int reserved,
            OctetsFW payload)
        {
            final int progress = grpcClient.onKafkaData(traceId, authorization, partitionId, partitionOffset,
                deferred, flags, payload);
            int length = payload != null ? payload.sizeof() : 0;
            final int remaining = length - progress;

            if (remaining == 0 && payload != null ||
                payload == null && KafkaGrpcState.initialClosing(grpcClient.state))
            {
                replyReserved -= reserved;
            }

            if (remaining > 0 && payload != null ||
                payload == null && !KafkaGrpcState.initialClosing(grpcClient.state))
            {
                flags = progress == 0 ? flags : DATA_FLAG_CON;
                queueGrpcMessage(traceId, authorization, partitionId, partitionOffset,
                    grpcClient.correlationId, service, method, metadata, deferred, flags, reserved, payload, remaining);
            }
        }

        private void queueGrpcMessage(
            long traceId,
            long authorization,
            int partitionId,
            long partitionOffset,
            OctetsFW correlationId,
            OctetsFW service,
            OctetsFW method,
            Array32FW<GrpcMetadataFW> metadata,
            int deferred,
            int flags,
            int reserved,
            OctetsFW payload,
            int length)
        {
            if (grpcQueueSlot == NO_SLOT)
            {
                grpcQueueSlot = bufferPool.acquire(initialId);
            }

            final MutableDirectBuffer grpcQueueBuffer = bufferPool.buffer(grpcQueueSlot);
            GrpcQueueMessageFW.Builder queueMessageBuilder = queueMessageRW
                .wrap(grpcQueueBuffer, grpcQueueSlotOffset, grpcQueueBuffer.capacity())
                .correlationId(correlationId)
                .service(service)
                .method(method)
                .metadata(metadata.buffer(), metadata.offset(), metadata.sizeof())
                .traceId(traceId)
                .authorization(authorization)
                .partitionId(partitionId)
                .partitionOffset(partitionOffset)
                .deferred(deferred)
                .flags(flags)
                .reserved(reserved);

            if (payload == null)
            {
                queueMessageBuilder.value(payload);
            }
            else
            {
                queueMessageBuilder.value(payload.value(), payload.sizeof() - length, length);
            }

            final GrpcQueueMessageFW queueMessage = queueMessageBuilder.build();

            grpcQueueSlotOffset = queueMessage.limit();
        }

        private void doKafkaCommitOffset(
            long traceId,
            long authorization,
            int partitionId,
            long partitionOffset)
        {
            final long nextPartitionOffset = partitionOffset + 1L;

            Flyweight commitFlushEx = kafkaFlushExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.consumer(mc -> mc
                    .progress(p -> p
                        .partitionId(partitionId)
                        .partitionOffset(nextPartitionOffset))))
                .build();

            doKafkaFlush(traceId, authorization, 0, 0, commitFlushEx);
        }

        private void cleanupQueueSlotIfNecessary()
        {
            if (grpcQueueSlot != NO_SLOT && grpcQueueSlotOffset == 0)
            {
                bufferPool.release(grpcQueueSlot);
                grpcQueueSlot = NO_SLOT;
                grpcQueueSlotOffset = 0;
            }
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = KafkaGrpcState.closeReply(state);

            assert replyAck <= replySeq;
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = KafkaGrpcState.closeReply(state);

            assert replyAck <= replySeq;

            doKafkaAbort(traceId, authorization);
        }

        private void onKafkaWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();

            assert acknowledge <= sequence;

            initialAck = acknowledge;
            initialMax = maximum;
            state = KafkaGrpcState.openInitial(state);

            assert initialAck <= initialSeq;
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            assert acknowledge <= sequence;

            doKafkaReset(traceId, authorization);
        }

        private void doKafkaReset(
            long traceId,
            long authorization)
        {
            if (!KafkaGrpcState.replyClosed(state) && kafka != null)
            {
                state = KafkaGrpcState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void doKafkaWindow(
            long traceId,
            long authorization)
        {
            long replyAckMax = Math.max(replySeq - replyReserved, replyAck);

            replyAck = replyAckMax;
            assert replyAck <= replySeq;

            doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, replyBud, replyPad, replyCap);
        }

        private void doKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            Flyweight extension)
        {
            doFlush(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, reserved, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }
    }

    private final class KafkaCorrelateProxy
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final KafkaGrpcConditionResult condition;
        private final OctetsFW replyTo;
        private final GrpcClient delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long replyBud;
        private int replyPad;
        private int replyCap;

        private KafkaCorrelateProxy(
            long originId,
            long routedId,
            OctetsFW replyTo,
            KafkaGrpcConditionResult condition,
            GrpcClient delegate)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.replyTo = replyTo;
            this.delegate = delegate;
            this.condition = condition;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            initialSeq = delegate.replySeq;
            initialAck = delegate.replyAck;
            initialMax = delegate.replyMax;
            state = KafkaGrpcState.openingInitial(state);

            kafka = newKafkaProducer(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, replyTo, condition);
        }

        private void doKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            Flyweight extension)
        {
            doData(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, flags, reserved, payload, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doKafkaEnd(
            long traceId,
            long authorization)
        {
            if (KafkaGrpcState.initialOpened(state) &&
                !KafkaGrpcState.initialClosed(state))
            {
                initialSeq = delegate.replySeq;
                initialAck = delegate.replyAck;
                initialMax = delegate.replyMax;
                state = KafkaGrpcState.closeInitial(state);

                doKafkaTombstone(traceId, authorization, HEADER_VALUE_GRPC_OK);

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        private void doKafkaAbort(
            long traceId,
            long authorization,
            String16FW status)
        {
            if (KafkaGrpcState.initialOpening(state) &&
                !KafkaGrpcState.initialClosed(state))
            {
                initialSeq = delegate.replySeq;
                initialAck = delegate.replyAck;
                initialMax = delegate.replyMax;
                state = KafkaGrpcState.closeInitial(state);

                doKafkaTombstone(traceId, authorization, status);

                doAbort(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void onKafkaMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onKafkaBegin(begin);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onKafkaEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onKafkaAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onKafkaReset(reset);
                break;
            }
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final OctetsFW extension = begin.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = KafkaGrpcState.openingReply(state);

            assert replyAck <= replySeq;

            doKafkaWindow(traceId);
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = KafkaGrpcState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaEnd(traceId, authorization);
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = KafkaGrpcState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaAbort(traceId, authorization);
        }

        private void onKafkaWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long authorization = window.authorization();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.replyAck;
            assert maximum >= delegate.replyMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = KafkaGrpcState.openInitial(state);

            assert initialAck <= initialSeq;

            delegate.onKafkaWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;
            state = KafkaGrpcState.closeInitial(state);

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.onKafkaReset(traceId, authorization);

            doKafkaReset(traceId, authorization);
        }

        private void doKafkaReset(
            long traceId,
            long authorization)
        {
            if (KafkaGrpcState.replyOpened(state) &&
                !KafkaGrpcState.replyClosed(state))
            {
                state = KafkaGrpcState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void doKafkaWindow(
            long traceId)
        {
            if (kafka != null && !KafkaGrpcState.replyClosed(state))
            {
                doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, 0L, replyBud, replyPad, replyCap);
            }
        }

        private void doKafkaTombstone(
            long traceId,
            long authorization,
            String16FW status)
        {
            Flyweight tombstoneDataEx = kafkaDataExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.produce(mp -> mp
                    .timestamp(now().toEpochMilli())
                    .partition(p -> p.partitionId(-1).partitionOffset(-1))
                    .key(k -> condition.key(delegate.correlationId, k))
                    .headers(h -> condition.headersWithStatusCode(delegate.correlationId, status, h))))
                .build();

            doKafkaData(traceId, authorization, delegate.initialBudetId, 0, DATA_FLAG_COMPLETE, null, tombstoneDataEx);
        }
    }

    private final class KafkaErrorProducer
    {
        private MessageConsumer kafka;
        private final KafkaRemoteServer server;
        private final KafkaGrpcConditionResult condition;
        private final List<OctetsFW> correlationIds;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long replyBud;
        private int replyPad;
        private int replyCap;

        private KafkaErrorProducer(
            long originId,
            long routedId,
            KafkaGrpcConditionResult condition,
            KafkaRemoteServer server)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.condition = condition;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.server = server;
            this.correlationIds = new ArrayList<>();
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity,
            OctetsFW replyTo)
        {
            state = KafkaGrpcState.openingInitial(state);

            kafka = newKafkaProducer(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, replyTo, condition);
        }

        private void doKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            Flyweight extension)
        {
            doData(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, flags, reserved, payload, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void onKafkaMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onKafkaBegin(begin);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onKafkaEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onKafkaAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onKafkaReset(reset);
                break;
            }
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final OctetsFW extension = begin.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = KafkaGrpcState.openingReply(state);

            assert replyAck <= replySeq;

            doKafkaWindow(traceId);
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = KafkaGrpcState.closeReply(state);

            assert replyAck <= replySeq;
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = KafkaGrpcState.closeReply(state);

            assert replyAck <= replySeq;
        }

        private void onKafkaWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final int maximum = window.maximum();

            assert acknowledge <= sequence;

            initialAck = acknowledge;
            initialMax = maximum;
            state = KafkaGrpcState.openInitial(state);

            assert initialAck <= initialSeq;

            doKafkaDataNull(traceId, authorization);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            assert acknowledge <= sequence;

            state = KafkaGrpcState.closeInitial(state);

            doKafkaReset(traceId, authorization);
        }

        private void onKafkaError(
            long traceId,
            long authorization,
            OctetsFW replyTo,
            OctetsFW correlationId)
        {
            correlationIds.add(new OctetsFW.Builder()
                .wrap(new UnsafeBuffer(new byte[correlationId.sizeof()]), 0, correlationId.sizeof())
                .set(correlationId)
                .build());

            if (!KafkaGrpcState.initialOpened(state))
            {
                doKafkaBegin(traceId, authorization, 0, replyTo);
            }
            else
            {
                doKafkaDataNull(traceId, authorization);
            }
        }

        private void doKafkaReset(
            long traceId,
            long authorization)
        {
            if (KafkaGrpcState.replyOpened(state) &&
                !KafkaGrpcState.replyClosed(state))
            {
                state = KafkaGrpcState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void doKafkaWindow(
            long traceId)
        {
            if (kafka != null && !KafkaGrpcState.replyClosed(state))
            {
                doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, 0L, replyBud, replyPad, replyCap);
            }
        }


        private void doKafkaDataNull(
            long traceId,
            long authorization)
        {
            correlationIds.forEach(c ->
            {
                Flyweight tombstoneDataEx = kafkaDataExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m -> m.produce(mp -> mp
                        .timestamp(now().toEpochMilli())
                        .partition(p -> p.partitionId(-1).partitionOffset(-1))
                        .key(k -> condition.key(c, k))
                        .headers(h -> condition.headersWithStatusCode(c, HEADER_VALUE_GRPC_INTERNAL_ERROR, h))))
                    .build();
                doKafkaData(traceId, authorization, 0, 0, DATA_FLAG_COMPLETE, null, tombstoneDataEx);
            });

            correlationIds.clear();
        }
    }

    private final class GrpcClient
    {
        private MessageConsumer grpc;
        private final KafkaRemoteServer server;
        private final KafkaCorrelateProxy correlater;
        private final OctetsFW correlationId;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long initialBudetId;
        private int initialPad;
        private int initialCap;
        private int initialAuth;

        private BudgetDebitor initialDeb;
        private long initialDebIndex = NO_DEBITOR_INDEX;

        private int state;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private GrpcClient(
            long originId,
            long routedId,
            long resolveId,
            OctetsFW correlationId,
            OctetsFW replyTo,
            KafkaRemoteServer server)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.correlationId = correlationId;
            this.server = server;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.correlater = new KafkaCorrelateProxy(originId, resolveId, replyTo, server.condition, this);
        }

        private int initialPendingAck()
        {
            return (int)(initialSeq - initialAck);
        }

        private int initialWindow()
        {
            return initialMax - initialPendingAck();
        }

        private void onGrpcMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onGrpcBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onGrpcData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onGrpcEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onGrpcAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onGrpcReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onGrpcWindow(window);
                break;
            }
        }

        private void onGrpcBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();
            final OctetsFW extension = begin.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = KafkaGrpcState.openReply(state);

            assert replyAck <= replySeq;

            if (extension.sizeof() > 0)
            {
                extension.get(grpcBeginExRO::tryWrap);
            }

            correlater.doKafkaBegin(traceId, authorization, affinity);
        }

        private void onGrpcData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            Flyweight kafkaDataEx = emptyRO;
            if ((flags & DATA_FLAG_INIT) != 0x00)
            {
                GrpcDataExFW dataEx = null;
                if (extension.sizeof() > 0)
                {
                    dataEx = extension.get(grpcDataExRO::tryWrap);
                }

                final int deferred = dataEx != null ? dataEx.deferred() : 0;
                kafkaDataEx = kafkaDataExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m -> m.produce(mp -> mp
                        .deferred(deferred)
                        .timestamp(now().toEpochMilli())
                        .partition(p -> p.partitionId(-1).partitionOffset(-1))
                        .key(k -> server.condition.key(correlationId, k))
                        .headers(h ->
                        {
                            server.condition.headers(correlationId, h);
                            if (grpcBeginExRO.buffer() != null)
                            {
                                Array32FW<GrpcMetadataFW> metadata = grpcBeginExRO.metadata();
                                metadata.forEach(md -> h.item(i -> metadata(i, md)));
                            }
                        })))
                    .build();
            }

            correlater.doKafkaData(traceId, authorization, budgetId, reserved, flags, payload, kafkaDataEx);
        }

        private void onGrpcEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = KafkaGrpcState.closeReply(state);

            assert replyAck <= replySeq;

            correlater.doKafkaEnd(traceId, authorization);

            server.removeIfClosed(correlationId);
        }

        private void onGrpcAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            final OctetsFW extension = abort.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = KafkaGrpcState.closeReply(state);

            assert replyAck <= replySeq;

            final GrpcAbortExFW abortEx = extension != null ? extension.get(abortExRO::tryWrap) : null;

            final String16FW status = abortEx != null ? abortEx.status() : HEADER_VALUE_GRPC_ABORTED;

            correlater.doKafkaAbort(traceId, authorization, status);
        }

        private void onGrpcReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final OctetsFW extension = reset.extension();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum >= initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = KafkaGrpcState.closeInitial(state);

            cleanup(traceId, authorization);

            server.flushGrpcMessagesIfBuffered(traceId, authorization, correlationId);
        }

        private void onGrpcWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum >= initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            initialBudetId = budgetId;
            initialAuth = padding;
            initialPad = padding;
            initialCap = capabilities;
            state = KafkaGrpcState.openInitial(state);

            assert initialAck <= initialSeq;

            if (initialBudetId != 0L && initialDebIndex == NO_DEBITOR_INDEX)
            {
                initialDeb = supplyDebitor.apply(budgetId);
                initialDebIndex = initialDeb.acquire(budgetId, initialId, this::flushMessage);
                assert initialDebIndex != NO_DEBITOR_INDEX;
            }

            if (initialBudetId != 0L && initialDebIndex == NO_DEBITOR_INDEX)
            {
                cleanup(traceId, authorization);
            }
            else
            {
                flushMessage(traceId);
            }
        }

        private void onKafkaReset(
            long traceId,
            long authorization)
        {
            cleanup(traceId, authorization);
        }

        private void onKafkaAbort(
            long traceId,
            long authorization)
        {
            cleanup(traceId, authorization);
        }

        private int onKafkaData(
            long traceId,
            long authorization,
            int partitionId,
            long partitionOffset,
            int deferred,
            int flags,
            OctetsFW payload)
        {
            final int payloadLength = payload != null ? payload.sizeof() : 0;
            final int length = Math.min(Math.max(initialWindow() - initialPad, 0), payloadLength);
            final int reservedMin = Math.min(payloadLength, 1024) + initialPad;
            final int reserved = length + initialPad;

            deferred = (flags & DATA_FLAG_INIT) != 0x00 ? deferred : 0;

            int claimed = reserved;
            if (length > 0 && initialDebIndex != NO_DEBITOR_INDEX)
            {
                claimed = initialDeb.claim(traceId, initialDebIndex, initialId, reserved, reserved, deferred);
            }

            final int flushableBytes = Math.max(claimed - initialPad, 0);

            if (length > 0 && claimed > 0)
            {
                final int newFlags = payloadLength ==  flushableBytes ? flags : flags & DATA_FLAG_INIT;
                doGrpcData(traceId, authorization, initialBudetId, reserved,
                    deferred, newFlags, payload.value(), 0, flushableBytes);

                if ((newFlags & DATA_FLAG_FIN) != 0x00) // FIN
                {
                    server.doKafkaCommitOffset(traceId, authorization, partitionId, partitionOffset);
                    state = KafkaGrpcState.closingInitial(state);
                }
            }

            if (payload == null && KafkaGrpcState.initialClosing(state))
            {
                server.doKafkaCommitOffset(traceId, authorization, partitionId, partitionOffset);

                doGrpcEnd(traceId, authorization);
            }

            return flushableBytes;
        }

        private void onKafkaEnd(
            long traceId,
            long authorization)
        {
            if (KafkaGrpcState.replyClosed(correlater.state))
            {
                doGrpcEnd(traceId, authorization);
            }
        }

        private void onKafkaWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            doGrpcWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void cleanup(
            long traceId,
            long authorization)
        {
            doGrpcReset(traceId, authorization);
            doGrpcAbort(traceId, authorization);

            correlater.doKafkaAbort(traceId, authorization, HEADER_VALUE_GRPC_INTERNAL_ERROR);
            correlater.doKafkaReset(traceId, authorization);

            server.removeIfClosed(correlationId);
        }

        private void cleanupBudgetIfNecessary()
        {
            if (initialDebIndex != NO_DEBITOR_INDEX)
            {
                initialDeb.release(initialDebIndex, initialId);
                initialDebIndex = NO_DEBITOR_INDEX;
            }
        }

        private void metadata(
            KafkaHeaderFW.Builder builder,
            GrpcMetadataFW metadata)
        {
            GrpcType type = metadata.type().get();
            DirectBuffer name = metadata.name().value();
            int nameLen = META_PREFIX_LENGTH + metadata.nameLen();
            nameBuffer.putBytes(META_PREFIX_LENGTH, name, 0, name.capacity());
            if (type == BASE64)
            {
                nameBuffer.putStringWithoutLengthAscii(nameLen, BIN_SUFFIX);
                nameLen += BIN_SUFFIX_LENGTH;
            }
            builder
                .nameLen(nameLen)
                .name(nameBuffer, 0, nameLen)
                .valueLen(metadata.valueLen())
                .value(metadata.value().value(), 0, metadata.valueLen());
        }

        private void doGrpcBegin(
            long traceId,
            long authorization,
            long affinity,
            OctetsFW service,
            OctetsFW method,
            Array32FW<GrpcMetadataFW> metadata)
        {
            state = KafkaGrpcState.openingInitial(state);

            grpc = newGrpcStream(this::onGrpcMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, server.condition.scheme(), server.condition.authority(),
                service, method, metadata);
        }

        private void doGrpcData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int deferred,
            int flags,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            GrpcDataExFW dataEx = grpcDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(grpcTypeId)
                .deferred(deferred)
                .build();

            doData(grpc, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, reserved, flags, buffer, offset, length, dataEx);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doGrpcAbort(
            long traceId,
            long authorization)
        {
            if (KafkaGrpcState.initialOpening(state) &&
                !KafkaGrpcState.initialClosed(state))
            {
                final GrpcAbortExFW grpcAbortEx =
                    grpcAbortExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(grpcTypeId)
                        .status(HEADER_VALUE_GRPC_INTERNAL_ERROR)
                        .build();

                doAbort(grpc, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, grpcAbortEx);
            }
            state = KafkaGrpcState.closeInitial(state);
        }

        private void doGrpcEnd(
            long traceId,
            long authorization)
        {
            if (!KafkaGrpcState.initialClosed(state))
            {
                state = KafkaGrpcState.closeInitial(state);

                doEnd(grpc, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        private void doGrpcWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            replyAck = correlater.initialAck;
            replyMax = correlater.initialMax;

            doWindow(grpc, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding, capabilities);
        }

        private void doGrpcReset(
            long traceId,
            long authorization)
        {
            if (!KafkaGrpcState.replyClosed(state))
            {
                state = KafkaGrpcState.closeReply(state);

                final GrpcResetExFW grpcResetEx =
                    grpcResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(grpcTypeId)
                        .status(HEADER_VALUE_GRPC_INTERNAL_ERROR)
                        .build();

                doReset(grpc, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, grpcResetEx);
            }
        }

        private void flushMessage(
            long traceId)
        {
            server.flushGrpcMessagesIfBuffered(traceId, initialAuth, correlationId);
        }
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
        int flags,
        int reserved,
        OctetsFW payload,
        Flyweight extension)
    {
        final DataFW frame = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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

        receiver.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());
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
        int flags,
        DirectBuffer buffer,
        int index,
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
            .flags(flags)
            .budgetId(budgetId)
            .reserved(reserved)
            .payload(buffer, index, length)
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
        long authorization)
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
        long budgetId,
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
            .budgetId(budgetId)
            .reserved(reserved)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private MessageConsumer newKafkaProducer(
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
        OctetsFW replyTo,
        KafkaGrpcConditionResult condition)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(PRODUCE_ONLY))
                              .topic(replyTo.value(), 0, replyTo.sizeof())
                              .partitionsItem(p -> p.partitionId(-1).partitionOffset(-2L))
                              .ackMode(condition::acks))
                .build();

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
                .extension(kafkaBeginEx.buffer(), kafkaBeginEx.offset(), kafkaBeginEx.sizeof())
                .build();

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private void onKafkaStreamInitializationSignal(
        int signalId)
    {
        assert signalId == SIGNAL_INITIATE_KAFKA_STREAM;

        this.reconnectAt = NO_CANCEL_ID;

        final long traceId = supplyTraceId.getAsLong();

        servers.forEach(s -> s.initiate(traceId));
    }

    private MessageConsumer newGrpcStream(
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
        String16FW scheme,
        String16FW authority,
        OctetsFW service,
        OctetsFW method,
        Array32FW<GrpcMetadataFW> metadata)
    {
        final GrpcBeginExFW grpcBeginEx =
            grpcBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(grpcTypeId)
                .scheme(scheme)
                .authority(authority)
                .service(service.buffer(), service.offset(), service.sizeof())
                .method(method.buffer(), method.offset(), method.sizeof())
                .metadata(metadata)
                .build();

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
                .extension(grpcBeginEx.buffer(), grpcBeginEx.offset(), grpcBeginEx.sizeof())
                .build();

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private MessageConsumer newKafkaFetch(
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
        KafkaGrpcConditionResult condition,
        String groupId)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(FETCH_ONLY))
                    .topic(condition.topic())
                    .groupId(groupId)
                    .partitions(condition::partitions)
                    .filters(condition::filters))
                .build();

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
            .extension(kafkaBeginEx.buffer(), kafkaBeginEx.offset(), kafkaBeginEx.sizeof())
            .build();

        MessageConsumer receiver =
            streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
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
        int padding,
        int capabilities)
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
                .capabilities(capabilities)
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
