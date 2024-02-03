package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.CROSS_ORIGIN;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;

import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiBinding;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;

public class OpenapiCompositeBindingAdapter implements CompositeBindingAdapterSpi
{
    @Override
    public String type()
    {
        return OpenapiBinding.NAME;
    }

    @Override
    public BindingConfig adapt(
        BindingConfig binding)
    {
        switch (binding.kind)
        {
        case SERVER:
            return resolveServerBinding(binding);
        default:
            return binding;
        }
    }

    private BindingConfig resolveServerBinding(
        BindingConfig binding)
    {
        return BindingConfig.builder(binding)
                .composite()
                    .name(String.format(binding.qname, "$composite"))
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
                    .build()
                .build();
    }
}
