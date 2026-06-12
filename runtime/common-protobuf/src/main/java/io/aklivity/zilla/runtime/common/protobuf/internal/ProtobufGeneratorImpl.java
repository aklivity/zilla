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
 * Buffer-backed {@link ProtobufGenerator}. Nested messages reserve a length slot sized to an optimistic
 * estimate ({@link #startMessage(int, int)}), stream their body straight to the output, and fill the slot
 * on {@link #endMessage()} — minimal when the estimate's varint width was right, padded within it
 * otherwise, never shifted. {@link #flush()} closes the open levels into a drainable chunk and a following
 * {@link #wrap(MutableDirectBuffer, int, int)} reopens them, so a message larger than the buffer streams
 * as merge-able records.
 */
public final class ProtobufGeneratorImpl implements ProtobufGenerator
{
    private static final int KIND_MESSAGE = 0;
    private static final int KIND_GROUP = 1;

    private final ProtobufWriter writer;

    private int[] fields;
    private int[] kinds;
    private int[] slots;
    private int[] widths;
    private int depth;
    private int limit;
    private boolean flushed;

    public ProtobufGeneratorImpl()
    {
        this.writer = new ProtobufWriter();
        this.fields = new int[8];
        this.kinds = new int[8];
        this.slots = new int[8];
        this.widths = new int[8];
    }

    @Override
    public ProtobufGenerator wrap(
        MutableDirectBuffer buffer,
        int offset,
        int limit)
    {
        if (limit > buffer.capacity() - offset)
        {
            throw new IllegalArgumentException("limit " + limit + " exceeds capacity " +
                (buffer.capacity() - offset));
        }
        this.limit = limit;
        writer.wrap(buffer, offset, offset + limit);
        if (flushed)
        {
            flushed = false;
            for (int i = 0; i < depth; i++)
            {
                reopen(i);
            }
        }
        else
        {
            depth = 0;
        }
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
        int width = varintSize(length);
        int slot = writer.reserve(width);
        push(field, KIND_MESSAGE, slot, width);
        return this;
    }

    @Override
    public ProtobufGenerator endMessage()
    {
        int level = depth - 1;
        if (kinds[level] != KIND_MESSAGE)
        {
            throw new ProtobufException("open group, expected endGroup");
        }
        fillMessage(level);
        depth--;
        return this;
    }

    @Override
    public ProtobufGenerator startGroup(
        int field)
    {
        writer.writeTag(field, ProtobufWireType.SGROUP);
        push(field, KIND_GROUP, 0, 0);
        return this;
    }

    @Override
    public ProtobufGenerator endGroup()
    {
        int level = depth - 1;
        if (kinds[level] != KIND_GROUP)
        {
            throw new ProtobufException("open message, expected endMessage");
        }
        writer.writeTag(fields[level], ProtobufWireType.EGROUP);
        depth--;
        return this;
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

    @Override
    public ProtobufGenerator flush()
    {
        for (int level = depth - 1; level >= 0; level--)
        {
            if (kinds[level] == KIND_MESSAGE)
            {
                fillMessage(level);
            }
            else
            {
                writer.writeTag(fields[level], ProtobufWireType.EGROUP);
            }
        }
        flushed = true;
        return this;
    }

    private void fillMessage(
        int level)
    {
        int bodyLength = writer.offset() - (slots[level] + widths[level]);
        if (varintSize(bodyLength) > widths[level])
        {
            throw new ProtobufException("nested message body " + bodyLength +
                " exceeds reserved length width " + widths[level]);
        }
        writer.putPaddedVarint(slots[level], bodyLength, widths[level]);
    }

    private void reopen(
        int level)
    {
        if (kinds[level] == KIND_MESSAGE)
        {
            writer.writeTag(fields[level], ProtobufWireType.LEN);
            slots[level] = writer.reserve(widths[level]);
        }
        else
        {
            writer.writeTag(fields[level], ProtobufWireType.SGROUP);
        }
    }

    private void push(
        int field,
        int kind,
        int slot,
        int width)
    {
        if (depth >= fields.length)
        {
            fields = Arrays.copyOf(fields, fields.length * 2);
            kinds = Arrays.copyOf(kinds, kinds.length * 2);
            slots = Arrays.copyOf(slots, slots.length * 2);
            widths = Arrays.copyOf(widths, widths.length * 2);
        }
        fields[depth] = field;
        kinds[depth] = kind;
        slots[depth] = slot;
        widths[depth] = width;
        depth++;
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

    // lent to the untyped sink, which splices raw values by wire type with no public equivalent
    ProtobufWriter writer()
    {
        return writer;
    }
}
