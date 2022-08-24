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
package io.aklivity.zilla.runtime.binding.sse.internal.types.codec;

import static java.lang.Long.numberOfLeadingZeros;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.function.IntPredicate;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.binding.sse.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.sse.internal.types.OctetsFW;

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
        private OctetsFW data;
        private int flags;
        private DirectBuffer id;
        private long timestamp;
        private DirectBuffer type;
        private DirectBuffer comment;

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

            data = null;
            flags = 0;
            id = null;
            timestamp = 0L;
            type = null;
            comment = null;

            return this;
        }

        public Builder flags(
            int flags)
        {
            this.flags = flags;
            return this;
        }

        public Builder data(
            OctetsFW data)
        {
            this.data = data;
            return this;
        }

        public Builder id(
            DirectBuffer id)
        {
            this.id = id;
            return this;
        }

        public Builder timestamp(
            long timestamp)
        {
            this.timestamp = timestamp;
            return this;
        }

        public Builder type(
            DirectBuffer type)
        {
            this.type = type;
            return this;
        }

        public Builder comment(
            DirectBuffer comment)
        {
            this.comment = comment;
            return this;
        }

        @Override
        public SseEventFW build()
        {
            if ((flags & 0x02) == 0x00) // INIT
            {
                buildData(data);
            }

            buildComment(comment);
            buildTimestamp(timestamp);
            buildId(id);
            buildType(type);

            if ((flags & 0x02) != 0x00) // INIT
            {
                buildData(data);
            }


            if ((flags & 0x01) != 0x00) // FIN
            {
                checkLimit(limit() + EVENT_TRAILER_LENGTH, maxLimit());

                buffer().putByte(limit(), EVENT_TRAILER);
                limit(limit() + EVENT_TRAILER_LENGTH);
            }

            return super.build();
        }

        private Builder buildData(
            OctetsFW data)
        {
            if (data != null)
            {
                final DirectBuffer textAsBytes = data.buffer();
                final int offset = data.offset();
                final int length = data.sizeof();

                int progress = offset;
                int limit = offset + length;
                int flags = this.flags;

                for (int newlineAt = indexOfByte(textAsBytes, progress, limit, v -> v == 0x0a);
                     newlineAt != -1;
                     progress = newlineAt + 1,
                        newlineAt = indexOfByte(textAsBytes, progress, limit, v -> v == 0x0a))
                {
                    buildData(textAsBytes, progress, newlineAt - progress, flags | 0x01); // FIN
                    flags |= 0x02; // INIT
                }

                buildData(textAsBytes, progress, limit - progress, flags);
            }

            return this;
        }

        private Builder buildData(
            DirectBuffer textAsBytes,
            int offset,
            int length,
            int flags)
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

        private Builder buildId(
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

        private Builder buildTimestamp(
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

        private Builder buildType(
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

        private Builder buildComment(
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
    }

    private static int indexOfByte(
        DirectBuffer buffer,
        int offset,
        int limit,
        IntPredicate matcher)
    {
        for (int cursor = offset; cursor < limit; cursor++)
        {
            final int ch = buffer.getByte(cursor);

            if (matcher.test(ch))
            {
                return cursor;
            }
        }

        return -1;
    }
}
