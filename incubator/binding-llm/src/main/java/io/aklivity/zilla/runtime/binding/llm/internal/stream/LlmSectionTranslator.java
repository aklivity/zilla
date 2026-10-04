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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

final class LlmSectionTranslator
{
    static String toAnthropic(
        String type,
        String payload)
    {
        String translated = null;

        final JsonObject source = parse(payload);

        if (source != null)
        {
            translated = switch (type)
            {
            case "tool-call" -> openaiToolCallToAnthropic(source);
            case "tool-definition" -> openaiFunctionToAnthropic(source);
            default -> null;
            };
        }

        return translated;
    }

    static String toOpenai(
        String type,
        String payload)
    {
        String translated = null;

        final JsonObject source = parse(payload);

        if (source != null)
        {
            translated = switch (type)
            {
            case "tool-call" -> anthropicToolUseToOpenai(source);
            case "tool-definition" -> anthropicToolToOpenai(source);
            default -> null;
            };
        }

        return translated;
    }

    private static String openaiToolCallToAnthropic(
        JsonObject call)
    {
        final JsonObject function = call.getJsonObject("function");
        final String id = string(call, "id");
        final String name = function != null ? string(function, "name") : null;
        final String arguments = function != null ? string(function, "arguments") : null;

        String translated = null;

        if (id != null && name != null)
        {
            final JsonObject input = arguments == null || arguments.isEmpty()
                ? JsonValue.EMPTY_JSON_OBJECT
                : parse(arguments);

            if (input != null)
            {
                translated = Json.createObjectBuilder()
                    .add("type", "tool_use")
                    .add("id", id)
                    .add("name", name)
                    .add("input", input)
                    .build()
                    .toString();
            }
        }

        return translated;
    }

    private static String openaiFunctionToAnthropic(
        JsonObject tool)
    {
        final JsonObject function = tool.getJsonObject("function");
        final String name = function != null ? string(function, "name") : null;

        String translated = null;

        if (name != null)
        {
            final String description = string(function, "description");
            final JsonObject parameters = function.getJsonObject("parameters");

            final JsonObjectBuilder builder = Json.createObjectBuilder().add("name", name);

            if (description != null)
            {
                builder.add("description", description);
            }

            builder.add("input_schema", parameters != null
                ? parameters
                : Json.createObjectBuilder().add("type", "object").build());

            translated = builder.build().toString();
        }

        return translated;
    }

    private static String anthropicToolUseToOpenai(
        JsonObject use)
    {
        final String id = string(use, "id");
        final String name = string(use, "name");
        final JsonObject input = use.getJsonObject("input");

        return id != null && name != null
            ? Json.createObjectBuilder()
                .add("id", id)
                .add("type", "function")
                .add("function", Json.createObjectBuilder()
                    .add("name", name)
                    .add("arguments", input != null ? input.toString() : "{}"))
                .build()
                .toString()
            : null;
    }

    private static String anthropicToolToOpenai(
        JsonObject tool)
    {
        final String name = string(tool, "name");

        String translated = null;

        if (name != null && string(tool, "type") == null)
        {
            final String description = string(tool, "description");
            final JsonObject schema = tool.getJsonObject("input_schema");

            final JsonObjectBuilder function = Json.createObjectBuilder().add("name", name);

            if (description != null)
            {
                function.add("description", description);
            }

            if (schema != null)
            {
                function.add("parameters", schema);
            }

            translated = Json.createObjectBuilder()
                .add("type", "function")
                .add("function", function)
                .build()
                .toString();
        }

        return translated;
    }

    private static String string(
        JsonObject object,
        String name)
    {
        final JsonValue value = object.get(name);

        return value instanceof JsonString text ? text.getString() : null;
    }

    private static JsonObject parse(
        String payload)
    {
        JsonObject object = null;

        try (JsonReader reader = Json.createReader(new StringReader(payload)))
        {
            object = reader.readObject();
        }
        catch (JsonException | ClassCastException ex)
        {
            object = null;
        }

        return object;
    }

    private LlmSectionTranslator()
    {
    }
}
