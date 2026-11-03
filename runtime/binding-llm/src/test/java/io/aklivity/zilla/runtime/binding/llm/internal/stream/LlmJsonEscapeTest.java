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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmJsonEscapeTest
{
    @Test
    public void shouldEscapeQuotesBackslashesAndControls()
    {
        final String text = "a\"b\\c\nd\re\tf\bg\fh\u0001i";

        assertThat(escape(text), equalTo("a\\\"b\\\\c\\nd\\re\\tf\\bg\\fh\\u0001i"));
    }

    @Test
    public void shouldPassNonAscii()
    {
        assertThat(escape("caf\u00e9 \u20ac"), equalTo("caf\u00e9 \u20ac"));
    }

    @Test
    public void shouldMeasureEscapedLength()
    {
        final String text = "a\"b\\c\nd\u0001i\u00e9";
        final byte[] bytes = text.getBytes(UTF_8);

        assertThat(LlmJsonEscape.escapedLength(new UnsafeBufferEx(bytes), 0, bytes.length),
            is(escape(text).getBytes(UTF_8).length));
    }

    private static String escape(
        String text)
    {
        final byte[] bytes = text.getBytes(UTF_8);
        final byte[] target = new byte[bytes.length * LlmJsonEscape.EXPANSION];
        final int length = LlmJsonEscape.escape(new UnsafeBufferEx(bytes), 0, bytes.length, target);

        return new String(target, 0, length, UTF_8);
    }
}
