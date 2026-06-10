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
package io.aklivity.zilla.runtime.common.yaml.internal.json;

import java.math.BigDecimal;

import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;

public final class YamlJsonReader implements JsonReader
{
    private final JsonParser parser;
    private boolean read;

    public YamlJsonReader(
        JsonParser parser)
    {
        this.parser = parser;
    }

    @Override
    public JsonStructure read()
    {
        JsonValue value = readValue();
        if (value instanceof JsonStructure structure)
        {
            return structure;
        }
        throw new JsonException("YAML document is not a JSON structure");
    }

    @Override
    public JsonObject readObject()
    {
        JsonValue value = readValue();
        if (value instanceof JsonObject object)
        {
            return object;
        }
        throw new JsonException("YAML document is not a JSON object");
    }

    @Override
    public JsonArray readArray()
    {
        JsonValue value = readValue();
        if (value instanceof JsonArray array)
        {
            return array;
        }
        throw new JsonException("YAML document is not a JSON array");
    }

    @Override
    public JsonValue readValue()
    {
        if (read)
        {
            throw new JsonException("YAML document has already been read");
        }
        read = true;
        if (!parser.hasNext())
        {
            throw new JsonException("YAML document is empty");
        }
        return readValue(parser.next());
    }

    @Override
    public void close()
    {
        parser.close();
    }

    private JsonValue readValue(
        JsonParser.Event event)
    {
        return switch (event)
        {
        case START_OBJECT -> readObjectValue();
        case START_ARRAY -> readArrayValue();
        case VALUE_STRING -> YamlJsonValues.string(parser.getString());
        case VALUE_NUMBER -> YamlJsonValues.number(new BigDecimal(parser.getString()));
        case VALUE_TRUE -> JsonValue.TRUE;
        case VALUE_FALSE -> JsonValue.FALSE;
        case VALUE_NULL -> JsonValue.NULL;
        default -> throw new JsonException("Unexpected YAML parser event: " + event);
        };
    }

    private JsonObject readObjectValue()
    {
        JsonObjectBuilder object = YamlJsonValues.objectBuilder();
        while (parser.hasNext())
        {
            JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.END_OBJECT)
            {
                return object.build();
            }
            if (event != JsonParser.Event.KEY_NAME)
            {
                throw new JsonException("Expected YAML object key");
            }
            String key = parser.getString();
            if (!parser.hasNext())
            {
                throw new JsonException("Expected YAML object value");
            }
            object.add(key, readValue(parser.next()));
        }
        throw new JsonException("Unterminated YAML object");
    }

    private JsonArray readArrayValue()
    {
        JsonArrayBuilder array = YamlJsonValues.arrayBuilder();
        while (parser.hasNext())
        {
            JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.END_ARRAY)
            {
                return array.build();
            }
            array.add(readValue(event));
        }
        throw new JsonException("Unterminated YAML array");
    }

}
