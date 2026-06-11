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
 * encoder validates each call against the schema and writes the Avro binary encoding, framing arrays
 * and maps as single-element blocks so values can be written without buffering. Records are positional
 * — field values are written in declaration order with no field names on the wire.
 * {@link #wrap(MutableDirectBuffer, int)} targets the output buffer and resets; {@link #length()}
 * reports the bytes written for the current message; {@link #writeSegment} appends a raw slice verbatim.
 * Not thread-safe; reuse one per thread.
 */
public interface AvroEncoder
{
    void wrap(
        MutableDirectBuffer buffer,
        int offset);

    int length();

    void startRecord();

    void endRecord();

    void startArray();

    void endArray();

    void startMap();

    void endMap();

    void mapKey(
        DirectBuffer buffer,
        int offset,
        int length);

    void unionBranch(
        int index);

    void encodeNull();

    void encodeBoolean(
        boolean value);

    void encodeInt(
        int value);

    void encodeLong(
        long value);

    void encodeFloat(
        float value);

    void encodeDouble(
        double value);

    void encodeString(
        DirectBuffer buffer,
        int offset,
        int length);

    void encodeBytes(
        DirectBuffer buffer,
        int offset,
        int length);

    void encodeFixed(
        DirectBuffer buffer,
        int offset,
        int length);

    void encodeEnum(
        int index);

    void writeSegment(
        DirectBuffer buffer,
        int offset,
        int length);
}
