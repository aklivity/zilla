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

import jakarta.json.stream.JsonParser;

import org.agrona.DirectBuffer;

/**
 * A {@link JsonParser} extended with the streaming-over-buffers surface the standard
 * {@code jakarta.json.stream} contract lacks. This is the {@code *Ex} pattern for going beyond JSON-P:
 * a sub-interface of the standard type that drives a {@link JsonStream} pipeline.
 * <p>
 * Obtain an instance from {@link StreamingJson#createParser()} (the implementation is internal). Reuse a
 * single instance per worker thread, calling {@link #wrap(DirectBuffer, int, int)} to borrow each frame's
 * buffer before pumping.
 */
public interface JsonParserEx extends JsonParser
{
    /**
     * Begins a pipeline pumped by this parser; append stages with {@link JsonStream#transform} and
     * terminate with {@link JsonStream#into}.
     */
    JsonStream stream();

    /**
     * Borrows {@code buffer} as the input for the next pump, starting at {@code offset} for {@code length}
     * bytes. The buffer is read in place for the duration of the pump; resume state carried in the parser
     * bridges values that span frames.
     */
    JsonParserEx wrap(
        DirectBuffer buffer,
        int offset,
        int length);
}
