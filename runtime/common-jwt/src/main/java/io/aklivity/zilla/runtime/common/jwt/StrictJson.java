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
package io.aklivity.zilla.runtime.common.jwt;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;

import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

final class StrictJson
{
    static final JsonProvider PROVIDER = JsonProvider.provider();

    static JsonObject readObject(
        String json) throws JwtException
    {
        if (json == null)
        {
            throw new JwtException("Missing JSON object");
        }

        return readObject(json.getBytes(UTF_8));
    }

    static JsonObject readObject(
        byte[] json) throws JwtException
    {
        JsonObject object;
        try (JsonParser parser = PROVIDER.createParser(new ByteArrayInputStream(json)))
        {
            if (!parser.hasNext() || parser.next() != Event.START_OBJECT)
            {
                throw new JwtException("Invalid JSON object");
            }

            object = readObject(parser);

            if (parser.hasNext())
            {
                throw new JwtException("Unexpected content after JSON object");
            }
        }
        catch (JsonException | IllegalStateException ex)
        {
            throw new JwtException("Invalid JSON object", ex);
        }

        return object;
    }

    private static JsonObject readObject(
        JsonParser parser) throws JwtException
    {
        JsonObjectBuilder builder = PROVIDER.createObjectBuilder();

        int names = 0;
        String name = null;
        Event event;
        while ((event = parser.next()) != Event.END_OBJECT)
        {
            if (event == Event.KEY_NAME)
            {
                name = parser.getString();
                names++;
            }
            else
            {
                addValue(parser, builder, name, event);
            }
        }

        JsonObject object = builder.build();
        if (object.size() != names)
        {
            throw new JwtException("Duplicate name in JSON object");
        }

        return object;
    }

    private static JsonArray readArray(
        JsonParser parser) throws JwtException
    {
        JsonArrayBuilder builder = PROVIDER.createArrayBuilder();

        Event event;
        while ((event = parser.next()) != Event.END_ARRAY)
        {
            addValue(parser, builder, event);
        }

        return builder.build();
    }

    private static void addValue(
        JsonParser parser,
        JsonObjectBuilder builder,
        String name,
        Event event) throws JwtException
    {
        switch (event)
        {
        case START_OBJECT -> builder.add(name, readObject(parser));
        case START_ARRAY -> builder.add(name, readArray(parser));
        case VALUE_STRING -> builder.add(name, parser.getString());
        case VALUE_NUMBER -> builder.add(name, parser.getBigDecimal());
        case VALUE_TRUE -> builder.add(name, JsonValue.TRUE);
        case VALUE_FALSE -> builder.add(name, JsonValue.FALSE);
        case VALUE_NULL -> builder.add(name, JsonValue.NULL);
        default -> throw new JwtException("Unexpected JSON event: " + event);
        }
    }

    private static void addValue(
        JsonParser parser,
        JsonArrayBuilder builder,
        Event event) throws JwtException
    {
        switch (event)
        {
        case START_OBJECT -> builder.add(readObject(parser));
        case START_ARRAY -> builder.add(readArray(parser));
        case VALUE_STRING -> builder.add(parser.getString());
        case VALUE_NUMBER -> builder.add(parser.getBigDecimal());
        case VALUE_TRUE -> builder.add(JsonValue.TRUE);
        case VALUE_FALSE -> builder.add(JsonValue.FALSE);
        case VALUE_NULL -> builder.add(JsonValue.NULL);
        default -> throw new JwtException("Unexpected JSON event: " + event);
        }
    }

    private StrictJson()
    {
    }
}
