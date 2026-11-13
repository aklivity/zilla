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

import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.offset_delete.OffsetDeleteRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.offset_delete.PartitionRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.offset_delete.TopicRequestFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.lang.Strings;

/**
 * OffsetDelete v0 removes the committed offsets of a consumer group for the given topic partitions.
 * Version 0 is not a flexible version: strings carry an int16 length, arrays an int32 count, and there
 * are no tagged fields.
 */
public final class KafkaOffsetDeleteRequest
{
    private static final int FIELD_SIZE_STRING_LENGTH = 2;
    private static final int FIELD_SIZE_ARRAY_COUNT = 4;
    private static final int FIELD_SIZE_PARTITION_INDEX = 4;

    private static final short OFFSET_DELETE_API_VERSION_V0 = 0;

    private KafkaOffsetDeleteRequest()
    {
    }

    /**
     * A fully-observed OffsetDelete request, ready to drive {@link Generator#generate(Source)} or
     * {@link #sizeof(Source, short)} directly, regardless of the input format that produced it.
     */
    public interface Source
    {
        String groupId();

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
     * version's fixed field widths) with no buffer touched. Only version 0 is implemented today; a
     * future version's formula would branch alongside it here rather than replacing it.
     */
    public static int sizeof(
        Source source,
        short apiVersion)
    {
        if (apiVersion != OFFSET_DELETE_API_VERSION_V0)
        {
            throw new UnsupportedOperationException("unsupported OffsetDelete API version: " + apiVersion);
        }

        final int[] size = { stringSizeof(source.groupId()) + FIELD_SIZE_ARRAY_COUNT };

        source.forEach(t ->
        {
            size[0] += stringSizeof(t.name()) + FIELD_SIZE_ARRAY_COUNT;

            t.forEachPartition(p -> size[0] += FIELD_SIZE_PARTITION_INDEX);
        });

        return size[0];
    }

    private static int stringSizeof(
        String value)
    {
        return FIELD_SIZE_STRING_LENGTH + Strings.utf8Length(value);
    }

    public static final class Generator
    {
        private final OffsetDeleteRequestFW.Builder offsetDeleteRequestRW = new OffsetDeleteRequestFW.Builder();
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
            String groupId,
            int count)
        {
            try
            {
                final OffsetDeleteRequestFW offsetDeleteRequest = offsetDeleteRequestRW.wrap(buffer, progress, limit)
                    .groupId(groupId)
                    .topicCount(count)
                    .build();

                progress = offsetDeleteRequest.limit();
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
            return declaredTopics >= 0 && declaredTopics == actualTopics;
        }

        /**
         * Drives this generator from {@code source}, writing every topic-partition it yields.
         * Returns {@code false} if any struct failed to fit the buffer.
         */
        public boolean generate(
            Source source)
        {
            topics(source.groupId(), source.topicCount());

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

            return !overflowed && declaredPartitions == actualPartitions;
        }
    }
}
