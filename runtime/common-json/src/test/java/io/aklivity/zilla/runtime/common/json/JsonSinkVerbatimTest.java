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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonSinkVerbatimTest
{
    private static final Map<String, ?> VERBATIM = Map.of(JsonSink.DELIVERY, JsonSink.Delivery.VERBATIM);

    @Test
    void shouldWriteWholeDocumentVerbatim()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(gen, VERBATIM));

        byte[] bytes = "{ \"a\" : [1, 2], \"b\" : 3 } ".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        assertEquals("{ \"a\" : [1, 2], \"b\" : 3 }", output(gen, buffer));
    }

    @Test
    void shouldPreserveInsignificantWhitespaceVerbatim()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(gen, VERBATIM));

        // the exact regression: a structured replay would canonicalize this to {"id":"123","status":"OK"}
        byte[] bytes = "{\"id\": \"123\", \"status\": \"OK\"}".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"id\": \"123\", \"status\": \"OK\"}", output(gen, buffer));
    }

    @Test
    void shouldWriteTopLevelScalarVerbatim()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(gen, VERBATIM));

        byte[] bytes = "  -2.50e3 ".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        // leading document whitespace is part of the verbatim run; the trailing space is not (the value
        // completes at the lexeme end)
        assertEquals("  -2.50e3", output(gen, buffer));
    }

    @Test
    void shouldWriteVerbatimAcrossInputFrames()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(gen, VERBATIM));

        byte[] first = "{\"a\": 1, ".getBytes(UTF_8);
        byte[] second = "\"b\": 2} ".getBytes(UTF_8);

        pipeline.reset();
        assertEquals(Status.STARVED, pipeline.feed(new UnsafeBuffer(first), 0, first.length, false));
        Status status = pipeline.feed(new UnsafeBuffer(second), 0, second.length);

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"a\": 1, \"b\": 2}", output(gen, buffer));
    }

    @Test
    void shouldChunkVerbatimThroughBoundedOutput()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBuffer(new byte[256]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(gen, VERBATIM));

        // a document whose verbatim form far exceeds the output bound: the run drains in pieces via
        // SUSPENDED/resume against a re-wrapped buffer, reassembling byte-identical
        String json = "{ \"items\" : [ \"aaaaaaaa\", \"bbbbbbbb\", \"cccccccc\", \"dddddddd\" ], \"ok\" : true }";
        assertEquals(json, chunked(pipeline, gen, output, json, 16));
    }

    private static String output(
        JsonGeneratorEx gen,
        MutableDirectBuffer buffer)
    {
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }

    private static String chunked(
        JsonPipeline pipeline,
        JsonGeneratorEx gen,
        MutableDirectBuffer output,
        String json,
        int bound)
    {
        byte[] bytes = (json + " ").getBytes(UTF_8);
        UnsafeBuffer in = new UnsafeBuffer(bytes);
        StringBuilder result = new StringBuilder();
        pipeline.reset();
        gen.wrap(output, 0, bound);
        int suspends = 0;
        int guard = 0;
        Status status;
        do
        {
            status = pipeline.feed(in, 0, bytes.length);
            byte[] chunk = new byte[gen.length()];
            output.getBytes(0, chunk);
            result.append(new String(chunk, UTF_8));
            if (status == Status.SUSPENDED)
            {
                suspends++;
                gen.wrap(output, 0, bound);
            }
            guard++;
        }
        while (status == Status.SUSPENDED && guard < 10_000);
        assertEquals(Status.COMPLETED, status);
        assertTrue(suspends >= 1, "expected at least one SUSPENDED chunk boundary");
        return result.toString();
    }
}
