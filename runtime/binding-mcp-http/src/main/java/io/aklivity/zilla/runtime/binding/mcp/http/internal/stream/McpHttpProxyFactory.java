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
import static io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpBeginExFW.KIND_PROMPTS_GET;
import static io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpBeginExFW.KIND_RESOURCES_READ;
import static io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpBeginExFW.KIND_TOOLS_CALL;
import static io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
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

import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpPromptArgumentConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpPromptConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpPromptMessageConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpResourceConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpToolConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.McpHttpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.config.McpHttpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.config.McpHttpRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.events.McpHttpEventContext;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.transform.McpHttpArguments;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.transform.McpHttpRename;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.transform.McpHttpResults;
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
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonStream;
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
    private static final String DEFAULT_CONTENT_TYPE = "application/json";

    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_COMPLETE = 0x03;
    private static final int WINDOW_MAX = 65536;
    private static final int JSON_RPC_INVALID_PARAMS = -32602;
    private static final int JSON_RPC_INTERNAL_ERROR = -32603;
    private static final int RESPONSE_WINDOW = 1024;
    // bounds the request-side templating scratch write into projectBuffer (see pumpRequest) and doubles as
    // the "is it worth attempting a write, or should I flush first" threshold for both directions' encode
    // slots (see the two encodeHasRoom() methods). The response side's generator itself still wraps directly
    // against encodeSlot's real remaining capacity (see responseStep) — this constant only gates when to
    // flush, not how much a single write may produce.
    private static final int MIN_ENCODE_ROOM = 1024;

    private static final byte[] REPLY_SUFFIX = "\"}]}".getBytes(UTF_8);
    // tools/call success streams structuredContent first (as it arrives from upstream) and only appends the
    // content/summary once the whole response has passed the McpHttpResults capture stage and completed —
    // see McpToolsCallProxy.doEncodeResponseSuffix for why the envelope is ordered this way
    private static final byte[] TOOL_RESULT_PREFIX = "{\"structuredContent\":".getBytes(UTF_8);
    private static final byte[] TOOL_RESULT_INFIX = ",\"content\":[{\"type\":\"text\",\"text\":".getBytes(UTF_8);
    private static final byte[] TOOL_RESULT_SUFFIX = "}],\"isError\":false}".getBytes(UTF_8);
    private static final byte[] TOOL_ERROR_PREFIX = "{\"content\":[{\"type\":\"text\",\"text\":\"".getBytes(UTF_8);
    private static final byte[] TOOL_ERROR_SUFFIX = "\"}],\"isError\":true}".getBytes(UTF_8);
    private static final byte[] EMPTY_OBJECT = "{}".getBytes(UTF_8);
    private static final byte[] RESOURCE_PREFIX = "{\"contents\":[{\"uri\":".getBytes(UTF_8);
    private static final byte[] RESOURCE_MIME = ",\"mimeType\":".getBytes(UTF_8);
    private static final byte[] RESOURCE_TEXT_OPEN = ",\"text\":\"".getBytes(UTF_8);
    private static final byte[] PROMPT_DESCRIPTION = "{\"description\":".getBytes(UTF_8);
    private static final byte[] PROMPT_MESSAGES = ",\"messages\":[".getBytes(UTF_8);
    private static final byte[] PROMPT_MESSAGES_OPEN = "{\"messages\":[".getBytes(UTF_8);
    private static final byte[] PROMPT_MESSAGE_ROLE = "{\"role\":".getBytes(UTF_8);
    private static final byte[] PROMPT_MESSAGE_CONTENT = ",\"content\":{\"type\":\"text\",\"text\":".getBytes(UTF_8);
    private static final byte[] PROMPT_MESSAGE_END = "}}".getBytes(UTF_8);
    private static final byte[] PROMPT_SUFFIX = "]}".getBytes(UTF_8);
    private static final byte[] COMMA = ",".getBytes(UTF_8);

    private static final Map<String, String> EMPTY_PARAMS = Map.of();
    private static final Map<String, Object> SINK_STRUCTURED = Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED);
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

    private final JsonGeneratorEx projectGenerator;
    private final JsonParserEx queryParser = JsonEx.createParser();
    private final MutableDirectBufferEx projectBuffer;
    private final UnsafeBufferEx escapeRO = new UnsafeBufferEx(new byte[0]);
    private final UnsafeBufferEx emptyRequestRO = new UnsafeBufferEx(new byte[0]);
    private final UnsafeBufferEx argsRO;
    private final Map<String, String> argsCaptured = new HashMap<>();
    private final JsonGeneratorEx argsGenerator;
    private final MutableDirectBufferEx argsBuffer;
    private final JsonPipeline argsPipeline;
    private final Map<JsonSchema, JsonPipeline> projectors;
    private final Map<JsonSchema, JsonPipeline> validators;
    private final Map<McpHttpRouteConfig, JsonPipeline> templates;
    private final Map<McpHttpRouteConfig, List<String>> routePathArgReferences;
    private final Map<McpHttpToolConfig, List<String>> toolResultReferences;

    // hoisted to avoid reallocating a capturing method-reference object on every computeIfAbsent call
    private final Function<JsonSchema, JsonPipeline> newProjectorFn = this::newProjector;
    private final Function<JsonSchema, JsonPipeline> newValidatorFn = this::newValidator;
    private final Function<McpHttpRouteConfig, JsonPipeline> newTemplateFn = this::newTemplate;
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
        this.projectGenerator = JsonEx.createGenerator();
        this.projectBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.argsRO = new UnsafeBufferEx(new byte[0]);
        this.argsGenerator = JsonEx.createGenerator();
        this.argsBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.argsPipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new McpHttpArguments(argsCaptured))
            .into(JsonEx.createSink(argsGenerator, SINK_STRUCTURED));
        this.projectors = new IdentityHashMap<>();
        this.validators = new IdentityHashMap<>();
        this.templates = new IdentityHashMap<>();
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
                    case KIND_PROMPTS_LIST:
                        newStream = new McpPromptsListProxy(binding,
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
                    case KIND_PROMPTS_GET:
                    {
                        final String name = beginEx.promptsGet().name().asString();
                        final int contentLength = beginEx.promptsGet().contentLength();
                        final McpHttpPromptConfig prompt = binding.prompt(name);
                        if (prompt != null)
                        {
                            newStream = new McpPromptsGetProxy(binding, name, contentLength,
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

        // The buffered kinds (listings, prompts/get) reply immediately when no request body is expected;
        // the HTTP-backed kinds override this to await the request body before shaping the upstream request.
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

        void onMcpData(
            DataFW data)
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

                if (decodeSlot == NO_SLOT)
                {
                    cleanup(traceId);
                }
                else
                {
                    final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                    slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                    decodeSlotOffset += payload.sizeof();
                }
            }

            if (payload != null && (contentLength < 0 || decodeSlotOffset < contentLength))
            {
                doMcpWindow(traceId);
            }

            if (!requestHandled && contentLength >= 0 && decodeSlotOffset >= contentLength)
            {
                onMcpRequest(traceId);
            }
        }

        void onMcpEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            initialSeq = end.sequence();
            state = McpHttpState.closedInitial(state);

            if (!requestHandled)
            {
                onMcpRequest(traceId);
            }
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

        void flushReply(
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
                    cleanupEncodeSlot();
                }
            }
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

        void doMcpReset(
            long traceId)
        {
            if (!McpHttpState.initialClosed(state))
            {
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
                state = McpHttpState.closedInitial(state);
            }
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
        }

        void cleanup(
            long traceId)
        {
            doMcpReset(traceId);
            doMcpAbort(traceId);
            cleanupDecodeSlot();
            cleanupEncodeSlot();
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

        JsonPipeline requestPipeline;
        JsonGeneratorEx requestGenerator;
        Map<String, String> requestArgs;
        List<String> requestPathArgs;
        boolean requestProjected;

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
        void onMcpBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            state = McpHttpState.openingInitial(state);

            doMcpWindow(traceId);
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
        }

        // The tool config for tools/call, or null for resources/read which has no input schema.
        McpHttpToolConfig tool()
        {
            return null;
        }

        @Override
        void onMcpRequest(
            long traceId)
        {
            requestHandled = true;

            final McpHttpToolConfig tool = tool();
            final McpHttpWithConfig with = route.with;

            final boolean needArgs = tool != null && tool.input != null ||
                with.query != null || with.body != null || with.bodyTemplate != null;
            final int argsLength = needArgs
                ? decodeSlot != NO_SLOT ? extractArgs(decodePool.buffer(decodeSlot), 0, decodeSlotOffset) : emptyArgs()
                : 0;

            if (tool != null && tool.input != null)
            {
                argsRO.wrap(argsBuffer, 0, argsLength);
                if (!validate(binding.jsonSchema(tool.input), argsRO, 0, argsLength))
                {
                    doMcpReset(traceId, JSON_RPC_INVALID_PARAMS, "Invalid params");
                    cleanupDecodeSlot();
                    return;
                }
            }

            final List<String> unsatisfied = binding.unsatisfiedAccessors(route);
            if (!unsatisfied.isEmpty())
            {
                final String accessor = unsatisfied.get(0);
                events.schemaAccessorUnresolved(traceId, binding.id, name != null ? name : uri, accessor);
                doMcpReset(traceId, JSON_RPC_INTERNAL_ERROR, "unresolved expression: ${" + accessor + "}");
                cleanupDecodeSlot();
                return;
            }

            String path = interpolate(with.headers.get(HEADER_PATH), expr -> resolveRequest(argsLength, expr));

            if (with.query != null)
            {
                argsRO.wrap(argsBuffer, 0, argsLength);
                final int produced = projectInto(binding.jsonSchema(with.query), argsRO, 0, argsLength);
                final String query = produced >= 0 ? queryStringFromBytes(projectBuffer, 0, produced) : "";
                if (!query.isEmpty())
                {
                    path = path + "?" + query;
                }
            }

            int bodyLength = -1;
            String contentType = null;
            if (with.bodyTemplate != null)
            {
                argsRO.wrap(argsBuffer, 0, argsLength);
                bodyLength = templateInto(route, argsRO, 0, argsLength);
                contentType = DEFAULT_CONTENT_TYPE;
            }
            else if (with.body != null)
            {
                argsRO.wrap(argsBuffer, 0, argsLength);
                bodyLength = projectInto(binding.jsonSchema(with.body), argsRO, 0, argsLength);
                contentType = DEFAULT_CONTENT_TYPE;
            }

            credentials.clear();
            binding.resolveCredentials(authorization, credentials);

            delegate.doHttpBegin(traceId, with.headers, credentials, path, contentType);
            if (contentType != null)
            {
                if (bodyLength < 0)
                {
                    projectBuffer.putBytes(0, EMPTY_OBJECT);
                    bodyLength = EMPTY_OBJECT.length;
                }
                if (bodyLength > 0)
                {
                    delegate.doEncodeRequestBody(traceId, projectBuffer, 0, bodyLength);
                }
            }
            delegate.requestComplete();
            delegate.flushRequest(traceId);

            cleanupDecodeSlot();
        }

        private String resolveRequest(
            int argsLength,
            String expression)
        {
            String value = "";
            if (expression.startsWith("args."))
            {
                value = encode(navigateBytes(argsBuffer, argsLength, expression.substring(5)));
            }
            else if (expression.startsWith("params."))
            {
                final String captured = params.get(expression.substring(7));
                value = encode(captured != null ? captured : "");
            }
            return value;
        }

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
                if (!delegate.encodeHasRoom())
                {
                    delegate.flushRequest(traceId);
                    if (!delegate.encodeHasRoom())
                    {
                        progress = false;
                        continue;
                    }
                }

                final DirectBufferEx buffer = decodeSlot != NO_SLOT ? decodePool.buffer(decodeSlot) : emptyRequestRO;
                requestGenerator.wrap(projectBuffer, 0, MIN_ENCODE_ROOM);
                final JsonPipeline.Status status = requestPipeline.transform(
                    buffer, 0, decodeSlotOffset, McpHttpState.initialClosed(state));
                final int produced = requestGenerator.length();
                if (produced > 0)
                {
                    delegate.doEncodeRequestBody(traceId, projectBuffer, 0, produced);
                }

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

            if (!McpHttpState.initialClosed(state) && !McpHttpState.initialOpening(delegate.state) && requestPathReady())
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
                grantMcpWindow(traceId);
            }
        }

        private boolean requestPathReady()
        {
            return requestArgs.keySet().containsAll(requestPathArgs);
        }

        private void sendRequestBegin(
            long traceId)
        {
            final McpHttpWithConfig with = route.with;
            final String path = interpolate(with.headers.get(HEADER_PATH), this::resolveStreamingRequest);
            credentials.clear();
            binding.resolveCredentials(authorization, credentials);
            delegate.doHttpBegin(traceId, with.headers, credentials, path, DEFAULT_CONTENT_TYPE);
        }

        private String resolveStreamingRequest(
            String expression)
        {
            String value = "";
            if (expression.startsWith("args."))
            {
                final String captured = requestArgs.get(expression.substring(5));
                value = encode(captured != null ? captured : "");
            }
            else if (expression.startsWith("params."))
            {
                final String captured = params.get(expression.substring(7));
                value = encode(captured != null ? captured : "");
            }
            return value;
        }

        void grantMcpWindow(
            long traceId)
        {
            initialAck = initialSeq - decodeSlotOffset;
            initialMax = decodeSlotOffset + (delegate.encodeHasRoom() ? RESPONSE_WINDOW : 0);
            state = McpHttpState.openedInitial(state);
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0);
        }

        void onHttpAbort(
            long traceId)
        {
            cleanupResponse();
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
            doEncodeResponseSuffix();
            state = McpHttpState.closingReply(state);
            flushReply(traceId);
        }

        // Writes the reply's closing bytes once the streamed body completes; the resources/read shape (the
        // only base-level user) closes the escaped text value and the surrounding contents envelope. Overridden
        // by tools/call, whose envelope shape depends on which of its two response modes (success/error) ran.
        void doEncodeResponseSuffix()
        {
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
        boolean encodeHasRoom()
        {
            return encodeFree() >= MIN_ENCODE_ROOM;
        }

        private int encodeFree()
        {
            return encodePool.slotCapacity() - encodeSlotOffset;
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

            if (route.with.body != null && route.with.bodyTemplate == null)
            {
                requestArgs = new HashMap<>();
                requestPathArgs = pathArgReferences(route);
                requestGenerator = JsonEx.createGenerator();

                final McpHttpToolConfig tool = tool();
                JsonStream stream = JsonEx.stream(JsonEx.createParser())
                    .transform(new McpHttpArguments(requestArgs));
                if (tool != null && tool.input != null &&
                    contentLength >= 0 && contentLength <= decodePool.slotCapacity())
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
                requestPipeline = stream
                    .transform(JsonEx.projector(binding.jsonSchema(route.with.body)))
                    .into(JsonEx.createSink(requestGenerator, SINK_SEGMENTABLE));
                requestPipeline.reset();
                grantMcpWindow(traceId);
            }
            else
            {
                doMcpWindow(traceId);
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

            if (requestPipeline != null)
            {
                pumpRequest(traceId);
            }
            else if (!requestHandled)
            {
                onMcpRequest(traceId);
            }
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
                final List<String> resultPaths = tool != null ? toolResultReferences(tool) : List.of();

                responseGenerator = JsonEx.createGenerator();
                JsonStream stream = JsonEx.stream(JsonEx.createParser());
                if (outputSchema != null)
                {
                    // validate against the full (unprojected) document before pruning it down, matching the
                    // pre-streaming validate-then-project order: a value the schema rejects must never reach
                    // structuredContent even if the retained paths alone would otherwise look fine
                    stream = stream.transform(outputSchema.validator()).transform(JsonEx.projector(outputSchema));
                }
                // captures result.* references from exactly the events reaching the sink (i.e. after any
                // projection), matching the pre-streaming behavior of scanning the already-projected buffer
                responsePipeline = stream
                    .transform(new McpHttpResults(capturedResults, resultPaths))
                    .into(JsonEx.createSink(responseGenerator, SINK_SEGMENTABLE));
                responsePipeline.reset();

                doEncodeReply(TOOL_RESULT_PREFIX);
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
        // streamed past, replacing the old re-scan of a fully buffered projectBuffer copy.
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

        @Override
        void doEncodeResponseSuffix()
        {
            if (errorRelay)
            {
                doEncodeReply(TOOL_ERROR_SUFFIX);
            }
            else
            {
                // structuredContent has already streamed by the time this runs (completeResponse only calls
                // it once the pipeline reaches COMPLETED), so every result.* reference tool.summary needs is
                // available in capturedResults now. Writing content after structuredContent — reversing the
                // pre-streaming field order — is what makes this possible without buffering the response: the
                // MCP client is a JSON-RPC/JSON reader, not a wire format with a fixed field order, and this
                // repo's own binding-mcp client (McpClientFactory's decodeJsonRpcSkipObject) already reads a
                // tools/call result generically, scanning for "isError" by key name wherever it falls rather
                // than assuming a position, confirming member order is not load-bearing on the receiving side.
                // When no summary is configured, tool.summary interpolates to null and content.text becomes
                // the empty string rather than mirroring the (potentially unbounded) structuredContent a
                // second time as escaped text — doing that would mean buffering the whole response again after
                // having just finished streaming it once, defeating the point of this change.
                final McpHttpToolConfig tool = binding.tool(name);
                final String summary = tool != null ? tool.summary : null;
                doEncodeReply(TOOL_RESULT_INFIX);
                doEncodeReplyJsonString(interpolate(summary, this::resolveCapturedResult));
                doEncodeReply(TOOL_RESULT_SUFFIX);
            }
        }

        // A schema-validation failure discovered while streaming structuredContent used to always produce a
        // proper {"content":...,"isError":true} reply (the pre-streaming buffered path always had, since it
        // never committed to a reply shape until the whole body had already been validated). Once the response
        // genuinely streams, TOOL_RESULT_PREFIX has necessarily already gone out — even a validator that
        // rejects on the very first field only reaches that verdict on a later pump cycle (e.g. once the
        // response body's own end has been seen), and pumpResponse flushes whatever is pending after every
        // cycle regardless of status, so by the time REJECTED is known the prefix is already on the wire. There
        // is no way back at that point, so this falls through to the inherited abort behavior (the same
        // fallback resources/read and the error-relay path already use) rather than attempting a
        // sometimes-possible, sometimes-not recovery that would depend on upstream framing details a client
        // cannot rely on.
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
            }
            else
            {
                final JsonSchema schema = resource != null ? binding.jsonSchema(resource.output) : null;
                responseGenerator = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true));
                responsePipeline = schema != null
                    ? JsonEx.stream(JsonEx.createParser())
                        .transform(JsonEx.projector(schema))
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

    private final class McpPromptsListProxy extends McpProxy
    {
        private McpPromptsListProxy(
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
            doMcpReply(traceId, promptsList(binding));
            cleanupDecodeSlot();
        }
    }

    private final class McpPromptsGetProxy extends McpProxy
    {
        private McpPromptsGetProxy(
            McpHttpBindingConfig binding,
            String name,
            int contentLength,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            super(binding, null, name, null, EMPTY_PARAMS, contentLength, sender, originId, routedId, initialId,
                authorization, affinity);
        }

        @Override
        void onMcpRequest(
            long traceId)
        {
            requestHandled = true;
            final int argsLength = decodeSlot != NO_SLOT
                ? extractArgs(decodePool.buffer(decodeSlot), 0, decodeSlotOffset)
                : emptyArgs();
            doEncodePromptGet(binding.prompt(name), argsLength);
            completeReply(traceId);
            cleanupDecodeSlot();
        }

        // Assembles {"description":<d>,"messages":[{"role":<r>,"content":{"type":"text","text":<t>}},...]}
        // directly into encodeSlot (description optional), interpolating each message text into the escaped
        // value directly — replaces the per-request jakarta DOM + compact round-trip.
        private void doEncodePromptGet(
            McpHttpPromptConfig prompt,
            int argsLength)
        {
            if (prompt.description != null)
            {
                doEncodeReply(PROMPT_DESCRIPTION);
                doEncodeReplyJsonString(prompt.description);
                doEncodeReply(PROMPT_MESSAGES);
            }
            else
            {
                doEncodeReply(PROMPT_MESSAGES_OPEN);
            }
            boolean first = true;
            for (McpHttpPromptMessageConfig message : prompt.messages)
            {
                if (!first)
                {
                    doEncodeReply(COMMA);
                }
                first = false;
                final String text = interpolate(message.text,
                    expr -> expr.startsWith("args.") ? navigateBytes(argsBuffer, argsLength, expr.substring(5)) : "");
                doEncodeReply(PROMPT_MESSAGE_ROLE);
                doEncodeReplyJsonString(message.role);
                doEncodeReply(PROMPT_MESSAGE_CONTENT);
                doEncodeReplyJsonString(text);
                doEncodeReply(PROMPT_MESSAGE_END);
            }
            doEncodeReply(PROMPT_SUFFIX);
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
        private int encodeSlotOffset;
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

            doHttpReplyWindow(traceId);
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
        }

        private void doHttpReplyWindow(
            long traceId)
        {
            replyAck = replySeq - decodeSlotOffset;
            replyMax = decodePool.slotCapacity();
            doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, mcp.authorization, 0L, 0);
        }

        private void grantHttpReplyWindow(
            long traceId)
        {
            if (!McpHttpState.replyClosed(state))
            {
                replyAck = replySeq - decodeSlotOffset;
                replyMax = decodeSlotOffset + (mcp.encodeHasRoom() ? RESPONSE_WINDOW : 0);
                doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, mcp.authorization, 0L, 0);
            }
        }

        private void doEncodeRequestBody(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(initialId);
            }

            if (encodeSlot == NO_SLOT || encodeSlotOffset + length > encodePool.slotCapacity())
            {
                mcp.cleanup(traceId);
            }
            else
            {
                encodePool.buffer(encodeSlot).putBytes(encodeSlotOffset, buffer, offset, length);
                encodeSlotOffset += length;
            }
        }

        private boolean encodeHasRoom()
        {
            return encodePool.slotCapacity() - encodeSlotOffset >= MIN_ENCODE_ROOM;
        }

        private void requestComplete()
        {
            state = McpHttpState.closingInitial(state);
        }

        private void flushRequest(
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
        }

        private void resumeRequest(
            long traceId)
        {
            if (mcp.requestPipeline != null && !mcp.requestProjected)
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
                if (!mcp.encodeHasRoom())
                {
                    mcp.flushReply(traceId);
                    if (!mcp.encodeHasRoom())
                    {
                        progress = false;
                        continue;
                    }
                }

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
                grantHttpReplyWindow(traceId);
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
        case KIND_RESOURCES_READ:
            sessionId = beginEx.resourcesRead().sessionId().asString();
            break;
        case KIND_PROMPTS_LIST:
            sessionId = beginEx.promptsList().sessionId().asString();
            break;
        case KIND_PROMPTS_GET:
            sessionId = beginEx.promptsGet().sessionId().asString();
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

    private boolean validate(
        JsonSchema schema,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        return runInto(validators.computeIfAbsent(schema, newValidatorFn), buffer, offset, length) >= 0;
    }

    private int projectInto(
        JsonSchema schema,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        return runInto(projectors.computeIfAbsent(schema, newProjectorFn), buffer, offset, length);
    }

    private int templateInto(
        McpHttpRouteConfig route,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        return runInto(templates.computeIfAbsent(route, newTemplateFn), buffer, offset, length);
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

    // Runs a pipeline over the input window into projectBuffer, returning the bytes produced, or -1 when the
    // value did not complete. The output stays in projectBuffer so the caller can stream it onward (a request
    // body) or walk it (a query object) without materializing an intermediate String.
    private int runInto(
        JsonPipeline pipeline,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        projectGenerator.wrap(projectBuffer, 0, projectBuffer.capacity());
        pipeline.reset();

        final JsonPipeline.Status status = pipeline.transform(buffer, offset, offset + length);

        return status == JsonPipeline.Status.COMPLETED ? projectGenerator.length() : -1;
    }

    // Re-roots the request to its arguments object in argsBuffer, returning the bytes produced (the empty
    // object when no arguments are present), by streaming it through the re-rooting McpHttpArguments stage
    // rather than materializing the request as a jakarta JsonObject tree.
    private int extractArgs(
        DirectBufferEx request,
        int offset,
        int length)
    {
        argsGenerator.wrap(argsBuffer, 0, argsBuffer.capacity());
        argsCaptured.clear();
        argsPipeline.reset();

        final JsonPipeline.Status status = argsPipeline.transform(request, offset, offset + length);
        final int produced = status == JsonPipeline.Status.COMPLETED ? argsGenerator.length() : 0;

        return produced > 0 ? produced : emptyArgs();
    }

    private int emptyArgs()
    {
        argsBuffer.putBytes(0, EMPTY_OBJECT);
        return EMPTY_OBJECT.length;
    }

    // Resolves a dotted path within the arguments object held in buffer, returning the scalar value as text or
    // an empty string when the path is absent or addresses a container, by walking it as a streaming event run
    // rather than navigating a jakarta JsonObject tree.
    private String navigateBytes(
        DirectBufferEx buffer,
        int length,
        String path)
    {
        final String[] segments = path.split("\\.");
        queryParser.reset();
        queryParser.wrap(buffer, 0, length, true);

        int depth = 0;
        int matched = 0;
        boolean awaitingValue = false;
        String result = "";
        boolean done = false;
        while (!done && queryParser.hasNextEvent())
        {
            final JsonEvent event = queryParser.nextEvent();
            if (awaitingValue)
            {
                result = scalarText(event);
                done = true;
            }
            else
            {
                switch (event)
                {
                case START_OBJECT:
                case START_ARRAY:
                    depth++;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    depth--;
                    break;
                case KEY_NAME:
                    if (depth == matched + 1 && segments[matched].contentEquals(queryParser.getStringView()))
                    {
                        matched++;
                        awaitingValue = matched == segments.length;
                    }
                    break;
                default:
                    break;
                }
            }
        }
        return result;
    }

    private String scalarText(
        JsonEvent event)
    {
        String result;
        switch (event)
        {
        case VALUE_STRING:
        case VALUE_NUMBER:
            result = queryParser.getString();
            break;
        case VALUE_TRUE:
            result = "true";
            break;
        case VALUE_FALSE:
            result = "false";
            break;
        default:
            result = "";
            break;
        }
        return result;
    }

    // Builds an application/x-www-form-urlencoded query string from a projected query object held in buffer,
    // walking it as a streaming event run rather than materializing a JSON object tree. Top-level scalar
    // members become key=value pairs; a member whose value is an array repeats the key per element.
    private String queryStringFromBytes(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        final StringBuilder builder = new StringBuilder();
        queryParser.reset();
        queryParser.wrap(buffer, offset, offset + length, true);

        String key = null;
        int depth = 0;
        boolean inArray = false;
        while (queryParser.hasNextEvent())
        {
            final JsonEvent event = queryParser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                depth++;
                break;
            case START_ARRAY:
                depth++;
                inArray = depth == 2;
                break;
            case END_OBJECT:
                depth--;
                break;
            case END_ARRAY:
                depth--;
                inArray = false;
                break;
            case KEY_NAME:
                if (depth == 1)
                {
                    key = queryParser.getString();
                }
                break;
            case VALUE_STRING:
            case VALUE_NUMBER:
                if (key != null && (depth == 1 || depth == 2 && inArray))
                {
                    appendQuery(builder, key, queryParser.getString());
                }
                break;
            case VALUE_TRUE:
                if (key != null && (depth == 1 || depth == 2 && inArray))
                {
                    appendQuery(builder, key, "true");
                }
                break;
            case VALUE_FALSE:
                if (key != null && (depth == 1 || depth == 2 && inArray))
                {
                    appendQuery(builder, key, "false");
                }
                break;
            default:
                break;
            }
        }
        return builder.toString();
    }

    private JsonPipeline newValidator(
        JsonSchema schema)
    {
        return JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .into(JsonEx.createSink(projectGenerator));
    }

    private JsonPipeline newProjector(
        JsonSchema schema)
    {
        return JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(schema))
            .into(JsonEx.createSink(projectGenerator));
    }

    private JsonPipeline newTemplate(
        McpHttpRouteConfig route)
    {
        return JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(route.bodyTemplatePointers))
            .transform(new McpHttpRename(route.bodyTemplateRenames))
            .into(JsonEx.createSink(projectGenerator));
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
        builder.append(encode(key)).append('=').append(encode(value));
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

    private byte[] promptsList(
        McpHttpBindingConfig binding)
    {
        byte[] json = binding.promptsListJson();
        if (json == null)
        {
            json = buildPromptsList(binding).getBytes(UTF_8);
            binding.promptsListJson(json);
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
            final List<GuardedConfig> guarded = binding.toolGuarded(tool.name);
            if (!guarded.isEmpty())
            {
                final JsonArrayBuilder schemes = Json.createArrayBuilder();
                for (GuardedConfig g : guarded)
                {
                    if (!g.roles.isEmpty())
                    {
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
                item.add("securitySchemes", schemes);
            }
            tools.add(item);
        }
        return compact(Json.createObjectBuilder().add("tools", tools).build());
    }

    private String buildResourcesList(
        McpHttpBindingConfig binding)
    {
        final JsonArrayBuilder resources = Json.createArrayBuilder();
        for (McpHttpResourceConfig resource : binding.resources())
        {
            final JsonObjectBuilder item = Json.createObjectBuilder()
                .add("name", resource.name);
            if (resource.uri != null)
            {
                final String key = resource.uri.indexOf('{') >= 0 ? "uriTemplate" : "uri";
                item.add(key, resource.uri);
            }
            if (resource.description != null)
            {
                item.add("description", resource.description);
            }
            if (resource.mimeType != null)
            {
                item.add("mimeType", resource.mimeType);
            }
            resources.add(item);
        }
        return compact(Json.createObjectBuilder().add("resources", resources).build());
    }

    private String buildPromptsList(
        McpHttpBindingConfig binding)
    {
        final JsonArrayBuilder prompts = Json.createArrayBuilder();
        for (McpHttpPromptConfig prompt : binding.prompts())
        {
            final JsonObjectBuilder item = Json.createObjectBuilder()
                .add("name", prompt.name);
            if (prompt.description != null)
            {
                item.add("description", prompt.description);
            }
            if (prompt.arguments != null)
            {
                final JsonArrayBuilder arguments = Json.createArrayBuilder();
                for (McpHttpPromptArgumentConfig argument : prompt.arguments)
                {
                    final JsonObjectBuilder argumentItem = Json.createObjectBuilder()
                        .add("name", argument.name);
                    if (argument.description != null)
                    {
                        argumentItem.add("description", argument.description);
                    }
                    argumentItem.add("required", argument.required);
                    arguments.add(argumentItem);
                }
                item.add("arguments", arguments);
            }
            prompts.add(item);
        }
        return compact(Json.createObjectBuilder().add("prompts", prompts).build());
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
    // ASCII char and its UTF-8 byte are bit-identical, so this produces the same output either way.
    private static String encode(
        String value)
    {
        final StringBuilder builder = new StringBuilder();
        if (isAscii(value))
        {
            for (int i = 0; i < value.length(); i++)
            {
                encodeByte(builder, value.charAt(i));
            }
        }
        else
        {
            final byte[] bytes = value.getBytes(UTF_8);
            for (byte b : bytes)
            {
                encodeByte(builder, b & 0xff);
            }
        }
        return builder.toString();
    }

    private static boolean isAscii(
        String value)
    {
        boolean ascii = true;
        for (int i = 0; ascii && i < value.length(); i++)
        {
            ascii = value.charAt(i) < 0x80;
        }
        return ascii;
    }

    private static void encodeByte(
        StringBuilder builder,
        int c)
    {
        if (c >= 'A' && c <= 'Z' ||
            c >= 'a' && c <= 'z' ||
            c >= '0' && c <= '9' ||
            c == '-' || c == '.' || c == '_' || c == '~')
        {
            builder.append((char) c);
        }
        else
        {
            builder.append('%');
            builder.append(Character.toUpperCase(Character.forDigit(c >> 4 & 0xf, 16)));
            builder.append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
        }
    }

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
