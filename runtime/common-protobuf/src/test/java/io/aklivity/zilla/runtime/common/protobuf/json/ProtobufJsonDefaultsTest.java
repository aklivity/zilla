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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;

public class ProtobufJsonDefaultsTest
{
    private static final String PROTO3 =
        "syntax = \"proto3\";\n" +
        "package t;\n" +
        "enum Color { RED = 0; GREEN = 1; }\n" +
        "message Nested { string v = 1; }\n" +
        "message Person {\n" +
        "  string name = 1;\n" +
        "  int32 id = 2;\n" +
        "  optional string nick = 3;\n" +
        "  Nested home = 4;\n" +
        "  repeated int32 nums = 5;\n" +
        "  map<string, int32> scores = 6;\n" +
        "  Color color = 7;\n" +
        "}\n";

    private static final String ALL =
        "syntax = \"proto3\";\n" +
        "package t;\n" +
        "message All {\n" +
        "  double db = 1;\n" +
        "  float fl = 2;\n" +
        "  int64 i64 = 3;\n" +
        "  uint64 u64 = 4;\n" +
        "  int32 i32 = 5;\n" +
        "  bool bo = 8;\n" +
        "  string st = 9;\n" +
        "  bytes by = 12;\n" +
        "  uint32 u32 = 13;\n" +
        "  sfixed64 sf64 = 16;\n" +
        "  map<string, int32> scores = 20;\n" +
        "}\n";

    private static final String PROTO2 =
        "syntax = \"proto2\";\n" +
        "package t;\n" +
        "enum Status { UNKNOWN = 0; ACTIVE = 1; }\n" +
        "message Account {\n" +
        "  required string name = 1;\n" +
        "  optional int32 with_default = 2 [default = 42];\n" +
        "  optional int32 plain = 3;\n" +
        "  repeated int32 nums = 4;\n" +
        "  optional bool bool_def = 5 [default = true];\n" +
        "  optional Status status = 6 [default = ACTIVE];\n" +
        "  optional string str_def = 7 [default = \"hi\"];\n" +
        "  optional int64 i64_def = 8 [default = 7];\n" +
        "}\n";

    @Test
    public void shouldIncludeProto3DefaultsAndOmitPresenceFields()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO3);
        byte[] wire = wire(g -> g.writeString(1, "neo"));

        assertEquals("{\"name\":\"neo\",\"id\":0,\"nums\":[],\"scores\":{},\"color\":\"RED\"}",
            toJson(schema, "t.Person", wire, true, true));
    }

    @Test
    public void shouldRenderAllProto3ScalarDefaults()
    {
        ProtobufSchema schema = Protobuf.schema(ALL);

        assertEquals("{\"db\":0.0,\"fl\":0.0,\"i64\":\"0\",\"u64\":\"0\",\"i32\":0," +
                "\"bo\":false,\"st\":\"\",\"by\":\"\",\"u32\":0,\"sf64\":\"0\",\"scores\":{}}",
            toJson(schema, "t.All", new byte[0], true, true));
    }

    @Test
    public void shouldRenderProto2DeclaredAndTypeDefaults()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO2);
        byte[] wire = wire(g -> g.writeString(1, "neo"));

        assertEquals("{\"name\":\"neo\",\"with_default\":42,\"plain\":0,\"nums\":[]," +
                "\"bool_def\":true,\"status\":\"ACTIVE\",\"str_def\":\"hi\",\"i64_def\":\"7\"}",
            toJson(schema, "t.Account", wire, true, true));
    }

    @Test
    public void shouldRecurseDefaultsIntoSetMessage()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO3);
        byte[] wire = wire(g -> g.startMessage(4, 0).endMessage());

        assertEquals("{\"home\":{\"v\":\"\"},\"name\":\"\",\"id\":0,\"nums\":[],\"scores\":{},\"color\":\"RED\"}",
            toJson(schema, "t.Person", wire, true, true));
    }

    @Test
    public void shouldUseProtoFieldNamesWithoutDefaults()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\nmessage M { string user_id = 1; }\n");
        byte[] wire = wire(g -> g.writeString(1, "x"));

        assertEquals("{\"user_id\":\"x\"}", toJson(schema, "M", wire, true, false));
        assertEquals("{\"userId\":\"x\"}", toJson(schema, "M", wire, false, false));
    }

    private static String toJson(
        ProtobufSchema schema,
        String messageName,
        byte[] wire,
        boolean protoFieldNames,
        boolean includeDefaults)
    {
        Map<String, Object> config = new HashMap<>();
        config.put(ProtobufJson.FIELD_NAMES,
            protoFieldNames ? ProtobufJson.FieldNames.PROTO : ProtobufJson.FieldNames.JSON);
        config.put(ProtobufJson.INCLUDE_DEFAULTS, includeDefaults);
        MutableDirectBuffer out = new UnsafeBuffer(new byte[8192]);
        ProtobufGenerator generator =
            ProtobufJson.generator(JsonEx.createGenerator(), schema, messageName, config);
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
