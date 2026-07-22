/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.config.engine;

import java.util.function.Function;

public final class RouterConfigBuilder<T> extends ConfigBuilder<T, RouterConfigBuilder<T>>
{
    private final Function<RouterConfig, T> mapper;

    private long id;
    private String name;
    private OptionsConfig options;

    RouterConfigBuilder(
        Function<RouterConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<RouterConfigBuilder<T>> thisType()
    {
        return (Class<RouterConfigBuilder<T>>) getClass();
    }

    public RouterConfigBuilder<T> id(
        long id)
    {
        this.id = id;
        return this;
    }

    public RouterConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public <C extends ConfigBuilder<RouterConfigBuilder<T>, C>> C options(
        Function<Function<OptionsConfig, RouterConfigBuilder<T>>, C> options)
    {
        return options.apply(this::options);
    }

    public RouterConfigBuilder<T> options(
        OptionsConfig options)
    {
        this.options = options;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new RouterConfig(id, name, options));
    }
}
