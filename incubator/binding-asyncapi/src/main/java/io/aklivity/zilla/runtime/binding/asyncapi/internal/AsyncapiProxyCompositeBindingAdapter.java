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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.PROXY;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiConditionConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiRouteConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiOperation;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiChannelView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionKind;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaWithConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;
public class AsyncapiProxyCompositeBindingAdapter extends AsyncapiCompositeBindingAdapter implements CompositeBindingAdapterSpi
{
    private static final String ASYNCAPI_SEND_ACTION_NAME = "send";
    private static final String ASYNCAPI_RECEIVE_ACTION_NAME = "receive";

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
        List<AsyncapiRouteConfig> routes = binding.routes.stream()
            .map(AsyncapiRouteConfig::new)
            .collect(Collectors.toList());
        this.asyncApis = options.specs.stream().map(s -> s.asyncApi).collect(Collectors.toList());
        this.asyncApi = asyncApis.get(0);

        //TODO: add composite for all servers
        AsyncapiServerView firstServer = AsyncapiServerView.of(asyncApi.servers.entrySet().iterator().next().getValue());

        this.qname = binding.qname;
        this.qvault = String.format("%s:%s", binding.namespace, binding.vault);
        this.protocol = resolveProtocol(firstServer.protocol(), options);

        return BindingConfig.builder(binding)
            .composite()
                .name(String.format("%s/%s", qname, protocol.scheme))
                .binding()
                    .name("mqtt_kafka_proxy0")
                    .type("mqtt-kafka")
                    .kind(PROXY)
                    .options(MqttKafkaOptionsConfig::builder)
                        .topics()
                            .sessions("mqtt-sessions")
                            .messages("mqtt-messages")
                            .retained("mqtt-retained")
                            .build()
                        .clients(Collections.emptyList())
                        .build()
                    .inject(b -> this.injectMqttKafkaRoutes(b, routes))
                    .build()
                .build()
           .build();
    }

    public <C> BindingConfigBuilder<C> injectMqttKafkaRoutes(
        BindingConfigBuilder<C> binding,
        List<AsyncapiRouteConfig> routes)
    {
        for (AsyncapiRouteConfig route : routes)
        {
            final RouteConfigBuilder<BindingConfigBuilder<C>> routeBuilder = binding.route();

            final Asyncapi kafkaAsyncapi = asyncApis.stream()
                .filter(a -> a.operations.containsKey(route.with.operation))
                .findFirst().get();
            final AsyncapiOperation withOperation = kafkaAsyncapi.operations.get(route.with.operation);
            final String messages = AsyncapiChannelView.of(kafkaAsyncapi.channels, withOperation.channel).address();

            for (AsyncapiConditionConfig condition : route.when)
            {
                final AsyncapiOperation operation = asyncApi.operations.get(condition.operation);
                final AsyncapiChannelView channel = AsyncapiChannelView.of(asyncApi.channels, operation.channel);
                final MqttKafkaConditionKind kind = operation.action.equals(ASYNCAPI_SEND_ACTION_NAME) ?
                    MqttKafkaConditionKind.PUBLISH : MqttKafkaConditionKind.SUBSCRIBE;
                final String topic = channel.address().replaceAll("\\{[^}]+\\}", "#");
                routeBuilder
                    .when(MqttKafkaConditionConfig::builder)
                        .topic(topic)
                        .kind(kind)
                    .build()
                    .with(MqttKafkaWithConfig::builder)
                        .messages(messages)
                    .build()
                    .exit(qname);
            }
            binding = routeBuilder.build();
        }
        return binding;
    }
}
