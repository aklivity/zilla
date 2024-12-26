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

import java.util.List;
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

    public MqttRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.when = route.when.stream()
            .map(MqttConditionConfig.class::cast)
            .map(conf -> new MqttConditionMatcher(conf, route.guarded))
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

    boolean matchesSession(
        String clientId)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matchesSession(clientId));
    }

    boolean matchesSubscribe(
        String topic,
        long authorization)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matchesSubscribe(topic, authorization));
    }

    boolean matchesPublish(
        String topic,
        long authorization)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matchesPublish(topic, authorization));
    }
}
