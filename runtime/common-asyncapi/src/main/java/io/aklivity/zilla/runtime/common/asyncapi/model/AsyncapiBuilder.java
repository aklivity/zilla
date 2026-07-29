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
package io.aklivity.zilla.runtime.common.asyncapi.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class AsyncapiBuilder<T> extends AsyncapiModelBuilder<T, AsyncapiBuilder<T>>
{
    private final Function<Asyncapi, T> mapper;

    private String asyncapi;
    private AsyncapiInfo info;
    private Map<String, AsyncapiServer> servers;
    private Map<String, AsyncapiChannel> channels;
    private Map<String, AsyncapiOperation> operations;
    private AsyncapiComponents components;

    AsyncapiBuilder(
        Function<Asyncapi, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AsyncapiBuilder<T>> thisType()
    {
        return (Class<AsyncapiBuilder<T>>) getClass();
    }

    public AsyncapiBuilder<T> asyncapi(
        String asyncapi)
    {
        this.asyncapi = asyncapi;
        return this;
    }

    public AsyncapiInfoBuilder<AsyncapiBuilder<T>> info()
    {
        return AsyncapiInfo.builder(this::info);
    }

    public AsyncapiBuilder<T> info(
        AsyncapiInfo info)
    {
        this.info = info;
        return this;
    }

    public AsyncapiServerBuilder<AsyncapiBuilder<T>> server(
        String name)
    {
        return AsyncapiServer.builder(server -> server(name, server));
    }

    public AsyncapiBuilder<T> server(
        String name,
        AsyncapiServer server)
    {
        if (servers == null)
        {
            servers = new LinkedHashMap<>();
        }
        servers.put(name, server);
        return this;
    }

    public AsyncapiBuilder<T> servers(
        Map<String, AsyncapiServer> servers)
    {
        this.servers = servers;
        return this;
    }

    public AsyncapiChannelBuilder<AsyncapiBuilder<T>> channel(
        String name)
    {
        return AsyncapiChannel.builder(channel -> channel(name, channel));
    }

    public AsyncapiBuilder<T> channel(
        String name,
        AsyncapiChannel channel)
    {
        if (channels == null)
        {
            channels = new LinkedHashMap<>();
        }
        channels.put(name, channel);
        return this;
    }

    public AsyncapiBuilder<T> channels(
        Map<String, AsyncapiChannel> channels)
    {
        this.channels = channels;
        return this;
    }

    public AsyncapiOperationBuilder<AsyncapiBuilder<T>> operation(
        String name)
    {
        return AsyncapiOperation.builder(operation -> operation(name, operation));
    }

    public AsyncapiBuilder<T> operation(
        String name,
        AsyncapiOperation operation)
    {
        if (operations == null)
        {
            operations = new LinkedHashMap<>();
        }
        operations.put(name, operation);
        return this;
    }

    public AsyncapiBuilder<T> operations(
        Map<String, AsyncapiOperation> operations)
    {
        this.operations = operations;
        return this;
    }

    public AsyncapiComponentsBuilder<AsyncapiBuilder<T>> components()
    {
        return AsyncapiComponents.builder(this::components);
    }

    public AsyncapiBuilder<T> components(
        AsyncapiComponents components)
    {
        this.components = components;
        return this;
    }

    @Override
    public T build()
    {
        Asyncapi asyncapiDoc = new Asyncapi();
        asyncapiDoc.asyncapi = asyncapi;
        asyncapiDoc.info = info;
        asyncapiDoc.servers = servers;
        asyncapiDoc.channels = channels;
        asyncapiDoc.operations = operations;
        asyncapiDoc.components = components;
        return mapper.apply(asyncapiDoc);
    }
}
