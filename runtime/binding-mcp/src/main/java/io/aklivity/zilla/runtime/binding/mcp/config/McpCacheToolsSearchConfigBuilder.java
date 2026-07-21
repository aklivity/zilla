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
package io.aklivity.zilla.runtime.binding.mcp.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class McpCacheToolsSearchConfigBuilder<T> extends ConfigBuilder<T, McpCacheToolsSearchConfigBuilder<T>>
{
    private final Function<McpCacheToolsSearchConfig, T> mapper;

    private String toolkit;
    private int limit = -1;
    private List<String> fields;
    private Map<String, Double> weights;
    private List<McpToolSearchIndexConfig> indexes;

    McpCacheToolsSearchConfigBuilder(
        Function<McpCacheToolsSearchConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpCacheToolsSearchConfigBuilder<T>> thisType()
    {
        return (Class<McpCacheToolsSearchConfigBuilder<T>>) getClass();
    }

    public McpCacheToolsSearchConfigBuilder<T> toolkit(
        String toolkit)
    {
        this.toolkit = toolkit;
        return this;
    }

    public McpCacheToolsSearchConfigBuilder<T> limit(
        int limit)
    {
        this.limit = limit;
        return this;
    }

    public McpCacheToolsSearchConfigBuilder<T> fields(
        List<String> fields)
    {
        this.fields = fields;
        return this;
    }

    public McpCacheToolsSearchConfigBuilder<T> weight(
        String field,
        double weight)
    {
        if (weights == null)
        {
            weights = new LinkedHashMap<>();
        }
        weights.put(field, weight);
        return this;
    }

    public McpCacheToolsSearchConfigBuilder<T> index(
        McpToolSearchIndexConfig index)
    {
        if (indexes == null)
        {
            indexes = new ArrayList<>();
        }
        indexes.add(index);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpCacheToolsSearchConfig(toolkit, limit, fields, weights, indexes));
    }
}
