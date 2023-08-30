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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Optional;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionFactory;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public class MqttKafkaBindingConfig
{
    public final long id;
    public final KindConfig kind;
    public final MqttKafkaOptionsConfig options;
    public final List<MqttKafkaRouteConfig> routes;
    public MqttKafkaSessionFactory.KafkaWillProxy willProxy;

    public MqttKafkaBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.kind = binding.kind;
        this.options = Optional.ofNullable(binding.options)
            .map(MqttKafkaOptionsConfig.class::cast)
            .orElse(MqttKafkaOptionsConfigAdapter.DEFAULT);
        this.routes = binding.routes.stream().map(MqttKafkaRouteConfig::new).collect(toList());
    }

    public MqttKafkaRouteConfig resolve(
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }

    public String16FW messagesTopic()
    {
        return options.topics.messages;
    }

    public String16FW sessionsTopic()
    {
        return options.topics.sessions;
    }

    public String16FW retainedTopic()
    {
        return options.topics.retained;
    }
}
