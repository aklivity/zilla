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

import jakarta.json.stream.JsonParser.Event;

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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

/**
 * Isolates {@link JsonParserEx#getLong()} from the rest of the parse: drains every event of a flat
 * number array, calling {@code getLong()} on each {@code VALUE_NUMBER}, so any change to number decoding
 * (e.g. fusing digit accumulation into the tokenizer's scan instead of a second
 * {@code Long.parseLong(CharSequence, ...)} pass) shows up directly in throughput and allocation here.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonNumberDecodeBM
{
    private static final String NUMBER_ARRAY = numberArray(2000);

    private DirectBufferEx buffer;
    private int length;
    private JsonParserEx parser;

    @Setup(Level.Trial)
    public void init()
    {
        byte[] bytes = NUMBER_ARRAY.getBytes(UTF_8);
        buffer = new UnsafeBufferEx(bytes);
        length = bytes.length;
        parser = JsonEx.createParser();
    }

    @Benchmark
    public long sumLongValues()
    {
        parser.reset();
        parser.wrap(buffer, 0, length);
        long sum = 0L;
        while (parser.hasNext())
        {
            if (parser.next() == Event.VALUE_NUMBER)
            {
                sum += parser.getLong();
            }
        }
        return sum;
    }

    private static String numberArray(
        int count)
    {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < count; i++)
        {
            if (i > 0)
            {
                builder.append(',');
            }
            builder.append(123456789 + i);
        }
        return builder.append(']').toString();
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonNumberDecodeBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
