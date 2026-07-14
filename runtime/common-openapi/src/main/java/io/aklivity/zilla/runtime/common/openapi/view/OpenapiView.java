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
package io.aklivity.zilla.runtime.common.openapi.view;

import static io.aklivity.zilla.runtime.common.openapi.view.OpenapiCompositeId.compositeId;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

import org.agrona.collections.MutableInteger;

import io.aklivity.zilla.runtime.common.openapi.model.Openapi;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiServer;
import io.aklivity.zilla.runtime.common.openapi.model.resolver.OpenapiResolver;

public final class OpenapiView
{
    public final String label;
    public final long compositeId;

    public final OpenapiComponentsView components;
    public final Map<String, OpenapiPathView> paths;
    public final List<OpenapiServerView> servers;
    public final Map<String, OpenapiOperationView> operations;
    public final List<List<OpenapiSecurityRequirementView>> security;

    private final OpenapiResolver resolver;
    private final Map<String, Object> extensions;

    public static OpenapiView of(
        Openapi model)
    {
        return of(0, null, model);
    }

    public static OpenapiView of(
        int id,
        String label,
        Openapi model)
    {
        return new OpenapiView(id, label, model, new OpenapiResolver(model));
    }

    private OpenapiView(
        int id,
        String label,
        Openapi model,
        OpenapiResolver resolver)
    {
        this.label = label;
        this.compositeId = compositeId(id, 0);
        this.resolver = resolver;
        this.extensions = model.extensions;

        List<OpenapiServer> declaredServers = model.servers != null && !model.servers.isEmpty()
            ? model.servers
            : List.of(OpenapiServerView.defaultServer());
        this.servers = declaredServers.stream()
            .map(s -> new OpenapiServerView(resolver, s))
            .toList();

        this.security = model.security != null
            ? model.security.stream()
                .map(s -> s.entrySet().stream()
                    .map(e -> new OpenapiSecurityRequirementView(e.getKey(), e.getValue()))
                    .toList())
                .toList()
            : null;

        this.components = model.components != null
            ? new OpenapiComponentsView(resolver, model.components)
            : null;

        MutableInteger opIndex = new MutableInteger(1);
        LongSupplier supplyCompositeId = () -> compositeId(id, opIndex.value++);
        this.paths = model.paths != null
            ? model.paths.entrySet().stream()
                .map(e -> new OpenapiPathView(this, supplyCompositeId, resolver, e.getKey(), e.getValue()))
                .collect(toMap(c -> c.path, identity()))
            : null;

        this.operations = this.paths != null
            ? this.paths.values().stream()
                .flatMap(p -> p.methods.values().stream())
                .filter(o -> o.id != null)
                .collect(toMap(o -> o.id, identity()))
            : null;
    }

    public Collection<String> unresolvedRefs()
    {
        return resolver.unresolvedRefs();
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
}
