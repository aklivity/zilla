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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiOperation;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.MqttQoS;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiChannelView;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionKind;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaWithConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;

public class AsyncapiMqttKafkaProxy extends AsyncapiProxy
{
    private static final String ASYNCAPI_KAFKA_PROTOCOL_NAME = "kafka";
    private static final String ASYNCAPI_MQTT_PROTOCOL_NAME = "mqtt";
    private static final String ASYNCAPI_SEND_ACTION_NAME = "send";
    private static final String ASYNCAPI_RECEIVE_ACTION_NAME = "receive";

    protected AsyncapiMqttKafkaProxy(
        String qname,
        Map<String, Asyncapi> asyncapis)
    {
        super("mqtt-kafka", qname, asyncapis);
    }

    @Override
    protected <C> BindingConfigBuilder<C> injectProxyRoutes(
        BindingConfigBuilder<C> binding,
        String namespace,
        List<AsyncapiRouteConfig> routes)
    {
        inject:
        for (AsyncapiRouteConfig route : routes)
        {
            final RouteConfigBuilder<BindingConfigBuilder<C>> routeBuilder = binding.route();

            final Asyncapi kafkaAsyncapi = asyncapis.get(route.with.apiId);

            final AsyncapiOperation withOperation = kafkaAsyncapi.operations.get(route.with.operationId);
            final String messages = AsyncapiChannelView.of(kafkaAsyncapi.channels, withOperation.channel).address();

            for (AsyncapiConditionConfig condition : route.when)
            {
                final Asyncapi mqttAsyncapi = asyncapis.get(condition.apiId);
                if (mqttAsyncapi.servers.values().stream().noneMatch(s -> s.protocol.startsWith(ASYNCAPI_MQTT_PROTOCOL_NAME)))
                {
                    break inject;
                }
                final AsyncapiOperation whenOperation = mqttAsyncapi.operations.get(condition.operationId);
                final AsyncapiChannelView channel = AsyncapiChannelView.of(mqttAsyncapi.channels, whenOperation.channel);
                final MqttKafkaConditionKind kind = whenOperation.action.equals(ASYNCAPI_SEND_ACTION_NAME) ?
                    MqttKafkaConditionKind.PUBLISH : MqttKafkaConditionKind.SUBSCRIBE;
                String topic = channel.address();

                routeBuilder
                    .when(MqttKafkaConditionConfig::builder)
                        .topic(topic)
                        .kind(kind)
                        .build()
                    .with(MqttKafkaWithConfig::builder)
                        .messages(messages.replaceAll("\\{([^{}]*)\\}", "\\${params.$1}"))
                        .build()
                    .exit(qname);
            }
            binding = routeBuilder.build();
        }
        return binding;
    }

    @Override
    public <C> BindingConfigBuilder<C> injectProxyOptions(
        BindingConfigBuilder<C> binding,
        AsyncapiOptionsConfig options)
    {
        String sessions = "";
        String messages = "";
        String retained = "";
        for (Asyncapi asyncapi : asyncapis.values())
        {
            if (asyncapi.channels.containsKey(options.mqttKafka.channels.sessions))
            {
                sessions = asyncapi.channels.get(options.mqttKafka.channels.sessions).address;
            }

            if (asyncapi.channels.containsKey(options.mqttKafka.channels.messages))
            {
                messages = asyncapi.channels.get(options.mqttKafka.channels.messages).address;
            }

            if (asyncapi.channels.containsKey(options.mqttKafka.channels.retained))
            {
                retained = asyncapi.channels.get(options.mqttKafka.channels.retained).address;
            }
        }
        return binding
            .options(MqttKafkaOptionsConfig::builder)
                .topics()
                    .sessions(sessions)
                    .messages(messages)
                    .retained(retained)
                .build()
                .publish()
                    .qosMax(MqttQoS.EXACTLY_ONCE.name().toLowerCase())
                    .build()
                .clients(Collections.emptyList())
            .build();
    }
}
