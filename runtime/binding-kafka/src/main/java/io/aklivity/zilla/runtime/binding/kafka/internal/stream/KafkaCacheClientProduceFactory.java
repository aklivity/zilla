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

import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition.CACHE_ENTRY_FLAGS_ADVANCE;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetFW.Builder.DEFAULT_LATEST_OFFSET;
import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_CREDITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.currentThread;
import static java.time.Instant.now;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.budget.KafkaCacheClientBudget;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCache;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorFactory;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorFactory.KafkaCacheCursor;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheTopic;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaEvaluation;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaProduceBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaProduceDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaProduceFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;

public final class KafkaCacheClientProduceFactory implements BindingHandler
{
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(), 0, 0);
    private static final KafkaKeyFW EMPTY_KEY =
        new OctetsFW().wrap(new UnsafeBuffer(ByteBuffer.wrap(new byte[] { 0x00 })), 0, 1)
            .get(new KafkaKeyFW()::wrap);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};
    private static final Array32FW<KafkaHeaderFW> EMPTY_TRAILERS =
            new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                .wrap(new UnsafeBuffer(new byte[8]), 0, 8)
                .build();
    private static final int PRODUCE_FLUSH_SEQUENCE = -1;

    private static final int ERROR_CORRUPT_MESSAGE = 2;
    private static final int ERROR_NOT_LEADER_FOR_PARTITION = 6;
    private static final int ERROR_RECORD_LIST_TOO_LARGE = 18;
    private static final int NO_ERROR = -1;
    private static final int UNKNOWN_ERROR = -2;
    private static final int ERROR_INVALID_RECORD = 87;

    private static final Array32FW<KafkaFilterFW> EMPTY_FILTER =
        new Array32FW.Builder<>(new KafkaFilterFW.Builder(), new KafkaFilterFW())
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64).build();

    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_INCOMPLETE = 0x04;

    private static final int SIZE_OF_FLUSH_WITH_EXTENSION = 64;

    private static final String TRANSACTION_NONE = null;

    private static final int SIGNAL_SEGMENT_COMPACT = 1;
    private static final int SIGNAL_GROUP_CLEANUP = 2;

    private static final int SIGNAL_RECONNECT = 3;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final FlushFW flushRO = new FlushFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final SignalFW signalRO = new SignalFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaFlushExFW kafkaFlushExRO = new KafkaFlushExFW();

    private final KafkaResetExFW kafkaResetExRO = new KafkaResetExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();
    private final KafkaResetExFW.Builder kafkaResetExRW = new KafkaResetExFW.Builder();
    private final KafkaFlushExFW.Builder kafkaFlushExRW = new KafkaFlushExFW.Builder();

    private final KafkaCacheEntryFW entryRO = new KafkaCacheEntryFW();

    private final int kafkaTypeId;
    private final BufferPool bufferPool;
    private final BudgetCreditor creditor;
    private final Signaler signaler;
    private final BindingHandler streamFactory;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final LongSupplier supplyBudgetId;
    private final LongFunction<String> supplyNamespace;
    private final LongFunction<String> supplyLocalName;
    private final LongFunction<KafkaBindingConfig> supplyBinding;
    private final Function<String, KafkaCache> supplyCache;
    private final LongFunction<KafkaCacheRoute> supplyCacheRoute;
    private final KafkaCacheCursorFactory cursorFactory;
    private final int initialBudgetMax;
    private final int localIndex;
    private final int cleanupDelay;
    private final int trailersSizeMax;
    private final int reconnectDelay;

    public KafkaCacheClientProduceFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding,
        Function<String, KafkaCache> supplyCache,
        LongFunction<KafkaCacheRoute> supplyCacheRoute)
    {
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.bufferPool = context.bufferPool();
        this.creditor = context.creditor();
        this.signaler = context.signaler();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.supplyBudgetId = context::supplyBudgetId;
        this.supplyNamespace = context::supplyNamespace;
        this.supplyLocalName = context::supplyLocalName;
        this.supplyBinding = supplyBinding;
        this.supplyCache = supplyCache;
        this.supplyCacheRoute = supplyCacheRoute;
        this.initialBudgetMax = bufferPool.slotCapacity();
        this.localIndex = context.index();
        this.cleanupDelay = config.cacheClientCleanupDelay();
        this.cursorFactory = new KafkaCacheCursorFactory(context.writeBuffer().capacity());
        this.trailersSizeMax = config.cacheClientTrailersSizeMax();
        this.reconnectDelay = config.cacheServerReconnect();
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long affinity = begin.affinity();
        final long authorization = begin.authorization();

        assert (initialId & 0x0000_0000_0000_0001L) != 0L;

        final OctetsFW extension = begin.extension();
        final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
        assert beginEx != null && beginEx.typeId() == kafkaTypeId;
        final KafkaBeginExFW kafkaBeginEx = extension.get(kafkaBeginExRO::wrap);
        assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_PRODUCE;
        final KafkaProduceBeginExFW kafkaProduceBeginEx = kafkaBeginEx.produce();
        final String16FW beginTopic = kafkaProduceBeginEx.topic();
        final int partitionId = kafkaProduceBeginEx.partition().partitionId();
        final String topicName = beginTopic.asString();

        MessageConsumer newStream = null;

        final KafkaBindingConfig binding = supplyBinding.apply(routedId);
        final KafkaRouteConfig resolved = binding != null ? binding.resolve(authorization, topicName) : null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;
            final KafkaCacheRoute cacheRoute = supplyCacheRoute.apply(resolvedId);
            final long topicKey = cacheRoute.topicKey(topicName);
            final long partitionKey = cacheRoute.topicPartitionKey(topicName, partitionId);

            KafkaCacheClientProduceFan fan = cacheRoute.clientProduceFansByTopicPartition.get(partitionKey);
            if (fan == null)
            {
                KafkaCacheClientBudget budget = cacheRoute.clientBudgetsByTopic.get(topicKey);

                if (budget == null)
                {
                    budget = new KafkaCacheClientBudget(creditor, supplyBudgetId.getAsLong(), bufferPool.slotCapacity());
                    cacheRoute.clientBudgetsByTopic.put(topicKey, budget);
                }

                final String cacheName =
                        String.format("%s.%s", supplyNamespace.apply(resolvedId), supplyLocalName.apply(resolvedId));
                final KafkaCache cache = supplyCache.apply(cacheName);
                final KafkaCacheTopic topic = cache.supplyTopic(topicName);
                final KafkaCachePartition partition = topic.supplyProducePartition(partitionId, localIndex);
                final ConverterHandler convertKey = binding.resolveKeyWriter(topicName);
                final ConverterHandler convertValue = binding.resolveValueWriter(topicName);
                final KafkaCacheClientProduceFan newFan =
                        new KafkaCacheClientProduceFan(routedId, resolvedId, authorization, budget,
                            partition, cacheRoute, topicName, convertKey, convertValue);

                cacheRoute.clientProduceFansByTopicPartition.put(partitionKey, newFan);
                fan = newFan;
            }

            final Int2IntHashMap leadersByPartitionId = cacheRoute.supplyLeadersByPartitionId(topicName);
            final int leaderId = leadersByPartitionId.get(partitionId);
            newStream = new KafkaCacheClientProduceStream(
                    fan,
                    sender,
                    originId,
                    routedId,
                    initialId,
                    leaderId,
                    authorization)::onClientMessage;
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
        Consumer<OctetsFW.Builder> extension)
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
                .extension(extension)
                .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
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

    final class KafkaCacheClientProduceFan
    {
        private final KafkaCachePartition partition;
        private final KafkaCacheCursor cursor;
        private final KafkaOffsetType defaultOffset;
        private final Long2ObjectHashMap<KafkaCacheClientProduceStream> members;
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final int partitionId;
        private final ConverterHandler convertKey;
        private final ConverterHandler convertValue;

        private long initialId;
        private long replyId;
        private MessageConsumer receiver;
        private KafkaCacheClientBudget budget;
        private KafkaCacheRoute cacheRoute;
        private String topicName;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private long lastAckOffsetHighWatermark;
        private long offsetHighWatermark = DEFAULT_LATEST_OFFSET;
        private int partitionOffset;
        private long compactAt = Long.MAX_VALUE;
        private long compactId = NO_CANCEL_ID;
        private long groupCleanupId = NO_CANCEL_ID;
        private long partitionIndex = NO_CREDITOR_INDEX;
        private long reconnectAt = NO_CANCEL_ID;

        private KafkaCacheClientProduceFan(
            long originId,
            long routedId,
            long authorization,
            KafkaCacheClientBudget budget,
            KafkaCachePartition partition,
            KafkaCacheRoute cacheRoute,
            String topicName,
            ConverterHandler convertKey,
            ConverterHandler convertValue)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.authorization = authorization;
            this.partition = partition;
            this.partitionId = partition.id();
            this.budget = budget;
            this.cacheRoute = cacheRoute;
            this.topicName = topicName;
            this.convertKey = convertKey;
            this.convertValue = convertValue;
            this.members = new Long2ObjectHashMap<>();
            this.defaultOffset = KafkaOffsetType.LIVE;
            this.cursor = cursorFactory.newCursor(
                    cursorFactory
                        .asCondition(EMPTY_FILTER, KafkaEvaluation.LAZY),
                        KafkaDeltaType.NONE);

            partition.newHeadIfNecessary(0L);

            KafkaCachePartition.Node segmentNode = partition.seekNotBefore(0);

            if (segmentNode.sentinel())
            {
                segmentNode = segmentNode.next();
            }
            cursor.init(segmentNode, 0, 0);
        }

        private void onClientFanMemberOpening(
            long traceId,
            KafkaCacheClientProduceStream member)
        {
            if (groupCleanupId != NO_CANCEL_ID)
            {
                signaler.cancel(groupCleanupId);

                groupCleanupId = NO_CANCEL_ID;
            }

            members.put(member.initialId, member);

            assert !members.isEmpty();

            doClientFanInitialBeginIfNecessary(traceId);

            if (KafkaState.initialOpened(state))
            {
                member.doClientInitialWindow(traceId, 0, initialMax);
            }

            if (KafkaState.replyOpened(state))
            {
                member.doClientReplyBeginIfNecessary(traceId);
            }
        }

        private void onClientFanMemberClosed(
            long traceId,
            KafkaCacheClientProduceStream member)
        {
            members.remove(member.initialId);

            member.markEntriesDirty(traceId);

            member.cursor.close();

            if (members.isEmpty())
            {
                if (cleanupDelay == 0)
                {
                    doClientFanInitialAbortIfNecessary(traceId);
                    doClientFanReplyResetIfNecessary(traceId);
                }
                else
                {
                    this.groupCleanupId = doClientFanoutInitialSignalAt(currentTimeMillis() + SECONDS.toMillis(cleanupDelay),
                            traceId, SIGNAL_GROUP_CLEANUP);
                }
            }
        }

        private void doClientFanInitialBeginIfNecessary(
            long traceId)
        {
            if (KafkaState.closed(state))
            {
                state = 0;
            }

            if (!KafkaState.initialOpening(state))
            {
                doClientFanInitialBegin(traceId);
            }
        }

        private void doClientFanInitialBegin(
            long traceId)
        {
            assert state == 0;

            final Int2IntHashMap leadersByPartitionId = cacheRoute.supplyLeadersByPartitionId(topicName);
            final int leaderId = leadersByPartitionId.get(partitionId);
            if (partitionIndex == NO_CREDITOR_INDEX)
            {
                this.partitionIndex = budget.acquire(partitionId);
            }
            assert partitionIndex != NO_CREDITOR_INDEX;

            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.receiver = newStream(this::onClientFanMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, leaderId,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .produce(p -> p.transaction(TRANSACTION_NONE)
                                       .topic(partition.topic())
                                       .partition(par -> par
                                           .partitionId(partitionId)
                                           .partitionOffset(DEFAULT_LATEST_OFFSET)))
                        .build()
                        .sizeof()));
            state = KafkaState.openingInitial(state);
        }

        private void onClientInitialData(
            KafkaCacheClientProduceStream stream,
            DataFW data)
        {
            final long traceId = data.traceId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW valueFragment = data.payload();

            int error = NO_ERROR;

            init:
            if ((flags & FLAGS_INIT) != 0x00)
            {
                final OctetsFW extension = data.extension();
                final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                assert dataEx != null && dataEx.typeId() == kafkaTypeId;
                final KafkaDataExFW kafkaDataEx = extension.get(kafkaDataExRO::wrap);
                assert kafkaDataEx.kind() == KafkaDataExFW.KIND_PRODUCE;
                KafkaProduceDataExFW kafkaProduceDataExFW = kafkaDataEx.produce();
                final int deferred = kafkaProduceDataExFW.deferred();
                final int sequence = kafkaProduceDataExFW.sequence();
                final Array32FW<KafkaHeaderFW> headers = kafkaProduceDataExFW.headers();
                final int headersSizeMax = headers.sizeof() + trailersSizeMax;
                final long timestamp = kafkaProduceDataExFW.timestamp();
                final KafkaAckMode ackMode = kafkaProduceDataExFW.ackMode().get();
                final KafkaKeyFW key = kafkaProduceDataExFW.key();
                final int valueLength = valueFragment != null ? valueFragment.sizeof() + deferred : -1;
                final int maxValueLength = valueLength + headersSizeMax;

                if ((flags & FLAGS_FIN) == 0x00 && deferred == 0)
                {
                    error = ERROR_CORRUPT_MESSAGE;
                    break init;
                }

                if (maxValueLength > partition.segmentBytes())
                {
                    error = ERROR_RECORD_LIST_TOO_LARGE;
                    break init;
                }

                stream.segment = partition.newHeadIfNecessary(partitionOffset, key, valueLength, headersSizeMax);

                if (stream.segment != null)
                {
                    final long nextOffset = partition.nextOffset(defaultOffset);
                    assert partitionOffset >= 0 && partitionOffset >= nextOffset
                        : String.format("%d >= 0 && %d >= %d", partitionOffset, partitionOffset, nextOffset);

                    final long keyHash = partition.computeKeyHash(key);
                    if (partition.writeProduceEntryStart(partitionOffset, stream.segment, stream.entryMark, stream.valueMark,
                        stream.valueLimit, timestamp, stream.initialId, sequence, ackMode, key, keyHash, valueLength,
                        headers, trailersSizeMax, valueFragment, convertKey, convertValue) == -1)
                    {
                        error = ERROR_INVALID_RECORD;
                        break init;
                    }
                    stream.partitionOffset = partitionOffset;
                    partitionOffset++;
                }
                else
                {
                    error = ERROR_RECORD_LIST_TOO_LARGE;
                }
            }

            if (valueFragment != null && error == NO_ERROR)
            {
                if (partition.writeProduceEntryContinue(flags, stream.segment,
                        stream.entryMark, stream.valueMark, stream.valueLimit,
                        valueFragment, convertValue) == -1)
                {
                    error = ERROR_INVALID_RECORD;
                }
            }

            if ((flags & FLAGS_FIN) != 0x00 && error == NO_ERROR)
            {
                Array32FW<KafkaHeaderFW> trailers = EMPTY_TRAILERS;

                if ((flags & FLAGS_INIT) == 0x00)
                {
                    final OctetsFW extension = data.extension();
                    final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);

                    if (dataEx != null && dataEx.typeId() == kafkaTypeId)
                    {
                        final KafkaDataExFW kafkaDataEx = extension.get(kafkaDataExRO::wrap);
                        assert kafkaDataEx.kind() == KafkaDataExFW.KIND_PRODUCE;
                        final KafkaProduceDataExFW kafkaProduceDataExFW = kafkaDataEx.produce();
                        trailers = kafkaProduceDataExFW.headers();
                    }
                }

                partition.writeProduceEntryFin(stream.segment, stream.entryMark, stream.valueLimit, stream.initialSeq, trailers);
                flushClientFanInitialIfNecessary(traceId);
            }

            if ((flags & FLAGS_INCOMPLETE) != 0x00)
            {
                markEntryDirty(traceId, stream.partitionOffset);
            }

            if (error != NO_ERROR)
            {
                stream.cleanupClient(traceId, error);
                onClientFanMemberClosed(traceId, stream);
            }

            creditor.credit(traceId, partitionIndex, reserved);
        }

        private void onClientInitialFlush(
            KafkaCacheClientProduceStream stream,
            FlushFW flush)
        {
            final long traceId = flush.traceId();
            final int reserved = flush.reserved();

            stream.segment = partition.newHeadIfNecessary(partitionOffset, EMPTY_KEY, 0, 0);

            int error = NO_ERROR;
            if (stream.segment != null)
            {
                final long nextOffset = partition.nextOffset(defaultOffset);
                assert partitionOffset >= 0 && partitionOffset >= nextOffset
                    : String.format("%d >= 0 && %d >= %d", partitionOffset, partitionOffset, nextOffset);

                final long keyHash = partition.computeKeyHash(EMPTY_KEY);
                partition.writeProduceEntryStart(partitionOffset, stream.segment, stream.entryMark, stream.valueMark,
                    stream.valueLimit, now().toEpochMilli(), stream.initialId, PRODUCE_FLUSH_SEQUENCE,
                    KafkaAckMode.LEADER_ONLY, EMPTY_KEY, keyHash, 0, EMPTY_TRAILERS,
                    trailersSizeMax, EMPTY_OCTETS, convertKey, convertValue);
                stream.partitionOffset = partitionOffset;
                partitionOffset++;

                Array32FW<KafkaHeaderFW> trailers = EMPTY_TRAILERS;

                partition.writeProduceEntryFin(stream.segment, stream.entryMark, stream.valueLimit, stream.initialSeq, trailers);
                flushClientFanInitialIfNecessary(traceId);
            }
            else
            {
                error = ERROR_RECORD_LIST_TOO_LARGE;
            }

            markEntryDirty(traceId, stream.partitionOffset);

            if (error != NO_ERROR)
            {
                stream.cleanupClient(traceId, error);
                onClientFanMemberClosed(traceId, stream);
            }
            creditor.credit(traceId, partitionIndex, reserved);
        }

        private void flushClientFanInitialIfNecessary(
            long traceId)
        {
            final long oldOffsetHighWaterMark = offsetHighWatermark;
            long newOffsetHighWatermark = cursor.offset;

            do
            {
                newOffsetHighWatermark++;

                final KafkaCacheEntryFW nextEntry = cursor.next(entryRO);

                if (nextEntry != null && (nextEntry.flags() & CACHE_ENTRY_FLAGS_ADVANCE) != 0)
                {
                    cursor.advance(newOffsetHighWatermark);
                }

            } while (newOffsetHighWatermark == cursor.offset);

            offsetHighWatermark = cursor.offset - 1;

            if (offsetHighWatermark > oldOffsetHighWaterMark)
            {
                doFlushClientInitialIfNecessary(traceId);
            }
        }

        private void doFlushClientInitialIfNecessary(
            long traceId)
        {
            if (initialMax - (initialSeq - initialAck) >= SIZE_OF_FLUSH_WITH_EXTENSION)
            {
                doFlush(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, 0L, SIZE_OF_FLUSH_WITH_EXTENSION,
                    ex -> ex.set((b, o, l) -> kafkaFlushExRW.wrap(b, o, l)
                                        .typeId(kafkaTypeId)
                                        .produce(f -> f.partition(p -> p.partitionId(partitionId)
                                                              .partitionOffset(offsetHighWatermark)))
                                        .build()
                                        .sizeof()));
            }
        }

        private void doClientFanInitialAbortIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doClientFanInitialAbort(traceId);
            }
        }

        private void doClientFanInitialAbort(
            long traceId)
        {
            doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

            onClientFanInitialClosed();
        }

        private void onClientFanInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final OctetsFW extension = reset.extension();

            state = KafkaState.closedInitial(state);

            final KafkaResetExFW kafkaResetEx = extension.get(kafkaResetExRO::tryWrap);
            final int error = kafkaResetEx != null ? kafkaResetEx.error() : UNKNOWN_ERROR;
            doClientFanReplyResetIfNecessary(traceId);

            if (reconnectDelay != 0 && !members.isEmpty() && error == ERROR_NOT_LEADER_FOR_PARTITION)
            {
                if (reconnectAt != NO_CANCEL_ID)
                {
                    signaler.cancel(reconnectAt);
                }

                this.reconnectAt = signaler.signalAt(
                        currentTimeMillis() + SECONDS.toMillis(reconnectDelay),
                        SIGNAL_RECONNECT,
                        this::onClientFanInitialSignalReconnect);
            }
            else
            {
                members.forEach((s, m) -> m.doClientInitialResetIfNecessary(traceId, extension));

                onClientFanInitialClosed();

                ackOffsetHighWatermark(error, traceId, offsetHighWatermark);
            }
        }

        private void onClientFanInitialWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final int padding = window.padding();

            this.initialPad = padding;

            if (!KafkaState.initialOpened(state))
            {
                onClientFanInitialOpened();

                this.initialMax = initialBudgetMax;
                budget.credit(traceId, partitionIndex, initialMax);

                members.forEach((s, m) -> m.doClientInitialWindow(traceId, 0, initialMax));
            }
            doFlushClientInitialIfNecessary(traceId);
        }

        private void onClientFanInitialSignal(
            SignalFW signal)
        {
            final int signalId = signal.signalId();

            switch (signalId)
            {
            case SIGNAL_SEGMENT_COMPACT:
                onClientFanInitialSignalSegmentCompact(signal);
                break;
            case SIGNAL_GROUP_CLEANUP:
                onClientFanInitialSignalCleanup(signal);
                break;
            }
        }

        private void onClientFanInitialSignalSegmentCompact(
            SignalFW signal)
        {
            final long now = currentTimeMillis();

            KafkaCachePartition.Node segmentNode = partition.sentinel().next();
            while (!segmentNode.next().sentinel()) // avoid cleaning head
            {
                segmentNode.clean(now);
                segmentNode = segmentNode.next();
            }

            this.compactAt = Long.MAX_VALUE;
            this.compactId = NO_CANCEL_ID;
        }

        private void onClientFanInitialSignalCleanup(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            if (members.isEmpty())
            {
                doClientFanInitialAbortIfNecessary(traceId);
                doClientFanReplyResetIfNecessary(traceId);
            }
        }

        private void onClientFanInitialSignalReconnect(
            long signalId)
        {
            assert signalId == SIGNAL_RECONNECT;

            this.reconnectAt = NO_CANCEL_ID;
            final long traceId = supplyTraceId.getAsLong();

            doClientFanInitialBeginIfNecessary(traceId);
        }

        private void onClientFanInitialOpened()
        {
            assert !KafkaState.initialOpened(state);
            state = KafkaState.openedInitial(state);
        }

        private void onClientFanInitialClosed()
        {
            state = KafkaState.closedInitial(state);

            if (partitionIndex != NO_CREDITOR_INDEX)
            {
                budget.release(partitionIndex, initialMax - (int)(initialSeq - initialAck));
                partitionIndex = NO_CREDITOR_INDEX;
            }

            initialSeq = 0L;
            initialAck = 0L;
            initialMax = 0;
            initialPad = 0;
        }

        private void onClientFanMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onClientFanReplyBegin(begin);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onClientFanReplyFlush(flush);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onClientFanReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onClientFanReplyAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onClientFanInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onClientFanInitialWindow(window);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onClientFanInitialSignal(signal);
                break;
            default:
                break;
            }
        }

        private void onClientFanReplyBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
            assert beginEx != null && beginEx.typeId() == kafkaTypeId;
            final KafkaBeginExFW kafkaBeginEx = extension.get(kafkaBeginExRO::wrap);
            assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_PRODUCE;
            final KafkaProduceBeginExFW kafkaProduceBeginEx = kafkaBeginEx.produce();
            final int partitionId = kafkaProduceBeginEx.partition().partitionId();

            state = KafkaState.openedReply(state);

            assert partitionId == this.partition.id();

            members.forEach((s, m) -> m.doClientReplyBeginIfNecessary(traceId));

            doClientFanReplyWindow(traceId);
        }

        private void onClientFanReplyFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();
            final OctetsFW extension = flush.extension();
            final KafkaFlushExFW kafkaFlushEx = extension.get(kafkaFlushExRO::wrap);
            final KafkaProduceFlushExFW kafkaProduceFlushEx = kafkaFlushEx.produce();
            final KafkaOffsetFW partition = kafkaProduceFlushEx.partition();
            final long partitionOffset = partition.partitionOffset();
            final int error = kafkaProduceFlushEx.error();

            final long currentLastAckOffsetHighWatermark = lastAckOffsetHighWatermark;
            ackOffsetHighWatermark(error, traceId, partitionOffset);

            if (lastAckOffsetHighWatermark > currentLastAckOffsetHighWatermark)
            {
                doClientFanReplyWindow(traceId);
            }
        }

        private void ackOffsetHighWatermark(
            int error,
            long traceId,
            long partitionOffset)
        {
            while (lastAckOffsetHighWatermark <= partitionOffset)
            {
                final KafkaCacheEntryFW entry = markEntryDirty(traceId, lastAckOffsetHighWatermark);
                final long memberStreamId = entry.ownerId();
                final KafkaCacheClientProduceStream member = members.get(memberStreamId);
                if (member != null)
                {
                    if (error != NO_ERROR)
                    {
                        member.onMessageError(error, traceId, partitionOffset);
                    }
                    else
                    {
                        member.onMessageAck(traceId, entry.offset$(), entry.acknowledge());
                    }
                }
                lastAckOffsetHighWatermark++;
            }
        }

        private KafkaCacheEntryFW markEntryDirty(
            long traceId,
            long partitionOffset)
        {
            final KafkaCachePartition.Node node = this.partition.seekNotAfter(partitionOffset);
            final KafkaCacheEntryFW dirtyEntry = node.findAndMarkDirty(entryRO, partitionOffset);

            final long newCompactAt = this.partition.compactAt(node.segment());

            if (newCompactAt != Long.MAX_VALUE)
            {
                if (compactId != NO_CANCEL_ID && newCompactAt < compactAt)
                {
                    signaler.cancel(compactId);
                    this.compactId = NO_CANCEL_ID;
                }

                if (compactId == NO_CANCEL_ID)
                {
                    this.compactAt = newCompactAt;
                    this.compactId = doClientFanoutInitialSignalAt(newCompactAt, traceId, SIGNAL_SEGMENT_COMPACT);
                }
            }

            return dirtyEntry;
        }

        private void onClientFanReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            doClientFanInitialAbortIfNecessary(traceId);

            members.forEach((s, m) -> m.doClientReplyEndIfNecessary(traceId));

            state = KafkaState.closedReply(state);
        }

        private void onClientFanReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            doClientFanInitialAbortIfNecessary(traceId);

            members.forEach((s, m) -> m.doClientReplyAbortIfNecessary(traceId));

            state = KafkaState.closedReply(state);
        }

        private void doClientFanReplyResetIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doClientFanReplyReset(traceId);
            }
        }

        private void doClientFanReplyReset(
            long traceId)
        {
            state = KafkaState.closedReply(state);

            doReset(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
        }

        private void doClientFanReplyWindow(
            long traceId)
        {
            state = KafkaState.openedReply(state);

            doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, 0L, 0);
        }

        private long doClientFanoutInitialSignalAt(
            long timeMillis,
            long traceId,
            int signalId)
        {
            return signaler.signalAt(timeMillis, originId, routedId, initialId, traceId, signalId, 0);
        }
    }

    private final class KafkaCacheClientProduceStream
    {
        private final KafkaCacheCursor cursor;
        private final MutableInteger entryMark;
        private final MutableInteger valueLimit;
        private final MutableInteger valueMark;
        private final KafkaCacheClientProduceFan fan;
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long leaderId;
        private final long authorization;

        private long partitionOffset = DEFAULT_LATEST_OFFSET;

        private KafkaCachePartition.Node segment;

        private int dataFlags = FLAGS_FIN;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        KafkaCacheClientProduceStream(
            KafkaCacheClientProduceFan fan,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long leaderId,
            long authorization)
        {
            this.cursor = cursorFactory.newCursor(
                    cursorFactory
                        .asCondition(EMPTY_FILTER, KafkaEvaluation.LAZY),
                        KafkaDeltaType.NONE);
            this.entryMark = new MutableInteger(0);
            this.valueMark = new MutableInteger(0);
            this.valueLimit = new MutableInteger(0);
            this.fan = fan;
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.leaderId = leaderId;
            this.authorization = authorization;
        }

        private void onClientMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onClientInitialBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onClientInitialData(data);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onClientInitialFlush(flush);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onClientInitialEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onClientInitialAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onClientReplyWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onClientReplyReset(reset);
                break;
            default:
                break;
            }
        }

        private void onClientInitialBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = KafkaState.openingInitial(state);

            KafkaCachePartition.Node segmentNode = fan.partition.seekNotBefore(fan.partitionOffset);

            if (segmentNode.sentinel())
            {
                segmentNode = segmentNode.next();
            }
            cursor.init(segmentNode, fan.partitionOffset - 1, 0);

            fan.onClientFanMemberOpening(traceId, this);
        }

        private void onClientInitialData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int reserved = data.reserved();

            dataFlags = data.flags();

            if (KafkaConfiguration.DEBUG_PRODUCE)
            {
                final int initialBudget = initialMax - (int)(initialSeq - initialAck);
                System.out.format("[%d] [%d] [%d] kafka cache client [%s] %d - %d => %d\n",
                        currentTimeMillis(), currentThread().getId(),
                        initialId, fan.partition, initialBudget, reserved, initialBudget - reserved);
            }

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + initialMax)
            {
                doClientInitialResetIfNecessary(traceId, EMPTY_OCTETS);
                doClientReplyAbortIfNecessary(traceId);
                fan.onClientFanMemberClosed(traceId, this);
            }
            else
            {
                fan.onClientInitialData(this, data);
            }

            // TODO: defer initialAck until previous DATA frames acked
            final boolean incomplete = (dataFlags & FLAGS_INCOMPLETE) != 0x00;
            final int noAck = incomplete ? 0 : (int) (initialSeq - initialAck);
            final int initialMax = incomplete ? initialBudgetMax : noAck + initialBudgetMax;
            doClientInitialWindow(traceId, noAck, initialMax);
        }

        private void onClientInitialFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            if (reserved > 0)
            {
                if (initialSeq > initialAck + initialMax)
                {
                    doClientInitialResetIfNecessary(traceId, EMPTY_OCTETS);
                    doClientReplyAbortIfNecessary(traceId);
                    fan.onClientFanMemberClosed(traceId, this);
                }
                else
                {
                    fan.onClientInitialFlush(this, flush);
                }
            }

            final int noAck = (int) (initialSeq - initialAck);
            doClientInitialWindow(traceId, noAck, initialBudgetMax);
        }

        private void onClientInitialEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedInitial(state);

            if (cursor.offset == partitionOffset)
            {
                fan.onClientFanMemberClosed(traceId, this);

                doClientReplyEndIfNecessary(traceId);
            }
        }

        private void onClientInitialAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedInitial(state);

            if (partitionOffset != DEFAULT_LATEST_OFFSET && dataFlags != FLAGS_FIN)
            {
                fan.markEntryDirty(traceId, partitionOffset);
            }

            fan.onClientFanMemberClosed(traceId, this);

            doClientReplyAbortIfNecessary(traceId);
        }

        private void doClientInitialWindow(
            long traceId,
            long minInitialNoAck,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(initialSeq - minInitialNoAck, initialAck);

            if (newInitialAck > initialAck || minInitialMax > initialMax || !KafkaState.initialOpened(state))
            {
                if (KafkaConfiguration.DEBUG_PRODUCE)
                {
                    final int initialBudget = initialMax - (int)(initialSeq - initialAck);
                    final int newInitialBudget = minInitialMax - (int)(initialSeq - newInitialAck);
                    final int credit = newInitialBudget - initialBudget;
                    System.out.format("[%d] [%d] [%d] kafka cache client [%s] %d + %d => %d\n",
                            currentTimeMillis(), currentThread().getId(),
                            initialId, fan.partition, initialBudget, credit, initialBudget + credit);
                }

                initialAck = newInitialAck;
                assert initialAck <= initialSeq;

                initialMax = minInitialMax;

                state = KafkaState.openedInitial(state);

                doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, 0L, fan.initialPad);
            }
        }

        private void doClientInitialResetIfNecessary(
            long traceId,
            Flyweight extension)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                doClientInitialReset(traceId, extension);
            }

            state = KafkaState.closedInitial(state);
        }

        private void doClientInitialReset(
            long traceId,
            Flyweight extension)
        {
            state = KafkaState.closedInitial(state);

            doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, extension);
        }

        private void doClientReplyBeginIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyOpening(state))
            {
                doClientReplyBegin(traceId);
            }
        }

        private void doClientReplyBegin(
            long traceId)
        {
            state = KafkaState.openingReply(state);

            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, leaderId,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .produce(p -> p.transaction(TRANSACTION_NONE)
                                       .topic(fan.partition.topic())
                                       .partition(par -> par
                                           .partitionId(fan.partition.id())
                                           .partitionOffset(fan.offsetHighWatermark)))
                        .build()
                        .sizeof()));
        }

        private void doClientReplyEnd(
            long traceId)
        {
            state = KafkaState.closedReply(state);
            doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
        }

        private void doClientReplyAbort(
            long traceId)
        {
            state = KafkaState.closedReply(state);
            doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
        }

        private void doClientReplyEndIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doClientReplyEnd(traceId);
            }

            state = KafkaState.closedReply(state);
        }

        private void doClientReplyAbortIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doClientReplyAbort(traceId);
            }

            state = KafkaState.closedReply(state);
        }

        private void onClientReplyWindow(
            WindowFW window)
        {
            state = KafkaState.openedReply(state);
        }

        private void onClientReplyReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedReply(state);

            if (cursor.offset == partitionOffset)
            {
                fan.onClientFanMemberClosed(traceId, this);

                doClientInitialResetIfNecessary(traceId, EMPTY_OCTETS);
            }
        }

        private void cleanupClient(
            long traceId,
            int error)
        {
            final KafkaResetExFW kafkaResetEx = kafkaResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                                                              .typeId(kafkaTypeId)
                                                              .error(error)
                                                              .build();

            cleanupClient(traceId, kafkaResetEx);
        }

        private void cleanupClient(
            long traceId,
            Flyweight extension)
        {
            doClientInitialResetIfNecessary(traceId, extension);
            doClientReplyAbortIfNecessary(traceId);
        }

        private void onMessageAck(
            long traceId,
            long partitionOffset,
            long acknowledge)
        {
            cursor.advance(partitionOffset);
            doClientInitialWindow(traceId, initialSeq - acknowledge,
                Math.max(initialMax - (int) (acknowledge - initialAck), initialBudgetMax));

            if (KafkaState.initialClosed(state) && partitionOffset == this.partitionOffset)
            {
                doClientReplyEndIfNecessary(traceId);
                fan.onClientFanMemberClosed(traceId, this);
            }
        }

        private void onMessageError(
                int error,
                long traceId,
                long partitionOffset)
        {
            cursor.advance(partitionOffset);

            final KafkaResetExFW kafkaResetEx = kafkaResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .error(error)
                    .build();

            doClientInitialReset(traceId, kafkaResetEx);
            fan.onClientFanMemberClosed(traceId, this);
        }

        private void markEntriesDirty(
            long traceId)
        {
            while (cursor.offset <= partitionOffset)
            {
                final KafkaCacheEntryFW nextEntry = cursor.next(entryRO);

                if (nextEntry != null && nextEntry.ownerId() == initialId)
                {
                    cursor.markEntryDirty(nextEntry);
                }
                cursor.advance(cursor.offset + 1);
            }
        }
    }
}
