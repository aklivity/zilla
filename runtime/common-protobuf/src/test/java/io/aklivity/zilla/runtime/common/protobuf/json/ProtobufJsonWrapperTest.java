/*
 * Copyright 2021-2026 Aklivity Inc
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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;

/**
 * Verifies the nine {@code google.protobuf.*Value} well-known wrapper types render to and parse from a bare
 * JSON scalar per the protobuf JSON mapping spec, instead of leaking their inner {@code value} field as a
 * nested object.
 */
public class ProtobufJsonWrapperTest
{
    private final ProtobufSchema schema = newSchema();

    @Test
    public void shouldRenderWrapperFieldsAsBareScalars()
    {
        byte[] wire = wire(g ->
        {
            g.startMessage(1, 0);
            g.writeString(1, "hello");
            g.endMessage();
            g.startMessage(2, 0);
            g.writeInt32(1, 42);
            g.endMessage();
            g.startMessage(3, 0);
            g.writeInt64(1, -7L);
            g.endMessage();
            g.startMessage(4, 0);
            g.writeUInt32(1, 9);
            g.endMessage();
            g.startMessage(5, 0);
            g.writeUInt64(1, 11L);
            g.endMessage();
            g.startMessage(6, 0);
            g.writeBool(1, true);
            g.endMessage();
            g.startMessage(7, 0);
            g.writeFloat(1, 0.5f);
            g.endMessage();
            g.startMessage(8, 0);
            g.writeDouble(1, 1.5);
            g.endMessage();
            g.startMessage(9, 0);
            g.writeBytes(1, new byte[]{1, 2, 3});
            g.endMessage();
        });

        assertEquals("{" +
            "\"note\":\"hello\"," +
            "\"count\":42," +
            "\"big\":\"-7\"," +
            "\"u32\":9," +
            "\"u64\":\"11\"," +
            "\"flag\":true," +
            "\"ratio\":0.5," +
            "\"precise\":1.5," +
            "\"data\":\"AQID\"" +
            "}", toJson("Wrapped", wire));
    }

    @Test
    public void shouldOmitAbsentWrapperField()
    {
        assertEquals("{}", toJson("Wrapped", new byte[0]));
    }

    @Test
    public void shouldRenderExplicitDefaultWrapperValue()
    {
        byte[] wire = wire(g ->
        {
            g.startMessage(1, 0);
            g.endMessage();
            g.startMessage(2, 0);
            g.endMessage();
            g.startMessage(3, 0);
            g.endMessage();
            g.startMessage(6, 0);
            g.endMessage();
            g.startMessage(9, 0);
            g.endMessage();
        });

        assertEquals("{\"note\":\"\",\"count\":0,\"big\":\"0\",\"flag\":false,\"data\":\"\"}",
            toJson("Wrapped", wire));
    }

    @Test
    public void shouldRenderRepeatedWrapperFieldAsBareArray()
    {
        byte[] wire = wire(g ->
        {
            g.startMessage(10, 0);
            g.writeString(1, "a");
            g.endMessage();
            g.startMessage(10, 0);
            g.writeString(1, "b");
            g.endMessage();
        });

        assertEquals("{\"tags\":[\"a\",\"b\"]}", toJson("Wrapped", wire));
    }

    @Test
    public void shouldRenderWrapperMapValueAsBareScalar()
    {
        byte[] wire = wire(g ->
        {
            g.startMessage(11, 0);
            g.writeString(1, "k");
            g.startMessage(2, 0);
            g.writeInt32(1, 5);
            g.endMessage();
            g.endMessage();
        });

        assertEquals("{\"scores\":{\"k\":5}}", toJson("Wrapped", wire));
    }

    @Test
    public void shouldRoundTripWrapperFields()
    {
        String json = "{" +
            "\"note\":\"hello\"," +
            "\"count\":42," +
            "\"big\":\"-7\"," +
            "\"u32\":9," +
            "\"u64\":\"11\"," +
            "\"flag\":true," +
            "\"ratio\":0.5," +
            "\"precise\":1.5," +
            "\"data\":\"AQID\"" +
            "}";

        assertEquals(json, roundTrip("Wrapped", json));
    }

    @Test
    public void shouldRoundTripRepeatedWrapperField()
    {
        assertEquals("{\"tags\":[\"a\",\"b\"]}", roundTrip("Wrapped", "{\"tags\":[\"a\",\"b\"]}"));
    }

    @Test
    public void shouldRoundTripWrapperMapValue()
    {
        assertEquals("{\"scores\":{\"k\":5}}", roundTrip("Wrapped", "{\"scores\":{\"k\":5}}"));
    }

    @Test
    public void shouldOmitNullWrapperValue()
    {
        assertEquals("{}", roundTrip("Wrapped", "{\"note\":null}"));
    }

    @Test
    public void shouldEncodeWrapperJsonAsExpectedWire()
    {
        byte[] expected = wire(g ->
        {
            g.startMessage(1, 0);
            g.writeString(1, "hello");
            g.endMessage();
        });

        byte[] actual = toProtobuf("Wrapped", "{\"note\":\"hello\"}");

        assertEquals(toJson("Wrapped", expected), toJson("Wrapped", actual));
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
        Map<String, Object> config = new HashMap<>();
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[8192]);
        ProtobufGenerator generator = ProtobufJson.generator(JsonEx.createGenerator(), schema, messageName, config);
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

    // deliberately does not declare any of the nine google.protobuf.*Value messages: ProtobufSchema
    // auto-registers their fixed single-field shape, so a field referencing one by typeName resolves
    // without the schema ever declaring or importing it
    private static ProtobufSchema newSchema()
    {
        return Protobuf.schema()
            .message(ProtobufMessage.builder("ScoresEntry")
                .mapEntry(true)
                .field(ProtobufField.builder().number(1).name("key").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("value").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.Int32Value").build())
                .build())
            .message(ProtobufMessage.builder("Wrapped")
                .field(ProtobufField.builder().number(1).name("note").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.StringValue").build())
                .field(ProtobufField.builder().number(2).name("count").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.Int32Value").build())
                .field(ProtobufField.builder().number(3).name("big").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.Int64Value").build())
                .field(ProtobufField.builder().number(4).name("u32").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.UInt32Value").build())
                .field(ProtobufField.builder().number(5).name("u64").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.UInt64Value").build())
                .field(ProtobufField.builder().number(6).name("flag").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.BoolValue").build())
                .field(ProtobufField.builder().number(7).name("ratio").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.FloatValue").build())
                .field(ProtobufField.builder().number(8).name("precise").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.DoubleValue").build())
                .field(ProtobufField.builder().number(9).name("data").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.BytesValue").build())
                .field(ProtobufField.builder().number(10).name("tags").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.StringValue").repeated(true).build())
                .field(ProtobufField.builder().number(11).name("scores").type(ProtobufType.MESSAGE)
                    .typeName("ScoresEntry").repeated(true).build())
                .build())
            .build();
    }
}
