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

import java.util.List;
import java.util.Map;

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

    public static OpenapiView of(
        Openapi model)
    {
        return of(0, null, model, List.of());
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

        OpenapiResolver resolver = new OpenapiResolver(model);

        this.servers = model.servers != null
            ? model.servers.stream()
                .flatMap(s -> configs.stream().map(c -> new OpenapiServerView(resolver, s, c)))
                .toList()
            : null;

        this.paths = model.paths != null
            ? model.paths.entrySet().stream()
                .map(e -> new OpenapiPathView(this, configs, resolver, e.getKey(), e.getValue()))
                .collect(toMap(c -> c.path, identity()))
            : null;

        this.components = model.components != null
            ? new OpenapiComponentsView(resolver, model.components)
            : null;
    }
}
