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
 * Each {@link #transform(ModelController, ModelSource, ModelEvent, ModelSink)} consumes one event and
 * forwards what it keeps to {@code sink}, the downstream supplied by the caller. The answer is the event
 * it forwards: {@link ModelEvent#FIELD} keeps the value as-is (the adapter forwards the original bytes by
 * reference, with no copy), {@link ModelEvent#REPLACED} substitutes the bytes the stage exposes through
 * its own {@link ModelSource}, and {@link ModelEvent#DECLINED} drops the value so the adapter writes a
 * structurally valid placeholder for that field's type — the only case with format-specific work, since
 * only the format knows what valid means there.
 * </p>
 * <p>
 * A stage that needs to accumulate something for its owner to query after the value completes does so
 * outside this contract, through its own methods that only its concrete owner calls.
 * </p>
 *
 * @see ModelHandler#supplyDecoder(ModelTransform)
 * @see ModelEvent
 */
public interface ModelTransform
{
    /**
     * Identity stage that forwards every field unchanged.
     */
    ModelTransform NONE = new ModelTransform()
    {
        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            return sink.transform(control, source, event);
        }

        @Override
        public ModelTransform andThen(
            ModelTransform next)
        {
            return next;
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
    ModelStatus transform(
        ModelController control,
        ModelSource source,
        ModelEvent event,
        ModelSink sink);

    /**
     * Returns a transform that feeds this stage's answer to {@code next}, so any number of stages compose
     * into the single {@code ModelTransform} a {@link ModelHandler} accepts.
     * <p>
     * Composition happens entirely in the format-agnostic domain, so the adapter driving the chain still
     * pays exactly one format-to-generic context switch per field however many stages the composition
     * represents. Chain directly — {@code first.andThen(second).andThen(third)} — or reduce a list with
     * {@code transforms.stream().reduce(ModelTransform::andThen)}.
     * </p>
     *
     * <p>
     * Composing with {@link #NONE} yields the other stage rather than a wrapper: this returns {@code this}
     * for a {@code NONE} argument, and {@code NONE} overrides it to return {@code next}.
     * </p>
     *
     * @param next  the stage to feed this stage's answer to
     * @return the composed transform
     */
    default ModelTransform andThen(
        ModelTransform next)
    {
        ModelTransform first = this;
        return next == NONE ? first : new ModelTransform()
        {
            // the downstream handed to first: it invokes next with whatever terminal the current call
            // supplied, so the chain re-binds per call without either stage holding the caller's sink
            private final ModelSink bridge = new ModelSink()
            {
                @Override
                public ModelStatus transform(
                    ModelController control,
                    ModelSource source,
                    ModelEvent event)
                {
                    return next.transform(control, source, event, terminal);
                }

                @Override
                public ModelStatus resume(
                    ModelController control,
                    ModelSource source,
                    ModelEvent event)
                {
                    return next.resume(control, source, event, terminal);
                }

                @Override
                public ModelStatus flush(
                    ModelController control,
                    ModelSource source)
                {
                    return next.flush(control, source, terminal);
                }

                @Override
                public boolean identity()
                {
                    return next.identity() && (terminal == null || terminal.identity());
                }
            };

            private ModelSink terminal;

            @Override
            public ModelStatus transform(
                ModelController control,
                ModelSource source,
                ModelEvent event,
                ModelSink sink)
            {
                this.terminal = sink;
                return first.transform(control, source, event, bridge);
            }

            @Override
            public ModelStatus resume(
                ModelController control,
                ModelSource source,
                ModelEvent event,
                ModelSink sink)
            {
                this.terminal = sink;
                return first.resume(control, source, event, bridge);
            }

            @Override
            public ModelStatus flush(
                ModelController control,
                ModelSource source,
                ModelSink sink)
            {
                this.terminal = sink;
                return first.flush(control, source, bridge);
            }

            @Override
            public void reset()
            {
                first.reset();
                next.reset();
            }

            @Override
            public boolean identity()
            {
                return first.identity() && next.identity();
            }
        };
    }

    /**
     * Resumes the downstream after a {@link ModelStatus#OVERFLOW} return, once the caller has drained the
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
    default ModelStatus resume(
        ModelController control,
        ModelSource source,
        ModelEvent event,
        ModelSink sink)
    {
        return sink.resume(control, source, event);
    }

    /**
     * Emits anything this stage has buffered, called once per value before {@link ModelEvent#END_VALUE}
     * reaches the chain. The default forwards to {@code sink}.
     *
     * @param control  the control handle for the format adapter driving this chain
     * @param source   the read-only view of the current value
     * @param sink     the downstream stage
     * @return the outcome of the flush
     */
    default ModelStatus flush(
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
