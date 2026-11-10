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

final class LlmHeaderBytes
{
    private static final int MEDIA_TYPE_PARAMETERS = ';';

    static boolean equalTo(
        DirectBufferEx value,
        String expected)
    {
        boolean matches = value != null && value.capacity() == expected.length();

        for (int position = 0; matches && position < expected.length(); position++)
        {
            matches = value.getByte(position) == expected.charAt(position);
        }

        return matches;
    }

    static boolean equalToIgnoreCase(
        DirectBufferEx value,
        String expected)
    {
        boolean matches = value != null && value.capacity() == expected.length();

        for (int position = 0; matches && position < expected.length(); position++)
        {
            matches = Character.toUpperCase((char) (value.getByte(position) & 0xff)) ==
                Character.toUpperCase(expected.charAt(position));
        }

        return matches;
    }

    static boolean startsWith(
        DirectBufferEx value,
        int from,
        int to,
        String prefix)
    {
        return to - from >= prefix.length() && regionEquals(value, from, prefix);
    }

    static boolean endsWith(
        DirectBufferEx value,
        int to,
        String suffix)
    {
        return to >= suffix.length() && regionEquals(value, to - suffix.length(), suffix);
    }

    static boolean isMediaType(
        DirectBufferEx value,
        String mediaType)
    {
        boolean matches = false;

        if (value != null)
        {
            final int length = value.capacity();

            int start = skipWhitespace(value, 0, length);
            int end = start;

            while (end < length && value.getByte(end) != MEDIA_TYPE_PARAMETERS && !whitespace(value.getByte(end)))
            {
                end++;
            }

            final int next = skipWhitespace(value, end, length);

            matches = end - start == mediaType.length() &&
                regionEquals(value, start, mediaType) &&
                (next == length || value.getByte(next) == MEDIA_TYPE_PARAMETERS && singleLine(value, next, length));
        }

        return matches;
    }

    private static boolean regionEquals(
        DirectBufferEx value,
        int offset,
        String expected)
    {
        boolean matches = true;

        for (int position = 0; matches && position < expected.length(); position++)
        {
            matches = value.getByte(offset + position) == expected.charAt(position);
        }

        return matches;
    }

    private static int skipWhitespace(
        DirectBufferEx value,
        int from,
        int length)
    {
        int position = from;

        while (position < length && whitespace(value.getByte(position)))
        {
            position++;
        }

        return position;
    }

    private static boolean singleLine(
        DirectBufferEx value,
        int from,
        int length)
    {
        boolean single = true;

        for (int position = from; single && position < length; position++)
        {
            final byte b = value.getByte(position);

            single = b != '\n' && b != '\r';
        }

        return single;
    }

    private static boolean whitespace(
        byte b)
    {
        return b == ' ' || b == '\t' || b == '\n' || b == 0x0b || b == '\f' || b == '\r';
    }

    private LlmHeaderBytes()
    {
    }
}
