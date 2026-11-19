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
package io.aklivity.zilla.runtime.binding.mcp.grpc.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class McpGrpcJsonTest
{
    private final MutableDirectBufferEx out = new UnsafeBufferEx(new byte[256]);

    @Test
    public void shouldEscapeQuotesAndBackslashes()
    {
        final byte[] in = "{\"a\":\"b\\c\"}".getBytes(UTF_8);

        final int limit = McpGrpcJson.putEscaped(out, 0, new UnsafeBufferEx(in), 0, in.length);

        assertEquals("{\\\"a\\\":\\\"b\\\\c\\\"}", text(0, limit));
    }

    @Test
    public void shouldEscapeControlCharacters()
    {
        final byte[] in = "a\n\t\r\b\f\u0001z".getBytes(UTF_8);

        final int limit = McpGrpcJson.putEscaped(out, 0, new UnsafeBufferEx(in), 0, in.length);

        assertEquals("a\\n\\t\\r\\b\\f\\u0001z", text(0, limit));
    }

    @Test
    public void shouldCopyMultiByteCharactersVerbatim()
    {
        final byte[] in = "héllo €".getBytes(UTF_8);

        final int limit = McpGrpcJson.putEscaped(out, 4, new UnsafeBufferEx(in), 0, in.length);

        assertEquals("héllo €", text(4, limit));
    }

    @Test
    public void shouldBoundExpansionToSixTimesInput()
    {
        assertEquals(60, McpGrpcJson.maxEscaped(10));
    }

    @Test
    public void shouldEscapeText()
    {
        final int limit = McpGrpcJson.putEscaped(out, 0, "say \"hi\"\n");

        assertEquals("say \\\"hi\\\"\\n", text(0, limit));
    }

    private String text(
        int offset,
        int limit)
    {
        final byte[] result = new byte[limit - offset];
        out.getBytes(offset, result);
        return new String(result, UTF_8);
    }
}
