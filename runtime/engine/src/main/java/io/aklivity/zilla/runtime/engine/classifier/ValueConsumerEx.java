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
package io.aklivity.zilla.runtime.engine.classifier;

import org.agrona.DirectBuffer;

/**
 * Receives the output of a value rewrite, distinguishing literal bytes from replacements.
 * <p>
 * Literal bytes are divisible: a run may be split at any point across calls. A replacement
 * is not: it is written whole or not at all, or the output would be corrupt.
 * </p>
 *
 * @see Anonymizer
 * @see Deanonymizer
 */
public interface ValueConsumerEx
{
    /**
     * Writes literal bytes.
     *
     * @param buffer  the buffer holding the bytes
     * @param index   the offset of the first byte
     * @param length  the number of bytes
     * @return the number of bytes accepted, which may be less than {@code length}
     */
    int write(
        DirectBuffer buffer,
        int index,
        int length);

    /**
     * Writes an indivisible replacement for a labelled span.
     *
     * @param label   the label alias the replacement stands for
     * @param buffer  the buffer holding the replacement bytes
     * @param index   the offset of the first byte
     * @param length  the number of bytes
     * @return {@code true} if the whole replacement was accepted, otherwise {@code false}
     */
    boolean writeSpan(
        String label,
        DirectBuffer buffer,
        int index,
        int length);
}
