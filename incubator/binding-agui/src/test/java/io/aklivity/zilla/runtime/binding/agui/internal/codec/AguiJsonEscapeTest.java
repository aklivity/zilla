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

public class AguiJsonEscapeTest
{
    private final MutableDirectBufferEx target = new UnsafeBufferEx(new byte[256]);

    @Test
    public void shouldReportGrowth()
    {
        assertThat(AguiJsonEscape.growth(), equalTo(6));
    }

    @Test
    public void shouldMeasureEscapedWidth()
    {
        assertThat(AguiJsonEscape.escapedWidth('a'), equalTo(1));
        assertThat(AguiJsonEscape.escapedWidth('"'), equalTo(2));
        assertThat(AguiJsonEscape.escapedWidth('\\'), equalTo(2));
        assertThat(AguiJsonEscape.escapedWidth('\n'), equalTo(2));
        assertThat(AguiJsonEscape.escapedWidth(0x01), equalTo(6));
        assertThat(AguiJsonEscape.escapedWidth(0xc3), equalTo(1));
    }

    @Test
    public void shouldEscapeSpecials()
    {
        final String text = "a\"b\\c\bd\fe\nf\rg\th\u0001i";
        final byte[] bytes = text.getBytes(UTF_8);

        final int limit = AguiJsonEscape.escape(new UnsafeBufferEx(bytes), 0, bytes.length, target, 0);

        assertThat(target.getStringWithoutLengthUtf8(0, limit), equalTo("a\\\"b\\\\c\\bd\\fe\\nf\\rg\\th\\u0001i"));
    }

    @Test
    public void shouldEscapeMultibyteVerbatim()
    {
        final byte[] bytes = "héllo € 😀".getBytes(UTF_8);

        final int limit = AguiJsonEscape.escape(new UnsafeBufferEx(bytes), 0, bytes.length, target, 0);

        assertThat(target.getStringWithoutLengthUtf8(0, limit), equalTo("héllo € 😀"));
    }

    @Test
    public void shouldEscapeStringValue()
    {
        assertThat(AguiJsonEscape.escape("a\"b\\c\bd\fe\nf\rg\th\u0001i"), equalTo("a\\\"b\\\\c\\bd\\fe\\nf\\rg\\th\\u0001i"));
    }

    @Test
    public void shouldMeasureCompleteSequences()
    {
        final byte[] bytes = "aé€😀".getBytes(UTF_8);

        assertThat(AguiJsonEscape.completeLength(new UnsafeBufferEx(bytes), 0, bytes.length), equalTo(bytes.length));
    }

    @Test
    public void shouldMeasureCompleteSequencesBeforePartialTail()
    {
        final byte[] bytes = "a€".getBytes(UTF_8);

        assertThat(AguiJsonEscape.completeLength(new UnsafeBufferEx(bytes), 0, bytes.length - 1), equalTo(1));
        assertThat(AguiJsonEscape.completeLength(new UnsafeBufferEx(bytes), 0, bytes.length - 2), equalTo(1));
    }

    @Test
    public void shouldMeasureEmptyAsComplete()
    {
        assertThat(AguiJsonEscape.completeLength(new UnsafeBufferEx(new byte[0]), 0, 0), equalTo(0));
    }

    @Test
    public void shouldRejectStrayContinuation()
    {
        final byte[] bytes = new byte[] {(byte) 0x80};

        assertThat(AguiJsonEscape.completeLength(new UnsafeBufferEx(bytes), 0, 1), equalTo(AguiJsonEscape.MALFORMED));
    }

    @Test
    public void shouldRejectInvalidContinuation()
    {
        final byte[] bytes = new byte[] {(byte) 0xe2, 0x41, 0x41};

        assertThat(AguiJsonEscape.completeLength(new UnsafeBufferEx(bytes), 0, 3), equalTo(AguiJsonEscape.MALFORMED));
    }
}
