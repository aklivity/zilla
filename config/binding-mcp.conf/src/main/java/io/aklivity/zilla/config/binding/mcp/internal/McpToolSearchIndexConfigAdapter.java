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
package io.aklivity.zilla.config.binding.mcp.internal;

import java.util.List;

import jakarta.json.JsonException;
import jakarta.json.JsonObject;

import io.aklivity.zilla.config.binding.mcp.McpKeywordToolSearchIndexConfig;
import io.aklivity.zilla.config.binding.mcp.McpToolSearchIndexConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.ConfigExtAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class McpToolSearchIndexConfigAdapter extends ConfigAdapter<McpToolSearchIndexConfig, JsonObject>
{
    private static final String TYPE_NAME = "type";

    private final McpKeywordToolSearchIndexConfigAdapter keyword = new McpKeywordToolSearchIndexConfigAdapter();
    private final List<ConfigExtAdapter<OptionsConfig>> extensions;

    public McpToolSearchIndexConfigAdapter(
        List<ConfigExtAdapter<OptionsConfig>> extensions)
    {
        this.extensions = extensions;
    }

    @Override
    public JsonObject adaptToJson(
        McpToolSearchIndexConfig options)
    {
        JsonObject object = McpKeywordToolSearchIndexConfig.NAME.equals(options.type)
            ? keyword.adaptToJson(options)
            : null;

        for (int i = 0; object == null && i < extensions.size(); i++)
        {
            object = extensions.get(i).adaptItemToJson(options.type, options);
        }

        return object;
    }

    @Override
    public McpToolSearchIndexConfig adaptFromJson(
        JsonObject object)
    {
        String type = object.getString(TYPE_NAME, null);

        McpToolSearchIndexConfig options = McpKeywordToolSearchIndexConfig.NAME.equals(type)
            ? keyword.adaptFromJson(object)
            : null;

        for (int i = 0; options == null && i < extensions.size(); i++)
        {
            options = (McpToolSearchIndexConfig) extensions.get(i).adaptItemFromJson(type, object);
        }

        if (options == null)
        {
            throw new JsonException(String.format("Unrecognized tool search index type: %s", type));
        }

        return options;
    }
}
