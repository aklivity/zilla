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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * Buffer-backed {@link ProtobufGenerator} over a single root {@link ProtobufWriter}, which it also
 * lends (via {@link #writer()}) to the wire sinks for their low-level encoding.
 */
public final class ProtobufGeneratorImpl implements ProtobufGenerator
{
    private final ProtobufWriter writer;

    public ProtobufGeneratorImpl()
    {
        this.writer = new ProtobufWriter();
    }

    @Override
    public ProtobufGenerator wrap(
        MutableDirectBuffer buffer,
        int offset)
    {
        writer.wrap(buffer, offset);
        return this;
    }

    @Override
    public int length()
    {
        return writer.length();
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
