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

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;

import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaConfiguration;

public final class KafkaCacheTopic
{
    private final Path location;
    private final String cache;
    private final long maxProduceCapacity;
    private final AtomicLong produceCapacity;
    private final String name;
    private final KafkaCacheTopicConfig config;
    private final int appendCapacity;
    private final Map<Integer, KafkaCachePartition> partitionsById;
    private final Map<Long, KafkaCachePartition> partitionsByIndex;
    private IntFunction<long[]> sortSpaceRef;

    public KafkaCacheTopic(
        Path location,
        KafkaConfiguration config,
        String cache,
        AtomicLong produceCapacity,
        String name,
        IntFunction<long[]> sortSpaceRef)
    {
        this.location = location;
        this.config = new KafkaCacheTopicConfig(config);
        this.appendCapacity = ENGINE_BUFFER_SLOT_CAPACITY.get(config);
        this.cache = cache;
        this.produceCapacity = produceCapacity;
        this.maxProduceCapacity = config.cacheProduceCapacity();
        this.name = name;
        this.partitionsById = new ConcurrentHashMap<>();
        this.partitionsByIndex = new ConcurrentHashMap<>();
        this.sortSpaceRef = sortSpaceRef;
    }

    public String cache()
    {
        return cache;
    }

    public String name()
    {
        return name;
    }

    public KafkaCacheTopicConfig config()
    {
        return config;
    }

    public KafkaCachePartition supplyFetchPartition(
        int id)
    {
        return partitionsById.computeIfAbsent(id, this::newFetchPartition);
    }

    public KafkaCachePartition supplyProducePartition(
        int id,
        int index)
    {
        final long uniqueId = (((long) id) << Integer.SIZE) | index;
        return partitionsByIndex.computeIfAbsent(uniqueId, i -> newProducePartition(id, index));
    }

    @Override
    public String toString()
    {
        return String.format("[%s] %s", cache, name);
    }

    private KafkaCachePartition newFetchPartition(
        int id)
    {
        return new KafkaCachePartition(location, config, cache, name, id, appendCapacity, sortSpaceRef);
    }

    private KafkaCachePartition newProducePartition(
        int id,
        int index)
    {
        return new KafkaCachePartition(location, config, cache, produceCapacity, maxProduceCapacity, name, id, appendCapacity,
            sortSpaceRef, index);
    }

}
