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
package io.aklivity.zilla.runtime.common.lang;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.lang.foreign.MemorySegment;

/**
 * A {@link StringView} over raw UTF-8 bytes, which carry no escapes. A transfer hands over the bytes of the chars
 * as they are, so a writer that needs an encoding of its own reads the chars and encodes them itself.
 * <p>
 * Bytes that are not well-formed UTF-8 read as U+FFFD, a char for each byte that is not part of a well-formed
 * sequence.
 */
final class Utf8StringView implements StringView
{
    private static final char REPLACEMENT = '\ufffd';

    private MemorySegment segment;
    private long offset;
    private long limit;
    private int length;
    private boolean ascii;

    private int cursorChar;
    private long position;
    private char pending;
    private char previous;

    @Override
    public Utf8StringView wrap(
        MemorySegment segment,
        long offset,
        int length)
    {
        this.segment = segment;
        this.offset = offset;
        this.limit = offset + length;

        scan();

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
        if (index < 0 || index >= length)
        {
            throw new IndexOutOfBoundsException(index);
        }

        final char c;

        if (ascii)
        {
            c = (char) segment.get(JAVA_BYTE, offset + index);
        }
        else
        {
            if (index < cursorChar - 1)
            {
                restart();
            }

            while (cursorChar <= index)
            {
                decodeNext();
            }

            c = previous;
        }

        return c;
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

        final CharSequence chars;

        if (ascii)
        {
            chars = new String(segment.asSlice(offset + start, end - start).toArray(JAVA_BYTE), UTF_8);
        }
        else
        {
            final StringBuilder builder = new StringBuilder(end - start);

            for (int index = start; index < end; index++)
            {
                builder.append(charAt(index));
            }

            chars = builder.toString();
        }

        return chars;
    }

    @Override
    public String toString()
    {
        return subSequence(0, length).toString();
    }

    @Override
    public int transferLength(
        int fromChar,
        int toChar)
    {
        check(fromChar, toChar);

        return (int) (boundary(toChar) - boundary(fromChar));
    }

    @Override
    public int transferTo(
        int fromChar,
        int toChar,
        TransferTarget target)
    {
        check(fromChar, toChar);

        final long start = boundary(fromChar);
        final long end = boundary(toChar);
        final int transferred = (int) (end - start);

        if (transferred > 0)
        {
            target.accept(segment, start, transferred);
        }

        return transferred;
    }

    private void check(
        int fromChar,
        int toChar)
    {
        if (fromChar < 0 || toChar > length || fromChar > toChar)
        {
            throw new IndexOutOfBoundsException(fromChar + ", " + toChar);
        }
    }

    private void scan()
    {
        boolean ascii = true;

        for (long index = offset; ascii && index < limit; index++)
        {
            ascii = segment.get(JAVA_BYTE, index) >= 0;
        }

        this.ascii = ascii;

        if (ascii)
        {
            this.length = (int) (limit - offset);
        }
        else
        {
            restart();

            while (position < limit || pending != 0)
            {
                decodeNext();
            }

            this.length = cursorChar;
            restart();
        }
    }

    private long boundary(
        int charIndex)
    {
        final long boundary;

        if (ascii)
        {
            boundary = offset + charIndex;
        }
        else
        {
            if (charIndex < cursorChar)
            {
                restart();
            }

            while (cursorChar < charIndex)
            {
                decodeNext();
            }

            assert pending == 0 : "index inside a character: " + charIndex;

            boundary = cursorChar == 0 ? offset : position;
        }

        return boundary;
    }

    private void restart()
    {
        cursorChar = 0;
        position = offset;
        pending = 0;
        previous = 0;
    }

    private void decodeNext()
    {
        if (pending != 0)
        {
            previous = pending;
            pending = 0;
        }
        else
        {
            final int lead = segment.get(JAVA_BYTE, position) & 0xff;
            final int continuations = continuations(lead);

            if (continuations == 0)
            {
                previous = (char) lead;
                position++;
            }
            else if (continuations < 0 || !continued(position + 1, continuations))
            {
                previous = REPLACEMENT;
                position++;
            }
            else
            {
                int codePoint = lead & (0x3f >> continuations);

                for (int continuation = 1; continuation <= continuations; continuation++)
                {
                    codePoint = codePoint << 6 | segment.get(JAVA_BYTE, position + continuation) & 0x3f;
                }

                position += continuations + 1;

                if (codePoint >= Character.MIN_SUPPLEMENTARY_CODE_POINT)
                {
                    previous = Character.highSurrogate(codePoint);
                    pending = Character.lowSurrogate(codePoint);
                }
                else
                {
                    previous = (char) codePoint;
                }
            }
        }

        cursorChar++;
    }

    private static int continuations(
        int lead)
    {
        final int continuations;

        if (lead < 0x80)
        {
            continuations = 0;
        }
        else if (lead < 0xc2 || lead >= 0xf5)
        {
            continuations = -1;
        }
        else
        {
            continuations = lead < 0xe0 ? 1 : lead < 0xf0 ? 2 : 3;
        }

        return continuations;
    }

    private boolean continued(
        long from,
        int count)
    {
        boolean continued = from + count <= limit;

        for (long index = from; continued && index < from + count; index++)
        {
            continued = (segment.get(JAVA_BYTE, index) & 0xc0) == 0x80;
        }

        return continued;
    }
}
