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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class AsyncapiSpecificationConfigBuilder<T> extends ConfigBuilder<T, AsyncapiSpecificationConfigBuilder<T>>
{
    private final Function<AsyncapiSpecificationConfig, T> mapper;

    private String label;
    private List<AsyncapiServerConfig> servers;
    private List<AsyncapiCatalogConfig> catalogs;

    public AsyncapiSpecificationConfigBuilder<T> label(
        String label)
    {
        this.label = label;
        return this;
    }

    public AsyncapiServerConfigBuilder<AsyncapiSpecificationConfigBuilder<T>> server()
    {
        return new AsyncapiServerConfigBuilder<>(this::server);
    }

    public AsyncapiSpecificationConfigBuilder<T> server(
        AsyncapiServerConfig server)
    {
        if (servers == null)
        {
            servers = new LinkedList<>();
        }

        servers.add(server);
        return this;
    }

    public AsyncapiCatalogConfigBuilder<AsyncapiSpecificationConfigBuilder<T>> catalog()
    {
        return new AsyncapiCatalogConfigBuilder<>(this::catalog);
    }

    public AsyncapiSpecificationConfigBuilder<T> catalog(
        AsyncapiCatalogConfig catalog)
    {
        if (catalogs == null)
        {
            catalogs = new LinkedList<>();
        }

        catalogs.add(catalog);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(
            new AsyncapiSpecificationConfig(label, servers, catalogs));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AsyncapiSpecificationConfigBuilder<T>> thisType()
    {
        return (Class<AsyncapiSpecificationConfigBuilder<T>>) getClass();
    }

    AsyncapiSpecificationConfigBuilder(
        Function<AsyncapiSpecificationConfig, T> mapper)
    {
        this.mapper = mapper;
    }
}
