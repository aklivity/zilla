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
package io.aklivity.zilla.runtime.common.json;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

/**
 * The decoded chars of a string token that are read in place from the bytes it was parsed from, so a caller
 * that reads the chars never copies them, and a caller that forwards the token copies its encoded bytes
 * exactly as they were written.
 * <p>
 * {@link JsonSource#getStringView()} and {@link JsonParserEx#getStringView()} return one of these for a
 * string value that was parsed whole from the input window. A string that spans windows, an object key,
 * and a view that has been partly consumed are plain char views, so a caller checks with {@code instanceof}
 * and otherwise reads the chars.
 * <p>
 * The chars are the string's value, so an escape such as {@code \n} is one char. The encoded form is the
 * string as written between its quotes, so it keeps its escapes and its multi-byte characters as they were,
 * and the bytes a caller copies are valid in a JSON string without being escaped again.
 * <p>
 * A char index addresses a position in the chars, and every index handed to {@link #encodedLength(int, int)}
 * and {@link #transferTo(int, int, MutableDirectBufferEx, int)} must fall between two units of the encoded
 * form. A unit is one char, except a raw four-byte UTF-8 sequence, which is two chars that cannot be split.
 * The view is valid only while the parser has not advanced and the input bytes are unchanged.
 */
public interface JsonStringView extends CharSequence
{
    /**
     * Returns the number of encoded bytes of the chars in {@code [fromChar, toChar)}.
     *
     * @param fromChar  the index of the first char
     * @param toChar    the index after the last char
     *
     * @return the number of encoded bytes
     *
     * @throws IndexOutOfBoundsException  if the range is not within the view
     * @throws IllegalArgumentException   if an index falls inside a raw four-byte UTF-8 sequence
     */
    int encodedLength(
        int fromChar,
        int toChar);

    /**
     * Copies the encoded bytes of the chars in {@code [fromChar, toChar)} to {@code dst}, exactly as they were
     * written, without allocating.
     *
     * @param fromChar   the index of the first char
     * @param toChar     the index after the last char
     * @param dst        the buffer to copy to
     * @param dstOffset  the offset in {@code dst} to copy to
     *
     * @return the number of bytes copied, which is {@link #encodedLength(int, int)}
     *
     * @throws IndexOutOfBoundsException  if the range is not within the view
     * @throws IllegalArgumentException   if an index falls inside a raw four-byte UTF-8 sequence
     */
    int transferTo(
        int fromChar,
        int toChar,
        MutableDirectBufferEx dst,
        int dstOffset);
}
