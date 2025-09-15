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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import static io.aklivity.zilla.runtime.model.protobuf.internal.ProtobufParser.ProtoSyntax.PROTO2;
import static io.aklivity.zilla.runtime.model.protobuf.internal.ProtobufParser.ProtoSyntax.PROTO3;
import static io.aklivity.zilla.runtime.model.protobuf.internal.ProtobufParser.ProtoSyntax.UNKNOWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;

public class ProtobufParserTest
{
    private ProtobufParser parser;

    @Before
    public void setUp()
    {
        parser = new ProtobufParser(null);
    }


    @Test
    public void shouldDetectProto2Syntax()
    {
        String schema = """
                syntax = "proto2";
                message Test {}""";

        assertEquals(PROTO2, ProtobufParser.detectSyntax(schema));
    }

    @Test
    public void shouldDetectProto3Syntax()
    {
        String schema = """
                syntax = "proto3";
                message Test {}""";

        assertEquals(PROTO3, ProtobufParser.detectSyntax(schema));
    }

    @Test
    public void shouldDetectSyntaxCaseInsensitive()
    {
        String schema1 = """
                SYNTAX = "proto3";
                """;
        String schema2 = """
                Syntax = "proto3";
                """;
        String schema3 = """
                syntax = "PROTO3";
                """;

        assertEquals(PROTO3, ProtobufParser.detectSyntax(schema1));
        assertEquals(PROTO3, ProtobufParser.detectSyntax(schema2));
        assertEquals(PROTO3, ProtobufParser.detectSyntax(schema3));
    }

    @Test
    public void shouldReturnUnknownForNullSchema()
    {
        assertEquals(UNKNOWN, ProtobufParser.detectSyntax(null));
    }

    @Test
    public void shouldReturnUnknownForInvalidSyntax()
    {
        String schema = """
                syntax = "proto4";
                """;
        assertEquals(UNKNOWN, ProtobufParser.detectSyntax(schema));
    }



    @Test
    public void shouldParseProto2SimpleMessage()
    {
        String schema = """
                syntax = "proto2";
                package test;
                message Person {
                  required string name = 1;
                  optional int32 age = 2;
                }""";

        FileDescriptor descriptor = parser.parse(schema);

        assertNotNull(descriptor);
        assertEquals("test", descriptor.getPackage());
        assertEquals(1, descriptor.getMessageTypes().size());

        Descriptor messageType = descriptor.getMessageTypes().get(0);
        assertEquals("Person", messageType.getName());
        assertEquals(2, messageType.getFields().size());

        FieldDescriptor nameField = messageType.findFieldByName("name");
        assertNotNull(nameField);
        assertEquals(1, nameField.getNumber());
        assertTrue(nameField.isRequired());

        FieldDescriptor ageField = messageType.findFieldByName("age");
        assertNotNull(ageField);
        assertEquals(2, ageField.getNumber());
        assertTrue(ageField.isOptional());
    }

    @Test
    public void shouldParseProto2WithRepeatedField()
    {
        String schema = """
                syntax = "proto2";
                message Test {
                  repeated string items = 1;
                }""";

        FileDescriptor descriptor = parser.parse(schema);

        assertNotNull(descriptor);
        Descriptor messageType = descriptor.getMessageTypes().get(0);
        FieldDescriptor field = messageType.findFieldByName("items");
        assertTrue(field.isRepeated());
    }

    @Test
    public void shouldParseProto2WithEnum()
    {
        String schema = """
                syntax = "proto2";
                enum Status {
                  UNKNOWN = 0;
                  ACTIVE = 1;
                  INACTIVE = 2;
                }
                message Test {
                  optional Status status = 1;
                }""";

        FileDescriptor descriptor = parser.parse(schema);

        assertNotNull(descriptor);
        assertEquals(1, descriptor.getEnumTypes().size());

        EnumDescriptor enumType = descriptor.getEnumTypes().get(0);
        assertEquals("Status", enumType.getName());
        assertEquals(3, enumType.getValues().size());
    }

    @Test
    public void shouldParseProto2WithNestedMessage()
    {
        String schema = """
                syntax = "proto2";
                message Outer {
                  message Inner {
                    optional string value = 1;
                  }
                  optional Inner inner = 1;
                }""";

        FileDescriptor descriptor = parser.parse(schema);

        assertNotNull(descriptor);
        Descriptor outerType = descriptor.getMessageTypes().get(0);
        assertEquals("Outer", outerType.getName());
        assertEquals(1, outerType.getNestedTypes().size());

        Descriptor innerType = outerType.getNestedTypes().get(0);
        assertEquals("Inner", innerType.getName());
    }

    @Test
    public void shouldParseProto3SimpleMessage()
    {
        String schema = """
                syntax = "proto3";
                package test;
                message Person {
                  string name = 1;
                  int32 age = 2;
                }""";

        FileDescriptor descriptor = parser.parse(schema);

        assertNotNull(descriptor);
        assertEquals("test", descriptor.getPackage());
        assertEquals(1, descriptor.getMessageTypes().size());

        Descriptor messageType = descriptor.getMessageTypes().get(0);
        assertEquals("Person", messageType.getName());
        assertEquals(2, messageType.getFields().size());
    }

    @Test
    public void shouldParseProto3WithRepeatedField()
    {
        String schema = """
                syntax = "proto3";
                message Test {
                  repeated string items = 1;
                }""";

        FileDescriptor descriptor = parser.parse(schema);

        assertNotNull(descriptor);
        Descriptor messageType = descriptor.getMessageTypes().get(0);
        FieldDescriptor field = messageType.findFieldByName("items");
        assertTrue(field.isRepeated());
    }

    @Test
    public void shouldReturnNullForEmptySchema()
    {
        FileDescriptor descriptor = parser.parse("");
        assertNull(descriptor);
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowExceptionForInvalidProto2Syntax()
    {
        String schema = """
                syntax = "proto2";
                message Test {
                  invalid syntax here
                }""";

        parser.parse(schema);
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowExceptionForInvalidProto3Syntax()
    {
        String schema = """
                syntax = "proto3";
                message Test {
                  invalid syntax here
                }""";

        parser.parse(schema);
    }

    @Test
    public void shouldHandleNullDependencies()
    {
        ProtobufParser parserWithNullDeps = new ProtobufParser(null);
        String schema = """
                syntax = "proto3";
                message Test {}""";

        FileDescriptor descriptor = parserWithNullDeps.parse(schema);
        assertNotNull(descriptor);
    }

    @Test
    public void shouldHandleEmptyDependencies()
    {
        ProtobufParser parserWithEmptyDeps = new ProtobufParser(new FileDescriptor[0]);
        String schema = """
                syntax = "proto3";
                message Test {}""";

        FileDescriptor descriptor = parserWithEmptyDeps.parse(schema);
        assertNotNull(descriptor);
    }

    @Test
    public void shouldParseComplexProto2Schema()
    {
        String schema = """
                syntax = "proto2";
                package complex;
                enum Color {
                  RED = 0;
                  GREEN = 1;
                  BLUE = 2;
                }
                message Address {
                  optional string street = 1;
                  optional string city = 2;
                  optional string state = 3;
                  optional int32 zip = 4;
                }
                message Person {
                  required string name = 1;
                  optional int32 age = 2;
                  repeated string emails = 3;
                  optional Address address = 4;
                  optional Color favorite_color = 5;
                  message PhoneNumber {
                    required string number = 1;
                    optional string type = 2;
                  }
                  repeated PhoneNumber phones = 6;
                }""";

        FileDescriptor descriptor = parser.parse(schema);

        assertNotNull(descriptor);
        assertEquals("complex", descriptor.getPackage());
        assertEquals(2, descriptor.getMessageTypes().size());
        assertEquals(1, descriptor.getEnumTypes().size());

        Descriptor personType = descriptor.findMessageTypeByName("Person");
        assertNotNull(personType);
        assertEquals(6, personType.getFields().size());
        assertEquals(1, personType.getNestedTypes().size());
    }
}
