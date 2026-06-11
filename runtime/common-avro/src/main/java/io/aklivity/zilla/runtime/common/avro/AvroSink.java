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
 * The consume end of an {@link AvroStream} pipeline. Each {@link #feed(AvroController, AvroSource,
 * AvroEvent)} delivers one event (with {@code source} positioned to read its scalar, or its bytes when
 * the event is {@link AvroEvent#segmented()}) and returns whether the current top-level datum has
 * reached a terminal {@link AvroPipeline.Status}. {@code control} steers the immediate upstream. A
 * terminal sink materializes events into a buffer; the downstream of an {@link AvroTransform} is also
 * an {@code AvroSink}. Third parties may implement this contract to consume the event stream — for
 * example, the {@code model-avro} {@code AvroJson} bridge drives the JSON side from these events.
 */
public interface AvroSink
{
    /**
     * Delivery mode a terminal sink requests. {@link #STRUCTURED} consumes structured events and
     * re-encodes; {@link #SEGMENTABLE} opts in to verbatim segment delivery (best-effort) by calling
     * {@link AvroController#segmentable()} on {@link AvroEvent#START_MESSAGE}.
     */
    enum Delivery
    {
        STRUCTURED,
        SEGMENTABLE
    }

    AvroPipeline.Status feed(
        AvroController control,
        AvroSource source,
        AvroEvent event);

    default void reset()
    {
    }

    /**
     * A terminal sink that materializes each fed event into the corresponding write on {@code encoder}.
     * The supplied encoder must already be wrapped over its target buffer.
     */
    static AvroSink of(
        AvroEncoder encoder)
    {
        return new AvroSinkImpl(encoder);
    }

    /**
     * A terminal sink with the given {@link Delivery} mode: {@link Delivery#SEGMENTABLE} opts in to
     * verbatim segment delivery; {@link Delivery#STRUCTURED} re-encodes structured events. The supplied
     * encoder must already be wrapped over its target buffer.
     */
    static AvroSink of(
        AvroEncoder encoder,
        Delivery delivery)
    {
        return new AvroSinkImpl(encoder, delivery);
    }
}
