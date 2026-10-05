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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

public class LlmJsonLiteralTest
{
    private static final int PREFIX_EVENTS = 3;

    @Test
    public void shouldCaptureNumber()
    {
        assertEquals("0.70", capture("0.70", 0));
    }

    @Test
    public void shouldCaptureInteger()
    {
        assertEquals("-42", capture("-42", 0));
    }

    @Test
    public void shouldCaptureBooleans()
    {
        assertEquals("true", capture("true", 0));
        assertEquals("false", capture("false", 0));
        assertEquals("null", capture("null", 0));
    }

    @Test
    public void shouldCaptureString()
    {
        assertEquals("\"json\"", capture("\"json\"", 0));
    }

    @Test
    public void shouldCaptureEscapedString()
    {
        assertEquals("\"a\\\"b\\\\c\\n\"", capture("\"a\\\"b\\\\c\\n\"", 0));
    }

    @Test
    public void shouldCaptureArray()
    {
        assertEquals("[\"END\",\"STOP\"]", capture("[\"END\", \"STOP\"]", 0));
    }

    @Test
    public void shouldCaptureEmptyArray()
    {
        assertEquals("[]", capture("[]", 0));
    }

    @Test
    public void shouldCaptureObject()
    {
        assertEquals("{\"include_usage\":true,\"n\":[1,2],\"o\":{}}",
            capture("{ \"include_usage\" : true, \"n\" : [1, 2], \"o\": {} }", 0));
    }

    @Test
    public void shouldCaptureStringAcrossWindows()
    {
        for (int window = 8; window < 24; window++)
        {
            assertEquals("\"abcdefghijklmnop\"", capture("\"abcdefghijklmnop\"", window));
        }
    }

    @Test
    public void shouldCaptureObjectAcrossWindows()
    {
        for (int window = 9; window < 40; window++)
        {
            assertEquals("{\"include_usage\":true,\"stop\":[\"END\"]}",
                capture("{\"include_usage\":true,\"stop\":[\"END\"]}", window));
        }
    }

    @Test
    public void shouldOverflowOversizedString()
    {
        final LlmJsonLiteral literal = new LlmJsonLiteral();
        final String oversized = "\"" + "x".repeat(LlmJsonLiteral.LITERAL_MAX + 1) + "\"";

        assertTrue(drive(literal, oversized, 0));
        assertTrue(literal.overflow());
    }

    @Test
    public void shouldResetForReuse()
    {
        final LlmJsonLiteral literal = new LlmJsonLiteral();

        assertTrue(drive(literal, "[1]", 0));
        assertEquals("[1]", literal.value());

        literal.reset();
        assertTrue(drive(literal, "2", 0));
        assertEquals("2", literal.value());
        assertFalse(literal.overflow());
    }

    @Test
    public void shouldQuote()
    {
        assertEquals("\"fp_abc\"", LlmJsonLiteral.quote("fp_abc"));
        assertEquals("\"a\\\"b\\\\\\n\"", LlmJsonLiteral.quote("a\"b\\\n"));
    }

    private static String capture(
        String value,
        int window)
    {
        final LlmJsonLiteral literal = new LlmJsonLiteral();

        assertTrue(drive(literal, value, window));
        assertFalse(literal.overflow());

        return literal.value();
    }

    private static boolean drive(
        LlmJsonLiteral literal,
        String value,
        int window)
    {
        final byte[] bytes = ("{\"x\":" + value + "}").getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final JsonParserEx parser = JsonEx.createParser();

        if (window == 0)
        {
            parser.wrap(buffer, 0, bytes.length, true);
        }
        else
        {
            parser.wrap(buffer, 0, Math.min(window, bytes.length), false);
        }

        for (int event = 0; event < PREFIX_EVENTS; event++)
        {
            parser.nextEvent();
        }

        boolean complete = pump(literal, parser);

        if (!complete && window != 0)
        {
            parser.wrap(buffer, window - parser.remaining(), bytes.length, true);
            complete = pump(literal, parser);
        }

        return complete;
    }

    private static boolean pump(
        LlmJsonLiteral literal,
        JsonParserEx parser)
    {
        boolean complete = false;

        while (!complete && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            complete = literal.accept(event, parser);
        }

        return complete;
    }
}
