/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.internal.config;

import java.util.Set;

import org.agrona.collections.ObjectHashSet;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import io.aklivity.zilla.runtime.binding.grpc.config.GrpcProtobufConfig;
import io.aklivity.zilla.runtime.binding.grpc.config.GrpcServiceConfig;
import io.aklivity.zilla.runtime.binding.grpc.internal.parser.Protobuf3Lexer;
import io.aklivity.zilla.runtime.binding.grpc.internal.parser.Protobuf3Parser;

public final class GrpcProtobufParser
{
    private final ParseTreeWalker walker;
    private final BailErrorStrategy errorStrategy;
    private final Protobuf3Lexer lexer;
    private final Protobuf3Parser parser;

    public GrpcProtobufParser()
    {
        this.walker = new ParseTreeWalker();
        this.errorStrategy = new BailErrorStrategy();
        this.lexer = new Protobuf3Lexer(null);
        this.parser = new Protobuf3Parser(null);
        parser.setErrorHandler(errorStrategy);
    }

    public GrpcProtobufConfig parse(
        String location,
        String schema)
    {
        CharStream input = CharStreams.fromString(schema);
        lexer.reset();
        lexer.setInputStream(input);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        parser.setTokenStream(tokens);

        Set<GrpcServiceConfig> services = new ObjectHashSet<>();
        GrpcServiceDefinitionListener listener = new GrpcServiceDefinitionListener(services);
        walker.walk(listener, parser.proto());

        return new GrpcProtobufConfig(location, services);
    }
}
