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

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_PROMPTS;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_RESOURCES;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_TOOLS;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_GET;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_READ;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_CALL;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.runtime.binding.mcp.config.McpConditionConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

public final class McpRouteConfig
{
    static final String CAPABILITY_TOOLS = "tools";
    static final String CAPABILITY_PROMPTS = "prompts";
    static final String CAPABILITY_RESOURCES = "resources";

    private static final String DELIMITER_NAME = "__";
    private static final String DELIMITER_URI = "+";

    public final long id;
    public final McpWithConfig with;

    private final List<ConditionMatcher> matchers;
    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public McpRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.with = McpWithConfig.class.cast(route.with);
        this.matchers = route.when.stream()
            .map(McpConditionConfig.class::cast)
            .map(ConditionMatcher::new)
            .collect(toList());
        this.authorized = route.authorized;
    }

    public boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }

    public int serverCapabilities()
    {
        int bits;
        if (matchers.isEmpty())
        {
            bits = SERVER_TOOLS.value() | SERVER_PROMPTS.value() | SERVER_RESOURCES.value();
        }
        else
        {
            bits = 0;
            for (ConditionMatcher matcher : matchers)
            {
                bits |= matcher.serverCapabilities();
            }
        }
        return bits;
    }

    public String strip(
        McpBeginExFW beginEx)
    {
        final String capability = capabilityOf(beginEx);
        final String identifier = identifierOf(beginEx);

        String result = identifier;

        if (capability != null && identifier != null && !matchers.isEmpty())
        {
            for (ConditionMatcher matcher : matchers)
            {
                final String stripped = matcher.match(capability, identifier);
                if (stripped != null)
                {
                    result = stripped;
                    break;
                }
            }
        }

        return result;
    }

    public String prefix(
        McpBeginExFW beginEx)
    {
        return prefix(capabilityOf(beginEx));
    }

    public String prefix(
        int kind)
    {
        return prefix(capabilityOf(kind));
    }

    private String prefix(
        String capability)
    {
        String result = "";

        if (capability != null && !matchers.isEmpty())
        {
            for (ConditionMatcher matcher : matchers)
            {
                final String prefix = matcher.prefix(capability);
                if (prefix != null)
                {
                    result = prefix;
                    break;
                }
            }
        }

        return result;
    }

    boolean matches(
        String capability,
        String identifier)
    {
        boolean result = matchers.isEmpty();

        if (!result)
        {
            for (ConditionMatcher matcher : matchers)
            {
                if (matcher.match(capability, identifier) != null)
                {
                    result = true;
                    break;
                }
            }
        }

        return result;
    }

    boolean serves(
        String capability)
    {
        boolean result = matchers.isEmpty();

        if (!result)
        {
            for (ConditionMatcher matcher : matchers)
            {
                if (matcher.serves(capability))
                {
                    result = true;
                    break;
                }
            }
        }

        return result;
    }

    static String capabilityOf(
        McpBeginExFW beginEx)
    {
        return capabilityOf(beginEx.kind());
    }

    static String capabilityOf(
        int kind)
    {
        return switch (kind)
        {
        case KIND_TOOLS_LIST, KIND_TOOLS_CALL -> CAPABILITY_TOOLS;
        case KIND_PROMPTS_LIST, KIND_PROMPTS_GET -> CAPABILITY_PROMPTS;
        case KIND_RESOURCES_LIST, KIND_RESOURCES_READ -> CAPABILITY_RESOURCES;
        default -> null;
        };
    }

    static String identifierOf(
        McpBeginExFW beginEx)
    {
        return switch (beginEx.kind())
        {
        case KIND_TOOLS_CALL -> beginEx.toolsCall().name().asString();
        case KIND_PROMPTS_GET -> beginEx.promptsGet().name().asString();
        case KIND_RESOURCES_READ -> beginEx.resourcesRead().uri().asString();
        default -> null;
        };
    }

    private static final class ConditionMatcher
    {
        private final String toolsPrefix;
        private final String promptsPrefix;
        private final String resourcesPrefix;

        private ConditionMatcher(
            McpConditionConfig condition)
        {
            final List<String> capabilities = condition.capability;
            final String toolkit = condition.toolkit;

            final boolean anyCapability = capabilities == null;
            final boolean tools = anyCapability || capabilities.contains(CAPABILITY_TOOLS);
            final boolean prompts = anyCapability || capabilities.contains(CAPABILITY_PROMPTS);
            final boolean resources = anyCapability || capabilities.contains(CAPABILITY_RESOURCES);

            this.toolsPrefix = tools ? (toolkit != null ? toolkit + DELIMITER_NAME : "") : null;
            this.promptsPrefix = prompts ? (toolkit != null ? toolkit + DELIMITER_NAME : "") : null;
            this.resourcesPrefix = resources ? (toolkit != null ? toolkit + DELIMITER_URI : "") : null;
        }

        private int serverCapabilities()
        {
            int bits = 0;
            if (toolsPrefix != null)
            {
                bits |= SERVER_TOOLS.value();
            }
            if (promptsPrefix != null)
            {
                bits |= SERVER_PROMPTS.value();
            }
            if (resourcesPrefix != null)
            {
                bits |= SERVER_RESOURCES.value();
            }
            return bits;
        }

        private String match(
            String capability,
            String identifier)
        {
            final String prefix = prefix(capability);
            String result = null;

            if (prefix != null && identifier != null && identifier.startsWith(prefix))
            {
                result = identifier.substring(prefix.length());
            }

            return result;
        }

        private boolean serves(
            String capability)
        {
            return prefix(capability) != null;
        }

        private String prefix(
            String capability)
        {
            return switch (capability)
            {
            case CAPABILITY_TOOLS -> toolsPrefix;
            case CAPABILITY_PROMPTS -> promptsPrefix;
            case CAPABILITY_RESOURCES -> resourcesPrefix;
            default -> null;
            };
        }
    }
}
