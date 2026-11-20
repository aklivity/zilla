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

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.view.JsonStringView;

// A string value consumed in small steps is read from where the previous step stopped, so the cost of reading
// the whole value grows linearly with its length, not with the square of it.
class JsonParserStringViewResumeTest
{
    private static final int STEP = 16;

    @Test
    void shouldResumePlainValueWhereConsumptionStopped()
    {
        final String value = "abcdefghij".repeat(37);

        assertEquals(value, resume(quoted(value), STEP));
    }

    @Test
    void shouldResumeEscapedValueWhereConsumptionStopped()
    {
        final String decoded = "line\n\"quoted\"\\ é€ 😀 end".repeat(23);
        final String encoded = "line\\n\\\"quoted\\\"\\\\ é€ \\ud83d\\ude00 end".repeat(23);

        assertEquals(decoded, resume(quoted(encoded), STEP));
    }

    @Test
    void shouldResumeAtEveryStepSize()
    {
        final String decoded = "a\nb😀cé".repeat(11);
        final String encoded = "a\\nb\\ud83d\\ude00cé".repeat(11);

        for (int step = 1; step <= 33; step++)
        {
            assertEquals(decoded, resume(quoted(encoded), step), "step " + step);
        }
    }

    @Test
    void shouldReadPlainValueInLinearTime()
    {
        assertLinear("x".repeat(1));
    }

    @Test
    void shouldReadEscapedValueInLinearTime()
    {
        assertLinear("a\\nb");
    }

    private static void assertLinear(
        String unit)
    {
        final byte[] small = quoted(unit.repeat(100_000 / unit.length())).getBytes(UTF_8);
        final byte[] large = quoted(unit.repeat(400_000 / unit.length())).getBytes(UTF_8);

        for (int warmup = 0; warmup < 3; warmup++)
        {
            resume(small, STEP);
        }

        final long smallNanos = best(small);
        final long largeNanos = best(large);

        assertTrue(largeNanos < smallNanos * 8,
            "4x the value took " + largeNanos / smallNanos + "x the time: small " + smallNanos / 1_000_000 +
                " ms, large " + largeNanos / 1_000_000 + " ms");
    }

    private static long best(
        byte[] bytes)
    {
        long best = Long.MAX_VALUE;

        for (int run = 0; run < 3; run++)
        {
            final long start = System.nanoTime();
            resume(bytes, STEP);
            best = Math.min(best, System.nanoTime() - start);
        }

        return best;
    }

    private static String quoted(
        String body)
    {
        return "\"" + body + "\"";
    }

    private static String resume(
        String json,
        int step)
    {
        return resume(json.getBytes(UTF_8), step);
    }

    private static String resume(
        byte[] bytes,
        int step)
    {
        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        JsonEvent event = parser.nextEvent();
        while (event != JsonEvent.VALUE_STRING)
        {
            event = parser.nextEvent();
        }

        final StringBuilder resumed = new StringBuilder();
        for (JsonStringView view = parser.getStringView(); view.length() > 0; view = parser.getStringView())
        {
            final int taken = Math.min(step, view.length());
            for (int index = 0; index < taken; index++)
            {
                resumed.append(view.charAt(index));
            }
            parser.consumed(taken);
        }

        return resumed.toString();
    }
}
