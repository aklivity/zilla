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
package io.aklivity.zilla.config.binding.echo.internal;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.echo.EchoOptionsConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class EchoOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String EMBEDDING_NAME = "embedding";
    private static final String REJECT_NAME = "reject";
    private static final String THRESHOLD_NAME = "threshold";

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        EchoOptionsConfig echoOptions = (EchoOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (echoOptions.embedding != null)
        {
            object.add(EMBEDDING_NAME, echoOptions.embedding);
        }

        if (echoOptions.reject != null)
        {
            JsonArrayBuilder reject = Json.createArrayBuilder();
            echoOptions.reject.forEach(reject::add);
            object.add(REJECT_NAME, reject);
        }

        if (echoOptions.embedding != null)
        {
            object.add(THRESHOLD_NAME, echoOptions.threshold);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        String embedding = object.containsKey(EMBEDDING_NAME)
            ? object.getString(EMBEDDING_NAME)
            : null;

        List<String> reject = object.containsKey(REJECT_NAME)
            ? asListString(object.getJsonArray(REJECT_NAME))
            : null;

        double threshold = object.containsKey(THRESHOLD_NAME)
            ? object.getJsonNumber(THRESHOLD_NAME).doubleValue()
            : 0.0;

        return EchoOptionsConfig.builder()
            .embedding(embedding)
            .reject(reject)
            .threshold(threshold)
            .build();
    }

    private static List<String> asListString(
        JsonArray array)
    {
        return array.stream()
            .map(EchoOptionsConfigAdapter::asString)
            .toList();
    }

    private static String asString(
        JsonValue value)
    {
        return ((JsonString) value).getString();
    }
}
