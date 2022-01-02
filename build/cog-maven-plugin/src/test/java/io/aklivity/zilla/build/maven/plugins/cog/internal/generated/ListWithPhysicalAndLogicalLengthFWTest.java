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

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.ListWithPhysicalAndLogicalLengthFW;

public class ListWithPhysicalAndLogicalLengthFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final ListWithPhysicalAndLogicalLengthFW.Builder flyweightRW = new ListWithPhysicalAndLogicalLengthFW.Builder();
    private final ListWithPhysicalAndLogicalLengthFW flyweightRO = new ListWithPhysicalAndLogicalLengthFW();
    private final String8FW.Builder stringRW = new String8FW.Builder();
    private final MutableDirectBuffer valueBuffer = new UnsafeBuffer(allocateDirect(100));
    private final int physicalLengthSize = Integer.BYTES;
    private final int logicalLengthSize = Integer.BYTES;
    private final int bitmaskSize = Long.BYTES;

    @Test
    public void shouldNotTryWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int physicalLength = 19;
        int offsetPhysicalLength = 10;
        buffer.putInt(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putInt(offsetLogicalLength, 1);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, 1);
        int offsetField0 = offsetBitMask + bitmaskSize;
        String8FW value = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
            .set("longValue", UTF_8)
            .build();
        buffer.putBytes(offsetField0, value.buffer(), 0, value.sizeof());

        for (int maxLimit = 10; maxLimit <= physicalLength; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer,  offsetPhysicalLength, maxLimit));
        }
    }

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int physicalLength = 23;
        int offsetPhysicalLength = 10;
        buffer.putInt(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putInt(offsetLogicalLength, 1);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, 1);
        int offsetField0 = offsetBitMask + bitmaskSize;
        String8FW value = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
            .set("longValue", UTF_8)
            .build();
        buffer.putBytes(offsetField0, value.buffer(), 0, value.sizeof());

        for (int maxLimit = 10; maxLimit <= physicalLength; maxLimit++)
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
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int physicalLength = 34;
        int offsetPhysicalLength = 10;
        buffer.putInt(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putInt(offsetLogicalLength, 3);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, 7L);
        int offsetField0 = offsetBitMask + bitmaskSize;
        String8FW value = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
            .set("value0", UTF_8)
            .build();
        buffer.putBytes(offsetField0, value.buffer(), 0, value.sizeof());
        int offsetField1 = offsetField0 + value.sizeof();
        buffer.putInt(offsetField1, 100);
        int offsetField2 = offsetField1 + Integer.BYTES;
        value = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
            .set("value2", UTF_8)
            .build();
        buffer.putBytes(offsetField2, value.buffer(), 0, value.sizeof());
        assertSame(flyweightRO, flyweightRO.tryWrap(buffer, offsetPhysicalLength, offsetPhysicalLength +
            physicalLength));
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int physicalLength = 34;
        int offsetPhysicalLength = 10;
        buffer.putInt(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putInt(offsetLogicalLength, 3);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, 7);
        int offsetField0 = offsetBitMask + bitmaskSize;
        String8FW value = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
            .set("value0", UTF_8)
            .build();
        buffer.putBytes(offsetField0, value.buffer(), 0, value.sizeof());
        int offsetField1 = offsetField0 + value.sizeof();
        buffer.putInt(offsetField1, 100);
        int offsetField2 = offsetField1 + Integer.BYTES;
        value = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
            .set("value2", UTF_8)
            .build();
        buffer.putBytes(offsetField2, value.buffer(), 0, value.sizeof());
        assertSame(flyweightRO, flyweightRO.wrap(buffer, offsetPhysicalLength, offsetPhysicalLength + physicalLength));
    }

    @Test
    public void shouldWrapField0AndField1()
    {
        int physicalLength = 27;
        int logicalLength = 2;
        long bitMask = 3;
        buffer.putInt(0, physicalLength);
        int offsetLogicalLength = physicalLengthSize;
        buffer.putInt(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitMask);
        int offsetField0 = offsetBitMask + bitmaskSize;
        String8FW value = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
            .set("value0", UTF_8)
            .build();
        buffer.putBytes(offsetField0, value.buffer(), 0, value.sizeof());
        int offsetField1 = offsetField0 + value.sizeof();
        buffer.putInt(offsetField1, 100);
        assertSame(flyweightRO, flyweightRO.wrap(buffer, 0, physicalLength));
    }

    @Test
    public void shouldWrapField0AndField2()
    {
        int physicalLength = 30;
        int logicalLength = 2;
        long bitMask = 5;
        buffer.putInt(0, physicalLength);
        int offsetLogicalLength = physicalLengthSize;
        buffer.putInt(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitMask);
        int offsetField0 = offsetBitMask + bitmaskSize;
        String8FW value = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
            .set("value0", UTF_8)
            .build();
        buffer.putBytes(offsetField0, value.buffer(), 0, value.sizeof());
        int offsetField2 = offsetField0 + value.sizeof();
        value = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
            .set("value2", UTF_8)
            .build();
        buffer.putBytes(offsetField2, value.buffer(), 0, value.sizeof());
        assertSame(flyweightRO, flyweightRO.wrap(buffer, 0, physicalLength));
    }

    @Test
    public void shouldWrapField0()
    {
        int physicalLength = 23;
        int logicalLength = 1;
        long bitMask = 1;
        buffer.putInt(0, physicalLength);
        int offsetLogicalLength = physicalLengthSize;
        buffer.putInt(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitMask);
        int offsetField0 = offsetBitMask + bitmaskSize;
        String8FW value = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
            .set("value0", UTF_8)
            .build();
        buffer.putBytes(offsetField0, value.buffer(), 0, value.sizeof());
        assertSame(flyweightRO, flyweightRO.wrap(buffer, 0, physicalLength));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotWrapWhenRequiredFieldIsNotSet() throws Exception
    {
        int physicalLength = 27;
        int logicalLength = 2;
        long bitMask = 6;
        buffer.putInt(0, physicalLength);
        int offsetLogicalLength = physicalLengthSize;
        buffer.putInt(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitMask);
        int offsetField1 = offsetBitMask + bitmaskSize;
        buffer.putInt(offsetField1, 100);
        int offsetField2 = offsetField1 + Integer.BYTES;
        String8FW value = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
            .set("value2", UTF_8)
            .build();
        buffer.putBytes(offsetField2, value.buffer(), 0, value.sizeof());
        flyweightRO.wrap(buffer, 0, physicalLength);
    }

    @Test
    public void shouldSetAllValues()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .field0("value0")
            .field1(100L)
            .field2("value2")
            .build()
            .limit();
        flyweightRO.wrap(buffer,  0,  limit);
        assertEquals(34, flyweightRO.sizeof());
        assertEquals(3, flyweightRO.fieldCount());
        assertEquals("value0", flyweightRO.field0().asString());
        assertEquals(100L, flyweightRO.field1());
        assertEquals("value2", flyweightRO.field2().asString());
    }

    @Test(expected = AssertionError.class)
    public void shouldSetRequiredAndOptionalValues() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .field0("value0")
            .field2("value2")
            .build()
            .limit();
        flyweightRO.wrap(buffer,  0,  limit);
        assertEquals(30, flyweightRO.sizeof());
        assertEquals(2, flyweightRO.fieldCount());
        assertEquals("value0", flyweightRO.field0().asString());
        assertEquals("value2", flyweightRO.field2().asString());
        flyweightRO.field1();
    }

    @Test(expected = AssertionError.class)
    public void shouldSetOnlyRequiredValues() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .field0("value0")
            .build()
            .limit();
        flyweightRO.wrap(buffer,  0,  limit);
        assertEquals(23, flyweightRO.sizeof());
        assertEquals(1, flyweightRO.fieldCount());
        assertEquals("value0", flyweightRO.field0().asString());
        flyweightRO.field1();
        flyweightRO.field2();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetField0WithInsufficientSpace()
    {
        int maxLimit = physicalLengthSize + logicalLengthSize + bitmaskSize;
        flyweightRW.wrap(buffer, 10, maxLimit)
            .field0("value0");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetField1WithInsufficientSpace()
    {
        String field0 = "value0";
        int maxLimit = physicalLengthSize + logicalLengthSize + bitmaskSize + field0.getBytes().length;
        flyweightRW.wrap(buffer, 10, maxLimit)
            .field0(field0)
            .field1(100);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetField2WithInsufficientSpace()
    {
        String field0 = "value0";
        int maxLimit = physicalLengthSize + logicalLengthSize + bitmaskSize + field0.getBytes().length + Integer.BYTES;
        flyweightRW.wrap(buffer, 10, maxLimit)
            .field0(field0)
            .field1(100)
            .field2("value2");
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetField1BeforeField0() throws Exception
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .field1(100)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldSetStringFieldsUsingString8FW()
    {
        ListWithPhysicalAndLogicalLengthFW.Builder builder = flyweightRW.wrap(buffer, 0, buffer.capacity());
        String8FW value0 = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
            .set("value0", UTF_8)
            .build();
        builder.field0(value0);
        String8FW value2 = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
            .set("value2", UTF_8)
            .build();
        int limit =  builder.field2(value2)
            .build()
            .limit();
        flyweightRO.wrap(buffer,  0,  limit);
        assertEquals(30, flyweightRO.sizeof());
        assertEquals(2, flyweightRO.fieldCount());
        assertEquals("value0", flyweightRO.field0().asString());
        assertEquals("value2", flyweightRO.field2().asString());
        flyweightRO.field1();
    }

    @Test(expected = AssertionError.class)
    public void shouldSetStringFieldsUsingBuffer()
    {
        valueBuffer.putStringWithoutLengthUtf8(0, "value0");
        valueBuffer.putStringWithoutLengthUtf8(6, "value2");
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .field0(valueBuffer, 0, 6)
            .field2(valueBuffer, 6, 6)
            .build()
            .limit();
        flyweightRO.wrap(buffer,  0,  limit);
        assertEquals(30, flyweightRO.sizeof());
        assertEquals(2, flyweightRO.fieldCount());
        assertEquals("value0", flyweightRO.field0().asString());
        assertEquals("value2", flyweightRO.field2().asString());
        flyweightRO.field1();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetField1WhenRequiredFieldIsNotSet() throws Exception
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .field1(100)
            .field2("value2")
            .build();
    }
}
