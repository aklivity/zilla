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
package io.aklivity.zilla.runtime.binding.mcp.internal.search;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import java.util.Arrays;

import org.junit.Test;

public class McpJsonStringEscaperTest
{
    @Test
    public void shouldPassThroughPlainBytes()
    {
        final byte[] src = "get_weather".getBytes(UTF_8);

        assertThat(escape(src), equalTo(src));
    }

    @Test
    public void shouldEscapeQuote()
    {
        final byte[] src = {'a', '"', 'b'};
        final byte[] expected = {'a', '\\', '"', 'b'};

        assertThat(escape(src), equalTo(expected));
    }

    @Test
    public void shouldEscapeBackslash()
    {
        final byte[] src = {'a', '\\', 'b'};
        final byte[] expected = {'a', '\\', '\\', 'b'};

        assertThat(escape(src), equalTo(expected));
    }

    @Test
    public void shouldEscapeControlByte()
    {
        final byte[] src = {'a', 0x09, 'b'};
        final byte[] expected = {'a', '\\', 'u', '0', '0', '0', '9', 'b'};

        assertThat(escape(src), equalTo(expected));
    }

    @Test
    public void shouldEscapeControlByteRequiringBothHexDigits()
    {
        final byte[] src = {(byte) 0x1f};
        final byte[] expected = {'\\', 'u', '0', '0', '1', 'f'};

        assertThat(escape(src), equalTo(expected));
    }

    @Test
    public void shouldBoundMaxEscapedLengthForAllControlBytes()
    {
        final byte[] src = new byte[16];
        Arrays.fill(src, (byte) 0x01);
        final byte[] dst = new byte[McpJsonStringEscaper.maxEscapedLength(src.length)];

        final int produced = McpJsonStringEscaper.escape(src, 0, src.length, dst, 0);

        assertThat(dst.length, greaterThanOrEqualTo(produced));
        assertThat(produced, equalTo(src.length * 6));
    }

    private static byte[] escape(
        byte[] src)
    {
        final byte[] dst = new byte[McpJsonStringEscaper.maxEscapedLength(src.length)];
        final int produced = McpJsonStringEscaper.escape(src, 0, src.length, dst, 0);
        return Arrays.copyOf(dst, produced);
    }
}
