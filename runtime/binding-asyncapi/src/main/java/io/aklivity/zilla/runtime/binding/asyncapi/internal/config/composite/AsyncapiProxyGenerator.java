/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config.composite;

import static io.aklivity.zilla.config.engine.KindConfig.PROXY;
import static io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithConfig.EVENT_ID_DEFAULT;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.config.binding.asyncapi.AsyncapiConditionConfig;
import io.aklivity.zilla.config.binding.http.kafka.HttpKafkaConditionConfig;
import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaConditionConfig;
import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaConditionKind;
import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaOptionsConfig;
import io.aklivity.zilla.config.binding.sse.kafka.SseKafkaConditionConfig;
import io.aklivity.zilla.config.engine.BindingConfigBuilder;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.config.engine.NamespaceConfigBuilder;
import io.aklivity.zilla.config.engine.RouteConfigBuilder;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeConditionConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeRouteConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiRouteConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiWithConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.http.AsyncapiHttpOperationBindingEx;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.http.kafka.AsyncapiHttpKafkaFilterEx;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.http.kafka.AsyncapiHttpKafkaOperationBindingEx;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.sse.kafka.AsyncapiSseKafkaFilterEx;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.sse.kafka.AsyncapiSseKafkaOperationBindingEx;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.extensions.mqtt.kafka.AsyncapiMqttKafkaChannelEx;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.MqttQoS;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchFilterConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchMergeConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceConfigBuilder;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithConfigBuilder;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithFilterConfigBuilder;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSchemaConfig;
import io.aklivity.zilla.runtime.common.asyncapi.security.GuardedRef;
import io.aklivity.zilla.runtime.common.asyncapi.security.GuardedResolution;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiChannelView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiCorrelationIdView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiOperationView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiReplyView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiSchemaItemView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiSchemaView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiView;

public final class AsyncapiProxyGenerator extends AsyncapiCompositeGenerator
{
    @Override
    protected AsyncapiCompositeConfig generate(
        AsyncapiBindingConfig binding,
        List<AsyncapiSchemaConfig> schemas)
    {
        final Map<String, AsyncapiSchemaConfig> schemasBySpecLabel = schemas.stream()
                .collect(Collectors.toMap(s -> s.specLabel, identity()));

        final List<ProxyMapping> mappings = binding.routes.stream()
            .flatMap(r -> r.when.stream()
                .map(w ->
                    new ProxyMapping(
                        schemasBySpecLabel.get(w.spec),
                        schemasBySpecLabel.get(r.with.spec))))
            .distinct()
            .toList();

        List<NamespaceConfig> namespaces = new LinkedList<>();
        List<AsyncapiCompositeRouteConfig> routes = new LinkedList<>();
        Matcher routed = Pattern.compile("(http|sse|mqtt)_kafka_proxy0").matcher("");

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

                    final AsyncapiCompositeConditionConfig when = new AsyncapiCompositeConditionConfig(
                        mapping.when.schemaId,
                        operationTypeId);

                    routes.add(new AsyncapiCompositeRouteConfig(routeId, when));
                });
        }

        return new AsyncapiCompositeConfig(schemas, namespaces, routes);
    }

    private final class ProxyNamespaceHelper extends NamespaceHelper
    {
        private final BindingsHelper bindings;

        private ProxyNamespaceHelper(
            AsyncapiBindingConfig config,
            ProxyMapping mapping)
        {
            super(config, "%s+%s".formatted(mapping.when.specLabel, mapping.with.specLabel));
            this.bindings = new ProxyBindingsHelper(mapping);
        }

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
            private final BindingsHelper sseKafka;
            private final BindingsHelper mqttKafka;

            private ProxyBindingsHelper(
                ProxyMapping mapping)
            {
                this.mapping = mapping;
                this.httpKafka = new HttpKafkaBindingsHelper();
                this.sseKafka = new SseKafkaBindingsHelper();
                this.mqttKafka = new MqttKafkaBindingsHelper();
            }

            @Override
            protected <C> NamespaceConfigBuilder<C> injectAll(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                    .inject(httpKafka::injectAll)
                    .inject(sseKafka::injectAll)
                    .inject(mqttKafka::injectAll);
            }

            private final class ProxyRouteHelper
            {
                private final List<ProxyOperationHelper> when;
                private final ProxyOperationHelper with;
                private final AsyncapiWithConfig withConfig;

                private ProxyRouteHelper(
                    AsyncapiRouteConfig route)
                {
                    this.when = route.when.stream()
                            .filter(c -> mapping.when.specLabel.equals(c.spec))
                            .map(c -> new ProxyOperationHelper(mapping.when, c))
                            .toList();
                    this.with = new ProxyOperationHelper(mapping.with, route.with.operation);
                    this.withConfig = route.with;
                }

                private boolean hasWhenProtocol(
                    Predicate<String> protocol)
                {
                    return when.stream().allMatch(s -> hasProtocol(s, protocol));
                }

                private boolean hasHttpWhenOperation()
                {
                    return when.stream().anyMatch(s -> hasHttpWhenOperation(s));
                }

                private boolean hasSseWhenOperation()
                {
                    return when.stream().anyMatch(s -> hasSseWhenOperation(s));
                }

                private boolean hasWithProtocol(
                    Predicate<String> protocol)
                {
                    return hasProtocol(with, protocol);
                }

                private static boolean hasProtocol(
                    ProxyOperationHelper operation,
                    Predicate<String> protocol)
                {
                    return operation.schema != null && operation.schema.asyncapi.hasProtocol(protocol);
                }

                private static boolean hasHttpWhenOperation(
                    ProxyOperationHelper operation)
                {
                    return operation.schema != null && operation.schema.asyncapi.hasOperationBindingsHttp();
                }

                private static boolean hasSseWhenOperation(
                    ProxyOperationHelper operation)
                {
                    return operation.schema != null && operation.schema.asyncapi.hasOperationBindingsSse();
                }
            }

            private final class ProxyOperationHelper
            {
                private final AsyncapiSchemaConfig schema;
                private final String operationId;
                private final AsyncapiConditionConfig condition;

                private ProxyOperationHelper(
                    AsyncapiSchemaConfig schema,
                    String operationId)
                {
                    this.schema = schema;
                    this.operationId = operationId;
                    this.condition = null;
                }

                private ProxyOperationHelper(
                    AsyncapiSchemaConfig schema,
                    AsyncapiConditionConfig condition)
                {
                    this.schema = schema;
                    this.operationId = condition.operation;
                    this.condition = condition;
                }

                private boolean matches(
                    AsyncapiOperationView candidate)
                {
                    return condition.matches(condition.spec, candidate.name, candidate.tags, candidate.servers);
                }
            }

            private final class MqttKafkaBindingsHelper extends BindingsHelper
            {
                private final List<ProxyRouteHelper> mqttKafkaRoutes;

                private MqttKafkaBindingsHelper()
                {
                    this.mqttKafkaRoutes = config.routes.stream()
                            .map(ProxyRouteHelper::new)
                            .filter(r -> r.hasWhenProtocol(p -> p.startsWith("mqtt")))
                            .filter(r -> r.hasWithProtocol(p -> p.startsWith("kafka")))
                            .toList();
                }

                @Override
                protected <C> NamespaceConfigBuilder<C> injectAll(
                    NamespaceConfigBuilder<C> namespace)
                {
                    if (!mqttKafkaRoutes.isEmpty())
                    {
                        namespace.inject(this::injectMqttKafka);
                    }

                    return namespace;
                }

                private <C> NamespaceConfigBuilder<C> injectMqttKafka(
                    NamespaceConfigBuilder<C> namespace)
                {
                    return namespace.binding()
                        .name("mqtt_kafka_proxy0")
                        .type("mqtt-kafka")
                        .kind(PROXY)
                        .inject(this::injectMetrics)
                        .inject(this::injectMqttKafkaOptions)
                        .inject(this::injectMqttKafkaRoutes)
                        .build();
                }

                private <C> BindingConfigBuilder<C> injectMqttKafkaOptions(
                    BindingConfigBuilder<C> binding)
                {
                    final AsyncapiView specification = mapping.with.asyncapi;
                    final Map<String, AsyncapiChannelView> channelsByRole = specification.channels.values().stream()
                        .filter(c -> c.hasExtension("x-zilla-mqtt-kafka"))
                        .collect(Collectors.toMap(
                            c -> c.extension("x-zilla-mqtt-kafka", AsyncapiMqttKafkaChannelEx.class).get().role,
                            identity()));

                    final AsyncapiChannelView sessions = requireMqttKafkaRole(channelsByRole, "sessions");
                    final AsyncapiChannelView messages = requireMqttKafkaRole(channelsByRole, "messages");
                    final AsyncapiChannelView retained = requireMqttKafkaRole(channelsByRole, "retained");

                    return binding.options(MqttKafkaOptionsConfig::builder)
                        .topics()
                            .sessions(sessions.address)
                            .messages(messages.address)
                            .retained(retained.address)
                        .build()
                        .publish()
                            .qosMax(MqttQoS.EXACTLY_ONCE.name().toLowerCase())
                            .build()
                        .build();
                }

                private AsyncapiChannelView requireMqttKafkaRole(
                    Map<String, AsyncapiChannelView> channelsByRole,
                    String role)
                {
                    return requireNonNull(channelsByRole.get(role),
                        "x-zilla-mqtt-kafka role \"%s\" not declared on any channel of the kafka spec".formatted(role));
                }

                private <C> BindingConfigBuilder<C> injectMqttKafkaRoutes(
                    BindingConfigBuilder<C> binding)
                {
                    for (ProxyRouteHelper route : mqttKafkaRoutes)
                    {
                        Map<String, AsyncapiOperationView> kafkaOpsById = route.with.schema.asyncapi.operations;

                        for (ProxyOperationHelper condition : route.when)
                        {
                            Map<String, AsyncapiOperationView> mqttOpsById = condition.schema.asyncapi.operations;

                            for (AsyncapiOperationView mqttOp : mqttOpsById.values())
                            {
                                if (condition.matches(mqttOp))
                                {
                                    String kafkaOpId = route.withConfig.operation != null
                                        ? route.withConfig.operation
                                        : mqttOp.name;

                                    AsyncapiOperationView kafkaOp = kafkaOpsById.get(kafkaOpId);

                                    if (kafkaOp != null)
                                    {
                                        injectMqttKafkaRoute(binding, mqttOp, kafkaOp);
                                    }
                                }
                            }
                        }
                    }

                    return binding;
                }

                private <C> BindingConfigBuilder<C> injectMqttKafkaRoute(
                    BindingConfigBuilder<C> binding,
                    AsyncapiOperationView mqttOperation,
                    AsyncapiOperationView kafkaOperation)
                {
                    final MqttKafkaConditionKind kind = mqttOperation.action.equals("send")
                            ? MqttKafkaConditionKind.PUBLISH
                            : MqttKafkaConditionKind.SUBSCRIBE;

                    binding.route()
                        .exit(config.qname)
                        .when(MqttKafkaConditionConfig::builder)
                            .topic(mqttOperation.channel.address)
                            .kind(kind)
                            .build()
                        .with(MqttKafkaWithConfig::builder)
                            .compositeId(mqttOperation.compositeId)
                            .messages(kafkaOperation.channel.address.replaceAll("\\{([^{}]*)\\}", "\\${params.$1}"))
                            .build()
                        .build();

                    return binding;
                }
            }

            private final class SseKafkaBindingsHelper extends BindingsHelper
            {
                private final List<ProxyRouteHelper> sseKafkaRoutes;

                private SseKafkaBindingsHelper()
                {
                    this.sseKafkaRoutes = config.routes.stream()
                        .map(ProxyRouteHelper::new)
                        .filter(r -> r.hasSseWhenOperation())
                        .filter(r -> r.hasWithProtocol(p -> p.startsWith("kafka")))
                        .toList();
                }

                @Override
                protected <C> NamespaceConfigBuilder<C> injectAll(
                    NamespaceConfigBuilder<C> namespace)
                {
                    if (!sseKafkaRoutes.isEmpty())
                    {
                        namespace.inject(this::injectSseKafka);
                    }

                    return namespace;
                }

                private <C> NamespaceConfigBuilder<C> injectSseKafka(
                    NamespaceConfigBuilder<C> namespace)
                {
                    return namespace.binding()
                        .name("sse_kafka_proxy0")
                        .type("sse-kafka")
                        .kind(PROXY)
                        .inject(this::injectMetrics)
                        .inject(this::injectSseKafkaRoutes)
                        .build();
                }

                private <C> BindingConfigBuilder<C> injectSseKafkaRoutes(
                    BindingConfigBuilder<C> binding)
                {
                    for (ProxyRouteHelper route : sseKafkaRoutes)
                    {
                        Map<String, AsyncapiOperationView> kafkaOpsById = route.with.schema.asyncapi.operations;

                        for (ProxyOperationHelper condition : route.when)
                        {
                            Map<String, AsyncapiOperationView> sseOpsById = condition.schema.asyncapi.operations;

                            for (AsyncapiOperationView sseOp : sseOpsById.values())
                            {
                                if (condition.matches(sseOp))
                                {
                                    String kafkaOpId = route.withConfig.operation != null
                                        ? route.withConfig.operation
                                        : sseOp.name;

                                    AsyncapiOperationView kafkaOp = kafkaOpsById.get(kafkaOpId);

                                    if (kafkaOp != null)
                                    {
                                        injectSseKafkaRoute(binding, condition.schema, sseOp, kafkaOp);
                                    }
                                }
                            }
                        }
                    }

                    return binding;
                }

                private <C> BindingConfigBuilder<C> injectSseKafkaRoute(
                    BindingConfigBuilder<C> binding,
                    AsyncapiSchemaConfig schema,
                    AsyncapiOperationView sseOperation,
                    AsyncapiOperationView kafkaOperation)
                {
                    if (sseOperation.hasBinding("x-zilla-sse") && allowed(schema, sseOperation))
                    {
                        final GuardedResolution resolution = resolveGuarded(schema, sseOperation);

                        binding.route()
                            .exit(config.qname)
                            .when(SseKafkaConditionConfig::builder)
                                .path(sseOperation.channel.address)
                                .build()
                                .inject(r -> injectSseKafkaRouteWith(r, sseOperation, kafkaOperation, guardQname(resolution)))
                                .inject(r -> injectSseServerRouteGuarded(r, resolution))
                            .build();
                    }

                    return binding;
                }


                private <C> RouteConfigBuilder<C> injectSseKafkaRouteWith(
                    RouteConfigBuilder<C> route,
                    AsyncapiOperationView sseOperation,
                    AsyncapiOperationView kafkaOperation,
                    String guardQname)
                {
                    if ("receive".equals(kafkaOperation.action))
                    {
                        route
                            .with(SseKafkaWithConfig::builder)
                            .compositeId(sseOperation.compositeId)
                            .topic(kafkaOperation.channel.address)
                            .eventId(EVENT_ID_DEFAULT)
                            .inject(w -> injectSseKafkaRouteWithFilters(w, sseOperation, guardQname))
                            .build();
                    }

                    return route;
                }

                private <C> SseKafkaWithConfigBuilder<C> injectSseKafkaRouteWithFilters(
                    SseKafkaWithConfigBuilder<C> with,
                    AsyncapiOperationView sseOperation,
                    String guardQname)
                {
                    if (sseOperation.hasBinding("x-zilla-sse-kafka"))
                    {
                        List<AsyncapiSseKafkaFilterEx> filters = sseOperation
                            .binding("x-zilla-sse-kafka", AsyncapiSseKafkaOperationBindingEx.class)
                            .get().filters;
                        if (filters != null)
                        {
                            for (AsyncapiSseKafkaFilterEx filter : filters)
                            {
                                SseKafkaWithFilterConfigBuilder<?> withFilter = with.filter();

                                String key = filter.key;
                                if (key != null)
                                {
                                    key = resolveIdentity(key, guardQname);

                                    withFilter.key(key);
                                }

                                Map<String, String> headers = filter.headers;
                                if (headers != null)
                                {
                                    for (Map.Entry<String, String> header : headers.entrySet())
                                    {
                                        String name = header.getKey();
                                        String value = header.getValue();

                                        value = resolveIdentity(value, guardQname);

                                        withFilter.header()
                                            .name(name)
                                            .value(value)
                                            .build();
                                    }
                                }

                                withFilter.build();
                            }
                        }
                    }

                    return with;
                }

                private <C> RouteConfigBuilder<C> injectSseServerRouteGuarded(
                    RouteConfigBuilder<C> route,
                    GuardedResolution resolution)
                {
                    for (GuardedRef ref : resolution.guarded)
                    {
                        route
                            .guarded()
                                .name(ref.qname)
                                .inject(guarded -> injectGuardedRoles(guarded, ref.roles))
                                .build();
                    }

                    return route;
                }
            }

            private final class HttpKafkaBindingsHelper extends BindingsHelper
            {
                private static final String CORRELATION_ID = "\\{correlationId\\}";
                private static final String PARAMETERS = "\\{(?!correlationId)(\\w+)\\}";

                private static final Pattern HEADER_LOCATION_PATTERN = Pattern.compile("([^/]+)$");
                private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
                private static final Pattern CORRELATION_HEADERS_NAME = Pattern.compile("\\$message\\.header#\\/(.+)");

                private final Matcher headerLocation = HEADER_LOCATION_PATTERN.matcher("");
                private final Matcher parameters = PARAMETER_PATTERN.matcher("");
                private final Matcher correlation = CORRELATION_HEADERS_NAME.matcher("");

                private final List<ProxyRouteHelper> httpKafkaRoutes;

                private HttpKafkaBindingsHelper()
                {
                    this.httpKafkaRoutes = config.routes.stream()
                            .map(ProxyRouteHelper::new)
                            .filter(r -> r.hasHttpWhenOperation())
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

                        for (ProxyOperationHelper condition : route.when)
                        {
                            Map<String, AsyncapiOperationView> httpOpsById = condition.schema.asyncapi.operations;

                            for (AsyncapiOperationView httpOp : httpOpsById.values())
                            {
                                if (condition.matches(httpOp))
                                {
                                    String kafkaOpId = route.withConfig.operation != null
                                        ? route.withConfig.operation
                                        : httpOp.name;

                                    AsyncapiOperationView kafkaOp = kafkaOpsById.get(kafkaOpId);

                                    if (kafkaOp != null)
                                    {
                                        injectHttpKafkaRoute(binding, condition.schema, httpOp, kafkaOp);
                                    }
                                }
                            }
                        }
                    }

                    return binding;
                }

                private <C> BindingConfigBuilder<C> injectHttpKafkaRoute(
                    BindingConfigBuilder<C> binding,
                    AsyncapiSchemaConfig schema,
                    AsyncapiOperationView httpOperation,
                    AsyncapiOperationView kafkaOperation)
                {
                    if (httpOperation.hasBinding("http") && allowed(schema, httpOperation))
                    {
                        final AsyncapiChannelView httpChannel = httpOperation.channel;
                        final String httpMethod = httpOperation
                            .binding("http", AsyncapiHttpOperationBindingEx.class).get().method;
                        final String httpPath = httpChannel.address;

                        boolean async = httpOperation.messages.stream()
                            .anyMatch(m -> m.correlationId != null);

                        if (async)
                        {
                            for (AsyncapiOperationView httpPeerOp : httpOperation.specification.operations.values())
                            {
                                AsyncapiChannelView channel = httpPeerOp.channel;
                                if (parameters.reset(channel.address).find())
                                {
                                    AsyncapiReplyView reply = kafkaOperation.reply;
                                    if (reply != null)
                                    {
                                        final String peerGuardQname = guardQname(resolveGuarded(schema, httpPeerOp));

                                        binding.route()
                                            .exit(config.qname)
                                            .when(HttpKafkaConditionConfig::builder)
                                                .method(httpPeerOp
                                                    .binding("http", AsyncapiHttpOperationBindingEx.class).get().method)
                                                .path(httpPeerOp.channel.address)
                                                .build()
                                            .with(HttpKafkaWithConfig::builder)
                                                .compositeId(httpOperation.compositeId)
                                                .produce()
                                                    .topic(kafkaOperation.channel.address)
                                                    .inject(w -> injectHttpKafkaRouteProduceWith(
                                                        w, httpPeerOp, kafkaOperation, peerGuardQname))
                                                    .build()
                                                .build()
                                            .build();
                                    }
                                }
                            }
                        }

                        final GuardedResolution resolution = resolveGuarded(schema, httpOperation);

                        binding.route()
                            .exit(config.qname)
                            .when(HttpKafkaConditionConfig::builder)
                                .method(httpMethod)
                                .path(httpPath)
                                .build()
                            .inject(r -> injectHttpKafkaRouteWith(r, httpOperation, kafkaOperation, guardQname(resolution)))
                            .inject(r -> injectHttpServerRouteGuarded(r, resolution))
                            .build();
                    }

                    return binding;
                }

                private <C> RouteConfigBuilder<C> injectHttpKafkaRouteWith(
                    RouteConfigBuilder<C> route,
                    AsyncapiOperationView httpOperation,
                    AsyncapiOperationView kafkaOperation,
                    String guardQname)
                {
                    switch (kafkaOperation.action)
                    {
                    case "receive":
                        route
                            .with(HttpKafkaWithConfig::builder)
                            .compositeId(httpOperation.compositeId)
                            .fetch()
                                .topic(kafkaOperation.channel.address)
                                .inject(w -> injectHttpKafkaRouteFetchWith(w, httpOperation, guardQname))
                                .build()
                            .build();
                        break;
                    case "send":
                        route
                            .with(HttpKafkaWithConfig::builder)
                            .compositeId(httpOperation.compositeId)
                            .produce()
                                .topic(kafkaOperation.channel.address)
                                .inject(w -> injectHttpKafkaRouteProduceWith(w, httpOperation, kafkaOperation, guardQname))
                                .build()
                            .build();
                        break;
                    }

                    return route;
                }

                private <C> HttpKafkaWithFetchConfigBuilder<C> injectHttpKafkaRouteFetchWith(
                    HttpKafkaWithFetchConfigBuilder<C> fetch,
                    AsyncapiOperationView httpOperation,
                    String guardQname)
                {
                    final AsyncapiChannelView channel = httpOperation.channel;

                    merge:
                    for (AsyncapiMessageView message : channel.messages)
                    {
                        AsyncapiSchemaItemView schemaItem = message.payload;

                        if (schemaItem instanceof AsyncapiSchemaView schema &&
                            "array".equals(schema.type))
                        {
                            fetch.merged(HttpKafkaWithFetchMergeConfig.builder()
                                .contentType("application/json")
                                .initial("[]")
                                .path("/-")
                                .build());
                            break merge;
                        }
                    }

                    // TODO: remove, driven by http kafka operation binding instead?
                    final List<String> httpParamNames = findParams(httpOperation.channel.address);
                    if (!httpParamNames.isEmpty())
                    {
                        fetch.filter()
                            .key(String.format("${params.%s}", httpParamNames.get(httpParamNames.size() - 1)))
                            .build();
                    }

                    if (httpOperation.hasBinding("x-zilla-http-kafka"))
                    {
                        List<AsyncapiHttpKafkaFilterEx> filters = httpOperation
                            .binding("x-zilla-http-kafka", AsyncapiHttpKafkaOperationBindingEx.class)
                            .get().filters;
                        if (filters != null)
                        {
                            for (AsyncapiHttpKafkaFilterEx filter : filters)
                            {
                                HttpKafkaWithFetchFilterConfigBuilder<?> withFilter = fetch.filter();

                                String key = filter.key;
                                if (key != null)
                                {
                                    key = resolveIdentity(key, guardQname);

                                    withFilter.key(key);
                                }

                                Map<String, String> headers = filter.headers;
                                if (headers != null)
                                {
                                    for (Map.Entry<String, String> header : headers.entrySet())
                                    {
                                        String name = header.getKey();
                                        String value = header.getValue();

                                        value = resolveIdentity(value, guardQname);

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
                    AsyncapiOperationView httpOperation,
                    AsyncapiOperationView kafkaOperation,
                    String guardQname)
                {
                    final List<String> httpParamNames = findParams(httpOperation.channel.address);

                    final String key = !httpParamNames.isEmpty()
                        ? String.format("${params.%s}", httpParamNames.get(httpParamNames.size() - 1))
                        : "${idempotencyKey}";

                    produce.acks("in_sync_replicas").key(key);

                    httpOperation.messages.forEach(message ->
                    {
                        if (message.correlationId != null)
                        {
                            AsyncapiCorrelationIdView correlationId = message.correlationId;

                            if (headerLocation.reset(correlationId.location).find())
                            {
                                String headerName = headerLocation.group(1);
                                AsyncapiSchemaView schema = message.headers.properties.get(headerName);
                                String location = schema.format
                                        .replaceAll(CORRELATION_ID, "\\${correlationId}")
                                        .replaceAll(PARAMETERS, "\\${params.$1}");

                                produce.async()
                                    .name("location")
                                    .value(location)
                                    .build();
                            }
                        }
                    });

                    if (kafkaOperation.reply != null)
                    {
                        produce.replyTo(kafkaOperation.reply.channel.address);
                    }

                    AsyncapiMessageView messageView = kafkaOperation.messages.get(0);
                    if (messageView.correlationId != null && messageView.correlationId.location != null)
                    {
                        String correlationId = messageView.correlationId.location;
                        if (correlation.reset(correlationId).matches())
                        {
                            produce.correlationId(correlation.group(1));
                        }
                    }

                    AsyncapiHttpKafkaOperationBindingEx httpKafkaBinding = httpOperation
                        .binding("x-zilla-http-kafka", AsyncapiHttpKafkaOperationBindingEx.class)
                        .orElse(null);
                    if (httpKafkaBinding != null)
                    {
                        String httpKafkaKey = httpKafkaBinding.key;
                        if (httpKafkaKey != null)
                        {
                            httpKafkaKey = resolveIdentity(httpKafkaKey, guardQname);

                            produce.key(httpKafkaKey);
                        }

                        Map<String, String> overrides = httpKafkaBinding.overrides;
                        if (overrides != null)
                        {
                            for (Map.Entry<String, String> override : overrides.entrySet())
                            {
                                String name = override.getKey();
                                String value = override.getValue();

                                value = resolveIdentity(value, guardQname);

                                produce.override()
                                    .name(name)
                                    .value(value)
                                    .build();
                            }
                        }
                    }

                    return produce;
                }

                private <C> RouteConfigBuilder<C> injectHttpServerRouteGuarded(
                    RouteConfigBuilder<C> route,
                    GuardedResolution resolution)
                {
                    for (GuardedRef ref : resolution.guarded)
                    {
                        route
                            .guarded()
                                .name(ref.qname)
                                .inject(guarded -> injectGuardedRoles(guarded, ref.roles))
                                .build();
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
            }
        }
    }

    private record ProxyMapping(
        AsyncapiSchemaConfig when,
        AsyncapiSchemaConfig with)
    {
    }
}
