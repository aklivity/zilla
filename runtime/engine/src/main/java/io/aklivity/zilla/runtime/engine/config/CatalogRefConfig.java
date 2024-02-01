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

import java.util.function.Function;

import org.agrona.collections.ObjectHashSet;

public final class CatalogRefConfig
{
    public final ObjectHashSet<CatalogedConfig> catalogs;

    public CatalogRefConfig(
        ObjectHashSet<CatalogedConfig> catalogs)
    {
        this.catalogs = catalogs;
    }

    public static <T> CatalogRefConfigBuilder<T> builder(
        Function<CatalogRefConfig, T> mapper)
    {
        return new CatalogRefConfigBuilder<>(mapper::apply);
    }

    public static CatalogRefConfigBuilder<CatalogRefConfig> builder()
    {
        return new CatalogRefConfigBuilder<>(CatalogRefConfig.class::cast);
    }
}
