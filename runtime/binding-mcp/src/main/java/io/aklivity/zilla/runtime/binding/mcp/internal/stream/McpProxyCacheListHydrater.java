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

import static java.lang.System.currentTimeMillis;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpListCache;
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
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

abstract class McpProxyCacheListHydrater
{
    static final int SIGNAL_REFRESH_TOOLS = 2;
    static final int SIGNAL_REFRESH_RESOURCES = 3;
    static final int SIGNAL_REFRESH_PROMPTS = 4;

    final McpListCache cache;

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer codecBuffer;
    private final BindingHandler streamFactory;
    private final BufferPool bufferPool;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final Signaler signaler;
    private final int mcpTypeId;
    private final long originId;
    private final long routedId;
    private final LongSupplier supplyAuthorization;
    private final Supplier<String> supplySessionId;
    private final Runnable onReady;
    private final long leaseTtlMs;
    private final Duration cacheTtl;
    private final int signalId;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    private long initialId;
    private long replyId;
    private long initialSeq;
    private long initialAck;
    private int initialMax;
    private long replySeq;
    private long replyAck;
    private int replyMax;
    private int state;
    private MessageConsumer receiver;
    private byte[] body;
    private int bodyLen;
    private boolean settled;

    McpProxyCacheListHydrater(
        EngineContext context,
        long originId,
        long routedId,
        LongSupplier supplyAuthorization,
        Supplier<String> supplySessionId,
        Runnable onReady,
        long leaseTtlMs,
        Duration cacheTtl,
        McpListCache cache,
        int signalId)
    {
        this.writeBuffer = context.writeBuffer();
        this.codecBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.bufferPool = context.bufferPool();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.signaler = context.signaler();
        this.mcpTypeId = context.supplyTypeId("mcp");
        this.originId = originId;
        this.routedId = routedId;
        this.supplyAuthorization = supplyAuthorization;
        this.supplySessionId = supplySessionId;
        this.onReady = onReady;
        this.leaseTtlMs = leaseTtlMs;
        this.cacheTtl = cacheTtl;
        this.cache = cache;
        this.signalId = signalId;
        this.body = new byte[1024];
    }

    final void initiate(
        long traceId)
    {
        cache.get(this::onInitialGetComplete);
    }

    protected abstract void injectInitialBeginEx(
        McpBeginExFW.Builder builder,
        String sessionId);

    private void onInitialGetComplete(
        String key,
        String value)
    {
        if (value != null)
        {
            onReady.run();
            scheduleRefresh();
        }
        else
        {
            cache.acquire(leaseTtlMs, this::onInitialAcquireLeaseComplete);
        }
    }

    private void onInitialAcquireLeaseComplete(
        boolean acquired)
    {
        if (acquired)
        {
            startListStream();
        }
        else
        {
            onReady.run();
            scheduleRefresh();
        }
    }

    private void onRefreshSignal(
        int signalId)
    {
        cache.acquire(leaseTtlMs, this::onRefreshAcquireLeaseComplete);
    }

    private void onRefreshAcquireLeaseComplete(
        boolean acquired)
    {
        if (acquired)
        {
            startListStream();
        }
        else
        {
            scheduleRefresh();
        }
    }

    private void scheduleRefresh()
    {
        if (cacheTtl != null)
        {
            signaler.signalAt(currentTimeMillis() + cacheTtl.toMillis(), signalId, this::onRefreshSignal);
        }
    }

    private void startListStream()
    {
        final long traceId = supplyTraceId.getAsLong();
        final long authorization = supplyAuthorization.getAsLong();
        final String sessionId = supplySessionId.get();

        initialSeq = 0L;
        initialAck = 0L;
        initialMax = 0;
        replySeq = 0L;
        replyAck = 0L;
        replyMax = bufferPool.slotCapacity();
        state = 0;
        bodyLen = 0;
        settled = false;
        receiver = null;

        initialId = supplyInitialId.applyAsLong(routedId);
        replyId = supplyReplyId.applyAsLong(initialId);

        final McpBeginExFW beginEx = mcpBeginExRW
            .wrap(codecBuffer, 0, codecBuffer.capacity())
            .typeId(mcpTypeId)
            .inject(builder -> injectInitialBeginEx(builder, sessionId))
            .build();

        receiver = newStream(this::onListHydrateMessage, originId, routedId, initialId,
            initialSeq, initialAck, initialMax, traceId, authorization, 0L, beginEx);
        state = McpState.openingInitial(state);

        doEnd(receiver, originId, routedId, initialId,
            initialSeq, initialAck, initialMax, traceId, authorization);
        state = McpState.closedInitial(state);
    }

    private void onListHydrateMessage(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        switch (msgTypeId)
        {
        case BeginFW.TYPE_ID:
            onListHydrateBegin(beginRO.wrap(buffer, index, index + length));
            break;
        case DataFW.TYPE_ID:
            onListHydrateData(dataRO.wrap(buffer, index, index + length));
            break;
        case EndFW.TYPE_ID:
            onListHydrateEnd(endRO.wrap(buffer, index, index + length));
            break;
        case AbortFW.TYPE_ID:
        case ResetFW.TYPE_ID:
            state = McpState.closedReply(state);
            terminal(supplyTraceId.getAsLong());
            break;
        default:
            break;
        }
    }

    private void onListHydrateBegin(
        BeginFW begin)
    {
        state = McpState.openingReply(state);
        doListHydrateWindow(begin.traceId());
    }

    private void onListHydrateData(
        DataFW data)
    {
        final OctetsFW payload = data.payload();
        if (payload != null)
        {
            final int payloadLen = payload.sizeof();
            if (bodyLen + payloadLen > body.length)
            {
                int newCap = body.length;
                while (newCap < bodyLen + payloadLen)
                {
                    newCap <<= 1;
                }
                final byte[] grown = new byte[newCap];
                System.arraycopy(body, 0, grown, 0, bodyLen);
                body = grown;
            }
            payload.buffer().getBytes(payload.offset(), body, bodyLen, payloadLen);
            bodyLen += payloadLen;
        }
        doListHydrateWindow(data.traceId());
    }

    private void onListHydrateEnd(
        EndFW end)
    {
        final long traceId = end.traceId();
        state = McpState.closedReply(state);
        if (bodyLen > 0)
        {
            final String value = new String(body, 0, bodyLen, StandardCharsets.UTF_8);
            cache.put(value, k -> terminal(traceId));
        }
        else
        {
            terminal(traceId);
        }
    }

    private void doListHydrateWindow(
        long traceId)
    {
        final long authorization = supplyAuthorization.getAsLong();
        doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
            traceId, authorization, 0L, 0);
    }

    private void terminal(
        long traceId)
    {
        if (!settled)
        {
            settled = true;
            cache.release(k -> {});
            onReady.run();
            scheduleRefresh();
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
        assert receiver != null;

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
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
