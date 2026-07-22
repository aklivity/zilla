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

import java.math.BigDecimal;

import jakarta.json.stream.JsonLocation;

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
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

/**
 * Measures the two mutating verbatim transforms — {@code skip} (Phase 2 prune, via
 * {@link JsonSource#skipValue()}) and {@code inject} (Phase 3, via {@link JsonSource#event()}-driven generator
 * step tracking) — against a verbatim passthrough control over the same input. Both transforms reuse constant
 * injected key/value and a reused mediating controller and (for inject) reused injection sources, so the only
 * per-op allocation under {@code -prof gc} is in the pipeline itself (parser, generator, sink, source) — the
 * verbatim primitives splice through reused buffer views, so skip and inject should approach the passthrough
 * control's allocation rate rather than the canonical re-render's.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonMutateBM
{
    // insignificant whitespace, so a verbatim splice and a canonical re-render diverge
    private static final String DOCUMENT = "{ \"a\" : 1, \"b\" : 2, \"c\" : 3 } ";

    // constants the transforms reuse, so no String/CharSequence is allocated per op
    private static final String DROP_KEY = "b";
    private static final String BEFORE_KEY = "c";
    private static final String FIRST_KEY = "a";
    private static final String INJECT_KEY = "x";
    private static final String INJECT_VALUE = "9";

    private final MutableDirectBufferEx outputBuffer = new UnsafeBufferEx(new byte[16 * 1024]);
    private final JsonGeneratorEx generator = JsonEx.createGenerator();
    private final JsonSink sink = JsonEx.createSink(generator);

    private JsonPipeline skipPipeline;
    private JsonPipeline injectPipeline;
    private JsonPipeline injectFirstPipeline;
    private JsonPipeline passthroughPipeline;

    private UnsafeBufferEx documentBuffer;
    private int documentLength;

    @Setup(Level.Trial)
    public void init()
    {
        skipPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new Skip(DROP_KEY)).into(sink);
        injectPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new Inject(BEFORE_KEY, INJECT_KEY, INJECT_VALUE)).into(sink);
        // inject before the container's first member: exercises the synthesized leading separator the
        // displaced former-first member's verbatim bytes do not carry
        injectFirstPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new Inject(FIRST_KEY, INJECT_KEY, INJECT_VALUE)).into(sink);
        // the verbatim passthrough control: forwards every event verbatim, no mutation
        passthroughPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new Passthrough()).into(sink);

        byte[] documentBytes = DOCUMENT.getBytes(UTF_8);
        documentBuffer = new UnsafeBufferEx(documentBytes);
        documentLength = documentBytes.length;
    }

    @Benchmark
    public int skipMiddleField()
    {
        return run(skipPipeline);
    }

    @Benchmark
    public int injectMember()
    {
        return run(injectPipeline);
    }

    @Benchmark
    public int injectBeforeFirstMember()
    {
        return run(injectFirstPipeline);
    }

    @Benchmark
    public int passthroughVerbatim()
    {
        return run(passthroughPipeline);
    }

    private int run(
        JsonPipeline pipeline)
    {
        generator.wrap(outputBuffer, 0, outputBuffer.capacity());
        pipeline.reset();
        pipeline.transform(documentBuffer, 0, documentLength);
        return generator.length();
    }

    // Re-asserts verbatim downstream once the sink has opted in: a body event (a scalar, key, or structural
    // event — not document framing or a segment) is forwarded as a VERBATIM event so the sink copies the
    // original source bytes rather than re-rendering them canonically.
    private static JsonEvent forward(
        boolean downstreamVerbatim,
        JsonEvent event)
    {
        boolean body = event != JsonEvent.START_DOCUMENT && event != JsonEvent.END_DOCUMENT && !event.segmented();
        return downstreamVerbatim && body ? JsonEvent.VERBATIM : event;
    }

    private static boolean contentEquals(
        String name,
        CharSequence view)
    {
        boolean matches = name.length() == view.length();
        for (int i = 0; matches && i < name.length(); i++)
        {
            matches = name.charAt(i) == view.charAt(i);
        }
        return matches;
    }

    // Forwards every event verbatim — the control measuring the verbatim primitive with no mutation.
    private static final class Passthrough implements JsonTransform
    {
        private JsonController upstream;
        private boolean downstreamVerbatim;
        private int depth;

        private final JsonController mediator = new JsonController()
        {
            @Override
            public void segmentable()
            {
            }

            @Override
            public void verbatim()
            {
                downstreamVerbatim = true;
            }

            @Override
            public void consumed(
                int sourceBytes)
            {
                upstream.consumed(sourceBytes);
            }
        };

        @Override
        public JsonPipeline.Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            upstream = control;
            JsonPipeline.Status status;
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                depth++;
                status = sink.transform(mediator, source, forward(downstreamVerbatim, event));
                break;
            case END_OBJECT:
            case END_ARRAY:
                depth--;
                JsonPipeline.Status downstream = sink.transform(mediator, source, forward(downstreamVerbatim, event));
                status = downstream == JsonPipeline.Status.REJECTED ? JsonPipeline.Status.REJECTED
                    : depth == 0 ? JsonPipeline.Status.COMPLETED
                    : downstream;
                break;
            default:
                status = sink.transform(mediator, source, forward(downstreamVerbatim, event));
                break;
            }
            return status;
        }

        @Override
        public void reset()
        {
            downstreamVerbatim = false;
            depth = 0;
        }
    }

    // Drops a named top-level field via a single source.skipValue() on the matched KEY_NAME; allocation-free
    // per op (the mediating controller is a reused field, the drop key a constant).
    private static final class Skip implements JsonTransform
    {
        private final String dropKey;

        private JsonController upstream;
        private boolean downstreamVerbatim;
        private int depth;

        private final JsonController mediator = new JsonController()
        {
            @Override
            public void segmentable()
            {
            }

            @Override
            public void verbatim()
            {
                downstreamVerbatim = true;
            }

            @Override
            public void consumed(
                int sourceBytes)
            {
                upstream.consumed(sourceBytes);
            }
        };

        private Skip(
            String dropKey)
        {
            this.dropKey = dropKey;
        }

        @Override
        public JsonPipeline.Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            upstream = control;
            JsonPipeline.Status status;
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                depth++;
                status = sink.transform(mediator, source, forward(downstreamVerbatim, event));
                break;
            case END_OBJECT:
            case END_ARRAY:
                depth--;
                JsonPipeline.Status downstream = sink.transform(mediator, source, forward(downstreamVerbatim, event));
                status = downstream == JsonPipeline.Status.REJECTED ? JsonPipeline.Status.REJECTED
                    : depth == 0 ? JsonPipeline.Status.COMPLETED
                    : downstream;
                break;
            case KEY_NAME:
                if (depth == 1 && contentEquals(dropKey, source.getStringView()))
                {
                    source.skipValue();
                    status = JsonPipeline.Status.ADVANCED;
                }
                else
                {
                    status = sink.transform(mediator, source, forward(downstreamVerbatim, event));
                }
                break;
            default:
                status = sink.transform(mediator, source, forward(downstreamVerbatim, event));
                break;
            }
            return status;
        }

        @Override
        public void reset()
        {
            downstreamVerbatim = false;
            depth = 0;
        }
    }

    // Injects a constant member before a named top-level key as structured events, then forwards the original
    // key verbatim; allocation-free per op (the injection sources and controllers are reused fields, the key
    // and value constants).
    private static final class Inject implements JsonTransform
    {
        private final String beforeKey;

        private JsonController upstream;
        private boolean downstreamVerbatim;
        private int depth;

        private final InjectSource keySource;
        private final InjectSource valueSource;

        private final JsonController mediator = new JsonController()
        {
            @Override
            public void segmentable()
            {
            }

            @Override
            public void verbatim()
            {
                downstreamVerbatim = true;
            }

            @Override
            public void consumed(
                int sourceBytes)
            {
                upstream.consumed(sourceBytes);
            }
        };

        // injected values carry no source bytes, so a consumed() report from rendering them must not advance
        // the parser's cursor
        private final JsonController injectControl = new JsonController()
        {
            @Override
            public void segmentable()
            {
            }

            @Override
            public void verbatim()
            {
            }

            @Override
            public void consumed(
                int sourceBytes)
            {
            }
        };

        private Inject(
            String beforeKey,
            CharSequence injectKey,
            CharSequence injectValue)
        {
            this.beforeKey = beforeKey;
            this.keySource = new InjectSource(injectKey);
            this.valueSource = new InjectSource(injectValue);
        }

        @Override
        public JsonPipeline.Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            upstream = control;
            JsonPipeline.Status status;
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                depth++;
                status = sink.transform(mediator, source, forward(downstreamVerbatim, event));
                break;
            case END_OBJECT:
            case END_ARRAY:
                depth--;
                JsonPipeline.Status downstream = sink.transform(mediator, source, forward(downstreamVerbatim, event));
                status = downstream == JsonPipeline.Status.REJECTED ? JsonPipeline.Status.REJECTED
                    : depth == 0 ? JsonPipeline.Status.COMPLETED
                    : downstream;
                break;
            case KEY_NAME:
                if (depth == 1 && contentEquals(beforeKey, source.getStringView()))
                {
                    // the generator's state is current from the verbatim steps applied so far, so the injected
                    // member separates correctly with no per-op allocation in the transform
                    sink.transform(injectControl, keySource, JsonEvent.KEY_NAME);
                    sink.transform(injectControl, valueSource, JsonEvent.VALUE_NUMBER);
                }
                status = sink.transform(mediator, source, forward(downstreamVerbatim, event));
                break;
            default:
                status = sink.transform(mediator, source, forward(downstreamVerbatim, event));
                break;
            }
            return status;
        }

        @Override
        public void reset()
        {
            downstreamVerbatim = false;
            depth = 0;
        }
    }

    // Supplies an injected key or number value: getStringView() returns the constant injected text. The
    // injected events are fed structurally (not verbatim), so the generator tracks state from them directly —
    // event() is never consulted for an injected source.
    private static final class InjectSource implements JsonSource
    {
        private final CharSequence text;

        private InjectSource(
            CharSequence text)
        {
            this.text = text;
        }

        @Override
        public CharSequence getStringView()
        {
            return text;
        }

        @Override
        public boolean deferredBytes()
        {
            return false;
        }

        @Override
        public String getString()
        {
            return text.toString();
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isIntegralNumber()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonLocation getLocation()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectBufferEx getSegment()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonVerbatim getVerbatim(
            int limit)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void skipValue()
        {
            throw new UnsupportedOperationException();
        }
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonMutateBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
