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
package io.aklivity.zilla.runtime.common.avro.internal.json;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A reusable {@link CharSequence} backed by a growable {@code char[]}, filled in place by decoding raw
 * UTF-8 bytes ({@link #utf8}) or base64-encoding raw bytes ({@link #base64}). Letting the
 * {@link io.aklivity.zilla.runtime.common.json.JsonGeneratorEx} write through this view avoids the per-value
 * {@code String} a {@code getString} / {@code Base64.encodeToString} would allocate; the backing array grows
 * only when a value exceeds the largest seen so far. Not thread-safe; one per worker thread.
 */
final class CharText implements CharSequence
{
    private static final char[] BASE64 =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private final UnsafeBuffer wrapper;

    private char[] chars;
    private int length;

    CharText(
        int initialCapacity)
    {
        this.chars = new char[initialCapacity];
        this.wrapper = new UnsafeBuffer(0, 0);
    }

    void utf8(
        byte[] source,
        int length)
    {
        wrapper.wrap(source, 0, length);
        utf8(wrapper, 0, length);
    }

    void utf8(
        DirectBuffer source,
        int offset,
        int length)
    {
        ensure(length);
        int count = 0;
        int index = offset;
        int limit = offset + length;
        while (index < limit)
        {
            int b0 = source.getByte(index++) & 0xff;
            int cp;
            if (b0 < 0x80)
            {
                cp = b0;
            }
            else if (b0 < 0xe0)
            {
                cp = ((b0 & 0x1f) << 6) | (source.getByte(index++) & 0x3f);
            }
            else if (b0 < 0xf0)
            {
                cp = ((b0 & 0x0f) << 12) | ((source.getByte(index++) & 0x3f) << 6) | (source.getByte(index++) & 0x3f);
            }
            else
            {
                cp = ((b0 & 0x07) << 18) | ((source.getByte(index++) & 0x3f) << 12) |
                    ((source.getByte(index++) & 0x3f) << 6) | (source.getByte(index++) & 0x3f);
            }
            if (cp > 0xffff)
            {
                ensure(count + 2);
                cp -= 0x10000;
                chars[count++] = (char) (0xd800 + (cp >> 10));
                chars[count++] = (char) (0xdc00 + (cp & 0x3ff));
            }
            else
            {
                chars[count++] = (char) cp;
            }
        }
        this.length = count;
    }

    void number(
        long value)
    {
        ensure(20);
        int count;
        if (value == 0)
        {
            chars[0] = '0';
            count = 1;
        }
        else
        {
            boolean negative = value < 0;
            long magnitude = value;
            int digits = 0;
            for (long scan = value; scan != 0; scan /= 10)
            {
                digits++;
            }
            count = negative ? digits + 1 : digits;
            int index = count;
            while (magnitude != 0)
            {
                int digit = (int) (magnitude % 10);
                digit = digit < 0 ? -digit : digit;
                chars[--index] = (char) ('0' + digit);
                magnitude /= 10;
            }
            if (negative)
            {
                chars[0] = '-';
            }
        }
        this.length = count;
    }

    void base64(
        byte[] source,
        int length)
    {
        ensure(4 * ((length + 2) / 3));
        int count = 0;
        int index = 0;
        while (index + 3 <= length)
        {
            int word = ((source[index] & 0xff) << 16) | ((source[index + 1] & 0xff) << 8) | (source[index + 2] & 0xff);
            index += 3;
            chars[count++] = BASE64[(word >> 18) & 0x3f];
            chars[count++] = BASE64[(word >> 12) & 0x3f];
            chars[count++] = BASE64[(word >> 6) & 0x3f];
            chars[count++] = BASE64[word & 0x3f];
        }
        int remaining = length - index;
        if (remaining == 1)
        {
            int word = (source[index] & 0xff) << 16;
            chars[count++] = BASE64[(word >> 18) & 0x3f];
            chars[count++] = BASE64[(word >> 12) & 0x3f];
            chars[count++] = '=';
            chars[count++] = '=';
        }
        else if (remaining == 2)
        {
            int word = ((source[index] & 0xff) << 16) | ((source[index + 1] & 0xff) << 8);
            chars[count++] = BASE64[(word >> 18) & 0x3f];
            chars[count++] = BASE64[(word >> 12) & 0x3f];
            chars[count++] = BASE64[(word >> 6) & 0x3f];
            chars[count++] = '=';
        }
        this.length = count;
    }

    @Override
    public int length()
    {
        return length;
    }

    @Override
    public char charAt(
        int index)
    {
        return chars[index];
    }

    @Override
    public CharSequence subSequence(
        int start,
        int end)
    {
        return new String(chars, start, end - start);
    }

    @Override
    public String toString()
    {
        return new String(chars, 0, length);
    }

    private void ensure(
        int capacity)
    {
        if (capacity > chars.length)
        {
            char[] grown = new char[Math.max(chars.length * 2, capacity)];
            System.arraycopy(chars, 0, grown, 0, length);
            chars = grown;
        }
    }
}
