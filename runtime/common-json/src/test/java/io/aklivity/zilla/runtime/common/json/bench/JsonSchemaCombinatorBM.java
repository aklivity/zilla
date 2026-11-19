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

// validates values whose schema is made of combinators: an object of 100 properties each under anyOf, under oneOf and
// under allOf, and an array of 100 objects each under oneOf, with a validating parser built per document
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(2)
@Warmup(iterations = 3, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonSchemaCombinatorBM
{
    private static final int COUNT = 100;
    private static final String ANY_OF = "{\"anyOf\":[{\"type\":\"integer\"},{\"type\":\"string\",\"minLength\":1}]}";
    private static final String ONE_OF =
        "{\"oneOf\":[{\"type\":\"string\",\"maxLength\":3},{\"type\":\"string\",\"minLength\":4}]}";
    private static final String ALL_OF = "{\"allOf\":[{\"type\":\"string\"},{\"minLength\":1},{\"maxLength\":9}]}";
    private static final String ONE_OF_OBJECT =
        "{\"oneOf\":[{\"type\":\"object\",\"required\":[\"id\"],\"properties\":{\"id\":{\"type\":\"string\"}}}," +
            "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}]}";

    private final DirectBufferInputStreamEx inputRO = new DirectBufferInputStreamEx();
    private final JsonParserEx parser = JsonEx.createParser(inputRO);

    private JsonSchema anyOfSchema;
    private JsonSchema oneOfSchema;
    private JsonSchema allOfSchema;
    private JsonSchema arraySchema;

    private UnsafeBufferEx stringsBuffer;
    private UnsafeBufferEx objectsBuffer;
    private int stringsLength;
    private int objectsLength;

    @Setup
    public void init()
    {
        anyOfSchema = JsonSchema.of(properties(ANY_OF), Draft.DRAFT_07);
        oneOfSchema = JsonSchema.of(properties(ONE_OF), Draft.DRAFT_07);
        allOfSchema = JsonSchema.of(properties(ALL_OF), Draft.DRAFT_07);
        arraySchema = JsonSchema.of("{\"type\":\"array\",\"items\":" + ONE_OF_OBJECT + "}", Draft.DRAFT_07);

        byte[] strings = object("\"value\"").getBytes(UTF_8);
        byte[] objects = array().getBytes(UTF_8);
        stringsBuffer = new UnsafeBufferEx(strings);
        objectsBuffer = new UnsafeBufferEx(objects);
        stringsLength = strings.length;
        objectsLength = objects.length;
    }

    @Benchmark
    public int validateAnyOfProperties()
    {
        return validate(anyOfSchema, stringsBuffer, stringsLength);
    }

    @Benchmark
    public int validateOneOfProperties()
    {
        return validate(oneOfSchema, stringsBuffer, stringsLength);
    }

    @Benchmark
    public int validateAllOfProperties()
    {
        return validate(allOfSchema, stringsBuffer, stringsLength);
    }

    @Benchmark
    public int validateOneOfObjects()
    {
        return validate(arraySchema, objectsBuffer, objectsLength);
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

    private static String properties(
        String schema)
    {
        final StringBuilder properties = new StringBuilder("{\"type\":\"object\",\"properties\":{");
        for (int index = 0; index < COUNT; index++)
        {
            properties.append(index == 0 ? "" : ",").append("\"k").append(index).append("\":").append(schema);
        }
        return properties.append("}}").toString();
    }

    private static String object(
        String value)
    {
        final StringBuilder instance = new StringBuilder("{");
        for (int index = 0; index < COUNT; index++)
        {
            instance.append(index == 0 ? "" : ",").append("\"k").append(index).append("\":").append(value);
        }
        return instance.append("} ").toString();
    }

    private static String array()
    {
        final StringBuilder instance = new StringBuilder("[");
        for (int index = 0; index < COUNT; index++)
        {
            instance.append(index == 0 ? "" : ",").append(index % 2 == 0 ? "{\"id\":\"v\"}" : "{\"name\":\"v\"}");
        }
        return instance.append("] ").toString();
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonSchemaCombinatorBM.class.getSimpleName())
            .forks(0)
            .build();

        new Runner(opt).run();
    }
}
