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
package io.aklivity.zilla.specs.binding.mqtt.internal;

import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.mqtt.internal.types.Array32FW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttBinaryFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttExpirySignalFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttPayloadFormatFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttPublishFlags;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttQoS;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttSessionFlags;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttSessionSignalFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttSessionSignalType;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttSessionStateFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttSubscribeFlags;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttTopicFilterFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttUserPropertyFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttWillMessageFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttWillSignalFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.String16FW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.Varuint32FW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttExtensionKind;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttFlushExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttOffsetMetadataFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttOffsetStateFlags;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttPublishBeginExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttPublishDataExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttResetExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttServerCapabilities;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttSessionBeginExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttSessionDataExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttSessionDataKind;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttSubscribeBeginExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttSubscribeDataExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttSubscribeFlushExFW;

public final class MqttFunctions
{
    @Function
    public static byte[] payloadFormat(String format)
    {
        final MqttPayloadFormat mqttPayloadFormat = MqttPayloadFormat.valueOf(format);
        final MqttPayloadFormatFW formatFW = new MqttPayloadFormatFW.Builder().wrap(new UnsafeBuffer(new byte[1]), 0, 1)
            .set(mqttPayloadFormat)
            .build();
        final byte[] array = new byte[formatFW.sizeof()];
        formatFW.buffer().getBytes(formatFW.offset(), array);

        return array;
    }

    @Function
    public static MqttBeginExBuilder beginEx()
    {
        return new MqttBeginExBuilder();
    }

    @Function
    public static MqttBeginExMatcherBuilder matchBeginEx()
    {
        return new MqttBeginExMatcherBuilder();
    }

    @Function
    public static MqttDataExBuilder dataEx()
    {
        return new MqttDataExBuilder();
    }
    @Function
    public static MqttDataExMatcherBuilder matchDataEx()
    {
        return new MqttDataExMatcherBuilder();
    }

    @Function
    public static MqttFlushExBuilder flushEx()
    {
        return new MqttFlushExBuilder();
    }

    @Function
    public static MqttResetExBuilder resetEx()
    {
        return new MqttResetExBuilder();
    }

    @Function
    public static MqttSessionStateBuilder session()
    {
        return new MqttSessionStateBuilder();
    }

    @Function
    public static MqttOffsetMetadataBuilder metadata()
    {
        return new MqttOffsetMetadataBuilder();
    }

    @Function
    public static MqttWillMessageBuilder will()
    {
        return new MqttWillMessageBuilder();
    }

    @Function
    public static MqttSessionSignalBuilder sessionSignal()
    {
        return new MqttSessionSignalBuilder();
    }

    @Function
    public static long timestamp()
    {
        return currentTimeMillis();
    }

    @Function
    public static byte[] randomBytes(
        int length)
    {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++)
        {
            bytes[i] = (byte) ThreadLocalRandom.current().nextInt(0x100);
        }
        return bytes;
    }

    public static final class MqttBeginExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        private final MqttBeginExFW beginExRO = new MqttBeginExFW();

        private final MqttBeginExFW.Builder beginExRW = new MqttBeginExFW.Builder();


        private MqttBeginExBuilder()
        {
            beginExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public MqttBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public MqttSessionBeginExBuilder session()
        {
            beginExRW.kind(MqttExtensionKind.SESSION.value());

            return new MqttSessionBeginExBuilder();
        }

        public MqttSubscribeBeginExBuilder subscribe()
        {
            beginExRW.kind(MqttExtensionKind.SUBSCRIBE.value());

            return new MqttSubscribeBeginExBuilder();
        }

        public MqttPublishBeginExBuilder publish()
        {
            beginExRW.kind(MqttExtensionKind.PUBLISH.value());

            return new MqttPublishBeginExBuilder();
        }

        public byte[] build()
        {
            final MqttBeginExFW beginEx = beginExRO;
            final byte[] array = new byte[beginEx.sizeof()];
            beginEx.buffer().getBytes(beginEx.offset(), array);
            return array;
        }

        public final class MqttSessionBeginExBuilder
        {
            private final MqttSessionBeginExFW.Builder sessionBeginExRW = new MqttSessionBeginExFW.Builder();

            private MqttSessionBeginExBuilder()
            {
                sessionBeginExRW.wrap(writeBuffer, MqttBeginExFW.FIELD_OFFSET_SESSION, writeBuffer.capacity());
            }

            public MqttSessionBeginExBuilder clientId(
                String clientId)
            {
                sessionBeginExRW.clientId(clientId);
                return this;
            }

            public MqttSessionBeginExBuilder expiry(
                int expiry)
            {
                sessionBeginExRW.expiry(expiry);
                return this;
            }

            public MqttSessionBeginExBuilder qosMax(
                int qosMax)
            {
                sessionBeginExRW.qosMax(qosMax);
                return this;
            }

            public MqttSessionBeginExBuilder packetSizeMax(
                int packetSizeMax)
            {
                sessionBeginExRW.packetSizeMax(packetSizeMax);
                return this;
            }

            public MqttSessionBeginExBuilder capabilities(
                String... capabilityNames)
            {
                int capabilities = Arrays.stream(capabilityNames)
                    .mapToInt(flag -> 1 << MqttServerCapabilities.valueOf(flag).value())
                    .reduce(0, (a, b) -> a | b);
                sessionBeginExRW.capabilities(capabilities);
                return this;
            }

            public MqttSessionBeginExBuilder flags(
                String... flagNames)
            {
                int flags = Arrays.stream(flagNames)
                    .mapToInt(flag -> 1 << MqttSessionFlags.valueOf(flag).value())
                    .reduce(0, (a, b) -> a | b);
                sessionBeginExRW.flags(flags);
                return this;
            }

            public MqttBeginExBuilder build()
            {
                final MqttSessionBeginExFW sessionBeginEx = sessionBeginExRW.build();
                beginExRO.wrap(writeBuffer, 0, sessionBeginEx.limit());
                return MqttBeginExBuilder.this;
            }
        }

        public final class MqttSubscribeBeginExBuilder
        {
            private final MqttSubscribeBeginExFW.Builder subscribeBeginExRW = new MqttSubscribeBeginExFW.Builder();

            private MqttSubscribeBeginExBuilder()
            {
                subscribeBeginExRW.wrap(writeBuffer, MqttBeginExFW.FIELD_OFFSET_SUBSCRIBE, writeBuffer.capacity());
            }

            public MqttSubscribeBeginExBuilder clientId(
                String clientId)
            {
                subscribeBeginExRW.clientId(clientId);
                return this;
            }

            public MqttSubscribeBeginExBuilder qos(
                String qosName)
            {
                subscribeBeginExRW.qos(MqttQoS.valueOf(qosName).ordinal());
                return this;
            }

            public MqttSubscribeBeginExBuilder filter(
                String pattern)
            {
                subscribeBeginExRW.filtersItem(f -> f.pattern(pattern));
                return this;
            }

            public MqttSubscribeBeginExBuilder filter(
                String pattern,
                int id)
            {
                subscribeBeginExRW.filtersItem(f -> f.subscriptionId(id).pattern(pattern));
                return this;
            }

            public MqttSubscribeBeginExBuilder filter(
                String pattern,
                int id,
                String qosName,
                String... flags)
            {
                int flagsBitset = Arrays.stream(flags)
                    .mapToInt(f -> 1 << MqttSubscribeFlags.valueOf(f).ordinal())
                    .reduce(0, (a, b) -> a | b);
                int qos = MqttQoS.valueOf(qosName).ordinal();
                subscribeBeginExRW.filtersItem(f -> f.subscriptionId(id).qos(qos).flags(flagsBitset).pattern(pattern));
                return this;
            }

            public MqttBeginExBuilder build()
            {
                final MqttSubscribeBeginExFW subscribeBeginEx = subscribeBeginExRW.build();
                beginExRO.wrap(writeBuffer, 0, subscribeBeginEx.limit());
                return MqttBeginExBuilder.this;
            }
        }

        public final class MqttPublishBeginExBuilder
        {
            private final MqttPublishBeginExFW.Builder publishBeginExRW = new MqttPublishBeginExFW.Builder();

            private MqttPublishBeginExBuilder()
            {
                publishBeginExRW.wrap(writeBuffer, MqttBeginExFW.FIELD_OFFSET_PUBLISH, writeBuffer.capacity());
            }

            public MqttPublishBeginExBuilder clientId(
                String clientId)
            {
                publishBeginExRW.clientId(clientId);
                return this;
            }

            public MqttPublishBeginExBuilder topic(
                String topic)
            {
                publishBeginExRW.topic(topic);
                return this;
            }

            public MqttPublishBeginExBuilder flags(
                String... flagNames)
            {
                int flags = Arrays.stream(flagNames)
                    .mapToInt(flag -> 1 << MqttPublishFlags.valueOf(flag).ordinal())
                    .reduce(0, (a, b) -> a | b);
                publishBeginExRW.flags(flags);
                return this;
            }

            public MqttPublishBeginExBuilder qos(
                int qos)
            {
                publishBeginExRW.qos(qos);
                return this;
            }

            public MqttBeginExBuilder build()
            {
                final MqttPublishBeginExFW publishBeginEx = publishBeginExRW.build();
                beginExRO.wrap(writeBuffer, 0, publishBeginEx.limit());
                return MqttBeginExBuilder.this;
            }
        }
    }

    public static final class MqttDataExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        private final MqttDataExFW dataExRO = new MqttDataExFW();

        private final MqttDataExFW.Builder dataExRW = new MqttDataExFW.Builder();

        private MqttDataExBuilder()
        {
            dataExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public MqttDataExBuilder typeId(
            int typeId)
        {
            dataExRW.typeId(typeId);
            return this;
        }

        public MqttDataExBuilder.MqttSubscribeDataExBuilder subscribe()
        {
            dataExRW.kind(MqttExtensionKind.SUBSCRIBE.value());

            return new MqttDataExBuilder.MqttSubscribeDataExBuilder();
        }

        public MqttDataExBuilder.MqttPublishDataExBuilder publish()
        {
            dataExRW.kind(MqttExtensionKind.PUBLISH.value());

            return new MqttDataExBuilder.MqttPublishDataExBuilder();
        }

        public MqttDataExBuilder.MqttSessionDataExBuilder session()
        {
            dataExRW.kind(MqttExtensionKind.SESSION.value());

            return new MqttDataExBuilder.MqttSessionDataExBuilder();
        }

        public byte[] build()
        {
            final MqttDataExFW dataEx = dataExRO;
            final byte[] array = new byte[dataEx.sizeof()];
            dataEx.buffer().getBytes(dataEx.offset(), array);
            return array;
        }

        public final class MqttSubscribeDataExBuilder
        {
            private final MqttSubscribeDataExFW.Builder subscribeDataExRW = new MqttSubscribeDataExFW.Builder();

            private MqttSubscribeDataExBuilder()
            {
                subscribeDataExRW.wrap(writeBuffer, MqttBeginExFW.FIELD_OFFSET_PUBLISH, writeBuffer.capacity());
            }

            public MqttSubscribeDataExBuilder topic(
                String topic)
            {
                subscribeDataExRW.topic(topic);
                return this;
            }

            public MqttSubscribeDataExBuilder packetId(
                int packetId)
            {
                subscribeDataExRW.packetId(packetId);
                return this;
            }

            public MqttSubscribeDataExBuilder qos(
                String qos)
            {
                subscribeDataExRW.qos(MqttQoS.valueOf(qos).ordinal());
                return this;
            }

            public MqttSubscribeDataExBuilder flags(
                String... flags)
            {
                int subscribeFlags = Arrays.stream(flags)
                    .mapToInt(flag -> 1 << MqttPublishFlags.valueOf(flag).ordinal())
                    .reduce(0, (a, b) -> a | b);

                subscribeDataExRW.flags(subscribeFlags);
                return this;
            }

            public MqttSubscribeDataExBuilder subscriptionId(
                int subscriptionId)
            {
                subscribeDataExRW.subscriptionIdsItem(i -> i.set(subscriptionId));
                return this;
            }

            public MqttSubscribeDataExBuilder expiryInterval(
                int expiryInterval)
            {
                subscribeDataExRW.expiryInterval(expiryInterval);
                return this;
            }

            public MqttSubscribeDataExBuilder contentType(
                String contentType)
            {
                subscribeDataExRW.contentType(contentType);
                return this;
            }

            public MqttSubscribeDataExBuilder format(
                String format)
            {
                subscribeDataExRW.format(p -> p.set(MqttPayloadFormat.valueOf(format)));
                return this;
            }

            public MqttSubscribeDataExBuilder responseTopic(
                String topic)
            {
                subscribeDataExRW.responseTopic(topic);
                return this;
            }

            public MqttSubscribeDataExBuilder correlation(
                String correlation)
            {
                subscribeDataExRW.correlation(c -> c.bytes(b -> b.set(correlation.getBytes(UTF_8))));
                return this;
            }

            public MqttSubscribeDataExBuilder correlationBytes(
                byte[] correlation)
            {
                subscribeDataExRW.correlation(c -> c.bytes(b -> b.set(correlation)));
                return this;
            }

            public MqttSubscribeDataExBuilder userProperty(
                String name,
                String value)
            {
                subscribeDataExRW.propertiesItem(p -> p.key(name).value(value));
                return this;
            }

            public MqttDataExBuilder build()
            {
                final MqttSubscribeDataExFW subscribeDataEx = subscribeDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, subscribeDataEx.limit());
                return MqttDataExBuilder.this;
            }
        }

        public final class MqttPublishDataExBuilder
        {
            private final MqttPublishDataExFW.Builder publishDataExRW = new MqttPublishDataExFW.Builder();

            private MqttPublishDataExBuilder()
            {
                publishDataExRW.wrap(writeBuffer, MqttBeginExFW.FIELD_OFFSET_PUBLISH, writeBuffer.capacity());
            }

            public MqttPublishDataExBuilder qos(
                String qos)
            {
                publishDataExRW.qos(MqttQoS.valueOf(qos).ordinal());
                return this;
            }

            public MqttPublishDataExBuilder flags(
                String... flagNames)
            {
                int flags = Arrays.stream(flagNames)
                    .mapToInt(flag -> 1 << MqttPublishFlags.valueOf(flag).ordinal())
                    .reduce(0, (a, b) -> a | b);
                publishDataExRW.flags(flags);
                return this;
            }

            public MqttPublishDataExBuilder expiryInterval(
                int expiryInterval)
            {
                publishDataExRW.expiryInterval(expiryInterval);
                return this;
            }

            public MqttPublishDataExBuilder contentType(
                String contentType)
            {
                publishDataExRW.contentType(contentType);
                return this;
            }

            public MqttPublishDataExBuilder format(
                String format)
            {
                publishDataExRW.format(p -> p.set(MqttPayloadFormat.valueOf(format)));
                return this;
            }

            public MqttPublishDataExBuilder responseTopic(
                String topic)
            {
                publishDataExRW.responseTopic(topic);
                return this;
            }

            public MqttPublishDataExBuilder correlation(
                String correlation)
            {
                publishDataExRW.correlation(c -> c.bytes(b -> b.set(correlation.getBytes(UTF_8))));
                return this;
            }

            public MqttPublishDataExBuilder correlationBytes(
                byte[] correlation)
            {
                publishDataExRW.correlation(c -> c.bytes(b -> b.set(correlation)));
                return this;
            }

            public MqttPublishDataExBuilder userProperty(
                String name,
                String value)
            {
                publishDataExRW.propertiesItem(p -> p.key(name).value(value));
                return this;
            }

            public MqttDataExBuilder build()
            {
                final MqttPublishDataExFW publishDataEx = publishDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, publishDataEx.limit());
                return MqttDataExBuilder.this;
            }
        }

        public final class MqttSessionDataExBuilder
        {
            private final MqttSessionDataExFW.Builder sessionDataExRW = new MqttSessionDataExFW.Builder();

            private MqttSessionDataExBuilder()
            {
                sessionDataExRW.wrap(writeBuffer, MqttBeginExFW.FIELD_OFFSET_SESSION, writeBuffer.capacity());
            }

            public MqttSessionDataExBuilder kind(
                String kind)
            {
                sessionDataExRW.kind(k -> k.set(MqttSessionDataKind.valueOf(kind)));
                return this;
            }

            public MqttDataExBuilder build()
            {
                final MqttSessionDataExFW sessionDataEx = sessionDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, sessionDataEx.limit());
                return MqttDataExBuilder.this;
            }
        }
    }

    public static final class MqttFlushExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        private final MqttFlushExFW flushExRO = new MqttFlushExFW();

        private final MqttFlushExFW.Builder flushExRW = new MqttFlushExFW.Builder();

        private MqttFlushExBuilder()
        {
            flushExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public MqttFlushExBuilder typeId(
            int typeId)
        {
            flushExRW.typeId(typeId);
            return this;
        }

        public MqttSubscribeFlushExBuilder subscribe()
        {
            flushExRW.kind(MqttExtensionKind.SUBSCRIBE.value());

            return new MqttSubscribeFlushExBuilder();
        }

        public final class MqttSubscribeFlushExBuilder
        {
            private final MqttSubscribeFlushExFW.Builder subscribeFlushExRW = new MqttSubscribeFlushExFW.Builder();

            private MqttSubscribeFlushExBuilder()
            {
                subscribeFlushExRW.wrap(writeBuffer, MqttBeginExFW.FIELD_OFFSET_PUBLISH, writeBuffer.capacity());
            }

            public MqttSubscribeFlushExBuilder packetId(
                int packetId)
            {
                subscribeFlushExRW.packetId(packetId);
                return this;
            }

            public MqttSubscribeFlushExBuilder qos(
                String qos)
            {
                subscribeFlushExRW.qos(MqttQoS.valueOf(qos).ordinal());
                return this;
            }

            public MqttSubscribeFlushExBuilder state(
                String state)
            {
                subscribeFlushExRW.state(MqttOffsetStateFlags.valueOf(state).ordinal());
                return this;
            }

            public MqttSubscribeFlushExBuilder filter(
                String topic,
                int id)
            {
                subscribeFlushExRW.filtersItem(f -> f.subscriptionId(id).pattern(topic));
                return this;
            }

            public MqttSubscribeFlushExBuilder filter(
                String topic,
                int id,
                String qosName,
                String... flagNames)
            {
                int flags = Arrays.stream(flagNames)
                    .mapToInt(f -> 1 << MqttSubscribeFlags.valueOf(f).ordinal())
                    .reduce(0, (a, b) -> a | b);
                int qos = MqttQoS.valueOf(qosName).ordinal();
                subscribeFlushExRW.filtersItem(f -> f.subscriptionId(id).qos(qos).flags(flags).pattern(topic));
                return this;
            }

            public MqttFlushExBuilder build()
            {
                final MqttSubscribeFlushExFW subscribeFlushEx = subscribeFlushExRW.build();
                flushExRO.wrap(writeBuffer, 0, subscribeFlushEx.limit());
                return MqttFlushExBuilder.this;
            }
        }

        public byte[] build()
        {
            final MqttFlushExFW flushEx = flushExRO;
            final byte[] array = new byte[flushEx.sizeof()];
            flushEx.buffer().getBytes(flushEx.offset(), array);
            return array;
        }
    }

    public static final class MqttResetExBuilder
    {
        private final MqttResetExFW.Builder resetExRW;

        private MqttResetExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.resetExRW = new MqttResetExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public MqttResetExBuilder typeId(
            int typeId)
        {
            resetExRW.typeId(typeId);
            return this;
        }

        public MqttResetExBuilder serverRef(
            String serverRef)
        {
            resetExRW.serverRef(serverRef);
            return this;
        }

        public MqttResetExBuilder reasonCode(
            int reasonCode)
        {
            resetExRW.reasonCode(reasonCode);
            return this;
        }

        public byte[] build()
        {
            final MqttResetExFW resetEx = resetExRW.build();
            final byte[] array = new byte[resetEx.sizeof()];
            resetEx.buffer().getBytes(resetEx.offset(), array);
            return array;
        }
    }

    public static final class MqttSessionStateBuilder
    {
        private final MqttSessionStateFW.Builder sessionStateRW = new MqttSessionStateFW.Builder();

        private MqttSessionStateBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            sessionStateRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public MqttSessionStateBuilder subscription(
            String pattern)
        {
            sessionStateRW.subscriptionsItem(f -> f.pattern(pattern));
            return this;
        }

        public MqttSessionStateBuilder subscription(
            String pattern,
            int id)
        {
            sessionStateRW.subscriptionsItem(f -> f.subscriptionId(id).pattern(pattern));
            return this;
        }

        public MqttSessionStateBuilder subscriptionWithReasonCode(
            String pattern,
            int id,
            int reasonCode)
        {
            sessionStateRW.subscriptionsItem(f -> f.subscriptionId(id).reasonCode(reasonCode).pattern(pattern));
            return this;
        }

        public MqttSessionStateBuilder subscription(
            String pattern,
            int id,
            String qosName,
            String... flagNames)
        {
            int flags = Arrays.stream(flagNames)
                .mapToInt(f -> 1 << MqttSubscribeFlags.valueOf(f).ordinal())
                .reduce(0, (a, b) -> a | b);
            int qos = MqttQoS.valueOf(qosName).ordinal();
            sessionStateRW.subscriptionsItem(f -> f.subscriptionId(id).qos(qos).flags(flags).pattern(pattern));
            return this;
        }

        public byte[] build()
        {
            final MqttSessionStateFW sessionState = sessionStateRW.build();
            final byte[] array = new byte[sessionState.sizeof()];
            sessionState.buffer().getBytes(sessionState.offset(), array);
            return array;
        }
    }

    public static final class MqttOffsetMetadataBuilder
    {
        private final MqttOffsetMetadataFW.Builder offsetMetadataRW = new MqttOffsetMetadataFW.Builder();

        private MqttOffsetMetadataBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            offsetMetadataRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public MqttOffsetMetadataBuilder metadata(
            int packetId)
        {
            offsetMetadataRW.metadataItem(f -> f.packetId(packetId));
            return this;
        }

        public String build()
        {
            final MqttOffsetMetadataFW offsetMetadata = offsetMetadataRW.build();
            final byte[] array = new byte[offsetMetadata.sizeof()];
            offsetMetadata.buffer().getBytes(offsetMetadata.offset(), array);
            return BitUtil.toHex(array);
        }
    }

    public static final class MqttWillMessageBuilder
    {
        private final MqttWillMessageFW.Builder willMessageRW = new MqttWillMessageFW.Builder();
        private final MqttWillMessageFW willMessageRO = new MqttWillMessageFW();

        private MqttWillMessageBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            willMessageRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public MqttWillMessageBuilder topic(
            String topic)
        {
            willMessageRW.topic(topic);
            return this;
        }

        public MqttWillMessageBuilder delay(
            int delay)
        {
            willMessageRW.delay(delay);
            return this;
        }

        public MqttWillMessageBuilder qos(
            String qos)
        {
            willMessageRW.qos(MqttQoS.valueOf(qos).ordinal());
            return this;
        }

        public MqttWillMessageBuilder flags(
            String... flagNames)
        {
            int flags = Arrays.stream(flagNames)
                .mapToInt(flag -> 1 << MqttPublishFlags.valueOf(flag).ordinal())
                .reduce(0, (a, b) -> a | b);
            willMessageRW.flags(flags);
            return this;
        }

        public MqttWillMessageBuilder expiryInterval(
            int expiryInterval)
        {
            willMessageRW.expiryInterval(expiryInterval);
            return this;
        }

        public MqttWillMessageBuilder contentType(
            String contentType)
        {
            willMessageRW.contentType(contentType);
            return this;
        }

        public MqttWillMessageBuilder format(
            String format)
        {
            willMessageRW.format(p -> p.set(MqttPayloadFormat.valueOf(format)));
            return this;
        }

        public MqttWillMessageBuilder responseTopic(
            String topic)
        {
            willMessageRW.responseTopic(topic);
            return this;
        }

        public MqttWillMessageBuilder lifetimeId(
            String lifetimeId)
        {
            willMessageRW.lifetimeId(lifetimeId);
            return this;
        }

        public MqttWillMessageBuilder willId(
            String willId)
        {
            willMessageRW.willId(willId);
            return this;
        }

        public MqttWillMessageBuilder correlation(
            String correlation)
        {
            willMessageRW.correlation(c -> c.bytes(b -> b.set(correlation.getBytes(UTF_8))));
            return this;
        }

        public MqttWillMessageBuilder correlationBytes(
            byte[] correlation)
        {
            willMessageRW.correlation(c -> c.bytes(b -> b.set(correlation)));
            return this;
        }

        public MqttWillMessageBuilder userProperty(
            String name,
            String value)
        {
            willMessageRW.propertiesItem(p -> p.key(name).value(value));
            return this;
        }

        public MqttWillMessageBuilder payload(
            String payload)
        {
            willMessageRW.payload(c -> c.bytes(b -> b.set(payload.getBytes(UTF_8))));
            return this;
        }

        public MqttWillMessageBuilder payloadBytes(
            byte[] payload)
        {
            willMessageRW.payload(c -> c.bytes(b -> b.set(payload)));
            return this;
        }

        public byte[] build()
        {
            final MqttWillMessageFW willMessage = willMessageRW.build();
            final byte[] array = new byte[willMessage.sizeof()];
            willMessage.buffer().getBytes(willMessage.offset(), array);
            return array;
        }
    }

    public static final class MqttSessionSignalBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        private final MqttSessionSignalFW signalRO = new MqttSessionSignalFW();

        private final MqttSessionSignalFW.Builder signalRW = new MqttSessionSignalFW.Builder();


        private MqttSessionSignalBuilder()
        {
            signalRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public MqttSessionExpirySignalBuilder expiry()
        {
            signalRW.kind(MqttSessionSignalType.EXPIRY.value());

            return new MqttSessionExpirySignalBuilder();
        }

        public MqttSessionWillSignalBuilder will()
        {
            signalRW.kind(MqttSessionSignalType.WILL.value());

            return new MqttSessionWillSignalBuilder();
        }

        public byte[] build()
        {
            final MqttSessionSignalFW signal = signalRO;
            final byte[] array = new byte[signal.sizeof()];
            signal.buffer().getBytes(signal.offset(), array);
            return array;
        }


        public final class MqttSessionWillSignalBuilder
        {
            private final MqttWillSignalFW.Builder willSignalRW = new MqttWillSignalFW.Builder();

            private MqttSessionWillSignalBuilder()
            {
                willSignalRW.wrap(writeBuffer, MqttSessionSignalFW.FIELD_OFFSET_WILL, writeBuffer.capacity());
            }

            public MqttSessionWillSignalBuilder clientId(
                String clientId)
            {
                willSignalRW.clientId(clientId);
                return this;
            }

            public MqttSessionWillSignalBuilder delay(
                int delay)
            {
                willSignalRW.delay(delay);
                return this;
            }

            public MqttSessionWillSignalBuilder deliverAt(
                long deliverAt)
            {
                willSignalRW.deliverAt(deliverAt);
                return this;
            }

            public MqttSessionWillSignalBuilder lifetimeId(
                String lifetimeId)
            {
                willSignalRW.lifetimeId(lifetimeId);
                return this;
            }

            public MqttSessionWillSignalBuilder willId(
                String willId)
            {
                willSignalRW.willId(willId);
                return this;
            }

            public MqttSessionWillSignalBuilder instanceId(
                String instanceId)
            {
                willSignalRW.instanceId(instanceId);
                return this;
            }

            public MqttSessionSignalBuilder build()
            {
                final MqttWillSignalFW willSignal = willSignalRW.build();
                signalRO.wrap(writeBuffer, 0, willSignal.limit());
                return MqttSessionSignalBuilder.this;
            }
        }

        public final class MqttSessionExpirySignalBuilder
        {
            private final MqttExpirySignalFW.Builder expirySignalRW = new MqttExpirySignalFW.Builder();

            private MqttSessionExpirySignalBuilder()
            {
                expirySignalRW.wrap(writeBuffer, MqttSessionSignalFW.FIELD_OFFSET_EXPIRY, writeBuffer.capacity());
            }

            public MqttSessionExpirySignalBuilder clientId(
                String clientId)
            {
                expirySignalRW.clientId(clientId);
                return this;
            }

            public MqttSessionExpirySignalBuilder delay(
                int delay)
            {
                expirySignalRW.delay(delay);
                return this;
            }

            public MqttSessionExpirySignalBuilder expireAt(
                long expireAt)
            {
                expirySignalRW.expireAt(expireAt);
                return this;
            }

            public MqttSessionExpirySignalBuilder instanceId(
                String instanceId)
            {
                expirySignalRW.instanceId(instanceId);
                return this;
            }

            public MqttSessionSignalBuilder build()
            {
                final MqttExpirySignalFW expirySignal = expirySignalRW.build();
                signalRO.wrap(writeBuffer, 0, expirySignal.limit());
                return MqttSessionSignalBuilder.this;
            }
        }
    }


    public static final class MqttBeginExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final MqttBeginExFW beginExRO = new MqttBeginExFW();

        private Integer typeId;
        private Integer kind;
        private Predicate<MqttBeginExFW> caseMatcher;

        public MqttPublishBeginExMatcherBuilder publish()
        {
            final MqttPublishBeginExMatcherBuilder matcherBuilder = new MqttPublishBeginExMatcherBuilder();

            this.kind = MqttExtensionKind.PUBLISH.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public MqttSubscribeBeginExMatcherBuilder subscribe()
        {
            final MqttSubscribeBeginExMatcherBuilder matcherBuilder = new MqttSubscribeBeginExMatcherBuilder();

            this.kind = MqttExtensionKind.SUBSCRIBE.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public MqttSessionBeginExMatcherBuilder session()
        {
            final MqttSessionBeginExMatcherBuilder matcherBuilder = new MqttSessionBeginExMatcherBuilder();

            this.kind = MqttExtensionKind.SESSION.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public MqttBeginExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null || kind != null ? this::match : buf -> null;
        }

        private MqttBeginExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final MqttBeginExFW beginEx = beginExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (beginEx != null &&
                matchTypeId(beginEx) &&
                matchKind(beginEx) &&
                matchCase(beginEx))
            {
                byteBuf.position(byteBuf.position() + beginEx.sizeof());
                return beginEx;
            }

            throw new Exception(beginEx.toString());
        }

        private boolean matchTypeId(
            final MqttBeginExFW beginEx)
        {
            return typeId == null || typeId == beginEx.typeId();
        }

        private boolean matchKind(
            final MqttBeginExFW beginEx)
        {
            return kind == null || kind == beginEx.kind();
        }

        private boolean matchCase(
            final MqttBeginExFW beginEx) throws Exception
        {
            return caseMatcher == null || caseMatcher.test(beginEx);
        }

        public final class MqttPublishBeginExMatcherBuilder
        {
            private String16FW clientId;
            private String16FW topic;
            private Integer flags;
            private Integer qos;

            private MqttPublishBeginExMatcherBuilder()
            {
            }
            public MqttPublishBeginExMatcherBuilder clientId(
                String clientId)
            {
                this.clientId = new String16FW(clientId);
                return this;
            }

            public MqttPublishBeginExMatcherBuilder topic(
                String topic)
            {
                this.topic = new String16FW(topic);
                return this;
            }

            public MqttPublishBeginExMatcherBuilder flags(
                String... flags)
            {
                this.flags = Arrays.stream(flags)
                    .mapToInt(flag -> 1 << MqttPublishFlags.valueOf(flag).ordinal())
                    .reduce(0, (a, b) -> a | b);
                return this;
            }

            public MqttPublishBeginExMatcherBuilder qos(
                int qos)
            {
                this.qos = qos;
                return this;
            }

            public MqttBeginExMatcherBuilder build()
            {
                return MqttBeginExMatcherBuilder.this;
            }

            private boolean match(
                MqttBeginExFW beginEx)
            {
                final MqttPublishBeginExFW publishBeginEx = beginEx.publish();
                return matchClientId(publishBeginEx) &&
                    matchTopic(publishBeginEx) &&
                    matchFlags(publishBeginEx) &&
                    matchQos(publishBeginEx);
            }

            private boolean matchClientId(
                final MqttPublishBeginExFW publishBeginEx)
            {
                return clientId == null || clientId.equals(publishBeginEx.clientId());
            }

            private boolean matchTopic(
                final MqttPublishBeginExFW publishBeginEx)
            {
                return topic == null || topic.equals(publishBeginEx.topic());
            }

            private boolean matchFlags(
                final MqttPublishBeginExFW publishBeginEx)
            {
                return flags == null || flags == publishBeginEx.flags();
            }

            private boolean matchQos(
                final MqttPublishBeginExFW publishBeginEx)
            {
                return qos == null || qos == publishBeginEx.qos();
            }
        }

        public final class MqttSubscribeBeginExMatcherBuilder
        {
            private String16FW clientId;
            private Integer qos;
            private Array32FW.Builder<MqttTopicFilterFW.Builder, MqttTopicFilterFW> filters;

            private MqttSubscribeBeginExMatcherBuilder()
            {
            }
            public MqttSubscribeBeginExMatcherBuilder clientId(
                String clientId)
            {
                this.clientId = new String16FW(clientId);
                return this;
            }

            public MqttSubscribeBeginExMatcherBuilder qos(
                String qosName)
            {
                this.qos = MqttQoS.valueOf(qosName).ordinal();
                return this;
            }

            public MqttSubscribeBeginExMatcherBuilder filter(
                String pattern)
            {
                if (filters == null)
                {
                    this.filters = new Array32FW.Builder<>(new MqttTopicFilterFW.Builder(), new MqttTopicFilterFW())
                        .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }
                filters.item(i -> i
                    .pattern(pattern));
                return this;
            }

            public MqttSubscribeBeginExMatcherBuilder filter(
                String pattern,
                int id)
            {
                if (filters == null)
                {
                    this.filters = new Array32FW.Builder<>(new MqttTopicFilterFW.Builder(), new MqttTopicFilterFW())
                        .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }
                filters.item(i -> i
                    .subscriptionId(id)
                    .pattern(pattern));
                return this;
            }

            public MqttSubscribeBeginExMatcherBuilder filter(
                String pattern,
                int id,
                String qosName,
                String... flagNames)
            {
                if (filters == null)
                {
                    this.filters = new Array32FW.Builder<>(new MqttTopicFilterFW.Builder(), new MqttTopicFilterFW())
                        .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }

                int flags = Arrays.stream(flagNames)
                    .mapToInt(f -> 1 << MqttSubscribeFlags.valueOf(f).ordinal())
                    .reduce(0, (a, b) -> a | b);
                int qos = MqttQoS.valueOf(qosName).ordinal();
                filters.item(i -> i
                    .subscriptionId(id)
                    .qos(qos)
                    .flags(flags)
                    .pattern(pattern));
                return this;
            }

            public MqttBeginExMatcherBuilder build()
            {
                return MqttBeginExMatcherBuilder.this;
            }

            private boolean match(
                MqttBeginExFW beginEx)
            {
                final MqttSubscribeBeginExFW subscribeBeginEx = beginEx.subscribe();
                return matchClientId(subscribeBeginEx) &&
                    matchQos(subscribeBeginEx) &&
                    matchFilters(subscribeBeginEx);
            }

            private boolean matchClientId(
                final MqttSubscribeBeginExFW subscribeBeginEx)
            {
                return clientId == null || clientId.equals(subscribeBeginEx.clientId());
            }

            private boolean matchQos(
                final MqttSubscribeBeginExFW subscribeBeginEx)
            {
                return qos == null || qos.equals(subscribeBeginEx.qos());
            }

            private boolean matchFilters(
                final MqttSubscribeBeginExFW subscribeBeginEx)
            {
                return filters == null || filters.build().equals(subscribeBeginEx.filters());
            }
        }

        public final class MqttSessionBeginExMatcherBuilder
        {
            private String16FW clientId;
            private Integer expiry;
            private Integer flags;
            private Integer capabilities;
            private Integer qosMax;
            private Integer packetSizeMax;

            private MqttSessionBeginExMatcherBuilder()
            {
            }

            public MqttSessionBeginExMatcherBuilder clientId(
                String clientId)
            {
                this.clientId = new String16FW(clientId);
                return this;
            }

            public MqttSessionBeginExMatcherBuilder expiry(
                int expiry)
            {
                this.expiry = expiry;
                return this;
            }

            public MqttSessionBeginExMatcherBuilder qosMax(
                int qosMax)
            {
                this.qosMax = qosMax;
                return this;
            }

            public MqttSessionBeginExMatcherBuilder capabilities(
                String... capabilityNames)
            {
                this.capabilities = Arrays.stream(capabilityNames)
                    .mapToInt(flag -> 1 << MqttServerCapabilities.valueOf(flag).value())
                    .reduce(0, (a, b) -> a | b);
                return this;
            }

            public MqttSessionBeginExMatcherBuilder packetSizeMax(
                int packetSizeMax)
            {
                this.packetSizeMax = packetSizeMax;
                return this;
            }

            public MqttSessionBeginExMatcherBuilder flags(
                String... flagNames)
            {
                this.flags = Arrays.stream(flagNames)
                    .mapToInt(flag -> 1 << MqttSessionFlags.valueOf(flag).value())
                    .reduce(0, (a, b) -> a | b);
                return this;
            }

            public MqttBeginExMatcherBuilder build()
            {
                return MqttBeginExMatcherBuilder.this;
            }

            private boolean match(
                MqttBeginExFW beginEx)
            {
                final MqttSessionBeginExFW sessionBeginEx = beginEx.session();
                return matchFlags(sessionBeginEx) &&
                    matchClientId(sessionBeginEx) &&
                    matchExpiry(sessionBeginEx) &&
                    matchQosMax(sessionBeginEx) &&
                    matchPacketSizeMax(sessionBeginEx) &&
                    matchCapabilities(sessionBeginEx);
            }

            private boolean matchClientId(
                final MqttSessionBeginExFW sessionBeginEx)
            {
                return clientId == null || clientId.equals(sessionBeginEx.clientId());
            }

            private boolean matchQosMax(
                final MqttSessionBeginExFW sessionBeginEx)
            {
                return qosMax == null || qosMax == sessionBeginEx.qosMax();
            }

            private boolean matchPacketSizeMax(
                final MqttSessionBeginExFW sessionBeginEx)
            {
                return packetSizeMax == null || packetSizeMax == sessionBeginEx.packetSizeMax();
            }

            private boolean matchCapabilities(
                final MqttSessionBeginExFW sessionBeginEx)
            {
                return capabilities == null || capabilities == sessionBeginEx.capabilities();
            }

            private boolean matchExpiry(
                final MqttSessionBeginExFW sessionBeginEx)
            {
                return expiry == null || expiry == sessionBeginEx.expiry();
            }

            private boolean matchFlags(
                final MqttSessionBeginExFW sessionBeginEx)
            {
                return flags == null || flags == sessionBeginEx.flags();
            }
        }
    }

    public static final class MqttDataExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final MqttDataExFW dataExRo = new MqttDataExFW();

        private Integer typeId;
        private Integer kind;
        private Predicate<MqttDataExFW> caseMatcher;

        public MqttSubscribeDataExMatcherBuilder subscribe()
        {
            final MqttSubscribeDataExMatcherBuilder matcherBuilder = new MqttSubscribeDataExMatcherBuilder();

            this.kind = MqttExtensionKind.SUBSCRIBE.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public MqttPublishDataExMatcherBuilder publish()
        {
            final MqttPublishDataExMatcherBuilder matcherBuilder = new MqttPublishDataExMatcherBuilder();

            this.kind = MqttExtensionKind.PUBLISH.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public MqttDataExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null || kind != null ? this::match : buf -> null;
        }

        private MqttDataExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final MqttDataExFW dataEx = dataExRo.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (dataEx != null &&
                matchTypeId(dataEx) &&
                matchKind(dataEx) &&
                matchCase(dataEx))
            {
                byteBuf.position(byteBuf.position() + dataEx.sizeof());
                return dataEx;
            }

            throw new Exception(dataEx.toString());
        }

        private boolean matchTypeId(
            final MqttDataExFW dataEx)
        {
            return typeId == null || typeId == dataEx.typeId();
        }

        private boolean matchKind(
            final MqttDataExFW dataEx)
        {
            return kind == null || kind == dataEx.kind();
        }

        private boolean matchCase(
            final MqttDataExFW dataEx) throws Exception
        {
            return caseMatcher == null || caseMatcher.test(dataEx);
        }

        public final class MqttSubscribeDataExMatcherBuilder
        {
            private MqttBinaryFW.Builder correlationRW;
            private final DirectBuffer correlationRO = new UnsafeBuffer(0, 0);
            private String16FW topic;
            private Integer packetId;
            private Integer qos;
            private Integer flags;
            private Integer expiryInterval = -1;
            private String16FW contentType;
            private MqttPayloadFormatFW format;
            private String16FW responseTopic;
            private Array32FW.Builder<MqttUserPropertyFW.Builder, MqttUserPropertyFW> userPropertiesRW;
            private Array32FW.Builder<Varuint32FW.Builder, Varuint32FW> subscriptionIdsRW;

            private MqttSubscribeDataExMatcherBuilder()
            {
            }

            public MqttSubscribeDataExMatcherBuilder topic(
                String topic)
            {
                this.topic = new String16FW(topic);
                return this;
            }

            public MqttSubscribeDataExMatcherBuilder packetId(
                int packetId)
            {
                this.packetId = packetId;
                return this;
            }

            public MqttSubscribeDataExMatcherBuilder qos(
                String qos)
            {
                this.qos = MqttQoS.valueOf(qos).ordinal();
                return this;
            }


            public MqttSubscribeDataExMatcherBuilder flags(
                String... flagNames)
            {
                int flags = Arrays.stream(flagNames)
                    .mapToInt(flag -> 1 << MqttPublishFlags.valueOf(flag).ordinal())
                    .reduce(0, (a, b) -> a | b);
                this.flags = flags;
                return this;
            }

            public MqttSubscribeDataExMatcherBuilder subscriptionId(
                int subscriptionId)
            {
                if (subscriptionIdsRW == null)
                {
                    this.subscriptionIdsRW =
                        new Array32FW.Builder<>(new Varuint32FW.Builder(), new Varuint32FW())
                            .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }
                subscriptionIdsRW.item(p -> p.set(subscriptionId));
                return this;
            }

            public MqttSubscribeDataExMatcherBuilder expiryInterval(
                int expiryInterval)
            {
                this.expiryInterval = expiryInterval;
                return this;
            }

            public MqttSubscribeDataExMatcherBuilder contentType(
                String contentType)
            {
                this.contentType = new String16FW(contentType);
                return this;
            }

            public MqttSubscribeDataExMatcherBuilder format(
                String format)
            {
                MqttPayloadFormatFW.Builder builder =
                    new MqttPayloadFormatFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                this.format = builder.set(MqttPayloadFormat.valueOf(format)).build();
                return this;
            }

            public MqttSubscribeDataExMatcherBuilder responseTopic(
                String topic)
            {
                this.responseTopic = new String16FW(topic);
                return this;
            }

            public MqttSubscribeDataExMatcherBuilder correlation(
                String correlation)
            {
                assert correlationRW == null;
                correlationRW = new MqttBinaryFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                correlationRO.wrap(correlation.getBytes(UTF_8));
                correlationRW.bytes(correlationRO, 0, correlationRO.capacity());

                return this;
            }

            public MqttSubscribeDataExMatcherBuilder correlationBytes(
                byte[] correlation)
            {
                assert correlationRW == null;
                correlationRW = new MqttBinaryFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                correlationRO.wrap(correlation);
                correlationRW.bytes(correlationRO, 0, correlationRO.capacity());

                return this;
            }

            public MqttSubscribeDataExMatcherBuilder userProperty(
                String name,
                String value)
            {
                if (userPropertiesRW == null)
                {
                    this.userPropertiesRW =
                        new Array32FW.Builder<>(new MqttUserPropertyFW.Builder(), new MqttUserPropertyFW())
                            .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }
                userPropertiesRW.item(p -> p.key(name).value(value));
                return this;
            }

            public MqttDataExMatcherBuilder build()
            {
                return MqttDataExMatcherBuilder.this;
            }

            private boolean match(
                MqttDataExFW dataEx)
            {
                final MqttSubscribeDataExFW subscribeDataEx = dataEx.subscribe();
                return matchTopic(subscribeDataEx) &&
                    matchPacketId(subscribeDataEx) &&
                    matchQos(subscribeDataEx) &&
                    matchFlags(subscribeDataEx) &&
                    matchSubscriptionIds(subscribeDataEx) &&
                    matchExpiryInterval(subscribeDataEx) &&
                    matchContentType(subscribeDataEx) &&
                    matchFormat(subscribeDataEx) &&
                    matchResponseTopic(subscribeDataEx) &&
                    matchCorrelation(subscribeDataEx) &&
                    matchUserProperties(subscribeDataEx);
            }

            private boolean matchTopic(
                final MqttSubscribeDataExFW data)
            {
                return topic == null || topic.equals(data.topic());
            }

            private boolean matchPacketId(
                final MqttSubscribeDataExFW data)
            {
                return packetId == null || packetId == data.packetId();
            }

            private boolean matchQos(
                final MqttSubscribeDataExFW data)
            {
                return qos == null || qos == data.qos();
            }

            private boolean matchFlags(
                final MqttSubscribeDataExFW data)
            {
                return flags == null || flags == data.flags();
            }

            private boolean matchSubscriptionIds(
                final MqttSubscribeDataExFW data)
            {
                return subscriptionIdsRW == null || subscriptionIdsRW.build().equals(data.subscriptionIds());
            }

            private boolean matchExpiryInterval(
                final MqttSubscribeDataExFW data)
            {
                return expiryInterval == null || expiryInterval == data.expiryInterval();
            }

            private boolean matchContentType(
                final MqttSubscribeDataExFW data)
            {
                return contentType == null || contentType.equals(data.contentType());
            }

            private boolean matchFormat(
                final MqttSubscribeDataExFW data)
            {
                return format == null || format.equals(data.format());
            }

            private boolean matchResponseTopic(
                final MqttSubscribeDataExFW data)
            {
                return responseTopic == null || responseTopic.equals(data.responseTopic());
            }

            private boolean matchCorrelation(
                final MqttSubscribeDataExFW data)
            {
                return correlationRW == null || correlationRW.build().equals(data.correlation());
            }

            private boolean matchUserProperties(
                final MqttSubscribeDataExFW data)
            {
                return userPropertiesRW == null || userPropertiesRW.build().equals(data.properties());
            }
        }

        public final class MqttPublishDataExMatcherBuilder
        {
            private MqttBinaryFW.Builder correlationRW;
            private final DirectBuffer correlationRO = new UnsafeBuffer(0, 0);
            private Integer qos;
            private Integer flags;
            private Integer expiryInterval = -1;
            private String16FW contentType;
            private MqttPayloadFormatFW format;
            private String16FW responseTopic;
            private Array32FW.Builder<MqttUserPropertyFW.Builder, MqttUserPropertyFW> userPropertiesRW;

            private MqttPublishDataExMatcherBuilder()
            {
            }

            public MqttPublishDataExMatcherBuilder qos(
                String qos)
            {
                this.qos = MqttQoS.valueOf(qos).ordinal();
                return this;
            }

            public MqttPublishDataExMatcherBuilder flags(
                String... flags)
            {
                this.flags = Arrays.stream(flags)
                    .mapToInt(flag -> 1 << MqttPublishFlags.valueOf(flag).ordinal())
                    .reduce(0, (a, b) -> a | b);
                return this;
            }

            public MqttPublishDataExMatcherBuilder expiryInterval(
                int expiryInterval)
            {
                this.expiryInterval = expiryInterval;
                return this;
            }

            public MqttPublishDataExMatcherBuilder contentType(
                String contentType)
            {
                this.contentType = new String16FW(contentType);
                return this;
            }

            public MqttPublishDataExMatcherBuilder format(
                String format)
            {
                MqttPayloadFormatFW.Builder builder =
                    new MqttPayloadFormatFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                this.format = builder.set(MqttPayloadFormat.valueOf(format)).build();
                return this;
            }

            public MqttPublishDataExMatcherBuilder responseTopic(
                String topic)
            {
                this.responseTopic = new String16FW(topic);
                return this;
            }

            public MqttPublishDataExMatcherBuilder correlation(
                String correlation)
            {
                assert correlationRW == null;
                correlationRW = new MqttBinaryFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                correlationRO.wrap(correlation.getBytes(UTF_8));
                correlationRW.bytes(correlationRO, 0, correlationRO.capacity());

                return this;
            }

            public MqttPublishDataExMatcherBuilder correlationBytes(
                byte[] correlation)
            {
                assert correlationRW == null;
                correlationRW = new MqttBinaryFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                correlationRO.wrap(correlation);
                correlationRW.bytes(correlationRO, 0, correlationRO.capacity());

                return this;
            }

            public MqttPublishDataExMatcherBuilder userProperty(
                String name,
                String value)
            {
                if (userPropertiesRW == null)
                {
                    this.userPropertiesRW =
                        new Array32FW.Builder<>(new MqttUserPropertyFW.Builder(), new MqttUserPropertyFW())
                            .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }
                userPropertiesRW.item(p -> p.key(name).value(value));
                return this;
            }

            public MqttDataExMatcherBuilder build()
            {
                return MqttDataExMatcherBuilder.this;
            }

            private boolean match(
                MqttDataExFW dataEx)
            {
                final MqttPublishDataExFW publishDataEx = dataEx.publish();
                return matchQos(publishDataEx) &&
                    matchFlags(publishDataEx) &&
                    matchExpiryInterval(publishDataEx) &&
                    matchContentType(publishDataEx) &&
                    matchFormat(publishDataEx) &&
                    matchResponseTopic(publishDataEx) &&
                    matchCorrelation(publishDataEx) &&
                    matchUserProperties(publishDataEx);
            }

            private boolean matchQos(
                final MqttPublishDataExFW data)
            {
                return qos == null || qos == data.qos();
            }

            private boolean matchFlags(
                final MqttPublishDataExFW data)
            {
                return flags == null || flags == data.flags();
            }

            private boolean matchExpiryInterval(
                final MqttPublishDataExFW data)
            {
                return expiryInterval == null || expiryInterval == data.expiryInterval();
            }

            private boolean matchContentType(
                final MqttPublishDataExFW data)
            {
                return contentType == null || contentType.equals(data.contentType());
            }

            private boolean matchFormat(
                final MqttPublishDataExFW data)
            {
                return format == null || format.equals(data.format());
            }

            private boolean matchResponseTopic(
                final MqttPublishDataExFW data)
            {
                return responseTopic == null || responseTopic.equals(data.responseTopic());
            }

            private boolean matchCorrelation(
                final MqttPublishDataExFW data)
            {
                return correlationRW == null || correlationRW.build().equals(data.correlation());
            }

            private boolean matchUserProperties(
                final MqttPublishDataExFW data)
            {
                return userPropertiesRW == null || userPropertiesRW.build().equals(data.properties());
            }
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(MqttFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "mqtt";
        }
    }

    private MqttFunctions()
    {
        /* utility */
    }
}
