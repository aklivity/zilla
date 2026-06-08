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

import jakarta.json.stream.JsonParser;

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

import io.aklivity.zilla.runtime.common.json.DirectBufferInputStreamEx;
import io.aklivity.zilla.runtime.common.json.JsonProjector;
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

    private final DirectBufferInputStreamEx inputRO = new DirectBufferInputStreamEx();
    private final MutableDirectBuffer outputBuffer = new UnsafeBuffer(new byte[16 * 1024]);

    private JsonProjector flatProjector;
    private JsonProjector nestedProjector;
    private JsonProjector arrayWildcardProjector;
    private JsonProjector rootIdentityProjector;
    private JsonProjector mostlySkippedProjector;

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
        flatProjector = new JsonProjector(List.of("/id", "/active"));
        nestedProjector = new JsonProjector(List.of("/meta/id", "/meta/source"));
        arrayWildcardProjector = new JsonProjector(List.of("/items/-/id"));
        rootIdentityProjector = new JsonProjector(List.of(""));
        mostlySkippedProjector = new JsonProjector(List.of("/keep/id"));

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
        return flatProjector.project(parserFor(flatBuffer, flatLength), outputBuffer, 0);
    }

    @Benchmark
    public int projectNestedObject()
    {
        return nestedProjector.project(parserFor(nestedBuffer, nestedLength), outputBuffer, 0);
    }

    @Benchmark
    public int projectArrayWildcard()
    {
        return arrayWildcardProjector.project(parserFor(arrayWildcardBuffer, arrayWildcardLength), outputBuffer, 0);
    }

    @Benchmark
    public int projectRootIdentity()
    {
        return rootIdentityProjector.project(parserFor(rootIdentityBuffer, rootIdentityLength), outputBuffer, 0);
    }

    @Benchmark
    public int projectMostlySkipped()
    {
        return mostlySkippedProjector.project(parserFor(mostlySkippedBuffer, mostlySkippedLength), outputBuffer, 0);
    }

    private JsonParser parserFor(
        UnsafeBuffer buffer,
        int length)
    {
        inputRO.wrap(buffer, 0, length);
        return StreamingJson.createParser(inputRO);
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
