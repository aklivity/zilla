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
import jakarta.json.JsonString;
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
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSink;
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
    private static final int MAX_ERROR_BODY = 8192;
    private static final int JSON_RPC_INTERNAL_ERROR = -32603;
    private static final int RESPONSE_WINDOW = 1024;
    private static final int RESPONSE_GEN_BOUND = 1024;
    private static final int RESPONSE_BUFFER_MAX = 4096;

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
    private final BufferPool bufferPool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int mcpTypeId;
    private final EngineContext context;
    private final McpHttpEventContext events;
    private final Long2ObjectHashMap<McpHttpBindingConfig> bindings;

    private final JsonGeneratorEx projectGenerator;
    private final MutableDirectBufferEx projectBuffer;
    private final UnsafeBufferEx argsRO;
    private final Map<JsonSchema, JsonPipeline> projectors;
    private final Map<JsonSchema, JsonPipeline> validatingProjectors;
    private final Map<JsonSchema, JsonPipeline> validators;
    private final Map<McpHttpRouteConfig, JsonPipeline> templates;

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
        this.bufferPool = context.bufferPool();
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
        this.projectors = new IdentityHashMap<>();
        this.validatingProjectors = new IdentityHashMap<>();
        this.validators = new IdentityHashMap<>();
        this.templates = new IdentityHashMap<>();
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
                    McpHttpRouteConfig route = null;
                    McpHttpResourceConfig resource = null;
                    McpHttpPromptConfig prompt = null;
                    String name = null;
                    String uri = null;
                    int contentLength = -1;
                    final Map<String, String> params = new HashMap<>();

                    if (kind == KIND_TOOLS_CALL)
                    {
                        name = beginEx.toolsCall().name().asString();
                        contentLength = beginEx.toolsCall().contentLength();
                        route = binding.resolveTool(name, authorization);
                    }
                    else if (kind == KIND_RESOURCES_READ)
                    {
                        uri = beginEx.resourcesRead().uri().asString();
                        contentLength = beginEx.resourcesRead().contentLength();
                        resource = binding.resolveResource(uri, params);
                        if (resource != null)
                        {
                            route = binding.resolveResourceRoute(resource.name, authorization);
                        }
                    }
                    else if (kind == KIND_PROMPTS_GET)
                    {
                        name = beginEx.promptsGet().name().asString();
                        contentLength = beginEx.promptsGet().contentLength();
                        prompt = binding.prompt(name);
                    }

                    final boolean listing = kind == KIND_TOOLS_LIST ||
                        kind == KIND_RESOURCES_LIST || kind == KIND_PROMPTS_LIST;

                    if (route != null || listing || prompt != null)
                    {
                        newStream = new McpProxy(binding, route, resource, kind, name, uri, params, contentLength,
                            sender, originId, routedId, initialId, authorization, affinity)::onMcpMessage;
                    }
                }
            }
        }

        return newStream;
    }

    private final class McpProxy
    {
        private final McpHttpBindingConfig binding;
        private final McpHttpRouteConfig route;
        private final McpHttpResourceConfig resource;
        private final int kind;
        private final String name;
        private final String uri;
        private final Map<String, String> params;
        private final int contentLength;

        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long authorization;
        private final long affinity;

        private final HttpProxy delegate;

        private int state;
        private boolean requestHandled;

        private int requestSlot = NO_SLOT;
        private int requestOffset;

        private int replySlot = NO_SLOT;
        private int replySlotOffset;
        private boolean replyDataStarted;
        private boolean replyComplete;

        private JsonPipeline responsePipeline;
        private JsonGeneratorEx responseGenerator;
        private boolean responseStreaming;
        private boolean responseDone;

        private boolean requestStreaming;
        private JsonPipeline requestPipeline;
        private JsonGeneratorEx requestGenerator;
        private Map<String, String> requestArgs;
        private List<String> requestPathArgs;
        private boolean requestBegun;
        private boolean requestProjected;
        private boolean requestEndedMcp;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private McpProxy(
            McpHttpBindingConfig binding,
            McpHttpRouteConfig route,
            McpHttpResourceConfig resource,
            int kind,
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
            this.resource = resource;
            this.kind = kind;
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
            this.delegate = route != null ? new HttpProxy(this, route.id) : null;
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

            if (kind == KIND_TOOLS_CALL && route != null && route.with.body != null &&
                route.with.bodyTemplate == null && contentLength > bufferPool.slotCapacity())
            {
                requestStreaming = true;
                requestArgs = new HashMap<>();
                requestPathArgs = argReferences(route.with.headers.get(HEADER_PATH));
                requestGenerator = JsonEx.createGenerator();
                requestPipeline = JsonEx.stream(JsonEx.createParser())
                    .transform(new McpHttpArguments(requestArgs))
                    .transform(JsonEx.projector(binding.jsonSchema(route.with.body)))
                    .into(JsonEx.createSink(requestGenerator, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));
                requestPipeline.reset();
                grantMcpWindow(traceId);
            }
            else
            {
                doMcpWindow(traceId);
                if (!requestHandled && delegate == null && contentLength < 0)
                {
                    handleRequest(traceId);
                }
            }
        }

        private void onMcpData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            initialSeq = data.sequence() + reserved;

            if (requestStreaming)
            {
                if (payload != null)
                {
                    if (requestSlot == NO_SLOT)
                    {
                        requestSlot = bufferPool.acquire(initialId);
                    }

                    if (requestSlot == NO_SLOT || requestOffset + payload.sizeof() > bufferPool.slotCapacity())
                    {
                        cleanup(traceId);
                    }
                    else
                    {
                        final MutableDirectBufferEx slot = bufferPool.buffer(requestSlot);
                        slot.putBytes(requestOffset, payload.buffer(), payload.offset(), payload.sizeof());
                        requestOffset += payload.sizeof();
                        pumpRequest(traceId);
                    }
                }
            }
            else
            {
                if (payload != null)
                {
                    if (requestSlot == NO_SLOT)
                    {
                        requestSlot = bufferPool.acquire(initialId);
                    }

                    if (requestSlot == NO_SLOT)
                    {
                        cleanup(traceId);
                    }
                    else
                    {
                        final MutableDirectBufferEx slot = bufferPool.buffer(requestSlot);
                        slot.putBytes(requestOffset, payload.buffer(), payload.offset(), payload.sizeof());
                        requestOffset += payload.sizeof();
                    }
                }

                if (payload != null && (contentLength < 0 || requestOffset < contentLength))
                {
                    doMcpWindow(traceId);
                }

                if (!requestHandled && contentLength >= 0 && requestOffset >= contentLength)
                {
                    handleRequest(traceId);
                }
            }
        }

        private void onMcpEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            initialSeq = end.sequence();
            state = McpHttpState.closedInitial(state);

            if (requestStreaming)
            {
                requestEndedMcp = true;
                pumpRequest(traceId);
            }
            else if (!requestHandled)
            {
                handleRequest(traceId);
            }
        }

        private void onMcpAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            state = McpHttpState.closedInitial(state);
            if (delegate != null)
            {
                delegate.doHttpAbort(traceId);
            }
            cleanupRequestSlot();
        }

        private void onMcpWindow(
            WindowFW window)
        {
            replyAck = window.acknowledge();
            replyMax = window.maximum();
            replyPad = window.padding();

            final long traceId = window.traceId();
            flushReply(traceId);

            if (responseStreaming && !responseDone && delegate != null)
            {
                delegate.resumeResponse(traceId);
            }
        }

        private void onMcpReset(
            ResetFW reset)
        {
            state = McpHttpState.closedReply(state);
            cleanupReplySlot();
        }

        private void handleRequest(
            long traceId)
        {
            requestHandled = true;

            if (kind == KIND_TOOLS_LIST)
            {
                doMcpReply(traceId, buildToolsList(binding));
                cleanupRequestSlot();
                return;
            }
            else if (kind == KIND_RESOURCES_LIST)
            {
                doMcpReply(traceId, buildResourcesList(binding));
                cleanupRequestSlot();
                return;
            }
            else if (kind == KIND_PROMPTS_LIST)
            {
                doMcpReply(traceId, buildPromptsList(binding));
                cleanupRequestSlot();
                return;
            }
            else if (kind == KIND_PROMPTS_GET)
            {
                final JsonObject promptRequest = requestSlot != NO_SLOT
                    ? parseObject(bufferPool.buffer(requestSlot), 0, requestOffset)
                    : null;
                final JsonObject promptArgs = promptRequest != null &&
                    promptRequest.containsKey("arguments") &&
                    promptRequest.get("arguments").getValueType() == JsonValue.ValueType.OBJECT
                    ? promptRequest.getJsonObject("arguments")
                    : JsonValue.EMPTY_JSON_OBJECT;
                doMcpReply(traceId, buildPromptGet(binding.prompt(name), promptArgs));
                cleanupRequestSlot();
                return;
            }

            final JsonObject request = requestSlot != NO_SLOT
                ? parseObject(bufferPool.buffer(requestSlot), 0, requestOffset)
                : null;

            final JsonObject args = kind == KIND_TOOLS_CALL && request != null &&
                request.containsKey("arguments") &&
                request.get("arguments").getValueType() == JsonValue.ValueType.OBJECT
                ? request.getJsonObject("arguments")
                : JsonValue.EMPTY_JSON_OBJECT;

            final McpHttpToolConfig tool = kind == KIND_TOOLS_CALL ? binding.tool(name) : null;
            final McpHttpWithConfig with = route.with;

            final boolean needArgs = tool != null && tool.input != null ||
                with.query != null || with.body != null || with.bodyTemplate != null;
            final byte[] argsBytes = needArgs ? compact(args).getBytes(UTF_8) : null;

            if (tool != null && tool.input != null)
            {
                argsRO.wrap(argsBytes);
                if (!validate(binding.jsonSchema(tool.input), argsRO, 0, argsBytes.length))
                {
                    doMcpReply(traceId, buildToolError("invalid arguments"));
                    cleanupRequestSlot();
                    return;
                }
            }

            final List<String> unsatisfied = binding.unsatisfiedAccessors(route);
            if (!unsatisfied.isEmpty())
            {
                final String accessor = unsatisfied.get(0);
                events.schemaAccessorUnresolved(traceId, binding.id, name != null ? name : uri, accessor);
                doMcpReset(traceId, JSON_RPC_INTERNAL_ERROR, "unresolved expression: ${" + accessor + "}");
                cleanupRequestSlot();
                return;
            }

            String path = interpolate(with.headers.get(HEADER_PATH), expr -> resolveRequest(args, expr));

            if (with.query != null)
            {
                argsRO.wrap(argsBytes);
                final String projected = project(binding.jsonSchema(with.query), argsRO, 0, argsBytes.length);
                final String query = projected != null ? queryString(parseValue(projected)) : "";
                if (!query.isEmpty())
                {
                    path = path + "?" + query;
                }
            }

            byte[] body = null;
            String contentType = null;
            if (with.bodyTemplate != null)
            {
                argsRO.wrap(argsBytes);
                final String remapped = template(route, argsRO, 0, argsBytes.length);
                body = (remapped != null ? remapped : "{}").getBytes(UTF_8);
                contentType = DEFAULT_CONTENT_TYPE;
            }
            else if (with.body != null)
            {
                argsRO.wrap(argsBytes);
                final String projected = project(binding.jsonSchema(with.body), argsRO, 0, argsBytes.length);
                body = (projected != null ? projected : "{}").getBytes(UTF_8);
                contentType = DEFAULT_CONTENT_TYPE;
            }

            final Map<String, String> credentials = new HashMap<>();
            binding.resolveCredentials(authorization, credentials);

            delegate.doHttpBegin(traceId, with.headers, credentials, path, contentType);
            delegate.doHttpRequest(traceId, body);

            cleanupRequestSlot();
        }

        private String resolveRequest(
            JsonObject args,
            String expression)
        {
            String value = "";
            if (expression.startsWith("args."))
            {
                value = encode(navigate(args, expression.substring(5)));
            }
            else if (expression.startsWith("params."))
            {
                final String captured = params.get(expression.substring(7));
                value = encode(captured != null ? captured : "");
            }
            return value;
        }

        private void pumpRequest(
            long traceId)
        {
            boolean progress = true;
            while (progress && !requestProjected)
            {
                if (delegate == null || !delegate.requestEncodeHasRoom())
                {
                    if (delegate != null)
                    {
                        delegate.flushRequestStream(traceId);
                    }
                    if (delegate == null || !delegate.requestEncodeHasRoom())
                    {
                        progress = false;
                        continue;
                    }
                }

                requestGenerator.wrap(projectBuffer, 0, RESPONSE_GEN_BOUND);
                final JsonPipeline.Status status =
                    requestPipeline.transform(bufferPool.buffer(requestSlot), 0, requestOffset, requestEndedMcp);
                final int produced = requestGenerator.length();
                if (produced > 0)
                {
                    delegate.stageRequestBody(projectBuffer, 0, produced);
                }

                switch (status)
                {
                case SUSPENDED:
                    break;
                case STARVED:
                    requestOffset = 0;
                    progress = false;
                    break;
                case COMPLETED:
                    requestProjected = true;
                    progress = false;
                    cleanupRequestSlot();
                    break;
                case REJECTED:
                    progress = false;
                    cleanup(traceId);
                    break;
                default:
                    progress = false;
                    break;
                }
            }

            if (!requestBegun && delegate != null && requestPathReady())
            {
                sendRequestBegin(traceId);
                requestBegun = true;
            }

            if (requestBegun)
            {
                if (requestProjected)
                {
                    delegate.requestComplete();
                }
                delegate.flushRequestStream(traceId);
            }

            if (!requestProjected)
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
            final Map<String, String> credentials = new HashMap<>();
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

        private void grantMcpWindow(
            long traceId)
        {
            initialAck = initialSeq - requestOffset;
            initialMax = requestOffset + (delegate != null && delegate.requestEncodeHasRoom() ? RESPONSE_WINDOW : 0);
            state = McpHttpState.openedInitial(state);
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0);
        }

        private void onUpstreamResponse(
            long traceId,
            String status,
            String contentType,
            DirectBufferEx body,
            int offset,
            int length)
        {
            if (kind == KIND_TOOLS_CALL)
            {
                final boolean ok = status != null && status.startsWith("2");
                if (ok)
                {
                    final McpHttpToolConfig tool = binding.tool(name);
                    final JsonSchema outputSchema = tool != null ? binding.jsonSchema(tool.output) : null;
                    final String projected = outputSchema != null
                        ? validateProject(outputSchema, body, offset, length)
                        : text(body, offset, length);
                    if (outputSchema != null && projected == null)
                    {
                        doMcpReply(traceId, buildToolError("invalid response"));
                    }
                    else
                    {
                        final JsonValue structured = parseValue(projected);
                        final String summary = tool != null && tool.summary != null
                            ? interpolate(tool.summary, expr -> resolveResult(structured, expr))
                            : compact((JsonStructure) structured);
                        doMcpReply(traceId, buildToolSuccess(structured, summary));
                    }
                }
                else
                {
                    doMcpReply(traceId, buildToolError(cap(text(body, offset, length))));
                }
            }
            else
            {
                final JsonSchema responseSchema = resource != null ? binding.jsonSchema(resource.output) : null;
                final String projected = responseSchema != null
                    ? validateProject(responseSchema, body, offset, length)
                    : text(body, offset, length);
                if (responseSchema != null && projected == null)
                {
                    doMcpAbort(traceId);
                }
                else
                {
                    final String mimeType = resource != null && resource.mimeType != null
                        ? resource.mimeType
                        : contentType;
                    final String resourceText = compact((JsonStructure) parseValue(projected));
                    doMcpReply(traceId, buildResource(uri, mimeType, resourceText));
                }
            }
        }

        private void onUpstreamAbort(
            long traceId)
        {
            cleanupResponse();
            doMcpAbort(traceId);
        }

        private void responseBegin(
            long traceId,
            String contentType)
        {
            responseStreaming = true;

            final JsonSchema schema = resource != null ? binding.jsonSchema(resource.output) : null;
            responseGenerator = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true));
            responsePipeline = schema != null
                ? JsonEx.stream(JsonEx.createParser())
                    .transform(JsonEx.projector(schema))
                    .into(JsonEx.createSink(responseGenerator, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)))
                : JsonEx.stream(JsonEx.createParser())
                    .into(JsonEx.createSink(responseGenerator, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));
            responsePipeline.reset();

            final String mimeType = resource != null && resource.mimeType != null
                ? resource.mimeType
                : contentType;
            stage(replyPrefix(uri, mimeType));
            doMcpBegin(traceId);
        }

        private JsonPipeline.Status responseStep(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            // SEGMENTABLE honors the generator output bound: it copies as many whole source units as fit
            // RESPONSE_GEN_BOUND, pushes the source bytes taken back via consumed() so the parser advances
            // position() by exactly that count, and returns SUSPENDED when the bound fills mid-value
            responseGenerator.wrap(projectBuffer, 0, RESPONSE_GEN_BOUND);
            final JsonPipeline.Status status = responsePipeline.transform(buffer, offset, offset + length, last);
            final int produced = responseGenerator.length();
            if (produced > 0)
            {
                stageBytes(projectBuffer, 0, produced);
            }
            return status;
        }

        private int responseRemaining()
        {
            return responsePipeline.remaining();
        }

        private void responseComplete(
            long traceId)
        {
            responseDone = true;
            stage(replySuffix());
            replyComplete = true;
            flushReply(traceId);
        }

        private void responseReject(
            long traceId)
        {
            responseDone = true;
            cleanupResponse();
            cleanupReplySlot();
            doMcpAbort(traceId);
        }

        private boolean encodeHasRoom()
        {
            return encodeFree() >= RESPONSE_GEN_BOUND;
        }

        private void stage(
            byte[] bytes)
        {
            if (replySlot == NO_SLOT)
            {
                replySlot = bufferPool.acquire(replyId);
            }

            if (replySlot == NO_SLOT)
            {
                cleanup(0L);
            }
            else
            {
                bufferPool.buffer(replySlot).putBytes(replySlotOffset, bytes);
                replySlotOffset += bytes.length;
            }
        }

        private void stageBytes(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (replySlot == NO_SLOT)
            {
                replySlot = bufferPool.acquire(replyId);
            }

            if (replySlot == NO_SLOT)
            {
                cleanup(0L);
            }
            else
            {
                bufferPool.buffer(replySlot).putBytes(replySlotOffset, buffer, offset, length);
                replySlotOffset += length;
            }
        }

        private int encodeFree()
        {
            return bufferPool.slotCapacity() - replySlotOffset;
        }

        private void cleanupResponse()
        {
            responsePipeline = null;
            responseGenerator = null;
            responseStreaming = false;
        }

        private void doMcpReply(
            long traceId,
            String reply)
        {
            stage(reply.getBytes(UTF_8));
            replyComplete = true;
            doMcpBegin(traceId);
            flushReply(traceId);
        }

        private void flushReply(
            long traceId)
        {
            if (replySlot != NO_SLOT && McpHttpState.replyOpened(state))
            {
                final MutableDirectBufferEx slot = bufferPool.buffer(replySlot);
                int maxPayload = replyMax - (int)(replySeq - replyAck) - replyPad;
                while (replySlotOffset > 0 && maxPayload > 0)
                {
                    final int length = Math.min(maxPayload, replySlotOffset);
                    final int reserved = length + replyPad;
                    final boolean fin = replyComplete && length == replySlotOffset;
                    final int flags = (replyDataStarted ? 0 : FLAGS_INIT) | (fin ? FLAGS_FIN : 0);
                    doMcpData(traceId, flags, reserved, slot, 0, length);
                    replyDataStarted = true;
                    final int remaining = replySlotOffset - length;
                    if (remaining > 0)
                    {
                        slot.putBytes(0, slot, length, remaining);
                    }
                    replySlotOffset = remaining;
                    maxPayload = replyMax - (int)(replySeq - replyAck) - replyPad;
                }

                if (replyComplete && replySlotOffset == 0)
                {
                    doMcpEnd(traceId);
                    cleanupReplySlot();
                }
            }
        }

        private void doMcpBegin(
            long traceId)
        {
            if (!McpHttpState.replyOpened(state))
            {
                doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, emptyRO);
                state = McpHttpState.openedReply(state);
            }
        }

        private void doMcpData(
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

        private void doMcpEnd(
            long traceId)
        {
            if (!McpHttpState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = McpHttpState.closedReply(state);
            }
        }

        private void doMcpAbort(
            long traceId)
        {
            doMcpBegin(traceId);
            if (!McpHttpState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = McpHttpState.closedReply(state);
            }
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

        private void doMcpReset(
            long traceId)
        {
            if (!McpHttpState.initialClosed(state))
            {
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
                state = McpHttpState.closedInitial(state);
            }
        }

        private void doMcpReset(
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

        private void cleanup(
            long traceId)
        {
            doMcpReset(traceId);
            doMcpAbort(traceId);
            if (delegate != null)
            {
                delegate.doHttpAbort(traceId);
            }
            cleanupRequestSlot();
            cleanupReplySlot();
        }

        private void cleanupRequestSlot()
        {
            if (requestSlot != NO_SLOT)
            {
                bufferPool.release(requestSlot);
                requestSlot = NO_SLOT;
                requestOffset = 0;
            }
        }

        private void cleanupReplySlot()
        {
            if (replySlot != NO_SLOT)
            {
                bufferPool.release(replySlot);
                replySlot = NO_SLOT;
                replySlotOffset = 0;
            }
        }

        private byte[] replyPrefix(
            String uri,
            String mimeType)
        {
            final StringBuilder builder = new StringBuilder()
                .append("{\"contents\":[{\"uri\":");
            jsonString(builder, uri);
            builder.append(",\"mimeType\":");
            jsonString(builder, mimeType);
            builder.append(",\"text\":\"");
            return builder.toString().getBytes(UTF_8);
        }

        private byte[] replySuffix()
        {
            return "\"}]}".getBytes(UTF_8);
        }
    }

    private final class HttpProxy
    {
        private final McpProxy server;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private MessageConsumer receiver;
        private int state;

        private byte[] pendingBody;
        private int requestProgress;

        private int requestEncodeSlot = NO_SLOT;
        private int requestEncodeOffset;
        private boolean requestDataStarted;
        private boolean requestEnd;

        private int responseSlot = NO_SLOT;
        private int responseOffset;
        private String responseStatus;
        private String responseContentType;
        private boolean responseStreaming;
        private boolean responseEnded;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private HttpProxy(
            McpProxy server,
            long resolvedId)
        {
            this.server = server;
            this.originId = server.routedId;
            this.routedId = resolvedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
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
                initialSeq, initialAck, initialMax, traceId, server.authorization, server.affinity, httpBeginEx);
        }

        private void doHttpRequest(
            long traceId,
            byte[] body)
        {
            if (body != null && body.length > 0)
            {
                pendingBody = body;
                requestProgress = 0;
                flushRequest(traceId);
            }
            else
            {
                doHttpEnd(traceId);
            }
        }

        private void flushRequest(
            long traceId)
        {
            if (pendingBody != null)
            {
                int maxPayload = initialMax - (int)(initialSeq - initialAck) - initialPad;
                while (requestProgress < pendingBody.length && maxPayload > 0)
                {
                    final int length = Math.min(maxPayload, pendingBody.length - requestProgress);
                    final int reserved = length + initialPad;
                    final int flags = (requestProgress == 0 ? FLAGS_INIT : 0) |
                        (requestProgress + length == pendingBody.length ? FLAGS_FIN : 0);
                    extBuffer.putBytes(0, pendingBody, requestProgress, length);
                    doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, server.authorization, flags, 0L, reserved, extBuffer, 0, length);
                    initialSeq += reserved;
                    requestProgress += length;
                    maxPayload = initialMax - (int)(initialSeq - initialAck) - initialPad;
                }

                if (requestProgress >= pendingBody.length)
                {
                    pendingBody = null;
                    doHttpEnd(traceId);
                }
            }
        }

        private void stageRequestBody(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (requestEncodeSlot == NO_SLOT)
            {
                requestEncodeSlot = bufferPool.acquire(initialId);
            }

            if (requestEncodeSlot == NO_SLOT)
            {
                server.cleanup(0L);
            }
            else
            {
                bufferPool.buffer(requestEncodeSlot).putBytes(requestEncodeOffset, buffer, offset, length);
                requestEncodeOffset += length;
            }
        }

        private boolean requestEncodeHasRoom()
        {
            return bufferPool.slotCapacity() - requestEncodeOffset >= RESPONSE_GEN_BOUND;
        }

        private void requestComplete()
        {
            requestEnd = true;
        }

        private void flushRequestStream(
            long traceId)
        {
            if (receiver != null && !McpHttpState.initialClosed(state))
            {
                if (requestEncodeSlot != NO_SLOT)
                {
                    final MutableDirectBufferEx slot = bufferPool.buffer(requestEncodeSlot);
                    int maxPayload = initialMax - (int)(initialSeq - initialAck) - initialPad;
                    while (requestEncodeOffset > 0 && maxPayload > 0)
                    {
                        final int length = Math.min(maxPayload, requestEncodeOffset);
                        final int reserved = length + initialPad;
                        final boolean fin = requestEnd && length == requestEncodeOffset;
                        final int flags = (requestDataStarted ? 0 : FLAGS_INIT) | (fin ? FLAGS_FIN : 0);
                        doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                            traceId, server.authorization, flags, 0L, reserved, slot, 0, length);
                        initialSeq += reserved;
                        requestDataStarted = true;
                        final int remaining = requestEncodeOffset - length;
                        if (remaining > 0)
                        {
                            slot.putBytes(0, slot, length, remaining);
                        }
                        requestEncodeOffset = remaining;
                        maxPayload = initialMax - (int)(initialSeq - initialAck) - initialPad;
                    }
                }

                if (requestEnd && requestEncodeOffset == 0)
                {
                    cleanupRequestEncodeSlot();
                    doHttpEnd(traceId);
                }
            }
        }

        private void resumeRequest(
            long traceId)
        {
            if (server.requestStreaming && !server.requestProjected)
            {
                server.pumpRequest(traceId);
            }
        }

        private void doHttpEnd(
            long traceId)
        {
            if (!McpHttpState.initialClosed(state))
            {
                doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, server.authorization);
                state = McpHttpState.closedInitial(state);
            }
        }

        private void doHttpAbort(
            long traceId)
        {
            if (McpHttpState.initialOpening(state) && !McpHttpState.initialClosed(state))
            {
                doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, server.authorization);
                state = McpHttpState.closedInitial(state);
            }
            pendingBody = null;
            cleanupRequestEncodeSlot();
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
                if (responseSlot == NO_SLOT)
                {
                    responseSlot = bufferPool.acquire(replyId);
                }

                if (responseSlot == NO_SLOT || responseOffset + payload.sizeof() > bufferPool.slotCapacity())
                {
                    server.cleanup(traceId);
                }
                else
                {
                    final MutableDirectBufferEx slot = bufferPool.buffer(responseSlot);
                    slot.putBytes(responseOffset, payload.buffer(), payload.offset(), payload.sizeof());
                    responseOffset += payload.sizeof();

                    if (!responseStreaming && server.kind == KIND_RESOURCES_READ &&
                        responseOffset > RESPONSE_BUFFER_MAX)
                    {
                        server.responseBegin(traceId, responseContentType);
                        responseStreaming = true;
                    }

                    if (responseStreaming)
                    {
                        pumpResponse(traceId);
                    }
                    else
                    {
                        doHttpReplyWindow(traceId);
                    }
                }
            }
        }

        private void onHttpEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            replySeq = end.sequence();
            state = McpHttpState.closedReply(state);
            responseEnded = true;

            if (responseStreaming)
            {
                pumpResponse(traceId);
            }
            else
            {
                final DirectBufferEx body = responseSlot != NO_SLOT ? bufferPool.buffer(responseSlot) : null;
                server.onUpstreamResponse(traceId, responseStatus, responseContentType, body, 0, responseOffset);
                cleanupResponseSlot();
            }
        }

        private void onHttpAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            state = McpHttpState.closedReply(state);
            server.onUpstreamAbort(traceId);
            cleanupResponseSlot();
        }

        private void pumpResponse(
            long traceId)
        {
            boolean progress = true;
            while (progress && !server.responseDone)
            {
                if (!server.encodeHasRoom())
                {
                    server.flushReply(traceId);
                    if (!server.encodeHasRoom())
                    {
                        progress = false;
                        continue;
                    }
                }

                final MutableDirectBufferEx slot = bufferPool.buffer(responseSlot);
                final JsonPipeline.Status status = server.responseStep(slot, 0, responseOffset, responseEnded);
                if (status != JsonPipeline.Status.SUSPENDED)
                {
                    // compact only at a terminal status: across suspend cycles the pipeline re-feeds the same
                    // window, so dropping consumed bytes mid-cycle would corrupt its positioning; here the
                    // window-relative remaining() is the tail to keep, so the consumed prefix is the rest
                    final int consumed = responseOffset - server.responseRemaining();
                    if (consumed > 0 && consumed < responseOffset)
                    {
                        slot.putBytes(0, slot, consumed, responseOffset - consumed);
                    }
                    responseOffset -= consumed;
                }
                switch (status)
                {
                case SUSPENDED:
                    break;
                case STARVED:
                    progress = false;
                    break;
                case COMPLETED:
                    server.responseComplete(traceId);
                    progress = false;
                    break;
                case REJECTED:
                    server.responseReject(traceId);
                    progress = false;
                    break;
                default:
                    progress = false;
                    break;
                }
            }

            server.flushReply(traceId);

            if (server.responseDone)
            {
                cleanupResponseSlot();
            }
            else
            {
                grantHttpReply(traceId);
            }
        }

        private void resumeResponse(
            long traceId)
        {
            if (responseStreaming && !server.responseDone)
            {
                pumpResponse(traceId);
            }
        }

        private void grantHttpReply(
            long traceId)
        {
            if (!McpHttpState.replyClosed(state))
            {
                replyAck = replySeq - responseOffset;
                replyMax = responseOffset + (server.encodeHasRoom() ? RESPONSE_WINDOW : 0);
                doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, server.authorization, 0L, 0);
            }
        }

        private void onHttpWindow(
            WindowFW window)
        {
            initialAck = window.acknowledge();
            initialMax = window.maximum();
            initialPad = window.padding();

            final long traceId = window.traceId();
            if (server.requestStreaming)
            {
                flushRequestStream(traceId);
                resumeRequest(traceId);
            }
            else
            {
                flushRequest(traceId);
            }
        }

        private void onHttpReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            state = McpHttpState.closedInitial(state);
            pendingBody = null;
            cleanupRequestEncodeSlot();
            server.onUpstreamAbort(traceId);
        }

        private void doHttpReplyWindow(
            long traceId)
        {
            replyAck = replySeq - responseOffset;
            replyMax = bufferPool.slotCapacity();
            doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, server.authorization, 0L, 0);
        }

        private void cleanupResponseSlot()
        {
            if (responseSlot != NO_SLOT)
            {
                bufferPool.release(responseSlot);
                responseSlot = NO_SLOT;
                responseOffset = 0;
            }
        }

        private void cleanupRequestEncodeSlot()
        {
            if (requestEncodeSlot != NO_SLOT)
            {
                bufferPool.release(requestEncodeSlot);
                requestEncodeSlot = NO_SLOT;
                requestEncodeOffset = 0;
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

    private String resolveResult(
        JsonValue structured,
        String expression)
    {
        String value = "";
        if (expression.startsWith("result.") && structured.getValueType() == JsonValue.ValueType.OBJECT)
        {
            value = navigate(structured.asJsonObject(), expression.substring(7));
        }
        return value;
    }

    private boolean validate(
        JsonSchema schema,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        return run(validators.computeIfAbsent(schema, this::newValidator), buffer, offset, length) != null;
    }

    private String project(
        JsonSchema schema,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        return run(projectors.computeIfAbsent(schema, this::newProjector), buffer, offset, length);
    }

    private String validateProject(
        JsonSchema schema,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        return run(validatingProjectors.computeIfAbsent(schema, this::newValidatingProjector), buffer, offset, length);
    }

    private String template(
        McpHttpRouteConfig route,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        return run(templates.computeIfAbsent(route, this::newTemplate), buffer, offset, length);
    }

    private String run(
        JsonPipeline pipeline,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        projectGenerator.wrap(projectBuffer, 0, projectBuffer.capacity());
        pipeline.reset();

        final JsonPipeline.Status status = pipeline.transform(buffer, offset, offset + length);

        String result = null;
        if (status == JsonPipeline.Status.COMPLETED)
        {
            final byte[] bytes = new byte[projectGenerator.length()];
            projectBuffer.getBytes(0, bytes);
            result = new String(bytes, UTF_8);
        }
        return result;
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

    private JsonPipeline newValidatingProjector(
        JsonSchema schema)
    {
        return JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
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

    private String queryString(
        JsonValue projected)
    {
        final StringBuilder builder = new StringBuilder();
        if (projected.getValueType() == JsonValue.ValueType.OBJECT)
        {
            final JsonObject object = projected.asJsonObject();
            for (String key : object.keySet())
            {
                final JsonValue value = object.get(key);
                if (value.getValueType() == JsonValue.ValueType.ARRAY)
                {
                    for (JsonValue element : value.asJsonArray())
                    {
                        appendQuery(builder, key, asText(element));
                    }
                }
                else
                {
                    appendQuery(builder, key, asText(value));
                }
            }
        }
        return builder.toString();
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

    private String buildToolSuccess(
        JsonValue structured,
        String summary)
    {
        final JsonArrayBuilder content = Json.createArrayBuilder()
            .add(Json.createObjectBuilder().add("type", "text").add("text", summary));
        final JsonObjectBuilder reply = Json.createObjectBuilder()
            .add("content", content)
            .add("structuredContent", structured)
            .add("isError", false);
        return compact(reply.build());
    }

    private String buildToolError(
        String bodyText)
    {
        final JsonArrayBuilder content = Json.createArrayBuilder()
            .add(Json.createObjectBuilder().add("type", "text").add("text", bodyText));
        final JsonObjectBuilder reply = Json.createObjectBuilder()
            .add("content", content)
            .add("isError", true);
        return compact(reply.build());
    }

    private String buildResource(
        String uri,
        String mimeType,
        String text)
    {
        final JsonObjectBuilder item = Json.createObjectBuilder()
            .add("uri", uri)
            .add("mimeType", mimeType)
            .add("text", text);
        final JsonObjectBuilder reply = Json.createObjectBuilder()
            .add("contents", Json.createArrayBuilder().add(item));
        return compact(reply.build());
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
                final JsonObjectBuilder schemes = Json.createObjectBuilder();
                for (GuardedConfig g : guarded)
                {
                    if (!g.roles.isEmpty())
                    {
                        final JsonArrayBuilder scopes = Json.createArrayBuilder();
                        for (String role : g.roles)
                        {
                            scopes.add(role);
                        }
                        schemes.add(g.name, Json.createObjectBuilder().add("scopes", scopes));
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

    private String buildPromptGet(
        McpHttpPromptConfig prompt,
        JsonObject args)
    {
        final JsonObjectBuilder reply = Json.createObjectBuilder();
        if (prompt.description != null)
        {
            reply.add("description", prompt.description);
        }
        final JsonArrayBuilder messages = Json.createArrayBuilder();
        for (McpHttpPromptMessageConfig message : prompt.messages)
        {
            final String text = interpolate(message.text,
                expr -> expr.startsWith("args.") ? navigate(args, expr.substring(5)) : "");
            final JsonObjectBuilder content = Json.createObjectBuilder()
                .add("type", "text")
                .add("text", text);
            messages.add(Json.createObjectBuilder()
                .add("role", message.role)
                .add("content", content));
        }
        reply.add("messages", messages);
        return compact(reply.build());
    }

    private JsonObject schemaObject(
        McpHttpBindingConfig binding,
        ModelConfig model)
    {
        final String text = model != null ? binding.schemaText(model) : null;
        return text != null ? parseObject(text) : null;
    }

    private static JsonObject parseObject(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        final byte[] bytes = new byte[length];
        buffer.getBytes(offset, bytes);
        return parseObject(new String(bytes, UTF_8));
    }

    private static JsonValue parseValue(
        String text)
    {
        JsonValue value = JsonValue.EMPTY_JSON_OBJECT;
        if (text != null && !text.isEmpty())
        {
            try (JsonReader reader = Json.createReader(new StringReader(text)))
            {
                value = reader.readValue();
            }
        }
        return value;
    }

    private static String text(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        final byte[] bytes = new byte[length];
        buffer.getBytes(offset, bytes);
        return new String(bytes, UTF_8);
    }

    private static String cap(
        String text)
    {
        return text.length() > MAX_ERROR_BODY ? text.substring(0, MAX_ERROR_BODY) : text;
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

    private static String navigate(
        JsonObject root,
        String path)
    {
        String result = "";
        JsonValue current = root;
        boolean found = root != null;
        for (String segment : path.split("\\."))
        {
            if (current != null && current.getValueType() == JsonValue.ValueType.OBJECT &&
                current.asJsonObject().containsKey(segment))
            {
                current = current.asJsonObject().get(segment);
            }
            else
            {
                found = false;
                break;
            }
        }
        if (found && current != null)
        {
            result = asText(current);
        }
        return result;
    }

    private static String asText(
        JsonValue value)
    {
        String result;
        switch (value.getValueType())
        {
        case STRING:
            result = ((JsonString) value).getString();
            break;
        case NULL:
            result = "";
            break;
        default:
            result = value.toString();
            break;
        }
        return result;
    }

    private static String encode(
        String value)
    {
        final StringBuilder builder = new StringBuilder();
        final byte[] bytes = value.getBytes(UTF_8);
        for (byte b : bytes)
        {
            final int c = b & 0xff;
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
        return builder.toString();
    }

    private static void jsonString(
        StringBuilder builder,
        String value)
    {
        builder.append('"');
        final int length = value != null ? value.length() : 0;
        for (int index = 0; index < length; index++)
        {
            final char c = value.charAt(index);
            switch (c)
            {
            case '"':
                builder.append("\\\"");
                break;
            case '\\':
                builder.append("\\\\");
                break;
            case '\n':
                builder.append("\\n");
                break;
            case '\r':
                builder.append("\\r");
                break;
            case '\t':
                builder.append("\\t");
                break;
            case '\b':
                builder.append("\\b");
                break;
            case '\f':
                builder.append("\\f");
                break;
            default:
                if (c < 0x20)
                {
                    builder.append("\\u00");
                    builder.append((char) hex(c >> 4));
                    builder.append((char) hex(c & 0xf));
                }
                else
                {
                    builder.append(c);
                }
                break;
            }
        }
        builder.append('"');
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
