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
package io.aklivity.zilla.runtime.common.json.view;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class EscapedStringViewTest
{
    private static final int PADDING = 3;

    private final JsonStringView.Escaped view = new JsonStringView.Escaped();

    @Test
    void shouldCopyPlainRunsLikeReadingEachChar()
    {
        final String[] decoded = {
            "plain value of some length 0123456789 abcdefghijklmnopqrstuvwxyz without any escapes at all",
            "a\"quoted\" run, then a backslash \\ and a newline \n then plain text again 0123456789abcdefghijklmnop",
            "{\"key\":\"value\",\"list\":[1,2,3],\"text\":\"plain 0123456789abcdefghijklmnopqrstuvwxyz\"}",
            "café € 😀 between plain runs of text 0123456789abcdefghijklmnopqrstuvwxyz",
            ""
        };

        for (String value : decoded)
        {
            final byte[] raw = escape(escape(value)).getBytes(UTF_8);
            final byte[] padded = new byte[PADDING + raw.length + PADDING];
            System.arraycopy(raw, 0, padded, PADDING, raw.length);
            view.wrap(new UnsafeBufferEx(padded), PADDING, raw.length, value.length());

            for (int from = 0; from <= view.length(); from++)
            {
                for (int max : new int[] {0, 1, 7, 8, 9, 64, 4096})
                {
                    final byte[] target = new byte[Math.max(max, 1)];
                    final int copied = view.copyPlain(from, max, target);

                    int expected = 0;
                    while (expected < max && from + expected < value.length() && plain(value.charAt(from + expected)))
                    {
                        expected++;
                    }

                    assertEquals(expected, copied, value + " from " + from + " max " + max);

                    for (int index = 0; index < copied; index++)
                    {
                        assertEquals(value.charAt(from + index), (char) target[index], value + " at " + (from + index));
                    }
                }
            }
        }
    }

    private static boolean plain(
        char c)
    {
        return c >= 0x20 && c < 0x80 && c != '"' && c != '\\';
    }

    private static String escape(
        String value)
    {
        final StringBuilder escaped = new StringBuilder();

        for (int index = 0; index < value.length(); index++)
        {
            final char c = value.charAt(index);

            switch (c)
            {
            case '"':
                escaped.append("\\\"");
                break;
            case '\\':
                escaped.append("\\\\");
                break;
            case '\n':
                escaped.append("\\n");
                break;
            default:
                escaped.append(c);
                break;
            }
        }

        return escaped.toString();
    }
}
