/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.common.agrona.buffer;

import java.lang.foreign.MemorySegment;

import org.agrona.MutableDirectBuffer;

public interface MutableDirectBufferEx extends MutableDirectBuffer, DirectBufferEx
{
    void putBytes(
        int index,
        DirectBufferEx srcBuffer,
        int srcIndex,
        int length);

    void putBytes(
        int index,
        MemorySegment srcSegment,
        int srcIndex,
        int length);

    /**
     * Encodes the chars of {@code value} in {@code [from, to)} as UTF-8 at {@code index}, with no length prefix,
     * and returns the number of bytes written, which is {@link #utf8Length(CharSequence, int, int)} of the same
     * range. A surrogate pair is encoded as one code point; a lone surrogate, including a high surrogate whose
     * pair is cut by {@code to}, is encoded as U+FFFD. The caller ensures that the range fits.
     */
    int putStringWithoutLengthUtf8(
        int index,
        CharSequence value,
        int from,
        int to);

    /**
     * The number of bytes {@link #putStringWithoutLengthUtf8(int, CharSequence, int, int)} writes for the chars of
     * {@code value} in {@code [from, to)}.
     */
    static int utf8Length(
        CharSequence value,
        int from,
        int to)
    {
        int length = 0;
        int index = from;

        while (index < to)
        {
            final char c = value.charAt(index++);

            if (c < 0x80)
            {
                length++;
            }
            else if (c < 0x800)
            {
                length += 2;
            }
            else if (Character.isHighSurrogate(c) && index < to && Character.isLowSurrogate(value.charAt(index)))
            {
                length += 4;
                index++;
            }
            else
            {
                length += 3;
            }
        }

        return length;
    }

    static int putUtf8(
        MutableDirectBuffer buffer,
        int index,
        CharSequence value,
        int from,
        int to)
    {
        int position = index;
        int next = from;

        while (next < to)
        {
            final char c = value.charAt(next++);

            if (c < 0x80)
            {
                buffer.putByte(position++, (byte) c);
            }
            else if (c < 0x800)
            {
                buffer.putByte(position++, (byte) (0xC0 | c >> 6));
                buffer.putByte(position++, (byte) (0x80 | c & 0x3F));
            }
            else if (Character.isHighSurrogate(c) && next < to && Character.isLowSurrogate(value.charAt(next)))
            {
                final int codePoint = Character.toCodePoint(c, value.charAt(next++));
                buffer.putByte(position++, (byte) (0xF0 | codePoint >> 18));
                buffer.putByte(position++, (byte) (0x80 | codePoint >> 12 & 0x3F));
                buffer.putByte(position++, (byte) (0x80 | codePoint >> 6 & 0x3F));
                buffer.putByte(position++, (byte) (0x80 | codePoint & 0x3F));
            }
            else
            {
                final char encoded = Character.isSurrogate(c) ? '\ufffd' : c;
                buffer.putByte(position++, (byte) (0xE0 | encoded >> 12));
                buffer.putByte(position++, (byte) (0x80 | encoded >> 6 & 0x3F));
                buffer.putByte(position++, (byte) (0x80 | encoded & 0x3F));
            }
        }

        return position - index;
    }
}
