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
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.List8FW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.ListFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.StringFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.VariantEnumKindOfStringFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.VariantOfListFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.VariantOfMapFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.VariantWithFourTypesFW;

public class VariantWithFourTypesFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final VariantWithFourTypesFW.Builder flyweightRW = new VariantWithFourTypesFW.Builder();
    private final VariantWithFourTypesFW flyweightRO = new VariantWithFourTypesFW();
    public static final EnumWithInt8 KIND_LIST32 = EnumWithInt8.ONE;
    public static final EnumWithInt8 KIND_LIST8 = EnumWithInt8.TWO;
    public static final EnumWithInt8 KIND_LIST0 = EnumWithInt8.THREE;
    public static final EnumWithInt8 KIND_MAP32 = EnumWithInt8.FOUR;
    public static final EnumWithInt8 KIND_MAP16 = EnumWithInt8.FIVE;
    public static final EnumWithInt8 KIND_MAP8 = EnumWithInt8.SIX;
    public static final EnumWithInt8 KIND_STRING8 = EnumWithInt8.NINE;
    public static final EnumWithInt8 KIND_STRING16 = EnumWithInt8.TEN;
    public static final EnumWithInt8 KIND_STRING32 = EnumWithInt8.ELEVEN;

    @Test
    public void shouldSetAsVariantOfList()
    {
        MutableDirectBuffer variantOfListBuffer = new UnsafeBuffer(allocateDirect(20));
        VariantEnumKindOfStringFW.Builder field1RW = new VariantEnumKindOfStringFW.Builder();
        ListFW.Builder<List8FW> listRW = new List8FW.Builder()
            .wrap(variantOfListBuffer, 0, variantOfListBuffer.capacity())
            .field((b, o, m) -> field1RW.wrap(b, o, m).set(asStringFW("string1")).build().sizeof());
        VariantOfListFW variantOfList = new VariantOfListFW.Builder().wrap(variantOfListBuffer, 0,
            variantOfListBuffer.capacity()).set(listRW.build()).build();

        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsVariantOfList(variantOfList)
            .build()
            .limit();

        VariantWithFourTypesFW variantWithThreeTypes = flyweightRO.wrap(buffer, 0, limit);

        assertNotNull(variantWithThreeTypes.getAsVariantOfList());
        assertEquals(1, variantWithThreeTypes.getAsVariantOfList().get().fieldCount());
        assertEquals(KIND_LIST8, variantWithThreeTypes.kind());
    }

    @Test
    public void shouldSetAsVariantOfMap()
    {
        MutableDirectBuffer variantOfMapBuffer = new UnsafeBuffer(allocateDirect(30));

        VariantOfMapFW<VariantWithFourTypesFW, VariantWithFourTypesFW> variantOfMap =
            new VariantOfMapFW.Builder<>(new VariantWithFourTypesFW(), new VariantWithFourTypesFW(),
                new VariantWithFourTypesFW.Builder(), new VariantWithFourTypesFW.Builder())
                .wrap(variantOfMapBuffer, 0, variantOfMapBuffer.capacity())
                .entry(b -> b.setAsVariantEnumKindOfString(asVariantEnumKindOfStringFW(asStringFW("key"))),
                    b -> b.setAsVariantEnumKindOfString(asVariantEnumKindOfStringFW(asStringFW("value"))))
                .build();

        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsVariantOfMap(variantOfMap)
            .build()
            .limit();

        VariantWithFourTypesFW variantWithThreeTypes = flyweightRO.wrap(buffer, 0, limit);

        assertNotNull(variantWithThreeTypes.variantOfMap());
        assertEquals(2, variantWithThreeTypes.variantOfMap().fieldCount());
        List<String> mapItems = new ArrayList<>();
        variantWithThreeTypes.variantOfMap().get().forEach((k, v) ->
        {
            mapItems.add(k.getAsVariantEnumKindOfString().get().asString());
            mapItems.add(v.getAsVariantEnumKindOfString().get().asString());
        });
        assertEquals("key", mapItems.get(0));
        assertEquals("value", mapItems.get(1));
        assertEquals(KIND_MAP8, variantWithThreeTypes.kind());
    }

    @Test
    public void shouldSetAsVariantEnumKindString()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsVariantEnumKindOfString(asVariantEnumKindOfStringFW(asStringFW("stringValue")))
            .build()
            .limit();

        VariantWithFourTypesFW variantWithThreeTypes = flyweightRO.wrap(buffer, 0, limit);

        assertNotNull(variantWithThreeTypes.getAsVariantEnumKindOfString());
        assertEquals("stringValue", variantWithThreeTypes.getAsVariantEnumKindOfString().get().asString());
        assertEquals(KIND_STRING8, variantWithThreeTypes.kind());
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
