/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal.codec;

import static io.aklivity.zilla.runtime.binding.http.internal.codec.Http2FrameType.GO_AWAY;
import static java.nio.ByteOrder.BIG_ENDIAN;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/*

    Flyweight for HTTP2 GOAWAY frame

    +-----------------------------------------------+
    |                 Length (24)                   |
    +---------------+---------------+---------------+
    |   Type (8)    |   Flags (8)   |
    +-+-------------+---------------+-------------------------------+
    |R|                 Stream Identifier (31)                      |
    +=+=============+===============================================+
    |R|                  Last-Stream-ID (31)                        |
    +-+-------------------------------------------------------------+
    |                      Error Code (32)                          |
    +---------------------------------------------------------------+
    |                  Additional Debug Data (*)                    |
    +---------------------------------------------------------------+

 */
public class Http2GoawayFW extends Http2FrameFW
{
    private static final int LAST_STREAM_ID_OFFSET = 9;
    private static final int ERROR_CODE_OFFSET = 13;

    @Override
    public Http2FrameType type()
    {
        return GO_AWAY;
    }

    public int lastStreamId()
    {
        return buffer().getInt(offset() + LAST_STREAM_ID_OFFSET, BIG_ENDIAN) & 0x7F_FF_FF_FF;
    }

    public int errorCode()
    {
        return buffer().getInt(offset() + ERROR_CODE_OFFSET, BIG_ENDIAN);
    }

    @Override
    public Http2GoawayFW tryWrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        super.tryWrap(buffer, offset, maxLimit);
        return this;
    }

    @Override
    public Http2GoawayFW wrap(DirectBuffer buffer, int offset, int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);

        int streamId = super.streamId();
        if (streamId != 0)
        {
            throw new IllegalArgumentException(String.format("Invalid stream-id=%d for GOAWAY frame", streamId));
        }

        Http2FrameType type = super.type();
        if (type != GO_AWAY)
        {
            throw new IllegalArgumentException(String.format("Invalid type=%s for GOAWAY frame", type));
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

    public static final class Builder extends Http2FrameFW.Builder<Builder, Http2GoawayFW>
    {

        public Builder()
        {
            super(new Http2GoawayFW());
        }

        @Override
        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);

            // not including "Additional Debug Data"
            payloadLength(8);

            return this;
        }

        public Builder lastStreamId(int lastStreamId)
        {
            buffer().putInt(offset() + LAST_STREAM_ID_OFFSET, lastStreamId, BIG_ENDIAN);
            return this;
        }

        public Builder errorCode(Http2ErrorCode error)
        {
            buffer().putInt(offset() + ERROR_CODE_OFFSET, error.errorCode, BIG_ENDIAN);
            return this;
        }

    }
}

