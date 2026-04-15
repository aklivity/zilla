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

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;

import java.nio.charset.StandardCharsets;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpClientFactory implements McpStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String MCP_TYPE_NAME = "mcp";
    private static final String HTTP_HEADER_METHOD = ":method";
    private static final String HTTP_HEADER_PATH = ":path";
    private static final String HTTP_HEADER_CONTENT_TYPE = "content-type";
    private static final String HTTP_HEADER_SESSION = "mcp-session-id";
    private static final String HTTP_HEADER_PROTOCOL_VERSION = "mcp-protocol-version";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_DELETE = "DELETE";
    private static final String PATH_MCP = "/mcp";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String PROTOCOL_VERSION = "2025-11-25";

    private static final byte[] INITIALIZED_BODY =
        "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TOOLS_LIST_BODY =
        "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TOOLS_CALL_PREFIX =
        "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PROMPTS_LIST_BODY =
        "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"prompts/list\"}".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RESOURCES_LIST_BODY =
        "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"resources/list\"}".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JSON_SUFFIX = "}".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RESULT_MARKER =
        "\"result\":".getBytes(StandardCharsets.US_ASCII);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(), 0, 0);

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    private final byte[] initBody;
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
    private final Long2ObjectHashMap<McpBindingConfig> bindings;

    public McpClientFactory(
        McpConfiguration config,
        EngineContext context)
    {
        final String clientName = config.clientName();
        final String clientVersion = config.clientVersion();
        this.initBody = ("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"," +
            "\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}," +
            "\"clientInfo\":{\"name\":\"" + clientName + "\"," +
            "\"version\":\"" + clientVersion + "\"}}}").getBytes(StandardCharsets.US_ASCII);
        this.writeBuffer = context.writeBuffer();
        this.codecBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.decodeMax = decodePool.slotCapacity();
        this.encodeMax = encodePool.slotCapacity();
        this.bindings = new Long2ObjectHashMap<>();
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
        bindings.put(binding.id, new McpBindingConfig(binding));
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

        final McpBindingConfig binding = bindings.get(routedId);
        final McpRouteConfig route = binding != null ? binding.resolve(authorization) : null;

        MessageConsumer newStream = null;

        if (route != null)
        {
            final long resolvedId = route.id;
            final OctetsFW extension = begin.extension();
            final McpBeginExFW mcpBeginEx = mcpBeginExRO.tryWrap(
                extension.buffer(), extension.offset(), extension.limit());

            if (mcpBeginEx != null)
            {
                final int kind = mcpBeginEx.kind();
                if (kind == McpBeginExFW.KIND_LIFECYCLE)
                {
                    newStream = new McpClientSession(
                        sender, originId, routedId, initialId, resolvedId)::onAppMessage;
                }
                else
                {
                    final String sessionId = extractSessionId(mcpBeginEx, kind);
                    final String toolName = kind == McpBeginExFW.KIND_TOOLS_CALL
                        ? mcpBeginEx.toolsCall().name().asString() : null;
                    newStream = new McpClientRequest(
                        sender, originId, routedId, initialId, resolvedId, kind, sessionId, toolName)::onAppMessage;
                }
            }
        }

        return newStream;
    }

    private String extractSessionId(
        McpBeginExFW mcpBeginEx,
        int kind)
    {
        return switch (kind)
        {
        case McpBeginExFW.KIND_LIFECYCLE -> mcpBeginEx.lifecycle().sessionId().asString();
        case McpBeginExFW.KIND_TOOLS_LIST -> mcpBeginEx.toolsList().sessionId().asString();
        case McpBeginExFW.KIND_TOOLS_CALL -> mcpBeginEx.toolsCall().sessionId().asString();
        case McpBeginExFW.KIND_PROMPTS_LIST -> mcpBeginEx.promptsList().sessionId().asString();
        case McpBeginExFW.KIND_PROMPTS_GET -> mcpBeginEx.promptsGet().sessionId().asString();
        case McpBeginExFW.KIND_RESOURCES_LIST -> mcpBeginEx.resourcesList().sessionId().asString();
        case McpBeginExFW.KIND_RESOURCES_READ -> mcpBeginEx.resourcesRead().sessionId().asString();
        default -> null;
        };
    }

    private final class McpClientSession
    {
        private static final int PHASE_INIT = 0;
        private static final int PHASE_INITIALIZING = 1;
        private static final int PHASE_INITIALIZED = 2;
        private static final int PHASE_SHUTDOWN = 3;

        private final MessageConsumer appSender;
        private final long originId;
        private final long routedId;
        private final long resolvedId;
        private final long initialId;
        private final long replyId;

        private long appInitialSeq;
        private long appInitialAck;
        private long appReplySeq;
        private long appReplyAck;
        private int appState;

        private long netInitialId;
        private long netReplyId;
        private MessageConsumer netSender;
        private long netInitialSeq;
        private long netInitialAck;
        private int netInitialMax;
        private long netReplySeq;
        private long netReplyAck;

        private int phase;
        private String sessionId;
        private boolean netBodySent;
        private boolean netEndSent;

        private long traceId;
        private long authorization;

        private McpClientSession(
            MessageConsumer appSender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId)
        {
            this.appSender = appSender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.resolvedId = resolvedId;
            this.phase = PHASE_INIT;
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
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAppWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAppReset(reset);
                break;
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            traceId = begin.traceId();
            authorization = begin.authorization();
            appInitialSeq = begin.sequence();
            appInitialAck = begin.acknowledge();
            appState = McpState.openedInitial(appState);

            doAppWindow(traceId, authorization);
            doNetInitializeBegin(traceId, authorization);
        }

        private void onAppEnd(
            EndFW end)
        {
            final long t = end.traceId();
            final long a = end.authorization();
            appState = McpState.closingInitial(appState);

            if (phase == PHASE_INITIALIZED)
            {
                phase = PHASE_SHUTDOWN;
                netBodySent = false;
                netEndSent = false;
                doNetDeleteBegin(t, a);
            }
        }

        private void onAppAbort(
            AbortFW abort)
        {
            doNetAbort(abort.traceId(), abort.authorization());
        }

        private void onAppWindow(
            WindowFW window)
        {
            appReplyAck = window.acknowledge();
            appState = McpState.openedReply(appState);
        }

        private void onAppReset(
            ResetFW reset)
        {
            doNetReset(reset.traceId(), reset.authorization());
        }

        private void onNetMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
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

        private void onNetWindow(
            WindowFW window)
        {
            netInitialAck = window.acknowledge();
            netInitialMax = window.maximum();

            if (!netBodySent)
            {
                netBodySent = true;
                final long t = window.traceId();
                final long a = window.authorization();
                sendNetBody(t, a);
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long t = begin.traceId();
            final long a = begin.authorization();
            netReplySeq = begin.sequence();
            netReplyAck = begin.acknowledge();

            if (phase == PHASE_INIT)
            {
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
                            sessionId = sessionHeader.value().asString();
                        }
                    }
                }
            }

            doNetWindow(t, a);
        }

        private void onNetData(
            DataFW data)
        {
            final long t = data.traceId();
            final long a = data.authorization();
            netReplySeq = data.sequence() + data.reserved();
            netReplyAck = data.acknowledge();
            doNetWindow(t, a);
        }

        private void onNetEnd(
            EndFW end)
        {
            final long t = end.traceId();
            final long a = end.authorization();

            switch (phase)
            {
            case PHASE_INIT:
                phase = PHASE_INITIALIZING;
                netBodySent = false;
                netEndSent = false;
                resetNetStream();
                doNetInitializedBegin(t, a);
                break;
            case PHASE_INITIALIZING:
                phase = PHASE_INITIALIZED;
                doAppBeginReply(t, a);
                break;
            case PHASE_SHUTDOWN:
                doAppEnd(t, a);
                break;
            default:
                break;
            }
        }

        private void onNetAbort(
            AbortFW abort)
        {
            doAppAbort(abort.traceId(), abort.authorization());
        }

        private void onNetReset(
            ResetFW reset)
        {
            doAppReset(reset.traceId(), reset.authorization());
        }

        private void sendNetBody(
            long t,
            long a)
        {
            final byte[] body;
            switch (phase)
            {
            case PHASE_INIT:
                body = initBody;
                break;
            case PHASE_INITIALIZING:
                body = INITIALIZED_BODY;
                break;
            default:
                body = null;
                break;
            }

            if (body != null)
            {
                final UnsafeBuffer payloadBuf = new UnsafeBuffer(body);
                doNetData(t, a, payloadBuf, 0, body.length);
            }

            if (!netEndSent)
            {
                netEndSent = true;
                doNetEnd(t, a);
            }
        }

        private void resetNetStream()
        {
            netInitialSeq = 0L;
            netInitialAck = 0L;
            netInitialMax = 0;
            netReplySeq = 0L;
            netReplyAck = 0L;
        }

        private void doNetInitializeBegin(
            long t,
            long a)
        {
            netInitialId = supplyInitialId.applyAsLong(resolvedId);
            netReplyId = supplyReplyId.applyAsLong(netInitialId);

            final HttpBeginExFW httpBeginEx = httpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(PATH_MCP))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_PROTOCOL_VERSION).value(PROTOCOL_VERSION))
                .build();

            netSender = newStream(this::onNetMessage, routedId, resolvedId, netInitialId,
                netInitialSeq, netInitialAck, 0, t, a, 0L, httpBeginEx);
        }

        private void doNetInitializedBegin(
            long t,
            long a)
        {
            netInitialId = supplyInitialId.applyAsLong(resolvedId);
            netReplyId = supplyReplyId.applyAsLong(netInitialId);

            final String sid = sessionId;
            final HttpBeginExFW httpBeginEx = httpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(PATH_MCP))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_PROTOCOL_VERSION).value(PROTOCOL_VERSION))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid))
                .build();

            netSender = newStream(this::onNetMessage, routedId, resolvedId, netInitialId,
                netInitialSeq, netInitialAck, 0, t, a, 0L, httpBeginEx);
        }

        private void doNetDeleteBegin(
            long t,
            long a)
        {
            netInitialId = supplyInitialId.applyAsLong(resolvedId);
            netReplyId = supplyReplyId.applyAsLong(netInitialId);
            resetNetStream();

            final String sid = sessionId;
            final HttpBeginExFW httpBeginEx = httpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(METHOD_DELETE))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(PATH_MCP))
                .headersItem(h -> h.name(HTTP_HEADER_PROTOCOL_VERSION).value(PROTOCOL_VERSION))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid))
                .build();

            netSender = newStream(this::onNetMessage, routedId, resolvedId, netInitialId,
                netInitialSeq, netInitialAck, 0, t, a, 0L, httpBeginEx);
        }

        private void doNetData(
            long t,
            long a,
            DirectBuffer payload,
            int offset,
            int length)
        {
            final int reserved = length;
            doData(netSender, routedId, resolvedId, netInitialId,
                netInitialSeq, netInitialAck, netInitialMax,
                t, a, 0x03, 0L, reserved, payload, offset, length);
            netInitialSeq += reserved;
        }

        private void doNetEnd(
            long t,
            long a)
        {
            doEnd(netSender, routedId, resolvedId, netInitialId,
                netInitialSeq, netInitialAck, netInitialMax, t, a);
        }

        private void doNetAbort(
            long t,
            long a)
        {
            doAbort(netSender, routedId, resolvedId, netInitialId,
                netInitialSeq, netInitialAck, netInitialMax, t, a);
        }

        private void doNetReset(
            long t,
            long a)
        {
            doReset(netSender, routedId, resolvedId, netReplyId,
                netReplySeq, netReplyAck, 0, t, a, emptyRO);
        }

        private void doNetWindow(
            long t,
            long a)
        {
            doWindow(netSender, routedId, resolvedId, netReplyId,
                netReplySeq, netReplyAck, decodeMax, t, a, 0L, 0);
        }

        private void doAppBeginReply(
            long t,
            long a)
        {
            final String sid = sessionId;
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .lifecycle(l -> l.sessionId(sid))
                .build();

            doBegin(appSender, originId, routedId, replyId,
                appReplySeq, appReplyAck, 0, t, a, 0L, beginEx);
            appState = McpState.openingReply(appState);
        }

        private void doAppEnd(
            long t,
            long a)
        {
            if (!McpState.initialClosed(appState))
            {
                appState = McpState.closedInitial(appState);
                doEnd(appSender, originId, routedId, initialId,
                    appInitialSeq, appInitialAck, 0, t, a);
            }
        }

        private void doAppAbort(
            long t,
            long a)
        {
            doAbort(appSender, originId, routedId, initialId,
                appInitialSeq, appInitialAck, 0, t, a);
        }

        private void doAppReset(
            long t,
            long a)
        {
            doReset(appSender, originId, routedId, replyId,
                appReplySeq, appReplyAck, 0, t, a, emptyRO);
        }

        private void doAppWindow(
            long t,
            long a)
        {
            doWindow(appSender, originId, routedId, initialId,
                appInitialSeq, appInitialAck, decodeMax, t, a, 0L, 0);
        }
    }

    private final class McpClientRequest
    {
        private final MessageConsumer appSender;
        private final long originId;
        private final long routedId;
        private final long resolvedId;
        private final long initialId;
        private final long replyId;
        private final int kind;
        private final String sessionId;
        private final String toolName;

        private long appInitialSeq;
        private long appInitialAck;
        private long appReplySeq;
        private long appReplyAck;
        private int appState;

        private long netInitialId;
        private long netReplyId;
        private MessageConsumer netSender;
        private long netInitialSeq;
        private long netInitialAck;
        private int netInitialMax;
        private long netReplySeq;
        private long netReplyAck;

        private boolean netWindowGranted;
        private boolean appEnded;
        private boolean requestSent;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private long traceId;
        private long authorization;

        private McpClientRequest(
            MessageConsumer appSender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            int kind,
            String sessionId,
            String toolName)
        {
            this.appSender = appSender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.resolvedId = resolvedId;
            this.kind = kind;
            this.sessionId = sessionId;
            this.toolName = toolName;
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
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAppWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAppReset(reset);
                break;
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            traceId = begin.traceId();
            authorization = begin.authorization();
            appInitialSeq = begin.sequence();
            appInitialAck = begin.acknowledge();
            appState = McpState.openedInitial(appState);

            doAppWindow(traceId, authorization);
            doNetRequestBegin(traceId, authorization);
        }

        private void onAppData(
            DataFW data)
        {
            final long t = data.traceId();
            final long a = data.authorization();
            final OctetsFW payload = data.payload();

            appInitialSeq = data.sequence() + data.reserved();
            appInitialAck = data.acknowledge();

            if (kind == McpBeginExFW.KIND_TOOLS_CALL && payload != null)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(initialId);
                }

                if (encodeSlot == NO_SLOT)
                {
                    doNetAbort(t, a);
                    doAppReset(t, a);
                }
                else
                {
                    final MutableDirectBuffer slot = encodePool.buffer(encodeSlot);
                    slot.putBytes(encodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                    encodeSlotOffset += payload.sizeof();
                }
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            traceId = end.traceId();
            authorization = end.authorization();
            appEnded = true;

            if (netWindowGranted)
            {
                sendRequest(traceId, authorization);
            }
        }

        private void onAppAbort(
            AbortFW abort)
        {
            cleanupEncodeSlot();
            doNetAbort(abort.traceId(), abort.authorization());
        }

        private void onAppWindow(
            WindowFW window)
        {
            appReplyAck = window.acknowledge();
            appState = McpState.openedReply(appState);
        }

        private void onAppReset(
            ResetFW reset)
        {
            cleanupEncodeSlot();
            cleanupDecodeSlot();
            doNetReset(reset.traceId(), reset.authorization());
        }

        private void onNetMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
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

        private void onNetWindow(
            WindowFW window)
        {
            netInitialAck = window.acknowledge();
            netInitialMax = window.maximum();
            netWindowGranted = true;

            if (appEnded && !requestSent)
            {
                sendRequest(window.traceId(), window.authorization());
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long t = begin.traceId();
            final long a = begin.authorization();
            netReplySeq = begin.sequence();
            netReplyAck = begin.acknowledge();
            doNetWindow(t, a);
        }

        private void onNetData(
            DataFW data)
        {
            final long t = data.traceId();
            final long a = data.authorization();
            final OctetsFW payload = data.payload();

            netReplySeq = data.sequence() + data.reserved();
            netReplyAck = data.acknowledge();

            if (payload != null && payload.sizeof() > 0)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    doNetAbort(t, a);
                    doAppReset(t, a);
                }
                else
                {
                    final MutableDirectBuffer slot = decodePool.buffer(decodeSlot);
                    slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                    decodeSlotOffset += payload.sizeof();
                    doNetWindow(t, a);
                }
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long t = end.traceId();
            final long a = end.authorization();

            sendResult(t, a);
            cleanupDecodeSlot();
        }

        private void onNetAbort(
            AbortFW abort)
        {
            cleanupDecodeSlot();
            cleanupEncodeSlot();
            doAppAbort(abort.traceId(), abort.authorization());
        }

        private void onNetReset(
            ResetFW reset)
        {
            cleanupDecodeSlot();
            cleanupEncodeSlot();
            doAppReset(reset.traceId(), reset.authorization());
        }

        private void sendRequest(
            long t,
            long a)
        {
            if (requestSent)
            {
                return;
            }
            requestSent = true;

            switch (kind)
            {
            case McpBeginExFW.KIND_TOOLS_LIST:
                doNetData(t, a, new UnsafeBuffer(TOOLS_LIST_BODY), 0, TOOLS_LIST_BODY.length);
                break;
            case McpBeginExFW.KIND_TOOLS_CALL:
                doNetData(t, a, new UnsafeBuffer(TOOLS_CALL_PREFIX), 0, TOOLS_CALL_PREFIX.length);
                if (encodeSlot != NO_SLOT)
                {
                    final DirectBuffer slot = encodePool.buffer(encodeSlot);
                    doNetData(t, a, slot, 0, encodeSlotOffset);
                    cleanupEncodeSlot();
                }
                doNetData(t, a, new UnsafeBuffer(JSON_SUFFIX), 0, JSON_SUFFIX.length);
                break;
            case McpBeginExFW.KIND_PROMPTS_LIST:
                doNetData(t, a, new UnsafeBuffer(PROMPTS_LIST_BODY), 0, PROMPTS_LIST_BODY.length);
                break;
            case McpBeginExFW.KIND_RESOURCES_LIST:
                doNetData(t, a, new UnsafeBuffer(RESOURCES_LIST_BODY), 0, RESOURCES_LIST_BODY.length);
                break;
            default:
                break;
            }

            doNetEnd(t, a);
        }

        private void sendResult(
            long t,
            long a)
        {
            if (decodeSlot == NO_SLOT)
            {
                doAppEnd(t, a);
                return;
            }

            final MutableDirectBuffer slot = decodePool.buffer(decodeSlot);
            final int limit = decodeSlotOffset;

            int resultOffset = findResultOffset(slot, 0, limit);
            if (resultOffset >= 0)
            {
                final int resultLimit = limit - 1;
                if (resultLimit > resultOffset)
                {
                    doAppBeginReply(t, a);
                    doAppData(t, a, slot, resultOffset, resultLimit - resultOffset);
                }
            }

            doAppEnd(t, a);
        }

        private int findResultOffset(
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final byte[] marker = RESULT_MARKER;
            final int markerLen = marker.length;
            for (int i = offset; i <= limit - markerLen; i++)
            {
                boolean found = true;
                for (int j = 0; j < markerLen; j++)
                {
                    if (buffer.getByte(i + j) != marker[j])
                    {
                        found = false;
                        break;
                    }
                }
                if (found)
                {
                    return i + markerLen;
                }
            }
            return -1;
        }

        private void doNetRequestBegin(
            long t,
            long a)
        {
            netInitialId = supplyInitialId.applyAsLong(resolvedId);
            netReplyId = supplyReplyId.applyAsLong(netInitialId);

            final String sid = sessionId;
            final HttpBeginExFW httpBeginEx = httpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(PATH_MCP))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_PROTOCOL_VERSION).value(PROTOCOL_VERSION))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid))
                .build();

            netSender = newStream(this::onNetMessage, routedId, resolvedId, netInitialId,
                netInitialSeq, netInitialAck, 0, t, a, 0L, httpBeginEx);
        }

        private void doNetData(
            long t,
            long a,
            DirectBuffer payload,
            int offset,
            int length)
        {
            final int reserved = length;
            doData(netSender, routedId, resolvedId, netInitialId,
                netInitialSeq, netInitialAck, netInitialMax,
                t, a, 0x03, 0L, reserved, payload, offset, length);
            netInitialSeq += reserved;
        }

        private void doNetEnd(
            long t,
            long a)
        {
            doEnd(netSender, routedId, resolvedId, netInitialId,
                netInitialSeq, netInitialAck, netInitialMax, t, a);
        }

        private void doNetAbort(
            long t,
            long a)
        {
            doAbort(netSender, routedId, resolvedId, netInitialId,
                netInitialSeq, netInitialAck, netInitialMax, t, a);
        }

        private void doNetReset(
            long t,
            long a)
        {
            doReset(netSender, routedId, resolvedId, netReplyId,
                netReplySeq, netReplyAck, 0, t, a, emptyRO);
        }

        private void doNetWindow(
            long t,
            long a)
        {
            doWindow(netSender, routedId, resolvedId, netReplyId,
                netReplySeq, netReplyAck, decodeMax, t, a, 0L, 0);
        }

        private void doAppBeginReply(
            long t,
            long a)
        {
            doBegin(appSender, originId, routedId, replyId,
                appReplySeq, appReplyAck, 0, t, a, 0L, emptyRO);
            appState = McpState.openingReply(appState);
        }

        private void doAppData(
            long t,
            long a,
            DirectBuffer payload,
            int offset,
            int length)
        {
            final int reserved = length;
            doData(appSender, originId, routedId, replyId,
                appReplySeq, appReplyAck, 0,
                t, a, 0x03, 0L, reserved, payload, offset, length);
            appReplySeq += reserved;
        }

        private void doAppEnd(
            long t,
            long a)
        {
            if (!McpState.initialClosed(appState))
            {
                appState = McpState.closedInitial(appState);
                doEnd(appSender, originId, routedId, replyId,
                    appReplySeq, appReplyAck, 0, t, a);
            }
        }

        private void doAppAbort(
            long t,
            long a)
        {
            doAbort(appSender, originId, routedId, replyId,
                appReplySeq, appReplyAck, 0, t, a);
        }

        private void doAppReset(
            long t,
            long a)
        {
            doReset(appSender, originId, routedId, initialId,
                appInitialSeq, appInitialAck, 0, t, a, emptyRO);
        }

        private void doAppWindow(
            long t,
            long a)
        {
            doWindow(appSender, originId, routedId, initialId,
                appInitialSeq, appInitialAck, decodeMax, t, a, 0L, 0);
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
            }
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
