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
package io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.McpBeginExFW.KIND_LIFECYCLE;
import static io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.McpBeginExFW.KIND_TOOLS_CALL;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;
import java.util.zip.CRC32C;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.McpSchemaRegistryConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.McpSchemaRegistryBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.McpSchemaRegistryRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.OpenapiBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpSchemaRegistryProxyFactory implements BindingHandler
{
    private static final int WINDOW_MAX = 65536;
    private static final String BUNDLED_SPEC_RESOURCE =
        "/io/aklivity/zilla/runtime/binding/mcp/schema/registry/internal/schema/karapace-schema-registry.openapi.json";
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final FlushFW flushRO = new FlushFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    private final OpenapiBeginExFW openapiBeginExRO = new OpenapiBeginExFW();
    private final OpenapiBeginExFW.Builder openapiBeginExRW = new OpenapiBeginExFW.Builder();

    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();

    private final EngineContext context;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final BufferPool bufferPool;

    private final int mcpTypeId;
    private final int openapiTypeId;
    private final int httpTypeId;

    private final Long2ObjectHashMap<McpSchemaRegistryBindingConfig> bindings;
    private final Map<String, McpSession> sessions;
    private final Supplier<String> supplySessionId;

    private final byte[] bundledSpec;
    private final long apiId;

    public McpSchemaRegistryProxyFactory(
        McpSchemaRegistryConfiguration config,
        EngineContext context)
    {
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bufferPool = context.bufferPool();
        this.mcpTypeId = context.supplyTypeId("mcp");
        this.openapiTypeId = context.supplyTypeId("openapi");
        this.httpTypeId = context.supplyTypeId("http");
        this.bindings = new Long2ObjectHashMap<>();
        this.sessions = new HashMap<>();
        this.supplySessionId = config.sessionIdSupplier();
        this.bundledSpec = loadBundledSpec();
        this.apiId = computeApiId(bundledSpec);
    }

    public void attach(
        BindingConfig binding)
    {
        bindings.put(binding.id, new McpSchemaRegistryBindingConfig(binding));
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

        final McpSchemaRegistryBindingConfig binding = bindings.get(routedId);
        final McpBeginExFW beginEx = extension.get(mcpBeginExRO::tryWrap);

        if (binding != null && beginEx != null)
        {
            final int kind = beginEx.kind();

            if (kind == KIND_LIFECYCLE)
            {
                final int capabilities = beginEx.lifecycle().capabilities();
                final String sessionId = supplySessionId.get();
                newStream = new McpSession(sessionId, capabilities,
                    sender, originId, routedId, initialId, authorization, affinity)::onMcpMessage;
            }
            else if (kind == KIND_TOOLS_CALL)
            {
                final String sessionId = beginEx.toolsCall().sessionId().asString();
                final McpSession session = sessions.get(sessionId);

                if (session != null)
                {
                    final String name = beginEx.toolsCall().name().asString();
                    final int contentLength = beginEx.toolsCall().contentLength();
                    final McpSchemaRegistryOperation operation = McpSchemaRegistryOperations.lookup(name);
                    final McpSchemaRegistryRouteConfig route = binding.resolveTool(name);

                    if (operation != null && route != null)
                    {
                        newStream = new McpToolsCallProxy(route, operation, contentLength,
                            sender, originId, routedId, initialId, authorization, affinity)::onMcpMessage;
                    }
                }
            }
        }

        return newStream;
    }

    private byte[] loadBundledSpec()
    {
        byte[] bytes;
        try (InputStream input = getClass().getResourceAsStream(BUNDLED_SPEC_RESOURCE))
        {
            bytes = input != null ? input.readAllBytes() : new byte[0];
        }
        catch (Exception ex)
        {
            bytes = new byte[0];
        }
        return bytes;
    }

    private static long computeApiId(
        byte[] schema)
    {
        final CRC32C crc32c = new CRC32C();
        crc32c.update(schema, 0, schema.length);
        return (int) crc32c.getValue();
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
            state = McpSchemaRegistryState.openingInitial(state);

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
            state = McpSchemaRegistryState.closedInitial(state);
            sessions.remove(sessionId, this);
            doMcpReplyEnd(traceId);
        }

        private void onMcpAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            state = McpSchemaRegistryState.closedInitial(state);
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
            state = McpSchemaRegistryState.closedReply(state);
            sessions.remove(sessionId, this);
        }

        private void doMcpWindow(
            long traceId)
        {
            initialMax = WINDOW_MAX;
            initialAck = initialSeq;
            state = McpSchemaRegistryState.openedInitial(state);
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0);
        }

        private void doMcpReplyBegin(
            long traceId)
        {
            if (!McpSchemaRegistryState.replyOpened(state))
            {
                final McpBeginExFW lifecycleEx = mcpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .lifecycle(l -> l
                        .sessionId(sessionId)
                        .capabilities(capabilities))
                    .build();
                doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, lifecycleEx);
                state = McpSchemaRegistryState.openedReply(state);
            }
        }

        private void doMcpReplyEnd(
            long traceId)
        {
            if (!McpSchemaRegistryState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                    EMPTY_OCTETS);
                state = McpSchemaRegistryState.closedReply(state);
            }
        }

        private void doMcpReplyAbort(
            long traceId)
        {
            if (!McpSchemaRegistryState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                    EMPTY_OCTETS);
                state = McpSchemaRegistryState.closedReply(state);
            }
        }
    }

    private final class McpToolsCallProxy
    {
        private final McpSchemaRegistryRouteConfig route;
        private final McpSchemaRegistryOperation operation;
        private final int contentLength;
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long authorization;
        private final long affinity;
        private final OpenapiProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int decodeSlot = BufferPool.NO_SLOT;
        private int decodeSlotOffset;

        private McpToolsCallProxy(
            McpSchemaRegistryRouteConfig route,
            McpSchemaRegistryOperation operation,
            int contentLength,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            this.route = route;
            this.operation = operation;
            this.contentLength = contentLength;
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.affinity = affinity;
            this.delegate = new OpenapiProxy(this, route.exitId);
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
            state = McpSchemaRegistryState.openingInitial(state);

            doMcpWindow(traceId);

            if (contentLength <= 0)
            {
                onRequestReady(traceId, Json.createObjectBuilder().build());
            }
        }

        private void onMcpData(
            DataFW data)
        {
            final long traceId = data.traceId();
            initialSeq = data.sequence() + data.reserved();

            final OctetsFW payload = data.payload();
            if (payload != null)
            {
                if (decodeSlot == BufferPool.NO_SLOT)
                {
                    decodeSlot = bufferPool.acquire(initialId);
                }

                if (decodeSlot == BufferPool.NO_SLOT)
                {
                    cleanup(traceId);
                }
                else
                {
                    final MutableDirectBufferEx slot = bufferPool.buffer(decodeSlot);
                    slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                    decodeSlotOffset += payload.sizeof();

                    if (decodeSlotOffset >= contentLength)
                    {
                        onRequestBuffered(traceId);
                    }
                }
            }
        }

        private void onRequestBuffered(
            long traceId)
        {
            final MutableDirectBufferEx slot = bufferPool.buffer(decodeSlot);
            final byte[] copy = new byte[decodeSlotOffset];
            slot.getBytes(0, copy, 0, decodeSlotOffset);
            cleanupDecodeSlot();

            try (JsonReader reader = Json.createReader(new ByteArrayInputStream(copy)))
            {
                final JsonObject call = reader.readObject();
                final JsonObject args = call.containsKey("arguments")
                    ? call.getJsonObject("arguments")
                    : Json.createObjectBuilder().build();
                onRequestReady(traceId, args);
            }
            catch (JsonException ex)
            {
                doMcpReset(traceId);
            }
        }

        private void onRequestReady(
            long traceId,
            JsonObject args)
        {
            final String path = McpSchemaRegistryOperations.resolvePath(operation, args);
            final JsonObject body = operation.hasRequestBody
                ? McpSchemaRegistryOperations.resolveRequestBody(args)
                : null;
            delegate.readyRequest(traceId, path, body);
        }

        private void onMcpEnd(
            EndFW end)
        {
            initialSeq = end.sequence();
            state = McpSchemaRegistryState.closedInitial(state);
        }

        private void onMcpAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            state = McpSchemaRegistryState.closedInitial(state);
            cleanupDecodeSlot();
            delegate.doOpenapiAbort(traceId);
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
            state = McpSchemaRegistryState.closedReply(state);
            cleanupDecodeSlot();
            delegate.doOpenapiAbort(reset.traceId());
        }

        private void doMcpWindow(
            long traceId)
        {
            initialMax = WINDOW_MAX;
            initialAck = initialSeq;
            state = McpSchemaRegistryState.openedInitial(state);
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0);
        }

        private void doMcpReplyBegin(
            long traceId)
        {
            if (!McpSchemaRegistryState.replyOpened(state))
            {
                doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, EMPTY_OCTETS);
                state = McpSchemaRegistryState.openedReply(state);
            }
        }

        private void doMcpReplyData(
            long traceId,
            byte[] payload)
        {
            final OctetsFW octets = new OctetsFW().wrap(new UnsafeBufferEx(payload), 0, payload.length);
            doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, 0L, 0x03, payload.length, octets, EMPTY_OCTETS);
            replySeq += payload.length;
        }

        private void doMcpReplyEnd(
            long traceId)
        {
            if (!McpSchemaRegistryState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                    EMPTY_OCTETS);
                state = McpSchemaRegistryState.closedReply(state);
            }
        }

        private void doMcpReset(
            long traceId)
        {
            if (!McpSchemaRegistryState.initialClosed(state))
            {
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
                    authorization, EMPTY_OCTETS);
                state = McpSchemaRegistryState.closedInitial(state);
            }
        }

        private void onResponseComplete(
            long traceId,
            boolean ok,
            byte[] responseBody)
        {
            final byte[] mcpResult = wrapMcpResult(ok, responseBody);
            doMcpReplyBegin(traceId);
            doMcpReplyData(traceId, mcpResult);
            doMcpReplyEnd(traceId);
        }

        private byte[] wrapMcpResult(
            boolean ok,
            byte[] responseBody)
        {
            JsonObjectBuilder result = Json.createObjectBuilder();
            if (ok)
            {
                JsonValue responseValue;
                try (JsonReader reader = Json.createReader(new ByteArrayInputStream(responseBody)))
                {
                    responseValue = reader.readValue();
                }
                catch (JsonException | IllegalStateException ex)
                {
                    responseValue = Json.createValue(new String(responseBody, StandardCharsets.UTF_8));
                }

                final JsonValue structured = operation.wrapKey == null
                    ? responseValue
                    : Json.createObjectBuilder().add(operation.wrapKey, responseValue).build();

                result.add("structuredContent", structured);
                result.add("content", Json.createArrayBuilder()
                    .add(Json.createObjectBuilder()
                        .add("type", "text")
                        .add("text", operation.summary)));
                result.add("isError", false);
            }
            else
            {
                result.add("content", Json.createArrayBuilder()
                    .add(Json.createObjectBuilder()
                        .add("type", "text")
                        .add("text", new String(responseBody, StandardCharsets.UTF_8))));
                result.add("isError", true);
            }

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (JsonWriter writer = Json.createWriter(out))
            {
                writer.writeObject(result.build());
            }
            return out.toByteArray();
        }

        private void cleanup(
            long traceId)
        {
            cleanupDecodeSlot();
            doMcpReset(traceId);
            delegate.doOpenapiAbort(traceId);
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
    }

    private final class OpenapiProxy
    {
        private final McpToolsCallProxy mcp;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private MessageConsumer receiver;

        private String path;
        private byte[] bodyBytes;
        private int bodyBytesSent;
        private boolean requestReady;
        private boolean windowReady;

        private int decodeSlot = BufferPool.NO_SLOT;
        private int decodeSlotOffset;
        private boolean responseOk;

        private OpenapiProxy(
            McpToolsCallProxy mcp,
            long routedId)
        {
            this.mcp = mcp;
            this.originId = mcp.routedId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void readyRequest(
            long traceId,
            String path,
            JsonObject body)
        {
            this.path = path;
            this.bodyBytes = body != null ? toBytes(body) : null;
            this.requestReady = true;
            doOpenapiBegin(traceId);
        }

        private void onOpenapiMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onOpenapiBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onOpenapiData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onOpenapiEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onOpenapiAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                onOpenapiWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onOpenapiReset(resetRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onOpenapiBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final OpenapiBeginExFW openapiBeginEx = extension.get(openapiBeginExRO::tryWrap);

            replySeq = begin.sequence();
            replyAck = begin.acknowledge();
            state = McpSchemaRegistryState.openedReply(state);

            boolean ok = true;
            if (openapiBeginEx != null)
            {
                final OctetsFW nested = openapiBeginEx.extension();
                final HttpBeginExFW httpBeginEx = nested.get(httpBeginExRO::tryWrap);
                if (httpBeginEx != null)
                {
                    final HttpHeaderFW statusHeader = httpBeginEx.headers()
                        .matchFirst(h -> ":status".equals(h.name().asString()));
                    final String status = statusHeader != null ? statusHeader.value().asString() : null;
                    ok = status != null && status.startsWith("2");
                }
            }
            this.responseOk = ok;

            doOpenapiReplyWindow(traceId);
        }

        private void onOpenapiData(
            DataFW data)
        {
            final long traceId = data.traceId();
            replySeq = data.sequence() + data.reserved();

            final OctetsFW payload = data.payload();
            if (payload != null)
            {
                if (decodeSlot == BufferPool.NO_SLOT)
                {
                    decodeSlot = bufferPool.acquire(replyId);
                }

                if (decodeSlot == BufferPool.NO_SLOT)
                {
                    doOpenapiReset(traceId);
                }
                else
                {
                    final MutableDirectBufferEx slot = bufferPool.buffer(decodeSlot);
                    slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                    decodeSlotOffset += payload.sizeof();
                }
            }
        }

        private void onOpenapiEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            replySeq = end.sequence();
            state = McpSchemaRegistryState.closedReply(state);

            final byte[] responseBody;
            if (decodeSlot != BufferPool.NO_SLOT)
            {
                final MutableDirectBufferEx slot = bufferPool.buffer(decodeSlot);
                responseBody = new byte[decodeSlotOffset];
                slot.getBytes(0, responseBody, 0, decodeSlotOffset);
                cleanupDecodeSlot();
            }
            else
            {
                responseBody = new byte[0];
            }

            mcp.onResponseComplete(traceId, responseOk, responseBody);
        }

        private void onOpenapiAbort(
            AbortFW abort)
        {
            state = McpSchemaRegistryState.closedReply(state);
            cleanupDecodeSlot();
        }

        private void onOpenapiWindow(
            WindowFW window)
        {
            initialAck = window.acknowledge();
            initialMax = window.maximum();
            state = McpSchemaRegistryState.openedInitial(state);
            windowReady = true;

            flushRequest(window.traceId());
        }

        private void onOpenapiReset(
            ResetFW reset)
        {
            state = McpSchemaRegistryState.closedInitial(state);
        }

        private void doOpenapiBegin(
            long traceId)
        {
            if (!McpSchemaRegistryState.initialOpening(state))
            {
                final HttpBeginExFW httpBeginEx = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(httpTypeId)
                    .headers(hs ->
                    {
                        hs.item(h -> h.name(":method").value(mcp.operation.method));
                        hs.item(h -> h.name(":scheme").value("http"));
                        hs.item(h -> h.name(":path").value(path));
                        hs.item(h -> h.name(":authority").value("localhost:8080"));
                        if (bodyBytes != null)
                        {
                            hs.item(h -> h.name("content-type").value("application/json"));
                            hs.item(h -> h.name("content-length").value(Integer.toString(bodyBytes.length)));
                        }
                        else
                        {
                            hs.item(h -> h.name("content-length").value("0"));
                        }
                    })
                    .build();

                final OpenapiBeginExFW openapiBeginEx = openapiBeginExRW
                    .wrap(extBuffer, httpBeginEx.limit(), extBuffer.capacity())
                    .typeId(openapiTypeId)
                    .apiId(apiId)
                    .operationId(mcp.operation.operationId)
                    .extension(httpBeginEx.buffer(), httpBeginEx.offset(), httpBeginEx.sizeof())
                    .build();

                receiver = newStream(this::onOpenapiMessage, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, mcp.authorization, mcp.affinity, openapiBeginEx);
                state = McpSchemaRegistryState.openingInitial(state);
            }
        }

        private void flushRequest(
            long traceId)
        {
            if (requestReady && windowReady && McpSchemaRegistryState.initialOpening(state) &&
                !McpSchemaRegistryState.initialClosed(state))
            {
                int maxPayload = initialMax - (int)(initialSeq - initialAck);
                while (bodyBytes != null && bodyBytesSent < bodyBytes.length && maxPayload > 0)
                {
                    final int length = Math.min(maxPayload, bodyBytes.length - bodyBytesSent);
                    final OctetsFW payload = new OctetsFW()
                        .wrap(new UnsafeBufferEx(bodyBytes), bodyBytesSent, bodyBytesSent + length);
                    doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, mcp.authorization, 0L, 0x03, length, payload, EMPTY_OCTETS);
                    initialSeq += length;
                    bodyBytesSent += length;
                    maxPayload = initialMax - (int)(initialSeq - initialAck);
                }

                if (bodyBytes == null || bodyBytesSent >= bodyBytes.length)
                {
                    doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, mcp.authorization, EMPTY_OCTETS);
                    state = McpSchemaRegistryState.closedInitial(state);
                }
            }
        }

        private void doOpenapiReplyWindow(
            long traceId)
        {
            replyMax = WINDOW_MAX;
            replyAck = replySeq;
            doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, mcp.authorization, 0L, 0);
        }

        private void doOpenapiAbort(
            long traceId)
        {
            if (receiver != null && !McpSchemaRegistryState.initialClosed(state))
            {
                doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, mcp.authorization, EMPTY_OCTETS);
                state = McpSchemaRegistryState.closedInitial(state);
            }
        }

        private void doOpenapiReset(
            long traceId)
        {
            if (!McpSchemaRegistryState.replyClosed(state))
            {
                doReset(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId,
                    mcp.authorization, EMPTY_OCTETS);
                state = McpSchemaRegistryState.closedReply(state);
            }
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
    }

    private static byte[] toBytes(
        JsonObject object)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonWriter writer = Json.createWriter(out))
        {
            writer.writeObject(object);
        }
        return out.toByteArray();
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
        long budgetId,
        int flags,
        int reserved,
        OctetsFW payload,
        Flyweight extension)
    {
        final DataFW frame = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
            .payload(payload)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());
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
        OctetsFW extension)
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
            .extension(extension)
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
        long authorization,
        OctetsFW extension)
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
            .extension(extension)
            .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doWindow(
        MessageConsumer sender,
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

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
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
        OctetsFW extension)
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
