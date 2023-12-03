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
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.GRANTED_QOS_2;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.IDENTIFIER_REJECTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.IMPLEMENTATION_SPECIFIC_ERROR;
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
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SUBACK_FAILURE_CODE_V4;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SUBSCRIPTION_IDS_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SUCCESS;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.TOPIC_ALIAS_INVALID;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
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
import org.agrona.collections.IntArrayList;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.MutableBoolean;
import org.agrona.collections.Object2IntHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttPatternConfig.MqttConnectProperty;
import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttBinding;
import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration;
import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttValidator;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttBindingConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttRouteConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttBinaryFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPayloadFormatFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttQoS;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSessionStateFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttWillMessageFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Varuint32FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.BinaryFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttConnackV4FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttConnackV5FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttConnectFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttConnectV4FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttConnectV5FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttDisconnectV4FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttDisconnectV5FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPacketHeaderFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPacketType;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPingReqFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPingRespFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertiesFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPubackFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPubcompFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPublishFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPublishQosFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPubrecFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPubrelFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPublishV4FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPublishV5FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttSubackV4FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttSubackV5FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttSubscribePayloadFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttSubscribeV4FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttSubscribeV5FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttUnsubackPayloadFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttUnsubackV4FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttUnsubackV5FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttUnsubscribePayloadFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttUnsubscribeV4FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttUnsubscribeV5FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttUserPropertyFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttWillV4FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttWillV5FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttResetExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttServerCapabilities;
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
import io.aklivity.zilla.runtime.engine.validator.Validator;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttOffsetStateFlags;

public final class MqttServerFactory implements MqttStreamFactory
{
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);

    private static final String16FW MQTT_PROTOCOL_NAME = new String16FW("MQTT", BIG_ENDIAN);
    private static final int MQTT_PROTOCOL_VERSION_5 = 5;
    private static final int MQTT_PROTOCOL_VERSION_4 = 4;
    private static final int MAXIMUM_CLIENT_ID_LENGTH = 36;
    private static final int CONNECT_FIXED_HEADER = 0b0001_0000;
    private static final int SUBSCRIBE_FIXED_HEADER = 0b1000_0010;
    private static final int UNSUBSCRIBE_FIXED_HEADER = 0b1010_0010;
    private static final int DISCONNECT_FIXED_HEADER = 0b1110_0000;
    private static final int PUBACK_FIXED_HEADER = 0b0100_0000;
    private static final int PUBREC_FIXED_HEADER = 0b0101_0000;
    private static final int PUBREL_FIXED_HEADER = 0b0110_0000;
    private static final int PUBCOMP_FIXED_HEADER = 0b0111_0000;

    private static final int CONNECT_RESERVED_MASK = 0b0000_0001;
    private static final int NO_FLAGS = 0b0000_0000;
    private static final int RETAIN_MASK = 0b0000_0001;
    private static final int PUBLISH_QOS1_MASK = 0b0000_0010;
    private static final int PUBLISH_QOS2_MASK = 0b0000_0100;
    private static final int NO_LOCAL_FLAG_MASK = 0b0000_0100;
    private static final int RETAIN_AS_PUBLISHED_MASK = 0b0000_1000;
    private static final int RETAIN_HANDLING_MASK = 0b0011_0000;
    private static final int SUBSCRIPTION_QOS_MASK = 0b0000_0011;
    private static final int RETAIN_AVAILABLE_MASK = 1 << MqttServerCapabilities.RETAIN.value();
    private static final int WILDCARD_AVAILABLE_MASK = 1 << MqttServerCapabilities.WILDCARD.value();
    private static final int SUBSCRIPTION_IDS_AVAILABLE_MASK = 1 << MqttServerCapabilities.SUBSCRIPTION_IDS.value();
    private static final int SHARED_SUBSCRIPTIONS_AVAILABLE_MASK = 1 << MqttServerCapabilities.SHARED_SUBSCRIPTIONS.value();

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
    public static final int GENERATED_SUBSCRIPTION_ID_MASK = 0x70;
    public static final int QOS2_INCOMPLETE_OFFSET_STATE = MqttOffsetStateFlags.INCOMPLETE.value();
    public static final int QOS2_COMPLETE_OFFSET_STATE = MqttOffsetStateFlags.COMPLETE.value();
    public static final int MAX_CONNACK_REASONCODE_V4 = 5;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final FlushFW flushRO = new FlushFW();
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

    private final MqttDataExFW mqttSubscribeDataExRO = new MqttDataExFW();
    private final MqttFlushExFW mqttSubscribeFlushExRO = new MqttFlushExFW();
    private final MqttResetExFW mqttResetExRO = new MqttResetExFW();
    private final MqttBeginExFW mqttBeginExRO = new MqttBeginExFW();

    private final MqttBeginExFW.Builder mqttPublishBeginExRW = new MqttBeginExFW.Builder();
    private final MqttBeginExFW.Builder mqttSubscribeBeginExRW = new MqttBeginExFW.Builder();
    private final MqttBeginExFW.Builder mqttSessionBeginExRW = new MqttBeginExFW.Builder();
    private final MqttDataExFW.Builder mqttPublishDataExRW = new MqttDataExFW.Builder();
    private final MqttDataExFW.Builder mqttSessionDataExRW = new MqttDataExFW.Builder();
    private final MqttFlushExFW.Builder mqttFlushExRW = new MqttFlushExFW.Builder();
    private final MqttWillMessageFW.Builder mqttWillMessageRW = new MqttWillMessageFW.Builder();
    private final MqttSessionStateFW.Builder mqttSessionStateFW = new MqttSessionStateFW.Builder();
    private final MqttPacketHeaderFW mqttPacketHeaderRO = new MqttPacketHeaderFW();
    private final MqttConnectFW mqttConnectRO = new MqttConnectFW();;
    private final MqttPubackFW mqttPubackRO = new MqttPubackFW();
    private final MqttPubrecFW mqttPubrecRO = new MqttPubrecFW();
    private final MqttPubrelFW mqttPubrelRO = new MqttPubrelFW();
    private final MqttPubcompFW mqttPubcompRO = new MqttPubcompFW();
    private final MqttSubscribeFW mqttSubscribeRO = new MqttSubscribeFW();
    private final MqttConnectV5FW mqttConnectV5RO = new MqttConnectV5FW();
    private final MqttConnectV4FW mqttConnectV4RO = new MqttConnectV4FW();
    private final MqttWillV5FW mqttWillV5RO = new MqttWillV5FW();
    private final MqttWillV4FW mqttWillV4RO = new MqttWillV4FW();
    private final MqttPublishV5FW mqttPublishV5RO = new MqttPublishV5FW();
    private final MqttPublishV4FW mqttPublishV4RO = new MqttPublishV4FW();
    private final MqttSubscribeV5FW mqttSubscribeV5RO = new MqttSubscribeV5FW();
    private final MqttSubscribeV4FW mqttSubscribeV4RO = new MqttSubscribeV4FW();
    private final MqttSubscribePayloadFW mqttSubscribePayloadRO = new MqttSubscribePayloadFW();
    private final MqttUnsubscribeV5FW mqttUnsubscribeV5RO = new MqttUnsubscribeV5FW();
    private final MqttUnsubscribeV4FW mqttUnsubscribeV4RO = new MqttUnsubscribeV4FW();
    private final MqttUnsubscribePayloadFW mqttUnsubscribePayloadRO = new MqttUnsubscribePayloadFW();
    private final MqttPingReqFW mqttPingReqRO = new MqttPingReqFW();
    private final MqttDisconnectV5FW mqttDisconnectV5RO = new MqttDisconnectV5FW();
    private final MqttDisconnectV4FW mqttDisconnectV4RO = new MqttDisconnectV4FW();

    private final OctetsFW octetsRO = new OctetsFW();
    private final OctetsFW.Builder octetsRW = new OctetsFW.Builder();

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);
    private final MqttPropertyFW mqttPropertyRO = new MqttPropertyFW();
    private final MqttPropertyFW.Builder mqttPropertyRW = new MqttPropertyFW.Builder();

    private final MqttSessionStateFW mqttSessionStateRO = new MqttSessionStateFW();

    private final String16FW contentTypeRO = new String16FW(BIG_ENDIAN);
    private final String16FW responseTopicRO = new String16FW(BIG_ENDIAN);
    private final String16FW usernameRO = new String16FW(BIG_ENDIAN);
    private final BinaryFW passwordRO = new BinaryFW();

    private final MqttPublishHeader mqttPublishHeaderRO = new MqttPublishHeader();
    private final MqttConnectPayload mqttConnectPayloadRO = new MqttConnectPayload();

    private final MqttPublishQosFW.Builder mqttPublishQosRW = new MqttPublishQosFW.Builder();
    private final MqttPubackFW.Builder mqttPubackRW = new MqttPubackFW.Builder();
    private final MqttPubrecFW.Builder mqttPubrecRW = new MqttPubrecFW.Builder();
    private final MqttPubrelFW.Builder mqttPubrelRW = new MqttPubrelFW.Builder();
    private final MqttPubcompFW.Builder mqttPubcompRW = new MqttPubcompFW.Builder();;
    private final MqttConnackV5FW.Builder mqttConnackV5RW = new MqttConnackV5FW.Builder();
    private final MqttConnackV4FW.Builder mqttConnackV4RW = new MqttConnackV4FW.Builder();
    private final MqttPublishV4FW.Builder mqttPublishV4RW = new MqttPublishV4FW.Builder();
    private final MqttPublishV5FW.Builder mqttPublishV5RW = new MqttPublishV5FW.Builder();
    private final MqttSubackV5FW.Builder mqttSubackV5RW = new MqttSubackV5FW.Builder();
    private final MqttSubackV4FW.Builder mqttSubackV4RW = new MqttSubackV4FW.Builder();
    private final MqttUnsubackV4FW.Builder mqttUnsubackV4RW = new MqttUnsubackV4FW.Builder();
    private final MqttUnsubackV5FW.Builder mqttUnsubackV5RW = new MqttUnsubackV5FW.Builder();
    private final MqttUnsubackPayloadFW.Builder mqttUnsubackPayloadRW = new MqttUnsubackPayloadFW.Builder();
    private final MqttPingRespFW.Builder mqttPingRespRW = new MqttPingRespFW.Builder();
    private final MqttDisconnectV5FW.Builder mqttDisconnectV5RW = new MqttDisconnectV5FW.Builder();
    private final Array32FW.Builder<MqttUserPropertyFW.Builder, MqttUserPropertyFW> userPropertiesRW =
        new Array32FW.Builder<>(new MqttUserPropertyFW.Builder(), new MqttUserPropertyFW());
    private final Array32FW.Builder<MqttUserPropertyFW.Builder, MqttUserPropertyFW> willUserPropertiesRW =
        new Array32FW.Builder<>(new MqttUserPropertyFW.Builder(), new MqttUserPropertyFW());

    private final MqttServerDecoder decodeInitialType = this::decodeInitialType;
    private final MqttServerDecoder decodeConnectV4 = this::decodeConnectV4;
    private final MqttServerDecoder decodeConnectV5 = this::decodeConnectV5;
    private final MqttServerDecoder decodeConnectPayload = this::decodeConnectPayload;
    private final MqttServerDecoder decodeConnectWillMessage = this::decodeConnectWillMessage;
    private final MqttServerDecoder decodePublishV4 = this::decodePublishV4;
    private final MqttServerDecoder decodePublishV5 = this::decodePublishV5;
    private final MqttServerDecoder decodeSubscribeV4 = this::decodeSubscribeV4;
    private final MqttServerDecoder decodeSubscribeV5 = this::decodeSubscribeV5;
    private final MqttServerDecoder decodeUnsubscribeV4 = this::decodeUnsubscribeV4;
    private final MqttServerDecoder decodeUnsubscribeV5 = this::decodeUnsubscribeV5;
    private final MqttServerDecoder decodePublish = this::decodePublish;
    private final MqttServerDecoder decodePuback = this::decodePuback;
    private final MqttServerDecoder decodePubrec = this::decodePubrec;
    private final MqttServerDecoder decodePubrel = this::decodePubrel;
    private final MqttServerDecoder decodePubcomp = this::decodePubcomp;
    private final MqttServerDecoder decodeSubscribe = this::decodeSubscribe;
    private final MqttServerDecoder decodeUnsubscribe = this::decodeUnsubscribe;
    private final MqttServerDecoder decodePingreq = this::decodePingreq;
    private final MqttServerDecoder decodeDisconnectV4 = this::decodeDisconnectV4;
    private final MqttServerDecoder decodeDisconnectV5 = this::decodeDisconnectV5;
    private final MqttServerDecoder decodeIgnoreAll = this::decodeIgnoreAll;
    private final MqttServerDecoder decodeUnknownType = this::decodeUnknownType;

    private final Int2ObjectHashMap<MqttServerDecoder> decodePacketTypeByVersion;
    private final Map<MqttPacketType, MqttServerDecoder> decodersByPacketTypeV4;
    private final Map<MqttPacketType, MqttServerDecoder> decodersByPacketTypeV5;
    private final IntSupplier supplySubscriptionId;
    private final EngineContext context;

    private int maximumPacketSize;

    {
        final Map<MqttPacketType, MqttServerDecoder> decodersByPacketType = new EnumMap<>(MqttPacketType.class);
        decodersByPacketType.put(MqttPacketType.CONNECT, decodeConnectV4);
        decodersByPacketType.put(MqttPacketType.PUBLISH, decodePublishV4);
        decodersByPacketType.put(MqttPacketType.PUBREC, decodePubrec);
        decodersByPacketType.put(MqttPacketType.PUBREL, decodePubrel);
        decodersByPacketType.put(MqttPacketType.PUBCOMP, decodePubcomp);
        decodersByPacketType.put(MqttPacketType.SUBSCRIBE, decodeSubscribeV4);
        decodersByPacketType.put(MqttPacketType.UNSUBSCRIBE, decodeUnsubscribeV4);
        decodersByPacketType.put(MqttPacketType.PINGREQ, decodePingreq);
        decodersByPacketType.put(MqttPacketType.DISCONNECT, decodeDisconnectV4);
        // decodersByPacketType.put(MqttPacketType.AUTH, decodeAuth);
        this.decodersByPacketTypeV4 = decodersByPacketType;
    }

    {
        final Map<MqttPacketType, MqttServerDecoder> decodersByPacketType = new EnumMap<>(MqttPacketType.class);
        decodersByPacketType.put(MqttPacketType.CONNECT, decodeConnectV5);
        decodersByPacketType.put(MqttPacketType.PUBLISH, decodePublishV5);
        decodersByPacketType.put(MqttPacketType.PUBREC, decodePubrec);
        decodersByPacketType.put(MqttPacketType.PUBREL, decodePubrel);
        decodersByPacketType.put(MqttPacketType.PUBCOMP, decodePubcomp);
        decodersByPacketType.put(MqttPacketType.SUBSCRIBE, decodeSubscribeV5);
        decodersByPacketType.put(MqttPacketType.UNSUBSCRIBE, decodeUnsubscribeV5);
        decodersByPacketType.put(MqttPacketType.PINGREQ, decodePingreq);
        decodersByPacketType.put(MqttPacketType.DISCONNECT, decodeDisconnectV5);
        // decodersByPacketType.put(MqttPacketType.AUTH, decodeAuth);
        this.decodersByPacketTypeV5 = decodersByPacketType;
    }

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer dataExtBuffer;
    private final MutableDirectBuffer payloadBuffer;
    private final MutableDirectBuffer propertyBuffer;
    private final MutableDirectBuffer sessionExtBuffer;
    private final MutableDirectBuffer sessionStateBuffer;
    private final MutableDirectBuffer userPropertiesBuffer;
    private final MutableDirectBuffer willMessageBuffer;
    private final MutableDirectBuffer willUserPropertiesBuffer;

    private final ByteBuffer charsetBuffer;
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
    private final short topicAliasMaximumLimit;
    private final boolean noLocal;
    private final int sessionExpiryGracePeriod;
    private final Supplier<String16FW> supplyClientId;
    private final MqttValidator validator;
    private final CharsetDecoder utf8Decoder;
    private final ConcurrentMap<String, IntArrayList> unreleasedPacketIdsByClientId;

    private Map<String, Validator> validators;

    public MqttServerFactory(
        MqttConfiguration config,
        EngineContext context,
        ConcurrentMap<String, IntArrayList> unreleasedPacketIdsByClientId)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.dataExtBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.propertyBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.userPropertiesBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.charsetBuffer = ByteBuffer.wrap(new byte[writeBuffer.capacity()]);
        this.payloadBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.sessionExtBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.sessionStateBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.willMessageBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
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
        this.context = context;
        this.bindings = new Long2ObjectHashMap<>();
        this.mqttTypeId = context.supplyTypeId(MqttBinding.NAME);
        this.publishTimeoutMillis = SECONDS.toMillis(config.publishTimeout());
        this.connectTimeoutMillis = SECONDS.toMillis(config.connectTimeout());
        this.keepAliveMinimum = config.keepAliveMinimum();
        this.keepAliveMaximum = config.keepAliveMaximum();
        this.maximumPacketSize = writeBuffer.capacity();
        this.topicAliasMaximumLimit = (short) Math.max(config.topicAliasMaximum(), 0);
        this.noLocal = config.noLocal();
        this.sessionExpiryGracePeriod = config.sessionExpiryGracePeriod();
        this.encodeBudgetMax = bufferPool.slotCapacity();
        this.validator = new MqttValidator();
        this.utf8Decoder = StandardCharsets.UTF_8.newDecoder();
        this.supplySubscriptionId = config.subscriptionId();
        final Optional<String16FW> clientId = Optional.ofNullable(config.clientId()).map(String16FW::new);
        this.supplyClientId = clientId.isPresent() ? clientId::get : () -> new String16FW(UUID.randomUUID().toString());
        this.decodePacketTypeByVersion = new Int2ObjectHashMap<>();
        this.decodePacketTypeByVersion.put(MQTT_PROTOCOL_VERSION_4, this::decodePacketTypeV4);
        this.decodePacketTypeByVersion.put(MQTT_PROTOCOL_VERSION_5, this::decodePacketTypeV5);
        this.unreleasedPacketIdsByClientId = unreleasedPacketIdsByClientId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        MqttBindingConfig mqttBinding = new MqttBindingConfig(binding, context);
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
            this.validators = binding.topics;

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

            final MqttConnectFW mqttConnect = mqttConnectRO.tryWrap(buffer, offset, limit);
            if (mqttConnect != null)
            {
                final int reasonCode = decodeConnectProtocol(mqttConnect.protocolName(), mqttConnect.protocolVersion());
                if (reasonCode != SUCCESS)
                {
                    server.onDecodeError(traceId, authorization, reasonCode, MQTT_PROTOCOL_VERSION_5);
                    server.decoder = decodeIgnoreAll;
                    break decode;
                }
                server.version = mqttConnect.protocolVersion();

                server.decodeablePacketBytes = packet.sizeof() + length;
                server.decoder = decodePacketTypeByVersion.get(server.version);
            }
        }

        return offset;
    }

    private int decodePacketTypeV4(
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
            MqttPacketType packetType = MqttPacketType.valueOf(packet.typeAndFlags() >> 4);

            final MqttServerDecoder decoder = server.version == MQTT_PROTOCOL_VERSION_4 ?
                decodersByPacketTypeV4.getOrDefault(packetType, decodeUnknownType) :
                decodersByPacketTypeV5.getOrDefault(packetType, decodeUnknownType);

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

    private int decodePacketTypeV5(
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
            MqttPacketType packetType = MqttPacketType.valueOf(packet.typeAndFlags() >> 4);

            final MqttServerDecoder decoder = server.version == MQTT_PROTOCOL_VERSION_4 ?
                decodersByPacketTypeV4.getOrDefault(packetType, decodeUnknownType) :
                decodersByPacketTypeV5.getOrDefault(packetType, decodeUnknownType);

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

    private int decodeConnectV4(
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

            final MqttConnectV4FW mqttConnect = mqttConnectV4RO.tryWrap(buffer, progress, limit);
            int flags = 0;
            decode:
            {
                if (mqttConnect == null)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }

                server.decodableRemainingBytes = mqttConnect.remainingLength();
                flags = mqttConnect.flags();

                reasonCode = decodeConnectType(mqttConnect.typeAndFlags(), flags);
                if (reasonCode != SUCCESS)
                {
                    break decode;
                }

                if (server.decodeablePacketBytes > maximumPacketSize)
                {
                    server.onDecodeError(traceId, authorization, PACKET_TOO_LARGE, MQTT_PROTOCOL_VERSION_4);
                    server.decoder = decodeIgnoreAll;
                }

                reasonCode = decodeConnectFlags(flags);
                if (reasonCode != SUCCESS)
                {
                    break decode;
                }

                if (server.connected)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }

                final String16FW clientId = mqttConnect.clientId();

                if (clientId.length() == 0 && !isCleanStart(flags))
                {
                    reasonCode = IDENTIFIER_REJECTED;
                    break decode;
                }

                progress = mqttConnect.limit();
                if (clientId.length() > MAXIMUM_CLIENT_ID_LENGTH)
                {
                    reasonCode = CLIENT_IDENTIFIER_NOT_VALID;
                    break decode;
                }

                progress = server.onDecodeConnect(traceId, authorization, buffer, progress, limit,
                    mqttConnect.keepAlive(), mqttConnect.flags(), mqttConnect.protocolVersion(), clientId);

                final int decodedLength = progress - offset - mqttPacketHeaderRO.sizeof();
                server.decodableRemainingBytes -= decodedLength;
            }

            if (reasonCode != SUCCESS)
            {
                server.onDecodeError(traceId, authorization, reasonCode, MQTT_PROTOCOL_VERSION_4);
                server.decoder = decodeIgnoreAll;
            }
            else
            {
                server.decoder = decodeConnectPayload;
            }
        }

        return progress;
    }

    private int decodeConnectV5(
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

            final MqttConnectV5FW mqttConnect = mqttConnectV5RO.tryWrap(buffer, progress, limit);
            int flags = 0;
            decode:
            {
                if (mqttConnect == null)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }

                server.decodableRemainingBytes = mqttConnect.remainingLength();
                flags = mqttConnect.flags();

                reasonCode = decodeConnectType(mqttConnect.typeAndFlags(), flags);
                if (reasonCode != SUCCESS)
                {
                    break decode;
                }

                if (server.decodeablePacketBytes > maximumPacketSize)
                {
                    server.onDecodeError(traceId, authorization, PACKET_TOO_LARGE, MQTT_PROTOCOL_VERSION_5);
                    server.decoder = decodeIgnoreAll;
                }

                reasonCode = decodeConnectFlags(flags);
                if (reasonCode != SUCCESS)
                {
                    break decode;
                }

                if (server.connected)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }

                final MqttPropertiesFW properties = mqttConnect.properties();

                reasonCode = server.decodeConnectProperties(properties);

                if (reasonCode != SUCCESS)
                {
                    break decode;
                }
                final String16FW clientId = mqttConnect.clientId();
                progress = mqttConnect.limit();

                if (clientId == null || clientId.length() > MAXIMUM_CLIENT_ID_LENGTH)
                {
                    reasonCode = CLIENT_IDENTIFIER_NOT_VALID;
                    break decode;
                }

                progress = server.onDecodeConnect(traceId, authorization, buffer, progress, limit,
                    mqttConnect.keepAlive(), mqttConnect.flags(), mqttConnect.protocolVersion(), clientId);

                final int decodedLength = progress - offset - mqttPacketHeaderRO.sizeof();
                server.decodableRemainingBytes -= decodedLength;
            }

            if (reasonCode != SUCCESS)
            {
                server.onDecodeError(traceId, authorization, reasonCode, MQTT_PROTOCOL_VERSION_5);
                server.decoder = decodeIgnoreAll;
            }
            else
            {
                server.decoder = decodeConnectPayload;
            }
        }

        return progress;
    }

    private int decodeConnectPayload(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        int progress = offset;

        progress = server.onDecodeConnectPayload(traceId, authorization, buffer, progress, limit);
        server.decodableRemainingBytes -= progress - offset;
        if (server.decodableRemainingBytes == 0)
        {
            server.decoder = decodePacketTypeByVersion.get(server.version);
        }

        return progress;
    }

    private int decodeConnectWillMessage(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        int progress = offset;

        progress = server.onDecodeConnectWillMessage(traceId, authorization, buffer, progress, limit);
        server.decodableRemainingBytes -= progress - offset;
        if (server.decodableRemainingBytes == 0)
        {
            server.decoder = decodePacketTypeByVersion.get(server.version);
        }

        return progress;
    }

    private int decodePublishV4(
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
        int reasonCode = SUCCESS;

        decode:
        if (length >= server.decodeablePacketBytes)
        {
            final MqttPublishHeader mqttPublishHeader = mqttPublishHeaderRO.reset();
            final MqttPublishV4FW publishV4 = mqttPublishV4RO.tryWrap(buffer, offset, offset + server.decodeablePacketBytes);
            if (publishV4 == null)
            {
                reasonCode = PROTOCOL_ERROR;
                break decode;
            }

            reasonCode = mqttPublishHeader.decodeV4(server, publishV4.topicName(), publishV4.typeAndFlags());

            final OctetsFW payload = publishV4.payload();

            if (reasonCode == SUCCESS)
            {
                final String topic = mqttPublishHeader.topic;
                final int topicKey = topicKey(topic);
                MqttServer.MqttPublishStream publisher = server.publishes.get(topicKey);

                if (publisher == null)
                {
                    publisher = server.resolvePublishStream(traceId, authorization, topic);
                    if (publisher == null)
                    {
                        server.decodePublisherKey = 0;
                        server.decodeablePacketBytes = 0;
                        server.decoder = decodePacketTypeByVersion.get(server.version);
                        progress = publishV4.limit();
                        break decode;
                    }
                }

                server.decodePublisherKey = topicKey;

                final int payloadSize = payload.sizeof();

                if (validators != null && !validContent(mqttPublishHeader.topic, payload))
                {
                    reasonCode = PAYLOAD_FORMAT_INVALID;
                    server.onDecodeError(traceId, authorization, reasonCode);
                    server.decoder = decodeIgnoreAll;
                    break decode;
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
                    server.decoder = decodePacketTypeByVersion.get(server.version);
                    progress = publishV4.limit();
                }
            }
        }

        if (reasonCode != SUCCESS)
        {
            server.onDecodeError(traceId, authorization, reasonCode);
            server.decoder = decodeIgnoreAll;
        }

        return progress;
    }

    private int decodePublishV5(
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
        int reasonCode = SUCCESS;

        decode:
        if (length >= server.decodeablePacketBytes)
        {
            final MqttPublishHeader mqttPublishHeader = mqttPublishHeaderRO.reset();
            final MqttPublishV5FW publishV5 = mqttPublishV5RO.tryWrap(buffer, offset, offset + server.decodeablePacketBytes);
            if (publishV5 == null)
            {
                reasonCode = PROTOCOL_ERROR;
                break decode;
            }

            reasonCode = mqttPublishHeader.decodeV5(server, publishV5.topicName(), publishV5.properties(),
                publishV5.typeAndFlags());

            final OctetsFW payload = publishV5.payload();

            if (mqttPublishHeaderRO.payloadFormat.equals(MqttPayloadFormat.TEXT) && invalidUtf8(payload))
            {
                reasonCode = PAYLOAD_FORMAT_INVALID;
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
                break decode;
            }

            if (reasonCode == SUCCESS)
            {
                final String topic = mqttPublishHeader.topic;
                final int topicKey = topicKey(topic);
                MqttServer.MqttPublishStream publisher = server.publishes.get(topicKey);

                if (publisher == null)
                {
                    publisher = server.resolvePublishStream(traceId, authorization, topic);
                    if (publisher == null)
                    {
                        server.decodePublisherKey = 0;
                        server.decodeablePacketBytes = 0;
                        server.decoder = decodePacketTypeByVersion.get(server.version);
                        progress = publishV5.limit();
                        break decode;
                    }
                }

                server.decodePublisherKey = topicKey;

                final int payloadSize = payload.sizeof();

                if (validators != null && !validContent(mqttPublishHeader.topic, payload))
                {
                    reasonCode = PAYLOAD_FORMAT_INVALID;
                    server.onDecodeError(traceId, authorization, reasonCode);
                    server.decoder = decodeIgnoreAll;
                    break decode;
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
                    server.decoder = decodePacketTypeByVersion.get(server.version);
                    progress = publishV5.limit();
                }
            }
        }

        if (reasonCode != SUCCESS)
        {
            server.onDecodeError(traceId, authorization, reasonCode);
            server.decoder = decodeIgnoreAll;
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
            final MqttPacketHeaderFW publishHeader = mqttPacketHeaderRO.tryWrap(buffer, offset, limit);

            final int typeAndFlags = publishHeader.typeAndFlags();
            final int qos = calculatePublishApplicationQos(typeAndFlags);


            String16FW topicName;
            MqttPropertiesFW properties;
            OctetsFW payload;
            int publishLimit;
            int packetId = -1;
            if (qos > 0)
            {
                final MqttPublishQosFW publish = mqttPublishQosRO.tryWrap(buffer, offset, offset + server.decodeablePacketBytes);
                if (publish == null)
                {
                    reasonCode = PROTOCOL_ERROR;
                    server.onDecodeError(traceId, authorization, reasonCode);
                    server.decoder = decodeIgnoreAll;
                    break decode;
                }

                topicName = publish.topicName();
                properties = publish.properties();
                payload = publish.payload();
                packetId = publish.packetId();
                publishLimit = publish.limit();
            }
            else
            {
                final MqttPublishFW publish = mqttPublishRO.tryWrap(buffer, offset, offset + server.decodeablePacketBytes);
                if (publish == null)
                {
                    reasonCode = PROTOCOL_ERROR;
                    server.onDecodeError(traceId, authorization, reasonCode);
                    server.decoder = decodeIgnoreAll;
                    break decode;
                }

                topicName = publish.topicName();
                properties = publish.properties();
                payload = publish.payload();
                publishLimit = publish.limit();
            }

            final MqttPublishHeader mqttPublishHeader = mqttPublishHeaderRO.reset();


            reasonCode = mqttPublishHeader.decode(server, topicName, properties, typeAndFlags, qos, packetId);

            if (reasonCode == SUCCESS)
            {
                final String topic = mqttPublishHeader.topic;
                final int topicKey = topicKey(topic);
                MqttServer.MqttPublishStream publisher = server.publishes.get(topicKey);

                if (publisher == null)
                {
                    publisher = server.resolvePublishStream(traceId, authorization, topic);
                    if (publisher == null)
                    {
                        server.decodePublisherKey = 0;
                        server.decodeablePacketBytes = 0;
                        server.decoder = decodePacketType;
                        progress = publishLimit;
                        break decode;
                    }
                }

                server.decodePublisherKey = topicKey;

                final int payloadSize = payload.sizeof();

                if (validators != null && !validContent(mqttPublishHeader.topic, payload))
                {
                    reasonCode = PAYLOAD_FORMAT_INVALID;
                    server.onDecodeError(traceId, authorization, reasonCode);
                    server.decoder = decodeIgnoreAll;
                    break decode;
                }

                if (mqttPublishHeaderRO.payloadFormat.equals(MqttPayloadFormat.TEXT) && invalidUtf8(payload))
                {
                    reasonCode = PAYLOAD_FORMAT_INVALID;
                    server.onDecodeError(traceId, authorization, reasonCode);
                    server.decoder = decodeIgnoreAll;
                    break decode;
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
                    server.onDecodePublish(traceId, authorization, reserved, packetId, payload);
                    server.decodeablePacketBytes = 0;
                    server.decoder = decodePacketType;
                    progress = publishLimit;
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

    private int decodePuback(
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

            final MqttPubackFW puback = mqttPubackRO.tryWrap(buffer, offset, limit);
            decode:
            {
                if (puback == null)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }
                else if ((puback.typeAndFlags() & 0b1111_1111) != PUBACK_FIXED_HEADER)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                progress = server.onDecodePuback(traceId, authorization, buffer, progress, limit, puback);
                server.decoder = decodePacketType;
            }

            if (reasonCode != SUCCESS)
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodePubrec(
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

            final MqttPubrecFW pubrec = mqttPubrecRO.tryWrap(buffer, offset, limit);
            decode:
            {
                if (pubrec == null)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }
                else if ((pubrec.typeAndFlags() & 0b1111_1111) != PUBREC_FIXED_HEADER)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                progress = server.onDecodePubrec(traceId, authorization, buffer, progress, limit, pubrec);
                server.decoder = decodePacketType;
            }

            if (reasonCode != SUCCESS)
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodePubrel(
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

            final MqttPubrelFW pubrel = mqttPubrelRO.tryWrap(buffer, offset, limit);
            decode:
            {
                if (pubrel == null)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }
                else if ((pubrel.typeAndFlags() & 0b1111_1111) != PUBREL_FIXED_HEADER)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                progress = server.onDecodePubrel(traceId, authorization, buffer, progress, limit, pubrel);
                server.decoder = decodePacketType;
            }

            if (reasonCode != SUCCESS)
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodePubcomp(
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

            final MqttPubcompFW pubcomp = mqttPubcompRO.tryWrap(buffer, offset, limit);
            decode:
            {
                if (pubcomp == null)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }
                else if ((pubcomp.typeAndFlags() & 0b1111_1111) != PUBCOMP_FIXED_HEADER)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                progress = server.onDecodePubcomp(traceId, authorization, buffer, progress, limit, pubcomp);
                server.decoder = decodePacketType;
            }

            if (reasonCode != SUCCESS)
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private boolean validContent(
        String topic,
        OctetsFW payload)
    {
        final Validator contentValidator = validators.get(topic);
        return contentValidator == null || contentValidator.write(payload.value(), payload.offset(), payload.sizeof());
    }

    private boolean invalidUtf8(
        OctetsFW payload)
    {
        boolean invalid = false;
        byte[] payloadBytes = charsetBuffer.array();
        final int payloadSize = payload.sizeof();
        payload.value().getBytes(0, payloadBytes, 0, payloadSize);
        try
        {
            charsetBuffer.position(0).limit(payloadSize);
            utf8Decoder.decode(charsetBuffer);
        }
        catch (CharacterCodingException ex)
        {
            invalid = true;
            utf8Decoder.reset();
        }
        return invalid;
    }

    private int decodeSubscribeV4(
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
        if (length > 0)
        {
            int reasonCode = SUCCESS;

            final MqttSubscribeV4FW subscribeV4 =
                mqttSubscribeV4RO.tryWrap(buffer, offset, offset + server.decodeablePacketBytes);
            if (subscribeV4 == null)
            {
                reasonCode = PROTOCOL_ERROR;
            }
            else if ((subscribeV4.typeAndFlags() & 0b1111_1111) != SUBSCRIBE_FIXED_HEADER)
            {
                reasonCode = MALFORMED_PACKET;
            }

            if (reasonCode == 0)
            {
                if (!MqttState.replyOpened(server.session.state))
                {
                    //We don't know the server capabilities yet
                    break decode;
                }
                server.onDecodeSubscribeV4(traceId, authorization, subscribeV4);
                server.decoder = decodePacketTypeByVersion.get(server.version);
                progress = subscribeV4.limit();
            }
            else
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodeSubscribeV5(
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
        if (length > 0)
        {
            int reasonCode = SUCCESS;

            final MqttSubscribeV5FW subscribeV5 =
                mqttSubscribeV5RO.tryWrap(buffer, offset, offset + server.decodeablePacketBytes);
            if (subscribeV5 == null)
            {
                reasonCode = PROTOCOL_ERROR;
            }
            else if ((subscribeV5.typeAndFlags() & 0b1111_1111) != SUBSCRIBE_FIXED_HEADER)
            {
                reasonCode = MALFORMED_PACKET;
            }

            if (reasonCode == 0)
            {
                if (!MqttState.replyOpened(server.session.state))
                {
                    //We don't know the server capabilities yet
                    break decode;
                }
                server.onDecodeSubscribeV5(traceId, authorization, subscribeV5);
                server.decoder = decodePacketTypeByVersion.get(server.version);
                progress = subscribeV5.limit();
            }
            else
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodeUnsubscribeV4(
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

            final MqttUnsubscribeV4FW unsubscribeV4 = mqttUnsubscribeV4RO.tryWrap(buffer, offset,
                offset + server.decodeablePacketBytes);
            if (unsubscribeV4 == null || unsubscribeV4.payload().sizeof() == 0 || unsubscribeV4.packetId() == 0)
            {
                reasonCode = PROTOCOL_ERROR;
            }
            else if ((unsubscribeV4.typeAndFlags() & 0b1111_1111) != UNSUBSCRIBE_FIXED_HEADER)
            {
                reasonCode = MALFORMED_PACKET;
            }

            if (reasonCode == 0)
            {
                server.onDecodeUnsubscribe(traceId, authorization, unsubscribeV4.packetId(), unsubscribeV4.payload());
                server.decoder = decodePacketTypeByVersion.get(server.version);
                progress = unsubscribeV4.limit();
            }
            else
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodeUnsubscribeV5(
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

            final MqttUnsubscribeV5FW unsubscribeV5 = mqttUnsubscribeV5RO.tryWrap(buffer, offset,
                offset + server.decodeablePacketBytes);
            if (unsubscribeV5 == null || unsubscribeV5.payload().sizeof() == 0 || unsubscribeV5.packetId() == 0)
            {
                reasonCode = PROTOCOL_ERROR;
            }
            else if ((unsubscribeV5.typeAndFlags() & 0b1111_1111) != UNSUBSCRIBE_FIXED_HEADER)
            {
                reasonCode = MALFORMED_PACKET;
            }

            if (reasonCode == 0)
            {
                server.onDecodeUnsubscribe(traceId, authorization, unsubscribeV5.packetId(), unsubscribeV5.payload());
                server.decoder = decodePacketTypeByVersion.get(server.version);
                progress = unsubscribeV5.limit();
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
                server.decoder = decodePacketTypeByVersion.get(server.version);
                progress = ping.limit();
            }
        }

        return progress;
    }

    private int decodeDisconnectV4(
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

            final MqttDisconnectV4FW disconnectV4 = mqttDisconnectV4RO.tryWrap(buffer, offset, limit);
            if (disconnectV4 == null)
            {
                reasonCode = PROTOCOL_ERROR;
            }
            else if ((disconnectV4.typeAndFlags() & 0b1111_1111) != DISCONNECT_FIXED_HEADER)
            {
                reasonCode = MALFORMED_PACKET;
            }

            if (reasonCode == 0)
            {
                server.onDecodeDisconnectV4(traceId, authorization);
                server.decoder = decodePacketTypeByVersion.get(server.version);
                progress = disconnectV4.limit();
            }
            else
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodeDisconnectV5(
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
        if (length > 0)
        {
            int reasonCode = NORMAL_DISCONNECT;

            if (limit - offset == 2)
            {
                server.onDecodeDisconnectV5(traceId, authorization, null);
                progress = limit;
                server.decoder = decodePacketTypeByVersion.get(server.version);
                break decode;
            }

            final MqttDisconnectV5FW disconnectV5 = mqttDisconnectV5RO.tryWrap(buffer, offset, limit);
            if (disconnectV5 == null)
            {
                reasonCode = PROTOCOL_ERROR;
            }
            else if ((disconnectV5.typeAndFlags() & 0b1111_1111) != DISCONNECT_FIXED_HEADER)
            {
                reasonCode = MALFORMED_PACKET;
            }

            if (reasonCode == 0)
            {
                server.onDecodeDisconnectV5(traceId, authorization, disconnectV5);
                server.decoder = decodePacketTypeByVersion.get(server.version);
                progress = disconnectV5.limit();
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
        private final long encodeBudgetId;

        private final Int2ObjectHashMap<MqttPublishStream> publishes;
        private final Long2ObjectHashMap<Int2ObjectHashMap<MqttSubscribeStream>> subscribes;
        private final Int2ObjectHashMap<String> topicAliases;
        private final Int2IntHashMap subscribePacketIds;
        private final Object2IntHashMap<String> unsubscribePacketIds;
        private final GuardHandler guard;
        private final Function<String, String> credentials;
        private final MqttConnectProperty authField;

        private MqttSessionStream session;

        private String16FW clientId;

        private long affinity;
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

        private int maximumQos;
        private int packetSizeMax;
        private int capabilities = RETAIN_AVAILABLE_MASK | SUBSCRIPTION_IDS_AVAILABLE_MASK | WILDCARD_AVAILABLE_MASK;
        private boolean serverDefinedKeepAlive = false;
        private short keepAlive;
        private long keepAliveTimeout;
        private int connectFlags;
        private boolean connected;

        private short topicAliasMaximum = 0;
        private int connectSessionExpiry = 0;
        private int sessionExpiry;
        private boolean assignedClientId = false;
        private int decodablePropertyMask = 0;

        private int state;
        private long sessionId;
        private int decodableRemainingBytes;
        private final Int2ObjectHashMap<MqttSubscribeStream> qos1Subscribes;
        private final Int2ObjectHashMap<MqttSubscribeStream> qos2Subscribes;
        private final LinkedHashMap<Long, Integer> unAckedReceivedQos1PacketIds;
        private final LinkedHashMap<Long, Integer> unAckedReceivedQos2PacketIds;

        private IntArrayList unreleasedPacketIds;

        private int version = MQTT_PROTOCOL_VERSION_5;

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
            this.encodeBudgetId = budgetId;
            this.decoder = decodeInitialType;
            this.publishes = new Int2ObjectHashMap<>();
            this.subscribes = new Long2ObjectHashMap<>();
            this.topicAliases = new Int2ObjectHashMap<>();
            this.subscribePacketIds = new Int2IntHashMap(-1);
            this.unsubscribePacketIds = new Object2IntHashMap<>(-1);
            this.unAckedReceivedQos1PacketIds = new LinkedHashMap<>();
            this.unAckedReceivedQos2PacketIds = new LinkedHashMap<>();
            this.qos1Subscribes = new Int2ObjectHashMap<>();
            this.qos2Subscribes = new Int2ObjectHashMap<>();
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
            final long streamId = begin.streamId();

            state = MqttState.openingInitial(state);
            affinity = streamId;

            doNetworkBegin(traceId, authorization);
            doNetworkWindow(traceId, authorization, 0, 0L, 0, bufferPool.slotCapacity());
            doSignalConnectTimeout(traceId);
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
                session.doSessionAbort(traceId);
                onDecodeError(traceId, authorization, KEEP_ALIVE_TIMEOUT);
                decoder = decodeIgnoreAll;
            }
            else
            {
                keepAliveTimeoutId =
                    signaler.signalAt(keepAliveTimeoutAt, originId, routedId, replyId, traceId,
                        KEEP_ALIVE_TIMEOUT_SIGNAL, 0);
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
                    if (isSetTopicAliasMaximum(decodablePropertyMask))
                    {
                        topicAliasMaximum = 0;
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.decodablePropertyMask |= CONNECT_TOPIC_ALIAS_MAXIMUM_MASK;
                    final short topicAliasMaximum = (short) (mqttProperty.topicAliasMaximum() & 0xFFFF);
                    this.topicAliasMaximum = (short) Math.min(topicAliasMaximum, topicAliasMaximumLimit);
                    break;
                case KIND_SESSION_EXPIRY:
                    if (isSetSessionExpiryInterval(decodablePropertyMask))
                    {
                        connectSessionExpiry = 0;
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.decodablePropertyMask |= CONNECT_SESSION_EXPIRY_INTERVAL_MASK;
                    this.connectSessionExpiry = (int) mqttProperty.sessionExpiry();
                    this.sessionExpiry = connectSessionExpiry;
                    break;
                case KIND_RECEIVE_MAXIMUM:
                case KIND_MAXIMUM_PACKET_SIZE:
                    final int maxConnectPacketSize = (int) mqttProperty.maximumPacketSize();
                    if (maxConnectPacketSize == 0 || isSetMaximumPacketSize(decodablePropertyMask))
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.decodablePropertyMask |= CONNECT_TOPIC_ALIAS_MAXIMUM_MASK;
                    //TODO: remove this once we will support large messages
                    maximumPacketSize = Math.min(maxConnectPacketSize, maximumPacketSize);
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
            int keepAlive,
            int flags,
            int protocolVersion,
            String16FW clientIdentifier)
        {
            this.assignedClientId = false;

            final int length = clientIdentifier.length();

            if (length == 0)
            {
                this.clientId = supplyClientId.get();
                this.assignedClientId = true;
            }
            else
            {
                this.clientId = new String16FW(clientIdentifier.asString());
            }

            unreleasedPacketIds = unreleasedPacketIdsByClientId.computeIfAbsent(clientId.asString(), c -> new IntArrayList());

            this.keepAlive = (short) Math.min(Math.max(keepAlive, keepAliveMinimum), keepAliveMaximum);
            serverDefinedKeepAlive = this.keepAlive != keepAlive;
            keepAliveTimeout = Math.round(TimeUnit.SECONDS.toMillis(keepAlive) * 1.5);
            connectFlags = flags;
            version = protocolVersion;
            doSignalKeepAliveTimeout(traceId);
            doCancelConnectTimeout();

            return progress;
        }

        private int onDecodeConnectPayload(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int progress,
            int limit)
        {
            byte reasonCode = SUCCESS;
            decode:
            {
                final MqttConnectPayload payload = mqttConnectPayloadRO.reset();
                int connectPayloadLimit = payload.decode(buffer, progress, limit, connectFlags, version);

                final boolean willFlagSet = isSetWillFlag(connectFlags);

                reasonCode = payload.reasonCode;

                if (reasonCode != SUCCESS)
                {
                    break decode;
                }

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

                final MqttRouteConfig resolved = binding != null ?
                    binding.resolveSession(sessionAuth, clientId.asString()) : null;

                if (resolved == null)
                {
                    reasonCode = BAD_USER_NAME_OR_PASSWORD;
                    break decode;
                }

                this.sessionId = sessionAuth;

                this.session = new MqttSessionStream(originId, resolved.id, 0);

                final MqttBeginExFW.Builder builder = mqttSessionBeginExRW.wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                    .typeId(mqttTypeId)
                    .session(s -> s
                        .flags(connectFlags & (CLEAN_START_FLAG_MASK | WILL_FLAG_MASK))
                        .expiry(sessionExpiry)
                        .clientId(clientId)
                    );
                session.doSessionBegin(traceId, affinity, builder.build());

                if (willFlagSet)
                {
                    decoder = decodeConnectWillMessage;
                }
                else
                {
                    progress = connectPayloadLimit;
                }
            }

            if (reasonCode != SUCCESS)
            {
                doCancelConnectTimeout();

                if (reasonCode != BAD_USER_NAME_OR_PASSWORD)
                {
                    onDecodeError(traceId, authorization, reasonCode);
                    decoder = decodeIgnoreAll;
                }

                if (session != null)
                {
                    session.doSessionAppEnd(traceId, EMPTY_OCTETS);
                }
                doNetworkEnd(traceId, authorization);

                decoder = decodeIgnoreAll;
                progress = limit;
            }

            return progress;
        }

        private int onDecodeConnectWillMessage(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int progress,
            int limit)
        {
            byte reasonCode = SUCCESS;
            decode:
            {
                final MqttConnectPayload payload = mqttConnectPayloadRO.reset();
                int connectPayloadLimit = payload.decode(buffer, progress, limit, connectFlags, version);

                final boolean willFlagSet = isSetWillFlag(connectFlags);

                reasonCode = payload.reasonCode;

                if (reasonCode != SUCCESS)
                {
                    break decode;
                }

                if (willFlagSet && !MqttState.initialOpened(session.state))
                {
                    break decode;
                }

                if (isSetWillRetain(connectFlags))
                {
                    if (!retainAvailable(capabilities))
                    {
                        reasonCode = RETAIN_NOT_SUPPORTED;
                        break decode;
                    }
                    payload.willRetain = (byte) RETAIN_FLAG;
                }

                if (payload.willQos > maximumQos)
                {
                    reasonCode = QOS_NOT_SUPPORTED;
                    break decode;
                }

                final int flags = connectFlags;
                final int willFlags = decodeWillFlags(flags);
                final int willQos = decodeWillQos(flags);

                if (willFlagSet)
                {
                    final MqttDataExFW.Builder sessionDataExBuilder =
                        mqttSessionDataExRW.wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                            .typeId(mqttTypeId)
                            .session(s -> s.kind(k -> k.set(MqttSessionDataKind.WILL)));

                    final MqttWillMessageFW.Builder willMessageBuilder =
                        mqttWillMessageRW.wrap(willMessageBuffer, 0, willMessageBuffer.capacity())
                            .topic(payload.willTopic)
                            .delay(payload.willDelay)
                            .qos(willQos)
                            .flags(willFlags)
                            .expiryInterval(payload.expiryInterval)
                            .contentType(payload.contentType)
                            .format(f -> f.set(payload.payloadFormat))
                            .responseTopic(payload.responseTopic)
                            .correlation(c -> c.bytes(payload.correlationData));

                    if (version == 5)
                    {
                        final Array32FW<MqttUserPropertyFW> userProperties = willUserPropertiesRW.build();
                        userProperties.forEach(
                            c -> willMessageBuilder.propertiesItem(p -> p.key(c.key()).value(c.value())));
                    }
                    willMessageBuilder.payload(p -> p.bytes(payload.willPayload.bytes()));

                    final MqttWillMessageFW will = willMessageBuilder.build();
                    final int willPayloadSize = willMessageBuilder.sizeof();

                    if (!session.hasSessionWindow(willPayloadSize))
                    {
                        break decode;
                    }
                    session.doSessionData(traceId, willPayloadSize, sessionDataExBuilder.build(), will);
                }
                progress = connectPayloadLimit;
            }

            if (reasonCode != SUCCESS)
            {
                doCancelConnectTimeout();

                if (reasonCode != BAD_USER_NAME_OR_PASSWORD)
                {
                    doEncodeConnack(traceId, authorization, reasonCode, assignedClientId, false, null, version);
                }

                if (session != null)
                {
                    session.doSessionAppEnd(traceId, EMPTY_OCTETS);
                }
                doNetworkEnd(traceId, authorization);

                decoder = decodeIgnoreAll;
                progress = limit;
            }


            return progress;
        }

        private MqttPublishStream resolvePublishStream(
            long traceId,
            long authorization,
            String topic)
        {
            MqttPublishStream stream = null;

            final MqttBindingConfig binding = bindings.get(routedId);
            final MqttRouteConfig resolved = binding != null ?
                binding.resolvePublish(sessionId, topic) : null;

            if (resolved != null)
            {
                final long resolvedId = resolved.id;
                final int topicKey = topicKey(topic);

                stream = publishes.computeIfAbsent(topicKey, s -> new MqttPublishStream(routedId, resolvedId, topic));
                stream.doPublishBegin(traceId, affinity);
            }
            else
            {
                onDecodeError(traceId, authorization, IMPLEMENTATION_SPECIFIC_ERROR);
                decoder = decodeIgnoreAll;
            }

            return stream;
        }

        private void onDecodePublish(
            long traceId,
            long authorization,
            int reserved,
            int packetId,
            OctetsFW payload)
        {
            int reasonCode = SUCCESS;
            if (mqttPublishHeaderRO.qos > maximumQos)
            {
                reasonCode = QOS_NOT_SUPPORTED;
            }
            else if (mqttPublishHeaderRO.retained && !retainAvailable(capabilities))
            {
                reasonCode = RETAIN_NOT_SUPPORTED;
            }

            if (reasonCode != SUCCESS)
            {
                onDecodeError(traceId, authorization, reasonCode);
                decoder = decodeIgnoreAll;
            }
            else
            {
                if (!unreleasedPacketIds.contains(mqttPublishHeaderRO.packetId))
                {
                    if (mqttPublishHeaderRO.qos == 2)
                    {
                        unreleasedPacketIds.add(mqttPublishHeaderRO.packetId);
                    }

                    final int topicKey = topicKey(mqttPublishHeaderRO.topic);
                    MqttPublishStream stream = publishes.get(topicKey);

                final MqttDataExFW.Builder builder = mqttPublishDataExRW.wrap(dataExtBuffer, 0, dataExtBuffer.capacity())
                    .typeId(mqttTypeId)
                    .publish(p ->
                    {
                        p.qos(mqttPublishHeaderRO.qos)
                            .flags(mqttPublishHeaderRO.flags)
                            .expiryInterval(mqttPublishHeaderRO.expiryInterval)
                            .contentType(mqttPublishHeaderRO.contentType)
                            .format(f -> f.set(mqttPublishHeaderRO.payloadFormat))
                            .responseTopic(mqttPublishHeaderRO.responseTopic)
                            .correlation(c -> c.bytes(mqttPublishHeaderRO.correlationData));
                        if (userPropertiesRW.buffer() != null)
                        {
                            final Array32FW<MqttUserPropertyFW> userProperties = userPropertiesRW.build();
                            userProperties.forEach(c -> p.propertiesItem(pi -> pi.key(c.key()).value(c.value())));
                        }
                    });


                    final MqttDataExFW dataEx = builder.build();
                    if (stream != null)
                    {
                        stream.doPublishData(traceId, reserved, packetId, payload, dataEx);
                    }
                }
                else
                {
                    doEncodePubrec(traceId, authorization, packetId);
                }
                doSignalKeepAliveTimeout(traceId);
            }
        }

        //TODO change method order
        private void onDecodeSubscribeV4(
            long traceId,
            long authorization,
            MqttSubscribeV4FW subscribe)
        {
            final int packetId = subscribe.packetId();
            final OctetsFW decodePayload = subscribe.payload();

            final DirectBuffer decodeBuffer = decodePayload.buffer();
            final int decodeOffset = decodePayload.offset();
            final int decodeLimit = decodePayload.limit();

            final List<Subscription> newSubscriptions = new ArrayList<>();
            final int subscriptionId = supplySubscriptionId.getAsInt();

            decode:
            {
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

                    final int options = mqttSubscribePayload.options();

                    if (options > MqttQoS.EXACTLY_ONCE.value())
                    {
                        onDecodeError(traceId, authorization, PROTOCOL_ERROR);
                        decoder = decodeIgnoreAll;
                        break;
                    }

                    Subscription subscription = new Subscription();
                    subscription.id = subscriptionId;
                    subscription.filter = filter;
                    subscription.flags = SEND_RETAINED_FLAG;
                    subscription.qos = options;
                    subscribePacketIds.put(subscriptionId, packetId);

                    newSubscriptions.add(subscription);
                }

                final MqttDataExFW.Builder sessionDataExBuilder =
                    mqttSessionDataExRW.wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                        .typeId(mqttTypeId)
                        .session(sessionBuilder -> sessionBuilder.kind(k -> k.set(MqttSessionDataKind.STATE)));

                final MqttSessionStateFW.Builder state =
                    mqttSessionStateFW.wrap(sessionStateBuffer, 0, sessionStateBuffer.capacity());

                session.unAckedSubscriptions.addAll(newSubscriptions);
                session.subscriptions.forEach(sub ->
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

                if (!session.hasSessionWindow(payloadSize))
                {
                    break decode;
                }
                session.doSessionData(traceId, payloadSize, sessionDataExBuilder.build(), sessionState);
            }
            doSignalKeepAliveTimeout(traceId);
        }

        private int onDecodePuback(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int progress,
            int limit,
            MqttPubackFW puback)
        {
            final int packetId = puback.packetId();

            MqttSubscribeStream stream = qos1Subscribes.remove(packetId);
            stream.doSubscribeWindow(traceId, encodeSlotOffset, encodeBudgetMax);
            stream.doSubscribeFlush(traceId, 0, MqttQoS.AT_LEAST_ONCE.ordinal(), packetId);

            progress = puback.limit();
            return progress;
        }
        private int onDecodePubrec(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int progress,
            int limit,
            MqttPubrecFW pubrec)
        {
            final int packetId = pubrec.packetId();

            qos2Subscribes.get(packetId)
                .doSubscribeFlush(traceId, 0, MqttQoS.EXACTLY_ONCE.ordinal(), packetId, QOS2_INCOMPLETE_OFFSET_STATE);

            progress = pubrec.limit();
            return progress;
        }

        private int onDecodePubrel(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int progress,
            int limit,
            MqttPubrelFW pubrel)
        {
            final int packetId = pubrel.packetId();

            unreleasedPacketIds.removeInt(packetId);
            doEncodePubcomp(traceId, authorization, packetId);

            progress = pubrel.limit();
            return progress;
        }

        private int onDecodePubcomp(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int progress,
            int limit,
            MqttPubcompFW pubcomp)
        {
            final int packetId = pubcomp.packetId();
            MqttSubscribeStream stream = qos2Subscribes.remove(packetId);
            stream.doSubscribeWindow(traceId, encodeSlotOffset, encodeBudgetMax);
            stream.doSubscribeFlush(traceId, 0, MqttQoS.EXACTLY_ONCE.ordinal(), packetId, QOS2_COMPLETE_OFFSET_STATE);

            progress = pubcomp.limit();
            return progress;
        }

        private void onDecodeSubscribeV5(
            long traceId,
            long authorization,
            MqttSubscribeV5FW subscribe)
        {
            final int packetId = subscribe.packetId();
            final OctetsFW decodePayload = subscribe.payload();

            final DirectBuffer decodeBuffer = decodePayload.buffer();
            final int decodeOffset = decodePayload.offset();
            final int decodeLimit = decodePayload.limit();

            int subscriptionId = 0;
            boolean containsSubscriptionId = false;

            MqttPropertiesFW properties = subscribe.properties();
            final OctetsFW propertiesValue = properties.value();
            final DirectBuffer propertiesBuffer = decodePayload.buffer();
            final int propertiesOffset = propertiesValue.offset();
            final int propertiesLimit = propertiesValue.limit();

            MqttPropertyFW mqttProperty;
            for (int propertiesProgress = propertiesOffset;
                 propertiesProgress < propertiesLimit; propertiesProgress = mqttProperty.limit())
            {
                mqttProperty = mqttPropertyRO.tryWrap(propertiesBuffer, propertiesProgress, propertiesLimit);
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

                if (!containsSubscriptionId)
                {
                    subscriptionId = supplySubscriptionId.getAsInt();
                }

                decode:
                {
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
                        if (!wildcardAvailable(capabilities) && (filter.contains("+") || filter.contains("#")))
                        {
                            onDecodeError(traceId, authorization, WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED);
                            decoder = decodeIgnoreAll;
                            break;
                        }

                        if (!sharedSubscriptionAvailable(capabilities) && filter.contains(SHARED_SUBSCRIPTION_LITERAL))
                        {
                            onDecodeError(traceId, authorization, SHARED_SUBSCRIPTION_NOT_SUPPORTED);
                            decoder = decodeIgnoreAll;
                            break;
                        }

                        if (!subscriptionIdsAvailable(capabilities) && containsSubscriptionId)
                        {
                            onDecodeError(traceId, authorization, SUBSCRIPTION_IDS_NOT_SUPPORTED);
                            decoder = decodeIgnoreAll;
                            break;
                        }

                        final int options = mqttSubscribePayload.options();
                        final int flags = calculateSubscribeFlags(traceId, authorization, options & ~SUBSCRIPTION_QOS_MASK);

                        final int qos = options & SUBSCRIPTION_QOS_MASK;

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
                        subscription.qos = qos;
                        subscribePacketIds.put(subscriptionId, packetId);

                        newSubscriptions.add(subscription);
                    }

                    final MqttDataExFW.Builder sessionDataExBuilder =
                        mqttSessionDataExRW.wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                            .typeId(mqttTypeId)
                            .session(sessionBuilder -> sessionBuilder.kind(k -> k.set(MqttSessionDataKind.STATE)));

                    final MqttSessionStateFW.Builder state =
                        mqttSessionStateFW.wrap(sessionStateBuffer, 0, sessionStateBuffer.capacity());

                    session.unAckedSubscriptions.addAll(newSubscriptions);
                    session.subscriptions.forEach(sub ->
                        state.subscriptionsItem(subscriptionBuilder ->
                            subscriptionBuilder
                                .subscriptionId(sub.id)
                                .qos(sub.qos)
                                .flags(sub.flags)
                                .pattern(sub.filter))
                    );

                    newSubscriptions.forEach(sub ->
                        state.subscriptionsItem(subscriptionBuilder ->
                            subscriptionBuilder
                                .subscriptionId(sub.id)
                                .qos(sub.qos)
                                .flags(sub.flags)
                                .pattern(sub.filter))
                    );

                    final MqttSessionStateFW sessionState = state.build();
                    final int payloadSize = sessionState.sizeof();

                    if (!session.hasSessionWindow(payloadSize))
                    {
                        break decode;
                    }
                    session.doSessionData(traceId, payloadSize, sessionDataExBuilder.build(), sessionState);
                }
            }
            doSignalKeepAliveTimeout(traceId);
        }

        private void openSubscribeStreams(
            int packetId,
            long traceId,
            long authorization,
            List<Subscription> subscriptions,
            boolean implicitSubscribe)
        {
            final Long2ObjectHashMap<List<Subscription>> subscriptionsByRouteId = new Long2ObjectHashMap<>();

            suback:
            {
                for (Subscription subscription : subscriptions)
                {
                    final MqttBindingConfig binding = bindings.get(routedId);
                    final MqttRouteConfig resolved =
                        binding != null ? binding.resolveSubscribe(sessionId, subscription.filter) : null;

                    if (resolved != null)
                    {
                        subscriptionsByRouteId.computeIfAbsent(resolved.id, s -> new ArrayList<>()).add(subscription);
                    }
                    else
                    {
                        onDecodeError(traceId, authorization, IMPLEMENTATION_SPECIFIC_ERROR);
                        decoder = decodeIgnoreAll;
                        break suback;
                    }
                }

                if (!implicitSubscribe)
                {
                    final byte[] subscriptionPayload = new byte[subscriptions.size()];
                    for (int i = 0; i < subscriptionPayload.length; i++)
                    {
                        byte reasonCode = (byte) subscriptions.get(i).reasonCode;
                        if (version == 4 && (reasonCode & 0xff) > GRANTED_QOS_2)
                        {
                            reasonCode = SUBACK_FAILURE_CODE_V4;
                        }
                        subscriptionPayload[i] = reasonCode == 0 ? (byte) subscription.qos : reasonCode;
                    }

                    switch (version)
                    {
                    case 4:
                        doEncodeSubackV4(traceId, sessionId, packetId, subscriptionPayload);
                        break;
                    case 5:
                        doEncodeSubackV5(traceId, sessionId, packetId, subscriptionPayload);
                        break;
                    }
                }

                subscriptionsByRouteId.forEach((key, value) ->
                {
                    Int2ObjectHashMap<MqttSubscribeStream> routeSubscribes =
                        subscribes.computeIfAbsent(key, s -> new Int2ObjectHashMap<>());

                    value.stream()
                        .collect(Collectors.groupingBy(Subscription::qos))
                        .forEach((maxQos, subscriptionList) ->
                        {
                            for (int level = 0; level <= maxQos; level++)
                            {
                                int qos = level;
                                MqttSubscribeStream stream = routeSubscribes.computeIfAbsent(qos,
                                    s -> new MqttSubscribeStream(routedId, key, implicitSubscribe, qos));
                                stream.packetId = packetId;
                                subscriptionList.removeIf(s -> s.reasonCode > GRANTED_QOS_2);
                                stream.doSubscribeBeginOrFlush(traceId, affinity, subscriptionList);
                            }
                        });
                });
            }
        }

        private void onDecodeUnsubscribe(
            long traceId,
            long authorization,
            int packetId,
            OctetsFW decodePayload)
        {
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
                List<Subscription> unAckedSubscriptions = session.unAckedSubscriptions.stream()
                    .filter(s -> topicFilters.contains(s.filter) && subscribePacketIds.containsKey(s.id))
                    .collect(Collectors.toList());

                if (!unAckedSubscriptions.isEmpty())
                {
                    session.deferredUnsubscribes.put(packetId, topicFilters);
                    return;
                }
                boolean matchingSubscription = topicFilters.stream().anyMatch(tf ->
                    session.subscriptions.stream().anyMatch(s -> s.filter.equals(tf)));
                if (matchingSubscription)
                {
                    topicFilters.forEach(filter -> unsubscribePacketIds.put(filter, packetId));
                    doSendSessionState(traceId, topicFilters);
                }
                else
                {
                    sendUnsuback(packetId, traceId, authorization, topicFilters, null, false);
                }
                doSignalKeepAliveTimeout(traceId);
            }
        }

        private void doSendSessionState(
            long traceId,
            List<String> topicFilters)
        {
            final MqttDataExFW.Builder sessionDataExBuilder =
                mqttSessionDataExRW.wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                    .typeId(mqttTypeId)
                    .session(sessionBuilder -> sessionBuilder.kind(k -> k.set(MqttSessionDataKind.STATE)));

            List<Subscription> currentState = session.subscriptions();
            List<Subscription> newState = currentState.stream()
                .filter(subscription -> !topicFilters.contains(subscription.filter))
                .collect(Collectors.toList());

            final MqttSessionStateFW.Builder sessionStateBuilder =
                mqttSessionStateFW.wrap(sessionStateBuffer, 0, sessionStateBuffer.capacity());

            newState.forEach(subscription ->
                sessionStateBuilder.subscriptionsItem(s ->
                    s.subscriptionId(subscription.id)
                        .qos(subscription.qos)
                        .flags(subscription.flags)
                        .pattern(subscription.filter))
            );

            final MqttSessionStateFW sessionState = sessionStateBuilder.build();
            final int payloadSize = sessionState.sizeof();

            session.doSessionData(traceId, payloadSize, sessionDataExBuilder.build(), sessionState);
        }

        private void sendUnsuback(
            int packetId,
            long traceId,
            long authorization,
            List<String> topicFilters,
            List<Subscription> newState,
            boolean adminUnsubscribe)
        {
            final MutableDirectBuffer encodeBuffer = payloadBuffer;
            final int encodeOffset = 0;
            final int encodeLimit = payloadBuffer.capacity();

            int encodeProgress = encodeOffset;

            final Map<MqttSubscribeStream, List<Subscription>> filtersByStream = new HashMap<>();

            for (String topicFilter : topicFilters)
            {
                final MqttBindingConfig binding = bindings.get(routedId);
                final MqttRouteConfig resolved =
                    binding != null ? binding.resolveSubscribe(sessionId, topicFilter) : null;
                final Int2ObjectHashMap<MqttSubscribeStream> streams = subscribes.get(resolved.id);

                int encodeReasonCode = SUCCESS;
                for (MqttSubscribeStream stream : streams.values())
                {
                    Optional<Subscription> subscription = stream.getSubscriptionByFilter(topicFilter, newState);

                    subscription.ifPresent(value -> filtersByStream.computeIfAbsent(stream, s -> new ArrayList<>()).add(value));
                    encodeReasonCode = subscription.isPresent() ? subscription.get().reasonCode : NO_SUBSCRIPTION_EXISTED;
                }

                final MqttUnsubackPayloadFW mqttUnsubackPayload =
                    mqttUnsubackPayloadRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .reasonCode(encodeReasonCode)
                        .build();
                encodeProgress = mqttUnsubackPayload.limit();
            }

            filtersByStream.forEach((stream, subscriptions) -> stream.doSubscribeFlushOrEnd(traceId, subscriptions));

            if (!adminUnsubscribe)
            {
                switch (version)
                {
                case 4:
                    doEncodeUnsubackV4(traceId, authorization, packetId);
                    break;
                case 5:
                    final OctetsFW encodePayload = octetsRO.wrap(encodeBuffer, encodeOffset, encodeProgress);
                    doEncodeUnsubackV5(traceId, authorization, packetId, encodePayload);
                    break;
                }
            }
        }

        private void onDecodePingReq(
            long traceId,
            long authorization,
            MqttPingReqFW ping)
        {
            doSignalKeepAliveTimeout(traceId);
            doEncodePingResp(traceId, authorization);
        }

        private void onDecodeDisconnectV4(
            long traceId,
            long authorization)
        {
            state = MqttState.closingInitial(state);
            closeStreams(traceId, authorization);
            doNetworkEnd(traceId, authorization);
        }

        private void onDecodeDisconnectV5(
            long traceId,
            long authorization,
            MqttDisconnectV5FW disconnect)
        {
            byte reasonCode = disconnect != null ? decodeDisconnectProperties(disconnect.properties()) : SUCCESS;

            if (reasonCode != SUCCESS)
            {
                onDecodeError(traceId, authorization, reasonCode);
                decoder = decodeIgnoreAll;
            }
            else
            {
                if (disconnect != null && disconnect.reasonCode() == DISCONNECT_WITH_WILL_MESSAGE)
                {
                    session.doSessionAbort(traceId);
                }
                else
                {
                    session.doSessionAppEnd(traceId, EMPTY_OCTETS);
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
            onDecodeError(traceId, authorization, reasonCode, version);
        }

        private void onDecodeError(
            long traceId,
            long authorization,
            int reasonCode,
            int version)
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
            if (version != MQTT_PROTOCOL_VERSION_4 || (reasonCode & 0xff)  <= MAX_CONNACK_REASONCODE_V4)
            {
                if (connected || reasonCode == SESSION_TAKEN_OVER)
                {
                    doEncodeDisconnect(traceId, authorization, reasonCode, null);
                }
                else
                {
                    doEncodeConnack(traceId, authorization, reasonCode, false, false, null, version);
                }
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

        private void doEncodePublishV4(
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

                String topicName = subscribeDataEx.subscribe().topic().asString();

                final int topicNameLength = topicName != null ? topicName.length() : 0;

                final int publishApplicationFlags = subscribeDataEx.subscribe().flags();

                final int qos = subscribeDataEx.subscribe().qos();
                final int publishNetworkTypeAndFlags = PUBLISH_TYPE << 4 |
                    calculatePublishNetworkFlags(PUBLISH_TYPE << 4 | publishApplicationFlags, qos);

                final MqttPublishV4FW publish =
                    mqttPublishV4RW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                        .typeAndFlags(publishNetworkTypeAndFlags)
                        .remainingLength(2 + topicNameLength + payloadSize + deferred)
                        .topicName(topicName)
                        .payload(payload)
                        .build();

                doNetworkData(traceId, authorization, 0L, publish);
            }
            else
            {
                doNetworkData(traceId, authorization, 0L, payload);
            }
        }

        private void doEncodePublishV5(
            long traceId,
            long authorization,
            int flags,
            List<Subscription> subscriptions,
            OctetsFW payload,
            MqttDataExFW subscribeDataEx,
            int qos)
        {
            if ((flags & 0x02) != 0)
            {
                final int payloadSize = payload.sizeof();
                final int deferred = subscribeDataEx.subscribe().deferred();
                final int expiryInterval = subscribeDataEx.subscribe().expiryInterval();
                final String16FW contentType = subscribeDataEx.subscribe().contentType();
                final String16FW responseTopic = subscribeDataEx.subscribe().responseTopic();
                final MqttPayloadFormatFW format = subscribeDataEx.subscribe().format();
                final MqttBinaryFW correlation = subscribeDataEx.subscribe().correlation();
                final Array32FW<Varuint32FW> subscriptionIds = subscribeDataEx.subscribe().subscriptionIds();
                final Array32FW<io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttUserPropertyFW> properties =
                    subscribeDataEx.subscribe().properties();

                String topicName = subscribeDataEx.subscribe().topic().asString();

                final int topicNameLength = topicName != null ? topicName.length() : 0;

                AtomicInteger propertiesSize = new AtomicInteger();

                MutableBoolean retainAsPublished = new MutableBoolean(false);

                subscriptionIds.forEach(s ->
                {
                    final int subscriptionId = s.value();
                    if (subscriptionId > 0 && !generatedSubscriptionId(subscriptionId))
                    {
                        Optional<Subscription> result = subscriptions.stream()
                            .filter(subscription -> subscription.id == subscriptionId)
                            .findFirst();
                        retainAsPublished.set(retainAsPublished.value | result.isPresent() && result.get().retainAsPublished());
                        mqttPropertyRW.wrap(propertyBuffer, propertiesSize.get(), propertyBuffer.capacity())
                            .subscriptionId(v -> v.set(subscriptionId));
                        propertiesSize.set(mqttPropertyRW.limit());
                    }
                });

                final int publishApplicationFlags = retainAsPublished.get() ?
                    subscribeDataEx.subscribe().flags() : subscribeDataEx.subscribe().flags() & ~RETAIN_FLAG;

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

                if (!format.get().equals(MqttPayloadFormat.NONE))
                {
                    mqttPropertyRW.wrap(propertyBuffer, propertiesSize.get(), propertyBuffer.capacity())
                        .payloadFormat((byte) subscribeDataEx.subscribe().format().get().ordinal())
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
                if (qos == 0)
                {
                    final MqttPublishV5FW publish =
                        mqttPublishV5RW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
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
                    final int packetId = subscribeDataEx.subscribe().packetId();
                    final MqttPublishQosFW publish =
                        mqttPublishQosRW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                            .typeAndFlags(publishNetworkTypeAndFlags)
                            .remainingLength(5 + topicNameLength + propertiesSize.get() + payloadSize + deferred)
                            .topicName(topicName)
                            .packetId(packetId)
                            .properties(p -> p.length(propertiesSize0)
                                .value(propertyBuffer, 0, propertiesSize0))
                            .payload(payload)
                            .build();

                    doNetworkData(traceId, authorization, 0L, publish);
                }
            }
                    doNetworkData(traceId, authorization, 0L, publish);
                }
            else
            {
                doNetworkData(traceId, authorization, 0L, payload);
            }
        }

        private void doEncodePuback(
            long traceId,
            long authorization,
            int packetId)
        {
            final MqttPubackFW puback = mqttPubackRW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(PUBACK_FIXED_HEADER)
                .remainingLength(4)
                .packetId(packetId)
                .reasonCode(SUCCESS)
                .properties(p -> p.length(0).value(EMPTY_OCTETS))
                .build();

            doNetworkData(traceId, authorization, 0L, puback);
        }

        private void doEncodePubrec(
            long traceId,
            long authorization,
            int packetId)
        {
            final MqttPubrecFW pubrec = mqttPubrecRW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(PUBREC_FIXED_HEADER)
                .remainingLength(4)
                .packetId(packetId)
                .reasonCode(SUCCESS)
                .properties(p -> p.length(0).value(EMPTY_OCTETS))
                .build();

            doNetworkData(traceId, authorization, 0L, pubrec);
        }

        private void doEncodePubrel(
            long traceId,
            long authorization,
            int packetId)
        {
            final MqttPubrelFW pubrel = mqttPubrelRW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(PUBREL_FIXED_HEADER)
                .remainingLength(4)
                .packetId(packetId)
                .reasonCode(SUCCESS)
                .properties(p -> p.length(0).value(EMPTY_OCTETS))
                .build();

            doNetworkData(traceId, authorization, 0L, pubrel);
        }

        private void doEncodePubcomp(
            long traceId,
            long authorization,
            int packetId)
        {
            final MqttPubcompFW pubcomp = mqttPubcompRW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(PUBCOMP_FIXED_HEADER)
                .remainingLength(4)
                .packetId(packetId)
                .reasonCode(SUCCESS)
                .properties(p -> p.length(0).value(EMPTY_OCTETS))
                .build();

            doNetworkData(traceId, authorization, 0L, pubcomp);
        }

        private boolean generatedSubscriptionId(
            int subscriptionId)
        {
            return (subscriptionId & GENERATED_SUBSCRIPTION_ID_MASK) == GENERATED_SUBSCRIPTION_ID_MASK;
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
            String16FW serverReference,
            int version)
        {

            switch (version)
            {
            case 4:
                doEncodeConnackV4(traceId, authorization, reasonCode, sessionPresent);
                break;
            case 5:
                doEncodeConnackV5(traceId, authorization, reasonCode, assignedClientId, sessionPresent, serverReference);
                break;
            default:
                doEncodeConnackV5(traceId, authorization, reasonCode, assignedClientId, sessionPresent, serverReference);
                break;
            }

        }

        private void doEncodeConnackV4(
            long traceId,
            long authorization,
            int reasonCode,
            boolean sessionPresent)
        {
            int flags = sessionPresent ? CONNACK_SESSION_PRESENT : 0x00;

            final MqttConnackV4FW connack =
                mqttConnackV4RW.wrap(writeBuffer, FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                    .typeAndFlags(0x20)
                    .remainingLength(2)
                    .flags(flags)
                    .reasonCode(reasonCode & 0xff)
                    .build();

            doNetworkData(traceId, authorization, 0L, connack);
        }

        private void doEncodeConnackV5(
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
                //TODO: remove this once we support large messages
                mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                    .maximumPacketSize(maximumPacketSize)
                    .build();
                propertiesSize = mqttProperty.limit();

                if (connectSessionExpiry != sessionExpiry)
                {
                    mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .sessionExpiry(sessionExpiry)
                        .build();
                    propertiesSize = mqttProperty.limit();
                }

                if (0 <= maximumQos && maximumQos < 2)
                {
                    mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .maximumQoS((byte) maximumQos)
                        .build();
                    propertiesSize = mqttProperty.limit();
                }

                if (!retainAvailable(capabilities))
                {
                    mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .retainAvailable((byte) 0)
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

                if (!subscriptionIdsAvailable(capabilities))
                {
                    mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .subscriptionIdsAvailable((byte) 0)
                        .build();
                    propertiesSize = mqttProperty.limit();
                }

                if (!sharedSubscriptionAvailable(capabilities))
                {
                    mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .sharedSubscriptionAvailable((byte) 0)
                        .build();
                    propertiesSize = mqttProperty.limit();
                }

                if (!wildcardAvailable(capabilities))
                {
                    mqttProperty = mqttPropertyRW.wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .wildcardSubscriptionAvailable((byte) 0)
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
            final MqttConnackV5FW connack =
                mqttConnackV5RW.wrap(writeBuffer, FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                    .typeAndFlags(0x20)
                    .remainingLength(3 + propertiesSize0)
                    .flags(flags)
                    .reasonCode(reasonCode & 0xff)
                    .properties(p -> p.length(propertiesSize0)
                        .value(propertyBuffer, 0, propertiesSize0))
                    .build();

            doNetworkData(traceId, authorization, 0L, connack);
        }

        private void doEncodeSubackV4(
            long traceId,
            long authorization,
            int packetId,
            byte[] subscriptions)
        {
            OctetsFW reasonCodes = octetsRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .put(subscriptions)
                .build();

            final MqttSubackV4FW suback = mqttSubackV4RW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(0x90)
                .remainingLength(2 + reasonCodes.sizeof())
                .packetId(packetId)
                .payload(reasonCodes)
                .build();

            doNetworkData(traceId, authorization, 0L, suback);
        }

        private void doEncodeSubackV5(
            long traceId,
            long authorization,
            int packetId,
            byte[] subscriptions)
        {
            OctetsFW reasonCodes = octetsRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .put(subscriptions)
                .build();

            final MqttSubackV5FW suback = mqttSubackV5RW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(0x90)
                .remainingLength(3 + reasonCodes.sizeof())
                .packetId(packetId)
                .properties(p -> p.length(0).value(EMPTY_OCTETS))
                .payload(reasonCodes)
                .build();

            doNetworkData(traceId, authorization, 0L, suback);
        }

        private void doEncodeUnsubackV4(
            long traceId,
            long authorization,
            int packetId)
        {
            final MqttUnsubackV4FW unsuback =
                mqttUnsubackV4RW.wrap(writeBuffer, FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                    .typeAndFlags(0xb0)
                    .remainingLength(2)
                    .packetId(packetId)
                    .build();

            doNetworkData(traceId, authorization, 0L, unsuback);
        }

        private void doEncodeUnsubackV5(
            long traceId,
            long authorization,
            int packetId,
            OctetsFW payload)
        {
            final MqttUnsubackV5FW unsuback =
                mqttUnsubackV5RW.wrap(writeBuffer, FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
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
            final MqttDisconnectV5FW disconnect =
                mqttDisconnectV5RW.wrap(writeBuffer, FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
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

                if (publishes.isEmpty() && subscribes.isEmpty() && decoder == decodeIgnoreAll)
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
            publishes.values().forEach(s -> s.cleanupAbort(traceId));
            subscribes.values().forEach(ss -> ss.values().forEach(s -> s.cleanupAbort(traceId)));
            if (session != null)
            {
                session.cleanupAbort(traceId);
            }
        }

        private void closeStreams(
            long traceId,
            long authorization)
        {
            publishes.values().forEach(s -> s.doPublishAppEnd(traceId));
            subscribes.values().forEach(ss -> ss.values().forEach(s -> s.doSubscribeAppEnd(traceId)));
            if (session != null)
            {
                session.cleanupEnd(traceId);
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

        private void doSignalKeepAliveTimeout(
            long traceId)
        {
            if (keepAlive > 0)
            {
                keepAliveTimeoutAt = System.currentTimeMillis() + keepAliveTimeout;

                if (keepAliveTimeoutId == NO_CANCEL_ID)
                {
                    keepAliveTimeoutId =
                        signaler.signalAt(keepAliveTimeoutAt, originId, routedId, replyId, traceId,
                            KEEP_ALIVE_TIMEOUT_SIGNAL, 0);
                }
            }
        }

        private void doSignalConnectTimeout(
            long traceId)
        {
            connectTimeoutAt = System.currentTimeMillis() + connectTimeoutMillis;

            if (connectTimeoutId == NO_CANCEL_ID)
            {
                connectTimeoutId = signaler.signalAt(connectTimeoutAt, originId, routedId, replyId,
                    traceId, CONNECT_TIMEOUT_SIGNAL, 0);
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
            private int reasonCode;

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
                    this.qos == other.qos && this.flags == other.flags && this.reasonCode == other.reasonCode;
            }

            @Override
            public int hashCode()
            {
                return Objects.hash(this.id, this.filter, this.qos, this.flags, this.reasonCode);
            }

            public int qos()
            {
                return qos;
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
                final boolean wasOpen = MqttState.initialOpened(state);

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

                if (!wasOpen)
                {
                    decodeNetwork(traceId);
                }

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



                if (mqttResetEx != null)
                {
                    String16FW serverRef = mqttResetEx.serverRef();
                    byte reasonCode = (byte) mqttResetEx.reasonCode();
                    boolean serverRefExists = serverRef != null && serverRef.asString() != null;

                    if (reasonCode == SUCCESS)
                    {
                        reasonCode = serverRefExists ? SERVER_MOVED : SESSION_TAKEN_OVER;
                    }

                    if (!connected)
                    {
                        doCancelConnectTimeout();
                        doEncodeConnack(traceId, authorization, reasonCode, assignedClientId,
                            false, serverRefExists ? serverRef : null, version);
                    }
                    else
                    {
                        doEncodeDisconnect(traceId, authorization, reasonCode, serverRefExists ? serverRef : null);
                    }
                }
                setInitialClosed();
                decodeNetwork(traceId);
                cleanupAbort(traceId);
            }

            private void onSessionBegin(
                BeginFW begin)
            {
                state = MqttState.openReply(state);

                final long traceId = begin.traceId();

                final OctetsFW extension = begin.extension();
                if (extension.sizeof() > 0)
                {
                    final MqttBeginExFW mqttBeginEx = extension.get(mqttBeginExRO::tryWrap);

                    assert mqttBeginEx.kind() == MqttBeginExFW.KIND_SESSION;
                    final MqttSessionBeginExFW mqttSessionBeginEx = mqttBeginEx.session();

                    sessionExpiry = mqttSessionBeginEx.expiry();
                    capabilities = mqttSessionBeginEx.capabilities();
                    maximumQos = mqttSessionBeginEx.qosMax();
                    maximumPacketSize = (int) mqttSessionBeginEx.packetSizeMax();
                }

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
                            if (isCleanStart(connectFlags))
                            {
                                doSessionData(traceId, 0, emptyRO, emptyRO);
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
                                    subscription.qos = filter.qos();
                                    subscriptions.add(subscription);
                                });

                                openSubscribeStreams(0, traceId, authorization, subscriptions, true);
                                sessionPresent = true;
                            }
                        }
                        doEncodeConnack(traceId, authorization, reasonCode, assignedClientId, sessionPresent, null, version);
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
                                subscription.qos = filter.qos();
                                subscription.reasonCode = filter.reasonCode();
                                newState.add(subscription);
                            });
                            List<Subscription> currentSubscriptions = session.subscriptions();
                            if (newState.size() >= currentSubscriptions.size())
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
                                final List<String> unsubscribedFilters = currentSubscriptions.stream()
                                    .filter(s -> !newState.contains(s))
                                    .map(s -> s.filter)
                                    .collect(Collectors.toList());

                                if (!unsubscribedFilters.isEmpty())
                                {
                                    Map<Integer, List<String>> packetIdToFilters = unsubscribedFilters.stream()
                                        .filter(unsubscribePacketIds::containsKey)
                                        .collect(Collectors.groupingBy(unsubscribePacketIds::remove, Collectors.toList()));

                                    if (!packetIdToFilters.isEmpty())
                                    {
                                        packetIdToFilters.forEach((unsubscribePacketId, filters) ->
                                            sendUnsuback(unsubscribePacketId, traceId, authorization, filters, newState, false));
                                    }
                                    else
                                    {
                                        sendUnsuback(packetId, traceId, authorization, unsubscribedFilters, newState, true);
                                    }
                                }
                            }
                            session.setSubscriptions(newState);
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

            private boolean hasSessionWindow(
                int length)
            {
                return initialMax - (initialSeq - initialAck) >= length + initialPad;
            }

            private void doSessionData(
                long traceId,
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
                        traceId, sessionId, budgetId, reserved, buffer, offset, length, dataEx);

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
                    doEnd(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, sessionId, extension);
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

            public void setSubscriptions(List<Subscription> subscriptions)
            {
                this.subscriptions = subscriptions;
            }
            public List<Subscription> subscriptions()
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
                        .publish(p ->
                            p.clientId(clientId)
                                .topic(topic)
                                .flags(retainAvailable(capabilities) ? 1 : 0))
                        .build();

                    application = newStream(this::onPublish, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, sessionId, affinity, beginEx);

                    doSignalPublishExpiration(traceId);
                    doPublishWindow(traceId, 0, 0);
                }
            }

            private void doPublishData(
                long traceId,
                int reserved,
                int packetId,
                OctetsFW payload,
                MqttDataExFW mqttData)
            {
                assert MqttState.initialOpening(state);

                final DirectBuffer buffer = payload.buffer();
                final int offset = payload.offset();
                final int limit = payload.limit();
                final int length = limit - offset;
                assert reserved >= length + initialPad;

                doData(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, sessionId, budgetId, reserved, buffer, offset, length, mqttData);

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;

                final int qos = mqttData.publish().qos();

                if (qos == 1)
                {
                    unAckedReceivedQos1PacketIds.put(initialSeq, packetId);
                }
                else if (qos == 2)
                {
                    unAckedReceivedQos2PacketIds.put(initialSeq, packetId);
                }

                doSignalPublishExpiration(traceId);
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

                acknowledgePublishPackets(acknowledge, traceId, authorization);
            }

            private void acknowledgePublishPackets(
                long acknowledge,
                long traceId,
                long authorization)
            {
                for (Map.Entry<Long, Integer> e : unAckedReceivedQos1PacketIds.entrySet())
                {
                    if (e.getKey() <= acknowledge)
                    {
                        doEncodePuback(traceId, authorization, e.getValue());
                        unAckedReceivedQos1PacketIds.remove(e.getKey());
                    }
                    else
                    {
                        break;
                    }
                }

                for (Map.Entry<Long, Integer> e : unAckedReceivedQos2PacketIds.entrySet())
                {
                    if (e.getKey() <= acknowledge)
                    {
                        doEncodePubrec(traceId, authorization, e.getValue());
                        unAckedReceivedQos2PacketIds.remove(e.getKey());
                    }
                    else
                    {
                        break;
                    }
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
                    onDecodeError(traceId, authorization, IMPLEMENTATION_SPECIFIC_ERROR);
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
                    doSignalPublishExpiration(traceId);
                }
            }

            private void doSignalPublishExpiration(
                long traceId)
            {
                publishExpiresAt = System.currentTimeMillis() + publishTimeoutMillis;

                if (publishExpiresId == NO_CANCEL_ID)
                {
                    publishExpiresId =
                        signaler.signalAt(publishExpiresAt, originId, routedId, initialId, traceId,
                            PUBLISH_EXPIRED_SIGNAL, 0);
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
                    publishes.remove(topicKey);
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
                    publishes.remove(topicKey);
                }
            }


            private void setPublishAppClosed()
            {
                assert !MqttState.replyClosed(state);

                state = MqttState.closeReply(state);

                if (MqttState.closed(state))
                {
                    publishes.remove(topicKey);
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
            private int packetId;
            private final int qos;
            private final boolean adminSubscribe;

            MqttSubscribeStream(
                long originId,
                long routedId,
                boolean adminSubscribe,
                int qos)
            {
                this.originId = originId;
                this.routedId = routedId;
                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.subscriptions = new ArrayList<>();
                this.adminSubscribe = adminSubscribe;
                this.qos = qos;
            }

            private Optional<Subscription> getSubscriptionByFilter(
                String filter,
                List<Subscription> newState)
            {
                return Optional.ofNullable(newState)
                    .flatMap(list -> list.stream().filter(s -> s.filter.equals(filter)).findFirst())
                    .or(() -> subscriptions.stream().filter(s -> s.filter.equals(filter)).findFirst());
            }

            private void doSubscribeBeginOrFlush(
                long traceId,
                long affinity,
                List<Subscription> subscriptions)
            {
                this.subscriptions.addAll(subscriptions);

                if (!MqttState.initialOpening(state))
                {
                    doSubscribeBegin(traceId, affinity);
                }
                else
                {
                    doSubscribeFlush(traceId, 0);
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
                        subscribeBuilder.qos(qos);
                        subscriptions.forEach(subscription ->
                            subscribeBuilder.filtersItem(filterBuilder ->
                            {
                                filterBuilder.subscriptionId(subscription.id);
                                filterBuilder.qos(subscription.qos);
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
                int reserved)
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
                                        filterBuilder.qos(subscription.qos);
                                        filterBuilder.flags(subscription.flags);
                                        filterBuilder.pattern(subscription.filter);
                                    }))))
                        .build()
                        .sizeof()));

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;
            }

            private void doSubscribeFlush(
                long traceId,
                int reserved,
                int qos,
                int packetId)
            {
                doFlush(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, sessionId, 0L, reserved,
                    ex -> ex.set((b, o, l) -> mqttFlushExRW.wrap(b, o, l)
                        .typeId(mqttTypeId)
                        .subscribe(subscribeBuilder -> subscribeBuilder.qos(qos).packetId(packetId))
                        .build()
                        .sizeof()));

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;
            }

            private void doSubscribeFlush(
                long traceId,
                int reserved,
                int qos,
                int packetId,
                int state)
            {
                doFlush(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, sessionId, 0L, reserved,
                    ex -> ex.set((b, o, l) -> mqttFlushExRW.wrap(b, o, l)
                        .typeId(mqttTypeId)
                        .subscribe(subscribeBuilder -> subscribeBuilder.qos(qos).packetId(packetId).state(state))
                        .build()
                        .sizeof()));

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;
            }

            private void doSubscribeFlushOrEnd(
                long traceId,
                List<Subscription> unsubscribed)
            {
                for (Subscription subscription : unsubscribed)
                {
                    if (subscription.reasonCode == SUCCESS)
                    {
                        this.subscriptions.remove(subscription);
                    }
                }
                if (!MqttState.initialOpened(state))
                {
                    state = MqttState.closingInitial(state);
                }
                else
                {
                    doSubscribeFlush(traceId, 0);
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
                    subscribes.remove(routedId);
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
                    final MqttDataExFW subscribeDataEx = extension.get(mqttSubscribeDataExRO::tryWrap);

                    if (payload != null && !subscriptions.isEmpty() && payload.sizeof() <= maximumPacketSize)
                    {
                        switch (version)
                        {
                        case 4:
                            doEncodePublishV4(traceId, authorization, flags, subscriptions, payload, subscribeDataEx, qos);
                            break;
                        case 5:
                            doEncodePublishV5(traceId, authorization, flags, subscriptions, payload, subscribeDataEx, qos);
                            break;
                        }
                    }
                    else
                    {
                        droppedHandler.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
                    }
                    if (qos == 0)
                    {
                        doSubscribeWindow(traceId, encodeSlotOffset, encodeBudgetMax);
                    }
                    else if (qos == 1)
                    {
                        //Save packetId and subscribeStream, so we can ack in the correct stream.
                        qos1Subscribes.put(subscribeDataEx.subscribe().packetId(), this);
                    }
                    else if (qos == 2)
                    {
                        //Save packetId and subscribeStream, so we can ack in the correct stream.
                        qos2Subscribes.put(subscribeDataEx.subscribe().packetId(), this);
                    }
                }
            }

            private void onSubscribeFlush(
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

                final MqttFlushExFW subscribeFlushEx = extension.get(mqttSubscribeFlushExRO::tryWrap);
                final int packetId = subscribeFlushEx.subscribe().packetId();

                doEncodePubrel(traceId, authorization, packetId);
                qos2Subscribes.put(packetId, this);
            }


            private void onSubscribeReset(
                ResetFW reset)
            {
                setNetClosed();

                final long traceId = reset.traceId();
                final long authorization = reset.authorization();

                onDecodeError(traceId, authorization, IMPLEMENTATION_SPECIFIC_ERROR);
                decoder = decodeIgnoreAll;
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
                    if (!session.deferredUnsubscribes.isEmpty())
                    {
                        Iterator<Map.Entry<Integer, List<String>>> iterator =
                            session.deferredUnsubscribes.entrySet().iterator();
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

                        doSendSessionState(traceId, ackedTopicFilters);
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
                    subscribes.remove(routedId);
                }
            }

        }
    }

    private static boolean invalidWillQos(
        int flags)
    {
        return (flags & WILL_QOS_MASK) == WILL_QOS_MASK;
    }

    private static boolean retainAvailable(
        int capabilities)
    {
        return (capabilities & RETAIN_AVAILABLE_MASK) != 0;
    }

    private static boolean wildcardAvailable(
        int capabilities)
    {
        return (capabilities & WILDCARD_AVAILABLE_MASK) != 0;
    }

    private static boolean subscriptionIdsAvailable(
        int capabilities)
    {
        return (capabilities & SUBSCRIPTION_IDS_AVAILABLE_MASK) != 0;
    }

    private static boolean sharedSubscriptionAvailable(
        int capabilities)
    {
        return (capabilities & SHARED_SUBSCRIPTIONS_AVAILABLE_MASK) != 0;
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
        int typeAndFlags,
        int flags)
    {
        int reasonCode = SUCCESS;

        if ((typeAndFlags & 0b1111_1111) != CONNECT_FIXED_HEADER || (flags & CONNECT_RESERVED_MASK) != 0)
        {
            reasonCode = MALFORMED_PACKET;
        }

        return reasonCode;
    }

    private static int decodeConnectProtocol(
        String16FW protocolName,
        int protocolVersion)
    {
        int reasonCode = SUCCESS;

        if (!MQTT_PROTOCOL_NAME.equals(protocolName) ||
            protocolVersion != MQTT_PROTOCOL_VERSION_4 && protocolVersion != MQTT_PROTOCOL_VERSION_5)
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
            int flags,
            int version)
        {
            int progress = offset;
            decode:
            {
                if (isSetWillFlag(flags))
                {
                    switch (version)
                    {
                    case 4:
                        final MqttWillV4FW mqttWillV4 = mqttWillV4RO.tryWrap(buffer, offset, limit);
                        if (mqttWillV4 == null)
                        {
                            reasonCode = MALFORMED_PACKET;
                            break decode;
                        }

                        willTopic = mqttWillV4.topic();
                        willPayload = mqttWillV4.payload();
                        progress = mqttWillV4.limit();
                        break;
                    case 5:
                        final MqttWillV5FW mqttWillV5 = mqttWillV5RO.tryWrap(buffer, offset, limit);
                        if (mqttWillV5 == null)
                        {
                            reasonCode = MALFORMED_PACKET;
                            break decode;
                        }
                        willProperties = mqttWillV5.properties();
                        decode(willProperties);

                        willTopic = mqttWillV5.topic();
                        willPayload = mqttWillV5.payload();
                        progress = mqttWillV5.limit();
                        break;
                    }

                    final byte qos = (byte) ((flags & WILL_QOS_MASK) >>> 3);
                    if (qos != 0)
                    {
                        willQos = (byte) (qos << 1);
                    }

                    if (willTopic == null || willTopic.asString().isEmpty())
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }

                    if (willPayload == null || willPayload.bytes().sizeof() == 0)
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
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
                    if (password == null || version == 4 && !isSetUsername(flags))
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
        private int qos;
        private int packetId;
        private int expiryInterval = DEFAULT_EXPIRY_INTERVAL;
        private String16FW contentType = NULL_STRING;
        private MqttPayloadFormat payloadFormat = DEFAULT_FORMAT;
        private String16FW responseTopic = NULL_STRING;
        private OctetsFW correlationData = null;
        private boolean retained = false;

        private MqttPublishHeader reset()
        {
            this.topic = null;
            this.flags = 0;
            this.qos = 0;
            this.packetId = 0;
            this.expiryInterval = DEFAULT_EXPIRY_INTERVAL;
            this.contentType = NULL_STRING;
            this.payloadFormat = DEFAULT_FORMAT;
            this.responseTopic = NULL_STRING;
            this.correlationData = null;

            return this;
        }

        private int decodeV5(
            MqttServer server,
            String16FW topicName,
            MqttPropertiesFW properties,
            int typeAndFlags,
            int qos,
            int packetId)
        {
            this.topic = topicName.asString();
            this.qos = qos;
            this.packetId = packetId;
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

        private int decodeV4(
            MqttServer server,
            String16FW topicName,
            int typeAndFlags)
        {
            this.topic = topicName.asString();
            int reasonCode = SUCCESS;
            if (topic == null)
            {
                reasonCode = PROTOCOL_ERROR;
            }
            else
            {
                flags = calculatePublishApplicationFlags(typeAndFlags);
                qos = calculatePublishApplicationQos(typeAndFlags);
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

