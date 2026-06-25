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

import java.io.ByteArrayInputStream;
import java.io.Writer;

import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.spi.JsonProvider;

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

import io.aklivity.zilla.runtime.common.json.internal.json.JsonProviderImpl;

/**
 * Exercises the {@code common-json} {@code jakarta.json} DOM path — {@link JsonReader} parse-to-tree
 * and {@link JsonWriter} tree-to-text — the complement to {@link JsonPipelineBM}, which covers the
 * streaming {@code JsonParserEx}/{@code JsonGeneratorEx} path. The streaming path materializes no
 * {@code JsonValue} tree and is allocation-free for scalars; the DOM path here builds a {@code JsonValue}
 * tree and so is where per-value allocation (number lexemes, {@code BigDecimal}, builders) is incurred.
 * A number-heavy document isolates the number representation cost; under {@code -prof gc} the
 * allocation-per-op of each shape can be compared directly.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonReaderWriterBM
{
    private static final String FLAT_OBJECT =
        "{\"id\":42,\"name\":\"zilla\",\"active\":true,\"secret\":\"drop\",\"version\":1}";

    // number-heavy: an array of objects whose values are all numbers, the worst case for the
    // number representation on both the read (lexeme -> JsonNumber) and write (JsonNumber -> text) paths
    private static final String NUMBERS =
        "{\"series\":[" +
        "{\"t\":1,\"v\":1.5,\"d\":-273.15,\"e\":6.022e23}," +
        "{\"t\":2,\"v\":2.25,\"d\":100.0,\"e\":1.602e-19}," +
        "{\"t\":3,\"v\":3.125,\"d\":42,\"e\":9223372036854775807}," +
        "{\"t\":4,\"v\":4.0625,\"d\":0.0001,\"e\":3.14159265358979}" +
        "]}";

    private static final String NESTED =
        "{\"meta\":{\"id\":7,\"source\":\"sensor\",\"trace\":\"keep-me\",\"tags\":[\"a\",\"b\",\"c\"]}," +
        "\"body\":{\"payload\":\"large\",\"headers\":{\"a\":1,\"b\":2}},\"ignored\":true}";

    private final JsonProvider provider = new JsonProviderImpl();
    private final Writer discard = new DiscardWriter();

    private byte[] flatBytes;
    private byte[] numbersBytes;
    private byte[] nestedBytes;

    private JsonValue flatValue;
    private JsonValue numbersValue;
    private JsonValue nestedValue;

    @Setup(Level.Trial)
    public void init()
    {
        flatBytes = FLAT_OBJECT.getBytes(UTF_8);
        numbersBytes = NUMBERS.getBytes(UTF_8);
        nestedBytes = NESTED.getBytes(UTF_8);

        flatValue = read(flatBytes);
        numbersValue = read(numbersBytes);
        nestedValue = read(nestedBytes);
    }

    @Benchmark
    public JsonValue readFlat()
    {
        return read(flatBytes);
    }

    @Benchmark
    public JsonValue readNumbers()
    {
        return read(numbersBytes);
    }

    @Benchmark
    public JsonValue readNested()
    {
        return read(nestedBytes);
    }

    @Benchmark
    public Writer writeFlat()
    {
        return write(flatValue);
    }

    @Benchmark
    public Writer writeNumbers()
    {
        return write(numbersValue);
    }

    @Benchmark
    public Writer writeNested()
    {
        return write(nestedValue);
    }

    @Benchmark
    public Writer roundTripNumbers()
    {
        return write(read(numbersBytes));
    }

    private JsonValue read(
        byte[] bytes)
    {
        JsonValue value;
        try (JsonReader reader = provider.createReader(new ByteArrayInputStream(bytes)))
        {
            value = reader.readValue();
        }
        return value;
    }

    private Writer write(
        JsonValue value)
    {
        try (JsonWriter writer = provider.createWriter(discard))
        {
            writer.write(value);
        }
        return discard;
    }

    // Sink that discards output so the measurement reflects the reader/writer and DOM allocations,
    // not the cost of an output buffer; the field instance is reused across invocations.
    private static final class DiscardWriter extends Writer
    {
        @Override
        public void write(
            char[] cbuf,
            int off,
            int len)
        {
        }

        @Override
        public void write(
            int c)
        {
        }

        @Override
        public void write(
            String str,
            int off,
            int len)
        {
        }

        @Override
        public void flush()
        {
        }

        @Override
        public void close()
        {
        }
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonReaderWriterBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
