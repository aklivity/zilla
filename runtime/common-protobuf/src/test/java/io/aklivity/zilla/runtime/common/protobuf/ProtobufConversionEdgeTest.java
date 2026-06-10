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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

public class ProtobufConversionEdgeTest
{
    @Test
    public void shouldRoundTripBoolKeyedMap()
    {
        assertEquals("{\"flags\":{\"true\":1}}", roundTrip(mapSchema(), "M", "{\"flags\":{\"true\":1}}"));
    }

    @Test
    public void shouldRoundTripInt64KeyedMap()
    {
        assertEquals("{\"longs\":{\"-5\":\"a\"}}", roundTrip(mapSchema(), "M", "{\"longs\":{\"-5\":\"a\"}}"));
    }

    @Test
    public void shouldRoundTripUint32KeyedMap()
    {
        assertEquals("{\"uints\":{\"4000000000\":\"a\"}}", roundTrip(mapSchema(), "M", "{\"uints\":{\"4000000000\":\"a\"}}"));
    }

    @Test
    public void shouldRoundTripFixed32KeyedMap()
    {
        assertEquals("{\"f32s\":{\"7\":\"a\"}}", roundTrip(mapSchema(), "M", "{\"f32s\":{\"7\":\"a\"}}"));
    }

    @Test
    public void shouldRoundTripSint64KeyedMap()
    {
        assertEquals("{\"s64s\":{\"-9\":\"a\"}}", roundTrip(mapSchema(), "M", "{\"s64s\":{\"-9\":\"a\"}}"));
    }

    @Test
    public void shouldRoundTripSfixed64KeyedMap()
    {
        assertEquals("{\"sf64s\":{\"-9\":\"a\"}}", roundTrip(mapSchema(), "M", "{\"sf64s\":{\"-9\":\"a\"}}"));
    }

    @Test
    public void shouldDropUnknownJsonFields()
    {
        ProtobufSchema schema = enumSchema();
        String json = "{\"name\":\"x\",\"extraObj\":{\"a\":1},\"extraArr\":[1,2],\"extraScalar\":5,\"color\":\"RED\"}";
        assertEquals("{\"name\":\"x\",\"color\":\"RED\"}", roundTrip(schema, "M", json));
    }

    @Test
    public void shouldEncodeEnumByNumber()
    {
        assertEquals("{\"color\":\"BLUE\"}", roundTrip(enumSchema(), "M", "{\"color\":2}"));
    }

    @Test
    public void shouldRejectUnknownEnumName()
    {
        assertThrows(ProtobufException.class, () -> roundTrip(enumSchema(), "M", "{\"color\":\"PURPLE\"}"));
    }

    @Test
    public void shouldRejectUnknownMessageOnEncode()
    {
        assertThrows(ProtobufException.class, () -> roundTrip(enumSchema(), "Nope", "{}"));
    }

    private String roundTrip(
        ProtobufSchema schema,
        String messageName,
        String json)
    {
        MutableDirectBuffer wire = new UnsafeBuffer(new byte[8192]);
        byte[] jsonBytes = json.getBytes(UTF_8);
        MutableDirectBuffer jsonBuffer = new UnsafeBuffer(jsonBytes);

        JsonToProtobuf encoder = StreamingProtobuf.jsonToProtobuf(schema);
        int wireLength = encoder.convert(messageName, jsonBuffer, 0, jsonBytes.length, wire, 0);

        MutableDirectBuffer out = new UnsafeBuffer(new byte[8192]);
        ProtobufToJson decoder = StreamingProtobuf.protobufToJson(schema);
        int jsonLength = decoder.convert(messageName, wire, 0, wireLength, out, 0);

        return out.getStringWithoutLengthUtf8(0, jsonLength);
    }

    private static ProtobufSchema enumSchema()
    {
        return StreamingProtobuf.schema()
            .enumeration(ProtobufEnum.builder("Color").value("RED", 0).value("GREEN", 1).value("BLUE", 2).build())
            .message(ProtobufMessage.builder("M")
                .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("color").type(ProtobufType.ENUM).typeName("Color").build())
                .build())
            .build();
    }

    private static ProtobufSchema mapSchema()
    {
        return StreamingProtobuf.schema()
            .message(entry("M.FlagsEntry", ProtobufType.BOOL, ProtobufType.INT32))
            .message(entry("M.LongsEntry", ProtobufType.INT64, ProtobufType.STRING))
            .message(entry("M.UintsEntry", ProtobufType.UINT32, ProtobufType.STRING))
            .message(entry("M.F32sEntry", ProtobufType.FIXED32, ProtobufType.STRING))
            .message(entry("M.S64sEntry", ProtobufType.SINT64, ProtobufType.STRING))
            .message(entry("M.Sf64sEntry", ProtobufType.SFIXED64, ProtobufType.STRING))
            .message(ProtobufMessage.builder("M")
                .field(mapField(1, "flags", "M.FlagsEntry"))
                .field(mapField(2, "longs", "M.LongsEntry"))
                .field(mapField(3, "uints", "M.UintsEntry"))
                .field(mapField(4, "f32s", "M.F32sEntry"))
                .field(mapField(5, "s64s", "M.S64sEntry"))
                .field(mapField(6, "sf64s", "M.Sf64sEntry"))
                .build())
            .build();
    }

    private static ProtobufMessage entry(
        String name,
        ProtobufType keyType,
        ProtobufType valueType)
    {
        return ProtobufMessage.builder(name)
            .mapEntry(true)
            .field(ProtobufField.builder().number(1).name("key").type(keyType).build())
            .field(ProtobufField.builder().number(2).name("value").type(valueType).build())
            .build();
    }

    private static ProtobufField mapField(
        int number,
        String name,
        String entryType)
    {
        return ProtobufField.builder().number(number).name(name).type(ProtobufType.MESSAGE)
            .typeName(entryType).repeated(true).build();
    }
}
