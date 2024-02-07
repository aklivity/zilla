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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.util.List;

import org.agrona.collections.IntHashSet;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;

public final class AsyncapiBindingConfig
{
    public final long id;
    public final String name;
    public final KindConfig kind;
    public final AsyncapiOptionsConfig options;
    public final List<AsyncapiRouteConfig> routes;
    private final IntHashSet mqttOrigins;

    public AsyncapiBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = AsyncapiOptionsConfig.class.cast(binding.options);
        this.routes = binding.routes.stream().map(AsyncapiRouteConfig::new).collect(toList());
        this.mqttOrigins = binding.composites.stream()
            .map(c -> c.bindings)
            .flatMap(List::stream)
            .filter(b -> b.type.equals("mqtt"))
            .map(b -> NamespacedId.namespaceId(b.id))
            .collect(toCollection(IntHashSet::new));
    }

    public boolean isCompositeNamespace(
        int namespaceId)
    {
        return mqttOrigins.contains(namespaceId);
    }

    public AsyncapiRouteConfig resolve(
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }
}
