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

import java.util.concurrent.ConcurrentMap;

import io.aklivity.zilla.runtime.catalog.schema.registry.internal.config.SchemaRegistryCatalogConfig;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.handler.SchemaRegistryCache;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.handler.SchemaRegistryCatalogHandler;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;

public class SchemaRegistryCatalogContext implements CatalogContext
{
    private final Configuration config;
    private final String type;
    private final EngineContext context;
    private final ConcurrentMap<Long, SchemaRegistryCache> cachesById;

    public SchemaRegistryCatalogContext(
        String type,
        Configuration config,
        EngineContext context,
        ConcurrentMap<Long, SchemaRegistryCache> cachesById)
    {
        this.config = config;
        this.type = type;
        this.context = context;
        this.cachesById = cachesById;
    }

    @Override
    public CatalogHandler attach(
        CatalogConfig catalog)
    {
        SchemaRegistryCache cache = cachesById.computeIfAbsent(catalog.id, id -> new SchemaRegistryCache());
        SchemaRegistryCatalogConfig attached = new SchemaRegistryCatalogConfig(type, context, catalog, cache);
        return new SchemaRegistryCatalogHandler(config, attached, context);
    }
}
