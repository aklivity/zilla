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

import static java.util.stream.Collector.of;
import static java.util.stream.Collector.Characteristics.IDENTITY_FINISH;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.util.List;

import org.agrona.collections.IntHashSet;
import org.agrona.collections.Long2LongHashMap;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;

public final class AsyncapiBindingConfig
{
    public final long id;
    public final String name;
    public final KindConfig kind;
    public final AsyncapiOptionsConfig options;
    public final List<AsyncapiRouteConfig> routes;
    private final IntHashSet composites;
    private final long overrideRouteId;
    private final Long2LongHashMap resolvedIds;

    public AsyncapiBindingConfig(
        BindingConfig binding,
        long overrideRouteId)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.overrideRouteId = overrideRouteId;
        this.options = AsyncapiOptionsConfig.class.cast(binding.options);
        this.routes = binding.routes.stream().map(AsyncapiRouteConfig::new).collect(toList());
        this.resolvedIds = binding.composites.stream()
            .map(c -> c.bindings)
            .flatMap(List::stream)
            .filter(b -> b.type.equals("mqtt"))
            .collect(of(
                () -> new Long2LongHashMap(-1),
                (m, r) -> m.put(0L, r.id), //TODO: populate proper apiId
                (m, r) -> m,
                IDENTITY_FINISH
            ));
        this.composites = binding.composites.stream()
            .map(c -> c.bindings)
            .flatMap(List::stream)
            .filter(b -> b.type.equals("mqtt"))
            .map(b -> NamespacedId.namespaceId(b.id))
            .collect(toCollection(IntHashSet::new));
    }

    public boolean isCompositeOriginId(
        long originId)
    {
        return composites.contains(NamespacedId.namespaceId(originId));
    }

    public long resolveResolvedId(
        long apiId)
    {
        return overrideRouteId != -1 ? overrideRouteId : resolvedIds.get(apiId);
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
