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

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;

// Exercises JsonGeneratorImpl's instanceof JsonStringView fast path directly: a non-null getSegment()
// splices bytes straight into string-body output instead of the ordinary codepoint-by-codepoint escape
// path. No production JsonTokenizer implementation in this repo produces a segment-backed JsonStringView
// yet -- JsonTokenizerImpl's stringView() always reports a null segment, correctly, since it only ever
// decodes into scratch (see JsonTokenizerStringViewTest). This test uses a minimal fixture instead, to
// verify the generator side of the contract independent of any specific producer: given a JsonStringView
// with a segment, the generator either splices it whole (segment fits this call) or declines and falls
// through unchanged to the pre-existing, already-covered codepoint path (segment does not fit) -- it
// never assumes anything about how a producer resumes a value across multiple calls, since that is the
// producer's contract to keep, not the generator's to enforce.
class JsonGeneratorStringViewTest
{
    @Test
    void shouldSpliceSegmentBackedValueUnbounded()
    {
        assertEquals("\"hello\"", generate(g -> g.write(segmentView("hello"))));
    }

    @Test
    void shouldFallBackToOrdinaryPathWhenSegmentIsNullUnbounded()
    {
        assertEquals("\"hello\"", generate(g -> g.write(nullSegmentView("hello"))));
    }

    @Test
    void shouldSpliceSegmentBackedValueBoundedWhenItFits()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[512]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());

        generator.write(segmentView("hello"), Completion.COMPLETE);

        assertEquals(5, generator.consumed());
        assertEquals("\"hello\"", drain(generator, buffer));
    }

    @Test
    void shouldDeclineFastPathAndFallBackWhenSegmentDoesNotFitBoundedOutput()
    {
        // 8-byte window: opening quote (1) + closing-quote reserve (1) leaves 6 bytes of budget, less
        // than the 11-byte segment -- the fast path's own bound check must decline, falling through to
        // the ordinary codepoint path, which partially consumes exactly as it would for a plain String
        MutableDirectBufferEx fastPathBuffer = new UnsafeBufferEx(new byte[8]);
        JsonGeneratorEx fastPathGenerator = JsonEx.createGenerator().wrap(fastPathBuffer, 0, fastPathBuffer.capacity());
        fastPathGenerator.write(segmentView("hello world"), Completion.COMPLETE);

        MutableDirectBufferEx plainBuffer = new UnsafeBufferEx(new byte[8]);
        JsonGeneratorEx plainGenerator = JsonEx.createGenerator().wrap(plainBuffer, 0, plainBuffer.capacity());
        plainGenerator.write("hello world", Completion.COMPLETE);

        assertTrue(fastPathGenerator.consumed() < 11, "fast path must not have spliced the whole segment");
        assertEquals(plainGenerator.consumed(), fastPathGenerator.consumed());
        assertEquals(drain(plainGenerator, plainBuffer), drain(fastPathGenerator, fastPathBuffer));
    }

    private static JsonStringView segmentView(
        String value)
    {
        byte[] bytes = value.getBytes(UTF_8);
        DirectBufferEx segment = new UnsafeBufferEx(bytes);
        return new JsonStringView()
        {
            @Override
            public int length()
            {
                return value.length();
            }

            @Override
            public char charAt(
                int index)
            {
                return value.charAt(index);
            }

            @Override
            public CharSequence subSequence(
                int start,
                int end)
            {
                return value.subSequence(start, end);
            }

            @Override
            public String toString()
            {
                return value;
            }

            @Override
            public DirectBufferEx getSegment()
            {
                return segment;
            }
        };
    }

    private static JsonStringView nullSegmentView(
        String value)
    {
        return new JsonStringView()
        {
            @Override
            public int length()
            {
                return value.length();
            }

            @Override
            public char charAt(
                int index)
            {
                return value.charAt(index);
            }

            @Override
            public CharSequence subSequence(
                int start,
                int end)
            {
                return value.subSequence(start, end);
            }

            @Override
            public String toString()
            {
                return value;
            }

            @Override
            public DirectBufferEx getSegment()
            {
                return null;
            }
        };
    }

    private static String generate(
        Consumer<JsonGeneratorEx> writer)
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[512]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        writer.accept(generator);
        return drain(generator, buffer);
    }

    private static String drain(
        JsonGeneratorEx generator,
        MutableDirectBufferEx buffer)
    {
        byte[] out = new byte[generator.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }
}
