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
import static java.util.Objects.requireNonNull;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiBinding;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.MediaType;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenApi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Operation;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Parameter;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Schema;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Server;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.PathView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.SchemaView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.ServerView;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpConditionConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineSchemaConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.GuardedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;
import io.aklivity.zilla.runtime.model.core.config.IntegerModelConfig;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public final class OpenapiServerCompositeBindingAdapter implements CompositeBindingAdapterSpi
{
    private static final String INLINE_CATALOG_NAME = "catalog0";
    private static final String INLINE_CATALOG_TYPE = "inline";
    private static final String VERSION_LATEST = "latest";
    private static final Pattern JSON_CONTENT_TYPE = Pattern.compile("^application/(?:.+\\+)?json$");

    private final Matcher jsonContentType = JSON_CONTENT_TYPE.matcher("");
    private final Map<String, ModelConfig> models = Map.of(
        "string", StringModelConfig.builder().build(),
        "integer", IntegerModelConfig.builder().build()
    );

    @Override
    public String type()
    {
        return OpenapiBinding.NAME;
    }

    @Override
    public BindingConfig adapt(
        BindingConfig binding)
    {
        OpenapiOptionsConfig options = (OpenapiOptionsConfig) binding.options;
        OpenapiConfig openapiConfig = options.openapis.get(0);

        final OpenApi openApi = openapiConfig.openapi;
        final TlsOptionsConfig tlsOption = options.tls != null ? options.tls : null;
        final HttpOptionsConfig httpOptions = options.http;
        final String guardName = httpOptions != null ? httpOptions.authorization.name : null;
        final HttpAuthorizationConfig authorization = httpOptions != null ?  httpOptions.authorization : null;

        final int[] allPorts = resolveAllPorts(openApi);
        final int[] httpPorts = resolvePortsForScheme(openApi, "http");
        final int[] httpsPorts = resolvePortsForScheme(openApi, "https");
        final boolean secure = httpsPorts != null;
        final Map<String, String> securitySchemes = resolveSecuritySchemes(openApi);
        final boolean hasJwt = !securitySchemes.isEmpty();

        return BindingConfig.builder(binding)
            .composite()
                .name(String.format("%s/http", binding.qname))
                .inject(n -> this.injectCatalog(n, openApi))
                .binding()
                    .name("tcp_server0")
                    .type("tcp")
                    .kind(SERVER)
                    .options(TcpOptionsConfig::builder)
                        .host("0.0.0.0")
                        .ports(allPorts)
                        .build()
                    .inject(b -> this.injectPlainTcpRoute(b, httpPorts, secure))
                    .inject(b -> this.injectTlsTcpRoute(b, httpsPorts, secure))
                    .build()
                .inject(n -> this.injectTlsServer(n, tlsOption, secure))
                .binding()
                    .name("http_server0")
                    .type("http")
                    .kind(SERVER)
                    .options(HttpOptionsConfig::builder)
                        .access()
                            .policy(CROSS_ORIGIN)
                            .build()
                        .inject(o -> this.injectHttpServerOptions(o, authorization, hasJwt))
                        .inject(r -> this.injectHttpServerRequests(r, openApi))
                        .build()
                    .inject(b -> this.injectHttpServerRoutes(b, openApi, guardName, securitySchemes))
                    .build()
                .build()
            .build();
    }

    private int[] resolveAllPorts(
        OpenApi openApi)
    {
        int[] ports = new int[openApi.servers.size()];
        for (int i = 0; i < openApi.servers.size(); i++)
        {
            ServerView server = ServerView.of(openApi.servers.get(i));
            URI url = server.url();
            ports[i] = url.getPort();
        }
        return ports;
    }

    private int[] resolvePortsForScheme(
        OpenApi openApi,
        String scheme)
    {
        requireNonNull(scheme);
        int[] ports = null;
        URI url = findFirstServerUrlWithScheme(openApi, scheme);
        if (url != null)
        {
            ports = new int[] {url.getPort()};
        }
        return ports;
    }

    private URI findFirstServerUrlWithScheme(
        OpenApi openApi,
        String scheme)
    {
        requireNonNull(scheme);
        URI result = null;
        for (Server item : openApi.servers)
        {
            ServerView server = ServerView.of(item);
            if (scheme.equals(server.url().getScheme()))
            {
                result = server.url();
                break;
            }
        }
        return result;
    }

    private Map<String, String> resolveSecuritySchemes(
        OpenApi openApi)
    {
        requireNonNull(openApi);
        Map<String, String> result = new Object2ObjectHashMap<>();
        if (openApi.components != null &&
            openApi.components.securitySchemes != null)
        {
            for (String securitySchemeName : openApi.components.securitySchemes.keySet())
            {
                String guardType = openApi.components.securitySchemes.get(securitySchemeName).bearerFormat;
                if ("jwt".equals(guardType))
                {
                    result.put(securitySchemeName, guardType);
                }
            }
        }
        return result;
    }

    private <C> BindingConfigBuilder<C> injectPlainTcpRoute(
        BindingConfigBuilder<C> binding,
        int[] httpPorts,
        boolean secure)
    {
        if (secure)
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
        int[] httpsPorts,
        boolean secure)
    {
        if (secure)
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
        TlsOptionsConfig tls,
        boolean secure)
    {
        if (secure)
        {
            namespace
                .binding()
                    .name("tls_server0")
                    .type("tls")
                    .kind(SERVER)
                    .options(tls)
                    .vault("server")
                    .exit("http_server0")
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
        OpenApi openApi)
    {
        for (String pathName : openApi.paths.keySet())
        {
            PathView path = PathView.of(openApi.paths.get(pathName));
            for (String methodName : path.methods().keySet())
            {
                Operation operation = path.methods().get(methodName);
                if (operation.requestBody != null || operation.parameters != null && !operation.parameters.isEmpty())
                {
                    options
                        .request()
                            .path(pathName)
                            .method(HttpRequestConfig.Method.valueOf(methodName))
                            .inject(request -> injectContent(request, operation, openApi))
                            .inject(request -> injectParams(request, operation))
                            .build();
                }
            }
        }
        return options;
    }

    private <C> HttpRequestConfigBuilder<C> injectContent(
        HttpRequestConfigBuilder<C> request,
        Operation operation,
        OpenApi openApi)
    {
        if (operation.requestBody != null && operation.requestBody.content != null && !operation.requestBody.content.isEmpty())
        {
            SchemaView schema = resolveSchemaForJsonContentType(operation.requestBody.content, openApi);
            if (schema != null)
            {
                request.
                    content(JsonModelConfig::builder)
                    .catalog()
                        .name(INLINE_CATALOG_NAME)
                        .schema()
                            .subject(schema.refKey())
                            .build()
                        .build()
                    .build();
            }
        }
        return request;
    }

    private <C> HttpRequestConfigBuilder<C> injectParams(
        HttpRequestConfigBuilder<C> request,
        Operation operation)
    {
        if (operation != null && operation.parameters != null)
        {
            for (Parameter parameter : operation.parameters)
            {
                if (parameter.schema != null && parameter.schema.type != null)
                {
                    ModelConfig model = models.get(parameter.schema.type);
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
        OpenApi openApi,
        String guardName,
        Map<String, String> securitySchemes)
    {
        for (String item : openApi.paths.keySet())
        {
            PathView path = PathView.of(openApi.paths.get(item));
            for (String method : path.methods().keySet())
            {
                binding
                    .route()
                        .exit("http_client0")
                        .when(HttpConditionConfig::builder)
                            .header(":path", item.replaceAll("\\{[^}]+\\}", "*"))
                            .header(":method", method)
                            .build()
                        .inject(route -> injectHttpServerRouteGuarded(route, path, method, guardName, securitySchemes))
                        .build();
            }
        }
        return binding;
    }

    private <C> RouteConfigBuilder<C> injectHttpServerRouteGuarded(
        RouteConfigBuilder<C> route,
        PathView path,
        String method,
        String guardName,
        Map<String, String> securitySchemes)
    {
        final List<Map<String, List<String>>> security = path.methods().get(method).security;
        final boolean hasJwt = securitySchemes.isEmpty();

        if (security != null)
        {
            for (Map<String, List<String>> securityItem : security)
            {
                for (String securityItemLabel : securityItem.keySet())
                {
                    if (hasJwt && "jwt".equals(securitySchemes.get(securityItemLabel)))
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

    private SchemaView resolveSchemaForJsonContentType(
        Map<String, MediaType> content,
        OpenApi openApi)
    {
        MediaType mediaType = null;
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
        return mediaType == null ? null : SchemaView.of(openApi.components.schemas, mediaType.schema);
    }

    private <C> NamespaceConfigBuilder<C> injectCatalog(
        NamespaceConfigBuilder<C> namespace,
        OpenApi openApi)
    {
        if (openApi.components != null &&
            openApi.components.schemas != null &&
            !openApi.components.schemas.isEmpty())
        {
            namespace
                .catalog()
                    .name(INLINE_CATALOG_NAME)
                    .type(INLINE_CATALOG_TYPE)
                    .options(InlineOptionsConfig::builder)
                        .subjects()
                            .inject(s -> this.injectSubjects(s, openApi))
                            .build()
                        .build()
                    .build();
        }
        return namespace;
    }

    private <C> InlineSchemaConfigBuilder<C> injectSubjects(
        InlineSchemaConfigBuilder<C> subjects,
        OpenApi openApi)
    {
        try (Jsonb jsonb = JsonbBuilder.create())
        {
            for (Map.Entry<String, Schema> entry : openApi.components.schemas.entrySet())
            {
                SchemaView schema = SchemaView.of(openApi.components.schemas, entry.getValue());
                subjects
                    .subject(entry.getKey())
                        .version(VERSION_LATEST)
                        .schema(jsonb.toJson(schema))
                        .build();
            }
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return subjects;
    }
}
