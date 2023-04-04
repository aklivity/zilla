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
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttEndReasonCode;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttPayloadFormatFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttPublishFlags;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttSessionStateFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttSubscribeFlags;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttTopicFilterFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttWillMessageFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.String16FW;
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

    @Function
    public static MqttWillMessageBuilder willMessage()
    {
        return new MqttWillMessageBuilder();
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

            public MqttSessionBeginExBuilder willMessage(
                MqttWillMessageFW willMessageFW)
            {
                sessionBeginExRW.willMessage(willMessageFW);
                return this;
            }

            public MqttBeginExBuilder build()
            {
                final MqttSessionBeginExFW subscribeBeginEx = sessionBeginExRW.build();
                beginExRO.wrap(writeBuffer, 0, subscribeBeginEx.limit());
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

            public MqttSubscribeBeginExBuilder filter(
                String pattern,
                int id,
                String... flags)
            {
                int flagsBitset = Arrays.stream(flags)
                    .mapToInt(flag -> 1 << MqttSubscribeFlags.valueOf(flag).ordinal())
                    .reduce(0, (a, b) -> a | b);
                subscribeBeginExRW.filtersItem(f -> f.pattern(pattern).subscriptionId(id).flags(flagsBitset));
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
                int msgExp)
            {
                subscribeDataExRW.expiryInterval(msgExp);
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
                final MqttSubscribeDataExFW subscribeBeginEx = subscribeDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, subscribeBeginEx.limit());
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

            public MqttPublishDataExBuilder flags(
                String... flags)
            {
                int publishFlags = Arrays.stream(flags)
                    .mapToInt(flag -> 1 << MqttPublishFlags.valueOf(flag).ordinal())
                    .reduce(0, (a, b) -> a | b);
                publishDataExRW.flags(publishFlags);
                return this;
            }

            public MqttPublishDataExBuilder expiryInterval(
                int msgExp)
            {
                publishDataExRW.expiryInterval(msgExp);
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
        private final MqttFlushExFW.Builder flushExRW;

        private MqttFlushExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.flushExRW = new MqttFlushExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public MqttFlushExBuilder typeId(
            int typeId)
        {
            flushExRW.typeId(typeId);
            return this;
        }

        public MqttFlushExBuilder filter(
            String topic,
            int id,
            String... flags)
        {
            int flagsLocal = Arrays.stream(flags)
                .mapToInt(flag -> 1 << MqttSubscribeFlags.valueOf(flag).ordinal())
                .reduce(0, (a, b) -> a | b);
            flushExRW.filtersItem(f -> f.pattern(topic).subscriptionId(id).flags(flagsLocal));
            return this;
        }

        public byte[] build()
        {
            final MqttFlushExFW flushEx = flushExRW.build();
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
            String pattern,
            int id,
            String... flags)
        {
            int flagsLocal = Arrays.stream(flags)
                .mapToInt(flag -> 1 << MqttSubscribeFlags.valueOf(flag).ordinal())
                .reduce(0, (a, b) -> a | b);
            sessionStateRW.subscriptionsItem(f -> f.pattern(pattern).subscriptionId(id).flags(flagsLocal));
            return this;
        }

        public byte[] build()
        {
            final MqttSessionStateFW sessionStateFW = sessionStateRW.build();
            final byte[] array = new byte[sessionStateFW.sizeof()];
            sessionStateFW.buffer().getBytes(sessionStateFW.offset(), array);
            return array;
        }
    }

    public static final class MqttWillMessageBuilder
    {
        private final MqttWillMessageFW.Builder willMessageRW = new MqttWillMessageFW.Builder();

        private MqttWillMessageBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            willMessageRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public MqttWillMessageBuilder topic(
            String willTopic)
        {
            willMessageRW.topic(willTopic);
            return this;
        }

        public MqttWillMessageBuilder delay(
            int willDelay)
        {
            willMessageRW.delay(willDelay);
            return this;
        }

        public MqttWillMessageBuilder flags(
            String... willFlags)
        {
            int publishFlags = Arrays.stream(willFlags)
                .mapToInt(flag -> 1 << MqttPublishFlags.valueOf(flag).ordinal())
                .reduce(0, (a, b) -> a | b);
            willMessageRW.flags(publishFlags);
            return this;
        }

        public MqttWillMessageBuilder expiryInterval(
            int willMsgExp)
        {
            willMessageRW.expiryInterval(willMsgExp);
            return this;
        }

        public MqttWillMessageBuilder contentType(
            String willContentType)
        {
            willMessageRW.contentType(willContentType);
            return this;
        }

        public MqttWillMessageBuilder format(
            String willFormat)
        {
            willMessageRW.format(p -> p.set(MqttPayloadFormat.valueOf(willFormat)));
            return this;
        }

        public MqttWillMessageBuilder responseTopic(
            String topic)
        {
            willMessageRW.responseTopic(topic);
            return this;
        }

        public MqttWillMessageBuilder correlation(
            String willCorrelation)
        {
            willMessageRW.correlation(c -> c.bytes(b -> b.set(willCorrelation.getBytes(UTF_8))));
            return this;
        }

        public MqttWillMessageBuilder correlationBytes(
            byte[] willCorrelation)
        {
            willMessageRW.correlation(c -> c.bytes(b -> b.set(willCorrelation)));
            return this;
        }

        public MqttWillMessageBuilder userProperty(
            String name,
            String value)
        {
            willMessageRW.userPropertiesItem(p -> p.key(name).value(value));
            return this;
        }

        public MqttWillMessageBuilder payload(
            String willPayload)
        {
            willMessageRW.payload(c -> c.bytes(b -> b.set(willPayload.getBytes(UTF_8))));
            return this;
        }

        public MqttWillMessageBuilder payloadBytes(
            byte[] willPayload)
        {
            willMessageRW.payload(c -> c.bytes(b -> b.set(willPayload)));
            return this;
        }

        public MqttWillMessageFW build()
        {
            return willMessageRW.build();
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

        //        public KafkaFetchBeginExMatcherBuilder fetch()
        //        {
        //            final KafkaFetchBeginExMatcherBuilder matcherBuilder = new KafkaFetchBeginExMatcherBuilder();
        //
        //            this.kind = KafkaApi.FETCH.value();
        //            this.caseMatcher = matcherBuilder::match;
        //            return matcherBuilder;
        //        }

        //        public KafkaProduceBeginExMatcherBuilder produce()
        //        {
        //            final KafkaProduceBeginExMatcherBuilder matcherBuilder = new KafkaProduceBeginExMatcherBuilder();
        //
        //            this.kind = KafkaApi.PRODUCE.value();
        //            this.caseMatcher = matcherBuilder::match;
        //            return matcherBuilder;
        //        }

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
            private Array32FW.Builder<MqttTopicFilterFW.Builder, MqttTopicFilterFW> topicFiltersRW;

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
                String pattern,
                int id,
                String... flags)
            {
                if (topicFiltersRW == null)
                {
                    this.topicFiltersRW = new Array32FW.Builder<>(new MqttTopicFilterFW.Builder(), new MqttTopicFilterFW())
                        .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }
                int flagsLocal = Arrays.stream(flags)
                    .mapToInt(flag -> 1 << MqttSubscribeFlags.valueOf(flag).ordinal())
                    .reduce(0, (a, b) -> a | b);
                topicFiltersRW.item(i -> i
                    .pattern(pattern)
                    .subscriptionId(id)
                    .flags(flagsLocal));
                return this;
            }

            public MqttBeginExMatcherBuilder build()
            {
                return MqttBeginExMatcherBuilder.this;
            }

            private boolean match(
                MqttBeginExFW beginEx)
            {
                final MqttSubscribeBeginExFW mergedBeginEx = beginEx.subscribe();
                return matchClientId(mergedBeginEx) &&
                    matchTopicFilters(mergedBeginEx);
            }

            private boolean matchClientId(
                final MqttSubscribeBeginExFW subscribeBeginEx)
            {
                return clientId == null || clientId.equals(subscribeBeginEx.clientId());
            }

            private boolean matchTopicFilters(
                final MqttSubscribeBeginExFW subscribeBeginEx)
            {
                return topicFiltersRW == null || topicFiltersRW.build().equals(subscribeBeginEx.filters());
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
