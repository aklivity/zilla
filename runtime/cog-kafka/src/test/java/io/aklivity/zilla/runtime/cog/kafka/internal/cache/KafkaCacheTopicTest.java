/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.kafka.internal.cache;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaConfiguration;

public class KafkaCacheTopicTest
{
    @Test
    public void shouldSupplyPartition() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();
        Path location = config.cacheDirectory().resolve("cache");
        KafkaCacheTopic topic = new KafkaCacheTopic(location, config, "cache", new AtomicLong(0L), "test", long[]::new);

        KafkaCachePartition partitionA = topic.supplyFetchPartition(0);
        KafkaCachePartition partitionB = topic.supplyFetchPartition(0);

        assert partitionA == partitionB;
    }

    @Test
    public void shouldDescribeObject() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();
        Path location = config.cacheDirectory().resolve("cache");

        KafkaCacheTopic topic = new KafkaCacheTopic(location, config, "cache", new AtomicLong(0L), "test", long[]::new);

        assertEquals("cache", topic.cache());
        assertEquals("test", topic.name());
        assertEquals("[cache] test", topic.toString());
    }
}
