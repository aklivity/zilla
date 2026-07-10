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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import java.util.LinkedList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public class OpenapiConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String SPEC_NAME = "spec";
    private static final String OPERATION_NAME = "operation";
    private static final String TAG_NAME = "tag";
    private static final String SERVERS_NAME = "servers";
    private static final String SERVER_URL_NAME = "url";

    @Override
    public String type()
    {
        return OpenapiBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        OpenapiConditionConfig openapiCondition = (OpenapiConditionConfig) condition;
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (openapiCondition.spec != null)
        {
            object.add(SPEC_NAME, openapiCondition.spec);
        }

        if (openapiCondition.operation != null)
        {
            object.add(OPERATION_NAME, openapiCondition.operation);
        }

        if (openapiCondition.tag != null)
        {
            object.add(TAG_NAME, openapiCondition.tag);
        }

        if (openapiCondition.servers != null && !openapiCondition.servers.isEmpty())
        {
            JsonArrayBuilder servers = Json.createArrayBuilder();
            openapiCondition.servers.forEach(server ->
            {
                JsonObjectBuilder serverJson = Json.createObjectBuilder();
                serverJson.add(SERVER_URL_NAME, server.url);
                servers.add(serverJson);
            });
            object.add(SERVERS_NAME, servers);
        }
        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
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

        List<OpenapiConditionServerConfig> servers = null;
        if (object.containsKey(SERVERS_NAME))
        {
            servers = new LinkedList<>();
            for (JsonValue serverValue : object.getJsonArray(SERVERS_NAME))
            {
                JsonObject serverJson = serverValue.asJsonObject();
                servers.add(new OpenapiConditionServerConfig(serverJson.getString(SERVER_URL_NAME)));
            }
        }

        return new OpenapiConditionConfig(spec, operation, tag, servers);
    }
}
