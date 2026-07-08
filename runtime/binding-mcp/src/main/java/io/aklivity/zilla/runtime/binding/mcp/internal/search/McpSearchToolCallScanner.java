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

import jakarta.json.stream.JsonParser;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

/**
 * Scans a buffered {@code tools/call} request payload for the agent-callable search tool,
 * extracting {@code arguments.query} and {@code arguments.max_results} without materializing a DOM.
 */
public final class McpSearchToolCallScanner
{
    private static final String ARGUMENTS_NAME = "arguments";
    private static final String QUERY_NAME = "query";
    private static final String MAX_RESULTS_NAME = "max_results";

    private McpSearchToolCallScanner()
    {
    }

    public static McpSearchToolCallArgs scan(
        DirectBufferEx buffer,
        int index,
        int limit)
    {
        McpSearchToolCallArgs args;

        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(buffer, index, limit);
        try
        {
            args = scanRequest(parser);
        }
        catch (Exception ex)
        {
            args = null;
        }

        return args;
    }

    private static McpSearchToolCallArgs scanRequest(
        JsonParserEx parser)
    {
        String query = null;
        int maxResults = 0;

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
                    if (depth == 1 && ARGUMENTS_NAME.contentEquals(parser.getStringView()))
                    {
                        final McpSearchToolCallArgs arguments = scanArguments(parser);
                        query = arguments.query;
                        maxResults = arguments.maxResults;
                    }
                    break;
                default:
                    break;
                }
            }
        }

        return new McpSearchToolCallArgs(query, maxResults);
    }

    private static McpSearchToolCallArgs scanArguments(
        JsonParserEx parser)
    {
        String query = null;
        int maxResults = 0;

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
                    if (depth == 1 && QUERY_NAME.contentEquals(parser.getStringView()))
                    {
                        parser.next();
                        query = parser.getString();
                    }
                    else if (depth == 1 && MAX_RESULTS_NAME.contentEquals(parser.getStringView()))
                    {
                        parser.next();
                        maxResults = (int) parser.getLong();
                    }
                    break;
                default:
                    break;
                }
            }
        }

        return new McpSearchToolCallArgs(query, maxResults);
    }
}
