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

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.junit.jupiter.api.Test;

public class JsonStringsTest
{
    private String unescape(
        String body)
    {
        final byte[] bytes = body.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final StringBuilder out = new StringBuilder();
        JsonStrings.unescape(buffer, 0, bytes.length, out);
        return out.toString();
    }

    @Test
    public void shouldDecodePlainAscii()
    {
        assertEquals("hello world", unescape("hello world"));
    }

    @Test
    public void shouldDecodeSimpleEscapes()
    {
        assertEquals("a\nb\tc\r\"\\/\b\f", unescape("a\\nb\\tc\\r\\\"\\\\\\/\\b\\f"));
    }

    @Test
    public void shouldDecodeUnicodeEscape()
    {
        assertEquals("é", unescape("\\u00e9"));
    }

    @Test
    public void shouldDecodeSurrogatePairEscape()
    {
        // U+1F600 GRINNING FACE as a UTF-16 surrogate-pair escape
        assertEquals("😀", unescape("\\ud83d\\ude00"));
    }

    @Test
    public void shouldDecodeRawUtf8Multibyte()
    {
        assertEquals("café é € 😀",
            unescape("café é € 😀"));
    }

    @Test
    public void shouldDecodeAtNonZeroOffset()
    {
        final byte[] bytes = "XXa\\nbYY".getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final StringBuilder out = new StringBuilder();
        JsonStrings.unescape(buffer, 2, 4, out);
        assertEquals("a\nb", out.toString());
    }
}
