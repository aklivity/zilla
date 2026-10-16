/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.grpc.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.stream.GrpcType.TEXT;
import static io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.stream.McpBeginExFW.KIND_LIFECYCLE;
import static io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.stream.McpBeginExFW.KIND_TOOLS_CALL;
import static io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static io.aklivity.zilla.runtime.engine.util.Flags.NONE;
import static io.aklivity.zilla.runtime.engine.util.Flags.hasFin;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.McpGrpcConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.config.McpGrpcBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.config.McpGrpcCompositeConfig;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.config.McpGrpcCompositeGenerator;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.config.McpGrpcTool;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.stream.GrpcAbortExFW;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.stream.GrpcDataExFW;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.stream.McpResetExFW;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipelineResult;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.json.ProtobufJson;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class McpGrpcClientFactory implements BindingHandler
{
    private static final String MCP_TYPE_NAME = "mcp";
    private static final String GRPC_TYPE_NAME = "grpc";

    private static final int WINDOW_MAX = 65536;
    private static final int JSON_RPC_INVALID_PARAMS = -32602;

    private static final byte[] RESULT_TEXT_PREFIX = "{\"content\":[{\"type\":\"text\",\"text\":\"".getBytes(UTF_8);
    private static final byte[] RESULT_TEXT_SUFFIX = "\"}],\"isError\":false}".getBytes(UTF_8);
    private static final byte[] RESULT_ERROR_SUFFIX = "\"}],\"isError\":true}".getBytes(UTF_8);
    private static final byte[] RESULT_STRUCTURED_PREFIX = "{\"structuredContent\":".getBytes(UTF_8);
    private static final byte[] RESULT_SUMMARY_PREFIX = ",\"content\":[{\"type\":\"text\",\"text\":\"".getBytes(UTF_8);
    private static final byte[] RESULT_SUMMARY_SUFFIX = "\"}],\"isError\":false}".getBytes(UTF_8);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final GrpcAbortExFW grpcAbortExRO = new GrpcAbortExFW();
    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();
    private final McpResetExFW.Builder mcpResetExRW = new McpResetExFW.Builder();
    private final GrpcBeginExFW.Builder grpcBeginExRW = new GrpcBeginExFW.Builder();
    private final GrpcDataExFW.Builder grpcDataExRW = new GrpcDataExFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final MutableDirectBufferEx transformBuffer;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int mcpTypeId;
    private final int grpcTypeId;
    private final EngineContext context;
    private final Long2ObjectHashMap<McpGrpcBindingConfig> bindings;
    private final McpGrpcCompositeGenerator generator;
    private final Map<String, McpSession> sessions;
    private final Map<McpGrpcTool, ProtobufPipeline> requestPipelines;
    private final LongFunction<String> supplySessionId;
    private final long affinity;
    private final String clientExit;

    public McpGrpcClientFactory(
        McpGrpcConfiguration config,
        EngineContext context)
    {
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.transformBuffer = new UnsafeBufferEx(new byte[context.bufferPool().slotCapacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.grpcTypeId = context.supplyTypeId(GRPC_TYPE_NAME);
        this.requestPipelines = new IdentityHashMap<>();
        this.bindings = new Long2ObjectHashMap<>();
        this.generator = new McpGrpcCompositeGenerator();
        this.sessions = new Object2ObjectHashMap<>();
        this.supplySessionId = config.sessionIdSupplier();
        this.affinity = context.affinity();
        this.clientExit = config.clientExit();
    }

    public void attach(
        BindingConfig binding)
    {
        final McpGrpcBindingConfig attached = new McpGrpcBindingConfig(binding, context, clientExit);
        final McpGrpcCompositeConfig composite = generator.generate(attached);
        composite.namespaces.forEach(context::attachComposite);
        attached.composite = composite;
        bindings.put(binding.id, attached);
    }

    public void detach(
        long bindingId)
    {
        final McpGrpcBindingConfig binding = bindings.remove(bindingId);
        if (binding != null && binding.composite != null)
        {
            binding.composite.namespaces.forEach(context::detachComposite);
        }
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
        final long authorization = begin.authorization();
        final long affinity = begin.affinity();
        final OctetsFW extension = begin.extension();

        MessageConsumer newStream = null;

        final McpGrpcBindingConfig binding = bindings.get(routedId);
        final McpBeginExFW beginEx = extension.get(mcpBeginExRO::tryWrap);

        if (binding != null && beginEx != null)
        {
            final int kind = beginEx.kind();

            if (kind == KIND_LIFECYCLE)
            {
                newStream = new McpSession(supplySessionId.apply(this.affinity), binding.serverCapabilities(),
                    sender, originId, routedId, initialId, authorization, affinity)::onMcpMessage;
            }
            else if (kind == KIND_TOOLS_LIST && sessions.containsKey(beginEx.toolsList().sessionId().asString()))
            {
                newStream = new McpToolsListProxy(binding,
                    sender, originId, routedId, initialId, authorization, affinity)::onMcpMessage;
            }
            else if (kind == KIND_TOOLS_CALL && sessions.containsKey(beginEx.toolsCall().sessionId().asString()))
            {
                final McpGrpcTool tool = binding.resolveTool(beginEx.toolsCall().name().asString(), authorization);
                if (tool != null)
                {
                    newStream = new McpToolsCallProxy(binding, tool, beginEx.toolsCall().contentLength(),
                        sender, originId, routedId, initialId, authorization, affinity)::onMcpMessage;
                }
            }
        }

        return newStream;
    }

    private abstract class McpProxy
    {
        final McpGrpcBindingConfig binding;
        final MessageConsumer sender;
        final long originId;
        final long routedId;
        final long initialId;
        final long replyId;
        final long authorization;
        final long affinity;

        int state;

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
            McpGrpcBindingConfig binding,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            this.binding = binding;
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

        void onMcpBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            state = McpGrpcState.openingInitial(state);

            doMcpWindow(traceId);
            onMcpRequest(traceId);
        }

        void onMcpData(
            DataFW data)
        {
            initialSeq = data.sequence() + data.reserved();
        }

        void onMcpEnd(
            EndFW end)
        {
            initialSeq = end.sequence();
            state = McpGrpcState.closedInitial(state);
        }

        void onMcpAbort(
            AbortFW abort)
        {
            state = McpGrpcState.closedInitial(state);
            cleanupEncodeSlot();
        }

        void onMcpWindow(
            WindowFW window)
        {
            replyAck = window.acknowledge();
            replyMax = window.maximum();
            replyPad = window.padding();

            flushReply(window.traceId());
        }

        void onMcpReset(
            ResetFW reset)
        {
            state = McpGrpcState.closedReply(state);
            cleanupEncodeSlot();
        }

        abstract void onMcpRequest(
            long traceId);

        void doMcpReply(
            long traceId,
            byte[] reply)
        {
            if (acquireEncodeSlot())
            {
                encodePool.buffer(encodeSlot).putBytes(encodeSlotOffset, reply);
                encodeSlotOffset += reply.length;
                state = McpGrpcState.closingReply(state);
                doMcpBegin(traceId);
                flushReply(traceId);
            }
            else
            {
                cleanup(traceId);
            }
        }

        boolean acquireEncodeSlot()
        {
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(replyId);
            }

            return encodeSlot != NO_SLOT;
        }

        void flushReply(
            long traceId)
        {
            if (encodeSlot != NO_SLOT && McpGrpcState.replyOpened(state))
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                int maxPayload = replyMax - (int)(replySeq - replyAck) - replyPad;
                while (encodeSlotOffset > 0 && maxPayload > 0)
                {
                    final int length = Math.min(maxPayload, encodeSlotOffset);
                    final int reserved = length + replyPad;
                    final boolean fin = McpGrpcState.replyClosing(state) && length == encodeSlotOffset;
                    final int flags = (replyDataStarted ? NONE : INIT) | (fin ? FIN : NONE);
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

                if (McpGrpcState.replyClosing(state) && encodeSlotOffset == 0)
                {
                    doMcpEnd(traceId);
                }
            }
        }

        void doMcpBegin(
            long traceId)
        {
            if (!McpGrpcState.replyOpened(state))
            {
                doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, emptyRO);
                state = McpGrpcState.openedReply(state);
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
            if (!McpGrpcState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = McpGrpcState.closedReply(state);
            }
            cleanupEncodeSlot();
            cleanupResources();
        }

        void doMcpAbort(
            long traceId)
        {
            doMcpBegin(traceId);
            if (!McpGrpcState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = McpGrpcState.closedReply(state);
            }
            cleanupEncodeSlot();
            cleanupResources();
        }

        void doMcpWindow(
            long traceId)
        {
            initialMax = initialWindowMax();
            initialAck = initialSeq;
            state = McpGrpcState.openedInitial(state);
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0);
        }

        int initialWindowMax()
        {
            return WINDOW_MAX;
        }

        void cleanupResources()
        {
        }

        void doMcpReset(
            long traceId)
        {
            if (!McpGrpcState.initialClosed(state))
            {
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
                state = McpGrpcState.closedInitial(state);
            }
        }

        void doMcpReset(
            long traceId,
            int code,
            String message)
        {
            if (!McpGrpcState.initialClosed(state))
            {
                final McpResetExFW resetEx = mcpResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .error(e -> e
                        .code(code)
                        .message(message))
                    .build();
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, resetEx);
                state = McpGrpcState.closedInitial(state);
            }
        }

        void cleanup(
            long traceId)
        {
            doMcpReset(traceId);
            doMcpAbort(traceId);
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

    private final class McpToolsListProxy extends McpProxy
    {
        private McpToolsListProxy(
            McpGrpcBindingConfig binding,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            super(binding, sender, originId, routedId, initialId, authorization, affinity);
        }

        @Override
        void onMcpRequest(
            long traceId)
        {
            doMcpReply(traceId, binding.toolsListJson());
        }
    }

    private final class McpToolsCallProxy extends McpProxy
    {
        private final McpGrpcTool tool;
        private final int contentLength;
        private final boolean summarized;
        private final long grpcRoutedId;
        private final long grpcInitialId;
        private final long grpcReplyId;

        private MessageConsumer grpc;
        private int grpcState;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private int requestSlot = NO_SLOT;
        private int requestSlotLimit;
        private int requestSlotProgress;
        private boolean requestDataStarted;

        private long grpcInitialSeq;
        private long grpcInitialAck;
        private int grpcInitialMax;
        private int grpcInitialPad;

        private long grpcReplySeq;
        private long grpcReplyAck;
        private int grpcReplyMax;

        private int replySlot = NO_SLOT;
        private int replySlotOffset;
        private ProtobufPipeline replyPipeline;
        private boolean replyMessageComplete;
        private boolean replyBodyComplete;
        private boolean replyPrefixed;
        private boolean replySuffixed;
        private boolean replyAborting;
        private boolean grpcEnded;

        private McpToolsCallProxy(
            McpGrpcBindingConfig binding,
            McpGrpcTool tool,
            int contentLength,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            super(binding, sender, originId, routedId, initialId, authorization, affinity);
            this.tool = tool;
            this.contentLength = contentLength;
            this.summarized = tool.summary != null;
            this.grpcRoutedId = binding.composite.routeId;
            this.grpcInitialId = supplyInitialId.applyAsLong(grpcRoutedId);
            this.grpcReplyId = supplyReplyId.applyAsLong(grpcInitialId);
        }

        @Override
        int initialWindowMax()
        {
            return decodePool.slotCapacity();
        }

        @Override
        void onMcpRequest(
            long traceId)
        {
            if (contentLength > decodePool.slotCapacity())
            {
                doMcpReset(traceId, JSON_RPC_INVALID_PARAMS, "request too large");
                doMcpAbort(traceId);
            }
        }

        @Override
        void onMcpData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            initialSeq = data.sequence() + data.reserved();

            if (payload != null && grpc == null)
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
                    decodePool.buffer(decodeSlot).putBytes(decodeSlotOffset,
                        payload.buffer(), payload.offset(), payload.sizeof());
                    decodeSlotOffset += payload.sizeof();

                    if (contentLength >= 0 && decodeSlotOffset >= contentLength)
                    {
                        openRequest(traceId);
                    }
                }
            }
        }

        @Override
        void onMcpEnd(
            EndFW end)
        {
            super.onMcpEnd(end);

            if (grpc == null && !McpGrpcState.replyClosed(state))
            {
                openRequest(end.traceId());
            }
        }

        @Override
        void onMcpAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = McpGrpcState.closedInitial(state);
            cleanupDecodeSlot();

            if (grpc != null)
            {
                doGrpcAbort(traceId);
            }
            else
            {
                doMcpAbort(traceId);
            }
        }

        @Override
        void onMcpWindow(
            WindowFW window)
        {
            super.onMcpWindow(window);
            pumpReply(window.traceId());
            completeReplyAbort(window.traceId());
        }

        @Override
        void onMcpReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpGrpcState.closedReply(state);
            cleanupEncodeSlot();

            if (grpc != null)
            {
                doGrpcReset(traceId);
                doGrpcAbort(traceId);
            }
            doMcpReset(traceId);
            cleanupResources();
        }

        @Override
        void cleanupResources()
        {
            cleanupDecodeSlot();
            cleanupRequestSlot();
            cleanupReplySlot();
        }

        private void openRequest(
            long traceId)
        {
            boolean converted = false;

            if (decodeSlot != NO_SLOT)
            {
                final byte[] request = new byte[decodeSlotOffset];
                decodePool.buffer(decodeSlot).getBytes(0, request);
                cleanupDecodeSlot();

                converted = convertRequest(request);
            }

            if (converted)
            {
                doGrpcBegin(traceId);
            }
            else
            {
                doMcpReset(traceId, JSON_RPC_INVALID_PARAMS, "invalid arguments for tool %s".formatted(tool.name));
                doMcpAbort(traceId);
            }
        }

        private boolean convertRequest(
            byte[] request)
        {
            boolean converted = false;

            try (JsonReader reader = Json.createReader(new ByteArrayInputStream(request)))
            {
                final JsonObject params = reader.readObject();
                final JsonValue arguments = params.get("arguments");
                final byte[] json = (arguments != null ? arguments.toString() : "{}").getBytes(UTF_8);

                requestSlot = decodePool.acquire(initialId);
                if (requestSlot != NO_SLOT)
                {
                    ProtobufPipeline pipeline = requestPipelines.get(tool);
                    if (pipeline == null)
                    {
                        pipeline = newRequestPipeline(binding.schema(), tool);
                        requestPipelines.put(tool, pipeline);
                    }
                    pipeline.reset();
                    final MutableDirectBufferEx slot = decodePool.buffer(requestSlot);
                    final ProtobufPipelineResult result =
                        pipeline.transform(new UnsafeBufferEx(json), 0, json.length, true, slot, 0, slot.capacity());

                    if (result.status() == ProtobufPipeline.Status.COMPLETED)
                    {
                        requestSlotLimit = result.produced();
                        converted = true;
                    }
                }
            }
            catch (JsonException | ClassCastException ex)
            {
                converted = false;
            }

            return converted;
        }

        private void doGrpcBegin(
            long traceId)
        {
            final Map<String, String> credentials = new HashMap<>();
            binding.resolveCredentials(authorization, credentials);

            final GrpcBeginExFW grpcBeginEx = grpcBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(grpcTypeId)
                .scheme(binding.scheme)
                .authority(binding.authority)
                .service(tool.service)
                .method(tool.method)
                .metadata(ms ->
                {
                    for (Map.Entry<String, String> entry : credentials.entrySet())
                    {
                        final byte[] name = entry.getKey().getBytes(UTF_8);
                        final byte[] value = entry.getValue().getBytes(UTF_8);
                        ms.item(m -> m
                            .type(t -> t.set(TEXT))
                            .nameLen(name.length)
                            .name(new UnsafeBufferEx(name), 0, name.length)
                            .valueLen(value.length)
                            .value(new UnsafeBufferEx(value), 0, value.length));
                    }
                })
                .build();

            grpcState = McpGrpcState.openingInitial(grpcState);

            grpc = newStream(this::onGrpcMessage, routedId, grpcRoutedId, grpcInitialId,
                grpcInitialSeq, grpcInitialAck, grpcInitialMax, traceId, authorization, affinity, grpcBeginEx);
        }

        private void onGrpcMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onGrpcBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onGrpcData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onGrpcEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onGrpcAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                onGrpcWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onGrpcReset(resetRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onGrpcBegin(
            BeginFW begin)
        {
            grpcReplySeq = begin.sequence();
            grpcReplyAck = begin.acknowledge();
            grpcState = McpGrpcState.openedReply(grpcState);

            doGrpcReplyWindow(begin.traceId());
        }

        private void onGrpcData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            grpcReplySeq = data.sequence() + data.reserved();

            if (payload != null)
            {
                if (replySlot == NO_SLOT)
                {
                    replySlot = decodePool.acquire(grpcReplyId);
                }

                if (replySlot == NO_SLOT || replySlotOffset + payload.sizeof() > decodePool.slotCapacity())
                {
                    doGrpcAbort(traceId);
                    doGrpcReset(traceId);
                    cleanup(traceId);
                }
                else
                {
                    decodePool.buffer(replySlot).putBytes(replySlotOffset,
                        payload.buffer(), payload.offset(), payload.sizeof());
                    replySlotOffset += payload.sizeof();
                    replyMessageComplete |= hasFin(data.flags());

                    pumpReply(traceId);
                }
            }
        }

        private void onGrpcEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            grpcReplySeq = end.sequence();
            grpcState = McpGrpcState.closedReply(grpcState);
            grpcEnded = true;
            replyMessageComplete = true;

            if (replySlot == NO_SLOT)
            {
                replySlot = decodePool.acquire(grpcReplyId);
            }

            pumpReply(traceId);
        }

        private void onGrpcAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final GrpcAbortExFW abortEx = abort.extension().get(grpcAbortExRO::tryWrap);

            grpcState = McpGrpcState.closedReply(grpcState);

            final String status = abortEx != null ? abortEx.status().asString() : null;
            final String message = abortEx != null && abortEx.message() != null ? abortEx.message().asString() : null;

            if (!replyPrefixed && !McpGrpcState.replyClosed(state) && !McpGrpcStatus.transport(status))
            {
                doToolError(traceId, McpGrpcStatus.describe(status, message));
            }
            else if (replyPrefixed && !McpGrpcState.replyClosed(state))
            {
                replyAborting = true;
                flushReply(traceId);
                completeReplyAbort(traceId);
            }
            else
            {
                doMcpAbort(traceId);
            }
        }

        private void completeReplyAbort(
            long traceId)
        {
            if (replyAborting && encodeSlotOffset == 0)
            {
                doMcpAbort(traceId);
            }
        }

        private void onGrpcWindow(
            WindowFW window)
        {
            grpcInitialAck = window.acknowledge();
            grpcInitialMax = window.maximum();
            grpcInitialPad = window.padding();
            grpcState = McpGrpcState.openedInitial(grpcState);

            pumpRequest(window.traceId());
        }

        private void onGrpcReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            grpcState = McpGrpcState.closedInitial(grpcState);
            cleanupRequestSlot();

            doMcpReset(traceId);
        }

        private void pumpRequest(
            long traceId)
        {
            if (requestSlot != NO_SLOT && !McpGrpcState.initialClosed(grpcState))
            {
                final MutableDirectBufferEx slot = decodePool.buffer(requestSlot);

                int maxPayload = grpcInitialMax - (int)(grpcInitialSeq - grpcInitialAck) - grpcInitialPad;
                while ((requestSlotProgress < requestSlotLimit || !requestDataStarted) && maxPayload >= 0 &&
                    (maxPayload > 0 || requestSlotLimit == requestSlotProgress))
                {
                    final int remaining = requestSlotLimit - requestSlotProgress;
                    final int length = Math.min(maxPayload, remaining);
                    final boolean fin = length == remaining;
                    final int flags = (requestDataStarted ? NONE : INIT) | (fin ? FIN : NONE);
                    final int deferred = requestDataStarted ? 0 : remaining - length;

                    doGrpcData(traceId, flags, length + grpcInitialPad, deferred, slot, requestSlotProgress, length);

                    requestSlotProgress += length;
                    requestDataStarted = true;
                    maxPayload = grpcInitialMax - (int)(grpcInitialSeq - grpcInitialAck) - grpcInitialPad;
                }

                if (requestDataStarted && requestSlotProgress == requestSlotLimit)
                {
                    doGrpcEnd(traceId);
                    cleanupRequestSlot();
                }
            }
        }

        private void pumpReply(
            long traceId)
        {
            if (replySlot != NO_SLOT && !McpGrpcState.replyClosed(state) && acquireEncodeSlot())
            {
                if (replyPipeline == null)
                {
                    replyPipeline = newReplyPipeline(binding.schema(), tool);
                    replyPipeline.reset();
                }

                if (!replyPrefixed)
                {
                    putReply(summarized ? RESULT_STRUCTURED_PREFIX : RESULT_TEXT_PREFIX);
                    replyPrefixed = true;
                    doMcpBegin(traceId);
                }

                final int ratio = summarized ? 1 : McpGrpcJson.maxEscaped(1);
                boolean pumping = true;
                while (pumping && !replyBodyComplete)
                {
                    final int space = encodePool.slotCapacity() - encodeSlotOffset;
                    final int limit = Math.min(transformBuffer.capacity(), space / ratio);
                    final MutableDirectBufferEx slot = decodePool.buffer(replySlot);

                    pumping = false;
                    if (limit > 0)
                    {
                        final ProtobufPipelineResult result = replyPipeline.transform(
                            slot, 0, replySlotOffset, replyMessageComplete, transformBuffer, 0, limit);
                        final ProtobufPipeline.Status status = result.status();
                        final int consumed = result.consumed();
                        final int produced = result.produced();

                        putReply(transformBuffer, produced);
                        if (consumed > 0)
                        {
                            slot.putBytes(0, slot, consumed, replySlotOffset - consumed);
                            replySlotOffset -= consumed;
                        }

                        switch (status)
                        {
                        case COMPLETED:
                            replyBodyComplete = true;
                            break;
                        case REJECTED:
                            doGrpcAbort(traceId);
                            doGrpcReset(traceId);
                            doMcpAbort(traceId);
                            break;
                        case SUSPENDED:
                            pumping = produced > 0 || consumed > 0;
                            break;
                        default:
                            break;
                        }
                    }
                }

                if (replyBodyComplete && grpcEnded && !replySuffixed)
                {
                    final byte[] suffix = summarized ? summarySuffix() : RESULT_TEXT_SUFFIX;
                    if (encodePool.slotCapacity() - encodeSlotOffset >= suffix.length)
                    {
                        putReply(suffix);
                        replySuffixed = true;
                        state = McpGrpcState.closingReply(state);
                    }
                }

                if (!McpGrpcState.replyClosed(state))
                {
                    doGrpcReplyWindow(traceId);
                    flushReply(traceId);
                }
            }
        }

        private byte[] summarySuffix()
        {
            final byte[] summary = tool.summary.getBytes(UTF_8);
            final MutableDirectBufferEx scratch = new UnsafeBufferEx(
                new byte[RESULT_SUMMARY_PREFIX.length + McpGrpcJson.maxEscaped(summary.length) + RESULT_SUMMARY_SUFFIX.length]);

            scratch.putBytes(0, RESULT_SUMMARY_PREFIX);
            int limit = RESULT_SUMMARY_PREFIX.length;
            limit = McpGrpcJson.putEscaped(scratch, limit, tool.summary);
            scratch.putBytes(limit, RESULT_SUMMARY_SUFFIX);
            limit += RESULT_SUMMARY_SUFFIX.length;

            final byte[] result = new byte[limit];
            scratch.getBytes(0, result);
            return result;
        }

        private void putReply(
            byte[] bytes)
        {
            encodePool.buffer(encodeSlot).putBytes(encodeSlotOffset, bytes);
            encodeSlotOffset += bytes.length;
        }

        private void putReply(
            DirectBufferEx buffer,
            int length)
        {
            final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
            if (summarized)
            {
                slot.putBytes(encodeSlotOffset, buffer, 0, length);
                encodeSlotOffset += length;
            }
            else
            {
                encodeSlotOffset = McpGrpcJson.putEscaped(slot, encodeSlotOffset, buffer, 0, length);
            }
        }

        private void doToolError(
            long traceId,
            String text)
        {
            if (acquireEncodeSlot())
            {
                putReply(RESULT_TEXT_PREFIX);
                encodeSlotOffset = McpGrpcJson.putEscaped(encodePool.buffer(encodeSlot), encodeSlotOffset, text);
                putReply(RESULT_ERROR_SUFFIX);
                replyPrefixed = true;
                replySuffixed = true;
                state = McpGrpcState.closingReply(state);
                doMcpBegin(traceId);
                flushReply(traceId);
            }
            else
            {
                doMcpAbort(traceId);
            }
        }

        private void doGrpcReplyWindow(
            long traceId)
        {
            if (grpc != null && McpGrpcState.replyOpened(grpcState) && !McpGrpcState.replyClosed(grpcState))
            {
                final long newReplyAck = Math.max(grpcReplyAck, grpcReplySeq - replySlotOffset);
                final int newReplyMax = Math.max(grpcReplyMax, decodePool.slotCapacity());

                if (newReplyAck > grpcReplyAck || newReplyMax > grpcReplyMax || grpcReplyMax == 0)
                {
                    grpcReplyAck = newReplyAck;
                    grpcReplyMax = newReplyMax;
                    doWindow(grpc, routedId, grpcRoutedId, grpcReplyId, grpcReplySeq, grpcReplyAck, grpcReplyMax,
                        traceId, authorization, 0L, 0);
                }
            }
        }

        private void doGrpcData(
            long traceId,
            int flags,
            int reserved,
            int deferred,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final Flyweight extension = deferred > 0
                ? grpcDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(grpcTypeId)
                    .deferred(deferred)
                    .build()
                : emptyRO;

            doData(grpc, routedId, grpcRoutedId, grpcInitialId, grpcInitialSeq, grpcInitialAck, grpcInitialMax,
                traceId, authorization, flags, 0L, reserved, buffer, offset, length, extension);
            grpcInitialSeq += reserved;
        }

        private void doGrpcEnd(
            long traceId)
        {
            if (!McpGrpcState.initialClosed(grpcState))
            {
                doEnd(grpc, routedId, grpcRoutedId, grpcInitialId, grpcInitialSeq, grpcInitialAck, grpcInitialMax,
                    traceId, authorization);
                grpcState = McpGrpcState.closedInitial(grpcState);
            }
        }

        private void doGrpcAbort(
            long traceId)
        {
            if (grpc != null && !McpGrpcState.initialClosed(grpcState))
            {
                doAbort(grpc, routedId, grpcRoutedId, grpcInitialId, grpcInitialSeq, grpcInitialAck, grpcInitialMax,
                    traceId, authorization);
                grpcState = McpGrpcState.closedInitial(grpcState);
            }
            cleanupRequestSlot();
        }

        private void doGrpcReset(
            long traceId)
        {
            if (grpc != null && !McpGrpcState.replyClosed(grpcState))
            {
                doReset(grpc, routedId, grpcRoutedId, grpcReplyId, grpcReplySeq, grpcReplyAck, grpcReplyMax,
                    traceId, authorization);
                grpcState = McpGrpcState.closedReply(grpcState);
            }
            cleanupReplySlot();
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

        private void cleanupRequestSlot()
        {
            if (requestSlot != NO_SLOT)
            {
                decodePool.release(requestSlot);
                requestSlot = NO_SLOT;
                requestSlotLimit = 0;
                requestSlotProgress = 0;
            }
        }

        private void cleanupReplySlot()
        {
            if (replySlot != NO_SLOT)
            {
                decodePool.release(replySlot);
                replySlot = NO_SLOT;
                replySlotOffset = 0;
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
            state = McpGrpcState.openingInitial(state);

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
            initialSeq = end.sequence();
            state = McpGrpcState.closedInitial(state);
            sessions.remove(sessionId, this);
            doMcpReplyEnd(end.traceId());
        }

        private void onMcpAbort(
            AbortFW abort)
        {
            state = McpGrpcState.closedInitial(state);
            sessions.remove(sessionId, this);
            doMcpReplyAbort(abort.traceId());
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
            state = McpGrpcState.closedReply(state);
            sessions.remove(sessionId, this);
            doMcpReset(reset.traceId());
        }

        private void doMcpWindow(
            long traceId)
        {
            initialMax = WINDOW_MAX;
            initialAck = initialSeq;
            state = McpGrpcState.openedInitial(state);
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0);
        }

        private void doMcpReplyBegin(
            long traceId)
        {
            if (!McpGrpcState.replyOpened(state))
            {
                final McpBeginExFW lifecycleEx = mcpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .lifecycle(l -> l
                        .sessionId(sessionId)
                        .capabilities(capabilities))
                    .build();
                doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, lifecycleEx);
                state = McpGrpcState.openedReply(state);
            }
        }

        private void doMcpReplyEnd(
            long traceId)
        {
            if (!McpGrpcState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = McpGrpcState.closedReply(state);
            }
        }

        private void doMcpReplyAbort(
            long traceId)
        {
            if (!McpGrpcState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = McpGrpcState.closedReply(state);
            }
        }

        private void doMcpReset(
            long traceId)
        {
            if (!McpGrpcState.initialClosed(state))
            {
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
                state = McpGrpcState.closedInitial(state);
            }
        }
    }

    private ProtobufPipeline newRequestPipeline(
        ProtobufSchema schema,
        McpGrpcTool tool)
    {
        return Protobuf.stream(ProtobufJson.parser(JsonEx.createParser(), schema, tool.inputType,
                Map.of(ProtobufJson.REJECT_UNKNOWN_FIELDS, Boolean.TRUE)))
            .into(Protobuf.generator(), schema, tool.inputType);
    }

    private ProtobufPipeline newReplyPipeline(
        ProtobufSchema schema,
        McpGrpcTool tool)
    {
        return Protobuf.stream(Protobuf.parser(schema, tool.outputType))
            .into(ProtobufJson.generator(JsonEx.createGenerator(), schema, tool.outputType), schema, tool.outputType);
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
        int length,
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
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
