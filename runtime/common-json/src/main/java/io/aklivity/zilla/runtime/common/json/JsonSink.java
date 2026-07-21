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
package io.aklivity.zilla.runtime.common.json;

/**
 * The consume end of a {@link JsonStream} pipeline. Each {@link #transform(JsonController, JsonSource, JsonEvent)}
 * delivers one event (with {@code source} positioned to read its scalar, or its bytes when the event is
 * {@link JsonEvent#segmented()}) and returns whether the current top-level value has reached a terminal
 * {@link JsonPipeline.Status}. {@code control} steers the immediate upstream. A terminal sink materializes
 * events into a buffer; the downstream of a {@link JsonTransform} is also a {@code JsonSink}. Third parties
 * may implement this contract to consume the projected event stream.
 */
public interface JsonSink
{
    /**
     * Config key (for {@link JsonEx#createSink(JsonGeneratorEx, java.util.Map)}) whose value is the
     * {@link Delivery} mode the terminal sink requests. The terminal sink prefers bytes by default;
     * {@link Delivery#STRUCTURED} is the explicit opt-out that forces canonical re-rendering.
     */
    String DELIVERY = "io.aklivity.zilla.runtime.common.json.sink.delivery";

    /**
     * Delivery mode a terminal sink requests. The terminal sink <em>prefers bytes</em> by default — it opts
     * into both {@link JsonController#segmentable()} and {@link JsonController#verbatim()} so the pipeline
     * negotiates the most efficient byte-preserving delivery (an opaque segment run, or per-event
     * {@link JsonEvent#VERBATIM} events from a mediator that needs structure). {@link #STRUCTURED} is the
     * explicit opt-out: it requests neither, so every value is re-rendered canonically from its decoded value
     * ({@link JsonSource#getStringView()}). {@link #SEGMENTABLE} is retained as a named synonym for the default
     * byte-preferring behavior.
     */
    enum Delivery
    {
        STRUCTURED,
        SEGMENTABLE
    }

    JsonPipeline.Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event);

    /**
     * Continues the value left in flight by a prior {@link JsonPipeline.Status#SUSPENDED} — a value being
     * written across chunks — before the next event is fed. {@code event} is the value event that
     * suspended (supplied by the pump, so the sink keeps no resume state); the sink re-reads the in-flight
     * value's remainder from {@code source} and steers the immediate upstream with {@code control}. Returns
     * {@link JsonPipeline.Status#SUSPENDED} if the bounded output filled again, or {@link
     * JsonPipeline.Status#ADVANCED} when nothing remains pending. A stage with no in-flight output returns
     * {@code ADVANCED}; the default is sufficient for stages that only forward events.
     */
    default JsonPipeline.Status resume(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        return JsonPipeline.Status.ADVANCED;
    }

    /**
     * Final drain hook the pump calls when the input window is consumed before a terminal value, giving the
     * sink one chance to do end-of-feed work against {@code source} before the window is replaced. A verbatim
     * sink uses it to pull {@link JsonSource#getVerbatim(int)} for bytes the parser consumed during
     * end-of-window lookahead (e.g. a separator between two values) that no event pulled, so they are written
     * out rather than lost when the next window arrives — un-pulled bytes meanwhile stay in the source's own
     * input buffer, never copied elsewhere. Returns {@link JsonPipeline.Status#SUSPENDED} if the bounded output
     * filled mid-drain (resume continues it) or {@link JsonPipeline.Status#ADVANCED} when nothing remains. The
     * default does nothing, for a sink that holds no end-of-feed state.
     */
    default JsonPipeline.Status flush(
        JsonController control,
        JsonSource source)
    {
        return JsonPipeline.Status.ADVANCED;
    }

    default void reset()
    {
    }

    /**
     * Whether this sink, together with everything downstream of it, leaves the bytes unchanged.
     */
    boolean identity();
}
