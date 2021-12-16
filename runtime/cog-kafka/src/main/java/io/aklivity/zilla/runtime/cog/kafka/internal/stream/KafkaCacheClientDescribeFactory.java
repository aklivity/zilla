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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaCog;
import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.cog.kafka.internal.config.KafkaBinding;
import io.aklivity.zilla.runtime.cog.kafka.internal.config.KafkaRoute;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.ArrayFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.KafkaConfigFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.KafkaDescribeBeginExFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.KafkaDescribeDataExFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.cog.AxleContext;
import io.aklivity.zilla.runtime.engine.cog.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.cog.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.cog.stream.StreamFactory;

public final class KafkaCacheClientDescribeFactory implements StreamFactory
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
    private final StreamFactory streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongFunction<KafkaBinding> supplyBinding;
    private final LongFunction<KafkaCacheRoute> supplyCacheRoute;

    public KafkaCacheClientDescribeFactory(
        KafkaConfiguration config,
        AxleContext context,
        LongFunction<KafkaBinding> supplyBinding,
        LongFunction<KafkaCacheRoute> supplyCacheRoute)
    {
        this.kafkaTypeId = context.supplyTypeId(KafkaCog.NAME);
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.bufferPool = context.bufferPool();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyBinding = supplyBinding;
        this.supplyCacheRoute = supplyCacheRoute;
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
        final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
        assert beginEx != null && beginEx.typeId() == kafkaTypeId;
        final KafkaBeginExFW kafkaBeginEx = extension.get(kafkaBeginExRO::tryWrap);
        assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_DESCRIBE;
        final KafkaDescribeBeginExFW kafkaDescribeBeginEx = kafkaBeginEx.describe();
        final String16FW topic = kafkaDescribeBeginEx.topic();
        final String topicName = topic.asString();

        MessageConsumer newStream = null;

        final KafkaBinding binding = supplyBinding.apply(routeId);
        final KafkaRoute resolved = binding != null ? binding.resolve(authorization, topicName) : null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;
            final KafkaCacheRoute cacheRoute = supplyCacheRoute.apply(resolvedId);
            final int topicKey = cacheRoute.topicKey(topicName);
            KafkaCacheClientDescribeFanout fanout = cacheRoute.clientDescribeFanoutsByTopic.get(topicKey);
            if (fanout == null)
            {
                final List<String> configNames = new ArrayList<>();
                kafkaDescribeBeginEx.configs().forEach(c -> configNames.add(c.asString()));
                final KafkaCacheClientDescribeFanout newFanout =
                        new KafkaCacheClientDescribeFanout(resolvedId, authorization, topicName, configNames);

                cacheRoute.clientDescribeFanoutsByTopic.put(topicKey, newFanout);
                fanout = newFanout;
            }

            if (fanout != null)
            {
                newStream = new KafkaCacheClientDescribeStream(
                        fanout,
                        sender,
                        routeId,
                        initialId,
                        affinity,
                        authorization)::onDescribeMessage;
            }
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

    private void doDataNull(
        MessageConsumer receiver,
        long routeId,
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
                .routeId(routeId)
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

    final class KafkaCacheClientDescribeFanout
    {
        private final long routeId;
        private final long authorization;
        private final String topic;
        private final List<String> configNames;
        private final List<KafkaCacheClientDescribeStream> members;

        private long initialId;
        private long replyId;
        private MessageConsumer receiver;
        private Map<String, String> configValues;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private KafkaCacheClientDescribeFanout(
            long routeId,
            long authorization,
            String topic,
            List<String> configNames)
        {
            this.routeId = routeId;
            this.authorization = authorization;
            this.topic = topic;
            this.configNames = configNames;
            this.members = new ArrayList<>();
        }

        private void onDescribeFanoutMemberOpening(
            long traceId,
            KafkaCacheClientDescribeStream member)
        {
            members.add(member);

            assert !members.isEmpty();

            doDescribeFanoutInitialBeginIfNecessary(traceId);

            if (KafkaState.initialOpened(state))
            {
                member.doDescribeInitialWindow(traceId, 0L, 0, 0, 0);
            }

            if (KafkaState.replyOpened(state))
            {
                member.doDescribeReplyBeginIfNecessary(traceId);
            }
        }

        private void onDescribeFanoutMemberOpened(
            long traceId,
            KafkaCacheClientDescribeStream member)
        {
            if (configValues != null)
            {
                final KafkaDataExFW kafkaDataEx =
                        kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                                     .typeId(kafkaTypeId)
                                     .describe(d -> configValues.forEach((n, v) -> d.configsItem(i -> i.name(n).value(v))))
                                     .build();
                member.doDescribeReplyDataIfNecessary(traceId, kafkaDataEx);
            }
        }

        private void onDescribeFanoutMemberClosed(
            long traceId,
            KafkaCacheClientDescribeStream member)
        {
            members.remove(member);

            if (members.isEmpty())
            {
                doDescribeFanoutInitialEndIfNecessary(traceId);
                doDescribeFanoutReplyResetIfNecessary(traceId);
            }
        }

        private void doDescribeFanoutInitialBeginIfNecessary(
            long traceId)
        {
            if (KafkaState.closed(state))
            {
                state = 0;
            }

            if (!KafkaState.initialOpening(state))
            {
                doDescribeFanoutInitialBegin(traceId);
            }
        }

        private void doDescribeFanoutInitialBegin(
            long traceId)
        {
            assert state == 0;

            this.initialId = supplyInitialId.applyAsLong(routeId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.receiver = newStream(this::onDescribeFanoutMessage, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, 0L,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .describe(d -> d.topic(topic).configs(cs -> configNames.forEach(c -> cs.item(i -> i.set(c, UTF_8)))))
                        .build()
                        .sizeof()));
            state = KafkaState.openingInitial(state);
        }

        private void doDescribeFanoutInitialEndIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doDescribeFanoutInitialEnd(traceId);
            }
        }

        private void doDescribeFanoutInitialEnd(
            long traceId)
        {
            doEnd(receiver, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

            state = KafkaState.closedInitial(state);
        }

        private void onDescribeFanoutInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            members.forEach(s -> s.doDescribeInitialResetIfNecessary(traceId));

            state = KafkaState.closedInitial(state);

            doDescribeFanoutReplyResetIfNecessary(traceId);
        }

        private void onDescribeFanoutInitialWindow(
            WindowFW window)
        {
            if (!KafkaState.initialOpened(state))
            {
                final long traceId = window.traceId();

                state = KafkaState.openedInitial(state);

                members.forEach(s -> s.doDescribeInitialWindow(traceId, 0L, 0, 0, 0));
            }
        }

        private void onDescribeFanoutMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onDescribeFanoutReplyBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onDescribeFanoutReplyData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onDescribeFanoutReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onDescribeFanoutReplyAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onDescribeFanoutInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onDescribeFanoutInitialWindow(window);
                break;
            default:
                break;
            }
        }

        private void onDescribeFanoutReplyBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = KafkaState.openedReply(state);

            members.forEach(s -> s.doDescribeReplyBeginIfNecessary(traceId));

            doDescribeFanoutReplyWindow(traceId, 0, bufferPool.slotCapacity());
        }

        private void onDescribeFanoutReplyData(
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

            final ExtensionFW dataEx = extensionRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
            final KafkaDataExFW kafkaDataEx = dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;
            assert kafkaDataEx == null || kafkaDataEx.kind() == KafkaBeginExFW.KIND_DESCRIBE;
            final KafkaDescribeDataExFW kafkaDescribeDataEx = kafkaDataEx != null ? kafkaDataEx.describe() : null;

            if (kafkaDescribeDataEx != null)
            {
                final ArrayFW<KafkaConfigFW> changedConfigs = kafkaDescribeDataEx.configs();
                if (configValues == null)
                {
                    configValues = new TreeMap<>();
                }

                configValues.clear();
                changedConfigs.forEach(c -> configValues.put(c.name().asString(), c.value().asString()));

                members.forEach(s -> s.doDescribeReplyDataIfNecessary(traceId, kafkaDataEx));
            }

            doDescribeFanoutReplyWindow(traceId, 0, replyMax);
        }

        private void onDescribeFanoutReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            members.forEach(s -> s.doDescribeReplyEndIfNecessary(traceId));

            state = KafkaState.closedReply(state);
        }

        private void onDescribeFanoutReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            members.forEach(s -> s.doDescribeReplyAbortIfNecessary(traceId));

            state = KafkaState.closedReply(state);
        }

        private void doDescribeFanoutReplyResetIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doDescribeFanoutReplyReset(traceId);
            }
        }

        private void doDescribeFanoutReplyReset(
            long traceId)
        {
            doReset(receiver, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);

            state = KafkaState.closedReply(state);
        }

        private void doDescribeFanoutReplyWindow(
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

                doWindow(receiver, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, 0L, 0);

                state = KafkaState.openedReply(state);
            }
        }
    }

    private final class KafkaCacheClientDescribeStream
    {
        private final KafkaCacheClientDescribeFanout group;
        private final MessageConsumer sender;
        private final long routeId;
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

        KafkaCacheClientDescribeStream(
            KafkaCacheClientDescribeFanout group,
            MessageConsumer sender,
            long routeId,
            long initialId,
            long affinity,
            long authorization)
        {
            this.group = group;
            this.sender = sender;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
        }

        private void onDescribeMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onDescribeInitialBegin(begin);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onDescribeInitialEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onDescribeInitialAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onDescribeReplyWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onDescribeReplyReset(reset);
                break;
            default:
                break;
            }
        }

        private void onDescribeInitialBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = KafkaState.openingInitial(state);

            group.onDescribeFanoutMemberOpening(traceId, this);
        }

        private void onDescribeInitialEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedInitial(state);

            group.onDescribeFanoutMemberClosed(traceId, this);

            doDescribeReplyEndIfNecessary(traceId);
        }

        private void onDescribeInitialAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedInitial(state);

            group.onDescribeFanoutMemberClosed(traceId, this);

            doDescribeReplyAbortIfNecessary(traceId);
        }

        private void doDescribeInitialResetIfNecessary(
            long traceId)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                doDescribeInitialReset(traceId);
            }

            state = KafkaState.closedInitial(state);
        }

        private void doDescribeInitialReset(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doReset(sender, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
        }

        private void doDescribeInitialWindow(
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

                doWindow(sender, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, budgetId, minInitialPad);
            }
        }

        private void doDescribeReplyBeginIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyOpening(state))
            {
                doDescribeReplyBegin(traceId);
            }
        }

        private void doDescribeReplyBegin(
            long traceId)
        {
            state = KafkaState.openingReply(state);

            doBegin(sender, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .describe(m -> m.topic(group.topic)
                                        .configs(cs -> group.configNames.forEach(c -> cs.item(i -> i.set(c, UTF_8)))))
                        .build()
                        .sizeof()));
        }

        private void doDescribeReplyDataIfNecessary(
            long traceId,
            KafkaDataExFW extension)
        {
            if (KafkaState.replyOpened(state))
            {
                doDescribeReplyData(traceId, extension);
            }
        }

        private void doDescribeReplyData(
            long traceId,
            KafkaDataExFW extension)
        {
            final int reserved = replyPad;

            doDataNull(sender, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, replyBudgetId, reserved, extension);

            replySeq += reserved;
        }

        private void doDescribeReplyEndIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doDescribeReplyEnd(traceId);
            }

            state = KafkaState.closedReply(state);
        }

        private void doDescribeReplyEnd(
            long traceId)
        {
            state = KafkaState.closedReply(state);
            doEnd(sender, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
        }

        private void doDescribeReplyAbortIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doDescribeReplyAbort(traceId);
            }

            state = KafkaState.closedReply(state);
        }

        private void doDescribeReplyAbort(
            long traceId)
        {
            state = KafkaState.closedReply(state);
            doAbort(sender, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
        }

        private void onDescribeReplyReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedInitial(state);

            group.onDescribeFanoutMemberClosed(traceId, this);

            doDescribeInitialResetIfNecessary(traceId);
        }

        private void onDescribeReplyWindow(
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
                group.onDescribeFanoutMemberOpened(traceId, this);
            }
        }
    }
}
