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
 * Immutable, read-only view of the value observed at the current event as a {@link ProtobufStream}
 * pipeline pumps events through its stages. It exposes the current {@link #field()} and typed
 * accessors for the scalar at a {@link ProtobufEvent#VALUE} event (chosen by {@link ProtobufField#type()}),
 * or the raw slice at a {@link ProtobufEvent#segmented()} event. It has no cursor advance — pumping is
 * the driver's job — so a stage cannot disturb the pump.
 */
public interface ProtobufSource
{
    /**
     * The field of the current {@link ProtobufEvent#FIELD} / {@link ProtobufEvent#VALUE} or composite,
     * or {@code null} at message and segment boundaries — and always {@code null} in the schema-free
     * mode, where {@link #fieldNumber()} and {@link #wireType()} carry the wire identity instead.
     */
    ProtobufField field();

    /**
     * The message descriptor at the current depth — set at {@link ProtobufEvent#START_MESSAGE} and
     * valid through that message's scope; {@code null} in the schema-free mode and outside any message.
     * A stage reads it (then walks the descriptor graph via {@link ProtobufField#message()}) to reason
     * about the current message without a separate schema reference.
     */
    ProtobufMessage message();

    /**
     * The wire field number of the current field, or {@code -1} at message boundaries. Available in
     * both the schema-driven and schema-free modes.
     */
    int fieldNumber();

    /**
     * The wire type of the current field, or {@code null} at message boundaries. Available in both
     * the schema-driven and schema-free modes.
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
    DirectBuffer segment();

    /**
     * The number of bytes of the current value still to come after this slice — {@code 0} on the last
     * (or only) piece, and {@code 0} for every event that is not a value piece. A length-delimited value
     * larger than the input window arrives as repeated pieces with a decreasing {@code deferredBytes()},
     * mirroring the generator's {@code writeSegment(..., deferred)}: a leaf {@code string}/{@code bytes}
     * scalar streams as repeated {@link ProtobufEvent#VALUE}s, a raw composite as repeated
     * {@link ProtobufEvent#SEGMENT}s.
     */
    int deferredBytes();
}
