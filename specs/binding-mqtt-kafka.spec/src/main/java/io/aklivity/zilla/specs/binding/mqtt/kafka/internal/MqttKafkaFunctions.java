/*
 * Copyright 2021-2023 Aklivity Inc
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
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.mqtt.kafka.internal.types.MqttPublishOffsetMetadataFW;
import io.aklivity.zilla.specs.binding.mqtt.kafka.internal.types.MqttSubscribeOffsetMetadataFW;

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
        private final MqttSubscribeOffsetMetadataFW.Builder offsetMetadataRW = new MqttSubscribeOffsetMetadataFW.Builder();

        byte version = 1;


        private MqttSubscribeOffsetMetadataBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            offsetMetadataRW.wrap(writeBuffer, 0, writeBuffer.capacity());
            offsetMetadataRW.version(version);
        }

        public MqttSubscribeOffsetMetadataBuilder metadata(
            int packetId)
        {
            offsetMetadataRW.appendPacketIds((short) packetId);
            return this;
        }

        public String build()
        {
            final MqttSubscribeOffsetMetadataFW offsetMetadata = offsetMetadataRW.build();
            return BitUtil.toHex(offsetMetadata.buffer().byteArray(), offsetMetadata.offset(), offsetMetadata.limit());
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
