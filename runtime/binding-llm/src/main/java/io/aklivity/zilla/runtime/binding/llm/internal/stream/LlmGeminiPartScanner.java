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

final class LlmGeminiPartScanner
{
    static final int UNDECIDED = 0;
    static final int PLAIN = 1;
    static final int THOUGHT = 2;

    private static final int INCOMPLETE = -1;
    private static final String MEMBER_THOUGHT = "thought";
    private static final String LITERAL_TRUE = "true";

    static int scan(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int status = UNDECIDED;
        int index = offset;

        while (status == UNDECIDED && index != INCOMPLETE)
        {
            index = skipSeparators(buffer, index, limit);

            if (index == limit)
            {
                index = INCOMPLETE;
            }
            else if (buffer.getByte(index) == '"')
            {
                final int keyEnd = skipString(buffer, index, limit);
                final boolean thought = keyEnd != INCOMPLETE && matches(buffer, index + 1, keyEnd - 1, MEMBER_THOUGHT);
                final int valueAt = keyEnd != INCOMPLETE ? skipSeparators(buffer, keyEnd, limit) : limit;

                if (valueAt < limit && buffer.getByte(valueAt) == ':')
                {
                    final int valueStart = skipSeparators(buffer, valueAt + 1, limit);

                    if (valueStart < limit && thought && limit - valueStart >= LITERAL_TRUE.length() &&
                        matches(buffer, valueStart, valueStart + LITERAL_TRUE.length(), LITERAL_TRUE))
                    {
                        status = THOUGHT;
                    }
                    else
                    {
                        index = valueStart < limit ? skipValue(buffer, valueStart, limit) : INCOMPLETE;
                    }
                }
                else
                {
                    index = INCOMPLETE;
                }
            }
            else
            {
                status = PLAIN;
            }
        }

        return status;
    }

    private static int skipSeparators(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int index = offset;

        while (index < limit && isSeparator(buffer.getByte(index)))
        {
            index++;
        }

        return index;
    }

    private static boolean isSeparator(
        byte value)
    {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n' || value == ',';
    }

    private static int skipString(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int index = offset + 1;
        boolean escaped = false;
        boolean closed = false;

        while (index < limit && !closed)
        {
            final byte value = buffer.getByte(index++);

            closed = !escaped && value == '"';
            escaped = !escaped && value == '\\';
        }

        return closed ? index : INCOMPLETE;
    }

    private static int skipValue(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final byte first = buffer.getByte(offset);
        int index = offset;

        if (first == '"')
        {
            index = skipString(buffer, offset, limit);
        }
        else if (first == '{' || first == '[')
        {
            int depth = 0;

            do
            {
                final byte value = buffer.getByte(index);

                if (value == '"')
                {
                    index = skipString(buffer, index, limit);
                }
                else
                {
                    depth += value == '{' || value == '[' ? 1 : value == '}' || value == ']' ? -1 : 0;
                    index++;
                }
            }
            while (depth > 0 && index != INCOMPLETE && index < limit);

            index = depth == 0 ? index : INCOMPLETE;
        }
        else
        {
            while (index < limit && !isDelimiter(buffer.getByte(index)))
            {
                index++;
            }

            index = index < limit ? index : INCOMPLETE;
        }

        return index;
    }

    private static boolean isDelimiter(
        byte value)
    {
        return isSeparator(value) || value == '}' || value == ']';
    }

    private static boolean matches(
        DirectBufferEx buffer,
        int from,
        int to,
        String expected)
    {
        boolean matches = to - from == expected.length();

        for (int index = 0; matches && index < expected.length(); index++)
        {
            matches = buffer.getByte(from + index) == expected.charAt(index);
        }

        return matches;
    }

    private LlmGeminiPartScanner()
    {
    }
}
