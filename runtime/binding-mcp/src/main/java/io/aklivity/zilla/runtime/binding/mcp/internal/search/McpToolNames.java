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
package io.aklivity.zilla.runtime.binding.mcp.internal.search;

/**
 * Fixed base names for the three agent-callable, search-family synthetic tools, plus the shared
 * {@code toolkit + "__" + name} prefixing convention already used for regular proxied tools
 * (see {@code McpConditionMatcher}'s {@code DELIMITER_NAME}).
 */
public final class McpToolNames
{
    public static final String SEARCH_TOOLS = "search_tools";
    public static final String DESCRIBE_TOOL = "describe_tool";
    public static final String EXECUTE_TOOL = "execute_tool";

    private static final String DELIMITER_NAME = "__";

    private McpToolNames()
    {
    }

    public static String effectiveName(
        String toolkit,
        String name)
    {
        return toolkit == null ? name : toolkit + DELIMITER_NAME + name;
    }
}
