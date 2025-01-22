/*
 * Copyright 2021-2024 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.catalog.schema.registry.internal;

import java.net.URL;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.catalog.schema.registry.internal.handler.SchemaRegistryCache;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.Catalog;
import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;

public final class SchemaRegistryCatalog implements Catalog
{
    private final Configuration config;
    private final String type;
    private final Set<String> aliases;
    private final Supplier<URL> schema;
    private final ConcurrentMap<Long, SchemaRegistryCache> cache;

    public SchemaRegistryCatalog(
        Configuration config,
        String type,
        Set<String> aliases,
        Supplier<URL> schema)
    {
        this.config = config;
        this.type = type;
        this.aliases = aliases;
        this.schema = schema;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public String name()
    {
        return type;
    }

    @Override
    public Set<String> aliases()
    {
        return aliases;
    }

    @Override
    public CatalogContext supply(
        EngineContext context)
    {
        return new SchemaRegistryCatalogContext(type, config, context, cache);
    }

    @Override
    public URL type()
    {
        return schema.get();
    }
}
