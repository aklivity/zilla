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
package io.aklivity.zilla.runtime.binding.llm.dialect;

import java.io.StringReader;
import java.io.StringWriter;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonWriter;

// Small jakarta.json DOM helpers shared by each dialect's decodeMessage/encodeMessage (the non-streaming,
// whole-document conversion) -- deliberately DOM-based, not event-streamed, since these handle one fully
// buffered document with no framing/streaming concern.
final class LlmDialectJson
{
    private LlmDialectJson()
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
