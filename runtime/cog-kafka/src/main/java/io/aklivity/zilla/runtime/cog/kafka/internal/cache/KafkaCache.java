/*
 * Copyright 2021-2021 Aklivity Inc.
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

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.agrona.BitUtil;

import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaConfiguration;

public final class KafkaCache
{
    private final AtomicLong produceCapacity = new AtomicLong(0L);

    private static final long[] EMPTY_SORT_SPACE = new long[0];

    private final KafkaConfiguration config;
    private final String name;
    private final Path location;
    private final Map<String, KafkaCacheTopic> topicsByName;
    private final ThreadLocal<long[]> sortSpaceRef;

    public KafkaCache(
        KafkaConfiguration config,
        String name)
    {
        this.config = config;
        this.name = name;
        this.location = config.cacheDirectory().resolve(name);
        this.topicsByName = new ConcurrentHashMap<>();
        this.sortSpaceRef = ThreadLocal.withInitial(() -> EMPTY_SORT_SPACE);
    }

    public boolean hasAvailableProduceCapacity()
    {
        return produceCapacity.longValue() < config.cacheProduceCapacity();
    }

    public String name()
    {
        return name;
    }

    public KafkaCacheTopic supplyTopic(
        String name)
    {
        return topicsByName.computeIfAbsent(name, this::newTopic);
    }

    @Override
    public String toString()
    {
        return String.format("[%s] %s", getClass().getSimpleName(), name);
    }

    private KafkaCacheTopic newTopic(
        String topic)
    {
        return new KafkaCacheTopic(location, config, name, produceCapacity, topic, this::supplySortSpace);
    }

    private long[] supplySortSpace(
        int lengthMin)
    {
        long[] sortSpace = sortSpaceRef.get();

        if (sortSpace.length < lengthMin)
        {
            sortSpace = new long[BitUtil.findNextPositivePowerOfTwo(lengthMin)];
            sortSpaceRef.set(sortSpace);
        }

        return sortSpace;
    }
}
