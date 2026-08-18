/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.catalog.filesystem;

import java.util.function.Function;

import io.aklivity.zilla.config.catalog.filesystem.internal.FilesystemCatalogInfo;
import io.aklivity.zilla.config.engine.CatalogConfig;
import io.aklivity.zilla.config.engine.CatalogConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class FilesystemCatalogConfigBuilder<T> extends CatalogConfigBuilder<T, FilesystemCatalogConfigBuilder<T>>
{
    FilesystemCatalogConfigBuilder(
        Function<CatalogConfig, T> mapper)
    {
        super(mapper);
        type(FilesystemCatalogInfo.TYPE);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<FilesystemCatalogConfigBuilder<T>> thisType()
    {
        return (Class<FilesystemCatalogConfigBuilder<T>>) getClass();
    }

    public FilesystemOptionsConfigBuilder<FilesystemCatalogConfigBuilder<T>> options()
    {
        return new FilesystemOptionsConfigBuilder<>(this::options);
    }

    @Override
    protected CatalogConfig newCatalog(
        String namespace,
        String name,
        String type,
        String vault,
        OptionsConfig options)
    {
        return new FilesystemCatalogConfig(namespace, name, type, vault, options);
    }
}
