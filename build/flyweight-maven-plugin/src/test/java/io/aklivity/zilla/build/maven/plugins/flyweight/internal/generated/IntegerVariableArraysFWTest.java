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

import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generated.FlyweightTest.putMediumInt;
import static java.nio.ByteBuffer.allocateDirect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PrimitiveIterator;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.IntegerVariableArraysFW;

public class IntegerVariableArraysFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final MutableDirectBuffer expected = new UnsafeBuffer(allocateDirect(100))
    {
        {
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final IntegerVariableArraysFW.Builder flyweightRW = new IntegerVariableArraysFW.Builder();
    private final IntegerVariableArraysFW flyweightRO = new IntegerVariableArraysFW();

    static int setAllTestValues(
        MutableDirectBuffer buffer,
        int offset)
    {
        buffer.putByte(offset + 0, (byte) 11); // fixed1
        buffer.putInt(offset + 1, 3); // lengthUnsigned64
        buffer.putShort(offset + 5, (short) 22); // fixed2
        buffer.putInt(offset + 7, 6); // varint32Array length
        buffer.putInt(offset + 11, 2); // varint32Array field count
        buffer.putByte(offset + 15, (byte) 1); // varint32Array first item
        buffer.putByte(offset + 16, (byte) 2); // varint32Array second item
        buffer.putLong(offset + 17, 10); // unsigned64Array
        buffer.putLong(offset + 25, 112345); // unsigned64Array
        buffer.putLong(offset + 33, 11234567); // unsigned64Array
        buffer.putByte(offset + 41, (byte) 2); // lengthSigned16
        buffer.putShort(offset + 42,  (short) 2); // signed16Array
        buffer.putShort(offset + 44,  (short) -500); // signed16Array
        buffer.putByte(offset + 46, (byte) 2); // lengthSigned24
        putMediumInt(buffer, offset + 47,  (short) 2); // signed24Array
        putMediumInt(buffer, offset + 50,  (short) -500); // signed24Array
        buffer.putInt(offset + 53, 5); // varint64Array length
        buffer.putInt(offset + 57, 1);
        buffer.putByte(offset + 61, (byte) 0x18);
        buffer.putByte(offset + 62, (byte) 1); // lengthInt8
        buffer.putInt(offset + 63, 123); // arrayWithInt8Size
        buffer.putShort(offset + 67, (short) 1); // lengthInt16
        buffer.putInt(offset + 69, 124); // arrayWithInt16Size
        putMediumInt(buffer, offset + 73, 1); // lengthInt24
        buffer.putInt(offset + 76, 125); // arrayWithInt24Size
        return 76 + Integer.BYTES;
    }

    static void assertAllTestValuesRead(IntegerVariableArraysFW flyweight)
    {
        PrimitiveIterator.OfLong unsigned64 = flyweight.unsigned64Array();
        assertEquals(11, flyweight.fixed1());
        List<Integer> varint32 = new ArrayList<Integer>();
        flyweight.varint32Array().forEach(v -> varint32.add(v.value()));
        assertEquals(Arrays.asList(-1, 1), varint32);
        assertEquals(10L, unsigned64.nextLong());
        assertEquals(112345, unsigned64.nextLong());
        assertEquals(11234567, unsigned64.nextLong());
        PrimitiveIterator.OfInt signed16 = flyweight.signed16Array();
        assertEquals(2, signed16.nextInt());
        assertEquals(-500, signed16.nextInt());
        List<Long> varint64 = new ArrayList<Long>();
        flyweight.varint64Array().forEach(v -> varint64.add(v.value()));
        assertEquals(Arrays.asList(12L), varint64);
        PrimitiveIterator.OfInt arrayWithInt8Size = flyweight.arrayWithInt8Size();
        assertEquals(123, arrayWithInt8Size.nextInt());
        PrimitiveIterator.OfInt arrayWithInt16Size = flyweight.arrayWithInt16Size();
        assertEquals(124, arrayWithInt16Size.nextInt());
    }

    @Test
    public void shouldNotTryWrapWhenIncomplete()
    {
        int size = setAllTestValues(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + size; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldNotWrapWhenIncomplete()
    {
        int size = setAllTestValues(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + size; maxLimit++)
        {
            try
            {
                flyweightRO.wrap(buffer,  10, maxLimit);
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
    public void shouldTryWrapWhenLengthSufficient()
    {
        int size = setAllTestValues(buffer, 10);
        assertSame(flyweightRO, flyweightRO.tryWrap(buffer, 10, 10 + size));
    }

    @Test
    public void shouldWrapWhenLengthSufficient()
    {
        int size = setAllTestValues(buffer, 10);
        assertSame(flyweightRO, flyweightRO.wrap(buffer, 10, 10 + size));
    }

    @Test
    public void shouldTryWrapAndReadAllValues() throws Exception
    {
        final int offset = 1;
        setAllTestValues(buffer, offset);
        assertNotNull(flyweightRO.tryWrap(buffer, offset, buffer.capacity()));
        assertAllTestValuesRead(flyweightRO);
    }

    @Test
    public void shouldSetUnsigned64ToMaximumValue()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
               .appendUnsigned64Array(Long.MAX_VALUE)
               .appendSigned16Array((short) 0)
               .appendSigned24Array((short) 0)
               .build()
               .limit();
        expected.putByte(0, (byte) 0); // fixed1
        expected.putInt(1, 1); // lengthUnsigned64
        expected.putShort(5, (short) 0); // fixed2
        expected.putInt(7, 4); // varint64Array length
        expected.putInt(11, 0); // varint64Array field count
        expected.putLong(15, Long.MAX_VALUE); // unsigned64Array
        expected.putByte(23, (byte) 1); // lengthSigned16
        expected.putShort(24,  (short) 0); // signed16Array
        expected.putByte(26, (byte) 1); // lengthSigned24
        putMediumInt(expected, 27,  (short) 0); // signed24Array
        expected.putInt(30, 4); // varint64Array length
        expected.putInt(34, 0); // varint64Array field count
        expected.putByte(38,  (byte) -1);
        expected.putShort(39,  (short) -1);
        putMediumInt(expected, 41, -1);
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());

        flyweightRO.wrap(buffer,  0,  limit);
        assertEquals(Long.MAX_VALUE, flyweightRO.unsigned64Array().nextLong());
    }

    @Test
    public void shouldSetUnsigned64ToMinimumValue()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
               .appendUnsigned64Array(0L)
               .appendSigned16Array((short) 0)
               .appendSigned24Array((short) 0)
               .build()
               .limit();
        expected.putByte(0, (byte) 0); // fixed1
        expected.putInt(1, 1); // lengthUnsigned64
        expected.putShort(5, (short) 0); // fixed2
        expected.putInt(7, 4); // varint32Array length
        expected.putInt(11, 0); // varint32Array field count
        expected.putLong(15, 0L); // unsigned64Array
        expected.putByte(23, (byte) 1); // lengthSigned16
        expected.putShort(24,  (short) 0); // signed16Array
        expected.putByte(26, (byte) 1); // lengthSigned24
        putMediumInt(expected, 27,  (short) 0); // signed24Array
        expected.putInt(30, 4); // varint64Array length
        expected.putInt(34, 0); // varint64Array count
        expected.putByte(38,  (byte) -1);
        expected.putShort(39,  (short) -1);
        putMediumInt(expected, 41,  -1);
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());

        flyweightRO.wrap(buffer,  0,  limit);
        assertEquals(0L, flyweightRO.unsigned64Array().nextLong());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUnsigned64WithInsufficientSpace()
    {
        flyweightRW.wrap(buffer, 10, 10 + 5 + 32);
        for (int i = 0; i < 5; i++)
        {
            flyweightRW.appendUnsigned64Array(i);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToSetUnsigned64WithValueTooLow()
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
               .appendUnsigned64Array(-1L);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetSigned16WithInsufficientSpace()
    {
        flyweightRW.wrap(buffer, 10, 10 + 6 + 4);
        for (int i = 0; i < 5; i++)
        {
            flyweightRW.appendSigned16Array((short) i);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToSetSigned16ToNull()
    {
        flyweightRW.wrap(buffer, 10, 10 + 6 + 4 + 4 + 4);
        flyweightRW.signed16Array(null);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToAppendUnsigned64ArrayWhenFollowingFieldsAreSet()
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .appendSigned16Array((short) 0)
            .appendUnsigned64Array(12L)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToAppendSigned64ArrayWhenFollowingFieldsAreSet()
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .appendArrayWithInt8Size(12)
            .appendSigned16Array((short) 0)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToAppendArraytWithInt8SizeWhenFollowingFieldsAreSet()
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .appendSigned16Array((short) 0)
            .appendArrayWithInt16Size(12)
            .appendArrayWithInt8Size(12)
            .build();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToBuildWithInsufficientSpace()
    {
        flyweightRW.wrap(buffer, 10, 15)
            .appendSigned16Array((short) 0)
            .build();
    }

    @Test
    public void shouldDefaultAllFieldsWithDefaults()
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .appendSigned16Array((short) 0)
            .appendSigned24Array(0)
            .build();

        expected.putByte(0, (byte) 0); // fixed1
        expected.putInt(1, -1); // lengthUnsigned64
        expected.putShort(5, (short) 0); // fixed2
        expected.putInt(7, 4); // varint32Array length
        expected.putInt(11, 0); // varint32Array field count
        expected.putByte(15, (byte) 1); // lengthSigned16
        expected.putShort(16,  (short) 0); // signed16Array
        expected.putByte(18, (byte) 1); // lengthSigned24
        putMediumInt(expected, 19,  (short) 0); // signed24Array
        expected.putInt(22, 4); // varint64Array length
        expected.putInt(26, 0); // varint64Array field count
        expected.putByte(30, (byte) -1);
        expected.putShort(31, (short) -1);
        putMediumInt(expected, 33, -1);

        assertEquals(expected.byteBuffer(), buffer.byteBuffer());

        buffer.setMemory(0, buffer.capacity(), (byte) 0xab);
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .appendSigned16Array((short) 0)
            .appendSigned24Array(0)
            .build();

        assertEquals(expected.byteBuffer(), buffer.byteBuffer());

        flyweightRO.wrap(buffer,  0,  buffer.capacity());
        assertEquals(0, flyweightRO.fixed1());
        assertEquals(0, flyweightRO.fixed2());
        assertNull(flyweightRO.unsigned64Array());
        assertNull(flyweightRO.arrayWithInt8Size());
        assertNull(flyweightRO.arrayWithInt16Size());
        assertNull(flyweightRO.arrayWithInt24Size());
    }

    @Test
    public void shouldDefaultValuesAfterVarintArray()
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .appendSigned16Array((short) 0)
            .appendSigned24Array(0)
            .varint64Array(a -> a.item(b -> b.set(12L)))
            .build();

        expected.putByte(0, (byte) 0); // fixed1
        expected.putInt(1, -1); // lengthUnsigned64
        expected.putShort(5, (short) 0); // fixed2
        expected.putInt(7, 4); // varint32Array length
        expected.putInt(11, 0); // varint32Array field count
        expected.putByte(15, (byte) 1); // lengthSigned16
        expected.putShort(16,  (short) 0); // signed16Array
        expected.putByte(18, (byte) 1); // lengthSigned24
        putMediumInt(expected, 19,  0); // signed24Array
        expected.putInt(22, 5); // varint64Array length
        expected.putInt(26, 1); // varint64Array field count
        expected.putByte(30, (byte) 0x18);
        expected.putByte(31, (byte) -1);
        expected.putShort(32, (short) -1);
        putMediumInt(expected, 34,  -1);

        assertEquals(expected.byteBuffer(), buffer.byteBuffer());

        flyweightRO.wrap(buffer,  0,  buffer.capacity());
        assertEquals(0, flyweightRO.fixed1());
        assertEquals(0, flyweightRO.fixed2());
        assertNull(flyweightRO.arrayWithInt8Size());
        assertNull(flyweightRO.arrayWithInt16Size());
        assertNull(flyweightRO.arrayWithInt24Size());
        List<Long> varint64 = new ArrayList<Long>();
        flyweightRO.varint64Array().forEach(v -> varint64.add(v.value()));
        assertEquals(Arrays.asList(12L), varint64);

    }

    @Test
    public void shouldSetAllValues() throws Exception
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .fixed1(11)
            .fixed2((short) 22)
            .varint32Array(a -> a.item(b -> b.set(-1))
                                 .item(b -> b.set(1)))
            .appendUnsigned64Array(10)
            .appendUnsigned64Array(112345)
            .appendUnsigned64Array(11234567)
            .appendSigned16Array((short) 2)
            .appendSigned16Array((short) -500)
            .appendSigned24Array(2)
            .appendSigned24Array(-500)
            .varint64Array(a -> a.item(b -> b.set(12L)))
            .appendArrayWithInt8Size(123)
            .appendArrayWithInt16Size(124)
            .appendArrayWithInt24Size(125)
            .build();
        setAllTestValues(expected, 0);

        assertEquals(expected.byteBuffer(), buffer.byteBuffer());

        flyweightRO.wrap(buffer,  0,  buffer.capacity());
        assertAllTestValuesRead(flyweightRO);
    }

    @Test
    public void shouldReadAllValues() throws Exception
    {
        setAllTestValues(buffer, 20);
        flyweightRO.wrap(buffer, 20,  buffer.capacity());
        assertAllTestValuesRead(flyweightRO);
    }

    @Test
    public void shouldConvertToString() throws Exception
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .fixed1(11)
            .appendUnsigned64Array(10)
            .appendUnsigned64Array(1112345)
            .appendUnsigned64Array(11234567)
            .appendSigned16Array((short) 2)
            .appendSigned16Array((short) -500)
            .appendSigned24Array(-1)
            .build();
        flyweightRO.wrap(buffer, 0, 100);
        assertTrue(flyweightRO.toString().contains("unsigned64Array=[10, 1112345, 11234567]"));
        assertTrue(flyweightRO.toString().contains("signed16Array=[2, -500]"));
    }
}
