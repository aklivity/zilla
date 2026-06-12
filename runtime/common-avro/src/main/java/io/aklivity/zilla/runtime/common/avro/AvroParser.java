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
 * A schema-bound Avro event cursor. {@link #reset()} rewinds for a new datum, {@link #wrap(DirectBuffer,
 * int, int)} presents the caller-owned bytes — read in place, never copied — then a {@link #hasNext()} /
 * {@link #nextEvent()} loop pulls one {@link AvroEvent} at a time, the value at each read through this
 * cursor's typed accessors ({@link #getInt()}, {@link #getString()}, {@link #getSegment()}, …). A datum is
 * framed by {@link AvroEvent#START_MESSAGE} and {@link AvroEvent#END_MESSAGE}; {@link #hasNext()} returns
 * {@code false} when the buffered bytes are exhausted, so feed more and continue; malformed binary throws
 * {@link AvroValidationException}. Whether the datum is delivered as structured events or as a verbatim
 * segment run is chosen per pull via {@link #nextEvent(Mode)} — a stream concern the pipeline drives, not
 * a mode held on the cursor. Reuse a single instance per worker thread; not thread-safe.
 */
public interface AvroParser
{
    /**
     * How {@link #nextEvent(Mode)} delivers the datum body: {@code STRUCTURED} emits the typed event
     * stream; {@code SEGMENTED} delivers it verbatim as a {@link AvroEvent#segmented()} run, read via
     * {@link #getSegment()}. Consulted at the message body (after {@link AvroEvent#START_MESSAGE}); for
     * every other event it is ignored.
     */
    enum Mode
    {
        STRUCTURED,
        SEGMENTED
    }

    /**
     * Rewinds the cursor to before the root {@link AvroEvent#START_MESSAGE} to begin a fresh datum.
     */
    void reset();

    /**
     * Presents {@code [offset, offset + length)} of the caller-owned {@code buffer} as the next contiguous
     * run of datum bytes — the parser's not-yet-consumed remainder plus any newly arrived bytes — and reads
     * it in place without copying. A datum fed in fragments is re-presented each call from the consumed
     * watermark ({@link #getLocation()} position), the cursor resuming where it underflowed. The buffer is
     * borrowed for the duration of the call only.
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

    /**
     * Advances the cursor and returns the next event in {@link Mode#STRUCTURED} mode.
     */
    default AvroEvent nextEvent()
    {
        return nextEvent(Mode.STRUCTURED);
    }

    /**
     * Advances the cursor and returns the next event, or {@code null} when the buffered bytes are
     * exhausted; the accessors then read the value it positions. At the message body {@code mode} chooses
     * structured events ({@link Mode#STRUCTURED}) or a verbatim segment run ({@link Mode#SEGMENTED}).
     */
    AvroEvent nextEvent(
        Mode mode);

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
     * Non-owning, on-stack view of the current contiguous raw Avro bytes — valid on {@link AvroEvent#BYTES}
     * and {@link AvroEvent#FIXED} value events (the chunk available now) and on segment events.
     */
    DirectBuffer getSegment();

    /**
     * The bytes still to come in later events of the run the current event belongs to, {@code 0} on its
     * final (or only) event. On a {@link AvroEvent#STRING} / {@link AvroEvent#BYTES} / {@link AvroEvent#FIXED}
     * value this is the exact remaining payload; on a {@link AvroEvent#SEGMENT} of a verbatim run, whose
     * total is not known up front, a non-final event reports {@link AvroSource#UNBOUNDED}. {@code 0} for
     * every other event.
     */
    int deferredBytes();

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
