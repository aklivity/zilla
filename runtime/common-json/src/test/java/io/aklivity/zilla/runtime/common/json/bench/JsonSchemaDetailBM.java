/*
 * Copyright 2021-2026 Aklivity Inc
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

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonDiagnostic;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;
import io.aklivity.zilla.runtime.common.json.JsonReporter;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonViolation;

/**
 * Measures the allocation of detailed schema-violation reporting. Each benchmark drives a lenient
 * validator stage over the same document; the document carries an undeclared member, an out-of-range value, a
 * value outside {@code const}, and omits a required member. {@code violationsPlain} reports no detail, and
 * {@code violationsDetailed} reports all four with the reporter reading the keyword, pointer, member and value of each
 * violation, so under {@code -prof gc} the difference in {@code gc.alloc.rate.norm} between the two is the
 * allocation of detailed reporting itself and is expected to be zero. {@code validPlain} and
 * {@code validDetailed} drive a conforming document, where the option is expected to add nothing either.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonSchemaDetailBM
{
    private static final String SCHEMA =
        "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"level\":{\"maximum\":2}," +
        "\"kind\":{\"const\":\"a\"},\"name\":{\"type\":\"string\"}},\"required\":[\"id\",\"name\"]," +
        "\"additionalProperties\":false}";

    private static final String VIOLATING = "{\"id\":1,\"extra\":\"x\",\"level\":3,\"kind\":\"b\"} ";
    private static final String VALID = "{\"id\":1,\"level\":2,\"kind\":\"a\",\"name\":\"n\"} ";

    private final MutableDirectBufferEx outputBuffer = new UnsafeBufferEx(new byte[1024]);
    private final JsonSchema schema = JsonSchema.of(SCHEMA);

    private long reported;

    private JsonPipeline violationsPlainPipeline;
    private JsonPipeline violationsDetailedPipeline;
    private JsonPipeline validPlainPipeline;
    private JsonPipeline validDetailedPipeline;

    private UnsafeBufferEx violatingBuffer;
    private UnsafeBufferEx validBuffer;
    private int violatingLength;
    private int validLength;

    @Setup(Level.Trial)
    public void init()
    {
        byte[] violating = VIOLATING.getBytes(UTF_8);
        byte[] valid = VALID.getBytes(UTF_8);
        violatingBuffer = new UnsafeBufferEx(violating);
        validBuffer = new UnsafeBufferEx(valid);
        violatingLength = violating.length;
        validLength = valid.length;

        JsonReporter detail = new JsonReporter()
        {
            @Override
            public void rejected(
                JsonDiagnostic diagnostic)
            {
            }

            @Override
            public void violated(
                JsonViolation violation)
            {
                JsonSource value = violation.value();
                reported += violation.keyword().length() + violation.pointer().length() +
                    (violation.member() != null ? violation.member().length() : 0) +
                    (value != null ? value.getStringView().length() : 0);
            }
        };
        JsonReporter plain = diagnostic -> {};

        violationsPlainPipeline = pipeline(plain, false);
        violationsDetailedPipeline = pipeline(detail, true);
        validPlainPipeline = pipeline(plain, false);
        validDetailedPipeline = pipeline(detail, true);
    }

    @Benchmark
    public long violationsPlain()
    {
        return run(violationsPlainPipeline, violatingBuffer, violatingLength);
    }

    @Benchmark
    public long violationsDetailed()
    {
        return run(violationsDetailedPipeline, violatingBuffer, violatingLength);
    }

    @Benchmark
    public long validPlain()
    {
        return run(validPlainPipeline, validBuffer, validLength);
    }

    @Benchmark
    public long validDetailed()
    {
        return run(validDetailedPipeline, validBuffer, validLength);
    }

    private JsonPipeline pipeline(
        JsonReporter reporter,
        boolean detailed)
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        return JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator(true))
            .lenient(true)
            .detailed(detailed)
            .reporting(reporter)
            .into(generator);
    }

    private long run(
        JsonPipeline pipeline,
        UnsafeBufferEx buffer,
        int length)
    {
        pipeline.reset();
        JsonPipelineResult result = pipeline.transform(buffer, 0, length, true, outputBuffer, 0, outputBuffer.capacity());
        return result.produced() + reported;
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonSchemaDetailBM.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
