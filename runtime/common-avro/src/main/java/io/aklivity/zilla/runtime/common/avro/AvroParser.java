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

import org.agrona.DirectBuffer;

/**
 * The pull cursor of a {@code common-avro} pipeline. A schema-bound parser decodes a datum into a typed
 * event stream framed by {@link AvroEvent#START_MESSAGE} and {@link AvroEvent#END_MESSAGE}. Reuse a
 * single instance per worker thread; not thread-safe.
 * <p>
 * It can be driven directly: {@link #reset()} rewinds for a new datum, {@link #wrap(DirectBuffer, int,
 * int)} appends each frame's bytes — a datum may span several wraps — then a {@link #hasNext()} /
 * {@link #nextEvent()} loop pulls one {@link AvroEvent} at a time, the value at each read through this
 * parser's accessors ({@link #getInt()}, {@link #getString()}, {@link #getSegment()}, …). That is the
 * same value surface an {@link AvroSource} exposes to a pipeline stage, but cursor-bearing;
 * {@link Avro#stream(AvroParser)} layers the push pipeline over the same cursor and hands stages a
 * non-advancing {@link AvroSource} view instead. {@link #hasNext()} returns {@code false} when the
 * buffered bytes are exhausted, so feed more and continue; malformed binary throws
 * {@link AvroValidationException}.
 */
public interface AvroParser
{
    /**
     * Rewinds the cursor to before the root {@link AvroEvent#START_MESSAGE}, discarding any buffered
     * bytes, to begin a fresh datum.
     */
    void reset();

    /**
     * Appends {@code [offset, offset + length)} of {@code buffer} to the in-flight datum's bytes; a datum
     * fed in fragments is wrapped once per fragment, the cursor resuming where it underflowed.
     */
    void wrap(
        DirectBuffer buffer,
        int offset,
        int length);

    /**
     * {@code true} while an event is available from the buffered bytes; {@code false} once they are
     * exhausted (feed more and continue) or the root {@link AvroEvent#END_MESSAGE} has been pulled.
     */
    boolean hasNext();

    AvroEvent nextEvent();

    /**
     * {@code true} once the root {@link AvroEvent#END_MESSAGE} has been pulled — the datum is fully
     * consumed.
     */
    boolean complete();

    /**
     * Requests that the next composite be delivered as a verbatim segment run ({@link AvroEvent#segmented()}
     * events) rather than as structured events; best-effort, consulted at the next {@link AvroEvent#START_MESSAGE}.
     */
    void segmentable();

    boolean getBoolean();

    int getInt();

    long getLong();

    float getFloat();

    double getDouble();

    String getString();

    /**
     * Valid only on a {@link AvroEvent#FIELD_NAME} event; the record field name from the schema
     * (a cached string, no per-message allocation).
     */
    String getField();

    /**
     * Valid only on a {@link AvroEvent#MAP_KEY} event; the map entry key decoded as UTF-8. The
     * zero-copy key bytes are also available via {@link #getSegment()}.
     */
    String getKey();

    /**
     * Non-owning, on-stack view of the current contiguous raw Avro bytes — valid on
     * {@link AvroEvent#BYTES} and {@link AvroEvent#FIXED} value events and on segment events.
     */
    DirectBuffer getSegment();

    /**
     * The {@link AvroType} of the value or composite at the current event — the record at
     * {@link AvroEvent#START_RECORD}, the selected branch at {@link AvroEvent#UNION_BRANCH}, the field
     * type at {@link AvroEvent#FIELD_NAME}, the scalar at a value event; {@code null} before the first
     * event, at {@link AvroEvent#END_MESSAGE}, and on segment events.
     */
    AvroType type();

    /**
     * @return the location of the current event within the datum, for diagnostics
     */
    AvroLocation getLocation();
}
