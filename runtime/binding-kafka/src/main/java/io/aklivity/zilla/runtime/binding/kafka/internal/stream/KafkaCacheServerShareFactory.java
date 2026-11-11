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

import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_BUDGET_ID;
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static io.aklivity.zilla.runtime.engine.util.Flags.COMPLETE;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static io.aklivity.zilla.runtime.engine.util.Flags.NONE;
import static io.aklivity.zilla.runtime.engine.util.Flags.hasFin;
import static io.aklivity.zilla.runtime.engine.util.Flags.hasInit;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.collections.LongArrayList;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCache;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheTopic;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheTopicConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.ArrayFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
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
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaShareAckType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaShareAcquiredFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaShareAcquiredRangeFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaShareBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaShareDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaShareFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.StreamState;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class KafkaCacheServerShareFactory implements BindingHandler
{
    private static final int ERROR_UNKNOWN_SERVER_ERROR = -1;
    private static final int ERROR_MESSAGE_TOO_LARGE = 10;
    private static final int ERROR_INVALID_REQUEST = 42;
    private static final int ERROR_INVALID_RECORD_STATE = 121;
    private static final long NO_CORRELATION_ID = -1L;
    private static final int SIGNAL_LOCK_EXPIRY = 1;
    private static final int DEBITOR_CLAIM_MINIMUM = 1024;
    private static final int PERCENT_MAX = 100;
    private static final int MEMBER_ID_BYTES = 16;

    private static final DirectBufferEx EMPTY_BUFFER = new UnsafeBufferEx();
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};
    private static final Array32FW<KafkaHeaderFW> EMPTY_HEADERS =
        new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
            .wrap(new UnsafeBufferEx(new byte[8]), 0, 8)
            .build();

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final FlushFW flushRO = new FlushFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();
    private final KafkaFlushExFW kafkaFlushExRO = new KafkaFlushExFW();
    private final KafkaShareAcquiredRangeFW acquiredRangeRO = new KafkaShareAcquiredRangeFW();
    private final OctetsFW payloadRO = new OctetsFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final KafkaFlushExFW.Builder kafkaFlushExRW = new KafkaFlushExFW.Builder();
    private final KafkaResetExFW.Builder kafkaResetExRW = new KafkaResetExFW.Builder();

    private final EngineContext context;
    private final int kafkaTypeId;
    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final BufferPool bufferPool;
    private final Signaler signaler;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final LongFunction<BudgetDebitor> supplyDebitor;
    private final LongFunction<String> supplyNamespace;
    private final LongFunction<String> supplyLocalName;
    private final LongFunction<KafkaBindingConfig> supplyBinding;
    private final Function<String, KafkaCache> supplyCache;
    private final KafkaConfiguration config;
    private final int stagingMaxBytes;
    private final int lockMarginPercent;
    private final Object2ObjectHashMap<String, KafkaCacheServerShareFanout> fanoutsByKey;

    public KafkaCacheServerShareFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding,
        Function<String, KafkaCache> supplyCache)
    {
        this.config = config;
        this.context = context;
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.writeBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.bufferPool = context.bufferPool();
        this.signaler = context.signaler();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.supplyDebitor = context::supplyDebitor;
        this.supplyNamespace = context::supplyNamespace;
        this.supplyLocalName = context::supplyLocalName;
        this.supplyBinding = supplyBinding;
        this.supplyCache = supplyCache;
        this.stagingMaxBytes = config.cacheServerShareStagingMaxBytes();
        this.lockMarginPercent = config.cacheServerShareLockMarginPercent();
        this.fanoutsByKey = new Object2ObjectHashMap<>();
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final long affinity = begin.affinity();

        assert (initialId & 0x0000_0000_0000_0001L) != 0L;

        final OctetsFW extension = begin.extension();
        final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
        assert beginEx != null && beginEx.typeId() == kafkaTypeId;
        final KafkaBeginExFW kafkaBeginEx = extension.get(kafkaBeginExRO::tryWrap);
        assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_SHARE;
        final KafkaShareBeginExFW kafkaShareBeginEx = kafkaBeginEx.share();
        final String groupId = kafkaShareBeginEx.groupId().asString();
        final String topic = kafkaShareBeginEx.topic().asString();

        MessageConsumer newStream = null;

        final KafkaBindingConfig binding = supplyBinding.apply(routedId);
        final KafkaRouteConfig resolved = binding != null ? binding.resolve(authorization, topic) : null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;
            final String key = String.format("%d/%s", routedId, groupId);

            KafkaCacheServerShareFanout fanout = fanoutsByKey.get(key);
            if (fanout == null)
            {
                fanout = newFanout(key, routedId, resolvedId, authorization, groupId, topic, kafkaShareBeginEx.filters());
                fanoutsByKey.put(key, fanout);
            }

            newStream = new KafkaCacheServerShareStream(
                fanout,
                sender,
                originId,
                routedId,
                initialId,
                affinity,
                authorization)::onShareMessage;
        }

        return newStream;
    }

    private KafkaCacheServerShareFanout newFanout(
        String key,
        long routedId,
        long resolvedId,
        long authorization,
        String groupId,
        String topic,
        Array32FW<KafkaFilterFW> filters)
    {
        final String cacheName = String.format("%s.%s", supplyNamespace.apply(routedId), supplyLocalName.apply(routedId));
        final KafkaCacheTopic cacheTopic = supplyCache.apply(cacheName).supplyTopic(topic);

        final KafkaCacheTopicConfig stagingConfig = new KafkaCacheTopicConfig(config);
        stagingConfig.segmentBytes = config.cacheServerShareStagingSegmentBytes();

        final String staging = String.format("share-%d-%s-%s", context.index(),
            URLEncoder.encode(groupId, UTF_8), URLEncoder.encode(topic, UTF_8));

        final KafkaShareStaging stagingLog =
            new KafkaShareStaging(context, cacheTopic.newShareStaging(stagingConfig, staging), routedId, writeBuffer.capacity());

        return new KafkaCacheServerShareFanout(key, routedId, resolvedId, authorization, groupId, topic, filters, stagingLog);
    }

    private MessageConsumer newStream(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int state,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .state(state)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        final MessageConsumer receiver =
            streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    final class KafkaCacheServerShareFanout
    {
        private final String key;
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final String groupId;
        private final String topic;
        private final String memberId;
        private final KafkaRecordFilter recordFilter;
        private final KafkaShareStaging staging;
        private final KafkaShareRecords records;
        private final List<KafkaCacheServerShareStream> streams;
        private final List<KafkaShareRecord> collected;
        private final LongArrayList confirmCorrelations;
        private final LongArrayList confirmStreamIds;
        private final LongArrayList confirmStreamCorrelations;

        private long initialId;
        private long replyId;
        private MessageConsumer receiver;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long initialBud;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private String host;
        private int port;

        private long sequence;
        private long correlation;
        private int rotation;

        private int stagingPartitionId;
        private long stagingPartitionOffset;
        private long stagingTimestamp;

        private long expireId = NO_CANCEL_ID;
        private long expireAt = Long.MAX_VALUE;

        private KafkaCacheServerShareFanout(
            String key,
            long originId,
            long routedId,
            long authorization,
            String groupId,
            String topic,
            Array32FW<KafkaFilterFW> filters,
            KafkaShareStaging staging)
        {
            this.key = key;
            this.originId = originId;
            this.routedId = routedId;
            this.authorization = authorization;
            this.groupId = groupId;
            this.topic = topic;
            this.memberId = newMemberId();
            this.recordFilter = new KafkaRecordFilter(filters);
            this.staging = staging;
            this.records = new KafkaShareRecords();
            this.streams = new ArrayList<>();
            this.collected = new ArrayList<>();
            this.confirmCorrelations = new LongArrayList();
            this.confirmStreamIds = new LongArrayList();
            this.confirmStreamCorrelations = new LongArrayList();
        }

        private void onShareFanoutStreamOpening(
            long traceId,
            KafkaCacheServerShareStream stream,
            String streamTopic,
            Array32FW<KafkaFilterFW> streamFilters)
        {
            if (!topic.equals(streamTopic) || !recordFilter.sameAs(streamFilters))
            {
                stream.doShareInitialWindow(traceId);
                stream.doShareInitialReset(traceId, newResetEx(ERROR_INVALID_REQUEST));
            }
            else
            {
                streams.add(stream);

                stream.doShareInitialWindow(traceId);

                if (!StreamState.initialOpening(state))
                {
                    doShareFanoutInitialBegin(traceId);
                }
                else if (StreamState.replyOpening(state))
                {
                    stream.doShareReplyBegin(traceId);
                }
            }
        }

        private void onShareFanoutStreamClosed(
            long traceId,
            KafkaCacheServerShareStream stream)
        {
            if (streams.remove(stream))
            {
                records.reroute(stream.initialId);
                stream.cleanupDelivery();

                for (int index = confirmStreamIds.size() - 1; index >= 0; index--)
                {
                    if (confirmStreamIds.getLong(index) == stream.initialId)
                    {
                        confirmCorrelations.removeAt(index);
                        confirmStreamIds.removeAt(index);
                        confirmStreamCorrelations.removeAt(index);
                    }
                }

                if (streams.isEmpty())
                {
                    doShareFanoutLeave(traceId);
                }
                else
                {
                    rotation %= streams.size();
                    doShareFanoutDrain(traceId);
                }
            }
        }

        private void onShareFanoutAcknowledge(
            long traceId,
            KafkaCacheServerShareStream stream,
            int partitionId,
            long partitionOffset,
            KafkaShareAckType type,
            long streamCorrelation)
        {
            final KafkaShareRecord record = records.find(partitionId, partitionOffset);

            if (record != null &&
                record.state == KafkaShareRecord.STATE_DELIVERED &&
                record.ownerId == stream.initialId)
            {
                long memberCorrelation = NO_CORRELATION_ID;

                if (streamCorrelation != NO_CORRELATION_ID)
                {
                    memberCorrelation = ++correlation;
                    confirmCorrelations.addLong(memberCorrelation);
                    confirmStreamIds.addLong(stream.initialId);
                    confirmStreamCorrelations.addLong(streamCorrelation);
                }

                doShareFanoutRecordCompleted(traceId, record);
                doShareFanoutInitialAck(traceId, partitionId, partitionOffset, type, memberCorrelation);
            }
            else if (streamCorrelation != NO_CORRELATION_ID)
            {
                stream.doShareReplyConfirm(traceId, partitionId, partitionOffset, type, streamCorrelation,
                    ERROR_INVALID_RECORD_STATE);
            }
        }

        private void onShareFanoutDelivered(
            long traceId,
            KafkaShareRecord record)
        {
            records.delivered(record);
            scheduleExpiry();
        }

        private void doShareFanoutRecordCompleted(
            long traceId,
            KafkaShareRecord record)
        {
            staging.release(record);
            records.remove(record);

            doShareFanoutReplyWindow(traceId);
        }

        private void doShareFanoutInitialBegin(
            long traceId)
        {
            assert state == 0;

            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);

            state = StreamState.openingInitial(state);

            final KafkaBeginExFW beginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .share(s -> s.groupId(groupId)
                             .memberId(memberId)
                             .topic(topic))
                .build();

            this.receiver = newStream(this::onShareFanoutMessage,
                originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                traceId, authorization, 0L, beginEx);
        }

        private void doShareFanoutInitialAck(
            long traceId,
            int partitionId,
            long partitionOffset,
            KafkaShareAckType type,
            long memberCorrelation)
        {
            final KafkaFlushExFW flushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .share(s -> s.partition(p -> p.partitionId(partitionId).partitionOffset(partitionOffset))
                             .type(t -> t.set(type))
                             .correlationId(memberCorrelation))
                .build();

            doFlush(receiver, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                traceId, authorization, initialBud, 0, flushEx);
        }

        private void doShareFanoutInitialEnd(
            long traceId)
        {
            if (StreamState.initialOpening(state) && !StreamState.initialClosed(state))
            {
                state = StreamState.closeInitial(state);

                doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);
            }
        }

        private void doShareFanoutInitialAbort(
            long traceId)
        {
            if (StreamState.initialOpening(state) && !StreamState.initialClosed(state))
            {
                state = StreamState.closeInitial(StreamState.abortingInitial(state));

                doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);
            }
        }

        private void doShareFanoutReplyReset(
            long traceId)
        {
            if (StreamState.replyOpening(state) && !StreamState.replyClosed(state))
            {
                state = StreamState.closeReply(state);

                doReset(receiver, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doShareFanoutReplyWindow(
            long traceId)
        {
            if (StreamState.replyOpening(state) && !StreamState.replyClosed(state))
            {
                final long acknowledge = records.heldBytes() < stagingMaxBytes ? replySeq : replyAck;

                if (!StreamState.replyOpened(state) || acknowledge > replyAck)
                {
                    replyAck = acknowledge;
                    replyMax = bufferPool.slotCapacity();
                    state = StreamState.openReply(state);

                    doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                        traceId, authorization, 0L, 0);
                }
            }
        }

        private void doShareFanoutLeave(
            long traceId)
        {
            collected.clear();
            records.collectAcquired(collected);

            for (int index = 0; index < collected.size(); index++)
            {
                final KafkaShareRecord record = collected.get(index);

                doShareFanoutInitialAck(traceId, record.partitionId, record.partitionOffset,
                    KafkaShareAckType.RELEASE, NO_CORRELATION_ID);
            }

            collected.clear();

            doShareFanoutInitialEnd(traceId);
            doShareFanoutClose();
        }

        private void doShareFanoutCleanup(
            long traceId,
            Flyweight extension)
        {
            while (!streams.isEmpty())
            {
                final KafkaCacheServerShareStream stream = streams.remove(streams.size() - 1);

                stream.cleanup(traceId, extension);
            }

            doShareFanoutInitialAbort(traceId);
            doShareFanoutReplyReset(traceId);
            doShareFanoutClose();
        }

        private void doShareFanoutClose()
        {
            if (fanoutsByKey.get(key) == this)
            {
                fanoutsByKey.remove(key);
            }

            if (expireId != NO_CANCEL_ID)
            {
                signaler.cancel(expireId);
                expireId = NO_CANCEL_ID;
                expireAt = Long.MAX_VALUE;
            }

            records.clear();
            staging.close();
        }

        private void doShareFanoutDrain(
            long traceId)
        {
            final long now = currentTimeMillis();

            KafkaShareRecord record = records.peekReady();
            while (record != null)
            {
                if (record.deliverBy <= now || record.expiresAt <= now)
                {
                    doShareFanoutRelease(traceId, record);
                }
                else
                {
                    final KafkaCacheServerShareStream stream = nextDeliverableStream();
                    if (stream == null)
                    {
                        break;
                    }

                    records.deliver(record, stream.initialId);
                    stream.doShareReplyRecord(traceId, record);
                }

                record = records.peekReady();
            }

            doShareFanoutReplyWindow(traceId);
            scheduleExpiry();
        }

        private KafkaCacheServerShareStream nextDeliverableStream()
        {
            KafkaCacheServerShareStream found = null;

            final int size = streams.size();
            for (int step = 0; found == null && step < size; step++)
            {
                final int index = (rotation + step) % size;
                final KafkaCacheServerShareStream stream = streams.get(index);

                if (stream.deliverable())
                {
                    found = stream;
                    rotation = (index + 1) % size;
                }
            }

            return found;
        }

        private void scheduleExpiry()
        {
            final long expiresAtMin = records.expiresAtMin();

            if (expiresAtMin != Long.MAX_VALUE && (expireId == NO_CANCEL_ID || expiresAtMin < expireAt))
            {
                if (expireId != NO_CANCEL_ID)
                {
                    signaler.cancel(expireId);
                }

                expireAt = expiresAtMin;
                expireId = signaler.signalAt(expiresAtMin, SIGNAL_LOCK_EXPIRY, this::onShareFanoutSignal);
            }
        }

        private void onShareFanoutSignal(
            int signalId)
        {
            expireId = NO_CANCEL_ID;
            expireAt = Long.MAX_VALUE;

            final long traceId = supplyTraceId.getAsLong();

            collected.clear();
            records.collectExpired(currentTimeMillis(), collected);

            for (int index = 0; index < collected.size(); index++)
            {
                doShareFanoutRelease(traceId, collected.get(index));
            }

            collected.clear();

            doShareFanoutDrain(traceId);
        }

        private void doShareFanoutRelease(
            long traceId,
            KafkaShareRecord record)
        {
            doShareFanoutInitialAck(traceId, record.partitionId, record.partitionOffset,
                KafkaShareAckType.RELEASE, NO_CORRELATION_ID);
            staging.release(record);
            records.remove(record);
        }

        private void onShareFanoutMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onShareFanoutReplyBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onShareFanoutReplyData(data);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onShareFanoutReplyFlush(flush);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onShareFanoutReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onShareFanoutReplyAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onShareFanoutInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onShareFanoutInitialWindow(window);
                break;
            default:
                break;
            }
        }

        private void onShareFanoutReplyBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final KafkaBeginExFW kafkaBeginEx = extension.get(kafkaBeginExRO::tryWrap);
            final KafkaShareBeginExFW kafkaShareBeginEx = kafkaBeginEx.share();

            host = kafkaShareBeginEx.host().asString();
            port = kafkaShareBeginEx.port();

            state = StreamState.openingReply(state);

            doShareFanoutReplyWindow(traceId);

            for (int index = 0; index < streams.size(); index++)
            {
                streams.get(index).doShareReplyBegin(traceId);
            }
        }

        private void onShareFanoutReplyData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;
            assert replySeq <= replyAck + replyMax;

            if (StreamState.initialClosed(state))
            {
                replyAck = replySeq;
            }
            else
            {
                onShareFanoutStage(traceId, data);
            }

            doShareFanoutReplyWindow(traceId);
        }

        private void onShareFanoutStage(
            long traceId,
            DataFW data)
        {
            final int flags = data.flags();
            final OctetsFW payload = data.payload();

            final KafkaDataExFW kafkaDataEx = hasInit(flags) || hasFin(flags)
                ? data.extension().get(kafkaDataExRO::tryWrap)
                : null;
            final KafkaShareDataExFW shareDataEx = kafkaDataEx != null ? kafkaDataEx.share() : null;

            boolean staged = true;

            if (hasInit(flags))
            {
                final int deferred = shareDataEx.deferred();
                final KafkaKeyFW recordKey = shareDataEx.key();
                final int valueLength = payload != null ? payload.sizeof() + deferred : -1;

                staged = staging.fits(recordKey, valueLength);

                if (staged)
                {
                    stagingPartitionId = shareDataEx.partition().partitionId();
                    stagingPartitionOffset = shareDataEx.partition().partitionOffset();
                    stagingTimestamp = shareDataEx.timestamp();

                    staging.start(traceId, ++sequence, stagingTimestamp, recordKey, valueLength, payload);
                }
                else
                {
                    doShareFanoutCleanup(traceId, newResetEx(ERROR_MESSAGE_TOO_LARGE));
                }
            }

            if (staged)
            {
                if (payload != null)
                {
                    staging.append(traceId, flags, payload);
                }

                if (hasFin(flags))
                {
                    final ArrayFW<KafkaHeaderFW> headers = shareDataEx != null ? shareDataEx.headers() : EMPTY_HEADERS;

                    staging.finish(records, stagingPartitionId, stagingPartitionOffset, sequence, stagingTimestamp, headers);
                }
            }
        }

        private void onShareFanoutReplyFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;
            assert replySeq <= replyAck + replyMax;

            final KafkaFlushExFW kafkaFlushEx = extension.get(kafkaFlushExRO::tryWrap);

            if (kafkaFlushEx != null && !StreamState.initialClosed(state))
            {
                switch (kafkaFlushEx.kind())
                {
                case KafkaFlushExFW.KIND_SHARE_ACQUIRED:
                    onShareFanoutAcquired(traceId, kafkaFlushEx.shareAcquired());
                    break;
                case KafkaFlushExFW.KIND_SHARE:
                    onShareFanoutConfirm(traceId, kafkaFlushEx.share());
                    break;
                default:
                    break;
                }
            }
        }

        private void onShareFanoutAcquired(
            long traceId,
            KafkaShareAcquiredFlushExFW acquiredEx)
        {
            final int partitionId = acquiredEx.partitionId();
            final int lockTimeoutMillis = acquiredEx.lockTimeoutMillis();
            final Array32FW<KafkaShareAcquiredRangeFW> ranges = acquiredEx.ranges();
            final long now = currentTimeMillis();
            final long expiresAt = now + Math.max(lockTimeoutMillis, 0);
            final long deliverBy = expiresAt - (long) lockTimeoutMillis * lockMarginPercent / PERCENT_MAX;

            collected.clear();

            for (int index = 0; index < records.stagedCount(); index++)
            {
                final KafkaShareRecord record = records.staged(index);

                if (record.partitionId == partitionId)
                {
                    collected.add(record);
                }
            }

            for (int index = 0; index < collected.size(); index++)
            {
                final KafkaShareRecord record = collected.get(index);
                final int deliveryCount = deliveryCountOf(ranges, record.partitionOffset);

                if (deliveryCount == 0)
                {
                    staging.release(record);
                    records.remove(record);
                }
                else if (expiresAt <= now)
                {
                    doShareFanoutRelease(traceId, record);
                }
                else if (!matchesFilters(record))
                {
                    doShareFanoutInitialAck(traceId, record.partitionId, record.partitionOffset,
                        KafkaShareAckType.ACCEPT, NO_CORRELATION_ID);
                    staging.release(record);
                    records.remove(record);
                }
                else
                {
                    records.acquire(record, deliveryCount, expiresAt, deliverBy);
                }
            }

            collected.clear();

            doShareFanoutDrain(traceId);
        }

        private int deliveryCountOf(
            Array32FW<KafkaShareAcquiredRangeFW> ranges,
            long partitionOffset)
        {
            int deliveryCount = 0;

            final DirectBufferEx items = ranges.items();
            final int limit = items.capacity();

            int offset = 0;
            while (deliveryCount == 0 && offset < limit)
            {
                final KafkaShareAcquiredRangeFW range = acquiredRangeRO.wrap(items, offset, limit);
                offset = range.limit();

                if (range.firstOffset() <= partitionOffset && partitionOffset <= range.lastOffset())
                {
                    deliveryCount = range.deliveryCount();
                }
            }

            return deliveryCount;
        }

        private boolean matchesFilters(
            KafkaShareRecord record)
        {
            final KafkaCacheEntryFW entry = staging.entry(record);

            return recordFilter.test(entry.paddedKey().key().value(), entry.headers());
        }

        private void onShareFanoutConfirm(
            long traceId,
            KafkaShareFlushExFW confirmEx)
        {
            final long memberCorrelation = confirmEx.correlationId();

            int found = -1;
            for (int index = 0; found == -1 && index < confirmCorrelations.size(); index++)
            {
                if (confirmCorrelations.getLong(index) == memberCorrelation)
                {
                    found = index;
                }
            }

            if (found != -1)
            {
                final long streamId = confirmStreamIds.getLong(found);
                final long streamCorrelation = confirmStreamCorrelations.getLong(found);

                confirmCorrelations.removeAt(found);
                confirmStreamIds.removeAt(found);
                confirmStreamCorrelations.removeAt(found);

                for (int index = 0; index < streams.size(); index++)
                {
                    final KafkaCacheServerShareStream stream = streams.get(index);

                    if (stream.initialId == streamId)
                    {
                        stream.doShareReplyConfirm(traceId, confirmEx.partition().partitionId(),
                            confirmEx.partition().partitionOffset(), confirmEx.type().get(), streamCorrelation,
                            confirmEx.error());
                    }
                }
            }
        }

        private void onShareFanoutReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            replySeq = end.sequence();
            state = StreamState.closeReply(state);

            if (!StreamState.initialClosed(state))
            {
                doShareFanoutCleanup(traceId, newResetEx(ERROR_UNKNOWN_SERVER_ERROR));
            }
        }

        private void onShareFanoutReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            replySeq = abort.sequence();
            state = StreamState.closeReply(state);

            if (!StreamState.initialClosed(state))
            {
                doShareFanoutCleanup(traceId, newResetEx(ERROR_UNKNOWN_SERVER_ERROR));
            }
        }

        private void onShareFanoutInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final OctetsFW extension = reset.extension();

            state = StreamState.closeInitial(state);

            doShareFanoutCleanup(traceId, extension);
        }

        private void onShareFanoutInitialWindow(
            WindowFW window)
        {
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long budgetId = window.budgetId();

            initialAck = acknowledge;
            initialMax = maximum;
            initialBud = budgetId;
            state = StreamState.openInitial(state);
        }
    }

    private final class KafkaCacheServerShareStream
    {
        private final KafkaCacheServerShareFanout fanout;
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBud;
        private BudgetDebitor replyDeb;
        private long replyDebIndex = NO_DEBITOR_INDEX;

        private KafkaShareRecord delivering;
        private int deliveringOffset;

        private KafkaCacheServerShareStream(
            KafkaCacheServerShareFanout fanout,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization)
        {
            this.fanout = fanout;
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
        }

        private boolean deliverable()
        {
            return StreamState.replyOpened(state) &&
                !StreamState.replyClosed(state) &&
                delivering == null &&
                replyBudget() > replyPad;
        }

        private int replyBudget()
        {
            return replyMax - (int) (replySeq - replyAck);
        }

        private int replyClaim(
            long traceId,
            int valueSize)
        {
            final int maximum = valueSize + replyPad;
            int claimed = Math.min(maximum, replyBudget());

            if (claimed < maximum && claimed <= replyPad)
            {
                claimed = 0;
            }

            if (claimed > 0 && replyDebIndex != NO_DEBITOR_INDEX)
            {
                claimed = replyDeb.claim(traceId, replyDebIndex, replyId, Math.min(claimed, DEBITOR_CLAIM_MINIMUM), claimed, 0);
            }

            return claimed;
        }

        private void onShareMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onShareInitialBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onShareInitialData(data);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onShareInitialFlush(flush);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onShareInitialEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onShareInitialAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onShareReplyWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onShareReplyReset(reset);
                break;
            default:
                break;
            }
        }

        private void onShareInitialBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final KafkaBeginExFW kafkaBeginEx = extension.get(kafkaBeginExRO::tryWrap);
            final KafkaShareBeginExFW kafkaShareBeginEx = kafkaBeginEx.share();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            initialAck = acknowledge;
            state = StreamState.openingInitial(state);

            fanout.onShareFanoutStreamOpening(traceId, this, kafkaShareBeginEx.topic().asString(),
                kafkaShareBeginEx.filters());
        }

        private void onShareInitialData(
            DataFW data)
        {
            final long traceId = data.traceId();

            cleanup(traceId, newResetEx(ERROR_INVALID_REQUEST));
            fanout.onShareFanoutStreamClosed(traceId, this);
        }

        private void onShareInitialFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence + reserved;

            final KafkaFlushExFW kafkaFlushEx = extension.get(kafkaFlushExRO::tryWrap);

            if (kafkaFlushEx != null && kafkaFlushEx.kind() == KafkaFlushExFW.KIND_SHARE)
            {
                final KafkaShareFlushExFW shareFlushEx = kafkaFlushEx.share();

                fanout.onShareFanoutAcknowledge(traceId, this,
                    shareFlushEx.partition().partitionId(),
                    shareFlushEx.partition().partitionOffset(),
                    shareFlushEx.type().get(),
                    shareFlushEx.correlationId());
            }
            else
            {
                cleanup(traceId, newResetEx(ERROR_INVALID_REQUEST));
                fanout.onShareFanoutStreamClosed(traceId, this);
            }

            if (reserved > 0)
            {
                initialAck = initialSeq;
                doShareInitialWindow(traceId);
            }
        }

        private void onShareInitialEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long traceId = end.traceId();

            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = StreamState.closeInitial(state);

            doShareReplyEnd(traceId);

            fanout.onShareFanoutStreamClosed(traceId, this);
        }

        private void onShareInitialAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long traceId = abort.traceId();

            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = StreamState.closeInitial(state);

            doShareReplyAbort(traceId);

            fanout.onShareFanoutStreamClosed(traceId, this);
        }

        private void onShareReplyWindow(
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

            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = padding;
            replyBud = budgetId;

            assert replyAck <= replySeq;

            if (replyBud != NO_BUDGET_ID && replyDeb == null)
            {
                replyDeb = supplyDebitor.apply(replyBud);
                replyDebIndex = replyDeb.acquire(replyBud, replyId, this::onShareReplyBudget);
            }

            state = StreamState.openReply(state);

            onShareReplyBudget(traceId);
        }

        private void onShareReplyBudget(
            long traceId)
        {
            if (delivering != null)
            {
                doShareReplyRecord(traceId, delivering);
            }

            fanout.doShareFanoutDrain(traceId);
        }

        private void onShareReplyReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = StreamState.closeReply(state);

            cleanup(traceId, EMPTY_OCTETS);

            fanout.onShareFanoutStreamClosed(traceId, this);
        }

        private void doShareInitialWindow(
            long traceId)
        {
            initialMax = bufferPool.slotCapacity();
            state = StreamState.openInitial(state);

            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                traceId, authorization, 0L, 0);
        }

        private void doShareInitialReset(
            long traceId,
            Flyweight extension)
        {
            if (!StreamState.initialClosed(state))
            {
                state = StreamState.closeInitial(state);

                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                    traceId, authorization, extension);
            }
        }

        private void doShareReplyBegin(
            long traceId)
        {
            if (!StreamState.replyOpening(state) && !StreamState.initialClosed(state))
            {
                state = StreamState.openingReply(state);

                final KafkaBeginExFW beginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .share(s -> s.groupId(fanout.groupId)
                                 .topic(fanout.topic)
                                 .host(fanout.host)
                                 .port(fanout.port))
                    .build();

                doBegin(sender, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                    traceId, authorization, affinity, beginEx);
            }
        }

        private void doShareReplyRecord(
            long traceId,
            KafkaShareRecord record)
        {
            delivering = record;

            final KafkaCacheEntryFW entry = fanout.staging.entry(record);
            final OctetsFW value = entry.paddedValue().value();
            final int valueLength = value != null ? value.sizeof() : 0;

            boolean progress = true;
            while (delivering != null && progress)
            {
                final int remaining = valueLength - deliveringOffset;
                final int claimed = replyClaim(traceId, remaining);

                if (claimed == remaining + replyPad)
                {
                    doShareReplyRecordFragment(traceId, entry, value, remaining, claimed, true);
                }
                else if (claimed > replyPad)
                {
                    doShareReplyRecordFragment(traceId, entry, value, claimed - replyPad, claimed, false);
                }
                else
                {
                    progress = false;
                }
            }
        }

        private void doShareReplyRecordFragment(
            long traceId,
            KafkaCacheEntryFW entry,
            OctetsFW value,
            int length,
            int reserved,
            boolean last)
        {
            final KafkaShareRecord record = delivering;
            final boolean first = deliveringOffset == 0;
            final int deferred = value != null ? value.sizeof() - deliveringOffset - length : 0;

            final int flags = first ? (last ? COMPLETE : INIT) : (last ? FIN : NONE);

            final OctetsFW payload = value != null
                ? payloadRO.wrap(value.buffer(), value.offset() + deliveringOffset, value.offset() + deliveringOffset + length)
                : null;

            final Flyweight dataEx = newDataEx(record, entry, first, last, deferred);

            doShareReplyData(dataEx, flags, reserved, payload, traceId);

            deliveringOffset += length;

            if (last)
            {
                delivering = null;
                deliveringOffset = 0;

                fanout.onShareFanoutDelivered(traceId, record);
            }
        }

        private Flyweight newDataEx(
            KafkaShareRecord record,
            KafkaCacheEntryFW entry,
            boolean first,
            boolean last,
            int deferred)
        {
            Flyweight dataEx = EMPTY_OCTETS;

            if (first)
            {
                dataEx = kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .share(s -> setRecordInit(s, record, entry, deferred))
                    .build();
            }
            else if (last)
            {
                dataEx = kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .share(s -> setRecordFin(s, record, entry))
                    .build();
            }

            return dataEx;
        }

        private void doShareReplyData(
            Flyweight dataEx,
            int flags,
            int reserved,
            OctetsFW payload,
            long traceId)
        {
            final DataFW frame = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .flags(flags)
                .budgetId(replyBud)
                .reserved(reserved)
                .payload(payload)
                .extension(dataEx.buffer(), dataEx.offset(), dataEx.sizeof())
                .build();

            sender.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());

            replySeq += reserved;

            assert replyAck <= replySeq;
        }

        private void setRecordInit(
            KafkaShareDataExFW.Builder share,
            KafkaShareRecord record,
            KafkaCacheEntryFW entry,
            int deferred)
        {
            share.deferred(deferred)
                 .timestamp(record.timestamp)
                 .deliveryCount(record.deliveryCount + record.redeliveries)
                 .partition(p -> p.partitionId(record.partitionId).partitionOffset(record.partitionOffset))
                 .key(k -> k.set(entry.paddedKey().key()));

            entry.headers().forEach(h -> share.headersItem(i -> i.set(h)));
        }

        private void setRecordFin(
            KafkaShareDataExFW.Builder share,
            KafkaShareRecord record,
            KafkaCacheEntryFW entry)
        {
            share.partition(p -> p.partitionId(record.partitionId).partitionOffset(record.partitionOffset));

            entry.headers().forEach(h -> share.headersItem(i -> i.set(h)));
        }

        private void doShareReplyConfirm(
            long traceId,
            int partitionId,
            long partitionOffset,
            KafkaShareAckType type,
            long correlationId,
            int error)
        {
            final KafkaFlushExFW flushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .share(s -> s.partition(p -> p.partitionId(partitionId).partitionOffset(partitionOffset))
                             .type(t -> t.set(type))
                             .correlationId(correlationId)
                             .error(error))
                .build();

            doFlush(sender, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                traceId, authorization, replyBud, 0, flushEx);
        }

        private void doShareReplyEnd(
            long traceId)
        {
            if (StreamState.replyOpening(state) && !StreamState.replyClosed(state))
            {
                state = StreamState.closeReply(state);

                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
            }

            cleanupDebitor();
        }

        private void doShareReplyAbort(
            long traceId)
        {
            if (!StreamState.replyClosed(state))
            {
                state = StreamState.closeReply(StreamState.abortingReply(state));

                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
            }

            cleanupDebitor();
        }

        private void cleanup(
            long traceId,
            Flyweight extension)
        {
            doShareInitialReset(traceId, extension);
            doShareReplyAbort(traceId);
        }

        private void cleanupDelivery()
        {
            delivering = null;
            deliveringOffset = 0;

            cleanupDebitor();
        }

        private void cleanupDebitor()
        {
            if (replyDebIndex != NO_DEBITOR_INDEX)
            {
                replyDeb.release(replyDebIndex, replyId);
                replyDebIndex = NO_DEBITOR_INDEX;
                replyDeb = null;
            }
        }
    }

    private Flyweight newResetEx(
        int error)
    {
        return kafkaResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
            .typeId(kafkaTypeId)
            .error(error)
            .build();
    }

    private static String newMemberId()
    {
        final UUID uuid = UUID.randomUUID();
        final ByteBuffer bytes = ByteBuffer.allocate(MEMBER_ID_BYTES);
        bytes.putLong(uuid.getMostSignificantBits());
        bytes.putLong(uuid.getLeastSignificantBits());

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.array());
    }

    private void doBegin(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int state,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .state(state)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        int state,
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
            .state(state)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .reserved(reserved)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        int state,
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
            .state(state)
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
        int state,
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
            .state(state)
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
        int state,
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
            .state(state)
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
        int state,
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
            .state(state)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }
}
