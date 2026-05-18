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

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

abstract class McpProxyCacheListHydrater
{
    static final int SIGNAL_REFRESH_TOOLS = 2;
    static final int SIGNAL_REFRESH_RESOURCES = 3;
    static final int SIGNAL_REFRESH_PROMPTS = 4;

    final McpProxyCacheHydrater parent;
    private final int kind;
    private final int signalId;

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
        McpProxyCacheHydrater parent,
        int kind,
        int signalId)
    {
        this.parent = parent;
        this.kind = kind;
        this.signalId = signalId;
        this.body = new byte[1024];
    }

    final void initiate(
        long traceId)
    {
        parent.binding.cache.get(kind, this::onInitialGetComplete);
    }

    protected abstract void injectInitialBeginEx(
        McpBeginExFW.Builder b,
        String sessionId);

    private Duration ttl()
    {
        return parent.binding.options.cache.ttl;
    }

    private void onInitialGetComplete(
        String key,
        String value)
    {
        if (value != null)
        {
            parent.markReady(kind);
            scheduleRefresh();
        }
        else
        {
            parent.binding.cache.acquireLease(kind, McpProxyCacheHydrater.LEASE_TTL_MS, this::onInitialAcquireLeaseComplete);
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
            parent.markReady(kind);
            scheduleRefresh();
        }
    }

    private void onRefreshSignal(
        int signalId)
    {
        parent.binding.cache.acquireLease(kind, McpProxyCacheHydrater.LEASE_TTL_MS, this::onRefreshAcquireLeaseComplete);
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
        final Duration interval = ttl();
        if (interval != null)
        {
            parent.signaler.signalAt(currentTimeMillis() + interval.toMillis(), signalId, this::onRefreshSignal);
        }
    }

    private void startListStream()
    {
        final long traceId = parent.supplyTraceId.getAsLong();
        initialSeq = 0L;
        initialAck = 0L;
        initialMax = 0;
        replySeq = 0L;
        replyAck = 0L;
        replyMax = parent.bufferPool.slotCapacity();
        state = 0;
        bodyLen = 0;
        settled = false;
        receiver = null;

        initialId = parent.supplyInitialId.applyAsLong(parent.routedId);
        replyId = parent.supplyReplyId.applyAsLong(initialId);

        final McpBeginExFW beginEx = parent.mcpBeginExRW
            .wrap(parent.codecBuffer, 0, parent.codecBuffer.capacity())
            .typeId(parent.mcpTypeId)
            .inject(b -> injectInitialBeginEx(b, parent.sessionId))
            .build();

        receiver = parent.newStream(this::onMessage, parent.originId, parent.routedId, initialId,
            initialSeq, initialAck, initialMax, traceId, parent.authorization, 0L, beginEx);
        state = McpState.openingInitial(state);

        parent.doEnd(receiver, parent.originId, parent.routedId, initialId,
            initialSeq, initialAck, initialMax, traceId, parent.authorization);
        state = McpState.closedInitial(state);
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
            onBegin(parent.beginRO.wrap(buffer, index, index + length));
            break;
        case DataFW.TYPE_ID:
            onData(parent.dataRO.wrap(buffer, index, index + length));
            break;
        case EndFW.TYPE_ID:
            onEnd(parent.endRO.wrap(buffer, index, index + length));
            break;
        case AbortFW.TYPE_ID:
        case ResetFW.TYPE_ID:
            state = McpState.closedReply(state);
            terminal(parent.supplyTraceId.getAsLong());
            break;
        default:
            break;
        }
    }

    private void onBegin(
        BeginFW begin)
    {
        state = McpState.openingReply(state);
        doReplyWindow(begin.traceId());
    }

    private void onData(
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
        doReplyWindow(data.traceId());
    }

    private void onEnd(
        EndFW end)
    {
        final long traceId = end.traceId();
        state = McpState.closedReply(state);
        if (bodyLen > 0)
        {
            final String value = new String(body, 0, bodyLen, StandardCharsets.UTF_8);
            parent.binding.cache.put(kind, value, k -> terminal(traceId));
        }
        else
        {
            terminal(traceId);
        }
    }

    private void doReplyWindow(
        long traceId)
    {
        parent.doWindow(receiver, parent.originId, parent.routedId, replyId, replySeq, replyAck, replyMax,
            traceId, parent.authorization, 0L, 0);
    }

    private void terminal(
        long traceId)
    {
        if (!settled)
        {
            settled = true;
            parent.binding.cache.releaseLease(kind, k -> {});
            parent.markReady(kind);
            scheduleRefresh();
        }
    }
}
