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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config;

import static java.util.stream.Collector.of;
import static java.util.stream.Collector.Characteristics.IDENTITY_FINISH;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.util.List;

import org.agrona.collections.IntHashSet;
import org.agrona.collections.Long2LongHashMap;

import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;

public final class OpenapiAsyncapiBindingConfig
{
    public final long id;
    public final String name;
    public final KindConfig kind;
    public final OpenapiAsyncapiOptionsConfig options;
    public final List<OpenapiAsyncapiRouteConfig> routes;

    private final IntHashSet httpKafkaOrigins;
    private final Long2LongHashMap resolvedIds;

    public OpenapiAsyncapiBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = OpenapiAsyncapiOptionsConfig.class.cast(binding.options);

        this.routes = binding.routes.stream()
            .map(r -> new OpenapiAsyncapiRouteConfig(r, options::resolveOpenapiApiId))
            .collect(toList());

        this.resolvedIds = binding.composites.values().stream()
            .map(c -> c.bindings)
            .flatMap(List::stream)
            .filter(b -> b.type.equals("http-kafka"))
            .collect(of(
                () -> new Long2LongHashMap(-1),
                (m, r) -> m.put(options.specs.openapi.stream().findFirst().get().apiId, r.id),
                (m, r) -> m,
                IDENTITY_FINISH
            ));

        this.httpKafkaOrigins = binding.composites.values().stream()
            .map(c -> c.bindings)
            .flatMap(List::stream)
            .filter(b -> b.type.equals("http-kafka"))
            .map(b -> NamespacedId.namespaceId(b.id))
            .collect(toCollection(IntHashSet::new));
    }

    public boolean isCompositeNamespace(
        int namespaceId)
    {
        return httpKafkaOrigins.contains(namespaceId);
    }

    public long resolveResolvedId(
        long apiId)
    {
        return resolvedIds.get(apiId);
    }

    public OpenapiAsyncapiRouteConfig resolve(
        long authorization,
        long apiId)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(apiId))
            .findFirst()
            .orElse(null);
    }
}
