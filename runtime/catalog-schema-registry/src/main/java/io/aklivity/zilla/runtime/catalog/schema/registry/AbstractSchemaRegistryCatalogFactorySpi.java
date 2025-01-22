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
package io.aklivity.zilla.runtime.catalog.schema.registry;

import static io.aklivity.zilla.runtime.engine.factory.Aliasable.ALIASES_DEFAULT;

import java.net.URL;
import java.util.Set;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.catalog.schema.registry.internal.SchemaRegistryCatalog;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.catalog.Catalog;
import io.aklivity.zilla.runtime.engine.catalog.CatalogFactorySpi;

public abstract class AbstractSchemaRegistryCatalogFactorySpi implements CatalogFactorySpi
{
    private final String type;
    private final Set<String> aliases;
    private final Supplier<URL> schema;

    @Override
    public String type()
    {
        return type;
    }

    @Override
    public Catalog create(
        Configuration config)
    {
        return new SchemaRegistryCatalog(type, config, aliases, schema);
    }

    protected AbstractSchemaRegistryCatalogFactorySpi(
        String type,
        Supplier<URL> schema)
    {
        this(type, ALIASES_DEFAULT, schema);
    }

    protected AbstractSchemaRegistryCatalogFactorySpi(
        String type,
        Set<String> aliases,
        Supplier<URL> schema)
    {
        this.type = type;
        this.aliases = aliases;
        this.schema = schema;
    }
}
