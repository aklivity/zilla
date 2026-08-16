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
package io.aklivity.zilla.runtime.common.avro;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * The metadata travelling alongside a datum, addressed by name rather than by position within the datum.
 * <p>
 * An {@code AvroEnvelope} is a named, ordered, repeatable list of byte values and nothing more: it never
 * interprets what any name means, and whether a name occurs zero, one, or many times is entirely up to
 * whatever populates and consumes it. Its contents are not part of the datum, so nothing an
 * {@link AvroStream} pipeline parses, validates, or generates reads or writes it — only a stage that asks
 * for it does.
 * </p>
 * <p>
 * An envelope is supplied once, at {@link AvroStream#envelope(AvroEnvelope)} time, so the pipeline binds
 * to it as it is assembled rather than receiving it again per datum. The supplier owns its lifecycle and
 * decides the scope its contents describe, clearing or repointing them through its own type as that scope
 * turns over. A pipeline reads and writes whatever the envelope holds while it runs and never resets it.
 * </p>
 * <p>
 * An envelope is confined to the same single thread as the pipeline it is supplied to.
 * </p>
 *
 * @see AvroController#envelope()
 */
public interface AvroEnvelope
{
    /**
     * Empty envelope, in force when no envelope is supplied to a pipeline. It reads as empty and discards
     * what is written to it, so a stage reaches for the envelope the same way whether or not one is
     * backing it.
     */
    AvroEnvelope NONE = new AvroEnvelope()
    {
        @Override
        public int count(
            String name)
        {
            return 0;
        }

        @Override
        public DirectBufferEx get(
            String name,
            int index)
        {
            return null;
        }

        @Override
        public void set(
            String name,
            DirectBufferEx value)
        {
        }
    };

    /**
     * Returns how many values the envelope holds under {@code name}.
     *
     * @param name  the name to count the values of
     * @return the number of values held under {@code name}, {@code 0} when the name is absent
     */
    int count(
        String name);

    /**
     * Returns the value held under {@code name} at {@code index}, where the values under one name are
     * ordered by the {@link #set(String, DirectBufferEx)} calls that added them and {@code index} runs
     * from {@code 0} to {@link #count(String)} exclusive.
     * <p>
     * The buffer returned is a non-owning view valid only while the envelope is in scope; a consumer that
     * needs the bytes beyond that must copy them out.
     * </p>
     *
     * @param name   the name to read a value of
     * @param index  the position of the value among those held under {@code name}
     * @return the value at that position, or {@code null} when the name holds no value there
     */
    DirectBufferEx get(
        String name,
        int index);

    /**
     * Adds {@code value} to the values held under {@code name}, so a repeated call under one name adds a
     * further occurrence rather than overwriting the one before it — {@link #count(String)} grows by one
     * per call and each value stays retrievable by its own {@link #get(String, int)} index.
     * <p>
     * The bytes are copied out before the call returns, so the caller may reuse {@code value} immediately
     * afterwards.
     * </p>
     *
     * @param name   the name to add a value under
     * @param value  the value bytes to add
     */
    void set(
        String name,
        DirectBufferEx value);
}
