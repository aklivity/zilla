/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.internal.config;


import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.grpc.internal.GrpcBinding;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class GrpcConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private final String16FW.Builder stringRW = new String16FW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
    private static final String METHOD_NAME = "method";
    private static final String METADATA_NAME = "metadata";

    @Override
    public String type()
    {
        return GrpcBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        GrpcConditionConfig grpcCondition = (GrpcConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (grpcCondition.metadata != null &&
            !grpcCondition.metadata.isEmpty())
        {
            JsonObjectBuilder entries = Json.createObjectBuilder();
            grpcCondition.metadata.forEach((k, v) ->
            {
                String key = k.asString();
                if (!key.contains("-bin"))
                {
                    entries.add(key, stringRW.set(v, 0, v.capacity()).build().asString());
                }
            });

            object.add(METADATA_NAME, entries);
        }

        if (grpcCondition.method != null)
        {
            object.add(METHOD_NAME, grpcCondition.method);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String method = object.containsKey(METHOD_NAME)
            ? object.getString(METHOD_NAME)
            : null;

        JsonObject metadata = object.containsKey(METADATA_NAME)
            ? object.getJsonObject(METADATA_NAME)
            : null;

        final Map<String8FW, DirectBuffer> newMetadata = new LinkedHashMap<>();

        if (metadata != null)
        {
            metadata.forEach((k, v) ->
            {
                String8FW key = new String8FW(k);
                String16FW value = new String16FW(JsonString.class.cast(v).getString());
                newMetadata.put(key, value.value());
            });
        }

        return new GrpcConditionConfig(method, newMetadata);
    }

}
