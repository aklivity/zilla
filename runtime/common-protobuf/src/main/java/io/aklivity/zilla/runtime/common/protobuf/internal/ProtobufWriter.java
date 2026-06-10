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

import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * A reusable encode cursor over a {@link MutableDirectBuffer}. All writes advance the cursor;
 * {@link #length()} reports the bytes written since {@link #wrap(MutableDirectBuffer, int)}.
 */
public final class ProtobufWriter
{
    private MutableDirectBuffer buffer;
    private int start;
    private int offset;

    public ProtobufWriter wrap(
        MutableDirectBuffer buffer,
        int offset)
    {
        this.buffer = buffer;
        this.start = offset;
        this.offset = offset;
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
        buffer.putInt(offset, value, ByteOrder.LITTLE_ENDIAN);
        offset += 4;
    }

    public void writeFixed64(
        long value)
    {
        buffer.putLong(offset, value, ByteOrder.LITTLE_ENDIAN);
        offset += 8;
    }

    public void writeBytes(
        DirectBuffer source,
        int index,
        int length)
    {
        writeVarint32(length);
        buffer.putBytes(offset, source, index, length);
        offset += length;
    }

    public void writeBytes(
        byte[] source)
    {
        writeVarint32(source.length);
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
        buffer.putBytes(offset, source, index, length);
        offset += length;
    }
}
