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
package io.aklivity.zilla.runtime.common.json.bench;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.List;
import java.util.Map;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
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
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSink.Delivery;

/**
 * Compares the {@code common-json} streaming pipeline, parser through generator, under structured
 * delivery (the kept subtree is re-rendered token-by-token and normalized) versus segmented delivery
 * (the kept subtree is copied verbatim via {@code writeRaw}). Each segmented benchmark is paired with
 * a structured benchmark over the same input and projection so throughput and (under {@code -prof gc})
 * allocation can be compared directly. The scalar-leaf case has no segmented counterpart because a
 * scalar value is never segmented — it is the control where the two modes coincide.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonPipelineBM
{
    private static final String FLAT_OBJECT =
        "{\"id\":42,\"name\":\"zilla\",\"active\":true,\"secret\":\"drop\",\"version\":1} ";

    private static final String NESTED_OBJECT =
        "{\"meta\":{\"id\":7,\"source\":\"sensor\",\"trace\":\"keep-me\",\"tags\":[\"a\",\"b\",\"c\"]}," +
        "\"body\":{\"payload\":\"large\",\"headers\":{\"a\":1,\"b\":2}},\"ignored\":true} ";

    private static final String ROOT_IDENTITY =
        "{ \"id\" : 42, \"items\" : [ { \"id\" : 1, \"name\" : \"a\" }, " +
        "{ \"id\" : 2, \"name\" : \"b\" } ], \"ok\" : true } ";

    private static final String MOSTLY_SKIPPED =
        "{\"drop0\":[{\"a\":1,\"b\":2,\"c\":3},{\"a\":4,\"b\":5,\"c\":6}," +
        "{\"a\":7,\"b\":8,\"c\":9}],\"drop1\":{\"nested\":{\"x\":1,\"y\":2,\"z\":[1,2,3,4]}}," +
        "\"keep\":{\"id\":99,\"name\":\"retain\",\"extra\":\"more-text-here\",\"nested\":{\"p\":1,\"q\":2}}," +
        "\"drop2\":[0,1,2,3,4,5,6,7,8,9],\"drop3\":{\"a\":\"b\",\"c\":\"d\"}} ";

    // values far larger than FRAGMENT_WINDOW so windowed feeding forces the fragmenting path
    private static final String LARGE_STRING = "{\"data\":\"" + "x".repeat(512) + "\"}";
    private static final String LARGE_NUMBER = "{\"data\":" + "1".repeat(512) + "}";

    private static final int FRAGMENT_WINDOW = 64;

    private final MutableDirectBuffer outputBuffer = new UnsafeBuffer(new byte[16 * 1024]);
    private final JsonGeneratorEx generator = JsonEx.createGenerator();
    private final JsonSink structuredSink = JsonEx.createSink(generator);
    private final JsonSink segmentableSink = JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, Delivery.SEGMENTABLE));
    private final JsonSink decodedSink = JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, Delivery.DECODED));

    private JsonPipeline scalarLeavesPipeline;
    private JsonPipeline keptContainerStructuredPipeline;
    private JsonPipeline keptContainerSegmentedPipeline;
    private JsonPipeline rootIdentityStructuredPipeline;
    private JsonPipeline rootIdentitySegmentedPipeline;
    private JsonPipeline mostlySkippedStructuredPipeline;
    private JsonPipeline mostlySkippedSegmentedPipeline;
    private JsonPipeline fragmentStringStructuredPipeline;
    private JsonPipeline fragmentStringSegmentedPipeline;
    private JsonPipeline fragmentStringDecodedPipeline;
    private JsonPipeline fragmentNumberDecodedPipeline;

    private UnsafeBuffer flatBuffer;
    private UnsafeBuffer nestedBuffer;
    private UnsafeBuffer rootIdentityBuffer;
    private UnsafeBuffer mostlySkippedBuffer;
    private UnsafeBuffer largeStringBuffer;
    private UnsafeBuffer largeNumberBuffer;

    private int flatLength;
    private int nestedLength;
    private int rootIdentityLength;
    private int mostlySkippedLength;
    private int largeStringLength;
    private int largeNumberLength;

    @Setup(Level.Trial)
    public void init()
    {
        scalarLeavesPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/id", "/active"))).into(structuredSink);

        keptContainerStructuredPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/meta"))).into(structuredSink);
        keptContainerSegmentedPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/meta"))).into(segmentableSink);

        rootIdentityStructuredPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of(""))).into(structuredSink);
        rootIdentitySegmentedPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of(""))).into(segmentableSink);

        mostlySkippedStructuredPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/keep"))).into(structuredSink);
        mostlySkippedSegmentedPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/keep"))).into(segmentableSink);

        // no transform: an over-window value fragments and is rendered straight to the sink
        fragmentStringStructuredPipeline = JsonEx.stream(JsonEx.createParser()).into(structuredSink);
        fragmentStringSegmentedPipeline = JsonEx.stream(JsonEx.createParser()).into(segmentableSink);
        fragmentStringDecodedPipeline = JsonEx.stream(JsonEx.createParser()).into(decodedSink);
        fragmentNumberDecodedPipeline = JsonEx.stream(JsonEx.createParser()).into(decodedSink);

        byte[] flatBytes = FLAT_OBJECT.getBytes(UTF_8);
        byte[] nestedBytes = NESTED_OBJECT.getBytes(UTF_8);
        byte[] rootIdentityBytes = ROOT_IDENTITY.getBytes(UTF_8);
        byte[] mostlySkippedBytes = MOSTLY_SKIPPED.getBytes(UTF_8);
        byte[] largeStringBytes = LARGE_STRING.getBytes(UTF_8);
        byte[] largeNumberBytes = LARGE_NUMBER.getBytes(UTF_8);

        flatBuffer = new UnsafeBuffer(flatBytes);
        nestedBuffer = new UnsafeBuffer(nestedBytes);
        rootIdentityBuffer = new UnsafeBuffer(rootIdentityBytes);
        mostlySkippedBuffer = new UnsafeBuffer(mostlySkippedBytes);
        largeStringBuffer = new UnsafeBuffer(largeStringBytes);
        largeNumberBuffer = new UnsafeBuffer(largeNumberBytes);

        flatLength = flatBytes.length;
        nestedLength = nestedBytes.length;
        rootIdentityLength = rootIdentityBytes.length;
        mostlySkippedLength = mostlySkippedBytes.length;
        largeStringLength = largeStringBytes.length;
        largeNumberLength = largeNumberBytes.length;
    }

    @Benchmark
    public int projectScalarLeaves()
    {
        return run(scalarLeavesPipeline, flatBuffer, flatLength);
    }

    @Benchmark
    public int keptContainerStructured()
    {
        return run(keptContainerStructuredPipeline, nestedBuffer, nestedLength);
    }

    @Benchmark
    public int keptContainerSegmented()
    {
        return run(keptContainerSegmentedPipeline, nestedBuffer, nestedLength);
    }

    @Benchmark
    public int rootIdentityStructured()
    {
        return run(rootIdentityStructuredPipeline, rootIdentityBuffer, rootIdentityLength);
    }

    @Benchmark
    public int rootIdentitySegmented()
    {
        return run(rootIdentitySegmentedPipeline, rootIdentityBuffer, rootIdentityLength);
    }

    @Benchmark
    public int mostlySkippedStructured()
    {
        return run(mostlySkippedStructuredPipeline, mostlySkippedBuffer, mostlySkippedLength);
    }

    @Benchmark
    public int mostlySkippedSegmented()
    {
        return run(mostlySkippedSegmentedPipeline, mostlySkippedBuffer, mostlySkippedLength);
    }

    @Benchmark
    public int fragmentStringSegmented()
    {
        return runWindowed(fragmentStringSegmentedPipeline, largeStringBuffer, largeStringLength, FRAGMENT_WINDOW);
    }

    @Benchmark
    public int fragmentStringStructured()
    {
        return runWindowed(fragmentStringStructuredPipeline, largeStringBuffer, largeStringLength, FRAGMENT_WINDOW);
    }

    @Benchmark
    public int fragmentStringDecoded()
    {
        return runWindowed(fragmentStringDecodedPipeline, largeStringBuffer, largeStringLength, FRAGMENT_WINDOW);
    }

    @Benchmark
    public int fragmentNumberDecoded()
    {
        return runWindowed(fragmentNumberDecodedPipeline, largeNumberBuffer, largeNumberLength, FRAGMENT_WINDOW);
    }

    private int run(
        JsonPipeline pipeline,
        UnsafeBuffer buffer,
        int length)
    {
        generator.wrap(outputBuffer, 0, outputBuffer.capacity());
        pipeline.reset();
        pipeline.feed(buffer, 0, length);
        return generator.length();
    }

    // Feeds an over-window value in fixed window-sized steps, advancing the committed watermark to
    // position() on each STARVED so the trailing partial unit carries into the next window; reuses the
    // field buffer so the fragmenting path's only allocations are the ones under measurement.
    private int runWindowed(
        JsonPipeline pipeline,
        UnsafeBuffer buffer,
        int length,
        int window)
    {
        generator.wrap(outputBuffer, 0, outputBuffer.capacity());
        pipeline.reset();
        int committed = 0;
        int offset = 0;
        Status status = Status.STARVED;
        while (offset < length)
        {
            offset = Math.min(offset + window, length);
            boolean last = offset >= length;
            status = pipeline.feed(buffer, committed, offset - committed, last);
            if (status != Status.STARVED)
            {
                break;
            }
            committed = (int) pipeline.position();
        }
        return status == Status.COMPLETED ? generator.length() : -1;
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonPipelineBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
