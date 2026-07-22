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

import static io.aklivity.zilla.config.engine.KindConfig.CLIENT;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.aklivity.zilla.config.binding.http.HttpOptionsConfig;
import io.aklivity.zilla.config.binding.http.HttpOptionsConfigBuilder;
import io.aklivity.zilla.config.binding.http.HttpParamConfigBuilder;
import io.aklivity.zilla.config.binding.http.HttpRequestConfig;
import io.aklivity.zilla.config.binding.http.HttpRequestConfigBuilder;
import io.aklivity.zilla.config.binding.http.HttpResponseConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.config.engine.NamespaceConfigBuilder;
import io.aklivity.zilla.config.engine.SchemaConfigBuilder;
import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiCompositeConditionConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiCompositeRouteConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSchemaConfig;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiHeaderView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiMediaTypeView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiSchemaView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiServerView;

public final class OpenapiClientGenerator extends OpenapiCompositeGenerator
{
    @Override
    protected OpenapiCompositeConfig generate(
        OpenapiBindingConfig binding,
        List<OpenapiSchemaConfig> schemas)
    {
        List<NamespaceConfig> namespaces = new LinkedList<>();
        List<OpenapiCompositeRouteConfig> routes = new LinkedList<>();
        for (OpenapiSchemaConfig schema  : schemas)
        {
            NamespaceHelper helper = new ClientNamespaceHelper(binding, schema);
            NamespaceConfig namespace = NamespaceConfig.builder()
                    .inject(helper::injectAll)
                    .build();

            namespaces.add(namespace);

            final String httpType = "http";
            final int httpTypeId = binding.supplyTypeId.applyAsInt(httpType);
            namespace.bindings.stream()
                .filter(b -> httpType.equals(b.type))
                .forEach(b ->
                {
                    final long routeId = binding.supplyBindingId.applyAsLong(namespace, b);

                    final OpenapiCompositeConditionConfig when = new OpenapiCompositeConditionConfig(
                        schema.schemaId,
                        httpTypeId);

                    routes.add(new OpenapiCompositeRouteConfig(routeId, when));
                });
        }

        return new OpenapiCompositeConfig(schemas, namespaces, routes);
    }

    private final class ClientNamespaceHelper extends NamespaceHelper
    {
        private final CatalogsHelper catalogs;
        private final BindingsHelper bindings;

        private ClientNamespaceHelper(
            OpenapiBindingConfig config,
            OpenapiSchemaConfig schema)
        {
            super(config, schema.specLabel);
            this.catalogs = new ClientCatalogsHelper(schema);
            this.bindings = new ClientBindingsHelper(schema);
        }

        protected <C> NamespaceConfigBuilder<C> injectComponents(
            NamespaceConfigBuilder<C> namespace)
        {
            return namespace
                    .inject(catalogs::injectAll)
                    .inject(bindings::injectAll);
        }

        private final class ClientCatalogsHelper extends CatalogsHelper
        {
            private ClientCatalogsHelper(
                OpenapiSchemaConfig schema)
            {
                super(schema);
            }

            public <C> NamespaceConfigBuilder<C> injectAll(
                NamespaceConfigBuilder<C> namespace)
            {
                injectInlineResponses(namespace);
                return namespace;
            }
        }

        private final class ClientBindingsHelper extends BindingsHelper
        {
            private final OpenapiSchemaConfig schema;
            private final List<String> secure;

            private ClientBindingsHelper(
                OpenapiSchemaConfig schema)
            {
                this.schema = schema;
                this.secure = List.of("https");
            }

            @Override
            protected <C> NamespaceConfigBuilder<C> injectAll(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                        .inject(this::injectHttpClient)
                        .inject(this::injectTlsClient);
            }

            private List<URI> resolveServers()
            {
                return config.resolveBaseURLs(schema.specLabel);
            }

            private boolean anySecure()
            {
                return resolveServers().stream().anyMatch(server -> secure.contains(server.getScheme()));
            }

            private <C> NamespaceConfigBuilder<C> injectHttpClient(
                NamespaceConfigBuilder<C> namespace)
            {
                if (anySecure())
                {
                    namespace
                        .binding()
                            .name("http_client0")
                            .type("http")
                            .kind(CLIENT)
                            .options(HttpOptionsConfig::builder)
                                .inject(options -> injectHttpAuthorization(options, schema))
                                .inject(this::injectHttpRequests)
                                .build()
                            .inject(this::injectMetrics)
                            .exit("tls_client0")
                            .build();
                }
                else
                {
                    namespace
                        .binding()
                            .name("http_client0")
                            .type("http")
                            .kind(CLIENT)
                            .options(HttpOptionsConfig::builder)
                                .inject(options -> injectHttpAuthorization(options, schema))
                                .inject(this::injectHttpRequests)
                                .build()
                            .inject(this::injectMetrics)
                            .exit("sys:tcp_client")
                            .build();
                }

                return namespace;
            }

            private <C> NamespaceConfigBuilder<C> injectTlsClient(
                NamespaceConfigBuilder<C> namespace)
            {
                if (anySecure())
                {
                    namespace
                        .binding()
                            .name("tls_client0")
                            .type("tls")
                            .kind(CLIENT)
                            .inject(this::injectMetrics)
                            .vault(config.qvault)
                            .exit("sys:tcp_client")
                            .build();
                }

                return namespace;
            }

            private <C> HttpOptionsConfigBuilder<C> injectHttpRequests(
                HttpOptionsConfigBuilder<C> options)
            {
                final List<URI> servers = resolveServers();

                Stream.of(schema)
                    .map(s -> s.openapi)
                    .flatMap(v -> v.operations.values().stream())
                    .filter(OpenapiOperationView::hasResponses)
                    .forEach(operation ->
                        servers.forEach(server ->
                            options
                                .request()
                                    .path(OpenapiServerView.requestPath(server, operation.path))
                                    .method(HttpRequestConfig.Method.valueOf(operation.method))
                                    .inject(request -> injectHttpResponses(request, operation))
                                    .build()
                                .build()));

                return options;
            }

            private <C> HttpRequestConfigBuilder<C> injectHttpResponses(
                HttpRequestConfigBuilder<C> request,
                OpenapiOperationView operation)
            {
                if (operation.hasResponses())
                {
                    operation.responses.values().stream()
                        .filter(response -> !"default".equals(response.status))
                        .filter(response -> response.content != null)
                        .forEach(response ->
                        {
                            response.content.values().forEach(typed ->
                            {
                                request
                                    .response()
                                        .status(Integer.parseInt(response.status))
                                        .contentType(typed.name)
                                        .inject(r -> injectResponseHeaders(r, response.operation, response.headers))
                                        .inject(r -> injectResponseContent(r, response.operation, typed))
                                        .build()
                                    .build();
                            });
                        });
                }

                return request;
            }

            private <C> HttpResponseConfigBuilder<C> injectResponseContent(
                HttpResponseConfigBuilder<C> response,
                OpenapiOperationView operation,
                OpenapiMediaTypeView typed)
            {
                return response.content(JsonModelConfig::builder)
                    .catalog()
                        .name("catalog0")
                        .schema()
                            .inject(s -> injectResponseContentSchema(s, operation, typed))
                            .build()
                        .build()
                    .build();
            }

            private <C> SchemaConfigBuilder<C> injectResponseContentSchema(
                SchemaConfigBuilder<C> schema,
                OpenapiOperationView operation,
                OpenapiMediaTypeView typed)
            {
                return schema.subject("%s-%s-value".formatted(operation.id, typed.name));
            }

            private <C> HttpResponseConfigBuilder<C> injectResponseHeaders(
                HttpResponseConfigBuilder<C> response,
                OpenapiOperationView operation,
                Map<String, OpenapiHeaderView> headers)
            {
                if (headers != null)
                {
                    for (OpenapiHeaderView header : headers.values())
                    {
                        if (header.schema != null)
                        {
                            response
                                .header()
                                .name(header.name)
                                .inject(p -> injectResponseHeader(p, header.schema))
                                .build();
                        }
                    }
                }
                return response;
            }

            private <C> HttpParamConfigBuilder<C> injectResponseHeader(
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
        }
    }
}
