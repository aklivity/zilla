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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonStringView;
import io.aklivity.zilla.runtime.common.json.JsonTokenizer;

// This tokenizer always decodes into scratch (a StringBuilder), so it never has a contiguous run of
// source bytes corresponding 1:1 to a value's decoded chars once escapes or multi-byte UTF-8 are folded
// in -- stringView()'s getSegment() must always report that honestly (null) rather than a caller
// mistakenly treating decoded content as splice-able source bytes.
public class JsonTokenizerStringViewTest
{
    @Test
    public void shouldReturnNullSegmentForKeyStringView()
    {
        final JsonTokenizer tokenizer = new JsonTokenizerImpl();
        wrap(tokenizer, "{\"a\":1}");

        assertTrue(tokenizer.advance());
        assertTrue(tokenizer.advance());
        final JsonStringView view = tokenizer.stringView();

        assertEquals("a", view.toString());
        assertNull(view.getSegment());
    }

    @Test
    public void shouldReturnNullSegmentForEscapedStringView()
    {
        final JsonTokenizer tokenizer = new JsonTokenizerImpl();
        wrap(tokenizer, "[\"a\\nb\"]");

        assertTrue(tokenizer.advance());
        assertTrue(tokenizer.advance());
        final JsonStringView view = tokenizer.stringView();

        assertEquals("a\nb", view.toString());
        assertNull(view.getSegment());
    }

    @Test
    public void shouldReturnNullSegmentForNumberStringView()
    {
        final JsonTokenizer tokenizer = new JsonTokenizerImpl();
        wrap(tokenizer, "[42]");

        assertTrue(tokenizer.advance());
        assertTrue(tokenizer.advance());
        final JsonStringView view = tokenizer.stringView();

        assertEquals("42", view.toString());
        assertNull(view.getSegment());
    }

    private static void wrap(
        JsonTokenizer tokenizer,
        String json)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        tokenizer.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);
    }
}
