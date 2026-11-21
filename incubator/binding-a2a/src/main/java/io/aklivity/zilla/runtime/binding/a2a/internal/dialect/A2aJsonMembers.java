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
package io.aklivity.zilla.runtime.binding.a2a.internal.dialect;

import java.util.function.UnaryOperator;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

final class A2aJsonMembers
{
    static final String SCHEME_BEARER = "bearer";

    static String string(
        JsonObject object,
        String name)
    {
        final JsonValue value = object.get(name);
        return value instanceof JsonString string ? string.getString() : null;
    }

    static int integer(
        JsonObject object,
        String name,
        int defaultValue)
    {
        final JsonValue value = object.get(name);
        return value != null && value.getValueType() == JsonValue.ValueType.NUMBER
            ? object.getInt(name)
            : defaultValue;
    }

    static JsonObject object(
        JsonObject object,
        String name)
    {
        final JsonValue value = object.get(name);
        return value instanceof JsonObject member ? member : null;
    }

    static JsonArray array(
        JsonObject object,
        String name)
    {
        final JsonValue value = object.get(name);
        return value instanceof JsonArray member ? member : null;
    }

    static JsonObjectBuilder copy(
        JsonObject object,
        String... excluded)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        object.forEach((name, value) ->
        {
            if (!contains(excluded, name))
            {
                builder.add(name, value);
            }
        });
        return builder;
    }

    static JsonArray map(
        JsonArray array,
        UnaryOperator<JsonObject> mapper)
    {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        array.forEach(item -> builder.add(item instanceof JsonObject object ? mapper.apply(object) : item));
        return builder.build();
    }

    private static boolean contains(
        String[] names,
        String name)
    {
        boolean found = false;
        for (String candidate : names)
        {
            found |= candidate.equals(name);
        }
        return found;
    }

    private A2aJsonMembers()
    {
    }
}
