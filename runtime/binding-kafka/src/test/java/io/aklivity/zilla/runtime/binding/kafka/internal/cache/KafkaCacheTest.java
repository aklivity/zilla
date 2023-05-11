/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;

public class KafkaCacheTest
{
    @Test
    public void shouldSupplyTopic() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();
        KafkaCache cache = new KafkaCache(config, "cache");

        KafkaCacheTopic topic1 = cache.supplyTopic("test");
        KafkaCacheTopic topic2 = cache.supplyTopic("test");

        assert topic1 == topic2;
    }

    @Test
    public void shouldDescribeObject() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();
        KafkaCache cache = new KafkaCache(config, "test");

        assertEquals("test", cache.name());
        assertEquals("[KafkaCache] test", cache.toString());
    }
}
