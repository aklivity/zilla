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
package io.aklivity.zilla.runtime.binding.llm.internal.decode;

import org.agrona.DirectBuffer;

/**
 * Receives the event frames produced by an {@link LlmContentDecoder} as it decodes content-type
 * framing from a stream's raw bytes.
 */
public interface LlmContentDecoderOutput
{
    /**
     * Reports whether the decoder may start emitting a new event boundary. A decoder that scans
     * multiple event boundaries within a single {@link LlmContentDecoder#decode} call checks this
     * between boundaries and stops early once it returns {@code false}, leaving the unscanned bytes
     * for a later {@code decode} call once the caller can accept more. An implementer with no concept
     * of named events (e.g. plain JSON) returns {@code true} unconditionally, since it has nothing to
     * stop for.
     *
     * @return {@code true} if decoding may continue past the boundary just reached
     */
    boolean available();

    /**
     * Signals that decoding has entered a new content-type-specific named event -- e.g. the SSE
     * {@code event:} field's value -- before any {@link #data} belonging to it is emitted. An
     * implementer with no concept of named events (e.g. plain JSON) never has this called, but still
     * implements it (e.g. as a no-op) rather than relying on a default.
     *
     * @param event  the event name
     */
    void event(
        String event);

    /**
     * Emits content bytes belonging to the event currently being decoded.
     *
     * @param buffer  the buffer holding the content bytes
     * @param offset  the offset of the content bytes within {@code buffer}
     * @param length  the number of content bytes
     * @param last    {@code true} when these bytes conclude the current field's value (its own line
     *                terminator was found, or the whole content is buffered in one call); {@code false}
     *                when more of the same value follows in a later call
     */
    void data(
        DirectBuffer buffer,
        int offset,
        int length,
        boolean last);

    /**
     * Emits an event boundary reached by the decoder.
     *
     * @param event   the content-type-specific event name, or {@code null} when the content-type has none
     * @param buffer  the buffer holding any bytes associated with the event boundary
     * @param offset  the offset of the associated bytes within {@code buffer}
     * @param length  the number of associated bytes
     */
    void flush(
        String event,
        DirectBuffer buffer,
        int offset,
        int length);
}
