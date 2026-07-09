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
package io.aklivity.zilla.runtime.binding.mcp.internal.search;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class McpSearchToolCallScannerTest
{
    @Test
    public void shouldScanQuery()
    {
        McpSearchToolCallScanner scanner = scan("{\"name\":\"zilla__search_tools\",\"arguments\":{\"query\":\"weather\"}}");

        assertThat(scanner.query, equalTo("weather"));
        assertThat(scanner.maxResults, equalTo(0));
        assertThat(scanner.malformed, equalTo(false));
    }

    @Test
    public void shouldScanQueryAndMaxResults()
    {
        McpSearchToolCallScanner scanner = scan(
            "{\"name\":\"zilla__search_tools\",\"arguments\":{\"query\":\"weather\",\"max_results\":3}}");

        assertThat(scanner.query, equalTo("weather"));
        assertThat(scanner.maxResults, equalTo(3));
    }

    @Test
    public void shouldReturnNullQueryWhenMissing()
    {
        McpSearchToolCallScanner scanner = scan("{\"name\":\"zilla__search_tools\",\"arguments\":{}}");

        assertThat(scanner.query, nullValue());
    }

    @Test
    public void shouldReturnNullForMalformedJson()
    {
        McpSearchToolCallScanner scanner = scan("not valid json at all");

        assertThat(scanner.query, nullValue());
        assertThat(scanner.malformed, equalTo(true));
    }

    @Test
    public void shouldScanQuerySplitAcrossWindowBoundary()
    {
        final byte[] bytes =
            "{\"name\":\"zilla__search_tools\",\"arguments\":{\"query\":\"weather forecast\"}}"
                .getBytes(StandardCharsets.UTF_8);

        for (int split = 1; split < bytes.length; split++)
        {
            final McpSearchToolCallScanner scanner = new McpSearchToolCallScanner();
            final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
            scanner.feed(buffer, 0, split, false);
            scanner.feed(buffer, split, bytes.length - split, true);

            assertThat("failed at split=" + split, scanner.query, equalTo("weather forecast"));
        }
    }

    @Test
    public void shouldScanQueryFedOneByteAtATime()
    {
        final byte[] bytes =
            "{\"name\":\"zilla__search_tools\",\"arguments\":{\"query\":\"weather\",\"max_results\":12}}"
                .getBytes(StandardCharsets.UTF_8);

        final McpSearchToolCallScanner scanner = new McpSearchToolCallScanner();
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        for (int i = 0; i < bytes.length; i++)
        {
            scanner.feed(buffer, i, 1, i == bytes.length - 1);
        }

        assertThat(scanner.query, equalTo("weather"));
        assertThat(scanner.maxResults, equalTo(12));
    }

    private static McpSearchToolCallScanner scan(
        String json)
    {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        McpSearchToolCallScanner scanner = new McpSearchToolCallScanner();
        scanner.feed(new UnsafeBufferEx(bytes), 0, bytes.length, true);
        return scanner;
    }
}
