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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache;

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpState;
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

final class McpProxyCacheHydrater
{
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer codecBuffer;
    private final BindingHandler streamFactory;
    private final BufferPool bufferPool;
    private final Signaler signaler;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
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

    private final McpToolsListHydrater toolsHydrater;
    private final McpResourcesListHydrater resourcesHydrater;
    private final McpPromptsListHydrater promptsHydrater;

    McpProxyCacheHydrater(
        McpConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.codecBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.bufferPool = context.bufferPool();
        this.signaler = context.signaler();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.mcpTypeId = context.supplyTypeId("mcp");
        this.supplySessionId = config.sessionIdSupplier();
        this.hydrateFilter = config.hydrateFilter();

        this.toolsHydrater = new McpToolsListHydrater();
        this.resourcesHydrater = new McpResourcesListHydrater();
        this.promptsHydrater = new McpPromptsListHydrater();
    }

    McpProxyCacheHandler attach(
        McpProxyCache cache,
        McpProxyCacheListener listener)
    {
        return new HandlerImpl(cache, listener);
    }

    private McpListHydrater hydraterOf(
        int kind)
    {
        return switch (kind)
        {
        case KIND_TOOLS_LIST -> toolsHydrater;
        case KIND_RESOURCES_LIST -> resourcesHydrater;
        case KIND_PROMPTS_LIST -> promptsHydrater;
        default -> null;
        };
    }

    private final class HandlerImpl implements McpProxyCacheHandler
    {
        private final McpProxyCache cache;
        private final McpProxyCacheListener listener;
        private final List<McpListHydrater> activeHydraters;
        private final long[] kindRetryBackoffMs;
        private final long[] kindRetryCancelIds;

        private McpHydrateLifecycleStream lifecycleStream;
        private long lifecycleRetryCancelId;
        private boolean stopped;
        private boolean closedNotified;

        HandlerImpl(
            McpProxyCache cache,
            McpProxyCacheListener listener)
        {
            this.cache = cache;
            this.listener = listener;
            this.kindRetryBackoffMs = new long[KIND_RESOURCES_LIST + 1];
            this.kindRetryCancelIds = new long[KIND_RESOURCES_LIST + 1];
            Arrays.fill(this.kindRetryCancelIds, NO_CANCEL_ID);
            this.lifecycleRetryCancelId = NO_CANCEL_ID;

            final List<McpListHydrater> active = new ArrayList<>();
            if (hydrateFilter.test(KIND_TOOLS_LIST))
            {
                active.add(toolsHydrater);
            }
            if (hydrateFilter.test(KIND_RESOURCES_LIST))
            {
                active.add(resourcesHydrater);
            }
            if (hydrateFilter.test(KIND_PROMPTS_LIST))
            {
                active.add(promptsHydrater);
            }
            this.activeHydraters = active;
        }

        @Override
        public void start()
        {
            acquireLifecycle();
        }

        @Override
        public void stop()
        {
            stopped = true;
            cancelLifecycleRetry();
            for (int i = 0; i < kindRetryCancelIds.length; i++)
            {
                cancelKindRetry(i);
            }
            if (lifecycleStream != null)
            {
                lifecycleStream.doLifecycleEnd(supplyTraceId.getAsLong());
                lifecycleStream = null;
            }
            cache.releaseLifecycle(k -> {});
        }

        @Override
        public void hydrate(
            int kind)
        {
            if (stopped || lifecycleStream == null)
            {
                return;
            }
            final McpListHydrater hydrater = hydraterOf(kind);
            if (hydrater != null)
            {
                hydrater.refresh(this);
            }
        }

        private void acquireLifecycle()
        {
            if (stopped)
            {
                return;
            }
            cache.acquireLifecycle(this::onAcquireLifecycleComplete);
        }

        private void onAcquireLifecycleComplete(
            boolean acquired)
        {
            if (stopped)
            {
                return;
            }
            if (acquired)
            {
                final long traceId = supplyTraceId.getAsLong();
                cache.sessionId = supplySessionId.get();
                cache.authorization = cache.guard != null
                    ? cache.guard.reauthorize(traceId, cache.bindingId, 0L, cache.credentials)
                    : 0L;
                lifecycleStream = new McpHydrateLifecycleStream(this);
                lifecycleStream.doLifecycleBegin(traceId);
            }
            else
            {
                scheduleLifecycleRetry();
            }
        }

        private void onLifecycleOpened(
            long traceId)
        {
            if (activeHydraters.isEmpty())
            {
                return;
            }
            for (McpListHydrater hydrater : activeHydraters)
            {
                hydrater.initiate(this);
            }
        }

        private void onLifecycleClosed()
        {
            lifecycleStream = null;
            cache.releaseLifecycle(k -> {});
            if (!stopped && !closedNotified)
            {
                closedNotified = true;
                listener.onClosed();
            }
        }

        private void scheduleLifecycleRetry()
        {
            cancelLifecycleRetry();
            lifecycleRetryCancelId = signaler.signalAt(
                Instant.now().plus(cache.leaseRetry), 0, sig -> acquireLifecycle());
        }

        private void cancelLifecycleRetry()
        {
            if (lifecycleRetryCancelId != NO_CANCEL_ID)
            {
                signaler.cancel(lifecycleRetryCancelId);
                lifecycleRetryCancelId = NO_CANCEL_ID;
            }
        }

        private void scheduleKindRetry(
            int kind)
        {
            cancelKindRetry(kind);
            long delay = kindRetryBackoffMs[kind];
            delay = delay == 0L ? cache.leaseRetry.toMillis() : Math.min(delay * 2L, cache.leaseTtl.toMillis());
            kindRetryBackoffMs[kind] = delay;
            kindRetryCancelIds[kind] = signaler.signalAt(
                Instant.now().plusMillis(delay), kind, this::onKindRetryFire);
        }

        private void onKindRetryFire(
            int kind)
        {
            kindRetryCancelIds[kind] = NO_CANCEL_ID;
            if (stopped || lifecycleStream == null)
            {
                return;
            }
            final McpListHydrater hydrater = hydraterOf(kind);
            if (hydrater != null)
            {
                hydrater.initiate(this);
            }
        }

        private void cancelKindRetry(
            int kind)
        {
            if (kindRetryCancelIds[kind] != NO_CANCEL_ID)
            {
                signaler.cancel(kindRetryCancelIds[kind]);
                kindRetryCancelIds[kind] = NO_CANCEL_ID;
            }
        }

        private void resetKindBackoff(
            int kind)
        {
            kindRetryBackoffMs[kind] = 0L;
        }
    }

    final class McpHydrateLifecycleStream
    {
        private final HandlerImpl handler;
        private final long initialId;
        private final long replyId;
        private final List<McpListHydrater.McpListHydrateStream> activeListStreams;

        private int state;
        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long replySeq;
        private long replyAck;
        private int replyMax;
        private MessageConsumer receiver;

        McpHydrateLifecycleStream(
            HandlerImpl handler)
        {
            this.handler = handler;
            this.initialId = supplyInitialId.applyAsLong(handler.cache.bindingId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.replyMax = bufferPool.slotCapacity();
            this.activeListStreams = new ArrayList<>();
        }

        void registerListStream(
            McpListHydrater.McpListHydrateStream stream)
        {
            activeListStreams.add(stream);
        }

        void unregisterListStream(
            McpListHydrater.McpListHydrateStream stream)
        {
            activeListStreams.remove(stream);
        }

        private void cleanupListStreams(
            long traceId)
        {
            if (activeListStreams.isEmpty())
            {
                return;
            }
            final List<McpListHydrater.McpListHydrateStream> copy = new ArrayList<>(activeListStreams);
            activeListStreams.clear();
            for (McpListHydrater.McpListHydrateStream stream : copy)
            {
                stream.doListHydrateEnd(traceId);
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
            handler.onLifecycleOpened(traceId);
        }

        private void onLifecycleEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            state = McpState.closedReply(state);
            cleanupListStreams(traceId);
            doLifecycleEnd(traceId);
            handler.onLifecycleClosed();
        }

        private void onLifecycleAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            state = McpState.closedReply(state);
            cleanupListStreams(traceId);
            doLifecycleAbort(traceId);
            handler.onLifecycleClosed();
        }

        private void onLifecycleReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            state = McpState.closedInitial(state);
            cleanupListStreams(traceId);
            handler.onLifecycleClosed();
        }

        void doLifecycleBegin(
            long traceId)
        {
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .lifecycle(l -> l.sessionId(handler.cache.sessionId))
                .build();

            receiver = newStream(this::onLifecycleMessage, handler.cache.bindingId, handler.cache.bindingId, initialId,
                initialSeq, initialAck, initialMax, traceId, handler.cache.authorization, 0L, beginEx);
            state = McpState.openingInitial(state);
        }

        private void doLifecycleWindow(
            long traceId)
        {
            doWindow(receiver, handler.cache.bindingId, handler.cache.bindingId, replyId, replySeq, replyAck, replyMax,
                traceId, handler.cache.authorization, 0L, 0);
        }

        void doLifecycleEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                cleanupListStreams(traceId);
                doEnd(receiver, handler.cache.bindingId, handler.cache.bindingId, initialId, initialSeq, initialAck, initialMax,
                    traceId, handler.cache.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doLifecycleAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doAbort(receiver, handler.cache.bindingId, handler.cache.bindingId, initialId, initialSeq, initialAck, initialMax,
                    traceId, handler.cache.authorization);
                state = McpState.closedInitial(state);
            }
        }
    }

    abstract class McpListHydrater
    {
        protected abstract int kind();

        protected abstract McpProxyCache.McpListCache cacheOf(
            McpProxyCache cache);

        protected abstract void injectInitialBeginEx(
            McpBeginExFW.Builder builder,
            String sessionId);

        final void initiate(
            HandlerImpl handler)
        {
            cacheOf(handler.cache).get((k, v) -> onGetComplete(handler, v));
        }

        final void refresh(
            HandlerImpl handler)
        {
            cacheOf(handler.cache).acquire(acquired -> onAcquireComplete(handler, acquired));
        }

        private void onGetComplete(
            HandlerImpl handler,
            String value)
        {
            if (handler.stopped)
            {
                return;
            }

            if (value != null)
            {
                handler.resetKindBackoff(kind());
            }
            else
            {
                cacheOf(handler.cache).acquire(acquired -> onAcquireComplete(handler, acquired));
            }
        }

        private void onAcquireComplete(
            HandlerImpl handler,
            boolean acquired)
        {
            if (handler.stopped || handler.lifecycleStream == null)
            {
                return;
            }

            if (acquired)
            {
                handler.resetKindBackoff(kind());
                startListStream(handler);
            }
            else
            {
                handler.scheduleKindRetry(kind());
            }
        }

        private void startListStream(
            HandlerImpl handler)
        {
            final long traceId = supplyTraceId.getAsLong();
            final McpListHydrateStream stream = new McpListHydrateStream(handler);
            handler.lifecycleStream.registerListStream(stream);
            stream.doListHydrateBegin(traceId);
        }

        final class McpListHydrateStream
        {
            private final HandlerImpl handler;
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
            private boolean failed;

            McpListHydrateStream(
                HandlerImpl handler)
            {
                this.handler = handler;
                this.bodyBuffer = new ExpandableArrayBuffer();
                this.initialId = supplyInitialId.applyAsLong(handler.cache.bindingId);
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
                    cacheOf(handler.cache).put(value, k -> terminal(traceId));
                }
                else
                {
                    failed = true;
                    terminal(traceId);
                }
            }

            private void onListHydrateAbort(
                AbortFW abort)
            {
                final long traceId = abort.traceId();
                state = McpState.closedReply(state);
                failed = true;
                doListHydrateAbort(traceId);
                terminal(traceId);
            }

            private void onListHydrateReset(
                ResetFW reset)
            {
                final long traceId = reset.traceId();
                state = McpState.closedInitial(state);
                failed = true;
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
                    .inject(builder -> injectInitialBeginEx(builder, handler.cache.sessionId))
                    .build();

                receiver = newStream(this::onListHydrateMessage, handler.cache.bindingId, handler.cache.bindingId, initialId,
                    initialSeq, initialAck, initialMax, traceId, handler.cache.authorization, 0L, beginEx);
                state = McpState.openingInitial(state);
                state = McpState.closingInitial(state);
            }

            void doListHydrateEnd(
                long traceId)
            {
                if (!McpState.initialClosed(state))
                {
                    doEnd(receiver, handler.cache.bindingId, handler.cache.bindingId, initialId,
                        initialSeq, initialAck, initialMax, traceId, handler.cache.authorization);
                    state = McpState.closedInitial(state);
                }
            }

            private void doListHydrateAbort(
                long traceId)
            {
                if (!McpState.initialClosed(state))
                {
                    doAbort(receiver, handler.cache.bindingId, handler.cache.bindingId, initialId,
                        initialSeq, initialAck, initialMax, traceId, handler.cache.authorization);
                    state = McpState.closedInitial(state);
                }
            }

            private void doListHydrateReset(
                long traceId)
            {
                if (!McpState.replyClosed(state))
                {
                    doReset(receiver, handler.cache.bindingId, handler.cache.bindingId, replyId,
                        replySeq, replyAck, replyMax, traceId, handler.cache.authorization);
                    state = McpState.closedReply(state);
                }
            }

            private void doListHydrateWindow(
                long traceId)
            {
                doWindow(receiver, handler.cache.bindingId, handler.cache.bindingId, replyId, replySeq, replyAck, replyMax,
                    traceId, handler.cache.authorization, 0L, 0);
            }

            private void terminal(
                long traceId)
            {
                if (!settled)
                {
                    settled = true;
                    if (handler.lifecycleStream != null)
                    {
                        handler.lifecycleStream.unregisterListStream(this);
                    }
                    cacheOf(handler.cache).release(k -> {});
                    if (failed && !handler.stopped)
                    {
                        handler.listener.onError(kind());
                    }
                }
            }
        }
    }

    private final class McpToolsListHydrater extends McpListHydrater
    {
        @Override
        protected int kind()
        {
            return KIND_TOOLS_LIST;
        }

        @Override
        protected McpProxyCache.McpListCache cacheOf(
            McpProxyCache cache)
        {
            return cache.tools();
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
        @Override
        protected int kind()
        {
            return KIND_RESOURCES_LIST;
        }

        @Override
        protected McpProxyCache.McpListCache cacheOf(
            McpProxyCache cache)
        {
            return cache.resources();
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
        @Override
        protected int kind()
        {
            return KIND_PROMPTS_LIST;
        }

        @Override
        protected McpProxyCache.McpListCache cacheOf(
            McpProxyCache cache)
        {
            return cache.prompts();
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
