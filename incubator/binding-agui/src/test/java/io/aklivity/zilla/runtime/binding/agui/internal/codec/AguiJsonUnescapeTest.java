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
package io.aklivity.zilla.runtime.binding.agui.internal.codec;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class AguiJsonUnescapeTest
{
    private final MutableDirectBufferEx target = new UnsafeBufferEx(new byte[256]);

    @Test
    public void shouldUnescapeSpecials()
    {
        final String text = "a\\\"b\\\\c\\bd\\fe\\nf\\rg\\th\\/i\\u0001j";

        assertThat(unescape(text), equalTo("a\"b\\c\bd\fe\nf\rg\th/i\u0001j"));
    }

    @Test
    public void shouldUnescapeUnicode()
    {
        assertThat(unescape("caf\\u00e9 \\u20AC"), equalTo("café €"));
    }

    @Test
    public void shouldUnescapeSurrogatePair()
    {
        assertThat(unescape("\\ud83d\\ude00"), equalTo("😀"));
    }

    @Test
    public void shouldCopyMultibyteVerbatim()
    {
        assertThat(unescape("café € 😀"), equalTo("café € 😀"));
    }

    @Test
    public void shouldNotGrow()
    {
        final byte[] bytes = "\\u0041\\n".getBytes(UTF_8);

        final int limit = AguiJsonUnescape.unescape(new UnsafeBufferEx(bytes), 0, bytes.length, target, 0);

        assertThat(limit, equalTo(2));
    }

    @Test
    public void shouldReportCompleteLengthOfCompleteInput()
    {
        final byte[] bytes = "ab\\ncd".getBytes(UTF_8);

        assertThat(AguiJsonUnescape.completeLength(new UnsafeBufferEx(bytes), 0, bytes.length), equalTo(bytes.length));
    }

    @Test
    public void shouldExcludePartialEscape()
    {
        assertThat(complete("ab\\"), equalTo(2));
        assertThat(complete("ab\\u"), equalTo(2));
        assertThat(complete("ab\\u00"), equalTo(2));
        assertThat(complete("ab\\u00e"), equalTo(2));
        assertThat(complete("ab\\u00e9"), equalTo(8));
    }

    @Test
    public void shouldExcludePartialSurrogatePair()
    {
        assertThat(complete("ab\\ud83d"), equalTo(2));
        assertThat(complete("ab\\ud83d\\"), equalTo(2));
        assertThat(complete("ab\\ud83d\\ude0"), equalTo(2));
        assertThat(complete("ab\\ud83d\\ude00"), equalTo(14));
    }

    @Test
    public void shouldExcludePartialMultibyte()
    {
        final byte[] bytes = "ab€".getBytes(UTF_8);

        assertThat(AguiJsonUnescape.completeLength(new UnsafeBufferEx(bytes), 0, bytes.length - 1), equalTo(2));
        assertThat(AguiJsonUnescape.completeLength(new UnsafeBufferEx(bytes), 0, bytes.length), equalTo(bytes.length));
    }

    @Test
    public void shouldRejectUnknownEscape()
    {
        assertThat(complete("a\\xb"), equalTo(AguiJsonUnescape.MALFORMED));
    }

    @Test
    public void shouldRejectInvalidHex()
    {
        assertThat(complete("a\\u00zz"), equalTo(AguiJsonUnescape.MALFORMED));
    }

    @Test
    public void shouldRejectLoneLowSurrogate()
    {
        assertThat(complete("a\\ude00"), equalTo(AguiJsonUnescape.MALFORMED));
    }

    @Test
    public void shouldRejectHighSurrogateWithoutLowSurrogate()
    {
        assertThat(complete("a\\ud83d\\u0041"), equalTo(AguiJsonUnescape.MALFORMED));
        assertThat(complete("a\\ud83dz"), equalTo(AguiJsonUnescape.MALFORMED));
    }

    @Test
    public void shouldRejectInvalidUtf8()
    {
        final byte[] bytes = { 'a', (byte) 0xc3, 'b' };

        assertThat(AguiJsonUnescape.completeLength(new UnsafeBufferEx(bytes), 0, bytes.length),
            equalTo(AguiJsonUnescape.MALFORMED));
    }

    private int complete(
        String text)
    {
        final byte[] bytes = text.getBytes(UTF_8);

        return AguiJsonUnescape.completeLength(new UnsafeBufferEx(bytes), 0, bytes.length);
    }

    private String unescape(
        String text)
    {
        final byte[] bytes = text.getBytes(UTF_8);

        final int limit = AguiJsonUnescape.unescape(new UnsafeBufferEx(bytes), 0, bytes.length, target, 0);

        return target.getStringWithoutLengthUtf8(0, limit);
    }
}
