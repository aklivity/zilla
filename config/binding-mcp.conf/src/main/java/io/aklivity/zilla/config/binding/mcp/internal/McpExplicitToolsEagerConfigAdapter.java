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
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;

import io.aklivity.zilla.config.binding.mcp.McpExplicitToolsEagerConfig;
import io.aklivity.zilla.config.binding.mcp.McpToolsEagerConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;

final class McpExplicitToolsEagerConfigAdapter extends ConfigAdapter<McpToolsEagerConfig, JsonObject>
{
    private static final String TYPE_NAME = "type";
    private static final String MATCH_NAME = "match";

    @Override
    public JsonObject adaptToJson(
        McpToolsEagerConfig options)
    {
        McpExplicitToolsEagerConfig explicit = (McpExplicitToolsEagerConfig) options;

        JsonArrayBuilder match = Json.createArrayBuilder();
        explicit.match.forEach(match::add);

        return Json.createObjectBuilder()
            .add(TYPE_NAME, McpExplicitToolsEagerConfig.NAME)
            .add(MATCH_NAME, match)
            .build();
    }

    @Override
    public McpToolsEagerConfig adaptFromJson(
        JsonObject object)
    {
        List<String> match = object.getJsonArray(MATCH_NAME).getValuesAs(JsonString.class).stream()
            .map(JsonString::getString)
            .collect(Collectors.toList());

        return McpExplicitToolsEagerConfig.builder()
            .match(match)
            .build();
    }
}
