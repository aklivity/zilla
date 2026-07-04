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
package io.aklivity.zilla.runtime.common.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.stream.JsonParser.Event;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonTokenizer;

// Direct, white-box coverage of numberIntegral()/numberInLongRange()/numberLongValue() -- the fused
// digit accumulation JsonParserImpl.getInt()/getLong() build their fast path on. JsonParserTest's own
// getInt()/getLong() tests exercise these transitively; this pins the tokenizer-level contract each of
// the three methods promises independent of that caller.
public class JsonTokenizerNumberValueTest
{
    @Test
    public void shouldReportIntegralAndInLongRangeForPlainInteger()
    {
        final JsonTokenizer tokenizer = wrap("42");

        assertEquals(Event.VALUE_NUMBER, tokenizer.event());
        assertTrue(tokenizer.numberIntegral());
        assertTrue(tokenizer.numberInLongRange());
        assertEquals(42L, tokenizer.numberLongValue());
    }

    @Test
    public void shouldReportNegativeValue()
    {
        final JsonTokenizer tokenizer = wrap("-42");

        assertTrue(tokenizer.numberIntegral());
        assertTrue(tokenizer.numberInLongRange());
        assertEquals(-42L, tokenizer.numberLongValue());
    }

    @Test
    public void shouldReportZeroForNegativeZero()
    {
        final JsonTokenizer tokenizer = wrap("-0");

        assertTrue(tokenizer.numberIntegral());
        assertTrue(tokenizer.numberInLongRange());
        assertEquals(0L, tokenizer.numberLongValue());
    }

    @Test
    public void shouldReportNotIntegralForDecimal()
    {
        final JsonTokenizer tokenizer = wrap("3.14");

        assertFalse(tokenizer.numberIntegral());
    }

    @Test
    public void shouldReportNotIntegralForExponent()
    {
        final JsonTokenizer tokenizer = wrap("1e10");

        assertFalse(tokenizer.numberIntegral());
    }

    @Test
    public void shouldReportInLongRangeAtEighteenDigits()
    {
        final JsonTokenizer tokenizer = wrap("999999999999999999");

        assertTrue(tokenizer.numberIntegral());
        assertTrue(tokenizer.numberInLongRange());
        assertEquals(999999999999999999L, tokenizer.numberLongValue());
    }

    @Test
    public void shouldReportOutOfLongRangeAtNineteenDigits()
    {
        final JsonTokenizer tokenizer = wrap("1000000000000000000");

        assertTrue(tokenizer.numberIntegral());
        assertFalse(tokenizer.numberInLongRange());
    }

    private static JsonTokenizer wrap(
        String number)
    {
        final JsonTokenizer tokenizer = new JsonTokenizerImpl();
        final byte[] bytes = number.getBytes(UTF_8);
        tokenizer.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);
        tokenizer.advance();
        return tokenizer;
    }
}
