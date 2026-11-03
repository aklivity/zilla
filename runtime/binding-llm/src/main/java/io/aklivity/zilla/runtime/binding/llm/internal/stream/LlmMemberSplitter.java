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

import static java.nio.charset.StandardCharsets.UTF_8;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class LlmMemberSplitter
{
    @FunctionalInterface
    interface Members
    {
        boolean put(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean init,
            boolean last);
    }

    private static final DirectBufferEx OPEN = new UnsafeBufferEx("{".getBytes(UTF_8));
    private static final DirectBufferEx CLOSE = new UnsafeBufferEx("}".getBytes(UTF_8));

    private static final int OBJECT_OPEN = 0;
    private static final int KEY_START = 1;
    private static final int KEY = 2;
    private static final int COLON = 3;
    private static final int VALUE_START = 4;
    private static final int STRING = 5;
    private static final int CONTAINER = 6;
    private static final int SCALAR = 7;
    private static final int MEMBER_END = 8;
    private static final int OBJECT_END = 9;
    private static final int PASS = 10;

    private final Members members;

    private int state;
    private int depth;
    private boolean inString;
    private boolean escaped;
    private boolean open;
    private boolean accepted;

    LlmMemberSplitter(
        Members members)
    {
        this.members = members;
    }

    void begin()
    {
        state = OBJECT_OPEN;
        depth = 0;
        inString = false;
        escaped = false;
        open = false;
        accepted = true;
    }

    boolean put(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last)
    {
        final int limit = offset + length;
        int runStart = offset;
        int index = offset;

        while (accepted && index < limit)
        {
            final byte value = buffer.getByte(index);
            boolean consumed = true;

            switch (state)
            {
            case OBJECT_OPEN:
                consumed = onObjectOpen(value);
                break;
            case KEY_START:
                if (value == '"')
                {
                    runStart = index;
                    openMember();
                    inString = true;
                    escaped = false;
                    state = KEY;
                }
                else if (value == '}')
                {
                    state = OBJECT_END;
                }
                else if (!isSeparator(value))
                {
                    runStart = index;
                    openMember();
                    state = PASS;
                }
                break;
            case KEY:
                if (endsString(value))
                {
                    state = COLON;
                }
                break;
            case COLON:
                if (value == ':')
                {
                    state = VALUE_START;
                }
                break;
            case VALUE_START:
                consumed = onValueStart(value);
                break;
            case STRING:
                if (endsString(value))
                {
                    runStart = closeMember(buffer, runStart, index + 1);
                }
                break;
            case CONTAINER:
                if (endsContainer(value))
                {
                    runStart = closeMember(buffer, runStart, index + 1);
                }
                break;
            case SCALAR:
                if (isSeparator(value) || value == '}')
                {
                    runStart = closeMember(buffer, runStart, index);
                    consumed = false;
                }
                break;
            case MEMBER_END:
                if (value == '}')
                {
                    state = OBJECT_END;
                }
                else if (value == ',')
                {
                    state = KEY_START;
                }
                break;
            default:
                break;
            }

            index += consumed ? 1 : 0;
        }

        if (accepted && open && runStart < limit)
        {
            accepted = members.put(buffer, runStart, limit - runStart, false, false);
        }

        if (accepted && last && open)
        {
            runStart = closeMember(buffer, limit, limit);
        }

        return accepted;
    }

    private boolean onObjectOpen(
        byte value)
    {
        boolean consumed = true;

        if (value == '{')
        {
            state = KEY_START;
        }
        else if (!isWhitespace(value))
        {
            openMember();
            state = PASS;
            consumed = false;
        }

        return consumed;
    }

    private boolean onValueStart(
        byte value)
    {
        boolean consumed = true;

        if (value == '"')
        {
            inString = true;
            escaped = false;
            state = STRING;
        }
        else if (value == '{' || value == '[')
        {
            depth = 1;
            inString = false;
            state = CONTAINER;
        }
        else if (!isWhitespace(value))
        {
            state = SCALAR;
        }

        return consumed;
    }

    private boolean endsString(
        byte value)
    {
        boolean ends = false;

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
            ends = true;
        }

        return ends;
    }

    private boolean endsContainer(
        byte value)
    {
        boolean ends = false;

        if (inString)
        {
            endsString(value);
        }
        else if (value == '"')
        {
            inString = true;
            escaped = false;
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

    private void openMember()
    {
        open = true;
        accepted = members.put(OPEN, 0, 1, true, false);
    }

    private int closeMember(
        DirectBufferEx buffer,
        int runStart,
        int runEnd)
    {
        if (runEnd > runStart)
        {
            accepted = members.put(buffer, runStart, runEnd - runStart, false, false);
        }

        if (accepted)
        {
            accepted = members.put(CLOSE, 0, 1, false, true);
        }

        open = false;
        state = MEMBER_END;

        return runEnd;
    }

    private static boolean isWhitespace(
        byte value)
    {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private static boolean isSeparator(
        byte value)
    {
        return value == ',' || isWhitespace(value);
    }
}
