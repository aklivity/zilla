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
package io.aklivity.zilla.config.engine;

import java.util.function.Function;

public final class GenericCatalogConfigBuilder<T> extends CatalogConfigBuilder<T, GenericCatalogConfigBuilder<T>>
{
    GenericCatalogConfigBuilder(
        Function<CatalogConfig, T> mapper)
    {
        super(mapper);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GenericCatalogConfigBuilder<T>> thisType()
    {
        return (Class<GenericCatalogConfigBuilder<T>>) getClass();
    }

    @Override
    protected CatalogConfig newCatalog(
        String namespace,
        String name,
        String type,
        String vault,
        OptionsConfig options)
    {
        return new GenericCatalogConfig(namespace, name, type, vault, options);
    }
}
