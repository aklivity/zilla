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

import java.util.Map;
import java.util.TreeMap;

import jakarta.json.stream.JsonParser;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

/**
 * Scans a cached {@code tools/list} JSON response and records, per tool {@code name}, the
 * half-open byte range bounding that tool's whole JSON object exactly as it appears in the scanned
 * bytes -- the byte-copy source for a {@code tools/search} match, so a match is served as a verbatim
 * slice of the same bytes indexed once per cache refresh rather than re-serialized per request.
 */
public final class McpToolByteRangeScanner
{
    private static final String TOOLS_NAME = "tools";
    private static final String NAME_NAME = "name";

    private McpToolByteRangeScanner()
    {
    }

    public static Map<CharSequence, McpToolByteRange> scan(
        byte[] bytes)
    {
        final Map<CharSequence, McpToolByteRange> ranges = new TreeMap<>(CharSequence::compare);

        if (bytes != null)
        {
            final JsonParserEx parser = JsonEx.createParser();
            parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
            try
            {
                scanToolsList(parser, ranges);
            }
            catch (Exception ex)
            {
                ranges.clear();
            }
        }

        return ranges;
    }

    private static void scanToolsList(
        JsonParserEx parser,
        Map<CharSequence, McpToolByteRange> ranges)
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
                        scanTools(parser, ranges);
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
        Map<CharSequence, McpToolByteRange> ranges)
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
                    // the tokenizer's stream offset already includes the just-consumed '{', so the
                    // object's own start is one byte earlier
                    final int start = (int) parser.getLocation().getStreamOffset() - 1;
                    scanTool(parser, ranges, start);
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

    private static void scanTool(
        JsonParserEx parser,
        Map<CharSequence, McpToolByteRange> ranges,
        int start)
    {
        String name = null;
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
                if (depth == 1 && NAME_NAME.contentEquals(parser.getStringView()))
                {
                    parser.next();
                    name = parser.getString();
                }
                break;
            default:
                break;
            }
        }

        if (name != null)
        {
            final int end = (int) parser.getLocation().getStreamOffset();
            ranges.put(name, new McpToolByteRange(start, end - start));
        }
    }
}
