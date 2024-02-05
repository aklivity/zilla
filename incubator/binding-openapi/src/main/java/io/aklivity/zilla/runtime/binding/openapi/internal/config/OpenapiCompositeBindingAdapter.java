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

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.MINIMIZE_QUOTES;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;
import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.CROSS_ORIGIN;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static java.util.Objects.requireNonNull;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonPatch;
import jakarta.json.JsonPatchBuilder;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpResponseConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiBinding;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Header;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.MediaType;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenApi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Operation;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Parameter;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Response;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.ResponseByContentType;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Schema;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Server;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OperationView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OperationsView;
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
import io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfig;
import io.aklivity.zilla.runtime.model.core.config.IntegerModelConfig;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemOptionsConfig;

public final class OpenapiCompositeBindingAdapter implements CompositeBindingAdapterSpi
{
    private static final String INLINE_CATALOG_NAME = "catalog0";
    private static final String INLINE_CATALOG_TYPE = "inline";
    private static final String VERSION_LATEST = "latest";
    protected static final Pattern JSON_CONTENT_TYPE = Pattern.compile("^application/(?:.+\\+)?json$");

    private final Matcher jsonContentType = JSON_CONTENT_TYPE.matcher("");
    private final Map<String, ModelConfig> models = Map.of(
        "string", StringModelConfig.builder().build(),
        "integer", IntegerModelConfig.builder().build()
    );

    private OpenApi openApi;
    private int[] allPorts;
    private int[] httpPorts;
    private int[] httpsPorts;
    private boolean isPlainEnabled;
    private boolean isTlsEnabled;
    private Map<String, String> securitySchemes;
    private boolean isJwtEnabled;

    @Override
    public String type()
    {
        return OpenapiBinding.NAME;
    }

    private OpenapiCompositeBindingAdapter()
    {
        this.allPorts = resolveAllPorts();
        this.httpPorts = resolvePortsForScheme("http");
        this.httpsPorts = resolvePortsForScheme("https");
        this.isPlainEnabled = httpPorts != null;
        this.isTlsEnabled = httpsPorts != null;
        this.securitySchemes = resolveSecuritySchemes();
        this.isJwtEnabled = !securitySchemes.isEmpty();
    }

    @Override
    public BindingConfig adapt(
        BindingConfig binding)
    {
        switch (binding.kind)
        {
        case SERVER:
            return resolveServerBinding(binding);
        default:
            return binding;
        }
    }

    private BindingConfig resolveServerBinding(
        BindingConfig binding)
    {
        OpenapiOptionsConfig options = (OpenapiOptionsConfig) binding.options;
        OpenapiConfig openapiConfig = options.openapis.get(0);
        this.openApi = openapiConfig.openapi;

        return BindingConfig.builder(binding)
                .composite()
                    .name(String.format(binding.qname, "$composite"))
                    .binding()
                        .name("tcp_server0")
                        .type("tcp")
                        .kind(SERVER)
                        .options(TcpOptionsConfig::builder)
                            .host("0.0.0.0")
                            .ports(allPorts)
                            .build()
                        .inject(this::injectPlainTcpRoute)
                        .inject(this::injectTlsTcpRoute)
                        .build()
                    .inject(this::injectTlsServer)
                    .binding()
                        .name("http_server0")
                        .type("http")
                        .kind(SERVER)
                        .options(HttpOptionsConfig::builder)
                            .access()
                                .policy(CROSS_ORIGIN)
                                .build()
                            .inject(this::injectHttpServerOptions)
                            .inject(this::injectHttpServerRequests)
                            .build()
                        .inject(this::injectHttpServerRoutes)
                        .build()
                    .build()
                .build();
    }

    private int[] resolveAllPorts()
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
        String scheme)
    {
        requireNonNull(scheme);
        int[] ports = null;
        URI url = findFirstServerUrlWithScheme(scheme);
        if (url != null)
        {
            ports = new int[] {url.getPort()};
        }
        return ports;
    }

    private URI findFirstServerUrlWithScheme(
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

    private Map<String, String> resolveSecuritySchemes()
    {
        requireNonNull(openApi);
        Map<String, String> result = new HashMap<>();
        if (openApi.components != null && openApi.components.securitySchemes != null)
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
        BindingConfigBuilder<C> binding)
    {
        if (isPlainEnabled)
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
        BindingConfigBuilder<C> binding)
    {
        if (isTlsEnabled)
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
        NamespaceConfigBuilder<C> namespace)
    {
        if (isTlsEnabled)
        {
            namespace
                .binding()
                    .name("tls_server0")
                    .type("tls")
                    .kind(SERVER)
                    .options(TlsOptionsConfig::builder)
                        .keys(List.of("")) // env
                        .sni(List.of("")) // env
                        .alpn(List.of("")) // env
                        .build()
                    .vault("server")
                    .exit("http_server0")
                    .build();
        }
        return namespace;
    }

    private <C> HttpOptionsConfigBuilder<C> injectHttpServerOptions(
        HttpOptionsConfigBuilder<C> options)
    {
        if (isJwtEnabled)
        {
            options
                .authorization()
                    .name("jwt0")
                    .credentials()
                        .header()
                            .name("authorization")
                            .pattern("Bearer {credentials}")
                            .build()
                    .build()
                .build();
        }
        return options;
    }

    private <C> HttpOptionsConfigBuilder<C> injectHttpServerRequests(
        HttpOptionsConfigBuilder<C> options)
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
                            .inject(request -> injectContent(request, operation))
                            .inject(request -> injectParams(request, operation))
                            .build();
                }
            }
        }
        return options;
    }

    private <C> HttpRequestConfigBuilder<C> injectContent(
        HttpRequestConfigBuilder<C> request,
        Operation operation)
    {
        if (operation.requestBody != null && operation.requestBody.content != null && !operation.requestBody.content.isEmpty())
        {
            SchemaView schema = resolveSchemaForJsonContentType(operation.requestBody.content);
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

    private <C> BindingConfigBuilder<C> injectHttpClientOptions(
        BindingConfigBuilder<C> binding)
    {
        OperationsView operations = OperationsView.of(openApi.paths);
        if (operations.hasResponses())
        {
            binding.
                options(HttpOptionsConfig::builder)
                    .inject(options -> injectHttpClientRequests(operations, options))
                    .build();
        }
        return binding;
    }

    private <C> HttpOptionsConfigBuilder<C> injectHttpClientRequests(
        OperationsView operations,
        HttpOptionsConfigBuilder<C> options)
    {
        for (String pathName : openApi.paths.keySet())
        {
            PathView path = PathView.of(openApi.paths.get(pathName));
            for (String methodName : path.methods().keySet())
            {
                OperationView operation = operations.operation(pathName, methodName);
                if (operation.hasResponses())
                {
                    options
                        .request()
                            .path(pathName)
                            .method(HttpRequestConfig.Method.valueOf(methodName))
                            .inject(request -> injectResponses(request, operation))
                            .build()
                        .build();
                }
            }
        }
        return options;
    }

    private <C> HttpRequestConfigBuilder<C> injectResponses(
        HttpRequestConfigBuilder<C> request,
        OperationView operation)
    {
        if (operation != null && operation.responsesByStatus() != null)
        {
            for (Map.Entry<String, ResponseByContentType> responses0 : operation.responsesByStatus().entrySet())
            {
                String status = responses0.getKey();
                ResponseByContentType responses1 = responses0.getValue();
                if (!(OperationView.DEFAULT.equals(status)) && responses1.content != null)
                {
                    for (Map.Entry<String, Response> response2 : responses1.content.entrySet())
                    {
                        SchemaView schema = SchemaView.of(openApi.components.schemas, response2.getValue().schema);
                        request
                            .response()
                                .status(Integer.parseInt(status))
                                .contentType(response2.getKey())
                                .inject(response -> injectResponseHeaders(responses1, response))
                                .content(JsonModelConfig::builder)
                                    .catalog()
                                    .name(INLINE_CATALOG_NAME)
                                    .schema()
                                        .subject(schema.refKey())
                                        .build()
                                    .build()
                                .build()
                            .build();
                    }
                }
            }
        }
        return request;
    }

    private <C> HttpResponseConfigBuilder<C> injectResponseHeaders(
        ResponseByContentType responses,
        HttpResponseConfigBuilder<C> response)
    {
        if (responses.headers != null && !responses.headers.isEmpty())
        {
            for (Map.Entry<String, Header> header : responses.headers.entrySet())
            {
                String name = header.getKey();
                ModelConfig model = models.get(header.getValue().schema.type);
                if (model != null)
                {
                    response
                        .header()
                            .name(name)
                            .model(model)
                            .build();
                }
            }
        }
        return response;
    }

    private <C> BindingConfigBuilder<C> injectHttpServerRoutes(
        BindingConfigBuilder<C> binding)
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
                        .inject(route -> injectHttpServerRouteGuarded(route, path, method))
                        .build();
            }
        }
        return binding;
    }

    private <C> RouteConfigBuilder<C> injectHttpServerRouteGuarded(
        RouteConfigBuilder<C> route,
        PathView path,
        String method)
    {
        List<Map<String, List<String>>> security = path.methods().get(method).security;
        if (security != null)
        {
            for (Map<String, List<String>> securityItem : security)
            {
                for (String securityItemLabel : securityItem.keySet())
                {
                    if (isJwtEnabled && "jwt".equals(securitySchemes.get(securityItemLabel)))
                    {
                        route
                            .guarded()
                                .name("jwt0")
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

    private <C> NamespaceConfigBuilder<C> injectTlsClient(
        NamespaceConfigBuilder<C> namespace)
    {
        if (isTlsEnabled)
        {
            namespace
                .binding()
                    .name("tls_client0")
                    .type("tls")
                    .kind(CLIENT)
                    .options(TlsOptionsConfig::builder)
                        .trust(List.of("")) // env
                        .sni(List.of("")) // env
                        .alpn(List.of("")) // env
                        .trustcacerts(true)
                        .build()
                    .vault("client")
                    .exit("tcp_client0")
                    .build();
        }
        return namespace;
    }

    private <C> NamespaceConfigBuilder<C> injectGuard(
        NamespaceConfigBuilder<C> namespace)
    {
        if (isJwtEnabled)
        {
            namespace
                .guard()
                    .name("jwt0")
                    .type("jwt")
                    .options(JwtOptionsConfig::builder)
                        .issuer("") // env
                        .audience("") // env
                        .key()
                            .alg("").kty("").kid("").use("").n("").e("").crv("").x("").y("") // env
                            .build()
                        .build()
                    .build();
        }
        return namespace;
    }

    private <C> NamespaceConfigBuilder<C> injectVaults(
        NamespaceConfigBuilder<C> namespace)
    {
        if (isTlsEnabled)
        {
            namespace
                .vault()
                    .name("client")
                    .type("filesystem")
                    .options(FileSystemOptionsConfig::builder)
                        .trust()
                            .store("") // env
                            .type("") // env
                            .password("") // env
                            .build()
                        .build()
                    .build()
                .vault()
                    .name("server")
                    .type("filesystem")
                    .options(FileSystemOptionsConfig::builder)
                        .keys()
                            .store("") // env
                            .type("") // env
                            .password("") //env
                            .build()
                        .build()
                    .build();
        }
        return namespace;
    }

    private JsonPatch createEnvVarsPatch()
    {
        JsonPatchBuilder patch = Json.createPatchBuilder();
        patch.replace("/bindings/tcp_client0/options/host", "${{env.TCP_CLIENT_HOST}}");
        patch.replace("/bindings/tcp_client0/options/port", "${{env.TCP_CLIENT_PORT}}");

        if (isJwtEnabled)
        {
            // jwt0 guard
            patch.replace("/guards/jwt0/options/issuer", "${{env.JWT_ISSUER}}");
            patch.replace("/guards/jwt0/options/audience", "${{env.JWT_AUDIENCE}}");
            patch.replace("/guards/jwt0/options/keys/0/alg", "${{env.JWT_ALG}}");
            patch.replace("/guards/jwt0/options/keys/0/kty", "${{env.JWT_KTY}}");
            patch.replace("/guards/jwt0/options/keys/0/kid", "${{env.JWT_KID}}");
            patch.replace("/guards/jwt0/options/keys/0/use", "${{env.JWT_USE}}");
            patch.replace("/guards/jwt0/options/keys/0/n", "${{env.JWT_N}}");
            patch.replace("/guards/jwt0/options/keys/0/e", "${{env.JWT_E}}");
            patch.replace("/guards/jwt0/options/keys/0/crv", "${{env.JWT_CRV}}");
            patch.replace("/guards/jwt0/options/keys/0/x", "${{env.JWT_X}}");
            patch.replace("/guards/jwt0/options/keys/0/y", "${{env.JWT_Y}}");
        }

        if (isTlsEnabled)
        {
            // tls_server0 binding
            patch.replace("/bindings/tls_server0/options/keys/0", "${{env.TLS_SERVER_KEY}}");
            patch.replace("/bindings/tls_server0/options/sni/0", "${{env.TLS_SERVER_SNI}}");
            patch.replace("/bindings/tls_server0/options/alpn/0", "${{env.TLS_SERVER_ALPN}}");
            // tls_client0 binding
            patch.replace("/bindings/tls_client0/options/trust/0", "${{env.TLS_CLIENT_TRUST}}");
            patch.replace("/bindings/tls_client0/options/sni/0", "${{env.TLS_CLIENT_SNI}}");
            patch.replace("/bindings/tls_client0/options/alpn/0", "${{env.TLS_CLIENT_ALPN}}");
            // client vault
            patch.replace("/vaults/client/options/trust/store", "${{env.TRUSTSTORE_PATH}}");
            patch.replace("/vaults/client/options/trust/type", "${{env.TRUSTSTORE_TYPE}}");
            patch.replace("/vaults/client/options/trust/password", "${{env.TRUSTSTORE_PASSWORD}}");
            // server vault
            patch.replace("/vaults/server/options/keys/store", "${{env.KEYSTORE_PATH}}");
            patch.replace("/vaults/server/options/keys/type", "${{env.KEYSTORE_TYPE}}");
            patch.replace("/vaults/server/options/keys/password", "${{env.KEYSTORE_PASSWORD}}");
        }

        return patch.build();
    }

    private List<String> unquotedEnvVars()
    {
        return List.of("TCP_CLIENT_PORT");
    }

    private SchemaView resolveSchemaForJsonContentType(
        Map<String, MediaType> content)
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
        NamespaceConfigBuilder<C> namespace)
    {
        if (openApi.components != null && openApi.components.schemas != null && !openApi.components.schemas.isEmpty())
        {
            namespace
                .catalog()
                    .name(INLINE_CATALOG_NAME)
                    .type(INLINE_CATALOG_TYPE)
                    .options(InlineOptionsConfig::builder)
                        .subjects()
                            .inject(this::injectSubjects)
                            .build()
                        .build()
                    .build();
        }
        return namespace;
    }

    private <C> InlineSchemaConfigBuilder<C> injectSubjects(
        InlineSchemaConfigBuilder<C> subjects)
    {
        try (Jsonb jsonb = JsonbBuilder.create())
        {
            YAMLMapper yaml = YAMLMapper.builder()
                .disable(WRITE_DOC_START_MARKER)
                .enable(MINIMIZE_QUOTES)
                .build();
            for (Map.Entry<String, Schema> entry : openApi.components.schemas.entrySet())
            {
                SchemaView schema = SchemaView.of(openApi.components.schemas, entry.getValue());
                subjects
                    .subject(entry.getKey())
                        .version(VERSION_LATEST)
                        .schema(writeSchemaYaml(jsonb, yaml, schema))
                        .build();
            }
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return subjects;
    }

    private static String writeSchemaYaml(
        Jsonb jsonb,
        YAMLMapper yaml,
        Object schema)
    {
        String result = null;
        try
        {
            String schemaJson = jsonb.toJson(schema);
            JsonNode json = new ObjectMapper().readTree(schemaJson);
            result = yaml.writeValueAsString(json);
        }
        catch (JsonProcessingException ex)
        {
            rethrowUnchecked(ex);
        }
        return result;
    }

}
