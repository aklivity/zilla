/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.config;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class CatalogRefConfigBuilder<T> extends ConfigBuilder<T, CatalogRefConfigBuilder<T>>
{
    private final Function<CatalogRefConfig, T> mapper;

    private List<CatalogedConfig> catalogs;

    CatalogRefConfigBuilder(
        Function<CatalogRefConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<CatalogRefConfigBuilder<T>> thisType()
    {
        return (Class<CatalogRefConfigBuilder<T>>) getClass();
    }

    public CatalogedConfigBuilder<CatalogRefConfigBuilder<T>> cataloged()
    {
        return CatalogedConfig.builder(this::cataloged);
    }

    public CatalogRefConfigBuilder<T> cataloged(
        CatalogedConfig catalog)
    {
        if (catalogs == null)
        {
            catalogs = new LinkedList<>();
        }
        catalogs.add(catalog);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new CatalogRefConfig(catalogs));
    }
}
