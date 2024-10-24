/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.config;

import java.util.LinkedList;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.agrona.collections.MutableInteger;

public final class EngineConfigAnnotator
{
    private final LinkedList<String> schemaKeys;
    private final LinkedList<Integer> schemaIndexes;

    public EngineConfigAnnotator()
    {
        this.schemaKeys = new LinkedList<>();
        this.schemaIndexes = new LinkedList<>();
    }

    public JsonObject annotate(
        JsonObject jsonObject)
    {
        schemaKeys.clear();
        schemaIndexes.clear();

        return (JsonObject) annotateJsonObject(jsonObject);
    }

    private JsonValue annotateJsonObject(
        JsonObject jsonObject)
    {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        jsonObject.forEach((key, value) ->
        {
            schemaKeys.push(key);

            parse:
            if ("expression".equals(key))
            {
                builder.add(key, value);
            }
            else if (value.getValueType() == JsonValue.ValueType.OBJECT)
            {
                builder.add(key, annotateJsonObject(value.asJsonObject()));
            }
            else if (value.getValueType() == JsonValue.ValueType.ARRAY)
            {
                if (jsonObject.containsKey("type") && key.equals("enum"))
                {
                    break parse;
                }
                builder.add(key, annotateJsonArray(value.asJsonArray()));
            }
            else if (key.equals("type") && isPrimitiveType(value))
            {
                builder.add("anyOf", createAnyOfTypes(jsonObject));
            }
            else if (jsonObject.containsKey("type") &&
                isPrimitiveType(jsonObject.get("type")))
            {
                if (key.equals("title") || key.equals("description") || key.equals("default"))
                {
                    builder.add(key, value);
                }
            }
            else
            {
                builder.add(key, value);
            }

            schemaKeys.pop();
        });

        return builder.build();
    }

    private JsonValue annotateJsonArray(
        JsonArray jsonArray)
    {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();

        MutableInteger index = new MutableInteger();

        jsonArray.forEach(item ->
        {
            schemaIndexes.push(index.value++);
            if (item.getValueType() == JsonValue.ValueType.OBJECT)
            {
                arrayBuilder.add(annotateJsonObject(item.asJsonObject()));
            }
            else
            {
                arrayBuilder.add(item);
            }
            schemaIndexes.pop();
        });

        return arrayBuilder.build();
    }

    private boolean isPrimitiveType(
        JsonValue type)
    {
        String typeText = type.toString().replaceAll("\"", "");
        return "string".equals(typeText) ||
                    "integer".equals(typeText) ||
                    "boolean".equals(typeText) ||
                    "number".equals(typeText);
    }

    private JsonArray createAnyOfTypes(
        JsonObject properties)
    {
        JsonArrayBuilder anyOfArrayBuilder = Json.createArrayBuilder();
        JsonObjectBuilder objectBuilder = Json.createObjectBuilder();

        properties.forEach((key, value) ->
        {
            if (!"title".equals(key) && !"description".equals(key) && !"default".equals(key))
            {
                objectBuilder.add(key, value);
            }
        });

        anyOfArrayBuilder.add(objectBuilder);

        if (!(!schemaKeys.isEmpty() &&
            schemaKeys.size() > 1 &&
            "oneOf".equals(schemaKeys.get(1))) ||
            schemaIndexes.peek() == 0)
        {
            anyOfArrayBuilder.add(Json.createObjectBuilder()
                .add("$ref", "#/$defs/expression")
            );
        }

        return anyOfArrayBuilder.build();
    }
}
