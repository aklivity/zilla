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
        McpSearchToolCallArgs args = scan("{\"name\":\"zilla__search_tools\",\"arguments\":{\"query\":\"weather\"}}");

        assertThat(args.query, equalTo("weather"));
        assertThat(args.maxResults, equalTo(0));
    }

    @Test
    public void shouldScanQueryAndMaxResults()
    {
        McpSearchToolCallArgs args = scan(
            "{\"name\":\"zilla__search_tools\",\"arguments\":{\"query\":\"weather\",\"max_results\":3}}");

        assertThat(args.query, equalTo("weather"));
        assertThat(args.maxResults, equalTo(3));
    }

    @Test
    public void shouldReturnNullQueryWhenMissing()
    {
        McpSearchToolCallArgs args = scan("{\"name\":\"zilla__search_tools\",\"arguments\":{}}");

        assertThat(args.query, nullValue());
    }

    @Test
    public void shouldReturnNullForMalformedJson()
    {
        McpSearchToolCallArgs args = scan("not valid json at all");

        assertThat(args, nullValue());
    }

    private static McpSearchToolCallArgs scan(
        String json)
    {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return McpSearchToolCallScanner.scan(new UnsafeBufferEx(bytes), 0, bytes.length);
    }
}
