/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal.util;

import org.agrona.DirectBuffer;

public final class BufferUtil
{
    public static int indexOfByte(
        DirectBuffer buffer,
        int offset,
        int limit,
        byte value)
    {
        for (int cursor = offset; cursor < limit; cursor++)
        {
            if (buffer.getByte(cursor) == value)
            {
                return cursor;
            }
        }

        return -1;
    }

    public static int limitOfBytes(
        DirectBuffer buffer,
        int offset,
        int limit,
        byte[] value)
    {
        int matchedBytes = 0;

        for (int cursor = offset; cursor < limit; cursor++)
        {
            if (buffer.getByte(cursor) != value[matchedBytes])
            {
                matchedBytes = 0;
                continue;
            }

            if (value.length == ++matchedBytes)
            {
                return cursor + 1;
            }
        }

        return -1;
    }

    public static int limitOfBytes(
            DirectBuffer fragment,
            int offset1,
            int limit1,
            DirectBuffer buffer,
            int offset2,
            int limit2,
            byte[] value)
    {
        int matchedBytes = 0;

        for (int cursor = offset1; cursor < limit1; cursor++)
        {
            if (fragment.getByte(cursor) != value[matchedBytes])
            {
                matchedBytes = 0;
                continue;
            }

            if (value.length == ++matchedBytes)
            {
                throw new IllegalArgumentException("Full match found in fragment buffer");
            }
        }

        for (int cursor = offset2; cursor < limit2; cursor++)
        {
            if (buffer.getByte(cursor) != value[matchedBytes])
            {
                matchedBytes = 0;
                continue;
            }

            if (value.length == ++matchedBytes)
            {
                return cursor + 1;
            }
        }

        return -1;
    }


    private BufferUtil()
    {
        // utility class, no instances
    }
}
