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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.BoundedOctets16FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.BoundedOctets32FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.BoundedOctets8FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.BoundedOctetsFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.VariantOfOctetsFW;

public class VariantOfOctetsFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(1000000))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final VariantOfOctetsFW.Builder flyweightRW = new VariantOfOctetsFW.Builder();
    private final VariantOfOctetsFW flyweightRO = new VariantOfOctetsFW();
    private final EnumWithInt8 kindOctets32 = EnumWithInt8.ONE;
    private final EnumWithInt8 kindOctets16 = EnumWithInt8.TWO;
    private final EnumWithInt8 kindOctets8 = EnumWithInt8.THREE;
    private final int kindSize = Byte.BYTES;

    private int setOctets8(
        MutableDirectBuffer buffer,
        int offset)
    {
        int length = 6;
        String value = "value1";

        buffer.putByte(offset, kindOctets8.value());
        int offsetLength = offset + kindSize;
        buffer.putByte(offsetLength, (byte) length);
        int offsetValue = offsetLength + Byte.BYTES;
        buffer.putBytes(offsetValue, value.getBytes(UTF_8));

        return kindSize + Byte.BYTES + value.length();
    }

    private void assertAllTestValuesReadCaseOctets32(
        VariantOfOctetsFW flyweight,
        int offset)
    {
        assertEquals(kindOctets32, flyweight.kind());
        assertEquals(offset + kindSize + Integer.BYTES + flyweight.get().length(), flyweight.limit());
        assertEquals(String.format("%70000s", "0"), flyweight.get().get((b, o, l) -> b.getStringWithoutLengthUtf8(o, l - o)));
        assertEquals(70000, flyweight.get().length());
        assertEquals(String.format("%70000s", "0"), flyweight.get((b, o, l) -> b.getStringWithoutLengthUtf8(o, l - o)));
        assertEquals(70000, flyweight.length());
    }

    private void assertAllTestValuesReadCaseOctets16(
        VariantOfOctetsFW flyweight,
        int offset)
    {
        assertEquals(kindOctets16, flyweight.kind());
        assertEquals(offset + kindSize + Short.BYTES + flyweight.get().length(), flyweight.limit());
        assertEquals(String.format("%65535s", "0"),
            flyweight.get().get((b, o, l) -> b.getStringWithoutLengthUtf8(o, l - o)));
        assertEquals(65535, flyweight.get().length());
        assertEquals(String.format("%65535s", "0"),
            flyweight.get((b, o, l) -> b.getStringWithoutLengthUtf8(o, l - o)));
        assertEquals(65535, flyweight.length());
    }

    private void assertAllTestValuesReadCaseOctets8(
        VariantOfOctetsFW flyweight,
        int offset)
    {
        assertEquals(kindOctets8, flyweight.kind());
        assertEquals(offset + kindSize + Byte.BYTES + flyweight.get().length(), flyweight.limit());
        assertEquals("value1", flyweight.get().get((b, o, l) -> b.getStringWithoutLengthUtf8(o, l - o)));
        assertEquals(6, flyweight.get().length());
    }

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int length = setOctets8(buffer, 10);
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
        int length = setOctets8(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setOctets8(buffer, 10);

        final VariantOfOctetsFW variantOfOctets = flyweightRO.wrap(buffer, 10, 10 + length);

        assertSame(flyweightRO, variantOfOctets);
        assertAllTestValuesReadCaseOctets8(variantOfOctets, 10);
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setOctets8(buffer, 10);

        final VariantOfOctetsFW variantOfOctets = flyweightRO.tryWrap(buffer, 10, 10 + length);

        assertNotNull(variantOfOctets);
        assertSame(flyweightRO, variantOfOctets);
        assertAllTestValuesReadCaseOctets8(variantOfOctets, 10);
    }

    @Test
    public void shouldSetWithBoundedOctets32FW() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(asBoundedOctetsFW(String.format("%70000s", "0")))
            .build()
            .limit();

        final VariantOfOctetsFW variantOfOctets = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseOctets32(variantOfOctets, 0);
    }

    @Test
    public void shouldSetWithBoundedOctets16FW() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(asBoundedOctetsFW(String.format("%65535s", "0")))
            .build()
            .limit();

        final VariantOfOctetsFW variantOfOctets = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseOctets16(variantOfOctets, 0);
    }

    @Test
    public void shouldSetWithBoundedOctets8FW() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(asBoundedOctetsFW("value1"))
            .build()
            .limit();

        final VariantOfOctetsFW variantOfOctets = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseOctets8(variantOfOctets, 0);
    }

    @Test
    public void shouldSetWithEmptyOctets() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(asBoundedOctetsFW(""))
            .build()
            .limit();

        final VariantOfOctetsFW variantOfOctets = flyweightRO.tryWrap(buffer, 0, limit);

        assertNotNull(variantOfOctets);
        assertEquals("", variantOfOctets.get().get((b, o, l) -> b.getStringWithoutLengthUtf8(o, l - o)));
    }

    @Test
    public void shouldSetWithBoundedOctets32FWUsingBuffer() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(asBuffer(String.format("%70000s", "0")), 0, 70000)
            .build()
            .limit();

        final VariantOfOctetsFW variantOfOctets = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseOctets32(variantOfOctets, 0);
    }

    @Test
    public void shouldSetWithBoundedOctets16FWUsingBuffer() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(asBuffer(String.format("%65535s", "0")), 0, 65535)
            .build()
            .limit();

        final VariantOfOctetsFW variantOfOctets = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseOctets16(variantOfOctets, 0);
    }

    @Test
    public void shouldSetWithBoundedOctets8FWUsingBuffer() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(asBuffer("value1"), 0, 6)
            .build()
            .limit();

        final VariantOfOctetsFW variantOfOctets = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseOctets8(variantOfOctets, 0);
    }

    @Test
    public void shouldSetWithEmptyOctetsUsingBuffer() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(asBuffer(""), 0, 0)
            .build()
            .limit();

        final VariantOfOctetsFW variantOfOctets = flyweightRO.tryWrap(buffer, 0, limit);

        assertNotNull(variantOfOctets);
        assertEquals("", variantOfOctets.get().get((b, o, l) -> b.getStringWithoutLengthUtf8(o, l - o)));
    }

    @Test
    public void shouldSetWithBoundedOctets32FWUsingByteArray() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(String.format("%70000s", "0").getBytes(UTF_8))
            .build()
            .limit();

        final VariantOfOctetsFW variantOfOctets = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseOctets32(variantOfOctets, 0);
    }

    @Test
    public void shouldSetWithBoundedOctets16FWUsingByteArray() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(String.format("%65535s", "0").getBytes(UTF_8))
            .build()
            .limit();

        final VariantOfOctetsFW variantOfOctets = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseOctets16(variantOfOctets, 0);
    }

    @Test
    public void shouldSetWithBoundedOctets8FWUsingByteArray() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set("value1".getBytes(UTF_8))
            .build()
            .limit();

        final VariantOfOctetsFW variantOfOctets = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseOctets8(variantOfOctets, 0);
    }

    @Test
    public void shouldSetWithEmptyOctetsUsingByteArray() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set("".getBytes(UTF_8))
            .build()
            .limit();

        final VariantOfOctetsFW variantOfOctets = flyweightRO.tryWrap(buffer, 0, limit);

        assertNotNull(variantOfOctets);
        assertEquals("", variantOfOctets.get().get((b, o, l) -> b.getStringWithoutLengthUtf8(o, l - o)));
    }

    private static DirectBuffer asBuffer(
        String value)
    {
        MutableDirectBuffer valueBuffer = new UnsafeBuffer(allocateDirect(value.length()));
        valueBuffer.putStringWithoutLengthUtf8(0, value);
        return valueBuffer;
    }

    private static BoundedOctetsFW asBoundedOctetsFW(
        String value)
    {
        int length = value.length();
        int highestByteIndex = Integer.numberOfTrailingZeros(Integer.highestOneBit(length)) >> 3;
        MutableDirectBuffer buffer;
        switch (highestByteIndex)
        {
        case 0:
        case 4:
            buffer = new UnsafeBuffer(allocateDirect(Byte.SIZE + value.length()));
            return new BoundedOctets8FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value.getBytes(UTF_8)).build();
        case 1:
            buffer = new UnsafeBuffer(allocateDirect(Short.SIZE + value.length()));
            return new BoundedOctets16FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value.getBytes(UTF_8)).build();
        case 2:
        case 3:
            buffer = new UnsafeBuffer(allocateDirect(Integer.SIZE + value.length()));
            return new BoundedOctets32FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value.getBytes(UTF_8)).build();
        default:
            throw new IllegalArgumentException("Illegal value: " + value);
        }
    }
}
