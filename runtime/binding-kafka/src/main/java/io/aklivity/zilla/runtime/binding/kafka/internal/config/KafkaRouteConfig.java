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
package io.aklivity.zilla.runtime.binding.kafka.internal.config;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;

import java.util.List;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaConditionConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

public final class KafkaRouteConfig
{
    public final long id;
    public final KafkaWithConfig with;

    private final List<KafkaConditionMatcher> when;
    private final LongObjectPredicate authorized;

    public KafkaRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.when = route.when.stream()
            .map(KafkaConditionConfig.class::cast)
            .map(KafkaConditionMatcher::new)
            .collect(toList());
        this.with = (KafkaWithConfig) route.with;
        this.authorized = route.authorized;
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }

    boolean matches(
        String topic,
        String groupId)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matches(topic, groupId));
    }
}
