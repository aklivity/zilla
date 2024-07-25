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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeConfig;
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
        NamespaceHelper helper = new ServerNamespaceHelper(binding, schemas);

        NamespaceConfig namespace = NamespaceConfig.builder()
            .inject(helper::injectAll)
            .build();

        return new AsyncapiCompositeConfig(namespace, schemas);
    }

    private final class ServerNamespaceHelper extends NamespaceHelper
    {
        private final CatalogsHelper catalogs;
        private final BindingsHelper bindings;

        private ServerNamespaceHelper(
            AsyncapiBindingConfig config,
            List<AsyncapiSchemaConfig> schemas)
        {
            super(config, schemas);
            this.catalogs = new CatalogsHelper();
            this.bindings = new ServerBindingsHelper();
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
            private static final Pattern PATTERN_PORT = Pattern.compile("\\:(\\d+)");

            private final Matcher matchPort = PATTERN_PORT.matcher("");

            private final Map<String, NamespaceInjector> protocols;
            private final List<String> plain;
            private final List<String> secure;

            private ServerBindingsHelper()
            {
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
                        .ports(config.options.specs.stream()
                            .flatMap(s -> s.servers.stream())
                            .map(s -> s.url != null ? s.url : s.host)
                            .filter(h -> matchPort.reset(h).matches())
                            .map(h -> matchPort.group(1))
                            .mapToInt(Integer::parseInt)
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
                schemas.stream()
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.servers.stream())
                    .filter(s -> plain.contains(s.protocol))
                    .forEach(s -> binding.route()
                        .when(TcpConditionConfig::builder)
                            .port(s.port)
                            .build()
                        .exit(String.format("%s_server0", s.protocol))
                        .build());

                schemas.stream()
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
                if (schemas.stream()
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
                schemas.stream()
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
                schemas.stream()
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.servers.stream())
                    .map(s -> s.protocol)
                    .distinct()
                    .map(protocols::get)
                    .forEach(p -> p.inject(namespace));

                return namespace;
            }

            private <C> NamespaceConfigBuilder<C> injectHttp(
                NamespaceConfigBuilder<C> namespace)
            {
                namespace.inject(this::injectHttpServer);

                if (schemas.stream()
                    .map(s -> s.asyncapi)
                    .filter(v -> v.servers.stream()
                        .anyMatch(s -> s.protocol.startsWith("http")))
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
                                .inject(this::injectHttpServerRequests)
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

            private <C> HttpOptionsConfigBuilder<C> injectHttpServerRequests(
                HttpOptionsConfigBuilder<C> options)
            {
                schemas.stream()
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
                                        .inject(cataloged -> injectHttpPathParamSchemas(cataloged, operation))
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

            private <C> CatalogedConfigBuilder<C> injectHttpPathParamSchemas(
                CatalogedConfigBuilder<C> cataloged,
                AsyncapiOperationView operation)
            {
                for (AsyncapiParameterView parameter : operation.channel.parameters)
                {
                    cataloged.schema()
                        .version("latest")
                        .subject("%s-params-%s".formatted(parameter.channel.name, parameter.name))
                        .build();
                }

                return cataloged;
            }

            private <C>BindingConfigBuilder<C> injectHttpRoutes(
                BindingConfigBuilder<C> binding)
            {
                schemas.stream()
                    .map(s -> s.asyncapi)
                    .filter(v -> v.servers.stream()
                        .anyMatch(s -> s.protocol.startsWith("http")))
                    .flatMap(v -> v.operations.values().stream())
                    .forEach(operation ->
                    {
                        final String path = operation.channel.address.replaceAll("\\{[^}]+\\}", "*");

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
                schemas.stream()
                    .map(s -> s.asyncapi)
                    .filter(v -> v.servers.stream()
                        .anyMatch(s -> s.protocol.startsWith("http")))
                    .flatMap(v -> v.operations.values().stream())
                    .filter(AsyncapiOperationView::hasBindingsSse)
                    .forEach(operation ->
                    {
                        final String path = operation.channel.address.replaceAll("\\{[^}]+\\}", "*");

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
                // TODO
                return namespace;
            }
        }
    }
}
