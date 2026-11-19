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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;

class ProtobufJsonSchemaTest
{
    @Test
    void shouldDescribeScalarFieldsByProto3JsonMapping()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "package test;\n" +
            "message Scalars {\n" +
            "  string text = 1;\n" +
            "  int32 count = 2;\n" +
            "  uint32 flags = 3;\n" +
            "  int64 big = 4;\n" +
            "  uint64 ubig = 5;\n" +
            "  bool enabled = 6;\n" +
            "  double ratio = 7;\n" +
            "  bytes payload = 8;\n" +
            "  string snake_case_name = 9;\n" +
            "}\n");

        JsonObject actual = ProtobufJsonSchema.of(schema, "test.Scalars");

        assertEquals(read(
            "{\"type\":\"object\",\"properties\":{" +
                "\"text\":{\"type\":\"string\"}," +
                "\"count\":{\"type\":\"integer\"}," +
                "\"flags\":{\"type\":\"integer\",\"minimum\":0}," +
                "\"big\":{\"type\":\"string\",\"pattern\":\"^-?[0-9]+$\"}," +
                "\"ubig\":{\"type\":\"string\",\"pattern\":\"^[0-9]+$\"}," +
                "\"enabled\":{\"type\":\"boolean\"}," +
                "\"ratio\":{\"type\":\"number\"}," +
                "\"payload\":{\"type\":\"string\",\"contentEncoding\":\"base64\"}," +
                "\"snakeCaseName\":{\"type\":\"string\"}}}"),
            actual);
    }

    @Test
    void shouldDescribeEnumFieldAsNames()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "package test;\n" +
            "enum Kind { UNKNOWN = 0; READ = 1; WRITE = 2; }\n" +
            "message Request { Kind kind = 1; }\n");

        JsonObject actual = ProtobufJsonSchema.of(schema, "test.Request");

        assertEquals(read(
            "{\"type\":\"object\",\"properties\":{" +
                "\"kind\":{\"type\":\"string\",\"enum\":[\"UNKNOWN\",\"READ\",\"WRITE\"]}}}"),
            actual);
    }

    @Test
    void shouldDescribeRepeatedFieldAsArray()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "package test;\n" +
            "message Request { repeated string names = 1; repeated int64 ids = 2; }\n");

        JsonObject actual = ProtobufJsonSchema.of(schema, "test.Request");

        assertEquals(read(
            "{\"type\":\"object\",\"properties\":{" +
                "\"names\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}," +
                "\"ids\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"pattern\":\"^-?[0-9]+$\"}}}}"),
            actual);
    }

    @Test
    void shouldDescribeMapFieldAsObject()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "package test;\n" +
            "message Request { map<string, int32> counts = 1; }\n");

        JsonObject actual = ProtobufJsonSchema.of(schema, "test.Request");

        assertEquals(read(
            "{\"type\":\"object\",\"properties\":{" +
                "\"counts\":{\"type\":\"object\",\"additionalProperties\":{\"type\":\"integer\"}}}}"),
            actual);
    }

    @Test
    void shouldDescribeNestedMessageInline()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "package test;\n" +
            "message Address { string city = 1; }\n" +
            "message Person { string name = 1; Address home = 2; repeated Address others = 3; }\n");

        JsonObject actual = ProtobufJsonSchema.of(schema, "test.Person");

        assertEquals(read(
            "{\"type\":\"object\",\"properties\":{" +
                "\"name\":{\"type\":\"string\"}," +
                "\"home\":{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}," +
                "\"others\":{\"type\":\"array\",\"items\":" +
                    "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}}}}"),
            actual);
    }

    @Test
    void shouldDescribeRecursiveMessageByReference()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "package test;\n" +
            "message Tree { string label = 1; repeated Tree children = 2; }\n");

        JsonObject actual = ProtobufJsonSchema.of(schema, "test.Tree");

        assertEquals(read(
            "{\"$ref\":\"#/$defs/test.Tree\",\"$defs\":{\"test.Tree\":{\"type\":\"object\",\"properties\":{" +
                "\"label\":{\"type\":\"string\"}," +
                "\"children\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/$defs/test.Tree\"}}}}}}"),
            actual);
    }

    @Test
    void shouldDescribeRecursiveMessageNestedInRoot()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "package test;\n" +
            "message Node { string id = 1; Node next = 2; }\n" +
            "message List { Node head = 1; }\n");

        JsonObject actual = ProtobufJsonSchema.of(schema, "test.List");

        assertEquals(read(
            "{\"type\":\"object\",\"properties\":{\"head\":{\"$ref\":\"#/$defs/test.Node\"}}," +
                "\"$defs\":{\"test.Node\":{\"type\":\"object\",\"properties\":{" +
                    "\"id\":{\"type\":\"string\"},\"next\":{\"$ref\":\"#/$defs/test.Node\"}}}}}"),
            actual);
    }

    @Test
    void shouldRequireProto2RequiredFields()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto2\";\n" +
            "package test;\n" +
            "message Request { required string id = 1; optional string note = 2; }\n");

        JsonObject actual = ProtobufJsonSchema.of(schema, "test.Request");

        assertEquals(read(
            "{\"type\":\"object\",\"properties\":{" +
                "\"id\":{\"type\":\"string\"},\"note\":{\"type\":\"string\"}},\"required\":[\"id\"]}"),
            actual);
    }

    @Test
    void shouldDescribeWrapperFieldAsUnderlyingScalar()
    {
        ProtobufSchema schema = Protobuf.schema()
            .message(ProtobufMessage.builder("ScoresEntry")
                .mapEntry(true)
                .field(ProtobufField.builder().number(1).name("key").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("value").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.Int32Value").build())
                .build())
            .message(ProtobufMessage.builder("Wrapped")
                .field(ProtobufField.builder().number(1).name("label").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.StringValue").build())
                .field(ProtobufField.builder().number(2).name("total").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.Int64Value").build())
                .field(ProtobufField.builder().number(3).name("tags").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.StringValue").repeated(true).build())
                .field(ProtobufField.builder().number(4).name("scores").type(ProtobufType.MESSAGE)
                    .typeName("ScoresEntry").repeated(true).build())
                .build())
            .build();

        JsonObject actual = ProtobufJsonSchema.of(schema, "Wrapped");

        assertEquals(read(
            "{\"type\":\"object\",\"properties\":{" +
                "\"label\":{\"type\":\"string\"}," +
                "\"total\":{\"type\":\"string\",\"pattern\":\"^-?[0-9]+$\"}," +
                "\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}," +
                "\"scores\":{\"type\":\"object\",\"additionalProperties\":{\"type\":\"integer\"}}}}"),
            actual);
    }

    @Test
    void shouldDescribeMessageWithoutFieldsAsEmptyObject()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "package test;\n" +
            "message Empty { }\n");

        JsonObject actual = ProtobufJsonSchema.of(schema, "test.Empty");

        assertEquals(read("{\"type\":\"object\",\"properties\":{}}"), actual);
    }

    @Test
    void shouldRejectUnknownMessage()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "package test;\n" +
            "message Request { string id = 1; }\n");

        assertThrows(ProtobufException.class, () -> ProtobufJsonSchema.of(schema, "test.Missing"));
    }

    private static JsonObject read(
        String text)
    {
        return Json.createReader(new StringReader(text)).readObject();
    }
}
