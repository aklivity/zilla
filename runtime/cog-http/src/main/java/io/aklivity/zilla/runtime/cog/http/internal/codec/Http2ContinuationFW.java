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

import static io.aklivity.zilla.runtime.cog.http.internal.codec.Http2FrameType.CONTINUATION;
import static io.aklivity.zilla.runtime.cog.http.internal.stream.Http2Flags.END_HEADERS;

import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.cog.http.internal.hpack.HpackHeaderBlockFW;
import io.aklivity.zilla.runtime.cog.http.internal.hpack.HpackHeaderFieldFW;
import io.aklivity.zilla.runtime.cog.http.internal.stream.Http2Flags;

/*

    Flyweight for HTTP2 CONTINUATION frame

    +-----------------------------------------------+
    |                 Length (24)                   |
    +---------------+---------------+---------------+
    |   Type (8)    |   Flags (8)   |
    +-+-------------+---------------+-------------------------------+
    |R|                 Stream Identifier (31)                      |
    +=+=============+===============================================+
    |                   Header Block Fragment (*)                 ...
    +---------------------------------------------------------------+

 */
public class Http2ContinuationFW extends Http2FrameFW
{
    private static final int FLAGS_OFFSET = 4;
    private static final int PAYLOAD_OFFSET = 9;

    @Override
    public Http2FrameType type()
    {
        return CONTINUATION;
    }

    public boolean endHeaders()
    {
        return Http2Flags.endHeaders(flags());
    }

    @Override
    public Http2ContinuationFW wrap(DirectBuffer buffer, int offset, int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);

        int streamId = streamId();
        if (streamId == 0)
        {
            throw new IllegalArgumentException(
                    String.format("Invalid CONTINUATION frame stream-id=%d (must not be 0)", streamId));
        }

        Http2FrameType type = super.type();
        if (type != CONTINUATION)
        {
            throw new IllegalArgumentException(String.format("Invalid CONTINUATION frame type=%s", type));
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

    public static final class Builder extends Http2FrameFW.Builder<Builder, Http2ContinuationFW>
    {
        private final HpackHeaderBlockFW.Builder blockRW = new HpackHeaderBlockFW.Builder();

        public Builder()
        {
            super(new Http2ContinuationFW());
        }

        @Override
        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);
            blockRW.wrap(buffer, offset + PAYLOAD_OFFSET, maxLimit);
            return this;
        }

        public Builder endHeaders()
        {
            buffer().putByte(offset() + FLAGS_OFFSET, END_HEADERS);
            return this;
        }

        public Builder header(Consumer<HpackHeaderFieldFW.Builder> mutator)
        {
            blockRW.header(mutator);
            int length = blockRW.limit() - offset() - PAYLOAD_OFFSET;
            payloadLength(length);
            return this;
        }

    }
}

