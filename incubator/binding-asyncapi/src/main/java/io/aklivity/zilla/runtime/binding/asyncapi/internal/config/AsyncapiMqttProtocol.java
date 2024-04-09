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

import static io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiNamespaceGenerator.APPLICATION_JSON;

import java.util.Map;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiChannel;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiItem;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiTrait;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiTraitView;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttTopicConfigBuilder;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttUserPropertyConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class AsyncapiMqttProtocol extends AsyncapiProtocol
{
    private static final String SCHEME = "mqtt";
    private static final String SECURE_SCHEME = "mqtts";
    private final String guardName;
    private final MqttAuthorizationConfig authorization;

    public AsyncapiMqttProtocol(
        String qname,
        Asyncapi asyncApi,
        AsyncapiOptionsConfig options,
        String namespace)
    {
        super(qname, asyncApi, SCHEME, SECURE_SCHEME);
        final MqttOptionsConfig mqttOptions = options.mqtt;
        this.guardName =  mqttOptions != null ? String.format("%s:%s", namespace, mqttOptions.authorization.name) : null;
        this.authorization = mqttOptions != null ?
            new MqttAuthorizationConfig(guardName, mqttOptions.authorization.credentials) : null;
    }

    @Override
    public <C> BindingConfigBuilder<C> injectProtocolServerOptions(
        BindingConfigBuilder<C> binding)
    {
        binding
            .options(MqttOptionsConfig::builder)
            .inject(this::injectMqttTopicsOptions)
            .inject(this::injectMqttAuthorization)
            .build();
        return binding;
    }

    private <C> MqttOptionsConfigBuilder<C> injectMqttAuthorization(
        MqttOptionsConfigBuilder<C> options)
    {
        if (isJwtEnabled)
        {
            options.authorization(authorization).build();
        }
        return options;
    }

    private <C> MqttOptionsConfigBuilder<C> injectMqttTopicsOptions(
        MqttOptionsConfigBuilder<C> options)
    {
        for (Map.Entry<String, AsyncapiChannel> channelEntry : asyncApi.channels.entrySet())
        {
            String topic = channelEntry.getValue().address.replaceAll("\\{[^}]+\\}", "#");
            Map<String, AsyncapiMessage> messages = channelEntry.getValue().messages;
            if (hasJsonContentType())
            {
                options
                    .topic()
                    .name(topic)
                    .content(JsonModelConfig::builder)
                        .catalog()
                            .name(INLINE_CATALOG_NAME)
                            .inject(cataloged -> injectJsonSchemas(cataloged, messages, APPLICATION_JSON))
                            .build()
                        .build()
                    .inject(t -> injectMqttUserPropertiesConfig(t, messages))
                    .build();
            }
        }
        return options;
    }

    private <C> MqttTopicConfigBuilder<C> injectMqttUserPropertiesConfig(
        MqttTopicConfigBuilder<C> topic,
        Map<String, AsyncapiMessage> messages)
    {
        for (Map.Entry<String, AsyncapiMessage> messageEntry : messages.entrySet())
        {
            AsyncapiMessageView message =
                AsyncapiMessageView.of(asyncApi.components.messages, messageEntry.getValue());

            if (message.traits() != null)
            {
                for (AsyncapiTrait asyncapiTrait : message.traits())
                {
                    AsyncapiTraitView trait = AsyncapiTraitView.of(asyncApi.components.messageTraits, asyncapiTrait);

                    for (Map.Entry<String, AsyncapiItem> header : trait.commonHeaders().properties.entrySet())
                    {
                        topic
                            .userProperty()
                            .inject(u -> injectUserProperty(u, header.getKey()))
                            .build();
                    }
                }
            }
        }

        return topic;
    }

    private <C> MqttUserPropertyConfigBuilder<C> injectUserProperty(
        MqttUserPropertyConfigBuilder<C> userProperty,
        String subject)
    {
        userProperty
            .name(subject)
            .value(JsonModelConfig::builder)
            .catalog()
                .name(INLINE_CATALOG_NAME)
                .schema()
                    .version(VERSION_LATEST)
                    .subject(subject)
                    .build()
                .build()
            .build();
        return userProperty;
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
        return findFirstServerUrlWithScheme(SECURE_SCHEME) != null;
    }
}
