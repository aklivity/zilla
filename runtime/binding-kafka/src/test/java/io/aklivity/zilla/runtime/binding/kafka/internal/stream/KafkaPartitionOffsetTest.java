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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KafkaPartitionOffsetTest
{
    @Test
    public void shouldSupersedeEarlierOffset() throws Exception
    {
        KafkaPartitionOffset earlier = offset(3L, "meta-2", 3L);
        KafkaPartitionOffset later = offset(4L, "meta-3", 4L);

        KafkaPartitionOffset merged = later.supersede(earlier);

        assertEquals(4L, merged.partitionOffset);
        assertEquals("meta-3", merged.metadata);
        assertEquals(4L, merged.correlationId);
        assertArrayEquals(new long[] {3L}, merged.superseded);
    }

    @Test
    public void shouldKeepEarlierSupersededInOrder() throws Exception
    {
        KafkaPartitionOffset first = offset(2L, "meta-1", 2L);
        KafkaPartitionOffset second = offset(3L, "meta-2", 3L).supersede(first);
        KafkaPartitionOffset third = offset(4L, "meta-3", 4L).supersede(second);

        assertEquals(4L, third.partitionOffset);
        assertEquals(4L, third.correlationId);
        assertArrayEquals(new long[] {2L, 3L}, third.superseded);
    }

    @Test
    public void shouldSupersedeNothingByDefault() throws Exception
    {
        assertArrayEquals(new long[0], offset(2L, "meta-1", 2L).superseded);
    }

    private static KafkaPartitionOffset offset(
        long partitionOffset,
        String metadata,
        long correlationId)
    {
        return new KafkaPartitionOffset("test", 0, partitionOffset, 0, 0, metadata, correlationId);
    }
}
