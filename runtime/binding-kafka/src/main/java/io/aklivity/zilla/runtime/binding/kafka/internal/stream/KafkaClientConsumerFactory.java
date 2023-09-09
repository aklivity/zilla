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
import io.aklivity.zilla.runtime.binding.kafka.internal.types.rebalance.MemberAssignmentFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.rebalance.TopicAssignmentFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaConsumerBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaGroupFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaGroupMemberMetadataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaGroupTopicMetadataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class KafkaClientConsumerFactory implements BindingHandler
{
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};
    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer();
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final FlushFW flushRO = new FlushFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaFlushExFW kafkaFlushExRO = new KafkaFlushExFW();
    private final KafkaGroupMemberMetadataFW kafkaGroupMemberMetadataRO = new KafkaGroupMemberMetadataFW();
    private final Array32FW<KafkaGroupTopicMetadataFW> groupTopicsMetadataRO =
        new Array32FW<>(new KafkaGroupTopicMetadataFW());
    private final Array32FW<TopicAssignmentFW> topicAssignmentsRO =
        new Array32FW<>(new TopicAssignmentFW());

    private final Array32FW.Builder<MemberAssignmentFW.Builder, MemberAssignmentFW> memberAssignmentRW =
        new Array32FW.Builder<>(new MemberAssignmentFW.Builder(), new MemberAssignmentFW());
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
    private final KafkaGroupMemberMetadataFW.Builder kafkaGroupMemberMetadataRW = new KafkaGroupMemberMetadataFW.Builder();

    private final int kafkaTypeId;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BufferPool bufferPool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongFunction<KafkaBindingConfig> supplyBinding;
    private final Object2ObjectHashMap<String, KafkaClientConsumerFanout> clientConsumerFansByGroupId;

    public KafkaClientConsumerFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding)
    {
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.bufferPool = context.bufferPool();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyBinding = supplyBinding;
        this.clientConsumerFansByGroupId = new Object2ObjectHashMap<>();
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
        final long traceId = begin.traceId();
        final long authorization = begin.authorization();
        final long affinity = begin.affinity();

        assert (initialId & 0x0000_0000_0000_0001L) != 0L;

        final OctetsFW extension = begin.extension();
        final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
        assert beginEx != null && beginEx.typeId() == kafkaTypeId;
        final KafkaBeginExFW kafkaBeginEx = extension.get(kafkaBeginExRO::tryWrap);
        assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_CONSUMER;
        final KafkaConsumerBeginExFW kafkaConsumerBeginEx = kafkaBeginEx.consumer();
        final String groupId = kafkaConsumerBeginEx.groupId().asString();
        final String topic = kafkaConsumerBeginEx.topic().asString();
        final String consumerId = kafkaConsumerBeginEx.consumerId().asString();
        final int timeout = kafkaConsumerBeginEx.timeout();
        final List<Integer> partitions = new ArrayList<>();
        kafkaConsumerBeginEx.partitionIds().forEach(p -> partitions.add(p.partitionId()));

        MessageConsumer newStream = null;

        final KafkaBindingConfig binding = supplyBinding.apply(routedId);
        final KafkaRouteConfig resolved = binding != null ? binding.resolve(authorization, topic, groupId) : null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;

            KafkaClientConsumerFanout fanout = clientConsumerFansByGroupId.get(groupId);

            if (fanout == null)
            {
                KafkaClientConsumerFanout newFanout =
                    new KafkaClientConsumerFanout(routedId, resolvedId, authorization, consumerId, groupId, timeout);
                fanout = newFanout;
                clientConsumerFansByGroupId.put(groupId, fanout);
            }

            newStream = new KafkaClientConsumerStream(
                fanout,
                sender,
                originId,
                routedId,
                initialId,
                affinity,
                authorization,
                topic,
                partitions)::onConsumerMessage;
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
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        int flags,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit,
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
            .payload(buffer, offset, limit)
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
        int flags,
        int reserved,
        OctetsFW payload,
        Consumer<OctetsFW.Builder> extension)
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
            .extension(extension)
            .build();

        receiver.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());
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

    final class KafkaClientConsumerFanout
    {
        private final String consumerId;
        private final String groupId;
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final int timeout;
        private final List<KafkaClientConsumerStream> streams;
        private final Object2ObjectHashMap<String, String> members;
        private final Object2ObjectHashMap<String, IntHashSet> partitionsByTopic;
        private final Object2ObjectHashMap<String, List<TopicPartition>> assignment;

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
        private int replyPad;
        private String leaderId;
        private String memberId;


        private KafkaClientConsumerFanout(
            long originId,
            long routedId,
            long authorization,
            String consumerId,
            String groupId,
            int timeout)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.authorization = authorization;
            this.consumerId = consumerId;
            this.groupId = groupId;
            this.timeout = timeout;
            this.streams = new ArrayList<>();
            this.members = new Object2ObjectHashMap<>();
            this.partitionsByTopic = new Object2ObjectHashMap<>();
            this.assignment = new Object2ObjectHashMap<>();
        }

        private void doConsumerInitialBegin(
            long traceId)
        {
            if (KafkaState.closed(state))
            {
                state = 0;
            }

            if (!KafkaState.initialOpening(state))
            {
                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);

                KafkaGroupMemberMetadataFW metadata = kafkaGroupMemberMetadataRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .consumerId(consumerId)
                    .topics(t -> streams.forEach(s -> t.item(tp -> tp
                        .topic(s.topic)
                        .partitions(p -> s.partitions.forEach(sp ->
                            p.item(gtp -> gtp.partitionId(sp)))))))
                    .build();

                this.receiver = newStream(this::onConsumerMessage,
                    originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, 0L,
                    ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .group(g ->
                            g.groupId(groupId)
                            .protocol("highlander")
                            .timeout(timeout)
                            .metadataLen(metadata.sizeof())
                            .metadata(metadata.buffer(), 0, metadata.sizeof()))
                        .build().sizeof()));
                state = KafkaState.openingInitial(state);
            }
        }

        private void doConsumerInitialData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            DirectBuffer buffer,
            int offset,
            int limit,
            Flyweight extension)
        {
            doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, flags, reserved, buffer, offset, limit, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doConsumerInitialFlush(
            long traceId,
            Consumer<OctetsFW.Builder> extension)
        {
            doFlush(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, initialBud, 0, extension);
        }

        private void doConsumerInitialEnd(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

                state = KafkaState.closedInitial(state);
            }
        }

        private void doConsumerInitialAbort(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

                state = KafkaState.closedInitial(state);
            }
        }

        private void onConsumerInitialReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= this.initialAck;

            this.initialAck = acknowledge;
            state = KafkaState.closedInitial(state);

            assert this.initialAck <= this.initialSeq;

            streams.forEach(m -> m.doConsumerInitialReset(traceId));

            doConsumerReplyReset(traceId);
        }


        private void onConsumerInitialWindow(
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
            assert acknowledge >= this.initialAck;
            assert maximum >= this.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            initialBud = budgetId;
            state = KafkaState.openedInitial(state);

            assert initialAck <= initialSeq;

            streams.forEach(m -> m.doConsumerInitialWindow(authorization, traceId, budgetId, padding));
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
                onConsumerReplyBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onConsumerReplyData(data);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onConsumerReplyFlush(flush);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onConsumerReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onConsumerReplyAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onConsumerInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onConsumerInitialWindow(window);
                break;
            default:
                break;
            }
        }

        private void onConsumerReplyBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = KafkaState.openingReply(state);

            streams.forEach(m -> m.doConsumerReplyBegin(traceId, begin.extension()));
        }

        private void onConsumerReplyFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorizationId = flush.authorization();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;
            replyAck = replySeq;

            assert replyAck <= replySeq;
            assert replySeq <= replyAck + replyMax;

            final KafkaFlushExFW flushEx = extension.get(kafkaFlushExRO::tryWrap);

            if (flushEx != null)
            {
                KafkaGroupFlushExFW kafkaGroupFlushEx = flushEx.group();

                leaderId = kafkaGroupFlushEx.leaderId().asString();
                memberId = kafkaGroupFlushEx.memberId().asString();

                partitionsByTopic.clear();
                members.clear();

                kafkaGroupFlushEx.members().forEach(m ->
                {
                    final OctetsFW metadata = m.metadata();
                    final KafkaGroupMemberMetadataFW groupMetadata = kafkaGroupMemberMetadataRO
                        .wrap(metadata.buffer(), metadata.offset(), metadata.limit());
                    final String consumerId = kafkaGroupMemberMetadataRO.consumerId().asString();

                    groupMetadata.topics().forEach(mt ->
                    {
                        final String mId = m.id().asString();
                        members.put(mId, consumerId);

                        final String topic = mt.topic().asString();
                        IntHashSet partitions = partitionsByTopic.computeIfAbsent(topic, s -> new IntHashSet());
                        mt.partitions().forEach(p -> partitions.add(p.partitionId()));
                    });

                });
            }

            doPartitionAssignment(traceId, authorization);
        }

        private void onConsumerReplyData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorizationId = data.authorization();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;
            assert replySeq <= replyAck + replyMax;

            Array32FW<TopicAssignmentFW> topicAssignments = topicAssignmentsRO
                .wrap(payload.value(), 0, payload.sizeof());

            topicAssignments.forEach(ta ->
            {
                KafkaClientConsumerStream stream =
                    streams.stream().filter(s -> s.topic.equals(ta.topic().asString())).findFirst().get();

                stream.doConsumerReplyData(traceId, flags, replyPad, EMPTY_OCTETS,
                    ex -> ex.set((b, o, l) -> kafkaDataExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .consumer(c -> c.partitions(p -> ta
                                .partitions()
                                .forEach(np -> p.item(tp -> tp.partitionId(np.partitionId()))))
                            .assignments(a -> ta.userdata().forEach(u ->
                                a.item(ua -> ua.consumerId(u.consumerId()).partitions(p -> u.partitions()
                                    .forEach(np -> p.item(tp -> tp.partitionId(np.partitionId()))))))))
                        .build()
                        .sizeof()));
            });
        }

        private void onConsumerReplyEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = KafkaState.closedReply(state);

            assert replyAck <= replySeq;

            streams.forEach(s -> s.doConsumerReplyEnd(traceId));
            doConsumerInitialEnd(traceId);
        }

        private void onConsumerReplyAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = KafkaState.closedReply(state);

            assert replyAck <= replySeq;

            streams.forEach(s -> s.cleanup(traceId));

            doConsumerInitialAbort(traceId);
        }

        private void doConsumerReplyReset(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doReset(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);

                state = KafkaState.closedReply(state);
            }
        }

        private void doConsumerReplyWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            replyAck = Math.max(replyAck - replyPad, 0);

            doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding + replyPad);
        }

        private void doPartitionAssignment(
            long traceId,
            long authorization)
        {
            if (memberId.equals(leaderId))
            {
                int memberSize = members.size();
                partitionsByTopic.forEach((t, p) ->
                {
                    final int partitionSize = p.size();
                    final int numberOfPartitionsPerMember = partitionSize / memberSize;
                    final int extraPartition = partitionSize % memberSize;

                    int partitionIndex = 0;
                    int newPartitionPerTopic = numberOfPartitionsPerMember + extraPartition;

                    for (String member : members.keySet())
                    {
                        String consumerId = members.get(member);
                        List<TopicPartition> topicPartitions = assignment.computeIfAbsent(
                            member, tp -> new ArrayList<>());
                        List<Integer> partitions = new ArrayList<>();

                        for (; partitionIndex < newPartitionPerTopic; partitionIndex++)
                        {
                            partitions.add(p.iterator().next());
                        }
                        topicPartitions.add(new TopicPartition(consumerId, t, partitions));

                        newPartitionPerTopic += numberOfPartitionsPerMember;
                    }
                });
            }

            doMemberAssigment(traceId, authorization);
        }

        private void doMemberAssigment(
            long traceId,
            long authorization)
        {
            if (!assignment.isEmpty())
            {
                Array32FW<MemberAssignmentFW> assignment = memberAssignmentRW
                    .wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                    .item(ma -> this.assignment.forEach((k, v) ->
                        ma.memberId(k)
                            .assignments(ta -> v.forEach(tp -> ta.item(i ->
                                i.topic(tp.topic)
                                .partitions(p -> tp.partitions.forEach(t -> p.item(tpa -> tpa.partitionId(t))))
                                .userdata(u ->
                                    this.assignment.forEach((ak, av) ->
                                        av.stream().filter(atp -> atp.topic.equals(tp.topic)).forEach(at ->
                                            u.item(ud -> ud
                                                .consumerId(at.consumerId)
                                                .partitions(pt -> at.partitions.forEach(up ->
                                                    pt.item(pi -> pi.partitionId(up))))))))
                            )))))
                    .build();

                doConsumerInitialData(traceId, authorization, initialBud, memberAssignmentRW.sizeof(), 3,
                    assignment.buffer(), assignment.offset(), assignment.sizeof(), EMPTY_OCTETS);
            }
            else
            {
                doConsumerInitialData(traceId, authorization, initialBud, memberAssignmentRW.sizeof(), 3,
                    EMPTY_OCTETS.buffer(), EMPTY_OCTETS.offset(), EMPTY_OCTETS.sizeof(), EMPTY_OCTETS);
            }
        }
    }

    final class KafkaClientConsumerStream
    {
        private final KafkaClientConsumerFanout fanout;
        private final MessageConsumer sender;
        private final String topic;
        private final List<Integer> partitions;
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
        private int replyCap;

        KafkaClientConsumerStream(
            KafkaClientConsumerFanout fanout,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization,
            String topic,
            List<Integer> partitions)
        {
            this.fanout = fanout;
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.topic = topic;
            this.partitions = partitions;
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
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onConsumerInitialData(data);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onConsumerInitialFlush(flush);
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
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            state = KafkaState.openingInitial(state);

            assert initialAck <= initialSeq;

            fanout.streams.add(this);

            fanout.doConsumerInitialBegin(traceId);
        }

        private void onConsumerInitialData(
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
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;
        }

        private void onConsumerInitialEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = KafkaState.closedInitial(state);

            assert initialAck <= initialSeq;
        }

        private void onConsumerInitialFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = KafkaState.closedInitial(state);

            assert initialAck <= initialSeq;
        }

        private void onConsumerInitialAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = KafkaState.closedInitial(state);

            assert initialAck <= initialSeq;

            doConsumerReplyAbort(traceId);
            fanout.streams.remove(this);
        }

        private void doConsumerInitialReset(
            long traceId)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                state = KafkaState.closedInitial(state);

                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }

            state = KafkaState.closedInitial(state);
        }

        private void doConsumerInitialWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding)
        {
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding);
        }

        private void doConsumerReplyBegin(
            long traceId,
            OctetsFW extension)
        {
            state = KafkaState.openingReply(state);

            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, extension);
        }

        private void doConsumerReplyData(
            long traceId,
            int flag,
            int reserved,
            OctetsFW payload,
            Consumer<OctetsFW.Builder> extension)
        {
            doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, replyBud, flag, reserved, payload, extension);

            replySeq += reserved;
        }

        private void doConsumerReplyEnd(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
            }

            state = KafkaState.closedReply(state);
        }

        private void doConsumerReplyAbort(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
            }

            state = KafkaState.closedReply(state);
        }

        private void onConsumerReplyReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            state = KafkaState.closedReply(state);

            assert replyAck <= replySeq;

            cleanup(traceId);
        }

        private void onConsumerReplyWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorizationId = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = budgetId;
            replyPad = padding;
            replyCap = capabilities;
            state = KafkaState.openedReply(state);

            assert replyAck <= replySeq;

            fanout.replyMax = replyMax;
            fanout.doConsumerReplyWindow(traceId, authorizationId, budgetId, padding);
        }

        private void cleanup(
            long traceId)
        {
            doConsumerInitialReset(traceId);
            doConsumerReplyAbort(traceId);
        }
    }

    final class TopicPartition
    {
        private final String consumerId;
        private final String topic;
        private final List<Integer> partitions;

        TopicPartition(
            String consumerId,
            String topic,
            List<Integer> partitions)
        {
            this.consumerId = consumerId;
            this.topic = topic;
            this.partitions = partitions;
        }
    }
}
