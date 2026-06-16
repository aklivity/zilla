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
package io.aklivity.zilla.runtime.common.json;

/**
 * The consume end of a {@link JsonStream} pipeline. Each {@link #feed(JsonController, JsonSource, JsonEvent)}
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
     * {@link Delivery} mode the terminal sink requests; absent ⇒ {@link Delivery#STRUCTURED}.
     */
    String DELIVERY = "io.aklivity.zilla.runtime.common.json.sink.delivery";

    /**
     * Delivery mode a terminal sink requests. {@link #STRUCTURED} consumes structured events and renders
     * each scalar canonically from its decoded value ({@link JsonSource#getStringView()}), the generator
     * owning quoting/escaping so a value delivered as fragments forms one value without the sink
     * concatenating; {@link #SEGMENTABLE} opts in to verbatim byte delivery for kept values (best-effort,
     * demand-gated) by calling {@link JsonController#segmentable()}.
     */
    enum Delivery
    {
        STRUCTURED,
        SEGMENTABLE
    }

    JsonPipeline.Status feed(
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

    default void reset()
    {
    }
}
