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

import static java.nio.ByteOrder.BIG_ENDIAN;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.cog.http.internal.types.Flyweight;

/*
    Flyweight for HTTP2 SETTINGS frame

    +-------------------------------+
    |     Identifier (16)           |
    +-------------------------------+-------------------------------+
    |                        Value (32)                             |
    +---------------------------------------------------------------+

 */
public class Http2SettingFW extends Flyweight
{

    public int id()
    {
        return buffer().getShort(offset(), BIG_ENDIAN);
    }

    public int value()
    {
        return buffer().getInt(offset() + 2, BIG_ENDIAN);
    }

    @Override
    public int limit()
    {
        return offset() + 6;
    }

    @Override
    public Http2SettingFW wrap(DirectBuffer buffer, int offset, int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);

        checkLimit(limit(), maxLimit);

        return this;
    }

    public static final class Builder extends Flyweight.Builder<Http2SettingFW>
    {
        public Builder()
        {
            super(new Http2SettingFW());
        }

        @Override
        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);
            return this;
        }

        public Builder setting(int id, long value)
        {
            buffer().putShort(offset(), (short) id, BIG_ENDIAN);
            buffer().putInt(offset() + 2, (int) value, BIG_ENDIAN);
            super.limit(offset() + 6);
            return this;
        }
    }
}

