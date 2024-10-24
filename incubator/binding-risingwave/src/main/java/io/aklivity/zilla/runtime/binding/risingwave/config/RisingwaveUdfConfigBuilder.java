/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.risingwave.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class RisingwaveUdfConfigBuilder<T> extends ConfigBuilder<T, RisingwaveUdfConfigBuilder<T>>
{
    private static final String LANGUAGE_DEFAULT = "java";

    private final Function<RisingwaveUdfConfig, T> mapper;

    private String server;
    private String language;

    RisingwaveUdfConfigBuilder(
        Function<RisingwaveUdfConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<RisingwaveUdfConfigBuilder<T>> thisType()
    {
        return (Class<RisingwaveUdfConfigBuilder<T>>) getClass();
    }

    public RisingwaveUdfConfigBuilder<T> server(
        String server)
    {
        this.server = server;
        return this;
    }

    public RisingwaveUdfConfigBuilder<T> language(
        String language)
    {
        this.language = language;
        return this;
    }

    public T build()
    {
        String language = this.language != null ? this.language : LANGUAGE_DEFAULT;
        return mapper.apply(new RisingwaveUdfConfig(server, language));
    }
}
