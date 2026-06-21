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

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonValidatorVerbatimTest
{
    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}";

    @Test
    void shouldValidateThenForwardVerbatimPreservingWhitespace()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator())
            .into(JsonEx.createSink(gen));

        // the regression: through the validator a structured replay canonicalizes to {"id":"123","status":"OK"}
        byte[] bytes = "{\"id\": \"123\", \"status\": \"OK\"}".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"id\": \"123\", \"status\": \"OK\"}", output(gen, buffer));
    }

    @Test
    void shouldValidateThenForwardVerbatimAcrossFrames()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator())
            .into(JsonEx.createSink(gen));

        // the separator consumed during end-of-window lookahead must be drained through the validator's
        // flush before the window is replaced
        byte[] first = "{\"id\": \"123\", ".getBytes(UTF_8);
        byte[] second = "\"n\": 2} ".getBytes(UTF_8);

        pipeline.reset();
        assertEquals(Status.STARVED, pipeline.feed(new UnsafeBuffer(first), 0, first.length, false));
        Status status = pipeline.feed(new UnsafeBuffer(second), 0, second.length);

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"id\": \"123\", \"n\": 2}", output(gen, buffer));
    }

    @Test
    void shouldRejectInvalidUnderVerbatim()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator())
            .into(JsonEx.createSink(gen));

        // id must be a string; a number violates the schema
        byte[] bytes = "{\"id\": 123}".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.REJECTED, status);
    }

    @Test
    void shouldValidateThenForwardVerbatimThroughBoundedOutput()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBuffer(new byte[256]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of("{\"type\":\"object\"}").validator())
            .into(JsonEx.createSink(gen));

        // a validated document whose verbatim form far exceeds the output bound: the run drains in pieces via
        // SUSPENDED/resume through the validator, reassembling byte-identical with whitespace preserved
        String json = "{ \"id\" : \"123\", \"items\" : [ \"aaaaaaaa\", \"bbbbbbbb\", \"cccccccc\" ], \"ok\" : true }";
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
