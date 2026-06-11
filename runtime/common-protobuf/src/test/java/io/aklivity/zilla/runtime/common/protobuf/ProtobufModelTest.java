/*
 * Copyright 2021-2024 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.common.protobuf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ProtobufModelTest
{
    @Test
    public void shouldMapTypeNumbersAndWireTypes()
    {
        assertEquals(ProtobufType.INT64, ProtobufType.ofNumber(3));
        assertEquals(ProtobufWireType.VARINT, ProtobufType.INT32.wireType());
        assertEquals(ProtobufWireType.I64, ProtobufType.DOUBLE.wireType());
        assertEquals(ProtobufWireType.I32, ProtobufType.FIXED32.wireType());
        assertEquals(ProtobufWireType.LEN, ProtobufType.STRING.wireType());
        assertEquals(5, ProtobufType.INT32.number());
    }

    @Test
    public void shouldClassifyTypes()
    {
        assertTrue(ProtobufType.INT32.packable());
        assertFalse(ProtobufType.STRING.packable());
        assertFalse(ProtobufType.MESSAGE.packable());
        assertTrue(ProtobufType.SINT32.zigzag());
        assertTrue(ProtobufType.UINT64.unsigned());
        assertFalse(ProtobufType.INT64.unsigned());
    }

    @Test
    public void shouldRejectInvalidTypeNumber()
    {
        assertThrows(ProtobufException.class, () -> ProtobufType.ofNumber(0));
        assertThrows(ProtobufException.class, () -> ProtobufType.ofNumber(99));
    }

    @Test
    public void shouldResolveWireTypeByCode()
    {
        assertEquals(ProtobufWireType.VARINT, ProtobufWireType.of(0));
        assertEquals(ProtobufWireType.SGROUP, ProtobufWireType.of(3));
        assertEquals(2, ProtobufWireType.LEN.code());
    }

    @Test
    public void shouldRejectInvalidWireTypeCode()
    {
        assertThrows(ProtobufException.class, () -> ProtobufWireType.of(7));
        assertThrows(ProtobufException.class, () -> ProtobufWireType.of(-1));
    }

    @Test
    public void shouldDeriveJsonName()
    {
        assertEquals("fooBarBaz", ProtobufField.toJsonName("foo_bar_baz"));
        assertEquals("name", ProtobufField.toJsonName("name"));
        assertEquals("aB", ProtobufField.toJsonName("a_b"));
    }

    @Test
    public void shouldDefaultFieldJsonNameAndPacked()
    {
        ProtobufField field = ProtobufField.builder()
            .number(3)
            .name("user_id")
            .type(ProtobufType.INT32)
            .repeated(true)
            .build();
        assertEquals("userId", field.jsonName());
        assertTrue(field.packed());
        assertTrue(field.repeated());
        assertFalse(field.composite());
    }

    @Test
    public void shouldHonorExplicitFieldProperties()
    {
        ProtobufField field = ProtobufField.builder()
            .number(1)
            .name("the_field")
            .jsonName("custom")
            .type(ProtobufType.MESSAGE)
            .typeName("Other")
            .packed(false)
            .proto3Optional(true)
            .oneof("kind")
            .build();
        assertEquals("custom", field.jsonName());
        assertEquals("Other", field.typeName());
        assertEquals("kind", field.oneofName());
        assertTrue(field.proto3Optional());
        assertTrue(field.composite());
    }

    @Test
    public void shouldRejectFieldWithoutNameOrType()
    {
        assertThrows(NullPointerException.class, () -> ProtobufField.builder().type(ProtobufType.INT32).build());
        assertThrows(NullPointerException.class, () -> ProtobufField.builder().name("x").build());
    }

    @Test
    public void shouldLookUpEnumValuesBothWays()
    {
        ProtobufEnum color = ProtobufEnum.builder("Color")
            .value("RED", 0)
            .value("GREEN", 1)
            .build();
        assertEquals("Color", color.name());
        assertEquals("GREEN", color.name(1));
        assertEquals(Integer.valueOf(0), color.number("RED"));
        assertNull(color.name(99));
        assertNull(color.number("PURPLE"));
    }

    @Test
    public void shouldLookUpMessageFields()
    {
        ProtobufMessage message = ProtobufMessage.builder("Entry")
            .mapEntry(true)
            .field(ProtobufField.builder().number(1).name("key").type(ProtobufType.STRING).build())
            .field(ProtobufField.builder().number(2).name("value").type(ProtobufType.INT32).build())
            .build();
        assertTrue(message.mapEntry());
        assertEquals(2, message.fields().size());
        assertEquals("key", message.field(1).name());
        assertEquals("value", message.field("value").name());
        assertEquals(1, message.mapKey().number());
        assertEquals(2, message.mapValue().number());
    }

    @Test
    public void shouldResolveSchemaReferences()
    {
        ProtobufEnum color = ProtobufEnum.builder("Color").value("RED", 0).build();
        ProtobufMessage address = ProtobufMessage.builder("Address")
            .field(ProtobufField.builder().number(1).name("city").type(ProtobufType.STRING).build())
            .build();
        ProtobufField messageField = ProtobufField.builder()
            .number(1).name("home").type(ProtobufType.MESSAGE).typeName("Address").build();
        ProtobufField enumField = ProtobufField.builder()
            .number(2).name("color").type(ProtobufType.ENUM).typeName("Color").build();
        ProtobufField scalarField = ProtobufField.builder()
            .number(3).name("count").type(ProtobufType.INT32).build();

        ProtobufSchema schema = Protobuf.schema()
            .message(address)
            .enumeration(color)
            .build();

        assertEquals("Address", schema.resolveMessage(messageField).name());
        assertEquals("Color", schema.resolveEnum(enumField).name());
        assertNull(schema.resolveMessage(scalarField));
        assertNull(schema.resolveEnum(scalarField));
        assertNull(schema.message("Missing"));
        assertNull(schema.enumeration("Missing"));
    }

    @Test
    public void shouldRejectUnresolvedReferences()
    {
        ProtobufSchema schema = Protobuf.schema().build();
        ProtobufField messageField = ProtobufField.builder()
            .number(1).name("home").type(ProtobufType.MESSAGE).typeName("Nope").build();
        ProtobufField enumField = ProtobufField.builder()
            .number(2).name("color").type(ProtobufType.ENUM).typeName("Nope").build();

        assertThrows(ProtobufException.class, () -> schema.resolveMessage(messageField));
        assertThrows(ProtobufException.class, () -> schema.resolveEnum(enumField));
    }

    @Test
    public void shouldCarryExceptionCause()
    {
        Throwable cause = new IllegalStateException("boom");
        ProtobufException exception = new ProtobufException("wrapped", cause);
        assertEquals("wrapped", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
