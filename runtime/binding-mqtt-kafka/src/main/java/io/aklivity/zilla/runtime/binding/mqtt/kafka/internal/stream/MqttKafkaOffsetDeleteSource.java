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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import org.agrona.collections.IntArrayList;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaOffsetDeleteRequest;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaPublishMetadata.KafkaTopicPartition;

final class MqttKafkaOffsetDeleteSource implements KafkaOffsetDeleteRequest.Source
{
    private final String groupId;
    private final Map<String, IntArrayList> partitionsByTopic;

    MqttKafkaOffsetDeleteSource(
        String groupId,
        List<KafkaTopicPartition> topicPartitions)
    {
        this.groupId = groupId;
        this.partitionsByTopic = new LinkedHashMap<>();
        topicPartitions.forEach(tp ->
            partitionsByTopic.computeIfAbsent(tp.topic, t -> new IntArrayList()).addInt(tp.partitionId));
    }

    @Override
    public String groupId()
    {
        return groupId;
    }

    @Override
    public int topicCount()
    {
        return partitionsByTopic.size();
    }

    @Override
    public void forEach(
        TopicConsumer consumer)
    {
        partitionsByTopic.forEach((name, partitions) -> consumer.accept(new Topic(name, partitions)));
    }

    private static final class Topic implements KafkaOffsetDeleteRequest.Source.Topic
    {
        private final String name;
        private final IntArrayList partitions;

        private Topic(
            String name,
            IntArrayList partitions)
        {
            this.name = name;
            this.partitions = partitions;
        }

        @Override
        public String name()
        {
            return name;
        }

        @Override
        public int partitionCount()
        {
            return partitions.size();
        }

        @Override
        public void forEachPartition(
            IntConsumer consumer)
        {
            partitions.forEachInt(consumer::accept);
        }
    }
}
