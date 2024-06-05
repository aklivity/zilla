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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.PROXY;
import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;

public class AsyncapiProxyNamespaceGenerator extends AsyncapiNamespaceGenerator
{
    private static final String ASYNCAPI_KAFKA_PROTOCOL_NAME = "kafka";
    private static final String ASYNCAPI_MQTT_PROTOCOL_NAME = "mqtt";
    private static final String ASYNCAPI_HTTP_PROTOCOL_NAME = "http";

    public NamespaceConfig generateProxy(
        BindingConfig binding,
        Map<String, Asyncapi> asyncapis,
        ToLongFunction<String> resolveApiId,
        List<String> labels)
    {
        AsyncapiOptionsConfig options = binding.options != null ? (AsyncapiOptionsConfig) binding.options : EMPTY_OPTION;
        List<AsyncapiRouteConfig> routes = binding.routes.stream()
            .map(r -> new AsyncapiRouteConfig(r, resolveApiId))
            .collect(Collectors.toList());
        this.asyncapis = asyncapis;

        final List<MetricRefConfig> metricRefs = binding.telemetryRef != null ?
            binding.telemetryRef.metricRefs : emptyList();

        final Map<String, List<AsyncapiRouteConfig>> routesByProtocol = new HashMap<>();

        inject:
        for (AsyncapiRouteConfig route : routes)
        {
            final Asyncapi kafkaAsyncapi = asyncapis.get(route.with.apiId);
            if (kafkaAsyncapi.servers.values().stream().anyMatch(s -> !s.protocol.startsWith(ASYNCAPI_KAFKA_PROTOCOL_NAME)))
            {
                break inject;
            }

            for (AsyncapiConditionConfig condition : route.when)
            {
                final Asyncapi asyncapi = asyncapis.get(condition.apiId);
                if (asyncapi.servers.values().stream().anyMatch(s ->
                    !s.protocol.startsWith(ASYNCAPI_MQTT_PROTOCOL_NAME) &&
                    !s.protocol.startsWith(ASYNCAPI_HTTP_PROTOCOL_NAME)))
                {
                    break inject;
                }
                final String conditionProtocol = asyncapi.servers.values().stream().findFirst().get().protocol;
                routesByProtocol.computeIfAbsent(conditionProtocol, c -> new ArrayList<>()).add(route);
            }
        }

        final String namespace = String.join("+", labels);
        NamespaceConfigBuilder<NamespaceConfig> builder = NamespaceConfig.builder()
            .name(String.format("%s/%s", qname, namespace))
            .inject(n -> this.injectNamespaceMetric(n, !metricRefs.isEmpty()));

        routesByProtocol.forEach((k, r) ->
        {
            final AsyncapiProxy proxy = resolveProxy(k);
            builder.binding()
                .name(String.format("%s_proxy0", proxy.type))
                .type(proxy.type)
                .kind(PROXY)
                .inject(b -> this.injectMetrics(b, metricRefs))
                .inject(b -> proxy.injectProxyOptions(b, options))
                .inject(b -> proxy.injectProxyRoutes(b, r))
                .build();
        });


        return builder.build();
    }

    private AsyncapiProxy resolveProxy(
        String protocol)
    {
        Pattern pattern = Pattern.compile("(http|mqtt)");
        Matcher matcher = pattern.matcher(protocol);
        AsyncapiProxy proxy = null;
        if (matcher.find())
        {
            switch (matcher.group())
            {
            case "http":
                proxy = new AsyncapiHttpKafkaProxy(qname, asyncapis);
                break;
            case "mqtt":
                proxy = new AsyncapiMqttKafkaProxy(qname, asyncapis);
                break;
            }
        }
        return proxy;
    }

}
