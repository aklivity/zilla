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

import jakarta.json.Json;
import jakarta.json.JsonObject;

import io.aklivity.zilla.runtime.binding.mcp.config.McpKeywordToolSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpToolSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpToolSearchIndexConfigAdapterSpi;

public final class McpKeywordToolSearchIndexConfigAdapterSpi implements McpToolSearchIndexConfigAdapterSpi
{
    private static final String TYPE_NAME = "type";

    @Override
    public String type()
    {
        return McpKeywordToolSearchIndexConfig.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        McpToolSearchIndexConfig options)
    {
        return Json.createObjectBuilder()
            .add(TYPE_NAME, McpKeywordToolSearchIndexConfig.NAME)
            .build();
    }

    @Override
    public McpToolSearchIndexConfig adaptFromJson(
        JsonObject object)
    {
        return new McpKeywordToolSearchIndexConfig();
    }
}
