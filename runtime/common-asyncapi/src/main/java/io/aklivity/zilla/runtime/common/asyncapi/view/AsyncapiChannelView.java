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
package io.aklivity.zilla.runtime.common.asyncapi.view;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiChannel;
import io.aklivity.zilla.runtime.common.asyncapi.model.resolver.AsyncapiResolver;

public final class AsyncapiChannelView
{
    public final String name;
    public final String address;
    public final List<AsyncapiMessageView> messages;
    public final List<AsyncapiParameterView> parameters;
    public final List<AsyncapiServerView> servers;

    private final Map<String, Object> extensions;

    public boolean hasMessages()
    {
        return messages != null && !messages.isEmpty();
    }

    public boolean hasParameters()
    {
        return parameters != null && !parameters.isEmpty();
    }

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

    AsyncapiChannelView(
        AsyncapiResolver resolver,
        List<AsyncapiServerView> specServers,
        AsyncapiChannel model)
    {
        this(resolver, specServers, resolver.channels.resolveRef(model.ref), model);
    }

    AsyncapiChannelView(
        AsyncapiResolver resolver,
        List<AsyncapiServerView> specServers,
        String name,
        AsyncapiChannel model)
    {
        final AsyncapiChannel resolved = resolver.channels.resolve(model);
        final List<AsyncapiServerView> allServers = specServers != null ? specServers : List.of();

        this.name = requireNonNull(name);
        this.address = resolved.address;
        this.messages = resolved.messages != null
            ? resolved.messages.entrySet().stream()
                .map(e -> new AsyncapiMessageView(this, resolver, e.getKey(), e.getValue()))
                .toList()
            : null;
        this.parameters = resolved.parameters != null
            ? resolved.parameters.entrySet().stream()
                .map(e -> new AsyncapiParameterView(this, resolver, e.getKey(), e.getValue()))
                .toList()
            : null;
        if (resolved.servers != null)
        {
            resolved.servers.forEach(resolver.servers::resolve);
        }
        this.servers = resolved.servers != null && !resolved.servers.isEmpty()
            ? resolved.servers.stream()
                .map(s -> resolver.servers.resolveRef(s.ref))
                .filter(Objects::nonNull)
                .flatMap(n -> allServers.stream().filter(s -> n.equals(s.name)))
                .toList()
            : allServers;
        this.extensions = resolved.extensions;
    }
}
