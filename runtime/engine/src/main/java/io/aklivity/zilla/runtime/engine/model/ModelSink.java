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
 * The consume end of a {@link ModelTransform} chain. Each {@link #transform(ModelController, ModelSource,
 * FieldEvent)} delivers one event, with {@code source} positioned to read the field it carries, and
 * reports a {@link FieldStatus}; {@code control} steers the format adapter driving the chain.
 * <p>
 * The terminal sink is supplied by the format adapter and turns each event back into the format's own
 * representation. The downstream of a {@link ModelTransform} is also a {@code ModelSink}, so stages
 * compose without knowing whether their downstream is another stage or the adapter.
 * </p>
 *
 * @see ModelTransform
 */
public interface ModelSink
{
    /**
     * Consumes one field event.
     *
     * @param control  the control handle for the format adapter driving the chain
     * @param source   the read-only view of the field the event carries
     * @param event    the field event
     * @return the outcome of consuming the event
     */
    FieldStatus transform(
        ModelController control,
        ModelSource source,
        FieldEvent event);

    /**
     * Continues the field in flight when the previous {@link #transform} returned
     * {@link FieldStatus#SUSPENDED}, after the caller has drained the bounded output. {@code event} is the
     * event that suspended, re-supplied by the adapter so the sink keeps no resume state of its own. The
     * default does nothing, since a sink that never suspends never sees this.
     *
     * @param control  the control handle for the format adapter driving the chain
     * @param source   the read-only view of the field the event carries
     * @param event    the event that suspended
     * @return the outcome of resuming the event
     */
    default FieldStatus resume(
        ModelController control,
        ModelSource source,
        FieldEvent event)
    {
        return FieldStatus.ADVANCED;
    }

    /**
     * Emits anything the sink has buffered, called once per value before {@link FieldEvent#END_VALUE}
     * reaches the chain. The default does nothing.
     *
     * @param control  the control handle for the format adapter driving the chain
     * @param source   the read-only view of the current value
     * @return the outcome of the flush
     */
    default FieldStatus flush(
        ModelController control,
        ModelSource source)
    {
        return FieldStatus.ADVANCED;
    }

    /**
     * Discards any in-flight state so the sink is ready for the next value.
     */
    default void reset()
    {
    }

    /**
     * Whether this sink, together with everything downstream of it, leaves every field value unchanged.
     *
     * @return {@code true} if every field passes through unchanged; {@code false} otherwise
     */
    boolean identity();
}
