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
 * Immutable, read-only view of the value observed at the current {@link AvroEvent} as an
 * {@link AvroStream} pipeline pumps events through its stages. An {@code AvroSource} exposes only the
 * typed accessor that matches the current event; it has no cursor-advancing method, so a stage cannot
 * disturb the pump. The buffer accessors for {@link AvroEvent#BYTES} / {@link AvroEvent#FIXED} and the
 * {@link #getSegment()} view for segmented events expose the value in place for zero-copy reads, valid
 * only for the duration of the {@code feed} call.
 */
public interface AvroSource
{
    boolean getBoolean();

    int getInt();

    long getLong();

    float getFloat();

    double getDouble();

    /**
     * The text of the current {@link AvroEvent#STRING} (or {@link AvroEvent#ENUM} symbol). For a value
     * streamed across chunks each call returns the current chunk's text, always ending on a UTF-8 character
     * boundary; concatenated across the run they form the whole string.
     */
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
     * On a {@link AvroEvent#STRING} / {@link AvroEvent#BYTES} / {@link AvroEvent#FIXED} value, the payload
     * bytes still to come in later events of that same value; {@code 0} on the final (or only) chunk.
     * {@code 0} for every other event.
     */
    int deferredBytes();

    /**
     * The {@link AvroType} of the value or composite at the current event — the record at
     * {@link AvroEvent#START_RECORD}, the selected branch at {@link AvroEvent#UNION_BRANCH}, the field
     * type at {@link AvroEvent#FIELD_NAME}, the scalar at a value event; {@code null} at
     * {@link AvroEvent#END_MESSAGE} and on segment events. A stage reads it (then walks the type graph)
     * to recover the named-type, union-branch, and logical-type detail the typed event stream omits.
     */
    AvroType type();

    /**
     * @return the location of the current event within the datum, for diagnostics
     */
    AvroLocation getLocation();
}
