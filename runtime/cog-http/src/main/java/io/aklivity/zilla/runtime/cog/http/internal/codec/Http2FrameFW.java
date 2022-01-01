/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.cog.http.internal.codec;

import static java.nio.ByteOrder.BIG_ENDIAN;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.cog.http.internal.stream.Http2Flags;
import io.aklivity.zilla.runtime.cog.http.internal.types.Flyweight;

/*
    Flyweight for for all HTTP2 frames

    +-----------------------------------------------+
    |                 Length (24)                   |
    +---------------+---------------+---------------+
    |   Type (8)    |   Flags (8)   |
    +-+-------------+---------------+-------------------------------+
    |R|                 Stream Identifier (31)                      |
    +=+=============================================================+
    |                   Frame Payload (0...)                      ...
    +---------------------------------------------------------------+
 */
public class Http2FrameFW extends Flyweight
{
    private static final int LENGTH_OFFSET = 0;
    private static final int TYPE_OFFSET = 3;
    private static final int FLAGS_OFFSET = 4;
    private static final int STREAM_ID_OFFSET = 5;
    private static final int PAYLOAD_OFFSET = 9;

    private final AtomicBuffer payloadRO = new UnsafeBuffer(new byte[0]);

    public int length()
    {
        final DirectBuffer buffer = buffer();
        final int offset = offset();

        return ((buffer.getByte(offset + LENGTH_OFFSET) & 0xFF) << Short.SIZE) |
               (buffer.getShort(offset + LENGTH_OFFSET + Byte.BYTES, BIG_ENDIAN) & 0xFF_FF);
    }

    public Http2FrameType type()
    {
        return Http2FrameType.get(buffer().getByte(offset() + TYPE_OFFSET));
    }

    public final byte flags()
    {
        return buffer().getByte(offset() + FLAGS_OFFSET);
    }

    public final boolean endStream()
    {
        return Http2Flags.endStream(flags());
    }

    public int streamId()
    {
        return buffer().getInt(offset() + STREAM_ID_OFFSET, BIG_ENDIAN) & 0x7F_FF_FF_FF;
    }

    public final int payloadOffset()
    {
        return offset() + PAYLOAD_OFFSET;
    }

    public final DirectBuffer payload()
    {
        return payloadRO;
    }

    @Override
    public final int limit()
    {
        return offset() + PAYLOAD_OFFSET + length();
    }

    public Http2FrameFW tryWrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        // TODO: super.tryWrap != null
        boolean wrappable = super.wrap(buffer, offset, maxLimit) != null;

        wrappable &= maxLimit - offset >= 9;
        wrappable &= maxLimit - offset >= 9 + length();

        return wrappable ? wrap(buffer, offset, maxLimit) : null;
    }

    @Override
    public Http2FrameFW wrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        if (maxLimit - offset < 9)
        {
            throw new IllegalArgumentException("Invalid HTTP2 frame - not enough bytes for 9-octet header");
        }
        super.wrap(buffer, offset, maxLimit);

        final int payloadLength = length();
        if (maxLimit - offset < 9 + payloadLength)
        {
            throw new IllegalArgumentException("Invalid HTTP2 frame - not enough payload bytes");
        }

        if (payloadLength > 0)
        {
            payloadRO.wrap(buffer, offset() + PAYLOAD_OFFSET, payloadLength);
        }
        else
        {
            payloadRO.wrap(0, 0);
        }

        checkLimit(limit(), maxLimit);

        return this;
    }

    @Override
    public String toString()
    {
        return String.format("%s frame <length=%s, flags=%s, id=%s>",
                type(), length(), flags(), streamId());
    }

    @SuppressWarnings("rawtypes")
    protected static class Builder<B extends Builder, T extends Http2FrameFW> extends Flyweight.Builder<T>
    {
        private final Http2FrameFW frame;

        public Builder(T frame)
        {
            super(frame);
            this.frame = frame;
        }

        @Override
        @SuppressWarnings("unchecked")
        public B wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);

            buffer.putByte(offset + TYPE_OFFSET, frame.type().type());
            buffer.putByte(offset + FLAGS_OFFSET, (byte) 0);
            buffer.putInt(offset + STREAM_ID_OFFSET, 0, BIG_ENDIAN);
            payloadLength(0);

            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public final B flags(byte f)
        {
            byte flags = buffer().getByte(offset() + FLAGS_OFFSET);
            flags |= f;
            buffer().putByte(offset() + FLAGS_OFFSET, flags);
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public final B streamId(int streamId)
        {
            buffer().putInt(offset() + STREAM_ID_OFFSET, streamId, BIG_ENDIAN);
            return (B) this;
        }

        public final B payload(DirectBuffer buffer)
        {
            return payload(buffer, 0, buffer.capacity());
        }

        @SuppressWarnings("unchecked")
        public B payload(DirectBuffer payload, int offset, int length)
        {
            buffer().putBytes(offset() + PAYLOAD_OFFSET, payload, offset, length);
            payloadLength(length);
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        protected final B payloadLength(
            int length)
        {
            final MutableDirectBuffer buffer = buffer();
            final int offset = offset();

            buffer.putByte(offset + LENGTH_OFFSET, (byte) ((length & 0x00_FF_00_00) >>> Short.SIZE));
            buffer.putShort(offset + LENGTH_OFFSET + Byte.BYTES, (short) (length & 0x00_00_FF_FF), BIG_ENDIAN);

            limit(offset + PAYLOAD_OFFSET + length);
            return (B) this;
        }
    }
}
