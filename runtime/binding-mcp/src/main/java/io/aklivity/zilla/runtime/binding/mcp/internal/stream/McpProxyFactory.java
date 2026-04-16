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

import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpProxyFactory implements McpStreamFactory
{
    private static final String MCP_TYPE_NAME = "mcp";

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(), 0, 0);

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer codecBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int mcpTypeId;
    private final BufferPool encodePool;
    private final BufferPool decodePool;
    private final int encodeMax;
    private final int decodeMax;
    private final Long2ObjectHashMap<McpBindingConfig> bindings;

    public McpProxyFactory(
        McpConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.codecBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.encodePool = context.bufferPool();
        this.decodePool = context.bufferPool().duplicate();
        this.encodeMax = encodePool.slotCapacity();
        this.decodeMax = decodePool.slotCapacity();
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
        return mcpTypeId;
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
            final OctetsFW ext = begin.extension();
            final McpBeginExFW mcpBeginEx = mcpBeginExRO.tryWrap(ext.buffer(), ext.offset(), ext.limit());

            if (mcpBeginEx != null)
            {
                newStream = new McpProxy(
                    sender, originId, routedId, initialId, route.id)::onAppMessage;
            }
        }

        return newStream;
    }

    private final class McpProxy
    {
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
        private int appReplyMax;
        private int appState;

        private long netInitialId;
        private long netReplyId;
        private MessageConsumer netSender;
        private long netInitialSeq;
        private long netInitialAck;
        private int netInitialMax;
        private long netReplySeq;
        private long netReplyAck;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private boolean appEnded;
        private boolean netWindowGranted;
        private boolean appWindowGranted;
        private boolean netEnded;

        private long traceId;
        private long authorization;

        private McpProxy(
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

            final OctetsFW ext = begin.extension();
            final McpBeginExFW mcpBeginEx = mcpBeginExRO.tryWrap(ext.buffer(), ext.offset(), ext.limit());

            doNetBegin(traceId, authorization, mcpBeginEx);
            doAppWindow(traceId, authorization);
        }

        private void onAppData(
            DataFW data)
        {
            final long t = data.traceId();
            final long a = data.authorization();
            final OctetsFW payload = data.payload();

            appInitialSeq = data.sequence() + data.reserved();
            appInitialAck = data.acknowledge();

            if (payload != null && payload.sizeof() > 0)
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
            final long t = end.traceId();
            final long a = end.authorization();
            appEnded = true;

            if (netWindowGranted)
            {
                flushToNet(t, a);
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
            final long t = window.traceId();
            final long a = window.authorization();
            appReplyAck = window.acknowledge();
            appReplyMax = window.maximum();
            appWindowGranted = true;
            appState = McpState.openedReply(appState);

            if (netEnded)
            {
                flushToApp(t, a);
            }
        }

        private void onAppReset(
            ResetFW reset)
        {
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
            final long t = begin.traceId();
            final long a = begin.authorization();
            netReplySeq = begin.sequence();
            netReplyAck = begin.acknowledge();

            doNetWindow(t, a);

            final OctetsFW ext = begin.extension();
            final McpBeginExFW mcpBeginEx = ext.sizeof() > 0
                ? mcpBeginExRO.tryWrap(ext.buffer(), ext.offset(), ext.limit())
                : null;
            doAppBeginReply(t, a, mcpBeginEx);
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
            netEnded = true;

            if (appWindowGranted)
            {
                flushToApp(t, a);
            }
        }

        private void onNetAbort(
            AbortFW abort)
        {
            cleanupDecodeSlot();
            cleanupEncodeSlot();
            doAppAbort(abort.traceId(), abort.authorization());
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long t = window.traceId();
            final long a = window.authorization();
            netInitialAck = window.acknowledge();
            netInitialMax = window.maximum();
            netWindowGranted = true;

            if (appEnded)
            {
                flushToNet(t, a);
            }
        }

        private void onNetReset(
            ResetFW reset)
        {
            cleanupDecodeSlot();
            cleanupEncodeSlot();
            doAppReset(reset.traceId(), reset.authorization());
        }

        private void flushToNet(
            long t,
            long a)
        {
            if (encodeSlot != NO_SLOT)
            {
                final DirectBuffer slot = encodePool.buffer(encodeSlot);
                doNetData(t, a, slot, 0, encodeSlotOffset);
                cleanupEncodeSlot();
            }

            doNetEnd(t, a);
        }

        private void flushToApp(
            long t,
            long a)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer slot = decodePool.buffer(decodeSlot);
                doAppData(t, a, slot, 0, decodeSlotOffset);
                cleanupDecodeSlot();
            }

            doAppEnd(t, a);
        }

        private void doNetBegin(
            long t,
            long a,
            McpBeginExFW mcpBeginEx)
        {
            netInitialId = supplyInitialId.applyAsLong(resolvedId);
            netReplyId = supplyReplyId.applyAsLong(netInitialId);

            final Flyweight ext = mcpBeginEx != null ? copyMcpBeginEx(mcpBeginEx) : emptyRO;
            netSender = newStream(this::onNetMessage, routedId, resolvedId, netInitialId,
                netInitialSeq, netInitialAck, 0, t, a, 0L, ext);
        }

        private McpBeginExFW copyMcpBeginEx(
            McpBeginExFW src)
        {
            final McpBeginExFW.Builder b = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId);
            final int kind = src.kind();

            switch (kind)
            {
            case McpBeginExFW.KIND_LIFECYCLE:
            {
                final String sessionId = src.lifecycle().sessionId().asString();
                b.lifecycle(l -> l.sessionId(sessionId));
                break;
            }
            case McpBeginExFW.KIND_TOOLS_LIST:
            {
                final String sessionId = src.toolsList().sessionId().asString();
                b.toolsList(l -> l.sessionId(sessionId));
                break;
            }
            case McpBeginExFW.KIND_TOOLS_CALL:
            {
                final String sessionId = src.toolsCall().sessionId().asString();
                final String name = src.toolsCall().name().asString();
                b.toolsCall(l -> l.sessionId(sessionId).name(name));
                break;
            }
            case McpBeginExFW.KIND_PROMPTS_LIST:
            {
                final String sessionId = src.promptsList().sessionId().asString();
                b.promptsList(l -> l.sessionId(sessionId));
                break;
            }
            case McpBeginExFW.KIND_PROMPTS_GET:
            {
                final String sessionId = src.promptsGet().sessionId().asString();
                final String name = src.promptsGet().name().asString();
                b.promptsGet(l -> l.sessionId(sessionId).name(name));
                break;
            }
            case McpBeginExFW.KIND_RESOURCES_LIST:
            {
                final String sessionId = src.resourcesList().sessionId().asString();
                b.resourcesList(l -> l.sessionId(sessionId));
                break;
            }
            case McpBeginExFW.KIND_RESOURCES_READ:
            {
                final String sessionId = src.resourcesRead().sessionId().asString();
                final String uri = src.resourcesRead().uri().asString();
                b.resourcesRead(l -> l.sessionId(sessionId).uri(uri));
                break;
            }
            default:
                break;
            }

            return b.build();
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
            long a,
            McpBeginExFW mcpBeginEx)
        {
            final Flyweight ext = mcpBeginEx != null ? copyMcpBeginEx(mcpBeginEx) : emptyRO;
            doBegin(appSender, originId, routedId, replyId,
                appReplySeq, appReplyAck, 0, t, a, 0L, ext);
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
                appReplySeq, appReplyAck, appReplyMax,
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
                appInitialSeq, appInitialAck, encodeMax, t, a, 0L, 0);
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
