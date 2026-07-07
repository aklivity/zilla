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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

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
     * Targets {@code buffer} starting at {@code offset}, bounding writes at the absolute position
     * {@code limit} — the bound a chunking driver watches via {@link #remaining()} to decide when to
     * drain and continue. Pass {@code buffer.capacity()} for the whole buffer; {@code limit} must not
     * exceed the capacity.
     */
    AvroGenerator wrap(
        MutableDirectBufferEx buffer,
        int offset,
        int limit);

    int length();

    /**
     * Bytes that may still be written before reaching the {@code limit} set at {@link #wrap}. A driver
     * checks this at a value boundary to decide whether to suspend (drain) before the next write.
     */
    int remaining();

    /**
     * Writes the record's opening brace if it fits the output bound, returning {@code true}; otherwise
     * writes nothing and returns {@code false} — a normal outcome for a chunking driver to suspend and
     * retry the identical call once drained, not a failure. This atomic, non-resumable write carries no
     * partial-write contract of its own (unlike a length-delimited value streamed via {@link #writeSegment}).
     */
    boolean writeStartRecord();

    /**
     * Checked, atomic write; semantics match {@link #writeStartRecord()}.
     */
    boolean writeStartArray();

    /**
     * Checked, atomic write; semantics match {@link #writeStartRecord()}.
     */
    boolean writeStartMap();

    /**
     * Checked, atomic write; semantics match {@link #writeStartRecord()}.
     */
    boolean writeEnd();

    void writeKey(
        DirectBufferEx buffer,
        int offset,
        int length);

    /**
     * Checked, atomic write; semantics match {@link #writeStartRecord()}.
     */
    boolean writeIndex(
        int index);

    /**
     * Checked, atomic write; semantics match {@link #writeStartRecord()}.
     */
    boolean writeNull();

    /**
     * Checked, atomic write; semantics match {@link #writeStartRecord()}.
     */
    boolean writeBoolean(
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
        DirectBufferEx buffer,
        int offset,
        int length);

    void writeBytes(
        DirectBufferEx buffer,
        int offset,
        int length);

    void writeFixed(
        DirectBufferEx buffer,
        int offset,
        int length);

    void writeEnum(
        int index);

    void writeRaw(
        DirectBufferEx buffer,
        int offset,
        int length);

    /**
     * Writes up to {@code length} payload bytes of a length-delimited value ({@link AvroKind#STRING},
     * {@link AvroKind#BYTES}, {@link AvroKind#FIXED}) from {@code source}, streaming it across chunks.
     * {@code deferred} is the payload bytes still to come in later calls; on the first call the total
     * ({@code length + deferred}) determines the length prefix, written immediately. Copies only what
     * fits within the bound and returns the bytes actually written; the caller advances by that, and
     * when the output fills it drains and calls again. Pair with {@link #flush()} once the whole value
     * has been written.
     */
    int writeSegment(
        DirectBufferEx source,
        int offset,
        int length,
        int deferred);

    /**
     * Finalizes the length-delimited value streamed via {@link #writeSegment}, requiring that all of its
     * declared bytes were written.
     */
    void flush();

    /**
     * Whether this generator writes the values it receives verbatim, reproducing the input bytes. A
     * generator that re-encodes into a different representation (e.g. JSON) is not identity.
     */
    boolean identity();
}
