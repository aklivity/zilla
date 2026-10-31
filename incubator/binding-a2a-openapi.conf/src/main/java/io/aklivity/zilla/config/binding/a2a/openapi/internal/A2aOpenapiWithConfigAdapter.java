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
package io.aklivity.zilla.config.binding.a2a.openapi.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiWithConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.WithConfig;

public final class A2aOpenapiWithConfigAdapter extends ConfigAdapter<WithConfig, JsonObject>
{
    private static final String SPEC_NAME = "spec";
    private static final String OPERATION_NAME = "operation";
    private static final String TAG_NAME = "tag";
    private static final String PARAMS_NAME = "params";

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        A2aOpenapiWithConfig a2aOpenapiWith = (A2aOpenapiWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (a2aOpenapiWith.spec != null)
        {
            object.add(SPEC_NAME, a2aOpenapiWith.spec);
        }

        if (a2aOpenapiWith.operation != null)
        {
            object.add(OPERATION_NAME, a2aOpenapiWith.operation);
        }

        if (a2aOpenapiWith.tag != null)
        {
            object.add(TAG_NAME, a2aOpenapiWith.tag);
        }

        if (a2aOpenapiWith.params != null && !a2aOpenapiWith.params.isEmpty())
        {
            final JsonObjectBuilder params = Json.createObjectBuilder();
            a2aOpenapiWith.params.forEach(params::add);
            object.add(PARAMS_NAME, params);
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

        return A2aOpenapiWithConfig.builder()
            .spec(spec)
            .operation(operation)
            .tag(tag)
            .params(params)
            .build();
    }
}
