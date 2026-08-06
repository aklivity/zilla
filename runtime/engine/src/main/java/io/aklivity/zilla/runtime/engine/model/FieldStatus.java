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
 * The outcome of a single {@link ModelTransform#transform} or {@link ModelSink#transform} call.
 * <p>
 * Unlike {@link ModelStatus}, which reports the progress of a whole {@link ModelPipeline} call over a
 * buffer, a {@code FieldStatus} reports the progress of one field event through a transform chain. The
 * format adapter driving the chain maps it onto its own pipeline status.
 * </p>
 *
 * @see ModelTransform
 * @see ModelSink
 */
public enum FieldStatus
{
    /** the event was consumed; the format adapter advances to the next */
    ADVANCED,
    /** the bounded output filled: drain it, then re-deliver the event via {@link ModelTransform#resume} */
    SUSPENDED,
    /** the value was rejected; the format adapter abandons its output */
    REJECTED
}
