/*
 * Copyright 2021-2024 Aklivity Inc.
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

public final class StoreConfigBuilder<T> extends ConfigBuilder<T, StoreConfigBuilder<T>>
{
    private final Function<StoreConfig, T> mapper;

    private String namespace;
    private String name;
    private String type;
    private String vault;
    private OptionsConfig options;

    StoreConfigBuilder(
        Function<StoreConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<StoreConfigBuilder<T>> thisType()
    {
        return (Class<StoreConfigBuilder<T>>) getClass();
    }

    public StoreConfigBuilder<T> namespace(
        String namespace)
    {
        this.namespace = namespace;
        return this;
    }

    public StoreConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public StoreConfigBuilder<T> type(
        String type)
    {
        this.type = type;
        return this;
    }

    public StoreConfigBuilder<T> vault(
        String vault)
    {
        this.vault = vault;
        return this;
    }

    public <C extends ConfigBuilder<StoreConfigBuilder<T>, C>> C options(
        Function<Function<OptionsConfig, StoreConfigBuilder<T>>, C> options)
    {
        return options.apply(this::options);
    }

    public StoreConfigBuilder<T> options(
        OptionsConfig options)
    {
        this.options = options;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new StoreConfig(namespace, name, type, vault, options));
    }
}
