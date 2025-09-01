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

import org.junit.Test;

import io.aklivity.zilla.runtime.model.protobuf.internal.ProtobufSyntaxDetector.ProtoSyntax;

public class ProtobufSyntaxDetectorTest
{
    @Test
    public void shouldDetectProto2Syntax()
    {
        String proto2Schema = """
            syntax = "proto2";
            
            message Person {
              required string name = 1;
              optional int32 id = 2;
              repeated string email = 3;
            }
            """;
        
        ProtoSyntax syntax = ProtobufSyntaxDetector.detectSyntax(proto2Schema);
        assertEquals(ProtoSyntax.PROTO2, syntax);
    }
    
    @Test
    public void shouldDetectProto3Syntax()
    {
        String proto3Schema = """
            syntax = "proto3";
            
            message Person {
              string name = 1;
              int32 id = 2;
              repeated string email = 3;
            }
            """;
        
        ProtoSyntax syntax = ProtobufSyntaxDetector.detectSyntax(proto3Schema);
        assertEquals(ProtoSyntax.PROTO3, syntax);
    }
    
    @Test
    public void shouldDetectProto2WithSingleQuotes()
    {
        String proto2Schema = "syntax = 'proto2';\nmessage Test {}";
        
        ProtoSyntax syntax = ProtobufSyntaxDetector.detectSyntax(proto2Schema);
        assertEquals(ProtoSyntax.PROTO2, syntax);
    }
    
    @Test
    public void shouldDetectProto3WithSingleQuotes()
    {
        String proto3Schema = "syntax = 'proto3';\nmessage Test {}";
        
        ProtoSyntax syntax = ProtobufSyntaxDetector.detectSyntax(proto3Schema);
        assertEquals(ProtoSyntax.PROTO3, syntax);
    }
    
    @Test
    public void shouldReturnUnknownWhenNoSyntax()
    {
        String noSyntaxSchema = """
            message Person {
              required string name = 1;
              optional int32 id = 2;
            }
            """;
        
        ProtoSyntax syntax = ProtobufSyntaxDetector.detectSyntax(noSyntaxSchema);
        assertEquals(ProtoSyntax.UNKNOWN, syntax);
    }
    
    @Test
    public void shouldReturnUnknownForNull()
    {
        ProtoSyntax syntax = ProtobufSyntaxDetector.detectSyntax(null);
        assertEquals(ProtoSyntax.UNKNOWN, syntax);
    }
    
    @Test
    public void shouldReturnUnknownForEmptyString()
    {
        ProtoSyntax syntax = ProtobufSyntaxDetector.detectSyntax("");
        assertEquals(ProtoSyntax.UNKNOWN, syntax);
    }
    
    @Test
    public void shouldConvertStringToProto2()
    {
        assertEquals(ProtoSyntax.PROTO2, ProtobufSyntaxDetector.fromString("proto2"));
        assertEquals(ProtoSyntax.PROTO2, ProtobufSyntaxDetector.fromString("Proto2"));
        assertEquals(ProtoSyntax.PROTO2, ProtobufSyntaxDetector.fromString("PROTO2"));
    }
    
    @Test
    public void shouldConvertStringToProto3()
    {
        assertEquals(ProtoSyntax.PROTO3, ProtobufSyntaxDetector.fromString("proto3"));
        assertEquals(ProtoSyntax.PROTO3, ProtobufSyntaxDetector.fromString("Proto3"));
        assertEquals(ProtoSyntax.PROTO3, ProtobufSyntaxDetector.fromString("PROTO3"));
    }
    
    @Test
    public void shouldReturnUnknownForInvalidString()
    {
        assertEquals(ProtoSyntax.UNKNOWN, ProtobufSyntaxDetector.fromString("proto4"));
        assertEquals(ProtoSyntax.UNKNOWN, ProtobufSyntaxDetector.fromString("invalid"));
        assertEquals(ProtoSyntax.UNKNOWN, ProtobufSyntaxDetector.fromString(null));
    }
}
