/*
 * Copyright 2021-2026 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.fin;
import static io.aklivity.zilla.runtime.engine.util.Flags.hasFin;
import static io.aklivity.zilla.runtime.engine.util.Flags.hasInit;
import static io.aklivity.zilla.runtime.engine.util.Flags.init;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.function.IntPredicate;

import io.aklivity.zilla.runtime.binding.http.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.http.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

public final class HttpSseEventFW extends Flyweight
{
    private static final byte[] DATA_FIELD_HEADER = "data:".getBytes(UTF_8);
    private static final byte[] ID_FIELD_HEADER = "id:".getBytes(UTF_8);
    private static final byte[] TYPE_FIELD_HEADER = "event:".getBytes(UTF_8);
    private static final byte[] RETRY_FIELD_HEADER = "retry:".getBytes(UTF_8);
    private static final byte[] COMMENT_FIELD_HEADER = ":".getBytes(UTF_8);

    private static final byte FIELD_TRAILER = 0x0a;
    private static final int FIELD_TRAILER_LENGTH = 1;

    private static final byte EVENT_TRAILER = 0x0a;
    private static final int EVENT_TRAILER_LENGTH = 1;

    @Override
    public int limit()
    {
        // TODO
        return maxLimit();
    }

    @Override
    public HttpSseEventFW wrap(DirectBufferEx buffer, int offset, int maxLimit)
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

    public static final class Builder extends Flyweight.Builder<HttpSseEventFW>
    {
        private OctetsFW data;
        private int flags;
        private DirectBufferEx id;
        private DirectBufferEx type;
        private DirectBufferEx retry;
        private DirectBufferEx comment;

        public Builder()
        {
            super(new HttpSseEventFW());
        }

        @Override
        public Builder wrap(
            MutableDirectBufferEx buffer,
            int offset,
            int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);

            data = null;
            flags = 0;
            id = null;
            type = null;
            retry = null;
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
            DirectBufferEx id)
        {
            this.id = id;
            return this;
        }

        public Builder type(
            DirectBufferEx type)
        {
            this.type = type;
            return this;
        }

        public Builder retry(
            DirectBufferEx retry)
        {
            this.retry = retry;
            return this;
        }

        public Builder comment(
            DirectBufferEx comment)
        {
            this.comment = comment;
            return this;
        }

        @Override
        public HttpSseEventFW build()
        {
            final DirectBufferEx textAsBytes = data != null ? data.buffer() : null;
            final int offset = data != null ? data.offset() : 0;
            final int limit = data != null ? data.limit() : 0;

            int progress = offset;
            int flags = this.flags;

            if (hasInit(flags))
            {
                buildFields();
            }

            if (data != null)
            {
                if (!hasInit(flags))
                {
                    int newlineAt = indexOfByte(textAsBytes, progress, limit, v -> v == 0x0a);
                    if (newlineAt != -1)
                    {
                        buildData(textAsBytes, progress, newlineAt - progress, fin(flags));
                        flags = init(flags);
                        progress = newlineAt + 1;
                    }
                }

                if (flags == FIN && progress == limit)
                {
                    buildData(textAsBytes, progress, limit - progress, flags);
                    progress = limit;
                }

                if (flags != FIN || progress < limit)
                {
                    for (int newlineAt = indexOfByte(textAsBytes, progress, limit, v -> v == 0x0a);
                        newlineAt != -1;
                        progress = newlineAt + 1,
                            newlineAt = indexOfByte(textAsBytes, progress, limit, v -> v == 0x0a))
                    {
                        buildData(textAsBytes, progress, newlineAt - progress, fin(flags));
                        flags = init(flags);
                    }

                    buildData(textAsBytes, progress, limit - progress, flags);
                }
            }

            if (!hasInit(this.flags) && hasFin(this.flags))
            {
                buildFields();
            }

            if (hasFin(flags))
            {
                checkLimit(limit() + EVENT_TRAILER_LENGTH, maxLimit());

                buffer().putByte(limit(), EVENT_TRAILER);
                limit(limit() + EVENT_TRAILER_LENGTH);
            }

            return super.build();
        }

        private void buildFields()
        {
            buildField(COMMENT_FIELD_HEADER, comment);
            buildField(ID_FIELD_HEADER, id);
            buildField(TYPE_FIELD_HEADER, type);
            buildField(RETRY_FIELD_HEADER, retry);
        }

        private Builder buildData(
            DirectBufferEx textAsBytes,
            int offset,
            int length,
            int flags)
        {
            final MutableDirectBufferEx buffer = buffer();

            if (hasInit(flags))
            {
                checkLimit(limit() + DATA_FIELD_HEADER.length, maxLimit());
                buffer.putBytes(limit(), DATA_FIELD_HEADER);
                limit(limit() + DATA_FIELD_HEADER.length);
            }

            if (length > 0)
            {
                buffer.putBytes(limit(), textAsBytes, offset, length);
                limit(limit() + length);
            }

            if (hasFin(flags))
            {
                checkLimit(limit() + FIELD_TRAILER_LENGTH, maxLimit());
                buffer.putByte(limit(), FIELD_TRAILER);
                limit(limit() + FIELD_TRAILER_LENGTH);
            }

            return this;
        }

        private Builder buildField(
            byte[] header,
            DirectBufferEx value)
        {
            if (value != null)
            {
                checkLimit(limit() +
                           header.length +
                           value.capacity() +
                           FIELD_TRAILER_LENGTH,
                           maxLimit());

                buffer().putBytes(limit(), header);
                limit(limit() + header.length);

                buffer().putBytes(limit(), value, 0, value.capacity());
                limit(limit() + value.capacity());

                buffer().putByte(limit(), FIELD_TRAILER);
                limit(limit() + FIELD_TRAILER_LENGTH);
            }

            return this;
        }
    }

    private static int indexOfByte(
        DirectBufferEx buffer,
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
