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

import jakarta.json.stream.JsonParser;

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

import io.aklivity.zilla.runtime.common.json.DirectBufferInputStreamEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonSchema;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonSchemaBM
{
    private static final String FLAT_OBJECT_SCHEMA =
        "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}," +
        "\"name\":{\"type\":\"string\"},\"active\":{\"type\":\"boolean\"}}," +
        "\"required\":[\"id\",\"name\",\"active\"],\"additionalProperties\":false}";

    private static final String ARRAY_OBJECTS_SCHEMA =
        "{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":" +
        "{\"id\":{\"type\":\"integer\"},\"tag\":{\"type\":\"string\"}}," +
        "\"required\":[\"id\",\"tag\"],\"additionalProperties\":false},\"minItems\":8}";

    private static final String ONE_OF_SCHEMA =
        "{\"oneOf\":[{\"type\":\"object\",\"properties\":{\"kind\":{\"const\":\"A\"}," +
        "\"value\":{\"type\":\"integer\"}},\"required\":[\"kind\",\"value\"]}," +
        "{\"type\":\"object\",\"properties\":{\"kind\":{\"const\":\"B\"}," +
        "\"value\":{\"type\":\"string\"}},\"required\":[\"kind\",\"value\"]}]}";

    private static final String CONTAINS_SCHEMA =
        "{\"type\":\"array\",\"contains\":{\"type\":\"object\",\"properties\":" +
        "{\"marker\":{\"const\":true}},\"required\":[\"marker\"]}}";

    private static final String UNIQUE_SCALARS_SCHEMA =
        "{\"type\":\"array\",\"uniqueItems\":true}";

    private static final String UNIQUE_OBJECTS_SCHEMA =
        "{\"type\":\"array\",\"uniqueItems\":true}";

    private static final String FLAT_OBJECT_INSTANCE =
        "{\"id\":42,\"name\":\"zilla\",\"active\":true} ";

    private static final String ARRAY_OBJECTS_INSTANCE =
        "[{\"id\":0,\"tag\":\"a\"},{\"id\":1,\"tag\":\"b\"},{\"id\":2,\"tag\":\"c\"}," +
        "{\"id\":3,\"tag\":\"d\"},{\"id\":4,\"tag\":\"e\"},{\"id\":5,\"tag\":\"f\"}," +
        "{\"id\":6,\"tag\":\"g\"},{\"id\":7,\"tag\":\"h\"}] ";

    private static final String ONE_OF_INSTANCE =
        "{\"kind\":\"B\",\"value\":\"payload\"} ";

    private static final String CONTAINS_INSTANCE =
        "[{\"marker\":false},{\"marker\":false},{\"marker\":false},{\"marker\":false}," +
        "{\"marker\":false},{\"marker\":false},{\"marker\":false},{\"marker\":true}] ";

    private static final String UNIQUE_SCALARS_INSTANCE =
        "[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15] ";

    private static final String UNIQUE_OBJECTS_INSTANCE =
        "[{\"id\":0,\"name\":\"a\"},{\"id\":1,\"name\":\"b\"},{\"id\":2,\"name\":\"c\"}," +
        "{\"id\":3,\"name\":\"d\"},{\"id\":4,\"name\":\"e\"},{\"id\":5,\"name\":\"f\"}," +
        "{\"id\":6,\"name\":\"g\"},{\"id\":7,\"name\":\"h\"}] ";

    private final DirectBufferInputStreamEx inputRO = new DirectBufferInputStreamEx();

    private JsonSchema flatObjectSchema;
    private JsonSchema arrayObjectsSchema;
    private JsonSchema oneOfSchema;
    private JsonSchema containsSchema;
    private JsonSchema uniqueScalarsSchema;
    private JsonSchema uniqueObjectsSchema;

    private UnsafeBufferEx flatObjectBuffer;
    private UnsafeBufferEx arrayObjectsBuffer;
    private UnsafeBufferEx oneOfBuffer;
    private UnsafeBufferEx containsBuffer;
    private UnsafeBufferEx uniqueScalarsBuffer;
    private UnsafeBufferEx uniqueObjectsBuffer;

    private int flatObjectLength;
    private int arrayObjectsLength;
    private int oneOfLength;
    private int containsLength;
    private int uniqueScalarsLength;
    private int uniqueObjectsLength;

    @Setup(Level.Trial)
    public void init()
    {
        flatObjectSchema = JsonSchema.of(FLAT_OBJECT_SCHEMA);
        arrayObjectsSchema = JsonSchema.of(ARRAY_OBJECTS_SCHEMA);
        oneOfSchema = JsonSchema.of(ONE_OF_SCHEMA);
        containsSchema = JsonSchema.of(CONTAINS_SCHEMA);
        uniqueScalarsSchema = JsonSchema.of(UNIQUE_SCALARS_SCHEMA);
        uniqueObjectsSchema = JsonSchema.of(UNIQUE_OBJECTS_SCHEMA);

        byte[] flatObjectBytes = FLAT_OBJECT_INSTANCE.getBytes(UTF_8);
        byte[] arrayObjectsBytes = ARRAY_OBJECTS_INSTANCE.getBytes(UTF_8);
        byte[] oneOfBytes = ONE_OF_INSTANCE.getBytes(UTF_8);
        byte[] containsBytes = CONTAINS_INSTANCE.getBytes(UTF_8);
        byte[] uniqueScalarsBytes = UNIQUE_SCALARS_INSTANCE.getBytes(UTF_8);
        byte[] uniqueObjectsBytes = UNIQUE_OBJECTS_INSTANCE.getBytes(UTF_8);

        flatObjectBuffer = new UnsafeBufferEx(flatObjectBytes);
        arrayObjectsBuffer = new UnsafeBufferEx(arrayObjectsBytes);
        oneOfBuffer = new UnsafeBufferEx(oneOfBytes);
        containsBuffer = new UnsafeBufferEx(containsBytes);
        uniqueScalarsBuffer = new UnsafeBufferEx(uniqueScalarsBytes);
        uniqueObjectsBuffer = new UnsafeBufferEx(uniqueObjectsBytes);

        flatObjectLength = flatObjectBytes.length;
        arrayObjectsLength = arrayObjectsBytes.length;
        oneOfLength = oneOfBytes.length;
        containsLength = containsBytes.length;
        uniqueScalarsLength = uniqueScalarsBytes.length;
        uniqueObjectsLength = uniqueObjectsBytes.length;
    }

    @Benchmark
    public boolean validateFlatObject()
    {
        return flatObjectSchema.validate(parserFor(flatObjectBuffer, flatObjectLength));
    }

    @Benchmark
    public boolean validateArrayObjects()
    {
        return arrayObjectsSchema.validate(parserFor(arrayObjectsBuffer, arrayObjectsLength));
    }

    @Benchmark
    public boolean validateOneOf()
    {
        return oneOfSchema.validate(parserFor(oneOfBuffer, oneOfLength));
    }

    @Benchmark
    public boolean validateContains()
    {
        return containsSchema.validate(parserFor(containsBuffer, containsLength));
    }

    @Benchmark
    public boolean validateUniqueScalars()
    {
        return uniqueScalarsSchema.validate(parserFor(uniqueScalarsBuffer, uniqueScalarsLength));
    }

    @Benchmark
    public boolean validateUniqueObjects()
    {
        return uniqueObjectsSchema.validate(parserFor(uniqueObjectsBuffer, uniqueObjectsLength));
    }

    private JsonParser parserFor(
        UnsafeBufferEx buffer,
        int length)
    {
        inputRO.wrap(buffer, 0, length);
        return JsonEx.createParser(inputRO);
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonSchemaBM.class.getSimpleName())
            .forks(0)
            .build();

        new Runner(opt).run();
    }
}
