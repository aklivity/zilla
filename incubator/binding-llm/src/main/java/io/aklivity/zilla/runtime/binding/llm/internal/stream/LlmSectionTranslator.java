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
    private static final String DATA_URL_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";

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
            case "user-image" -> openaiImageToAnthropic(source);
            case "user-document" -> openaiFileToAnthropic(source);
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
            case "user-image" -> anthropicImageToOpenai(source);
            case "user-document" -> anthropicDocumentToOpenai(source);
            case "tool-call" -> anthropicToolUseToOpenai(source);
            case "tool-definition" -> anthropicToolToOpenai(source);
            default -> null;
            };
        }

        return translated;
    }

    private static String openaiImageToAnthropic(
        JsonObject image)
    {
        final JsonObject imageUrl = image.getJsonObject("image_url");
        final String url = imageUrl != null ? string(imageUrl, "url") : null;

        return url != null
            ? Json.createObjectBuilder()
                .add("type", "image")
                .add("source", anthropicSource(url))
                .build()
                .toString()
            : null;
    }

    private static String openaiFileToAnthropic(
        JsonObject part)
    {
        final JsonObject file = part.getJsonObject("file");
        final String data = file != null ? string(file, "file_data") : null;
        final String fileId = file != null ? string(file, "file_id") : null;

        String translated = null;

        if (data != null && data.startsWith(DATA_URL_PREFIX) && data.contains(BASE64_MARKER))
        {
            translated = Json.createObjectBuilder()
                .add("type", "document")
                .add("source", anthropicSource(data))
                .build()
                .toString();
        }
        else if (fileId != null)
        {
            translated = Json.createObjectBuilder()
                .add("type", "document")
                .add("source", Json.createObjectBuilder()
                    .add("type", "file")
                    .add("file_id", fileId))
                .build()
                .toString();
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

    private static String anthropicImageToOpenai(
        JsonObject image)
    {
        final String url = openaiUrl(image.getJsonObject("source"));

        return url != null
            ? Json.createObjectBuilder()
                .add("type", "image_url")
                .add("image_url", Json.createObjectBuilder().add("url", url))
                .build()
                .toString()
            : null;
    }

    private static String anthropicDocumentToOpenai(
        JsonObject document)
    {
        final JsonObject source = document.getJsonObject("source");
        final String sourceType = source != null ? string(source, "type") : null;

        String translated = null;

        if ("base64".equals(sourceType))
        {
            final String url = openaiUrl(source);

            if (url != null)
            {
                translated = Json.createObjectBuilder()
                    .add("type", "file")
                    .add("file", Json.createObjectBuilder().add("file_data", url))
                    .build()
                    .toString();
            }
        }
        else if ("file".equals(sourceType) && string(source, "file_id") != null)
        {
            translated = Json.createObjectBuilder()
                .add("type", "file")
                .add("file", Json.createObjectBuilder().add("file_id", string(source, "file_id")))
                .build()
                .toString();
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

    private static JsonObjectBuilder anthropicSource(
        String url)
    {
        final JsonObjectBuilder source = Json.createObjectBuilder();
        final int marker = url.indexOf(BASE64_MARKER);

        if (url.startsWith(DATA_URL_PREFIX) && marker > 0)
        {
            source.add("type", "base64")
                .add("media_type", url.substring(DATA_URL_PREFIX.length(), marker))
                .add("data", url.substring(marker + BASE64_MARKER.length()));
        }
        else
        {
            source.add("type", "url").add("url", url);
        }

        return source;
    }

    private static String openaiUrl(
        JsonObject source)
    {
        final String sourceType = source != null ? string(source, "type") : null;

        String url = null;

        if ("url".equals(sourceType))
        {
            url = string(source, "url");
        }
        else if ("base64".equals(sourceType))
        {
            final String mediaType = string(source, "media_type");
            final String data = string(source, "data");

            if (mediaType != null && data != null)
            {
                url = DATA_URL_PREFIX + mediaType + BASE64_MARKER + data;
            }
        }

        return url;
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
