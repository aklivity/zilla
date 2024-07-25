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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.view;

import static io.aklivity.zilla.runtime.binding.asyncapi.internal.config.composite.AsyncapiCompositeId.compositeId;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.agrona.collections.MutableInteger;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiServerConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.resolver.AsyncapiResolver;

public final class AsyncapiView
{
    public final String label;
    public final List<AsyncapiServerView> servers;
    public final List<AsyncapiChannelView> channels;
    public final Map<String, AsyncapiOperationView> operations;

    public boolean hasProtocol(
        String protocol)
    {
        return hasProtocol(protocol::equals);
    }

    public boolean hasProtocol(
        Predicate<String> protocol)
    {
        return servers.stream().map(s -> s.name).anyMatch(protocol);
    }

    public boolean hasOperationBindingsSse()
    {
        return operations.values().stream().anyMatch(AsyncapiOperationView::hasBindingsSse);
    }

    public static AsyncapiView of(
        Asyncapi model)
    {
        return of(0, null, model, List.of());
    }

    public static AsyncapiView of(
        int index,
        String label,
        Asyncapi model,
        List<AsyncapiServerConfig> configs)
    {
        return new AsyncapiView(index, label, model, configs);
    }

    private AsyncapiView(
        int id,
        String label,
        Asyncapi asyncapi,
        List<AsyncapiServerConfig> configs)
    {
        this.label = label;

        AsyncapiResolver resolver = new AsyncapiResolver(asyncapi);

        this.servers = asyncapi.servers.entrySet().stream()
            .flatMap(e -> configs.stream().map(c -> new AsyncapiServerView(resolver, e.getKey(), e.getValue(), c)))
            .toList();

        this.channels = asyncapi.channels.entrySet().stream()
            .map(e -> new AsyncapiChannelView(resolver, e.getKey(), e.getValue()))
            .toList();

        MutableInteger opIndex = new MutableInteger(1);
        this.operations = new TreeMap<>(asyncapi.operations).entrySet().stream()
            .collect(toMap(Map.Entry::getKey,
                e -> new AsyncapiOperationView(this, compositeId(id, opIndex.value++), resolver, e.getKey(), e.getValue())));
    }
}
