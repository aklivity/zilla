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
package io.aklivity.zilla.runtime.model.core.ext;

import io.aklivity.zilla.runtime.engine.model.ModelStatus;

/**
 * The consume end of a {@code string} pipeline. Each
 * {@link #transform(StringController, StringSource, StringEvent)} delivers one event, with {@code source}
 * positioned to read the bytes it carries, and reports a {@link ModelStatus}; {@code control} steers the
 * immediate upstream.
 * <p>
 * The terminal sink is supplied by the model and writes the value into the caller's destination. The
 * downstream of a {@link StringTransform} is also a {@code StringSink}, so stages compose without knowing
 * whether their downstream is another stage or the model itself. Chains are bound sink-to-sink at
 * assembly, so a composition of any number of stages is still one pass over the value.
 * </p>
 *
 * @see StringTransform
 */
public interface StringSink
{
    /**
     * Consumes one event.
     *
     * @param control  the control handle for the immediate upstream
     * @param source   the read-only view of the value bytes the event carries
     * @param event    the event
     * @return the outcome of consuming the event
     */
    ModelStatus transform(
        StringController control,
        StringSource source,
        StringEvent event);

    /**
     * Continues the output in flight when the previous {@link #transform} returned
     * {@link ModelStatus#OVERFLOW}, after the caller has drained the bounded destination. {@code event} is
     * the event that suspended, re-supplied by the pipeline so the sink keeps no resume state of its own,
     * and {@code source} exposes whatever of that event's bytes the sink has not reported consuming. The
     * default does nothing, since a sink that never suspends never sees this.
     *
     * @param control  the control handle for the immediate upstream
     * @param source   the read-only view of the value bytes still to be consumed
     * @param event    the event that suspended
     * @return the outcome of resuming the event
     */
    default ModelStatus resume(
        StringController control,
        StringSource source,
        StringEvent event)
    {
        return ModelStatus.OK;
    }

    /**
     * Discards any in-flight state so the sink is ready for the next value.
     */
    default void reset()
    {
    }

    /**
     * Whether this sink, together with everything downstream of it, leaves the value bytes unchanged.
     *
     * @return {@code true} if every value passes through unchanged; {@code false} otherwise
     */
    boolean identity();
}
