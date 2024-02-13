package io.aklivity.zilla.runtime.binding.asyncapi.internal;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiChannel;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

import static io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiCompositeBindingAdapter.APPLICATION_JSON;

import java.util.Map;

public class AyncapiMqttProtocol extends AsyncapiProtocol
{
    public AyncapiMqttProtocol(
        Asyncapi asyncApi,
        String scheme,
        String secureScheme)
    {
        super(asyncApi, scheme, secureScheme);
    }

    @Override
    public <C> BindingConfigBuilder<C> injectProtocolServerOptions(
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

    @Override
    public <C> BindingConfigBuilder<C> injectProtocolClientOptions(
        BindingConfigBuilder<C> binding)
    {
        return null;
    }
}
