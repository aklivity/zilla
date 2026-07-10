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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpBeginExFW.KIND_LIFECYCLE;
import static io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpBeginExFW.KIND_RESOURCES_READ;
import static io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpBeginExFW.KIND_RESOURCES_TEMPLATES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpBeginExFW.KIND_TOOLS_CALL;
import static io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpResourceConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpToolConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.McpHttpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.config.McpHttpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.config.McpHttpRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.events.McpHttpEventContext;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.transform.McpHttpArguments;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.transform.McpHttpDiscard;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.transform.McpHttpQuery;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.transform.McpHttpResultWrap;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.transform.McpHttpResults;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.transform.McpHttpToolResult;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpResetExFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonStream;
import io.aklivity.zilla.runtime.common.json.JsonTransforms;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongIntPredicate;

public final class McpHttpProxyFactory implements BindingHandler
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String MCP_TYPE_NAME = "mcp";

    private static final String HEADER_PATH = ":path";
    private static final String HEADER_STATUS = ":status";
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String HEADER_COOKIE = "cookie";
    private static final String DEFAULT_CONTENT_TYPE = "application/json";

    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_COMPLETE = 0x03;
    private static final int WINDOW_MAX = 65536;
    private static final int JSON_RPC_INVALID_PARAMS = -32602;
    private static final int JSON_RPC_INTERNAL_ERROR = -32603;

    private static final byte[] REPLY_SUFFIX = "\"}]}".getBytes(UTF_8);
    private static final byte[] TOOL_ERROR_PREFIX = "{\"content\":[{\"type\":\"text\",\"text\":\"".getBytes(UTF_8);
    private static final byte[] TOOL_ERROR_SUFFIX = "\"}],\"isError\":true}".getBytes(UTF_8);
    private static final byte[] RESOURCE_PREFIX = "{\"contents\":[{\"uri\":".getBytes(UTF_8);
    private static final byte[] RESOURCE_MIME = ",\"mimeType\":".getBytes(UTF_8);
    private static final byte[] RESOURCE_TEXT_OPEN = ",\"text\":\"".getBytes(UTF_8);

    private static final Map<String, String> EMPTY_PARAMS = Map.of();
    private static final Map<String, Object> SINK_SEGMENTABLE = Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();
    private final McpResetExFW.Builder mcpResetExRW = new McpResetExFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int mcpTypeId;
    private final EngineContext context;
    private final McpHttpEventContext events;
    private final Long2ObjectHashMap<McpHttpBindingConfig> bindings;

    private final UnsafeBufferEx escapeRO = new UnsafeBufferEx(new byte[0]);
    private final UnsafeBufferEx emptyRequestRO = new UnsafeBufferEx(new byte[0]);
    private final Map<McpHttpRouteConfig, List<String>> routePathArgReferences;
    private final Map<McpHttpToolConfig, List<String>> toolResultReferences;

    // hoisted to avoid reallocating a capturing method-reference object on every computeIfAbsent call
    private final Function<McpHttpRouteConfig, List<String>> newPathArgReferencesFn = this::newPathArgReferences;
    private final Function<McpHttpToolConfig, List<String>> newToolResultReferencesFn = this::newToolResultReferences;

    private final Map<String, McpSession> sessions;
    private final Supplier<String> supplySessionId;
    private final int sessionIdAttempts;
    private final LongIntPredicate isLocalIndex;

    public McpHttpProxyFactory(
        McpHttpConfiguration config,
        EngineContext context)
    {
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.events = new McpHttpEventContext(context);
        this.bindings = new Long2ObjectHashMap<>();
        this.routePathArgReferences = new IdentityHashMap<>();
        this.toolResultReferences = new IdentityHashMap<>();
        this.sessions = new Object2ObjectHashMap<>();
        this.supplySessionId = config.sessionIdSupplier();
        this.sessionIdAttempts = config.sessionIdAttempts();
        this.isLocalIndex = context::isLocalIndex;
    }

    public void attach(
        BindingConfig binding)
    {
        bindings.put(binding.id, new McpHttpBindingConfig(binding, context));
    }

    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
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

        MessageConsumer newStream = null;

        final McpHttpBindingConfig binding = bindings.get(routedId);
        final McpBeginExFW beginEx = extension.get(mcpBeginExRO::tryWrap);

        if (binding != null && beginEx != null)
        {
            final int kind = beginEx.kind();

            if (kind == KIND_LIFECYCLE)
            {
                final int capabilities = beginEx.lifecycle().capabilities();
                final String sessionId = newSessionId(routedId);
                if (sessionId != null)
                {
                    newStream = new McpSession(sessionId, capabilities,
                        sender, originId, routedId, initialId, authorization, affinity)::onMcpMessage;
                }
            }
            else
            {
                final String sessionId = sessionId(beginEx);
                final McpSession session = sessionId != null ? sessions.get(sessionId) : null;

                if (session != null)
                {
                    switch (kind)
                    {
                    case KIND_TOOLS_LIST:
                        newStream = new McpToolsListProxy(binding,
                            sender, originId, routedId, initialId, authorization, affinity)::onMcpMessage;
                        break;
                    case KIND_RESOURCES_LIST:
                        newStream = new McpResourcesListProxy(binding,
                            sender, originId, routedId, initialId, authorization, affinity)::onMcpMessage;
                        break;
                    case KIND_RESOURCES_TEMPLATES_LIST:
                        newStream = new McpResourcesTemplatesListProxy(binding,
                            sender, originId, routedId, initialId, authorization, affinity)::onMcpMessage;
                        break;
                    case KIND_TOOLS_CALL:
                    {
                        final String name = beginEx.toolsCall().name().asString();
                        final int contentLength = beginEx.toolsCall().contentLength();
                        final McpHttpRouteConfig route = binding.resolveTool(name, authorization);
                        if (route != null)
                        {
                            newStream = new McpToolsCallProxy(binding, route, name, contentLength,
                                sender, originId, routedId, initialId, authorization, affinity)::onMcpMessage;
                        }
                        break;
                    }
                    case KIND_RESOURCES_READ:
                    {
                        final String uri = beginEx.resourcesRead().uri().asString();
                        final int contentLength = beginEx.resourcesRead().contentLength();
                        final Map<String, String> params = new HashMap<>();
                        final McpHttpResourceConfig resource = binding.resolveResource(uri, params);
                        final McpHttpRouteConfig route = resource != null
                            ? binding.resolveResourceRoute(resource.name, authorization)
                            : null;
                        if (route != null)
                        {
                            newStream = new McpResourcesReadProxy(binding, route, resource, uri, params, contentLength,
                                sender, originId, routedId, initialId, authorization, affinity)::onMcpMessage;
                        }
                        break;
                    }
                    default:
                        break;
                    }
                }
            }
        }

        return newStream;
    }

    private abstract class McpProxy
    {
        final McpHttpBindingConfig binding;
        final McpHttpRouteConfig route;
        final String name;
        final String uri;
        final Map<String, String> params;
        final int contentLength;

        final MessageConsumer sender;
        final long originId;
        final long routedId;
        final long initialId;
        final long replyId;
        final long authorization;
        final long affinity;

        int state;
        boolean requestHandled;

        int decodeSlot = NO_SLOT;
        int decodeSlotOffset;

        int encodeSlot = NO_SLOT;
        int encodeSlotOffset;
        boolean replyDataStarted;

        long initialSeq;
        long initialAck;
        int initialMax;

        long replySeq;
        long replyAck;
        int replyMax;
        int replyPad;

        private McpProxy(
            McpHttpBindingConfig binding,
            McpHttpRouteConfig route,
            String name,
            String uri,
            Map<String, String> params,
            int contentLength,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            this.binding = binding;
            this.route = route;
            this.name = name;
            this.uri = uri;
            this.params = params;
            this.contentLength = contentLength;
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.affinity = affinity;
        }

        void onMcpMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onMcpBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onMcpData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onMcpEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onMcpAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                onMcpWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onMcpReset(resetRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        // The buffered kinds (listings) reply immediately when no request body is expected; the HTTP-backed
        // kinds override this to await the request body before shaping the upstream request.
        void onMcpBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            state = McpHttpState.openingInitial(state);

            doMcpWindow(traceId);
            if (!requestHandled && contentLength < 0)
            {
                onMcpRequest(traceId);
            }
        }

        // Every concrete kind reaching this base implementation (listings, resources/read) already
        // dispatches its request synchronously from onMcpBegin — see McpHttpProxy.sendTrivialRequestBegin
        // and the contentLength < 0 branch below — so requestHandled is always true by the time any
        // further DATA or END arrives; there is nothing left to buffer or dispatch here.
        void onMcpData(
            DataFW data)
        {
            initialSeq = data.sequence() + data.reserved();
        }

        void onMcpEnd(
            EndFW end)
        {
            initialSeq = end.sequence();
            state = McpHttpState.closedInitial(state);
            cleanupDecodeSlot();
        }

        void onMcpAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            state = McpHttpState.closedInitial(state);
            cleanupDecodeSlot();
        }

        void onMcpWindow(
            WindowFW window)
        {
            replyAck = window.acknowledge();
            replyMax = window.maximum();
            replyPad = window.padding();

            final long traceId = window.traceId();
            flushReply(traceId);
        }

        void onMcpReset(
            ResetFW reset)
        {
            state = McpHttpState.closedReply(state);
            cleanupEncodeSlot();
        }

        abstract void onMcpRequest(
            long traceId);

        void doMcpReply(
            long traceId,
            byte[] reply)
        {
            doEncodeReply(reply);
            completeReply(traceId);
        }

        void completeReply(
            long traceId)
        {
            state = McpHttpState.closingReply(state);
            doMcpBegin(traceId);
            flushReply(traceId);
        }

        boolean acquireEncodeSlot()
        {
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(replyId);
            }

            return encodeSlot != NO_SLOT;
        }

        void doEncodeReply(
            byte[] bytes)
        {
            if (acquireEncodeSlot())
            {
                encodePool.buffer(encodeSlot).putBytes(encodeSlotOffset, bytes);
                encodeSlotOffset += bytes.length;
            }
            else
            {
                cleanup(0L);
            }
        }

        void doEncodeReply(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (acquireEncodeSlot())
            {
                encodePool.buffer(encodeSlot).putBytes(encodeSlotOffset, buffer, offset, length);
                encodeSlotOffset += length;
            }
            else
            {
                cleanup(0L);
            }
        }

        // Writes an escaped JSON string directly into encodeSlot at encodeSlotOffset, avoiding a
        // separate scratch buffer for content that is only ever appended once, in order.
        void doEncodeReplyJsonString(
            String value)
        {
            if (acquireEncodeSlot())
            {
                encodeSlotOffset = putJsonString(encodePool.buffer(encodeSlot), encodeSlotOffset, value);
            }
            else
            {
                cleanup(0L);
            }
        }

        void doEncodeReplyJsonString(
            DirectBufferEx source,
            int offset,
            int length)
        {
            if (acquireEncodeSlot())
            {
                encodeSlotOffset = putJsonString(encodePool.buffer(encodeSlot), encodeSlotOffset, source, offset, length);
            }
            else
            {
                cleanup(0L);
            }
        }

        // Returns the resulting encodeSlotOffset (bytes still queued after this attempt) so callers reacting
        // to a SUSPENDED transform status can tell whether flushing actually freed any room before retrying.
        int flushReply(
            long traceId)
        {
            if (encodeSlot != NO_SLOT && McpHttpState.replyOpened(state))
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                int maxPayload = replyMax - (int)(replySeq - replyAck) - replyPad;
                while (encodeSlotOffset > 0 && maxPayload > 0)
                {
                    final int length = Math.min(maxPayload, encodeSlotOffset);
                    final int reserved = length + replyPad;
                    final boolean fin = McpHttpState.replyClosing(state) && length == encodeSlotOffset;
                    final int flags = (replyDataStarted ? 0 : FLAGS_INIT) | (fin ? FLAGS_FIN : 0);
                    doMcpData(traceId, flags, reserved, slot, 0, length);
                    replyDataStarted = true;
                    final int remaining = encodeSlotOffset - length;
                    if (remaining > 0)
                    {
                        slot.putBytes(0, slot, length, remaining);
                    }
                    encodeSlotOffset = remaining;
                    maxPayload = replyMax - (int)(replySeq - replyAck) - replyPad;
                }

                if (McpHttpState.replyClosing(state) && encodeSlotOffset == 0)
                {
                    doMcpEnd(traceId);
                }
            }

            return encodeSlotOffset;
        }

        void doMcpBegin(
            long traceId)
        {
            if (!McpHttpState.replyOpened(state))
            {
                doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, emptyRO);
                state = McpHttpState.openedReply(state);
            }
        }

        void doMcpData(
            long traceId,
            int flags,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, flags, 0L, reserved, buffer, offset, length);
            replySeq += reserved;
        }

        void doMcpEnd(
            long traceId)
        {
            if (!McpHttpState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = McpHttpState.closedReply(state);
            }
            cleanupEncodeSlot();
        }

        void doMcpAbort(
            long traceId)
        {
            doMcpBegin(traceId);
            if (!McpHttpState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = McpHttpState.closedReply(state);
            }
            cleanupEncodeSlot();
        }

        void doMcpWindow(
            long traceId)
        {
            initialMax = WINDOW_MAX;
            initialAck = initialSeq;
            state = McpHttpState.openedInitial(state);
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0);
        }

        // Triggered whenever HttpProxy's initial (request) window changes, since that is what lets
        // pumpRequest drain more of this side's own decodeSlot; the window granted back to the mcp client
        // reflects only this side's own backlog — decodeSlotOffset is in mcp-client request bytes, the same
        // units as initialSeq/initialAck, unlike HttpProxy's own initial-direction counters, which are a
        // distinct byte stream once request shaping (body/query) has transformed the content.
        // max stays pinned to the slot's full physical capacity; ack alone carries the backlog discount, so
        // ack + max lands exactly on the true remaining room without double-counting the backlog.
        void flushMcpWindow(
            long traceId,
            long budgetId,
            int padding)
        {
            final long newInitialAck = Math.max(initialAck, initialSeq - decodeSlotOffset);
            final int newInitialMax = Math.max(initialMax, decodePool.slotCapacity());

            if (newInitialAck > initialAck || newInitialMax > initialMax || !McpHttpState.initialOpened(state))
            {
                initialAck = newInitialAck;
                initialMax = newInitialMax;
                state = McpHttpState.openedInitial(state);
                doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding);
            }
        }

        void doMcpReset(
            long traceId)
        {
            if (!McpHttpState.initialClosed(state))
            {
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
                state = McpHttpState.closedInitial(state);
            }
            cleanupDecodeSlot();
        }

        void doMcpReset(
            long traceId,
            int code,
            String message)
        {
            if (!McpHttpState.initialClosed(state))
            {
                final McpResetExFW resetEx = mcpResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .error(e -> e
                        .code(code)
                        .message(message))
                    .build();
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, resetEx);
                state = McpHttpState.closedInitial(state);
            }
            cleanupDecodeSlot();
        }

        void cleanup(
            long traceId)
        {
            doMcpReset(traceId);
            doMcpAbort(traceId);
        }

        void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
            }
        }

        void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
            }
        }
    }

    // Shared base for the two request kinds that proxy to an upstream HTTP endpoint (tools/call and
    // resources/read): owns the paired HttpProxy, the request-shaping path built from route.with, and
    // the streaming request/response machinery. Per-kind response mapping is left to responseBegin.
    private abstract class McpHttpProxy extends McpProxy
    {
        final HttpProxy delegate;
        final Map<String, String> credentials = new HashMap<>();

        JsonPipeline responsePipeline;
        JsonGeneratorEx responseGenerator;
        // set once, unconditionally, when the upstream response headers arrive (see HttpProxy.onHttpBegin);
        // distinct from responsePipeline being non-null since the tools/call error-relay path (non-2xx
        // status) streams without a JsonPipeline at all
        boolean responseStarted;
        boolean responseDone;

        private McpHttpProxy(
            McpHttpBindingConfig binding,
            McpHttpRouteConfig route,
            String name,
            String uri,
            Map<String, String> params,
            int contentLength,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            super(binding, route, name, uri, params, contentLength, sender, originId, routedId, initialId,
                authorization, affinity);
            this.delegate = new HttpProxy(this, route.id);
        }

        @Override
        void onMcpAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            state = McpHttpState.closedInitial(state);
            delegate.doHttpAbort(traceId);
            cleanupDecodeSlot();
        }

        @Override
        void onMcpReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            state = McpHttpState.closedReply(state);
            delegate.doHttpReset(traceId);
            cleanupEncodeSlot();
        }

        @Override
        void onMcpWindow(
            WindowFW window)
        {
            super.onMcpWindow(window);

            if (responseStarted && !responseDone)
            {
                delegate.resumeResponse(window.traceId());
            }

            flushHttpWindow(window.traceId());
        }

        // Convenience trigger: delegate computes its own window purely from its own decodeSlot backlog, so
        // this only needs to supply the padding to report and re-fire the check.
        void flushHttpWindow(
            long traceId)
        {
            delegate.flushHttpWindow(traceId, 0L, replyPad);
        }

        // The tool config for tools/call, or null for resources/read which has no input schema.
        McpHttpToolConfig tool()
        {
            return null;
        }

        // Whether HttpProxy.resumeRequest (a window grant from the upstream) should call pumpRequest again —
        // only McpToolsCallProxy has a resumable request-streaming pipeline; overridden there. Kept as a
        // method (not a field check) so HttpProxy, which holds a McpHttpProxy-typed reference shared by every
        // kind, can ask without knowing which concrete kind it is talking to.
        boolean requestPumpable()
        {
            return false;
        }

        // Default no-op: only McpToolsCallProxy streams a request body/query pipeline that needs
        // pumping across multiple onMcpData calls; overridden there. Called polymorphically from
        // HttpProxy.resumeRequest via the same McpHttpProxy-typed reference requestPumpable() serves.
        void pumpRequest(
            long traceId)
        {
        }

        // Dispatches a bodyless upstream request immediately, resolving only ${params.*} references in
        // with.headers (there is no arguments/body concept at all for this shape). Used by both concrete
        // kinds for a route with nothing to stream: McpToolsCallProxy's fallback when with.body/
        // with.query are all absent (and tool.input is either absent or skipped — see its onMcpBegin), and
        // McpResourcesReadProxy's only supported shape today (see its onMcpBegin). This is genuinely
        // shared, kind-independent plumbing — not the per-kind streaming/dispatch logic those two classes
        // otherwise keep separate.
        void sendTrivialRequestBegin(
            long traceId)
        {
            requestHandled = true;
            doMcpWindow(traceId);

            final String path = route.resolvePath(null, params);
            final Map<String, String> headers = route.resolveHeaders(null, params);
            final String cookie = route.resolveCookies(null, params);
            if (cookie != null)
            {
                headers.put(HEADER_COOKIE, cookie);
            }
            credentials.clear();
            binding.resolveCredentials(authorization, credentials);
            delegate.doHttpBegin(traceId, headers, credentials, path, null);
            delegate.requestComplete();
            delegate.flushRequest(traceId);
        }

        // Unreachable for both concrete kinds: McpToolsCallProxy and McpResourcesReadProxy each fully
        // dispatch their own request from onMcpBegin (see each class's own override) and never reach the
        // base McpProxy dispatch path (onMcpData/onMcpEnd) that would call this — a no-op here only to
        // satisfy McpProxy's abstract contract.
        @Override
        void onMcpRequest(
            long traceId)
        {
        }

        void onHttpAbort(
            long traceId)
        {
            // a response may already be mid-construction (responseStep/errorRelayStep having acquired
            // encodeSlot to stage bytes not yet flushed) when the upstream aborts; the normal onHttpEnd path
            // drains and releases it via pumpResponse's terminal-status handling, but abort short-circuits
            // straight here, so this must release it directly or it leaks for the lifetime of the stream
            cleanupResponse();
            cleanupEncodeSlot();
            doMcpAbort(traceId);
        }

        // Opens the reply for the upstream response, now that status and content-type are known (called
        // unconditionally from HttpProxy.onHttpBegin, before any response DATA arrives) — every kind streams,
        // so there is no threshold decision left to make here.
        abstract void responseBegin(
            long traceId,
            String status,
            String contentType);

        // Feeds one input window through responsePipeline, wrapping responseGenerator directly against
        // encodeSlot's own buffer at the live write position so the generator's remaining() is the real
        // destination capacity: SUSPENDED naturally fires when encodeSlot is actually full, not at some
        // artificial proxy bound, and no intermediate scratch-buffer copy is needed. Acquiring the slot here
        // (rather than assuming the caller already has) mirrors acquireEncodeSlot()'s guarded pattern; the
        // freshly re-fetched encodePool.buffer(encodeSlot) is never held across another encodePool.buffer(...)
        // call, avoiding the shared-wrapper aliasing hazard DefaultBufferPool.buffer(int) exposes.
        JsonPipeline.Status responseStep(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            JsonPipeline.Status status;
            if (acquireEncodeSlot())
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                responseGenerator.wrap(slot, encodeSlotOffset, encodePool.slotCapacity());
                status = responsePipeline.transform(buffer, offset, offset + length, last);
                encodeSlotOffset += responseGenerator.length();
            }
            else
            {
                cleanup(0L);
                status = JsonPipeline.Status.REJECTED;
            }
            return status;
        }

        int responseRemaining()
        {
            return responsePipeline.remaining();
        }

        void completeResponse(
            long traceId)
        {
            responseDone = true;
            doEncodeResponseSuffix(traceId);
            state = McpHttpState.closingReply(state);
            flushReply(traceId);
        }

        // Writes the reply's closing bytes once the streamed body completes; the resources/read shape (the
        // only base-level user) closes the escaped text value and the surrounding contents envelope. Overridden
        // by tools/call, whose envelope shape depends on which of its two response modes (success/error) ran.
        // Self-contained: checks for room and flushes first if needed, rather than relying on the caller to
        // have reserved headroom for it.
        void doEncodeResponseSuffix(
            long traceId)
        {
            ensureEncodeRoom(traceId, REPLY_SUFFIX.length);
            doEncodeReply(REPLY_SUFFIX);
        }

        void responseReject(
            long traceId)
        {
            responseDone = true;
            cleanupResponse();
            cleanupEncodeSlot();
            doMcpAbort(traceId);
        }

        // A minimum-room check, not just "some room": responseStep can be called again and again with the
        // same near-full slot producing zero bytes each time once free space drops below what the next value
        // needs, and nothing else would ever trigger a flush to reclaim space — SUSPENDED alone does not shrink
        // the loop's progress flag. Gating on a real minimum (mirroring the request side's identical need)
        // guarantees flushReply actually runs once room gets tight, instead of spinning forever making no
        // progress.
        private int encodeFree()
        {
            return encodePool.slotCapacity() - encodeSlotOffset;
        }

        // Guarantees at least `length` bytes are free before a fixed-size, non-generator-tracked write
        // (a closing suffix): a full flush always frees the whole slot, vastly more than any of this
        // file's small fixed suffixes, so one flush attempt is always sufficient for them.
        void ensureEncodeRoom(
            long traceId,
            int length)
        {
            if (encodeFree() < length)
            {
                flushReply(traceId);
            }
        }

        void cleanupResponse()
        {
            responsePipeline = null;
            responseGenerator = null;
        }

        @Override
        void cleanup(
            long traceId)
        {
            doMcpReset(traceId);
            doMcpAbort(traceId);
            delegate.doHttpAbort(traceId);
            cleanupDecodeSlot();
            cleanupEncodeSlot();
        }
    }

    private final class McpToolsCallProxy extends McpHttpProxy
    {
        // populated by the McpHttpResults capture stage as structuredContent streams past; read back once the
        // response completes to resolve tool.summary's ${result.*} references without re-scanning a buffer
        private final Map<String, String> capturedResults = new HashMap<>();

        // the non-2xx response mode: relays the raw upstream body as escaped text with no JsonPipeline at all
        // (the body is not guaranteed to be valid JSON), using errorGenerator directly the same way responseStep
        // uses responseGenerator — wrap against encodeSlot's live position, drive via consumed()/length()
        private boolean errorRelay;
        private JsonGeneratorEx errorGenerator;
        private int errorRelayConsumed;
        private int errorRelayRemaining;

        // the single streaming pipeline for this route's request shape: with.body (model or template) projects
        // into requestGenerator (which writes into HttpProxy's own encode slot via requestStep); with.query
        // alone projects into a McpHttpQuery sink (requestGenerator stays null — nothing to write into
        // encodeSlot); neither, but tool.input still needs validating, projects into a McpHttpDiscard sink.
        // A route combining with.body and with.query is not yet supported here — see onMcpBegin.
        private JsonPipeline requestPipeline;
        private JsonGeneratorEx requestGenerator;
        private Map<String, String> requestArgs;
        private List<String> requestPathArgs;
        private boolean requestProjected;
        // non-null only when route.with.query != null; requestPipeline (the query projector's own sink)
        // populates it, and sendRequestBegin reads it back once requestProjected to build the query string
        private Map<String, List<String>> queryCaptured;

        private McpToolsCallProxy(
            McpHttpBindingConfig binding,
            McpHttpRouteConfig route,
            String name,
            int contentLength,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            super(binding, route, name, null, EMPTY_PARAMS, contentLength, sender, originId, routedId, initialId,
                authorization, affinity);
        }

        @Override
        McpHttpToolConfig tool()
        {
            return binding.tool(name);
        }

        @Override
        void onMcpBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            state = McpHttpState.openingInitial(state);

            final McpHttpWithConfig with = route.with;
            final McpHttpToolConfig tool = tool();
            final boolean needsBody = with.body != null;
            final boolean needsQuery = with.query != null;
            final boolean needsValidation = tool != null && tool.input != null &&
                contentLength >= 0 && contentLength <= decodePool.slotCapacity();

            if (needsBody || needsQuery || needsValidation)
            {
                requestArgs = new HashMap<>();
                requestPathArgs = pathArgReferences(route);

                JsonStream stream = JsonEx.stream(JsonEx.createParser())
                    .transform(new McpHttpArguments(requestArgs));
                if (needsValidation)
                {
                    // the schema validator must fully reassemble any individual scalar value spanning
                    // multiple windows before validating it (common-json's Eval is not fragment-aware) —
                    // the constraint is per-value, not per-request, but bounding the whole request to one
                    // decode slot is a simple sufficient (not necessary) proxy for "no value can possibly
                    // exceed the window", since no field can be larger than the whole document; a request
                    // whose length is unknown or exceeds the slot skips validation rather than risk the
                    // decode slot filling with an unconsumed in-flight value (see common-json issue for
                    // the underlying gap: a value that never fits any window stalls the pipeline forever
                    // rather than resolving to REJECTED)
                    stream = stream.transform(binding.jsonSchema(tool.input).validator());
                }

                if (needsBody)
                {
                    // a route combining with.body and with.query is not yet supported: producing
                    // both an outbound body and a query string from one incremental pass would need a second,
                    // independently-driven pipeline sharing the same decode-slot window (JsonPipeline has no
                    // fan-out — see JsonStream), which raises real compaction-ordering questions across two
                    // pipelines completing at different times; no current route configures both, so this is
                    // deferred rather than solved speculatively — with.query is silently ignored in this case
                    requestGenerator = JsonEx.createGenerator();
                    stream = with.body.template != null
                        ? stream.transform(JsonTransforms.projector(route.bodyTemplatePointers))
                            .transform(JsonTransforms.flatten(route.bodyTemplateTargets))
                        : stream.transform(JsonTransforms.projector(binding.jsonSchema(with.body.model)));
                    requestPipeline = stream.into(JsonEx.createSink(requestGenerator, SINK_SEGMENTABLE));
                }
                else if (needsQuery)
                {
                    queryCaptured = new LinkedHashMap<>();
                    requestPipeline = stream
                        .transform(JsonTransforms.projector(binding.jsonSchema(with.query)))
                        .into(new McpHttpQuery(queryCaptured));
                }
                else
                {
                    // tool.input must be validated even though this route has nothing to shape a request
                    // from (e.g. a route templating only static/${params.*} headers) — validate and discard
                    requestPipeline = stream.into(new McpHttpDiscard());
                }
                requestPipeline.reset();
                flushMcpWindow(traceId, 0L, 0);
            }
            else
            {
                sendTrivialRequestBegin(traceId);
            }
        }

        @Override
        void onMcpData(
            DataFW data)
        {
            if (requestPipeline != null)
            {
                final long traceId = data.traceId();
                final int reserved = data.reserved();
                final OctetsFW payload = data.payload();

                initialSeq = data.sequence() + reserved;

                if (payload != null)
                {
                    if (decodeSlot == NO_SLOT)
                    {
                        decodeSlot = decodePool.acquire(initialId);
                    }

                    if (decodeSlot == NO_SLOT || decodeSlotOffset + payload.sizeof() > decodePool.slotCapacity())
                    {
                        cleanup(traceId);
                    }
                    else
                    {
                        final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                        slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                        decodeSlotOffset += payload.sizeof();
                        pumpRequest(traceId);
                    }
                }
            }
            else
            {
                super.onMcpData(data);
            }
        }

        @Override
        void onMcpEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            initialSeq = end.sequence();
            state = McpHttpState.closedInitial(state);

            // requestPipeline is null only for the trivial (no body/query/validation) shape, which already
            // dispatched synchronously in onMcpBegin and set requestHandled there — nothing left to do here
            if (requestPipeline != null)
            {
                pumpRequest(traceId);
            }
        }

        @Override
        boolean requestPumpable()
        {
            return requestPipeline != null && !requestProjected;
        }

        @Override
        void pumpRequest(
            long traceId)
        {
            final List<String> unsatisfied = McpHttpState.initialClosed(state)
                ? List.of() : binding.unsatisfiedAccessors(route);
            if (!unsatisfied.isEmpty())
            {
                final String accessor = unsatisfied.get(0);
                events.schemaAccessorUnresolved(traceId, binding.id, name != null ? name : uri, accessor);
                doMcpReset(traceId, JSON_RPC_INTERNAL_ERROR, "unresolved expression: ${" + accessor + "}");
                cleanupDecodeSlot();
                return;
            }

            boolean progress = true;
            while (progress && !requestProjected)
            {
                final DirectBufferEx buffer = decodeSlot != NO_SLOT ? decodePool.buffer(decodeSlot) : emptyRequestRO;
                final boolean last = McpHttpState.initialClosed(state);
                final JsonPipeline.Status status = requestGenerator != null
                    ? delegate.requestStep(requestGenerator, requestPipeline, buffer, 0, decodeSlotOffset, last)
                    : requestPipeline.transform(buffer, 0, decodeSlotOffset, last);

                // compact only at a terminal status: across suspend cycles the pipeline re-feeds the same
                // window, so dropping consumed bytes mid-cycle would corrupt its positioning; here the
                // window-relative remaining() is the tail to keep, so the consumed prefix is the rest
                if (status != JsonPipeline.Status.SUSPENDED && decodeSlot != NO_SLOT)
                {
                    final int consumed = decodeSlotOffset - requestPipeline.remaining();
                    if (consumed > 0 && consumed < decodeSlotOffset)
                    {
                        final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                        slot.putBytes(0, slot, consumed, decodeSlotOffset - consumed);
                    }
                    decodeSlotOffset -= consumed;
                }

                switch (status)
                {
                case SUSPENDED:
                    // requestGenerator's destination is encodeSlot (see requestStep) — SUSPENDED means it
                    // just filled up; flush what's queued and retry only if that actually freed room (delegate
                    // is bounded by the upstream HTTP server's own window, which can leave encodeSlot short of
                    // full yet still unable to drain any further until the next onHttpWindow), else stop and
                    // wait for that to resume (other sink kinds never suspend)
                    final int beforeFlush = delegate.encodeSlotOffset;
                    if (requestGenerator != null && delegate.flushRequest(traceId) >= beforeFlush)
                    {
                        progress = false;
                    }
                    break;
                case STARVED:
                    progress = false;
                    break;
                case COMPLETED:
                    requestProjected = true;
                    progress = false;
                    cleanupDecodeSlot();
                    break;
                case REJECTED:
                    progress = false;
                    delegate.doHttpAbort(traceId);
                    doMcpReset(traceId, JSON_RPC_INVALID_PARAMS, "Invalid params");
                    cleanupDecodeSlot();
                    break;
                default:
                    progress = false;
                    break;
                }
            }

            if (!McpHttpState.initialClosed(state) && !McpHttpState.initialOpening(delegate.state) && requestReady())
            {
                sendRequestBegin(traceId);
            }

            if (McpHttpState.initialOpening(delegate.state))
            {
                if (requestProjected)
                {
                    delegate.requestComplete();
                }
                delegate.flushRequest(traceId);
            }

            if (!requestProjected && !McpHttpState.initialClosed(state))
            {
                delegate.flushMcpWindow(traceId);
            }
        }

        private boolean requestPathReady()
        {
            return requestArgs.keySet().containsAll(requestPathArgs);
        }

        // With a with.query-only route, requestPipeline is itself the query projector, so the query string
        // is not ready to build until that pipeline (tracked via requestProjected) reaches COMPLETED — the
        // same "path args ready" gate a body route uses is not sufficient here, since (unlike a request body,
        // which can keep streaming after the HTTP BEGIN) the query string must be fully known before the
        // BEGIN's :path is built.
        private boolean requestReady()
        {
            return requestPathReady() && (queryCaptured == null || requestProjected);
        }

        private void sendRequestBegin(
            long traceId)
        {
            String path = route.resolvePath(requestArgs, params);
            if (queryCaptured != null)
            {
                final String query = buildQueryString(queryCaptured);
                if (!query.isEmpty())
                {
                    path = path + "?" + query;
                }
            }
            final Map<String, String> headers = route.resolveHeaders(requestArgs, params);
            final String cookie = route.resolveCookies(requestArgs, params);
            if (cookie != null)
            {
                headers.put(HEADER_COOKIE, cookie);
            }
            credentials.clear();
            binding.resolveCredentials(authorization, credentials);
            delegate.doHttpBegin(traceId, headers, credentials, path,
                requestGenerator != null ? DEFAULT_CONTENT_TYPE : null);
        }

        private String buildQueryString(
            Map<String, List<String>> captured)
        {
            final StringBuilder builder = new StringBuilder();
            for (Map.Entry<String, List<String>> entry : captured.entrySet())
            {
                for (String value : entry.getValue())
                {
                    appendQuery(builder, entry.getKey(), value);
                }
            }
            return builder.toString();
        }

        // Owns the status check that used to live in onHttpResponse, now made up front before any response
        // pipeline exists: a 2xx status streams structuredContent through the output schema (or passes it
        // through canonicalized when there is none), capturing tool.summary's ${result.*} references as the
        // body streams past; any other status streams the raw upstream body back as escaped text with no size
        // cap, since a genuinely streamed relay needs none. Both modes still open the mcp reply via doMcpBegin
        // after writing their envelope's leading bytes, mirroring doEncodeReplyPrefix-then-doMcpBegin below.
        @Override
        void responseBegin(
            long traceId,
            String status,
            String contentType)
        {
            final boolean ok = status != null && status.startsWith("2");
            if (ok)
            {
                final McpHttpToolConfig tool = binding.tool(name);
                final JsonSchema outputSchema = tool != null ? binding.jsonSchema(tool.output) : null;
                // tool.summary is required by schema for every configured tool; tool is null only if a route
                // matches a tool name with no corresponding tools: entry (a misconfiguration), in which case
                // there is nothing to interpolate and McpHttpToolResult's own resolved-null fallback applies
                final String summaryTemplate = tool != null ? tool.summary : null;
                final List<String> resultPaths = tool != null ? toolResultReferences(tool) : List.of();

                responseGenerator = JsonEx.createGenerator();
                JsonStream stream = JsonEx.stream(JsonEx.createParser());
                if (tool != null && tool.outputWrapped)
                {
                    // the upstream body itself is not an object (e.g. a top-level array); wrap it to match
                    // the {"result":<value>} shape McpOpenapiCompositeGenerator advertised as outputSchema,
                    // before validating/projecting against that same wrapped schema below
                    stream = stream.transform(new McpHttpResultWrap());
                }
                if (outputSchema != null)
                {
                    // validate against the full (unprojected) document before pruning it down, matching the
                    // pre-streaming validate-then-project order: a value the schema rejects must never reach
                    // structuredContent even if the retained paths alone would otherwise look fine
                    stream = stream.transform(outputSchema.validator()).transform(JsonTransforms.projector(outputSchema));
                }
                // captures result.* references from exactly the events reaching the sink (i.e. after any
                // projection), matching the pre-streaming behavior of scanning the already-projected buffer;
                // McpHttpToolResult wraps the whole envelope around that same event stream, injecting
                // content/isError as more generator-tracked events once structuredContent's value closes —
                // see its class doc for why that is what keeps the (potentially large) summary text bounded
                responsePipeline = stream
                    .transform(new McpHttpResults(capturedResults, resultPaths))
                    .transform(new McpHttpToolResult(() -> interpolate(summaryTemplate, this::resolveCapturedResult)))
                    .into(JsonEx.createSink(responseGenerator, SINK_SEGMENTABLE));
                responsePipeline.reset();

                doMcpBegin(traceId);
            }
            else
            {
                errorRelay = true;
                errorGenerator = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true));

                doEncodeReply(TOOL_ERROR_PREFIX);
                doMcpBegin(traceId);
            }
        }

        @Override
        JsonPipeline.Status responseStep(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            return errorRelay ? errorRelayStep(buffer, offset, length, last) : super.responseStep(buffer, offset, length, last);
        }

        @Override
        int responseRemaining()
        {
            return errorRelay ? errorRelayRemaining : super.responseRemaining();
        }

        // Escapes buffer[offset + errorRelayConsumed .. offset + length) directly into encodeSlot via
        // errorGenerator, bounded by the generator's real remaining() the same way the JsonPipeline-driven
        // responseStep is: SUSPENDED when encodeSlot fills before the window is exhausted (retry the same
        // window), STARVED once the window is fully relayed but more is expected, COMPLETED once the window is
        // fully relayed and last. Unlike JSON parsing there is no mid-token boundary to respect, so any prefix
        // of the window can be taken — the only limit is the destination.
        private JsonPipeline.Status errorRelayStep(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            JsonPipeline.Status status;
            if (acquireEncodeSlot())
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                errorGenerator.wrap(slot, encodeSlotOffset, encodePool.slotCapacity());
                final int pending = length - errorRelayConsumed;
                final Completion completion = last ? Completion.COMPLETE : Completion.INCOMPLETE;
                errorGenerator.writeSegment(buffer, offset + errorRelayConsumed, pending, completion);
                encodeSlotOffset += errorGenerator.length();
                errorRelayConsumed += errorGenerator.consumed();
                errorRelayRemaining = length - errorRelayConsumed;

                if (errorRelayRemaining > 0)
                {
                    status = JsonPipeline.Status.SUSPENDED;
                }
                else
                {
                    errorRelayConsumed = 0;
                    status = last ? JsonPipeline.Status.COMPLETED : JsonPipeline.Status.STARVED;
                }
            }
            else
            {
                cleanup(0L);
                status = JsonPipeline.Status.REJECTED;
            }
            return status;
        }

        // Resolves a result.<path> reference from the values McpHttpResults captured while structuredContent
        // streamed past, replacing a re-scan of a fully buffered response copy.
        private String resolveCapturedResult(
            String expression)
        {
            String value = "";
            if (expression.startsWith("result."))
            {
                final String captured = capturedResults.get(expression.substring(7));
                value = captured != null ? captured : "";
            }
            return value;
        }

        // The success envelope (structuredContent, content, isError, and the closing brace) is written
        // entirely by McpHttpToolResult as part of responsePipeline itself — including the interpolated
        // tool.summary, injected as more generator-tracked events once structuredContent's value closes, so
        // it shares the same bounded, resumable write path a large captured result value would otherwise
        // overflow. By the time this runs (completeResponse only calls it once the pipeline reaches
        // COMPLETED), that envelope is already fully written; only the error-relay mode — which streams the
        // raw, non-JSON upstream body via errorGenerator directly, with no JsonPipeline of its own — still
        // needs its closing suffix written here, self-contained (checks for room, flushing first if needed,
        // rather than relying on the caller to have reserved headroom for it).
        @Override
        void doEncodeResponseSuffix(
            long traceId)
        {
            if (errorRelay)
            {
                ensureEncodeRoom(traceId, TOOL_ERROR_SUFFIX.length);
                doEncodeReply(TOOL_ERROR_SUFFIX);
            }
        }

        // A schema-validation failure discovered while streaming structuredContent used to always produce a
        // proper {"content":...,"isError":true} reply (the pre-streaming buffered path always had, since it
        // never committed to a reply shape until the whole body had already been validated). Once the response
        // genuinely streams, the envelope's opening bytes (now McpHttpToolResult's own injected START_OBJECT
        // and "structuredContent" key) have necessarily already gone out — even a validator that rejects on
        // the very first field only reaches that verdict on a later pump cycle (e.g. once the response body's
        // own end has been seen), and pumpResponse flushes whatever is pending after every cycle regardless of
        // status, so by the time REJECTED is known the opening is already on the wire. There is no way back at
        // that point, so this falls through to the inherited abort behavior (the same fallback resources/read
        // and the error-relay path already use) rather than attempting a sometimes-possible, sometimes-not
        // recovery that would depend on upstream framing details a client cannot rely on.
        @Override
        void cleanupResponse()
        {
            super.cleanupResponse();
            errorRelay = false;
            errorGenerator = null;
            errorRelayConsumed = 0;
            errorRelayRemaining = 0;
        }
    }

    private final class McpResourcesReadProxy extends McpHttpProxy
    {
        private final McpHttpResourceConfig resource;

        private McpResourcesReadProxy(
            McpHttpBindingConfig binding,
            McpHttpRouteConfig route,
            McpHttpResourceConfig resource,
            String uri,
            Map<String, String> params,
            int contentLength,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            super(binding, route, null, uri, params, contentLength, sender, originId, routedId, initialId,
                authorization, affinity);
            this.resource = resource;
        }

        // A resources/read request has no with.body concept at all (it is identified by uri + params,
        // already resolved via URI matching before this stream even opens — unlike tools/call's
        // name+arguments shape) and no current route configures with.query here either: doing so would need
        // an "arguments" JSON object to project a query from, which a resource read simply does not carry.
        // sendTrivialRequestBegin (shared with McpToolsCallProxy's own fallback for the equivalent shape) is
        // therefore this kind's only supported request path today; with.query is a documented, accepted gap.
        @Override
        void onMcpBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            state = McpHttpState.openingInitial(state);

            sendTrivialRequestBegin(traceId);
        }

        // Owns the status check that used to live in onHttpResponse, now made up front before any response
        // pipeline exists: a non-2xx status aborts immediately, so no pipeline is ever created and no reply
        // bytes are ever written for it — closing what used to be a documented gap (the streaming path used to
        // have no upstream status available at this point, since doMcpBegin had already opened the mcp reply
        // by the time the status was known; responseBegin now runs at onHttpBegin time, before any body byte
        // arrives, so the status is already in hand).
        @Override
        void responseBegin(
            long traceId,
            String status,
            String contentType)
        {
            final boolean ok = status != null && status.startsWith("2");
            if (!ok)
            {
                responseDone = true;
                doMcpAbort(traceId);
                // the mcp reply is already closed, so no future onMcpWindow will ever arrive to trigger
                // delegate's window; the upstream body is going to be discarded either way (no downstream
                // to backpressure against), so grant its full physical capacity now rather than leave it
                // starved of window forever
                flushHttpWindow(traceId);
            }
            else
            {
                final JsonSchema schema = resource != null ? binding.jsonSchema(resource.output) : null;
                responseGenerator = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true));
                responsePipeline = schema != null
                    ? JsonEx.stream(JsonEx.createParser())
                        .transform(JsonTransforms.projector(schema))
                        .into(JsonEx.createSink(responseGenerator, SINK_SEGMENTABLE))
                    : JsonEx.stream(JsonEx.createParser())
                        .into(JsonEx.createSink(responseGenerator, SINK_SEGMENTABLE));
                responsePipeline.reset();

                final String mimeType = resource != null && resource.mimeType != null
                    ? resource.mimeType
                    : contentType;
                doEncodeReplyPrefix(uri, mimeType);
                doMcpBegin(traceId);
            }
        }

        // Writes the streaming resource reply prefix up to the open quote of the text value directly into
        // encodeSlot; the escaped resource text and RESOURCE_SUFFIX follow as the projected body streams in.
        private void doEncodeReplyPrefix(
            String uri,
            String mimeType)
        {
            doEncodeReply(RESOURCE_PREFIX);
            doEncodeReplyJsonString(uri);
            doEncodeReply(RESOURCE_MIME);
            doEncodeReplyJsonString(mimeType);
            doEncodeReply(RESOURCE_TEXT_OPEN);
        }
    }

    private final class McpToolsListProxy extends McpProxy
    {
        private McpToolsListProxy(
            McpHttpBindingConfig binding,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            super(binding, null, null, null, EMPTY_PARAMS, -1, sender, originId, routedId, initialId,
                authorization, affinity);
        }

        @Override
        void onMcpRequest(
            long traceId)
        {
            requestHandled = true;
            doMcpReply(traceId, toolsList(binding));
            cleanupDecodeSlot();
        }
    }

    private final class McpResourcesListProxy extends McpProxy
    {
        private McpResourcesListProxy(
            McpHttpBindingConfig binding,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            super(binding, null, null, null, EMPTY_PARAMS, -1, sender, originId, routedId, initialId,
                authorization, affinity);
        }

        @Override
        void onMcpRequest(
            long traceId)
        {
            requestHandled = true;
            doMcpReply(traceId, resourcesList(binding));
            cleanupDecodeSlot();
        }
    }

    private final class McpResourcesTemplatesListProxy extends McpProxy
    {
        private McpResourcesTemplatesListProxy(
            McpHttpBindingConfig binding,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            super(binding, null, null, null, EMPTY_PARAMS, -1, sender, originId, routedId, initialId,
                authorization, affinity);
        }

        @Override
        void onMcpRequest(
            long traceId)
        {
            requestHandled = true;
            doMcpReply(traceId, resourcesTemplatesList(binding));
            cleanupDecodeSlot();
        }
    }

    private final class HttpProxy
    {
        private final McpHttpProxy mcp;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private MessageConsumer receiver;
        private int state;

        private int encodeSlot = NO_SLOT;
        int encodeSlotOffset;
        private boolean requestDataStarted;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private String responseStatus;
        private String responseContentType;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private HttpProxy(
            McpHttpProxy mcp,
            long resolvedId)
        {
            this.mcp = mcp;
            this.originId = mcp.routedId;
            this.routedId = resolvedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void onHttpMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onHttpBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onHttpData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onHttpEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onHttpAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case FlushFW.TYPE_ID:
                flushRO.wrap(buffer, index, index + length);
                break;
            case WindowFW.TYPE_ID:
                onHttpWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onHttpReset(resetRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onHttpBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            replySeq = begin.sequence();
            replyAck = begin.acknowledge();
            state = McpHttpState.openedReply(state);

            final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);
            if (httpBeginEx != null)
            {
                final HttpHeaderFW status =
                    httpBeginEx.headers().matchFirst(h -> HEADER_STATUS.equals(h.name().asString()));
                responseStatus = status != null ? status.value().asString() : null;
                final HttpHeaderFW contentType =
                    httpBeginEx.headers().matchFirst(h -> HEADER_CONTENT_TYPE.equals(h.name().asString()));
                responseContentType = contentType != null ? contentType.value().asString() : DEFAULT_CONTENT_TYPE;
            }

            // every kind streams now, so the reply is opened here, unconditionally, as soon as status and
            // content-type are known — before any response DATA arrives — rather than waiting on a buffered
            // prefix to reach some threshold
            mcp.responseStarted = true;
            mcp.responseBegin(traceId, responseStatus, responseContentType);
        }

        private void onHttpData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            replySeq = data.sequence() + reserved;

            if (payload != null)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(replyId);
                }

                if (decodeSlot == NO_SLOT || decodeSlotOffset + payload.sizeof() > decodePool.slotCapacity())
                {
                    mcp.cleanup(traceId);
                }
                else
                {
                    final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                    slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                    decodeSlotOffset += payload.sizeof();

                    pumpResponse(traceId);
                }
            }
        }

        private void onHttpEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            replySeq = end.sequence();
            state = McpHttpState.closedReply(state);

            pumpResponse(traceId);
        }

        private void onHttpAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            state = McpHttpState.closedReply(state);
            mcp.onHttpAbort(traceId);
            cleanupDecodeSlot();
        }

        private void onHttpWindow(
            WindowFW window)
        {
            initialAck = window.acknowledge();
            initialMax = window.maximum();
            initialPad = window.padding();

            final long traceId = window.traceId();
            flushRequest(traceId);
            resumeRequest(traceId);
            flushMcpWindow(traceId);
        }

        private void onHttpReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            state = McpHttpState.closedInitial(state);
            cleanupEncodeSlot();
            mcp.onHttpAbort(traceId);
        }

        private void doHttpBegin(
            long traceId,
            Map<String, String> headers,
            Map<String, String> credentials,
            String path,
            String contentType)
        {
            final HttpBeginExFW httpBeginEx = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headers(hs ->
                {
                    for (Map.Entry<String, String> entry : headers.entrySet())
                    {
                        final String header = entry.getKey();
                        final String value = HEADER_PATH.equals(header) ? path : entry.getValue();
                        hs.item(h -> h.name(header).value(value));
                    }
                    if (contentType != null)
                    {
                        hs.item(h -> h.name(HEADER_CONTENT_TYPE).value(contentType));
                    }
                    for (Map.Entry<String, String> entry : credentials.entrySet())
                    {
                        hs.item(h -> h.name(entry.getKey()).value(entry.getValue()));
                    }
                })
                .build();

            state = McpHttpState.openingInitial(state);

            receiver = newStream(this::onHttpMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, mcp.authorization, mcp.affinity, httpBeginEx);
        }

        private void doHttpEnd(
            long traceId)
        {
            if (!McpHttpState.initialClosed(state))
            {
                doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, mcp.authorization);
                state = McpHttpState.closedInitial(state);
            }
            cleanupEncodeSlot();
        }

        private void doHttpAbort(
            long traceId)
        {
            if (McpHttpState.initialOpening(state) && !McpHttpState.initialClosed(state))
            {
                doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, mcp.authorization);
                state = McpHttpState.closedInitial(state);
            }
            cleanupEncodeSlot();
        }

        private void doHttpReset(
            long traceId)
        {
            if (McpHttpState.initialOpening(state) && !McpHttpState.replyClosed(state))
            {
                doReset(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, mcp.authorization);
                state = McpHttpState.closedReply(state);
            }
            cleanupDecodeSlot();
        }

        // Triggered whenever McpHttpProxy's reply window changes, since that is what lets pumpResponse drain
        // more of this side's own decodeSlot; the window granted back to the upstream HTTP server reflects
        // only this side's own backlog — decodeSlotOffset is in raw upstream response bytes, the same units
        // as replySeq/replyAck, unlike McpHttpProxy's own reply-direction counters, which are a distinct byte
        // stream once the response envelope/escaping transform has run. max stays pinned to the slot's full
        // physical capacity; ack alone carries the backlog discount, so ack + max lands exactly on the true
        // remaining room (replySeq + (slotCapacity - decodeSlotOffset)) without double-counting the backlog.
        private void flushHttpWindow(
            long traceId,
            long budgetId,
            int padding)
        {
            final long newReplyAck = Math.max(replyAck, replySeq - decodeSlotOffset);
            final int newReplyMax = Math.max(replyMax, decodePool.slotCapacity());

            if (newReplyAck > replyAck || newReplyMax > replyMax || !McpHttpState.replyOpened(state))
            {
                replyAck = newReplyAck;
                replyMax = newReplyMax;
                doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, mcp.authorization, budgetId, padding);
            }
        }

        // Convenience trigger: mcp computes its own window purely from its own decodeSlot backlog, so this
        // only needs to supply the padding to report and re-fire the check.
        private void flushMcpWindow(
            long traceId)
        {
            mcp.flushMcpWindow(traceId, 0L, initialPad);
        }

        private boolean acquireEncodeSlot()
        {
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(initialId);
            }

            return encodeSlot != NO_SLOT;
        }

        // Feeds one input window through the request pipeline, wrapping the generator directly against
        // encodeSlot's own buffer at the live write position — mirroring McpHttpProxy.responseStep's
        // direct-into-encodeSlot pattern.
        private JsonPipeline.Status requestStep(
            JsonGeneratorEx generator,
            JsonPipeline pipeline,
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            JsonPipeline.Status status;
            if (acquireEncodeSlot())
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                generator.wrap(slot, encodeSlotOffset, encodePool.slotCapacity());
                status = pipeline.transform(buffer, offset, offset + length, last);
                encodeSlotOffset += generator.length();
            }
            else
            {
                mcp.cleanup(0L);
                status = JsonPipeline.Status.REJECTED;
            }
            return status;
        }

        private void requestComplete()
        {
            state = McpHttpState.closingInitial(state);
        }

        // Returns the resulting encodeSlotOffset (bytes still queued after this attempt) so callers reacting
        // to a SUSPENDED transform status can tell whether flushing actually freed any room before retrying.
        private int flushRequest(
            long traceId)
        {
            if (receiver != null && !McpHttpState.initialClosed(state))
            {
                if (encodeSlot != NO_SLOT)
                {
                    final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                    int maxPayload = initialMax - (int)(initialSeq - initialAck) - initialPad;
                    while (encodeSlotOffset > 0 && maxPayload > 0)
                    {
                        final int length = Math.min(maxPayload, encodeSlotOffset);
                        final int reserved = length + initialPad;
                        final boolean fin = McpHttpState.initialClosing(state) && length == encodeSlotOffset;
                        final int flags = (requestDataStarted ? 0 : FLAGS_INIT) | (fin ? FLAGS_FIN : 0);
                        doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                            traceId, mcp.authorization, flags, 0L, reserved, slot, 0, length);
                        initialSeq += reserved;
                        requestDataStarted = true;
                        final int remaining = encodeSlotOffset - length;
                        if (remaining > 0)
                        {
                            slot.putBytes(0, slot, length, remaining);
                        }
                        encodeSlotOffset = remaining;
                        maxPayload = initialMax - (int)(initialSeq - initialAck) - initialPad;
                    }
                }

                if (McpHttpState.initialClosing(state) && encodeSlotOffset == 0)
                {
                    cleanupEncodeSlot();
                    doHttpEnd(traceId);
                }
            }

            return encodeSlotOffset;
        }

        private void resumeRequest(
            long traceId)
        {
            if (mcp.requestPumpable())
            {
                mcp.pumpRequest(traceId);
            }
        }

        private void pumpResponse(
            long traceId)
        {
            boolean progress = true;
            while (progress && !mcp.responseDone)
            {
                // responseBegin now runs at onHttpBegin time, before any DATA arrives, so a response that
                // closes with zero body bytes (decodeSlot never acquired) still needs to drive the pipeline to
                // a terminal status with an empty window, rather than dereferencing NO_SLOT
                final MutableDirectBufferEx slot = decodeSlot != NO_SLOT ? decodePool.buffer(decodeSlot) : emptyRequestRO;
                final JsonPipeline.Status status =
                    mcp.responseStep(slot, 0, decodeSlotOffset, McpHttpState.replyClosed(state));
                if (status != JsonPipeline.Status.SUSPENDED)
                {
                    // compact only at a terminal status: across suspend cycles the pipeline re-feeds the same
                    // window, so dropping consumed bytes mid-cycle would corrupt its positioning; here the
                    // window-relative remaining() is the tail to keep, so the consumed prefix is the rest
                    final int consumed = decodeSlotOffset - mcp.responseRemaining();
                    if (consumed > 0 && consumed < decodeSlotOffset)
                    {
                        slot.putBytes(0, slot, consumed, decodeSlotOffset - consumed);
                    }
                    decodeSlotOffset -= consumed;
                }
                switch (status)
                {
                case SUSPENDED:
                    // responseStep's destination is always McpProxy's own encodeSlot (structured or
                    // error-relay mode) — SUSPENDED means it just filled up; flush what's queued and retry
                    // only if that actually freed room (flushReply is bounded by the real mcp client's own
                    // window, which can leave encodeSlot short of full yet still unable to drain any further
                    // until the next onMcpWindow), else stop and wait for that to resume
                    final int beforeFlush = mcp.encodeSlotOffset;
                    if (mcp.flushReply(traceId) >= beforeFlush)
                    {
                        progress = false;
                    }
                    break;
                case STARVED:
                    progress = false;
                    break;
                case COMPLETED:
                    mcp.completeResponse(traceId);
                    progress = false;
                    break;
                case REJECTED:
                    mcp.responseReject(traceId);
                    progress = false;
                    break;
                default:
                    progress = false;
                    break;
                }
            }

            mcp.flushReply(traceId);

            if (mcp.responseDone)
            {
                cleanupDecodeSlot();
            }
            else
            {
                mcp.flushHttpWindow(traceId);
            }
        }

        private void resumeResponse(
            long traceId)
        {
            if (mcp.responseStarted && !mcp.responseDone)
            {
                pumpResponse(traceId);
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

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
            }
        }
    }

    private final class McpSession
    {
        private final String sessionId;
        private final int capabilities;
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long authorization;
        private final long affinity;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private McpSession(
            String sessionId,
            int capabilities,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            this.sessionId = sessionId;
            this.capabilities = capabilities;
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.affinity = affinity;
            sessions.put(sessionId, this);
        }

        private void onMcpMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onMcpBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onMcpData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onMcpEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onMcpAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                onMcpWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onMcpReset(resetRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onMcpBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            state = McpHttpState.openingInitial(state);

            doMcpWindow(traceId);
            doMcpReplyBegin(traceId);
        }

        private void onMcpData(
            DataFW data)
        {
            initialSeq = data.sequence() + data.reserved();
        }

        private void onMcpEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            initialSeq = end.sequence();
            state = McpHttpState.closedInitial(state);
            sessions.remove(sessionId, this);
            doMcpReplyEnd(traceId);
        }

        private void onMcpAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            state = McpHttpState.closedInitial(state);
            sessions.remove(sessionId, this);
            doMcpReplyAbort(traceId);
        }

        private void onMcpWindow(
            WindowFW window)
        {
            replyAck = window.acknowledge();
            replyMax = window.maximum();
        }

        private void onMcpReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            state = McpHttpState.closedReply(state);
            sessions.remove(sessionId, this);
            doMcpReset(traceId);
        }

        private void doMcpWindow(
            long traceId)
        {
            initialMax = WINDOW_MAX;
            initialAck = initialSeq;
            state = McpHttpState.openedInitial(state);
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0);
        }

        private void doMcpReplyBegin(
            long traceId)
        {
            if (!McpHttpState.replyOpened(state))
            {
                final McpBeginExFW lifecycleEx = mcpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .lifecycle(l -> l
                        .sessionId(sessionId)
                        .capabilities(capabilities))
                    .build();
                doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, lifecycleEx);
                state = McpHttpState.openedReply(state);
            }
        }

        private void doMcpReplyEnd(
            long traceId)
        {
            if (!McpHttpState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = McpHttpState.closedReply(state);
            }
        }

        private void doMcpReplyAbort(
            long traceId)
        {
            if (!McpHttpState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = McpHttpState.closedReply(state);
            }
        }

        private void doMcpReset(
            long traceId)
        {
            if (!McpHttpState.initialClosed(state))
            {
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
                state = McpHttpState.closedInitial(state);
            }
        }
    }

    private String sessionId(
        McpBeginExFW beginEx)
    {
        String sessionId = null;
        switch (beginEx.kind())
        {
        case KIND_TOOLS_LIST:
            sessionId = beginEx.toolsList().sessionId().asString();
            break;
        case KIND_TOOLS_CALL:
            sessionId = beginEx.toolsCall().sessionId().asString();
            break;
        case KIND_RESOURCES_LIST:
            sessionId = beginEx.resourcesList().sessionId().asString();
            break;
        case KIND_RESOURCES_TEMPLATES_LIST:
            sessionId = beginEx.resourcesTemplatesList().sessionId().asString();
            break;
        case KIND_RESOURCES_READ:
            sessionId = beginEx.resourcesRead().sessionId().asString();
            break;
        default:
            break;
        }
        return sessionId;
    }

    private String newSessionId(
        long routedId)
    {
        String sessionId = null;
        for (int attempt = 0; attempt < sessionIdAttempts; attempt++)
        {
            final String candidate = supplySessionId.get();
            if (isLocalIndex.test(routedId, candidate.hashCode()))
            {
                sessionId = candidate;
                break;
            }
        }
        return sessionId;
    }

    // Memoizes argReferences(route.with.headers.get(HEADER_PATH)) per route: the result depends only on
    // immutable route config, so re-parsing it on every streaming tools/call stream open is wasted work.
    private List<String> pathArgReferences(
        McpHttpRouteConfig route)
    {
        return routePathArgReferences.computeIfAbsent(route, newPathArgReferencesFn);
    }

    private List<String> newPathArgReferences(
        McpHttpRouteConfig route)
    {
        return argReferences(route.with.headers.get(HEADER_PATH));
    }

    // Memoizes resultReferences(tool.summary) per tool: the result depends only on immutable tool config, so
    // re-parsing the summary template on every tools/call response stream open is wasted work.
    private List<String> toolResultReferences(
        McpHttpToolConfig tool)
    {
        return toolResultReferences.computeIfAbsent(tool, newToolResultReferencesFn);
    }

    private List<String> newToolResultReferences(
        McpHttpToolConfig tool)
    {
        return resultReferences(tool.summary);
    }

    private void appendQuery(
        StringBuilder builder,
        String key,
        String value)
    {
        if (builder.length() > 0)
        {
            builder.append('&');
        }
        builder.append(McpHttpRouteConfig.encode(key)).append('=').append(McpHttpRouteConfig.encode(value));
    }

    private byte[] toolsList(
        McpHttpBindingConfig binding)
    {
        byte[] json = binding.toolsListJson();
        if (json == null)
        {
            json = buildToolsList(binding).getBytes(UTF_8);
            binding.toolsListJson(json);
        }
        return json;
    }

    private byte[] resourcesList(
        McpHttpBindingConfig binding)
    {
        byte[] json = binding.resourcesListJson();
        if (json == null)
        {
            json = buildResourcesList(binding).getBytes(UTF_8);
            binding.resourcesListJson(json);
        }
        return json;
    }

    private byte[] resourcesTemplatesList(
        McpHttpBindingConfig binding)
    {
        byte[] json = binding.resourcesTemplatesListJson();
        if (json == null)
        {
            json = buildResourcesTemplatesList(binding).getBytes(UTF_8);
            binding.resourcesTemplatesListJson(json);
        }
        return json;
    }

    private String buildToolsList(
        McpHttpBindingConfig binding)
    {
        final JsonArrayBuilder tools = Json.createArrayBuilder();
        for (McpHttpToolConfig tool : binding.tools())
        {
            final JsonObjectBuilder item = Json.createObjectBuilder()
                .add("name", tool.name);
            if (tool.description != null)
            {
                item.add("description", tool.description);
            }
            final JsonObject inputSchema = schemaObject(binding, tool.input);
            if (inputSchema != null)
            {
                item.add("inputSchema", inputSchema);
            }
            final JsonObject outputSchema = schemaObject(binding, tool.output);
            if (outputSchema != null)
            {
                item.add("outputSchema", outputSchema);
            }
            final JsonArrayBuilder toolSchemes = securitySchemes(binding.toolGuarded(tool.name));
            if (toolSchemes != null)
            {
                item.add("securitySchemes", toolSchemes);
            }
            tools.add(item);
        }
        return compact(Json.createObjectBuilder().add("tools", tools).build());
    }

    private static JsonArrayBuilder securitySchemes(
        List<GuardedConfig> guarded)
    {
        JsonArrayBuilder schemes = null;
        for (GuardedConfig g : guarded)
        {
            if (!g.roles.isEmpty())
            {
                if (schemes == null)
                {
                    schemes = Json.createArrayBuilder();
                }
                final JsonArrayBuilder scopes = Json.createArrayBuilder();
                for (String role : g.roles)
                {
                    scopes.add(role);
                }
                schemes.add(Json.createObjectBuilder()
                    .add("type", "oauth2")
                    .add("scopes", scopes));
            }
        }
        return schemes;
    }

    private String buildResourcesList(
        McpHttpBindingConfig binding)
    {
        final JsonArrayBuilder resources = Json.createArrayBuilder();
        for (McpHttpResourceConfig resource : binding.resources())
        {
            if (resource.template)
            {
                continue;
            }
            final JsonObjectBuilder item = Json.createObjectBuilder()
                .add("name", resource.name);
            if (resource.uri != null)
            {
                item.add("uri", resource.uri);
            }
            if (resource.description != null)
            {
                item.add("description", resource.description);
            }
            if (resource.mimeType != null)
            {
                item.add("mimeType", resource.mimeType);
            }
            final JsonArrayBuilder resourceSchemes = securitySchemes(binding.resourceGuarded(resource.name));
            if (resourceSchemes != null)
            {
                item.add("securitySchemes", resourceSchemes);
            }
            resources.add(item);
        }
        return compact(Json.createObjectBuilder().add("resources", resources).build());
    }

    private String buildResourcesTemplatesList(
        McpHttpBindingConfig binding)
    {
        final JsonArrayBuilder resourceTemplates = Json.createArrayBuilder();
        for (McpHttpResourceConfig resource : binding.resources())
        {
            if (!resource.template)
            {
                continue;
            }
            final JsonObjectBuilder item = Json.createObjectBuilder()
                .add("name", resource.name)
                .add("uriTemplate", resource.uri);
            if (resource.description != null)
            {
                item.add("description", resource.description);
            }
            if (resource.mimeType != null)
            {
                item.add("mimeType", resource.mimeType);
            }
            final JsonArrayBuilder templateSchemes = securitySchemes(binding.resourceGuarded(resource.name));
            if (templateSchemes != null)
            {
                item.add("securitySchemes", templateSchemes);
            }
            resourceTemplates.add(item);
        }
        return compact(Json.createObjectBuilder().add("resourceTemplates", resourceTemplates).build());
    }

    private JsonObject schemaObject(
        McpHttpBindingConfig binding,
        ModelConfig model)
    {
        final String text = model != null ? binding.schemaText(model) : null;
        return text != null ? parseObject(text) : null;
    }

    private static JsonObject parseObject(
        String text)
    {
        JsonObject object = null;
        if (text != null && !text.isEmpty())
        {
            try (JsonReader reader = Json.createReader(new StringReader(text)))
            {
                final JsonValue value = reader.readValue();
                if (value.getValueType() == JsonValue.ValueType.OBJECT)
                {
                    object = value.asJsonObject();
                }
            }
        }
        return object;
    }

    private static String compact(
        JsonStructure value)
    {
        final StringWriter writer = new StringWriter();
        try (JsonWriter json = Json.createWriter(writer))
        {
            json.write(value);
        }
        return writer.toString();
    }

    private static String interpolate(
        String template,
        Function<String, String> resolver)
    {
        String result = template;

        if (template != null && template.contains("${"))
        {
            final StringBuilder builder = new StringBuilder();
            int index = 0;
            while (index < template.length())
            {
                final int start = template.indexOf("${", index);
                if (start < 0)
                {
                    builder.append(template, index, template.length());
                    index = template.length();
                }
                else
                {
                    builder.append(template, index, start);
                    final int end = template.indexOf('}', start + 2);
                    if (end < 0)
                    {
                        builder.append(template, start, template.length());
                        index = template.length();
                    }
                    else
                    {
                        final String expression = template.substring(start + 2, end);
                        final String value = resolver.apply(expression);
                        builder.append(value != null ? value : "");
                        index = end + 1;
                    }
                }
            }
            result = builder.toString();
        }

        return result;
    }

    // ASCII input (the common case for tool args, ids, route params) is percent-encoded by iterating
    // chars directly, skipping the UTF-8 byte conversion the general case requires below: a single-byte
    private static int put(
        MutableDirectBufferEx buffer,
        int offset,
        byte[] bytes)
    {
        buffer.putBytes(offset, bytes);
        return offset + bytes.length;
    }

    // Writes the UTF-8 of value as a quoted, JSON-escaped string into buffer at offset, returning the new
    // offset. value is encoded once into a wrapped byte view, then escaped byte-wise — equivalent to escaping
    // each char and UTF-8 encoding, since the escaped set and control bytes are all single-byte ASCII.
    private int putJsonString(
        MutableDirectBufferEx buffer,
        int offset,
        String value)
    {
        int progress = offset;
        buffer.putByte(progress++, (byte) '"');
        if (value != null && !value.isEmpty())
        {
            final byte[] bytes = value.getBytes(UTF_8);
            escapeRO.wrap(bytes);
            progress = putEscaped(buffer, progress, escapeRO, 0, bytes.length);
        }
        buffer.putByte(progress++, (byte) '"');
        return progress;
    }

    // Writes the bytes of source[sourceOffset..+length] as a quoted, JSON-escaped string into buffer at
    // offset, returning the new offset — used to splice already-UTF-8 projected content (a resource body or
    // structuredContent) as a JSON string value without materializing an intermediate String.
    private int putJsonString(
        MutableDirectBufferEx buffer,
        int offset,
        DirectBufferEx source,
        int sourceOffset,
        int length)
    {
        int progress = offset;
        buffer.putByte(progress++, (byte) '"');
        progress = putEscaped(buffer, progress, source, sourceOffset, length);
        buffer.putByte(progress++, (byte) '"');
        return progress;
    }

    private static int putEscaped(
        MutableDirectBufferEx buffer,
        int offset,
        DirectBufferEx source,
        int sourceOffset,
        int length)
    {
        int progress = offset;
        for (int index = 0; index < length; index++)
        {
            final int c = source.getByte(sourceOffset + index) & 0xff;
            switch (c)
            {
            case '"':
                buffer.putByte(progress++, (byte) '\\');
                buffer.putByte(progress++, (byte) '"');
                break;
            case '\\':
                buffer.putByte(progress++, (byte) '\\');
                buffer.putByte(progress++, (byte) '\\');
                break;
            case '\n':
                buffer.putByte(progress++, (byte) '\\');
                buffer.putByte(progress++, (byte) 'n');
                break;
            case '\r':
                buffer.putByte(progress++, (byte) '\\');
                buffer.putByte(progress++, (byte) 'r');
                break;
            case '\t':
                buffer.putByte(progress++, (byte) '\\');
                buffer.putByte(progress++, (byte) 't');
                break;
            case '\b':
                buffer.putByte(progress++, (byte) '\\');
                buffer.putByte(progress++, (byte) 'b');
                break;
            case '\f':
                buffer.putByte(progress++, (byte) '\\');
                buffer.putByte(progress++, (byte) 'f');
                break;
            default:
                if (c < 0x20)
                {
                    buffer.putByte(progress++, (byte) '\\');
                    buffer.putByte(progress++, (byte) 'u');
                    buffer.putByte(progress++, (byte) '0');
                    buffer.putByte(progress++, (byte) '0');
                    buffer.putByte(progress++, hex(c >> 4));
                    buffer.putByte(progress++, hex(c & 0xf));
                }
                else
                {
                    buffer.putByte(progress++, (byte) c);
                }
                break;
            }
        }
        return progress;
    }

    private static byte hex(
        int nibble)
    {
        return (byte) (nibble < 10 ? '0' + nibble : 'a' + nibble - 10);
    }

    private static List<String> argReferences(
        String template)
    {
        final List<String> result = new ArrayList<>();
        if (template != null)
        {
            int index = 0;
            int start = template.indexOf("${args.", index);
            while (start >= 0)
            {
                final int end = template.indexOf('}', start);
                if (end < 0)
                {
                    break;
                }
                result.add(template.substring(start + 7, end));
                index = end + 1;
                start = template.indexOf("${args.", index);
            }
        }
        return result;
    }

    // Extracts the result.<path> references from a tool.summary template (e.g. "result.number" from
    // "Created pull request #${result.number}"), the set McpHttpResults is asked to capture as the response
    // streams past.
    private static List<String> resultReferences(
        String template)
    {
        final List<String> result = new ArrayList<>();
        if (template != null)
        {
            int index = 0;
            int start = template.indexOf("${result.", index);
            while (start >= 0)
            {
                final int end = template.indexOf('}', start);
                if (end < 0)
                {
                    break;
                }
                result.add(template.substring(start + 9, end));
                index = end + 1;
                start = template.indexOf("${result.", index);
            }
        }
        return result;
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
        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

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
        DirectBufferEx buffer,
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
            .payload(buffer, offset, length)
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
}
