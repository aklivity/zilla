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

import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

/**
 * Service provider interface for a pluggable ranking backend behind
 * {@code options.cache.tools.search}'s {@code type}/{@code index} discriminator.
 * <p>
 * Each backend provides an implementation, registered via {@link java.util.ServiceLoader} in
 * {@code META-INF/services/io.aklivity.zilla.runtime.binding.mcp.config.McpToolSearchIndexConfigAdapterSpi}.
 * The binding selects the correct adapter by matching {@link #type()} against an indexer
 * entry's {@code type} field value.
 * </p>
 */
public interface McpToolSearchIndexConfigAdapterSpi extends JsonbAdapter<McpToolSearchIndexConfig, JsonObject>
{
    /**
     * Returns the index type name this adapter handles, e.g. {@code "keyword"}.
     * <p>
     * Must match the {@code type} field value of the indexer entry.
     * </p>
     *
     * @return the index type name
     */
    String type();

    /**
     * Serializes a type-specific {@link McpToolSearchIndexConfig} instance to a
     * {@link jakarta.json.JsonObject} for writing back to YAML/JSON.
     *
     * @param options  the indexer configuration to serialize
     * @return a {@link jakarta.json.JsonObject} representation of the indexer configuration
     */
    @Override
    JsonObject adaptToJson(
        McpToolSearchIndexConfig options);

    /**
     * Deserializes a {@link jakarta.json.JsonObject} indexer entry into a type-specific
     * {@link McpToolSearchIndexConfig} instance.
     *
     * @param object  the raw JSON object from the indexer entry
     * @return the deserialized {@link McpToolSearchIndexConfig}
     */
    @Override
    McpToolSearchIndexConfig adaptFromJson(
        JsonObject object);
}
