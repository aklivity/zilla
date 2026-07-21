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
package io.aklivity.zilla.runtime.common.json.internal.json;

import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;

public final class JsonReaderImpl implements JsonReader
{
    private final JsonParser parser;
    private boolean read;

    public JsonReaderImpl(
        JsonParser parser)
    {
        this.parser = parser;
    }

    @Override
    public JsonStructure read()
    {
        JsonValue value = readValue();
        if (!(value instanceof JsonStructure))
        {
            throw new JsonException("JSON document is not a JSON structure");
        }
        return (JsonStructure) value;
    }

    @Override
    public JsonObject readObject()
    {
        JsonValue value = readValue();
        if (!(value instanceof JsonObject))
        {
            throw new JsonException("JSON document is not a JSON object");
        }
        return (JsonObject) value;
    }

    @Override
    public JsonArray readArray()
    {
        JsonValue value = readValue();
        if (!(value instanceof JsonArray))
        {
            throw new JsonException("JSON document is not a JSON array");
        }
        return (JsonArray) value;
    }

    @Override
    public JsonValue readValue()
    {
        if (read)
        {
            throw new JsonException("JSON document has already been read");
        }
        read = true;
        if (!parser.hasNext())
        {
            throw new JsonException("JSON document is empty");
        }
        JsonValue value = readValue(parser.next());
        if (parser.hasNext())
        {
            throw new JsonException("Unexpected content after JSON document");
        }
        return value;
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
        case VALUE_STRING -> JsonValues.string(parser.getString());
        case VALUE_NUMBER -> JsonValues.numberLiteral(parser.getString());
        case VALUE_TRUE -> JsonValue.TRUE;
        case VALUE_FALSE -> JsonValue.FALSE;
        case VALUE_NULL -> JsonValue.NULL;
        default -> throw new JsonException("Unexpected JSON parser event: " + event);
        };
    }

    private JsonObject readObjectValue()
    {
        JsonObjectBuilder object = JsonValues.objectBuilder();
        while (parser.hasNext())
        {
            JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.END_OBJECT)
            {
                return object.build();
            }
            if (event != JsonParser.Event.KEY_NAME)
            {
                throw new JsonException("Expected JSON object key");
            }
            String key = parser.getString();
            if (!parser.hasNext())
            {
                throw new JsonException("Expected JSON object value");
            }
            object.add(key, readValue(parser.next()));
        }
        throw new JsonException("Unterminated JSON object");
    }

    private JsonArray readArrayValue()
    {
        JsonArrayBuilder array = JsonValues.arrayBuilder();
        while (parser.hasNext())
        {
            JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.END_ARRAY)
            {
                return array.build();
            }
            array.add(readValue(event));
        }
        throw new JsonException("Unterminated JSON array");
    }
}
