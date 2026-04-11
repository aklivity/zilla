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

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;

import java.util.function.LongUnaryOperator;

import jakarta.json.Json;
import jakarta.json.stream.JsonParser;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;

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

    private static final String JSON_RPC_VERSION = "2.0";
    private static final String HTTP_HEADER_SESSION = "mcp-session-id";
    private static final String HTTP_HEADER_STATUS = ":status";
    private static final String HTTP_HEADER_CONTENT_TYPE = "content-type";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String STATUS_200 = "200";

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
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int mcpTypeId;
    private final BufferPool bufferPool;
    private final int decodeMax;

    private final DirectBufferInputStream inputRO = new DirectBufferInputStream();

    private final Long2ObjectHashMap<McpBindingConfig> bindings;

    private final McpServerDecoder decodeJsonRpc = this::decodeJsonRpc;
    private final McpServerDecoder decodeJsonRpcStart = this::decodeJsonRpcStart;
    private final McpServerDecoder decodeJsonRpcNext = this::decodeJsonRpcNext;
    private final McpServerDecoder decodeJsonRpcEnd = this::decodeJsonRpcEnd;
    private final McpServerDecoder decodeJsonRpcVersion = this::decodeJsonRpcVersion;
    private final McpServerDecoder decodeJsonRpcId = this::decodeJsonRpcId;
    private final McpServerDecoder decodeJsonRpcMethod = this::decodeJsonRpcMethod;
    private final McpServerDecoder decodeJsonRpcParams = this::decodeJsonRpcParams;
    private final McpServerDecoder decodeJsonRpcParamsValue = this::decodeJsonRpcParamsValue;
    private final McpServerDecoder decodeIgnore = this::decodeIgnore;

    public McpServerFactory(
        McpConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.bufferPool = context.bufferPool();
        this.decodeMax = bufferPool.slotCapacity();
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

            final OctetsFW extension = begin.extension();
            final HttpBeginExFW httpBeginEx = extension.sizeof() > 0
                ? httpBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            String sessionId = null;
            if (httpBeginEx != null)
            {
                final HttpHeaderFW sessionHeader = httpBeginEx.headers()
                    .matchFirst(h -> HTTP_HEADER_SESSION.equals(h.name().asString()));

                if (sessionHeader != null)
                {
                    sessionId = sessionHeader.value().asString();
                }
            }

            newStream = new McpServer(
                sender,
                originId,
                routedId,
                initialId,
                resolvedId,
                sessionId)::onNetMessage;
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
        int limit)
    {
        server.decodableJson = Json.createParser(inputRO);
        server.decodedVersion = null;
        server.decodedMethod = null;
        server.decodedId = null;
        server.decoder = decodeJsonRpcStart;

        return offset;
    }

    private int decodeJsonRpcStart(
        McpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        inputRO.wrap(buffer, offset, limit - offset);
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

            progress += (int) parser.getLocation().getStreamOffset();
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
        int limit)
    {
        int progress = offset;

        inputRO.wrap(buffer, offset, limit - offset);
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
                    server.decoder = decodeJsonRpcParams;
                    break;
                default:
                    server.onDecodeParseError(traceId, authorization);
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

            progress += (int) parser.getLocation().getStreamOffset();
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
        int limit)
    {
        int progress = offset;

        inputRO.wrap(buffer, offset, limit - offset);
        JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.END_OBJECT)
            {
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }

            parser.close();
            server.decoder = decodeIgnore;

            progress += (int) parser.getLocation().getStreamOffset();
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
        int limit)
    {
        int progress = offset;

        inputRO.wrap(buffer, offset, limit - offset);
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

            server.decodedVersion = JSON_RPC_VERSION;
            server.decoder = decodeJsonRpcNext;

            progress += (int) parser.getLocation().getStreamOffset();
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
        int limit)
    {
        int progress = offset;

        inputRO.wrap(buffer, offset, limit - offset);
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

            final String id = parser.getString();
            server.decodedId = id;
            server.decoder = decodeJsonRpcNext;

            progress += (int) parser.getLocation().getStreamOffset();
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
        int limit)
    {
        int progress = offset;

        inputRO.wrap(buffer, offset, limit - offset);
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
            server.decodedMethod = method;

            switch (method)
            {
            case "initialize":
            case "notifications/initialized":
            case "ping":
            case "tools/list":
            case "tools/call":
            case "prompts/list":
            case "prompts/get":
            case "resources/list":
            case "resources/read":
            case "completion/complete":
            case "logging/setLevel":
            case "notifications/cancelled":
                break;
            default:
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }

            server.decoder = decodeJsonRpcNext;

            progress += (int) parser.getLocation().getStreamOffset();
        }

        return progress;
    }

    private int decodeJsonRpcParams(
        McpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        inputRO.wrap(buffer, offset, limit - offset);
        JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.START_ARRAY)
            {
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }

            progress += (int) parser.getLocation().getStreamOffset();

            parser.skipArray();
            server.decoder = decodeJsonRpcParamsValue;
        }

        return progress;
    }

    private int decodeJsonRpcParamsValue(
        McpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        inputRO.wrap(buffer, offset, limit - offset);
        JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.END_ARRAY)
            {
                server.onDecodeParseError(traceId, authorization);
                server.decoder = decodeIgnore;
                break decode;
            }

            server.decoder = decodeJsonRpcNext;
        }

        progress += (int) parser.getLocation().getStreamOffset();

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
        private final String sessionId;

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

        private McpServerDecoder decoder;

        private JsonParser decodableJson;
        private String decodedVersion;
        private String decodedMethod;
        private String decodedId;

        private McpStream stream;

        private McpServer(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            String sessionId)
        {
            this.net = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.resolvedId = resolvedId;
            this.sessionId = sessionId;
            this.decoder = decodeJsonRpc;
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
            final int maximum = begin.maximum();

            initialSeq = sequence;
            initialAck = acknowledge;
            initialMax = maximum;

            state = McpServerState.openedInitial(state);

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
                    final MutableDirectBuffer slotBuffer = bufferPool.buffer(decodeSlot);
                    slotBuffer.putBytes(decodeSlotOffset, buffer, offset, limit - offset);
                    decodeSlotOffset += limit - offset;
                    decodeSlotReserved += reserved;

                    buffer = slotBuffer;
                    offset = 0;
                    limit = decodeSlotOffset;
                    reserved = decodeSlotReserved;
                }

                decodeNet(traceId, authorization, budgetId, reserved, buffer, offset, limit);
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            if (stream == null && decodeSlot != BufferPool.NO_SLOT)
            {
                final DirectBuffer slot = bufferPool.buffer(decodeSlot);
                decodeNet(traceId, authorization, 0, 0, slot, 0, decodeSlotOffset);
            }

            cleanupDecodeSlot();

            if (stream != null)
            {
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
                stream.doAppAbort(traceId, authorization);
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

            // TODO: encodeNet(traceId, authorization, budgetId);

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
            final long authorization = reset.authorization();

            if (stream != null)
            {
                stream.doAppAbort(traceId, authorization);
            }
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            String sessionId)
        {
            final HttpBeginExFW httpBeginEx = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(STATUS_200))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sessionId != null ? sessionId : ""))
                .build();

            doBegin(net, originId, routedId, replyId,
                initialSeq, initialAck, initialMax, traceId, authorization, 0,
                httpBeginEx);

            state = McpServerState.openingReply(state);
        }

        private void doNetData(
            long traceId,
            long authorization,
            long budgetId,
            int flags,
            int reserved,
            DirectBuffer payload,
            int offset,
            int length)
        {
            doData(net, originId, routedId, replyId,
                replySeq, replyAck, replyMax, traceId, authorization,
                budgetId, flags, reserved, payload, offset, length);
        }

        private void doNetFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            if (McpServerState.initialOpened(state))
            {
                doFlush(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization,
                    budgetId, reserved);
            }
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!McpServerState.replyClosed(state))
            {
                state = McpServerState.closedReply(state);
                doEnd(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization);
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            if (!McpServerState.replyClosed(state))
            {
                state = McpServerState.closedReply(state);
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
            doWindow(net, originId, routedId, initialId,
                initialSeq, initialAck, decodeMax - decodeSlotReserved, traceId, authorization, budgetId, padding);
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            if (!McpServerState.initialClosed(state))
            {
                state = McpServerState.closedInitial(state);
                doReset(net, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, authorization);
            }
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

        private void onDecodeParseError(
            long traceId,
            long authorization)
        {
            // TODO HttpBeginEx :status 400
            //      DATA
            //      {
            //        "jsonrpc": "2.0",
            //        "error": {
            //            "code": -32700,
            //            "message": "Parse error"
            //        },
            //        "id": null
            //        }
            doNetReset(traceId, authorization);
        }

        private void onDecodeInvalidRequest(
            long traceId,
            long authorization)
        {
            // TODO HttpBeginEx :status 400
            //      DATA
            //      {
            //        "jsonrpc": "2.0",
            //        "error": {
            //            "code": -32600,
            //            "message": "Invalid request"
            //        },
            //        "id": null
            //        }
            doNetReset(traceId, authorization);
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

        private void cleanupNet(
            long traceId,
            long authorization)
        {
            doNetReset(traceId, authorization);
            doNetAbort(traceId, authorization);
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
            long authorization,
            Flyweight extension)
        {
            app = newStream(this::onAppMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, authorization, 0,
                extension);

            state = McpServerState.openingInitial(state);
        }

        private void doAppFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            if (McpServerState.initialOpened(state))
            {
                doFlush(app, originId, routedId, replyId,
                    initialSeq, initialAck, initialMax, traceId, authorization,
                    budgetId, reserved);
            }
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (McpServerState.initialOpened(state) &&
                !McpServerState.initialClosed(state))
            {
                state = McpServerState.closedInitial(state);
                doEnd(app, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (McpServerState.initialOpened(state) &&
                !McpServerState.initialClosed(state))
            {
                state = McpServerState.closedInitial(state);
                doAbort(app, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        private void doAppWindow(
            long traceId,
            long authorization)
        {
            state = McpServerState.openedReply(state);
            // TODO:
            doWindow(app, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, 0, 0);
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (!McpServerState.replyClosed(state))
            {
                state = McpServerState.closedReply(state);
                doReset(app, originId, routedId, replyId,
                    replySeq, replyAck, replyMax,
                    traceId, authorization);
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
            final long authorization = begin.authorization();
            final OctetsFW extension = begin.extension();
            final McpBeginExFW mcpBeginEx = extension.sizeof() > 0
                ? mcpBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;
            final String sessionId = extractMcpSessionId(mcpBeginEx, server.sessionId);

            server.doNetBegin(traceId, authorization, sessionId);

            doAppWindow(traceId, authorization);
        }

        private void onAppData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                // TODO: server.doEncodeResponse(traceId, authorization, budgetId, flags, reserved, payload);
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            server.doNetEnd(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            server.doNetAbort(traceId, authorization);
        }

        private void onAppFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final int maximum = flush.maximum();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + decodeMax)
            {
                cleanupApp(traceId, authorization);
            }
            else
            {
                server.doNetFlush(traceId, authorization, budgetId, reserved);
            }
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

            initialSeq = sequence;
            initialAck = acknowledge;
            initialMax = maximum;

            doWindow(server.net, server.originId, server.routedId, server.initialId,
                initialSeq, initialAck, initialMax, traceId, authorization, budgetId, padding);
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
        McpBeginExFW mcpBeginEx,
        String defaultSessionId)
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
        return sessionId != null ? sessionId : defaultSessionId;
    }
}
