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
package io.aklivity.zilla.runtime.command.config.internal.openapi;

import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.CROSS_ORIGIN;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static java.util.Objects.requireNonNull;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonPatch;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.spi.JsonProvider;

import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.command.config.internal.airline.ConfigGenerator;
import io.aklivity.zilla.runtime.command.config.internal.openapi.model.OpenApi;
import io.aklivity.zilla.runtime.command.config.internal.openapi.model.PathItem;
import io.aklivity.zilla.runtime.command.config.internal.openapi.model.Server;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigWriter;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.VaultConfig;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtKeyConfig;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfig;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemOptionsConfig;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemStoreConfig;

public class OpenApiHttpProxyConfigGenerator implements ConfigGenerator
{
    private static final JsonPatch ENV_VARS_PATCH = createEnvVarsPatch();

    private final OpenApi openApi;
    private final ConfigWriter configWriter;

    public OpenApiHttpProxyConfigGenerator(
        Path input)
    {
        this.openApi = parseOpenApi(input);
        this.configWriter = new ConfigWriter(null);
    }

    public String generateConfig()
    {
        return writeConfig(createNamespaceConfig());
    }

    private OpenApi parseOpenApi(
        Path input)
    {
        OpenApi openApi = null;
        try (InputStream inputStream = new FileInputStream(input.toFile()))
        {
            Jsonb jsonb = JsonbBuilder.create();
            openApi = jsonb.fromJson(inputStream, OpenApi.class);
            jsonb.close();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            rethrowUnchecked(ex);
        }
        return openApi;
    }

    private NamespaceConfig createNamespaceConfig()
    {
        // guards
        List<GuardConfig> guards = new ArrayList<>();
        Map<String, GuardedConfig> guardedRoutes = new HashMap<>();
        for (String securitySchemeName : openApi.components.securitySchemes.keySet())
        {
            String guardType = openApi.components.securitySchemes.get(securitySchemeName).bearerFormat;
            if ("jwt".equals(guardType))
            {
                JwtKeyConfig key = JwtKeyConfig.builder()
                    .alg("").kty("").kid("").use("").n("").e("").crv("").x("").y("") // env
                    .build();
                OptionsConfig guardOptions = JwtOptionsConfig.builder()
                    .issuer("") // env
                    .audience("") // env
                    .keys(List.of(key))
                    .build();
                GuardConfig guard = GuardConfig.builder().name("jwt0").type(guardType).options(guardOptions).build();
                guards.add(guard);
                GuardedConfig guarded = GuardedConfig.builder().name("jwt0").role("echo:stream").build();
                guardedRoutes.put(securitySchemeName, guarded);
            }
        }

        // vaults
        // - client
        FileSystemStoreConfig trust = FileSystemStoreConfig.builder()
            .store("") // env
            .type("") // env
            .password("") // env
            .build();
        FileSystemOptionsConfig clientOptions = FileSystemOptionsConfig.builder().trust(trust).build();
        VaultConfig clientVault = VaultConfig.builder().name("client").type("filesystem").options(clientOptions).build();
        // - server
        FileSystemStoreConfig keys = FileSystemStoreConfig.builder()
            .store("") // env
            .type("") // env
            .password("") //env
            .build();
        FileSystemOptionsConfig serverOptions = FileSystemOptionsConfig.builder().keys(keys).build();
        VaultConfig serverVault = VaultConfig.builder().name("server").type("filesystem").options(serverOptions).build();
        List<VaultConfig> vaults = List.of(clientVault, serverVault);

        // bindings
        // - tcp_server0
        TcpOptionsConfig tcpServer0Options = TcpOptionsConfig.builder()
            .host("0.0.0.0")
            .ports(resolvePortsForScheme("https"))
            .build();
        BindingConfig tcpServer0 = BindingConfig.builder()
            .name("tcp_server0")
            .type("tcp")
            .kind(SERVER)
            .options(tcpServer0Options)
            .route(RouteConfig.builder().exit("tls_server0").build())
            .build();

        // - tcp_server1
        TcpOptionsConfig tcpServer1Options = TcpOptionsConfig.builder()
            .host("0.0.0.0")
            .ports(resolvePortsForScheme("http"))
            .build();
        BindingConfig tcpServer1 = BindingConfig.builder()
            .name("tcp_server1")
            .type("tcp")
            .kind(SERVER)
            .options(tcpServer1Options)
            .route(RouteConfig.builder().exit("http_server0").build())
            .build();

        // - tls_server0
        TlsOptionsConfig tlsServer0Options = TlsOptionsConfig.builder()
            .keys(List.of("")) // env
            .sni(List.of("")) // env
            .alpn(List.of("")) // env
            .build();
        BindingConfig tlsServer0 = BindingConfig.builder()
            .name("tls_server0")
            .type("tls")
            .kind(SERVER)
            .options(tlsServer0Options)
            .vault("server")
            .route(RouteConfig.builder().exit("http_server0").build())
            .build();

        // - http_server0
        HttpOptionsConfig httpServer0Options = HttpOptionsConfig.builder()
            .access()
            .policy(CROSS_ORIGIN)
            .build()
            .authorization()
            .name("jwt0")
            .credentials()
            .header()
            .name("authorization")
            .pattern("Bearer {credentials}")
            .build()
            .build()
            .build()
            .build();
        List<RouteConfig> httpServer0routes = generateRoutes("http_client0", guardedRoutes);
        BindingConfigBuilder<BindingConfig> httpServer0builder = BindingConfig.builder()
            .name("httpServer0")
            .type("http")
            .kind(SERVER)
            .options(httpServer0Options);
        for (RouteConfig route : httpServer0routes)
        {
            httpServer0builder.route(route);
        }
        BindingConfig httpServer0 = httpServer0builder.build();

        // - http_client0
        BindingConfig httpClient0 = BindingConfig.builder()
            .name("http_client0")
            .type("http")
            .kind(CLIENT)
            .route(RouteConfig.builder().exit("tls_client0").build())
            .build();

        // - tls_client0
        TlsOptionsConfig tlsClient0Options = TlsOptionsConfig.builder()
            .trust(List.of("")) // env
            .sni(List.of("")) // env
            .alpn(List.of("")) // env
            .trustcacerts(true)
            .build();
        BindingConfig tlsClient0 = BindingConfig.builder()
            .name("tls_client0")
            .type("tls")
            .kind(CLIENT)
            .options(tlsClient0Options)
            .vault("client")
            .route(RouteConfig.builder().exit("tcp_client0").build())
            .build();

        // - tcp_client0
        TcpOptionsConfig tcpClient0Options = TcpOptionsConfig.builder()
            .host("") // env
            .ports(new int[]{0}) // env
            .build();
        BindingConfig tcpClient0 = BindingConfig.builder()
            .name("tcp_client0")
            .type("tcp")
            .kind(CLIENT)
            .options(tcpClient0Options)
            .build();

        List<BindingConfig> bindings = List.of(tcpServer0, tcpServer1, tlsServer0, httpServer0, httpClient0, tlsClient0,
            tcpClient0);

        // namespace
        return NamespaceConfig.builder()
            .name("example")
            .bindings(bindings)
            .guards(guards)
            .vaults(vaults)
            .build();
    }

    private int[] resolvePortsForScheme(
        String scheme)
    {
        requireNonNull(scheme);
        int[] httpPorts = null;
        URI httpServerUrl = findFirstServerUrlWithScheme(scheme);
        if (httpServerUrl != null)
        {
            httpPorts = new int[] {httpServerUrl.getPort()};
        }
        return httpPorts;
    }

    private URI findFirstServerUrlWithScheme(
        String scheme)
    {
        requireNonNull(scheme);
        URI result = null;
        for (Server server : openApi.servers)
        {
            if (scheme.equals(server.url().getScheme()))
            {
                result = server.url();
                break;
            }
        }
        return result;
    }

    private List<RouteConfig> generateRoutes(
        String exit,
        Map<String, GuardedConfig> guardedRoutes)
    {
        List<RouteConfig> routes = new LinkedList<>();
        for (String path : openApi.paths.keySet())
        {
            PathItem item = openApi.paths.get(path);
            item.initMethods();
            for (String method : item.methods().keySet())
            {
                ConditionConfig when = HttpConditionConfig.builder()
                    .header(":path", path.replaceAll("\\{[^}]+\\}", "*"))
                    .header(":method", method)
                    .build();
                RouteConfigBuilder<RouteConfig> routeBuilder = RouteConfig.builder()
                    .exit(exit)
                    .when(when);
                List<Map<String, List<String>>> security = item.methods().get(method).security;
                if (security != null)
                {
                    for (Map<String, List<String>> securityItem : security)
                    {
                        for (String securityItemLabel : securityItem.keySet())
                        {
                            if (guardedRoutes.containsKey(securityItemLabel))
                            {
                                routeBuilder.guarded(guardedRoutes.get(securityItemLabel));
                            }
                        }
                    }
                }
                routes.add(routeBuilder.build());
            }
        }
        return routes;
    }

    private String writeConfig(
        NamespaceConfig namespace)
    {
        return configWriter.write(namespace, ENV_VARS_PATCH);
    }

    private static JsonPatch createEnvVarsPatch()
    {
        Map<String, String> ops = new HashMap<>();
        // tls_server0 binding
        ops.put("/bindings/tls_server0/options/keys/0", "${{env.TLS_SERVER_KEYS}}");
        ops.put("/bindings/tls_server0/options/sni/0", "${{env.TLS_SERVER_SNI}}");
        ops.put("/bindings/tls_server0/options/alpn/0", "${{env.TLS_SERVER_ALPN}}");
        // tls_client0 binding
        ops.put("/bindings/tls_client0/options/trust/0", "${{env.TLS_CLIENT_TRUST}}");
        ops.put("/bindings/tls_client0/options/sni/0", "${{env.TLS_CLIENT_SNI}}");
        ops.put("/bindings/tls_client0/options/alpn/0", "${{env.TLS_CLIENT_ALPN}}");
        // tcp_client0 binding
        ops.put("/bindings/tcp_client0/options/host", "${{env.TCP_CLIENT_HOST}}");
        ops.put("/bindings/tcp_client0/options/port", "${{env.TCP_CLIENT_PORT}}");
        // jwt0 guard
        ops.put("/guards/jwt0/options/issuer", "${{env.JWT_ISSUER}}");
        ops.put("/guards/jwt0/options/audience", "${{env.JWT_AUDIENCE}}");
        ops.put("/guards/jwt0/options/keys/0/alg", "${{env.JWT_ALG}}");
        ops.put("/guards/jwt0/options/keys/0/kty", "${{env.JWT_KTY}}");
        ops.put("/guards/jwt0/options/keys/0/kid", "${{env.JWT_KID}}");
        ops.put("/guards/jwt0/options/keys/0/use", "${{env.JWT_USE}}");
        ops.put("/guards/jwt0/options/keys/0/n", "${{env.JWT_N}}");
        ops.put("/guards/jwt0/options/keys/0/e", "${{env.JWT_E}}");
        ops.put("/guards/jwt0/options/keys/0/crv", "${{env.JWT_CRV}}");
        ops.put("/guards/jwt0/options/keys/0/x", "${{env.JWT_X}}");
        ops.put("/guards/jwt0/options/keys/0/y", "${{env.JWT_Y}}");
        // client vault
        ops.put("/vaults/client/options/trust/store", "${{env.TRUSTSTORE_PATH}}");
        ops.put("/vaults/client/options/trust/type", "${{env.TRUSTSTORE_TYPE}}");
        ops.put("/vaults/client/options/trust/password", "${{env.TRUSTSTORE_PASSWORD}}");
        // server vault
        ops.put("/vaults/server/options/keys/store", "${{env.KEYSTORE_PATH}}");
        ops.put("/vaults/server/options/keys/type", "${{env.KEYSTORE_TYPE}}");
        ops.put("/vaults/server/options/keys/password", "${{env.KEYSTORE_PASSWORD}}");

        JsonArrayBuilder patch = Json.createArrayBuilder();
        for (Map.Entry<String, String> entry: ops.entrySet())
        {
            JsonObject op = Json.createObjectBuilder()
                .add("op", "replace")
                .add("path", entry.getKey())
                .add("value", entry.getValue())
                .build();
            patch.add(op);
        }
        return JsonProvider.provider().createPatch(patch.build());
    }

}
