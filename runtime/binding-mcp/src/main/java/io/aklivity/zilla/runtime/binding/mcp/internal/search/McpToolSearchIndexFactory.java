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
package io.aklivity.zilla.runtime.binding.mcp.internal.search;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheToolsSearchConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpToolSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchIndex;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchIndexFactorySpi;

/**
 * Builds the runtime {@link McpToolSearchIndex} for a configured
 * {@code options.cache.tools.search}, dispatching each configured indexer to the
 * {@link McpToolSearchIndexFactorySpi} registered for its {@code type}, and composing more than
 * one behind {@link McpToolSearchComposite}.
 */
public final class McpToolSearchIndexFactory
{
    private final Map<String, McpToolSearchIndexFactorySpi> factoriesByType;

    public McpToolSearchIndexFactory()
    {
        this.factoriesByType = ServiceLoader
            .load(McpToolSearchIndexFactorySpi.class)
            .stream()
            .map(Supplier::get)
            .collect(toMap(McpToolSearchIndexFactorySpi::type, identity()));
    }

    public McpToolSearchIndex create(
        McpCacheToolsSearchConfig search)
    {
        McpToolSearchIndex result = null;

        if (search != null && search.indexes != null && !search.indexes.isEmpty())
        {
            List<McpToolSearchIndex> indexes = new ArrayList<>();
            for (McpToolSearchIndexConfig config : search.indexes)
            {
                McpToolSearchIndexFactorySpi factory = factoriesByType.get(config.type);
                if (factory != null)
                {
                    indexes.add(factory.create(config, search.fields, search.weights));
                }
            }

            if (!indexes.isEmpty())
            {
                result = indexes.size() == 1 ? indexes.get(0) : new McpToolSearchComposite(indexes);
            }
        }

        return result;
    }
}
