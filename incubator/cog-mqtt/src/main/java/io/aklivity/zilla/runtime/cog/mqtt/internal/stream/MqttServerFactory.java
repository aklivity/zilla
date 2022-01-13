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
package io.aklivity.zilla.runtime.cog.mqtt.internal.stream;

import static io.aklivity.zilla.runtime.cog.mqtt.internal.MqttReasonCodes.BAD_AUTHENTICATION_METHOD;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.MqttReasonCodes.CLIENT_IDENTIFIER_NOT_VALID;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.MqttReasonCodes.KEEP_ALIVE_TIMEOUT;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.MqttReasonCodes.MALFORMED_PACKET;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.MqttReasonCodes.NORMAL_DISCONNECT;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.MqttReasonCodes.NOT_AUTHORIZED;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.MqttReasonCodes.NO_SUBSCRIPTION_EXISTED;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.MqttReasonCodes.PROTOCOL_ERROR;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.MqttReasonCodes.SUCCESS;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.MqttReasonCodes.TOPIC_ALIAS_INVALID;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.MqttReasonCodes.TOPIC_FILTER_INVALID;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.MqttReasonCodes.TOPIC_NAME_INVALID;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.MqttReasonCodes.UNSUPPORTED_PROTOCOL_VERSION;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.MqttCapabilities.PUBLISH_ONLY;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.MqttCapabilities.SUBSCRIBE_ONLY;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.MqttCapabilities.valueOf;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.MqttPublishFlags.RETAIN;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.MqttSubscribeFlags.NO_LOCAL;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.MqttSubscribeFlags.RETAIN_AS_PUBLISHED;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.MqttSubscribeFlags.SEND_RETAINED;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_AUTHENTICATION_DATA;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_AUTHENTICATION_METHOD;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_CONTENT_TYPE;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_CORRELATION_DATA;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_EXPIRY_INTERVAL;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_MAXIMUM_PACKET_SIZE;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_PAYLOAD_FORMAT;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_RECEIVE_MAXIMUM;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_REQUEST_PROBLEM_INFORMATION;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_REQUEST_RESPONSE_INFORMATION;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_RESPONSE_TOPIC;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_SESSION_EXPIRY;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_SUBSCRIPTION_ID;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_TOPIC_ALIAS;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_TOPIC_ALIAS_MAXIMUM;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_USER_PROPERTY;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW.KIND_WILL_DELAY_INTERVAL;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.stream.DataFW.FIELD_OFFSET_PAYLOAD;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.stream.MqttDataExFW.Builder.DEFAULT_EXPIRY_INTERVAL;
import static io.aklivity.zilla.runtime.cog.mqtt.internal.types.stream.MqttDataExFW.Builder.DEFAULT_FORMAT;
import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_CREDITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.cog.mqtt.internal.MqttBinding;
import io.aklivity.zilla.runtime.cog.mqtt.internal.MqttConfiguration;
import io.aklivity.zilla.runtime.cog.mqtt.internal.MqttValidator;
import io.aklivity.zilla.runtime.cog.mqtt.internal.config.MqttBindingConfig;
import io.aklivity.zilla.runtime.cog.mqtt.internal.config.MqttRouteConfig;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.Array32FW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.Flyweight;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.MqttBinaryFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.MqttCapabilities;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.MqttPayloadFormatFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.String16FW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.BinaryFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttConnackFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttConnectFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttDisconnectFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPacketHeaderFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPacketType;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPingReqFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPingRespFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertiesFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPropertyFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttPublishFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttSubackFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttSubscribeFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttSubscribePayloadFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttUnsubackFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttUnsubackPayloadFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttUnsubscribeFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttUnsubscribePayloadFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.codec.MqttUserPropertyFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.stream.MqttFlushExFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class MqttServerFactory implements MqttStreamFactory
{
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);

    private static final String16FW MQTT_PROTOCOL_NAME = new String16FW("MQTT", BIG_ENDIAN);
    private static final int MQTT_PROTOCOL_VERSION = 5;

    private static final int MAXIMUM_CLIENT_ID_LENGTH = 36;

    private static final String SESSION_TOPIC_FORMAT = "$SYS/sessions/%s";
    private static final String SESSION_WILDCARD_TOPIC_FORMAT = "$SYS/sessions/%s/#";
    private static final String WILL_TOPIC_FORMAT = "$SYS/sessions/%s/will";

    private static final String SESSION_EXPIRES_AT_NAME = "expiresAt";
    private static final String WILL_DELAY_NAME = "willDelay";
    private static final String WILL_TOPIC_NAME = "willTopic";

    private static final int CONNECT_FIXED_HEADER = 0b0001_0000;
    private static final int SUBSCRIBE_FIXED_HEADER = 0b1000_0010;
    private static final int UNSUBSCRIBE_FIXED_HEADER = 0b1010_0010;
    private static final int DISCONNECT_FIXED_HEADER = 0b1110_0000;

    private static final int CONNECT_RESERVED_MASK = 0b0000_0001;
    private static final int NO_FLAGS = 0b0000_0000;
    private static final int PUBLISH_FLAGS_MASK = 0b0000_1111;
    private static final int NO_LOCAL_FLAG_MASK = 0b0000_0100;
    private static final int RETAIN_AS_PUBLISHED_MASK = 0b0000_1000;
    private static final int RETAIN_HANDLING_MASK = 0b0011_0000;
    private static final int BASIC_AUTHENTICATION_MASK = 0b1100_0000;

    private static final int WILL_FLAG_MASK = 0b0000_0100;
    private static final int WILL_QOS_MASK = 0b0001_1000;
    private static final int WILL_RETAIN_MASK = 0b0010_0000;
    private static final int USERNAME_MASK = 0b1000_0000;
    private static final int PASSWORD_MASK = 0b0100_0000;

    private static final int CONNECT_TOPIC_ALIAS_MAXIMUM_MASK = 0b0000_0001;
    private static final int CONNECT_SESSION_EXPIRY_INTERVAL_MASK = 0b0000_0010;

    private static final int CONNACK_SESSION_PRESENT = 0b0000_0001;

    private static final int RETAIN_HANDLING_SEND = 0;

    private static final int RETAIN_FLAG = 1 << RETAIN.ordinal();
    private static final int SEND_RETAINED_FLAG = 1 << SEND_RETAINED.ordinal();
    private static final int RETAIN_AS_PUBLISHED_FLAG = 1 << RETAIN_AS_PUBLISHED.ordinal();
    private static final int NO_LOCAL_FLAG = 1 << NO_LOCAL.ordinal();

    private static final int PUBLISH_TYPE = 0x03;

    private static final int DEFAULT_WILL_DELAY = 0;

    private static final int PUBLISH_EXPIRED_SIGNAL = 1;
    private static final int KEEP_ALIVE_TIMEOUT_SIGNAL = 2;
    private static final int CONNECT_TIMEOUT_SIGNAL = 3;
    private static final int SESSION_EXPIRY_SIGNAL = 4;

    private static final int PUBLISH_FRAMING = 255;

    private static final String16FW NULL_STRING = new String16FW((String) null);

    private final JsonBuilderFactory json = Json.createBuilderFactory(new HashMap<>());
    private final JsonObjectBuilder objectBuilder = json.createObjectBuilder();

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final SignalFW signalRO = new SignalFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final MqttDataExFW mqttDataExRO = new MqttDataExFW();

    private final MqttBeginExFW.Builder mqttBeginExRW = new MqttBeginExFW.Builder();
    private final MqttDataExFW.Builder mqttDataExRW = new MqttDataExFW.Builder();
    private final MqttFlushExFW.Builder mqttFlushExRW = new MqttFlushExFW.Builder();
    private final MqttBeginExFW.Builder mqttWillBeginExRW = new MqttBeginExFW.Builder();
    private final MqttDataExFW.Builder mqttWillDataExRW = new MqttDataExFW.Builder();

    private final MqttDataExFW.Builder mqttWillMessageFW = new MqttDataExFW.Builder();

    private final MqttPacketHeaderFW mqttPacketHeaderRO = new MqttPacketHeaderFW();
    private final MqttConnectFW mqttConnectRO = new MqttConnectFW();
    private final MqttPublishFW mqttPublishRO = new MqttPublishFW();
    private final MqttSubscribeFW mqttSubscribeRO = new MqttSubscribeFW();
    private final MqttSubscribePayloadFW mqttSubscribePayloadRO = new MqttSubscribePayloadFW();
    private final MqttUnsubscribeFW mqttUnsubscribeRO = new MqttUnsubscribeFW();
    private final MqttUnsubscribePayloadFW mqttUnsubscribePayloadRO = new MqttUnsubscribePayloadFW();
    private final MqttPingReqFW mqttPingReqRO = new MqttPingReqFW();
    private final MqttDisconnectFW mqttDisconnectRO = new MqttDisconnectFW();

    private final OctetsFW octetsRO = new OctetsFW();
    private final OctetsFW.Builder octetsRW = new OctetsFW.Builder();
    private final MqttPropertyFW mqttPropertyRO = new MqttPropertyFW();
    private final MqttPropertyFW.Builder mqttPropertyRW = new MqttPropertyFW.Builder();

    private final MqttPropertiesFW mqttPropertiesRO = new MqttPropertiesFW();

    private final String16FW.Builder clientIdRW = new String16FW.Builder(BIG_ENDIAN);

    private final String16FW clientIdRO = new String16FW(BIG_ENDIAN);
    private final String16FW contentTypeRO = new String16FW(BIG_ENDIAN);
    private final String16FW responseTopicRO = new String16FW(BIG_ENDIAN);
    private final String16FW willTopicRO = new String16FW(BIG_ENDIAN);
    private final String16FW usernameRO = new String16FW(BIG_ENDIAN);

    private final OctetsFW.Builder sessionPayloadRW = new OctetsFW.Builder();

    private final BinaryFW willPayloadRO = new BinaryFW();
    private final OctetsFW passwordRO = new OctetsFW();

    private final MqttPublishHeader mqttPublishHeaderRO = new MqttPublishHeader();
    private final MqttConnectPayload mqttConnectPayloadRO = new MqttConnectPayload();

    private final MqttConnackFW.Builder mqttConnackRW = new MqttConnackFW.Builder();
    private final MqttPublishFW.Builder mqttPublishRW = new MqttPublishFW.Builder();
    private final MqttSubackFW.Builder mqttSubackRW = new MqttSubackFW.Builder();
    private final MqttUnsubackFW.Builder mqttUnsubackRW = new MqttUnsubackFW.Builder();
    private final MqttUnsubackPayloadFW.Builder mqttUnsubackPayloadRW = new MqttUnsubackPayloadFW.Builder();
    private final MqttPingRespFW.Builder mqttPingRespRW = new MqttPingRespFW.Builder();
    private final MqttDisconnectFW.Builder mqttDisconnectRW = new MqttDisconnectFW.Builder();
    private final Array32FW.Builder<MqttUserPropertyFW.Builder, MqttUserPropertyFW> userPropertiesRW =
        new Array32FW.Builder<>(new MqttUserPropertyFW.Builder(), new MqttUserPropertyFW());
    private final Array32FW.Builder<MqttUserPropertyFW.Builder, MqttUserPropertyFW> willUserPropertiesRW =
        new Array32FW.Builder<>(new MqttUserPropertyFW.Builder(), new MqttUserPropertyFW());

    private final MqttServerDecoder decodePacketType = this::decodePacketType;
    private final MqttServerDecoder decodeConnect = this::decodeConnect;
    private final MqttServerDecoder decodePublish = this::decodePublish;
    private final MqttServerDecoder decodeSubscribe = this::decodeSubscribe;
    private final MqttServerDecoder decodeUnsubscribe = this::decodeUnsubscribe;
    private final MqttServerDecoder decodePingreq = this::decodePingreq;
    private final MqttServerDecoder decodeDisconnect = this::decodeDisconnect;
    private final MqttServerDecoder decodeIgnoreAll = this::decodeIgnoreAll;
    private final MqttServerDecoder decodeUnknownType = this::decodeUnknownType;

    private final Map<MqttPacketType, MqttServerDecoder> decodersByPacketType;

    {
        final Map<MqttPacketType, MqttServerDecoder> decodersByPacketType = new EnumMap<>(MqttPacketType.class);
        decodersByPacketType.put(MqttPacketType.CONNECT, decodeConnect);
        decodersByPacketType.put(MqttPacketType.PUBLISH, decodePublish);
        // decodersByPacketType.put(MqttPacketType.PUBREC, decodePubrec);
        // decodersByPacketType.put(MqttPacketType.PUBREL, decodePubrel);
        // decodersByPacketType.put(MqttPacketType.PUBCOMP, decodePubcomp);
        decodersByPacketType.put(MqttPacketType.SUBSCRIBE, decodeSubscribe);
        decodersByPacketType.put(MqttPacketType.UNSUBSCRIBE, decodeUnsubscribe);
        decodersByPacketType.put(MqttPacketType.PINGREQ, decodePingreq);
        decodersByPacketType.put(MqttPacketType.DISCONNECT, decodeDisconnect);
        // decodersByPacketType.put(MqttPacketType.AUTH, decodeAuth);
        this.decodersByPacketType = decodersByPacketType;
    }

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer dataExtBuffer;
    private final MutableDirectBuffer clientIdBuffer;
    private final MutableDirectBuffer willDataExtBuffer;
    private final MutableDirectBuffer payloadBuffer;
    private final MutableDirectBuffer propertyBuffer;
    private final MutableDirectBuffer sessionPayloadBuffer;
    private final MutableDirectBuffer userPropertiesBuffer;
    private final MutableDirectBuffer willMessageBuffer;
    private final MutableDirectBuffer willPropertyBuffer;
    private final MutableDirectBuffer willUserPropertiesBuffer;
    private final BufferPool bufferPool;
    private final BudgetCreditor creditor;
    private final Signaler signaler;
    private final MessageConsumer droppedHandler;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final LongSupplier supplyBudgetId;
    private final LongFunction<BudgetDebitor> supplyDebitor;
    private final Long2ObjectHashMap<MqttBindingConfig> bindings;
    private final int mqttTypeId;

    private final long publishTimeoutMillis;
    private final long connectTimeoutMillis;
    private final int encodeBudgetMax;

    private final int sessionExpiryIntervalLimit;
    private final byte maximumQos;
    private final byte retainedMessages;
    private final short topicAliasMaximumLimit;
    private final byte wildcardSubscriptions;
    private final byte subscriptionIdentifiers;
    private final byte sharedSubscriptions;
    private final boolean noLocal;
    private final int sessionExpiryGracePeriod;
    private final Supplier<String16FW> supplyClientId;

    private final MqttValidator validator;

    public MqttServerFactory(
        MqttConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.dataExtBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.clientIdBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.willDataExtBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.propertyBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.userPropertiesBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.payloadBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.sessionPayloadBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.willMessageBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.willPropertyBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.willUserPropertiesBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.bufferPool = context.bufferPool();
        this.creditor = context.creditor();
        this.signaler = context.signaler();
        this.droppedHandler = context.droppedFrameHandler();
        this.streamFactory = context.streamFactory();
        this.supplyDebitor = context::supplyDebitor;
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyBudgetId = context::supplyBudgetId;
        this.supplyTraceId = context::supplyTraceId;
        this.bindings = new Long2ObjectHashMap<>();
        this.mqttTypeId = context.supplyTypeId(MqttBinding.NAME);
        this.publishTimeoutMillis = SECONDS.toMillis(config.publishTimeout());
        this.connectTimeoutMillis = SECONDS.toMillis(config.connectTimeout());
        this.sessionExpiryIntervalLimit = config.sessionExpiryInterval();
        this.maximumQos = config.maximumQos();
        this.retainedMessages = config.retainAvailable() ? (byte) 1 : 0;
        this.wildcardSubscriptions = config.wildcardSubscriptionAvailable() ? (byte) 1 : 0;
        this.subscriptionIdentifiers = config.subscriptionIdentifierAvailable() ? (byte) 1 : 0;
        this.sharedSubscriptions = config.sharedSubscriptionAvailable() ? (byte) 1 : 0;
        this.topicAliasMaximumLimit = (short) Math.max(config.topicAliasMaximum(), 0);
        this.noLocal = config.noLocal();
        this.sessionExpiryGracePeriod = config.sessionExpiryGracePeriod();
        this.encodeBudgetMax = bufferPool.slotCapacity();
        this.validator = new MqttValidator();

        final Optional<String16FW> clientId = Optional.ofNullable(config.clientId()).map(String16FW::new);
        this.supplyClientId = clientId.isPresent() ? clientId::get : () -> new String16FW(UUID.randomUUID().toString());
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        MqttBindingConfig mqttBinding = new MqttBindingConfig(binding);
        bindings.put(binding.id, mqttBinding);
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
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

        MqttBindingConfig binding = bindings.get(routeId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final long initialId = begin.streamId();
            final long affinity = begin.affinity();
            final long replyId = supplyReplyId.applyAsLong(initialId);
            final long budgetId = supplyBudgetId.getAsLong();

            newStream = new MqttServer(
                    sender,
                    routeId,
                    initialId,
                    replyId,
                    affinity,
                    budgetId)::onNetwork;
        }
        return newStream;
    }

    private int topicKey(
        String topicFilter)
    {
        return System.identityHashCode(topicFilter.intern());
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
        Flyweight extension)
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
                                  .extension(extension.buffer(), extension.offset(), extension.sizeof())
                                  .build();

        MessageConsumer receiver =
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
        Flyweight extension)
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
                                  .extension(extension.buffer(), extension.offset(), extension.sizeof())
                                  .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
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
        DirectBuffer buffer,
        int index,
        int length,
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
                                .payload(buffer, index, length)
                                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                                .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doData(
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
        int flags,
        DirectBuffer buffer,
        int index,
        int length,
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
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                              .routeId(routeId)
                              .streamId(streamId)
                              .sequence(sequence)
                              .acknowledge(acknowledge)
                              .maximum(maximum)
                              .traceId(traceId)
                              .authorization(authorization)
                              .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        Flyweight extension)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                                  .routeId(routeId)
                                  .streamId(streamId)
                                  .sequence(sequence)
                                  .acknowledge(acknowledge)
                                  .maximum(maximum)
                                  .traceId(traceId)
                                  .authorization(authorization)
                                  .extension(extension.buffer(), extension.offset(), extension.sizeof())
                                  .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doWindow(
        MessageConsumer receiver,
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

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity()).routeId(routeId)
                                     .streamId(streamId)
                                     .sequence(sequence)
                                     .acknowledge(acknowledge)
                                     .maximum(maximum)
                                     .traceId(traceId)
                                     .authorization(authorization)
                                     .extension(extension.buffer(), extension.offset(), extension.sizeof())
                                     .build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doFlush(
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
        Consumer<OctetsFW.Builder> extension)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                                     .routeId(routeId)
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

    private int decodePacketType(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final MqttPacketHeaderFW packet = mqttPacketHeaderRO.tryWrap(buffer, offset, limit);

        if (packet != null)
        {
            final int length = packet.remainingLength();
            final MqttPacketType packetType = MqttPacketType.valueOf(packet.typeAndFlags() >> 4);
            final MqttServerDecoder decoder = decodersByPacketType.getOrDefault(packetType, decodeUnknownType);

            if (limit - packet.limit() >= length)
            {
                server.decodeablePacketBytes = packet.sizeof() + length;
                server.decoder = decoder;
            }
        }

        return offset;
    }

    private int decodeConnect(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        if (length > 0)
        {
            int reasonCode = SUCCESS;

            final MqttConnectFW mqttConnect = mqttConnectRO.tryWrap(buffer, offset, limit);
            decode:
            {
                if (mqttConnect == null)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }

                int flags = mqttConnect.flags();

                reasonCode = decodeConnectType(mqttConnect, flags);
                if (reasonCode != SUCCESS)
                {
                    break decode;
                }

                reasonCode = decodeConnectProtocol(mqttConnect);
                if (reasonCode != SUCCESS)
                {
                    break decode;
                }

                reasonCode = decodeConnectFlags(flags);
                if (reasonCode != SUCCESS)
                {
                    break decode;
                }

                progress = server.onDecodeConnect(traceId, authorization, progress, mqttConnect);
            }

            if (reasonCode != SUCCESS)
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodePublish(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        decode:
        if (length >= server.decodeablePacketBytes)
        {
            int reasonCode = SUCCESS;
            final MqttPublishFW publish = mqttPublishRO.tryWrap(buffer, offset, offset + server.decodeablePacketBytes);

            final MqttPublishHeader mqttPublishHeader = mqttPublishHeaderRO.reset();

            if (publish == null)
            {
                reasonCode = PROTOCOL_ERROR;
            }
            else
            {
                reasonCode = mqttPublishHeader.decode(server, publish.topicName(), publish.properties());
            }

            if (reasonCode == SUCCESS)
            {
                final int flags = publish.typeAndFlags() & PUBLISH_FLAGS_MASK;
                final String topic = mqttPublishHeader.topic;
                final int topicKey = topicKey(topic);
                MqttServer.MqttServerStream publisher = server.streams.get(topicKey);

                if (publisher == null)
                {
                    publisher = server.resolvePublisher(traceId, authorization, topic);
                    if (publisher == null)
                    {
                        server.decodePublisherKey = 0;
                        server.decodeablePacketBytes = 0;
                        server.decoder = decodePacketType;
                        progress = publish.limit();
                        break decode;
                    }
                }

                publisher.ensurePublishCapability(traceId, authorization);

                server.decodePublisherKey = topicKey;

                final OctetsFW payload = publish.payload();
                final int payloadSize = payload.sizeof();

                boolean canPublish = MqttState.initialOpened(publisher.state);

                int reserved = payloadSize + publisher.initialPad;
                canPublish &= publisher.initialSeq + reserved <= publisher.initialAck + publisher.initialMax;

                if (canPublish && publisher.debitorIndex != NO_DEBITOR_INDEX && reserved != 0)
                {
                    final int minimum = reserved; // TODO: fragmentation
                    reserved = publisher.debitor.claim(publisher.debitorIndex, publisher.initialId, minimum, reserved);
                }

                if (canPublish && (reserved != 0 || payloadSize == 0))
                {
                    server.onDecodePublish(traceId, authorization, reserved, flags, payload);
                    server.decodeablePacketBytes = 0;
                    server.decoder = decodePacketType;
                    progress = publish.limit();
                }
            }
            else
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodeSubscribe(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        if (length > 0)
        {
            int reasonCode = SUCCESS;

            final MqttSubscribeFW subscribe = mqttSubscribeRO.tryWrap(buffer, offset, limit);
            if (subscribe == null)
            {
                reasonCode = PROTOCOL_ERROR;
            }
            else if ((subscribe.typeAndFlags() & 0b1111_1111) != SUBSCRIBE_FIXED_HEADER)
            {
                reasonCode = MALFORMED_PACKET;
            }

            if (reasonCode == 0)
            {
                server.onDecodeSubscribe(traceId, authorization, subscribe);
                server.decoder = decodePacketType;
                progress = subscribe.limit();
            }
            else
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodeUnsubscribe(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        if (length > 0)
        {
            int reasonCode = SUCCESS;

            final MqttUnsubscribeFW unsubscribe = mqttUnsubscribeRO.tryWrap(buffer, offset, limit);
            if (unsubscribe == null)
            {
                reasonCode = PROTOCOL_ERROR;
            }
            else if ((unsubscribe.typeAndFlags() & 0b1111_1111) != UNSUBSCRIBE_FIXED_HEADER)
            {
                reasonCode = MALFORMED_PACKET;
            }

            if (reasonCode == 0)
            {
                server.onDecodeUnsubscribe(traceId, authorization, unsubscribe);
                server.decoder = decodePacketType;
                progress = unsubscribe.limit();
            }
            else
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodePingreq(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        if (length > 0)
        {
            final MqttPingReqFW ping = mqttPingReqRO.tryWrap(buffer, offset, limit);
            if (ping == null)
            {
                server.onDecodeError(traceId, authorization, PROTOCOL_ERROR);
                server.decoder = decodeIgnoreAll;
            }
            else
            {
                server.onDecodePingReq(traceId, authorization, ping);
                server.decoder = decodePacketType;
                progress = ping.limit();
            }
        }

        return progress;
    }

    private int decodeDisconnect(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        if (length > 0)
        {
            int reasonCode = NORMAL_DISCONNECT;

            final MqttDisconnectFW disconnect = mqttDisconnectRO.tryWrap(buffer, offset, limit);
            if (disconnect == null)
            {
                reasonCode = PROTOCOL_ERROR;
            }
            else if ((disconnect.typeAndFlags() & 0b1111_1111) != DISCONNECT_FIXED_HEADER)
            {
                reasonCode = MALFORMED_PACKET;
            }

            if (reasonCode == 0)
            {
                server.onDecodeDisconnect(traceId, authorization, disconnect);
                server.decoder = decodePacketType;
                progress = disconnect.limit();
            }
            else
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodeIgnoreAll(
        MqttServer server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        return limit;
    }

    private int decodeUnknownType(
        MqttServer server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        server.onDecodeError(traceId, authorization, PROTOCOL_ERROR);
        server.decoder = decodeIgnoreAll;
        return limit;
    }

    @FunctionalInterface
    private interface MqttServerDecoder
    {
        int decode(
            MqttServer server,
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit);
    }

    private boolean hasPublishCapability(
        int capabilities)
    {
        return (capabilities & PUBLISH_ONLY.value()) != 0;
    }

    private boolean hasSubscribeCapability(
        int capabilities)
    {
        return (capabilities & SUBSCRIBE_ONLY.value()) != 0;
    }

    private final class MqttServer
    {
        private final MessageConsumer network;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long encodeBudgetId;

        private final Int2ObjectHashMap<MqttServerStream> streams;
        private final Int2ObjectHashMap<MutableInteger> activeStreamsByTopic;
        private final Int2ObjectHashMap<Subscription> subscriptionsByPacketId;
        private final Int2ObjectHashMap<String> topicAliases;

        private MqttSessionStream sessionStream;
        private MqttWillStream willStream;

        private String16FW clientId;

        private long decodeSeq;
        private long decodeAck;
        private int decodeMax;

        private long encodeSeq;
        private long encodeAck;
        private int encodeMax;
        private int encodePad;

        private long encodeBudgetIndex = NO_CREDITOR_INDEX;
        private int encodeSharedBudget;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private long encodeSlotTraceId;

        private MqttServerDecoder decoder;
        private int decodePublisherKey;
        private int decodeablePacketBytes;

        private long connectTimeoutId = NO_CANCEL_ID;
        private long connectTimeoutAt;

        private long keepAliveTimeoutId = NO_CANCEL_ID;
        private long keepAliveTimeoutAt;

        private long keepAlive;
        private long keepAliveTimeout;
        private boolean connected;

        private short topicAliasMaximum = 0;
        private int sessionExpiryInterval = 0;
        private boolean sessionStateUnavailable = false;
        private boolean assignedClientId = false;
        private int propertyMask = 0;

        private int state;

        private MqttServer(
            MessageConsumer network,
            long routeId,
            long initialId,
            long replyId,
            long affinity,
            long budgetId)
        {
            this.network = network;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = replyId;
            this.affinity = affinity;
            this.encodeBudgetId = budgetId;
            this.decoder = decodePacketType;
            this.streams = new Int2ObjectHashMap<>();
            this.activeStreamsByTopic = new Int2ObjectHashMap<>();
            this.subscriptionsByPacketId = new Int2ObjectHashMap<>();
            this.topicAliases = new Int2ObjectHashMap<>();
        }

        private void onNetwork(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetworkBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onNetworkData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetworkEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetworkAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetworkWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetworkReset(reset);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onNetworkSignal(signal);
                break;
            default:
                break;
            }
        }

        private void onNetworkBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            state = MqttState.openingInitial(state);

            doNetworkBegin(traceId, authorization);
            doNetworkWindow(traceId, authorization, 0, 0L, 0, bufferPool.slotCapacity());
            doSignalConnectTimeoutIfNecessary();
        }

        private void onNetworkData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();

            assert acknowledge <= sequence;
            assert sequence >= decodeSeq;
            assert acknowledge <= decodeAck;

            decodeSeq = sequence + data.reserved();

            assert decodeAck <= decodeSeq;

            if (decodeSeq > decodeAck + decodeMax)
            {
                doNetworkReset(supplyTraceId.getAsLong(), authorization);
            }
            else
            {
                final long budgetId = data.budgetId();
                final OctetsFW payload = data.payload();

                DirectBuffer buffer = payload.buffer();
                int offset = payload.offset();
                int limit = payload.limit();
                int reserved = data.reserved();

                if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBuffer slotBuffer = bufferPool.buffer(decodeSlot);
                    slotBuffer.putBytes(decodeSlotOffset, buffer, offset, limit - offset);
                    decodeSlotOffset += limit - offset;
                    decodeSlotReserved += reserved;

                    buffer = slotBuffer;
                    offset = 0;
                    limit = decodeSlotOffset;
                    reserved = decodeSlotReserved;
                }

                decodeNetwork(traceId, authorization, budgetId, reserved, buffer, offset, limit);
            }
        }

        private void onNetworkEnd(
            EndFW end)
        {
            final long authorization = end.authorization();
            final long traceId = end.traceId();

            if (decodeSlot == NO_SLOT)
            {
                state = MqttState.closeInitial(state);

                if (sessionStream != null)
                {
                    doEncodeWillMessageIfNecessary(sessionStream, traceId, authorization);
                }

                cleanupStreams(traceId, authorization);

                doNetworkEndIfNecessary(traceId, authorization);

                decoder = decodeIgnoreAll;
            }
        }

        private void onNetworkAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = MqttState.closeInitial(state);

            if (sessionStream != null)
            {
                doEncodeWillMessageIfNecessary(sessionStream, traceId, authorization);
            }

            cleanupDecodeSlotIfNecessary();

            cleanupNetwork(traceId, authorization);
        }

        private void onNetworkWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            state = MqttState.openReply(state);

            assert acknowledge <= sequence;
            assert sequence <= encodeSeq;
            assert acknowledge >= encodeAck;
            assert maximum >= encodeMax;

            encodeAck = acknowledge;
            encodeMax = maximum;
            encodePad = padding;

            assert encodeAck <= encodeSeq;

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer buffer = bufferPool.buffer(encodeSlot);
                final int offset = 0;
                final int limit = encodeSlotOffset;

                encodeNetwork(encodeSlotTraceId, authorization, budgetId, buffer, offset, limit);
            }

            final int encodeWin = encodeMax - (int)(encodeSeq - encodeAck);
            final int encodeSharedCredit = Math.min(encodeBudgetMax, encodeWin - encodeSlotOffset - encodeSharedBudget);

            if (encodeSharedCredit > 0)
            {
                final long encodeSharedBudgetPrevious = creditor.credit(traceId, encodeBudgetIndex, encodeSharedCredit);
                encodeSharedBudget += encodeSharedCredit;

                assert encodeSharedBudgetPrevious + encodeSharedCredit <= encodeBudgetMax
                    : String.format("%d + %d <= %d, encodeBudget = %d",
                    encodeSharedBudgetPrevious, encodeSharedCredit, encodeBudgetMax, encodeWin);

                assert encodeSharedCredit <= encodeBudgetMax
                    : String.format("%d <= %d", encodeSharedCredit, encodeBudgetMax);
            }
        }

        private void onNetworkReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            cleanupNetwork(traceId, authorization);
        }

        private void onNetworkSignal(
            SignalFW signal)
        {
            final int signalId = signal.signalId();

            switch (signalId)
            {
            case KEEP_ALIVE_TIMEOUT_SIGNAL:
                onKeepAliveTimeoutSignal(signal);
                break;
            case CONNECT_TIMEOUT_SIGNAL:
                onConnectTimeoutSignal(signal);
                break;
            default:
                break;
            }
        }

        private void onKeepAliveTimeoutSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            final long now = System.currentTimeMillis();
            if (now >= keepAliveTimeoutAt)
            {
                onDecodeError(traceId, authorization, KEEP_ALIVE_TIMEOUT);
                decoder = decodeIgnoreAll;
            }
            else
            {
                keepAliveTimeoutId = signaler.signalAt(keepAliveTimeoutAt, routeId, replyId, KEEP_ALIVE_TIMEOUT_SIGNAL);
            }
        }

        private void onConnectTimeoutSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            final long now = System.currentTimeMillis();
            if (now >= connectTimeoutAt)
            {
                cleanupStreams(traceId, authorization);
                doNetworkEnd(traceId, authorization);
                decoder = decodeIgnoreAll;
            }
        }

        private void doCancelConnectTimeoutIfNecessary()
        {
            if (connectTimeoutId != NO_CANCEL_ID)
            {
                signaler.cancel(connectTimeoutId);
                connectTimeoutId = NO_CANCEL_ID;
            }
        }

        private byte decodeConnectProperties(
            MqttPropertiesFW properties)
        {
            byte reasonCode = SUCCESS;

            final OctetsFW propertiesValue = properties.value();
            final DirectBuffer decodeBuffer = propertiesValue.buffer();
            final int decodeOffset = propertiesValue.offset();
            final int decodeLimit = propertiesValue.limit();

            decode:
            for (int decodeProgress = decodeOffset; decodeProgress < decodeLimit; )
            {
                final MqttPropertyFW mqttProperty = mqttPropertyRO.wrap(decodeBuffer, decodeProgress, decodeLimit);
                switch (mqttProperty.kind())
                {
                case KIND_TOPIC_ALIAS_MAXIMUM:
                    if (isSetTopicAliasMaximum(propertyMask))
                    {
                        topicAliasMaximum = 0;
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.propertyMask |= CONNECT_TOPIC_ALIAS_MAXIMUM_MASK;
                    final short topicAliasMaximum = (short) (mqttProperty.topicAliasMaximum() & 0xFFFF);
                    this.topicAliasMaximum = (short) Math.min(topicAliasMaximum, topicAliasMaximumLimit);
                    break;
                case KIND_SESSION_EXPIRY:
                    if (isSetSessionExpiryInterval(propertyMask))
                    {
                        sessionExpiryInterval = 0;
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.propertyMask |= CONNECT_SESSION_EXPIRY_INTERVAL_MASK;
                    final int sessionExpiryInterval = mqttProperty.expiryInterval();
                    this.sessionExpiryInterval = Math.min(sessionExpiryInterval, sessionExpiryIntervalLimit);
                    break;
                case KIND_RECEIVE_MAXIMUM:
                case KIND_MAXIMUM_PACKET_SIZE:
                case KIND_REQUEST_RESPONSE_INFORMATION:
                case KIND_REQUEST_PROBLEM_INFORMATION:
                case KIND_USER_PROPERTY:
                    // TODO
                    break;
                case KIND_AUTHENTICATION_METHOD:
                    reasonCode = BAD_AUTHENTICATION_METHOD;
                    break decode;
                case KIND_AUTHENTICATION_DATA:
                    // TODO
                    break;
                default:
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                decodeProgress = mqttProperty.limit();
            }

            return reasonCode;
        }

        private int onDecodeConnect(
            long traceId,
            long authorization,
            int progress,
            MqttConnectFW connect)
        {
            final String16FW clientIdentifier = connect.clientId();
            this.assignedClientId = false;
            byte reasonCode;
            decode:
            {
                if (connected)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }

                final int length = clientIdentifier.length();

                if (length == 0)
                {
                    this.clientId = supplyClientId.get();
                    this.assignedClientId = true;
                }
                else if (length > MAXIMUM_CLIENT_ID_LENGTH)
                {
                    reasonCode = CLIENT_IDENTIFIER_NOT_VALID;
                    break decode;
                }
                else
                {
                    this.clientId = new String16FW(clientIdentifier.asString());
                }

                final MqttPropertiesFW properties = connect.properties();

                reasonCode = decodeConnectProperties(properties);

                if (reasonCode != SUCCESS)
                {
                    break decode;
                }

                final MqttConnectPayload payload = mqttConnectPayloadRO.reset();
                payload.decode(connect);
                reasonCode = payload.reasonCode;

                if (reasonCode != SUCCESS)
                {
                    break decode;
                }

                final int flags = connect.flags();
                final boolean willFlagSet = isSetWillFlag(flags);

                if (sessionExpiryInterval > 0 || willFlagSet && sessionExpiryInterval >= 0)
                {
                    progress = onResolveSession(traceId, authorization, reasonCode, progress, connect, payload);
                }
                else
                {
                    doCancelConnectTimeoutIfNecessary();
                    doEncodeConnack(traceId, authorization, reasonCode, assignedClientId);
                    connected = true;
                    keepAlive = connect.keepAlive();
                    keepAliveTimeout = Math.round(TimeUnit.SECONDS.toMillis(keepAlive) * 1.5);
                    doSignalKeepAliveTimeoutIfNecessary();
                    decoder = decodePacketType;
                    progress = connect.limit();
                }
            }

            if (reasonCode != SUCCESS)
            {
                doCancelConnectTimeoutIfNecessary();
                doEncodeConnack(traceId, authorization, reasonCode, assignedClientId);
                doNetworkEnd(traceId, authorization);
                decoder = decodeIgnoreAll;
                progress = connect.limit();
            }
            return progress;
        }

        private int onResolveSession(
            long traceId,
            long authorization,
            int reasonCode,
            int progress,
            MqttConnectFW connect,
            MqttConnectPayload payload)
        {
            final int flags = connect.flags();
            final String topic = String.format(SESSION_WILDCARD_TOPIC_FORMAT, clientId.asString());
            final int topicKey = topicKey(topic);
            final MqttBindingConfig binding = bindings.get(routeId);
            final MqttRouteConfig resolved = binding != null ? binding.resolve(authorization, topic, PUBLISH_ONLY) : null;

            if (resolved != null)
            {
                final long resolvedId = resolved.id;

                final boolean willFlagSet = isSetWillFlag(flags);

                objectBuilder.add(SESSION_EXPIRES_AT_NAME, System.currentTimeMillis() +
                                                               TimeUnit.SECONDS.toMillis(sessionExpiryInterval));

                if (willFlagSet && payload.willDelay > 0)
                {
                    objectBuilder.add(WILL_DELAY_NAME, payload.willDelay);
                    objectBuilder.add(WILL_TOPIC_NAME, payload.willTopic.asString());
                }

                final JsonObject sessionObj = objectBuilder.build();
                final OctetsFW sessionPayload =
                    sessionPayloadRW.wrap(sessionPayloadBuffer, 0, sessionPayloadBuffer.capacity())
                                    .set(sessionObj.toString().getBytes(UTF_8))
                                    .build();

                if (sessionStream == null)
                {
                    sessionStream = new MqttSessionStream(resolvedId, 0, willFlagSet, topic);
                    sessionStream.doApplicationBeginOrFlush(traceId, authorization, affinity, topic, NO_FLAGS,
                        0, PUBLISH_ONLY);
                }

                int willPayloadSize = 0;
                final int willFlags = decodeWillFlags(willFlagSet, flags);
                MqttDataExFW willDataEx = null;
                if (willFlagSet)
                {
                    willPayloadSize = payload.willPayload.bytes().sizeof();

                    final MqttDataExFW.Builder builder = mqttWillDataExRW.wrap(willDataExtBuffer, 0, willDataExtBuffer.capacity())
                                                                         .typeId(mqttTypeId)
                                                                         .topic(String.format(WILL_TOPIC_FORMAT,
                                                                             clientId.asString()))
                                                                         .flags(willFlags)
                                                                         .expiryInterval(payload.expiryInterval)
                                                                         .contentType(payload.contentType)
                                                                         .format(f -> f.set(payload.payloadFormat))
                                                                         .responseTopic(payload.responseTopic)
                                                                         .correlation(c -> c.bytes(payload.correlationData));

                    final Array32FW<MqttUserPropertyFW> userProperties = willUserPropertiesRW.build();
                    userProperties.forEach(c -> builder.propertiesItem(p -> p.key(c.key()).value(c.value())));

                    willDataEx = builder.build();

                    if (willStream == null)
                    {
                        this.willStream = new MqttWillStream(sessionStream.routeId, authorization, 0,
                            payload.willTopic.asString(), payload.willPayload.bytes(), willDataEx);
                    }
                }

                final int payloadSize = sessionPayload.sizeof() + willPayloadSize;

                boolean canPublish = MqttState.initialOpened(sessionStream.state);

                int reserved = payloadSize + sessionStream.initialPad;
                canPublish &= sessionStream.initialSeq + reserved <= sessionStream.initialAck + sessionStream.initialMax;

                if (canPublish && sessionStream.debitorIndex != NO_DEBITOR_INDEX && reserved != 0)
                {
                    final int minimum = reserved; // TODO: fragmentation
                    reserved = sessionStream.debitor.claim(sessionStream.debitorIndex,
                        sessionStream.initialId, minimum, reserved);
                }

                if (canPublish && (reserved != 0 || payloadSize == 0))
                {
                    connected = true;

                    onEncodeSession(sessionStream, traceId, authorization, reserved, sessionPayload);
                    onEncodeWillMessageIfNecessary(sessionStream, traceId, authorization, reserved, willFlagSet,
                        payload.willPayload, willDataEx);

                    if (reasonCode == SUCCESS)
                    {
                        keepAlive = connect.keepAlive();
                        keepAliveTimeout = Math.round(TimeUnit.SECONDS.toMillis(keepAlive) * 1.5);
                        doSignalKeepAliveTimeoutIfNecessary();
                        decoder = decodePacketType;
                        progress = connect.limit();
                    }
                }
                else
                {
                    this.propertyMask = 0;
                    this.topicAliasMaximum = 0;
                    this.sessionExpiryInterval = 0;
                    decodePublisherKey = topicKey;

                    if (sessionStateUnavailable)
                    {
                        keepAlive = connect.keepAlive();
                        keepAliveTimeout = Math.round(TimeUnit.SECONDS.toMillis(keepAlive) * 1.5);
                        doSignalKeepAliveTimeoutIfNecessary();
                        decoder = decodePacketType;
                        progress = connect.limit();
                    }
                }
            }
            else
            {
                sessionStream = new MqttSessionStream(0, topic);
            }

            return progress;
        }

        private MqttServerStream resolvePublisher(
            long traceId,
            long authorization,
            String topic)
        {
            MqttServerStream stream = null;

            final MqttBindingConfig binding = bindings.get(routeId);
            final MqttRouteConfig resolved = binding != null ? binding.resolve(authorization, topic, PUBLISH_ONLY) : null;

            if (resolved != null)
            {
                final long resolvedId = resolved.id;
                final int topicKey = topicKey(topic);

                stream = streams.computeIfAbsent(topicKey, s -> new MqttServerStream(resolvedId, topic));
                stream.doApplicationBeginOrFlush(traceId, authorization, affinity, topic, 0, PUBLISH_ONLY);
            }
            else
            {
                onDecodeError(traceId, authorization, TOPIC_NAME_INVALID);
                decoder = decodeIgnoreAll;
            }

            return stream;
        }

        private void onEncodeSession(
            MqttSessionStream stream,
            long traceId,
            long authorization,
            int reserved,
            OctetsFW sessionPayload)
        {
            final MqttDataExFW dataEx = mqttDataExRW.wrap(dataExtBuffer, 0, dataExtBuffer.capacity())
                                                    .typeId(mqttTypeId)
                                                    .topic(String.format(SESSION_TOPIC_FORMAT, clientId.asString()))
                                                    .build();

            stream.doApplicationData(traceId, authorization, reserved, sessionPayload, dataEx);
            stream.doSignalSessionExpirationIfNecessary();
        }

        private void onEncodeWillMessageIfNecessary(
            MqttSessionStream stream,
            long traceId,
            long authorization,
            int reserved,
            boolean willFlagSet,
            BinaryFW willPayload,
            MqttDataExFW willDataEx)
        {
            if (willFlagSet)
            {
                stream.doApplicationData(traceId, authorization, reserved, willPayload.bytes(), willDataEx);
            }
        }

        private void onDecodePublish(
            long traceId,
            long authorization,
            int reserved,
            int flags,
            OctetsFW payload)
        {
            final String topic = mqttPublishHeaderRO.topic;
            final int topicKey = topicKey(topic);
            MqttServerStream stream = streams.get(topicKey);

            final MqttDataExFW.Builder builder = mqttDataExRW.wrap(dataExtBuffer, 0, dataExtBuffer.capacity())
                                                             .typeId(mqttTypeId)
                                                             .topic(topic)
                                                             .flags(flags)
                                                             .expiryInterval(mqttPublishHeaderRO.expiryInterval)
                                                             .contentType(mqttPublishHeaderRO.contentType)
                                                             .format(f -> f.set(mqttPublishHeaderRO.payloadFormat))
                                                             .responseTopic(mqttPublishHeaderRO.responseTopic)
                                                             .correlation(c -> c.bytes(mqttPublishHeaderRO.correlationData));

            final Array32FW<MqttUserPropertyFW> userProperties = userPropertiesRW.build();
            userProperties.forEach(c -> builder.propertiesItem(p -> p.key(c.key()).value(c.value())));

            final MqttDataExFW dataEx = builder.build();
            if (stream != null)
            {
                stream.doApplicationData(traceId, authorization, reserved, payload, dataEx);
            }
        }

        private void onDecodeSubscribe(
            long traceId,
            long authorization,
            MqttSubscribeFW subscribe)
        {
            final int packetId = subscribe.packetId();
            final OctetsFW decodePayload = subscribe.payload();

            final DirectBuffer decodeBuffer = decodePayload.buffer();
            final int decodeOffset = decodePayload.offset();
            final int decodeLimit = decodePayload.limit();

            int subscriptionId = 0;
            boolean containsSubscriptionId = false;
            int unrouteableMask = 0;

            Subscription subscription = new Subscription();
            subscriptionsByPacketId.put(packetId, subscription);
            MqttPropertiesFW properties = subscribe.properties();
            final OctetsFW propertiesValue = properties.value();
            final DirectBuffer propertiesBuffer = decodePayload.buffer();
            final int propertiesOffset = propertiesValue.offset();
            final int propertiesLimit = propertiesValue.limit();

            MqttPropertyFW mqttProperty;
            for (int progress = propertiesOffset; progress < propertiesLimit; progress = mqttProperty.limit())
            {
                mqttProperty = mqttPropertyRO.tryWrap(propertiesBuffer, progress, propertiesLimit);
                switch (mqttProperty.kind())
                {
                case KIND_SUBSCRIPTION_ID:
                    subscriptionId = mqttProperty.subscriptionId().value();
                    containsSubscriptionId = true;
                    break;
                }
            }

            if (containsSubscriptionId && subscriptionId == 0)
            {
                onDecodeError(traceId, authorization, PROTOCOL_ERROR);
                decoder = decodeIgnoreAll;
            }
            else
            {
                if (containsSubscriptionId)
                {
                    subscription.id = subscriptionId;
                }

                for (int decodeProgress = decodeOffset; decodeProgress < decodeLimit;)
                {
                    final MqttSubscribePayloadFW mqttSubscribePayload =
                            mqttSubscribePayloadRO.tryWrap(decodeBuffer, decodeProgress, decodeLimit);
                    if (mqttSubscribePayload == null)
                    {
                        break;
                    }
                    decodeProgress = mqttSubscribePayload.limit();

                    final String filter = mqttSubscribePayload.filter().asString();
                    if (filter == null)
                    {
                        onDecodeError(traceId, authorization, PROTOCOL_ERROR);
                        decoder = decodeIgnoreAll;
                        break;
                    }

                    final boolean validTopicFilter = validator.isTopicFilterValid(filter);
                    if (!validTopicFilter)
                    {
                        onDecodeError(traceId, authorization, PROTOCOL_ERROR);
                        decoder = decodeIgnoreAll;
                        break;
                    }

                    final MqttBindingConfig binding = bindings.get(routeId);
                    final MqttRouteConfig resolved =
                            binding != null ? binding.resolve(authorization, filter, SUBSCRIBE_ONLY) : null;

                    if (resolved != null)
                    {
                        final long resolvedId = resolved.id;

                        final int topicKey = topicKey(filter);
                        final int options = mqttSubscribePayload.options();
                        final int flags = calculateSubscribeFlags(traceId, authorization, options);

                        if (!noLocal && isSetNoLocal(flags))
                        {
                            onDecodeError(traceId, authorization, PROTOCOL_ERROR);
                            decoder = decodeIgnoreAll;
                            break;
                        }

                        MqttServerStream stream = streams.computeIfAbsent(topicKey, s ->
                                                    new MqttServerStream(resolvedId, filter));
                        stream.packetId = packetId;
                        stream.subscribeFlags = flags;
                        stream.onApplicationSubscribe(topicKey, subscription);
                        stream.doApplicationBeginOrFlush(traceId, authorization, affinity,
                                filter, subscriptionId, SUBSCRIBE_ONLY);
                    }
                    else
                    {
                        unrouteableMask |= 1 << subscription.ackCount;
                    }
                }
                subscription.ackMask |= unrouteableMask;
                doSignalKeepAliveTimeoutIfNecessary();
            }
        }

        private void onDecodeUnsubscribe(
            long traceId,
            long authorization,
            MqttUnsubscribeFW unsubscribe)
        {
            final int packetId = unsubscribe.packetId();
            final OctetsFW decodePayload = unsubscribe.payload();
            final DirectBuffer decodeBuffer = decodePayload.buffer();
            final int decodeLimit = decodePayload.limit();
            final int offset = decodePayload.offset();

            final MutableDirectBuffer encodeBuffer = payloadBuffer;
            final int encodeOffset = 0;
            final int encodeLimit = payloadBuffer.capacity();

            int encodeProgress = encodeOffset;

            int decodeReasonCode = SUCCESS;
            for (int decodeProgress = offset; decodeProgress < decodeLimit; )
            {
                final MqttUnsubscribePayloadFW mqttUnsubscribePayload =
                        mqttUnsubscribePayloadRO.tryWrap(decodeBuffer, decodeProgress, decodeLimit);
                if (mqttUnsubscribePayload == null)
                {
                    decodeReasonCode = PROTOCOL_ERROR;
                    break;
                }
                final String topicFilter = mqttUnsubscribePayload.filter().asString();
                if (topicFilter == null)
                {
                    decodeReasonCode = PROTOCOL_ERROR;
                    break;
                }

                final int topicKey = topicKey(topicFilter);
                final MqttServerStream stream = streams.get(topicKey);

                int encodeReasonCode = NO_SUBSCRIPTION_EXISTED;
                if (stream != null)
                {
                    encodeReasonCode = SUCCESS;
                    stream.doApplicationFlushOrEnd(traceId, authorization, NO_FLAGS, SUBSCRIBE_ONLY);
                    stream.subscription.removeTopicFilter(topicKey);
                }

                final MqttUnsubackPayloadFW mqttUnsubackPayload =
                        mqttUnsubackPayloadRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .reasonCode(encodeReasonCode)
                        .build();
                encodeProgress = mqttUnsubackPayload.limit();

                decodeProgress = mqttUnsubscribePayload.limit();
            }

            if (decodeReasonCode != SUCCESS)
            {
                onDecodeError(traceId, authorization, decodeReasonCode);
            }
            else
            {
                doSignalKeepAliveTimeoutIfNecessary();
                final OctetsFW encodePayload = octetsRO.wrap(encodeBuffer, encodeOffset, encodeProgress);
                doEncodeUnsuback(traceId, authorization, packetId, encodePayload);
            }
        }

        private void onDecodePingReq(
            long traceId,
            long authorization,
            MqttPingReqFW ping)
        {
            doSignalKeepAliveTimeoutIfNecessary();
            doEncodePingResp(traceId, authorization);
        }

        private void onDecodeDisconnect(
            long traceId,
            long authorization,
            MqttDisconnectFW disconnect)
        {
            state = MqttState.closingInitial(state);
            streams.values().forEach(s -> s.doApplicationEndIfNecessary(traceId, authorization, EMPTY_OCTETS));
            doNetworkEnd(traceId, authorization);
            sessionStream = null;
        }

        private void onDecodeError(
            long traceId,
            long authorization,
            int reasonCode)
        {
            cleanupStreams(traceId, authorization);
            if (connected)
            {
                doEncodeDisconnect(traceId, authorization, reasonCode);
            }
            else
            {
                doEncodeConnack(traceId, authorization, reasonCode, false);
            }

            doNetworkEnd(traceId, authorization);
        }

        private void doNetworkBegin(
            long traceId,
            long authorization)
        {
            state = MqttState.openingReply(state);

            doBegin(network, routeId, replyId, encodeSeq, encodeAck, encodeMax,
                    traceId, authorization, affinity, EMPTY_OCTETS);

            assert encodeBudgetIndex == NO_CREDITOR_INDEX;
            this.encodeBudgetIndex = creditor.acquire(encodeBudgetId);
        }

        private void doNetworkData(
            long traceId,
            long authorization,
            long budgetId,
            Flyweight flyweight)
        {
            doNetworkData(traceId, authorization, budgetId, flyweight.buffer(), flyweight.offset(), flyweight.limit());
        }

        private void doNetworkData(
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer encodeBuffer = bufferPool.buffer(encodeSlot);
                encodeBuffer.putBytes(encodeSlotOffset, buffer, offset, limit - offset);
                encodeSlotOffset += limit - offset;
                encodeSlotTraceId = traceId;

                buffer = encodeBuffer;
                offset = 0;
                limit = encodeSlotOffset;
            }

            encodeNetwork(traceId, authorization, budgetId, buffer, offset, limit);
        }

        private void doNetworkEndIfNecessary(
            long traceId,
            long authorization)
        {
            if (!MqttState.replyClosed(state))
            {
                doNetworkEnd(traceId, authorization);
            }
        }

        private void doNetworkEnd(
            long traceId,
            long authorization)
        {
            state = MqttState.closeReply(state);

            cleanupBudgetCreditorIfNecessary();
            cleanupEncodeSlotIfNecessary();

            doEnd(network, routeId, replyId, encodeSeq, encodeAck, encodeMax,
                    traceId, authorization, EMPTY_OCTETS);
        }

        private void doNetworkAbortIfNecessary(
            long traceId,
            long authorization)
        {
            if (!MqttState.replyClosed(state))
            {
                doNetworkAbort(traceId, authorization);
            }
        }

        private void doNetworkAbort(
            long traceId,
            long authorization)
        {
            state = MqttState.closeReply(state);

            cleanupBudgetCreditorIfNecessary();
            cleanupEncodeSlotIfNecessary();

            doAbort(network, routeId, replyId, encodeSeq, encodeAck, encodeMax,
                    traceId, authorization, EMPTY_OCTETS);
        }

        private void doNetworkResetIfNecessary(
            long traceId,
            long authorization)
        {
            if (!MqttState.initialClosed(state))
            {
                doNetworkReset(traceId, authorization);
            }
        }

        private void doNetworkReset(
            long traceId,
            long authorization)
        {
            state = MqttState.closeInitial(state);

            cleanupDecodeSlotIfNecessary();

            doReset(network, routeId, initialId, decodeSeq, decodeAck, decodeMax,
                    traceId, authorization, EMPTY_OCTETS);
        }

        private void doNetworkWindow(
            long traceId,
            long authorization,
            int padding,
            long budgetId,
            int minInitialNoAck,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(decodeSeq - minInitialNoAck, decodeAck);

            if (newInitialAck > decodeAck || minInitialMax > decodeMax || !MqttState.initialOpened(state))
            {
                decodeAck = newInitialAck;
                assert decodeAck <= decodeSeq;

                decodeMax = minInitialMax;

                state = MqttState.openInitial(state);

                doWindow(network, routeId, initialId, decodeSeq, decodeAck, decodeMax,
                        traceId, authorization, budgetId, 0);
            }
        }

        private void doEncodePublish(
            long traceId,
            long authorization,
            int flags,
            int subscriptionFlags,
            int subscriptionId,
            Subscription subscription,
            String topic,
            OctetsFW payload,
            OctetsFW extension)
        {
            if ((flags & 0x02) != 0)
            {
                final MqttDataExFW dataEx = extension.get(mqttDataExRO::tryWrap);
                final int payloadSize = payload.sizeof();
                final int deferred = dataEx.deferred();
                final int expiryInterval = dataEx.expiryInterval();
                final String16FW contentType = dataEx.contentType();
                final String16FW responseTopic = dataEx.responseTopic();
                final MqttBinaryFW correlation = dataEx.correlation();
                final Array32FW<io.aklivity.zilla.runtime.cog.mqtt.internal.types.MqttUserPropertyFW> properties =
                        dataEx.properties();

                String topicName = dataEx.topic().asString();

                if (topicName == null)
                {
                    topicName = topic;
                }

                final boolean retainAsPublished = subscription.retainAsPublished(subscriptionFlags);
                final int publishFlags = retainAsPublished ? dataEx.flags() : dataEx.flags() & ~RETAIN_FLAG;

                final int topicNameLength = topicName != null ? topicName.length() : 0;

                AtomicInteger propertiesSize = new AtomicInteger();

                if (subscriptionId > 0)
                {
                    mqttPropertyRW.wrap(propertyBuffer, propertiesSize.get(), propertyBuffer.capacity())
                        .subscriptionId(v -> v.set(subscriptionId));
                    propertiesSize.set(mqttPropertyRW.limit());
                }

                if (expiryInterval != -1)
                {
                    mqttPropertyRW.wrap(propertyBuffer, propertiesSize.get(), propertyBuffer.capacity())
                        .expiryInterval(expiryInterval)
                        .build();
                    propertiesSize.set(mqttPropertyRW.limit());
                }

                if (contentType.value() != null)
                {
                    mqttPropertyRW.wrap(propertyBuffer, propertiesSize.get(), propertyBuffer.capacity())
                        .contentType(contentType.asString())
                        .build();
                    propertiesSize.set(mqttPropertyRW.limit());
                }

                // TODO: optional format
                mqttPropertyRW.wrap(propertyBuffer, propertiesSize.get(), propertyBuffer.capacity())
                    .payloadFormat((byte) dataEx.format().get().ordinal())
                    .build();
                propertiesSize.set(mqttPropertyRW.limit());

                if (responseTopic.value() != null)
                {
                    mqttPropertyRW.wrap(propertyBuffer, propertiesSize.get(), propertyBuffer.capacity())
                                  .responseTopic(responseTopic.asString())
                                  .build();
                    propertiesSize.set(mqttPropertyRW.limit());
                }

                if (correlation.length() != -1)
                {
                    mqttPropertyRW.wrap(propertyBuffer, propertiesSize.get(), propertyBuffer.capacity())
                                  .correlationData(a -> a.bytes(correlation.bytes()))
                                  .build();
                    propertiesSize.set(mqttPropertyRW.limit());
                }

                properties.forEach(p ->
                {
                    mqttPropertyRW.wrap(propertyBuffer, propertiesSize.get(), propertyBuffer.capacity())
                                  .userProperty(c -> c.key(p.key()).value(p.value()))
                                  .build();
                    propertiesSize.set(mqttPropertyRW.limit());
                });

                final int propertiesSize0 = propertiesSize.get();
                final MqttPublishFW publish =
                        mqttPublishRW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                                     .typeAndFlags(PUBLISH_TYPE << 4 | publishFlags)
                                     .remainingLength(3 + topicNameLength + propertiesSize.get() + payloadSize + deferred)
                                     .topicName(topicName)
                                     .properties(p -> p.length(propertiesSize0)
                                                       .value(propertyBuffer, 0, propertiesSize0))
                                     .payload(payload)
                                     .build();

                doNetworkData(traceId, authorization, 0L, publish);
            }
            else
            {
                doNetworkData(traceId, authorization, 0L, payload);
            }
        }

        private void doEncodeConnack(
            long traceId,
            long authorization,
            int reasonCode,
            boolean assignedClientId)
        {
            int propertiesSize = 0;

            MqttPropertyFW mqttProperty;
            if (sessionExpiryInterval > sessionExpiryIntervalLimit)
            {
                mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                                         .sessionExpiry(sessionExpiryIntervalLimit)
                                         .build();
                propertiesSize = mqttProperty.limit();
            }

            if (0 <= maximumQos && maximumQos < 2)
            {
                mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                                         .maximumQoS(maximumQos)
                                         .build();
                propertiesSize = mqttProperty.limit();
            }

            if (retainedMessages == 0)
            {
                mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                                         .retainAvailable(retainedMessages)
                                         .build();
                propertiesSize = mqttProperty.limit();
            }

            if (topicAliasMaximum > 0)
            {
                mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                                         .topicAliasMaximum(topicAliasMaximum)
                                         .build();
                propertiesSize = mqttProperty.limit();
            }

            if (wildcardSubscriptions == 0)
            {
                mqttProperty =  mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                                          .wildcardSubscriptionAvailable(wildcardSubscriptions)
                                          .build();
                propertiesSize = mqttProperty.limit();
            }

            if (subscriptionIdentifiers == 0)
            {
                mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                                         .subscriptionIdsAvailable(subscriptionIdentifiers)
                                         .build();
                propertiesSize = mqttProperty.limit();
            }

            if (sharedSubscriptions == 0)
            {
                mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                                         .sharedSubscriptionAvailable(sharedSubscriptions)
                                         .build();
                propertiesSize = mqttProperty.limit();
            }

            if (assignedClientId)
            {
                mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                                         .assignedClientId(this.clientId)
                                         .build();
                propertiesSize = mqttProperty.limit();
            }

            int flags = 0x00;

            final int propertiesSize0 = propertiesSize;
            final MqttConnackFW connack =
                    mqttConnackRW.wrap(writeBuffer, FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                                       .typeAndFlags(0x20)
                                       .remainingLength(3 + propertiesSize0)
                                       .flags(flags)
                                       .reasonCode(reasonCode & 0xff)
                                       .properties(p -> p.length(propertiesSize0)
                                                         .value(propertyBuffer, 0, propertiesSize0))
                                       .build();

            doNetworkData(traceId, authorization, 0L, connack);
        }

        private void doEncodeSuback(
            long traceId,
            long authorization,
            int packetId,
            int ackMask,
            int successMask)
        {
            final int ackCount = Integer.bitCount(ackMask);
            final byte[] subscriptions = new byte[ackCount];
            for (int i = 0; i < ackCount; i++)
            {
                final int ackIndex = 1 << i;
                subscriptions[i] = (successMask & ackIndex) != 0 ? SUCCESS : TOPIC_FILTER_INVALID;
            }

            OctetsFW reasonCodes = octetsRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                                           .put(subscriptions)
                                           .build();

            final MqttSubackFW suback = mqttSubackRW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                                            .typeAndFlags(0x90)
                                            .remainingLength(3 + reasonCodes.sizeof())
                                            .packetId(packetId)
                                            .properties(p -> p.length(0).value(EMPTY_OCTETS))
                                            .payload(reasonCodes)
                                            .build();

            doNetworkData(traceId, authorization, 0L, suback);
        }

        private void doEncodeUnsuback(
            long traceId,
            long authorization,
            int packetId,
            OctetsFW payload)
        {
            final MqttUnsubackFW unsuback =
                    mqttUnsubackRW.wrap(writeBuffer, FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                                        .typeAndFlags(0xb0)
                                        .remainingLength(3 + payload.sizeof())
                                        .packetId(packetId)
                                        .properties(p -> p.length(0).value(EMPTY_OCTETS))
                                        .payload(payload)
                                        .build();

            doNetworkData(traceId, authorization, 0L, unsuback);
        }

        private void doEncodePingResp(
            long traceId,
            long authorization)
        {
            final MqttPingRespFW pingResp =
                    mqttPingRespRW.wrap(writeBuffer, FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                                 .typeAndFlags(0xd0)
                                 .remainingLength(0x00)
                                 .build();

            doNetworkData(traceId, authorization, 0L, pingResp);
        }

        private void doEncodeDisconnect(
            long traceId,
            long authorization,
            int reasonCode)
        {
            final MqttDisconnectFW disconnect =
                    mqttDisconnectRW.wrap(writeBuffer, FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                                    .typeAndFlags(0xe0)
                                    .remainingLength(2)
                                    .reasonCode(reasonCode & 0xff)
                                    .properties(p -> p.length(0).value(EMPTY_OCTETS))
                                    .build();

            doNetworkData(traceId, authorization, 0L, disconnect);
        }

        private void encodeNetwork(
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int encodeWin = encodeMax - (int)(encodeSeq - encodeAck + encodeSlotOffset);
            final int length = Math.min(maxLength, Math.max(encodeWin - encodePad, 0));

            if (length > 0)
            {
                final int reserved = length + encodePad;

                doData(network, routeId, replyId, encodeSeq, encodeAck, encodeMax,
                        traceId, authorization, budgetId, reserved, buffer, offset, length, EMPTY_OCTETS);

                encodeSeq += reserved;

                assert encodeSeq <= encodeAck + encodeMax :
                    String.format("%d <= %d + %d", encodeSeq, encodeAck, encodeMax);
            }

            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = bufferPool.acquire(replyId);
                    assert encodeSlotOffset == 0;
                }

                if (encodeSlot == NO_SLOT)
                {
                    cleanupNetwork(traceId, authorization);
                }
                else
                {
                    final MutableDirectBuffer encodeBuffer = bufferPool.buffer(encodeSlot);
                    encodeBuffer.putBytes(0, buffer, offset + length, remaining);
                    encodeSlotOffset = remaining;
                }
            }
            else
            {
                cleanupEncodeSlotIfNecessary();

                if (streams.isEmpty() && decoder == decodeIgnoreAll)
                {
                    doNetworkEnd(traceId, authorization);
                }
            }
        }

        private void decodeNetworkIfNecessary(
            long traceId)
        {
            if (decodeSlot != NO_SLOT)
            {
                final long authorization = 0L; // TODO;
                final long budgetId = 0L; // TODO

                final DirectBuffer buffer = bufferPool.buffer(decodeSlot);
                final int offset = 0;
                final int limit = decodeSlotOffset;
                final int reserved = decodeSlotReserved;

                decodeNetwork(traceId, authorization, budgetId, reserved, buffer, offset, limit);
            }
        }

        private void decodeNetwork(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            MqttServerDecoder previous = null;
            int progress = offset;
            while (progress <= limit && previous != decoder)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, authorization, budgetId, buffer, progress, limit);
            }

            if (progress < limit)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = bufferPool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNetwork(traceId, authorization);
                }
                else
                {
                    final MutableDirectBuffer slotBuffer = bufferPool.buffer(decodeSlot);
                    slotBuffer.putBytes(0, buffer, progress, limit - progress);
                    decodeSlotOffset = limit - progress;
                    decodeSlotReserved = (int)((long) reserved * (limit - progress) / (limit - offset));
                }
            }
            else
            {
                cleanupDecodeSlotIfNecessary();

                if (MqttState.initialClosing(state))
                {
                    state = MqttState.closeInitial(state);
                    cleanupStreams(traceId, authorization);
                    doNetworkEndIfNecessary(traceId, authorization);
                }
            }

            if (!MqttState.initialClosed(state))
            {
                doNetworkWindow(traceId, authorization, 0, budgetId, decodeSlotReserved, decodeMax);
            }
        }

        private void cleanupNetwork(
            long traceId,
            long authorization)
        {
            cleanupStreams(traceId, authorization);

            doNetworkResetIfNecessary(traceId, authorization);
            doNetworkAbortIfNecessary(traceId, authorization);
        }

        private void cleanupStreams(
            long traceId,
            long authorization)
        {
            streams.values().forEach(s -> s.cleanup(traceId, authorization));
        }

        private void cleanupBudgetCreditorIfNecessary()
        {
            if (encodeBudgetIndex != NO_CREDITOR_INDEX)
            {
                creditor.release(encodeBudgetIndex);
                encodeBudgetIndex = NO_CREDITOR_INDEX;
            }
        }

        private void cleanupDecodeSlotIfNecessary()
        {
            if (decodeSlot != NO_SLOT)
            {
                bufferPool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
                decodeSlotReserved = 0;
            }
        }

        private void cleanupEncodeSlotIfNecessary()
        {
            if (encodeSlot != NO_SLOT)
            {
                bufferPool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeSlotTraceId = 0;
            }
        }

        private void doSignalKeepAliveTimeoutIfNecessary()
        {
            if (keepAlive > 0)
            {
                keepAliveTimeoutAt = System.currentTimeMillis() + keepAliveTimeout;

                if (keepAliveTimeoutId == NO_CANCEL_ID)
                {
                    keepAliveTimeoutId = signaler.signalAt(keepAliveTimeoutAt, routeId, replyId, KEEP_ALIVE_TIMEOUT_SIGNAL);
                }
            }
        }

        private void doSignalConnectTimeoutIfNecessary()
        {
            connectTimeoutAt = System.currentTimeMillis() + connectTimeoutMillis;

            if (connectTimeoutId == NO_CANCEL_ID)
            {
                connectTimeoutId = signaler.signalAt(connectTimeoutAt, routeId, replyId, CONNECT_TIMEOUT_SIGNAL);
            }
        }

        private int calculateSubscribeFlags(
            long traceId,
            long authorization,
            int options)
        {
            int flags = 0;

            if ((options & NO_LOCAL_FLAG_MASK) != 0)
            {
                flags |= NO_LOCAL_FLAG;
            }

            if ((options & RETAIN_AS_PUBLISHED_MASK) != 0)
            {
                flags |= RETAIN_AS_PUBLISHED_FLAG;
            }

            final int retainHandling = options & RETAIN_HANDLING_MASK;

            switch (retainHandling)
            {
            case RETAIN_HANDLING_SEND:
                flags |= SEND_RETAINED_FLAG;
                break;
            case RETAIN_HANDLING_MASK:
                onDecodeError(traceId, authorization, PROTOCOL_ERROR);
                break;
            }

            return flags;
        }

        private final class Subscription
        {
            private final IntArrayList topicKeys = new IntArrayList();

            private int id = 0;
            private int ackCount;
            private int successMask;
            private int ackMask;

            private Subscription()
            {
                this.successMask = 0;
            }

            private void onSubscribeFailed(
                long traceId,
                long authorization,
                int packetId,
                int ackIndex)
            {
                final int bit = 1 << ackIndex;
                ackMask |= bit;
                onSubscribeCompleted(traceId, authorization, packetId);
            }

            private void onSubscribeSucceeded(
                long traceId,
                long authorization,
                int packetId,
                int ackIndex)
            {
                final int bit = 1 << ackIndex;
                successMask |= bit;
                ackMask |= bit;
                onSubscribeCompleted(traceId, authorization, packetId);
            }

            private void onSubscribeCompleted(
                long traceId,
                long authorization,
                int packetId)
            {
                if (acknowledged())
                {
                    doEncodeSuback(traceId, authorization, packetId, ackMask, successMask);
                }
            }

            private void addTopicKey(
                int topicKey)
            {
                topicKeys.add(topicKey);
            }

            private void removeTopicFilter(
                int topicKey)
            {
                topicKeys.removeInt(topicKey);
            }

            private boolean acknowledged()
            {
                return Integer.bitCount(ackMask) == ackCount;
            }

            private boolean hasSubscribeCompleted(
                int ackIndex)
            {
                return (ackMask & 1 << ackIndex) != 0;
            }

            private boolean retainAsPublished(
                int flags)
            {
                return (flags & RETAIN_AS_PUBLISHED_FLAG) == RETAIN_AS_PUBLISHED_FLAG;
            }
        }

        private class MqttServerStream
        {
            private MessageConsumer application;
            private final int topicKey;

            private long routeId;
            private long initialId;
            private long replyId;
            private long budgetId;

            private BudgetDebitor debitor;
            private long debitorIndex = NO_DEBITOR_INDEX;

            private long initialSeq;
            private long initialAck;
            private int initialMax;
            private int initialPad;

            private long replySeq;
            private long replyAck;
            private int replyMax;

            private String topicFilter;

            private Subscription subscription;
            private int subackIndex;
            private int packetId;
            private boolean acknowledged;

            private int state;
            private int capabilities;

            private long publishExpiresId = NO_CANCEL_ID;
            private long publishExpiresAt;

            private int subscribeFlags;

            MqttServerStream(
                long routeId,
                String topicFilter)
            {
                this.routeId = routeId;
                this.initialId = supplyInitialId.applyAsLong(routeId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.topicFilter = topicFilter;
                this.topicKey = topicKey(topicFilter);
            }

            private void onApplicationSubscribe(
                int topicKey,
                Subscription subscription)
            {
                this.subscription = subscription;
                this.subackIndex = subscription != null ? subscription.ackCount++ : -1;
                if (subscription != null)
                {
                    subscription.addTopicKey(topicKey);
                }
            }

            private void doApplicationBeginOrFlush(
                long traceId,
                long authorization,
                long affinity,
                String topicFilter,
                int subscriptionId,
                MqttCapabilities capability)
            {
                final int newCapabilities = capabilities | capability.value();
                if (!MqttState.initialOpening(state))
                {
                    this.capabilities = newCapabilities;
                    doApplicationBegin(traceId, authorization, affinity, topicFilter, subscribeFlags, subscriptionId);
                }
                else if (newCapabilities != capabilities)
                {
                    this.capabilities = newCapabilities;

                    if (subscription != null &&
                            !subscription.hasSubscribeCompleted(subackIndex) && hasSubscribeCapability(capabilities))
                    {
                        subscription.onSubscribeSucceeded(traceId, authorization, packetId, subackIndex);
                    }

                    doApplicationFlush(traceId, authorization, 0, subscribeFlags);
                }
            }

            private void doApplicationBegin(
                long traceId,
                long authorization,
                long affinity,
                String topicFilter,
                int flags,
                int subscriptionId)
            {
                assert state == 0;
                state = MqttState.openingInitial(state);

                final MqttBeginExFW beginEx = mqttBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                                                           .typeId(mqttTypeId)
                                                           .capabilities(r -> r.set(valueOf(capabilities)))
                                                           .clientId(clientId)
                                                           .topic(topicFilter)
                                                           .flags(flags)
                                                           .subscriptionId(subscriptionId)
                                                           .build();

                application = newStream(this::onApplication, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, affinity, beginEx);

                final int topicKey = topicKey(topicFilter);
                final MutableInteger activeStreams = activeStreamsByTopic.computeIfAbsent(topicKey, key -> new MutableInteger());
                activeStreams.value++;

                if (hasPublishCapability(capabilities))
                {
                    doSignalPublishExpirationIfNecessary();
                }
            }

            private void doApplicationData(
                long traceId,
                long authorization,
                int reserved,
                OctetsFW payload,
                Flyweight extension)
            {
                assert MqttState.initialOpening(state);

                assert hasPublishCapability(this.capabilities);

                final DirectBuffer buffer = payload.buffer();
                final int offset = payload.offset();
                final int limit = payload.limit();
                final int length = limit - offset;
                assert reserved >= length + initialPad;

                doData(application, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, budgetId, reserved, buffer, offset, length, extension);

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;

                doSignalPublishExpirationIfNecessary();
            }

            private void doApplicationFlushOrEnd(
                long traceId,
                long authorization,
                int flags,
                MqttCapabilities capability)
            {
                final int newCapabilities = capabilities & ~capability.value();
                if (newCapabilities == 0)
                {
                    this.capabilities = newCapabilities;
                    if (!MqttState.initialOpened(state))
                    {
                        state = MqttState.closingInitial(state);
                    }
                    else
                    {
                        doApplicationEnd(traceId, authorization, EMPTY_OCTETS);
                    }
                }
                else if (newCapabilities != capabilities)
                {
                    this.capabilities = newCapabilities;
                    doApplicationFlush(traceId, authorization, 0, flags);
                }
            }

            private void doApplicationAbort(
                long traceId,
                long authorization,
                Flyweight extension)
            {
                setInitialClosed();

                doAbort(application, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, extension);
            }

            private void doApplicationAbortIfNecessary(
                long traceId,
                long authorization)
            {
                if (!MqttState.initialClosed(state))
                {
                    doApplicationAbort(traceId, authorization, EMPTY_OCTETS);
                }
            }

            private void doApplicationFlush(
                long traceId,
                long authorization,
                int reserved,
                int flags)
            {
                doFlush(application, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, 0L, reserved,
                    ex -> ex.set((b, o, l) -> mqttFlushExRW.wrap(b, o, l)
                                                           .typeId(mqttTypeId)
                                                           .flags(flags)
                                                           .capabilities(c -> c.set(valueOf(capabilities)))
                                                           .build()
                                                           .sizeof()));

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;
            }

            private void setInitialClosed()
            {
                assert !MqttState.initialClosed(state);

                state = MqttState.closeInitial(state);

                if (debitorIndex != NO_DEBITOR_INDEX)
                {
                    debitor.release(debitorIndex, initialId);
                    debitorIndex = NO_DEBITOR_INDEX;
                }

                if (MqttState.closed(state))
                {
                    capabilities = 0;
                    subscribeFlags = 0;
                    streams.remove(topicKey);

                    final MutableInteger activeStreams = activeStreamsByTopic.get(topicKey);

                    assert activeStreams != null;

                    activeStreams.value--;

                    assert activeStreams.value >= 0;

                    if (activeStreams.value == 0)
                    {
                        activeStreamsByTopic.remove(topicKey);
                    }
                }
            }

            private void onApplication(
                int msgTypeId,
                DirectBuffer buffer,
                int index,
                int length)
            {
                switch (msgTypeId)
                {
                case BeginFW.TYPE_ID:
                    final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                    onApplicationBegin(begin);
                    break;
                case DataFW.TYPE_ID:
                    final DataFW data = dataRO.wrap(buffer, index, index + length);
                    onApplicationData(data);
                    break;
                case EndFW.TYPE_ID:
                    final EndFW end = endRO.wrap(buffer, index, index + length);
                    onApplicationEnd(end);
                    break;
                case AbortFW.TYPE_ID:
                    final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                    onApplicationAbort(abort);
                    break;
                case WindowFW.TYPE_ID:
                    final WindowFW window = windowRO.wrap(buffer, index, index + length);
                    onApplicationWindow(window);
                    break;
                case ResetFW.TYPE_ID:
                    final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                    onApplicationReset(reset);
                    break;
                case SignalFW.TYPE_ID:
                    final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                    onApplicationSignal(signal);
                    break;
                }
            }

            private void onApplicationWindow(
                WindowFW window)
            {
                final long sequence = window.sequence();
                final long acknowledge = window.acknowledge();
                final int maximum = window.maximum();
                final long traceId = window.traceId();
                final long authorization = window.authorization();
                final long budgetId = window.budgetId();
                final int padding = window.padding();

                if (subscription != null &&
                    !subscription.hasSubscribeCompleted(subackIndex) && hasSubscribeCapability(capabilities))
                {
                    subscription.onSubscribeSucceeded(traceId, authorization, packetId, subackIndex);

                    if (subscription.acknowledged())
                    {
                        doApplicationWindowIfNecessary(traceId, authorization);

                        for (int topicKey : subscription.topicKeys)
                        {
                            if (topicKey != this.topicKey)
                            {
                                final MqttServerStream stream = streams.get(topicKey);
                                if (!stream.acknowledged)
                                {
                                    stream.doApplicationWindowIfNecessary(traceId, authorization);
                                    stream.acknowledged = true;
                                }
                            }
                        }
                    }
                }

                this.state = MqttState.openInitial(state);
                this.budgetId = budgetId;

                assert acknowledge <= sequence;
                assert sequence <= initialSeq;
                assert acknowledge >= initialAck;
                assert maximum >= initialMax;

                initialAck = acknowledge;
                initialMax = maximum;
                initialPad = padding;

                assert initialAck <= initialSeq;

                if (budgetId != 0L && debitorIndex == NO_DEBITOR_INDEX)
                {
                    debitor = supplyDebitor.apply(budgetId);
                    debitorIndex = debitor.acquire(budgetId, initialId, MqttServer.this::decodeNetworkIfNecessary);
                }

                if (MqttState.initialClosing(state) &&
                    !MqttState.initialClosed(state))
                {
                    doApplicationEnd(traceId, authorization, EMPTY_OCTETS);
                }
                else if (decodePublisherKey == topicKey)
                {
                    decodeNetworkIfNecessary(traceId);
                }
            }

            private void onApplicationReset(
                ResetFW reset)
            {
                setInitialClosed();

                final long traceId = reset.traceId();
                final long authorization = reset.authorization();

                if (!MqttState.initialOpened(state))
                {
                    if (hasSubscribeCapability(capabilities))
                    {
                        subscription.onSubscribeFailed(traceId, authorization, packetId, subackIndex);
                    }

                    if (hasPublishCapability(capabilities))
                    {
                        onDecodeError(traceId, authorization, TOPIC_NAME_INVALID);
                        decoder = decodeIgnoreAll;
                    }
                }

                decodeNetworkIfNecessary(traceId);
                cleanup(traceId, authorization);
            }

            private void onApplicationSignal(
                SignalFW signal)
            {
                final int signalId = signal.signalId();

                switch (signalId)
                {
                case PUBLISH_EXPIRED_SIGNAL:
                    onPublishExpiredSignal(signal);
                    break;
                default:
                    break;
                }
            }

            private void onPublishExpiredSignal(
                SignalFW signal)
            {
                final long traceId = signal.traceId();
                final long authorization = signal.authorization();

                final long now = System.currentTimeMillis();
                if (now >= publishExpiresAt)
                {
                    doApplicationFlushOrEnd(traceId, authorization, subscribeFlags, PUBLISH_ONLY);
                }
                else
                {
                    publishExpiresId = NO_CANCEL_ID;
                    doSignalPublishExpirationIfNecessary();
                }
            }

            private void onApplicationBegin(
                BeginFW begin)
            {
                state = MqttState.openingReply(state);

                final long traceId = begin.traceId();
                final long authorization = begin.authorization();

                if (hasPublishCapability(capabilities))
                {
                    doApplicationWindowIfNecessary(traceId, authorization);
                }
                else
                {
                    if (subscription.acknowledged())
                    {
                        if (!acknowledged)
                        {
                            doApplicationWindowIfNecessary(traceId, authorization);
                            acknowledged = true;
                        }
                    }
                    else
                    {
                        doApplicationWindow(traceId, authorization, 0, replyMax);
                    }
                }
            }

            private void onApplicationData(
                DataFW data)
            {
                final long sequence = data.sequence();
                final long acknowledge = data.acknowledge();
                final long traceId = data.traceId();
                final int reserved = data.reserved();
                final long authorization = data.authorization();
                final int flags = data.flags();
                final OctetsFW payload = data.payload();
                final OctetsFW extension = data.extension();

                assert acknowledge <= sequence;
                assert sequence >= replySeq;
                assert acknowledge <= replyAck;

                replySeq = sequence + reserved;
                encodeSharedBudget -= reserved;

                assert replyAck <= replySeq;

                if (replySeq > replyAck + replyMax)
                {
                    doApplicationReset(traceId, authorization);
                    doNetworkAbort(traceId, authorization);
                }
                else
                {
                    if (payload != null && subscription != null)
                    {
                        doEncodePublish(traceId, authorization, flags, subscribeFlags, subscription.id, subscription,
                            topicFilter, payload, extension);
                    }
                    else
                    {
                        droppedHandler.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
                    }
                    doApplicationWindowIfNecessary(traceId, authorization);
                }
            }

            private void onApplicationEnd(
                EndFW end)
            {
                setReplyClosed();
            }

            private void onApplicationAbort(
                AbortFW abort)
            {
                setReplyClosed();

                final long traceId = abort.traceId();
                final long authorization = abort.authorization();

                cleanup(traceId, authorization);
            }

            private void doApplicationEndIfNecessary(
                long traceId,
                long authorization,
                Flyweight extension)
            {
                if (MqttState.initialOpening(state) && !MqttState.initialClosed(state))
                {
                    doApplicationEnd(traceId, authorization, extension);
                }
            }

            private void doApplicationEnd(
                long traceId,
                long authorization,
                Flyweight extension)
            {
                setInitialClosed();
                capabilities = 0;
                streams.remove(topicKey);

                doEnd(application, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, extension);
            }

            private void doApplicationWindowIfNecessary(
                long traceId,
                long authorization)
            {
                if (MqttState.replyOpening(state))
                {
                    doApplicationWindow(traceId, authorization, encodeSlotOffset, encodeBudgetMax);
                }
            }

            private void doApplicationWindow(
                long traceId,
                long authorization,
                int minReplyNoAck,
                int minReplyMax)
            {
                final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

                if (newReplyAck > replyAck || minReplyMax > replyMax || !MqttState.replyOpened(state))
                {
                    replyAck = newReplyAck;
                    assert replyAck <= replySeq;

                    replyMax = minReplyMax;

                    state = MqttState.openReply(state);

                    doWindow(application, routeId, replyId, replySeq, replyAck, replyMax,
                            traceId, authorization, encodeBudgetId, PUBLISH_FRAMING);
                }
            }

            private void doApplicationReset(
                long traceId,
                long authorization)
            {
                setReplyClosed();

                doReset(application, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, EMPTY_OCTETS);
            }

            private void doApplicationResetIfNecessary(
                long traceId,
                long authorization)
            {
                if (!MqttState.replyClosed(state))
                {
                    doApplicationReset(traceId, authorization);
                }
            }

            private void setReplyClosed()
            {
                assert !MqttState.replyClosed(state);

                state = MqttState.closeReply(state);

                if (MqttState.closed(state))
                {
                    capabilities = 0;
                    streams.remove(topicKey);
                    sessionStream = null;
                    final MutableInteger count = activeStreamsByTopic.get(topicKey);

                    assert count != null;

                    count.value--;

                    assert count.value >= 0;

                    if (count.value == 0)
                    {
                        activeStreamsByTopic.remove(topicKey);
                    }
                }
            }

            private void cleanup(
                long traceId,
                long authorization)
            {
                doApplicationAbortIfNecessary(traceId, authorization);
                doApplicationResetIfNecessary(traceId, authorization);
                doCancelPublishExpirationIfNecessary();
            }

            private void doSignalPublishExpirationIfNecessary()
            {
                publishExpiresAt = System.currentTimeMillis() + publishTimeoutMillis;

                if (publishExpiresId == NO_CANCEL_ID)
                {
                    publishExpiresId = signaler.signalAt(publishExpiresAt, routeId, initialId, PUBLISH_EXPIRED_SIGNAL);
                }
            }

            private void doCancelPublishExpirationIfNecessary()
            {
                if (publishExpiresId != NO_CANCEL_ID)
                {
                    signaler.cancel(publishExpiresId);
                    publishExpiresId = NO_CANCEL_ID;
                }
            }

            private void ensurePublishCapability(
                long traceId,
                long authorization)
            {
                if (!hasPublishCapability(capabilities))
                {
                    this.capabilities |= PUBLISH_ONLY.value();
                    doApplicationFlush(traceId, authorization, 0, subscribeFlags);
                }
            }
        }

        private void doEncodeWillMessageIfNecessary(
            MqttSessionStream sessionStream,
            long traceId,
            long authorization)
        {
            if (sessionStream != null && sessionStream.willFlagSet && sessionExpiryInterval == 0)
            {
                willStream.doApplicationBegin(traceId, authorization, affinity, NO_FLAGS, 0);
                willStream.publishWillMessage(traceId);
            }
        }

        private class MqttSessionStream
        {
            private MessageConsumer application;
            private final int topicKey;
            private final boolean willFlagSet;

            private long routeId;
            private long initialId;
            private long replyId;
            private long budgetId;

            private BudgetDebitor debitor;
            private long debitorIndex = NO_DEBITOR_INDEX;

            private long initialSeq;
            private long initialAck;
            private int initialMax;
            private int initialPad;

            private long replySeq;
            private long replyAck;
            private int replyMax;

            private String topicFilter;

            private int packetId;

            private int state;
            private int publishOnlyCapabilities;

            private long sessionExpiresId = NO_CANCEL_ID;
            private long sessionExpiresAt;

            MqttSessionStream(
                int packetId,
                String topicFilter)
            {
                this.packetId = packetId;
                this.willFlagSet = false;
                this.topicFilter = topicFilter;
                this.topicKey = topicKey(topicFilter);
            }

            MqttSessionStream(
                long routeId,
                int packetId,
                boolean willFlagSet,
                String topicFilter)
            {
                this.routeId = routeId;
                this.initialId = supplyInitialId.applyAsLong(routeId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.packetId = packetId;
                this.willFlagSet = willFlagSet;
                this.topicFilter = topicFilter;
                this.topicKey = topicKey(topicFilter);
            }

            private void onApplication(
                int msgTypeId,
                DirectBuffer buffer,
                int index,
                int length)
            {
                switch (msgTypeId)
                {
                case BeginFW.TYPE_ID:
                    final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                    onApplicationBegin(begin);
                    break;
                case DataFW.TYPE_ID:
                    final DataFW data = dataRO.wrap(buffer, index, index + length);
                    onApplicationData(data);
                    break;
                case EndFW.TYPE_ID:
                    final EndFW end = endRO.wrap(buffer, index, index + length);
                    onApplicationEnd(end);
                    break;
                case AbortFW.TYPE_ID:
                    final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                    onApplicationAbort(abort);
                    break;
                case WindowFW.TYPE_ID:
                    final WindowFW window = windowRO.wrap(buffer, index, index + length);
                    onApplicationWindow(window);
                    break;
                case ResetFW.TYPE_ID:
                    final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                    onApplicationReset(reset);
                    break;
                case SignalFW.TYPE_ID:
                    final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                    onApplicationSignal(signal);
                    break;
                }
            }

            private void onApplicationWindow(
                WindowFW window)
            {
                final long sequence = window.sequence();
                final long acknowledge = window.acknowledge();
                final int maximum = window.maximum();
                final long traceId = window.traceId();
                final long authorization = window.authorization();
                final long budgetId = window.budgetId();
                final int padding = window.padding();

                if (!MqttState.initialOpened(state))
                {
                    doCancelConnectTimeoutIfNecessary();
                    doEncodeConnack(traceId, authorization, SUCCESS, assignedClientId);
                }

                this.state = MqttState.openInitial(state);
                this.budgetId = budgetId;

                assert acknowledge <= sequence;
                assert sequence <= initialSeq;
                assert acknowledge >= initialAck;
                assert maximum >= initialMax;

                initialAck = acknowledge;
                initialMax = maximum;
                initialPad = padding;

                assert initialAck <= initialSeq;

                if (budgetId != 0L && debitorIndex == NO_DEBITOR_INDEX)
                {
                    debitor = supplyDebitor.apply(budgetId);
                    debitorIndex = debitor.acquire(budgetId, initialId, MqttServer.this::decodeNetworkIfNecessary);
                }

                if (MqttState.initialClosing(state) && !MqttState.initialClosed(state))
                {
                    doApplicationEnd(traceId, authorization, EMPTY_OCTETS);
                }
                else if (decodePublisherKey == topicKey)
                {
                    decodeNetworkIfNecessary(traceId);
                }
            }

            private void onApplicationReset(
                ResetFW reset)
            {
                final long traceId = reset.traceId();
                final long authorization = reset.authorization();

                if (!MqttState.initialOpened(state))
                {
                    doCancelConnectTimeoutIfNecessary();
                    doEncodeConnack(traceId, authorization, SUCCESS, assignedClientId);
                    sessionStateUnavailable = true;
                }

                setInitialClosed();

                decodeNetworkIfNecessary(traceId);
                cleanup(traceId, authorization);
            }

            private void onApplicationSignal(
                SignalFW signal)
            {
                final int signalId = signal.signalId();

                switch (signalId)
                {
                case SESSION_EXPIRY_SIGNAL:
                    onSessionExpiredSignal(signal);
                    break;
                default:
                    break;
                }
            }

            private void onSessionExpiredSignal(
                SignalFW signal)
            {
                final long traceId = signal.traceId();
                final long authorization = signal.authorization();
                final long now = System.currentTimeMillis();

                if (MqttState.initialClosing(state) && now >= sessionExpiresAt)
                {
                    doApplicationFlushOrEnd(traceId, authorization, NO_FLAGS, PUBLISH_ONLY);
                }
                else
                {
                    sessionExpiresId = NO_CANCEL_ID;
                    doSignalSessionExpirationIfNecessary();
                }
            }

            private void onApplicationBegin(
                BeginFW begin)
            {
                state = MqttState.openReply(state);

                final long traceId = begin.traceId();
                final long authorization = begin.authorization();

                doApplicationWindowIfNecessary(traceId, authorization, encodeSlotOffset, encodeBudgetMax);
            }

            private void onApplicationData(
                DataFW data)
            {
                final long sequence = data.sequence();
                final long acknowledge = data.acknowledge();
                final long traceId = data.traceId();
                final int reserved = data.reserved();
                final long authorization = data.authorization();
                final int flags = data.flags();
                final OctetsFW payload = data.payload();
                final OctetsFW extension = data.extension();

                assert acknowledge <= sequence;
                assert sequence >= replySeq;
                assert acknowledge <= replyAck;

                replySeq = sequence + reserved;
                encodeSharedBudget -= reserved;

                assert replyAck <= replySeq;

                if (replySeq > replyAck + replyMax)
                {
                    doApplicationReset(traceId, authorization);
                    doNetworkAbort(traceId, authorization);
                }
                else
                {
                    if (payload != null)
                    {
                        onSessionStateUpdated(payload, extension);
                    }
                    doApplicationWindowIfNecessary(traceId, authorization, encodeSlotOffset, encodeBudgetMax);
                }
            }

            private void onApplicationEnd(
                EndFW end)
            {
                setReplyClosed();
            }

            private void onApplicationAbort(
                AbortFW abort)
            {
                setReplyClosed();

                final long traceId = abort.traceId();
                final long authorization = abort.authorization();

                cleanup(traceId, authorization);
            }

            private void doApplicationBeginOrFlush(
                long traceId,
                long authorization,
                long affinity,
                String topicFilter,
                int flags,
                int subscriptionId,
                MqttCapabilities capability)
            {
                final int newCapabilities = publishOnlyCapabilities | capability.value();
                if (!MqttState.initialOpening(state))
                {
                    this.publishOnlyCapabilities = newCapabilities;
                    doApplicationBegin(traceId, authorization, affinity, topicFilter, flags, subscriptionId);
                }
                else if (newCapabilities != publishOnlyCapabilities)
                {
                    this.publishOnlyCapabilities = newCapabilities;
                    doApplicationFlush(traceId, authorization, 0, flags);
                }
            }

            private void doApplicationBegin(
                long traceId,
                long authorization,
                long affinity,
                String topicFilter,
                int flags,
                int subscriptionId)
            {
                assert state == 0;
                state = MqttState.openingInitial(state);

                final MqttBeginExFW beginEx = mqttBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                                                           .typeId(mqttTypeId)
                                                           .capabilities(r -> r.set(valueOf(publishOnlyCapabilities)))
                                                           .clientId(clientId)
                                                           .topic(topicFilter)
                                                           .flags(flags)
                                                           .subscriptionId(subscriptionId)
                                                           .build();

                application = newStream(this::onApplication, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, affinity, beginEx);

                final int topicKey = topicKey(topicFilter);
                final MutableInteger activeStreams = activeStreamsByTopic.computeIfAbsent(topicKey, key -> new MutableInteger());
                activeStreams.value++;
            }

            private void doApplicationData(
                long traceId,
                long authorization,
                int reserved,
                OctetsFW payload,
                Flyweight extension)
            {
                assert MqttState.initialOpening(state);

                assert hasPublishCapability(this.publishOnlyCapabilities);

                final DirectBuffer buffer = payload.buffer();
                final int offset = payload.offset();
                final int limit = payload.limit();
                final int length = limit - offset;
                assert reserved >= length + initialPad;

                if (MqttState.closed(state))
                {
                    onSessionStateUpdated(payload, extension);
                }
                else
                {
                    doData(application, routeId, initialId, initialSeq, initialAck, initialMax,
                            traceId, authorization, budgetId, reserved, buffer, offset, length, extension);

                    initialSeq += reserved;
                    assert initialSeq <= initialAck + initialMax;
                }
            }

            private void onSessionStateUpdated(
                OctetsFW payload,
                Flyweight extension)
            {
            }

            private void doSignalSessionExpirationIfNecessary()
            {
                sessionExpiresAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(sessionExpiryInterval);

                if (sessionExpiresId == NO_CANCEL_ID)
                {
                    sessionExpiresId = signaler.signalAt(sessionExpiresAt, routeId, initialId, SESSION_EXPIRY_SIGNAL);
                }
            }

            private void cleanup(
                long traceId,
                long authorization)
            {
                doApplicationAbortIfNecessary(traceId, authorization);
                doApplicationResetIfNecessary(traceId, authorization);
            }

            private void doApplicationFlushOrEnd(
                long traceId,
                long authorization,
                int flags,
                MqttCapabilities capability)
            {
                final int newCapabilities = publishOnlyCapabilities & ~capability.value();
                if (newCapabilities == 0)
                {
                    this.publishOnlyCapabilities = newCapabilities;
                    if (!MqttState.initialOpened(state))
                    {
                        state = MqttState.closingInitial(state);
                    }
                    else
                    {
                        doApplicationEnd(traceId, authorization, EMPTY_OCTETS);
                    }
                }
                else if (newCapabilities != publishOnlyCapabilities)
                {
                    this.publishOnlyCapabilities = newCapabilities;
                    doApplicationFlush(traceId, authorization, 0, flags);
                }
            }

            private void doApplicationAbort(
                long traceId,
                long authorization,
                Flyweight extension)
            {
                setInitialClosed();

                doAbort(application, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, extension);
            }

            private void doApplicationAbortIfNecessary(
                long traceId,
                long authorization)
            {
                if (!MqttState.initialClosed(state))
                {
                    doApplicationAbort(traceId, authorization, EMPTY_OCTETS);
                }
            }

            private void doApplicationEnd(
                long traceId,
                long authorization,
                Flyweight extension)
            {
                setInitialClosed();
                publishOnlyCapabilities = 0;
                streams.remove(topicKey);

                doEnd(application, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, extension);
            }

            private void doApplicationWindowIfNecessary(
                long traceId,
                long authorization,
                int minReplyNoAck,
                int minReplyMax)
            {
                if (MqttState.replyOpened(state))
                {
                    doApplicationWindow(traceId, authorization, minReplyNoAck, minReplyMax);
                }
            }

            private void doApplicationWindow(
                long traceId,
                long authorization,
                int minReplyNoAck,
                int minReplyMax)
            {
                final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

                if (newReplyAck > replyAck || minReplyMax > replyMax || !MqttState.replyOpened(state))
                {
                    replyAck = newReplyAck;
                    assert replyAck <= replySeq;

                    replyMax = minReplyMax;

                    doWindow(application, routeId, replyId, replySeq, replyAck, replyMax,
                            traceId, authorization, encodeBudgetId, PUBLISH_FRAMING);
                }
            }

            private void doApplicationReset(
                long traceId,
                long authorization)
            {
                setReplyClosed();

                doReset(application, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, EMPTY_OCTETS);
            }

            private void doApplicationResetIfNecessary(
                long traceId,
                long authorization)
            {
                if (!MqttState.replyClosed(state))
                {
                    doApplicationReset(traceId, authorization);
                }
            }

            private void setReplyClosed()
            {
                assert !MqttState.replyClosed(state);

                state = MqttState.closeReply(state);

                if (MqttState.closed(state))
                {
                    publishOnlyCapabilities = 0;
                    streams.remove(topicKey);
                    final MutableInteger count = activeStreamsByTopic.get(topicKey);

                    assert count != null;

                    count.value--;

                    assert count.value >= 0;

                    if (count.value == 0)
                    {
                        activeStreamsByTopic.remove(topicKey);
                    }
                }
            }

            private void doApplicationFlush(
                long traceId,
                long authorization,
                int reserved,
                int flags)
            {
                doFlush(application, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, 0L, reserved,
                    ex -> ex.set((b, o, l) -> mqttFlushExRW.wrap(b, o, l)
                                                           .typeId(mqttTypeId)
                                                           .flags(flags)
                                                           .capabilities(c -> c.set(valueOf(publishOnlyCapabilities)))
                                                           .build()
                                                           .sizeof()));

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;
            }

            private void setInitialClosed()
            {
                assert !MqttState.initialClosed(state);

                state = MqttState.closeInitial(state);

                if (debitorIndex != NO_DEBITOR_INDEX)
                {
                    debitor.release(debitorIndex, initialId);
                    debitorIndex = NO_DEBITOR_INDEX;
                }

                if (MqttState.closed(state))
                {
                    publishOnlyCapabilities = 0;
                    streams.remove(topicKey);

                    final MutableInteger activeStreams = activeStreamsByTopic.get(topicKey);

                    assert activeStreams != null;

                    activeStreams.value--;

                    assert activeStreams.value >= 0;

                    if (activeStreams.value == 0)
                    {
                        activeStreamsByTopic.remove(topicKey);
                    }
                }
            }
        }

        private class MqttWillStream
        {
            private final int publishOnlyCapabilities = PUBLISH_ONLY.value();

            private MessageConsumer application;
            private final int topicKey;

            private long routeId;
            private long initialId;
            private long replyId;
            private long budgetId;

            private long authorization;

            private BudgetDebitor debitor;
            private long debitorIndex = NO_DEBITOR_INDEX;

            private long initialSeq;
            private long initialAck;
            private int initialMax;
            private int initialPad;

            private long replySeq;
            private long replyAck;
            private int replyMax;

            private String topicFilter;

            private MqttDataExFW willMessage;
            private OctetsFW willPayload;

            private int packetId;

            private int state;

            MqttWillStream(
                long routeId,
                long authorization,
                int packetId,
                String topicFilter,
                OctetsFW willPayload,
                MqttDataExFW willMessage)
            {
                this.routeId = routeId;
                this.authorization = authorization;
                this.initialId = supplyInitialId.applyAsLong(routeId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.packetId = packetId;
                this.topicFilter = topicFilter;
                this.topicKey = topicKey(topicFilter);
                this.willPayload = willPayload;
                this.willMessage = encodeWillMessage(topicFilter, willMessage);
            }

            private void onApplication(
                int msgTypeId,
                DirectBuffer buffer,
                int index,
                int length)
            {
                switch (msgTypeId)
                {
                case BeginFW.TYPE_ID:
                    final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                    onApplicationBegin(begin);
                    break;
                case EndFW.TYPE_ID:
                    final EndFW end = endRO.wrap(buffer, index, index + length);
                    onApplicationEnd(end);
                    break;
                case AbortFW.TYPE_ID:
                    final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                    onApplicationAbort(abort);
                    break;
                case WindowFW.TYPE_ID:
                    final WindowFW window = windowRO.wrap(buffer, index, index + length);
                    onApplicationWindow(window);
                    break;
                case ResetFW.TYPE_ID:
                    final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                    onApplicationReset(reset);
                    break;
                }
            }

            private void onApplicationWindow(
                WindowFW window)
            {
                final long sequence = window.sequence();
                final long acknowledge = window.acknowledge();
                final int maximum = window.maximum();
                final long traceId = window.traceId();
                final long authorization = window.authorization();
                final long budgetId = window.budgetId();
                final int padding = window.padding();

                assert acknowledge <= sequence;
                assert sequence <= initialSeq;
                assert acknowledge >= initialAck;
                assert maximum >= initialMax;

                this.state = MqttState.openInitial(state);
                this.budgetId = budgetId;
                this.initialAck = acknowledge;
                this.initialMax = maximum;
                this.initialPad = padding;

                assert initialAck <= initialSeq;

                if (budgetId != 0L && debitorIndex == NO_DEBITOR_INDEX)
                {
                    debitor = supplyDebitor.apply(budgetId);
                    debitorIndex = debitor.acquire(budgetId, initialId, MqttWillStream.this::publishWillMessage);
                }

                publishWillMessage(traceId);

                if (MqttState.initialClosing(state) && !MqttState.initialClosed(state))
                {
                    doApplicationEnd(traceId, authorization, EMPTY_OCTETS);
                }
                else if (decodePublisherKey == topicKey)
                {
                    decodeNetworkIfNecessary(traceId);
                }
            }

            private void onApplicationReset(
                ResetFW reset)
            {
                setInitialClosed();

                final long traceId = reset.traceId();
                final long authorization = reset.authorization();

                decodeNetworkIfNecessary(traceId);
                cleanup(traceId, authorization);
            }

            private void onApplicationBegin(
                BeginFW begin)
            {
                state = MqttState.openReply(state);

                final long traceId = begin.traceId();
                final long authorization = begin.authorization();

                doApplicationWindowIfNecessary(traceId, authorization);
            }

            private void onApplicationEnd(
                EndFW end)
            {
                setReplyClosed();
            }

            private void onApplicationAbort(
                AbortFW abort)
            {
                setReplyClosed();

                final long traceId = abort.traceId();
                final long authorization = abort.authorization();

                cleanup(traceId, authorization);
            }

            private MqttDataExFW encodeWillMessage(
                String topic,
                MqttDataExFW dataEx)
            {
                final int publishFlags = dataEx.flags() & ~RETAIN_FLAG;
                final int expiryInterval = dataEx.expiryInterval();
                final MqttPayloadFormatFW format = dataEx.format();
                final String16FW contentType = dataEx.contentType();
                final String16FW responseTopic = dataEx.responseTopic();
                final MqttBinaryFW correlation = dataEx.correlation();
                final Array32FW<io.aklivity.zilla.runtime.cog.mqtt.internal.types.MqttUserPropertyFW> properties =
                    dataEx.properties();

                return mqttWillMessageFW.wrap(willMessageBuffer, 0, willMessageBuffer.capacity())
                                        .typeId(mqttTypeId)
                                        .topic(topic)
                                        .flags(publishFlags)
                                        .expiryInterval(expiryInterval)
                                        .contentType(contentType)
                                        .format(format)
                                        .responseTopic(responseTopic)
                                        .correlation(correlation)
                                        .properties(properties)
                                        .build();
            }

            private void doApplicationBegin(
                long traceId,
                long authorization,
                long affinity,
                int flags,
                int subscriptionId)
            {
                assert state == 0;
                state = MqttState.openingInitial(state);

                final MqttBeginExFW beginEx = mqttWillBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                                                           .typeId(mqttTypeId)
                                                           .capabilities(r -> r.set(valueOf(publishOnlyCapabilities)))
                                                           .clientId(clientId)
                                                           .topic(topicFilter)
                                                           .flags(flags)
                                                           .subscriptionId(subscriptionId)
                                                           .build();

                application = newStream(this::onApplication, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, affinity, beginEx);

                final int topicKey = topicKey(topicFilter);
                final MutableInteger activeStreams = activeStreamsByTopic.computeIfAbsent(topicKey, key -> new MutableInteger());
                activeStreams.value++;
            }

            private void doApplicationData(
                long traceId,
                long authorization,
                int reserved,
                int flags,
                OctetsFW payload,
                Flyweight extension)
            {
                assert MqttState.initialOpening(state);

                assert hasPublishCapability(this.publishOnlyCapabilities);

                final DirectBuffer buffer = payload.buffer();
                final int offset = payload.offset();
                final int limit = payload.limit();
                final int length = limit - offset;
                assert reserved >= length + initialPad;

                doData(application, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, budgetId, reserved, flags, buffer, offset, length, extension);

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;
            }

            private void cleanup(
                long traceId,
                long authorization)
            {
                doApplicationAbortIfNecessary(traceId, authorization);
                doApplicationResetIfNecessary(traceId, authorization);
            }

            private void doApplicationAbort(
                long traceId,
                long authorization,
                Flyweight extension)
            {
                setInitialClosed();

                doAbort(application, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, extension);
            }

            private void doApplicationAbortIfNecessary(
                long traceId,
                long authorization)
            {
                if (!MqttState.initialClosed(state))
                {
                    doApplicationAbort(traceId, authorization, EMPTY_OCTETS);
                }
            }

            private void doApplicationEnd(
                long traceId,
                long authorization,
                Flyweight extension)
            {
                setInitialClosed();
                streams.remove(topicKey);
                willStream = null;

                doEnd(application, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, extension);
            }

            private void doApplicationWindowIfNecessary(
                long traceId,
                long authorization)
            {
                if (MqttState.replyOpened(state))
                {
                    doWindow(application, routeId, replyId, replySeq, replyAck, replyMax,
                            traceId, authorization, encodeBudgetId, PUBLISH_FRAMING);
                }
            }

            private void doApplicationReset(
                long traceId,
                long authorization)
            {
                setReplyClosed();

                doReset(application, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, EMPTY_OCTETS);
            }

            private void doApplicationResetIfNecessary(
                long traceId,
                long authorization)
            {
                if (!MqttState.replyClosed(state))
                {
                    doApplicationReset(traceId, authorization);
                }
            }

            private void setReplyClosed()
            {
                assert !MqttState.replyClosed(state);

                state = MqttState.closeReply(state);

                if (MqttState.closed(state))
                {
                    streams.remove(topicKey);
                    final MutableInteger count = activeStreamsByTopic.get(topicKey);

                    assert count != null;

                    count.value--;

                    assert count.value >= 0;

                    if (count.value == 0)
                    {
                        activeStreamsByTopic.remove(topicKey);
                    }
                }
            }

            private void setInitialClosed()
            {
                assert !MqttState.initialClosed(state);

                state = MqttState.closeInitial(state);

                if (debitorIndex != NO_DEBITOR_INDEX)
                {
                    debitor.release(debitorIndex, initialId);
                    debitorIndex = NO_DEBITOR_INDEX;
                }

                if (MqttState.closed(state))
                {
                    streams.remove(topicKey);

                    final MutableInteger activeStreams = activeStreamsByTopic.get(topicKey);

                    assert activeStreams != null;

                    activeStreams.value--;

                    assert activeStreams.value >= 0;

                    if (activeStreams.value == 0)
                    {
                        activeStreamsByTopic.remove(topicKey);
                    }
                }
            }

            private void publishWillMessage(
                long traceId)
            {
                final OctetsFW payload = willPayload;
                final int payloadSize = payload.sizeof();

                int reserved = payloadSize + initialPad;
                boolean canPublish = initialSeq + reserved <= initialAck + initialMax;
                int dataFlags = 0x03;

                if (canPublish && debitorIndex != NO_DEBITOR_INDEX && reserved != 0)
                {
                    final int minimum = reserved; // TODO: fragmentation
                    reserved = debitor.claim(debitorIndex, initialId, minimum, reserved);

                    if (reserved != minimum)
                    {
                        dataFlags &= ~0x01;
                    }
                }

                if (canPublish && (reserved != 0 || payloadSize == 0))
                {
                    doApplicationData(traceId, authorization, reserved, dataFlags, payload, willMessage);

                    if (dataFlags == 0x03)
                    {
                        this.state = MqttState.closingInitial(state);
                    }
                }
            }
        }
    }

    private static boolean invalidWillQos(
        int flags)
    {
        return (flags & WILL_QOS_MASK) == WILL_QOS_MASK;
    }

    private static boolean isSetWillQos(
        int flags)
    {
        return (flags & WILL_QOS_MASK) != 0b0000_0000;
    }

    private static int decodeWillFlags(
        boolean willFlagSet,
        int flags)
    {
        int willFlags = 0;

        if (willFlagSet)
        {
            if (isSetWillQos(flags))
            {
                willFlags = (willFlags | WILL_QOS_MASK) >>> 2;
            }

            if (isSetWillRetain(flags))
            {
                willFlags |= RETAIN_FLAG;
            }
        }

        return willFlags;
    }

    private static boolean isSetWillRetain(
        int flags)
    {
        return (flags & WILL_RETAIN_MASK) != 0;
    }

    private static boolean isSetWillFlag(
        int flags)
    {
        return (flags & WILL_FLAG_MASK) != 0;
    }

    private static boolean isSetUsername(
        int flags)
    {
        return (flags & USERNAME_MASK) != 0;
    }

    private static boolean isSetPassword(
        int flags)
    {
        return (flags & PASSWORD_MASK) != 0;
    }

    private static boolean isSetNoLocal(
        int flags)
    {
        return (flags & NO_LOCAL_FLAG) != 0;
    }

    private static boolean isSetBasicAuthentication(
        int flags)
    {
        return (flags & BASIC_AUTHENTICATION_MASK) != 0;
    }

    private static boolean isSetTopicAliasMaximum(
        int flags)
    {
        return (flags & CONNECT_TOPIC_ALIAS_MAXIMUM_MASK) != 0;
    }

    private static boolean isSetSessionExpiryInterval(
        int flags)
    {
        return (flags & CONNECT_SESSION_EXPIRY_INTERVAL_MASK) != 0;
    }

    private static int decodeConnectType(
        MqttConnectFW connect,
        int flags)
    {
        int reasonCode = SUCCESS;

        if ((connect.typeAndFlags() & 0b1111_1111) != CONNECT_FIXED_HEADER || (flags & CONNECT_RESERVED_MASK) != 0)
        {
            reasonCode = MALFORMED_PACKET;
        }

        return reasonCode;
    }

    private static int decodeConnectProtocol(
        MqttConnectFW connect)
    {
        int reasonCode = SUCCESS;

        if (!MQTT_PROTOCOL_NAME.equals(connect.protocolName()) || connect.protocolVersion() != MQTT_PROTOCOL_VERSION)
        {
            reasonCode = UNSUPPORTED_PROTOCOL_VERSION;
        }

        return reasonCode;
    }

    private static int decodeConnectFlags(
        int flags)
    {
        int reasonCode = SUCCESS;

        if (!isSetWillFlag(flags) && (isSetWillQos(flags) || isSetWillRetain(flags)) || invalidWillQos(flags))
        {
            reasonCode = MALFORMED_PACKET;
        }
        else if (isSetBasicAuthentication(flags))
        {
            reasonCode = NOT_AUTHORIZED;
        }

        return reasonCode;
    }

    private static DirectBuffer copyBuffer(
        DirectBuffer buffer,
        int index,
        int length)
    {
        UnsafeBuffer copy = new UnsafeBuffer(new byte[length]);
        copy.putBytes(0, buffer, index, length);
        return copy;
    }

    private final class MqttConnectPayload
    {
        private byte reasonCode = SUCCESS;
        private MqttPropertiesFW willProperties = null;
        private byte willQos = 0;
        private byte willRetain = 0;
        private String16FW willTopic = null;
        private BinaryFW willPayload = null;
        private String16FW username = null;
        private OctetsFW password = null;

        private int willDelay = DEFAULT_WILL_DELAY;
        private MqttPayloadFormat payloadFormat = DEFAULT_FORMAT;
        private int expiryInterval = DEFAULT_EXPIRY_INTERVAL;
        private String16FW contentType = NULL_STRING;
        private String16FW responseTopic = NULL_STRING;
        private OctetsFW correlationData = null;

        private MqttConnectPayload reset()
        {
            this.reasonCode = SUCCESS;
            this.willProperties = null;
            this.willQos = 0;
            this.willRetain = 0;
            this.willTopic = null;
            this.willPayload = null;
            this.username = null;
            this.password = null;
            this.willDelay = DEFAULT_WILL_DELAY;
            this.payloadFormat = DEFAULT_FORMAT;
            this.expiryInterval = DEFAULT_EXPIRY_INTERVAL;
            this.contentType = NULL_STRING;
            this.responseTopic = NULL_STRING;
            this.correlationData = null;

            return this;
        }

        private void decode(
            MqttConnectFW connect)
        {
            final int flags = connect.flags();
            final OctetsFW payload = connect.payload();

            final DirectBuffer buffer = payload.buffer();
            final int offset = payload.offset();
            final int limit = payload.limit();

            int progress = offset;
            decode:
            {
                if (isSetWillFlag(flags))
                {
                    final byte qos = (byte) ((flags & WILL_QOS_MASK) >>> 3);
                    if (qos != 0 && qos <= maximumQos)
                    {
                        willQos = (byte) (qos << 1);
                    }

                    if (isSetWillRetain(flags))
                    {
                        willRetain = (byte) RETAIN_FLAG;
                    }

                    willProperties = mqttPropertiesRO.tryWrap(buffer, progress, limit);
                    if (willProperties == null)
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    decode(willProperties);
                    progress = mqttPropertiesRO.limit();

                    willTopic = willTopicRO.tryWrap(buffer, progress, limit);
                    if (willTopic == null)
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    progress = willTopicRO.limit();

                    final DirectBuffer willPayloadBuffer = copyBuffer(buffer, progress, limit);
                    willPayload = willPayloadRO.tryWrap(willPayloadBuffer, 0, willPayloadBuffer.capacity());
                    if (willPayload == null)
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    progress = willPayloadRO.limit();
                }

                if (isSetUsername(flags))
                {
                    username = usernameRO.tryWrap(buffer, progress, limit);
                    if (username == null)
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    progress = usernameRO.limit();
                }

                if (isSetPassword(flags))
                {
                    password = passwordRO.tryWrap(buffer, progress, limit);
                    if (password == null)
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    progress = passwordRO.limit();
                }
            }
        }

        private void decode(
            MqttPropertiesFW properties)
        {
            willUserPropertiesRW.wrap(willUserPropertiesBuffer, 0, willUserPropertiesBuffer.capacity());

            final OctetsFW propertiesValue = properties.value();
            final DirectBuffer decodeBuffer = propertiesValue.buffer();
            final int decodeOffset = propertiesValue.offset();
            final int decodeLimit = propertiesValue.limit();

            decode:
            {
                for (int decodeProgress = decodeOffset; decodeProgress < decodeLimit; )
                {
                    final MqttPropertyFW mqttProperty = mqttPropertyRO.wrap(decodeBuffer, decodeProgress, decodeLimit);
                    switch (mqttProperty.kind())
                    {
                    case KIND_WILL_DELAY_INTERVAL:
                        willDelay = mqttProperty.willDelayInterval();
                        break;
                    case KIND_PAYLOAD_FORMAT:
                        payloadFormat = MqttPayloadFormat.valueOf(mqttProperty.payloadFormat());
                        break;
                    case KIND_EXPIRY_INTERVAL:
                        expiryInterval = mqttProperty.expiryInterval();
                        break;
                    case KIND_CONTENT_TYPE:
                        final String16FW mContentType = mqttProperty.contentType();
                        if (mContentType.value() != null)
                        {
                            final int offset = mContentType.offset();
                            final int limit = mContentType.limit();

                            contentType = contentTypeRO.wrap(mContentType.buffer(), offset, limit);
                        }
                        break;
                    case KIND_RESPONSE_TOPIC:
                        final String16FW mResponseTopic = mqttProperty.responseTopic();
                        if (mResponseTopic.value() != null)
                        {
                            final int offset = mResponseTopic.offset();
                            final int limit = mResponseTopic.limit();

                            responseTopic = responseTopicRO.wrap(mResponseTopic.buffer(), offset, limit);
                        }
                        break;
                    case KIND_CORRELATION_DATA:
                        correlationData = mqttProperty.correlationData().bytes();
                        break;
                    case KIND_USER_PROPERTY:
                        final MqttUserPropertyFW userProperty = mqttProperty.userProperty();
                        userPropertiesRW.item(c -> c.key(userProperty.key()).value(userProperty.value()));
                        break;
                    default:
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    decodeProgress = mqttProperty.limit();
                }
            }
        }
    }

    private final class MqttPublishHeader
    {
        private String topic;

        private int expiryInterval = DEFAULT_EXPIRY_INTERVAL;
        private String16FW contentType = NULL_STRING;
        private MqttPayloadFormat payloadFormat = DEFAULT_FORMAT;
        private String16FW responseTopic = NULL_STRING;
        private OctetsFW correlationData = null;

        private MqttPublishHeader reset()
        {
            this.topic = null;
            this.expiryInterval = DEFAULT_EXPIRY_INTERVAL;
            this.contentType = NULL_STRING;
            this.payloadFormat = DEFAULT_FORMAT;
            this.responseTopic = NULL_STRING;
            this.correlationData = null;

            return this;
        }

        private int decode(
            MqttServer server,
            String16FW topicName,
            MqttPropertiesFW properties)
        {
            this.topic = topicName.asString();
            int reasonCode = SUCCESS;
            userPropertiesRW.wrap(userPropertiesBuffer, 0, userPropertiesBuffer.capacity());

            final OctetsFW propertiesValue = properties.value();
            final DirectBuffer decodeBuffer = propertiesValue.buffer();
            final int decodeOffset = propertiesValue.offset();
            final int decodeLimit = propertiesValue.limit();

            if (topic == null)
            {
                reasonCode = PROTOCOL_ERROR;
            }
            else
            {
                int alias = 0;
                decode:
                for (int decodeProgress = decodeOffset; decodeProgress < decodeLimit; )
                {
                    final MqttPropertyFW mqttProperty = mqttPropertyRO.wrap(decodeBuffer, decodeProgress, decodeLimit);
                    switch (mqttProperty.kind())
                    {
                    case KIND_EXPIRY_INTERVAL:
                        expiryInterval = mqttProperty.expiryInterval();
                        break;
                    case KIND_CONTENT_TYPE:
                        final String16FW mContentType = mqttProperty.contentType();
                        if (mContentType.value() != null)
                        {
                            final int offset = mContentType.offset();
                            final int limit = mContentType.limit();

                            contentType = contentTypeRO.wrap(mContentType.buffer(), offset, limit);
                        }
                        break;
                    case KIND_TOPIC_ALIAS:
                        if (alias != 0)
                        {
                            reasonCode = PROTOCOL_ERROR;
                            break decode;
                        }

                        alias = mqttProperty.topicAlias() & 0xFFFF;

                        if (alias <= 0 || alias > server.topicAliasMaximum)
                        {
                            reasonCode = TOPIC_ALIAS_INVALID;
                            break decode;
                        }

                        if (topic.isEmpty())
                        {
                            if (!server.topicAliases.containsKey(alias))
                            {
                                reasonCode = PROTOCOL_ERROR;
                                break decode;
                            }
                            topic = server.topicAliases.get(alias);
                        }
                        else
                        {
                            server.topicAliases.put(alias, topic);
                        }
                        break;
                    case KIND_PAYLOAD_FORMAT:
                        payloadFormat = MqttPayloadFormat.valueOf(mqttProperty.payloadFormat());
                        break;
                    case KIND_RESPONSE_TOPIC:
                        final String16FW mResponseTopic = mqttProperty.responseTopic();
                        if (mResponseTopic.value() != null)
                        {
                            final int offset = mResponseTopic.offset();
                            final int limit = mResponseTopic.limit();

                            responseTopic = responseTopicRO.wrap(mResponseTopic.buffer(), offset, limit);
                        }
                        break;
                    case KIND_CORRELATION_DATA:
                        correlationData = mqttProperty.correlationData().bytes();
                        break;
                    case KIND_USER_PROPERTY:
                        final MqttUserPropertyFW userProperty = mqttProperty.userProperty();
                        userPropertiesRW.item(c -> c.key(userProperty.key()).value(userProperty.value()));
                        break;
                    default:
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    decodeProgress = mqttProperty.limit();
                }
            }

            return reasonCode;
        }
    }
}
