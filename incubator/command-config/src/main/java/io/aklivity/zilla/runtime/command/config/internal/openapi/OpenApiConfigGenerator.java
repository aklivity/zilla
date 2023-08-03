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
import io.aklivity.zilla.runtime.command.config.internal.openapi.model.OpenApi;
import io.aklivity.zilla.runtime.command.config.internal.openapi.model.PathItem;
import io.aklivity.zilla.runtime.command.config.internal.openapi.model.Server;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.ConfigWriter;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.config.VaultConfig;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtKeyConfig;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfig;
import io.aklivity.zilla.runtime.guard.jwt.internal.JwtGuard;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemOptionsConfig;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemStoreConfig;

public class OpenApiConfigGenerator
{
    private final OpenApi openApi;

    public OpenApiConfigGenerator(
        Path input)
    {
        this.openApi = parseOpenApi(input);
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
        TlsOptionsConfig tlsServer0Options = new TlsOptionsConfig(null, List.of("localhost"), null, List.of("localhost"),
            List.of("h2"), null, null, false);
        BindingConfig tlsServer0 = new BindingConfig("server", "tls_server0", "tls", SERVER, null,
            tlsServer0Options, List.of(new RouteConfig("http_server0")), null);

        // - http_server0
        HttpAccessControlConfig accessControl = new HttpAccessControlConfig(CROSS_ORIGIN);
        HttpPatternConfig headers = new HttpPatternConfig("authorization", "Bearer {credentials}");
        HttpCredentialsConfig credentials = new HttpCredentialsConfig(List.of(headers), null, null);
        HttpAuthorizationConfig authorization = new HttpAuthorizationConfig("jwt0", credentials);
        HttpOptionsConfig httpServer0Options = new HttpOptionsConfig(null, null, accessControl, authorization);
        List<RouteConfig> httpServer0routes = generateRoutes("http_client0");
        BindingConfig httpServer0 = new BindingConfig(null, "httpServer0", "http", SERVER, null,
            httpServer0Options, httpServer0routes, null);

        // - http_client0
        BindingConfig httpClient0 = new BindingConfig(null, "http_client0", "http", CLIENT, null,
            null, List.of(new RouteConfig("tls_client0")), null);

        // - tls_client0
        TlsOptionsConfig tlsClient0Options = new TlsOptionsConfig(null, null, List.of("nginx"), List.of("nginx"),
            List.of("h2"), null, null, true);
        BindingConfig tlsClient0 = new BindingConfig("client", "tls_client0", "tls", CLIENT, null,
            tlsClient0Options, List.of(new RouteConfig("tcp_client0")), null);

        // - tcp_client0
        TcpOptionsConfig tcpClient0Options = new TcpOptionsConfig("nginx", new int[]{443}, 0, true, false);
        BindingConfig tcpClient0 = new BindingConfig(null, "tcp_client0", "tcp", CLIENT, null,
            tcpClient0Options, List.of(), null);

        List<BindingConfig> bindings = List.of(tcpServer0, tcpServer1, tlsServer0, httpServer0, httpClient0, tlsClient0,
            tcpClient0);

        // guards
        String guardType = openApi.components.securitySchemes.bearerAuth.bearerFormat;
        OptionsConfig guardOptions = null;
        if (JwtGuard.NAME.equals(guardType))
        {
            String n = "qqEu50hX+43Bx4W1UYWnAVKwFm+vDbP0kuIOSLVNa+HKQdHTf+3Sei5UCnkskn796izA29D0DdCy3ET9oaKRHIJyKbqFl0rv6f516Q" +
                "zOoXKC6N01sXBHBE/ovs0wwDvlaW+gFGPgkzdcfUlyrWLDnLV7LcuQymhTND2uH0oR3wJnNENN/OFgM1KGPPDOe19YsIKdLqARgxrhZVsh06O" +
                "urEviZTXOBFI5r+yac7haDwOQhLHXNv+Y9MNvxs5QLWPFIM3bNUWfYrJnLrs4hGJS+y/KDM9Si+HL30QAFXy4YNO33J8DHjZ7ddG5n8/FqplO" +
                "KvRtUgjcKWlxoGY4VdVaDQ==";
            JwtKeyConfig key = new JwtKeyConfig("RSA", "example", null, n, "AQAB", "RS256", null, null, null);
            guardOptions = new JwtOptionsConfig("https://auth.example.com", "https://api.example.com", List.of(key), null);
        }
        GuardConfig guard = new GuardConfig("jwt0", guardType, guardOptions);
        List<GuardConfig> guards = List.of(guard);

        // vaults
        FileSystemStoreConfig trust = new FileSystemStoreConfig("tls/truststore.p12", "pkcs12", "${{env.KEYSTORE_PASSWORD}}");
        FileSystemOptionsConfig options = new FileSystemOptionsConfig(null, trust, null);
        VaultConfig clientVault = new VaultConfig("client", "filesystem", options);
        VaultConfig serverVault = new VaultConfig("server", "filesystem", options);
        List<VaultConfig> vaults = List.of(clientVault, serverVault);

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
        String exit)
    {
        List<RouteConfig> routes = new LinkedList<>();
        List<ConditionConfig> simpleRouteConditions = new LinkedList<>();
        for (String path: openApi.paths.keySet())
        {
            PathItem item = openApi.paths.get(path);
            item.initMethods();
            for (String method: item.methods().keySet())
            {
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put(":path", path);
                headers.put(":method", method);
                // TODO: Ati - get queries
                List<String> queries = List.of();
                if (!queries.isEmpty())
                {
                    // TODO: Ati - add queries to header
                    headers.put(":query", "TODO");
                }
                ConditionConfig when = new HttpConditionConfig(headers);
                // TODO: Ati - guarded
                List<GuardedConfig> guarded = List.of();
                if (queries.isEmpty() && guarded.isEmpty())
                {
                    simpleRouteConditions.add(when);
                }
                else
                {
                    RouteConfig route = new RouteConfig(exit, List.of(when), guarded);
                    routes.add(route);
                }
            }
        }
        if (!simpleRouteConditions.isEmpty())
        {
            RouteConfig route = new RouteConfig(exit, simpleRouteConditions, List.of());
            routes.add(route);
        }
        return routes;
    }

    private String writeConfig(
        NamespaceConfig namespace)
    {
        ConfigAdapterContext context = location -> "hello"; // TODO: Ati - ?
        ConfigWriter configWriter = new ConfigWriter(null);
        return configWriter.write(namespace);
    }
}
