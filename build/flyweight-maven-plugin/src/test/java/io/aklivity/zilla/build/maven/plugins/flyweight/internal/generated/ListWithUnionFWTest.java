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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.function.Consumer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.OctetsFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.ListWithUnionFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.UnionOctetsFW;

public class ListWithUnionFWTest
{
    private static final int KIND_SIZE = Byte.BYTES;
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final ListWithUnionFW.Builder listWithUnionRW = new ListWithUnionFW.Builder();
    private final ListWithUnionFW listWithUnionRO = new ListWithUnionFW();
    private final int physicalLengthSize = Byte.BYTES;
    private final int logicalLengthSize = Byte.BYTES;
    private final int bitmaskSize = Long.BYTES;

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        byte physicalLength = 16;
        byte logicalLength = 2;
        long bitmask = 0x03;
        int offsetPhysicalLength = 10;
        buffer.putByte(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putByte(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);

        int offsetUnionOctets = offsetBitMask + bitmaskSize;
        UnionOctetsFW unionOctets = asOctets4CaseOfUnionOctetsFW(b -> b.put("1234".getBytes(UTF_8)));
        buffer.putBytes(offsetUnionOctets, unionOctets.buffer(), 0, unionOctets.sizeof());

        int offsetField1 = offsetUnionOctets + unionOctets.sizeof();
        buffer.putByte(offsetField1, (byte) 3);

        for (int maxLimit = 10; maxLimit <= physicalLength; maxLimit++)
        {
            try
            {
                listWithUnionRO.wrap(buffer,  10, maxLimit);
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
        byte physicalLength = 16;
        byte logicalLength = 2;
        long bitmask = 0x03;
        int offsetPhysicalLength = 10;
        buffer.putByte(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putByte(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);

        int offsetUnionOctets = offsetBitMask + bitmaskSize;
        UnionOctetsFW unionOctets = asOctets4CaseOfUnionOctetsFW(b -> b.put("1234".getBytes(UTF_8)));
        buffer.putBytes(offsetUnionOctets, unionOctets.buffer(), 0, unionOctets.sizeof());

        int offsetField1 = offsetUnionOctets + unionOctets.sizeof();
        buffer.putByte(offsetField1, (byte) 3);

        for (int maxLimit = 10; maxLimit <= physicalLength; maxLimit++)
        {
            assertNull(listWithUnionRO.tryWrap(buffer,  offsetPhysicalLength, maxLimit));
        }
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        byte physicalLength = 16;
        byte logicalLength = 2;
        long bitmask = 0x03;
        int offsetPhysicalLength = 10;
        buffer.putByte(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putByte(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);

        int offsetUnionOctets = offsetBitMask + bitmaskSize;
        UnionOctetsFW unionOctets = asOctets4CaseOfUnionOctetsFW(b -> b.put("1234".getBytes(UTF_8)));
        buffer.putBytes(offsetUnionOctets, unionOctets.buffer(), 0, unionOctets.sizeof());

        int offsetField1 = offsetUnionOctets + unionOctets.sizeof();
        buffer.putByte(offsetField1, (byte) 3);

        assertSame(listWithUnionRO, listWithUnionRO.wrap(buffer, offsetPhysicalLength,
            offsetPhysicalLength + physicalLength));
        assertEquals(physicalLength, listWithUnionRO.limit() - offsetPhysicalLength);
        assertEquals(logicalLength, listWithUnionRO.fieldCount());
        final String octetsValue = listWithUnionRO.unionOctets().octets4().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("1234", octetsValue);
        assertEquals(3, listWithUnionRO.field1());
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        byte physicalLength = 16;
        byte logicalLength = 2;
        long bitmask = 0x03;
        int offsetPhysicalLength = 10;
        buffer.putByte(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putByte(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);

        int offsetUnionOctets = offsetBitMask + bitmaskSize;
        UnionOctetsFW unionOctets = asOctets4CaseOfUnionOctetsFW(b -> b.put("1234".getBytes(UTF_8)));
        buffer.putBytes(offsetUnionOctets, unionOctets.buffer(), 0, unionOctets.sizeof());

        int offsetField1 = offsetUnionOctets + unionOctets.sizeof();
        buffer.putByte(offsetField1, (byte) 3);

        assertSame(listWithUnionRO, listWithUnionRO.tryWrap(buffer, offsetPhysicalLength,
            offsetPhysicalLength + physicalLength));
        assertEquals(physicalLength, listWithUnionRO.limit() - offsetPhysicalLength);
        assertEquals(logicalLength, listWithUnionRO.fieldCount());
        final String octetsValue = listWithUnionRO.unionOctets().octets4().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("1234", octetsValue);
        assertEquals(3, listWithUnionRO.field1());
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetSameFieldMoreThanOnce() throws Exception
    {
        listWithUnionRW.wrap(buffer, 0, buffer.capacity())
            .unionOctets(b -> b.octets4(a -> a.put("1234".getBytes(UTF_8))))
            .unionOctets(b -> b.octets4(a -> a.put("4321".getBytes(UTF_8))))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetFieldWhenSubsequentFieldAlreadySet() throws Exception
    {
        listWithUnionRW.wrap(buffer, 0, buffer.capacity())
            .field1((byte) 120)
            .unionOctets(b -> b.octets4(a -> a.put("1234".getBytes(UTF_8))))
            .build();
    }

    @Test
    public void shouldGetFiedlsWithDefaultWhenNotSet() throws Exception
    {
        int limit = listWithUnionRW.wrap(buffer, 0, buffer.capacity())
            .build()
            .limit();
        listWithUnionRO.wrap(buffer, 0, limit);
        assertEquals(1, listWithUnionRO.field1());
    }

    @Test
    public void shouldSetUnionOctetsFieldAsOctets4UsingUnionOctetsFW() throws Exception
    {
        int limit = listWithUnionRW.wrap(buffer, 0, buffer.capacity())
            .unionOctets(asOctets4CaseOfUnionOctetsFW(b -> b.put("1234".getBytes(UTF_8))))
            .build()
            .limit();
        listWithUnionRO.wrap(buffer, 0, limit);
        assertEquals(1, listWithUnionRO.unionOctets().kind());
        final String octetsValue = listWithUnionRO.unionOctets().octets4().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("1234", octetsValue);
    }

    @Test
    public void shouldSetUnionOctetsFieldAsOctets16UsingUnionOctetsFW() throws Exception
    {
        int limit = listWithUnionRW.wrap(buffer, 0, buffer.capacity())
            .unionOctets(asOctets16CaseOfUnionOctetsFW(b -> b.put("octetsWith16byte".getBytes(UTF_8))))
            .build()
            .limit();
        listWithUnionRO.wrap(buffer, 0, limit);
        assertEquals(2, listWithUnionRO.unionOctets().kind());
        final String octetsValue = listWithUnionRO.unionOctets().octets16().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("octetsWith16byte", octetsValue);
    }

    @Test
    public void shouldSetUnionOctetsFieldAsStringUsingUnionOctetsFW() throws Exception
    {
        int limit = listWithUnionRW.wrap(buffer, 0, buffer.capacity())
            .unionOctets(asString1CaseOfUnionOctetsFW("string1"))
            .build()
            .limit();
        listWithUnionRO.wrap(buffer, 0, limit);
        assertEquals(3, listWithUnionRO.unionOctets().kind());
        assertEquals("string1", listWithUnionRO.unionOctets().string1().asString());
    }

    @Test
    public void shouldSetAllValues() throws Exception
    {
        int limit = listWithUnionRW.wrap(buffer, 0, buffer.capacity())
            .unionOctets(b -> b.octets4(a -> a.put("1234".getBytes(UTF_8))))
            .field1((byte) 100)
            .build()
            .limit();
        listWithUnionRO.wrap(buffer, 0, limit);
        assertEquals(1, listWithUnionRO.unionOctets().kind());
        final String octetsValue = listWithUnionRO.unionOctets().octets4().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("1234", octetsValue);
        assertEquals(100, listWithUnionRO.field1());
    }

    private static UnionOctetsFW asOctets4CaseOfUnionOctetsFW(
        Consumer<OctetsFW.Builder> mutator)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(KIND_SIZE + 4));
        return new UnionOctetsFW.Builder().wrap(buffer, 0, buffer.capacity()).octets4(mutator).build();
    }

    private static UnionOctetsFW asOctets16CaseOfUnionOctetsFW(
        Consumer<OctetsFW.Builder> mutator)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(KIND_SIZE + 16));
        return new UnionOctetsFW.Builder().wrap(buffer, 0, buffer.capacity()).octets16(mutator).build();
    }

    private static UnionOctetsFW asString1CaseOfUnionOctetsFW(
        String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(KIND_SIZE + Byte.SIZE + value.length()));
        return new UnionOctetsFW.Builder().wrap(buffer, 0, buffer.capacity()).string1(value).build();
    }
}
