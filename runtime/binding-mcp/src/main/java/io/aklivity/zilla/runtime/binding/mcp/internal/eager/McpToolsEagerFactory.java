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
package io.aklivity.zilla.runtime.binding.mcp.internal.eager;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.aklivity.zilla.config.binding.mcp.McpToolsEagerConfig;
import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEager;
import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEagerFactorySpi;
import io.aklivity.zilla.runtime.engine.EngineContext;

public final class McpToolsEagerFactory
{
    private final Map<String, McpToolsEagerFactorySpi> factoriesByType;

    public McpToolsEagerFactory()
    {
        this.factoriesByType = ServiceLoader
            .load(McpToolsEagerFactorySpi.class)
            .stream()
            .map(Supplier::get)
            .collect(toMap(McpToolsEagerFactorySpi::type, identity()));
    }

    public McpToolsEager create(
        EngineContext context,
        List<McpToolsEagerConfig> eager)
    {
        McpToolsEager result = null;

        if (eager != null && !eager.isEmpty())
        {
            List<McpToolsEager> stages = new ArrayList<>();
            for (McpToolsEagerConfig config : eager)
            {
                McpToolsEagerFactorySpi factory = factoriesByType.get(config.type);
                if (factory != null)
                {
                    stages.add(factory.create(context, config));
                }
            }

            if (!stages.isEmpty())
            {
                result = stages.size() == 1 ? stages.get(0) : new McpToolsEagerComposite(stages);
            }
        }

        return result;
    }
}
