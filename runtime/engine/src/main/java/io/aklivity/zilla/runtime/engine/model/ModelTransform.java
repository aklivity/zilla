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
 * A format-agnostic, per-field stage in the model pipeline that observes, substitutes, or declines each
 * field of a value as it is transformed.
 * <p>
 * A {@code ModelTransform} is wired to a pipeline at {@link ModelHandler#supplyDecoder} or
 * {@link ModelHandler#supplyEncoder} time and confined to the same single I/O thread. It never sees the
 * format's wire representation: a thin adapter inside each model derives the field's path and value
 * bytes on the way in, and turns the transform's answer back into a valid format-specific event on the
 * way out. One transform implementation therefore plugs into any model that ships an adapter.
 * </p>
 * <p>
 * Each {@link #transform(ModelController, ModelSource, FieldEvent, ModelSink)} consumes one event and
 * forwards what it keeps to {@code sink}, the downstream supplied by the caller. The answer is the event
 * it forwards: {@link FieldEvent#FIELD} keeps the value as-is (the adapter forwards the original bytes by
 * reference, with no copy), {@link FieldEvent#REPLACED} substitutes the bytes the stage exposes through
 * its own {@link ModelSource}, and {@link FieldEvent#DECLINED} drops the value so the adapter writes a
 * structurally valid placeholder for that field's type — the only case with format-specific work, since
 * only the format knows what valid means there.
 * </p>
 * <p>
 * A stage that needs to accumulate something for its owner to query after the value completes does so
 * outside this contract, through its own methods that only its concrete owner calls.
 * </p>
 *
 * @see ModelHandler#supplyDecoder(ModelTransform)
 * @see FieldEvent
 * @see CompositeModelTransform
 */
public interface ModelTransform
{
    /**
     * Identity stage that forwards every field unchanged.
     */
    ModelTransform NONE = new ModelTransform()
    {
        @Override
        public FieldStatus transform(
            ModelController control,
            ModelSource source,
            FieldEvent event,
            ModelSink sink)
        {
            return sink.transform(control, source, event);
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    };

    /**
     * Consumes one field event and forwards what it keeps to {@code sink}.
     *
     * @param control  the control handle for the format adapter driving this chain
     * @param source   the read-only view of the field the event carries
     * @param event    the field event
     * @param sink     the downstream stage
     * @return the outcome of consuming the event
     */
    FieldStatus transform(
        ModelController control,
        ModelSource source,
        FieldEvent event,
        ModelSink sink);

    /**
     * Resumes the downstream after a {@link FieldStatus#SUSPENDED} return, once the caller has drained the
     * bounded output. The default forwards to {@code sink}, so a stage that merely forwards events never
     * re-sees them on resume; a stage that buffers or substitutes overrides this to continue its own
     * emission before forwarding.
     *
     * @param control  the control handle for the format adapter driving this chain
     * @param source   the read-only view of the field the event carries
     * @param event    the event that suspended
     * @param sink     the downstream stage
     * @return the outcome of resuming the event
     */
    default FieldStatus resume(
        ModelController control,
        ModelSource source,
        FieldEvent event,
        ModelSink sink)
    {
        return sink.resume(control, source, event);
    }

    /**
     * Emits anything this stage has buffered, called once per value before {@link FieldEvent#END_VALUE}
     * reaches the chain. The default forwards to {@code sink}.
     *
     * @param control  the control handle for the format adapter driving this chain
     * @param source   the read-only view of the current value
     * @param sink     the downstream stage
     * @return the outcome of the flush
     */
    default FieldStatus flush(
        ModelController control,
        ModelSource source,
        ModelSink sink)
    {
        return sink.flush(control, source);
    }

    /**
     * Discards any in-flight state so this stage is ready for the next value.
     */
    default void reset()
    {
    }

    /**
     * Whether this stage forwards every field verbatim, leaving the value bytes unchanged. An observing
     * stage is identity; a stage that substitutes or declines a field is not.
     * <p>
     * An adapter reads this to keep the verbatim fast path for an observing stage, forwarding the original
     * bytes rather than re-encoding each field from the generic rendering.
     * </p>
     *
     * @return {@code true} if every field passes through unchanged; {@code false} otherwise
     */
    default boolean identity()
    {
        return false;
    }
}
