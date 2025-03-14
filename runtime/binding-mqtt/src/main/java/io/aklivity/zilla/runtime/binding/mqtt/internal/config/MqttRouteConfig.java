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

import static io.aklivity.zilla.runtime.engine.config.WithConfig.NO_COMPOSITE_ID;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

import io.aklivity.zilla.runtime.binding.mqtt.config.MqttConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttWithConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public final class MqttRouteConfig
{
    public final long id;

    private final List<MqttConditionMatcher> when;
    private final MqttWithConfig with;
    private final LongPredicate authorized;
    private final Map<String, LongFunction<String>> identities;

    public MqttRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.identities = route.guarded.stream()
            .collect(toMap(g -> g.name, g -> g.identity));
        this.when = route.when.stream()
            .map(MqttConditionConfig.class::cast)
            .map(c -> new MqttConditionMatcher(this::identity, c))
            .collect(toList());
        this.with = (MqttWithConfig) route.with;
        this.authorized = route.authorized;
    }

    public long compositeId()
    {
        return with != null ? with.compositeId : NO_COMPOSITE_ID;
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization);
    }

    LongFunction<String> identity(
        String guard)
    {
        return identities.get(guard);
    }

    boolean matchesSession(
        String clientId)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matchesSession(clientId));
    }

    boolean matchesSubscribe(
        long authorization,
        String topic)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matchesSubscribe(authorization, topic));
    }

    boolean matchesPublish(
        long authorization,
        String topic)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matchesPublish(authorization, topic));
    }
}
