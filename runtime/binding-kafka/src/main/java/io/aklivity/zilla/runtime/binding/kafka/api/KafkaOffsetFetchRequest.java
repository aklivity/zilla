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

import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.offset_fetch_v6.OffsetFetchRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.offset_fetch_v6.OffsetFetchRequestPart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.lang.Strings;

/**
 * OffsetFetch v6 always requests every topic the group has committed offsets for (a null, rather
 * than empty, topics array), the only shape a {@code describe_consumer_group_lag} caller needs.
 */
public final class KafkaOffsetFetchRequest
{
    /**
     * Requests committed offsets for every topic the group has, encoded on the wire as a null
     * (rather than empty) topics array, mirroring describe_configs_v4's null configNamesCount.
     */
    public static final int ALL_TOPICS = -1;

    private static final short OFFSET_FETCH_API_VERSION_V6 = 6;

    private KafkaOffsetFetchRequest()
    {
    }

    /**
     * The exact number of bytes {@link Generator#generate(String)} will write for {@code groupId} at
     * {@code apiVersion}. Only version 6 - the first flexible (compact strings/arrays + tagged
     * fields) OffsetFetch version - is implemented today; a future version's formula would branch
     * alongside it here rather than replacing it.
     */
    public static int sizeof(
        String groupId,
        short apiVersion)
    {
        if (apiVersion != OFFSET_FETCH_API_VERSION_V6)
        {
            throw new UnsupportedOperationException("unsupported OffsetFetch API version: " + apiVersion);
        }

        final int length = Strings.utf8Length(groupId);

        return 1 + varintWidth(length + 1) + length + 1 + 1;
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
        private final OffsetFetchRequestFW.Builder offsetFetchRequestRW = new OffsetFetchRequestFW.Builder();
        private final OffsetFetchRequestPart2FW.Builder offsetFetchRequestPart2RW = new OffsetFetchRequestPart2FW.Builder();

        private MutableDirectBufferEx buffer;
        private int limit;
        private int progress;

        public Generator wrap(
            MutableDirectBufferEx buffer,
            int offset,
            int limit)
        {
            this.buffer = buffer;
            this.limit = limit;
            this.progress = offset;
            return this;
        }

        /**
         * Writes the OffsetFetch v6 request body for {@code groupId}, requesting every topic the
         * group has committed offsets for. Returns {@code false} if it did not fit the buffer.
         */
        public boolean generate(
            String groupId)
        {
            boolean built;
            try
            {
                final OffsetFetchRequestFW offsetFetchRequest = offsetFetchRequestRW.wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .groupId(groupId)
                    .topicCount(ALL_TOPICS)
                    .build();

                progress = offsetFetchRequest.limit();

                final OffsetFetchRequestPart2FW offsetFetchRequestPart2 = offsetFetchRequestPart2RW
                    .wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .build();

                progress = offsetFetchRequestPart2.limit();
                built = true;
            }
            catch (IndexOutOfBoundsException ex)
            {
                built = false;
            }
            return built;
        }

        public int limit()
        {
            return progress;
        }
    }
}
