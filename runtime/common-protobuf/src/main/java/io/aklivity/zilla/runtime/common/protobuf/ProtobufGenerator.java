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
package io.aklivity.zilla.runtime.common.protobuf;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * A buffer-backed Protobuf wire generator. Reuse a single instance per worker thread;
 * {@link #wrap(MutableDirectBuffer, int)} retargets it and {@link #length()} reports the bytes
 * written since.
 * <p>
 * Each {@code writeXxx(field, value)} emits one field — its tag (the field number paired with the wire
 * type implied by the method) followed by the value — chosen by the Protobuf type, since the wire is
 * not self-describing about signedness, zig-zag, or fixed width. {@code writeMessage} length-prefixes a
 * pre-encoded nested message and {@code writeRaw} splices bytes verbatim. All writers return {@code this}
 * to chain.
 */
public interface ProtobufGenerator
{
    ProtobufGenerator wrap(
        MutableDirectBuffer buffer,
        int offset);

    int length();

    ProtobufGenerator writeInt32(
        int field,
        int value);

    ProtobufGenerator writeInt64(
        int field,
        long value);

    ProtobufGenerator writeUInt32(
        int field,
        int value);

    ProtobufGenerator writeUInt64(
        int field,
        long value);

    ProtobufGenerator writeSInt32(
        int field,
        int value);

    ProtobufGenerator writeSInt64(
        int field,
        long value);

    ProtobufGenerator writeFixed32(
        int field,
        int value);

    ProtobufGenerator writeFixed64(
        int field,
        long value);

    ProtobufGenerator writeSFixed32(
        int field,
        int value);

    ProtobufGenerator writeSFixed64(
        int field,
        long value);

    ProtobufGenerator writeFloat(
        int field,
        float value);

    ProtobufGenerator writeDouble(
        int field,
        double value);

    ProtobufGenerator writeBool(
        int field,
        boolean value);

    ProtobufGenerator writeEnum(
        int field,
        int number);

    ProtobufGenerator writeString(
        int field,
        String value);

    ProtobufGenerator writeBytes(
        int field,
        byte[] value);

    ProtobufGenerator writeBytes(
        int field,
        DirectBuffer value,
        int offset,
        int length);

    /**
     * Writes {@code field} as a length-delimited message whose body is the pre-encoded bytes in
     * {@code message[offset, offset+length)}.
     */
    ProtobufGenerator writeMessage(
        int field,
        DirectBuffer message,
        int offset,
        int length);

    /**
     * Begins a length-delimited nested message on {@code field}, written incrementally: subsequent
     * {@code writeXxx} / {@code startMessage} calls target the nested body until the matching
     * {@link #endMessage()}, which back-patches the length. This is the write-side mirror of a
     * {@link ProtobufEvent#START_MESSAGE} event from a parser; pair each with {@link #endMessage()}.
     */
    ProtobufGenerator startMessage(
        int field);

    /**
     * Ends the nested message opened by the most recent {@link #startMessage(int)}, splicing its body
     * with its length into the enclosing message — the write-side mirror of a
     * {@link ProtobufEvent#END_MESSAGE} event.
     */
    ProtobufGenerator endMessage();

    /**
     * Splices {@code length} bytes from {@code source} verbatim — tag and value already encoded — used
     * to pass a field through unchanged.
     */
    ProtobufGenerator writeRaw(
        DirectBuffer source,
        int offset,
        int length);
}
