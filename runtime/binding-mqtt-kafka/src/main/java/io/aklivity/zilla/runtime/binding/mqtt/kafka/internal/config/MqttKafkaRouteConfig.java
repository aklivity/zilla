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
import java.util.function.LongPredicate;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public class MqttKafkaRouteConfig
{
    public final long id;
    public final long order;

    private final Optional<MqttKafkaWithResolver> with;
    private final List<MqttKafkaConditionMatcher> when;
    private final LongPredicate authorized;

    public final String16FW messages;
    public final String16FW retained;
    private final List<String> clients;

    public MqttKafkaRouteConfig(
        MqttKafkaOptionsConfig options,
        RouteConfig route)
    {
        this.id = route.id;
        this.order = route.order;
        this.with = Optional.ofNullable(route.with)
            .map(MqttKafkaWithConfig.class::cast)
            .map(c -> new MqttKafkaWithResolver(options, c));
        this.messages = with.isPresent() ? with.get().messages() : options.topics.messages;
        this.retained = options.topics.retained;
        this.when = route.when.stream()
            .map(MqttKafkaConditionConfig.class::cast)
            .map(MqttKafkaConditionMatcher::new)
            .collect(toList());
        this.authorized = route.authorized;
        this.clients = options.clients;
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization);
    }

    public boolean matchesSubscribe(
        String topic)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matchesSubscribe(topic));
    }

    public boolean matchesClient(
        String client)
    {
        return !when.isEmpty() && when.stream()
            .filter(m -> m.subscribeMatchers != null)
            .allMatch(m -> m.matchesSubscribe(client));
    }

    public boolean matchesPublish(
        String topic)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matchesPublish(topic));
    }
}
