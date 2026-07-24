/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.binding.mqtt.kafka;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.RouteConfigBuilder;

public final class MqttKafkaRouteConfigBuilder<T> extends RouteConfigBuilder<T, MqttKafkaRouteConfigBuilder<T>>
{
    MqttKafkaRouteConfigBuilder(
        Function<RouteConfig, T> mapper)
    {
        super(mapper);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttKafkaRouteConfigBuilder<T>> thisType()
    {
        return (Class<MqttKafkaRouteConfigBuilder<T>>) getClass();
    }

    public MqttKafkaConditionConfigBuilder<MqttKafkaRouteConfigBuilder<T>> when()
    {
        return new MqttKafkaConditionConfigBuilder<>(this::when);
    }

    public MqttKafkaWithConfigBuilder<MqttKafkaRouteConfigBuilder<T>> with()
    {
        return new MqttKafkaWithConfigBuilder<>(this::with);
    }
}
