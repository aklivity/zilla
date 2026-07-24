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

import static java.util.Objects.requireNonNull;

import java.util.function.Function;

public abstract class CatalogConfigBuilder<T, B extends CatalogConfigBuilder<T, B>> extends ConfigBuilder<T, B>
{
    private final Function<CatalogConfig, T> mapper;

    private String namespace;
    private String name;
    private String type;
    private String vault;
    private OptionsConfig options;

    protected CatalogConfigBuilder(
        Function<CatalogConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public B namespace(
        String namespace)
    {
        this.namespace = namespace;
        return thisType().cast(this);
    }

    public B name(
        String name)
    {
        this.name = name;
        return thisType().cast(this);
    }

    public B type(
        String type)
    {
        this.type = requireNonNull(type);
        return thisType().cast(this);
    }

    public B vault(
        String vault)
    {
        this.vault = vault;
        return thisType().cast(this);
    }

    public <C extends ConfigBuilder<B, C>> C options(
        Function<Function<OptionsConfig, B>, C> options)
    {
        return options.apply(this::options);
    }

    public B options(
        OptionsConfig options)
    {
        this.options = options;
        return thisType().cast(this);
    }

    @Override
    public T build()
    {
        return mapper.apply(newCatalog(namespace, name, type, vault, options));
    }

    protected abstract CatalogConfig newCatalog(
        String namespace,
        String name,
        String type,
        String vault,
        OptionsConfig options);
}
