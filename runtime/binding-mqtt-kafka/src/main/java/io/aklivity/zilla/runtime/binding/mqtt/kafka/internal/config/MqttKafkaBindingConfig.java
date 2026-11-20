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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config;

import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttQoS.AT_LEAST_ONCE;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaConditionKind;
import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionFactory;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttQoS;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttTopicFilterFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public class MqttKafkaBindingConfig
{
    public static final int UNSPECIFIED_ERROR = 0x80;

    private static final int SUCCESS = 0x00;

    private final List<MqttKafkaRouteConfig> bootstrapRoutes;

    public final long id;
    public final KindConfig kind;
    public final MqttKafkaOptionsConfig options;
    public final List<MqttKafkaRouteConfig> routes;
    public final List<Function<String, String>> clients;
    public final StoreHandler store;

    public MqttKafkaSessionFactory.KafkaSignalStream willProxy;
    public int capabilities;

    private final MqttQoS publishQosMax;
    private final String16FW messagesTopic;
    private final String16FW sessionsTopic;
    private final String16FW retainedTopic;

    public MqttKafkaBindingConfig(
        BindingConfig binding,
        EngineContext context)
    {
        this.id = binding.id;
        this.kind = binding.kind;
        this.options = (MqttKafkaOptionsConfig) binding.options;
        this.routes = binding.routes.stream().map(r -> new MqttKafkaRouteConfig(options, r)).collect(toList());
        this.clients = options != null && options.clients != null ?
            asAccessor(options.clients) : null;
        this.bootstrapRoutes = routes.stream()
            .filter(r ->
                options.clients != null &&
                options.clients.stream().anyMatch(r::matchesClient))
            .toList();
        this.store = options.store != null
            ? context.supplyStore(binding.resolveId.applyAsLong(options.store))
            : null;
        this.publishQosMax = MqttQoS.valueOf(options.publish.qosMax.toUpperCase());
        this.messagesTopic = new String16FW(options.topics.messages);
        this.sessionsTopic = new String16FW(options.topics.sessions);
        this.retainedTopic = new String16FW(options.topics.retained);
    }

    public MqttKafkaRouteConfig resolve(
        long authorization)
    {
        MqttKafkaRouteConfig resolved = null;

        for (int index = 0; resolved == null && index < routes.size(); index++)
        {
            final MqttKafkaRouteConfig route = routes.get(index);
            if (route.authorized(authorization))
            {
                resolved = route;
            }
        }

        return resolved;
    }

    public MqttKafkaRouteConfig resolve(
        long authorization,
        String topic)
    {
        MqttKafkaRouteConfig resolved = null;

        for (int index = 0; resolved == null && index < routes.size(); index++)
        {
            final MqttKafkaRouteConfig route = routes.get(index);
            if (route.authorized(authorization) && route.matches(topic, MqttKafkaConditionKind.PUBLISH))
            {
                resolved = route;
            }
        }

        return resolved;
    }

    public List<MqttKafkaRouteConfig> resolveAll(
        long authorization,
        Array32FW<MqttTopicFilterFW> filters)
    {
        final List<MqttKafkaRouteConfig> resolved = new ArrayList<>(routes.size());

        for (int index = 0; index < routes.size(); index++)
        {
            final MqttKafkaRouteConfig route = routes.get(index);
            if (filters.anyMatch(f -> subscribes(route, authorization, f.pattern().asString(), f.shareName().asString())))
            {
                resolved.add(route);
            }
        }

        return resolved;
    }

    public int resolveSubscribeReasonCode(
        long authorization,
        String topic)
    {
        return resolveSubscribeReasonCode(authorization, topic, null);
    }

    public int resolveSubscribeReasonCode(
        long authorization,
        String topic,
        String share)
    {
        int reasonCode = UNSPECIFIED_ERROR;

        for (int index = 0; reasonCode != SUCCESS && index < routes.size(); index++)
        {
            if (subscribes(routes.get(index), authorization, topic, share))
            {
                reasonCode = SUCCESS;
            }
        }

        return reasonCode;
    }

    private boolean subscribes(
        MqttKafkaRouteConfig route,
        long authorization,
        String topic,
        String share)
    {
        return route.authorized(authorization) && route.matches(topic, share, MqttKafkaConditionKind.SUBSCRIBE);
    }

    public MqttQoS publishQosMax()
    {
        boolean parameterized = false;

        for (int index = 0; !parameterized && index < routes.size(); index++)
        {
            final MqttKafkaRouteConfig route = routes.get(index);
            parameterized = route.with != null && route.with.containsParams();
        }

        return parameterized ? AT_LEAST_ONCE : publishQosMax;
    }

    public String16FW messagesTopic()
    {
        return messagesTopic;
    }

    public String16FW sessionsTopic()
    {
        return sessionsTopic;
    }

    public String16FW retainedTopic()
    {
        return retainedTopic;
    }

    public List<MqttKafkaRouteConfig> bootstrapRoutes()
    {
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
                String pattern = client
                    .replace("{identity}", "(?<identity>[^\\s/]+)")
                    .replace("+", "[^/]+")
                    .replace("/#", ".*");

                Matcher matcher = Pattern.compile(pattern).matcher("");

                Function<String, String> accessor = topic ->
                    topic != null &&
                    matcher.reset(topic).matches()
                        ? matcher.group("identity")
                        : null;

                accessors.add(accessor);
            }
        }

        return accessors;
    }
}
