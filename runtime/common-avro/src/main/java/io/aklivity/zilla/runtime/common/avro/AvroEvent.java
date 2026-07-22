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
package io.aklivity.zilla.runtime.common.avro;

/**
 * The event currency of an {@link AvroStream} pipeline: the Avro-native structured events produced by
 * walking the compiled schema in lockstep with the binary, plus document framing
 * ({@link #START_MESSAGE} / {@link #END_MESSAGE}) and {@link #SEGMENT} for verbatim passthrough. A
 * segment run delivers the whole datum as raw Avro bytes rather than as structured events, bracketed by
 * the message framing; {@link #segmented()} distinguishes a segment event.
 */
public enum AvroEvent
{
    /** start of a top-level datum; structured (or segment) events follow until {@link #END_MESSAGE} */
    START_MESSAGE,
    /** end of a top-level datum */
    END_MESSAGE,
    /** start of a record; field events follow until {@link #END_RECORD} */
    START_RECORD,
    /** end of a record */
    END_RECORD,
    /** the name of the next record field; the field's value event follows */
    FIELD_NAME,
    /** start of an array; element value events follow until {@link #END_ARRAY} */
    START_ARRAY,
    /** end of an array */
    END_ARRAY,
    /** start of a map; alternating {@link #MAP_KEY} and value events follow until {@link #END_MAP} */
    START_MAP,
    /** end of a map */
    END_MAP,
    /** the key of the next map entry; the entry's value event follows */
    MAP_KEY,
    /** the selected branch index of a union; the branch's value event follows */
    UNION_BRANCH,
    /** an Avro {@code null} */
    NULL,
    /** an Avro {@code boolean}, readable via {@link AvroSource#getBoolean()} */
    BOOLEAN,
    /** an Avro {@code int}, readable via {@link AvroSource#getInt()} */
    INT,
    /** an Avro {@code long}, readable via {@link AvroSource#getLong()} */
    LONG,
    /** an Avro {@code float}, readable via {@link AvroSource#getFloat()} */
    FLOAT,
    /** an Avro {@code double}, readable via {@link AvroSource#getDouble()} */
    DOUBLE,
    /**
     * an Avro {@code string}, readable via {@link AvroSource#getString()}. A value larger than the input
     * window arrives over several {@code STRING} events, each the chunk available now (on a UTF-8 char
     * boundary), with {@link AvroSource#deferredBytes()} bytes still to come; {@code deferredBytes() == 0}
     * marks the final (or only) chunk.
     */
    STRING,
    /**
     * Avro {@code bytes}, readable via the zero-copy {@link AvroSource} buffer accessors. A value larger
     * than the input window arrives over several {@code BYTES} events, each the chunk available now, with
     * {@link AvroSource#deferredBytes()} bytes still to come; {@code deferredBytes() == 0} marks the final
     * (or only) chunk.
     */
    BYTES,
    /**
     * an Avro {@code fixed}, readable via the zero-copy {@link AvroSource} buffer accessors. A value larger
     * than the input window arrives over several {@code FIXED} events, each the chunk available now, with
     * {@link AvroSource#deferredBytes()} bytes still to come; {@code deferredBytes() == 0} marks the final
     * (or only) chunk.
     */
    FIXED,
    /** an Avro {@code enum} symbol, readable via {@link AvroSource#getString()} */
    ENUM,
    /**
     * a slice of the datum delivered as raw Avro bytes for verbatim passthrough, readable via
     * {@link AvroSource#getSegment()}. The whole datum arrives as a run of {@code SEGMENT} events whose
     * total length is not known up front: a non-final event reports {@link AvroSource#UNBOUNDED} from
     * {@link AvroSource#deferredBytes()}, the final event reports {@code 0}.
     */
    SEGMENT;

    public boolean segmented()
    {
        return this == SEGMENT;
    }
}
