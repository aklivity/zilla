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
package io.aklivity.zilla.runtime.common.openapi.model;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

// materializes a JsonObject using only the primitive JsonParser event API (next/getString/
// getBigDecimal); the convenience JsonParser.getObject()/getValue(), and likewise a
// JsonbAdapter<T, JsonObject>, lose position tracking and corrupt the parser in this
// Yasson + common-yaml YamlJsonParser combination - see
// https://github.com/aklivity/zilla/issues/1997, tracked separately from this module
final class OpenapiJsonValues
{
    private OpenapiJsonValues()
    {
    }

    static JsonObject readObject(
        JsonParser parser)
    {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        while (parser.hasNext())
        {
            Event event = parser.next();
            if (event == Event.KEY_NAME)
            {
                String name = parser.getString();
                builder.add(name, readValue(parser, parser.next()));
            }
            else if (event == Event.END_OBJECT)
            {
                break;
            }
        }

        return builder.build();
    }

    private static JsonValue readValue(
        JsonParser parser,
        Event event)
    {
        JsonValue value;

        switch (event)
        {
        case START_OBJECT:
            value = readObject(parser);
            break;
        case START_ARRAY:
            value = readArray(parser);
            break;
        case VALUE_STRING:
            value = Json.createValue(parser.getString());
            break;
        case VALUE_NUMBER:
            value = Json.createValue(parser.getBigDecimal());
            break;
        case VALUE_TRUE:
            value = JsonValue.TRUE;
            break;
        case VALUE_FALSE:
            value = JsonValue.FALSE;
            break;
        case VALUE_NULL:
            value = JsonValue.NULL;
            break;
        default:
            throw new IllegalStateException("Unexpected event: " + event);
        }

        return value;
    }

    private static JsonValue readArray(
        JsonParser parser)
    {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        Event event;
        while ((event = parser.next()) != Event.END_ARRAY)
        {
            builder.add(readValue(parser, event));
        }

        return builder.build();
    }
}
