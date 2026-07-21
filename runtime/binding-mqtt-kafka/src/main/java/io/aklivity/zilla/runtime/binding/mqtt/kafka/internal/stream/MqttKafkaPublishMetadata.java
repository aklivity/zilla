/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.agrona.BitUtil;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttKafkaSessionOffsetMetadataFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttKafkaSessionOffsetsFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPublishOffsetMetadataFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class MqttKafkaPublishMetadata
{
    final Long2ObjectHashMap<KafkaOffsetMetadata> offsets;
    final List<KafkaOffsetMetadata> orderedOffsets;
    final Int2ObjectHashMap<List<KafkaTopicPartition>> partitions;
    final Long2LongHashMap leaderEpochs;

    String16FW sessionsTopic;
    String16FW clientId;

    public MqttKafkaPublishMetadata(
        Long2ObjectHashMap<KafkaOffsetMetadata> offsets,
        Int2ObjectHashMap<List<KafkaTopicPartition>> partitions,
        Long2LongHashMap leaderEpochs)
    {
        this.offsets = offsets;
        this.orderedOffsets = new ArrayList<>();
        this.partitions = partitions;
        this.leaderEpochs = leaderEpochs;
    }

    public static final class KafkaTopicPartition
    {
        public final String topic;
        public final int partitionId;

        KafkaTopicPartition(
            String topic,
            int partitionId)
        {
            this.topic = topic;
            this.partitionId = partitionId;
        }
    }

    public static final class KafkaOffsetMetadata
    {
        public final long producerId;
        public final short producerEpoch;
        public final IntArrayList packetIds;

        public long sequence;
        public String topic;
        public int partitionId;
        public int producerSequence;

        KafkaOffsetMetadata(
            String topic,
            long producerId,
            short producerEpoch)
        {
            this(topic, producerId, producerEpoch, new IntArrayList());
        }

        KafkaOffsetMetadata(
            long producerId,
            short producerEpoch,
            IntArrayList packetIds)
        {
            this.sequence = 1;
            this.producerId = producerId;
            this.producerEpoch = producerEpoch;
            this.packetIds = packetIds;
        }

        KafkaOffsetMetadata(
            String topic,
            long producerId,
            short producerEpoch,
            IntArrayList packetIds)
        {
            this.sequence = 1;
            this.topic = topic.intern();
            this.producerId = producerId;
            this.producerEpoch = producerEpoch;
            this.packetIds = packetIds;
        }
    }

    public static final class KafkaOffsetMetadataHelper
    {
        private static final int OFFSET_METADATA_VERSION = 1;

        private final MqttPublishOffsetMetadataFW mqttOffsetMetadataRO = new MqttPublishOffsetMetadataFW();
        private final MqttPublishOffsetMetadataFW.Builder mqttOffsetMetadataRW = new MqttPublishOffsetMetadataFW.Builder();
        private final MutableDirectBufferEx offsetBuffer;

        KafkaOffsetMetadataHelper(
            MutableDirectBufferEx offsetBuffer)
        {
            this.offsetBuffer = offsetBuffer;
        }

        public KafkaOffsetMetadata stringToOffsetMetadata(
            String16FW metadata)
        {
            final IntArrayList packetIds = new IntArrayList();
            UnsafeBufferEx buffer = new UnsafeBufferEx(BitUtil.fromHex(metadata.asString()));
            final MqttPublishOffsetMetadataFW offsetMetadata = mqttOffsetMetadataRO.wrap(buffer, 0, buffer.capacity());
            if (offsetMetadata.packetIds() != null)
            {
                offsetMetadata.packetIds().forEachRemaining((IntConsumer) packetIds::add);
            }
            return new KafkaOffsetMetadata(offsetMetadata.producerId(), offsetMetadata.producerEpoch(), packetIds);
        }

        public String16FW offsetMetadataToString(
            KafkaOffsetMetadata metadata)
        {
            mqttOffsetMetadataRW.wrap(offsetBuffer, 0, offsetBuffer.capacity());
            mqttOffsetMetadataRW.version(OFFSET_METADATA_VERSION);
            mqttOffsetMetadataRW.producerId(metadata.producerId);
            mqttOffsetMetadataRW.producerEpoch(metadata.producerEpoch);

            if (metadata.packetIds != null)
            {
                metadata.packetIds.forEach(p -> mqttOffsetMetadataRW.appendPacketIds(p.shortValue()));
            }
            final MqttPublishOffsetMetadataFW offsetMetadata = mqttOffsetMetadataRW.build();
            return new String16FW(BitUtil.toHex(offsetMetadata.buffer().byteArray(),
                offsetMetadata.offset(), offsetMetadata.limit()));
        }
    }

    public static final class KafkaSessionOffsetsHelper
    {
        private static final int SESSION_OFFSETS_VERSION = 1;

        private final MqttKafkaSessionOffsetsFW.Builder sessionOffsetsRW = new MqttKafkaSessionOffsetsFW.Builder();
        private final MutableDirectBufferEx offsetBuffer;
        private final Consumer<MqttKafkaSessionOffsetMetadataFW.Builder> encodeEntry = this::encodeEntry;
        private final IntConsumer appendPacketId = this::appendPacketId;

        private KafkaOffsetMetadata encodingMetadata;
        private MqttKafkaSessionOffsetMetadataFW.Builder encodingItem;

        KafkaSessionOffsetsHelper(
            MutableDirectBufferEx offsetBuffer)
        {
            this.offsetBuffer = offsetBuffer;
        }

        public MqttKafkaSessionOffsetsFW encode(
            List<KafkaOffsetMetadata> entries)
        {
            sessionOffsetsRW.wrap(offsetBuffer, 0, offsetBuffer.capacity());
            sessionOffsetsRW.version(SESSION_OFFSETS_VERSION);
            for (int i = 0, size = entries.size(); i < size; i++)
            {
                encodingMetadata = entries.get(i);
                sessionOffsetsRW.entriesItem(encodeEntry);
            }
            encodingMetadata = null;
            encodingItem = null;
            return sessionOffsetsRW.build();
        }

        public List<KafkaOffsetMetadata> decode(
            MqttKafkaSessionOffsetsFW offsets)
        {
            final List<KafkaOffsetMetadata> entries = new ArrayList<>();
            offsets.entries().forEach(entry ->
            {
                final IntArrayList packetIds = new IntArrayList();
                final PrimitiveIterator.OfInt packetIdsIterator = entry.packetIds();
                if (packetIdsIterator != null)
                {
                    packetIdsIterator.forEachRemaining((IntConsumer) packetIds::add);
                }

                final KafkaOffsetMetadata metadata = new KafkaOffsetMetadata(entry.topic().asString(),
                    entry.producerId(), entry.producerEpoch(), packetIds);
                metadata.sequence = entry.consumedOffset();
                metadata.partitionId = entry.partitionId();
                metadata.producerSequence = entry.producerSequence();
                entries.add(metadata);
            });
            return entries;
        }

        private void encodeEntry(
            MqttKafkaSessionOffsetMetadataFW.Builder item)
        {
            final KafkaOffsetMetadata metadata = encodingMetadata;
            encodingItem = item;
            item.topic(metadata.topic);
            item.partitionId(metadata.partitionId);
            item.consumedOffset(metadata.sequence);
            item.producerId(metadata.producerId);
            item.producerEpoch(metadata.producerEpoch);
            item.producerSequence(metadata.producerSequence);
            if (metadata.packetIds != null)
            {
                metadata.packetIds.forEachInt(appendPacketId);
            }
        }

        private void appendPacketId(
            int packetId)
        {
            encodingItem.appendPacketIds((short) packetId);
        }
    }
}
