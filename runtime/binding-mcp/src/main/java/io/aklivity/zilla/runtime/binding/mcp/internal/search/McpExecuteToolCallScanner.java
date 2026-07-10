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
 * Incrementally scans a {@code tools/call} request payload for the agent-callable execute tool, one
 * {@code DATA} frame at a time via {@link #feed}, extracting {@code arguments.name} (the target tool
 * to invoke) and the raw verbatim byte span of {@code arguments.arguments} (the target tool's own
 * call arguments), without materializing a DOM or re-serializing either value.
 * <p>
 * The target's own arguments object is never decoded -- its exact source bytes are copied once,
 * whole-token by whole-token as they stream past, into {@link #arguments}, bounded by that value's
 * own size rather than the whole request -- so they can be forwarded byte-for-byte to a different
 * stream's upstream connection. One instance is created per stream and fed every frame in order. Per
 * {@link JsonParserEx}'s windowed contract, a value that straddles a window boundary is declined
 * ({@code consumed(0)}) until it arrives whole, so {@link #name} is only ever assigned a complete
 * value, never a fragment.
 */
public final class McpExecuteToolCallScanner
{
    private static final String ARGUMENTS_NAME = "arguments";
    private static final String NAME_NAME = "name";

    private final JsonParserEx parser;
    private final ExpandableDirectByteBufferEx carry;
    private final ExpandableDirectByteBufferEx window;

    public final ExpandableDirectByteBufferEx arguments;

    private int windowLength;
    private int depth;
    private int argumentsDepth = -1;
    private boolean argumentsArmed;
    private boolean nameArmed;
    private boolean targetArgsArmed;
    private boolean capturing;
    private int captureDepth;
    private int captureStart;
    private boolean captureStartedThisWindow;
    private int captureStopThisWindow;
    private boolean done;

    public String name;
    public int argumentsLength;
    public boolean malformed;

    public McpExecuteToolCallScanner()
    {
        this.parser = JsonEx.createParser();
        this.carry = new ExpandableDirectByteBufferEx();
        this.window = new ExpandableDirectByteBufferEx();
        this.arguments = new ExpandableDirectByteBufferEx();
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

        final boolean capturingAtEntry = capturing;
        captureStartedThisWindow = false;
        captureStopThisWindow = -1;

        try
        {
            scan();
        }
        catch (Exception ex)
        {
            malformed = true;
            done = true;
        }

        if (capturingAtEntry || captureStartedThisWindow)
        {
            final int from = captureStartedThisWindow ? captureStart : 0;
            final int to = captureStopThisWindow >= 0
                ? captureStopThisWindow
                : windowLength - parser.remaining();
            if (to > from)
            {
                arguments.putBytes(argumentsLength, window, from, to - from);
                argumentsLength += to - from;
            }
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
                onOpen(event);
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
            case VALUE_TRUE:
            case VALUE_FALSE:
            case VALUE_NULL:
                onScalarValue();
                break;
            case END_DOCUMENT:
                done = true;
                break;
            default:
                break;
            }
        }
    }

    private void onOpen(
        JsonEvent event)
    {
        depth++;
        if (argumentsArmed)
        {
            argumentsArmed = false;
            argumentsDepth = depth;
        }

        if (capturing)
        {
            captureDepth++;
        }
        else if (targetArgsArmed)
        {
            targetArgsArmed = false;
            if (event == JsonEvent.START_OBJECT)
            {
                capturing = true;
                captureDepth = 1;
                captureStartedThisWindow = true;
                captureStart = windowLength - parser.remaining() - 1;
            }
            else
            {
                malformed = true;
                done = true;
            }
        }
    }

    private void onClose()
    {
        if (capturing)
        {
            captureDepth--;
            if (captureDepth == 0)
            {
                captureStopThisWindow = windowLength - parser.remaining();
                capturing = false;
            }
        }

        if (depth == argumentsDepth)
        {
            argumentsDepth = -1;
            nameArmed = false;
            targetArgsArmed = false;
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
                nameArmed = NAME_NAME.contentEquals(key);
                targetArgsArmed = ARGUMENTS_NAME.contentEquals(key);
            }
        }
    }

    private void onStringValue()
    {
        if (nameArmed)
        {
            if (parser.deferredBytes())
            {
                parser.consumed(0);
            }
            else
            {
                name = parser.getString();
                nameArmed = false;
            }
        }
        else if (targetArgsArmed)
        {
            malformed = true;
            done = true;
        }
    }

    private void onScalarValue()
    {
        if (targetArgsArmed)
        {
            malformed = true;
            done = true;
        }
    }
}
