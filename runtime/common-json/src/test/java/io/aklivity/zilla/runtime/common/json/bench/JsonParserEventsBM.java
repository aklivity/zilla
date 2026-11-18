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
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

// pulls every event of one large document delivered as a single window, with a parser built for every
// document or one parser reset for every document: what a parser retains per delivered event shows as
// allocation per operation
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonParserEventsBM
{
    private static final int ELEMENTS = 50_000;
    private static final int PROPERTIES = 10_000;

    private final JsonParserEx parser = JsonEx.createParser();

    private UnsafeBufferEx arrayBuffer;
    private UnsafeBufferEx objectBuffer;
    private int arrayLength;
    private int objectLength;

    @Setup
    public void init()
    {
        final StringBuilder array = new StringBuilder("[");
        for (int index = 0; index < ELEMENTS; index++)
        {
            array.append(index == 0 ? "" : ",").append(index);
        }
        final StringBuilder object = new StringBuilder("{");
        for (int index = 0; index < PROPERTIES; index++)
        {
            object.append(index == 0 ? "" : ",").append("\"k").append(index).append("\":\"v\"");
        }

        final byte[] arrayBytes = array.append("] ").toString().getBytes(UTF_8);
        final byte[] objectBytes = object.append("} ").toString().getBytes(UTF_8);
        arrayBuffer = new UnsafeBufferEx(arrayBytes);
        objectBuffer = new UnsafeBufferEx(objectBytes);
        arrayLength = arrayBytes.length;
        objectLength = objectBytes.length;
    }

    @Benchmark
    public int pullArrayFresh()
    {
        return pull(JsonEx.createParser(), arrayBuffer, arrayLength);
    }

    @Benchmark
    public int pullObjectFresh()
    {
        return pull(JsonEx.createParser(), objectBuffer, objectLength);
    }

    @Benchmark
    public int pullArrayReused()
    {
        parser.reset();
        return pull(parser, arrayBuffer, arrayLength);
    }

    @Benchmark
    public int pullObjectReused()
    {
        parser.reset();
        return pull(parser, objectBuffer, objectLength);
    }

    private static int pull(
        JsonParserEx parser,
        UnsafeBufferEx buffer,
        int length)
    {
        parser.wrap(buffer, 0, length, true);
        int events = 0;
        while (parser.hasNextEvent())
        {
            parser.nextEvent(JsonParserEx.Mode.STRUCTURED);
            events++;
        }
        return events;
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonParserEventsBM.class.getSimpleName())
            .forks(0)
            .build();

        new Runner(opt).run();
    }
}
