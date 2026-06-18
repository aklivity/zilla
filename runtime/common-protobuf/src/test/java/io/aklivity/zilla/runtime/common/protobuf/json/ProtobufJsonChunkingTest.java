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
package io.aklivity.zilla.runtime.common.protobuf.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.function.Consumer;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEnum;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;

public class ProtobufJsonChunkingTest
{
    private final ProtobufSchema schema = newSchema();

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

        byte[] wire = wire(g -> g
            .writeInt32(1, -5)
            .writeBool(13, true)
            .writeString(14, text)
            .writeBytes(15, blob));

        Drained drained = toJsonWindowed("Scalars", wire, 64);

        assertTrue(drained.suspends >= 1, "expected at least one SUSPENDED chunk boundary");

        JsonObject object = parse(drained.json);
        assertEquals(-5, object.getInt("i32"));
        assertEquals(true, object.getBoolean("bo"));
        assertEquals(text, object.getString("st"));
        assertArrayEquals(blob, Base64.getDecoder().decode(object.getString("by")));
    }

    @Test
    public void shouldFragmentAcrossWindowedInputAndOutput()
    {
        // window both input and output so a value arrives in input pieces (deferred > 0) that also do not
        // align to 3-byte base64 groups — exercising the cross-window byte carry as well as output suspends
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 2500; i++)
        {
            builder.append("Z\"é中\n");
        }
        String text = builder.toString();

        byte[] blob = new byte[9001];
        for (int i = 0; i < blob.length; i++)
        {
            blob[i] = (byte) (i * 53 + 11);
        }

        byte[] wire = wire(g -> g
            .writeInt32(1, 17)
            .writeString(14, text)
            .writeBytes(15, blob));

        Drained drained = toJsonTwoAxis("Scalars", wire, 50, 37);

        assertTrue(drained.suspends >= 1, "expected at least one SUSPENDED chunk boundary");

        JsonObject object = parse(drained.json);
        assertEquals(17, object.getInt("i32"));
        assertEquals(text, object.getString("st"));
        assertArrayEquals(blob, Base64.getDecoder().decode(object.getString("by")));
    }

    @Test
    public void shouldRenderSmallValueIdenticallyInOneFeed()
    {
        byte[] wire = wire(g -> g
            .writeInt32(1, -5)
            .writeBool(13, true)
            .writeString(14, "hi \"there\"\n")
            .writeBytes(15, new byte[]{1, 2, 3, 4, 5}));

        // a single feed through a generous window must not suspend and must match the windowed result
        Drained whole = toJsonWindowed("Scalars", wire, 8192);
        assertEquals(0, whole.suspends, "small value must not suspend with a generous window");

        // small enough that the document spans multiple windows, large enough that each scalar field fits
        Drained bounded = toJsonWindowed("Scalars", wire, 32);
        assertEquals(whole.json, bounded.json, "chunked output must concatenate to the same document");

        JsonObject object = parse(whole.json);
        assertEquals("hi \"there\"\n", object.getString("st"));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, Base64.getDecoder().decode(object.getString("by")));
    }

    private Drained toJsonWindowed(
        String messageName,
        byte[] wire,
        int window)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[window]);
        ProtobufGenerator generator = ProtobufJson.generator(JsonEx.createGenerator(), schema, messageName);
        generator.wrap(out, 0, window);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, messageName))
            .into(ProtobufSink.of(generator, schema, messageName));
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
        generator.flush();
        result.append(chunk(out, generator.length()));

        return new Drained(result.toString(), suspends);
    }

    private Drained toJsonTwoAxis(
        String messageName,
        byte[] wire,
        int inputWindow,
        int outputWindow)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[outputWindow]);
        ProtobufGenerator generator = ProtobufJson.generator(JsonEx.createGenerator(), schema, messageName);
        generator.wrap(out, 0, outputWindow);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, messageName))
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();

        StringBuilder result = new StringBuilder();
        UnsafeBuffer in = new UnsafeBuffer(wire);
        int progress = 0;
        int limit = 0;
        int suspends = 0;
        int guard = 0;
        Status status = Status.STARVED;
        boolean done = false;
        while (!done && guard < 5_000_000)
        {
            int take = Math.min(inputWindow, wire.length - limit);
            limit += take;
            boolean last = limit >= wire.length;
            status = pipeline.feed(in, progress, limit, last);
            while (status == Status.SUSPENDED && guard < 5_000_000)
            {
                assertTrue(generator.length() <= outputWindow, "chunk exceeded the generator limit");
                result.append(chunk(out, generator.length()));
                generator.wrap(out, 0, outputWindow);
                suspends++;
                guard++;
                status = pipeline.feed(in, progress, limit, last);
            }
            if (status == Status.STARVED)
            {
                progress = limit - pipeline.remaining();
            }
            else
            {
                done = true;
            }
            guard++;
        }
        assertEquals(Status.COMPLETED, status);
        generator.flush();
        result.append(chunk(out, generator.length()));

        return new Drained(result.toString(), suspends);
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

    private static byte[] wire(
        Consumer<ProtobufGenerator> body)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[65536]);
        ProtobufGenerator generator = Protobuf.generator().wrap(buffer, 0, buffer.capacity());
        body.accept(generator);
        byte[] bytes = new byte[generator.length()];
        buffer.getBytes(0, bytes);
        return bytes;
    }

    private static ProtobufSchema newSchema()
    {
        return Protobuf.schema()
            .enumeration(ProtobufEnum.builder("Color")
                .value("RED", 0)
                .value("GREEN", 1)
                .value("BLUE", 2)
                .build())
            .message(ProtobufMessage.builder("Scalars")
                .field(ProtobufField.builder().number(1).name("i32").type(ProtobufType.INT32).build())
                .field(ProtobufField.builder().number(13).name("bo").type(ProtobufType.BOOL).build())
                .field(ProtobufField.builder().number(14).name("st").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(15).name("by").type(ProtobufType.BYTES).build())
                .build())
            .build();
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
}
