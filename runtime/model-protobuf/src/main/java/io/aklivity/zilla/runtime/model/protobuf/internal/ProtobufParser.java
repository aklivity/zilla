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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.LangUtil;
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

    private static final Pattern SYNTAX_PATTERN = Pattern.compile(
            "^\\s*syntax\\s*=\\s*[\"'](proto2|proto3)[\"']\\s*;",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    private static final String PROTO2_SYNTAX = "proto2";
    private static final String PROTO3_SYNTAX = "proto3";

    public enum ProtoSyntax
    {
        PROTO2,
        PROTO3,
        UNKNOWN
    }

    public ProtobufParser(
            FileDescriptor[] dependencies)
    {
        this.dependencies = dependencies != null ? dependencies : new FileDescriptor[0];
    }

    public FileDescriptor parse(
            String schemaText)
    {
        FileDescriptor descriptor = null;
        if (schemaText != null || schemaText.isEmpty())
        {
            // Detect syntax version from schema content
            ProtoSyntax syntax = detectSyntax(schemaText);

            // Create CharStream for parsing
            CharStream input = CharStreams.fromString(schemaText);

            // Dispatch to appropriate parser - no defaults
            descriptor = switch (syntax)
            {
            case PROTO2 -> parseProto2(input);
            case PROTO3 -> parseProto3(input);
            default -> descriptor;
            };
        }
        return descriptor;
    }

    private FileDescriptor parseProto2(
            CharStream input)
    {
        FileDescriptor descriptor = null;
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
            LangUtil.rethrowUnchecked(ex);
        }

        return descriptor;

    }

    private FileDescriptor parseProto3(
            CharStream input)
    {
        FileDescriptor descriptor = null;
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
            LangUtil.rethrowUnchecked(ex);
        }
        return descriptor;
    }

    public static ProtoSyntax detectSyntax(
            String schemaText)
    {
        if (schemaText == null || schemaText.isEmpty())
        {
            return ProtoSyntax.UNKNOWN;
        }

        Matcher matcher = SYNTAX_PATTERN.matcher(schemaText);
        return matcher.find() ? parseSyntax(matcher.group(1).toLowerCase()) : ProtoSyntax.UNKNOWN;
    }

    private static ProtoSyntax parseSyntax(
            String syntax)
    {
        if (syntax == null)
        {
            return ProtoSyntax.UNKNOWN;
        }

        String lowerSyntax = syntax.toLowerCase();
        if (PROTO2_SYNTAX.equals(lowerSyntax))
        {
            return ProtoSyntax.PROTO2;
        }
        else if (PROTO3_SYNTAX.equals(lowerSyntax))
        {
            return ProtoSyntax.PROTO3;
        }

        return ProtoSyntax.UNKNOWN;
    }
}
