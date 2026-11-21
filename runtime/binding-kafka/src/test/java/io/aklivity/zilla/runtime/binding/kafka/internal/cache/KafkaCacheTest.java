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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static org.junit.Assert.assertEquals;

import java.util.UUID;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheRoute;

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
    public void shouldKeepTopicKeyStableWhileTopicIsCached() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();
        KafkaCache cache = new KafkaCache(config, "cache");
        char[] name = ("topic-" + UUID.randomUUID()).toCharArray();

        String interned = new String(name);
        int topicKey = KafkaCacheRoute.topicKey(interned);
        cache.supplyTopic(new String(name));
        interned = null;

        for (int attempt = 0; attempt < 4; attempt++)
        {
            System.gc();
        }

        assertEquals(topicKey, KafkaCacheRoute.topicKey(new String(name)));
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
