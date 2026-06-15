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
package io.aklivity.zilla.runtime.common.protobuf.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.Consumer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEnum;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;

public class ProtobufJsonTest
{
    private final ProtobufSchema schema = newSchema();

    @Test
    public void shouldRenderScalarsAsJson()
    {
        byte[] wire = wire(g -> g
            .writeInt32(1, -5)
            .writeInt64(2, 42L)
            .writeUInt32(3, 0xffffffff)
            .writeUInt64(4, -1L)
            .writeSInt32(5, -6)
            .writeSInt64(6, -7L)
            .writeFixed32(7, 8)
            .writeFixed64(8, 9L)
            .writeSFixed32(9, -8)
            .writeSFixed64(10, -9L)
            .writeFloat(11, 0.25f)
            .writeDouble(12, 1.5)
            .writeBool(13, true)
            .writeString(14, "hi")
            .writeBytes(15, new byte[]{1, 2, 3})
            .writeEnum(16, 2));

        String json = toJson("Scalars", wire);

        assertEquals("{" +
            "\"i32\":-5," +
            "\"i64\":\"42\"," +
            "\"u32\":4294967295," +
            "\"u64\":\"18446744073709551615\"," +
            "\"si32\":-6," +
            "\"si64\":\"-7\"," +
            "\"f32\":8," +
            "\"f64\":\"9\"," +
            "\"sf32\":-8," +
            "\"sf64\":\"-9\"," +
            "\"fl\":0.25," +
            "\"db\":1.5," +
            "\"bo\":true," +
            "\"st\":\"hi\"," +
            "\"by\":\"AQID\"," +
            "\"en\":\"BLUE\"" +
            "}", json);
    }

    @Test
    public void shouldRenderUnknownEnumAsNumber()
    {
        byte[] wire = wire(g -> g.writeEnum(16, 7));

        assertEquals("{\"en\":7}", toJson("Scalars", wire));
    }

    @Test
    public void shouldRenderNonFiniteAsString()
    {
        byte[] wire = wire(g -> g
            .writeFloat(11, Float.NaN)
            .writeDouble(12, Double.POSITIVE_INFINITY));

        assertEquals("{\"fl\":\"NaN\",\"db\":\"Infinity\"}", toJson("Scalars", wire));
    }

    @Test
    public void shouldRenderNestedRepeatedAndMapsAsJson()
    {
        byte[] wire = wire(g ->
        {
            g.writeString(1, "neo");
            g.startMessage(2, 16).writeString(1, "Zion").endMessage();
            g.writeInt32(3, 1).writeInt32(3, 2);
            g.writeString(4, "a").writeString(4, "b");
            g.startMessage(5, 16).writeString(1, "k").writeString(2, "v").endMessage();
            g.startMessage(6, 16).writeString(1, "s").writeInt32(2, 7).endMessage();
        });

        String json = toJson("Person", wire);

        assertEquals("{" +
            "\"name\":\"neo\"," +
            "\"home\":{\"name\":\"Zion\"}," +
            "\"nums\":[1,2]," +
            "\"tags\":[\"a\",\"b\"]," +
            "\"props\":{\"k\":\"v\"}," +
            "\"scores\":{\"s\":7}" +
            "}", json);
    }

    @Test
    public void shouldRenderEmptyMessageAsJson()
    {
        assertEquals("{}", toJson("Person", new byte[0]));
    }

    @Test
    public void shouldRoundTripScalars()
    {
        String json = "{" +
            "\"i32\":-5," +
            "\"i64\":\"42\"," +
            "\"u32\":4294967295," +
            "\"u64\":\"18446744073709551615\"," +
            "\"fl\":0.25," +
            "\"db\":1.5," +
            "\"bo\":true," +
            "\"st\":\"hi\"," +
            "\"by\":\"AQID\"," +
            "\"en\":\"BLUE\"" +
            "}";

        assertEquals(json, roundTrip("Scalars", json));
    }

    @Test
    public void shouldRoundTripNestedRepeatedAndMaps()
    {
        String json = "{" +
            "\"name\":\"neo\"," +
            "\"home\":{\"name\":\"Zion\"}," +
            "\"nums\":[1,2,3]," +
            "\"tags\":[\"a\",\"b\"]," +
            "\"props\":{\"k\":\"v\"}," +
            "\"scores\":{\"s\":7}" +
            "}";

        assertEquals(json, roundTrip("Person", json));
    }

    @Test
    public void shouldEncodeJsonAsExpectedWire()
    {
        byte[] expected = wire(g -> g
            .writeString(1, "neo")
            .writeInt32(3, 5));

        byte[] actual = toProtobuf("Person", "{\"name\":\"neo\",\"nums\":[5]}");

        assertEquals(toJson("Person", expected), toJson("Person", actual));
    }

    @Test
    public void shouldIgnoreUnknownJsonField()
    {
        assertEquals("{\"name\":\"neo\"}",
            roundTrip("Person", "{\"unknown\":{\"a\":[1,2]},\"name\":\"neo\"}"));
    }

    @Test
    public void shouldOmitNullValue()
    {
        assertEquals("{\"name\":\"neo\"}",
            roundTrip("Person", "{\"home\":null,\"name\":\"neo\"}"));
    }

    @Test
    public void shouldRejectMalformedJson()
    {
        assertEquals(Status.REJECTED, feedJson("Person", "{\"name\":"));
    }

    @Test
    public void shouldRejectNonObjectJson()
    {
        assertEquals(Status.REJECTED, feedJson("Person", "[1,2,3]"));
    }

    @Test
    public void shouldRejectUnknownMessage()
    {
        assertEquals(Status.REJECTED, feedJson("Nope", "{}"));
    }

    @Test
    public void shouldRejectUnknownEnumValue()
    {
        assertEquals(Status.REJECTED, feedJson("Scalars", "{\"en\":\"PURPLE\"}"));
    }

    private String roundTrip(
        String messageName,
        String json)
    {
        return toJson(messageName, toProtobuf(messageName, json));
    }

    private String toJson(
        String messageName,
        byte[] wire)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[8192]);
        JsonGeneratorEx generator = JsonEx.createGenerator();
        generator.wrap(out, 0, out.capacity());
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, messageName))
            .into(ProtobufJson.sink(generator));
        pipeline.reset();

        Status status = pipeline.feed(new UnsafeBuffer(wire), 0, wire.length);
        assertEquals(Status.COMPLETED, status);

        byte[] bytes = new byte[generator.length()];
        out.getBytes(0, bytes);
        return new String(bytes, UTF_8);
    }

    private byte[] toProtobuf(
        String messageName,
        String json)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[8192]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
        JsonParserEx parser = JsonEx.createParser();
        ProtobufPipeline pipeline = ProtobufJson.stream(parser, schema, messageName)
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();

        byte[] in = json.getBytes(UTF_8);
        Status status = pipeline.feed(new UnsafeBuffer(in), 0, in.length);
        assertEquals(Status.COMPLETED, status);

        byte[] bytes = new byte[generator.length()];
        out.getBytes(0, bytes);
        return bytes;
    }

    private Status feedJson(
        String messageName,
        String json)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[8192]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
        JsonParserEx parser = JsonEx.createParser();
        ProtobufPipeline pipeline = ProtobufJson.stream(parser, schema, messageName)
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();

        byte[] in = json.getBytes(UTF_8);
        return pipeline.feed(new UnsafeBuffer(in), 0, in.length);
    }

    private static byte[] wire(
        Consumer<ProtobufGenerator> body)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[8192]);
        ProtobufGenerator generator = Protobuf.generator().wrap(buffer, 0, buffer.capacity());
        body.accept(generator);
        byte[] bytes = new byte[generator.length()];
        buffer.getBytes(0, bytes);
        return bytes;
    }

    private static ProtobufSchema newSchema()
    {
        return Protobuf.schema()
            .enumeration(ProtobufEnum.builder("Color")
                .value("RED", 0)
                .value("GREEN", 1)
                .value("BLUE", 2)
                .build())
            .message(ProtobufMessage.builder("Scalars")
                .field(ProtobufField.builder().number(1).name("i32").type(ProtobufType.INT32).build())
                .field(ProtobufField.builder().number(2).name("i64").type(ProtobufType.INT64).build())
                .field(ProtobufField.builder().number(3).name("u32").type(ProtobufType.UINT32).build())
                .field(ProtobufField.builder().number(4).name("u64").type(ProtobufType.UINT64).build())
                .field(ProtobufField.builder().number(5).name("si32").type(ProtobufType.SINT32).build())
                .field(ProtobufField.builder().number(6).name("si64").type(ProtobufType.SINT64).build())
                .field(ProtobufField.builder().number(7).name("f32").type(ProtobufType.FIXED32).build())
                .field(ProtobufField.builder().number(8).name("f64").type(ProtobufType.FIXED64).build())
                .field(ProtobufField.builder().number(9).name("sf32").type(ProtobufType.SFIXED32).build())
                .field(ProtobufField.builder().number(10).name("sf64").type(ProtobufType.SFIXED64).build())
                .field(ProtobufField.builder().number(11).name("fl").type(ProtobufType.FLOAT).build())
                .field(ProtobufField.builder().number(12).name("db").type(ProtobufType.DOUBLE).build())
                .field(ProtobufField.builder().number(13).name("bo").type(ProtobufType.BOOL).build())
                .field(ProtobufField.builder().number(14).name("st").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(15).name("by").type(ProtobufType.BYTES).build())
                .field(ProtobufField.builder().number(16).name("en").type(ProtobufType.ENUM).typeName("Color").build())
                .build())
            .message(ProtobufMessage.builder("Nested")
                .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
                .build())
            .message(ProtobufMessage.builder("PropsEntry")
                .mapEntry(true)
                .field(ProtobufField.builder().number(1).name("key").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("value").type(ProtobufType.STRING).build())
                .build())
            .message(ProtobufMessage.builder("ScoresEntry")
                .mapEntry(true)
                .field(ProtobufField.builder().number(1).name("key").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("value").type(ProtobufType.INT32).build())
                .build())
            .message(ProtobufMessage.builder("Person")
                .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("home").type(ProtobufType.MESSAGE).typeName("Nested")
                    .build())
                .field(ProtobufField.builder().number(3).name("nums").type(ProtobufType.INT32).repeated(true).build())
                .field(ProtobufField.builder().number(4).name("tags").type(ProtobufType.STRING).repeated(true).build())
                .field(ProtobufField.builder().number(5).name("props").type(ProtobufType.MESSAGE).typeName("PropsEntry")
                    .repeated(true).build())
                .field(ProtobufField.builder().number(6).name("scores").type(ProtobufType.MESSAGE)
                    .typeName("ScoresEntry").repeated(true).build())
                .build())
            .build();
    }
}
