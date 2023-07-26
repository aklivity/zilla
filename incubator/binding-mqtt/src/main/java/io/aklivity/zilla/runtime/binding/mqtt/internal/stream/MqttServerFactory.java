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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.BAD_AUTHENTICATION_METHOD;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.BAD_USER_NAME_OR_PASSWORD;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.CLIENT_IDENTIFIER_NOT_VALID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.DISCONNECT_WITH_WILL_MESSAGE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.KEEP_ALIVE_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.MALFORMED_PACKET;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.NORMAL_DISCONNECT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.NO_SUBSCRIPTION_EXISTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.PAYLOAD_FORMAT_INVALID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.PROTOCOL_ERROR;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.QOS_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.RETAIN_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SERVER_MOVED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SESSION_TAKEN_OVER;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SHARED_SUBSCRIPTION_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SUBSCRIPTION_IDS_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SUCCESS;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.TOPIC_ALIAS_INVALID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.TOPIC_NAME_INVALID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.UNSUPPORTED_PROTOCOL_VERSION;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPublishFlags.RETAIN;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSubscribeFlags.NO_LOCAL;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSubscribeFlags.RETAIN_AS_PUBLISHED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSubscribeFlags.SEND_RETAINED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_AUTHENTICATION_DATA;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_AUTHENTICATION_METHOD;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_CONTENT_TYPE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_CORRELATION_DATA;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_EXPIRY_INTERVAL;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_MAXIMUM_PACKET_SIZE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_PAYLOAD_FORMAT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_RECEIVE_MAXIMUM;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_REQUEST_PROBLEM_INFORMATION;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_REQUEST_RESPONSE_INFORMATION;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_RESPONSE_TOPIC;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_SESSION_EXPIRY;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_SUBSCRIPTION_ID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_TOPIC_ALIAS;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_TOPIC_ALIAS_MAXIMUM;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_USER_PROPERTY;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_WILL_DELAY_INTERVAL;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.DataFW.FIELD_OFFSET_PAYLOAD;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttPublishDataExFW.Builder.DEFAULT_EXPIRY_INTERVAL;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttPublishDataExFW.Builder.DEFAULT_FORMAT;
import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_CREDITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.MutableBoolean;
import org.agrona.collections.Object2IntHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttBinding;
import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration;
import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttValidator;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttAuthorizationConfig.MqttConnectProperty;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttBindingConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttRouteConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttBinaryFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttCapabilities;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttEndReasonCode;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttMessageFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttQoS;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSessionStateFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttTopicFilterFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Varuint32FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.BinaryFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttConnackFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttConnectFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttDisconnectFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPacketHeaderFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPacketType;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPingReqFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPingRespFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertiesFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPublishFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttSubackFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttSubscribeFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttSubscribePayloadFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttUnsubackFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttUnsubackPayloadFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttUnsubscribeFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttUnsubscribePayloadFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttUserPropertyFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttWillFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttEndExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttPublishDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttResetExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class MqttServerFactory implements MqttStreamFactory
{
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);

    private static final String16FW MQTT_PROTOCOL_NAME = new String16FW("MQTT", BIG_ENDIAN);
    private static final int MQTT_PROTOCOL_VERSION = 5;
    private static final int MAXIMUM_CLIENT_ID_LENGTH = 36;
    private static final int CONNECT_FIXED_HEADER = 0b0001_0000;
    private static final int SUBSCRIBE_FIXED_HEADER = 0b1000_0010;
    private static final int UNSUBSCRIBE_FIXED_HEADER = 0b1010_0010;
    private static final int DISCONNECT_FIXED_HEADER = 0b1110_0000;

    private static final int CONNECT_RESERVED_MASK = 0b0000_0001;
    private static final int NO_FLAGS = 0b0000_0000;
    private static final int RETAIN_MASK = 0b0000_0001;
    private static final int PUBLISH_QOS1_MASK = 0b0000_0010;
    private static final int PUBLISH_QOS2_MASK = 0b0000_0100;
    private static final int NO_LOCAL_FLAG_MASK = 0b0000_0100;
    private static final int RETAIN_AS_PUBLISHED_MASK = 0b0000_1000;
    private static final int RETAIN_HANDLING_MASK = 0b0011_0000;
    private static final int BASIC_AUTHENTICATION_MASK = 0b1100_0000;

    private static final int WILL_FLAG_MASK = 0b0000_0100;
    private static final int CLEAN_START_FLAG_MASK = 0b0000_0010;
    private static final int WILL_QOS_MASK = 0b0001_1000;
    private static final int WILL_RETAIN_MASK = 0b0010_0000;
    private static final int USERNAME_MASK = 0b1000_0000;
    private static final int PASSWORD_MASK = 0b0100_0000;

    private static final int CONNECT_TOPIC_ALIAS_MAXIMUM_MASK = 0b0000_0001;
    private static final int CONNECT_SESSION_EXPIRY_INTERVAL_MASK = 0b0000_0010;
    private static final int CONNECT_MAXIMUM_PACKET_SIZE_MASK = 0b0000_0100;

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

    private static final int PUBLISH_FRAMING = 255;

    private static final String16FW NULL_STRING = new String16FW((String) null);
    public static final String SHARED_SUBSCRIPTION_LITERAL = "$share";

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

    private final MqttPublishDataExFW mqttPublishDataExRO = new MqttPublishDataExFW();
    private final MqttDataExFW mqttSubscribeDataExRO = new MqttDataExFW();
    private final MqttResetExFW mqttResetExRO = new MqttResetExFW();

    private final MqttBeginExFW.Builder mqttPublishBeginExRW = new MqttBeginExFW.Builder();
    private final MqttBeginExFW.Builder mqttSubscribeBeginExRW = new MqttBeginExFW.Builder();
    private final MqttBeginExFW.Builder mqttSessionBeginExRW = new MqttBeginExFW.Builder();
    private final MqttEndExFW.Builder mqttEndExRW = new MqttEndExFW.Builder();
    private final MqttDataExFW.Builder mqttPublishDataExRW = new MqttDataExFW.Builder();
    private final MqttDataExFW.Builder mqttSubscribeDataExRW = new MqttDataExFW.Builder();
    private final MqttDataExFW.Builder mqttSessionDataExRW = new MqttDataExFW.Builder();
    private final MqttFlushExFW.Builder mqttFlushExRW = new MqttFlushExFW.Builder();
    private final MqttMessageFW.Builder mqttMessageFW = new MqttMessageFW.Builder();
    private final MqttSessionStateFW.Builder mqttSessionStateFW = new MqttSessionStateFW.Builder();
    private final MqttPacketHeaderFW mqttPacketHeaderRO = new MqttPacketHeaderFW();
    private final MqttConnectFW mqttConnectRO = new MqttConnectFW();
    private final MqttWillFW mqttWillRO = new MqttWillFW();
    private final MqttPublishFW mqttPublishRO = new MqttPublishFW();
    private final MqttSubscribeFW mqttSubscribeRO = new MqttSubscribeFW();
    private final MqttSubscribePayloadFW mqttSubscribePayloadRO = new MqttSubscribePayloadFW();
    private final MqttUnsubscribeFW mqttUnsubscribeRO = new MqttUnsubscribeFW();
    private final MqttUnsubscribePayloadFW mqttUnsubscribePayloadRO = new MqttUnsubscribePayloadFW();
    private final MqttPingReqFW mqttPingReqRO = new MqttPingReqFW();
    private final MqttDisconnectFW mqttDisconnectRO = new MqttDisconnectFW();

    private final OctetsFW octetsRO = new OctetsFW();
    private final OctetsFW.Builder octetsRW = new OctetsFW.Builder();

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);
    private final MqttPropertyFW mqttPropertyRO = new MqttPropertyFW();
    private final MqttPropertyFW.Builder mqttPropertyRW = new MqttPropertyFW.Builder();

    private final MqttPropertiesFW mqttPropertiesRO = new MqttPropertiesFW();
    private final MqttSessionStateFW mqttSessionStateRO = new MqttSessionStateFW();

    private final String16FW.Builder clientIdRW = new String16FW.Builder(BIG_ENDIAN);

    private final String16FW clientIdRO = new String16FW(BIG_ENDIAN);
    private final String16FW contentTypeRO = new String16FW(BIG_ENDIAN);
    private final String16FW responseTopicRO = new String16FW(BIG_ENDIAN);
    private final String16FW willTopicRO = new String16FW(BIG_ENDIAN);
    private final String16FW usernameRO = new String16FW(BIG_ENDIAN);

    private final OctetsFW.Builder sessionPayloadRW = new OctetsFW.Builder();

    private final BinaryFW willPayloadRO = new BinaryFW();
    private final BinaryFW passwordRO = new BinaryFW();

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

    private final Array32FW.Builder<MqttTopicFilterFW.Builder, MqttTopicFilterFW> topicFiltersRW =
        new Array32FW.Builder<>(new MqttTopicFilterFW.Builder(), new MqttTopicFilterFW());
    private final Array32FW.Builder<MqttUserPropertyFW.Builder, MqttUserPropertyFW> willUserPropertiesRW =
        new Array32FW.Builder<>(new MqttUserPropertyFW.Builder(), new MqttUserPropertyFW());

    private final MqttServerDecoder decodeInitialType = this::decodeInitialType;
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
    private final boolean session;
    private final String serverReference;

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
    private final MutableDirectBuffer sessionExtBuffer;
    private final MutableDirectBuffer sessionStateBuffer;
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
    private final LongFunction<GuardHandler> supplyGuard;
    private final Long2ObjectHashMap<MqttBindingConfig> bindings;
    private final int mqttTypeId;

    private final long publishTimeoutMillis;
    private final long connectTimeoutMillis;
    private final int encodeBudgetMax;

    private final int sessionExpiryIntervalLimit;
    private final short keepAliveMinimum;
    private final short keepAliveMaximum;
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
    private final CharsetDecoder utf8Decoder;


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
        this.sessionExtBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.sessionStateBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
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
        this.supplyGuard = context::supplyGuard;
        this.bindings = new Long2ObjectHashMap<>();
        this.mqttTypeId = context.supplyTypeId(MqttBinding.NAME);
        this.publishTimeoutMillis = SECONDS.toMillis(config.publishTimeout());
        this.connectTimeoutMillis = SECONDS.toMillis(config.connectTimeout());
        this.sessionExpiryIntervalLimit = config.sessionExpiryInterval();
        this.keepAliveMinimum = config.keepAliveMinimum();
        this.keepAliveMaximum = config.keepAliveMaximum();
        this.maximumQos = config.maximumQos();
        this.retainedMessages = config.retainAvailable() ? (byte) 1 : 0;
        this.wildcardSubscriptions = config.wildcardSubscriptionAvailable() ? (byte) 1 : 0;
        this.subscriptionIdentifiers = config.subscriptionIdentifierAvailable() ? (byte) 1 : 0;
        this.sharedSubscriptions = config.sharedSubscriptionAvailable() ? (byte) 1 : 0;
        this.session = config.sessionsAvailable();
        this.topicAliasMaximumLimit = (short) Math.max(config.topicAliasMaximum(), 0);
        this.noLocal = config.noLocal();
        this.sessionExpiryGracePeriod = config.sessionExpiryGracePeriod();
        this.encodeBudgetMax = bufferPool.slotCapacity();
        this.validator = new MqttValidator();
        this.utf8Decoder = StandardCharsets.UTF_8.newDecoder();

        final Optional<String16FW> clientId = Optional.ofNullable(config.clientId()).map(String16FW::new);
        this.supplyClientId = clientId.isPresent() ? clientId::get : () -> new String16FW(UUID.randomUUID().toString());
        this.serverReference = config.serverReference();
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
        final long originId = begin.originId();
        final long routedId = begin.routedId();

        MqttBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final long initialId = begin.streamId();
            final long affinity = begin.affinity();
            final long replyId = supplyReplyId.applyAsLong(initialId);
            final long budgetId = supplyBudgetId.getAsLong();

            newStream = new MqttServer(
                binding.credentials(),
                binding.authField(),
                binding.options,
                binding.resolveId,
                sender,
                originId,
                routedId,
                initialId,
                replyId,
                affinity,
                budgetId)::onNetwork;
        }
        return newStream;
    }

    private int topicKey(
        String topic)
    {
        return System.identityHashCode(topic.intern());
    }

    private int subscribeKey(
        String clientId,
        long resolveId)
    {
        return System.identityHashCode((clientId + "_" + resolveId).intern());
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

        MessageConsumer receiver =
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
            .budgetId(budgetId)
            .reserved(reserved)
            .payload(buffer, index, length)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
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
        int reserved,
        int flags,
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
        long authorization,
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doWindow(
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

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
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
            .authorization(authorization)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
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

    private int decodeInitialType(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final MqttPacketHeaderFW packet = mqttPacketHeaderRO.tryWrap(buffer, offset, limit);

        decode:
        if (packet != null)
        {
            final int length = packet.remainingLength();
            final MqttPacketType packetType = MqttPacketType.valueOf(packet.typeAndFlags() >> 4);

            if (packetType != MqttPacketType.CONNECT)
            {
                server.doNetworkEnd(traceId, authorization);
                server.decoder = decodeIgnoreAll;
                break decode;
            }

            if (limit - packet.limit() >= length)
            {
                server.decodeablePacketBytes = packet.sizeof() + length;
                server.decoder = decodePacketType;
            }
        }

        return offset;
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

                progress = server.onDecodeConnect(traceId, authorization, buffer, progress, limit, mqttConnect);
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
                reasonCode = mqttPublishHeader.decode(server, publish.topicName(), publish.properties(), publish.typeAndFlags());
            }

            if (reasonCode == SUCCESS)
            {
                final String topic = mqttPublishHeader.topic;
                final int topicKey = topicKey(topic);
                MqttServer.MqttPublishStream publisher = server.publishStreams.get(topicKey);

                if (publisher == null)
                {
                    publisher = server.resolvePublishStream(traceId, authorization, topic);
                    if (publisher == null)
                    {
                        server.decodePublisherKey = 0;
                        server.decodeablePacketBytes = 0;
                        server.decoder = decodePacketType;
                        progress = publish.limit();
                        break decode;
                    }
                }

                server.decodePublisherKey = topicKey;

                final OctetsFW payload = publish.payload();
                final int payloadSize = payload.sizeof();

                if (mqttPublishHeaderRO.payloadFormat.equals(MqttPayloadFormat.TEXT) && invalidUtf8(payload))
                {
                    reasonCode = PAYLOAD_FORMAT_INVALID;
                    server.onDecodeError(traceId, authorization, reasonCode);
                    server.decoder = decodeIgnoreAll;
                }

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
                    server.onDecodePublish(traceId, authorization, reserved, payload);
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

    private boolean invalidUtf8(OctetsFW payload)
    {
        boolean invalid = false;
        byte[] payloadBytes = new byte[payload.sizeof()];
        payload.value().getBytes(0, payloadBytes);
        try
        {
            utf8Decoder.decode(ByteBuffer.wrap(payloadBytes));
        }
        catch (CharacterCodingException ex)
        {
            invalid = true;
        }
        return invalid;
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

            final MqttSubscribeFW subscribe = mqttSubscribeRO.tryWrap(buffer, offset, offset + server.decodeablePacketBytes);
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
        final int length = server.decodeablePacketBytes;

        int progress = offset;

        if (length > 0)
        {
            int reasonCode = SUCCESS;

            final MqttUnsubscribeFW unsubscribe = mqttUnsubscribeRO.tryWrap(buffer, offset,
                offset + server.decodeablePacketBytes);
            if (unsubscribe == null || unsubscribe.payload().sizeof() == 0 || unsubscribe.packetId() == 0)
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

    private final class MqttServer
    {
        private final MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long encodeBudgetId;

        private final Int2ObjectHashMap<MqttPublishStream> publishStreams;
        private final Int2ObjectHashMap<MqttSubscribeStream> subscribeStreams;
        private final Int2ObjectHashMap<String> topicAliases;
        private final Int2IntHashMap subscribePacketIds;
        private final Object2IntHashMap<String> unsubscribePacketIds;
        private final GuardHandler guard;
        private final Function<String, String> credentials;
        private final MqttConnectProperty authField;

        private MqttSessionStream sessionStream;

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

        private boolean serverDefinedKeepAlive = false;
        private boolean cleanStart = false;
        private short keepAlive;
        private long keepAliveTimeout;
        private boolean connected;

        private short topicAliasMaximum = 0;
        private int maximumPacketSize = Integer.MAX_VALUE;
        private int sessionExpiryInterval = 0;
        private boolean assignedClientId = false;
        private int propertyMask = 0;

        private int state;
        private long sessionId;

        private MqttServer(
            Function<String, String> credentials,
            MqttConnectProperty authField,
            MqttOptionsConfig options,
            ToLongFunction<String> resolveId,
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId,
            long replyId,
            long affinity,
            long budgetId)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = replyId;
            this.affinity = affinity;
            this.encodeBudgetId = budgetId;
            this.decoder = decodeInitialType;
            this.publishStreams = new Int2ObjectHashMap<>();
            this.subscribeStreams = new Int2ObjectHashMap<>();
            this.topicAliases = new Int2ObjectHashMap<>();
            this.subscribePacketIds = new Int2IntHashMap(-1);
            this.unsubscribePacketIds = new Object2IntHashMap<>(-1);
            this.guard = resolveGuard(options, resolveId);
            this.credentials = credentials;
            this.authField = authField;
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
            doSignalConnectTimeout();
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

                cleanupStreamsUsingAbort(traceId);

                doNetworkEnd(traceId, authorization);

                decoder = decodeIgnoreAll;
            }
        }

        private void onNetworkAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = MqttState.closeInitial(state);

            cleanupDecodeSlot();

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

            final int encodeWin = encodeMax - (int) (encodeSeq - encodeAck);
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
                if (session)
                {
                    final MqttEndExFW.Builder builder = mqttEndExRW.wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                        .typeId(mqttTypeId)
                        .reasonCode(r -> r.set(MqttEndReasonCode.KEEP_ALIVE_EXPIRY));
                    sessionStream.doSessionAppEnd(traceId, builder.build());
                }
                onDecodeError(traceId, authorization, KEEP_ALIVE_TIMEOUT);
                decoder = decodeIgnoreAll;
            }
            else
            {
                keepAliveTimeoutId =
                    signaler.signalAt(keepAliveTimeoutAt, originId, routedId, replyId, KEEP_ALIVE_TIMEOUT_SIGNAL, 0);
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
                cleanupStreamsUsingAbort(traceId);
                doNetworkEnd(traceId, authorization);
                decoder = decodeIgnoreAll;
            }
        }

        private void doCancelConnectTimeout()
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
                    final int sessionExpiryInterval = (int) mqttProperty.sessionExpiry();
                    this.sessionExpiryInterval = Math.min(sessionExpiryInterval, sessionExpiryIntervalLimit);
                    break;
                case KIND_RECEIVE_MAXIMUM:
                case KIND_MAXIMUM_PACKET_SIZE:
                    final int maximumPacketSize = (int) mqttProperty.maximumPacketSize();
                    if (maximumPacketSize == 0 || isSetMaximumPacketSize(propertyMask))
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.propertyMask |= CONNECT_TOPIC_ALIAS_MAXIMUM_MASK;
                    this.maximumPacketSize = maximumPacketSize;
                    break;
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
            DirectBuffer buffer,
            int progress,
            int limit,
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
                progress = connect.limit();
                progress = payload.decode(buffer, progress, limit, connect.flags());
                reasonCode = payload.reasonCode;

                if (reasonCode != SUCCESS)
                {
                    break decode;
                }
                if (isCleanStart(connect.flags()))
                {
                    this.cleanStart = true;
                }
                keepAlive = (short) Math.min(Math.max(connect.keepAlive(), keepAliveMinimum), keepAliveMaximum);
                serverDefinedKeepAlive = keepAlive != connect.keepAlive();
                keepAliveTimeout = Math.round(TimeUnit.SECONDS.toMillis(keepAlive) * 1.5);
                doSignalKeepAliveTimeout();

                long sessionAuth = authorization;
                if (guard != null)
                {
                    String authField = null;
                    if (this.authField.equals(MqttConnectProperty.USERNAME))
                    {
                        authField = payload.username != null ? payload.username.asString() : null;
                    }
                    else if (this.authField.equals(MqttConnectProperty.PASSWORD))
                    {
                        authField = payload.password != null ?
                            payload.password.bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)) : null;
                    }

                    final String credentialsMatch = credentials.apply(authField);

                    if (credentialsMatch != null)
                    {
                        sessionAuth = guard.reauthorize(initialId, credentialsMatch);
                    }
                }

                final MqttBindingConfig binding = bindings.get(routedId);

                final MqttRouteConfig resolved = binding != null ? binding.resolve(sessionAuth) : null;

                if (resolved == null)
                {
                    reasonCode = BAD_USER_NAME_OR_PASSWORD;
                    break decode;
                }

                this.sessionId = sessionAuth;
                if (session)
                {
                    resolveSession(traceId, sessionAuth, resolved.id, connect, payload);
                }
                else
                {
                    doEncodeConnack(traceId, authorization, reasonCode, assignedClientId, false, null);
                    connected = true;
                }

                doCancelConnectTimeout();

                decoder = decodePacketType;
            }

            if (reasonCode == BAD_USER_NAME_OR_PASSWORD)
            {
                doCancelConnectTimeout();
                doNetworkEnd(traceId, authorization);
                decoder = decodeIgnoreAll;
                progress = connect.limit();
            }
            else if (reasonCode != SUCCESS)
            {
                doCancelConnectTimeout();
                doEncodeConnack(traceId, authorization, reasonCode, assignedClientId, false, null);
                doNetworkEnd(traceId, authorization);
                decoder = decodeIgnoreAll;
                progress = connect.limit();
            }
            return progress;
        }

        private void resolveSession(
            long traceId,
            long authorization,
            long resolvedId,
            MqttConnectFW connect,
            MqttConnectPayload payload)
        {
            final int flags = connect.flags();

            final boolean willFlagSet = isSetWillFlag(flags);

            final MqttBeginExFW.Builder builder = mqttSessionBeginExRW.wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                .typeId(mqttTypeId)
                .session(sessionBuilder ->
                {
                    sessionBuilder.clientId(clientId);
                    sessionBuilder.expiry(sessionExpiryInterval);
                    sessionBuilder.serverReference(serverReference);
                    if (willFlagSet)
                    {
                        final int willFlags = decodeWillFlags(flags);
                        final int willQos = decodeWillQos(flags);
                        final MqttMessageFW.Builder willMessageBuilder =
                            mqttMessageFW.wrap(willMessageBuffer, 0, willMessageBuffer.capacity())
                            .topic(payload.willTopic)
                            .delay(payload.willDelay)
                            .qos(willQos)
                            .flags(willFlags)
                            .expiryInterval(payload.expiryInterval)
                            .contentType(payload.contentType)
                            .format(f -> f.set(payload.payloadFormat))
                            .responseTopic(payload.responseTopic)
                            .correlation(c -> c.bytes(payload.correlationData));

                        final Array32FW<MqttUserPropertyFW> userProperties = willUserPropertiesRW.build();
                        userProperties.forEach(
                            c -> willMessageBuilder.propertiesItem(p -> p.key(c.key()).value(c.value())));
                        willMessageBuilder.payload(p -> p.bytes(payload.willPayload.bytes()));
                        sessionBuilder.will(willMessageBuilder.build());
                    }
                });

            if (sessionStream == null)
            {
                sessionStream = new MqttSessionStream(originId, resolvedId, 0);
            }

            sessionStream.doSessionBegin(traceId, affinity, builder.build());
        }

        private MqttPublishStream resolvePublishStream(
            long traceId,
            long authorization,
            String topic)
        {
            MqttPublishStream stream = null;

            final MqttBindingConfig binding = bindings.get(routedId);
            final MqttRouteConfig resolved = binding != null ?
                binding.resolve(sessionId, topic, MqttCapabilities.PUBLISH_ONLY) : null;

            if (resolved != null)
            {
                final long resolvedId = resolved.id;
                final int topicKey = topicKey(topic);

                stream = publishStreams.computeIfAbsent(topicKey, s -> new MqttPublishStream(routedId, resolvedId, topic));
                stream.doPublishBegin(traceId, affinity);
            }
            else
            {
                onDecodeError(traceId, authorization, TOPIC_NAME_INVALID);
                decoder = decodeIgnoreAll;
            }

            return stream;
        }

        private void onDecodePublish(
            long traceId,
            long authorization,
            int reserved,
            OctetsFW payload)
        {
            final int topicKey = topicKey(mqttPublishHeaderRO.topic);
            MqttPublishStream stream = publishStreams.get(topicKey);

            final MqttDataExFW.Builder builder = mqttPublishDataExRW.wrap(dataExtBuffer, 0, dataExtBuffer.capacity())
                .typeId(mqttTypeId)
                .publish(publishBuilder ->
                {
                    publishBuilder.qos(mqttPublishHeaderRO.qos);
                    publishBuilder.flags(mqttPublishHeaderRO.flags);
                    publishBuilder.expiryInterval(mqttPublishHeaderRO.expiryInterval);
                    publishBuilder.contentType(mqttPublishHeaderRO.contentType);
                    publishBuilder.format(f -> f.set(mqttPublishHeaderRO.payloadFormat));
                    publishBuilder.responseTopic(mqttPublishHeaderRO.responseTopic);
                    publishBuilder.correlation(c -> c.bytes(mqttPublishHeaderRO.correlationData));
                    final Array32FW<MqttUserPropertyFW> userProperties = userPropertiesRW.build();
                    userProperties.forEach(c -> publishBuilder.propertiesItem(p -> p.key(c.key()).value(c.value())));
                });


            final MqttDataExFW dataEx = builder.build();
            if (stream != null)
            {
                stream.doPublishData(traceId, reserved, payload, dataEx);
            }
            doSignalKeepAliveTimeout();
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
                final List<Subscription> newSubscriptions = new ArrayList<>();

                for (int decodeProgress = decodeOffset; decodeProgress < decodeLimit; )
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
                    if (wildcardSubscriptions == 0 && (filter.contains("+") || filter.contains("#")))
                    {
                        onDecodeError(traceId, authorization, WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED);
                        decoder = decodeIgnoreAll;
                        break;
                    }

                    if (sharedSubscriptions == 0 && filter.contains(SHARED_SUBSCRIPTION_LITERAL))
                    {
                        onDecodeError(traceId, authorization, SHARED_SUBSCRIPTION_NOT_SUPPORTED);
                        decoder = decodeIgnoreAll;
                        break;
                    }

                    if (subscriptionIdentifiers == 0 && containsSubscriptionId)
                    {
                        onDecodeError(traceId, authorization, SUBSCRIPTION_IDS_NOT_SUPPORTED);
                        decoder = decodeIgnoreAll;
                        break;
                    }

                    final int options = mqttSubscribePayload.options();
                    final int flags = calculateSubscribeFlags(traceId, authorization, options);

                    if (!noLocal && isSetNoLocal(flags))
                    {
                        onDecodeError(traceId, authorization, PROTOCOL_ERROR);
                        decoder = decodeIgnoreAll;
                        break;
                    }

                    Subscription subscription = new Subscription();
                    subscription.id = subscriptionId;
                    subscription.filter = filter;
                    subscription.flags = flags;
                    //TODO: what if we don't have a subscriptionId
                    subscribePacketIds.put(subscriptionId, packetId);
                    newSubscriptions.add(subscription);
                }

                if (session)
                {
                    final MqttSessionStateFW.Builder sessionStateBuilder =
                        mqttSessionStateFW.wrap(sessionStateBuffer, 0, sessionStateBuffer.capacity())
                            .clientId(clientId);

                    newSubscriptions.forEach(subscription ->
                        {
                            sessionStateBuilder.subscriptionsItem(subscriptionBuilder ->
                            {
                                subscriptionBuilder.subscriptionId(subscription.id);
                                subscriptionBuilder.flags(subscription.flags);
                                subscriptionBuilder.pattern(subscription.filter);
                            });
                            sessionStream.unAckedSubscriptions.add(subscription);
                        }
                    );

                    final MqttSessionStateFW sessionState = sessionStateBuilder.build();
                    final int payloadSize = sessionState.sizeof();

                    //TODO: is this correct? What is this?
                    int reserved = payloadSize;

                    sessionStream.doSessionData(traceId, reserved, sessionState);
                }
                else
                {
                    openSubscribeStreams(packetId, traceId, authorization, newSubscriptions, false);
                }
            }
            doSignalKeepAliveTimeout();
        }

        private void openSubscribeStreams(
            int packetId,
            long traceId,
            long authorization,
            List<Subscription> subscriptions,
            boolean adminSubscribe)
        {
            final Long2ObjectHashMap<List<Subscription>> subscriptionsByRouteId = new Long2ObjectHashMap<>();

            for (Subscription subscription : subscriptions)
            {
                final MqttBindingConfig binding = bindings.get(routedId);
                final MqttRouteConfig resolved =
                    binding != null ? binding.resolve(sessionId, subscription.filter, MqttCapabilities.SUBSCRIBE_ONLY) : null;

                if (resolved != null)
                {
                    subscriptionsByRouteId.computeIfAbsent(resolved.id, s -> new ArrayList<>()).add(subscription);
                }
                //TODO: unroutable
            }

            subscriptionsByRouteId.forEach((key, value) ->
            {
                int subscribeKey = subscribeKey(clientId.asString(), key);
                MqttSubscribeStream stream = subscribeStreams.computeIfAbsent(subscribeKey, s ->
                    new MqttSubscribeStream(routedId, key, adminSubscribe));
                stream.packetId = packetId;
                stream.doSubscribeBeginOrFlush(traceId, affinity, subscribeKey, value);
            });

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

            final List<String> topicFilters = new ArrayList<>();
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

                topicFilters.add(topicFilter);

                decodeProgress = mqttUnsubscribePayload.limit();
            }


            if (decodeReasonCode != SUCCESS)
            {
                onDecodeError(traceId, authorization, decodeReasonCode);
            }
            else
            {
                if (session)
                {
                    List<Subscription> unAckedSubscriptions = sessionStream.unAckedSubscriptions.stream()
                        .filter(s -> topicFilters.contains(s.filter) && subscribePacketIds.containsKey(s.id))
                        .collect(Collectors.toList());

                    if (!unAckedSubscriptions.isEmpty())
                    {
                        sessionStream.deferredUnsubscribes.put(packetId, topicFilters);
                        return;
                    }
                    topicFilters.forEach(filter -> unsubscribePacketIds.put(filter, packetId));
                    sendNewSessionStateForUnsubscribe(traceId, authorization, topicFilters);
                }
                else
                {
                    sendUnsuback(packetId, traceId, authorization, topicFilters, false);
                }
                doSignalKeepAliveTimeout();
            }
        }

        private void sendNewSessionStateForUnsubscribe(
            long traceId,
            long authorization,
            List<String> topicFilters)
        {
            List<Subscription> currentState = sessionStream.getSubscriptions();
            List<Subscription> newState = currentState.stream()
                .filter(subscription -> !topicFilters.contains(subscription.filter))
                .collect(Collectors.toList());

            final MqttSessionStateFW.Builder sessionStateBuilder =
                mqttSessionStateFW.wrap(sessionStateBuffer, 0, sessionStateBuffer.capacity())
                    .clientId(clientId);

            newState.forEach(subscription ->
                sessionStateBuilder.subscriptionsItem(subscriptionBuilder ->
                {
                    subscriptionBuilder.pattern(subscription.filter);
                    subscriptionBuilder.subscriptionId(subscription.id);
                    subscriptionBuilder.flags(subscription.flags);
                })
            );

            final MqttSessionStateFW sessionState = sessionStateBuilder.build();
            final int payloadSize = sessionState.sizeof();

            //TODO: is this correct? What is this?
            int reserved = payloadSize;

            sessionStream.doSessionData(traceId, reserved, sessionState);
        }

        private void sendUnsuback(
            int packetId,
            long traceId,
            long authorization,
            List<String> topicFilters,
            boolean adminUnsubscribe)
        {
            final MutableDirectBuffer encodeBuffer = payloadBuffer;
            final int encodeOffset = 0;
            final int encodeLimit = payloadBuffer.capacity();

            int encodeProgress = encodeOffset;

            final Map<MqttSubscribeStream, List<String>> filtersByStream = new HashMap<>();

            for (String topicFilter : topicFilters)
            {
                final MqttBindingConfig binding = bindings.get(routedId);
                final MqttRouteConfig resolved =
                    binding != null ? binding.resolve(sessionId, topicFilter, MqttCapabilities.SUBSCRIBE_ONLY) : null;
                final int subscribeKey = subscribeKey(clientId.asString(), resolved.id);
                final MqttSubscribeStream stream = subscribeStreams.get(subscribeKey);

                filtersByStream.computeIfAbsent(stream, s -> new ArrayList<>()).add(topicFilter);

                Optional<Subscription> subscription = stream.getSubscriptionByFilter(topicFilter);

                int encodeReasonCode = subscription.isPresent() ? SUCCESS : NO_SUBSCRIPTION_EXISTED;
                final MqttUnsubackPayloadFW mqttUnsubackPayload =
                    mqttUnsubackPayloadRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .reasonCode(encodeReasonCode)
                        .build();
                encodeProgress = mqttUnsubackPayload.limit();
            }

            filtersByStream.forEach(
                (stream, filters) -> stream.doSubscribeFlushOrEnd(traceId, filters));
            if (!adminUnsubscribe)
            {
                final OctetsFW encodePayload = octetsRO.wrap(encodeBuffer, encodeOffset, encodeProgress);
                doEncodeUnsuback(traceId, authorization, packetId, encodePayload);
            }
        }

        private void onDecodePingReq(
            long traceId,
            long authorization,
            MqttPingReqFW ping)
        {
            doSignalKeepAliveTimeout();
            doEncodePingResp(traceId, authorization);
        }

        private void onDecodeDisconnect(
            long traceId,
            long authorization,
            MqttDisconnectFW disconnect)
        {
            state = MqttState.closingInitial(state);
            if (session)
            {
                final MqttEndExFW.Builder builder = mqttEndExRW.wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                    .typeId(mqttTypeId)
                    .reasonCode(r -> r.set(
                        disconnect.reasonCode() == DISCONNECT_WITH_WILL_MESSAGE ?
                            MqttEndReasonCode.DISCONNECT_WITH_WILL :
                            MqttEndReasonCode.DISCONNECT));
                sessionStream.doSessionAppEnd(traceId, builder.build());
            }
            closeStreams(traceId, authorization);
            doNetworkEnd(traceId, authorization);
        }

        private void onDecodeError(
            long traceId,
            long authorization,
            int reasonCode)
        {
            switch (reasonCode)
            {
            case SESSION_TAKEN_OVER:
                closeStreams(traceId, authorization);
                break;
            default:
                cleanupStreamsUsingAbort(traceId);
                break;
            }
            if (connected)
            {
                doEncodeDisconnect(traceId, authorization, reasonCode, null);
            }
            else
            {
                doEncodeConnack(traceId, authorization, reasonCode, false, false, null);
            }

            doNetworkEnd(traceId, authorization);
        }

        private void doNetworkBegin(
            long traceId,
            long authorization)
        {
            state = MqttState.openingReply(state);

            doBegin(network, originId, routedId, replyId, encodeSeq, encodeAck, encodeMax,
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

        private void doNetworkEnd(
            long traceId,
            long authorization)
        {
            if (!MqttState.replyClosed(state))
            {
                state = MqttState.closeReply(state);

                cleanupBudgetCreditor();
                cleanupEncodeSlot();

                doEnd(network, originId, routedId, replyId, encodeSeq, encodeAck, encodeMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doNetworkAbort(
            long traceId,
            long authorization)
        {
            if (!MqttState.replyClosed(state))
            {
                state = MqttState.closeReply(state);

                cleanupBudgetCreditor();
                cleanupEncodeSlot();

                doAbort(network, originId, routedId, replyId, encodeSeq, encodeAck, encodeMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doNetworkReset(
            long traceId,
            long authorization)
        {
            if (!MqttState.initialClosed(state))
            {
                state = MqttState.closeInitial(state);

                cleanupDecodeSlot();

                doReset(network, originId, routedId, initialId, decodeSeq, decodeAck, decodeMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
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

                doWindow(network, originId, routedId, initialId, decodeSeq, decodeAck, decodeMax,
                    traceId, authorization, budgetId, 0);
            }
        }

        private void doEncodePublish(
            long traceId,
            long authorization,
            int flags,
            List<Subscription> subscriptions,
            OctetsFW payload,
            OctetsFW extension)
        {
            if ((flags & 0x02) != 0)
            {
                final MqttDataExFW subscribeDataEx = extension.get(mqttSubscribeDataExRO::tryWrap);
                final int payloadSize = payload.sizeof();
                final int deferred = subscribeDataEx.subscribe().deferred();
                final int expiryInterval = subscribeDataEx.subscribe().expiryInterval();
                final String16FW contentType = subscribeDataEx.subscribe().contentType();
                final String16FW responseTopic = subscribeDataEx.subscribe().responseTopic();
                final MqttBinaryFW correlation = subscribeDataEx.subscribe().correlation();
                final Array32FW<Varuint32FW> subscriptionIds = subscribeDataEx.subscribe().subscriptionIds();
                final Array32FW<io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttUserPropertyFW> properties =
                    subscribeDataEx.subscribe().properties();

                String topicName = subscribeDataEx.subscribe().topic().asString();

                final int topicNameLength = topicName != null ? topicName.length() : 0;

                AtomicInteger propertiesSize = new AtomicInteger();

                MutableBoolean retainAsPublished = new MutableBoolean(false);

                subscriptionIds.forEach(subscriptionId ->
                {
                    if (subscriptionId.value() > 0)
                    {
                        Optional<Subscription> result = subscriptions.stream()
                            .filter(subscription -> subscription.id == subscriptionId.value())
                            .findFirst();
                        retainAsPublished.set(retainAsPublished.value | result.isPresent() && result.get().retainAsPublished());
                        mqttPropertyRW.wrap(propertyBuffer, propertiesSize.get(), propertyBuffer.capacity())
                            .subscriptionId(v -> v.set(subscriptionId.value()));
                        propertiesSize.set(mqttPropertyRW.limit());
                    }
                });

                final int publishApplicationFlags = retainAsPublished.get() ?
                    subscribeDataEx.subscribe().flags() : subscribeDataEx.subscribe().flags() & ~RETAIN_FLAG;

                final int qos = subscribeDataEx.subscribe().qos();
                final int publishNetworkTypeAndFlags = PUBLISH_TYPE << 4 |
                    calculatePublishNetworkFlags(PUBLISH_TYPE << 4 | publishApplicationFlags, qos);

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
                    .payloadFormat((byte) subscribeDataEx.subscribe().format().get().ordinal())
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
                        .typeAndFlags(publishNetworkTypeAndFlags)
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

        private int calculatePublishNetworkFlags(int applicationTypeAndFlags, int qos)
        {
            int flags = 0;

            if ((applicationTypeAndFlags & RETAIN_FLAG) != 0)
            {
                flags |= RETAIN_MASK;
            }

            if (qos == MqttQoS.AT_LEAST_ONCE.value())
            {
                flags |= PUBLISH_QOS1_MASK;
            }
            else if (qos == MqttQoS.EXACTLY_ONCE.value())
            {
                flags |= PUBLISH_QOS2_MASK;
            }
            return flags;
        }

        private void doEncodeConnack(
            long traceId,
            long authorization,
            int reasonCode,
            boolean assignedClientId,
            boolean sessionPresent,
            String16FW serverReference)
        {
            int propertiesSize = 0;

            MqttPropertyFW mqttProperty;
            if (reasonCode == SUCCESS)
            {
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
                    mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
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

                if (serverDefinedKeepAlive)
                {
                    mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .serverKeepAlive(this.keepAlive)
                        .build();
                    propertiesSize = mqttProperty.limit();
                }
            }

            if (serverReference != null)
            {
                mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                    .serverReference(serverReference)
                    .build();
                propertiesSize = mqttProperty.limit();
            }

            int flags = sessionPresent ? CONNACK_SESSION_PRESENT : 0x00;

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
            byte[] subscriptions)
        {
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
            int reasonCode,
            String16FW serverReference)
        {
            int propertiesSize = 0;

            MqttPropertyFW mqttProperty;
            if (serverReference != null)
            {
                mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                    .serverReference(serverReference)
                    .build();
                propertiesSize = mqttProperty.limit();
            }

            final int propertySize0 = propertiesSize;
            final MqttDisconnectFW disconnect =
                mqttDisconnectRW.wrap(writeBuffer, FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                    .typeAndFlags(0xe0)
                    .remainingLength(2 + propertySize0)
                    .reasonCode(reasonCode & 0xff)
                    .properties(p -> p.length(propertySize0)
                        .value(propertyBuffer, 0, propertySize0))
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
            final int encodeWin = encodeMax - (int) (encodeSeq - encodeAck + encodeSlotOffset);
            final int length = Math.min(maxLength, Math.max(encodeWin - encodePad, 0));

            if (length > 0)
            {
                final int reserved = length + encodePad;

                doData(network, originId, routedId, replyId, encodeSeq, encodeAck, encodeMax,
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
                cleanupEncodeSlot();

                if (publishStreams.isEmpty() && subscribeStreams.isEmpty() && decoder == decodeIgnoreAll)
                {
                    doNetworkEnd(traceId, authorization);
                }
            }
        }

        private void decodeNetwork(
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
                    decodeSlotReserved = (int) ((long) reserved * (limit - progress) / (limit - offset));
                }
            }
            else
            {
                cleanupDecodeSlot();

                if (MqttState.initialClosing(state))
                {
                    state = MqttState.closeInitial(state);
                    cleanupStreamsUsingAbort(traceId);
                    doNetworkEnd(traceId, authorization);
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
            cleanupStreamsUsingAbort(traceId);

            doNetworkReset(traceId, authorization);
            doNetworkAbort(traceId, authorization);
        }

        private void cleanupStreamsUsingAbort(
            long traceId)
        {
            publishStreams.values().forEach(s -> s.cleanupAbort(traceId));
            subscribeStreams.values().forEach(s -> s.cleanupAbort(traceId));
            if (sessionStream != null)
            {
                sessionStream.cleanupAbort(traceId);
            }
        }

        private void closeStreams(
            long traceId,
            long authorization)
        {
            publishStreams.values().forEach(s -> s.doPublishAppEnd(traceId));
            subscribeStreams.values().forEach(s -> s.doSubscribeAppEnd(traceId));
            if (sessionStream != null)
            {
                sessionStream.cleanupEnd(traceId);
            }
        }

        private void cleanupBudgetCreditor()
        {
            if (encodeBudgetIndex != NO_CREDITOR_INDEX)
            {
                creditor.release(encodeBudgetIndex);
                encodeBudgetIndex = NO_CREDITOR_INDEX;
            }
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                bufferPool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
                decodeSlotReserved = 0;
            }
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                bufferPool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeSlotTraceId = 0;
            }
        }

        private void doSignalKeepAliveTimeout()
        {
            if (keepAlive > 0)
            {
                keepAliveTimeoutAt = System.currentTimeMillis() + keepAliveTimeout;

                if (keepAliveTimeoutId == NO_CANCEL_ID)
                {
                    keepAliveTimeoutId =
                        signaler.signalAt(keepAliveTimeoutAt, originId, routedId, replyId, KEEP_ALIVE_TIMEOUT_SIGNAL, 0);
                }
            }
        }

        private void doSignalConnectTimeout()
        {
            connectTimeoutAt = System.currentTimeMillis() + connectTimeoutMillis;

            if (connectTimeoutId == NO_CANCEL_ID)
            {
                connectTimeoutId = signaler.signalAt(connectTimeoutAt, originId, routedId, replyId, CONNECT_TIMEOUT_SIGNAL, 0);
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
            private int id = 0;
            private String filter;
            private int qos;
            private int flags;

            private boolean retainAsPublished()
            {
                return (flags & RETAIN_AS_PUBLISHED_FLAG) == RETAIN_AS_PUBLISHED_FLAG;
            }

            @Override
            public boolean equals(Object obj)
            {
                if (obj == this)
                {
                    return true;
                }
                if (!(obj instanceof Subscription))
                {
                    return false;
                }
                Subscription other = (Subscription) obj;
                return this.id == other.id && Objects.equals(this.filter, other.filter) &&
                    this.qos == other.qos && this.flags == other.flags;
            }

            @Override
            public int hashCode()
            {
                return Objects.hash(this.id, this.filter, this.qos, this.flags);
            }
        }

        private class MqttSessionStream
        {
            private List<Subscription> subscriptions;
            private final List<Subscription> unAckedSubscriptions;
            private final Int2ObjectHashMap<List<String>> deferredUnsubscribes;
            private MessageConsumer application;

            private long originId;
            private long routedId;
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
            private int packetId;

            private int state;

            private long sessionExpiresId = NO_CANCEL_ID;
            private long sessionExpiresAt;

            MqttSessionStream(
                long originId,
                long routedId,
                int packetId)
            {
                this.subscriptions = new ArrayList<>();
                this.originId = originId;
                this.routedId = routedId;
                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.packetId = packetId;
                this.unAckedSubscriptions = new ArrayList<>();
                this.deferredUnsubscribes = new Int2ObjectHashMap<>();
            }

            private void onSession(
                int msgTypeId,
                DirectBuffer buffer,
                int index,
                int length)
            {
                switch (msgTypeId)
                {
                case BeginFW.TYPE_ID:
                    final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                    onSessionBegin(begin);
                    break;
                case DataFW.TYPE_ID:
                    final DataFW data = dataRO.wrap(buffer, index, index + length);
                    onSessionData(data);
                    break;
                case EndFW.TYPE_ID:
                    final EndFW end = endRO.wrap(buffer, index, index + length);
                    onSessionEnd(end);
                    break;
                case AbortFW.TYPE_ID:
                    final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                    onSessionAbort(abort);
                    break;
                case WindowFW.TYPE_ID:
                    final WindowFW window = windowRO.wrap(buffer, index, index + length);
                    onSessionWindow(window);
                    break;
                case ResetFW.TYPE_ID:
                    final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                    onSessionReset(reset);
                    break;
                case SignalFW.TYPE_ID:
                    final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                    onSessionSignal(signal);
                    break;
                }
            }

            private void onSessionWindow(
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
                    doCancelConnectTimeout();
                }

                this.state = MqttState.openInitial(state);
                this.budgetId = budgetId;

                assert acknowledge <= sequence;
                assert sequence <= initialSeq;
                assert acknowledge >= initialAck;
                assert maximum + acknowledge >= initialMax + initialAck;

                initialAck = acknowledge;
                initialMax = maximum;
                initialPad = padding;

                assert initialAck <= initialSeq;

                if (budgetId != 0L && debitorIndex == NO_DEBITOR_INDEX)
                {
                    debitor = supplyDebitor.apply(budgetId);
                    debitorIndex = debitor.acquire(budgetId, initialId, MqttServer.this::decodeNetwork);
                }

                if (MqttState.initialClosing(state) && !MqttState.initialClosed(state))
                {
                    doSessionAppEnd(traceId, EMPTY_OCTETS);
                }
            }

            private void onSessionReset(
                ResetFW reset)
            {
                final long traceId = reset.traceId();
                final long authorization = reset.authorization();

                final OctetsFW extension = reset.extension();
                final MqttResetExFW mqttResetEx = extension.get(mqttResetExRO::tryWrap);

                String16FW serverReference = mqttResetEx.serverReference();
                byte reasonCode = SUCCESS;
                if (serverReference != null && serverReference.length() != 0)
                {
                    reasonCode = SERVER_MOVED;
                }
                if (!connected)
                {
                    doCancelConnectTimeout();

                    doEncodeConnack(traceId, authorization, reasonCode, assignedClientId, false, serverReference);
                }
                else
                {
                    doEncodeDisconnect(traceId, authorization, reasonCode, serverReference);
                }

                setInitialClosed();

                decodeNetwork(traceId);
                cleanupAbort(traceId);
            }

            private void onSessionSignal(
                SignalFW signal)
            {
                final int signalId = signal.signalId();

                switch (signalId)
                {
                default:
                    break;
                }
            }

            private void onSessionBegin(
                BeginFW begin)
            {
                state = MqttState.openReply(state);

                final long traceId = begin.traceId();
                final long authorization = begin.authorization();

                doSessionWindow(traceId, encodeSlotOffset, encodeBudgetMax);
            }

            private void onSessionData(
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
                    doSessionReset(traceId);
                    doNetworkAbort(traceId, authorization);
                }
                else
                {
                    final DirectBuffer buffer = payload.buffer();
                    final int offset = payload.offset();
                    final int limit = payload.limit();

                    MqttSessionStateFW sessionState = mqttSessionStateRO.tryWrap(buffer, offset, limit);

                    byte reasonCode = SUCCESS;
                    if (!connected)
                    {
                        boolean sessionPresent = false;
                        if (sessionState != null)
                        {
                            if (cleanStart)
                            {
                                doSessionData(traceId, 0, emptyRO);
                            }
                            else
                            {
                                List<Subscription> subscriptions = new ArrayList<>();
                                sessionState.subscriptions().forEach(filter ->
                                {
                                    Subscription subscription = new Subscription();
                                    subscription.id = (int) filter.subscriptionId();
                                    subscription.filter = filter.pattern().asString();
                                    subscription.flags = filter.flags();
                                    subscriptions.add(subscription);
                                });
                                openSubscribeStreams(packetId, traceId, authorization, subscriptions, true);
                                sessionPresent = true;
                            }
                        }
                        doEncodeConnack(traceId, authorization, reasonCode, assignedClientId, sessionPresent, null);
                        connected = true;
                    }
                    else
                    {
                        if (sessionState != null)
                        {
                            List<Subscription> newState = new ArrayList<>();
                            sessionState.subscriptions().forEach(filter ->
                            {
                                Subscription subscription = new Subscription();
                                subscription.id = (int) filter.subscriptionId();
                                subscription.filter = filter.pattern().asString();
                                subscription.flags = filter.flags();
                                newState.add(subscription);
                            });
                            List<Subscription> currentSubscriptions = sessionStream.getSubscriptions();
                            if (newState.size() > currentSubscriptions.size())
                            {
                                List<Subscription> newSubscriptions = newState.stream()
                                    .filter(s -> !currentSubscriptions.contains(s))
                                    .collect(Collectors.toList());
                                if (subscribePacketIds.containsKey(newSubscriptions.get(0).id))
                                {
                                    int packetId = subscribePacketIds.get(newSubscriptions.get(0).id);
                                    newSubscriptions.forEach(sub -> subscribePacketIds.remove(sub.id));
                                    openSubscribeStreams(packetId, traceId, authorization, newSubscriptions, false);
                                }
                                else
                                {
                                    openSubscribeStreams(0, traceId, authorization, newState, true);
                                }
                                unAckedSubscriptions.removeAll(newSubscriptions);
                            }
                            else
                            {
                                List<String> removedFilters = currentSubscriptions.stream()
                                    .filter(s -> !newState.contains(s))
                                    .map(s -> s.filter)
                                    .collect(Collectors.toList());

                                if (!removedFilters.isEmpty())
                                {
                                    Map<Integer, List<String>> packetIdToFilters = removedFilters.stream()
                                        .filter(unsubscribePacketIds::containsKey)
                                        .collect(Collectors.groupingBy(unsubscribePacketIds::remove, Collectors.toList()));

                                    if (!packetIdToFilters.isEmpty())
                                    {
                                        packetIdToFilters.forEach((unsubscribePacketId, filters) ->
                                            sendUnsuback(unsubscribePacketId, traceId, authorization, filters, false));
                                    }
                                    else
                                    {
                                        sendUnsuback(packetId, traceId, authorization, removedFilters, true);
                                    }
                                }
                            }
                            sessionStream.setSubscriptions(newState);
                        }
                    }

                    doSessionWindow(traceId, encodeSlotOffset, encodeBudgetMax);
                }
            }

            private void onSessionEnd(
                EndFW end)
            {
                final long traceId = end.traceId();
                final long authorization = end.authorization();
                if (!MqttState.initialClosed(state))
                {
                    onDecodeError(traceId, authorization, SESSION_TAKEN_OVER);
                }
                //setReplyClosed();
            }

            private void onSessionAbort(
                AbortFW abort)
            {
                setReplyClosed();

                final long traceId = abort.traceId();

                cleanupAbort(traceId);
            }

            private void doSessionBegin(
                long traceId,
                long affinity,
                Flyweight beginEx)
            {
                if (!MqttState.initialOpening(state))
                {
                    assert state == 0;
                    state = MqttState.openingInitial(state);


                    application = newStream(this::onSession, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, sessionId, affinity, beginEx);

                    doSessionWindow(traceId, 0, 0);
                }
            }

            private void doSessionData(
                long traceId,
                int reserved,
                Flyweight sessionState)
            {
                assert MqttState.initialOpening(state);

                final DirectBuffer buffer = sessionState.buffer();
                final int offset = sessionState.offset();
                final int limit = sessionState.limit();
                final int length = limit - offset;
                assert reserved >= length + initialPad;

                if (!MqttState.closed(state))
                {
                    doData(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, sessionId, budgetId, reserved, buffer, offset, length, EMPTY_OCTETS);

                    initialSeq += reserved;
                    assert initialSeq <= initialAck + initialMax;
                }
            }

            private void cleanupAbort(
                long traceId)
            {
                doSessionAbort(traceId);
                doSessionReset(traceId);
            }

            private void cleanupEnd(
                long traceId)
            {
                doSessionAppEnd(traceId, EMPTY_OCTETS);
            }

            private void doSessionAbort(
                long traceId)
            {
                if (!MqttState.initialClosed(state))
                {
                    setInitialClosed();

                    doAbort(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, sessionId, EMPTY_OCTETS);
                }
            }

            private void doSessionAppEnd(
                long traceId,
                Flyweight extension)
            {
                if (MqttState.initialOpening(state) && !MqttState.initialClosed(state))
                {
                    setReplyClosed();

                    doEnd(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, sessionId, extension);
                    sessionStream = null;
                }
            }

            private void doSessionWindow(
                long traceId,
                int minReplyNoAck,
                int minReplyMax)
            {
                if (MqttState.replyOpened(state))
                {
                    final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

                    if (newReplyAck > replyAck || minReplyMax > replyMax || !MqttState.replyOpened(state))
                    {
                        replyAck = newReplyAck;
                        assert replyAck <= replySeq;

                        replyMax = minReplyMax;

                        doWindow(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                            traceId, sessionId, encodeBudgetId, PUBLISH_FRAMING);
                    }
                }
            }

            private void doSessionReset(
                long traceId)
            {
                if (!MqttState.replyClosed(state))
                {
                    setReplyClosed();

                    doReset(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, sessionId, EMPTY_OCTETS);
                }
            }

            private void setReplyClosed()
            {
                assert !MqttState.replyClosed(state);

                state = MqttState.closeReply(state);

                if (MqttState.closed(state))
                {
                    sessionStream = null;
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
                    sessionStream = null;
                }
            }

            public void setSubscriptions(List<Subscription> subscriptions)
            {
                this.subscriptions = subscriptions;
            }
            public List<Subscription> getSubscriptions()
            {
                return subscriptions;
            }
        }

        private class MqttPublishStream
        {
            private MessageConsumer application;
            private final int topicKey;
            private final String topic;
            private final long originId;
            private final long routedId;
            private final long initialId;
            private final long replyId;
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

            private int state;

            private long publishExpiresId = NO_CANCEL_ID;
            private long publishExpiresAt;

            MqttPublishStream(
                long originId,
                long routedId,
                String topic)
            {
                this.originId = originId;
                this.routedId = routedId;
                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.topic = topic;
                this.topicKey = topicKey(topic);
            }

            private void doPublishBegin(
                long traceId,
                long affinity)
            {
                if (!MqttState.initialOpening(state))
                {
                    assert state == 0;
                    state = MqttState.openingInitial(state);

                    final MqttBeginExFW beginEx = mqttPublishBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(mqttTypeId)
                        .publish(publishBuilder ->
                        {
                            publishBuilder.clientId(clientId);
                            publishBuilder.topic(topic);
                            publishBuilder.flags(retainedMessages);
                        })
                        .build();

                    application = newStream(this::onPublish, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, sessionId, affinity, beginEx);

                    doSignalPublishExpiration();
                    doPublishWindow(traceId, 0, 0);
                }
            }

            private void doPublishData(
                long traceId,
                int reserved,
                OctetsFW payload,
                Flyweight extension)
            {
                assert MqttState.initialOpening(state);

                final DirectBuffer buffer = payload.buffer();
                final int offset = payload.offset();
                final int limit = payload.limit();
                final int length = limit - offset;
                assert reserved >= length + initialPad;

                doData(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, sessionId, budgetId, reserved, buffer, offset, length, extension);

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;

                doSignalPublishExpiration();
            }

            private void doPublishAbort(
                long traceId)
            {
                if (!MqttState.initialClosed(state))
                {
                    setPublishNetClosed();

                    doAbort(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, sessionId, EMPTY_OCTETS);
                }
            }

            private void onPublish(
                int msgTypeId,
                DirectBuffer buffer,
                int index,
                int length)
            {
                switch (msgTypeId)
                {
                case BeginFW.TYPE_ID:
                    final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                    onPublishBegin(begin);
                    break;
                case DataFW.TYPE_ID:
                    final DataFW data = dataRO.wrap(buffer, index, index + length);
                    onPublishData(data);
                    break;
                case EndFW.TYPE_ID:
                    final EndFW end = endRO.wrap(buffer, index, index + length);
                    onPublishEnd(end);
                    break;
                case AbortFW.TYPE_ID:
                    final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                    onPublishAbort(abort);
                    break;
                case WindowFW.TYPE_ID:
                    final WindowFW window = windowRO.wrap(buffer, index, index + length);
                    onPublishWindow(window);
                    break;
                case ResetFW.TYPE_ID:
                    final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                    onPublishReset(reset);
                    break;
                case SignalFW.TYPE_ID:
                    final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                    onPublishSignal(signal);
                    break;
                }
            }

            private void onPublishBegin(
                BeginFW begin)
            {
                state = MqttState.openingReply(state);

                final long traceId = begin.traceId();

                doPublishWindow(traceId, encodeSlotOffset, encodeBudgetMax);
            }

            private void onPublishData(
                DataFW data)
            {
                final long sequence = data.sequence();
                final long acknowledge = data.acknowledge();
                final long traceId = data.traceId();
                final int reserved = data.reserved();
                final long authorization = data.authorization();

                assert acknowledge <= sequence;
                assert sequence >= replySeq;
                assert acknowledge <= replyAck;

                replySeq = sequence + reserved;
                encodeSharedBudget -= reserved;

                assert replyAck <= replySeq;

                if (replySeq > replyAck + replyMax)
                {
                    doPublishReset(traceId);
                    doNetworkAbort(traceId, authorization);
                }
                else
                {
                    droppedHandler.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
                    doPublishWindow(traceId, encodeSlotOffset, encodeBudgetMax);
                }
            }

            private void onPublishEnd(
                EndFW end)
            {
                setPublishAppClosed();
            }

            private void onPublishAbort(
                AbortFW abort)
            {
                setPublishAppClosed();

                final long traceId = abort.traceId();

                cleanupAbort(traceId);
            }

            private void onPublishWindow(
                WindowFW window)
            {
                final long sequence = window.sequence();
                final long acknowledge = window.acknowledge();
                final int maximum = window.maximum();
                final long traceId = window.traceId();
                final long authorization = window.authorization();
                final long budgetId = window.budgetId();
                final int padding = window.padding();

                this.state = MqttState.openInitial(state);
                this.budgetId = budgetId;

                assert acknowledge <= sequence;
                assert sequence <= initialSeq;
                assert acknowledge >= initialAck;
                assert maximum + acknowledge >= initialMax + initialAck;

                initialAck = acknowledge;
                initialMax = maximum;
                initialPad = padding;

                assert initialAck <= initialSeq;

                if (budgetId != 0L && debitorIndex == NO_DEBITOR_INDEX)
                {
                    debitor = supplyDebitor.apply(budgetId);
                    debitorIndex = debitor.acquire(budgetId, initialId, MqttServer.this::decodeNetwork);
                }

                if (MqttState.initialClosing(state))
                {
                    doPublishAppEnd(traceId);
                }
                else if (decodePublisherKey == topicKey)
                {
                    decodeNetwork(traceId);
                }
            }

            private void onPublishReset(
                ResetFW reset)
            {
                setPublishNetClosed();

                final long traceId = reset.traceId();
                final long authorization = reset.authorization();

                if (!MqttState.initialOpened(state))
                {
                    onDecodeError(traceId, authorization, TOPIC_NAME_INVALID);
                    decoder = decodeIgnoreAll;
                }

                decodeNetwork(traceId);
                cleanupAbort(traceId);
            }

            private void onPublishSignal(
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

                final long now = System.currentTimeMillis();
                if (now >= publishExpiresAt)
                {
                    doPublishAppEnd(traceId);
                }
                else
                {
                    publishExpiresId = NO_CANCEL_ID;
                    doSignalPublishExpiration();
                }
            }

            private void doSignalPublishExpiration()
            {
                publishExpiresAt = System.currentTimeMillis() + publishTimeoutMillis;

                if (publishExpiresId == NO_CANCEL_ID)
                {
                    publishExpiresId =
                        signaler.signalAt(publishExpiresAt, originId, routedId, initialId, PUBLISH_EXPIRED_SIGNAL, 0);
                }
            }

            private void doCancelPublishExpiration()
            {
                if (publishExpiresId != NO_CANCEL_ID)
                {
                    signaler.cancel(publishExpiresId);
                    publishExpiresId = NO_CANCEL_ID;
                }
            }

            private void doPublishWindow(
                long traceId,
                int minReplyNoAck,
                int minReplyMax)
            {
                if (MqttState.replyOpening(state))
                {
                    final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

                    if (newReplyAck > replyAck || minReplyMax > replyMax || !MqttState.replyOpened(state))
                    {
                        replyAck = newReplyAck;
                        assert replyAck <= replySeq;

                        replyMax = minReplyMax;

                        state = MqttState.openReply(state);

                        doWindow(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                            traceId, sessionId, encodeBudgetId, PUBLISH_FRAMING);
                    }
                }
            }

            private void doPublishReset(
                long traceId)
            {
                if (!MqttState.replyClosed(state))
                {
                    setPublishAppClosed();

                    doReset(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, sessionId, EMPTY_OCTETS);
                }
            }

            private void doPublishAppEnd(
                long traceId)
            {
                if (!MqttState.initialClosed(state))
                {
                    doCancelPublishExpiration();
                    doEnd(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, sessionId, EMPTY_OCTETS);
                }
            }

            private void setPublishNetClosed()
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
                    publishStreams.remove(topicKey);
                }
            }


            private void setPublishAppClosed()
            {
                assert !MqttState.replyClosed(state);

                state = MqttState.closeReply(state);

                if (MqttState.closed(state))
                {
                    publishStreams.remove(topicKey);
                }
            }

            private void cleanupAbort(
                long traceId)
            {
                doPublishAbort(traceId);
                doPublishReset(traceId);
                doCancelPublishExpiration();
            }
        }

        private class MqttSubscribeStream
        {
            private MessageConsumer application;
            private final long originId;
            private final long routedId;
            private final long initialId;
            private final long replyId;
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

            private int state;
            private final List<Subscription> subscriptions;
            private boolean acknowledged;
            private int clientKey;
            private int packetId;
            private final boolean adminSubscribe;

            MqttSubscribeStream(
                long originId,
                long routedId,
                boolean adminSubscribe)
            {
                this.originId = originId;
                this.routedId = routedId;
                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.subscriptions = new ArrayList<>();
                this.adminSubscribe = adminSubscribe;
            }

            private Optional<Subscription> getSubscriptionByFilter(String filter)
            {
                return subscriptions.stream().filter(s -> s.filter.equals(filter)).findFirst();
            }

            private void doSubscribeBeginOrFlush(
                long traceId,
                long affinity,
                int clientKey,
                List<Subscription> subscriptions)
            {
                this.subscriptions.addAll(subscriptions);
                this.clientKey = clientKey;

                if (!MqttState.initialOpening(state))
                {
                    doSubscribeBegin(traceId, affinity);
                }
                else
                {
                    doSubscribeFlush(traceId, 0, subscriptions);
                }
            }

            private void doSubscribeBegin(
                long traceId,
                long affinity)
            {
                assert state == 0;
                state = MqttState.openingInitial(state);

                final MqttBeginExFW beginEx = mqttSubscribeBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mqttTypeId)
                    .subscribe(subscribeBuilder ->
                    {
                        subscribeBuilder.clientId(clientId);
                        subscriptions.forEach(subscription ->
                            subscribeBuilder.filtersItem(filterBuilder ->
                            {
                                filterBuilder.subscriptionId(subscription.id);
                                filterBuilder.flags(subscription.flags);
                                filterBuilder.pattern(subscription.filter);
                            })
                        );
                    })
                    .build();

                application = newStream(this::onSubscribe, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, sessionId, affinity, beginEx);

                doSubscribeWindow(traceId, 0, 0);
            }

            private void doSubscribeFlush(
                long traceId,
                int reserved,
                List<Subscription> newSubscriptions)
            {
                doFlush(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, sessionId, 0L, reserved,
                    ex -> ex.set((b, o, l) -> mqttFlushExRW.wrap(b, o, l)
                        .typeId(mqttTypeId)
                        .subscribe(subscribeBuilder ->
                            subscribeBuilder.filters(builder ->
                                subscriptions.forEach(subscription ->
                                    builder.item(filterBuilder ->
                                    {
                                        filterBuilder.subscriptionId(subscription.id);
                                        filterBuilder.flags(subscription.flags);
                                        filterBuilder.pattern(subscription.filter);
                                    }))))
                        .build()
                        .sizeof()));

                if (newSubscriptions != null && !newSubscriptions.isEmpty())
                {
                    // TODO: do we get back anything after we send a flush?
                    //  Should we say it's a success right after we sent the flush?
                    final byte[] subscriptionPayload = new byte[newSubscriptions.size()];
                    for (int i = 0; i < subscriptionPayload.length; i++)
                    {
                        subscriptionPayload[i] = SUCCESS;
                    }

                    doEncodeSuback(traceId, sessionId, packetId, subscriptionPayload);
                }

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;
            }

            private void doSubscribeFlushOrEnd(
                long traceId,
                List<String> unsubscribedPatterns)
            {
                this.subscriptions.removeIf(subscription -> unsubscribedPatterns.contains(subscription.filter));
                if (!MqttState.initialOpened(state))
                {
                    state = MqttState.closingInitial(state);
                }
                else
                {
                    if (subscriptions.isEmpty())
                    {
                        doSubscribeAppEnd(traceId);
                    }
                    else
                    {
                        doSubscribeFlush(traceId, 0, null);
                    }
                }
            }

            private void doSubscribeAbort(
                long traceId)
            {
                if (!MqttState.initialClosed(state))
                {
                    setNetClosed();

                    doAbort(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, sessionId, EMPTY_OCTETS);
                }
            }

            private void setNetClosed()
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
                    subscribeStreams.remove(clientKey);
                }
            }

            private void onSubscribe(
                int msgTypeId,
                DirectBuffer buffer,
                int index,
                int length)
            {
                switch (msgTypeId)
                {
                case BeginFW.TYPE_ID:
                    final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                    onSubscribeBegin(begin);
                    break;
                case DataFW.TYPE_ID:
                    final DataFW data = dataRO.wrap(buffer, index, index + length);
                    onSubscribeData(data);
                    break;
                case EndFW.TYPE_ID:
                    final EndFW end = endRO.wrap(buffer, index, index + length);
                    onSubscribeEnd(end);
                    break;
                case AbortFW.TYPE_ID:
                    final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                    onSubscribeAbort(abort);
                    break;
                case WindowFW.TYPE_ID:
                    final WindowFW window = windowRO.wrap(buffer, index, index + length);
                    onSubscribeWindow(window);
                    break;
                case ResetFW.TYPE_ID:
                    final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                    onSubscribeReset(reset);
                    break;
                }
            }

            private void onSubscribeBegin(
                BeginFW begin)
            {
                state = MqttState.openingReply(state);

                final long traceId = begin.traceId();
                final long authorization = begin.authorization();

                if (!acknowledged)
                {
                    doSubscribeWindow(traceId, encodeSlotOffset, encodeBudgetMax);
                    acknowledged = true;
                }
                else
                {
                    doSubscribeWindow(traceId, 0, replyMax);
                }

            }

            private void onSubscribeData(
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
                    doSubscribeReset(traceId);
                    doNetworkAbort(traceId, authorization);
                }
                else
                {
                    if (payload != null && !subscriptions.isEmpty() && payload.sizeof() <= maximumPacketSize)
                    {
                        doEncodePublish(traceId, authorization, flags, subscriptions, payload, extension);
                    }
                    else
                    {
                        droppedHandler.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
                    }
                    doSubscribeWindow(traceId, encodeSlotOffset, encodeBudgetMax);
                }
            }


            private void onSubscribeReset(
                ResetFW reset)
            {
                setNetClosed();

                final long traceId = reset.traceId();
                final long authorization = reset.authorization();

                //TODO: can this happen that the server sends a reset, before suback?
                //                if (!MqttState.initialOpened(state))
                //                {
                //                    subscription.onSubscribeFailed(traceId, authorization, packetId, subackIndex);
                //                }

                decodeNetwork(traceId);
                cleanupAbort(traceId);
            }

            private void onSubscribeWindow(
                WindowFW window)
            {
                final long sequence = window.sequence();
                final long acknowledge = window.acknowledge();
                final int maximum = window.maximum();
                final long traceId = window.traceId();
                final long authorization = window.authorization();
                final long budgetId = window.budgetId();
                final int padding = window.padding();

                if (!subscriptions.isEmpty() && !adminSubscribe)
                {
                    final byte[] subscriptionPayload = new byte[subscriptions.size()];
                    //TODO: if we get back the window, can it be anything else than success? I think yes, and we only need to
                    // recognize reject scenarios when doing the decodeSubscribe
                    for (int i = 0; i < subscriptionPayload.length; i++)
                    {
                        subscriptionPayload[i] = SUCCESS;
                    }

                    if (!MqttState.initialOpened(state))
                    {
                        doEncodeSuback(traceId, authorization, packetId, subscriptionPayload);
                    }
                    if (session && !sessionStream.deferredUnsubscribes.isEmpty())
                    {
                        Iterator<Map.Entry<Integer, List<String>>> iterator =
                            sessionStream.deferredUnsubscribes.entrySet().iterator();
                        List<String> ackedTopicFilters = new ArrayList<>();
                        while (iterator.hasNext())
                        {
                            Map.Entry<Integer, List<String>> entry = iterator.next();
                            int unsubscribePacketId = entry.getKey();
                            List<String> deferredTopicFilters = entry.getValue();
                            ackedTopicFilters.addAll(subscriptions.stream()
                                .filter(s -> deferredTopicFilters.contains(s.filter))
                                .peek(s -> unsubscribePacketIds.put(s.filter, unsubscribePacketId))
                                .map(s -> s.filter)
                                .collect(Collectors.toList()));
                            iterator.remove();
                        }

                        sendNewSessionStateForUnsubscribe(traceId, authorization, ackedTopicFilters);
                    }
                }

                this.state = MqttState.openInitial(state);
                this.budgetId = budgetId;

                assert acknowledge <= sequence;
                assert sequence <= initialSeq;
                assert acknowledge >= initialAck;
                assert maximum + acknowledge >= initialMax + initialAck;

                initialAck = acknowledge;
                initialMax = maximum;
                initialPad = padding;

                assert initialAck <= initialSeq;

                if (budgetId != 0L && debitorIndex == NO_DEBITOR_INDEX)
                {
                    debitor = supplyDebitor.apply(budgetId);
                    debitorIndex = debitor.acquire(budgetId, initialId, MqttServer.this::decodeNetwork);
                }

                if (MqttState.initialClosing(state) &&
                    !MqttState.initialClosed(state))
                {
                    doSubscribeAppEnd(traceId);
                }
            }

            private void onSubscribeEnd(
                EndFW end)
            {
                assert !MqttState.replyClosed(state);

                state = MqttState.closeReply(state);
                setSubscribeAppClosed();
            }

            private void onSubscribeAbort(
                AbortFW abort)
            {
                assert !MqttState.replyClosed(state);

                state = MqttState.closeReply(state);
                setSubscribeAppClosed();

                final long traceId = abort.traceId();
                final long authorization = abort.authorization();

                cleanupAbort(traceId);
            }

            private void cleanupAbort(
                long traceId)
            {
                doSubscribeAbort(traceId);
                doSubscribeReset(traceId);
            }


            private void doSubscribeWindow(
                long traceId,
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

                    doWindow(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, sessionId, encodeBudgetId, PUBLISH_FRAMING);
                }
            }

            private void doSubscribeReset(
                long traceId)
            {
                if (!MqttState.replyClosed(state))
                {
                    assert !MqttState.replyClosed(state);

                    state = MqttState.closeReply(state);
                    setSubscribeAppClosed();

                    doReset(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, sessionId, EMPTY_OCTETS);
                }
            }

            private void doSubscribeAppEnd(
                long traceId)
            {
                if (MqttState.initialOpening(state) && !MqttState.initialClosed(state))
                {
                    doEnd(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, sessionId, EMPTY_OCTETS);
                }
            }


            private void setSubscribeAppClosed()
            {
                if (MqttState.closed(state))
                {
                    subscribeStreams.remove(clientKey);
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

    private static int decodeWillQos(
        int flags)
    {
        int willQos = 0;
        if (isSetWillQos(flags))
        {
            //TODO shift by 3?
            willQos = (flags & WILL_QOS_MASK) >>> 2;
        }
        return willQos;
    }

    private static int decodeWillFlags(
        int flags)
    {
        int willFlags = 0;

        if (isSetWillRetain(flags))
        {
            willFlags |= RETAIN_FLAG;
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

    private static boolean isCleanStart(
        int flags)
    {
        return (flags & CLEAN_START_FLAG_MASK) != 0;
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

    private static boolean isSetTopicAliasMaximum(
        int flags)
    {
        return (flags & CONNECT_TOPIC_ALIAS_MAXIMUM_MASK) != 0;
    }

    private static boolean isSetMaximumPacketSize(
        int flags)
    {
        return (flags & CONNECT_MAXIMUM_PACKET_SIZE_MASK) != 0;
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
        private MqttPropertiesFW willProperties;
        private byte willQos;
        private byte willRetain;
        private String16FW willTopic;
        private BinaryFW willPayload;
        private String16FW username;
        private BinaryFW password;

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

        private int decode(
            DirectBuffer buffer,
            int offset,
            int limit,
            int flags)
        {
            int progress = offset;
            decode:
            {
                if (isSetWillFlag(flags))
                {
                    final MqttWillFW mqttWill = mqttWillRO.tryWrap(buffer, offset, limit);
                    if (mqttWill == null)
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    final byte qos = (byte) ((flags & WILL_QOS_MASK) >>> 3);
                    if (qos != 0 && qos <= maximumQos)
                    {
                        willQos = (byte) (qos << 1);
                    }

                    if (isSetWillRetain(flags))
                    {
                        if (retainedMessages == 0)
                        {
                            reasonCode = RETAIN_NOT_SUPPORTED;
                            break decode;
                        }
                        willRetain = (byte) RETAIN_FLAG;
                    }

                    willProperties = mqttWill.properties();
                    decode(willProperties);

                    willTopic = mqttWill.topic();
                    if (willTopic == null || willTopic.asString().isEmpty())
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }

                    willPayload = mqttWill.payload();
                    if (willPayload == null || willPayload.bytes().sizeof() == 0)
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    progress = mqttWill.limit();
                }

                if (isSetUsername(flags))
                {
                    username = usernameRO.tryWrap(buffer, progress, limit);
                    if (username == null || username.sizeof() == 0)
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
            return progress;
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
        private int flags;
        private int expiryInterval = DEFAULT_EXPIRY_INTERVAL;
        private String16FW contentType = NULL_STRING;
        private MqttPayloadFormat payloadFormat = DEFAULT_FORMAT;
        private String16FW responseTopic = NULL_STRING;
        private OctetsFW correlationData = null;
        private int qos;
        private boolean retained = false;

        private MqttPublishHeader reset()
        {
            this.topic = null;
            this.flags = 0;
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
            MqttPropertiesFW properties,
            int typeAndFlags)
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
                flags = calculatePublishApplicationFlags(typeAndFlags);
                qos = calculatePublishApplicationQos(typeAndFlags);

                int alias = 0;
                if (qos > maximumQos)
                {
                    reasonCode = QOS_NOT_SUPPORTED;
                }
                else if (retained && retainedMessages == 0)
                {
                    reasonCode = RETAIN_NOT_SUPPORTED;
                }
                else
                {
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
            }

            return reasonCode;
        }
        private int calculatePublishApplicationQos(
            int networkTypeAndFlags)
        {
            int qos = 0;
            if ((networkTypeAndFlags & PUBLISH_QOS1_MASK) != 0)
            {
                qos = 1;
            }
            else if ((networkTypeAndFlags & PUBLISH_QOS2_MASK) != 0)
            {
                qos = 2;
            }
            return qos;
        }

        private int calculatePublishApplicationFlags(
            int networkTypeAndFlags)
        {
            int flags = 0;

            if ((networkTypeAndFlags & RETAIN_MASK) != 0)
            {
                flags |= RETAIN_FLAG;
                retained = true;
            }
            return flags;
        }
    }

    private GuardHandler resolveGuard(
        MqttOptionsConfig options,
        ToLongFunction<String> resolveId)
    {
        GuardHandler guard = null;

        if (options != null &&
            options.authorization != null)
        {
            long guardId = resolveId.applyAsLong(options.authorization.name);
            guard = supplyGuard.apply(guardId);
        }

        return guard;
    }
}

