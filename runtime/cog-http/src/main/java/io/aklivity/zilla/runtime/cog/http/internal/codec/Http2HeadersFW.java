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

import static io.aklivity.zilla.runtime.cog.http.internal.codec.Http2FrameType.HEADERS;
import static io.aklivity.zilla.runtime.cog.http.internal.stream.Http2Flags.END_HEADERS;
import static io.aklivity.zilla.runtime.cog.http.internal.stream.Http2Flags.END_STREAM;
import static io.aklivity.zilla.runtime.cog.http.internal.stream.Http2Flags.PADDED;
import static io.aklivity.zilla.runtime.cog.http.internal.stream.Http2Flags.PRIORITY;
import static java.nio.ByteOrder.BIG_ENDIAN;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.cog.http.internal.hpack.HpackHeaderBlockFW;
import io.aklivity.zilla.runtime.cog.http.internal.hpack.HpackHeaderFieldFW;
import io.aklivity.zilla.runtime.cog.http.internal.stream.Http2Flags;
import io.aklivity.zilla.runtime.cog.http.internal.types.HttpHeaderFW;

/*

    Flyweight for HTTP2 HEADERS frame

    +-----------------------------------------------+
    |                 Length (24)                   |
    +---------------+---------------+---------------+
    |   Type (8)    |   Flags (8)   |
    +-+-------------+---------------+-------------------------------+
    |R|                 Stream Identifier (31)                      |
    +=+=============+===============================================+
    |Pad Length? (8)|
    +-+-------------+-----------------------------------------------+
    |E|                 Stream Dependency? (31)                     |
    +-+-------------+-----------------------------------------------+
    |  Weight? (8)  |
    +-+-------------+-----------------------------------------------+
    |                   Header Block Fragment (*)                 ...
    +---------------------------------------------------------------+
    |                           Padding (*)                       ...
    +---------------------------------------------------------------+

 */
public class Http2HeadersFW extends Http2FrameFW
{

    private static final int FLAGS_OFFSET = 4;
    private static final int PAYLOAD_OFFSET = 9;

    @Override
    public Http2FrameType type()
    {
        return HEADERS;
    }

    public boolean padded()
    {
        return Http2Flags.padded(flags());
    }

    public boolean endHeaders()
    {
        return Http2Flags.endHeaders(flags());
    }

    public boolean priority()
    {
        return Http2Flags.priority(flags());
    }

    public int dataOffset()
    {
        int dataOffset = offset() + PAYLOAD_OFFSET;
        if (padded())
        {
            dataOffset++;        // +1 for Pad Length
        }
        if (priority())
        {
            dataOffset += 5;      // +4 for Stream Dependency, +1 for Weight
        }
        return dataOffset;
    }

    public int parentStream()
    {
        if (priority())
        {
            int dependencyOffset = offset() + PAYLOAD_OFFSET;
            if (padded())
            {
                dependencyOffset++;
            }
            return buffer().getInt(dependencyOffset, BIG_ENDIAN) & 0x7F_FF_FF_FF;
        }
        else
        {
            return 0;
        }
    }

    public int dataLength()
    {
        int dataLength = length();
        if (padded())
        {
            int paddingLength = buffer().getByte(offset() + PAYLOAD_OFFSET) & 0xff;
            dataLength -= paddingLength + 1;    // -1 for Pad Length, -Padding
        }

        if (priority())
        {
            dataLength -= 5;    // -4 for Stream Dependency, -1 for Weight
        }

        return dataLength;
    }

    @Override
    public Http2HeadersFW wrap(DirectBuffer buffer, int offset, int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);
        int streamId = streamId();
        if (streamId == 0)
        {
            throw new IllegalArgumentException(
                    String.format("Invalid HEADERS frame stream-id=%d (must not be 0)", streamId));
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

    public static final class Builder extends Http2FrameFW.Builder<Builder, Http2HeadersFW>
    {
        private final HpackHeaderBlockFW.Builder blockRW = new HpackHeaderBlockFW.Builder();

        public Builder()
        {
            super(new Http2HeadersFW());
        }

        @Override
        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);

            blockRW.wrap(buffer, offset + PAYLOAD_OFFSET, maxLimit());

            return this;
        }

        public Builder padded(boolean padded)
        {
            buffer().putByte(offset() + FLAGS_OFFSET, PADDED);
            return this;
        }

        public Builder endStream(boolean endStream)
        {
            byte flags = buffer().getByte(offset() + FLAGS_OFFSET);
            flags = (byte) (endStream ? (flags | END_STREAM) : (flags & ~END_STREAM));
            buffer().putByte(offset() + FLAGS_OFFSET, flags);
            return this;
        }

        public Builder endStream()
        {
            endStream(true);
            return this;
        }

        public Builder endHeaders()
        {
            byte flags = buffer().getByte(offset() + FLAGS_OFFSET);
            flags |= END_HEADERS;
            buffer().putByte(offset() + FLAGS_OFFSET, flags);
            return this;
        }

        public Builder priority()
        {
            byte flags = buffer().getByte(offset() + FLAGS_OFFSET);
            flags |= PRIORITY;
            buffer().putByte(offset() + FLAGS_OFFSET, flags);
            return this;
        }

        public Builder header(Consumer<HpackHeaderFieldFW.Builder> mutator)
        {
            blockRW.header(mutator);
            int length = blockRW.limit() - offset() - PAYLOAD_OFFSET;
            payloadLength(length);
            return this;
        }

        public Builder set(
                UnboundedListFW<HttpHeaderFW> listRO,
                BiFunction<HttpHeaderFW, HpackHeaderFieldFW.Builder, HpackHeaderFieldFW> mapper)
        {
            blockRW.set(listRO, mapper);
            int length = blockRW.limit() - offset() - PAYLOAD_OFFSET;
            payloadLength(length);
            return this;
        }

        public Builder headers(Consumer<HpackHeaderBlockFW.Builder> mutator)
        {
            mutator.accept(blockRW);
            int length = blockRW.limit() - offset() - PAYLOAD_OFFSET;
            payloadLength(length);
            return this;
        }

    }
}

