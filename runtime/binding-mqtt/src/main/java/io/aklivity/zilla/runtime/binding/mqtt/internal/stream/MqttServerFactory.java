/*
 * Copyright 2021-2026 Aklivity Inc.
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
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.GRANTED_QOS_2;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.IDENTIFIER_REJECTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.IMPLEMENTATION_SPECIFIC_ERROR;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.KEEP_ALIVE_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.MALFORMED_PACKET;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.NORMAL_DISCONNECT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.NO_CONNACK;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.NO_SUBSCRIPTION_EXISTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.PACKET_IDENTIFIER_NOT_FOUND;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.PACKET_TOO_LARGE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.PAYLOAD_FORMAT_INVALID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.PROTOCOL_ERROR;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.QOS_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.RETAIN_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SERVER_BUSY;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SERVER_MOVED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SERVER_UNAVAILABLE_V4;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SESSION_TAKEN_OVER;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SHARED_SUBSCRIPTION_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SUBACK_FAILURE_CODE_V4;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SUBSCRIPTION_IDS_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SUCCESS;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.TOPIC_ALIAS_INVALID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.TOPIC_NAME_INVALID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.UNSPECIFIED_ERROR;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.UNSUPPORTED_PROTOCOL_VERSION;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.acknowledgesPublish;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.connackReasonCodeV4;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.isConnackReasonCode;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.isDisconnectReasonCode;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.isError;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPublishFlags.DUP;
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
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_REASON_STRING;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_RECEIVE_MAXIMUM;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_REQUEST_PROBLEM_INFORMATION;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_REQUEST_RESPONSE_INFORMATION;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_RESPONSE_TOPIC;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_SERVER_REFERENCE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_SESSION_EXPIRY;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_SUBSCRIPTION_ID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_TOPIC_ALIAS;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_TOPIC_ALIAS_MAXIMUM;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_USER_PROPERTY;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPropertyFW.KIND_WILL_DELAY_INTERVAL;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.DataFW.FIELD_OFFSET_PAYLOAD;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttPublishDataExFW.Builder.DEFAULT_EXPIRY_INTERVAL;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttPublishDataExFW.Builder.DEFAULT_FORMAT;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static io.aklivity.zilla.runtime.engine.guard.GuardHandler.EXPIRES_NEVER;
import static io.aklivity.zilla.runtime.engine.guard.GuardHandler.MASK_AUTHORIZED;
import static io.aklivity.zilla.runtime.engine.guard.GuardHandler.NOT_AUTHORIZED;
import static io.aklivity.zilla.runtime.engine.util.Flags.COMPLETE;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static io.aklivity.zilla.runtime.engine.util.Flags.NONE;
import static io.aklivity.zilla.runtime.engine.util.Flags.hasFin;
import static io.aklivity.zilla.runtime.engine.util.Flags.hasInit;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.nativeOrder;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.Closeable;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.agrona.BitUtil;
import org.agrona.LangUtil;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongArrayList;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.config.binding.mqtt.MqttPatternConfig.MqttConnectProperty;
import io.aklivity.zilla.config.binding.mqtt.MqttVersion;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.WithConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.InstanceId;
import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttBinding;
import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration;
import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttEventContext;
import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes;
import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttValidator;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttBindingConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttModel;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttRouteConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttBinaryFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPacketState;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPayloadFormatFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttQoS;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSessionStateFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttTopicFilterFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttTopicFilterV1FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttTopicFilterV2FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttWillMessageFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Varint32FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.BinaryFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttAckHeaderFW;
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
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPubackV4FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPubackV5FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPubcompV4FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPubcompV5FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPublishQosV4FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPublishQosV5FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPublishV4FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPublishV5FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPubrecV4FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPubrecV5FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPubrelV4FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.codec.MqttPubrelV5FW;
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
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttEndExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttExtensionKind;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttOffsetStateFlags;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttPublishDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttPublishFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttResetExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttServerCapabilities;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttSessionBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttSessionDataKind;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttSessionFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttSubscribeFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.agrona.collections.Int2ObjectHashMaps;
import io.aklivity.zilla.runtime.common.agrona.collections.IntArrayLists;
import io.aklivity.zilla.runtime.common.agrona.collections.Long2ObjectHashMaps;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.StreamState;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetCredit;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebit;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public final class MqttServerFactory implements MqttStreamFactory
{
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);

    private static final String16FW MQTT_PROTOCOL_NAME = new String16FW("MQTT", BIG_ENDIAN);
    public static final int MQTT_PROTOCOL_VERSION_5 = 5;
    public static final int MQTT_PROTOCOL_VERSION_4 = 4;
    private static final int LEGACY_GENERATED_SUBSCRIPTION_ID_MASK = 0x70;
    private static final int SESSION_EXPIRY_NEVER = -1;
    private static final int CONNACK_FIXED_LENGTH = 3;
    private static final int DISCONNECT_FIXED_LENGTH = 2;
    private static final int MAXIMUM_CLIENT_ID_LENGTH = 256;
    private static final int MAXIMUM_TOPIC_BYTES = 0xffff * 3;
    private static final int NO_PACKET_ID = -1;
    private static final int TOPIC_HASH_SEED = 0x811c9dc5;
    private static final int TOPIC_HASH_PRIME = 0x01000193;
    private static final int PENDING_SUBSCRIBE = 0;
    private static final int PENDING_UNSUBSCRIBE = 1;
    private static final int PENDING_UNSUBSCRIBE_FILTERS = 2;
    private static final int CONNECT_FIXED_HEADER = 0b0001_0000;
    private static final int SUBSCRIBE_FIXED_HEADER = 0b1000_0010;
    private static final int UNSUBSCRIBE_FIXED_HEADER = 0b1010_0010;
    private static final int DISCONNECT_FIXED_HEADER = 0b1110_0000;
    private static final int PINGREQ_FIXED_HEADER = 0b1100_0000;
    private static final long NO_DISCONNECT_SESSION_EXPIRY = -1L;
    private static final int RECEIVE_MAXIMUM_DEFAULT = 65535;
    private static final int PUBACK_FIXED_HEADER = 0b0100_0000;
    private static final int PUBREC_FIXED_HEADER = 0b0101_0000;
    private static final int PUBREL_FIXED_HEADER = 0b0110_0010;
    private static final int PUBCOMP_FIXED_HEADER = 0b0111_0000;

    private static final int CONNECT_RESERVED_MASK = 0b0000_0001;
    private static final int NO_FLAGS = 0b0000_0000;
    private static final int RETAIN_MASK = 0b0000_0001;
    private static final int PUBLISH_QOS1_MASK = 0b0000_0010;
    private static final int PUBLISH_QOS2_MASK = 0b0000_0100;
    private static final int PUBLISH_DUP_MASK = 0b0000_1000;
    private static final int NO_LOCAL_FLAG_MASK = 0b0000_0100;
    private static final int RETAIN_AS_PUBLISHED_MASK = 0b0000_1000;
    private static final int RETAIN_HANDLING_MASK = 0b0011_0000;
    private static final int SUBSCRIPTION_QOS_MASK = 0b0000_0011;
    private static final int SUBSCRIPTION_RESERVED_MASK = 0b1100_0000;
    private static final int RETAIN_AVAILABLE_MASK = 1 << MqttServerCapabilities.RETAIN.value();
    private static final int WILDCARD_AVAILABLE_MASK = 1 << MqttServerCapabilities.WILDCARD.value();
    private static final int SUBSCRIPTION_IDS_AVAILABLE_MASK = 1 << MqttServerCapabilities.SUBSCRIPTION_IDS.value();
    private static final int SHARED_SUBSCRIPTIONS_AVAILABLE_MASK = 1 << MqttServerCapabilities.SHARED_SUBSCRIPTIONS.value();
    private static final int SHARED_GENERATION_MASK = 1 << MqttServerCapabilities.SHARED_GENERATION.value();

    private static final int REDIRECT_MASK = 1 << MqttServerCapabilities.REDIRECT.value();
    private static final int WILL_FLAG_MASK = 0b0000_0100;
    private static final int CLEAN_START_FLAG_MASK = 0b0000_0010;
    private static final int WILL_QOS_MASK = 0b0001_1000;
    private static final int WILL_RETAIN_MASK = 0b0010_0000;
    private static final int USERNAME_MASK = 0b1000_0000;
    private static final int PASSWORD_MASK = 0b0100_0000;

    private static final int CONNECT_TOPIC_ALIAS_MAXIMUM_MASK = 0b0000_0001;
    private static final int CONNECT_SESSION_EXPIRY_INTERVAL_MASK = 0b0000_0010;
    private static final int DISCONNECT_REASON_STRING_MASK = 0b0000_0001;
    private static final int CONNECT_MAXIMUM_PACKET_SIZE_MASK = 0b0000_0100;
    private static final int CONNECT_RECEIVE_MAXIMUM_MASK = 0b0000_1000;

    private static final int CONNACK_SESSION_PRESENT = 0b0000_0001;

    private static final int RETAIN_HANDLING_SEND = 0;
    private static final int RETAIN_HANDLING_SEND_IF_NEW = 0b0001_0000;

    private static final int RETAIN_FLAG = 1 << RETAIN.ordinal();
    private static final int DUP_FLAG = 1 << DUP.ordinal();
    private static final int SEND_RETAINED_FLAG = 1 << SEND_RETAINED.ordinal();
    private static final int RETAIN_AS_PUBLISHED_FLAG = 1 << RETAIN_AS_PUBLISHED.ordinal();
    private static final int NO_LOCAL_FLAG = 1 << NO_LOCAL.ordinal();

    private static final int PUBLISH_TYPE = 0x03;
    private static final int FAILURE_REASON_CODE_MIN = Byte.toUnsignedInt(UNSPECIFIED_ERROR);

    private static final int DEFAULT_WILL_DELAY = 0;

    private static final int PUBLISH_EXPIRED_SIGNAL = 1;
    private static final int KEEP_ALIVE_TIMEOUT_SIGNAL = 2;
    private static final int CONNECT_TIMEOUT_SIGNAL = 3;
    private static final int SIGNAL_RENEW_SESSION_OWNERSHIP = 4;
    private static final int SIGNAL_STEAL_SESSION_OWNERSHIP = 5;
    private static final int SIGNAL_ACQUIRE_SESSION_OWNERSHIP = 6;
    private static final int GUARD_SESSION_EXPIRING_SIGNAL = 7;
    private static final int SIGNAL_AWAIT_STATE_RELEASE = 8;

    private static final String OWNER_KEY_POSTFIX = "#owner";
    private static final String STATE_KEY_POSTFIX = "#state";

    private static final int PUBLISH_FRAMING = 255;

    private static final String16FW NULL_STRING = new String16FW((String) null);
    public static final int QOS2_INCOMPLETE_OFFSET_STATE = MqttOffsetStateFlags.INCOMPLETE.value();
    public static final int QOS2_COMPLETE_OFFSET_STATE = MqttOffsetStateFlags.COMPLETE.value();
    public static final int RECORDED_OFFSET_STATE = MqttOffsetStateFlags.RECORDED.value();
    public static final int ABANDONED_OFFSET_STATE = MqttOffsetStateFlags.ABANDONED.value();


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
    private final MqttFlushExFW mqttFlushExRO = new MqttFlushExFW();
    private final MqttResetExFW mqttResetExRO = new MqttResetExFW();
    private final MqttEndExFW.Builder mqttEndExRW = new MqttEndExFW.Builder();
    private final MqttBeginExFW mqttBeginExRO = new MqttBeginExFW();

    private final MqttBeginExFW.Builder mqttPublishBeginExRW = new MqttBeginExFW.Builder();
    private final MqttBeginExFW.Builder mqttSubscribeBeginExRW = new MqttBeginExFW.Builder();
    private final MqttClientStates clientStates = new MqttClientStates();
    private final MqttBeginExFW.Builder mqttSessionBeginExRW = new MqttBeginExFW.Builder();
    private final MqttDataExFW.Builder mqttPublishDataExRW = new MqttDataExFW.Builder();
    private final MqttDataExFW.Builder mqttSessionDataExRW = new MqttDataExFW.Builder();
    private final MqttFlushExFW.Builder mqttFlushExRW = new MqttFlushExFW.Builder();
    private final MqttWillMessageFW.Builder mqttWillMessageRW = new MqttWillMessageFW.Builder();
    private final MqttSessionStateFW mqttSessionStateRO = new MqttSessionStateFW();
    private final Varint32FW filterIdRO = new Varint32FW();
    private final Varint32FW filterIdEarlierRO = new Varint32FW();
    private final MqttSessionStateFW.Builder mqttSessionStateRW = new MqttSessionStateFW.Builder();
    private final MqttPacketHeaderFW mqttPacketHeaderRO = new MqttPacketHeaderFW();
    private final MqttConnectFW mqttConnectRO = new MqttConnectFW();
    private final MqttAckHeaderFW mqttAckHeaderRO = new MqttAckHeaderFW();
    private final MqttPubackV4FW mqttPubackV4RO = new MqttPubackV4FW();
    private final MqttPubackV5FW mqttPubackV5RO = new MqttPubackV5FW();
    private final MqttPubrecV4FW mqttPubrecV4RO = new MqttPubrecV4FW();
    private final MqttPubrecV5FW mqttPubrecV5RO = new MqttPubrecV5FW();
    private final MqttPubrelV4FW mqttPubrelV4RO = new MqttPubrelV4FW();
    private final MqttPubrelV5FW mqttPubrelV5RO = new MqttPubrelV5FW();
    private final MqttPubcompV4FW mqttPubcompV4RO = new MqttPubcompV4FW();
    private final MqttPubcompV5FW mqttPubcompV5RO = new MqttPubcompV5FW();
    private final MqttPropertiesFW mqttPropertiesRO = new MqttPropertiesFW();
    private final MqttConnectV5FW mqttConnectV5RO = new MqttConnectV5FW();
    private final MqttConnectV4FW mqttConnectV4RO = new MqttConnectV4FW();
    private final MqttWillV5FW mqttWillV5RO = new MqttWillV5FW();
    private final MqttWillV4FW mqttWillV4RO = new MqttWillV4FW();
    private final MqttPublishV5FW mqttPublishV5RO = new MqttPublishV5FW();
    private final MqttPublishQosV5FW mqttPublishQosV5RO = new MqttPublishQosV5FW();
    private final MqttPublishV4FW mqttPublishV4RO = new MqttPublishV4FW();
    private final MqttPublishQosV4FW mqttPublishQosV4RO = new MqttPublishQosV4FW();
    private final OctetsFW payloadRO = new OctetsFW();
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

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBufferEx(0L, 0), 0, 0);
    private final String16FW clientIdRO = new String16FW();
    private final MqttPayloadFormatFW[] payloadFormats = payloadFormats();
    private final io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttUserPropertyFW subscribeUserPropertyRO =
        new io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttUserPropertyFW();
    private final Consumer<MqttUserPropertyFW.Builder> subscribeUserPropertyBuilder = this::buildSubscribeUserProperty;
    private final Consumer<MqttPropertiesFW.Builder> publishPropertiesBuilder = this::buildPublishProperties;
    private final Consumer<MqttSubscribeFlushExFW.Builder> subscribeFlushBuilder = this::buildSubscribeFlush;
    private final Consumer<MqttSessionFlushExFW.Builder> sessionFlushBuilder = this::buildSessionFlush;
    private int publishPropertiesSize;
    private int flushQos;
    private int flushPacketId;
    private int flushOffsetState;
    private int flushReasonCode;
    private final MqttUserPropertyFW publishUserPropertyRO = new MqttUserPropertyFW();
    private final Consumer<io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttUserPropertyFW.Builder>
        publishUserPropertyBuilder = this::buildPublishUserProperty;
    private final MutableDirectBufferEx topicKeyBuffer = new UnsafeBufferEx(new byte[MAXIMUM_TOPIC_BYTES]);
    private final String16FW decodedContentTypeRO = new String16FW();
    private final String16FW decodedResponseTopicRO = new String16FW();
    private final OctetsFW decodedCorrelationDataRO = new OctetsFW();
    private final Array32FW<MqttUserPropertyFW> decodedUserPropertiesRO = new Array32FW<>(new MqttUserPropertyFW());
    private final MqttPropertyFW mqttPropertyRO = new MqttPropertyFW();
    private final MqttPropertyFW.Builder mqttPropertyRW = new MqttPropertyFW.Builder();


    private final String16FW contentTypeRO = new String16FW(BIG_ENDIAN);
    private final String16FW responseTopicRO = new String16FW(BIG_ENDIAN);
    private final String16FW usernameRO = new String16FW(BIG_ENDIAN);
    private final BinaryFW passwordRO = new BinaryFW();

    private final MqttPublishHelper mqttPublishHelper = new MqttPublishHelper();
    private final MqttConnectPayload mqttConnectPayloadRO = new MqttConnectPayload();

    private final MqttPublishQosV5FW.Builder mqttPublishQosV5RW = new MqttPublishQosV5FW.Builder();
    private final MqttPublishQosV4FW.Builder mqttPublishQosV4RW = new MqttPublishQosV4FW.Builder();
    private final MqttPubackV4FW.Builder mqttPubackV4RW = new MqttPubackV4FW.Builder();
    private final MqttPubackV5FW.Builder mqttPubackV5RW = new MqttPubackV5FW.Builder();
    private final MqttPubrecV4FW.Builder mqttPubrecV4RW = new MqttPubrecV4FW.Builder();
    private final MqttPubrecV5FW.Builder mqttPubrecV5RW = new MqttPubrecV5FW.Builder();
    private final MqttPubrelV4FW.Builder mqttPubrelV4RW = new MqttPubrelV4FW.Builder();
    private final MqttPubrelV5FW.Builder mqttPubrelV5RW = new MqttPubrelV5FW.Builder();
    private final MqttPubcompV4FW.Builder mqttPubcompV4RW = new MqttPubcompV4FW.Builder();
    private final MqttPubcompV5FW.Builder mqttPubcompV5RW = new MqttPubcompV5FW.Builder();
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
    private final MqttServerDecoder decodeConnectWillMessagePayload = this::decodeConnectWillMessagePayload;
    private final MqttServerDecoder decodeAwaitOwnership = this::decodeAwaitOwnership;
    private final MqttServerDecoder decodeAwaitConnack = this::decodeAwaitConnack;
    private final MqttServerDecoder decodePublishV4 = this::decodePublishV4;
    private final MqttServerDecoder decodePublishV5 = this::decodePublishV5;
    private final MqttServerDecoder decodePublishWindow = this::decodePublishWindow;
    private final MqttServerDecoder decodePublishPayload = this::decodePublishPayload;
    private final MqttServerDecoder decodeRejectedPublishPayload = this::decodeRejectedPublishPayload;
    private final MqttServerDecoder decodeSubscribeV4 = this::decodeSubscribeV4;
    private final MqttServerDecoder decodeSubscribeV5 = this::decodeSubscribeV5;
    private final MqttServerDecoder decodeUnsubscribeV4 = this::decodeUnsubscribeV4;
    private final MqttServerDecoder decodeUnsubscribeV5 = this::decodeUnsubscribeV5;
    private final MqttServerDecoder decodePubackV4 = this::decodePubackV4;
    private final MqttServerDecoder decodePubackV5 = this::decodePubackV5;
    private final MqttServerDecoder decodePubrecV4 = this::decodePubrecV4;
    private final MqttServerDecoder decodePubrecV5 = this::decodePubrecV5;
    private final MqttServerDecoder decodePubrelV4 = this::decodePubrelV4;
    private final MqttServerDecoder decodePubrelV5 = this::decodePubrelV5;
    private final MqttServerDecoder decodePubcompV4 = this::decodePubcompV4;
    private final MqttServerDecoder decodePubcompV5 = this::decodePubcompV5;
    private final MqttServerDecoder decodePingreq = this::decodePingreq;
    private final MqttServerDecoder decodeDisconnectV4 = this::decodeDisconnectV4;
    private final MqttServerDecoder decodeDisconnectV5 = this::decodeDisconnectV5;
    private final MqttServerDecoder decodeIgnoreAll = this::decodeIgnoreAll;
    private final MqttServerDecoder decodeUnknownType = this::decodeUnknownType;

    private final Int2ObjectHashMap<MqttServerDecoder> decodePacketTypeByVersion;
    private final Map<MqttPacketType, MqttServerDecoder> decodersByPacketTypeV4;
    private final Map<MqttPacketType, MqttServerDecoder> decodersByPacketTypeV5;
    private final EngineContext context;
    private final Clock clock;

    private final int maximumPacketSize;

    {
        final Map<MqttPacketType, MqttServerDecoder> decodersByPacketType = new EnumMap<>(MqttPacketType.class);
        decodersByPacketType.put(MqttPacketType.CONNECT, decodeConnectV4);
        decodersByPacketType.put(MqttPacketType.PUBLISH, decodePublishV4);
        decodersByPacketType.put(MqttPacketType.PUBACK, decodePubackV4);
        decodersByPacketType.put(MqttPacketType.PUBREC, decodePubrecV4);
        decodersByPacketType.put(MqttPacketType.PUBREL, decodePubrelV4);
        decodersByPacketType.put(MqttPacketType.PUBCOMP, decodePubcompV4);
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
        decodersByPacketType.put(MqttPacketType.PUBACK, decodePubackV5);
        decodersByPacketType.put(MqttPacketType.PUBREC, decodePubrecV5);
        decodersByPacketType.put(MqttPacketType.PUBREL, decodePubrelV5);
        decodersByPacketType.put(MqttPacketType.PUBCOMP, decodePubcompV5);
        decodersByPacketType.put(MqttPacketType.SUBSCRIBE, decodeSubscribeV5);
        decodersByPacketType.put(MqttPacketType.UNSUBSCRIBE, decodeUnsubscribeV5);
        decodersByPacketType.put(MqttPacketType.PINGREQ, decodePingreq);
        decodersByPacketType.put(MqttPacketType.DISCONNECT, decodeDisconnectV5);
        // decodersByPacketType.put(MqttPacketType.AUTH, decodeAuth);
        this.decodersByPacketTypeV5 = decodersByPacketType;
    }

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final MutableDirectBufferEx dataExtBuffer;
    private final MutableDirectBufferEx flushExtBuffer;
    private final MutableDirectBufferEx payloadBuffer;
    private final MutableDirectBufferEx propertyBuffer;
    private final MutableDirectBufferEx sessionExtBuffer;
    private final MutableDirectBufferEx sessionStateBuffer;
    private final MutableDirectBufferEx userPropertiesBuffer;
    private final MutableDirectBufferEx willMessageBuffer;
    private final MutableDirectBufferEx willUserPropertiesBuffer;

    private final BufferPool bufferPool;
    private static final Long2ObjectHashMap<?> EMPTY_LONG_MAP = Long2ObjectHashMaps.emptyMap();
    private static final Int2ObjectHashMap<?> EMPTY_INT_MAP = Int2ObjectHashMaps.emptyMap();
    private static final Map<?, ?> EMPTY_MAP = Collections.emptyMap();
    private static final List<?> EMPTY_LIST = Collections.emptyList();
    private static final IntArrayList EMPTY_INT_LIST = IntArrayLists.emptyList();

    private final Signaler signaler;
    private final MessageConsumer droppedHandler;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final LongSupplier supplyBudgetId;
    private final Long2ObjectHashMap<MqttBindingConfig> bindings;
    private final int mqttTypeId;

    private final long publishTimeoutMillis;
    private final long connectTimeoutMillis;
    private final int encodeBudgetMax;

    private final short keepAliveMinimum;
    private final short keepAliveMaximum;
    private final short topicAliasMaximumLimit;
    private final boolean noLocal;
    private final Supplier<String> supplyClientId;
    private final MqttValidator validator;
    private final MqttUtf8Validator utf8Validator;
    private final Function<ModelConfig, ModelHandler> supplyModel;
    private final MutableDirectBufferEx modelBuffer;
    private final String16FW.Builder userPropertyValueRW = new String16FW.Builder();
    private final MutableDirectBufferEx userPropertyValueBuffer;
    private final OctetsFW modelPayloadRO = new OctetsFW();
    private final MqttEventContext events;
    private final InstanceId instanceId;
    private final Duration sessionLease;
    private final Duration sessionRenew;
    private final String serviceHostname;
    private final String replicaId;
    private final String ownerIdentity;
    private final String ownershipRecordHead;
    private final StringBuilder ownershipRecordRW = new StringBuilder();

    private final MqttQoS publishQosMaxDefault;
    private final int subscribeQosMaxDefault;

    public MqttServerFactory(
        MqttConfiguration config,
        EngineContext context,
        InstanceId instanceId)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = context.writeBuffer("extension");
        this.dataExtBuffer = context.writeBuffer("dataExtension");
        this.flushExtBuffer = context.writeBuffer("flushExtension");
        this.propertyBuffer = context.writeBuffer("property");
        this.userPropertiesBuffer = context.writeBuffer("userProperties");
        this.payloadBuffer = context.writeBuffer("payload");
        this.sessionExtBuffer = context.writeBuffer("sessionExtension");
        this.sessionStateBuffer = context.writeBuffer("sessionState");
        this.willMessageBuffer = context.writeBuffer("willMessage");
        this.willUserPropertiesBuffer = context.writeBuffer("willUserProperties");
        this.bufferPool = context.bufferPool();
        this.signaler = context.signaler();
        this.droppedHandler = context.droppedFrameHandler();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyBudgetId = context::supplyBudgetId;
        this.supplyTraceId = context::supplyTraceId;
        this.context = context;
        this.clock = context.clock();
        this.bindings = new Long2ObjectHashMap<>();
        this.mqttTypeId = context.supplyTypeId(MqttBinding.NAME);
        this.publishTimeoutMillis = SECONDS.toMillis(config.publishTimeout());
        this.connectTimeoutMillis = SECONDS.toMillis(config.connectTimeout());
        this.keepAliveMinimum = config.keepAliveMinimum();
        this.keepAliveMaximum = config.keepAliveMaximum();
        this.maximumPacketSize = config.maximumPacketSize();
        this.topicAliasMaximumLimit = (short) Math.max(config.topicAliasMaximum(), 0);
        this.noLocal = config.noLocal();
        this.encodeBudgetMax = bufferPool.slotCapacity();
        this.validator = new MqttValidator();
        this.utf8Validator = new MqttUtf8Validator();
        this.publishQosMaxDefault = config.publishQosMax();
        this.subscribeQosMaxDefault = config.maximumQos();
        final String configuredClientId = config.clientId();
        this.supplyClientId = configuredClientId != null ? () -> configuredClientId : () -> UUID.randomUUID().toString();
        this.decodePacketTypeByVersion = new Int2ObjectHashMap<>();
        this.decodePacketTypeByVersion.put(MQTT_PROTOCOL_VERSION_4, this::decodePacketTypeV4);
        this.decodePacketTypeByVersion.put(MQTT_PROTOCOL_VERSION_5, this::decodePacketTypeV5);
        this.supplyModel = context::supplyModel;
        this.modelBuffer = context.writeBuffer("model");
        this.userPropertyValueBuffer = context.writeBuffer("userPropertyValue");
        this.events = new MqttEventContext(context);
        this.instanceId = instanceId;
        this.sessionLease = config.sessionLease();
        this.sessionRenew = config.sessionRenew();
        this.serviceHostname = config.serviceHostname();
        this.replicaId = instanceId.instanceId().asString();
        this.ownerIdentity = serviceHostname != null ? serviceHostname : replicaId;
        this.ownershipRecordHead = (serviceHostname != null ? serviceHostname : OwnershipRecord.WEAK_PREFIX + replicaId) +
            OwnershipRecord.FIELD_SEPARATOR;
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
        DirectBufferEx buffer,
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

            newStream = new MqttServer(
                sender,
                originId,
                routedId,
                initialId,
                affinity,
                binding.versions,
                binding.guard,
                binding.resolveCredentials(),
                binding.authField(),
                binding.store)::onNetwork;
        }
        return newStream;
    }

    private long topicKey(
        String topic,
        int qos)
    {
        final int length = topicKeyBuffer.putStringWithoutLengthUtf8(0, topic);

        return topicKey(topicKeyBuffer, 0, length, qos);
    }

    private long topicKey(
        String16FW topicName,
        int qos)
    {
        return topicKey(topicName.buffer(), topicName.offset() + Short.BYTES, topicName.length(), qos);
    }

    private long topicKey(
        DirectBufferEx buffer,
        int offset,
        int length,
        int qos)
    {
        int topicHashCode = TOPIC_HASH_SEED;
        for (int index = 0; index < length; index++)
        {
            topicHashCode = (topicHashCode ^ (buffer.getByte(offset + index) & 0xff)) * TOPIC_HASH_PRIME;
        }

        return ((long) topicHashCode << 32) | (qos & 0xFFFFFFFFL);
    }

    private MessageConsumer newStream(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int state,
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
            .state(state)
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
        int state,
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
            .state(state)
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
        int state,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
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
            .state(state)
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
        int state,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        int flags,
        DirectBufferEx buffer,
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
            .state(state)
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
        int state,
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
            .state(state)
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
        int state,
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
            .state(state)
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
        int state,
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
            .state(state)
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
        int state,
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
            .state(state)
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
        int state,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx extension,
        int extensionOffset,
        int extensionLength)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .state(state)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .reserved(reserved)
            .extension(extension, extensionOffset, extensionLength)
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doFlush(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int state,
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
            .state(state)
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
        final DirectBufferEx buffer,
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
                int reasonCode = decodeConnectProtocol(mqttConnect.protocolName(), mqttConnect.protocolVersion());
                if (!server.versions.contains(MqttVersion.ofProtocol(mqttConnect.protocolVersion())))
                {
                    reasonCode = UNSUPPORTED_PROTOCOL_VERSION;
                }
                if (reasonCode != SUCCESS)
                {
                    final int connackVersion = mqttConnect.protocolVersion() == MQTT_PROTOCOL_VERSION_4
                        ? MQTT_PROTOCOL_VERSION_4
                        : MQTT_PROTOCOL_VERSION_5;
                    server.onDecodeError(traceId, authorization, reasonCode, connackVersion);
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
        final DirectBufferEx buffer,
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
            else if (limit - packet.limit() >= length || server.decodeBudget() == 0)
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
        final DirectBufferEx buffer,
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
            else if (limit - packet.limit() >= length || server.decodeBudget() == 0)
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
        final DirectBufferEx buffer,
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

                if (server.connected || server.clientId != null)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }

                final String16FW clientId = mqttConnect.clientId();

                if (!utf8Validator.isValidString(clientId))
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

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
        final DirectBufferEx buffer,
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

                if (server.connected || server.clientId != null)
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

                if (!utf8Validator.isValidString(clientId))
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

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
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        int progress = offset;

        progress = server.onDecodeConnectPayload(traceId, authorization, buffer, progress, limit);
        server.decodableRemainingBytes -= progress - offset;
        if (server.decodableRemainingBytes == 0 && server.decoder == decodeConnectPayload)
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
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        int progress = server.onDecodeConnectWillMessage(traceId, authorization, buffer, offset, limit);

        server.decodableRemainingBytes -= progress - offset;

        return progress;
    }

    private int decodeConnectWillMessagePayload(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        int progress = offset;

        progress = server.onDecodeConnectWillPayload(traceId, authorization, buffer,
            progress, limit);

        server.decodableRemainingBytes -= progress - offset;
        if (server.decodableRemainingBytes == 0)
        {
            server.decoder = decodePacketTypeByVersion.get(server.version);
        }
        return progress;
    }

    private int topicReasonCode(
        String topic)
    {
        return topic.indexOf('\u0000') != -1 ? MALFORMED_PACKET : PROTOCOL_ERROR;
    }

    private int topicReasonCode(
        String16FW topicName)
    {
        final DirectBufferEx buffer = topicName.buffer();
        final int offset = topicName.offset() + Short.BYTES;
        final int length = Math.max(topicName.length(), 0);

        int reasonCode = PROTOCOL_ERROR;
        for (int index = 0; reasonCode == PROTOCOL_ERROR && index < length; index++)
        {
            reasonCode = buffer.getByte(offset + index) == 0 ? MALFORMED_PACKET : PROTOCOL_ERROR;
        }

        return reasonCode;
    }

    private int decodePublishV4(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;
        int reasonCode = SUCCESS;

        decode:
        if (length >= 0)
        {
            final MqttPacketHeaderFW publishHeader = mqttPacketHeaderRO.tryWrap(buffer, offset, limit);
            final int typeAndFlags = publishHeader.typeAndFlags();
            final int qos = calculatePublishApplicationQos(typeAndFlags);

            reasonCode = decodePublishFlags(typeAndFlags);
            if (reasonCode != SUCCESS)
            {
                break decode;
            }

            String16FW topicName;
            int publishLimit;
            int packetId = 0;

            if (qos > server.publishQosMax.value())
            {
                reasonCode = QOS_NOT_SUPPORTED;
                break decode;
            }

            if (qos > 0)
            {
                final MqttPublishQosV4FW publish =
                    mqttPublishQosV4RO.tryWrap(buffer, offset, offset + server.decodeablePacketBytes);
                if (publish == null)
                {
                    reasonCode = MALFORMED_PACKET;
                    server.onDecodeError(traceId, authorization, reasonCode);
                    server.decoder = decodeIgnoreAll;
                    break decode;
                }

                topicName = publish.topicName();
                packetId = publish.packetId();
                if (packetId == 0)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }
                publishLimit = publish.limit();
            }
            else
            {
                final MqttPublishV4FW publish = mqttPublishV4RO
                    .tryWrap(buffer, offset, offset + server.decodeablePacketBytes);
                if (publish == null)
                {
                    reasonCode = MALFORMED_PACKET;
                    server.onDecodeError(traceId, authorization, reasonCode);
                    server.decoder = decodeIgnoreAll;
                    break decode;
                }

                topicName = publish.topicName();
                publishLimit = publish.limit();
            }

            final MqttPublishHelper mqttPublishHelper = this.mqttPublishHelper.reset();

            reasonCode = mqttPublishHelper.decodeV4(server, topicName, typeAndFlags, qos, packetId);

            if (reasonCode == SUCCESS)
            {
                final long topicKey = mqttPublishHelper.topicKey();
                MqttServer.MqttPublishStream publisher = server.publishes.get(topicKey);

                if (publisher == null)
                {
                    publisher = server.resolvePublishStream(traceId, authorization, topicKey, mqttPublishHelper.topic(),
                        mqttPublishHelper.qos);
                    if (publisher == null)
                    {
                        server.decodeablePublishPayloadBytes =
                            publishHeader.remainingLength() + publishHeader.sizeof() - (publishLimit - offset);
                        server.onDecodePublish(traceId, authorization, mqttPublishHelper.qos, mqttPublishHelper.retained,
                            mqttPublishHelper.packetId, UNSPECIFIED_ERROR);
                        progress = publishLimit;
                        break decode;
                    }
                }
                server.decodePublisherKey = topicKey;

                server.decodedQos = mqttPublishHelper.qos;
                server.decodedRetained = mqttPublishHelper.retained;
                server.decodedPacketId = mqttPublishHelper.packetId;
                server.decodedFlags = mqttPublishHelper.flags;

                final int decodeablePublishPayloadBytes =
                    publishHeader.remainingLength() + publishHeader.sizeof() - (publishLimit - offset);
                if (decodeablePublishPayloadBytes == 0 && !StreamState.initialOpened(publisher.state))
                {
                    server.decodeablePublishPacketBytes = publishLimit - offset;
                    server.decoder = decodePublishWindow;
                    break decode;
                }
                server.decodeablePublishPayloadBytes = decodeablePublishPayloadBytes;
                server.publishPayloadModeling = false;
                server.publishPayloadModelProgress = 0;
                server.publishPayloadValidating = false;
                server.decodeablePacketBytes = limit - publishLimit;
                server.decoder = decodePublishPayload;
                progress = publishLimit;
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
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;
        int reasonCode = SUCCESS;

        decode:
        if (length >= 0)
        {
            final MqttPacketHeaderFW publishHeader = mqttPacketHeaderRO.tryWrap(buffer, offset, limit);
            final int typeAndFlags = publishHeader.typeAndFlags();
            final int qos = calculatePublishApplicationQos(typeAndFlags);

            reasonCode = decodePublishFlags(typeAndFlags);
            if (reasonCode != SUCCESS)
            {
                break decode;
            }

            String16FW topicName;
            MqttPropertiesFW properties;
            int publishLimit;
            int packetId = 0;

            if (qos > server.publishQosMax.value())
            {
                reasonCode = QOS_NOT_SUPPORTED;
                break decode;
            }

            if (qos > 0)
            {
                final MqttPublishQosV5FW publish =
                    mqttPublishQosV5RO.tryWrap(buffer, offset, offset + server.decodeablePacketBytes);
                if (publish == null)
                {
                    reasonCode = MALFORMED_PACKET;
                    server.onDecodeError(traceId, authorization, reasonCode);
                    server.decoder = decodeIgnoreAll;
                    break decode;
                }

                topicName = publish.topicName();
                properties = publish.properties();
                packetId = publish.packetId();
                if (packetId == 0)
                {
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
                }
                publishLimit = publish.limit();
            }
            else
            {
                final MqttPublishV5FW publish = mqttPublishV5RO
                    .tryWrap(buffer, offset, offset + server.decodeablePacketBytes);
                if (publish == null)
                {
                    reasonCode = MALFORMED_PACKET;
                    server.onDecodeError(traceId, authorization, reasonCode);
                    server.decoder = decodeIgnoreAll;
                    break decode;
                }

                topicName = publish.topicName();
                properties = publish.properties();
                publishLimit = publish.limit();
            }

            final MqttPublishHelper mqttPublishHelper = this.mqttPublishHelper.reset();

            reasonCode = mqttPublishHelper.decodeV5(traceId, authorization, server, topicName, properties, typeAndFlags,
                qos, packetId);

            if (reasonCode == SUCCESS)
            {
                final long topicKey = mqttPublishHelper.topicKey();
                MqttServer.MqttPublishStream publisher = server.publishes.get(topicKey);

                if (publisher == null)
                {
                    publisher = server.resolvePublishStream(traceId, authorization, topicKey, mqttPublishHelper.topic(),
                        mqttPublishHelper.qos);
                    if (publisher == null)
                    {
                        server.decodeablePublishPayloadBytes =
                            publishHeader.remainingLength() + publishHeader.sizeof() - (publishLimit - offset);
                        server.onDecodePublish(traceId, authorization, mqttPublishHelper.qos, mqttPublishHelper.retained,
                            mqttPublishHelper.packetId, UNSPECIFIED_ERROR);
                        progress = publishLimit;
                        break decode;
                    }
                }

                server.decodePublisherKey = topicKey;
                server.decodedQos = mqttPublishHelper.qos;
                server.decodedRetained = mqttPublishHelper.retained;
                server.decodedPacketId = mqttPublishHelper.packetId;
                server.decodedFlags = mqttPublishHelper.flags;
                server.decodedExpiryInterval = mqttPublishHelper.expiryInterval;
                server.decodePublishProperties(mqttPublishHelper);
                server.decodedPayloadFormat = mqttPublishHelper.payloadFormat;

                final int decodeablePublishPayloadBytes =
                    publishHeader.remainingLength() + publishHeader.sizeof() - (publishLimit - offset);
                if (decodeablePublishPayloadBytes == 0 && !StreamState.initialOpened(publisher.state))
                {
                    server.decodeablePublishPacketBytes = publishLimit - offset;
                    server.decoder = decodePublishWindow;
                    break decode;
                }
                server.decodeablePublishPayloadBytes = decodeablePublishPayloadBytes;
                server.publishPayloadModeling = false;
                server.publishPayloadModelProgress = 0;
                server.publishPayloadValidating = false;
                server.decodeablePacketBytes = limit - publishLimit;
                server.decoder = decodePublishPayload;
                progress = publishLimit;
            }
        }

        if (reasonCode != SUCCESS)
        {
            server.onDecodeError(traceId, authorization, reasonCode);
            server.decoder = decodeIgnoreAll;
        }

        return progress;
    }

    private int decodePublishWindow(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        final MqttServer.MqttPublishStream publisher = server.publishes.get(server.decodePublisherKey);

        if (publisher == null || StreamState.initialOpened(publisher.state))
        {
            server.decoder = decodePacketTypeByVersion.get(server.version);
        }

        return offset;
    }

    private int decodePublishPayload(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;
        int reasonCode = SUCCESS;

        if (length >= 0)
        {
            MqttServer.MqttPublishStream publisher = server.publishes.get(server.decodePublisherKey);
            final MqttModel model = publisher.contentType;
            final boolean canPublish = StreamState.initialOpened(publisher.state);

            DirectBufferEx payloadBuffer = buffer;
            int payloadOffset = offset;
            int available = length;
            boolean ready = true;

            if (model.active() && !model.identity() && canPublish)
            {
                if (!server.publishPayloadModeling)
                {
                    final int sourceBytes = server.decodeablePublishPayloadBytes;
                    if (length < sourceBytes)
                    {
                        if (sourceBytes > bufferPool.slotCapacity())
                        {
                            reasonCode = PAYLOAD_FORMAT_INVALID;
                        }
                        ready = false;
                    }
                    else
                    {
                        final int produced =
                            model.transform(traceId, publisher.routedId, authorization, buffer, offset, offset + sourceBytes);
                        if (produced < 0)
                        {
                            reasonCode = PAYLOAD_FORMAT_INVALID;
                        }
                        else
                        {
                            server.publishPayloadModeling = true;
                            server.publishPayloadModelProgress = 0;
                            server.publishPayloadConsumable = sourceBytes;
                            server.decodeablePublishPayloadBytes = produced;
                        }
                    }
                }

                if (ready && reasonCode == SUCCESS && server.publishPayloadModeling)
                {
                    payloadBuffer = model.buffer();
                    payloadOffset = server.publishPayloadModelProgress;
                    available = server.decodeablePublishPayloadBytes;
                }
            }

            final int initialBudget = publisher.initialBudget();
            final int lengthMax = Math.min(available, server.decodeablePublishPayloadBytes);

            // a frame reserves its payload plus the padding, so the window must hold both; when it
            // cannot hold even the padding this is negative, and reserving the padding regardless
            // would advance initialSeq past the window the publish stream has been granted
            final int sizeMax = Math.min(lengthMax, initialBudget - publisher.initialPad);

            final int maximum = sizeMax + publisher.initialPad;
            final int minimum = Math.min(maximum, Math.max(publisher.initialMin, 1024) + publisher.initialPad);

            int valueClaimed = maximum;

            if (ready && reasonCode == SUCCESS && sizeMax >= 0 &&
                canPublish && publisher.debit != null && lengthMax != 0)
            {
                valueClaimed = publisher.debit.claim(traceId, minimum, maximum);
            }

            final int sizeClaimed = valueClaimed - publisher.initialPad;

            if (ready && reasonCode == SUCCESS && sizeClaimed >= 0)
            {
                final OctetsFW payload = payloadRO
                    .wrap(payloadBuffer, payloadOffset, payloadOffset + sizeClaimed);

                if (canPublish && model.identity() && (sizeClaimed > 0 || server.decodeablePublishPayloadBytes == 0))
                {
                    final boolean first = !server.publishPayloadValidating;
                    final boolean last = sizeClaimed == server.decodeablePublishPayloadBytes;
                    server.publishPayloadValidating = !last;
                    if (!model.validate(traceId, publisher.routedId, authorization, first, last,
                            payloadBuffer, payloadOffset, payloadOffset + sizeClaimed))
                    {
                        reasonCode = PAYLOAD_FORMAT_INVALID;
                    }
                }

                if (reasonCode == SUCCESS && canPublish && (sizeClaimed != 0 || payload.sizeof() == 0))
                {
                    boolean accepted = true;

                    if (server.publishPayloadDeferred == 0)
                    {
                        accepted = server.onDecodePublish(traceId, authorization, server.decodedQos,
                            server.decodedRetained, server.decodedPacketId, SUCCESS);
                    }

                    if (accepted)
                    {
                        server.onDecodePublishPayload(traceId, authorization, valueClaimed, payload, payload.offset(),
                            payload.offset() + sizeClaimed);

                        if (server.publishPayloadModeling)
                        {
                            server.publishPayloadModelProgress += sizeClaimed;

                            if (server.decodeablePublishPayloadBytes == 0)
                            {
                                progress = offset + server.publishPayloadConsumable;
                                server.publishPayloadModeling = false;
                                server.decoder = decodePacketTypeByVersion.get(server.version);
                            }
                        }
                        else
                        {
                            progress = payload.offset() + sizeClaimed;

                            if (server.decodeablePublishPayloadBytes == 0)
                            {
                                server.decoder = decodePacketTypeByVersion.get(server.version);
                            }
                        }
                    }
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

    private int decodeRejectedPublishPayload(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        final int length = Math.min(limit - offset, server.decodeablePublishPayloadBytes);

        server.decodeablePublishPayloadBytes -= length;

        if (server.decodeablePublishPayloadBytes == 0)
        {
            server.decoder = decodePacketTypeByVersion.get(server.version);
        }

        return offset + length;
    }

    private static int decodePublishFlags(
        int typeAndFlags)
    {
        final boolean bothQosBits = (typeAndFlags & PUBLISH_QOS1_MASK) != 0 && (typeAndFlags & PUBLISH_QOS2_MASK) != 0;
        final boolean dupWithoutQos = (typeAndFlags & PUBLISH_DUP_MASK) != 0 &&
            (typeAndFlags & (PUBLISH_QOS1_MASK | PUBLISH_QOS2_MASK)) == 0;

        return bothQosBits || dupWithoutQos ? MALFORMED_PACKET : SUCCESS;
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

    private int decodePubackV4(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        if (length > 0)
        {
            int reasonCode = SUCCESS;

            final MqttPubackV4FW puback = mqttPubackV4RO.tryWrap(buffer, offset, limit);
            decode:
            {
                if (puback == null)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }
                else if ((puback.typeAndFlags() & 0b1111_1111) != PUBACK_FIXED_HEADER)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                server.onDecodePuback(traceId, authorization, buffer, progress, limit, puback.packetId(), SUCCESS);
                progress = puback.limit();
                server.decoder = decodePacketTypeByVersion.get(server.version);
            }

            if (reasonCode != SUCCESS)
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodePubackV5(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        if (length > 0)
        {
            int reasonCode = SUCCESS;
            int ackReasonCode = SUCCESS;

            decode:
            {
                final MqttAckHeaderFW ackHeader = mqttAckHeaderRO.tryWrap(buffer, offset, limit);
                if (ackHeader == null)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }
                int pubackLimit = ackHeader.limit();
                final int remainingLength = ackHeader.remainingLength();
                if (remainingLength > 2)
                {
                    final MqttPubackV5FW puback = mqttPubackV5RO.tryWrap(buffer, offset, limit);
                    if (puback == null)
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    pubackLimit = puback.limit();
                    ackReasonCode = puback.reasonCode();
                    if (remainingLength >= 4)
                    {
                        final MqttPropertiesFW properties = mqttPropertiesRO.tryWrap(buffer, pubackLimit, limit);
                        if (properties == null)
                        {
                            reasonCode = MALFORMED_PACKET;
                            break decode;
                        }
                        pubackLimit = properties.limit();
                    }
                }

                if ((ackHeader.typeAndFlags() & 0b1111_1111) != PUBACK_FIXED_HEADER)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                server.onDecodePuback(traceId, authorization, buffer, progress, limit, ackHeader.packetId(), ackReasonCode);
                progress = pubackLimit;
                server.decoder = decodePacketTypeByVersion.get(server.version);
            }

            if (reasonCode != SUCCESS)
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodePubrecV4(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        if (length > 0)
        {
            int reasonCode = SUCCESS;

            final MqttPubrecV4FW pubrec = mqttPubrecV4RO.tryWrap(buffer, offset, limit);
            decode:
            {
                if (pubrec == null)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }
                else if ((pubrec.typeAndFlags() & 0b1111_1111) != PUBREC_FIXED_HEADER)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                server.onDecodePubrec(traceId, authorization, buffer, progress, limit, pubrec.packetId(), SUCCESS);
                progress = pubrec.limit();
                server.decoder = decodePacketTypeByVersion.get(server.version);
            }

            if (reasonCode != SUCCESS)
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodePubrecV5(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        if (length > 0)
        {
            int reasonCode = SUCCESS;

            decode:
            {
                final MqttAckHeaderFW ackHeader = mqttAckHeaderRO.tryWrap(buffer, offset, limit);
                if (ackHeader == null)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }
                int pubrecLimit = ackHeader.limit();
                int pubrecReasonCode = SUCCESS;
                final int remainingLength = ackHeader.remainingLength();
                if (remainingLength > 2)
                {
                    final MqttPubrecV5FW pubrec = mqttPubrecV5RO.tryWrap(buffer, offset, limit);
                    if (pubrec == null)
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    pubrecReasonCode = pubrec.reasonCode();
                    pubrecLimit = pubrec.limit();
                    if (remainingLength >= 4)
                    {
                        final MqttPropertiesFW properties = mqttPropertiesRO.tryWrap(buffer, pubrecLimit, limit);
                        if (properties == null)
                        {
                            reasonCode = MALFORMED_PACKET;
                            break decode;
                        }
                        pubrecLimit = properties.limit();
                    }
                }

                if ((ackHeader.typeAndFlags() & 0b1111_1111) != PUBREC_FIXED_HEADER)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                server.onDecodePubrec(traceId, authorization, buffer, progress, limit, ackHeader.packetId(), pubrecReasonCode);
                progress = pubrecLimit;
                server.decoder = decodePacketTypeByVersion.get(server.version);
            }

            if (reasonCode != SUCCESS)
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodePubrelV4(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        if (length > 0)
        {
            int reasonCode = SUCCESS;

            final MqttPubrelV4FW pubrel = mqttPubrelV4RO.tryWrap(buffer, offset, limit);
            decode:
            {
                if (pubrel == null)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }
                else if ((pubrel.typeAndFlags() & 0b1111_1111) != PUBREL_FIXED_HEADER)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                server.onDecodePubrel(traceId, authorization, buffer, progress, limit, pubrel.packetId());
                progress = pubrel.limit();
                server.decoder = decodePacketTypeByVersion.get(server.version);
            }

            if (reasonCode != SUCCESS)
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodePubrelV5(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        if (length > 0)
        {
            int reasonCode = SUCCESS;

            decode:
            {
                final MqttAckHeaderFW ackHeader = mqttAckHeaderRO.tryWrap(buffer, offset, limit);
                if (ackHeader == null)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }
                int pubrelLimit = ackHeader.limit();
                final int remainingLength = ackHeader.remainingLength();
                if (remainingLength > 2)
                {
                    final MqttPubrelV5FW pubrel = mqttPubrelV5RO.tryWrap(buffer, offset, limit);
                    if (pubrel == null)
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    pubrelLimit = pubrel.limit();
                    if (remainingLength >= 4)
                    {
                        final MqttPropertiesFW properties = mqttPropertiesRO.tryWrap(buffer, pubrelLimit, limit);
                        if (properties == null)
                        {
                            reasonCode = MALFORMED_PACKET;
                            break decode;
                        }
                        pubrelLimit = properties.limit();
                    }
                }

                if ((ackHeader.typeAndFlags() & 0b1111_1111) != PUBREL_FIXED_HEADER)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                server.onDecodePubrel(traceId, authorization, buffer, progress, limit, ackHeader.packetId());
                progress = pubrelLimit;
                server.decoder = decodePacketTypeByVersion.get(server.version);
            }

            if (reasonCode != SUCCESS)
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodePubcompV4(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        if (length > 0)
        {
            int reasonCode = SUCCESS;

            final MqttPubcompV4FW pubcomp = mqttPubcompV4RO.tryWrap(buffer, offset, limit);
            decode:
            {
                if (pubcomp == null)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }
                else if ((pubcomp.typeAndFlags() & 0b1111_1111) != PUBCOMP_FIXED_HEADER)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                server.onDecodePubcomp(traceId, authorization, buffer, progress, limit, pubcomp.packetId(), SUCCESS);
                progress = pubcomp.limit();
                server.decoder = decodePacketTypeByVersion.get(server.version);
            }

            if (reasonCode != SUCCESS)
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodePubcompV5(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        if (length > 0)
        {
            int reasonCode = SUCCESS;

            decode:
            {
                final MqttAckHeaderFW ackHeader = mqttAckHeaderRO.tryWrap(buffer, offset, limit);
                if (ackHeader == null)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }
                int pubcompLimit = ackHeader.limit();
                final int remainingLength = ackHeader.remainingLength();
                if (remainingLength > 2)
                {
                    final MqttPubcompV5FW pubrec = mqttPubcompV5RO.tryWrap(buffer, offset, limit);
                    if (pubrec == null)
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    pubcompLimit = pubrec.limit();
                    if (remainingLength >= 4)
                    {
                        final MqttPropertiesFW properties = mqttPropertiesRO
                            .tryWrap(buffer, pubcompLimit, limit);
                        if (properties == null)
                        {
                            reasonCode = MALFORMED_PACKET;
                            break decode;
                        }
                        pubcompLimit = properties.limit();
                    }
                }

                if ((ackHeader.typeAndFlags() & 0b1111_1111) != PUBCOMP_FIXED_HEADER)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                server.onDecodePubcomp(traceId, authorization, buffer, progress, limit, ackHeader.packetId(), SUCCESS);
                progress = pubcompLimit;
                server.decoder = decodePacketTypeByVersion.get(server.version);
            }

            if (reasonCode != SUCCESS)
            {
                server.onDecodeError(traceId, authorization, reasonCode);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private boolean invalidUtf8(
        OctetsFW payload)
    {
        return !utf8Validator.isWellFormed(payload.buffer(), payload.offset(), payload.sizeof());
    }

    private int decodeSubscribeV4(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
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
            if (subscribeV4 == null || subscribeV4.payload().sizeof() == 0 || subscribeV4.packetId() == 0)
            {
                reasonCode = subscribeV4 == null ? MALFORMED_PACKET : PROTOCOL_ERROR;
            }
            else if ((subscribeV4.typeAndFlags() & 0b1111_1111) != SUBSCRIBE_FIXED_HEADER)
            {
                reasonCode = MALFORMED_PACKET;
            }

            if (reasonCode == 0)
            {
                if (!StreamState.replyOpening(server.session.state))
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
        final DirectBufferEx buffer,
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
            if (subscribeV5 == null || subscribeV5.payload().sizeof() == 0 || subscribeV5.packetId() == 0)
            {
                reasonCode = subscribeV5 == null ? MALFORMED_PACKET : PROTOCOL_ERROR;
            }
            else if ((subscribeV5.typeAndFlags() & 0b1111_1111) != SUBSCRIBE_FIXED_HEADER)
            {
                reasonCode = MALFORMED_PACKET;
            }

            if (reasonCode == 0)
            {
                if (!StreamState.replyOpening(server.session.state))
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
        final DirectBufferEx buffer,
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
                reasonCode = unsubscribeV4 == null ? MALFORMED_PACKET : PROTOCOL_ERROR;
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
        final DirectBufferEx buffer,
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
                reasonCode = unsubscribeV5 == null ? MALFORMED_PACKET : PROTOCOL_ERROR;
            }
            else if ((unsubscribeV5.typeAndFlags() & 0b1111_1111) != UNSUBSCRIBE_FIXED_HEADER)
            {
                reasonCode = MALFORMED_PACKET;
            }
            else
            {
                reasonCode = decodeUnsubscribeProperties(unsubscribeV5.properties());
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

    private int decodeUnsubscribeProperties(
        MqttPropertiesFW properties)
    {
        int reasonCode = SUCCESS;

        final OctetsFW propertiesValue = properties.value();
        final DirectBufferEx propertiesBuffer = propertiesValue.buffer();
        final int propertiesOffset = propertiesValue.offset();
        final int propertiesLimit = propertiesValue.limit();

        decodeProperties:
        for (int propertiesProgress = propertiesOffset; propertiesProgress < propertiesLimit; )
        {
            final MqttPropertyFW mqttProperty =
                mqttPropertyRO.tryWrap(propertiesBuffer, propertiesProgress, propertiesLimit);
            if (mqttProperty == null)
            {
                reasonCode = MALFORMED_PACKET;
                break decodeProperties;
            }

            switch (mqttProperty.kind())
            {
            case KIND_USER_PROPERTY:
                break;
            default:
                reasonCode = MALFORMED_PACKET;
                break decodeProperties;
            }

            propertiesProgress = mqttProperty.limit();
        }

        return reasonCode;
    }

    private int decodePingreq(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
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
                server.onDecodeError(traceId, authorization, MALFORMED_PACKET);
                server.decoder = decodeIgnoreAll;
            }
            else if ((ping.typeAndFlags() & 0b1111_1111) != PINGREQ_FIXED_HEADER || ping.remainingLength() != 0)
            {
                server.onDecodeError(traceId, authorization, MALFORMED_PACKET);
                server.decoder = decodeIgnoreAll;
            }
            else if (server.connected)
            {
                server.onDecodePingReq(traceId, authorization, ping);
                server.decoder = decodePacketTypeByVersion.get(server.version);
                progress = ping.limit();
            }
            else
            {
                server.decoder = decodeAwaitConnack;
            }
        }

        return progress;
    }

    private int decodeDisconnectV4(
        MqttServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBufferEx buffer,
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
                reasonCode = MALFORMED_PACKET;
            }
            else if ((disconnectV4.typeAndFlags() & 0b1111_1111) != DISCONNECT_FIXED_HEADER ||
                disconnectV4.remainingLength() != 0)
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
        final DirectBufferEx buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        if (length > 0)
        {
            int reasonCode = SUCCESS;

            final MqttPacketHeaderFW header = mqttPacketHeaderRO.tryWrap(buffer, offset, limit);
            if (header == null)
            {
                reasonCode = MALFORMED_PACKET;
            }
            else if ((header.typeAndFlags() & 0b1111_1111) != DISCONNECT_FIXED_HEADER)
            {
                reasonCode = MALFORMED_PACKET;
            }
            else
            {
                switch (header.remainingLength())
                {
                case 0:
                    server.onDecodeDisconnectV5(traceId, authorization, NORMAL_DISCONNECT, null);
                    progress = header.limit();
                    server.decoder = decodePacketTypeByVersion.get(server.version);
                    break;
                case 1:
                    server.onDecodeDisconnectV5(traceId, authorization, buffer.getByte(header.limit()) & 0xff, null);
                    progress = header.limit() + 1;
                    server.decoder = decodePacketTypeByVersion.get(server.version);
                    break;
                default:
                    final MqttDisconnectV5FW disconnectV5 = mqttDisconnectV5RO.tryWrap(buffer, offset, limit);
                    if (disconnectV5 == null)
                    {
                        reasonCode = MALFORMED_PACKET;
                    }
                    else
                    {
                        server.onDecodeDisconnectV5(traceId, authorization, disconnectV5.reasonCode(),
                            disconnectV5.properties());
                        progress = disconnectV5.limit();
                        server.decoder = decodePacketTypeByVersion.get(server.version);
                    }
                    break;
                }
            }

            if (reasonCode != SUCCESS)
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
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return limit;
    }

    private int decodeAwaitOwnership(
        MqttServer server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return offset;
    }

    private int decodeAwaitConnack(
        MqttServer server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return offset;
    }

    private int decodeUnknownType(
        MqttServer server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final MqttPacketHeaderFW packet = mqttPacketHeaderRO.tryWrap(buffer, offset, limit);
        final boolean reservedType = packet != null && (packet.typeAndFlags() >> 4) == 0;

        server.onDecodeError(traceId, authorization, reservedType ? MALFORMED_PACKET : PROTOCOL_ERROR);
        server.decoder = decodeIgnoreAll;
        return limit;
    }

    @FunctionalInterface
    private interface MqttSubscribeDecoder
    {
        void decode(
            DataFW data);
    }

    @FunctionalInterface
    private interface MqttServerDecoder
    {
        int decode(
            MqttServer server,
            long traceId,
            long authorization,
            long budgetId,
            DirectBufferEx buffer,
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

        private Long2ObjectHashMap<MqttPublishStream> publishes;
        private Long2ObjectHashMap<Int2ObjectHashMap<MqttSubscribeStream>> subscribes;
        private Long2ObjectHashMap<Map<String, MqttSubscribeStream>> sharedSubscribes;
        private List<MqttSubscribeStream> drainingSubscribes;
        private List<PendingSuback> pendingSubacks;
        private Int2ObjectHashMap<String> topicAliases;
        private Map<String, Integer> filterIds;
        private PendingRequest pendingRequests;
        private final GuardHandler guard;
        private final Function<String, String> credentials;
        private final MqttConnectProperty authField;
        private final List<MqttVersion> versions;
        private final StoreHandler store;
        private final Consumer<String> onOwnershipRenewedRef = this::onOwnershipRenewed;

        private int filterIdSequence;
        private String ownerKey;
        private MqttClientStateEntry clientState;
        private MqttSharedRegistry registry;
        private String ownerToken;
        private long renewAt = NO_CANCEL_ID;
        private long stealAt = NO_CANCEL_ID;
        private long releaseAt = NO_CANCEL_ID;
        private long pendingOwnershipAuth;
        private boolean owns;
        private boolean claimed;
        private boolean ownershipResolved;
        private Closeable ownerWatch;
        private Closeable stateWatch;
        private long renewTraceId;
        private long pendingSessionRoutedId;
        private long pendingSessionCompositeId;
        private int pendingConnectPayloadLimit;
        private boolean pendingConnectWillFlagSet;

        private MqttSessionStream session;

        private String clientId;
        private UnsafeBufferEx clientIdBuffer;

        private long affinity;
        private long decodeSeq;
        private long decodeAck;
        private int decodeMax;

        private long encodeSeq;
        private long encodeAck;
        private int encodeMax;
        private int encodePad;

        private BudgetCredit encodeCredit;
        private int encodeSharedBudget;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private long encodeSlotTraceId;

        private MqttServerDecoder decoder;
        private long decodePublisherKey;
        private int decodeablePublishPacketBytes;
        private int decodeablePacketBytes;
        private int publishPayloadDeferred;
        public int decodeablePublishPayloadBytes;
        private boolean publishPayloadModeling;
        private int publishPayloadModelProgress;
        private int publishPayloadConsumable;
        private boolean publishPayloadValidating;
        private int willPayloadDeferred;
        public int willPayloadBytes;

        private long connectTimeoutId = NO_CANCEL_ID;
        private long connectTimeoutAt;

        private long keepAliveTimeoutId = NO_CANCEL_ID;
        private long keepAliveTimeoutAt;

        private MqttQoS publishQosMax = publishQosMaxDefault;
        private int subscribeQosMax = subscribeQosMaxDefault;
        private int clientMaximumPacketSize = Integer.MAX_VALUE;
        private int receiveMaximum = RECEIVE_MAXIMUM_DEFAULT;
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
        private long guardSessionId = NOT_AUTHORIZED;
        private long guardSessionExpiringId = NO_CANCEL_ID;
        private int decodableRemainingBytes;
        private Int2ObjectHashMap<MqttSubscribeStream> awaitingAcknowledgeSubscribes;
        private List<MqttPublishStream> unAckedReceivedPublishes;
        private List<MqttSubscribeStream> heldSubscribes;

        private IntArrayList unreleasedPacketIds;

        private int version = MQTT_PROTOCOL_VERSION_5;
        private int decodedQos;
        private int decodedPacketId;
        private boolean decodedRetained = false;
        private int decodedFlags;
        private int decodedExpiryInterval = DEFAULT_EXPIRY_INTERVAL;
        private DecodedBytes decodedContentType;
        private MqttPayloadFormat decodedPayloadFormat = DEFAULT_FORMAT;
        private DecodedBytes decodedResponseTopic;
        private DecodedBytes decodedCorrelationData;
        private DecodedBytes decodedUserProperties;
        private Consumer<MqttPublishDataExFW.Builder> publishDataExBuilder;

        private MqttServer(
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            List<MqttVersion> versions,
            GuardHandler guard,
            Function<String, String> credentials,
            MqttConnectProperty authField,
            StoreHandler store)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.versions = versions;
            this.guard = guard;
            this.credentials = credentials;
            this.authField = authField;
            this.store = store;
            this.encodeBudgetId = supplyBudgetId.getAsLong();
            this.decoder = decodeInitialType;
            this.publishes = Long2ObjectHashMaps.emptyMap();
            this.subscribes = Long2ObjectHashMaps.emptyMap();
            this.sharedSubscribes = Long2ObjectHashMaps.emptyMap();
            this.drainingSubscribes = Collections.emptyList();
            this.pendingSubacks = Collections.emptyList();
            this.topicAliases = Int2ObjectHashMaps.emptyMap();
            this.filterIds = Collections.emptyMap();
            this.unAckedReceivedPublishes = Collections.emptyList();
            this.heldSubscribes = Collections.emptyList();
            this.awaitingAcknowledgeSubscribes = Int2ObjectHashMaps.emptyMap();
            this.unreleasedPacketIds = IntArrayLists.emptyList();
        }

        private void onNetwork(
            int msgTypeId,
            DirectBufferEx buffer,
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

            state = StreamState.openingInitial(state);
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

                DirectBufferEx buffer = payload.buffer();
                int offset = payload.offset();
                int limit = payload.limit();
                int reserved = data.reserved();

                if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBufferEx slotBuffer = bufferPool.buffer(decodeSlot);
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
                final boolean disconnected = StreamState.initialClosing(state);

                state = StreamState.closeInitial(state);

                if (disconnected || !isSetWillFlag(connectFlags))
                {
                    closeStreams(traceId, authorization);
                }
                else
                {
                    // a will was set but no DISCONNECT was ever decoded, so this is not a clean
                    // disconnect per MQTT-3.14.4-3 - clean up as if the network had aborted
                    cleanupStreamsUsingAbort(traceId);
                    doCancelTimeouts();
                    doReleaseOwnership(traceId);
                    decoder = decodeIgnoreAll;
                }

                doNetworkEnd(traceId, authorization);
            }
            else
            {
                // a decode is still deferred (e.g. awaiting session window for a will payload);
                // mark closing and let it complete via the session window retry in decodeNetwork
                state = StreamState.closingInitial(state);
            }
        }

        private void onNetworkAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            if (decodeSlot == NO_SLOT)
            {
                state = StreamState.closeInitial(state);

                cleanupNetwork(traceId, authorization);
            }
            else
            {
                // a decode is still deferred (e.g. awaiting session window for a will payload);
                // mark closing and let it complete via the session window retry in decodeNetwork,
                // rather than discarding the deferred payload along with the dead network -
                // doNetworkReset is skipped here since it would cleanupDecodeSlot() as a side effect
                state = StreamState.closingInitial(state);

                doReleaseOwnership(traceId);
                doNetworkAbort(traceId, authorization);
            }
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

            state = StreamState.openReply(state);

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
                final MutableDirectBufferEx buffer = bufferPool.buffer(encodeSlot);
                final int offset = 0;
                final int limit = encodeSlotOffset;

                encodeNetwork(encodeSlotTraceId, authorization, budgetId, buffer, offset, limit);
            }

            final int encodeWin = encodeMax - (int) (encodeSeq - encodeAck);
            final int encodeSharedCredit = Math.min(encodeBudgetMax, encodeWin - encodeSlotOffset - encodeSharedBudget);

            if (encodeSharedCredit > 0)
            {
                encodeCredit.capacity(traceId, encodeSharedCredit);
                encodeSharedBudget += encodeSharedCredit;

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
            case GUARD_SESSION_EXPIRING_SIGNAL:
                onGuardSessionExpiringSignal(signal);
                break;
            case CONNECT_TIMEOUT_SIGNAL:
                onConnectTimeoutSignal(signal);
                break;
            case SIGNAL_RENEW_SESSION_OWNERSHIP:
                onRenewOwnership(signal.traceId());
                break;
            case SIGNAL_STEAL_SESSION_OWNERSHIP:
                onStealOwnership(signal.traceId(), signal.authorization());
                break;
            case SIGNAL_ACQUIRE_SESSION_OWNERSHIP:
                onAcquireOwnership(signal.traceId());
                break;
            case SIGNAL_AWAIT_STATE_RELEASE:
                onAwaitStateReleaseTimeout(signal.traceId());
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

            keepAliveTimeoutId = NO_CANCEL_ID;

            final long now = clock.millis();
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

        private void onGuardSessionExpiringSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            guardSessionExpiringId = NO_CANCEL_ID;

            if ((guardSessionId & MASK_AUTHORIZED) != 0)
            {
                if (clock.millis() >= guard.expiresAt(guardSessionId))
                {
                    if (session != null)
                    {
                        session.doSessionAbort(traceId);
                    }
                    onDecodeError(traceId, authorization, MqttReasonCodes.NOT_AUTHORIZED);
                    decoder = decodeIgnoreAll;
                }
                else
                {
                    doScheduleGuardSessionExpiring(traceId);
                }
            }
        }

        private void doScheduleGuardSessionExpiring(
            long traceId)
        {
            final long expiringAt = guard.expiringAt(guardSessionId);
            final long checkAt = expiringAt > clock.millis() ? expiringAt : guard.expiresAt(guardSessionId);
            if (checkAt != EXPIRES_NEVER)
            {
                guardSessionExpiringId = signaler.signalAt(checkAt, originId, routedId, replyId, traceId,
                    GUARD_SESSION_EXPIRING_SIGNAL, 0);
            }
        }

        private void onConnectTimeoutSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            connectTimeoutId = NO_CANCEL_ID;

            final long now = clock.millis();
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

        private void doCancelKeepAliveTimeout()
        {
            if (keepAliveTimeoutId != NO_CANCEL_ID)
            {
                signaler.cancel(keepAliveTimeoutId);
                keepAliveTimeoutId = NO_CANCEL_ID;
            }
        }

        private void doCancelTimeouts()
        {
            doCancelConnectTimeout();
            doCancelKeepAliveTimeout();
        }

        private byte decodeConnectProperties(
            MqttPropertiesFW properties)
        {
            byte reasonCode = SUCCESS;

            final OctetsFW propertiesValue = properties.value();
            final DirectBufferEx decodeBuffer = propertiesValue.buffer();
            final int decodeOffset = propertiesValue.offset();
            final int decodeLimit = propertiesValue.limit();

            decode:
            for (int decodeProgress = decodeOffset; decodeProgress < decodeLimit; )
            {
                final MqttPropertyFW mqttProperty = mqttPropertyRO.tryWrap(decodeBuffer, decodeProgress, decodeLimit);
                if (mqttProperty == null)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

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
                    final int receiveMaximum = mqttProperty.receiveMaximum() & 0xFFFF;
                    if (receiveMaximum == 0 || isSetReceiveMaximum(decodablePropertyMask))
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    this.receiveMaximum = receiveMaximum;
                    this.decodablePropertyMask |= CONNECT_RECEIVE_MAXIMUM_MASK;
                    break;
                case KIND_MAXIMUM_PACKET_SIZE:
                    final int maxConnectPacketSize = (int) mqttProperty.maximumPacketSize();
                    if (maxConnectPacketSize == 0 || isSetMaximumPacketSize(decodablePropertyMask))
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    clientMaximumPacketSize = maxConnectPacketSize;
                    this.decodablePropertyMask |= CONNECT_MAXIMUM_PACKET_SIZE_MASK;
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
            DirectBufferEx buffer,
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
                this.clientIdBuffer = encodeClientId(new String16FW(clientId));
                this.assignedClientId = true;
            }
            else
            {
                this.clientId = clientIdentifier.asString();
                this.clientIdBuffer = encodeClientId(clientIdentifier);
            }

            this.keepAlive = (short) Math.min(Math.max(keepAlive, keepAliveMinimum), keepAliveMaximum);
            serverDefinedKeepAlive = this.keepAlive != keepAlive;
            keepAliveTimeout = Math.round(TimeUnit.SECONDS.toMillis(this.keepAlive) * 1.5);
            connectFlags = flags;
            version = protocolVersion;
            if (version == MQTT_PROTOCOL_VERSION_4 && !isCleanStart(flags))
            {
                sessionExpiry = SESSION_EXPIRY_NEVER;
            }
            doSignalKeepAliveTimeout(traceId);
            doCancelConnectTimeout();

            return progress;
        }

        private int onDecodeConnectPayload(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int progress,
            int limit)
        {
            final int offset = progress;
            byte reasonCode = SUCCESS;
            decode:
            {
                final MqttConnectPayload payload = mqttConnectPayloadRO.reset();
                int connectPayloadLimit = payload.decode(buffer, progress, limit, connectFlags, version);

                final boolean willFlagSet = isSetWillFlag(connectFlags);

                reasonCode = payload.reasonCode;

                if (reasonCode == SUCCESS && !willFlagSet && decodableRemainingBytes > connectPayloadLimit - offset)
                {
                    reasonCode = MALFORMED_PACKET;
                }

                if (reasonCode != SUCCESS)
                {
                    break decode;
                }

                long sessionAuth = authorization;
                boolean credentialsRejected = false;
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
                        sessionAuth = guard.reauthorize(traceId, routedId, initialId, credentialsMatch);
                        guardSessionId = sessionAuth;
                        credentialsRejected = sessionAuth == NOT_AUTHORIZED;
                    }
                }

                final MqttBindingConfig binding = bindings.get(routedId);

                final MqttRouteConfig resolved = binding != null ?
                    binding.resolveSession(sessionAuth, clientId) : null;

                if (resolved == null)
                {
                    reasonCode = credentialsRejected ? BAD_USER_NAME_OR_PASSWORD : MqttReasonCodes.NOT_AUTHORIZED;
                    break decode;
                }

                this.sessionId = sessionAuth;
                this.pendingSessionRoutedId = resolved.id;
                this.pendingSessionCompositeId = resolved.compositeId();
                this.pendingConnectPayloadLimit = connectPayloadLimit;
                this.pendingConnectWillFlagSet = willFlagSet;

                if (guard != null && (guardSessionId & MASK_AUTHORIZED) != 0)
                {
                    doScheduleGuardSessionExpiring(traceId);
                }

                if (store != null && !ownershipResolved)
                {
                    decoder = decodeAwaitOwnership;

                    if (!willFlagSet)
                    {
                        progress = connectPayloadLimit;
                    }

                    pendingOwnershipAuth = authorization;
                    signaler.signalNow(originId, routedId, replyId, traceId, SIGNAL_ACQUIRE_SESSION_OWNERSHIP, 0);
                }
                else
                {
                    doEstablishSession(traceId);

                    if (willFlagSet)
                    {
                        decoder = decodeConnectWillMessage;
                    }
                    else
                    {
                        progress = connectPayloadLimit;
                    }
                }
            }

            if (reasonCode != SUCCESS)
            {
                doCancelConnectTimeout();

                onDecodeError(traceId, authorization, reasonCode);
                decoder = decodeIgnoreAll;

                if (session != null)
                {
                    session.doSessionEnd(traceId, EMPTY_OCTETS);
                }
                doNetworkEnd(traceId, authorization);

                decoder = decodeIgnoreAll;
                progress = limit;
            }

            return progress;
        }

        private void doEstablishSession(
            long traceId)
        {
            if (session == null)
            {
                this.session = new MqttSessionStream(routedId, pendingSessionRoutedId, 0);
            }

            final int sessionCapabilities = versions.contains(MqttVersion.V_5) && versions.size() == 1
                ? REDIRECT_MASK : 0;

            final MqttBeginExFW.Builder builder = mqttSessionBeginExRW
                .wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                .compositeId(pendingSessionCompositeId)
                .typeId(mqttTypeId)
                .session(s -> s
                    .flags(connectFlags & (CLEAN_START_FLAG_MASK | WILL_FLAG_MASK))
                    .expiry(sessionExpiry)
                    .publishQosMax(publishQosMax.value())
                    .capabilities(sessionCapabilities)
                    .clientId(clientId()));

            session.doSessionBegin(traceId, affinity, builder.build());
        }

        private void onAcquireOwnership(
            long traceId)
        {
            if (decoder == decodeAwaitOwnership && !StreamState.initialClosed(state) && !StreamState.replyClosed(state))
            {
                doAcquireOwnership(traceId, pendingOwnershipAuth);
            }
        }

        private void doAcquireOwnership(
            long traceId,
            long authorization)
        {
            this.ownerKey = clientId + OWNER_KEY_POSTFIX;
            store.get(ownerKey, (key, value) -> onOwnershipResolved(traceId, authorization, value));
        }

        private void onOwnershipResolved(
            long traceId,
            long authorization,
            String value)
        {
            if (StreamState.initialClosed(state) || StreamState.replyClosed(state))
            {
                return;
            }

            final OwnershipRecord owner = OwnershipRecord.decode(value);

            if (owner != null && owner.strong && !ownerIdentity.equals(owner.identity))
            {
                doRedirect(traceId, authorization, owner.identity);
            }
            else
            {
                claimed = true;
                store.lock(ownerKey, sessionLease, (key, token) -> onOwnershipLocked(traceId, authorization, token));
            }
        }

        private void onOwnershipLocked(
            long traceId,
            long authorization,
            String token)
        {
            if (StreamState.initialClosed(state) || StreamState.replyClosed(state))
            {
                if (token != null)
                {
                    store.unlock(ownerKey, token, ignored -> {});
                }
                return;
            }

            if (token != null)
            {
                completeOwnership(traceId, token, this::onClientStateLoaded);
            }
            else if (claimed)
            {
                stealAt = signaler.signalAt(clock.millis() + sessionRenew.toMillis(),
                    originId, routedId, replyId, traceId, SIGNAL_STEAL_SESSION_OWNERSHIP, 0);
            }
        }

        private void completeOwnership(
            long traceId,
            String token,
            LongConsumer stateLoaded)
        {
            owns = true;
            ownerToken = token;
            ownershipResolved = true;
            store.put(ownerKey, ownershipRecord(token), sessionLease, ignored -> {});
            ownerWatch = store.watch(ownerKey, (key, value) -> onOwnershipChallenged(traceId, value));
            renewAt = signaler.signalAt(clock.millis() + sessionRenew.toMillis(),
                originId, routedId, replyId, traceId, SIGNAL_RENEW_SESSION_OWNERSHIP, 0);

            registry = new MqttSharedRegistry(store);
            clientState = new MqttClientStateEntry(store, clientId + STATE_KEY_POSTFIX, clientStates, this::clientStateTtl);
            clientState.awaitLoaded(() -> stateLoaded.accept(traceId));
        }

        private void onClientStateLoaded(
            long traceId)
        {
            final boolean ready = clientState.ready();

            if (ready && !isCleanStart(connectFlags) && clientState.held())
            {
                connectFlags |= CLEAN_START_FLAG_MASK;
            }

            final boolean discard = ready && isCleanStart(connectFlags) && (!clientState.empty() || clientState.held());
            final List<MqttMembershipRecord> memberships = discard ? clientState.discard() : List.of();
            final Runnable stored = () -> leaveShared(memberships, () -> onClientStateReady(traceId));

            if (ready && sessionExpiry > 0)
            {
                clientState.hold(ownerNonce(), stored);
            }
            else if (discard)
            {
                clientState.write(stored);
            }
            else
            {
                onClientStateReady(traceId);
            }
        }

        private void onClientStateLoadedAfterSteal(
            long traceId)
        {
            if (clientState.ready() && clientState.held() && !isCleanStart(connectFlags))
            {
                stateWatch = store.watch(clientId + STATE_KEY_POSTFIX, (key, value) -> onClientStateChanged(traceId));
                releaseAt = signaler.signalAt(clock.millis() + sessionLease.toMillis(),
                    originId, routedId, replyId, traceId, SIGNAL_AWAIT_STATE_RELEASE, 0);
                clientState.reload(() -> onClientStateReloaded(traceId));
            }
            else
            {
                onClientStateLoaded(traceId);
            }
        }

        private void onClientStateChanged(
            long traceId)
        {
            if (stateWatch != null)
            {
                clientState.reload(() -> onClientStateReloaded(traceId));
            }
        }

        private void onClientStateReloaded(
            long traceId)
        {
            if (stateWatch != null && (!clientState.ready() || !clientState.held()))
            {
                doCancelAwaitStateRelease();
                onClientStateLoaded(traceId);
            }
        }

        private void onAwaitStateReleaseTimeout(
            long traceId)
        {
            releaseAt = NO_CANCEL_ID;

            if (stateWatch != null)
            {
                doCancelAwaitStateRelease();
                onClientStateLoaded(traceId);
            }
        }

        private void doCancelAwaitStateRelease()
        {
            if (releaseAt != NO_CANCEL_ID)
            {
                signaler.cancel(releaseAt);
                releaseAt = NO_CANCEL_ID;
            }

            final Closeable watch = stateWatch;
            stateWatch = null;

            if (watch != null)
            {
                try
                {
                    watch.close();
                }
                catch (IOException ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                }
            }
        }

        private void onClientStateReady(
            long traceId)
        {
            if (!StreamState.initialClosed(state) && !StreamState.replyClosed(state))
            {
                doEstablishSession(traceId);

                if (decoder == decodeAwaitOwnership)
                {
                    decoder = pendingConnectWillFlagSet
                        ? decodeConnectWillMessage
                        : decodePacketTypeByVersion.get(version);
                }

                decodeNetwork(traceId);
            }
        }

        private Duration clientStateTtl()
        {
            return sessionExpiry > 0 ? Duration.ofSeconds(sessionExpiry) : null;
        }

        private void releaseClientState(
            Runnable continuation)
        {
            if (clientState != null)
            {
                clientState.release(continuation);
            }
            else
            {
                continuation.run();
            }
        }

        private void awaitClientStateIdle(
            Runnable continuation)
        {
            if (clientState != null)
            {
                clientState.awaitIdle(continuation);
            }
            else
            {
                continuation.run();
            }
        }

        private void joinShared(
            String shareName,
            String filter,
            LongConsumer joined,
            Runnable failed)
        {
            final MqttMembershipRecord membership = new MqttMembershipRecord(shareName, filter);
            final String key = MqttSharedRegistry.key(shareName, filter);

            if (clientState.memberships().contains(membership))
            {
                registry.epoch(key, joined, failed);
            }
            else
            {
                registry.join(key, epoch -> onSharedJoined(membership, epoch, joined), failed);
            }
        }

        private void onSharedJoined(
            MqttMembershipRecord membership,
            long epoch,
            LongConsumer joined)
        {
            if (StreamState.initialClosed(state) || StreamState.replyClosed(state))
            {
                registry.leave(MqttSharedRegistry.key(membership.shareName, membership.filter), () -> {});
            }
            else
            {
                clientState.memberships().add(membership);
                clientState.write(() -> joined.accept(epoch));
            }
        }

        private void leaveShared(
            List<MqttMembershipRecord> memberships,
            Runnable completed)
        {
            if (memberships.isEmpty())
            {
                completed.run();
            }
            else
            {
                final MqttMembershipRecord membership = memberships.get(0);
                registry.leave(MqttSharedRegistry.key(membership.shareName, membership.filter),
                    () -> leaveShared(memberships.subList(1, memberships.size()), completed));
            }
        }

        private void onStealOwnership(
            long traceId,
            long authorization)
        {
            stealAt = NO_CANCEL_ID;

            if (StreamState.initialClosed(state) || StreamState.replyClosed(state))
            {
                return;
            }

            if (!owns && claimed)
            {
                store.get(ownerKey, (key, value) ->
                {
                    if (StreamState.initialClosed(state) || StreamState.replyClosed(state))
                    {
                        return;
                    }

                    final OwnershipRecord existing = OwnershipRecord.decode(value);
                    if (existing != null && existing.token != null)
                    {
                        store.unlock(ownerKey, existing.token, ignored -> {});
                    }

                    store.lock(ownerKey, sessionLease,
                        (lockKey, token) -> onStealComplete(traceId, authorization, token));
                });
            }
        }

        private void onStealComplete(
            long traceId,
            long authorization,
            String token)
        {
            if (StreamState.initialClosed(state) || StreamState.replyClosed(state))
            {
                if (token != null)
                {
                    store.unlock(ownerKey, token, ignored -> {});
                }
                return;
            }

            if (token != null)
            {
                completeOwnership(traceId, token, this::onClientStateLoadedAfterSteal);
            }
            else
            {
                rejectWithServerBusy(traceId, authorization);
            }
        }

        private void rejectWithServerBusy(
            long traceId,
            long authorization)
        {
            doCancelConnectTimeout();
            doEncodeConnack(traceId, authorization, SERVER_BUSY, assignedClientId, false,
                null, null, version);
            doNetworkEnd(traceId, authorization);
            decoder = decodeIgnoreAll;
            cleanupDecodeSlot();
        }

        private void onRenewOwnership(
            long traceId)
        {
            renewAt = NO_CANCEL_ID;
            if (owns && ownerToken != null)
            {
                renewTraceId = traceId;
                store.renew(ownerKey, ownerToken, sessionLease, onOwnershipRenewedRef);
            }
        }

        private void onOwnershipRenewed(
            String token)
        {
            final long traceId = renewTraceId;
            if (token != null)
            {
                // the entry lifetime is independent of the lock lease, so it is rewritten with each renewal;
                // StoreHandler.touch is expected to replace this rewrite
                store.put(ownerKey, ownershipRecord(token), sessionLease, ignored -> {});
                renewAt = signaler.signalAt(clock.millis() + sessionRenew.toMillis(),
                    originId, routedId, replyId, traceId, SIGNAL_RENEW_SESSION_OWNERSHIP, 0);
            }
            else if (owns)
            {
                doSurrenderOwnership(traceId);
            }
        }

        private void decodePublishProperties(
            MqttPublishHelper helper)
        {
            decodedContentType = decodeString(decodedContentType, helper.contentType);
            decodedResponseTopic = decodeString(decodedResponseTopic, helper.responseTopic);
            decodedCorrelationData = decodeBytes(decodedCorrelationData, helper.correlationData);
            decodedUserProperties = decodeBytes(decodedUserProperties,
                helper.userProperties != null && !helper.userProperties.isEmpty() ? helper.userProperties : null);
        }

        private DecodedBytes decodeString(
            DecodedBytes decoded,
            String16FW value)
        {
            DecodedBytes result = decoded;

            if (value.value() != null)
            {
                final int length = value.length();

                result = result != null ? result : new DecodedBytes();
                result.reserve(Short.BYTES + length);
                result.buffer.putShort(0, (short) length, nativeOrder());
                result.buffer.putBytes(Short.BYTES, value.buffer(), value.offset() + Short.BYTES, length);
            }
            else if (result != null)
            {
                result.clear();
            }

            return result;
        }

        private DecodedBytes decodeBytes(
            DecodedBytes decoded,
            Flyweight value)
        {
            DecodedBytes result = decoded;

            if (value != null)
            {
                result = result != null ? result : new DecodedBytes();
                result.reserve(value.sizeof());
                result.buffer.putBytes(0, value.buffer(), value.offset(), value.sizeof());
            }
            else if (result != null)
            {
                result.clear();
            }

            return result;
        }

        private String16FW contentType()
        {
            return decodedContentType != null && decodedContentType.length >= 0
                ? decodedContentTypeRO.wrap(decodedContentType.buffer, 0, decodedContentType.length)
                : NULL_STRING;
        }

        private String16FW responseTopic()
        {
            return decodedResponseTopic != null && decodedResponseTopic.length >= 0
                ? decodedResponseTopicRO.wrap(decodedResponseTopic.buffer, 0, decodedResponseTopic.length)
                : NULL_STRING;
        }

        private OctetsFW correlationData()
        {
            return decodedCorrelationData != null && decodedCorrelationData.length >= 0
                ? decodedCorrelationDataRO.wrap(decodedCorrelationData.buffer, 0, decodedCorrelationData.length)
                : null;
        }

        private Array32FW<MqttUserPropertyFW> userProperties()
        {
            return decodedUserProperties != null && decodedUserProperties.length >= 0
                ? decodedUserPropertiesRO.wrap(decodedUserProperties.buffer, 0, decodedUserProperties.length)
                : null;
        }

        private void buildPublishDataEx(
            MqttPublishDataExFW.Builder publish)
        {
            publish.deferred(publishPayloadDeferred)
                .qos(decodedQos)
                .flags(decodedFlags)
                .packetId(decodedPacketId)
                .expiryInterval(decodedExpiryInterval)
                .contentType(contentType())
                .format(payloadFormats[decodedPayloadFormat.ordinal()])
                .responseTopic(responseTopic())
                .correlation(c -> c.bytes(correlationData()));

            final Array32FW<MqttUserPropertyFW> userProperties = userProperties();
            if (userProperties != null)
            {
                final DirectBufferEx items = userProperties.items();
                final int limit = items.capacity();
                for (int offset = 0; offset < limit; offset = publishUserPropertyRO.limit())
                {
                    publishUserPropertyRO.wrap(items, offset, limit);
                    publish.propertiesItem(publishUserPropertyBuilder);
                }
            }
        }

        private String16FW clientId()
        {
            return clientIdRO.wrap(clientIdBuffer, 0, clientIdBuffer.capacity());
        }

        private String ownerNonce()
        {
            return replicaId + '-' + initialId;
        }

        private String ownershipRecord(
            String token)
        {
            ownershipRecordRW.setLength(0);

            return ownershipRecordRW
                .append(ownershipRecordHead)
                .append(replicaId)
                .append('-')
                .append(initialId)
                .append(OwnershipRecord.FIELD_SEPARATOR)
                .append(token)
                .toString();
        }

        private void onOwnershipChallenged(
            long traceId,
            String value)
        {
            final OwnershipRecord challenger = OwnershipRecord.decode(value);

            if (owns && challenger != null && !ownerNonce().equals(challenger.nonce))
            {
                doSurrenderOwnership(traceId);
            }
        }

        private void doSurrenderOwnership(
            long traceId)
        {
            owns = false;

            if (renewAt != NO_CANCEL_ID)
            {
                signaler.cancel(renewAt);
                renewAt = NO_CANCEL_ID;
            }

            if (ownerToken != null)
            {
                final String token = ownerToken;
                releaseClientState(() -> store.unlock(ownerKey, token, ignored -> {}));
                ownerToken = null;
            }

            if (isSetWillFlag(connectFlags) && session != null)
            {
                session.cleanupAbort(traceId);
            }
            onDecodeError(traceId, sessionId, SESSION_TAKEN_OVER);
        }

        private void doReleaseOwnership(
            long traceId)
        {
            doCancelAwaitStateRelease();

            if (owns && sessionExpiry == 0 && clientState != null && clientState.ready() && !clientState.empty())
            {
                final List<MqttMembershipRecord> memberships = clientState.discard();
                clientState.write(() -> leaveShared(memberships, () -> {}));
            }

            if (renewAt != NO_CANCEL_ID)
            {
                signaler.cancel(renewAt);
                renewAt = NO_CANCEL_ID;
            }

            if (stealAt != NO_CANCEL_ID)
            {
                signaler.cancel(stealAt);
                stealAt = NO_CANCEL_ID;
            }

            if (ownerWatch != null)
            {
                try
                {
                    ownerWatch.close();
                }
                catch (IOException ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                }
                ownerWatch = null;
            }

            if (owns && ownerToken != null)
            {
                final String token = ownerToken;
                releaseClientState(() -> store.unlock(ownerKey, token, ignored -> {}));
            }

            if (claimed && owns)
            {
                awaitClientStateIdle(() -> store.delete(ownerKey, ignored -> {}));
            }

            owns = false;
            claimed = false;
            ownerToken = null;
        }

        private void doRedirect(
            long traceId,
            long authorization,
            String serverRef)
        {
            doCancelConnectTimeout();
            doEncodeConnack(traceId, authorization, SERVER_MOVED, assignedClientId, false,
                new String16FW(serverRef), null, version);
            doNetworkEnd(traceId, authorization);
            decoder = decodeIgnoreAll;
            cleanupDecodeSlot();
        }

        private int onDecodeConnectWillMessage(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int progress,
            int limit)
        {
            byte reasonCode = SUCCESS;
            decode:
            {
                final MqttConnectPayload payload = mqttConnectPayloadRO.reset();
                final int connectPayloadLimit = payload.decode(buffer, progress, limit, connectFlags, version);

                willPayloadBytes = payload.payloadSize;
                final boolean willFlagSet = isSetWillFlag(connectFlags);

                reasonCode = payload.reasonCode;

                if (reasonCode != SUCCESS)
                {
                    break decode;
                }

                if (willFlagSet && !StreamState.initialOpened(session.state))
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
                }

                final int flags = connectFlags;
                final int willQos = decodeWillQos(flags);

                if (willQos > publishQosMax.value())
                {
                    reasonCode = QOS_NOT_SUPPORTED;
                    break decode;
                }

                // re-derived from buffer on every attempt, including retries - progress is not
                // committed past these bytes until they are actually handed to session.doSessionData,
                // so decodeNetwork's own decodeSlot buffering keeps them available for the next retry;
                // no snapshot needs to survive between calls, however large the will payload is
                final int willFlags = decodeWillFlags(flags);
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

                willMessageBuilder.payloadSize(payload.payloadSize);
                willMessageBuilder.build();
                final int headerSize = willMessageBuilder.sizeof();

                if (!session.hasSessionWindow(headerSize))
                {
                    // not enough session window even for the header yet - leave progress where it
                    // started so these bytes stay in decodeSlot for the next session window retry
                    break decode;
                }

                // bounded by whatever is currently available in buffer (network-limited),
                // the session window (backpressure-limited), and the declared will payload length
                final int payloadAvailable = Math.min(limit - connectPayloadLimit, willPayloadBytes);
                final int payloadSize = Math.min(payloadAvailable, session.initialBudget() - headerSize);
                final OctetsFW willPayload = payloadRO.wrap(buffer, connectPayloadLimit, connectPayloadLimit + payloadSize);

                willMessageBuffer.putBytes(headerSize, willPayload.buffer(), willPayload.offset(), willPayload.sizeof());

                final int deferred = willPayloadBytes - payloadSize;
                final int dataFlags = deferred > 0 ? INIT : COMPLETE;

                final MqttDataExFW.Builder sessionDataExBuilder =
                    mqttSessionDataExRW.wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                        .typeId(mqttTypeId)
                        .session(s -> s.deferred(deferred).kind(k -> k.set(MqttSessionDataKind.WILL)));

                final int publishedWillSize = session.doSessionData(traceId, dataFlags,
                    willMessageBuffer, 0, headerSize + willPayload.sizeof(), headerSize, sessionDataExBuilder.build());

                if (publishedWillSize < headerSize)
                {
                    // session stream pushed back before even the header went through - retry later
                    break decode;
                }

                willPayloadDeferred = deferred;
                willPayloadBytes -= payloadSize;
                progress = connectPayloadLimit + payloadSize;

                decoder = deferred > 0 ? decodeConnectWillMessagePayload : decodePacketTypeByVersion.get(version);
            }

            if (reasonCode != SUCCESS)
            {
                doCancelConnectTimeout();

                doEncodeConnack(traceId, authorization, reasonCode, assignedClientId, false, null, null, version);

                if (session != null)
                {
                    session.doSessionEnd(traceId, EMPTY_OCTETS);
                }
                doNetworkEnd(traceId, authorization);

                decoder = decodeIgnoreAll;
                progress = limit;
            }

            return progress;
        }

        private int onDecodeConnectWillPayload(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            assert willPayloadDeferred >= 0;

            // bounded by whatever is currently available in buffer (network-limited), the session
            // window (backpressure-limited), and what is left of the declared will payload - bytes
            // buffered beyond that boundary belong to the next control packet, not to the will
            final int payloadAvailable = Math.min(limit - offset, willPayloadDeferred);
            final int payloadSize = Math.min(payloadAvailable, session.initialBudget());
            final OctetsFW willPayload = payloadRO.wrap(buffer, offset, offset + payloadSize);

            final int flags = willPayloadDeferred - payloadSize > 0 ? NONE : FIN;

            final int publishedWillSize = session.doSessionData(traceId, flags,
                willPayload.buffer(), willPayload.offset(), willPayload.limit(), 0, EMPTY_OCTETS);
            willPayloadDeferred -= publishedWillSize;
            willPayloadBytes -= publishedWillSize;

            return offset + publishedWillSize;
        }

        private MqttPublishStream resolvePublishStream(
            long traceId,
            long authorization,
            long topicKey,
            String topic,
            int qos)
        {
            MqttPublishStream stream = null;

            final MqttBindingConfig binding = bindings.get(routedId);
            final MqttRouteConfig resolved = binding != null ?
                binding.resolvePublish(sessionId, topic) : null;

            if (resolved != null)
            {
                final long resolvedId = resolved.id;
                final long compositeId = resolved.compositeId();

                if (publishes == EMPTY_LONG_MAP)
                {
                    publishes = new Long2ObjectHashMap<>();
                }

                stream = publishes.computeIfAbsent(topicKey, s ->
                    new MqttPublishStream(routedId, resolvedId, compositeId, topic, topicKey, binding.supplyModelConfig(topic)));
                stream.doPublishBegin(traceId, affinity, qos);
            }

            return stream;
        }

        private boolean onDecodePublish(
            long traceId,
            long authorization,
            int qos,
            boolean retained,
            int packetId,
            int reasonCode)
        {
            int publishReasonCode = reasonCode;

            if (publishReasonCode == SUCCESS && retained && !retainAvailable(capabilities))
            {
                publishReasonCode = RETAIN_NOT_SUPPORTED;
            }

            if (publishReasonCode == SUCCESS)
            {
                if (unreleasedPacketIds.contains(packetId))
                {
                    switch (version)
                    {
                    case 4:
                        doEncodePubrecV4(traceId, authorization, packetId);
                        break;
                    case 5:
                        doEncodePubrecV5(traceId, authorization, packetId, SUCCESS);
                        break;
                    }
                }

                doSignalKeepAliveTimeout(traceId);
            }
            else if (acknowledgesFailedPublish(qos, publishReasonCode))
            {
                switch (qos)
                {
                case 1:
                    doEncodePubackV5(traceId, authorization, packetId, publishReasonCode);
                    break;
                case 2:
                    doEncodePubrecV5(traceId, authorization, packetId, publishReasonCode);
                    break;
                }

                decoder = decodeRejectedPublishPayload;
            }
            else
            {
                onDecodeError(traceId, authorization, publishReasonCode);
                decoder = decodeIgnoreAll;
            }

            return publishReasonCode == SUCCESS;
        }

        private boolean acknowledgesFailedPublish(
            int qos,
            int reasonCode)
        {
            return version == MQTT_PROTOCOL_VERSION_5 && qos > 0 && acknowledgesPublish(reasonCode);
        }

        private void onDecodePublishPayload(
            long traceId,
            long authorization,
            int reserved,
            OctetsFW payload,
            int offset,
            int limit)
        {
            int reasonCode = SUCCESS;

            if (mqttPublishHelper.payloadFormat.equals(MqttPayloadFormat.TEXT) && invalidUtf8(payload))
            {
                reasonCode = PAYLOAD_FORMAT_INVALID;
            }

            if (reasonCode != SUCCESS)
            {
                onDecodeError(traceId, authorization, reasonCode);
                decoder = decodeIgnoreAll;
            }
            else
            {
                final int length = limit - offset;

                if (!unreleasedPacketIds.contains(decodedPacketId))
                {
                    MqttPublishStream stream = publishes.get(decodePublisherKey);

                    if (publishPayloadDeferred == 0)
                    {
                        publishPayloadDeferred = decodeablePublishPayloadBytes - length;
                        publishDataExBuilder = publishDataExBuilder != null ? publishDataExBuilder : this::buildPublishDataEx;

                        final Flyweight dataEx = mqttPublishDataExRW
                            .wrap(dataExtBuffer, 0, dataExtBuffer.capacity())
                            .typeId(mqttTypeId)
                            .publish(publishDataExBuilder)
                            .build();

                        int dataFlags = publishPayloadDeferred > 0 ? INIT : COMPLETE;

                        if (stream != null)
                        {
                            stream.doPublishData(traceId, authorization, reserved, decodedPacketId, payload, dataFlags,
                                offset, limit, dataEx);
                            decodeablePublishPayloadBytes -= length;
                        }
                    }
                    else
                    {
                        publishPayloadDeferred -= length;
                        assert publishPayloadDeferred >= 0;
                        int dataFlags = publishPayloadDeferred > 0 ? NONE : FIN;

                        if (stream != null)
                        {
                            stream.doPublishData(traceId, authorization, reserved, decodedPacketId, payload, dataFlags,
                                offset, limit, EMPTY_OCTETS);
                            decodeablePublishPayloadBytes -= length;
                        }
                    }
                }
                else
                {
                    decodeablePublishPayloadBytes -= length;
                }
            }
        }

        private Subscription findByFilter(
            List<Subscription> candidates,
            String filter)
        {
            Subscription found = null;

            for (int index = 0; found == null && index < candidates.size(); index++)
            {
                final Subscription candidate = candidates.get(index);
                found = candidate.filter.equals(filter) ? candidate : null;
            }

            return found;
        }

        private int acknowledgeKey(
            int qos,
            int packetId)
        {
            return qos << Short.SIZE | packetId;
        }

        private int pendingPacketId(
            int kind,
            String filter)
        {
            PendingRequest pending = pendingRequests;
            while (pending != null && !pending.matches(kind, NO_PACKET_ID, filter))
            {
                pending = pending.next;
            }

            return pending != null ? pending.packetId : NO_PACKET_ID;
        }

        private void putPendingFilter(
            int kind,
            String filter,
            int packetId)
        {
            removePendingFilter(kind, filter);
            pendingRequests = new PendingRequest(kind, packetId, filter, null, pendingRequests);
        }

        private int removePendingFilter(
            int kind,
            String filter)
        {
            final PendingRequest removed = removePending(kind, NO_PACKET_ID, filter);
            return removed != null ? removed.packetId : NO_PACKET_ID;
        }

        private void putPendingFilters(
            int packetId,
            List<String> filters)
        {
            removePendingFilters(packetId);
            pendingRequests = new PendingRequest(PENDING_UNSUBSCRIBE_FILTERS, packetId, null, filters, pendingRequests);
        }

        private List<String> removePendingFilters(
            int packetId)
        {
            final PendingRequest removed = removePending(PENDING_UNSUBSCRIBE_FILTERS, packetId, null);
            return removed != null ? removed.filters : null;
        }

        private PendingRequest removePending(
            int kind,
            int packetId,
            String filter)
        {
            PendingRequest previous = null;
            PendingRequest pending = pendingRequests;
            while (pending != null && !pending.matches(kind, packetId, filter))
            {
                previous = pending;
                pending = pending.next;
            }

            if (pending != null)
            {
                if (previous != null)
                {
                    previous.next = pending.next;
                }
                else
                {
                    pendingRequests = pending.next;
                }
            }

            return pending;
        }

        private boolean receiveAvailable()
        {
            return version == MQTT_PROTOCOL_VERSION_4 ||
                awaitingAcknowledgeSubscribes.size() < receiveMaximum;
        }

        private void releaseHeldSubscribes(
            long traceId)
        {
            while (receiveAvailable() && !heldSubscribes.isEmpty())
            {
                heldSubscribes.removeFirst().releaseHeld(traceId);
            }
        }

        private void onDecodePuback(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int progress,
            int limit,
            int packetId,
            int reasonCode)
        {
            final MqttSubscribeStream stream =
                awaitingAcknowledgeSubscribes.remove(acknowledgeKey(MqttQoS.AT_LEAST_ONCE.ordinal(), packetId));

            if (stream != null)
            {
                stream.onSubscribeAcknowledge(traceId, MqttQoS.AT_LEAST_ONCE.ordinal(), packetId, QOS2_COMPLETE_OFFSET_STATE,
                    reasonCode, true);
                releaseHeldSubscribes(traceId);
            }

            doSignalKeepAliveTimeout(traceId);
        }

        private void onDecodePubrec(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int progress,
            int limit,
            int packetId,
            int reasonCode)
        {
            final boolean rejected = isError(reasonCode);
            final int acknowledgeKey = acknowledgeKey(MqttQoS.EXACTLY_ONCE.ordinal(), packetId);
            final MqttSubscribeStream stream = rejected ?
                awaitingAcknowledgeSubscribes.remove(acknowledgeKey) : awaitingAcknowledgeSubscribes.get(acknowledgeKey);

            if (stream != null)
            {
                stream.onSubscribeAcknowledge(traceId, MqttQoS.EXACTLY_ONCE.ordinal(), packetId,
                    rejected ? QOS2_COMPLETE_OFFSET_STATE : QOS2_INCOMPLETE_OFFSET_STATE, reasonCode, rejected);

                if (rejected)
                {
                    releaseHeldSubscribes(traceId);
                }
            }
            else if (!rejected)
            {
                switch (version)
                {
                case 4:
                    doEncodePubrelV4(traceId, authorization, packetId);
                    break;
                case 5:
                    doEncodePubrelV5(traceId, authorization, packetId, PACKET_IDENTIFIER_NOT_FOUND);
                    break;
                }
            }

            doSignalKeepAliveTimeout(traceId);
        }

        private void onDecodePubrel(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int progress,
            int limit,
            int packetId)
        {
            if (unreleasedPacketIds.contains(packetId))
            {
                session.doSessionFlush(traceId, 0, packetId);
            }
            else
            {
                switch (version)
                {
                case 4:
                    doEncodePubcompV4(traceId, authorization, packetId);
                    break;
                case 5:
                    doEncodePubcompV5(traceId, authorization, packetId, PACKET_IDENTIFIER_NOT_FOUND);
                    break;
                }
            }
            doSignalKeepAliveTimeout(traceId);
        }

        private void onDecodePubcomp(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int progress,
            int limit,
            int packetId,
            int reasonCode)
        {
            final MqttSubscribeStream stream =
                awaitingAcknowledgeSubscribes.remove(acknowledgeKey(MqttQoS.EXACTLY_ONCE.ordinal(), packetId));

            if (stream != null)
            {
                stream.onSubscribeAcknowledge(traceId, MqttQoS.EXACTLY_ONCE.ordinal(), packetId, QOS2_COMPLETE_OFFSET_STATE,
                    reasonCode, true);
                releaseHeldSubscribes(traceId);
            }

            doSignalKeepAliveTimeout(traceId);
        }

        private void onDecodeSubscribeV4(
            long traceId,
            long authorization,
            MqttSubscribeV4FW subscribe)
        {
            final int packetId = subscribe.packetId();
            final OctetsFW decodePayload = subscribe.payload();

            final DirectBufferEx decodeBuffer = decodePayload.buffer();
            final int decodeOffset = decodePayload.offset();
            final int decodeLimit = decodePayload.limit();

            final List<Subscription> newSubscriptions = new ArrayList<>();

            decode:
            {
                for (int decodeProgress = decodeOffset; decodeProgress < decodeLimit; )
                {
                    final MqttSubscribePayloadFW mqttSubscribePayload =
                        mqttSubscribePayloadRO.tryWrap(decodeBuffer, decodeProgress, decodeLimit);
                    if (mqttSubscribePayload == null)
                    {
                        onDecodeError(traceId, authorization, MALFORMED_PACKET);
                        decoder = decodeIgnoreAll;
                        break decode;
                    }
                    decodeProgress = mqttSubscribePayload.limit();

                    final String filter = mqttSubscribePayload.filter().asString();
                    if (filter == null)
                    {
                        onDecodeError(traceId, authorization, PROTOCOL_ERROR);
                        decoder = decodeIgnoreAll;
                        break;
                    }

                    if (!utf8Validator.isValidString(mqttSubscribePayload.filter()))
                    {
                        onDecodeError(traceId, authorization, MALFORMED_PACKET);
                        decoder = decodeIgnoreAll;
                        break;
                    }

                    final boolean validTopicFilter = validator.isTopicFilterValid(filter);
                    if (!validTopicFilter)
                    {
                        onDecodeError(traceId, authorization, topicReasonCode(filter));
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
                    subscription.filter = filter;
                    subscription.flags = retainAvailable(capabilities) ? SEND_RETAINED_FLAG : 0;
                    subscription.qos = Math.min(options, subscribeQosMax);
                    putPendingFilter(PENDING_SUBSCRIBE, filter, packetId);

                    newSubscriptions.add(subscription);
                }

                final MqttDataExFW.Builder sessionDataExBuilder =
                    mqttSessionDataExRW.wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                        .typeId(mqttTypeId)
                        .session(sessionBuilder -> sessionBuilder.kind(k -> k.set(MqttSessionDataKind.STATE)));

                newSubscriptions.forEach(sub -> sub.reasonCode = resolveSubscribeReasonCode(sub));
                if (session.unAckedSubscriptions == EMPTY_LIST)
                {
                    session.unAckedSubscriptions = new ArrayList<>();
                }
                session.unAckedSubscriptions.addAll(newSubscriptions);

                if (subscribeFailed(newSubscriptions))
                {
                    doSubscribeFailure(traceId, packetId, newSubscriptions);
                    break decode;
                }

                final MqttSessionStateFW sessionState = sessionState(replaceSubscriptions(newSubscriptions));
                final int payloadSize = sessionState.sizeof();

                if (!session.hasSessionWindow(payloadSize))
                {
                    break decode;
                }
                session.doSessionData(traceId, payloadSize, sessionDataExBuilder.build(), sessionState);
            }
            doSignalKeepAliveTimeout(traceId);
        }

        private void onDecodeSubscribeV5(
            long traceId,
            long authorization,
            MqttSubscribeV5FW subscribe)
        {
            final int packetId = subscribe.packetId();
            final OctetsFW decodePayload = subscribe.payload();

            final DirectBufferEx decodeBuffer = decodePayload.buffer();
            final int decodeOffset = decodePayload.offset();
            final int decodeLimit = decodePayload.limit();

            int subscriptionId = 0;
            boolean containsSubscriptionId = false;
            int propertiesReasonCode = SUCCESS;

            MqttPropertiesFW properties = subscribe.properties();
            final OctetsFW propertiesValue = properties.value();
            final DirectBufferEx propertiesBuffer = decodePayload.buffer();
            final int propertiesOffset = propertiesValue.offset();
            final int propertiesLimit = propertiesValue.limit();

            decodeProperties:
            for (int propertiesProgress = propertiesOffset; propertiesProgress < propertiesLimit; )
            {
                final MqttPropertyFW mqttProperty =
                    mqttPropertyRO.tryWrap(propertiesBuffer, propertiesProgress, propertiesLimit);
                if (mqttProperty == null)
                {
                    propertiesReasonCode = MALFORMED_PACKET;
                    break decodeProperties;
                }

                switch (mqttProperty.kind())
                {
                case KIND_SUBSCRIPTION_ID:
                    subscriptionId = mqttProperty.subscriptionId().value();
                    if (containsSubscriptionId || subscriptionId == 0)
                    {
                        propertiesReasonCode = PROTOCOL_ERROR;
                    }
                    containsSubscriptionId = true;
                    break;
                case KIND_USER_PROPERTY:
                    break;
                default:
                    propertiesReasonCode = MALFORMED_PACKET;
                    break decodeProperties;
                }

                propertiesProgress = mqttProperty.limit();
            }

            if (propertiesReasonCode != SUCCESS)
            {
                onDecodeError(traceId, authorization, propertiesReasonCode);
                decoder = decodeIgnoreAll;
            }
            else
            {
                final List<Subscription> newSubscriptions = new ArrayList<>();

                decode:
                {
                    for (int decodeProgress = decodeOffset; decodeProgress < decodeLimit; )
                    {
                        final MqttSubscribePayloadFW mqttSubscribePayload =
                            mqttSubscribePayloadRO.tryWrap(decodeBuffer, decodeProgress, decodeLimit);
                        if (mqttSubscribePayload == null)
                        {
                            onDecodeError(traceId, authorization, MALFORMED_PACKET);
                            decoder = decodeIgnoreAll;
                            break decode;
                        }
                        decodeProgress = mqttSubscribePayload.limit();

                        final String filter = mqttSubscribePayload.filter().asString();
                        if (filter == null)
                        {
                            onDecodeError(traceId, authorization, PROTOCOL_ERROR);
                            decoder = decodeIgnoreAll;
                            break;
                        }

                        if (!utf8Validator.isValidString(mqttSubscribePayload.filter()))
                        {
                            onDecodeError(traceId, authorization, MALFORMED_PACKET);
                            decoder = decodeIgnoreAll;
                            break;
                        }

                        final boolean validTopicFilter = validator.isTopicFilterValid(filter);
                        if (!validTopicFilter)
                        {
                            onDecodeError(traceId, authorization, topicReasonCode(filter));
                            decoder = decodeIgnoreAll;
                            break;
                        }
                        if (!wildcardAvailable(capabilities) && (filter.contains("+") || filter.contains("#")))
                        {
                            onDecodeError(traceId, authorization, WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED);
                            decoder = decodeIgnoreAll;
                            break;
                        }

                        final boolean sharedFilter = MqttValidator.isShared(filter);

                        if (sharedFilter && !sharedSubscriptionAvailable(capabilities))
                        {
                            onDecodeError(traceId, authorization, SHARED_SUBSCRIPTION_NOT_SUPPORTED);
                            decoder = decodeIgnoreAll;
                            break;
                        }

                        if (sharedFilter && !validator.isSharedTopicFilterValid(filter))
                        {
                            onDecodeError(traceId, authorization, PROTOCOL_ERROR);
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

                        if ((options & SUBSCRIPTION_RESERVED_MASK) != 0)
                        {
                            onDecodeError(traceId, authorization, MALFORMED_PACKET);
                            decoder = decodeIgnoreAll;
                            break;
                        }

                        if ((options & SUBSCRIPTION_QOS_MASK) > MqttQoS.EXACTLY_ONCE.value())
                        {
                            onDecodeError(traceId, authorization, PROTOCOL_ERROR);
                            decoder = decodeIgnoreAll;
                            break;
                        }

                        final int subscribeFlags =
                            calculateSubscribeFlags(traceId, authorization, filter, options & ~SUBSCRIPTION_QOS_MASK);
                        final int flags = sharedFilter ? subscribeFlags & ~SEND_RETAINED_FLAG : subscribeFlags;

                        final int qos = options & SUBSCRIPTION_QOS_MASK;

                        if ((sharedFilter || !noLocal) && isSetNoLocal(flags))
                        {
                            onDecodeError(traceId, authorization, PROTOCOL_ERROR);
                            decoder = decodeIgnoreAll;
                            break;
                        }

                        Subscription subscription = new Subscription();
                        subscription.id = subscriptionId;
                        subscription.filter = filter;
                        subscription.flags = retainAvailable(capabilities) ? flags : flags & ~SEND_RETAINED_FLAG;
                        subscription.qos = Math.min(qos, subscribeQosMax);
                        putPendingFilter(PENDING_SUBSCRIBE, filter, packetId);

                        newSubscriptions.add(subscription);
                    }

                    final MqttDataExFW.Builder sessionDataExBuilder =
                        mqttSessionDataExRW.wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                            .typeId(mqttTypeId)
                            .session(sessionBuilder -> sessionBuilder.kind(k -> k.set(MqttSessionDataKind.STATE)));

                    newSubscriptions.forEach(sub -> sub.reasonCode = resolveSubscribeReasonCode(sub));
                    if (session.unAckedSubscriptions == EMPTY_LIST)
                    {
                        session.unAckedSubscriptions = new ArrayList<>();
                    }
                    session.unAckedSubscriptions.addAll(newSubscriptions);

                    if (subscribeFailed(newSubscriptions))
                    {
                        doSubscribeFailure(traceId, packetId, newSubscriptions);
                        break decode;
                    }

                    final MqttSessionStateFW sessionState = sessionState(replaceSubscriptions(newSubscriptions));
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

        private List<Subscription> replaceSubscriptions(
            List<Subscription> newSubscriptions)
        {
            final Map<String, Subscription> subscriptionsByFilter = new LinkedHashMap<>();
            for (int subscriptionIndex = 0; subscriptionIndex < session.subscriptions.size(); subscriptionIndex++)
            {
                final Subscription subscription = session.subscriptions.get(subscriptionIndex);
                subscriptionsByFilter.put(subscription.filter, subscription);
            }
            for (int subscriptionIndex = 0; subscriptionIndex < newSubscriptions.size(); subscriptionIndex++)
            {
                final Subscription subscription = newSubscriptions.get(subscriptionIndex);
                if (subscription.reasonCode == SUCCESS)
                {
                    subscriptionsByFilter.put(subscription.filter, subscription);
                }
            }

            return new ArrayList<>(subscriptionsByFilter.values());
        }

        private void openSubscribeStreams(
            int packetId,
            long traceId,
            long authorization,
            List<Subscription> subscriptions,
            boolean implicitSubscribe)
        {
            final Long2ObjectHashMap<List<Subscription>> subscriptionsByRouteId = new Long2ObjectHashMap<>();
            final List<MqttSubscribeStream> awaitingStreams = new ArrayList<>();

            for (int subscriptionIndex = 0; subscriptionIndex < subscriptions.size(); subscriptionIndex++)
            {
                final Subscription subscription = subscriptions.get(subscriptionIndex);
                if (subscription.reasonCode == SUCCESS)
                {
                    final MqttBindingConfig binding = bindings.get(routedId);
                    final MqttRouteConfig resolved = binding != null
                        ? binding.resolveSubscribe(sessionId, subscription.shareName(), subscription.pattern())
                        : null;

                    if (resolved != null)
                    {
                        final List<Subscription> resolvedSubscriptions =
                            subscriptionsByRouteId.computeIfAbsent(resolved.id, s -> new ArrayList<>());
                        resolvedSubscriptions.add(subscription.withCompositeId(resolved.compositeId()));
                    }
                    else
                    {
                        subscription.reasonCode = resolveSubscribeReasonCode(subscription);
                    }
                }
            }

            subscriptionsByRouteId.forEach((key, value) ->
                openRouteSubscribeStreams(traceId, key, value, awaitingStreams));

            if (!implicitSubscribe)
            {
                final byte[] reasonCodes = subackReasonCodes(subscriptions);
                if (awaitingStreams.isEmpty())
                {
                    doEncodeSuback(traceId, packetId, reasonCodes);
                }
                else
                {
                    if (pendingSubacks == EMPTY_LIST)
                    {
                        pendingSubacks = new ArrayList<>();
                    }
                    pendingSubacks.add(new PendingSuback(packetId, subscriptions, reasonCodes, awaitingStreams));
                }
            }
        }

        private void openRouteSubscribeStreams(
            long traceId,
            long routeId,
            List<Subscription> routeSubscriptions,
            List<MqttSubscribeStream> awaitingStreams)
        {
            if (subscribes == EMPTY_LONG_MAP)
            {
                subscribes = new Long2ObjectHashMap<>();
            }

            final Int2ObjectHashMap<MqttSubscribeStream> routeSubscribes =
                subscribes.computeIfAbsent(routeId, s -> new Int2ObjectHashMap<>());

            final Map<MqttSubscribeStream, List<Subscription>> replaced = new HashMap<>();
            for (MqttSubscribeStream stream : routeSubscribes.values())
            {
                final List<Subscription> removed = stream.removeSubscriptions(routeSubscriptions);
                if (!removed.isEmpty())
                {
                    replaced.put(stream, removed);
                }
            }

            for (int maxQos = 0; maxQos <= MqttQoS.EXACTLY_ONCE.value(); maxQos++)
            {
                final List<Subscription> subscriptionList = new ArrayList<>();
                for (int subscriptionIndex = 0; subscriptionIndex < routeSubscriptions.size(); subscriptionIndex++)
                {
                    final Subscription subscription = routeSubscriptions.get(subscriptionIndex);
                    if (!subscription.shared() && subscription.qos == maxQos)
                    {
                        subscriptionList.add(subscription);
                    }
                }

                if (!subscriptionList.isEmpty())
                {
                    for (int qos = 0; qos <= maxQos; qos++)
                    {
                        MqttSubscribeStream stream = routeSubscribes.get(qos);
                        if (stream == null)
                        {
                            final long compositeId = !subscriptionList.isEmpty()
                                ? subscriptionList.get(0).compositeId
                                : WithConfig.NO_COMPOSITE_ID;
                            stream = new MqttSubscribeStream(routedId, routeId, compositeId, qos, null);
                            routeSubscribes.put(qos, stream);
                        }
                        subscriptionList.removeIf(s -> s.reasonCode > GRANTED_QOS_2);
                        stream.doSubscribeBeginOrFlush(traceId, affinity, subscriptionList);
                        awaitReply(awaitingStreams, stream);
                        replaced.remove(stream);
                    }
                }
            }

            for (int subscriptionIndex = 0; subscriptionIndex < routeSubscriptions.size(); subscriptionIndex++)
            {
                final Subscription subscription = routeSubscriptions.get(subscriptionIndex);
                if (subscription.shared() && subscription.reasonCode <= GRANTED_QOS_2)
                {
                    awaitReply(awaitingStreams, openSharedSubscribeStream(traceId, routeId, subscription));
                }
            }

            for (Map.Entry<MqttSubscribeStream, List<Subscription>> entry : replaced.entrySet())
            {
                entry.getKey().doSubscribeFlushOrEnd(traceId, entry.getValue());
            }
        }

        private int resolveSubscribeReasonCode(
            Subscription subscription)
        {
            final MqttBindingConfig binding = bindings.get(routedId);

            return version == 4 && subscription.shared() || binding == null
                ? UNSPECIFIED_ERROR
                : binding.resolveSubscribeReasonCode(sessionId, subscription.shareName(), subscription.pattern());
        }

        private boolean subscribeFailed(
            List<Subscription> subscriptions)
        {
            boolean granted = false;
            for (int index = 0; !granted && index < subscriptions.size(); index++)
            {
                granted = subscriptions.get(index).reasonCode == SUCCESS;
            }

            return !subscriptions.isEmpty() && !granted;
        }

        private void doSubscribeFailure(
            long traceId,
            int packetId,
            List<Subscription> subscriptions)
        {
            if (decoder != decodeIgnoreAll)
            {
                doEncodeSuback(traceId, packetId, subscriptions);
            }

            session.unAckedSubscriptions.removeAll(subscriptions);
            subscriptions.forEach(s -> removePendingFilter(PENDING_SUBSCRIBE, s.filter));
        }

        private void doEncodeSuback(
            long traceId,
            int packetId,
            List<Subscription> subscriptions)
        {
            doEncodeSuback(traceId, packetId, subackReasonCodes(subscriptions));
        }

        private void awaitReply(
            List<MqttSubscribeStream> awaitingStreams,
            MqttSubscribeStream stream)
        {
            if (!StreamState.replyOpening(stream.state) && !awaitingStreams.contains(stream))
            {
                awaitingStreams.add(stream);
            }
        }

        private byte[] subackReasonCodes(
            List<Subscription> subscriptions)
        {
            final byte[] reasonCodes = new byte[subscriptions.size()];
            for (int i = 0; i < reasonCodes.length; i++)
            {
                final Subscription subscription = subscriptions.get(i);
                final byte reasonCode = subackReasonCode(subscription.reasonCode);
                reasonCodes[i] = reasonCode == 0 ? (byte) subscription.qos : reasonCode;
            }
            return reasonCodes;
        }

        private byte subackReasonCode(
            int reasonCode)
        {
            return version == 4 && (reasonCode & 0xff) > GRANTED_QOS_2 ? SUBACK_FAILURE_CODE_V4 : (byte) reasonCode;
        }

        private void doEncodeSuback(
            long traceId,
            int packetId,
            byte[] reasonCodes)
        {
            switch (version)
            {
            case 4:
                doEncodeSubackV4(traceId, sessionId, packetId, reasonCodes);
                break;
            case 5:
                doEncodeSubackV5(traceId, sessionId, packetId, reasonCodes);
                break;
            }
        }

        private void onSubscribeStreamBegin(
            long traceId,
            long authorization,
            MqttSubscribeStream stream)
        {
            final Iterator<PendingSuback> iterator = pendingSubacks.iterator();
            while (iterator.hasNext())
            {
                final PendingSuback suback = iterator.next();
                if (suback.streams.remove(stream) && suback.streams.isEmpty())
                {
                    iterator.remove();
                    doEncodeSuback(traceId, suback.packetId, suback.reasonCodes);
                }
            }

            releaseDeferredUnsubscribes(traceId, authorization);
        }

        private boolean onSubscribeStreamRejected(
            long traceId,
            long authorization,
            MqttSubscribeStream stream,
            int reasonCode)
        {
            final boolean rejected = rejectable(stream);

            if (rejected)
            {
                final List<String> failedFilters = new ArrayList<>();
                final byte failureCode = subackReasonCode(reasonCode);
                final Iterator<PendingSuback> iterator = pendingSubacks.iterator();
                while (iterator.hasNext())
                {
                    final PendingSuback suback = iterator.next();
                    if (suback.streams.remove(stream))
                    {
                        for (int i = 0; i < suback.reasonCodes.length; i++)
                        {
                            final Subscription subscription = suback.subscriptions.get(i);
                            if ((suback.reasonCodes[i] & 0xff) <= GRANTED_QOS_2 && stream.subscriptions.contains(subscription))
                            {
                                suback.reasonCodes[i] = failureCode;
                                failedFilters.add(subscription.filter);
                            }
                        }

                        if (suback.streams.isEmpty())
                        {
                            iterator.remove();
                            doEncodeSuback(traceId, suback.packetId, suback.reasonCodes);
                        }
                    }
                }

                if (!failedFilters.isEmpty())
                {
                    doSendSessionState(traceId, failedFilters);
                }

                releaseDeferredUnsubscribes(traceId, authorization);
            }

            return rejected;
        }

        private boolean rejectable(
            MqttSubscribeStream stream)
        {
            final List<PendingSuback> awaiting = pendingSubacks.stream()
                .filter(suback -> suback.streams.contains(stream))
                .collect(Collectors.toList());

            return !awaiting.isEmpty() && stream.subscriptions.stream()
                .allMatch(subscription -> awaiting.stream().anyMatch(suback -> suback.subscriptions.contains(subscription)));
        }

        private MqttSubscribeStream openSharedSubscribeStream(
            long traceId,
            long routeId,
            Subscription subscription)
        {
            if (sharedSubscribes == EMPTY_LONG_MAP)
            {
                sharedSubscribes = new Long2ObjectHashMap<>();
            }

            final Map<String, MqttSubscribeStream> routeShared =
                sharedSubscribes.computeIfAbsent(routeId, s -> new Object2ObjectHashMap<>());

            MqttSubscribeStream stream = routeShared.get(subscription.filter);
            if (stream == null || StreamState.initialClosed(stream.state))
            {
                stream = new MqttSubscribeStream(routedId, routeId, subscription.compositeId,
                    subscription.qos, subscription.filter);
                routeShared.put(subscription.filter, stream);
            }

            stream.doSubscribeBeginOrFlush(traceId, affinity, List.of(subscription));

            return stream;
        }

        private Collection<MqttSubscribeStream> subscribeStreams(
            long routeId,
            String topicFilter)
        {
            final Collection<MqttSubscribeStream> streams;
            if (MqttValidator.isShared(topicFilter))
            {
                final Map<String, MqttSubscribeStream> routeShared = sharedSubscribes.get(routeId);
                final MqttSubscribeStream stream = routeShared != null ? routeShared.get(topicFilter) : null;
                streams = stream != null ? List.of(stream) : List.of();
            }
            else
            {
                final Int2ObjectHashMap<MqttSubscribeStream> routeSubscribes = subscribes.get(routeId);
                streams = routeSubscribes != null ? routeSubscribes.values() : List.of();
            }
            return streams;
        }

        private List<MqttSubscribeStream> sharedSubscribeStreams()
        {
            final List<MqttSubscribeStream> streams = new ArrayList<>();
            sharedSubscribes.values().forEach(routeShared -> streams.addAll(routeShared.values()));
            return streams;
        }

        private void onDecodeUnsubscribe(
            long traceId,
            long authorization,
            int packetId,
            OctetsFW decodePayload)
        {
            final DirectBufferEx decodeBuffer = decodePayload.buffer();
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
                if (!utf8Validator.isValidString(mqttUnsubscribePayload.filter()))
                {
                    decodeReasonCode = MALFORMED_PACKET;
                    break;
                }
                if (!validator.isTopicFilterValid(topicFilter))
                {
                    decodeReasonCode = topicReasonCode(topicFilter);
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
                if (anySubscribePending(topicFilters))
                {
                    if (session.deferredUnsubscribes == EMPTY_INT_MAP)
                    {
                        session.deferredUnsubscribes = new Int2ObjectHashMap<>();
                    }
                    session.deferredUnsubscribes.put(packetId, topicFilters);
                    putPendingFilters(packetId, topicFilters);
                    return;
                }
                if (subscribed(topicFilters))
                {
                    for (int index = 0; index < topicFilters.size(); index++)
                    {
                        putPendingFilter(PENDING_UNSUBSCRIBE, topicFilters.get(index), packetId);
                    }
                    putPendingFilters(packetId, topicFilters);

                    if (anyDelivering(topicFilters))
                    {
                        for (int index = 0; index < topicFilters.size(); index++)
                        {
                            stopDelivering(traceId, topicFilters.get(index));
                        }
                        if (session.deferredUnsubscribes == EMPTY_INT_MAP)
                        {
                            session.deferredUnsubscribes = new Int2ObjectHashMap<>();
                        }
                        session.deferredUnsubscribes.put(packetId, topicFilters);
                    }
                    else
                    {
                        doSendSessionState(traceId, topicFilters);
                    }
                }
                else
                {
                    sendUnsuback(packetId, traceId, authorization, topicFilters, null, false);
                }
                doSignalKeepAliveTimeout(traceId);
            }
        }

        private boolean anySubscribePending(
            List<String> topicFilters)
        {
            boolean pending = false;

            for (int index = 0; !pending && index < topicFilters.size(); index++)
            {
                pending = subscribePending(topicFilters.get(index));
            }

            return pending;
        }

        private boolean anyDelivering(
            List<String> topicFilters)
        {
            boolean delivering = false;

            for (int index = 0; !delivering && index < topicFilters.size(); index++)
            {
                delivering = delivering(topicFilters.get(index));
            }

            return delivering;
        }

        private boolean subscribePending(
            String topicFilter)
        {
            boolean pending = false;

            final List<Subscription> unAcked = session.unAckedSubscriptions;
            for (int index = 0; !pending && index < unAcked.size(); index++)
            {
                final Subscription unAckedSubscription = unAcked.get(index);
                pending = unAckedSubscription.filter.equals(topicFilter) &&
                    pendingPacketId(PENDING_SUBSCRIBE, unAckedSubscription.filter) != NO_PACKET_ID;
            }

            for (int index = 0; !pending && index < pendingSubacks.size(); index++)
            {
                pending = findByFilter(pendingSubacks.get(index).subscriptions, topicFilter) != null;
            }

            return pending;
        }

        private MqttSubscribeStream sharedSubscribeStream(
            String topicFilter)
        {
            final MqttBindingConfig binding = bindings.get(routedId);
            final String shareName = MqttValidator.shareName(topicFilter);
            final String pattern = shareName != null ? MqttValidator.sharePattern(topicFilter) : topicFilter;
            final MqttRouteConfig resolved =
                shareName != null && binding != null ? binding.resolveSubscribe(sessionId, shareName, pattern) : null;
            final Collection<MqttSubscribeStream> streams =
                resolved != null ? subscribeStreams(resolved.id, topicFilter) : List.of();

            return streams.isEmpty() ? null : streams.iterator().next();
        }

        private boolean delivering(
            String topicFilter)
        {
            final MqttSubscribeStream stream = sharedSubscribeStream(topicFilter);

            return stream != null && stream.delivering();
        }

        private void stopDelivering(
            long traceId,
            String topicFilter)
        {
            final MqttSubscribeStream stream = sharedSubscribeStream(topicFilter);

            if (stream != null)
            {
                stream.doSubscribeDrain(traceId);
            }
        }

        private boolean draining(
            String topicFilter)
        {
            final MqttSubscribeStream stream = sharedSubscribeStream(topicFilter);

            return stream != null && stream.draining();
        }

        private boolean subscribed(
            List<String> topicFilters)
        {
            boolean subscribed = false;

            for (int index = 0; !subscribed && index < topicFilters.size(); index++)
            {
                subscribed = findByFilter(session.subscriptions, topicFilters.get(index)) != null;
            }

            return subscribed;
        }

        private void releaseDeferredUnsubscribes(
            long traceId,
            long authorization)
        {
            if (session.deferredUnsubscribes.isEmpty())
            {
                return;
            }

            final List<String> unsubscribedFilters = new ArrayList<>();

            final Iterator<Map.Entry<Integer, List<String>>> iterator = session.deferredUnsubscribes.entrySet().iterator();
            while (iterator.hasNext())
            {
                final Map.Entry<Integer, List<String>> entry = iterator.next();
                final int unsubscribePacketId = entry.getKey();
                final List<String> topicFilters = entry.getValue();

                if (topicFilters.stream().noneMatch(this::subscribePending) &&
                    topicFilters.stream().noneMatch(this::draining))
                {
                    iterator.remove();

                    if (subscribed(topicFilters))
                    {
                        topicFilters.forEach(filter -> putPendingFilter(PENDING_UNSUBSCRIBE, filter, unsubscribePacketId));
                        unsubscribedFilters.addAll(topicFilters);
                    }
                    else
                    {
                        sendUnsuback(unsubscribePacketId, traceId, authorization, topicFilters, null, false);
                    }
                }
            }

            if (!unsubscribedFilters.isEmpty())
            {
                doSendSessionState(traceId, unsubscribedFilters);
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

            final List<Subscription> newState = new ArrayList<>();
            for (int subscriptionIndex = 0; subscriptionIndex < session.subscriptions().size(); subscriptionIndex++)
            {
                final Subscription subscription = session.subscriptions().get(subscriptionIndex);
                if (!topicFilters.contains(subscription.filter))
                {
                    newState.add(subscription);
                }
            }

            final MqttSessionStateFW sessionState = sessionState(newState);
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
            final MutableDirectBufferEx encodeBuffer = payloadBuffer;
            final int encodeOffset = 0;
            final int encodeLimit = payloadBuffer.capacity();

            int encodeProgress = encodeOffset;

            final Map<MqttSubscribeStream, List<Subscription>> filtersByStream = new HashMap<>();

            for (int topicFilterIndex = 0; topicFilterIndex < topicFilters.size(); topicFilterIndex++)
            {
                final String topicFilter = topicFilters.get(topicFilterIndex);
                final MqttBindingConfig binding = bindings.get(routedId);
                final String shareName = MqttValidator.shareName(topicFilter);
                final String pattern = shareName != null ? MqttValidator.sharePattern(topicFilter) : topicFilter;
                final MqttRouteConfig resolved =
                    binding != null ? binding.resolveSubscribe(sessionId, shareName, pattern) : null;
                final Collection<MqttSubscribeStream> streams =
                    resolved != null ? subscribeStreams(resolved.id, topicFilter) : List.of();

                final boolean existed = !streams.isEmpty() ||
                    MqttValidator.isShared(topicFilter) && subscribed(List.of(topicFilter));
                int encodeReasonCode = existed ? SUCCESS : NO_SUBSCRIPTION_EXISTED;
                for (MqttSubscribeStream stream : streams)
                {
                    final Subscription subscription = stream.subscriptionByFilter(topicFilter, newState);

                    if (subscription != null)
                    {
                        filtersByStream.computeIfAbsent(stream, s -> new ArrayList<>()).add(subscription);
                    }
                    encodeReasonCode = subscription != null
                        ? unsubackReasonCode(subscription.reasonCode)
                        : NO_SUBSCRIPTION_EXISTED;
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
                MqttSubscribeStream delivering = null;
                for (MqttSubscribeStream stream : filtersByStream.keySet())
                {
                    delivering = delivering == null && stream.deliveringMessage() ? stream : delivering;
                }

                if (delivering != null)
                {
                    final byte[] reasonCodes = new byte[encodeProgress - encodeOffset];
                    encodeBuffer.getBytes(encodeOffset, reasonCodes);
                    delivering.pendingPackets.add(() ->
                        doEncodeUnsuback(traceId, authorization, packetId, new UnsafeBufferEx(reasonCodes), reasonCodes.length));
                }
                else
                {
                    doEncodeUnsuback(traceId, authorization, packetId, encodeBuffer, encodeProgress - encodeOffset);
                }
            }
        }

        private void doEncodeUnsuback(
            long traceId,
            long authorization,
            int packetId,
            DirectBufferEx reasonCodes,
            int length)
        {
            switch (version)
            {
            case 4:
                doEncodeUnsubackV4(traceId, authorization, packetId);
                break;
            default:
                doEncodeUnsubackV5(traceId, authorization, packetId, octetsRO.wrap(reasonCodes, 0, length));
                break;
            }
        }

        private int unsubackReasonCode(
            int reasonCode)
        {
            return reasonCode <= GRANTED_QOS_2 ? SUCCESS : reasonCode;
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
            state = StreamState.closingInitial(state);
            closeStreams(traceId, authorization);
            doNetworkEnd(traceId, authorization);
        }

        private void onDecodeDisconnectV5(
            long traceId,
            long authorization,
            int disconnectReasonCode,
            MqttPropertiesFW properties)
        {
            byte reasonCode = properties != null ? decodeDisconnectProperties(properties) : SUCCESS;

            if (reasonCode == SUCCESS && !isDisconnectReasonCode(disconnectReasonCode))
            {
                reasonCode = PROTOCOL_ERROR;
            }

            if (reasonCode != SUCCESS)
            {
                onDecodeError(traceId, authorization, reasonCode);
                decoder = decodeIgnoreAll;
            }
            else
            {
                final long disconnectExpiry = properties != null
                    ? disconnectSessionExpiry(properties)
                    : NO_DISCONNECT_SESSION_EXPIRY;
                final Flyweight endEx = disconnectExpiry == NO_DISCONNECT_SESSION_EXPIRY
                    ? EMPTY_OCTETS
                    : mqttEndExRW.wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                        .typeId(mqttTypeId)
                        .expiry((int) disconnectExpiry)
                        .build();

                if (disconnectReasonCode != NORMAL_DISCONNECT)
                {
                    session.doSessionAbort(traceId, endEx);
                }
                else
                {
                    session.doSessionEnd(traceId, endEx);
                }
            }

            state = StreamState.closingInitial(state);
            closeStreams(traceId, authorization);
            doNetworkEnd(traceId, authorization);
        }

        private long disconnectSessionExpiry(
            MqttPropertiesFW properties)
        {
            long sessionExpiry = NO_DISCONNECT_SESSION_EXPIRY;

            final OctetsFW propertiesValue = properties.value();
            final DirectBufferEx decodeBuffer = propertiesValue.buffer();
            final int decodeLimit = propertiesValue.limit();

            for (int decodeProgress = propertiesValue.offset(); decodeProgress < decodeLimit; )
            {
                final MqttPropertyFW mqttProperty = mqttPropertyRO.tryWrap(decodeBuffer, decodeProgress, decodeLimit);
                if (mqttProperty == null)
                {
                    break;
                }

                if (mqttProperty.kind() == KIND_SESSION_EXPIRY)
                {
                    sessionExpiry = mqttProperty.sessionExpiry();
                }

                decodeProgress = mqttProperty.limit();
            }

            return sessionExpiry;
        }

        private byte decodeDisconnectProperties(
            MqttPropertiesFW properties)
        {
            byte reasonCode = SUCCESS;
            int decodedPropertyMask = 0;

            final OctetsFW propertiesValue = properties.value();
            final DirectBufferEx decodeBuffer = propertiesValue.buffer();
            final int decodeOffset = propertiesValue.offset();
            final int decodeLimit = propertiesValue.limit();

            decode:
            for (int decodeProgress = decodeOffset; decodeProgress < decodeLimit; )
            {
                final MqttPropertyFW mqttProperty = mqttPropertyRO.tryWrap(decodeBuffer, decodeProgress, decodeLimit);
                if (mqttProperty == null)
                {
                    reasonCode = MALFORMED_PACKET;
                    break decode;
                }

                switch (mqttProperty.kind())
                {
                case KIND_SESSION_EXPIRY:
                    if (isSetSessionExpiryInterval(decodedPropertyMask))
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    decodedPropertyMask |= CONNECT_SESSION_EXPIRY_INTERVAL_MASK;
                    if (mqttProperty.sessionExpiry() > 0 && this.connectSessionExpiry == 0)
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    break;
                case KIND_REASON_STRING:
                    if ((decodedPropertyMask & DISCONNECT_REASON_STRING_MASK) != 0)
                    {
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    }
                    decodedPropertyMask |= DISCONNECT_REASON_STRING_MASK;
                    if (!utf8Validator.isValidString(mqttProperty.reasonString()))
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    break;
                case KIND_USER_PROPERTY:
                    final MqttUserPropertyFW userProperty = mqttProperty.userProperty();
                    if (!utf8Validator.isValidString(userProperty.key()) ||
                        !utf8Validator.isValidString(userProperty.value()))
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    break;
                case KIND_SERVER_REFERENCE:
                    reasonCode = PROTOCOL_ERROR;
                    break decode;
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
            if (!willPayloadPending())
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
            }
            else
            {
                // the will payload forward is still deferred, awaiting session window; mark
                // closing and let it complete via the session window retry in decodeNetwork
                state = StreamState.closingInitial(state);
            }

            if (connected)
            {
                if (version != MQTT_PROTOCOL_VERSION_4)
                {
                    doEncodeDisconnect(traceId, authorization, reasonCode, null, null, null);
                }
            }
            else
            {
                doEncodeConnack(traceId, authorization, reasonCode, false, false, null, null, version);
            }

            doNetworkEnd(traceId, authorization);
        }

        private void doNetworkBegin(
            long traceId,
            long authorization)
        {
            state = StreamState.openingReply(state);

            doBegin(network, originId, routedId, replyId, encodeSeq, encodeAck, state, encodeMax,
                traceId, authorization, affinity, EMPTY_OCTETS);

            assert encodeCredit == null;
            this.encodeCredit = context.supplyCredit(replyId, encodeBudgetId);
        }

        private void doNetworkControl(
            long traceId,
            long authorization,
            Flyweight packet)
        {
            final MqttSubscribeStream delivering = deliveringSubscribe();

            if (delivering != null)
            {
                final byte[] bytes = new byte[packet.sizeof()];
                packet.buffer().getBytes(packet.offset(), bytes);
                delivering.pendingPackets.add(() ->
                    doNetworkData(traceId, authorization, 0L, new UnsafeBufferEx(bytes), 0, bytes.length));
            }
            else
            {
                doNetworkData(traceId, authorization, 0L, packet);
            }
        }

        private MqttSubscribeStream deliveringSubscribe()
        {
            MqttSubscribeStream delivering = null;

            for (Int2ObjectHashMap<MqttSubscribeStream> routeSubscribes : subscribes.values())
            {
                for (MqttSubscribeStream stream : routeSubscribes.values())
                {
                    delivering = delivering == null && stream.deliveringMessage() ? stream : delivering;
                }
            }

            for (Map<String, MqttSubscribeStream> routeShared : sharedSubscribes.values())
            {
                for (MqttSubscribeStream stream : routeShared.values())
                {
                    delivering = delivering == null && stream.deliveringMessage() ? stream : delivering;
                }
            }

            return delivering;
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
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx encodeBuffer = bufferPool.buffer(encodeSlot);
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
            if (!StreamState.replyClosed(state))
            {
                if (encodeSlot != NO_SLOT)
                {
                    state = StreamState.closingReply(state);
                }
                else
                {
                    state = StreamState.closeReply(state);

                    doEnd(network, originId, routedId, replyId, encodeSeq, encodeAck, state, encodeMax,
                        traceId, authorization, EMPTY_OCTETS);
                }
            }

            deauthorizeGuardSession();
        }

        private void doNetworkAbort(
            long traceId,
            long authorization)
        {
            if (!StreamState.replyClosed(state))
            {
                state = StreamState.closeReply(state);

                cleanupEncodeSlot();

                doAbort(network, originId, routedId, replyId, encodeSeq, encodeAck,
                    StreamState.closeReply(StreamState.abortingReply(state)), encodeMax,
                    traceId, authorization, EMPTY_OCTETS);
            }

            deauthorizeGuardSession();
        }

        private void doNetworkReset(
            long traceId,
            long authorization)
        {
            if (!StreamState.initialClosed(state))
            {
                state = StreamState.closeInitial(state);

                cleanupDecodeSlot();

                doReset(network, originId, routedId, initialId, decodeSeq, decodeAck,
                    StreamState.closeInitial(StreamState.abortingInitial(state)), decodeMax,
                    traceId, authorization, EMPTY_OCTETS);
            }

            deauthorizeGuardSession();
        }

        private void deauthorizeGuardSession()
        {
            if (guard != null && (guardSessionId & MASK_AUTHORIZED) != 0)
            {
                guard.deauthorize(guardSessionId);
                guardSessionId = NOT_AUTHORIZED;
            }

            if (guardSessionExpiringId != NO_CANCEL_ID)
            {
                signaler.cancel(guardSessionExpiringId);
                guardSessionExpiringId = NO_CANCEL_ID;
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

            if (newInitialAck > decodeAck || minInitialMax > decodeMax || !StreamState.initialOpened(state))
            {
                decodeAck = newInitialAck;
                assert decodeAck <= decodeSeq;

                decodeMax = minInitialMax;

                state = StreamState.openInitial(state);

                doWindow(network, originId, routedId, initialId, decodeSeq, decodeAck, state, decodeMax,
                    traceId, authorization, budgetId, 0);
            }
        }

        private Subscription deliverableSubscription(
            List<Subscription> subscriptions,
            int filterId,
            String16FW topicName)
        {
            Subscription deliverable = null;

            for (int index = 0; deliverable == null && index < subscriptions.size(); index++)
            {
                final Subscription subscription = subscriptions.get(index);

                if (subscription.filterId == filterId &&
                    !(systemTopic(topicName) && MqttValidator.wildcardFirst(subscription.filter)))
                {
                    deliverable = subscription;
                }
            }

            return deliverable;
        }

        private boolean subscriptionIdEncoded(
            List<Subscription> subscriptions,
            DirectBufferEx filterIdItems,
            int filterIdOffset,
            int subscriptionId,
            String16FW topicName)
        {
            boolean encoded = false;

            for (int offset = 0; !encoded && offset < filterIdOffset; offset = filterIdEarlierRO.limit())
            {
                filterIdEarlierRO.wrap(filterIdItems, offset, filterIdOffset);
                final Subscription earlier = deliverableSubscription(subscriptions, filterIdEarlierRO.value(), topicName);
                encoded = earlier != null && earlier.id == subscriptionId;
            }

            return encoded;
        }

        private int encodePublishV4(
            long traceId,
            long authorization,
            List<Subscription> subscriptions,
            OctetsFW payload,
            MqttDataExFW subscribeDataEx,
            int qos)
        {
            int frameLength;

            final int payloadSize = payload.sizeof();
            final int deferred = subscribeDataEx.subscribe().deferred();

            final String16FW topicName = subscribeDataEx.subscribe().topic();

            final int topicNameLength = Math.max(topicName.length(), 0);

            final int publishApplicationFlags = subscribeDataEx.subscribe().flags();

            final int publishNetworkTypeAndFlags = PUBLISH_TYPE << 4 |
                calculatePublishNetworkFlags(PUBLISH_TYPE << 4 | publishApplicationFlags, qos);

            if (qos == 0)
            {
                final MqttPublishV4FW publish = mqttPublishV4RW
                    .wrap(writeBuffer,
                        DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                        .typeAndFlags(publishNetworkTypeAndFlags)
                        .remainingLength(2 + topicNameLength + payloadSize + deferred)
                        .topicName(topicName)
                        .build();

                int limit = DataFW.FIELD_OFFSET_PAYLOAD + publish.sizeof();
                writeBuffer.putBytes(limit, payload.buffer(), payload.offset(), payload.sizeof());
                limit += payload.sizeof();
                frameLength = limit - DataFW.FIELD_OFFSET_PAYLOAD;
            }
            else
            {
                final int packetId = subscribeDataEx.subscribe().packetId();
                final MqttPublishQosV4FW publish = mqttPublishQosV4RW
                    .wrap(writeBuffer,
                        DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                        .typeAndFlags(publishNetworkTypeAndFlags)
                        .remainingLength(4 + topicNameLength + payloadSize + deferred)
                        .topicName(topicName)
                        .packetId(packetId)
                        .build();

                int limit = DataFW.FIELD_OFFSET_PAYLOAD + publish.sizeof();
                writeBuffer.putBytes(limit, payload.buffer(), payload.offset(), payload.sizeof());
                limit += payload.sizeof();
                frameLength = limit - DataFW.FIELD_OFFSET_PAYLOAD;
            }

            return frameLength;
        }

        private int encodePublishV5(
            long traceId,
            long authorization,
            List<Subscription> subscriptions,
            OctetsFW payload,
            MqttDataExFW subscribeDataEx,
            int qos)
        {
            int frameLength;

            final int payloadSize = payload.sizeof();
            final int deferred = subscribeDataEx.subscribe().deferred();
            final int expiryInterval = subscribeDataEx.subscribe().expiryInterval();
            final String16FW contentType = subscribeDataEx.subscribe().contentType();
            final String16FW responseTopic = subscribeDataEx.subscribe().responseTopic();
            final MqttPayloadFormatFW format = subscribeDataEx.subscribe().format();
            final MqttBinaryFW correlation = subscribeDataEx.subscribe().correlation();
            final Array32FW<Varint32FW> filterIds = subscribeDataEx.subscribe().subscriptionIds();
            final Array32FW<io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttUserPropertyFW> properties =
                subscribeDataEx.subscribe().properties();

            final String16FW topicName = subscribeDataEx.subscribe().topic();

            final int topicNameLength = Math.max(topicName.length(), 0);

            int propertiesSize = 0;

            final DirectBufferEx filterIdItems = filterIds.items();
            final int filterIdLimit = filterIdItems.capacity();
            for (int filterIdOffset = 0; filterIdOffset < filterIdLimit; filterIdOffset = filterIdRO.limit())
            {
                filterIdRO.wrap(filterIdItems, filterIdOffset, filterIdLimit);
                final Subscription subscription = deliverableSubscription(subscriptions, filterIdRO.value(), topicName);

                if (subscription != null && subscription.id > 0 &&
                    !subscriptionIdEncoded(subscriptions, filterIdItems, filterIdOffset, subscription.id, topicName))
                {
                    final int subscriptionId = subscription.id;
                    mqttPropertyRW.wrap(propertyBuffer,
                        propertiesSize, propertyBuffer.capacity())
                        .subscriptionId(v -> v.set(subscriptionId));
                    propertiesSize = mqttPropertyRW.limit();
                }
            }

            final int publishApplicationFlags = subscribeDataEx.subscribe().flags();

            final int publishNetworkTypeAndFlags = PUBLISH_TYPE << 4 |
                calculatePublishNetworkFlags(PUBLISH_TYPE << 4 | publishApplicationFlags, qos);

            if (expiryInterval != -1)
            {
                mqttPropertyRW.wrap(propertyBuffer,
                    propertiesSize, propertyBuffer.capacity())
                    .expiryInterval(expiryInterval)
                    .build();
                propertiesSize = mqttPropertyRW.limit();
            }

            if (contentType.value() != null)
            {
                mqttPropertyRW.wrap(propertyBuffer,
                    propertiesSize, propertyBuffer.capacity())
                    .contentType(contentType.asString())
                    .build();
                propertiesSize = mqttPropertyRW.limit();
            }

            if (!format.get().equals(MqttPayloadFormat.NONE))
            {
                mqttPropertyRW.wrap(propertyBuffer,
                    propertiesSize, propertyBuffer.capacity())
                    .payloadFormat((byte) subscribeDataEx.subscribe().format().get().ordinal())
                    .build();
                propertiesSize = mqttPropertyRW.limit();
            }

            if (responseTopic.value() != null)
            {
                mqttPropertyRW.wrap(propertyBuffer,
                    propertiesSize, propertyBuffer.capacity())
                    .responseTopic(responseTopic.asString())
                    .build();
                propertiesSize = mqttPropertyRW.limit();
            }

            if (correlation.length() != -1)
            {
                mqttPropertyRW.wrap(propertyBuffer,
                    propertiesSize, propertyBuffer.capacity())
                    .correlationData(a -> a.bytes(correlation.bytes()))
                    .build();
                propertiesSize = mqttPropertyRW.limit();
            }

            final DirectBufferEx userPropertyItems = properties.items();
            final int userPropertyLimit = userPropertyItems.capacity();
            for (int userPropertyOffset = 0; userPropertyOffset < userPropertyLimit;
                userPropertyOffset = subscribeUserPropertyRO.limit())
            {
                subscribeUserPropertyRO.wrap(userPropertyItems, userPropertyOffset, userPropertyLimit);
                mqttPropertyRW.wrap(propertyBuffer,
                    propertiesSize, propertyBuffer.capacity())
                    .userProperty(subscribeUserPropertyBuilder)
                    .build();
                propertiesSize = mqttPropertyRW.limit();
            }

            publishPropertiesSize = propertiesSize;
            if (qos == 0)
            {
                final MqttPublishV5FW publish = mqttPublishV5RW
                    .wrap(writeBuffer,
                        DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                        .typeAndFlags(publishNetworkTypeAndFlags)
                        .remainingLength(3 + topicNameLength + propertiesSize + payloadSize + deferred)
                        .topicName(topicName)
                        .properties(publishPropertiesBuilder)
                        .build();

                int limit = DataFW.FIELD_OFFSET_PAYLOAD + publish.sizeof();
                writeBuffer.putBytes(limit, payload.buffer(), payload.offset(), payload.sizeof());
                limit += payload.sizeof();
                frameLength = limit - DataFW.FIELD_OFFSET_PAYLOAD;
            }
            else
            {
                final int packetId = subscribeDataEx.subscribe().packetId();
                final MqttPublishQosV5FW publish = mqttPublishQosV5RW
                    .wrap(writeBuffer,
                        DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                        .typeAndFlags(publishNetworkTypeAndFlags)
                        .remainingLength(5 + topicNameLength + propertiesSize + payloadSize + deferred)
                        .topicName(topicName)
                        .packetId(packetId)
                        .properties(publishPropertiesBuilder)
                        .build();

                int limit = DataFW.FIELD_OFFSET_PAYLOAD + publish.sizeof();
                writeBuffer.putBytes(limit, payload.buffer(), payload.offset(), payload.sizeof());
                limit += payload.sizeof();
                frameLength = limit - DataFW.FIELD_OFFSET_PAYLOAD;
            }

            return frameLength;
        }

        private void acknowledgePublishPackets(
            long traceId,
            long authorization)
        {
            while (!unAckedReceivedPublishes.isEmpty() && unAckedReceivedPublishes.getFirst().acknowledged())
            {
                unAckedReceivedPublishes.removeFirst().doEncodePublishAck(traceId, authorization);
            }
        }

        private void doEncodePubackV4(
            long traceId,
            long authorization,
            int packetId)
        {
            final MqttPubackV4FW puback = mqttPubackV4RW
                .wrap(writeBuffer,
                    DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(PUBACK_FIXED_HEADER)
                .remainingLength(2)
                .packetId(packetId)
                .build();

            doNetworkControl(traceId, authorization, puback);
        }

        private void doEncodePubackV5(
            long traceId,
            long authorization,
            int packetId,
            int reasonCode)
        {
            final MqttPubackV5FW puback = mqttPubackV5RW
                .wrap(writeBuffer,
                    DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(PUBACK_FIXED_HEADER)
                .remainingLength(3)
                .packetId(packetId)
                .reasonCode(reasonCode & 0xff)
                .build();

            doNetworkControl(traceId, authorization, puback);
        }

        private void doEncodePubrecV4(
            long traceId,
            long authorization,
            int packetId)
        {
            final MqttPubrecV4FW pubrec = mqttPubrecV4RW
                .wrap(writeBuffer,
                    DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(PUBREC_FIXED_HEADER)
                .remainingLength(2)
                .packetId(packetId)
                .build();

            doNetworkControl(traceId, authorization, pubrec);
        }

        private void doEncodePubrecV5(
            long traceId,
            long authorization,
            int packetId,
            int reasonCode)
        {
            final MqttPubrecV5FW pubrec = mqttPubrecV5RW
                .wrap(writeBuffer,
                    DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(PUBREC_FIXED_HEADER)
                .remainingLength(3)
                .packetId(packetId)
                .reasonCode(reasonCode & 0xff)
                .build();

            doNetworkControl(traceId, authorization, pubrec);
        }

        private void doEncodePubrelV4(
            long traceId,
            long authorization,
            int packetId)
        {
            final MqttPubrelV4FW pubrel = mqttPubrelV4RW
                .wrap(writeBuffer,
                    DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(PUBREL_FIXED_HEADER)
                .remainingLength(2)
                .packetId(packetId)
                .build();

            doNetworkControl(traceId, authorization, pubrel);
        }

        private void doEncodePubrelV5(
            long traceId,
            long authorization,
            int packetId,
            int reasonCode)
        {
            final MqttPubrelV5FW pubrel = mqttPubrelV5RW
                .wrap(writeBuffer,
                    DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(PUBREL_FIXED_HEADER)
                .remainingLength(3)
                .packetId(packetId)
                .reasonCode(reasonCode & 0xff)
                .build();

            doNetworkControl(traceId, authorization, pubrel);
        }

        private void doEncodePubcompV4(
            long traceId,
            long authorization,
            int packetId)
        {
            final MqttPubcompV4FW pubcomp = mqttPubcompV4RW
                .wrap(writeBuffer,
                    DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(PUBCOMP_FIXED_HEADER)
                .remainingLength(2)
                .packetId(packetId)
                .build();

            doNetworkControl(traceId, authorization, pubcomp);
        }

        private void doEncodePubcompV5(
            long traceId,
            long authorization,
            int packetId,
            int reasonCode)
        {
            final MqttPubcompV5FW pubcomp = mqttPubcompV5RW
                .wrap(writeBuffer,
                    DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(PUBCOMP_FIXED_HEADER)
                .remainingLength(3)
                .packetId(packetId)
                .reasonCode(reasonCode & 0xff)
                .build();

            doNetworkControl(traceId, authorization, pubcomp);
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

            if (qos != MqttQoS.AT_MOST_ONCE.value() && (applicationTypeAndFlags & DUP_FLAG) != 0)
            {
                flags |= PUBLISH_DUP_MASK;
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
            String16FW reason,
            int version)
        {
            final int connackReasonCode = connackReasonCode(reasonCode, version);

            if (connackReasonCode != NO_CONNACK)
            {
                if (connackReasonCode == SUCCESS)
                {
                    events.onClientConnected(traceId, routedId, guard, authorization, clientId);
                }

                switch (version)
                {
                case 4:
                    doEncodeConnackV4(traceId, authorization, connackReasonCode, sessionPresent);
                    break;
                case 5:
                    doEncodeConnackV5(traceId, authorization, connackReasonCode, assignedClientId, sessionPresent,
                        serverReference, reason);
                    break;
                default:
                    doEncodeConnackV5(traceId, authorization, connackReasonCode, assignedClientId, sessionPresent,
                        serverReference, reason);
                    break;
                }
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
            String16FW serverReference,
            String16FW reason)
        {
            int propertiesSize = 0;

            MqttPropertyFW mqttProperty;
            if (reasonCode == SUCCESS)
            {
                if (connectSessionExpiry != sessionExpiry)
                {
                    mqttProperty = mqttPropertyRW
                        .wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .sessionExpiry(sessionExpiry)
                        .build();
                    propertiesSize = mqttProperty.limit();
                }

                if (publishQosMax != MqttQoS.EXACTLY_ONCE)
                {
                    mqttProperty = mqttPropertyRW
                        .wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .maximumQoS((byte) publishQosMax.value())
                        .build();
                    propertiesSize = mqttProperty.limit();
                }

                if (!retainAvailable(capabilities))
                {
                    mqttProperty = mqttPropertyRW
                        .wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .retainAvailable((byte) 0)
                        .build();
                    propertiesSize = mqttProperty.limit();
                }

                if (topicAliasMaximum > 0)
                {
                    mqttProperty = mqttPropertyRW
                        .wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .topicAliasMaximum(topicAliasMaximum)
                        .build();
                    propertiesSize = mqttProperty.limit();
                }

                if (!subscriptionIdsAvailable(capabilities))
                {
                    mqttProperty = mqttPropertyRW
                        .wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .subscriptionIdsAvailable((byte) 0)
                        .build();
                    propertiesSize = mqttProperty.limit();
                }

                if (!sharedSubscriptionAvailable(capabilities))
                {
                    mqttProperty = mqttPropertyRW
                        .wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .sharedSubscriptionAvailable((byte) 0)
                        .build();
                    propertiesSize = mqttProperty.limit();
                }

                if (!wildcardAvailable(capabilities))
                {
                    mqttProperty = mqttPropertyRW
                        .wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .wildcardSubscriptionAvailable((byte) 0)
                        .build();
                    propertiesSize = mqttProperty.limit();
                }

                if (assignedClientId)
                {
                    mqttProperty = mqttPropertyRW
                        .wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .assignedClientId(this.clientId)
                        .build();
                    propertiesSize = mqttProperty.limit();
                }

                if (serverDefinedKeepAlive)
                {
                    mqttProperty = mqttPropertyRW
                        .wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .serverKeepAlive(this.keepAlive)
                        .build();
                    propertiesSize = mqttProperty.limit();
                }

                if (maximumPacketSize != Integer.MAX_VALUE)
                {
                    mqttProperty = mqttPropertyRW
                        .wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                        .maximumPacketSize(maximumPacketSize)
                        .build();
                    propertiesSize = mqttProperty.limit();
                }
            }
            else if (reason != null && reason.length() != -1 &&
                withinClientMaximumPacketSize(CONNACK_FIXED_LENGTH,
                    propertiesSize + sizeOfStringProperty(reason) + sizeOfStringProperty(serverReference)))
            {
                mqttProperty = mqttPropertyRW
                    .wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                    .reasonString(reason)
                    .build();
                propertiesSize = mqttProperty.limit();
            }

            if (serverReference != null)
            {
                mqttProperty = mqttPropertyRW
                    .wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
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

            final MqttSubackV4FW suback = mqttSubackV4RW
                .wrap(writeBuffer,
                    DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(0x90)
                .remainingLength(2 + reasonCodes.sizeof())
                .packetId(packetId)
                .payload(reasonCodes)
                .build();

            doNetworkControl(traceId, authorization, suback);
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

            final MqttSubackV5FW suback = mqttSubackV5RW
                .wrap(writeBuffer,
                    DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                .typeAndFlags(0x90)
                .remainingLength(3 + reasonCodes.sizeof())
                .packetId(packetId)
                .properties(p -> p.length(0).value(EMPTY_OCTETS))
                .payload(reasonCodes)
                .build();

            doNetworkControl(traceId, authorization, suback);
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

            doNetworkControl(traceId, authorization, pingResp);
        }

        private void doEncodeDisconnect(
            long traceId,
            long authorization,
            int reasonCode,
            String16FW serverReference,
            String16FW reason,
            Array32FW<io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttUserPropertyFW> userProperties)
        {
            int propertiesSize = 0;
            final int serverReferenceSize = sizeOfStringProperty(serverReference);

            MqttPropertyFW mqttProperty;
            final boolean reasonSent = reason != null && reason.length() != -1 &&
                withinClientMaximumPacketSize(DISCONNECT_FIXED_LENGTH, serverReferenceSize + sizeOfStringProperty(reason));
            if (reasonSent)
            {
                mqttProperty = mqttPropertyRW
                    .wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                    .reasonString(reason)
                    .build();
                propertiesSize = mqttProperty.limit();
            }

            if (serverReference != null)
            {
                mqttProperty = mqttPropertyRW
                    .wrap(propertyBuffer, propertiesSize, propertyBuffer.capacity())
                    .serverReference(serverReference)
                    .build();
                propertiesSize = mqttProperty.limit();
            }

            if (userProperties != null &&
                withinClientMaximumPacketSize(DISCONNECT_FIXED_LENGTH, propertiesSize + sizeOfUserProperties(userProperties)))
            {
                final AtomicInteger userPropertiesSize = new AtomicInteger(propertiesSize);
                userProperties.forEach(p ->
                {
                    mqttPropertyRW.wrap(propertyBuffer, userPropertiesSize.get(), propertyBuffer.capacity())
                        .userProperty(c -> c.key(p.key()).value(p.value()))
                        .build();
                    userPropertiesSize.set(mqttPropertyRW.limit());
                });
                propertiesSize = userPropertiesSize.get();
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

        private boolean withinClientMaximumPacketSize(
            int fixedLength,
            int propertiesLength)
        {
            final int remainingLength = fixedLength + propertiesLength;
            final int remainingLengthSize =
                remainingLength < 0x80 ? 1 : remainingLength < 0x4000 ? 2 : remainingLength < 0x200000 ? 3 : 4;
            return 1 + remainingLengthSize + remainingLength <= clientMaximumPacketSize;
        }

        private int sizeOfStringProperty(
            String16FW value)
        {
            return value != null ? 1 + 2 + Math.max(value.length(), 0) : 0;
        }

        private int sizeOfUserProperties(
            Array32FW<io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttUserPropertyFW> userProperties)
        {
            final AtomicInteger size = new AtomicInteger();
            userProperties.forEach(p -> size.addAndGet(1 + 2 + p.key().length() + 2 + p.value().length()));
            return size.get();
        }

        private void encodeNetwork(
            long traceId,
            long authorization,
            long budgetId,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int encodeWin = encodeMax - (int) (encodeSeq - encodeAck + encodeSlotOffset);
            final int length = Math.min(maxLength, Math.max(encodeWin - encodePad, 0));

            if (length > 0)
            {
                final int reserved = length + encodePad;

                doData(network, originId, routedId, replyId, encodeSeq, encodeAck, state, encodeMax,
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
                    final MutableDirectBufferEx encodeBuffer = bufferPool.buffer(encodeSlot);
                    encodeBuffer.putBytes(0, buffer, offset + length, remaining);
                    encodeSlotOffset = remaining;
                }
            }
            else
            {
                cleanupEncodeSlot();

                if (StreamState.replyClosing(state) ||
                    publishes.isEmpty() && subscribes.isEmpty() && sharedSubscribes.isEmpty() && decoder == decodeIgnoreAll)
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

                final DirectBufferEx buffer = bufferPool.buffer(decodeSlot);
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
            DirectBufferEx buffer,
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
                    final MutableDirectBufferEx slotBuffer = bufferPool.buffer(decodeSlot);
                    slotBuffer.putBytes(0, buffer, progress, limit - progress);
                    decodeSlotOffset = limit - progress;
                    decodeSlotReserved = (limit - progress) * reserved / (limit - offset);
                }
            }
            else
            {
                cleanupDecodeSlot();

                if (StreamState.initialClosing(state))
                {
                    state = StreamState.closeInitial(state);
                    cleanupStreamsUsingAbort(traceId);
                    doNetworkEnd(traceId, authorization);
                }
            }

            if (!StreamState.initialClosed(state))
            {
                doNetworkWindow(traceId, authorization, 0, budgetId, decodeSlotOffset, decodeMax);
            }
        }

        private void cleanupNetwork(
            long traceId,
            long authorization)
        {
            cleanupStreamsUsingAbort(traceId);

            doCancelTimeouts();
            doReleaseOwnership(traceId);
            doNetworkReset(traceId, authorization);
            doNetworkAbort(traceId, authorization);
        }

        private void cleanupStreamsUsingAbort(
            long traceId)
        {
            publishes.values().forEach(s -> s.cleanupAbort(traceId));
            subscribes.values().forEach(ss -> ss.values().forEach(s -> s.cleanupAbort(traceId)));
            sharedSubscribeStreams().forEach(s -> s.cleanupAbort(traceId));
            new ArrayList<>(drainingSubscribes).forEach(s -> s.cleanupAbort(traceId));
            if (session != null)
            {
                session.cleanupAbort(traceId);
            }
        }

        private void closeStreams(
            long traceId,
            long authorization)
        {
            publishes.values().forEach(s -> s.doPublishEnd(traceId));
            subscribes.values().forEach(ss -> ss.values().forEach(s -> s.doSubscribeEnd(traceId)));
            sharedSubscribeStreams().forEach(s -> s.doSubscribeEnd(traceId));
            new ArrayList<>(drainingSubscribes).forEach(s -> s.doSubscribeEnd(traceId));
            if (session != null)
            {
                session.cleanupEnd(traceId);
            }
            doCancelTimeouts();
            doReleaseOwnership(traceId);
            decoder = decodeIgnoreAll;
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

        private boolean willPayloadPending()
        {
            // decodeSlot alone is not a reliable signal here - it is also populated by ordinary
            // network fragmentation of any packet, with no session window retry to complete it.
            // only a will-message decode blocked on session window can resume via that retry.
            return decodeSlot != NO_SLOT &&
                (decoder == decodeConnectWillMessage || decoder == decodeConnectWillMessagePayload);
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
                keepAliveTimeoutAt = clock.millis() + keepAliveTimeout;

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
            connectTimeoutAt = clock.millis() + connectTimeoutMillis;

            if (connectTimeoutId == NO_CANCEL_ID)
            {
                connectTimeoutId = signaler.signalAt(connectTimeoutAt, originId, routedId, replyId,
                    traceId, CONNECT_TIMEOUT_SIGNAL, 0);
            }
        }

        private int calculateSubscribeFlags(
            long traceId,
            long authorization,
            String filter,
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
            case RETAIN_HANDLING_SEND_IF_NEW:
                if (session.subscriptions.stream().noneMatch(s -> s.filter.equals(filter)))
                {
                    flags |= SEND_RETAINED_FLAG;
                }
                break;
            case RETAIN_HANDLING_MASK:
                onDecodeError(traceId, authorization, PROTOCOL_ERROR);
                break;
            }

            return flags;
        }


        private int decodeBudget()
        {
            return decodeMax - (int) (decodeSeq - decodeAck);
        }

        private MqttSessionStateFW sessionState(
            List<Subscription> state)
        {
            mqttSessionStateRW.wrap(sessionStateBuffer, 0, sessionStateBuffer.capacity());

            boolean shared = false;
            for (int index = 0; !shared && index < state.size(); index++)
            {
                shared = state.get(index).shared();
            }

            if (shared)
            {
                mqttSessionStateRW.sessionStateV3(s -> state.forEach(sub -> s.subscriptionsItem(sub::asTopicFilterV2)));
            }
            else
            {
                mqttSessionStateRW.sessionStateV2(s -> state.forEach(sub -> s.subscriptionsItem(sub::asTopicFilterV1)));
            }

            return mqttSessionStateRW.build();
        }

        private static final class PendingRequest
        {
            private final int kind;
            private final int packetId;
            private final String filter;
            private final List<String> filters;
            private PendingRequest next;

            private PendingRequest(
                int kind,
                int packetId,
                String filter,
                List<String> filters,
                PendingRequest next)
            {
                this.kind = kind;
                this.packetId = packetId;
                this.filter = filter;
                this.filters = filters;
                this.next = next;
            }

            private boolean matches(
                int kind,
                int packetId,
                String filter)
            {
                return this.kind == kind &&
                    (kind == PENDING_UNSUBSCRIBE_FILTERS ? this.packetId == packetId : this.filter.equals(filter));
            }
        }

        private final class PendingSuback
        {
            private final int packetId;
            private final List<Subscription> subscriptions;
            private final byte[] reasonCodes;
            private final List<MqttSubscribeStream> streams;

            private PendingSuback(
                int packetId,
                List<Subscription> subscriptions,
                byte[] reasonCodes,
                List<MqttSubscribeStream> streams)
            {
                this.packetId = packetId;
                this.subscriptions = subscriptions;
                this.reasonCodes = reasonCodes;
                this.streams = streams;
            }
        }

        private final class Subscription
        {
            private int id = 0;
            private int filterId;
            private String filter;
            private String shareName;
            private String pattern;
            private int qos;
            private int flags;
            private int reasonCode;

            private long compositeId;

            private boolean retainAsPublished()
            {
                return (flags & RETAIN_AS_PUBLISHED_FLAG) == RETAIN_AS_PUBLISHED_FLAG;
            }

            private boolean shared()
            {
                return MqttValidator.isShared(filter);
            }

            private String shareName()
            {
                resolveShare();

                return shareName;
            }

            private String pattern()
            {
                resolveShare();

                return pattern;
            }

            private void resolveShare()
            {
                if (pattern == null)
                {
                    shareName = MqttValidator.shareName(filter);
                    pattern = shared() ? MqttValidator.sharePattern(filter) : filter;
                }
            }

            private Subscription withTopicFilter(
                String shareName,
                String pattern)
            {
                this.filter = MqttValidator.topicFilter(shareName, pattern);
                this.shareName = shareName;
                this.pattern = pattern;
                if (filterIds == EMPTY_MAP)
                {
                    filterIds = new HashMap<>();
                }
                this.filterId = filterIds.computeIfAbsent(filter, f -> --filterIdSequence);
                return this;
            }

            private Subscription withFilter(
                String shareName,
                String pattern,
                int id,
                int qos,
                int flags,
                int reasonCode)
            {
                this.id = id;
                this.qos = qos;
                this.flags = flags;
                this.reasonCode = reasonCode;
                return withTopicFilter(shareName, pattern);
            }

            private MqttTopicFilterV1FW.Builder asTopicFilterV1(
                MqttTopicFilterV1FW.Builder builder)
            {
                return builder
                    .subscriptionId(id)
                    .qos(qos)
                    .flags(flags)
                    .pattern(pattern());
            }

            private MqttTopicFilterV2FW.Builder asTopicFilterV2(
                MqttTopicFilterV2FW.Builder builder)
            {
                return builder
                    .subscriptionId(id)
                    .qos(qos)
                    .flags(flags)
                    .pattern(pattern())
                    .shareName(shareName());
            }

            private MqttTopicFilterFW.Builder asTopicFilter(
                MqttTopicFilterFW.Builder builder)
            {
                return builder
                    .subscriptionId(filterId)
                    .qos(qos)
                    .flags(flags)
                    .pattern(pattern())
                    .shareName(shareName());
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
                    this.qos == other.qos && this.flags == other.flags && this.outcome() == other.outcome();
            }

            @Override
            public int hashCode()
            {
                return Objects.hash(this.id, this.filter, this.qos, this.flags, this.outcome());
            }

            public int qos()
            {
                return qos;
            }

            private int outcome()
            {
                return unsubackReasonCode(reasonCode);
            }

            public Subscription withCompositeId(
                long compositeId)
            {
                this.compositeId = compositeId;
                return this;
            }
        }

        private class MqttSessionStream
        {
            private List<Subscription> subscriptions;
            private List<Subscription> unAckedSubscriptions;
            private Int2ObjectHashMap<List<String>> deferredUnsubscribes;
            private MessageConsumer application;

            private long originId;
            private long routedId;
            private long initialId;
            private long replyId;
            private long budgetId;

            private BudgetDebit debit;

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
                this.subscriptions = Collections.emptyList();
                this.originId = originId;
                this.routedId = routedId;
                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.packetId = packetId;
                this.unAckedSubscriptions = Collections.emptyList();
                this.deferredUnsubscribes = Int2ObjectHashMaps.emptyMap();
            }

            private void onSession(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                switch (msgTypeId)
                {
                case BeginFW.TYPE_ID:
                    final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                    onSessionBegin(begin);
                    break;
                case FlushFW.TYPE_ID:
                    final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                    onSessionFlush(flush);
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
                final boolean wasOpen = StreamState.initialOpened(state);

                if (!StreamState.initialOpened(state))
                {
                    doCancelConnectTimeout();
                }

                this.state = StreamState.openInitial(state);
                this.budgetId = budgetId;

                assert acknowledge <= sequence;
                assert sequence <= initialSeq;
                assert acknowledge >= initialAck;
                assert maximum + acknowledge >= initialMax + initialAck;

                initialAck = acknowledge;
                initialMax = maximum;
                initialPad = padding;

                assert initialAck <= initialSeq;

                decodeNetwork(traceId);

                if (budgetId != 0L && debit == null)
                {
                    debit = context.supplyDebit(initialId, budgetId, MqttServer.this::decodeNetwork);
                }

                if (StreamState.initialClosing(state) && !StreamState.initialClosed(state))
                {
                    doSessionEnd(traceId, EMPTY_OCTETS);
                }
            }

            private void onSessionReset(
                ResetFW reset)
            {
                final long traceId = reset.traceId();
                final long authorization = reset.authorization();

                final OctetsFW extension = reset.extension();
                final MqttResetExFW mqttResetEx = extension.get(mqttResetExRO::tryWrap);

                byte reasonCode = IMPLEMENTATION_SPECIFIC_ERROR;
                boolean serverRefExists = false;
                String16FW serverRef = null;
                String16FW reason = null;
                Array32FW<io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttUserPropertyFW> userProperties = null;

                if (mqttResetEx != null)
                {
                    userProperties = mqttResetEx.properties();
                    serverRef = mqttResetEx.serverRef();
                    reasonCode = (byte) mqttResetEx.reasonCode();
                    reason = mqttResetEx.reason();
                    serverRefExists = serverRef != null && serverRef.asString() != null;

                    if (reasonCode == SUCCESS)
                    {
                        reasonCode = serverRefExists ? SERVER_MOVED : IMPLEMENTATION_SPECIFIC_ERROR;
                    }
                }

                if (!connected)
                {
                    doCancelConnectTimeout();

                    int connackReasonCode = connackReasonCode(reasonCode, version);

                    if (connackReasonCode == NO_CONNACK)
                    {
                        connackReasonCode = version == MQTT_PROTOCOL_VERSION_4
                            ? SERVER_UNAVAILABLE_V4
                            : IMPLEMENTATION_SPECIFIC_ERROR;
                    }

                    doEncodeConnack(traceId, authorization, connackReasonCode, assignedClientId,
                        false, serverRefExists ? serverRef : null, reason, version);
                }
                else if (version == MQTT_PROTOCOL_VERSION_5)
                {
                    doEncodeDisconnect(traceId, authorization, reasonCode, serverRefExists ? serverRef : null, reason,
                        userProperties);
                }

                doNetworkEnd(traceId, authorization);
                setInitialClosed();
                decodeNetwork(traceId);
                cleanupAbort(traceId);
            }

            private void onSessionBegin(
                BeginFW begin)
            {
                state = StreamState.openingReply(state);

                final long traceId = begin.traceId();

                final OctetsFW extension = begin.extension();
                if (extension.sizeof() > 0)
                {
                    final MqttBeginExFW mqttBeginEx = extension.get(mqttBeginExRO::tryWrap);

                    assert mqttBeginEx.kind() == MqttBeginExFW.KIND_SESSION;
                    final MqttSessionBeginExFW mqttSessionBeginEx = mqttBeginEx.session();
                    final PrimitiveIterator.OfInt packetIds = mqttSessionBeginEx.packetIds();

                    sessionExpiry = mqttSessionBeginEx.expiry();
                    capabilities = mqttSessionBeginEx.capabilities();
                    publishQosMax = MqttQoS.valueOf(mqttSessionBeginEx.publishQosMax());
                    subscribeQosMax = Math.min(subscribeQosMaxDefault, mqttSessionBeginEx.subscribeQosMax());
                    if (packetIds != null)
                    {
                        if (unreleasedPacketIds == EMPTY_INT_LIST)
                        {
                            unreleasedPacketIds = new IntArrayList();
                        }
                        packetIds.forEachRemaining((IntConsumer) p -> unreleasedPacketIds.add(p));
                    }
                }

                doSessionWindow(traceId, encodeSlotOffset, encodeBudgetMax);
            }

            private void onSessionFlush(
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

                if (extension.sizeof() > 0)
                {
                    final MqttFlushExFW sessionFlushEx = extension.get(mqttFlushExRO::tryWrap);
                    final int packetId = sessionFlushEx.session().packetId();

                    unreleasedPacketIds.removeInt(packetId);
                    switch (version)
                    {
                    case 4:
                        doEncodePubcompV4(traceId, authorization, packetId);
                        break;
                    case 5:
                        doEncodePubcompV5(traceId, authorization, packetId, SUCCESS);
                        break;
                    }
                }
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
                    final DirectBufferEx buffer = payload.buffer();
                    final int offset = payload.offset();
                    final int limit = payload.limit();

                    final List<Subscription> sessionSubscriptions = decodeSubscriptions(buffer, offset, limit);

                    byte reasonCode = SUCCESS;
                    if (!connected && isSetWillFlag(connectFlags) && decodeWillQos(connectFlags) > publishQosMax.value())
                    {
                        doCancelConnectTimeout();
                        doEncodeConnack(traceId, authorization, QOS_NOT_SUPPORTED, assignedClientId, false, null, null, version);
                        doSessionEnd(traceId, EMPTY_OCTETS);
                        doNetworkEnd(traceId, authorization);
                        decoder = decodeIgnoreAll;
                    }
                    else if (!connected)
                    {
                        boolean sessionPresent = false;
                        if (sessionSubscriptions != null)
                        {
                            if (isCleanStart(connectFlags))
                            {
                                final MqttDataExFW.Builder sessionDataExBuilder =
                                    mqttSessionDataExRW
                                        .wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                                        .typeId(mqttTypeId)
                                        .session(sessionBuilder -> sessionBuilder.kind(k -> k.set(MqttSessionDataKind.STATE)));
                                doSessionData(traceId, 0, sessionDataExBuilder.build(), emptyRO);
                            }
                            else
                            {
                                openSubscribeStreams(0, traceId, authorization, sessionSubscriptions, true);
                                session.setSubscriptions(sessionSubscriptions);
                                sessionPresent = true;
                            }
                        }
                        else if (sessionExpiry != 0)
                        {
                            final MqttSessionStateFW emptyState = sessionState(List.of());
                            final int emptyStateSize = emptyState.sizeof();

                            if (hasSessionWindow(emptyStateSize))
                            {
                                final MqttDataExFW.Builder sessionDataExBuilder =
                                    mqttSessionDataExRW
                                        .wrap(sessionExtBuffer, 0, sessionExtBuffer.capacity())
                                        .typeId(mqttTypeId)
                                        .session(sessionBuilder -> sessionBuilder.kind(k -> k.set(MqttSessionDataKind.STATE)));
                                doSessionData(traceId, emptyStateSize, sessionDataExBuilder.build(), emptyState);
                            }
                        }
                        doEncodeConnack(traceId, authorization, reasonCode, assignedClientId, sessionPresent,
                            null, null, version);
                        connected = true;

                        if (decoder == decodeAwaitConnack)
                        {
                            decoder = decodePacketTypeByVersion.get(version);
                            decodeNetwork(traceId);
                        }
                    }
                    else
                    {
                        if (sessionSubscriptions != null)
                        {
                            final List<Subscription> newState = sessionSubscriptions;
                            List<Subscription> currentSubscriptions = session.subscriptions();
                            if (newState.size() >= currentSubscriptions.size())
                            {
                                final List<Subscription> newSubscriptions = new ArrayList<>();
                                for (int subscriptionIndex = 0; subscriptionIndex < newState.size(); subscriptionIndex++)
                                {
                                    final Subscription subscription = newState.get(subscriptionIndex);
                                    if (!currentSubscriptions.contains(subscription) ||
                                        unAckedSubscriptions.contains(subscription))
                                    {
                                        newSubscriptions.add(subscription);
                                    }
                                }
                                if (!newSubscriptions.isEmpty() &&
                                    pendingPacketId(PENDING_SUBSCRIBE, newSubscriptions.get(0).filter) != NO_PACKET_ID)
                                {
                                    int packetId = pendingPacketId(PENDING_SUBSCRIBE, newSubscriptions.get(0).filter);
                                    final List<Subscription> acknowledgedSubscriptions =
                                        acknowledgedSubscriptions(packetId, newSubscriptions);
                                    acknowledgedSubscriptions.forEach(sub -> removePendingFilter(PENDING_SUBSCRIBE, sub.filter));
                                    openSubscribeStreams(packetId, traceId, authorization, acknowledgedSubscriptions, false);
                                    unAckedSubscriptions.removeAll(acknowledgedSubscriptions);
                                }
                                else
                                {
                                    openSubscribeStreams(0, traceId, authorization, newState, true);
                                }
                                unAckedSubscriptions.removeAll(newSubscriptions);
                            }
                            else
                            {
                                final List<String> unsubscribedFilters = new ArrayList<>();
                                for (int currentIndex = 0; currentIndex < currentSubscriptions.size(); currentIndex++)
                                {
                                    final Subscription subscription = currentSubscriptions.get(currentIndex);
                                    if (!newState.contains(subscription))
                                    {
                                        unsubscribedFilters.add(subscription.filter);
                                    }
                                }

                                if (!unsubscribedFilters.isEmpty())
                                {
                                    final Map<Integer, List<String>> packetIdToFilters = new HashMap<>();
                                    for (int filterIndex = 0; filterIndex < unsubscribedFilters.size(); filterIndex++)
                                    {
                                        final String unsubscribedFilter = unsubscribedFilters.get(filterIndex);
                                        if (pendingPacketId(PENDING_UNSUBSCRIBE, unsubscribedFilter) != NO_PACKET_ID)
                                        {
                                            packetIdToFilters
                                                .computeIfAbsent(removePendingFilter(PENDING_UNSUBSCRIBE, unsubscribedFilter),
                                                    id -> new ArrayList<>())
                                                .add(unsubscribedFilter);
                                        }
                                    }

                                    if (!packetIdToFilters.isEmpty())
                                    {
                                        for (int unsubscribePacketId : packetIdToFilters.keySet())
                                        {
                                            final List<String> filters = removePendingFilters(unsubscribePacketId);
                                            for (int filterIndex = 0; filterIndex < filters.size(); filterIndex++)
                                            {
                                                final String filter = filters.get(filterIndex);
                                                removePendingFilter(PENDING_UNSUBSCRIBE, filter);
                                            }
                                            sendUnsuback(unsubscribePacketId, traceId, authorization, filters, newState, false);
                                        }
                                    }
                                    else
                                    {
                                        sendUnsuback(packetId, traceId, authorization, unsubscribedFilters, newState, true);
                                    }
                                }
                            }
                            final List<Subscription> granted = new ArrayList<>();
                            for (int subscriptionIndex = 0; subscriptionIndex < newState.size(); subscriptionIndex++)
                            {
                                final Subscription subscription = newState.get(subscriptionIndex);
                                if (subscription.reasonCode == SUCCESS)
                                {
                                    granted.add(subscription);
                                }
                            }
                            session.setSubscriptions(granted);
                            releaseDeferredUnsubscribes(traceId, authorization);
                        }
                    }

                    doSessionWindow(traceId, encodeSlotOffset, encodeBudgetMax);
                }
            }

            private List<Subscription> acknowledgedSubscriptions(
                int packetId,
                List<Subscription> newSubscriptions)
            {
                final List<Subscription> acknowledged = new ArrayList<>();

                for (int unackedIndex = 0; unackedIndex < unAckedSubscriptions.size(); unackedIndex++)
                {
                    final Subscription unacked = unAckedSubscriptions.get(unackedIndex);
                    if (pendingPacketId(PENDING_SUBSCRIBE, unacked.filter) == packetId)
                    {
                        if (unacked.reasonCode != SUCCESS)
                        {
                            acknowledged.add(unacked);
                        }
                        else
                        {
                            final Subscription match = findByFilter(newSubscriptions, unacked.filter);
                            if (match != null)
                            {
                                acknowledged.add(match);
                            }
                        }
                    }
                }

                return acknowledged;
            }

            private List<Subscription> decodeSubscriptions(
                DirectBufferEx buffer,
                int offset,
                int limit)
            {
                final List<Subscription> decoded = new ArrayList<>();

                final MqttSessionStateFW sessionState = mqttSessionStateRO.tryWrap(buffer, offset, limit);

                if (sessionState != null)
                {
                    switch (sessionState.kind())
                    {
                    case V1:
                        sessionState.sessionStateV1().subscriptions().forEach(filter ->
                            decoded.add(new Subscription().withFilter(null, filter.pattern().asString(),
                                resolveLegacySubscriptionId(filter.subscriptionId()), filter.qos(), filter.flags(),
                                filter.reasonCode())));
                        break;
                    case V2:
                        sessionState.sessionStateV2().subscriptions().forEach(filter ->
                            decoded.add(new Subscription().withFilter(null, filter.pattern().asString(),
                                (int) filter.subscriptionId(), filter.qos(), filter.flags(), filter.reasonCode())));
                        break;
                    case V3:
                        sessionState.sessionStateV3().subscriptions().forEach(filter ->
                            decoded.add(new Subscription().withFilter(filter.shareName().asString(), filter.pattern().asString(),
                                (int) filter.subscriptionId(), filter.qos(), filter.flags(), filter.reasonCode())));
                        break;
                    }
                }

                return sessionState != null ? decoded : null;
            }

            private void onSessionEnd(
                EndFW end)
            {
                final long traceId = end.traceId();
                final long authorization = end.authorization();
                if (!StreamState.initialClosed(state))
                {
                    onDecodeError(traceId, authorization, SESSION_TAKEN_OVER);
                }
                //setReplyClosed();
            }

            private void onSessionAbort(
                AbortFW abort)
            {
                final boolean beforeBegin = !StreamState.replyOpening(state);

                setReplyClosed();

                final long traceId = abort.traceId();
                final long authorization = abort.authorization();

                cleanupAbort(traceId);

                if (beforeBegin)
                {
                    cleanupNetwork(traceId, authorization);
                }
            }

            private void doSessionBegin(
                long traceId,
                long affinity,
                Flyweight beginEx)
            {
                if (!StreamState.initialOpening(state))
                {
                    assert state == 0;
                    state = StreamState.openingInitial(state);

                    application = newStream(this::onSession, originId, routedId, initialId, initialSeq, initialAck, state,
                            initialMax,
                        traceId, sessionId, affinity, beginEx);

                    doSessionWindow(traceId, 0, 0);
                }
            }

            private void doSessionFlush(
                long traceId,
                int reserved,
                int packetId)
            {
                flushPacketId = packetId;

                final MqttFlushExFW flushEx = mqttFlushExRW.wrap(flushExtBuffer, 0, flushExtBuffer.capacity())
                    .typeId(mqttTypeId)
                    .session(sessionFlushBuilder)
                    .build();

                doFlush(application, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                    traceId, sessionId, 0L, reserved, flushEx.buffer(), flushEx.offset(), flushEx.sizeof());

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;
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
                assert StreamState.initialOpening(state);

                final DirectBufferEx buffer = payload.buffer();
                final int offset = payload.offset();
                final int limit = payload.limit();
                final int length = limit - offset;
                assert reserved >= length;

                reserved += initialPad;

                if (!StreamState.closed(state))
                {
                    doData(application, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                        traceId, sessionId, budgetId, reserved, buffer, offset, length, dataEx);

                    initialSeq += reserved;
                    assert initialSeq <= initialAck + initialMax;
                }
            }

            private int doSessionData(
                long traceId,
                int flags,
                DirectBufferEx buffer,
                int offset,
                int limit,
                int minimum,
                Flyweight dataEx)
            {
                assert StreamState.initialOpening(state);

                final int length = limit - offset;
                int minBudget = Math.min(length, session.initialBudget());
                int publishablePayloadSize = minBudget > minimum ? minBudget : 0;
                int reserved = publishablePayloadSize + initialPad;

                if (publishablePayloadSize > 0 && !StreamState.closed(state))
                {
                    doData(application, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                        traceId, sessionId, budgetId, reserved, flags, buffer, offset, publishablePayloadSize, dataEx);

                    initialSeq += reserved;
                    assert initialSeq <= initialAck + initialMax;
                }
                return publishablePayloadSize;
            }

            private void cleanupAbort(
                long traceId)
            {
                doSessionAbort(traceId);
            }

            private void cleanupEnd(
                long traceId)
            {
                doSessionEnd(traceId, EMPTY_OCTETS);
            }

            private void doSessionAbort(
                long traceId)
            {
                doSessionAbort(traceId, EMPTY_OCTETS);
            }

            private void doSessionAbort(
                long traceId,
                Flyweight extension)
            {
                if (!StreamState.initialClosed(state))
                {
                    setInitialClosed();

                    doAbort(application, originId, routedId, initialId, initialSeq, initialAck,
                        StreamState.closeInitial(StreamState.abortingInitial(state)), initialMax,
                        traceId, sessionId, extension);
                }
            }

            private void doSessionEnd(
                long traceId,
                Flyweight extension)
            {
                if (StreamState.initialOpening(state) && !StreamState.initialClosed(state))
                {
                    doEnd(application, originId, routedId, initialId, initialSeq, initialAck,
                        StreamState.closeInitial(state), initialMax,
                        traceId, sessionId, extension);
                }
            }

            private void doSessionWindow(
                long traceId,
                int minReplyNoAck,
                int minReplyMax)
            {
                if (StreamState.replyOpening(state))
                {
                    final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

                    if (newReplyAck > replyAck || minReplyMax > replyMax || !StreamState.replyOpened(state))
                    {
                        replyAck = newReplyAck;
                        assert replyAck <= replySeq;

                        replyMax = minReplyMax;

                        state = StreamState.openReply(state);

                        doWindow(application, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                            traceId, sessionId, encodeBudgetId, PUBLISH_FRAMING);
                    }
                }
            }

            private void doSessionReset(
                long traceId)
            {
                if (!StreamState.replyClosed(state))
                {
                    setReplyClosed();

                    doReset(application, originId, routedId, replyId, replySeq, replyAck,
                        StreamState.closeReply(StreamState.abortingReply(state)), replyMax,
                        traceId, sessionId, EMPTY_OCTETS);
                }
            }

            private void setReplyClosed()
            {
                assert !StreamState.replyClosed(state);

                state = StreamState.closeReply(state);
            }

            private void setInitialClosed()
            {
                assert !StreamState.initialClosed(state);

                state = StreamState.closeInitial(state);

                debit = null;
            }

            public void setSubscriptions(List<Subscription> subscriptions)
            {
                this.subscriptions = subscriptions;
            }
            public List<Subscription> subscriptions()
            {
                return subscriptions;
            }

            private int initialBudget()
            {
                return initialMax - (int)(initialSeq - initialAck) - initialPad;
            }
        }

        private class MqttPublishStream
        {
            private MessageConsumer application;
            private final long topicKey;
            private final String topic;
            private final long originId;
            private final long routedId;
            private final long initialId;
            private final long replyId;
            private final long compositeId;
            private final MqttModel contentType;
            private long budgetId;

            private BudgetDebit debit;

            private long initialSeq;
            private long initialAck;
            private int initialMax;
            private int initialMin;
            private int initialPad;
            private int decodablePayloadSize;
            private int qos;
            private final LongArrayList pendingAckSequences = new LongArrayList();
            private final IntArrayList pendingAckPacketIds = new IntArrayList();
            private final IntArrayList pendingAckReasonCodes = new IntArrayList();

            private long replySeq;
            private long replyAck;
            private int replyMax;

            private int state;

            private long publishExpiresId = NO_CANCEL_ID;
            private long publishExpiresAt;

            MqttPublishStream(
                long originId,
                long routedId,
                long compositeId,
                String topic,
                long topicKey,
                ModelConfig config)
            {
                this.originId = originId;
                this.routedId = routedId;
                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.compositeId = compositeId;
                this.topic = topic;
                this.topicKey = topicKey;
                this.contentType = MqttModel.decoder(config != null ? supplyModel.apply(config) : null, modelBuffer);
            }

            private void doPublishBegin(
                long traceId,
                long affinity,
                int qos)
            {
                if (!StreamState.initialOpening(state))
                {
                    assert state == 0;
                    state = StreamState.openingInitial(state);
                    this.qos = qos;

                    final MqttBeginExFW beginEx = mqttPublishBeginExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .compositeId(compositeId)
                        .typeId(mqttTypeId)
                        .publish(p ->
                            p.clientId(clientId())
                                .topic(topic)
                                .flags(retainAvailable(capabilities) ? 1 : 0)
                                .qos(qos))
                        .build();

                    application = newStream(this::onPublish, originId, routedId, initialId, initialSeq, initialAck, state,
                            initialMax,
                        traceId, sessionId, affinity, beginEx);

                    doSignalPublishExpiration(traceId);
                    doPublishWindow(traceId, 0, 0);
                }
            }

            private void doPublishData(
                long traceId,
                long authorization,
                int reserved,
                int packetId,
                OctetsFW payload,
                int flags,
                int offset,
                int limit,
                Flyweight mqttData)
            {
                assert StreamState.initialOpening(state);

                final DirectBufferEx buffer = payload.buffer();
                final int length = limit - offset;
                assert reserved >= length + initialPad;

                doData(application, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                    traceId, sessionId, budgetId, reserved, flags, buffer, offset, length, mqttData);

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;

                if (qos > 0 && hasFin(flags))
                {
                    pendingAckSequences.addLong(initialSeq);
                    pendingAckPacketIds.addInt(packetId);
                    pendingAckReasonCodes.addInt(SUCCESS);
                    if (unAckedReceivedPublishes == EMPTY_LIST)
                    {
                        unAckedReceivedPublishes = new ArrayList<>();
                    }
                    unAckedReceivedPublishes.add(this);
                }

                doSignalPublishExpiration(traceId);
            }

            private void doPublishAbort(
                long traceId)
            {
                if (!StreamState.initialClosed(state))
                {
                    setPublishNetClosed();

                    doAbort(application, originId, routedId, initialId, initialSeq, initialAck,
                        StreamState.closeInitial(StreamState.abortingInitial(state)), initialMax,
                        traceId, sessionId, EMPTY_OCTETS);
                }
            }

            private void onPublish(
                int msgTypeId,
                DirectBufferEx buffer,
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
                case FlushFW.TYPE_ID:
                    final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                    onPublishFlush(flush);
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
                state = StreamState.openingReply(state);

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
                final int minimum = window.minimum();
                final long traceId = window.traceId();
                final long authorization = window.authorization();
                final long budgetId = window.budgetId();
                final int padding = window.padding();

                this.state = StreamState.openInitial(state);
                this.budgetId = budgetId;

                assert acknowledge <= sequence;
                assert sequence <= initialSeq;
                assert acknowledge >= initialAck;
                assert maximum + acknowledge >= initialMax + initialAck;

                initialAck = acknowledge;
                initialMax = maximum;
                initialMin = minimum;
                initialPad = padding;

                assert initialAck <= initialSeq;

                if (budgetId != 0L && debit == null)
                {
                    debit = context.supplyDebit(initialId, budgetId, MqttServer.this::decodeNetwork);
                    debit.declare(traceId, 0, 0);
                }

                if (StreamState.initialClosing(state))
                {
                    doPublishEnd(traceId);
                }
                else if (decodePublisherKey == topicKey)
                {
                    decodeNetwork(traceId);
                }

                acknowledgePublishPackets(traceId, authorization);
            }

            private boolean acknowledged()
            {
                return pendingAckSequences.getLong(0) <= initialAck;
            }

            private void doEncodePublishAck(
                long traceId,
                long authorization)
            {
                final int packetId = pendingAckPacketIds.getInt(0);
                final int reasonCode = pendingAckReasonCodes.getInt(0);
                pendingAckSequences.removeAt(0);
                pendingAckPacketIds.removeAt(0);
                pendingAckReasonCodes.removeAt(0);

                if (qos == 1)
                {
                    switch (version)
                    {
                    case 4:
                        doEncodePubackV4(traceId, authorization, packetId);
                        break;
                    case 5:
                        doEncodePubackV5(traceId, authorization, packetId, reasonCode);
                        break;
                    }
                }
                else
                {
                    switch (version)
                    {
                    case 4:
                        doEncodePubrecV4(traceId, authorization, packetId);
                        break;
                    case 5:
                        doEncodePubrecV5(traceId, authorization, packetId, reasonCode);
                        break;
                    }

                    if (reasonCode < FAILURE_REASON_CODE_MIN)
                    {
                        if (unreleasedPacketIds == EMPTY_INT_LIST)
                        {
                            unreleasedPacketIds = new IntArrayList();
                        }
                        unreleasedPacketIds.add(packetId);
                    }
                }
            }

            private void onPublishFlush(
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

                final MqttFlushExFW mqttFlushEx = extension.get(mqttFlushExRO::tryWrap);

                if (mqttFlushEx != null && mqttFlushEx.kind() == MqttExtensionKind.PUBLISH.value())
                {
                    final MqttPublishFlushExFW publishFlushEx = mqttFlushEx.publish();

                    if (version == MQTT_PROTOCOL_VERSION_5)
                    {
                        final int pendingAckIndex = pendingAckPacketIds.indexOf(publishFlushEx.packetId());

                        if (pendingAckIndex != -1)
                        {
                            pendingAckReasonCodes.setInt(pendingAckIndex, publishFlushEx.reasonCode());
                        }
                    }
                    else
                    {
                        onPublishFailure(traceId, authorization, IMPLEMENTATION_SPECIFIC_ERROR);
                    }
                }
            }

            private void onPublishReset(
                ResetFW reset)
            {
                final long traceId = reset.traceId();
                final long authorization = reset.authorization();
                final OctetsFW extension = reset.extension();
                final MqttResetExFW mqttResetEx = extension.get(mqttResetExRO::tryWrap);

                byte reasonCode = IMPLEMENTATION_SPECIFIC_ERROR;

                if (mqttResetEx != null)
                {
                    reasonCode = (byte) mqttResetEx.reasonCode();
                }

                onPublishFailure(traceId, authorization, reasonCode);
            }

            private void onPublishFailure(
                long traceId,
                long authorization,
                byte reasonCode)
            {
                if (acknowledgesFailedPublish(qos, reasonCode))
                {
                    final int failureReasonCode = Byte.toUnsignedInt(reasonCode);

                    for (int index = 0; index < pendingAckReasonCodes.size(); index++)
                    {
                        pendingAckReasonCodes.setInt(index, failureReasonCode);
                        pendingAckSequences.setLong(index, 0L);
                    }

                    acknowledgePublishPackets(traceId, authorization);

                    if (decodePublisherKey == topicKey && decoder == decodePublishWindow)
                    {
                        decodeablePublishPayloadBytes = decodeablePublishPacketBytes;

                        onDecodePublish(traceId, authorization, qos, decodedRetained, decodedPacketId, failureReasonCode);
                    }
                    else if (decodePublisherKey == topicKey && decoder == decodePublishPayload)
                    {
                        if (publishPayloadModeling)
                        {
                            decodeablePublishPayloadBytes = publishPayloadConsumable;
                            publishPayloadModeling = false;
                        }

                        publishPayloadDeferred = 0;

                        onDecodePublish(traceId, authorization, qos, decodedRetained, decodedPacketId, failureReasonCode);
                    }

                    cleanupAbort(traceId);
                    decodeNetwork(traceId);
                }
                else
                {
                    onDecodeError(traceId, authorization, reasonCode);
                    decoder = decodeIgnoreAll;

                    decodeNetwork(traceId);
                    cleanupAbort(traceId);
                }
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

                final long now = clock.millis();
                if (now >= publishExpiresAt && decodeablePublishPayloadBytes == 0)
                {
                    doPublishEnd(traceId);
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
                publishExpiresAt = clock.millis() + publishTimeoutMillis;

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
                if (StreamState.replyOpening(state))
                {
                    final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

                    if (newReplyAck > replyAck || minReplyMax > replyMax || !StreamState.replyOpened(state))
                    {
                        replyAck = newReplyAck;
                        assert replyAck <= replySeq;

                        replyMax = minReplyMax;

                        state = StreamState.openReply(state);

                        doWindow(application, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                            traceId, sessionId, encodeBudgetId, PUBLISH_FRAMING);
                    }
                }
            }

            private void doPublishReset(
                long traceId)
            {
                if (!StreamState.replyClosed(state))
                {
                    setPublishAppClosed();

                    doReset(application, originId, routedId, replyId, replySeq, replyAck,
                        StreamState.closeReply(StreamState.abortingReply(state)), replyMax,
                        traceId, sessionId, EMPTY_OCTETS);
                }
            }

            private void doPublishEnd(
                long traceId)
            {
                if (!StreamState.initialClosed(state))
                {
                    doCancelPublishExpiration();
                    publishes.remove(topicKey);
                    doEnd(application, originId, routedId, initialId, initialSeq, initialAck,
                        StreamState.closeInitial(state), initialMax,
                        traceId, sessionId, EMPTY_OCTETS);
                }
            }

            private void setPublishNetClosed()
            {
                assert !StreamState.initialClosed(state);

                state = StreamState.closeInitial(state);

                debit = null;

                if (StreamState.closed(state))
                {
                    publishes.remove(topicKey);
                }
            }


            private void setPublishAppClosed()
            {
                assert !StreamState.replyClosed(state);

                state = StreamState.closeReply(state);

                if (StreamState.closed(state))
                {
                    publishes.remove(topicKey);
                }
            }

            private void cleanupAbort(
                long traceId)
            {
                doPublishReset(traceId);
                doPublishAbort(traceId);
                doCancelPublishExpiration();
            }

            private int initialBudget()
            {
                return initialMax - (int)(initialSeq - initialAck);
            }
        }

        private class MqttSubscribeStream
        {
            private MessageConsumer application;
            private final long originId;
            private final long routedId;
            private final long initialId;
            private final long replyId;
            private final long compositeId;
            private long budgetId;

            private BudgetDebit debit;

            private long initialSeq;
            private long initialAck;
            private int initialMax;
            private int initialPad;

            private long replySeq;
            private long replyAck;
            private int replyMax;
            private final List<Runnable> pendingPackets;

            private int state;
            private final List<Subscription> subscriptions;
            private boolean acknowledged;
            private int packetId;
            private int qos;
            private int deliveryQos;
            private final String sharedFilter;
            private final MqttSubscribeDecoder decodeSubscribeMessage = this::decodeSubscribeMessage;
            private final MqttSubscribeDecoder decodeSubscribeFragment = this::decodeSubscribeFragment;
            private final MqttSubscribeDecoder decodeSubscribeIgnore = this::decodeSubscribeIgnore;
            private final MqttSubscribeDecoder decodeSubscribeDrop = this::decodeSubscribeDrop;
            private final MqttSubscribeDecoder decodeSubscribeHold = this::decodeSubscribeHold;
            private MqttSubscribeDecoder subscribeDecoder;
            private int heldSlot = NO_SLOT;
            private int heldSlotOffset;

            MqttSubscribeStream(
                long originId,
                long routedId,
                long compositeId,
                int qos,
                String sharedFilter)
            {
                this.originId = originId;
                this.routedId = routedId;
                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.compositeId = compositeId;
                this.subscriptions = new ArrayList<>();
                this.pendingPackets = new ArrayList<>();
                this.qos = qos;
                this.deliveryQos = qos;
                this.sharedFilter = sharedFilter;
                this.subscribeDecoder = decodeSubscribeMessage;
            }

            private boolean shared()
            {
                return sharedFilter != null;
            }

            private void removeShared()
            {
                final Map<String, MqttSubscribeStream> routeShared = sharedSubscribes.get(routedId);
                if (routeShared != null)
                {
                    if (routeShared.get(sharedFilter) == this)
                    {
                        routeShared.remove(sharedFilter);
                    }

                    if (routeShared.isEmpty())
                    {
                        sharedSubscribes.remove(routedId);
                    }
                }
            }

            private void removeSubscribe()
            {
                if (shared())
                {
                    removeShared();
                }
                else
                {
                    removeNonShared();
                }

                drainingSubscribes.remove(this);
                heldSubscribes.remove(this);
                cleanupHeldSlot();
                awaitingAcknowledgeSubscribes.values().removeIf(stream -> stream == this);
                releaseHeldSubscribes(0L);
            }

            private void removeNonShared()
            {
                final Int2ObjectHashMap<MqttSubscribeStream> routeSubscribes = subscribes.get(routedId);
                if (routeSubscribes != null)
                {
                    if (routeSubscribes.get(qos) == this)
                    {
                        routeSubscribes.remove(qos);
                    }

                    if (routeSubscribes.isEmpty())
                    {
                        subscribes.remove(routedId);
                    }
                }
            }

            private boolean deliveringMessage()
            {
                return subscribeDecoder == decodeSubscribeFragment;
            }

            private Subscription subscriptionByFilter(
                String filter,
                List<Subscription> newState)
            {
                final Subscription found = newState != null ? findByFilter(newState, filter) : null;

                return found != null ? found : findByFilter(subscriptions, filter);
            }

            private List<Subscription> removeSubscriptions(
                List<Subscription> replacements)
            {
                List<Subscription> removed = List.of();

                for (int subscriptionIndex = 0; subscriptionIndex < subscriptions.size(); subscriptionIndex++)
                {
                    final Subscription subscription = subscriptions.get(subscriptionIndex);
                    if (findByFilter(replacements, subscription.filter) != null)
                    {
                        removed = removed.isEmpty() ? new ArrayList<>() : removed;
                        removed.add(subscription);
                    }
                }

                subscriptions.removeAll(removed);

                return removed;
            }

            private void doSubscribeBeginOrFlush(
                long traceId,
                long affinity,
                List<Subscription> subscriptions)
            {
                if (shared())
                {
                    this.subscriptions.clear();
                    this.qos = subscriptions.get(0).qos;
                }

                this.subscriptions.addAll(subscriptions);

                if (!StreamState.initialOpening(state))
                {
                    doSubscribeBegin(traceId, affinity);
                }
                else
                {
                    doSubscribeFlush(traceId, 0);

                    if (StreamState.replyOpening(state))
                    {
                        doSubscribeWindow(traceId, encodeSlotOffset, encodeBudgetMax);
                    }
                }
            }

            private void doSubscribeBegin(
                long traceId,
                long affinity)
            {
                assert state == 0;
                state = StreamState.openingInitial(state);

                if (shared() && clientState != null && clientState.ready() && sharedGeneration(capabilities))
                {
                    final Subscription subscription = subscriptions.get(0);

                    application = MessageConsumer.NOOP;
                    joinShared(subscription.shareName(), subscription.pattern(),
                        generation -> onSubscribeJoined(traceId, affinity, generation),
                        () -> onSubscribeJoinFailed(traceId));
                }
                else
                {
                    doSubscribeBeginEx(traceId, affinity, 0L);
                }
            }

            private void onSubscribeJoined(
                long traceId,
                long affinity,
                long generation)
            {
                if (!StreamState.initialClosed(state))
                {
                    doSubscribeBeginEx(traceId, affinity, generation);
                }
            }

            private void onSubscribeJoinFailed(
                long traceId)
            {
                if (!StreamState.initialClosed(state))
                {
                    setNetClosed();
                    onSubscribeRejected(traceId, sessionId, UNSPECIFIED_ERROR, IMPLEMENTATION_SPECIFIC_ERROR);
                }
            }

            private void doSubscribeBeginEx(
                long traceId,
                long affinity,
                long generation)
            {
                final MqttBeginExFW beginEx = mqttSubscribeBeginExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .compositeId(compositeId)
                    .typeId(mqttTypeId)
                    .subscribe(subscribeBuilder ->
                    {
                        subscribeBuilder.clientId(clientId());
                        subscribeBuilder.qos(qos);
                        subscriptions.forEach(subscription -> subscribeBuilder.filtersItem(subscription::asTopicFilter));
                        subscribeBuilder.generation(generation);
                        if (clientState != null && clientState.ready())
                        {
                            clientState.packets().forEach(packet -> subscribeBuilder.packetsItem(item -> item
                                .packetId(packet.packetId)
                                .state(packet.state.value())
                                .qos(packet.qos)
                                .shareName(packet.shareName)
                                .filter(packet.filter)
                                .metadata(metadata -> metadata.bytes(bytes -> bytes.set(packet.metadata)))));
                        }
                    })
                    .build();

                application = newStream(this::onSubscribe, originId, routedId, initialId, initialSeq, initialAck, state,
                        initialMax,
                    traceId, sessionId, affinity, beginEx);

                doSubscribeWindow(traceId, 0, 0);
            }

            private void doSubscribeFlush(
                long traceId,
                int reserved)
            {
                if (!StreamState.initialClosed(state))
                {
                    doFlush(application, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                        traceId, sessionId, 0L, reserved,
                        ex -> ex.set((b, o, l) -> mqttFlushExRW.wrap(b, o, l)
                            .typeId(mqttTypeId)
                            .subscribe(subscribeBuilder ->
                                subscribeBuilder.filters(builder ->
                                    subscriptions.forEach(subscription -> builder.item(subscription::asTopicFilter))))
                            .build()
                            .sizeof()));

                    initialSeq += reserved;
                    assert initialSeq <= initialAck + initialMax;
                }
            }

            private void doSubscribeFlush(
                long traceId,
                int reserved,
                int qos,
                int packetId,
                int offsetState,
                int reasonCode)
            {
                if (!StreamState.initialClosed(state))
                {
                    flushQos = qos;
                    flushPacketId = packetId;
                    flushOffsetState = offsetState;
                    flushReasonCode = reasonCode;

                    final MqttFlushExFW flushEx = mqttFlushExRW.wrap(flushExtBuffer, 0, flushExtBuffer.capacity())
                        .typeId(mqttTypeId)
                        .subscribe(subscribeFlushBuilder)
                        .build();

                    doFlush(application, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                        traceId, sessionId, 0L, reserved, flushEx.buffer(), flushEx.offset(), flushEx.sizeof());

                    initialSeq += reserved;
                    assert initialSeq <= initialAck + initialMax;
                }
            }

            private void doSubscribeComplete(
                long traceId,
                int qos)
            {
                doSubscribeWindow(traceId, encodeSlotOffset, encodeBudgetMax);

                final int deliveredQos = qos == 1 ? MqttQoS.AT_LEAST_ONCE.ordinal() : MqttQoS.EXACTLY_ONCE.ordinal();

                doSubscribeFlush(traceId, 0, deliveredQos, packetId, QOS2_COMPLETE_OFFSET_STATE, SUCCESS);
            }

            private void doSubscribeFlushOrEnd(
                long traceId,
                List<Subscription> unsubscribed)
            {
                for (int subscriptionIndex = 0; subscriptionIndex < unsubscribed.size(); subscriptionIndex++)
                {
                    final Subscription subscription = unsubscribed.get(subscriptionIndex);
                    if (subscription.reasonCode <= GRANTED_QOS_2)
                    {
                        this.subscriptions.remove(subscription);
                    }
                }

                if (!shared())
                {
                    if (!StreamState.initialOpened(state))
                    {
                        state = StreamState.closingInitial(state);
                    }
                    else
                    {
                        doSubscribeFlush(traceId, 0);
                    }
                }
                else if (this.subscriptions.isEmpty())
                {
                    removeShared();
                    leaveSharedMembership();
                    if (drainingSubscribes == EMPTY_LIST)
                    {
                        drainingSubscribes = new ArrayList<>();
                    }
                    drainingSubscribes.add(this);
                    state = StreamState.closingInitial(state);

                    if (delivering() && StreamState.initialOpened(state))
                    {
                        doSubscribeFlush(traceId, 0);
                    }

                    completeSubscribeClosing(traceId);
                }
            }

            private void leaveSharedMembership()
            {
                if (clientState != null && clientState.ready())
                {
                    final MqttMembershipRecord membership =
                        new MqttMembershipRecord(MqttValidator.shareName(sharedFilter), MqttValidator.sharePattern(sharedFilter));

                    if (clientState.memberships().remove(membership))
                    {
                        clientState.write(() -> leaveShared(List.of(membership), () -> {}));
                    }
                }
            }

            private void doSubscribeDrain(
                long traceId)
            {
                state = StreamState.closingInitial(state);

                if (delivering() && StreamState.initialOpened(state))
                {
                    doSubscribeFlush(traceId, 0, 0, 0, 0, 0);
                }
            }

            private boolean stopped()
            {
                return shared() && StreamState.initialClosing(state);
            }

            private boolean draining()
            {
                return stopped() && !subscriptions.isEmpty() && delivering();
            }

            private boolean delivering()
            {
                return subscribeDecoder == decodeSubscribeFragment ||
                    subscribeDecoder == decodeSubscribeHold ||
                    awaitingAcknowledgeSubscribes.containsValue(this);
            }

            private void completeSubscribeClosing(
                long traceId)
            {
                if (StreamState.initialClosing(state) &&
                    StreamState.initialOpened(state) &&
                    !StreamState.initialClosed(state) &&
                    !delivering())
                {
                    if (subscriptions.isEmpty())
                    {
                        doSubscribeEnd(traceId);
                    }
                    else
                    {
                        releaseDeferredUnsubscribes(traceId, sessionId);
                    }
                }
            }

            private void onSubscribeAcknowledge(
                long traceId,
                int qos,
                int packetId,
                int offsetState,
                int reasonCode,
                boolean released)
            {
                if (offsetState == QOS2_COMPLETE_OFFSET_STATE &&
                    qos == MqttQoS.EXACTLY_ONCE.ordinal() &&
                    clientState != null &&
                    clientState.remove(packetId))
                {
                    clientState.write(() ->
                        completeSubscribeAcknowledge(traceId, qos, packetId, offsetState, reasonCode, released));
                }
                else
                {
                    completeSubscribeAcknowledge(traceId, qos, packetId, offsetState, reasonCode, released);
                }
            }

            private void completeSubscribeAcknowledge(
                long traceId,
                int qos,
                int packetId,
                int offsetState,
                int reasonCode,
                boolean released)
            {
                if (released && !subscriptions.isEmpty())
                {
                    doSubscribeWindow(traceId, encodeSlotOffset, encodeBudgetMax);
                }

                doSubscribeFlush(traceId, 0, qos, packetId, offsetState, reasonCode);
                completeSubscribeClosing(traceId);
            }

            private void doSubscribeAbort(
                long traceId)
            {
                if (!StreamState.initialClosed(state))
                {
                    setNetClosed();

                    doAbort(application, originId, routedId, initialId, initialSeq, initialAck,
                        StreamState.closeInitial(StreamState.abortingInitial(state)), initialMax,
                        traceId, sessionId, EMPTY_OCTETS);
                }
            }

            private void setNetClosed()
            {
                assert !StreamState.initialClosed(state);

                state = StreamState.closeInitial(state);

                debit = null;

                if (StreamState.closed(state))
                {
                    removeSubscribe();
                }
            }

            private void onSubscribe(
                int msgTypeId,
                DirectBufferEx buffer,
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
                state = StreamState.openingReply(state);

                final long traceId = begin.traceId();
                final long authorization = begin.authorization();

                onSubscribeStreamBegin(traceId, authorization, this);

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
                    subscribeDecoder.decode(data);
                    completeSubscribeClosing(traceId);
                }
            }

            private void decodeSubscribeMessage(
                DataFW data)
            {
                final long traceId = data.traceId();
                final long authorization = data.authorization();
                final int flags = data.flags();
                final OctetsFW payload = data.payload();
                final OctetsFW extension = data.extension();
                final MqttDataExFW subscribeDataEx =
                    mqttSubscribeDataExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());

                if (hasInit(flags) && deliverable(payload, subscribeDataEx))
                {
                    if (shared())
                    {
                        deliveryQos = Math.min(subscribeDataEx.subscribe().qos(), qos);
                    }

                    if (recordable(subscribeDataEx))
                    {
                        recordSubscribeMessage(data, subscribeDataEx);
                    }
                    else if (deliveryQos > 0 && !receiveAvailable())
                    {
                        holdSubscribeMessage(data);
                    }
                    else
                    {
                        final int deferred = subscribeDataEx.subscribe().deferred();
                        final int frameLength = encodeSubscribePublish(traceId, authorization, payload, subscribeDataEx);

                        packetId = subscribeDataEx.subscribe().packetId();

                        if (frameLength + deferred <= clientMaximumPacketSize)
                        {
                            doNetworkData(traceId, authorization, 0L, writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD,
                                DataFW.FIELD_OFFSET_PAYLOAD + frameLength);

                            doSubscribeAwaitAcknowledge();

                            if (recorded(subscribeDataEx))
                            {
                                doSubscribeFlush(traceId, 0, deliveryQos, packetId, RECORDED_OFFSET_STATE, SUCCESS);
                            }

                            if (hasFin(flags))
                            {
                                doSubscribeDelivered(traceId);
                            }
                            else
                            {
                                subscribeDecoder = decodeSubscribeFragment;
                                doSubscribeWindow(traceId, encodeSlotOffset, encodeBudgetMax);
                            }
                        }
                        else
                        {
                            droppedHandler.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());

                            if (hasFin(flags))
                            {
                                doSubscribeDropped(traceId);
                            }
                            else
                            {
                                subscribeDecoder = decodeSubscribeDrop;
                                doSubscribeWindow(traceId, encodeSlotOffset, encodeBudgetMax);
                            }
                        }
                    }
                }
                else
                {
                    decodeSubscribeIgnore(data);
                }
            }

            private void decodeSubscribeFragment(
                DataFW data)
            {
                final long traceId = data.traceId();
                final long authorization = data.authorization();
                final int flags = data.flags();
                final OctetsFW payload = data.payload();

                if (payload != null)
                {
                    doNetworkData(traceId, authorization, 0L, payload);
                }
                else
                {
                    droppedHandler.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
                }

                if (hasFin(flags))
                {
                    subscribeDecoder = decodeSubscribeMessage;
                    pendingPackets.forEach(Runnable::run);
                    pendingPackets.clear();
                    doSubscribeDelivered(traceId);
                }
                else
                {
                    doSubscribeWindow(traceId, encodeSlotOffset, encodeBudgetMax);
                }
            }

            private void decodeSubscribeDrop(
                DataFW data)
            {
                final long traceId = data.traceId();

                droppedHandler.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());

                if (hasFin(data.flags()))
                {
                    subscribeDecoder = decodeSubscribeMessage;
                    doSubscribeDropped(traceId);
                }
                else
                {
                    doSubscribeWindow(traceId, encodeSlotOffset, encodeBudgetMax);
                }
            }

            private void decodeSubscribeIgnore(
                DataFW data)
            {
                final long traceId = data.traceId();
                final int flags = data.flags();

                droppedHandler.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());

                subscribeDecoder = hasFin(flags) ? decodeSubscribeMessage : decodeSubscribeIgnore;

                if (!subscriptions.isEmpty())
                {
                    doSubscribeWindow(traceId, encodeSlotOffset, encodeBudgetMax);
                }
            }

            private boolean deliverable(
                OctetsFW payload,
                MqttDataExFW subscribeDataEx)
            {
                return payload != null &&
                    subscribeDataEx != null &&
                    !subscriptions.isEmpty() &&
                    !stopped() &&
                    subscribed(subscribeDataEx);
            }

            private boolean subscribed(
                MqttDataExFW subscribeDataEx)
            {
                final Array32FW<Varint32FW> filterIds = subscribeDataEx.subscribe().subscriptionIds();
                final String16FW topicName = subscribeDataEx.subscribe().topic();
                final DirectBufferEx filterIdItems = filterIds.items();
                final int filterIdLimit = filterIdItems.capacity();

                boolean subscribed = filterIds.isEmpty();

                for (int filterIdOffset = 0; !subscribed && filterIdOffset < filterIdLimit; filterIdOffset = filterIdRO.limit())
                {
                    filterIdRO.wrap(filterIdItems, filterIdOffset, filterIdLimit);
                    subscribed = deliverableSubscription(subscriptions, filterIdRO.value(), topicName) != null;
                }

                return subscribed;
            }

            private int encodeSubscribePublish(
                long traceId,
                long authorization,
                OctetsFW payload,
                MqttDataExFW subscribeDataEx)
            {
                int frameLength = 0;

                switch (version)
                {
                case 4:
                    frameLength = encodePublishV4(traceId, authorization, subscriptions, payload, subscribeDataEx, deliveryQos);
                    break;
                case 5:
                    frameLength = encodePublishV5(traceId, authorization, subscriptions, payload, subscribeDataEx, deliveryQos);
                    break;
                }

                return frameLength;
            }

            private void doSubscribeDropped(
                long traceId)
            {
                if (deliveryQos == 0)
                {
                    doSubscribeWindow(traceId, encodeSlotOffset, encodeBudgetMax);
                }
                else
                {
                    doSubscribeComplete(traceId, deliveryQos);
                }
            }

            private void doSubscribeDelivered(
                long traceId)
            {
                if (deliveryQos == 0)
                {
                    doSubscribeWindow(traceId, encodeSlotOffset, encodeBudgetMax);
                }
            }

            private void doSubscribeAwaitAcknowledge()
            {
                if (awaitingAcknowledgeSubscribes == EMPTY_INT_MAP)
                {
                    awaitingAcknowledgeSubscribes = new Int2ObjectHashMap<>();
                }

                if (deliveryQos == 1)
                {
                    awaitingAcknowledgeSubscribes.put(acknowledgeKey(deliveryQos, packetId), this);
                }
                else if (deliveryQos == 2)
                {
                    awaitingAcknowledgeSubscribes.put(acknowledgeKey(deliveryQos, packetId), this);
                }
            }

            private boolean recordable(
                MqttDataExFW subscribeDataEx)
            {
                return clientState != null &&
                    clientState.ready() &&
                    deliveryQos == MqttQoS.EXACTLY_ONCE.value() &&
                    subscribeDataEx.subscribe().metadata().length() > 0 &&
                    clientState.packet(subscribeDataEx.subscribe().packetId()) == null;
            }

            private boolean recorded(
                MqttDataExFW subscribeDataEx)
            {
                return clientState != null &&
                    clientState.ready() &&
                    deliveryQos == MqttQoS.EXACTLY_ONCE.value() &&
                    subscribeDataEx.subscribe().metadata().length() > 0 &&
                    (subscribeDataEx.subscribe().flags() & DUP_FLAG) == 0;
            }

            private void recordSubscribeMessage(
                DataFW data,
                MqttDataExFW subscribeDataEx)
            {
                final long traceId = data.traceId();
                final Subscription subscription = subscriptions.get(0);
                final OctetsFW metadata = subscribeDataEx.subscribe().metadata().bytes();
                final byte[] metadataBytes = new byte[metadata.sizeof()];
                metadata.buffer().getBytes(metadata.offset(), metadataBytes);

                clientState.add(new MqttPacketRecord(
                    subscribeDataEx.subscribe().packetId(),
                    MqttPacketState.PUBLISH_SENT,
                    deliveryQos,
                    subscription.shareName(),
                    shared() ? subscription.pattern() : null,
                    metadataBytes));

                subscribeDecoder = decodeSubscribeHold;
                decodeSubscribeHold(data);

                clientState.write(() -> releaseRecorded(traceId));
            }

            private void releaseRecorded(
                long traceId)
            {
                if (heldSlot != NO_SLOT)
                {
                    releaseHeld(traceId);
                }
            }

            private void holdSubscribeMessage(
                DataFW data)
            {
                subscribeDecoder = decodeSubscribeHold;
                if (heldSubscribes == EMPTY_LIST)
                {
                    heldSubscribes = new ArrayList<>();
                }
                heldSubscribes.add(this);
                decodeSubscribeHold(data);
            }

            private void decodeSubscribeHold(
                DataFW data)
            {
                if (heldSlot == NO_SLOT)
                {
                    heldSlot = bufferPool.acquire(replyId);
                }

                if (heldSlot == NO_SLOT)
                {
                    doSubscribeReset(data.traceId());
                    doNetworkAbort(data.traceId(), data.authorization());
                }
                else
                {
                    final MutableDirectBufferEx heldBuffer = bufferPool.buffer(heldSlot);
                    heldBuffer.putBytes(heldSlotOffset, data.buffer(), data.offset(), data.sizeof());
                    heldSlotOffset += data.sizeof();
                }
            }

            private void releaseHeld(
                long traceId)
            {
                final int slot = heldSlot;
                final int limit = heldSlotOffset;

                heldSlot = NO_SLOT;
                heldSlotOffset = 0;
                subscribeDecoder = decodeSubscribeMessage;

                final MutableDirectBufferEx heldBuffer = bufferPool.buffer(slot);
                for (int progress = 0; progress < limit; )
                {
                    final DataFW data = dataRO.wrap(heldBuffer, progress, limit);
                    progress = data.limit();
                    subscribeDecoder.decode(data);
                }

                bufferPool.release(slot);
                completeSubscribeClosing(traceId);
            }

            private void cleanupHeldSlot()
            {
                if (heldSlot != NO_SLOT)
                {
                    bufferPool.release(heldSlot);
                    heldSlot = NO_SLOT;
                    heldSlotOffset = 0;
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

                if (extension.sizeof() > 0)
                {
                    final MqttFlushExFW subscribeFlushEx = extension.get(mqttFlushExRO::tryWrap);
                    final int packetId = subscribeFlushEx.subscribe().packetId();
                    final MqttPacketRecord packet = clientState != null ? clientState.packet(packetId) : null;

                    if (subscribeFlushEx.subscribe().state() == ABANDONED_OFFSET_STATE)
                    {
                        if (packet != null && clientState.remove(packetId))
                        {
                            clientState.write(() -> {});
                        }
                    }
                    else if (packet != null && packet.state == MqttPacketState.PUBLISH_SENT)
                    {
                        clientState.replace(packet.pubrelPending());
                        clientState.write(() -> doSubscribePubrel(traceId, authorization, packetId));
                    }
                    else
                    {
                        doSubscribePubrel(traceId, authorization, packetId);
                    }
                }
            }

            private void doSubscribePubrel(
                long traceId,
                long authorization,
                int packetId)
            {
                switch (version)
                {
                case 4:
                    doEncodePubrelV4(traceId, authorization, packetId);
                    break;
                case 5:
                    doEncodePubrelV5(traceId, authorization, packetId, SUCCESS);
                    break;
                }
                if (awaitingAcknowledgeSubscribes == EMPTY_INT_MAP)
                {
                    awaitingAcknowledgeSubscribes = new Int2ObjectHashMap<>();
                }
                awaitingAcknowledgeSubscribes.put(acknowledgeKey(MqttQoS.EXACTLY_ONCE.ordinal(), packetId), this);
            }


            private void onSubscribeReset(
                ResetFW reset)
            {
                if (!StreamState.initialClosed(state))
                {
                    setNetClosed();
                }

                final long traceId = reset.traceId();
                final long authorization = reset.authorization();
                final OctetsFW extension = reset.extension();
                final MqttResetExFW mqttResetEx = extension.get(mqttResetExRO::tryWrap);

                final boolean reasoned = mqttResetEx != null && mqttResetEx.reasonCode() != SUCCESS;
                final int subackReasonCode = reasoned ? mqttResetEx.reasonCode() : UNSPECIFIED_ERROR;
                final int disconnectReasonCode = reasoned ? mqttResetEx.reasonCode() : IMPLEMENTATION_SPECIFIC_ERROR;

                onSubscribeRejected(traceId, authorization, subackReasonCode, disconnectReasonCode);
            }

            private void onSubscribeRejected(
                long traceId,
                long authorization,
                int subackReasonCode,
                int disconnectReasonCode)
            {
                final boolean rejected = onSubscribeStreamRejected(traceId, authorization, this, subackReasonCode);
                final boolean unsubscribing = stopped();

                if (!rejected && !subscriptions.isEmpty() && !unsubscribing)
                {
                    onDecodeError(traceId, authorization, disconnectReasonCode);
                    decoder = decodeIgnoreAll;
                    decodeNetwork(traceId);
                }

                cleanupAbort(traceId);

                if (unsubscribing)
                {
                    releaseDeferredUnsubscribes(traceId, sessionId);
                }
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

                this.state = StreamState.openInitial(state);
                this.budgetId = budgetId;

                assert acknowledge <= sequence;
                assert sequence <= initialSeq;
                assert acknowledge >= initialAck;
                assert maximum + acknowledge >= initialMax + initialAck;

                initialAck = acknowledge;
                initialMax = maximum;
                initialPad = padding;

                assert initialAck <= initialSeq;

                if (budgetId != 0L && debit == null)
                {
                    debit = context.supplyDebit(initialId, budgetId, MqttServer.this::decodeNetwork);
                }

                completeSubscribeClosing(traceId);
            }

            private void onSubscribeEnd(
                EndFW end)
            {
                assert !StreamState.replyClosed(state);

                final boolean beforeBegin = !StreamState.replyOpening(state);

                state = StreamState.closeReply(state);
                setSubscribeAppClosed();

                if (beforeBegin)
                {
                    onSubscribeRejected(end.traceId(), end.authorization(), UNSPECIFIED_ERROR, IMPLEMENTATION_SPECIFIC_ERROR);
                }
            }

            private void onSubscribeAbort(
                AbortFW abort)
            {
                assert !StreamState.replyClosed(state);

                final boolean beforeBegin = !StreamState.replyOpening(state);

                state = StreamState.closeReply(state);
                setSubscribeAppClosed();

                final long traceId = abort.traceId();
                final long authorization = abort.authorization();

                if (beforeBegin)
                {
                    onSubscribeRejected(traceId, authorization, UNSPECIFIED_ERROR, IMPLEMENTATION_SPECIFIC_ERROR);
                }
                else
                {
                    final boolean unsubscribing = stopped();

                    cleanupAbort(traceId);

                    if (unsubscribing)
                    {
                        releaseDeferredUnsubscribes(traceId, sessionId);
                    }
                }
            }

            private void cleanupAbort(
                long traceId)
            {
                doSubscribeReset(traceId);
                doSubscribeAbort(traceId);
            }


            private void doSubscribeWindow(
                long traceId,
                int minReplyNoAck,
                int minReplyMax)
            {
                final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

                if (newReplyAck > replyAck || minReplyMax > replyMax || !StreamState.replyOpened(state))
                {
                    replyAck = newReplyAck;
                    assert replyAck <= replySeq;

                    replyMax = minReplyMax;

                    state = StreamState.openReply(state);

                    doWindow(application, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                        traceId, sessionId, encodeBudgetId, PUBLISH_FRAMING);
                }
            }

            private void doSubscribeReset(
                long traceId)
            {
                if (!StreamState.replyClosed(state))
                {
                    assert !StreamState.replyClosed(state);

                    state = StreamState.closeReply(state);
                    setSubscribeAppClosed();

                    doReset(application, originId, routedId, replyId, replySeq, replyAck,
                        StreamState.closeReply(StreamState.abortingReply(state)), replyMax,
                        traceId, sessionId, EMPTY_OCTETS);
                }
            }

            private void doSubscribeEnd(
                long traceId)
            {
                if (StreamState.initialOpening(state) && !StreamState.initialClosed(state))
                {
                    setNetClosed();

                    doEnd(application, originId, routedId, initialId, initialSeq, initialAck,
                        state, initialMax,
                        traceId, sessionId, EMPTY_OCTETS);
                }
            }


            private void setSubscribeAppClosed()
            {
                if (StreamState.closed(state))
                {
                    removeSubscribe();
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

    private static int resolveLegacySubscriptionId(
        long chosenSubscriptionId)
    {
        final int subscriptionId = (int) chosenSubscriptionId;

        return (subscriptionId & LEGACY_GENERATED_SUBSCRIPTION_ID_MASK) == LEGACY_GENERATED_SUBSCRIPTION_ID_MASK
            ? 0 : subscriptionId;
    }

    private static boolean systemTopic(
        String16FW topicName)
    {
        final DirectBufferEx value = topicName.value();

        return value != null && value.capacity() > 0 && value.getByte(0) == '$';
    }

    private static boolean subscriptionIdsAvailable(
        int capabilities)
    {
        return (capabilities & SUBSCRIPTION_IDS_AVAILABLE_MASK) != 0;
    }

    private static boolean sharedGeneration(
        int capabilities)
    {
        return (capabilities & SHARED_GENERATION_MASK) != 0;
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
            willQos = (flags & WILL_QOS_MASK) >>> 3;
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

    private static boolean isSetReceiveMaximum(
        int flags)
    {
        return (flags & CONNECT_RECEIVE_MAXIMUM_MASK) != 0;
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

    private static int connackReasonCode(
        int reasonCode,
        int version)
    {
        return version == MQTT_PROTOCOL_VERSION_4
            ? connackReasonCodeV4(reasonCode)
            : isConnackReasonCode(reasonCode) ? reasonCode : NO_CONNACK;
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
        private String16FW willTopic;
        private String16FW username;
        private BinaryFW password;
        private int payloadSize;

        private long willDelay = DEFAULT_WILL_DELAY;
        private MqttPayloadFormat payloadFormat = DEFAULT_FORMAT;
        private int expiryInterval = DEFAULT_EXPIRY_INTERVAL;
        private String16FW contentType = NULL_STRING;
        private String16FW responseTopic = NULL_STRING;
        private OctetsFW correlationData = null;

        private MqttConnectPayload reset()
        {
            this.reasonCode = SUCCESS;
            this.willProperties = null;
            this.willTopic = null;
            this.username = null;
            this.password = null;
            this.willDelay = DEFAULT_WILL_DELAY;
            this.payloadFormat = DEFAULT_FORMAT;
            this.expiryInterval = DEFAULT_EXPIRY_INTERVAL;
            this.contentType = NULL_STRING;
            this.responseTopic = NULL_STRING;
            this.correlationData = null;
            this.payloadSize = 0;

            return this;
        }

        private int decode(
            DirectBufferEx buffer,
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
                        payloadSize = mqttWillV4.payloadSize();
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
                        payloadSize = mqttWillV5.payloadSize();
                        progress = mqttWillV5.limit();
                        break;
                    }

                    if (willTopic == null || willTopic.asString().isEmpty() || !utf8Validator.isValidString(willTopic))
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }

                    if (!validator.isTopicNameValid(willTopic.asString()))
                    {
                        reasonCode = TOPIC_NAME_INVALID;
                        break decode;
                    }
                }

                if (isSetUsername(flags))
                {
                    username = usernameRO.tryWrap(buffer, progress, limit);
                    if (username == null || username.sizeof() == 0 || !utf8Validator.isValidString(username))
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
            final DirectBufferEx decodeBuffer = propertiesValue.buffer();
            final int decodeOffset = propertiesValue.offset();
            final int decodeLimit = propertiesValue.limit();

            decode:
            {
                for (int decodeProgress = decodeOffset; decodeProgress < decodeLimit; )
                {
                    final MqttPropertyFW mqttProperty = mqttPropertyRO.tryWrap(decodeBuffer, decodeProgress, decodeLimit);
                    if (mqttProperty == null)
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }

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
                        if (!utf8Validator.isValidString(mContentType))
                        {
                            reasonCode = MALFORMED_PACKET;
                            break decode;
                        }
                        if (mContentType.value() != null)
                        {
                            final int offset = mContentType.offset();
                            final int limit = mContentType.limit();

                            contentType = contentTypeRO.wrap(mContentType.buffer(), offset, limit);
                        }
                        break;
                    case KIND_RESPONSE_TOPIC:
                        final String16FW mResponseTopic = mqttProperty.responseTopic();
                        if (!utf8Validator.isValidString(mResponseTopic))
                        {
                            reasonCode = MALFORMED_PACKET;
                            break decode;
                        }
                        if (mResponseTopic.value() != null)
                        {
                            final String responseTopicName = mResponseTopic.asString();
                            if (!validator.isTopicNameValid(responseTopicName))
                            {
                                reasonCode = (byte) topicReasonCode(responseTopicName);
                                break decode;
                            }

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
                        if (!utf8Validator.isValidString(userProperty.key()) ||
                            !utf8Validator.isValidString(userProperty.value()))
                        {
                            reasonCode = MALFORMED_PACKET;
                            break decode;
                        }
                        willUserPropertiesRW.item(c -> c.key(userProperty.key()).value(userProperty.value()));
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

    private final class MqttPublishHelper
    {
        private String16FW topicName;
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
        private Array32FW<MqttUserPropertyFW> userProperties = null;

        private String topic()
        {
            topic = topic != null ? topic : topicName.asString();

            return topic;
        }

        private long topicKey()
        {
            return topic != null ? MqttServerFactory.this.topicKey(topic, qos) : MqttServerFactory.this.topicKey(topicName, qos);
        }

        private MqttPublishHelper reset()
        {
            this.topicName = null;
            this.topic = null;
            this.flags = 0;
            this.qos = 0;
            this.packetId = 0;
            this.expiryInterval = DEFAULT_EXPIRY_INTERVAL;
            this.contentType = NULL_STRING;
            this.payloadFormat = DEFAULT_FORMAT;
            this.responseTopic = NULL_STRING;
            this.correlationData = null;
            this.retained = false;

            return this;
        }

        private int decodeV5(
            long traceId,
            long authorization,
            MqttServer server,
            String16FW topicName,
            MqttPropertiesFW properties,
            int typeAndFlags,
            int qos,
            int packetId)
        {
            this.topicName = topicName;
            this.qos = qos;
            this.packetId = packetId;
            int reasonCode = SUCCESS;
            userPropertiesRW.wrap(userPropertiesBuffer, 0, userPropertiesBuffer.capacity());

            final OctetsFW propertiesValue = properties.value();
            final DirectBufferEx decodeBuffer = propertiesValue.buffer();
            final int decodeOffset = propertiesValue.offset();
            final int decodeLimit = propertiesValue.limit();

            if (topicName.value() == null)
            {
                reasonCode = PROTOCOL_ERROR;
            }
            else if (!utf8Validator.isValidString(topicName))
            {
                reasonCode = MALFORMED_PACKET;
            }
            else
            {
                flags = calculatePublishApplicationFlags(typeAndFlags);
                final MqttBindingConfig binding = bindings.get(server.routedId);

                int alias = 0;

                decode:
                for (int decodeProgress = decodeOffset; decodeProgress < decodeLimit; )
                {
                    final MqttPropertyFW mqttProperty = mqttPropertyRO.tryWrap(decodeBuffer, decodeProgress, decodeLimit);
                    if (mqttProperty == null)
                    {
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }

                    switch (mqttProperty.kind())
                    {
                    case KIND_SUBSCRIPTION_ID:
                        reasonCode = PROTOCOL_ERROR;
                        break decode;
                    case KIND_EXPIRY_INTERVAL:
                        expiryInterval = mqttProperty.expiryInterval();
                        break;
                    case KIND_CONTENT_TYPE:
                        final String16FW mContentType = mqttProperty.contentType();
                        if (!utf8Validator.isValidString(mContentType))
                        {
                            reasonCode = MALFORMED_PACKET;
                            break decode;
                        }
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

                        if (topicName.length() == 0)
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
                            if (server.topicAliases == EMPTY_INT_MAP)
                            {
                                server.topicAliases = new Int2ObjectHashMap<>();
                            }
                            server.topicAliases.put(alias, topic());
                        }
                        break;
                    case KIND_PAYLOAD_FORMAT:
                        payloadFormat = MqttPayloadFormat.valueOf(mqttProperty.payloadFormat());
                        break;
                    case KIND_RESPONSE_TOPIC:
                        final String16FW mResponseTopic = mqttProperty.responseTopic();
                        if (!utf8Validator.isValidString(mResponseTopic))
                        {
                            reasonCode = MALFORMED_PACKET;
                            break decode;
                        }
                        if (mResponseTopic.value() != null)
                        {
                            if (!validator.isTopicNameValid(mResponseTopic))
                            {
                                reasonCode = topicReasonCode(mResponseTopic);
                                break decode;
                            }

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
                        final String16FW userPropertyKey = userProperty.key();
                        final String16FW userPropertyValue = userProperty.value();
                        if (!utf8Validator.isValidString(userPropertyKey) || !utf8Validator.isValidString(userPropertyValue))
                        {
                            reasonCode = MALFORMED_PACKET;
                            break decode;
                        }
                        final ModelConfig config =
                            binding.hasTopicModels() ? binding.supplyUserPropertyModelConfig(topic(), userPropertyKey) : null;
                        final MqttModel model =
                            MqttModel.decoder(config != null ? supplyModel.apply(config) : null, modelBuffer);
                        if (model.active())
                        {
                            final int produced = model.transform(traceId, server.routedId, authorization,
                                userPropertyValue.buffer(),
                                userPropertyValue.offset() + BitUtil.SIZE_OF_SHORT, userPropertyValue.limit());
                            if (produced < 0)
                            {
                                reasonCode = PAYLOAD_FORMAT_INVALID;
                            }
                            else
                            {
                                final String16FW transformed = userPropertyValueRW
                                    .wrap(userPropertyValueBuffer, 0, userPropertyValueBuffer.capacity())
                                    .set(model.buffer(), 0, produced)
                                    .build();
                                userPropertiesRW.item(c -> c.key(userPropertyKey).value(transformed));
                            }
                        }
                        else
                        {
                            userPropertiesRW.item(c -> c.key(userPropertyKey).value(userPropertyValue));
                        }
                        break;
                    default:
                        reasonCode = MALFORMED_PACKET;
                        break decode;
                    }
                    decodeProgress = mqttProperty.limit();
                }
                userProperties = userPropertiesRW.build();

                if (reasonCode == SUCCESS)
                {
                    reasonCode = topic != null ? topicNameReasonCode(topic) : topicNameReasonCode(topicName);
                }
            }

            return reasonCode;
        }

        private int topicNameReasonCode(
            String name)
        {
            return validator.isTopicNameValid(name) ? SUCCESS : topicReasonCode(name);
        }

        private int topicNameReasonCode(
            String16FW name)
        {
            return validator.isTopicNameValid(name) ? SUCCESS : topicReasonCode(name);
        }

        private int decodeV4(
            MqttServer server,
            String16FW topicName,
            int typeAndFlags,
            int qos,
            int packetId)
        {
            this.qos = qos;
            this.packetId = packetId;
            this.topicName = topicName;
            int reasonCode = SUCCESS;
            if (topicName.value() == null)
            {
                reasonCode = PROTOCOL_ERROR;
            }
            else if (!utf8Validator.isValidString(topicName))
            {
                reasonCode = MALFORMED_PACKET;
            }
            else if (!validator.isTopicNameValid(topicName))
            {
                reasonCode = topicReasonCode(topicName);
            }
            else
            {
                flags = calculatePublishApplicationFlags(typeAndFlags);
            }

            return reasonCode;
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

    private void buildSubscribeUserProperty(
        MqttUserPropertyFW.Builder item)
    {
        item.key(subscribeUserPropertyRO.key()).value(subscribeUserPropertyRO.value());
    }

    private void buildPublishProperties(
        MqttPropertiesFW.Builder properties)
    {
        properties.length(publishPropertiesSize).value(propertyBuffer, 0, publishPropertiesSize);
    }

    private void buildSessionFlush(
        MqttSessionFlushExFW.Builder session)
    {
        session.packetId(flushPacketId);
    }

    private void buildSubscribeFlush(
        MqttSubscribeFlushExFW.Builder subscribe)
    {
        subscribe.qos(flushQos).packetId(flushPacketId).state(flushOffsetState).reasonCode(flushReasonCode);
    }

    private void buildPublishUserProperty(
        io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttUserPropertyFW.Builder item)
    {
        item.key(publishUserPropertyRO.key()).value(publishUserPropertyRO.value());
    }

    private static MqttPayloadFormatFW[] payloadFormats()
    {
        final MqttPayloadFormat[] formats = MqttPayloadFormat.values();
        final MqttPayloadFormatFW[] flyweights = new MqttPayloadFormatFW[formats.length];

        for (MqttPayloadFormat format : formats)
        {
            flyweights[format.ordinal()] = new MqttPayloadFormatFW.Builder()
                .wrap(new UnsafeBufferEx(new byte[Byte.BYTES]), 0, Byte.BYTES)
                .set(format)
                .build();
        }

        return flyweights;
    }

    private UnsafeBufferEx encodeClientId(
        String16FW clientId)
    {
        final int length = clientId.length();
        final UnsafeBufferEx encoded = new UnsafeBufferEx(new byte[Short.BYTES + length]);

        encoded.putShort(0, (short) length, nativeOrder());
        encoded.putBytes(Short.BYTES, clientId.buffer(), clientId.offset() + Short.BYTES, length);

        return encoded;
    }

    private static final class DecodedBytes
    {
        private static final int ABSENT = -1;

        private UnsafeBufferEx buffer;
        private int length = ABSENT;

        private void reserve(
            int length)
        {
            if (buffer == null || buffer.capacity() < length)
            {
                buffer = new UnsafeBufferEx(new byte[length]);
            }

            this.length = length;
        }

        private void clear()
        {
            length = ABSENT;
        }
    }

    private static final class OwnershipRecord
    {
        private static final String WEAK_PREFIX = "W/";
        private static final String FIELD_SEPARATOR = "\u001f";

        private final boolean strong;
        private final String identity;
        private final String nonce;
        private final String token;

        private OwnershipRecord(
            boolean strong,
            String identity,
            String nonce,
            String token)
        {
            this.strong = strong;
            this.identity = identity;
            this.nonce = nonce;
            this.token = token;
        }

        private static OwnershipRecord decode(
            String value)
        {
            OwnershipRecord record = null;

            if (value != null && !value.isEmpty())
            {
                final boolean strong = !value.startsWith(WEAK_PREFIX);
                final String body = strong ? value : value.substring(WEAK_PREFIX.length());
                final int firstSeparator = body.indexOf(FIELD_SEPARATOR);
                final String identity = firstSeparator >= 0 ? body.substring(0, firstSeparator) : body;

                String nonce = null;
                String token = null;
                if (firstSeparator >= 0)
                {
                    final int nonceStart = firstSeparator + FIELD_SEPARATOR.length();
                    final int secondSeparator = body.indexOf(FIELD_SEPARATOR, nonceStart);
                    if (secondSeparator >= 0)
                    {
                        nonce = body.substring(nonceStart, secondSeparator);
                        token = body.substring(secondSeparator + FIELD_SEPARATOR.length());
                    }
                    else
                    {
                        nonce = body.substring(nonceStart);
                    }
                }

                record = new OwnershipRecord(strong, identity, nonce, token);
            }

            return record;
        }
    }
}

