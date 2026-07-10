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

import static io.aklivity.zilla.runtime.common.openapi.view.OpenapiCompositeId.compositeId;
import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiView;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;

public final class OpenapiBindingConfig
{
    public final long id;
    public final String namespace;
    public final String qname;
    public final String qvault;
    public final KindConfig kind;
    public final OpenapiOptionsConfig options;
    public final List<OpenapiRouteConfig> routes;
    public final List<MetricRefConfig> metricRefs;

    public final ToLongFunction<String> resolveId;
    public final LongFunction<CatalogHandler> supplyCatalog;
    public final ToIntFunction<String> supplyTypeId;
    public final ToLongBiFunction<NamespaceConfig, BindingConfig> supplyBindingId;

    public transient OpenapiCompositeConfig composite;

    public OpenapiBindingConfig(
        EngineContext context,
        BindingConfig binding)
    {
        this.id = binding.id;
        this.namespace = binding.namespace;
        this.qname = binding.qname;
        this.qvault = binding.qvault;
        this.kind = binding.kind;
        this.options = (OpenapiOptionsConfig) binding.options;
        this.routes = binding.routes.stream()
                .map(OpenapiRouteConfig::new)
                .collect(toList());
        this.metricRefs =
                binding.telemetryRef != null && binding.telemetryRef.metricRefs != null
                    ? binding.telemetryRef.metricRefs
                    : List.of();

        this.resolveId = binding.resolveId;
        this.supplyBindingId = context::supplyBindingId;
        this.supplyCatalog = context::supplyCatalog;
        this.supplyTypeId = context::supplyTypeId;

        // TODO: move to engine
        if (options != null)
        {
            if (options.http != null)
            {
                final HttpAuthorizationConfig authorization = options.http.authorization;
                if (authorization != null)
                {
                    final long namespacedId = binding.resolveId.applyAsLong(authorization.name);
                    authorization.qname = context.supplyQName(namespacedId);
                }
            }
        }
    }

    public OpenapiRouteConfig resolve(
        long authorization,
        String apiId,
        String operationId)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(apiId, operationId))
            .filter(r -> !r.isBulk() || matchesBulkTarget(r, apiId, operationId))
            .findFirst()
            .orElse(null);
    }

    public String resolveSpecLabel(
        OpenapiRouteConfig route,
        String apiId)
    {
        return route.with != null && route.with.spec != null
            ? route.with.spec
            : apiId;
    }

    public String resolveOperationId(
        OpenapiRouteConfig route,
        String operationId)
    {
        return route.with != null && !route.isBulk()
            ? route.with.operation
            : operationId;
    }

    private boolean matchesBulkTarget(
        OpenapiRouteConfig route,
        String apiId,
        String operationId)
    {
        boolean matches = false;
        OpenapiView specification = resolveSpecification(resolveSpecLabel(route, apiId));

        if (specification != null && specification.operations != null)
        {
            OpenapiOperationView candidate = specification.operations.get(operationId);
            matches = candidate != null && matchesBulk(candidate, route.with);
        }

        return matches;
    }

    private OpenapiView resolveSpecification(
        String label)
    {
        OpenapiView specification = null;
        long apiId = composite.resolveApiId(label);

        if (apiId != NO_SCHEMA_ID)
        {
            specification = composite.resolveSpecification(compositeId((int) apiId, 0));
        }

        return specification;
    }

    private static boolean matchesBulk(
        OpenapiOperationView operation,
        OpenapiWithConfig with)
    {
        boolean matches = true;

        if (with.tag != null)
        {
            matches = operation.tags != null && operation.tags.contains(with.tag);
        }
        else if (with.operation != null)
        {
            matches = compileGlob(with.operation).matcher(operation.id).matches();
        }

        return matches;
    }

    private static Pattern compileGlob(
        String glob)
    {
        StringBuilder regex = new StringBuilder();
        String[] literals = glob.split("\\*", -1);

        for (int index = 0; index < literals.length; index++)
        {
            if (index > 0)
            {
                regex.append(".*");
            }

            if (!literals[index].isEmpty())
            {
                regex.append(Pattern.quote(literals[index]));
            }
        }

        return Pattern.compile(regex.toString());
    }
}
