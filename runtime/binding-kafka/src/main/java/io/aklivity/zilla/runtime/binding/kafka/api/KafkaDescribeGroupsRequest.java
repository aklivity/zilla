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

import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_groups.DescribeGroupsRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_groups.DescribeGroupsRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_groups.GroupIdRequestFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.lang.Strings;

public final class KafkaDescribeGroupsRequest
{
    private static final short DESCRIBE_GROUPS_API_VERSION_V5 = 5;

    private KafkaDescribeGroupsRequest()
    {
    }

    /**
     * A fully-observed DescribeGroups request, ready to drive {@link Generator#generate(Source)} or
     * {@link #sizeof(Source, short)} directly, regardless of the input format that produced it
     * (JSON tool-call arguments today; any format implements the same contract).
     */
    public interface Source
    {
        int groupCount();

        void forEach(
            Consumer<String> consumer);

        boolean includeAuthorizedOperations();
    }

    /**
     * The exact number of bytes {@link Generator#generate(Source)} will write for {@code source} at
     * {@code apiVersion}, computed by arithmetic alone (string byte lengths, item counts, and this
     * version's fixed field widths) with no buffer touched. Only version 5 is implemented today;
     * a future version's formula would branch alongside it here rather than replacing it.
     */
    public static int sizeof(
        Source source,
        short apiVersion)
    {
        if (apiVersion != DESCRIBE_GROUPS_API_VERSION_V5)
        {
            throw new UnsupportedOperationException("unsupported DescribeGroups API version: " + apiVersion);
        }

        final int[] size = { 1 + varintWidth(source.groupCount() + 1) };

        source.forEach(groupId -> size[0] += stringSizeof(groupId));

        size[0] += 1 + 1;

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
        private final DescribeGroupsRequestFW.Builder describeGroupsRequestRW = new DescribeGroupsRequestFW.Builder();
        private final GroupIdRequestFW.Builder groupIdRequestRW = new GroupIdRequestFW.Builder();
        private final DescribeGroupsRequestPart2FW.Builder describeGroupsRequestPart2RW =
            new DescribeGroupsRequestPart2FW.Builder();

        private MutableDirectBufferEx buffer;
        private int limit;
        private int progress;

        private int declaredGroups;
        private int actualGroups;
        private boolean overflowed;

        public Generator wrap(
            MutableDirectBufferEx buffer,
            int offset,
            int limit)
        {
            this.buffer = buffer;
            this.limit = limit;
            this.progress = offset;
            this.declaredGroups = -1;
            this.actualGroups = 0;
            this.overflowed = false;
            return this;
        }

        public Generator groups(
            int count)
        {
            try
            {
                final DescribeGroupsRequestFW describeGroupsRequest = describeGroupsRequestRW.wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .groupCount(count)
                    .build();

                progress = describeGroupsRequest.limit();
                declaredGroups = count;
            }
            catch (IndexOutOfBoundsException ex)
            {
                declaredGroups = -1;
            }
            return this;
        }

        public Generator group(
            String groupId)
        {
            actualGroups++;
            try
            {
                final GroupIdRequestFW groupIdRequest = groupIdRequestRW.wrap(buffer, progress, limit)
                    .groupId(groupId)
                    .build();

                progress = groupIdRequest.limit();
            }
            catch (IndexOutOfBoundsException ex)
            {
                overflowed = true;
            }
            return this;
        }

        public boolean build(
            boolean includeAuthorizedOperations)
        {
            boolean built = !overflowed && declaredGroups >= 0 && declaredGroups == actualGroups;
            if (built)
            {
                try
                {
                    final DescribeGroupsRequestPart2FW describeGroupsRequestPart2 = describeGroupsRequestPart2RW
                        .wrap(buffer, progress, limit)
                        .includeAuthorizedOperations(includeAuthorizedOperations ? (byte) 1 : (byte) 0)
                        .taggedFields(0)
                        .build();

                    progress = describeGroupsRequestPart2.limit();
                }
                catch (IndexOutOfBoundsException ex)
                {
                    built = false;
                }
            }
            return built;
        }

        /**
         * Drives this generator from {@code source}, writing every group it yields, then finishing
         * with {@code source}'s own includeAuthorizedOperations. Returns {@code false} if any struct
         * failed to fit the buffer.
         */
        public boolean generate(
            Source source)
        {
            groups(source.groupCount());
            source.forEach(this::group);
            return build(source.includeAuthorizedOperations());
        }

        public int limit()
        {
            return progress;
        }
    }
}
