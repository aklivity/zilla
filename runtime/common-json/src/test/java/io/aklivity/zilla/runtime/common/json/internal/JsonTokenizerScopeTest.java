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
package io.aklivity.zilla.runtime.common.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;

public class JsonTokenizerScopeTest
{
    @Test
    public void shouldEndAKeyAndAStringOfAScopeBeforeTheEscapeOfTheirClosingQuote()
    {
        final JsonTokenizerImpl tokenizer = new JsonTokenizerImpl(false);
        final DirectBufferEx window = wrap(tokenizer, "{\"a\":\"{\\\"b\\\":\\\"x\\\\ny\\\"}\"}");

        advance(tokenizer, JsonEvent.START_OBJECT);
        advance(tokenizer, JsonEvent.KEY_NAME);
        tokenizer.escape();
        advance(tokenizer, JsonEvent.START_ESCAPED);
        advance(tokenizer, JsonEvent.START_OBJECT);

        advance(tokenizer, JsonEvent.KEY_NAME);
        assertEquals("b", token(tokenizer, window));
        assertEquals(1, tokenizer.tokenChars());

        advance(tokenizer, JsonEvent.VALUE_STRING);
        assertEquals("x\\\\ny", token(tokenizer, window));
        assertEquals(3, tokenizer.tokenChars());
    }

    @Test
    public void shouldEndAStringOfAScopeBeforeAClosingQuoteSpelledAsAUnicodeEscape()
    {
        final JsonTokenizerImpl tokenizer = new JsonTokenizerImpl(false);
        final DirectBufferEx window = wrap(tokenizer, "{\"a\":\"{\\\"b\\\":\\\"xy\\u0022}\"}");

        advance(tokenizer, JsonEvent.START_OBJECT);
        advance(tokenizer, JsonEvent.KEY_NAME);
        tokenizer.escape();
        advance(tokenizer, JsonEvent.START_ESCAPED);
        advance(tokenizer, JsonEvent.START_OBJECT);
        advance(tokenizer, JsonEvent.KEY_NAME);

        advance(tokenizer, JsonEvent.VALUE_STRING);
        assertEquals("xy", token(tokenizer, window));
    }

    @Test
    public void shouldEndAStringOutsideAScopeBeforeItsClosingQuote()
    {
        final JsonTokenizerImpl tokenizer = new JsonTokenizerImpl(false);
        final DirectBufferEx window = wrap(tokenizer, "[\"xy\"]");

        advance(tokenizer, JsonEvent.START_ARRAY);
        advance(tokenizer, JsonEvent.VALUE_STRING);
        assertEquals("xy", token(tokenizer, window));
    }

    private static void advance(
        JsonTokenizerImpl tokenizer,
        JsonEvent expected)
    {
        assertTrue(tokenizer.advance());
        assertEquals(expected, tokenizer.event());
        tokenizer.clearEvent();
    }

    private static String token(
        JsonTokenizerImpl tokenizer,
        DirectBufferEx window)
    {
        return window.getStringWithoutLengthUtf8(tokenizer.tokenOffset(), tokenizer.tokenLength());
    }

    private static DirectBufferEx wrap(
        JsonTokenizerImpl tokenizer,
        String content)
    {
        final byte[] bytes = content.getBytes(UTF_8);
        final DirectBufferEx buffer = new UnsafeBufferEx(bytes);
        tokenizer.wrap(buffer, 0, bytes.length, true);
        tokenizer.window(bytes.length);
        return buffer;
    }
}
