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
package io.aklivity.zilla.runtime.binding.asyncapi.internal;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;

import java.util.Map;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiChannel;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpConditionConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class AsyncapiServerCompositeBindingAdapter extends AsyncapiCompositeBindingAdapter implements CompositeBindingAdapterSpi
{
    private int[] mqttPorts;
    private int[] mqttsPorts;
    private boolean isPlainEnabled;

    @Override
    public String type()
    {
        return AsyncapiBinding.NAME;
    }

    @Override
    public BindingConfig adapt(
        BindingConfig binding)
    {
        AsyncapiOptionsConfig options = (AsyncapiOptionsConfig) binding.options;
        AsyncapiConfig asyncapiConfig = options.specs.get(0);
        this.asyncApi = asyncapiConfig.asyncApi;

        int[] allPorts = resolveAllPorts();
        this.mqttPorts = resolvePortsForScheme("mqtt");
        this.mqttsPorts = resolvePortsForScheme("mqtts");
        this.isPlainEnabled = mqttPorts != null;
        this.isTlsEnabled = mqttsPorts != null;
        this.qname = binding.qname;
        this.qvault = String.format("%s:%s", binding.namespace, binding.vault);

        return BindingConfig.builder(binding)
            .composite()
                .name(String.format("%s/mqtt", qname))
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
                .inject(n -> injectTlsServer(n, options))
                .binding()
                    .name("mqtt_server0")
                    .type("mqtt")
                    .kind(SERVER)
                    .inject(this::injectMqttServerOptions)
                    .inject(this::injectMqttServerRoutes)
                    .build()
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
        NamespaceConfigBuilder<C> namespace,
        AsyncapiOptionsConfig options)
    {
        if (isTlsEnabled)
        {
            namespace
                .binding()
                    .name("tls_server0")
                    .type("tls")
                    .kind(SERVER)
                    .options(TlsOptionsConfig::builder)
                        .keys(options.tls.keys)
                        .sni(options.tls.sni)
                        .alpn(options.tls.alpn)
                        .build()
                    .vault(qvault)
                    .exit("mqtt_server0")
                    .build();
        }
        return namespace;
    }

    private <C> BindingConfigBuilder<C> injectMqttServerOptions(
        BindingConfigBuilder<C> binding)
    {
        for (Map.Entry<String, AsyncapiChannel> channelEntry : asyncApi.channels.entrySet())
        {
            String topic = channelEntry.getValue().address.replaceAll("\\{[^}]+\\}", "#");
            Map<String, AsyncapiMessage> messages = channelEntry.getValue().messages;
            if (hasJsonContentType())
            {
                binding
                    .options(MqttOptionsConfig::builder)
                        .topic()
                            .name(topic)
                            .content(JsonModelConfig::builder)
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
        Map<String, AsyncapiMessage> messages,
        String contentType)
    {
        for (Map.Entry<String, AsyncapiMessage> messageEntry : messages.entrySet())
        {
            AsyncapiMessageView message = AsyncapiMessageView.of(asyncApi.components.messages, messageEntry.getValue());
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
        for (Map.Entry<String, AsyncapiChannel> entry : asyncApi.channels.entrySet())
        {
            String topic = entry.getValue().address.replaceAll("\\{[^}]+\\}", "#");
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
                    .exit(qname)
                .build();
        }
        return binding;
    }

    private boolean hasJsonContentType()
    {
        String contentType = null;
        if (asyncApi.components != null && asyncApi.components.messages != null &&
            !asyncApi.components.messages.isEmpty())
        {
            AsyncapiMessage firstAsyncapiMessage = asyncApi.components.messages.entrySet().stream()
                .findFirst().get().getValue();
            contentType = AsyncapiMessageView.of(asyncApi.components.messages, firstAsyncapiMessage).contentType();
        }
        return contentType != null && jsonContentType.reset(contentType).matches();
    }
}
