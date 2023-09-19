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
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.MALFORMED_PACKET;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.NORMAL_DISCONNECT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.PACKET_TOO_LARGE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.PAYLOAD_FORMAT_INVALID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.PROTOCOL_ERROR;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.QOS_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.RETAIN_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SUCCESS;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.TOPIC_ALIAS_INVALID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPublishFlags.RETAIN;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSubscribeFlags.NO_LOCAL;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSubscribeFlags.RETAIN_AS_PUBLISHED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSubscribeFlags.SEND_RETAINED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_ASSIGNED_CLIENT_ID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_AUTHENTICATION_DATA;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_AUTHENTICATION_METHOD;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_CONTENT_TYPE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_CORRELATION_DATA;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_EXPIRY_INTERVAL;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_MAXIMUM_PACKET_SIZE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_MAXIMUM_QO_S;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_PAYLOAD_FORMAT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_RECEIVE_MAXIMUM;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_RESPONSE_TOPIC;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_RETAIN_AVAILABLE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_SERVER_KEEP_ALIVE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_SESSION_EXPIRY;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_SHARED_SUBSCRIPTION_AVAILABLE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_SUBSCRIPTION_ID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_SUBSCRIPTION_IDS_AVAILABLE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_TOPIC_ALIAS;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_TOPIC_ALIAS_MAXIMUM;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_USER_PROPERTY;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_WILDCARD_SUBSCRIPTION_AVAILABLE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.DataFW.FIELD_OFFSET_PAYLOAD;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttPublishDataExFW.Builder.DEFAULT_EXPIRY_INTERVAL;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttPublishDataExFW.Builder.DEFAULT_FORMAT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttServerCapabilities.SHARED_SUBSCRIPTIONS;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttServerCapabilities.SUBSCRIPTION_IDS;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttServerCapabilities.WILDCARD;
import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_CREDITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.ObjectHashSet;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttBinding;
import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttBindingConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttRouteConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttBinaryFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPayloadFormatFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttQoS;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSessionFlags;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSessionStateFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttTopicFilterFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttWillMessageFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Varuint32FW;
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
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttSubackPayloadFW;
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
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttPublishBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttPublishDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttResetExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttSessionBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttSessionDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttSubscribeBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttSubscribeFlushExFW;
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
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttServerCapabilities;

public final class MqttClientFactory implements MqttStreamFactory
{
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);

    private static final String16FW MQTT_PROTOCOL_NAME = new String16FW("MQTT", BIG_ENDIAN);
    private static final int MQTT_PROTOCOL_VERSION = 5;
    private static final int CONNACK_FIXED_HEADER = 0b0010_0000;
    private static final int SUBACK_FIXED_HEADER = 0b1001_0000;
    private static final int UNSUBACK_FIXED_HEADER = 0b1011_0000;
    private static final int DISCONNECT_FIXED_HEADER = 0b1110_0000;

    private static final int NO_FLAGS = 0b0000_0000;
    private static final int RETAIN_MASK = 0b0000_0001;
    private static final int PUBLISH_QOS1_MASK = 0b0000_0010;
    private static final int PUBLISH_QOS2_MASK = 0b0000_0100;
    private static final int NO_LOCAL_FLAG_MASK = 0b0000_0100;
    private static final int RETAIN_AS_PUBLISHED_MASK = 0b0000_1000;
    private static final int RETAIN_HANDLING_MASK = 0b0011_0000;

    private static final int WILL_FLAG_MASK = 0b0000_0100;
    private static final int WILL_QOS_MASK = 0b0001_1000;
    private static final int WILL_RETAIN_MASK = 0b0010_0000;

    private static final int CONNACK_SESSION_PRESENT_MASK = 0b0000_0001;
    private static final int CONNACK_RESERVED_FLAGS_MASK = 0b1111_1110;

    private static final int CONNACK_SESSION_EXPIRY_MASK = 0b0000_0000_0001;
    private static final int CONNACK_MAXIMUM_QOS_MASK = 0b0000_0000_0010;
    private static final int CONNACK_RETAIN_AVAILABLE_MASK = 0b0000_0000_0100;
    private static final int CONNACK_MAXIMUM_PACKET_SIZE_MASK = 0b0000_0000_1000;
    private static final int CONNACK_ASSIGNED_CLIENT_IDENTIFIER_MASK = 0b0000_0001_0000;
    private static final int CONNACK_WILDCARD_SUBSCRIPTION_AVAILABLE_MASK = 0b0000_0010_0000;
    private static final int CONNACK_SUBSCRIPTION_IDENTIFIERS_MASK = 0b0000_0100_0000;
    private static final int CONNACK_SHARED_SUBSCRIPTION_AVAILABLE_MASK = 0b0000_1000_0000;
    private static final int CONNACK_KEEP_ALIVE_MASK = 0b0001_0000_0000;
    private static final int CONNACK_TOPIC_ALIAS_MAXIMUM_MASK = 0b0010_0000_0000;

    private static final int SHARED_SUBSCRIPTION_AVAILABLE_MASK = 1 << SHARED_SUBSCRIPTIONS.value();
    private static final int WILDCARD_AVAILABLE_MASK = 1 << WILDCARD.value();
    private static final int SUBSCRIPTION_IDS_AVAILABLE_MASK = 1 << SUBSCRIPTION_IDS.value();
    private static final int RETAIN_AVAILABLE_MASK = 1 << RETAIN.value();

    private static final int RETAIN_FLAG = 1 << RETAIN.ordinal();
    private static final int SEND_RETAINED_FLAG = 1 << SEND_RETAINED.ordinal();
    private static final int RETAIN_AS_PUBLISHED_FLAG = 1 << RETAIN_AS_PUBLISHED.ordinal();
    private static final int NO_LOCAL_FLAG = 1 << NO_LOCAL.ordinal();
    private static final int DO_NOT_SEND_RETAINED_MASK = 0b0010_0000;

    private static final int PUBLISH_TYPE = 0x03;

    private static final int PUBLISH_EXPIRED_SIGNAL = 1;
    private static final int KEEP_ALIVE_TIMEOUT_SIGNAL = 2;
    private static final int CONNACK_TIMEOUT_SIGNAL = 3;
    private static final int PINGRESP_TIMEOUT_SIGNAL = 4;

    private static final int PUBLISH_FRAMING = 255;
    private static final int KEEP_ALIVE = 60000;

    private static final String16FW NULL_STRING = new String16FW((String) null);
    public static final String SHARED_SUBSCRIPTION_LITERAL = "$share";


    private final BeginFW beginRO = new BeginFW();
    private final FlushFW flushRO = new FlushFW();
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
    private final MqttDataExFW mqttPublishDataExRO = new MqttDataExFW();
    private final MqttBeginExFW mqttBeginExRO = new MqttBeginExFW();
    private final MqttDataExFW mqttDataExRO = new MqttDataExFW();
    private final MqttFlushExFW mqttFlushExRO = new MqttFlushExFW();

    private final MqttBeginExFW.Builder mqttSessionBeginExRW = new MqttBeginExFW.Builder();
    private final MqttDataExFW.Builder mqttPublishDataExRW = new MqttDataExFW.Builder();
    private final MqttResetExFW.Builder mqttResetExRW = new MqttResetExFW.Builder();
    private final MqttWillFW.Builder willMessageRW = new MqttWillFW.Builder();
    private final MqttPacketHeaderFW mqttPacketHeaderRO = new MqttPacketHeaderFW();
    private final MqttConnackFW mqttConnackRO = new MqttConnackFW();
    private final MqttSubackFW mqttSubackRO = new MqttSubackFW();
    private final MqttUnsubackFW mqttUnsubackRO = new MqttUnsubackFW();
    private final MqttWillFW mqttWillRO = new MqttWillFW();
    private final MqttWillMessageFW mqttWillMessageRO = new MqttWillMessageFW();
    private final MqttPublishFW mqttPublishRO = new MqttPublishFW();
    private final MqttSubackPayloadFW mqttSubackPayloadRO = new MqttSubackPayloadFW();
    private final MqttUnsubackPayloadFW mqttUnsubackPayloadRO = new MqttUnsubackPayloadFW();
    private final MqttSubscribePayloadFW.Builder mqttSubscribePayloadRW = new MqttSubscribePayloadFW.Builder();
    private final MqttUnsubscribePayloadFW.Builder mqttUnsubscribePayloadRW = new MqttUnsubscribePayloadFW.Builder();
    private final MqttPingRespFW mqttPingRespRO = new MqttPingRespFW();
    private final MqttDisconnectFW mqttDisconnectRO = new MqttDisconnectFW();

    private final OctetsFW octetsRO = new OctetsFW();
    private final OctetsFW.Builder octetsRW = new OctetsFW.Builder();

    private final MqttPropertyFW mqttPropertyRO = new MqttPropertyFW();
    private final MqttPropertyFW.Builder mqttPropertyRW = new MqttPropertyFW.Builder();
    private final MqttPropertyFW.Builder mqttWillPropertyRW = new MqttPropertyFW.Builder();
    private final MqttSessionStateFW.Builder mqttSessionStateRW = new MqttSessionStateFW.Builder();

    private final MqttSessionStateFW mqttSessionStateRO = new MqttSessionStateFW();

    private final String16FW contentTypeRO = new String16FW(BIG_ENDIAN);
    private final String16FW responseTopicRO = new String16FW(BIG_ENDIAN);

    private final MqttPublishHeader mqttPublishHeaderRO = new MqttPublishHeader();

    private final MqttConnectFW.Builder mqttConnectRW = new MqttConnectFW.Builder();
    private final MqttSubscribeFW.Builder mqttSubscribeRW = new MqttSubscribeFW.Builder();
    private final MqttUnsubscribeFW.Builder mqttUnsubscribeRW = new MqttUnsubscribeFW.Builder();
    private final MqttPublishFW.Builder mqttPublishRW = new MqttPublishFW.Builder();
    private final MqttPingReqFW.Builder mqttPingReqRW = new MqttPingReqFW.Builder();
    private final MqttDisconnectFW.Builder mqttDisconnectRW = new MqttDisconnectFW.Builder();
    private final Array32FW.Builder<MqttUserPropertyFW.Builder, MqttUserPropertyFW> userPropertiesRW =
        new Array32FW.Builder<>(new MqttUserPropertyFW.Builder(), new MqttUserPropertyFW());
    private final Array32FW.Builder<Varuint32FW.Builder, Varuint32FW> subscriptionIdsRW =
        new Array32FW.Builder<>(new Varuint32FW.Builder(), new Varuint32FW());
    private final MqttClientDecoder decodeInitialType = this::decodeInitialType;
    private final MqttClientDecoder decodePacketType = this::decodePacketType;
    private final MqttClientDecoder decodeConnack = this::decodeConnack;
    private final MqttClientDecoder decodeSuback = this::decodeSuback;
    private final MqttClientDecoder decodeUnsuback = this::decodeUnsuback;
    private final MqttClientDecoder decodePublish = this::decodePublish;
    private final MqttClientDecoder decodePingresp = this::decodePingResp;
    private final MqttClientDecoder decodeDisconnect = this::decodeDisconnect;
    private final MqttClientDecoder decodeIgnoreAll = this::decodeIgnoreAll;
    private final MqttClientDecoder decodeUnknownType = this::decodeUnknownType;

    private final Map<MqttPacketType, MqttClientDecoder> decodersByPacketType;
    private final Int2ObjectHashMap<MqttClient> clients;

    private int maximumPacketSize;

    {
        final Map<MqttPacketType, MqttClientDecoder> decodersByPacketType = new EnumMap<>(MqttPacketType.class);
        decodersByPacketType.put(MqttPacketType.CONNACK, decodeConnack);
        decodersByPacketType.put(MqttPacketType.SUBACK, decodeSuback);
        decodersByPacketType.put(MqttPacketType.UNSUBACK, decodeUnsuback);
        decodersByPacketType.put(MqttPacketType.PUBLISH, decodePublish);
        // decodersByPacketType.put(MqttPacketType.PUBREC, decodePubrec);
        // decodersByPacketType.put(MqttPacketType.PUBREL, decodePubrel);
        // decodersByPacketType.put(MqttPacketType.PUBCOMP, decodePubcomp);
        decodersByPacketType.put(MqttPacketType.PINGRESP, decodePingresp);
        decodersByPacketType.put(MqttPacketType.DISCONNECT, decodeDisconnect);
        // decodersByPacketType.put(MqttPacketType.AUTH, decodeAuth);
        this.decodersByPacketType = decodersByPacketType;
    }

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer dataExtBuffer;
    private final MutableDirectBuffer sessionStateBuffer;
    private final MutableDirectBuffer payloadBuffer;
    private final MutableDirectBuffer propertyBuffer;
    private final MutableDirectBuffer userPropertiesBuffer;
    private final MutableDirectBuffer subscriptionIdsBuffer;
    private final MutableDirectBuffer willMessageBuffer;
    private final MutableDirectBuffer willPropertyBuffer;
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
    private final long connackTimeoutMillis;
    private final int encodeBudgetMax;

    private final CharsetDecoder utf8Decoder;


    public MqttClientFactory(
        MqttConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.dataExtBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.sessionStateBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.propertyBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.userPropertiesBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.subscriptionIdsBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.payloadBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.willMessageBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.willPropertyBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
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
        this.connackTimeoutMillis = SECONDS.toMillis(config.connackTimeout());
        this.maximumPacketSize = writeBuffer.capacity();
        this.encodeBudgetMax = bufferPool.slotCapacity();
        this.utf8Decoder = StandardCharsets.UTF_8.newDecoder();
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
            MqttClient client;
            switch (mqttBeginEx.kind())
            {
            case MqttBeginExFW.KIND_SESSION:
                clientId = mqttBeginEx.session().clientId();
                client = resolveClient(routedId, resolvedId, supplyInitialId.applyAsLong(resolvedId), clientId);
                client.sessionStream = new MqttSessionStream(client, sender, originId, routedId, initialId);
                newStream = client.sessionStream::onSession;
                break;
            case MqttBeginExFW.KIND_PUBLISH:
                final MqttPublishBeginExFW publishBeginEx = mqttBeginEx.publish();
                clientId = publishBeginEx.clientId();
                client = resolveClient(routedId, resolvedId, supplyInitialId.applyAsLong(resolvedId), clientId);
                MqttPublishStream publishStream = new MqttPublishStream(client, sender, originId, routedId, initialId);
                newStream = publishStream::onPublish;
                break;
            case MqttBeginExFW.KIND_SUBSCRIBE:
                final MqttSubscribeBeginExFW subscribeBeginEx = mqttBeginEx.subscribe();
                clientId = subscribeBeginEx.clientId();
                client = resolveClient(routedId, resolvedId, supplyInitialId.applyAsLong(resolvedId), clientId);
                MqttSubscribeStream subscribeStream = new MqttSubscribeStream(client, sender, originId, routedId, initialId);
                newStream = subscribeStream::onSubscribe;
                break;
            }
        }

        return newStream;
    }

    private MqttClient resolveClient(
        long routedId,
        long resolvedId,
        long initialId,
        String16FW clientId)
    {
        final int clientKey = clientKey(clientId.asString());
        return clients.computeIfAbsent(clientKey,
            s -> new MqttClient(routedId, resolvedId, initialId, maximumPacketSize));
    }

    private int clientKey(
        String client)
    {
        return Math.abs(client.intern().hashCode());
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
        MqttClient client,
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

            if (packetType != MqttPacketType.CONNACK)
            {
                client.doNetworkEnd(traceId, authorization);
                client.decoder = decodeIgnoreAll;
                break decode;
            }

            client.decodeablePacketBytes = packet.sizeof() + length;
            client.decoder = decodePacketType;
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

            final MqttConnackFW connack = mqttConnackRO.tryWrap(buffer, offset, limit);
            int flags = 0;
            decode:
            {
                if (connack == null)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }

                else if ((connack.typeAndFlags() & 0b1111_1111) != CONNACK_FIXED_HEADER)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                flags = connack.flags();

                reasonCode = decodeConnackFlags(flags);
                if (reasonCode != SUCCESS)
                {
                    break decode;
                }

                progress = client.onDecodeConnack(traceId, authorization, buffer, progress, limit, connack);
                client.decoder = decodePacketType;
            }

            if (reasonCode != SUCCESS)
            {
                client.onDecodeError(traceId, authorization, reasonCode);
                client.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodeSuback(
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

            final MqttSubackFW suback = mqttSubackRO.tryWrap(buffer, offset, limit);
            decode:
            {
                if (suback == null)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }
                else if ((suback.typeAndFlags() & 0b1111_1111) != SUBACK_FIXED_HEADER)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                progress = client.onDecodeSuback(traceId, authorization, buffer, progress, limit, suback);
                client.decoder = decodePacketType;
            }

            if (reasonCode != SUCCESS)
            {
                client.onDecodeError(traceId, authorization, reasonCode);
                client.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodeUnsuback(
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

            final MqttUnsubackFW unsuback = mqttUnsubackRO.tryWrap(buffer, offset, limit);
            decode:
            {
                if (unsuback == null)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }
                else if ((unsuback.typeAndFlags() & 0b1111_1111) != UNSUBACK_FIXED_HEADER)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                progress = client.onDecodeUnsuback(traceId, authorization, buffer, progress, limit, unsuback);
                client.decoder = decodePacketType;
            }

            if (reasonCode != SUCCESS)
            {
                client.onDecodeError(traceId, authorization, reasonCode);
                client.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodePublish(
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

        decode:
        if (length >= client.decodeablePacketBytes)
        {
            int reasonCode = SUCCESS;
            final MqttPublishFW publish = mqttPublishRO.tryWrap(buffer, offset, offset + client.decodeablePacketBytes);

            final MqttPublishHeader mqttPublishHeader = mqttPublishHeaderRO.reset();

            if (publish == null)
            {
                reasonCode = PROTOCOL_ERROR;
            }
            else
            {
                reasonCode = mqttPublishHeader.decode(client, publish.topicName(), publish.properties(), publish.typeAndFlags());
            }

            if (reasonCode == SUCCESS)
            {
                final int qos = mqttPublishHeader.qos;
                MqttSubscribeStream subscriber = client.subscribeStreams.get(qos);

                if (!client.existStreamForTopic(mqttPublishHeader.topic))
                {
                    MqttSessionStateFW.Builder sessionStateBuilder =
                        mqttSessionStateRW.wrap(sessionStateBuffer, 0, sessionStateBuffer.capacity());
                    client.sessionStream.subscriptions.forEach(s ->
                        sessionStateBuilder.subscriptionsItem(si ->
                            si.subscriptionId(s.id)
                                .qos(s.qos)
                                .flags(s.flags)
                                .pattern(s.filter)));
                    final Varuint32FW firstSubscriptionId = subscriptionIdsRW.build().matchFirst(s -> true);
                    final int subscriptionId = firstSubscriptionId != null ? firstSubscriptionId.value() : 0;
                    final Subscription adminSubscription = new Subscription();
                    adminSubscription.id = subscriptionId;
                    adminSubscription.qos = mqttPublishHeader.qos;
                    adminSubscription.filter = mqttPublishHeader.topic;
                    client.sessionStream.subscriptions.add(adminSubscription);

                    sessionStateBuilder.subscriptionsItem(si ->
                        si.subscriptionId(adminSubscription.id)
                            .qos(adminSubscription.qos)
                            .pattern(adminSubscription.filter));

                    MqttSessionStateFW sessionState = sessionStateBuilder.build();
                    client.sessionStream.doSessionData(traceId, authorization, sessionState.sizeof(), EMPTY_OCTETS, sessionState);

                    break decode;
                }

                if (subscriber == null)
                {
                    break decode;
                }

                final OctetsFW payload = publish.payload();
                final int payloadSize = payload.sizeof();

                if (mqttPublishHeaderRO.payloadFormat.equals(MqttPayloadFormat.TEXT) && invalidUtf8(payload))
                {
                    reasonCode = PAYLOAD_FORMAT_INVALID;
                    client.onDecodeError(traceId, authorization, reasonCode);
                    client.decoder = decodeIgnoreAll;
                }

                boolean canPublish = MqttState.replyOpened(subscriber.state);

                int reserved = payloadSize + subscriber.replyPad;
                canPublish &= subscriber.replySeq + reserved <= subscriber.replyAck + subscriber.replyMax;

                if (canPublish && subscriber.debitorIndex != NO_DEBITOR_INDEX && reserved != 0)
                {
                    final int minimum = reserved; // TODO: fragmentation
                    reserved = subscriber.debitor.claim(subscriber.debitorIndex, subscriber.replyId, minimum, reserved);
                }

                if (canPublish && (reserved != 0 || payloadSize == 0))
                {
                    client.onDecodePublish(traceId, authorization, reserved, payload, subscriber);
                    client.decodeablePacketBytes = 0;
                    client.decoder = decodePacketType;
                    progress = publish.limit();
                }
            }
            else
            {
                client.onDecodeError(traceId, authorization, reasonCode);
                client.decoder = decodeIgnoreAll;
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

    private int decodePingResp(
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
            final MqttPingRespFW ping = mqttPingRespRO.tryWrap(buffer, offset, limit);
            if (ping == null)
            {
                client.onDecodeError(traceId, authorization, PROTOCOL_ERROR);
                client.decoder = decodeIgnoreAll;
            }
            else
            {
                client.onDecodePingResp(traceId, authorization, ping);
                client.decoder = decodePacketType;
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
        private final AtomicInteger packetIdCounter;
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
        private final ObjectHashSet<MqttPublishStream> publishStreams;
        private final Int2ObjectHashMap<MqttSubscribeStream> subscribeStreams;
        private final Int2ObjectHashMap<String> topicAliases;
        private final long encodeBudgetId;

        private MessageConsumer network;
        private MqttSessionStream sessionStream;

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
        private int decodeablePacketBytes;

        private long connackTimeoutId = NO_CANCEL_ID;
        private long pingRespTimeoutId = NO_CANCEL_ID;
        private long connackTimeoutAt;
        private long pingRespTimeoutAt;

        private long keepAliveTimeoutId = NO_CANCEL_ID;
        private long keepAliveTimeoutAt;

        private long keepAliveMillis = KEEP_ALIVE;
        private long pingRespTimeoutMillis = (long) (KEEP_ALIVE * 0.5);

        private boolean connectAcked;
        private short topicAliasMaximum = Short.MAX_VALUE;
        private int flags = 0;

        private int capabilities = SHARED_SUBSCRIPTION_AVAILABLE_MASK | WILDCARD_AVAILABLE_MASK |
            SUBSCRIPTION_IDS_AVAILABLE_MASK | RETAIN_AVAILABLE_MASK;
        private int sessionExpiry = 0;
        private String clientId;
        private byte maximumQos = 2;
        private int maximumPacketSize;

        private int decodablePropertyMask = 0;

        private MqttClient(
            long originId,
            long routedId,
            long initialId,
            int maximumPacketSize)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.encodeBudgetId = supplyBudgetId.getAsLong();
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.decoder = decodeInitialType;
            this.publishStreams = new ObjectHashSet<>();
            this.subscribeStreams = new Int2ObjectHashMap<>();
            this.topicAliases = new Int2ObjectHashMap<>();
            this.maximumPacketSize = maximumPacketSize;
            this.packetIdCounter = new AtomicInteger();
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

            assert encodeBudgetIndex == NO_CREDITOR_INDEX;
            this.encodeBudgetIndex = creditor.acquire(encodeBudgetId);

            doNetworkWindow(traceId, authorization, 0, 0L, 0, bufferPool.slotCapacity());
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
                state = MqttState.closeReply(state);

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
            sessionStream.doSessionWindow(traceId, authorization, encodeSlotOffset, encodeBudgetMax);
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
            case PINGRESP_TIMEOUT_SIGNAL:
                onPingRespTimeoutSignal(signal);
                break;
            case CONNACK_TIMEOUT_SIGNAL:
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

            doEncodePingReq(traceId, authorization);
            keepAliveTimeoutId = NO_CANCEL_ID;
            doSignalKeepAliveTimeout();
        }

        private void onPingRespTimeoutSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            cleanupNetwork(traceId, authorization);
        }

        private void onConnectTimeoutSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            final long now = System.currentTimeMillis();
            if (now >= connackTimeoutAt)
            {
                cleanupStreamsUsingAbort(traceId, authorization);
                doNetworkEnd(traceId, authorization);
                decoder = decodeIgnoreAll;
            }
        }

        private void doCancelConnackTimeout()
        {
            if (connackTimeoutId != NO_CANCEL_ID)
            {
                signaler.cancel(connackTimeoutId);
                connackTimeoutId = NO_CANCEL_ID;
            }
        }

        private int onDecodeConnack(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int progress,
            int limit,
            MqttConnackFW connack)
        {
            byte reasonCode;
            decode:
            {
                if (connectAcked)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }

                final MqttPropertiesFW properties = connack.properties();

                reasonCode = decodeConnackProperties(properties);

                if (reasonCode != SUCCESS || connack.reasonCode() != SUCCESS)
                {
                    break decode;
                }


                Flyweight mqttBeginEx = mqttSessionBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mqttTypeId)
                    .session(sessionBuilder -> sessionBuilder
                        .flags(flags)
                        .expiry((int) TimeUnit.MILLISECONDS.toSeconds(sessionExpiry))
                        .qosMax(maximumQos)
                        .packetSizeMax(maximumPacketSize)
                        .capabilities(capabilities)
                        .clientId(clientId))
                    .build();

                sessionStream.doSessionBegin(traceId, authorization, 0, mqttBeginEx);
                connectAcked = true;

                doCancelConnackTimeout();
                doSignalKeepAliveTimeout();
            }

            progress = connack.limit();
            if (reasonCode != SUCCESS)
            {
                doCancelConnackTimeout();
                cleanupNetwork(traceId, authorization);
                decoder = decodeIgnoreAll;
            }

            final Flyweight mqttBeginEx = mqttSessionBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mqttTypeId)
                .session(sessionBuilder -> sessionBuilder
                    .flags(flags)
                    .expiry(sessionExpiry)
                    .qosMax(maximumQos)
                    .packetSizeMax(maximumPacketSize)
                    .capabilities(capabilities)
                    .clientId(clientId))
                .build();

            sessionStream.doSessionBegin(traceId, authorization, 0, mqttBeginEx);

            if (connack.reasonCode() != SUCCESS)
            {
                sessionStream.doSessionReset(traceId, authorization, connack.reasonCode());
                cleanupNetwork(traceId, authorization);
                decoder = decodeIgnoreAll;
            }

            return progress;
        }

        private byte decodeConnackProperties(
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
                        sessionExpiry = 0;
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.decodablePropertyMask |= CONNACK_SESSION_EXPIRY_MASK;
                    this.sessionExpiry = (int) mqttProperty.sessionExpiry();
                    break;
                case KIND_TOPIC_ALIAS_MAXIMUM:
                    if (isSetTopicAliasMaximum(decodablePropertyMask))
                    {
                        topicAliasMaximum = 0;
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.decodablePropertyMask |= CONNACK_TOPIC_ALIAS_MAXIMUM_MASK;
                    this.topicAliasMaximum = (short) (mqttProperty.topicAliasMaximum() & 0xFFFF);
                    break;
                case KIND_MAXIMUM_QO_S:
                    if (isSetMaximumQos(decodablePropertyMask))
                    {
                        maximumQos = 0;
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.decodablePropertyMask |= CONNACK_MAXIMUM_QOS_MASK;
                    this.maximumQos = (byte) mqttProperty.maximumQoS();
                    break;
                case KIND_RECEIVE_MAXIMUM:
                case KIND_MAXIMUM_PACKET_SIZE:
                    final int maxConnackPacketSize = (int) mqttProperty.maximumPacketSize();
                    if (maxConnackPacketSize == 0 || isSetMaximumPacketSize(decodablePropertyMask))
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.decodablePropertyMask |= CONNACK_MAXIMUM_PACKET_SIZE_MASK;
                    if (maxConnackPacketSize < maximumPacketSize)
                    {
                        this.maximumPacketSize = maxConnackPacketSize;
                    }
                    break;
                case KIND_RETAIN_AVAILABLE:
                    if (isSetRetainAvailable(decodablePropertyMask))
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.decodablePropertyMask |= CONNACK_RETAIN_AVAILABLE_MASK;
                    if (mqttProperty.retainAvailable() == 0)
                    {
                        this.capabilities &= ~(1 << MqttServerCapabilities.RETAIN.value());
                    }
                    break;
                case KIND_ASSIGNED_CLIENT_ID:
                    if (isSetAssignedClientId(decodablePropertyMask))
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.decodablePropertyMask |= CONNACK_ASSIGNED_CLIENT_IDENTIFIER_MASK;
                    clientId = mqttProperty.assignedClientId().asString();
                    break;
                case KIND_WILDCARD_SUBSCRIPTION_AVAILABLE:
                    if (isSetWildcardSubscriptions(decodablePropertyMask))
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.decodablePropertyMask |= CONNACK_WILDCARD_SUBSCRIPTION_AVAILABLE_MASK;
                    if (mqttProperty.wildcardSubscriptionAvailable() == 0)
                    {
                        this.capabilities &= ~(1 << MqttServerCapabilities.WILDCARD.value());
                    }
                    break;
                case KIND_SUBSCRIPTION_IDS_AVAILABLE:
                    if (isSetSubscriptionIdentifiers(decodablePropertyMask))
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.decodablePropertyMask |= CONNACK_SUBSCRIPTION_IDENTIFIERS_MASK;
                    if (mqttProperty.subscriptionIdsAvailable() == 0)
                    {
                        this.capabilities &= ~(1 << MqttServerCapabilities.SUBSCRIPTION_IDS.value());
                    }
                    break;
                case KIND_SHARED_SUBSCRIPTION_AVAILABLE:
                    if (isSetSharedSubscriptions(decodablePropertyMask))
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.decodablePropertyMask |= CONNACK_SHARED_SUBSCRIPTION_AVAILABLE_MASK;
                    if (mqttProperty.sharedSubscriptionAvailable() == 0)
                    {
                        this.capabilities &= ~(1 << MqttServerCapabilities.SHARED_SUBSCRIPTIONS.value());
                    }
                    break;
                case KIND_SERVER_KEEP_ALIVE:
                    if (isSetServerKeepAlive(decodablePropertyMask))
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.decodablePropertyMask |= CONNACK_SHARED_SUBSCRIPTION_AVAILABLE_MASK;
                    keepAliveMillis = SECONDS.toMillis(mqttProperty.serverKeepAlive());
                    pingRespTimeoutMillis = (long) (keepAliveMillis * 0.5);
                    break;
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

        private void onDecodePingResp(
            long traceId,
            long authorization,
            MqttPingRespFW ping)
        {
            doCancelPingRespTimeout();
        }

        private int onDecodeSuback(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int progress,
            int limit,
            MqttSubackFW suback)
        {
            final int packetId = suback.packetId();
            final OctetsFW decodePayload = suback.payload();

            final DirectBuffer decodeBuffer = decodePayload.buffer();
            final int decodeOffset = decodePayload.offset();
            final int decodeLimit = decodePayload.limit();

            final List<Subscription> unackedSubscriptions = sessionStream.unAckedSubscriptionsByPacketId.remove(packetId);
            MqttSessionStateFW.Builder sessionStateBuilder =
                mqttSessionStateRW.wrap(sessionStateBuffer, 0, sessionStateBuffer.capacity());

            sessionStream.subscriptions.stream().filter(s -> !unackedSubscriptions.contains(s)).forEach(s ->
                sessionStateBuilder.subscriptionsItem(si ->
                    si.subscriptionId(s.id)
                        .qos(s.qos)
                        .flags(s.flags)
                        .pattern(s.filter)));

            int i = 0;
            for (int decodeProgress = decodeOffset; decodeProgress < decodeLimit; )
            {
                final MqttSubackPayloadFW subackPayload =
                    mqttSubackPayloadRO.tryWrap(decodeBuffer, decodeProgress, decodeLimit);
                if (subackPayload == null)
                {
                    break;
                }
                decodeProgress = subackPayload.limit();

                final Subscription subscription = unackedSubscriptions.get(i++);
                sessionStateBuilder.subscriptionsItem(si ->
                    si.subscriptionId(subscription.id)
                        .qos(subscription.qos)
                        .flags(subscription.flags)
                        .reasonCode(subackPayload.reasonCode())
                        .pattern(subscription.filter));
            }
            final MqttSessionStateFW sessionState = sessionStateBuilder.build();
            sessionStream.doSessionData(traceId, authorization, sessionState.sizeof(), EMPTY_OCTETS, sessionState);

            progress = suback.limit();
            return progress;
        }

        private int onDecodeUnsuback(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int progress,
            int limit,
            MqttUnsubackFW unsuback)
        {
            final int packetId = unsuback.packetId();
            final OctetsFW decodePayload = unsuback.payload();

            final DirectBuffer decodeBuffer = decodePayload.buffer();
            final int decodeOffset = decodePayload.offset();
            final int decodeLimit = decodePayload.limit();

            final List<Subscription> unackedSubscriptions = sessionStream.unAckedSubscriptionsByPacketId.remove(packetId);
            sessionStream.subscriptions.removeAll(unackedSubscriptions);

            MqttSessionStateFW.Builder sessionStateBuilder =
                mqttSessionStateRW.wrap(sessionStateBuffer, 0, sessionStateBuffer.capacity());
            sessionStream.subscriptions.forEach(s ->
                sessionStateBuilder.subscriptionsItem(si ->
                    si.subscriptionId(s.id)
                        .qos(s.qos)
                        .flags(s.flags)
                        .pattern(s.filter)));
            int i = 0;
            for (int decodeProgress = decodeOffset; decodeProgress < decodeLimit; )
            {
                final MqttUnsubackPayloadFW unsubackPayload =
                    mqttUnsubackPayloadRO.tryWrap(decodeBuffer, decodeProgress, decodeLimit);
                if (unsubackPayload == null)
                {
                    break;
                }
                decodeProgress = unsubackPayload.limit();

                final int reasonCode = unsubackPayload.reasonCode();
                if (reasonCode != SUCCESS)
                {
                    final Subscription subscription = unackedSubscriptions.get(i);
                    sessionStateBuilder.subscriptionsItem(si ->
                        si.subscriptionId(subscription.id)
                            .qos(subscription.qos)
                            .flags(subscription.flags)
                            .reasonCode(reasonCode)
                            .pattern(subscription.filter));
                }
                i++;
            }
            final MqttSessionStateFW sessionState = sessionStateBuilder.build();
            sessionStream.doSessionData(traceId, authorization, sessionState.sizeof(), EMPTY_OCTETS, sessionState);

            progress = unsuback.limit();

            return progress;
        }

        private void onDecodePublish(
            long traceId,
            long authorization,
            int reserved,
            OctetsFW payload,
            MqttSubscribeStream stream)
        {
            final MqttDataExFW.Builder builder = mqttPublishDataExRW.wrap(dataExtBuffer, 0, dataExtBuffer.capacity())
                .typeId(mqttTypeId)
                .subscribe(s ->
                {
                    s.topic(mqttPublishHeaderRO.topic);
                    s.qos(mqttPublishHeaderRO.qos);
                    s.flags(mqttPublishHeaderRO.flags);
                    s.subscriptionIds(subscriptionIdsRW.build());
                    s.expiryInterval(mqttPublishHeaderRO.expiryInterval);
                    s.contentType(mqttPublishHeaderRO.contentType);
                    s.format(f -> f.set(mqttPublishHeaderRO.payloadFormat));
                    s.responseTopic(mqttPublishHeaderRO.responseTopic);
                    s.correlation(c -> c.bytes(mqttPublishHeaderRO.correlationData));
                    final Array32FW<MqttUserPropertyFW> userProperties = userPropertiesRW.build();
                    userProperties.forEach(c -> s.propertiesItem(p -> p.key(c.key()).value(c.value())));
                });


            final MqttDataExFW dataEx = builder.build();
            if (stream != null)
            {
                stream.doSubscribeData(traceId, authorization, reserved, payload, dataEx);
            }
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
                if (disconnect.reasonCode() != SUCCESS)
                {
                    sessionStream.doSessionReset(traceId, authorization, disconnect.reasonCode());
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
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                default:
                    break;
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
            cleanupStreamsUsingAbort(traceId, authorization);
            doNetworkEnd(traceId, authorization);
        }

        private void doNetworkBegin(
            long traceId,
            long authorization,
            long affinity)
        {
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

                doEnd(network, originId, routedId, initialId, encodeSeq, encodeAck, encodeMax,
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

                doAbort(network, originId, routedId, initialId, encodeSeq, encodeAck, encodeMax,
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
            long budgetId)
        {
            doNetworkWindow(traceId, authorization, padding, budgetId, decodeSlotReserved, decodeMax);
        }

        private void doNetworkWindow(
            long traceId,
            long authorization,
            int padding,
            long budgetId,
            int minReplyNoAck,
            int minReplyMax)
        {
            final long newReplyAck = Math.max(decodeSeq - minReplyNoAck, decodeAck);

            if (newReplyAck > decodeAck || minReplyMax > decodeMax || !MqttState.initialOpened(state))
            {
                decodeAck = newReplyAck;
                assert decodeAck <= decodeSeq;

                decodeMax = minReplyMax;

                state = MqttState.openInitial(state);

                doWindow(network, originId, routedId, replyId, decodeSeq, decodeAck, decodeMax,
                    traceId, authorization, budgetId, padding);
            }
        }

        private void doEncodePublish(
            long traceId,
            long authorization,
            int flags,
            OctetsFW payload,
            OctetsFW extension,
            String topic)
        {
            if ((flags & 0x02) != 0)
            {
                final MqttDataExFW mqttDataEx = extension.get(mqttPublishDataExRO::tryWrap);
                final int payloadSize = payload.sizeof();

                assert mqttDataEx.kind() == MqttBeginExFW.KIND_PUBLISH;
                final MqttPublishDataExFW mqttPublishDataEx = mqttDataEx.publish();

                final int deferred = mqttPublishDataEx.deferred();
                final int expiryInterval = mqttPublishDataEx.expiryInterval();
                final String16FW contentType = mqttPublishDataEx.contentType();
                final String16FW responseTopic = mqttPublishDataEx.responseTopic();
                final MqttBinaryFW correlation = mqttPublishDataEx.correlation();
                final MqttPayloadFormatFW format = mqttPublishDataEx.format();
                final Array32FW<io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttUserPropertyFW> properties =
                    mqttPublishDataEx.properties();

                final int topicLength = topic.length();

                AtomicInteger propertiesSize = new AtomicInteger();

                final int publishFlags = mqttPublishDataEx.flags();
                final int qos = mqttPublishDataEx.qos();

                final int publishNetworkTypeAndFlags = PUBLISH_TYPE << 4 |
                    calculatePublishNetworkFlags(PUBLISH_TYPE << 4 | publishFlags, qos);

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

                if (!format.get().equals(MqttPayloadFormat.NONE))
                {
                    mqttPropertyRW.wrap(propertyBuffer, propertiesSize.get(), propertyBuffer.capacity())
                        .payloadFormat((byte) mqttPublishDataEx.format().get().ordinal())
                        .build();
                    propertiesSize.set(mqttPropertyRW.limit());
                }

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
                        .remainingLength(3 + topicLength + propertiesSize.get() + payloadSize + deferred)
                        .topicName(topic)
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
            String clientId,
            int flags,
            int sessionExpiry,
            MqttWillMessageFW willMessage)
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
            MqttWillFW will = null;
            if (willMessage != null)
            {
                final int expiryInterval = willMessage.expiryInterval();
                final String16FW contentType = willMessage.contentType();
                final String16FW responseTopic = willMessage.responseTopic();
                final MqttBinaryFW correlation = willMessage.correlation();
                final MqttPayloadFormatFW format = willMessage.format();
                final Array32FW<io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttUserPropertyFW> properties =
                    willMessage.properties();

                AtomicInteger willPropertiesSize = new AtomicInteger();
                if (expiryInterval != -1)
                {
                    mqttWillPropertyRW.wrap(willPropertyBuffer, willPropertiesSize.get(), willPropertyBuffer.capacity())
                        .expiryInterval(expiryInterval)
                        .build();
                    willPropertiesSize.set(mqttWillPropertyRW.limit());
                }

                if (contentType.value() != null)
                {
                    mqttWillPropertyRW.wrap(willPropertyBuffer, willPropertiesSize.get(), willPropertyBuffer.capacity())
                        .contentType(contentType.asString())
                        .build();
                    willPropertiesSize.set(mqttWillPropertyRW.limit());
                }

                if (!format.get().equals(MqttPayloadFormat.NONE))
                {
                    mqttWillPropertyRW.wrap(willPropertyBuffer, willPropertiesSize.get(), willPropertyBuffer.capacity())
                        .payloadFormat((byte) format.get().ordinal())
                        .build();
                    willPropertiesSize.set(mqttWillPropertyRW.limit());
                }

                if (responseTopic.value() != null)
                {
                    mqttWillPropertyRW.wrap(willPropertyBuffer, willPropertiesSize.get(), willPropertyBuffer.capacity())
                        .responseTopic(responseTopic.asString())
                        .build();
                    willPropertiesSize.set(mqttWillPropertyRW.limit());
                }

                if (correlation.length() != -1)
                {
                    mqttWillPropertyRW.wrap(willPropertyBuffer, willPropertiesSize.get(), willPropertyBuffer.capacity())
                        .correlationData(a -> a.bytes(correlation.bytes()))
                        .build();
                    willPropertiesSize.set(mqttWillPropertyRW.limit());
                }

                properties.forEach(p ->
                {
                    mqttWillPropertyRW.wrap(willPropertyBuffer, willPropertiesSize.get(), willPropertyBuffer.capacity())
                        .userProperty(c -> c.key(p.key()).value(p.value()))
                        .build();
                    willPropertiesSize.set(mqttWillPropertyRW.limit());
                });

                will = willMessageRW.wrap(willMessageBuffer, 0, willMessageBuffer.capacity())
                    .properties(p -> p.length(willPropertiesSize.get())
                        .value(willPropertyBuffer, 0, willPropertiesSize.get()))
                    .topic(willMessage.topic())
                    .payload(p -> p.bytes(willMessage.payload().bytes()))
                    .build();
            }

            final int propertiesSize0 = propertiesSize;
            final int willSize = will != null ? will.sizeof() : 0;
            flags |= will != null ? (WILL_FLAG_MASK | ((willMessage.flags() & RETAIN_MASK) != 0 ? WILL_RETAIN_MASK : 0)) : 0;
            final MqttConnectFW connect =
                mqttConnectRW.wrap(writeBuffer, FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                    .typeAndFlags(0x10)
                    .remainingLength(11 + propertiesSize0 + clientId.length() + 2 + willSize)
                    .protocolName(MQTT_PROTOCOL_NAME)
                    .protocolVersion(MQTT_PROTOCOL_VERSION)
                    .flags(flags)
                    .keepAlive((int) MILLISECONDS.toSeconds(keepAliveMillis))
                    .properties(p -> p.length(propertiesSize0)
                        .value(propertyBuffer, 0, propertiesSize0))
                    .clientId(clientId)
                    .build();

            doNetworkData(traceId, authorization, 0L, connect);
            if (will != null)
            {
                doNetworkData(traceId, authorization, 0L, will);
            }
        }

        private void doEncodeSubscribe(
            long traceId,
            long authorization,
            List<Subscription> subscriptions,
            int packetId)
        {
            int propertiesSize = 0;

            MqttPropertyFW mqttProperty;

            final int subscriptionId = subscriptions.get(0).id;
            if (subscriptionId != 0)
            {
                mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                    .subscriptionId(i -> i.set(subscriptionId))
                    .build();
                propertiesSize = mqttProperty.limit();
            }

            final int propertiesSize0 = propertiesSize;

            final MutableDirectBuffer encodeBuffer = payloadBuffer;
            final int encodeOffset = 0;
            final int encodeLimit = payloadBuffer.capacity();

            int encodeProgress = encodeOffset;

            for (Subscription s : subscriptions)
            {
                final int flags = calculateSubscribeFlags(s.flags);
                final MqttSubscribePayloadFW subscribePayload =
                    mqttSubscribePayloadRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .filter(s.filter)
                        .options(flags)
                        .build();
                encodeProgress = subscribePayload.limit();
            }

            final OctetsFW encodePayload = octetsRO.wrap(encodeBuffer, encodeOffset, encodeProgress);
            final MqttSubscribeFW subscribe =
                mqttSubscribeRW.wrap(writeBuffer, FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                    .typeAndFlags(0x82)
                    .remainingLength(3 + propertiesSize0 + encodePayload.sizeof())
                    .packetId(packetId)
                    .properties(p -> p.length(propertiesSize0)
                        .value(propertyBuffer, 0, propertiesSize0))
                    .payload(encodePayload)
                    .build();

            sessionStream.unAckedSubscriptionsByPacketId.put(packetId, subscriptions);
            doNetworkData(traceId, authorization, 0L, subscribe);
        }

        private void doEncodeUnsubscribe(
            long traceId,
            long authorization,
            List<Subscription> subscriptions,
            int packetId)
        {
            final MutableDirectBuffer encodeBuffer = payloadBuffer;
            final int encodeOffset = 0;
            final int encodeLimit = payloadBuffer.capacity();

            int encodeProgress = encodeOffset;

            for (Subscription s : subscriptions)
            {
                final MqttUnsubscribePayloadFW unsubscribePayload =
                    mqttUnsubscribePayloadRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .filter(s.filter)
                        .build();
                encodeProgress = unsubscribePayload.limit();
            }

            final OctetsFW encodePayload = octetsRO.wrap(encodeBuffer, encodeOffset, encodeProgress);
            final MqttUnsubscribeFW unsubscribe =
                mqttUnsubscribeRW.wrap(writeBuffer, FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                    .typeAndFlags(0xa2)
                    .remainingLength(3 + encodePayload.sizeof())
                    .packetId(packetId)
                    .properties(p -> p.length(0)
                        .value(propertyBuffer, 0, 0))
                    .payload(encodePayload)
                    .build();

            sessionStream.unAckedSubscriptionsByPacketId.put(packetId, subscriptions);
            doNetworkData(traceId, authorization, 0L, unsubscribe);
        }

        private void doEncodePingReq(
            long traceId,
            long authorization)
        {
            final MqttPingReqFW pingReq =
                mqttPingReqRW.wrap(writeBuffer, FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                    .typeAndFlags(0xc0)
                    .remainingLength(0x00)
                    .build();

            doNetworkData(traceId, authorization, 0L, pingReq);
            doSignalPingRespTimeout();
        }

        private void doEncodeDisconnect(
            long traceId,
            long authorization,
            int reasonCode)
        {
            int propertiesSize = 0;

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

                doData(network, originId, routedId, initialId, encodeSeq, encodeAck, encodeMax,
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

        private void  cleanupStreamsUsingAbort(
            long traceId,
            long authorization)
        {
            publishStreams.forEach(s -> s.cleanupAbort(traceId, authorization));
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
            publishStreams.forEach(s -> s.doPublishEnd(traceId, authorization));
            subscribeStreams.values().forEach(s -> s.doSubscribeEnd(traceId, authorization));
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
            if (keepAliveMillis > 0)
            {
                keepAliveTimeoutAt = System.currentTimeMillis() + keepAliveMillis;

                if (keepAliveTimeoutId == NO_CANCEL_ID)
                {
                    keepAliveTimeoutId =
                        signaler.signalAt(keepAliveTimeoutAt, originId, routedId, initialId, KEEP_ALIVE_TIMEOUT_SIGNAL, 0);
                }
            }
        }

        private void doSignalConnackTimeout()
        {
            connackTimeoutAt = System.currentTimeMillis() + connackTimeoutMillis;

            if (connackTimeoutId == NO_CANCEL_ID)
            {
                connackTimeoutId = signaler.signalAt(connackTimeoutAt, originId, routedId, initialId, CONNACK_TIMEOUT_SIGNAL, 0);
            }
        }

        private void doSignalPingRespTimeout()
        {
            pingRespTimeoutAt = System.currentTimeMillis() + pingRespTimeoutMillis;

            if (pingRespTimeoutId == NO_CANCEL_ID)
            {
                pingRespTimeoutId =
                    signaler.signalAt(pingRespTimeoutAt, originId, routedId, initialId, PINGRESP_TIMEOUT_SIGNAL, 0);
            }
        }

        private void doCancelPingRespTimeout()
        {
            if (pingRespTimeoutId != NO_CANCEL_ID)
            {
                signaler.cancel(pingRespTimeoutId);
                pingRespTimeoutId = NO_CANCEL_ID;
            }
        }

        private int calculateSubscribeFlags(
            int options)
        {
            int flags = 0;
            if ((options & SEND_RETAINED_FLAG) == 0)
            {
                flags |= DO_NOT_SEND_RETAINED_MASK;
            }

            if ((options & RETAIN_AS_PUBLISHED_FLAG) != 0)
            {
                flags |= RETAIN_AS_PUBLISHED_MASK;
            }

            if ((options & NO_LOCAL_FLAG) != 0)
            {
                flags |= NO_LOCAL_FLAG_MASK;
            }


            return flags;
        }

        private boolean existStreamForTopic(
            String topic)
        {
            return sessionStream.subscriptions.stream().anyMatch(s ->
            {
                String regex = s.filter.replace("#", ".*").replace("+", "[^/]+");
                return Pattern.matches(regex, topic);
            });
        }

        private int getNextPacketId()
        {
            final int packetId = packetIdCounter.incrementAndGet();
            if (packetId == Integer.MAX_VALUE)
            {
                packetIdCounter.set(0);
            }
            return packetId;
        }
    }

    private final class MqttSessionStream
    {
        private final MqttClient client;
        private final List<Subscription> subscriptions;
        private final Int2ObjectHashMap<List<Subscription>> unAckedSubscriptionsByPacketId;
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

        MqttSessionStream(
            MqttClient client,
            MessageConsumer application,
            long originId,
            long routedId,
            long initialId)
        {
            this.client = client;
            this.application = application;
            this.subscriptions = new ArrayList<>();
            this.unAckedSubscriptionsByPacketId = new Int2ObjectHashMap<>();
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
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
            final boolean wasOpen = MqttState.replyOpened(state);

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
                doSessionEnd(traceId, authorization);
            }

            if (!wasOpen)
            {
                doSessionData(traceId, authorization, 0, EMPTY_OCTETS, EMPTY_OCTETS);
            }
        }

        private void onSessionReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = MqttState.closeReply(state);

            client.doNetworkReset(traceId, authorization);
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

            client.clientId = mqttSessionBeginEx.clientId().asString();
            client.flags = mqttSessionBeginEx.flags();
            client.sessionExpiry = mqttSessionBeginEx.expiry();

            if (!isSetWillFlag(mqttSessionBeginEx.flags()))
            {
                client.doEncodeConnect(traceId, authorization, client.clientId, client.flags, client.sessionExpiry, null);
                client.doSignalConnackTimeout();
            }
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
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + reserved;
            client.encodeSharedBudget -= reserved;

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + initialMax)
            {
                doSessionReset(traceId, authorization);
                client.doNetworkAbort(traceId, authorization);
            }
            else
            {

                final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                final MqttDataExFW mqttDataEx =
                    dataEx != null && dataEx.typeId() == mqttTypeId ? extension.get(mqttDataExRO::tryWrap) : null;
                final MqttSessionDataExFW mqttSessionDataEx =
                    mqttDataEx != null && mqttDataEx.kind() == MqttDataExFW.KIND_SESSION ? mqttDataEx.session() : null;

                final DirectBuffer buffer = payload.buffer();
                final int offset = payload.offset();
                final int limit = payload.limit();

                switch (mqttSessionDataEx.kind().get())
                {
                case WILL:
                    MqttWillMessageFW willMessage = mqttWillMessageRO.tryWrap(buffer, offset, limit);
                    client.doEncodeConnect(traceId, authorization, client.clientId, client.flags,
                        client.sessionExpiry, willMessage);
                    client.doSignalConnackTimeout();
                    break;
                case STATE:
                    MqttSessionStateFW sessionState = mqttSessionStateRO.tryWrap(buffer, offset, limit);

                    final List<Subscription> newSubscribeState = new ArrayList<>();
                    sessionState.subscriptions().forEach(filter ->
                    {
                        Subscription subscription = new Subscription();
                        subscription.id = (int) filter.subscriptionId();
                        subscription.filter = filter.pattern().asString();
                        subscription.flags = filter.flags();
                        subscription.qos = filter.qos();
                        newSubscribeState.add(subscription);
                    });


                    final List<Subscription> newSubscriptions = newSubscribeState.stream()
                        .filter(s -> !subscriptions.contains(s))
                        .collect(Collectors.toList());

                    final List<Subscription>  oldSubscriptions = subscriptions.stream()
                        .filter(s -> !newSubscribeState.contains(s))
                        .collect(Collectors.toList());
                    final int packetId = client.getNextPacketId();

                    if (newSubscriptions.size() > 0)
                    {
                        client.doEncodeSubscribe(traceId, authorization, newSubscriptions, packetId);
                    }
                    if (oldSubscriptions.size() > 0)
                    {
                        client.doEncodeUnsubscribe(traceId, authorization, oldSubscriptions, packetId);
                    }
                    client.sessionStream.subscriptions.addAll(newSubscriptions);
                    client.sessionStream.subscriptions.removeAll(oldSubscriptions);
                    break;
                }
            }
        }

        private void onSessionEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = MqttState.closeInitial(state);

            assert initialAck <= initialSeq;

            client.doEncodeDisconnect(traceId, authorization, SUCCESS);
            client.doNetworkEnd(traceId, authorization);

            doSessionEnd(traceId, authorization);
        }

        private void onSessionAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = MqttState.closeInitial(state);

            assert initialAck <= initialSeq;

            client.doNetworkAbort(traceId, authorization);
        }

        private void doSessionData(
            long traceId,
            long authorization,
            int reserved,
            Flyweight dataEx,
            Flyweight payload)
        {
            final DirectBuffer buffer = payload.buffer();
            final int offset = payload.offset();
            final int limit = payload.limit();
            final int length = limit - offset;

            if (!MqttState.closed(state))
            {
                doData(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, reserved, buffer, offset, length, dataEx);

                replySeq += reserved;
                assert replySeq <= replyAck + replyMax;
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
            doSessionEnd(traceId, authorization);
        }

        private void doSessionAbort(
            long traceId,
            long authorization)
        {
            if (!MqttState.replyClosed(state))
            {
                state = MqttState.closeReply(state);

                doAbort(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doSessionWindow(
            long traceId,
            long authorization,
            int minInitialNoAck,
            int minInitialMax)
        {
            if (MqttState.replyOpened(client.state))
            {
                final long newInitialAck = Math.max(initialSeq - minInitialNoAck, initialAck);

                if (newInitialAck > initialAck || minInitialMax > initialMax || !MqttState.initialOpened(state))
                {
                    initialAck = newInitialAck;
                    assert initialAck <= initialSeq;

                    initialMax = minInitialMax;

                    doWindow(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, client.encodeBudgetId, PUBLISH_FRAMING);
                }
            }
        }

        private void doSessionReset(
            long traceId,
            long authorization,
            int reasonCode)
        {
            if (!MqttState.initialClosed(state))
            {
                Flyweight resetEx = mqttResetExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mqttTypeId)
                    .reasonCode(reasonCode)
                    .build();

                state = MqttState.closeInitial(state);

                doReset(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, resetEx);
            }
        }

        private void doSessionReset(
            long traceId,
            long authorization)
        {
            if (!MqttState.initialClosed(state))
            {
                state = MqttState.closeInitial(state);

                doReset(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        public void doSessionBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            state = MqttState.openingReply(state);

            doBegin(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, extension);
        }

        private void doSessionEnd(
            long traceId,
            long authorization)
        {
            if (!MqttState.replyClosed(state))
            {
                state = MqttState.closeReply(state);

                doEnd(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }
    }

    private class MqttSubscribeStream
    {
        private final MessageConsumer application;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private long budgetId;

        private MqttClient client;
        private BudgetDebitor debitor;
        private long debitorIndex = NO_DEBITOR_INDEX;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private int state;
        private List<Subscription> subscriptions;

        MqttSubscribeStream(
            MqttClient client,
            MessageConsumer application,
            long originId,
            long routedId,
            long initialId)
        {
            this.client = client;
            this.application = application;
            this.subscriptions = new ArrayList<>();
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
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
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onSubscribeFlush(flush);
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

            final MqttBeginExFW mqttBeginEx = extension.get(mqttBeginExRO::tryWrap);

            assert mqttBeginEx.kind() == MqttBeginExFW.KIND_SUBSCRIBE;
            final MqttSubscribeBeginExFW mqttSubscribeBeginEx = mqttBeginEx.subscribe();

            final Array32FW<MqttTopicFilterFW> filters = mqttSubscribeBeginEx.filters();

            filters.forEach(filter ->
            {
                Subscription subscription = new Subscription();
                subscription.id = (int) filter.subscriptionId();
                subscription.filter = filter.pattern().asString();
                subscription.flags = filter.flags();
                subscription.qos = filter.qos();
                subscriptions.add(subscription);
            });
            final int qos = subscriptions.get(0).qos;
            client.subscribeStreams.put(qos, this);

            doSubscribeBegin(traceId, authorization, affinity);
            doSubscribeWindow(traceId, authorization, client.encodeSlotOffset, encodeBudgetMax);
        }

        private void onSubscribeFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            final OctetsFW extension = flush.extension();
            final MqttFlushExFW mqttFlushEx = extension.get(mqttFlushExRO::tryWrap);

            assert mqttFlushEx.kind() == MqttFlushExFW.KIND_SUBSCRIBE;
            final MqttSubscribeFlushExFW mqttSubscribeFlushEx = mqttFlushEx.subscribe();

            Array32FW<MqttTopicFilterFW> filters = mqttSubscribeFlushEx.filters();

            final List<Subscription> newSubscribeState = new ArrayList<>();
            filters.forEach(f ->
            {
                Subscription subscription = new Subscription();
                subscription.id = (int) f.subscriptionId();
                subscription.filter = f.pattern().asString();
                subscription.flags = f.flags();
                subscription.qos = f.qos();
                newSubscribeState.add(subscription);
            });

            this.subscriptions = newSubscribeState;
        }

        private void doSubscribeBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            state = MqttState.openingReply(state);

            doBegin(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, EMPTY_OCTETS);
        }


        private void doSubscribeAbort(
            long traceId,
            long authorization)
        {
            if (!MqttState.replyClosed(state))
            {
                state = MqttState.closeReply(state);

                doAbort(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doSubscribeEnd(
            long traceId,
            long authorization)
        {
            if (!MqttState.replyClosed(state))
            {
                state = MqttState.closeReply(state);

                doEnd(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void onSubscribeReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = MqttState.closeReply(state);

            client.doNetworkReset(traceId, authorization);
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

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = padding;
            state = MqttState.openReply(state);

            client.doNetworkWindow(traceId, authorization, padding, budgetId);
            client.decodeNetwork(traceId);
        }

        private void onSubscribeEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = MqttState.closeInitial(state);

            assert initialAck <= initialSeq;

            final int packetId = client.getNextPacketId();
            client.doEncodeUnsubscribe(traceId, authorization, subscriptions, packetId);
            doSubscribeEnd(traceId, authorization);
        }

        private void onSubscribeAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = MqttState.closeInitial(state);

            assert initialAck <= initialSeq;

            client.doNetworkAbort(traceId, authorization);
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
            int minInitialNoAck,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(initialSeq - minInitialNoAck, initialAck);

            if (newInitialAck > initialAck || minInitialMax > initialMax || !MqttState.initialOpened(state))
            {
                initialAck = newInitialAck;
                assert initialAck <= initialSeq;

                initialMax = minInitialMax;

                state = MqttState.openInitial(state);

                doWindow(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, client.encodeBudgetId, PUBLISH_FRAMING);
            }
        }

        private void doSubscribeReset(
            long traceId,
            long authorization)
        {
            if (!MqttState.initialClosed(state))
            {
                state = MqttState.closeInitial(state);

                doReset(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }


        private void doSubscribeData(
            long traceId,
            long authorization,
            int reserved,
            OctetsFW payload,
            Flyweight extension)
        {
            assert MqttState.replyOpening(state);

            final DirectBuffer buffer = payload.buffer();
            final int offset = payload.offset();
            final int limit = payload.limit();
            final int length = limit - offset;
            assert reserved >= length + replyPad;

            doData(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, reserved, buffer, offset, length, extension);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }
    }


    private class MqttPublishStream
    {
        private final MessageConsumer application;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private long budgetId;

        private MqttClient client;
        private BudgetDebitor debitor;
        private long debitorIndex = NO_DEBITOR_INDEX;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private int state;

        private long publishExpiresId = NO_CANCEL_ID;
        private long publishExpiresAt;
        private String topic;


        MqttPublishStream(
            MqttClient client,
            MessageConsumer application,
            long originId,
            long routedId,
            long initialId)
        {
            this.client = client;
            this.application = application;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
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

            final MqttBeginExFW mqttBeginEx = extension.get(mqttBeginExRO::tryWrap);

            assert mqttBeginEx.kind() == MqttBeginExFW.KIND_PUBLISH;
            final MqttPublishBeginExFW mqttPublishBeginEx = mqttBeginEx.publish();

            this.topic = mqttPublishBeginEx.topic().asString();
            client.publishStreams.add(this);

            doPublishBegin(traceId, authorization, affinity);
            doPublishWindow(traceId, authorization, client.encodeSlotOffset, encodeBudgetMax);
        }

        private void onPublishData(
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
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + reserved;
            client.encodeSharedBudget -= reserved;

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + initialMax)
            {
                doPublishAbort(traceId, authorization);
                client.doNetworkAbort(traceId, authorization);
            }
            else
            {
                if (payload != null && payload.sizeof() <= maximumPacketSize)
                {
                    client.doEncodePublish(traceId, authorization, flags, payload, extension, topic);
                }
                else
                {
                    droppedHandler.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
                }
                doPublishWindow(traceId, authorization, client.encodeSlotOffset, encodeBudgetMax);
            }
        }

        private void onPublishEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = MqttState.closeInitial(state);

            assert initialAck <= initialSeq;

            doPublishEnd(traceId, authorization);
        }

        private void onPublishAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = MqttState.closeInitial(state);

            assert initialAck <= initialSeq;

            client.doNetworkAbort(traceId, authorization);
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

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = padding;
            state = MqttState.openReply(state);

            client.doNetworkWindow(traceId, authorization, padding, budgetId);
        }

        private void onPublishReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = MqttState.closeReply(state);

            client.doNetworkReset(traceId, authorization);
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
                doPublishEnd(traceId, authorization);
            }
            else
            {
                publishExpiresId = NO_CANCEL_ID;
                doSignalPublishExpiration();
            }
        }


        private void doPublishBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            state = MqttState.openingReply(state);

            doBegin(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, EMPTY_OCTETS);
        }

        private void doPublishAbort(
            long traceId,
            long authorization)
        {
            if (!MqttState.replyClosed(state))
            {
                state = MqttState.closeReply(state);

                doAbort(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
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
            int minInitialNoAck,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(initialSeq - minInitialNoAck, initialAck);

            if (newInitialAck > initialAck || minInitialMax > initialMax || !MqttState.initialOpened(state))
            {
                initialAck = newInitialAck;
                assert initialAck <= initialSeq;

                initialMax = minInitialMax;

                state = MqttState.openInitial(state);

                doWindow(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, client.encodeBudgetId, PUBLISH_FRAMING);
            }
        }

        private void doPublishReset(
            long traceId,
            long authorization)
        {
            if (!MqttState.initialClosed(state))
            {
                state = MqttState.closeInitial(state);

                doReset(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doPublishEnd(
            long traceId,
            long authorization)
        {
            if (!MqttState.replyClosed(state))
            {
                state = MqttState.closeReply(state);

                doEnd(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
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

    private static boolean isSetSessionExpiryInterval(
        int flags)
    {
        return (flags & CONNACK_SESSION_EXPIRY_MASK) != 0;
    }

    private static boolean isSetTopicAliasMaximum(
        int flags)
    {
        return (flags & CONNACK_TOPIC_ALIAS_MAXIMUM_MASK) != 0;
    }

    private static boolean isSetMaximumQos(
        int flags)
    {
        return (flags & CONNACK_MAXIMUM_QOS_MASK) != 0;
    }

    private static boolean isSetMaximumPacketSize(
        int flags)
    {
        return (flags & CONNACK_MAXIMUM_PACKET_SIZE_MASK) != 0;
    }

    private static boolean isSetRetainAvailable(
        int flags)
    {
        return (flags & CONNACK_RETAIN_AVAILABLE_MASK) != 0;
    }

    private static boolean isSetAssignedClientId(
        int flags)
    {
        return (flags & CONNACK_ASSIGNED_CLIENT_IDENTIFIER_MASK) != 0;
    }

    private static boolean isSetWildcardSubscriptions(
        int flags)
    {
        return (flags & CONNACK_WILDCARD_SUBSCRIPTION_AVAILABLE_MASK) != 0;
    }

    private static boolean isSetSubscriptionIdentifiers(
        int flags)
    {
        return (flags & CONNACK_SUBSCRIPTION_IDENTIFIERS_MASK) != 0;
    }

    private static boolean isSetSharedSubscriptions(
        int flags)
    {
        return (flags & CONNACK_SHARED_SUBSCRIPTION_AVAILABLE_MASK) != 0;
    }

    private static boolean isSetServerKeepAlive(
        int flags)
    {
        return (flags & CONNACK_KEEP_ALIVE_MASK) != 0;
    }

    private static boolean isSetWillFlag(
        int flags)
    {
        return (flags & MqttSessionFlags.WILL.value() << 1) != 0;
    }

    private static int decodeConnackFlags(
        int flags)
    {
        int reasonCode = SUCCESS;

        if ((flags & CONNACK_RESERVED_FLAGS_MASK) != 0)
        {
            reasonCode = MALFORMED_PACKET;
        }

        return reasonCode;
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
            MqttClient client,
            String16FW topicName,
            MqttPropertiesFW properties,
            int typeAndFlags)
        {
            this.topic = topicName.asString();
            int reasonCode = SUCCESS;
            userPropertiesRW.wrap(userPropertiesBuffer, 0, userPropertiesBuffer.capacity());
            subscriptionIdsRW.wrap(subscriptionIdsBuffer, 0, subscriptionIdsBuffer.capacity());

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
                flags = calculatePublishFlags(typeAndFlags);
                qos = calculatePublishQos(typeAndFlags);

                int alias = 0;
                if (qos > client.maximumQos)
                {
                    reasonCode = QOS_NOT_SUPPORTED;
                }
                else if (retained && !isRetainAvailable(client.capabilities))
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

                            if (alias <= 0 || alias > client.topicAliasMaximum)
                            {
                                reasonCode = TOPIC_ALIAS_INVALID;
                                break decode;
                            }

                            if (topic.isEmpty())
                            {
                                if (!client.topicAliases.containsKey(alias))
                                {
                                    reasonCode = PROTOCOL_ERROR;
                                    break decode;
                                }
                                topic = client.topicAliases.get(alias);
                            }
                            else
                            {
                                client.topicAliases.put(alias, topic);
                            }
                            break;
                        case KIND_SUBSCRIPTION_ID:
                            subscriptionIdsRW.item(i -> i.set(mqttProperty.subscriptionId().value()));
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
        private int calculatePublishQos(
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

        private int calculatePublishFlags(
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

    private boolean isRetainAvailable(
        int capabilities)
    {
        return (capabilities & 1 << MqttServerCapabilities.RETAIN.value()) != 0;
    }

    private final class Subscription
    {
        private int id = 0;
        private String filter;
        private int qos;
        private int flags;
        private int reasonCode;


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
                this.qos == other.qos && this.flags == other.flags && this.reasonCode == other.reasonCode;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(this.id, this.filter, this.qos, this.flags, this.reasonCode);
        }
    }
}

