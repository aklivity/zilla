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
package io.aklivity.zilla.runtime.cog.http.internal.codec;

import static io.aklivity.zilla.runtime.cog.http.internal.codec.Http2FrameType.PING;
import static io.aklivity.zilla.runtime.cog.http.internal.stream.Http2Flags.ACK;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.cog.http.internal.stream.Http2Flags;

/*

    Flyweight for HTTP2 PING frame

    +-----------------------------------------------+
    |                 Length (24)                   |
    +---------------+---------------+---------------+
    |   Type (8)    |   Flags (8)   |
    +-+-------------+---------------+-------------------------------+
    |R|                 Stream Identifier (31)                      |
    +=+=============+===============================================+
    |                                                               |
    |                      Opaque Data (64)                         |
    |                                                               |
    +---------------------------------------------------------------+

 */
public class Http2PingFW extends Http2FrameFW
{
    private static final int FLAGS_OFFSET = 4;

    @Override
    public Http2FrameType type()
    {
        return PING;
    }

    public boolean ack()
    {
        return Http2Flags.ack(flags());
    }

    public Http2PingFW tryWrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        boolean wrappable = super.wrap(buffer, offset, maxLimit) != null;

        wrappable &= super.streamId() == 0;
        wrappable &= super.type() == PING;
        wrappable &= super.length() == 8;
        wrappable &= limit() <= maxLimit;

        return wrappable ? this : null;
    }

    @Override
    public Http2PingFW wrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);

        int streamId = super.streamId();
        if (streamId != 0)
        {
            throw new IllegalArgumentException(String.format("Invalid PING frame stream-id=%d", streamId));
        }

        Http2FrameType type = super.type();
        if (type != PING)
        {
            throw new IllegalArgumentException(String.format("Invalid PING frame type=%s", type));
        }

        int payloadLength = super.length();
        if (payloadLength != 8)
        {
            throw new IllegalArgumentException(String.format("Invalid PING frame length=%d (must be 8)", payloadLength));
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

    public static final class Builder extends Http2FrameFW.Builder<Http2PingFW.Builder, Http2PingFW>
    {

        public Builder()
        {
            super(new Http2PingFW());
        }

        @Override
        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);
            payloadLength(8);
            return this;
        }

        public Builder ack()
        {
            buffer().putByte(offset() + FLAGS_OFFSET, ACK);
            return this;
        }

        @Override
        public Http2PingFW.Builder payload(DirectBuffer payload, int offset, int length)
        {
            if (length != 8)
            {
                throw new IllegalArgumentException(String.format("Invalid PING frame length = %d (must be 8)", length));
            }

            super.payload(payload, offset, length);
            return this;
        }

    }
}

