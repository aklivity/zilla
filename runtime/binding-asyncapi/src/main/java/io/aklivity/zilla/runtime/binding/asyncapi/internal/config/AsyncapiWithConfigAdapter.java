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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiBinding;
import io.aklivity.zilla.runtime.engine.config.WithConfig;
import io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi;

public class AsyncapiWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final String API_ID_NAME = "api-id";
    private static final String OPERATION_ID_NAME = "operation-id";

    @Override
    public String type()
    {
        return AsyncapiBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        AsyncapiWithConfig config = (AsyncapiWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (config.apiId != null)
        {
            object.add(API_ID_NAME, config.apiId);
        }

        if (config.operationId != null)
        {
            object.add(OPERATION_ID_NAME, config.operationId);
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        String apiId = object.containsKey(API_ID_NAME)
            ? object.getString(API_ID_NAME)
            : null;

        String operationId = object.containsKey(OPERATION_ID_NAME)
            ? object.getString(OPERATION_ID_NAME)
            : null;

        return new AsyncapiWithConfig(apiId, operationId);
    }
}
