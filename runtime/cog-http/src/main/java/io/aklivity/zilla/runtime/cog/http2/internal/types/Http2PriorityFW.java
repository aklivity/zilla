/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.http2.internal.types;

import static io.aklivity.zilla.runtime.cog.http2.internal.types.Http2FrameType.PRIORITY;
import static java.nio.ByteOrder.BIG_ENDIAN;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/*

    Flyweight for HTTP2 PRIORITY frame

    +-----------------------------------------------+
    |                 Length (24)                   |
    +---------------+---------------+---------------+
    |   Type (8)    |   Flags (8)   |
    +-+-------------+---------------+-------------------------------+
    |R|                 Stream Identifier (31)                      |
    +=+=============================================================+
    |E|                  Stream Dependency (31)                     |
    +-+-------------+-----------------------------------------------+
    |   Weight (8)  |
    +-+-------------+

 */
public class Http2PriorityFW extends Http2FrameFW
{
    private static final int PAYLOAD_OFFSET = 9;

    @Override
    public Http2FrameType type()
    {
        return PRIORITY;
    }

    public boolean exclusive()
    {
        return (buffer().getByte(offset() + PAYLOAD_OFFSET) & 0x80) != 0;
    }

    public int parentStream()
    {
        return buffer().getInt(offset() + PAYLOAD_OFFSET, BIG_ENDIAN) & 0x7F_FF_FF_FF;
    }

    public int weight()
    {
        int weight = buffer().getByte(offset() + PAYLOAD_OFFSET + 4) & 0xFF;
        return weight + 1;      // 1 ... 256
    }

    public Http2PriorityFW tryWrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        boolean wrappable = super.wrap(buffer, offset, maxLimit) != null;

        wrappable &= super.type() == PRIORITY;
        wrappable &= super.streamId() != 0;
        wrappable &= super.length() == 5;
        wrappable &= limit() <= maxLimit;

        return wrappable ? this : null;
    }

    @Override
    public Http2PriorityFW wrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);

        int streamId = super.streamId();
        if (streamId == 0)
        {
            throw new IllegalArgumentException("Invalid PRIORITY frame stream-id=0 (must not be 0)");
        }

        Http2FrameType type = super.type();
        if (type != PRIORITY)
        {
            throw new IllegalArgumentException(String.format("Invalid PRIORITY frame type=%s", type));
        }

        int payloadLength = super.length();
        if (payloadLength != 5)
        {
            throw new IllegalArgumentException(String.format("Invalid PRIORITY frame length=%d (must be 5)", payloadLength));
        }
        checkLimit(limit(), maxLimit);
        return this;
    }

    @Override
    public String toString()
    {
        return String.format("%s frame <length=%s, type=%s, flags=%s, id=%s>",
                type(), length(), type(), flags(), streamId());
    }

    public static final class Builder extends Http2FrameFW.Builder<Builder, Http2PriorityFW>
    {

        public Builder()
        {
            super(new Http2PriorityFW());
        }

        @Override
        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);
            payloadLength(5);
            return this;
        }

        public Builder exclusive()
        {
            buffer().putByte(offset() + PAYLOAD_OFFSET, (byte) 0x80);
            return this;
        }

        public Builder parentStream(int parentStreamId)
        {
            // preserve exclusive flag while writing parent stream id
            int exclusive = buffer().getByte(offset() + PAYLOAD_OFFSET) & 0x80;
            parentStreamId |= exclusive << 24;
            buffer().putInt(offset() + PAYLOAD_OFFSET, parentStreamId, BIG_ENDIAN);
            return this;
        }

        public Builder weight(int weight)
        {
            if (weight < 1 || weight > 256)
            {
                throw new IllegalArgumentException(
                        String.format("Invalid weight=%d for PRIORITY frame (must be between 1 to 256)", weight));
            }
            buffer().putByte(offset() + PAYLOAD_OFFSET + 4, (byte) (weight - 1));
            return this;
        }

    }
}

