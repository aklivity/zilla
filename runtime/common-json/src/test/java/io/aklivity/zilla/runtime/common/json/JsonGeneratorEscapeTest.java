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

import java.io.ByteArrayInputStream;
import java.util.function.Consumer;

import jakarta.json.stream.JsonParser;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class JsonGeneratorEscapeTest
{
    @Test
    void shouldEscapeQuotesAndPassStructuralBytesThrough()
    {
        // structural bytes ({ } : ) pass through verbatim; the value's own quotes are escaped
        assertEquals("{\\\"a\\\":1}", escaped(g -> g
            .writeStartObject()
            .write("a", 1)
            .writeEnd()));
    }

    @Test
    void shouldEscapeBackslashByDoubling()
    {
        // a value-side backslash is first turned into \\ by writeString, then the escape layer doubles
        // each of those, yielding the correct nested representation \\\\
        assertEquals("\\\"a\\\\\\\\b\\\"", escaped(g -> g.write("a\\b")));
    }

    @Test
    void shouldEscapeEmbeddedQuoteWithNesting()
    {
        // a value containing a quote: writeString turns " into \" then the escape layer escapes both
        // bytes, producing \\\" — the JSON-in-JSON nested form
        assertEquals("\\\"a\\\\\\\"b\\\"", escaped(g -> g.write("a\"b")));
    }

    @Test
    void shouldEscapeControlCharacters()
    {
        // round-trips are asserted separately; here just confirm controls are escaped, not raw
        String esc = escaped(g -> g.write("a\nb\tc"));
        assertEquals("\\\"a\\\\nb\\\\tc\\\"", esc);
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
        assertRoundTrips(g -> g.write("line1\nline2\ttab\r\b\f"));
    }

    @Test
    void shouldRoundTripMultibyteUtf8()
    {
        assertRoundTrips(g -> g.write("é€😀"));
    }

    @Test
    void shouldEscapeWriteRawContent()
    {
        // writeRaw splices pre-encoded bytes; in escape mode they are escaped like any other output
        MutableDirectBuffer source = new UnsafeBuffer("\"x\"".getBytes(UTF_8));
        assertEquals("[\\\"x\\\"]", escaped(g -> g
            .writeStartArray()
            .writeRaw(source, 0, 3)
            .writeEnd()));
    }

    @Test
    void shouldEscapeRawBytesThroughSegment()
    {
        // the segment path escapes verbatim bytes directly; raw control bytes (which valid JSON never
        // carries as a token) exercise escapeByte's short-escape and unicode-escape branches. Escaping
        // then decoding as JSON string content returns the original bytes.
        byte[] raw = {'"', '\\', '\n', '\r', '\t', '\b', '\f', 0x01, 'A', '~'};
        assertEquals(new String(raw, UTF_8), decodeJsonString(escapeSegment(raw, 64)));
    }

    @Test
    void shouldConsumeSegmentAtomicallyByEscapedWidth()
    {
        // a raw 0x01 needs six output bytes; the segment cut must never split an escape across the bound —
        // a five-byte window consumes nothing, a six-byte window consumes it whole
        MutableDirectBuffer source = new UnsafeBuffer(new byte[] { 0x01 });
        MutableDirectBuffer out = new UnsafeBuffer(new byte[16]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(out, 0, 5, true);
        assertEquals(0, generator.writeSegment(source, 0, 1));
        assertEquals(0, generator.length());

        generator.wrap(out, 0, 6, true);
        assertEquals(1, generator.writeSegment(source, 0, 1));
        byte[] written = new byte[generator.length()];
        out.getBytes(0, written);
        assertEquals("\\u0001", new String(written, UTF_8));
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

    // The defining property of escape mode: escaping a document, then decoding the result as JSON
    // string content, yields the document's plain serialization. This is exactly JSON-in-JSON.
    private static void assertRoundTrips(
        Consumer<JsonGeneratorEx> writer)
    {
        String plain = generate(writer, false);
        String escaped = generate(writer, true);
        assertEquals(plain, decodeJsonString(escaped));
    }

    private static String escaped(
        Consumer<JsonGeneratorEx> writer)
    {
        return generate(writer, true);
    }

    // Escapes raw bytes through the consumption-driven segment path, draining and re-wrapping on each
    // bounded cut, and concatenates the escaped chunks.
    private static String escapeSegment(
        byte[] raw,
        int bound)
    {
        MutableDirectBuffer source = new UnsafeBuffer(raw);
        MutableDirectBuffer out = new UnsafeBuffer(new byte[raw.length * 6 + 16]);
        JsonGeneratorEx generator = JsonEx.createGenerator();
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < raw.length)
        {
            generator.wrap(out, 0, Math.min(bound, out.capacity()), true);
            int consumed = generator.writeSegment(source, index, raw.length - index);
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
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[512]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity(), escape);
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
