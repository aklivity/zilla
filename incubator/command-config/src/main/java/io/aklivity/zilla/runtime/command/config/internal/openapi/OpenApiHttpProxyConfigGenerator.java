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

import static io.aklivity.zilla.runtime.binding.http.config.HttpAccessControlConfig.HttpPolicyConfig.CROSS_ORIGIN;
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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import io.aklivity.zilla.runtime.binding.http.config.HttpAccessControlConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfig.HttpCredentialsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfig.HttpPatternConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.command.config.internal.airline.ConfigGenerator;
import io.aklivity.zilla.runtime.command.config.internal.openapi.model.OpenApi;
import io.aklivity.zilla.runtime.command.config.internal.openapi.model.PathItem;
import io.aklivity.zilla.runtime.command.config.internal.openapi.model.Server;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigWriter;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.config.VaultConfig;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtKeyConfig;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfig;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemOptionsConfig;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemStoreConfig;

public class OpenApiHttpProxyConfigGenerator implements ConfigGenerator
{
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
        for (String securitySchemeName: openApi.components.securitySchemes.keySet())
        {
            String guardType = openApi.components.securitySchemes.get(securitySchemeName).bearerFormat;
            if ("jwt".equals(guardType))
            {
                JwtKeyConfig key = new JwtKeyConfig("${{env.JWT_KTY}}", "${{env.JWT_KID}}", "${{env.JWT_USE}}", "${{env.JWT_N}}",
                    "${{env.JWT_E}}", "${{env.JWT_ALG}}", "${{env.JWT_CRV}}", "${{env.JWT_X}}", "${{env.JWT_Y}}");
                OptionsConfig guardOptions = new JwtOptionsConfig("${{env.JWT_ISSUER}}", "${{env.JWT_AUDIENCE}}",
                    List.of(key), null);
                guards.add(new GuardConfig("jwt0", guardType, guardOptions));
                guardedRoutes.put(securitySchemeName, new GuardedConfig("jwt0", List.of("echo:stream")));
            }
        }

        // vaults
        // - client
        FileSystemStoreConfig trust = new FileSystemStoreConfig("${{env.TRUSTSTORE_PATH}}", "${{env.TRUSTSTORE_TYPE}}",
            "${{env.TRUSTSTORE_PASSWORD}}");
        FileSystemOptionsConfig clientOptions = new FileSystemOptionsConfig(null, trust, null);
        VaultConfig clientVault = new VaultConfig("client", "filesystem", clientOptions);
        // - server
        FileSystemStoreConfig keys = new FileSystemStoreConfig("${{env.KEYSTORE_PATH}}", "${{env.KEYSTORE_TYPE}}",
            "${{env.KEYSTORE_PASSWORD}}");
        FileSystemOptionsConfig serverOptions = new FileSystemOptionsConfig(keys, null, null);
        VaultConfig serverVault = new VaultConfig("server", "filesystem", serverOptions);
        List<VaultConfig> vaults = List.of(clientVault, serverVault);

        // bindings
        // - tcp_server0
        TcpOptionsConfig tcpServer0Options = new TcpOptionsConfig("0.0.0.0", resolvePortsForScheme("https"), 0,
            true, false);
        BindingConfig tcpServer0 = new BindingConfig(null, "tcp_server0", "tcp", SERVER, null, tcpServer0Options,
            List.of(new RouteConfig("tls_server0")), null);

        // - tcp_server1
        TcpOptionsConfig tcpServer1Options = new TcpOptionsConfig("0.0.0.0", resolvePortsForScheme("http"), 0,
            true, false);
        BindingConfig tcpServer1 = new BindingConfig(null, "tcp_server1", "tcp", SERVER, null, tcpServer1Options,
            List.of(new RouteConfig("http_server0")), null);

        // - tls_server0
        TlsOptionsConfig tlsServer0Options = new TlsOptionsConfig(null, List.of("${{env.TLS_SERVER_KEYS}}"), null,
            List.of("${{env.TLS_SERVER_SNI}}"), List.of("${{env.TLS_SERVER_ALPN}}"), null, null, false);
        BindingConfig tlsServer0 = new BindingConfig("server", "tls_server0", "tls", SERVER, null,
            tlsServer0Options, List.of(new RouteConfig("http_server0")), null);

        // - http_server0
        HttpAccessControlConfig accessControl = new HttpAccessControlConfig(CROSS_ORIGIN);
        HttpPatternConfig headers = new HttpPatternConfig("authorization", "Bearer {credentials}");
        HttpCredentialsConfig credentials = new HttpCredentialsConfig(List.of(headers), null, null);
        HttpAuthorizationConfig authorization = new HttpAuthorizationConfig("jwt0", credentials);
        HttpOptionsConfig httpServer0Options = new HttpOptionsConfig(null, null, accessControl, authorization);
        List<RouteConfig> httpServer0routes = generateRoutes("http_client0", guardedRoutes);
        BindingConfig httpServer0 = new BindingConfig(null, "httpServer0", "http", SERVER, null,
            httpServer0Options, httpServer0routes, null);

        // - http_client0
        BindingConfig httpClient0 = new BindingConfig(null, "http_client0", "http", CLIENT, null,
            null, List.of(new RouteConfig("tls_client0")), null);

        // - tls_client0
        TlsOptionsConfig tlsClient0Options = new TlsOptionsConfig(null, null, List.of("${{env.TLS_CLIENT_TRUST}}"),
            List.of("${{env.TLS_CLIENT_SNI}}"), List.of("${{env.TLS_CLIENT_ALPN}}"), null, null, true);
        BindingConfig tlsClient0 = new BindingConfig("client", "tls_client0", "tls", CLIENT, null,
            tlsClient0Options, List.of(new RouteConfig("tcp_client0")), null);

        // - tcp_client0
        TcpOptionsConfig tcpClient0Options = new TcpOptionsConfig("${{env.TCP_CLIENT_HOST}}", new int[]{443}, 0, true, false);
        BindingConfig tcpClient0 = new BindingConfig(null, "tcp_client0", "tcp", CLIENT, null,
            tcpClient0Options, List.of(), null);

        List<BindingConfig> bindings = List.of(tcpServer0, tcpServer1, tlsServer0, httpServer0, httpClient0, tlsClient0,
            tcpClient0);

        // namespace
        return new NamespaceConfig("example", List.of(), null, bindings, guards, vaults);
    }

    private int[] resolvePortsForScheme(
        String scheme)
    {
        requireNonNull(scheme);
        int[] httpPorts = null;
        URI httpServerUrl = findFirstServerUrlWithScheme(scheme);
        if (httpServerUrl != null)
        {
            httpPorts = new int[]{httpServerUrl.getPort()};
        }
        return httpPorts;
    }

    private URI findFirstServerUrlWithScheme(
        String scheme)
    {
        requireNonNull(scheme);
        URI result = null;
        for (Server server: openApi.servers)
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
        List<ConditionConfig> unguardedConditions = new LinkedList<>();
        for (String path: openApi.paths.keySet())
        {
            PathItem item = openApi.paths.get(path);
            item.initMethods();
            for (String method: item.methods().keySet())
            {
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put(":path", path);
                headers.put(":method", method);
                ConditionConfig when = new HttpConditionConfig(headers);
                List<GuardedConfig> guarded = new LinkedList<>();
                List<Map<String, List<String>>> security = item.methods().get(method).security;
                if (security != null)
                {
                    for (Map<String, List<String>> securityItem: security)
                    {
                        for (String securityItemLabel: securityItem.keySet())
                        {
                            if (guardedRoutes.containsKey(securityItemLabel))
                            {
                                guarded.add(guardedRoutes.get(securityItemLabel));
                            }
                        }
                    }
                }
                if (guarded.isEmpty())
                {
                    unguardedConditions.add(when);
                }
                else
                {
                    RouteConfig route = new RouteConfig(exit, List.of(when), guarded);
                    routes.add(route);
                }
            }
        }
        if (!unguardedConditions.isEmpty())
        {
            RouteConfig route = new RouteConfig(exit, unguardedConditions, List.of());
            routes.add(route);
        }
        return routes;
    }

    private String writeConfig(
        NamespaceConfig namespace)
    {
        return configWriter.write(namespace);
    }
}
