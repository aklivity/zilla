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

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonProjectorSegmentTest
{
    private final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

    @Test
    void shouldSegmentKeptSubtreeWhenSinkOptsIn()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/a")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));

        Status status = run(pipeline, "{\"a\":{ \"b\" : 1 },\"z\":9} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"a\":{ \"b\" : 1 }} ", output(gen));
    }

    @Test
    void shouldNormalizeWhenSinkDoesNotOptIn()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/a")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));

        Status status = run(pipeline, "{\"a\":{ \"b\" : 1 },\"z\":9} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"a\":{\"b\":1}}", output(gen));
    }

    @Test
    void shouldCanonicalizeLeafStringEscapesWhenStructured()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));

        // structured delivery renders each leaf string from its decoded value, so escaping is canonical:
        // the redundant A collapses to A. Byte-verbatim preservation is the segmented path's job.
        Status status = run(pipeline, "{ \"a\" : \"x\\u0041y\" } ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"a\":\"xAy\"}", output(gen));
    }

    @Test
    void shouldPreserveLeafNumberFormVerbatimWhenStructured()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));

        Status status = run(pipeline, "{ \"a\" : 1.0e2 } ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"a\":1.0e2}", output(gen));
    }

    @Test
    void shouldSegmentRootIdentityWhenSinkOptsIn()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));

        Status status = run(pipeline, "{ \"a\" : 1 } ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{ \"a\" : 1 } ", output(gen));
    }

    @Test
    void shouldStaySegmentFreeAndValidWithValidatorUpstream()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonTransform decliner = (control, source, event, sink) -> sink.transform(() ->
        {
        }, source, event);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(decliner)
            .transform(JsonEx.projector(List.of("/a")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));

        Status status = run(pipeline, "{\"a\":{ \"b\" : 1 },\"z\":9} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"a\":{\"b\":1}} ", output(gen));
    }

    @Test
    void shouldHandleArrayElementSegment()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/items/0")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));

        Status status = run(pipeline, "{\"items\":[{ \"id\" : 1 },{\"id\":2}]} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"items\":[{ \"id\" : 1 }]} ", output(gen));
    }

    @Test
    void shouldDecodeKeptScalarLeafFragmentedAcrossWindows()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/a")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));

        // a retained scalar leaf far larger than the input window: it fragments across windows and the
        // structured sink rejoins the fragments canonically; the dropped sibling /b also fragments and
        // must not be emitted
        String value = "x".repeat(40);
        String json = "{\"a\":\"" + value + "\",\"b\":\"" + "y".repeat(40) + "\"} ";
        byte[] bytes = json.getBytes(UTF_8);
        pipeline.reset();

        int progress = 0;
        int limit = 0;
        Status status = Status.STARVED;
        int guard = 0;
        while (guard++ < 10_000)
        {
            limit = Math.min(limit + 8, bytes.length);
            boolean last = limit >= bytes.length;
            status = pipeline.transform(new UnsafeBufferEx(bytes), progress, limit, last);
            progress = limit - pipeline.remaining();
            if (status == Status.COMPLETED || status == Status.REJECTED)
            {
                break;
            }
        }

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"a\":\"" + value + "\"}", output(gen));
    }

    @Test
    void shouldStreamKeptScalarLeafSegmentedAcrossInputAndOutputBounds()
    {
        // a retained scalar leaf far larger than both the input window and the output bound, whose JSON
        // string content carries escape sequences and a multibyte character: it streams verbatim through
        // projector -> SEGMENTABLE sink -> escape-mode generator, fragmenting across input (STARVED) and
        // output (SUSPENDED). The escaped chunked result must equal the escaped whole-shot result, and
        // when read back as JSON string content must decode to the projected plain document (kept /a,
        // dropped sibling /b excluded), with neither a multibyte character nor an escape sequence split.
        String leafContent = ("A\\\"é\\\\B\\t" + "z".repeat(40)).repeat(4);
        String json = "{\"a\":\"" + leafContent + "\",\"b\":\"" + "y".repeat(120) + "\"} ";

        String escaped = drive(json, 16, 7);
        String whole = drive(json, 4096, 4096);

        assertEquals(whole, escaped);
        assertEquals(plain(json), decodeJsonString(escaped));
    }

    // Drives the project(/a) -> SEGMENTABLE -> escape-mode pipeline with a bounded output (outBound) and a
    // bounded input window (inStep), accumulating each drained output chunk; reconstructs the full escaped
    // output across STARVED (input back-pressure) and SUSPENDED (output back-pressure).
    private static String drive(
        String json,
        int outBound,
        int inStep)
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[outBound]);
        JsonGeneratorEx gen = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true));
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/a")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));

        byte[] bytes = json.getBytes(UTF_8);
        pipeline.reset();
        gen.wrap(out, 0, outBound);

        StringBuilder result = new StringBuilder();
        int progress = 0;
        int limit = 0;
        Status status = Status.STARVED;
        int guard = 0;
        while (guard++ < 1_000_000)
        {
            if (status != Status.SUSPENDED)
            {
                limit = Math.min(limit + inStep, bytes.length);
            }
            boolean last = limit >= bytes.length;
            status = pipeline.transform(new UnsafeBufferEx(bytes), progress, limit, last);
            byte[] chunk = new byte[gen.length()];
            out.getBytes(0, chunk);
            result.append(new String(chunk, UTF_8));
            progress = limit - pipeline.remaining();
            if (status == Status.SUSPENDED)
            {
                gen.wrap(out, 0, outBound);
            }
            else if (status == Status.COMPLETED || status == Status.REJECTED)
            {
                break;
            }
        }

        assertEquals(Status.COMPLETED, status);
        return result.toString();
    }

    @Test
    void shouldStreamMultiLeafResourceLikeBinding()
    {
        String status = "z".repeat(300);
        String json = "{\"id\":\"12345\",\"status\":\"" + status + "\",\"total\":42.5," +
            "\"customer\":\"acme\",\"createdAt\":\"2026-01-01\"} ";

        String escaped = driveMulti(json, 16, 7);
        String whole = driveMulti(json, 4096, 4096);

        assertEquals(whole, escaped);
        assertEquals("{\"id\":\"12345\",\"status\":\"" + status + "\",\"total\":42.5} ",
            decodeJsonString(escaped));
    }

    private static String driveMulti(
        String json,
        int outBound,
        int inStep)
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[outBound]);
        JsonGeneratorEx gen = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true));
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/id", "/status", "/total")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));

        byte[] bytes = json.getBytes(UTF_8);
        pipeline.reset();
        gen.wrap(out, 0, outBound);

        StringBuilder result = new StringBuilder();
        int progress = 0;
        int limit = 0;
        Status status = Status.STARVED;
        int guard = 0;
        while (guard++ < 1_000_000)
        {
            if (status != Status.SUSPENDED)
            {
                limit = Math.min(limit + inStep, bytes.length);
            }
            boolean last = limit >= bytes.length;
            status = pipeline.transform(new UnsafeBufferEx(bytes), progress, limit, last);
            byte[] chunk = new byte[gen.length()];
            out.getBytes(0, chunk);
            result.append(new String(chunk, UTF_8));
            progress = limit - pipeline.remaining();
            if (status == Status.SUSPENDED)
            {
                gen.wrap(out, 0, outBound);
            }
            else if (status == Status.COMPLETED || status == Status.REJECTED)
            {
                break;
            }
        }

        assertEquals(Status.COMPLETED, status);
        return result.toString();
    }

    // The project(/a) plain (non-escaped) rendering, whole-shot, for the JSON-in-JSON round-trip check.
    private static String plain(
        String json)
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[4096]);
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(out, 0, out.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/a")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));
        byte[] bytes = json.getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals(Status.COMPLETED, status);
        byte[] rendered = new byte[gen.length()];
        out.getBytes(0, rendered);
        return new String(rendered, UTF_8);
    }

    private static String decodeJsonString(
        String content)
    {
        String document = "{\"v\":\"" + content + "\"}";
        JsonParser parser = JsonEx.createParser(new ByteArrayInputStream(document.getBytes(UTF_8)));
        String result = null;
        while (parser.hasNext())
        {
            if (parser.next() == JsonParser.Event.VALUE_STRING)
            {
                result = parser.getString();
            }
        }
        return result;
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
}
