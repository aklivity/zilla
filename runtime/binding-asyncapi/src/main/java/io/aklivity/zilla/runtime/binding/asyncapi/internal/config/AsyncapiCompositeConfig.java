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

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2LongHashMap;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiOperationView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiView;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;

public final class AsyncapiCompositeConfig
{
    public final List<AsyncapiCompositeRouteConfig> routes;
    public final List<NamespaceConfig> namespaces;

    private final LongFunction<String> resolveLabel;
    private final ToLongFunction<String> resolveSchemaId;

    private Long2ObjectHashMap<AsyncapiOperationView> operationsById;
    private Long2ObjectHashMap<AsyncapiView> specificationsById;

    public AsyncapiCompositeConfig(
        List<AsyncapiSchemaConfig> schemas,
        List<NamespaceConfig> namespaces)
    {
        this(schemas, namespaces, List.of());
    }

    public AsyncapiCompositeConfig(
        List<AsyncapiSchemaConfig> schemas,
        List<NamespaceConfig> namespaces,
        List<AsyncapiCompositeRouteConfig> routes)
    {
        this.routes = routes;
        this.namespaces = namespaces;

        final Long2ObjectHashMap<String> labelsBySchemaId = new Long2ObjectHashMap<>();
        schemas.forEach(s -> labelsBySchemaId.put(s.schemaId, s.apiLabel));
        this.resolveLabel = labelsBySchemaId::get;

        final Object2LongHashMap<String> schemaIdsByLabel = new Object2LongHashMap<>(NO_SCHEMA_ID);
        schemas.forEach(s -> schemaIdsByLabel.put(s.apiLabel, s.schemaId));
        this.resolveSchemaId = schemaIdsByLabel::get;

        this.operationsById = schemas.stream()
            .map(s -> s.asyncapi)
            .flatMap(v -> v.operations.values().stream())
            .collect(toMap(o -> o.compositeId, o -> o, (o1, o2) -> o1, Long2ObjectHashMap::new));

        this.specificationsById = schemas.stream()
            .map(s -> s.asyncapi)
            .collect(toMap(v -> v.compositeId, v -> v, (v1, v2) -> v1, Long2ObjectHashMap::new));
    }

    public boolean hasBindingId(
        long bindingId)
    {
        return namespaces.stream()
                .mapToInt(n -> n.id)
                .anyMatch(id -> id == NamespacedId.namespaceId(bindingId));
    }

    public AsyncapiCompositeRouteConfig resolve(
        long authorization,
        long apiId,
        int operationTypeId)
    {
        return routes.stream()
                .filter(r -> r.matches(apiId, operationTypeId))
                .findFirst()
                .orElse(null);
    }

    public String resolveApiId(
        long apiId)
    {
        return resolveLabel.apply(apiId);
    }

    public long resolveApiId(
        String apiId)
    {
        return resolveSchemaId.applyAsLong(apiId);
    }

    public AsyncapiOperationView resolveOperation(
        long compositeId)
    {
        return operationsById.get(compositeId);
    }

    public AsyncapiView resolveSpecification(
        long compositeId)
    {
        return specificationsById.get(compositeId);
    }
}
