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
package io.aklivity.zilla.runtime.binding.kafka.internal;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCache;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;

public final class KafkaBinding implements Binding
{
    public static final String NAME = "kafka";

    private final KafkaConfiguration config;
    private final Map<String, KafkaCache> cachesByName;

    KafkaBinding(
        KafkaConfiguration config)
    {
        this.config = config;
        this.cachesByName = new ConcurrentHashMap<>();
    }

    @Override
    public String name()
    {
        return KafkaBinding.NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/kafka.schema.patch.json");
    }

    @Override
    public KafkaBindingContext supply(
        EngineContext context)
    {
        return new KafkaBindingContext(config, context, this::supplyCache);
    }

    public KafkaCache supplyCache(
        String name)
    {
        return cachesByName.computeIfAbsent(name, this::newCache);
    }

    private KafkaCache newCache(
        String name)
    {
        return new KafkaCache(config, name);
    }
}
