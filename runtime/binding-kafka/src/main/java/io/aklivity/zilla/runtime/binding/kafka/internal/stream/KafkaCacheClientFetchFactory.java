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

import static io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheServerFetchFactory.SIZE_OF_FLUSH_WITH_EXTENSION;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetFW.Builder.DEFAULT_LATEST_OFFSET;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType.HISTORICAL;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType.LIVE;
import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_BUDGET_ID;
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCache;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorFactory;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorFactory.KafkaCacheCursor;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorFactory.KafkaFilterCondition;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition.Node;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheTopic;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.ArrayFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
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
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFetchBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFetchFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class KafkaCacheClientFetchFactory implements BindingHandler
{
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(), 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};
    private static final MessageConsumer NO_RECEIVER = (m, b, i, l) -> {};

    private static final int ERROR_NOT_LEADER_FOR_PARTITION = 6;

    private static final long OFFSET_LIVE = LIVE.value();
    private static final long OFFSET_HISTORICAL = HISTORICAL.value();

    private static final int FLAG_FIN = 0x01;
    private static final int FLAG_INIT = 0x02;
    private static final int FLAG_NONE = 0x00;

    private static final int SIGNAL_FANOUT_REPLY_WINDOW = 1;

    private final BeginFW beginRO = new BeginFW();
    private final FlushFW flushRO = new FlushFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final SignalFW signalRO = new SignalFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaFlushExFW kafkaFlushExRO = new KafkaFlushExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final KafkaResetExFW.Builder kafkaResetExRW = new KafkaResetExFW.Builder();

    private final OctetsFW valueFragmentRO = new OctetsFW();
    private final KafkaCacheEntryFW entryRO = new KafkaCacheEntryFW();

    private final int kafkaTypeId;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BufferPool bufferPool;
    private final Signaler signaler;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongFunction<String> supplyNamespace;
    private final LongFunction<String> supplyLocalName;
    private final LongFunction<BudgetDebitor> supplyDebitor;
    private final LongFunction<KafkaBindingConfig> supplyBinding;
    private final Function<String, KafkaCache> supplyCache;
    private final LongFunction<KafkaCacheRoute> supplyCacheRoute;
    private final KafkaCacheCursorFactory cursorFactory;

    public KafkaCacheClientFetchFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding,
        LongFunction<BudgetDebitor> supplyDebitor,
        Function<String, KafkaCache> supplyCache,
        LongFunction<KafkaCacheRoute> supplyCacheRoute)
    {
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.bufferPool = context.bufferPool();
        this.signaler = context.signaler();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyNamespace = context::supplyNamespace;
        this.supplyLocalName = context::supplyLocalName;
        this.supplyBinding = supplyBinding;
        this.supplyDebitor = supplyDebitor;
        this.supplyCache = supplyCache;
        this.supplyCacheRoute = supplyCacheRoute;
        this.cursorFactory = new KafkaCacheCursorFactory(context.writeBuffer());
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
        final long affinity = begin.affinity();
        final long authorization = begin.authorization();

        assert (initialId & 0x0000_0000_0000_0001L) != 0L;

        final OctetsFW extension = begin.extension();
        final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
        assert beginEx != null && beginEx.typeId() == kafkaTypeId;
        final KafkaBeginExFW kafkaBeginEx = extension.get(kafkaBeginExRO::wrap);
        assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_FETCH;
        final KafkaFetchBeginExFW kafkaFetchBeginEx = kafkaBeginEx.fetch();
        final String16FW beginTopic = kafkaFetchBeginEx.topic();
        final KafkaOffsetFW progress = kafkaFetchBeginEx.partition();
        final ArrayFW<KafkaFilterFW> filters = kafkaFetchBeginEx.filters();
        final KafkaDeltaType deltaType = kafkaFetchBeginEx.deltaType().get();
        final String topicName = beginTopic.asString();

        MessageConsumer newStream = null;

        final KafkaBindingConfig binding = supplyBinding.apply(routeId);
        final KafkaRouteConfig resolved = binding != null ? binding.resolve(authorization, topicName) : null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;
            final int partitionId = progress.partitionId();
            final long partitionOffset = progress.partitionOffset();
            final KafkaCacheRoute cacheRoute = supplyCacheRoute.apply(resolvedId);
            final long partitionKey = cacheRoute.topicPartitionKey(topicName, partitionId);

            KafkaCacheClientFetchFanout fanout = cacheRoute.clientFetchFanoutsByTopicPartition.get(partitionKey);
            if (fanout == null)
            {
                final String cacheName =
                        String.format("%s.%s", supplyNamespace.apply(resolvedId), supplyLocalName.apply(resolvedId));
                final KafkaCache cache = supplyCache.apply(cacheName);
                final KafkaCacheTopic topic = cache.supplyTopic(topicName);
                final KafkaCachePartition partition = topic.supplyFetchPartition(partitionId);
                final long defaultOffset = resolved.with != null ?
                    resolved.with.defaultOffset.value() : KafkaOffsetType.HISTORICAL.value();
                final KafkaCacheClientFetchFanout newFanout =
                        new KafkaCacheClientFetchFanout(
                            resolvedId,
                            authorization,
                            affinity,
                            partition,
                            defaultOffset);

                cacheRoute.clientFetchFanoutsByTopicPartition.put(partitionKey, newFanout);
                fanout = newFanout;
            }

            final KafkaFilterCondition condition = cursorFactory.asCondition(filters);
            final long latestOffset = kafkaFetchBeginEx.partition().latestOffset();
            final KafkaOffsetType maximumOffset = KafkaOffsetType.valueOf((byte) latestOffset);
            final int leaderId = cacheRoute.leadersByPartitionId.get(partitionId);

            newStream = new KafkaCacheClientFetchStream(
                    fanout,
                    sender,
                    routeId,
                    initialId,
                    leaderId,
                    authorization,
                    partitionOffset,
                    condition,
                    maximumOffset,
                    deltaType)::onClientMessage;
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

    private void doData(
        MessageConsumer receiver,
        long routeId,
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
        Consumer<OctetsFW.Builder> extension)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
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
                .extension(extension)
                .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
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
        long authorization,
        Flyweight extension)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
               .routeId(routeId)
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

    final class KafkaCacheClientFetchFanout
    {
        private final long routeId;
        private final long authorization;
        private final KafkaCachePartition partition;
        private final List<KafkaCacheClientFetchStream> members;

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
        private long latestOffset;

        private KafkaCacheClientFetchFanout(
            long routeId,
            long authorization,
            long leaderId,
            KafkaCachePartition partition,
            long defaultOffset)
        {
            this.routeId = routeId;
            this.authorization = authorization;
            this.partition = partition;
            this.partitionOffset = defaultOffset;
            this.latestOffset = DEFAULT_LATEST_OFFSET;
            this.members = new ArrayList<>();
            this.leaderId = leaderId;
            this.receiver = NO_RECEIVER;
        }

        private void onClientFanoutMemberOpening(
            long traceId,
            KafkaCacheClientFetchStream member)
        {
            if (member.leaderId != leaderId)
            {
                doClientFanoutInitialAbortIfNecessary(traceId);
                doClientFanoutReplyResetIfNecessary(traceId);
                leaderId = member.leaderId;

                members.forEach(m -> m.cleanupClient(traceId, ERROR_NOT_LEADER_FOR_PARTITION));
                members.clear();
            }

            members.add(member);

            assert !members.isEmpty();

            doClientFanoutInitialBeginIfNecessary(traceId);

            if (KafkaState.initialOpened(state))
            {
                member.doClientInitialWindow(traceId, 0L, 0, 0, 0);
            }

            if (isFanoutReplyOpened())
            {
                member.doClientReplyBeginIfNecessary(traceId);
            }
        }

        private boolean isFanoutReplyOpened()
        {
            return KafkaState.replyOpened(state);
        }

        private void onClientFanoutMemberClosed(
            long traceId,
            KafkaCacheClientFetchStream member)
        {
            members.remove(member);

            if (members.isEmpty())
            {
                doClientFanoutInitialAbortIfNecessary(traceId);
                doClientFanoutReplyResetIfNecessary(traceId);
            }
        }

        private void doClientFanoutInitialBeginIfNecessary(
            long traceId)
        {
            if (KafkaState.closed(state))
            {
                state = 0;
            }

            if (!KafkaState.initialOpening(state))
            {
                doClientFanoutInitialBegin(traceId);
            }
        }

        private void doClientFanoutInitialBegin(
            long traceId)
        {
            assert state == 0;

            this.initialId = supplyInitialId.applyAsLong(routeId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.receiver = newStream(this::onClientFanoutMessage, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, leaderId,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .fetch(f -> f.topic(partition.topic())
                                     .partition(p -> p.partitionId(partition.id())
                                                      .partitionOffset(partitionOffset)
                                                      .latestOffset(latestOffset)))
                        .build()
                        .sizeof()));
            state = KafkaState.openingInitial(state);
        }

        private void doClientFanoutInitialAbortIfNecessary(
            long traceId)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                doClientFanoutInitialAbort(traceId);
            }
        }

        private void doClientFanoutInitialAbort(
            long traceId)
        {
            doAbort(receiver, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

            state = KafkaState.closedInitial(state);
        }

        private void onClientFanoutMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onClientFanoutReplyBegin(begin);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onClientFanoutReplyFlush(flush);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onClientFanoutReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onClientFanoutReplyAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onClientFanoutInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onClientFanoutInitialWindow(window);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onClientFanoutInitialSignal(signal);
                break;
            default:
                break;
            }
        }

        private void onClientFanoutInitialSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final int signalId = signal.signalId();

            assert signalId == SIGNAL_FANOUT_REPLY_WINDOW;

            doClientFanoutReplyWindow(traceId, 0, replyMax);
        }

        private void onClientFanoutReplyBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
            assert beginEx != null && beginEx.typeId() == kafkaTypeId;
            final KafkaBeginExFW kafkaBeginEx = extension.get(kafkaBeginExRO::wrap);
            assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_FETCH;
            final KafkaFetchBeginExFW kafkaFetchBeginEx = kafkaBeginEx.fetch();
            final KafkaOffsetFW partition = kafkaFetchBeginEx.partition();
            final int partitionId = partition.partitionId();
            final long partitionOffset = partition.partitionOffset();

            state = KafkaState.openedReply(state);

            assert partitionId == this.partition.id();
            assert partitionOffset >= 0 && partitionOffset >= this.partitionOffset;
            this.partitionOffset = partitionOffset;
            this.latestOffset = partition.latestOffset();

            members.forEach(s -> s.doClientReplyBeginIfNecessary(traceId));

            doClientFanoutReplyWindow(traceId, 0, bufferPool.slotCapacity());
        }

        private void onClientFanoutReplyFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();
            final KafkaFlushExFW kafkaFlushEx = extension.get(kafkaFlushExRO::wrap);
            final KafkaFetchFlushExFW kafkaFetchFlushEx = kafkaFlushEx.fetch();
            final KafkaOffsetFW partition = kafkaFetchFlushEx.partition();
            final long partitionOffset = partition.partitionOffset();
            final long latestOffset = partition.latestOffset();

            replySeq += reserved;

            assert replyAck <= replySeq;

            assert partitionOffset >= this.partitionOffset;
            this.partitionOffset = partitionOffset;
            this.latestOffset = latestOffset;

            members.forEach(s -> s.doClientReplyDataIfNecessary(traceId));

            // defer reply window credit until next tick
            assert reserved == SIZE_OF_FLUSH_WITH_EXTENSION;
            signaler.signalNow(routeId, initialId, SIGNAL_FANOUT_REPLY_WINDOW);
        }

        private void onClientFanoutReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            members.forEach(s -> s.doClientReplyEndIfNecessary(traceId));
            members.forEach(s -> s.doClientInitialResetIfNecessary(traceId, EMPTY_OCTETS));
            members.clear();

            state = KafkaState.closedReply(state);

            doClientFanoutInitialEndIfNecessary(traceId);
        }

        private void onClientFanoutReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            members.forEach(s -> s.doClientReplyAbortIfNecessary(traceId));
            members.forEach(s -> s.doClientInitialResetIfNecessary(traceId, EMPTY_OCTETS));
            members.clear();

            state = KafkaState.closedReply(state);

            doClientFanoutInitialAbortIfNecessary(traceId);
        }

        private void onClientFanoutInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final OctetsFW extension = reset.extension();

            members.forEach(s -> s.doClientInitialResetIfNecessary(traceId, extension));
            members.forEach(s -> s.doClientReplyAbortIfNecessary(traceId));
            members.clear();

            state = KafkaState.closedInitial(state);

            doClientFanoutReplyResetIfNecessary(traceId);
        }

        private void doClientFanoutInitialEndIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doClientFanoutInitialEnd(traceId);
            }
        }

        private void doClientFanoutInitialEnd(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doEnd(receiver, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);
        }

        private void onClientFanoutInitialWindow(
            WindowFW window)
        {
            if (!KafkaState.initialOpened(state))
            {
                final long traceId = window.traceId();

                state = KafkaState.openedInitial(state);

                members.forEach(s -> s.doClientInitialWindow(traceId, 0L, 0, 0, 0));
            }
        }

        private void doClientFanoutReplyResetIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doClientFanoutReplyReset(traceId);
            }
        }

        private void doClientFanoutReplyReset(
            long traceId)
        {
            state = KafkaState.closedReply(state);

            doReset(receiver, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
        }

        private void doClientFanoutReplyWindow(
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

                doWindow(receiver, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, 0L, 0);
            }
        }
    }

    private final class KafkaCacheClientFetchStream
    {
        private final KafkaCacheClientFetchFanout group;
        private final MessageConsumer sender;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final long leaderId;
        private final long authorization;
        private final KafkaCacheCursor cursor;
        private final KafkaDeltaType deltaType;
        private final KafkaOffsetType maximumOffset;

        private int state;

        private long replyDebitorIndex = NO_DEBITOR_INDEX;
        private BudgetDebitor replyDebitor;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyPad;
        private int replyMax;
        private int replyMin;

        private long replyBudgetId;

        private long initialOffset;
        private int messageOffset;
        private long initialGroupPartitionOffset;
        private long initialGroupLatestOffset;

        KafkaCacheClientFetchStream(
            KafkaCacheClientFetchFanout group,
            MessageConsumer sender,
            long routeId,
            long initialId,
            long leaderId,
            long authorization,
            long initialOffset,
            KafkaFilterCondition condition,
            KafkaOffsetType maximumOffset,
            KafkaDeltaType deltaType)
        {
            this.group = group;
            this.sender = sender;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.leaderId = leaderId;
            this.authorization = authorization;
            this.initialOffset = initialOffset;
            this.cursor = cursorFactory.newCursor(condition, deltaType);
            this.maximumOffset = maximumOffset;
            this.deltaType = deltaType;
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
            final long affinity = begin.affinity();

            if (affinity != leaderId)
            {
                cleanupClient(traceId, ERROR_NOT_LEADER_FOR_PARTITION);
            }
            else
            {
                state = KafkaState.openingInitial(state);

                group.onClientFanoutMemberOpening(traceId, this);
            }

        }

        private void onClientInitialEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedInitial(state);

            group.onClientFanoutMemberClosed(traceId, this);

            doClientReplyEndIfNecessary(traceId);
        }

        private void onClientInitialAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedInitial(state);

            group.onClientFanoutMemberClosed(traceId, this);

            doClientReplyAbortIfNecessary(traceId);
        }

        private void doClientInitialWindow(
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

                doWindow(sender, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, budgetId, minInitialPad);
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

            doReset(sender, routeId, initialId, initialSeq, initialAck, initialMax,
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

            this.initialGroupPartitionOffset = group.partitionOffset;
            this.initialGroupLatestOffset = group.latestOffset;

            if (initialOffset == OFFSET_LIVE)
            {
                this.initialOffset = group.latestOffset + 1;
            }
            else if (initialOffset == OFFSET_HISTORICAL)
            {
                final Node segmentNode = group.partition.seekNotBefore(0L);
                assert !segmentNode.sentinel();
                this.initialOffset = segmentNode.segment().baseOffset();
            }
            assert initialOffset >= 0;

            Node segmentNode = group.partition.seekNotAfter(initialOffset);
            if (segmentNode.sentinel())
            {
                segmentNode = segmentNode.next();
            }
            cursor.init(segmentNode, initialOffset, initialGroupLatestOffset);

            doBegin(sender, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, leaderId,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .fetch(f -> f.topic(group.partition.topic())
                                     .partition(p -> p.partitionId(group.partition.id())
                                                      .partitionOffset(cursor.offset)
                                                      .latestOffset(initialGroupLatestOffset))
                                     .deltaType(t -> t.set(deltaType)))
                        .build()
                        .sizeof()));
        }

        private void doClientReplyDataIfNecessary(
            long traceId)
        {
            assert !KafkaState.closing(state) :
                String.format("!replyClosing(%08x) [%016x] [%016x] [%016x] %s",
                        state, replyBudgetId, replyId, replyDebitorIndex, replyDebitor);

            while (KafkaState.replyOpened(state) &&
                !KafkaState.replyClosing(state) &&
                (replyMax - (int)(replySeq - replyAck)) >= replyPad &&
                cursor.offset <= group.partitionOffset)
            {
                final KafkaCacheEntryFW nextEntry = cursor.next(entryRO);

                if (nextEntry == null || nextEntry.offset$() > group.latestOffset)
                {
                    if (maximumOffset == HISTORICAL)
                    {
                        cursor.advance(group.partitionOffset + 1);
                    }
                    break;
                }

                final long descendantOffset = nextEntry.descendant();
                if (descendantOffset != -1L && descendantOffset <= initialGroupPartitionOffset)
                {
                    this.messageOffset = 0;

                    cursor.advance(nextEntry.offset$() + 1);
                    continue;
                }

                final long replySeqSnapshot = replySeq;

                doClientReplyData(traceId, nextEntry);

                if (replySeq == replySeqSnapshot ||
                    maximumOffset == HISTORICAL && cursor.offset > initialGroupLatestOffset)
                {
                    break;
                }
            }

            if (maximumOffset == HISTORICAL && cursor.offset > initialGroupLatestOffset)
            {
                doClientReplyEndIfNecessary(traceId);
            }
        }

        private void doClientReplyData(
            long traceId,
            KafkaCacheEntryFW nextEntry)
        {
            assert nextEntry != null;

            final long partitionOffset = nextEntry.offset$();
            final long timestamp = nextEntry.timestamp();
            final KafkaKeyFW key = nextEntry.key();
            final ArrayFW<KafkaHeaderFW> headers = nextEntry.headers();
            final long ancestor = nextEntry.ancestor();
            final OctetsFW value = nextEntry.value();
            final int remaining = value != null ? value.sizeof() - messageOffset : 0;
            assert remaining >= 0;
            final int lengthMin = Math.min(remaining, 1024);
            final int replyBudget = Math.max(replyMax - (int)(replySeq - replyAck), 0);
            final int reservedMax = Math.max(Math.min(remaining + replyPad, replyBudget), replyMin);
            final int reservedMin = Math.max(Math.min(lengthMin + replyPad, reservedMax), replyMin);
            final long latestOffset = group.latestOffset;

            assert partitionOffset >= cursor.offset : String.format("%d >= %d", partitionOffset, cursor.offset);

            flush:
            if (replyBudget >= reservedMin &&
                (reservedMin > replyPad || reservedMin == replyPad && remaining == 0))
            {
                int reserved = reservedMax;
                boolean claimed = false;
                if (replyDebitorIndex != NO_DEBITOR_INDEX)
                {
                    final int lengthMax = Math.min(reservedMax - replyPad, remaining);
                    final int deferredMax = remaining - lengthMax;
                    reserved = replyDebitor.claim(traceId, replyDebitorIndex, replyId, reservedMin, reservedMax, deferredMax);
                    claimed = reserved > 0;
                }

                if (reserved < replyPad || reserved == replyPad && value != null && remaining > 0)
                {
                    assert !claimed : String.format("reserved=%d replyBudget=%d replyPadg=%d messageOffset=%d " +
                                                    "reservedMin=%d reservedMax=%d %s",
                        reserved, replyBudget, replyPad, messageOffset, reservedMin, reservedMax, value);
                    break flush;
                }

                final int length = Math.min(reserved - replyPad, remaining);
                assert length >= 0 : String.format("%d >= 0", length);

                final int deferred = remaining - length;
                assert deferred >= 0 : String.format("%d >= 0", deferred);

                int flags = 0x00;
                if (messageOffset == 0)
                {
                    flags |= FLAG_INIT;
                }
                if (length == remaining)
                {
                    flags |= FLAG_FIN;
                }

                OctetsFW fragment = value;
                if (flags != (FLAG_INIT | FLAG_FIN))
                {
                    final int fragmentOffset = value.offset() + messageOffset;
                    final int fragmentLimit = fragmentOffset + length;
                    fragment = valueFragmentRO.wrap(value.buffer(), fragmentOffset, fragmentLimit);
                }

                final int partitionId = group.partition.id();
                switch (flags)
                {
                case FLAG_INIT | FLAG_FIN:
                    doClientReplyDataFull(traceId, timestamp, key, headers, deltaType, ancestor, fragment,
                                          reserved, flags, partitionId, partitionOffset, latestOffset);
                    break;
                case FLAG_INIT:
                    doClientReplyDataInit(traceId, deferred, timestamp, key, deltaType, ancestor, fragment,
                                          reserved, length, flags, partitionId, partitionOffset, latestOffset);
                    break;
                case FLAG_NONE:
                    doClientReplyDataNone(traceId, fragment, reserved, length, flags);
                    break;
                case FLAG_FIN:
                    doClientReplyDataFin(traceId, headers, deltaType, ancestor, fragment,
                                         reserved, length, flags, partitionId, partitionOffset, latestOffset);
                    break;
                }

                if ((flags & FLAG_FIN) == 0x00)
                {
                    this.messageOffset += length;
                }
                else
                {
                    this.messageOffset = 0;

                    cursor.advance(partitionOffset + 1);
                }
            }
        }

        private void doClientReplyDataFull(
            long traceId,
            long timestamp,
            KafkaKeyFW key,
            ArrayFW<KafkaHeaderFW> headers,
            KafkaDeltaType deltaType,
            long ancestorOffset,
            OctetsFW value,
            int reserved,
            int flags,
            int partitionId,
            long partitionOffset,
            long latestOffset)
        {
            doData(sender, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, flags, replyBudgetId, reserved, value,
                ex -> ex.set((b, o, l) -> kafkaDataExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .fetch(f -> f.timestamp(timestamp)
                                     .partition(p -> p.partitionId(partitionId)
                                                      .partitionOffset(partitionOffset)
                                                      .latestOffset(latestOffset))
                                     .key(k -> k.length(key.length())
                                                .value(key.value()))
                                     .delta(d -> d.type(t -> t.set(deltaType))
                                                              .ancestorOffset(ancestorOffset))
                                     .headers(hs -> headers.forEach(h -> hs.item(i -> i.nameLen(h.nameLen())
                                                                                       .name(h.name())
                                                                                       .valueLen(h.valueLen())
                                                                                       .value(h.value())))))
                        .build()
                        .sizeof()));

            replySeq += reserved;

            assert replyAck <= replySeq;
        }

        private void doClientReplyDataInit(
            long traceId,
            int deferred,
            long timestamp,
            KafkaKeyFW key,
            KafkaDeltaType deltaType,
            long ancestorOffset,
            OctetsFW fragment,
            int reserved,
            int length,
            int flags,
            int partitionId,
            long partitionOffset,
            long latestOffset)
        {
            doData(sender, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, flags, replyBudgetId, reserved, fragment,
                ex -> ex.set((b, o, l) -> kafkaDataExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .fetch(f -> f.deferred(deferred)
                                     .timestamp(timestamp)
                                     .partition(p -> p.partitionId(partitionId)
                                                      .partitionOffset(partitionOffset)
                                                      .latestOffset(latestOffset))
                                     .key(k -> k.length(key.length())
                                                .value(key.value()))
                                     .delta(d -> d.type(t -> t.set(deltaType))
                                                  .ancestorOffset(ancestorOffset)))
                        .build()
                        .sizeof()));

            replySeq += reserved;

            assert replyAck <= replySeq;
        }

        private void doClientReplyDataNone(
            long traceId,
            OctetsFW fragment,
            int reserved,
            int length,
            int flags)
        {
            doData(sender, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, flags, replyBudgetId, reserved, fragment, EMPTY_EXTENSION);

            replySeq += reserved;

            assert replyAck <= replySeq;
        }

        private void doClientReplyDataFin(
            long traceId,
            ArrayFW<KafkaHeaderFW> headers,
            KafkaDeltaType deltaType,
            long ancestorOffset,
            OctetsFW fragment,
            int reserved,
            int length,
            int flags,
            int partitionId,
            long partitionOffset,
            long latestOffset)
        {
            doData(sender, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, flags, replyBudgetId, reserved, fragment,
                ex -> ex.set((b, o, l) -> kafkaDataExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .fetch(f -> f.partition(p -> p.partitionId(partitionId)
                                                      .partitionOffset(partitionOffset)
                                                      .latestOffset(latestOffset))
                                     .delta(d -> d.type(t -> t.set(deltaType))
                                                  .ancestorOffset(ancestorOffset))
                                     .headers(hs -> headers.forEach(h -> hs.item(i -> i.nameLen(h.nameLen())
                                                                                       .name(h.name())
                                                                                       .valueLen(h.valueLen())
                                                                                       .value(h.value())))))
                        .build()
                        .sizeof()));

            replySeq += reserved;

            assert replyAck <= replySeq;
        }

        private void doClientReplyEnd(
            long traceId)
        {
            state = KafkaState.closedReply(state);
            doEnd(sender, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
            cleanupDebitorIfNecessary();
            cursor.close();
        }

        private void doClientReplyAbort(
            long traceId)
        {
            state = KafkaState.closedReply(state);
            doAbort(sender, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
            cleanupDebitorIfNecessary();
            cursor.close();
        }

        private void doClientReplyEndIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doClientReplyEnd(traceId);
            }

            state = KafkaState.closedReply(state);
            cleanupDebitorIfNecessary();
            cursor.close();
        }

        private void doClientReplyAbortIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doClientReplyAbort(traceId);
            }

            state = KafkaState.closedReply(state);
            cleanupDebitorIfNecessary();
            cursor.close();
        }

        private void onClientReplyWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int minimum = window.minimum();

            assert replyBudgetId == 0L || replyBudgetId == budgetId :
                String.format("%d == 0 || %d == %d)", replyBudgetId, replyBudgetId, budgetId);

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            this.replyAck = acknowledge;
            this.replyMax = maximum;
            this.replyPad = padding;
            this.replyMin = minimum;
            this.replyBudgetId = budgetId;

            if (!KafkaState.replyOpened(state))
            {
                state = KafkaState.openedReply(state);

                if (replyBudgetId != NO_BUDGET_ID && replyDebitorIndex == NO_DEBITOR_INDEX)
                {
                    replyDebitor = supplyDebitor.apply(replyBudgetId);
                    replyDebitorIndex = replyDebitor.acquire(replyBudgetId, replyId, this::doClientReplyDataIfNecessary);
                    assert replyDebitorIndex != NO_DEBITOR_INDEX;
                }
            }

            if (group.isFanoutReplyOpened())
            {
                doClientReplyDataIfNecessary(traceId);
            }
        }

        private void onClientReplyReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedReply(state);
            cleanupDebitorIfNecessary();
            cursor.close();

            group.onClientFanoutMemberClosed(traceId, this);

            doClientInitialResetIfNecessary(traceId, EMPTY_OCTETS);
        }

        private void cleanupDebitorIfNecessary()
        {
            if (replyDebitor != null && replyDebitorIndex != NO_DEBITOR_INDEX)
            {
                replyDebitor.release(replyBudgetId, replyId);
                replyDebitorIndex = NO_DEBITOR_INDEX;
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
    }
}
