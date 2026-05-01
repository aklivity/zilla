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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_PROMPTS;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_RESOURCES;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_TOOLS;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_LIFECYCLE;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_GET;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_READ;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_CALL;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;

import java.io.StringReader;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.config.McpWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpChallengeExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpFlushExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.json.DirectBufferInputStreamEx;
import io.aklivity.zilla.runtime.common.json.StreamingJson;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpClientFactory implements McpStreamFactory
{
    private static final int SERVER_CAPABILITIES =
        SERVER_TOOLS.value() |
        SERVER_PROMPTS.value() |
        SERVER_RESOURCES.value();

    private static final String HTTP_TYPE_NAME = "http";
    private static final String MCP_TYPE_NAME = "mcp";

    private static final int KEEPALIVE_SIGNAL_ID = 1;

    private static final String JSON_RPC_VERSION = "2.0";
    private static final String HTTP_HEADER_METHOD = ":method";
    private static final String HTTP_HEADER_CONTENT_TYPE = "content-type";
    private static final String HTTP_HEADER_ACCEPT = "accept";
    private static final String HTTP_HEADER_STATUS = ":status";
    private static final String HTTP_HEADER_SESSION = "mcp-session-id";
    private static final String HTTP_HEADER_MCP_VERSION = "mcp-protocol-version";
    private static final String HTTP_HEADER_LAST_EVENT_ID = "last-event-id";
    private static final String STATUS_405 = "405";
    private static final long SUSPEND_RETRY_NEVER = -1L;
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_JSON_AND_EVENT_STREAM = "application/json, text/event-stream";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String MCP_PROTOCOL_VERSION = "2025-11-25";
    private static final String HTTP_METHOD_GET = "GET";
    private static final String HTTP_METHOD_POST = "POST";

    private static final int DATA_FLAGS_COMPLETE = 0x03;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final SignalFW signalRO = new SignalFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final McpChallengeExFW mcpChallengeExRO = new McpChallengeExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();
    private final McpChallengeExFW.Builder mcpChallengeExRW = new McpChallengeExFW.Builder();
    private final McpFlushExFW.Builder mcpFlushExRW = new McpFlushExFW.Builder();

    private final DirectBufferInputStreamEx inputRO = new DirectBufferInputStreamEx();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer codecBuffer;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int mcpTypeId;
    private final int decodeMax;
    private final int encodeMax;
    private final long inactivityTimeoutMillis;
    private final int keepaliveTolerance;
    private final Signaler signaler;
    private final String clientName;
    private final String clientVersion;

    private static final List<String> CLIENT_JSON_PATH_INCLUDES = List.of(
        "/jsonrpc",
        "/id",
        "/method",
        "/params/progressToken",
        "/params/progress",
        "/params/total",
        "/params/message");

    private static final int SSE_LINE_START = 0;
    private static final int SSE_FIELD_NAME = 1;
    private static final int SSE_AFTER_COLON = 2;
    private static final int SSE_SMALL_VALUE = 3;
    private static final int SSE_IGNORE_VALUE = 4;
    private final JsonParserFactory parserFactory;

    private final Long2ObjectHashMap<McpBindingConfig> bindings;
    private final Map<String, McpStream> sessions = new Object2ObjectHashMap<>();
    private final Int2ObjectHashMap<McpSessionIdResolver> resolvers;
    private final Int2ObjectHashMap<McpRequestStreamFactory> requestFactories;

    public McpClientFactory(
        McpConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.codecBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.decodeMax = decodePool.slotCapacity();
        this.encodeMax = decodePool.slotCapacity();
        this.inactivityTimeoutMillis = config.inactivityTimeout().toMillis();
        this.keepaliveTolerance = config.keepaliveTolerance();
        this.signaler = context.signaler();
        this.clientName = config.clientName();
        this.clientVersion = config.clientVersion();
        this.parserFactory = StreamingJson.createParserFactory(Map.of(
            StreamingJson.PATH_INCLUDES, CLIENT_JSON_PATH_INCLUDES,
            StreamingJson.TOKEN_MAX_BYTES, decodeMax));

        final Int2ObjectHashMap<McpSessionIdResolver> resolvers = new Int2ObjectHashMap<>();
        resolvers.put(KIND_TOOLS_LIST, ex -> ex.toolsList().sessionId().asString());
        resolvers.put(KIND_TOOLS_CALL, ex -> ex.toolsCall().sessionId().asString());
        resolvers.put(KIND_PROMPTS_LIST, ex -> ex.promptsList().sessionId().asString());
        resolvers.put(KIND_PROMPTS_GET, ex -> ex.promptsGet().sessionId().asString());
        resolvers.put(KIND_RESOURCES_LIST, ex -> ex.resourcesList().sessionId().asString());
        resolvers.put(KIND_RESOURCES_READ, ex -> ex.resourcesRead().sessionId().asString());
        this.resolvers = resolvers;

        final Int2ObjectHashMap<McpRequestStreamFactory> requestFactories = new Int2ObjectHashMap<>();
        requestFactories.put(KIND_TOOLS_LIST, McpToolsListStream::new);
        requestFactories.put(KIND_TOOLS_CALL, McpToolsCallStream::new);
        requestFactories.put(KIND_PROMPTS_LIST, McpPromptsListStream::new);
        requestFactories.put(KIND_PROMPTS_GET, McpPromptsGetStream::new);
        requestFactories.put(KIND_RESOURCES_LIST, McpResourcesListStream::new);
        requestFactories.put(KIND_RESOURCES_READ, McpResourcesReadStream::new);
        this.requestFactories = requestFactories;
    }

    private McpLifecycleStream lookupSession(
        String sessionId)
    {
        final McpStream session = sessions.get(sessionId);
        return session instanceof McpLifecycleStream ? (McpLifecycleStream) session : null;
    }

    @FunctionalInterface
    private interface McpSessionIdResolver
    {
        McpSessionIdResolver DEFAULT = beginEx -> null;

        String resolveSessionId(McpBeginExFW beginEx);
    }

    @FunctionalInterface
    private interface McpRequestStreamFactory
    {
        McpRequestStreamFactory DEFAULT =
            (session, sender, originId, routedId, initialId, resolvedId, affinity, authorization) -> null;

        McpRequestStream newRequest(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization);
    }

    @FunctionalInterface
    private interface HttpResponseDecoder
    {
        int decode(
            HttpStream http,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int progress,
            int limit);
    }

    private final HttpResponseDecoder decodeJsonRpc = this::decodeJsonRpc;
    private final HttpResponseDecoder decodeSse = this::decodeSse;
    private final HttpResponseDecoder decodeSseEventEnd = this::decodeSseEventEnd;
    private final HttpResponseDecoder decodeJsonRpcStart = this::decodeJsonRpcStart;
    private final HttpResponseDecoder decodeJsonRpcNext = this::decodeJsonRpcNext;
    private final HttpResponseDecoder decodeJsonRpcEnd = this::decodeJsonRpcEnd;
    private final HttpResponseDecoder decodeJsonRpcVersion = this::decodeJsonRpcVersion;
    private final HttpResponseDecoder decodeJsonRpcId = this::decodeJsonRpcId;
    private final HttpResponseDecoder decodeJsonRpcResultStart = this::decodeJsonRpcResultStart;
    private final HttpResponseDecoder decodeJsonRpcResultEnd = this::decodeJsonRpcResultEnd;
    private final HttpResponseDecoder decodeJsonRpcSkipObject = this::decodeJsonRpcSkipObject;
    private final HttpResponseDecoder decodeJsonRpcMethod = this::decodeJsonRpcMethod;
    private final HttpResponseDecoder decodeJsonRpcParamsStart = this::decodeJsonRpcParamsStart;
    private final HttpResponseDecoder decodeJsonRpcParamsNext = this::decodeJsonRpcParamsNext;
    private final HttpResponseDecoder decodeJsonRpcParamsToken = this::decodeJsonRpcParamsToken;
    private final HttpResponseDecoder decodeJsonRpcParamsProgress = this::decodeJsonRpcParamsProgress;
    private final HttpResponseDecoder decodeJsonRpcParamsTotal = this::decodeJsonRpcParamsTotal;
    private final HttpResponseDecoder decodeJsonRpcParamsMessage = this::decodeJsonRpcParamsMessage;
    private final HttpResponseDecoder decodeIgnore = this::decodeIgnore;

    private int decodeJsonRpc(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        input.wrap(buffer, progress, limit - progress);

        http.decodableJson = parserFactory.createParser(input);
        http.decoder = decodeJsonRpcStart;
        // Map parser stream-offset 0 to buffer position `progress`.
        // The decodeJsonRpc* methods use offset + decodedX - decodedParserProgress as buffer position,
        // so set decodedParserProgress = offset - progress to align.
        http.decodedParserProgress = offset - progress;

        progress = limit - input.available();

        return progress;
    }

    private int decodeJsonRpcStart(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        JsonParser parser = http.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.START_OBJECT)
            {
                http.onDecodeParseError(traceId, authorization);
                http.decoder = decodeIgnore;
                break decode;
            }

            http.decoder = decodeJsonRpcNext;

            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcNext(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        JsonParser parser = http.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case JsonParser.Event.KEY_NAME:
                final String key = parser.getString();
                switch (key)
                {
                case "jsonrpc":
                    http.decoder = decodeJsonRpcVersion;
                    break;
                case "id":
                    http.decoder = decodeJsonRpcId;
                    break;
                case "result":
                    http.decoder = decodeJsonRpcResultStart;
                    break;
                case "method":
                    http.decoder = decodeJsonRpcMethod;
                    break;
                case "params":
                    http.decoder = decodeJsonRpcParamsStart;
                    break;
                default:
                    http.onDecodeInvalidResponse(traceId, authorization);
                    http.decoder = decodeIgnore;
                    break decode;
                }
                break;
            case JsonParser.Event.END_OBJECT:
                http.decoder = decodeJsonRpcEnd;
                break;
            default:
                http.onDecodeParseError(traceId, authorization);
                break decode;
            }

            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcEnd(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        JsonParser parser = http.decodableJson;

        decode:
        {
            if (parser.hasNext())
            {
                http.onDecodeParseError(traceId, authorization);
                http.decoder = decodeIgnore;
                break decode;
            }

            parser.close();
            http.decodableJson = null;
            http.decoder = http.sseMode ? decodeSseEventEnd : decodeIgnore;

            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcVersion(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        JsonParser parser = http.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.VALUE_STRING ||
                !JSON_RPC_VERSION.equals(parser.getString()))
            {
                http.onDecodeParseError(traceId, authorization);
                http.decoder = decodeIgnore;
                break decode;
            }

            http.decoder = decodeJsonRpcNext;

            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcId(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        JsonParser parser = http.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.VALUE_STRING &&
                event != JsonParser.Event.VALUE_NUMBER)
            {
                http.onDecodeParseError(traceId, authorization);
                http.decoder = decodeIgnore;
                break decode;
            }

            http.decoder = decodeJsonRpcNext;

            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcResultStart(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        JsonParser parser = http.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.START_OBJECT)
            {
                http.onDecodeParseError(traceId, authorization);
                http.decoder = decodeIgnore;
                break decode;
            }

            http.decodedResultProgress = (int) parser.getLocation().getStreamOffset() - 1;

            http.decodedSkipObjectDepth = 1;
            http.decodedSkipObjectThen = decodeJsonRpcResultEnd;
            http.decoder = decodeJsonRpcSkipObject;

            progress = offset + http.decodedResultProgress - http.decodedParserProgress;
        }

        return progress;
    }

    private int decodeJsonRpcResultEnd(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        JsonParser parser = http.decodableJson;

        final int decodedResultProgress = (int) parser.getLocation().getStreamOffset();
        if (decodedResultProgress > http.decodedResultProgress)
        {
            final int decodedOffset = offset + http.decodedResultProgress - http.decodedParserProgress;
            final int decodedLimit = offset + decodedResultProgress - http.decodedParserProgress;
            final int decodedProgress = http.onDecodeResponseResult(traceId, authorization, buffer, decodedOffset, decodedLimit);

            http.decodedResultProgress = decodedProgress - offset + http.decodedParserProgress;
        }

        progress = offset + http.decodedResultProgress - http.decodedParserProgress;

        if (http.decodedResultProgress == decodedResultProgress)
        {
            http.decoder = decodeJsonRpcNext;
        }

        return progress;
    }

    private int decodeJsonRpcSkipObject(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        JsonParser parser = http.decodableJson;

        while (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.START_OBJECT)
            {
                http.decodedSkipObjectDepth++;
            }
            else if (event == JsonParser.Event.END_OBJECT)
            {
                http.decodedSkipObjectDepth--;
                if (http.decodedSkipObjectDepth == 0)
                {
                    http.decoder = http.decodedSkipObjectThen;
                    break;
                }
            }
        }

        final int decodedResultProgress = (int) parser.getLocation().getStreamOffset();
        if (decodedResultProgress > http.decodedResultProgress)
        {
            final int decodedOffset = offset + http.decodedResultProgress - http.decodedParserProgress;
            final int decodedLimit = offset + decodedResultProgress - http.decodedParserProgress;
            final int decodedProgress = http.onDecodeResponseResult(traceId, authorization, buffer, decodedOffset, decodedLimit);

            http.decodedResultProgress = decodedProgress - offset + http.decodedParserProgress;
        }

        progress = offset + http.decodedResultProgress - http.decodedParserProgress;

        return progress;
    }

    private int decodeJsonRpcMethod(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        JsonParser parser = http.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.VALUE_STRING)
            {
                http.onDecodeParseError(traceId, authorization);
                http.decoder = decodeIgnore;
                break decode;
            }
            final String method = parser.getString();
            if ("notifications/progress".equals(method))
            {
                http.sseEventProgress = true;
            }
            http.decoder = decodeJsonRpcNext;
            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcParamsStart(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        JsonParser parser = http.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.START_OBJECT)
            {
                http.onDecodeParseError(traceId, authorization);
                http.decoder = decodeIgnore;
                break decode;
            }
            http.decoder = decodeJsonRpcParamsNext;
            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcParamsNext(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        JsonParser parser = http.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case JsonParser.Event.KEY_NAME:
                final String key = parser.getString();
                switch (key)
                {
                case "progressToken":
                    http.decoder = decodeJsonRpcParamsToken;
                    break;
                case "progress":
                    http.decoder = decodeJsonRpcParamsProgress;
                    break;
                case "total":
                    http.decoder = decodeJsonRpcParamsTotal;
                    break;
                case "message":
                    http.decoder = decodeJsonRpcParamsMessage;
                    break;
                default:
                    http.decoder = decodeJsonRpcParamsNext;
                    break;
                }
                break;
            case JsonParser.Event.END_OBJECT:
                http.decoder = decodeJsonRpcNext;
                break;
            default:
                http.onDecodeParseError(traceId, authorization);
                break decode;
            }

            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcParamsToken(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        JsonParser parser = http.decodableJson;

        if (parser.hasNext())
        {
            parser.next();
            http.sseProgressToken = parser.getString();
            http.decoder = decodeJsonRpcParamsNext;
            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcParamsProgress(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        JsonParser parser = http.decodableJson;

        if (parser.hasNext())
        {
            parser.next();
            http.sseProgress = parser.getLong();
            http.decoder = decodeJsonRpcParamsNext;
            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcParamsTotal(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        JsonParser parser = http.decodableJson;

        if (parser.hasNext())
        {
            parser.next();
            http.sseProgressTotal = parser.getLong();
            http.decoder = decodeJsonRpcParamsNext;
            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcParamsMessage(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        JsonParser parser = http.decodableJson;

        if (parser.hasNext())
        {
            parser.next();
            http.sseProgressMessage = parser.getString();
            http.decoder = decodeJsonRpcParamsNext;
            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeIgnore(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        return limit;
    }

    private int decodeSse(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        outer:
        while (progress < limit)
        {
            switch (http.sseLineState)
            {
            case SSE_LINE_START:
            {
                final byte b = buffer.getByte(progress);
                if (b == (byte) '\n')
                {
                    progress++;
                    finalizeSseEvent(http, traceId, authorization);
                    resetSseEvent(http);
                }
                else if (b == (byte) ':')
                {
                    http.sseLineState = SSE_IGNORE_VALUE;
                    progress++;
                }
                else
                {
                    http.sseFieldKind = (byte) (b | 0x20);
                    http.sseLineState = SSE_FIELD_NAME;
                    progress++;
                }
                break;
            }
            case SSE_FIELD_NAME:
            {
                final byte b = buffer.getByte(progress);
                if (b == (byte) ':')
                {
                    http.sseLineState = SSE_AFTER_COLON;
                    progress++;
                }
                else if (b == (byte) '\n')
                {
                    http.sseLineState = SSE_LINE_START;
                    progress++;
                }
                else
                {
                    progress++;
                }
                break;
            }
            case SSE_AFTER_COLON:
            {
                final byte b = buffer.getByte(progress);
                if (b == (byte) ' ')
                {
                    progress++;
                    break;
                }
                if (b == (byte) '\n')
                {
                    if (http.sseFieldKind == (byte) 'i')
                    {
                        http.sseEventId = "";
                    }
                    http.sseLineState = SSE_LINE_START;
                    progress++;
                    break;
                }
                if (http.sseFieldKind == (byte) 'd')
                {
                    http.sseEventHasData = true;
                    http.sseLineState = SSE_FIELD_NAME;
                    http.decoder = decodeJsonRpc;
                    break outer;
                }
                if (http.sseFieldKind == (byte) 'i' || http.sseFieldKind == (byte) 'r')
                {
                    http.sseLineState = SSE_SMALL_VALUE;
                    http.sseSmallValue.setLength(0);
                    http.sseSmallValue.append((char) (b & 0xff));
                    progress++;
                }
                else
                {
                    http.sseLineState = SSE_IGNORE_VALUE;
                    progress++;
                }
                break;
            }
            case SSE_SMALL_VALUE:
            {
                final byte b = buffer.getByte(progress);
                if (b == (byte) '\n')
                {
                    final String value = http.sseSmallValue.toString();
                    if (http.sseFieldKind == (byte) 'i')
                    {
                        http.sseEventId = value;
                    }
                    else if (http.sseFieldKind == (byte) 'r')
                    {
                        try
                        {
                            http.sseEventRetry = Long.parseLong(value);
                        }
                        catch (NumberFormatException ignored)
                        {
                        }
                    }
                    http.sseLineState = SSE_LINE_START;
                    progress++;
                }
                else
                {
                    http.sseSmallValue.append((char) (b & 0xff));
                    progress++;
                }
                break;
            }
            case SSE_IGNORE_VALUE:
            {
                final byte b = buffer.getByte(progress);
                if (b == (byte) '\n')
                {
                    http.sseLineState = SSE_LINE_START;
                }
                progress++;
                break;
            }
            default:
                break outer;
            }
        }
        return progress;
    }

    private int decodeSseEventEnd(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        // After JSON-RPC payload ends, expect '\n' (end of data: line) followed by '\n' (event terminator).
        while (progress < limit)
        {
            final byte b = buffer.getByte(progress);
            if (b == (byte) '\n')
            {
                progress++;
                if (http.sseLineState == SSE_LINE_START)
                {
                    finalizeSseEvent(http, traceId, authorization);
                    resetSseEvent(http);
                    http.decoder = decodeSse;
                    break;
                }
                http.sseLineState = SSE_LINE_START;
            }
            else
            {
                progress++;
            }
        }
        return progress;
    }

    private void finalizeSseEvent(
        HttpStream http,
        long traceId,
        long authorization)
    {
        if (http.sseEventProgress)
        {
            http.mcp.relayProgress(traceId, authorization,
                stripSseEventIdPrefix(http.sseEventId),
                http.sseProgressToken,
                http.sseProgress,
                http.sseProgressTotal,
                http.sseProgressMessage);
        }
        else if (http.sseEventRetry >= 0)
        {
            http.mcp.relaySuspend(traceId, authorization, http.sseEventRetry);
        }
        else if (http.sseEventId != null && !http.sseEventHasData)
        {
            http.mcp.relayResumable(traceId, authorization, stripSseEventIdPrefix(http.sseEventId));
        }
    }

    private void resetSseEvent(
        HttpStream http)
    {
        http.sseEventId = null;
        http.sseEventRetry = -1L;
        http.sseEventHasData = false;
        http.sseEventProgress = false;
        http.sseProgressToken = null;
        http.sseProgress = 0L;
        http.sseProgressTotal = -1L;
        http.sseProgressMessage = null;
        http.sseFieldKind = 0;
        http.sseSmallValue.setLength(0);
        http.sseLineState = SSE_LINE_START;
    }

    private static String stripSseEventIdPrefix(
        String id)
    {
        if (id == null)
        {
            return "";
        }
        final int colon = id.lastIndexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    @Override
    public int originTypeId()
    {
        return mcpTypeId;
    }

    @Override
    public int routedTypeId()
    {
        return httpTypeId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        McpBindingConfig newBinding = new McpBindingConfig(binding);
        bindings.put(binding.id, newBinding);
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long affinity = begin.affinity();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();

        final McpBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final McpRouteConfig route = binding.resolve(authorization);

            if (route != null && extension.sizeof() > 0)
            {
                final McpBeginExFW mcpBeginEx = mcpBeginExRO.wrap(
                    extension.buffer(), extension.offset(), extension.limit());

                if (mcpBeginEx.kind() == KIND_LIFECYCLE)
                {
                    newStream = new McpLifecycleStream(
                        sender, originId, routedId, initialId, route.id, affinity,
                        mcpBeginEx.lifecycle().sessionId().asString(), route.with)::onAppMessage;
                }
                else
                {
                    final McpSessionIdResolver resolver = resolvers.getOrDefault(
                        mcpBeginEx.kind(), McpSessionIdResolver.DEFAULT);
                    final String sessionId = resolver.resolveSessionId(mcpBeginEx);
                    final McpLifecycleStream session = lookupSession(sessionId);
                    if (session != null)
                    {
                        final McpRequestStreamFactory requestFactory = requestFactories.getOrDefault(
                            mcpBeginEx.kind(), McpRequestStreamFactory.DEFAULT);
                        final McpRequestStream request = requestFactory.newRequest(
                            session, sender, originId, routedId, initialId, route.id,
                            affinity, authorization);
                        if (request != null)
                        {
                            newStream = request::onAppMessage;
                        }
                    }
                }
            }
        }

        return newStream;
    }

    private abstract class McpStream
    {
        protected final MessageConsumer sender;
        protected final long originId;
        protected final long routedId;
        protected final long initialId;
        protected final long replyId;
        protected final long resolvedId;
        protected final long affinity;
        protected final String sessionId;
        protected final McpWithConfig with;

        protected HttpStream http;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long replyBud;
        private int replyPad;

        int state;

        McpStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            String sessionId,
            McpWithConfig with,
            Function<McpStream, HttpStream> httpFactory)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.sessionId = sessionId;
            this.with = with;
            this.http = httpFactory.apply(this);
        }

        void onAppMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onAppBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onAppData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onAppEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAppAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onAppFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAppWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAppReset(reset);
                break;
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onAppChallenge(challenge);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onAppSignal(signal);
                break;
            default:
                break;
            }
        }

        void onAppChallenge(
            ChallengeFW challenge)
        {
        }

        void onAppSignal(
            SignalFW signal)
        {
        }

        void relayResumable(
            long traceId,
            long authorization,
            String id)
        {
        }

        void relayProgress(
            long traceId,
            long authorization,
            String id,
            String token,
            long progress,
            long total,
            String message)
        {
        }

        void relayResult(
            long traceId,
            long authorization,
            String result)
        {
        }

        void relaySuspend(
            long traceId,
            long authorization,
            long retry)
        {
        }

        void relaySuspended(
            long traceId,
            long authorization)
        {
        }

        void relayNotification(
            long traceId,
            long authorization,
            String id,
            String data)
        {
        }

        void relayCompletion(
            long traceId,
            long authorization)
        {
        }

        String resumeSessionId()
        {
            return sessionId;
        }

        boolean isEventsUnsupported()
        {
            return false;
        }

        void markEventsUnsupported()
        {
        }

        HttpEventStream eventStreamRef()
        {
            return null;
        }

        void clearEventStream()
        {
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            state = McpState.openingInitial(state);
            state = McpState.openedInitial(state);

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            initialMax = encodeMax;

            final OctetsFW extension = begin.extension();
            final McpBeginExFW mcpBeginEx = extension.sizeof() > 0
                ? mcpBeginExRO.wrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            http.doEncodeRequestBegin(traceId, authorization);

            onAppBeginImpl(traceId, authorization, mcpBeginEx);

            doAppWindow(traceId, authorization, 0L, 0);
        }

        void onAppBeginImpl(
            long traceId,
            long authorization,
            McpBeginExFW mcpBeginEx)
        {
        }

        private void onAppData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;

            final OctetsFW payload = data.payload();
            if (payload != null && payload.sizeof() > 0)
            {
                http.doEncodeRequestData(traceId, authorization,
                    payload.buffer(), payload.offset(), payload.limit());
            }

            flushAppWindow(traceId, authorization, 0L, 0);
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence;
            state = McpState.closedInitial(state);

            assert initialAck <= initialSeq;

            http.doEncodeRequestEnd(traceId, authorization);

            onAppClosed(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence;
            state = McpState.closedInitial(state);

            assert initialAck <= initialSeq;

            http.doNetAbort(traceId, authorization);

            onAppErrored(traceId, authorization);
        }

        private void onAppFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + flush.reserved();

            assert initialAck <= initialSeq;
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum + acknowledge >= replyMax + replyAck;

            state = McpState.openedReply(state);
            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = budgetId;
            replyPad = padding;

            assert replyAck <= replySeq;

            http.decodeNet(traceId, authorization, budgetId);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;

            state = McpState.closedReply(state);

            assert replyAck <= replySeq;

            http.doNetReset(traceId, authorization);

            onAppErrored(traceId, authorization);
        }

        void onAppErrored(
            long traceId,
            long authorization)
        {
        }

        void onAppClosed(
            long traceId,
            long authorization)
        {
        }

        void onNetBegin(
            BeginFW begin)
        {
        }

        void onNetEnd(
            long traceId,
            long authorization)
        {
        }

        void onNetAbort(
            HttpStream http,
            long traceId,
            long authorization)
        {
            doAppAbort(traceId, authorization);
        }

        void onNetReset(
            HttpStream http,
            long traceId,
            long authorization)
        {
            doAppReset(traceId, authorization);
        }

        void doAppBegin(
            long traceId,
            long authorization,
            McpBeginExFW beginEx)
        {
            state = McpState.openingReply(state);

            doBegin(sender, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, affinity,
                beginEx);
        }

        int doAppData(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            int replyNoAck = (int)(replySeq - replyAck);
            int length = Math.min(Math.max(replyMax - replyNoAck - replyPad, 0), limit - offset);

            if (length > 0)
            {
                final int reserved = length + replyPad;

                doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, DATA_FLAGS_COMPLETE, replyBud, reserved, buffer, offset, length);

                replySeq += reserved;
                assert replySeq <= replyAck + replyMax;
            }

            return offset + length;
        }

        void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doEnd(sender, originId, routedId, replyId,
                    replySeq, replyAck, replyMax,
                    traceId, authorization);

                onAppClosed(traceId, authorization);
            }
        }

        void doAppFlush(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            doFlush(sender, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, replyBud, 0, extension);
        }

        void doAppChallenge(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            doChallenge(sender, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, extension);
        }

        void doAppAbort(
            long traceId,
            long authorization)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doAbort(sender, originId, routedId, replyId,
                    replySeq, replyAck, replyMax,
                    traceId, authorization);

                onAppErrored(traceId, authorization);
            }
        }

        void doAppReset(
            long traceId,
            long authorization)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doReset(sender, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);

                onAppErrored(traceId, authorization);
            }
        }

        private void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            doWindow(sender, originId, routedId, initialId,
                initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding);
        }

        void flushAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            final int pending = http != null ? http.encodeSlotOffset : 0;
            final long newAck = Math.max(initialSeq - pending, initialAck);
            if (newAck > initialAck)
            {
                initialAck = newAck;
                assert initialAck <= initialSeq;
                doAppWindow(traceId, authorization, budgetId, padding);
            }
        }

        private void cleanupApp(
            long traceId,
            long authorization)
        {
            doAppReset(traceId, authorization);
            doAppAbort(traceId, authorization);
        }
    }

    private final class McpLifecycleStream extends McpStream
    {
        private final Int2ObjectHashMap<McpRequestStream> requests = new Int2ObjectHashMap<>();

        String responseSessionId;
        private int nextRequestId = 2;
        private long keepaliveId = Signaler.NO_CANCEL_ID;
        private long lastActiveAt;
        private int failedKeepalives;
        HttpEventStream eventStream;
        boolean eventsUnsupported;

        @Override
        String resumeSessionId()
        {
            return responseSessionId != null ? responseSessionId : sessionId;
        }

        @Override
        boolean isEventsUnsupported()
        {
            return eventsUnsupported;
        }

        @Override
        void markEventsUnsupported()
        {
            eventsUnsupported = true;
        }

        @Override
        HttpEventStream eventStreamRef()
        {
            return eventStream;
        }

        @Override
        void clearEventStream()
        {
            eventStream = null;
        }

        McpLifecycleStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            String sessionId,
            McpWithConfig with)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, sessionId, with,
                HttpInitializeRequest::new);
            sessions.put(sessionId, this);
        }

        void touch()
        {
            lastActiveAt = System.currentTimeMillis();
            failedKeepalives = 0;
        }

        int register(
            McpRequestStream request)
        {
            touch();
            final int id = nextRequestId++;
            requests.put(id, request);
            return id;
        }

        boolean unregister(
            int id)
        {
            touch();
            return requests.remove(id) != null;
        }

        @Override
        void onAppBeginImpl(
            long traceId,
            long authorization,
            McpBeginExFW mcpBeginEx)
        {
            http.doEncodeRequestEnd(traceId, authorization);
        }

        @Override
        void onAppChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            if (extension.sizeof() > 0)
            {
                final McpChallengeExFW challengeEx = mcpChallengeExRO.tryWrap(
                    extension.buffer(), extension.offset(), extension.limit());
                if (challengeEx != null)
                {
                    switch (challengeEx.kind())
                    {
                    case McpChallengeExFW.KIND_RESUME:
                        if (eventStream != null)
                        {
                            doAppReset(traceId, authorization);
                            doAppAbort(traceId, authorization);
                        }
                        else if (eventsUnsupported)
                        {
                            relaySuspend(traceId, authorization, SUSPEND_RETRY_NEVER);
                        }
                        else
                        {
                            final String16FW resumeId = challengeEx.resume().id();
                            final String lastEventId = resumeId != null ? resumeId.asString() : null;
                            eventStream = new HttpEventStream(this, lastEventId);
                            eventStream.doNetStart(traceId, authorization);
                        }
                        break;
                    case McpChallengeExFW.KIND_SUSPENDED:
                        if (eventStream != null)
                        {
                            eventStream.doNetAbort(traceId, authorization);
                            eventStream.detach();
                        }
                        break;
                    default:
                        break;
                    }
                }
            }
        }

        @Override
        void relayResumable(
            long traceId,
            long authorization,
            String id)
        {
            final McpFlushExFW flushEx = mcpFlushExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId)
                .resumable(b -> b.id(id))
                .build();
            doAppFlush(traceId, authorization, flushEx);
        }

        @Override
        void relaySuspend(
            long traceId,
            long authorization,
            long retry)
        {
            final McpFlushExFW flushEx = mcpFlushExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId)
                .suspend(b -> b.retry(retry))
                .build();
            doAppFlush(traceId, authorization, flushEx);
        }

        @Override
        void relaySuspended(
            long traceId,
            long authorization)
        {
            final McpChallengeExFW challengeEx = mcpChallengeExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId)
                .suspended(b ->
                {
                })
                .build();
            doAppChallenge(traceId, authorization, challengeEx);
        }

        void relayNotification(
            long traceId,
            long authorization,
            String id,
            String data)
        {
            final McpFlushExFW flushEx;
            if (data.contains("notifications/tools/list_changed"))
            {
                flushEx = mcpFlushExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .toolsListChanged(b -> b.id(id))
                    .build();
            }
            else if (data.contains("notifications/prompts/list_changed"))
            {
                flushEx = mcpFlushExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .promptsListChanged(b -> b.id(id))
                    .build();
            }
            else if (data.contains("notifications/resources/list_changed"))
            {
                flushEx = mcpFlushExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .resourcesListChanged(b -> b.id(id))
                    .build();
            }
            else
            {
                flushEx = null;
            }

            if (flushEx != null)
            {
                doAppFlush(traceId, authorization, flushEx);
            }
        }

        @Override
        void onNetBegin(
            BeginFW begin)
        {
            if (responseSessionId == null)
            {
                responseSessionId = sessionId;

                final OctetsFW ext = begin.extension();
                if (ext.sizeof() > 0)
                {
                    final HttpBeginExFW httpBeginEx = httpBeginExRO.tryWrap(
                        ext.buffer(), ext.offset(), ext.limit());
                    if (httpBeginEx != null)
                    {
                        final HttpHeaderFW sessionHeader = httpBeginEx.headers()
                            .matchFirst(h -> HTTP_HEADER_SESSION.equals(h.name().asString()));
                        if (sessionHeader != null)
                        {
                            responseSessionId = sessionHeader.value().asString();
                        }
                    }
                }
            }
        }

        @Override
        void onNetEnd(
            long traceId,
            long authorization)
        {
            if (http instanceof HttpInitializeRequest)
            {
                final HttpNotifyInitialized notify = new HttpNotifyInitialized(this);
                this.http = notify;
                notify.doEncodeRequestBegin(traceId, authorization);
                notify.doEncodeRequestEnd(traceId, authorization);
            }
            else if (http instanceof HttpKeepalive)
            {
                touch();
                scheduleKeepalive(traceId);
            }
            else
            {
                final String sid = responseSessionId;
                doAppBegin(traceId, authorization, mcpBeginExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(mcpTypeId)
                    .lifecycle(b -> b
                        .sessionId(sid)
                        .capabilities(SERVER_CAPABILITIES))
                    .build());
                touch();
                scheduleKeepalive(traceId);
            }
        }

        @Override
        void onNetAbort(
            HttpStream http,
            long traceId,
            long authorization)
        {
            if (http instanceof HttpKeepalive)
            {
                scheduleKeepalive(traceId);
            }
            else
            {
                doAppAbort(traceId, authorization);
            }
        }

        @Override
        void onNetReset(
            HttpStream http,
            long traceId,
            long authorization)
        {
            if (http instanceof HttpKeepalive)
            {
                scheduleKeepalive(traceId);
            }
            else
            {
                doAppReset(traceId, authorization);
            }
        }

        @Override
        void onAppSignal(
            SignalFW signal)
        {
            if (signal.signalId() != KEEPALIVE_SIGNAL_ID)
            {
                return;
            }

            keepaliveId = Signaler.NO_CANCEL_ID;

            final long traceId = signal.traceId();
            final long authorization = signal.authorization();
            final long now = System.currentTimeMillis();
            final long nextPingAt = lastActiveAt + inactivityTimeoutMillis / 2;

            if (nextPingAt > now)
            {
                keepaliveId = signaler.signalAt(nextPingAt, originId, routedId, replyId,
                    traceId, KEEPALIVE_SIGNAL_ID, 0);
                return;
            }

            if (failedKeepalives >= keepaliveTolerance)
            {
                doAppTerminate(traceId, authorization);
                return;
            }

            failedKeepalives++;

            final HttpKeepalive keepalive = new HttpKeepalive(this);
            this.http = keepalive;
            keepalive.doEncodeRequestBegin(traceId, authorization);
            keepalive.doEncodeRequestEnd(traceId, authorization);
        }

        @Override
        void onAppClosed(
            long traceId,
            long authorization)
        {
            doAppTerminate(traceId, authorization);
        }

        @Override
        void onAppErrored(
            long traceId,
            long authorization)
        {
            doAppTerminate(traceId, authorization);
        }

        private void scheduleKeepalive(
            long traceId)
        {
            cancelKeepalive();
            if (sessions.containsKey(sessionId))
            {
                final long at = lastActiveAt + inactivityTimeoutMillis / 2;
                keepaliveId = signaler.signalAt(at, originId, routedId, replyId,
                    traceId, KEEPALIVE_SIGNAL_ID, 0);
            }
        }

        private void cancelKeepalive()
        {
            if (keepaliveId != Signaler.NO_CANCEL_ID)
            {
                signaler.cancel(keepaliveId);
                keepaliveId = Signaler.NO_CANCEL_ID;
            }
        }

        private void doAppTerminate(
            long traceId,
            long authorization)
        {
            if (sessions.remove(sessionId) != null)
            {
                cancelKeepalive();

                if (eventStream != null)
                {
                    eventStream.doNetAbort(traceId, authorization);
                    eventStream.doNetReset(traceId, authorization);
                    eventStream = null;
                }

                for (Iterator<McpRequestStream> i = requests.values().iterator(); i.hasNext(); )
                {
                    McpRequestStream request = i.next();
                    i.remove();

                    request.doAppAbort(traceId, authorization);
                    request.http.doNetAbort(traceId, authorization);
                }
                requests.clear();

                doAppEnd(traceId, authorization);

                new HttpTerminateSession(this).doNetBegin(traceId, authorization);
            }
        }
    }

    private abstract class McpRequestStream extends McpStream
    {
        final McpLifecycleStream session;
        final int requestId;
        HttpEventStream eventStream;

        McpRequestStream(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            Function<McpStream, HttpStream> httpFactory)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity,
                session.sessionId, session.with, httpFactory);
            this.session = session;
            this.requestId = session.register(this);
        }

        @Override
        String resumeSessionId()
        {
            return session.resumeSessionId();
        }

        @Override
        boolean isEventsUnsupported()
        {
            return session.isEventsUnsupported();
        }

        @Override
        void markEventsUnsupported()
        {
            session.markEventsUnsupported();
        }

        @Override
        HttpEventStream eventStreamRef()
        {
            return eventStream;
        }

        @Override
        void clearEventStream()
        {
            eventStream = null;
        }

        @Override
        void onAppChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            if (extension.sizeof() > 0)
            {
                final McpChallengeExFW challengeEx = mcpChallengeExRO.tryWrap(
                    extension.buffer(), extension.offset(), extension.limit());
                if (challengeEx != null)
                {
                    switch (challengeEx.kind())
                    {
                    case McpChallengeExFW.KIND_RESUME:
                        if (eventStream == null)
                        {
                            final String16FW resumeId = challengeEx.resume().id();
                            final String suffix = resumeId != null ? resumeId.asString() : null;
                            final String prefixedId = suffix != null && !suffix.isEmpty()
                                ? requestId + ":" + suffix
                                : null;
                            eventStream = new HttpEventStream(this, prefixedId);
                            eventStream.doNetStart(traceId, authorization);
                        }
                        break;
                    case McpChallengeExFW.KIND_SUSPENDED:
                        if (http != null)
                        {
                            http.doNetReset(traceId, authorization);
                        }
                        if (eventStream != null)
                        {
                            eventStream.doNetAbort(traceId, authorization);
                            eventStream.detach();
                        }
                        break;
                    default:
                        break;
                    }
                }
            }
        }

        @Override
        final void onAppErrored(
            long traceId,
            long authorization)
        {
            if (session.unregister(requestId))
            {
                new HttpNotifyCancelled(this).doNetBegin(traceId, authorization);
            }
        }

        @Override
        final void onAppClosed(
            long traceId,
            long authorization)
        {
            if (McpState.closed(state))
            {
                session.unregister(requestId);
            }
        }

        @Override
        final void onNetBegin(
            BeginFW begin)
        {
            doAppBegin(begin.traceId(), begin.authorization(), null);
        }

        @Override
        void onNetEnd(
            long traceId,
            long authorization)
        {
            doAppEnd(traceId, authorization);
        }

        @Override
        void relayResumable(
            long traceId,
            long authorization,
            String id)
        {
            final McpFlushExFW flushEx = mcpFlushExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId)
                .resumable(b -> b.id(id))
                .build();
            doAppFlush(traceId, authorization, flushEx);
        }

        @Override
        void relayProgress(
            long traceId,
            long authorization,
            String id,
            String token,
            long progress,
            long total,
            String message)
        {
            final McpFlushExFW flushEx = mcpFlushExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId)
                .progress(b ->
                {
                    b.id(id);
                    if (token != null)
                    {
                        b.token(token);
                    }
                    b.progress(progress);
                    b.total(total);
                    if (message != null)
                    {
                        b.message(message);
                    }
                })
                .build();
            doAppFlush(traceId, authorization, flushEx);
        }

        @Override
        void relayResult(
            long traceId,
            long authorization,
            String result)
        {
            final byte[] bytes = result.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            codecBuffer.putBytes(0, bytes);
            doAppData(traceId, authorization, codecBuffer, 0, bytes.length);
        }

        @Override
        void relaySuspend(
            long traceId,
            long authorization,
            long retry)
        {
            final McpFlushExFW flushEx = mcpFlushExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId)
                .suspend(b -> b.retry(retry))
                .build();
            doAppFlush(traceId, authorization, flushEx);
        }

        @Override
        void relaySuspended(
            long traceId,
            long authorization)
        {
            final McpChallengeExFW challengeEx = mcpChallengeExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId)
                .suspended(b ->
                {
                })
                .build();
            doAppChallenge(traceId, authorization, challengeEx);
        }

        @Override
        void onNetAbort(
            HttpStream http,
            long traceId,
            long authorization)
        {
            if (http.sseMode)
            {
                relaySuspended(traceId, authorization);
            }
            else
            {
                doAppAbort(traceId, authorization);
            }
        }

        @Override
        void relayCompletion(
            long traceId,
            long authorization)
        {
            doAppEnd(traceId, authorization);
        }

        @Override
        void relayNotification(
            long traceId,
            long authorization,
            String id,
            String data)
        {
            try (JsonReader reader = Json.createReader(new StringReader(data)))
            {
                final JsonObject json = reader.readObject();
                final String method = json.containsKey("method") ? json.getString("method") : null;
                if ("notifications/progress".equals(method) && json.containsKey("params"))
                {
                    final JsonObject params = json.getJsonObject("params");
                    final String token = params.containsKey("progressToken") &&
                        params.get("progressToken") instanceof JsonString js
                        ? js.getString()
                        : null;
                    final long progress = params.containsKey("progress")
                        ? params.getJsonNumber("progress").longValueExact()
                        : 0L;
                    final long total = params.containsKey("total")
                        ? params.getJsonNumber("total").longValueExact()
                        : -1L;
                    final String message = params.containsKey("message") &&
                        params.get("message") instanceof JsonString jsm
                        ? jsm.getString()
                        : null;
                    relayProgress(traceId, authorization, id, token, progress, total, message);
                }
                else if (json.containsKey("result"))
                {
                    final JsonValue result = json.get("result");
                    relayResult(traceId, authorization, result.toString());
                }
            }
            catch (Exception ignored)
            {
            }
        }
    }

    private final class McpToolsListStream extends McpRequestStream
    {
        McpToolsListStream(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            super(session, sender, originId, routedId, initialId, resolvedId, affinity,
                HttpToolsListStream::new);
        }
    }

    private final class McpToolsCallStream extends McpRequestStream
    {
        McpToolsCallStream(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            super(session, sender, originId, routedId, initialId, resolvedId, affinity,
                HttpToolsCallStream::new);
        }
    }

    private final class McpPromptsListStream extends McpRequestStream
    {
        McpPromptsListStream(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            super(session, sender, originId, routedId, initialId, resolvedId, affinity,
                HttpPromptsListStream::new);
        }
    }

    private final class McpPromptsGetStream extends McpRequestStream
    {
        McpPromptsGetStream(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            super(session, sender, originId, routedId, initialId, resolvedId, affinity,
                HttpPromptsGetStream::new);
        }
    }

    private final class McpResourcesListStream extends McpRequestStream
    {
        McpResourcesListStream(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            super(session, sender, originId, routedId, initialId, resolvedId, affinity,
                HttpResourcesListStream::new);
        }
    }

    private final class McpResourcesReadStream extends McpRequestStream
    {
        McpResourcesReadStream(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            super(session, sender, originId, routedId, initialId, resolvedId, affinity,
                HttpResourcesReadStream::new);
        }
    }

    private abstract class HttpStream
    {
        protected final long originId;
        protected final long routedId;
        protected final long initialId;
        protected final long replyId;
        protected final long affinity;
        protected final McpStream mcp;

        protected MessageConsumer net;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private long encodeSlotTraceId;
        private long encodeSlotAuthorization;

        protected int decodeSlot = NO_SLOT;
        protected int decodeSlotOffset;
        protected int decodeSlotReserved;

        protected HttpResponseDecoder decoder;
        protected JsonParser decodableJson;
        protected int decodedResultProgress;
        protected int decodedParserProgress;
        protected HttpResponseDecoder decodedSkipObjectThen;
        protected int decodedSkipObjectDepth;

        protected boolean sseMode;
        protected String sseEventId;
        protected long sseEventRetry = -1L;
        protected int sseLineState;
        protected byte sseFieldKind;
        protected final StringBuilder sseSmallValue = new StringBuilder();
        protected boolean sseEventHasData;
        protected boolean sseEventProgress;
        protected String sseProgressToken;
        protected long sseProgress;
        protected long sseProgressTotal = -1L;
        protected String sseProgressMessage;

        private int state;

        HttpStream(
            McpStream mcp)
        {
            this.originId = mcp.routedId;
            this.routedId = mcp.resolvedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = mcp.affinity;
            this.mcp = mcp;
            this.decoder = decodeIgnore;
            this.replyMax = decodeMax;
        }

        void onNetMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onNetData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onNetFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetReset(reset);
                break;
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();

            assert acknowledge <= sequence;

            state = McpState.openingReply(state);
            replySeq = sequence;
            replyAck = acknowledge;
            replyMax = decodeMax;

            assert replyAck <= replySeq;

            final OctetsFW ext = begin.extension();
            if (ext.sizeof() > 0)
            {
                final HttpBeginExFW httpBeginEx = httpBeginExRO.tryWrap(
                    ext.buffer(), ext.offset(), ext.limit());
                if (httpBeginEx != null)
                {
                    final HttpHeaderFW contentType = httpBeginEx.headers()
                        .matchFirst(h -> HTTP_HEADER_CONTENT_TYPE.equals(h.name().asString()));
                    if (contentType != null &&
                        CONTENT_TYPE_EVENT_STREAM.equals(contentType.value().asString()))
                    {
                        decoder = decodeSse;
                        sseMode = true;
                    }
                }
            }

            mcp.onNetBegin(begin);

            flushNetWindow(traceId, authorization, 0L);
        }

        void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + data.reserved();

            assert replyAck <= replySeq;

            if (replySeq > replyAck + decodeMax)
            {
                cleanupNet(traceId, authorization);
            }
            else
            {
                final OctetsFW payload = data.payload();
                int reserved = data.reserved();
                DirectBuffer buffer = payload.buffer();
                int offset = payload.offset();
                int limit = payload.limit();

                if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBuffer slotBuffer = decodePool.buffer(decodeSlot);
                    slotBuffer.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                    decodeSlotOffset += payload.sizeof();
                    decodeSlotReserved += reserved;

                    buffer = slotBuffer;
                    offset = 0;
                    limit = decodeSlotOffset;
                    reserved = decodeSlotReserved;
                }

                if (decodableJson != null)
                {
                    final int delta = (int) (decodableJson.getLocation().getStreamOffset() - decodedParserProgress);
                    final DirectBufferInputStreamEx input = inputRO;
                    input.wrap(buffer, offset + delta, limit - offset - delta);
                }

                decodeNet(traceId, authorization, budgetId, reserved, buffer, offset, limit);
            }
        }

        private void decodeNet(
            long traceId,
            long authorization,
            long budgetId)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer buffer = decodePool.buffer(decodeSlot);
                final int reserved = decodeSlotReserved;
                final int offset = 0;
                final int limit = decodeSlotOffset;

                decodeNet(traceId, authorization, budgetId, reserved, buffer, offset, limit);
            }

            flushNetWindow(traceId, authorization, budgetId);
        }

        void decodeNet(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            HttpResponseDecoder previous = null;
            int progress = offset;
            while (progress <= limit && previous != decoder)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, authorization, budgetId, reserved, buffer, offset, progress, limit);
            }

            decodedParserProgress += progress - offset;

            if (progress < limit)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(replyId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNet(traceId, authorization);
                }
                else
                {
                    final MutableDirectBuffer slot = decodePool.buffer(decodeSlot);
                    slot.putBytes(0, buffer, progress, limit - progress);
                    decodeSlotOffset = limit - progress;
                    decodeSlotReserved = (int) ((long) reserved * (limit - progress) / (limit - offset));
                }
            }
            else
            {
                cleanupDecodeSlot();
            }

            if (McpState.replyClosing(state) &&
                decodeSlot == BufferPool.NO_SLOT &&
                mcp != null)
            {
                state = McpState.closedReply(state);
                mcp.onNetEnd(traceId, authorization);
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence;
            state = McpState.closingReply(state);

            assert replyAck <= replySeq;

            if (decodeSlot == BufferPool.NO_SLOT)
            {
                state = McpState.closedReply(state);
                mcp.onNetEnd(traceId, authorization);
            }
        }

        private void onNetFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + flush.reserved();
            replyAck = replySeq;

            assert replyAck <= replySeq;
            doNetWindow(traceId, authorization, 0L, 0);
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            state = McpState.openedReply(state);

            doWindow(net, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = McpState.closedReply(state);

            assert replyAck <= replySeq;

            cleanupDecodeSlot();
            mcp.onNetAbort(this, traceId, authorization);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;

            state = McpState.openedInitial(state);
            initialAck = acknowledge;
            initialMax = maximum;
            initialPad = padding;

            assert initialAck <= initialSeq;

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer encodeBuffer = encodePool.buffer(encodeSlot);
                final int limit = encodeSlotOffset;
                final long slotTraceId = encodeSlotTraceId;
                final long slotAuthorization = encodeSlotAuthorization;

                encodeSlotOffset = 0;
                encodeNet(slotTraceId, slotAuthorization, encodeBuffer, 0, limit);
            }

            mcp.flushAppWindow(traceId, authorization, 0L, 0);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();

            assert acknowledge <= sequence;
            assert acknowledge <= initialAck;

            initialAck = acknowledge;
            state = McpState.closedInitial(state);

            assert initialAck <= initialSeq;

            cleanupEncodeSlot();

            mcp.onNetReset(this, traceId, authorization);
        }

        private void flushNetWindow(
            long traceId,
            long authorization,
            long budgetId)
        {
            final long replyAckMax = Math.max(replySeq - decodeSlotReserved, replyAck);
            if (replyAckMax > replyAck || !McpState.replyOpened(state))
            {
                replyAck = replyAckMax;
                assert replyAck <= replySeq;

                doNetWindow(traceId, authorization, budgetId, 0);
            }
        }

        abstract void doEncodeRequestBegin(long traceId, long authorization);

        void doEncodeRequestData(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            doNetData(traceId, authorization, buffer, offset, limit);
        }

        void doEncodeRequestEnd(
            long traceId,
            long authorization)
        {
            doNetEnd(traceId, authorization);
        }

        protected void doNetBegin(
            long traceId,
            long authorization,
            HttpBeginExFW httpBeginEx)
        {
            state = McpState.openingInitial(state);

            net = newStream(this::onNetMessage,
                originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, httpBeginEx);

            assert net != null;
        }

        void doNetData(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer encodeBuffer = encodePool.buffer(encodeSlot);
                encodeBuffer.putBytes(encodeSlotOffset, buffer, offset, limit - offset);
                encodeSlotOffset += limit - offset;
                encodeSlotTraceId = traceId;
                encodeSlotAuthorization = authorization;

                buffer = encodeBuffer;
                offset = 0;
                limit = encodeSlotOffset;
            }

            encodeNet(traceId, authorization, buffer, offset, limit);
        }

        void doNetEnd(
            long traceId,
            long authorization)
        {
            state = McpState.closingInitial(state);

            if (!McpState.initialClosed(state) &&
                encodeSlot == NO_SLOT)
            {
                state = McpState.closedInitial(state);
                doEnd(net, originId, routedId, initialId, traceId, authorization);

                cleanupEncodeSlot();
            }
        }

        void doNetAbort(
            long traceId,
            long authorization)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doAbort(net, originId, routedId, initialId, traceId, authorization);

                cleanupEncodeSlot();
            }
        }

        void doNetReset(
            long traceId,
            long authorization)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doReset(net, originId, routedId, replyId, traceId, authorization);

                cleanupDecodeSlot();
            }
        }

        private int onDecodeResponseResult(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            return mcp != null ? mcp.doAppData(traceId, authorization, buffer, offset, limit) : offset;
        }

        private void onDecodeParseError(
            long traceId,
            long authorization)
        {
            cleanupNet(traceId, authorization);
        }

        private void onDecodeInvalidResponse(
            long traceId,
            long authorization)
        {
            cleanupNet(traceId, authorization);
        }

        private void encodeNet(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int initialWin = initialMax - (int)(initialSeq - initialAck);
            final int length = Math.max(Math.min(initialWin, maxLength), 0);

            if (length > 0)
            {
                final int reserved = length;

                doData(net, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, DATA_FLAGS_COMPLETE, 0L, reserved,
                    buffer, offset, length);

                initialSeq += reserved;
            }

            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(initialId);
                }

                if (encodeSlot == NO_SLOT)
                {
                    cleanupNet(traceId, authorization);
                }
                else
                {
                    final MutableDirectBuffer encodeBuffer = encodePool.buffer(encodeSlot);
                    encodeBuffer.putBytes(0, buffer, offset + length, remaining);
                    encodeSlotOffset = remaining;
                    encodeSlotTraceId = traceId;
                    encodeSlotAuthorization = authorization;
                }
            }
            else
            {
                cleanupEncodeSlot();

                if (McpState.initialClosing(state))
                {
                    doNetEnd(traceId, authorization);
                }
            }
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeSlotTraceId = 0;
                encodeSlotAuthorization = 0;
            }
        }

        protected void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
                decodeSlotReserved = 0;
            }
        }

        private void cleanupNet(
            long traceId,
            long authorization)
        {
            doNetAbort(traceId, authorization);
            doNetReset(traceId, authorization);
            mcp.cleanupApp(traceId, authorization);
        }
    }

    private final class HttpInitializeRequest extends HttpStream
    {
        HttpInitializeRequest(
            McpStream mcp)
        {
            super(mcp);
        }

        @Override
        void doEncodeRequestBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    builder.headersItem(h -> h.name(name).value(value)));
            }
            final HttpBeginExFW httpBeginEx = builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION))
                .build();

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"%s","capabilities":{},\
                "clientInfo":{"name":"%s","version":"%s"}}}\
                """.formatted(MCP_PROTOCOL_VERSION, clientName, clientVersion));

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }
    }

    private final class HttpNotifyInitialized extends HttpStream
    {
        HttpNotifyInitialized(
            McpStream mcp)
        {
            super(mcp);
        }

        @Override
        void doEncodeRequestBegin(
            long traceId,
            long authorization)
        {
            final String sid = ((McpLifecycleStream) mcp).responseSessionId;
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    builder.headersItem(h -> h.name(name).value(value)));
            }
            final HttpBeginExFW httpBeginEx = builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid))
                .build();

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }
    }

    private final class HttpKeepalive extends HttpStream
    {
        HttpKeepalive(
            McpStream mcp)
        {
            super(mcp);
        }

        @Override
        void doEncodeRequestBegin(
            long traceId,
            long authorization)
        {
            final String sid = ((McpLifecycleStream) mcp).responseSessionId;
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    builder.headersItem(h -> h.name(name).value(value)));
            }
            final HttpBeginExFW httpBeginEx = builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid))
                .build();

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"ping\"}"
                    .formatted(((McpLifecycleStream) mcp).nextRequestId++));

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }
    }

    private final class HttpEventStream
    {
        private final McpStream lifecycle;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final String lastEventId;

        private MessageConsumer net;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private String currentEventId;
        private final StringBuilder currentEventData = new StringBuilder();
        private long currentEventRetry;

        HttpEventStream(
            McpStream lifecycle,
            String lastEventId)
        {
            this.lifecycle = lifecycle;
            this.originId = lifecycle.routedId;
            this.routedId = lifecycle.resolvedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = lifecycle.affinity;
            this.replyMax = decodeMax;
            this.lastEventId = lastEventId;
        }

        void doNetStart(
            long traceId,
            long authorization)
        {
            final String sid = lifecycle.resumeSessionId();
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (lifecycle.with != null && lifecycle.with.headers != null)
            {
                lifecycle.with.headers.forEach((name, value) ->
                    builder.headersItem(h -> h.name(name).value(value)));
            }
            builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_GET))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));
            if (lastEventId != null && !lastEventId.isEmpty())
            {
                builder.headersItem(h -> h.name(HTTP_HEADER_LAST_EVENT_ID).value(lastEventId));
            }
            final HttpBeginExFW httpBeginEx = builder.build();

            state = McpState.openingInitial(state);
            net = newStream(this::onNetMessage,
                originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, httpBeginEx);

            assert net != null;

            state = McpState.closedInitial(state);
            doEnd(net, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization);
        }

        void doNetAbort(
            long traceId,
            long authorization)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doAbort(net, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        void doNetReset(
            long traceId,
            long authorization)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doReset(net, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
            }
        }

        private void onNetMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onNetData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetReset(reset);
                break;
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            replySeq = begin.sequence();
            replyAck = begin.acknowledge();
            replyMax = decodeMax;
            state = McpState.openedReply(state);

            final OctetsFW ext = begin.extension();
            String status = null;
            if (ext.sizeof() > 0)
            {
                final HttpBeginExFW httpBeginEx = httpBeginExRO.tryWrap(
                    ext.buffer(), ext.offset(), ext.limit());
                if (httpBeginEx != null)
                {
                    final HttpHeaderFW statusHeader = httpBeginEx.headers()
                        .matchFirst(h -> HTTP_HEADER_STATUS.equals(h.name().asString()));
                    if (statusHeader != null)
                    {
                        status = statusHeader.value().asString();
                    }
                }
            }

            if (STATUS_405.equals(status))
            {
                lifecycle.markEventsUnsupported();
                lifecycle.relaySuspend(traceId, authorization, SUSPEND_RETRY_NEVER);
                doNetReset(traceId, authorization);
                detach();
            }
            else
            {
                lifecycle.relayResumable(traceId, authorization, null);
                doNetWindow(traceId, authorization, 0L, 0);
            }
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            replySeq = data.sequence() + data.reserved();

            final OctetsFW payload = data.payload();
            if (payload != null)
            {
                decodeSse(traceId, authorization, payload.buffer(), payload.offset(), payload.limit());
            }

            doNetWindow(traceId, authorization, budgetId, 0);
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            state = McpState.closedReply(state);
            cleanupDecodeSlot();
            lifecycle.relayCompletion(traceId, authorization);
            detach();
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = McpState.closedReply(state);
            cleanupDecodeSlot();

            if (lifecycle.eventStreamRef() == this)
            {
                lifecycle.relaySuspended(traceId, authorization);
            }
            detach();
        }

        private void onNetReset(
            ResetFW reset)
        {
            state = McpState.closedInitial(state);
            cleanupDecodeSlot();
            detach();
        }

        private void onNetWindow(
            WindowFW window)
        {
            state = McpState.openedInitial(state);
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            doWindow(net, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding);
        }

        private void detach()
        {
            if (lifecycle.eventStreamRef() == this)
            {
                lifecycle.clearEventStream();
            }
        }

        private void decodeSse(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final int length = limit - offset;

            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(replyId);
            }

            if (decodeSlot == NO_SLOT)
            {
                return;
            }

            final MutableDirectBuffer slotBuffer = decodePool.buffer(decodeSlot);
            slotBuffer.putBytes(decodeSlotOffset, buffer, offset, length);
            decodeSlotOffset += length;

            int progress = 0;
            int eventStart = 0;
            while (progress < decodeSlotOffset - 1)
            {
                if (slotBuffer.getByte(progress) == (byte) '\n' &&
                    slotBuffer.getByte(progress + 1) == (byte) '\n')
                {
                    decodeSseEvent(traceId, authorization, slotBuffer, eventStart, progress);
                    progress += 2;
                    eventStart = progress;
                }
                else
                {
                    progress++;
                }
            }

            if (eventStart > 0)
            {
                final int remaining = decodeSlotOffset - eventStart;
                if (remaining > 0)
                {
                    slotBuffer.putBytes(0, slotBuffer, eventStart, remaining);
                }
                decodeSlotOffset = remaining;
            }

            if (decodeSlotOffset == 0)
            {
                cleanupDecodeSlot();
            }
        }

        private void decodeSseEvent(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int eventStart,
            int eventEnd)
        {
            currentEventId = null;
            currentEventData.setLength(0);
            currentEventRetry = -1L;

            int lineStart = eventStart;
            for (int i = eventStart; i < eventEnd; i++)
            {
                if (buffer.getByte(i) == (byte) '\n')
                {
                    decodeSseLine(buffer, lineStart, i);
                    lineStart = i + 1;
                }
            }
            if (lineStart < eventEnd)
            {
                decodeSseLine(buffer, lineStart, eventEnd);
            }

            if (currentEventRetry >= 0)
            {
                lifecycle.relaySuspend(traceId, authorization, currentEventRetry);
            }
            else if (currentEventId != null && currentEventData.length() == 0)
            {
                lifecycle.relayResumable(traceId, authorization, stripEventIdPrefix(currentEventId));
            }
            else if (currentEventData.length() > 0)
            {
                lifecycle.relayNotification(traceId, authorization,
                    stripEventIdPrefix(currentEventId), currentEventData.toString());
            }
        }

        private void decodeSseLine(
            DirectBuffer buffer,
            int lineStart,
            int lineEnd)
        {
            final int length = lineEnd - lineStart;
            if (length == 0 || buffer.getByte(lineStart) == (byte) ':')
            {
                return;
            }

            int colon = -1;
            for (int i = lineStart; i < lineEnd; i++)
            {
                if (buffer.getByte(i) == (byte) ':')
                {
                    colon = i;
                    break;
                }
            }
            if (colon < 0)
            {
                return;
            }

            int valueStart = colon + 1;
            if (valueStart < lineEnd && buffer.getByte(valueStart) == (byte) ' ')
            {
                valueStart++;
            }

            final String fieldName = buffer.getStringWithoutLengthAscii(lineStart, colon - lineStart);
            final String fieldValue = buffer.getStringWithoutLengthAscii(valueStart, lineEnd - valueStart);

            switch (fieldName)
            {
            case "id":
                currentEventId = fieldValue;
                break;
            case "data":
                if (currentEventData.length() > 0)
                {
                    currentEventData.append('\n');
                }
                currentEventData.append(fieldValue);
                break;
            case "retry":
                try
                {
                    currentEventRetry = Long.parseLong(fieldValue);
                }
                catch (NumberFormatException ignored)
                {
                }
                break;
            default:
                break;
            }
        }

        private String stripEventIdPrefix(
            String id)
        {
            if (id == null)
            {
                return "";
            }
            final int colon = id.lastIndexOf(':');
            return colon < 0 ? id : id.substring(colon + 1);
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
            }
        }
    }

    private abstract class HttpRequestStream extends HttpStream
    {
        protected final McpRequestStream request;

        HttpRequestStream(
            McpStream mcp)
        {
            super(mcp);
            this.request = (McpRequestStream) mcp;
            this.decoder = decodeJsonRpc;
        }
    }

    private final class HttpToolsListStream extends HttpRequestStream
    {
        HttpToolsListStream(
            McpStream mcp)
        {
            super(mcp);
        }

        @Override
        void doEncodeRequestBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW.Builder extBuilder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    extBuilder.headersItem(h -> h.name(name).value(value)));
            }
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            final String sid = mcp.sessionId;
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/list\"}".formatted(request.requestId));

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }
    }

    private final class HttpToolsCallStream extends HttpRequestStream
    {
        HttpToolsCallStream(
            McpStream mcp)
        {
            super(mcp);
        }

        @Override
        void doEncodeRequestBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW.Builder extBuilder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    extBuilder.headersItem(h -> h.name(name).value(value)));
            }
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            final String sid = mcp.sessionId;
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/call\",\"params\":".formatted(request.requestId));

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }

        @Override
        void doEncodeRequestEnd(
            long traceId,
            long authorization)
        {
            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0, "}");
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
            doNetEnd(traceId, authorization);
        }
    }

    private final class HttpPromptsListStream extends HttpRequestStream
    {
        HttpPromptsListStream(
            McpStream mcp)
        {
            super(mcp);
        }

        @Override
        void doEncodeRequestBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW.Builder extBuilder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    extBuilder.headersItem(h -> h.name(name).value(value)));
            }
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            final String sid = mcp.sessionId;
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"prompts/list\"}".formatted(request.requestId));

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }

    }

    private final class HttpPromptsGetStream extends HttpRequestStream
    {
        HttpPromptsGetStream(
            McpStream mcp)
        {
            super(mcp);
        }

        @Override
        void doEncodeRequestBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW.Builder extBuilder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    extBuilder.headersItem(h -> h.name(name).value(value)));
            }
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            final String sid = mcp.sessionId;
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"prompts/get\",\"params\":".formatted(request.requestId));

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }

        @Override
        void doEncodeRequestEnd(
            long traceId,
            long authorization)
        {
            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0, "}");
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
            doNetEnd(traceId, authorization);
        }

    }

    private final class HttpResourcesListStream extends HttpRequestStream
    {
        HttpResourcesListStream(
            McpStream mcp)
        {
            super(mcp);
        }

        @Override
        void doEncodeRequestBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW.Builder extBuilder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    extBuilder.headersItem(h -> h.name(name).value(value)));
            }
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            final String sid = mcp.sessionId;
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"resources/list\"}".formatted(request.requestId));

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }

    }

    private final class HttpResourcesReadStream extends HttpRequestStream
    {
        HttpResourcesReadStream(
            McpStream mcp)
        {
            super(mcp);
        }

        @Override
        void doEncodeRequestBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW.Builder extBuilder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    extBuilder.headersItem(h -> h.name(name).value(value)));
            }
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            final String sid = mcp.sessionId;
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"resources/read\",\"params\":".formatted(request.requestId));

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }

        @Override
        void doEncodeRequestEnd(
            long traceId,
            long authorization)
        {
            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0, "}");
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
            doNetEnd(traceId, authorization);
        }

    }

    private final class HttpTerminateSession
    {
        private final long initialId;
        private final long replyId;
        private final McpStream mcp;

        private MessageConsumer net;
        private int initialMax;
        private boolean endSent;

        HttpTerminateSession(
            McpStream mcp)
        {
            this.initialId = supplyInitialId.applyAsLong(mcp.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.mcp = mcp;
        }

        void doNetBegin(
            long traceId,
            long authorization)
        {
            final String sid = mcp.sessionId;
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    builder.headersItem(h -> h.name(name).value(value)));
            }
            final HttpBeginExFW httpBeginEx = builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value("DELETE"))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid))
                .build();

            net = newStream(this::onNetMessage, mcp.routedId, mcp.resolvedId, initialId,
                0, 0, 0, traceId, authorization, mcp.affinity, httpBeginEx);

            if (net != null && initialMax > 0)
            {
                endSent = true;
                doEnd(net, mcp.routedId, mcp.resolvedId, initialId, traceId, authorization);
            }
        }

        private void onNetMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetBegin(begin);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
            case DataFW.TYPE_ID:
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetAbort(abort);
                break;
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            doWindow(net, mcp.routedId, mcp.resolvedId, replyId,
                begin.traceId(), begin.authorization(), 0, writeBuffer.capacity(), 0);
        }

        private void onNetWindow(
            WindowFW window)
        {
            initialMax = window.maximum();
            if (!endSent && initialMax > 0)
            {
                endSent = true;
                doEnd(net, mcp.routedId, mcp.resolvedId, initialId,
                    window.traceId(), window.authorization());
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            mcp.doAppEnd(traceId, authorization);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            mcp.doAppAbort(traceId, authorization);
        }
    }

    private final class HttpNotifyCancelled
    {
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final String sessionId;
        private final int requestId;
        private final McpWithConfig with;

        private MessageConsumer net;
        private long authorization;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        HttpNotifyCancelled(
            McpRequestStream mcp)
        {
            this.initialId = supplyInitialId.applyAsLong(mcp.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.sessionId = mcp.sessionId;
            this.requestId = mcp.requestId;
            this.originId = mcp.routedId;
            this.routedId = mcp.resolvedId;
            this.affinity = mcp.affinity;
            this.with = mcp.with;
        }

        void doNetBegin(
            long traceId,
            long authorization)
        {
            this.authorization = authorization;
            state = McpState.openingInitial(state);

            final String sid = sessionId;
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (with != null && with.headers != null)
            {
                with.headers.forEach((name, value) ->
                    builder.headersItem(h -> h.name(name).value(value)));
            }
            final HttpBeginExFW httpBeginEx = builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid))
                .build();

            net = newStream(this::onNetMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, httpBeginEx);
        }

        void doNetData(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final int length = limit - offset;
            final int reserved = length;

            doData(net, originId, routedId, initialId,
                initialSeq, initialAck, initialMax,
                traceId, authorization,
                DATA_FLAGS_COMPLETE, 0L, reserved,
                buffer, offset, length);

            initialSeq += reserved;
        }

        void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doEnd(net, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        void doNetAbort(
            long traceId,
            long authorization)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doAbort(net, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        void doNetReset(
            long traceId,
            long authorization)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doReset(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax,
                    traceId, authorization);
            }
        }

        private void onNetMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetBegin(begin);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetReset(reset);
                break;
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();

            assert acknowledge <= sequence;

            state = McpState.openedReply(state);
            replySeq = sequence;
            replyAck = acknowledge;
            replyMax = writeBuffer.capacity();

            assert replyAck <= replySeq;

            doWindow(net, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, 0L, 0);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();

            assert acknowledge <= sequence;

            state = McpState.openedInitial(state);
            initialAck = acknowledge;
            initialMax = maximum;

            if (!McpState.initialClosed(state))
            {
                final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                    """
                    {"jsonrpc":"2.0","method":"notifications/cancelled","params":\
                    {"requestId":%d,"reason":"User cancelled"}}\
                    """.formatted(requestId));

                doNetData(traceId, authorization, codecBuffer, 0, codecLength);
                doNetEnd(traceId, authorization);
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            doNetEnd(traceId, authorization);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            doNetAbort(traceId, authorization);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            doNetReset(traceId, authorization);
        }
    }

    private MessageConsumer newStream(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        HttpBeginExFW extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        final MessageConsumer receiver =
            streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        if (receiver != null)
        {
            receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        return receiver;
    }

    private void doBegin(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
    {
        final BeginFW.Builder builder = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity);
        if (extension != null && extension.sizeof() > 0)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }
        final BeginFW begin = builder.build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer payload,
        int offset,
        int length)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .flags(flags)
            .budgetId(budgetId)
            .reserved(reserved)
            .payload(payload, offset, length)
            .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization)
    {
        doEnd(receiver, originId, routedId, streamId, 0L, 0L, 0, traceId, authorization);
    }

    private void doEnd(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doAbort(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization)
    {
        doAbort(receiver, originId, routedId, streamId, 0L, 0L, 0, traceId, authorization);
    }

    private void doAbort(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doReset(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization)
    {
        doReset(receiver, originId, routedId, streamId, 0L, 0L, 0, traceId, authorization);
    }

    private void doReset(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doFlush(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        Flyweight extension)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .reserved(reserved)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doChallenge(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
    }

    private void doWindow(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization,
        long budgetId,
        int credit,
        int padding)
    {
        doWindow(receiver, originId, routedId, streamId, 0L, 0L, credit,
            traceId, authorization, budgetId, padding);
    }

    private void doWindow(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int padding)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .padding(padding)
            .build();

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }
}
