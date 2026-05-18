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
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

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
    static final int SIGNAL_INITIATE_LIFECYCLE = 1;

    private final McpBindingConfig binding;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer codecBuffer;
    private final BindingHandler streamFactory;
    private final BufferPool bufferPool;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final Signaler signaler;
    private final int mcpTypeId;
    private final Supplier<String> supplySessionId;
    private final IntPredicate hydrateFilter;
    private final Duration leaseTtl;
    private final Duration leaseRetry;

    private final BeginFW beginRO = new BeginFW();
    private final EndFW endRO = new EndFW();
    private final DataFW dataRO = new DataFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    private final boolean enabled;
    private final long originId;
    private final long routedId;

    private final List<McpProxyCacheListHydrater> hydraters;
    private final List<McpSignalHandle> awaiters;
    private final int expected;

    private int populated;
    private boolean complete;

    private McpHydrateLifecycleStream stream;

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
        this.supplySessionId = config.sessionIdSupplier();
        this.hydrateFilter = config.hydrateFilter();
        this.leaseTtl = config.leaseTtl();
        this.leaseRetry = config.leaseRetry();

        this.originId = binding.id;

        final McpRouteConfig route = binding.resolve(0L);
        this.enabled = route != null;
        this.routedId = route != null ? route.id : 0L;

        final Duration cacheTtl = Optional.ofNullable(binding.options)
            .map(o -> o.cache)
            .map(c -> c.ttl)
            .orElse(null);

        final List<McpProxyCacheListHydrater> hydraters = new ArrayList<>();
        if (enabled)
        {
            if (hydrateFilter.test(KIND_TOOLS_LIST))
            {
                hydraters.add(new McpProxyCacheToolsListHydrater(context, originId, routedId,
                    this::currentAuthorization, this::currentSessionId, this::markReady,
                    leaseTtl, cacheTtl, binding.toolsCache));
            }
            if (hydrateFilter.test(KIND_RESOURCES_LIST))
            {
                hydraters.add(new McpProxyCacheResourcesListHydrater(context, originId, routedId,
                    this::currentAuthorization, this::currentSessionId, this::markReady,
                    leaseTtl, cacheTtl, binding.resourcesCache));
            }
            if (hydrateFilter.test(KIND_PROMPTS_LIST))
            {
                hydraters.add(new McpProxyCachePromptsListHydrater(context, originId, routedId,
                    this::currentAuthorization, this::currentSessionId, this::markReady,
                    leaseTtl, cacheTtl, binding.promptsCache));
            }
        }
        this.hydraters = hydraters;
        this.awaiters = new ArrayList<>();
        this.expected = hydraters.size();
    }

    public void start()
    {
        if (enabled)
        {
            signaler.signalAt(currentTimeMillis(), SIGNAL_INITIATE_LIFECYCLE, this::onInitiateLifecycle);
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
        if (stream != null)
        {
            stream.doLifecycleEnd(traceId);
        }
        binding.lifecycleCache.releaseLifecycle(k -> {});
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

    void markReady()
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

    private long currentAuthorization()
    {
        return stream != null ? stream.authorization : 0L;
    }

    private String currentSessionId()
    {
        return stream != null ? stream.sessionId : null;
    }

    private void markComplete()
    {
        complete = true;
        for (McpSignalHandle h : awaiters)
        {
            h.signalVia(signaler);
        }
        awaiters.clear();
        binding.lifecycleCache.releaseLifecycle(k -> {});
    }

    private void onInitiateLifecycle(
        int signalId)
    {
        final long traceId = supplyTraceId.getAsLong();
        final long authorization = binding.cacheGuard != null
            ? binding.cacheGuard.reauthorize(traceId, originId, 0L, binding.cacheCredentials)
            : 0L;
        final McpRouteConfig route = binding.resolve(authorization);
        if (route != null)
        {
            binding.lifecycleCache.acquireLifecycle(leaseTtl.toMillis(),
                acquired -> onAcquireLifecycleComplete(traceId, authorization, acquired));
        }
    }

    private void onAcquireLifecycleComplete(
        long traceId,
        long authorization,
        boolean acquired)
    {
        if (acquired)
        {
            stream = new McpHydrateLifecycleStream(traceId, authorization);
        }
        else
        {
            signaler.signalAt(currentTimeMillis() + leaseRetry.toMillis(), SIGNAL_INITIATE_LIFECYCLE,
                this::onInitiateLifecycle);
        }
    }

    private final class McpHydrateLifecycleStream
    {
        final String sessionId;
        final long authorization;
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

        McpHydrateLifecycleStream(
            long traceId,
            long authorization)
        {
            this.sessionId = supplySessionId.get();
            this.authorization = authorization;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.replyMax = bufferPool.slotCapacity();
            doLifecycleBegin(traceId);
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
                onLifecycleEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onLifecycleAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onLifecycleReset(resetRO.wrap(buffer, index, index + length));
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
            doLifecycleWindow(traceId);

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

        private void onLifecycleEnd(
            EndFW end)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doLifecycleEnd(end.traceId());
                binding.lifecycleCache.releaseLifecycle(k -> {});
            }
        }

        private void onLifecycleAbort(
            AbortFW abort)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doLifecycleAbort(abort.traceId());
                binding.lifecycleCache.releaseLifecycle(k -> {});
            }
        }

        private void onLifecycleReset(
            ResetFW reset)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                binding.lifecycleCache.releaseLifecycle(k -> {});
            }
        }

        private void doLifecycleBegin(
            long traceId)
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

        private void doLifecycleWindow(
            long traceId)
        {
            doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, 0L, 0);
        }

        void doLifecycleEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doLifecycleAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
                state = McpState.closedInitial(state);
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
}
