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
 * {@link #wrap(MutableDirectBuffer, int, int)} retargets it at a buffer with a byte limit and
 * {@link #length()} reports the bytes written since.
 * <p>
 * Each {@code writeXxx(field, value)} emits one field — its tag (the field number paired with the wire
 * type implied by the method) followed by the value — chosen by the Protobuf type, since the wire is
 * not self-describing about signedness, zig-zag, or fixed width. {@code writeMessage} length-prefixes a
 * pre-encoded nested message and {@code writeRaw} splices bytes verbatim. All writers return {@code this}
 * to chain.
 * <p>
 * Nested messages use an optimistic length: {@link #startMessage(int, int)} reserves a length slot sized
 * to the supplied estimate, the body streams straight to the output, and {@link #endMessage()} fills the
 * slot with the actual length — minimal (canonical) when the estimate's varint width was right, padded
 * within that width otherwise (never shifted). When the output reaches its limit, {@link #flush()} closes
 * the open levels so the buffer is a drainable chunk; a subsequent {@link #wrap(MutableDirectBuffer, int,
 * int)} reopens them, and the resulting records reassemble by message-merge semantics.
 */
public interface ProtobufGenerator
{
    /**
     * Retargets the generator at {@code buffer} from {@code offset} with a hard byte {@code limit} that
     * {@link #remaining()} tracks; {@code limit} must not exceed {@code buffer.capacity() - offset}. When
     * levels are open from a preceding {@link #flush()}, they are reopened against the fresh buffer so a
     * chunked message continues; otherwise the generator starts a fresh message.
     */
    ProtobufGenerator wrap(
        MutableDirectBuffer buffer,
        int offset,
        int limit);

    int length();

    /**
     * Bytes that may still be written before reaching the {@code limit} set at {@link #wrap}. A driver
     * checks this at a field boundary to decide whether to {@link #flush()} (drain) before the next write.
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
     * Begins a length-delimited nested message on {@code field} with an optimistic body {@code length}:
     * the tag is written and a length slot sized to {@code varintWidth(length)} is reserved, then the body
     * streams straight to the output (no scratch) until the matching {@link #endMessage()}. {@code length}
     * only sizes the slot — pass the expected size (e.g. the source message length), or a deliberately
     * long value to widen the slot for a body that may grow. The write-side mirror of a
     * {@link ProtobufEvent#START_MESSAGE} event, which carries the source length via
     * {@link ProtobufSource#length()}.
     */
    ProtobufGenerator startMessage(
        int field,
        int length);

    /**
     * Ends the nested message opened by the most recent {@link #startMessage(int, int)} — the write-side
     * mirror of a {@link ProtobufEvent#END_MESSAGE} event. Fills the reserved slot with the actual body
     * length: minimal (canonical) when its varint width equals the reserved width, padded within that
     * width when smaller. Throws if the body is larger than the reserved width can hold (the optimistic
     * length under-estimated it).
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

    /**
     * Closes every open nested level so {@code [wrapOffset, wrapOffset + length())} is a complete,
     * decodable chunk — message slots are filled with their partial body lengths, groups are end-tagged.
     * The open levels are remembered; after the caller drains the chunk and calls
     * {@link #wrap(MutableDirectBuffer, int, int)} again, each is reopened against the fresh buffer so the
     * message continues, and the per-field records merge on decode. Used by a bounded driver to drain when
     * {@link #remaining()} runs low.
     */
    ProtobufGenerator flush();
}
