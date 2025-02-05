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
package io.aklivity.zilla.runtime.binding.openapi.internal.config.composite;

import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.CROSS_ORIGIN;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpParamConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig.Method;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpWithConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiSchemaView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiSecurityRequirementView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiSecuritySchemeView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiServerView;
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
            this.catalogs = new ServerCatalogsHelper(schema);
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

        private final class ServerCatalogsHelper extends CatalogsHelper
        {
            private ServerCatalogsHelper(
                OpenapiSchemaConfig schema)
            {
                super(schema);
            }

            public <C> NamespaceConfigBuilder<C> injectAll(
                NamespaceConfigBuilder<C> namespace)
            {
                injectInlineRequests(namespace);
                return namespace;
            }
        }

        private final class ServerBindingsHelper extends BindingsHelper
        {
            private final OpenapiSchemaConfig schema;
            private final List<String> plain;
            private final List<String> secure;

            private ServerBindingsHelper(
                OpenapiSchemaConfig schema)
            {
                this.schema = schema;
                this.plain = List.of("http");
                this.secure = List.of("https");
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
                            .mapToInt(s -> s.url.getPort())
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
                    .filter(s -> plain.contains(s.url.getScheme()))
                    .forEach(s -> binding.route()
                        .when(TcpConditionConfig::builder)
                            .port(s.url.getPort())
                            .build()
                        .exit("http_server0")
                        .build());

                Stream.of(schema)
                    .map(s -> s.openapi)
                    .flatMap(v -> v.servers.stream())
                    .filter(s -> secure.contains(s.url.getScheme()))
                    .forEach(s -> binding.route()
                        .when(TcpConditionConfig::builder)
                            .port(s.url.getPort())
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
                    .anyMatch(s -> secure.contains(s.url.getScheme())))
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
                    .filter(s -> secure.contains(s.url.getScheme()))
                    .forEach(s -> binding.route()
                        .when(TlsConditionConfig::builder)
                            .port(s.url.getPort())
                            .build()
                        .exit("http_server0")
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
                    .filter(OpenapiOperationView::hasRequestBodyOrParameters)
                    .forEach(operation ->
                    {
                        for (OpenapiServerView server : operation.servers)
                        {
                            options
                                .request()
                                    .path(server.requestPath(operation.path))
                                    .method(Method.valueOf(operation.method))
                                    .inject(request -> injectHttpParams(request, operation))
                                    .inject(request -> injectHttpContent(request, operation))
                                .build();
                        }
                    });

                return options;
            }

            private <C> HttpRequestConfigBuilder<C> injectHttpParams(
                HttpRequestConfigBuilder<C> request,
                OpenapiOperationView operation)
            {
                if (operation.hasParameters())
                {
                    operation.parameters.stream()
                        .filter(parameter -> parameter.schema != null)
                        .forEach(parameter ->
                        {
                            switch (parameter .in)
                            {
                            case "path":
                                request.inject(HttpRequestConfigBuilder::pathParam)
                                    .name(parameter.name)
                                    .inject(p -> injectHttpParam(p, parameter.schema))
                                    .build();
                                break;
                            case "query":
                                request.inject(HttpRequestConfigBuilder::queryParam)
                                    .name(parameter.name)
                                    .inject(p -> injectHttpParam(p, parameter.schema))
                                    .build();
                                break;
                            case "header":
                                request.inject(HttpRequestConfigBuilder::header)
                                    .name(parameter.name)
                                    .inject(p -> injectHttpParam(p, parameter.schema))
                                    .build();
                                break;
                            }
                        });
                }

                return request;
            }

            private <C> HttpParamConfigBuilder<C> injectHttpParam(
                HttpParamConfigBuilder<C> param,
                OpenapiSchemaView schema)
            {
                String format = schema.format;
                String type = schema.type;

                ModelConfig model = resolveModelBySchema(type, format);

                if (model != null)
                {
                    param.model(model);
                }

                return param;
            }

            private <C> HttpRequestConfigBuilder<C> injectHttpContent(
                HttpRequestConfigBuilder<C> request,
                OpenapiOperationView operation)
            {
                if (operation.hasRequestBody())
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
                if (operation.hasRequestBody())
                {
                    operation.requestBody.content.values()
                        .forEach(typed ->
                            cataloged.schema()
                                .subject("%s-%s-value".formatted(operation.id, typed.name))
                                .version("latest")
                                .build());
                }

                return cataloged;
            }

            private <C>BindingConfigBuilder<C> injectHttpRoutes(
                BindingConfigBuilder<C> binding)
            {
                Stream.of(schema)
                    .map(s -> s.openapi)
                    .flatMap(v -> v.operations.values().stream())
                    .filter(o -> o.servers != null)
                    .forEach(operation ->
                        operation.servers.forEach(server ->
                            binding
                                .route()
                                .exit(config.qname)
                                .when(HttpConditionConfig::builder)
                                    .header(":path", server.requestPath(operation.path).replaceAll(REGEX_ADDRESS_PARAMETER, "*"))
                                    .header(":method", operation.method)
                                    .build()
                                .with(HttpWithConfig::builder)
                                    .compositeId(operation.compositeId)
                                    .build()
                                .inject(route -> injectHttpServerRouteGuarded(route, operation))
                                .build()));

                return binding;
            }

            private <C> RouteConfigBuilder<C> injectHttpServerRouteGuarded(
                RouteConfigBuilder<C> route,
                OpenapiOperationView operation)
            {
                Map<String, OpenapiSecuritySchemeView> securitySchemes = operation.specification.components.securitySchemes;
                final List<List<OpenapiSecurityRequirementView>> security = operation.security;

                if (security != null)
                {
                    security.stream()
                        .flatMap(s -> s.stream())
                        .filter(r -> securitySchemes.containsKey(r.name))
                        .filter(r -> "jwt".equalsIgnoreCase(securitySchemes.get(r.name).bearerFormat))
                        .forEach(r ->
                            route
                                .guarded()
                                    .name(config.options.http.authorization.qname)
                                    .inject(guarded -> injectGuardedRoles(guarded, r.scopes))
                                    .build());
                }

                return route;
            }
        }
    }
}
