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

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.config.engine.KindConfig;

public final class McpKafkaBindingConfig
{
    public final long id;
    public final String name;
    public final KindConfig kind;
    public final List<McpKafkaRouteConfig> routes;

    // memoized tools/list reply; derived solely from static binding config, built once on first request
    private byte[] toolsListJson;

    public McpKafkaBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.routes = binding.routes.stream().map(McpKafkaRouteConfig::new).collect(toList());
    }

    public McpKafkaRouteConfig resolve(
        long authorization,
        String tool,
        String topic)
    {
        return routes.stream()
            .filter(r -> r.matches(tool, topic) && r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }

    public List<GuardedConfig> toolGuarded(
        String name)
    {
        final List<GuardedConfig> result = new ArrayList<>();
        for (McpKafkaRouteConfig route : routes)
        {
            if (route.matches(name, null))
            {
                result.addAll(route.guarded);
            }
        }
        return result;
    }

    public byte[] toolsListJson()
    {
        return toolsListJson;
    }

    public void toolsListJson(
        byte[] json)
    {
        this.toolsListJson = json;
    }
}
