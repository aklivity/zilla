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
package io.aklivity.zilla.runtime.command.generate.internal.openapi.http.proxy;

import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.CROSS_ORIGIN;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static java.util.Objects.requireNonNull;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonPatch;
import jakarta.json.JsonPatchBuilder;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfigBuilder;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpConditionConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.command.generate.internal.openapi.OpenApiConfigGenerator;
import io.aklivity.zilla.runtime.command.generate.internal.openapi.model.OpenApi;
import io.aklivity.zilla.runtime.command.generate.internal.openapi.model.Operation;
import io.aklivity.zilla.runtime.command.generate.internal.openapi.model.Parameter;
import io.aklivity.zilla.runtime.command.generate.internal.openapi.model.Server;
import io.aklivity.zilla.runtime.command.generate.internal.openapi.view.PathView;
import io.aklivity.zilla.runtime.command.generate.internal.openapi.view.SchemaView;
import io.aklivity.zilla.runtime.command.generate.internal.openapi.view.ServerView;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ConfigWriter;
import io.aklivity.zilla.runtime.engine.config.GuardedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfig;
import io.aklivity.zilla.runtime.validator.json.config.JsonValidatorConfig;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemOptionsConfig;

public class OpenApiHttpProxyConfigGenerator extends OpenApiConfigGenerator
{
    private final InputStream inputStream;

    private int[] allPorts;
    private int[] httpPorts;
    private int[] httpsPorts;
    private boolean isPlainEnabled;
    private boolean isTlsEnabled;
    private Map<String, String> securitySchemes;
    private boolean isJwtEnabled;

    public OpenApiHttpProxyConfigGenerator(
        InputStream inputStream)
    {
        this.inputStream = inputStream;
    }

    @Override
    public String generate()
    {
        this.openApi = parseOpenApi(inputStream);
        this.allPorts = resolveAllPorts();
        this.httpPorts = resolvePortsForScheme("http");
        this.httpsPorts = resolvePortsForScheme("https");
        this.isPlainEnabled = httpPorts != null;
        this.isTlsEnabled = httpsPorts != null;
        this.securitySchemes = resolveSecuritySchemes();
        this.isJwtEnabled = !securitySchemes.isEmpty();
        ConfigWriter configWriter = new ConfigWriter(null);
        String yaml = configWriter.write(createNamespace(), createEnvVarsPatch());
        return unquoteEnvVars(yaml, unquotedEnvVars());
    }

    private OpenApi parseOpenApi(
        InputStream inputStream)
    {
        OpenApi openApi = null;
        try (Jsonb jsonb = JsonbBuilder.create())
        {
            openApi = jsonb.fromJson(inputStream, OpenApi.class);
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return openApi;
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

    private NamespaceConfig createNamespace()
    {
        return NamespaceConfig.builder()
            .name("example")
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
            .binding()
                .name("http_client0")
                .type("http")
                .kind(CLIENT)
                .exit(isTlsEnabled ? "tls_client0" : "tcp_client0")
                .build()
            .inject(this::injectTlsClient)
            .binding()
                .name("tcp_client0")
                .type("tcp")
                .kind(CLIENT)
                .options(TcpOptionsConfig::builder)
                    .host("") // env
                    .ports(new int[]{0}) // env
                    .build()
                .build()
            .inject(this::injectGuard)
            .inject(this::injectVaults)
            .inject(this::injectCatalog)
            .build();
    }

    private BindingConfigBuilder<NamespaceConfigBuilder<NamespaceConfig>> injectPlainTcpRoute(
        BindingConfigBuilder<NamespaceConfigBuilder<NamespaceConfig>> binding)
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

    private BindingConfigBuilder<NamespaceConfigBuilder<NamespaceConfig>> injectTlsTcpRoute(
        BindingConfigBuilder<NamespaceConfigBuilder<NamespaceConfig>> binding)
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

    private NamespaceConfigBuilder<NamespaceConfig> injectTlsServer(
        NamespaceConfigBuilder<NamespaceConfig> namespace)
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
                    content(JsonValidatorConfig::builder)
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
                    ValidatorConfig validator = validators.get(parameter.schema.type);
                    if (validator != null)
                    {
                        switch (parameter.in)
                        {
                        case "path":
                            request.
                                pathParam()
                                    .name(parameter.name)
                                    .validator(validator)
                                    .build();
                            break;
                        case "query":
                            request.
                                queryParam()
                                    .name(parameter.name)
                                    .validator(validator)
                                    .build();
                            break;
                        case "header":
                            request.
                                header()
                                    .name(parameter.name)
                                    .validator(validator)
                                    .build();
                            break;
                        }
                    }
                }
            }
        }
        return request;
    }

    private BindingConfigBuilder<NamespaceConfigBuilder<NamespaceConfig>> injectHttpServerRoutes(
        BindingConfigBuilder<NamespaceConfigBuilder<NamespaceConfig>> binding)
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

    private RouteConfigBuilder<BindingConfigBuilder<NamespaceConfigBuilder<NamespaceConfig>>> injectHttpServerRouteGuarded(
        RouteConfigBuilder<BindingConfigBuilder<NamespaceConfigBuilder<NamespaceConfig>>> route,
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

    private NamespaceConfigBuilder<NamespaceConfig> injectTlsClient(
        NamespaceConfigBuilder<NamespaceConfig> namespace)
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

    private NamespaceConfigBuilder<NamespaceConfig> injectGuard(
        NamespaceConfigBuilder<NamespaceConfig> namespace)
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

    private NamespaceConfigBuilder<NamespaceConfig> injectVaults(
        NamespaceConfigBuilder<NamespaceConfig> namespace)
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
}
