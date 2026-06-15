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

import java.util.List;
import java.util.Map;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonProjectorSegmentTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);

    @Test
    void shouldSegmentKeptSubtreeWhenSinkOptsIn()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/a")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));

        Status status = run(pipeline, "{\"a\":{ \"b\" : 1 },\"z\":9} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"a\":{ \"b\" : 1 }}", output(gen));
    }

    @Test
    void shouldNormalizeWhenSinkDoesNotOptIn()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/a")))
            .into(JsonEx.createSink(gen));

        Status status = run(pipeline, "{\"a\":{ \"b\" : 1 },\"z\":9} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"a\":{\"b\":1}}", output(gen));
    }

    @Test
    void shouldPreserveLeafStringEscapesVerbatimWhenStructured()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("")))
            .into(JsonEx.createSink(gen));

        // structured delivery normalizes structure (whitespace) but preserves each leaf value's bytes,
        // so the original A escape survives rather than collapsing to A
        Status status = run(pipeline, "{ \"a\" : \"x\\u0041y\" } ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"a\":\"x\\u0041y\"}", output(gen));
    }

    @Test
    void shouldPreserveLeafNumberFormVerbatimWhenStructured()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("")))
            .into(JsonEx.createSink(gen));

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
        assertEquals("{ \"a\" : 1 }", output(gen));
    }

    @Test
    void shouldStaySegmentFreeAndValidWithValidatorUpstream()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonTransform decliner = (control, source, event, sink) -> sink.feed(() ->
        {
        }, source, event);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(decliner)
            .transform(JsonEx.projector(List.of("/a")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));

        Status status = run(pipeline, "{\"a\":{ \"b\" : 1 },\"z\":9} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"a\":{\"b\":1}}", output(gen));
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
        assertEquals("{\"items\":[{ \"id\" : 1 }]}", output(gen));
    }

    @Test
    void shouldDecodeKeptScalarLeafFragmentedAcrossWindows()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/a")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.DECODED)));

        // a retained scalar leaf far larger than the input window: it fragments across windows and the
        // DECODED sink rejoins the fragments; the dropped sibling /b also fragments and must not be emitted
        String value = "x".repeat(40);
        String json = "{\"a\":\"" + value + "\",\"b\":\"" + "y".repeat(40) + "\"} ";
        byte[] bytes = json.getBytes(UTF_8);
        pipeline.reset();

        int committed = 0;
        int offset = 0;
        Status status = Status.STARVED;
        int guard = 0;
        while (guard++ < 10_000)
        {
            offset = Math.min(offset + 8, bytes.length);
            boolean last = offset >= bytes.length;
            status = pipeline.feed(new UnsafeBuffer(bytes), committed, offset - committed, last);
            committed = (int) pipeline.position();
            if (status == Status.COMPLETED || status == Status.REJECTED)
            {
                break;
            }
        }

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"a\":\"" + value + "\"}", output(gen));
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
        return pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);
    }
}
