/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.catalog.karapace.internal;

import java.util.concurrent.ConcurrentMap;

import io.aklivity.zilla.runtime.catalog.karapace.config.KarapaceOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;

public class KarapaceCatalogContext implements CatalogContext
{
    private final EngineContext context;
    private final ConcurrentMap<Long, KarapaceCache> cachesById;

    public KarapaceCatalogContext(
        EngineContext context,
        ConcurrentMap<Long, KarapaceCache> cachesById)
    {
        this.context = context;
        this.cachesById = cachesById;
    }

    @Override
    public CatalogHandler attach(
        CatalogConfig catalog)
    {
        KarapaceCache cache = cachesById.computeIfAbsent(catalog.id, id -> new KarapaceCache());
        return new KarapaceCatalogHandler(KarapaceOptionsConfig.class.cast(catalog.options), context, catalog.id, cache);
    }
}
