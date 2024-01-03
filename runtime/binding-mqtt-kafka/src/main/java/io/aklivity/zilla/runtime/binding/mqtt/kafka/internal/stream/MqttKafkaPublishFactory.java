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

import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaAckMode.IN_SYNC_REPLICAS;
import static java.time.Instant.now;

import java.nio.ByteOrder;
import java.util.List;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaHeaderHelper;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaCapabilities;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPayloadFormatFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPublishFlags;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttPublishBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttPublishDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class MqttKafkaPublishFactory implements MqttKafkaStreamFactory
{
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);
    private static final KafkaAckMode KAFKA_DEFAULT_ACK_MODE = KafkaAckMode.LEADER_ONLY;
    private static final String KAFKA_TYPE_NAME = "kafka";
    private static final byte SLASH_BYTE = (byte) '/';
    private static final int DATA_FLAG_INIT = 0x02;
    private static final int DATA_FLAG_FIN = 0x01;
    private static final int DATA_FLAG_COMPLETE = 0x03;

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

    private final MqttBeginExFW mqttBeginExRO = new MqttBeginExFW();
    private final MqttDataExFW mqttDataExRO = new MqttDataExFW();

    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaFlushExFW.Builder kafkaFlushExRW = new KafkaFlushExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> kafkaHeadersRW =
        new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW());

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer kafkaHeadersBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final MqttKafkaHeaderHelper helper;
    private final int kafkaTypeId;
    private final LongFunction<MqttKafkaBindingConfig> supplyBinding;
    private final String16FW binaryFormat;
    private final String16FW textFormat;
    private final Int2ObjectHashMap<String16FW> qosLevels;

    public MqttKafkaPublishFactory(
        MqttKafkaConfiguration config,
        EngineContext context,
        LongFunction<MqttKafkaBindingConfig> supplyBinding)
    {
        this.kafkaTypeId = context.supplyTypeId(KAFKA_TYPE_NAME);
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.kafkaHeadersBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.helper = new MqttKafkaHeaderHelper();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyBinding = supplyBinding;
        this.binaryFormat = new String16FW(MqttPayloadFormat.BINARY.name());
        this.textFormat = new String16FW(MqttPayloadFormat.TEXT.name());
        this.qosLevels = new Int2ObjectHashMap<>();
        this.qosLevels.put(0, new String16FW("0"));
        this.qosLevels.put(1, new String16FW("1"));
        this.qosLevels.put(2, new String16FW("2"));
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

        assert mqttBeginEx.kind() == MqttBeginExFW.KIND_PUBLISH;
        final MqttPublishBeginExFW mqttPublishBeginEx = mqttBeginEx.publish();

        final MqttKafkaBindingConfig binding = supplyBinding.apply(routedId);

        final MqttKafkaRouteConfig resolved = binding != null ?
            binding.resolve(authorization, mqttPublishBeginEx.topic().asString()) : null;
        MessageConsumer newStream = null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;
            final String16FW messagesTopic = resolved.messages;
            newStream = new MqttPublishProxy(mqtt, originId, routedId, initialId, resolvedId,
                messagesTopic, binding.retainedTopic(), binding.clients)::onMqttMessage;
        }

        return newStream;
    }

    private final class MqttPublishProxy
    {
        private final MessageConsumer mqtt;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final KafkaMessagesProxy messages;
        private final KafkaRetainedProxy retained;
        private final List<Function<String, String>> clients;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private KafkaKeyFW key;
        private KafkaKeyFW hashKey;

        private Array32FW<String16FW> topicNameHeaders;
        private OctetsFW clientIdOctets;
        private boolean retainAvailable;
        private boolean retainedData;
        private boolean fragmentedData;

        private MqttPublishProxy(
            MessageConsumer mqtt,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            String16FW kafkaMessagesTopic,
            String16FW kafkaRetainedTopic,
            List<Function<String, String>> clients)
        {
            this.mqtt = mqtt;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.messages = new KafkaMessagesProxy(originId, resolvedId, this, kafkaMessagesTopic);
            this.retained = new KafkaRetainedProxy(originId, resolvedId, this, kafkaRetainedTopic);
            this.clients = clients;
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

            assert mqttBeginEx.kind() == MqttBeginExFW.KIND_PUBLISH;
            final MqttPublishBeginExFW mqttPublishBeginEx = mqttBeginEx.publish();
            String topicName = mqttPublishBeginEx.topic().asString();
            assert topicName != null;

            final int qos = mqttPublishBeginEx.qos();

            final String16FW clientId = mqttPublishBeginEx.clientId();
            final MutableDirectBuffer clientIdBuffer = new UnsafeBuffer(new byte[clientId.sizeof() + 2]);
            this.clientIdOctets = new OctetsFW.Builder().wrap(clientIdBuffer, 0, clientIdBuffer.capacity())
                .set(clientId.value(), 0, mqttPublishBeginEx.clientId().length()).build();

            String[] topicHeaders = topicName.split("/");
            final OctetsFW[] topicNameHeaders = new OctetsFW[topicHeaders.length];

            final int topicNameHeadersBufferSize = topicName.length() - (topicNameHeaders.length - 1) +
                topicNameHeaders.length * 2 + BitUtil.SIZE_OF_INT + BitUtil.SIZE_OF_INT; //Array32FW count, length
            final MutableDirectBuffer topicNameHeadersBuffer = new UnsafeBuffer(new byte[topicNameHeadersBufferSize]);

            final Array32FW.Builder<String16FW.Builder, String16FW> topicNameHeadersRW =
                new Array32FW.Builder<>(new String16FW.Builder(), new String16FW());
            topicNameHeadersRW.wrap(topicNameHeadersBuffer, 0, topicNameHeadersBuffer.capacity());

            for (int i = 0; i < topicHeaders.length; i++)
            {
                String16FW topicHeader = new String16FW(topicHeaders[i]);
                topicNameHeadersRW.item(h -> h.set(topicHeader));
            }
            this.topicNameHeaders = topicNameHeadersRW.build();


            final DirectBuffer topicNameBuffer = mqttPublishBeginEx.topic().value();
            final MutableDirectBuffer keyBuffer = new UnsafeBuffer(new byte[topicNameBuffer.capacity() + 4]);
            key = new KafkaKeyFW.Builder()
                .wrap(keyBuffer, 0, keyBuffer.capacity())
                .length(topicNameBuffer.capacity())
                .value(topicNameBuffer, 0, topicNameBuffer.capacity())
                .build();

            final String clientHashKey = clientHashKey(topicName);
            if (clientHashKey != null)
            {
                final DirectBuffer clientHashKeyBuffer = new String16FW(clientHashKey).value();
                final MutableDirectBuffer hashKeyBuffer = new UnsafeBuffer(new byte[clientHashKeyBuffer.capacity() + 4]);
                hashKey = new KafkaKeyFW.Builder()
                    .wrap(hashKeyBuffer, 0, hashKeyBuffer.capacity())
                    .length(clientHashKeyBuffer.capacity())
                    .value(clientHashKeyBuffer, 0, clientHashKeyBuffer.capacity())
                    .build();
            }

            messages.doKafkaBegin(traceId, authorization, affinity, qos);
            this.retainAvailable = (mqttPublishBeginEx.flags() & 1 << MqttPublishFlags.RETAIN.value()) != 0;
            if (retainAvailable)
            {
                retained.doKafkaBegin(traceId, authorization, affinity, qos);
            }
        }

        private String clientHashKey(
            String topicName)
        {
            String clientHashKey = null;
            if (clients != null)
            {
                for (Function<String, String> client : clients)
                {
                    clientHashKey = client.apply(topicName);
                    break;
                }
            }
            return clientHashKey;
        }

        private void onMqttData(
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

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            Flyweight kafkaDataEx = emptyRO;
            MqttDataExFW mqttDataEx = null;
            if (extension.sizeof() > 0)
            {
                mqttDataEx = extension.get(mqttDataExRO::tryWrap);
            }

            if ((flags & DATA_FLAG_INIT) != 0x00)
            {
                assert mqttDataEx.kind() == MqttDataExFW.KIND_PUBLISH;
                final MqttPublishDataExFW mqttPublishDataEx = mqttDataEx.publish();
                kafkaHeadersRW.wrap(kafkaHeadersBuffer, 0, kafkaHeadersBuffer.capacity());

                topicNameHeaders.forEach(th -> addHeader(helper.kafkaFilterHeaderName, th));

                addHeader(helper.kafkaLocalHeaderName, clientIdOctets);

                if (mqttPublishDataEx.expiryInterval() != -1)
                {
                    final MutableDirectBuffer expiryBuffer = new UnsafeBuffer(new byte[4]);
                    expiryBuffer.putInt(0, mqttPublishDataEx.expiryInterval(), ByteOrder.BIG_ENDIAN);
                    kafkaHeadersRW.item(h ->
                    {
                        h.nameLen(helper.kafkaTimeoutHeaderName.sizeof());
                        h.name(helper.kafkaTimeoutHeaderName);
                        h.valueLen(4);
                        h.value(expiryBuffer, 0, expiryBuffer.capacity());
                    });
                }

                if (mqttPublishDataEx.contentType().length() != -1)
                {
                    addHeader(helper.kafkaContentTypeHeaderName, mqttPublishDataEx.contentType());
                }

                if (payload.sizeof() != 0 && mqttPublishDataEx.format() != null &&
                    !mqttPublishDataEx.format().get().equals(MqttPayloadFormat.NONE))
                {
                    addHeader(helper.kafkaFormatHeaderName, mqttPublishDataEx.format());
                }

                if (mqttPublishDataEx.responseTopic().length() != -1)
                {
                    final String16FW responseTopic = mqttPublishDataEx.responseTopic();
                    addHeader(helper.kafkaReplyToHeaderName, messages.topic);
                    addHeader(helper.kafkaReplyKeyHeaderName, responseTopic);

                    addFiltersHeader(responseTopic);
                }

                if (mqttPublishDataEx.correlation().bytes() != null)
                {
                    addHeader(helper.kafkaCorrelationHeaderName, mqttPublishDataEx.correlation().bytes());
                }


                mqttPublishDataEx.properties().forEach(property ->
                    addHeader(property.key(), property.value()));

                addHeader(helper.kafkaQosHeaderName, qosLevels.get(mqttPublishDataEx.qos()));

                final int deferred = mqttPublishDataEx.deferred();
                kafkaDataEx = kafkaDataExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m -> m.produce(mp -> mp
                        .deferred(deferred)
                        .timestamp(now().toEpochMilli())
                        .partition(p -> p.partitionId(-1).partitionOffset(-1))
                        .key(b -> b.set(key))
                        .hashKey(this::setHashKey)
                        .headers(kafkaHeadersRW.build())))
                    .build();

                retainedData = (mqttPublishDataEx.flags() & 1 << MqttPublishFlags.RETAIN.value()) != 0;
            }

            fragmentedData = (flags & DATA_FLAG_FIN) == 0;
            messages.doKafkaData(traceId, authorization, budgetId, reserved, flags, payload, kafkaDataEx);

            if (retainAvailable)
            {
                if (retainedData)
                {
                    retained.doKafkaData(traceId, authorization, budgetId, reserved, flags, payload, kafkaDataEx);
                }
                else
                {
                    final KafkaFlushExFW kafkaFlushEx =
                        kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                            .typeId(kafkaTypeId)
                            .merged(m -> m.fetch(f -> f
                                .partition(p -> p.partitionId(-1).partitionOffset(-1))
                                .capabilities(c -> c.set(KafkaCapabilities.PRODUCE_ONLY))
                                .key(key)))
                            .build();
                    retained.doKafkaFlush(traceId, authorization, budgetId, kafkaFlushEx);
                }
            }

            if ((flags & DATA_FLAG_FIN) != 0x00)
            {
                retainedData = false;
            }
        }

        private void setHashKey(
            KafkaKeyFW.Builder builder)
        {
            if (hashKey != null)
            {
                builder.set(hashKey);
            }
        }

        private void addFiltersHeader(
            String16FW responseTopic)
        {
            final DirectBuffer responseBuffer = responseTopic.value();
            final int capacity = responseBuffer.capacity();

            int offset = 0;
            int matchAt = 0;
            while (offset >= 0 && offset < capacity && matchAt != -1)
            {
                matchAt = indexOfByte(responseBuffer, offset, capacity, SLASH_BYTE);
                if (matchAt != -1)
                {
                    addHeader(helper.kafkaReplyFilterHeaderName, responseBuffer, offset, matchAt - offset);
                    offset = matchAt + 1;
                }
            }
            addHeader(helper.kafkaReplyFilterHeaderName, responseBuffer, offset, capacity - offset);
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

            assert replyAck <= replySeq;

            messages.doKafkaWindow(traceId, authorization, budgetId, padding, capabilities);
            if (retainAvailable)
            {
                retained.doKafkaWindow(traceId, authorization, budgetId, padding, capabilities);
            }
        }

        private void doMqttBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            replySeq = messages.replySeq;
            replyAck = messages.replyAck;
            replyMax = messages.replyMax;
            state = MqttKafkaState.openingReply(state);

            doBegin(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity);
        }

        private void doMqttFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            replySeq = messages.replySeq;

            doFlush(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, budgetId, reserved,
                EMPTY_OCTETS);
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
            final long newInitialAck = retainAvailable ? Math.min(messages.initialAck, retained.initialAck) : messages.initialAck;
            final int newInitialMax = retainAvailable ? Math.max(messages.initialMax, retained.initialMax) : messages.initialMax;

            if (initialAck != newInitialAck || initialMax != newInitialMax)
            {
                initialAck = newInitialAck;
                initialMax = newInitialMax;

                assert initialAck <= initialSeq;

                doWindow(mqtt, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, 0, capabilities);
            }
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
    }

    private void addHeader(
        OctetsFW key,
        OctetsFW value)
    {
        kafkaHeadersRW.item(h ->
        {
            h.nameLen(key.sizeof());
            h.name(key);
            h.valueLen(value.sizeof());
            h.value(value);
        });
    }

    private void addHeader(
        OctetsFW key,
        MqttPayloadFormatFW format)
    {
        String16FW value = format.get() == MqttPayloadFormat.BINARY ? binaryFormat : textFormat;
        addHeader(key, value);
    }

    private void addHeader(
        OctetsFW key,
        String16FW value)
    {
        DirectBuffer buffer = value.value();
        kafkaHeadersRW.item(h ->
        {
            h.nameLen(key.sizeof());
            h.name(key);
            h.valueLen(value.length());
            h.value(buffer, 0, buffer.capacity());
        });
    }

    private void addHeader(
        OctetsFW key,
        DirectBuffer buffer,
        int offset,
        int length)
    {
        kafkaHeadersRW.item(h ->
        {
            h.nameLen(key.sizeof());
            h.name(key);
            h.valueLen(length);
            h.value(buffer, offset, length);
        });
    }

    private void addHeader(
        String16FW key,
        String16FW value)
    {
        DirectBuffer keyBuffer = key.value();
        DirectBuffer valueBuffer = value.value();
        kafkaHeadersRW.item(h ->
        {
            h.nameLen(key.length());
            h.name(keyBuffer, 0, keyBuffer.capacity());
            h.valueLen(value.length());
            h.value(valueBuffer, 0, valueBuffer.capacity());
        });
    }

    private static int indexOfByte(
        DirectBuffer buffer,
        int offset,
        int limit,
        byte value)
    {
        int byteAt = -1;
        for (int index = offset; index < limit; index++)
        {
            if (buffer.getByte(index) == value)
            {
                byteAt = index;
                break;
            }
        }
        return byteAt;
    }


    final class KafkaMessagesProxy
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final MqttPublishProxy delegate;
        private final String16FW topic;

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
            MqttPublishProxy delegate,
            String16FW topic)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.topic = topic;

        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity,
            int qos)
        {
            initialSeq = delegate.initialSeq;
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;
            state = MqttKafkaState.openingInitial(state);

            kafka = newKafkaStream(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, topic, qos);
        }

        private void doKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            Flyweight extension)
        {
            doData(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, flags, reserved, payload, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doKafkaEnd(
            long traceId,
            long sequence,
            long authorization)
        {
            if (!MqttKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
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
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
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

            delegate.doMqttBegin(traceId, authorization, affinity);
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
            doKafkaReset(traceId);
            delegate.doMqttAbort(traceId, authorization);
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

            delegate.doMqttEnd(traceId, authorization);
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

            delegate.doMqttFlush(traceId, authorization, budgetId, reserved);
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

            delegate.doMqttAbort(traceId, authorization);
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
            assert acknowledge >= delegate.initialAck;
            assert maximum >= delegate.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = MqttKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            delegate.doMqttWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.doMqttReset(traceId);
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
            replyAck = delegate.replyAck;
            replyMax = delegate.replyMax;
            replyPad = delegate.replyPad;

            doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding, replyPad, capabilities);
        }
    }

    final class KafkaRetainedProxy
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final MqttPublishProxy delegate;
        private final String16FW topic;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialPad;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private KafkaRetainedProxy(
            long originId,
            long routedId,
            MqttPublishProxy delegate,
            String16FW topic)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.topic = topic;
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity,
            int qos)
        {
            initialSeq = 0;
            initialAck = 0;
            initialMax = delegate.initialMax;
            state = MqttKafkaState.openingInitial(state);

            kafka = newKafkaStream(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, topic, qos);
        }

        private void doKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            Flyweight extension)
        {
            doData(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, flags, reserved, payload, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            KafkaFlushExFW extension)
        {
            doFlush(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, initialPad, extension);

            initialSeq += initialPad;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doKafkaEnd(
            long traceId,
            long sequence,
            long authorization)
        {
            if (!MqttKafkaState.initialClosed(state))
            {
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

            delegate.doMqttBegin(traceId, authorization, affinity);
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
            doKafkaReset(traceId);
            delegate.doMqttAbort(traceId, authorization);
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

            delegate.doMqttEnd(traceId, authorization);
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

            delegate.doMqttFlush(traceId, authorization, budgetId, reserved);
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

            delegate.doMqttAbort(traceId, authorization);
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
            assert acknowledge >= delegate.initialAck;
            assert maximum >= delegate.initialMax;

            initialAck = acknowledge;
            initialPad = padding;
            initialMax = maximum;
            state = MqttKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            delegate.doMqttWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.doMqttReset(traceId);
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
            replyAck = delegate.replyAck;
            replyMax = delegate.replyMax;
            replyPad = delegate.replyPad;

            doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding, replyPad, capabilities);
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
        String16FW topic,
        int qos)
    {
        final KafkaAckMode ackMode = qos > 0 ? IN_SYNC_REPLICAS : KAFKA_DEFAULT_ACK_MODE;
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(KafkaCapabilities.PRODUCE_ONLY))
                    .topic(topic)
                    .partitionsItem(p -> p.partitionId(-1).partitionOffset(-2L))
                    .ackMode(b -> b.set(ackMode)))
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
}
