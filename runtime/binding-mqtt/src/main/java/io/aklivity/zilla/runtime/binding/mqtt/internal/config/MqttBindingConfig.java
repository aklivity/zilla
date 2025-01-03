/*
 * Copyright 2021-2024 Aklivity Inc.
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

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.mqtt.config.MqttCredentialsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttPatternConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttPatternConfig.MqttConnectProperty;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.String16FW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class MqttBindingConfig
{
    private static final Function<String, String> DEFAULT_CREDENTIALS = x -> null;
    private static final List<MqttVersion> DEFAULT_VERSIONS = Arrays.asList(MqttVersion.V3_1_1, MqttVersion.V_5);

    public final long id;
    public final String name;
    public final KindConfig kind;
    public final MqttOptionsConfig options;
    public final List<MqttRouteConfig> routes;
    public final Function<String, String> resolveCredentials;
    public final Function<String, String> injectCredentials;
    public final Map<Matcher, TopicValidator> topics;
    public final List<MqttVersion> versions;
    public final ToLongFunction<String> resolveId;
    public final GuardHandler guard;

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
        this.resolveCredentials = options != null && options.authorization != null ?
            asAccessor(options.authorization.credentials) : DEFAULT_CREDENTIALS;
        this.injectCredentials = options != null && options.authorization != null ?
            asInjector(options.authorization.credentials) : DEFAULT_CREDENTIALS;
        this.topics = new HashMap<>();
        if (options != null && options.topics != null)
        {
            options.topics.forEach(t ->
            {
                String topicPattern = t.name.replace(".", "\\.")
                    .replace("$", "\\$")
                    .replace("+", "[^/]*")
                    .replace("#", ".*");
                Map<String16FW, ModelConfig> userProperties =
                    Optional.ofNullable(t.userProperties).orElseGet(Collections::emptyList)
                    .stream()
                    .collect(Collectors.toMap(up -> new String16FW(up.name, ByteOrder.BIG_ENDIAN), up -> up.value));
                topics.put(Pattern.compile(topicPattern).matcher(""), new TopicValidator(t.content, userProperties));
            });
        }

        this.guard = resolveGuard(context);
        this.versions = options != null &&
            options.versions != null ? options.versions : DEFAULT_VERSIONS;
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
            .filter(r -> r.authorized(authorization) && r.matchesSubscribe(topic, authorization))
            .findFirst()
            .orElse(null);
    }

    public MqttRouteConfig resolvePublish(
        long authorization,
        String topic)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matchesPublish(topic, authorization))
            .findFirst()
            .orElse(null);
    }

    public ModelConfig supplyModelConfig(
        String topic)
    {
        ModelConfig config = null;
        if (topics != null)
        {
            for (Map.Entry<Matcher, TopicValidator> t : topics.entrySet())
            {
                final Matcher matcher = t.getKey();
                matcher.reset(topic);
                if (matcher.find())
                {
                    config = t.getValue().content;
                    break;
                }
            }
        }
        return config;
    }

    public ModelConfig supplyUserPropertyModelConfig(
        String topic,
        String16FW userPropertyKey)
    {
        ModelConfig config = null;
        if (topics != null)
        {
            for (Map.Entry<Matcher, TopicValidator> t : topics.entrySet())
            {
                final Matcher matcher = t.getKey();
                matcher.reset(topic);
                if (matcher.find())
                {
                    Map<String16FW, ModelConfig> userProperties = t.getValue().userProperties;
                    if (userProperties != null)
                    {
                        config = userProperties.get(userPropertyKey);
                    }
                    break;
                }
            }
        }
        return config;
    }

    public Function<String, String> resolveCredentials()
    {
        return resolveCredentials;
    }

    public Function<String, String> injectCredentials()
    {
        return injectCredentials;
    }

    public MqttConnectProperty authField()
    {
        return options != null && options.authorization != null ?
            options.authorization.credentials.connect.get(0).property : null;
    }

    private GuardHandler resolveGuard(
        EngineContext context)
    {
        GuardHandler guard = null;

        if (options != null &&
            options.authorization != null)
        {
            long guardId = resolveId.applyAsLong(options.authorization.name);
            guard = context.supplyGuard(guardId);
        }

        return guard;
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

            accessor = connect ->
            {
                String result = null;
                if (connect != null && connectMatch.reset(connect).matches())
                {
                    result = connectMatch.group("credentials");
                }
                return result;
            };
        }

        return accessor;
    }

    private Function<String, String> asInjector(
        MqttCredentialsConfig credentials)
    {
        Function<String, String> injector = DEFAULT_CREDENTIALS;
        List<MqttPatternConfig> connectPatterns = credentials.connect;

        if (connectPatterns != null && !connectPatterns.isEmpty())
        {
            MqttPatternConfig config = connectPatterns.get(0);

            injector = connect ->
            {
                String result = null;
                if (connect != null)
                {
                    result = config.pattern.replace("{credentials}", connect);
                }
                return result;
            };
        }

        return injector;
    }

    private static class TopicValidator
    {
        ModelConfig content;
        Map<String16FW, ModelConfig> userProperties;

        TopicValidator(
            ModelConfig content,
            Map<String16FW, ModelConfig> userProperties)
        {
            this.content = content;
            this.userProperties = userProperties;
        }
    }
}
