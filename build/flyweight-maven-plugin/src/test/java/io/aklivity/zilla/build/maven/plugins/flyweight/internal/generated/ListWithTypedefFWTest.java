/*
 * Copyright 2021-2023 Aklivity Inc.
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithUint32;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.ListWithTypedefFW;

public class ListWithTypedefFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final ListWithTypedefFW.Builder flyweightRW = new ListWithTypedefFW.Builder();
    private final ListWithTypedefFW flyweightRO = new ListWithTypedefFW();
    private final int lengthSize = Byte.BYTES;
    private final int fieldCountSize = Byte.BYTES;

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int length = 20;
        setAllFields(buffer);
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
        int length = 18;
        int offsetLength = 10;
        setAllFields(buffer);
        for (int maxLimit = 10; maxLimit <= length; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer,  offsetLength, maxLimit));
        }
    }

    private void setAllFields(
        MutableDirectBuffer buffer)
    {
        int length = 18;
        int fieldCount = 2;
        int offsetKind = 10;
        buffer.putByte(offsetKind, EnumWithInt8.TWO.value());
        int offsetLength = offsetKind + Byte.BYTES;
        buffer.putByte(offsetLength, (byte) length);
        int offsetFieldCount = offsetLength + lengthSize;
        buffer.putByte(offsetFieldCount, (byte) fieldCount);

        int offsetField1Kind = offsetFieldCount + fieldCountSize;
        buffer.putInt(offsetField1Kind, (int) EnumWithUint32.NI.value());
        int offsetField1 = offsetField1Kind + Integer.BYTES;
        buffer.putInt(offsetField1, 100);

        int offsetField2Kind = offsetField1 + Integer.BYTES;
        buffer.putInt(offsetField2Kind, (int) EnumWithUint32.NI.value());
        int offsetField2 = offsetField2Kind + Integer.BYTES;
        buffer.putInt(offsetField2, 10000);
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = 18;
        int kindSize = Byte.BYTES;
        int fieldCount = 2;
        int offsetLength = 10;
        int maxLimit = offsetLength + kindSize + lengthSize + length;
        setAllFields(buffer);

        final ListWithTypedefFW listWithTypedef = flyweightRO.wrap(buffer, offsetLength, maxLimit);

        assertSame(flyweightRO, listWithTypedef);
        assertEquals(length, listWithTypedef.length());
        assertEquals(fieldCount, listWithTypedef.fieldCount());
        assertEquals(100, listWithTypedef.field1());
        assertEquals(10000, listWithTypedef.field2());
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = 18;
        int kindSize = Byte.BYTES;
        int fieldCount = 2;
        int offsetLength = 10;
        int maxLimit = offsetLength + kindSize + lengthSize + length;
        setAllFields(buffer);

        final ListWithTypedefFW listWithTypedef = flyweightRO.tryWrap(buffer, offsetLength, maxLimit);

        assertSame(flyweightRO, listWithTypedef);
        assertEquals(length, listWithTypedef.length());
        assertEquals(fieldCount, listWithTypedef.fieldCount());
        assertEquals(100, listWithTypedef.field1());
        assertEquals(10000, listWithTypedef.field2());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetFieldWithInsufficientSpace() throws Exception
    {
        flyweightRW.wrap(buffer, 10, 20)
            .field1(100);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenFieldIsSetOutOfOrder() throws Exception
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .field2(10000)
            .field1(100)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenSameFieldIsSetMoreThanOnce() throws Exception
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .field1(100)
            .field1(100)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenRequiredFieldIsNotSet() throws Exception
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .field2(10000)
            .build();
    }

    @Test
    public void shouldSetOnlyRequiredField() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .field1(100)
            .build()
            .limit();

        final ListWithTypedefFW listWithTypedef = flyweightRO.wrap(buffer, 0, limit);

        assertEquals(100, listWithTypedef.field1());
        assertEquals(4000000000L, listWithTypedef.field2());
    }

    @Test
    public void shouldSetAllFields() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .field1(100)
            .field2(10000)
            .build()
            .limit();

        final ListWithTypedefFW listWithTypedef = flyweightRO.wrap(buffer, 0, limit);

        assertEquals(100, listWithTypedef.field1());
        assertEquals(10000, listWithTypedef.field2());
    }
}
