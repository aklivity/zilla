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
package io.aklivity.zilla.build.maven.plugins.cog.internal.generated;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.StringFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.VariantEnumKindOfStringFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.VariantOfInt32FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.VariantWithoutOfFW;

public class VariantWithoutOfFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final VariantWithoutOfFW.Builder flyweightRW = new VariantWithoutOfFW.Builder();
    private final VariantWithoutOfFW flyweightRO = new VariantWithoutOfFW();
    public static final EnumWithInt8 KIND_NINE = EnumWithInt8.NINE;
    public static final EnumWithInt8 KIND_FOUR = EnumWithInt8.FOUR;
    public static final EnumWithInt8 KIND_FIVE = EnumWithInt8.FIVE;

    static int setStringValue(
        MutableDirectBuffer buffer,
        final int offset)
    {
        String value = "stringValue";
        buffer.putByte(offset, KIND_NINE.value());
        int offsetValueLength = offset + Byte.BYTES;
        buffer.putByte(offsetValueLength, (byte) value.length());
        int offsetValue = offsetValueLength + Byte.BYTES;
        buffer.putBytes(offsetValue, value.getBytes());
        return Byte.BYTES + Byte.BYTES + value.length();
    }

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int length = setStringValue(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            try
            {
                flyweightRO.wrap(buffer, 10, maxLimit);
                fail("Exception not thrown");
            }
            catch (Exception e)
            {
                if (!(e instanceof IndexOutOfBoundsException))
                {
                    fail("Unexpected exception " + e);
                }
            }
        }
    }

    @Test
    public void shouldNotTryWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int length = setStringValue(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setStringValue(buffer, 10);
        assertSame(flyweightRO, flyweightRO.wrap(buffer, 10, 10 + length));
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setStringValue(buffer, 10);
        assertSame(flyweightRO, flyweightRO.tryWrap(buffer, 10, 10 + length));
    }

    @Test
    public void shouldSetAsVariantOfInt32WithInt8()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsVariantOfInt32(asVariantOfInt32FW(100))
            .build()
            .limit();

        VariantWithoutOfFW variantWithoutOf = flyweightRO.wrap(buffer, 0, limit);

        assertEquals(100, variantWithoutOf.getAsVariantOfInt32().get());
        assertEquals(KIND_FIVE, variantWithoutOf.kind());
    }

    @Test
    public void shouldSetAsVariantOfInt32WithInt32()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsVariantOfInt32(asVariantOfInt32FW(100000))
            .build()
            .limit();

        VariantWithoutOfFW variantWithoutOf = flyweightRO.wrap(buffer, 0, limit);

        assertEquals(100000, variantWithoutOf.getAsVariantOfInt32().get());
        assertEquals(KIND_FOUR, variantWithoutOf.kind());
    }

    @Test
    public void shouldSetAsVariantEnumKindOfString()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsVariantEnumKindOfString(asVariantEnumKindOfStringFW(asStringFW("stringValue")))
            .build()
            .limit();

        VariantWithoutOfFW variantWithoutOf = flyweightRO.wrap(buffer, 0, limit);

        assertNotNull(variantWithoutOf.getAsVariantEnumKindOfString());
        assertEquals("stringValue", variantWithoutOf.getAsVariantEnumKindOfString().get().asString());
        assertEquals(KIND_NINE, variantWithoutOf.kind());
    }

    private static VariantOfInt32FW asVariantOfInt32FW(
        int value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.BYTES + Integer.BYTES));
        return new VariantOfInt32FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value).build();
    }

    private static VariantEnumKindOfStringFW asVariantEnumKindOfStringFW(
        StringFW value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.BYTES + value.sizeof()));
        return new VariantEnumKindOfStringFW.Builder().wrap(buffer, 0, buffer.capacity())
            .set(value).build();
    }

    private static StringFW asStringFW(
        String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.BYTES + value.length()));
        return new String8FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
    }
}
