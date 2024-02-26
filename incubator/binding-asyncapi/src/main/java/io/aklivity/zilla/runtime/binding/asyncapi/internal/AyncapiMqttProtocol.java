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

import static io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiCompositeBindingAdapter.APPLICATION_JSON;

import java.util.Map;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiChannel;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class AyncapiMqttProtocol extends AsyncapiProtocol
{
    private static final String SCHEME = "mqtt";
    private static final String SECURE_SCHEME = "mqtts";

    public AyncapiMqttProtocol(
        String qname,
        Asyncapi asyncApi)
    {
        super(qname, asyncApi, SCHEME, SECURE_SCHEME);
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
    public <C> BindingConfigBuilder<C> injectProtocolServerRoutes(
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

    @Override
    protected boolean isSecure()
    {
        return scheme.equals(SECURE_SCHEME);
    }
}
