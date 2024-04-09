/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class AsyncapiServerConfigBuilder<T> extends ConfigBuilder<T, AsyncapiServerConfigBuilder<T>>
{
    private final Function<AsyncapiServerConfig, T> mapper;

    private String name;
    private String host;
    private String url;
    private String pathname;
    AsyncapiServerConfigBuilder(
        Function<AsyncapiServerConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AsyncapiServerConfigBuilder<T>> thisType()
    {
        return (Class<AsyncapiServerConfigBuilder<T>>) getClass();
    }


    public AsyncapiServerConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public AsyncapiServerConfigBuilder<T> host(
        String host)
    {
        this.host = host;
        return this;
    }

    public AsyncapiServerConfigBuilder<T> url(
        String url)
    {
        this.url = url;
        return this;
    }

    public AsyncapiServerConfigBuilder<T> pathname(
        String pathname)
    {
        this.pathname = pathname;
        return this;
    }


    @Override
    public T build()
    {
        return mapper.apply(
            new AsyncapiServerConfig(name, host, url, pathname));
    }
}
