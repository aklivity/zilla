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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * Buffer-backed {@link ProtobufGenerator}. The root writer (index 0) targets the caller's output
 * buffer; {@link #beginMessage(int)} pushes a pooled scratch writer for the nested body and
 * {@link #endMessage()} pops it, back-patching the length into the parent. Scratch writers and
 * buffers are pooled by depth and reused across {@link #wrap(MutableDirectBuffer, int)}, so nesting
 * allocates nothing after warmup. The root writer is also lent (via {@link #writer()}) to the wire
 * sinks for their low-level encoding.
 */
public final class ProtobufGeneratorImpl implements ProtobufGenerator
{
    private final List<ProtobufWriter> writers;
    private final List<ExpandableArrayBuffer> buffers;

    private int[] messageFields;
    private int depth;
    private ProtobufWriter current;

    public ProtobufGeneratorImpl()
    {
        this.writers = new ArrayList<>();
        this.buffers = new ArrayList<>();
        this.writers.add(new ProtobufWriter());
        this.buffers.add(null);
        this.messageFields = new int[8];
        this.current = writers.get(0);
    }

    @Override
    public ProtobufGenerator wrap(
        MutableDirectBuffer buffer,
        int offset)
    {
        depth = 0;
        current = writers.get(0).wrap(buffer, offset);
        return this;
    }

    @Override
    public int length()
    {
        return writers.get(0).length();
    }

    @Override
    public ProtobufGenerator writeInt32(
        int field,
        int value)
    {
        current.writeTag(field, ProtobufWireType.VARINT);
        current.writeVarint64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeInt64(
        int field,
        long value)
    {
        current.writeTag(field, ProtobufWireType.VARINT);
        current.writeVarint64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeUInt32(
        int field,
        int value)
    {
        current.writeTag(field, ProtobufWireType.VARINT);
        current.writeVarint64(value & 0xffffffffL);
        return this;
    }

    @Override
    public ProtobufGenerator writeUInt64(
        int field,
        long value)
    {
        current.writeTag(field, ProtobufWireType.VARINT);
        current.writeVarint64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeSInt32(
        int field,
        int value)
    {
        current.writeTag(field, ProtobufWireType.VARINT);
        current.writeZigzag32(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeSInt64(
        int field,
        long value)
    {
        current.writeTag(field, ProtobufWireType.VARINT);
        current.writeZigzag64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeFixed32(
        int field,
        int value)
    {
        current.writeTag(field, ProtobufWireType.I32);
        current.writeFixed32(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeFixed64(
        int field,
        long value)
    {
        current.writeTag(field, ProtobufWireType.I64);
        current.writeFixed64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeSFixed32(
        int field,
        int value)
    {
        current.writeTag(field, ProtobufWireType.I32);
        current.writeFixed32(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeSFixed64(
        int field,
        long value)
    {
        current.writeTag(field, ProtobufWireType.I64);
        current.writeFixed64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeFloat(
        int field,
        float value)
    {
        current.writeTag(field, ProtobufWireType.I32);
        current.writeFixed32(Float.floatToIntBits(value));
        return this;
    }

    @Override
    public ProtobufGenerator writeDouble(
        int field,
        double value)
    {
        current.writeTag(field, ProtobufWireType.I64);
        current.writeFixed64(Double.doubleToLongBits(value));
        return this;
    }

    @Override
    public ProtobufGenerator writeBool(
        int field,
        boolean value)
    {
        current.writeTag(field, ProtobufWireType.VARINT);
        current.writeVarint64(value ? 1L : 0L);
        return this;
    }

    @Override
    public ProtobufGenerator writeEnum(
        int field,
        int number)
    {
        current.writeTag(field, ProtobufWireType.VARINT);
        current.writeVarint64(number);
        return this;
    }

    @Override
    public ProtobufGenerator writeString(
        int field,
        String value)
    {
        current.writeTag(field, ProtobufWireType.LEN);
        current.writeBytes(value.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    @Override
    public ProtobufGenerator writeBytes(
        int field,
        byte[] value)
    {
        current.writeTag(field, ProtobufWireType.LEN);
        current.writeBytes(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeBytes(
        int field,
        DirectBuffer value,
        int offset,
        int length)
    {
        current.writeTag(field, ProtobufWireType.LEN);
        current.writeBytes(value, offset, length);
        return this;
    }

    @Override
    public ProtobufGenerator writeMessage(
        int field,
        DirectBuffer message,
        int offset,
        int length)
    {
        current.writeTag(field, ProtobufWireType.LEN);
        current.writeBytes(message, offset, length);
        return this;
    }

    @Override
    public ProtobufGenerator beginMessage(
        int field)
    {
        depth++;
        while (writers.size() <= depth)
        {
            writers.add(new ProtobufWriter());
            buffers.add(new ExpandableArrayBuffer());
        }
        if (depth >= messageFields.length)
        {
            messageFields = Arrays.copyOf(messageFields, messageFields.length * 2);
        }
        messageFields[depth] = field;
        current = writers.get(depth).wrap(buffers.get(depth), 0);
        return this;
    }

    @Override
    public ProtobufGenerator endMessage()
    {
        ProtobufWriter child = current;
        ExpandableArrayBuffer childBuffer = buffers.get(depth);
        int field = messageFields[depth];
        depth--;
        current = writers.get(depth);
        current.writeTag(field, ProtobufWireType.LEN);
        current.writeBytes(childBuffer, 0, child.length());
        return this;
    }

    @Override
    public ProtobufGenerator writeRaw(
        DirectBuffer source,
        int offset,
        int length)
    {
        current.writeRaw(source, offset, length);
        return this;
    }

    ProtobufWriter writer()
    {
        return writers.get(0);
    }
}
