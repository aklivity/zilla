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


import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPublishFlags.RETAIN;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSubscribeFlags.NO_LOCAL;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSubscribeFlags.RETAIN_AS_PUBLISHED;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSubscribeFlags.SEND_RETAINED;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;


import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

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
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaSkip;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPayloadFormat;
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
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttSubscribeBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttSubscribeFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public class MqttKafkaSubscribeFactory implements BindingHandler
{
    private static final String MQTT_TYPE_NAME = "mqtt";
    private static final String KAFKA_TYPE_NAME = "kafka";
    private static final String MQTT_SINGLE_LEVEL_WILDCARD = "+";
    private static final String MQTT_MULTI_LEVEL_WILDCARD = "#";
    private static final int NO_LOCAL_FLAG = 1 << NO_LOCAL.ordinal();
    private static final int SEND_RETAIN_FLAG = 1 << SEND_RETAINED.ordinal();
    private static final int RETAIN_FLAG = 1 << RETAIN.ordinal();
    private static final int RETAIN_AS_PUBLISHED_FLAG = 1 << RETAIN_AS_PUBLISHED.ordinal();
    public static final int DATA_FIN_FLAG = 0x03;
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

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final MqttBeginExFW mqttBeginExRO = new MqttBeginExFW();
    private final MqttFlushExFW mqttFlushExRO = new MqttFlushExFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();
    private final KafkaHeaderFW kafkaHeaderRO = new KafkaHeaderFW();
    private final MqttSubscribeMessageFW mqttSubscribeMessageRO = new MqttSubscribeMessageFW();

    private final MqttDataExFW.Builder mqttDataExRW = new MqttDataExFW.Builder();

    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaFlushExFW.Builder kafkaFlushExRW = new KafkaFlushExFW.Builder();
    private final Array32FW.Builder<MqttTopicFilterFW.Builder, MqttTopicFilterFW> sendRetainedFiltersRW =
        new Array32FW.Builder<>(new MqttTopicFilterFW.Builder(), new MqttTopicFilterFW());

    private final Array32FW.Builder<Varuint32FW.Builder, Varuint32FW> subscriptionIdsRW =
        new Array32FW.Builder<>(new Varuint32FW.Builder(), new Varuint32FW());

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer retainFilterBuffer;
    private final MutableDirectBuffer subscriptionIdsBuffer;
    private final BindingHandler streamFactory;
    private final BufferPool bufferPool;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int mqttTypeId;
    private final int kafkaTypeId;
    private final LongFunction<MqttKafkaBindingConfig> supplyBinding;
    private final MqttKafkaHeaderHelper helper;
    private final String kafkaMessagesTopicName;
    private final String kafkaRetainedTopicName;

    public MqttKafkaSubscribeFactory(
        MqttKafkaConfiguration config,
        EngineContext context,
        LongFunction<MqttKafkaBindingConfig> supplyBinding)
    {
        this.mqttTypeId = context.supplyTypeId(MQTT_TYPE_NAME);
        this.kafkaTypeId = context.supplyTypeId(KAFKA_TYPE_NAME);
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.retainFilterBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.subscriptionIdsBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.bufferPool = context.bufferPool();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyBinding = supplyBinding;
        this.helper = new MqttKafkaHeaderHelper();
        this.kafkaMessagesTopicName = config.kafkaMessagesTopic();
        this.kafkaRetainedTopicName = config.kafkaRetainedMessagesTopic();
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

        final MqttKafkaBindingConfig binding = supplyBinding.apply(routedId);

        final MqttKafkaRouteConfig resolved = binding != null ? binding.resolve(authorization) : null;


        MessageConsumer newStream = null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;
            newStream = new MqttSubscribeProxy(mqtt, originId, routedId, initialId, resolvedId)::onMqttMessage;
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
        private final KafkaMessagesProxy messages;
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

        private final Queue<List<Subscription>> deferredRetainedFiltersQueue;
        private final List<Subscription> retainedSubscriptions;
        private final IntArrayList messagesSubscriptionIds;
        private final IntArrayList retainedSubscriptionIds;
        private final Long2ObjectHashMap<Boolean> retainAsPublished;

        private String clientId;
        private Array32FW<MqttTopicFilterFW> filters;
        private Array32FW<MqttTopicFilterFW> retainedFilters;
        private boolean retainAvailable;

        private MqttSubscribeProxy(
            MessageConsumer mqtt,
            long originId,
            long routedId,
            long initialId,
            long resolvedId)
        {
            this.mqtt = mqtt;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.deferredRetainedFiltersQueue = new LinkedList<>();
            this.retainedSubscriptions = new ArrayList<>();
            this.messagesSubscriptionIds = new IntArrayList();
            this.retainedSubscriptionIds = new IntArrayList();
            this.retainAsPublished = new Long2ObjectHashMap<>();
            this.messages = new KafkaMessagesProxy(originId, resolvedId, this);
            this.retained = new KafkaRetainedProxy(originId, resolvedId, this);
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

            clientId = mqttSubscribeBeginEx.clientId().asString();
            filters = mqttSubscribeBeginEx.filters();
            filters.forEach(filter ->
            {
                int subscriptionId = (int) filter.subscriptionId();
                if (!messagesSubscriptionIds.contains(subscriptionId))
                {
                    messagesSubscriptionIds.add(subscriptionId);
                }
                if ((filter.flags() & SEND_RETAIN_FLAG) != 0)
                {
                    retainAvailable = true;
                }
            });

            final List<Subscription> retainedFilters = new ArrayList<>();
            if (retainAvailable)
            {
                filters.forEach(filter ->
                {
                    final boolean sendRetained = (filter.flags() & SEND_RETAIN_FLAG) != 0;
                    if (sendRetained)
                    {
                        retainedFilters.add(new Subscription(
                            (int) filter.subscriptionId(), filter.pattern().asString(), filter.qos(), filter.flags()));
                    }
                });
            }
            if (retainAvailable && !retainedFilters.isEmpty())
            {
                retained.doKafkaBegin(traceId, authorization, affinity, retainedFilters);
            }
            else
            {
                messages.doKafkaBegin(traceId, authorization, affinity);
            }
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

            final OctetsFW extension = flush.extension();
            final MqttFlushExFW mqttFlushEx = extension.get(mqttFlushExRO::tryWrap);

            assert mqttFlushEx.kind() == MqttFlushExFW.KIND_SUBSCRIBE;
            final MqttSubscribeFlushExFW mqttSubscribeFlushEx = mqttFlushEx.subscribe();

            filters = mqttSubscribeFlushEx.filters();
            messagesSubscriptionIds.clear();

            final KafkaFlushExFW kafkaFlushEx =
                kafkaFlushExRW.wrap(writeBuffer, FlushFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m ->
                {
                    m.capabilities(c -> c.set(KafkaCapabilities.FETCH_ONLY));
                    filters.forEach(filter ->
                    {
                        if ((filter.flags() & SEND_RETAIN_FLAG) != 0)
                        {
                            retainAvailable = true;
                        }
                        m.filtersItem(f ->
                        {
                            final int subscriptionId = (int) filter.subscriptionId();
                            f.conditionsItem(ci ->
                            {
                                if (!messagesSubscriptionIds.contains(subscriptionId))
                                {
                                    messagesSubscriptionIds.add(subscriptionId);
                                }
                                buildHeaders(ci, filter.pattern().asString());
                            });

                            final boolean noLocal = (filter.flags() & NO_LOCAL_FLAG) != 0;
                            if (noLocal)
                            {
                                final DirectBuffer valueBuffer = new String16FW(clientId).value();
                                f.conditionsItem(i -> i.not(n -> n.condition(c -> c.header(h ->
                                    h.nameLen(helper.kafkaLocalHeaderName.sizeof())
                                        .name(helper.kafkaLocalHeaderName)
                                        .valueLen(valueBuffer.capacity())
                                        .value(valueBuffer, 0, valueBuffer.capacity())))));
                            }
                        });
                    });
                })
                .build();

            messages.doKafkaFlush(traceId, authorization, budgetId, reserved, kafkaFlushEx);

            if (retainAvailable)
            {
                retainedSubscriptions.removeIf(rf -> !filters.anyMatch(f -> f.pattern().asString().equals(rf.filter)));
                final List<Subscription> newRetainedFilters = new ArrayList<>();
                filters.forEach(filter ->
                {
                    final boolean sendRetained = (filter.flags() & SEND_RETAIN_FLAG) != 0;
                    final boolean alreadySentRetained =
                        retainedSubscriptions.stream().anyMatch(rf -> rf.filter.equals(filter.pattern().asString()));
                    if (sendRetained && !alreadySentRetained)
                    {
                        newRetainedFilters.add(new Subscription(
                            (int) filter.subscriptionId(), filter.pattern().asString(), filter.qos(), filter.flags()));
                        final boolean rap = (filter.flags() & RETAIN_AS_PUBLISHED_FLAG) != 0;
                        retainAsPublished.put((int) filter.subscriptionId(), rap);
                    }
                });

                if (!newRetainedFilters.isEmpty())
                {
                    if (MqttKafkaState.initialOpened(retained.state) && !MqttKafkaState.initialClosed(retained.state))
                    {
                        deferredRetainedFiltersQueue.add(newRetainedFilters);
                    }
                    else
                    {
                        retained.doKafkaBegin(traceId, authorization, 0, newRetainedFilters);
                    }
                }
            }
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

            messages.doKafkaAbort(traceId, authorization);
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


            messages.doKafkaEnd(traceId, initialSeq, authorization);
            if (retainAvailable)
            {
                retained.doKafkaEnd(traceId, initialSeq, authorization);
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


            messages.doKafkaAbort(traceId, authorization);
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


            messages.doKafkaReset(traceId);
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
            else if (messages.messageSlotOffset != messages.messageSlotLimit)
            {
                messages.flushData(traceId, authorization, budgetId);
            }
            messages.doKafkaWindow(traceId, authorization, budgetId, padding, capabilities);
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
            int reserved)
        {
            replySeq = messages.replySeq;

            doFlush(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, reserved, emptyRO);
        }

        private void doMqttAbort(
            long traceId,
            long authorization)
        {
            if (!MqttKafkaState.replyClosed(state))
            {
                replySeq = messages.replySeq;
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
                replySeq = messages.replySeq;
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
            initialAck = messages.initialAck;
            initialMax = messages.initialMax;

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

        private MqttDataExFW createSubscribeDataEx(
            KafkaMergedDataExFW kafkaMergedDataEx,
            long filters,
            String topicName,
            boolean retained,
            IntArrayList subscriptionIds)
        {
            return mqttDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mqttTypeId)
                .subscribe(b ->
                {
                    b.topic(topicName);

                    int flag = 0;
                    subscriptionIdsRW.wrap(subscriptionIdsBuffer, 0, subscriptionIdsBuffer.capacity());
                    for (int i = 0; i < subscriptionIds.size(); i++)
                    {
                        if (((filters >> i) & 1) == 1)
                        {
                            long subscriptionId = subscriptionIds.get(i);
                            if (retained && retainAsPublished.getOrDefault(subscriptionId, false))
                            {
                                flag |= RETAIN_FLAG;
                            }
                            subscriptionIdsRW.item(si -> si.set((int) subscriptionId));
                        }
                    }
                    b.flags(flag);
                    b.subscriptionIds(subscriptionIdsRW.build());
                    if (helper.timeout != -1)
                    {
                        b.expiryInterval(helper.timeout / 1000);
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
    }

    final class KafkaMessagesProxy
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final MqttSubscribeProxy mqtt;
        private int dataSlot = NO_SLOT;
        private int messageSlotLimit;
        private int messageSlotOffset;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private KafkaMessagesProxy(
            long originId,
            long routedId,
            MqttSubscribeProxy mqtt)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.mqtt = mqtt;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            if (!MqttKafkaState.initialOpening(state))
            {
                initialSeq = mqtt.initialSeq;
                initialAck = mqtt.initialAck;
                initialMax = mqtt.initialMax;
                state = MqttKafkaState.openingInitial(state);

                kafka = newKafkaStream(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, mqtt.clientId, kafkaMessagesTopicName,
                    mqtt.filters, KafkaOffsetType.LIVE);
            }
        }

        private void doKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            Flyweight extension)
        {
            initialSeq = mqtt.initialSeq;

            doFlush(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, reserved, extension);
        }

        private void doKafkaEnd(
            long traceId,
            long sequence,
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

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            replyMax = maximum;
            state = MqttKafkaState.openingReply(state);

            assert replyAck <= replySeq;

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

            replySeq += reserved;

            assert replyAck <= replySeq;

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
                final OctetsFW key = kafkaMergedDataEx != null ? kafkaMergedDataEx.key().value() : null;
                final long filters = kafkaMergedDataEx != null ? kafkaMergedDataEx.filters() : 0;

                if (key != null)
                {
                    String topicName = kafkaMergedDataEx.key().value()
                        .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o));
                    helper.visit(kafkaMergedDataEx);
                    final MqttDataExFW mqttSubscribeDataEx = mqtt.createSubscribeDataEx(kafkaMergedDataEx, filters, topicName,
                        false, mqtt.messagesSubscriptionIds);

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

                        replySeq -= reserved;
                    }
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
                    mqtt.doMqttData(traceId, authorization, budgetId, reserved, DATA_FIN_FLAG, message.payload(),
                        message.extension());

                    replySeq += Math.min(message.payload().sizeof(), length);
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

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            mqtt.doMqttFlush(traceId, authorization, budgetId, reserved);
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

    final class KafkaRetainedProxy
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final MqttSubscribeProxy mqtt;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private KafkaRetainedProxy(
            long originId,
            long routedId,
            MqttSubscribeProxy mqtt)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.mqtt = mqtt;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
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

            sendRetainedFiltersRW.wrap(retainFilterBuffer, 0, retainFilterBuffer.capacity());

            newRetainedFilters.forEach(f ->
            {
                final int subscriptionId = f.id;
                if (!mqtt.retainedSubscriptionIds.contains(subscriptionId))
                {
                    mqtt.retainedSubscriptionIds.add(subscriptionId);
                }
                sendRetainedFiltersRW.item(fb -> fb
                    .subscriptionId(subscriptionId).qos(f.qos).flags(f.flags).pattern(f.filter));
                final boolean rap = (f.flags & RETAIN_AS_PUBLISHED_FLAG) != 0;
                mqtt.retainAsPublished.put(f.id, rap);
            });
            mqtt.retainedSubscriptions.addAll(newRetainedFilters);

            mqtt.retainedFilters = sendRetainedFiltersRW.build();

            initialSeq = mqtt.initialSeq;
            initialAck = mqtt.initialAck;
            initialMax = mqtt.initialMax;

            state = MqttKafkaState.openingInitial(state);

            kafka =
                newKafkaStream(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, mqtt.clientId, kafkaRetainedTopicName,
                    mqtt.retainedFilters, KafkaOffsetType.HISTORICAL);
        }

        private void doKafkaEnd(
            long traceId,
            long sequence,
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

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = replySeq + sequence;
            replyAck = replySeq + acknowledge;
            replyMax = maximum;

            state = MqttKafkaState.openingReply(state);

            assert replyAck <= replySeq;

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
                final OctetsFW key = kafkaMergedDataEx != null ? kafkaMergedDataEx.key().value() : null;
                final long filters = kafkaMergedDataEx != null ? kafkaMergedDataEx.filters() : 0;

                if (key != null)
                {
                    String topicName = kafkaMergedDataEx.key().value()
                        .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o));
                    helper.visit(kafkaMergedDataEx);
                    final Flyweight mqttSubscribeDataEx = mqtt.createSubscribeDataEx(kafkaMergedDataEx, filters, topicName,
                        true, mqtt.retainedSubscriptionIds);

                    mqtt.doMqttData(traceId, authorization, budgetId, reserved, flags, payload, mqttSubscribeDataEx);

                    mqtt.mqttSharedBudget -= length;
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

            if (!mqtt.deferredRetainedFiltersQueue.isEmpty())
            {
                List<Subscription> deferredRetainedFilters = mqtt.deferredRetainedFiltersQueue.remove();
                state = 0;
                replyAck = 0;
                replySeq = 0;
                replyMax = 0;
                doKafkaBegin(traceId, authorization, 0, deferredRetainedFilters);
            }
            mqtt.messages.flushData(traceId, authorization, mqtt.replyBud);
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

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            mqtt.retainedSubscriptionIds.clear();
            doKafkaEnd(traceId, sequence, authorization);

            mqtt.messages.doKafkaBegin(traceId, authorization, 0);
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
            if (MqttKafkaState.initialOpening(state) &&
                !MqttKafkaState.initialClosing(state))
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
        if (receiver != null)
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
        String clientId,
        String topic,
        Array32FW<MqttTopicFilterFW> filters,
        KafkaOffsetType offsetType)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m ->
                {
                    m.capabilities(c -> c.set(KafkaCapabilities.FETCH_ONLY));
                    m.topic(topic);
                    m.partitionsItem(p ->
                        p.partitionId(offsetType.value())
                        .partitionOffset(offsetType.value()));
                    filters.forEach(filter ->

                        m.filtersItem(f ->
                        {
                            f.conditionsItem(ci ->
                            {
                                buildHeaders(ci, filter.pattern().asString());
                            });
                            boolean noLocal = (filter.flags() & NO_LOCAL_FLAG) != 0;
                            if (noLocal)
                            {
                                final DirectBuffer valueBuffer = new String16FW(clientId).value();
                                f.conditionsItem(i -> i.not(n -> n.condition(c -> c.header(h ->
                                    h.nameLen(helper.kafkaLocalHeaderName.sizeof())
                                        .name(helper.kafkaLocalHeaderName)
                                        .valueLen(valueBuffer.capacity())
                                        .value(valueBuffer, 0, valueBuffer.capacity())))));
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

    private void buildHeaders(
        KafkaConditionFW.Builder conditionBuilder,
        String pattern)
    {
        String[] headers = pattern.split("/");
        conditionBuilder.headers(hb ->
        {
            hb.nameLen(helper.kafkaTopicHeaderName.sizeof());
            hb.name(helper.kafkaTopicHeaderName);
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

    private static final class Subscription
    {
        private final int id;
        private final String filter;
        private final int qos;
        private final int flags;

        Subscription(int id, String filter, int qos, int flags)
        {
            this.id = id;
            this.filter = filter;
            this.qos = qos;
            this.flags = flags;
        }
    }
}
