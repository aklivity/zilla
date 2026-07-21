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

/**
 * The pull cursor of a {@code common-protobuf} pipeline. A schema-bound parser
 * ({@link Protobuf#parser(ProtobufSchema, String)}) decodes a message into a typed event stream;
 * a schema-free parser ({@link Protobuf#parser()}) tokenizes the wire into generic events. Reuse a
 * single instance per worker thread.
 * <p>
 * It can be driven directly: {@link #wrap} borrows a fully-buffered message, then a
 * {@link #hasNext()} / {@link #nextEvent()} loop pulls one {@link ProtobufEvent} at a time, with
 * the current field and value read through this parser's accessors ({@link #field()},
 * {@link #longValue()}, {@link #segment()}, …) — the same surface a {@link ProtobufSource} exposes to a
 * pipeline stage, but cursor-bearing. {@link Protobuf#stream(ProtobufParser)} layers the push pipeline
 * over the same cursor; stages there receive a non-advancing {@link ProtobufSource} view instead.
 */
public interface ProtobufParser
{
    /**
     * How {@link #nextEvent(Mode)} descends into a composite field: {@code STRUCTURED} recurses into its
     * nested events, {@code SEGMENTED} delivers it as raw segment bytes (a {@link ProtobufEvent#segmented()}
     * START/END pair, read via {@link #segment()}). The mode is consulted
     * only at a composite field; for every other event it is ignored.
     */
    enum Mode
    {
        STRUCTURED,
        SEGMENTED
    }

    /**
     * Borrows {@code buffer} as a whole, fully-buffered message (equivalent to
     * {@link #wrap(DirectBuffer, int, int, boolean)} with {@code last == true}) and rewinds the cursor to
     * before the root {@link ProtobufEvent#START_MESSAGE}.
     */
    default ProtobufParser wrap(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return wrap(buffer, offset, limit, true);
    }

    /**
     * Borrows {@code buffer} as the first (or only) input window of a message, the bytes occupying the
     * half-open range {@code [offset, limit)}, and rewinds the cursor to before the root
     * {@link ProtobufEvent#START_MESSAGE}. {@code last} marks the final window: when {@code false} and the
     * window is exhausted mid-message, {@link #nextEvent(Mode)} returns {@code null} to signal starvation,
     * and the next window is supplied via {@link #resume}.
     */
    ProtobufParser wrap(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last);

    /**
     * Continues an in-flight message with its next input window {@code [offset, limit)} after
     * {@link #nextEvent(Mode)} returned {@code null} (starvation); {@code last} marks the final window. The
     * cursor's position within the message is preserved across the window swap.
     */
    ProtobufParser resume(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last);

    /**
     * {@code true} until the root {@link ProtobufEvent#END_MESSAGE} has been pulled.
     */
    boolean hasNext();

    /**
     * The number of bytes at the tail of the current window not yet consumed — what the driver retains and
     * re-presents, contiguous, at the front of the next window via {@link #resume}. The window-relative peer
     * of the absolute {@code getLocation().getStreamOffset()}: a driver buffering across windows keeps exactly this
     * many bytes without tracking the window's absolute base. Reported at a whole-unit boundary; zero once the
     * window is fully consumed.
     */
    int remaining();

    /**
     * Advances the cursor and returns the next event in {@link Mode#STRUCTURED} mode.
     */
    default ProtobufEvent nextEvent()
    {
        return nextEvent(Mode.STRUCTURED);
    }

    /**
     * Advances the cursor and returns the next event; the accessors then read the value it positions. At a
     * composite field {@code mode} chooses whether to recurse into it ({@link Mode#STRUCTURED}) or deliver
     * it as raw segment bytes ({@link Mode#SEGMENTED}). Malformed wire and wire-type/declared-type
     * mismatches raise a {@link ProtobufException}.
     * <p>
     * Returns {@code null} only when a window borrowed with {@code last == false} (see {@link #wrap} /
     * {@link #resume}) is exhausted before the message completes, signalling starvation — the caller then
     * supplies the next window via {@link #resume}. A whole-buffer cursor ({@code last == true}, the
     * default) never returns {@code null}; it completes through the root {@link ProtobufEvent#END_MESSAGE}
     * or rejects truncated input with a {@link ProtobufException}.
     */
    ProtobufEvent nextEvent(
        Mode mode);

    /**
     * The field of the current {@link ProtobufEvent#FIELD} / {@link ProtobufEvent#VALUE} or composite,
     * or {@code null} at message and segment boundaries — and always {@code null} in the schema-free
     * mode, where {@link #fieldNumber()} and {@link #wireType()} carry the wire identity instead.
     */
    ProtobufField field();

    /**
     * The message descriptor at the current depth — set at {@link ProtobufEvent#START_MESSAGE} and
     * valid through that message's scope; {@code null} in the schema-free mode and outside any message.
     */
    ProtobufMessage message();

    /**
     * The wire field number of the current field, or {@code -1} at message boundaries.
     */
    int fieldNumber();

    /**
     * The wire type of the current field, or {@code null} at message boundaries.
     */
    ProtobufWireType wireType();

    /**
     * The scalar as a 64-bit integer — for the varint, zigzag, and fixed integer types, {@code bool}
     * (0 or 1), and {@code enum} (the number).
     */
    long longValue();

    double doubleValue();

    float floatValue();

    /**
     * Non-owning view of the bytes of a {@code string} / {@code bytes} scalar, of the current value
     * chunk, of the current segment slice, or of the whole message at a {@link ProtobufEvent#START_MESSAGE};
     * its {@code [0, capacity())} is the slice. Valid on-stack only.
     */
    DirectBufferEx segment();

    /**
     * For a chunked value — a leaf {@code string}/{@code bytes} {@link ProtobufEvent#VALUE} or a
     * {@link ProtobufEvent#SEGMENT} — the number of bytes of the value still to come after this slice;
     * {@code 0} on the last (or only) piece, and {@code 0} for every other event.
     */
    int deferredBytes();

    /**
     * Advances past {@code sourceBytes} of the current value's slice so {@link #segment()} re-exposes the
     * unconsumed remainder — the pushback a bounded sink uses to stream a length-delimited value across output
     * windows without tracking its own write cursor. For a value still streaming across input windows
     * ({@link #deferredBytes()} {@code > 0}) it also commits the read cursor by exactly {@code sourceBytes}, so a
     * sub-unit tail the sink leaves unconsumed stays uncommitted and {@link #remaining()} reports it for the next
     * window — without over-committing the cursor at delivery. The default is a no-op for cursors that need no
     * pushback.
     */
    default void consumed(
        int sourceBytes)
    {
    }

    /**
     * @return the location of the current event within the message, for diagnostics
     */
    ProtobufLocation getLocation();

    /**
     * Whether this cursor reads its input verbatim, leaving the bytes intact for an identity generator. A
     * cursor over the native Protobuf wire form is identity; one that parses a foreign representation is not.
     */
    boolean identity();
}
