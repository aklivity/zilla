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

/**
 * The pull cursor of a {@code common-protobuf} pipeline. A schema-bound parser
 * ({@link Protobuf#parser(ProtobufSchema, String)}) decodes a message into a typed event stream;
 * a schema-free parser ({@link Protobuf#parser()}) tokenizes the wire into generic events. Reuse a
 * single instance per worker thread.
 * <p>
 * It can be driven directly: {@link #wrap} borrows a fully-buffered message, then a
 * {@link #hasNext()} / {@link #nextEvent()} loop pulls one {@link ProtobufEvent} at a time, with
 * the current field and value read through this parser's accessors ({@link #field()},
 * {@link #longValue()}, {@link #buffer()}, …) — the same surface a {@link ProtobufSource} exposes to a
 * pipeline stage, but cursor-bearing. {@link #stream()} layers the push pipeline over the same cursor;
 * stages there receive a non-advancing {@link ProtobufSource} view instead.
 */
public interface ProtobufParser
{
    /**
     * Borrows {@code buffer} as the input for the next pull, the message occupying {@code [offset,
     * offset + length)}, and rewinds the cursor to before the root {@link ProtobufEvent#START_MESSAGE}.
     */
    ProtobufParser wrap(
        DirectBuffer buffer,
        int offset,
        int length);

    /**
     * {@code true} until the root {@link ProtobufEvent#END_MESSAGE} has been pulled.
     */
    boolean hasNext();

    /**
     * Advances the cursor and returns the next event; the accessors then read the value it positions.
     * Malformed wire and wire-type/declared-type mismatches raise a {@link ProtobufException}.
     */
    ProtobufEvent nextEvent();

    /**
     * Begins a push pipeline pumped by this parser; append stages with {@link ProtobufStream#transform}
     * and terminate with {@link ProtobufStream#into}.
     */
    ProtobufStream stream();

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
     * Non-owning view of the bytes of a {@code string} / {@code bytes} scalar, of the current segment
     * slice, or of the whole message at a {@link ProtobufEvent#START_MESSAGE}; valid on-stack only.
     * Use with {@link #offset()} and {@link #length()}.
     */
    DirectBuffer buffer();

    int offset();

    int length();
}
