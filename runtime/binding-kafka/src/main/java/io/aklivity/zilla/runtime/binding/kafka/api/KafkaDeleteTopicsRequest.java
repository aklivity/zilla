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

import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.delete_topics.DeleteTopicsRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.delete_topics.DeleteTopicsRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.delete_topics.TopicRequestFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.lang.Strings;

public final class KafkaDeleteTopicsRequest
{
    private static final short DELETE_TOPICS_API_VERSION_V6 = 6;
    private static final int TOPIC_ID_SIZE = 16;
    private static final DirectBufferEx ZERO_TOPIC_ID = new UnsafeBufferEx(new byte[TOPIC_ID_SIZE]);

    private KafkaDeleteTopicsRequest()
    {
    }

    /**
     * A fully-observed DeleteTopics request, ready to drive {@link Generator#generate(Source)} or
     * {@link #sizeof(Source, short)} directly, regardless of the input format that produced it
     * (JSON tool-call arguments today; any format implements the same contract).
     */
    public interface Source
    {
        int topicCount();

        void forEach(
            Consumer<String> consumer);

        int timeoutMs();
    }

    /**
     * The exact number of bytes {@link Generator#generate(Source)} will write for {@code source} at
     * {@code apiVersion}, computed by arithmetic alone (string byte lengths, item counts, and this
     * version's fixed field widths) with no buffer touched. Only version 6 is implemented today;
     * a future version's formula would branch alongside it here rather than replacing it.
     */
    public static int sizeof(
        Source source,
        short apiVersion)
    {
        if (apiVersion != DELETE_TOPICS_API_VERSION_V6)
        {
            throw new UnsupportedOperationException("unsupported DeleteTopics API version: " + apiVersion);
        }

        final int[] size = { 1 + varintWidth(source.topicCount() + 1) };

        source.forEach(name -> size[0] += stringSizeof(name) + TOPIC_ID_SIZE + 1);

        size[0] += 4 + 1;

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
        private final DeleteTopicsRequestFW.Builder deleteTopicsRequestRW = new DeleteTopicsRequestFW.Builder();
        private final TopicRequestFW.Builder topicRequestRW = new TopicRequestFW.Builder();
        private final DeleteTopicsRequestPart2FW.Builder deleteTopicsRequestPart2RW = new DeleteTopicsRequestPart2FW.Builder();

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
            this.declaredTopics = -1;
            this.actualTopics = 0;
            this.overflowed = false;
            return this;
        }

        public Generator topics(
            int count)
        {
            try
            {
                final DeleteTopicsRequestFW deleteTopicsRequest = deleteTopicsRequestRW.wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .topicCount(count)
                    .build();

                progress = deleteTopicsRequest.limit();
                declaredTopics = count;
            }
            catch (IndexOutOfBoundsException ex)
            {
                declaredTopics = -1;
            }
            return this;
        }

        /**
         * Writes one topic entry directly - no per-topic sub-builder is needed since nothing nests
         * under a delete-topics entry (unlike CreateTopics' assignments/configs).
         */
        public Generator topic(
            String name)
        {
            actualTopics++;
            try
            {
                final TopicRequestFW topicRequest = topicRequestRW.wrap(buffer, progress, limit)
                    .name(name)
                    .topicId(ZERO_TOPIC_ID, 0, TOPIC_ID_SIZE)
                    .taggedFields(0)
                    .build();

                progress = topicRequest.limit();
            }
            catch (IndexOutOfBoundsException ex)
            {
                overflowed = true;
            }
            return this;
        }

        public boolean build(
            int timeoutMs)
        {
            boolean built = !overflowed && declaredTopics >= 0 && declaredTopics == actualTopics;
            if (built)
            {
                try
                {
                    final DeleteTopicsRequestPart2FW deleteTopicsRequestPart2 = deleteTopicsRequestPart2RW
                        .wrap(buffer, progress, limit)
                        .timeout(timeoutMs)
                        .taggedFields(0)
                        .build();

                    progress = deleteTopicsRequestPart2.limit();
                }
                catch (IndexOutOfBoundsException ex)
                {
                    built = false;
                }
            }
            return built;
        }

        /**
         * Drives this generator from {@code source}, writing every topic it yields, then finishing
         * with {@code source}'s own timeout. Returns {@code false} if any struct failed to fit the
         * buffer.
         */
        public boolean generate(
            Source source)
        {
            topics(source.topicCount());
            source.forEach(this::topic);
            return build(source.timeoutMs());
        }

        public int limit()
        {
            return progress;
        }
    }
}
