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

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;

class JsonGeneratorStringRunTest
{
    private static final int[] RUNS = {0, 1, 7, 15, 16, 17, 255, 256, 257, 700};
    private static final String[] SPECIALS = {"\"", "\\", "\n", "\u0001", "é", "€", "😀"};

    private final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[16384]);

    @Test
    void shouldWriteRunsOfPlainCharsLikeOneCodePointAtATime()
    {
        final String text = text();

        for (boolean escaped : new boolean[] {false, true})
        {
            assertEquals(pieces(text, escaped), whole(text, escaped), "escaped " + escaped);
        }
    }

    @Test
    void shouldResumeRunsOfPlainCharsWithinTheOutputBound()
    {
        final String text = text();

        for (boolean escaped : new boolean[] {false, true})
        {
            final String expected = whole(text, escaped);

            for (int bound : new int[] {17, 64, 300})
            {
                assertEquals(expected, bounded(text, escaped, bound), "escaped " + escaped + " bound " + bound);
            }
        }
    }

    private String whole(
        String text,
        boolean escaped)
    {
        final JsonGeneratorEx generator = generator(escaped);

        generator.write(text, Completion.COMPLETE);

        return output(generator.length());
    }

    private String pieces(
        String text,
        boolean escaped)
    {
        final JsonGeneratorEx generator = generator(escaped);

        for (int index = 0; index < text.length(); index += Character.charCount(text.codePointAt(index)))
        {
            generator.write(text.substring(index, index + Character.charCount(text.codePointAt(index))),
                Completion.INCOMPLETE);
        }

        generator.write("", Completion.COMPLETE);

        return output(generator.length());
    }

    private String bounded(
        String text,
        boolean escaped,
        int bound)
    {
        final JsonGeneratorEx generator = generator(escaped);
        final StringBuilder result = new StringBuilder();

        int from = 0;
        int calls = 0;

        generator.wrap(buffer, 0, bound);

        while (from < text.length() && calls++ < 100_000)
        {
            final int before = generator.consumed();

            generator.write(text.substring(from), Completion.COMPLETE);
            from += generator.consumed() - before;
            result.append(output(generator.length()));
            generator.wrap(buffer, 0, bound);
        }

        assertTrue(from == text.length(), "text consumed");

        return result.toString();
    }

    private JsonGeneratorEx generator(
        boolean escaped)
    {
        final JsonGeneratorEx generator = escaped
            ? JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true))
            : JsonEx.createGenerator();

        generator.wrap(buffer, 0, buffer.capacity());

        return generator;
    }

    private String output(
        int length)
    {
        final byte[] bytes = new byte[length];
        buffer.getBytes(0, bytes);

        return new String(bytes, UTF_8);
    }

    private static String text()
    {
        final StringBuilder text = new StringBuilder();

        for (int run : RUNS)
        {
            for (String special : SPECIALS)
            {
                for (int index = 0; index < run; index++)
                {
                    text.append((char) ('a' + index % 26));
                }

                text.append(special);
            }
        }

        return text.toString();
    }
}
