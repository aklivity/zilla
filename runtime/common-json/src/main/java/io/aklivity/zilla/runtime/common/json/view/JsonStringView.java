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
package io.aklivity.zilla.runtime.common.json.view;

import java.lang.foreign.MemorySegment;
import java.nio.ByteOrder;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.lang.CharSequenceEx;

/**
 * A {@link CharSequenceEx} over the chars of a JSON string, whose {@link #transferTo transfer} hands over the bytes of
 * the string in the form the view observed them, and whose {@link #kind() kind} names that form, so a writer can copy
 * the bytes as they are when they are the form it writes, and read the chars and encode them itself when they are not.
 */
public interface JsonStringView extends CharSequenceEx
{
    /**
     * The form of the bytes that a transfer hands over.
     */
    enum Kind
    {
        /**
         * The body of a JSON string as it was written between its quotes, with its escapes in place.
         */
        ENCODED,

        /**
         * The body of a JSON string that was read out of another string, so the bytes carry the escapes of the string
         * that holds it as well as its own.
         */
        ESCAPED,

        /**
         * The UTF-8 bytes of the chars with no escapes, as for a value that was not read from a document.
         */
        DECODED
    }

    /**
     * @return the form of the bytes that {@link #transferTo} hands over
     */
    Kind kind();

    /**
     * Copies the chars from {@code from} that are printable ASCII other than a quote or a backslash, which a
     * writer emits as they are, into {@code target}, stopping at the first char that is not, at the end of the
     * view, or after {@code max} chars.
     *
     * @return the number of chars copied
     */
    default int copyPlain(
        int from,
        int max,
        byte[] target)
    {
        final int limit = Math.min(max, length() - from);
        int copied = 0;

        while (copied < limit)
        {
            final char c = charAt(from + copied);

            if (c < 0x20 || c >= 0x80 || c == '"' || c == '\\')
            {
                break;
            }

            target[copied++] = (byte) c;
        }

        return copied;
    }

    /**
     * A {@link CharSequence} over the bytes of a JSON string as they were written between its quotes, so the
     * decoded chars are read in place and never copied.
     * <p>
     * Each unit of the encoded form is one UTF-16 char, except a raw four-byte UTF-8 sequence, which is two (a
     * surrogate pair). A unit is one ASCII byte, a simple escape ({@code \n}), a unicode escape ({@code é}, six
     * bytes, and each half of an escaped surrogate pair is its own unit), or a UTF-8 sequence.
     * <p>
     * The view decodes the value a short run of chars at a time into a small array, so reading the chars in order
     * decodes each unit once and costs a lookup per char; reading backwards starts again from the start of the
     * value. A stretch of plain ASCII, with no escape, is widened a word at a time, so the bytes are read from the
     * buffer eight at a time, and only the escapes and the multi-byte characters are decoded a unit at a time.
     * <p>
     * The view is a flyweight that is reused for every value and is valid only while the bytes it wraps are
     * unchanged. It assumes the bytes are a valid JSON string body, as the tokenizer has already checked them.
     */
    final class Encoded implements JsonStringView
    {
        @Override
        public Kind kind()
        {
            return Kind.ENCODED;
        }

        private static final int RUN = 64;
        private static final int STRETCH = Long.BYTES;
        private static final long ONES = 0x0101010101010101L;
        private static final long HIGHS = 0x8080808080808080L;
        private static final long BACKSLASHES = 0x5c5c5c5c5c5c5c5cL;
        private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

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

        public Encoded wrap(
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

        public Encoded wrap(
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
            final int position = index - runChar;
            final char result;

            if (position >= 0 && position < runLength)
            {
                result = run[position];
            }
            else if (index < 0 || index >= length)
            {
                throw new IndexOutOfBoundsException(index);
            }
            else
            {
                cover(index);
                result = run[index - runChar];
            }

            return result;
        }

        @Override
        public int copyPlain(
            int from,
            int max,
            byte[] target)
        {
            final int limit = Math.min(max, length - from);
            int copied = 0;

            while (copied < limit)
            {
                int position = from + copied - runChar;

                if (position < 0 || position >= runLength)
                {
                    cover(from + copied);
                    position = from + copied - runChar;
                }

                final int end = Math.min(runLength, position + limit - copied);
                final char[] run = this.run;
                int next = position;

                while (next < end)
                {
                    final char c = run[next];

                    if (c < 0x20 || c >= 0x80 || c == '"' || c == '\\')
                    {
                        break;
                    }

                    target[copied + next - position] = (byte) c;
                    next++;
                }

                copied += next - position;

                if (next < end)
                {
                    break;
                }
            }

            return copied;
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
        public boolean plain()
        {
            return plain;
        }

        @Override
        public int transferLength(
            int fromChar,
            int toChar)
        {
            check(fromChar, toChar);

            return byteOffsetOf(toChar) - byteOffsetOf(fromChar);
        }

        @Override
        public int transferTo(
            int fromChar,
            int toChar,
            TransferTarget target)
        {
            check(fromChar, toChar);

            final int start = byteOffsetOf(fromChar);
            final int end = byteOffsetOf(toChar);

            if (end > start)
            {
                target.accept(buffer.segment(), buffer.wrapAdjustment() + start, end - start);
            }

            return end - start;
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

        /**
         * The offset in the buffer of the unit that holds the char at {@code charIndex}: the start of the pair when the
         * char is the second half of a raw four-byte sequence.
         */
        public int floorOffsetOf(
            int charIndex)
        {
            return byteOffsetOf(charIndex, false);
        }

        private int byteOffsetOf(
            int charIndex)
        {
            return byteOffsetOf(charIndex, true);
        }

        private int byteOffsetOf(
            int charIndex,
            boolean strict)
        {
            int result;

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
                result = runByte;

                while (index < charIndex)
                {
                    final int c = buffer.getByte(result) & 0xff;

                    if (c == '\\')
                    {
                        result += buffer.getByte(result + 1) == 'u' ? 6 : 2;
                        index++;
                    }
                    else if (c < 0x80)
                    {
                        result++;
                        index++;
                    }
                    else
                    {
                        final int size = unitSize(c);

                        if (size == 4 && index + 1 == charIndex)
                        {
                            assert !strict : "char " + charIndex + " is inside a four-byte sequence";

                            index = charIndex;
                        }
                        else
                        {
                            result += size;
                            index += size == 4 ? 2 : 1;
                        }
                    }
                }
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
                final long word = index + STRETCH <= limit && count + STRETCH <= RUN ? plainWord(index) : 0L;

                if (word != 0L)
                {
                    widen(word, count);
                    count += STRETCH;
                    index += STRETCH;
                }
                else
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
            }

            runChar = nextChar;
            runByte = nextByte;
            runLength = count;
            nextChar += count;
            nextByte = index;
        }

        // the next eight bytes as a word, in byte order, or zero if any of them is not plain ASCII: a byte is plain
        // unless its high bit is set or it is a backslash, which is the byte that is zero once xor-ed with one, and
        // a plain word is never zero since a zero byte is not valid in a string
        private long plainWord(
            int index)
        {
            final long word = buffer.getLong(index);
            final long backslashes = word ^ BACKSLASHES;
            final boolean plain = ((word | (backslashes - ONES) & ~backslashes) & HIGHS) == 0L;

            return plain ? LITTLE_ENDIAN ? word : Long.reverseBytes(word) : 0L;
        }

        private void widen(
            long word,
            int count)
        {
            final char[] run = this.run;

            run[count] = (char) (word & 0xff);
            run[count + 1] = (char) (word >>> 8 & 0xff);
            run[count + 2] = (char) (word >>> 16 & 0xff);
            run[count + 3] = (char) (word >>> 24 & 0xff);
            run[count + 4] = (char) (word >>> 32 & 0xff);
            run[count + 5] = (char) (word >>> 40 & 0xff);
            run[count + 6] = (char) (word >>> 48 & 0xff);
            run[count + 7] = (char) (word >>> 56 & 0xff);
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

    /**
     * A {@link CharSequence} over the bytes of a JSON string inside the document that an escaped scope reads out of a
     * string, so the bytes are the string as it is written between its quotes, escaped twice: once by the string that
     * holds the document, and once by the string within the document. The chars are read in place, decoding both
     * layers as they are reached, and nothing is copied or allocated.
     * <p>
     * The chars are read in order, and a char just read can be read again. Reading further back starts again from the
     * start of the value. The bytes of a string within a scope carry the outer escapes, so a transfer hands them over
     * with those escapes in place.
     * <p>
     * The view is a flyweight that is reused for every value and is valid only while the bytes it wraps are unchanged.
     * It assumes the bytes are a valid string body, as the tokenizer has already checked them.
     */
    final class Escaped implements JsonStringView
    {
        private static final long ONES = 0x0101010101010101L;
        private static final long HIGHS = 0x8080808080808080L;
        private static final long CONTROLS = ONES * 0x20;
        private static final long QUOTES = ONES * '"';
        private static final long BACKSLASHES = ONES * '\\';

        private DirectBufferEx buffer;
        private int offset;
        private int limit;
        private int length;

        private int cursorChar;
        private int unitByte;
        private int position;
        private char pending;
        private char previous;
        private long unit;
        private int unitIndex;
        private int unitCount;

        public Escaped wrap(
            DirectBufferEx buffer,
            int offset,
            int byteLength,
            int chars)
        {
            this.buffer = buffer;
            this.offset = offset;
            this.limit = offset + byteLength;
            this.length = chars;

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
            if (index < 0 || index >= length)
            {
                throw new IndexOutOfBoundsException(index);
            }

            if (index < cursorChar - 1)
            {
                restart();
            }

            while (cursorChar <= index)
            {
                decodeNext();
            }

            return previous;
        }

        @Override
        public int copyPlain(
            int from,
            int max,
            byte[] target)
        {
            final int available = Math.min(max, length - from);
            int copied = 0;

            while (copied < available)
            {
                if (from + copied == cursorChar && pending == 0 && unitIndex == unitCount)
                {
                    while (copied + Long.BYTES <= available && position + Long.BYTES <= limit &&
                        printableWord(buffer.getLong(position)))
                    {
                        buffer.getBytes(position, target, copied, Long.BYTES);
                        position += Long.BYTES;
                        cursorChar += Long.BYTES;
                        copied += Long.BYTES;
                        previous = (char) (target[copied - 1] & 0xff);
                        unitByte = position - 1;
                    }
                }

                if (copied < available)
                {
                    final char c = charAt(from + copied);

                    if (c < 0x20 || c >= 0x80 || c == '"' || c == '\\')
                    {
                        break;
                    }

                    target[copied++] = (byte) c;
                }
            }

            return copied;
        }

        // whether all eight bytes are printable ASCII that is neither a quote nor a backslash, so that none starts an
        // escape at either level and each byte is one char
        private static boolean printableWord(
            long word)
        {
            final long controls = (word - CONTROLS) & ~word;
            final long quotes = word ^ QUOTES;
            final long slashes = word ^ BACKSLASHES;
            final long found = controls | (quotes - ONES) & ~quotes | (slashes - ONES) & ~slashes | word;

            return (found & HIGHS) == 0L;
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

        @Override
        public Kind kind()
        {
            return Kind.ESCAPED;
        }

        @Override
        public int transferLength(
            int fromChar,
            int toChar)
        {
            check(fromChar, toChar);

            final int start = boundary(fromChar);
            final int end = boundary(toChar);

            return end - start;
        }

        @Override
        public int transferTo(
            int fromChar,
            int toChar,
            TransferTarget target)
        {
            check(fromChar, toChar);

            final int start = boundary(fromChar);
            final int end = boundary(toChar);

            if (end > start)
            {
                target.accept(buffer.segment(), buffer.wrapAdjustment() + start, end - start);
            }

            return end - start;
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

        private int boundary(
            int charIndex)
        {
            if (charIndex < cursorChar)
            {
                restart();
            }

            while (cursorChar < charIndex)
            {
                decodeNext();
            }

            assert pending == 0 && unitIndex == unitCount : "index inside a character: " + charIndex;

            return cursorChar == 0 ? offset : position;
        }

        /**
         * The offset in the buffer of the unit that holds the char at {@code charIndex}: where the escape or the
         * character begins, and the start of the pair when the char is the second half of a surrogate pair.
         */
        public int floorOffsetOf(
            int charIndex)
        {
            if (charIndex < cursorChar - 1)
            {
                restart();
            }

            while (cursorChar <= charIndex)
            {
                decodeNext();
            }

            return unitByte;
        }

        private void restart()
        {
            cursorChar = 0;
            unitByte = offset;
            position = offset;
            pending = 0;
            previous = 0;
            unitIndex = 0;
            unitCount = 0;
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
                unitByte = position;

                final int b = inner();

                if (b == '\\')
                {
                    previous = escape();
                }
                else if (b < 0x80)
                {
                    previous = (char) b;
                }
                else
                {
                    final int codePoint = sequence(b);

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

        private char escape()
        {
            final int e = inner();
            final char c;

            if (e == 'u')
            {
                c = (char) (hex(inner()) << 12 | hex(inner()) << 8 | hex(inner()) << 4 | hex(inner()));
            }
            else
            {
                c = switch (e)
                {
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                default -> (char) e;
                };
            }

            return c;
        }

        private int sequence(
            int lead)
        {
            final int continuations = lead < 0xe0 ? 1 : lead < 0xf0 ? 2 : 3;

            int codePoint = lead & (0x3f >> continuations);

            for (int continuation = 0; continuation < continuations; continuation++)
            {
                codePoint = codePoint << 6 | inner() & 0x3f;
            }

            return codePoint;
        }

        // the next byte of the string within the document, which is the next byte of the string as it is written, or a
        // byte of the escape it spells
        private int inner()
        {
            final int b;

            if (unitIndex < unitCount)
            {
                b = JsonEscapeLens.byteAt(unit, unitIndex++) & 0xff;
            }
            else
            {
                final int c = buffer.getByte(position) & 0xff;

                if (c != '\\')
                {
                    position++;
                    b = c;
                }
                else
                {
                    unit = JsonEscapeLens.decode(buffer, position, limit, true);
                    position += JsonEscapeLens.size(unit);
                    unitCount = JsonEscapeLens.count(unit);
                    unitIndex = 1;
                    b = JsonEscapeLens.byteAt(unit, 0) & 0xff;
                }
            }

            return b;
        }

        private static int hex(
            int c)
        {
            return c <= '9' ? c - '0' : (c | 0x20) - 'a' + 10;
        }
    }

    /**
     * A {@link CharSequenceEx} over chars that were not read from the bytes they were written as, so it transfers them
     * as the body of a JSON string: the chars in UTF-8, with a quote, a backslash and a control char escaped, and a lone
     * surrogate, which has no UTF-8 form, written as U+FFFD.
     * <p>
     * The view is a flyweight that is reused for every sequence and holds the bytes of a transfer in an array it grows
     * to the largest it has been asked for.
     */
    final class Decoded implements JsonStringView
    {
        @Override
        public Kind kind()
        {
            return Kind.DECODED;
        }

        private static final int REPLACEMENT = 0xfffd;

        private CharSequence chars;
        private byte[] bytes = new byte[64];
        private MemorySegment segment = MemorySegment.ofArray(bytes);

        public Decoded wrap(
            CharSequence chars)
        {
            this.chars = chars;

            return this;
        }

        @Override
        public int length()
        {
            return chars.length();
        }

        @Override
        public char charAt(
            int index)
        {
            return chars.charAt(index);
        }

        @Override
        public CharSequence subSequence(
            int start,
            int end)
        {
            return chars.subSequence(start, end);
        }

        @Override
        public String toString()
        {
            return chars.toString();
        }

        @Override
        public int transferLength(
            int fromChar,
            int toChar)
        {
            check(fromChar, toChar);

            int length = 0;

            for (int index = fromChar; index < toChar; )
            {
                final int codePoint = codePointAt(index, toChar);

                length += width(codePoint);
                index += codePoint >= 0x10000 ? 2 : 1;
            }

            return length;
        }

        @Override
        public int transferTo(
            int fromChar,
            int toChar,
            TransferTarget target)
        {
            final int length = transferLength(fromChar, toChar);

            if (length > bytes.length)
            {
                bytes = new byte[Math.max(length, bytes.length << 1)];
                segment = MemorySegment.ofArray(bytes);
            }

            int position = 0;

            for (int index = fromChar; index < toChar; )
            {
                final int codePoint = codePointAt(index, toChar);

                position = put(codePoint, position);
                index += codePoint >= 0x10000 ? 2 : 1;
            }

            target.accept(segment, 0, position);

            return position;
        }

        private void check(
            int fromChar,
            int toChar)
        {
            if (fromChar < 0 || toChar > chars.length() || fromChar > toChar)
            {
                throw new IndexOutOfBoundsException(fromChar + ", " + toChar);
            }

            assert !splitsPair(fromChar, toChar) : "index inside a surrogate pair: " + fromChar + ", " + toChar;
        }

        private boolean splitsPair(
            int fromChar,
            int toChar)
        {
            return fromChar > 0 && fromChar < chars.length() &&
                Character.isLowSurrogate(chars.charAt(fromChar)) && Character.isHighSurrogate(chars.charAt(fromChar - 1)) ||
                toChar > 0 && toChar < chars.length() &&
                Character.isHighSurrogate(chars.charAt(toChar - 1)) && Character.isLowSurrogate(chars.charAt(toChar));
        }

        private boolean isPair(
            int index,
            int limit)
        {
            return Character.isHighSurrogate(chars.charAt(index)) && index + 1 < limit &&
                Character.isLowSurrogate(chars.charAt(index + 1));
        }

        private int codePointAt(
            int index,
            int limit)
        {
            final char c = chars.charAt(index);
            final int codePoint;

            if (isPair(index, limit))
            {
                codePoint = Character.toCodePoint(c, chars.charAt(index + 1));
            }
            else if (Character.isSurrogate(c))
            {
                codePoint = REPLACEMENT;
            }
            else
            {
                codePoint = c;
            }

            return codePoint;
        }

        private static int width(
            int codePoint)
        {
            final int width;

            if (codePoint < 0x80)
            {
                width = 1;
            }
            else if (codePoint < 0x800)
            {
                width = 2;
            }
            else if (codePoint < 0x10000)
            {
                width = 3;
            }
            else
            {
                width = 4;
            }

            return width;
        }

        private int put(
            int codePoint,
            int position)
        {
            int next = position;

            if (codePoint < 0x80)
            {
                bytes[next++] = (byte) codePoint;
            }
            else if (codePoint < 0x800)
            {
                bytes[next++] = (byte) (0xc0 | codePoint >> 6);
                bytes[next++] = (byte) (0x80 | codePoint & 0x3f);
            }
            else if (codePoint < 0x10000)
            {
                bytes[next++] = (byte) (0xe0 | codePoint >> 12);
                bytes[next++] = (byte) (0x80 | codePoint >> 6 & 0x3f);
                bytes[next++] = (byte) (0x80 | codePoint & 0x3f);
            }
            else
            {
                bytes[next++] = (byte) (0xf0 | codePoint >> 18);
                bytes[next++] = (byte) (0x80 | codePoint >> 12 & 0x3f);
                bytes[next++] = (byte) (0x80 | codePoint >> 6 & 0x3f);
                bytes[next++] = (byte) (0x80 | codePoint & 0x3f);
            }

            return next;
        }
    }
}
