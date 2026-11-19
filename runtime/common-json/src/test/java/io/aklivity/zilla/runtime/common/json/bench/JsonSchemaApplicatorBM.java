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

// validates values under the keywords that apply a schema to part of a value: an object of 100 properties under
// patternProperties and under propertyNames, and an array of 100 elements under contains, with a validating parser
// built per document
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(2)
@Warmup(iterations = 3, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonSchemaApplicatorBM
{
    private static final int COUNT = 100;

    private final DirectBufferInputStreamEx inputRO = new DirectBufferInputStreamEx();
    private final JsonParserEx parser = JsonEx.createParser(inputRO);

    private JsonSchema patternSchema;
    private JsonSchema namesSchema;
    private JsonSchema containsSchema;

    private UnsafeBufferEx objectBuffer;
    private UnsafeBufferEx arrayBuffer;
    private int objectLength;
    private int arrayLength;

    @Setup
    public void init()
    {
        patternSchema = JsonSchema.of("{\"type\":\"object\",\"patternProperties\":" +
            "{\"^k\":{\"type\":\"string\"},\"[0-9]$\":{\"minLength\":1}}}", Draft.DRAFT_07);
        namesSchema = JsonSchema.of("{\"type\":\"object\",\"propertyNames\":" +
            "{\"type\":\"string\",\"pattern\":\"^k[0-9]+$\"}}", Draft.DRAFT_07);
        containsSchema = JsonSchema.of("{\"type\":\"array\",\"contains\":{\"type\":\"string\",\"minLength\":2}," +
            "\"minContains\":1,\"maxContains\":1000}", Draft.DRAFT_07);

        byte[] object = object().getBytes(UTF_8);
        byte[] array = array().getBytes(UTF_8);
        objectBuffer = new UnsafeBufferEx(object);
        arrayBuffer = new UnsafeBufferEx(array);
        objectLength = object.length;
        arrayLength = array.length;
    }

    @Benchmark
    public int validatePatternProperties()
    {
        return validate(patternSchema, objectBuffer, objectLength);
    }

    @Benchmark
    public int validatePropertyNames()
    {
        return validate(namesSchema, objectBuffer, objectLength);
    }

    @Benchmark
    public int validateContains()
    {
        return validate(containsSchema, arrayBuffer, arrayLength);
    }

    private int validate(
        JsonSchema schema,
        UnsafeBufferEx buffer,
        int length)
    {
        inputRO.wrap(buffer, 0, length);
        parser.reset();
        final JsonParserEx validator = (JsonParserEx) schema.newParser(false, parser);
        int events = 0;
        while (validator.hasNext())
        {
            validator.next();
            events++;
        }
        return events;
    }

    private static String object()
    {
        final StringBuilder instance = new StringBuilder("{");
        for (int index = 0; index < COUNT; index++)
        {
            instance.append(index == 0 ? "" : ",").append("\"k").append(index).append("\":\"value\"");
        }
        return instance.append("} ").toString();
    }

    private static String array()
    {
        final StringBuilder instance = new StringBuilder("[");
        for (int index = 0; index < COUNT; index++)
        {
            instance.append(index == 0 ? "" : ",").append(index == COUNT - 1 ? "\"value\"" : "1");
        }
        return instance.append("] ").toString();
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonSchemaApplicatorBM.class.getSimpleName())
            .forks(0)
            .build();

        new Runner(opt).run();
    }
}
