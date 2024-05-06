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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.CROSS_ORIGIN;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiMediaType;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiOperation;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiParameter;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiSchema;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiPathView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiSchemaView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiServerView;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpConditionConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineSchemaConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.GuardedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public final class OpenapiServerNamespaceGenerator extends OpenapiNamespaceGenerator
{
    private static final String JWT = "jwt";

    @Override
    public NamespaceConfig generate(
        BindingConfig binding,
        OpenapiNamespaceConfig namespaceConfig)
    {
        final OpenapiOptionsConfig options = binding.options != null ? (OpenapiOptionsConfig) binding.options : EMPTY_OPTIONS;
        final List<MetricRefConfig> metricRefs = binding.telemetryRef != null ?
            binding.telemetryRef.metricRefs : emptyList();
        final List<OpenapiServerView> servers = namespaceConfig.servers;

        final String qvault = String.format("%s:%s", binding.namespace, binding.vault);

        final String namespace = String.join("+", namespaceConfig.openapiLabels);
        return NamespaceConfig.builder()
                .name(String.format("%s/%s", binding.qname, namespace))
                .inject(n -> injectNamespaceMetric(n, !metricRefs.isEmpty()))
                .inject(n -> injectCatalog(n, namespaceConfig.openapis))
                .inject(n -> injectTcpServer(n, servers, options, metricRefs))
                .inject(n -> injectTlsServer(n, qvault, servers, options, metricRefs))
                .inject(n -> injectHttpServer(n, binding.qname, namespaceConfig.openapis, servers, options, metricRefs))
                .build();
    }

    private <C> BindingConfigBuilder<C> injectPlainTcpRoute(
        BindingConfigBuilder<C> binding,
        int[] httpPorts,
        boolean secure)
    {
        if (!secure)
        {
            binding
                .route()
                    .when(TcpConditionConfig::builder)
                        .ports(httpPorts)
                        .build()
                    .exit("http_server0")
                    .build();
        }
        return binding;
    }

    private <C> BindingConfigBuilder<C> injectTlsTcpRoute(
        BindingConfigBuilder<C> binding,
        int[] httpsPorts)
    {
        if (httpsPorts.length > 0)
        {
            binding
                .route()
                    .when(TcpConditionConfig::builder)
                        .ports(httpsPorts)
                        .build()
                    .exit("tls_server0")
                    .build();
        }
        return binding;
    }

    private <C> NamespaceConfigBuilder<C> injectTlsServer(
        NamespaceConfigBuilder<C> namespace,
        String vault,
        List<OpenapiServerView> servers,
        OpenapiOptionsConfig options,
        List<MetricRefConfig> metricRefs)
    {
        final int[] httpsPorts = resolvePortsForScheme("https", servers);
        final boolean secure =  httpsPorts.length > 0;

        if (secure)
        {
            final TlsOptionsConfig tlsOption = options.tls != null ? options.tls : null;
            namespace
                .binding()
                    .name("tls_server0")
                    .type("tls")
                    .kind(SERVER)
                    .options(tlsOption)
                    .vault(vault)
                    .exit("http_server0")
                    .inject(b -> this.injectMetrics(b, metricRefs, "tls"))
                    .build();
        }
        return namespace;
    }

    private <C> HttpOptionsConfigBuilder<C> injectHttpServerOptions(
        HttpOptionsConfigBuilder<C> options,
        HttpAuthorizationConfig authorization,
        boolean hasJwt)
    {
        if (hasJwt)
        {
            options.authorization(authorization).build();
        }
        return options;
    }

    private <C> HttpOptionsConfigBuilder<C> injectHttpServerRequests(
        HttpOptionsConfigBuilder<C> options,
        List<Openapi> openapis)
    {
        for (Openapi openapi : openapis)
        {
            for (String pathName : openapi.paths.keySet())
            {
                OpenapiPathView path = OpenapiPathView.of(openapi.paths.get(pathName));
                for (String methodName : path.methods().keySet())
                {
                    final OpenapiOperation operation = path.methods().get(methodName);
                    if (operation.requestBody != null ||
                        operation.parameters != null &&
                        !operation.parameters.isEmpty())
                    {
                        options
                            .request()
                                .path(pathName)
                                .method(HttpRequestConfig.Method.valueOf(methodName))
                                .inject(request -> injectContent(request, operation, openapi))
                                .inject(request -> injectParams(request, operation))
                                .build();
                    }
                }
            }
        }
        return options;
    }

    private <C> HttpRequestConfigBuilder<C> injectContent(
        HttpRequestConfigBuilder<C> request,
        OpenapiOperation operation,
        Openapi openApi)
    {
        if (operation.requestBody != null &&
            operation.requestBody.content != null &&
            !operation.requestBody.content.isEmpty())
        {
            OpenapiSchemaView schema = resolveSchemaForJsonContentType(operation.requestBody.content, openApi);
            if (schema != null)
            {
                request.
                    content(JsonModelConfig::builder)
                    .catalog()
                        .name(INLINE_CATALOG_NAME)
                        .schema()
                            .subject(schema.refKey())
                            .version(VERSION_LATEST)
                            .build()
                        .build()
                    .build();
            }
        }
        return request;
    }

    private <C> HttpRequestConfigBuilder<C> injectParams(
        HttpRequestConfigBuilder<C> request,
        OpenapiOperation operation)
    {
        if (operation != null && operation.parameters != null)
        {
            for (OpenapiParameter parameter : operation.parameters)
            {
                if (parameter.schema != null && parameter.schema.type != null)
                {
                    OpenapiSchema schema = parameter.schema;
                    String modelName = schema.format != null ? String.format("%s:%s", schema.type, schema.format) :
                        schema.type;
                    ModelConfig model = models.get(modelName);
                    if (model != null)
                    {
                        switch (parameter.in)
                        {
                        case "path":
                            request.
                                pathParam()
                                    .name(parameter.name)
                                    .model(model)
                                    .build();
                            break;
                        case "query":
                            request.
                                queryParam()
                                    .name(parameter.name)
                                    .model(model)
                                    .build();
                            break;
                        case "header":
                            request.
                                header()
                                    .name(parameter.name)
                                    .model(model)
                                    .build();
                            break;
                        }
                    }
                }
            }
        }
        return request;
    }

    private <C> BindingConfigBuilder<C> injectHttpServerRoutes(
        BindingConfigBuilder<C> binding,
        List<Openapi> openApis,
        String qname,
        String guardName,
        Map<String, String> securitySchemes)
    {
        for (Openapi openApi : openApis)
        {
            for (String item : openApi.paths.keySet())
            {
                OpenapiPathView path = OpenapiPathView.of(openApi.paths.get(item));
                for (String method : path.methods().keySet())
                {
                    binding
                        .route()
                            .exit(qname)
                            .when(HttpConditionConfig::builder)
                                .header(":path", item.replaceAll("\\{[^}]+\\}", "*"))
                                .header(":method", method)
                                .build()
                            .inject(route -> injectHttpServerRouteGuarded(route, path, method, guardName, securitySchemes))
                            .build();
                }
            }
        }

        return binding;
    }

    private <C> RouteConfigBuilder<C> injectHttpServerRouteGuarded(
        RouteConfigBuilder<C> route,
        OpenapiPathView path,
        String method,
        String guardName,
        Map<String, String> securitySchemes)
    {
        final List<Map<String, List<String>>> security = path.methods().get(method).security;
        final boolean hasJwt = !securitySchemes.isEmpty();

        if (security != null)
        {
            for (Map<String, List<String>> securityItem : security)
            {
                for (String securityItemLabel : securityItem.keySet())
                {
                    if (hasJwt && JWT.equalsIgnoreCase(securitySchemes.get(securityItemLabel)))
                    {
                        route
                            .guarded()
                                .name(guardName)
                                .inject(guarded -> injectGuardedRoles(guarded, securityItem.get(securityItemLabel)))
                                .build();
                    }
                }
            }
        }
        return route;
    }

    private <C> GuardedConfigBuilder<C> injectGuardedRoles(
        GuardedConfigBuilder<C> guarded,
        List<String> roles)
    {
        for (String role : roles)
        {
            guarded.role(role);
        }
        return guarded;
    }

    private <C> NamespaceConfigBuilder<C> injectCatalog(
        NamespaceConfigBuilder<C> namespace,
        List<Openapi> openapis)
    {
        final boolean injectCatalog = openapis.stream()
            .anyMatch(a -> a.components != null && a.components.schemas != null && !a.components.schemas.isEmpty());
        if (injectCatalog)
        {
            namespace
                .catalog()
                    .name(INLINE_CATALOG_NAME)
                    .type(INLINE_CATALOG_TYPE)
                    .options(InlineOptionsConfig::builder)
                        .subjects()
                            .inject(s -> this.injectSubjects(s, openapis))
                            .build()
                        .build()
                    .build();
        }

        return namespace;
    }

    private <C> NamespaceConfigBuilder<C> injectTcpServer(
        NamespaceConfigBuilder<C> namespace,
        List<OpenapiServerView> servers,
        OpenapiOptionsConfig options,
        List<MetricRefConfig> metricRefs)
    {
        final int[] allPorts = resolveAllPorts(servers);
        final int[] httpPorts = resolvePortsForScheme("http", servers);
        final int[] httpsPorts = resolvePortsForScheme("https", servers);
        final boolean secure =  httpsPorts.length > 0;

        final TcpOptionsConfig tcpOption = options.tcp != null ? options.tcp :
            TcpOptionsConfig.builder()
                .host("0.0.0.0")
                .ports(allPorts)
                .build();

        namespace
            .binding()
                .name("tcp_server0")
                .type("tcp")
                .kind(SERVER)
                .options(tcpOption)
                .inject(b -> this.injectPlainTcpRoute(b, httpPorts, secure))
                .inject(b -> this.injectTlsTcpRoute(b, httpsPorts))
                .inject(b -> this.injectMetrics(b, metricRefs, "tcp"))
                .build();

        return namespace;
    }

    private <C> NamespaceConfigBuilder<C> injectHttpServer(
        NamespaceConfigBuilder<C> namespace,
        String qname,
        List<Openapi> openapis,
        List<OpenapiServerView> servers,
        OpenapiOptionsConfig options,
        List<MetricRefConfig> metricRefs)
    {
        final Map<String, String> securitySchemes = resolveSecuritySchemes(openapis);
        final boolean hasJwt = !securitySchemes.isEmpty();
        final HttpOptionsConfig httpOptions = options.http;
        final String guardName = httpOptions != null ? httpOptions.authorization.name : null;
        final HttpAuthorizationConfig authorization = httpOptions != null ? httpOptions.authorization : null;
        final List<URI> serverUrls = findServersUrlWithScheme(null, servers);

        namespace
            .binding()
            .name("http_server0")
            .type("http")
            .kind(SERVER)
            .options(HttpOptionsConfig::builder)
            .access()
            .policy(CROSS_ORIGIN)
            .build()
            .inject(o -> this.injectHttpServerOptions(o, authorization, hasJwt))
            .inject(r -> this.injectHttpServerRequests(r, openapis))
            .build()
            .inject(b -> this.injectHttpServerRoutes(b, openapis, qname, guardName, securitySchemes))
            .inject(b -> this.injectMetrics(b, metricRefs, "http"))
            .build();

        return namespace;
    }

    private <C> InlineSchemaConfigBuilder<C> injectSubjects(
        InlineSchemaConfigBuilder<C> subjects,
        List<Openapi> openApis)
    {
        for (Openapi openApi : openApis)
        {
            try (Jsonb jsonb = JsonbBuilder.create())
            {
                for (Map.Entry<String, OpenapiSchema> entry : openApi.components.schemas.entrySet())
                {
                    OpenapiSchemaView schemaView = OpenapiSchemaView.of(openApi.components.schemas, entry.getValue());
                    OpenapiSchema schema = schemaView.ref() != null ? schemaView.ref() : entry.getValue();

                    subjects
                        .subject(entry.getKey())
                        .schema(jsonb.toJson(schema))
                        .version(VERSION_LATEST)
                        .build();
                }
            }
            catch (Exception ex)
            {
                rethrowUnchecked(ex);
            }
        }
        return subjects;
    }

    public int[] resolveAllPorts(
        List<OpenapiServerView> servers)
    {
        int[] ports = new int[servers.size()];
        for (int i = 0; i < servers.size(); i++)
        {
            OpenapiServerView server = servers.get(i);
            ports[i] = server.url().getPort();
        }
        return ports;
    }

    private Map<String, String> resolveSecuritySchemes(
        List<Openapi> openApis)
    {
        requireNonNull(openApis);
        Map<String, String> result = new Object2ObjectHashMap<>();
        for (Openapi openApi : openApis)
        {
            if (openApi.components != null &&
                openApi.components.securitySchemes != null)
            {
                for (String securitySchemeName : openApi.components.securitySchemes.keySet())
                {
                    String guardType = openApi.components.securitySchemes.get(securitySchemeName).bearerFormat;
                    if (JWT.equalsIgnoreCase(guardType))
                    {
                        result.put(securitySchemeName, guardType);
                    }
                }
            }
        }
        return result;
    }

    private OpenapiSchemaView resolveSchemaForJsonContentType(
        Map<String, OpenapiMediaType> content,
        Openapi openApi)
    {
        OpenapiMediaType mediaType = null;
        if (content != null)
        {
            for (String contentType : content.keySet())
            {
                if (jsonContentType.reset(contentType).matches())
                {
                    mediaType = content.get(contentType);
                    break;
                }
            }
        }

        return mediaType == null ? null : OpenapiSchemaView.of(openApi.components.schemas, mediaType.schema);
    }
}
