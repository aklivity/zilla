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
package io.aklivity.zilla.config.catalog.inline;

import java.util.function.Function;

import io.aklivity.zilla.config.catalog.inline.internal.InlineCatalogInfo;
import io.aklivity.zilla.config.engine.CatalogConfig;
import io.aklivity.zilla.config.engine.CatalogConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class InlineCatalogConfigBuilder<T> extends CatalogConfigBuilder<T, InlineCatalogConfigBuilder<T>>
{
    InlineCatalogConfigBuilder(
        Function<CatalogConfig, T> mapper)
    {
        super(mapper);
        type(InlineCatalogInfo.TYPE);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<InlineCatalogConfigBuilder<T>> thisType()
    {
        return (Class<InlineCatalogConfigBuilder<T>>) getClass();
    }

    public InlineOptionsConfigBuilder<InlineCatalogConfigBuilder<T>> options()
    {
        return new InlineOptionsConfigBuilder<>(this::options);
    }

    @Override
    protected CatalogConfig newCatalog(
        String namespace,
        String name,
        String type,
        String vault,
        String guard,
        OptionsConfig options)
    {
        return new InlineCatalogConfig(namespace, name, type, vault, guard, options);
    }
}
