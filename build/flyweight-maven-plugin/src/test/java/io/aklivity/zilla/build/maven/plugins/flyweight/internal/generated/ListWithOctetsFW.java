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
package io.aklivity.zilla.build.maven.plugins.flyweight.internal.generated;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.function.Consumer;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.Flyweight;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.OctetsFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.Varint32FW;

public final class ListWithOctetsFW extends Flyweight
{
    private static final int PHYSICAL_LENGTH_SIZE = BitUtil.SIZE_OF_BYTE;

    private static final int LOGICAL_LENGTH_SIZE = BitUtil.SIZE_OF_BYTE;

    private static final int BIT_MASK_SIZE = BitUtil.SIZE_OF_LONG;

    private static final int PHYSICAL_LENGTH_OFFSET = 0;

    private static final int LOGICAL_LENGTH_OFFSET = PHYSICAL_LENGTH_OFFSET + PHYSICAL_LENGTH_SIZE;

    private static final int BIT_MASK_OFFSET = LOGICAL_LENGTH_OFFSET + LOGICAL_LENGTH_SIZE;

    private static final int FIRST_FIELD_OFFSET = BIT_MASK_OFFSET + BIT_MASK_SIZE;

    private static final int FIELD_SIZE_FIXED1 = BitUtil.SIZE_OF_INT;

    private static final int FIELD_SIZE_LENGTHOCTETS2 = BitUtil.SIZE_OF_SHORT;

    private static final int FIELD_SIZE_LENGTHOCTETS4 = BitUtil.SIZE_OF_INT;

    private static final int FIELD_DEFAULT_VALUE_FIXED1 = 11;

    private static final int FIELD_INDEX_FIXED1 = 0;

    private static final int FIELD_INDEX_OCTETS1 = 1;

    private static final int FIELD_INDEX_LENGTHOCTETS2 = 2;

    private static final int FIELD_INDEX_OCTETS2 = 3;

    private static final int FIELD_INDEX_STRING1 = 4;

    private static final int FIELD_INDEX_LENGTHOCTETS3 = 5;

    private static final int FIELD_INDEX_OCTETS3 = 6;

    private static final int FIELD_INDEX_LENGTHOCTETS4 = 7;

    private static final int FIELD_INDEX_OCTETS4 = 8;

    private static final int FIELD_INDEX_EXTENSION = 9;

    private final OctetsFW octets1RO = new OctetsFW();

    private final OctetsFW octets2RO = new OctetsFW();

    private final String8FW string1RO = new String8FW();

    private final Varint32FW lengthOctets3RO = new Varint32FW();

    private OctetsFW octets3RO = new OctetsFW();

    private OctetsFW octets4RO = new OctetsFW();

    private final int[] optionalOffsets = new int[FIELD_INDEX_EXTENSION + 1];

    public int length()
    {
        return buffer().getByte(offset() + LOGICAL_LENGTH_OFFSET);
    }

    private long bitmask()
    {
        return buffer().getLong(offset() + BIT_MASK_OFFSET);
    }

    public long fixed1()
    {
        return (bitmask() & (1 << FIELD_INDEX_FIXED1)) == 0 ? FIELD_DEFAULT_VALUE_FIXED1 :
            buffer().getInt(optionalOffsets[FIELD_INDEX_FIXED1]) & 0xFFFF_FFFFL;
    }

    public OctetsFW octets1()
    {
        return octets1RO;
    }

    public int lengthOctets2()
    {
        assert (bitmask() & (1 << FIELD_INDEX_LENGTHOCTETS2)) != 0 : "Field \"lengthOctets2\" is not set";
        return buffer().getShort(optionalOffsets[FIELD_INDEX_LENGTHOCTETS2]) & 0xFFFF;
    }

    public OctetsFW octets2()
    {
        assert (bitmask() & (1 << FIELD_INDEX_OCTETS2)) != 0 : "Field \"octets2\" is not set";
        return octets2RO;
    }

    public String8FW string1()
    {
        return string1RO;
    }

    public int lengthOctets3()
    {
        return (bitmask() & (1 << FIELD_INDEX_LENGTHOCTETS3)) == 0 ? -1 : lengthOctets3RO.value();
    }

    public OctetsFW octets3()
    {
        return (bitmask() & (1 << FIELD_INDEX_OCTETS3)) == 0 || lengthOctets3() == -1 ? null : octets3RO;
    }

    public int lengthOctets4()
    {
        return (bitmask() & (1 << FIELD_INDEX_LENGTHOCTETS4)) == 0 ? -1 :
            buffer().getInt(optionalOffsets[FIELD_INDEX_LENGTHOCTETS4]);
    }

    public OctetsFW octets4()
    {
        return (bitmask() & (1 << FIELD_INDEX_OCTETS4)) == 0 || lengthOctets4() == -1 ? null : octets4RO;
    }

    @Override
    public ListWithOctetsFW wrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);
        final long bitmask = bitmask();
        int fieldLimit = offset + FIRST_FIELD_OFFSET;
        for (int field = FIELD_INDEX_FIXED1; field < FIELD_INDEX_EXTENSION + 1; field++)
        {
            switch (field)
            {
            case FIELD_INDEX_FIXED1:
                if ((bitmask & (1 << FIELD_INDEX_FIXED1)) != 0)
                {
                    optionalOffsets[FIELD_INDEX_FIXED1] = fieldLimit;
                    fieldLimit += FIELD_SIZE_FIXED1;
                }
                break;
            case FIELD_INDEX_OCTETS1:
                if ((bitmask & (1 << FIELD_INDEX_OCTETS1)) == 0)
                {
                    throw new IllegalArgumentException("Field \"octets1\" is required but not set");
                }
                octets1RO.wrap(buffer, fieldLimit, fieldLimit + 10);
                fieldLimit = octets1RO.limit();
                break;
            case FIELD_INDEX_LENGTHOCTETS2:
                if ((bitmask & (1 << FIELD_INDEX_LENGTHOCTETS2)) != 0)
                {
                    optionalOffsets[FIELD_INDEX_LENGTHOCTETS2] = fieldLimit;
                    fieldLimit += FIELD_SIZE_LENGTHOCTETS2;
                }
                break;
            case FIELD_INDEX_OCTETS2:
                if ((bitmask & (1 << FIELD_INDEX_OCTETS2)) != 0)
                {
                    octets2RO.wrap(buffer, fieldLimit, fieldLimit + lengthOctets2());
                    fieldLimit = octets2RO.limit();
                }
                break;
            case FIELD_INDEX_STRING1:
                if ((bitmask & (1 << FIELD_INDEX_STRING1)) == 0)
                {
                    throw new IllegalArgumentException("Field \"string1\" is required but not set");
                }
                string1RO.wrap(buffer, fieldLimit, maxLimit);
                fieldLimit = string1RO.limit();
                break;
            case FIELD_INDEX_LENGTHOCTETS3:
                if ((bitmask & (1 << FIELD_INDEX_LENGTHOCTETS3)) != 0)
                {
                    lengthOctets3RO.wrap(buffer, fieldLimit, maxLimit);
                    fieldLimit = lengthOctets3RO.limit();
                }
                break;
            case FIELD_INDEX_OCTETS3:
                if ((bitmask & (1 << FIELD_INDEX_OCTETS3)) != 0)
                {
                    octets3RO.wrap(buffer, fieldLimit, lengthOctets3() >= 0 ? fieldLimit + lengthOctets3() : fieldLimit);
                    fieldLimit = octets3RO.limit();
                }
                break;
            case FIELD_INDEX_LENGTHOCTETS4:
                if ((bitmask & (1 << FIELD_INDEX_LENGTHOCTETS4)) != 0)
                {
                    optionalOffsets[FIELD_INDEX_LENGTHOCTETS4] = fieldLimit;
                    fieldLimit += FIELD_SIZE_LENGTHOCTETS4;
                }
                break;
            case FIELD_INDEX_OCTETS4:
                if ((bitmask & (1 << FIELD_INDEX_OCTETS4)) != 0)
                {
                    octets4RO.wrap(buffer, fieldLimit, lengthOctets4() >= 0 ? fieldLimit + lengthOctets4() : fieldLimit);
                    fieldLimit = octets4RO.limit();
                }
                break;
            }
        }
        checkLimit(limit(), maxLimit);
        return this;
    }

    @Override
    public ListWithOctetsFW tryWrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        if (super.tryWrap(buffer, offset, maxLimit) == null)
        {
            return null;
        }
        final long bitmask = bitmask();
        int fieldLimit = offset + FIRST_FIELD_OFFSET;
        for (int field = FIELD_INDEX_FIXED1; field < FIELD_INDEX_EXTENSION + 1; field++)
        {
            switch (field)
            {
            case FIELD_INDEX_FIXED1:
                if ((bitmask & (1 << FIELD_INDEX_FIXED1)) != 0)
                {
                    optionalOffsets[FIELD_INDEX_FIXED1] = fieldLimit;
                    fieldLimit += FIELD_SIZE_FIXED1;
                }
                break;
            case FIELD_INDEX_OCTETS1:
                if ((bitmask & (1 << FIELD_INDEX_OCTETS1)) == 0)
                {
                    return null;
                }
                final OctetsFW octets1 = octets1RO.tryWrap(buffer, fieldLimit, fieldLimit + 10);
                if (octets1 == null)
                {
                    return null;
                }
                fieldLimit = octets1.limit();
                break;
            case FIELD_INDEX_LENGTHOCTETS2:
                if ((bitmask & (1 << FIELD_INDEX_LENGTHOCTETS2)) != 0)
                {
                    optionalOffsets[FIELD_INDEX_LENGTHOCTETS2] = fieldLimit;
                    fieldLimit += FIELD_SIZE_LENGTHOCTETS2;
                }
                break;
            case FIELD_INDEX_OCTETS2:
                if ((bitmask & (1 << FIELD_INDEX_OCTETS2)) != 0)
                {
                    final OctetsFW octets2 = octets2RO.tryWrap(buffer, fieldLimit, fieldLimit + lengthOctets2());
                    if (octets2 == null)
                    {
                        return null;
                    }
                    fieldLimit = octets2.limit();
                }
                break;
            case FIELD_INDEX_STRING1:
                if ((bitmask & (1 << FIELD_INDEX_STRING1)) == 0)
                {
                    return null;
                }
                final String8FW string1 = string1RO.tryWrap(buffer, fieldLimit, maxLimit);
                if (string1 == null)
                {
                    return null;
                }
                fieldLimit = string1.limit();
                break;
            case FIELD_INDEX_LENGTHOCTETS3:
                if ((bitmask & (1 << FIELD_INDEX_LENGTHOCTETS3)) != 0)
                {
                    final Varint32FW lengthOctets3 = lengthOctets3RO.tryWrap(buffer, fieldLimit, maxLimit);
                    if (lengthOctets3 == null)
                    {
                        return null;
                    }
                    fieldLimit = lengthOctets3.limit();
                }
                break;
            case FIELD_INDEX_OCTETS3:
                if ((bitmask & (1 << FIELD_INDEX_OCTETS3)) != 0)
                {
                    final OctetsFW octets3 = octets3RO.tryWrap(buffer, fieldLimit, lengthOctets3() >= 0 ?
                        fieldLimit + lengthOctets3() : fieldLimit);
                    if (octets3 == null)
                    {
                        return null;
                    }
                    fieldLimit = octets3.limit();
                }
                break;
            case FIELD_INDEX_LENGTHOCTETS4:
                if ((bitmask & (1 << FIELD_INDEX_LENGTHOCTETS4)) != 0)
                {
                    optionalOffsets[FIELD_INDEX_LENGTHOCTETS4] = fieldLimit;
                    fieldLimit += FIELD_SIZE_LENGTHOCTETS4;
                }
                break;
            case FIELD_INDEX_OCTETS4:
                if ((bitmask & (1 << FIELD_INDEX_OCTETS4)) != 0)
                {
                    final OctetsFW octets4 = octets4RO.tryWrap(buffer, fieldLimit, lengthOctets4() >= 0 ?
                        fieldLimit + lengthOctets4() : fieldLimit);
                    if (octets4 == null)
                    {
                        return null;
                    }
                    fieldLimit = octets4.limit();
                }
                break;
            }
        }
        if (limit() > maxLimit)
        {
            return null;
        }
        return this;
    }

    @Override
    public int limit()
    {
        return offset() + buffer().getByte(offset() + PHYSICAL_LENGTH_OFFSET);
    }

    @Override
    public String toString()
    {
        final long bitmask = bitmask() | (1 << FIELD_INDEX_FIXED1) | (1 << FIELD_INDEX_OCTETS3) | (1 << FIELD_INDEX_OCTETS4);
        boolean lengthOctets2IsSet = (bitmask & (1 << FIELD_INDEX_LENGTHOCTETS2)) != 0;
        boolean octets2IsSet = (bitmask & (1 << FIELD_INDEX_OCTETS2)) != 0;

        StringBuilder format = new StringBuilder();
        format.append("LIST_WITH_OCTETS [bitmask={0}");
        format.append(", fixed1={1}");
        format.append(", octets1={2}");
        if (lengthOctets2IsSet)
        {
            format.append(", lengthOctets2={3}");
        }
        if (octets2IsSet)
        {
            format.append(", octets2={4}");
        }
        format.append(", string1={5}");
        format.append(", lengthOctets3={6}");
        format.append(", octets3={7}");
        format.append(", lengthOctets4={8}");
        format.append(", octets4={9}");

        format.append("]");
        return MessageFormat.format(format.toString(),
            String.format("0x%04X", bitmask),
            fixed1(),
            octets1(),
            lengthOctets2IsSet ? lengthOctets2() : null,
            octets2IsSet ? octets2() : null,
            string1(),
            lengthOctets3(),
            octets3(),
            lengthOctets4(),
            octets4());
    }

    public static final class Builder extends Flyweight.Builder<ListWithOctetsFW>
    {
        private final OctetsFW.Builder octets1RW = new OctetsFW.Builder();

        private final OctetsFW.Builder octets2RW = new OctetsFW.Builder();

        private final String8FW.Builder string1RW = new String8FW.Builder();

        private final Varint32FW.Builder lengthOctets3RW = new Varint32FW.Builder();

        private final OctetsFW.Builder octets3RW = new OctetsFW.Builder();

        private final OctetsFW.Builder octets4RW = new OctetsFW.Builder();

        private long fieldsMask;

        public Builder()
        {
            super(new ListWithOctetsFW());
        }

        public Builder fixed1(
            long value)
        {
            assert (fieldsMask & ~0x00) == 0 : "Field \"fixed1\" cannot be set out of order";
            if (value < 0)
            {
                throw new IllegalArgumentException(String.format("Value %d too low for field \"fixed1\"", value));
            }
            assert (value & 0xffff_ffff_0000_0000L) == 0L : "Value out of range for field \"fixed1\"";
            int newLimit = limit() + FIELD_SIZE_FIXED1;
            checkLimit(newLimit, maxLimit());
            buffer().putInt(limit(), (int) (value & 0xFFFF_FFFFL));
            fieldsMask |= 1 << FIELD_INDEX_FIXED1;
            limit(newLimit);
            return this;
        }

        private OctetsFW.Builder octets1()
        {
            int newLimit = limit() + 10;
            checkLimit(newLimit, maxLimit());
            return octets1RW.wrap(buffer(), limit(), newLimit);
        }

        public Builder octets1(
            OctetsFW value)
        {
            assert (fieldsMask & ~0x01) == 0 : "Field \"octets1\" cannot be set out of order";
            OctetsFW.Builder octets1RW = octets1();
            octets1RW.set(value);
            int expectedLimit = octets1RW.maxLimit();
            int actualLimit = octets1RW.build().limit();
            if (actualLimit != expectedLimit)
            {
                throw new IllegalStateException(String.format("%d instead of %d bytes have been set for field \"octets1\"",
                    actualLimit - limit(), expectedLimit - limit()));
            }
            fieldsMask |= 1 << FIELD_INDEX_OCTETS1;
            limit(octets1RW.build().limit());
            return this;
        }

        public Builder octets1(
            Consumer<OctetsFW.Builder> mutator)
        {
            assert (fieldsMask & ~0x01) == 0 : "Field \"octets1\" cannot be set out of order";
            OctetsFW.Builder octets1RW = octets1();
            mutator.accept(octets1RW);
            int expectedLimit = octets1RW.maxLimit();
            int actualLimit = octets1RW.build().limit();
            if (actualLimit != expectedLimit)
            {
                throw new IllegalStateException(String.format("%d instead of %d bytes have been set for field \"octets1\"",
                    actualLimit - limit(), expectedLimit - limit()));
            }
            fieldsMask |= 1 << FIELD_INDEX_OCTETS1;
            limit(octets1RW.build().limit());
            return this;
        }

        public Builder octets1(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            assert (fieldsMask & ~0x01) == 0 : "Field \"octets1\" cannot be set out of order";
            OctetsFW.Builder octets1RW = octets1();
            int fieldSize = octets1RW.maxLimit() - limit();
            if (length != fieldSize)
            {
                throw new IllegalArgumentException(String.format("Invalid length %d for field \"octets1\", expected %d", length,
                    fieldSize));
            }
            octets1RW.set(buffer, offset, length);
            fieldsMask |= 1 << FIELD_INDEX_OCTETS1;
            limit(octets1RW.build().limit());
            return this;
        }

        private Builder lengthOctets2(
            int value)
        {
            if (value < 0)
            {
                throw new IllegalArgumentException(String.format("Value %d too low for field \"lengthOctets2\"", value));
            }
            assert (value & 0xFFFF_0000) == 0 : "Value out of range for field \"lengthOctets2\"";
            int newLimit = limit() + FIELD_SIZE_LENGTHOCTETS2;
            checkLimit(newLimit, maxLimit());
            buffer().putShort(limit(), (short)(value & 0xFFFF));
            fieldsMask |= 1 << FIELD_INDEX_LENGTHOCTETS2;
            limit(newLimit);
            return this;
        }

        private OctetsFW.Builder octets2()
        {
            return octets2RW.wrap(buffer(), limit(), maxLimit());
        }

        public Builder octets2(
            OctetsFW value)
        {
            assert (fieldsMask & ~0x07) == 0 : "Field \"octets2\" cannot be set out of order";
            assert (fieldsMask & 0x02) != 0 : "Prior required field \"octets1\" is not set";
            OctetsFW.Builder octets2RW = octets2();
            if (value == null)
            {
                throw new IllegalArgumentException("value cannot be null for field \"octets2\" that does not default to null");
            }
            octets2RW.set(value);
            int newLimit = octets2RW.build().limit();
            int size = newLimit - limit();
            lengthOctets2(size);
            octets2RW = octets2();
            octets2RW.set(value);
            newLimit = limit() + size;
            fieldsMask |= 1 << FIELD_INDEX_OCTETS2;
            limit(newLimit);
            return this;
        }

        public Builder octets2(
            Consumer<OctetsFW.Builder> mutator)
        {
            assert (fieldsMask & ~0x07) == 0 : "Field \"octets2\" cannot be set out of order";
            assert (fieldsMask & 0x02) != 0 : "Prior required field \"octets1\" is not set";
            OctetsFW.Builder octets2RW = octets2();
            mutator.accept(octets2RW);
            int newLimit = octets2RW.build().limit();
            int size = newLimit - limit();
            lengthOctets2(size);
            octets2RW = octets2();
            mutator.accept(octets2RW);
            newLimit = limit() + size;
            fieldsMask |= 1 << FIELD_INDEX_OCTETS2;
            limit(newLimit);
            return this;
        }

        public Builder octets2(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            assert (fieldsMask & ~0x07) == 0 : "Field \"octets2\" cannot be set out of order";
            assert (fieldsMask & 0x02) != 0 : "Prior required field \"octets1\" is not set";
            lengthOctets2(length);
            OctetsFW.Builder octets2RW = octets2();
            octets2RW.set(buffer, offset, length);
            int newLimit = octets2RW.build().limit();
            fieldsMask |= 1 << FIELD_INDEX_OCTETS2;
            limit(newLimit);
            return this;
        }

        private String8FW.Builder string1()
        {
            return string1RW.wrap(buffer(), limit(), maxLimit());
        }

        public Builder string1(
            String value)
        {
            assert (fieldsMask & ~0x0F) == 0 : "Field \"string1\" cannot be set out of order";
            assert (fieldsMask & 0x02) != 0 : "Prior required field \"octets1\" is not set";
            String8FW.Builder string1RW = string1();
            string1RW.set(value, StandardCharsets.UTF_8);
            fieldsMask |= 1 << FIELD_INDEX_STRING1;
            limit(string1RW.build().limit());
            return this;
        }

        public Builder string1(
            String8FW value)
        {
            assert (fieldsMask & ~0x0F) == 0 : "Field \"string1\" cannot be set out of order";
            assert (fieldsMask & 0x02) != 0 : "Prior required field \"octets1\" is not set";
            String8FW.Builder string1RW = string1();
            string1RW.set(value);
            fieldsMask |= 1 << FIELD_INDEX_STRING1;
            limit(string1RW.build().limit());
            return this;
        }

        public Builder string1(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            assert (fieldsMask & ~0x0F) == 0 : "Field \"string1\" cannot be set out of order";
            assert (fieldsMask & 0x02) != 0 : "Prior required field \"octets1\" is not set";
            String8FW.Builder string1RW = string1();
            string1RW.set(buffer, offset, length);
            fieldsMask |= 1 << FIELD_INDEX_STRING1;
            limit(string1RW.build().limit());
            return this;
        }

        private Builder lengthOctets3(
            int value)
        {
            Varint32FW.Builder lengthOctets3RW = this.lengthOctets3RW.wrap(buffer(), limit(), maxLimit());
            lengthOctets3RW.set(value);
            fieldsMask |= 1 << FIELD_INDEX_LENGTHOCTETS3;
            limit(lengthOctets3RW.build().limit());
            return this;
        }

        private OctetsFW.Builder octets3()
        {
            return octets3RW.wrap(buffer(), limit(), maxLimit());
        }

        public Builder octets3(
            OctetsFW value)
        {
            assert (fieldsMask & ~0x3F) == 0 : "Field \"octets3\" cannot be set out of order";
            assert (fieldsMask & 0x10) != 0 : "Prior required field \"string1\" is not set";
            int newLimit;
            OctetsFW.Builder octets3RW = octets3();
            if (value == null)
            {
                lengthOctets3(-1);
                newLimit = limit();
            }
            else
            {
                octets3RW.set(value);
                newLimit = octets3RW.build().limit();
                int size = newLimit - limit();
                lengthOctets3(size);
                octets3RW = octets3();
                octets3RW.set(value);
                newLimit = limit() + size;
            }
            fieldsMask |= 1 << FIELD_INDEX_OCTETS3;
            limit(newLimit);
            return this;
        }

        public Builder octets3(
            Consumer<OctetsFW.Builder> mutator)
        {
            assert (fieldsMask & ~0x3F) == 0 : "Field \"octets3\" cannot be set out of order";
            assert (fieldsMask & 0x10) != 0 : "Prior required field \"string1\" is not set";
            OctetsFW.Builder octets3RW = octets3();
            mutator.accept(octets3RW);
            int newLimit = octets3RW.build().limit();
            int size = newLimit - limit();
            lengthOctets3(size);
            octets3RW = octets3();
            mutator.accept(octets3RW);
            newLimit = limit() + size;
            fieldsMask |= 1 << FIELD_INDEX_OCTETS3;
            limit(newLimit);
            return this;
        }

        public Builder octets3(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            assert (fieldsMask & ~0x3F) == 0 : "Field \"octets3\" cannot be set out of order";
            assert (fieldsMask & 0x10) != 0 : "Prior required field \"string1\" is not set";
            lengthOctets3(length);
            OctetsFW.Builder octets3RW = octets3();
            octets3RW.set(buffer, offset, length);
            int newLimit = octets3RW.build().limit();
            fieldsMask |= 1 << FIELD_INDEX_OCTETS3;
            limit(newLimit);
            return this;
        }

        private Builder lengthOctets4(
            int value)
        {
            int newLimit = limit() + FIELD_SIZE_LENGTHOCTETS4;
            checkLimit(newLimit, maxLimit());
            buffer().putInt(limit(), value);
            fieldsMask |= 1 << FIELD_INDEX_LENGTHOCTETS4;
            limit(newLimit);
            return this;
        }

        private OctetsFW.Builder octets4()
        {
            return octets4RW.wrap(buffer(), limit(), maxLimit());
        }

        public Builder octets4(
            OctetsFW value)
        {
            assert (fieldsMask & ~0xFF) == 0 : "Field \"octets4\" cannot be set out of order";
            assert (fieldsMask & 0x10) != 0 : "Prior required field \"string1\" is not set";
            int newLimit;
            OctetsFW.Builder octets4RW = octets4();
            if (value == null)
            {
                lengthOctets4(-1);
                newLimit = limit();
            }
            else
            {
                octets4RW.set(value);
                newLimit = octets4RW.build().limit();
                int size = newLimit - limit();
                lengthOctets4(size);
                octets4RW = octets4();
                octets4RW.set(value);
                newLimit = limit() + size;
            }
            fieldsMask |= 1 << FIELD_INDEX_OCTETS4;
            limit(newLimit);
            return this;
        }

        public Builder octets4(
            Consumer<OctetsFW.Builder> mutator)
        {
            assert (fieldsMask & ~0xFF) == 0 : "Field \"octets4\" cannot be set out of order";
            assert (fieldsMask & 0x10) != 0 : "Prior required field \"string1\" is not set";
            OctetsFW.Builder octets4RW = octets4();
            mutator.accept(octets4RW);
            int newLimit = octets4RW.build().limit();
            int size = newLimit - limit();
            lengthOctets4(size);
            octets4RW = octets4();
            mutator.accept(octets4RW);
            newLimit = limit() + size;
            fieldsMask |= 1 << FIELD_INDEX_OCTETS4;
            limit(newLimit);
            return this;
        }

        public Builder octets4(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            assert (fieldsMask & ~0xFF) == 0 : "Field \"octets4\" cannot be set out of order";
            assert (fieldsMask & 0x10) != 0 : "Prior required field \"string1\" is not set";
            lengthOctets4(length);
            OctetsFW.Builder octets4RW = octets4();
            octets4RW.set(buffer, offset, length);
            int newLimit = octets4RW.build().limit();
            fieldsMask |= 1 << FIELD_INDEX_OCTETS4;
            limit(newLimit);
            return this;
        }

        @Override
        public Builder wrap(
            MutableDirectBuffer buffer,
            int offset,
            int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);
            fieldsMask = 0;
            int newLimit = limit() + FIRST_FIELD_OFFSET;
            checkLimit(newLimit, maxLimit());
            limit(newLimit);
            return this;
        }

        @Override
        public ListWithOctetsFW build()
        {
            assert (fieldsMask & 0x02) != 0 : "Required field \"octets1\" is not set";
            assert (fieldsMask & 0x10) != 0 : "Required field \"string1\" is not set";
            buffer().putByte(offset() + PHYSICAL_LENGTH_OFFSET, (byte) (limit() - offset()));
            buffer().putByte(offset() + LOGICAL_LENGTH_OFFSET, (byte) (Long.bitCount(fieldsMask)));
            buffer().putLong(offset() + BIT_MASK_OFFSET, fieldsMask);
            return super.build();
        }
    }
}
