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
package io.aklivity.zilla.runtime.binding.openapi.internal.config.composite;

import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.CROSS_ORIGIN;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiChannelView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiMessageView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiParameterView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiSecuritySchemeView;
import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig.Method;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpWithConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiSchemaView;
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

public final class OpenapiServerGenerator extends OpenapiCompositeGenerator
{
    @Override
    protected OpenapiCompositeConfig generate(
        OpenapiBindingConfig binding,
        List<OpenapiSchemaConfig> schemas)
    {
        List<NamespaceConfig> namespaces = schemas.stream()
            .map(schema -> new ServerNamespaceHelper(binding, schema))
            .map(helper -> NamespaceConfig.builder()
                .inject(helper::injectAll)
                .build())
            .toList();

        return new OpenapiCompositeConfig(schemas, namespaces);
    }

    private final class ServerNamespaceHelper extends NamespaceHelper
    {
        private final CatalogsHelper catalogs;
        private final BindingsHelper bindings;

        private ServerNamespaceHelper(
            OpenapiBindingConfig config,
            OpenapiSchemaConfig schema)
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
            private final OpenapiSchemaConfig schema;
            private final List<String> plain;
            private final List<String> secure;
            private final Map<String, String> plainBySecure;

            private ServerBindingsHelper(
                OpenapiSchemaConfig schema)
            {
                this.schema = schema;
                this.plain = List.of("http");
                this.secure = List.of("https");
                this.plainBySecure = Map.of(
                    "https", "http"
                );
            }

            @Override
            protected <C> NamespaceConfigBuilder<C> injectAll(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                        .inject(this::injectTcpServer)
                        .inject(this::injectTlsServer)
                        .inject(this::injectHttpServer);
            }

            private <C> NamespaceConfigBuilder<C> injectTcpServer(
                NamespaceConfigBuilder<C> namespace)
            {
                final TcpOptionsConfig tcpOptions = config.options.tcp != null
                    ? config.options.tcp
                    : TcpOptionsConfig.builder()
                        .host("0.0.0.0")
                        .ports(Stream.of(schema)
                            .map(s -> s.openapi)
                            .flatMap(v -> v.servers.stream())
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
                    .map(s -> s.openapi)
                    .flatMap(v -> v.servers.stream())
                    .filter(s -> plain.contains(s.protocol))
                    .forEach(s -> binding.route()
                        .when(TcpConditionConfig::builder)
                            .port(s.port)
                            .build()
                        .exit(String.format("%s_server0", s.protocol))
                        .build());

                Stream.of(schema)
                    .map(s -> s.openapi)
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
                    .map(s -> s.openapi)
                    .flatMap(v -> v.servers.stream())
                    .anyMatch(s -> secure.contains(s.protocol)))
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
                    .map(s -> s.openapi)
                    .flatMap(v -> v.servers.stream())
                    .filter(s -> secure.contains(s.protocol))
                    .forEach(s -> binding.route()
                        .when(TlsConditionConfig::builder)
                            .port(s.port)
                            .build()
                        .exit(String.format("%s_server0", plainBySecure.get(s.protocol)))
                        .build());

                return binding;
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
                    .map(s -> s.openapi)
                    .flatMap(v -> v.operations.values().stream())
                    .filter(OpenapiOperationView::hasBindingsHttp)
                    .filter(OpenapiOperationView::hasMessagesOrParameters)
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
                OpenapiOperationView operation)
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
                OpenapiOperationView operation)
            {
                for (OpenapiMessageView message : operation.messages)
                {
                    cataloged.schema()
                        .subject("%s-%s-value".formatted(message.channel.name, message.name))
                        .version("latest")
                        .build();
                }

                return cataloged;
            }

            private <C> HttpRequestConfigBuilder<C> injectHttpPathParams(
                HttpRequestConfigBuilder<C> request,
                OpenapiOperationView operation)
            {
                if (operation.channel.hasParameters())
                {
                    for (OpenapiParameterView parameter : operation.channel.parameters)
                    {
                        final OpenapiSchemaView schema = parameter.schema;
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
                    .map(s -> s.openapi)
                    .flatMap(v -> v.operations.values().stream())
                    .forEach(operation ->
                    {
                        final String path = operation.channel.address.replaceAll(REGEX_ADDRESS_PARAMETER, "*");

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
                    });

                return binding;
            }

            private <C> RouteConfigBuilder<C> injectHttpServerRouteGuarded(
                RouteConfigBuilder<C> route,
                OpenapiOperationView operation)
            {
                if (operation.security != null && !operation.security.isEmpty())
                {
                    final OpenapiSecuritySchemeView securityScheme = operation.security.get(0);
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
        }
    }
}
