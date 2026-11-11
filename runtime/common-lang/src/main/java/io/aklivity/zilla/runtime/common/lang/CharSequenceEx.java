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

import java.lang.foreign.MemorySegment;

/**
 * A {@link CharSequence} whose chars can also be handed to a {@link Target} as the bytes they are encoded as,
 * so a consumer that forwards the chars does not decode them to {@code char}s and encode them again.
 * <p>
 * The encoded form of a range of chars is defined by the implementation, as the form the chars take where they
 * are written: it is the form a consumer of that implementation knows how to accept. A view that reads the
 * chars from the bytes they were parsed from hands those bytes over exactly as they were written, and a view
 * that does not has to encode the chars itself, so a {@code Target} is always given a complete encoded form and
 * never needs to ask how the view came by it.
 * <p>
 * A char index addresses a position in the chars, and every index handed to {@link #encodedLength(int, int)}
 * and {@link #transferTo(int, int, Target)} must fall between two units of the encoded form. A unit is one
 * char, except where the encoding spells a code point with more than one char, which cannot be split.
 */
public interface CharSequenceEx extends CharSequence
{
    /**
     * Returns the number of bytes of the encoded form of the chars in {@code [fromChar, toChar)}.
     *
     * @param fromChar  the index of the first char
     * @param toChar    the index after the last char
     *
     * @return the number of encoded bytes
     *
     * @throws IndexOutOfBoundsException  if the range is not within this sequence
     * @throws IllegalArgumentException   if an index falls inside a unit of the encoded form
     */
    int encodedLength(
        int fromChar,
        int toChar);

    /**
     * Hands the encoded form of the chars in {@code [fromChar, toChar)} to {@code target}, in as many pieces as
     * the implementation needs, in order and without allocating.
     *
     * @param fromChar  the index of the first char
     * @param toChar    the index after the last char
     * @param target    the target to hand the encoded form to
     *
     * @return the number of encoded bytes handed over, which is {@link #encodedLength(int, int)}
     *
     * @throws IndexOutOfBoundsException  if the range is not within this sequence
     * @throws IllegalArgumentException   if an index falls inside a unit of the encoded form
     */
    int transferTo(
        int fromChar,
        int toChar,
        Target target);

    /**
     * Accepts the encoded form of chars. A piece is valid only for the duration of the call.
     */
    interface Target
    {
        /**
         * Accepts {@code length} bytes of the encoded form, starting at {@code offset} of {@code bytes}.
         *
         * @param bytes   the memory holding the bytes
         * @param offset  the offset of the first byte in {@code bytes}
         * @param length  the number of bytes
         */
        void accept(
            MemorySegment bytes,
            long offset,
            int length);
    }
}
