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
import static java.util.Collections.emptyList;

import java.util.List;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;

public class AsyncapiClientNamespaceGenerator extends AsyncapiNamespaceGenerator
{
    public NamespaceConfig generate(
        BindingConfig binding,
        AsyncapiNamespaceConfig namespaceConfig)
    {
        final List<AsyncapiServerView> servers = namespaceConfig.servers;
        final AsyncapiOptionsConfig options = binding.options != null ? (AsyncapiOptionsConfig) binding.options : EMPTY_OPTION;
        final List<MetricRefConfig> metricRefs = binding.telemetryRef != null ?
            binding.telemetryRef.metricRefs : emptyList();

        int[] compositeSecurePorts = resolvePorts(servers, true);
        this.isTlsEnabled =  compositeSecurePorts.length > 0;

        final String namespace = String.join("+", namespaceConfig.asyncapiLabels);
        return NamespaceConfig.builder()
                .name(String.format("%s.%s-%s", qname, "$composite", namespace))
                .inject(n -> this.injectNamespaceMetric(n, !metricRefs.isEmpty()))
                .inject(n -> this.injectCatalog(n, namespaceConfig.asyncapis))
                .inject(n -> this.injectProtocolClients(n, servers, metricRefs))
                .inject(n -> this.injectProtocolRelatedBindings(n, servers, metricRefs))
                .inject(n -> this.injectTlsClient(n, options, metricRefs))
                .inject(n -> this.injectTcpClient(n, servers, options, metricRefs))
                .build();
    }

    private <C> NamespaceConfigBuilder<C> injectTcpClient(
        NamespaceConfigBuilder<C> namespace,
        List<AsyncapiServerView> servers,
        AsyncapiOptionsConfig options,
        List<MetricRefConfig> metricRefs)
    {
        for (AsyncapiServerView server : servers)
        {
            final AsyncapiProtocol protocol = server.getAsyncapiProtocol();
            namespace = namespace
                .binding()
                    .name("tcp_client0")
                    .type("tcp")
                    .kind(CLIENT)
                    .inject(b -> this.injectMetrics(b, metricRefs))
                    .options(!protocol.scheme.equals(AyncapiKafkaProtocol.SCHEME) ? options.tcp : null)
                .build();
        }

        return namespace;
    }

    private <C> NamespaceConfigBuilder<C> injectProtocolClients(
        NamespaceConfigBuilder<C> namespace,
        List<AsyncapiServerView> servers,
        List<MetricRefConfig> metricRefs)
    {
        for (AsyncapiServerView server : servers)
        {
            final AsyncapiProtocol protocol = server.getAsyncapiProtocol();
            final String scheme = protocol.scheme;
            final String exit = "sse".equals(scheme) ? "http_client0" : isTlsEnabled ? "tls_client0" : "tcp_client0";
            namespace = namespace
                .binding()
                    .name(String.format("%s_client0", scheme))
                    .type(scheme)
                    .kind(CLIENT)
                    .inject(b -> this.injectMetrics(b, metricRefs))
                    .inject(protocol::injectProtocolClientOptions)
                    .exit(exit)
                .build();
        }
        return  namespace;
    }

    protected <C> NamespaceConfigBuilder<C> injectProtocolRelatedBindings(
        NamespaceConfigBuilder<C> namespace,
        List<AsyncapiServerView> servers,
        List<MetricRefConfig> metricRefs)
    {
        for (AsyncapiServerView server : servers)
        {
            final AsyncapiProtocol protocol = server.getAsyncapiProtocol();
            namespace = protocol.injectProtocolRelatedClientBindings(namespace, metricRefs, isTlsEnabled);
        }
        return namespace;
    }

    private <C> NamespaceConfigBuilder<C> injectTlsClient(
        NamespaceConfigBuilder<C> namespace,
        AsyncapiOptionsConfig options,
        List<MetricRefConfig> metricRefs)
    {
        if (isTlsEnabled)
        {
            namespace
                .binding()
                    .name("tls_client0")
                    .type("tls")
                    .kind(CLIENT)
                    .inject(b -> this.injectMetrics(b, metricRefs))
                    .options(options.tls)
                    .vault(String.format("%s:%s", this.namespace, vault))
                    .exit("tcp_client0")
                    .build();
        }
        return namespace;
    }
}
