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

import static io.aklivity.zilla.runtime.cog.http2.internal.types.Http2FrameType.WINDOW_UPDATE;
import static java.nio.ByteOrder.BIG_ENDIAN;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/*

    Flyweight for HTTP2 RST_STREAM frame

    +-----------------------------------------------+
    |                 Length (24)                   |
    +---------------+---------------+---------------+
    |   Type (8)    |   Flags (8)   |
    +-+-------------+---------------+-------------------------------+
    |R|                 Stream Identifier (31)                      |
    +=+=============================================================+
    |R|              Window Size Increment (31)                     |
    +-+-------------------------------------------------------------+

 */
public class Http2WindowUpdateFW extends Http2FrameFW
{

    private static final int PAYLOAD_OFFSET = 9;

    @Override
    public Http2FrameType type()
    {
        return WINDOW_UPDATE;
    }

    public int size()
    {
        return payload().getInt(0, BIG_ENDIAN) & 0x7F_FF_FF_FF;
    }

    public Http2WindowUpdateFW tryWrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        boolean wrappable = super.wrap(buffer, offset, maxLimit) != null;

        wrappable &= super.type() == WINDOW_UPDATE;
        wrappable &= super.length() == 4;
        wrappable &= limit() <= maxLimit;

        return wrappable ? this : null;
    }

    @Override
    public Http2WindowUpdateFW wrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);

        Http2FrameType type = super.type();
        if (type != WINDOW_UPDATE)
        {
            throw new IllegalArgumentException(String.format("Invalid type=%s for WINDOW_UPDATE frame", type));
        }

        int payloadLength = super.length();
        if (payloadLength != 4)
        {
            throw new IllegalArgumentException(String.format("Invalid WINDOW_UPDATE frame length=%d (must be 4)", payloadLength));
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

    public static final class Builder extends Http2FrameFW.Builder<Builder, Http2WindowUpdateFW>
    {

        public Builder()
        {
            super(new Http2WindowUpdateFW());
        }

        @Override
        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);
            payloadLength(4);
            return this;
        }

        public Builder size(int size)
        {
            if (size < 1)
            {
                throw new IllegalArgumentException(String.format("Invalid WINDOW_UPDATE Size Incremnt = %d (> 0)", size));
            }

            buffer().putInt(offset() + PAYLOAD_OFFSET, size & 0x7F_FF_FF_FF, BIG_ENDIAN);
            return this;
        }

    }
}

