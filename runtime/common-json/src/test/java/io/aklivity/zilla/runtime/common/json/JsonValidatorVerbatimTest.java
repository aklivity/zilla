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

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonValidatorVerbatimTest
{
    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}";

    @Test
    void shouldReportIdentityForValidatingPipeline()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator())
            .into(JsonEx.createSink(gen));

        assertTrue(pipeline.identity());
    }

    @Test
    void shouldValidateThenForwardVerbatimPreservingWhitespace()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator())
            .into(JsonEx.createSink(gen));

        // the regression: through the validator a structured replay canonicalizes to {"id":"123","status":"OK"}
        byte[] bytes = "{\"id\": \"123\", \"status\": \"OK\"}".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"id\": \"123\", \"status\": \"OK\"}", output(gen, buffer));
    }

    @Test
    void shouldValidateThenForwardVerbatimAcrossFrames()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator())
            .into(JsonEx.createSink(gen));

        // the separator consumed during end-of-window lookahead must be drained through the validator's
        // flush before the window is replaced
        byte[] first = "{\"id\": \"123\", ".getBytes(UTF_8);
        byte[] second = "\"n\": 2} ".getBytes(UTF_8);

        pipeline.reset();
        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBufferEx(first), 0, first.length, false));
        Status status = pipeline.transform(new UnsafeBufferEx(second), 0, second.length);

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"id\": \"123\", \"n\": 2} ", output(gen, buffer));
    }

    @Test
    void shouldForwardTrailingNewlineVerbatim()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator())
            .into(JsonEx.createSink(gen));

        String json = "{\n    \"id\": \"123\"\n}\n";
        byte[] bytes = json.getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        assertEquals(json, output(gen, buffer));
    }

    @Test
    void shouldForwardTrailingNewlineThroughBoundedOutput()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[256]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator())
            .into(JsonEx.createSink(gen));

        // the trailing run fits a fresh output window but not the space left after the body, so the pipeline
        // must report SUSPENDED (never COMPLETED with trailing pending), let the caller drain, then complete the
        // trailing splice against a fresh window
        String json = "{ \"id\" : \"123\" }\n\n\n";
        assertEquals(json, bounded(pipeline, gen, output, json, 16));
    }

    @Test
    void shouldForwardMultipleDocumentsVerbatimPreservingInterDocumentBytes()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator())
            .into(JsonEx.createSink(gen));

        String first = "{ \"id\" : \"1\" }\n";
        String second = "{ \"id\" : \"2\" }\n";

        byte[] firstBytes = first.getBytes(UTF_8);
        byte[] secondBytes = second.getBytes(UTF_8);

        pipeline.reset();
        Status one = pipeline.transform(new UnsafeBufferEx(firstBytes), 0, firstBytes.length);
        assertEquals(Status.COMPLETED, one);
        String oneOut = output(gen, buffer);

        pipeline.reset();
        gen.wrap(buffer, 0, buffer.capacity());
        Status two = pipeline.transform(new UnsafeBufferEx(secondBytes), 0, secondBytes.length);
        assertEquals(Status.COMPLETED, two);
        String twoOut = output(gen, buffer);

        assertEquals(first, oneOut);
        assertEquals(second, twoOut);
    }

    @Test
    void shouldRejectInvalidUnderVerbatim()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator())
            .into(JsonEx.createSink(gen));

        // id must be a string; a number violates the schema
        byte[] bytes = "{\"id\": 123}".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(Status.REJECTED, status);
    }

    @Test
    void shouldValidateThenForwardVerbatimThroughBoundedOutput()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[256]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of("{\"type\":\"object\"}").validator())
            .into(JsonEx.createSink(gen));

        // a validated document whose verbatim form far exceeds the output bound: the run drains in pieces via
        // SUSPENDED/resume through the validator, reassembling byte-identical with whitespace preserved
        String json = "{ \"id\" : \"123\", \"items\" : [ \"aaaaaaaa\", \"bbbbbbbb\", \"cccccccc\" ], \"ok\" : true }";
        assertEquals(json + " ", chunked(pipeline, gen, output, json, 16));
    }

    private static String output(
        JsonGeneratorEx gen,
        MutableDirectBufferEx buffer)
    {
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }

    private static String bounded(
        JsonPipeline pipeline,
        JsonGeneratorEx gen,
        MutableDirectBufferEx output,
        String json,
        int bound)
    {
        byte[] bytes = json.getBytes(UTF_8);
        UnsafeBufferEx in = new UnsafeBufferEx(bytes);
        StringBuilder result = new StringBuilder();
        pipeline.reset();
        gen.wrap(output, 0, bound);
        int suspends = 0;
        int guard = 0;
        Status status;
        do
        {
            status = pipeline.transform(in, 0, bytes.length);
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

    private static String chunked(
        JsonPipeline pipeline,
        JsonGeneratorEx gen,
        MutableDirectBufferEx output,
        String json,
        int bound)
    {
        byte[] bytes = (json + " ").getBytes(UTF_8);
        UnsafeBufferEx in = new UnsafeBufferEx(bytes);
        StringBuilder result = new StringBuilder();
        pipeline.reset();
        gen.wrap(output, 0, bound);
        int suspends = 0;
        int guard = 0;
        Status status;
        do
        {
            status = pipeline.transform(in, 0, bytes.length);
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
