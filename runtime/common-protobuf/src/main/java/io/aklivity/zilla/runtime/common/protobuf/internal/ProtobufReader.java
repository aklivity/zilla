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
 * A reusable decode cursor over a {@link DirectBuffer} region. All reads advance {@link #offset()}.
 * <p>
 * The cursor separates two bounds: {@link #limit()} is the end of the bytes currently <em>available</em>
 * (refillable across windows), while a higher layer tracks each scope's semantic end via the swap-safe
 * {@link #position()} counter. A read that would cross {@link #limit()} resolves by the {@code last} flag:
 * when {@code last} (the whole-buffer contract) it raises a {@link ProtobufException} — truncated input is
 * rejected — otherwise it sets {@link #starved()} and returns without reading out of bounds, so the caller
 * can {@link #rewind()} to the last {@link #mark()} (a committed unit boundary) and report starvation.
 * <p>
 * The cursor never copies or retains bytes: it reads directly from the window it is given. Whatever the
 * driver has not yet consumed (everything at or after {@link #position()}) is the driver's to retain and
 * re-present, contiguous with the next window, on {@link #resume}.
 */
public final class ProtobufReader
{
    private DirectBuffer buffer;
    private int offset;
    private long start;
    private int progress;
    private int limit;
    private int mark;
    private boolean last;
    private boolean starved;

    public ProtobufReader wrap(
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        return wrap(buffer, offset, limit, true);
    }

    public ProtobufReader wrap(
        DirectBuffer buffer,
        int offset,
        int limit,
        boolean last)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.start = 0L;
        this.progress = offset;
        this.limit = limit;
        this.mark = offset;
        this.last = last;
        this.starved = false;
        return this;
    }

    public ProtobufReader resume(
        DirectBuffer buffer,
        int offset,
        int limit,
        boolean last)
    {
        // the driver re-presents everything not yet consumed at the front of this window, so the cursor
        // simply continues from its committed position against the new, contiguous bytes
        long position = position();
        this.buffer = buffer;
        this.offset = offset;
        this.start = position;
        this.progress = offset;
        this.limit = limit;
        this.mark = offset;
        this.last = last;
        this.starved = false;
        return this;
    }

    public int offset()
    {
        return progress;
    }

    public int limit()
    {
        return limit;
    }

    public long position()
    {
        return start + (progress - offset);
    }

    public int remaining()
    {
        return limit - progress;
    }

    public boolean last()
    {
        return last;
    }

    public boolean starved()
    {
        return starved;
    }

    public int available()
    {
        return limit - progress;
    }

    public boolean hasRemaining()
    {
        return progress < limit;
    }

    public void mark()
    {
        this.mark = progress;
        this.starved = false;
    }

    public void rewind()
    {
        this.progress = mark;
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
            if (progress >= limit)
            {
                if (last)
                {
                    throw new ProtobufException("truncated varint");
                }
                starved = true;
                break;
            }
            int b = buffer.getByte(progress++) & 0xff;
            value |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0)
            {
                complete = true;
                break;
            }
            shift += 7;
        }
        if (!complete && !starved)
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
        int value = 0;
        if (require(4))
        {
            value = buffer.getInt(progress, ByteOrder.LITTLE_ENDIAN);
            progress += 4;
        }
        return value;
    }

    public long readFixed64()
    {
        long value = 0L;
        if (require(8))
        {
            value = buffer.getLong(progress, ByteOrder.LITTLE_ENDIAN);
            progress += 8;
        }
        return value;
    }

    public int readLength()
    {
        int length = readVarint32();
        if (!starved)
        {
            if (length < 0)
            {
                throw new ProtobufException("negative length " + length);
            }
            require(length);
        }
        return length;
    }

    public DirectBuffer buffer()
    {
        return buffer;
    }

    public void skip(
        int length)
    {
        if (require(length))
        {
            progress += length;
        }
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

    public void skipField(
        int number,
        ProtobufWireType wireType)
    {
        if (wireType == ProtobufWireType.SGROUP)
        {
            skipGroup(number);
        }
        else
        {
            skipField(wireType);
        }
    }

    /**
     * Skips a proto2 group body, leaving the cursor just past the matching {@code EGROUP} tag, and
     * returns the offset of that {@code EGROUP} tag (i.e. the end of the group body). Nested groups
     * are handled recursively; an unterminated or mismatched group is rejected. A group that runs past
     * the available bytes under {@code !last} sets {@link #starved()} and returns early.
     */
    public int skipGroup(
        int number)
    {
        int end = -1;
        while (end < 0)
        {
            if (progress >= limit)
            {
                if (last)
                {
                    throw new ProtobufException("unterminated group " + number);
                }
                starved = true;
                end = progress;
                break;
            }
            int tagOffset = progress;
            int tag = readVarint32();
            if (starved)
            {
                end = tagOffset;
                break;
            }
            int fieldNumber = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (wireType == ProtobufWireType.EGROUP)
            {
                if (fieldNumber != number)
                {
                    throw new ProtobufException("mismatched group end " + fieldNumber + " for " + number);
                }
                end = tagOffset;
            }
            else
            {
                skipField(fieldNumber, wireType);
                if (starved)
                {
                    end = tagOffset;
                    break;
                }
            }
        }
        return end;
    }

    private boolean require(
        int length)
    {
        boolean available = progress + length <= limit;
        if (!available)
        {
            if (last)
            {
                throw new ProtobufException("truncated field: need " + length + " bytes");
            }
            starved = true;
        }
        return available;
    }
}
