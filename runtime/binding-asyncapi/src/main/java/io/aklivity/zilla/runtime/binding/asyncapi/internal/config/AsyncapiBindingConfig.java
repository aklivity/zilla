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

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttAuthorizationConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;

public final class AsyncapiBindingConfig
{
    public final long id;
    public final String namespace;
    public final String qname;
    public final String qvault;
    public final KindConfig kind;
    public final AsyncapiOptionsConfig options;
    public final List<AsyncapiRouteConfig> routes;
    public final List<MetricRefConfig> metricRefs;

    public final ToLongFunction<String> resolveId;
    public final LongFunction<CatalogHandler> supplyCatalog;
    public final ToIntFunction<String> supplyTypeId;

    public transient AsyncapiCompositeConfig composite;

    public AsyncapiBindingConfig(
        EngineContext context,
        BindingConfig binding)
    {
        this.id = binding.id;
        this.namespace = binding.namespace;
        this.qname = binding.qname;
        this.qvault = binding.qvault;
        this.kind = binding.kind;
        this.options = (AsyncapiOptionsConfig) binding.options;
        this.routes = binding.routes.stream()
                .map(AsyncapiRouteConfig::new)
                .collect(toList());
        this.metricRefs =
            binding.telemetryRef != null && binding.telemetryRef.metricRefs != null
                ? binding.telemetryRef.metricRefs
                : List.of();

        this.resolveId = binding.resolveId;
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

            if (options.mqtt != null)
            {
                final MqttAuthorizationConfig authorization = options.mqtt.authorization;
                if (authorization != null)
                {
                    final long namespacedId = binding.resolveId.applyAsLong(authorization.name);
                    authorization.qname = context.supplyQName(namespacedId);
                }
            }
        }
    }

    public AsyncapiRouteConfig resolve(
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
