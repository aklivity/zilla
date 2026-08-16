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
package io.aklivity.zilla.runtime.engine.model;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * The metadata travelling alongside the message currently being transformed, addressed by name rather
 * than by position in the value.
 * <p>
 * A {@code ModelEnvelope} is a named, ordered, repeatable list of byte values and nothing more: it never
 * interprets what any name means, and whether a name occurs zero, one, or many times per message is
 * entirely up to whatever populates and consumes it. The caller supplying the envelope decides where its
 * contents come from on the way in and where they end up on the way out — typically the out-of-band
 * channel the caller's own transport offers — so a {@link ModelTransform} written against this interface
 * works unmodified wherever an envelope is supplied.
 * </p>
 * <p>
 * An envelope is scoped to one message and confined to the same single I/O thread as the pipeline
 * reading it.
 * </p>
 *
 * @see ModelController#envelope()
 */
public interface ModelEnvelope
{
    /**
     * Empty envelope, supplied when the caller offers no metadata channel for the current message. It
     * reads as empty and discards what is written to it, so a stage reaches for the envelope the same way
     * whether or not one is backing it.
     */
    ModelEnvelope NONE = new ModelEnvelope()
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
     * The buffer returned is a non-owning view valid only while the envelope is in scope for the current
     * message; a consumer that needs the bytes beyond that must copy them out.
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
