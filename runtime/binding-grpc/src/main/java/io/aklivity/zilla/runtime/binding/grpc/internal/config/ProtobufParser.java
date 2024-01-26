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

public final class ProtobufParser
{
    private ProtobufParser()
    {
    }

    public static GrpcProtobufConfig protobufConfig(
        String location,
        String schema)
    {
        CharStream input = CharStreams.fromString(schema);
        Protobuf3Lexer lexer = new Protobuf3Lexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        Protobuf3Parser parser = new Protobuf3Parser(tokens);
        parser.setErrorHandler(new BailErrorStrategy());
        ParseTreeWalker walker = new ParseTreeWalker();
        Set<GrpcServiceConfig> services = new ObjectHashSet<>();
        GrpcServiceDefinitionListener listener = new GrpcServiceDefinitionListener(services);
        walker.walk(listener, parser.proto());

        return new GrpcProtobufConfig(location, services);
    }
}
