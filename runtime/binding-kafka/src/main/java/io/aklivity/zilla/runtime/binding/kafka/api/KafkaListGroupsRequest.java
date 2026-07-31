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

import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.list_groups.ListGroupsRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.list_groups.ListGroupsRequestPart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

/**
 * ListGroups v4 always requests every group - {@code statesFilter} is written as an empty compact
 * array, matching Kafka's own "no filter" semantics, since {@code list_consumer_groups} takes no
 * arguments.
 */
public final class KafkaListGroupsRequest
{
    private static final short LIST_GROUPS_API_VERSION_V4 = 4;

    private KafkaListGroupsRequest()
    {
    }

    /**
     * The exact number of bytes {@link Generator#generate()} will write at {@code apiVersion}. Only
     * version 4 is implemented today; a future version's formula would branch alongside it here
     * rather than replacing it.
     */
    public static int sizeof(
        short apiVersion)
    {
        if (apiVersion != LIST_GROUPS_API_VERSION_V4)
        {
            throw new UnsupportedOperationException("unsupported ListGroups API version: " + apiVersion);
        }

        return 1 + 1;
    }

    public static final class Generator
    {
        private final ListGroupsRequestFW.Builder listGroupsRequestRW = new ListGroupsRequestFW.Builder();
        private final ListGroupsRequestPart2FW.Builder listGroupsRequestPart2RW = new ListGroupsRequestPart2FW.Builder();

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
         * Writes the fixed empty-states-filter ListGroups v4 request body. Returns {@code false} if
         * it did not fit the buffer.
         */
        public boolean generate()
        {
            boolean built;
            try
            {
                final ListGroupsRequestFW listGroupsRequest = listGroupsRequestRW.wrap(buffer, progress, limit)
                    .statesFilterCount(0)
                    .build();

                progress = listGroupsRequest.limit();

                final ListGroupsRequestPart2FW listGroupsRequestPart2 = listGroupsRequestPart2RW
                    .wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .build();

                progress = listGroupsRequestPart2.limit();
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
