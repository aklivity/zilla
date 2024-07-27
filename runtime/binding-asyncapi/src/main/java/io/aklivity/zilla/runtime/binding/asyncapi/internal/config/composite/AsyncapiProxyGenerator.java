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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config.composite;

import static io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithConfig.EVENT_ID_DEFAULT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.PROXY;
import static java.util.function.Function.identity;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeConditionConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeRouteConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiRouteConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.http.kafka.AsyncapiHttpKafkaFilter;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.http.kafka.AsyncapiHttpKafkaOperationBinding;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.sse.kafka.AsyncapiSseKafkaFilter;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiChannelView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiCorrelationIdView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiOperationView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiReplyView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiSchemaItemView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiSchemaView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiSecuritySchemeView;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchFilterConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchMergeConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceConfigBuilder;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionKind;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithConfigBuilder;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithFilterConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;
import io.aklivity.zilla.specs.binding.asyncapi.internal.types.MqttQoS;

public final class AsyncapiProxyGenerator extends AsyncapiCompositeGenerator
{
    @Override
    protected AsyncapiCompositeConfig generate(
        AsyncapiBindingConfig binding,
        List<AsyncapiSchemaConfig> schemas)
    {
        // TODO: each ProxyNamespaceBuilder should represent a pair of schemas
        //       mapped by binding routes
        List<NamespaceConfig> namespaces = new LinkedList<>();
        List<AsyncapiCompositeRouteConfig> routes = new LinkedList<>();
        for (AsyncapiSchemaConfig schema : schemas)
        {
            Function<String, AsyncapiSchemaConfig> resolveSchema = schemas.stream()
                .collect(Collectors.toMap(s -> s.apiLabel, identity()))::get;
            NamespaceHelper helper = new ProxyNamespaceHelper(binding, schema, resolveSchema);
            NamespaceConfig namespace = NamespaceConfig.builder()
                .inject(helper::injectAll)
                .build();
            namespaces.add(namespace);

            Matcher routed = Pattern.compile("(http|sse|mqtt)_kafka_proxy0").matcher("");
            final int apiId = schema.schemaId;

            // routes (apiId + operationId) -> (apiId + operationId)
            // convert to schemaId + operationId -> compositeId + affinity (references apiId + operationId)
            // note: server must deduce context from typed extension unless correlating affinity can be "stamped"
            namespace.bindings.stream()
                .filter(b -> routed.reset(b.type).matches())
                .filter(b -> schema.asyncapi.servers.stream().anyMatch(s -> s.protocol.startsWith(routed.group(1))))
                .forEach(b ->
                {
                    final long routeId = b.resolveId.applyAsLong(b.name); // TODO: b.resolveId not set yet?
                    final String operationType = routed.group(1);
                    final int operationTypeId = binding.supplyTypeId.applyAsInt(operationType);

                    routes.add(new AsyncapiCompositeRouteConfig(
                        routeId,
                        new AsyncapiCompositeConditionConfig(
                            apiId,
                            operationTypeId)));
                });
        }

        return new AsyncapiCompositeConfig(schemas, namespaces, routes);
    }

    private final class ProxyNamespaceHelper extends NamespaceHelper
    {
        private final CatalogsHelper catalogs;
        private final BindingsHelper bindings;
        private final Function<String, AsyncapiSchemaConfig> resolveSchema;

        private ProxyNamespaceHelper(
            AsyncapiBindingConfig config,
            AsyncapiSchemaConfig schema,
            Function<String, AsyncapiSchemaConfig> resolveSchema)
        {
            super(config, schema);
            this.resolveSchema = resolveSchema;
            this.catalogs = new CatalogsHelper();
            this.bindings = new ProxyBindingsHelper();
        }

        protected <C> NamespaceConfigBuilder<C> injectComponents(
            NamespaceConfigBuilder<C> namespace)
        {
            return namespace
                    .inject(catalogs::injectAll)
                    .inject(bindings::injectAll);
        }

        private final class ProxyRouteHelper
        {
            private final List<ProxyOperationHelper> when;
            private final ProxyOperationHelper with;

            private ProxyRouteHelper(
                AsyncapiRouteConfig route)
            {
                this.when = route.when.stream()
                        .filter(c -> schema.apiLabel.equals(c.apiId))
                        .map(c -> new ProxyOperationHelper(schema, c.operationId))
                        .toList();
                this.with = new ProxyOperationHelper(resolveSchema.apply(route.with.apiId), route.with.operationId);
            }

            private boolean hasWhenProtocol(
                Predicate<String> protocol)
            {
                return when.stream().allMatch(s -> hasProtocol(s, protocol));
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

            private ProxyOperationHelper(
                AsyncapiSchemaConfig schema,
                String operationId)
            {
                this.schema = schema;
                this.operationId = operationId;
            }
        }

        private final class ProxyBindingsHelper extends BindingsHelper
        {
            private final BindingsHelper httpKafka;
            private final BindingsHelper sseKafka;
            private final BindingsHelper mqttKafka;

            private ProxyBindingsHelper()
            {
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
                    .name("sse_kafka_proxy0")
                    .type("sse-kafka")
                    .kind(PROXY)
                    .inject(this::injectMetrics)
                    .inject(this::injectMqttKafkaOptions)
                    .inject(this::injectMqttKafkaRoutes)
                    .build();
            }

            private <C> BindingConfigBuilder<C> injectMqttKafkaOptions(
                BindingConfigBuilder<C> binding)
            {
                return binding.options(MqttKafkaOptionsConfig::builder)
                    .topics()
                        .sessions(config.options.mqttKafka.channels.sessions)
                        .messages(config.options.mqttKafka.channels.messages)
                        .retained(config.options.mqttKafka.channels.retained)
                    .build()
                    .publish()
                        .qosMax(MqttQoS.EXACTLY_ONCE.name().toLowerCase())
                        .build()
                    .build();
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

                        AsyncapiOperationView mqttOp = mqttOpsById.get(condition.operationId);
                        if (mqttOp == null)
                        {
                            for (AsyncapiOperationView mqttAnyOp : mqttOpsById.values())
                            {
                                String kafkaOpId = route.with.operationId != null
                                    ? route.with.operationId
                                    : mqttAnyOp.name;

                                AsyncapiOperationView kafkaOp = kafkaOpsById.get(kafkaOpId);

                                if (kafkaOp != null)
                                {
                                    injectMqttKafkaRoute(binding, mqttAnyOp, kafkaOp);
                                }
                            }
                        }
                        else
                        {
                            AsyncapiOperationView kafkaOp = kafkaOpsById.get(route.with.operationId);
                            binding.inject(b -> injectMqttKafkaRoute(b, mqttOp, kafkaOp));
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
                final String messages = config.options.mqttKafka.channels.messages;

                binding.route()
                    .exit(config.qname)
                    .when(MqttKafkaConditionConfig::builder)
                        .topic(mqttOperation.channel.address)
                        .kind(kind)
                        .build()
                    .with(MqttKafkaWithConfig::builder)
                        .messages(messages.replaceAll("\\{([^{}]*)\\}", "\\${params.$1}"))
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
                        Map<String, AsyncapiOperationView> httpOpsById = condition.schema.asyncapi.operations;

                        AsyncapiOperationView httpOp = httpOpsById.get(condition.operationId);
                        if (httpOp == null)
                        {
                            for (AsyncapiOperationView httpAnyOp : httpOpsById.values())
                            {
                                String kafkaOpId = route.with.operationId != null
                                    ? route.with.operationId
                                    : httpAnyOp.name;

                                AsyncapiOperationView kafkaOp = kafkaOpsById.get(kafkaOpId);

                                if (kafkaOp != null)
                                {
                                    injectSseKafkaRoute(binding, httpAnyOp, kafkaOp);
                                }
                            }
                        }
                        else
                        {
                            AsyncapiOperationView kafkaOp = kafkaOpsById.get(route.with.operationId);
                            binding.inject(b -> injectSseKafkaRoute(b, httpOp, kafkaOp));
                        }
                    }
                }

                return binding;
            }

            private <C> BindingConfigBuilder<C> injectSseKafkaRoute(
                BindingConfigBuilder<C> binding,
                AsyncapiOperationView sseOperation,
                AsyncapiOperationView kafkaOperation)
            {
                if (sseOperation.hasBindingsSse())
                {
                    binding.route()
                        .exit(config.qname)
                        .when(SseKafkaConditionConfig::builder)
                            .path(sseOperation.channel.address)
                            .build()
                            .inject(r -> injectSseKafkaRouteWith(r, sseOperation, kafkaOperation))
                            .inject(r -> injectSseServerRouteGuarded(r, sseOperation.security))
                        .build();
                }

                return binding;
            }


            private <C> RouteConfigBuilder<C> injectSseKafkaRouteWith(
                RouteConfigBuilder<C> route,
                AsyncapiOperationView sseOperation,
                AsyncapiOperationView kafkaOperation)
            {
                if ("receive".equals(kafkaOperation.action))
                {
                    route.with(SseKafkaWithConfig.builder()
                            .topic(kafkaOperation.channel.address)
                            .eventId(EVENT_ID_DEFAULT)
                            .inject(w -> injectSseKafkaRouteWithFilters(w, sseOperation))
                            .build());
                }

                return route;
            }

            private <C> SseKafkaWithConfigBuilder<C> injectSseKafkaRouteWithFilters(
                SseKafkaWithConfigBuilder<C> with,
                AsyncapiOperationView sseOperation)
            {
                if (sseOperation.hasBindingsSseKafka())
                {
                    List<AsyncapiSseKafkaFilter> filters = sseOperation.bindings.sseKafka.filters;
                    if (filters != null)
                    {
                        for (AsyncapiSseKafkaFilter filter : filters)
                        {
                            SseKafkaWithFilterConfigBuilder<?> withFilter = with.filter();

                            String key = filter.key;
                            if (key != null)
                            {
                                key = AsyncapiIdentity.resolve(config.namespace, key);

                                withFilter.key(key);
                            }

                            Map<String, String> headers = filter.headers;
                            if (headers != null)
                            {
                                for (Map.Entry<String, String> header : headers.entrySet())
                                {
                                    String name = header.getKey();
                                    String value = header.getValue();

                                    value = AsyncapiIdentity.resolve(config.namespace, value);

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
                List<AsyncapiSecuritySchemeView> securitySchemes)
            {
                if (securitySchemes != null && !securitySchemes.isEmpty())
                {
                    AsyncapiSecuritySchemeView securityScheme = securitySchemes.get(0);

                    if ("oauth2".equals(securityScheme.type))
                    {
                        route
                            .guarded()
                            .name(String.format("%s:jwt0", config.namespace))
                            .inject(guarded -> injectGuardedRoles(guarded, securityScheme.scopes))
                            .build();
                    }
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

            private final Matcher headerLocation = HEADER_LOCATION_PATTERN.matcher("");
            private final Matcher parameters = PARAMETER_PATTERN.matcher("");

            private final List<ProxyRouteHelper> httpKafkaRoutes;

            private HttpKafkaBindingsHelper()
            {
                this.httpKafkaRoutes = config.routes.stream()
                        .map(ProxyRouteHelper::new)
                        .filter(r -> r.hasWhenProtocol(p -> p.startsWith("http")))
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

                        AsyncapiOperationView httpOp = httpOpsById.get(condition.operationId);
                        if (httpOp == null)
                        {
                            for (AsyncapiOperationView httpAnyOp : httpOpsById.values())
                            {
                                String kafkaOpId = route.with.operationId != null
                                    ? route.with.operationId
                                    : httpAnyOp.name;

                                AsyncapiOperationView kafkaOp = kafkaOpsById.get(kafkaOpId);

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
                AsyncapiOperationView httpOperation,
                AsyncapiOperationView kafkaOperation)
            {
                if (httpOperation.hasBindingsHttp())
                {
                    final AsyncapiChannelView httpChannel = httpOperation.channel;
                    final String httpMethod = httpOperation.bindings.http.method;
                    final String httpPath = httpChannel.address;

                    boolean async = httpChannel.messages.stream()
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
                                    binding.route()
                                        .exit(config.qname)
                                        .when(HttpKafkaConditionConfig::builder)
                                            .method(httpPeerOp.channel.address)
                                            .path(httpPeerOp.bindings.http.method)
                                            .build()
                                        .with(HttpKafkaWithConfig::builder)
                                            .produce()
                                                .topic(kafkaOperation.channel.address)
                                                .inject(w -> injectHttpKafkaRouteProduceWith(w, httpPeerOp, kafkaOperation))
                                                .build()
                                            .build()
                                        .build();
                                }
                            }
                        }
                    }

                    binding.route()
                        .exit(config.qname)
                        .when(HttpKafkaConditionConfig::builder)
                            .method(httpMethod)
                            .path(httpPath)
                            .build()
                        .inject(r -> injectHttpKafkaRouteWith(r, httpOperation, kafkaOperation))
                        .inject(r -> injectHttpServerRouteGuarded(r, httpOperation.security))
                        .build();
                }

                return binding;
            }

            private <C> RouteConfigBuilder<C> injectHttpKafkaRouteWith(
                RouteConfigBuilder<C> route,
                AsyncapiOperationView httpOperation,
                AsyncapiOperationView kafkaOperation)
            {
                switch (kafkaOperation.action)
                {
                case "receive":
                    route.with(HttpKafkaWithConfig::builder)
                        .fetch()
                            .topic(kafkaOperation.channel.address)
                            .inject(w -> injectHttpKafkaRouteFetchWith(w, httpOperation))
                            .build();
                    break;
                case "send":
                    route.with(HttpKafkaWithConfig::builder)
                        .produce()
                            .topic(kafkaOperation.channel.address)
                            .inject(w -> injectHttpKafkaRouteProduceWith(w, httpOperation, kafkaOperation))
                            .build();
                    break;
                }

                return route;
            }

            private <C> HttpKafkaWithFetchConfigBuilder<C> injectHttpKafkaRouteFetchWith(
                HttpKafkaWithFetchConfigBuilder<C> fetch,
                AsyncapiOperationView httpOperation)
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

                if (httpOperation.hasBindingsHttpKafka())
                {
                    List<AsyncapiHttpKafkaFilter> filters = httpOperation.bindings.httpKafka.filters;
                    if (filters != null)
                    {
                        for (AsyncapiHttpKafkaFilter filter : filters)
                        {
                            HttpKafkaWithFetchFilterConfigBuilder<?> withFilter = fetch.filter();

                            String key = filter.key;
                            if (key != null)
                            {
                                key = AsyncapiIdentity.resolve(config.namespace, key);

                                withFilter.key(key);
                            }

                            Map<String, String> headers = filter.headers;
                            if (headers != null)
                            {
                                for (Map.Entry<String, String> header : headers.entrySet())
                                {
                                    String name = header.getKey();
                                    String value = header.getValue();

                                    value = AsyncapiIdentity.resolve(config.namespace, value);

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
                AsyncapiOperationView kafkaOperation)
            {
                final List<String> httpParamNames = findParams(httpOperation.channel.address);

                final String key = !httpParamNames.isEmpty()
                    ? String.format("${params.%s}", httpParamNames.get(httpParamNames.size() - 1))
                    : "${idempotencyKey}";

                produce.acks("in_sync_replicas").key(key);

                AsyncapiChannelView httpChannel = httpOperation.channel;

                httpChannel.messages.forEach(message ->
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

                AsyncapiHttpKafkaOperationBinding httpKafkaBinding = httpOperation.bindings.httpKafka;
                if (httpKafkaBinding != null)
                {
                    String httpKafkaKey = httpKafkaBinding.key;
                    if (httpKafkaKey != null)
                    {
                        httpKafkaKey = AsyncapiIdentity.resolve(config.namespace, httpKafkaKey);

                        produce.key(httpKafkaKey);
                    }

                    Map<String, String> overrides = httpKafkaBinding.overrides;
                    if (overrides != null)
                    {
                        for (Map.Entry<String, String> override : overrides.entrySet())
                        {
                            String name = override.getKey();
                            String value = override.getValue();

                            value = AsyncapiIdentity.resolve(config.namespace, value);

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
                List<AsyncapiSecuritySchemeView> securitySchemes)
            {
                if (securitySchemes != null && !securitySchemes.isEmpty())
                {
                    AsyncapiSecuritySchemeView security = securitySchemes.get(0);

                    if ("oauth2".equals(security.type))
                    {
                        route
                            .guarded()
                            .name(String.format("%s:jwt0", config.namespace))
                            .inject(guarded -> injectGuardedRoles(guarded, security.scopes))
                            .build();
                    }
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
