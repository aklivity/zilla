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

/**
 * The result of a single {@link ModelPipeline#transform} call.
 * <p>
 * A {@code ModelPipelineResult} is a mutable holder owned by, and reused across every {@code transform}
 * call of, a single {@link ModelPipeline}. The caller reads its fields immediately after each call and
 * must not retain the instance beyond the next {@code transform}.
 * </p>
 *
 * @see ModelPipeline
 * @see ModelStatus
 */
public final class ModelPipelineResult
{
    private ModelStatus status;
    private int consumed;
    private int produced;

    public ModelStatus status()
    {
        return status;
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
     * Updates this result in place and returns it, for an implementation to report the outcome of a
     * {@link ModelPipeline#transform} call without allocating.
     *
     * @param status    the outcome
     * @param consumed  the number of input bytes consumed
     * @param produced  the number of output bytes produced
     * @return this result
     */
    public ModelPipelineResult set(
        ModelStatus status,
        int consumed,
        int produced)
    {
        this.status = status;
        this.consumed = consumed;
        this.produced = produced;
        return this;
    }
}
