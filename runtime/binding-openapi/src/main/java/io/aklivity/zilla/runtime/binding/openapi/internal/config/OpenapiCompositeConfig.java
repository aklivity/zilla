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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;
import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.IntStream;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2LongHashMap;

import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSchemaConfig;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiServerView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiView;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;

public final class OpenapiCompositeConfig
{
    public final List<OpenapiCompositeRouteConfig> routes;
    public final List<NamespaceConfig> namespaces;

    private final LongFunction<String> resolveLabel;
    private final ToLongFunction<String> resolveSchemaId;

    private Long2ObjectHashMap<OpenapiOperationView> operationsById;
    private Long2ObjectHashMap<OpenapiView> specificationsById;
    private Long2ObjectHashMap<OpenapiServerView> serversById;

    public OpenapiCompositeConfig(
        List<OpenapiSchemaConfig> schemas,
        List<NamespaceConfig> namespaces)
    {
        this(schemas, namespaces, List.of());
    }

    public OpenapiCompositeConfig(
        List<OpenapiSchemaConfig> schemas,
        List<NamespaceConfig> namespaces,
        List<OpenapiCompositeRouteConfig> routes)
    {
        this.routes = routes;
        this.namespaces = namespaces;

        final Long2ObjectHashMap<String> labelsBySchemaId = new Long2ObjectHashMap<>();
        schemas.forEach(s -> labelsBySchemaId.put(s.schemaId, s.specLabel));
        this.resolveLabel = labelsBySchemaId::get;

        final Object2LongHashMap<String> schemaIdsByLabel = new Object2LongHashMap<>(NO_SCHEMA_ID);
        schemas.forEach(s -> schemaIdsByLabel.put(s.specLabel, s.schemaId));
        this.resolveSchemaId = schemaIdsByLabel::get;

        this.operationsById = schemas.stream()
            .map(s -> s.openapi)
            .flatMap(v -> v.operations.values().stream())
            .flatMap(o -> operationCompositeIds(o).stream().map(id -> Map.entry(id, o)))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (o1, o2) -> o1, Long2ObjectHashMap::new));

        this.specificationsById = schemas.stream()
            .map(s -> s.openapi)
            .collect(toMap(v -> v.compositeId, v -> v, (v1, v2) -> v1, Long2ObjectHashMap::new));

        this.serversById = schemas.stream()
            .map(s -> s.openapi)
            .flatMap(v -> v.operations.values().stream())
            .filter(o -> o.servers != null)
            .flatMap(o -> IntStream.range(0, o.servers.size())
                .mapToObj(i -> Map.entry(o.compositeId(i + 1), o.servers.get(i))))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (s1, s2) -> s1, Long2ObjectHashMap::new));
    }

    private static List<Long> operationCompositeIds(
        OpenapiOperationView operation)
    {
        final List<Long> ids = new ArrayList<>();
        ids.add(operation.compositeId);
        if (operation.servers != null)
        {
            IntStream.range(0, operation.servers.size())
                .forEach(i -> ids.add(operation.compositeId(i + 1)));
        }
        return ids;
    }

    public boolean hasBindingId(
        long bindingId)
    {
        return namespaces.stream()
                .mapToInt(n -> n.id)
                .anyMatch(id -> id == NamespacedId.namespaceId(bindingId));
    }

    public OpenapiCompositeRouteConfig resolve(
        long authorization,
        long specId,
        int operationTypeId)
    {
        return routes.stream()
                .filter(r -> r.matches(specId, operationTypeId))
                .findFirst()
                .orElse(null);
    }

    public String resolveSpecLabel(
        long specId)
    {
        return resolveLabel.apply(specId);
    }

    public long resolveSpecId(
        String specLabel)
    {
        return resolveSchemaId.applyAsLong(specLabel);
    }

    public OpenapiOperationView resolveOperation(
        long compositeId)
    {
        return operationsById.get(compositeId);
    }

    public OpenapiView resolveSpecification(
        long compositeId)
    {
        return specificationsById.get(compositeId);
    }

    public OpenapiServerView resolveServer(
        long compositeId)
    {
        return serversById.get(compositeId);
    }
}
