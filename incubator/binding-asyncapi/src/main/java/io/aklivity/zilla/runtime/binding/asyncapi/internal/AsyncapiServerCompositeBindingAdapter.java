package io.aklivity.zilla.runtime.binding.asyncapi.internal;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.mqtt.proxy.AsyncApiMqttProxyConfigGenerator;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.EngineConfig;

import java.util.List;

public class AsyncapiServerCompositeBindingAdapter implements CompositeBindingAdapterSpi
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
        AsyncapiBindingConfig asyncapiBinding = new AsyncapiBindingConfig(binding);
        BindingConfigBuilder<BindingConfig> builder = BindingConfig.builder(binding);
        final AsyncapiOptionsConfig options = asyncapiBinding.options;
        final List<AsyncapiConfig> asyncapis = options.asyncapis;
        switch (binding.kind)
        {
        case SERVER:
            for (int i = 0; i < asyncapis.size(); i++)
            {
                AsyncApiMqttProxyConfigGenerator generator = new AsyncApiMqttProxyConfigGenerator(asyncapis.get(i).asyncApi);
                EngineConfig config = generator.createServerConfig(String.format("%s/mqtt", binding.qname));
                config.namespaces.forEach(builder::composite);
            }
            return builder.build();
        case CLIENT:
            for (int i = 0; i < asyncapis.size(); i++)
            {
                AsyncApiMqttProxyConfigGenerator generator = new AsyncApiMqttProxyConfigGenerator(asyncapis.get(i).asyncApi);
                EngineConfig config = generator.createClientConfig(String.format(binding.qname, "$composite"));
                config.namespaces.forEach(builder::composite);
            }
            return builder.build();
        default:
            return binding;
        }
    }
}
