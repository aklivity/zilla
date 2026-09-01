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

import io.aklivity.zilla.config.binding.mcp.McpAllToolsEagerConfig;
import io.aklivity.zilla.config.binding.mcp.McpToolsEagerConfig;
import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEager;
import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEagerFactorySpi;
import io.aklivity.zilla.runtime.engine.EngineContext;

public final class McpAllToolsEagerFactorySpi implements McpToolsEagerFactorySpi
{
    @Override
    public String type()
    {
        return McpAllToolsEagerConfig.NAME;
    }

    @Override
    public McpToolsEager create(
        EngineContext context,
        McpToolsEagerConfig config)
    {
        return new McpAllToolsEager(context::dispatch);
    }
}
