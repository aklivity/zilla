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
package io.aklivity.zilla.runtime.common.json.internal;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonStringView;

/**
 * A {@link CharSequence} over the bytes of a JSON string as they were written between its quotes, so the
 * decoded chars are read in place and never copied.
 * <p>
 * Each unit of the encoded form is one UTF-16 char, except a raw four-byte UTF-8 sequence, which is two (a
 * surrogate pair). A unit is one ASCII byte, a simple escape ({@code \n}), a unicode escape ({@code é}, six
 * bytes, and each half of an escaped surrogate pair is its own unit), or a UTF-8 sequence. A view with only
 * single ASCII bytes indexes the bytes directly. Any other view decodes a short run of chars at a time into a
 * small array, so reading the chars in order decodes each unit once and costs a lookup per char; reading
 * backwards decodes again from the start of the value.
 * <p>
 * The view is a flyweight that is reused for every value and is valid only while the bytes it wraps are
 * unchanged. It assumes the bytes are a valid JSON string body, as the tokenizer has already checked them.
 */
final class EncodedStringView implements JsonStringView
{
    private static final int RUN = 64;

    private char[] run;

    private DirectBufferEx buffer;
    private int offset;
    private int limit;
    private int length;
    private boolean plain;

    private int runChar;
    private int runByte;
    private int runLength;
    private int nextChar;
    private int nextByte;

    EncodedStringView wrap(
        DirectBufferEx buffer,
        int offset,
        int byteLength)
    {
        int chars = 0;
        boolean ascii = true;

        for (int index = offset, end = offset + byteLength; index < end; )
        {
            final int c = buffer.getByte(index) & 0xff;

            if (c == '\\')
            {
                ascii = false;
                index += buffer.getByte(index + 1) == 'u' ? 6 : 2;
                chars++;
            }
            else if (c < 0x80)
            {
                index++;
                chars++;
            }
            else
            {
                ascii = false;
                index += unitSize(c);
                chars += c >= 0xf0 ? 2 : 1;
            }
        }

        return wrap(buffer, offset, byteLength, chars, ascii);
    }

    EncodedStringView wrap(
        DirectBufferEx buffer,
        int offset,
        int byteLength,
        int chars,
        boolean plain)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.limit = offset + byteLength;
        this.length = chars;
        this.plain = plain;

        restart();

        return this;
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
        final char result;

        if (plain)
        {
            if (index < 0 || index >= length)
            {
                throw new IndexOutOfBoundsException(index);
            }

            result = (char) (buffer.getByte(offset + index) & 0xff);
        }
        else
        {
            final int position = index - runChar;

            if (position >= 0 && position < runLength)
            {
                result = run[position];
            }
            else
            {
                if (index < 0 || index >= length)
                {
                    throw new IndexOutOfBoundsException(index);
                }

                cover(index);
                result = run[index - runChar];
            }
        }

        return result;
    }

    @Override
    public CharSequence subSequence(
        int start,
        int end)
    {
        if (start < 0 || end > length || start > end)
        {
            throw new IndexOutOfBoundsException(start + ", " + end);
        }

        final StringBuilder chars = new StringBuilder(end - start);

        for (int index = start; index < end; index++)
        {
            chars.append(charAt(index));
        }

        return chars.toString();
    }

    @Override
    public String toString()
    {
        return subSequence(0, length).toString();
    }

    /**
     * Whether the encoded form is exactly the decoded chars: ASCII with no escapes.
     */
    boolean plain()
    {
        return plain;
    }

    @Override
    public int encodedLength(
        int fromChar,
        int toChar)
    {
        if (fromChar < 0 || toChar > length || fromChar > toChar)
        {
            throw new IndexOutOfBoundsException(fromChar + ", " + toChar);
        }

        return byteOffsetOf(toChar) - byteOffsetOf(fromChar);
    }

    @Override
    public int transferTo(
        int fromChar,
        int toChar,
        MutableDirectBufferEx dst,
        int dstOffset)
    {
        final int start = byteOffsetOf(fromChar);
        final int end = byteOffsetOf(toChar);

        dst.putBytes(dstOffset, buffer, start, end - start);

        return end - start;
    }

    private int byteOffsetOf(
        int charIndex)
    {
        final int result;

        if (charIndex == length)
        {
            result = limit;
        }
        else if (plain)
        {
            result = offset + charIndex;
        }
        else
        {
            cover(charIndex);

            int index = runChar;
            int position = runByte;

            while (index < charIndex)
            {
                final int c = buffer.getByte(position) & 0xff;

                if (c == '\\')
                {
                    position += buffer.getByte(position + 1) == 'u' ? 6 : 2;
                    index++;
                }
                else if (c < 0x80)
                {
                    position++;
                    index++;
                }
                else
                {
                    final int size = unitSize(c);

                    if (size == 4 && index + 1 == charIndex)
                    {
                        throw new IllegalArgumentException("char " + charIndex + " is inside a four-byte sequence");
                    }

                    position += size;
                    index += size == 4 ? 2 : 1;
                }
            }

            result = position;
        }

        return result;
    }

    private void cover(
        int charIndex)
    {
        if (charIndex < runChar)
        {
            restart();
        }

        while (charIndex >= nextChar)
        {
            decodeRun();
        }
    }

    private void restart()
    {
        runChar = 0;
        runByte = offset;
        runLength = 0;
        nextChar = 0;
        nextByte = offset;
    }

    private void decodeRun()
    {
        if (run == null)
        {
            run = new char[RUN + 1];
        }

        final DirectBufferEx buffer = this.buffer;
        final char[] run = this.run;
        final int limit = this.limit;

        int count = 0;
        int index = nextByte;

        while (count < RUN && index < limit)
        {
            final int c = buffer.getByte(index) & 0xff;

            if (c < 0x80 && c != '\\')
            {
                run[count++] = (char) c;
                index++;
            }
            else
            {
                index = decodeUnit(c, index, count);
                count += c >= 0xf0 ? 2 : 1;
            }
        }

        runChar = nextChar;
        runByte = nextByte;
        runLength = count;
        nextChar += count;
        nextByte = index;
    }

    private int decodeUnit(
        int c,
        int index,
        int count)
    {
        return c == '\\' ? decodeEscape(index, count) : decodeSequence(c, index, count);
    }

    private int decodeEscape(
        int index,
        int count)
    {
        final int e = buffer.getByte(index + 1);
        final int size;

        if (e == 'u')
        {
            run[count] = (char) (hex(buffer.getByte(index + 2)) << 12 |
                hex(buffer.getByte(index + 3)) << 8 |
                hex(buffer.getByte(index + 4)) << 4 |
                hex(buffer.getByte(index + 5)));
            size = 6;
        }
        else
        {
            run[count] = switch (e)
            {
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            default -> (char) e;
            };
            size = 2;
        }

        return index + size;
    }

    private int decodeSequence(
        int c,
        int index,
        int count)
    {
        final int size = unitSize(c);
        final int second = buffer.getByte(index + 1) & 0x3f;

        if (size == 2)
        {
            run[count] = (char) ((c & 0x1f) << 6 | second);
        }
        else if (size == 3)
        {
            run[count] = (char) ((c & 0x0f) << 12 | second << 6 | buffer.getByte(index + 2) & 0x3f);
        }
        else
        {
            final int codePoint = (c & 0x07) << 18 | second << 12 |
                (buffer.getByte(index + 2) & 0x3f) << 6 | buffer.getByte(index + 3) & 0x3f;

            run[count] = Character.highSurrogate(codePoint);
            run[count + 1] = Character.lowSurrogate(codePoint);
        }

        return index + size;
    }

    private static int unitSize(
        int lead)
    {
        return lead < 0xe0 ? 2 : lead < 0xf0 ? 3 : 4;
    }

    private static int hex(
        int c)
    {
        return c <= '9' ? c - '0' : (c | 0x20) - 'a' + 10;
    }
}
