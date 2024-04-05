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
import static java.util.Collections.emptyList;

import java.util.List;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpConditionConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;

public class AsyncapiServerCompositeBindingAdapter extends AsyncapiCompositeBindingAdapter implements CompositeBindingAdapterSpi
{
    private int[] compositePorts;

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
        this.asyncapi = asyncapiConfig.asyncapi;
        final List<MetricRefConfig> metricRefs = binding.telemetryRef != null ?
            binding.telemetryRef.metricRefs : emptyList();

        //TODO: add composite for all servers
        AsyncapiServerView firstServer = AsyncapiServerView.of(asyncapi.servers.entrySet().iterator().next().getValue());

        this.qname = binding.qname;
        this.qvault = binding.qvault;
        this.protocol = resolveProtocol(firstServer.protocol(), options);
        //TODO: pass port so we can verify if port is a valid value based on the variable if exists
        this.compositePorts = protocol.resolvePorts();
        this.isTlsEnabled = protocol.isSecure();

        return BindingConfig.builder(binding)
            .composite()
                .name(String.format("%s/%s", qname, protocol.scheme))
                .inject(n -> this.injectNamespaceMetric(n, !metricRefs.isEmpty()))
                .inject(n -> this.injectCatalog(n, asyncapi))
                .binding()
                    .name("tcp_server0")
                    .type("tcp")
                    .kind(SERVER)
                    .inject(b -> this.injectMetrics(b, metricRefs, "tcp"))
                    .options(TcpOptionsConfig::builder)
                        .host("0.0.0.0")
                        .ports(compositePorts)
                        .build()
                    .inject(this::injectPlainTcpRoute)
                    .inject(b -> this.injectTlsTcpRoute(b, metricRefs))
                    .build()
                .inject(n -> injectTlsServer(n, options))
                .binding()
                    .name(String.format("%s_server0", protocol.scheme))
                    .type(protocol.scheme)
                    .inject(b -> this.injectMetrics(b, metricRefs, protocol.scheme))
                    .kind(SERVER)
                    .inject(protocol::injectProtocolServerOptions)
                    .inject(protocol::injectProtocolServerRoutes)
                    .build()
                .build()
           .build();
    }

    private <C> BindingConfigBuilder<C> injectPlainTcpRoute(
        BindingConfigBuilder<C> binding)
    {
        if (!isTlsEnabled)
        {
            binding
                .route()
                    .when(TcpConditionConfig::builder)
                        .ports(compositePorts)
                        .build()
                    .exit(String.format("%s_server0", protocol.scheme))
                    .build();
        }
        return binding;
    }

    private <C> BindingConfigBuilder<C> injectTlsTcpRoute(
        BindingConfigBuilder<C> binding,
        List<MetricRefConfig> metricRefs)
    {
        if (isTlsEnabled)
        {
            binding
                .inject(b -> this.injectMetrics(b, metricRefs, "tls"))
                .route()
                    .when(TcpConditionConfig::builder)
                        .ports(compositePorts)
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
                    .vault(String.format("%s:%s", this.namespace, vault))
                    .exit(String.format("%s_server0", protocol.scheme))
                    .build();
        }
        return namespace;
    }
}
