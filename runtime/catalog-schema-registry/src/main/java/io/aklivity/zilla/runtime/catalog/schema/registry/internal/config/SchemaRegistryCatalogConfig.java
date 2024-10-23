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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.config;

import io.aklivity.zilla.runtime.catalog.schema.registry.config.AbstractSchemaRegistryOptionsConfig;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.SchemaRegistryCatalogFactorySpi;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.events.SchemaRegistryEventContext;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.handler.SchemaRegistryCache;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;

public final class SchemaRegistryCatalogConfig
{
    public final long id;
    public final SchemaRegistryEventContext events;
    public final AbstractSchemaRegistryOptionsConfig options;
    public final SchemaRegistryCache cache;

    public SchemaRegistryCatalogConfig(
        EngineContext context,
        CatalogConfig catalog)
    {
        this(SchemaRegistryCatalogFactorySpi.TYPE, context, catalog, new SchemaRegistryCache());
    }

    public SchemaRegistryCatalogConfig(
        String type,
        EngineContext context,
        CatalogConfig catalog,
        SchemaRegistryCache cache)
    {
        this.id = catalog.id;
        this.options = AbstractSchemaRegistryOptionsConfig.class.cast(catalog.options);
        this.events = new SchemaRegistryEventContext(context, type);
        this.cache = cache;
    }
}
