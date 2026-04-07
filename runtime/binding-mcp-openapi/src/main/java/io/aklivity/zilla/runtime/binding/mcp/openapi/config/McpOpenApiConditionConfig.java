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
package io.aklivity.zilla.runtime.binding.mcp.openapi.config;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;

public final class McpOpenApiConditionConfig extends ConditionConfig
{
    public final String tool;
    public final String resource;

    public McpOpenApiConditionConfig(
        String tool,
        String resource)
    {
        this.tool = tool;
        this.resource = resource;
    }

    public boolean matches(
        String tool,
        String resource)
    {
        return matchesTool(tool) && matchesResource(resource);
    }

    private boolean matchesTool(
        String tool)
    {
        return this.tool == null || this.tool.equals(tool);
    }

    private boolean matchesResource(
        String resource)
    {
        return this.resource == null || this.resource.equals(resource);
    }
}
