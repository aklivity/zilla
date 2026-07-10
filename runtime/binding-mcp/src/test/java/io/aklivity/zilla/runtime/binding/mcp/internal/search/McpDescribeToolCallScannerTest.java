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

public class McpDescribeToolCallScannerTest
{
    @Test
    public void shouldScanName()
    {
        McpDescribeToolCallScanner scanner =
            scan("{\"name\":\"zilla__describe_tool\",\"arguments\":{\"name\":\"get_weather\"}}");

        assertThat(scanner.name, equalTo("get_weather"));
        assertThat(scanner.malformed, equalTo(false));
    }

    @Test
    public void shouldReturnNullNameWhenMissing()
    {
        McpDescribeToolCallScanner scanner = scan("{\"name\":\"zilla__describe_tool\",\"arguments\":{}}");

        assertThat(scanner.name, nullValue());
    }

    @Test
    public void shouldReturnNullForMalformedJson()
    {
        McpDescribeToolCallScanner scanner = scan("not valid json at all");

        assertThat(scanner.name, nullValue());
        assertThat(scanner.malformed, equalTo(true));
    }

    @Test
    public void shouldScanNameSplitAcrossWindowBoundary()
    {
        final byte[] bytes =
            "{\"name\":\"zilla__describe_tool\",\"arguments\":{\"name\":\"get_weather_forecast\"}}"
                .getBytes(StandardCharsets.UTF_8);

        for (int split = 1; split < bytes.length; split++)
        {
            final McpDescribeToolCallScanner scanner = new McpDescribeToolCallScanner();
            final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
            scanner.feed(buffer, 0, split, false);
            scanner.feed(buffer, split, bytes.length - split, true);

            assertThat("failed at split=" + split, scanner.name, equalTo("get_weather_forecast"));
        }
    }

    @Test
    public void shouldScanNameFedOneByteAtATime()
    {
        final byte[] bytes =
            "{\"name\":\"zilla__describe_tool\",\"arguments\":{\"name\":\"get_weather\"}}"
                .getBytes(StandardCharsets.UTF_8);

        final McpDescribeToolCallScanner scanner = new McpDescribeToolCallScanner();
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        for (int i = 0; i < bytes.length; i++)
        {
            scanner.feed(buffer, i, 1, i == bytes.length - 1);
        }

        assertThat(scanner.name, equalTo("get_weather"));
    }

    private static McpDescribeToolCallScanner scan(
        String json)
    {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        McpDescribeToolCallScanner scanner = new McpDescribeToolCallScanner();
        scanner.feed(new UnsafeBufferEx(bytes), 0, bytes.length, true);
        return scanner;
    }
}
