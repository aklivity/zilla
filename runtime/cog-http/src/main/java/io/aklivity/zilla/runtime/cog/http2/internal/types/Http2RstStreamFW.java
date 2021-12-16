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

import static io.aklivity.zilla.runtime.cog.http2.internal.types.Http2FrameType.RST_STREAM;
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
    +=+=============+===============================================+
    |                        Error Code (32)                        |
    +---------------------------------------------------------------+

 */
public class Http2RstStreamFW extends Http2FrameFW
{
    private static final int PAYLOAD_OFFSET = 9;

    @Override
    public Http2FrameType type()
    {
        return RST_STREAM;
    }

    public int errorCode()
    {
        return buffer().getInt(offset() + PAYLOAD_OFFSET, BIG_ENDIAN);
    }

    public Http2RstStreamFW tryWrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        boolean wrappable = super.wrap(buffer, offset, maxLimit) != null;

        wrappable &= super.streamId() != 0;
        wrappable &= super.type() == RST_STREAM;
        wrappable &= super.length() == 4;
        wrappable &= limit() <= maxLimit;

        return wrappable ? this : null;
    }

    @Override
    public Http2RstStreamFW wrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);

        int streamId = super.streamId();
        if (streamId == 0)
        {
            throw new IllegalArgumentException(String.format("Invalid RST_STREAM frame stream-id=%d (must not be 0)", streamId));
        }

        Http2FrameType type = super.type();
        if (type != RST_STREAM)
        {
            throw new IllegalArgumentException(String.format("Invalid RST_STREAM frame type=%s", type));
        }

        int payloadLength = super.length();
        if (payloadLength != 4)
        {
            throw new IllegalArgumentException(String.format("Invalid RST_STREAM frame length=%d (must be 4)", payloadLength));
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

    public static final class Builder extends Http2FrameFW.Builder<Builder, Http2RstStreamFW>
    {

        public Builder()
        {
            super(new Http2RstStreamFW());
        }

        @Override
        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);
            payloadLength(4);
            return this;
        }

        public Builder errorCode(Http2ErrorCode errorCode)
        {
            buffer().putInt(offset() + PAYLOAD_OFFSET, errorCode.errorCode, BIG_ENDIAN);
            return this;
        }

    }
}

