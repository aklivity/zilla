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

import static io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttAuthorizationConfig.DEFAULT_CREDENTIALS;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttAuthorizationConfig.MqttCredentialsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttAuthorizationConfig.MqttPatternConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttCapabilities;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class MqttBindingConfig
{
    public final long id;
    public final String name;
    public final KindConfig kind;
    public final MqttOptionsConfig options;
    public final List<MqttRouteConfig> routes;
    public final Function<Function<String, String>, String> credentials;
    public final ToLongFunction<String> resolveId;

    public MqttBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.routes = binding.routes.stream().map(MqttRouteConfig::new).collect(toList());
        this.options = (MqttOptionsConfig) binding.options;
        this.resolveId = binding.resolveId;
        this.credentials = options != null && options.authorization != null ?
            asAccessor(options.authorization.credentials) : DEFAULT_CREDENTIALS;
    }

    public MqttRouteConfig resolve(
        long authorization,
        MqttCapabilities capabilities)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(capabilities))
            .findFirst()
            .orElse(null);
    }

    public MqttRouteConfig resolve(
        long authorization,
        String topic,
        MqttCapabilities capabilities)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(topic, capabilities))
            .findFirst()
            .orElse(null);
    }

    public Function<Function<String, String>, String> credentials()
    {
        return credentials;
    }

    private Function<Function<String, String>, String> asAccessor(
        MqttCredentialsConfig credentials)
    {
        Function<Function<String, String>, String> accessor = DEFAULT_CREDENTIALS;
        List<MqttPatternConfig> connectPatterns = credentials.connect;

        if (connectPatterns != null && !connectPatterns.isEmpty())
        {
            MqttPatternConfig config = connectPatterns.get(0);
            String name = config.name;

            Matcher connectMatch =
                Pattern.compile(String.format(
                        "(;\\s*)?%s=%s",
                        name,
                        config.pattern.replace("{credentials}", "(?<credentials>[^\\s]+)")))
                    .matcher("");

            accessor = orElseIfNull(accessor, hs ->
            {
                String connect = hs.apply("connect");
                return connect != null && connectMatch.reset(connect).find()
                    ? connectMatch.group("credentials")
                    : null;
            });
        }

        return accessor;
    }

    private static Function<Function<String, String>, String> orElseIfNull(
        Function<Function<String, String>, String> first,
        Function<Function<String, String>, String> second)
    {
        return hs ->
        {
            String result = first.apply(hs);
            return result != null ? result : second.apply(hs);
        };
    }
}
