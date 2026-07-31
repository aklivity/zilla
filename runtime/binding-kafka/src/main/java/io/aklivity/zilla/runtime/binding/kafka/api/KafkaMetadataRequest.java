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

import java.util.function.Consumer;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata.MetadataRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata.MetadataRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata.TopicNameRequestFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.lang.Strings;

public final class KafkaMetadataRequest
{
    private static final short METADATA_API_VERSION_V9 = 9;

    private KafkaMetadataRequest()
    {
    }

    /**
     * A fully-observed Metadata request, ready to drive {@link Generator#generate(Source)} or
     * {@link #sizeof(Source, short)} directly, regardless of the input format that produced it
     * (JSON tool-call arguments today; any format implements the same contract).
     */
    public interface Source
    {
        /**
         * @return {@code true} to request every topic in the cluster - {@link #topicCount()} and
         *         {@link #forEach(Consumer)} are not consulted in that case
         */
        boolean allTopics();

        int topicCount();

        void forEach(
            Consumer<String> consumer);
    }

    /**
     * The exact number of bytes {@link Generator#generate(Source)} will write for {@code source} at
     * {@code apiVersion}, computed by arithmetic alone (string byte lengths, item counts, and this
     * version's fixed field widths) with no buffer touched. Only version 9 is implemented today;
     * a future version's formula would branch alongside it here rather than replacing it.
     */
    public static int sizeof(
        Source source,
        short apiVersion)
    {
        if (apiVersion != METADATA_API_VERSION_V9)
        {
            throw new UnsupportedOperationException("unsupported Metadata API version: " + apiVersion);
        }

        final int count = source.allTopics() ? -1 : source.topicCount();
        final int[] size = { 1 + varintWidth(count + 1) };

        if (!source.allTopics())
        {
            source.forEach(name -> size[0] += stringSizeof(name) + 1);
        }

        size[0] += 3 + 1;

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
        private final MetadataRequestFW.Builder metadataRequestRW = new MetadataRequestFW.Builder();
        private final TopicNameRequestFW.Builder topicNameRequestRW = new TopicNameRequestFW.Builder();
        private final MetadataRequestPart2FW.Builder metadataRequestPart2RW = new MetadataRequestPart2FW.Builder();

        private MutableDirectBufferEx buffer;
        private int limit;
        private int progress;

        private int declaredTopics;
        private int actualTopics;
        private boolean overflowed;

        public Generator wrap(
            MutableDirectBufferEx buffer,
            int offset,
            int limit)
        {
            this.buffer = buffer;
            this.limit = limit;
            this.progress = offset;
            this.declaredTopics = Integer.MIN_VALUE;
            this.actualTopics = 0;
            this.overflowed = false;
            return this;
        }

        /**
         * @param count the number of topics that will follow, or {@code -1} to request every topic
         *              in the cluster
         */
        public Generator topics(
            int count)
        {
            try
            {
                final MetadataRequestFW metadataRequest = metadataRequestRW.wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .topicCount(count)
                    .build();

                progress = metadataRequest.limit();
                declaredTopics = count;
            }
            catch (IndexOutOfBoundsException ex)
            {
                overflowed = true;
            }
            return this;
        }

        /**
         * Writes one topic entry directly - no per-topic sub-builder is needed since nothing nests
         * under a Metadata request-side topic entry.
         */
        public Generator topic(
            String name)
        {
            actualTopics++;
            try
            {
                final TopicNameRequestFW topicNameRequest = topicNameRequestRW.wrap(buffer, progress, limit)
                    .name(name)
                    .taggedFields(0)
                    .build();

                progress = topicNameRequest.limit();
            }
            catch (IndexOutOfBoundsException ex)
            {
                overflowed = true;
            }
            return this;
        }

        public boolean build()
        {
            final boolean topicsMatch = declaredTopics == -1 ? actualTopics == 0 : declaredTopics == actualTopics;
            boolean built = !overflowed && topicsMatch;
            if (built)
            {
                try
                {
                    final MetadataRequestPart2FW metadataRequestPart2 = metadataRequestPart2RW
                        .wrap(buffer, progress, limit)
                        .allowAutoTopicCreation((byte) 0)
                        .includeClusterAuthorizedOperations((byte) 0)
                        .includeTopicAuthorizedOperations((byte) 0)
                        .taggedFields(0)
                        .build();

                    progress = metadataRequestPart2.limit();
                }
                catch (IndexOutOfBoundsException ex)
                {
                    built = false;
                }
            }
            return built;
        }

        /**
         * Drives this generator from {@code source}, writing either every topic it yields or the
         * null/all-topics marker, then finishing with the fixed request-side flags (auto topic
         * creation and authorized-operations reporting are never requested by a read-only metadata
         * tool). Returns {@code false} if any struct failed to fit the buffer.
         */
        public boolean generate(
            Source source)
        {
            topics(source.allTopics() ? -1 : source.topicCount());

            if (!source.allTopics())
            {
                source.forEach(this::topic);
            }

            return build();
        }

        public int limit()
        {
            return progress;
        }
    }
}
