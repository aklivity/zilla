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


import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiOperation;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiChannelView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaServerConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;

import java.net.URI;
import java.util.stream.Collectors;

public class AyncapiKafkaProtocol extends AsyncapiProtocol
{
    private static final String SCHEME = "kafka";
    private static final String SECURE_SCHEME = "";
    private static final String SECURE_PROTOCOL = "kafka-secure";
    private final String protocol;
    private final KafkaSaslConfig sasl;

    public AyncapiKafkaProtocol(
        String qname,
        Asyncapi asyncApi,
        AsyncapiOptionsConfig options,
        String protocol)
    {
        super(qname, asyncApi, SCHEME, SECURE_SCHEME);
        this.protocol = protocol;
        this.sasl = options.kafka != null ? options.kafka.sasl : null;
    }

    @Override
    public <C> NamespaceConfigBuilder<C> injectProtocolClientCache(
        NamespaceConfigBuilder<C> namespace)
    {
        return namespace
                .binding()
                    .name("kafka_cache_client0")
                    .type("kafka")
                    .kind(KindConfig.CACHE_CLIENT)
                    .exit("kafka_cache_server0")
                .build()
                .binding()
                    .name("kafka_cache_server0")
                    .type("kafka")
                    .kind(KindConfig.CACHE_SERVER)
                    .exit("kafka_client0")
                .build();
    }

    @Override
    public <C> BindingConfigBuilder<C> injectProtocolClientOptions(
        BindingConfigBuilder<C> binding)
    {
        return sasl == null ? binding :
            binding.options(KafkaOptionsConfig::builder)
                .sasl(KafkaSaslConfig::builder)
                    .mechanism(sasl.mechanism)
                    .username(sasl.username)
                    .password(sasl.password)
                    .build()
                .inject(this::injectKafkaServerOptions)
                .inject(this::injectKafkaBootstrapOptions)
                .inject(this::injectKafkaTopicOptions)
                .build();
    }

    @Override
    public <C> BindingConfigBuilder<C> injectProtocolServerOptions(
        BindingConfigBuilder<C> binding)
    {
        return binding;
    }

    @Override
    public <C> BindingConfigBuilder<C> injectProtocolServerRoutes(
        BindingConfigBuilder<C> binding)
    {
        return binding;
    }

    @Override
    protected boolean isSecure()
    {
        return protocol.equals(SECURE_PROTOCOL);
    }

    private <C> KafkaOptionsConfigBuilder<C> injectKafkaServerOptions(
        KafkaOptionsConfigBuilder<C> options)
    {
        return options.servers(asyncApi.servers.values().stream().map(s ->
        {
            final URI serverUrl = AsyncapiServerView.of(s).url();
            return new KafkaServerConfig(serverUrl.getHost(), serverUrl.getPort());
        }).collect(Collectors.toList()));
    }

    private <C> KafkaOptionsConfigBuilder<C> injectKafkaTopicOptions(
        KafkaOptionsConfigBuilder<C> options)
    {
        for (String name : asyncApi.operations.keySet())
        {
            AsyncapiOperation operation = asyncApi.operations.get(name);
            AsyncapiChannelView channel = AsyncapiChannelView.of(asyncApi.channels, operation.channel);
            String topic = channel.address().replaceAll("\\{[^}]+\\}", "*");




            HttpRequestConfig.Method method = HttpRequestConfig.Method.valueOf(operation.bindings.get("http").method);
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
}
