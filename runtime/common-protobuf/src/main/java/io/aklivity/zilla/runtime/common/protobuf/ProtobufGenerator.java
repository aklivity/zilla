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
package io.aklivity.zilla.runtime.common.protobuf;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

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
 * within that width otherwise (never shifted).
 * <p>
 * Output is one logical byte stream split into flow-control chunks that the consumer concatenates. When
 * the buffer fills, {@link #flush()} fills each open message's slot with its full declared length and the
 * caller drains and re-{@link #wrap(MutableDirectBuffer, int, int) wraps}, leaving the levels open so the
 * body continues into the next chunk. A length-delimited value larger than the buffer fragments across
 * chunks via {@link #writeSegment}.
 */
public interface ProtobufGenerator
{
    /**
     * Retargets the generator at {@code buffer} from {@code offset} with a hard byte {@code limit} that
     * {@link #remaining()} tracks; {@code limit} must not exceed {@code buffer.capacity() - offset}. When
     * levels are open from a preceding {@link #flush()}, they stay open against the fresh buffer — nothing
     * is re-emitted — so a chunked message and any in-flight {@link #writeSegment} continue; otherwise the
     * generator starts a fresh message.
     */
    ProtobufGenerator wrap(
        MutableDirectBufferEx buffer,
        int offset,
        int limit);

    int length();

    /**
     * Bytes that may still be written before reaching the {@code limit} set at {@link #wrap}. A driver
     * checks this before a write to decide whether the field fits or the value must be fragmented or drained.
     */
    int remaining();

    /**
     * Cumulative count of source value bytes consumed by {@link #writeSegment} since the last {@link #wrap}.
     * Because {@code writeSegment} is consumption-driven — it writes only as many of the offered bytes as fit
     * the bound — a driver reads this around a call (as a delta) to learn how many bytes were taken and push
     * the unconsumed remainder back to its upstream.
     */
    int consumed();

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
        DirectBufferEx value,
        int offset,
        int length);

    /**
     * Writes {@code field} as a length-delimited message whose body is the pre-encoded bytes in
     * {@code message[offset, offset+length)}.
     */
    ProtobufGenerator writeMessage(
        int field,
        DirectBufferEx message,
        int offset,
        int length);

    /**
     * Writes part of a length-delimited {@code field} whose total body length is {@code length + deferred}.
     * The first call (when no segment is open) emits the tag and the total length prefix; thereafter it
     * appends body bytes from {@code value[offset, offset+length)}. It is <em>consumption-driven</em>: it
     * writes only as many of the offered {@code length} bytes as fit the bound (and, on the first call, writes
     * nothing if the header plus one byte would not fit), reporting how many it took via {@link #consumed()}.
     * A driver reads {@code consumed()} as a delta to learn the amount written, pushes the remainder back to
     * its upstream, drains, re-{@link #wrap(MutableDirectBuffer, int, int) wraps} (the open levels and segment
     * persist), and continues. The total is known up front, so the length prefix is correct in the
     * concatenated stream even though the body arrives in pieces.
     */
    ProtobufGenerator writeSegment(
        int field,
        DirectBufferEx value,
        int offset,
        int length,
        int deferred);

    /**
     * Begins a length-delimited nested message on {@code field} with an optimistic body {@code length}:
     * the tag is written and a length slot sized to {@code varintWidth(length)} is reserved, then the body
     * streams straight to the output (no scratch) until the matching {@link #endMessage()}. {@code length}
     * only sizes the slot — pass the expected size (e.g. the source message length), or a deliberately
     * long value to widen the slot for a body that may grow. The write-side mirror of a
     * {@link ProtobufEvent#START_MESSAGE} event, which carries the source length via
     * {@link ProtobufSource#segment()}.
     * <p>
     * This atomic, non-resumable write carries no partial-write contract of its own (unlike a
     * length-delimited value streamed via {@link #writeSegment}): it returns {@code true} once every byte
     * it needs (tag, length slot, and any structural bytes a rendering generator emits alongside them,
     * e.g. a JSON object brace and field key) fits and is written, or {@code false} having written nothing
     * at all, so a caller retries the identical call once drained rather than resuming mid-write.
     */
    boolean startMessage(
        int field,
        int length);

    /**
     * Ends the nested message opened by the most recent {@link #startMessage(int, int)} — the write-side
     * mirror of a {@link ProtobufEvent#END_MESSAGE} event. Fills the reserved slot with the actual body
     * length: minimal (canonical) when its varint width equals the reserved width, padded within that
     * width when smaller. Throws if the body is larger than the reserved width can hold (the optimistic
     * length under-estimated it). Checked, atomic write; semantics otherwise match {@link #startMessage}.
     */
    boolean endMessage();

    /**
     * Begins a proto2 group on {@code field} by writing its start-group tag — the write-side mirror of a
     * {@link ProtobufEvent#START_GROUP} event. A group carries no length prefix, so the body streams
     * straight to the output and is closed by {@link #endGroup()}; nothing is written up front beyond the
     * tag, which makes it the framing of choice when the body length is not known in advance. Checked,
     * atomic write; semantics otherwise match {@link #startMessage}.
     */
    boolean startGroup(
        int field);

    /**
     * Ends the group opened by the most recent {@link #startGroup(int)} by writing its end-group tag —
     * the write-side mirror of an {@link ProtobufEvent#END_GROUP} event. Checked, atomic write; semantics
     * otherwise match {@link #startMessage}.
     */
    boolean endGroup();

    /**
     * Splices {@code length} bytes from {@code source} verbatim — tag and value already encoded — used
     * to pass a field through unchanged.
     */
    ProtobufGenerator writeRaw(
        DirectBufferEx source,
        int offset,
        int length);

    /**
     * Writes {@code field} with the given {@code wireType} from a raw value slice — the write-side mirror
     * of a schema-free {@link ProtobufEvent#VALUE} event, where {@link ProtobufSource#wireType()} and the
     * slice ({@link ProtobufSource#segment()})
     * stand in for a typed value. The tag is written, then the slice: a {@code LEN} value is re-length-prefixed,
     * a group ({@code SGROUP}) is wrapped in start/end-group tags around the body, and a varint or fixed value
     * is spliced verbatim (preserving its exact encoding). Used for a lossless schema-free copy.
     */
    ProtobufGenerator writeValue(
        int field,
        ProtobufWireType wireType,
        DirectBufferEx value,
        int offset,
        int length);

    /**
     * Closes the current chunk's records so it can be drained: each open message's slot is filled with the
     * body present in this record plus the bytes still deferred by an in-flight {@link #writeSegment}, and
     * each open group is end-tagged. The records are reopened lazily — re-emitted on the next field write —
     * so the levels stay logically open across the drain; after the caller drains
     * {@code [wrapOffset, wrapOffset + length())} and calls {@link #wrap(MutableDirectBuffer, int, int)}
     * again, writing resumes where it left off and the per-record fragments merge on decode. Used by a
     * bounded driver before draining when the next write will not fit. A group may not enclose a value being
     * fragmented across a chunk (it cannot be end-tagged mid-value); attempting it throws.
     * <p>
     * Returns {@code true} once every open level is fully closed, or {@code false} if the output filled
     * before that — the caller drains the bytes written so far and calls {@link #flush()} again to
     * continue closing the remaining levels, rather than losing partial progress.
     */
    boolean flush();

    /**
     * Whether this generator writes the values it receives verbatim, reproducing the input bytes. A
     * generator that re-encodes into a different representation (e.g. JSON) is not identity.
     */
    boolean identity();
}
