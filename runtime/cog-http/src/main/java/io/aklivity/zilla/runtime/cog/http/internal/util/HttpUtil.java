/*
 * Copyright 2021-2021 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.cog.http.internal.util;

import static java.lang.Character.toUpperCase;

import org.agrona.DirectBuffer;

public final class HttpUtil
{
    private static final long ASCII_LOW_RANGE_LONG_MASK = 0xE0E0_E0E0_E0E0_E0E0L;
    private static final long ASCII_HIGH_RANGE_LONG_MASK = 0x8080_8080_8080_8080L;
    private static final byte ASCII_LOW_RANGE_MASK = (byte) 0xE0;
    private static final byte ASCII_HIGH_RANGE_MASK = -0x80;

    private static final byte ASCII_SPACE = 0x20;
    private static final byte ASCII_DOUBLE_QUOTES = 0x22;
    private static final byte ASCII_LESS_THAN = 0x3C;
    private static final byte ASCII_GREATER_THAN = 0x3E;
    private static final byte ASCII_BACKSLASH = 0x5C;
    private static final byte ASCII_CARET = 0x5E;
    private static final byte ASCII_GRAVE = 0x60;
    private static final byte ASCII_OPEN_BRACE = 0x7B;
    private static final byte ASCII_VERTICAL_BAR = 0x7C;
    private static final byte ASCII_CLOSE_BRACE = 0x7D;
    private static final byte ASCII_DELETE = 0x7F;

    private static final byte ASCII_PERCENT = 0x25;
    private static final byte ASCII_ZERO = 0x30;
    private static final byte ASCII_NINE = 0x39;
    private static final byte ASCII_UPPERCASE_A = 0x41;
    private static final byte ASCII_UPPERCASE_F = 0x46;
    private static final byte ASCII_LOWERCASE_A = 0x61;
    private static final byte ASCII_LOWERCASE_F = 0x66;

    public static void appendHeader(
        StringBuilder payload,
        String name,
        String value)
    {
        StringBuilder initCapsName = new StringBuilder(name);
        int fromIndex = 0;
        do
        {
            initCapsName.setCharAt(fromIndex, toUpperCase(initCapsName.charAt(fromIndex)));
            fromIndex = initCapsName.indexOf("-", fromIndex) + 1;
        } while (fromIndex > 0 && fromIndex < initCapsName.length());

        payload.append(initCapsName).append(": ").append(value).append("\r\n");
    }

    public static boolean isPathValid(
        DirectBuffer path)
    {
        final int pathLength = path.capacity();
        boolean valid = true;
        int capacity = path.capacity();
        int index = 0;
        int longIteration = 0;

        long_loop:
        for (; capacity >= Long.BYTES; index += Long.BYTES, capacity -= Long.BYTES)
        {
            long candidate = path.getLong(index);

            if ((candidate & ASCII_LOW_RANGE_LONG_MASK) == 0L || (candidate & ASCII_HIGH_RANGE_LONG_MASK) != 0L)
            {
                valid = false;
                break long_loop;
            }

            longIteration = 0;
            while (candidate != 0L)
            {
                switch ((int)(candidate & 0x0000_0000_0000_00FFL))
                {
                case ASCII_PERCENT:
                    if (index + longIteration + 2 > pathLength)
                    {
                        valid = false;
                        break long_loop;
                    }

                    byte followedFirstByte = path.getByte(index + longIteration + 1);
                    byte followedSecondByte = path.getByte(index + longIteration + 2);
                    if (!percentEncoded(followedFirstByte, followedSecondByte))
                    {
                        valid = false;
                        break long_loop;
                    }
                    candidate >>= 8;
                    break;
                case ASCII_SPACE:
                case ASCII_DOUBLE_QUOTES:
                case ASCII_LESS_THAN:
                case ASCII_GREATER_THAN:
                case ASCII_BACKSLASH:
                case ASCII_CARET:
                case ASCII_GRAVE:
                case ASCII_OPEN_BRACE:
                case ASCII_VERTICAL_BAR:
                case ASCII_CLOSE_BRACE:
                case ASCII_DELETE:
                    valid = false;
                    break long_loop;
                default:
                    candidate >>= 8;
                    break;
                }
                longIteration++;
            }
        }

        if (valid)
        {
            byte_loop:
            for (; capacity > 0; capacity--, index++)
            {
                byte candidate = path.getByte(index);

                if ((candidate & ASCII_LOW_RANGE_MASK) == 0 || (candidate & ASCII_HIGH_RANGE_MASK) != 0)
                {
                    valid = false;
                    break byte_loop;
                }

                switch (candidate)
                {
                case ASCII_PERCENT:
                    if (capacity < 2)
                    {
                        valid = false;
                        break byte_loop;
                    }

                    byte followedFirsByte = path.getByte(index + 1);
                    byte followedSecondByte = path.getByte(index + 2);
                    if (!percentEncoded(followedFirsByte, followedSecondByte))
                    {
                        valid = false;
                        break byte_loop;
                    }
                    break;
                case ASCII_SPACE:
                case ASCII_DOUBLE_QUOTES:
                case ASCII_LESS_THAN:
                case ASCII_GREATER_THAN:
                case ASCII_BACKSLASH:
                case ASCII_CARET:
                case ASCII_GRAVE:
                case ASCII_OPEN_BRACE:
                case ASCII_VERTICAL_BAR:
                case ASCII_CLOSE_BRACE:
                case ASCII_DELETE:
                    valid = false;
                    break byte_loop;
                default:
                    break;
                }
            }
        }

        return valid;
    }

    private static boolean percentEncoded(
        final byte followedFirstByte,
        final byte followedSecondByte)
    {
        boolean percentEncoded = true;

        if (followedFirstByte < ASCII_ZERO || followedFirstByte > ASCII_NINE)
        {
            percentEncoded = false;
        }

        if ((followedSecondByte < ASCII_ZERO || followedSecondByte > ASCII_NINE) &&
            (followedSecondByte < ASCII_UPPERCASE_A || followedSecondByte > ASCII_UPPERCASE_F) &&
            (followedSecondByte < ASCII_LOWERCASE_A || followedSecondByte > ASCII_LOWERCASE_F))
        {
            percentEncoded = false;
        }
        return percentEncoded;
    }

    private HttpUtil()
    {
        // utility class, no instances
    }
}
