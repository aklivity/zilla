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

import java.util.ArrayDeque;
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
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetFW;
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
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaConsumerFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaGroupBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaGroupFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaGroupMemberFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaGroupMemberMetadataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class KafkaCacheServerConsumerFactory implements BindingHandler
{
    private static final int OFFSET_COMMIT_REQUEST_RECORD_MAX = 512;

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
    private final Object2ObjectHashMap<String, KafkaCacheServerConsumerFanout> clientConsumerFansByGroupId;

    public KafkaCacheServerConsumerFactory(
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
        String consumerId = kafkaConsumerBeginEx.consumerId().asString();
        consumerId = consumerId != null ? consumerId : "";
        final int timeout = kafkaConsumerBeginEx.timeout();
        final List<Integer> partitions = new ArrayList<>();
        kafkaConsumerBeginEx.partitionIds().forEach(p -> partitions.add(p.partitionId()));

        MessageConsumer newStream = null;

        final KafkaBindingConfig binding = supplyBinding.apply(routedId);
        final KafkaRouteConfig resolved = binding != null ? binding.resolve(authorization, topic, groupId) : null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;

            KafkaCacheServerConsumerFanout fanout = clientConsumerFansByGroupId.get(groupId);

            if (fanout == null)
            {
                KafkaCacheServerConsumerFanout newFanout =
                    new KafkaCacheServerConsumerFanout(routedId, resolvedId, authorization, consumerId, groupId, timeout);
                fanout = newFanout;
                clientConsumerFansByGroupId.put(groupId, fanout);
            }

            newStream = new KafkaCacheServerConsumerStream(
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

    final class KafkaCacheServerConsumerFanout
    {
        private final String consumerId;
        private final String groupId;
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final List<KafkaCacheServerConsumerStream> streams;
        private final Object2ObjectHashMap<String, String> members;
        private final Object2ObjectHashMap<String, IntHashSet> partitionsByTopic;
        private final Object2ObjectHashMap<String, List<TopicPartition>> consumers;
        private final Object2ObjectHashMap<String, TopicConsumer> assignments;

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
        private String instanceId;
        private int timeout;
        private int generationId;


        private KafkaCacheServerConsumerFanout(
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
            this.consumers = new Object2ObjectHashMap<>();
            this.assignments = new Object2ObjectHashMap<>();
        }

        private void onConsumerFanoutStreamOpening(
            long traceId,
            KafkaCacheServerConsumerStream stream)
        {
            streams.add(stream);

            assert !streams.isEmpty();

            doConsumerInitialBegin(traceId, stream);

            if (KafkaState.initialOpened(state))
            {
                stream.doConsumerInitialWindow(authorization, traceId, initialBud, 0);
            }

            if (KafkaState.replyOpened(state))
            {
                stream.doConsumerReplyBegin(traceId);
            }
        }

        private void onConsumerFanoutMemberOpened(
            long traceId,
            KafkaCacheServerConsumerStream stream)
        {
            final TopicConsumer topicConsumer = assignments.get(stream.topic);
            if (topicConsumer != null)
            {
                stream.doConsumerReplyData(traceId, 3, replyPad, EMPTY_OCTETS,
                    ex -> ex.set((b, o, l) -> kafkaDataExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .consumer(c -> c.partitions(p -> topicConsumer
                            .partitions.forEach(np -> p.item(tp -> tp.partitionId(np))))
                            .assignments(a -> topicConsumer.consumers.forEach(u ->
                                a.item(ua -> ua.consumerId(u.consumerId).partitions(p -> u.partitions
                                    .forEach(np ->
                                        p.item(tp -> tp.partitionId(np))))))))
                        .build()
                        .sizeof()));
            }
        }

        private void doConsumerInitialBegin(
            long traceId,
            KafkaCacheServerConsumerStream stream)
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
            else if (!assignments.containsKey(stream.topic))
            {
                doConsumerInitialFlush(traceId,
                    ex -> ex.set((b, o, l) -> kafkaFlushExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .group(g -> g.leaderId(leaderId)
                            .memberId(memberId)
                            .members(gm ->
                            {
                                KafkaGroupMemberMetadataFW metadata = kafkaGroupMemberMetadataRW
                                    .wrap(extBuffer, 0, extBuffer.capacity())
                                    .consumerId(consumerId)
                                    .topics(t -> streams.forEach(s -> t.item(tp -> tp
                                        .topic(s.topic)
                                        .partitions(p -> s.partitions.forEach(sp ->
                                            p.item(gtp -> gtp.partitionId(sp)))))))
                                    .build();

                                gm.item(i ->
                                {
                                    KafkaGroupMemberFW.Builder builder = i.id(memberId);
                                    builder.metadataLen(metadata.sizeof())
                                        .metadata(metadata.buffer(), 0, metadata.sizeof());
                                });
                            }))
                        .build()
                        .sizeof()));
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

            streams.forEach(m -> m.cleanup(traceId));

            doConsumerReplyReset(traceId);

            onConsumerFanClosed(traceId);
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
            final OctetsFW extension = begin.extension();

            final ExtensionFW beginEx = extensionRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
            final KafkaBeginExFW groupBeginEx = beginEx.typeId() == kafkaTypeId ? extension.get(kafkaBeginExRO::wrap) : null;
            final KafkaGroupBeginExFW kafkaGroupBeginEx = groupBeginEx != null ? groupBeginEx.group() : null;

            instanceId = kafkaGroupBeginEx.instanceId().asString();

            state = KafkaState.openedReply(state);

            streams.forEach(m -> m.doConsumerReplyBegin(traceId));
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

                generationId = kafkaGroupFlushEx.generationId();
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

                    final String mId = m.id().asString();
                    members.put(mId, consumerId);

                    groupMetadata.topics().forEach(mt ->
                    {
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
                .wrap(payload.buffer(), payload.offset(), payload.limit());

            this.assignments.clear();

            topicAssignments.forEach(ta ->
            {
                KafkaCacheServerConsumerStream stream =
                    streams.stream().filter(s -> s.topic.equals(ta.topic().asString())).findFirst().get();
                IntHashSet partitions = new IntHashSet();
                List<TopicPartition> topicConsumers = new ArrayList<>();

                stream.doConsumerReplyData(traceId, flags, replyPad, EMPTY_OCTETS,
                    ex -> ex.set((b, o, l) -> kafkaDataExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .consumer(c -> c.partitions(p -> ta
                            .partitions()
                            .forEach(np ->
                            {
                                partitions.add(np.partitionId());
                                p.item(tp -> tp.partitionId(np.partitionId()));
                            }))
                            .assignments(a -> ta.userdata().forEach(u ->
                            {
                                final IntHashSet consumerTopicPartitions = new IntHashSet();
                                a.item(ua -> ua.consumerId(u.consumerId()).partitions(p -> u.partitions()
                                    .forEach(np ->
                                    {
                                        consumerTopicPartitions.add(np.partitionId());
                                        p.item(tp -> tp.partitionId(np.partitionId()));
                                    })));
                                topicConsumers.add(new TopicPartition(u.consumerId().asString(), ta.topic().asString(),
                                    consumerTopicPartitions));
                            })))
                        .build()
                        .sizeof()));

                assignments.put(ta.topic().asString(), new TopicConsumer(partitions, topicConsumers));
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

            onConsumerFanClosed(traceId);
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

            onConsumerFanClosed(traceId);
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
                consumers.clear();

                partitionsByTopic.forEach((t, p) ->
                {
                    final int partitionSize = p.size();
                    final int numberOfPartitionsPerMember = partitionSize / memberSize;
                    final int extraPartition = partitionSize % memberSize;

                    int partitionIndex = 0;
                    int newPartitionPerTopic = numberOfPartitionsPerMember + extraPartition;

                    IntHashSet.IntIterator iterator = p.iterator();

                    for (String member : members.keySet())
                    {
                        String consumerId = members.get(member);
                        List<TopicPartition> topicPartitions = consumers.computeIfAbsent(
                            member, tp -> new ArrayList<>());
                        IntHashSet partitions = new IntHashSet();

                        for (; partitionIndex < newPartitionPerTopic; partitionIndex++)
                        {
                            final int partitionId = iterator.nextValue();
                            partitions.add(partitionId);
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
            if (!consumers.isEmpty())
            {
                Array32FW.Builder<MemberAssignmentFW.Builder, MemberAssignmentFW> assignmentBuilder = memberAssignmentRW
                    .wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity());

                this.consumers.forEach((k, v) ->
                {
                    assignmentBuilder.item(ma -> ma
                        .memberId(k)
                        .assignments(ta -> v.forEach(tp -> ta.item(i -> i
                            .topic(tp.topic)
                            .partitions(p -> tp.partitions.forEach(t -> p.item(tpa -> tpa.partitionId(t))))
                            .userdata(u ->
                                this.consumers.forEach((ak, av) -> av
                                    .stream().filter(atp -> atp.topic.equals(tp.topic)).forEach(at ->
                                        u.item(ud -> ud
                                            .consumerId(at.consumerId)
                                            .partitions(pt -> at.partitions.forEach(up ->
                                                pt.item(pi -> pi.partitionId(up))))))))
                        ))));
                });

                Array32FW<MemberAssignmentFW> assignment = assignmentBuilder.build();

                doConsumerInitialData(traceId, authorization, initialBud, assignment.sizeof(), 3,
                    assignment.buffer(), assignment.offset(), assignment.sizeof(), EMPTY_OCTETS);
            }
            else
            {
                doConsumerInitialData(traceId, authorization, initialBud, memberAssignmentRW.sizeof(), 3,
                    EMPTY_OCTETS.buffer(), EMPTY_OCTETS.offset(), EMPTY_OCTETS.sizeof(), EMPTY_OCTETS);
            }
        }

        private void onConsumerFanClosed(
            long traceId)
        {
            clientConsumerFansByGroupId.remove(this.groupId);
        }
    }

    final class KafkaCacheServerConsumerStream
    {
        private final KafkaCacheServerConsumerFanout fanout;
        private final KafkaOffsetCommitStream offsetCommit;
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

        KafkaCacheServerConsumerStream(
            KafkaCacheServerConsumerFanout fanout,
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
            this.offsetCommit = new KafkaOffsetCommitStream(this, originId, routedId, authorization);
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

            fanout.onConsumerFanoutStreamOpening(traceId, this);
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

            cleanup(traceId);
        }

        private void onConsumerInitialFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = KafkaState.closedInitial(state);

            assert initialAck <= initialSeq;

            final ExtensionFW dataEx = extensionRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
            final KafkaFlushExFW kafkaFlushEx = dataEx != null && dataEx.typeId() == kafkaTypeId ?
                kafkaFlushExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit()) : null;

            KafkaConsumerFlushExFW consumerFlushEx = kafkaFlushEx.consumer();
            final KafkaOffsetFW partition = consumerFlushEx.partition();
            final int leaderEpoch = consumerFlushEx.leaderEpoch();

            offsetCommit.onOffsetCommitRequest(traceId, authorization, partition, leaderEpoch);
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

            cleanup(traceId);
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
            long traceId)
        {
            state = KafkaState.openingReply(state);

            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, EMPTY_OCTETS);
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

        private void doConsumerReplyFlush(
            long traceId,
            Consumer<OctetsFW.Builder> extension)
        {
            doFlush(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0, 0, extension);
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

            assert replyAck <= replySeq;

            fanout.replyMax = replyMax;
            fanout.doConsumerReplyWindow(traceId, authorizationId, budgetId, padding);

            if (!KafkaState.replyOpened(state))
            {
                state = KafkaState.openedReply(state);

                fanout.onConsumerFanoutMemberOpened(traceId, this);
            }
        }

        private void onOffsetCommitAck(
            long traceId,
            int partitionId,
            long partitionOffset)
        {
            doConsumerReplyFlush(traceId,
                ex -> ex.set((b, o, l) -> kafkaFlushExRW.wrap(b, o, l)
                    .typeId(kafkaTypeId)
                    .consumer(c -> c
                        .partition(p -> p.partitionId(partitionId).partitionOffset(partitionOffset)))
                    .build()
                    .sizeof()));
        }

        private void cleanup(
            long traceId)
        {
            doConsumerInitialReset(traceId);
            doConsumerReplyAbort(traceId);

            offsetCommit.cleanup(traceId);

            fanout.streams.remove(this);
        }
    }

    final class KafkaOffsetCommitStream
    {
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final KafkaCacheServerConsumerStream delegate;
        private final ArrayDeque<KafkaPartitionOffset> commitRequests;
        private final ArrayDeque<KafkaPartitionOffset> commitResponses;

        private long initialId;
        private long replyId;
        private MessageConsumer receiver;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long initialBud;
        private int initialPad;
        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private KafkaOffsetCommitStream(
            KafkaCacheServerConsumerStream delegate,
            long originId,
            long routedId,
            long authorization)
        {
            this.delegate = delegate;
            this.originId = originId;
            this.routedId = routedId;
            this.receiver = MessageConsumer.NOOP;
            this.authorization = authorization;
            this.commitRequests = new ArrayDeque<>();
            this.commitResponses = new ArrayDeque<>();
        }

        private int initialWindow()
        {
            return initialMax - (int)(initialSeq - initialAck);
        }

        private void doOffsetCommitInitialBegin(
            long traceId,
            long affinity)
        {
            if (!KafkaState.initialOpening(state))
            {
                assert state == 0;

                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.receiver = newStream(this::onOffsetCommitMessage,
                    originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, this.authorization, affinity, ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .offsetCommit(oc -> oc
                            .topic(delegate.topic)
                            .groupId(delegate.fanout.groupId)
                            .memberId(delegate.fanout.memberId)
                            .instanceId(delegate.fanout.instanceId))
                        .build().sizeof()));
                state = KafkaState.openingInitial(state);
            }
        }

        private void doOffsetCommitInitialData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            Consumer<OctetsFW.Builder> extension)
        {
            doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, flags, reserved, payload, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doOffsetCommitInitialAbort(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

                state = KafkaState.closedInitial(state);
            }
        }

        private void onOffsetCommitInitialReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;
            state = KafkaState.closedInitial(state);

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.cleanup(traceId);

            doOffsetCommitReplyReset(traceId);
        }


        private void onOffsetCommitInitialWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long authorization = window.authorization();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;
            assert maximum >= delegate.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            initialBud = budgetId;
            initialPad = padding;
            state = KafkaState.openedInitial(state);

            assert initialAck <= initialSeq;

            doOffsetCommit(traceId, authorization);
            onOffsetCommitResponse(traceId);
        }

        private void onOffsetCommitMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onOffsetCommitReplyBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onOffsetCommitReplyData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onOffsetCommitReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onOffsetCommitReplyAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onOffsetCommitInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onOffsetCommitInitialWindow(window);
                break;
            default:
                break;
            }
        }

        private void onOffsetCommitReplyBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = KafkaState.openingReply(state);
        }

        private void onOffsetCommitReplyData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;
            assert replySeq <= replyAck + replyMax;
        }

        private void onOffsetCommitReplyEnd(
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

            delegate.cleanup(traceId);
        }

        private void onOffsetCommitReplyAbort(
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

            delegate.cleanup(traceId);
        }

        private void doOffsetCommitReplyReset(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doReset(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);

                state = KafkaState.closedReply(state);
            }
        }

        private void onOffsetCommitRequest(
            long traceId,
            long authorization,
            KafkaOffsetFW partition,
            int leaderEpoch)
        {
            doOffsetCommitInitialBegin(traceId, 0);

            commitRequests.add(new KafkaPartitionOffset(
                partition.partitionId(),
                partition.partitionOffset(),
                delegate.fanout.generationId,
                leaderEpoch,
                partition.metadata().asString()));

            doOffsetCommit(traceId, authorization);
        }

        private void onOffsetCommitResponse(
            long traceId)
        {
            if (!commitResponses.isEmpty())
            {
                KafkaPartitionOffset commit = commitResponses.remove();
                delegate.onOffsetCommitAck(traceId, commit.partitionId, commit.partitionOffset);
            }
        }

        private void doOffsetCommit(
            long traceId,
            long authorization)
        {
            if (KafkaState.initialOpened(state))
            {
                //TODO: find better way to handle flow control
                final int recordSize = OFFSET_COMMIT_REQUEST_RECORD_MAX + initialPad;
                while (commitRequests.isEmpty() && initialWindow() > recordSize)
                {
                    KafkaPartitionOffset commit = commitRequests.remove();
                    Consumer<OctetsFW.Builder> offsetCommitDataEx = ex -> ex
                        .set((b, o, l) -> kafkaDataExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .offsetCommit(oc -> oc
                            .partition(p -> p.partitionId(commit.partitionId)
                                .partitionOffset(commit.partitionOffset)
                                .metadata(commit.metadata))
                            .generationId(delegate.fanout.generationId)
                            .leaderEpoch(commit.leaderEpoch))
                        .build()
                        .sizeof());

                    doOffsetCommitInitialData(traceId, authorization, initialBud, recordSize, 3,
                        EMPTY_OCTETS, offsetCommitDataEx);

                    commitResponses.add(commit);
                }
            }
        }

        private void cleanup(
            long traceId)
        {
            doOffsetCommitInitialAbort(traceId);
            doOffsetCommitReplyReset(traceId);

            commitRequests.clear();
            commitResponses.clear();
        }
    }

    final class TopicPartition
    {
        private final String consumerId;
        private final String topic;
        private final IntHashSet partitions;

        TopicPartition(
            String consumerId,
            String topic,
            IntHashSet partitions)
        {
            this.consumerId = consumerId;
            this.topic = topic;
            this.partitions = partitions;
        }
    }

    final class TopicConsumer
    {
        private final IntHashSet partitions;
        private final List<TopicPartition> consumers;

        TopicConsumer(
            IntHashSet partitions,
            List<TopicPartition> consumers)
        {
            this.partitions = partitions;
            this.consumers = consumers;
        }
    }
}
