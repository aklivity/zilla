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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;

import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiOperation;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiChannelView;
import io.aklivity.zilla.runtime.binding.sse.config.SseConditionConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.model.core.config.DoubleModelConfig;
import io.aklivity.zilla.runtime.model.core.config.FloatModelConfig;
import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig;
import io.aklivity.zilla.runtime.model.core.config.Int64ModelConfig;

public class AsyncapiSseProtocol extends AsyncapiProtocol
{
    private static final String SCHEME = "sse";
    private static final String SECURE_PROTOCOL = "secure-sse";

    protected static final Map<String, ModelConfig> MODELS = Map.of(
        "integer", Int32ModelConfig.builder().build(),
        "integer:int32", Int32ModelConfig.builder().build(),
        "integer:int64", Int64ModelConfig.builder().build(),
        "number", FloatModelConfig.builder().build(),
        "number:float", FloatModelConfig.builder().build(),
        "number:double", DoubleModelConfig.builder().build()
    );

    private final boolean httpServerAvailable;
    private final AsyncapiOptionsConfig options;

    protected AsyncapiSseProtocol(
        String qname,
        boolean httpServerAvailable,
        List<Asyncapi> asyncapis,
        AsyncapiOptionsConfig options,
        String protocol)
    {
        super(qname, asyncapis, protocol, SCHEME, "http");
        this.httpServerAvailable = httpServerAvailable;
        this.options = options;
    }

    @Override
    public <C> NamespaceConfigBuilder<C> injectProtocolRelatedServerBindings(
        NamespaceConfigBuilder<C> namespace,
        List<MetricRefConfig> metricRefs)
    {
        if (!httpServerAvailable)
        {
            final AsyncapiProtocol httpProtocol = new AsyncapiHttpProtocol(qname, asyncapis, options, "http");

            namespace
                .binding()
                    .name(String.format("%s_server0", httpProtocol.scheme))
                    .type(httpProtocol.scheme)
                    .inject(b -> this.injectMetrics(b, metricRefs))
                    .kind(SERVER)
                    .inject(httpProtocol::injectProtocolServerOptions)
                    .inject(httpProtocol::injectProtocolServerRoutes)
                .build();
        }
        return namespace;
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
        for (Asyncapi asyncapi : asyncapis)
        {
            for (String name : asyncapi.operations.keySet())
            {
                AsyncapiOperation operation = asyncapi.operations.get(name);
                AsyncapiChannelView channel = AsyncapiChannelView.of(asyncapi.channels, operation.channel);
                String path = channel.address().replaceAll("\\{[^}]+\\}", "*");
                binding
                    .route()
                    .exit(qname)
                    .when(SseConditionConfig::builder)
                        .path(path)
                        .build()
                    .build();
            }
        }
        return binding;
    }

    @Override
    public <C> NamespaceConfigBuilder<C> injectProtocolRelatedClientBindings(
        NamespaceConfigBuilder<C> namespace,
        List<MetricRefConfig> metricRefs,
        boolean isTlsEnabled)
    {
        if (!httpServerAvailable)
        {
            namespace
                .binding()
                .name(String.format("%s_client0", "http"))
                .type("http")
                .kind(CLIENT)
                .inject(b -> this.injectMetrics(b, metricRefs))
                .exit(isTlsEnabled ? "tls_client0" : "tcp_client0")
                .build();
        }
        return namespace;
    }

    @Override
    protected boolean isSecure()
    {
        return protocol.equals(SECURE_PROTOCOL);
    }

}
