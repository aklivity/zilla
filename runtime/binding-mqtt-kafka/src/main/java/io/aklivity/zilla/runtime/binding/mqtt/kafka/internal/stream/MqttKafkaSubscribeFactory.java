/*
 * Copyright 2021-2023 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream;


import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionFactory.MQTT_CLIENTS_GROUP_ID;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPublishFlags.RETAIN;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSubscribeFlags.NO_LOCAL;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSubscribeFlags.RETAIN_AS_PUBLISHED;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSubscribeFlags.SEND_RETAINED;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.lang.System.currentTimeMillis;
import static java.time.Instant.now;
import static java.util.concurrent.TimeUnit.SECONDS;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.MutableInteger;
import org.agrona.collections.Object2IntHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionKind;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaHeaderHelper;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaCapabilities;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaConditionFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaEvaluation;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaSkip;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttQoS;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttTopicFilterFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.Varuint32FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.codec.MqttSubscribeMessageFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedConsumerFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttOffsetMetadataFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttOffsetStateFlags;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttSubscribeBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttSubscribeFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public class MqttKafkaSubscribeFactory implements MqttKafkaStreamFactory
{
    private static final String MQTT_TYPE_NAME = "mqtt";
    private static final String KAFKA_TYPE_NAME = "kafka";
    private static final String MQTT_SINGLE_LEVEL_WILDCARD = "+";
    private static final String MQTT_MULTI_LEVEL_WILDCARD = "#";
    private static final int NO_LOCAL_FLAG = 1 << NO_LOCAL.ordinal();
    private static final int SEND_RETAIN_FLAG = 1 << SEND_RETAINED.ordinal();
    private static final int RETAIN_FLAG = 1 << RETAIN.ordinal();
    private static final int RETAIN_AS_PUBLISHED_FLAG = 1 << RETAIN_AS_PUBLISHED.ordinal();
    private static final int SIGNAL_CONNECT_BOOTSTRAP_STREAM = 1;
    private static final int DATA_FLAG_INIT = 0x02;
    private static final int DATA_FLAG_FIN = 0x01;
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final MqttSubscribeMessageFW.Builder mqttSubscribeMessageRW = new MqttSubscribeMessageFW.Builder();
    private final MqttOffsetMetadataFW.Builder mqttOffsetMetadataRW = new MqttOffsetMetadataFW.Builder();

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final MqttBeginExFW mqttBeginExRO = new MqttBeginExFW();
    private final MqttFlushExFW mqttFlushExRO = new MqttFlushExFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();
    private final KafkaFlushExFW kafkaFlushExRO = new KafkaFlushExFW();
    private final KafkaHeaderFW kafkaHeaderRO = new KafkaHeaderFW();
    private final MqttSubscribeMessageFW mqttSubscribeMessageRO = new MqttSubscribeMessageFW();
    private final MqttOffsetMetadataFW mqttOffsetMetadataRO = new MqttOffsetMetadataFW();

    private final MqttDataExFW.Builder mqttDataExRW = new MqttDataExFW.Builder();
    private final MqttFlushExFW.Builder mqttFlushExRW = new MqttFlushExFW.Builder();

    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaFlushExFW.Builder kafkaFlushExRW = new KafkaFlushExFW.Builder();
    private final Array32FW.Builder<MqttTopicFilterFW.Builder, MqttTopicFilterFW> filtersRW =
        new Array32FW.Builder<>(new MqttTopicFilterFW.Builder(), new MqttTopicFilterFW());

    private final Array32FW.Builder<Varuint32FW.Builder, Varuint32FW> subscriptionIdsRW =
        new Array32FW.Builder<>(new Varuint32FW.Builder(), new Varuint32FW());

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer subscriptionIdsBuffer;
    private final MutableDirectBuffer filterBuffer;
    private final MutableDirectBuffer offsetBuffer;
    private final BindingHandler streamFactory;
    private final Signaler signaler;
    private final BufferPool bufferPool;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final Supplier<Long> supplyTraceId;
    private final int mqttTypeId;
    private final int kafkaTypeId;
    private final LongFunction<MqttKafkaBindingConfig> supplyBinding;
    private final MqttKafkaHeaderHelper helper;
    private final int reconnectDelay;
    private final boolean bootstrapAvailable;
    private final List<KafkaMessagesBootstrap> bootstrapStreams;
    private final MutableInteger packetIdCounter;
    private final Int2ObjectHashMap<String16FW> qosNames;
    //TODO: rename
    private final Int2ObjectHashMap<PartitionOffset> offsetsPerPacketId;
    private final Long2ObjectHashMap<OffsetHighWaterMark> highWaterMarks;
    private final Object2IntHashMap<String> qosLevels;
    private int reconnectAttempt;

    public MqttKafkaSubscribeFactory(
        MqttKafkaConfiguration config,
        EngineContext context,
        LongFunction<MqttKafkaBindingConfig> supplyBinding)
    {
        this.mqttTypeId = context.supplyTypeId(MQTT_TYPE_NAME);
        this.kafkaTypeId = context.supplyTypeId(KAFKA_TYPE_NAME);
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.subscriptionIdsBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.filterBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.offsetBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.signaler = context.signaler();
        this.bufferPool = context.bufferPool();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.supplyBinding = supplyBinding;
        this.helper = new MqttKafkaHeaderHelper();
        this.bootstrapAvailable = config.bootstrapAvailable();
        this.reconnectDelay = config.bootstrapStreamReconnectDelay();
        this.bootstrapStreams = new ArrayList<>();
        this.packetIdCounter = new MutableInteger(1);
        this.qosNames = new Int2ObjectHashMap<>();
        this.offsetsPerPacketId = new Int2ObjectHashMap<>();
        this.highWaterMarks = new Long2ObjectHashMap<>();
        this.qosLevels = new Object2IntHashMap<>(-1);
        this.qosLevels.put("0", 0);
        this.qosLevels.put("1", 1);
        this.qosLevels.put("2", 2);
    }

    @Override
    public void onAttached(
        long bindingId)
    {
        if (bootstrapAvailable)
        {
            MqttKafkaBindingConfig binding = supplyBinding.apply(bindingId);
            List<MqttKafkaRouteConfig> bootstrap = binding.bootstrapRoutes();
            bootstrap.forEach(r ->
            {
                final KafkaMessagesBootstrap stream = new KafkaMessagesBootstrap(binding.id, r);
                bootstrapStreams.add(stream);
                stream.doKafkaBeginAt(currentTimeMillis());
            });
        }
        this.qosNames.put(0, new String16FW("0"));
        this.qosNames.put(1, new String16FW("1"));
        this.qosNames.put(2, new String16FW("2"));
    }

    @Override
    public void onDetached(
        long bindingId)
    {
        for (KafkaMessagesBootstrap stream : bootstrapStreams)
        {
            stream.doKafkaEnd(supplyTraceId.get(), 0);
        }
        bootstrapStreams.clear();
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer mqtt)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();
        final MqttBeginExFW mqttBeginEx = extension.get(mqttBeginExRO::tryWrap);

        assert mqttBeginEx.kind() == MqttBeginExFW.KIND_SUBSCRIBE;
        final MqttSubscribeBeginExFW mqttSubscribeBeginEx = mqttBeginEx.subscribe();

        final MqttKafkaBindingConfig binding = supplyBinding.apply(routedId);

        final List<MqttKafkaRouteConfig> routes = binding != null ?
            binding.resolveAll(authorization, mqttSubscribeBeginEx.filters()) : null;
        MessageConsumer newStream = null;

        if (routes != null && !routes.isEmpty())
        {
            newStream = new MqttSubscribeProxy(mqtt, originId, routedId, initialId, routes)::onMqttMessage;
        }

        return newStream;
    }

    private final class MqttSubscribeProxy
    {
        private final MessageConsumer mqtt;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final Long2ObjectHashMap<KafkaMessagesProxy> messages;
        private final Long2LongHashMap messagesPerTopicKey;
        private final KafkaRetainedProxy retained;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBud;
        private int mqttSharedBudget;

        private final IntArrayList retainedSubscriptionIds;
        private final Long2ObjectHashMap<Boolean> retainAsPublished;
        private final List<Subscription> retainedSubscriptions;
        private String16FW clientId;
        private int qos;
        private boolean retainAvailable;

        private MqttSubscribeProxy(
            MessageConsumer mqtt,
            long originId,
            long routedId,
            long initialId,
            List<MqttKafkaRouteConfig> routes)
        {
            this.mqtt = mqtt;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.retainedSubscriptionIds = new IntArrayList();
            this.retainedSubscriptions = new ArrayList<>();
            this.retainAsPublished = new Long2ObjectHashMap<>();
            this.messagesPerTopicKey = new Long2LongHashMap(-1);
            this.messages = new Long2ObjectHashMap<>();
            routes.forEach(r ->
            {
                KafkaMessagesProxy messagesProxy = new KafkaMessagesProxy(originId, r, this);
                messages.put(r.order, messagesProxy);
                messagesPerTopicKey.put(messagesProxy.topicKey, r.order);
            });
            final MqttKafkaRouteConfig retainedRoute = routes.get(0);
            this.retained = new KafkaRetainedProxy(originId, retainedRoute.id, retainedRoute.retained, this);
        }

        private void onMqttMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onMqttBegin(begin);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onMqttFlush(flush);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onMqttData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onMqttEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onMqttAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onMqttReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onMqttWindow(window);
                break;
            }
        }

        private void onMqttBegin(
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
            state = MqttKafkaState.openingInitial(state);

            assert initialAck <= initialSeq;

            final OctetsFW extension = begin.extension();
            final MqttBeginExFW mqttBeginEx = extension.get(mqttBeginExRO::tryWrap);

            assert mqttBeginEx.kind() == MqttBeginExFW.KIND_SUBSCRIBE;
            final MqttSubscribeBeginExFW mqttSubscribeBeginEx = mqttBeginEx.subscribe();

            qos = mqttSubscribeBeginEx.qos();
            clientId = newString16FW(mqttSubscribeBeginEx.clientId());

            Array32FW<MqttTopicFilterFW> filters = mqttSubscribeBeginEx.filters();
            filters.forEach(f -> retainAvailable |= (f.flags() & SEND_RETAIN_FLAG) != 0);

            final List<Subscription> retainedFilters = new ArrayList<>();
            if (retainAvailable)
            {
                filters.forEach(filter ->
                {
                    final boolean sendRetained = (filter.flags() & SEND_RETAIN_FLAG) != 0;
                    if (sendRetained)
                    {
                        retainedFilters.add(new Subscription(
                            (int) filter.subscriptionId(), newString16FW(filter.pattern()), filter.qos(), filter.flags()));
                    }
                });
            }
            if (retainAvailable && !retainedFilters.isEmpty())
            {
                retained.doKafkaBegin(traceId, authorization, affinity, retainedFilters);
            }
            messages.values().forEach(m -> m.doKafkaBegin(traceId, authorization, affinity, filters));
        }

        private void onMqttFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            final MqttKafkaBindingConfig binding = supplyBinding.apply(routedId);

            final OctetsFW extension = flush.extension();
            final MqttFlushExFW mqttFlushEx = extension.get(mqttFlushExRO::tryWrap);

            assert mqttFlushEx.kind() == MqttFlushExFW.KIND_SUBSCRIBE;
            final MqttSubscribeFlushExFW mqttSubscribeFlushEx = mqttFlushEx.subscribe();

            final Array32FW<MqttTopicFilterFW> filters = mqttSubscribeFlushEx.filters();

            final List<MqttKafkaRouteConfig> routes = binding != null ?
                binding.resolveAll(authorization, filters) : null;
            final int packetId = mqttSubscribeFlushEx.packetId();

            if (!filters.isEmpty())
            {
                if (routes != null)
                {
                    routes.forEach(r ->
                    {
                        final long routeOrder = r.order;
                        if (!messages.containsKey(routeOrder))
                        {
                            KafkaMessagesProxy messagesProxy = new KafkaMessagesProxy(originId, r, this);
                            messages.put(routeOrder, messagesProxy);
                            messagesPerTopicKey.put(messagesProxy.topicKey, r.order);
                            messagesProxy.doKafkaBegin(traceId, authorization, 0, filters);
                        }
                        else
                        {
                            messages.get(routeOrder).doKafkaFlush(traceId, authorization, budgetId, reserved, qos, filters);
                        }
                    });
                }

                if (retainAvailable)
                {
                    final List<Subscription> retainedFilters = new ArrayList<>();
                    filters.forEach(filter ->
                    {
                        final boolean sendRetained = (filter.flags() & SEND_RETAIN_FLAG) != 0;
                        if (sendRetained)
                        {
                            retainedFilters.add(new Subscription(
                                (int) filter.subscriptionId(), newString16FW(filter.pattern()), filter.qos(), filter.flags()));
                            final boolean rap = (filter.flags() & RETAIN_AS_PUBLISHED_FLAG) != 0;
                            retainAsPublished.put((int) filter.subscriptionId(), rap);
                        }
                    });

                    retainedSubscriptions.removeIf(rf -> !filters.anyMatch(f -> f.pattern().equals(rf.filter)));
                    if (!retainedFilters.isEmpty())
                    {
                        if (MqttKafkaState.initialOpened(retained.state) && !MqttKafkaState.initialClosed(retained.state))
                        {
                            retained.doKafkaFlush(traceId, authorization, budgetId, reserved, qos, retainedFilters);
                        }
                        else
                        {
                            final List<Subscription> newRetainedFilters = new ArrayList<>();
                            retainedFilters.forEach(subscription ->
                            {
                                if (!retainedSubscriptions.contains(subscription))
                                {
                                    newRetainedFilters.add(subscription);
                                }
                            });
                            retained.doKafkaBegin(traceId, authorization, 0, newRetainedFilters);
                        }
                    }
                }
            }
            else if (packetId > 0)
            {
                final int qos = mqttSubscribeFlushEx.qos();
                final MqttOffsetStateFlags state = MqttOffsetStateFlags.valueOf(mqttSubscribeFlushEx.state());
                final PartitionOffset offset = state == MqttOffsetStateFlags.INCOMPLETE ?
                    offsetsPerPacketId.get(packetId) : offsetsPerPacketId.remove(packetId);

                final long topicPartitionKey = topicPartitionKey(offset.topicKey, offset.partitionId);
                final long messagesId = messagesPerTopicKey.get(offset.topicKey);

                OffsetCommit offsetCommit = new OffsetCommit(offset, qos, state, packetId);

                final OffsetHighWaterMark highWaterMark = highWaterMarks.get(topicPartitionKey);

                final KafkaProxy proxy = messagesId != -1 ? messages.get(messagesId) : retained;

                if (highWaterMark.offset >= offset.offset)
                {
                    highWaterMark.increase();
                    commitOffset(traceId, authorization, budgetId, reserved, proxy, offsetCommit);
                    commitDeferredOffsets(traceId, authorization, budgetId, reserved, highWaterMark);
                }
                else if (qos == MqttQoS.EXACTLY_ONCE.value() && state != MqttOffsetStateFlags.INCOMPLETE)
                {
                    commitOffset(traceId, authorization, budgetId, reserved, proxy, offsetCommit);
                }
                else
                {
                    highWaterMark.deferOffsetCommit(offsetCommit, proxy);
                }
            }
        }

        private void commitDeferredOffsets(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OffsetHighWaterMark highWaterMark)
        {
            long offset = highWaterMark.offset;
            DeferredOffsetCommit deferredOffsetCommit = highWaterMark.deferredOffsetCommits.get(offset);

            while (deferredOffsetCommit != null)
            {
                deferredOffsetCommit.commit(traceId, authorization, budgetId, reserved);
                highWaterMark.deferredOffsetCommits.remove(offset);
                offset = highWaterMark.increase();
                deferredOffsetCommit = highWaterMark.deferredOffsetCommits.get(highWaterMark.offset);
            }
        }

        private void commitOffset(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            KafkaProxy proxy,
            OffsetCommit offsetCommit)
        {
            proxy.doKafkaConsumerFlush(traceId, authorization, budgetId, reserved, offsetCommit);
        }

        private void onMqttData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            doMqttReset(traceId);

            messages.values().forEach(m -> m.doKafkaAbort(traceId, authorization));
            messages.clear();
            if (retainAvailable)
            {
                retained.doKafkaAbort(traceId, authorization);
            }
        }


        private void onMqttEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = MqttKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;


            messages.values().forEach(m -> m.doKafkaEnd(traceId, authorization));
            messages.clear();
            if (retainAvailable)
            {
                retained.doKafkaEnd(traceId, authorization);
            }
        }

        private void onMqttAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = MqttKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;


            messages.values().forEach(m -> m.doKafkaAbort(traceId, authorization));
            messages.clear();
            if (retainAvailable)
            {
                retained.doKafkaAbort(traceId, authorization);
            }
        }

        private void onMqttReset(
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
            state = MqttKafkaState.closeReply(state);

            assert replyAck <= replySeq;


            messages.values().forEach(m -> m.doKafkaReset(traceId));
            messages.clear();
            if (retainAvailable)
            {
                retained.doKafkaReset(traceId);
            }
        }

        private void onMqttWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = padding;
            state = MqttKafkaState.openReply(state);
            this.replyBud = window.budgetId();

            assert replyAck <= replySeq;

            mqttSharedBudget = replyMax - (int)(replySeq - replyAck);

            if (retainAvailable)
            {
                retained.doKafkaWindow(traceId, authorization, budgetId, padding, capabilities);
            }
            else
            {
                messages.values().forEach(m -> m.flushDataIfNecessary(traceId, authorization, budgetId));
            }
            messages.values().forEach(m -> m.doKafkaWindow(traceId, authorization, budgetId, padding, capabilities));
        }

        private void doMqttBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            state = MqttKafkaState.openingReply(state);

            doBegin(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity);
        }

        private void doMqttData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            Flyweight extension)
        {
            doData(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, flags, reserved, payload, extension);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doMqttFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            Flyweight extension)
        {
            doFlush(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, reserved, extension);
        }

        private void doMqttFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            doFlush(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, reserved, emptyRO);
        }

        private void doMqttAbort(
            long traceId,
            long authorization)
        {
            if (!MqttKafkaState.replyClosed(state))
            {
                state = MqttKafkaState.closeReply(state);

                doAbort(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
            }
        }

        private void doMqttEnd(
            long traceId,
            long authorization)
        {
            if (!MqttKafkaState.replyClosed(state))
            {
                state = MqttKafkaState.closeReply(state);

                doEnd(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
            }
        }

        private void doMqttWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            doWindow(mqtt, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding, 0, capabilities);
        }

        private void doMqttReset(
            long traceId)
        {
            if (!MqttKafkaState.initialClosed(state))
            {
                state = MqttKafkaState.closeInitial(state);

                doReset(mqtt, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
            }
        }

        public int replyPendingAck()
        {
            return (int)(replySeq - replyAck);
        }

        private int replyWindow()
        {
            return replyMax - replyPendingAck();
        }
    }

    private final class KafkaMessagesBootstrap
    {
        private final String16FW topic;
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long reconnectAt;

        private KafkaMessagesBootstrap(
            long originId,
            MqttKafkaRouteConfig route)
        {
            this.originId = originId;
            this.routedId = route.id;
            this.topic = route.messages;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doKafkaBeginAt(
            long timeMillis)
        {
            this.reconnectAt = signaler.signalAt(
                timeMillis,
                SIGNAL_CONNECT_BOOTSTRAP_STREAM,
                this::onSignalConnectBootstrapStream);
        }

        private void onSignalConnectBootstrapStream(
            int signalId)
        {
            assert signalId == SIGNAL_CONNECT_BOOTSTRAP_STREAM;

            this.reconnectAt = NO_CANCEL_ID;
            doKafkaBegin(supplyTraceId.get(), 0, 0);
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            reconnectAttempt = 0;
            state = MqttKafkaState.openingInitial(state);

            kafka = newKafkaBootstrapStream(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck,
                initialMax, traceId, authorization, affinity, topic);
        }

        private void doKafkaEnd(
            long traceId,
            long authorization)
        {
            if (!MqttKafkaState.initialClosed(state))
            {
                state = MqttKafkaState.closeInitial(state);

                doEnd(kafka, originId, routedId, initialId, 0, 0, 0, traceId, authorization);

                signaler.cancel(reconnectAt);
                reconnectAt = NO_CANCEL_ID;
            }
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (!MqttKafkaState.initialClosed(state))
            {
                state = MqttKafkaState.closeInitial(state);

                doAbort(kafka, originId, routedId, initialId, 0, 0, 0, traceId, authorization);
            }
        }

        private void doKafkaWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
            replyMax = 8192;

            doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding, 0, capabilities);
        }

        private void onKafkaMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onKafkaBegin(begin);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onKafkaEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onKafkaAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onKafkaReset(reset);
                break;
            }
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            replyMax = maximum;
            state = MqttKafkaState.openingReply(state);

            assert replyAck <= replySeq;

            doKafkaWindow(traceId, authorization, 0, 0, 0);
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = MqttKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            doKafkaEnd(traceId, authorization);

            if (reconnectDelay != 0)
            {
                if (reconnectAt != NO_CANCEL_ID)
                {
                    signaler.cancel(reconnectAt);
                }

                reconnectAt = signaler.signalAt(
                    currentTimeMillis() + SECONDS.toMillis(reconnectDelay),
                    SIGNAL_CONNECT_BOOTSTRAP_STREAM,
                    this::onSignalConnectBootstrapStream);
            }
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = MqttKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            doKafkaAbort(traceId, authorization);

            if (reconnectDelay != 0)
            {
                if (reconnectAt != NO_CANCEL_ID)
                {
                    signaler.cancel(reconnectAt);
                }

                reconnectAt = signaler.signalAt(
                    currentTimeMillis() + SECONDS.toMillis(reconnectDelay),
                    SIGNAL_CONNECT_BOOTSTRAP_STREAM,
                    this::onSignalConnectBootstrapStream);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();

            assert acknowledge <= sequence;

            if (reconnectDelay != 0)
            {
                if (reconnectAt != NO_CANCEL_ID)
                {
                    signaler.cancel(reconnectAt);
                }

                reconnectAt = signaler.signalAt(
                    currentTimeMillis() + Math.min(50 << reconnectAttempt++, SECONDS.toMillis(reconnectDelay)),
                    SIGNAL_CONNECT_BOOTSTRAP_STREAM,
                    this::onSignalConnectBootstrapStream);
            }
        }
    }

    abstract class KafkaProxy
    {
        abstract void doKafkaConsumerFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OffsetCommit offsetCommit);
    }

    final class KafkaMessagesProxy extends  KafkaProxy
    {
        private final String16FW topic;
        private final long topicKey;
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final IntArrayList messagesSubscriptionIds;
        private final Int2ObjectHashMap<IntArrayList> incompletePacketIds;
        private final MqttKafkaRouteConfig routeConfig;
        private final MqttSubscribeProxy mqtt;

        private int dataSlot = NO_SLOT;
        private int messageSlotLimit;
        private int messageSlotOffset;
        private int messageSlotReserved;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private boolean expiredMessage;

        private KafkaMessagesProxy(
            long originId,
            MqttKafkaRouteConfig route,
            MqttSubscribeProxy mqtt)
        {
            this.originId = originId;
            this.routedId = route.id;
            this.topic = route.messages;
            this.topicKey = System.identityHashCode(topic.asString().intern());
            this.routeConfig = route;
            this.mqtt = mqtt;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.messagesSubscriptionIds = new IntArrayList();
            this.incompletePacketIds = new Int2ObjectHashMap<>();
        }

        public boolean matchesTopicFilter(
            String topicFilter)
        {
            return routeConfig.matches(topicFilter, MqttKafkaConditionKind.SUBSCRIBE);
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity,
            Array32FW<MqttTopicFilterFW> filters)
        {
            if (!MqttKafkaState.initialOpening(state))
            {
                final Array32FW.Builder<MqttTopicFilterFW.Builder, MqttTopicFilterFW> filterBuilder =
                    filtersRW.wrap(filterBuffer, 0, filterBuffer.capacity());

                filters.forEach(f ->
                {
                    if (matchesTopicFilter(f.pattern().asString()))
                    {
                        int subscriptionId = (int) f.subscriptionId();
                        if (!messagesSubscriptionIds.contains(subscriptionId))
                        {
                            messagesSubscriptionIds.add(subscriptionId);
                        }
                        filterBuilder.item(fb -> fb
                            .subscriptionId(subscriptionId).qos(f.qos()).flags(f.flags()).pattern(f.pattern()));
                    }
                });

                initialSeq = mqtt.initialSeq;
                initialAck = mqtt.initialAck;
                initialMax = mqtt.initialMax;
                state = MqttKafkaState.openingInitial(state);

                kafka = newKafkaStream(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, affinity, mqtt.clientId, topic, filterBuilder.build(), mqtt.qos,
                        KafkaOffsetType.LIVE);
            }
        }

        @Override
        protected void doKafkaConsumerFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OffsetCommit offsetCommit)
        {
            final int qos = offsetCommit.qos;
            final PartitionOffset offset = offsetCommit.partitionOffset;
            final MqttOffsetStateFlags state = offsetCommit.state;
            final int packetId = offsetCommit.packetId;


            if (qos == MqttQoS.EXACTLY_ONCE.value() && state == MqttOffsetStateFlags.COMPLETE)
            {
                incompletePacketIds.computeIfAbsent(offset.partitionId, c -> new IntArrayList()).removeInt(packetId);
            }
            else if (state == MqttOffsetStateFlags.INCOMPLETE)
            {
                incompletePacketIds.computeIfAbsent(offset.partitionId, c -> new IntArrayList()).add(packetId);
            }

            final int correlationId = state == MqttOffsetStateFlags.INCOMPLETE ? packetId : -1;

            final KafkaFlushExFW kafkaFlushEx =
                kafkaFlushExRW.wrap(writeBuffer, FlushFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m -> m.consumer(f ->
                    {
                        f.progress(p ->
                        {
                            p.partitionId(offset.partitionId).partitionOffset(offset.offset + 1);
                            final IntArrayList incomplete = incompletePacketIds.get(offset.partitionId);
                            final String partitionMetadata =
                                incomplete == null || incomplete.isEmpty() ? "" : offSetMetadataListToString(incomplete);
                            p.metadata(partitionMetadata);
                        });
                        f.correlationId(correlationId);
                    }))
                    .build();

            doFlush(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, reserved, kafkaFlushEx);
        }

        private void doKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int qos,
            Array32FW<MqttTopicFilterFW> filters)
        {
            initialSeq = mqtt.initialSeq;

            messagesSubscriptionIds.clear();

            final KafkaFlushExFW kafkaFlushEx =
                kafkaFlushExRW.wrap(writeBuffer, FlushFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m -> m.fetch(f ->
                    {
                        f.capabilities(c -> c.set(KafkaCapabilities.FETCH_ONLY));
                        filters.forEach(filter ->
                        {
                            if (matchesTopicFilter(filter.pattern().asString()))
                            {
                                final int subscriptionId = (int) filter.subscriptionId();
                                if (!messagesSubscriptionIds.contains(subscriptionId))
                                {
                                    messagesSubscriptionIds.add(subscriptionId);
                                }
                                if ((filter.flags() & SEND_RETAIN_FLAG) != 0)
                                {
                                    mqtt.retainAvailable = true;
                                }
                                f.filtersItem(fi ->
                                {
                                    fi.conditionsItem(ci -> buildHeaders(ci, filter.pattern().asString()));

                                    final boolean noLocal = (filter.flags() & NO_LOCAL_FLAG) != 0;
                                    if (noLocal)
                                    {
                                        final DirectBuffer valueBuffer = mqtt.clientId.value();
                                        fi.conditionsItem(i -> i.not(n -> n.condition(c -> c.header(h ->
                                            h.nameLen(helper.kafkaLocalHeaderName.sizeof())
                                                .name(helper.kafkaLocalHeaderName)
                                                .valueLen(valueBuffer.capacity())
                                                .value(valueBuffer, 0, valueBuffer.capacity())))));
                                    }

                                    final int maxQos = filter.qos();
                                    if (maxQos != qos || maxQos == MqttQoS.EXACTLY_ONCE.value())
                                    {
                                        for (int level = 0; level <= MqttQoS.EXACTLY_ONCE.value(); level++)
                                        {
                                            if (level != qos)
                                            {
                                                final DirectBuffer valueBuffer = qosNames.get(level).value();
                                                fi.conditionsItem(i -> i.not(n -> n.condition(c -> c.header(h ->
                                                    h.nameLen(helper.kafkaQosHeaderName.sizeof())
                                                        .name(helper.kafkaQosHeaderName)
                                                        .valueLen(valueBuffer.capacity())
                                                        .value(valueBuffer, 0, valueBuffer.capacity())))));
                                            }
                                        }
                                    }
                                    else
                                    {
                                        for (int level = 0; level < maxQos; level++)
                                        {
                                            final DirectBuffer valueBuffer = qosNames.get(level).value();
                                            fi.conditionsItem(i -> i.not(n -> n.condition(c -> c.header(h ->
                                                h.nameLen(helper.kafkaQosHeaderName.sizeof())
                                                    .name(helper.kafkaQosHeaderName)
                                                    .valueLen(valueBuffer.capacity())
                                                    .value(valueBuffer, 0, valueBuffer.capacity())))));
                                        }
                                    }
                                });
                            }
                        });
                    }))
                    .build();

            doFlush(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, reserved, kafkaFlushEx);
        }

        private void doKafkaEnd(
            long traceId,
            long authorization)
        {
            if (MqttKafkaState.initialOpened(state) && !MqttKafkaState.initialClosed(state))
            {
                initialSeq = mqtt.initialSeq;
                initialAck = mqtt.initialAck;
                initialMax = mqtt.initialMax;
                state = MqttKafkaState.closeInitial(state);

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
            }
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (MqttKafkaState.initialOpened(state) && !MqttKafkaState.initialClosed(state))
            {
                initialSeq = mqtt.initialSeq;
                initialAck = mqtt.initialAck;
                initialMax = mqtt.initialMax;
                state = MqttKafkaState.closeInitial(state);

                doAbort(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
            }
        }

        private void onKafkaMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onKafkaBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onKafkaData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onKafkaEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onKafkaAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onKafkaFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onKafkaReset(reset);
                break;
            }
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();
            final OctetsFW extension = begin.extension();
            final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
            final KafkaBeginExFW kafkaBeginEx =
                beginEx != null && beginEx.typeId() == kafkaTypeId ? extension.get(kafkaBeginExRO::tryWrap) : null;
            final KafkaMergedBeginExFW kafkaMergedBeginEx =
                kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaDataExFW.KIND_MERGED ? kafkaBeginEx.merged() : null;

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            replyMax = maximum;
            state = MqttKafkaState.openingReply(state);

            assert replyAck <= replySeq;

            if (kafkaMergedBeginEx != null)
            {
                kafkaMergedBeginEx.partitions().forEach(p ->
                {
                    final String16FW metadata = p.metadata();
                    if (mqtt.qos == MqttQoS.EXACTLY_ONCE.value() && metadata != null && metadata.length() > 0)
                    {
                        final IntArrayList packetIds = stringToOffsetMetadataList(metadata);
                        incompletePacketIds.put(p.partitionId(), packetIds);
                        packetIds.forEach(id -> offsetsPerPacketId.put(id,
                            new PartitionOffset(topicKey, p.partitionId(), p.partitionOffset())));
                    }
                    highWaterMarks.put(topicPartitionKey(topicKey, p.partitionId()),
                        new OffsetHighWaterMark(p.stableOffset() + 1));
                });
            }

            mqtt.doMqttBegin(traceId, authorization, affinity);
            doKafkaWindow(traceId, authorization, mqtt.replyBud, mqtt.replyPad, 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            sendData:
            if (replySeq > replyAck + replyMax)
            {
                doKafkaReset(traceId);
                mqtt.doMqttAbort(traceId, authorization);
            }
            else
            {
                final int flags = data.flags();
                final int length = data.length();
                final OctetsFW payload = data.payload();
                final OctetsFW extension = data.extension();
                final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                final KafkaDataExFW kafkaDataEx =
                    dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;
                final KafkaMergedDataExFW kafkaMergedDataEx =
                    kafkaDataEx != null && kafkaDataEx.kind() == KafkaDataExFW.KIND_MERGED ? kafkaDataEx.merged() : null;
                final OctetsFW key = kafkaMergedDataEx != null ? kafkaMergedDataEx.fetch().key().value() : null;
                final long filters = kafkaMergedDataEx != null ? kafkaMergedDataEx.fetch().filters() : 0;
                final KafkaOffsetFW partition = kafkaMergedDataEx != null ? kafkaMergedDataEx.fetch().partition() : null;
                final long timestamp = kafkaMergedDataEx != null ? kafkaMergedDataEx.fetch().timestamp() : 0;


                Flyweight mqttSubscribeDataEx = EMPTY_OCTETS;
                if ((flags & DATA_FLAG_INIT) != 0x00 && key != null)
                {
                    String topicName = kafkaMergedDataEx.fetch().key().value()
                        .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o));
                    helper.visit(kafkaMergedDataEx);

                    long expireInterval;
                    if (helper.timeout != -1)
                    {
                        expireInterval = timestamp + helper.timeout - now().toEpochMilli();
                        if (expireInterval < 0)
                        {
                            expiredMessage = true;
                            break sendData;
                        }
                    }
                    else
                    {
                        expireInterval = helper.timeout;
                    }

                    // If the qos it was created for is 0, set the high watermark, as we won't receive ack
                    if (mqtt.qos == MqttQoS.AT_MOST_ONCE.value())
                    {
                        final long topicPartitionKey = topicPartitionKey(topicKey, partition.partitionId());
                        //If we don't have the high watermark yet, it means that no stream > 0 was opened yet
                        if (highWaterMarks.containsKey(topicPartitionKey))
                        {
                            highWaterMarks.get(topicPartitionKey).increase();
                        }
                    }

                    mqttSubscribeDataEx = mqttDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(mqttTypeId)
                        .subscribe(b ->
                        {
                            b.topic(topicName);
                            if (helper.qos != null)
                            {
                                final int qos = qosLevels.getValue(helper.qos);
                                if (qos >= MqttQoS.AT_LEAST_ONCE.value())
                                {
                                    final int packetId = packetIdCounter.getAndIncrement();
                                    offsetsPerPacketId.put(packetId,
                                        new PartitionOffset(topicKey, partition.partitionId(), partition.partitionOffset()));
                                    b.packetId(packetId);
                                    b.qos(qos);
                                }
                            }

                            int flag = 0;
                            subscriptionIdsRW.wrap(subscriptionIdsBuffer, 0, subscriptionIdsBuffer.capacity());
                            for (int i = 0; i < messagesSubscriptionIds.size(); i++)
                            {
                                if (((filters >> i) & 1) == 1)
                                {
                                    long subscriptionId = messagesSubscriptionIds.get(i);
                                    subscriptionIdsRW.item(si -> si.set((int) subscriptionId));
                                }
                            }
                            b.flags(flag);
                            b.subscriptionIds(subscriptionIdsRW.build());

                            if (expireInterval != -1)
                            {
                                b.expiryInterval((int) expireInterval);
                            }
                            if (helper.contentType != null)
                            {
                                b.contentType(
                                    helper.contentType.buffer(), helper.contentType.offset(), helper.contentType.sizeof());
                            }
                            if (helper.format != null)
                            {
                                b.format(f -> f.set(MqttPayloadFormat.valueOf(helper.format)));
                            }
                            if (helper.replyTo != null)
                            {
                                b.responseTopic(
                                    helper.replyTo.buffer(), helper.replyTo.offset(), helper.replyTo.sizeof());
                            }
                            if (helper.correlation != null)
                            {
                                b.correlation(c -> c.bytes(helper.correlation));
                            }

                            final DirectBuffer buffer = kafkaMergedDataEx.buffer();
                            final int limit = kafkaMergedDataEx.limit();
                            helper.userPropertiesOffsets.forEach(o ->
                            {
                                final KafkaHeaderFW header = kafkaHeaderRO.wrap(buffer, o, limit);
                                final OctetsFW name = header.name();
                                final OctetsFW value = header.value();
                                if (value != null)
                                {
                                    b.propertiesItem(pi -> pi
                                        .key(name.buffer(), name.offset(), name.sizeof())
                                        .value(value.buffer(), value.offset(), value.sizeof()));
                                }
                            });
                        }).build();
                }

                if (!expiredMessage)
                {
                    if (!MqttKafkaState.initialOpened(mqtt.retained.state) ||
                        MqttKafkaState.replyClosed(mqtt.retained.state))
                    {
                        mqtt.doMqttData(traceId, authorization, budgetId, reserved, flags, payload, mqttSubscribeDataEx);
                        mqtt.mqttSharedBudget -= length;
                    }
                    else
                    {
                        if (dataSlot == NO_SLOT)
                        {
                            dataSlot = bufferPool.acquire(initialId);
                        }

                        if (dataSlot == NO_SLOT)
                        {
                            cleanup(traceId, authorization);
                        }


                        final MutableDirectBuffer dataBuffer = bufferPool.buffer(dataSlot);
                        Flyweight message = mqttSubscribeMessageRW.wrap(dataBuffer, messageSlotLimit, dataBuffer.capacity())
                            .extension(mqttSubscribeDataEx.buffer(), mqttSubscribeDataEx.offset(), mqttSubscribeDataEx.sizeof())
                            .payload(payload)
                            .build();

                        messageSlotLimit = message.limit();
                        messageSlotReserved += reserved;
                    }
                }

                if ((flags & DATA_FLAG_FIN) != 0x00)
                {
                    expiredMessage = false;
                }
            }
        }

        private void flushData(
            long traceId,
            long authorization,
            long budgetId)
        {
            int length = Math.max(Math.min(mqtt.replyWindow() - mqtt.replyPad, messageSlotLimit - messageSlotOffset), 0);
            int reserved = length + mqtt.replyPad;
            if (length > 0)
            {
                final MutableDirectBuffer dataBuffer = bufferPool.buffer(dataSlot);
                // TODO: data fragmentation
                while (messageSlotOffset != length)
                {
                    final MqttSubscribeMessageFW message = mqttSubscribeMessageRO.wrap(dataBuffer, messageSlotOffset,
                        dataBuffer.capacity());
                    mqtt.doMqttData(traceId, authorization, budgetId, reserved, DATA_FLAG_FIN, message.payload(),
                        message.extension());

                    messageSlotOffset += message.sizeof();
                }
                if (messageSlotOffset == messageSlotLimit)
                {
                    bufferPool.release(dataSlot);
                    dataSlot = NO_SLOT;
                    messageSlotLimit = 0;
                    messageSlotOffset = 0;
                }
            }
        }

        private void cleanup(
            long traceId,
            long authorization)
        {
            mqtt.doMqttAbort(traceId, authorization);
            doKafkaAbort(traceId, authorization);
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = MqttKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            mqtt.doMqttEnd(traceId, authorization);
        }

        private void onKafkaFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();
            final ExtensionFW flushEx = extension.get(extensionRO::tryWrap);
            final KafkaFlushExFW kafkaFlushEx =
                flushEx != null && flushEx.typeId() == kafkaTypeId ? extension.get(kafkaFlushExRO::tryWrap) : null;

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;
            final KafkaMergedConsumerFlushExFW kafkaConsumerFlushEx = kafkaFlushEx != null &&
                kafkaFlushEx.kind() == KafkaFlushExFW.KIND_MERGED &&
                kafkaFlushEx.merged().kind() == KafkaMergedFlushExFW.KIND_CONSUMER ? kafkaFlushEx.merged().consumer() : null;
            if (kafkaConsumerFlushEx != null)
            {
                final long correlationId = kafkaConsumerFlushEx.correlationId();
                if (correlationId != -1)
                {
                    final Flyweight mqttSubscribeFlushEx = mqttFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(mqttTypeId)
                        .subscribe(b -> b.packetId((int) correlationId)).build();
                    mqtt.doMqttFlush(traceId, authorization, budgetId, reserved, mqttSubscribeFlushEx);
                }
            }
            else
            {
                if (incompletePacketIds.isEmpty())
                {
                    mqtt.doMqttFlush(traceId, authorization, budgetId, reserved);
                }
                else
                {
                    incompletePacketIds.forEach((partitionId, metadata) ->
                        metadata.forEach(packetId ->
                        {
                            final Flyweight mqttSubscribeFlushEx = mqttFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                                .typeId(mqttTypeId)
                                .subscribe(b -> b.packetId(packetId)).build();
                            mqtt.doMqttFlush(traceId, authorization, 0, 0, mqttSubscribeFlushEx);
                        }));
                }
            }
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = MqttKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            mqtt.doMqttAbort(traceId, authorization);
        }

        private void onKafkaWindow(
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
            assert acknowledge >= mqtt.initialAck;
            assert maximum >= mqtt.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = MqttKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            mqtt.doMqttWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= mqtt.initialAck;

            mqtt.initialAck = acknowledge;

            assert mqtt.initialAck <= mqtt.initialSeq;

            mqtt.doMqttReset(traceId);
        }

        private void doKafkaReset(
            long traceId)
        {
            if (MqttKafkaState.initialOpened(state) && !MqttKafkaState.replyClosed(state))
            {
                state = MqttKafkaState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId);
            }
        }

        private void doKafkaWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
            if (MqttKafkaState.replyOpening(state))
            {
                final int replyWin = replyMax - (int) (replySeq - replyAck);
                final int newReplyWin = mqtt.mqttSharedBudget;

                final int replyCredit = newReplyWin - replyWin;

                if (replyCredit > 0)
                {
                    final int replyNoAck = (int) (replySeq - replyAck - messageSlotReserved);
                    final int replyAcked = Math.min(replyNoAck, replyCredit);
                    replyAck += replyAcked;
                    assert replyAck <= replySeq;

                    replyMax = newReplyWin + (int) (replySeq - replyAck - messageSlotReserved);
                    assert replyMax >= 0;

                    if (messageSlotReserved > 0 && dataSlot == NO_SLOT)
                    {
                        replyAck += messageSlotReserved;
                        messageSlotReserved = 0;
                    }
                    doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, budgetId, padding, replyPad, capabilities);
                }
            }
        }

        public void flushDataIfNecessary(
            long traceId,
            long authorization,
            long budgetId)
        {
            if (messageSlotOffset != messageSlotLimit)
            {
                flushData(traceId, authorization, budgetId);
            }
        }
    }

    //TODO: how to make these more efficient while keeping the internal object easily modifieable (not using FW)?
    private IntArrayList stringToOffsetMetadataList(
        String16FW metadata)
    {
        final IntArrayList metadataList = new IntArrayList();
        UnsafeBuffer buffer = new UnsafeBuffer(BitUtil.fromHex(metadata.asString()));
        final MqttOffsetMetadataFW offsetMetadata = mqttOffsetMetadataRO.wrap(buffer, 0, buffer.capacity());
        offsetMetadata.metadata().forEach(m -> metadataList.add(m.packetId()));
        return metadataList;
    }

    private String offSetMetadataListToString(
        IntArrayList metadataList)
    {
        mqttOffsetMetadataRW.wrap(offsetBuffer, 0, offsetBuffer.capacity());
        metadataList.forEach(m -> mqttOffsetMetadataRW.metadataItem(mi -> mi.packetId(m)));
        final MqttOffsetMetadataFW offsetMetadata = mqttOffsetMetadataRW.build();
        final byte[] array = new byte[offsetMetadata.sizeof()];
        offsetMetadata.buffer().getBytes(offsetMetadata.offset(), array);
        return BitUtil.toHex(array);
    }

    final class KafkaRetainedProxy extends KafkaProxy
    {
        private final String16FW topic;
        private final long topicKey;
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final MqttSubscribeProxy mqtt;
        private final Int2ObjectHashMap<IntArrayList> incompletePacketIds;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private int unAckedPackets;
        private boolean expiredMessage;

        private KafkaRetainedProxy(
            long originId,
            long routedId,
            String16FW topic,
            MqttSubscribeProxy mqtt)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.topic = topic;
            this.topicKey = System.identityHashCode(topic.asString().intern());
            this.mqtt = mqtt;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.incompletePacketIds = new Int2ObjectHashMap<>();
            this.unAckedPackets = 0;
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity,
            List<Subscription> newRetainedFilters)
        {
            state = 0;
            replySeq = 0;
            replyAck = 0;
            replyMax = 0;

            final Array32FW.Builder<MqttTopicFilterFW.Builder, MqttTopicFilterFW> filterBuilder =
                filtersRW.wrap(filterBuffer, 0, filterBuffer.capacity());

            newRetainedFilters.forEach(f ->
            {
                final int subscriptionId = f.id;
                if (!mqtt.retainedSubscriptionIds.contains(subscriptionId))
                {
                    mqtt.retainedSubscriptionIds.add(subscriptionId);
                }
                filterBuilder.item(fb -> fb
                    .subscriptionId(subscriptionId).qos(f.qos).flags(f.flags).pattern(f.filter));
                final boolean rap = (f.flags & RETAIN_AS_PUBLISHED_FLAG) != 0;
                mqtt.retainAsPublished.put(f.id, rap);
            });
            mqtt.retainedSubscriptions.addAll(newRetainedFilters);

            Array32FW<MqttTopicFilterFW> retainedFilters = filterBuilder.build();

            initialSeq = mqtt.initialSeq;
            initialAck = mqtt.initialAck;
            initialMax = mqtt.initialMax;

            state = MqttKafkaState.openingInitial(state);

            kafka =
                newKafkaStream(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, mqtt.clientId, topic, retainedFilters, mqtt.qos,
                    KafkaOffsetType.HISTORICAL);
        }

        @Override
        protected void doKafkaConsumerFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OffsetCommit offsetCommit)
        {
            final int qos = offsetCommit.qos;
            final PartitionOffset offset = offsetCommit.partitionOffset;
            final MqttOffsetStateFlags state = offsetCommit.state;
            final int packetId = offsetCommit.packetId;

            if (qos == MqttQoS.EXACTLY_ONCE.value() && state == MqttOffsetStateFlags.COMPLETE)
            {
                final IntArrayList incompletes = incompletePacketIds.get(offset.partitionId);
                incompletes.removeInt(packetId);
                if (incompletes.isEmpty())
                {
                    incompletePacketIds.remove(offset.partitionId);
                }
            }

            if (state == MqttOffsetStateFlags.INCOMPLETE)
            {
                incompletePacketIds.computeIfAbsent(offset.partitionId, c -> new IntArrayList()).add(packetId);
            }

            final int correlationId = state == MqttOffsetStateFlags.INCOMPLETE ? packetId : -1;

            final KafkaFlushExFW kafkaFlushEx =
                kafkaFlushExRW.wrap(writeBuffer, FlushFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m -> m.consumer(f ->
                    {
                        f.progress(p ->
                        {
                            p.partitionId(offset.partitionId).partitionOffset(offset.offset + 1);
                            final IntArrayList incomplete = incompletePacketIds.get(offset.partitionId);
                            final String partitionMetadata =
                                incomplete == null || incomplete.isEmpty() ? "" : offSetMetadataListToString(incomplete);
                            p.metadata(partitionMetadata);
                        });
                        f.correlationId(correlationId);
                    }))
                    .build();

            doFlush(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, reserved, kafkaFlushEx);
        }

        private void doKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int qos,
            List<Subscription> retainedFiltersList)
        {
            initialSeq = mqtt.initialSeq;

            final Array32FW.Builder<MqttTopicFilterFW.Builder, MqttTopicFilterFW> filterBuilder =
                filtersRW.wrap(filterBuffer, 0, filterBuffer.capacity());

            retainedFiltersList.forEach(f ->
            {
                final int subscriptionId = f.id;
                if (!mqtt.retainedSubscriptionIds.contains(subscriptionId))
                {
                    mqtt.retainedSubscriptionIds.add(subscriptionId);
                }
                filterBuilder.item(fb -> fb
                    .subscriptionId(subscriptionId).qos(f.qos).flags(f.flags).pattern(f.filter));
                final boolean rap = (f.flags & RETAIN_AS_PUBLISHED_FLAG) != 0;
                mqtt.retainAsPublished.put(f.id, rap);
            });

            Array32FW<MqttTopicFilterFW> retainedFilters = filterBuilder.build();

            final KafkaFlushExFW retainedKafkaFlushEx =
                kafkaFlushExRW.wrap(writeBuffer, FlushFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m -> m.fetch(f ->
                    {
                        f.capabilities(c -> c.set(KafkaCapabilities.FETCH_ONLY));
                        retainedFilters.forEach(filter ->
                            f.filtersItem(fi ->
                                fi.conditionsItem(ci ->
                                    buildHeaders(ci, filter.pattern().asString()))));
                    }))
                    .build();

            doFlush(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, reserved, retainedKafkaFlushEx);
        }

        private void doKafkaEnd(
            long traceId,
            long authorization)
        {
            if (!MqttKafkaState.initialClosed(state))
            {
                initialSeq = mqtt.initialSeq;
                initialAck = mqtt.initialAck;
                initialMax = mqtt.initialMax;
                state = MqttKafkaState.closeInitial(state);

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
            }
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (!MqttKafkaState.initialClosed(state))
            {
                initialSeq = mqtt.initialSeq;
                initialAck = mqtt.initialAck;
                initialMax = mqtt.initialMax;
                state = MqttKafkaState.closeInitial(state);

                doAbort(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
            }
        }

        private void onKafkaMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onKafkaBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onKafkaData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onKafkaEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onKafkaAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onKafkaFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onKafkaReset(reset);
                break;
            }
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();
            final OctetsFW extension = begin.extension();
            final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
            final KafkaBeginExFW kafkaBeginEx =
                beginEx != null && beginEx.typeId() == kafkaTypeId ? extension.get(kafkaBeginExRO::tryWrap) : null;
            final KafkaMergedBeginExFW kafkaMergedBeginEx =
                kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaDataExFW.KIND_MERGED ? kafkaBeginEx.merged() : null;


            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = replySeq + sequence;
            replyAck = replySeq + acknowledge;
            replyMax = maximum;

            state = MqttKafkaState.openingReply(state);

            assert replyAck <= replySeq;

            if (kafkaMergedBeginEx != null)
            {
                kafkaMergedBeginEx.partitions().forEach(p ->
                {
                    final String16FW metadata = p.metadata();
                    if (mqtt.qos == MqttQoS.EXACTLY_ONCE.value() && metadata != null && metadata.length() > 0)
                    {
                        final IntArrayList packetIds = stringToOffsetMetadataList(metadata);
                        incompletePacketIds.put(p.partitionId(), packetIds);
                        packetIds.forEach(id -> offsetsPerPacketId.put(id,
                            new PartitionOffset(topicKey, p.partitionId(), p.partitionOffset())));
                    }
                    highWaterMarks.put(topicPartitionKey(topicKey, p.partitionId()),
                        new OffsetHighWaterMark(p.stableOffset() + 1));
                });
            }

            mqtt.doMqttBegin(traceId, authorization, affinity);
            doKafkaWindow(traceId, authorization, mqtt.replyBud, mqtt.replyPad, 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            sendData:
            if (replySeq > replyAck + replyMax)
            {
                doKafkaReset(traceId);
                mqtt.doMqttAbort(traceId, authorization);
            }
            else
            {
                final int flags = data.flags();
                final int length = data.length();
                final OctetsFW payload = data.payload();
                final OctetsFW extension = data.extension();
                final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                final KafkaDataExFW kafkaDataEx =
                    dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;
                final KafkaMergedDataExFW kafkaMergedDataEx =
                    kafkaDataEx != null && kafkaDataEx.kind() == KafkaDataExFW.KIND_MERGED ? kafkaDataEx.merged() : null;
                final OctetsFW key = kafkaMergedDataEx != null ? kafkaMergedDataEx.fetch().key().value() : null;
                final long filters = kafkaMergedDataEx != null ? kafkaMergedDataEx.fetch().filters() : 0;
                final KafkaOffsetFW partition = kafkaMergedDataEx != null ? kafkaMergedDataEx.fetch().partition() : null;
                final long timestamp = kafkaMergedDataEx != null ? kafkaMergedDataEx.fetch().timestamp() : 0;

                Flyweight mqttSubscribeDataEx = EMPTY_OCTETS;
                if ((flags & DATA_FLAG_INIT) != 0x00 && key != null)
                {
                    String topicName = kafkaMergedDataEx.fetch().key().value()
                        .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o));
                    helper.visit(kafkaMergedDataEx);

                    long expireInterval;
                    if (helper.timeout != -1)
                    {
                        expireInterval = timestamp + helper.timeout - now().toEpochMilli();
                        if (expireInterval < 0)
                        {
                            expiredMessage = true;
                            break sendData;
                        }
                    }
                    else
                    {
                        expireInterval = helper.timeout;
                    }

                    mqttSubscribeDataEx = mqttDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(mqttTypeId)
                        .subscribe(b ->
                        {
                            b.topic(topicName);

                            if (helper.qos != null)
                            {
                                final int qos = qosLevels.getValue(helper.qos);
                                if (qos >= MqttQoS.AT_LEAST_ONCE.value())
                                {
                                    final int packetId = packetIdCounter.getAndIncrement();
                                    offsetsPerPacketId.put(packetId,
                                        new PartitionOffset(topicKey, partition.partitionId(), partition.partitionOffset()));
                                    unAckedPackets++;
                                    b.packetId(packetId);
                                    b.qos(qos);
                                }
                            }

                            int flag = 0;
                            subscriptionIdsRW.wrap(subscriptionIdsBuffer, 0, subscriptionIdsBuffer.capacity());
                            for (int i = 0; i < mqtt.retainedSubscriptionIds.size(); i++)
                            {
                                if (((filters >> i) & 1) == 1)
                                {
                                    long subscriptionId = mqtt.retainedSubscriptionIds.get(i);
                                    if (mqtt.retainAsPublished.getOrDefault(subscriptionId, false))
                                    {
                                        flag |= RETAIN_FLAG;
                                    }
                                    subscriptionIdsRW.item(si -> si.set((int) subscriptionId));
                                }
                            }
                            b.flags(flag);
                            b.subscriptionIds(subscriptionIdsRW.build());
                            if (expireInterval != -1)
                            {
                                b.expiryInterval((int) expireInterval);
                            }
                            if (helper.contentType != null)
                            {
                                b.contentType(
                                    helper.contentType.buffer(), helper.contentType.offset(), helper.contentType.sizeof());
                            }
                            if (helper.format != null)
                            {
                                b.format(f -> f.set(MqttPayloadFormat.valueOf(helper.format)));
                            }
                            if (helper.replyTo != null)
                            {
                                b.responseTopic(
                                    helper.replyTo.buffer(), helper.replyTo.offset(), helper.replyTo.sizeof());
                            }
                            if (helper.correlation != null)
                            {
                                b.correlation(c -> c.bytes(helper.correlation));
                            }

                            final DirectBuffer buffer = kafkaMergedDataEx.buffer();
                            final int limit = kafkaMergedDataEx.limit();
                            helper.userPropertiesOffsets.forEach(o ->
                            {
                                final KafkaHeaderFW header = kafkaHeaderRO.wrap(buffer, o, limit);
                                final OctetsFW name = header.name();
                                final OctetsFW value = header.value();
                                if (value != null)
                                {
                                    b.propertiesItem(pi -> pi
                                        .key(name.buffer(), name.offset(), name.sizeof())
                                        .value(value.buffer(), value.offset(), value.sizeof()));
                                }
                            });
                        }).build();
                }

                if (!expiredMessage)
                {
                    mqtt.doMqttData(traceId, authorization, budgetId, reserved, flags, payload, mqttSubscribeDataEx);
                    mqtt.mqttSharedBudget -= length;
                }

                if ((flags & DATA_FLAG_FIN) != 0x00)
                {
                    expiredMessage = false;
                }
            }
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = MqttKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            mqtt.messages.values().forEach(m -> m.flushData(traceId, authorization, mqtt.replyBud));
        }

        private void onKafkaFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();
            final ExtensionFW flushEx = extension.get(extensionRO::tryWrap);
            final KafkaFlushExFW kafkaFlushEx =
                flushEx != null && flushEx.typeId() == kafkaTypeId ? extension.get(kafkaFlushExRO::tryWrap) : null;


            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            final KafkaMergedConsumerFlushExFW kafkaConsumerFlushEx = kafkaFlushEx != null &&
                kafkaFlushEx.kind() == KafkaFlushExFW.KIND_MERGED &&
                kafkaFlushEx.merged().kind() == KafkaMergedFlushExFW.KIND_CONSUMER ? kafkaFlushEx.merged().consumer() : null;
            if (kafkaConsumerFlushEx != null)
            {
                final long correlationId = kafkaConsumerFlushEx.correlationId();
                if (correlationId != -1)
                {
                    final Flyweight mqttSubscribeFlushEx = mqttFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(mqttTypeId)
                        .subscribe(b -> b.packetId((int) correlationId)).build();
                    mqtt.doMqttFlush(traceId, authorization, budgetId, reserved, mqttSubscribeFlushEx);
                }
                else
                {
                    unAckedPackets--;
                }
            }
            else
            {
                incompletePacketIds.forEach((partitionId, metadata) ->
                    metadata.forEach(packetId ->
                    {
                        final Flyweight mqttSubscribeFlushEx = mqttFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                            .typeId(mqttTypeId)
                            .subscribe(b -> b.packetId(packetId)).build();
                        mqtt.doMqttFlush(traceId, authorization, 0, 0, mqttSubscribeFlushEx);
                    }));
            }

            if (unAckedPackets == 0 && incompletePacketIds.isEmpty())
            {
                mqtt.retainedSubscriptionIds.clear();
                doKafkaEnd(traceId, authorization);
            }
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = MqttKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            mqtt.doMqttAbort(traceId, authorization);
        }

        private void onKafkaWindow(
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
            assert acknowledge >= mqtt.initialAck;
            assert maximum >= mqtt.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = MqttKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            mqtt.doMqttWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= mqtt.initialAck;

            mqtt.initialAck = acknowledge;

            assert mqtt.initialAck <= mqtt.initialSeq;

            mqtt.doMqttReset(traceId);
        }

        private void doKafkaReset(
            long traceId)
        {
            if (!MqttKafkaState.replyClosed(state))
            {
                state = MqttKafkaState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId);
            }
        }

        private void doKafkaWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
            if (MqttKafkaState.replyOpening(state) &&
                !MqttKafkaState.replyClosing(state))
            {
                final int replyWin = replyMax - (int) (replySeq - replyAck);
                final int newReplyWin = mqtt.mqttSharedBudget;

                final int replyCredit = newReplyWin - replyWin;

                if (replyCredit > 0)
                {
                    final int replyNoAck = (int) (replySeq - replyAck);
                    final int replyAcked = Math.min(replyNoAck, replyCredit);

                    replyAck += replyAcked;
                    assert replyAck <= replySeq;

                    replyMax = newReplyWin + (int) (replySeq - replyAck);
                    assert replyMax >= 0;

                    doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, budgetId, padding, replyPad, capabilities);
                }
            }
        }
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
        long affinity)
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
        OctetsFW payload,
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
            .payload(payload)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());
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
        long authorization)
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
        long authorization)
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
            .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
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
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private MessageConsumer newKafkaStream(
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
        String16FW clientId,
        String16FW topic,
        Array32FW<MqttTopicFilterFW> filters,
        int qos,
        KafkaOffsetType offsetType)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m ->
                {
                    m.capabilities(c -> c.set(KafkaCapabilities.FETCH_ONLY));
                    m.topic(topic);
                    if (qos >= MqttQoS.AT_LEAST_ONCE.value())
                    {
                        m.groupId(clientId);
                    }
                    m.partitionsItem(p ->
                        p.partitionId(offsetType.value())
                            .partitionOffset(offsetType.value()));
                    filters.forEach(filter ->
                        m.filtersItem(f ->
                        {
                            f.conditionsItem(ci -> buildHeaders(ci, filter.pattern().asString()));
                            boolean noLocal = (filter.flags() & NO_LOCAL_FLAG) != 0;
                            if (noLocal)
                            {
                                final DirectBuffer valueBuffer = clientId.value();
                                f.conditionsItem(i -> i.not(n -> n.condition(c -> c.header(h ->
                                    h.nameLen(helper.kafkaLocalHeaderName.sizeof())
                                        .name(helper.kafkaLocalHeaderName)
                                        .valueLen(valueBuffer.capacity())
                                        .value(valueBuffer, 0, valueBuffer.capacity())))));
                            }

                            final int maxQos = filter.qos();
                            if (maxQos != qos || maxQos == MqttQoS.EXACTLY_ONCE.value())
                            {
                                for (int level = 0; level <= MqttQoS.EXACTLY_ONCE.value(); level++)
                                {
                                    if (level != qos)
                                    {
                                        final DirectBuffer valueBuffer = qosNames.get(level).value();
                                        f.conditionsItem(i -> i.not(n -> n.condition(c -> c.header(h ->
                                            h.nameLen(helper.kafkaQosHeaderName.sizeof())
                                                .name(helper.kafkaQosHeaderName)
                                                .valueLen(valueBuffer.capacity())
                                                .value(valueBuffer, 0, valueBuffer.capacity())))));
                                    }
                                }
                            }
                            else
                            {
                                for (int level = 0; level < maxQos; level++)
                                {
                                    final DirectBuffer valueBuffer = qosNames.get(level).value();
                                    f.conditionsItem(i -> i.not(n -> n.condition(c -> c.header(h ->
                                        h.nameLen(helper.kafkaQosHeaderName.sizeof())
                                            .name(helper.kafkaQosHeaderName)
                                            .valueLen(valueBuffer.capacity())
                                            .value(valueBuffer, 0, valueBuffer.capacity())))));
                                }
                            }
                        }));
                    m.evaluation(b -> b.set(KafkaEvaluation.EAGER));
                })
                .build();


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
            .extension(kafkaBeginEx.buffer(), kafkaBeginEx.offset(), kafkaBeginEx.sizeof())
            .build();

        MessageConsumer receiver =
            streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private MessageConsumer newKafkaBootstrapStream(
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
        String16FW topic)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(kafkaTypeId)
                .bootstrap(b -> b.topic(topic).groupId(MQTT_CLIENTS_GROUP_ID))
                .build();


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
            .extension(kafkaBeginEx.buffer(), kafkaBeginEx.offset(), kafkaBeginEx.sizeof())
            .build();

        MessageConsumer receiver =
            streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private void buildHeaders(
        KafkaConditionFW.Builder conditionBuilder,
        String pattern)
    {
        String[] headers = pattern.split("/");
        conditionBuilder.headers(hb ->
        {
            hb.nameLen(helper.kafkaFilterHeaderName.sizeof());
            hb.name(helper.kafkaFilterHeaderName);
            for (String header : headers)
            {
                if (header.equals(MQTT_SINGLE_LEVEL_WILDCARD))
                {
                    hb.valuesItem(vi -> vi.skip(sb -> sb.set(KafkaSkip.SKIP)));
                }
                else if (header.equals(MQTT_MULTI_LEVEL_WILDCARD))
                {
                    hb.valuesItem(vi -> vi.skip(sb -> sb.set(KafkaSkip.SKIP_MANY)));
                }
                else
                {
                    final DirectBuffer valueBuffer = new String16FW(header).value();
                    hb.valuesItem(vi -> vi.value(vb -> vb.length(valueBuffer.capacity())
                        .value(valueBuffer, 0, valueBuffer.capacity())));

                }
            }
        });
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
        int padding,
        int minimum,
        int capabilities)
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
            .minimum(minimum)
            .capabilities(capabilities)
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
        long traceId)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private String16FW newString16FW(
        String16FW value)
    {
        return new String16FW().wrap(value.buffer(), value.offset(), value.limit());
    }

    private static final class Subscription
    {
        private final int id;
        private final String16FW filter;
        private final int qos;
        private final int flags;

        Subscription(
            int id,
            String16FW filter,
            int qos,
            int flags)
        {
            this.id = id;
            this.filter = filter;
            this.qos = qos;
            this.flags = flags;
        }
    }

    private static final class PartitionOffset
    {
        public final long topicKey;
        public final int partitionId;
        public final long offset;

        PartitionOffset(
            long topicKey,
            int partitionId,
            long offset)
        {
            this.topicKey = topicKey;
            this.partitionId = partitionId;
            this.offset = offset;
        }
    }

    private static class OffsetCommit
    {
        final PartitionOffset partitionOffset;
        final int qos;
        final MqttOffsetStateFlags state;
        final int packetId;

        OffsetCommit(
            OffsetCommit offsetCommit)
        {
            this.partitionOffset = offsetCommit.partitionOffset;
            this.qos = offsetCommit.qos;
            this.state = offsetCommit.state;
            this.packetId = offsetCommit.packetId;
        }

        OffsetCommit(
            PartitionOffset partitionOffset,
            int qos,
            MqttOffsetStateFlags state,
            int packetId)
        {
            this.partitionOffset = partitionOffset;
            this.qos = qos;
            this.state = state;
            this.packetId = packetId;
        }
    }

    private static class DeferredOffsetCommit extends  OffsetCommit
    {
        final KafkaProxy proxy;

        DeferredOffsetCommit(
            OffsetCommit offsetCommit,
            KafkaProxy proxy)
        {
            super(offsetCommit);
            this.proxy = proxy;
        }

        public void commit(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            proxy.doKafkaConsumerFlush(traceId, authorization, budgetId, reserved, this);
        }
    }

    private static final class OffsetHighWaterMark
    {
        final Long2ObjectHashMap<DeferredOffsetCommit> deferredOffsetCommits;
        private long offset;

        OffsetHighWaterMark(
            long offset)
        {
            this.offset = offset;
            this.deferredOffsetCommits = new Long2ObjectHashMap<>();
        }

        public long increase()
        {
            return ++offset;
        }

        public void deferOffsetCommit(
            OffsetCommit offsetCommit,
            KafkaProxy proxy)
        {
            deferredOffsetCommits.put(offsetCommit.partitionOffset.offset, new DeferredOffsetCommit(offsetCommit, proxy));
        }
    }

    private long topicPartitionKey(
        long topicKey,
        int partitionId)
    {
        return Objects.hash(topicKey, partitionId);
    }
}
