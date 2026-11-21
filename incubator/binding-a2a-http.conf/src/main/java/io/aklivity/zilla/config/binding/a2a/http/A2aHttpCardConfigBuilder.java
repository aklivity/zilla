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
package io.aklivity.zilla.config.binding.a2a.http;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class A2aHttpCardConfigBuilder<T> extends ConfigBuilder<T, A2aHttpCardConfigBuilder<T>>
{
    private static final String VERSION_DEFAULT = "1.0.0";
    private static final List<String> INPUT_MODES_DEFAULT = List.of("text/plain", "application/json");
    private static final List<String> OUTPUT_MODES_DEFAULT = List.of("application/json", "text/plain");

    private final Function<A2aHttpCardConfig, T> mapper;

    private String name;
    private String description;
    private String version;
    private List<String> defaultInputModes;
    private List<String> defaultOutputModes;

    A2aHttpCardConfigBuilder(
        Function<A2aHttpCardConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aHttpCardConfigBuilder<T>> thisType()
    {
        return (Class<A2aHttpCardConfigBuilder<T>>) getClass();
    }

    public A2aHttpCardConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public A2aHttpCardConfigBuilder<T> description(
        String description)
    {
        this.description = description;
        return this;
    }

    public A2aHttpCardConfigBuilder<T> version(
        String version)
    {
        this.version = version;
        return this;
    }

    public A2aHttpCardConfigBuilder<T> defaultInputModes(
        List<String> defaultInputModes)
    {
        this.defaultInputModes = defaultInputModes;
        return this;
    }

    public A2aHttpCardConfigBuilder<T> defaultOutputModes(
        List<String> defaultOutputModes)
    {
        this.defaultOutputModes = defaultOutputModes;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new A2aHttpCardConfig(
            name,
            description,
            version != null ? version : VERSION_DEFAULT,
            defaultInputModes != null ? defaultInputModes : INPUT_MODES_DEFAULT,
            defaultOutputModes != null ? defaultOutputModes : OUTPUT_MODES_DEFAULT));
    }
}
