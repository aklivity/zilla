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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionFactory;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttTopicFilterFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public class MqttKafkaBindingConfig
{
    private final List<MqttKafkaRouteConfig> bootstrapRoutes;

    public final long id;
    public final KindConfig kind;
    public final MqttKafkaOptionsConfig options;
    public final List<MqttKafkaRouteConfig> routes;
    public final List<Function<String, String>> clients;

    public MqttKafkaSessionFactory.KafkaSignalStream willProxy;

    public MqttKafkaBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.kind = binding.kind;
        this.options = (MqttKafkaOptionsConfig) binding.options;
        this.routes = binding.routes.stream().map(r -> new MqttKafkaRouteConfig(options, r)).collect(toList());
        this.clients = options != null && options.clients != null ?
            asAccessor(options.clients) : null;
        this.bootstrapRoutes = new ArrayList<>();
    }

    public MqttKafkaRouteConfig resolve(
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }

    public MqttKafkaRouteConfig resolve(
        long authorization,
        String topic)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matchesPublish(topic))
            .findFirst()
            .orElse(null);
    }

    public List<MqttKafkaRouteConfig> resolveAll(
        long authorization,
        Array32FW<MqttTopicFilterFW> filters)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && filters.anyMatch(f -> r.matchesSubscribe(f.pattern().asString())))
            .collect(Collectors.toList());
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

    public List<MqttKafkaRouteConfig> bootstrapRoutes()
    {
        routes.forEach(r ->
        {
            if (options.clients.stream().anyMatch(r::matchesClient))
            {
                bootstrapRoutes.add(r);
            }
        });
        return bootstrapRoutes;
    }

    private List<Function<String, String>> asAccessor(
        List<String> clients)
    {
        List<Function<String, String>> accessors = new ArrayList<>();

        if (clients != null)
        {
            for (String client : clients)
            {
                Matcher topicMatch =
                    Pattern.compile(client.replace("{identity}", "(?<identity>[^\\s/]+)").replace("#", ".*"))
                        .matcher("");

                Function<String, String> accessor = topic ->
                {
                    String result = null;
                    if (topic != null && topicMatch.reset(topic).matches())
                    {
                        result = topicMatch.group("identity");
                    }
                    return result;
                };
                accessors.add(accessor);
            }
        }

        return accessors;
    }
}
