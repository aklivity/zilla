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
package io.aklivity.zilla.runtime.binding.llm.internal.mapper;

import java.io.StringReader;
import java.io.StringWriter;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonWriter;

final class LlmEventMapperJson
{
    private LlmEventMapperJson()
    {
    }

    static JsonObject readObject(
        String data)
    {
        try (JsonReader reader = Json.createReader(new StringReader(data)))
        {
            return reader.readObject();
        }
    }

    static String getString(
        JsonObject object,
        String name,
        String fallback)
    {
        return object.containsKey(name) && !object.isNull(name) ? object.getString(name) : fallback;
    }

    static String compact(
        JsonStructure value)
    {
        StringWriter writer = new StringWriter();
        try (JsonWriter json = Json.createWriter(writer))
        {
            json.write(value);
        }
        return writer.toString();
    }

    static String orDefault(
        String value,
        String fallback)
    {
        return value != null ? value : fallback;
    }
}
