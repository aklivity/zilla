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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class JsonTokenizerWrapTest
{
    @Test
    public void shouldRejectRewindOnceTheMarkedWindowHasBeenReplaced()
    {
        final JsonTokenizerImpl tokenizer = new JsonTokenizerImpl(Integer.MAX_VALUE, false);
        wrap(tokenizer, "{\"a\":1}", false);

        assertTrue(tokenizer.advance());
        assertEquals(JsonParser.Event.START_OBJECT, tokenizer.event());
        tokenizer.clearEvent();
        tokenizer.mark();

        assertTrue(tokenizer.advance());
        assertEquals(JsonParser.Event.KEY_NAME, tokenizer.event());
        tokenizer.clearEvent();
        assertTrue(tokenizer.advance());
        assertEquals(JsonParser.Event.VALUE_NUMBER, tokenizer.event());
        tokenizer.clearEvent();

        // a later window replaces the one the mark was taken in, well past the marked offset
        wrap(tokenizer, "ignored", false);

        final IllegalStateException ex = assertThrows(IllegalStateException.class, tokenizer::rewind);
        assertEquals("rewind requires the window the mark was taken in", ex.getMessage());
    }

    @Test
    public void shouldAlignOnTheValueBoundaryAfterAQuotedKeySpansWindows()
    {
        final JsonTokenizerImpl tokenizer = new JsonTokenizerImpl(Integer.MAX_VALUE, true);
        wrap(tokenizer, "{\"a\"", false);

        assertTrue(tokenizer.advance());
        assertEquals(JsonParser.Event.START_OBJECT, tokenizer.event());
        tokenizer.clearEvent();

        // the key itself is whole in this window, but its separator/whitespace/value start are not: the
        // key is rolled back rather than delivered early
        assertFalse(tokenizer.advance());

        wrap(tokenizer, "\"a\":\"value\"}", true);

        assertTrue(tokenizer.advance());
        assertEquals(JsonParser.Event.KEY_NAME, tokenizer.event());
        assertEquals("a", tokenizer.stringValue());
        final long positionAfterKey = tokenizer.streamOffset();
        tokenizer.clearEvent();

        assertTrue(tokenizer.advance());
        assertEquals(JsonParser.Event.VALUE_STRING, tokenizer.event());
        assertEquals(positionAfterKey, tokenizer.valueStreamStart());
    }

    private static void wrap(
        JsonTokenizerImpl tokenizer,
        String content,
        boolean last)
    {
        final byte[] bytes = content.getBytes(UTF_8);
        final DirectBufferEx buffer = new UnsafeBufferEx(bytes);
        tokenizer.wrap(buffer, 0, bytes.length, last);
        tokenizer.window(bytes.length);
    }
}
