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

import io.aklivity.zilla.runtime.catalog.karapace.common.KarapaceCatalogHandler;
import io.aklivity.zilla.runtime.catalog.karapace.common.config.KarapaceOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;

public class KarapaceCatalogContext implements CatalogContext
{
    private final EngineContext context;

    public KarapaceCatalogContext(
        EngineContext context)
    {
        this.context = context;
    }

    @Override
    public CatalogHandler attach(
        CatalogConfig catalog)
    {
        return new KarapaceCatalogHandler(KarapaceOptionsConfig.class.cast(catalog.options), context,
            catalog.id, KarapaceCatalog.NAME);
    }
}
