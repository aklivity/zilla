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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream;

import java.util.List;
import java.util.function.IntConsumer;

import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPublishOffsetMetadataFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;

public class MqttKafkaPublishMetadata
{
    final Long2ObjectHashMap<KafkaOffsetMetadata> offsets;
    final Int2ObjectHashMap<List<KafkaTopicPartition>> partitions;
    final Long2LongHashMap leaderEpochs;

    KafkaGroup group;

    public MqttKafkaPublishMetadata(
        Long2ObjectHashMap<KafkaOffsetMetadata> offsets,
        Int2ObjectHashMap<List<KafkaTopicPartition>> partitions,
        Long2LongHashMap leaderEpochs)
    {
        this.offsets = offsets;
        this.partitions = partitions;
        this.leaderEpochs = leaderEpochs;
    }

    public static final class KafkaGroup
    {
        public final String instanceId;
        public final String groupId;
        public final String memberId;
        public final int generationId;

        KafkaGroup(
            String instanceId,
            String groupId,
            String memberId,
            int generationId)
        {
            this.instanceId = instanceId;
            this.groupId = groupId;
            this.memberId = memberId;
            this.generationId = generationId;
        }
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

        KafkaOffsetMetadata(
            long producerId,
            short producerEpoch)
        {
            this(producerId, producerEpoch, new IntArrayList());
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
    }

    public static final class KafkaOffsetMetadataHelper
    {
        private static final int OFFSET_METADATA_VERSION = 1;

        private final MqttPublishOffsetMetadataFW mqttOffsetMetadataRO = new MqttPublishOffsetMetadataFW();
        private final MqttPublishOffsetMetadataFW.Builder mqttOffsetMetadataRW = new MqttPublishOffsetMetadataFW.Builder();
        private final MutableDirectBuffer offsetBuffer;

        KafkaOffsetMetadataHelper(
            MutableDirectBuffer offsetBuffer)
        {
            this.offsetBuffer = offsetBuffer;
        }

        public KafkaOffsetMetadata stringToOffsetMetadata(
            String16FW metadata)
        {
            final IntArrayList packetIds = new IntArrayList();
            UnsafeBuffer buffer = new UnsafeBuffer(BitUtil.fromHex(metadata.asString()));
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
}
