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

/**
 * The vocabulary of events produced by an {@link AvroDecodePipeline} and consumed by an
 * {@link AvroEncodePipeline}, mirroring the structure of the compiled Avro schema as it is
 * walked in lockstep with the binary. Each event is delivered with an {@link AvroSource}
 * positioned to read the corresponding scalar (for value events) or name (for
 * {@link #FIELD_NAME} / {@link #MAP_KEY}).
 */
public enum AvroEvent
{
    /** start of a record; field events follow until {@link #RECORD_END} */
    RECORD_START,
    /** end of a record */
    RECORD_END,
    /** the name of the next record field; the field's value event follows */
    FIELD_NAME,
    /** start of an array; element value events follow until {@link #ARRAY_END} */
    ARRAY_START,
    /** end of an array */
    ARRAY_END,
    /** start of a map; alternating {@link #MAP_KEY} and value events follow until {@link #MAP_END} */
    MAP_START,
    /** end of a map */
    MAP_END,
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
    /** an Avro {@code string}, readable via {@link AvroSource#getString()} */
    STRING,
    /** Avro {@code bytes}, readable via the zero-copy {@link AvroSource} buffer accessors */
    BYTES,
    /** an Avro {@code fixed}, readable via the zero-copy {@link AvroSource} buffer accessors */
    FIXED,
    /** an Avro {@code enum} symbol, readable via {@link AvroSource#getString()} */
    ENUM
}
