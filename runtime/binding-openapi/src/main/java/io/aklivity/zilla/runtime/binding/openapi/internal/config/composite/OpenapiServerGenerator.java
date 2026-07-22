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
package io.aklivity.zilla.runtime.binding.openapi.internal.config.composite;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.CROSS_ORIGIN;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import io.aklivity.zilla.config.binding.tcp.TcpConditionConfig;
import io.aklivity.zilla.config.binding.tcp.TcpOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfigBuilder;
import io.aklivity.zilla.config.engine.CatalogedConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.config.engine.NamespaceConfigBuilder;
import io.aklivity.zilla.config.engine.RouteConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpParamConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig.Method;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpWithConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsConditionConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSchemaConfig;
import io.aklivity.zilla.runtime.common.openapi.security.GuardedRef;
import io.aklivity.zilla.runtime.common.openapi.security.GuardedResolution;
import io.aklivity.zilla.runtime.common.openapi.security.OpenapiGuardResolver;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiSchemaView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiServerView;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public final class OpenapiServerGenerator extends OpenapiCompositeGenerator
{
    private static final List<String> TLS_ALPN_PROTOCOLS = List.of("h2", "http/1.1");

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
            super(config, schema.specLabel);
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

            private List<URI> resolveServers()
            {
                return config.resolveBaseURLs(schema.specLabel);
            }

            private <C> NamespaceConfigBuilder<C> injectTcpServer(
                NamespaceConfigBuilder<C> namespace)
            {
                final int[] ports = resolveServers().stream()
                    .mapToInt(URI::getPort)
                    .distinct()
                    .toArray();

                final TcpOptionsConfig tcpOptions = TcpOptionsConfig.builder()
                    .host("0.0.0.0")
                    .ports(ports)
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
                resolveServers().stream()
                    .collect(toMap(URI::getPort, identity(), (first, second) -> first, LinkedHashMap::new))
                    .values()
                    .forEach(server ->
                    {
                        if (plain.contains(server.getScheme()))
                        {
                            binding.route()
                                .when(TcpConditionConfig::builder)
                                    .port(server.getPort())
                                    .build()
                                .exit("http_server0")
                                .build();
                        }
                        else if (secure.contains(server.getScheme()))
                        {
                            binding.route()
                                .when(TcpConditionConfig::builder)
                                    .port(server.getPort())
                                    .build()
                                .exit("tls_server0")
                                .build();
                        }
                    });

                return binding;
            }

            private <C> NamespaceConfigBuilder<C> injectTlsServer(
                NamespaceConfigBuilder<C> namespace)
            {
                if (resolveServers().stream().anyMatch(server -> secure.contains(server.getScheme())))
                {
                    namespace.binding()
                        .name("tls_server0")
                        .type("tls")
                        .kind(SERVER)
                        .vault(config.qvault)
                        .options(TlsOptionsConfig::builder)
                            .alpn(TLS_ALPN_PROTOCOLS)
                            .build()
                        .inject(this::injectTlsRoutes)
                        .build();
                }

                return namespace;
            }

            private <C>BindingConfigBuilder<C> injectTlsRoutes(
                BindingConfigBuilder<C> binding)
            {
                resolveServers().stream()
                    .filter(server -> secure.contains(server.getScheme()))
                    .forEach(server ->
                        binding.route()
                            .when(TlsConditionConfig::builder)
                                .port(server.getPort())
                                .authority(server.getHost())
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
                            .inject(options -> injectHttpAuthorization(options, schema))
                            .inject(this::injectHttpRequests)
                            .build()
                        .inject(this::injectHttpRoutes)
                        .inject(this::injectMetrics)
                        .build();
            }

            private <C> HttpOptionsConfigBuilder<C> injectHttpRequests(
                HttpOptionsConfigBuilder<C> options)
            {
                final List<URI> servers = resolveServers();

                Stream.of(schema)
                    .map(s -> s.openapi)
                    .flatMap(v -> v.operations.values().stream())
                    .filter(OpenapiOperationView::hasRequestBodyOrParameters)
                    .filter(config::included)
                    .forEach(operation ->
                        servers.forEach(server ->
                            options
                                .request()
                                    .path(OpenapiServerView.requestPath(server, operation.path))
                                    .method(Method.valueOf(operation.method))
                                    .inject(request -> injectHttpParams(request, operation))
                                    .inject(request -> injectHttpContent(request, operation))
                                .build()));

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
                final List<URI> servers = resolveServers();

                Stream.of(schema)
                    .map(s -> s.openapi)
                    .flatMap(v -> v.operations.values().stream())
                    .filter(o -> o.servers != null)
                    .filter(this::allowed)
                    .filter(config::included)
                    .forEach(operation ->
                        servers.forEach(server ->
                            binding
                                .route()
                                .exit(config.qname)
                                .when(HttpConditionConfig::builder)
                                    .header(":path",
                                        OpenapiServerView.requestPath(server, operation.path)
                                            .replaceAll(REGEX_ADDRESS_PARAMETER, "*"))
                                    .header(":method", operation.method)
                                    .build()
                                .with(HttpWithConfig::builder)
                                    .compositeId(operation.compositeId)
                                    .build()
                                .inject(route -> injectHttpServerRouteGuarded(route, operation))
                                .build()));

                return binding;
            }

            private GuardedResolution resolveGuarded(
                OpenapiOperationView operation)
            {
                return OpenapiGuardResolver.resolve(
                    operation.id, schema.specLabel, operation.security, schema.security,
                    config.resolveId, config.supplyQName);
            }

            private boolean allowed(
                OpenapiOperationView operation)
            {
                final GuardedResolution resolution = resolveGuarded(operation);
                final boolean allowed = !resolution.denied();

                if (!allowed)
                {
                    denied.add(resolution.reason);
                }

                return allowed;
            }

            private <C> RouteConfigBuilder<C> injectHttpServerRouteGuarded(
                RouteConfigBuilder<C> route,
                OpenapiOperationView operation)
            {
                for (GuardedRef ref : resolveGuarded(operation).guarded)
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
    }
}
