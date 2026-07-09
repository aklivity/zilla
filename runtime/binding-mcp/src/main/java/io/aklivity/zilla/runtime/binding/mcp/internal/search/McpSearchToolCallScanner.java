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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableDirectByteBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

/**
 * Incrementally scans a {@code tools/call} request payload for the agent-callable search tool, one
 * {@code DATA} frame at a time via {@link #feed}, extracting {@code arguments.query} and
 * {@code arguments.max_results} without materializing a DOM or buffering the whole request.
 * <p>
 * One instance is created per stream and fed every frame in order. Per {@link JsonParserEx}'s
 * windowed contract, a value that straddles a window boundary is declined ({@code consumed(0)})
 * until it arrives whole, so {@link #query} and {@link #maxResults} are only ever assigned a
 * complete value, never a fragment.
 */
public final class McpSearchToolCallScanner
{
    private static final String ARGUMENTS_NAME = "arguments";
    private static final String QUERY_NAME = "query";
    private static final String MAX_RESULTS_NAME = "max_results";

    private final JsonParserEx parser;
    private final ExpandableDirectByteBufferEx carry;
    private final ExpandableDirectByteBufferEx window;

    private int windowLength;
    private int depth;
    private int argumentsDepth = -1;
    private boolean argumentsArmed;
    private boolean queryArmed;
    private boolean maxResultsArmed;
    private boolean done;

    public String query;
    public int maxResults;
    public boolean malformed;

    public McpSearchToolCallScanner()
    {
        this.parser = JsonEx.createParser();
        this.carry = new ExpandableDirectByteBufferEx();
        this.window = new ExpandableDirectByteBufferEx();
    }

    public void feed(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last)
    {
        if (done)
        {
            return;
        }

        final int carryLength = parser.remaining();
        if (carryLength > 0)
        {
            carry.putBytes(0, window, windowLength - carryLength, carryLength);
        }
        window.putBytes(0, carry, 0, carryLength);
        window.putBytes(carryLength, buffer, offset, length);
        windowLength = carryLength + length;

        parser.wrap(window, 0, windowLength, last);

        try
        {
            scan();
        }
        catch (Exception ex)
        {
            malformed = true;
            done = true;
        }
    }

    private void scan()
    {
        JsonEvent event;
        while (!done && (event = parser.nextEvent()) != null)
        {
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                onOpen();
                break;
            case END_OBJECT:
            case END_ARRAY:
                onClose();
                break;
            case KEY_NAME:
                onKey();
                break;
            case VALUE_STRING:
                onStringValue();
                break;
            case VALUE_NUMBER:
                onNumberValue();
                break;
            case END_DOCUMENT:
                done = true;
                break;
            default:
                break;
            }
        }
    }

    private void onOpen()
    {
        depth++;
        if (argumentsArmed)
        {
            argumentsArmed = false;
            argumentsDepth = depth;
        }
    }

    private void onClose()
    {
        if (depth == argumentsDepth)
        {
            argumentsDepth = -1;
            queryArmed = false;
            maxResultsArmed = false;
        }
        depth--;
        if (depth == 0)
        {
            done = true;
        }
    }

    private void onKey()
    {
        if (parser.deferredBytes())
        {
            parser.consumed(0);
        }
        else
        {
            final CharSequence key = parser.getStringView();
            if (depth == 1)
            {
                argumentsArmed = ARGUMENTS_NAME.contentEquals(key);
            }
            else if (depth == argumentsDepth)
            {
                queryArmed = QUERY_NAME.contentEquals(key);
                maxResultsArmed = MAX_RESULTS_NAME.contentEquals(key);
            }
        }
    }

    private void onStringValue()
    {
        if (queryArmed)
        {
            if (parser.deferredBytes())
            {
                parser.consumed(0);
            }
            else
            {
                query = parser.getString();
                queryArmed = false;
            }
        }
    }

    private void onNumberValue()
    {
        if (maxResultsArmed)
        {
            if (parser.deferredBytes())
            {
                parser.consumed(0);
            }
            else
            {
                maxResults = (int) parser.getLong();
                maxResultsArmed = false;
            }
        }
    }
}
