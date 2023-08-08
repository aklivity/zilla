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

public final class GuardConfigBuilder<T> implements ConfigBuilder<T>
{
    private final Function<GuardConfig, T> mapper;

    private String name;
    private String type;
    private OptionsConfig options;

    GuardConfigBuilder(
        Function<GuardConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public GuardConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public GuardConfigBuilder<T> type(
        String type)
    {
        this.type = type;
        return this;
    }

    public <B extends ConfigBuilder<GuardConfigBuilder<T>>> B options(
        Function<GuardConfigBuilder<T>, B> options)
    {
        return options.apply(this);
    }

    public GuardConfigBuilder<T> options(
        OptionsConfig options)
    {
        this.options = options;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new GuardConfig(name, type, options));
    }
}
