/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.avro;

import io.aklivity.zilla.runtime.common.avro.internal.AvroSinkImpl;

/**
 * The consume end of an {@link AvroStream} pipeline. Each {@link #transform(AvroController, AvroSource,
 * AvroEvent)} delivers one event (with {@code source} positioned to read its scalar, or its bytes when
 * the event is {@link AvroEvent#segmented()}) and returns whether the current top-level datum has
 * reached a terminal {@link AvroPipeline.Status}. {@code control} steers the immediate upstream. A
 * terminal sink materializes events into a buffer; the downstream of an {@link AvroTransform} is also
 * an {@code AvroSink}. Third parties may implement this contract to consume the event stream — for
 * example, the {@code common-avro} {@link io.aklivity.zilla.runtime.common.avro.json.AvroJson} bridge (in
 * the {@code .json} subpackage) drives the JSON side from these events.
 */
public interface AvroSink
{
    /**
     * Delivery mode a terminal sink requests. {@link #STRUCTURED} consumes structured events and
     * re-generates; {@link #SEGMENTABLE} opts in to verbatim segment delivery (best-effort) by calling
     * {@link AvroController#segmentable()} on {@link AvroEvent#START_MESSAGE}.
     */
    enum Delivery
    {
        STRUCTURED,
        SEGMENTABLE
    }

    AvroPipeline.Status transform(
        AvroController control,
        AvroSource source,
        AvroEvent event);

    /**
     * Continues the value in flight when the previous {@link #transform} returned
     * {@link AvroPipeline.Status#SUSPENDED}, after the caller has drained and re-wrapped the output.
     * {@code event} is the value event that suspended, supplied by the pump so the sink keeps no resume
     * state of its own; the sink re-reads the in-flight value's remainder from {@code source}. The default
     * does nothing (a sink that never suspends never sees this); a sink that returns {@code SUSPENDED}
     * overrides it to resume from where it paused.
     */
    default AvroPipeline.Status resume(
        AvroController control,
        AvroSource source,
        AvroEvent event)
    {
        return AvroPipeline.Status.ADVANCED;
    }

    default void reset()
    {
    }

    /**
     * Whether this sink, together with everything downstream of it, leaves the bytes unchanged.
     */
    boolean identity();

    /**
     * A terminal sink that materializes each fed event into the corresponding write on {@code generator}.
     * The supplied generator must already be wrapped over its target buffer.
     */
    static AvroSink of(
        AvroGenerator generator)
    {
        return new AvroSinkImpl(generator);
    }

    /**
     * A terminal sink with the given {@link Delivery} mode: {@link Delivery#SEGMENTABLE} opts in to
     * verbatim segment delivery; {@link Delivery#STRUCTURED} re-generates structured events. The supplied
     * generator must already be wrapped over its target buffer.
     */
    static AvroSink of(
        AvroGenerator generator,
        Delivery delivery)
    {
        return new AvroSinkImpl(generator, delivery);
    }
}
