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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.lang.foreign.MemorySegment;
import java.nio.charset.Charset;

/**
 * A {@link CharSequenceEx} over the encoded bytes of a string in memory, so the chars are read in place and nothing
 * is copied or allocated. A {@link #transferTo transfer} hands over the bytes of the chars as they are held.
 * <p>
 * The chars are read in order, and a char just read can be read again. Reading further back starts again from the
 * start of the value. The view is a flyweight that is reused for every value and is valid only while the memory it
 * wraps is unchanged.
 */
public interface StringView extends CharSequenceEx
{
    /**
     * Points this view at the encoded string in {@code segment} from {@code offset} for {@code length} bytes.
     *
     * @param segment  the memory that holds the string
     * @param offset   the offset in {@code segment} of the first byte
     * @param length   the number of bytes of the encoded string
     *
     * @return this view
     */
    StringView wrap(
        MemorySegment segment,
        long offset,
        int length);

    /**
     * Creates a view over strings encoded as {@code charset}.
     *
     * @param charset  the charset the bytes are encoded as, which can only be UTF-8
     *
     * @return a view, to be wrapped over a string
     *
     * @throws IllegalArgumentException  if the charset is not UTF-8
     */
    static StringView of(
        Charset charset)
    {
        if (!UTF_8.equals(charset))
        {
            throw new IllegalArgumentException("unsupported charset: " + charset);
        }

        return new Utf8StringView();
    }
}
