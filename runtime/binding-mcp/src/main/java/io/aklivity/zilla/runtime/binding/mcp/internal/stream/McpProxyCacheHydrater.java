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

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;
import static java.lang.System.currentTimeMillis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.Flyweight;
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

public final class McpProxyCacheHydrater
{
    static final long LEASE_TTL_MS = Duration.ofSeconds(30).toMillis();
    static final long LEASE_RETRY_MS = 100L;
    static final int SIGNAL_INITIATE_LIFECYCLE = 1;

    final McpBindingConfig binding;

    final MutableDirectBuffer writeBuffer;
    final MutableDirectBuffer codecBuffer;
    final BindingHandler streamFactory;
    final BufferPool bufferPool;
    final LongUnaryOperator supplyInitialId;
    final LongUnaryOperator supplyReplyId;
    final LongSupplier supplyTraceId;
    final Signaler signaler;
    final int mcpTypeId;
    final IntPredicate hydrateKindFilter;

    final BeginFW beginRO = new BeginFW();
    final EndFW endRO = new EndFW();
    final DataFW dataRO = new DataFW();
    final AbortFW abortRO = new AbortFW();
    final ResetFW resetRO = new ResetFW();
    final BeginFW.Builder beginRW = new BeginFW.Builder();
    final EndFW.Builder endRW = new EndFW.Builder();
    final WindowFW.Builder windowRW = new WindowFW.Builder();
    final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    final String sessionId;
    final long authorization;
    final long originId;
    final long routedId;
    final long initialId;
    final long replyId;

    private final boolean enabled;
    private final List<McpProxyCacheListHydrater> hydraters;
    private final List<McpSignalHandle> awaiters = new ArrayList<>();
    private final int expected;

    private int populated;
    private boolean complete;

    private int state;
    private long initialSeq;
    private long initialAck;
    private int initialMax;
    private long replySeq;
    private long replyAck;
    private int replyMax;
    private MessageConsumer receiver;

    public McpProxyCacheHydrater(
        McpBindingConfig binding,
        McpConfiguration config,
        EngineContext context)
    {
        this.binding = binding;
        this.writeBuffer = context.writeBuffer();
        this.codecBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.bufferPool = context.bufferPool();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.signaler = context.signaler();
        this.mcpTypeId = context.supplyTypeId("mcp");
        this.hydrateKindFilter = config.hydrateKindFilter();

        this.sessionId = config.sessionIdSupplier().get();

        final McpRouteConfig route = binding.resolve(0L);
        this.enabled = route != null;
        this.originId = binding.id;
        this.routedId = route != null ? route.id : 0L;

        if (route != null)
        {
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.replyMax = bufferPool.slotCapacity();
            this.authorization = binding.cacheGuard != null
                ? binding.cacheGuard.reauthorize(supplyTraceId.getAsLong(), binding.id, 0L, binding.cacheCredentials)
                : 0L;
        }
        else
        {
            this.initialId = 0L;
            this.replyId = 0L;
            this.authorization = 0L;
        }

        this.hydraters = new ArrayList<>();
        if (enabled)
        {
            buildListHydraters();
        }
        this.expected = hydraters.size();
    }

    public void start()
    {
        if (enabled)
        {
            signaler.signalAt(currentTimeMillis(), SIGNAL_INITIATE_LIFECYCLE, this::onInitiateLifecycleSignal);
        }
    }

    public void cleanup()
    {
        cleanup(supplyTraceId.getAsLong());
    }

    public void cleanup(
        long traceId)
    {
        awaiters.clear();
        if (receiver != null)
        {
            doInitialEnd(traceId);
        }
        binding.cache.releaseLifecycleLease(k -> {});
    }

    public void register(
        McpSignalHandle handle)
    {
        if (complete)
        {
            handle.signalVia(signaler);
        }
        else
        {
            awaiters.add(handle);
        }
    }

    void markReady(
        int kind)
    {
        if (!complete)
        {
            populated++;
            if (populated >= expected)
            {
                markComplete();
            }
        }
    }

    private void markComplete()
    {
        complete = true;
        for (McpSignalHandle h : awaiters)
        {
            h.signalVia(signaler);
        }
        awaiters.clear();
        binding.cache.releaseLifecycleLease(k -> {});
    }

    private void buildListHydraters()
    {
        for (int kind : new int[] { KIND_TOOLS_LIST, KIND_RESOURCES_LIST, KIND_PROMPTS_LIST })
        {
            if (hydrateKindFilter.test(kind))
            {
                final McpProxyCacheListHydrater hydrater = switch (kind)
                {
                case KIND_TOOLS_LIST -> new McpProxyCacheToolsListHydrater(this);
                case KIND_RESOURCES_LIST -> new McpProxyCacheResourcesListHydrater(this);
                case KIND_PROMPTS_LIST -> new McpProxyCachePromptsListHydrater(this);
                default -> throw new IllegalStateException("unexpected hydrate list kind: " + kind);
                };
                hydraters.add(hydrater);
            }
        }
    }

    private void onInitiateLifecycleSignal(
        int signalId)
    {
        binding.cache.acquireLifecycleLease(LEASE_TTL_MS, this::onAcquireLifecycleLeaseComplete);
    }

    private void onAcquireLifecycleLeaseComplete(
        boolean acquired)
    {
        final long traceId = supplyTraceId.getAsLong();
        if (acquired)
        {
            doLifecycleBegin(traceId);
        }
        else
        {
            signaler.signalAt(currentTimeMillis() + LEASE_RETRY_MS, SIGNAL_INITIATE_LIFECYCLE,
                this::onInitiateLifecycleSignal);
        }
    }

    private void doLifecycleBegin(
        long traceId)
    {
        if (!McpState.initialOpening(state))
        {
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .lifecycle(l -> l.sessionId(sessionId))
                .build();

            receiver = newStream(this::onLifecycleMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, authorization, 0L, beginEx);
            state = McpState.openingInitial(state);
        }
    }

    private void onLifecycleMessage(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        switch (msgTypeId)
        {
        case BeginFW.TYPE_ID:
            onLifecycleBegin(beginRO.wrap(buffer, index, index + length));
            break;
        case EndFW.TYPE_ID:
        case AbortFW.TYPE_ID:
        case ResetFW.TYPE_ID:
            state = McpState.closedInitial(state);
            state = McpState.closedReply(state);
            binding.cache.releaseLifecycleLease(k -> {});
            break;
        default:
            break;
        }
    }

    private void onLifecycleBegin(
        BeginFW begin)
    {
        final long traceId = begin.traceId();
        state = McpState.openingReply(state);
        doReplyWindow(traceId);

        if (hydraters.isEmpty())
        {
            markComplete();
        }
        else
        {
            for (McpProxyCacheListHydrater hydrater : hydraters)
            {
                hydrater.initiate(traceId);
            }
        }
    }

    private void doReplyWindow(
        long traceId)
    {
        doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
            traceId, authorization, 0L, 0);
    }

    private void doInitialEnd(
        long traceId)
    {
        if (!McpState.initialClosed(state))
        {
            doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization);
            state = McpState.closedInitial(state);
        }
    }

    MessageConsumer newStream(
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

    void doEnd(
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

    void doWindow(
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
