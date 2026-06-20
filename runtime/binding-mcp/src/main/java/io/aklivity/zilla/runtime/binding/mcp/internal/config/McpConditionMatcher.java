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

import static io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig.CAPABILITY_PROMPTS;
import static io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig.CAPABILITY_RESOURCES;
import static io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig.CAPABILITY_TOOLS;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_PROMPTS;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_RESOURCES;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_TOOLS;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.mcp.config.McpConditionConfig;

final class McpConditionMatcher
{
    private static final String DELIMITER_NAME = "__";
    private static final String DELIMITER_URI = "+";

    final String toolkit;

    private final String toolsPrefix;
    private final String promptsPrefix;
    private final String resourcesPrefix;
    private final List<Pattern> toolsAllow;
    private final List<Pattern> promptsAllow;
    private final List<Pattern> resourcesAllow;

    McpConditionMatcher(
        McpConditionConfig condition)
    {
        final List<String> capabilities = condition.capability;
        final String toolkit = condition.toolkit;
        this.toolkit = toolkit;

        final boolean anyCapability = capabilities == null;
        final boolean tools = anyCapability || capabilities.contains(CAPABILITY_TOOLS);
        final boolean prompts = anyCapability || capabilities.contains(CAPABILITY_PROMPTS);
        final boolean resources = anyCapability || capabilities.contains(CAPABILITY_RESOURCES);

        this.toolsPrefix = tools ? (toolkit != null ? toolkit + DELIMITER_NAME : "") : null;
        this.promptsPrefix = prompts ? (toolkit != null ? toolkit + DELIMITER_NAME : "") : null;
        this.resourcesPrefix = resources ? (toolkit != null ? toolkit + DELIMITER_URI : "") : null;

        this.toolsAllow = compile(condition.tools);
        this.promptsAllow = compile(condition.prompts);
        this.resourcesAllow = compile(condition.resources);
    }

    int serverCapabilities()
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

    String match(
        String capability,
        String identifier)
    {
        final String prefix = prefix(capability);
        String result = null;

        if (prefix != null && identifier != null && identifier.startsWith(prefix))
        {
            final String stripped = identifier.substring(prefix.length());
            if (admits(allow(capability), stripped))
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
        return serves(capability) && admits(allow(capability), name);
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
        case CAPABILITY_TOOLS -> toolsPrefix;
        case CAPABILITY_PROMPTS -> promptsPrefix;
        case CAPABILITY_RESOURCES -> resourcesPrefix;
        default -> null;
        };
    }

    private List<Pattern> allow(
        String capability)
    {
        return switch (capability)
        {
        case CAPABILITY_TOOLS -> toolsAllow;
        case CAPABILITY_PROMPTS -> promptsAllow;
        case CAPABILITY_RESOURCES -> resourcesAllow;
        default -> null;
        };
    }

    private static boolean admits(
        List<Pattern> allow,
        String name)
    {
        boolean result = allow == null;

        if (!result)
        {
            for (Pattern pattern : allow)
            {
                if (pattern.matcher(name).matches())
                {
                    result = true;
                    break;
                }
            }
        }

        return result;
    }

    private static List<Pattern> compile(
        List<String> globs)
    {
        return globs == null
            ? null
            : globs.stream()
                .map(McpConditionMatcher::compile)
                .collect(Collectors.toList());
    }

    private static Pattern compile(
        String glob)
    {
        final StringBuilder regex = new StringBuilder();
        final String[] literals = glob.split("\\*", -1);
        for (int index = 0; index < literals.length; index++)
        {
            if (index > 0)
            {
                regex.append(".*");
            }
            if (!literals[index].isEmpty())
            {
                regex.append(Pattern.quote(literals[index]));
            }
        }
        return Pattern.compile(regex.toString());
    }
}
