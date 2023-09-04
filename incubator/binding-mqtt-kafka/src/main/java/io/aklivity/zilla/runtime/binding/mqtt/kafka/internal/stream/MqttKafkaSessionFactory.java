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

import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration.MQTT_CLIENTS_GROUP_ID;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntHashSet;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongArrayList;
import org.agrona.collections.Object2LongHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.InstanceId;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaHeaderHelper;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaCapabilities;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaEvaluation;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttExpirySignalFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPayloadFormatFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPublishFlags;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSessionFlags;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSessionSignalFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSessionStateFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttTime;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttWillMessageFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttWillSignalFW;
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
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaGroupDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttResetExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttSessionBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttSessionDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.Configuration.IntPropertyDef;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public class MqttKafkaSessionFactory implements MqttKafkaStreamFactory
{
    private static final byte SLASH_BYTE = (byte) '/';
    private static final KafkaAckMode KAFKA_DEFAULT_ACK_MODE = KafkaAckMode.LEADER_ONLY;
    private static final String KAFKA_TYPE_NAME = "kafka";
    private static final String MQTT_TYPE_NAME = "mqtt";
    private static final String MIGRATE_KEY_POSTFIX = "#migrate";
    private static final String WILL_SIGNAL_KEY_POSTFIX = "#will-signal";
    private static final String EXPIRY_SIGNAL_KEY_POSTFIX = "#expiry-signal";
    private static final String WILL_KEY_POSTFIX = "#will-";
    private static final String GROUP_PROTOCOL = "highlander";
    private static final String16FW SENDER_ID_NAME = new String16FW("sender-id");
    private static final String16FW TYPE_HEADER_NAME = new String16FW("type");
    private static final String16FW WILL_SIGNAL_NAME = new String16FW("will-signal");
    private static final String16FW EXPIRY_SIGNAL_NAME = new String16FW("expiry-signal");
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);
    private static final int DATA_FLAG_COMPLETE = 0x03;
    public static final String MQTT_CLIENTS_GROUP_ID = "mqtt-clients";
    private static final int SIGNAL_DELIVER_WILL_MESSAGE = 1;
    private static final int SIGNAL_CONNECT_WILL_STREAM = 2;
    private static final int SIGNAL_EXPIRE_SESSION = 3;
    private static final int SIZE_OF_UUID = 38;

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
    private final MqttWillMessageFW.Builder mqttMessageRW = new MqttWillMessageFW.Builder();
    private final MqttSessionSignalFW.Builder mqttSessionSignalRW = new MqttSessionSignalFW.Builder();
    private final Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> kafkaHeadersRW =
        new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW());

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final SignalFW signalRO = new SignalFW();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final MqttBeginExFW mqttBeginExRO = new MqttBeginExFW();
    private final MqttSessionStateFW mqttSessionStateRO = new MqttSessionStateFW();
    private final MqttSessionSignalFW mqttSessionSignalRO = new MqttSessionSignalFW();
    private final MqttWillMessageFW mqttWillRO = new MqttWillMessageFW();
    private final MqttDataExFW mqttDataExRO = new MqttDataExFW();
    private final MqttResetExFW.Builder mqttResetExRW = new MqttResetExFW.Builder();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();
    private final KafkaResetExFW kafkaResetExRO = new KafkaResetExFW();
    private final KafkaFlushExFW kafkaFlushExRO = new KafkaFlushExFW();
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final KafkaFlushExFW.Builder kafkaFlushExRW = new KafkaFlushExFW.Builder();
    private final MqttBeginExFW.Builder mqttSessionBeginExRW = new MqttBeginExFW.Builder();
    private final String16FW binaryFormat = new String16FW(MqttPayloadFormat.BINARY.name());
    private final String16FW textFormat = new String16FW(MqttPayloadFormat.TEXT.name());

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer kafkaHeadersBuffer;
    private final MutableDirectBuffer willMessageBuffer;
    private final MutableDirectBuffer sessionSignalBuffer;
    private final MutableDirectBuffer willKeyBuffer;
    private final MutableDirectBuffer sessionSignalKeyBuffer;
    private final MutableDirectBuffer sessionExtBuffer;
    private final BufferPool bufferPool;
    private final BindingHandler streamFactory;
    private final Signaler signaler;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int kafkaTypeId;
    private final int mqttTypeId;
    private final LongFunction<MqttKafkaBindingConfig> supplyBinding;
    private final Supplier<String> supplySessionId;
    private final Supplier<String> supplyWillId;
    private final Supplier<String> supplyLifetimeId;
    private final LongSupplier supplyTime;
    private final Long2ObjectHashMap<String> sessionIds;
    private final MqttKafkaHeaderHelper helper;
    private final int coreIndex;
    private final Supplier<Long> supplyTraceId;
    private final Object2ObjectHashMap<String16FW, LongArrayList> willDeliverIds;
    private final Object2LongHashMap<String16FW> sessionExpiryIds;
    private final InstanceId instanceId;
    private final boolean willAvailable;
    private final int reconnectDelay;
    private final int sessionExpiryIntervalMaxMillis;
    private final int sessionExpiryIntervalMinMillis;

    private int reconnectAttempt;

    public MqttKafkaSessionFactory(
        MqttKafkaConfiguration config,
        EngineContext context,
        InstanceId instanceId,
        LongFunction<MqttKafkaBindingConfig> supplyBinding,
        IntPropertyDef reconnectDelay)
    {
        this.kafkaTypeId = context.supplyTypeId(KAFKA_TYPE_NAME);
        this.mqttTypeId = context.supplyTypeId(MQTT_TYPE_NAME);
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.kafkaHeadersBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.willMessageBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.sessionSignalBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.willKeyBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.sessionSignalKeyBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.sessionExtBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.bufferPool = context.bufferPool();
        this.helper = new MqttKafkaHeaderHelper();
        this.streamFactory = context.streamFactory();
        this.signaler = context.signaler();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyBinding = supplyBinding;
        this.supplySessionId = config.sessionId();
        this.supplyWillId = config.willId();
        this.supplyLifetimeId = config.lifetimeId();
        this.supplyTime = config.time();
        this.supplyTraceId = context::supplyTraceId;
        this.sessionIds = new Long2ObjectHashMap<>();
        this.coreIndex = context.index();
        this.willAvailable = config.willAvailable();
        this.willDeliverIds = new Object2ObjectHashMap<>();
        this.sessionExpiryIds = new Object2LongHashMap<>(-1);
        this.instanceId = instanceId;
        this.reconnectDelay = reconnectDelay.getAsInt(config);
        this.sessionExpiryIntervalMaxMillis = config.sessionExpiryIntervalMax();
        this.sessionExpiryIntervalMinMillis = config.sessionExpiryIntervalMin();
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
            final String16FW sessionTopic = binding.sessionsTopic();
            newStream = new MqttSessionProxy(mqtt, originId, routedId, initialId, resolvedId,
                binding.id, sessionTopic)::onMqttMessage;
        }

        return newStream;
    }

    @Override
    public void onAttached(
        long bindingId)
    {
        MqttKafkaBindingConfig binding = supplyBinding.apply(bindingId);
        if (willAvailable && coreIndex == 0)
        {
            Optional<MqttKafkaRouteConfig> route = binding.routes.stream().findFirst();
            final long routeId = route.map(mqttKafkaRouteConfig -> mqttKafkaRouteConfig.id).orElse(0L);

            binding.willProxy = new KafkaSignalStream(binding.id, routeId,
                binding.sessionsTopic(), binding.messagesTopic(), binding.retainedTopic());
            binding.willProxy.doKafkaBegin(currentTimeMillis());
        }
        sessionIds.put(bindingId, supplySessionId.get());
    }

    @Override
    public void onDetached(
        long bindingId)
    {
        MqttKafkaBindingConfig binding = supplyBinding.apply(bindingId);
        sessionIds.remove(bindingId);

        if (binding.willProxy != null)
        {
            binding.willProxy.doKafkaEnd(supplyTraceId.get(), 0);
            binding.willProxy = null;
        }
    }

    private final class MqttSessionProxy
    {
        private final MessageConsumer mqtt;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final String16FW sessionId;
        private final String16FW sessionsTopic;
        private String lifetimeId;
        private KafkaSessionStream session;
        private KafkaGroupStream group;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private String16FW clientId;
        private String16FW clientIdMigrate;
        private String serverRef;
        private int sessionExpiryMillis;
        private int sessionFlags;
        private int sessionPadding;
        private String willId;
        private int delay;

        private MqttSessionProxy(
            MessageConsumer mqtt,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long bindingId,
            String16FW sessionsTopic)
        {
            this.mqtt = mqtt;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.session = new KafkaFetchWillSignalStream(originId, resolvedId, this);
            this.sessionsTopic = sessionsTopic;
            this.sessionId = new String16FW(sessionIds.get(bindingId));
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

            assert mqttBeginEx.kind() == MqttBeginExFW.KIND_SESSION;
            final MqttSessionBeginExFW mqttSessionBeginEx = mqttBeginEx.session();

            final String clientId0 = mqttSessionBeginEx.clientId().asString();
            this.clientId = new String16FW(clientId0);
            this.clientIdMigrate = new String16FW(clientId0 + MIGRATE_KEY_POSTFIX);

            sessionExpiryMillis = (int) SECONDS.toMillis(mqttSessionBeginEx.expiry());
            sessionFlags = mqttSessionBeginEx.flags();
            serverRef = mqttSessionBeginEx.serverRef().asString();

            if (!isSetWillFlag(sessionFlags) || isSetCleanStart(sessionFlags))
            {
                final long routedId = session.routedId;
                session = new KafkaSessionSignalStream(originId, routedId, this);
            }
            if (isSetWillFlag(sessionFlags))
            {
                final int willSignalSize = 1 + clientId.sizeof() + SIZE_OF_INT + SIZE_OF_LONG + SIZE_OF_UUID + SIZE_OF_UUID +
                    instanceId.instanceId().sizeof();
                sessionPadding = willSignalSize + SIZE_OF_UUID + SIZE_OF_UUID;
            }
            final int expirySignalSize = 1 + clientId.sizeof() + SIZE_OF_INT + SIZE_OF_LONG + instanceId.instanceId().sizeof();
            sessionPadding += expirySignalSize;

            session.doKafkaBeginIfNecessary(traceId, authorization, affinity);
        }

        private void onMqttData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW extension = data.extension();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            final DirectBuffer buffer = payload.buffer();
            final int offset = payload.offset();
            final int limit = payload.limit();


            final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
            final MqttDataExFW mqttDataEx =
                dataEx != null && dataEx.typeId() == mqttTypeId ? extension.get(mqttDataExRO::tryWrap) : null;
            final MqttSessionDataExFW mqttSessionDataEx =
                mqttDataEx != null && mqttDataEx.kind() == MqttDataExFW.KIND_SESSION ? mqttDataEx.session() : null;

            Flyweight kafkaDataEx;
            Flyweight kafkaPayload;
            if (mqttSessionDataEx != null)
            {
                switch (mqttSessionDataEx.kind().get())
                {
                case WILL:
                    if (lifetimeId == null)
                    {
                        lifetimeId = supplyLifetimeId.get();
                    }
                    this.willId  = supplyWillId.get();

                    String16FW key = new String16FW.Builder().wrap(willKeyBuffer, 0, willKeyBuffer.capacity())
                        .set(clientId.asString() + WILL_KEY_POSTFIX + lifetimeId, StandardCharsets.UTF_8).build();
                    kafkaDataEx = kafkaDataExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .merged(m -> m
                            .deferred(0)
                            .timestamp(System.currentTimeMillis())
                            .partition(p -> p.partitionId(-1).partitionOffset(-1))
                            .key(b -> b.length(key.length())
                                .value(key.value(), 0, key.length()))
                            .hashKey(b -> b.length(clientId.length())
                                .value(clientId.value(), 0, clientId.length())))
                        .build();

                    MqttWillMessageFW will = mqttWillRO.tryWrap(buffer, offset, limit);
                    this.delay = (int) Math.min(SECONDS.toMillis(will.delay()), sessionExpiryMillis);
                    final int expiryInterval = will.expiryInterval() == -1 ? -1 :
                        (int) TimeUnit.SECONDS.toMillis(will.expiryInterval());
                    final MqttWillMessageFW.Builder willMessageBuilder =
                        mqttMessageRW.wrap(willMessageBuffer, 0, willMessageBuffer.capacity())
                            .topic(will.topic())
                            .delay(delay)
                            .qos(will.qos())
                            .flags(will.flags())
                            .expiryInterval(expiryInterval)
                            .contentType(will.contentType())
                            .format(will.format())
                            .responseTopic(will.responseTopic())
                            .lifetimeId(lifetimeId)
                            .willId(willId)
                            .correlation(will.correlation())
                            .properties(will.properties())
                            .payload(will.payload());

                    kafkaPayload = willMessageBuilder.build();
                    session.doKafkaData(traceId, authorization, budgetId,
                        kafkaPayload.sizeof(), flags, kafkaPayload, kafkaDataEx);


                    String16FW willSignalKey = new String16FW.Builder()
                        .wrap(sessionSignalKeyBuffer, 0, sessionSignalKeyBuffer.capacity())
                        .set(clientId.asString() + WILL_SIGNAL_KEY_POSTFIX, StandardCharsets.UTF_8).build();
                    Flyweight willSignalKafkaDataEx = kafkaDataExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .merged(m -> m
                            .deferred(0)
                            .timestamp(System.currentTimeMillis())
                            .partition(p -> p.partitionId(-1).partitionOffset(-1))
                            .key(b -> b.length(willSignalKey.length())
                                .value(willSignalKey.value(), 0, willSignalKey.length()))
                            .hashKey(b -> b.length(clientId.length())
                                .value(clientId.value(), 0, clientId.length()))
                            .headersItem(h ->
                                h.nameLen(TYPE_HEADER_NAME.length())
                                    .name(TYPE_HEADER_NAME.value(), 0, TYPE_HEADER_NAME.length())
                                    .valueLen(WILL_SIGNAL_NAME.length())
                                    .value(WILL_SIGNAL_NAME.value(), 0, WILL_SIGNAL_NAME.length())))
                        .build();

                    final MqttSessionSignalFW willSignal =
                        mqttSessionSignalRW.wrap(sessionSignalBuffer, 0, sessionSignalBuffer.capacity())
                            .will(w -> w
                                .instanceId(instanceId.instanceId())
                                .clientId(clientId)
                                .delay(delay)
                                .deliverAt(MqttTime.UNKNOWN.value())
                                .lifetimeId(lifetimeId)
                                .willId(willId))
                            .build();

                    session.doKafkaData(traceId, authorization, budgetId, willSignal.sizeof(), flags,
                        willSignal, willSignalKafkaDataEx);


                    doFlushProduceAndFetchWithFilter(traceId, authorization, budgetId);
                    break;
                case STATE:
                    kafkaDataEx = kafkaDataExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .merged(m -> m
                            .deferred(0)
                            .timestamp(System.currentTimeMillis())
                            .partition(p -> p.partitionId(-1).partitionOffset(-1))
                            .key(b -> b.length(clientId.length())
                                .value(clientId.value(), 0, clientId.length())))
                        .build();

                    kafkaPayload = payload.sizeof() > 0 ? mqttSessionStateRO.wrap(buffer, offset, limit) : EMPTY_OCTETS;

                    session.doKafkaData(traceId, authorization, budgetId,
                        reserved, flags, kafkaPayload, kafkaDataEx);
                    break;
                }
            }
        }

        private void doFlushProduceAndFetchWithFilter(
            long traceId,
            long authorization,
            long budgetId)
        {
            final KafkaFlushExFW kafkaFlushEx =
                kafkaFlushExRW.wrap(writeBuffer, FlushFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m ->
                    {
                        m.capabilities(c -> c.set(KafkaCapabilities.PRODUCE_AND_FETCH));
                        m.filtersItem(f -> f.conditionsItem(ci ->
                            ci.key(kb -> kb.length(clientId.length())
                                .value(clientId.value(), 0, clientId.length()))));
                        m.filtersItem(f ->
                        {
                            f.conditionsItem(ci ->
                                ci.key(kb -> kb.length(clientIdMigrate.length())
                                    .value(clientIdMigrate.value(), 0, clientIdMigrate.length())));
                            f.conditionsItem(i -> i.not(n -> n.condition(c -> c.header(h ->
                                h.nameLen(SENDER_ID_NAME.length())
                                    .name(SENDER_ID_NAME.value(), 0, SENDER_ID_NAME.length())
                                    .valueLen(sessionId.length())
                                    .value(sessionId.value(), 0, sessionId.length())))));
                        });
                    })
                    .build();

            session.doKafkaFlush(traceId, authorization, budgetId, 0, kafkaFlushEx);
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

            if (isSetWillFlag(sessionFlags))
            {
                // Cleanup will message + will signal
                String16FW key = new String16FW.Builder().wrap(willKeyBuffer, 0, willKeyBuffer.capacity())
                    .set(clientId.asString() + WILL_KEY_POSTFIX + lifetimeId, StandardCharsets.UTF_8).build();
                Flyweight kafkaWillDataEx = kafkaDataExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m -> m
                        .deferred(0)
                        .timestamp(System.currentTimeMillis())
                        .partition(p -> p.partitionId(-1).partitionOffset(-1))
                        .key(b -> b.length(key.length())
                            .value(key.value(), 0, key.length()))
                        .hashKey(b -> b.length(clientId.length())
                            .value(clientId.value(), 0, clientId.length())))
                    .build();

                session.doKafkaData(traceId, authorization, 0, 0, DATA_FLAG_COMPLETE,
                    null, kafkaWillDataEx);

                String16FW willSignalKey = new String16FW.Builder()
                    .wrap(sessionSignalKeyBuffer, 0, sessionSignalKeyBuffer.capacity())
                    .set(clientId.asString() + WILL_SIGNAL_KEY_POSTFIX, StandardCharsets.UTF_8).build();
                Flyweight willSignalKafkaDataEx = kafkaDataExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m -> m
                        .deferred(0)
                        .timestamp(System.currentTimeMillis())
                        .partition(p -> p.partitionId(-1).partitionOffset(-1))
                        .key(b -> b.length(willSignalKey.length())
                            .value(willSignalKey.value(), 0, willSignalKey.length()))
                        .hashKey(b -> b.length(clientId.length())
                            .value(clientId.value(), 0, clientId.length()))
                        .headersItem(h ->
                            h.nameLen(TYPE_HEADER_NAME.length())
                                .name(TYPE_HEADER_NAME.value(), 0, TYPE_HEADER_NAME.length())
                                .valueLen(WILL_SIGNAL_NAME.length())
                                .value(WILL_SIGNAL_NAME.value(), 0, WILL_SIGNAL_NAME.length())))
                    .build();

                session.doKafkaData(traceId, authorization, 0, 0, DATA_FLAG_COMPLETE,
                    null, willSignalKafkaDataEx);
            }

            final MqttSessionSignalFW expirySignal =
                mqttSessionSignalRW.wrap(sessionSignalBuffer, 0, sessionSignalBuffer.capacity())
                    .expiry(w -> w
                        .instanceId(instanceId.instanceId())
                        .clientId(clientId)
                        .delay(sessionExpiryMillis)
                        .expireAt(supplyTime.getAsLong() + sessionExpiryMillis))
                    .build();
            session.sendExpirySignal(authorization, traceId, expirySignal); // expire at expireAt

            session.doKafkaEnd(traceId, authorization);
            if (group != null)
            {
                group.doKafkaEnd(traceId, authorization);
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

            if (isSetWillFlag(sessionFlags))
            {
                session.sendWillSignal(traceId, authorization);
            }
            final MqttSessionSignalFW expirySignal =
                mqttSessionSignalRW.wrap(sessionSignalBuffer, 0, sessionSignalBuffer.capacity())
                    .expiry(w -> w
                        .instanceId(instanceId.instanceId())
                        .clientId(clientId)
                        .delay(sessionExpiryMillis)
                        .expireAt(supplyTime.getAsLong() + sessionExpiryMillis))
                    .build();
            session.sendExpirySignal(authorization, traceId, expirySignal); // expire at expireAt

            session.doKafkaAbort(traceId, authorization);
            if (group != null)
            {
                group.doKafkaAbort(traceId, authorization);
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

            session.doKafkaReset(traceId);
            if (group != null)
            {
                group.doKafkaReset(traceId);
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
            final boolean wasOpen = MqttKafkaState.replyOpened(state);

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = padding;
            state = MqttKafkaState.openReply(state);

            assert replyAck <= replySeq;

            session.doKafkaWindow(traceId, authorization, budgetId, capabilities);
            if (!wasOpen && group != null)
            {
                group.doKafkaWindow(traceId, authorization, budgetId, padding, capabilities);
            }
        }

        private void doMqttBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            if (!MqttKafkaState.replyOpening(state))
            {
                replySeq = session.replySeq;
                replyAck = session.replyAck;
                replyMax = session.replyMax;
                state = MqttKafkaState.openingReply(state);

                doBegin(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, extension);
            }
        }

        private void doMqttData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            MqttSessionStateFW sessionState)
        {
            Flyweight state = sessionState == null ? EMPTY_OCTETS : sessionState;
            final DirectBuffer buffer = state.buffer();
            final int offset = state.offset();
            final int limit = state.limit();
            final int length = limit - offset;

            doData(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, flags, reserved, buffer, offset, length, EMPTY_OCTETS);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doMqttData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload)
        {
            doData(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, flags, reserved, payload, EMPTY_OCTETS);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doMqttAbort(
            long traceId,
            long authorization)
        {
            if (!MqttKafkaState.replyClosed(state))
            {
                replySeq = session.replySeq;
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
                replySeq = session.replySeq;
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
            initialAck = session.initialAck - padding;
            initialMax = session.initialMax;

            doWindow(mqtt, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding, 0, capabilities);
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

    public final class KafkaSignalStream
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final String16FW sessionsTopic;
        private final String16FW messagesTopic;
        private final String16FW retainedTopic;
        private final Object2ObjectHashMap<String16FW, KafkaFetchWillStream> willFetchers;
        private final Int2ObjectHashMap<String16FW> expiryClientIds;

        private IntHashSet partitions;
        private int state;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long reconnectAt;

        private KafkaSignalStream(
            long originId,
            long routedId,
            String16FW sessionsTopic,
            String16FW messagesTopic,
            String16FW retainedTopic)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.sessionsTopic = sessionsTopic;
            this.messagesTopic = messagesTopic;
            this.retainedTopic = retainedTopic;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.willFetchers = new Object2ObjectHashMap<>();
            this.expiryClientIds = new Int2ObjectHashMap<>();
            this.partitions = new IntHashSet();

        }

        private void doKafkaBegin(
            long timeMillis)
        {
            this.reconnectAt = signaler.signalAt(
                timeMillis,
                SIGNAL_CONNECT_WILL_STREAM,
                this::onSignalConnectWillStream);
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            reconnectAttempt = 0;
            willFetchers.values().forEach(f -> f.cleanup(traceId, authorization));
            willFetchers.clear();

            state = MqttKafkaState.openingInitial(state);

            kafka = newSignalStream(this::onSignalMessage, originId, routedId, initialId, 0, 0, 0,
                traceId, authorization, affinity, sessionsTopic);
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

        private void onSignalMessage(
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
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onKafkaFlush(flush);
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
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onSignal(signal);
                break;
            }
        }

        private void onSignal(SignalFW signal)
        {
            final int signalId = signal.signalId();

            switch (signalId)
            {
            case SIGNAL_EXPIRE_SESSION:
                onSessionExpirySignal(signal);
                break;
            default:
                break;
            }
        }

        private void onSessionExpirySignal(
            SignalFW signal)
        {
            String16FW clientId = expiryClientIds.get(signal.contextId());

            Flyweight expireSessionKafkaDataEx = kafkaDataExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m
                    .deferred(0)
                    .timestamp(System.currentTimeMillis())
                    .partition(p -> p.partitionId(-1).partitionOffset(-1))
                    .key(b -> b.length(clientId.length())
                        .value(clientId.value(), 0, clientId.length()))
                    .hashKey(b -> b.length(clientId.length())
                        .value(clientId.value(), 0, clientId.length())))
                .build();

            doKafkaData(supplyTraceId.get(), 0, expireSessionKafkaDataEx);
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
            }
            else
            {
                final OctetsFW extension = data.extension();
                final OctetsFW payload = data.payload();
                final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                final KafkaDataExFW kafkaDataEx =
                    dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;
                final KafkaMergedDataExFW kafkaMergedDataEx =
                    kafkaDataEx != null && kafkaDataEx.kind() == KafkaDataExFW.KIND_MERGED ? kafkaDataEx.merged() : null;
                final KafkaKeyFW key = kafkaMergedDataEx != null ? kafkaMergedDataEx.key() : null;

                reactToSignal:
                if (key != null)
                {
                    if (payload == null)
                    {
                        final OctetsFW typeHeaderNameOctets =
                            new OctetsFW().wrap(TYPE_HEADER_NAME.value(), 0, TYPE_HEADER_NAME.length());
                        final String type = kafkaMergedDataEx.headers().matchFirst(h -> h.name().equals(typeHeaderNameOctets))
                            .value().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o));

                        final String keyPostfix = type.equals(WILL_SIGNAL_NAME.asString()) ?
                            WILL_SIGNAL_KEY_POSTFIX : EXPIRY_SIGNAL_KEY_POSTFIX;

                        final String clientId0 = key.value()
                            .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o - keyPostfix.length()));
                        final String16FW clientId = new String16FW(clientId0);

                        if (type.equals(WILL_SIGNAL_NAME.asString()) && willDeliverIds.containsKey(clientId))
                        {
                            willDeliverIds.get(clientId).forEach(signaler::cancel);
                            KafkaFetchWillStream willFetcher = willFetchers.get(clientId);
                            if (willFetcher != null)
                            {
                                willFetcher.cleanup(traceId, authorization);
                            }
                        }
                        else if (type.equals(EXPIRY_SIGNAL_NAME.asString()) && sessionExpiryIds.containsKey(clientId))
                        {
                            signaler.cancel(sessionExpiryIds.get(clientId));
                        }

                        break reactToSignal;
                    }

                    final MqttSessionSignalFW sessionSignal =
                        mqttSessionSignalRO.wrap(payload.buffer(), payload.offset(), payload.limit());

                    if (sessionSignal != null)
                    {
                        switch (sessionSignal.kind())
                        {
                        case MqttSessionSignalFW.KIND_WILL:
                            final MqttWillSignalFW willSignal = sessionSignal.will();
                            long deliverAt = willSignal.deliverAt();
                            final String16FW willClientId = willSignal.clientId();

                            if (deliverAt == MqttTime.UNKNOWN.value())
                            {
                                if (!instanceId.instanceId().equals(willSignal.instanceId()))
                                {
                                    deliverAt = supplyTime.getAsLong() + willSignal.delay();
                                }
                                else
                                {
                                    break reactToSignal;
                                }
                            }

                            KafkaFetchWillStream willFetcher =
                                new KafkaFetchWillStream(originId, routedId, this, sessionsTopic, willClientId,
                                    willSignal.willId().asString(), willSignal.lifetimeId().asString(), deliverAt);
                            willFetcher.doKafkaBegin(traceId, authorization, 0, willSignal.lifetimeId());
                            willFetchers.put(new String16FW(willClientId.asString()), willFetcher);
                            break;
                        case MqttSessionSignalFW.KIND_EXPIRY:
                            final MqttExpirySignalFW expirySignal = sessionSignal.expiry();
                            long expireAt = expirySignal.expireAt();
                            final String16FW expiryClientId = expirySignal.clientId();

                            if (expireAt == MqttTime.UNKNOWN.value())
                            {
                                if (!instanceId.instanceId().equals(expirySignal.instanceId()))
                                {
                                    expireAt = supplyTime.getAsLong() + expirySignal.delay();
                                }
                                else
                                {
                                    break reactToSignal;
                                }
                            }

                            final int contextId = System.identityHashCode(expiryClientId.asString().intern());
                            expiryClientIds.put(contextId, expiryClientId);

                            final long signalId =
                                signaler.signalAt(expireAt, originId, routedId, initialId, SIGNAL_EXPIRE_SESSION, contextId);
                            sessionExpiryIds.put(expiryClientId, signalId);
                            break;
                        }
                    }
                }
            }
        }

        private void onKafkaFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            final OctetsFW extension = flush.extension();
            final ExtensionFW flushEx = extension.get(extensionRO::tryWrap);
            final KafkaFlushExFW kafkaFlushEx =
                flushEx != null && flushEx.typeId() == kafkaTypeId ? extension.get(kafkaFlushExRO::tryWrap) : null;
            final KafkaMergedFlushExFW kafkaMergedFlushEx =
                kafkaFlushEx != null && kafkaFlushEx.kind() == KafkaDataExFW.KIND_MERGED ? kafkaFlushEx.merged() : null;
            final Array32FW<KafkaOffsetFW> progress = kafkaMergedFlushEx != null ? kafkaMergedFlushEx.progress() : null;

            if (progress != null)
            {
                final IntHashSet newPartitions = new IntHashSet();
                progress.forEach(p -> newPartitions.add(p.partitionId()));
                if (!newPartitions.equals(partitions))
                {
                    instanceId.regenerate();
                    partitions = newPartitions;
                }
            }
        }

        private void onSignalConnectWillStream(
            int signalId)
        {
            assert signalId == SIGNAL_CONNECT_WILL_STREAM;

            this.reconnectAt = NO_CANCEL_ID;
            doKafkaBegin(supplyTraceId.get(), 0, 0);
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
                    SIGNAL_CONNECT_WILL_STREAM,
                    this::onSignalConnectWillStream);
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
                    SIGNAL_CONNECT_WILL_STREAM,
                    this::onSignalConnectWillStream);
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
                    SIGNAL_CONNECT_WILL_STREAM,
                    this::onSignalConnectWillStream);
            }
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
            replyMax = 8192;

            doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding, 0, capabilities);
        }


        private void doKafkaData(
            long traceId,
            long authorization,
            Flyweight extension)
        {

            doData(kafka, originId, routedId, initialId, 0, 0, 0,
                traceId, authorization, 0, DATA_FLAG_COMPLETE, 0, null, extension);
        }
    }

    private final class KafkaFetchWillStream
    {
        private final KafkaSignalStream delegate;
        private final String16FW topic;
        private final String16FW clientId;
        private final String lifetimeId;
        private final String willId;
        private final long deliverAt;
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

        private int dataSlot = NO_SLOT;
        private int messageSlotOffset;
        private int messageSlotReserved;
        private KafkaProduceWillStream willProducer;
        private KafkaProduceWillStream willRetainProducer;
        private int willMessageAckCount;

        private KafkaFetchWillStream(
            long originId,
            long routedId,
            KafkaSignalStream delegate,
            String16FW topic,
            String16FW clientId,
            String willId,
            String lifetimeId,
            long deliverAt)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.topic = topic;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.clientId = clientId;
            this.willId = willId;
            this.lifetimeId = lifetimeId;
            this.deliverAt = deliverAt;
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity,
            String16FW lifetimeId)
        {
            if (!MqttKafkaState.initialOpening(state))
            {
                state = MqttKafkaState.openingInitial(state);

                kafka = newKafkaStream(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, clientId, lifetimeId, topic);
            }
        }

        private void cleanup(
            long traceId,
            long authorization)
        {
            doKafkaEnd(traceId, authorization);
            if (willProducer != null)
            {
                willProducer.doKafkaEnd(traceId, authorization);
            }
            if (willRetainProducer != null)
            {
                willRetainProducer.doKafkaEnd(traceId, authorization);
            }
            bufferPool.release(dataSlot);
            dataSlot = NO_SLOT;
            messageSlotOffset = 0;
        }

        private void doKafkaEnd(
            long traceId,
            long authorization)
        {
            if (!MqttKafkaState.initialClosed(state))
            {
                state = MqttKafkaState.closeInitial(state);

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
                delegate.willFetchers.remove(clientId);
            }
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (MqttKafkaState.initialOpened(state) && !MqttKafkaState.initialClosed(state))
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
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onKafkaFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
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
            }
            else
            {
                final OctetsFW extension = data.extension();
                final OctetsFW payload = data.payload();
                final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                final KafkaDataExFW kafkaDataEx =
                    dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;
                final KafkaMergedDataExFW kafkaMergedDataEx =
                    kafkaDataEx != null && kafkaDataEx.kind() == KafkaDataExFW.KIND_MERGED ? kafkaDataEx.merged() : null;
                final KafkaKeyFW key = kafkaMergedDataEx != null ? kafkaMergedDataEx.key() : null;

                if (key != null && payload != null)
                {
                    MqttWillMessageFW willMessage =
                        mqttWillRO.wrap(payload.buffer(), payload.offset(), payload.limit());

                    if (willId.equals(willMessage.willId().asString()))
                    {
                        if (dataSlot == NO_SLOT)
                        {
                            dataSlot = bufferPool.acquire(initialId);
                        }

                        if (dataSlot == NO_SLOT)
                        {
                            doKafkaAbort(traceId, authorization);
                        }


                        final MutableDirectBuffer dataBuffer = bufferPool.buffer(dataSlot);
                        dataBuffer.putBytes(0, willMessage.buffer(), willMessage.offset(), willMessage.sizeof());

                        messageSlotReserved = willMessage.sizeof();

                        willProducer =
                            new KafkaProduceWillStream(originId, routedId, this, delegate.messagesTopic, deliverAt);
                        willProducer.doKafkaBegin(traceId, authorization, 0);
                        willMessageAckCount++;
                        if ((willMessage.flags() & 1 << MqttPublishFlags.RETAIN.value()) != 0)
                        {
                            willRetainProducer =
                                new KafkaProduceWillStream(originId, routedId, this, delegate.retainedTopic, deliverAt);
                            willRetainProducer.doKafkaBegin(traceId, authorization, 0);
                            willMessageAckCount++;
                        }
                    }
                    else
                    {
                        doKafkaEnd(traceId, authorization);
                    }
                }
            }
        }

        private void onKafkaFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            doKafkaEnd(traceId, authorization);
        }

        private void onKafkaWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();

            assert acknowledge <= sequence;

            initialAck = acknowledge;
            initialMax = maximum;
            state = MqttKafkaState.openInitial(state);

            assert initialAck <= initialSeq;
        }

        private void doKafkaReset(
            long traceId)
        {
            if (MqttKafkaState.initialOpened(state) && !MqttKafkaState.replyClosed(state))
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
            replyMax = bufferPool.slotCapacity();

            doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding, replyPad, capabilities);
        }

        private void onWillMessageAcked(
            long traceId,
            long authorization)
        {
            if (--willMessageAckCount == 0)
            {
                bufferPool.release(dataSlot);
                dataSlot = NO_SLOT;
                messageSlotOffset = 0;

                // Cleanup will message + will signal
                String16FW key = new String16FW.Builder().wrap(willKeyBuffer, 0, willKeyBuffer.capacity())
                    .set(clientId.asString() + WILL_KEY_POSTFIX + lifetimeId, StandardCharsets.UTF_8).build();
                Flyweight kafkaWillDataEx = kafkaDataExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m -> m
                        .deferred(0)
                        .timestamp(System.currentTimeMillis())
                        .partition(p -> p.partitionId(-1).partitionOffset(-1))
                        .key(b -> b.length(key.length())
                            .value(key.value(), 0, key.length()))
                        .hashKey(b -> b.length(clientId.length())
                            .value(clientId.value(), 0, clientId.length())))
                    .build();

                delegate.doKafkaData(traceId, authorization, kafkaWillDataEx);

                String16FW willSignalKey = new String16FW.Builder()
                    .wrap(sessionSignalKeyBuffer, 0, sessionSignalKeyBuffer.capacity())
                    .set(clientId.asString() + WILL_SIGNAL_KEY_POSTFIX, StandardCharsets.UTF_8).build();
                Flyweight willSignalKafkaDataEx = kafkaDataExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m -> m
                        .deferred(0)
                        .timestamp(System.currentTimeMillis())
                        .partition(p -> p.partitionId(-1).partitionOffset(-1))
                        .key(b -> b.length(willSignalKey.length())
                            .value(willSignalKey.value(), 0, willSignalKey.length()))
                        .hashKey(b -> b.length(clientId.length())
                            .value(clientId.value(), 0, clientId.length()))
                        .headersItem(h ->
                            h.nameLen(TYPE_HEADER_NAME.length())
                                .name(TYPE_HEADER_NAME.value(), 0, TYPE_HEADER_NAME.length())
                                .valueLen(WILL_SIGNAL_NAME.length())
                                .value(WILL_SIGNAL_NAME.value(), 0, WILL_SIGNAL_NAME.length())))
                    .build();

                delegate.doKafkaData(traceId, authorization, willSignalKafkaDataEx);

                doKafkaEnd(traceId, authorization);
            }
        }
    }

    private final class KafkaProduceWillStream
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final String16FW kafkaTopic;
        private final long deliverAt;
        private final long replyId;
        private final KafkaFetchWillStream delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private KafkaProduceWillStream(
            long originId,
            long routedId,
            KafkaFetchWillStream delegate,
            String16FW kafkaTopic,
            long deliverAt)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.kafkaTopic = kafkaTopic;
            this.deliverAt = deliverAt;
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            initialSeq = delegate.initialSeq;
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;
            state = MqttKafkaState.openingInitial(state);

            kafka = newKafkaStream(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, kafkaTopic);
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
            long authorization)
        {
            if (!MqttKafkaState.initialClosed(state))
            {
                state = MqttKafkaState.closeInitial(state);

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
            }
        }

        private void doKafkaWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
            doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding, replyPad, capabilities);
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
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onSignal(signal);
                break;
            }
        }

        private void onSignal(SignalFW signal)
        {
            final int signalId = signal.signalId();

            switch (signalId)
            {
            case SIGNAL_DELIVER_WILL_MESSAGE:
                onWillDeliverSignal(signal);
                break;
            default:
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
            assert acknowledge >= delegate.initialAck;
            assert maximum >= delegate.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = MqttKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            if (!wasOpen)
            {
                final long signalId =
                    signaler.signalAt(deliverAt, originId, routedId, initialId, SIGNAL_DELIVER_WILL_MESSAGE, 0);
                willDeliverIds.computeIfAbsent(delegate.clientId, k -> new LongArrayList()).add(signalId);
            }
            if (initialAck == delegate.messageSlotReserved)
            {
                doKafkaEnd(traceId, authorization);
                delegate.onWillMessageAcked(traceId, authorization);
            }
        }


        private void onWillDeliverSignal(SignalFW signal)
        {
            sendWill(signal.traceId(), signal.authorization(), 0);
            willDeliverIds.remove(delegate.clientId);
        }

        private void sendWill(
            long traceId,
            long authorization,
            long budgetId)
        {
            final MutableDirectBuffer dataBuffer = bufferPool.buffer(delegate.dataSlot);
            // TODO: data fragmentation
            final MqttWillMessageFW will = mqttWillRO.wrap(dataBuffer, delegate.messageSlotOffset, dataBuffer.capacity());

            Flyweight kafkaDataEx;

            kafkaHeadersRW.wrap(kafkaHeadersBuffer, 0, kafkaHeadersBuffer.capacity());


            String topicName = will.topic().asString();
            assert topicName != null;

            final DirectBuffer topicNameBuffer = will.topic().value();

            final MutableDirectBuffer keyBuffer = new UnsafeBuffer(new byte[topicNameBuffer.capacity() + 4]);
            final KafkaKeyFW key = new KafkaKeyFW.Builder()
                .wrap(keyBuffer, 0, keyBuffer.capacity())
                .length(topicNameBuffer.capacity())
                .value(topicNameBuffer, 0, topicNameBuffer.capacity())
                .build();

            String[] topicHeaders = topicName.split("/");
            for (String header : topicHeaders)
            {
                String16FW topicHeader = new String16FW(header);
                addHeader(helper.kafkaFilterHeaderName, topicHeader);
            }

            if (will.expiryInterval() != -1)
            {
                final MutableDirectBuffer expiryBuffer = new UnsafeBuffer(new byte[4]);
                expiryBuffer.putInt(0, will.expiryInterval(), ByteOrder.BIG_ENDIAN);
                kafkaHeadersRW.item(h ->
                {
                    h.nameLen(helper.kafkaTimeoutHeaderName.sizeof());
                    h.name(helper.kafkaTimeoutHeaderName);
                    h.valueLen(4);
                    h.value(expiryBuffer, 0, expiryBuffer.capacity());
                });
            }

            if (will.contentType().length() != -1)
            {
                addHeader(helper.kafkaContentTypeHeaderName, will.contentType());
            }

            if (will.payload().sizeof() != 0 && will.format() != null)
            {
                addHeader(helper.kafkaFormatHeaderName, will.format());
            }

            if (will.responseTopic().length() != -1)
            {
                final String16FW responseTopic = will.responseTopic();
                addHeader(helper.kafkaReplyToHeaderName, kafkaTopic);
                addHeader(helper.kafkaReplyKeyHeaderName, responseTopic);

                addFiltersHeader(responseTopic);
            }

            if (will.correlation().bytes() != null)
            {
                addHeader(helper.kafkaCorrelationHeaderName, will.correlation().bytes());
            }

            will.properties().forEach(property ->
                addHeader(property.key(), property.value()));

            kafkaDataEx = kafkaDataExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m
                    .deferred(0)
                    .timestamp(System.currentTimeMillis())
                    .partition(p -> p.partitionId(-1).partitionOffset(-1))
                    .key(b -> b.set(key))
                    .headers(kafkaHeadersRW.build()))
                .build();

            doKafkaData(traceId, authorization, budgetId, will.sizeof(), DATA_FLAG_COMPLETE, will.payload().bytes(), kafkaDataEx);
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

        private void addHeader(String16FW key, String16FW value)
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

    private static boolean isSetWillFlag(
        int flags)
    {
        return (flags & MqttSessionFlags.WILL.value() << 1) != 0;
    }

    private static boolean isSetCleanStart(
        int flags)
    {
        return (flags & MqttSessionFlags.CLEAN_START.value() << 1) != 0;
    }

    private abstract class KafkaSessionStream
    {
        protected MessageConsumer kafka;
        protected final long originId;
        protected final long routedId;
        protected long initialId;
        protected long replyId;
        protected final MqttSessionProxy delegate;

        protected int state;

        protected long initialSeq;
        protected long initialAck;
        protected int initialMax;

        protected long replySeq;
        protected long replyAck;
        protected int replyMax;
        protected int replyPad;

        private KafkaSessionStream(
            long originId,
            long routedId,
            MqttSessionProxy delegate)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doKafkaBeginIfNecessary(
            long traceId,
            long authorization,
            long affinity)
        {
            if (!MqttKafkaState.initialOpening(state))
            {
                doKafkaBegin(traceId, authorization, affinity);
            }
        }

        protected final void doKafkaData(
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

        protected final void cancelExpirySignal(
            long authorization,
            long traceId)
        {
            String16FW expirySignalKey = new String16FW.Builder()
                .wrap(sessionSignalKeyBuffer, 0, sessionSignalKeyBuffer.capacity())
                .set(delegate.clientId.asString() + EXPIRY_SIGNAL_KEY_POSTFIX, StandardCharsets.UTF_8).build();
            Flyweight expirySignalKafkaDataEx = kafkaDataExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m
                    .deferred(0)
                    .timestamp(System.currentTimeMillis())
                    .partition(p -> p.partitionId(-1).partitionOffset(-1))
                    .key(b -> b.length(expirySignalKey.length())
                        .value(expirySignalKey.value(), 0, expirySignalKey.length()))
                    .hashKey(b -> b.length(delegate.clientId.length())
                        .value(delegate.clientId.value(), 0, delegate.clientId.length()))
                    .headersItem(h ->
                        h.nameLen(TYPE_HEADER_NAME.length())
                            .name(TYPE_HEADER_NAME.value(), 0, TYPE_HEADER_NAME.length())
                            .valueLen(EXPIRY_SIGNAL_NAME.length())
                            .value(EXPIRY_SIGNAL_NAME.value(), 0, EXPIRY_SIGNAL_NAME.length())))
                .build();

            doKafkaData(traceId, authorization, 0, 0, DATA_FLAG_COMPLETE,
                null, expirySignalKafkaDataEx);
        }

        protected final void sendExpirySignal(
            long authorization,
            long traceId,
            Flyweight payload)
        {
            String16FW expirySignalKey = new String16FW.Builder()
                .wrap(sessionSignalKeyBuffer, 0, sessionSignalKeyBuffer.capacity())
                .set(delegate.clientId.asString() + EXPIRY_SIGNAL_KEY_POSTFIX, StandardCharsets.UTF_8).build();
            Flyweight expirySignalKafkaDataEx = kafkaDataExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m
                    .deferred(0)
                    .timestamp(System.currentTimeMillis())
                    .partition(p -> p.partitionId(-1).partitionOffset(-1))
                    .key(b -> b.length(expirySignalKey.length())
                        .value(expirySignalKey.value(), 0, expirySignalKey.length()))
                    .hashKey(b -> b.length(delegate.clientId.length())
                        .value(delegate.clientId.value(), 0, delegate.clientId.length()))
                    .headersItem(h ->
                        h.nameLen(TYPE_HEADER_NAME.length())
                            .name(TYPE_HEADER_NAME.value(), 0, TYPE_HEADER_NAME.length())
                            .valueLen(EXPIRY_SIGNAL_NAME.length())
                            .value(EXPIRY_SIGNAL_NAME.value(), 0, EXPIRY_SIGNAL_NAME.length())))
                .build();


            doKafkaData(traceId, authorization, 0, payload.sizeof(), DATA_FLAG_COMPLETE,
                payload, expirySignalKafkaDataEx);
        }

        private void sendWillSignal(
            long traceId,
            long authorization)
        {
            String16FW willSignalKey = new String16FW.Builder()
                .wrap(sessionSignalKeyBuffer, 0, sessionSignalKeyBuffer.capacity())
                .set(delegate.clientId.asString() + WILL_SIGNAL_KEY_POSTFIX, StandardCharsets.UTF_8).build();
            Flyweight willSignalKafkaDataEx = kafkaDataExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m
                    .deferred(0)
                    .timestamp(System.currentTimeMillis())
                    .partition(p -> p.partitionId(-1).partitionOffset(-1))
                    .key(b -> b.length(willSignalKey.length())
                        .value(willSignalKey.value(), 0, willSignalKey.length()))
                    .hashKey(b -> b.length(delegate.clientId.length())
                        .value(delegate.clientId.value(), 0, delegate.clientId.length()))
                    .headersItem(h ->
                        h.nameLen(TYPE_HEADER_NAME.length())
                            .name(TYPE_HEADER_NAME.value(), 0, TYPE_HEADER_NAME.length())
                            .valueLen(WILL_SIGNAL_NAME.length())
                            .value(WILL_SIGNAL_NAME.value(), 0, WILL_SIGNAL_NAME.length())))
                .build();

            final MqttSessionSignalFW willSignal =
                mqttSessionSignalRW.wrap(sessionSignalBuffer, 0, sessionSignalBuffer.capacity())
                    .will(w -> w
                        .instanceId(instanceId.instanceId())
                        .clientId(delegate.clientId)
                        .delay(delegate.delay)
                        .deliverAt(supplyTime.getAsLong() + delegate.delay)
                        .lifetimeId(delegate.lifetimeId)
                        .willId(delegate.willId))
                        .build();

            doKafkaData(traceId, authorization, 0, willSignal.sizeof(), DATA_FLAG_COMPLETE,
                willSignal, willSignalKafkaDataEx);
        }

        protected void doKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            Flyweight payload,
            Flyweight extension)
        {
            final DirectBuffer buffer = payload.buffer();
            final int offset = payload.offset();
            final int limit = payload.limit();
            final int length = limit - offset;

            doData(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, flags, reserved, buffer, offset, length, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            Flyweight extension)
        {
            doFlush(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, reserved, extension);
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

            if (isSetWillFlag(delegate.sessionFlags))
            {
                delegate.doMqttBegin(traceId, authorization, affinity, EMPTY_OCTETS);
            }
            doKafkaWindow(traceId, authorization, 0, 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                doKafkaReset(traceId);
                delegate.doMqttAbort(traceId, authorization);
            }
            else
            {
                handleKafkaData(data);
            }
        }

        protected abstract void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity);

        protected abstract void handleKafkaData(
            DataFW data);

        protected void onKafkaWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;
            assert maximum >= delegate.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = MqttKafkaState.openInitial(state);

            assert initialAck <= initialSeq;
        }

        protected void onKafkaEnd(
            EndFW end)
        {
        }

        protected void onKafkaFlush(
            FlushFW flush)
        {
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

        protected void sendMigrateSignal(long authorization, long traceId)
        {
            Flyweight kafkaMigrateDataEx = kafkaDataExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m
                    .deferred(0)
                    .timestamp(System.currentTimeMillis())
                    .partition(p -> p.partitionId(-1).partitionOffset(-1))
                    .key(b -> b.length(delegate.clientIdMigrate.length())
                        .value(delegate.clientIdMigrate.value(), 0, delegate.clientIdMigrate.length()))
                    .hashKey(b -> b.length(delegate.clientId.length())
                        .value(delegate.clientId.value(), 0, delegate.clientId.length()))
                    .headersItem(c -> c.nameLen(SENDER_ID_NAME.length())
                        .name(SENDER_ID_NAME.value(), 0, SENDER_ID_NAME.length())
                        .valueLen(delegate.sessionId.length())
                        .value(delegate.sessionId.value(), 0, delegate.sessionId.length())))
                .build();

            doKafkaData(traceId, authorization, 0, 0, DATA_FLAG_COMPLETE,
                EMPTY_OCTETS, kafkaMigrateDataEx);
        }

        protected void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;

            final OctetsFW extension = reset.extension();
            final ExtensionFW resetEx = extension.get(extensionRO::tryWrap);
            final KafkaResetExFW kafkaResetEx =
                resetEx != null && resetEx.typeId() == kafkaTypeId ? extension.get(kafkaResetExRO::tryWrap) : null;

            Flyweight mqttResetEx = EMPTY_OCTETS;

            final String16FW consumerId = kafkaResetEx != null ? kafkaResetEx.consumerId() : null;

            if (consumerId != null)
            {
                mqttResetEx = mqttResetExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mqttTypeId)
                    .serverRef(consumerId)
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
            int capabilities)
        {
            replyAck = delegate.replyAck;
            replyMax = delegate.replyMax;
            replyPad = delegate.replyPad;

            doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, replyPad, 0, capabilities);
        }
    }

    private final class KafkaSessionSignalStream extends KafkaSessionStream
    {
        private KafkaSessionSignalStream(
            long originId,
            long routedId,
            MqttSessionProxy delegate)
        {
            super(originId, routedId, delegate);
        }

        @Override
        protected void doKafkaBegin(long traceId, long authorization, long affinity)
        {
            assert state == 0;

            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);

            state = MqttKafkaState.openingInitial(state);

            kafka = newKafkaStream(super::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, delegate.sessionsTopic, null, delegate.clientIdMigrate,
                delegate.sessionId, delegate.serverRef, KafkaCapabilities.PRODUCE_AND_FETCH);
        }

        @Override
        protected void handleKafkaData(DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            final OctetsFW extension = data.extension();
            final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
            final KafkaDataExFW kafkaDataEx =
                dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;
            final KafkaMergedDataExFW kafkaMergedDataEx =
                kafkaDataEx != null && kafkaDataEx.kind() == KafkaDataExFW.KIND_MERGED ? kafkaDataEx.merged() : null;
            final KafkaKeyFW key = kafkaMergedDataEx != null ? kafkaMergedDataEx.key() : null;

            if (delegate.group != null && key != null)
            {
                delegate.group.doKafkaFlush(traceId, authorization, budgetId, reserved);
            }
        }

        @Override
        protected void onKafkaWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long authorization = window.authorization();
            final long traceId = window.traceId();
            final boolean wasOpen = MqttKafkaState.initialOpened(state);

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;
            assert maximum >= delegate.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = MqttKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            if (!wasOpen)
            {
                final long routedId = delegate.session.routedId;

                delegate.group = new KafkaGroupStream(originId, routedId, delegate);
                delegate.group.doKafkaBegin(traceId, authorization, 0);

                sendMigrateSignal(authorization, traceId);
            }
        }
    }

    private final class KafkaSessionStateProxy extends KafkaSessionStream
    {
        private KafkaSessionStateProxy(
            long originId,
            long routedId,
            MqttSessionProxy delegate)
        {
            super(originId, routedId, delegate);
        }

        @Override
        protected void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            assert state == 0;

            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);

            state = MqttKafkaState.openingInitial(state);

            KafkaCapabilities capabilities = isSetWillFlag(delegate.sessionFlags) ?
                KafkaCapabilities.PRODUCE_ONLY : KafkaCapabilities.PRODUCE_AND_FETCH;
            kafka = newKafkaStream(super::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, delegate.sessionsTopic, delegate.clientId, delegate.clientIdMigrate,
                delegate.sessionId, delegate.serverRef, capabilities);
        }

        @Override
        protected void handleKafkaData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            final int flags = data.flags();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();
            final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
            final KafkaDataExFW kafkaDataEx =
                dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;
            final KafkaMergedDataExFW kafkaMergedDataEx =
                kafkaDataEx != null && kafkaDataEx.kind() == KafkaDataExFW.KIND_MERGED ? kafkaDataEx.merged() : null;
            final KafkaKeyFW key = kafkaMergedDataEx != null ? kafkaMergedDataEx.key() : null;

            if (key != null && payload != null)
            {
                int keyLen = key.length();
                if (keyLen == delegate.clientId.length())
                {
                    MqttSessionStateFW sessionState =
                        mqttSessionStateRO.wrap(payload.buffer(), payload.offset(), payload.limit());
                    delegate.doMqttData(traceId, authorization, budgetId, reserved, flags, sessionState);
                }
                else if (keyLen == delegate.clientIdMigrate.length())
                {
                    delegate.group.doKafkaFlush(traceId, authorization, budgetId, reserved);
                }
            }
        }

        @Override
        protected void onKafkaWindow(
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
            assert acknowledge >= delegate.initialAck;
            assert maximum >= delegate.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = MqttKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            if (!wasOpen)
            {
                if (!isSetCleanStart(delegate.sessionFlags))
                {
                    cancelWillSignal(authorization, traceId);
                }
                cancelExpirySignal(authorization, traceId); // expiry cancellation

                final MqttSessionSignalFW expirySignal =
                    mqttSessionSignalRW.wrap(sessionSignalBuffer, 0, sessionSignalBuffer.capacity())
                        .expiry(w -> w
                            .instanceId(instanceId.instanceId())
                            .clientId(delegate.clientId)
                            .delay(delegate.sessionExpiryMillis)
                            .expireAt(MqttTime.UNKNOWN.value()))
                        .build();
                sendExpirySignal(authorization, traceId, expirySignal); // expire later
            }

            delegate.doMqttWindow(authorization, traceId, budgetId, padding + delegate.sessionPadding, capabilities);
        }

        @Override
        protected void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= initialAck;

            this.initialAck = acknowledge;

            assert initialAck <= initialSeq;

            delegate.doMqttReset(traceId);
        }

        private void cancelWillSignal(
            long authorization,
            long traceId)
        {
            String16FW willSignalKey = new String16FW.Builder()
                .wrap(sessionSignalKeyBuffer, 0, sessionSignalKeyBuffer.capacity())
                .set(delegate.clientId.asString() + WILL_SIGNAL_KEY_POSTFIX, StandardCharsets.UTF_8).build();
            Flyweight willSignalKafkaDataEx = kafkaDataExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m
                    .deferred(0)
                    .timestamp(System.currentTimeMillis())
                    .partition(p -> p.partitionId(-1).partitionOffset(-1))
                    .key(b -> b.length(willSignalKey.length())
                        .value(willSignalKey.value(), 0, willSignalKey.length()))
                    .hashKey(b -> b.length(delegate.clientId.length())
                        .value(delegate.clientId.value(), 0, delegate.clientId.length()))
                    .headersItem(h ->
                        h.nameLen(TYPE_HEADER_NAME.length())
                            .name(TYPE_HEADER_NAME.value(), 0, TYPE_HEADER_NAME.length())
                            .valueLen(WILL_SIGNAL_NAME.length())
                            .value(WILL_SIGNAL_NAME.value(), 0, WILL_SIGNAL_NAME.length())))
                .build();

            doKafkaData(traceId, authorization, 0, 0, DATA_FLAG_COMPLETE,
                null, willSignalKafkaDataEx);
        }

        @Override
        protected void onKafkaFlush(
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

            delegate.doMqttData(traceId, authorization, budgetId, 0, DATA_FLAG_COMPLETE, EMPTY_OCTETS);
        }

        @Override
        protected void onKafkaEnd(
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
    }

    private final class KafkaFetchWillSignalStream extends KafkaSessionStream
    {
        private KafkaFetchWillSignalStream(
            long originId,
            long routedId,
            MqttSessionProxy delegate)
        {
            super(originId, routedId, delegate);
        }

        @Override
        protected void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            if (!MqttKafkaState.initialOpening(state))
            {
                state = MqttKafkaState.openingInitial(state);

                kafka = newKafkaStream(super::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, delegate.sessionsTopic, delegate.clientId, delegate.serverRef);
            }
        }

        @Override
        protected void handleKafkaData(
            DataFW data)
        {
            final OctetsFW extension = data.extension();
            final OctetsFW payload = data.payload();
            final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
            final KafkaDataExFW kafkaDataEx =
                dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;
            final KafkaMergedDataExFW kafkaMergedDataEx =
                kafkaDataEx != null && kafkaDataEx.kind() == KafkaDataExFW.KIND_MERGED ? kafkaDataEx.merged() : null;
            final KafkaKeyFW key = kafkaMergedDataEx != null ? kafkaMergedDataEx.key() : null;

            if (key != null && payload != null)
            {
                MqttSessionSignalFW sessionSignal =
                    mqttSessionSignalRO.wrap(payload.buffer(), payload.offset(), payload.limit());
                if (sessionSignal != null)
                {
                    delegate.lifetimeId = sessionSignal.will().lifetimeId().asString();
                }
            }
        }

        @Override
        protected void onKafkaFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            delegate.session.doKafkaEnd(traceId, authorization);
            final long routedId = delegate.session.routedId;

            delegate.session = new KafkaSessionSignalStream(originId, routedId, delegate);
            delegate.session.doKafkaBeginIfNecessary(traceId, authorization, 0);
        }
    }

    private final class KafkaGroupStream
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final MqttSessionProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private KafkaGroupStream(
            long originId,
            long routedId,
            MqttSessionProxy delegate)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            initialSeq = delegate.initialSeq;
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;
            state = MqttKafkaState.openingInitial(state);

            kafka = newGroupStream(this::onGroupMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, delegate.clientId, delegate.sessionExpiryMillis);
        }

        private void doKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            initialSeq = delegate.initialSeq;

            doFlush(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, reserved, EMPTY_OCTETS);
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

        private void onGroupMessage(
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

            Flyweight mqttBeginEx = EMPTY_OCTETS;

            final int sessionExpiryMillisInRange =
                Math.max(sessionExpiryIntervalMinMillis, Math.min(sessionExpiryIntervalMaxMillis, delegate.sessionExpiryMillis));
            if (delegate.sessionExpiryMillis != sessionExpiryMillisInRange)
            {
                mqttBeginEx = mqttSessionBeginExRW.wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                    .typeId(mqttTypeId)
                    .session(sessionBuilder -> sessionBuilder
                        .flags(delegate.sessionFlags)
                        .expiry((int) TimeUnit.MILLISECONDS.toSeconds(sessionExpiryMillisInRange))
                        .clientId(delegate.clientId))
                    .build();
                delegate.sessionExpiryMillis = sessionExpiryMillisInRange;
            }

            delegate.doMqttBegin(traceId, authorization, affinity, mqttBeginEx);
            doKafkaWindow(traceId, authorization, 0, 0, 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;
            if (replySeq > replyAck + replyMax)
            {
                doKafkaReset(traceId);
                delegate.doMqttAbort(traceId, authorization);
            }
            else
            {
                final OctetsFW extension = data.extension();
                final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                final KafkaDataExFW kafkaDataEx =
                    dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;
                final KafkaGroupDataExFW kafkaGroupDataEx =
                    kafkaDataEx != null && kafkaDataEx.kind() == KafkaDataExFW.KIND_GROUP ? kafkaDataEx.group() : null;
                final String16FW leaderId = kafkaGroupDataEx != null ? kafkaGroupDataEx.leaderId() : null;
                final String16FW memberId  = kafkaGroupDataEx != null ? kafkaGroupDataEx.memberId() : null;
                final int members  = kafkaGroupDataEx != null ? kafkaGroupDataEx.members() : 0;

                if (leaderId.equals(memberId))
                {
                    if (members > 1)
                    {
                        delegate.session.sendMigrateSignal(authorization, traceId);
                        delegate.session.sendWillSignal(authorization, traceId);
                        delegate.session.doKafkaEnd(traceId, authorization);
                        doKafkaEnd(traceId, authorization);
                    }
                    else
                    {
                        delegate.session.doKafkaEnd(traceId, authorization);
                        final long routedId = delegate.session.routedId;
                        delegate.session = new KafkaSessionStateProxy(originId, routedId, delegate);
                        delegate.session.doKafkaBeginIfNecessary(traceId, authorization, 0);
                    }
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

            delegate.doMqttEnd(traceId, authorization);
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
        int index,
        int length,
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
            .flags(flags)
            .budgetId(budgetId)
            .reserved(reserved)
            .payload(buffer, index, length)
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
        String16FW sessionsTopicName,
        String16FW clientId,
        String16FW clientIdMigrate,
        String16FW sessionId,
        String serverRef,
        KafkaCapabilities capabilities)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m ->
                {
                    m.capabilities(c -> c.set(capabilities));
                    m.topic(sessionsTopicName);
                    m.groupId(MQTT_CLIENTS_GROUP_ID);
                    m.consumerId(serverRef);
                    if (clientId != null)
                    {
                        m.partitionsItem(p ->
                            p.partitionId(KafkaOffsetType.HISTORICAL.value())
                                .partitionOffset(KafkaOffsetType.HISTORICAL.value()));
                        m.filtersItem(f -> f.conditionsItem(ci ->
                            ci.key(kb -> kb.length(clientId.length())
                            .value(clientId.value(), 0, clientId.length()))));
                    }
                    m.filtersItem(f ->
                    {
                        f.conditionsItem(ci ->
                            ci.key(kb -> kb.length(clientIdMigrate.length())
                                .value(clientIdMigrate.value(), 0, clientIdMigrate.length())));
                        f.conditionsItem(i -> i.not(n -> n.condition(c -> c.header(h ->
                            h.nameLen(SENDER_ID_NAME.length())
                                .name(SENDER_ID_NAME.value(), 0, SENDER_ID_NAME.length())
                                .valueLen(sessionId.length())
                                .value(sessionId.value(), 0, sessionId.length())))));
                    });
                    m.ackMode(b -> b.set(KAFKA_DEFAULT_ACK_MODE));
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
        String16FW topic)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(KafkaCapabilities.PRODUCE_ONLY))
                    .topic(topic)
                    .partitionsItem(p -> p.partitionId(-1).partitionOffset(-2L))
                    .ackMode(b -> b.set(KAFKA_DEFAULT_ACK_MODE)))
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
        String16FW clientId,
        String serverRef)
    {
        String16FW key = new String16FW(clientId.asString() + WILL_SIGNAL_KEY_POSTFIX);
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m ->
                    m.capabilities(c -> c.set(KafkaCapabilities.FETCH_ONLY))
                        .topic(topic)
                        .groupId(MQTT_CLIENTS_GROUP_ID)
                        .consumerId(serverRef)
                        .partitionsItem(p ->
                            p.partitionId(KafkaOffsetType.HISTORICAL.value())
                                .partitionOffset(KafkaOffsetType.HISTORICAL.value()))
                        .filtersItem(f ->
                            f.conditionsItem(c ->
                                c.key(k -> k.length(key.length())
                                    .value(key.value(), 0, key.length()))))
                        .evaluation(b -> b.set(KafkaEvaluation.EAGER)))
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
        String16FW lifetimeId,
        String16FW topic)
    {
        String16FW key = new String16FW(clientId.asString() + WILL_KEY_POSTFIX + lifetimeId.asString());
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m ->
                    m.capabilities(c -> c.set(KafkaCapabilities.FETCH_ONLY))
                        .topic(topic)
                        .partitionsItem(p ->
                            p.partitionId(KafkaOffsetType.HISTORICAL.value())
                                .partitionOffset(KafkaOffsetType.HISTORICAL.value()))
                        .filtersItem(f ->
                            f.conditionsItem(c ->
                                c.key(k -> k.length(key.length())
                                    .value(key.value(), 0, key.length()))))
                        .evaluation(b -> b.set(KafkaEvaluation.EAGER)))
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


    private MessageConsumer newSignalStream(
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
        String16FW sessionsTopicName)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m ->
                    m.capabilities(c -> c.set(KafkaCapabilities.PRODUCE_AND_FETCH))
                        .topic(sessionsTopicName)
                        .groupId(MQTT_CLIENTS_GROUP_ID)
                        .filtersItem(f ->
                            f.conditionsItem(c -> c.header(h ->
                                h.nameLen(TYPE_HEADER_NAME.length())
                                    .name(TYPE_HEADER_NAME.value(), 0, TYPE_HEADER_NAME.length())
                                    .valueLen(WILL_SIGNAL_NAME.length())
                                    .value(WILL_SIGNAL_NAME.value(), 0, WILL_SIGNAL_NAME.length()))))
                        .filtersItem(f ->
                            f.conditionsItem(c -> c.header(h ->
                                h.nameLen(TYPE_HEADER_NAME.length())
                                    .name(TYPE_HEADER_NAME.value(), 0, TYPE_HEADER_NAME.length())
                                    .valueLen(EXPIRY_SIGNAL_NAME.length())
                                    .value(EXPIRY_SIGNAL_NAME.value(), 0, EXPIRY_SIGNAL_NAME.length()))))
                        .ackMode(b -> b.set(KAFKA_DEFAULT_ACK_MODE)))
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

    private MessageConsumer newGroupStream(
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
        int sessionExpiryMs)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(kafkaTypeId)
                .group(g -> g.groupId(clientId).protocol(GROUP_PROTOCOL).timeout(sessionExpiryMs))
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
