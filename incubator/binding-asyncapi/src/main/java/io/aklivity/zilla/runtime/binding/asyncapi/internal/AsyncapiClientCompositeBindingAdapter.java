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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static java.util.Collections.emptyList;

import java.util.List;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiServer;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;

public class AsyncapiClientCompositeBindingAdapter extends AsyncapiCompositeBindingAdapter implements CompositeBindingAdapterSpi
{

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
        final AsyncapiServer server = asyncapi.servers
            .getOrDefault("options.server", asyncapi.servers.entrySet().iterator().next().getValue());
        AsyncapiServerView serverView = AsyncapiServerView.of(server);
        this.qname = binding.qname;
        this.namespace = binding.namespace;
        this.qvault = binding.qvault;
        this.vault = binding.vault;
        this.protocol = resolveProtocol(serverView.protocol(), options);
        this.isTlsEnabled = protocol.isSecure();

        return BindingConfig.builder(binding)
            .composite()
                .name(String.format("%s.%s", qname, "$composite"))
                .inject(n -> this.injectNamespaceMetric(n, !metricRefs.isEmpty()))
                .inject(n -> this.injectCatalog(n, asyncapi))
                .inject(n -> protocol.injectProtocolClientCache(n, metricRefs))
                .binding()
                    .name(String.format("%s_client0", protocol.scheme))
                    .type(protocol.scheme)
                    .kind(CLIENT)
                    .inject(b -> this.injectMetrics(b, metricRefs, protocol.scheme))
                    .inject(protocol::injectProtocolClientOptions)
                    .exit(isTlsEnabled ? "tls_client0" : "tcp_client0")
                    .build()
                .inject(n -> injectTlsClient(n, options, metricRefs))
                .binding()
                    .name("tcp_client0")
                    .type("tcp")
                    .kind(CLIENT)
                    .inject(b -> this.injectMetrics(b, metricRefs, "tcp"))
                    .options(options.tcp)
                    .build()
                .build()
            .build();
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
                    .inject(b -> this.injectMetrics(b, metricRefs, "tls"))
                    .options(options.tls)
                    .vault(String.format("%s:%s", this.namespace, vault))
                    .exit("tcp_client0")
                    .build();
        }
        return namespace;
    }
}
