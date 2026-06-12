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
package io.aklivity.zilla.runtime.common.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.StreamingJson;

class JsonPipelineChunkingTest
{
    private static final int BOUND = 32;

    @Test
    void shouldChunkLargeArrayThroughTerminalSink()
    {
        JsonGeneratorEx generator = StreamingJson.createGenerator();
        MutableDirectBuffer output = new UnsafeBuffer(new byte[128]);
        JsonPipeline pipeline = StreamingJson.createParser().stream()
            .into(JsonSink.of(generator));

        String json = "[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19]";
        assertEquals(json, chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldChunkLargeObjectThroughTerminalSink()
    {
        JsonGeneratorEx generator = StreamingJson.createGenerator();
        MutableDirectBuffer output = new UnsafeBuffer(new byte[128]);
        JsonPipeline pipeline = StreamingJson.createParser().stream()
            .into(JsonSink.of(generator));

        String json = "{\"k0\":0,\"k1\":1,\"k2\":2,\"k3\":3,\"k4\":4,\"k5\":5,\"k6\":6,\"k7\":7,\"k8\":8,\"k9\":9}";
        assertEquals(json, chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldChunkThroughProjectorTransform()
    {
        JsonGeneratorEx generator = StreamingJson.createGenerator();
        MutableDirectBuffer output = new UnsafeBuffer(new byte[128]);
        JsonPipeline pipeline = StreamingJson.createParser().stream()
            .transform(StreamingJson.projector(List.of("")))
            .into(JsonSink.of(generator));

        String json = "{\"id\":1,\"items\":[10,11,12,13,14,15,16,17,18,19],\"ok\":true}";
        assertEquals(json, chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldChunkThroughValidatorTransform()
    {
        JsonGeneratorEx generator = StreamingJson.createGenerator();
        MutableDirectBuffer output = new UnsafeBuffer(new byte[128]);
        JsonPipeline pipeline = StreamingJson.createParser().stream()
            .transform(JsonSchema.of("{\"type\":\"object\"}").validator())
            .into(JsonSink.of(generator));

        String json = "{\"k0\":0,\"k1\":1,\"k2\":2,\"k3\":3,\"k4\":4,\"k5\":5,\"k6\":6,\"k7\":7,\"k8\":8,\"k9\":9}";
        assertEquals(json, chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldFragmentValueLargerThanBound()
    {
        JsonGeneratorEx generator = StreamingJson.createGenerator();
        MutableDirectBuffer output = new UnsafeBuffer(new byte[256]);
        JsonPipeline pipeline = StreamingJson.createParser().stream()
            .into(JsonSink.of(generator, JsonSink.Delivery.SEGMENTABLE));

        // one top-level array whose verbatim form far exceeds BOUND, delivered as a single segment that
        // must be fragmented across many chunks
        String json = "[\"aaaaaaaa\",\"bbbbbbbb\",\"cccccccc\",\"dddddddd\",\"eeeeeeee\",\"ffffffff\"]";
        assertEquals(json, chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldFragmentValueThroughForwardingTransform()
    {
        JsonGeneratorEx generator = StreamingJson.createGenerator();
        MutableDirectBuffer output = new UnsafeBuffer(new byte[256]);
        JsonTransform passthrough = (control, source, event, sink) -> sink.feed(control, source, event);
        JsonPipeline pipeline = StreamingJson.createParser().stream()
            .transform(passthrough)
            .into(JsonSink.of(generator, JsonSink.Delivery.SEGMENTABLE));

        // the resume cascade must continue the in-flight fragment through the transform stage
        String json = "[\"aaaaaaaa\",\"bbbbbbbb\",\"cccccccc\",\"dddddddd\",\"eeeeeeee\",\"ffffffff\"]";
        assertEquals(json, chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldCompleteWithoutSuspendWhenWithinBound()
    {
        JsonGeneratorEx generator = StreamingJson.createGenerator();
        MutableDirectBuffer output = new UnsafeBuffer(new byte[128]);
        JsonPipeline pipeline = StreamingJson.createParser().stream()
            .into(JsonSink.of(generator));

        byte[] bytes = "[1,2,3] ".getBytes(UTF_8);
        pipeline.reset();
        generator.wrap(output, 0, 128);
        Status status = pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETE, status);
        byte[] out = new byte[generator.length()];
        output.getBytes(0, out);
        assertEquals("[1,2,3]", new String(out, UTF_8));
    }

    // Drives a value through the pipeline with the generator bounded at BOUND, draining and re-targeting
    // (context preserved) on each SUSPENDED, and concatenating the drained chunks into one document.
    private static String chunked(
        JsonPipeline pipeline,
        JsonGeneratorEx generator,
        MutableDirectBuffer output,
        String json)
    {
        byte[] bytes = (json + " ").getBytes(UTF_8);
        UnsafeBuffer in = new UnsafeBuffer(bytes);
        StringBuilder result = new StringBuilder();
        pipeline.reset();
        generator.wrap(output, 0, BOUND);
        int suspends = 0;
        int guard = 0;
        Status status;
        do
        {
            status = pipeline.feed(in, 0, bytes.length);
            byte[] chunk = new byte[generator.length()];
            output.getBytes(0, chunk);
            result.append(new String(chunk, UTF_8));
            if (status == Status.SUSPENDED)
            {
                suspends++;
                generator.wrap(output, 0, BOUND);
            }
            guard++;
        }
        while (status == Status.SUSPENDED && guard < 10_000);
        assertEquals(Status.COMPLETE, status);
        assertTrue(suspends >= 1, "expected at least one SUSPENDED chunk boundary");
        return result.toString();
    }
}
