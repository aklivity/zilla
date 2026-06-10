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

import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * A reusable decode cursor over a {@link DirectBuffer} region. All reads advance {@link #offset()};
 * truncated or overlong varints and wire types that run past the region limit raise a
 * {@link ProtobufException} so malformed input is rejected rather than read out of bounds.
 */
public final class ProtobufReader
{
    private DirectBuffer buffer;
    private int offset;
    private int limit;

    public ProtobufReader wrap(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.limit = offset + length;
        return this;
    }

    public int offset()
    {
        return offset;
    }

    public int limit()
    {
        return limit;
    }

    public boolean hasRemaining()
    {
        return offset < limit;
    }

    public int readVarint32()
    {
        return (int) readVarint64();
    }

    public long readVarint64()
    {
        long value = 0L;
        int shift = 0;
        boolean complete = false;
        while (shift < 64)
        {
            if (offset >= limit)
            {
                throw new ProtobufException("truncated varint");
            }
            int b = buffer.getByte(offset++) & 0xff;
            value |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0)
            {
                complete = true;
                break;
            }
            shift += 7;
        }
        if (!complete)
        {
            throw new ProtobufException("malformed varint");
        }
        return value;
    }

    public int readZigzag32()
    {
        int n = readVarint32();
        return (n >>> 1) ^ -(n & 1);
    }

    public long readZigzag64()
    {
        long n = readVarint64();
        return (n >>> 1) ^ -(n & 1);
    }

    public int readFixed32()
    {
        require(4);
        int value = buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
        offset += 4;
        return value;
    }

    public long readFixed64()
    {
        require(8);
        long value = buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN);
        offset += 8;
        return value;
    }

    public int readLength()
    {
        int length = readVarint32();
        if (length < 0)
        {
            throw new ProtobufException("negative length " + length);
        }
        require(length);
        return length;
    }

    public DirectBuffer buffer()
    {
        return buffer;
    }

    public void skip(
        int length)
    {
        require(length);
        offset += length;
    }

    public void skipField(
        ProtobufWireType wireType)
    {
        switch (wireType)
        {
        case VARINT:
            readVarint64();
            break;
        case I64:
            skip(8);
            break;
        case LEN:
            skip(readLength());
            break;
        case I32:
            skip(4);
            break;
        default:
            throw new ProtobufException("cannot skip wire type " + wireType);
        }
    }

    private void require(
        int length)
    {
        if (offset + length > limit)
        {
            throw new ProtobufException("truncated field: need " + length + " bytes");
        }
    }
}
