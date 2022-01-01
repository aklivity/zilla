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
package io.aklivity.zilla.build.maven.plugins.cog.internal.generated;

import static io.aklivity.zilla.build.maven.plugins.cog.internal.generated.FlyweightTest.putMediumInt;
import static java.nio.ByteBuffer.allocateDirect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.NestedFW;

public class NestedFWTest
{
    private final NestedFW.Builder nestedRW = new NestedFW.Builder();
    private final NestedFW nestedRO = new NestedFW();
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    static void assertAllTestValuesRead(NestedFW flyweight)
    {
        assertEquals(40, flyweight.fixed4());
        assertEquals(10, flyweight.flat().fixed1());
        assertEquals(20, flyweight.flat().fixed2());
        assertEquals("value1", flyweight.flat().string1().asString());
        assertEquals(30, flyweight.flat().fixed3());
        assertEquals("value2", flyweight.flat().string2().asString());
        assertEquals(40, flyweight.flat().fixed4());
        assertEquals("value3", flyweight.flat().string3().asString());
        assertEquals(50, flyweight.flat().fixed5());
        assertEquals("value4", flyweight.flat().string4().asString());
        assertEquals(50, flyweight.fixed5());
        assertEquals(flyweight.flat().string4().limit() + 8, flyweight.limit());
    }

    static int setAllTestValues(MutableDirectBuffer buffer, final int offset)
    {
        int pos = offset;
        buffer.putLong(pos, 40);
        buffer.putLong(pos += 8, 10);
        buffer.putShort(pos += 8, (short) 20);
        buffer.putByte(pos += 2, (byte) 6);
        buffer.putStringWithoutLengthUtf8(pos += 1,  "value1");
        putMediumInt(buffer, pos += 6,  30);
        buffer.putByte(pos += 3, (byte) 6);
        buffer.putStringWithoutLengthUtf8(pos += 1,  "value2");
        buffer.putInt(pos += 6,  40);
        buffer.putByte(pos += 4, (byte) 6);
        buffer.putStringWithoutLengthUtf8(pos += 1,  "value3");
        buffer.putByte(pos += 6,  (byte) 50);
        buffer.putByte(pos += 1, (byte) 7);
        buffer.putStringWithoutLengthUtf8(pos += 1,  "value4");
        buffer.putLong(pos += 6,  50);

        return pos - offset + Long.BYTES;
    }

    @Test
    public void shouldNotTryWrapWhenIncomplete()
    {
        int size = setAllTestValues(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + size; maxLimit++)
        {
            assertNull("at maxLimit " + maxLimit, nestedRO.tryWrap(buffer,  10, maxLimit));
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
                nestedRO.wrap(buffer,  10, maxLimit);
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
    public void shouldTryWrapAndReadAllValues() throws Exception
    {
        final int offset = 1;
        setAllTestValues(buffer, offset);
        assertNotNull(nestedRO.tryWrap(buffer, offset, buffer.capacity()));
        assertAllTestValuesRead(nestedRO);
    }

    @Test
    public void shouldWrapAndReadAllValues() throws Exception
    {
        int size = setAllTestValues(buffer, 10);
        nestedRO.wrap(buffer, 10, buffer.capacity());
        assertEquals(10 + size, nestedRO.limit());
        assertAllTestValuesRead(nestedRO);
    }

    @Test
    public void shouldDefaultValues() throws Exception
    {
        // Set an explicit value first in the same memory to make sure it really
        // gets set to the default value next time round
        int limit1 = nestedRW.wrap(buffer, 0, buffer.capacity())
                .fixed4(40)
                .flat(flat -> flat
                    .fixed1(10)
                    .fixed2(20)
                    .string1("value1")
                    .fixed3(30)
                    .string2("value2")
                    .fixed4(40)
                    .string3("value3")
                    .fixed5((byte) 50)
                    .string4("value4")
                 )
                .fixed5(50)
                .build()
                .limit();
        nestedRO.wrap(buffer,  0,  limit1);
        assertEquals(40, nestedRO.fixed4());
        assertEquals(20, nestedRO.flat().fixed2());

        int limit2 = nestedRW.wrap(buffer, 0, 100)
                .flat(flat -> flat
                    .fixed1(10)
                    .string1("value1")
                    //.fixed3(30)
                    .string2("value2")
                    //.fixed4(40)
                    .string3("value3")
                    //.fixed5((byte) 50)
                    .string4("value4")
                )
                .fixed5(50)
                .build()
                .limit();
        nestedRO.wrap(buffer,  0,  limit2);
        assertEquals(444, nestedRO.fixed4());
        assertEquals(222, nestedRO.flat().fixed2());
        assertEquals(333, nestedRO.flat().fixed3());
        assertEquals(444, nestedRO.flat().fixed4());
        assertEquals(55, nestedRO.flat().fixed5());
        assertEquals(limit1, limit2);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetFixed2BeforeFixed1() throws Exception
    {
        nestedRW.wrap(buffer, 0, 100)
                .flat(flat -> flat
                    .fixed2(10)
                )
                .build()
                .limit();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetFixed5BeforeFlatFixed1() throws Exception
    {
        nestedRW.wrap(buffer, 0, 100)
                .fixed5(50);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetFlat() throws Exception
    {
        nestedRW.wrap(buffer, 0, 100)
            .flat(flat -> flat
                .fixed1(10)
                .string1("value1")
                .string2("value2")
            )
            .flat(flat ->
            { })
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetFixed4() throws Exception
    {
        nestedRW.wrap(buffer, 0, 100)
            .fixed4(40)
            .fixed4(4)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWhenFlatIsNotSet() throws Exception
    {
        nestedRW.wrap(buffer, 0, 100)
            .fixed5(12L)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWhenFixed5IsNotSet() throws Exception
    {
        nestedRW.wrap(buffer, 0, 100)
            .flat(flat -> flat
                .fixed1(10)
                .string1("value1")
                .string2("value2")
            )
            .build();
    }

    @Test
    public void shouldSetAllValues() throws Exception
    {
        int limit = nestedRW.wrap(buffer, 0, buffer.capacity())
                .fixed4(40)
                .flat(flat -> flat
                    .fixed1(10)
                    .fixed2(20)
                    .string1("value1")
                    .fixed3(30)
                    .string2("value2")
                    .fixed4(40)
                    .string3("value3")
                    .fixed5((byte) 50)
                    .string4("value4")
                 )
                .fixed5(50)
                .build()
                .limit();
        nestedRO.wrap(buffer,  0,  limit);
        assertEquals(40, nestedRO.fixed4());
        assertEquals(10, nestedRO.flat().fixed1());
        assertEquals(20, nestedRO.flat().fixed2());
        assertEquals("value1", nestedRO.flat().string1().asString());
        assertEquals(30, nestedRO.flat().fixed3());
        assertEquals("value2", nestedRO.flat().string2().asString());
        assertEquals(40, nestedRO.flat().fixed4());
        assertEquals("value3", nestedRO.flat().string3().asString());
        assertEquals(50, nestedRO.flat().fixed5());
        assertEquals("value4", nestedRO.flat().string4().asString());
        assertEquals(50, nestedRO.fixed5());
        assertEquals(nestedRO.flat().string4().limit() + 8, limit);
    }

}
