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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.Array32FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithUint8;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithUint8FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.ListWithPhysicalAndLogicalLengthFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.StructWithNonPrimitiveFieldsFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.UnionOctetsFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.VariantEnumKindOfInt8FW;

public class StructWithNonPrimitiveFieldsFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final StructWithNonPrimitiveFieldsFW.Builder structWithNonPrimitiveFieldsRW =
        new StructWithNonPrimitiveFieldsFW.Builder();
    private final StructWithNonPrimitiveFieldsFW structWithNonPrimitiveFieldsRO = new StructWithNonPrimitiveFieldsFW();
    private final EnumWithUint8FW.Builder enumWithUint8RW = new EnumWithUint8FW.Builder();
    private final UnionOctetsFW.Builder unionOctetsRW = new UnionOctetsFW.Builder();
    private final Array32FW.Builder<UnionOctetsFW.Builder, UnionOctetsFW> arrayRW =
        new Array32FW.Builder<>(new UnionOctetsFW.Builder(), new UnionOctetsFW());
    private final ListWithPhysicalAndLogicalLengthFW.Builder listWithPhysicalAndLogicalLengthRW =
        new ListWithPhysicalAndLogicalLengthFW.Builder();
    private final VariantEnumKindOfInt8FW.Builder variantEnumKindOfInt8RW = new VariantEnumKindOfInt8FW.Builder();

    static int setStringValue(
        MutableDirectBuffer buffer,
        final int offset)
    {
        buffer.putByte(offset, (byte) "stringValue".length());
        int offsetStringValueLength = offset + Byte.BYTES;
        buffer.putBytes(offsetStringValueLength, "stringValue".getBytes(StandardCharsets.UTF_8));
        int offsetStringValue = offsetStringValueLength + "stringValue".length();
        buffer.putByte(offsetStringValue, (byte) EnumWithUint8.ICHI.value());
        int offsetEnumField = offsetStringValue + Byte.BYTES;
        buffer.putByte(offsetEnumField, (byte) 3);
        int offsetUnionFieldKind = offsetEnumField + Byte.BYTES;
        buffer.putByte(offsetUnionFieldKind, (byte) "unionValue".length());
        int offsetUnionFieldLength = offsetUnionFieldKind + Byte.BYTES;
        buffer.putBytes(offsetUnionFieldLength, "unionValue".getBytes(StandardCharsets.UTF_8));
        int offsetArrayFieldsLength = offsetUnionFieldLength + "unionValue".length();
        buffer.putInt(offsetArrayFieldsLength, 14);
        int offsetArrayFieldsFieldCount = offsetArrayFieldsLength + Integer.BYTES;
        buffer.putInt(offsetArrayFieldsFieldCount, 1);
        int offsetArrayFieldValueLength = offsetArrayFieldsFieldCount + Integer.BYTES;
        buffer.putByte(offsetArrayFieldValueLength, (byte) "arrayItem".length());
        int offsetArrayFieldValue = offsetArrayFieldValueLength + Byte.BYTES;
        buffer.putBytes(offsetArrayFieldValue, "arrayItem".getBytes(StandardCharsets.UTF_8));
        int offsetListFieldLength = offsetArrayFieldValue + "arrayItem".length();
        buffer.putInt(offsetListFieldLength, Integer.BYTES + Integer.BYTES + Long.BYTES + Byte.BYTES +
            "listValue".length());
        int offsetListFieldFieldCount = offsetListFieldLength + Integer.BYTES;
        buffer.putInt(offsetListFieldFieldCount, 1);
        int offsetListFieldBitmask = offsetListFieldFieldCount + Integer.BYTES;
        buffer.putLong(offsetListFieldBitmask, 1L);
        int offsetListFieldValueLength = offsetListFieldBitmask + Long.BYTES;
        buffer.putByte(offsetListFieldValueLength, (byte) "listValue".length());
        int offsetListFieldValue = offsetListFieldValueLength + Byte.BYTES;
        buffer.putBytes(offsetListFieldValue, "listValue".getBytes(StandardCharsets.UTF_8));
        int offsetVariantFieldKind = offsetListFieldValue + "listValue".length();
        buffer.putByte(offsetVariantFieldKind, EnumWithInt8.ONE.value());
        int offsetVariantFieldValue = offsetVariantFieldKind + Byte.BYTES;
        buffer.putByte(offsetVariantFieldValue, (byte) 10);
        return Byte.BYTES + offsetVariantFieldValue - offset;
    }


    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int length = setStringValue(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            try
            {
                structWithNonPrimitiveFieldsRO.wrap(buffer, 10, maxLimit);
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
            assertNull(structWithNonPrimitiveFieldsRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setStringValue(buffer, 10);
        assertSame(structWithNonPrimitiveFieldsRO, structWithNonPrimitiveFieldsRO.wrap(buffer, 10, 10 + length));
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setStringValue(buffer, 10);
        assertSame(structWithNonPrimitiveFieldsRO, structWithNonPrimitiveFieldsRO.tryWrap(buffer, 10, 10 + length));
    }

    @Test
    public void shouldSetFieldsWithConsumer() throws Exception
    {
        int limit = structWithNonPrimitiveFieldsRW.wrap(buffer, 0, buffer.capacity())
            .stringField("stringValue")
            .enumField(b -> b.set(EnumWithUint8.ICHI))
            .unionField(b -> b.string1("unionValue"))
            .arrayField(b -> b.item(i -> i.string1("arrayItem")))
            .listField(b -> b.field0("listValue"))
            .variantField(b -> b.set(10))
            .build()
            .limit();

        final StructWithNonPrimitiveFieldsFW structWithNonPrimitiveFields = structWithNonPrimitiveFieldsRO.wrap(buffer,  0,
            limit);

        assertEquals("stringValue", structWithNonPrimitiveFields.stringField().asString());
        assertEquals(EnumWithUint8.ICHI, structWithNonPrimitiveFields.enumField().get());
        assertEquals("unionValue", structWithNonPrimitiveFields.unionField().string1().asString());
        assertEquals(1, structWithNonPrimitiveFields.arrayField().fieldCount());
        structWithNonPrimitiveFields.arrayField().forEach(i -> assertEquals("arrayItem", i.string1().asString()));
        assertEquals("listValue", structWithNonPrimitiveFields.listField().field0().asString());
        assertEquals(10, structWithNonPrimitiveFields.variantField().get());
    }

    @Test
    public void shouldSetFieldsWithFlyweights() throws Exception
    {
        final MutableDirectBuffer fieldBuffer = new UnsafeBuffer(allocateDirect(100))
        {
            {
                // Make sure the code is not secretly relying upon memory being initialized to 0
                setMemory(0, capacity(), (byte) 0xab);
            }
        };

        final EnumWithUint8FW enumWithUint8 = enumWithUint8RW.wrap(fieldBuffer, 0, fieldBuffer.capacity())
            .set(EnumWithUint8.ICHI)
            .build();

        final UnionOctetsFW unionOctets = unionOctetsRW.wrap(fieldBuffer, enumWithUint8.limit(), fieldBuffer.capacity())
            .string1("unionValue")
            .build();

        final Array32FW<UnionOctetsFW> array = arrayRW.wrap(fieldBuffer, unionOctets.limit(), fieldBuffer.capacity())
            .item(i -> i.string1("arrayItem"))
            .build();

        final ListWithPhysicalAndLogicalLengthFW listWithPhysicalAndLogicalLength =
            listWithPhysicalAndLogicalLengthRW.wrap(fieldBuffer, array.limit(), fieldBuffer.capacity())
                .field0("listValue")
                .build();

        final VariantEnumKindOfInt8FW variantEnumKindOfInt8 =
            variantEnumKindOfInt8RW.wrap(fieldBuffer, listWithPhysicalAndLogicalLength.limit(), fieldBuffer.capacity())
                .set(10)
                .build();

        int limit = structWithNonPrimitiveFieldsRW.wrap(buffer, 0, buffer.capacity())
            .stringField("stringValue")
            .enumField(enumWithUint8)
            .unionField(unionOctets)
            .arrayField(array)
            .listField(listWithPhysicalAndLogicalLength)
            .variantField(variantEnumKindOfInt8)
            .build()
            .limit();

        final StructWithNonPrimitiveFieldsFW structWithNonPrimitiveFields = structWithNonPrimitiveFieldsRO.wrap(buffer,  0,
            limit);

        assertEquals("stringValue", structWithNonPrimitiveFields.stringField().asString());
        assertEquals(EnumWithUint8.ICHI, structWithNonPrimitiveFields.enumField().get());
        assertEquals("unionValue", structWithNonPrimitiveFields.unionField().string1().asString());
        assertEquals(1, structWithNonPrimitiveFields.arrayField().fieldCount());
        structWithNonPrimitiveFields.arrayField().forEach(i -> assertEquals("arrayItem", i.string1().asString()));
        assertEquals("listValue", structWithNonPrimitiveFields.listField().field0().asString());
        assertEquals(10, structWithNonPrimitiveFields.variantField().get());
    }
}
