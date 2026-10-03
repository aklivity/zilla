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

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
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

        JsonObject object;
        try
        {
            requireUniqueNames(json);

            try (JsonReader reader = PROVIDER.createReader(new StringReader(json)))
            {
                object = reader.readObject();
            }
        }
        catch (JsonException | IllegalStateException ex)
        {
            throw new JwtException("Invalid JSON object", ex);
        }

        return object;
    }

    private static void requireUniqueNames(
        String json) throws JwtException
    {
        Deque<Set<String>> scopes = new ArrayDeque<>();

        try (JsonParser parser = PROVIDER.createParser(new StringReader(json)))
        {
            while (parser.hasNext())
            {
                Event event = parser.next();

                if (event == Event.START_OBJECT)
                {
                    scopes.push(new HashSet<>());
                }
                else if (event == Event.END_OBJECT)
                {
                    scopes.pop();
                }
                else if (event == Event.KEY_NAME && !scopes.peek().add(parser.getString()))
                {
                    throw new JwtException("Duplicate name in JSON object: " + parser.getString());
                }
            }
        }
    }

    private StrictJson()
    {
    }
}
