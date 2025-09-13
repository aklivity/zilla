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

import org.junit.Test;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;
import io.aklivity.zilla.runtime.model.protobuf.internal.ProtobufModelContext;

public class Proto2ModelTest
{
    @Test
    public void shouldParseProto2WithRequiredFields() throws Exception
    {
        String proto2Schema = """
            syntax = "proto2";
            
            package test;
            
            message Person {
              required string name = 1;
              required int32 id = 2;
              optional string email = 3;
              repeated string phone = 4;
            }
            """;

        FileDescriptor descriptor = parseProto2Schema(proto2Schema);
        
        assertNotNull(descriptor);
        assertEquals("proto2", descriptor.getSyntax());
        assertEquals("test", descriptor.getPackage());
        
        Descriptors.Descriptor personMessage = descriptor.findMessageTypeByName("Person");
        assertNotNull(personMessage);
        
        // Verify field labels
        FieldDescriptor nameField = personMessage.findFieldByName("name");
        assertNotNull(nameField);
        assertEquals(Label.LABEL_REQUIRED, nameField.toProto().getLabel());
        
        FieldDescriptor idField = personMessage.findFieldByName("id");
        assertNotNull(idField);
        assertEquals(Label.LABEL_REQUIRED, idField.toProto().getLabel());
        
        FieldDescriptor emailField = personMessage.findFieldByName("email");
        assertNotNull(emailField);
        assertEquals(Label.LABEL_OPTIONAL, emailField.toProto().getLabel());
        
        FieldDescriptor phoneField = personMessage.findFieldByName("phone");
        assertNotNull(phoneField);
        assertEquals(Label.LABEL_REPEATED, phoneField.toProto().getLabel());
    }
    
    @Test
    public void shouldParseProto2WithDefaultValues() throws Exception
    {
        String proto2Schema = """
            syntax = "proto2";
            
            message Settings {
              optional int32 port = 1 [default = 8080];
              optional string host = 2 [default = "localhost"];
              optional bool enabled = 3 [default = true];
              optional float timeout = 4 [default = 30.5];
            }
            """;

        FileDescriptor descriptor = parseProto2Schema(proto2Schema);
        
        assertNotNull(descriptor);
        
        Descriptors.Descriptor settingsMessage = descriptor.findMessageTypeByName("Settings");
        assertNotNull(settingsMessage);
        
        // Verify default values
        FieldDescriptor portField = settingsMessage.findFieldByName("port");
        assertNotNull(portField);
        assertEquals("8080", portField.toProto().getDefaultValue());
        
        FieldDescriptor hostField = settingsMessage.findFieldByName("host");
        assertNotNull(hostField);
        assertEquals("localhost", hostField.toProto().getDefaultValue());
        
        FieldDescriptor enabledField = settingsMessage.findFieldByName("enabled");
        assertNotNull(enabledField);
        assertEquals("true", enabledField.toProto().getDefaultValue());
        
        FieldDescriptor timeoutField = settingsMessage.findFieldByName("timeout");
        assertNotNull(timeoutField);
        assertEquals("30.5", timeoutField.toProto().getDefaultValue());
    }
    
    @Test
    public void shouldParseProto2WithGroups() throws Exception
    {
        String proto2Schema = """
            syntax = "proto2";
            
            message Result {
              required int32 id = 1;
              optional group Data = 2 {
                optional string value = 3;
                optional int32 count = 4;
              }
            }
            """;

        FileDescriptor descriptor = parseProto2Schema(proto2Schema);
        
        assertNotNull(descriptor);
        
        Descriptors.Descriptor resultMessage = descriptor.findMessageTypeByName("Result");
        assertNotNull(resultMessage);
        
        // Verify group field
        FieldDescriptor dataField = resultMessage.findFieldByName("data");
        assertNotNull(dataField);
        assertEquals(FieldDescriptor.Type.GROUP, dataField.getType());
        assertEquals("Data", dataField.toProto().getTypeName());
    }
    
    @Test
    public void shouldParseProto2WithExtensions() throws Exception
    {
        String proto2Schema = """
            syntax = "proto2";
            
            message Base {
              required int32 id = 1;
              extensions 100 to 199;
            }
            
            extend Base {
              optional string extra_field = 100;
            }
            """;

        FileDescriptor descriptor = parseProto2Schema(proto2Schema);
        
        assertNotNull(descriptor);
        
        Descriptors.Descriptor baseMessage = descriptor.findMessageTypeByName("Base");
        assertNotNull(baseMessage);
        assertEquals(1, baseMessage.getExtensionRanges().size());
        
        // Verify extension range
        Descriptors.Descriptor.ExtensionRange range = baseMessage.getExtensionRanges().get(0);
        assertEquals(100, range.getStart());
        assertEquals(200, range.getEnd()); // End is exclusive
    }
    

    
    private FileDescriptor parseProto2Schema(String schemaText) throws DescriptorValidationException
    {
        org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromString(schemaText);
        io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf2Lexer lexer = 
            new io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf2Lexer(input);
        org.antlr.v4.runtime.CommonTokenStream tokens = new org.antlr.v4.runtime.CommonTokenStream(lexer);

        io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf2Parser parser = 
            new io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf2Parser(tokens);
        parser.setErrorHandler(new org.antlr.v4.runtime.BailErrorStrategy());
        org.antlr.v4.runtime.tree.ParseTreeWalker walker = new org.antlr.v4.runtime.tree.ParseTreeWalker();

        Proto2Listener listener = new Proto2Listener();
        walker.walk(listener, parser.proto());

        return FileDescriptor.buildFrom(listener.build(), new FileDescriptor[0]);
    }
}
