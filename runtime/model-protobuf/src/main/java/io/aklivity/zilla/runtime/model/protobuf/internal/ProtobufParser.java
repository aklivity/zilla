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

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;

import io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf2Lexer;
import io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf2Parser;
import io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf3Lexer;
import io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf3Parser;

public final class ProtobufParser
{
    private final FileDescriptor[] dependencies;

    public ProtobufParser(FileDescriptor[] dependencies)
    {
        this.dependencies = dependencies != null ? dependencies : new FileDescriptor[0];
    }

    public FileDescriptor parse(String schemaText)
    {
        if (schemaText == null || schemaText.isEmpty())
        {
            throw new ProtobufParseException("Schema text cannot be null or empty");
        }

        // Detect syntax version from schema content
        ProtobufSyntaxDetector.ProtoSyntax syntax = ProtobufSyntaxDetector.detectSyntax(schemaText);

        // Create CharStream for parsing
        CharStream input = CharStreams.fromString(schemaText);

        // Dispatch to appropriate parser - no defaults
        switch (syntax)
        {
            case PROTO2:
                return parseProto2(input);

            case PROTO3:
                return parseProto3(input);

            case UNKNOWN:
            default:
                throw new ProtobufParseException(
                        "Unable to determine protobuf syntax version. " +
                                "Schema must include an explicit syntax declaration: " +
                                "syntax = \"proto2\"; or syntax = \"proto3\";");
        }
    }

    private FileDescriptor parseProto2(CharStream input)
    {
        try
        {
            // Create lexer and token stream
            Protobuf2Lexer lexer = new Protobuf2Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);

            // Create parser with error handling
            Protobuf2Parser parser = new Protobuf2Parser(tokens);
            parser.setErrorHandler(new BailErrorStrategy());

            // Walk the parse tree with listener
            ParseTreeWalker walker = new ParseTreeWalker();
            Proto2Listener listener = new Proto2Listener();
            walker.walk(listener, parser.proto());

            // Build and return the FileDescriptor
            return FileDescriptor.buildFrom(listener.build(), dependencies);
        }
        catch (DescriptorValidationException ex)
        {
            throw new ProtobufParseException("Failed to parse Proto2 schema", ex);
        }
        catch (Exception ex)
        {
            throw new ProtobufParseException("Unexpected error parsing Proto2 schema", ex);
        }
    }

    private FileDescriptor parseProto3(CharStream input)
    {
        try
        {
            // Create lexer and token stream
            Protobuf3Lexer lexer = new Protobuf3Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);

            // Create parser with error handling
            Protobuf3Parser parser = new Protobuf3Parser(tokens);
            parser.setErrorHandler(new BailErrorStrategy());

            // Walk the parse tree with listener
            ParseTreeWalker walker = new ParseTreeWalker();
            Proto3Listener listener = new Proto3Listener();
            walker.walk(listener, parser.proto());

            // Build and return the FileDescriptor
            return FileDescriptor.buildFrom(listener.build(), dependencies);
        }
        catch (DescriptorValidationException ex)
        {
            throw new ProtobufParseException("Failed to parse Proto3 schema", ex);
        }
        catch (Exception ex)
        {
            throw new ProtobufParseException("Unexpected error parsing Proto3 schema", ex);
        }
    }

    public static class ProtobufParseException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        public ProtobufParseException(String message)
        {
            super(message);
        }

        public ProtobufParseException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }
}
