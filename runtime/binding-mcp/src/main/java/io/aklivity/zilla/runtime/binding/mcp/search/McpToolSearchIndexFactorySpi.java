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
package io.aklivity.zilla.runtime.binding.mcp.search;

import java.util.List;
import java.util.Map;

import io.aklivity.zilla.config.binding.mcp.McpToolSearchIndexConfig;

/**
 * Service provider interface for a pluggable {@link McpToolSearchIndex} implementation.
 * <p>
 * Each ranking backend provides an implementation, registered via {@link java.util.ServiceLoader}
 * in {@code META-INF/services/io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchIndexFactorySpi}.
 * The binding selects the correct factory by matching {@link #type()} against a configured
 * indexer's {@code type} field value.
 * </p>
 */
public interface McpToolSearchIndexFactorySpi
{
    /**
     * Returns the index type name this factory handles, e.g. {@code "keyword"}.
     * <p>
     * Must match the {@link McpToolSearchIndexConfig#type} value of the indexer configuration.
     * </p>
     *
     * @return the index type name
     */
    String type();

    /**
     * Creates a new {@link McpToolSearchIndex} instance for the given indexer configuration.
     *
     * @param config   the type-specific indexer configuration
     * @param fields   the shared list of document fields to index, in configuration order
     * @param weights  the shared per-field relative importance, keyed by field name
     * @return a new index instance
     */
    McpToolSearchIndex create(
        McpToolSearchIndexConfig config,
        List<String> fields,
        Map<String, Double> weights);
}
