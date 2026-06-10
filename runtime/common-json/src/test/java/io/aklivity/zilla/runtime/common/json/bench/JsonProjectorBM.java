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

import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.StreamingJson;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonProjectorBM
{
    private static final String FLAT_OBJECT =
        "{\"id\":42,\"name\":\"zilla\",\"active\":true,\"secret\":\"drop\",\"version\":1} ";

    private static final String NESTED_OBJECT =
        "{\"meta\":{\"id\":7,\"source\":\"sensor\",\"trace\":\"drop\"}," +
        "\"body\":{\"payload\":\"large\",\"headers\":{\"a\":1,\"b\":2}},\"ignored\":true} ";

    private static final String ARRAY_WILDCARD =
        "{\"items\":[{\"id\":0,\"name\":\"a\",\"drop\":10},{\"id\":1,\"name\":\"b\",\"drop\":11}," +
        "{\"id\":2,\"name\":\"c\",\"drop\":12},{\"id\":3,\"name\":\"d\",\"drop\":13}],\"cursor\":\"next\"} ";

    private static final String ROOT_IDENTITY =
        "{ \"id\" : 42, \"items\" : [ { \"id\" : 1, \"name\" : \"a\" }, " +
        "{ \"id\" : 2, \"name\" : \"b\" } ], \"ok\" : true } ";

    private static final String MOSTLY_SKIPPED =
        "{\"drop0\":[{\"a\":1,\"b\":2,\"c\":3},{\"a\":4,\"b\":5,\"c\":6}," +
        "{\"a\":7,\"b\":8,\"c\":9}],\"drop1\":{\"nested\":{\"x\":1,\"y\":2,\"z\":[1,2,3,4]}}," +
        "\"keep\":{\"id\":99,\"name\":\"retain\",\"extra\":\"drop\"}," +
        "\"drop2\":[0,1,2,3,4,5,6,7,8,9],\"drop3\":{\"a\":\"b\",\"c\":\"d\"}} ";

    private final MutableDirectBuffer outputBuffer = new UnsafeBuffer(new byte[16 * 1024]);
    private final JsonGeneratorEx generator = StreamingJson.createGenerator();
    private final JsonSink sink = JsonSink.of(generator);

    private JsonPipeline flatPipeline;
    private JsonPipeline nestedPipeline;
    private JsonPipeline arrayWildcardPipeline;
    private JsonPipeline rootIdentityPipeline;
    private JsonPipeline mostlySkippedPipeline;

    private UnsafeBuffer flatBuffer;
    private UnsafeBuffer nestedBuffer;
    private UnsafeBuffer arrayWildcardBuffer;
    private UnsafeBuffer rootIdentityBuffer;
    private UnsafeBuffer mostlySkippedBuffer;

    private int flatLength;
    private int nestedLength;
    private int arrayWildcardLength;
    private int rootIdentityLength;
    private int mostlySkippedLength;

    @Setup(Level.Trial)
    public void init()
    {
        flatPipeline = StreamingJson.createParser().stream()
            .transform(StreamingJson.projector(List.of("/id", "/active"))).into(sink);
        nestedPipeline = StreamingJson.createParser().stream()
            .transform(StreamingJson.projector(List.of("/meta/id", "/meta/source"))).into(sink);
        arrayWildcardPipeline = StreamingJson.createParser().stream()
            .transform(StreamingJson.projector(List.of("/items/-/id"))).into(sink);
        rootIdentityPipeline = StreamingJson.createParser().stream()
            .transform(StreamingJson.projector(List.of(""))).into(sink);
        mostlySkippedPipeline = StreamingJson.createParser().stream()
            .transform(StreamingJson.projector(List.of("/keep/id"))).into(sink);

        byte[] flatBytes = FLAT_OBJECT.getBytes(UTF_8);
        byte[] nestedBytes = NESTED_OBJECT.getBytes(UTF_8);
        byte[] arrayWildcardBytes = ARRAY_WILDCARD.getBytes(UTF_8);
        byte[] rootIdentityBytes = ROOT_IDENTITY.getBytes(UTF_8);
        byte[] mostlySkippedBytes = MOSTLY_SKIPPED.getBytes(UTF_8);

        flatBuffer = new UnsafeBuffer(flatBytes);
        nestedBuffer = new UnsafeBuffer(nestedBytes);
        arrayWildcardBuffer = new UnsafeBuffer(arrayWildcardBytes);
        rootIdentityBuffer = new UnsafeBuffer(rootIdentityBytes);
        mostlySkippedBuffer = new UnsafeBuffer(mostlySkippedBytes);

        flatLength = flatBytes.length;
        nestedLength = nestedBytes.length;
        arrayWildcardLength = arrayWildcardBytes.length;
        rootIdentityLength = rootIdentityBytes.length;
        mostlySkippedLength = mostlySkippedBytes.length;
    }

    @Benchmark
    public int projectFlatObject()
    {
        return project(flatPipeline, flatBuffer, flatLength);
    }

    @Benchmark
    public int projectNestedObject()
    {
        return project(nestedPipeline, nestedBuffer, nestedLength);
    }

    @Benchmark
    public int projectArrayWildcard()
    {
        return project(arrayWildcardPipeline, arrayWildcardBuffer, arrayWildcardLength);
    }

    @Benchmark
    public int projectRootIdentity()
    {
        return project(rootIdentityPipeline, rootIdentityBuffer, rootIdentityLength);
    }

    @Benchmark
    public int projectMostlySkipped()
    {
        return project(mostlySkippedPipeline, mostlySkippedBuffer, mostlySkippedLength);
    }

    private int project(
        JsonPipeline pipeline,
        UnsafeBuffer buffer,
        int length)
    {
        generator.wrap(outputBuffer, 0);
        pipeline.reset();
        pipeline.feed(buffer, 0, length);
        return generator.length();
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonProjectorBM.class.getSimpleName())
            .forks(0)
            .build();

        new Runner(opt).run();
    }
}
