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

import java.util.Arrays;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttPayloadFormatFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttPublishFlags;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttSubscribeFlags;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttAbortExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttApi;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttFlushExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttPublishBeginExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttPublishDataExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttSessionBeginExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttSessionDataExFW;
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
    public static MqttAbortExBuilder abortEx()
    {
        return new MqttAbortExBuilder();
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
            beginExRW.kind(MqttApi.SESSION.value());

            return new MqttSessionBeginExBuilder();
        }

        public MqttSubscribeBeginExBuilder subscribe()
        {
            beginExRW.kind(MqttApi.SUBSCRIBE.value());

            return new MqttSubscribeBeginExBuilder();
        }

        public MqttPublishBeginExBuilder publish()
        {
            beginExRW.kind(MqttApi.PUBLISH.value());

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

            public MqttSessionBeginExBuilder pattern(
                String pattern)
            {
                sessionBeginExRW.pattern(pattern);
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
                int flagsLocal = Arrays.stream(flags)
                    .mapToInt(flag -> 1 << MqttSubscribeFlags.valueOf(flag).ordinal())
                    .reduce(0, (a, b) -> a | b);
                subscribeBeginExRW.filtersItem(f -> f.pattern(pattern).subscriptionId(id).flags(flagsLocal));
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

        public MqttDataExBuilder.MqttSessionDataExBuilder session()
        {
            dataExRW.kind(MqttApi.SESSION.value());

            return new MqttDataExBuilder.MqttSessionDataExBuilder();
        }

        public MqttDataExBuilder.MqttSubscribeDataExBuilder subscribe()
        {
            dataExRW.kind(MqttApi.SUBSCRIBE.value());

            return new MqttDataExBuilder.MqttSubscribeDataExBuilder();
        }

        public MqttDataExBuilder.MqttPublishDataExBuilder publish()
        {
            dataExRW.kind(MqttApi.PUBLISH.value());

            return new MqttDataExBuilder.MqttPublishDataExBuilder();
        }

        public byte[] build()
        {
            final MqttDataExFW dataEx = dataExRO;
            final byte[] array = new byte[dataEx.sizeof()];
            dataEx.buffer().getBytes(dataEx.offset(), array);
            return array;
        }

        public final class MqttSessionDataExBuilder
        {
            private final MqttSessionDataExFW.Builder sessionDataExRW = new MqttSessionDataExFW.Builder();

            private MqttSessionDataExBuilder()
            {
                sessionDataExRW.wrap(writeBuffer, MqttBeginExFW.FIELD_OFFSET_PUBLISH, writeBuffer.capacity());
            }

            public MqttSessionDataExBuilder topic(
                String topic)
            {
                sessionDataExRW.topic(topic);
                return this;
            }

            public MqttSessionDataExBuilder flags(
                String... flags)
            {
                int publishFlags = 0;
                for (int i = 0; i < flags.length; i++)
                {
                    publishFlags |= 1 << MqttPublishFlags.valueOf(flags[i]).ordinal();
                }
                sessionDataExRW.flags(publishFlags);
                return this;
            }

            public MqttDataExBuilder build()
            {
                final MqttSessionDataExFW sessionBeginEx = sessionDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, sessionBeginEx.limit());
                return MqttDataExBuilder.this;
            }
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
                int subscribeFlags = 0;
                for (int i = 0; i < flags.length; i++)
                {
                    subscribeFlags |= 1 << MqttSubscribeFlags.valueOf(flags[i]).ordinal();
                }
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
                int publishFlags = 0;
                for (int i = 0; i < flags.length; i++)
                {
                    publishFlags |= 1 << MqttPublishFlags.valueOf(flags[i]).ordinal();
                }
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

    public static final class MqttAbortExBuilder
    {
        private final MqttAbortExFW.Builder abortExRW;

        private MqttAbortExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.abortExRW = new MqttAbortExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public MqttAbortExBuilder typeId(
            int typeId)
        {
            abortExRW.typeId(typeId);
            return this;
        }

        public MqttAbortExBuilder reason(
            int reason)
        {
            abortExRW.reason(reason);
            return this;
        }

        public byte[] build()
        {
            final MqttAbortExFW abortEx = abortExRW.build();
            final byte[] array = new byte[abortEx.sizeof()];
            abortEx.buffer().getBytes(abortEx.offset(), array);
            return array;
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
