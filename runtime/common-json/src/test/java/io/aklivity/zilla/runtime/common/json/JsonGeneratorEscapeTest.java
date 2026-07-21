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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.function.Consumer;

import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class JsonGeneratorEscapeTest
{
    @Test
    void shouldEscapeQuotesAndPassStructuralBytesThrough()
    {
        assertEquals("{\\\"a\\\":1}", escaped(g -> g
            .writeStartObject()
            .write("a", 1)
            .writeEnd()));
    }

    @Test
    void shouldEscapeBackslashByDoubling()
    {
        assertEquals("\\\"a\\\\\\\\b\\\"", escaped(g -> g.write("a\\b")));
    }

    @Test
    void shouldEscapeEmbeddedQuoteWithNesting()
    {
        assertEquals("\\\"a\\\\\\\"b\\\"", escaped(g -> g.write("a\"b")));
    }

    @Test
    void shouldEscapeControlCharacters()
    {
        assertEquals("\\\"a\\\\nb\\\\tc\\\"", escaped(g -> g.write("a\nb\tc")));
    }

    @Test
    void shouldRoundTripFlatObject()
    {
        assertRoundTrips(g -> g
            .writeStartObject()
            .write("id", 1)
            .write("name", "alice")
            .writeEnd());
    }

    @Test
    void shouldRoundTripQuoteHeavyValue()
    {
        assertRoundTrips(g -> g.write("she said \"hi\" then \\left\\"));
    }

    @Test
    void shouldRoundTripControlHeavyValue()
    {
        assertRoundTrips(g -> g.write("line1\nline2\ttab\r\b\f"));
    }

    @Test
    void shouldRoundTripMultibyteUtf8()
    {
        assertRoundTrips(g -> g.write("é€😀"));
    }

    @Test
    void shouldEscapeWriteRawContent()
    {
        MutableDirectBufferEx source = new UnsafeBufferEx("\"x\"".getBytes(UTF_8));
        assertEquals("[\\\"x\\\"]", escaped(g -> g
            .writeStartArray()
            .writeRaw(source, 0, 3)
            .writeEnd()));
    }

    @Test
    void shouldEscapeRawBytesThroughSegment()
    {
        byte[] raw = {'"', '\\', '\n', '\r', '\t', '\b', '\f', 0x01, 'A', '~'};
        assertEquals(new String(raw, UTF_8), decodeJsonString(escapeSegment(raw, 64)));
    }

    @Test
    void shouldConsumeSegmentAtomicallyByEscapedWidth()
    {
        MutableDirectBufferEx source = new UnsafeBufferEx(new byte[] { 0x01 });
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[16]);
        JsonGeneratorEx generator = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true)).wrap(out, 0, 5);
        generator.writeSegment(source, 0, 1);
        assertEquals(0, generator.consumed());
        assertEquals(0, generator.length());

        generator.wrap(out, 0, 6);
        generator.writeSegment(source, 0, 1);
        assertEquals(1, generator.consumed());
        byte[] written = new byte[generator.length()];
        out.getBytes(0, written);
        assertEquals("\\u0001", new String(written, UTF_8));
    }

    @Test
    void shouldWriteDoublyEscapedQuoteWhenExactRoom()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[10]);
        JsonGeneratorEx generator = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true))
            .wrap(buffer, 0, 10);
        generator.write("a\"b");
        assertEquals(3, generator.consumed());
        assertEquals(10, generator.length());
    }

    @Test
    void shouldNotOverrunWrappedLimitWhenRoomInsufficientForDoublyEscapedQuote()
    {
        for (int limit = 1; limit <= 10; limit++)
        {
            MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[limit]);
            JsonGeneratorEx generator = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true))
                .wrap(buffer, 0, limit);
            generator.write("a\"b");
            assertTrue(generator.length() <= limit, "overran limit=" + limit);
        }
    }

    @Test
    void shouldNotOverrunWrappedLimitWhenRoomInsufficientForDoublyEscapedKeyQuote()
    {
        for (int limit = 1; limit <= 12; limit++)
        {
            MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[limit]);
            JsonGeneratorEx generator = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true))
                .wrap(buffer, 0, limit);
            generator.writeStartObject();
            generator.writeKey("a\"b");
            assertTrue(generator.length() <= limit, "overran limit=" + limit);
        }
    }

    @Test
    void shouldNotOverrunWrappedLimitWhenRoomInsufficientForDoublyEscapedBackslashOrControl()
    {
        for (int limit = 1; limit <= 12; limit++)
        {
            MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[limit]);
            JsonGeneratorEx generator = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true))
                .wrap(buffer, 0, limit);
            generator.write("a\\\nb");
            assertTrue(generator.length() <= limit, "overran limit=" + limit);
        }
    }

    @Test
    void shouldRoundTripNestedDocument()
    {
        assertRoundTrips(g -> g
            .writeStartObject()
            .writeKey("text").write("a \"quoted\" \\ value\nwith newline")
            .writeKey("nested").writeStartObject()
                .write("k", "v\"v")
                .writeEnd()
            .writeKey("arr").writeStartArray()
                .write("x\\y")
                .writeNumber("-2.5e3")
                .write(true)
                .writeNull()
                .writeEnd()
            .writeEnd());
    }

    private static void assertRoundTrips(
        Consumer<JsonGeneratorEx> writer)
    {
        assertEquals(generate(writer, false), decodeJsonString(generate(writer, true)));
    }

    private static String escaped(
        Consumer<JsonGeneratorEx> writer)
    {
        return generate(writer, true);
    }

    private static String escapeSegment(
        byte[] raw,
        int bound)
    {
        MutableDirectBufferEx source = new UnsafeBufferEx(raw);
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[raw.length * 6 + 16]);
        JsonGeneratorEx generator = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true));
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < raw.length)
        {
            generator.wrap(out, 0, Math.min(bound, out.capacity()));
            generator.writeSegment(source, index, raw.length - index);
            int consumed = generator.consumed();
            index += consumed;
            byte[] chunk = new byte[generator.length()];
            out.getBytes(0, chunk);
            result.append(new String(chunk, UTF_8));
            assertTrue(consumed > 0, "no progress at bound " + bound);
        }
        return result.toString();
    }

    private static String generate(
        Consumer<JsonGeneratorEx> writer,
        boolean escape)
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[512]);
        Map<String, ?> config = escape ? Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true) : Map.of();
        JsonGeneratorEx generator = JsonEx.createGenerator(config).wrap(buffer, 0, buffer.capacity());
        writer.accept(generator);
        byte[] out = new byte[generator.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }

    private static String decodeJsonString(
        String content)
    {
        String document = "{\"v\":\"" + content + "\"}";
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
