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

import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
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
 * Measures reading one string value through {@link JsonParserEx#getStringView()}, in a document that fits
 * the input window, as the value's length and its encoding vary. {@code walk} reads every char of the view,
 * {@code encode} reads every char and writes it as UTF-8, as a consumer that forwards the text would, and
 * {@code scan} reads only the first char, so it measures the parser's own cost of delivering the value.
 * Under {@code -prof gc} each should report the allocation the parser makes to deliver the value.
 * <p>
 * {@code plain} has no escapes and only ASCII, {@code escaped} has about 2% of its chars as escapes,
 * {@code dense} about 8%, and {@code multibyte} has about 2% raw multi-byte characters and 2% escapes.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonStringBM
{
    private static final String[] WORDS =
    {
        "the", "model", "returns", "a", "response", "with", "tokens", "and", "Hello", "world", "x", "plan", "list", "of", "items"
    };

    @Param({"plain", "escaped", "dense", "multibyte"})
    public String kind;

    @Param({"400", "10000"})
    public int length;

    private final JsonParserEx parser = JsonEx.createParser();

    private UnsafeBufferEx input;
    private int inputLength;
    private UnsafeBufferEx output;

    @Setup(Level.Trial)
    public void init()
    {
        final String value = encoded(kind, length);
        final byte[] bytes = String.format("{\"text\":\"%s\"}", value).getBytes(UTF_8);

        input = new UnsafeBufferEx(bytes);
        inputLength = bytes.length;
        output = new UnsafeBufferEx(new byte[length * 4 + 8]);
    }

    @Benchmark
    public int scan()
    {
        int result = 0;

        parser.reset();
        parser.wrap(input, 0, inputLength);

        while (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.VALUE_STRING)
            {
                result += parser.getStringView().charAt(0);
            }
        }

        return result;
    }

    @Benchmark
    public int walk()
    {
        int result = 0;

        parser.reset();
        parser.wrap(input, 0, inputLength);

        while (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.VALUE_STRING)
            {
                final CharSequence chars = parser.getStringView();

                for (int index = 0, length = chars.length(); index < length; index++)
                {
                    result += chars.charAt(index);
                }
            }
        }

        return result;
    }

    @Benchmark
    public int encode()
    {
        int result = 0;

        parser.reset();
        parser.wrap(input, 0, inputLength);

        while (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.VALUE_STRING)
            {
                result += encode(parser.getStringView());
            }
        }

        return result;
    }

    private int encode(
        CharSequence chars)
    {
        int position = 0;

        for (int index = 0, length = chars.length(); index < length; index++)
        {
            final char c = chars.charAt(index);

            if (c < 0x80)
            {
                output.putByte(position++, (byte) c);
            }
            else if (c < 0x800)
            {
                output.putByte(position++, (byte) (0xC0 | c >> 6));
                output.putByte(position++, (byte) (0x80 | c & 0x3F));
            }
            else
            {
                output.putByte(position++, (byte) (0xE0 | c >> 12));
                output.putByte(position++, (byte) (0x80 | c >> 6 & 0x3F));
                output.putByte(position++, (byte) (0x80 | c & 0x3F));
            }
        }

        return position;
    }

    private static String encoded(
        String kind,
        int length)
    {
        final Random random = new Random(7L);
        final double escapes = switch (kind)
        {
        case "escaped", "multibyte" -> 0.02;
        case "dense" -> 0.08;
        default -> 0.0;
        };
        final double multibyte = "multibyte".equals(kind) ? 0.02 : 0.0;

        final StringBuilder value = new StringBuilder();
        int chars = 0;

        while (chars < length)
        {
            final String word = WORDS[random.nextInt(WORDS.length)];
            value.append(word);
            chars += word.length();

            final double next = random.nextDouble();

            if (next < escapes)
            {
                value.append(next < escapes / 2 ? "\\n" : "\\\"");
            }
            else if (next < escapes + multibyte)
            {
                value.append(random.nextBoolean() ? "é" : "€");
            }
            else
            {
                value.append(' ');
            }

            chars++;
        }

        return value.toString();
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonStringBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
