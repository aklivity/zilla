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

import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_cluster.DescribeClusterRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_cluster.DescribeClusterRequestPart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

/**
 * Unlike {@link KafkaCreateTopicsRequest}/{@link KafkaDeleteTopicsRequest}, a DescribeCluster request has
 * no variable-length or per-item content - it is two fixed-width structs regardless of caller input - so
 * there is no {@code Source} interface and {@link #sizeof(short)} is a constant.
 */
public final class KafkaDescribeClusterRequest
{
    private static final short DESCRIBE_CLUSTER_API_VERSION_V0 = 0;
    private static final int SIZEOF_V0 = 3;

    private KafkaDescribeClusterRequest()
    {
    }

    /**
     * The exact number of bytes {@link Generator#generate(boolean)} will write at {@code apiVersion},
     * computed by arithmetic alone (both structs are fixed-width). Only version 0 is implemented today -
     * the first Kafka-flexible (tagged-fields) version of DescribeCluster - a future version's formula
     * would branch alongside it here rather than replacing it.
     */
    public static int sizeof(
        short apiVersion)
    {
        if (apiVersion != DESCRIBE_CLUSTER_API_VERSION_V0)
        {
            throw new UnsupportedOperationException("unsupported DescribeCluster API version: " + apiVersion);
        }

        return SIZEOF_V0;
    }

    public static final class Generator
    {
        private final DescribeClusterRequestFW.Builder describeClusterRequestRW = new DescribeClusterRequestFW.Builder();
        private final DescribeClusterRequestPart2FW.Builder describeClusterRequestPart2RW =
            new DescribeClusterRequestPart2FW.Builder();

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
         * Writes both DescribeCluster request structs in one call - there is no per-item content to
         * accumulate first, unlike {@code KafkaCreateTopicsRequest.Generator}/{@code KafkaDeleteTopicsRequest.Generator}.
         */
        public boolean generate(
            boolean includeAuthorizedOperations)
        {
            boolean built;
            try
            {
                final DescribeClusterRequestFW describeClusterRequest = describeClusterRequestRW.wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .build();

                progress = describeClusterRequest.limit();

                final DescribeClusterRequestPart2FW describeClusterRequestPart2 = describeClusterRequestPart2RW
                    .wrap(buffer, progress, limit)
                    .includeAuthorizedOperations(includeAuthorizedOperations ? 1 : 0)
                    .taggedFields(0)
                    .build();

                progress = describeClusterRequestPart2.limit();
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
