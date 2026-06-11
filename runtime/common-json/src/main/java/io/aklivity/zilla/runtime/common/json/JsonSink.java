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

import io.aklivity.zilla.runtime.common.json.internal.JsonSinkImpl;

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
    JsonPipeline.Status feed(
        JsonController control,
        JsonSource source,
        JsonEvent event);

    default void reset()
    {
    }

    /**
     * A terminal sink that materializes each fed event into the corresponding {@code writeXxx} call on
     * {@code generator}. The supplied generator must already be wrapped over its target buffer.
     */
    static JsonSink of(
        JsonGeneratorEx generator)
    {
        return new JsonSinkImpl(generator);
    }
}
