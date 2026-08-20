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

import java.util.List;
import java.util.Map;

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
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSink.Delivery;
import io.aklivity.zilla.runtime.common.json.JsonTransforms;

/**
 * Compares the {@code common-json} streaming pipeline, parser through generator, under structured
 * delivery (the kept subtree is re-rendered token-by-token and normalized) versus segmented delivery
 * (the kept subtree is copied verbatim via {@code writeRaw}). Each segmented benchmark is paired with
 * a structured benchmark over the same input and projection so throughput and (under {@code -prof gc})
 * allocation can be compared directly. The scalar-leaf case has no segmented counterpart because a
 * scalar value is never segmented — it is the control where the two modes coincide.
 * <p>
 * The {@code validate*} benchmarks drive {@code JsonSchemaImpl.Validator} (the push-based
 * {@code JsonTransform} every {@code JsonPipeline} consumer gets from {@code schema.validator()}) rather
 * than a projector, so allocation regressions in {@code Validator}/{@code Eval}'s fastKeys path are
 * caught here directly instead of only downstream in a consumer module's own benchmarks.
 * {@code validateCanonical}/{@code validateVerbatim} use a schema with no declared {@code properties},
 * so every key falls through to the default (never-fails) {@code additionalProperties}.
 * {@code validatePropertiesCanonical}/{@code validatePropertiesVerbatim} add declared {@code properties}
 * and {@code required}, exercising fastKeys' matched-property path (expected ~0 B/op: the pointer segment
 * reuses the schema's own property name). {@code validateAdditionalCanonical}/
 * {@code validateAdditionalVerbatim} route an undeclared key to a real (fallible) {@code
 * additionalProperties} sub-schema — the one case fastKeys still has to materialize the document's own
 * key text, since the schema has no name of its own to reuse for it.
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

    // a schema-valid document carrying insignificant whitespace, so canonical re-render and verbatim copy
    // diverge: re-render normalizes the spacing (and re-quotes/re-lexes each value), verbatim splices bytes
    private static final String VALIDATE_DOCUMENT = ROOT_IDENTITY;
    private static final String VALIDATE_SCHEMA = "{\"type\":\"object\"}";

    // unlike VALIDATE_SCHEMA (bare "type":"object", no declared properties), this exercises Eval's
    // fastKeys "matched declared property" branch: "id" and "ok" resolve against propertyKeys/requiredKeys
    // (array scans, the schema's own strings pushed onto the pointer trail — see onFastKey), while "items"
    // falls through to the default additionalProperties:true (ANY, never fails, no pointer push at all)
    private static final String VALIDATE_PROPERTIES_SCHEMA =
        "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"ok\":{\"type\":\"boolean\"}}," +
        "\"required\":[\"id\",\"ok\"]}";

    // "note" is not a declared property, and additionalProperties is a real (fallible) sub-schema rather
    // than the bare-true/absent default — the one case fastKeys still has to materialize the document's
    // own key text (not the schema's), since the schema has no name for it to reuse
    private static final String VALIDATE_ADDITIONAL_SCHEMA =
        "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}},\"required\":[\"id\"]," +
        "\"additionalProperties\":{\"type\":\"string\"}}";
    private static final String VALIDATE_ADDITIONAL_DOCUMENT = "{\"id\":1,\"note\":\"hello\"} ";

    private final MutableDirectBufferEx outputBuffer = new UnsafeBufferEx(new byte[16 * 1024]);
    private final JsonGeneratorEx generator = JsonEx.createGenerator();
    // structured = the explicit canonical opt-out (re-render); the bare default now prefers bytes
    private final JsonSink structuredSink = JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, Delivery.STRUCTURED));
    private final JsonSink segmentableSink = JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, Delivery.SEGMENTABLE));
    private final JsonSink bytePreferringSink = JsonEx.createSink(generator);

    private JsonPipeline scalarLeavesPipeline;
    private JsonPipeline keptContainerStructuredPipeline;
    private JsonPipeline keptContainerSegmentedPipeline;
    private JsonPipeline rootIdentityStructuredPipeline;
    private JsonPipeline rootIdentitySegmentedPipeline;
    private JsonPipeline mostlySkippedStructuredPipeline;
    private JsonPipeline mostlySkippedSegmentedPipeline;
    private JsonPipeline fragmentStringStructuredPipeline;
    private JsonPipeline fragmentStringSegmentedPipeline;
    private JsonPipeline fragmentNumberStructuredPipeline;
    private JsonPipeline validateCanonicalPipeline;
    private JsonPipeline validateVerbatimPipeline;
    private JsonPipeline validatePropertiesCanonicalPipeline;
    private JsonPipeline validatePropertiesVerbatimPipeline;
    private JsonPipeline validateAdditionalCanonicalPipeline;
    private JsonPipeline validateAdditionalVerbatimPipeline;

    private UnsafeBufferEx flatBuffer;
    private UnsafeBufferEx nestedBuffer;
    private UnsafeBufferEx rootIdentityBuffer;
    private UnsafeBufferEx mostlySkippedBuffer;
    private UnsafeBufferEx largeStringBuffer;
    private UnsafeBufferEx largeNumberBuffer;
    private UnsafeBufferEx validateBuffer;
    private UnsafeBufferEx validateAdditionalBuffer;

    private int flatLength;
    private int nestedLength;
    private int rootIdentityLength;
    private int mostlySkippedLength;
    private int largeStringLength;
    private int largeNumberLength;
    private int validateLength;
    private int validateAdditionalLength;

    @Setup(Level.Trial)
    public void init()
    {
        scalarLeavesPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonTransforms.projector(List.of("/id", "/active"))).into(structuredSink);

        keptContainerStructuredPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonTransforms.projector(List.of("/meta"))).into(structuredSink);
        keptContainerSegmentedPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonTransforms.projector(List.of("/meta"))).into(segmentableSink);

        rootIdentityStructuredPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonTransforms.projector(List.of(""))).into(structuredSink);
        rootIdentitySegmentedPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonTransforms.projector(List.of(""))).into(segmentableSink);

        mostlySkippedStructuredPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonTransforms.projector(List.of("/keep"))).into(structuredSink);
        mostlySkippedSegmentedPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonTransforms.projector(List.of("/keep"))).into(segmentableSink);

        // no transform: an over-window value fragments and is rendered straight to the sink
        fragmentStringStructuredPipeline = JsonEx.stream(JsonEx.createParser()).into(structuredSink);
        fragmentStringSegmentedPipeline = JsonEx.stream(JsonEx.createParser()).into(segmentableSink);
        fragmentNumberStructuredPipeline = JsonEx.stream(JsonEx.createParser()).into(structuredSink);

        // the validate path: validator forwards unchanged — canonical re-renders each value, verbatim copies
        // the original bytes; same schema and input so the two are directly comparable
        validateCanonicalPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(VALIDATE_SCHEMA).validator()).into(structuredSink);
        validateVerbatimPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(VALIDATE_SCHEMA).validator()).into(bytePreferringSink);
        validatePropertiesCanonicalPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(VALIDATE_PROPERTIES_SCHEMA).validator()).into(structuredSink);
        validatePropertiesVerbatimPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(VALIDATE_PROPERTIES_SCHEMA).validator()).into(bytePreferringSink);
        validateAdditionalCanonicalPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(VALIDATE_ADDITIONAL_SCHEMA).validator()).into(structuredSink);
        validateAdditionalVerbatimPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(VALIDATE_ADDITIONAL_SCHEMA).validator()).into(bytePreferringSink);

        byte[] flatBytes = FLAT_OBJECT.getBytes(UTF_8);
        byte[] nestedBytes = NESTED_OBJECT.getBytes(UTF_8);
        byte[] rootIdentityBytes = ROOT_IDENTITY.getBytes(UTF_8);
        byte[] mostlySkippedBytes = MOSTLY_SKIPPED.getBytes(UTF_8);
        byte[] largeStringBytes = LARGE_STRING.getBytes(UTF_8);
        byte[] largeNumberBytes = LARGE_NUMBER.getBytes(UTF_8);
        byte[] validateBytes = VALIDATE_DOCUMENT.getBytes(UTF_8);
        byte[] validateAdditionalBytes = VALIDATE_ADDITIONAL_DOCUMENT.getBytes(UTF_8);

        flatBuffer = new UnsafeBufferEx(flatBytes);
        nestedBuffer = new UnsafeBufferEx(nestedBytes);
        rootIdentityBuffer = new UnsafeBufferEx(rootIdentityBytes);
        mostlySkippedBuffer = new UnsafeBufferEx(mostlySkippedBytes);
        largeStringBuffer = new UnsafeBufferEx(largeStringBytes);
        largeNumberBuffer = new UnsafeBufferEx(largeNumberBytes);
        validateBuffer = new UnsafeBufferEx(validateBytes);
        validateAdditionalBuffer = new UnsafeBufferEx(validateAdditionalBytes);

        flatLength = flatBytes.length;
        nestedLength = nestedBytes.length;
        rootIdentityLength = rootIdentityBytes.length;
        mostlySkippedLength = mostlySkippedBytes.length;
        largeStringLength = largeStringBytes.length;
        largeNumberLength = largeNumberBytes.length;
        validateLength = validateBytes.length;
        validateAdditionalLength = validateAdditionalBytes.length;
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
    public int fragmentNumberStructured()
    {
        return runWindowed(fragmentNumberStructuredPipeline, largeNumberBuffer, largeNumberLength, FRAGMENT_WINDOW);
    }

    @Benchmark
    public int validateCanonical()
    {
        return run(validateCanonicalPipeline, validateBuffer, validateLength);
    }

    @Benchmark
    public int validateVerbatim()
    {
        return run(validateVerbatimPipeline, validateBuffer, validateLength);
    }

    @Benchmark
    public int validatePropertiesCanonical()
    {
        return run(validatePropertiesCanonicalPipeline, validateBuffer, validateLength);
    }

    @Benchmark
    public int validatePropertiesVerbatim()
    {
        return run(validatePropertiesVerbatimPipeline, validateBuffer, validateLength);
    }

    @Benchmark
    public int validateAdditionalCanonical()
    {
        return run(validateAdditionalCanonicalPipeline, validateAdditionalBuffer, validateAdditionalLength);
    }

    @Benchmark
    public int validateAdditionalVerbatim()
    {
        return run(validateAdditionalVerbatimPipeline, validateAdditionalBuffer, validateAdditionalLength);
    }

    private int run(
        JsonPipeline pipeline,
        UnsafeBufferEx buffer,
        int length)
    {
        generator.wrap(outputBuffer, 0, outputBuffer.capacity());
        pipeline.reset();
        pipeline.transform(buffer, 0, length);
        return generator.length();
    }

    // Feeds an over-window value in fixed window-sized steps, advancing the progress watermark to
    // position() on each STARVED so the trailing partial unit carries into the next window; reuses the
    // field buffer so the fragmenting path's only allocations are the ones under measurement.
    private int runWindowed(
        JsonPipeline pipeline,
        UnsafeBufferEx buffer,
        int length,
        int window)
    {
        generator.wrap(outputBuffer, 0, outputBuffer.capacity());
        pipeline.reset();
        int progress = 0;
        int limit = 0;
        Status status = Status.STARVED;
        while (limit < length)
        {
            limit = Math.min(limit + window, length);
            boolean last = limit >= length;
            status = pipeline.transform(buffer, progress, limit, last);
            if (status != Status.STARVED)
            {
                break;
            }
            progress = limit - pipeline.remaining();
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
