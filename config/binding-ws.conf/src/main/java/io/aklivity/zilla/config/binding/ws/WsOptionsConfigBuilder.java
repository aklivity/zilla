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
package io.aklivity.zilla.config.binding.ws;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class WsOptionsConfigBuilder<T> extends ConfigBuilder<T, WsOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private String protocol;
    private String scheme;
    private String authority;
    private String path;

    WsOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<WsOptionsConfigBuilder<T>> thisType()
    {
        return (Class<WsOptionsConfigBuilder<T>>) getClass();
    }

    public WsOptionsConfigBuilder<T> protocol(
        String protocol)
    {
        this.protocol = protocol;
        return this;
    }

    public WsOptionsConfigBuilder<T> scheme(
        String scheme)
    {
        this.scheme = scheme;
        return this;
    }

    public WsOptionsConfigBuilder<T> authority(
        String authority)
    {
        this.authority = authority;
        return this;
    }

    public WsOptionsConfigBuilder<T> path(
        String path)
    {
        this.path = path;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new WsOptionsConfig(protocol, scheme, authority, path));
    }
}
