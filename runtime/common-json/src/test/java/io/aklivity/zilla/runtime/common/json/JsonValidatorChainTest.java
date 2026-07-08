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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.agrona.io.DirectBufferInputStreamEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonValidatorChainTest
{
    private static final String OBJECT_SCHEMA =
        "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}," +
        "\"name\":{\"type\":\"string\"}},\"required\":[\"id\",\"name\"]}";

    private final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

    @Test
    void shouldForwardFullStreamWhenValid()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .into(JsonEx.createSink(gen));

        Status status = run(pipeline, "{\"id\":1,\"name\":\"x\"} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"id\":1,\"name\":\"x\"} ", output(gen));
    }

    @Test
    void shouldValidateWindowFragmentedStringAgainstPattern()
    {
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\",\"pattern\":\"^a+$\"}");
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .into(JsonEx.createSink(gen));
        pipeline.reset();

        // a string long enough to fragment across 8-byte windows; ^a+$ holds only when the whole value is
        // reassembled, so the validator must decline the fragments and match the complete value
        byte[] bytes = ("\"" + "a".repeat(40) + "\" ").getBytes(UTF_8);
        Status status = feedWindowed(pipeline, bytes, 8);

        assertEquals(Status.COMPLETED, status);
    }

    @Test
    void shouldRejectWindowFragmentedStringViolatingPattern()
    {
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\",\"pattern\":\"^a+$\"}");
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .into(JsonEx.createSink(gen));
        pipeline.reset();

        // a 'b' buried deep in the value violates ^a+$, detectable only once the whole value is reassembled;
        // validating any single fragment would miss it
        byte[] bytes = ("\"" + "a".repeat(30) + "b" + "a".repeat(9) + "\" ").getBytes(UTF_8);
        Status status = feedWindowed(pipeline, bytes, 8);

        assertEquals(Status.REJECTED, status);
    }

    private static Status feedWindowed(
        JsonPipeline pipeline,
        byte[] bytes,
        int step)
    {
        int progress = 0;
        int limit = 0;
        Status status = Status.STARVED;
        int guard = 0;
        while (guard++ < 100_000 && status == Status.STARVED)
        {
            limit = Math.min(limit + step, bytes.length);
            boolean last = limit >= bytes.length;
            status = pipeline.transform(new UnsafeBufferEx(bytes), progress, limit, last);
            progress = limit - pipeline.remaining();
        }
        return status;
    }

    @Test
    void shouldValidateFullStreamWhileProjectingSubset()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .transform(JsonTransforms.projector(List.of("/id")))
            .into(JsonEx.createSink(gen));

        Status status = run(pipeline, "{\"id\":1,\"name\":\"x\"} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"id\":1} ", output(gen));
    }

    @Test
    void shouldRejectMissingRequiredAfterEmitting()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .transform(JsonTransforms.projector(List.of("/id")))
            .into(JsonEx.createSink(gen));

        Status status = run(pipeline, "{\"id\":1} ");

        assertEquals(Status.REJECTED, status);
        assertEquals("{\"id\":1}", output(gen));
    }

    @Test
    void shouldRejectTypeViolation()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .into(JsonEx.createSink(gen));

        Status status = run(pipeline, "{\"id\":\"x\",\"name\":\"y\"} ");

        assertEquals(Status.REJECTED, status);
    }

    @Test
    void shouldResumeMidValueWithoutReset()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .into(JsonEx.createSink(gen));

        byte[] bytes = "{\"id\":1,\"name\":\"x\"} ".getBytes(UTF_8);
        UnsafeBufferEx in = new UnsafeBufferEx(bytes);

        pipeline.reset();
        assertEquals(Status.STARVED, pipeline.transform(in, 0, 8, false));
        Status status = pipeline.transform(in, 8, bytes.length);

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"id\":1,\"name\":\"x\"} ", output(gen));
    }

    @Test
    void shouldReuseChainAcrossValues()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = JsonEx.createGenerator();
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .into(JsonEx.createSink(gen));

        gen.wrap(buffer, 0, buffer.capacity());
        assertEquals(Status.COMPLETED, run(pipeline, "{\"id\":1,\"name\":\"a\"} "));
        assertEquals("{\"id\":1,\"name\":\"a\"} ", output(gen));

        gen.wrap(buffer, 0, buffer.capacity());
        assertEquals(Status.REJECTED, run(pipeline, "{\"id\":2} "));
    }

    // Reproduces a bug found via binding-mcp-http: a mediating stage positioned after the validator (e.g.
    // McpHttpToolResult, wrapping a validated value in a larger envelope) can inject more content once the
    // real value closes and have that injection alone be what backs up against the destination — so the
    // downstream status for the closing event is SUSPENDED even though the validated value itself is now
    // schema-VALID. verdictStatus() must let a SUSPENDED downstream outrank a VALID verdict; getting this
    // wrong reports COMPLETED while the mediator's own write is still pending, silently truncating
    // whatever it still owed downstream (the pipeline caller sees "done" and never resumes the drain).
    @Test
    void shouldSuspendNotCompleteWhenInjectorSuspendsAtValidVerdict()
    {
        JsonSchema schema = JsonSchema.of("{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}}}");
        // enough room for the validated "{\"id\":1}" (8 bytes) plus headroom, but not enough left over for
        // the injector's own additional value on top of it
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, 20);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .transform(new Injector())
            .into(JsonEx.createSink(gen));

        Status status = run(pipeline, "{\"id\":1}");

        assertEquals(Status.SUSPENDED, status);
    }

    // Forwards every event unchanged; once the real root object closes, also injects one more value too
    // large for the remaining destination room, so the combined status for that closing event is SUSPENDED
    // even though the value forwarded just fine on its own.
    private static final class Injector implements JsonTransform
    {
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

        private int depth;

        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            switch (event)
            {
            case START_OBJECT:
                depth++;
                break;
            case END_OBJECT:
                depth--;
                break;
            default:
                break;
            }
            Status status = sink.transform(control, source, event);
            if (status != Status.SUSPENDED && status != Status.REJECTED &&
                event == JsonEvent.END_OBJECT && depth == 0)
            {
                status = sink.transform(injectControl, new FixedSource("x".repeat(50)), JsonEvent.VALUE_STRING);
            }
            return status;
        }

        @Override
        public void reset()
        {
            depth = 0;
        }
    }

    // Supplies a fixed injected scalar value with no real source bytes to advance.
    private static final class FixedSource implements JsonSource
    {
        private final CharSequence text;

        private FixedSource(
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

    @Test
    void shouldForwardThroughDefaultResetTransform()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonTransform passthrough = (control, source, event, sink) -> sink.transform(control, source, event);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(passthrough)
            .into(JsonEx.createSink(gen));

        Status status = run(pipeline, "{\"id\":1} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"id\":1} ", output(gen));
    }

    @Test
    void shouldPullValidDocumentThroughNewParser()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonParser parser = schema.newParser(true, parserFor("{\"id\":1,\"name\":\"x\"} "));

        int events = 0;
        while (parser.hasNext())
        {
            parser.next();
            events++;
        }

        assertEquals(6, events);
    }

    @Test
    void shouldThrowFromNewParserOnInvalid()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonParser parser = schema.newParser(true, parserFor("{\"id\":\"x\",\"name\":\"y\"} "));

        assertThrows(JsonValidationException.class, () ->
        {
            while (parser.hasNext())
            {
                parser.next();
            }
        });
    }

    @Test
    void shouldValidateValueFromNewParser()
    {
        JsonSchema schema = JsonSchema.of("{\"type\":\"integer\",\"minimum\":0}");
        JsonParser parser = schema.newParser(true,
            JsonEx.createParserFactory(Map.of()).createParser(streamFor("7 ")));

        Event event = parser.next();

        assertEquals(Event.VALUE_NUMBER, event);
        assertEquals(7, parser.getInt());
    }

    private String output(
        JsonGeneratorEx gen)
    {
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }

    private static Status run(
        JsonPipeline pipeline,
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        pipeline.reset();
        return pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);
    }

    private static JsonParser parserFor(
        String text)
    {
        return JsonEx.createParser(streamFor(text));
    }

    private static InputStream streamFor(
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        return in;
    }
}
