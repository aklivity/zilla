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
package io.aklivity.zilla.runtime.common.avro.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Base64;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.avro.Avro;
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

public class AvroJsonChunkingTest
{
    private static final String SCHEMA = """
        {"type":"record","name":"Scalars","fields":[
        {"name":"st","type":"string"},
        {"name":"by","type":"bytes"}]}""";

    private final AvroSchema schema = Avro.schema(SCHEMA);

    @Test
    public void shouldFragmentLargeStringAndBytesAcrossTinyWindows()
    {
        // a string whose JSON rendering far exceeds the output window, with chars that need escaping,
        // and a bytes value whose base64 rendering also far exceeds the window
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 4000; i++)
        {
            builder.append("ab\"c\nd");
        }
        String text = builder.toString();

        byte[] blob = new byte[15000];
        for (int i = 0; i < blob.length; i++)
        {
            blob[i] = (byte) (i * 31 + 7);
        }

        byte[] wire = jsonToAvro(json(text, blob));

        Drained drained = avroToJsonWindowed(wire, 64);

        assertTrue(drained.suspends >= 1, "expected at least one SUSPENDED chunk boundary");

        JsonObject object = parse(drained.json);
        assertEquals(text, object.getString("st"));
        assertArrayEquals(blob, Base64.getDecoder().decode(object.getString("by")));
    }

    @Test
    public void shouldRenderIdenticallyChunkedAndWhole()
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 30; i++)
        {
            builder.append("hi \"there\"\n");
        }
        String text = builder.toString();
        byte[] blob = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        byte[] wire = jsonToAvro(json(text, blob));

        // a single feed through a generous window must not suspend
        Drained whole = avroToJsonWindowed(wire, 8192);
        assertEquals(0, whole.suspends, "value must not suspend with a generous window");

        // a tiny window forces chunking; the concatenated output must match the whole-window result
        Drained bounded = avroToJsonWindowed(wire, 64);
        assertTrue(bounded.suspends >= 1, "expected at least one SUSPENDED chunk boundary");
        assertEquals(whole.json, bounded.json, "chunked output must concatenate to the same document");

        JsonObject object = parse(whole.json);
        assertEquals(text, object.getString("st"));
        assertArrayEquals(blob, Base64.getDecoder().decode(object.getString("by")));
    }

    private Drained avroToJsonWindowed(
        byte[] wire,
        int window)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[window]);
        JsonGeneratorEx json = JsonEx.createGenerator();
        AvroGenerator generator = AvroJson.generator(schema, json, false).wrap(out, 0, window);
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema)).into(AvroSink.of(generator));
        pipeline.reset();

        StringBuilder result = new StringBuilder();
        UnsafeBuffer in = new UnsafeBuffer(wire);
        int suspends = 0;
        int guard = 0;
        Status status = pipeline.feed(in, 0, wire.length);
        while (status == Status.SUSPENDED && guard < 1_000_000)
        {
            assertTrue(generator.length() <= window, "chunk exceeded the generator limit");
            result.append(chunk(out, generator.length()));
            generator.wrap(out, 0, window);
            suspends++;
            guard++;
            status = pipeline.feed(in, 0, wire.length);
        }
        assertEquals(Status.COMPLETED, status);
        json.flush();
        result.append(chunk(out, generator.length()));

        return new Drained(result.toString(), suspends);
    }

    @Test
    public void shouldFragmentBytesAcrossTinyInputAndOutputWindows()
    {
        // a bytes value whose base64 groups straddle a 64-byte input window boundary (64 is not a multiple of 3)
        // plus a large escaped string, driven through a 50-byte output window — exercises both input-window
        // reassembly (STARVED) and output back-pressure (SUSPENDED) on the avro -> JSON path
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 4000; i++)
        {
            builder.append("ab\"c\nd");
        }
        String text = builder.toString();

        byte[] blob = new byte[15001];
        for (int i = 0; i < blob.length; i++)
        {
            blob[i] = (byte) (i * 31 + 7);
        }

        byte[] wire = jsonToAvro(json(text, blob));

        Windowed drained = avroToJsonInputWindowed(wire, 64, 50);

        assertTrue(drained.starves >= 1, "expected at least one STARVED input-window boundary");
        assertTrue(drained.suspends >= 1, "expected at least one SUSPENDED output-window boundary");

        JsonObject object = parse(drained.json);
        assertEquals(text, object.getString("st"));
        assertArrayEquals(blob, Base64.getDecoder().decode(object.getString("by")));
    }

    private Windowed avroToJsonInputWindowed(
        byte[] wire,
        int inputWindow,
        int outputWindow)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[outputWindow]);
        JsonGeneratorEx json = JsonEx.createGenerator();
        AvroGenerator generator = AvroJson.generator(schema, json, false).wrap(out, 0, outputWindow);
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema)).into(AvroSink.of(generator));
        pipeline.reset();

        StringBuilder result = new StringBuilder();
        UnsafeBuffer in = new UnsafeBuffer(wire);
        int progress = 0;
        int length = 0;
        int suspends = 0;
        int starves = 0;
        int guard = 0;
        Status status = pipeline.feed(in, 0, 0, false);
        while (status != Status.COMPLETED && status != Status.REJECTED && guard < 10_000_000)
        {
            if (status == Status.SUSPENDED)
            {
                result.append(chunk(out, generator.length()));
                generator.wrap(out, 0, outputWindow);
                suspends++;
            }
            else
            {
                // STARVED: keep the unconsumed tail of the last window and slide forward over what was consumed
                progress += length - pipeline.remaining();
                starves++;
            }
            guard++;
            length = Math.min(inputWindow, wire.length - progress);
            status = pipeline.feed(in, progress, progress + length, progress + length == wire.length);
        }
        assertEquals(Status.COMPLETED, status);
        json.flush();
        result.append(chunk(out, generator.length()));

        return new Windowed(result.toString(), suspends, starves);
    }

    private byte[] jsonToAvro(
        String json)
    {
        byte[] jsonBytes = json.getBytes(UTF_8);
        MutableDirectBuffer out = new UnsafeBuffer(new byte[Math.max(256, jsonBytes.length * 4)]);
        AvroGenerator generator = Avro.generator(schema, out, 0);
        JsonParserEx parser = JsonEx.createParser();
        AvroPipeline pipeline = AvroJson.stream(schema, parser).into(AvroSink.of(generator));
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(jsonBytes), 0, jsonBytes.length);
        assertEquals(Status.COMPLETED, status);
        byte[] avro = new byte[generator.length()];
        out.getBytes(0, avro);
        return avro;
    }

    private static String json(
        String st,
        byte[] by)
    {
        return Json.createObjectBuilder()
            .add("st", st)
            .add("by", Base64.getEncoder().encodeToString(by))
            .build()
            .toString();
    }

    private static String chunk(
        MutableDirectBuffer buffer,
        int length)
    {
        byte[] bytes = new byte[length];
        buffer.getBytes(0, bytes);
        return new String(bytes, UTF_8);
    }

    private static JsonObject parse(
        String json)
    {
        try (JsonReader reader = Json.createReader(new ByteArrayInputStream(json.getBytes(UTF_8))))
        {
            return reader.readObject();
        }
    }

    private static final class Drained
    {
        private final String json;
        private final int suspends;

        private Drained(
            String json,
            int suspends)
        {
            this.json = json;
            this.suspends = suspends;
        }
    }

    private static final class Windowed
    {
        private final String json;
        private final int suspends;
        private final int starves;

        private Windowed(
            String json,
            int suspends,
            int starves)
        {
            this.json = json;
            this.suspends = suspends;
            this.starves = starves;
        }
    }
}
