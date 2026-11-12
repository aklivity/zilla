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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.view.JsonStringView;

class JsonGeneratorViewSegmentTest
{
    private static final String WRITTEN = "a\\/b \\u00e9 \\ud83d\\ude00 é 😀 \\\"q\\\" \\n end";

    private final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

    @Test
    void shouldCopyEncodedBytesAsWrittenIntoEscapedScope()
    {
        final JsonGeneratorEx generator = generator();
        final JsonStringView view = encoded(WRITTEN);

        generator.writeStartObject().writeKey("a").writeStartEscaped();
        generator.writeSegment(view, 0, view.length());
        generator.writeEndEscaped().writeEnd();

        assertEquals("{\"a\":\"" + WRITTEN + "\"}", output(generator));
    }

    @Test
    void shouldCopyOnlyTheRequestedRange()
    {
        final JsonGeneratorEx generator = generator();
        final JsonStringView view = encoded("ab\\/cd\\u00e9ef");

        generator.writeStartObject().writeKey("a").writeStartEscaped();

        final int before = generator.consumed();

        generator.writeSegment(view, 2, 6);

        assertEquals(4, generator.consumed() - before);

        generator.writeEndEscaped().writeEnd();

        assertEquals("{\"a\":\"\\/cd\\u00e9\"}", output(generator));
    }

    @Test
    void shouldAppendSeveralSegmentsToOneString()
    {
        final JsonGeneratorEx generator = generator();

        generator.writeStartObject().writeKey("a").writeStartEscaped();
        generator.writeSegment(encoded("a\\/b"), 0, 3);
        generator.writeSegment(encoded("\\u00e9c"), 0, 2);
        generator.writeEndEscaped().writeEnd();

        assertEquals("{\"a\":\"a\\/b\\u00e9c\"}", output(generator));
    }

    @Test
    void shouldCopyNothingForAnEmptyRange()
    {
        final JsonGeneratorEx generator = generator();

        generator.writeStartObject().writeKey("a").writeStartEscaped();
        generator.writeSegment(encoded("abc"), 1, 1);
        generator.writeEndEscaped().writeEnd();

        assertEquals("{\"a\":\"\"}", output(generator));
    }

    @Test
    void shouldTakeWholeUnitsWithinTheOutputBound()
    {
        final String body = "a\\/b \\u00e9 \\ud83d\\ude00 é 😀 \\\"q\\\" \\n end ".repeat(3);
        final JsonStringView view = encoded(body);
        final int length = view.length();

        for (int bound = 12; bound <= 30; bound++)
        {
            final JsonGeneratorEx generator = generator();
            final StringBuilder result = new StringBuilder();

            generator.writeStartObject().writeKey("a").writeStartEscaped();
            result.append(output(generator));

            int from = 0;

            while (from < length)
            {
                generator.wrap(buffer, 0, bound);
                generator.writeSegment(view, from, length);

                final int taken = generator.consumed();
                final int end = from + taken;

                assertTrue(taken > 0, "bound " + bound + " from " + from);
                assertFalse(end < length &&
                    Character.isHighSurrogate(view.charAt(end - 1)) && Character.isLowSurrogate(view.charAt(end)),
                    "bound " + bound + " splits a pair at " + end);

                result.append(output(generator));
                from = end;
            }

            generator.wrap(buffer, 0, buffer.capacity());
            generator.writeEndEscaped().writeEnd();
            result.append(output(generator));

            assertEquals("{\"a\":\"" + body + "\"}", result.toString(), "bound " + bound);
        }
    }

    @Test
    void shouldWriteDecodedViewAsEscapedChars()
    {
        final String text = "a\"b\\é\n😀";
        final JsonStringView view = new JsonStringView.Decoded().wrap(text);

        assertEquals(charsAt(1, text), viewAt(1, view));
    }

    @Test
    void shouldWriteEncodedViewAsCharsOutsideAScope()
    {
        final JsonStringView view = encoded("a\\/b \\u00e9");

        assertEquals(charsAt(0, view), viewAt(0, view));
        assertEquals("a/b é", viewAt(0, view));
    }

    @Test
    void shouldWriteEncodedViewAsCharsInAnEscapedScopeOfAnEscapedScope()
    {
        final JsonStringView view = encoded("a\\/b \\u00e9 \\\"q\\\"");

        assertEquals(charsAt(2, view), viewAt(2, view));
    }

    private String viewAt(
        int depth,
        JsonStringView view)
    {
        final JsonGeneratorEx generator = generator();

        open(generator, depth);
        generator.writeSegment(view, 0, view.length());
        close(generator, depth);

        return output(generator);
    }

    private String charsAt(
        int depth,
        CharSequence text)
    {
        final JsonGeneratorEx generator = generator();

        open(generator, depth);
        generator.writeSegment(text, 0, text.length());
        close(generator, depth);

        return output(generator);
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

    private static JsonStringView encoded(
        String body)
    {
        final byte[] bytes = body.getBytes(UTF_8);

        return new JsonStringView.Encoded().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
    }

    private JsonGeneratorEx generator()
    {
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        generator.wrap(buffer, 0, buffer.capacity());
        return generator;
    }

    private String output(
        JsonGeneratorEx generator)
    {
        final byte[] bytes = new byte[generator.length()];
        buffer.getBytes(0, bytes);
        return new String(bytes, UTF_8);
    }
}
