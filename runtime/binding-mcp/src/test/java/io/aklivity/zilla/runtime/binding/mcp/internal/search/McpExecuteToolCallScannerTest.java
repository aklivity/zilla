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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class McpExecuteToolCallScannerTest
{
    @Test
    public void shouldScanNameAndArguments()
    {
        McpExecuteToolCallScanner scanner =
            scan("{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather\"," +
                "\"arguments\":{\"location\":\"New York\"}}}");

        assertThat(scanner.name, equalTo("get_weather"));
        assertThat(scanner.malformed, equalTo(false));
        assertThat(argumentsAsString(scanner), equalTo("{\"location\":\"New York\"}"));
    }

    @Test
    public void shouldCaptureNestedArguments()
    {
        McpExecuteToolCallScanner scanner =
            scan("{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather\"," +
                "\"arguments\":{\"nested\":{\"a\":[1,2,3]},\"flag\":true,\"n\":null}}}");

        assertThat(scanner.name, equalTo("get_weather"));
        assertThat(scanner.malformed, equalTo(false));
        assertThat(argumentsAsString(scanner), equalTo("{\"nested\":{\"a\":[1,2,3]},\"flag\":true,\"n\":null}"));
    }

    @Test
    public void shouldLeaveArgumentsEmptyWhenMissing()
    {
        McpExecuteToolCallScanner scanner =
            scan("{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather\"}}");

        assertThat(scanner.name, equalTo("get_weather"));
        assertThat(scanner.malformed, equalTo(false));
        assertThat(scanner.argumentsLength, equalTo(0));
    }

    @Test
    public void shouldReturnNullNameWhenMissing()
    {
        McpExecuteToolCallScanner scanner =
            scan("{\"name\":\"zilla__execute_tool\",\"arguments\":{\"arguments\":{\"location\":\"New York\"}}}");

        assertThat(scanner.name, nullValue());
    }

    @Test
    public void shouldReturnNullForMalformedJson()
    {
        McpExecuteToolCallScanner scanner = scan("not valid json at all");

        assertThat(scanner.name, nullValue());
        assertThat(scanner.malformed, equalTo(true));
    }

    @Test
    public void shouldMarkMalformedWhenTargetArgumentsIsNotAnObject()
    {
        McpExecuteToolCallScanner scanner =
            scan("{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather\",\"arguments\":\"oops\"}}");

        assertThat(scanner.malformed, equalTo(true));
    }

    @Test
    public void shouldScanSplitAcrossWindowBoundary()
    {
        final String json =
            "{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather_forecast\"," +
            "\"arguments\":{\"location\":\"New York\",\"days\":3,\"units\":\"imperial\"}}}";
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        final String expectedArgs = "{\"location\":\"New York\",\"days\":3,\"units\":\"imperial\"}";

        for (int split = 1; split < bytes.length; split++)
        {
            final McpExecuteToolCallScanner scanner = new McpExecuteToolCallScanner();
            final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
            scanner.feed(buffer, 0, split, false);
            scanner.feed(buffer, split, bytes.length - split, true);

            assertThat("failed at split=" + split, scanner.malformed, equalTo(false));
            assertThat("failed at split=" + split, scanner.name, equalTo("get_weather_forecast"));
            assertThat("failed at split=" + split, argumentsAsString(scanner), equalTo(expectedArgs));
        }
    }

    @Test
    public void shouldScanFedOneByteAtATime()
    {
        final String json =
            "{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather\"," +
            "\"arguments\":{\"location\":\"New York\"}}}";
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        final McpExecuteToolCallScanner scanner = new McpExecuteToolCallScanner();
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        for (int i = 0; i < bytes.length; i++)
        {
            scanner.feed(buffer, i, 1, i == bytes.length - 1);
        }

        assertThat(scanner.name, equalTo("get_weather"));
        assertThat(argumentsAsString(scanner), equalTo("{\"location\":\"New York\"}"));
    }

    private static McpExecuteToolCallScanner scan(
        String json)
    {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        McpExecuteToolCallScanner scanner = new McpExecuteToolCallScanner();
        scanner.feed(new UnsafeBufferEx(bytes), 0, bytes.length, true);
        return scanner;
    }

    private static String argumentsAsString(
        McpExecuteToolCallScanner scanner)
    {
        byte[] copy = new byte[scanner.argumentsLength];
        scanner.arguments.getBytes(0, copy);
        return new String(copy, StandardCharsets.UTF_8);
    }
}
