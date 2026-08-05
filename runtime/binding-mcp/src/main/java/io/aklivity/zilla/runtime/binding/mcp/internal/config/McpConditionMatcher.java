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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import static io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig.CAPABILITY_PROMPTS;
import static io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig.CAPABILITY_RESOURCES;
import static io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig.CAPABILITY_TOOLS;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_PROMPTS;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_RESOURCES;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_TOOLS;

import java.util.List;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.binding.mcp.McpConditionConfig;
import io.aklivity.zilla.runtime.common.lang.Matchers;

final class McpConditionMatcher
{
    private static final String DELIMITER_NAME = "__";
    private static final String DELIMITER_URI = "+";

    final String toolkit;

    private final String toolPrefix;
    private final String promptPrefix;
    private final String resourcePrefix;
    private final List<Pattern> toolAllow;
    private final List<Pattern> promptAllow;
    private final List<Pattern> resourceAllow;

    McpConditionMatcher(
        McpConditionConfig condition)
    {
        final String toolkit = condition.toolkit;
        this.toolkit = toolkit;

        // a capability is active when its own allow-set field is given; when none of the three
        // are given at all, the condition is unrestricted and every capability is active
        final boolean anyAllowSet = condition.tool != null || condition.prompt != null || condition.resource != null;
        final boolean tool = !anyAllowSet || condition.tool != null;
        final boolean prompt = !anyAllowSet || condition.prompt != null;
        final boolean resource = !anyAllowSet || condition.resource != null;

        this.toolPrefix = tool ? (toolkit != null ? toolkit + DELIMITER_NAME : "") : null;
        this.promptPrefix = prompt ? (toolkit != null ? toolkit + DELIMITER_NAME : "") : null;
        this.resourcePrefix = resource ? (toolkit != null ? toolkit + DELIMITER_URI : "") : null;

        this.toolAllow = Matchers.globAll(condition.tool);
        this.promptAllow = Matchers.globAll(condition.prompt);
        this.resourceAllow = Matchers.globAll(condition.resource);
    }

    int serverCapabilities()
    {
        int bits = 0;
        if (toolPrefix != null)
        {
            bits |= SERVER_TOOLS.value();
        }
        if (promptPrefix != null)
        {
            bits |= SERVER_PROMPTS.value();
        }
        if (resourcePrefix != null)
        {
            bits |= SERVER_RESOURCES.value();
        }
        return bits;
    }

    String match(
        String capability,
        String identifier)
    {
        final String prefix = prefix(capability);
        String result = null;

        if (prefix != null && identifier != null && identifier.startsWith(prefix))
        {
            final String stripped = identifier.substring(prefix.length());
            if (Matchers.admits(allow(capability), stripped))
            {
                result = stripped;
            }
        }

        return result;
    }

    boolean serves(
        String capability)
    {
        return prefix(capability) != null;
    }

    boolean admits(
        String capability,
        String name)
    {
        return serves(capability) && Matchers.admits(allow(capability), name);
    }

    boolean filters(
        String capability)
    {
        return serves(capability) && allow(capability) != null;
    }

    String prefix(
        String capability)
    {
        return switch (capability)
        {
        case CAPABILITY_TOOLS -> toolPrefix;
        case CAPABILITY_PROMPTS -> promptPrefix;
        case CAPABILITY_RESOURCES -> resourcePrefix;
        default -> null;
        };
    }

    private List<Pattern> allow(
        String capability)
    {
        return switch (capability)
        {
        case CAPABILITY_TOOLS -> toolAllow;
        case CAPABILITY_PROMPTS -> promptAllow;
        case CAPABILITY_RESOURCES -> resourceAllow;
        default -> null;
        };
    }
}
