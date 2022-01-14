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

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.FlatWithArrayFW;

public class FlatWithArrayFWTest
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
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final FlatWithArrayFW.Builder flatRW = new FlatWithArrayFW.Builder();
    private final FlatWithArrayFW flyweightRO = new FlatWithArrayFW();
    private final String8FW.Builder stringRW = new String8FW.Builder();
    private final MutableDirectBuffer valueBuffer = new UnsafeBuffer(allocateDirect(100));

    static int setAllTestValues(
        MutableDirectBuffer buffer,
        final int offset)
    {
        int arrayLengthSize = Integer.BYTES;
        int fieldCountSize = Integer.BYTES;
        String stringArrayItem = "arrayItem1";
        int stringValueLengthSize = Byte.BYTES;
        int pos = offset;
        buffer.putLong(pos,  10);
        buffer.putByte(pos += 8, (byte) 6);
        buffer.putStringWithoutLengthUtf8(pos += 1,  "value1");
        buffer.putInt(pos += "value1".length(), fieldCountSize + stringValueLengthSize + stringArrayItem.length());
        buffer.putInt(pos += arrayLengthSize, 1);
        buffer.putByte(pos += fieldCountSize, (byte) stringArrayItem.length());
        buffer.putStringWithoutLengthUtf8(pos += 1,  stringArrayItem);
        buffer.putInt(pos += stringArrayItem.length(), 11);

        return pos - offset + Integer.BYTES;
    }

    void assertAllTestValuesRead(
        FlatWithArrayFW flyweight)
    {
        assertEquals(10, flyweight.fixed1());
        assertEquals("value1", flyweight.string1().asString());
        final String[] arrayValue = new String[1];
        flyweight.array1().forEach(s -> arrayValue[0] = s.asString());
        assertEquals("arrayItem1", arrayValue[0]);
        assertEquals(11, flyweight.fixed2());
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
    public void shouldTryWrapAndReadAllValues() throws Exception
    {
        final int offset = 1;
        setAllTestValues(buffer, offset);
        assertNotNull(flyweightRO.tryWrap(buffer, offset, buffer.capacity()));
        assertAllTestValuesRead(flyweightRO);
    }

    @Test
    public void shouldWrapAndReadAllValues() throws Exception
    {
        int size = setAllTestValues(buffer, 10);
        flyweightRO.wrap(buffer, 10,  buffer.capacity());
        assertEquals(10 + size, flyweightRO.limit());
        assertAllTestValuesRead(flyweightRO);
    }

    @Test
    public void shouldDefaultValues() throws Exception
    {
        int limit = flatRW.wrap(buffer, 0, 100)
            .string1("value1")
            .array1(b -> b.item(i -> i.set("arrayItem1", UTF_8)))
            .build()
            .limit();
        flyweightRO.wrap(buffer,  0,  limit);
        assertEquals(111, flyweightRO.fixed1());
        assertEquals(5, flyweightRO.fixed2());
        AtomicInteger arraySize = new AtomicInteger(0);
        flyweightRO.array1().forEach(s -> arraySize.incrementAndGet());
        assertEquals(1, arraySize.get());
        assertEquals(1, flyweightRO.array1().fieldCount());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetFixed1WithInsufficientSpace()
    {
        flatRW.wrap(buffer, 10, 10)
               .fixed1(10);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetString1WithInsufficientSpaceToDefaultPriorField()
    {
        flatRW.wrap(buffer, 10, 11)
                .string1("");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetString1WithInsufficientSpace()
    {
        flatRW.wrap(buffer, 10, 18)
                .fixed1(10)
                .string1("1234");
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetArray1BeforeString1() throws Exception
    {
        flatRW.wrap(buffer, 0, 100)
                .array1(b ->
                {
                });
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetFixed1() throws Exception
    {
        flatRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .fixed1(101)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetString1() throws Exception
    {
        flatRW.wrap(buffer, 0, 100)
            .string1("value1")
            .string1("another value");
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetArray1() throws Exception
    {
        flatRW.wrap(buffer, 0, 100)
            .string1("value1")
            .array1(b -> b.item(i -> i.set("arrayItem1", UTF_8)))
            .array1(b -> b.item(i -> i.set("updatedListItem1", UTF_8)));
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetArray1AfterSettingArray1Items() throws Exception
    {
        flatRW.wrap(buffer, 0, 100)
            .string1("value1")
            .array1Item(b -> b.set("item1", UTF_8))
            .array1(b -> {});
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWhenString1NotSet() throws Exception
    {
        flatRW.wrap(buffer, 0, 100)
            .build();
    }

    @Test
    public void shouldAddArray1Items() throws Exception
    {
        int limit = flatRW.wrap(buffer, 0, buffer.capacity())
                .fixed1(10)
                .string1("value1")
                .array1Item(b -> b.set("item1", UTF_8))
                .array1Item(b -> b.set("item2", UTF_8))
                .array1Item(b -> b.set("item3", UTF_8))
                .build()
                .limit();
        flyweightRO.wrap(buffer,  0,  limit);
        assertEquals(10, flyweightRO.fixed1());
        assertEquals("value1", flyweightRO.string1().asString());
        final List<String> arrayValues = new ArrayList<>();
        flyweightRO.array1().forEach(s -> arrayValues.add(s.asString()));
        assertEquals(Arrays.asList("item1", "item2", "item3"), arrayValues);
    }

    @Test
    public void shouldSetAllValues() throws Exception
    {
        FlatWithArrayFW flyweight = flatRW.wrap(buffer, 0, buffer.capacity())
                .fixed1(10)
                .string1("value1")
                .array1(b -> b.item(i -> i.set("arrayItem1", UTF_8)))
                .fixed2(11)
                .build();
        int size = setAllTestValues(expected, 0);
        assertEquals(0 + size, flyweight.limit());
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldSetStringValuesUsingString8FW() throws Exception
    {
        FlatWithArrayFW.Builder builder = flatRW.wrap(buffer, 0, buffer.capacity());
        builder.fixed1(10);
        String8FW value = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
               .set("value1", UTF_8)
               .build();
        FlatWithArrayFW flatWithList = builder.string1(value)
                .array1(b -> {})
               .build();
        flyweightRO.wrap(buffer,  0,  flatWithList.limit());
        assertEquals(10, flyweightRO.fixed1());
        assertEquals("value1", flyweightRO.string1().asString());
    }

    @Test
    public void shouldSetStringValuesUsingBuffer() throws Exception
    {
        valueBuffer.putStringWithoutLengthUtf8(0, "value1");
        int limit = flatRW.wrap(buffer, 0, buffer.capacity())
            .fixed1(10)
            .string1(valueBuffer, 0, 6)
            .array1(b ->
            { })
            .build()
            .limit();
        flyweightRO.wrap(buffer,  0,  limit);
        assertEquals(10, flyweightRO.fixed1());
        assertEquals("value1", flyweightRO.string1().asString());
    }
}
