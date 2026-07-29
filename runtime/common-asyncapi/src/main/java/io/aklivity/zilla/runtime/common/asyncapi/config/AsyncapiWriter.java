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
package io.aklivity.zilla.runtime.common.asyncapi.config;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import io.aklivity.zilla.runtime.common.asyncapi.model.Asyncapi;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class AsyncapiWriter
{
    private static final String EXTENSIONS_FIELD = "extensions";

    private final Jsonb jsonb;

    public AsyncapiWriter()
    {
        this.jsonb = JsonbBuilder.newBuilder()
            .withConfig(new JsonbConfig().withNullValues(false))
            .build();
    }

    public String write(
        Asyncapi asyncapi)
    {
        String json = jsonb.toJson(asyncapi);

        JsonObject object;
        try (JsonReader reader = YamlJson.createReader(new StringReader(json)))
        {
            object = reader.readObject();
        }

        JsonObject flattened = (JsonObject) flattenExtensions(object);

        StringWriter writer = new StringWriter();
        try (JsonWriter yaml = YamlJson.createWriter(writer))
        {
            yaml.writeObject(flattened);
        }

        return writer.toString();
    }

    private static JsonValue flattenExtensions(
        JsonValue value)
    {
        JsonValue result = value;

        if (value instanceof JsonObject object)
        {
            JsonObjectBuilder builder = Json.createObjectBuilder();

            for (Map.Entry<String, JsonValue> entry : object.entrySet())
            {
                if (EXTENSIONS_FIELD.equals(entry.getKey()) && entry.getValue() instanceof JsonObject extensions)
                {
                    for (Map.Entry<String, JsonValue> extension : extensions.entrySet())
                    {
                        builder.add(extension.getKey(), flattenExtensions(extension.getValue()));
                    }
                }
                else
                {
                    builder.add(entry.getKey(), flattenExtensions(entry.getValue()));
                }
            }

            result = builder.build();
        }
        else if (value instanceof JsonArray array)
        {
            JsonArrayBuilder builder = Json.createArrayBuilder();

            for (JsonValue element : array)
            {
                builder.add(flattenExtensions(element));
            }

            result = builder.build();
        }

        return result;
    }
}
