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

import java.util.Map;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonValidatorVerbatimTest
{
    private static final Map<String, ?> VERBATIM = Map.of(JsonSink.DELIVERY, JsonSink.Delivery.VERBATIM);
    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}";

    @Test
    void shouldValidateThenForwardVerbatimPreservingWhitespace()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator())
            .into(JsonEx.createSink(gen, VERBATIM));

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
            .into(JsonEx.createSink(gen, VERBATIM));

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
            .into(JsonEx.createSink(gen, VERBATIM));

        // id must be a string; a number violates the schema
        byte[] bytes = "{\"id\": 123}".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.REJECTED, status);
    }

    private static String output(
        JsonGeneratorEx gen,
        MutableDirectBuffer buffer)
    {
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }
}
