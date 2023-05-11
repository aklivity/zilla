/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.guard.jwt.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

public final class JwtKeySetConfigAdapter implements JsonbAdapter<JwtKeySetConfig, JsonObject>
{
    private static final String KEYS_NAME = "keys";

    private final JwtKeyConfigAdapter keyAdapter = new JwtKeyConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        JwtKeySetConfig jwtKeySetConfig)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        JsonArrayBuilder keysArray = Json.createArrayBuilder();
        for (JwtKeyConfig key : jwtKeySetConfig.keys)
        {
            keysArray.add(keyAdapter.adaptToJson(key));
        }
        object.add(KEYS_NAME, keysArray);
        return object.build();
    }

    @Override
    public JwtKeySetConfig adaptFromJson(
        JsonObject keysObject)
    {
        List<JwtKeyConfig> keysConfig = keysObject
                .getJsonArray(KEYS_NAME)
                .stream()
                .map(JsonValue::asJsonObject)
                .map(keyAdapter::adaptFromJson)
                .collect(toList());
        return new JwtKeySetConfig(keysConfig);
    }
}
