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

import java.util.Map;
import java.util.function.Function;

public final class AsyncapiServerBuilder<T> extends AbstractAsyncapiResolvableBuilder<T, AsyncapiServerBuilder<T>>
{
    private final Function<AsyncapiServer, T> mapper;

    private String host;
    private String protocol;
    private Map<String, Object> bindings;
    private Map<String, Object> extensions;

    AsyncapiServerBuilder(
        Function<AsyncapiServer, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AsyncapiServerBuilder<T>> thisType()
    {
        return (Class<AsyncapiServerBuilder<T>>) getClass();
    }

    public AsyncapiServerBuilder<T> host(
        String host)
    {
        this.host = host;
        return this;
    }

    public AsyncapiServerBuilder<T> protocol(
        String protocol)
    {
        this.protocol = protocol;
        return this;
    }

    public AsyncapiServerBuilder<T> bindings(
        Map<String, Object> bindings)
    {
        this.bindings = bindings;
        return this;
    }

    public AsyncapiServerBuilder<T> extensions(
        Map<String, Object> extensions)
    {
        this.extensions = extensions;
        return this;
    }

    @Override
    public T build()
    {
        AsyncapiServer server = new AsyncapiServer();
        server.ref = ref;
        server.host = host;
        server.protocol = protocol;
        server.bindings = bindings;
        server.extensions = extensions;
        return mapper.apply(server);
    }
}
