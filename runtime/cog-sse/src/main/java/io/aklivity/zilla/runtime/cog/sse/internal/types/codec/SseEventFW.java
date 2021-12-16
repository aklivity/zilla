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
package io.aklivity.zilla.runtime.cog.sse.internal.types.codec;

import static java.lang.Long.numberOfLeadingZeros;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.cog.sse.internal.types.Flyweight;
import io.aklivity.zilla.runtime.cog.sse.internal.types.OctetsFW;

public final class SseEventFW extends Flyweight
{
    private static final byte[] DATA_FIELD_HEADER = "data:".getBytes(UTF_8);
    private static final byte[] ID_FIELD_HEADER = "id:".getBytes(UTF_8);
    private static final byte[] TIMESTAMP_FIELD_HEADER = "timestamp:".getBytes(UTF_8);
    private static final byte[] TYPE_FIELD_HEADER = "event:".getBytes(UTF_8);
    private static final byte[] COMMENT_HEADER = ":".getBytes(UTF_8);

    private static final byte[] TIMESTAMP_HEX_PREFIX = "0x".getBytes(UTF_8);

    private static final byte FIELD_TRAILER = 0x0a;
    private static final int FIELD_TRAILER_LENGTH = 1;

    private static final byte COMMENT_TRAILER = 0x0a;
    private static final int COMMENT_TRAILER_LENGTH = 1;

    private static final byte EVENT_TRAILER = 0x0a;
    private static final int EVENT_TRAILER_LENGTH = 1;

    private static final int ASCII_LC_A_MINUS_10 = 'a' - 10;
    private static final int ASCII_0 = '0';
    private static final int HEX_MASK = 0xf;

    static int putHexLong(
        long value,
        MutableDirectBuffer buffer,
        int offset)
    {
        int sizeOfHex = Math.max((Long.SIZE - numberOfLeadingZeros(value) + 3) / 4, 1);
        int bytePos = sizeOfHex;
        do
        {
            final int index = sizeOfHex - bytePos;
            final byte hexValue = (byte) ((value >> (bytePos - 1) * 4) & HEX_MASK);
            bytePos--;
            buffer.putByte(offset + index, (byte) (hexValue < 10 ? hexValue + ASCII_0 : hexValue + ASCII_LC_A_MINUS_10));
        } while (value != 0 && bytePos > 0);
        return sizeOfHex;
    }

    @Override
    public int limit()
    {
        // TODO
        return maxLimit();
    }

    @Override
    public SseEventFW wrap(DirectBuffer buffer, int offset, int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);

        checkLimit(limit(), maxLimit);

        return this;
    }

    @Override
    public String toString()
    {
        return buffer().getStringWithoutLengthUtf8(offset(), sizeof());
    }

    public static final class Builder extends Flyweight.Builder<SseEventFW>
    {
        private int flags;

        public Builder()
        {
            super(new SseEventFW());
        }

        @Override
        public Builder wrap(
            MutableDirectBuffer buffer,
            int offset,
            int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);
            flags = 0;
            return this;
        }

        public Builder flags(
            int flags)
        {
            this.flags = flags;
            return this;
        }

        public Builder dataInit(
            OctetsFW data)
        {
            if (data != null && (flags & 0x02) != 0x00) // INIT
            {
                data(data.buffer(), data.offset(), data.sizeof());
            }

            return this;
        }

        public Builder dataContOnly(
            OctetsFW data)
        {
            if (data != null && flags == 0x00) // CONT only
            {
                data(data.buffer(), data.offset(), data.sizeof());
            }

            return this;
        }

        public Builder dataFinOnly(
            OctetsFW data)
        {
            if (data != null && flags == 0x01) // FIN only
            {
                data(data.buffer(), data.offset(), data.sizeof());
            }

            return this;
        }

        public Builder data(
            DirectBuffer textAsBytes,
            int offset,
            int length)
        {
            final MutableDirectBuffer buffer = buffer();

            if ((flags & 0x02) != 0x00) // INIT
            {
                checkLimit(limit() + DATA_FIELD_HEADER.length, maxLimit());
                buffer.putBytes(limit(), DATA_FIELD_HEADER);
                limit(limit() + DATA_FIELD_HEADER.length);
            }

            buffer.putBytes(limit(), textAsBytes, offset, length);
            limit(limit() + length);

            if ((flags & 0x01) != 0x00) // FIN
            {
                checkLimit(limit() + FIELD_TRAILER_LENGTH, maxLimit());
                buffer.putByte(limit(), FIELD_TRAILER);
                limit(limit() + FIELD_TRAILER_LENGTH);
            }

            return this;
        }

        public Builder id(
            DirectBuffer id)
        {
            if (id != null)
            {
                assert (flags & 0x03) != 0x00; // INIT | FIN

                checkLimit(limit() +
                           ID_FIELD_HEADER.length +
                           id.capacity() +
                           FIELD_TRAILER_LENGTH,
                           maxLimit());

                buffer().putBytes(limit(), ID_FIELD_HEADER);
                limit(limit() + ID_FIELD_HEADER.length);

                buffer().putBytes(limit(), id, 0, id.capacity());
                limit(limit() + id.capacity());

                buffer().putByte(limit(), FIELD_TRAILER);
                limit(limit() + FIELD_TRAILER_LENGTH);
            }

            return this;
        }

        public Builder timestamp(
            long timestamp)
        {
            if (timestamp > 0L)
            {
                assert (flags & 0x03) != 0x00; // INIT | FIN

                final int timestampSize = Math.max((Long.SIZE - numberOfLeadingZeros(timestamp) + 3) / 4, 1);

                checkLimit(limit() +
                        TIMESTAMP_FIELD_HEADER.length +
                        TIMESTAMP_HEX_PREFIX.length +
                        timestampSize +
                        FIELD_TRAILER_LENGTH,
                        maxLimit());

                buffer().putBytes(limit(), TIMESTAMP_FIELD_HEADER);
                limit(limit() + TIMESTAMP_FIELD_HEADER.length);

                buffer().putBytes(limit(), TIMESTAMP_HEX_PREFIX);
                limit(limit() + TIMESTAMP_HEX_PREFIX.length);

                final int bytesAdded = putHexLong(timestamp, buffer(), limit());
                limit(limit() + bytesAdded);

                buffer().putByte(limit(), FIELD_TRAILER);
                limit(limit() + FIELD_TRAILER_LENGTH);
            }

            return this;
        }

        public Builder type(
            DirectBuffer type)
        {
            if (type != null)
            {
                assert (flags & 0x03) != 0x00; // INIT | FIN

                checkLimit(limit() +
                           TYPE_FIELD_HEADER.length +
                           type.capacity() +
                           FIELD_TRAILER_LENGTH,
                           maxLimit());

                buffer().putBytes(limit(), TYPE_FIELD_HEADER);
                limit(limit() + TYPE_FIELD_HEADER.length);

                buffer().putBytes(limit(), type, 0, type.capacity());
                limit(limit() + type.capacity());

                buffer().putByte(limit(), FIELD_TRAILER);
                limit(limit() + FIELD_TRAILER_LENGTH);
            }

            return this;
        }

        public Builder comment(
            DirectBuffer comment)
        {
            if (comment != null)
            {
                assert (flags & 0x03) != 0x00; // INIT | FIN

                checkLimit(limit() +
                            COMMENT_HEADER.length +
                            COMMENT_TRAILER_LENGTH,
                            maxLimit());

                buffer().putBytes(limit(), COMMENT_HEADER);
                limit(limit() + COMMENT_HEADER.length);

                buffer().putBytes(limit(), comment, 0, comment.capacity());
                limit(limit() + comment.capacity());

                buffer().putByte(limit(), COMMENT_TRAILER);
                limit(limit() + COMMENT_TRAILER_LENGTH);
            }
            return this;
        }

        @Override
        public SseEventFW build()
        {
            if ((flags & 0x01) != 0x00) // FIN
            {
                checkLimit(limit() + EVENT_TRAILER_LENGTH, maxLimit());

                buffer().putByte(limit(), EVENT_TRAILER);
                limit(limit() + EVENT_TRAILER_LENGTH);
            }

            return super.build();
        }
    }
}
