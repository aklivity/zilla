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

import java.util.function.Consumer;

import org.agrona.ExpandableArrayBuffer;
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

import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufCanonicalizer;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufDiscardSinkImpl;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufWriter;

/**
 * Compares the {@code common-protobuf} hot paths over the same fully-buffered message: descriptor-
 * driven streaming validation, canonical re-encode, schema transformation (read one schema, write
 * another via the wire sink), and schema-free structural copy. Run with {@code -prof gc} to compare
 * allocation per op across the paths.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class ProtobufPipelineBM
{
    private final MutableDirectBufferEx outputBuffer = new UnsafeBufferEx(new byte[16 * 1024]);

    private final ProtobufSchema schema = newSchema();
    private final ProtobufGenerator generator = Protobuf.generator();
    private final ProtobufCanonicalizer canonicalizer = new ProtobufCanonicalizer(schema);

    private ProtobufPipeline validatePipeline;
    private ProtobufPipeline transformPipeline;
    private ProtobufPipeline rawCopyPipeline;

    private UnsafeBufferEx flatBuffer;
    private UnsafeBufferEx nestedBuffer;
    private int flatLength;
    private int nestedLength;

    @Setup(Level.Trial)
    public void init()
    {
        validatePipeline = Protobuf.stream(Protobuf.parser(schema, "Record"))
            .transform(schema.validator("Record"))
            .into(new ProtobufDiscardSinkImpl());

        transformPipeline = Protobuf.stream(Protobuf.parser(schema, "Record"))
            .into(ProtobufSink.of(generator, schema, "RecordV2"));

        rawCopyPipeline = Protobuf.stream(Protobuf.parser())
            .into(ProtobufSink.of(generator));

        byte[] meta = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("sensor".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(7);
        });
        byte[] flat = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeVarint64(42);
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes("zilla".getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(1);
            w.writeTag(4, ProtobufWireType.I64);
            w.writeFixed64(Double.doubleToLongBits(1.5));
        });
        byte[] nested = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeVarint64(42);
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes("zilla".getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(1);
            w.writeTag(4, ProtobufWireType.I64);
            w.writeFixed64(Double.doubleToLongBits(1.5));
            w.writeTag(5, ProtobufWireType.LEN);
            w.writeBytes("a".getBytes(UTF_8));
            w.writeTag(5, ProtobufWireType.LEN);
            w.writeBytes("b".getBytes(UTF_8));
            w.writeTag(6, ProtobufWireType.LEN);
            w.writeBytes(meta);
        });

        flatBuffer = new UnsafeBufferEx(flat);
        nestedBuffer = new UnsafeBufferEx(nested);
        flatLength = flat.length;
        nestedLength = nested.length;
    }

    @Benchmark
    public int validateFlat()
    {
        return runValidate(validatePipeline, flatBuffer, flatLength);
    }

    @Benchmark
    public int validateNested()
    {
        return runValidate(validatePipeline, nestedBuffer, nestedLength);
    }

    @Benchmark
    public int canonicalizeNested()
    {
        return canonicalizer.canonicalize("Record", nestedBuffer, 0, nestedLength, outputBuffer, 0);
    }

    @Benchmark
    public int transformNested()
    {
        return runWrite(transformPipeline, nestedBuffer, nestedLength);
    }

    @Benchmark
    public int rawCopyNested()
    {
        return runWrite(rawCopyPipeline, nestedBuffer, nestedLength);
    }

    private int runValidate(
        ProtobufPipeline pipeline,
        UnsafeBufferEx buffer,
        int length)
    {
        pipeline.reset();
        return pipeline.transform(buffer, 0, length).ordinal();
    }

    private int runWrite(
        ProtobufPipeline pipeline,
        UnsafeBufferEx buffer,
        int length)
    {
        generator.wrap(outputBuffer, 0, outputBuffer.capacity());
        pipeline.reset();
        pipeline.transform(buffer, 0, length);
        return generator.length();
    }

    private static byte[] wire(
        Consumer<ProtobufWriter> body)
    {
        ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
        ProtobufWriter writer = new ProtobufWriter().wrap(buffer, 0);
        body.accept(writer);
        byte[] bytes = new byte[writer.length()];
        buffer.getBytes(0, bytes);
        return bytes;
    }

    private static ProtobufSchema newSchema()
    {
        return Protobuf.schema()
            .message(ProtobufMessage.builder("Meta")
                .field(ProtobufField.builder().number(1).name("source").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("seq").type(ProtobufType.INT64).build())
                .build())
            .message(ProtobufMessage.builder("MetaV2")
                .field(ProtobufField.builder().number(3).name("source").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(4).name("seq").type(ProtobufType.INT64).build())
                .build())
            .message(ProtobufMessage.builder("Record")
                .field(ProtobufField.builder().number(1).name("id").type(ProtobufType.INT32).build())
                .field(ProtobufField.builder().number(2).name("name").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(3).name("active").type(ProtobufType.BOOL).build())
                .field(ProtobufField.builder().number(4).name("score").type(ProtobufType.DOUBLE).build())
                .field(ProtobufField.builder().number(5).name("tags").type(ProtobufType.STRING).repeated(true).build())
                .field(ProtobufField.builder().number(6).name("meta").type(ProtobufType.MESSAGE).typeName("Meta").build())
                .build())
            .message(ProtobufMessage.builder("RecordV2")
                .field(ProtobufField.builder().number(7).name("id").type(ProtobufType.INT32).build())
                .field(ProtobufField.builder().number(8).name("name").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(9).name("meta").type(ProtobufType.MESSAGE).typeName("MetaV2").build())
                .build())
            .build();
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(ProtobufPipelineBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
