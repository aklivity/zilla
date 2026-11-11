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
 * A {@link CharSequence} whose chars can also be transferred to a {@link TransferTarget} as the bytes they are
 * written as, so a consumer that forwards the chars does not decode them to {@code char}s and encode them again.
 * <p>
 * The transfer of a range of chars is defined by the implementation, as the bytes the chars take where they are
 * written: it is the form a consumer of that implementation knows how to accept. A sequence that reads its chars
 * from the bytes they were parsed from transfers those bytes exactly as they were written, and a sequence that
 * does not transfers the bytes it encodes the chars as, so a {@code TransferTarget} is always given a complete
 * transfer and never needs to ask how the sequence came by it.
 * <p>
 * A char index addresses a position in the chars, and every index handed to {@link #transferLength(int, int)}
 * and {@link #transferTo(int, int, TransferTarget)} must fall between two units of the transfer. A unit is one
 * char, except where the bytes spell a code point with more than one char, which cannot be split.
 */
public interface CharSequenceEx extends CharSequence
{
    /**
     * Returns the number of bytes {@link #transferTo(int, int, TransferTarget)} transfers for the chars in
     * {@code [fromChar, toChar)}.
     *
     * @param fromChar  the index of the first char
     * @param toChar    the index after the last char
     *
     * @return the number of bytes in the transfer
     *
     * @throws IndexOutOfBoundsException  if the range is not within this sequence
     * @throws IllegalArgumentException   if an index falls inside a unit of the transfer
     */
    int transferLength(
        int fromChar,
        int toChar);

    /**
     * Transfers the chars in {@code [fromChar, toChar)} to {@code target}, in as many pieces as the
     * implementation needs, in order and without allocating.
     *
     * @param fromChar  the index of the first char
     * @param toChar    the index after the last char
     * @param target    the target to transfer to
     *
     * @return the number of bytes transferred, which is {@link #transferLength(int, int)}
     *
     * @throws IndexOutOfBoundsException  if the range is not within this sequence
     * @throws IllegalArgumentException   if an index falls inside a unit of the transfer
     */
    int transferTo(
        int fromChar,
        int toChar,
        TransferTarget target);

    /**
     * Accepts the pieces of a transfer. A piece is valid only for the duration of the call.
     */
    interface TransferTarget
    {
        /**
         * Accepts {@code length} bytes of the transfer, starting at {@code offset} of {@code bytes}.
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
