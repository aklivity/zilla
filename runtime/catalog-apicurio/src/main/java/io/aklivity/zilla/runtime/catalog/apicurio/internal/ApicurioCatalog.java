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
package io.aklivity.zilla.runtime.catalog.apicurio.internal;

import java.net.URL;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.Catalog;
import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;

public class ApicurioCatalog implements Catalog
{
    public static final String TYPE = "apicurio-registry";
    public static final Set<String> TYPE_ALIASES = Set.of("apicurio");

    private final ConcurrentMap<Long, ApicurioCache> cache;

    public ApicurioCatalog(
        Configuration config)
    {
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public String name()
    {
        return TYPE;
    }

    @Override
    public Set<String> aliases()
    {
        return TYPE_ALIASES;
    }

    @Override
    public CatalogContext supply(
        EngineContext context)
    {
        return new ApicurioCatalogContext(context, cache);
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/apicurio.schema.patch.json");
    }
}
