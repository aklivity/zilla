/*
 * Copyright 2021-2024 Aklivity Inc
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

import static java.util.stream.Collectors.toList;

import java.util.List;

import io.aklivity.zilla.runtime.binding.mcp.kafka.config.McpKafkaConditionConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public final class McpKafkaRouteConfig
{
    public final long id;

    private final List<McpKafkaConditionConfig> when;

    public McpKafkaRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.when = route.when.stream()
            .map(McpKafkaConditionConfig.class::cast)
            .collect(toList());
    }

    public boolean matches(
        String tool)
    {
        return when.isEmpty() || when.stream().anyMatch(w -> matchesTool(w, tool));
    }

    private boolean matchesTool(
        McpKafkaConditionConfig condition,
        String tool)
    {
        return condition.tool == null || condition.tool.equals(tool);
    }
}
