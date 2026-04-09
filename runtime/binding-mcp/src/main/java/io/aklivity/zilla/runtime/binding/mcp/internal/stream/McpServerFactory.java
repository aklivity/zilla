/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.mcp.internal.stream;

import java.io.StringReader;
import java.util.function.LongUnaryOperator;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParsingException;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpServerFactory implements McpStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String MCP_TYPE_NAME = "mcp";
    private static final String KIND_SERVER = "server";
    private static final String HTTP_HEADER_METHOD = ":method";
    private static final String HTTP_HEADER_SESSION = "mcp-session-id";
    private static final String HTTP_HEADER_ACCEPT = "accept";
    private static final String HTTP_HEADER_STATUS = ":status";
    private static final String HTTP_HEADER_CONTENT_TYPE = "content-type";
    private static final String HTTP_DELETE = "DELETE";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_SSE = "text/event-stream";
    private static final String STATUS_200 = "200";
    private static final String STATUS_202 = "202";
    private static final String SSE_DATA_PREFIX = "data: ";
    private static final String SSE_DATA_SUFFIX = "\n\n";
    private static final int FLAG_FIN = 0x01;

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(), 0, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer sseBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int mcpTypeId;
    private final BufferPool bufferPool;

    private final Long2ObjectHashMap<McpBindingConfig> bindings;

    public McpServerFactory(
        McpConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.sseBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.bufferPool = context.bufferPool();
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
        final long affinity = begin.affinity();
        final long authorization = begin.authorization();

        final McpBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final long resolvedId = binding.resolveRoute(authorization, KIND_SERVER);

            if (resolvedId != -1L)
            {
                newStream = new McpServerStream(
                    sender,
                    originId,
                    routedId,
                    initialId,
                    resolvedId,
                    affinity,
                    authorization)::onMessage;
            }
        }

        return newStream;
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
        DirectBuffer extBuf,
        int extOffset,
        int extLength)
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
            .extension(extBuf, extOffset, extLength)
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
        long budgetId,
        int flags,
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
            .budgetId(budgetId)
            .reserved(reserved)
            .flags(flags)
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

    private static String extractMcpSessionId(
        McpBeginExFW mcpBeginEx)
    {
        switch (mcpBeginEx.kind())
        {
        case McpBeginExFW.KIND_INITIALIZE:
        {
            final String16FW sid = mcpBeginEx.initialize().sessionId();
            return sid != null ? sid.asString() : null;
        }
        case McpBeginExFW.KIND_PING:
        {
            final String16FW sid = mcpBeginEx.ping().sessionId();
            return sid != null ? sid.asString() : null;
        }
        case McpBeginExFW.KIND_TOOLS:
        {
            final String16FW sid = mcpBeginEx.tools().sessionId();
            return sid != null ? sid.asString() : null;
        }
        case McpBeginExFW.KIND_TOOL:
        {
            final String16FW sid = mcpBeginEx.tool().sessionId();
            return sid != null ? sid.asString() : null;
        }
        case McpBeginExFW.KIND_PROMPTS:
        {
            final String16FW sid = mcpBeginEx.prompts().sessionId();
            return sid != null ? sid.asString() : null;
        }
        case McpBeginExFW.KIND_PROMPT:
        {
            final String16FW sid = mcpBeginEx.prompt().sessionId();
            return sid != null ? sid.asString() : null;
        }
        case McpBeginExFW.KIND_RESOURCES:
        {
            final String16FW sid = mcpBeginEx.resources().sessionId();
            return sid != null ? sid.asString() : null;
        }
        case McpBeginExFW.KIND_RESOURCE:
        {
            final String16FW sid = mcpBeginEx.resource().sessionId();
            return sid != null ? sid.asString() : null;
        }
        case McpBeginExFW.KIND_COMPLETION:
        {
            final String16FW sid = mcpBeginEx.completion().sessionId();
            return sid != null ? sid.asString() : null;
        }
        case McpBeginExFW.KIND_LOGGING:
        {
            final String16FW sid = mcpBeginEx.logging().sessionId();
            return sid != null ? sid.asString() : null;
        }
        case McpBeginExFW.KIND_CANCEL:
        {
            final String16FW sid = mcpBeginEx.cancel().sessionId();
            return sid != null ? sid.asString() : null;
        }
        case McpBeginExFW.KIND_DISCONNECT:
        {
            final String16FW sid = mcpBeginEx.disconnect().sessionId();
            return sid != null ? sid.asString() : null;
        }
        default:
            return null;
        }
    }

    private final class McpServerStream
    {
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;

        private long downstreamInitialId;
        private long downstreamReplyId;
        private MessageConsumer downstream;

        private String sessionId;
        private String mcpVersion;
        private String toolName;
        private String promptName;
        private String resourceUri;
        private String loggingLevel;
        private String cancelReason;
        private boolean httpDelete;
        private boolean acceptSse;
        private boolean notification;
        private boolean downstreamBeginSent;
        private boolean sseResponse;
        private int decodeSlot = BufferPool.NO_SLOT;
        private int decodeSlotOffset;

        private long pendingSequence;
        private long pendingAcknowledge;
        private int pendingMaximum;
        private long pendingTraceId;

        private McpServerStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
        }

        private void onMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onReset(reset);
                break;
            default:
                break;
            }
        }

        private void onBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            pendingSequence = sequence;
            pendingAcknowledge = acknowledge;
            pendingMaximum = maximum;
            pendingTraceId = traceId;

            if (extension.sizeof() > 0)
            {
                final HttpBeginExFW httpBeginEx =
                    httpBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
                if (httpBeginEx != null)
                {
                    final HttpHeaderFW methodHeader =
                        httpBeginEx.headers().matchFirst(h -> HTTP_HEADER_METHOD.equals(h.name().asString()));
                    if (methodHeader != null && HTTP_DELETE.equals(methodHeader.value().asString()))
                    {
                        httpDelete = true;
                    }

                    final HttpHeaderFW sessionHeader =
                        httpBeginEx.headers().matchFirst(h -> HTTP_HEADER_SESSION.equals(h.name().asString()));
                    if (sessionHeader != null)
                    {
                        sessionId = sessionHeader.value().asString();
                    }

                    final HttpHeaderFW acceptHeader =
                        httpBeginEx.headers().matchFirst(h -> HTTP_HEADER_ACCEPT.equals(h.name().asString()));
                    if (acceptHeader != null && acceptHeader.value().asString().contains(CONTENT_TYPE_SSE))
                    {
                        acceptSse = true;
                    }
                }
            }

            downstreamInitialId = supplyInitialId.applyAsLong(resolvedId);
            downstreamReplyId = supplyReplyId.applyAsLong(downstreamInitialId);

            if (httpDelete)
            {
                sendDownstreamBegin(traceId, null, sequence, acknowledge, maximum);
            }
            else
            {
                doWindow(sender, originId, routedId, initialId,
                    sequence, acknowledge, writeBuffer.capacity(),
                    traceId, authorization, 0, 0);
            }
        }

        private void sendDownstreamBegin(
            long traceId,
            String method,
            long sequence,
            long acknowledge,
            int maximum)
        {
            final McpBeginExFW.Builder mcpBeginExBuilder = mcpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId);

            if ("initialize".equals(method))
            {
                final String version = mcpVersion;
                mcpBeginExBuilder.initialize(b -> b.version(version));
            }
            else if ("notifications/initialized".equals(method))
            {
                final String sid = sessionId;
                mcpBeginExBuilder.initialize(b -> b.sessionId(sid));
            }
            else if ("ping".equals(method))
            {
                final String sid = sessionId;
                mcpBeginExBuilder.ping(b -> b.sessionId(sid));
            }
            else if ("tools/list".equals(method))
            {
                final String sid = sessionId;
                mcpBeginExBuilder.tools(b -> b.sessionId(sid));
            }
            else if ("tools/call".equals(method))
            {
                final String sid = sessionId;
                final String name = toolName;
                mcpBeginExBuilder.tool(b -> b.sessionId(sid).name(name));
            }
            else if ("prompts/list".equals(method))
            {
                final String sid = sessionId;
                mcpBeginExBuilder.prompts(b -> b.sessionId(sid));
            }
            else if ("prompts/get".equals(method))
            {
                final String sid = sessionId;
                final String name = promptName;
                mcpBeginExBuilder.prompt(b -> b.sessionId(sid).name(name));
            }
            else if ("resources/list".equals(method))
            {
                final String sid = sessionId;
                mcpBeginExBuilder.resources(b -> b.sessionId(sid));
            }
            else if ("resources/read".equals(method))
            {
                final String sid = sessionId;
                final String uri = resourceUri;
                mcpBeginExBuilder.resource(b -> b.sessionId(sid).uri(uri));
            }
            else if ("completion/complete".equals(method))
            {
                final String sid = sessionId;
                mcpBeginExBuilder.completion(b -> b.sessionId(sid));
            }
            else if ("logging/setLevel".equals(method))
            {
                final String sid = sessionId;
                final String level = loggingLevel;
                mcpBeginExBuilder.logging(b -> b.sessionId(sid).level(level));
            }
            else if ("notifications/cancelled".equals(method))
            {
                final String sid = sessionId;
                final String reason = cancelReason;
                mcpBeginExBuilder.cancel(b -> b.sessionId(sid).reason(reason));
            }
            else
            {
                final String sid = sessionId;
                mcpBeginExBuilder.disconnect(b -> b.sessionId(sid));
            }

            final McpBeginExFW mcpBeginEx = mcpBeginExBuilder.build();

            final BeginFW downstreamBegin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(resolvedId)
                .streamId(downstreamInitialId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(mcpBeginEx.buffer(), mcpBeginEx.offset(), mcpBeginEx.sizeof())
                .build();

            downstream = streamFactory.newStream(
                downstreamBegin.typeId(),
                downstreamBegin.buffer(),
                downstreamBegin.offset(),
                downstreamBegin.sizeof(),
                this::onDownstreamMessage);

            if (downstream != null)
            {
                downstream.accept(
                    downstreamBegin.typeId(),
                    downstreamBegin.buffer(),
                    downstreamBegin.offset(),
                    downstreamBegin.sizeof());

                downstreamBeginSent = true;

                doWindow(sender, originId, routedId, initialId,
                    sequence, acknowledge, writeBuffer.capacity(),
                    traceId, authorization, 0, 0);
            }
            else
            {
                doReset(sender, originId, routedId, initialId,
                    sequence, acknowledge, maximum, traceId, authorization);
            }
        }

        private void onData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final int maximum = data.maximum();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();

            if (downstreamBeginSent || payload == null)
            {
                return;
            }

            if (decodeSlot == BufferPool.NO_SLOT)
            {
                decodeSlot = bufferPool.acquire(initialId);
            }

            if (decodeSlot == BufferPool.NO_SLOT)
            {
                doReset(sender, originId, routedId, initialId,
                    sequence, acknowledge, maximum, traceId, authorization);
                return;
            }

            final MutableDirectBuffer slot = bufferPool.buffer(decodeSlot);
            slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
            decodeSlotOffset += payload.sizeof();

            final String fullJson = slot.getStringWithoutLengthUtf8(0, decodeSlotOffset);
            String parsedMethod = null;
            boolean parsedHasId = false;
            JsonValue parsedParams = null;

            try
            {
                final JsonObject request = Json.createReader(new StringReader(fullJson)).readObject();
                parsedMethod = request.getString("method", null);
                parsedHasId = request.containsKey("id") && !request.isNull("id");
                parsedParams = request.get("params");
            }
            catch (JsonParsingException ex)
            {
                if ((flags & FLAG_FIN) == 0)
                {
                    return;
                }
            }

            if (parsedParams instanceof JsonObject paramsObj)
            {
                if ("initialize".equals(parsedMethod))
                {
                    mcpVersion = paramsObj.containsKey("protocolVersion")
                        ? paramsObj.getString("protocolVersion") : null;
                }
                else if ("tools/call".equals(parsedMethod))
                {
                    toolName = paramsObj.containsKey("name") ? paramsObj.getString("name") : null;
                }
                else if ("prompts/get".equals(parsedMethod))
                {
                    promptName = paramsObj.containsKey("name") ? paramsObj.getString("name") : null;
                }
                else if ("resources/read".equals(parsedMethod))
                {
                    resourceUri = paramsObj.containsKey("uri") ? paramsObj.getString("uri") : null;
                }
                else if ("logging/setLevel".equals(parsedMethod))
                {
                    loggingLevel = paramsObj.containsKey("level") ? paramsObj.getString("level") : null;
                }
                else if ("notifications/cancelled".equals(parsedMethod))
                {
                    cancelReason = paramsObj.containsKey("reason") ? paramsObj.getString("reason") : null;
                }
            }

            notification = !parsedHasId;
            sendDownstreamBegin(traceId, parsedMethod, pendingSequence, pendingAcknowledge, pendingMaximum);

            if (downstream != null && parsedParams != null)
            {
                final String paramsStr = parsedParams.toString();
                final int paramsLength = extBuffer.putStringWithoutLengthUtf8(0, paramsStr);
                doData(downstream, routedId, resolvedId, downstreamInitialId,
                    sequence, acknowledge, maximum, traceId, authorization,
                    budgetId, flags & ~FLAG_FIN, 0, extBuffer, 0, paramsLength);
            }

            cleanupDecodeSlot();
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != BufferPool.NO_SLOT)
            {
                bufferPool.release(decodeSlot);
                decodeSlot = BufferPool.NO_SLOT;
                decodeSlotOffset = 0;
            }
        }

        private void onEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final int maximum = end.maximum();
            final long traceId = end.traceId();

            if (!downstreamBeginSent && decodeSlot != BufferPool.NO_SLOT)
            {
                final MutableDirectBuffer slot = bufferPool.buffer(decodeSlot);
                final String fullJson = slot.getStringWithoutLengthUtf8(0, decodeSlotOffset);
                String parsedMethod = null;
                boolean parsedHasId = false;
                JsonValue parsedParams = null;

                try
                {
                    final JsonObject request = Json.createReader(new StringReader(fullJson)).readObject();
                    parsedMethod = request.getString("method", null);
                    parsedHasId = request.containsKey("id") && !request.isNull("id");
                    parsedParams = request.get("params");
                }
                catch (JsonParsingException ex)
                {
                    // fall through with null method (disconnect kind)
                }

                if (parsedParams instanceof JsonObject paramsObj)
                {
                    if ("initialize".equals(parsedMethod))
                    {
                        mcpVersion = paramsObj.containsKey("protocolVersion")
                            ? paramsObj.getString("protocolVersion") : null;
                    }
                    else if ("tools/call".equals(parsedMethod))
                    {
                        toolName = paramsObj.containsKey("name") ? paramsObj.getString("name") : null;
                    }
                    else if ("prompts/get".equals(parsedMethod))
                    {
                        promptName = paramsObj.containsKey("name") ? paramsObj.getString("name") : null;
                    }
                    else if ("resources/read".equals(parsedMethod))
                    {
                        resourceUri = paramsObj.containsKey("uri") ? paramsObj.getString("uri") : null;
                    }
                    else if ("logging/setLevel".equals(parsedMethod))
                    {
                        loggingLevel = paramsObj.containsKey("level") ? paramsObj.getString("level") : null;
                    }
                    else if ("notifications/cancelled".equals(parsedMethod))
                    {
                        cancelReason = paramsObj.containsKey("reason") ? paramsObj.getString("reason") : null;
                    }
                }

                notification = !parsedHasId;
                sendDownstreamBegin(traceId, parsedMethod, pendingSequence, pendingAcknowledge, pendingMaximum);

                if (downstream != null && parsedParams != null)
                {
                    final String paramsStr = parsedParams.toString();
                    final int paramsLength = extBuffer.putStringWithoutLengthUtf8(0, paramsStr);
                    doData(downstream, routedId, resolvedId, downstreamInitialId,
                        sequence, acknowledge, maximum, traceId, authorization,
                        0, 0, 0, extBuffer, 0, paramsLength);
                }
            }

            cleanupDecodeSlot();

            if (downstream != null)
            {
                doEnd(downstream, routedId, resolvedId, downstreamInitialId,
                    sequence, acknowledge, maximum, traceId, authorization);
            }
        }

        private void onAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final int maximum = abort.maximum();
            final long traceId = abort.traceId();

            cleanupDecodeSlot();

            if (downstream != null)
            {
                doAbort(downstream, routedId, resolvedId, downstreamInitialId,
                    sequence, acknowledge, maximum, traceId, authorization);
            }
        }

        private void onFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final int maximum = flush.maximum();
            final long traceId = flush.traceId();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            if (downstream != null)
            {
                doFlush(downstream, routedId, resolvedId, downstreamInitialId,
                    sequence, acknowledge, maximum, traceId, authorization,
                    budgetId, reserved);
            }
        }

        private void onWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            if (downstream != null)
            {
                doWindow(downstream, routedId, resolvedId, downstreamReplyId,
                    sequence, acknowledge, maximum,
                    traceId, authorization, budgetId, padding);
            }
        }

        private void onReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            if (downstream != null)
            {
                doAbort(downstream, routedId, resolvedId, downstreamInitialId,
                    sequence, acknowledge, maximum, traceId, authorization);
            }
        }

        private void onDownstreamMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onDownstreamBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onDownstreamData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onDownstreamEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onDownstreamAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onDownstreamFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onDownstreamWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onDownstreamReset(reset);
                break;
            default:
                break;
            }
        }

        private void onDownstreamBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            String responseSessionId = sessionId;
            if (extension.sizeof() > 0)
            {
                final McpBeginExFW mcpBeginEx =
                    mcpBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
                if (mcpBeginEx != null)
                {
                    final String sid = extractMcpSessionId(mcpBeginEx);
                    if (sid != null)
                    {
                        responseSessionId = sid;
                    }
                }
            }

            final String status = notification ? STATUS_202 : STATUS_200;
            sseResponse = acceptSse && !notification;

            final String finalResponseSessionId = responseSessionId;
            final HttpBeginExFW httpBeginEx = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(status))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE)
                    .value(sseResponse ? CONTENT_TYPE_SSE : CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(finalResponseSessionId != null ? finalResponseSessionId : ""))
                .build();

            doBegin(sender, originId, routedId, replyId,
                sequence, acknowledge, maximum, traceId, authorization, affinity,
                httpBeginEx.buffer(), httpBeginEx.offset(), httpBeginEx.sizeof());

            doWindow(downstream, routedId, resolvedId, downstreamReplyId,
                sequence, acknowledge, writeBuffer.capacity(),
                traceId, authorization, 0, 0);
        }

        private void onDownstreamData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final int maximum = data.maximum();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                if (sseResponse)
                {
                    final byte[] prefixBytes = SSE_DATA_PREFIX.getBytes();
                    final byte[] suffixBytes = SSE_DATA_SUFFIX.getBytes();
                    final int sseLength = prefixBytes.length + payload.sizeof() + suffixBytes.length;
                    sseBuffer.putBytes(0, prefixBytes);
                    sseBuffer.putBytes(prefixBytes.length, payload.buffer(), payload.offset(), payload.sizeof());
                    sseBuffer.putBytes(prefixBytes.length + payload.sizeof(), suffixBytes);

                    doData(sender, originId, routedId, replyId,
                        sequence, acknowledge, maximum, traceId, authorization,
                        budgetId, flags, reserved,
                        sseBuffer, 0, sseLength);
                }
                else
                {
                    doData(sender, originId, routedId, replyId,
                        sequence, acknowledge, maximum, traceId, authorization,
                        budgetId, flags, reserved,
                        payload.buffer(), payload.offset(), payload.sizeof());
                }
            }
        }

        private void onDownstreamEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final int maximum = end.maximum();
            final long traceId = end.traceId();

            doEnd(sender, originId, routedId, replyId,
                sequence, acknowledge, maximum, traceId, authorization);
        }

        private void onDownstreamAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final int maximum = abort.maximum();
            final long traceId = abort.traceId();

            doAbort(sender, originId, routedId, replyId,
                sequence, acknowledge, maximum, traceId, authorization);
        }

        private void onDownstreamFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final int maximum = flush.maximum();
            final long traceId = flush.traceId();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            doFlush(sender, originId, routedId, replyId,
                sequence, acknowledge, maximum, traceId, authorization,
                budgetId, reserved);
        }

        private void onDownstreamWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            doWindow(sender, originId, routedId, initialId,
                sequence, acknowledge, maximum,
                traceId, authorization, budgetId, padding);
        }

        private void onDownstreamReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            doReset(downstream, routedId, resolvedId, downstreamReplyId,
                sequence, acknowledge, maximum, traceId, authorization);
        }
    }
}
