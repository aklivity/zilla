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
package io.aklivity.zilla.runtime.common.protobuf.bench;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.json.ProtobufJson;

/**
 * Compares the two {@code ProtobufJson} bridge directions over the same fully-buffered message — JSON →
 * protobuf wire (a {@code ProtobufJsonParserImpl} feeding the wire sink) and protobuf wire → JSON (the wire
 * parser feeding a {@code ProtobufJsonGeneratorImpl}). Run with {@code -prof gc} to read allocation per op
 * ({@code gc.alloc.rate.norm}, B/op): the bridge adapters reuse all buffers and read scalars/keys through the
 * parser's non-owning char views, so {@code protobufToJson} is ≈ 0 B/op and {@code jsonToProtobuf}'s small
 * residual (≈ 100 B/op) is only {@code float}/{@code double} values, which the JDK can parse only from a
 * {@code String}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class ProtobufJsonPipelineBM
{
    private final MutableDirectBufferEx outputBuffer = new UnsafeBufferEx(new byte[16 * 1024]);

    private final ProtobufSchema schema = newSchema();
    private final ProtobufGenerator wireGenerator = Protobuf.generator();

    private ProtobufPipeline jsonToProtobufPipeline;
    private ProtobufPipeline protobufToJsonPipeline;
    private ProtobufGenerator jsonRenderer;

    private UnsafeBufferEx jsonBuffer;
    private int jsonLength;
    private UnsafeBufferEx wireBuffer;
    private int wireLength;

    @Setup(Level.Trial)
    public void init()
    {
        jsonToProtobufPipeline = Protobuf.stream(ProtobufJson.parser(JsonEx.createParser(), schema, "Record"))
            .into(ProtobufSink.of(wireGenerator, schema, "Record"));

        jsonRenderer = ProtobufJson.generator(JsonEx.createGenerator(), schema, "Record");
        protobufToJsonPipeline = Protobuf.stream(Protobuf.parser(schema, "Record"))
            .into(ProtobufSink.of(jsonRenderer, schema, "Record"));

        String json = "{" +
            "\"id\":42," +
            "\"name\":\"zilla\"," +
            "\"active\":true," +
            "\"score\":1.5," +
            "\"tags\":[\"a\",\"b\"]," +
            "\"meta\":{\"source\":\"sensor\",\"seq\":\"7\"}" +
            "}";
        byte[] jsonBytes = json.getBytes(UTF_8);
        jsonBuffer = new UnsafeBufferEx(jsonBytes);
        jsonLength = jsonBytes.length;

        UnsafeBufferEx wire = new UnsafeBufferEx(new byte[1024]);
        ProtobufGenerator generator = Protobuf.generator().wrap(wire, 0, wire.capacity());
        generator.writeInt32(1, 42).writeString(2, "zilla").writeBool(3, true).writeDouble(4, 1.5)
            .writeString(5, "a").writeString(5, "b")
            .startMessage(6, 32).writeString(1, "sensor").writeInt64(2, 7).endMessage();
        wireLength = generator.length();
        wireBuffer = wire;
    }

    @Benchmark
    public int jsonToProtobuf()
    {
        wireGenerator.wrap(outputBuffer, 0, outputBuffer.capacity());
        jsonToProtobufPipeline.reset();
        jsonToProtobufPipeline.feed(jsonBuffer, 0, jsonLength);
        return wireGenerator.length();
    }

    @Benchmark
    public int protobufToJson()
    {
        jsonRenderer.wrap(outputBuffer, 0, outputBuffer.capacity());
        protobufToJsonPipeline.reset();
        protobufToJsonPipeline.feed(wireBuffer, 0, wireLength);
        jsonRenderer.flush();
        return jsonRenderer.length();
    }

    private static ProtobufSchema newSchema()
    {
        return Protobuf.schema()
            .message(ProtobufMessage.builder("Meta")
                .field(ProtobufField.builder().number(1).name("source").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("seq").type(ProtobufType.INT64).build())
                .build())
            .message(ProtobufMessage.builder("Record")
                .field(ProtobufField.builder().number(1).name("id").type(ProtobufType.INT32).build())
                .field(ProtobufField.builder().number(2).name("name").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(3).name("active").type(ProtobufType.BOOL).build())
                .field(ProtobufField.builder().number(4).name("score").type(ProtobufType.DOUBLE).build())
                .field(ProtobufField.builder().number(5).name("tags").type(ProtobufType.STRING).repeated(true).build())
                .field(ProtobufField.builder().number(6).name("meta").type(ProtobufType.MESSAGE).typeName("Meta").build())
                .build())
            .build();
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(ProtobufJsonPipelineBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
