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
package io.aklivity.zilla.runtime.common.asyncapi.config;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class AsyncapiSpecificationConfigBuilder<T>
{
    private final Function<AsyncapiSpecificationConfig, T> mapper;

    private String label;
    private String server;
    private List<AsyncapiServerConfig> servers;
    private List<AsyncapiCatalogConfig> catalogs;
    private Map<String, String> security;
    private String store;
    private AsyncapiCatalogConfig overlay;

    public AsyncapiSpecificationConfigBuilder<T> label(
        String label)
    {
        this.label = label;
        return this;
    }

    public AsyncapiSpecificationConfigBuilder<T> serverOverride(
        String server)
    {
        this.server = server;
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

    public AsyncapiSpecificationConfigBuilder<T> security(
        String scheme,
        String guard)
    {
        if (security == null)
        {
            security = new LinkedHashMap<>();
        }

        security.put(scheme, guard);
        return this;
    }

    public AsyncapiSpecificationConfigBuilder<T> store(
        String store)
    {
        this.store = store;
        return this;
    }

    public AsyncapiCatalogConfigBuilder<AsyncapiSpecificationConfigBuilder<T>> overlay()
    {
        return new AsyncapiCatalogConfigBuilder<>(this::overlay);
    }

    public AsyncapiSpecificationConfigBuilder<T> overlay(
        AsyncapiCatalogConfig overlay)
    {
        this.overlay = overlay;
        return this;
    }

    public T build()
    {
        return mapper.apply(
            new AsyncapiSpecificationConfig(label, server, servers, catalogs, security, store, overlay));
    }

    AsyncapiSpecificationConfigBuilder(
        Function<AsyncapiSpecificationConfig, T> mapper)
    {
        this.mapper = mapper;
    }
}
