/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.mqtt.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.mqtt.config.MqttCredentialsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttPatternConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttPatternConfig.MqttConnectProperty;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.validator.Validator;

public final class MqttBindingConfig
{
    private static final Function<String, String> DEFAULT_CREDENTIALS = x -> null;

    public final long id;
    public final String name;
    public final KindConfig kind;
    public final MqttOptionsConfig options;
    public final List<MqttRouteConfig> routes;
    public final Function<String, String> credentials;
    public final Map<String, Validator> topics;
    public final ToLongFunction<String> resolveId;

    public MqttBindingConfig(
        BindingConfig binding,
        EngineContext context)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.routes = binding.routes.stream().map(MqttRouteConfig::new).collect(toList());
        this.options = (MqttOptionsConfig) binding.options;
        this.resolveId = binding.resolveId;
        this.credentials = options != null && options.authorization != null ?
            asAccessor(options.authorization.credentials) : DEFAULT_CREDENTIALS;
        this.topics = options != null &&
            options.topics != null
            ? options.topics.stream()
            .collect(Collectors.toMap(t -> t.name,
                t -> context.createWriteValidator(t.content, resolveId))) : null;
    }

    public MqttRouteConfig resolve(
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }

    public MqttRouteConfig resolveSession(
        long authorization,
        String clientId)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matchesSession(clientId))
            .findFirst()
            .orElse(null);
    }

    public MqttRouteConfig resolveSubscribe(
        long authorization,
        String topic)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matchesSubscribe(topic))
            .findFirst()
            .orElse(null);
    }

    public MqttRouteConfig resolvePublish(
        long authorization,
        String topic)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matchesPublish(topic))
            .findFirst()
            .orElse(null);
    }

    public Function<String, String> credentials()
    {
        return credentials;
    }

    public MqttConnectProperty authField()
    {
        return options != null && options.authorization != null ?
            options.authorization.credentials.connect.get(0).property : null;
    }

    private Function<String, String> asAccessor(
        MqttCredentialsConfig credentials)
    {
        Function<String, String> accessor = DEFAULT_CREDENTIALS;
        List<MqttPatternConfig> connectPatterns = credentials.connect;

        if (connectPatterns != null && !connectPatterns.isEmpty())
        {
            MqttPatternConfig config = connectPatterns.get(0);

            Matcher connectMatch =
                Pattern.compile(config.pattern.replace("{credentials}", "(?<credentials>[^\\s]+)"))
                    .matcher("");

            accessor = orElseIfNull(accessor, connect ->
            {
                String result = null;
                if (connect != null && connectMatch.reset(connect).matches())
                {
                    result = connectMatch.group("credentials");
                }
                return result;
            });
        }

        return accessor;
    }

    private static Function<String, String> orElseIfNull(
        Function<String, String> first,
        Function<String, String> second)
    {
        return x ->
        {
            String result = first.apply(x);
            return result != null ? result : second.apply(x);
        };
    }
}
