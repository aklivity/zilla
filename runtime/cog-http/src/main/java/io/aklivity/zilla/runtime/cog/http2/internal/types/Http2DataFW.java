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

import static io.aklivity.zilla.runtime.cog.http2.internal.stream.Http2Flags.END_STREAM;
import static io.aklivity.zilla.runtime.cog.http2.internal.types.Http2FrameType.DATA;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.cog.http2.internal.stream.Http2Flags;

/*
    Flyweight for HTTP2 DATA frame

    +-----------------------------------------------+
    |                 Length (24)                   |
    +---------------+---------------+---------------+
    |   Type (8)    |   Flags (8)   |
    +-+-------------+---------------+-------------------------------+
    |R|                 Stream Identifier (31)                      |
    +=+=============+===============================================+
    |Pad Length? (8)|
    +---------------+-----------------------------------------------+
    |                            Data (*)                         ...
    +---------------------------------------------------------------+
    |                           Padding (*)                       ...
    +---------------------------------------------------------------+

 */
public class Http2DataFW extends Http2FrameFW
{
    private static final int FLAGS_OFFSET = 4;
    private static final int PAYLOAD_OFFSET = 9;

    @Override
    public Http2FrameType type()
    {
        return DATA;
    }

    private boolean padding()
    {
        return Http2Flags.padded(flags());
    }

    public int dataOffset()
    {
        int payloadOffset = offset() + PAYLOAD_OFFSET;
        return  padding() ? payloadOffset + 1 : payloadOffset;
    }

    public int dataLength()
    {
        if (padding())
        {
            int paddingLength = buffer().getByte(offset() + PAYLOAD_OFFSET) & 0xff;
            return length() - paddingLength - 1;
        }
        else
        {
            return length();
        }
    }


    @Override
    public Http2DataFW wrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);
        int streamId = streamId();
        if (streamId == 0)
        {
            throw new IllegalArgumentException(
                    String.format("Invalid DATA frame stream-id=%d (must not be 0)", streamId));
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

    public static final class Builder extends Http2FrameFW.Builder<Builder, Http2DataFW>
    {
        public Builder()
        {
            super(new Http2DataFW());
        }

        @Override
        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);
            return this;
        }

        public Builder endStream()
        {
            buffer().putByte(offset() + FLAGS_OFFSET, END_STREAM);
            return this;
        }
    }
}

