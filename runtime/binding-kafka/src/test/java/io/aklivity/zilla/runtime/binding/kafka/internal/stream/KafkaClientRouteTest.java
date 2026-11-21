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

import static org.junit.Assert.assertSame;

import java.util.UUID;

import org.agrona.collections.Int2IntHashMap;
import org.junit.Test;

public class KafkaClientRouteTest
{
    @Test
    public void shouldSupplySamePartitionsAfterTopicNameIsCollected()
    {
        KafkaClientRoute route = new KafkaClientRoute(1L);
        char[] name = ("topic-" + UUID.randomUUID()).toCharArray();

        Int2IntHashMap partitions = route.supplyPartitions(new String(name));

        for (int attempt = 0; attempt < 4; attempt++)
        {
            System.gc();
        }

        assertSame(partitions, route.supplyPartitions(new String(name)));
    }
}
