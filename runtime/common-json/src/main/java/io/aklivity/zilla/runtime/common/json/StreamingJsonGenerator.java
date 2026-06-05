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
package io.aklivity.zilla.runtime.common.json;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * A streaming, compact JSON generator that writes directly into a {@link MutableDirectBuffer}
 * with no intermediate DOM and no per-call allocation. Structural separators ({@code ,} and
 * {@code :}) and quoting are inserted automatically from an internal context stack; output is
 * emitted in source order with no insignificant whitespace.
 * <p>
 * Reuse a single instance per worker thread: {@link #wrap(MutableDirectBuffer, int)} resets the
 * generator over a target region, then drive it with the {@code startObject}/{@code startArray}/
 * {@code key}/{@code *Value}/{@code end} methods. {@link #length()} reports the bytes written.
 */
public final class StreamingJsonGenerator
{
    private static final int MAX_DEPTH = 64;
    private static final byte[] HEX = "0123456789abcdef".getBytes();

    private final boolean[] inArray = new boolean[MAX_DEPTH];
    private final boolean[] hasMembers = new boolean[MAX_DEPTH];

    private MutableDirectBuffer buffer;
    private int offset;
    private int limit;
    private int depth;
    private boolean afterKey;

    public StreamingJsonGenerator wrap(
        MutableDirectBuffer buffer,
        int offset)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.limit = offset;
        this.depth = 0;
        this.afterKey = false;
        return this;
    }

    public int length()
    {
        return limit - offset;
    }

    public StreamingJsonGenerator startObject()
    {
        preValue();
        putByte('{');
        push(false);
        return this;
    }

    public StreamingJsonGenerator startArray()
    {
        preValue();
        putByte('[');
        push(true);
        return this;
    }

    public StreamingJsonGenerator end()
    {
        depth--;
        putByte(inArray[depth] ? ']' : '}');
        return this;
    }

    public StreamingJsonGenerator key(
        String name)
    {
        if (hasMembers[depth - 1])
        {
            putByte(',');
        }
        hasMembers[depth - 1] = true;
        writeString(name);
        putByte(':');
        afterKey = true;
        return this;
    }

    public StreamingJsonGenerator stringValue(
        String value)
    {
        preValue();
        writeString(value);
        return this;
    }

    public StreamingJsonGenerator numberValue(
        String value)
    {
        preValue();
        writeAscii(value);
        return this;
    }

    public StreamingJsonGenerator booleanValue(
        boolean value)
    {
        preValue();
        writeAscii(value ? "true" : "false");
        return this;
    }

    public StreamingJsonGenerator nullValue()
    {
        preValue();
        writeAscii("null");
        return this;
    }

    public StreamingJsonGenerator rawValue(
        DirectBuffer source,
        int index,
        int length)
    {
        preValue();
        buffer.putBytes(limit, source, index, length);
        limit += length;
        return this;
    }

    private void push(
        boolean array)
    {
        inArray[depth] = array;
        hasMembers[depth] = false;
        depth++;
        afterKey = false;
    }

    private void preValue()
    {
        if (afterKey)
        {
            afterKey = false;
        }
        else if (depth > 0 && inArray[depth - 1])
        {
            if (hasMembers[depth - 1])
            {
                putByte(',');
            }
            hasMembers[depth - 1] = true;
        }
    }

    private void writeString(
        String value)
    {
        putByte('"');
        int index = 0;
        int length = value.length();
        while (index < length)
        {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            switch (codePoint)
            {
            case '"':
                putByte('\\');
                putByte('"');
                break;
            case '\\':
                putByte('\\');
                putByte('\\');
                break;
            case '\n':
                putByte('\\');
                putByte('n');
                break;
            case '\r':
                putByte('\\');
                putByte('r');
                break;
            case '\t':
                putByte('\\');
                putByte('t');
                break;
            case '\b':
                putByte('\\');
                putByte('b');
                break;
            case '\f':
                putByte('\\');
                putByte('f');
                break;
            default:
                if (codePoint < 0x20)
                {
                    writeUnicodeEscape(codePoint);
                }
                else
                {
                    writeUtf8(codePoint);
                }
                break;
            }
        }
        putByte('"');
    }

    private void writeUtf8(
        int codePoint)
    {
        if (codePoint < 0x80)
        {
            putByte(codePoint);
        }
        else if (codePoint < 0x800)
        {
            putByte(0xc0 | codePoint >> 6);
            putByte(0x80 | codePoint & 0x3f);
        }
        else if (codePoint < 0x10000)
        {
            putByte(0xe0 | codePoint >> 12);
            putByte(0x80 | codePoint >> 6 & 0x3f);
            putByte(0x80 | codePoint & 0x3f);
        }
        else
        {
            putByte(0xf0 | codePoint >> 18);
            putByte(0x80 | codePoint >> 12 & 0x3f);
            putByte(0x80 | codePoint >> 6 & 0x3f);
            putByte(0x80 | codePoint & 0x3f);
        }
    }

    private void writeUnicodeEscape(
        int codePoint)
    {
        putByte('\\');
        putByte('u');
        putByte(HEX[codePoint >> 12 & 0xf]);
        putByte(HEX[codePoint >> 8 & 0xf]);
        putByte(HEX[codePoint >> 4 & 0xf]);
        putByte(HEX[codePoint & 0xf]);
    }

    private void writeAscii(
        String value)
    {
        for (int index = 0; index < value.length(); index++)
        {
            putByte(value.charAt(index));
        }
    }

    private void putByte(
        int value)
    {
        buffer.putByte(limit, (byte) value);
        limit++;
    }
}
