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

import io.aklivity.zilla.config.binding.mcp.McpAllToolsEagerConfig;
import io.aklivity.zilla.config.binding.mcp.McpExplicitToolsEagerConfig;
import io.aklivity.zilla.config.binding.mcp.McpNoneToolsEagerConfig;
import io.aklivity.zilla.config.binding.mcp.McpToolsEagerConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.ConfigExtAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class McpToolsEagerConfigAdapter extends ConfigAdapter<McpToolsEagerConfig, JsonObject>
{
    private static final String TYPE_NAME = "type";

    private final McpNoneToolsEagerConfigAdapter none = new McpNoneToolsEagerConfigAdapter();
    private final McpAllToolsEagerConfigAdapter all = new McpAllToolsEagerConfigAdapter();
    private final McpExplicitToolsEagerConfigAdapter explicit = new McpExplicitToolsEagerConfigAdapter();
    private final List<ConfigExtAdapter<OptionsConfig>> extensions;

    public McpToolsEagerConfigAdapter(
        List<ConfigExtAdapter<OptionsConfig>> extensions)
    {
        this.extensions = extensions;
    }

    @Override
    public JsonObject adaptToJson(
        McpToolsEagerConfig options)
    {
        JsonObject object = switch (options.type)
        {
        case McpNoneToolsEagerConfig.NAME -> none.adaptToJson(options);
        case McpAllToolsEagerConfig.NAME -> all.adaptToJson(options);
        case McpExplicitToolsEagerConfig.NAME -> explicit.adaptToJson(options);
        default -> null;
        };

        for (int i = 0; object == null && i < extensions.size(); i++)
        {
            object = extensions.get(i).adaptItemToJson(options.type, options);
        }

        return object;
    }

    @Override
    public McpToolsEagerConfig adaptFromJson(
        JsonObject object)
    {
        String type = object.getString(TYPE_NAME, null);

        McpToolsEagerConfig options = switch (type == null ? "" : type)
        {
        case McpNoneToolsEagerConfig.NAME -> none.adaptFromJson(object);
        case McpAllToolsEagerConfig.NAME -> all.adaptFromJson(object);
        case McpExplicitToolsEagerConfig.NAME -> explicit.adaptFromJson(object);
        default -> null;
        };

        for (int i = 0; options == null && i < extensions.size(); i++)
        {
            options = (McpToolsEagerConfig) extensions.get(i).adaptItemFromJson(type, object);
        }

        if (options == null)
        {
            throw new JsonException(String.format("Unrecognized tool eager type: %s", type));
        }

        return options;
    }
}
