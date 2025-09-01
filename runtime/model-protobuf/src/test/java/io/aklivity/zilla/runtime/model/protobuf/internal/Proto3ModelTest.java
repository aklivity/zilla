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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;

public class Proto3ModelTest
{
    @Test
    public void shouldParseProto3WithImplicitOptionalFields() throws Exception
    {
        String proto3Schema = """
            syntax = "proto3";
            
            package test;
            
            message Person {
              string name = 1;
              int32 id = 2;
              string email = 3;
              repeated string phone = 4;
            }
            """;

        FileDescriptor descriptor = parseProto3Schema(proto3Schema);
        
        assertNotNull(descriptor);
        assertEquals("proto3", descriptor.getSyntax());
        assertEquals("test", descriptor.getPackage());
        
        Descriptors.Descriptor personMessage = descriptor.findMessageTypeByName("Person");
        assertNotNull(personMessage);
        
        // In proto3, all fields are implicitly optional (except repeated)
        FieldDescriptor nameField = personMessage.findFieldByName("name");
        assertNotNull(nameField);
        // Proto3 doesn't use LABEL_OPTIONAL for regular fields
        
        FieldDescriptor phoneField = personMessage.findFieldByName("phone");
        assertNotNull(phoneField);
        assertEquals(Label.LABEL_REPEATED, phoneField.toProto().getLabel());
    }
    
    @Test
    public void shouldParseProto3WithOneOf() throws Exception
    {
        String proto3Schema = """
            syntax = "proto3";
            
            message TestMessage {
              oneof test_oneof {
                string name = 1;
                int32 id = 2;
              }
              string common_field = 3;
            }
            """;

        FileDescriptor descriptor = parseProto3Schema(proto3Schema);
        
        assertNotNull(descriptor);
        
        Descriptors.Descriptor testMessage = descriptor.findMessageTypeByName("TestMessage");
        assertNotNull(testMessage);
        
        // Verify oneof fields
        FieldDescriptor nameField = testMessage.findFieldByName("name");
        assertNotNull(nameField);
        assertNotNull(nameField.getContainingOneof());
        assertEquals("test_oneof", nameField.getContainingOneof().getName());
        
        FieldDescriptor idField = testMessage.findFieldByName("id");
        assertNotNull(idField);
        assertNotNull(idField.getContainingOneof());
        assertEquals("test_oneof", idField.getContainingOneof().getName());
        
        FieldDescriptor commonField = testMessage.findFieldByName("common_field");
        assertNotNull(commonField);
        assertNull(commonField.getContainingOneof());
    }
    
    @Test
    public void shouldParseProto3WithMaps() throws Exception
    {
        String proto3Schema = """
            syntax = "proto3";
            
            message MapMessage {
              map<string, string> string_map = 1;
              map<int32, string> int_string_map = 2;
              map<string, Person> person_map = 3;
            }
            
            message Person {
              string name = 1;
              int32 age = 2;
            }
            """;

        FileDescriptor descriptor = parseProto3Schema(proto3Schema);
        
        assertNotNull(descriptor);
        
        Descriptors.Descriptor mapMessage = descriptor.findMessageTypeByName("MapMessage");
        assertNotNull(mapMessage);
        
        // Verify map fields
        FieldDescriptor stringMapField = mapMessage.findFieldByName("string_map");
        assertNotNull(stringMapField);
        assertEquals(FieldDescriptor.Type.MESSAGE, stringMapField.getType());
        assertTrue(stringMapField.isMapField());
        
        FieldDescriptor intStringMapField = mapMessage.findFieldByName("int_string_map");
        assertNotNull(intStringMapField);
        assertTrue(intStringMapField.isMapField());
        
        FieldDescriptor personMapField = mapMessage.findFieldByName("person_map");
        assertNotNull(personMapField);
        assertTrue(personMapField.isMapField());
    }
    
    @Test
    public void shouldParseProto3WithEnums() throws Exception
    {
        String proto3Schema = """
            syntax = "proto3";
            
            message Settings {
              enum Theme {
                THEME_UNSPECIFIED = 0;  // Proto3 requires first enum value to be 0
                LIGHT = 1;
                DARK = 2;
                AUTO = 3;
              }
              
              Theme theme = 1;
              repeated Theme available_themes = 2;
            }
            """;

        FileDescriptor descriptor = parseProto3Schema(proto3Schema);
        
        assertNotNull(descriptor);
        
        Descriptors.Descriptor settingsMessage = descriptor.findMessageTypeByName("Settings");
        assertNotNull(settingsMessage);
        
        // Verify enum
        Descriptors.EnumDescriptor themeEnum = settingsMessage.findEnumTypeByName("Theme");
        assertNotNull(themeEnum);
        assertEquals(4, themeEnum.getValues().size());
        
        // Proto3 requires first enum value to be 0
        assertEquals(0, themeEnum.getValues().get(0).getNumber());
        assertEquals("THEME_UNSPECIFIED", themeEnum.getValues().get(0).getName());
    }
    
    @Test
    public void shouldParseProto3WithNestedMessages() throws Exception
    {
        String proto3Schema = """
            syntax = "proto3";
            
            message Outer {
              message Middle {
                message Inner {
                  string value = 1;
                }
                Inner inner = 1;
              }
              Middle middle = 1;
              string name = 2;
            }
            """;

        FileDescriptor descriptor = parseProto3Schema(proto3Schema);
        
        assertNotNull(descriptor);
        
        Descriptors.Descriptor outerMessage = descriptor.findMessageTypeByName("Outer");
        assertNotNull(outerMessage);
        
        Descriptors.Descriptor middleMessage = outerMessage.findNestedTypeByName("Middle");
        assertNotNull(middleMessage);
        
        Descriptors.Descriptor innerMessage = middleMessage.findNestedTypeByName("Inner");
        assertNotNull(innerMessage);
        
        FieldDescriptor valueField = innerMessage.findFieldByName("value");
        assertNotNull(valueField);
    }
    
    private FileDescriptor parseProto3Schema(String schemaText) throws DescriptorValidationException
    {
        org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromString(schemaText);
        io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf3Lexer lexer = 
            new io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf3Lexer(input);
        org.antlr.v4.runtime.CommonTokenStream tokens = new org.antlr.v4.runtime.CommonTokenStream(lexer);

        io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf3Parser parser = 
            new io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf3Parser(tokens);
        parser.setErrorHandler(new org.antlr.v4.runtime.BailErrorStrategy());
        org.antlr.v4.runtime.tree.ParseTreeWalker walker = new org.antlr.v4.runtime.tree.ParseTreeWalker();

        Proto3Listener listener = new Proto3Listener();
        walker.walk(listener, parser.proto());

        return FileDescriptor.buildFrom(listener.build(), new FileDescriptor[0]);
    }
}
