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
package io.aklivity.zilla.runtime.binding.kafka.api;

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

public final class KafkaCreateTopicsRequest
{
    private KafkaCreateTopicsRequest()
    {
    }

    public static final class Generator
    {
        private final CreateTopicsRequestFW.Builder createTopicsRequestRW = new CreateTopicsRequestFW.Builder();
        private final CreateTopicsRequestPart2FW.Builder createTopicsRequestPart2RW = new CreateTopicsRequestPart2FW.Builder();
        private final Topic topicRW = new Topic();

        private MutableDirectBufferEx buffer;
        private int limit;
        private int progress;

        private int declaredTopics;
        private int actualTopics;

        public Generator wrap(
            MutableDirectBufferEx buffer,
            int offset,
            int limit)
        {
            this.buffer = buffer;
            this.limit = limit;
            this.progress = offset;
            this.declaredTopics = -1;
            this.actualTopics = 0;
            return this;
        }

        public Generator topics(
            int count)
        {
            try
            {
                final CreateTopicsRequestFW createTopicsRequest = createTopicsRequestRW.wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .topicCount(count)
                    .build();

                progress = createTopicsRequest.limit();
                declaredTopics = count;
            }
            catch (IndexOutOfBoundsException ex)
            {
                declaredTopics = -1;
            }
            return this;
        }

        public Topic topic()
        {
            actualTopics++;
            return topicRW.wrap(this);
        }

        public boolean build(
            int timeoutMs,
            boolean validateOnly)
        {
            boolean built = declaredTopics >= 0 && declaredTopics == actualTopics;
            if (built)
            {
                try
                {
                    final CreateTopicsRequestPart2FW createTopicsRequestPart2 = createTopicsRequestPart2RW
                        .wrap(buffer, progress, limit)
                        .timeout(timeoutMs)
                        .validate_only(validateOnly ? (byte) 1 : (byte) 0)
                        .taggedFields(0)
                        .build();

                    progress = createTopicsRequestPart2.limit();
                }
                catch (IndexOutOfBoundsException ex)
                {
                    built = false;
                }
            }
            return built;
        }

        public int limit()
        {
            return progress;
        }
    }

    public static final class Topic
    {
        private final TopicRequestFW.Builder topicRequestRW = new TopicRequestFW.Builder();
        private final TopicRequestPart2FW.Builder topicRequestPart2RW = new TopicRequestPart2FW.Builder();
        private final ConfigsRequestFW.Builder configsRequestRW = new ConfigsRequestFW.Builder();
        private final Assignment assignmentRW = new Assignment();
        private final Config configRW = new Config();

        private Generator generator;
        private String name;
        private int partitions;
        private short replicas;

        private boolean headerWritten;
        private boolean configsHeaderWritten;
        private boolean overflowed;

        private int declaredAssignments;
        private int actualAssignments;
        private int declaredConfigs;
        private int actualConfigs;

        private Topic wrap(
            Generator generator)
        {
            this.generator = generator;
            this.name = null;
            this.partitions = 0;
            this.replicas = 0;
            this.headerWritten = false;
            this.configsHeaderWritten = false;
            this.overflowed = false;
            this.declaredAssignments = 0;
            this.actualAssignments = 0;
            this.declaredConfigs = 0;
            this.actualConfigs = 0;
            return this;
        }

        public Topic name(
            String name)
        {
            this.name = name;
            return this;
        }

        public Topic partitions(
            int partitions)
        {
            this.partitions = partitions;
            return this;
        }

        public Topic replicas(
            short replicas)
        {
            this.replicas = replicas;
            return this;
        }

        public Topic assignments(
            int count)
        {
            writeHeader(count);
            declaredAssignments = overflowed ? -1 : count;
            actualAssignments = 0;
            return this;
        }

        public Assignment assignment()
        {
            if (!headerWritten)
            {
                assignments(0);
            }
            actualAssignments++;
            return assignmentRW.wrap(generator, this);
        }

        public Topic configs(
            int count)
        {
            if (!headerWritten)
            {
                assignments(0);
            }
            try
            {
                final ConfigsRequestFW configsRequest = configsRequestRW
                    .wrap(generator.buffer, generator.progress, generator.limit)
                    .configCount(count)
                    .build();

                generator.progress = configsRequest.limit();
                declaredConfigs = count;
            }
            catch (IndexOutOfBoundsException ex)
            {
                overflowed = true;
                declaredConfigs = -1;
            }
            configsHeaderWritten = true;
            actualConfigs = 0;
            return this;
        }

        public Config config()
        {
            if (!configsHeaderWritten)
            {
                configs(0);
            }
            actualConfigs++;
            return configRW.wrap(generator, this);
        }

        public boolean build()
        {
            if (!headerWritten)
            {
                assignments(0);
            }
            if (!configsHeaderWritten)
            {
                configs(0);
            }

            boolean built = !overflowed &&
                declaredAssignments == actualAssignments &&
                declaredConfigs == actualConfigs;

            if (built)
            {
                try
                {
                    final TopicRequestPart2FW topicRequestPart2 = topicRequestPart2RW
                        .wrap(generator.buffer, generator.progress, generator.limit)
                        .taggedFields(0)
                        .build();

                    generator.progress = topicRequestPart2.limit();
                }
                catch (IndexOutOfBoundsException ex)
                {
                    built = false;
                }
            }
            return built;
        }

        private void writeHeader(
            int assignmentCount)
        {
            try
            {
                final TopicRequestFW topicRequest = topicRequestRW.wrap(generator.buffer, generator.progress, generator.limit)
                    .name(name)
                    .partitions(partitions)
                    .replicas(replicas)
                    .assignmentCount(assignmentCount)
                    .build();

                generator.progress = topicRequest.limit();
            }
            catch (IndexOutOfBoundsException ex)
            {
                overflowed = true;
            }
            headerWritten = true;
        }
    }

    public static final class Assignment
    {
        private final AssignmentRequestFW.Builder assignmentRequestRW = new AssignmentRequestFW.Builder();
        private final BrokerRequestFW.Builder brokerRequestRW = new BrokerRequestFW.Builder();
        private final AssignmentRequestPart2FW.Builder assignmentRequestPart2RW = new AssignmentRequestPart2FW.Builder();

        private Generator generator;
        private Topic topic;
        private int partitionIndex;

        private boolean headerWritten;
        private boolean overflowed;

        private int declaredBrokers;
        private int actualBrokers;

        private Assignment wrap(
            Generator generator,
            Topic topic)
        {
            this.generator = generator;
            this.topic = topic;
            this.partitionIndex = 0;
            this.headerWritten = false;
            this.overflowed = false;
            this.declaredBrokers = 0;
            this.actualBrokers = 0;
            return this;
        }

        public Assignment partitionIndex(
            int partitionIndex)
        {
            this.partitionIndex = partitionIndex;
            return this;
        }

        public Assignment brokers(
            int count)
        {
            try
            {
                final AssignmentRequestFW assignmentRequest = assignmentRequestRW
                    .wrap(generator.buffer, generator.progress, generator.limit)
                    .partitionIndex(partitionIndex)
                    .brokerCount(count)
                    .build();

                generator.progress = assignmentRequest.limit();
                declaredBrokers = count;
            }
            catch (IndexOutOfBoundsException ex)
            {
                overflowed = true;
                declaredBrokers = -1;
            }
            headerWritten = true;
            actualBrokers = 0;
            return this;
        }

        public Assignment broker(
            int brokerId)
        {
            if (!headerWritten)
            {
                brokers(1);
            }
            actualBrokers++;
            try
            {
                final BrokerRequestFW brokerRequest = brokerRequestRW.wrap(generator.buffer, generator.progress, generator.limit)
                    .brokerId(brokerId)
                    .build();

                generator.progress = brokerRequest.limit();
            }
            catch (IndexOutOfBoundsException ex)
            {
                overflowed = true;
            }
            return this;
        }

        public Topic build()
        {
            if (!headerWritten)
            {
                brokers(0);
            }

            boolean built = !overflowed && declaredBrokers == actualBrokers;
            if (built)
            {
                try
                {
                    final AssignmentRequestPart2FW assignmentRequestPart2 = assignmentRequestPart2RW
                        .wrap(generator.buffer, generator.progress, generator.limit)
                        .taggedFields(0)
                        .build();

                    generator.progress = assignmentRequestPart2.limit();
                }
                catch (IndexOutOfBoundsException ex)
                {
                    built = false;
                }
            }
            if (!built)
            {
                topic.overflowed = true;
            }
            return topic;
        }
    }

    public static final class Config
    {
        private final ConfigRequestFW.Builder configRequestRW = new ConfigRequestFW.Builder();

        private Generator generator;
        private Topic topic;
        private String name;
        private String value;

        private Config wrap(
            Generator generator,
            Topic topic)
        {
            this.generator = generator;
            this.topic = topic;
            this.name = null;
            this.value = null;
            return this;
        }

        public Config name(
            String name)
        {
            this.name = name;
            return this;
        }

        public Config value(
            String value)
        {
            this.value = value;
            return this;
        }

        public Topic build()
        {
            try
            {
                final ConfigRequestFW configRequest = configRequestRW.wrap(generator.buffer, generator.progress, generator.limit)
                    .name(name)
                    .value(value)
                    .taggedFields(0)
                    .build();

                generator.progress = configRequest.limit();
            }
            catch (IndexOutOfBoundsException ex)
            {
                topic.overflowed = true;
            }
            return topic;
        }
    }
}
