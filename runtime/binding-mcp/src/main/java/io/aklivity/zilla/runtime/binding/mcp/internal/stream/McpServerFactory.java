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
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
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

    private final McpServerDecoder decodeMethod = this::decodeMethod;
    private final McpServerDecoder decodeInitialize = this::decodeInitialize;
    private final McpServerDecoder decode = this::decode;

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
                final OctetsFW extension = begin.extension();
                final HttpBeginExFW httpBeginEx = extension.sizeof() > 0
                    ? httpBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                    : null;

                boolean httpDelete = false;
                String sessionId = null;
                boolean acceptSse = false;

                if (httpBeginEx != null)
                {
                    final HttpHeaderFW methodHeader = httpBeginEx.headers()
                        .matchFirst(h -> HTTP_HEADER_METHOD.equals(h.name().asString()));
                    httpDelete = methodHeader != null &&
                        HTTP_DELETE.equals(methodHeader.value().asString());

                    final HttpHeaderFW sessionHeader = httpBeginEx.headers()
                        .matchFirst(h -> HTTP_HEADER_SESSION.equals(h.name().asString()));
                    if (sessionHeader != null)
                    {
                        sessionId = sessionHeader.value().asString();
                    }

                    final HttpHeaderFW acceptHeader = httpBeginEx.headers()
                        .matchFirst(h -> HTTP_HEADER_ACCEPT.equals(h.name().asString()));
                    acceptSse = acceptHeader != null &&
                        acceptHeader.value().asString().contains(CONTENT_TYPE_SSE);
                }

                newStream = new McpServer(
                    sender,
                    originId,
                    routedId,
                    initialId,
                    resolvedId,
                    affinity,
                    authorization,
                    sessionId,
                    httpDelete,
                    acceptSse)::onNetMessage;
            }
        }

        return newStream;
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
            int limit);
    }

    private int decodeMethod(
        McpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final String fullJson = buffer.getStringWithoutLengthUtf8(offset, limit - offset);
        String parsedMethod = null;
        boolean parsedHasId = false;
        String parsedRequestId = null;
        JsonObject parsedParams = null;

        try (JsonParser parser = Json.createParser(new StringReader(fullJson)))
        {
            String currentKey = null;
            while (parser.hasNext())
            {
                final JsonParser.Event event = parser.next();
                switch (event)
                {
                case KEY_NAME:
                    currentKey = parser.getString();
                    break;
                case VALUE_STRING:
                    if ("method".equals(currentKey))
                    {
                        parsedMethod = parser.getString();
                    }
                    else if ("id".equals(currentKey))
                    {
                        parsedHasId = true;
                        parsedRequestId = "\"" + parser.getString() + "\"";
                    }
                    currentKey = null;
                    break;
                case VALUE_NUMBER:
                case VALUE_TRUE:
                case VALUE_FALSE:
                    if ("id".equals(currentKey))
                    {
                        parsedHasId = true;
                        parsedRequestId = String.valueOf(parser.getLong());
                    }
                    currentKey = null;
                    break;
                case VALUE_NULL:
                    currentKey = null;
                    break;
                case START_OBJECT:
                    if ("params".equals(currentKey))
                    {
                        parsedParams = parser.getObject();
                    }
                    else if (currentKey != null)
                    {
                        parser.skipObject();
                    }
                    currentKey = null;
                    break;
                case START_ARRAY:
                    parser.skipArray();
                    currentKey = null;
                    break;
                default:
                    break;
                }
            }
        }
        catch (JsonParsingException ex)
        {
            return offset;
        }

        if (parsedParams != null)
        {
            if ("tools/call".equals(parsedMethod))
            {
                server.toolName = parsedParams.containsKey("name") ? parsedParams.getString("name") : null;
            }
            else if ("prompts/get".equals(parsedMethod))
            {
                server.promptName = parsedParams.containsKey("name") ? parsedParams.getString("name") : null;
            }
            else if ("resources/read".equals(parsedMethod))
            {
                server.resourceUri = parsedParams.containsKey("uri") ? parsedParams.getString("uri") : null;
            }
            else if ("logging/setLevel".equals(parsedMethod))
            {
                server.loggingLevel = parsedParams.containsKey("level") ? parsedParams.getString("level") : null;
            }
            else if ("notifications/cancelled".equals(parsedMethod))
            {
                server.cancelReason =
                    parsedParams.containsKey("reason") ? parsedParams.getString("reason") : null;
            }
        }

        server.method = parsedMethod;
        server.requestId = parsedRequestId;
        server.notification = !parsedHasId;
        server.paramsStr = parsedParams != null ? parsedParams.toString() : null;
        server.decoder = "initialize".equals(parsedMethod) ? decodeInitialize : decode;

        return limit;
    }

    private int decodeInitialize(
        McpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        server.openAppStream(traceId);
        return limit;
    }

    private int decode(
        McpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        server.openAppStream(traceId);
        return limit;
    }

    private final class McpServer
    {
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final String sessionId;
        private final boolean httpDelete;
        private final boolean acceptSse;

        private McpServerDecoder decoder;
        private McpStream stream;

        String method;
        String requestId;
        boolean notification;
        String toolName;
        String promptName;
        String resourceUri;
        String loggingLevel;
        String cancelReason;
        String paramsStr;

        private int state;

        private int decodeSlot = BufferPool.NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private McpServer(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            String sessionId,
            boolean httpDelete,
            boolean acceptSse)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.sessionId = sessionId;
            this.httpDelete = httpDelete;
            this.acceptSse = acceptSse;
            this.decoder = decodeMethod;
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
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();

            initialSeq = sequence;
            initialAck = acknowledge;
            initialMax = maximum;

            if (httpDelete)
            {
                openAppStream(traceId);
            }
            else
            {
                doNetWindow(traceId, 0, 0);
            }
        }

        private void onNetData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final int maximum = data.maximum();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            if (stream != null || payload == null)
            {
                return;
            }

            if (decodeSlot == BufferPool.NO_SLOT)
            {
                decodeSlot = bufferPool.acquire(initialId);
            }

            if (decodeSlot == BufferPool.NO_SLOT)
            {
                doNetReset(sequence, acknowledge, maximum, traceId);
                return;
            }

            final MutableDirectBuffer slot = bufferPool.buffer(decodeSlot);
            if (decodeSlotOffset + payload.sizeof() > slot.capacity())
            {
                cleanupDecodeSlot();
                doNetReset(sequence, acknowledge, maximum, traceId);
                return;
            }

            slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
            decodeSlotOffset += payload.sizeof();
            decodeSlotReserved += reserved;

            decodeNet(traceId, authorization, budgetId, reserved, slot, 0, decodeSlotOffset);
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
                progress = decoder.decode(this, traceId, authorization, budgetId, reserved, buffer, progress, limit);
            }

            if (progress >= limit)
            {
                cleanupDecodeSlot();
            }
            else if (progress > offset)
            {
                final MutableDirectBuffer slot = bufferPool.buffer(decodeSlot);
                slot.putBytes(0, buffer, progress, limit - progress);
                decodeSlotOffset = limit - progress;
                decodeSlotReserved = 0;
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            if (stream == null && decodeSlot != BufferPool.NO_SLOT)
            {
                final DirectBuffer slot = bufferPool.buffer(decodeSlot);
                decodeNet(traceId, authorization, 0, 0, slot, 0, decodeSlotOffset);
            }

            cleanupDecodeSlot();

            if (stream != null)
            {
                stream.doAppEnd(traceId);
            }
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            cleanupDecodeSlot();

            if (stream != null)
            {
                stream.doAppAbort(traceId);
            }
        }

        private void onNetFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final int maximum = flush.maximum();
            final long traceId = flush.traceId();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            if (stream != null && McpServerState.initialOpened(stream.state))
            {
                doFlush(stream.app, routedId, resolvedId, stream.initialId,
                    sequence, acknowledge, maximum, traceId, authorization,
                    budgetId, reserved);
            }
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            if (stream != null)
            {
                doWindow(stream.app, routedId, resolvedId, stream.replyId,
                    sequence, acknowledge, maximum, traceId, authorization, budgetId, padding);
            }
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            if (stream != null)
            {
                stream.doAppAbort(traceId);
            }
        }

        private void openAppStream(
            long traceId)
        {
            final McpBeginExFW.Builder mcpBeginExBuilder = mcpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId);

            final String sid = sessionId;

            if ("initialize".equals(method))
            {
                mcpBeginExBuilder.initialize(b -> b.sessionId(s -> s.text((String) null)));
            }
            else if ("notifications/initialized".equals(method))
            {
                mcpBeginExBuilder.initialize(b -> b.sessionId(s -> s.text(sid)));
            }
            else if ("ping".equals(method))
            {
                mcpBeginExBuilder.ping(b -> b.sessionId(s -> s.text(sid)));
            }
            else if ("tools/list".equals(method))
            {
                mcpBeginExBuilder.tools(b -> b.sessionId(s -> s.text(sid)));
            }
            else if ("tools/call".equals(method))
            {
                final String name = toolName;
                mcpBeginExBuilder.tool(b -> b.sessionId(s -> s.text(sid)).name(name));
            }
            else if ("prompts/list".equals(method))
            {
                mcpBeginExBuilder.prompts(b -> b.sessionId(s -> s.text(sid)));
            }
            else if ("prompts/get".equals(method))
            {
                final String name = promptName;
                mcpBeginExBuilder.prompt(b -> b.sessionId(s -> s.text(sid)).name(name));
            }
            else if ("resources/list".equals(method))
            {
                mcpBeginExBuilder.resources(b -> b.sessionId(s -> s.text(sid)));
            }
            else if ("resources/read".equals(method))
            {
                final String uri = resourceUri;
                mcpBeginExBuilder.resource(b -> b.sessionId(s -> s.text(sid)).uri(uri));
            }
            else if ("completion/complete".equals(method))
            {
                mcpBeginExBuilder.completion(b -> b.sessionId(s -> s.text(sid)));
            }
            else if ("logging/setLevel".equals(method))
            {
                final String level = loggingLevel;
                mcpBeginExBuilder.logging(b -> b.sessionId(s -> s.text(sid)).level(level));
            }
            else if ("notifications/cancelled".equals(method))
            {
                final String reason = cancelReason;
                mcpBeginExBuilder.cancel(b -> b.sessionId(s -> s.text(sid)).reason(reason));
            }
            else
            {
                mcpBeginExBuilder.disconnect(b -> b.sessionId(s -> s.text(sid)));
            }

            final McpBeginExFW mcpBeginEx = mcpBeginExBuilder.build();
            final McpStream newStream = new McpStream(this);
            newStream.doAppBegin(traceId, mcpBeginEx);
            if (newStream.app != null)
            {
                stream = newStream;
            }
        }

        private void doNetBegin(
            long traceId,
            DirectBuffer extBuf,
            int extOffset,
            int extLength)
        {
            doBegin(sender, originId, routedId, replyId,
                initialSeq, initialAck, initialMax, traceId, authorization, affinity,
                extBuf, extOffset, extLength);

            state = McpServerState.openingReply(state);
        }

        private void doNetData(
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            DirectBuffer payload,
            int offset,
            int length)
        {
            doData(sender, originId, routedId, replyId,
                initialSeq, initialAck, initialMax, traceId, authorization,
                budgetId, flags, reserved, payload, offset, length);
        }

        private void doNetEnd(
            long traceId)
        {
            if (!McpServerState.replyClosed(state))
            {
                state = McpServerState.closedReply(state);
                doEnd(sender, originId, routedId, replyId,
                    initialSeq, initialAck, initialMax, traceId, authorization);
            }
        }

        private void doNetAbort(
            long traceId)
        {
            if (!McpServerState.replyClosed(state))
            {
                state = McpServerState.closedReply(state);
                doAbort(sender, originId, routedId, replyId,
                    initialSeq, initialAck, initialMax, traceId, authorization);
            }
        }

        private void doNetWindow(
            long traceId,
            long budgetId,
            int padding)
        {
            doWindow(sender, originId, routedId, initialId,
                initialSeq, initialAck, writeBuffer.capacity(), traceId, authorization, budgetId, padding);
        }

        private void doNetReset(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId)
        {
            if (!McpServerState.initialClosed(state))
            {
                state = McpServerState.closedInitial(state);
                doReset(sender, originId, routedId, initialId,
                    sequence, acknowledge, maximum, traceId, authorization);
            }
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != BufferPool.NO_SLOT)
            {
                bufferPool.release(decodeSlot);
                decodeSlot = BufferPool.NO_SLOT;
                decodeSlotOffset = 0;
                decodeSlotReserved = 0;
            }
        }
    }

    private final class McpStream
    {
        private final McpServer server;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private MessageConsumer app;

        private int state;
        private boolean sseResponse;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long replySeq;
        private long replyAck;
        private int replyMax;

        private McpStream(
            McpServer server)
        {
            this.server = server;
            this.originId = server.routedId;
            this.routedId = server.resolvedId;
            this.initialId = supplyInitialId.applyAsLong(server.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doAppBegin(
            long traceId,
            Flyweight extension)
        {
            initialSeq = server.initialSeq;
            initialAck = server.initialAck;
            initialMax = server.initialMax;

            app = newStream(this::onAppMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, server.authorization, server.affinity,
                extension);

            if (app != null)
            {
                state = McpServerState.openedInitial(state);

                if (server.paramsStr != null)
                {
                    final int paramsLength = extBuffer.putStringWithoutLengthUtf8(0, server.paramsStr);
                    doData(app, originId, routedId, initialId,
                        initialSeq, initialAck, initialMax,
                        traceId, server.authorization, 0, 0, 0, extBuffer, 0, paramsLength);
                }

                server.doNetWindow(traceId, 0, 0);
            }
            else
            {
                server.doNetReset(server.initialSeq, server.initialAck, server.initialMax, traceId);
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
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            String responseSessionId = server.sessionId;
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

            final String status = server.notification ? STATUS_202 : STATUS_200;
            sseResponse = server.acceptSse && !server.notification;

            final String finalResponseSessionId = responseSessionId;
            final HttpBeginExFW httpBeginEx = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(status))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE)
                    .value(sseResponse ? CONTENT_TYPE_SSE : CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION)
                    .value(finalResponseSessionId != null ? finalResponseSessionId : ""))
                .build();

            server.doNetBegin(traceId, httpBeginEx.buffer(), httpBeginEx.offset(), httpBeginEx.sizeof());
            doAppWindow(traceId);
        }

        private void onAppData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                final String payloadStr =
                    payload.buffer().getStringWithoutLengthUtf8(payload.offset(), payload.sizeof());
                final String resultStr;
                if (isNotification(payloadStr))
                {
                    resultStr = payloadStr;
                }
                else
                {
                    resultStr = "{\"jsonrpc\":\"2.0\",\"id\":" + server.requestId +
                        ",\"result\":" + payloadStr + "}";
                }
                final int resultLength = extBuffer.putStringWithoutLengthUtf8(0, resultStr);

                if (sseResponse)
                {
                    final byte[] prefixBytes = SSE_DATA_PREFIX.getBytes();
                    final byte[] suffixBytes = SSE_DATA_SUFFIX.getBytes();
                    final int sseLength = prefixBytes.length + resultLength + suffixBytes.length;
                    sseBuffer.putBytes(0, prefixBytes);
                    sseBuffer.putBytes(prefixBytes.length, extBuffer, 0, resultLength);
                    sseBuffer.putBytes(prefixBytes.length + resultLength, suffixBytes);

                    server.doNetData(traceId, budgetId, flags, reserved, sseBuffer, 0, sseLength);
                }
                else
                {
                    server.doNetData(traceId, budgetId, flags, reserved, extBuffer, 0, resultLength);
                }
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            server.doNetEnd(traceId);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            server.doNetAbort(traceId);
        }

        private void onAppFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final int maximum = flush.maximum();
            final long traceId = flush.traceId();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            doFlush(server.sender, server.originId, server.routedId, server.replyId,
                replySeq, replyAck, replyMax, traceId, server.authorization,
                budgetId, reserved);
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            initialSeq = sequence;
            initialAck = acknowledge;
            initialMax = maximum;

            doWindow(server.sender, server.originId, server.routedId, server.initialId,
                initialSeq, initialAck, initialMax, traceId, server.authorization, budgetId, padding);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            server.doNetReset(server.initialSeq, server.initialAck, server.initialMax, traceId);
        }

        private void doAppEnd(
            long traceId)
        {
            if (McpServerState.initialOpened(state) && !McpServerState.initialClosed(state))
            {
                state = McpServerState.closedInitial(state);
                doEnd(app, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, server.authorization);
            }
        }

        private void doAppAbort(
            long traceId)
        {
            if (McpServerState.initialOpened(state) && !McpServerState.initialClosed(state))
            {
                state = McpServerState.closedInitial(state);
                doAbort(app, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, server.authorization);
            }
        }

        private void doAppWindow(
            long traceId)
        {
            replySeq = 0;
            replyAck = 0;
            replyMax = writeBuffer.capacity();
            doWindow(app, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, server.authorization, 0, 0);
        }

        private void doAppReset(
            long traceId)
        {
            if (!McpServerState.replyClosed(state))
            {
                state = McpServerState.closedReply(state);
                doReset(app, originId, routedId, replyId,
                    replySeq, replyAck, replyMax,
                    traceId, server.authorization);
            }
        }
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
        final String sessionId;
        switch (mcpBeginEx.kind())
        {
        case McpBeginExFW.KIND_INITIALIZE:
            sessionId = mcpBeginEx.initialize().sessionId().text().asString();
            break;
        case McpBeginExFW.KIND_PING:
            sessionId = mcpBeginEx.ping().sessionId().text().asString();
            break;
        case McpBeginExFW.KIND_TOOLS:
            sessionId = mcpBeginEx.tools().sessionId().text().asString();
            break;
        case McpBeginExFW.KIND_TOOL:
            sessionId = mcpBeginEx.tool().sessionId().text().asString();
            break;
        case McpBeginExFW.KIND_PROMPTS:
            sessionId = mcpBeginEx.prompts().sessionId().text().asString();
            break;
        case McpBeginExFW.KIND_PROMPT:
            sessionId = mcpBeginEx.prompt().sessionId().text().asString();
            break;
        case McpBeginExFW.KIND_RESOURCES:
            sessionId = mcpBeginEx.resources().sessionId().text().asString();
            break;
        case McpBeginExFW.KIND_RESOURCE:
            sessionId = mcpBeginEx.resource().sessionId().text().asString();
            break;
        case McpBeginExFW.KIND_COMPLETION:
            sessionId = mcpBeginEx.completion().sessionId().text().asString();
            break;
        case McpBeginExFW.KIND_LOGGING:
            sessionId = mcpBeginEx.logging().sessionId().text().asString();
            break;
        case McpBeginExFW.KIND_CANCEL:
            sessionId = mcpBeginEx.cancel().sessionId().text().asString();
            break;
        case McpBeginExFW.KIND_DISCONNECT:
            sessionId = mcpBeginEx.disconnect().sessionId().text().asString();
            break;
        default:
            sessionId = null;
            break;
        }
        return sessionId;
    }

    private static boolean isNotification(
        String json)
    {
        try (JsonParser parser = Json.createParser(new StringReader(json)))
        {
            int depth = 0;
            while (parser.hasNext())
            {
                final JsonParser.Event event = parser.next();
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
                    if (depth == 1 && "method".equals(parser.getString()))
                    {
                        return true;
                    }
                    break;
                default:
                    break;
                }
            }
        }
        catch (Exception ex)
        {
            // fall through
        }
        return false;
    }
}
