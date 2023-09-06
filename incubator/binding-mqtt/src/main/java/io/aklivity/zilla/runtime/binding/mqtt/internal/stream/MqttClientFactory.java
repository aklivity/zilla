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

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.DISCONNECT_WITH_WILL_MESSAGE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.KEEP_ALIVE_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.MALFORMED_PACKET;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.NORMAL_DISCONNECT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.NO_SUBSCRIPTION_EXISTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.PACKET_TOO_LARGE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.PAYLOAD_FORMAT_INVALID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.PROTOCOL_ERROR;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.QOS_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.RETAIN_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SERVER_MOVED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SESSION_TAKEN_OVER;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SHARED_SUBSCRIPTION_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SUBSCRIPTION_IDS_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SUCCESS;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.TOPIC_NAME_INVALID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.UNSUPPORTED_PROTOCOL_VERSION;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPublishFlags.RETAIN;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSubscribeFlags.NO_LOCAL;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSubscribeFlags.RETAIN_AS_PUBLISHED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSubscribeFlags.SEND_RETAINED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_CONTENT_TYPE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_CORRELATION_DATA;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_EXPIRY_INTERVAL;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_PAYLOAD_FORMAT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_RESPONSE_TOPIC;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_SESSION_EXPIRY;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_SUBSCRIPTION_ID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_TOPIC_ALIAS;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttBindingConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttRouteConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttBinaryFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttCapabilities;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttQoS;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSessionStateFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttTopicFilterFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttWillMessageFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Varuint32FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.BinaryFW;
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
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttPublishDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttResetExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttSessionBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttSessionDataKind;
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

public final class MqttClientFactory implements MqttStreamFactory
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
    //TODO: should these come from config?
    private static final short TOPIC_ALIAS_MAXIMUM = 0;
    private static final int KEEP_ALIVE = 60;

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


    private final ExtensionFW extensionRO = new ExtensionFW();
    private final MqttPublishDataExFW mqttPublishDataExRO = new MqttPublishDataExFW();
    private final MqttDataExFW mqttSubscribeDataExRO = new MqttDataExFW();
    private final MqttResetExFW mqttResetExRO = new MqttResetExFW();
    private final MqttBeginExFW mqttBeginExRO = new MqttBeginExFW();

    private final MqttBeginExFW.Builder mqttPublishBeginExRW = new MqttBeginExFW.Builder();
    private final MqttBeginExFW.Builder mqttSubscribeBeginExRW = new MqttBeginExFW.Builder();
    private final MqttBeginExFW.Builder mqttSessionBeginExRW = new MqttBeginExFW.Builder();
    private final MqttDataExFW.Builder mqttPublishDataExRW = new MqttDataExFW.Builder();
    private final MqttDataExFW.Builder mqttSubscribeDataExRW = new MqttDataExFW.Builder();
    private final MqttDataExFW.Builder mqttSessionDataExRW = new MqttDataExFW.Builder();
    private final MqttFlushExFW.Builder mqttFlushExRW = new MqttFlushExFW.Builder();
    private final MqttWillMessageFW.Builder mqttWillMessageRW = new MqttWillMessageFW.Builder();
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

    private final MqttConnectFW.Builder mqttConnectRW = new MqttConnectFW.Builder();
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

    private final MqttClientDecoder decodeInitialType = this::decodeInitialType;
    private final MqttClientDecoder decodePacketType = this::decodePacketType;
    private final MqttClientDecoder decodeConnack = this::decodeConnack;
    //private final MqttClientDecoder decodeConnectPayload = this::decodeConnectPayload;
    private final MqttClientDecoder decodePublish = this::decodePublish;
    private final MqttClientDecoder decodeSubscribe = this::decodeSubscribe;
    private final MqttClientDecoder decodeUnsubscribe = this::decodeUnsubscribe;
    private final MqttClientDecoder decodePingreq = this::decodePingreq;
    private final MqttClientDecoder decodeDisconnect = this::decodeDisconnect;
    private final MqttClientDecoder decodeIgnoreAll = this::decodeIgnoreAll;
    private final MqttClientDecoder decodeUnknownType = this::decodeUnknownType;

    private final Map<MqttPacketType, MqttClientDecoder> decodersByPacketType;
    private final boolean session;
    private final String serverRef;
    private final Int2ObjectHashMap<MqttClient> clients;

    private int maximumPacketSize;

    {
        final Map<MqttPacketType, MqttClientDecoder> decodersByPacketType = new EnumMap<>(MqttPacketType.class);
        decodersByPacketType.put(MqttPacketType.CONNECT, decodeConnack);
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


    public MqttClientFactory(
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
        this.keepAliveMinimum = config.keepAliveMinimum();
        this.keepAliveMaximum = config.keepAliveMaximum();
        this.maximumQos = config.maximumQos();
        this.maximumPacketSize = writeBuffer.capacity();
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
        this.serverRef = config.serverReference();
        this.clients = new Int2ObjectHashMap<>();
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
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();

        MqttBindingConfig binding = bindings.get(routedId);

        final MqttRouteConfig resolved = binding != null ? binding.resolve(authorization) : null;

        MessageConsumer newStream = null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;

            final OctetsFW extension = begin.extension();
            final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
            assert beginEx != null;
            final int typeId = beginEx.typeId();
            assert typeId == mqttTypeId;


            final MqttBeginExFW mqttBeginEx = extension.get(mqttBeginExRO::tryWrap);
            String16FW clientId;

            switch (mqttBeginEx.kind())
            {
            case MqttBeginExFW.KIND_SESSION:
                clientId = mqttBeginEx.session().clientId();
                //TODO: create resolveClient?
                final int clientKey = clientKey(clientId.asString());
                //TODO: do we need any Id in the MqttClient? We already store them in the streams, and they'll be different
                // for each individual stream
                final MqttClient client = clients.computeIfAbsent(clientKey,
                    s -> new MqttClient(routedId, resolvedId, initialId));
                client.sessionStream = new MqttSessionStream(client, sender, originId, routedId);
                newStream = client.sessionStream::onSession;
                break;
            case MqttBeginExFW.KIND_PUBLISH:
                clientId = mqttBeginEx.publish().clientId();
                break;
            case MqttBeginExFW.KIND_SUBSCRIBE:
                clientId = mqttBeginEx.subscribe().clientId();
                break;
            }
        }

        return newStream;
    }

    private int clientKey(
        String topic)
    {
        return System.identityHashCode(topic.intern());
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
        MqttClient server,
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

            server.decodeablePacketBytes = packet.sizeof() + length;
            server.decoder = decodePacketType;
        }

        return offset;
    }

    private int decodePacketType(
        MqttClient server,
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
            final MqttClientDecoder decoder = decodersByPacketType.getOrDefault(packetType, decodeUnknownType);

            if (packet.sizeof() + length > maximumPacketSize)
            {
                server.onDecodeError(traceId, authorization, PACKET_TOO_LARGE);
                server.decoder = decodeIgnoreAll;
            }
            else if (limit - packet.limit() >= length)
            {
                server.decodeablePacketBytes = packet.sizeof() + length;
                server.decoder = decoder;
            }
        }

        return offset;
    }

    private int decodeConnack(
        MqttClient client,
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
            int flags = 0;
            decode:
            {
                if (mqttConnect == null)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }

                client.decodableRemainingBytes = mqttConnect.remainingLength();
                flags = mqttConnect.flags();

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

                //progress = client.onDecodeConack(traceId, authorization, buffer, progress, limit, mqttConnect);

                final int decodedLength = progress - offset - 2;
                client.decodableRemainingBytes -= decodedLength;
            }

            if (reasonCode != SUCCESS)
            {
                client.onDecodeError(traceId, authorization, reasonCode);
                client.decoder = decodeIgnoreAll;
            }
            else
            {
                //client.decoder = decodeConnectPayload;
            }
        }

        return progress;
    }

    private int decodePublish(
        MqttClient server,
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
                MqttClient.MqttPublishStream publisher = server.publishStreams.get(topicKey);

                if (publisher == null)
                {
                    //publisher = server.resolvePublishStream(traceId, authorization, topic);
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
        MqttClient server,
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
        MqttClient server,
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
        MqttClient server,
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
        MqttClient server,
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
        MqttClient server,
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
        MqttClient server,
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
    private interface MqttClientDecoder
    {
        int decode(
            MqttClient client,
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit);
    }

    private final class MqttClient
    {
        //TODO: organize final and non-final fields
        private MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long replyId;
        private final long initialId;
        private long budgetId;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private final Int2ObjectHashMap<MqttPublishStream> publishStreams;
        private final Int2ObjectHashMap<MqttSubscribeStream> subscribeStreams;
        private final Int2ObjectHashMap<String> topicAliases;
        private final Int2IntHashMap subscribePacketIds;
        private final Object2IntHashMap<String> unsubscribePacketIds;
        private final long encodeBudgetId;

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

        private MqttClientDecoder decoder;
        private int decodePublisherKey;
        private int decodeablePacketBytes;

        private long connectTimeoutId = NO_CANCEL_ID;
        private long connectTimeoutAt;

        private long keepAliveTimeoutId = NO_CANCEL_ID;
        private long keepAliveTimeoutAt;

        private boolean serverDefinedKeepAlive = false;
        private short keepAlive;
        private long keepAliveTimeout;
        private int connectFlags;
        private boolean connected;

        private int sessionExpiry;
        private boolean assignedClientId = false;
        private int decodablePropertyMask = 0;
        private int decodableRemainingBytes;

        private MqttClient(
            long originId,
            long routedId,
            long initialId)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.encodeBudgetId = supplyBudgetId.getAsLong();
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.decoder = decodeInitialType;
            this.publishStreams = new Int2ObjectHashMap<>();
            this.subscribeStreams = new Int2ObjectHashMap<>();
            this.topicAliases = new Int2ObjectHashMap<>();
            this.subscribePacketIds = new Int2IntHashMap(-1);
            this.unsubscribePacketIds = new Object2IntHashMap<>(-1);
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
            final long affinity = begin.affinity();

            state = MqttState.openingInitial(state);

            sessionStream.doSessionBegin(traceId, authorization, affinity);
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

                cleanupStreamsUsingAbort(traceId, authorization);

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
                    sessionStream.doSessionAbort(traceId, authorization);
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
                cleanupStreamsUsingAbort(traceId, authorization);
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
                stream.doPublishData(traceId, authorization, reserved, payload, dataEx);
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
                    final MqttDataExFW.Builder sessionDataExBuilder =
                        mqttSessionDataExRW.wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                            .typeId(mqttTypeId)
                            .session(sessionBuilder -> sessionBuilder.kind(k -> k.set(MqttSessionDataKind.STATE)));

                    final MqttSessionStateFW.Builder state =
                        mqttSessionStateFW.wrap(sessionStateBuffer, 0, sessionStateBuffer.capacity());

                    sessionStream.unAckedSubscriptions.addAll(newSubscriptions);
                    sessionStream.subscriptions.forEach(sub ->
                        state.subscriptionsItem(subscriptionBuilder ->
                            subscriptionBuilder
                                .subscriptionId(sub.id)
                                .flags(sub.flags)
                                .pattern(sub.filter))
                    );

                    newSubscriptions.forEach(sub ->
                        state.subscriptionsItem(subscriptionBuilder ->
                            subscriptionBuilder
                                .subscriptionId(sub.id)
                                .flags(sub.flags)
                                .pattern(sub.filter))
                    );

                    final MqttSessionStateFW sessionState = state.build();
                    final int payloadSize = sessionState.sizeof();

                    sessionStream.doSessionData(traceId, authorization, payloadSize, sessionDataExBuilder.build(), sessionState);
                }
                else
                {
                    //openSubscribeStreams(packetId, traceId, authorization, newSubscriptions, false);
                }
            }
            doSignalKeepAliveTimeout();
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
                    doSendSessionState(traceId, authorization, topicFilters);
                }
                else
                {
                    sendUnsuback(packetId, traceId, authorization, topicFilters, false);
                }
                doSignalKeepAliveTimeout();
            }
        }

        private void doSendSessionState(
            long traceId,
            long authorization,
            List<String> topicFilters)
        {
            final MqttDataExFW.Builder sessionDataExBuilder =
                mqttSessionDataExRW.wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                    .typeId(mqttTypeId)
                    .session(sessionBuilder -> sessionBuilder.kind(k -> k.set(MqttSessionDataKind.STATE)));

            List<Subscription> currentState = sessionStream.subscriptions();
            List<Subscription> newState = currentState.stream()
                .filter(subscription -> !topicFilters.contains(subscription.filter))
                .collect(Collectors.toList());

            final MqttSessionStateFW.Builder sessionStateBuilder =
                mqttSessionStateFW.wrap(sessionStateBuffer, 0, sessionStateBuffer.capacity());

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

            sessionStream.doSessionData(traceId, authorization, payloadSize, sessionDataExBuilder.build(), sessionState);
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
                    binding != null ? binding.resolve(authorization, topicFilter, MqttCapabilities.SUBSCRIBE_ONLY) : null;
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

            //filtersByStream.forEach(
            //   (stream, filters) -> stream.doSubscribeFlushOrEnd(traceId, authorization, filters));
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
            byte reasonCode = decodeDisconnectProperties(disconnect.properties());

            if (reasonCode != SUCCESS)
            {
                onDecodeError(traceId, authorization, reasonCode);
                decoder = decodeIgnoreAll;
            }
            else
            {
                if (session)
                {
                    if (disconnect.reasonCode() == DISCONNECT_WITH_WILL_MESSAGE)
                    {
                        sessionStream.doSessionAbort(traceId, authorization);
                    }
                    else
                    {
                        sessionStream.doSessionAppEnd(traceId, authorization, EMPTY_OCTETS);
                    }
                }
            }

            state = MqttState.closingInitial(state);
            closeStreams(traceId, authorization);
            doNetworkEnd(traceId, authorization);
        }

        private byte decodeDisconnectProperties(
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
                case KIND_SESSION_EXPIRY:
                    if (isSetSessionExpiryInterval(decodablePropertyMask))
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.decodablePropertyMask |= CONNECT_SESSION_EXPIRY_INTERVAL_MASK;
                    final int sessionExpiryInterval = (int) mqttProperty.sessionExpiry();
                    if (sessionExpiryInterval > 0 && this.sessionExpiry == 0)
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    break;
                default:
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                decodeProgress = mqttProperty.limit();
            }

            return reasonCode;
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
                cleanupStreamsUsingAbort(traceId, authorization);
                break;
            }
            if (connected)
            {
                doEncodeDisconnect(traceId, authorization, reasonCode, null);
            }
            else
            {
                //doEncodeConnack(traceId, authorization, reasonCode, false, false, null);
            }

            doNetworkEnd(traceId, authorization);
        }

        private void doNetworkBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            state = MqttState.openingReply(state);

            if (!MqttState.initialOpening(state))
            {
                state = MqttState.openingInitial(state);

                network = newStream(this::onNetwork, originId, routedId, initialId, initialSeq, initialAck,
                    initialMax, traceId, authorization, affinity, EMPTY_OCTETS);
            }
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
            if (!MqttState.initialClosed(state))
            {
                state = MqttState.closeInitial(state);

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

        private void doEncodeConnect(
            long traceId,
            long authorization,
            String16FW clientId,
            int flags,
            int sessionExpiry)
        {
            int propertiesSize = 0;

            MqttPropertyFW mqttProperty;

            //TODO: remove this once we support large messages
            mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                .maximumPacketSize(maximumPacketSize)
                .build();
            propertiesSize = mqttProperty.limit();

            if (sessionExpiry != 0)
            {
                mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                    .sessionExpiry(sessionExpiry)
                    .build();
                propertiesSize = mqttProperty.limit();
            }

            if (TOPIC_ALIAS_MAXIMUM > 0)
            {
                mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                    .topicAliasMaximum(TOPIC_ALIAS_MAXIMUM)
                    .build();
                propertiesSize = mqttProperty.limit();
            }

            final int propertiesSize0 = propertiesSize;
            final MqttConnectFW connect =
                mqttConnectRW.wrap(writeBuffer, FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                    .typeAndFlags(0x10)
                    .remainingLength(11 + propertiesSize0) // TODO: create constant from fixed length?
                    .protocolName(MQTT_PROTOCOL_NAME)
                    .protocolVersion(MQTT_PROTOCOL_VERSION)
                    .flags(flags)
                    .keepAlive(KEEP_ALIVE)
                    .properties(p -> p.length(propertiesSize0)
                        .value(propertyBuffer, 0, propertiesSize0))
                    .clientId(clientId)
                    .build();

            doNetworkData(traceId, authorization, 0L, connect);
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
            MqttClientDecoder previous = null;
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
                    cleanupStreamsUsingAbort(traceId, authorization);
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
            cleanupStreamsUsingAbort(traceId, authorization);

            doNetworkReset(traceId, authorization);
            doNetworkAbort(traceId, authorization);
        }

        private void cleanupStreamsUsingAbort(
            long traceId,
            long authorization)
        {
            publishStreams.values().forEach(s -> s.cleanupAbort(traceId, authorization));
            subscribeStreams.values().forEach(s -> s.cleanupAbort(traceId, authorization));
            if (sessionStream != null)
            {
                sessionStream.cleanupAbort(traceId, authorization);
            }
        }

        private void closeStreams(
            long traceId,
            long authorization)
        {
            publishStreams.values().forEach(s -> s.doPublishAppEnd(traceId, authorization));
            subscribeStreams.values().forEach(s -> s.doSubscribeAppEnd(traceId, authorization));
            if (sessionStream != null)
            {
                sessionStream.cleanupEnd(traceId, authorization);
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
                long authorization,
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
                        traceId, authorization, affinity, beginEx);

                    doSignalPublishExpiration();
                    doPublishWindow(traceId, authorization, 0, 0);
                }
            }

            private void doPublishData(
                long traceId,
                long authorization,
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
                    traceId, authorization, budgetId, reserved, buffer, offset, length, extension);

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;

                doSignalPublishExpiration();
            }

            private void doPublishAbort(
                long traceId,
                long authorization)
            {
                if (!MqttState.initialClosed(state))
                {
                    setPublishNetClosed();

                    doAbort(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, EMPTY_OCTETS);
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

                //doPublishWindow(traceId, authorization, encodeSlotOffset, encodeBudgetMax);
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
                    doPublishReset(traceId, authorization);
                    doNetworkAbort(traceId, authorization);
                }
                else
                {
                    droppedHandler.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
                    doPublishWindow(traceId, authorization, encodeSlotOffset, encodeBudgetMax);
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
                final long authorization = abort.authorization();

                cleanupAbort(traceId, authorization);
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
                    debitorIndex = debitor.acquire(budgetId, initialId, MqttClient.this::decodeNetwork);
                }

                if (MqttState.initialClosing(state))
                {
                    doPublishAppEnd(traceId, authorization);
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
                cleanupAbort(traceId, authorization);
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
                final long authorization = signal.authorization();

                final long now = System.currentTimeMillis();
                if (now >= publishExpiresAt)
                {
                    doPublishAppEnd(traceId, authorization);
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
                long authorization,
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
                            traceId, authorization, encodeBudgetId, PUBLISH_FRAMING);
                    }
                }
            }

            private void doPublishReset(
                long traceId,
                long authorization)
            {
                if (!MqttState.replyClosed(state))
                {
                    setPublishAppClosed();

                    doReset(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, EMPTY_OCTETS);
                }
            }

            private void doPublishAppEnd(
                long traceId,
                long authorization)
            {
                if (!MqttState.initialClosed(state))
                {
                    doCancelPublishExpiration();
                    doEnd(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, EMPTY_OCTETS);
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
                long traceId,
                long authorization)
            {
                doPublishAbort(traceId, authorization);
                doPublishReset(traceId, authorization);
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

            private void doSubscribeAbort(
                long traceId,
                long authorization)
            {
                if (!MqttState.initialClosed(state))
                {
                    setNetClosed();

                    doAbort(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, EMPTY_OCTETS);
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
                    doSubscribeReset(traceId, authorization);
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
                    doSubscribeWindow(traceId, authorization, encodeSlotOffset, encodeBudgetMax);
                }
            }


            private void onSubscribeReset(
                ResetFW reset)
            {
                setNetClosed();

                final long traceId = reset.traceId();
                final long authorization = reset.authorization();

                decodeNetwork(traceId);
                cleanupAbort(traceId, authorization);
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

                        doSendSessionState(traceId, authorization, ackedTopicFilters);
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
                    debitorIndex = debitor.acquire(budgetId, initialId, MqttClient.this::decodeNetwork);
                }

                if (MqttState.initialClosing(state) &&
                    !MqttState.initialClosed(state))
                {
                    doSubscribeAppEnd(traceId, authorization);
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

                cleanupAbort(traceId, authorization);
            }

            private void cleanupAbort(
                long traceId,
                long authorization)
            {
                doSubscribeAbort(traceId, authorization);
                doSubscribeReset(traceId, authorization);
            }


            private void doSubscribeWindow(
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

                    doWindow(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, encodeBudgetId, PUBLISH_FRAMING);
                }
            }

            private void doSubscribeReset(
                long traceId,
                long authorization)
            {
                if (!MqttState.replyClosed(state))
                {
                    assert !MqttState.replyClosed(state);

                    state = MqttState.closeReply(state);
                    setSubscribeAppClosed();

                    doReset(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, EMPTY_OCTETS);
                }
            }

            private void doSubscribeAppEnd(
                long traceId,
                long authorization)
            {
                if (MqttState.initialOpening(state) && !MqttState.initialClosed(state))
                {
                    doEnd(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, EMPTY_OCTETS);
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

    private final class MqttSessionStream
    {
        private final MqttClient client;
        private List<MqttClient.Subscription> subscriptions;
        private final List<MqttClient.Subscription> unAckedSubscriptions;
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
        private long replyBud;
        private int replyPad;
        private int state;

        private long sessionExpiresId = NO_CANCEL_ID;
        private long sessionExpiresAt;

        MqttSessionStream(
            MqttClient client,
            MessageConsumer application,
            long originId,
            long routedId)
        {
            this.client = client;
            this.application = application;
            this.subscriptions = new ArrayList<>();
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
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

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = budgetId;
            replyPad = padding;
            state = MqttState.openReply(state);

            assert initialAck <= initialSeq;

            if (budgetId != 0L && debitorIndex == NO_DEBITOR_INDEX)
            {
                debitor = supplyDebitor.apply(budgetId);
                debitorIndex = debitor.acquire(budgetId, initialId, client::decodeNetwork);
            }

            if (MqttState.initialClosing(state) && !MqttState.initialClosed(state))
            {
                doSessionAppEnd(traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void onSessionReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            final OctetsFW extension = reset.extension();
            final MqttResetExFW mqttResetEx = extension.get(mqttResetExRO::tryWrap);

            if (mqttResetEx != null)
            {
                String16FW serverRef = mqttResetEx.serverRef();
                boolean serverRefExists = serverRef != null;

                byte reasonCode = serverRefExists ? SERVER_MOVED : SESSION_TAKEN_OVER;

                client.doEncodeDisconnect(traceId, authorization, reasonCode, serverRefExists ? serverRef : null);
            }
            setInitialClosed();

            client.decodeNetwork(traceId);
            cleanupAbort(traceId, authorization);
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
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();
            final OctetsFW extension = begin.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            initialMax = maximum;
            state = MqttState.openingInitial(state);


            client.doNetworkBegin(traceId, authorization, affinity);

            final MqttBeginExFW mqttBeginEx = extension.get(mqttBeginExRO::tryWrap);

            assert mqttBeginEx.kind() == MqttBeginExFW.KIND_SESSION;
            final MqttSessionBeginExFW mqttSessionBeginEx = mqttBeginEx.session();

            final String16FW clientId = mqttSessionBeginEx.clientId();
            final int flags = mqttSessionBeginEx.flags();
            final int expiry = mqttSessionBeginEx.expiry();

            client.doEncodeConnect(traceId, authorization, clientId, flags, expiry);
            doSessionWindow(traceId, authorization, client.encodeSlotOffset, encodeBudgetMax);
        }

        private void onSessionData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int reserved = data.reserved();
            final long authorization = data.authorization();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + reserved;
            client.encodeSharedBudget -= reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                doSessionReset(traceId, authorization);
                client.doNetworkAbort(traceId, authorization);
            }
            else
            {
                final DirectBuffer buffer = payload.buffer();
                final int offset = payload.offset();
                final int limit = payload.limit();

                MqttSessionStateFW sessionState = mqttSessionStateRO.tryWrap(buffer, offset, limit);

                //TODO: do we just echo back this? We should not subscribe yet,
                // as that should be done in reaction to onSubscribeBegin
                doSessionData(traceId, authorization, reserved, EMPTY_OCTETS, sessionState);
            }
        }

        private void onSessionEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            if (!MqttState.initialClosed(state))
            {
                client.doNetworkEnd(traceId, authorization);
            }
        }

        private void onSessionAbort(
            AbortFW abort)
        {
            setReplyClosed();

            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            cleanupAbort(traceId, authorization);
        }

        private boolean hasSessionWindow(
            int length)
        {
            return initialMax - (initialSeq - initialAck) >= length + initialPad;
        }

        private void doSessionData(
            long traceId,
            long authorization,
            int reserved,
            Flyweight dataEx,
            Flyweight payload)
        {
            assert MqttState.initialOpening(state);

            final DirectBuffer buffer = payload.buffer();
            final int offset = payload.offset();
            final int limit = payload.limit();
            final int length = limit - offset;
            assert reserved >= length + initialPad;

            reserved += initialPad;

            if (!MqttState.closed(state))
            {
                doData(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, reserved, buffer, offset, length, dataEx);

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;
            }
        }

        private void cleanupAbort(
            long traceId,
            long authorization)
        {
            doSessionAbort(traceId, authorization);
            doSessionReset(traceId, authorization);
        }

        private void cleanupEnd(
            long traceId,
            long authorization)
        {
            doSessionAppEnd(traceId, authorization, EMPTY_OCTETS);
        }

        private void doSessionAbort(
            long traceId,
            long authorization)
        {
            if (!MqttState.initialClosed(state))
            {
                setInitialClosed();

                doAbort(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doSessionAppEnd(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (MqttState.initialOpening(state) && !MqttState.initialClosed(state))
            {
                doEnd(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, extension);
            }
        }

        private void doSessionWindow(
            long traceId,
            long authorization,
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
                        traceId, authorization, client.encodeBudgetId, PUBLISH_FRAMING);
                }
            }
        }

        private void doSessionReset(
            long traceId,
            long authorization)
        {
            if (!MqttState.replyClosed(state))
            {
                setReplyClosed();

                doReset(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void setReplyClosed()
        {
            assert !MqttState.replyClosed(state);

            state = MqttState.closeReply(state);
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
        }

        public void setSubscriptions(List<MqttClient.Subscription> subscriptions)
        {
            this.subscriptions = subscriptions;
        }
        public List<MqttClient.Subscription> subscriptions()
        {
            return subscriptions;
        }

        public void doSessionBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            state = MqttState.openingReply(state);

            doBegin(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, EMPTY_OCTETS);
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
            MqttClient server,
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

                            //if (alias <= 0 || alias > server.topicAliasMaximum)
                            //{
                            //    reasonCode = TOPIC_ALIAS_INVALID;
                            //    break decode;
                            //}

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

