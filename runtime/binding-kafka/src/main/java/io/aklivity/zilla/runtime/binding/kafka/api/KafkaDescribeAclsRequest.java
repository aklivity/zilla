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

import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_acls_v2.DescribeAclsRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_acls_v2.DescribeAclsRequestPart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.lang.Strings;

public final class KafkaDescribeAclsRequest
{
    private static final short DESCRIBE_ACLS_API_VERSION_V2 = 2;

    private KafkaDescribeAclsRequest()
    {
    }

    /**
     * A fully-observed DescribeAcls filter, ready to drive {@link Generator#generate(Source)} or
     * {@link #sizeof(Source, short)} directly, regardless of the input format that produced it
     * (JSON tool-call arguments today; any format implements the same contract). Unlike
     * {@link KafkaCreateAclsRequest} and {@link KafkaDeleteAclsRequest}, DescribeAcls carries exactly
     * one filter per request - the real Kafka wire shape has no repeated top-level array - so
     * {@code Source} exposes the filter fields directly rather than a collection to iterate.
     */
    public interface Source
    {
        byte resourceType();

        /**
         * @return the resource name to match, or {@code null} to match any resource name
         */
        String resourceName();

        byte patternType();

        /**
         * @return the principal to match, or {@code null} to match any principal
         */
        String principal();

        /**
         * @return the host to match, or {@code null} to match any host
         */
        String host();

        byte operation();

        byte permissionType();
    }

    /**
     * The exact number of bytes {@link Generator#generate(Source)} will write for {@code source} at
     * {@code apiVersion}, computed by arithmetic alone (string byte lengths and this version's fixed
     * field widths) with no buffer touched. Only version 2 - the first flexible (compact strings +
     * tagged fields) DescribeAcls version - is implemented today; a future version's formula would
     * branch alongside it here rather than replacing it.
     */
    public static int sizeof(
        Source source,
        short apiVersion)
    {
        if (apiVersion != DESCRIBE_ACLS_API_VERSION_V2)
        {
            throw new UnsupportedOperationException("unsupported DescribeAcls API version: " + apiVersion);
        }

        return 1 +
            1 +
            stringSizeof(source.resourceName()) +
            1 +
            stringSizeof(source.principal()) +
            stringSizeof(source.host()) +
            1 +
            1 +
            1;
    }

    private static int stringSizeof(
        String value)
    {
        int sizeof;
        if (value == null)
        {
            sizeof = 1;
        }
        else
        {
            final int length = Strings.utf8Length(value);
            sizeof = varintWidth(length + 1) + length;
        }
        return sizeof;
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
        private final DescribeAclsRequestFW.Builder describeAclsRequestRW = new DescribeAclsRequestFW.Builder();
        private final DescribeAclsRequestPart2FW.Builder describeAclsRequestPart2RW =
            new DescribeAclsRequestPart2FW.Builder();

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
         * Drives this generator from {@code source}, writing the single filter it describes.
         * Returns {@code false} if any struct failed to fit the buffer.
         */
        public boolean generate(
            Source source)
        {
            boolean built;
            try
            {
                final DescribeAclsRequestFW describeAclsRequest = describeAclsRequestRW
                    .wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .resourceTypeFilter(source.resourceType())
                    .resourceNameFilter(source.resourceName())
                    .patternTypeFilter(source.patternType())
                    .principalFilter(source.principal())
                    .hostFilter(source.host())
                    .operation(source.operation())
                    .permissionType(source.permissionType())
                    .build();

                progress = describeAclsRequest.limit();

                final DescribeAclsRequestPart2FW describeAclsRequestPart2 = describeAclsRequestPart2RW
                    .wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .build();

                progress = describeAclsRequestPart2.limit();

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
