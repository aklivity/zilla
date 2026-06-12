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
import org.agrona.MutableDirectBuffer;

/**
 * A schema-bound Avro binary writer. Call the typed methods in schema order to write one message; the
 * generator validates each call against the schema and writes the Avro binary encoding, framing arrays
 * and maps as single-element blocks so values can be written without buffering. Records are positional
 * — field values are written in declaration order with no field names on the wire.
 * {@link #wrap(MutableDirectBuffer, int, int)} targets the output buffer and resets; {@link #length()}
 * reports the bytes written for the current message; {@link #writeRaw} appends a raw slice verbatim.
 * Not thread-safe; reuse one per thread.
 */
public interface AvroGenerator
{
    /**
     * Targets {@code buffer} starting at {@code offset} with a hard byte {@code limit} — the bound a
     * chunking driver watches via {@link #remaining()} to decide when to drain and continue. Pass the
     * buffer's remaining capacity for unbounded output; {@code offset + limit} must not exceed the
     * buffer's capacity.
     */
    AvroGenerator wrap(
        MutableDirectBuffer buffer,
        int offset,
        int limit);

    int length();

    /**
     * Bytes that may still be written before reaching the {@code limit} set at {@link #wrap}. A driver
     * checks this at a value boundary to decide whether to suspend (drain) before the next write.
     */
    int remaining();

    void writeStartRecord();

    void writeStartArray();

    void writeStartMap();

    void writeEnd();

    void writeKey(
        DirectBuffer buffer,
        int offset,
        int length);

    void writeIndex(
        int index);

    void writeNull();

    void writeBoolean(
        boolean value);

    void writeInt(
        int value);

    void writeLong(
        long value);

    void writeFloat(
        float value);

    void writeDouble(
        double value);

    void writeString(
        DirectBuffer buffer,
        int offset,
        int length);

    void writeBytes(
        DirectBuffer buffer,
        int offset,
        int length);

    void writeFixed(
        DirectBuffer buffer,
        int offset,
        int length);

    void writeEnum(
        int index);

    void writeRaw(
        DirectBuffer buffer,
        int offset,
        int length);
}
