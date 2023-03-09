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


import java.util.Base64;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.grpc.internal.GrpcBinding;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class GrpcConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String BASE64_NAME = "base64";
    private static final String METHOD_NAME = "method";
    private static final String METADATA_NAME = "metadata";
    private static final int ASCII_SPACE = 0x20;
    private static final int ASCII_TILDE = 0x7e;
    private final Base64.Decoder decoder64 = Base64.getUrlDecoder();
    private final Base64.Encoder encoder64 = Base64.getUrlEncoder();

    @Override
    public String type()
    {
        return GrpcBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig adaptable)
    {
        GrpcConditionConfig condition = (GrpcConditionConfig) adaptable;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (condition.metadata != null &&
            !condition.metadata.isEmpty())
        {
            JsonObjectBuilder entries = Json.createObjectBuilder();
            condition.metadata.forEach((k, v) ->
            {
                String key = k.asString();
                String value = v.textValue != null ? v.textValue.asString() : v.base64Value.asString();
                entries.add(key, value);
            });

            object.add(METADATA_NAME, entries);
        }

        if (condition.method != null)
        {
            object.add(METHOD_NAME, condition.method);
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

        final Map<String8FW, GrpcMetadataValue> newMetadata = new Object2ObjectHashMap<>();

        if (metadata != null)
        {
            metadata.forEach((k, v) ->
            {
                final String8FW key = new String8FW(k);
                final JsonObject value = getJsonObject(v);

                String textValue = null;
                String base64Value = null;

                if (value != null && value.containsKey(BASE64_NAME))
                {
                    base64Value = value.getString(BASE64_NAME);
                }
                else
                {
                    textValue = JsonString.class.cast(v).getString();
                    base64Value = encoder64.encodeToString(textValue.getBytes());
                }

                GrpcMetadataValue metadataValue =  new GrpcMetadataValue(new String16FW(textValue),
                    new String16FW(base64Value));
                newMetadata.put(key, metadataValue);
            });
        }

        return new GrpcConditionConfig(method, newMetadata);
    }

    private static JsonObject getJsonObject(JsonValue v)
    {
        JsonObject value = null;
        try
        {
            value = JsonObject.class.cast(v);
        }
        catch (ClassCastException ex)
        {
        }
        return value;
    }

}
