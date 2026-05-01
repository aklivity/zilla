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
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.CLIENT_ROOTS;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.McpCapabilities.CLIENT_SAMPLING;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;

import java.util.List;
import java.util.Map;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import jakarta.json.bind.JsonbBuilder;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.codec.McpNotifyCanceledParams;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.HttpResetExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpChallengeExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpFlushExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpProgressFlushExFW;
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

public final class McpServerFactory implements McpStreamFactory
{
    private static final int CLIENT_CAPABILITIES =
        CLIENT_ROOTS.value() | CLIENT_SAMPLING.value() | CLIENT_ELICITATION.value();

    private static final String HTTP_TYPE_NAME = "http";
    private static final String MCP_TYPE_NAME = "mcp";

    private static final int INACTIVE_SIGNAL_ID = 1;

    private static final String JSON_RPC_VERSION = "2.0";
    private static final String HTTP_HEADER_METHOD = ":method";
    private static final String HTTP_HEADER_SESSION = "mcp-session-id";
    private static final String HTTP_HEADER_STATUS = ":status";
    private static final String HTTP_HEADER_CONTENT_TYPE = "content-type";
    private static final String HTTP_HEADER_ACCEPT = "accept";
    private static final String HTTP_HEADER_LAST_EVENT_ID = "last-event-id";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String STATUS_200 = "200";
    private static final String STATUS_202 = "202";
    private static final String STATUS_400 = "400";
    private static final String STATUS_405 = "405";
    private static final String STATUS_406 = "406";
    private static final long SUSPEND_RETRY_NEVER = -1L;

    private static final int SSE_KEEPALIVE_SIGNAL_ID = 2;
    private static final byte[] SSE_KEEPALIVE_BYTES = ":\n\n".getBytes();
    private static final byte[] SSE_DATA_PREFIX_BYTES = "data: ".getBytes();
    private static final byte[] SSE_MESSAGE_TERMINATOR_BYTES = "\n\n".getBytes();
    private static final byte[] SSE_ID_PREFIX_BYTES = "id: ".getBytes();
    private static final String LIFECYCLE_STREAM_ID_PREFIX = "";

    private static final byte[] NOTIFICATIONS_TOOLS_LIST_CHANGED_BYTES =
        "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}".getBytes();
    private static final byte[] NOTIFICATIONS_PROMPTS_LIST_CHANGED_BYTES =
        "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/prompts/list_changed\"}".getBytes();
    private static final byte[] NOTIFICATIONS_RESOURCES_LIST_CHANGED_BYTES =
        "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/resources/list_changed\"}".getBytes();

    private static final int DATA_FLAG_FIN = 0x01;
    private static final int DATA_FLAG_INIT = 0x02;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final SignalFW signalRO = new SignalFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final McpFlushExFW mcpFlushExRO = new McpFlushExFW();
    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(), 0, 0);

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final HttpResetExFW.Builder httpResetExRW = new HttpResetExFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();
    private final McpChallengeExFW.Builder mcpChallengeExRW = new McpChallengeExFW.Builder();
    private final McpFlushExFW.Builder mcpFlushExRW = new McpFlushExFW.Builder();

    private final Supplier<String> supplySessionId;
    private final String serverName;
    private final String serverVersion;
    private final long inactivityTimeoutMillis;
    private final long sseKeepaliveIntervalMillis;
    private final Signaler signaler;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer codecBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int mcpTypeId;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final int decodeMax;
    private final int encodeMax;

    private final DirectBufferInputStreamEx inputRO = new DirectBufferInputStreamEx();

    private static final List<String> SERVER_JSON_PATH_INCLUDES = List.of(
        "/jsonrpc",
        "/id",
        "/method",
        "/params/name",
        "/params/uri",
        "/params/_meta/progressToken");
    private final JsonParserFactory parserFactory;

    private final McpServerDecoder decodeJsonRpc = this::decodeJsonRpc;
    private final McpServerDecoder decodeJsonRpcStart = this::decodeJsonRpcStart;
    private final McpServerDecoder decodeJsonRpcNext = this::decodeJsonRpcNext;
    private final McpServerDecoder decodeJsonRpcEnd = this::decodeJsonRpcEnd;
    private final McpServerDecoder decodeJsonRpcVersion = this::decodeJsonRpcVersion;
    private final McpServerDecoder decodeJsonRpcId = this::decodeJsonRpcId;
    private final McpServerDecoder decodeJsonRpcMethod = this::decodeJsonRpcMethod;
    private final McpServerDecoder decodeJsonRpcParamsStart = this::decodeJsonRpcParamsStart;
    private final McpServerDecoder decodeJsonRpcParamsEnd = this::decodeJsonRpcParamsEnd;
    private final McpServerDecoder decodeJsonRpcMethodWithParam = this::decodeJsonRpcMethodWithParam;
    private final McpServerDecoder decodeJsonRpcMethodParamValue = this::decodeJsonRpcMethodParamValue;
    private final McpServerDecoder decodeJsonRpcParamsSkipValue = this::decodeJsonRpcParamsSkipValue;
    private final McpServerDecoder decodeJsonRpcSkipObject = this::decodeJsonRpcSkipObject;
    private final McpServerDecoder decodeJsonRpcMetaStart = this::decodeJsonRpcMetaStart;
    private final McpServerDecoder decodeJsonRpcMetaNext = this::decodeJsonRpcMetaNext;
    private final McpServerDecoder decodeJsonRpcProgressToken = this::decodeJsonRpcProgressToken;
    private final McpServerDecoder decodeIgnore = this::decodeIgnore;

    private final Long2ObjectHashMap<McpBindingConfig> bindings;
    private final Map<String, McpServerFactory.McpLifecycleStream> sessions;

    public McpServerFactory(
        McpConfiguration config,
        EngineContext context)
    {
        this.supplySessionId = config.sessionIdSupplier();
        this.serverName = config.serverName();
        this.serverVersion = config.serverVersion();
        this.inactivityTimeoutMillis = config.inactivityTimeout().toMillis();
        this.sseKeepaliveIntervalMillis = config.sseKeepaliveInterval().toMillis();
        this.signaler = context.signaler();
        this.writeBuffer = context.writeBuffer();
        this.codecBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.decodeMax = decodePool.slotCapacity();
        this.encodeMax = encodePool.slotCapacity();
        this.sessions = new Object2ObjectHashMap<>();
        this.parserFactory = StreamingJson.createParserFactory(Map.of(
            StreamingJson.PATH_INCLUDES, SERVER_JSON_PATH_INCLUDES,
            StreamingJson.TOKEN_MAX_BYTES, decodeMax));
    }

    @Override
    public int originTypeId()
    {
        return httpTypeId;
    }

    @Override
    public int routedTypeId()
    {
        return mcpTypeId;
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
        final long authorization = begin.authorization();

        MessageConsumer newStream = null;

        final McpBindingConfig binding = bindings.get(routedId);
        final McpRouteConfig route = binding != null ? binding.resolve(authorization) : null;

        if (route != null)
        {
            final long resolvedId = route.id;

            final long initialSeq = begin.sequence();
            final long initialAck = begin.acknowledge();
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::wrap);

            McpLifecycleStream session = null;

            final HttpHeaderFW sessionHeader = httpBeginEx.headers()
                .matchFirst(h -> HTTP_HEADER_SESSION.equals(h.name().asString()));

            if (sessionHeader != null)
            {
                String sessionId = sessionHeader.value().asString();
                session = sessions.get(sessionId);
            }

            final HttpHeaderFW methodHeader = httpBeginEx.headers()
                .matchFirst(h -> HTTP_HEADER_METHOD.equals(h.name().asString()));

            String method = methodHeader.value().asString();

            final HttpHeaderFW acceptHeader = httpBeginEx.headers()
                .matchFirst(h -> HTTP_HEADER_ACCEPT.equals(h.name().asString()));
            final String accept = acceptHeader != null ? acceptHeader.value().asString() : null;

            final McpLifecycleStream resolvedSession = session;

            switch (method)
            {
            case "POST":
                if (!acceptRequiresJsonAndEventStream(accept))
                {
                    newStream = new McpRejectHandler(sender, STATUS_406)::onNetBegin;
                }
                else
                {
                    newStream = new McpServer(
                        sender,
                        originId,
                        routedId,
                        initialId,
                        resolvedId,
                        resolvedSession)::onNetMessage;
                }
                break;
            case "GET":
                if (!acceptIncludesEventStream(accept))
                {
                    newStream = new McpRejectHandler(sender, STATUS_406)::onNetBegin;
                }
                else if (resolvedSession == null)
                {
                    newStream = new McpRejectHandler(sender, STATUS_400)::onNetBegin;
                }
                else if (resolvedSession.eventsUnsupported)
                {
                    newStream = new McpRejectHandler(sender, STATUS_405)::onNetBegin;
                }
                else
                {
                    final HttpHeaderFW lastEventIdHeader = httpBeginEx.headers()
                        .matchFirst(h -> HTTP_HEADER_LAST_EVENT_ID.equals(h.name().asString()));
                    String lastEventIdPrefix = null;
                    String lastEventIdSuffix = null;
                    McpRequestStream resolvedRequest = null;
                    if (lastEventIdHeader != null)
                    {
                        final String lastEventId = lastEventIdHeader.value().asString();
                        final int colon = lastEventId != null ? lastEventId.indexOf(':') : -1;
                        if (colon >= 0)
                        {
                            lastEventIdPrefix = lastEventId.substring(0, colon);
                            lastEventIdSuffix = lastEventId.substring(colon + 1);
                            if (!lastEventIdPrefix.isEmpty())
                            {
                                resolvedRequest = resolvedSession.requests.get(lastEventIdPrefix);
                            }
                        }
                    }

                    if (lastEventIdPrefix != null && !lastEventIdPrefix.isEmpty() && resolvedRequest == null)
                    {
                        newStream = new McpRejectHandler(sender, STATUS_400)::onNetBegin;
                    }
                    else
                    {
                        newStream = new McpEventStream(
                            sender,
                            originId,
                            routedId,
                            initialId,
                            resolvedSession,
                            resolvedRequest,
                            lastEventIdSuffix)::onNetMessage;
                    }
                }
                break;
            case "DELETE":
                newStream = new McpShutdownHandler(
                    sender,
                    originId,
                    routedId,
                    initialId,
                    resolvedId,
                    resolvedSession)::onNetMessage;
                break;
            default:
                newStream = new McpRejectHandler(sender, STATUS_405)::onNetBegin;
                break;
            }
        }

        return newStream;
    }

    @FunctionalInterface
    private interface McpServerDecoder
    {
        int decode(
            McpServer server,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int progress,
            int limit);
    }

    @FunctionalInterface
    private interface McpServerRequestParamsConsumer
    {
        int accept(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit);
    }

    private int decodeJsonRpc(
        McpServer server,
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

        server.decodedId = null;
        server.decodedMethod = null;
        server.decodedMethodParam = null;
        server.decodedProgressToken = null;
        server.decodableJson = parserFactory.createParser(input);
        server.decoder = decodeJsonRpcStart;

        progress = limit - input.available();

        return progress;
    }

    private int decodeJsonRpcStart(
        McpServer server,
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
        JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.START_OBJECT)
            {
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }

            server.decoder = decodeJsonRpcNext;

            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcNext(
        McpServer server,
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
        JsonParser parser = server.decodableJson;

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
                    server.decoder = decodeJsonRpcVersion;
                    break;
                case "id":
                    server.decoder = decodeJsonRpcId;
                    break;
                case "method":
                    server.decoder = decodeJsonRpcMethod;
                    break;
                case "params":
                    if (server.decodedMethod == null)
                    {
                        server.onDecodeParseError(traceId, authorization);
                        server.decoder = decodeIgnore;
                        break decode;
                    }
                    server.decoder = decodeJsonRpcParamsStart;
                    break;
                default:
                    server.onDecodeInvalidRequest(traceId, authorization);
                    server.decoder = decodeIgnore;
                    break decode;
                }
                break;
            case JsonParser.Event.END_OBJECT:
                server.decoder = decodeJsonRpcEnd;
                break;
            default:
                server.onDecodeParseError(traceId, authorization);
                break decode;
            }

            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcEnd(
        McpServer server,
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
        JsonParser parser = server.decodableJson;

        decode:
        {
            if (parser.hasNext())
            {
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }

            parser.close();
            server.decoder = decodeIgnore;

            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcVersion(
        McpServer server,
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
        JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.VALUE_STRING ||
                !JSON_RPC_VERSION.equals(parser.getString()))
            {
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }

            server.decoder = decodeJsonRpcNext;

            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcId(
        McpServer server,
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
        JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.VALUE_STRING &&
                event != JsonParser.Event.VALUE_NUMBER)
            {
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }

            final String id = parser.getString();
            server.decodedId = id;
            server.decoder = decodeJsonRpcNext;

            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcMethod(
        McpServer server,
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
        JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.VALUE_STRING)
            {
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }

            final String method = parser.getString();
            if (!method.startsWith("notifications/") && server.decodedId == null)
            {
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }

            if ("initialize".equals(method))
            {
                server.decodedRequest = server::onDecodeInitialize;
            }
            else
            {
                if (server.session == null)
                {
                    server.onDecodeInvalidRequest(traceId, authorization);
                    server.decoder = decodeIgnore;
                    break decode;
                }

                switch (method)
                {
                case "notifications/initialized":
                    server.onDecodeNotifyInitialized(traceId, authorization);
                    break;
                case "ping":
                    server.onDecodePing(traceId, authorization);
                    break;
                case "tools/list":
                    server.onDecodeToolsList(traceId, authorization);
                    server.decodedRequest = server::onDecodeRequestParams;
                    break;
                case "tools/call":
                    server.decodedMethodParam = "name";
                    server.decodedRequest = server::onDecodeRequestParams;
                    break;
                case "prompts/list":
                    server.onDecodePromptsList(traceId, authorization);
                    server.decodedRequest = server::onDecodeRequestParams;
                    break;
                case "prompts/get":
                    server.decodedMethodParam = "name";
                    server.decodedRequest = server::onDecodeRequestParams;
                    break;
                case "resources/list":
                    server.onDecodeResourcesList(traceId, authorization);
                    server.decodedRequest = server::onDecodeRequestParams;
                    break;
                case "resources/read":
                    server.decodedMethodParam = "uri";
                    server.decodedRequest = server::onDecodeRequestParams;
                    break;
                case "notifications/cancelled":
                    server.decodedRequest = server::onDecodeNotifyCancelled;
                    break;
                default:
                    server.onDecodeParseError(traceId, authorization);
                    server.decoder = decodeIgnore;
                    break decode;
                }
            }

            server.decodedMethod = method;
            server.decoder = decodeJsonRpcNext;

            progress = limit - input.available();
        }

        return progress;
    }

    private int decodeJsonRpcParamsStart(
        McpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.START_OBJECT)
            {
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }

            // position of '{' in cumulative stream coordinates is just before the current offset
            server.decodedParamsProgress = (int) parser.getLocation().getStreamOffset() - 1;

            if (server.decodedMethodParam != null)
            {
                server.decoder = decodeJsonRpcMethodWithParam;
            }
            else
            {
                server.decodedSkipObjectDepth = 1;
                server.decodedSkipObjectThen = decodeJsonRpcParamsEnd;
                server.decoder = decodeJsonRpcSkipObject;
            }

            progress = offset + server.decodedParamsProgress - server.decodedParserProgress;
        }

        return progress;
    }

    private int decodeJsonRpcMethodWithParam(
        McpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case JsonParser.Event.KEY_NAME:
                final String key = parser.getString();
                if (server.decodedMethodParam != null && server.decodedMethodParam.equals(key))
                {
                    server.decoder = decodeJsonRpcMethodParamValue;
                }
                else if ("_meta".equals(key))
                {
                    server.decoder = decodeJsonRpcMetaStart;
                }
                else
                {
                    server.decodedSkipObjectThen = decodeJsonRpcMethodWithParam;
                    server.decoder = decodeJsonRpcParamsSkipValue;
                }
                break;
            case JsonParser.Event.END_OBJECT:
                server.decoder = decodeJsonRpcParamsEnd;
                break;
            default:
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }
        }

        progress = offset + server.decodedParamsProgress - server.decodedParserProgress;

        return progress;
    }

    private int decodeJsonRpcParamsSkipValue(
        McpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case JsonParser.Event.START_OBJECT:
                server.decodedSkipObjectDepth = 1;
                server.decoder = decodeJsonRpcSkipObject;
                break decode;
            case JsonParser.Event.VALUE_STRING:
            case JsonParser.Event.VALUE_NUMBER:
            case JsonParser.Event.VALUE_TRUE:
            case JsonParser.Event.VALUE_FALSE:
            case JsonParser.Event.VALUE_NULL:
                server.decoder = server.decodedSkipObjectThen;
                break;
            default:
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }
        }

        progress = offset + server.decodedParamsProgress - server.decodedParserProgress;

        return progress;
    }

    private int decodeJsonRpcMethodParamValue(
        McpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.VALUE_STRING)
            {
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }

            final String value = parser.getString();
            switch (server.decodedMethod)
            {
            case "tools/call":
                server.onDecodeToolsCall(value, traceId, authorization);
                break;
            case "prompts/get":
                server.onDecodePromptsGet(value, traceId, authorization);
                break;
            case "resources/read":
                server.onDecodeResourcesRead(value, traceId, authorization);
                break;
            }

            server.decoder = decodeJsonRpcMethodWithParam;

            progress = offset + server.decodedParamsProgress - server.decodedParserProgress;
        }

        return progress;
    }

    private int decodeJsonRpcMetaStart(
        McpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.START_OBJECT)
            {
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }

            server.decoder = decodeJsonRpcMetaNext;

            progress = offset + server.decodedParamsProgress - server.decodedParserProgress;
        }

        return progress;
    }

    private int decodeJsonRpcMetaNext(
        McpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case JsonParser.Event.KEY_NAME:
                final String key = parser.getString();
                if ("progressToken".equals(key))
                {
                    server.decoder = decodeJsonRpcProgressToken;
                }
                else
                {
                    server.decoder = decodeJsonRpcParamsSkipValue;
                    server.decodedSkipObjectThen = decodeJsonRpcMetaNext;
                }
                break;
            case JsonParser.Event.END_OBJECT:
                server.decoder = decodeJsonRpcMethodWithParam;
                break;
            default:
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }

            progress = offset + server.decodedParamsProgress - server.decodedParserProgress;
        }

        return progress;
    }

    private int decodeJsonRpcProgressToken(
        McpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.VALUE_STRING &&
                event != JsonParser.Event.VALUE_NUMBER)
            {
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }

            server.decodedProgressToken = parser.getString();
            server.decoder = decodeJsonRpcMetaNext;

            progress = offset + server.decodedParamsProgress - server.decodedParserProgress;
        }

        return progress;
    }

    private int decodeJsonRpcParamsEnd(
        McpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        JsonParser parser = server.decodableJson;

        final int decodedParamsProgress = (int) parser.getLocation().getStreamOffset();
        if (decodedParamsProgress > server.decodedParamsProgress)
        {
            final int decodedOffset = offset + server.decodedParamsProgress - server.decodedParserProgress;
            final int decodedLimit = offset + decodedParamsProgress - server.decodedParserProgress;
            final int decodedProgress =
                server.decodedRequest.accept(traceId, authorization, buffer, decodedOffset, decodedLimit);

            server.decodedParamsProgress = decodedProgress - offset + server.decodedParserProgress;
        }

        progress = offset + server.decodedParamsProgress - server.decodedParserProgress;

        if (server.decodedParamsProgress == decodedParamsProgress)
        {
            server.decoder = decodeJsonRpcNext;
        }

        return progress;
    }

    private int decodeJsonRpcSkipObject(
        McpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        JsonParser parser = server.decodableJson;

        while (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.START_OBJECT)
            {
                server.decodedSkipObjectDepth++;
            }
            else if (event == JsonParser.Event.END_OBJECT)
            {
                server.decodedSkipObjectDepth--;
                if (server.decodedSkipObjectDepth == 0)
                {
                    server.decoder = server.decodedSkipObjectThen;
                    break;
                }
            }
        }

        final int decodedParamsProgress = (int) parser.getLocation().getStreamOffset();
        if (decodedParamsProgress > server.decodedParamsProgress)
        {
            final int decodedOffset = offset + server.decodedParamsProgress - server.decodedParserProgress;
            final int decodedLimit = offset + decodedParamsProgress - server.decodedParserProgress;
            final int decodedProgress =
                server.decodedRequest.accept(traceId, authorization, buffer, decodedOffset, decodedLimit);

            server.decodedParamsProgress = decodedProgress - offset + server.decodedParserProgress;
        }

        progress = offset + server.decodedParamsProgress - server.decodedParserProgress;

        return progress;
    }

    private int decodeIgnore(
        McpServer server,
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

    private final class McpServer
    {
        private final MessageConsumer net;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long resolvedId;

        private McpLifecycleStream session;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long replyBud;
        private int replyPad;

        private int state;

        private int decodeSlot = BufferPool.NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;
        private int decodedParserProgress;

        private int encodeSlot = BufferPool.NO_SLOT;
        private int encodeSlotOffset;
        private long encodeSlotTraceId;

        private McpServerDecoder decoder;
        private boolean sseUpgrade;
        private boolean responseStarted;

        private JsonParser decodableJson;
        private String decodedMethod;
        private String decodedMethodParam;
        private String decodedId;
        private String decodedProgressToken;
        private int decodedParamsProgress;
        private McpServerRequestParamsConsumer decodedRequest;
        private McpServerDecoder decodedSkipObjectThen;
        private int decodedSkipObjectDepth;

        private McpRequestStream stream;

        private McpServer(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            McpLifecycleStream session)
        {
            this.net = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.resolvedId = resolvedId;
            this.session = session;
            this.decoder = decodeJsonRpc;
            this.initialMax = decodeMax;
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

            initialSeq = sequence;
            initialAck = acknowledge;

            state = McpState.openedInitial(state);

            doNetWindow(traceId, authorization, 0, 0);
        }

        private void onNetData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + decodeMax)
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
                    slotBuffer.putBytes(decodeSlotOffset, buffer, offset, limit - offset);
                    decodeSlotOffset += limit - offset;
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

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = McpState.closingInitial(state);

            if (decodeSlot == BufferPool.NO_SLOT &&
                stream != null)
            {
                state = McpState.closedInitial(state);
                stream.doAppEnd(traceId, authorization);
            }
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            cleanupDecodeSlot();

            if (stream != null)
            {
                if (sseUpgrade)
                {
                    stream.doAppChallenge(traceId, authorization, suspendedChallengeEx());
                }
                else
                {
                    stream.doAppAbort(traceId, authorization);
                }
            }
        }

        private void onNetFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + decodeMax)
            {
                cleanupNet(traceId, authorization);
            }
            else if (stream != null)
            {
                stream.doAppFlush(traceId, authorization, budgetId, reserved);
            }
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = budgetId;
            replyPad = padding;

            assert replyAck <= replySeq;

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer buffer = encodePool.buffer(encodeSlot);
                final int limit = encodeSlotOffset;

                encodeNet(encodeSlotTraceId, budgetId, buffer, 0, limit);
            }

            if (stream != null)
            {
                stream.flushAppWindow(traceId, authorization, 0L, 0, encodeSlotOffset, encodeMax);
            }
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            if (stream != null)
            {
                if (sseUpgrade)
                {
                    stream.doAppChallenge(traceId, authorization, suspendedChallengeEx());
                }
                else
                {
                    stream.doAppAbort(traceId, authorization);
                }
            }

            cleanupEncodeSlot();
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (!McpState.replyOpening(state))
            {
                doBegin(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization, 0,
                    extension);

                state = McpState.openingReply(state);
            }
        }

        private void doNetData(
            long traceId,
            long authorization,
            DirectBuffer payload)
        {
            doNetData(traceId, authorization, payload, 0, payload.capacity());
        }

        private void doNetData(
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

                buffer = encodeBuffer;
                offset = 0;
                limit = encodeSlotOffset;
            }

            encodeNet(traceId, authorization, buffer, offset, limit);
        }

        private void doNetFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            if (McpState.initialOpened(state))
            {
                doFlush(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization,
                    budgetId, reserved);

                replySeq += reserved;
            }
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doEnd(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization);
            }

            cleanupEncodeSlot();
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doAbort(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization);
            }

            cleanupEncodeSlot();
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            state = McpState.openedInitial(state);

            doWindow(net, originId, routedId, initialId,
                initialSeq, initialAck, decodeMax - decodeSlotReserved, traceId, authorization, budgetId, padding);
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            doNetReset(traceId, authorization, emptyRO);
        }

        private void doNetReset(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doReset(net, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, authorization, extension);
            }
        }

        private void flushNetWindow(
            long traceId,
            long authorization,
            long budgetId)
        {
            final long initialAckMax = Math.max(initialSeq - decodeSlotReserved, initialAck);
            if (initialAckMax > initialAck || !McpState.initialOpened(state))
            {
                initialAck = initialAckMax;
                assert initialAck <= initialSeq;

                doNetWindow(traceId, authorization, budgetId, 0);
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

        private void decodeNet(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            McpServerDecoder previous = null;
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
                    decodeSlot = decodePool.acquire(initialId);
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

            if (McpState.initialClosing(state) &&
                decodeSlot == BufferPool.NO_SLOT &&
                stream != null)
            {
                state = McpState.closedInitial(state);
                stream.doAppEnd(traceId, authorization);
            }
        }

        private void onLifecycleInitialize(
            long traceId,
            long authorization)
        {
            McpLifecycleStream session = new McpLifecycleStream(this);
            sessions.put(session.sessionId, session);

            assert this.session == null;
            this.session = session;
        }

        private void onLifecycleInitialized(
            long traceId,
            long authorization)
        {
            assert session != null;

            McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .lifecycle(i -> i
                    .sessionId(session.sessionId)
                    .capabilities(CLIENT_CAPABILITIES))
                .build();
            session.doAppBegin(traceId, authorization, beginEx);
        }

        private int onDecodeInitialize(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            // DirectBufferInputStreamEx input = new DirectBufferInputStreamEx(buffer, offset, limit - offset);
            // McpInitializeRequestParams params = JsonbBuilder.create().fromJson(input, McpInitializeRequestParams.class);

            onLifecycleInitialize(traceId, authorization);
            doEncodeInitialize(traceId, authorization);

            return limit;
        }

        private void doEncodeInitialize(
            long traceId,
            long authorization)
        {
            doEncodeResponseBegin(traceId, authorization, httpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(STATUS_200))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(session.sessionId))
                .build());
            String8FW payload = new String8FW(("""
                {
                "protocolVersion":"2025-11-25",
                "capabilities":{"prompts":{},"resources":{},"tools":{}},
                "serverInfo":{"name":"%s","version":"%s"}
                }
                """.replaceAll("\n", "")).formatted(serverName, serverVersion));
            doEncodeResponseData(traceId, authorization, payload.value());
            doEncodeResponseEnd(traceId, authorization);
        }

        private void onDecodeNotifyInitialized(
            long traceId,
            long authorization)
        {
            doEncodeNotifyInitialized(traceId, authorization);
            onLifecycleInitialized(traceId, authorization);
        }

        private void doEncodeNotifyInitialized(
            long traceId,
            long authorization)
        {
            doNetBegin(traceId, authorization,
                httpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(httpTypeId)
                    .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(STATUS_200))
                    .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                    .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(session.sessionId))
                    .build());
            doNetEnd(traceId, authorization);
        }

        private void onDecodePing(
            long traceId,
            long authorization)
        {
            session.touch();

            doEncodeResponseBegin(traceId, authorization,
                httpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(httpTypeId)
                    .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(STATUS_200))
                    .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                    .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(session.sessionId))
                    .build());
            String8FW payload = new String8FW("{}");
            doEncodeResponseData(traceId, authorization, payload.value());
            doEncodeResponseEnd(traceId, authorization);
        }

        private void onDecodeToolsList(
            long traceId,
            long authorization)
        {
            McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .toolsList(t -> t
                    .sessionId(session.sessionId))
                .build();

            assert stream == null;
            stream = new McpRequestStream(session, decodedId, this);
            stream.doAppBegin(traceId, authorization, beginEx);
        }

        private void onDecodeToolsCall(
            String name,
            long traceId,
            long authorization)
        {
            McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .toolsCall(t -> t
                    .sessionId(session.sessionId)
                    .name(name))
                .build();

            assert stream == null;
            stream = new McpRequestStream(session, decodedId, this);
            stream.doAppBegin(traceId, authorization, beginEx);
        }

        private void onDecodePromptsList(
            long traceId,
            long authorization)
        {
            McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .promptsList(p -> p
                    .sessionId(session.sessionId))
                .build();

            assert stream == null;
            stream = new McpRequestStream(session, decodedId, this);
            stream.doAppBegin(traceId, authorization, beginEx);
        }

        private void onDecodePromptsGet(
            String name,
            long traceId,
            long authorization)
        {
            McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .promptsGet(p -> p
                    .sessionId(session.sessionId)
                    .name(name))
                .build();

            assert stream == null;
            stream = new McpRequestStream(session, decodedId, this);
            stream.doAppBegin(traceId, authorization, beginEx);
        }

        private void onDecodeResourcesList(
            long traceId,
            long authorization)
        {
            McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .resourcesList(r -> r
                    .sessionId(session.sessionId))
                .build();

            assert stream == null;
            stream = new McpRequestStream(session, decodedId, this);
            stream.doAppBegin(traceId, authorization, beginEx);
        }

        private void onDecodeResourcesRead(
            String uri,
            long traceId,
            long authorization)
        {
            McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .resourcesRead(r -> r
                    .sessionId(session.sessionId)
                    .uri(uri))
                .build();

            assert stream == null;
            stream = new McpRequestStream(session, decodedId, this);
            stream.doAppBegin(traceId, authorization, beginEx);
        }

        private int onDecodeRequestParams(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            return stream != null ? stream.doAppData(traceId, authorization, buffer, offset, limit) : offset;
        }

        private int onDecodeNotifyCancelled(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            DirectBufferInputStreamEx input = new DirectBufferInputStreamEx();
            input.wrap(buffer, offset, limit - offset);
            McpNotifyCanceledParams params = JsonbBuilder.create().fromJson(input, McpNotifyCanceledParams.class);

            assert session != null;

            McpRequestStream request = session.requests.get(params.requestId.toString());
            request.doAppCancel(traceId, authorization);

            doEncodeNotifyCanceled(traceId, authorization);

            return limit;
        }

        private void doEncodeNotifyCanceled(
            long traceId,
            long authorization)
        {
            doNetBegin(traceId, authorization,
                httpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(httpTypeId)
                    .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(STATUS_202))
                    .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                    .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(session.sessionId))
                    .build());
            doNetEnd(traceId, authorization);
        }

        private void onDecodeParseError(
            long traceId,
            long authorization)
        {
            doEncodeResponseError(traceId, authorization,
                httpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(httpTypeId)
                    .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(STATUS_400))
                    .build(),
                -32700,
                "Parse error");
        }

        private void onDecodeInvalidRequest(
            long traceId,
            long authorization)
        {
            doEncodeResponseError(traceId, authorization,
                httpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(httpTypeId)
                    .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(STATUS_400))
                    .build(),
                -32600,
                "Invalid request");
        }

        private void onAppChallenge(
            long traceId,
            long authorization)
        {
            sseUpgrade = true;
            doNetBegin(traceId, authorization, httpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(STATUS_200))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(session.sessionId))
                .build());
        }

        private void doEncodeResponseBegin(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            doNetBegin(traceId, authorization, extension);
        }

        private void doEncodeRequestEvent(
            long traceId,
            long authorization,
            String streamIdPrefix,
            McpFlushExFW flushEx)
        {
            final int length;
            switch (flushEx.kind())
            {
            case McpFlushExFW.KIND_RESUMABLE:
                length = encodeSseNotifyEvent(codecBuffer, 0, streamIdPrefix,
                    flushEx.resumable().id(), null);
                break;
            case McpFlushExFW.KIND_PROGRESS:
                length = encodeSseProgressEvent(codecBuffer, 0, streamIdPrefix, flushEx.progress());
                break;
            case McpFlushExFW.KIND_SUSPEND:
                final long requestRetry = flushEx.suspend().retry();
                length = requestRetry > 0
                    ? encodeSseRetry(codecBuffer, 0, requestRetry)
                    : 0;
                break;
            default:
                length = 0;
                break;
            }

            if (length > 0)
            {
                doNetData(traceId, authorization, codecBuffer, 0, length);
            }
        }

        private void doEncodeResponsePreamble(
            long traceId,
            long authorization)
        {
            if (!responseStarted)
            {
                responseStarted = true;
                final String prefix = sseUpgrade ? "data: " : "";
                final int codecLimit = codecBuffer.putStringWithoutLengthAscii(0,
                    "%s{\"jsonrpc\":\"2.0\",\"id\":%s,\"result\":".formatted(prefix, decodedId));
                doNetData(traceId, authorization, codecBuffer, 0, codecLimit);
            }
        }

        private void doEncodeResponseData(
            long traceId,
            long authorization,
            DirectBuffer payload)
        {
            doEncodeResponsePreamble(traceId, authorization);
            if (sseUpgrade)
            {
                final int length = rewriteSseDataLines(codecBuffer, 0, payload, 0, payload.capacity());
                doNetData(traceId, authorization, codecBuffer, 0, length);
            }
            else
            {
                doNetData(traceId, authorization, payload);
            }
        }

        private void doEncodeResponsePostamble(
            long traceId,
            long authorization)
        {
            final String suffix = sseUpgrade ? "}\n\n" : "}";
            final int codecLimit = codecBuffer.putStringWithoutLengthAscii(0, suffix);
            doNetData(traceId, authorization, codecBuffer, 0, codecLimit);
        }

        private void doEncodeResponseEnd(
            long traceId,
            long authorization)
        {
            if (responseStarted)
            {
                doEncodeResponsePostamble(traceId, authorization);
            }

            state = McpState.closingReply(state);

            if (encodeSlot == BufferPool.NO_SLOT)
            {
                doNetEnd(traceId, authorization);
            }
        }

        private void doEncodeResponseError(
            long traceId,
            long authorization,
            Flyweight extension,
            int code,
            String message)
        {
            doNetBegin(traceId, authorization, extension);

            final int codecLimit = codecBuffer.putStringWithoutLengthAscii(0,
                """
                {"jsonrpc":"2.0","id":%s,"error":{"code":%d,"message":"%s"}
                """.formatted(decodedId, code, message));
            doNetData(traceId, authorization, codecBuffer, 0, codecLimit);
            doNetEnd(traceId, authorization);
        }

        private void encodeNet(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int replyWin = replyMax - (int)(replySeq - replyAck);
            final int length = Math.max(Math.min(replyWin - replyPad, maxLength), 0);

            if (length > 0)
            {
                final int reserved = length + replyPad;

                assert replyBud == 0L;

                doData(net, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                       0x03, replyBud, reserved, buffer, offset, length);

                replySeq += reserved;

                assert replySeq <= replyAck + replyMax :
                    String.format("%d <= %d + %d", replySeq, replyAck, replyMax);
            }

            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(replyId);
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
                }
            }
            else
            {
                cleanupEncodeSlot();

                if (McpState.replyClosing(state))
                {
                    doNetEnd(traceId, authorization);
                }
            }

            if (stream != null)
            {
                stream.flushAppWindow(traceId, authorization, 0L, 0, encodeSlotOffset, encodeMax);
            }
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != BufferPool.NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = BufferPool.NO_SLOT;
                decodeSlotOffset = 0;
                decodeSlotReserved = 0;
            }
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != BufferPool.NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = BufferPool.NO_SLOT;
                encodeSlotOffset = 0;
                encodeSlotTraceId = 0;
            }
        }

        private void cleanupNet(
            long traceId,
            long authorization)
        {
            doNetReset(traceId, authorization);
            doNetAbort(traceId, authorization);
        }
    }

    private final class McpShutdownHandler
    {
        private final MessageConsumer net;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private McpLifecycleStream session;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private McpShutdownHandler(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            McpLifecycleStream session)
        {
            this.net = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.session = session;
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
            final int maximum = begin.maximum();

            initialSeq = sequence;
            initialAck = acknowledge;
            initialMax = maximum;

            state = McpState.openedInitial(state);
            doNetWindow(traceId, authorization, 0, 0);

            session.doAppEnd(traceId, authorization);

            doNetBegin(traceId, authorization, httpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(STATUS_200))
                .build());
        }

        private void onNetData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + decodeMax)
            {
                cleanupNet(traceId, authorization);
            }
            else
            {
                initialAck = initialSeq;
                doNetWindow(traceId, authorization, budgetId, 0);
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = McpState.closingInitial(state);

            doNetEnd(traceId, authorization);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            doNetAbort(traceId, authorization);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            doNetReset(traceId, authorization);
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (!McpState.replyOpening(state))
            {
                doBegin(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization, 0,
                    extension);

                state = McpState.openingReply(state);
            }
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doEnd(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization);
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doAbort(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization);
            }
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            state = McpState.openedInitial(state);

            doWindow(net, originId, routedId, initialId,
                initialSeq, initialAck, decodeMax, traceId, authorization, budgetId, padding);
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doReset(net, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, authorization, emptyRO);
            }
        }

        private void cleanupNet(
            long traceId,
            long authorization)
        {
            doNetReset(traceId, authorization);
            doNetAbort(traceId, authorization);
        }
    }

    private final class McpRejectHandler
    {
        private final MessageConsumer net;
        private final String status;

        private McpRejectHandler(
            MessageConsumer sender,
            String status)
        {
            this.net = sender;
            this.status = status;
        }

        private void onNetBegin(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            if (msgTypeId == BeginFW.TYPE_ID)
            {
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                doReset(net, begin.originId(), begin.routedId(), begin.streamId(),
                    begin.sequence(), begin.acknowledge(), 0,
                    begin.traceId(), begin.authorization(), httpResetExRW
                        .wrap(codecBuffer, 0, codecBuffer.capacity())
                        .typeId(httpTypeId)
                        .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(status))
                        .build());
            }
        }
    }

    private final class McpLifecycleStream
    {
        private final String sessionId;
        private final Object2ObjectHashMap<String, McpRequestStream> requests;

        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private MessageConsumer app;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long replySeq;
        private long replyAck;
        private int replyMax;

        private long lastActiveAt;
        private long inactiveId = Signaler.NO_CANCEL_ID;
        private McpEventStream eventStream;
        private boolean eventsUnsupported;

        private McpLifecycleStream(
            McpServer server)
        {
            this.sessionId = supplySessionId.get();
            this.requests = new Object2ObjectHashMap<>();
            this.originId = server.routedId;
            this.routedId = server.resolvedId;
            this.initialId = supplyInitialId.applyAsLong(server.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            touch();

            app = newStream(this::onAppMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, authorization, 0,
                extension);

            state = McpState.openingInitial(state);

            scheduleInactivity(traceId);
        }

        private void touch()
        {
            lastActiveAt = System.currentTimeMillis();
        }

        private void scheduleInactivity(
            long traceId)
        {
            final long at = lastActiveAt + inactivityTimeoutMillis;
            inactiveId = signaler.signalAt(at, originId, routedId, initialId,
                traceId, INACTIVE_SIGNAL_ID, 0);
        }

        private void cancelInactivity()
        {
            if (inactiveId != Signaler.NO_CANCEL_ID)
            {
                signaler.cancel(inactiveId);
                inactiveId = Signaler.NO_CANCEL_ID;
            }
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doEnd(app, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        private void doAppResume(
            long traceId,
            long authorization)
        {
            final McpChallengeExFW resumeEx = mcpChallengeExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .resume(b ->
                {
                })
                .build();
            doAppChallenge(traceId, authorization, resumeEx);
        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (McpState.initialOpened(state) &&
                !McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doAbort(app, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        private void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            state = McpState.openedReply(state);
            doWindow(app, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding);
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doReset(app, originId, routedId, replyId,
                    replySeq, replyAck, replyMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void doAppChallenge(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            doChallenge(app, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, extension);
        }

        private void onAppMessage(
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
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onAppSignal(signal);
                break;
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;

            assert replyAck <= replySeq;

            touch();

            doAppWindow(traceId, authorization, 0L, 0);
        }

        private void onAppSignal(
            SignalFW signal)
        {
            if (signal.signalId() != INACTIVE_SIGNAL_ID)
            {
                return;
            }

            final long traceId = signal.traceId();
            final long authorization = signal.authorization();
            final long now = System.currentTimeMillis();
            final long shutdownAt = lastActiveAt + inactivityTimeoutMillis;

            if (shutdownAt <= now)
            {
                inactiveId = Signaler.NO_CANCEL_ID;
                requests.values().stream()
                    .forEach(r -> r.doAppCancel(traceId, authorization));
                requests.clear();
                if (eventStream != null)
                {
                    eventStream.doNetEnd(traceId, authorization);
                    eventStream = null;
                }
                doAppEnd(traceId, authorization);
                sessions.remove(sessionId);
            }
            else
            {
                inactiveId = signaler.signalAt(shutdownAt, originId, routedId, initialId,
                    traceId, INACTIVE_SIGNAL_ID, 0);
            }
        }

        private void onAppFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + decodeMax)
            {
                cleanupApp(traceId, authorization);
            }
            else if (eventStream != null && extension.sizeof() > 0)
            {
                final McpFlushExFW flushEx =
                    mcpFlushExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
                if (flushEx != null)
                {
                    onAppFlushEx(flushEx, traceId, authorization);
                }
            }
        }

        private void onAppFlushEx(
            McpFlushExFW flushEx,
            long traceId,
            long authorization)
        {
            switch (flushEx.kind())
            {
            case McpFlushExFW.KIND_RESUMABLE:
                if (eventStream != null)
                {
                    if (!McpState.replyOpening(eventStream.state))
                    {
                        eventStream.doNetBeginAccepted(traceId, authorization);
                    }
                    final String16FW resumableId = flushEx.resumable().id();
                    if (resumableId != null && resumableId.length() != -1)
                    {
                        eventStream.doEncodeNotifyEvent(traceId, authorization, LIFECYCLE_STREAM_ID_PREFIX,
                            resumableId, null);
                    }
                }
                break;
            case McpFlushExFW.KIND_TOOLS_LIST_CHANGED:
                eventStream.doEncodeNotifyEvent(traceId, authorization, LIFECYCLE_STREAM_ID_PREFIX,
                    flushEx.toolsListChanged().id(), NOTIFICATIONS_TOOLS_LIST_CHANGED_BYTES);
                break;
            case McpFlushExFW.KIND_PROMPTS_LIST_CHANGED:
                eventStream.doEncodeNotifyEvent(traceId, authorization, LIFECYCLE_STREAM_ID_PREFIX,
                    flushEx.promptsListChanged().id(), NOTIFICATIONS_PROMPTS_LIST_CHANGED_BYTES);
                break;
            case McpFlushExFW.KIND_RESOURCES_LIST_CHANGED:
                eventStream.doEncodeNotifyEvent(traceId, authorization, LIFECYCLE_STREAM_ID_PREFIX,
                    flushEx.resourcesListChanged().id(), NOTIFICATIONS_RESOURCES_LIST_CHANGED_BYTES);
                break;
            case McpFlushExFW.KIND_SUSPEND:
                final long suspendRetry = flushEx.suspend().retry();
                if (suspendRetry == SUSPEND_RETRY_NEVER)
                {
                    eventsUnsupported = true;
                }
                if (eventStream != null)
                {
                    if (!McpState.replyOpening(eventStream.state))
                    {
                        if (suspendRetry == SUSPEND_RETRY_NEVER)
                        {
                            eventStream.doNetBeginRejected(traceId, authorization, STATUS_405);
                        }
                        else
                        {
                            eventStream.doNetBeginAccepted(traceId, authorization);
                            if (suspendRetry > 0)
                            {
                                eventStream.doEncodeRetryEvent(traceId, authorization, suspendRetry);
                            }
                            eventStream.doNetEnd(traceId, authorization);
                        }
                    }
                    else
                    {
                        if (suspendRetry > 0)
                        {
                            eventStream.doEncodeRetryEvent(traceId, authorization, suspendRetry);
                        }
                        eventStream.doNetEnd(traceId, authorization);
                    }
                }
                break;
            default:
                break;
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = McpState.closedReply(state);

            doAppEnd(traceId, authorization);

            onAppClosed(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = McpState.closedReply(state);

            doAppAbort(traceId, authorization);

            onAppClosed(traceId, authorization);
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();

            initialSeq = sequence;
            initialAck = acknowledge;
            initialMax = maximum;

            state = McpState.openedInitial(state);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = McpState.closedInitial(state);

            doAppReset(traceId, authorization);

            onAppClosed(traceId, authorization);
        }

        private void cleanupApp(
            long traceId,
            long authorization)
        {
            doAppReset(traceId, authorization);
            doAppAbort(traceId, authorization);
        }

        private void onAppClosed(
            long traceId,
            long authorization)
        {
            if (McpState.closed(state))
            {
                requests.values().stream()
                    .forEach(r -> r.doAppCancel(traceId, authorization));
                requests.clear();
                if (eventStream != null)
                {
                    eventStream.doNetEnd(traceId, authorization);
                    eventStream = null;
                }
                cancelInactivity();
                sessions.remove(sessionId);
            }
        }
    }

    private final class McpEventStream
    {
        private final MessageConsumer net;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final McpLifecycleStream session;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long replyBud;
        private int replyPad;

        private int state;

        private int encodeSlot = BufferPool.NO_SLOT;
        private int encodeSlotOffset;
        private long encodeSlotTraceId;

        private boolean endPending;

        private final McpRequestStream request;
        private final String lastEventId;

        private long keepaliveCancelId = Signaler.NO_CANCEL_ID;

        private McpEventStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            McpLifecycleStream session,
            McpRequestStream request,
            String lastEventId)
        {
            this.net = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.session = session;
            this.request = request;
            this.lastEventId = lastEventId;
            this.initialMax = decodeMax;
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
                onNetClientEnd(end);
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
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onNetSignal(signal);
                break;
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            initialSeq = sequence;
            initialAck = acknowledge;

            state = McpState.openedInitial(state);

            if (request != null)
            {
                if (request.eventStream != null)
                {
                    request.eventStream.doNetEnd(traceId, authorization);
                }
                request.eventStream = this;
                request.doAppResume(traceId, authorization, lastEventId);
            }
            else
            {
                if (session.eventStream != null)
                {
                    session.eventStream.doNetEnd(traceId, authorization);
                }
                session.eventStream = this;
                session.doAppResume(traceId, authorization);
            }
        }

        private void doNetBeginAccepted(
            long traceId,
            long authorization)
        {
            doNetBegin(traceId, authorization, httpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(STATUS_200))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_EVENT_STREAM))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(session.sessionId))
                .build());

            doNetWindow(traceId, authorization, 0, 0);

            scheduleKeepalive(traceId);
        }

        private void doNetBeginRejected(
            long traceId,
            long authorization,
            String status)
        {
            doNetBegin(traceId, authorization, httpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(status))
                .build());

            doNetEnd(traceId, authorization);
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            cleanupNet(traceId, authorization);
        }

        private void onNetClientEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = McpState.closedInitial(state);

            doNetEnd(traceId, authorization);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = McpState.closedInitial(state);

            notifySuspendedToSession(traceId, authorization);

            doNetAbort(traceId, authorization);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = budgetId;
            replyPad = padding;

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer buffer = encodePool.buffer(encodeSlot);
                final int limit = encodeSlotOffset;
                encodeNet(encodeSlotTraceId, authorization, buffer, 0, limit);
            }

            if (endPending && encodeSlot == NO_SLOT)
            {
                endPending = false;
                doNetEnd(traceId, authorization);
            }
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            notifySuspendedToSession(traceId, authorization);

            cleanupNet(traceId, authorization);
        }

        private void notifySuspendedToSession(
            long traceId,
            long authorization)
        {
            if (request != null)
            {
                if (request.eventStream == this)
                {
                    request.eventStream = null;
                    request.doAppChallenge(traceId, authorization, suspendedChallengeEx());
                }
            }
            else if (session.eventStream == this)
            {
                session.eventStream = null;
                session.doAppChallenge(traceId, authorization, suspendedChallengeEx());
            }
        }

        private void onNetSignal(
            SignalFW signal)
        {
            if (signal.signalId() != SSE_KEEPALIVE_SIGNAL_ID)
            {
                return;
            }

            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            keepaliveCancelId = Signaler.NO_CANCEL_ID;

            if (!McpState.replyClosed(state))
            {
                final int length = encodeSseKeepAlive(codecBuffer, 0);
                doNetData(traceId, authorization, codecBuffer, 0, length);
                scheduleKeepalive(traceId);
            }
        }

        private void doEncodeEventData(
            long traceId,
            long authorization,
            DirectBuffer payload,
            int payloadOffset,
            int payloadLength,
            int flags)
        {
            if (McpState.replyClosed(state))
            {
                return;
            }

            final int length = encodeSseEvent(codecBuffer, 0, payload, payloadOffset, payloadLength, flags);
            doNetData(traceId, authorization, codecBuffer, 0, length);
        }

        private void doEncodeNotifyEvent(
            long traceId,
            long authorization,
            String streamIdPrefix,
            String16FW id,
            byte[] body)
        {
            if (McpState.replyClosed(state))
            {
                return;
            }

            final int length = encodeSseNotifyEvent(codecBuffer, 0, streamIdPrefix, id, body);
            doNetData(traceId, authorization, codecBuffer, 0, length);
        }

        private void doEncodeProgressEvent(
            long traceId,
            long authorization,
            String streamIdPrefix,
            McpProgressFlushExFW progress)
        {
            if (McpState.replyClosed(state))
            {
                return;
            }

            final int length = encodeSseProgressEvent(codecBuffer, 0, streamIdPrefix, progress);
            doNetData(traceId, authorization, codecBuffer, 0, length);
        }

        private void doEncodeResponseSseData(
            long traceId,
            long authorization,
            String requestId,
            DirectBuffer payload)
        {
            if (McpState.replyClosed(state))
            {
                return;
            }

            int progress0 = 0;
            progress0 += codecBuffer.putStringWithoutLengthAscii(progress0,
                "data: {\"jsonrpc\":\"2.0\",\"id\":%s,\"result\":".formatted(requestId));
            progress0 += rewriteSseDataLines(codecBuffer, progress0, payload, 0, payload.capacity());
            doNetData(traceId, authorization, codecBuffer, 0, progress0);
        }

        private void doEncodeResponseSsePostamble(
            long traceId,
            long authorization)
        {
            if (McpState.replyClosed(state))
            {
                return;
            }

            int progress0 = 0;
            progress0 += codecBuffer.putStringWithoutLengthAscii(progress0, "}\n\n");
            doNetData(traceId, authorization, codecBuffer, 0, progress0);
        }

        private void doEncodeRetryEvent(
            long traceId,
            long authorization,
            long retry)
        {
            if (McpState.replyClosed(state))
            {
                return;
            }

            final int length = encodeSseRetry(codecBuffer, 0, retry);
            doNetData(traceId, authorization, codecBuffer, 0, length);
        }

        private void scheduleKeepalive(
            long traceId)
        {
            if (sseKeepaliveIntervalMillis > 0L && keepaliveCancelId == Signaler.NO_CANCEL_ID)
            {
                final long at = System.currentTimeMillis() + sseKeepaliveIntervalMillis;
                keepaliveCancelId = signaler.signalAt(at, originId, routedId, replyId,
                    traceId, SSE_KEEPALIVE_SIGNAL_ID, 0);
            }
        }

        private void cancelKeepalive()
        {
            if (keepaliveCancelId != Signaler.NO_CANCEL_ID)
            {
                signaler.cancel(keepaliveCancelId);
                keepaliveCancelId = Signaler.NO_CANCEL_ID;
            }
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (!McpState.replyOpening(state))
            {
                doBegin(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization, 0,
                    extension);
                state = McpState.openingReply(state);
            }
        }

        private void doNetData(
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

                buffer = encodeBuffer;
                offset = 0;
                limit = encodeSlotOffset;
            }

            encodeNet(traceId, authorization, buffer, offset, limit);
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (encodeSlot != NO_SLOT)
            {
                endPending = true;
                return;
            }

            cancelKeepalive();
            if (session.eventStream == this)
            {
                session.eventStream = null;
            }

            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doEnd(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization);
            }

            cleanupEncodeSlot();
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            cancelKeepalive();
            if (session.eventStream == this)
            {
                session.eventStream = null;
            }

            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doAbort(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization);
            }

            cleanupEncodeSlot();
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doReset(net, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, authorization, emptyRO);
            }
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            doWindow(net, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, authorization, budgetId, padding);
        }

        private void encodeNet(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int replyWin = replyMax - (int)(replySeq - replyAck);
            final int length = Math.max(Math.min(replyWin - replyPad, maxLength), 0);

            if (length > 0)
            {
                final int reserved = length + replyPad;

                doData(net, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                       0x03, replyBud, reserved, buffer, offset, length);

                replySeq += reserved;
                assert replySeq <= replyAck + replyMax;
            }

            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(replyId);
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
                }
            }
            else
            {
                cleanupEncodeSlot();
            }
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != BufferPool.NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = BufferPool.NO_SLOT;
                encodeSlotOffset = 0;
                encodeSlotTraceId = 0;
            }
        }

        private void cleanupNet(
            long traceId,
            long authorization)
        {
            doNetReset(traceId, authorization);
            doNetAbort(traceId, authorization);
        }
    }

    private final class McpRequestStream
    {
        private final McpLifecycleStream session;
        private final String requestId;
        private final McpServer server;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private MessageConsumer app;

        McpEventStream eventStream;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private long initialBud;
        private long replySeq;
        private long replyAck;
        private int replyMax;

        private McpRequestStream(
            McpLifecycleStream session,
            String requestId,
            McpServer server)
        {
            this.session = session;
            this.requestId = requestId;
            this.server = server;
            this.originId = server.routedId;
            this.routedId = server.resolvedId;
            this.initialId = supplyInitialId.applyAsLong(server.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            session.touch();

            app = newStream(this::onAppMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, authorization, 0,
                extension);

            state = McpState.openingInitial(state);

            session.requests.put(requestId, this);
        }

        private int doAppData(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            int initialNoAck = (int)(initialSeq - initialAck);
            int length = Math.min(Math.max(initialMax - initialNoAck - initialPad, 0), limit - offset);

            if (length > 0)
            {
                final int reserved = length + initialPad;

                session.touch();

                doData(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, 0x03, initialBud, reserved, buffer, offset, length);

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;
            }

            return offset + length;
        }

        private void doAppFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            if (McpState.initialOpened(state))
            {
                doFlush(app, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, authorization,
                    budgetId, reserved);
            }
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doEnd(app, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);

                if (McpState.replyClosed(state))
                {
                    session.requests.remove(requestId);
                }
            }
        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (McpState.initialOpened(state) &&
                !McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doAbort(app, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);

                if (McpState.replyClosed(state))
                {
                    session.requests.remove(requestId);
                }
            }
        }

        private void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            state = McpState.openedReply(state);
            doWindow(app, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding);
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doReset(app, originId, routedId, replyId,
                    replySeq, replyAck, replyMax,
                    traceId, authorization, emptyRO);

                if (McpState.initialClosed(state))
                {
                    session.requests.remove(requestId);
                }
            }
        }

        private void doAppCancel(
            long traceId,
            long authorization)
        {
            server.doNetBegin(traceId, authorization, emptyRO);
            server.doNetAbort(traceId, authorization);
            doAppReset(traceId, authorization);
        }

        void doAppChallenge(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            doChallenge(app, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, extension);
        }

        void doAppResume(
            long traceId,
            long authorization,
            String lastEventId)
        {
            final McpChallengeExFW resumeEx = mcpChallengeExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .resume(b ->
                {
                    if (lastEventId != null)
                    {
                        b.id(lastEventId);
                    }
                })
                .build();
            doAppChallenge(traceId, authorization, resumeEx);
        }

        private void flushAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int minReplyNoAck,
            int minReplyMax)
        {
            final long replyAckMax = Math.max(replySeq - minReplyNoAck, 0);
            if (replyAckMax > replyAck || minReplyMax > replyMax)
            {
                replyAck = replyAckMax;
                assert replyAck <= replySeq;

                replyMax = Math.max(minReplyMax, replyMax);

                doAppWindow(traceId, authorization, budgetId, padding);
            }
        }

        private void onAppMessage(
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
            default:
                break;
            }
        }

        private void onAppChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();

            server.onAppChallenge(traceId, authorization);
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;

            assert replyAck <= replySeq;

            session.touch();

            if (!server.sseUpgrade && server.decodedProgressToken != null)
            {
                server.sseUpgrade = true;
            }

            if (server.sseUpgrade)
            {
                server.doEncodeResponseBegin(traceId, authorization,
                    httpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                        .typeId(httpTypeId)
                        .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(STATUS_200))
                        .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_EVENT_STREAM))
                        .build());
            }
            else
            {
                server.doEncodeResponseBegin(traceId, authorization,
                    httpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                        .typeId(httpTypeId)
                        .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(STATUS_200))
                        .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                        .build());
            }
        }

        private void onAppData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            session.touch();

            if (replySeq > replyAck + encodeMax)
            {
                cleanupApp(traceId, authorization);
            }
            else if (payload != null)
            {
                if (eventStream != null)
                {
                    eventStream.doEncodeResponseSseData(traceId, authorization, requestId, payload.value());
                }
                else
                {
                    server.doEncodeResponseData(traceId, authorization, payload.value());
                }
            }
        }

        private void onAppFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + decodeMax)
            {
                cleanupApp(traceId, authorization);
            }
            else if (!server.sseUpgrade)
            {
                cleanupApp(traceId, authorization);
            }
            else if (extension.sizeof() > 0)
            {
                final McpFlushExFW flushEx =
                    mcpFlushExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
                if (flushEx != null)
                {
                    if (eventStream != null)
                    {
                        encodeRequestEventViaEventStream(traceId, authorization, flushEx);
                    }
                    else
                    {
                        server.doEncodeRequestEvent(traceId, authorization, requestId, flushEx);
                    }
                }
            }
        }

        private void encodeRequestEventViaEventStream(
            long traceId,
            long authorization,
            McpFlushExFW flushEx)
        {
            switch (flushEx.kind())
            {
            case McpFlushExFW.KIND_RESUMABLE:
                if (!McpState.replyOpening(eventStream.state))
                {
                    eventStream.doNetBeginAccepted(traceId, authorization);
                }
                final String16FW resumableId = flushEx.resumable().id();
                if (resumableId != null && resumableId.length() != -1)
                {
                    eventStream.doEncodeNotifyEvent(traceId, authorization, requestId,
                        resumableId, null);
                }
                break;
            case McpFlushExFW.KIND_PROGRESS:
                eventStream.doEncodeProgressEvent(traceId, authorization, requestId, flushEx.progress());
                break;
            case McpFlushExFW.KIND_SUSPEND:
                final long requestRetry = flushEx.suspend().retry();
                if (!McpState.replyOpening(eventStream.state))
                {
                    eventStream.doNetBeginAccepted(traceId, authorization);
                }
                if (requestRetry > 0)
                {
                    eventStream.doEncodeRetryEvent(traceId, authorization, requestRetry);
                }
                eventStream.doNetEnd(traceId, authorization);
                break;
            default:
                break;
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            if (eventStream != null)
            {
                eventStream.doEncodeResponseSsePostamble(traceId, authorization);
                eventStream.doNetEnd(traceId, authorization);
            }
            else
            {
                server.doEncodeResponseEnd(traceId, authorization);
            }
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            server.doNetAbort(traceId, authorization);
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;
            assert budgetId == 0L;

            initialAck = acknowledge;
            initialMax = maximum;
            initialBud = budgetId;
            initialPad = padding;

            state = McpState.openedInitial(state);

            server.decodeNet(traceId, authorization, budgetId);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            server.doNetReset(traceId, authorization);
        }

        private void cleanupApp(
            long traceId,
            long authorization)
        {
            doAppReset(traceId, authorization);
            doAppAbort(traceId, authorization);

            server.cleanupNet(traceId, authorization);
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
        Flyweight extension)
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
        int reserved)
    {
        doFlush(receiver, originId, routedId, streamId, sequence, acknowledge, maximum,
            traceId, authorization, budgetId, reserved, emptyRO);
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

    private static int indexOfByte(
        DirectBuffer buffer,
        int offset,
        int limit,
        byte value)
    {
        for (int cursor = offset; cursor < limit; cursor++)
        {
            if (buffer.getByte(cursor) == value)
            {
                return cursor;
            }
        }

        return -1;
    }

    private static boolean acceptRequiresJsonAndEventStream(
        String accept)
    {
        if (accept == null)
        {
            return false;
        }

        final boolean jsonOk = accept.contains(CONTENT_TYPE_JSON) ||
            accept.contains("application/*") ||
            accept.contains("*/*");
        final boolean sseOk = accept.contains(CONTENT_TYPE_EVENT_STREAM) ||
            accept.contains("text/*") ||
            accept.contains("*/*");
        return jsonOk && sseOk;
    }

    private static boolean acceptIncludesEventStream(
        String accept)
    {
        return accept != null &&
            (accept.contains(CONTENT_TYPE_EVENT_STREAM) ||
             accept.contains("*/*") ||
             accept.contains("text/*"));
    }

    private int encodeSseKeepAlive(
        MutableDirectBuffer out,
        int offset)
    {
        out.putBytes(offset, SSE_KEEPALIVE_BYTES);
        return SSE_KEEPALIVE_BYTES.length;
    }

    private int encodeSseRetry(
        MutableDirectBuffer out,
        int offset,
        long retry)
    {
        int progress = offset;
        progress += out.putStringWithoutLengthAscii(progress, "retry: " + retry + "\n");
        out.putByte(progress, (byte) '\n');
        progress += 1;
        return progress - offset;
    }

    private McpChallengeExFW suspendedChallengeEx()
    {
        return mcpChallengeExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
            .typeId(mcpTypeId)
            .suspended(b ->
            {
            })
            .build();
    }

    private int encodeSseNotifyEvent(
        MutableDirectBuffer out,
        int offset,
        String streamIdPrefix,
        String16FW id,
        byte[] body)
    {
        int progress = offset;

        out.putBytes(progress, SSE_ID_PREFIX_BYTES);
        progress += SSE_ID_PREFIX_BYTES.length;
        progress += out.putStringWithoutLengthAscii(progress, streamIdPrefix);
        out.putByte(progress, (byte) ':');
        progress += 1;
        final DirectBuffer idBuf = id.value();
        if (idBuf != null && id.length() > 0)
        {
            out.putBytes(progress, idBuf, 0, id.length());
            progress += id.length();
        }
        out.putByte(progress, (byte) '\n');
        progress += 1;

        if (body != null)
        {
            out.putBytes(progress, SSE_DATA_PREFIX_BYTES);
            progress += SSE_DATA_PREFIX_BYTES.length;
            out.putBytes(progress, body);
            progress += body.length;
            out.putBytes(progress, SSE_MESSAGE_TERMINATOR_BYTES);
            progress += SSE_MESSAGE_TERMINATOR_BYTES.length;
        }
        else
        {
            out.putByte(progress, (byte) '\n');
            progress += 1;
        }

        return progress - offset;
    }

    private int encodeSseProgressEvent(
        MutableDirectBuffer out,
        int offset,
        String streamIdPrefix,
        McpProgressFlushExFW progress)
    {
        int progress0 = offset;

        out.putBytes(progress0, SSE_ID_PREFIX_BYTES);
        progress0 += SSE_ID_PREFIX_BYTES.length;
        progress0 += out.putStringWithoutLengthAscii(progress0, streamIdPrefix);
        out.putByte(progress0, (byte) ':');
        progress0 += 1;
        final String16FW idValue = progress.id();
        if (idValue.length() > 0)
        {
            out.putBytes(progress0, idValue.value(), 0, idValue.length());
            progress0 += idValue.length();
        }
        out.putByte(progress0, (byte) '\n');
        progress0 += 1;

        out.putBytes(progress0, SSE_DATA_PREFIX_BYTES);
        progress0 += SSE_DATA_PREFIX_BYTES.length;

        final StringBuilder body = new StringBuilder(160);
        body.append("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\",\"params\":{\"progressToken\":\"")
            .append(progress.token().asString())
            .append("\",\"progress\":")
            .append(progress.progress());
        if (progress.total() != -1L)
        {
            body.append(",\"total\":").append(progress.total());
        }
        final String16FW message = progress.message();
        if (message.length() != -1)
        {
            body.append(",\"message\":\"").append(message.asString()).append("\"");
        }
        body.append("}}");
        progress0 += out.putStringWithoutLengthAscii(progress0, body.toString());

        out.putBytes(progress0, SSE_MESSAGE_TERMINATOR_BYTES);
        progress0 += SSE_MESSAGE_TERMINATOR_BYTES.length;

        return progress0 - offset;
    }

    private int encodeSseEvent(
        MutableDirectBuffer out,
        int offset,
        DirectBuffer payload,
        int payloadOffset,
        int payloadLength,
        int flags)
    {
        int progress = offset;

        if ((flags & DATA_FLAG_INIT) != 0)
        {
            out.putBytes(progress, SSE_DATA_PREFIX_BYTES);
            progress += SSE_DATA_PREFIX_BYTES.length;
        }

        progress += rewriteSseDataLines(out, progress, payload, payloadOffset, payloadLength);

        if ((flags & DATA_FLAG_FIN) != 0)
        {
            out.putBytes(progress, SSE_MESSAGE_TERMINATOR_BYTES);
            progress += SSE_MESSAGE_TERMINATOR_BYTES.length;
        }

        return progress - offset;
    }

    private static int rewriteSseDataLines(
        MutableDirectBuffer out,
        int offset,
        DirectBuffer payload,
        int payloadOffset,
        int payloadLength)
    {
        out.putBytes(offset, payload, payloadOffset, payloadLength);
        for (int i = 0; i < payloadLength; i++)
        {
            if (out.getByte(offset + i) == (byte) '\n')
            {
                out.putByte(offset + i, (byte) ' ');
            }
        }
        final int progress = offset + payloadLength;

        return progress - offset;
    }
}
