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
package io.aklivity.zilla.runtime.command.generate.internal.asyncapi.http.proxy;

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
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig.Method;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfigBuilder;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpConditionConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.AsyncApiConfigGenerator;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.AsyncApi;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Item;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Message;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Operation;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Parameter;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Server;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.view.ChannelView;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.view.MessageView;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.view.ServerView;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.EngineConfig;
import io.aklivity.zilla.runtime.engine.config.EngineConfigWriter;
import io.aklivity.zilla.runtime.engine.config.GuardedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfig;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemOptionsConfig;

public class AsyncApiHttpProxyConfigGenerator extends AsyncApiConfigGenerator
{
    private final InputStream input;

    private int[] allPorts;
    private int[] httpPorts;
    private int[] httpsPorts;
    private boolean isPlainEnabled;
    private boolean isTlsEnabled;
    private Map<String, String> securitySchemes;
    private String authorizationHeader;
    private boolean isJwtEnabled;

    public AsyncApiHttpProxyConfigGenerator(
        InputStream input)
    {
        this.input = input;
    }

    @Override
    public String generate()
    {
        this.asyncApi = parseAsyncApi(input);
        this.allPorts = resolveAllPorts();
        this.httpPorts = resolvePortsForScheme("http");
        this.httpsPorts = resolvePortsForScheme("https");
        this.isPlainEnabled = httpPorts != null;
        this.isTlsEnabled = httpsPorts != null;
        this.securitySchemes = resolveSecuritySchemes();
        this.authorizationHeader = resolveAuthorizationHeader();
        this.isJwtEnabled = !securitySchemes.isEmpty();
        EngineConfigWriter configWriter = new EngineConfigWriter(null);
        String yaml = configWriter.write(createConfig(), createEnvVarsPatch());
        return unquoteEnvVars(yaml, unquotedEnvVars());
    }

    private AsyncApi parseAsyncApi(
        InputStream inputStream)
    {
        AsyncApi asyncApi = null;
        try (Jsonb jsonb = JsonbBuilder.create())
        {
            asyncApi = jsonb.fromJson(inputStream, AsyncApi.class);
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return asyncApi;
    }

    private int[] resolveAllPorts()
    {
        int[] ports = new int[asyncApi.servers.size()];
        String[] keys = asyncApi.servers.keySet().toArray(String[]::new);
        for (int i = 0; i < asyncApi.servers.size(); i++)
        {
            ServerView server = ServerView.of(asyncApi.servers.get(keys[i]));
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
        for (String key : asyncApi.servers.keySet())
        {
            ServerView server = ServerView.of(asyncApi.servers.get(key));
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
        requireNonNull(asyncApi);
        Map<String, String> result = new HashMap<>();
        if (asyncApi.components != null && asyncApi.components.securitySchemes != null)
        {
            for (String securitySchemeName : asyncApi.components.securitySchemes.keySet())
            {
                String guardType = asyncApi.components.securitySchemes.get(securitySchemeName).bearerFormat;
                if ("jwt".equals(guardType))
                {
                    result.put(securitySchemeName, guardType);
                }
            }
        }
        return result;
    }

    private String resolveAuthorizationHeader()
    {
        requireNonNull(asyncApi);
        requireNonNull(asyncApi.components);
        String result = null;
        if (asyncApi.components.messages != null)
        {
            for (Map.Entry<String, Message> entry : asyncApi.components.messages.entrySet())
            {
                Message message = entry.getValue();
                if (message.headers != null && message.headers.properties != null)
                {
                    Item authorization = message.headers.properties.get("authorization");
                    if (authorization != null)
                    {
                        result = authorization.description;
                        break;
                    }
                }
            }
        }
        return result;
    }

    private EngineConfig createConfig()
    {
        return EngineConfig.builder()
            .namespace()
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
                .build()
            .build();
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
                            .pattern(authorizationHeader)
                            .build()
                        .build()
                    .build();
        }
        return options;
    }

    private <C> HttpOptionsConfigBuilder<C> injectHttpServerRequests(
        HttpOptionsConfigBuilder<C> options)
    {
        for (String name : asyncApi.operations.keySet())
        {
            Operation operation = asyncApi.operations.get(name);
            ChannelView channel = ChannelView.of(asyncApi.channels, operation.channel);
            String path = channel.address();
            Method method = Method.valueOf(operation.bindings.get("http").method);
            if (channel.messages() != null && !channel.messages().isEmpty() ||
                channel.parameters() != null && !channel.parameters().isEmpty())
            {
                options
                    .request()
                        .path(path)
                        .method(method)
                        .inject(request -> injectContent(request, channel.messages()))
                        .inject(request -> injectPathParams(request, channel.parameters()))
                        .build();
            }
        }
        return options;
    }

    private <C> HttpRequestConfigBuilder<C> injectContent(
        HttpRequestConfigBuilder<C> request,
        Map<String, Message> messages)
    {
        if (messages != null)
        {
            if (hasJsonContentType())
            {
                request.
                    content(JsonModelConfig::builder)
                        .catalog()
                            .name(INLINE_CATALOG_NAME)
                            .inject(catalog -> injectSchemas(catalog, messages))
                            .build()
                        .build();
            }
        }
        return request;
    }

    private <C> CatalogedConfigBuilder<C> injectSchemas(
        CatalogedConfigBuilder<C> catalog,
        Map<String, Message> messages)
    {
        for (String name : messages.keySet())
        {
            MessageView message = MessageView.of(asyncApi.components.messages, messages.get(name));
            String subject = message.refKey() != null ? message.refKey() : name;
            catalog
                .schema()
                    .subject(subject)
                    .build()
                .build();
        }
        return catalog;
    }

    private <C> HttpRequestConfigBuilder<C> injectPathParams(
        HttpRequestConfigBuilder<C> request,
        Map<String, Parameter> parameters)
    {
        if (parameters != null)
        {
            for (String name : parameters.keySet())
            {
                Parameter parameter = parameters.get(name);
                if (parameter.schema != null && parameter.schema.type != null)
                {
                    ModelConfig model = models.get(parameter.schema.type);
                    if (model != null)
                    {
                        request
                            .pathParam()
                                .name(name)
                                .model(model)
                                .build();
                    }
                }
            }
        }
        return request;
    }


    private <C> BindingConfigBuilder<C> injectHttpServerRoutes(
        BindingConfigBuilder<C> binding)
    {
        for (Map.Entry<String, Server> entry : asyncApi.servers.entrySet())
        {
            ServerView server = ServerView.of(entry.getValue());
            for (String name : asyncApi.operations.keySet())
            {
                Operation operation = asyncApi.operations.get(name);
                ChannelView channel = ChannelView.of(asyncApi.channels, operation.channel);
                String path = channel.address().replaceAll("\\{[^}]+\\}", "*");
                String method = operation.bindings.get("http").method;
                binding
                    .route()
                        .exit("http_client0")
                        .when(HttpConditionConfig::builder)
                            .header(":scheme", server.scheme())
                            .header(":authority", server.authority())
                            .header(":path", path)
                            .header(":method", method)
                            .build()
                        .inject(route -> injectHttpServerRouteGuarded(route, server))
                    .build();
            }
        }
        return binding;
    }

    private <C> RouteConfigBuilder<C> injectHttpServerRouteGuarded(
        RouteConfigBuilder<C> route,
        ServerView server)
    {
        if (server.security() != null)
        {
            for (Map<String, List<String>> securityItem : server.security())
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
                        break;
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
}
