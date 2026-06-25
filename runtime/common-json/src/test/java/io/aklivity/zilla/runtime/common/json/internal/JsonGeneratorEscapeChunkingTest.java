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

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import jakarta.json.stream.JsonParser;

import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;

class JsonGeneratorEscapeChunkingTest
{
    private final MutableDirectBuffer output = new UnsafeBufferEx(new byte[256]);

    @Test
    void shouldChunkQuoteHeavyStringThroughTinyWindow()
    {
        String json = "{\"text\":\"" + "\\\"".repeat(40) + "\"}";
        assertRoundTrips(json, 8, false);
    }

    @Test
    void shouldChunkControlHeavyStringThroughTinyWindow()
    {
        String json = "{\"text\":\"" + "\\n\\t\\r".repeat(20) + "\"}";
        assertRoundTrips(json, 8, false);
    }

    @Test
    void shouldChunkEscapeHeavyDocumentAcrossManyBounds()
    {
        String json = "{\"a\":\"x\\\"y\\\\z\",\"b\":[\"p\\nq\",\"r\\ts\"],\"c\":1.0e2,\"d\":true}";
        assertRoundTrips(json, 6, false);
        assertRoundTrips(json, 8, false);
        assertRoundTrips(json, 16, false);
        assertRoundTrips(json, 32, false);
    }

    @Test
    void shouldChunkThroughProjectorTransform()
    {
        String json = "{\"keep\":{\"msg\":\"hello \\\"world\\\"\\nbye\"},\"drop\":9}";
        assertRoundTrips(json, 16, true);
    }

    @Test
    void shouldEmbedProjectedSubDocumentAsEscapedStringEndToEnd()
    {
        String json = "{\"keep\":{\"id\":1,\"note\":\"a \\\"b\\\" \\\\ c\\n\"},\"drop\":true}";
        String plain = drive(json, Integer.MAX_VALUE, true, false);
        String escaped = drive(json, 16, true, true);

        String envelope = "{\"contents\":[{\"text\":\"" + escaped + "\"}]}";
        assertEquals(plain, textOf(envelope));
    }

    private void assertRoundTrips(
        String json,
        int bound,
        boolean project)
    {
        String plain = drive(json, Integer.MAX_VALUE, project, false);
        String escaped = drive(json, bound, project, true);
        assertEquals(plain, decodeJsonString(escaped));
    }

    private String drive(
        String json,
        int bound,
        boolean project,
        boolean escape)
    {
        Map<String, ?> config = escape ? Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true) : Map.of();
        JsonGeneratorEx generator = JsonEx.createGenerator(config);
        JsonPipeline pipeline = project
            ? JsonEx.stream(JsonEx.createParser())
                .transform(JsonEx.projector(List.of("/keep")))
                .into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)))
            : JsonEx.stream(JsonEx.createParser())
                .into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));

        byte[] bytes = (json + " ").getBytes(UTF_8);
        UnsafeBufferEx in = new UnsafeBufferEx(bytes);
        StringBuilder result = new StringBuilder();
        pipeline.reset();
        int window = Math.min(bound, output.capacity());
        generator.wrap(output, 0, window);
        int suspends = 0;
        int guard = 0;
        Status status;
        do
        {
            status = pipeline.transform(in, 0, bytes.length);
            byte[] chunk = new byte[generator.length()];
            output.getBytes(0, chunk);
            result.append(new String(chunk, UTF_8));
            if (status == Status.SUSPENDED)
            {
                suspends++;
                generator.wrap(output, 0, window);
            }
            guard++;
        }
        while (status == Status.SUSPENDED && guard < 100_000);

        assertEquals(Status.COMPLETED, status);
        if (bound < output.capacity())
        {
            assertTrue(suspends >= 1, "expected at least one SUSPENDED chunk boundary");
        }
        return result.toString();
    }

    private static String decodeJsonString(
        String content)
    {
        return textOf("{\"v\":\"" + content + "\"}");
    }

    private static String textOf(
        String document)
    {
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
}
