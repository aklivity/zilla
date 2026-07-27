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

public abstract class VaultConfigBuilder<T, B extends VaultConfigBuilder<T, B>> extends ConfigBuilder<T, B>
{
    private final Function<VaultConfig, T> mapper;

    private String name;
    private String type;
    private OptionsConfig options;

    private String namespace;

    protected VaultConfigBuilder(
        Function<VaultConfig, T> mapper)
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

    protected B type(
        String type)
    {
        this.type = requireNonNull(type);
        return thisType().cast(this);
    }

    protected <C extends ConfigBuilder<B, C>> C options(
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
        return mapper.apply(newVault(namespace, name, type, options));
    }

    protected abstract VaultConfig newVault(
        String namespace,
        String name,
        String type,
        OptionsConfig options);
}
