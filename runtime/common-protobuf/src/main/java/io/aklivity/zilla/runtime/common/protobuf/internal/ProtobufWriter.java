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
package io.aklivity.zilla.runtime.common.protobuf.internal;

import java.nio.ByteOrder;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * A reusable encode cursor over a {@link MutableDirectBuffer}. All writes advance the cursor;
 * {@link #length()} reports the bytes written since {@link #wrap(MutableDirectBuffer, int)}. The usable
 * region is {@code [offset, limit)} — a write that would cross {@code limit} throws a
 * {@link ProtobufException}, so {@code limit} is a hard bound, never overshot. The two-argument
 * {@link #wrap(MutableDirectBuffer, int)} leaves the cursor unbounded (suited to an expandable buffer).
 */
public final class ProtobufWriter
{
    // shared empty target so an unwrapped writer is in a defensible state (length reads zero) before it is
    // wrapped over the caller's destination, rather than holding a null buffer
    private static final MutableDirectBuffer EMPTY = new UnsafeBufferEx(new byte[0]);

    private MutableDirectBuffer buffer = EMPTY;
    private int start;
    private int offset;
    private int limit;

    public ProtobufWriter wrap(
        MutableDirectBuffer buffer,
        int offset)
    {
        return wrap(buffer, offset, Integer.MAX_VALUE);
    }

    public ProtobufWriter wrap(
        MutableDirectBuffer buffer,
        int offset,
        int limit)
    {
        this.buffer = buffer;
        this.start = offset;
        this.offset = offset;
        this.limit = limit;
        return this;
    }

    public int offset()
    {
        return offset;
    }

    public int length()
    {
        return offset - start;
    }

    public void writeTag(
        int fieldNumber,
        ProtobufWireType wireType)
    {
        writeVarint64(((long) fieldNumber << 3) | wireType.code());
    }

    public void writeVarint32(
        int value)
    {
        writeVarint64(value);
    }

    public void writeVarint64(
        long value)
    {
        while (true)
        {
            ensure(1);
            if ((value & ~0x7fL) == 0)
            {
                buffer.putByte(offset++, (byte) value);
                break;
            }
            else
            {
                buffer.putByte(offset++, (byte) ((value & 0x7f) | 0x80));
                value >>>= 7;
            }
        }
    }

    public void writeZigzag32(
        int value)
    {
        writeVarint64((value << 1) ^ (value >> 31));
    }

    public void writeZigzag64(
        long value)
    {
        writeVarint64((value << 1) ^ (value >> 63));
    }

    public void writeFixed32(
        int value)
    {
        ensure(4);
        buffer.putInt(offset, value, ByteOrder.LITTLE_ENDIAN);
        offset += 4;
    }

    public void writeFixed64(
        long value)
    {
        ensure(8);
        buffer.putLong(offset, value, ByteOrder.LITTLE_ENDIAN);
        offset += 8;
    }

    public void writeBytes(
        DirectBuffer source,
        int index,
        int length)
    {
        writeVarint32(length);
        ensure(length);
        buffer.putBytes(offset, source, index, length);
        offset += length;
    }

    public void writeBytes(
        byte[] source)
    {
        writeVarint32(source.length);
        ensure(source.length);
        buffer.putBytes(offset, source);
        offset += source.length;
    }

    /**
     * Copies {@code length} bytes from {@code source} verbatim — no length prefix — used to pass an
     * unknown field through unchanged, tag and value together.
     */
    public void writeRaw(
        DirectBuffer source,
        int index,
        int length)
    {
        ensure(length);
        buffer.putBytes(offset, source, index, length);
        offset += length;
    }

    /**
     * Advances the cursor by {@code length} bytes without writing, returning the absolute offset of the
     * reserved region — used to hold a fixed-width length slot that {@link #putPaddedVarint} fills later.
     */
    public int reserve(
        int length)
    {
        ensure(length);
        int at = offset;
        offset += length;
        return at;
    }

    private void ensure(
        int additional)
    {
        if (offset + additional > limit)
        {
            throw new ProtobufException("output exceeds limit by " + (offset + additional - limit) + " bytes");
        }
    }

    /**
     * Patches a fixed {@code width}-byte varint of {@code value} at absolute offset {@code at} without
     * moving the cursor. The encoding is non-minimal when {@code value} would fit in fewer bytes (the
     * leading bytes carry the continuation bit), which is what lets a reserved length slot be filled in
     * place — no body shift. {@code value} must fit in {@code width * 7} bits.
     */
    public void putPaddedVarint(
        int at,
        int value,
        int width)
    {
        long remaining = value & 0xffffffffL;
        for (int i = 0; i < width; i++)
        {
            int b = (int) (remaining & 0x7f);
            if (i < width - 1)
            {
                b |= 0x80;
            }
            buffer.putByte(at + i, (byte) b);
            remaining >>>= 7;
        }
    }
}
