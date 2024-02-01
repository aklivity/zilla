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
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaAckMode.NONE;
import static java.time.Instant.now;

import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaHeaderHelper;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionFactory.KafkaGroup;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionFactory.KafkaTopicPartition;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionFactory.PublishClientMetadata;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionFactory.PublishOffsetMetadata;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaCapabilities;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPayloadFormatFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPublishFlags;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttQoS;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedProduceFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttPublishBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttPublishDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttPublishOffsetMetadataFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttResetExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class MqttKafkaPublishFactory implements MqttKafkaStreamFactory
{
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);
    private static final String KAFKA_TYPE_NAME = "kafka";
    private static final String MQTT_TYPE_NAME = "mqtt";
    private static final byte SLASH_BYTE = (byte) '/';
    private static final int DATA_FLAG_INIT = 0x02;
    private static final int DATA_FLAG_FIN = 0x01;
    private static final int DATA_FLAG_COMPLETE = 0x03;
    private static final int PUBLISH_FLAGS_RETAINED_MASK = 1 << MqttPublishFlags.RETAIN.value();
    private static final int MQTT_PACKET_TOO_LARGE = 0x95;
    private static final int MQTT_IMPLEMENTATION_SPECIFIC_ERROR = 0x83;
    private static final int KAFKA_ERROR_RECORD_LIST_TOO_LARGE = 18;
    private static final int KAFKA_ERROR_MESSAGE_TOO_LARGE = 10;
    private static final Int2IntHashMap MQTT_REASON_CODES;
    private static final int OFFSET_METADATA_VERSION = 1;

    static
    {
        final Int2IntHashMap reasonCodes = new Int2IntHashMap(MQTT_IMPLEMENTATION_SPECIFIC_ERROR);

        reasonCodes.put(KAFKA_ERROR_RECORD_LIST_TOO_LARGE, MQTT_PACKET_TOO_LARGE);
        reasonCodes.put(KAFKA_ERROR_MESSAGE_TOO_LARGE, MQTT_PACKET_TOO_LARGE);

        MQTT_REASON_CODES = reasonCodes;
    }

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

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final MqttBeginExFW mqttBeginExRO = new MqttBeginExFW();
    private final MqttDataExFW mqttDataExRO = new MqttDataExFW();
    private final KafkaResetExFW kafkaResetExRO = new KafkaResetExFW();
    private final KafkaFlushExFW kafkaFlushExRO = new KafkaFlushExFW();

    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaFlushExFW.Builder kafkaFlushExRW = new KafkaFlushExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final MqttFlushExFW.Builder mqttFlushExRW = new MqttFlushExFW.Builder();
    private final MqttResetExFW.Builder mqttResetExRW = new MqttResetExFW.Builder();
    private final MqttPublishOffsetMetadataFW.Builder mqttOffsetMetadataRW = new MqttPublishOffsetMetadataFW.Builder();
    private final Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> kafkaHeadersRW =
        new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW());

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer kafkaHeadersBuffer;
    private final MutableDirectBuffer offsetBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final MqttKafkaHeaderHelper helper;
    private final int kafkaTypeId;
    private final int mqttTypeId;
    private final LongFunction<MqttKafkaBindingConfig> supplyBinding;
    private final String16FW binaryFormat;
    private final String16FW textFormat;
    private final Int2ObjectHashMap<String16FW> qosLevels;
    private final LongFunction<PublishClientMetadata> supplyClientMetadata;

    public MqttKafkaPublishFactory(
        MqttKafkaConfiguration config,
        EngineContext context,
        LongFunction<MqttKafkaBindingConfig> supplyBinding,
        LongFunction<PublishClientMetadata> supplyClientMetadata)
    {
        this.kafkaTypeId = context.supplyTypeId(KAFKA_TYPE_NAME);
        this.mqttTypeId = context.supplyTypeId(MQTT_TYPE_NAME);
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.kafkaHeadersBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.offsetBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
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
        this.supplyClientMetadata = supplyClientMetadata;
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
        final long affinity = begin.affinity();
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
            final int qos = mqttPublishBeginEx.qos();
            final MqttPublishProxy proxy = new MqttPublishProxy(mqtt, originId, routedId, initialId, resolvedId, affinity,
                binding, messagesTopic, binding.retainedTopic(), qos, binding.clients);
            newStream = proxy::onMqttMessage;
        }

        return newStream;
    }

    public final class MqttPublishProxy
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
        private long affinity;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private KafkaKeyFW key;
        private KafkaKeyFW hashKey;

        private Array32FW<String16FW> topicNameHeaders;
        private OctetsFW clientIdOctets;
        private boolean retainAvailable;
        private int publishFlags;
        private int packetId;
        private int qos;
        private KafkaOffsetCommitStream offsetCommit;
        private Long2ObjectHashMap<PublishOffsetMetadata> offsets;
        private Int2ObjectHashMap<KafkaTopicPartition> partitions;
        private Int2ObjectHashMap<KafkaTopicPartition> retainedPartitions;
        private Long2LongHashMap leaderEpochs;
        private KafkaGroup group;

        private MqttPublishProxy(
            MessageConsumer mqtt,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            MqttKafkaBindingConfig binding,
            String16FW kafkaMessagesTopic,
            String16FW kafkaRetainedTopic,
            int qos,
            List<Function<String, String>> clients)
        {
            this.mqtt = mqtt;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.qos = qos;
            if (qos == MqttQoS.EXACTLY_ONCE.value())
            {
                this.offsetCommit = new KafkaOffsetCommitStream(originId, resolvedId, this);
            }
            this.messages = new KafkaMessagesProxy(originId, resolvedId, affinity, this, kafkaMessagesTopic);
            this.retained = new KafkaRetainedProxy(originId, resolvedId, affinity, this, kafkaRetainedTopic);
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

            this.qos = mqttPublishBeginEx.qos();

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
            this.retainAvailable = (mqttPublishBeginEx.flags() & 1 << MqttPublishFlags.RETAIN.value()) != 0;

            if (qos == MqttQoS.EXACTLY_ONCE.value())
            {
                final PublishClientMetadata clientMetadata = supplyClientMetadata.apply(affinity);
                this.offsets = clientMetadata.offsets;
                this.partitions = clientMetadata.partitions;
                this.retainedPartitions = clientMetadata.retainedPartitions;
                this.leaderEpochs = clientMetadata.leaderEpochs;
                this.group = clientMetadata.group;
                offsetCommit.doKafkaBegin(traceId, authorization, affinity, retainAvailable);
            }
            else
            {
                messages.doKafkaBegin(traceId, authorization, affinity, qos);
                if (retainAvailable)
                {
                    retained.doKafkaBegin(traceId, authorization, affinity, qos);
                }
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
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();
            int flags = data.flags();
            int kafkaFlags = data.flags();

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

            final MqttKafkaBindingConfig binding = supplyBinding.apply(routedId);
            MqttKafkaSessionFactory.MqttSessionProxy session = binding.sessions.get(affinity);
            int deferred;

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

                deferred = mqttPublishDataEx.deferred();

                long producerId;
                short producerEpoch;
                long producerSequence;

                if (qos == MqttQoS.EXACTLY_ONCE.value())
                {
                    kafkaFlags = flags & ~DATA_FLAG_FIN;
                    final long offsetKey = offsetKey(messages.topicString, messages.qos2PartitionId);
                    final PublishOffsetMetadata metadata = offsets.get(offsetKey);
                    producerId = metadata.producerId;
                    producerEpoch = metadata.producerEpoch;
                    producerSequence = metadata.sequence;
                }
                else
                {
                    producerId = -1;
                    producerEpoch = -1;
                    producerSequence = -1;
                }

                kafkaDataEx = kafkaDataExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m -> m.produce(mp -> mp
                        .deferred(deferred)
                        .timestamp(now().toEpochMilli())
                        .producerId(producerId)
                        .producerEpoch(producerEpoch)
                        .partition(p -> p.partitionId(-1).partitionOffset(producerSequence))
                        .key(b -> b.set(key))
                        .hashKey(this::setHashKey)
                        .headers(kafkaHeadersRW.build())))
                    .build();

                publishFlags = mqttPublishDataEx.flags();
                packetId = mqttPublishDataEx.packetId();
            }
            else
            {
                deferred = 0;
            }

            messages.doKafkaData(traceId, authorization, budgetId, reserved, kafkaFlags, payload, kafkaDataEx);

            if ((flags & DATA_FLAG_FIN) != 0x00 && qos == MqttQoS.EXACTLY_ONCE.value())
            {
                commitOffsetIncomplete(traceId, authorization, messages.topicString,
                    messages.qos2PartitionId, packetId, messages, false);
            }

            if (retainAvailable)
            {
                if (hasPublishFlagRetained(publishFlags))
                {
                    long producerId;
                    short producerEpoch;
                    long producerSequence;

                    if (qos == MqttQoS.EXACTLY_ONCE.value())
                    {
                        kafkaFlags = flags & ~DATA_FLAG_FIN;
                        final long offsetKey = offsetKey(messages.topicString, messages.qos2PartitionId);
                        final PublishOffsetMetadata metadata = offsets.get(offsetKey);
                        producerId = metadata.producerId;
                        producerEpoch = metadata.producerEpoch;
                        producerSequence = metadata.sequence;

                        kafkaDataEx = kafkaDataExRW
                            .wrap(extBuffer, 0, extBuffer.capacity())
                            .typeId(kafkaTypeId)
                            .merged(m -> m.produce(mp -> mp
                                .deferred(deferred)
                                .timestamp(now().toEpochMilli())
                                .producerId(producerId)
                                .producerEpoch(producerEpoch)
                                .partition(p -> p.partitionId(-1).partitionOffset(producerSequence))
                                .key(b -> b.set(key))
                                .hashKey(this::setHashKey)
                                .headers(kafkaHeadersRW.build())))
                            .build();
                    }

                    retained.doKafkaData(traceId, authorization, budgetId, reserved, kafkaFlags, payload, kafkaDataEx);

                    if ((flags & DATA_FLAG_FIN) != 0x00 && qos == MqttQoS.EXACTLY_ONCE.value())
                    {
                        commitOffsetIncomplete(traceId, authorization, retained.topicString,
                            retained.qos2PartitionId, packetId, retained, true);
                    }
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

            if ((flags & DATA_FLAG_FIN) != 0x00 && qos != MqttQoS.EXACTLY_ONCE.value())
            {
                publishFlags = 0;
            }
        }

        public void commitOffsetIncomplete(
            long traceId,
            long authorization,
            String topic,
            int partitionId,
            int packetId,
            KafkaProxy kafka,
            boolean retained)
        {
            final long offsetKey = offsetKey(topic, partitionId);
            final PublishOffsetMetadata metadata = offsets.get(offsetKey);
            metadata.packetIds.add(packetId);
            Flyweight offsetCommitEx = kafkaDataExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .offsetCommit(o -> o
                    .topic(topic)
                    .progress(p -> p
                        .partitionId(partitionId)
                        .partitionOffset(metadata.sequence)
                        .metadata(offsetMetadataToString(metadata)))
                    .generationId(group.generationId)
                    .leaderEpoch((int) leaderEpochs.get(offsetKey)))
                .build();

            offsetCommit.unfinishedKafkas.add(kafka);
            if (retained)
            {
                retainedPartitions.put(packetId, new KafkaTopicPartition(topic, partitionId));
            }
            else
            {
                partitions.put(packetId, new KafkaTopicPartition(topic, partitionId));
            }
            offsetCommit.doKafkaData(traceId, authorization, 0, DATA_FLAG_COMPLETE, offsetCommitEx);
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
            if (offsetCommit != null)
            {
                offsetCommit.doKafkaEnd(traceId, authorization);
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
            if (offsetCommit != null)
            {
                offsetCommit.doKafkaAbort(traceId, authorization);
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
            if (offsetCommit != null)
            {
                offsetCommit.doKafkaReset(traceId);
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

        public void doMqttWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
            final boolean retainedFlag = hasPublishFlagRetained(publishFlags);
            final long newInitialAck = retainedFlag ? Math.min(messages.initialAck, retained.initialAck) : messages.initialAck;
            final int newInitialMax = retainedFlag ? Math.max(messages.initialMax, retained.initialMax) : messages.initialMax;

            if (initialAck != newInitialAck || initialMax != newInitialMax)
            {
                initialAck = newInitialAck;
                initialMax = newInitialMax;
                int minimum = 0;
                if (qos == MqttQoS.EXACTLY_ONCE.value())
                {
                    minimum = initialMax;
                }

                assert initialAck <= initialSeq;
                doWindow(mqtt, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, minimum, capabilities);
            }
        }

        private void doMqttReset(
            long traceId,
            Flyweight extension)
        {
            if (!MqttKafkaState.initialClosed(state))
            {
                state = MqttKafkaState.closeInitial(state);

                doReset(mqtt, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, extension);
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

    private static boolean hasPublishFlagRetained(
        int publishFlags)
    {
        return (publishFlags & PUBLISH_FLAGS_RETAINED_MASK) != 0;
    }


    public abstract class KafkaProxy
    {
        protected MessageConsumer kafka;
        protected long mqttAffinity;
        protected final long originId;
        protected final long routedId;
        protected final long initialId;
        protected final long replyId;
        protected final String16FW topic;
        protected final String topicString;

        protected MqttPublishProxy delegate;
        protected int state;

        protected long initialSeq;
        protected long initialAck;
        protected int initialMax;
        protected int initialPad;

        protected long replySeq;
        protected long replyAck;
        protected int replyMax;
        protected int replyPad;
        protected int qos2PartitionId = -1;

        public KafkaProxy(
            long originId,
            long routedId,
            long mqttAffinity,
            MqttPublishProxy delegate,
            String16FW topic)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.mqttAffinity = mqttAffinity;
            this.delegate = delegate;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.topic = topic;
            this.topicString = topic.asString();
        }

        public void sendKafkaFinData(
            long traceId,
            long authorization)
        {
            doKafkaData(traceId, authorization, 0, 0, DATA_FLAG_FIN, EMPTY_OCTETS, EMPTY_OCTETS);
        }

        abstract void doKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            Flyweight extension);
    }

    public final class KafkaMessagesProxy extends KafkaProxy
    {
        public KafkaMessagesProxy(
            long originId,
            long routedId,
            long mqttAffinity,
            MqttPublishProxy delegate,
            String16FW topic)
        {
            super(originId, routedId, mqttAffinity, delegate, topic);
        }

        public void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity,
            int qos)
        {
            initialSeq = delegate.initialSeq;
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;

            if (!MqttKafkaState.initialOpening(state))
            {
                state = MqttKafkaState.openingInitial(state);

                kafka = newKafkaStream(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, topic, qos);
            }
        }

        void doKafkaData(
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

        public void onKafkaMessage(
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
            doKafkaWindow(traceId, authorization, 0, 0, 0);
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
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            final ExtensionFW flushEx = extension.get(extensionRO::tryWrap);
            final KafkaFlushExFW kafkaFlushEx =
                flushEx != null && flushEx.typeId() == kafkaTypeId ? extension.get(kafkaFlushExRO::tryWrap) : null;
            final KafkaMergedFlushExFW kafkaMergedFlushEx =
                kafkaFlushEx != null && kafkaFlushEx.kind() == KafkaFlushExFW.KIND_MERGED ? kafkaFlushEx.merged() : null;
            final KafkaMergedProduceFlushExFW kafkaMergedProduceFlushEx = kafkaMergedFlushEx != null &&
                kafkaMergedFlushEx.kind() == KafkaMergedFlushExFW.KIND_PRODUCE ? kafkaMergedFlushEx.produce() : null;

            if (kafkaMergedProduceFlushEx != null)
            {
                this.qos2PartitionId = kafkaMergedProduceFlushEx.partitionId();

                if (!delegate.retainAvailable || delegate.retained.qos2PartitionId != -1)
                {
                    delegate.doMqttWindow(traceId, authorization, 0, 0, 0);
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
            final boolean wasOpen = MqttKafkaState.initialOpened(state);

            assert acknowledge <= sequence;
            assert acknowledge >= initialAck;
            assert maximum >= initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            initialPad = padding;
            state = MqttKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            if (wasOpen)
            {
                delegate.doMqttWindow(traceId, authorization, budgetId, padding, capabilities);
            }
            else if (delegate.qos < MqttQoS.EXACTLY_ONCE.value())
            {
                delegate.doMqttWindow(traceId, authorization, budgetId, padding, capabilities);
            }
            else
            {
                final KafkaKeyFW hashKey = delegate.hashKey != null ? delegate.hashKey : delegate.key;
                final KafkaFlushExFW kafkaFlushEx =
                    kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .merged(m -> m.produce(p -> p.hashKey(hashKey)))
                        .build();
                doKafkaFlush(traceId, authorization, 0, kafkaFlushEx);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= initialAck;

            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            final OctetsFW extension = reset.extension();
            final ExtensionFW resetEx = extension.get(extensionRO::tryWrap);
            final KafkaResetExFW kafkaResetEx =
                resetEx != null && resetEx.typeId() == kafkaTypeId ? extension.get(kafkaResetExRO::tryWrap) : null;

            Flyweight mqttResetEx = EMPTY_OCTETS;
            if (kafkaResetEx != null)
            {
                mqttResetEx = mqttResetExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mqttTypeId)
                    .reasonCode(MQTT_REASON_CODES.get(kafkaResetEx.error()))
                    .build();
            }

            delegate.doMqttReset(traceId, mqttResetEx);
        }

        private void doKafkaReset(
            long traceId)
        {
            if (!MqttKafkaState.replyClosed(state))
            {
                state = MqttKafkaState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, EMPTY_OCTETS);
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

    final class KafkaRetainedProxy extends KafkaProxy
    {
        KafkaRetainedProxy(
            long originId,
            long routedId,
            long mqttAffinity,
            MqttPublishProxy delegate,
            String16FW topic)
        {
            super(originId, routedId, mqttAffinity, delegate, topic);
        }

        public void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity,
            int qos)
        {
            initialSeq = 0;
            initialAck = 0;
            initialMax = delegate.initialMax;

            if (!MqttKafkaState.initialOpening(state))
            {

                state = MqttKafkaState.openingInitial(state);

                kafka = newKafkaStream(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, topic, qos);
            }
        }

        void doKafkaData(
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
            doKafkaWindow(traceId, authorization, 0, 0, 0);
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
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            final ExtensionFW flushEx = extension.get(extensionRO::tryWrap);
            final KafkaFlushExFW kafkaFlushEx =
                flushEx != null && flushEx.typeId() == kafkaTypeId ? extension.get(kafkaFlushExRO::tryWrap) : null;
            final KafkaMergedFlushExFW kafkaMergedFlushEx =
                kafkaFlushEx != null && kafkaFlushEx.kind() == KafkaFlushExFW.KIND_MERGED ? kafkaFlushEx.merged() : null;
            final KafkaMergedProduceFlushExFW kafkaMergedProduceFlushEx = kafkaMergedFlushEx != null &&
                kafkaMergedFlushEx.kind() == KafkaMergedFlushExFW.KIND_PRODUCE ? kafkaMergedFlushEx.produce() : null;

            if (kafkaMergedProduceFlushEx != null)
            {
                this.qos2PartitionId = kafkaMergedProduceFlushEx.partitionId();

                if (delegate.messages.qos2PartitionId != -1)
                {
                    delegate.doMqttWindow(traceId, authorization, 0, 0, 0);
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
            final boolean wasOpen = MqttKafkaState.initialOpened(state);

            assert acknowledge <= sequence;
            assert acknowledge >= initialAck;
            assert maximum >= initialMax;

            initialAck = acknowledge;
            initialPad = padding;
            initialMax = maximum;
            state = MqttKafkaState.openInitial(state);

            assert initialAck <= initialSeq;


            if (wasOpen)
            {
                delegate.doMqttWindow(traceId, authorization, budgetId, padding, capabilities);
            }
            else if (delegate.qos < MqttQoS.EXACTLY_ONCE.value())
            {
                delegate.doMqttWindow(traceId, authorization, budgetId, padding, capabilities);
            }
            else
            {
                final KafkaKeyFW hashKey = delegate.hashKey != null ? delegate.hashKey : delegate.key;
                final KafkaFlushExFW kafkaFlushEx =
                    kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .merged(m -> m.produce(p -> p.hashKey(hashKey)))
                        .build();
                doKafkaFlush(traceId, authorization, 0, kafkaFlushEx);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= initialAck;

            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            final OctetsFW extension = reset.extension();
            final ExtensionFW resetEx = extension.get(extensionRO::tryWrap);
            final KafkaResetExFW kafkaResetEx =
                resetEx != null && resetEx.typeId() == kafkaTypeId ? extension.get(kafkaResetExRO::tryWrap) : null;

            Flyweight mqttResetEx = EMPTY_OCTETS;
            if (kafkaResetEx != null)
            {
                mqttResetEx = mqttResetExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mqttTypeId)
                    .reasonCode(MQTT_REASON_CODES.get(kafkaResetEx.error()))
                    .build();
            }

            delegate.doMqttReset(traceId, mqttResetEx);
        }

        private void doKafkaReset(
            long traceId)
        {
            if (!MqttKafkaState.replyClosed(state))
            {
                state = MqttKafkaState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, EMPTY_OCTETS);
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

    private final class KafkaOffsetCommitStream
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final MqttPublishProxy delegate;
        private final Queue<KafkaProxy> unfinishedKafkas;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private boolean retainAvailable;


        private KafkaOffsetCommitStream(
            long originId,
            long routedId,
            MqttPublishProxy delegate)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.unfinishedKafkas = new LinkedList<>();
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity,
            boolean retainAvailable)
        {
            initialSeq = delegate.initialSeq;
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;
            state = MqttKafkaState.openingInitial(state);
            this.retainAvailable = retainAvailable;

            kafka = newOffsetCommitStream(this::onOffsetCommitMessage, originId, routedId, initialId, initialSeq, initialAck,
                initialMax, traceId, authorization, affinity, delegate.group);
        }

        private void doKafkaEnd(
            long traceId,
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
                onKafkaBegin(begin);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
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
            final long affinity = begin.affinity();

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

        private void onKafkaWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final boolean wasOpen = MqttKafkaState.initialOpened(state);

            assert acknowledge <= sequence;
            assert acknowledge >= initialAck;
            assert maximum >= initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = MqttKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            if (!wasOpen)
            {
                delegate.messages.doKafkaBegin(traceId, authorization, 0, MqttQoS.EXACTLY_ONCE.value());
                if (retainAvailable)
                {
                    delegate.retained.doKafkaBegin(traceId, authorization, 0, MqttQoS.EXACTLY_ONCE.value());
                }
            }
            else
            {
                final MqttKafkaPublishFactory.KafkaProxy kafka = unfinishedKafkas.remove();
                kafka.sendKafkaFinData(traceId, authorization);
            }
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = MqttKafkaState.closeReply(state);

            assert replyAck <= replySeq;
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

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;

            delegate.doMqttReset(traceId, EMPTY_OCTETS);
        }

        private void doKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int flags,
            Flyweight extension)
        {
            doData(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, flags, 0, EMPTY_OCTETS, extension);

            assert initialSeq <= initialAck + initialMax;
        }

        private void doKafkaReset(
            long traceId)
        {
            if (!MqttKafkaState.replyClosed(state))
            {
                state = MqttKafkaState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, EMPTY_OCTETS);
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

    private String16FW offsetMetadataToString(
        PublishOffsetMetadata metadata)
    {
        mqttOffsetMetadataRW.wrap(offsetBuffer, 0, offsetBuffer.capacity());
        mqttOffsetMetadataRW.version(OFFSET_METADATA_VERSION);
        mqttOffsetMetadataRW.producerId(metadata.producerId);
        mqttOffsetMetadataRW.producerEpoch(metadata.producerEpoch);

        if (metadata.packetIds != null)
        {
            metadata.packetIds.forEach(p -> mqttOffsetMetadataRW.appendPacketIds(p.shortValue()));
        }
        final MqttPublishOffsetMetadataFW offsetMetadata = mqttOffsetMetadataRW.build();
        return new String16FW(BitUtil.toHex(offsetMetadata.buffer().byteArray(),
            offsetMetadata.offset(), offsetMetadata.limit()));
    }

    private static long offsetKey(
        String topic,
        int partitionId)
    {
        final int topicHashCode = System.identityHashCode(topic.intern());
        return ((long) topicHashCode << 32) | (partitionId & 0xFFFFFFFFL);
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
        final KafkaAckMode ackMode = qos > 0 ? IN_SYNC_REPLICAS : NONE;
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

    private MessageConsumer newOffsetCommitStream(
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
        KafkaGroup group)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(kafkaTypeId)
                .offsetCommit(o -> o
                    .groupId(group.groupId)
                    .memberId(group.memberId)
                    .instanceId(group.instanceId))
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
        long traceId,
        Flyweight extension)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }
}
