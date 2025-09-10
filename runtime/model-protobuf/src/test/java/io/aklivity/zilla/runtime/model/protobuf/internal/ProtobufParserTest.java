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

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;


public class ProtobufParserTest
{
    @Test
    public void shouldParseProto2Schema()
    {
        // Given: A proto2 schema
        String proto2Schema =
                "syntax = \"proto2\";\n" +
                        "package example;\n" +
                        "\n" +
                        "message Person {\n" +
                        "  required string name = 1;\n" +
                        "  optional int32 age = 2;\n" +
                        "  repeated string email = 3;\n" +
                        "}\n";

        // When: Parsing with the unified parser
        ProtobufParser parser = new ProtobufParser(new FileDescriptor[0]);
        FileDescriptor descriptor = parser.parse(proto2Schema);

        // Then: The schema should be correctly parsed
        assertNotNull(descriptor);
        assertEquals("proto2", descriptor.getSyntax());
        assertEquals("example", descriptor.getPackage());
        assertEquals(1, descriptor.getMessageTypes().size());

        Descriptor messageDescriptor = descriptor.getMessageTypes().get(0);
        assertEquals("Person", messageDescriptor.getName());
        assertEquals(3, messageDescriptor.getFields().size());

        // Verify field details
        FieldDescriptor nameField = messageDescriptor.getFields().get(0);
        assertEquals("name", nameField.getName());
        assertEquals(FieldDescriptor.Type.STRING, nameField.getType());
        assertTrue(nameField.isRequired());

        FieldDescriptor ageField = messageDescriptor.getFields().get(1);
        assertEquals("age", ageField.getName());
        assertEquals(FieldDescriptor.Type.INT32, ageField.getType());
        assertTrue(ageField.isOptional());

        FieldDescriptor emailField = messageDescriptor.getFields().get(2);
        assertEquals("email", emailField.getName());
        assertEquals(FieldDescriptor.Type.STRING, emailField.getType());
        assertTrue(emailField.isRepeated());
    }

    @Test
    public void shouldParseProto3Schema()
    {
        // Given: A proto3 schema
        String proto3Schema =
                "syntax = \"proto3\";\n" +
                        "package example;\n" +
                        "\n" +
                        "message Person {\n" +
                        "  string name = 1;\n" +
                        "  int32 age = 2;\n" +
                        "  repeated string email = 3;\n" +
                        "  map<string, string> metadata = 4;\n" +
                        "}\n";

        // When: Parsing with the unified parser
        ProtobufParser parser = new ProtobufParser(new FileDescriptor[0]);
        FileDescriptor descriptor = parser.parse(proto3Schema);

        // Then: The schema should be correctly parsed
        assertNotNull(descriptor);
        assertEquals("proto3", descriptor.getSyntax());
        assertEquals("example", descriptor.getPackage());
        assertEquals(1, descriptor.getMessageTypes().size());

        Descriptor messageDescriptor = descriptor.getMessageTypes().get(0);
        assertEquals("Person", messageDescriptor.getName());
        assertEquals(4, messageDescriptor.getFields().size());

        // Verify field details
        FieldDescriptor nameField = messageDescriptor.getFields().get(0);
        assertEquals("name", nameField.getName());
        assertEquals(FieldDescriptor.Type.STRING, nameField.getType());
        assertFalse(nameField.isRequired());
        assertFalse(nameField.isRepeated());

        FieldDescriptor emailField = messageDescriptor.getFields().get(2);
        assertEquals("email", emailField.getName());
        assertEquals(FieldDescriptor.Type.STRING, emailField.getType());
        assertTrue(emailField.isRepeated());

        FieldDescriptor metadataField = messageDescriptor.getFields().get(3);
        assertEquals("metadata", metadataField.getName());
        assertTrue(metadataField.isMapField());
    }

    @Test
    public void shouldAutoDetectProto2Syntax()
    {
        // Given: A proto2 schema without explicit knowledge of syntax
        String proto2Schema =
                "syntax = \"proto2\";\n" +
                        "message Test {\n" +
                        "  required string field = 1;\n" +
                        "}\n";

        // When: Parsing without syntax override
        ProtobufParser parser = new ProtobufParser(new FileDescriptor[0]);
        FileDescriptor descriptor = parser.parse(proto2Schema);

        // Then: The parser should auto-detect proto2
        assertNotNull(descriptor);
        assertEquals("proto2", descriptor.getSyntax());
    }

    @Test
    public void shouldAutoDetectProto3Syntax()
    {
        // Given: A proto3 schema without explicit knowledge of syntax
        String proto3Schema =
                "syntax = \"proto3\";\n" +
                        "message Test {\n" +
                        "  string field = 1;\n" +
                        "}\n";

        // When: Parsing without syntax override
        ProtobufParser parser = new ProtobufParser(new FileDescriptor[0]);
        FileDescriptor descriptor = parser.parse(proto3Schema);

        // Then: The parser should auto-detect proto3
        assertNotNull(descriptor);
        assertEquals("proto3", descriptor.getSyntax());
    }



    @Test(expected = ProtobufParser.ProtobufParseException.class)
    public void shouldThrowExceptionForInvalidSchema()
    {
        // Given: An invalid protobuf schema
        String invalidSchema =
                "syntax = \"proto3\";\n" +
                        "this is not valid protobuf syntax";

        // When: Parsing the invalid schema
        ProtobufParser parser = new ProtobufParser(new FileDescriptor[0]);
        parser.parse(invalidSchema);

        // Then: Should throw ProtobufParseException
    }

    @Test(expected = ProtobufParser.ProtobufParseException.class)
    public void shouldThrowExceptionForUnknownSyntax()
    {
        // Given: A schema without syntax declaration
        String schemaWithoutSyntax =
                "message Test {\n" +
                        "  string field = 1;\n" +
                        "}\n";

        // When: Parsing without syntax declaration
        ProtobufParser parser = new ProtobufParser(new FileDescriptor[0]);
        parser.parse(schemaWithoutSyntax);

        // Then: Should throw exception - syntax declaration is required
    }

    @Test(expected = ProtobufParser.ProtobufParseException.class)
    public void shouldThrowExceptionForNullInput()
    {
        // Given: Null input
        ProtobufParser parser = new ProtobufParser(new FileDescriptor[0]);

        // When: Parsing null string
        parser.parse((String) null);

        // Then: Should throw exception
    }

    @Test(expected = ProtobufParser.ProtobufParseException.class)
    public void shouldThrowExceptionForEmptySchema()
    {
        // Given: Empty schema
        String emptySchema = "";

        // When: Parsing empty schema
        ProtobufParser parser = new ProtobufParser(new FileDescriptor[0]);
        parser.parse(emptySchema);

        // Then: Should throw exception
    }

    @Test
    public void shouldParseComplexProto2Schema()
    {
        // Given: A complex proto2 schema with enums, nested messages, and oneofs
        String complexSchema =
                "syntax = \"proto2\";\n" +
                        "package complex;\n" +
                        "\n" +
                        "enum Status {\n" +
                        "  UNKNOWN = 0;\n" +
                        "  ACTIVE = 1;\n" +
                        "  INACTIVE = 2;\n" +
                        "}\n" +
                        "\n" +
                        "message Address {\n" +
                        "  optional string street = 1;\n" +
                        "  optional string city = 2;\n" +
                        "}\n" +
                        "\n" +
                        "message Person {\n" +
                        "  required string name = 1;\n" +
                        "  optional int32 age = 2;\n" +
                        "  optional Status status = 3;\n" +
                        "  repeated Address addresses = 4;\n" +
                        "  \n" +
                        "  oneof contact {\n" +
                        "    string email = 5;\n" +
                        "    string phone = 6;\n" +
                        "  }\n" +
                        "}\n";

        // When: Parsing the complex schema
        ProtobufParser parser = new ProtobufParser(new FileDescriptor[0]);
        FileDescriptor descriptor = parser.parse(complexSchema);

        // Then: All elements should be correctly parsed
        assertNotNull(descriptor);
        assertEquals("proto2", descriptor.getSyntax());
        assertEquals("complex", descriptor.getPackage());
        assertEquals(2, descriptor.getMessageTypes().size());
        assertEquals(1, descriptor.getEnumTypes().size());

        // Verify enum
        assertEquals("Status", descriptor.getEnumTypes().get(0).getName());
        assertEquals(3, descriptor.getEnumTypes().get(0).getValues().size());

        // Verify Person message with oneof
        Descriptor personDescriptor = descriptor.findMessageTypeByName("Person");
        assertNotNull(personDescriptor);
        assertEquals(1, personDescriptor.getOneofs().size());
        assertEquals("contact", personDescriptor.getOneofs().get(0).getName());
    }
}
