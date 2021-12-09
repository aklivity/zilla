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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.stream.IntStream;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.ContiguousSizeFieldsFW;

public class ContiguousSizeFieldsFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[100]);
    private final MutableDirectBuffer expected = new UnsafeBuffer(new byte[100]);

    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            buffer.setMemory(0, buffer.capacity(), (byte) 0xab);
            expected.setMemory(0, expected.capacity(), (byte) 0xab);
        }
    }

    private final ContiguousSizeFieldsFW.Builder builder = new ContiguousSizeFieldsFW.Builder();
    private final ContiguousSizeFieldsFW flyweightRO = new ContiguousSizeFieldsFW();

    static int setAllTestValues(MutableDirectBuffer buffer, int offset)
    {
        int pos = offset;
        buffer.putByte(pos, (byte) 1); // length1
        buffer.putByte(pos += 1, (byte) 1); // length2
        buffer.putByte(pos += 1, (byte) 1); // array1
        buffer.putByte(pos += 1, (byte) 2); // array1
        buffer.putInt(pos += 1, (byte) 6); // length string1
        buffer.putStringWithoutLengthUtf8(pos += 1,  "value1");
        buffer.putByte(pos += 6, (byte) 1); // length3
        buffer.putByte(pos += 1, (byte) 1); // length4
        buffer.putByte(pos += 1, (byte) 3); // array3
        buffer.putByte(pos += 1, (byte) 4); // array4
        return pos - offset + Byte.BYTES;
    }

    static void assertAllTestValuesRead(ContiguousSizeFieldsFW flyweight)
    {
        assertEquals(1, flyweight.array1().next().intValue());
        assertEquals(2, flyweight.array2().next().intValue());
        assertEquals("value1", flyweight.string1().asString());
        assertEquals(3, flyweight.array3().next().intValue());
        assertEquals(4, flyweight.array4().next().intValue());
    }

    @Test
    public void shouldNotTryWrapWhenIncomplete()
    {
        int size = setAllTestValues(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + size; maxLimit++)
        {
            assertNull("at maxLimit " + maxLimit, flyweightRO.tryWrap(buffer,  10, maxLimit));
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
                fail("Exception not thrown for maxLimit " + maxLimit);
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
    public void shouldReadAllValues() throws Exception
    {
        int size = setAllTestValues(buffer, 0);
        int limit = flyweightRO.wrap(buffer,  0,  buffer.capacity()).limit();
        assertEquals(size, limit);
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());

        assertAllTestValuesRead(flyweightRO);
    }

    @Test
    public void shouldSetAllValues() throws Exception
    {
        int limit = builder.wrap(buffer, 0, buffer.capacity())
                .array1(IntStream.of(1).iterator())
                .array2(IntStream.of(2).iterator())
                .string1("value1")
                .array3(IntStream.of(3).iterator())
                .array4(IntStream.of(4).iterator())
                .build()
                .limit();
        int size = setAllTestValues(expected, 0);
        assertEquals(size, limit);
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldDefaultValues() throws Exception
    {
        int limit = builder.wrap(buffer, 0, 100)
                .string1("value1")
                .build()
                .limit();

        int pos = 0;
        expected.putByte(pos, (byte) -1); // length1
        expected.putByte(pos += 1, (byte) -1); // length2
        expected.putByte(pos += 1, (byte) 6);   // length string1
        expected.putStringWithoutLengthUtf8(pos += 1,  "value1");
        expected.putByte(pos += 6, (byte) -1); // length3
        expected.putByte(pos += 1, (byte) -1); // length4

        assertEquals(pos + 1, limit);
        assertArrayEquals(expected.byteArray(), buffer.byteArray());

        flyweightRO.wrap(buffer, 0, 100);
        assertNull(flyweightRO.array1());
        assertNull(flyweightRO.array2());
        assertNull(flyweightRO.array3());
        assertNull(flyweightRO.array4());
    }

    @Test(expected =  AssertionError.class)
    public void shouldFailToBuildWithoutString1()
    {
        builder.wrap(buffer, 10, 10)
               .build();
    }

}
