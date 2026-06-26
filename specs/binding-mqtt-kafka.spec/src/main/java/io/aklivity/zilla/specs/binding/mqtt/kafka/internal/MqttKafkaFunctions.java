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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PrimitiveIterator;

import org.agrona.BitUtil;
import org.agrona.collections.IntArrayList;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.mqtt.kafka.internal.types.MqttKafkaSessionOffsetMetadataFW;
import io.aklivity.zilla.specs.binding.mqtt.kafka.internal.types.MqttKafkaSessionOffsetsFW;
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

    @Function
    public static MqttKafkaSessionOffsetsBuilder sessionOffsets()
    {
        return new MqttKafkaSessionOffsetsBuilder();
    }

    @Function
    public static MqttKafkaSessionOffsetsMatcher matchSessionOffsets()
    {
        return new MqttKafkaSessionOffsetsMatcher();
    }

    public static final class MqttSubscribeOffsetMetadataBuilder
    {
        private final MqttSubscribeOffsetMetadataFW.Builder offsetMetadataRW =
            new MqttSubscribeOffsetMetadataFW.Builder();

        private final MqttSubscribeOffsetMetadataFW offsetMetadataRO = new MqttSubscribeOffsetMetadataFW();
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);

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
            MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);
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

    public static final class MqttKafkaSessionOffsetsBuilder
    {
        private final MqttKafkaSessionOffsetsFW.Builder sessionOffsetsRW = new MqttKafkaSessionOffsetsFW.Builder();
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);

        private final List<SessionOffsetEntry> entries = new ArrayList<>();

        private int version = 1;

        private MqttKafkaSessionOffsetsBuilder()
        {
        }

        public MqttKafkaSessionOffsetsBuilder version(
            int version)
        {
            this.version = version;
            return this;
        }

        public MqttKafkaSessionOffsetsBuilder entry(
            String topic,
            int partitionId,
            long consumedOffset,
            long producerId,
            int producerEpoch,
            int producerSequence)
        {
            entries.add(new SessionOffsetEntry(topic, partitionId, consumedOffset, producerId, producerEpoch, producerSequence));
            return this;
        }

        public MqttKafkaSessionOffsetsBuilder packetId(
            int packetId)
        {
            entries.get(entries.size() - 1).packetIds.add(packetId);
            return this;
        }

        public byte[] build()
        {
            MqttKafkaSessionOffsetsFW.Builder builder = sessionOffsetsRW
                .wrap(writeBuffer, 0, writeBuffer.capacity())
                .version(version);
            for (SessionOffsetEntry entry : entries)
            {
                builder.entriesItem(item ->
                {
                    item.topic(entry.topic)
                        .partitionId(entry.partitionId)
                        .consumedOffset(entry.consumedOffset)
                        .producerId(entry.producerId)
                        .producerEpoch((short) entry.producerEpoch)
                        .producerSequence(entry.producerSequence);
                    for (int packetId : entry.packetIds)
                    {
                        item.appendPacketIds((short) packetId);
                    }
                });
            }
            MqttKafkaSessionOffsetsFW sessionOffsets = builder.build();
            return Arrays.copyOfRange(sessionOffsets.buffer().byteArray(), sessionOffsets.offset(), sessionOffsets.limit());
        }

        private static final class SessionOffsetEntry
        {
            private final String topic;
            private final int partitionId;
            private final long consumedOffset;
            private final long producerId;
            private final int producerEpoch;
            private final int producerSequence;
            private final IntArrayList packetIds = new IntArrayList();

            private SessionOffsetEntry(
                String topic,
                int partitionId,
                long consumedOffset,
                long producerId,
                int producerEpoch,
                int producerSequence)
            {
                this.topic = topic;
                this.partitionId = partitionId;
                this.consumedOffset = consumedOffset;
                this.producerId = producerId;
                this.producerEpoch = producerEpoch;
                this.producerSequence = producerSequence;
            }
        }
    }

    public static final class MqttKafkaSessionOffsetsMatcher implements BytesMatcher
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
        private final MqttKafkaSessionOffsetsFW sessionOffsetsRO = new MqttKafkaSessionOffsetsFW();

        private final List<SessionOffsetEntryMatcher> entries = new ArrayList<>();

        private Integer version;

        private MqttKafkaSessionOffsetsMatcher()
        {
        }

        public MqttKafkaSessionOffsetsMatcher version(
            int version)
        {
            this.version = version;
            return this;
        }

        public MqttKafkaSessionOffsetsMatcher entry(
            String topic,
            int partitionId,
            long consumedOffset,
            long producerId,
            int producerEpoch,
            int producerSequence)
        {
            entries.add(new SessionOffsetEntryMatcher(topic, partitionId, consumedOffset, producerId, producerEpoch,
                producerSequence));
            return this;
        }

        public MqttKafkaSessionOffsetsMatcher packetId(
            int packetId)
        {
            entries.get(entries.size() - 1).packetIds.add(packetId);
            return this;
        }

        public BytesMatcher build()
        {
            return this;
        }

        @Override
        public ByteBuffer match(
            ByteBuffer byteBuf) throws Exception
        {
            if (version == null && entries.isEmpty())
            {
                return null;
            }

            ByteBuffer result = null;

            if (byteBuf.hasRemaining())
            {
                bufferRO.wrap(byteBuf);
                MqttKafkaSessionOffsetsFW sessionOffsets = sessionOffsetsRO.tryWrap(bufferRO, byteBuf.position(),
                    byteBuf.capacity());

                if (sessionOffsets != null && matchVersion(sessionOffsets) && matchEntries(sessionOffsets))
                {
                    byteBuf.position(byteBuf.position() + sessionOffsets.sizeof());
                    result = byteBuf;
                }
            }

            if (result == null)
            {
                throw new Exception("session offsets did not match");
            }

            return result;
        }

        private boolean matchVersion(
            MqttKafkaSessionOffsetsFW sessionOffsets)
        {
            return version == null || version == sessionOffsets.version();
        }

        private boolean matchEntries(
            MqttKafkaSessionOffsetsFW sessionOffsets)
        {
            boolean match = true;

            if (!entries.isEmpty())
            {
                List<MqttKafkaSessionOffsetMetadataFW> actual = new ArrayList<>();
                sessionOffsets.entries().forEach(entry ->
                {
                    MqttKafkaSessionOffsetMetadataFW copy = new MqttKafkaSessionOffsetMetadataFW();
                    copy.wrap(entry.buffer(), entry.offset(), entry.limit());
                    actual.add(copy);
                });

                match = actual.size() == entries.size();
                for (int i = 0; match && i < entries.size(); i++)
                {
                    match = entries.get(i).matches(actual.get(i));
                }
            }

            return match;
        }

        private static final class SessionOffsetEntryMatcher
        {
            private final String topic;
            private final int partitionId;
            private final long consumedOffset;
            private final long producerId;
            private final int producerEpoch;
            private final int producerSequence;
            private final IntArrayList packetIds = new IntArrayList();

            private SessionOffsetEntryMatcher(
                String topic,
                int partitionId,
                long consumedOffset,
                long producerId,
                int producerEpoch,
                int producerSequence)
            {
                this.topic = topic;
                this.partitionId = partitionId;
                this.consumedOffset = consumedOffset;
                this.producerId = producerId;
                this.producerEpoch = producerEpoch;
                this.producerSequence = producerSequence;
            }

            private boolean matches(
                MqttKafkaSessionOffsetMetadataFW entry)
            {
                boolean match = topic.equals(entry.topic().asString()) &&
                    partitionId == entry.partitionId() &&
                    consumedOffset == entry.consumedOffset() &&
                    producerId == entry.producerId() &&
                    producerEpoch == entry.producerEpoch() &&
                    producerSequence == entry.producerSequence();

                if (match)
                {
                    IntArrayList actualPacketIds = new IntArrayList();
                    PrimitiveIterator.OfInt iterator = entry.packetIds();
                    if (iterator != null)
                    {
                        while (iterator.hasNext())
                        {
                            actualPacketIds.add(iterator.nextInt());
                        }
                    }
                    match = actualPacketIds.equals(packetIds);
                }

                return match;
            }
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
