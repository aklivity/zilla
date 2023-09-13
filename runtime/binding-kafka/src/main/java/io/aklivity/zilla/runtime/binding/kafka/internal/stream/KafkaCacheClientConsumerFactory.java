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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.IntHashSet;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaConsumerAssignmentFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaConsumerBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaConsumerDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaTopicPartitionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class KafkaCacheClientConsumerFactory implements BindingHandler
{
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};


    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();
    private final KafkaResetExFW kafkaResetExRO = new KafkaResetExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();

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

    private final Object2ObjectHashMap<String, KafkaCacheClientConsumerFan> clientConsumerFansByConsumer;

    public KafkaCacheClientConsumerFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding)
    {
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.bufferPool = context.bufferPool();
        this.signaler = context.signaler();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.supplyNamespace = context::supplyNamespace;
        this.supplyLocalName = context::supplyLocalName;
        this.supplyBinding = supplyBinding;
        this.clientConsumerFansByConsumer = new Object2ObjectHashMap<>();
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
        final long authorization = begin.authorization();
        final long affinity = begin.affinity();

        assert (initialId & 0x0000_0000_0000_0001L) != 0L;

        final OctetsFW extension = begin.extension();
        final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
        assert beginEx != null && beginEx.typeId() == kafkaTypeId;
        final KafkaBeginExFW kafkaBeginEx = extension.get(kafkaBeginExRO::wrap);
        assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_CONSUMER;
        final KafkaConsumerBeginExFW kafkaConsumerBeginEx = kafkaBeginEx.consumer();
        final String groupId = kafkaConsumerBeginEx.groupId().asString();
        final String topic = kafkaConsumerBeginEx.topic().asString();
        final String consumerId = kafkaConsumerBeginEx.consumerId().asString();
        final int timeout = kafkaConsumerBeginEx.timeout();
        final IntHashSet partitions = new IntHashSet();
        kafkaConsumerBeginEx.partitionIds().forEach(p -> partitions.add(p.partitionId()));

        MessageConsumer newStream = null;

        final KafkaBindingConfig binding = supplyBinding.apply(routedId);
        final KafkaRouteConfig resolved = binding != null ? binding.resolve(authorization, topic, groupId) : null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;

            String fanKey = String.format("%s-%s-%s-%d", groupId, topic, consumerId, resolvedId);
            KafkaCacheClientConsumerFan fan = clientConsumerFansByConsumer.get(fanKey);

            if (fan == null)
            {
                KafkaCacheClientConsumerFan newFan =
                     new KafkaCacheClientConsumerFan(routedId, resolvedId, authorization, groupId,
                         topic, consumerId, partitions, timeout);
                fan = newFan;
                clientConsumerFansByConsumer.put(fanKey, fan);
            }

            newStream = new KafkaCacheClientConsumerStream(
                fan,
                sender,
                originId,
                routedId,
                initialId,
                affinity,
                authorization
                )::onConsumerMessage;
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
        long authorization)
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
                .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    final class KafkaCacheClientConsumerFan
    {
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final String groupId;
        private final String topic;
        private final String consumerId;
        private final int timeout;
        private final List<KafkaCacheClientConsumerStream> members;
        private final IntHashSet partitions;
        private final IntHashSet assignedPartitions;
        private final Object2ObjectHashMap<String, IntHashSet> assignments;

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


        private KafkaCacheClientConsumerFan(
            long originId,
            long routedId,
            long authorization,
            String groupId,
            String topic,
            String consumerId,
            IntHashSet partitions,
            int timeout)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.authorization = authorization;
            this.groupId = groupId;
            this.topic = topic;
            this.consumerId = consumerId;
            this.partitions = partitions;
            this.timeout = timeout;
            this.members = new ArrayList<>();
            this.assignedPartitions = new IntHashSet();
            this.assignments = new Object2ObjectHashMap<>();
        }

        private void onConsumerFanMemberOpening(
            long traceId,
            KafkaCacheClientConsumerStream member)
        {
            members.add(member);

            assert !members.isEmpty();

            doConsumerFanInitialBeginIfNecessary(traceId);

            if (KafkaState.initialOpened(state))
            {
                member.doConsumerInitialWindow(traceId, 0L, 0, 0, 0);
            }

            if (KafkaState.replyOpened(state))
            {
                member.doConsumerReplyBeginIfNecessary(traceId);
            }
        }

        private void onConsumerFanMemberOpened(
            long traceId,
            KafkaCacheClientConsumerStream member)
        {
            if (!assignedPartitions.isEmpty())
            {
                final KafkaDataExFW kafkaDataEx =
                    kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .consumer(m -> m
                            .partitions(p ->
                                assignedPartitions.forEach(ap -> p.item(np -> np.partitionId(ap))))
                            .assignments(a ->
                                assignments.forEach((k, v) -> a.item(na -> na.consumerId(k)
                                    .partitions(pa ->
                                        v.forEach(pi -> pa.item(pai -> pai.partitionId(pi))))))))
                        .build();
                member.doConsumerReplyDataIfNecessary(traceId, kafkaDataEx);
            }
        }

        private void onConsumerFanMemberClosed(
            long traceId,
            KafkaCacheClientConsumerStream member)
        {
            members.remove(member);

            if (members.isEmpty())
            {
                doConsumerFanInitialEndIfNecessary(traceId);
                doConsumerFanReplyResetIfNecessary(traceId);
            }
        }

        private void doConsumerFanInitialBeginIfNecessary(
            long traceId)
        {
            if (KafkaState.closed(state))
            {
                state = 0;
            }

            if (!KafkaState.initialOpening(state))
            {
                doConsumerFanInitialBegin(traceId);
            }
        }

        private void doConsumerFanInitialBegin(
            long traceId)
        {
            assert state == 0;

            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.receiver = newStream(this::onConsumerFanMessage,
                originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .consumer(m -> m.groupId(groupId)
                            .consumerId(consumerId)
                            .timeout(timeout)
                            .topic(topic)
                            .partitionIds(p -> partitions.forEach(tp -> p.item(np -> np.partitionId(tp.intValue())))))
                        .build()
                        .sizeof()));
            state = KafkaState.openingInitial(state);
        }

        private void doConsumerFanInitialEndIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doConsumerFanInitialEnd(traceId);
            }
        }

        private void doConsumerFanInitialEnd(
            long traceId)
        {
            doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

            state = KafkaState.closedInitial(state);
        }

        private void doConsumerFanInitialAbortIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doConsumerFanInitialAbort(traceId);
            }
        }

        private void doConsumerFanInitialAbort(
            long traceId)
        {
            doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

            state = KafkaState.closedInitial(state);
        }

        private void onConsumerFanInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final OctetsFW extension = reset.extension();

            final KafkaResetExFW kafkaResetEx = extension.get(kafkaResetExRO::tryWrap);
            final int error = kafkaResetEx != null ? kafkaResetEx.error() : -1;

            state = KafkaState.closedInitial(state);

            doConsumerFanReplyResetIfNecessary(traceId);

            members.forEach(s -> s.doConsumerInitialResetIfNecessary(traceId));
        }

        private void onConsumerFanInitialWindow(
            WindowFW window)
        {
            if (!KafkaState.initialOpened(state))
            {

                final long traceId = window.traceId();

                state = KafkaState.openedInitial(state);

                members.forEach(s -> s.doConsumerInitialWindow(traceId, 0L, 0, 0, 0));
            }
        }

        private void onConsumerFanMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onConsumerFanReplyBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onConsumerFanReplyData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onConsumerFanReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onConsumerFanReplyAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onConsumerFanInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onConsumerFanInitialWindow(window);
                break;
            default:
                break;
            }
        }

        private void onConsumerFanReplyBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = KafkaState.openingReply(state);

            members.forEach(s -> s.doConsumerReplyBeginIfNecessary(traceId));

            doConsumerFanReplyWindow(traceId, 0, bufferPool.slotCapacity());
        }

        private void onConsumerFanReplyData(
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
            assert replySeq <= replyAck + replyMax;

            final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
            final KafkaDataExFW kafkaDataEx = dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;
            assert kafkaDataEx == null || kafkaDataEx.kind() == KafkaBeginExFW.KIND_CONSUMER;
            final KafkaConsumerDataExFW kafkaConsumerDataEx = kafkaDataEx != null ? kafkaDataEx.consumer() : null;

            if (kafkaConsumerDataEx != null)
            {
                final Array32FW<KafkaTopicPartitionFW> newPartitions = kafkaConsumerDataEx.partitions();
                final Array32FW<KafkaConsumerAssignmentFW> newAssignments = kafkaConsumerDataEx.assignments();

                assignedPartitions.clear();
                newPartitions.forEach(p -> this.assignedPartitions.add(p.partitionId()));

                assignments.clear();
                newAssignments.forEach(a ->
                {
                    IntHashSet partitions = new IntHashSet();
                    a.partitions().forEach(p -> partitions.add(p.partitionId()));
                    assignments.put(a.consumerId().asString(), partitions);
                });

                members.forEach(s -> s.doConsumerReplyDataIfNecessary(traceId, kafkaDataEx));
            }

            doConsumerFanReplyWindow(traceId, 0, replyMax);
        }

        private void onConsumerFanReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedReply(state);

            doConsumerFanInitialEndIfNecessary(traceId);

            members.forEach(s -> s.doConsumerReplyEndIfNecessary(traceId));
        }

        private void onConsumerFanReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedReply(state);

            doConsumerFanInitialAbortIfNecessary(traceId);

            members.forEach(s -> s.doConsumerReplyAbortIfNecessary(traceId));
        }

        private void doConsumerFanReplyResetIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doConsumerFanReplyReset(traceId);
            }
        }

        private void doConsumerFanReplyReset(
            long traceId)
        {
            doReset(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);

            state = KafkaState.closedReply(state);
        }

        private void doConsumerFanReplyWindow(
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

                doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, 0L, 0);
            }
        }
    }

    private final class KafkaCacheClientConsumerStream
    {
        private final KafkaCacheClientConsumerFan fan;
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

        private long replyBudgetId;

        KafkaCacheClientConsumerStream(
            KafkaCacheClientConsumerFan fan,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization)
        {
            this.fan = fan;
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
        }

        private void onConsumerMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onConsumerInitialBegin(begin);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onConsumerInitialEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onConsumerInitialAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onConsumerReplyWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onConsumerReplyReset(reset);
                break;
            default:
                break;
            }
        }

        private void onConsumerInitialBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = KafkaState.openingInitial(state);

            fan.onConsumerFanMemberOpening(traceId, this);
        }

        private void onConsumerInitialEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedInitial(state);

            fan.onConsumerFanMemberClosed(traceId, this);

            doConsumerReplyEndIfNecessary(traceId);
        }

        private void onConsumerInitialAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedInitial(state);

            fan.onConsumerFanMemberClosed(traceId, this);

            doConsumerReplyAbortIfNecessary(traceId);
        }

        private void doConsumerInitialResetIfNecessary(
            long traceId)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                doConsumerInitialReset(traceId);
            }

            state = KafkaState.closedInitial(state);
        }

        private void doConsumerInitialReset(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
        }

        private void doConsumerInitialWindow(
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

                doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, budgetId, minInitialPad);
            }
        }

        private void doConsumerReplyBeginIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyOpening(state))
            {
                doConsumerReplyBegin(traceId);
            }
        }

        private void doConsumerReplyBegin(
            long traceId)
        {
            state = KafkaState.openingReply(state);

            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, EMPTY_EXTENSION);
        }

        private void doConsumerReplyDataIfNecessary(
            long traceId,
            Flyweight extension)
        {
            if (KafkaState.replyOpened(state))
            {
                doConsumerReplyData(traceId, extension);
            }
        }

        private void doConsumerReplyData(
            long traceId,
            Flyweight extension)
        {
            final int reserved = replyPad;

            doDataNull(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, replyBudgetId, reserved, extension);

            replySeq += reserved;
        }

        private void doConsumerReplyEndIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doConsumerReplyEnd(traceId);
            }

            state = KafkaState.closedReply(state);
        }

        private void doConsumerReplyEnd(
            long traceId)
        {
            state = KafkaState.closedReply(state);
            doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
        }

        private void doConsumerReplyAbortIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doConsumerReplyAbort(traceId);
            }

            state = KafkaState.closedReply(state);
        }

        private void doConsumerReplyAbort(
            long traceId)
        {
            state = KafkaState.closedReply(state);
            doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
        }

        private void onConsumerReplyReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedInitial(state);

            fan.onConsumerFanMemberClosed(traceId, this);

            doConsumerInitialResetIfNecessary(traceId);
        }

        private void onConsumerReplyWindow(
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

            if (!KafkaState.replyOpened(state))
            {
                state = KafkaState.openedReply(state);

                final long traceId = window.traceId();
                fan.onConsumerFanMemberOpened(traceId, this);
            }
        }
    }
}
