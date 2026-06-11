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

    /**
     * Wraps {@code buffer} with a hard byte {@code limit} (rather than the buffer's full capacity) — the
     * bound a chunking driver watches via {@link #remaining()} to decide when to drain and continue. The
     * two-argument {@link #wrap(MutableDirectBuffer, int)} is equivalent with {@code limit} set to the
     * remaining capacity.
     */
    ProtobufGenerator wrap(
        MutableDirectBuffer buffer,
        int offset,
        int limit);

    int length();

    /**
     * Bytes that may still be written before reaching the {@code limit} set at {@link #wrap}. A driver
     * checks this at a field boundary to decide whether to suspend (drain) before the next write.
     */
    int remaining();

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
     * Begins a length-delimited nested message on {@code field} with its body {@code length} known up
     * front — the tag and length prefix are written immediately and the body is then streamed straight
     * to the output (no buffering, no back-patch) until the matching {@link #endMessage()}. This is the
     * write-side mirror of a {@link ProtobufEvent#START_MESSAGE} event, which carries the message length
     * via {@link ProtobufSource#length()}; pair each with {@link #endMessage()}.
     */
    ProtobufGenerator startMessage(
        int field,
        int length);

    /**
     * Begins a length-delimited nested message on {@code field} whose body length is not known up front:
     * the tag is written and a fixed-width length slot is reserved, to be filled in place by the matching
     * {@link #endMessage()} once the body is complete (no back-patch shift). This is the form a chunking
     * driver uses — on a drain boundary it closes every open level with {@link #endMessage()}, then on
     * resume reopens each with this call against a fresh buffer, and the decoder merges the resulting
     * records.
     */
    ProtobufGenerator startMessage(
        int field);

    /**
     * Ends the nested message opened by the most recent {@code startMessage} — the write-side mirror of a
     * {@link ProtobufEvent#END_MESSAGE} event. For {@link #startMessage(int, int)} the body must match the
     * declared length; for {@link #startMessage(int)} the reserved slot is filled with the body length.
     */
    ProtobufGenerator endMessage();

    /**
     * Begins a proto2 group on {@code field} by writing its start-group tag — the write-side mirror of a
     * {@link ProtobufEvent#START_GROUP} event. A group carries no length prefix, so the body streams
     * straight to the output and is closed by {@link #endGroup()}; nothing is written up front beyond the
     * tag, which makes it the framing of choice when the body length is not known in advance.
     */
    ProtobufGenerator startGroup(
        int field);

    /**
     * Ends the group opened by the most recent {@link #startGroup(int)} by writing its end-group tag —
     * the write-side mirror of an {@link ProtobufEvent#END_GROUP} event.
     */
    ProtobufGenerator endGroup();

    /**
     * Splices {@code length} bytes from {@code source} verbatim — tag and value already encoded — used
     * to pass a field through unchanged.
     */
    ProtobufGenerator writeRaw(
        DirectBuffer source,
        int offset,
        int length);
}
