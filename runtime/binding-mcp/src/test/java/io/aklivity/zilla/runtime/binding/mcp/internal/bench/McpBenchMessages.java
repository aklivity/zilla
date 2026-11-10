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
package io.aklivity.zilla.runtime.binding.mcp.internal.bench;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Base64;
import java.util.Random;

import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.mcp.internal.McpFunctions;

/**
 * Message shapes of the {@code mcp} specification scripts: the JSON-RPC bodies, the HTTP headers that carry
 * them and the application stream extensions.
 */
final class McpBenchMessages
{
    static final String INITIALIZE =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":" +
        "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    static final String INITIALIZE_RESULT =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":" +
        "{\"prompts\":{\"listChanged\":true},\"resources\":{\"listChanged\":true},\"tools\":{\"listChanged\":true}}," +
        "\"serverInfo\":{\"name\":\"zilla\",\"version\":\"1.0\"}}}";

    static final String INITIALIZED = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    static final String TOOLS_LIST = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";

    static final String APP_SESSION_ID = "5ca1ab1e-c0de-4a11-5e55-00010000003f";

    private static final int LARGE_LENGTH = 10_000;
    private static final String SMALL_ARGUMENT = "New York";
    private static final String SMALL_RESULT_TEXT =
        "Current weather in New York:\\nTemperature: 72°F\\nConditions: Partly cloudy";
    private static final String TOOL =
        "{\"name\": \"%s\",\"title\": \"Weather Information Provider\"," +
        "\"description\": \"Get current weather information for a location\"," +
        "\"inputSchema\": {\"type\": \"object\",\"properties\": {\"location\": " +
        "{\"type\": \"string\",\"description\": \"City name or zip code\"}},\"required\": [\"location\"]}," +
        "\"icons\": [{\"src\": \"https://example.com/weather-icon.png\",\"mimeType\": \"image/png\"," +
        "\"sizes\": [\"48x48\"]}],\"execution\": {\"taskSupport\": \"optional\"}}";

    private McpBenchMessages()
    {
    }

    static boolean large(
        String size)
    {
        return "10k".equals(size);
    }

    static String toolsCallRequest(
        String size)
    {
        final String argument = large(size) ? filler(LARGE_LENGTH) : SMALL_ARGUMENT;

        return String.format("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\"," +
            "\"params\":{\"name\":\"get_weather\",\"arguments\":{\"location\": \"%s\"}}}", argument);
    }

    static String toolsCallParams(
        String size)
    {
        final String argument = large(size) ? filler(LARGE_LENGTH) : SMALL_ARGUMENT;

        return String.format("{\"name\":\"get_weather\",\"arguments\":{\"location\": \"%s\"}}", argument);
    }

    static String toolsCallProgressRequest(
        String size)
    {
        final String argument = large(size) ? filler(LARGE_LENGTH) : SMALL_ARGUMENT;

        return String.format("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\"," +
            "\"params\":{\"name\":\"get_weather\",\"arguments\":{\"location\": \"%s\"}," +
            "\"_meta\":{\"progressToken\":\"abc123\"}}}", argument);
    }

    static byte[] resumableFlushEx(
        int mcpTypeId)
    {
        return McpFunctions.flushEx()
            .typeId(mcpTypeId)
            .resumable()
                .id("0")
                .build()
            .build();
    }

    static byte[] progressFlushEx(
        int mcpTypeId)
    {
        return McpFunctions.flushEx()
            .typeId(mcpTypeId)
            .progress()
                .id("1")
                .token("abc123")
                .progress(50)
                .total(100)
                .message("Checking if Lady Liberty needs an umbrella...")
                .build()
            .build();
    }

    static String toolsCallResult(
        String size)
    {
        final String text = large(size) ? filler(LARGE_LENGTH) : SMALL_RESULT_TEXT;

        return String.format("{\"content\":[{\"type\": \"text\",\"text\": \"%s\"}],\"isError\": false}", text);
    }

    static String toolsCallResponse(
        String size)
    {
        return String.format("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":%s}", toolsCallResult(size));
    }

    static String toolsListResult(
        String size)
    {
        final StringBuilder tools = new StringBuilder("{\"tools\":[");
        final int count = large(size) ? LARGE_LENGTH / TOOL.length() + 1 : 1;

        for (int i = 0; i < count; i++)
        {
            tools.append(i != 0 ? "," : "").append(String.format(TOOL, i == 0 ? "get_weather" : "get_weather_" + i));
        }

        return tools.append("]}").toString();
    }

    static String toolsListResponse(
        String size)
    {
        return String.format("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":%s}", toolsListResult(size));
    }

    static byte[] bytes(
        String text)
    {
        return text.getBytes(UTF_8);
    }

    static UnsafeBufferEx buffer(
        String text)
    {
        return new UnsafeBufferEx(bytes(text));
    }

    static UnsafeBufferEx httpRequestEx(
        int httpTypeId,
        String method,
        String sessionId,
        int contentLength)
    {
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        final HttpBeginExFW.Builder builder = new HttpBeginExFW.Builder().wrap(buffer, 0, buffer.capacity())
            .typeId(httpTypeId)
            .headersItem(h -> h.name(":method").value(method))
            .headersItem(h -> h.name(":scheme").value("http"))
            .headersItem(h -> h.name(":authority").value("localhost:8080"))
            .headersItem(h -> h.name(":path").value("/mcp"));

        if (!"DELETE".equals(method))
        {
            builder.headersItem(h -> h.name("content-type").value("application/json"))
                   .headersItem(h -> h.name("accept").value("application/json, text/event-stream"));
        }

        builder.headersItem(h -> h.name("mcp-protocol-version").value("2025-11-25"));

        if (sessionId != null)
        {
            builder.headersItem(h -> h.name("mcp-session-id").value(sessionId));
        }

        if (contentLength != 0)
        {
            builder.headersItem(h -> h.name("content-length").value(Integer.toString(contentLength)));
        }

        return copy(buffer, builder.build().sizeof());
    }

    static byte[] httpResponseEx(
        int httpTypeId,
        int status,
        String contentType,
        String sessionId)
    {
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[512]);

        final HttpBeginExFW.Builder builder = new HttpBeginExFW.Builder().wrap(buffer, 0, buffer.capacity())
            .typeId(httpTypeId)
            .headersItem(h -> h.name(":status").value(Integer.toString(status)));

        if (contentType != null)
        {
            builder.headersItem(h -> h.name("content-type").value(contentType));
        }

        if (sessionId != null)
        {
            builder.headersItem(h -> h.name("mcp-session-id").value(sessionId));
        }

        return copy(buffer, builder.build().sizeof()).byteArray();
    }

    static byte[] lifecycleReplyEx(
        int mcpTypeId)
    {
        return McpFunctions.beginEx()
            .typeId(mcpTypeId)
            .lifecycle()
                .sessionId(APP_SESSION_ID)
                .build()
            .build();
    }

    static UnsafeBufferEx lifecycleEx(
        int mcpTypeId)
    {
        return new UnsafeBufferEx(McpFunctions.beginEx()
            .typeId(mcpTypeId)
            .lifecycle()
                .build()
            .build());
    }

    static UnsafeBufferEx toolsCallEx(
        int mcpTypeId,
        String sessionId,
        int contentLength)
    {
        return new UnsafeBufferEx(McpFunctions.beginEx()
            .typeId(mcpTypeId)
            .toolsCall()
                .sessionId(sessionId)
                .name("get_weather")
                .contentLength(contentLength)
                .build()
            .build());
    }

    static UnsafeBufferEx toolsListEx(
        int mcpTypeId,
        String sessionId)
    {
        return new UnsafeBufferEx(McpFunctions.beginEx()
            .typeId(mcpTypeId)
            .toolsList()
                .sessionId(sessionId)
                .build()
            .build());
    }

    private static UnsafeBufferEx copy(
        MutableDirectBufferEx buffer,
        int length)
    {
        final byte[] encoded = new byte[length];
        buffer.getBytes(0, encoded);

        return new UnsafeBufferEx(encoded);
    }

    private static String filler(
        int length)
    {
        final byte[] random = new byte[length];
        new Random(0L).nextBytes(random);

        return Base64.getEncoder().withoutPadding().encodeToString(random).substring(0, length);
    }
}
