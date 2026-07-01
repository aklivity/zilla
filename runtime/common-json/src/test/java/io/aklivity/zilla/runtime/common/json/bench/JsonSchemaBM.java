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

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.DirectBufferInputStreamEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
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

    // typed integers with no numeric bounds: the type check needs only integrality, never a BigDecimal
    private static final String TYPED_INTEGERS_SCHEMA =
        "{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}";

    // the control: minimum/maximum/multipleOf force a BigDecimal per element
    private static final String BOUNDED_NUMBERS_SCHEMA =
        "{\"type\":\"array\",\"items\":{\"type\":\"number\",\"minimum\":0,\"maximum\":1000,\"multipleOf\":1}}";

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

    private static final String NUMBERS_INSTANCE =
        "[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15] ";

    private static final String UNIQUE_SCALARS_INSTANCE =
        "[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15] ";

    private static final String UNIQUE_OBJECTS_INSTANCE =
        "[{\"id\":0,\"name\":\"a\"},{\"id\":1,\"name\":\"b\"},{\"id\":2,\"name\":\"c\"}," +
        "{\"id\":3,\"name\":\"d\"},{\"id\":4,\"name\":\"e\"},{\"id\":5,\"name\":\"f\"}," +
        "{\"id\":6,\"name\":\"g\"},{\"id\":7,\"name\":\"h\"}] ";

    // one parser reused across messages as a long-lived caller does: each op re-wraps the input and
    // resets it, rather than constructing a parser (and its 64-deep tokenizer path buffers) per message
    private final DirectBufferInputStreamEx inputRO = new DirectBufferInputStreamEx();
    private final JsonParserEx parser = JsonEx.createParser(inputRO);

    // one validating parser per kind, reused across ops over the shared delegate: reset() per op replays
    // one evaluator, the production pattern, rather than building a fresh tree per document
    private JsonParserEx flatObjectValidator;
    private JsonParserEx arrayObjectsValidator;
    private JsonParserEx oneOfValidator;
    private JsonParserEx containsValidator;
    private JsonParserEx typedIntegersValidator;
    private JsonParserEx boundedNumbersValidator;
    private JsonParserEx uniqueScalarsValidator;
    private JsonParserEx uniqueObjectsValidator;

    private UnsafeBufferEx flatObjectBuffer;
    private UnsafeBufferEx arrayObjectsBuffer;
    private UnsafeBufferEx oneOfBuffer;
    private UnsafeBufferEx containsBuffer;
    private UnsafeBufferEx numbersBuffer;
    private UnsafeBufferEx uniqueScalarsBuffer;
    private UnsafeBufferEx uniqueObjectsBuffer;

    private int flatObjectLength;
    private int arrayObjectsLength;
    private int oneOfLength;
    private int containsLength;
    private int numbersLength;
    private int uniqueScalarsLength;
    private int uniqueObjectsLength;

    @Setup(Level.Trial)
    public void init()
    {
        flatObjectValidator = validator(FLAT_OBJECT_SCHEMA);
        arrayObjectsValidator = validator(ARRAY_OBJECTS_SCHEMA);
        oneOfValidator = validator(ONE_OF_SCHEMA);
        containsValidator = validator(CONTAINS_SCHEMA);
        typedIntegersValidator = validator(TYPED_INTEGERS_SCHEMA);
        boundedNumbersValidator = validator(BOUNDED_NUMBERS_SCHEMA);
        uniqueScalarsValidator = validator(UNIQUE_SCALARS_SCHEMA);
        uniqueObjectsValidator = validator(UNIQUE_OBJECTS_SCHEMA);

        byte[] flatObjectBytes = FLAT_OBJECT_INSTANCE.getBytes(UTF_8);
        byte[] arrayObjectsBytes = ARRAY_OBJECTS_INSTANCE.getBytes(UTF_8);
        byte[] oneOfBytes = ONE_OF_INSTANCE.getBytes(UTF_8);
        byte[] containsBytes = CONTAINS_INSTANCE.getBytes(UTF_8);
        byte[] numbersBytes = NUMBERS_INSTANCE.getBytes(UTF_8);
        byte[] uniqueScalarsBytes = UNIQUE_SCALARS_INSTANCE.getBytes(UTF_8);
        byte[] uniqueObjectsBytes = UNIQUE_OBJECTS_INSTANCE.getBytes(UTF_8);

        flatObjectBuffer = new UnsafeBufferEx(flatObjectBytes);
        arrayObjectsBuffer = new UnsafeBufferEx(arrayObjectsBytes);
        oneOfBuffer = new UnsafeBufferEx(oneOfBytes);
        containsBuffer = new UnsafeBufferEx(containsBytes);
        numbersBuffer = new UnsafeBufferEx(numbersBytes);
        uniqueScalarsBuffer = new UnsafeBufferEx(uniqueScalarsBytes);
        uniqueObjectsBuffer = new UnsafeBufferEx(uniqueObjectsBytes);

        flatObjectLength = flatObjectBytes.length;
        arrayObjectsLength = arrayObjectsBytes.length;
        oneOfLength = oneOfBytes.length;
        containsLength = containsBytes.length;
        numbersLength = numbersBytes.length;
        uniqueScalarsLength = uniqueScalarsBytes.length;
        uniqueObjectsLength = uniqueObjectsBytes.length;
    }

    @Benchmark
    public int validateFlatObject()
    {
        return drive(flatObjectValidator, flatObjectBuffer, flatObjectLength);
    }

    @Benchmark
    public int validateArrayObjects()
    {
        return drive(arrayObjectsValidator, arrayObjectsBuffer, arrayObjectsLength);
    }

    @Benchmark
    public int validateOneOf()
    {
        return drive(oneOfValidator, oneOfBuffer, oneOfLength);
    }

    @Benchmark
    public int validateContains()
    {
        return drive(containsValidator, containsBuffer, containsLength);
    }

    @Benchmark
    public int validateTypedIntegers()
    {
        return drive(typedIntegersValidator, numbersBuffer, numbersLength);
    }

    @Benchmark
    public int validateBoundedNumbers()
    {
        return drive(boundedNumbersValidator, numbersBuffer, numbersLength);
    }

    @Benchmark
    public int validateUniqueScalars()
    {
        return drive(uniqueScalarsValidator, uniqueScalarsBuffer, uniqueScalarsLength);
    }

    @Benchmark
    public int validateUniqueObjects()
    {
        return drive(uniqueObjectsValidator, uniqueObjectsBuffer, uniqueObjectsLength);
    }

    private JsonParserEx validator(
        String schema)
    {
        return (JsonParserEx) JsonSchema.of(schema).newParser(false, parser);
    }

    // re-wrap the input over the next instance and reset the validating parser, then drive it to completion
    private int drive(
        JsonParserEx validator,
        UnsafeBufferEx buffer,
        int length)
    {
        inputRO.wrap(buffer, 0, length);
        validator.reset();
        int events = 0;
        while (validator.hasNext())
        {
            validator.next();
            events++;
        }
        return events;
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
