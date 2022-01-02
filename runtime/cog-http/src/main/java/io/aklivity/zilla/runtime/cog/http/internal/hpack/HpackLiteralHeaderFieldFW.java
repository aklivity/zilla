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
package io.aklivity.zilla.runtime.cog.http.internal.hpack;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.cog.http.internal.types.Flyweight;

/*
 * Flyweight for HPACK Literal Header Field
 */
public class HpackLiteralHeaderFieldFW extends Flyweight
{

    private final HpackIntegerFW integer6RO = new HpackIntegerFW(6);
    private final HpackIntegerFW integer4RO = new HpackIntegerFW(4);

    private final HpackStringFW nameRO = new HpackStringFW();
    private final HpackStringFW valueRO = new HpackStringFW();

    @Override
    public int limit()
    {
        return valueRO.limit();
    }

    public boolean error()
    {
        return literalType() == LiteralType.UNKNOWN || (nameType() == NameType.NEW && nameRO.error()) || valueRO.error();
    }

    public enum LiteralType
    {
        INCREMENTAL_INDEXING,   // Literal Header Field with Incremental Indexing
        WITHOUT_INDEXING,       // Literal Header Field without Indexing
        NEVER_INDEXED,          // Literal Header Field Never Indexed
        UNKNOWN
    }

    public enum NameType
    {
        INDEXED,                // Literal Header Field -- Indexed Name
        NEW                     // Literal Header Field -- New Name
    }

    public LiteralType literalType()
    {
        byte b = buffer().getByte(offset());

        if ((b & 0b1100_0000) == 0b0100_0000)
        {
            return LiteralType.INCREMENTAL_INDEXING;
        }
        else if ((b & 0b1111_0000) == 0)
        {
            return LiteralType.WITHOUT_INDEXING;
        }
        else if ((b & 0b1111_0000) == 0b0001_0000)
        {
            return LiteralType.NEVER_INDEXED;
        }

        return LiteralType.UNKNOWN;
    }

    public int nameIndex()
    {
        assert nameType() == NameType.INDEXED;

        switch (literalType())
        {
        case INCREMENTAL_INDEXING:
            return integer6RO.integer();
        case WITHOUT_INDEXING:
        case NEVER_INDEXED:
            return integer4RO.integer();
        default:
            return 0;
        }
    }

    public NameType nameType()
    {
        switch (literalType())
        {
        case INCREMENTAL_INDEXING:
            return integer6RO.integer() == 0 ? NameType.NEW : NameType.INDEXED;
        case WITHOUT_INDEXING:
        case NEVER_INDEXED:
            return integer4RO.integer() == 0 ? NameType.NEW : NameType.INDEXED;
        default:
            return null;
        }
    }

    public HpackStringFW nameLiteral()
    {
        assert nameType() == NameType.NEW;

        return nameRO;
    }

    public HpackStringFW valueLiteral()
    {
        return valueRO;
    }

    @Override
    public HpackLiteralHeaderFieldFW wrap(DirectBuffer buffer, int offset, int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);

        switch (literalType())
        {
        case INCREMENTAL_INDEXING:
            integer6RO.wrap(buffer(), offset, maxLimit());
            literalHeader(integer6RO);
            break;
        case WITHOUT_INDEXING:
        case NEVER_INDEXED:
            integer4RO.wrap(buffer(), offset, maxLimit());
            literalHeader(integer4RO);
            break;
        default:
            break;
        }

        checkLimit(limit(), maxLimit);
        return this;
    }

    private void literalHeader(HpackIntegerFW integerRO)
    {
        int index = integerRO.integer();
        int offset = integerRO.limit();
        if (index == 0)
        {
            nameRO.wrap(buffer(), offset, maxLimit());
            offset = nameRO.limit();
        }
        valueRO.wrap(buffer(), offset, maxLimit());
    }

    public static final class Builder extends Flyweight.Builder<HpackLiteralHeaderFieldFW>
    {
        private final HpackIntegerFW.Builder integer6RW = new HpackIntegerFW.Builder(6);
        private final HpackIntegerFW.Builder integer4RW = new HpackIntegerFW.Builder(4);

        private final HpackStringFW.Builder nameRW = new HpackStringFW.Builder();
        private final HpackStringFW.Builder valueRW = new HpackStringFW.Builder();

        public Builder()
        {
            super(new HpackLiteralHeaderFieldFW());
        }

        @Override
        public HpackLiteralHeaderFieldFW.Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);
            return this;
        }

        public HpackLiteralHeaderFieldFW.Builder type(LiteralType type)
        {
            switch (type)
            {
            case INCREMENTAL_INDEXING:
                buffer().putByte(offset(), (byte) 0b0100_0000);
                break;
            case WITHOUT_INDEXING:
                buffer().putByte(offset(), (byte) 0b0000_0000);
                break;
            case NEVER_INDEXED:
                buffer().putByte(offset(), (byte) 0b0001_0000);
                break;
            default:
                break;
            }

            return this;
        }

        private LiteralType literalType()
        {
            byte b = buffer().getByte(offset());

            if ((b & 0b1100_0000) == 0b0100_0000)
            {
                return LiteralType.INCREMENTAL_INDEXING;
            }
            else if ((b & 0b1111_0000) == 0)
            {
                return LiteralType.WITHOUT_INDEXING;
            }
            else if ((b & 0b1111_0000) == 0b0001_0000)
            {
                return LiteralType.NEVER_INDEXED;
            }

            return LiteralType.UNKNOWN;
        }

        public HpackLiteralHeaderFieldFW.Builder name(int indexedName)
        {
            switch (literalType())
            {
            case INCREMENTAL_INDEXING:
                integer6RW.wrap(buffer(), offset(), maxLimit());
                integer6RW.integer(indexedName);
                valueRW.wrap(buffer(), integer6RW.limit(), maxLimit());
                break;
            case WITHOUT_INDEXING:
            case NEVER_INDEXED:
                integer4RW.wrap(buffer(), offset(), maxLimit());
                integer4RW.integer(indexedName);
                valueRW.wrap(buffer(), integer4RW.limit(), maxLimit());
                break;
            default:
                break;
            }

            return this;
        }

        public HpackLiteralHeaderFieldFW.Builder name(String name)
        {
            nameRW.wrap(buffer(), offset() + 1, maxLimit());
            nameRW.string(name);
            valueRW.wrap(buffer(), nameRW.limit(), maxLimit());
            return this;
        }

        public HpackLiteralHeaderFieldFW.Builder name(DirectBuffer nameBuffer, int offset, int length)
        {
            nameRW.wrap(buffer(), offset() + 1, maxLimit());
            nameRW.string(nameBuffer, offset, length);
            valueRW.wrap(buffer(), nameRW.limit(), maxLimit());
            return this;
        }


        public HpackLiteralHeaderFieldFW.Builder value(String value)
        {
            valueRW.string(value);
            limit(valueRW.limit());
            return this;
        }

        public HpackLiteralHeaderFieldFW.Builder value(DirectBuffer valueBuffer)
        {
            return value(valueBuffer, 0, valueBuffer.capacity());
        }

        public HpackLiteralHeaderFieldFW.Builder value(DirectBuffer valueBuffer, int offset, int length)
        {
            valueRW.string(valueBuffer, offset, length);
            limit(valueRW.limit());
            return this;
        }

    }

}
