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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * Buffer-backed {@link ProtobufGenerator} over a single writer — no scratch, no back-patch.
 * {@link #startMessage(int, int)} writes the tag and length prefix immediately (the body length is
 * known up front), then the body streams straight to the output; {@link #endMessage()} verifies the
 * body matched the declared length. The single writer is also lent (via {@link #writer()}) to the
 * wire sinks.
 */
public final class ProtobufGeneratorImpl implements ProtobufGenerator
{
    private static final int GROUP_LEVEL = -1;
    private static final int PADDED_LEVEL = -2;

    private final ProtobufWriter writer;

    private int[] ends;
    private int[] groupFields;
    private int[] slots;
    private int depth;
    private int limit;
    private int slotWidth;

    public ProtobufGeneratorImpl()
    {
        this.writer = new ProtobufWriter();
        this.ends = new int[8];
        this.groupFields = new int[8];
        this.slots = new int[8];
    }

    @Override
    public ProtobufGenerator wrap(
        MutableDirectBuffer buffer,
        int offset)
    {
        return wrap(buffer, offset, buffer.capacity() - offset);
    }

    @Override
    public ProtobufGenerator wrap(
        MutableDirectBuffer buffer,
        int offset,
        int limit)
    {
        depth = 0;
        this.limit = limit;
        this.slotWidth = varintSize(limit);
        writer.wrap(buffer, offset);
        return this;
    }

    @Override
    public int length()
    {
        return writer.length();
    }

    @Override
    public int remaining()
    {
        return limit - writer.length();
    }

    @Override
    public ProtobufGenerator writeInt32(
        int field,
        int value)
    {
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeVarint64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeInt64(
        int field,
        long value)
    {
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeVarint64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeUInt32(
        int field,
        int value)
    {
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeVarint64(value & 0xffffffffL);
        return this;
    }

    @Override
    public ProtobufGenerator writeUInt64(
        int field,
        long value)
    {
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeVarint64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeSInt32(
        int field,
        int value)
    {
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeZigzag32(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeSInt64(
        int field,
        long value)
    {
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeZigzag64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeFixed32(
        int field,
        int value)
    {
        writer.writeTag(field, ProtobufWireType.I32);
        writer.writeFixed32(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeFixed64(
        int field,
        long value)
    {
        writer.writeTag(field, ProtobufWireType.I64);
        writer.writeFixed64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeSFixed32(
        int field,
        int value)
    {
        writer.writeTag(field, ProtobufWireType.I32);
        writer.writeFixed32(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeSFixed64(
        int field,
        long value)
    {
        writer.writeTag(field, ProtobufWireType.I64);
        writer.writeFixed64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeFloat(
        int field,
        float value)
    {
        writer.writeTag(field, ProtobufWireType.I32);
        writer.writeFixed32(Float.floatToIntBits(value));
        return this;
    }

    @Override
    public ProtobufGenerator writeDouble(
        int field,
        double value)
    {
        writer.writeTag(field, ProtobufWireType.I64);
        writer.writeFixed64(Double.doubleToLongBits(value));
        return this;
    }

    @Override
    public ProtobufGenerator writeBool(
        int field,
        boolean value)
    {
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeVarint64(value ? 1L : 0L);
        return this;
    }

    @Override
    public ProtobufGenerator writeEnum(
        int field,
        int number)
    {
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeVarint64(number);
        return this;
    }

    @Override
    public ProtobufGenerator writeString(
        int field,
        String value)
    {
        writer.writeTag(field, ProtobufWireType.LEN);
        writer.writeBytes(value.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    @Override
    public ProtobufGenerator writeBytes(
        int field,
        byte[] value)
    {
        writer.writeTag(field, ProtobufWireType.LEN);
        writer.writeBytes(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeBytes(
        int field,
        DirectBuffer value,
        int offset,
        int length)
    {
        writer.writeTag(field, ProtobufWireType.LEN);
        writer.writeBytes(value, offset, length);
        return this;
    }

    @Override
    public ProtobufGenerator writeMessage(
        int field,
        DirectBuffer message,
        int offset,
        int length)
    {
        writer.writeTag(field, ProtobufWireType.LEN);
        writer.writeBytes(message, offset, length);
        return this;
    }

    @Override
    public ProtobufGenerator startMessage(
        int field,
        int length)
    {
        writer.writeTag(field, ProtobufWireType.LEN);
        writer.writeVarint32(length);
        push();
        ends[depth] = writer.length() + length;
        return this;
    }

    @Override
    public ProtobufGenerator startMessage(
        int field)
    {
        writer.writeTag(field, ProtobufWireType.LEN);
        int slot = writer.reserve(slotWidth);
        push();
        ends[depth] = PADDED_LEVEL;
        slots[depth] = slot;
        return this;
    }

    @Override
    public ProtobufGenerator endMessage()
    {
        if (ends[depth] == GROUP_LEVEL)
        {
            throw new ProtobufException("open group, expected endGroup");
        }
        else if (ends[depth] == PADDED_LEVEL)
        {
            int slot = slots[depth];
            int bodyLength = writer.offset() - (slot + slotWidth);
            depth--;
            writer.putPaddedVarint(slot, bodyLength, slotWidth);
        }
        else
        {
            int expected = ends[depth];
            depth--;
            if (writer.length() != expected)
            {
                throw new ProtobufException("message body length mismatch: expected " + expected +
                    " but wrote " + writer.length());
            }
        }
        return this;
    }

    @Override
    public ProtobufGenerator startGroup(
        int field)
    {
        writer.writeTag(field, ProtobufWireType.SGROUP);
        push();
        ends[depth] = GROUP_LEVEL;
        groupFields[depth] = field;
        return this;
    }

    @Override
    public ProtobufGenerator endGroup()
    {
        if (ends[depth] != GROUP_LEVEL)
        {
            throw new ProtobufException("open message, expected endMessage");
        }
        int field = groupFields[depth];
        depth--;
        writer.writeTag(field, ProtobufWireType.EGROUP);
        return this;
    }

    private void push()
    {
        depth++;
        if (depth >= ends.length)
        {
            ends = Arrays.copyOf(ends, ends.length * 2);
            groupFields = Arrays.copyOf(groupFields, groupFields.length * 2);
            slots = Arrays.copyOf(slots, slots.length * 2);
        }
    }

    private static int varintSize(
        int value)
    {
        long remaining = value & 0xffffffffL;
        int size = 1;
        while (remaining >= 0x80L)
        {
            remaining >>>= 7;
            size++;
        }
        return size;
    }

    @Override
    public ProtobufGenerator writeRaw(
        DirectBuffer source,
        int offset,
        int length)
    {
        writer.writeRaw(source, offset, length);
        return this;
    }

    ProtobufWriter writer()
    {
        return writer;
    }
}
