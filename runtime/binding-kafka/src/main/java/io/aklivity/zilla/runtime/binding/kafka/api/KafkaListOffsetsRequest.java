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

import java.util.function.IntConsumer;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.list_offsets_v6.ListOffsetsRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.list_offsets_v6.ListOffsetsRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.list_offsets_v6.PartitionRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.list_offsets_v6.TopicRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.list_offsets_v6.TopicRequestPart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.lang.Strings;

public final class KafkaListOffsetsRequest
{
    /**
     * Requests the latest (log end) offset for a partition, the only timestamp a
     * {@code describe_consumer_group_lag} caller needs.
     */
    public static final long LATEST_TIMESTAMP = -1L;

    private static final int REPLICA_ID_CONSUMER = -1;
    private static final byte ISOLATION_LEVEL_READ_UNCOMMITTED = 0;
    private static final int CURRENT_LEADER_EPOCH_UNKNOWN = -1;

    private static final int FIELD_SIZE_PARTITION_INDEX = 4;
    private static final int FIELD_SIZE_CURRENT_LEADER_EPOCH = 4;
    private static final int FIELD_SIZE_TIMESTAMP = 8;
    private static final int FIELD_SIZE_REPLICA_ID = 4;
    private static final int FIELD_SIZE_ISOLATION_LEVEL = 1;

    private static final short LIST_OFFSETS_API_VERSION_V6 = 6;

    private KafkaListOffsetsRequest()
    {
    }

    /**
     * A fully-observed ListOffsets request, ready to drive {@link Generator#generate(Source)} or
     * {@link #sizeof(Source, short)} directly, regardless of the input format that produced it
     * (a topic-partition list collected from an OffsetFetch response today; any format implements
     * the same contract).
     */
    public interface Source
    {
        int topicCount();

        void forEach(
            TopicConsumer consumer);

        interface TopicConsumer
        {
            void accept(
                Topic topic);
        }

        interface Topic
        {
            String name();

            int partitionCount();

            void forEachPartition(
                IntConsumer consumer);
        }
    }

    /**
     * The exact number of bytes {@link Generator#generate(Source)} will write for {@code source} at
     * {@code apiVersion}, computed by arithmetic alone (string byte lengths, item counts, and this
     * version's fixed field widths) with no buffer touched. Only version 6 - the first flexible
     * (compact strings/arrays + tagged fields) ListOffsets version - is implemented today; a future
     * version's formula would branch alongside it here rather than replacing it.
     */
    public static int sizeof(
        Source source,
        short apiVersion)
    {
        if (apiVersion != LIST_OFFSETS_API_VERSION_V6)
        {
            throw new UnsupportedOperationException("unsupported ListOffsets API version: " + apiVersion);
        }

        final int[] size = { 1 + FIELD_SIZE_REPLICA_ID + FIELD_SIZE_ISOLATION_LEVEL + varintWidth(source.topicCount() + 1) };

        source.forEach(t ->
        {
            size[0] += stringSizeof(t.name()) + varintWidth(t.partitionCount() + 1);

            t.forEachPartition(p ->
                size[0] += FIELD_SIZE_PARTITION_INDEX + FIELD_SIZE_CURRENT_LEADER_EPOCH + FIELD_SIZE_TIMESTAMP + 1);

            size[0] += 1;
        });

        size[0] += 1;

        return size[0];
    }

    private static int stringSizeof(
        String value)
    {
        final int length = Strings.utf8Length(value);
        return varintWidth(length + 1) + length;
    }

    private static int varintWidth(
        int value)
    {
        int width = 1;
        int remaining = value >>> 7;
        while (remaining != 0)
        {
            width++;
            remaining >>>= 7;
        }
        return width;
    }

    public static final class Generator
    {
        private final ListOffsetsRequestFW.Builder listOffsetsRequestRW = new ListOffsetsRequestFW.Builder();
        private final ListOffsetsRequestPart2FW.Builder listOffsetsRequestPart2RW = new ListOffsetsRequestPart2FW.Builder();
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
                final ListOffsetsRequestFW listOffsetsRequest = listOffsetsRequestRW.wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .replicaId(REPLICA_ID_CONSUMER)
                    .isolationLevel(ISOLATION_LEVEL_READ_UNCOMMITTED)
                    .topicCount(count)
                    .build();

                progress = listOffsetsRequest.limit();
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

        public boolean build()
        {
            boolean built = declaredTopics >= 0 && declaredTopics == actualTopics;
            if (built)
            {
                try
                {
                    final ListOffsetsRequestPart2FW listOffsetsRequestPart2 = listOffsetsRequestPart2RW
                        .wrap(buffer, progress, limit)
                        .taggedFields(0)
                        .build();

                    progress = listOffsetsRequestPart2.limit();
                }
                catch (IndexOutOfBoundsException ex)
                {
                    built = false;
                }
            }
            return built;
        }

        /**
         * Drives this generator from {@code source}, writing every topic-partition it yields at
         * {@link #LATEST_TIMESTAMP}. Returns {@code false} if any struct failed to fit the buffer.
         */
        public boolean generate(
            Source source)
        {
            topics(source.topicCount());

            final boolean[] ok = { true };
            source.forEach(t ->
            {
                final Topic topic = topic()
                    .name(t.name())
                    .partitions(t.partitionCount());

                t.forEachPartition(topic::partition);

                if (!topic.build())
                {
                    ok[0] = false;
                }
            });

            return ok[0] && build();
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
        private final PartitionRequestFW.Builder partitionRequestRW = new PartitionRequestFW.Builder();

        private Generator generator;
        private String name;

        private boolean headerWritten;
        private boolean overflowed;

        private int declaredPartitions;
        private int actualPartitions;

        private Topic wrap(
            Generator generator)
        {
            this.generator = generator;
            this.name = null;
            this.headerWritten = false;
            this.overflowed = false;
            this.declaredPartitions = 0;
            this.actualPartitions = 0;
            return this;
        }

        public Topic name(
            String name)
        {
            this.name = name;
            return this;
        }

        public Topic partitions(
            int count)
        {
            try
            {
                final TopicRequestFW topicRequest = topicRequestRW
                    .wrap(generator.buffer, generator.progress, generator.limit)
                    .name(name)
                    .partitionCount(count)
                    .build();

                generator.progress = topicRequest.limit();
                declaredPartitions = count;
            }
            catch (IndexOutOfBoundsException ex)
            {
                overflowed = true;
                declaredPartitions = -1;
            }
            headerWritten = true;
            actualPartitions = 0;
            return this;
        }

        public Topic partition(
            int partitionIndex)
        {
            if (!headerWritten)
            {
                partitions(0);
            }
            actualPartitions++;
            try
            {
                final PartitionRequestFW partitionRequest = partitionRequestRW
                    .wrap(generator.buffer, generator.progress, generator.limit)
                    .partitionIndex(partitionIndex)
                    .currentLeaderEpoch(CURRENT_LEADER_EPOCH_UNKNOWN)
                    .timestamp(LATEST_TIMESTAMP)
                    .taggedFields(0)
                    .build();

                generator.progress = partitionRequest.limit();
            }
            catch (IndexOutOfBoundsException ex)
            {
                overflowed = true;
            }
            return this;
        }

        public boolean build()
        {
            if (!headerWritten)
            {
                partitions(0);
            }

            boolean built = !overflowed && declaredPartitions == actualPartitions;
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
    }
}
