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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
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
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

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
        byte[] wire = nestedWire();

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
    public void shouldRoundTripMultibyteUtf8String()
    {
        // 2-byte (é), 3-byte (中), and 4-byte surrogate-pair (😀) UTF-8 sequences
        String json = "{\"name\":\"é中😀\",\"tags\":[\"é\",\"中\"]}";

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
    public void shouldStreamJsonAcrossTinyWindows()
    {
        String json = "{" +
            "\"name\":\"neo\"," +
            "\"home\":{\"name\":\"Zion\"}," +
            "\"nums\":[1,2,3]," +
            "\"props\":{\"k\":\"v\"}" +
            "}";

        byte[] whole = toProtobuf("Person", json);

        // each JSON leaf value must fit within a window; structure may split between tokens across windows
        for (int window : new int[]{8, 12, 20, 40})
        {
            byte[] streamed = toProtobufWindowed("Person", json, window);
            assertEquals(toJson("Person", whole), toJson("Person", streamed), "window=" + window);
        }
    }

    @Test
    public void shouldReusePipelineAcrossMessages()
    {
        UnsafeBufferEx out = new UnsafeBufferEx(new byte[4096]);
        ProtobufGenerator generator = Protobuf.generator();
        JsonParserEx parser = JsonEx.createParser();
        ProtobufPipeline pipeline = Protobuf.stream(ProtobufJson.parser(parser, schema, "Person"))
            .into(ProtobufSink.of(generator, schema, "Person"));

        byte[] first = feedReuse(pipeline, generator, out, "{\"name\":\"neo\",\"nums\":[1]}");
        byte[] second = feedReuse(pipeline, generator, out, "{\"name\":\"trinity\",\"nums\":[2,3]}");

        assertEquals("{\"name\":\"neo\",\"nums\":[1]}", toJson("Person", first));
        assertEquals("{\"name\":\"trinity\",\"nums\":[2,3]}", toJson("Person", second));
    }

    @Test
    public void shouldRoundTripGroup()
    {
        assertEquals("{\"grp\":{\"x\":5}}", roundTrip("Person", "{\"grp\":{\"x\":5}}"));
    }

    @Test
    public void shouldRoundTripRepeatedMessage()
    {
        assertEquals("{\"labels\":[{\"name\":\"a\"},{\"name\":\"b\"}]}",
            roundTrip("Person", "{\"labels\":[{\"name\":\"a\"},{\"name\":\"b\"}]}"));
    }

    @Test
    public void shouldRoundTripMessageValuedMap()
    {
        assertEquals("{\"meta\":{\"k\":{\"name\":\"v\"}}}",
            roundTrip("Person", "{\"meta\":{\"k\":{\"name\":\"v\"}}}"));
    }

    @Test
    public void shouldRoundTripMultiCharacterMapKey()
    {
        assertEquals("{\"props\":{\"hello-world\":\"v\"}}",
            roundTrip("Person", "{\"props\":{\"hello-world\":\"v\"}}"));
    }

    @Test
    public void shouldRoundTripNonFinite()
    {
        assertEquals("{\"fl\":\"NaN\",\"db\":\"-Infinity\"}",
            roundTrip("Scalars", "{\"fl\":\"NaN\",\"db\":\"-Infinity\"}"));
    }

    @Test
    public void shouldEncodeNumberFormsAsStrings()
    {
        assertEquals("{\"i64\":\"42\",\"u64\":\"7\"}", roundTrip("Scalars", "{\"i64\":42,\"u64\":7}"));
    }

    @Test
    public void shouldEncodeEnumNumber()
    {
        assertEquals("{\"en\":\"GREEN\"}", roundTrip("Scalars", "{\"en\":1}"));
    }

    @Test
    public void shouldRejectUnsupportedGeneratorWrites()
    {
        ProtobufGenerator generator = ProtobufJson.generator(JsonEx.createGenerator(), schema, "Person");
        generator.wrap(new UnsafeBufferEx(new byte[64]), 0, 64);
        UnsafeBufferEx any = new UnsafeBufferEx(new byte[]{1});
        assertThrows(RuntimeException.class, () -> generator.writeMessage(1, any, 0, 1));
        assertThrows(RuntimeException.class, () -> generator.writeRaw(any, 0, 1));
        assertThrows(RuntimeException.class, () -> generator.writeValue(1, ProtobufWireType.LEN, any, 0, 1));
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
    public void shouldRejectJsonSyntaxError()
    {
        // an invalid token where a value is expected makes the jakarta parser throw JsonParsingException; the
        // parser boundary translates it to ProtobufParsingException so the pipeline rejects rather than crashing
        assertEquals(Status.REJECTED, feedJson("Person", "{\"name\": &}"));
    }

    @Test
    public void shouldRejectNonJsonInput()
    {
        // raw non-JSON bytes: the very first token cannot be parsed
        assertEquals(Status.REJECTED, feedJson("Person", "@notjson@"));
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
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[8192]);
        ProtobufGenerator generator = ProtobufJson.generator(JsonEx.createGenerator(), schema, messageName);
        generator.wrap(out, 0, out.capacity());
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, messageName))
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();

        assertEquals(Status.COMPLETED, pipeline.transform(new UnsafeBufferEx(wire), 0, wire.length));
        generator.flush();

        byte[] bytes = new byte[generator.length()];
        out.getBytes(0, bytes);
        return new String(bytes, UTF_8);
    }

    private byte[] toProtobuf(
        String messageName,
        String json)
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[8192]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
        ProtobufPipeline pipeline = Protobuf.stream(ProtobufJson.parser(JsonEx.createParser(), schema, messageName))
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();

        byte[] in = json.getBytes(UTF_8);
        assertEquals(Status.COMPLETED, pipeline.transform(new UnsafeBufferEx(in), 0, in.length));

        byte[] bytes = new byte[generator.length()];
        out.getBytes(0, bytes);
        return bytes;
    }

    private byte[] toProtobufWindowed(
        String messageName,
        String json,
        int window)
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[8192]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
        ProtobufPipeline pipeline = Protobuf.stream(ProtobufJson.parser(JsonEx.createParser(), schema, messageName))
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();

        UnsafeBufferEx in = new UnsafeBufferEx(json.getBytes(UTF_8));
        int length = in.capacity();
        int progress = 0;
        int limit = 0;
        Status status = Status.STARVED;
        boolean done = false;
        while (!done)
        {
            int take = Math.min(window, length - limit);
            limit += take;
            boolean last = limit >= length;
            status = pipeline.transform(in, progress, limit, last);
            if (status == Status.STARVED)
            {
                progress = limit - pipeline.remaining();
            }
            else
            {
                done = true;
            }
        }

        assertEquals(Status.COMPLETED, status, "window=" + window);
        byte[] bytes = new byte[generator.length()];
        out.getBytes(0, bytes);
        return bytes;
    }

    private byte[] feedReuse(
        ProtobufPipeline pipeline,
        ProtobufGenerator generator,
        UnsafeBufferEx out,
        String json)
    {
        generator.wrap(out, 0, out.capacity());
        pipeline.reset();
        byte[] in = json.getBytes(UTF_8);
        assertEquals(Status.COMPLETED, pipeline.transform(new UnsafeBufferEx(in), 0, in.length));
        byte[] bytes = new byte[generator.length()];
        out.getBytes(0, bytes);
        return bytes;
    }

    private Status feedJson(
        String messageName,
        String json)
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[8192]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
        ProtobufPipeline pipeline = Protobuf.stream(ProtobufJson.parser(JsonEx.createParser(), schema, messageName))
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();
        byte[] in = json.getBytes(UTF_8);
        return pipeline.transform(new UnsafeBufferEx(in), 0, in.length);
    }

    private byte[] nestedWire()
    {
        return wire(g ->
        {
            g.writeString(1, "neo");
            g.startMessage(2, 16);
            g.writeString(1, "Zion");
            g.endMessage();
            g.writeInt32(3, 1).writeInt32(3, 2);
            g.writeString(4, "a").writeString(4, "b");
            g.startMessage(5, 16);
            g.writeString(1, "k");
            g.writeString(2, "v");
            g.endMessage();
            g.startMessage(6, 16);
            g.writeString(1, "s");
            g.writeInt32(2, 7);
            g.endMessage();
        });
    }

    private static byte[] wire(
        Consumer<ProtobufGenerator> body)
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[8192]);
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
            .message(ProtobufMessage.builder("Grp")
                .field(ProtobufField.builder().number(1).name("x").type(ProtobufType.INT32).build())
                .build())
            .message(ProtobufMessage.builder("MetaEntry")
                .mapEntry(true)
                .field(ProtobufField.builder().number(1).name("key").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("value").type(ProtobufType.MESSAGE).typeName("Nested")
                    .build())
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
                .field(ProtobufField.builder().number(7).name("labels").type(ProtobufType.MESSAGE).typeName("Nested")
                    .repeated(true).build())
                .field(ProtobufField.builder().number(8).name("grp").type(ProtobufType.GROUP).typeName("Grp").build())
                .field(ProtobufField.builder().number(9).name("meta").type(ProtobufType.MESSAGE).typeName("MetaEntry")
                    .repeated(true).build())
                .build())
            .build();
    }
}
