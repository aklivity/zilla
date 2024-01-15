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
package io.aklivity.zilla.runtime.command.generate.internal.asyncapi.mqtt.proxy;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static java.util.Objects.requireNonNull;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonPatch;
import jakarta.json.JsonPatchBuilder;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import io.aklivity.zilla.runtime.binding.mqtt.config.MqttConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpConditionConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.AsyncApiConfigGenerator;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.AsyncApi;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Channel;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Message;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.view.MessageView;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.view.ServerView;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.EngineConfig;
import io.aklivity.zilla.runtime.engine.config.EngineConfigWriter;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.validator.json.config.JsonValidatorConfig;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemOptionsConfig;

public class AsyncApiMqttProxyConfigGenerator extends AsyncApiConfigGenerator
{
    private final InputStream input;

    private int[] allPorts;
    private int[] mqttPorts;
    private int[] mqttsPorts;
    private boolean isPlainEnabled;
    private boolean isTlsEnabled;

    public AsyncApiMqttProxyConfigGenerator(
        InputStream input)
    {
        this.input = input;
    }

    @Override
    public String generate()
    {
        this.asyncApi = parseAsyncApi(input);
        this.allPorts = resolveAllPorts();
        this.mqttPorts = resolvePortsForScheme("mqtt");
        this.mqttsPorts = resolvePortsForScheme("mqtts");
        this.isPlainEnabled = mqttPorts != null;
        this.isTlsEnabled = mqttsPorts != null;
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
                    .name("mqtt_server0")
                    .type("mqtt")
                    .kind(SERVER)
                    .inject(this::injectMqttServerOptions)
                    .inject(this::injectMqttServerRoutes)
                    .build()
                .binding()
                    .name("mqtt_client0")
                    .type("mqtt")
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
                        .ports(mqttPorts)
                        .build()
                    .exit("mqtt_server0")
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
                        .ports(mqttsPorts)
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
                    .exit("mqtt_server0")
                    .build();
        }
        return namespace;
    }

    private <C> BindingConfigBuilder<C> injectMqttServerOptions(
        BindingConfigBuilder<C> binding)
    {
        for (Map.Entry<String, Channel> channelEntry : asyncApi.channels.entrySet())
        {
            String topic = channelEntry.getValue().address.replaceAll("\\{[^}]+\\}", "*");
            Map<String, Message> messages = channelEntry.getValue().messages;
            if (hasJsonContentType())
            {
                binding
                    .options(MqttOptionsConfig::builder)
                        .topic()
                            .name(topic)
                            .content(JsonValidatorConfig::builder)
                                .catalog()
                                    .name(INLINE_CATALOG_NAME)
                                    .inject(cataloged -> injectJsonSchemas(cataloged, messages, APPLICATION_JSON))
                                    .build()
                                .build()
                            .build()
                        .build()
                    .build();
            }
        }
        return binding;
    }

    private <C> CatalogedConfigBuilder<C> injectJsonSchemas(
        CatalogedConfigBuilder<C> cataloged,
        Map<String, Message> messages,
        String contentType)
    {
        for (Map.Entry<String, Message> messageEntry : messages.entrySet())
        {
            MessageView message = MessageView.of(asyncApi.components.messages, messageEntry.getValue());
            String schema = messageEntry.getKey();
            if (message.contentType().equals(contentType))
            {
                cataloged
                    .schema()
                        .subject(schema)
                        .build()
                    .build();
            }
            else
            {
                throw new RuntimeException("Invalid content type");
            }
        }
        return cataloged;
    }

    private <C> BindingConfigBuilder<C> injectMqttServerRoutes(
        BindingConfigBuilder<C> binding)
    {
        for (Map.Entry<String, Channel> entry : asyncApi.channels.entrySet())
        {
            String topic = entry.getValue().address.replaceAll("\\{[^}]+\\}", "*");
            binding
                .route()
                    .when(MqttConditionConfig::builder)
                        .publish()
                            .topic(topic)
                            .build()
                        .build()
                    .when(MqttConditionConfig::builder)
                        .subscribe()
                            .topic(topic)
                            .build()
                        .build()
                    .exit("mqtt_client0")
                .build();
        }
        return binding;
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
