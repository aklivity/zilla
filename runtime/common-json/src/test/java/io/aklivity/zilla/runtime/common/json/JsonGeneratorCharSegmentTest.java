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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class JsonGeneratorCharSegmentTest
{
    private static final String[] TEXTS =
    {
        "plain",
        "quote \" backslash \\ slash / tab \t newline \n return \r backspace \b formfeed \f",
        "control \u0001 \u001f",
        "café € 😀 end",
        ""
    };

    private final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

    @Test
    void shouldAppendCharsAsContentOfEscapedScope()
    {
        final JsonGeneratorEx generator = generator();

        generator.writeStartObject().writeKey("a").writeStartEscaped();
        generator.writeSegment("x\"y\\z\nw", 0, 7);
        generator.writeEndEscaped().writeEnd();

        assertEquals("{\"a\":\"x\\\"y\\\\z\\nw\"}", output(generator));
    }

    @Test
    void shouldAppendMultiByteAndSupplementaryChars()
    {
        final JsonGeneratorEx generator = generator();
        final String text = "é€😀";

        generator.writeStartObject().writeKey("a").writeStartEscaped();
        generator.writeSegment(text, 0, text.length());
        generator.writeEndEscaped().writeEnd();

        assertEquals("{\"a\":\"" + text + "\"}", output(generator));
    }

    @Test
    void shouldAppendSeveralSegmentsToOneString()
    {
        final JsonGeneratorEx generator = generator();

        generator.writeStartObject().writeKey("a").writeStartEscaped();
        generator.writeSegment("ab", 0, 2);
        generator.writeSegment("cd", 0, 2);
        generator.writeEndEscaped().writeEnd();

        assertEquals("{\"a\":\"abcd\"}", output(generator));
    }

    @Test
    void shouldAppendOnlyTheRequestedRange()
    {
        final JsonGeneratorEx generator = generator();

        generator.writeStartObject().writeKey("a").writeStartEscaped();

        final int before = generator.consumed();

        generator.writeSegment("xxabyy", 2, 4);

        assertEquals(2, generator.consumed() - before);

        generator.writeEndEscaped().writeEnd();

        assertEquals("{\"a\":\"ab\"}", output(generator));
    }

    @Test
    void shouldAppendLikeTheBytesOutsideAScope()
    {
        for (String text : TEXTS)
        {
            assertEquals(bytesAt(0, text), charsAt(0, text), text);
        }
    }

    @Test
    void shouldAppendLikeTheBytesInsideAScope()
    {
        for (String text : TEXTS)
        {
            assertEquals(bytesAt(1, text), charsAt(1, text), text);
        }
    }

    @Test
    void shouldAppendLikeTheBytesInsideNestedScopes()
    {
        for (String text : TEXTS)
        {
            assertEquals(bytesAt(2, text), charsAt(2, text), text);
        }
    }

    @Test
    void shouldTakeWholeCodePointsWithinTheOutputBound()
    {
        final String text = "a😀b€\"c\néd".repeat(3);
        final String whole = charsAt(1, text);

        for (int bound = 8; bound <= 20; bound++)
        {
            final JsonGeneratorEx generator = generator();
            final StringBuilder result = new StringBuilder();

            generator.writeStartObject().writeKey("a").writeStartEscaped();
            result.append(output(generator));

            int from = 0;

            while (from < text.length())
            {
                generator.wrap(buffer, 0, bound);
                generator.writeSegment(text, from, text.length());

                final int taken = generator.consumed();
                final int end = from + taken;

                assertTrue(taken > 0, "bound " + bound + " from " + from);
                assertFalse(end < text.length() &&
                    Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end)),
                    "bound " + bound + " splits a pair at " + end);

                result.append(output(generator));
                from = end;
            }

            generator.wrap(buffer, 0, buffer.capacity());
            generator.writeEndEscaped().writeEnd();
            result.append(output(generator));

            assertEquals(whole, result.toString(), "bound " + bound);
        }
    }

    @Test
    void shouldReplaceLoneSurrogatesInSegments()
    {
        assertEquals(latin("a\ufffdbx"), segment("a\ud83dbx"));
        assertEquals(latin("a\ufffdb"), segment("a\ude00b"));
        assertEquals(latin("ab\ufffd"), segment("ab\ud83d"));
        assertEquals(latin("a\ud83d\ude00b"), segment("a\ud83d\ude00b"));
        assertEquals(latin("\ufffd\ufffd"), segment("\ud83d\ud83d"));
    }

    @Test
    void shouldReplaceLoneSurrogatesInStringValues()
    {
        assertEquals(latin("{\"k\":\"a\ufffdbx\"}"), value("a\ud83dbx"));
        assertEquals(latin("{\"k\":\"a\ufffdb\"}"), value("a\ude00b"));
        assertEquals(latin("{\"k\":\"ab\ufffd\"}"), value("ab\ud83d"));
        assertEquals(latin("{\"k\":\"a\ud83d\ude00b\"}"), value("a\ud83d\ude00b"));
    }

    @Test
    void shouldReplaceLoneSurrogatesInStringValuesFromChars()
    {
        assertEquals(latin("{\"k\":\"a\ufffdbx\"}"), valueFromChars("a\ud83dbx"));
        assertEquals(latin("{\"k\":\"a\ufffdb\"}"), valueFromChars("a\ude00b"));
        assertEquals(latin("{\"k\":\"ab\ufffd\"}"), valueFromChars("ab\ud83d"));
        assertEquals(latin("{\"k\":\"a\ud83d\ude00b\"}"), valueFromChars("a\ud83d\ude00b"));
    }

    @Test
    void shouldReplaceLoneSurrogatesInFragmentedStringValues()
    {
        final JsonGeneratorEx generator = generator();

        generator.writeStartObject().writeKey("k");
        generator.write(new StringBuilder("a\ud83d"), JsonGeneratorEx.Completion.INCOMPLETE);
        generator.write(new StringBuilder("xb"), JsonGeneratorEx.Completion.COMPLETE);
        generator.writeEnd();

        assertEquals(latin("{\"k\":\"a\ufffdxb\"}"), raw(generator));
    }

    @Test
    void shouldReplaceLoneSurrogatesInKeys()
    {
        final JsonGeneratorEx string = generator();
        string.writeStartObject().writeKey("a\ud83dbx").write(1).writeEnd();
        assertEquals(latin("{\"a\ufffdbx\":1}"), raw(string));

        final JsonGeneratorEx chars = generator();
        chars.writeStartObject().writeKey(new StringBuilder("a\ude00b")).write(1).writeEnd();
        assertEquals(latin("{\"a\ufffdb\":1}"), raw(chars));
    }

    private String bytesAt(
        int depth,
        String text)
    {
        final JsonGeneratorEx generator = generator();
        final byte[] bytes = text.getBytes(UTF_8);
        final MutableDirectBufferEx source = new UnsafeBufferEx(bytes);

        open(generator, depth);
        generator.writeSegment(source, 0, bytes.length);
        close(generator, depth);

        return output(generator);
    }

    private String charsAt(
        int depth,
        String text)
    {
        final JsonGeneratorEx generator = generator();

        open(generator, depth);
        generator.writeSegment(text, 0, text.length());
        close(generator, depth);

        return output(generator);
    }

    private String segment(
        String text)
    {
        final JsonGeneratorEx generator = generator();

        generator.writeStartObject().writeKey("a").writeStartEscaped();
        generator.writeSegment(text, 0, text.length());
        generator.writeEndEscaped().writeEnd();

        final String json = raw(generator);

        return json.substring("{\"a\":\"".length(), json.length() - "\"}".length());
    }

    private String value(
        String text)
    {
        final JsonGeneratorEx generator = generator();

        generator.writeStartObject().write("k", text).writeEnd();

        return raw(generator);
    }

    private String valueFromChars(
        String text)
    {
        final JsonGeneratorEx generator = generator();

        generator.writeStartObject().writeKey("k").write(new StringBuilder(text)).writeEnd();

        return raw(generator);
    }

    private static void open(
        JsonGeneratorEx generator,
        int depth)
    {
        if (depth > 0)
        {
            generator.writeStartObject().writeKey("a");
        }

        for (int level = 0; level < depth; level++)
        {
            generator.writeStartEscaped();
        }
    }

    private static void close(
        JsonGeneratorEx generator,
        int depth)
    {
        for (int level = 0; level < depth; level++)
        {
            generator.writeEndEscaped();
        }

        if (depth > 0)
        {
            generator.writeEnd();
        }
    }

    private JsonGeneratorEx generator()
    {
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        generator.wrap(buffer, 0, buffer.capacity());
        return generator;
    }

    private static String latin(
        String text)
    {
        return new String(text.getBytes(UTF_8), ISO_8859_1);
    }

    private String raw(
        JsonGeneratorEx generator)
    {
        final byte[] bytes = new byte[generator.length()];
        buffer.getBytes(0, bytes);
        return new String(bytes, ISO_8859_1);
    }

    private String output(
        JsonGeneratorEx generator)
    {
        final byte[] bytes = new byte[generator.length()];
        buffer.getBytes(0, bytes);
        return new String(bytes, UTF_8);
    }
}
