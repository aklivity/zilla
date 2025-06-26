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
package io.aklivity.zilla.runtime.binding.openapi.internal.view;

import static io.aklivity.zilla.runtime.binding.openapi.internal.config.composite.OpenapiCompositeId.compositeId;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

import org.agrona.collections.MutableInteger;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiServerConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver.OpenapiResolver;

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

    public static OpenapiView of(
        Openapi model)
    {
        return of(model, List.of());
    }

    public static OpenapiView of(
        Openapi model,
        List<OpenapiServerConfig> configs)
    {
        return of(0, null, model, configs);
    }

    public static OpenapiView of(
        int id,
        String label,
        Openapi model,
        List<OpenapiServerConfig> configs)
    {
        return new OpenapiView(id, label, model, configs);
    }

    private OpenapiView(
        int id,
        String label,
        Openapi model,
        List<OpenapiServerConfig> configs)
    {
        this.label = label;
        this.compositeId = compositeId(id, 0);

        this.resolver = new OpenapiResolver(model);

        this.servers = model.servers != null
            ? model.servers.stream()
                .flatMap(s -> configs.isEmpty()
                    ? Stream.of(new OpenapiServerView(resolver, s, null))
                    : configs.stream().map(c -> new OpenapiServerView(resolver, s, c)))
                .toList()
            : null;

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
                .map(e -> new OpenapiPathView(this, supplyCompositeId, configs, resolver, e.getKey(), e.getValue()))
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
}
