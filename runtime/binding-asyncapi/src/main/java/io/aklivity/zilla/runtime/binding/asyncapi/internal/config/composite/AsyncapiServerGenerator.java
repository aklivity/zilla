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

import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.CROSS_ORIGIN;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiChannelView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiOperationView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiParameterView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiSchemaView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiSecuritySchemeView;
import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig.Method;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpWithConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttTopicConfigBuilder;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttWithConfig;
import io.aklivity.zilla.runtime.binding.sse.config.SseConditionConfig;
import io.aklivity.zilla.runtime.binding.sse.config.SseWithConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpConditionConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsConditionConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public final class AsyncapiServerGenerator extends AsyncapiCompositeGenerator
{
    @Override
    protected AsyncapiCompositeConfig generate(
        AsyncapiBindingConfig binding,
        List<AsyncapiSchemaConfig> schemas)
    {
        List<NamespaceConfig> namespaces = schemas.stream()
            .map(schema -> new ServerNamespaceHelper(binding, schema))
            .map(helper -> NamespaceConfig.builder()
                .inject(helper::injectAll)
                .build())
            .toList();

        return new AsyncapiCompositeConfig(schemas, namespaces);
    }

    private final class ServerNamespaceHelper extends NamespaceHelper
    {
        private final CatalogsHelper catalogs;
        private final BindingsHelper bindings;

        private ServerNamespaceHelper(
            AsyncapiBindingConfig config,
            AsyncapiSchemaConfig schema)
        {
            super(config, schema.apiLabel);
            this.catalogs = new CatalogsHelper(schema);
            this.bindings = new ServerBindingsHelper(schema);
        }

        @Override
        protected <C> NamespaceConfigBuilder<C> injectComponents(
            NamespaceConfigBuilder<C> namespace)
        {
            return namespace
                .inject(catalogs::injectAll)
                .inject(bindings::injectAll);
        }

        private final class ServerBindingsHelper extends BindingsHelper
        {
            private final AsyncapiSchemaConfig schema;
            private final Map<String, NamespaceInjector> protocols;
            private final List<String> plain;
            private final List<String> secure;

            private ServerBindingsHelper(
                AsyncapiSchemaConfig schema)
            {
                this.schema = schema;
                this.protocols = Map.of(
                    "http", this::injectHttp,
                    "https", this::injectHttp,
                    "mqtt", this::injectMqtt,
                    "mqtts", this::injectMqtt);
                this.plain = List.of("http", "mqtt");
                this.secure = List.of("https", "mqtts");
            }

            @Override
            protected <C> NamespaceConfigBuilder<C> injectAll(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                        .inject(this::injectTcpServer)
                        .inject(this::injectTlsServer)
                        .inject(this::injectProtocols);
            }

            private <C> NamespaceConfigBuilder<C> injectTcpServer(
                NamespaceConfigBuilder<C> namespace)
            {
                final TcpOptionsConfig tcpOptions = config.options.tcp != null
                    ? config.options.tcp
                    : TcpOptionsConfig.builder()
                        .host("0.0.0.0")
                        .ports(Stream.of(schema)
                            .map(s -> s.asyncapi)
                            .flatMap(v -> v.servers.stream())
                            .filter(s -> plain.contains(s.protocol))
                            .mapToInt(s -> s.port)
                            .distinct()
                            .toArray())
                        .build();

                namespace
                    .binding()
                        .name("tcp_server0")
                        .type("tcp")
                        .kind(SERVER)
                        .options(tcpOptions)
                        .inject(this::injectTcpRoutes)
                        .inject(this::injectMetrics)
                        .build();

                return namespace;
            }

            private <C>BindingConfigBuilder<C> injectTcpRoutes(
                BindingConfigBuilder<C> binding)
            {
                Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.servers.stream())
                    .filter(s -> plain.contains(s.protocol))
                    .forEach(s -> binding.route()
                        .when(TcpConditionConfig::builder)
                            .port(s.port)
                            .build()
                        .exit(String.format("%s_server0", s.protocol))
                        .build());

                Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.servers.stream())
                    .filter(s -> secure.contains(s.protocol))
                    .forEach(s -> binding.route()
                        .when(TcpConditionConfig::builder)
                            .port(s.port)
                            .build()
                        .exit("tls_server0")
                        .build());

                return binding;
            }

            private <C> NamespaceConfigBuilder<C> injectTlsServer(
                NamespaceConfigBuilder<C> namespace)
            {
                if (Stream.of(schema)
                        .map(s -> s.asyncapi)
                        .flatMap(v -> v.servers.stream())
                        .filter(s -> secure.contains(s.protocol))
                        .count() != 0L)
                {
                    namespace.binding()
                        .name("tls_server0")
                        .type("tls")
                        .kind(SERVER)
                        .vault(config.qvault)
                        .options(config.options.tls)
                        .inject(this::injectTlsRoutes)
                        .build();
                }

                return namespace;
            }

            private <C>BindingConfigBuilder<C> injectTlsRoutes(
                BindingConfigBuilder<C> binding)
            {
                Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.servers.stream())
                    .filter(s -> secure.contains(s.protocol))
                    .forEach(s -> binding.route()
                        .when(TlsConditionConfig::builder)
                            .port(s.port)
                            .build()
                        .exit(String.format("%s_server0", s.protocol))
                        .build());

                return binding;
            }

            private <C> NamespaceConfigBuilder<C> injectProtocols(
                NamespaceConfigBuilder<C> namespace)
            {
                Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.servers.stream())
                    .map(s -> s.protocol)
                    .distinct()
                    .map(protocols::get)
                    .filter(p -> p != null)
                    .forEach(p -> p.inject(namespace));

                return namespace;
            }

            private <C> NamespaceConfigBuilder<C> injectHttp(
                NamespaceConfigBuilder<C> namespace)
            {
                namespace.inject(this::injectHttpServer);

                if (Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.operations.values().stream())
                    .filter(AsyncapiOperationView::hasBindingsSse)
                    .count() != 0L)
                {
                    namespace.inject(this::injectSseServer);
                }

                return namespace;
            }

            private <C> NamespaceConfigBuilder<C> injectHttpServer(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                    .binding()
                        .name("http_server0")
                        .type("http")
                        .kind(SERVER)
                        .options(HttpOptionsConfig::builder)
                            .access()
                                .policy(CROSS_ORIGIN)
                                .build()
                            .inject(this::injectHttpAuthorization)
                            .inject(this::injectHttpRequests)
                            .build()
                        .inject(this::injectHttpRoutes)
                        .inject(this::injectMetrics)
                        .build();
            }

            private <C> HttpOptionsConfigBuilder<C> injectHttpAuthorization(
                HttpOptionsConfigBuilder<C> options)
            {
                final HttpOptionsConfig httpOptions = config.options.http;
                if (httpOptions != null &&
                    httpOptions.authorization != null)
                {
                    options.authorization()
                        .name(httpOptions.authorization.qname)
                        .credentials(httpOptions.authorization.credentials)
                        .build();
                }

                return options;
            }

            private <C> HttpOptionsConfigBuilder<C> injectHttpRequests(
                HttpOptionsConfigBuilder<C> options)
            {
                Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.operations.values().stream())
                    .filter(AsyncapiOperationView::hasBindingsHttp)
                    .filter(AsyncapiOperationView::hasMessagesOrParameters)
                    .forEach(operation ->
                    {
                        options
                            .request()
                                .path(operation.channel.address)
                                .method(Method.valueOf(operation.bindings.http.method))
                                .inject(request -> injectHttpContent(request, operation))
                                .inject(request -> injectHttpPathParams(request, operation))
                            .build();
                    });

                return options;
            }

            private <C> HttpRequestConfigBuilder<C> injectHttpContent(
                HttpRequestConfigBuilder<C> request,
                AsyncapiOperationView operation)
            {
                if (operation.channel.hasMessages())
                {
                    request
                        .content(JsonModelConfig::builder)
                        .catalog()
                            .name("catalog0")
                            .inject(cataloged -> injectHttpContentSchemas(cataloged, operation))
                            .build()
                        .build();
                }

                return request;
            }

            private <C> CatalogedConfigBuilder<C> injectHttpContentSchemas(
                CatalogedConfigBuilder<C> cataloged,
                AsyncapiOperationView operation)
            {
                for (AsyncapiMessageView message : operation.channel.messages)
                {
                    cataloged.schema()
                        .version("latest")
                        .subject("%s-%s-payload".formatted(message.channel.name, message.name))
                        .build();
                }

                return cataloged;
            }

            private <C> HttpRequestConfigBuilder<C> injectHttpPathParams(
                HttpRequestConfigBuilder<C> request,
                AsyncapiOperationView operation)
            {
                if (operation.channel.hasParameters())
                {
                    for (AsyncapiParameterView parameter : operation.channel.parameters)
                    {
                        final AsyncapiSchemaView schema = parameter.schema;
                        if (schema != null && schema.type != null)
                        {
                            String modelType = schema.format != null
                                ? String.format("%s:%s", schema.type, schema.format)
                                : schema.type;

                            ModelConfig model = MODELS.get(modelType);

                            if (model == null)
                            {
                                model = JsonModelConfig.builder()
                                    .catalog()
                                        .name("catalog0")
                                        .schema()
                                            .version("latest")
                                            .subject("%s-params-%s".formatted(parameter.channel.name, parameter.name))
                                            .build()
                                        .build()
                                    .build();
                            }

                            request
                                .pathParam()
                                .name(parameter.name)
                                .model(model)
                                .build();
                        }
                    }
                }

                return request;
            }

            private <C>BindingConfigBuilder<C> injectHttpRoutes(
                BindingConfigBuilder<C> binding)
            {
                Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.operations.values().stream())
                    .forEach(operation ->
                    {
                        final String path = operation.channel.address.replaceAll(REGEX_ADDRESS_PARAMETER, "*");

                        if (operation.hasBindingsHttp())
                        {
                            binding
                                .route()
                                .exit(config.qname)
                                .when(HttpConditionConfig::builder)
                                    .header(":path", path)
                                    .header(":method", operation.bindings.http.method)
                                    .build()
                                .with(HttpWithConfig::builder)
                                    .compositeId(operation.compositeId)
                                    .build()
                                .inject(route -> injectHttpServerRouteGuarded(route, operation))
                                .build();
                        }
                        else if (operation.hasBindingsSse())
                        {
                            binding
                                .route()
                                .exit("sse_server0")
                                .when(HttpConditionConfig::builder)
                                    .header(":path", path)
                                    .header(":method", "GET")
                                    .build()
                                .build();
                        }
                    });

                return binding;
            }

            private <C> RouteConfigBuilder<C> injectHttpServerRouteGuarded(
                RouteConfigBuilder<C> route,
                AsyncapiOperationView operation)
            {
                if (operation.security != null && !operation.security.isEmpty())
                {
                    final AsyncapiSecuritySchemeView securityScheme = operation.security.get(0);
                    if (config.options.http.authorization != null &&
                        "oauth2".equals(securityScheme.type))
                    {
                        route
                            .guarded()
                                .name(config.options.http.authorization.qname)
                                .roles(securityScheme.scopes)
                                .build();
                    }
                }
                return route;
            }

            private <C> NamespaceConfigBuilder<C> injectSseServer(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                    .binding()
                        .name("sse_server0")
                        .type("sse")
                        .kind(SERVER)
                        .inject(this::injectSseRoutes)
                        .inject(this::injectMetrics)
                        .build();
            }

            private <C>BindingConfigBuilder<C> injectSseRoutes(
                BindingConfigBuilder<C> binding)
            {
                Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .filter(v -> v.servers.stream()
                        .anyMatch(s -> s.protocol.startsWith("http")))
                    .flatMap(v -> v.operations.values().stream())
                    .filter(AsyncapiOperationView::hasBindingsSse)
                    .forEach(operation ->
                    {
                        final String path = operation.channel.address.replaceAll(REGEX_ADDRESS_PARAMETER, "*");

                        binding
                            .route()
                            .exit(config.qname)
                            .when(SseConditionConfig::builder)
                                .path(path)
                                .build()
                            .with(SseWithConfig::builder)
                                .compositeId(operation.compositeId)
                                .build()
                            .build();
                    });

                return binding;
            }

            private <C> NamespaceConfigBuilder<C> injectMqtt(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                        .binding()
                            .name("mqtt_server0")
                            .type("mqtt")
                            .kind(SERVER)
                            .options(MqttOptionsConfig::builder)
                                .inject(this::injectMqttAuthorization)
                                .inject(this::injectMqttTopicsOptions)
                                .build()
                            .inject(this::injectMqttRoutes)
                            .inject(this::injectMetrics)
                            .build();
            }

            private <C> MqttOptionsConfigBuilder<C> injectMqttAuthorization(
                MqttOptionsConfigBuilder<C> options)
            {
                final MqttOptionsConfig mqttOptions = config.options.mqtt;
                if (mqttOptions != null &&
                    mqttOptions.authorization != null)
                {
                    options.authorization()
                        .name(mqttOptions.authorization.qname)
                        .credentials(mqttOptions.authorization.credentials)
                        .build();
                }

                return options;
            }

            private <C> MqttOptionsConfigBuilder<C> injectMqttTopicsOptions(
                MqttOptionsConfigBuilder<C> options)
            {
                Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.channels.values().stream())
                    .filter(AsyncapiChannelView::hasMessages)
                    .forEach(c -> c.messages.stream()
                        .forEach(m ->
                            options.topic()
                                .name(c.address.replaceAll(REGEX_ADDRESS_PARAMETER, "#"))
                                .inject(t -> injectMqttContentModel(t, m))
                                .inject(t -> injectMqttUserProperties(t, m))
                                .build()));

                return options;
            }

            private <C> MqttTopicConfigBuilder<C> injectMqttContentModel(
                MqttTopicConfigBuilder<C> topic,
                AsyncapiMessageView message)
            {
                injectPayloadModel(topic::content, message);
                return topic;
            }

            private <C> MqttTopicConfigBuilder<C> injectMqttUserProperties(
                MqttTopicConfigBuilder<C> topic,
                AsyncapiMessageView message)
            {
                if (message.hasTraits())
                {
                    message.traits.stream()
                        .filter(t -> t.headers != null)
                        .filter(t -> t.headers.properties != null)
                        .flatMap(t -> t.headers.properties.keySet().stream())
                        .forEach(property ->
                            topic
                                .userProperty()
                                .name(property)
                                .value(JsonModelConfig::builder)
                                .catalog()
                                    .name("catalog0")
                                    .schema()
                                        .subject("%s-%s-header-%s".formatted(message.channel.name, message.name, property))
                                        .version("latest")
                                        .build()
                                    .build()
                                .build());
                }

                return topic;
            }

            private <C> BindingConfigBuilder<C> injectMqttRoutes(
                BindingConfigBuilder<C> binding)
            {
                Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.operations.values().stream())
                    .forEach(o ->
                        binding.inject(b -> injectMqttRoute(b, o)));

                binding.route()
                    .exit(config.qname)
                    .when(MqttConditionConfig::builder)
                        .session()
                            .clientId("*")
                            .build()
                        .build()
                    .with(MqttWithConfig::builder)
                        .compositeId(schema.asyncapi.compositeId)
                        .build()
                    .build();

                return binding;
            }

            private <C> BindingConfigBuilder<C> injectMqttRoute(
                BindingConfigBuilder<C> binding,
                AsyncapiOperationView operation)
            {
                String topic = operation.channel.address.replaceAll(REGEX_ADDRESS_PARAMETER, "#");

                if ("send".equals(operation.action))
                {
                    binding.route()
                        .exit(config.qname)
                        .when(MqttConditionConfig::builder)
                            .publish()
                                .topic(topic)
                                .build()
                            .build()
                        .with(MqttWithConfig::builder)
                            .compositeId(operation.compositeId)
                            .build()
                        .build();
                }
                else if ("receive".equals(operation.action))
                {
                    binding.route()
                        .exit(config.qname)
                        .when(MqttConditionConfig::builder)
                            .subscribe()
                                .topic(topic)
                                .build()
                            .build()
                        .with(MqttWithConfig::builder)
                            .compositeId(schema.asyncapi.compositeId)
                            .build()
                        .build();
                }

                return binding;
            }
        }
    }
}
