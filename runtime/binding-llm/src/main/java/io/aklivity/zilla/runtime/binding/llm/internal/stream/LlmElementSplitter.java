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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

final class LlmElementSplitter
{
    @FunctionalInterface
    interface Elements
    {
        boolean put(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean init,
            boolean last);
    }

    private static final int ARRAY_OPEN = 0;
    private static final int ELEMENT_START = 1;
    private static final int ELEMENT = 2;
    private static final int ARRAY_END = 3;

    private final Elements elements;

    private int state;
    private int depth;
    private boolean inString;
    private boolean escaped;
    private boolean init;
    private boolean accepted;

    LlmElementSplitter(
        Elements elements)
    {
        this.elements = elements;
    }

    void begin()
    {
        state = ARRAY_OPEN;
        depth = 0;
        inString = false;
        escaped = false;
        init = false;
        accepted = true;
    }

    boolean put(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        final int limit = offset + length;
        int runStart = offset;

        for (int index = offset; accepted && index < limit; index++)
        {
            final byte value = buffer.getByte(index);

            switch (state)
            {
            case ARRAY_OPEN:
                accepted = value == '[' || isWhitespace(value);
                state = value == '[' ? ELEMENT_START : ARRAY_OPEN;
                break;
            case ELEMENT_START:
                if (value == '{')
                {
                    runStart = index;
                    depth = 1;
                    inString = false;
                    init = true;
                    state = ELEMENT;
                }
                else if (value == ']')
                {
                    state = ARRAY_END;
                }
                else
                {
                    accepted = value == ',' || isWhitespace(value);
                }
                break;
            case ELEMENT:
                if (endsElement(value))
                {
                    accepted = elements.put(buffer, runStart, index + 1 - runStart, init, true);
                    init = false;
                    runStart = index + 1;
                    state = ELEMENT_START;
                }
                break;
            default:
                break;
            }
        }

        if (accepted && state == ELEMENT && runStart < limit)
        {
            accepted = elements.put(buffer, runStart, limit - runStart, init, false);
            init = false;
        }

        return accepted;
    }

    private boolean endsElement(
        byte value)
    {
        boolean ends = false;

        if (inString)
        {
            if (escaped)
            {
                escaped = false;
            }
            else if (value == '\\')
            {
                escaped = true;
            }
            else if (value == '"')
            {
                inString = false;
            }
        }
        else if (value == '"')
        {
            inString = true;
        }
        else if (value == '{' || value == '[')
        {
            depth++;
        }
        else if (value == '}' || value == ']')
        {
            depth--;
            ends = depth == 0;
        }

        return ends;
    }

    private static boolean isWhitespace(
        byte value)
    {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }
}
