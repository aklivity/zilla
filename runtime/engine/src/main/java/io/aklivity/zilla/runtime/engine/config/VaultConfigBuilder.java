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

public final class VaultConfigBuilder<T> implements ConfigBuilder<T>
{
    private final Function<VaultConfig, T> mapper;

    private String name;
    private String type;
    private OptionsConfig options;

    VaultConfigBuilder(
        Function<VaultConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public VaultConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public VaultConfigBuilder<T> type(
        String type)
    {
        this.type = requireNonNull(type);
        return this;
    }

    public <B extends ConfigBuilder<VaultConfigBuilder<T>>> B options(
        Function<Function<OptionsConfig, VaultConfigBuilder<T>>, B> options)
    {
        return options.apply(this::options);
    }

    public VaultConfigBuilder<T> options(
        OptionsConfig options)
    {
        this.options = options;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new VaultConfig(name, type, options));
    }
}
