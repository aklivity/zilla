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

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;

public final class OpenapiAsyncapiBindingConfig
{
    public final long id;
    public final String namespace;
    public final String qname;
    public final KindConfig kind;
    public final OpenapiAsyncapiOptionsConfig options;
    public final List<OpenapiAsyncapiRouteConfig> routes;
    public final List<MetricRefConfig> metricRefs;

    public final ToLongFunction<String> resolveId;
    public final LongFunction<CatalogHandler> supplyCatalog;
    public final ToIntFunction<String> supplyTypeId;
    public final ToLongBiFunction<NamespaceConfig, BindingConfig> supplyBindingId;

    public transient OpenapiAsyncapiCompositeConfig composite;

    public OpenapiAsyncapiBindingConfig(
        EngineContext context,
        BindingConfig binding)
    {
        this.id = binding.id;
        this.namespace = binding.namespace;
        this.qname = binding.qname;
        this.kind = binding.kind;
        this.options = (OpenapiAsyncapiOptionsConfig) binding.options;
        this.routes = binding.routes.stream()
            .map(OpenapiAsyncapiRouteConfig::new)
            .collect(toList());
        this.metricRefs =
            binding.telemetryRef != null && binding.telemetryRef.metricRefs != null
                ? binding.telemetryRef.metricRefs
                : List.of();
        this.resolveId = binding.resolveId;
        this.supplyCatalog = context::supplyCatalog;
        this.supplyTypeId = context::supplyTypeId;
        this.supplyBindingId = context::supplyBindingId;
    }

    public OpenapiAsyncapiRouteConfig resolve(
        long authorization,
        String apiId,
        String operationId)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(apiId, operationId))
            .findFirst()
            .orElse(null);
    }
}
