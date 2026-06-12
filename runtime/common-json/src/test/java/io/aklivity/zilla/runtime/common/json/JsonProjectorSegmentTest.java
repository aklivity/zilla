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
            .into(JsonSink.of(gen, JsonSink.Delivery.SEGMENTABLE));

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
            .into(JsonSink.of(gen));

        Status status = run(pipeline, "{\"a\":{ \"b\" : 1 },\"z\":9} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"a\":{\"b\":1}}", output(gen));
    }

    @Test
    void shouldSegmentRootIdentityWhenSinkOptsIn()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("")))
            .into(JsonSink.of(gen, JsonSink.Delivery.SEGMENTABLE));

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
            .into(JsonSink.of(gen, JsonSink.Delivery.SEGMENTABLE));

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
            .into(JsonSink.of(gen, JsonSink.Delivery.SEGMENTABLE));

        Status status = run(pipeline, "{\"items\":[{ \"id\" : 1 },{\"id\":2}]} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"items\":[{ \"id\" : 1 }]}", output(gen));
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
