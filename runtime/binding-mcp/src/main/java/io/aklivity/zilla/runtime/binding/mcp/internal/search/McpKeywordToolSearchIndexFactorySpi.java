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

import java.util.List;
import java.util.Map;

import io.aklivity.zilla.config.binding.mcp.McpKeywordToolSearchIndexConfig;
import io.aklivity.zilla.config.binding.mcp.McpToolSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchIndex;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchIndexFactorySpi;

public final class McpKeywordToolSearchIndexFactorySpi implements McpToolSearchIndexFactorySpi
{
    @Override
    public String type()
    {
        return McpKeywordToolSearchIndexConfig.NAME;
    }

    @Override
    public McpToolSearchIndex create(
        McpToolSearchIndexConfig config,
        List<String> fields,
        Map<String, Double> weights)
    {
        return new McpKeywordToolSearchIndex(fields, weights);
    }
}
