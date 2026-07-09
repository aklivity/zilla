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
package io.aklivity.zilla.runtime.binding.mcp.config;

import static java.util.function.Function.identity;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class McpCacheToolsSearchConfig
{
    public final String toolkit;
    public final int limit;
    public final List<String> fields;
    public final Map<String, Double> weights;
    public final List<McpToolSearchIndexConfig> indexes;

    McpCacheToolsSearchConfig(
        String toolkit,
        int limit,
        List<String> fields,
        Map<String, Double> weights,
        List<McpToolSearchIndexConfig> indexes)
    {
        this.toolkit = toolkit;
        this.limit = limit;
        this.fields = fields;
        this.weights = weights;
        this.indexes = indexes;
    }

    public static McpCacheToolsSearchConfigBuilder<McpCacheToolsSearchConfig> builder()
    {
        return new McpCacheToolsSearchConfigBuilder<>(identity());
    }

    public static <T> McpCacheToolsSearchConfigBuilder<T> builder(
        Function<McpCacheToolsSearchConfig, T> mapper)
    {
        return new McpCacheToolsSearchConfigBuilder<>(mapper);
    }
}
