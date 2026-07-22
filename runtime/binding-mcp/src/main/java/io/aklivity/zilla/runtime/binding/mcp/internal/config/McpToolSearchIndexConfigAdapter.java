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

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.config.McpToolSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpToolSearchIndexConfigAdapterSpi;

public final class McpToolSearchIndexConfigAdapter implements JsonbAdapter<McpToolSearchIndexConfig, JsonObject>
{
    private static final String TYPE_NAME = "type";

    private final Map<String, McpToolSearchIndexConfigAdapterSpi> delegatesByType;

    public McpToolSearchIndexConfigAdapter()
    {
        delegatesByType = ServiceLoader
            .load(McpToolSearchIndexConfigAdapterSpi.class)
            .stream()
            .map(Supplier::get)
            .collect(toMap(McpToolSearchIndexConfigAdapterSpi::type, identity()));
    }

    @Override
    public JsonObject adaptToJson(
        McpToolSearchIndexConfig options)
    {
        McpToolSearchIndexConfigAdapterSpi delegate = delegatesByType.get(options.type);
        return delegate != null ? delegate.adaptToJson(options) : null;
    }

    @Override
    public McpToolSearchIndexConfig adaptFromJson(
        JsonObject object)
    {
        String type = object.getString(TYPE_NAME, null);
        McpToolSearchIndexConfigAdapterSpi delegate = delegatesByType.get(type);
        if (delegate == null)
        {
            throw new JsonException(String.format("Unrecognized tool search index type: %s", type));
        }
        return delegate.adaptFromJson(object);
    }
}
