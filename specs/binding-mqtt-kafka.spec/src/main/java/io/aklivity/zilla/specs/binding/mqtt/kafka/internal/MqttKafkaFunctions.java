/*
 * Copyright 2021-2024 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.specs.binding.mqtt.kafka.internal;

import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.specs.binding.mqtt.kafka.internal.types.MqttPublishOffsetMetadataFW;
import io.aklivity.zilla.specs.binding.mqtt.kafka.internal.types.MqttSubscribeOffsetMetadataFW;
import io.aklivity.zilla.specs.binding.mqtt.kafka.internal.types.MqttSubscribeOffsetMetadataV1FW;
import io.aklivity.zilla.specs.binding.mqtt.kafka.internal.types.MqttSubscribeOffsetMetadataV2FW;
import io.aklivity.zilla.specs.binding.mqtt.kafka.internal.types.MqttSubscribeOffsetMetadataVersion;

public final class MqttKafkaFunctions
{
    @Function
    public static MqttSubscribeOffsetMetadataBuilder subscribeMetadata()
    {
        return new MqttSubscribeOffsetMetadataBuilder();
    }

    @Function
    public static MqttPublishOffsetMetadataBuilder publishMetadata()
    {
        return new MqttPublishOffsetMetadataBuilder();
    }

    public static final class MqttSubscribeOffsetMetadataBuilder
    {
        private final MqttSubscribeOffsetMetadataFW.Builder offsetMetadataRW =
            new MqttSubscribeOffsetMetadataFW.Builder();

        private final MqttSubscribeOffsetMetadataFW offsetMetadataRO = new MqttSubscribeOffsetMetadataFW();
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        private MqttSubscribeOffsetMetadataBuilder()
        {
            offsetMetadataRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public MqttSubscribeOffsetMetadataV1Builder v1()
        {
            offsetMetadataRW.kind(MqttSubscribeOffsetMetadataVersion.V1);
            return new MqttSubscribeOffsetMetadataV1Builder();
        }

        public MqttSubscribeOffsetMetadataV2Builder v2()
        {
            offsetMetadataRW.kind(MqttSubscribeOffsetMetadataVersion.V2);
            return new MqttSubscribeOffsetMetadataV2Builder();
        }

        public String build()
        {
            final MqttSubscribeOffsetMetadataFW offsetMetadata = offsetMetadataRO;
            return BitUtil.toHex(offsetMetadata.buffer().byteArray(), offsetMetadata.offset(), offsetMetadata.limit());
        }

        public final class MqttSubscribeOffsetMetadataV1Builder
        {
            private final MqttSubscribeOffsetMetadataV1FW.Builder offsetMetadataV1RW =
                new MqttSubscribeOffsetMetadataV1FW.Builder();

            private MqttSubscribeOffsetMetadataV1Builder()
            {
                offsetMetadataV1RW.wrap(writeBuffer, 1, writeBuffer.capacity());
            }

            public MqttSubscribeOffsetMetadataV1Builder metadata(
                int packetId)
            {
                offsetMetadataV1RW.appendPacketIds((short) packetId);
                return this;
            }

            public MqttSubscribeOffsetMetadataBuilder build()
            {
                final MqttSubscribeOffsetMetadataV1FW offsetMetadataV1 = offsetMetadataV1RW.build();
                offsetMetadataRO.wrap(writeBuffer, 0, offsetMetadataV1.limit());
                return MqttSubscribeOffsetMetadataBuilder.this;
            }
        }

        public final class MqttSubscribeOffsetMetadataV2Builder
        {
            private final MqttSubscribeOffsetMetadataV2FW.Builder offsetMetadataV2RW =
                new MqttSubscribeOffsetMetadataV2FW.Builder();

            private MqttSubscribeOffsetMetadataV2Builder()
            {
                offsetMetadataV2RW.wrap(writeBuffer, 1, writeBuffer.capacity());
            }

            public MqttSubscribeOffsetMetadataV2Builder metadata(
                int packetId)
            {
                offsetMetadataV2RW.appendPacketIds((short) packetId);
                return this;
            }

            public MqttSubscribeOffsetMetadataBuilder build()
            {
                final MqttSubscribeOffsetMetadataV2FW offsetMetadataV2 = offsetMetadataV2RW.build();
                offsetMetadataRO.wrap(writeBuffer, 0, offsetMetadataV2.limit());
                return MqttSubscribeOffsetMetadataBuilder.this;
            }
        }
    }

    public static final class MqttPublishOffsetMetadataBuilder
    {
        private final MqttPublishOffsetMetadataFW.Builder offsetMetadataRW = new MqttPublishOffsetMetadataFW.Builder();

        byte version = 1;


        private MqttPublishOffsetMetadataBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            offsetMetadataRW.wrap(writeBuffer, 0, writeBuffer.capacity());
            offsetMetadataRW.version(version);
        }

        public MqttPublishOffsetMetadataBuilder packetId(
            int packetId)
        {
            offsetMetadataRW.appendPacketIds((short) packetId);
            return this;
        }

        public MqttPublishOffsetMetadataBuilder producer(
            long producerId,
            short producerEpoch)
        {
            offsetMetadataRW.producerId(producerId).producerEpoch(producerEpoch);
            return this;
        }

        public String build()
        {
            final MqttPublishOffsetMetadataFW offsetMetadata = offsetMetadataRW.build();
            return BitUtil.toHex(offsetMetadata.buffer().byteArray(), offsetMetadata.offset(), offsetMetadata.limit());
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(MqttKafkaFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "mqtt_kafka";
        }
    }

    private MqttKafkaFunctions()
    {
        /* utility */
    }
}
