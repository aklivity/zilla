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
package io.aklivity.zilla.runtime.cog.kafka.internal;

import static io.aklivity.zilla.runtime.engine.config.RoleConfig.CACHE_CLIENT;
import static io.aklivity.zilla.runtime.engine.config.RoleConfig.CACHE_SERVER;
import static io.aklivity.zilla.runtime.engine.config.RoleConfig.CLIENT;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.cog.kafka.internal.cache.KafkaCache;
import io.aklivity.zilla.runtime.cog.kafka.internal.stream.KafkaCacheClientFactory;
import io.aklivity.zilla.runtime.cog.kafka.internal.stream.KafkaCacheRoute;
import io.aklivity.zilla.runtime.cog.kafka.internal.stream.KafkaCacheServerFactory;
import io.aklivity.zilla.runtime.cog.kafka.internal.stream.KafkaClientFactory;
import io.aklivity.zilla.runtime.cog.kafka.internal.stream.KafkaClientRoute;
import io.aklivity.zilla.runtime.cog.kafka.internal.stream.KafkaStreamFactory;
import io.aklivity.zilla.runtime.engine.cog.Axle;
import io.aklivity.zilla.runtime.engine.cog.AxleContext;
import io.aklivity.zilla.runtime.engine.cog.stream.StreamFactory;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.RoleConfig;

final class KafkaAxle implements Axle
{
    private final Long2ObjectHashMap<KafkaClientRoute> clientRoutesById;
    private final Long2ObjectHashMap<KafkaCacheRoute> cacheRoutesById;
    private final Map<RoleConfig, KafkaStreamFactory> factories;

    KafkaAxle(
        KafkaConfiguration config,
        AxleContext context,
        Function<String, KafkaCache> supplyCache)
    {
        this.clientRoutesById = new Long2ObjectHashMap<>();
        this.cacheRoutesById = new Long2ObjectHashMap<>();

        Map<RoleConfig, KafkaStreamFactory> factories = new EnumMap<>(RoleConfig.class);
        factories.put(CLIENT, new KafkaClientFactory(config, context, this::supplyClientRoute));
        factories.put(CACHE_SERVER, new KafkaCacheServerFactory(config, context, supplyCache,
            this::supplyCacheRoute));
        factories.put(CACHE_CLIENT, new KafkaCacheClientFactory(config, context, supplyCache,
            this::supplyCacheRoute));
        this.factories = factories;
    }

    @Override
    public StreamFactory attach(
        BindingConfig binding)
    {
        final KafkaStreamFactory factory = factories.get(binding.kind);

        if (factory != null)
        {
            factory.attach(binding);
        }

        return factory;
    }

    @Override
    public void detach(
        BindingConfig binding)
    {
        final KafkaStreamFactory factory = factories.get(binding.kind);

        if (factory != null)
        {
            factory.detach(binding.id);
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s %s", getClass().getSimpleName(), factories);
    }

    private KafkaCacheRoute supplyCacheRoute(
        long routeId)
    {
        return cacheRoutesById.computeIfAbsent(routeId, KafkaCacheRoute::new);
    }

    private KafkaClientRoute supplyClientRoute(
        long routeId)
    {
        return clientRoutesById.computeIfAbsent(routeId, KafkaClientRoute::new);
    }
}
