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
package io.aklivity.zilla.runtime.cog.kafka.internal.stream;

import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.cog.kafka.internal.cache.KafkaCache;
import io.aklivity.zilla.runtime.cog.kafka.internal.cache.KafkaCacheTopic;
import io.aklivity.zilla.runtime.cog.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.cog.kafka.internal.config.KafkaRouteConfig;
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
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class KafkaCacheServerDescribeFactory implements BindingHandler
{
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private static final int SIGNAL_RECONNECT = 1;

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
    private final Function<String, KafkaCache> supplyCache;
    private final LongFunction<KafkaCacheRoute> supplyCacheRoute;
    private final int reconnectDelay;

    public KafkaCacheServerDescribeFactory(
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
        final String16FW beginTopic = kafkaDescribeBeginEx.topic();
        final String topicName = beginTopic.asString();

        MessageConsumer newStream = null;

        final KafkaBindingConfig binding = supplyBinding.apply(routeId);
        final KafkaRouteConfig resolved = binding != null ? binding.resolve(authorization, topicName) : null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;
            final KafkaCacheRoute cacheRoute = supplyCacheRoute.apply(resolvedId);
            final int topicKey = cacheRoute.topicKey(topicName);
            KafkaCacheServerDescribeFanout fanout = cacheRoute.serverDescribeFanoutsByTopic.get(topicKey);
            if (fanout == null)
            {
                final String cacheName = String.format("%s.%s", supplyNamespace.apply(routeId), supplyLocalName.apply(routeId));
                final KafkaCache cache = supplyCache.apply(cacheName);
                final KafkaCacheTopic topic = cache.supplyTopic(topicName);
                final List<String> configNames = new ArrayList<>();
                kafkaDescribeBeginEx.configs().forEach(c -> configNames.add(c.asString()));
                final KafkaCacheServerDescribeFanout newFanout =
                        new KafkaCacheServerDescribeFanout(resolvedId, authorization, topic, configNames);

                cacheRoute.serverDescribeFanoutsByTopic.put(topicKey, newFanout);
                fanout = newFanout;
            }

            if (fanout != null)
            {
                newStream = new KafkaCacheServerDescribeStream(
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

    final class KafkaCacheServerDescribeFanout
    {
        private final long routeId;
        private final long authorization;
        private final KafkaCacheTopic topic;
        private final List<String> configNames;
        private final List<KafkaCacheServerDescribeStream> members;

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

        private long reconnectAt = NO_CANCEL_ID;
        private int reconnectAttempt;

        private KafkaCacheServerDescribeFanout(
            long routeId,
            long authorization,
            KafkaCacheTopic topic,
            List<String> configNames)
        {
            this.routeId = routeId;
            this.receiver = MessageConsumer.NOOP;
            this.authorization = authorization;
            this.topic = topic;
            this.configNames = configNames;
            this.members = new ArrayList<>();
        }

        private void onDescribeFanoutMemberOpening(
            long traceId,
            KafkaCacheServerDescribeStream member)
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
            KafkaCacheServerDescribeStream member)
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
            KafkaCacheServerDescribeStream member)
        {
            members.remove(member);

            if (members.isEmpty())
            {
                if (reconnectAt != NO_CANCEL_ID)
                {
                    signaler.cancel(reconnectAt);
                    this.reconnectAt = NO_CANCEL_ID;
                }

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
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("%s DESCRIBE connect\n", topic);
                }

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
                        .describe(d -> d.topic(topic.name())
                                        .configs(cs -> configNames.forEach(c -> cs.item(i -> i.set(c, UTF_8)))))
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

        private void doDescribeFanoutInitialAbortIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doDescribeFanoutInitialAbort(traceId);
            }
        }

        private void doDescribeFanoutInitialAbort(
            long traceId)
        {
            doAbort(receiver, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

            state = KafkaState.closedInitial(state);
        }

        private void onDescribeFanoutInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final OctetsFW extension = reset.extension();

            final KafkaResetExFW kafkaResetEx = extension.get(kafkaResetExRO::tryWrap);
            final int error = kafkaResetEx != null ? kafkaResetEx.error() : -1;

            state = KafkaState.closedInitial(state);

            doDescribeFanoutReplyResetIfNecessary(traceId);

            if (reconnectDelay != 0 && !members.isEmpty())
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("%s DESCRIBE reconnect in %ds, error %d\n", topic, reconnectDelay, error);
                }

                if (reconnectAt != NO_CANCEL_ID)
                {
                    signaler.cancel(reconnectAt);
                }

                this.reconnectAt = signaler.signalAt(
                    currentTimeMillis() + Math.min(50 << reconnectAttempt++, SECONDS.toMillis(reconnectDelay)),
                    SIGNAL_RECONNECT,
                    this::onDescribeFanoutSignal);
            }
            else
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("%s DESCRIBE disconnect, error %d\n", topic, error);
                }

                members.forEach(s -> s.doDescribeInitialResetIfNecessary(traceId));
            }
        }

        private void onDescribeFanoutSignal(
            int signalId)
        {
            assert signalId == SIGNAL_RECONNECT;

            this.reconnectAt = NO_CANCEL_ID;

            final long traceId = supplyTraceId.getAsLong();

            doDescribeFanoutInitialBeginIfNecessary(traceId);
        }

        private void onDescribeFanoutInitialWindow(
            WindowFW window)
        {
            if (!KafkaState.initialOpened(state))
            {
                this.reconnectAttempt = 0;

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
                changedConfigs.forEach(this::onDescribeFanoutConfigChanged);
                members.forEach(s -> s.doDescribeReplyDataIfNecessary(traceId, kafkaDataEx));
            }

            doDescribeFanoutReplyWindow(traceId, 0, replyMax);
        }

        private void onDescribeFanoutConfigChanged(
            KafkaConfigFW config)
        {
            final String16FW configName = config.name();
            final String16FW configValue = config.value();

            topic.config().onChanged(configName, configValue);

            configValues.put(configName.asString(), configValue.asString());
        }

        private void onDescribeFanoutReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedReply(state);

            doDescribeFanoutInitialEndIfNecessary(traceId);

            if (reconnectDelay != 0 && !members.isEmpty())
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("%s DESCRIBE reconnect in %ds\n", topic, reconnectDelay);
                }

                if (reconnectAt != NO_CANCEL_ID)
                {
                    signaler.cancel(reconnectAt);
                }

                this.reconnectAt = signaler.signalAt(
                    currentTimeMillis() + Math.min(50 << reconnectAttempt++, SECONDS.toMillis(reconnectDelay)),
                    SIGNAL_RECONNECT,
                    this::onDescribeFanoutSignal);
            }
            else
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("%s DESCRIBE disconnect\n", topic);
                }

                members.forEach(s -> s.doDescribeReplyEndIfNecessary(traceId));
            }
        }

        private void onDescribeFanoutReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedReply(state);

            doDescribeFanoutInitialAbortIfNecessary(traceId);

            if (reconnectDelay != 0 && !members.isEmpty())
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("%s DESCRIBE reconnect in %ds\n", topic, reconnectDelay);
                }

                if (reconnectAt != NO_CANCEL_ID)
                {
                    signaler.cancel(reconnectAt);
                }

                this.reconnectAt = signaler.signalAt(
                    currentTimeMillis() + Math.min(50 << reconnectAttempt++, SECONDS.toMillis(reconnectDelay)),
                    SIGNAL_RECONNECT,
                    this::onDescribeFanoutSignal);
            }
            else
            {
                if (KafkaConfiguration.DEBUG)
                {
                    System.out.format("%s DESCRIBE disconnect\n", topic);
                }

                members.forEach(s -> s.doDescribeReplyAbortIfNecessary(traceId));
            }
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

                state = KafkaState.openedReply(state);

                doWindow(receiver, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, 0L, 0);
            }
        }
    }

    private final class KafkaCacheServerDescribeStream
    {
        private final KafkaCacheServerDescribeFanout group;
        private final MessageConsumer sender;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;

        private int state;

        private long replyBudgetId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        KafkaCacheServerDescribeStream(
            KafkaCacheServerDescribeFanout group,
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
            int minInitialWin,
            int minInitialPad,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(initialSeq - minInitialWin, initialAck);

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
                        .describe(m -> m.topic(group.topic.name())
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
