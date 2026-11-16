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
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

final class LlmUsageMerge
{
    private static final int LIMIT = 0;
    private static final int BODY = Integer.BYTES;
    private static final int NONE = -1;

    private LlmUsageMerge()
    {
    }

    static void reset(
        MutableDirectBufferEx state)
    {
        state.putInt(LIMIT, BODY);
    }

    static boolean merge(
        MutableDirectBufferEx state,
        int capacity,
        DirectBufferEx json,
        int offset,
        int limit)
    {
        int index = skipWhitespace(json, offset, limit);

        index = index < limit && json.getByte(index) == '{' ? skipWhitespace(json, index + 1, limit) : NONE;
        index = index != NONE && index < limit && json.getByte(index) == '"' ? scanString(json, index, limit) : NONE;
        index = index != NONE ? skipWhitespace(json, index, limit) : NONE;
        index = index != NONE && index < limit && json.getByte(index) == ':' ? skipWhitespace(json, index + 1, limit) : NONE;
        index = index != NONE && index < limit && json.getByte(index) == '{' ? skipWhitespace(json, index + 1, limit) : NONE;

        boolean merged = index != NONE;
        boolean more = merged && !(index < limit && json.getByte(index) == '}');

        while (merged && more)
        {
            final int keyStart = index;
            final int keyEnd = index < limit && json.getByte(index) == '"' ? scanString(json, index, limit) : NONE;
            final int colon = keyEnd != NONE ? skipWhitespace(json, keyEnd, limit) : NONE;
            final int valueStart = colon != NONE && colon < limit && json.getByte(colon) == ':'
                ? skipWhitespace(json, colon + 1, limit)
                : NONE;
            final int valueEnd = valueStart != NONE ? scanValue(json, valueStart, limit) : NONE;

            merged = valueEnd != NONE && put(state, capacity, json, keyStart, keyEnd, valueStart, valueEnd);

            if (merged)
            {
                index = skipWhitespace(json, valueEnd, limit);

                final byte next = index < limit ? json.getByte(index) : 0;

                if (next == ',')
                {
                    index = skipWhitespace(json, index + 1, limit);
                }
                else
                {
                    more = false;
                    merged = next == '}';
                }
            }
        }

        return merged;
    }

    static int compose(
        MutableDirectBufferEx state,
        int capacity,
        byte[] prefix)
    {
        final int limit = state.getInt(LIMIT);
        final int body = limit - BODY;
        final int length = prefix.length + 1 + body + 2;

        int composed = NONE;

        if (limit + length <= capacity)
        {
            int index = limit;

            state.putBytes(index, prefix);
            index += prefix.length;
            state.putByte(index++, (byte) '{');
            state.putBytes(index, state, BODY, body);
            index += body;
            state.putByte(index++, (byte) '}');
            state.putByte(index, (byte) '}');

            composed = length;
        }

        return composed;
    }

    static int composedAt(
        DirectBufferEx state)
    {
        return state.getInt(LIMIT);
    }

    private static boolean put(
        MutableDirectBufferEx state,
        int capacity,
        DirectBufferEx json,
        int keyStart,
        int keyEnd,
        int valueStart,
        int valueEnd)
    {
        final int limit = state.getInt(LIMIT);
        final int keyLength = keyEnd - keyStart;
        final int valueLength = compactLength(json, valueStart, valueEnd);

        int memberStart = BODY;
        int foundStart = NONE;
        int foundEnd = NONE;

        while (foundStart == NONE && memberStart < limit)
        {
            final int memberKeyEnd = scanString(state, memberStart, limit);
            final int memberValueEnd = scanValue(state, memberKeyEnd + 1, limit);

            if (memberKeyEnd - memberStart == keyLength && sameBytes(state, memberStart, json, keyStart, keyLength))
            {
                foundStart = memberKeyEnd + 1;
                foundEnd = memberValueEnd;
            }
            else
            {
                memberStart = memberValueEnd < limit ? memberValueEnd + 1 : limit;
            }
        }

        final boolean stored;

        if (foundStart != NONE)
        {
            final int delta = valueLength - (foundEnd - foundStart);

            stored = limit + delta <= capacity;

            if (stored)
            {
                shift(state, foundEnd, limit, delta);
                copyCompact(state, foundStart, json, valueStart, valueEnd);
                state.putInt(LIMIT, limit + delta);
            }
        }
        else
        {
            final int separator = limit > BODY ? 1 : 0;
            final int length = separator + keyLength + 1 + valueLength;

            stored = limit + length <= capacity;

            if (stored)
            {
                int index = limit;

                if (separator != 0)
                {
                    state.putByte(index++, (byte) ',');
                }

                state.putBytes(index, json, keyStart, keyLength);
                index += keyLength;
                state.putByte(index++, (byte) ':');
                copyCompact(state, index, json, valueStart, valueEnd);
                state.putInt(LIMIT, limit + length);
            }
        }

        return stored;
    }

    private static void shift(
        MutableDirectBufferEx state,
        int from,
        int limit,
        int delta)
    {
        if (delta > 0)
        {
            for (int index = limit - 1; index >= from; index--)
            {
                state.putByte(index + delta, state.getByte(index));
            }
        }
        else if (delta < 0)
        {
            for (int index = from; index < limit; index++)
            {
                state.putByte(index + delta, state.getByte(index));
            }
        }
    }

    private static boolean sameBytes(
        DirectBufferEx left,
        int leftIndex,
        DirectBufferEx right,
        int rightIndex,
        int length)
    {
        boolean same = true;

        for (int index = 0; same && index < length; index++)
        {
            same = left.getByte(leftIndex + index) == right.getByte(rightIndex + index);
        }

        return same;
    }

    private static int compactLength(
        DirectBufferEx json,
        int from,
        int to)
    {
        int length = 0;
        boolean string = false;
        boolean escaped = false;

        for (int index = from; index < to; index++)
        {
            final byte value = json.getByte(index);

            if (string || !isWhitespace(value))
            {
                length++;
            }

            if (string)
            {
                string = escaped || value != '"';
                escaped = !escaped && value == '\\';
            }
            else
            {
                string = value == '"';
            }
        }

        return length;
    }

    private static void copyCompact(
        MutableDirectBufferEx state,
        int at,
        DirectBufferEx json,
        int from,
        int to)
    {
        int next = at;
        boolean string = false;
        boolean escaped = false;

        for (int index = from; index < to; index++)
        {
            final byte value = json.getByte(index);

            if (string || !isWhitespace(value))
            {
                state.putByte(next++, value);
            }

            if (string)
            {
                string = escaped || value != '"';
                escaped = !escaped && value == '\\';
            }
            else
            {
                string = value == '"';
            }
        }
    }

    private static int scanValue(
        DirectBufferEx json,
        int index,
        int limit)
    {
        final byte first = index < limit ? json.getByte(index) : 0;

        int end = NONE;

        if (first == '"')
        {
            end = scanString(json, index, limit);
        }
        else if (first == '{' || first == '[')
        {
            end = scanStructure(json, index, limit);
        }
        else if (first != 0)
        {
            end = index;

            while (end < limit && !isDelimiter(json.getByte(end)))
            {
                end++;
            }

            end = end > index ? end : NONE;
        }

        return end;
    }

    private static int scanStructure(
        DirectBufferEx json,
        int index,
        int limit)
    {
        int depth = 0;
        int next = index;
        int end = NONE;

        while (end == NONE && next < limit)
        {
            final byte value = json.getByte(next);

            if (value == '"')
            {
                next = scanString(json, next, limit);

                if (next == NONE)
                {
                    next = limit;
                }
            }
            else
            {
                depth += value == '{' || value == '[' ? 1 : value == '}' || value == ']' ? -1 : 0;
                next++;
                end = depth == 0 ? next : NONE;
            }
        }

        return end;
    }

    private static int scanString(
        DirectBufferEx json,
        int index,
        int limit)
    {
        int next = index + 1;
        int end = NONE;

        while (end == NONE && next < limit)
        {
            final byte value = json.getByte(next);

            if (value == '\\')
            {
                next += 2;
            }
            else if (value == '"')
            {
                end = next + 1;
            }
            else
            {
                next++;
            }
        }

        return end;
    }

    private static int skipWhitespace(
        DirectBufferEx json,
        int index,
        int limit)
    {
        int next = index;

        while (next < limit && isWhitespace(json.getByte(next)))
        {
            next++;
        }

        return next;
    }

    private static boolean isWhitespace(
        byte value)
    {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private static boolean isDelimiter(
        byte value)
    {
        return isWhitespace(value) || value == ',' || value == '}' || value == ']';
    }
}
