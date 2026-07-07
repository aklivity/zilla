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

// wrap(buffer, offset, limit, last) folds terminal(boolean) into the rebind call; wrap(buffer, offset,
// limit) is the leave-terminal-state-unchanged three-argument form (not a last == true shorthand, unlike
// the JsonParserEx layer above it, since JsonParserImpl's own three-argument wrap() never touched terminal
// state either -- see its wrap(DirectBufferEx, int, int) override).
public class JsonTokenizerWrapTest
{
    @Test
    public void shouldCompleteTrailingNumberWhenWrapMarksLast()
    {
        final JsonTokenizer tokenizer = new JsonTokenizerImpl();
        final byte[] bytes = "42".getBytes(UTF_8);

        tokenizer.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        assertTrue(tokenizer.advance());
        assertEquals(Event.VALUE_NUMBER, tokenizer.event());
        assertEquals("42", tokenizer.stringValue());
    }

    @Test
    public void shouldStarveTrailingNumberWhenWrapDoesNotMarkLast()
    {
        final JsonTokenizer tokenizer = new JsonTokenizerImpl();
        final byte[] bytes = "42".getBytes(UTF_8);

        tokenizer.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, false);

        assertFalse(tokenizer.advance());
    }

    @Test
    public void shouldLeaveTerminalStateUnchangedOnThreeArgumentWrap()
    {
        final JsonTokenizer tokenizer = new JsonTokenizerImpl();

        // "1 " completes independent of terminal state (whitespace, not EOF, ends the number); this call
        // exists only to mark the tokenizer terminal via the four-argument overload before reset()
        final byte[] setup = "1 ".getBytes(UTF_8);
        tokenizer.wrap(new UnsafeBufferEx(setup), 0, setup.length, true);
        assertTrue(tokenizer.advance());
        tokenizer.clearEvent();
        tokenizer.reset();

        // a fresh document, wrapped via the three-argument overload; if it left terminal state unchanged
        // (still true from the earlier four-argument call) the bare trailing number completes at EOF
        // instead of starving
        final byte[] bytes = "42".getBytes(UTF_8);
        tokenizer.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertTrue(tokenizer.advance());
        assertEquals(Event.VALUE_NUMBER, tokenizer.event());
        assertEquals("42", tokenizer.stringValue());
    }
}
