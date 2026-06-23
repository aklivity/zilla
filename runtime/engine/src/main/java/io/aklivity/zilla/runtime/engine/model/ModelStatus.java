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
 * The outcome of a single {@link ModelPipeline#transform} call, reported on a {@link ModelPipelineResult}.
 *
 * @see ModelPipeline
 * @see ModelPipelineResult
 */
public enum ModelStatus
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
