/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.kafka.internal.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaCog;
import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.cog.kafka.internal.config.KafkaBinding;
import io.aklivity.zilla.runtime.cog.kafka.internal.config.KafkaTopic;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.ArrayFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.KafkaConfigFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.KafkaPartitionFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.KafkaBootstrapBeginExFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.KafkaDescribeDataExFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.KafkaFetchFlushExFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.KafkaMetaDataExFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.cog.AxleContext;
import io.aklivity.zilla.runtime.engine.cog.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.cog.stream.StreamFactory;

public final class KafkaCacheServerBootstrapFactory implements StreamFactory
{
    private static final String16FW CONFIG_NAME_CLEANUP_POLICY = new String16FW("cleanup.policy");
    private static final String16FW CONFIG_NAME_MAX_MESSAGE_BYTES = new String16FW("max.message.bytes");
    private static final String16FW CONFIG_NAME_SEGMENT_BYTES = new String16FW("segment.bytes");
    private static final String16FW CONFIG_NAME_SEGMENT_INDEX_BYTES = new String16FW("segment.index.bytes");
    private static final String16FW CONFIG_NAME_SEGMENT_MILLIS = new String16FW("segment.ms");
    private static final String16FW CONFIG_NAME_RETENTION_BYTES = new String16FW("retention.bytes");
    private static final String16FW CONFIG_NAME_RETENTION_MILLIS = new String16FW("retention.ms");
    private static final String16FW CONFIG_NAME_DELETE_RETENTION_MILLIS = new String16FW("delete.retention.ms");
    private static final String16FW CONFIG_NAME_MIN_COMPACTION_LAG_MILLIS = new String16FW("min.compaction.lag.ms");
    private static final String16FW CONFIG_NAME_MAX_COMPACTION_LAG_MILLIS = new String16FW("max.compaction.lag.ms");
    private static final String16FW CONFIG_NAME_MIN_CLEANABLE_DIRTY_RATIO = new String16FW("min.cleanable.dirty.ratio");

    private static final long OFFSET_HISTORICAL = KafkaOffsetType.HISTORICAL.value();

    private static final int ERROR_NOT_LEADER_FOR_PARTITION = 6;

    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();
    private final KafkaFlushExFW kafkaFlushExRO = new KafkaFlushExFW();
    private final KafkaResetExFW kafkaResetExRO = new KafkaResetExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();

    private final MutableInteger partitionCount = new MutableInteger();

    private final int kafkaTypeId;
    private final MutableDirectBuffer writeBuffer;
    private final StreamFactory streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongFunction<KafkaBinding> supplyBinding;

    public KafkaCacheServerBootstrapFactory(
        KafkaConfiguration config,
        AxleContext context,
        LongFunction<KafkaBinding> supplyBinding)
    {
        this.kafkaTypeId = context.supplyTypeId(KafkaCog.NAME);
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyBinding = supplyBinding;
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
        final long routeId = begin.routeId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final long affinity = begin.affinity();

        assert (initialId & 0x0000_0000_0000_0001L) != 0L;

        final OctetsFW extension = begin.extension();
        final ExtensionFW beginEx = extensionRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
        final KafkaBeginExFW kafkaBeginEx = beginEx != null && beginEx.typeId() == kafkaTypeId ?
                kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit()) : null;

        assert kafkaBeginEx != null;
        assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_BOOTSTRAP;
        final KafkaBootstrapBeginExFW kafkaBootstrapBeginEx = kafkaBeginEx.bootstrap();
        final String16FW beginTopic = kafkaBootstrapBeginEx.topic();
        final String topicName = beginTopic != null ? beginTopic.asString() : null;

        MessageConsumer newStream = null;

        final KafkaBinding binding = supplyBinding.apply(routeId);

        if (binding != null && binding.bootstrap(topicName))
        {
            final KafkaTopic topic = binding.topic(topicName);
            final long resolvedId = routeId;
            final long defaultOffset = topic != null && topic.defaultOffset != null
                    ? topic.defaultOffset.value()
                    : OFFSET_HISTORICAL;

            newStream = new KafkaBootstrapStream(
                    sender,
                    routeId,
                    initialId,
                    affinity,
                    authorization,
                    topicName,
                    resolvedId,
                    defaultOffset)::onBootstrapInitial;
        }

        return newStream;
    }

    private MessageConsumer newStream(
        MessageConsumer sender,
        long routeId,
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
                .routeId(routeId)
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
        long routeId,
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
                .routeId(routeId)
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

    private void doEnd(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Consumer<OctetsFW.Builder> extension)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                               .routeId(routeId)
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
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Consumer<OctetsFW.Builder> extension)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
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

    private final class KafkaBootstrapStream
    {
        private final MessageConsumer sender;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;
        private final String topic;
        private final long resolvedId;
        private final KafkaBootstrapDescribeStream describeStream;
        private final KafkaBootstrapMetaStream metaStream;
        private final List<KafkaBootstrapFetchStream> fetchStreams;
        private final Long2LongHashMap nextOffsetsById;
        private final long defaultOffset;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private long replyBudgetId;

        KafkaBootstrapStream(
            MessageConsumer sender,
            long routeId,
            long initialId,
            long affinity,
            long authorization,
            String topic,
            long resolvedId,
            long defaultOffset)
        {
            this.sender = sender;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.topic = topic;
            this.resolvedId = resolvedId;
            this.describeStream = new KafkaBootstrapDescribeStream(this);
            this.metaStream = new KafkaBootstrapMetaStream(this);
            this.fetchStreams = new ArrayList<>();
            this.nextOffsetsById = new Long2LongHashMap(-1L);
            this.defaultOffset = defaultOffset;
        }

        private void onBootstrapInitial(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onBootstrapInitialBegin(begin);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onBootstrapInitialEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onBootstrapInitialAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onBootstrapReplyWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onBootstrapReplyReset(reset);
                break;
            default:
                break;
            }
        }

        private void onBootstrapInitialBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            assert state == 0;
            state = KafkaState.openingInitial(state);

            describeStream.doDescribeInitialBegin(traceId);
        }

        private void onBootstrapInitialEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            assert !KafkaState.initialClosed(state);
            state = KafkaState.closedInitial(state);

            describeStream.doDescribeInitialEndIfNecessary(traceId);
            metaStream.doMetaInitialEndIfNecessary(traceId);
            fetchStreams.forEach(f -> f.doFetchInitialEndIfNecessary(traceId));

            doBootstrapReplyEndIfNecessary(traceId);
        }

        private void onBootstrapInitialAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            assert !KafkaState.initialClosed(state);
            state = KafkaState.closedInitial(state);

            describeStream.doDescribeInitialAbortIfNecessary(traceId);
            metaStream.doMetaInitialAbortIfNecessary(traceId);
            fetchStreams.forEach(f -> f.doFetchInitialAbortIfNecessary(traceId));

            doBootstrapReplyAbortIfNecessary(traceId);
        }

        private void onBootstrapReplyWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            state = KafkaState.openedReply(state);

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

        private void onBootstrapReplyReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedReply(state);

            describeStream.doDescribeReplyReset(traceId);
            metaStream.doMetaReplyReset(traceId);
            fetchStreams.forEach(f -> f.doFetchReplyReset(traceId));

            doBootstrapInitialResetIfNecessary(traceId);
        }

        private void doBootstrapReplyBeginIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyOpening(state))
            {
                doBootstrapReplyBegin(traceId);
            }
        }

        private void doBootstrapReplyBegin(
            long traceId)
        {
            assert !KafkaState.replyOpening(state);
            state = KafkaState.openingReply(state);

            doBegin(sender, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, EMPTY_EXTENSION);
        }

        private void doBootstrapReplyEnd(
            long traceId)
        {
            assert !KafkaState.replyClosed(state);
            state = KafkaState.closedReply(state);
            doEnd(sender, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
        }

        private void doBootstrapReplyAbort(
            long traceId)
        {
            assert !KafkaState.replyClosed(state);
            state = KafkaState.closedReply(state);
            doAbort(sender, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
        }

        private void doBootstrapInitialWindow(
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

                initialMax = Math.max(initialMax, minInitialMax);

                state = KafkaState.openedInitial(state);

                doWindow(sender, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, budgetId, minInitialPad);
            }
        }

        private void doBootstrapInitialReset(
            long traceId)
        {
            assert !KafkaState.initialClosed(state);
            state = KafkaState.closedInitial(state);

            doReset(sender, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
        }

        private void doBootstrapReplyEndIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doBootstrapReplyEnd(traceId);
                describeStream.doDescribeReplyResetIfNecessary(traceId);
                metaStream.doMetaReplyResetIfNecessary(traceId);
                fetchStreams.forEach(f -> f.doFetchReplyResetIfNecessary(traceId));
            }
        }

        private void doBootstrapReplyAbortIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doBootstrapReplyAbort(traceId);
                describeStream.doDescribeReplyResetIfNecessary(traceId);
                metaStream.doMetaReplyResetIfNecessary(traceId);
                fetchStreams.forEach(f -> f.doFetchReplyResetIfNecessary(traceId));
            }
        }

        private void doBootstrapInitialResetIfNecessary(
            long traceId)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                doBootstrapInitialReset(traceId);
                describeStream.doDescribeInitialAbortIfNecessary(traceId);
                metaStream.doMetaInitialAbortIfNecessary(traceId);
                fetchStreams.forEach(f -> f.doFetchInitialAbortIfNecessary(traceId));
            }
        }

        private void doBootstrapCleanup(
            long traceId)
        {
            doBootstrapInitialResetIfNecessary(traceId);
            doBootstrapReplyAbortIfNecessary(traceId);
        }

        private void onTopicConfigChanged(
            long traceId,
            ArrayFW<KafkaConfigFW> changedConfigs)
        {
            metaStream.doMetaInitialBeginIfNecessary(traceId);
        }

        private void onTopicMetaDataChanged(
            long traceId,
            ArrayFW<KafkaPartitionFW> partitions)
        {
            partitions.forEach(partition -> onPartitionMetaDataChangedIfNecessary(traceId, partition));

            partitionCount.value = 0;
            partitions.forEach(partition -> partitionCount.value++);
            assert fetchStreams.size() >= partitionCount.value;
        }

        private void onPartitionMetaDataChangedIfNecessary(
            long traceId,
            KafkaPartitionFW partition)
        {
            final int partitionId = partition.partitionId();
            final int leaderId = partition.leaderId();
            final long partitionOffset = nextPartitionOffset(partitionId);

            KafkaBootstrapFetchStream leader = findPartitionLeader(partitionId);

            if (leader != null && leader.leaderId != leaderId)
            {
                leader.leaderId = leaderId;
                leader.doFetchInitialBeginIfNecessary(traceId, partitionOffset);
            }

            if (leader == null)
            {
                leader = new KafkaBootstrapFetchStream(partitionId, leaderId, this);
                leader.doFetchInitialBegin(traceId, partitionOffset);
                fetchStreams.add(leader);
            }

            assert leader != null;
            assert leader.partitionId == partitionId;
            assert leader.leaderId == leaderId;
        }

        private void onPartitionLeaderReady(
            long traceId,
            long partitionId)
        {
            nextOffsetsById.putIfAbsent(partitionId, defaultOffset);

            if (nextOffsetsById.size() == fetchStreams.size())
            {
                doBootstrapReplyBeginIfNecessary(traceId);

                if (KafkaState.initialClosed(state))
                {
                    doBootstrapReplyEndIfNecessary(traceId);
                }
            }
        }

        private void onPartitionLeaderError(
            long traceId,
            int partitionId,
            int error)
        {
            if (error == ERROR_NOT_LEADER_FOR_PARTITION)
            {
                final KafkaBootstrapFetchStream leader = findPartitionLeader(partitionId);
                assert leader != null;

                if (nextOffsetsById.containsKey(partitionId))
                {
                    final long partitionOffset = nextPartitionOffset(partitionId);
                    leader.doFetchInitialBegin(traceId, partitionOffset);
                }
                else
                {
                    fetchStreams.remove(leader);
                }
            }
            else
            {
                doBootstrapCleanup(traceId);
            }
        }

        private long nextPartitionOffset(
            int partitionId)
        {
            long partitionOffset = nextOffsetsById.get(partitionId);
            if (partitionOffset == nextOffsetsById.missingValue())
            {
                partitionOffset = defaultOffset;
            }
            return partitionOffset;
        }

        private KafkaBootstrapFetchStream findPartitionLeader(
            int partitionId)
        {
            KafkaBootstrapFetchStream leader = null;
            for (int index = 0; index < fetchStreams.size(); index++)
            {
                final KafkaBootstrapFetchStream fetchStream = fetchStreams.get(index);
                if (fetchStream.partitionId == partitionId)
                {
                    leader = fetchStream;
                    break;
                }
            }
            return leader;
        }
    }

    private final class KafkaBootstrapDescribeStream
    {
        private final KafkaBootstrapStream bootstrap;

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

        private KafkaBootstrapDescribeStream(
            KafkaBootstrapStream bootstrap)
        {
            this.bootstrap = bootstrap;
        }

        private void doDescribeInitialBegin(
            long traceId)
        {
            assert state == 0;

            state = KafkaState.openingInitial(state);

            this.initialId = supplyInitialId.applyAsLong(bootstrap.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.receiver = newStream(this::onDescribeReply, bootstrap.resolvedId, initialId, initialSeq, initialAck, initialMax,
                traceId, bootstrap.authorization, 0L,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .describe(m -> m.topic(bootstrap.topic)
                                        .configsItem(ci -> ci.set(CONFIG_NAME_CLEANUP_POLICY))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_MAX_MESSAGE_BYTES))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_SEGMENT_BYTES))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_SEGMENT_INDEX_BYTES))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_SEGMENT_MILLIS))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_RETENTION_BYTES))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_RETENTION_MILLIS))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_DELETE_RETENTION_MILLIS))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_MIN_COMPACTION_LAG_MILLIS))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_MAX_COMPACTION_LAG_MILLIS))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_MIN_CLEANABLE_DIRTY_RATIO)))
                        .build()
                        .sizeof()));
        }

        private void doDescribeInitialEndIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doDescribeInitialEnd(traceId);
            }
        }

        private void doDescribeInitialEnd(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doEnd(receiver, bootstrap.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, bootstrap.authorization, EMPTY_EXTENSION);
        }

        private void doDescribeInitialAbortIfNecessary(
            long traceId)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                doDescribeInitialAbort(traceId);
            }
        }

        private void doDescribeInitialAbort(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doAbort(receiver, bootstrap.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, bootstrap.authorization, EMPTY_EXTENSION);
        }

        private void onDescribeReply(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onDescribeReplyBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onDescribeReplyData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onDescribeReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onDescribeReplyAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onDescribeInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onDescribeInitialWindow(window);
                break;
            default:
                break;
            }
        }

        private void onDescribeReplyBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = KafkaState.openedReply(state);

            doDescribeReplyWindow(traceId, 0, 8192);
        }

        private void onDescribeReplyData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int reserved = data.reserved();
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                bootstrap.doBootstrapCleanup(traceId);
            }
            else
            {
                final KafkaDataExFW kafkaDataEx = extension.get(kafkaDataExRO::wrap);
                final KafkaDescribeDataExFW kafkaDescribeDataEx = kafkaDataEx.describe();
                final ArrayFW<KafkaConfigFW> changedConfigs = kafkaDescribeDataEx.configs();

                bootstrap.onTopicConfigChanged(traceId, changedConfigs);

                doDescribeReplyWindow(traceId, 0, replyMax);
            }
        }

        private void onDescribeReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedReply(state);

            bootstrap.doBootstrapReplyBeginIfNecessary(traceId);
            bootstrap.doBootstrapReplyEndIfNecessary(traceId);

            doDescribeInitialEndIfNecessary(traceId);
        }

        private void onDescribeReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedReply(state);

            bootstrap.doBootstrapReplyAbortIfNecessary(traceId);

            doDescribeInitialAbortIfNecessary(traceId);
        }

        private void onDescribeInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedInitial(state);

            doDescribeReplyResetIfNecessary(traceId);

            bootstrap.doBootstrapCleanup(traceId);
        }

        private void onDescribeInitialWindow(
            WindowFW window)
        {
            if (!KafkaState.initialOpened(state))
            {
                final long traceId = window.traceId();

                state = KafkaState.openedInitial(state);

                bootstrap.doBootstrapInitialWindow(traceId, 0L, 0, 0, 0);
            }
        }

        private void doDescribeReplyWindow(
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

                doWindow(receiver, bootstrap.resolvedId, replyId, replySeq, replyAck, replyMax,
                        traceId, bootstrap.authorization, 0, bootstrap.replyPad);
            }
        }

        private void doDescribeReplyResetIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doDescribeReplyReset(traceId);
            }
        }

        private void doDescribeReplyReset(
            long traceId)
        {
            state = KafkaState.closedReply(state);

            doReset(receiver, bootstrap.resolvedId, replyId, replySeq, replyAck, replyMax,
                    traceId, bootstrap.authorization);
        }
    }

    private final class KafkaBootstrapMetaStream
    {
        private final KafkaBootstrapStream bootstrap;

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

        private KafkaBootstrapMetaStream(
            KafkaBootstrapStream bootstrap)
        {
            this.bootstrap = bootstrap;
        }

        private void doMetaInitialBeginIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialOpening(state))
            {
                doMetaInitialBegin(traceId);
            }
        }

        private void doMetaInitialBegin(
            long traceId)
        {
            assert state == 0;

            state = KafkaState.openingInitial(state);

            this.initialId = supplyInitialId.applyAsLong(bootstrap.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.receiver = newStream(this::onMetaReply, bootstrap.resolvedId, initialId, initialSeq, initialAck, initialMax,
                traceId, bootstrap.authorization, 0L,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .meta(m -> m.topic(bootstrap.topic))
                        .build()
                        .sizeof()));
        }

        private void doMetaInitialEndIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doMetaInitialEnd(traceId);
            }
        }

        private void doMetaInitialEnd(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doEnd(receiver, bootstrap.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, bootstrap.authorization, EMPTY_EXTENSION);
        }

        private void doMetaInitialAbortIfNecessary(
            long traceId)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                doMetaInitialAbort(traceId);
            }
        }

        private void doMetaInitialAbort(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doAbort(receiver, bootstrap.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, bootstrap.authorization, EMPTY_EXTENSION);
        }

        private void onMetaReply(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onMetaReplyBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onMetaReplyData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onMetaReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onMetaReplyAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onMetaInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onMetaInitialWindow(window);
                break;
            default:
                break;
            }
        }

        private void onMetaReplyBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = KafkaState.openedReply(state);

            doMetaReplyWindow(traceId, 0, 8192);
        }

        private void onMetaReplyData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int reserved = data.reserved();
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                bootstrap.doBootstrapCleanup(traceId);
            }
            else
            {
                final KafkaDataExFW kafkaDataEx = extension.get(kafkaDataExRO::wrap);
                final KafkaMetaDataExFW kafkaMetaDataEx = kafkaDataEx.meta();
                final ArrayFW<KafkaPartitionFW> partitions = kafkaMetaDataEx.partitions();

                bootstrap.onTopicMetaDataChanged(traceId, partitions);

                doMetaReplyWindow(traceId, 0, replyMax);
            }
        }

        private void onMetaReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedReply(state);

            bootstrap.doBootstrapReplyBeginIfNecessary(traceId);
            bootstrap.doBootstrapReplyEndIfNecessary(traceId);

            doMetaInitialEndIfNecessary(traceId);
        }

        private void onMetaReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedReply(state);

            bootstrap.doBootstrapReplyAbortIfNecessary(traceId);

            doMetaInitialAbortIfNecessary(traceId);
        }

        private void onMetaInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedInitial(state);

            doMetaReplyResetIfNecessary(traceId);

            bootstrap.doBootstrapCleanup(traceId);
        }

        private void onMetaInitialWindow(
            WindowFW window)
        {
            if (!KafkaState.initialOpened(state))
            {
                final long traceId = window.traceId();

                state = KafkaState.openedInitial(state);

                bootstrap.doBootstrapInitialWindow(traceId, 0L, 0, 0, 0);
            }
        }

        private void doMetaReplyWindow(
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

                doWindow(receiver, bootstrap.resolvedId, replyId, replySeq, replyAck, replyMax,
                        traceId, bootstrap.authorization, 0, bootstrap.replyPad);
            }
        }

        private void doMetaReplyResetIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doMetaReplyReset(traceId);
            }
        }

        private void doMetaReplyReset(
            long traceId)
        {
            state = KafkaState.closedReply(state);

            doReset(receiver, bootstrap.resolvedId, replyId, replySeq, replyAck, replyMax,
                    traceId, bootstrap.authorization);
        }
    }

    private final class KafkaBootstrapFetchStream
    {
        private final int partitionId;
        private final KafkaBootstrapStream bootstrap;

        private int leaderId;

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

        @SuppressWarnings("unused")
        private long partitionOffset;

        private KafkaBootstrapFetchStream(
            int partitionId,
            int leaderId,
            KafkaBootstrapStream bootstrap)
        {
            this.leaderId = leaderId;
            this.partitionId = partitionId;
            this.bootstrap = bootstrap;
        }

        private void doFetchInitialBeginIfNecessary(
            long traceId,
            long partitionOffset)
        {
            if (!KafkaState.initialOpening(state))
            {
                doFetchInitialBegin(traceId, partitionOffset);
            }
        }

        private void doFetchInitialBegin(
            long traceId,
            long partitionOffset)
        {
            if (KafkaState.closed(state))
            {
                state = 0;
            }

            assert state == 0;

            state = KafkaState.openingInitial(state);

            this.initialId = supplyInitialId.applyAsLong(bootstrap.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.receiver = newStream(this::onFetchReply, bootstrap.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, bootstrap.authorization, leaderId,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .fetch(f -> f.topic(bootstrap.topic)
                                     .partition(p -> p.partitionId(partitionId).partitionOffset(OFFSET_HISTORICAL)))
                        .build()
                        .sizeof()));
        }

        private void doFetchInitialEndIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doFetchInitialEnd(traceId);
            }
        }

        private void doFetchInitialEnd(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doEnd(receiver, bootstrap.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, bootstrap.authorization, EMPTY_EXTENSION);
        }

        private void doFetchInitialAbortIfNecessary(
            long traceId)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                doFetchInitialAbort(traceId);
            }
        }

        private void doFetchInitialAbort(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doAbort(receiver, bootstrap.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, bootstrap.authorization, EMPTY_EXTENSION);
        }

        private void onFetchInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final OctetsFW extension = reset.extension();

            state = KafkaState.closedInitial(state);

            final KafkaResetExFW kafkaResetEx = extension.get(kafkaResetExRO::tryWrap);
            final int error = kafkaResetEx != null ? kafkaResetEx.error() : -1;

            doFetchReplyResetIfNecessary(traceId);

            assert KafkaState.closed(state);

            bootstrap.onPartitionLeaderError(traceId, partitionId, error);
        }

        private void onFetchInitialWindow(
            WindowFW window)
        {
            if (!KafkaState.initialOpened(state))
            {
                final long traceId = window.traceId();

                state = KafkaState.openedInitial(state);

                bootstrap.doBootstrapInitialWindow(traceId, 0L, 0, 0, 0);
            }
        }

        private void onFetchReply(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onFetchReplyBegin(begin);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onFetchReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onFetchReplyAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onFetchReplyFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onFetchInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onFetchInitialWindow(window);
                break;
            default:
                break;
            }
        }

        private void onFetchReplyBegin(
            BeginFW begin)
        {
            state = KafkaState.openingReply(state);

            final long traceId = begin.traceId();

            bootstrap.onPartitionLeaderReady(traceId, partitionId);

            doFetchReplyWindow(traceId, 0, 8192); // TODO: consider 0 to avoid receiving FLUSH frames
        }

        private void onFetchReplyFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                bootstrap.doBootstrapCleanup(traceId);
            }
            else
            {
                final OctetsFW extension = flush.extension();
                final KafkaFlushExFW kafkaFlushEx = extension.get(kafkaFlushExRO::wrap);
                final KafkaFetchFlushExFW kafkaFetchFlushEx = kafkaFlushEx.fetch();
                final KafkaOffsetFW partition = kafkaFetchFlushEx.partition();

                this.partitionOffset = partition.partitionOffset();

                doFetchReplyWindow(traceId, 0, replyMax);
            }
        }

        private void onFetchReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedReply(state);

            bootstrap.doBootstrapReplyEndIfNecessary(traceId);

            doFetchInitialEndIfNecessary(traceId);
        }

        private void onFetchReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedReply(state);

            bootstrap.doBootstrapReplyAbortIfNecessary(traceId);

            doFetchInitialAbortIfNecessary(traceId);
        }

        private void doFetchReplyWindow(
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

                doWindow(receiver, bootstrap.resolvedId, replyId, replySeq, replyAck, replyMax,
                        traceId, bootstrap.authorization, bootstrap.replyBudgetId, 0);
            }
        }

        private void doFetchReplyResetIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doFetchReplyReset(traceId);
            }
        }

        private void doFetchReplyReset(
            long traceId)
        {
            state = KafkaState.closedReply(state);

            doReset(receiver, bootstrap.resolvedId, replyId, replySeq, replyAck, replyMax,
                    traceId, bootstrap.authorization);
        }
    }
}
