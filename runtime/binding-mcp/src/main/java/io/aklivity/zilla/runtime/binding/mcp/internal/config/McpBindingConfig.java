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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_GET;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_READ;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_CALL;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;

import java.util.List;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.mcp.config.McpOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpBindingConfig
{
    private static final String CAPABILITY_TOOLS = "tools";
    private static final String CAPABILITY_PROMPTS = "prompts";
    private static final String CAPABILITY_RESOURCES = "resources";

    private static final String TOOLKIT_NAME_DELIMITER = "__";
    private static final String TOOLKIT_URI_DELIMITER = "+";

    public final long id;
    public final McpOptionsConfig options;

    private final List<McpRouteConfig> routes;

    public McpBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.options = (McpOptionsConfig) binding.options;
        this.routes = binding.routes.stream()
            .map(McpRouteConfig::new)
            .collect(Collectors.toList());
    }

    public McpRouteConfig resolve(
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }

    public McpRouteConfig resolve(
        McpBeginExFW beginEx,
        long authorization)
    {
        final String capability = capability(beginEx);
        final String toolkit = toolkit(beginEx);

        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(toolkit, capability))
            .findFirst()
            .orElse(null);
    }

    private static String capability(
        McpBeginExFW beginEx)
    {
        return switch (beginEx.kind())
        {
        case KIND_TOOLS_LIST, KIND_TOOLS_CALL -> CAPABILITY_TOOLS;
        case KIND_PROMPTS_LIST, KIND_PROMPTS_GET -> CAPABILITY_PROMPTS;
        case KIND_RESOURCES_LIST, KIND_RESOURCES_READ -> CAPABILITY_RESOURCES;
        default -> null;
        };
    }

    private static String toolkit(
        McpBeginExFW beginEx)
    {
        return switch (beginEx.kind())
        {
        case KIND_TOOLS_CALL -> prefix(beginEx.toolsCall().name().asString(), TOOLKIT_NAME_DELIMITER);
        case KIND_PROMPTS_GET -> prefix(beginEx.promptsGet().name().asString(), TOOLKIT_NAME_DELIMITER);
        case KIND_RESOURCES_READ -> prefix(beginEx.resourcesRead().uri().asString(), TOOLKIT_URI_DELIMITER);
        default -> null;
        };
    }

    private static String prefix(
        String value,
        String delimiter)
    {
        final int index = value != null ? value.indexOf(delimiter) : -1;
        return index > 0 ? value.substring(0, index) : null;
    }
}
