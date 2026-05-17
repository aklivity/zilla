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
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public final class McpProxyFactory implements McpStreamFactory
{
    private static final String MCP_TYPE_NAME = "mcp";

    private static final int SIGNAL_INITIATE_HYDRATE = 1;

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
        McpBindingConfig newBinding = new McpBindingConfig(binding);
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
                McpHydrateSession hydrate = new McpHydrateSession(newBinding.id, route.id, newBinding.cache);
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

    private final class McpHydrateSession implements McpProxyHydrate
    {
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final McpListCache cache;
        private final String sessionId;

        private MessageConsumer receiver;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        McpHydrateSession(
            long originId,
            long routedId,
            McpListCache cache)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.cache = cache;
            this.sessionId = supplyHydrateSessionId.get();
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.replyMax = bufferPool.slotCapacity();
        }

        private void onInitiateSignal(
            int signalId)
        {
            assert signalId == SIGNAL_INITIATE_HYDRATE;
            doLifecycleBegin(supplyTraceId.getAsLong());
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
                initialSeq, initialAck, initialMax, traceId, 0L, 0L, beginEx);
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

            for (int kind : new int[] { KIND_TOOLS_LIST, KIND_RESOURCES_LIST, KIND_PROMPTS_LIST })
            {
                if (!hydrateKindFilter.test(kind))
                {
                    continue;
                }
                final int listKind = kind;
                cache.get(listKind, (key, value) ->
                {
                    if (value == null)
                    {
                        startListStream(listKind, traceId);
                    }
                });
            }
        }

        private void doReplyWindow(
            long traceId)
        {
            doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, 0L, 0L, 0);
        }

        private void startListStream(
            int kind,
            long traceId)
        {
            HydrateListStream list = new HydrateListStream(originId, routedId, kind, cache, sessionId);
            list.initiate(traceId);
        }

        @Override
        public void cleanup(
            long traceId)
        {
            if (receiver != null && !McpState.initialClosed(state))
            {
                doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, 0L);
                state = McpState.closedInitial(state);
            }
        }
    }

    private final class HydrateListStream
    {
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

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        HydrateListStream(
            long originId,
            long routedId,
            int kind,
            McpListCache cache,
            String sessionId)
        {
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
                initialSeq, initialAck, initialMax, traceId, 0L, 0L, beginEx);
            state = McpState.openingInitial(state);

            doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, 0L);
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
                cache.put(kind, value, k ->
                {
                });
            }
        }

        private void doReplyWindow(
            long traceId)
        {
            doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, 0L, 0L, 0);
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
