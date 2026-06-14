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

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.CLIENT_ELICITATION;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.CLIENT_ELICITATION_URL;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_PROMPTS;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_PROMPTS_LIST_CHANGED;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_RESOURCES;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_RESOURCES_LIST_CHANGED;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_TOOLS;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.SERVER_TOOLS_LIST_CHANGED;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_LIFECYCLE;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_GET;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_READ;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_CALL;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

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
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBearerError;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpChallengeExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpElicitAction;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpElicitResponseFlushExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpEndExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpFlushExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpOutcome;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpResetExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.json.DirectBufferInputStreamEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler.LongCompletionCallback;
import io.aklivity.zilla.runtime.engine.util.function.LongIntPredicate;

public final class McpClientFactory implements McpStreamFactory
{
    private static final int SERVER_CAPABILITIES =
        SERVER_TOOLS.value() |
        SERVER_PROMPTS.value() |
        SERVER_RESOURCES.value();

    private static final String JSON_KEY_CAPABILITIES = "capabilities";
    private static final String JSON_KEY_PROTOCOL_VERSION = "protocolVersion";
    private static final String JSON_KEY_TOOLS = "tools";
    private static final String JSON_KEY_PROMPTS = "prompts";
    private static final String JSON_KEY_RESOURCES = "resources";
    private static final String JSON_KEY_LIST_CHANGED = "listChanged";

    private static final String HTTP_TYPE_NAME = "http";
    private static final String MCP_TYPE_NAME = "mcp";

    private static final int KEEPALIVE_SIGNAL_ID = 1;
    private static final int ELICIT_TIMEOUT_SIGNAL_ID = 2;

    private static final String JSON_RPC_VERSION = "2.0";
    private static final String HTTP_HEADER_METHOD = ":method";
    private static final String HTTP_HEADER_CONTENT_TYPE = "content-type";
    private static final String HTTP_HEADER_CONTENT_LENGTH = "content-length";
    private static final String HTTP_HEADER_ACCEPT = "accept";
    private static final String HTTP_HEADER_STATUS = ":status";
    private static final String HTTP_HEADER_SESSION = "mcp-session-id";
    private static final String HTTP_HEADER_AUTHORIZATION = "authorization";
    private static final String HTTP_HEADER_WWW_AUTHENTICATE = "www-authenticate";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HTTP_HEADER_MCP_VERSION = "mcp-protocol-version";
    private static final String HTTP_HEADER_LAST_EVENT_ID = "last-event-id";
    private static final String STATUS_401 = "401";
    private static final String STATUS_403 = "403";
    private static final String STATUS_405 = "405";
    private static final Pattern BEARER_CHALLENGE_PATTERN = Pattern.compile(
        "^\\s*Bearer\\b" +
        "(?=.*?\\brealm\\s*=\\s*\"(?<realm>[^\"]*)\")?" +
        "(?=.*?\\bscope\\s*=\\s*\"(?<scope>[^\"]*)\")?" +
        "(?=.*?\\bresource_metadata\\s*=\\s*\"(?<resourceMetadata>[^\"]*)\")?" +
        "(?=.*?\\berror\\s*=\\s*\"(?<error>invalid_request|invalid_token|insufficient_scope)\")?" +
        ".*$",
        Pattern.CASE_INSENSITIVE);
    private static final long SUSPEND_RETRY_NEVER = -1L;
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_JSON_AND_EVENT_STREAM = "application/json, text/event-stream";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String MCP_PROTOCOL_VERSION = "2025-11-25";
    private static final String HTTP_METHOD_GET = "GET";
    private static final String HTTP_METHOD_POST = "POST";

    private static final String JSON_RPC_REQUEST_ID_PREFIX = "{\"jsonrpc\":\"2.0\",\"id\":";
    private static final String JSON_RPC_TOOLS_LIST_METHOD = ",\"method\":\"tools/list\"}";
    private static final String JSON_RPC_TOOLS_CALL_METHOD = ",\"method\":\"tools/call\",\"params\":";
    private static final String JSON_RPC_PROMPTS_LIST_METHOD = ",\"method\":\"prompts/list\"}";
    private static final String JSON_RPC_PROMPTS_GET_METHOD = ",\"method\":\"prompts/get\",\"params\":";
    private static final String JSON_RPC_RESOURCES_LIST_METHOD = ",\"method\":\"resources/list\"}";
    private static final String JSON_RPC_RESOURCES_READ_METHOD = ",\"method\":\"resources/read\",\"params\":";
    private static final String JSON_RPC_PING_METHOD = ",\"method\":\"ping\"}";
    private static final String JSON_RPC_NOTIFY_CANCELLED_PREFIX =
        "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{\"requestId\":";
    private static final String JSON_RPC_NOTIFY_CANCELLED_SUFFIX = ",\"reason\":\"User cancelled\"}}";
    private static final String JSON_RPC_ELICIT_RESPONSE_PREFIX = "{\"jsonrpc\":\"2.0\",\"id\":";
    private static final String JSON_RPC_ELICIT_RESPONSE_MIDDLE = ",\"result\":{\"action\":\"";
    private static final String JSON_RPC_ELICIT_RESPONSE_SUFFIX = "\"}}";
    private static final String JSON_RPC_INITIALIZE_PREFIX =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"";
    private static final String JSON_RPC_INITIALIZE_CAPABILITIES_PREFIX = "\",\"capabilities\":";
    private static final String JSON_RPC_INITIALIZE_CLIENT_INFO = ",\"clientInfo\":{\"name\":\"";
    private static final String JSON_RPC_CAPABILITIES_NONE = "{}";
    private static final String JSON_RPC_CAPABILITIES_ELICITATION_FORM = "{\"elicitation\":{}}";
    private static final String JSON_RPC_CAPABILITIES_ELICITATION_URL = "{\"elicitation\":{\"url\":{}}}";
    private static final String JSON_RPC_INITIALIZE_VERSION_PREFIX = "\",\"version\":\"";
    private static final String JSON_RPC_INITIALIZE_SUFFIX = "\"}}}";
    private static final String JSON_RPC_NOTIFY_INITIALIZED = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
    private static final String JSON_RPC_PARAMS_CLOSE = "}";

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
    private final McpFlushExFW mcpFlushExRO = new McpFlushExFW();

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
    private final McpEndExFW.Builder mcpEndExRW = new McpEndExFW.Builder();
    private final McpFlushExFW.Builder mcpFlushExRW = new McpFlushExFW.Builder();
    private final McpResetExFW.Builder mcpResetExRW = new McpResetExFW.Builder();

    private final DirectBufferInputStreamEx responseInputRO = new DirectBufferInputStreamEx();

    private final EngineContext context;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer codecBuffer;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final Supplier<String> supplySessionId;
    private final Supplier<String> supplyElicitationId;
    private final Supplier<String> supplyElicitCorrelationId;
    private final LongIntPredicate isLocalIndex;
    private final int sessionIdAttempts;
    private final int httpTypeId;
    private final int mcpTypeId;
    private final int decodeMax;
    private final int encodeMax;
    private final long inactivityTimeoutMillis;
    private final int keepaliveTolerance;
    private final Signaler signaler;
    private final String clientName;
    private final String clientVersion;

    private static final int SSE_LINE_START = 0;
    private static final int SSE_FIELD_NAME = 1;
    private static final int SSE_AFTER_COLON = 2;
    private static final int SSE_SMALL_VALUE = 3;
    private static final int SSE_IGNORE_VALUE = 4;

    private final JsonParserFactory responseParserFactory;
    private final JsonParserFactory requestParserFactory;
    private final DirectBufferInputStreamEx requestInputRO = new DirectBufferInputStreamEx();
    private final Matcher bearerChallengeMatcher = BEARER_CHALLENGE_PATTERN.matcher("");

    private final McpConfiguration config;
    private final Long2ObjectHashMap<McpBindingConfig> bindings;
    private final Map<String, McpStream> sessions = new Object2ObjectHashMap<>();
    private final Int2ObjectHashMap<McpSessionIdResolver> resolvers;
    private final Int2ObjectHashMap<McpRequestStreamFactory> requestFactories;

    public McpClientFactory(
        McpConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.codecBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplySessionId = config.sessionIdSupplier();
        this.supplyElicitationId = config.elicitationIdSupplier();
        this.supplyElicitCorrelationId = config.elicitCorrelationIdSupplier();
        this.isLocalIndex = context::isLocalIndex;
        this.sessionIdAttempts = config.sessionIdAttempts();
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
        this.responseParserFactory = JsonEx.createParserFactory(Map.of());
        this.requestParserFactory = JsonEx.createParserFactory(Map.of());

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

    // mint a per-route session id (Xc) aligned to this worker, so that subsequent request streams
    // hash-routed by Xc.hashCode() land back here where its session state lives
    private String newSessionId(
        long routedId)
    {
        return McpSessionId.newSessionId(routedId, sessionIdAttempts, supplySessionId, isLocalIndex);
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
            McpHttpStream http,
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
    private final HttpResponseDecoder decodeJsonRpcParamsElicitationId = this::decodeJsonRpcParamsElicitationId;
    private final HttpResponseDecoder decodeJsonRpcParamsUrl = this::decodeJsonRpcParamsUrl;
    private final HttpResponseDecoder decodeJsonRpcParamsMode = this::decodeJsonRpcParamsMode;
    private final HttpResponseDecoder decodeIgnore = this::decodeIgnore;

    @FunctionalInterface
    private interface McpRequestDecoder
    {
        int decode(
            McpRequestStream stream,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int progress,
            int limit);
    }

    private final McpRequestDecoder decodeJsonRpcParamsBody = this::decodeJsonRpcParamsBody;
    private final McpRequestDecoder decodeRequestEnd = this::decodeRequestEnd;

    private int decodeJsonRpcParamsBody(
        McpRequestStream stream,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = requestInputRO;
        input.wrap(buffer, progress, limit - progress);
        if (stream.paramsParser == null)
        {
            stream.paramsParser = requestParserFactory.createParser(input);
        }
        final JsonParser parser = stream.paramsParser;
        while (stream.decoder != decodeRequestEnd && parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                stream.paramsDepth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                stream.paramsDepth--;
                if (stream.paramsDepth == 0)
                {
                    parser.close();
                    stream.paramsParser = null;
                    stream.state = McpState.openedInitial(stream.state);
                    stream.http.doEncodeRequestEnd(traceId, authorization);
                    stream.decoder = decodeRequestEnd;
                }
                break;
            default:
                break;
            }
        }
        return limit - input.available();
    }

    private int decodeRequestEnd(
        McpRequestStream stream,
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

    private int decodeJsonRpc(
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
        input.wrap(buffer, progress, limit - progress);

        http.decodableJson = responseParserFactory.createParser(input);
        http.decoder = decodeJsonRpcStart;
        // Map parser stream-offset 0 to buffer position `progress`.
        // The decodeJsonRpc* methods use offset + decodedX - decodedParserProgress as buffer position,
        // so set decodedParserProgress = offset - progress to align.
        http.decodedParserProgress = offset - progress;

        progress = limit - input.available();

        return progress;
    }

    private int decodeJsonRpcStart(
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
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
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
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
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
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
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
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
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
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

            http.sseEventJsonId = parser.getString();
            http.decoder = decodeJsonRpcNext;

            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcResultStart(
        McpHttpStream http,
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
        McpHttpStream http,
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
        McpHttpStream http,
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
            else if (http.decodedSkipObjectThen == decodeJsonRpcResultEnd)
            {
                if (http.decodedResultErrorKey)
                {
                    http.responseError = event == JsonParser.Event.VALUE_TRUE;
                    http.decodedResultErrorKey = false;
                }
                else if (event == JsonParser.Event.KEY_NAME &&
                    http.decodedSkipObjectDepth == 1 &&
                    "isError".equals(parser.getString()))
                {
                    http.decodedResultErrorKey = true;
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
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
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
            else
            {
                http.sseEventMethod = method;
            }
            http.decoder = decodeJsonRpcNext;
            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcParamsStart(
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
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
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
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
                case "elicitationId":
                    http.decoder = decodeJsonRpcParamsElicitationId;
                    break;
                case "url":
                    http.decoder = decodeJsonRpcParamsUrl;
                    break;
                case "mode":
                    http.decoder = decodeJsonRpcParamsMode;
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
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
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
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
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
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
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
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
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

    private int decodeJsonRpcParamsElicitationId(
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
        JsonParser parser = http.decodableJson;

        if (parser.hasNext())
        {
            parser.next();
            http.sseElicitationId = parser.getString();
            http.decoder = decodeJsonRpcParamsNext;
            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcParamsUrl(
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
        JsonParser parser = http.decodableJson;

        if (parser.hasNext())
        {
            parser.next();
            http.sseElicitUrl = parser.getString();
            http.decoder = decodeJsonRpcParamsNext;
            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcParamsMode(
        McpHttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        DirectBufferInputStreamEx input = responseInputRO;
        JsonParser parser = http.decodableJson;

        if (parser.hasNext())
        {
            parser.next();
            http.sseElicitMode = parser.getString();
            http.decoder = decodeJsonRpcParamsNext;
            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeIgnore(
        McpHttpStream http,
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
        McpHttpStream http,
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
                    if (http.sseFieldKind == (byte) 'i')
                    {
                        http.sseEventId = http.sseSmallValue.toString();
                    }
                    else if (http.sseFieldKind == (byte) 'r')
                    {
                        try
                        {
                            http.sseEventRetry =
                                Long.parseLong(http.sseSmallValue, 0, http.sseSmallValue.length(), 10);
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
        McpHttpStream http,
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
        McpHttpStream http,
        long traceId,
        long authorization)
    {
        if (http.sseEventProgress)
        {
            http.mcp.onDecodeProgress(traceId, authorization,
                stripSseEventIdPrefix(http.sseEventId),
                http.sseProgressToken,
                http.sseProgress,
                http.sseProgressTotal,
                http.sseProgressMessage);
        }
        else if (http.sseEventRetry >= 0)
        {
            http.mcp.onDecodeSuspend(traceId, authorization, http.sseEventRetry);
        }
        else if ("elicitation/create".equals(http.sseEventMethod))
        {
            http.mcp.onDecodeElicitCreate(traceId, authorization,
                http.sseElicitationId,
                http.sseElicitUrl,
                http.sseProgressMessage,
                http.sseEventJsonId);
        }
        else if ("notifications/elicitation/complete".equals(http.sseEventMethod) ||
            "elicitation/complete".equals(http.sseEventMethod))
        {
            http.mcp.onDecodeElicitComplete(traceId, authorization,
                http.sseElicitationId);
        }
        else if (http.sseEventMethod != null)
        {
            http.mcp.onDecodeNotification(traceId, authorization,
                stripSseEventIdPrefix(http.sseEventId), http.sseEventMethod);
        }
        else if (http.sseEventId != null && !http.sseEventHasData)
        {
            http.mcp.onDecodeResumable(traceId, authorization, stripSseEventIdPrefix(http.sseEventId));
        }
    }

    private void resetSseEvent(
        McpHttpStream http)
    {
        http.sseEventId = null;
        http.sseEventRetry = -1L;
        http.sseEventHasData = false;
        http.sseEventProgress = false;
        http.sseEventMethod = null;
        http.sseEventJsonId = null;
        http.sseProgressToken = null;
        http.sseProgress = 0L;
        http.sseProgressTotal = -1L;
        http.sseProgressMessage = null;
        http.sseElicitationId = null;
        http.sseElicitUrl = null;
        http.sseElicitMode = null;
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
        McpBindingConfig newBinding = new McpBindingConfig(binding, config, context);
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
                    final String requestSessionId = mcpBeginEx.lifecycle().sessionId().asString();
                    assert requestSessionId == null;
                    final String sessionId = newSessionId(routedId);
                    if (sessionId != null)
                    {
                        newStream = new McpLifecycleStream(
                            binding, sender, originId, routedId, initialId, route.id, affinity,
                            sessionId)::onAppMessage;
                    }
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
                            request.contentLength = switch (mcpBeginEx.kind())
                            {
                            case KIND_TOOLS_CALL -> mcpBeginEx.toolsCall().contentLength();
                            case KIND_PROMPTS_GET -> mcpBeginEx.promptsGet().contentLength();
                            case KIND_RESOURCES_READ -> mcpBeginEx.resourcesRead().contentLength();
                            default -> -1;
                            };
                            request.timeout = switch (mcpBeginEx.kind())
                            {
                            case KIND_TOOLS_LIST -> mcpBeginEx.toolsList().timeout();
                            case KIND_TOOLS_CALL -> mcpBeginEx.toolsCall().timeout();
                            case KIND_PROMPTS_LIST -> mcpBeginEx.promptsList().timeout();
                            case KIND_PROMPTS_GET -> mcpBeginEx.promptsGet().timeout();
                            case KIND_RESOURCES_LIST -> mcpBeginEx.resourcesList().timeout();
                            case KIND_RESOURCES_READ -> mcpBeginEx.resourcesRead().timeout();
                            default -> 0L;
                            };
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

        protected HttpStream http;
        protected String credentials;
        protected int clientCapabilities;
        protected String authCallback;
        protected int serverCapabilities = SERVER_CAPABILITIES;
        protected McpRequestDecoder decoder;

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

        void onDecodeResumable(
            long traceId,
            long authorization,
            String id)
        {
        }

        void onDecodeProgress(
            long traceId,
            long authorization,
            String id,
            String token,
            long progress,
            long total,
            String message)
        {
        }

        void onDecodeSuspend(
            long traceId,
            long authorization,
            long retry)
        {
        }

        void onDecodeSuspended(
            long traceId,
            long authorization)
        {
        }

        void onDecodeNotification(
            long traceId,
            long authorization,
            String id,
            String method)
        {
        }

        void onDecodeElicitCreate(
            long traceId,
            long authorization,
            String elicitationId,
            String url,
            String message,
            String correlationId)
        {
        }

        void onDecodeElicitComplete(
            long traceId,
            long authorization,
            String elicitationId)
        {
        }

        void onDecodeCompletion(
            long traceId,
            long authorization)
        {
        }

        String transportSessionId()
        {
            return sessionId;
        }

        String protocolVersion()
        {
            return MCP_PROTOCOL_VERSION;
        }

        boolean isEventsUnsupported()
        {
            return false;
        }

        void markEventsUnsupported()
        {
        }

        HttpEventStream sseRef()
        {
            return null;
        }

        void clearSse()
        {
        }

        void onAppBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            state = McpState.openingInitial(state);

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            initialMax = encodeMax;

            final OctetsFW extension = begin.extension();
            final McpBeginExFW mcpBeginEx = extension.sizeof() > 0
                ? mcpBeginExRO.wrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            if (mcpBeginEx != null && mcpBeginEx.kind() == KIND_LIFECYCLE)
            {
                clientCapabilities = mcpBeginEx.lifecycle().capabilities();
                authCallback = mcpBeginEx.lifecycle().authCallback().asString();
            }

            if (proceedWithRequest(traceId, authorization, mcpBeginEx))
            {
                http.doEncodeRequestBegin(traceId, authorization);

                onAppBeginImpl(traceId, authorization, mcpBeginEx);
            }

            doAppWindow(traceId, authorization, 0L, 0);
        }

        abstract McpBindingConfig binding();

        boolean proceedWithRequest(
            long traceId,
            long authorization,
            McpBeginExFW mcpBeginEx)
        {
            final McpBindingConfig binding = binding();
            final GuardHandler guard = binding.guard;
            if (guard != null)
            {
                final long sessionId = guard.reauthorize(traceId, binding.id, initialId, null);
                if ((sessionId & GuardHandler.MASK_AUTHORIZED) != 0L)
                {
                    credentials = guard.credentials(sessionId);
                }
            }
            if (credentials == null)
            {
                credentials = binding.credentials;
            }
            return true;
        }

        void onAppBeginImpl(
            long traceId,
            long authorization,
            McpBeginExFW mcpBeginEx)
        {
        }

        void onAppData(
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
                if (!bufferAppData(traceId, authorization, payload))
                {
                    http.doEncodeRequestData(traceId, authorization,
                        payload.buffer(), payload.offset(), payload.limit());
                }
            }

            flushAppWindow(traceId, authorization, 0L, 0);
        }

        boolean bufferAppData(
            long traceId,
            long authorization,
            OctetsFW payload)
        {
            return false;
        }

        void onAppEnd(
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

            if (decoder != decodeRequestEnd)
            {
                http.doEncodeRequestEnd(traceId, authorization);
                decoder = decodeRequestEnd;
            }

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

        void onAppFlush(
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
                if (http != null && http.responseError)
                {
                    McpEndExFW endEx = mcpEndExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(mcpTypeId)
                        .outcome(o -> o.set(McpOutcome.ERROR))
                        .build();
                    doEnd(sender, originId, routedId, replyId,
                        replySeq, replyAck, replyMax,
                        traceId, authorization, endEx);
                }
                else
                {
                    doEnd(sender, originId, routedId, replyId,
                        replySeq, replyAck, replyMax,
                        traceId, authorization);
                }

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
            // client-kind throttle is wired on initialId
            doChallenge(sender, originId, routedId, initialId,
                initialSeq, initialAck, initialMax,
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

        void doAppReset(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doReset(sender, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization, extension);

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

        String remoteSessionId;
        String negotiatedVersion;
        private int nextRequestId = 2;
        private String elicitCorrelationId;
        private long reauthTraceId;
        private long reauthAuthorization;
        private long keepaliveId = Signaler.NO_CANCEL_ID;
        private long lastActiveAt;
        private int failedKeepalives;
        private HttpEventStream sse;
        boolean eventsUnsupported;

        private final LongCompletionCallback reauthorizeCompletion = new LongCompletionCallback()
        {
            @Override
            public void completed(
                long contextId,
                long sessionId)
            {
                onReauthorized(sessionId);
            }

            @Override
            public void failed(
                long contextId,
                Throwable ex)
            {
            }
        };

        @Override
        McpBindingConfig binding()
        {
            return binding;
        }

        @Override
        String transportSessionId()
        {
            return remoteSessionId != null ? remoteSessionId : sessionId;
        }

        @Override
        String protocolVersion()
        {
            return negotiatedVersion != null ? negotiatedVersion : MCP_PROTOCOL_VERSION;
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
        HttpEventStream sseRef()
        {
            return sse;
        }

        @Override
        void clearSse()
        {
            sse = null;
        }

        final McpBindingConfig binding;

        McpLifecycleStream(
            McpBindingConfig binding,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            String sessionId)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, sessionId,
                HttpInitializeRequest::new);
            this.binding = binding;
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
            state = McpState.openedInitial(state);
            http.doEncodeRequestEnd(traceId, authorization);
            decoder = decodeRequestEnd;
        }

        @Override
        void onAppChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            final McpChallengeExFW challengeEx = mcpChallengeExRO.tryWrap(
                extension.buffer(), extension.offset(), extension.limit());
            if (challengeEx != null)
            {
                switch (challengeEx.kind())
                {
                case McpChallengeExFW.KIND_RESUME:
                    if (sse != null)
                    {
                        doAppReset(traceId, authorization);
                        doAppAbort(traceId, authorization);
                    }
                    else if (eventsUnsupported)
                    {
                        onDecodeSuspend(traceId, authorization, SUSPEND_RETRY_NEVER);
                    }
                    else
                    {
                        final String16FW resumeId = challengeEx.resume().id();
                        final String lastEventId = resumeId != null ? resumeId.asString() : null;
                        sse = new HttpEventStream(this, lastEventId);
                        sse.doNetStart(traceId, authorization);
                        scheduleKeepalive(traceId);
                    }
                    break;
                case McpChallengeExFW.KIND_SUSPENDED:
                    if (sse != null)
                    {
                        sse.doNetAbort(traceId, authorization);
                        sse.detach();
                    }
                    cancelKeepalive();
                    break;
                default:
                    break;
                }
            }
        }

        @Override
        void onDecodeResumable(
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
        void onDecodeSuspend(
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
        void onDecodeSuspended(
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
        void onDecodeNotification(
            long traceId,
            long authorization,
            String id,
            String method)
        {
            final McpFlushExFW flushEx;
            switch (method)
            {
            case "notifications/tools/list_changed":
                flushEx = mcpFlushExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .toolsListChanged(b -> b.id(id))
                    .build();
                break;
            case "notifications/prompts/list_changed":
                flushEx = mcpFlushExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .promptsListChanged(b -> b.id(id))
                    .build();
                break;
            case "notifications/resources/list_changed":
                flushEx = mcpFlushExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .resourcesListChanged(b -> b.id(id))
                    .build();
                break;
            default:
                flushEx = null;
                break;
            }

            if (flushEx != null)
            {
                doAppFlush(traceId, authorization, flushEx);
            }
        }

        @Override
        void onDecodeElicitCreate(
            long traceId,
            long authorization,
            String elicitationId,
            String url,
            String message,
            String correlationId)
        {
            elicitCorrelationId = correlationId;
            final McpChallengeExFW challengeEx = mcpChallengeExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId)
                .elicitCreate(b ->
                {
                    b.id(elicitationId).url(url);
                    if (message != null)
                    {
                        b.message(message);
                    }
                    if (correlationId != null)
                    {
                        b.correlationId(correlationId);
                    }
                })
                .build();
            doAppChallenge(traceId, authorization, challengeEx);
        }

        @Override
        void onDecodeElicitComplete(
            long traceId,
            long authorization,
            String elicitationId)
        {
            final McpFlushExFW flushEx = mcpFlushExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId)
                .elicitComplete(b -> b.id(elicitationId))
                .build();
            doAppFlush(traceId, authorization, flushEx);

            elicitCorrelationId = null;
        }

        @Override
        void onAppFlush(
            FlushFW flush)
        {
            super.onAppFlush(flush);

            final OctetsFW extension = flush.extension();
            if (extension.sizeof() == 0)
            {
                return;
            }

            final McpFlushExFW flushEx = mcpFlushExRO.tryWrap(
                extension.buffer(), extension.offset(), extension.limit());
            if (flushEx == null)
            {
                return;
            }

            if (flushEx.kind() == McpFlushExFW.KIND_ELICIT_RESPONSE)
            {
                if (binding.guard == null)
                {
                    final McpElicitResponseFlushExFW elicitResponse = flushEx.elicitResponse();
                    final String correlationId = elicitResponse.correlationId().asString();
                    final String action = elicitResponse.action().get().name().toLowerCase();
                    new HttpElicitResponse(this, correlationId, action)
                        .doEncodeRequestBegin(flush.traceId(), flush.authorization());
                }
                return;
            }

            if (flushEx.kind() != McpFlushExFW.KIND_ELICIT_CALLBACK)
            {
                return;
            }

            final GuardHandler guard = binding.guard;
            if (guard == null)
            {
                return;
            }

            reauthTraceId = flush.traceId();
            reauthAuthorization = flush.authorization();

            final String callbackUrl = flushEx.elicitCallback().url().asString();
            guard.reauthorize(reauthTraceId, binding.id, initialId, callbackUrl, reauthorizeCompletion);
        }

        private void onReauthorized(
            long sessionId)
        {
            if ((sessionId & GuardHandler.MASK_AUTHORIZED) != 0L)
            {
                final McpFlushExFW flushEx = mcpFlushExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .toolsListChanged(b ->
                    {
                    })
                    .build();
                doAppFlush(reauthTraceId, reauthAuthorization, flushEx);
            }
        }

        @Override
        void onNetBegin(
            BeginFW begin)
        {
            if (remoteSessionId == null)
            {
                final OctetsFW ext = begin.extension();
                final HttpBeginExFW httpBeginEx = httpBeginExRO.tryWrap(ext.buffer(), ext.offset(), ext.limit());
                remoteSessionId = httpBeginEx == null ? sessionId : Optional.ofNullable(httpBeginEx.headers()
                    .matchFirst(h -> HTTP_HEADER_SESSION.equals(h.name().asString())))
                    .map(HttpHeaderFW::value)
                    .map(String16FW::asString)
                    .orElse(sessionId);
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
                final String sid = sessionId;
                final int caps = serverCapabilities;
                doAppBegin(traceId, authorization, mcpBeginExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(mcpTypeId)
                    .lifecycle(b -> b
                        .sessionId(sid)
                        .capabilities(caps))
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

                if (sse != null)
                {
                    sse.doNetAbort(traceId, authorization);
                    sse.doNetReset(traceId, authorization);
                    sse = null;
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
        int contentLength = -1;
        long timeout;
        private HttpEventStream sse;

        JsonParser paramsParser;
        int paramsDepth;

        private boolean pendingAuth;
        private String elicitCorrelationId;
        private String elicitElicitationId;
        private byte[] bufferedBody;
        private int bufferedBodyLength;
        private long elicitTraceId;
        private long elicitAuthorization;
        private long elicitTimeoutId = Signaler.NO_CANCEL_ID;

        private final LongCompletionCallback elicitCompletion = new LongCompletionCallback()
        {
            @Override
            public void completed(
                long contextId,
                long sessionId)
            {
                onElicitCompleted(sessionId);
            }

            @Override
            public void failed(
                long contextId,
                Throwable ex)
            {
                onElicitFailed();
            }
        };

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
                session.sessionId, httpFactory);
            this.session = session;
            this.requestId = session.register(this);
        }

        @Override
        McpBindingConfig binding()
        {
            return session.binding;
        }

        @Override
        boolean proceedWithRequest(
            long traceId,
            long authorization,
            McpBeginExFW mcpBeginEx)
        {
            doAppBegin(traceId, authorization, null);

            final GuardHandler guard = session.binding.guard;
            if (guard == null)
            {
                credentials = session.binding.credentials;
                return true;
            }

            if ((authorization & GuardHandler.MASK_AUTHORIZED) != 0L)
            {
                credentials = guard.credentials(authorization);
                return true;
            }

            final long sessionId = guard.reauthorize(traceId, session.binding.id, initialId, null);

            if ((sessionId & GuardHandler.MASK_AUTHORIZED) != 0L)
            {
                credentials = guard.credentials(sessionId);
                return true;
            }

            if (sessionId == GuardHandler.NEEDS_PREAUTHORIZE && session.binding.credentials == null)
            {
                final String preauthorizeUrl =
                    guard.preauthorize(traceId, session.binding.id, initialId, session.authCallback);
                if (preauthorizeUrl == null)
                {
                    doAppReset(traceId, authorization);
                    doAppAbort(traceId, authorization);
                    return false;
                }

                elicitElicitationId = supplyElicitationId.get();
                elicitCorrelationId = supplyElicitCorrelationId.get();

                final String createElicitationId = elicitElicitationId;
                final String createCorrelationId = elicitCorrelationId;
                final McpChallengeExFW challengeEx = mcpChallengeExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .elicitCreate(b -> b
                        .id(createElicitationId)
                        .url(preauthorizeUrl)
                        .correlationId(createCorrelationId))
                    .build();
                doAppChallenge(traceId, authorization, challengeEx);

                if (timeout > 0L)
                {
                    pendingAuth = true;
                    elicitTraceId = traceId;
                    elicitAuthorization = authorization;

                    elicitTimeoutId = signaler.signalAt(
                        System.currentTimeMillis() + timeout,
                        originId, routedId, replyId,
                        traceId, ELICIT_TIMEOUT_SIGNAL_ID, 0);
                }
                else
                {
                    doAppReset(traceId, authorization);
                    doAppAbort(traceId, authorization);
                }

                return false;
            }

            if (session.binding.credentials != null)
            {
                credentials = session.binding.credentials;
                return true;
            }

            doAppReset(traceId, authorization);
            doAppAbort(traceId, authorization);
            return false;
        }

        @Override
        void onAppData(
            DataFW data)
        {
            super.onAppData(data);

            if (!pendingAuth)
            {
                decodeRequestBody(data);
            }
        }

        @Override
        boolean bufferAppData(
            long traceId,
            long authorization,
            OctetsFW payload)
        {
            if (!pendingAuth)
            {
                return false;
            }

            final int payloadLength = payload.sizeof();
            final int needed = bufferedBodyLength + payloadLength;
            if (bufferedBody == null || bufferedBody.length < needed)
            {
                final int newSize = Math.max(needed,
                    bufferedBody != null ? bufferedBody.length * 2 : 256);
                final byte[] newBuffer = new byte[newSize];
                if (bufferedBody != null)
                {
                    System.arraycopy(bufferedBody, 0, newBuffer, 0, bufferedBodyLength);
                }
                bufferedBody = newBuffer;
            }
            payload.buffer().getBytes(payload.offset(), bufferedBody, bufferedBodyLength, payloadLength);
            bufferedBodyLength += payloadLength;
            return true;
        }

        @Override
        void onAppEnd(
            EndFW end)
        {
            if (pendingAuth)
            {
                final long traceId = end.traceId();
                final long authorization = end.authorization();
                cancelElicitTimeout();
                pendingAuth = false;
                state = McpState.openedInitial(state);
                decoder = decodeRequestEnd;
                doAppReset(traceId, authorization);
                doAppAbort(traceId, authorization);
            }
            super.onAppEnd(end);
        }

        @Override
        void onAppFlush(
            FlushFW flush)
        {
            super.onAppFlush(flush);

            final OctetsFW extension = flush.extension();
            if (extension.sizeof() == 0)
            {
                return;
            }

            final McpFlushExFW flushEx = mcpFlushExRO.tryWrap(
                extension.buffer(), extension.offset(), extension.limit());
            if (flushEx == null)
            {
                return;
            }

            if (flushEx.kind() == McpFlushExFW.KIND_ELICIT_RESPONSE)
            {
                onAppFlushElicitResponse(flush.traceId(), flush.authorization(), flushEx.elicitResponse());
                return;
            }

            if (!pendingAuth || flushEx.kind() != McpFlushExFW.KIND_ELICIT_CALLBACK)
            {
                return;
            }

            final GuardHandler guard = session.binding.guard;
            if (guard == null)
            {
                return;
            }

            final long traceId = flush.traceId();
            elicitTraceId = traceId;
            elicitAuthorization = flush.authorization();

            final String callbackUrl = flushEx.elicitCallback().url().asString();
            guard.reauthorize(traceId, session.binding.id, initialId, callbackUrl, elicitCompletion);
        }

        private void onAppFlushElicitResponse(
            long traceId,
            long authorization,
            McpElicitResponseFlushExFW elicitResponse)
        {
            final McpElicitAction action = elicitResponse.action().get();

            if (session.binding.guard == null)
            {
                final String correlationId = elicitResponse.correlationId().asString();
                new HttpElicitResponse(this, correlationId, action.name().toLowerCase())
                    .doEncodeRequestBegin(traceId, authorization);
            }
            else if (action != McpElicitAction.ACCEPT && pendingAuth)
            {
                cancelElicitTimeout();
                pendingAuth = false;
                state = McpState.openedInitial(state);
                decoder = decodeRequestEnd;
                emitElicitComplete(traceId, authorization);
                doAppAbort(traceId, authorization);
            }
        }

        @Override
        void onAppSignal(
            SignalFW signal)
        {
            if (signal.signalId() == ELICIT_TIMEOUT_SIGNAL_ID && pendingAuth)
            {
                elicitTimeoutId = Signaler.NO_CANCEL_ID;
                final long traceId = signal.traceId();
                final long authorization = signal.authorization();
                pendingAuth = false;
                state = McpState.openedInitial(state);
                decoder = decodeRequestEnd;
                emitElicitComplete(traceId, authorization);
                doAppAbort(traceId, authorization);
                return;
            }

            super.onAppSignal(signal);
        }

        private void onElicitCompleted(
            long sessionId)
        {
            cancelElicitTimeout();
            pendingAuth = false;
            state = McpState.openedInitial(state);
            decoder = decodeRequestEnd;

            if ((sessionId & GuardHandler.MASK_AUTHORIZED) != 0L)
            {
                credentials = session.binding.guard.credentials(sessionId);
                emitElicitComplete(elicitTraceId, elicitAuthorization);

                http.doEncodeRequestBegin(elicitTraceId, elicitAuthorization);
                if (bufferedBodyLength > 0)
                {
                    final UnsafeBuffer body = new UnsafeBuffer(bufferedBody, 0, bufferedBodyLength);
                    http.doEncodeRequestData(elicitTraceId, elicitAuthorization, body, 0, bufferedBodyLength);
                }
                http.doEncodeRequestEnd(elicitTraceId, elicitAuthorization);
            }
            else
            {
                emitElicitComplete(elicitTraceId, elicitAuthorization);
                doAppAbort(elicitTraceId, elicitAuthorization);
            }
        }

        private void onElicitFailed()
        {
            cancelElicitTimeout();
            pendingAuth = false;
            state = McpState.openedInitial(state);
            decoder = decodeRequestEnd;
            doAppAbort(elicitTraceId, elicitAuthorization);
        }

        private void cancelElicitTimeout()
        {
            if (elicitTimeoutId != Signaler.NO_CANCEL_ID)
            {
                signaler.cancel(elicitTimeoutId);
                elicitTimeoutId = Signaler.NO_CANCEL_ID;
            }
        }

        private void emitElicitComplete(
            long traceId,
            long authorization)
        {
            final String elicitationId = elicitElicitationId;
            final McpFlushExFW flushEx = mcpFlushExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId)
                .elicitComplete(b -> b.id(elicitationId))
                .build();
            doAppFlush(traceId, authorization, flushEx);
        }

        @Override
        String transportSessionId()
        {
            return session.transportSessionId();
        }

        @Override
        String protocolVersion()
        {
            return session.protocolVersion();
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
        HttpEventStream sseRef()
        {
            return sse;
        }

        @Override
        void clearSse()
        {
            sse = null;
        }

        @Override
        void onAppChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            final McpChallengeExFW challengeEx = mcpChallengeExRO.tryWrap(
                extension.buffer(), extension.offset(), extension.limit());
            if (challengeEx != null)
            {
                switch (challengeEx.kind())
                {
                case McpChallengeExFW.KIND_RESUME:
                    if (sse == null)
                    {
                        final String16FW resumeId = challengeEx.resume().id();
                        final String suffix = resumeId != null ? resumeId.asString() : null;
                        final String prefixedId = suffix != null && !suffix.isEmpty()
                            ? requestId + ":" + suffix
                            : null;
                        sse = new HttpEventStream(this, prefixedId);
                        sse.doNetStart(traceId, authorization);
                    }
                    break;
                case McpChallengeExFW.KIND_SUSPENDED:
                    if (http != null)
                    {
                        http.doNetReset(traceId, authorization);
                    }
                    if (sse != null)
                    {
                        sse.doNetAbort(traceId, authorization);
                        sse.detach();
                    }
                    break;
                default:
                    break;
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
            if (!McpState.replyOpening(state))
            {
                doAppBegin(begin.traceId(), begin.authorization(), null);
            }
        }

        @Override
        void onNetEnd(
            long traceId,
            long authorization)
        {
            doAppEnd(traceId, authorization);
        }

        @Override
        void onDecodeResumable(
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
        void onDecodeProgress(
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
        void onDecodeSuspend(
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
        void onDecodeSuspended(
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
        void onDecodeElicitCreate(
            long traceId,
            long authorization,
            String elicitationId,
            String url,
            String message,
            String correlationId)
        {
            elicitCorrelationId = correlationId;
            final McpChallengeExFW challengeEx = mcpChallengeExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId)
                .elicitCreate(b ->
                {
                    b.id(elicitationId).url(url);
                    if (message != null)
                    {
                        b.message(message);
                    }
                    if (correlationId != null)
                    {
                        b.correlationId(correlationId);
                    }
                })
                .build();
            doAppChallenge(traceId, authorization, challengeEx);
        }

        @Override
        void onDecodeElicitComplete(
            long traceId,
            long authorization,
            String elicitationId)
        {
            final McpFlushExFW flushEx = mcpFlushExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId)
                .elicitComplete(b -> b.id(elicitationId))
                .build();
            doAppFlush(traceId, authorization, flushEx);

            elicitCorrelationId = null;
        }

        @Override
        void onNetAbort(
            HttpStream http,
            long traceId,
            long authorization)
        {
            if (http.sseMode)
            {
                onDecodeSuspended(traceId, authorization);
            }
            else
            {
                doAppAbort(traceId, authorization);
            }
        }

        @Override
        void onDecodeCompletion(
            long traceId,
            long authorization)
        {
            doAppEnd(traceId, authorization);
        }

        void decodeRequestBody(
            DataFW data)
        {
            if (decoder != null)
            {
                final OctetsFW payload = data.payload();
                if (payload != null && payload.sizeof() > 0)
                {
                    decoder.decode(this, data.traceId(), data.authorization(),
                        0L, 0, payload.buffer(), payload.offset(), payload.offset(), payload.limit());
                }
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

        @Override
        void onAppBeginImpl(
            long traceId,
            long authorization,
            McpBeginExFW mcpBeginEx)
        {
            state = McpState.openedInitial(state);
            http.doEncodeRequestEnd(traceId, authorization);
            decoder = decodeRequestEnd;
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
            this.decoder = decodeJsonRpcParamsBody;
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

        @Override
        void onAppBeginImpl(
            long traceId,
            long authorization,
            McpBeginExFW mcpBeginEx)
        {
            state = McpState.openedInitial(state);
            http.doEncodeRequestEnd(traceId, authorization);
            decoder = decodeRequestEnd;
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
            this.decoder = decodeJsonRpcParamsBody;
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

        @Override
        void onAppBeginImpl(
            long traceId,
            long authorization,
            McpBeginExFW mcpBeginEx)
        {
            state = McpState.openedInitial(state);
            http.doEncodeRequestEnd(traceId, authorization);
            decoder = decodeRequestEnd;
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
            this.decoder = decodeJsonRpcParamsBody;
        }
    }

    private abstract class McpHttpStream
    {
        protected final McpStream mcp;
        protected final long originId;
        protected final long routedId;
        protected final long initialId;
        protected final long replyId;
        protected final long affinity;

        protected MessageConsumer net;

        protected long initialSeq;
        protected long initialAck;
        protected int initialMax;

        protected long replySeq;
        protected long replyAck;
        protected int replyMax;

        protected int decodeSlot = NO_SLOT;
        protected int decodeSlotOffset;
        protected int decodeSlotReserved;

        protected HttpResponseDecoder decoder;
        protected JsonParser decodableJson;
        protected int decodedResultProgress;
        protected int decodedParserProgress;
        protected HttpResponseDecoder decodedSkipObjectThen;
        protected int decodedSkipObjectDepth;
        protected boolean decodedResultErrorKey;
        protected boolean responseError;

        protected boolean sseMode;
        protected String sseEventId;
        protected long sseEventRetry = -1L;
        protected int sseLineState;
        protected byte sseFieldKind;
        protected final StringBuilder sseSmallValue = new StringBuilder();
        protected boolean sseEventHasData;
        protected boolean sseEventProgress;
        protected String sseEventMethod;
        protected String sseEventJsonId;
        protected String sseProgressToken;
        protected long sseProgress;
        protected long sseProgressTotal = -1L;
        protected String sseProgressMessage;
        protected String sseElicitationId;
        protected String sseElicitUrl;
        protected String sseElicitMode;

        protected int state;

        McpHttpStream(
            McpStream mcp)
        {
            this.mcp = mcp;
            this.originId = mcp.routedId;
            this.routedId = mcp.resolvedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = mcp.affinity;
            this.replyMax = decodeMax;
        }

        final void injectAuthorization(
            HttpBeginExFW.Builder builder)
        {
            if (mcp.credentials != null)
            {
                builder.headersItem(h -> h.name(HTTP_HEADER_AUTHORIZATION).value(BEARER_PREFIX + mcp.credentials));
            }
        }

        abstract void onDecodeParseError(
            long traceId,
            long authorization);

        abstract void onDecodeInvalidResponse(
            long traceId,
            long authorization);

        abstract int onDecodeResponseResult(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit);

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
    }

    private abstract class HttpStream extends McpHttpStream
    {
        private int initialPad;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private long encodeSlotTraceId;
        private long encodeSlotAuthorization;

        HttpStream(
            McpStream mcp)
        {
            super(mcp);
            this.decoder = decodeIgnore;
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
            final HttpBeginExFW httpBeginEx = httpBeginExRO.tryWrap(ext.buffer(), ext.offset(), ext.limit());
            String status = null;
            String wwwAuthenticate = null;
            if (httpBeginEx != null)
            {
                status = Optional.ofNullable(httpBeginEx.headers()
                    .matchFirst(h -> HTTP_HEADER_STATUS.equals(h.name().asString())))
                    .map(HttpHeaderFW::value)
                    .map(String16FW::asString)
                    .orElse(null);
                wwwAuthenticate = Optional.ofNullable(httpBeginEx.headers()
                    .matchFirst(h -> HTTP_HEADER_WWW_AUTHENTICATE.equals(h.name().asString())))
                    .map(HttpHeaderFW::value)
                    .map(String16FW::asString)
                    .orElse(null);
                final String contentType = Optional.ofNullable(httpBeginEx.headers()
                    .matchFirst(h -> HTTP_HEADER_CONTENT_TYPE.equals(h.name().asString())))
                    .map(HttpHeaderFW::value)
                    .map(String16FW::asString)
                    .orElse(null);
                if (CONTENT_TYPE_EVENT_STREAM.equals(contentType))
                {
                    decoder = decodeSse;
                    sseMode = true;
                }
            }

            if ((STATUS_401.equals(status) || STATUS_403.equals(status)) &&
                wwwAuthenticate != null &&
                bearerChallengeMatcher.reset(wwwAuthenticate).matches())
            {
                final String realm = bearerChallengeMatcher.group("realm");
                final String scopes = bearerChallengeMatcher.group("scope");
                final String resourceMetadata = bearerChallengeMatcher.group("resourceMetadata");
                final String errorParam = bearerChallengeMatcher.group("error");
                final McpBearerError error = errorParam != null
                    ? McpBearerError.valueOf(errorParam.toUpperCase())
                    : STATUS_403.equals(status) ? McpBearerError.INSUFFICIENT_SCOPE : McpBearerError.INVALID_TOKEN;
                final McpResetExFW mcpResetEx = mcpResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .bearer(b -> b.realm(realm).scopes(scopes).resourceMetadata(resourceMetadata).error(s -> s.set(error)))
                    .build();
                mcp.doAppReset(traceId, authorization, mcpResetEx);
                doNetReset(traceId, authorization);
            }
            else
            {
                mcp.onNetBegin(begin);

                flushNetWindow(traceId, authorization, 0L);
            }
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
                    final DirectBufferInputStreamEx input = responseInputRO;
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
                onResponseComplete(traceId, authorization);
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
                onResponseComplete(traceId, authorization);
                mcp.onNetEnd(traceId, authorization);
            }
        }

        void onResponseComplete(
            long traceId,
            long authorization)
        {
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
            if (net == null)
            {
                return;
            }

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
                traceId, 0L, affinity, httpBeginEx);

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
            if (net == null)
            {
                return;
            }

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
            if (net != null && !McpState.initialClosed(state))
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
            if (net != null && !McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doReset(net, originId, routedId, replyId, traceId, authorization);

                cleanupDecodeSlot();
            }
        }

        @Override
        int onDecodeResponseResult(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            return mcp != null ? mcp.doAppData(traceId, authorization, buffer, offset, limit) : offset;
        }

        @Override
        void onDecodeParseError(
            long traceId,
            long authorization)
        {
            cleanupNet(traceId, authorization);
        }

        @Override
        void onDecodeInvalidResponse(
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
        private final ExpandableArrayBuffer resultBuffer = new ExpandableArrayBuffer();
        private int resultLimit;

        HttpInitializeRequest(
            McpStream mcp)
        {
            super(mcp);
            this.decoder = decodeJsonRpc;
        }

        @Override
        void doEncodeRequestBegin(
            long traceId,
            long authorization)
        {
            int codecLength = 0;
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_INITIALIZE_PREFIX);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, MCP_PROTOCOL_VERSION);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_INITIALIZE_CAPABILITIES_PREFIX);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, capabilitiesJson(mcp.clientCapabilities));
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_INITIALIZE_CLIENT_INFO);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, clientName);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_INITIALIZE_VERSION_PREFIX);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, clientVersion);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_INITIALIZE_SUFFIX);

            final int contentLength = codecLength;
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            mcp.binding().injectHeaders(builder);
            builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(mcp.protocolVersion()))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_LENGTH).value(Integer.toString(contentLength)));
            injectAuthorization(builder);
            final HttpBeginExFW httpBeginEx = builder.build();

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }

        private String capabilitiesJson(
            int capabilities)
        {
            final String json;
            if ((capabilities & CLIENT_ELICITATION_URL.value()) != 0)
            {
                json = JSON_RPC_CAPABILITIES_ELICITATION_URL;
            }
            else if ((capabilities & CLIENT_ELICITATION.value()) != 0)
            {
                json = JSON_RPC_CAPABILITIES_ELICITATION_FORM;
            }
            else
            {
                json = JSON_RPC_CAPABILITIES_NONE;
            }
            return json;
        }

        @Override
        int onDecodeResponseResult(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final int length = limit - offset;
            resultBuffer.putBytes(resultLimit, buffer, offset, length);
            resultLimit += length;
            return limit;
        }

        @Override
        void onResponseComplete(
            long traceId,
            long authorization)
        {
            if (resultLimit == 0)
            {
                return;
            }

            responseInputRO.wrap(resultBuffer, 0, resultLimit);

            int bits = 0;
            try (JsonParser parser = responseParserFactory.createParser(responseInputRO))
            {
                int depth = 0;
                boolean inCapabilities = false;
                String primitive = null;

                while (parser.hasNext())
                {
                    final JsonParser.Event event = parser.next();
                    switch (event)
                    {
                    case START_OBJECT:
                        depth++;
                        break;
                    case END_OBJECT:
                        depth--;
                        if (depth == 2 && primitive != null)
                        {
                            primitive = null;
                        }
                        if (depth == 1 && inCapabilities)
                        {
                            inCapabilities = false;
                        }
                        break;
                    case KEY_NAME:
                        final String key = parser.getString();
                        if (depth == 1 && JSON_KEY_PROTOCOL_VERSION.equals(key))
                        {
                            if (parser.hasNext() && parser.next() == JsonParser.Event.VALUE_STRING &&
                                mcp instanceof McpLifecycleStream lifecycle)
                            {
                                lifecycle.negotiatedVersion = parser.getString();
                            }
                        }
                        else if (depth == 1 && JSON_KEY_CAPABILITIES.equals(key))
                        {
                            inCapabilities = true;
                        }
                        else if (depth == 2 && inCapabilities)
                        {
                            primitive = key;
                        }
                        else if (depth == 3 && primitive != null && JSON_KEY_LIST_CHANGED.equals(key))
                        {
                            if (parser.hasNext() && parser.next() == JsonParser.Event.VALUE_TRUE)
                            {
                                switch (primitive)
                                {
                                case JSON_KEY_TOOLS:
                                    bits |= SERVER_TOOLS_LIST_CHANGED.value();
                                    break;
                                case JSON_KEY_PROMPTS:
                                    bits |= SERVER_PROMPTS_LIST_CHANGED.value();
                                    break;
                                case JSON_KEY_RESOURCES:
                                    bits |= SERVER_RESOURCES_LIST_CHANGED.value();
                                    break;
                                default:
                                    break;
                                }
                            }
                        }
                        break;
                    default:
                        break;
                    }
                }
            }

            mcp.serverCapabilities |= bits;
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
            final String sid = mcp.transportSessionId();

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0, JSON_RPC_NOTIFY_INITIALIZED);

            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            mcp.binding().injectHeaders(builder);
            builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(mcp.protocolVersion()))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_LENGTH).value(Integer.toString(codecLength)));
            injectAuthorization(builder);
            final HttpBeginExFW httpBeginEx = builder.build();

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
            final String sid = mcp.transportSessionId();

            int codecLength = 0;
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_REQUEST_ID_PREFIX);
            codecLength += codecBuffer.putIntAscii(codecLength, ((McpLifecycleStream) mcp).nextRequestId++);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_PING_METHOD);

            final int contentLength = codecLength;
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            mcp.binding().injectHeaders(builder);
            builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(mcp.protocolVersion()))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_LENGTH).value(Integer.toString(contentLength)));
            injectAuthorization(builder);
            final HttpBeginExFW httpBeginEx = builder.build();

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }
    }

    private final class HttpEventStream extends McpHttpStream
    {
        private final String lastEventId;

        HttpEventStream(
            McpStream mcp,
            String lastEventId)
        {
            super(mcp);
            this.decoder = decodeSse;
            this.sseMode = true;
            this.lastEventId = lastEventId;
        }

        void doNetStart(
            long traceId,
            long authorization)
        {
            final String sid = mcp.transportSessionId();
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            mcp.binding().injectHeaders(builder);
            builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_GET))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(mcp.protocolVersion()))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));
            if (lastEventId != null && !lastEventId.isEmpty())
            {
                builder.headersItem(h -> h.name(HTTP_HEADER_LAST_EVENT_ID).value(lastEventId));
            }
            injectAuthorization(builder);
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
            final HttpBeginExFW httpBeginEx = httpBeginExRO.tryWrap(ext.buffer(), ext.offset(), ext.limit());
            final String status = httpBeginEx == null ? null : Optional.ofNullable(httpBeginEx.headers()
                .matchFirst(h -> HTTP_HEADER_STATUS.equals(h.name().asString())))
                .map(HttpHeaderFW::value)
                .map(String16FW::asString)
                .orElse(null);


            if (STATUS_405.equals(status))
            {
                mcp.markEventsUnsupported();
                mcp.onDecodeSuspend(traceId, authorization, SUSPEND_RETRY_NEVER);
                doNetReset(traceId, authorization);
                detach();
            }
            else
            {
                mcp.onDecodeResumable(traceId, authorization, null);
                doNetWindow(traceId, authorization, 0L, 0);
            }
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + data.reserved();

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
                    final DirectBufferInputStreamEx input = responseInputRO;
                    input.wrap(buffer, offset + delta, limit - offset - delta);
                }

                decodeNet(traceId, authorization, budgetId, reserved, buffer, offset, limit);
            }

            doNetWindow(traceId, authorization, budgetId, 0);
        }

        private void decodeNet(
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
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            state = McpState.closedReply(state);
            cleanupDecodeSlot();
            mcp.onDecodeCompletion(traceId, authorization);
            detach();
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = McpState.closedReply(state);
            cleanupDecodeSlot();

            if (mcp.sseRef() == this)
            {
                mcp.onDecodeSuspended(traceId, authorization);
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

        @Override
        void onDecodeParseError(
            long traceId,
            long authorization)
        {
            cleanupNet(traceId, authorization);
        }

        @Override
        void onDecodeInvalidResponse(
            long traceId,
            long authorization)
        {
            cleanupNet(traceId, authorization);
        }

        @Override
        int onDecodeResponseResult(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            return mcp.doAppData(traceId, authorization, buffer, offset, limit);
        }

        private void cleanupNet(
            long traceId,
            long authorization)
        {
            doNetReset(traceId, authorization);
            cleanupDecodeSlot();
            if (mcp.sseRef() == this)
            {
                mcp.onDecodeSuspended(traceId, authorization);
            }
            detach();
        }

        private void detach()
        {
            if (mcp.sseRef() == this)
            {
                mcp.clearSse();
            }
        }
    }

    private abstract class HttpRequestStream extends HttpStream
    {
        protected final McpRequestStream request;
        protected int paramsForwarded;

        HttpRequestStream(
            McpStream mcp)
        {
            super(mcp);
            this.request = (McpRequestStream) mcp;
            this.decoder = decodeJsonRpc;
        }

        @Override
        void doEncodeRequestData(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            paramsForwarded += limit - offset;
            doNetData(traceId, authorization, buffer, offset, limit);
        }

        int streamedContentLength(
            int prefixLength)
        {
            return prefixLength + request.contentLength + JSON_RPC_PARAMS_CLOSE.length();
        }

        void doEncodeStreamedRequestEnd(
            long traceId,
            long authorization)
        {
            int codecLength = codecBuffer.putStringWithoutLengthAscii(0, JSON_RPC_PARAMS_CLOSE);
            final int padding = request.contentLength - paramsForwarded;
            codecBuffer.setMemory(codecLength, padding, (byte) ' ');
            codecLength += padding;
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
            doNetEnd(traceId, authorization);
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
            mcp.binding().injectHeaders(extBuilder);
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(mcp.protocolVersion()));

            final String sid = mcp.transportSessionId();
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            int codecLength = 0;
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_REQUEST_ID_PREFIX);
            codecLength += codecBuffer.putIntAscii(codecLength, request.requestId);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_TOOLS_LIST_METHOD);

            injectAuthorization(extBuilder);

            final int contentLength = codecLength;
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_CONTENT_LENGTH).value(Integer.toString(contentLength)));
            final HttpBeginExFW httpBeginEx = extBuilder.build();

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
            mcp.binding().injectHeaders(extBuilder);
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(mcp.protocolVersion()));

            final String sid = mcp.transportSessionId();
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            injectAuthorization(extBuilder);

            paramsForwarded = 0;
            int codecLength = 0;
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_REQUEST_ID_PREFIX);
            codecLength += codecBuffer.putIntAscii(codecLength, request.requestId);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_TOOLS_CALL_METHOD);

            final int contentLength = streamedContentLength(codecLength);
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_CONTENT_LENGTH).value(Integer.toString(contentLength)));
            final HttpBeginExFW httpBeginEx = extBuilder.build();

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }

        @Override
        void doEncodeRequestEnd(
            long traceId,
            long authorization)
        {
            doEncodeStreamedRequestEnd(traceId, authorization);
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
            mcp.binding().injectHeaders(extBuilder);
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(mcp.protocolVersion()));

            final String sid = mcp.transportSessionId();
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            int codecLength = 0;
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_REQUEST_ID_PREFIX);
            codecLength += codecBuffer.putIntAscii(codecLength, request.requestId);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_PROMPTS_LIST_METHOD);

            injectAuthorization(extBuilder);

            final int contentLength = codecLength;
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_CONTENT_LENGTH).value(Integer.toString(contentLength)));
            final HttpBeginExFW httpBeginEx = extBuilder.build();

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
            mcp.binding().injectHeaders(extBuilder);
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(mcp.protocolVersion()));

            final String sid = mcp.transportSessionId();
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            paramsForwarded = 0;
            int codecLength = 0;
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_REQUEST_ID_PREFIX);
            codecLength += codecBuffer.putIntAscii(codecLength, request.requestId);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_PROMPTS_GET_METHOD);

            injectAuthorization(extBuilder);

            final int contentLength = streamedContentLength(codecLength);
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_CONTENT_LENGTH).value(Integer.toString(contentLength)));
            final HttpBeginExFW httpBeginEx = extBuilder.build();

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }

        @Override
        void doEncodeRequestEnd(
            long traceId,
            long authorization)
        {
            doEncodeStreamedRequestEnd(traceId, authorization);
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
            mcp.binding().injectHeaders(extBuilder);
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(mcp.protocolVersion()));

            final String sid = mcp.transportSessionId();
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            int codecLength = 0;
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_REQUEST_ID_PREFIX);
            codecLength += codecBuffer.putIntAscii(codecLength, request.requestId);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_RESOURCES_LIST_METHOD);

            injectAuthorization(extBuilder);

            final int contentLength = codecLength;
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_CONTENT_LENGTH).value(Integer.toString(contentLength)));
            final HttpBeginExFW httpBeginEx = extBuilder.build();

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
            mcp.binding().injectHeaders(extBuilder);
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(mcp.protocolVersion()));

            final String sid = mcp.transportSessionId();
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            paramsForwarded = 0;
            int codecLength = 0;
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_REQUEST_ID_PREFIX);
            codecLength += codecBuffer.putIntAscii(codecLength, request.requestId);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_RESOURCES_READ_METHOD);

            injectAuthorization(extBuilder);

            final int contentLength = streamedContentLength(codecLength);
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_CONTENT_LENGTH).value(Integer.toString(contentLength)));
            final HttpBeginExFW httpBeginEx = extBuilder.build();

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }

        @Override
        void doEncodeRequestEnd(
            long traceId,
            long authorization)
        {
            doEncodeStreamedRequestEnd(traceId, authorization);
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
            final String sid = mcp.transportSessionId();
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            mcp.binding().injectHeaders(builder);
            final HttpBeginExFW httpBeginEx = builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value("DELETE"))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(mcp.protocolVersion()))
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
        private final String protocolVersion;
        private final int requestId;
        private final McpBindingConfig binding;

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
            this.sessionId = mcp.transportSessionId();
            this.protocolVersion = mcp.protocolVersion();
            this.requestId = mcp.requestId;
            this.originId = mcp.routedId;
            this.routedId = mcp.resolvedId;
            this.affinity = mcp.affinity;
            this.binding = mcp.binding();
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
            binding.injectHeaders(builder);
            final HttpBeginExFW httpBeginEx = builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(protocolVersion))
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
                int codecLength = 0;
                codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_NOTIFY_CANCELLED_PREFIX);
                codecLength += codecBuffer.putIntAscii(codecLength, requestId);
                codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_NOTIFY_CANCELLED_SUFFIX);

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

    private final class HttpElicitResponse
    {
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final String sessionId;
        private final String protocolVersion;
        private final String requestId;
        private final String action;
        private final McpBindingConfig binding;
        private final int contentLength;

        private MessageConsumer net;
        private long authorization;
        private boolean sent;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        HttpElicitResponse(
            McpStream mcp,
            String requestId,
            String action)
        {
            this.initialId = supplyInitialId.applyAsLong(mcp.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.sessionId = mcp.transportSessionId();
            this.protocolVersion = mcp.protocolVersion();
            this.requestId = requestId;
            this.action = action;
            this.originId = mcp.routedId;
            this.routedId = mcp.resolvedId;
            this.affinity = mcp.affinity;
            this.binding = mcp.binding();
            this.contentLength = JSON_RPC_ELICIT_RESPONSE_PREFIX.length() + requestId.length() +
                JSON_RPC_ELICIT_RESPONSE_MIDDLE.length() + action.length() + JSON_RPC_ELICIT_RESPONSE_SUFFIX.length();
        }

        void doEncodeRequestBegin(
            long traceId,
            long authorization)
        {
            this.authorization = authorization;
            state = McpState.openingInitial(state);

            final String sid = sessionId;
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            binding.injectHeaders(builder);
            final HttpBeginExFW httpBeginEx = builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON_AND_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(protocolVersion))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_LENGTH).value(Integer.toString(contentLength)))
                .build();

            net = newStream(this::onNetMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, httpBeginEx);
        }

        private void doNetData(
            long traceId,
            long authorization)
        {
            int codecLength = 0;
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_ELICIT_RESPONSE_PREFIX);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, requestId);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_ELICIT_RESPONSE_MIDDLE);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, action);
            codecLength += codecBuffer.putStringWithoutLengthAscii(codecLength, JSON_RPC_ELICIT_RESPONSE_SUFFIX);

            final int reserved = codecLength;
            doData(net, originId, routedId, initialId,
                initialSeq, initialAck, initialMax,
                traceId, authorization,
                DATA_FLAGS_COMPLETE, 0L, reserved,
                codecBuffer, 0, codecLength);

            initialSeq += reserved;
        }

        private void doNetEnd(
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

        private void doNetReset(
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
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final int padding = window.padding();

            state = McpState.openedInitial(state);
            initialAck = acknowledge;
            initialMax = maximum;

            final int available = initialMax - (int)(initialSeq - initialAck) - padding;
            if (!sent && available >= contentLength)
            {
                sent = true;
                doNetData(traceId, authorization);
                doNetEnd(traceId, authorization);
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            doNetEnd(end.traceId(), authorization);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            doNetReset(abort.traceId(), authorization);
        }

        private void onNetReset(
            ResetFW reset)
        {
            doNetEnd(reset.traceId(), authorization);
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

    private void doEnd(
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
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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

    private void doReset(
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
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
