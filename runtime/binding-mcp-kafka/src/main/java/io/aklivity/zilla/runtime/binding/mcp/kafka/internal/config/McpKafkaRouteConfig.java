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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.config.binding.mcp.kafka.McpKafkaConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;

public final class McpKafkaRouteConfig
{
    public long id;

    private final List<McpKafkaConditionMatcher> when;
    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public McpKafkaRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.when = route.when.stream()
            .map(McpKafkaConditionConfig.class::cast)
            .map(McpKafkaConditionMatcher::new)
            .collect(toList());
        this.authorized = route.authorized;
    }

    public boolean authorized(
        long authorization)
    {
        return authorized == null || authorized.test(authorization, identity());
    }

    public boolean matches(
        String tool,
        String topic)
    {
        return when.isEmpty() || when.stream().anyMatch(w -> w.matchesTool(tool) && w.matchesTopic(topic));
    }
}
