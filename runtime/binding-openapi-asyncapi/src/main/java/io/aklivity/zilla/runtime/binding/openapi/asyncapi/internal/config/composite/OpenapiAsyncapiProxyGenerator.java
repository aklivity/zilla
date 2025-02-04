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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.composite;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.PROXY;
import static java.util.function.Function.identity;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiOperationView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiReplyView;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchFilterConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchMergeConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceAsyncHeaderConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiCompositeConditionConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiCompositeRouteConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiRouteConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.extensions.http.kafka.OpenapiHttpKafkaFilter;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiHeaderView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiResponseView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiSchemaView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiSecurityRequirementView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiSecuritySchemeView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiServerView;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;

public final class OpenapiAsyncapiProxyGenerator extends OpenapiAsyncapiCompositeGenerator
{
    @Override
    protected OpenapiAsyncapiCompositeConfig generate(
        OpenapiAsyncapiBindingConfig binding,
        List<OpenapiSchemaConfig> openapis,
        List<AsyncapiSchemaConfig> asyncapis)
    {
        final Map<String, OpenapiSchemaConfig> openapisByApiId = openapis.stream()
                .collect(Collectors.toMap(s -> s.apiLabel, identity()));

        final Map<String, AsyncapiSchemaConfig> asyncapisByApiId = asyncapis.stream()
                .collect(Collectors.toMap(s -> s.apiLabel, identity()));

        final List<ProxyMapping> mappings = binding.routes.stream()
            .flatMap(r -> r.when.stream()
                .map(w ->
                    new ProxyMapping(
                        openapisByApiId.get(w.apiId),
                        asyncapisByApiId.get(r.with.apiId))))
            .distinct()
            .toList();

        List<NamespaceConfig> namespaces = new LinkedList<>();
        List<OpenapiAsyncapiCompositeRouteConfig> routes = new LinkedList<>();
        Matcher routed = Pattern.compile("(http)_kafka_proxy0").matcher("");

        for (ProxyMapping mapping : mappings)
        {
            NamespaceHelper helper = new ProxyNamespaceHelper(binding, mapping);
            NamespaceConfig namespace = NamespaceConfig.builder()
                .inject(helper::injectAll)
                .build();
            namespaces.add(namespace);

            namespace.bindings.stream()
                .filter(b -> routed.reset(b.name).matches())
                .forEach(b ->
                {
                    final int operationTypeId = binding.supplyTypeId.applyAsInt(routed.group(1));
                    final long routeId = binding.supplyBindingId.applyAsLong(namespace, b);

                    final OpenapiAsyncapiCompositeConditionConfig when = new OpenapiAsyncapiCompositeConditionConfig(
                        mapping.when.schemaId,
                        operationTypeId);

                    routes.add(new OpenapiAsyncapiCompositeRouteConfig(routeId, when));
                });
        }

        return new OpenapiAsyncapiCompositeConfig(openapis, asyncapis, namespaces, routes);
    }

    private final class ProxyNamespaceHelper extends NamespaceHelper
    {
        private final BindingsHelper bindings;

        private ProxyNamespaceHelper(
            OpenapiAsyncapiBindingConfig config,
            ProxyMapping mapping)
        {
            super(config, "%s+%s".formatted(mapping.when.apiLabel, mapping.with.apiLabel));
            this.bindings = new ProxyBindingsHelper(mapping);
        }

        @Override
        protected <C> NamespaceConfigBuilder<C> injectComponents(
            NamespaceConfigBuilder<C> namespace)
        {
            return namespace
                .inject(bindings::injectAll);
        }

        private final class ProxyBindingsHelper extends BindingsHelper
        {
            private final ProxyMapping mapping;
            private final BindingsHelper httpKafka;

            private ProxyBindingsHelper(
                ProxyMapping mapping)
            {
                this.mapping = mapping;
                this.httpKafka = new HttpKafkaBindingsHelper();
            }

            @Override
            protected <C> NamespaceConfigBuilder<C> injectAll(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                    .inject(httpKafka::injectAll);
            }

            private final class ProxyRouteHelper
            {
                private final List<ProxyWhenHelper> when;
                private final ProxyWithHelper with;

                private ProxyRouteHelper(
                    OpenapiAsyncapiRouteConfig route)
                {
                    this.when = route.when.stream()
                            .filter(c -> mapping.when.apiLabel.equals(c.apiId))
                            .map(c -> new ProxyWhenHelper(mapping.when, c.operationId))
                            .toList();
                    this.with = new ProxyWithHelper(mapping.with, route.with.operationId);
                }

                private boolean hasWithProtocol(
                    Predicate<String> protocol)
                {
                    return hasProtocol(with, protocol);
                }

                private static boolean hasProtocol(
                    ProxyWithHelper operation,
                    Predicate<String> protocol)
                {
                    return operation.schema != null && operation.schema.asyncapi.hasProtocol(protocol);
                }
            }

            private final class ProxyWhenHelper
            {
                private final OpenapiSchemaConfig schema;
                private final String operationId;

                private ProxyWhenHelper(
                    OpenapiSchemaConfig schema,
                    String operationId)
                {
                    this.schema = schema;
                    this.operationId = operationId;
                }
            }

            private final class ProxyWithHelper
            {
                private final AsyncapiSchemaConfig schema;
                private final String operationId;

                private ProxyWithHelper(
                    AsyncapiSchemaConfig schema,
                    String operationId)
                {
                    this.schema = schema;
                    this.operationId = operationId;
                }
            }

            private final class HttpKafkaBindingsHelper extends BindingsHelper
            {
                private static final String CORRELATION_ID = "\\{correlationId\\}";
                private static final String PARAMETERS = "\\{(?!correlationId)(\\w+)\\}";

                private static final Pattern JSON_CONTENT_TYPE_PATTERN = Pattern.compile("^application/(?:.+\\+)?json$");
                private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
                private static final Pattern CORRELATION_PATTERN = Pattern.compile(CORRELATION_ID);

                private final Matcher parameters = PARAMETER_PATTERN.matcher("");
                private final Matcher correlation = CORRELATION_PATTERN.matcher("");
                private final Matcher jsonContentType = JSON_CONTENT_TYPE_PATTERN.matcher("");


                private final List<ProxyRouteHelper> httpKafkaRoutes;

                private HttpKafkaBindingsHelper()
                {
                    this.httpKafkaRoutes = config.routes.stream()
                            .map(ProxyRouteHelper::new)
                            .filter(r -> r.hasWithProtocol(p -> p.startsWith("kafka")))
                            .toList();
                }

                @Override
                protected <C> NamespaceConfigBuilder<C> injectAll(
                    NamespaceConfigBuilder<C> namespace)
                {
                    if (!httpKafkaRoutes.isEmpty())
                    {
                        namespace.inject(this::injectHttpKafka);
                    }

                    return namespace;
                }

                private <C> NamespaceConfigBuilder<C> injectHttpKafka(
                    NamespaceConfigBuilder<C> namespace)
                {
                    return namespace.binding()
                        .name("http_kafka_proxy0")
                        .type("http-kafka")
                        .kind(PROXY)
                        .inject(this::injectMetrics)
                        .inject(this::injectHttpKafkaRoutes)
                        .build();
                }

                private <C> BindingConfigBuilder<C> injectHttpKafkaRoutes(
                    BindingConfigBuilder<C> binding)
                {
                    for (ProxyRouteHelper route : httpKafkaRoutes)
                    {
                        Map<String, AsyncapiOperationView> kafkaOpsById = route.with.schema.asyncapi.operations;

                        for (ProxyWhenHelper condition : route.when)
                        {
                            Map<String, OpenapiOperationView> httpOpsById = condition.schema.openapi.operations;

                            OpenapiOperationView httpOp = httpOpsById.get(condition.operationId);
                            if (httpOp == null)
                            {
                                for (OpenapiOperationView httpAnyOp : httpOpsById.values())
                                {
                                    String kafkaOpId = route.with.operationId != null
                                        ? route.with.operationId
                                        : httpAnyOp.id;

                                    AsyncapiOperationView kafkaOp = kafkaOpsById.get(kafkaOpId);

                                    if (kafkaOp == null)
                                    {
                                        OpenapiOperationView httpFormatOp = httpOpsById.values().stream()
                                            .filter(o -> o != httpAnyOp)
                                            .filter(o -> o.responses.values().stream()
                                                .filter(r -> "202".equals(r.status))
                                                .anyMatch(r -> r.headers.values().stream()
                                                    .filter(OpenapiHeaderView::hasSchemaFormat)
                                                    .anyMatch(
                                                        h -> httpAnyOp.path.equals(h.schema.format))))
                                            .findFirst()
                                            .orElse(null);

                                        if (httpFormatOp != null)
                                        {
                                            kafkaOp = kafkaOpsById.get(httpFormatOp.id);
                                        }
                                    }

                                    if (kafkaOp != null)
                                    {
                                        injectHttpKafkaRoute(binding, httpAnyOp, kafkaOp);
                                    }
                                }
                            }
                            else
                            {
                                AsyncapiOperationView kafkaOp = kafkaOpsById.get(route.with.operationId);
                                binding.inject(b -> injectHttpKafkaRoute(b, httpOp, kafkaOp));
                            }
                        }
                    }

                    return binding;
                }

                private <C> BindingConfigBuilder<C> injectHttpKafkaRoute(
                    BindingConfigBuilder<C> binding,
                    OpenapiOperationView httpOp,
                    AsyncapiOperationView kafkaOp)
                {
                    for (OpenapiServerView httpServer : httpOp.servers)
                    {
                        binding
                            .route()
                                .exit(config.qname)
                                .when(HttpKafkaConditionConfig::builder)
                                    .method(httpOp.method)
                                    .path(httpServer.requestPath(httpOp.path))
                                    .build()
                                .inject(r -> injectHttpKafkaRouteWith(r, httpServer, httpOp, kafkaOp))
                                .inject(r -> injectHttpServerRouteGuarded(r, httpOp))
                                .build();
                    }

                    return binding;
                }

                private <C> RouteConfigBuilder<C> injectHttpKafkaRouteWith(
                    RouteConfigBuilder<C> route,
                    OpenapiServerView httpServer,
                    OpenapiOperationView httpOperation,
                    AsyncapiOperationView kafkaOperation)
                {
                    switch (kafkaOperation.action)
                    {
                    case "receive":
                        route
                            .with(HttpKafkaWithConfig::builder)
                            .compositeId(httpOperation.compositeId)
                            .fetch()
                                .topic(kafkaOperation.channel.address)
                                .inject(w -> injectHttpKafkaRouteFetchWith(w, httpServer, httpOperation))
                                .build()
                            .build();
                        break;
                    case "send":
                        route
                            .with(HttpKafkaWithConfig::builder)
                            .compositeId(httpOperation.compositeId)
                            .produce()
                                .topic(kafkaOperation.channel.address)
                                .inject(w -> injectHttpKafkaRouteProduceWith(w, httpServer, httpOperation, kafkaOperation))
                                .build()
                            .build();
                        break;
                    }

                    return route;
                }

                private <C> HttpKafkaWithFetchConfigBuilder<C> injectHttpKafkaRouteFetchWith(
                    HttpKafkaWithFetchConfigBuilder<C> fetch,
                    OpenapiServerView httpServer,
                    OpenapiOperationView httpOperation)
                {
                    merge:
                    for (OpenapiResponseView response : httpOperation.responses.values())
                    {
                        OpenapiSchemaView schema = response.content != null
                                ? response.content.values().stream()
                                    .filter(r -> jsonContentType.reset(r.name).matches())
                                    .findFirst()
                                    .map(r -> r.schema)
                                    .orElse(null)
                                : null;

                        if (schema != null && "array".equals(schema.type))
                        {
                            fetch.merged(HttpKafkaWithFetchMergeConfig.builder()
                                .contentType("application/json")
                                .initial("[]")
                                .path("/-")
                                .build());
                            break merge;
                        }
                    }

                    // TODO: remove, driven by http kafka operation extension instead?
                    final List<String> httpParamNames = findParams(httpOperation.path);
                    if (!httpParamNames.isEmpty())
                    {
                        fetch.filter()
                            .key(String.format("${params.%s}", httpParamNames.get(httpParamNames.size() - 1)))
                            .build();
                    }

                    if (httpOperation.hasExtensionHttpKafka())
                    {
                        List<OpenapiHttpKafkaFilter> filters = httpOperation.httpKafka.filters;
                        if (filters != null)
                        {
                            for (OpenapiHttpKafkaFilter filter : filters)
                            {
                                HttpKafkaWithFetchFilterConfigBuilder<?> withFilter = fetch.filter();

                                String key = filter.key;
                                if (key != null)
                                {
                                    key = resolveIdentity(key);

                                    withFilter.key(key);
                                }

                                Map<String, String> headers = filter.headers;
                                if (headers != null)
                                {
                                    for (Map.Entry<String, String> header : headers.entrySet())
                                    {
                                        String name = header.getKey();
                                        String value = header.getValue();

                                        value = resolveIdentity(value);

                                        withFilter.header(name, value);
                                    }
                                }

                                withFilter.build();
                            }
                        }
                    }

                    return fetch;
                }

                private <C> HttpKafkaWithProduceConfigBuilder<C> injectHttpKafkaRouteProduceWith(
                    HttpKafkaWithProduceConfigBuilder<C> produce,
                    OpenapiServerView httpServer,
                    OpenapiOperationView httpOperation,
                    AsyncapiOperationView kafkaOperation)
                {
                    final List<String> httpParamNames = findParams(httpOperation.path);
                    final String key = !httpParamNames.isEmpty()
                        ? String.format("${params.%s}", httpParamNames.get(httpParamNames.size() - 1))
                        : "${idempotencyKey}";

                    produce.acks("in_sync_replicas").key(key);

                    for (Map.Entry<String, OpenapiResponseView> response : httpOperation.responses.entrySet())
                    {
                        if ("202".equals(response.getKey()))
                        {
                            OpenapiResponseView content = response.getValue();
                            boolean async = content.headers.entrySet().stream()
                                .anyMatch(e -> hasCorrelationId(e.getValue()));

                            if (async)
                            {
                                content.headers.forEach((k, v) ->
                                {
                                    String location = httpServer.requestPath(v.schema.format);
                                    location = location.replaceAll(CORRELATION_ID, "\\${correlationId}");
                                    location = location.replaceAll(PARAMETERS, "\\${params.$1}");
                                    produce.async(HttpKafkaWithProduceAsyncHeaderConfig.builder()
                                        .name(k)
                                        .value(location)
                                        .build());
                                });
                            }
                        }
                    }
                    AsyncapiReplyView reply = kafkaOperation.reply;
                    if (reply != null)
                    {
                        produce.replyTo(reply.channel.address);
                    }

                    return produce;
                }


                private <C> RouteConfigBuilder<C> injectHttpServerRouteGuarded(
                    RouteConfigBuilder<C> route,
                    OpenapiOperationView httpOp)
                {
                    Map<String, OpenapiSecuritySchemeView> securitySchemes = httpOp.specification.components.securitySchemes;
                    final List<List<OpenapiSecurityRequirementView>> security = httpOp.security;

                    if (security != null)
                    {
                        security.stream()
                            .flatMap(s -> s.stream())
                            .filter(r -> securitySchemes != null && securitySchemes.containsKey(r.name))
                            .filter(r -> "jwt".equalsIgnoreCase(securitySchemes.get(r.name).bearerFormat))
                            .forEach(r ->
                                route
                                    .guarded()
                                        .name(String.format("%s:jwt0", config.namespace))
                                        .inject(guarded -> injectGuardedRoles(guarded, r.scopes))
                                        .build());
                    }

                    return route;
                }

                private List<String> findParams(
                    String item)
                {
                    List<String> paramNames = new ArrayList<>();
                    Matcher matcher = parameters.reset(item);
                    while (matcher.find())
                    {
                        paramNames.add(parameters.group(1));
                    }
                    return paramNames;
                }

                private boolean hasCorrelationId(
                    OpenapiHeaderView header)
                {
                    boolean hasCorrelationId = false;
                    OpenapiSchemaView schema = header.schema;
                    if (schema != null &&
                        schema.format != null)
                    {
                        hasCorrelationId = correlation.reset(schema.format).find();
                    }
                    return hasCorrelationId;
                }
            }
        }
    }

    private record ProxyMapping(
        OpenapiSchemaConfig when,
        AsyncapiSchemaConfig with)
    {
    }
}
