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

public final class McpProxyCacheHydrater
{
    static final int SIGNAL_INITIATE_LIFECYCLE = 1;
    static final int SIGNAL_REFRESH_TOOLS = 2;
    static final int SIGNAL_REFRESH_RESOURCES = 3;
    static final int SIGNAL_REFRESH_PROMPTS = 4;

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer codecBuffer;
    private final BindingHandler streamFactory;
    private final BufferPool bufferPool;
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
    private final List<McpListHydrater> activeHydraters;

    public McpProxyCacheHydrater(
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
        this.supplySessionId = config.sessionIdSupplier();
        this.hydrateFilter = config.hydrateFilter();

        this.toolsHydrater = new McpToolsListHydrater();
        this.resourcesHydrater = new McpResourcesListHydrater();
        this.promptsHydrater = new McpPromptsListHydrater();

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

    int activeHydraterCount()
    {
        return activeHydraters.size();
    }

    long supplyTraceId()
    {
        return supplyTraceId.getAsLong();
    }

    String supplySessionId()
    {
        return supplySessionId.get();
    }

    McpHydrateLifecycleStream newLifecycleStream(
        McpCacheContext context)
    {
        return new McpHydrateLifecycleStream(context);
    }

    void initiateListHydraters(
        McpCacheContext context,
        long traceId)
    {
        for (McpListHydrater hydrater : activeHydraters)
        {
            hydrater.initiate(context);
        }
    }

    List<McpCacheContext.McpListCache> activeCaches(
        McpCacheContext context)
    {
        final List<McpCacheContext.McpListCache> active = new ArrayList<>(activeHydraters.size());
        for (McpListHydrater hydrater : activeHydraters)
        {
            active.add(hydrater.cacheOf(context));
        }
        return active;
    }

    void refresh(
        McpCacheContext context,
        int signalId,
        boolean polling)
    {
        final McpListHydrater hydrater = switch (signalId)
        {
        case SIGNAL_REFRESH_TOOLS -> toolsHydrater;
        case SIGNAL_REFRESH_RESOURCES -> resourcesHydrater;
        case SIGNAL_REFRESH_PROMPTS -> promptsHydrater;
        default -> null;
        };
        if (hydrater != null)
        {
            if (polling)
            {
                hydrater.initiate(context);
            }
            else
            {
                hydrater.refresh(context);
            }
        }
    }

    final class McpHydrateLifecycleStream
    {
        private final McpCacheContext context;
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
            McpCacheContext context)
        {
            this.context = context;
            this.initialId = supplyInitialId.applyAsLong(context.bindingId);
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
            context.onLifecycleOpened(traceId);
        }

        private void onLifecycleEnd(
            EndFW end)
        {
            if (!McpState.replyClosed(state))
            {
                final long traceId = end.traceId();
                state = McpState.closedReply(state);
                cleanupListStreams(traceId);
                doLifecycleEnd(traceId);
                context.onLifecycleClosed();
            }
        }

        private void onLifecycleAbort(
            AbortFW abort)
        {
            if (!McpState.replyClosed(state))
            {
                final long traceId = abort.traceId();
                state = McpState.closedReply(state);
                cleanupListStreams(traceId);
                doLifecycleAbort(traceId);
                context.onLifecycleClosed();
            }
        }

        private void onLifecycleReset(
            ResetFW reset)
        {
            if (!McpState.initialClosed(state))
            {
                final long traceId = reset.traceId();
                state = McpState.closedInitial(state);
                cleanupListStreams(traceId);
                context.onLifecycleClosed();
            }
        }

        void doLifecycleBegin(
            long traceId)
        {
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .lifecycle(l -> l.sessionId(context.sessionId))
                .build();

            receiver = newStream(this::onLifecycleMessage, context.bindingId, context.bindingId, initialId,
                initialSeq, initialAck, initialMax, traceId, context.authorization, 0L, beginEx);
            state = McpState.openingInitial(state);
        }

        private void doLifecycleWindow(
            long traceId)
        {
            doWindow(receiver, context.bindingId, context.bindingId, replyId, replySeq, replyAck, replyMax,
                traceId, context.authorization, 0L, 0);
        }

        void doLifecycleEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                cleanupListStreams(traceId);
                doEnd(receiver, context.bindingId, context.bindingId, initialId, initialSeq, initialAck, initialMax,
                    traceId, context.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doLifecycleAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doAbort(receiver, context.bindingId, context.bindingId, initialId, initialSeq, initialAck, initialMax,
                    traceId, context.authorization);
                state = McpState.closedInitial(state);
            }
        }
    }

    abstract class McpListHydrater
    {
        protected abstract int signalId();

        protected abstract McpCacheContext.McpListCache cacheOf(
            McpCacheContext context);

        protected abstract void injectInitialBeginEx(
            McpBeginExFW.Builder builder,
            String sessionId);

        final void initiate(
            McpCacheContext context)
        {
            cacheOf(context).get((k, v) -> onGetComplete(context, v));
        }

        final void refresh(
            McpCacheContext context)
        {
            cacheOf(context).acquire(acquired -> onAcquireComplete(context, acquired));
        }

        private void onGetComplete(
            McpCacheContext context,
            String value)
        {
            if (context.detached())
            {
                return;
            }

            if (value != null)
            {
                context.resetBackoff(signalId());
                context.scheduleRefresh(signalId());
            }
            else
            {
                cacheOf(context).acquire(acquired -> onAcquireComplete(context, acquired));
            }
        }

        private void onAcquireComplete(
            McpCacheContext context,
            boolean acquired)
        {
            if (context.detached())
            {
                return;
            }

            if (acquired)
            {
                context.resetBackoff(signalId());
                startListStream(context);
            }
            else
            {
                context.scheduleBackoffRetry(signalId());
            }
        }

        private void startListStream(
            McpCacheContext context)
        {
            final long traceId = supplyTraceId.getAsLong();
            final McpListHydrateStream stream = new McpListHydrateStream(context);
            context.lifecycleStream().registerListStream(stream);
            stream.doListHydrateBegin(traceId);
        }

        final class McpListHydrateStream
        {
            private final McpCacheContext context;
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

            McpListHydrateStream(
                McpCacheContext context)
            {
                this.context = context;
                this.bodyBuffer = new ExpandableArrayBuffer();
                this.initialId = supplyInitialId.applyAsLong(context.bindingId);
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
                    cacheOf(context).put(value, k -> terminal(traceId));
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
                    .inject(builder -> injectInitialBeginEx(builder, context.sessionId))
                    .build();

                receiver = newStream(this::onListHydrateMessage, context.bindingId, context.bindingId, initialId,
                    initialSeq, initialAck, initialMax, traceId, context.authorization, 0L, beginEx);
                state = McpState.openingInitial(state);
                state = McpState.closingInitial(state);
            }

            void doListHydrateEnd(
                long traceId)
            {
                if (!McpState.initialClosed(state))
                {
                    doEnd(receiver, context.bindingId, context.bindingId, initialId,
                        initialSeq, initialAck, initialMax, traceId, context.authorization);
                    state = McpState.closedInitial(state);
                }
            }

            private void doListHydrateAbort(
                long traceId)
            {
                if (!McpState.initialClosed(state))
                {
                    doAbort(receiver, context.bindingId, context.bindingId, initialId,
                        initialSeq, initialAck, initialMax, traceId, context.authorization);
                    state = McpState.closedInitial(state);
                }
            }

            private void doListHydrateReset(
                long traceId)
            {
                if (!McpState.replyClosed(state))
                {
                    doReset(receiver, context.bindingId, context.bindingId, replyId,
                        replySeq, replyAck, replyMax, traceId, context.authorization);
                    state = McpState.closedReply(state);
                }
            }

            private void doListHydrateWindow(
                long traceId)
            {
                doWindow(receiver, context.bindingId, context.bindingId, replyId, replySeq, replyAck, replyMax,
                    traceId, context.authorization, 0L, 0);
            }

            private void terminal(
                long traceId)
            {
                if (!settled)
                {
                    settled = true;
                    final McpHydrateLifecycleStream lifecycle = context.lifecycleStream();
                    if (lifecycle != null)
                    {
                        lifecycle.unregisterListStream(this);
                    }
                    cacheOf(context).release(k -> {});
                    context.scheduleRefresh(signalId());
                }
            }
        }
    }

    private final class McpToolsListHydrater extends McpListHydrater
    {
        @Override
        protected int signalId()
        {
            return SIGNAL_REFRESH_TOOLS;
        }

        @Override
        protected McpCacheContext.McpListCache cacheOf(
            McpCacheContext context)
        {
            return context.tools();
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
        protected int signalId()
        {
            return SIGNAL_REFRESH_RESOURCES;
        }

        @Override
        protected McpCacheContext.McpListCache cacheOf(
            McpCacheContext context)
        {
            return context.resources();
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
        protected int signalId()
        {
            return SIGNAL_REFRESH_PROMPTS;
        }

        @Override
        protected McpCacheContext.McpListCache cacheOf(
            McpCacheContext context)
        {
            return context.prompts();
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
