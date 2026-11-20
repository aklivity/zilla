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
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.agrona.io.DirectBufferInputStreamEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSchema.Draft;

// validates an object whose every property refers to one shared definition, at two sizes, either with a
// validating parser built per document (single use) or one validating parser reset per document
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonSchemaRefBM
{
    private static final int SMALL_PROPERTIES = 8;
    private static final int LARGE_PROPERTIES = 10_000;

    @Param({"DRAFT_07", "DRAFT_2020_12"})
    public String draft;

    private final DirectBufferInputStreamEx inputRO = new DirectBufferInputStreamEx();
    private final JsonParserEx parser = JsonEx.createParser(inputRO);

    private JsonSchema smallSchema;
    private JsonSchema largeSchema;
    private JsonParserEx smallValidator;
    private JsonParserEx largeValidator;

    private UnsafeBufferEx smallBuffer;
    private UnsafeBufferEx largeBuffer;
    private int smallLength;
    private int largeLength;

    @Setup
    public void init()
    {
        final Draft version = Draft.valueOf(draft);
        smallSchema = JsonSchema.of(schema(SMALL_PROPERTIES, version), version);
        largeSchema = JsonSchema.of(schema(LARGE_PROPERTIES, version), version);
        smallValidator = (JsonParserEx) smallSchema.newParser(false, parser);
        largeValidator = (JsonParserEx) largeSchema.newParser(false, parser);

        byte[] smallBytes = instance(SMALL_PROPERTIES).getBytes(UTF_8);
        byte[] largeBytes = instance(LARGE_PROPERTIES).getBytes(UTF_8);
        smallBuffer = new UnsafeBufferEx(smallBytes);
        largeBuffer = new UnsafeBufferEx(largeBytes);
        smallLength = smallBytes.length;
        largeLength = largeBytes.length;
    }

    @Benchmark
    public int validateSmallSingleUse()
    {
        inputRO.wrap(smallBuffer, 0, smallLength);
        parser.reset();
        return drive((JsonParserEx) smallSchema.newParser(false, parser));
    }

    @Benchmark
    public int validateLargeSingleUse()
    {
        inputRO.wrap(largeBuffer, 0, largeLength);
        parser.reset();
        return drive((JsonParserEx) largeSchema.newParser(false, parser));
    }

    @Benchmark
    public int validateSmallReused()
    {
        inputRO.wrap(smallBuffer, 0, smallLength);
        smallValidator.reset();
        return drive(smallValidator);
    }

    @Benchmark
    public int validateLargeReused()
    {
        inputRO.wrap(largeBuffer, 0, largeLength);
        largeValidator.reset();
        return drive(largeValidator);
    }

    private static int drive(
        JsonParserEx validator)
    {
        int events = 0;
        while (validator.hasNext())
        {
            validator.next();
            events++;
        }
        return events;
    }

    private static String schema(
        int properties,
        Draft version)
    {
        final String definitions = version == Draft.DRAFT_07 ? "definitions" : "$defs";
        StringBuilder schema = new StringBuilder("{\"type\":\"object\",\"" + definitions +
            "\":{\"text\":{\"type\":\"string\"}},\"properties\":{");
        for (int index = 0; index < properties; index++)
        {
            schema.append(index == 0 ? "" : ",");
            schema.append("\"k").append(index).append("\":{\"$ref\":\"#/").append(definitions).append("/text\"}");
        }
        return schema.append("}}").toString();
    }

    private static String instance(
        int properties)
    {
        StringBuilder instance = new StringBuilder("{");
        for (int index = 0; index < properties; index++)
        {
            instance.append(index == 0 ? "" : ",");
            instance.append("\"k").append(index).append("\":\"v\"");
        }
        return instance.append("} ").toString();
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonSchemaRefBM.class.getSimpleName())
            .forks(0)
            .build();

        new Runner(opt).run();
    }
}
