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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.json.ProtobufJson;

public class ProtobufSourceCompilerTest
{
    private static final String PROTO3 =
        "syntax = \"proto3\";\n" +
        "package test;\n" +
        "enum Color { RED = 0; GREEN = 1; BLUE = 2; }\n" +
        "message Nested { string name = 1; }\n" +
        "message Person {\n" +
        "  string name = 1;\n" +
        "  Nested home = 2;\n" +
        "  repeated int32 nums = 3;\n" +
        "  repeated string tags = 4;\n" +
        "  map<string, string> props = 5;\n" +
        "  map<string, int32> scores = 6;\n" +
        "  repeated Nested labels = 7;\n" +
        "  Color color = 8;\n" +
        "  optional string note = 9;\n" +
        "}\n";

    private static final String PROTO2 =
        "syntax = \"proto2\";\n" +
        "package test;\n" +
        "enum Status { ACTIVE = 0; INACTIVE = 1; }\n" +
        "message Account {\n" +
        "  required string name = 1;\n" +
        "  optional int32 id = 2;\n" +
        "  repeated int32 nums = 3;\n" +
        "  optional Status status = 4;\n" +
        "  map<string, int32> scores = 5;\n" +
        "  oneof kind {\n" +
        "    string s = 6;\n" +
        "    int32 i = 7;\n" +
        "  }\n" +
        "}\n";

    @Test
    public void shouldCompileProto3AndResolveNames()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO3);

        assertNotNull(schema.message("test.Person"));
        assertNotNull(schema.message("test.Nested"));
        assertNotNull(schema.enumeration("test.Color"));
        assertEquals("test.Nested", schema.message("test.Person").field(2).typeName());
        assertEquals("home", schema.message("test.Person").field(2).jsonName());
        assertTrue(schema.message("test.Person").field(3).repeated());
        assertEquals("test.Color", schema.message("test.Person").field(8).typeName());
        assertEquals(ProtobufType.ENUM, schema.message("test.Person").field(8).type());
    }

    @Test
    public void shouldExpandProto3MapAsEntryMessage()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO3);

        assertNotNull(schema.message("test.Person.PropsEntry"));
        assertTrue(schema.message("test.Person.PropsEntry").mapEntry());
        assertEquals("test.Person.PropsEntry", schema.message("test.Person").field(5).typeName());
        assertTrue(schema.message("test.Person").field(5).repeated());
        assertEquals(ProtobufType.STRING, schema.message("test.Person.PropsEntry").field(2).type());
        assertEquals(ProtobufType.INT32, schema.message("test.Person.ScoresEntry").field(2).type());
    }

    @Test
    public void shouldMarkProto3Optional()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO3);

        assertTrue(schema.message("test.Person").field(9).proto3Optional());
    }

    @Test
    public void shouldPackProto3RepeatedScalarByDefault()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO3);

        assertTrue(schema.message("test.Person").field(3).packed());
    }

    @Test
    public void shouldRoundTripProto3NestedRepeatedAndMaps()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO3);
        String json = "{" +
            "\"name\":\"neo\"," +
            "\"home\":{\"name\":\"Zion\"}," +
            "\"nums\":[1,2,3]," +
            "\"tags\":[\"a\",\"b\"]," +
            "\"props\":{\"k\":\"v\"}," +
            "\"scores\":{\"s\":7}" +
            "}";

        assertEquals(json, roundTrip(schema, "test.Person", json));
    }

    @Test
    public void shouldRenderProto3EnumAsName()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO3);

        assertEquals("{\"color\":\"GREEN\"}", roundTrip(schema, "test.Person", "{\"color\":\"GREEN\"}"));
    }

    @Test
    public void shouldCompileProto2OneofAndMap()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO2);

        assertEquals("kind", schema.message("test.Account").field(6).oneofName());
        assertEquals("kind", schema.message("test.Account").field(7).oneofName());
        assertTrue(schema.message("test.Account.ScoresEntry").mapEntry());
        assertEquals("test.Status", schema.message("test.Account").field(4).typeName());
    }

    @Test
    public void shouldNotPackProto2RepeatedScalarByDefault()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO2);

        assertFalse(schema.message("test.Account").field(3).packed());
    }

    @Test
    public void shouldValidateProto2RequiredField()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO2);

        byte[] valid = wire(g -> g.writeString(1, "neo"));
        byte[] missing = wire(g -> g.writeInt32(2, 7));

        assertTrue(schema.validate("test.Account", new UnsafeBuffer(valid), 0, valid.length));
        assertFalse(schema.validate("test.Account", new UnsafeBuffer(missing), 0, missing.length));
    }

    @Test
    public void shouldHonorExplicitPackedAndJsonNameOptions()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "message M {\n" +
            "  repeated int32 nums = 1 [packed = false];\n" +
            "  string user_id = 2 [json_name = \"uid\"];\n" +
            "}\n");

        assertFalse(schema.message("M").field(1).packed());
        assertEquals("uid", schema.message("M").field(2).jsonName());
    }

    @Test
    public void shouldDeriveJsonNameForSnakeCaseField()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "message M { string user_id = 1; }\n");

        assertEquals("userId", schema.message("M").field(1).jsonName());
    }

    @Test
    public void shouldCompileWithoutPackage()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "message Outer { message Inner { string v = 1; } Inner inner = 1; }\n");

        assertNotNull(schema.message("Outer.Inner"));
        assertEquals("Outer.Inner", schema.message("Outer").field(1).typeName());
    }

    @Test
    public void shouldRejectEmptySource()
    {
        assertThrows(ProtobufException.class, () -> Protobuf.schema(""));
        assertThrows(ProtobufException.class, () -> Protobuf.schema((CharSequence) null));
    }

    @Test
    public void shouldRejectUnresolvedType()
    {
        assertThrows(ProtobufException.class, () -> Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "message M { Missing other = 1; }\n"));
    }

    private static String roundTrip(
        ProtobufSchema schema,
        String messageName,
        String json)
    {
        return toJson(schema, messageName, toProtobuf(schema, messageName, json));
    }

    private static String toJson(
        ProtobufSchema schema,
        String messageName,
        byte[] wire)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[8192]);
        ProtobufGenerator generator = ProtobufJson.generator(JsonEx.createGenerator(), schema, messageName);
        generator.wrap(out, 0, out.capacity());
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, messageName))
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();

        assertEquals(Status.COMPLETED, pipeline.transform(new UnsafeBuffer(wire), 0, wire.length));
        generator.flush();

        byte[] bytes = new byte[generator.length()];
        out.getBytes(0, bytes);
        return new String(bytes, UTF_8);
    }

    private static byte[] toProtobuf(
        ProtobufSchema schema,
        String messageName,
        String json)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[8192]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
        ProtobufPipeline pipeline = Protobuf.stream(ProtobufJson.parser(JsonEx.createParser(), schema, messageName))
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();

        byte[] in = json.getBytes(UTF_8);
        assertEquals(Status.COMPLETED, pipeline.transform(new UnsafeBuffer(in), 0, in.length));

        byte[] bytes = new byte[generator.length()];
        out.getBytes(0, bytes);
        return bytes;
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
}
