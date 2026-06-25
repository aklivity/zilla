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
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Base64;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
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

// Exercises the JSON -> wire write path's bounded value streaming: the parser stages each large string/bytes
// value through a small FIXED buffer, transcoding incrementally and delivering BOUNDED chunks. Drives both
// chunk refills (decoded length far exceeds the fixed staging buffer) and within-chunk output back-pressure
// (a tiny output window forces SUSPEND mid-chunk), asserting the decoded wire round-trips bit-exactly.
public class ProtobufJsonValueChunkingTest
{
    private final ProtobufSchema schema = newSchema();

    @Test
    public void shouldRoundTripLargeMultibyteStringAndPaddedBytesThroughTinyOutputWindow()
    {
        // a 20k-char string mixing ASCII, 2-byte (é), 3-byte (中), and surrogate-pair emoji code points so the
        // UTF-8 encoder must align chunk refills on whole code points across the fixed staging buffer
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 5000; i++)
        {
            builder.append("aé中😀");
        }
        String text = builder.toString();
        assertTrue(text.length() >= 20000, "string under 20k chars");

        // ~15k bytes whose length is NOT a multiple of 3, so the final base64 group is padded (1 trailing byte)
        byte[] blob = new byte[15001];
        for (int i = 0; i < blob.length; i++)
        {
            blob[i] = (byte) (i * 31 + 7);
        }

        String json = json(text, blob);

        // a 40-byte output window is far smaller than either value, forcing SUSPEND within most chunks
        WireDrained drained = fromJsonWindowed("Scalars", json.getBytes(UTF_8), 40);
        assertTrue(drained.suspends >= 1, "expected at least one SUSPENDED chunk boundary");

        JsonObject object = parse(toJson("Scalars", drained.wire));
        assertEquals(text, object.getString("st"));
        assertArrayEquals(blob, Base64.getDecoder().decode(object.getString("by")));
    }

    @Test
    public void shouldRoundTripValueStraddlingFixedBufferAndOutputWindowSimultaneously()
    {
        // values sized so the decoded length straddles the parser's fixed staging buffer (forcing refills) while
        // the output window forces a within-chunk pushback at the same time — both axes active together
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 3000; i++)
        {
            builder.append("中é𐍈x");
        }
        String text = builder.toString();

        byte[] blob = new byte[9007];
        for (int i = 0; i < blob.length; i++)
        {
            blob[i] = (byte) (i * 17 + 3);
        }

        String json = json(text, blob);

        // a 37-byte output window forces within-chunk pushback; the values dwarf the fixed staging buffer so
        // multiple refills also occur
        WireDrained bounded = fromJsonWindowed("Scalars", json.getBytes(UTF_8), 37);
        assertTrue(bounded.suspends >= 1, "expected SUSPENDED chunk boundaries");

        // a generous single-feed window must produce the identical wire bytes
        WireDrained whole = fromJsonWindowed("Scalars", json.getBytes(UTF_8), 1 << 17);
        assertEquals(0, whole.suspends, "generous window must not suspend");
        assertArrayEquals(whole.wire, bounded.wire, "chunked wire must concatenate to the same bytes");

        JsonObject object = parse(toJson("Scalars", bounded.wire));
        assertEquals(text, object.getString("st"));
        assertArrayEquals(blob, Base64.getDecoder().decode(object.getString("by")));
    }

    @Test
    public void shouldRoundTripBytesWithTwoPaddingBytes()
    {
        // a bytes length == 2 mod 3 produces a final group with one '=' pad -> 2 decoded bytes on the last chunk
        byte[] blob = new byte[8000 + 2];
        for (int i = 0; i < blob.length; i++)
        {
            blob[i] = (byte) (i * 13 + 1);
        }
        String json = json("short", blob);

        WireDrained bounded = fromJsonWindowed("Scalars", json.getBytes(UTF_8), 48);
        WireDrained whole = fromJsonWindowed("Scalars", json.getBytes(UTF_8), 1 << 17);
        assertArrayEquals(whole.wire, bounded.wire, "chunked wire must concatenate to the same bytes");

        JsonObject object = parse(toJson("Scalars", bounded.wire));
        assertArrayEquals(blob, Base64.getDecoder().decode(object.getString("by")));
    }

    @Test
    public void shouldStageValueInBoundedFixedBuffer() throws Exception
    {
        Object parser = ProtobufJson.parser(JsonEx.createParser(), schema, "Scalars");
        boolean fixedFound = false;
        for (Field field : parser.getClass().getDeclaredFields())
        {
            assertTrue(!ExpandableArrayBufferEx.class.isAssignableFrom(field.getType()),
                "no growable ExpandableArrayBufferEx may stage the value");
            if ("valueChunk".equals(field.getName()))
            {
                field.setAccessible(true);
                Object value = field.get(parser);
                assertTrue(value instanceof UnsafeBufferEx, "valueChunk must be a fixed UnsafeBufferEx");
                int capacity = ((UnsafeBufferEx) value).capacity();
                assertTrue(capacity > 0 && capacity <= 1 << 13, "value staging buffer must be small and bounded");
                assertEquals(0, capacity % 3, "value staging buffer must be a multiple of 3 for base64 groups");
                fixedFound = true;
            }
        }
        assertTrue(fixedFound, "expected a fixed valueChunk staging buffer");
    }

    private WireDrained fromJsonWindowed(
        String messageName,
        byte[] json,
        int window)
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[window]);
        ProtobufGenerator generator = Protobuf.generator();
        generator.wrap(out, 0, window);
        ProtobufPipeline pipeline = Protobuf.stream(ProtobufJson.parser(JsonEx.createParser(), schema, messageName))
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        UnsafeBufferEx in = new UnsafeBufferEx(json);
        int suspends = 0;
        int guard = 0;
        Status status = pipeline.transform(in, 0, json.length);
        while (status == Status.SUSPENDED && guard < 10_000_000)
        {
            assertTrue(generator.length() <= window, "chunk exceeded the generator limit");
            result.writeBytes(bytes(out, generator.length()));
            generator.wrap(out, 0, window);
            suspends++;
            guard++;
            status = pipeline.transform(in, 0, json.length);
        }
        assertEquals(Status.COMPLETED, status);
        generator.flush();
        result.writeBytes(bytes(out, generator.length()));

        return new WireDrained(result.toByteArray(), suspends);
    }

    private String toJson(
        String messageName,
        byte[] wire)
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[1 << 18]);
        ProtobufGenerator generator = ProtobufJson.generator(JsonEx.createGenerator(), schema, messageName);
        generator.wrap(out, 0, out.capacity());
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, messageName))
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();
        Status status = pipeline.transform(new UnsafeBufferEx(wire), 0, wire.length);
        assertEquals(Status.COMPLETED, status);
        generator.flush();
        byte[] bytes = new byte[generator.length()];
        out.getBytes(0, bytes);
        return new String(bytes, UTF_8);
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

    private static byte[] bytes(
        MutableDirectBufferEx buffer,
        int length)
    {
        byte[] bytes = new byte[length];
        buffer.getBytes(0, bytes);
        return bytes;
    }

    private static JsonObject parse(
        String json)
    {
        try (JsonReader reader = Json.createReader(new ByteArrayInputStream(json.getBytes(UTF_8))))
        {
            return reader.readObject();
        }
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
                .field(ProtobufField.builder().number(14).name("st").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(15).name("by").type(ProtobufType.BYTES).build())
                .build())
            .build();
    }

    private static final class WireDrained
    {
        private final byte[] wire;
        private final int suspends;

        private WireDrained(
            byte[] wire,
            int suspends)
        {
            this.wire = wire;
            this.suspends = suspends;
        }
    }
}
