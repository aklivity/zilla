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
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

/**
 * Measures parsing many streams that each deliver a document in small chunks, one chunk per operation, taken from
 * the streams in turn. {@code perStreamParser} gives every stream a parser of its own for the life of its
 * document, and {@code sharedParser} gives all streams one parser, which restores the state of the stream whose
 * chunk it is about to parse and saves it again afterwards. Each chunk is parsed after the bytes the stream's
 * previous chunk left unconsumed, so the two differ only in where the parse state lives. {@code constructParser}
 * measures building a parser, which under {@code -prof gc} reports its size as {@code gc.alloc.rate.norm}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonSaveRestoreBM
{
    private static final int DOCUMENT_LENGTH = 8192;
    private static final int RECORD_CAPACITY = 128;
    private static final String ITEM =
        "{\"id\":%d,\"name\":\"item-%d\",\"tags\":[\"a\",\"bb\",\"ccc\"],\"ok\":true,\"score\":-1.5e2,\"next\":null," +
        "\"note\":\"text with \\\"escape\\\" and café\"}";

    @Param({"64", "256"})
    public int chunk;

    @Param({"16", "256"})
    public int streams;

    private byte[] document;
    private Stream[] perStream;
    private Stream[] shared;
    private JsonParserEx sharedParser;
    private int perStreamCursor;
    private int sharedCursor;

    @Setup
    public void init()
    {
        final StringBuilder json = new StringBuilder("[");
        for (int id = 0; json.length() < DOCUMENT_LENGTH; id++)
        {
            json.append(id == 0 ? "" : ",").append(String.format(ITEM, id, id));
        }
        json.append("]");
        document = json.toString().getBytes(UTF_8);

        perStream = new Stream[streams];
        shared = new Stream[streams];
        for (int index = 0; index < streams; index++)
        {
            perStream[index] = new Stream(JsonEx.createParser(), chunk);
            shared[index] = new Stream(null, chunk);
        }
        sharedParser = JsonEx.createParser();
    }

    @Benchmark
    public int perStreamParser()
    {
        final Stream stream = perStream[perStreamCursor++ & streams - 1];

        return parse(stream, stream.parser, false);
    }

    @Benchmark
    public int sharedParser()
    {
        final Stream stream = shared[sharedCursor++ & streams - 1];

        return parse(stream, sharedParser, true);
    }

    @Benchmark
    public JsonParserEx constructParser()
    {
        return JsonEx.createParser();
    }

    private int parse(
        Stream stream,
        JsonParserEx parser,
        boolean saved)
    {
        final int offset = stream.chunkIndex * chunk;
        final int length = Math.min(chunk, document.length - offset);
        final boolean last = offset + length == document.length;

        System.arraycopy(document, offset, stream.window, stream.tail, length);
        final int limit = stream.tail + length;

        if (stream.chunkIndex == 0)
        {
            parser.reset();
        }
        else if (saved)
        {
            parser.restore(stream.record, 0);
        }

        parser.wrap(stream.input, 0, limit, last);

        int result = 0;
        while (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event == JsonEvent.KEY_NAME || event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER)
            {
                final int chars = parser.getStringView().length();
                result += chars;
                parser.consumed(chars);
            }
            else
            {
                result += event.ordinal();
            }
        }

        if (last)
        {
            stream.chunkIndex = 0;
            stream.tail = 0;
        }
        else
        {
            final int remaining = parser.remaining();
            System.arraycopy(stream.window, limit - remaining, stream.window, 0, remaining);
            stream.tail = remaining;
            stream.chunkIndex++;

            if (saved)
            {
                result += parser.save(stream.record, 0);
            }
        }

        return result;
    }

    private static final class Stream
    {
        private final JsonParserEx parser;
        private final byte[] window;
        private final UnsafeBufferEx input;
        private final UnsafeBufferEx record = new UnsafeBufferEx(new byte[RECORD_CAPACITY]);

        private int chunkIndex;
        private int tail;

        Stream(
            JsonParserEx parser,
            int chunk)
        {
            this.parser = parser;
            this.window = new byte[chunk + 256];
            this.input = new UnsafeBufferEx(window);
        }
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonSaveRestoreBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
