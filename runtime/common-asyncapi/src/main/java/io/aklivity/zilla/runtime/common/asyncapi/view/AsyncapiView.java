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
package io.aklivity.zilla.runtime.common.asyncapi.view;

import static io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiCompositeId.compositeId;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.agrona.collections.MutableInteger;

import io.aklivity.zilla.runtime.common.asyncapi.model.Asyncapi;
import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiMultiFormatSchema;
import io.aklivity.zilla.runtime.common.asyncapi.model.resolver.AsyncapiResolver;

public final class AsyncapiView
{
    public final String label;
    public final long compositeId;
    public final List<AsyncapiServerView> servers;
    public final Map<String, AsyncapiChannelView> channels;
    public final Map<String, AsyncapiOperationView> operations;
    public final AsyncapiComponentsView components;

    private final AsyncapiResolver resolver;
    private final Map<String, Object> extensions;

    public boolean hasExtension(
        String name)
    {
        return extensions != null && extensions.containsKey(name);
    }

    public <T> Optional<T> extension(
        String name,
        Class<T> type)
    {
        return Optional.ofNullable(extensions != null ? type.cast(extensions.get(name)) : null);
    }

    public boolean hasProtocol(
        String protocol)
    {
        return hasProtocol(protocol::equals);
    }

    public boolean hasProtocol(
        Predicate<String> protocol)
    {
        return servers.stream()
            .map(s -> s.url)
            .filter(Objects::nonNull)
            .map(URI::getScheme)
            .filter(Objects::nonNull)
            .anyMatch(protocol);
    }

    public boolean hasOperationBindingsHttp()
    {
        return operations.values().stream().anyMatch(o -> o.hasBinding("http"));
    }

    public boolean hasOperationBindingsSse()
    {
        return operations.values().stream().anyMatch(o -> o.hasBinding("x-zilla-sse"));
    }

    public AsyncapiMultiFormatSchemaView resolveSchema(
        AsyncapiMultiFormatSchema schema)
    {
        return new AsyncapiMultiFormatSchemaView(resolver, schema);
    }

    public static AsyncapiView of(
        Asyncapi model)
    {
        return of(0, null, model);
    }

    public static AsyncapiView of(
        int index,
        String label,
        Asyncapi model)
    {
        return new AsyncapiView(index, label, model);
    }

    private AsyncapiView(
        int id,
        String label,
        Asyncapi asyncapi)
    {
        this.label = label;
        this.compositeId = compositeId(id, 0);

        this.resolver = new AsyncapiResolver(asyncapi);
        this.extensions = asyncapi.extensions;

        this.servers = asyncapi.servers != null
            ? asyncapi.servers.entrySet().stream()
                .map(e -> new AsyncapiServerView(resolver, e.getKey(), e.getValue()))
                .toList()
            : null;

        this.channels = asyncapi.channels != null
            ? asyncapi.channels.entrySet().stream()
                .map(e -> new AsyncapiChannelView(resolver, this.servers, e.getKey(), e.getValue()))
                .collect(toMap(c -> c.name, identity()))
            : null;

        MutableInteger opIndex = new MutableInteger(1);
        this.operations = asyncapi.operations != null
            ? new TreeMap<>(asyncapi.operations).entrySet().stream()
                .collect(toMap(Map.Entry::getKey,
                    e -> new AsyncapiOperationView(this, compositeId(id, opIndex.value++), resolver, e.getKey(), e.getValue())))
            : null;

        this.components = asyncapi.components != null
            ? new AsyncapiComponentsView(resolver, asyncapi.components)
            : null;
    }

    public Collection<String> unresolvedRefs()
    {
        return resolver.unresolvedRefs();
    }
}
