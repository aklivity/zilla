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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class McpGrpcJsonDocumentTest
{
    private final McpGrpcJsonDocument document = new McpGrpcJsonDocument();

    @Test
    public void shouldCompleteWhenTopLevelObjectCloses()
    {
        assertTrue(feed("{\"name\":\"a\",\"arguments\":{\"sku\":\"1\"}}"));
    }

    @Test
    public void shouldNotCompleteWhileObjectIsOpen()
    {
        assertFalse(feed("{\"name\":\"a\",\"arguments\":{\"sku\":\"1\""));
    }

    @Test
    public void shouldIgnoreBracesInsideStrings()
    {
        assertFalse(feed("{\"a\":\"}}\""));
        assertTrue(feed("}"));
    }

    @Test
    public void shouldIgnoreEscapedQuotesInsideStrings()
    {
        assertFalse(feed("{\"a\":\"x\\\"}"));
        assertTrue(feed("\"}"));
    }

    @Test
    public void shouldCompleteAcrossWindows()
    {
        assertFalse(feed("{\"name\":\"a\",\"argu"));
        assertFalse(feed("ments\":{\"sku\":\"1\"}"));
        assertTrue(feed("}"));
    }

    @Test
    public void shouldSkipLeadingWhitespace()
    {
        assertFalse(feed("  \n"));
        assertTrue(feed("{}"));
    }

    @Test
    public void shouldResetForNextDocument()
    {
        assertTrue(feed("{}"));
        document.reset();
        assertFalse(feed("{\"a\":"));
    }

    private boolean feed(
        String text)
    {
        final byte[] bytes = text.getBytes(UTF_8);
        return document.advance(new UnsafeBufferEx(bytes), 0, bytes.length);
    }
}
