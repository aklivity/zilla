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

import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorRecord.cursorNextValue;
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorRecord.cursorRetryValue;
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorRecord.cursorValue;
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition.CACHE_ENTRY_FLAGS_ABORTED;
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition.CACHE_ENTRY_FLAGS_CONTROL;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetFW.Builder.DEFAULT_LATEST_OFFSET;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetFW.Builder.DEFAULT_STABLE_OFFSET;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType.HISTORICAL;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType.LIVE;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCache;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheIndexFile;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition.Node;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheSegment;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheTopic;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaTopicConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.ArrayFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaIsolation;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaTransactionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaTransactionResult;
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
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFetchBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFetchDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFetchFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class KafkaCacheServerFetchFactory implements BindingHandler
{
    static final int SIZE_OF_FLUSH_WITH_EXTENSION = 64;

    private static final int ERROR_NOT_LEADER_FOR_PARTITION = 6;

    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer();
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};
    private static final ArrayFW<KafkaHeaderFW> EMPTY_HEADERS =
            new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                .wrap(new UnsafeBuffer(new byte[8]), 0, 8)
                .build();
    private static final KafkaKeyFW EMPTY_KEY =
            new OctetsFW().wrap(new UnsafeBuffer(ByteBuffer.wrap(new byte[] { 0x00 })), 0, 1)
                .get(new KafkaKeyFW()::wrap);

    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_SKIP = 0x08;

    private static final int SIGNAL_RECONNECT = 1;
    private static final int SIGNAL_SEGMENT_RETAIN = 2;
    private static final int SIGNAL_SEGMENT_DELETE = 3;
    private static final int SIGNAL_SEGMENT_COMPACT = 4;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final SignalFW signalRO = new SignalFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();
    private final KafkaFlushExFW kafkaFlushExRO = new KafkaFlushExFW();
    private final KafkaResetExFW kafkaResetExRO = new KafkaResetExFW();
    private final KafkaResetExFW.Builder kafkaResetExRW = new KafkaResetExFW.Builder();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaFlushExFW.Builder kafkaFlushExRW = new KafkaFlushExFW.Builder();

    private final KafkaCacheEntryFW ancestorEntryRO = new KafkaCacheEntryFW();
    private final KafkaCacheEntryFW abortedEntryRO = new KafkaCacheEntryFW();

    private final int kafkaTypeId;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BufferPool bufferPool;
    private final Signaler signaler;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final LongFunction<String> supplyNamespace;
    private final LongFunction<String> supplyLocalName;
    private final LongFunction<KafkaBindingConfig> supplyBinding;
    private final Function<String, KafkaCache> supplyCache;
    private final LongFunction<KafkaCacheRoute> supplyCacheRoute;
    private final int reconnectDelay;

    public KafkaCacheServerFetchFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding,
        Function<String, KafkaCache> supplyCache,
        LongFunction<KafkaCacheRoute> supplyCacheRoute)
    {
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.bufferPool = context.bufferPool();
        this.signaler = context.signaler();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.supplyNamespace = context::supplyNamespace;
        this.supplyLocalName = context::supplyLocalName;
        this.supplyBinding = supplyBinding;
        this.supplyCache = supplyCache;
        this.supplyCacheRoute = supplyCacheRoute;
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
        final ExtensionFW beginEx = extension.get(extensionRO::wrap);
        assert beginEx != null && beginEx.typeId() == kafkaTypeId;
        final KafkaBeginExFW kafkaBeginEx = extension.get(kafkaBeginExRO::wrap);
        final KafkaFetchBeginExFW kafkaFetchBeginEx = kafkaBeginEx.fetch();

        final String16FW beginTopic = kafkaFetchBeginEx.topic();
        final KafkaOffsetFW progress = kafkaFetchBeginEx.partition();
        final int partitionId = progress.partitionId();
        final long partitionOffset = progress.partitionOffset();
        final KafkaDeltaType deltaType = kafkaFetchBeginEx.deltaType().get();
        final String topicName = beginTopic.asString();

        MessageConsumer newStream = null;

        final KafkaBindingConfig binding = supplyBinding.apply(routedId);
        final KafkaRouteConfig resolved = binding != null ? binding.resolve(authorization, topicName) : null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;
            final KafkaCacheRoute cacheRoute = supplyCacheRoute.apply(resolvedId);
            final long partitionKey = cacheRoute.topicPartitionKey(topicName, partitionId);

            KafkaCacheServerFetchFanout fanout = cacheRoute.serverFetchFanoutsByTopicPartition.get(partitionKey);
            if (fanout == null)
            {
                final KafkaTopicConfig topic = binding.topic(topicName);
                final KafkaDeltaType routeDeltaType = topic != null ? topic.deltaType : deltaType;
                final KafkaOffsetType defaultOffset = topic != null ? topic.defaultOffset : HISTORICAL;
                final String cacheName = String.format("%s.%s", supplyNamespace.apply(routedId), supplyLocalName.apply(routedId));
                final KafkaCache cache = supplyCache.apply(cacheName);
                final KafkaCacheTopic cacheTopic = cache.supplyTopic(topicName);
                final KafkaCachePartition partition = cacheTopic.supplyFetchPartition(partitionId);
                final KafkaCacheServerFetchFanout newFanout =
                    new KafkaCacheServerFetchFanout(routedId, resolvedId, authorization,
                        affinity, partition, routeDeltaType, defaultOffset);

                cacheRoute.serverFetchFanoutsByTopicPartition.put(partitionKey, newFanout);
                fanout = newFanout;
            }

            final Int2IntHashMap leadersByPartitionId = cacheRoute.supplyLeadersByPartitionId(topicName);
            final int leaderId = leadersByPartitionId.get(partitionId);

            newStream = new KafkaCacheServerFetchStream(
                    fanout,
                    sender,
                    originId,
                    routedId,
                    initialId,
                    leaderId,
                    authorization,
                    partitionOffset)::onServerMessage;
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

    final class KafkaCacheServerFetchFanout
    {
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final KafkaCachePartition partition;
        private final KafkaDeltaType deltaType;
        private final KafkaOffsetType defaultOffset;
        private final long retentionMillisMax;
        private final List<KafkaCacheServerFetchStream> members;

        private long leaderId;
        private long initialId;
        private long replyId;
        private MessageConsumer receiver;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private long partitionOffset;
        private long latestOffset = DEFAULT_LATEST_OFFSET;
        private long stableOffset = DEFAULT_STABLE_OFFSET;
        private long retainId = NO_CANCEL_ID;
        private long deleteId = NO_CANCEL_ID;
        private long compactId = NO_CANCEL_ID;
        private long compactAt = Long.MAX_VALUE;
        private long reconnectAt = NO_CANCEL_ID;
        private int reconnectAttempt;

        private KafkaCacheServerFetchFanout(
            long originId,
            long routedId,
            long authorization,
            long leaderId,
            KafkaCachePartition partition,
            KafkaDeltaType deltaType,
            KafkaOffsetType defaultOffset)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.authorization = authorization;
            this.partition = partition;
            this.deltaType = deltaType;
            this.defaultOffset = defaultOffset;
            this.retentionMillisMax = defaultOffset == LIVE ? SECONDS.toMillis(30) : Long.MAX_VALUE;
            this.members = new ArrayList<>();
            this.leaderId = leaderId;
        }

        private void onServerFanoutMemberOpening(
            long traceId,
            KafkaCacheServerFetchStream member)
        {
            if (member.leaderId != leaderId)
            {
                doServerFanoutInitialAbortIfNecessary(traceId);
                doServerFanoutReplyResetIfNecessary(traceId);
                leaderId = member.leaderId;

                members.forEach(m -> m.cleanupServer(traceId, ERROR_NOT_LEADER_FOR_PARTITION));
                members.clear();
            }

            members.add(member);

            assert !members.isEmpty();

            doServerFanoutInitialBeginIfNecessary(traceId);

            if (KafkaState.initialOpened(state))
            {
                member.doServerInitialWindow(traceId, 0L, 0, 0, 0);
            }

            if (KafkaState.replyOpened(state))
            {
                member.doServerReplyBeginIfNecessary(traceId);
            }
        }

        private void onServerFanoutMemberClosed(
            long traceId,
            KafkaCacheServerFetchStream member)
        {
            members.remove(member);

            if (members.isEmpty())
            {
                if (reconnectAt != NO_CANCEL_ID)
                {
                    signaler.cancel(reconnectAt);
                    this.reconnectAt = NO_CANCEL_ID;
                }

                doServerFanoutInitialAbortIfNecessary(traceId);
                doServerFanoutReplyResetIfNecessary(traceId);
            }
        }

        private void doServerFanoutInitialBeginIfNecessary(
            long traceId)
        {
            if (KafkaState.closed(state))
            {
                state = 0;
            }

            if (!KafkaState.initialOpening(state))
            {
                doServerFanoutInitialBegin(traceId);
            }
        }

        private void doServerFanoutInitialBegin(
            long traceId)
        {
            assert state == 0;

            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);

            if (KafkaConfiguration.DEBUG)
            {
                System.out.format("[0x%016x] %s FETCH connect, affinity %d\n", initialId, partition, leaderId);
            }

            this.partitionOffset = partition.nextOffset(defaultOffset);

            this.receiver = newStream(this::onServerFanoutMessage,
                originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, leaderId,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .fetch(f -> f.topic(partition.topic())
                                     .partition(p -> p.partitionId(partition.id())
                                                      .partitionOffset(partitionOffset))
                                     .isolation(i -> i.set(KafkaIsolation.READ_UNCOMMITTED)))
                        .build()
                        .sizeof()));
            state = KafkaState.openingInitial(state);
        }

        private void doServerFanoutInitialEndIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doServerFanoutInitialEnd(traceId);
            }
        }

        private void doServerFanoutInitialEnd(
            long traceId)
        {
            doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

            state = KafkaState.closedInitial(state);
        }

        private void doServerFanoutInitialAbortIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doServerFanoutInitialAbort(traceId);
            }
        }

        private void doServerFanoutInitialAbort(
            long traceId)
        {
            doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

            state = KafkaState.closedInitial(state);
        }

        private void onServerFanoutMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onServerFanoutReplyBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onServerFanoutReplyData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onServerFanoutReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onServerFanoutReplyAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onServerFanoutReplyFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onServerFanoutInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onServerFanoutInitialWindow(window);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onServerFanoutInitialSignal(signal);
                break;
            default:
                break;
            }
        }

        private void onServerFanoutReplyBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
            assert beginEx != null && beginEx.typeId() == kafkaTypeId;
            final KafkaBeginExFW kafkaBeginEx = extension.get(kafkaBeginExRO::wrap);
            assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_FETCH;
            final KafkaFetchBeginExFW kafkaFetchBeginEx = kafkaBeginEx.fetch();
            final KafkaOffsetFW progress = kafkaFetchBeginEx.partition();
            final int partitionId = progress.partitionId();
            final long partitionOffset = progress.partitionOffset();

            state = KafkaState.openingReply(state);

            assert partitionId == partition.id();
            assert partitionOffset >= 0L && partitionOffset >= this.partitionOffset;
            this.partitionOffset = partitionOffset;
            this.stableOffset = progress.stableOffset();
            this.latestOffset = progress.latestOffset();

            partition.newHeadIfNecessary(partitionOffset);

            members.forEach(s -> s.doServerReplyBeginIfNecessary(traceId));

            doServerFanoutReplyWindow(traceId, 0, bufferPool.slotCapacity());
        }

        private void onServerFanoutReplyFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();

            final OctetsFW extension = flush.extension();
            final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
            assert beginEx != null && beginEx.typeId() == kafkaTypeId;
            final KafkaFlushExFW kafkaFlushEx = extension.get(kafkaFlushExRO::wrap);
            assert kafkaFlushEx.kind() == KafkaBeginExFW.KIND_FETCH;
            final KafkaFetchFlushExFW kafkaFetchFlushEx = kafkaFlushEx.fetch();
            final KafkaOffsetFW progress = kafkaFetchFlushEx.partition();
            final int partitionId = progress.partitionId();
            final long partitionOffset = progress.partitionOffset();
            final Array32FW<KafkaTransactionFW> transactions = kafkaFetchFlushEx.transactions();

            assert partitionId == partition.id();
            assert partitionOffset >= 0L && partitionOffset >= this.partitionOffset;

            if (!transactions.isEmpty())
            {
                final KafkaTransactionFW transaction = transactions.matchFirst(t -> true);
                final KafkaTransactionResult result = transaction.result().get();
                final long producerId = transaction.producerId();

                int entryFlags = CACHE_ENTRY_FLAGS_CONTROL;
                if (result == KafkaTransactionResult.ABORT)
                {
                    entryFlags |= CACHE_ENTRY_FLAGS_ABORTED;
                }

                partition.writeEntry(partitionOffset, 0L, producerId,
                        EMPTY_KEY, EMPTY_HEADERS, EMPTY_OCTETS, null,
                        entryFlags, KafkaDeltaType.NONE);

                if (result == KafkaTransactionResult.ABORT)
                {
                    Node stable = partition.seekNotBefore(stableOffset);
                    while (!stable.sentinel())
                    {
                        stable.findAndAbortProducerId(producerId, abortedEntryRO);
                        stable = stable.next();
                    }
                }
            }

            this.partitionOffset = partitionOffset;
            this.stableOffset = progress.stableOffset();
            this.latestOffset = progress.latestOffset();

            members.forEach(s -> s.doServerReplyFlushIfNecessary(traceId));

            doServerFanoutReplyWindow(traceId, 0, replyMax);
        }

        private void onServerFanoutReplyData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int reserved = data.reserved();
            final int flags = data.flags();
            final OctetsFW valueFragment = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;
            assert replySeq <= replyAck + replyMax;

            KafkaFetchDataExFW kafkaFetchDataEx = null;
            if ((flags & (FLAGS_INIT | FLAGS_FIN)) != 0x00)
            {
                final OctetsFW extension = data.extension();
                final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                assert dataEx != null && dataEx.typeId() == kafkaTypeId;
                final KafkaDataExFW kafkaDataEx = extension.get(kafkaDataExRO::wrap);
                assert kafkaDataEx.kind() == KafkaDataExFW.KIND_FETCH;
                kafkaFetchDataEx = kafkaDataEx.fetch();
            }

            if ((flags & FLAGS_INIT) != 0x00)
            {
                assert kafkaFetchDataEx != null;
                final int deferred = kafkaFetchDataEx.deferred();
                final int partitionId = kafkaFetchDataEx.partition().partitionId();
                final long partitionOffset = kafkaFetchDataEx.partition().partitionOffset();
                final int headersSizeMax = Math.max(kafkaFetchDataEx.headers().sizeof(), kafkaFetchDataEx.headersSizeMax());
                final long timestamp = kafkaFetchDataEx.timestamp();
                final long producerId = kafkaFetchDataEx.producerId();
                final KafkaKeyFW key = kafkaFetchDataEx.key();
                final KafkaDeltaFW delta = kafkaFetchDataEx.delta();
                final int valueLength = valueFragment != null ? valueFragment.sizeof() + deferred : -1;

                assert delta.type().get() == KafkaDeltaType.NONE;
                assert delta.ancestorOffset() == -1L;
                assert partitionId == partition.id();
                assert partitionOffset >= this.partitionOffset : String.format("%d >= %d", partitionOffset, this.partitionOffset);

                final KafkaCachePartition.Node head = partition.head();
                final KafkaCachePartition.Node nextHead =
                        partition.newHeadIfNecessary(partitionOffset, key, valueLength, headersSizeMax);

                final long nextOffset = partition.nextOffset(defaultOffset);
                assert partitionOffset >= 0 && partitionOffset >= nextOffset
                        : String.format("%d >= 0 && %d >= %d", partitionOffset, partitionOffset, nextOffset);

                if (nextHead != head)
                {
                    if (retainId != NO_CANCEL_ID)
                    {
                        signaler.cancel(retainId);
                        this.retainId = NO_CANCEL_ID;
                    }

                    assert retainId == NO_CANCEL_ID;

                    final long retainAt = partition.retainAt(nextHead.segment());
                    this.retainId = doServerFanoutInitialSignalAt(retainAt, SIGNAL_SEGMENT_RETAIN);

                    if (deleteId == NO_CANCEL_ID &&
                        partition.cleanupPolicy().delete() &&
                        !nextHead.previous().sentinel())
                    {
                        final long deleteAt = partition.deleteAt(nextHead.previous().segment(), retentionMillisMax);
                        this.deleteId = doServerFanoutInitialSignalAt(deleteAt, SIGNAL_SEGMENT_DELETE);
                    }
                }

                final int entryFlags = (flags & FLAGS_SKIP) != 0x00 ? CACHE_ENTRY_FLAGS_ABORTED : 0x00;
                final long keyHash = partition.computeKeyHash(key);
                final KafkaCacheEntryFW ancestor = findAndMarkAncestor(key, nextHead, (int) keyHash, partitionOffset);
                partition.writeEntryStart(partitionOffset, timestamp, producerId,
                        key, keyHash, valueLength, ancestor, entryFlags, deltaType);
            }

            if (valueFragment != null)
            {
                partition.writeEntryContinue(valueFragment);
            }

            if ((flags & FLAGS_FIN) != 0x00)
            {
                assert kafkaFetchDataEx != null;
                final KafkaOffsetFW progress = kafkaFetchDataEx.partition();
                final int partitionId = progress.partitionId();
                final long partitionOffset = progress.partitionOffset();
                final long stableOffset = progress.stableOffset();
                final long latestOffset = progress.latestOffset();
                final KafkaDeltaFW delta = kafkaFetchDataEx.delta();
                final ArrayFW<KafkaHeaderFW> headers = kafkaFetchDataEx.headers();

                assert delta.type().get() == KafkaDeltaType.NONE;
                assert delta.ancestorOffset() == -1L;
                assert partitionId == partition.id();
                assert partitionOffset >= this.partitionOffset;

                partition.writeEntryFinish(headers, deltaType);

                this.partitionOffset = partitionOffset;
                this.stableOffset = stableOffset;
                this.latestOffset = latestOffset;

                members.forEach(s -> s.doServerReplyFlushIfNecessary(traceId));
            }

            doServerFanoutReplyWindow(traceId, 0, replyMax);
        }

        private KafkaCacheEntryFW findAndMarkAncestor(
            KafkaKeyFW key,
            KafkaCachePartition.Node head,
            int keyHash,
            long descendantOffset)
        {
            KafkaCacheEntryFW ancestorEntry = null;
            ancestor:
            if (key.length() != -1)
            {
                ancestorEntry = head.findAndMarkAncestor(key, keyHash, descendantOffset, ancestorEntryRO);
                if (ancestorEntry != null)
                {
                    if (partition.cleanupPolicy().compact())
                    {
                        final long newCompactAt = partition.compactAt(head.segment());
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
                                this.compactId = doServerFanoutInitialSignalAt(newCompactAt, SIGNAL_SEGMENT_COMPACT);
                            }
                        }
                    }
                    break ancestor;
                }

                Node previousNode = head.previous();
                while (!previousNode.sentinel())
                {
                    final KafkaCacheSegment previousSegment = previousNode.segment();
                    final KafkaCacheIndexFile previousKeys = previousSegment.keysFile();

                    long keyCursor = previousKeys.last(keyHash);
                    while (!cursorNextValue(keyCursor) && !cursorRetryValue(keyCursor))
                    {
                        final int keyBaseOffsetDelta = cursorValue(keyCursor);
                        assert keyBaseOffsetDelta <= 0;
                        final long keyBaseOffset = previousSegment.baseOffset() + keyBaseOffsetDelta;
                        final Node ancestorNode = previousNode.seekAncestor(keyBaseOffset);
                        if (!ancestorNode.sentinel())
                        {
                            final KafkaCacheSegment segment = ancestorNode.segment();
                            final long ancestorBase = segment.baseOffset();
                            assert ancestorBase == keyBaseOffset : String.format("%d == %d", ancestorBase, keyBaseOffset);
                            ancestorEntry = ancestorNode.findAndMarkAncestor(key, keyHash, descendantOffset, ancestorEntryRO);
                            if (ancestorEntry != null)
                            {
                                if (partition.cleanupPolicy().compact())
                                {
                                    final long newCompactAt = partition.compactAt(segment);
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
                                            this.compactId = doServerFanoutInitialSignalAt(newCompactAt, SIGNAL_SEGMENT_COMPACT);
                                        }
                                    }
                                }
                                break ancestor;
                            }
                        }

                        final long nextKeyCursor = previousKeys.lower(keyHash, keyCursor);
                        if (cursorNextValue(nextKeyCursor) || cursorRetryValue(nextKeyCursor))
                        {
                            break;
                        }

                        keyCursor = nextKeyCursor;
                    }

                    previousNode = previousNode.previous();
                }
            }
            return ancestorEntry;
        }

        private void onServerFanoutReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedReply(state);

            doServerFanoutInitialEndIfNecessary(traceId);

            if (reconnectDelay != 0 && !members.isEmpty())
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[0x%016x] %s FETCH reconnect in %ds\n", initialId, partition, reconnectDelay);
                }

                if (reconnectAt != NO_CANCEL_ID)
                {
                    signaler.cancel(reconnectAt);
                }

                this.reconnectAt = signaler.signalAt(
                    currentTimeMillis() + Math.min(50 << reconnectAttempt++, SECONDS.toMillis(reconnectDelay)),
                    SIGNAL_RECONNECT,
                    this::onServerFanoutSignal);
            }
            else
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[0x%016x] %s FETCH disconnect\n", initialId, partition);
                }

                members.forEach(s -> s.doServerReplyEndIfNecessary(traceId));
            }
        }

        private void onServerFanoutReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedReply(state);

            doServerFanoutInitialAbortIfNecessary(traceId);

            if (reconnectDelay != 0 && !members.isEmpty())
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[0x%016x] %s FETCH reconnect in %ds\n", initialId, partition, reconnectDelay);
                }

                if (reconnectAt != NO_CANCEL_ID)
                {
                    signaler.cancel(reconnectAt);
                }

                this.reconnectAt = signaler.signalAt(
                    currentTimeMillis() + Math.min(50 << reconnectAttempt++, SECONDS.toMillis(reconnectDelay)),
                    SIGNAL_RECONNECT,
                    this::onServerFanoutSignal);
            }
            else
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[0x%016x] %s FETCH disconnect\n", initialId, partition);
                }

                members.forEach(s -> s.doServerReplyAbortIfNecessary(traceId));
            }
        }

        private void onServerFanoutInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final OctetsFW extension = reset.extension();

            state = KafkaState.closedInitial(state);

            doServerFanoutReplyResetIfNecessary(traceId);

            final KafkaResetExFW kafkaResetEx = extension.get(kafkaResetExRO::tryWrap);
            final int error = kafkaResetEx != null ? kafkaResetEx.error() : -1;

            if (reconnectDelay != 0 && !members.isEmpty() &&
                error != ERROR_NOT_LEADER_FOR_PARTITION)
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[0x%016x] %s FETCH reconnect in %ds, error %d \n", initialId, partition, reconnectDelay,
                        error);
                }

                if (reconnectAt != NO_CANCEL_ID)
                {
                    signaler.cancel(reconnectAt);
                }

                this.reconnectAt = signaler.signalAt(
                    currentTimeMillis() + Math.min(50 << reconnectAttempt++, SECONDS.toMillis(reconnectDelay)),
                    SIGNAL_RECONNECT,
                    this::onServerFanoutSignal);
            }
            else
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("[0x%016x] %s FETCH disconnect, error %d\n", initialId, partition, error);
                }

                members.forEach(s -> s.doServerInitialResetIfNecessary(traceId, extension));
            }
        }

        private void onServerFanoutInitialWindow(
            WindowFW window)
        {
            if (!KafkaState.initialOpened(state))
            {
                this.reconnectAttempt = 0;

                final long traceId = window.traceId();

                state = KafkaState.openedInitial(state);

                members.forEach(s -> s.doServerInitialWindow(traceId, 0L, 0, 0, 0));
            }
        }

        private void onServerFanoutSignal(
            int signalId)
        {
            assert signalId == SIGNAL_RECONNECT;

            this.reconnectAt = NO_CANCEL_ID;

            final long traceId = supplyTraceId.getAsLong();

            doServerFanoutInitialBeginIfNecessary(traceId);
        }

        private void onServerFanoutInitialSignal(
            SignalFW signal)
        {
            final int signalId = signal.signalId();

            switch (signalId)
            {
            case SIGNAL_SEGMENT_RETAIN:
                onServerFanoutInitialSignalSegmentRetain(signal);
                break;
            case SIGNAL_SEGMENT_DELETE:
                onServerFanoutInitialSignalSegmentDelete(signal);
                break;
            case SIGNAL_SEGMENT_COMPACT:
                onServerFanoutInitialSignalSegmentCompact(signal);
                break;
            }
        }

        private void onServerFanoutInitialSignalSegmentRetain(
            SignalFW signal)
        {
            partition.append(partitionOffset + 1);
        }

        private void onServerFanoutInitialSignalSegmentDelete(
            SignalFW signal)
        {
            final long now = currentTimeMillis();

            Node segmentNode = partition.sentinel().next();
            while (segmentNode != partition.head() &&
                    partition.deleteAt(segmentNode.segment(), retentionMillisMax) <= now)
            {
                segmentNode.remove();
                segmentNode = segmentNode.next();
            }
            assert segmentNode != null;

            if (segmentNode != partition.head())
            {
                final long deleteAt = partition.deleteAt(segmentNode.segment(), retentionMillisMax);
                this.deleteId = doServerFanoutInitialSignalAt(deleteAt, SIGNAL_SEGMENT_DELETE);
            }
            else
            {
                this.deleteId = NO_CANCEL_ID;
            }
        }

        private void onServerFanoutInitialSignalSegmentCompact(
            SignalFW signal)
        {
            final long now = currentTimeMillis();

            Node segmentNode = partition.sentinel().next();
            while (!segmentNode.next().sentinel()) // avoid cleaning head
            {
                segmentNode.clean(now);
                segmentNode = segmentNode.next();
            }

            this.compactAt = Long.MAX_VALUE;
            this.compactId = NO_CANCEL_ID;
        }

        private void doServerFanoutReplyResetIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doServerFanoutReplyReset(traceId);
            }
        }

        private void doServerFanoutReplyReset(
            long traceId)
        {
            state = KafkaState.closedReply(state);

            doReset(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
        }

        private void doServerFanoutReplyWindow(
            long traceId,
            int minReplyNoAck,
            int minReplyMax)
        {
            final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

            if (newReplyAck > replyAck || minReplyMax > replyMax || !KafkaState.replyOpened(state))
            {
                replyAck = newReplyAck;
                assert replyAck <= replySeq;

                replyMax = minReplyMax;

                state = KafkaState.openedReply(state);

                doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, 0L, 0);
            }
        }

        private long doServerFanoutInitialSignalAt(
            long timeMillis,
            int signalId)
        {
            long timerId = NO_CANCEL_ID;

            if (timeMillis <= System.currentTimeMillis())
            {
                signaler.signalNow(originId, routedId, initialId, signalId, 0);
            }
            else
            {
                timerId = signaler.signalAt(timeMillis, originId, routedId, initialId, signalId, 0);
            }

            return timerId;
        }
    }

    private final class KafkaCacheServerFetchStream
    {
        private final KafkaCacheServerFetchFanout group;
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long leaderId;
        private final long authorization;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private long partitionOffset;
        private long stableOffset;
        private long latestOffset;

        KafkaCacheServerFetchStream(
            KafkaCacheServerFetchFanout group,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long leaderId,
            long authorization,
            long partitionOffset)
        {
            this.group = group;
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.leaderId = leaderId;
            this.authorization = authorization;
            this.partitionOffset = partitionOffset;
        }

        private void onServerMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onServerInitialBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onServerInitialData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onServerInitialEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onServerInitialAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onServerReplyWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onServerReplyReset(reset);
                break;
            default:
                break;
            }
        }

        private void onServerInitialBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long affinity = begin.affinity();

            if (affinity != leaderId)
            {
                cleanupServer(traceId, ERROR_NOT_LEADER_FOR_PARTITION);
            }
            else
            {
                state = KafkaState.openingInitial(state);
                group.onServerFanoutMemberOpening(traceId, this);
            }
        }

        private void onServerInitialData(
            DataFW data)
        {
            final long traceId = data.traceId();

            doServerInitialResetIfNecessary(traceId, EMPTY_OCTETS);
            doServerReplyAbortIfNecessary(traceId);

            group.onServerFanoutMemberClosed(traceId, this);
        }

        private void onServerInitialEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedInitial(state);

            group.onServerFanoutMemberClosed(traceId, this);

            doServerReplyEndIfNecessary(traceId);
        }

        private void onServerInitialAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedInitial(state);

            group.onServerFanoutMemberClosed(traceId, this);

            doServerReplyAbortIfNecessary(traceId);
        }

        private void doServerInitialResetIfNecessary(
            long traceId,
            Flyweight extension)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                doServerInitialReset(traceId, extension);
            }

            state = KafkaState.closedInitial(state);
        }

        private void doServerInitialReset(
            long traceId,
            Flyweight extension)
        {
            state = KafkaState.closedInitial(state);

            doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, extension);
        }

        private void doServerInitialWindow(
            long traceId,
            long budgetId,
            int minInitialWin,
            int minInitialPad,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(initialSeq - minInitialWin, initialAck);

            if (newInitialAck > initialAck || minInitialMax > initialMax || !KafkaState.initialOpened(state))
            {
                initialAck = newInitialAck;
                assert initialAck <= initialSeq;

                initialMax = minInitialMax;

                state = KafkaState.openedInitial(state);

                doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, budgetId, minInitialPad);
            }
        }

        private void doServerReplyBeginIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyOpening(state))
            {
                doServerReplyBegin(traceId);
            }
        }

        private void doServerReplyBegin(
            long traceId)
        {
            state = KafkaState.openingReply(state);

            this.partitionOffset = Math.max(partitionOffset, group.partitionOffset);

            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, leaderId,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .fetch(f -> f.topic(group.partition.topic())
                                     .partition(p -> p.partitionId(group.partition.id())
                                                      .partitionOffset(partitionOffset)
                                                      .stableOffset(group.stableOffset)
                                                      .latestOffset(group.latestOffset)))
                        .build()
                        .sizeof()));
        }

        private void doServerReplyFlushIfNecessary(
            long traceId)
        {
            if ((partitionOffset <= group.partitionOffset ||
                 latestOffset <= group.latestOffset ||
                 stableOffset <= group.stableOffset) &&
                replyMax - (int)(replySeq - replyAck) >= SIZE_OF_FLUSH_WITH_EXTENSION)
            {
                doServerReplyFlush(traceId, SIZE_OF_FLUSH_WITH_EXTENSION);
            }
        }

        private void doServerReplyFlush(
            long traceId,
            int reserved)
        {
            assert partitionOffset <= group.partitionOffset || latestOffset <= group.latestOffset;

            doFlush(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, 0L, reserved,
                ex -> ex.set((b, o, l) -> kafkaFlushExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .fetch(f -> f.partition(p -> p.partitionId(group.partition.id())
                                                      .partitionOffset(group.partitionOffset)
                                                      .stableOffset(group.stableOffset)
                                                      .latestOffset(group.latestOffset)))
                        .build()
                        .sizeof()));

            replySeq += reserved;

            assert replyAck <= replySeq;

            this.partitionOffset = group.partitionOffset + 1;
            this.stableOffset = group.stableOffset + 1;
            this.latestOffset = group.latestOffset + 1;
        }

        private void doServerReplyEndIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doServerReplyEnd(traceId);
            }

            state = KafkaState.closedReply(state);
        }

        private void doServerReplyEnd(
                long traceId)
        {
            state = KafkaState.closedReply(state);
            doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
        }

        private void doServerReplyAbortIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doServerReplyAbort(traceId);
            }

            state = KafkaState.closedReply(state);
        }

        private void doServerReplyAbort(
                long traceId)
        {
            state = KafkaState.closedReply(state);
            doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
        }

        private void onServerReplyWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert budgetId == 0L;
            assert padding == 0;

            state = KafkaState.openedReply(state);

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            this.replyAck = acknowledge;
            this.replyMax = maximum;

            assert replyAck <= replySeq;

            doServerReplyFlushIfNecessary(traceId);
        }

        private void onServerReplyReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedReply(state);

            group.onServerFanoutMemberClosed(traceId, this);

            doServerInitialResetIfNecessary(traceId, EMPTY_OCTETS);
        }

        private void cleanupServer(
            long traceId,
            int error)
        {
            final KafkaResetExFW kafkaResetEx = kafkaResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                                                              .typeId(kafkaTypeId)
                                                              .error(error)
                                                              .build();

            cleanupServer(traceId, kafkaResetEx);
        }

        private void cleanupServer(
            long traceId,
            Flyweight extension)
        {
            doServerInitialReset(traceId, extension);
            doServerReplyAbortIfNecessary(traceId);
        }
    }
}
