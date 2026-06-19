/*
 * Copyright 2021-2024 Aklivity Inc.
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

/**
 * The result of a single {@link ModelPipeline#transform} call.
 * <p>
 * A {@code ModelStatus} is a mutable holder owned by, and reused across every {@code transform}
 * call of, a single {@link ModelPipeline}. The caller reads its fields immediately after each
 * call and must not retain the instance beyond the next {@code transform}.
 * </p>
 *
 * @see ModelPipeline
 */
public final class ModelStatus
{
    /**
     * The outcome of a {@link ModelPipeline#transform} call.
     */
    public enum Kind
    {
        /** progress was made; call {@code transform} again with the unconsumed input */
        OK,
        /** the output buffer filled before the value completed; drain it and call {@code transform} again */
        OVERFLOW,
        /** the input was consumed before the value completed; supply more input on the next call */
        UNDERFLOW,
        /** the current value completed and was accepted */
        COMPLETE,
        /** the current value was rejected; the stream should be reset */
        REJECTED
    }

    private Kind kind;
    private int consumed;
    private int produced;

    public Kind kind()
    {
        return kind;
    }

    public int consumed()
    {
        return consumed;
    }

    public int produced()
    {
        return produced;
    }

    /**
     * Updates this status in place and returns it, for an implementation to report the outcome of a
     * {@link ModelPipeline#transform} call without allocating.
     *
     * @param kind      the outcome
     * @param consumed  the number of input bytes consumed
     * @param produced  the number of output bytes produced
     * @return this status
     */
    public ModelStatus set(
        Kind kind,
        int consumed,
        int produced)
    {
        this.kind = kind;
        this.consumed = consumed;
        this.produced = produced;
        return this;
    }
}
