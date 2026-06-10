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

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

public class ProtobufRoundTripTest
{
    private final ProtobufSchema schema = newSchema();

    @Test
    public void shouldRoundTripScalars()
    {
        String json = "{\"name\":\"Neo\",\"id\":42,\"active\":true,\"score\":1.5,\"ratio\":0.25}";
        assertEquals(json, roundTrip("Person", json));
    }

    @Test
    public void shouldRoundTripInt64AsString()
    {
        String json = "{\"name\":\"\",\"big\":\"9223372036854775807\"}";
        assertEquals(json, roundTrip("Person", json));
    }

    @Test
    public void shouldRoundTripNegativeInt32()
    {
        String json = "{\"name\":\"\",\"id\":-7,\"delta\":-3}";
        assertEquals(json, roundTrip("Person", json));
    }

    @Test
    public void shouldRoundTripBytesAsBase64()
    {
        String json = "{\"name\":\"\",\"blob\":\"aGVsbG8=\"}";
        assertEquals(json, roundTrip("Person", json));
    }

    @Test
    public void shouldRoundTripRepeated()
    {
        String json = "{\"name\":\"\",\"emails\":[\"a@x\",\"b@y\"],\"scores\":[1,2,3]}";
        assertEquals(json, roundTrip("Person", json));
    }

    @Test
    public void shouldRoundTripEnum()
    {
        String json = "{\"name\":\"\",\"color\":\"GREEN\"}";
        assertEquals(json, roundTrip("Person", json));
    }

    @Test
    public void shouldRoundTripNestedMessage()
    {
        String json = "{\"name\":\"\",\"home\":{\"city\":\"Zion\",\"zip\":90210}}";
        assertEquals(json, roundTrip("Person", json));
    }

    @Test
    public void shouldRoundTripRepeatedNestedMessage()
    {
        String json = "{\"name\":\"\",\"places\":[{\"city\":\"A\",\"zip\":1},{\"city\":\"B\",\"zip\":2}]}";
        assertEquals(json, roundTrip("Person", json));
    }

    @Test
    public void shouldRoundTripOneof()
    {
        String json = "{\"name\":\"\",\"phone\":\"555\"}";
        assertEquals(json, roundTrip("Person", json));
    }

    @Test
    public void shouldRoundTripMap()
    {
        String json = "{\"name\":\"\",\"labels\":{\"x\":1,\"y\":2}}";
        assertEquals(json, roundTrip("Person", json));
    }

    @Test
    public void shouldRoundTripIntKeyedMap()
    {
        String json = "{\"name\":\"\",\"byId\":{\"1\":\"one\",\"2\":\"two\"}}";
        assertEquals(json, roundTrip("Person", json));
    }

    private String roundTrip(
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

    private static ProtobufSchema newSchema()
    {
        return StreamingProtobuf.schema()
            .enumeration(ProtobufEnum.builder("Color")
                .value("RED", 0)
                .value("GREEN", 1)
                .value("BLUE", 2)
                .build())
            .message(ProtobufMessage.builder("Address")
                .field(ProtobufField.builder().number(1).name("city").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("zip").type(ProtobufType.INT32).build())
                .build())
            .message(ProtobufMessage.builder("Person.LabelsEntry")
                .mapEntry(true)
                .field(ProtobufField.builder().number(1).name("key").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("value").type(ProtobufType.INT32).build())
                .build())
            .message(ProtobufMessage.builder("Person.ByIdEntry")
                .mapEntry(true)
                .field(ProtobufField.builder().number(1).name("key").type(ProtobufType.INT32).build())
                .field(ProtobufField.builder().number(2).name("value").type(ProtobufType.STRING).build())
                .build())
            .message(ProtobufMessage.builder("Person")
                .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("id").type(ProtobufType.INT32).build())
                .field(ProtobufField.builder().number(3).name("emails").type(ProtobufType.STRING).repeated(true).build())
                .field(ProtobufField.builder().number(4).name("active").type(ProtobufType.BOOL).build())
                .field(ProtobufField.builder().number(5).name("score").type(ProtobufType.DOUBLE).build())
                .field(ProtobufField.builder().number(6).name("ratio").type(ProtobufType.FLOAT).build())
                .field(ProtobufField.builder().number(7).name("big").type(ProtobufType.INT64).build())
                .field(ProtobufField.builder().number(8).name("delta").type(ProtobufType.SINT32).build())
                .field(ProtobufField.builder().number(9).name("blob").type(ProtobufType.BYTES).build())
                .field(ProtobufField.builder().number(10).name("scores").type(ProtobufType.INT32).repeated(true).build())
                .field(ProtobufField.builder().number(11).name("color").type(ProtobufType.ENUM).typeName("Color").build())
                .field(ProtobufField.builder().number(12).name("home").type(ProtobufType.MESSAGE).typeName("Address").build())
                .field(ProtobufField.builder().number(13).name("places").type(ProtobufType.MESSAGE).typeName("Address")
                    .repeated(true).build())
                .field(ProtobufField.builder().number(14).name("phone").type(ProtobufType.STRING).oneof("contact").build())
                .field(ProtobufField.builder().number(15).name("fax").type(ProtobufType.STRING).oneof("contact").build())
                .field(ProtobufField.builder().number(16).name("labels").type(ProtobufType.MESSAGE)
                    .typeName("Person.LabelsEntry").repeated(true).build())
                .field(ProtobufField.builder().number(17).name("by_id").type(ProtobufType.MESSAGE)
                    .typeName("Person.ByIdEntry").repeated(true).build())
                .build())
            .build();
    }
}
