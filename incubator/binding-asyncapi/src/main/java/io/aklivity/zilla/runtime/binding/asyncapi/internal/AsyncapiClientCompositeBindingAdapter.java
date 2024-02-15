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

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;
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
        this.asyncApi = asyncapiConfig.asyncApi;

        //TODO: add composite for all servers
        AsyncapiServerView firstServer = AsyncapiServerView.of(asyncApi.servers.entrySet().iterator().next().getValue());

        this.qname = binding.qname;
        this.qvault = String.format("%s:%s", binding.namespace, binding.vault);
        this.protocol = resolveProtocol(firstServer.protocol(), options);
        this.allPorts = resolveAllPorts();
        this.compositePorts = resolvePortsForScheme(protocol.scheme);
        this.compositeSecurePorts = resolvePortsForScheme(protocol.secureScheme);
        this.isPlainEnabled = compositePorts != null;
        this.isTlsEnabled = compositeSecurePorts != null;

        return BindingConfig.builder(binding)
            .composite()
                .name(String.format("%s.%s", qname, "$composite"))
                .binding()
                    .name(String.format("%s_client0", protocol.scheme))
                    .type(protocol.scheme)
                    .kind(CLIENT)
                    .exit(isTlsEnabled ? "tls_client0" : "tcp_client0")
                    .build()
                .inject(n -> injectTlsClient(n, options))
                .binding()
                    .name("tcp_client0")
                    .type("tcp")
                    .kind(CLIENT)
                    .options(TcpOptionsConfig::builder)
                        .host(options.tcp.host)
                        .ports(options.tcp.ports)
                        .build()
                    .build()
                .build()
            .build();
    }

    private <C> NamespaceConfigBuilder<C> injectTlsClient(
        NamespaceConfigBuilder<C> namespace,
        AsyncapiOptionsConfig options)
    {
        if (isTlsEnabled)
        {
            namespace
                .binding()
                    .name("tls_client0")
                    .type("tls")
                    .kind(CLIENT)
                    .options(TlsOptionsConfig::builder)
                        .trust(options.tls.trust)
                        .sni(options.tls.sni)
                        .alpn(options.tls.alpn)
                        .trustcacerts(options.tls.trustcacerts)
                        .build()
                    .vault(qvault)
                    .exit("tcp_client0")
                    .build();
        }
        return namespace;
    }
}
