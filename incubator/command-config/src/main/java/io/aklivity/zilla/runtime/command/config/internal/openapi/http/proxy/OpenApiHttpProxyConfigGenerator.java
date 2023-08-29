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
package io.aklivity.zilla.runtime.command.config.internal.openapi.http.proxy;

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
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonPatch;
import jakarta.json.JsonPatchBuilder;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpConditionConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.command.config.internal.airline.ConfigGenerator;
import io.aklivity.zilla.runtime.command.config.internal.openapi.model.OpenApi;
import io.aklivity.zilla.runtime.command.config.internal.openapi.model.Server;
import io.aklivity.zilla.runtime.command.config.internal.openapi.model2.PathItem2;
import io.aklivity.zilla.runtime.command.config.internal.openapi.model2.Server2;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ConfigWriter;
import io.aklivity.zilla.runtime.engine.config.GuardedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfig;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemOptionsConfig;

public class OpenApiHttpProxyConfigGenerator implements ConfigGenerator
{
    private final InputStream inputStream;

    private OpenApi openApi;
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
        return unquoteEnvVars(yaml);
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
            Server2 server2 = Server2.of(openApi.servers.get(i));
            URI url = server2.url();
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
        for (Server server : openApi.servers)
        {
            Server2 server2 = Server2.of(server);
            if (scheme.equals(server2.url().getScheme()))
            {
                result = server2.url();
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

    private HttpOptionsConfigBuilder<BindingConfigBuilder<NamespaceConfigBuilder<NamespaceConfig>>> injectHttpServerOptions(
        HttpOptionsConfigBuilder<BindingConfigBuilder<NamespaceConfigBuilder<NamespaceConfig>>> options)
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

    private BindingConfigBuilder<NamespaceConfigBuilder<NamespaceConfig>> injectHttpServerRoutes(
        BindingConfigBuilder<NamespaceConfigBuilder<NamespaceConfig>> binding)
    {
        for (String path : openApi.paths.keySet())
        {
            PathItem2 item = PathItem2.of(openApi.paths.get(path));
            for (String method : item.methods().keySet())
            {
                binding
                    .route()
                        .exit("http_client0")
                        .when(HttpConditionConfig::builder)
                            .header(":path", path.replaceAll("\\{[^}]+\\}", "*"))
                            .header(":method", method)
                            .build()
                        .inject(route -> injectHttpServerRouteGuarded(route, item, method))
                        .build();
            }
        }
        return binding;
    }

    private RouteConfigBuilder<BindingConfigBuilder<NamespaceConfigBuilder<NamespaceConfig>>> injectHttpServerRouteGuarded(
        RouteConfigBuilder<BindingConfigBuilder<NamespaceConfigBuilder<NamespaceConfig>>> route,
        PathItem2 item,
        String method)
    {
        List<Map<String, List<String>>> security = item.methods().get(method).security;
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
            patch.replace("/bindings/tls_server0/options/keys/0", "${{env.TLS_SERVER_KEYS}}");
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

    private String unquoteEnvVars(
        String yaml)
    {
        List<String> unquotedEnvVars = List.of("TCP_CLIENT_PORT");
        for (String envVar : unquotedEnvVars)
        {
            yaml = yaml.replaceAll(
                Pattern.quote(String.format("\"${{env.%s}}\"", envVar)),
                String.format("\\${{env.%s}}", envVar)
            );
        }
        return yaml;
    }
}
