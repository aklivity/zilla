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
package io.aklivity.zilla.runtime.binding.mcp.internal.search;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.stream.JsonParser;

import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchDocument;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

/**
 * Scans a cached {@code tools/list} JSON response into {@link McpToolSearchDocument}s, one per
 * tool, extracting only the configured {@code fields}. A field whose JSON value is an object
 * (e.g. {@code outputSchema}) is flattened to space-joined text (every key name and string value
 * in its subtree) rather than indexed as structured JSON.
 */
public final class McpToolSearchDocumentScanner
{
    private static final String TOOLS_NAME = "tools";
    private static final String NAME_NAME = "name";
    private static final String DESCRIPTION_NAME = "description";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String OUTPUT_SCHEMA_NAME = "outputSchema";
    private static final String OUTPUT_SCHEMA_FIELD = "output-schema";

    private McpToolSearchDocumentScanner()
    {
    }

    public static List<McpToolSearchDocument> scan(
        String toolsListJson,
        List<String> fields)
    {
        List<McpToolSearchDocument> documents = new ArrayList<>();

        if (toolsListJson != null && fields != null && !fields.isEmpty())
        {
            final byte[] bytes = toolsListJson.getBytes(StandardCharsets.UTF_8);
            final JsonParserEx parser = JsonEx.createParser();
            parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
            try
            {
                scanToolsList(parser, fields, documents);
            }
            catch (Exception ex)
            {
                documents.clear();
            }
        }

        return documents;
    }

    private static void scanToolsList(
        JsonParserEx parser,
        List<String> fields,
        List<McpToolSearchDocument> documents)
    {
        if (parser.hasNext() && parser.next() == JsonParser.Event.START_OBJECT)
        {
            int depth = 1;
            while (depth > 0 && parser.hasNext())
            {
                final JsonParser.Event event = parser.next();
                switch (event)
                {
                case START_OBJECT:
                case START_ARRAY:
                    depth++;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    depth--;
                    break;
                case KEY_NAME:
                    if (depth == 1 && TOOLS_NAME.contentEquals(parser.getStringView()))
                    {
                        scanTools(parser, fields, documents);
                    }
                    break;
                default:
                    break;
                }
            }
        }
    }

    private static void scanTools(
        JsonParserEx parser,
        List<String> fields,
        List<McpToolSearchDocument> documents)
    {
        if (parser.hasNext() && parser.next() == JsonParser.Event.START_ARRAY)
        {
            boolean items = true;
            while (items && parser.hasNext())
            {
                final JsonParser.Event event = parser.next();
                switch (event)
                {
                case START_OBJECT:
                    final McpToolSearchDocument document = scanTool(parser, fields);
                    if (document != null)
                    {
                        documents.add(document);
                    }
                    break;
                case END_ARRAY:
                    items = false;
                    break;
                default:
                    break;
                }
            }
        }
    }

    private static McpToolSearchDocument scanTool(
        JsonParserEx parser,
        List<String> fields)
    {
        String name = null;
        final Map<String, String> values = new HashMap<>();
        int depth = 1;
        while (depth > 0 && parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                depth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                depth--;
                break;
            case KEY_NAME:
                if (depth == 1)
                {
                    final CharSequence key = parser.getStringView();
                    if (NAME_NAME.contentEquals(key))
                    {
                        parser.next();
                        name = parser.getString();
                        if (fields.contains(NAME_NAME))
                        {
                            values.put(NAME_NAME, name);
                        }
                    }
                    else
                    {
                        final String field = fieldFor(key, fields);
                        if (field != null)
                        {
                            values.put(field, flattenText(parser));
                        }
                    }
                }
                break;
            default:
                break;
            }
        }

        return name != null ? new McpToolSearchDocument(name, values) : null;
    }

    private static String fieldFor(
        CharSequence key,
        List<String> fields)
    {
        String field = null;
        if (DESCRIPTION_NAME.contentEquals(key) && fields.contains(DESCRIPTION_FIELD))
        {
            field = DESCRIPTION_FIELD;
        }
        else if (OUTPUT_SCHEMA_NAME.contentEquals(key) && fields.contains(OUTPUT_SCHEMA_FIELD))
        {
            field = OUTPUT_SCHEMA_FIELD;
        }
        return field;
    }

    // flattens a scalar string value, or every key name and string value within an object/array
    // subtree, into one space-joined string; the parser is left positioned after the value either way
    private static String flattenText(
        JsonParserEx parser)
    {
        final StringBuilder text = new StringBuilder();
        final JsonParser.Event first = parser.hasNext() ? parser.next() : null;

        if (first == JsonParser.Event.VALUE_STRING)
        {
            text.append(parser.getString());
        }
        else if (first == JsonParser.Event.START_OBJECT || first == JsonParser.Event.START_ARRAY)
        {
            int depth = 1;
            while (depth > 0 && parser.hasNext())
            {
                final JsonParser.Event event = parser.next();
                switch (event)
                {
                case START_OBJECT:
                case START_ARRAY:
                    depth++;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    depth--;
                    break;
                case KEY_NAME:
                    appendText(text, parser.getStringView());
                    break;
                case VALUE_STRING:
                    appendText(text, parser.getStringView());
                    break;
                default:
                    break;
                }
            }
        }

        return text.toString();
    }

    private static void appendText(
        StringBuilder text,
        CharSequence value)
    {
        if (text.length() > 0)
        {
            text.append(' ');
        }
        text.append(value);
    }
}
