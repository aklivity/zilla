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
package io.aklivity.zilla.config.binding.mcp;

import static java.util.function.Function.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;
import io.aklivity.zilla.config.engine.NamedConfig;

public final class McpCacheToolsSearchConfig extends Config.Extensible
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
        super(null, withIndexes(indexes));
        this.toolkit = toolkit;
        this.limit = limit;
        this.fields = fields;
        this.weights = weights;
        this.indexes = indexes;
    }

    // each configured index may itself contribute named references (e.g. an embedding vault); folding
    // them in here lets McpCacheToolsConfig discover every name under search generically via search.refs()
    private static List<NamedConfig> withIndexes(
        List<McpToolSearchIndexConfig> indexes)
    {
        List<NamedConfig> all = new ArrayList<>();
        if (indexes != null)
        {
            for (McpToolSearchIndexConfig index : indexes)
            {
                all.addAll(index.refs());
            }
        }
        return all;
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
