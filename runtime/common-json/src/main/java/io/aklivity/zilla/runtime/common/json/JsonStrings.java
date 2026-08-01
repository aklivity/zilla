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
package io.aklivity.zilla.runtime.common.json;

import static java.util.stream.Collectors.toList;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Reads and writes a {@code string | string[]} shorthand-permissive JSON property as a
 * {@code List<String>}, for config adapters whose field accepts either a bare string or an
 * array of strings.
 */
public final class JsonStrings
{
    private JsonStrings()
    {
    }

    /**
     * Returns {@code null} when {@code name} is absent from {@code object}, a singleton list
     * when its value is a bare string, or the mapped list of elements when its value is an array.
     */
    public static List<String> asStringOrArray(
        JsonObject object,
        String name)
    {
        List<String> result = null;

        if (object.containsKey(name))
        {
            JsonValue value = object.get(name);
            result = value.getValueType() == JsonValue.ValueType.ARRAY
                ? value.asJsonArray().stream()
                    .map(JsonString.class::cast)
                    .map(JsonString::getString)
                    .collect(toList())
                : List.of(((JsonString) value).getString());
        }

        return result;
    }

    /**
     * Adds nothing when {@code values} is {@code null}; otherwise adds {@code name} as a bare
     * string when {@code values} has exactly one element, or as an array otherwise (including
     * an empty array, distinct from {@code null}, meaning "admit nothing" to an allow-set caller).
     */
    public static void addStringOrArray(
        JsonObjectBuilder builder,
        String name,
        List<String> values)
    {
        if (values != null)
        {
            if (values.size() == 1)
            {
                builder.add(name, values.get(0));
            }
            else
            {
                JsonArrayBuilder array = Json.createArrayBuilder();
                values.forEach(array::add);
                builder.add(name, array);
            }
        }
    }
}
