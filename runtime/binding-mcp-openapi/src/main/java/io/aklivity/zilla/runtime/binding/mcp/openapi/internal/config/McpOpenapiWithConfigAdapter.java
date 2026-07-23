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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.WithConfig;
import io.aklivity.zilla.config.engine.WithConfigAdapterSpi;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.McpOpenapiBinding;

public final class McpOpenapiWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final String SPEC_NAME = "spec";
    private static final String OPERATION_NAME = "operation";
    private static final String TAG_NAME = "tag";
    private static final String PARAMS_NAME = "params";
    private static final String BODY_NAME = "body";

    @Override
    public String type()
    {
        return McpOpenapiBinding.TYPE;
    }

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        McpOpenapiWithConfig mcpOpenapiWith = (McpOpenapiWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mcpOpenapiWith.spec != null)
        {
            object.add(SPEC_NAME, mcpOpenapiWith.spec);
        }

        if (mcpOpenapiWith.operation != null)
        {
            object.add(OPERATION_NAME, mcpOpenapiWith.operation);
        }

        if (mcpOpenapiWith.tag != null)
        {
            object.add(TAG_NAME, mcpOpenapiWith.tag);
        }

        if (mcpOpenapiWith.params != null && !mcpOpenapiWith.params.isEmpty())
        {
            final JsonObjectBuilder params = Json.createObjectBuilder();
            mcpOpenapiWith.params.forEach(params::add);
            object.add(PARAMS_NAME, params);
        }

        if (mcpOpenapiWith.body != null && !mcpOpenapiWith.body.isEmpty())
        {
            final JsonObjectBuilder body = Json.createObjectBuilder();
            mcpOpenapiWith.body.forEach(body::add);
            object.add(BODY_NAME, body);
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        String spec = object.containsKey(SPEC_NAME)
            ? object.getString(SPEC_NAME)
            : null;

        String operation = object.containsKey(OPERATION_NAME)
            ? object.getString(OPERATION_NAME)
            : null;

        String tag = object.containsKey(TAG_NAME)
            ? object.getString(TAG_NAME)
            : null;

        Map<String, String> params = null;
        if (object.containsKey(PARAMS_NAME))
        {
            params = new LinkedHashMap<>();
            final JsonObject paramsObject = object.getJsonObject(PARAMS_NAME);
            for (Map.Entry<String, JsonValue> entry : paramsObject.entrySet())
            {
                params.put(entry.getKey(), paramsObject.getString(entry.getKey()));
            }
        }

        Map<String, String> body = null;
        if (object.containsKey(BODY_NAME))
        {
            body = new LinkedHashMap<>();
            final JsonObject bodyObject = object.getJsonObject(BODY_NAME);
            for (Map.Entry<String, JsonValue> entry : bodyObject.entrySet())
            {
                body.put(entry.getKey(), bodyObject.getString(entry.getKey()));
            }
        }

        return McpOpenapiWithConfig.builder()
            .spec(spec)
            .operation(operation)
            .tag(tag)
            .params(params)
            .body(body)
            .build();
    }
}
