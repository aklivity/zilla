/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import java.util.List;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_topics.AssignmentRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_topics.AssignmentRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_topics.BrokerRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_topics.ConfigRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_topics.ConfigsRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_topics.CreateTopicsRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_topics.CreateTopicsRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_topics.TopicRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_topics.TopicRequestPart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

public final class KafkaCreateTopicsRequestGenerator
{
    private final CreateTopicsRequestFW.Builder createTopicsRequestRW = new CreateTopicsRequestFW.Builder();
    private final TopicRequestFW.Builder topicRequestRW = new TopicRequestFW.Builder();
    private final AssignmentRequestFW.Builder assignmentRequestRW = new AssignmentRequestFW.Builder();
    private final BrokerRequestFW.Builder brokerRequestRW = new BrokerRequestFW.Builder();
    private final AssignmentRequestPart2FW.Builder assignmentRequestPart2RW = new AssignmentRequestPart2FW.Builder();
    private final ConfigsRequestFW.Builder configsRequestRW = new ConfigsRequestFW.Builder();
    private final ConfigRequestFW.Builder configRequestRW = new ConfigRequestFW.Builder();
    private final TopicRequestPart2FW.Builder topicRequestPart2RW = new TopicRequestPart2FW.Builder();
    private final CreateTopicsRequestPart2FW.Builder createTopicsRequestPart2RW = new CreateTopicsRequestPart2FW.Builder();

    public int generate(
        MutableDirectBufferEx buffer,
        int offset,
        int limit,
        Request request)
    {
        int progress = offset;

        final CreateTopicsRequestFW createTopicsRequest = createTopicsRequestRW.wrap(buffer, progress, limit)
            .taggedFields(0)
            .topicCount(request.topics().size())
            .build();

        progress = createTopicsRequest.limit();

        for (Topic topic : request.topics())
        {
            final TopicRequestFW topicRequest = topicRequestRW.wrap(buffer, progress, limit)
                .name(topic.name())
                .partitions(topic.partitions())
                .replicas(topic.replicas())
                .assignmentCount(topic.assignments().size())
                .build();

            progress = topicRequest.limit();

            for (Assignment assignment : topic.assignments())
            {
                final AssignmentRequestFW assignmentRequest = assignmentRequestRW.wrap(buffer, progress, limit)
                    .partitionIndex(assignment.partitionIndex())
                    .brokerCount(assignment.brokerIds().size())
                    .build();

                progress = assignmentRequest.limit();

                for (int brokerId : assignment.brokerIds())
                {
                    final BrokerRequestFW brokerRequest = brokerRequestRW.wrap(buffer, progress, limit)
                        .brokerId(brokerId)
                        .build();

                    progress = brokerRequest.limit();
                }

                final AssignmentRequestPart2FW assignmentRequestPart2 = assignmentRequestPart2RW
                    .wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .build();

                progress = assignmentRequestPart2.limit();
            }

            final ConfigsRequestFW configsRequest = configsRequestRW.wrap(buffer, progress, limit)
                .configCount(topic.configs().size())
                .build();

            progress = configsRequest.limit();

            for (Config config : topic.configs())
            {
                final ConfigRequestFW configRequest = configRequestRW.wrap(buffer, progress, limit)
                    .name(config.name())
                    .value(config.value())
                    .taggedFields(0)
                    .build();

                progress = configRequest.limit();
            }

            final TopicRequestPart2FW topicRequestPart2 = topicRequestPart2RW.wrap(buffer, progress, limit)
                .taggedFields(0)
                .build();

            progress = topicRequestPart2.limit();
        }

        final CreateTopicsRequestPart2FW createTopicsRequestPart2 = createTopicsRequestPart2RW
            .wrap(buffer, progress, limit)
            .timeout(request.timeoutMs())
            .validate_only(request.validateOnly() ? (byte) 1 : (byte) 0)
            .taggedFields(0)
            .build();

        progress = createTopicsRequestPart2.limit();

        return progress;
    }

    public record Request(
        List<Topic> topics,
        int timeoutMs,
        boolean validateOnly)
    {
    }

    public record Topic(
        String name,
        int partitions,
        short replicas,
        List<Assignment> assignments,
        List<Config> configs)
    {
    }

    public record Assignment(
        int partitionIndex,
        List<Integer> brokerIds)
    {
    }

    public record Config(
        String name,
        String value)
    {
    }
}
