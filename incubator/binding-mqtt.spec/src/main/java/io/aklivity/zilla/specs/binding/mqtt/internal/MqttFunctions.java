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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.mqtt.internal.types.Array32FW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttBinaryFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttEndReasonCode;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttMessageFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttPayloadFormatFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttPublishFlags;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttQoS;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttSessionStateFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttSubscribeFlags;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttTopicFilterFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttUserPropertyFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.String16FW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.Varuint32FW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttEndExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttExtensionKind;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttFlushExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttPublishBeginExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttPublishDataExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttSessionBeginExFW;
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
    public static MqttEndExBuilder endEx()
    {
        return new MqttEndExBuilder();
    }

    @Function
    public static MqttSessionStateBuilder session()
    {
        return new MqttSessionStateBuilder();
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

            public MqttWillMessageBuilder will()
            {
                return new MqttWillMessageBuilder(this);
            }

            public MqttBeginExBuilder build()
            {
                final MqttSessionBeginExFW subscribeBeginEx = sessionBeginExRW.build();
                beginExRO.wrap(writeBuffer, 0, subscribeBeginEx.limit());
                return MqttBeginExBuilder.this;
            }

            private void willMessage(
                MqttMessageFW willMessage)
            {
                sessionBeginExRW.will(willMessage);
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

            public MqttPublishDataExBuilder topic(
                String topic)
            {
                publishDataExRW.topic(topic);
                return this;
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
                final MqttPublishDataExFW publishBeginEx = publishDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, publishBeginEx.limit());
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

    public static final class MqttEndExBuilder
    {
        private final MqttEndExFW.Builder endExRW;

        private MqttEndExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.endExRW = new MqttEndExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public MqttEndExBuilder typeId(
            int typeId)
        {
            endExRW.typeId(typeId);
            return this;
        }

        public MqttEndExBuilder reason(
            String reason)
        {
            endExRW.reasonCode(r -> r.set(MqttEndReasonCode.valueOf(reason)));
            return this;
        }

        public byte[] build()
        {
            final MqttEndExFW endEx = endExRW.build();
            final byte[] array = new byte[endEx.sizeof()];
            endEx.buffer().getBytes(endEx.offset(), array);
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

        public MqttSessionStateBuilder clientId(
            String clientId)
        {
            sessionStateRW.clientId(clientId);
            return this;
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

    public static final class MqttWillMessageBuilder
    {
        private final MqttMessageFW.Builder willMessageRW = new MqttMessageFW.Builder();
        private final MqttBeginExBuilder.MqttSessionBeginExBuilder beginExBuilder;

        private MqttWillMessageBuilder(MqttBeginExBuilder.MqttSessionBeginExBuilder beginExBuilder)
        {
            this.beginExBuilder = beginExBuilder;
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

        public MqttBeginExBuilder.MqttSessionBeginExBuilder build()
        {
            beginExBuilder.willMessage(willMessageRW.build());
            return beginExBuilder;
        }
    }

    public static final class MqttBeginExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final MqttBeginExFW beginExRO = new MqttBeginExFW();

        private Integer typeId;
        private Integer kind;
        private Predicate<MqttBeginExFW> caseMatcher;

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

        public final class MqttSubscribeBeginExMatcherBuilder
        {
            private String16FW clientId;
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
                    matchFilters(subscribeBeginEx);
            }

            private boolean matchClientId(
                final MqttSubscribeBeginExFW subscribeBeginEx)
            {
                return clientId == null || clientId.equals(subscribeBeginEx.clientId());
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
            private MqttWillMessageMatcherBuilder willMessageMatcher;

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

            public MqttWillMessageMatcherBuilder will()
            {
                this.willMessageMatcher = new MqttWillMessageMatcherBuilder();
                return willMessageMatcher;
            }

            public MqttBeginExMatcherBuilder build()
            {
                return MqttBeginExMatcherBuilder.this;
            }

            private boolean match(
                MqttBeginExFW beginEx)
            {
                final MqttSessionBeginExFW sessionBeginEx = beginEx.session();
                final MqttMessageFW willMessage = beginEx.session().will();
                return matchClientId(sessionBeginEx) &&
                    matchExpiry(sessionBeginEx) &&
                    (willMessageMatcher == null || willMessageMatcher.match(willMessage));
            }

            private boolean matchClientId(
                final MqttSessionBeginExFW sessionBeginEx)
            {
                return clientId == null || clientId.equals(sessionBeginEx.clientId());
            }

            private boolean matchExpiry(
                final MqttSessionBeginExFW sessionBeginEx)
            {
                return expiry == null || expiry == sessionBeginEx.expiry();
            }

            public final class MqttWillMessageMatcherBuilder
            {
                private MqttBinaryFW.Builder correlationRW;
                private final DirectBuffer correlationRO = new UnsafeBuffer(0, 0);
                private MqttBinaryFW.Builder payloadRW;
                private final DirectBuffer payloadRO = new UnsafeBuffer(0, 0);
                private String16FW topic;
                private Integer delay;
                private Integer qos;
                private Integer flags;
                private Integer expiryInterval = -1;
                private String16FW contentType;
                private MqttPayloadFormatFW format;
                private String16FW responseTopic;
                private Array32FW.Builder<MqttUserPropertyFW.Builder, MqttUserPropertyFW> userPropertiesRW;

                private MqttWillMessageMatcherBuilder()
                {
                }

                public MqttWillMessageMatcherBuilder topic(
                    String topic)
                {
                    this.topic = new String16FW(topic);
                    return this;
                }

                public MqttWillMessageMatcherBuilder delay(
                    int delay)
                {
                    this.delay = delay;
                    return this;
                }

                public MqttWillMessageMatcherBuilder qos(
                    String qos)
                {
                    this.qos = MqttQoS.valueOf(qos).ordinal();
                    return this;
                }

                public MqttWillMessageMatcherBuilder flags(
                    String... flagNames)
                {
                    int flags = Arrays.stream(flagNames)
                        .mapToInt(flag -> 1 << MqttPublishFlags.valueOf(flag).ordinal())
                        .reduce(0, (a, b) -> a | b);
                    this.flags = flags;
                    return this;
                }

                public MqttWillMessageMatcherBuilder expiryInterval(
                    int expiryInterval)
                {
                    this.expiryInterval = expiryInterval;
                    return this;
                }

                public MqttWillMessageMatcherBuilder contentType(
                    String contentType)
                {
                    this.contentType = new String16FW(contentType);
                    return this;
                }

                public MqttWillMessageMatcherBuilder format(
                    String format)
                {
                    MqttPayloadFormatFW.Builder builder =
                        new MqttPayloadFormatFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                    this.format = builder.set(MqttPayloadFormat.valueOf(format)).build();
                    return this;
                }

                public MqttWillMessageMatcherBuilder responseTopic(
                    String topic)
                {
                    this.responseTopic = new String16FW(topic);
                    return this;
                }

                public MqttWillMessageMatcherBuilder correlation(
                    String correlation)
                {
                    assert correlationRW == null;
                    correlationRW = new MqttBinaryFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                    correlationRO.wrap(correlation.getBytes(UTF_8));
                    correlationRW.bytes(correlationRO, 0, correlationRO.capacity());

                    return this;
                }

                public MqttWillMessageMatcherBuilder correlationBytes(
                    byte[] correlation)
                {
                    assert correlationRW == null;
                    correlationRW = new MqttBinaryFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                    correlationRO.wrap(correlation);
                    correlationRW.bytes(correlationRO, 0, correlationRO.capacity());

                    return this;
                }

                public MqttWillMessageMatcherBuilder userProperty(
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

                public MqttWillMessageMatcherBuilder payload(
                    String payload)
                {
                    assert payloadRW == null;
                    payloadRW = new MqttBinaryFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                    payloadRO.wrap(payload.getBytes(UTF_8));
                    payloadRW.bytes(payloadRO, 0, payloadRO.capacity());

                    return this;
                }

                public MqttWillMessageMatcherBuilder payloadBytes(
                    byte[] payload)
                {
                    assert payloadRW == null;
                    payloadRW = new MqttBinaryFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                    payloadRO.wrap(payload);
                    payloadRW.bytes(payloadRO, 0, payloadRO.capacity());

                    return this;
                }

                public MqttSessionBeginExMatcherBuilder build()
                {
                    return MqttSessionBeginExMatcherBuilder.this;
                }

                private boolean match(
                    MqttMessageFW willMessage)
                {
                    return matchTopic(willMessage) &&
                        matchDelay(willMessage) &&
                        matchQos(willMessage) &&
                        matchFlags(willMessage) &&
                        matchExpiryInterval(willMessage) &&
                        matchContentType(willMessage) &&
                        matchFormat(willMessage) &&
                        matchResponseTopic(willMessage) &&
                        matchCorrelation(willMessage) &&
                        matchUserProperties(willMessage) &&
                        matchPayload(willMessage);
                }

                private boolean matchTopic(
                    final MqttMessageFW willMessage)
                {
                    return topic == null || topic.equals(willMessage.topic());
                }

                private boolean matchDelay(
                    final MqttMessageFW willMessage)
                {
                    return delay == null || delay == willMessage.delay();
                }

                private boolean matchQos(
                    final MqttMessageFW willMessage)
                {
                    return qos == null || qos == willMessage.qos();
                }

                private boolean matchFlags(
                    final MqttMessageFW willMessage)
                {
                    return flags == null || flags == willMessage.flags();
                }

                private boolean matchExpiryInterval(
                    final MqttMessageFW willMessage)
                {
                    return expiryInterval == null || expiryInterval == willMessage.expiryInterval();
                }

                private boolean matchContentType(
                    final MqttMessageFW willMessage)
                {
                    return contentType == null || contentType.equals(willMessage.contentType());
                }

                private boolean matchFormat(
                    final MqttMessageFW willMessage)
                {
                    return format == null || format.equals(willMessage.format());
                }

                private boolean matchResponseTopic(
                    final MqttMessageFW willMessage)
                {
                    return responseTopic == null || responseTopic.equals(willMessage.responseTopic());
                }

                private boolean matchCorrelation(
                    final MqttMessageFW willMessage)
                {
                    return correlationRW == null || correlationRW.build().equals(willMessage.correlation());
                }

                private boolean matchUserProperties(
                    final MqttMessageFW willMessage)
                {
                    return userPropertiesRW == null || userPropertiesRW.build().equals(willMessage.properties());
                }

                private boolean matchPayload(
                    final MqttMessageFW willMessage)
                {
                    return payloadRW == null || payloadRW.build().equals(willMessage.payload());
                }
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
            private String16FW topic;
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

            public MqttPublishDataExMatcherBuilder topic(
                String topic)
            {
                this.topic = new String16FW(topic);
                return this;
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
                return matchTopic(publishDataEx) &&
                    matchQos(publishDataEx) &&
                    matchFlags(publishDataEx) &&
                    matchExpiryInterval(publishDataEx) &&
                    matchContentType(publishDataEx) &&
                    matchFormat(publishDataEx) &&
                    matchResponseTopic(publishDataEx) &&
                    matchCorrelation(publishDataEx) &&
                    matchUserProperties(publishDataEx);
            }

            private boolean matchTopic(
                final MqttPublishDataExFW data)
            {
                return topic == null || topic.equals(data.topic());
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
