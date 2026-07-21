/*
 * Copyright 2021-2026 Aklivity Inc
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
import java.util.Base64;
import java.util.function.Consumer;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import org.junit.jupiter.api.Test;

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

public class ProtobufJsonChunkingTest
{
    private static final String LONG_KEY = "status_field_with_a_deliberately_long_name";

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
    public void shouldFragmentLargeBytesAndStringAcrossTinyInputAndOutputWindows()
    {
        // a bytes value larger than both the input and the output window, with per-window byte counts that
        // are NOT aligned to 3, so a base64 group straddles the input-window boundary and the adapter holds a
        // 1-2 byte sub-group tail that only the next input window can complete (input back-pressure, STARVED);
        // a large string in the same message forces output back-pressure (SUSPENDED) as well
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

        byte[] wire = wire(g -> g
            .writeInt32(1, -5)
            .writeBool(13, true)
            .writeString(14, text)
            .writeBytes(15, blob));

        // a 64-byte input window is not a multiple of 3, so the bytes value re-aligns to a sub-group tail at
        // many window boundaries; the 50-byte output window is far smaller than either large value
        TwoAxisDrained drained = toJsonTwoAxis("Scalars", wire, 64, 50);

        assertTrue(drained.starves >= 1, "expected at least one STARVED (input) chunk boundary");
        assertTrue(drained.suspends >= 1, "expected at least one SUSPENDED (output) chunk boundary");

        JsonObject object = parse(drained.json);
        assertEquals(-5, object.getInt("i32"));
        assertEquals(true, object.getBoolean("bo"));
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

    @Test
    public void shouldFragmentLargeStringAndBytesFromJsonAcrossTinyWindows()
    {
        // a string and a bytes value whose wire rendering each far exceed a tiny output window, driving
        // JSON -> wire so the parser must push back the unconsumed remainder of each value on every suspend
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

        String json = json(1, true, text, blob);

        WireDrained drained = fromJsonWindowed("Scalars", json.getBytes(UTF_8), 64);

        assertTrue(drained.suspends >= 1, "expected at least one SUSPENDED chunk boundary");

        JsonObject object = parse(toJson("Scalars", drained.wire));
        assertEquals(1, object.getInt("i32"));
        assertEquals(true, object.getBoolean("bo"));
        assertEquals(text, object.getString("st"));
        assertArrayEquals(blob, Base64.getDecoder().decode(object.getString("by")));
    }

    @Test
    public void shouldRenderSmallValueFromJsonIdenticallyInOneFeed()
    {
        String text = "hi \"there\"\n";
        byte[] blob = new byte[]{1, 2, 3, 4, 5};
        String json = json(-5, true, text, blob);

        // a single feed through a generous window must not suspend
        WireDrained whole = fromJsonWindowed("Scalars", json.getBytes(UTF_8), 8192);
        assertEquals(0, whole.suspends, "small value must not suspend with a generous window");

        // a small window forces chunking; the concatenated wire must match the whole-window result
        WireDrained bounded = fromJsonWindowed("Scalars", json.getBytes(UTF_8), 16);
        assertArrayEquals(whole.wire, bounded.wire, "chunked wire must concatenate to the same bytes");

        JsonObject object = parse(toJson("Scalars", whole.wire));
        assertEquals(-5, object.getInt("i32"));
        assertEquals(true, object.getBoolean("bo"));
        assertEquals(text, object.getString("st"));
        assertArrayEquals(blob, Base64.getDecoder().decode(object.getString("by")));
    }

    @Test
    public void shouldFragmentMapKeyAcrossTinyInputWindows()
    {
        // a string map key long enough that its wire bytes split across several tiny input windows, exercising
        // the multi-fragment accumulation path that materializes the key once complete
        String key = "the-quick-brown-fox-jumps-over-the-lazy-dog";

        byte[] wire = wire(g ->
        {
            g.startMessage(1, 64);
            g.writeString(1, key);
            g.writeString(2, "v");
            g.endMessage();
        });

        // a 4-byte input window forces the key's wire bytes to arrive in many fragments (STARVED), while a
        // generous output window keeps the whole document in one chunk
        TwoAxisDrained drained = toJsonTwoAxis("Props", wire, 4, 8192);

        assertTrue(drained.starves >= 1, "expected at least one STARVED (input) chunk boundary");

        JsonObject object = parse(drained.json);
        assertEquals("v", object.getJsonObject("props").getString(key));
    }

    @Test
    public void shouldPreserveStringValuesAcrossStructuralPrefixOverrun()
    {
        byte[] wire = wire(g -> g
            .writeString(1, "123")
            .writeString(2, "OK"));

        Drained drained = toJsonBounded("Event", wire, 56);

        assertTrue(drained.suspends >= 1, "expected at least one SUSPENDED chunk boundary, got: " + drained.json);
        assertEquals("{\"id\":\"123\",\"" + LONG_KEY + "\":\"OK\"}", drained.json);
    }

    @Test
    public void shouldFragmentRecordKeyPrefixLargerThanWindow()
    {
        byte[] wire = wire(g -> g
            .writeString(1, "123")
            .writeString(2, "OK"));
        Drained drained = toJsonBounded("Event", wire, 32);
        assertTrue(drained.suspends >= 1, "expected at least one SUSPENDED chunk boundary, got: " + drained.json);
        assertEquals("{\"id\":\"123\",\"" + LONG_KEY + "\":\"OK\"}", drained.json);
    }

    @Test
    public void shouldMatchFieldNameKeyThatFragmentsAcrossJsonInputWindows()
    {
        // the field name key itself is longer than the JSON input feed window and fragments across STARVED
        // windows before messageStep()'s field lookup sees it complete; the field must still be found once
        // the key finishes reassembling, rather than being rejected (or matched) on a prefix
        String json = Json.createObjectBuilder()
            .add("id", "123")
            .add(LONG_KEY, "OK")
            .build()
            .toString();

        byte[] wire = fromJsonInputWindowed("Event", json.getBytes(UTF_8), 8);

        JsonObject object = parse(toJson("Event", wire));
        assertEquals("123", object.getString("id"));
        assertEquals("OK", object.getString(LONG_KEY));
    }

    @Test
    public void shouldResolveMapKeyThatFragmentsAcrossJsonInputWindows()
    {
        // a string map key long enough to fragment across the JSON input feed window before mapStep() sees
        // it complete; the map entry must still resolve to the correct (whole) key
        String key = "the-quick-brown-fox-jumps-over-the-lazy-dog";
        String json = "{\"props\":{\"" + key + "\":\"v\"}}";

        byte[] wire = fromJsonInputWindowed("Props", json.getBytes(UTF_8), 4);

        JsonObject object = parse(toJson("Props", wire));
        assertEquals("v", object.getJsonObject("props").getString(key));
    }

    // Drives the JSON -> wire direction (ProtobufJsonParserImpl) through fixed-size JSON input windows,
    // carrying the unconsumed tail (pipeline.remaining()) across STARVED feeds the way a real caller does, so
    // a field-name or map key larger than the window fragments and reassembles before the schema walk reads it.
    private byte[] fromJsonInputWindowed(
        String messageName,
        byte[] json,
        int window)
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[Math.max(256, json.length * 4)]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
        ProtobufPipeline pipeline = Protobuf.stream(ProtobufJson.parser(JsonEx.createParser(), schema, messageName))
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();

        UnsafeBufferEx in = new UnsafeBufferEx(json);
        int progress = 0;
        int limit = 0;
        Status status = Status.STARVED;
        int guard = 0;
        while (status == Status.STARVED && guard++ < 10_000)
        {
            limit = Math.min(limit + window, json.length);
            boolean last = limit >= json.length;
            status = pipeline.transform(in, progress, limit, last);
            if (status == Status.STARVED)
            {
                progress = limit - pipeline.remaining();
            }
        }
        assertEquals(Status.COMPLETED, status);
        byte[] wire = new byte[generator.length()];
        out.getBytes(0, wire);
        return wire;
    }

    private Drained toJsonBounded(
        String messageName,
        byte[] wire,
        int window)
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[Math.max(256, window * 8)]);
        ProtobufGenerator generator = ProtobufJson.generator(JsonEx.createGenerator(), schema, messageName);
        generator.wrap(out, 0, window);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, messageName))
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();

        StringBuilder result = new StringBuilder();
        UnsafeBufferEx in = new UnsafeBufferEx(wire);
        int suspends = 0;
        int guard = 0;
        Status status = pipeline.transform(in, 0, wire.length);
        while (status == Status.SUSPENDED && guard < 1_000_000)
        {
            assertTrue(generator.length() <= window, "drained chunk overran the output bound: " + generator.length());
            result.append(chunk(out, generator.length()));
            generator.wrap(out, 0, window);
            suspends++;
            guard++;
            status = pipeline.transform(in, 0, wire.length);
        }
        assertEquals(Status.COMPLETED, status);
        generator.flush();
        assertTrue(generator.length() <= window, "final chunk overran the output bound: " + generator.length());
        result.append(chunk(out, generator.length()));

        return new Drained(result.toString(), suspends);
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
        while (status == Status.SUSPENDED && guard < 1_000_000)
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
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[65536]);
        ProtobufGenerator generator = ProtobufJson.generator(JsonEx.createGenerator(), schema, messageName);
        generator.wrap(out, 0, out.capacity());
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, messageName))
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();
        Status status = pipeline.transform(new UnsafeBufferEx(wire), 0, wire.length);
        assertEquals(Status.COMPLETED, status);
        generator.flush();
        return chunk(out, generator.length());
    }

    private static String json(
        int i32,
        boolean bo,
        String st,
        byte[] by)
    {
        return Json.createObjectBuilder()
            .add("i32", i32)
            .add("bo", bo)
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

    private Drained toJsonWindowed(
        String messageName,
        byte[] wire,
        int window)
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[window]);
        ProtobufGenerator generator = ProtobufJson.generator(JsonEx.createGenerator(), schema, messageName);
        generator.wrap(out, 0, window);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, messageName))
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();

        StringBuilder result = new StringBuilder();
        UnsafeBufferEx in = new UnsafeBufferEx(wire);
        int suspends = 0;
        int guard = 0;
        Status status = pipeline.transform(in, 0, wire.length);
        while (status == Status.SUSPENDED && guard < 1_000_000)
        {
            assertTrue(generator.length() <= window, "chunk exceeded the generator limit");
            result.append(chunk(out, generator.length()));
            generator.wrap(out, 0, window);
            suspends++;
            guard++;
            status = pipeline.transform(in, 0, wire.length);
        }
        assertEquals(Status.COMPLETED, status);
        generator.flush();
        result.append(chunk(out, generator.length()));

        return new Drained(result.toString(), suspends);
    }

    // drives the wire -> JSON pipeline across both input windows (advancing on STARVED) and output windows
    // (draining on SUSPENDED), the canonical two-axis loop; collects the concatenated JSON document
    private TwoAxisDrained toJsonTwoAxis(
        String messageName,
        byte[] wire,
        int inWindow,
        int outWindow)
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[outWindow]);
        ProtobufGenerator generator = ProtobufJson.generator(JsonEx.createGenerator(), schema, messageName);
        generator.wrap(out, 0, outWindow);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, messageName))
            .into(ProtobufSink.of(generator, schema, messageName));
        pipeline.reset();

        StringBuilder result = new StringBuilder();
        UnsafeBufferEx in = new UnsafeBufferEx(wire);
        int progress = 0;
        int limit = 0;
        int suspends = 0;
        int starves = 0;
        int guard = 0;
        boolean completed = false;
        while (!completed)
        {
            if (guard++ > 1_000_000)
            {
                throw new AssertionError("pipeline failed to converge");
            }
            int take = Math.min(inWindow, wire.length - limit);
            limit += take;
            boolean last = limit >= wire.length;
            Status status = pipeline.transform(in, progress, limit, last);
            while (status == Status.SUSPENDED)
            {
                assertTrue(generator.length() <= outWindow, "chunk exceeded the generator limit");
                result.append(chunk(out, generator.length()));
                generator.wrap(out, 0, outWindow);
                suspends++;
                status = pipeline.transform(in, progress, limit, last);
            }
            switch (status)
            {
            case STARVED:
                assertTrue(!last, "last window must not starve");
                progress = limit - pipeline.remaining();
                starves++;
                break;
            case COMPLETED:
                completed = true;
                break;
            default:
                throw new AssertionError("unexpected status " + status);
            }
        }
        generator.flush();
        result.append(chunk(out, generator.length()));

        return new TwoAxisDrained(result.toString(), suspends, starves);
    }

    private static String chunk(
        MutableDirectBufferEx buffer,
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
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[65536]);
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
            .message(ProtobufMessage.builder("PropsEntry")
                .mapEntry(true)
                .field(ProtobufField.builder().number(1).name("key").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("value").type(ProtobufType.STRING).build())
                .build())
            .message(ProtobufMessage.builder("Props")
                .field(ProtobufField.builder().number(1).name("props").type(ProtobufType.MESSAGE).typeName("PropsEntry")
                    .repeated(true).build())
                .build())
            .message(ProtobufMessage.builder("Event")
                .field(ProtobufField.builder().number(1).name("id").jsonName("id").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name(LONG_KEY).jsonName(LONG_KEY).type(ProtobufType.STRING).build())
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

    private static final class TwoAxisDrained
    {
        private final String json;
        private final int suspends;
        private final int starves;

        private TwoAxisDrained(
            String json,
            int suspends,
            int starves)
        {
            this.json = json;
            this.suspends = suspends;
            this.starves = starves;
        }
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
