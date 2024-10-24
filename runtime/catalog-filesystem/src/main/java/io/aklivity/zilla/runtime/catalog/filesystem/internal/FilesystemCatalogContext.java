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
package io.aklivity.zilla.runtime.catalog.filesystem.internal;

import io.aklivity.zilla.runtime.catalog.filesystem.internal.config.FilesystemOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;

public class FilesystemCatalogContext implements CatalogContext
{
    private final EngineContext context;

    public FilesystemCatalogContext(
        EngineContext context)
    {
        this.context = context;
    }

    @Override
    public CatalogHandler attach(
        CatalogConfig catalog)
    {
        return new FilesystemCatalogHandler(
            FilesystemOptionsConfig.class.cast(catalog.options), context, catalog.id);
    }
}
