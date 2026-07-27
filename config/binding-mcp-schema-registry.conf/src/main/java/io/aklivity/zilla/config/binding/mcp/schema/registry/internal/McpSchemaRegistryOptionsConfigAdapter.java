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
package io.aklivity.zilla.config.binding.mcp.schema.registry.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.mcp.schema.registry.McpSchemaRegistryOptionsConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class McpSchemaRegistryOptionsConfigAdapter implements JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String SERVER_NAME = "server";

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        McpSchemaRegistryOptionsConfig schemaRegistryOptions = (McpSchemaRegistryOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();
        object.add(SERVER_NAME, schemaRegistryOptions.server);
        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        String server = object.getString(SERVER_NAME);

        return McpSchemaRegistryOptionsConfig.builder()
            .server(server)
            .build();
    }
}
