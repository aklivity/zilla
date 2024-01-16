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

import static java.util.Objects.requireNonNull;

import java.util.function.Function;

public final class CatalogConfigBuilder<T> extends ConfigBuilder<T, CatalogConfigBuilder<T>>
{
    private final Function<CatalogConfig, T> mapper;

    private String namespace;
    private String name;
    private String type;
    private OptionsConfig options;

    CatalogConfigBuilder(
        Function<CatalogConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<CatalogConfigBuilder<T>> thisType()
    {
        return (Class<CatalogConfigBuilder<T>>) getClass();
    }

    public CatalogConfigBuilder<T> namespace(
        String namespace)
    {
        this.namespace = namespace;
        return this;
    }

    public CatalogConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public CatalogConfigBuilder<T> type(
        String type)
    {
        this.type = requireNonNull(type);
        return this;
    }

    public <C extends ConfigBuilder<CatalogConfigBuilder<T>, C>> C options(
            Function<Function<OptionsConfig, CatalogConfigBuilder<T>>, C> options)
    {
        return options.apply(this::options);
    }

    public CatalogConfigBuilder<T> options(
            OptionsConfig options)
    {
        this.options = options;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new CatalogConfig(namespace, name, type, options));
    }
}
