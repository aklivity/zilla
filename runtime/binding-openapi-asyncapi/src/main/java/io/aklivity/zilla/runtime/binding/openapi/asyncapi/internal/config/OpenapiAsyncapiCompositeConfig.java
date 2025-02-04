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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.function.ToLongFunction;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2LongHashMap;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;

public final class OpenapiAsyncapiCompositeConfig
{
    public final List<OpenapiAsyncapiCompositeRouteConfig> routes;
    public final List<NamespaceConfig> namespaces;

    private final ToLongFunction<String> resolveSchemaId;

    private Long2ObjectHashMap<OpenapiOperationView> operationsById;

    public OpenapiAsyncapiCompositeConfig(
        List<OpenapiSchemaConfig> openapis,
        List<AsyncapiSchemaConfig> asyncapis,
        List<NamespaceConfig> namespaces,
        List<OpenapiAsyncapiCompositeRouteConfig> routes)
    {
        this.routes = routes;
        this.namespaces = namespaces;

        final Object2LongHashMap<String> schemaIdsByLabel = new Object2LongHashMap<>(NO_SCHEMA_ID);
        asyncapis.forEach(s -> schemaIdsByLabel.put(s.apiLabel, s.schemaId));
        this.resolveSchemaId = schemaIdsByLabel::get;

        this.operationsById = openapis.stream()
            .map(s -> s.openapi)
            .flatMap(v -> v.operations.values().stream())
            .collect(toMap(o -> o.compositeId, o -> o, (o1, o2) -> o1, Long2ObjectHashMap::new));
    }

    public boolean hasBindingId(
        long bindingId)
    {
        return namespaces.stream()
                .mapToInt(n -> n.id)
                .anyMatch(id -> id == NamespacedId.namespaceId(bindingId));
    }

    public OpenapiAsyncapiCompositeRouteConfig resolve(
        long authorization,
        long apiId,
        int operationTypeId)
    {
        return routes.stream()
                .filter(r -> r.matches(apiId, operationTypeId))
                .findFirst()
                .orElse(null);
    }

    public long resolveApiId(
        String apiId)
    {
        return resolveSchemaId.applyAsLong(apiId);
    }

    public OpenapiOperationView resolveOperation(
        long compositeId)
    {
        return operationsById.get(compositeId);
    }
}
