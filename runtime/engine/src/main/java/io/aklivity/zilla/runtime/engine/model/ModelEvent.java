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
 * The event currency of a {@link ModelTransform} chain: the format-agnostic, per-field view of a value
 * as it flows through the model pipeline, framed by {@link #START_VALUE} and {@link #END_VALUE}.
 * <p>
 * A field arrives as a single {@link #FIELD} event carrying the whole field value, so a transform can
 * decide on the complete value without reassembling chunks. Its answer is expressed as the event it
 * forwards to its {@link ModelSink}: {@link #FIELD} to keep the value as-is, {@link #REPLACED} with a
 * {@link ModelSource} view over substitute bytes, or {@link #DECLINED} to drop the value and let the
 * format substitute a structurally valid placeholder for the field's type.
 * </p>
 *
 * @see ModelTransform
 * @see ModelSource
 */
public enum ModelEvent
{
    /** start of a top-level value; {@link #FIELD} events follow until {@link #END_VALUE} */
    START_VALUE,
    /** a field of the current value, its path and value readable from the {@link ModelSource} */
    FIELD,
    /** a field whose value a transform substituted; the substitute is readable from the {@link ModelSource} */
    REPLACED,
    /** a field a transform declined; the format writes a structurally valid placeholder for its type */
    DECLINED,
    /** end of a top-level value */
    END_VALUE
}
