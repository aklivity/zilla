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
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpFlushExFW.KIND_PROMPTS_LIST_CHANGED;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpFlushExFW.KIND_RESOURCES_LIST_CHANGED;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpFlushExFW.KIND_TOOLS_LIST_CHANGED;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpState;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpFlushExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

final class McpProxyCacheHydrater
{
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer codecBuffer;
    private final BindingHandler streamFactory;
    private final BufferPool bufferPool;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final int mcpTypeId;

    private final BeginFW beginRO = new BeginFW();
    private final EndFW endRO = new EndFW();
    private final DataFW dataRO = new DataFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final McpFlushExFW mcpFlushExRO = new McpFlushExFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    private final Int2ObjectHashMap<McpListHydrater> hydraters;

    McpProxyCacheHydrater(
        McpConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.codecBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.bufferPool = context.bufferPool();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.mcpTypeId = context.supplyTypeId("mcp");

        this.hydraters = new Int2ObjectHashMap<>();
        hydraters.put(KIND_TOOLS_LIST, new McpToolsListHydrater());
        hydraters.put(KIND_RESOURCES_LIST, new McpResourcesListHydrater());
        hydraters.put(KIND_PROMPTS_LIST, new McpPromptsListHydrater());
    }

    McpProxyCacheHandler attach(
        McpProxyCache cache,
        McpProxyCacheListener listener)
    {
        return new HandlerImpl(cache, listener);
    }

    private final class HandlerImpl implements McpProxyCacheHandler
    {
        private final McpProxyCache cache;
        private final McpProxyCacheListener listener;

        private McpHydrateLifecycleStream lifecycle;
        private boolean stopped;
        private boolean closedNotified;

        HandlerImpl(
            McpProxyCache cache,
            McpProxyCacheListener listener)
        {
            this.cache = cache;
            this.listener = listener;
        }

        @Override
        public void start()
        {
            if (stopped)
            {
                return;
            }
            cache.acquireLock(this::onAcquireLifecycleComplete);
        }

        @Override
        public void stop()
        {
            stopped = true;
            if (lifecycle != null)
            {
                final long traceId = supplyTraceId.getAsLong();
                lifecycle.abortStreams(traceId);
                lifecycle.doLifecycleEnd(traceId);
                lifecycle = null;
            }
            cache.releaseLock(k -> {});
        }

        @Override
        public void hydrate(
            int kind)
        {
            if (stopped || lifecycle == null)
            {
                return;
            }
            final McpListHydrater hydrater = hydraters.get(kind);
            if (hydrater != null)
            {
                hydrater.hydrate(this);
            }
        }

        @Override
        public void onChanged(
            int kind)
        {
            if (!stopped)
            {
                listener.onChanged(kind);
            }
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
                cache.authorization = cache.guard != null
                    ? cache.guard.reauthorize(traceId, cache.bindingId, 0L, cache.credentials)
                    : 0L;
                lifecycle = new McpHydrateLifecycleStream(this);
                lifecycle.doLifecycleBegin(traceId);
            }
            else
            {
                notifyClosed();
            }
        }

        private void onLifecycleOpened(
            long traceId)
        {
            if (!stopped)
            {
                listener.onOpened();
            }
        }

        private void onLifecycleClosed()
        {
            lifecycle = null;
            cache.releaseLock(k -> {});
            notifyClosed();
        }

        private void notifyClosed()
        {
            if (!stopped && !closedNotified)
            {
                closedNotified = true;
                listener.onClosed();
            }
        }
    }

    final class McpHydrateLifecycleStream
    {
        private final HandlerImpl handler;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final List<McpListHydrater.McpListHydrateStream> streams;

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
            this.originId = handler.cache.bindingId;
            this.routedId = handler.cache.bindingId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.replyMax = bufferPool.slotCapacity();
            this.streams = new ArrayList<>();
        }

        void register(
            McpListHydrater.McpListHydrateStream stream)
        {
            streams.add(stream);
        }

        void unregister(
            McpListHydrater.McpListHydrateStream stream)
        {
            streams.remove(stream);
        }

        private void cleanupStreams(
            long traceId)
        {
            if (streams.isEmpty())
            {
                return;
            }
            final List<McpListHydrater.McpListHydrateStream> copy = new ArrayList<>(streams);
            streams.clear();
            for (McpListHydrater.McpListHydrateStream stream : copy)
            {
                stream.doListHydrateEnd(traceId);
            }
        }

        void abortStreams(
            long traceId)
        {
            if (streams.isEmpty())
            {
                return;
            }
            final List<McpListHydrater.McpListHydrateStream> copy = new ArrayList<>(streams);
            streams.clear();
            for (McpListHydrater.McpListHydrateStream stream : copy)
            {
                stream.doListHydrateAbort(traceId);
                stream.doListHydrateReset(traceId);
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
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onLifecycleBegin(begin);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onLifecycleEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onLifecycleAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onLifecycleFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onLifecycleReset(reset);
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

            final OctetsFW extension = begin.extension();
            final McpBeginExFW beginEx = extension.sizeof() > 0
                ? mcpBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;
            if (beginEx != null && beginEx.kind() == McpBeginExFW.KIND_LIFECYCLE)
            {
                handler.cache.sessionId = beginEx.lifecycle().sessionId().asString();
            }

            doLifecycleWindow(traceId);
            handler.onLifecycleOpened(traceId);
        }

        private void onLifecycleEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            state = McpState.closedReply(state);
            cleanupStreams(traceId);
            doLifecycleEnd(traceId);
            handler.onLifecycleClosed();
        }

        private void onLifecycleAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            state = McpState.closedReply(state);
            cleanupStreams(traceId);
            doLifecycleAbort(traceId);
            handler.onLifecycleClosed();
        }

        private void onLifecycleFlush(
            FlushFW flush)
        {
            final OctetsFW extension = flush.extension();
            final McpFlushExFW flushEx = mcpFlushExRO.wrap(extension.buffer(), extension.offset(), extension.limit());

            switch (flushEx.kind())
            {
            case KIND_TOOLS_LIST_CHANGED:
                handler.onChanged(KIND_TOOLS_LIST);
                break;
            case KIND_PROMPTS_LIST_CHANGED:
                handler.onChanged(KIND_PROMPTS_LIST);
                break;
            case KIND_RESOURCES_LIST_CHANGED:
                handler.onChanged(KIND_RESOURCES_LIST);
                break;
            default:
                break;
            }
        }

        private void onLifecycleReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            state = McpState.closedInitial(state);
            cleanupStreams(traceId);
            handler.onLifecycleClosed();
        }

        void doLifecycleBegin(
            long traceId)
        {
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .lifecycle(l -> l.capabilities(0))
                .build();

            receiver = newStream(this::onLifecycleMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, handler.cache.authorization, 0L, beginEx);
            state = McpState.openingInitial(state);
        }

        private void doLifecycleWindow(
            long traceId)
        {
            doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, handler.cache.authorization, 0L, 0);
        }

        void doLifecycleEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                cleanupStreams(traceId);
                doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, handler.cache.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doLifecycleAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, handler.cache.authorization);
                state = McpState.closedInitial(state);
            }
        }
    }

    abstract class McpListHydrater
    {
        protected abstract int kind();

        protected abstract void injectInitialBeginEx(
            McpBeginExFW.Builder builder,
            String sessionId);

        final void hydrate(
            HandlerImpl handler)
        {
            final McpProxyCache.McpListCache listCache = handler.cache.cacheOf(kind());
            if (handler.cache.populated)
            {
                listCache.acquire(acquired -> onAcquireComplete(handler, acquired));
            }
            else
            {
                listCache.get((k, v) -> onGetComplete(handler, v));
            }
        }

        private void onGetComplete(
            HandlerImpl handler,
            String value)
        {
            if (handler.stopped || handler.lifecycle == null)
            {
                return;
            }

            if (value == null)
            {
                handler.cache.cacheOf(kind()).acquire(acquired -> onAcquireComplete(handler, acquired));
            }
        }

        private void onAcquireComplete(
            HandlerImpl handler,
            boolean acquired)
        {
            if (handler.stopped || handler.lifecycle == null)
            {
                return;
            }

            if (acquired)
            {
                startListStream(handler);
            }
            else
            {
                handler.listener.onError(kind());
            }
        }

        private void startListStream(
            HandlerImpl handler)
        {
            final long traceId = supplyTraceId.getAsLong();
            final McpListHydrateStream stream = new McpListHydrateStream(handler);
            handler.lifecycle.register(stream);
            stream.doListHydrateBegin(traceId);
        }

        final class McpListHydrateStream
        {
            private final HandlerImpl handler;
            private final long originId;
            private final long routedId;
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
                this.originId = handler.cache.bindingId;
                this.routedId = handler.cache.bindingId;
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
                    final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                    onListHydrateBegin(begin);
                    break;
                case DataFW.TYPE_ID:
                    final DataFW data = dataRO.wrap(buffer, index, index + length);
                    onListHydrateData(data);
                    break;
                case EndFW.TYPE_ID:
                    final EndFW end = endRO.wrap(buffer, index, index + length);
                    onListHydrateEnd(end);
                    break;
                case AbortFW.TYPE_ID:
                    final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                    onListHydrateAbort(abort);
                    break;
                case ResetFW.TYPE_ID:
                    final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                    onListHydrateReset(reset);
                    break;
                case WindowFW.TYPE_ID:
                    final WindowFW window = windowRO.wrap(buffer, index, index + length);
                    onListHydrateWindow(window);
                    break;
                default:
                    break;
                }
            }

            private void onListHydrateBegin(
                BeginFW begin)
            {
                replySeq = begin.sequence();
                replyAck = begin.acknowledge();
                state = McpState.openingReply(state);
                doListHydrateWindow(begin.traceId());
            }

            private void onListHydrateData(
                DataFW data)
            {
                replySeq = data.sequence() + data.reserved();

                final OctetsFW payload = data.payload();
                if (payload != null)
                {
                    final int payloadLen = payload.sizeof();
                    bodyBuffer.putBytes(bodyLen, payload.buffer(), payload.offset(), payloadLen);
                    bodyLen += payloadLen;
                }

                replyAck = replySeq;
                doListHydrateWindow(data.traceId());
            }

            private void onListHydrateEnd(
                EndFW end)
            {
                final long traceId = end.traceId();
                state = McpState.closedReply(state);
                doListHydrateEnd(traceId);
                if (bodyLen > 0)
                {
                    final String value = bodyBuffer.getStringWithoutLengthUtf8(0, bodyLen);
                    handler.cache.cacheOf(kind()).put(value, k -> terminal(traceId));
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

                receiver = newStream(this::onListHydrateMessage, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, handler.cache.authorization, 0L, beginEx);
                state = McpState.openingInitial(state);
            }

            void doListHydrateEnd(
                long traceId)
            {
                if (!McpState.initialClosed(state))
                {
                    doEnd(receiver, originId, routedId, initialId,
                        initialSeq, initialAck, initialMax, traceId, handler.cache.authorization);
                    state = McpState.closedInitial(state);
                }
            }

            private void doListHydrateAbort(
                long traceId)
            {
                if (!McpState.initialClosed(state))
                {
                    doAbort(receiver, originId, routedId, initialId,
                        initialSeq, initialAck, initialMax, traceId, handler.cache.authorization);
                    state = McpState.closedInitial(state);
                }
            }

            private void doListHydrateReset(
                long traceId)
            {
                if (!McpState.replyClosed(state))
                {
                    doReset(receiver, originId, routedId, replyId,
                        replySeq, replyAck, replyMax, traceId, handler.cache.authorization);
                    state = McpState.closedReply(state);
                }
            }

            private void doListHydrateWindow(
                long traceId)
            {
                doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, handler.cache.authorization, 0L, 0);
            }

            private void terminal(
                long traceId)
            {
                if (!settled)
                {
                    settled = true;
                    if (handler.lifecycle != null)
                    {
                        handler.lifecycle.unregister(this);
                    }
                    handler.cache.cacheOf(kind()).release(k -> {});
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
