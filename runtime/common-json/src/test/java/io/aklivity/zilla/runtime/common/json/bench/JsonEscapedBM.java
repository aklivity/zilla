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

import java.util.Map;

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

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSink.Delivery;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Measures the escaped scope — a string value that itself holds a JSON document — against a control that
 * carries the same document as a plain value. {@code unescape} parses the string as a document and drops the
 * markers, {@code unescapeForward} forwards them so the generator renders the string again, {@code escape}
 * renders a plain value as the string, and {@code unescapeWindowed} feeds {@code unescape} through windows
 * narrower than the string so the document is carried across fragments. After warm-up each should allocate
 * nothing under {@code -prof gc}, as the control does.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonEscapedBM
{
    private static final String PLAIN = "{\"a\":{\"b\":1,\"c\":[1,2,3],\"e\":\"text\"},\"d\":4}";
    private static final String STRINGIFIED = "{\"a\":\"{\\\"b\\\":1,\\\"c\\\":[1,2,3],\\\"e\\\":\\\"text\\\"}\",\"d\":4}";

    private static final int WINDOW = 16;

    private final MutableDirectBufferEx outputBuffer = new UnsafeBufferEx(new byte[16 * 1024]);
    private final JsonGeneratorEx generator = JsonEx.createGenerator();
    private final JsonSink sink = JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, Delivery.STRUCTURED));

    private JsonPipeline plainPipeline;
    private JsonPipeline unescapePipeline;
    private JsonPipeline unescapeForwardPipeline;
    private JsonPipeline escapePipeline;

    private UnsafeBufferEx plainBuffer;
    private UnsafeBufferEx stringifiedBuffer;

    private int plainLength;
    private int stringifiedLength;

    @Setup(Level.Trial)
    public void init()
    {
        plainPipeline = JsonEx.stream(JsonEx.createParser()).into(sink);
        unescapePipeline = JsonEx.stream(JsonEx.createParser()).transform(new Unescape(false)).into(sink);
        unescapeForwardPipeline = JsonEx.stream(JsonEx.createParser()).transform(new Unescape(true)).into(sink);
        escapePipeline = JsonEx.stream(JsonEx.createParser()).transform(new Escape()).into(sink);

        byte[] plainBytes = PLAIN.getBytes(UTF_8);
        byte[] stringifiedBytes = STRINGIFIED.getBytes(UTF_8);

        plainBuffer = new UnsafeBufferEx(plainBytes);
        stringifiedBuffer = new UnsafeBufferEx(stringifiedBytes);

        plainLength = plainBytes.length;
        stringifiedLength = stringifiedBytes.length;
    }

    @Benchmark
    public int plain()
    {
        return run(plainPipeline, plainBuffer, plainLength);
    }

    @Benchmark
    public int unescape()
    {
        return run(unescapePipeline, stringifiedBuffer, stringifiedLength);
    }

    @Benchmark
    public int unescapeForward()
    {
        return run(unescapeForwardPipeline, stringifiedBuffer, stringifiedLength);
    }

    @Benchmark
    public int unescapeWindowed()
    {
        return runWindowed(unescapePipeline, stringifiedBuffer, stringifiedLength, WINDOW);
    }

    @Benchmark
    public int escape()
    {
        return run(escapePipeline, plainBuffer, plainLength);
    }

    private int run(
        JsonPipeline pipeline,
        UnsafeBufferEx buffer,
        int length)
    {
        generator.wrap(outputBuffer, 0, outputBuffer.capacity());
        pipeline.reset();
        pipeline.transform(buffer, 0, length);
        return generator.length();
    }

    private int runWindowed(
        JsonPipeline pipeline,
        UnsafeBufferEx buffer,
        int length,
        int window)
    {
        generator.wrap(outputBuffer, 0, outputBuffer.capacity());
        pipeline.reset();
        int progress = 0;
        int limit = 0;
        Status status = Status.STARVED;
        while (limit < length)
        {
            limit = Math.min(limit + window, length);
            boolean last = limit >= length;
            status = pipeline.transform(buffer, progress, limit, last);
            if (status != Status.STARVED)
            {
                break;
            }
            progress = limit - pipeline.remaining();
        }
        return status == Status.COMPLETED ? generator.length() : -1;
    }

    // Asks for the string value of key "a" to be parsed as a document, dropping the markers or forwarding them;
    // mediating, so the structured events it matches on are what it sees. Allocates nothing per document.
    private static final class Unescape implements JsonTransform
    {
        private final boolean forward;
        private final Mediator mediator;

        private Unescape(
            boolean forward)
        {
            this.forward = forward;
            this.mediator = new Mediator();
        }

        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            mediator.upstream = control;
            Status status;
            if (event == JsonEvent.KEY_NAME && source.deferredBytes())
            {
                control.consumed(0);
                status = Status.STARVED;
            }
            else
            {
                final boolean escape = event == JsonEvent.KEY_NAME && isKeyA(source.getStringView());
                status = event.isEscaped() && !forward ? Status.ADVANCED : sink.transform(mediator, source, event);
                if (escape)
                {
                    control.escaped();
                }
            }
            return status;
        }
    }

    // Wraps the value of key "a" in markers, so the generator renders it as a string.
    private static final class Escape implements JsonTransform
    {
        private final Mediator mediator = new Mediator();

        private int depth;
        private boolean escaping;

        @Override
        public void reset()
        {
            depth = 0;
            escaping = false;
        }

        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            mediator.upstream = control;
            final boolean escape = event == JsonEvent.KEY_NAME && isKeyA(source.getStringView());
            final Status status = sink.transform(mediator, source, event);
            if (escaping)
            {
                switch (event)
                {
                case START_OBJECT:
                case START_ARRAY:
                    depth++;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    depth--;
                    break;
                default:
                    break;
                }
                if (depth == 1)
                {
                    escaping = false;
                    sink.transform(mediator, source, JsonEvent.END_ESCAPED);
                }
            }
            else if (escape)
            {
                escaping = true;
                sink.transform(mediator, source, JsonEvent.START_ESCAPED);
            }
            else if (event == JsonEvent.START_OBJECT)
            {
                depth = 1;
            }
            return status;
        }
    }

    private static final class Mediator implements JsonController
    {
        private JsonController upstream;

        @Override
        public void segmentable()
        {
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            upstream.consumed(sourceBytes);
        }

        @Override
        public void escaped()
        {
            upstream.escaped();
        }
    }

    private static boolean isKeyA(
        CharSequence key)
    {
        return key.length() == 1 && key.charAt(0) == 'a';
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonEscapedBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
