/*
 * Copyright 2021-2024 Aklivity Inc.
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

import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaServerConfig;

public final class KafkaClientRoute
{
    public final long resolvedId;
    public final Long2ObjectHashMap<KafkaServerConfig> servers;
    public final Int2ObjectHashMap<Int2IntHashMap> partitions;

    public volatile long metaInitialId;

    public KafkaClientRoute(
        long resolvedId)
    {
        this.resolvedId = resolvedId;
        this.servers = new Long2ObjectHashMap<>();
        this.partitions = new Int2ObjectHashMap<>();
    }

    public Int2IntHashMap supplyPartitions(
        String topic)
    {
        int topicKey = System.identityHashCode(topic.intern());
        return partitions.computeIfAbsent(topicKey, k -> new Int2IntHashMap(-1));
    }
}
