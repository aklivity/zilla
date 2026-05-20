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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpCacheContext;
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

public final class McpProxyCacheHydrater
{
    static final int SIGNAL_INITIATE_LIFECYCLE = 1;
    static final int SIGNAL_REFRESH_TOOLS = 2;
    static final int SIGNAL_REFRESH_RESOURCES = 3;
    static final int SIGNAL_REFRESH_PROMPTS = 4;

    private final McpCacheContext cacheContext;
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

    private final BeginFW beginRO = new BeginFW();
    private final EndFW endRO = new EndFW();
    private final DataFW dataRO = new DataFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    private final long originId;
    private final long routedId;
    private final List<McpListHydrater> hydraters;
    private final List<McpSignalHandle> awaiters;
    private final int expected;

    private int populated;
    private boolean complete;

    private McpHydrateLifecycleStream stream;

    public McpProxyCacheHydrater(
        McpCacheContext cacheContext,
        McpConfiguration config,
        EngineContext context)
    {
        this.cacheContext = cacheContext;
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

        this.originId = cacheContext.bindingId;
        this.routedId = cacheContext.bindingId;

        final List<McpListHydrater> hydraters = new ArrayList<>();
        if (hydrateFilter.test(KIND_TOOLS_LIST))
        {
            hydraters.add(new McpToolsListHydrater());
        }
        if (hydrateFilter.test(KIND_RESOURCES_LIST))
        {
            hydraters.add(new McpResourcesListHydrater());
        }
        if (hydrateFilter.test(KIND_PROMPTS_LIST))
        {
            hydraters.add(new McpPromptsListHydrater());
        }
        this.hydraters = hydraters;
        this.awaiters = new ArrayList<>();
        this.expected = hydraters.size();
    }

    public void start()
    {
        signaler.signalAt(Instant.now(), SIGNAL_INITIATE_LIFECYCLE, this::onInitiateLifecycle);
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
        cacheContext.releaseLifecycle(k -> {});
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

    private void markComplete()
    {
        complete = true;
        for (McpSignalHandle h : awaiters)
        {
            h.signalVia(signaler);
        }
        awaiters.clear();
        cacheContext.releaseLifecycle(k -> {});
    }

    private void onInitiateLifecycle(
        int signalId)
    {
        final long traceId = supplyTraceId.getAsLong();
        cacheContext.sessionId = supplySessionId.get();
        cacheContext.authorization = cacheContext.guard != null
            ? cacheContext.guard.reauthorize(traceId, originId, 0L, cacheContext.credentials)
            : 0L;
        cacheContext.acquireLifecycle(acquired -> onAcquireLifecycleComplete(traceId, acquired));
    }

    private void onAcquireLifecycleComplete(
        long traceId,
        boolean acquired)
    {
        if (acquired)
        {
            stream = new McpHydrateLifecycleStream(traceId);
            stream.doLifecycleBegin(traceId);
        }
        else
        {
            signaler.signalAt(Instant.now().plus(cacheContext.leaseRetry), SIGNAL_INITIATE_LIFECYCLE,
                this::onInitiateLifecycle);
        }
    }

    private final class McpHydrateLifecycleStream
    {
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
            long traceId)
        {
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.replyMax = bufferPool.slotCapacity();
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
                for (McpListHydrater hydrater : hydraters)
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
                cacheContext.releaseLifecycle(k -> {});
            }
        }

        private void onLifecycleAbort(
            AbortFW abort)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doLifecycleAbort(abort.traceId());
                cacheContext.releaseLifecycle(k -> {});
            }
        }

        private void onLifecycleReset(
            ResetFW reset)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                cacheContext.releaseLifecycle(k -> {});
            }
        }

        void doLifecycleBegin(
            long traceId)
        {
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .lifecycle(l -> l.sessionId(cacheContext.sessionId))
                .build();

            receiver = newStream(this::onLifecycleMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, cacheContext.authorization, 0L, beginEx);
            state = McpState.openingInitial(state);
        }

        private void doLifecycleWindow(
            long traceId)
        {
            doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, cacheContext.authorization, 0L, 0);
        }

        void doLifecycleEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, cacheContext.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doLifecycleAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, cacheContext.authorization);
                state = McpState.closedInitial(state);
            }
        }
    }

    private abstract class McpListHydrater
    {
        final McpCacheContext.McpListCache cache;

        private McpListHydrateStream stream;

        McpListHydrater(
            McpCacheContext.McpListCache cache)
        {
            this.cache = cache;
        }

        protected abstract int signalId();

        protected abstract void injectInitialBeginEx(
            McpBeginExFW.Builder builder,
            String sessionId);

        final void initiate(
            long traceId)
        {
            cache.get(this::onInitialGetComplete);
        }

        private void onInitialGetComplete(
            String key,
            String value)
        {
            if (value != null)
            {
                markReady();
                scheduleRefresh();
            }
            else
            {
                cache.acquire(this::onInitialAcquireComplete);
            }
        }

        private void onInitialAcquireComplete(
            boolean acquired)
        {
            if (acquired)
            {
                startListStream();
            }
            else
            {
                markReady();
                scheduleRefresh();
            }
        }

        private void onRefreshSignal(
            int signalId)
        {
            cache.acquire(this::onRefreshAcquireComplete);
        }

        private void onRefreshAcquireComplete(
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
            if (cacheContext.cacheTtl != null)
            {
                signaler.signalAt(Instant.now().plus(cacheContext.cacheTtl), signalId(), this::onRefreshSignal);
            }
        }

        private void startListStream()
        {
            final long traceId = supplyTraceId.getAsLong();
            stream = new McpListHydrateStream();
            stream.doListHydrateBegin(traceId);
        }

        private final class McpListHydrateStream
        {
            private final long initialId;
            private final long replyId;
            private final ExpandableArrayBuffer bodyBuffer;

            private int state;
            private long initialSeq;
            private long initialAck;
            private int initialMax;
            private long replySeq;
            private long replyAck;
            private int replyMax;
            private MessageConsumer receiver;
            private int bodyLen;
            private boolean settled;

            McpListHydrateStream()
            {
                this.bodyBuffer = new ExpandableArrayBuffer();
                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.replyMax = bufferPool.slotCapacity();
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
                    onListHydrateAbort(abortRO.wrap(buffer, index, index + length));
                    break;
                case ResetFW.TYPE_ID:
                    onListHydrateReset(resetRO.wrap(buffer, index, index + length));
                    break;
                case WindowFW.TYPE_ID:
                    onListHydrateWindow(windowRO.wrap(buffer, index, index + length));
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
                    bodyBuffer.putBytes(bodyLen, payload.buffer(), payload.offset(), payloadLen);
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
                    final String value = bodyBuffer.getStringWithoutLengthUtf8(0, bodyLen);
                    cache.put(value, k -> terminal(traceId));
                }
                else
                {
                    terminal(traceId);
                }
            }

            private void onListHydrateAbort(
                AbortFW abort)
            {
                final long traceId = abort.traceId();
                state = McpState.closedReply(state);
                doListHydrateAbort(traceId);
                terminal(traceId);
            }

            private void onListHydrateReset(
                ResetFW reset)
            {
                final long traceId = reset.traceId();
                state = McpState.closedInitial(state);
                doListHydrateReset(traceId);
                terminal(traceId);
            }

            private void onListHydrateWindow(
                WindowFW window)
            {
                if (McpState.initialClosing(state) && !McpState.initialClosed(state))
                {
                    doListHydrateEnd(window.traceId());
                }
            }

            void doListHydrateBegin(
                long traceId)
            {
                final McpBeginExFW beginEx = mcpBeginExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(mcpTypeId)
                    .inject(builder -> injectInitialBeginEx(builder, cacheContext.sessionId))
                    .build();

                receiver = newStream(this::onListHydrateMessage, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, cacheContext.authorization, 0L, beginEx);
                state = McpState.openingInitial(state);
                state = McpState.closingInitial(state);
            }

            void doListHydrateEnd(
                long traceId)
            {
                if (!McpState.initialClosed(state))
                {
                    doEnd(receiver, originId, routedId, initialId,
                        initialSeq, initialAck, initialMax, traceId, cacheContext.authorization);
                    state = McpState.closedInitial(state);
                }
            }

            private void doListHydrateAbort(
                long traceId)
            {
                if (!McpState.initialClosed(state))
                {
                    doAbort(receiver, originId, routedId, initialId,
                        initialSeq, initialAck, initialMax, traceId, cacheContext.authorization);
                    state = McpState.closedInitial(state);
                }
            }

            private void doListHydrateReset(
                long traceId)
            {
                if (!McpState.replyClosed(state))
                {
                    doReset(receiver, originId, routedId, replyId,
                        replySeq, replyAck, replyMax, traceId, cacheContext.authorization);
                    state = McpState.closedReply(state);
                }
            }

            private void doListHydrateWindow(
                long traceId)
            {
                doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, cacheContext.authorization, 0L, 0);
            }

            private void terminal(
                long traceId)
            {
                if (!settled)
                {
                    settled = true;
                    cache.release(k -> {});
                    markReady();
                    scheduleRefresh();
                }
            }
        }
    }

    private final class McpToolsListHydrater extends McpListHydrater
    {
        McpToolsListHydrater()
        {
            super(cacheContext.tools());
        }

        @Override
        protected int signalId()
        {
            return SIGNAL_REFRESH_TOOLS;
        }

        @Override
        protected void injectInitialBeginEx(
            McpBeginExFW.Builder builder,
            String sessionId)
        {
            builder.toolsList(t -> t.sessionId(sessionId));
        }
    }

    private final class McpResourcesListHydrater extends McpListHydrater
    {
        McpResourcesListHydrater()
        {
            super(cacheContext.resources());
        }

        @Override
        protected int signalId()
        {
            return SIGNAL_REFRESH_RESOURCES;
        }

        @Override
        protected void injectInitialBeginEx(
            McpBeginExFW.Builder builder,
            String sessionId)
        {
            builder.resourcesList(r -> r.sessionId(sessionId));
        }
    }

    private final class McpPromptsListHydrater extends McpListHydrater
    {
        McpPromptsListHydrater()
        {
            super(cacheContext.prompts());
        }

        @Override
        protected int signalId()
        {
            return SIGNAL_REFRESH_PROMPTS;
        }

        @Override
        protected void injectInitialBeginEx(
            McpBeginExFW.Builder builder,
            String sessionId)
        {
            builder.promptsList(p -> p.sessionId(sessionId));
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
}
