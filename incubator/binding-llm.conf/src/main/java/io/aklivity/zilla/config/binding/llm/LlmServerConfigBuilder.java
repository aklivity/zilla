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
package io.aklivity.zilla.config.binding.llm;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class LlmServerConfigBuilder<T> extends ConfigBuilder<T, LlmServerConfigBuilder<T>>
{
    private final Function<LlmServerConfig, T> mapper;
    private String scheme;
    private String host;
    private int port;
    private String path;

    LlmServerConfigBuilder(
        Function<LlmServerConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<LlmServerConfigBuilder<T>> thisType()
    {
        return (Class<LlmServerConfigBuilder<T>>) getClass();
    }

    public LlmServerConfigBuilder<T> scheme(
        String scheme)
    {
        this.scheme = scheme;
        return this;
    }

    public LlmServerConfigBuilder<T> host(
        String host)
    {
        this.host = host;
        return this;
    }

    public LlmServerConfigBuilder<T> port(
        int port)
    {
        this.port = port;
        return this;
    }

    public LlmServerConfigBuilder<T> path(
        String path)
    {
        this.path = path;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new LlmServerConfig(scheme, host, port, path));
    }
}
