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

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_LIFECYCLE;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_GET;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_READ;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_CALL;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;
import static java.lang.System.currentTimeMillis;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheTtlConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpListCache;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpProxyHydrate;
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
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public final class McpProxyFactory implements McpStreamFactory
{
    private static final String MCP_TYPE_NAME = "mcp";

    private static final int SIGNAL_INITIATE_HYDRATE = 1;
    private static final int SIGNAL_REFRESH_TOOLS = 2;
    private static final int SIGNAL_REFRESH_RESOURCES = 3;
    private static final int SIGNAL_REFRESH_PROMPTS = 4;
    private static final long LEASE_TTL_MS = Duration.ofSeconds(30).toMillis();
    private static final long LEASE_RETRY_MS = 100L;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer codecBuffer;
    private final BindingHandler streamFactory;
    private final BufferPool bufferPool;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final LongFunction<StoreHandler> supplyStore;
    private final LongFunction<GuardHandler> supplyGuard;
    private final Supplier<String> supplyHydrateSessionId;
    private final IntPredicate hydrateKindFilter;
    private final Signaler signaler;
    private final int mcpTypeId;

    private final Long2ObjectHashMap<McpBindingConfig> bindings;
    private final Int2ObjectHashMap<BindingHandler> factories;

    public McpProxyFactory(
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
        this.supplyStore = context::supplyStore;
        this.supplyGuard = context::supplyGuard;
        this.supplyHydrateSessionId = config.sessionIdSupplier();
        this.hydrateKindFilter = config.hydrateKindFilter();
        this.signaler = context.signaler();
        this.bindings = new Long2ObjectHashMap<>();
        this.factories = new Int2ObjectHashMap<>();
        this.factories.put(KIND_LIFECYCLE,
            new McpProxyLifecycleFactory(config, context, bindings::get));
        this.factories.put(KIND_TOOLS_CALL,
            new McpProxyToolsCallFactory(config, context, bindings::get));
        this.factories.put(KIND_PROMPTS_GET,
            new McpProxyPromptsGetFactory(config, context, bindings::get));
        this.factories.put(KIND_RESOURCES_READ,
            new McpProxyResourcesReadFactory(config, context, bindings::get));
        this.factories.put(KIND_TOOLS_LIST,
            new McpProxyToolsListFactory(config, context, bindings::get));
        this.factories.put(KIND_PROMPTS_LIST,
            new McpProxyPromptsListFactory(config, context, bindings::get));
        this.factories.put(KIND_RESOURCES_LIST,
            new McpProxyResourcesListFactory(config, context, bindings::get));
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
    }

    @Override
    public int originTypeId()
    {
        return mcpTypeId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        McpBindingConfig newBinding = new McpBindingConfig(binding, supplyGuard);
        newBinding.sessions = new Object2ObjectHashMap<>();
        bindings.put(binding.id, newBinding);

        if (newBinding.options != null && newBinding.options.cache != null)
        {
            final long storeId = binding.resolveId.applyAsLong(newBinding.options.cache.store);
            final StoreHandler store = supplyStore.apply(storeId);
            newBinding.cache = new McpListCache(store);

            McpRouteConfig route = newBinding.resolve(0L);
            if (route != null)
            {
                final long cacheAuthorization;
                if (newBinding.cacheGuard != null)
                {
                    cacheAuthorization = newBinding.cacheGuard.reauthorize(supplyTraceId.getAsLong(),
                        binding.id, 0L, newBinding.cacheCredentials);
                }
                else
                {
                    cacheAuthorization = 0L;
                }

                McpHydrateSession hydrate = new McpHydrateSession(newBinding.id, route.id, newBinding.cache,
                    newBinding.options.cache.ttl, cacheAuthorization);
                newBinding.hydrate = hydrate;
                signaler.signalAt(currentTimeMillis(), SIGNAL_INITIATE_HYDRATE, hydrate::onInitiateSignal);
            }
        }
    }

    @Override
    public void detach(
        long bindingId)
    {
        McpBindingConfig binding = bindings.remove(bindingId);

        if (binding != null && binding.hydrate != null)
        {
            binding.hydrate.cleanup(supplyTraceId.getAsLong());
        }
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
        final OctetsFW extension = begin.extension();

        MessageConsumer newStream = null;

        final McpBeginExFW beginEx = extension.get(mcpBeginExRO::tryWrap);

        if (beginEx != null)
        {
            final BindingHandler factory = factories.get(beginEx.kind());
            if (factory != null)
            {
                newStream = factory.newStream(msgTypeId, buffer, index, length, sender);
            }
        }

        return newStream;
    }

    private record PendingAwait(
        long originId,
        long routedId,
        long streamId,
        long traceId,
        int signalId)
    {
    }

    private final class McpHydrateSession implements McpProxyHydrate
    {
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long authorization;
        private final McpListCache cache;
        private final McpCacheTtlConfig ttl;
        private final String sessionId;
        private final List<PendingAwait> pending = new ArrayList<>();

        private MessageConsumer receiver;
        private int state;
        private int totalKinds;
        private int settledKinds;
        private boolean complete;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        McpHydrateSession(
            long originId,
            long routedId,
            McpListCache cache,
            McpCacheTtlConfig ttl,
            long authorization)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.cache = cache;
            this.ttl = ttl;
            this.authorization = authorization;
            this.sessionId = supplyHydrateSessionId.get();
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.replyMax = bufferPool.slotCapacity();
        }

        private void onInitiateSignal(
            int signalId)
        {
            assert signalId == SIGNAL_INITIATE_HYDRATE;
            final long traceId = supplyTraceId.getAsLong();
            cache.acquireLifecycleLease(LEASE_TTL_MS, acquired ->
            {
                if (acquired)
                {
                    doLifecycleBegin(traceId);
                }
                else
                {
                    signaler.signalAt(currentTimeMillis() + LEASE_RETRY_MS, SIGNAL_INITIATE_HYDRATE,
                        this::onInitiateSignal);
                }
            });
        }

        private void doLifecycleBegin(
            long traceId)
        {
            if (McpState.initialOpening(state))
            {
                return;
            }

            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .lifecycle(l -> l.sessionId(sessionId))
                .build();

            receiver = newStream(this::onMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, authorization, 0L, beginEx);
            state = McpState.openingInitial(state);
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
                onBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                state = McpState.closedInitial(state);
                state = McpState.closedReply(state);
                break;
            default:
                break;
            }
        }

        private void onBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            state = McpState.openingReply(state);
            doReplyWindow(traceId);

            int filtered = 0;
            for (int kind : new int[] { KIND_TOOLS_LIST, KIND_RESOURCES_LIST, KIND_PROMPTS_LIST })
            {
                if (hydrateKindFilter.test(kind))
                {
                    filtered++;
                }
            }
            totalKinds = filtered;

            if (totalKinds == 0)
            {
                markComplete();
            }
            else
            {
                for (int kind : new int[] { KIND_TOOLS_LIST, KIND_RESOURCES_LIST, KIND_PROMPTS_LIST })
                {
                    if (!hydrateKindFilter.test(kind))
                    {
                        continue;
                    }
                    final int listKind = kind;
                    cache.get(listKind, (key, value) ->
                    {
                        if (value != null)
                        {
                            markSettled(listKind);
                        }
                        else
                        {
                            cache.acquireLease(listKind, LEASE_TTL_MS, acquired ->
                            {
                                if (acquired)
                                {
                                    startListStream(listKind, traceId);
                                }
                                else
                                {
                                    markSettled(listKind);
                                }
                            });
                        }
                    });
                }
            }
        }

        private void doReplyWindow(
            long traceId)
        {
            doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, 0L, 0);
        }

        private void startListStream(
            int kind,
            long traceId)
        {
            HydrateListStream list = new HydrateListStream(this, originId, routedId, kind, cache, sessionId);
            list.initiate(traceId);
        }

        private void markSettled(
            int kind)
        {
            if (!complete && ++settledKinds >= totalKinds)
            {
                markComplete();
            }
        }

        private void settle(
            int kind)
        {
            markSettled(kind);
            scheduleRefresh(kind);
        }

        private void scheduleRefresh(
            int kind)
        {
            final Duration interval = ttlForKind(kind);
            if (interval != null)
            {
                signaler.signalAt(currentTimeMillis() + interval.toMillis(), signalIdForKind(kind), this::onRefreshSignal);
            }
        }

        private void onRefreshSignal(
            int signalId)
        {
            final int kind = kindForSignalId(signalId);
            if (kind != 0)
            {
                final long traceId = supplyTraceId.getAsLong();
                cache.acquireLease(kind, LEASE_TTL_MS, acquired ->
                {
                    if (acquired)
                    {
                        startListStream(kind, traceId);
                    }
                });
            }
        }

        private Duration ttlForKind(
            int kind)
        {
            Duration interval = null;
            if (ttl != null)
            {
                interval = switch (kind)
                {
                case KIND_TOOLS_LIST -> ttl.tools;
                case KIND_RESOURCES_LIST -> ttl.resources;
                case KIND_PROMPTS_LIST -> ttl.prompts;
                default -> null;
                };
            }
            return interval;
        }

        private static int signalIdForKind(
            int kind)
        {
            return switch (kind)
            {
            case KIND_TOOLS_LIST -> SIGNAL_REFRESH_TOOLS;
            case KIND_RESOURCES_LIST -> SIGNAL_REFRESH_RESOURCES;
            case KIND_PROMPTS_LIST -> SIGNAL_REFRESH_PROMPTS;
            default -> 0;
            };
        }

        private static int kindForSignalId(
            int signalId)
        {
            return switch (signalId)
            {
            case SIGNAL_REFRESH_TOOLS -> KIND_TOOLS_LIST;
            case SIGNAL_REFRESH_RESOURCES -> KIND_RESOURCES_LIST;
            case SIGNAL_REFRESH_PROMPTS -> KIND_PROMPTS_LIST;
            default -> 0;
            };
        }

        private void markComplete()
        {
            complete = true;
            for (PendingAwait p : pending)
            {
                signaler.signalNow(p.originId(), p.routedId(), p.streamId(), p.traceId(), p.signalId(), 0);
            }
            pending.clear();
            cache.releaseLifecycleLease(k ->
            {
            });
        }

        @Override
        public void awaitComplete(
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId)
        {
            if (complete)
            {
                signaler.signalNow(originId, routedId, streamId, traceId, signalId, 0);
            }
            else
            {
                pending.add(new PendingAwait(originId, routedId, streamId, traceId, signalId));
            }
        }

        @Override
        public void cleanup(
            long traceId)
        {
            pending.clear();
            if (receiver != null && !McpState.initialClosed(state))
            {
                doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
                state = McpState.closedInitial(state);
            }
        }
    }

    private final class HydrateListStream
    {
        private final McpHydrateSession parent;
        private final long originId;
        private final long routedId;
        private final int kind;
        private final McpListCache cache;
        private final String sessionId;
        private final long initialId;
        private final long replyId;

        private MessageConsumer receiver;
        private int state;
        private byte[] body;
        private int bodyLen;
        private boolean settled;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        HydrateListStream(
            McpHydrateSession parent,
            long originId,
            long routedId,
            int kind,
            McpListCache cache,
            String sessionId)
        {
            this.parent = parent;
            this.originId = originId;
            this.routedId = routedId;
            this.kind = kind;
            this.cache = cache;
            this.sessionId = sessionId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.replyMax = bufferPool.slotCapacity();
            this.body = new byte[1024];
        }

        private void initiate(
            long traceId)
        {
            final String sid = sessionId;
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .inject(b ->
                {
                    switch (kind)
                    {
                    case KIND_TOOLS_LIST -> b.toolsList(t -> t.sessionId(sid));
                    case KIND_RESOURCES_LIST -> b.resourcesList(r -> r.sessionId(sid));
                    case KIND_PROMPTS_LIST -> b.promptsList(p -> p.sessionId(sid));
                    default -> throw new IllegalStateException("unexpected hydrate list kind: " + kind);
                    }
                })
                .build();

            receiver = newStream(this::onMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, parent.authorization, 0L, beginEx);
            state = McpState.openingInitial(state);

            doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, parent.authorization);
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
                onBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                state = McpState.closedReply(state);
                if (cache != null)
                {
                    cache.releaseLease(kind, l -> settle());
                }
                else
                {
                    settle();
                }
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
            state = McpState.closedReply(state);
            if (cache != null && bodyLen > 0)
            {
                final String value = new String(body, 0, bodyLen, StandardCharsets.UTF_8);
                cache.put(kind, value, k -> cache.releaseLease(kind, l -> settle()));
            }
            else if (cache != null)
            {
                cache.releaseLease(kind, l -> settle());
            }
            else
            {
                settle();
            }
        }

        private void doReplyWindow(
            long traceId)
        {
            doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, parent.authorization, 0L, 0);
        }

        private void settle()
        {
            if (!settled)
            {
                settled = true;
                parent.settle(kind);
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
