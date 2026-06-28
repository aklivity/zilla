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

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRoutePrefix;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpProxyLifecycleFactory.McpLifecycleClient;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpProxyLifecycleFactory.McpLifecycleServer;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpProxyLifecycleFactory.McpRouteRequest;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache.McpProxyCache;
import io.aklivity.zilla.runtime.binding.mcp.internal.transform.McpScopeFilter;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.util.function.LongIntToLongFunction;

abstract class McpProxyListFactory implements BindingHandler
{
    private static final String MCP_TYPE_NAME = "mcp";

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBufferEx(), 0, 0);
    private final DirectBufferEx listReplyCloseRO =
        new UnsafeBufferEx("]}".getBytes(StandardCharsets.UTF_8));
    private final DirectBufferEx listReplySeparatorRO =
        new UnsafeBufferEx(",".getBytes(StandardCharsets.UTF_8));

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx codecBuffer;
    private final BindingHandler streamFactory;
    private final BufferPool bufferPool;
    private final int decodeMax;
    private final LongUnaryOperator supplyInitialId;
    private final LongIntToLongFunction supplyInitialIdHash;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final int mcpTypeId;
    private final LongFunction<McpBindingConfig> supplyBinding;
    private final int kind;
    private final JsonParserFactory listItemParserFactory;

    private final McpListClientDecoder decodeInit = this::decodeInit;
    private final McpListClientDecoder decodeReply = this::decodeReply;
    private final McpListClientDecoder decodeItemsKey = this::decodeItemsKey;
    private final McpListClientDecoder decodeSkipObject = this::decodeSkipObject;
    private final McpListClientDecoder decodeItems = this::decodeItems;
    private final McpListClientDecoder decodeItemStart = this::decodeItemStart;
    private final McpListClientDecoder decodeItemScan = this::decodeItemScan;
    private final McpListClientDecoder decodeItemName = this::decodeItemName;
    private final McpListClientDecoder decodeItemDrop = this::decodeItemDrop;
    private final McpListClientDecoder decodeItemBody = this::decodeItemBody;
    private final McpListClientDecoder decodeItemId = this::decodeItemId;
    private final McpListClientDecoder decodeItemSchemes = this::decodeItemSchemes;
    private final McpListClientDecoder decodeItemScopeValues = this::decodeItemScopeValues;
    private final McpListClientDecoder decodeItemFinalize = this::decodeItemFinalize;
    private final McpListClientDecoder decodeIgnore = this::decodeIgnore;

    // reusable per-worker scope-filter machinery; reconfigured per list response, never reallocated
    private final ScopeAdmitter scopeAdmitter = new ScopeAdmitter();
    private final McpScopeFilter scopeFilter = new McpScopeFilter();
    private final JsonParserEx scopeParser = JsonEx.createParser();
    private final JsonGeneratorEx scopeGenerator = JsonEx.createGenerator();
    private final JsonPipeline scopePipeline = JsonEx.stream(scopeParser)
        .transform(scopeFilter)
        .into(scopeGenerator);
    private final MutableDirectBufferEx scopeSourceBuffer = new UnsafeBufferEx();
    private final MutableDirectBufferEx scopeTargetBuffer = new UnsafeBufferEx();
    private byte[] scopeTargetArray = new byte[0];

    McpProxyListFactory(
        McpConfiguration config,
        EngineContext context,
        LongFunction<McpBindingConfig> supplyBinding,
        int kind)
    {
        this.writeBuffer = context.writeBuffer();
        this.codecBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.bufferPool = context.bufferPool();
        this.decodeMax = bufferPool.slotCapacity();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyInitialIdHash = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.supplyBinding = supplyBinding;
        this.kind = kind;
        this.listItemParserFactory = JsonEx.createParserFactory(Map.of());
    }

    @Override
    public final MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long affinity = begin.affinity();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();

        MessageConsumer newStream = null;

        final McpBindingConfig binding = supplyBinding.apply(routedId);
        final McpBeginExFW beginEx = extension.get(mcpBeginExRO::tryWrap);

        if (binding != null && beginEx != null && beginEx.kind() == kind)
        {
            final String sessionId = sessionId(beginEx);
            if (binding.sessions.get(sessionId) instanceof McpLifecycleServer lifecycle)
            {
                final McpProxyCache.McpListCache cache = cacheOf(binding);
                if (cache != null)
                {
                    newStream = new McpCacheListServer(
                        lifecycle,
                        initialId,
                        affinity,
                        authorization,
                        cache,
                        binding)::onServerMessage;
                }
                else
                {
                    final List<McpRoutePrefix> prefixes = binding.resolveAll(beginEx, authorization)
                        .stream()
                        .map(r -> new McpRoutePrefix(r.id, new String8FW(r.prefix(kind)), r))
                        .toList();
                    newStream = new McpListServer(
                        lifecycle,
                        initialId,
                        affinity,
                        authorization,
                        binding.filterGuard,
                        prefixes)::onServerMessage;
                }
            }
        }

        return newStream;
    }

    protected abstract McpProxyCache.McpListCache cacheOf(
        McpBindingConfig binding);

    protected abstract void injectInitialBeginEx(
        McpBeginExFW.Builder builder,
        String sessionId);

    protected abstract void injectReplyBeginEx(
        McpBeginExFW.Builder builder,
        String sessionId);

    protected abstract DirectBufferEx listReplyOpenPrelude();

    protected abstract String arrayKey();

    protected abstract String idKey();

    protected abstract String sessionId(
        McpBeginExFW beginEx);

    String hydrationPrelude()
    {
        final DirectBufferEx prelude = listReplyOpenPrelude();
        return prelude.getStringWithoutLengthUtf8(0, prelude.capacity());
    }

    String hydrationClose()
    {
        return listReplyCloseRO.getStringWithoutLengthUtf8(0, listReplyCloseRO.capacity());
    }

    MessageConsumer newHydrationList(
        McpLifecycleServer lifecycle,
        MessageConsumer sink,
        long authorization,
        int replyMax,
        McpRoutePrefix route,
        long traceId)
    {
        final long initialId = supplyInitialId.applyAsLong(route.resolvedId());
        final McpListServer server = new McpListServer(
            lifecycle, sink, true, initialId, 0L, authorization, null, List.of(route));
        server.replyMax = replyMax;
        server.driveHydrationBegin(traceId);
        return server::onServerMessage;
    }

    @FunctionalInterface
    private interface RoleVerifier
    {
        boolean verify(
            long authorization,
            List<String> roles);
    }

    private static final RoleVerifier ALLOW_ALL = (authorization, roles) -> true;
    private static final List<String> EMPTY_ROLES = List.of();

    // reusable scope-filter predicate, reconfigured per list response to avoid a per-call capture
    private static final class ScopeAdmitter implements BiPredicate<CharSequence, List<String>>
    {
        private GuardHandler guard;
        private long authorization;

        @Override
        public boolean test(
            CharSequence name,
            List<String> roles)
        {
            return guard.verify(authorization, roles);
        }
    }

    private final class McpListClient implements McpRouteRequest
    {
        private final McpListServer server;
        private final long originId;
        private final long routedId;
        private final String8FW prefix;
        private final Predicate<String> admits;
        private final RoleVerifier verifier;
        private final boolean verifies;
        private final McpLifecycleClient lifecycle;
        private long initialId;
        private long replyId;

        private MessageConsumer sender;
        private int state;
        private int replySlot = NO_SLOT;
        private int replySlotOffset;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private JsonParserEx decodableJson;
        private long decodedParserProgress;       // absolute streamOffset of buffer[offset] passed to decode
        private int decodeDepth;                  // JSON nesting depth in the reply envelope
        private int decodeItemDepth;              // JSON nesting depth within the current item
        private int decodeSkipDepth;              // JSON nesting depth within a skipped value
        private long decodedItemProgress = -1;    // streamOffset of last byte emitted within the current item, -1 between items
        private McpListClientDecoder decoder = decodeInit;
        private String arrayKey;
        private String idKey;
        private boolean itemBegun;
        private boolean itemDeferred;
        private long decodedNameKeyProgress;
        private long decodedNameValueProgress;
        private final List<String> decodedScopes = new ArrayList<>();
        private boolean decodedScopesFound;
        private boolean decodedSchemesFound;
        private boolean decodedNoAuth;
        private boolean decodeSchemesTypeKey;
        private McpListClient(
            McpListServer server,
            long routedId,
            String8FW prefix,
            Predicate<String> admits,
            GuardHandler guard)
        {
            this.server = server;
            this.originId = server.lifecycle.originId;
            this.routedId = routedId;
            this.prefix = prefix;
            this.admits = admits;
            this.verifies = guard != null;
            this.verifier = guard != null ? guard::verify : ALLOW_ALL;
            this.lifecycle = server.lifecycle.supplyClient(routedId);
        }

        private void doClientBegin(
            long traceId)
        {
            lifecycle.doClientBegin(traceId);
            lifecycle.register(traceId, this);
        }

        @Override
        public void onLifecycleSettled(
            long traceId)
        {
            if (McpState.initialClosed(state) || McpState.replyClosed(state))
            {
                return;
            }

            final String sid = lifecycle.sessionId;
            if (sid != null)
            {
                initialId = supplyInitialIdHash.apply(routedId, sid.hashCode());
                replyId = supplyReplyId.applyAsLong(initialId);

                final McpBeginExFW beginEx = mcpBeginExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(mcpTypeId)
                    .inject(b -> injectInitialBeginEx(b, sid))
                    .build();

                sender = newStream(this::onClientMessage, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, lifecycle.authorization, server.affinity, beginEx);
                state = McpState.openingInitial(state);
            }
            else
            {
                server.onClientSkip(traceId);
            }
        }

        private void doClientEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state) &&
                McpState.replyClosed(state))
            {
                if (McpState.initialOpening(state))
                {
                    doEnd(sender, originId, routedId, initialId,
                        initialSeq, initialAck, initialMax, traceId, lifecycle.authorization);
                }
                state = McpState.closedInitial(state);
            }
        }

        private void doClientAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                if (McpState.initialOpening(state))
                {
                    doAbort(sender, originId, routedId, initialId,
                        initialSeq, initialAck, initialMax, traceId, lifecycle.authorization);
                }
                state = McpState.closedInitial(state);
            }
        }

        private void doClientReset(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                if (McpState.initialOpening(state))
                {
                    doReset(sender, originId, routedId, replyId,
                        replySeq, replyAck, replyMax, traceId, lifecycle.authorization, emptyRO);
                }
                state = McpState.closedReply(state);
            }
        }

        private void doClientWindow(
            long traceId,
            long budgetId,
            int padding)
        {
            if (McpState.initialOpening(state))
            {
                state = McpState.openedReply(state);
                doWindow(sender, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, lifecycle.authorization, budgetId, padding);
            }
        }

        private void flushClientWindow(
            long traceId,
            long budgetId,
            int padding,
            long minReplyNoAck,
            int minReplyMax)
        {
            final long newReplyAck = Math.max(replyAck, replySeq - minReplyNoAck);
            final int newReplyMax = Math.max(replyMax, minReplyMax);

            if (newReplyAck > replyAck || newReplyMax > replyMax || !McpState.replyOpened(state))
            {
                replyAck = newReplyAck;
                replyMax = newReplyMax;
                doClientWindow(traceId, budgetId, padding);
            }
        }

        private void onClientMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onClientBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onClientData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onClientEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onClientAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onClientWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onClientReset(reset);
                break;
            default:
                break;
            }
        }

        private void onClientBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();

            replySeq = sequence;
            replyAck = acknowledge;

            state = McpState.openedInitial(state);

            flushClientWindow(traceId, 0L, 0, 0L, decodeMax);
        }

        private void onClientData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            DirectBufferEx buffer = payload.buffer();
            int offset = payload.offset();
            int limit = payload.limit();

            if (replySlot != NO_SLOT)
            {
                final MutableDirectBufferEx slot = bufferPool.buffer(replySlot);
                if (replySlotOffset + (limit - offset) > slot.capacity())
                {
                    state = McpState.closedReply(state);
                    server.onClientError(traceId);
                    return;
                }
                slot.putBytes(replySlotOffset, buffer, offset, limit - offset);
                replySlotOffset += limit - offset;

                buffer = slot;
                offset = 0;
                limit = replySlotOffset;
            }

            decode(traceId, authorization, budgetId, reserved, buffer, offset, limit);

            if (server.hydration)
            {
                flushClientWindow(traceId, 0L, 0, replySlotOffset, decodeMax);
            }
        }

        private void onClientEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence;

            assert replyAck <= replySeq;

            state = McpState.closedReply(state);
            cleanupClientSlot();
            doClientEnd(traceId);
            server.onClientClosed(traceId);
        }

        private void onClientAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence;

            assert replyAck <= replySeq;

            state = McpState.closedReply(state);
            cleanupClientSlot();
            server.onClientError(traceId);
        }

        private void onClientWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int maximum = window.maximum();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;

            initialAck = acknowledge;
            initialMax = maximum;
            initialPad = padding;

            assert initialAck <= initialSeq;

            server.flushServerWindow(traceId, budgetId, padding, initialSeq - initialAck, initialMax);
        }

        private void onClientReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;

            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            state = McpState.closedInitial(state);
            doClientReset(traceId);
            server.onClientError(traceId);
        }

        private void decode(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (decodableJson != null)
            {
                final int delta = (int) (decodableJson.getLocation().getStreamOffset() - decodedParserProgress);
                decodableJson.wrap(buffer, offset + delta, limit);
            }

            McpListClientDecoder previous = null;
            int progress = offset;
            while (progress <= limit && previous != decoder)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, authorization, budgetId, reserved,
                    buffer, offset, progress, limit);
            }

            final int compactBoundaryInBuf;
            if (decodedItemProgress >= 0)
            {
                compactBoundaryInBuf = offset + (int) (decodedItemProgress - decodedParserProgress);
            }
            else
            {
                compactBoundaryInBuf = offset + (int) (decodableJson.getLocation().getStreamOffset() - decodedParserProgress);
            }

            if (compactBoundaryInBuf < limit)
            {
                final int retained = limit - compactBoundaryInBuf;
                if (replySlot == NO_SLOT)
                {
                    replySlot = bufferPool.acquire(initialId);
                    if (replySlot == NO_SLOT)
                    {
                        state = McpState.closedReply(state);
                        server.onClientError(traceId);
                        return;
                    }
                }
                final MutableDirectBufferEx slot = bufferPool.buffer(replySlot);
                if (retained > slot.capacity())
                {
                    state = McpState.closedReply(state);
                    server.onClientError(traceId);
                    return;
                }
                slot.putBytes(0, buffer, compactBoundaryInBuf, retained);
                replySlotOffset = retained;
                decodedParserProgress += compactBoundaryInBuf - offset;
            }
            else
            {
                cleanupClientSlot();
                decodedParserProgress += compactBoundaryInBuf - offset;
            }
        }

        private void decode(
            long traceId)
        {
            if (replySlot != NO_SLOT)
            {
                final MutableDirectBufferEx slot = bufferPool.buffer(replySlot);
                decode(traceId, lifecycle.authorization, 0L, 0, slot, 0, replySlotOffset);
            }
        }

        private void cleanupClientSlot()
        {
            if (replySlot != NO_SLOT)
            {
                bufferPool.release(replySlot);
                replySlot = NO_SLOT;
                replySlotOffset = 0;
            }
        }

        private void onDecodedItemBegin(
            long traceId)
        {
            if (!itemBegun)
            {
                itemBegun = true;
                server.doEncodeBeginItem(traceId);
            }
        }

        private int onDecodedItemChunk(
            DirectBufferEx buffer,
            int offset,
            int length,
            long traceId)
        {
            return server.doEncodeItemChunk(buffer, offset, length, traceId);
        }

        private void onDecodedItemEnd(
            long traceId)
        {
            server.doEncodeEndItem(traceId);
        }
    }

    @FunctionalInterface
    private interface McpListClientDecoder
    {
        int decode(
            McpListClient client,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int progress,
            int limit);
    }

    private int decodeInit(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        if (listItemParserFactory == null)
        {
            client.decoder = decodeIgnore;
            return limit;
        }

        client.decodableJson = JsonEx.createParser(Map.of());
        client.decodableJson.wrap(buffer, progress, limit);
        client.arrayKey = arrayKey();
        client.idKey = idKey();
        client.decoder = decodeReply;

        return progress;
    }

    private int decodeReply(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;

        decode:
        while (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.START_OBJECT)
            {
                client.decodeDepth = 1;
                client.decoder = decodeItemsKey;
                break decode;
            }
        }

        return offset + (int) (parser.getLocation().getStreamOffset() - client.decodedParserProgress);
    }

    private int decodeItemsKey(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;

        decode:
        while (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case KEY_NAME:
                if (client.decodeDepth == 1)
                {
                    final String key = parser.getString();
                    if (client.arrayKey.equals(key))
                    {
                        client.decoder = decodeItems;
                    }
                    else
                    {
                        client.decodeSkipDepth = 0;
                        client.decoder = decodeSkipObject;
                    }
                    break decode;
                }
                break;
            case END_OBJECT:
                client.decodeDepth--;
                if (client.decodeDepth == 0)
                {
                    client.decoder = decodeIgnore;
                    break decode;
                }
                break;
            default:
                break;
            }
        }

        return offset + (int) (parser.getLocation().getStreamOffset() - client.decodedParserProgress);
    }

    private int decodeSkipObject(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;

        decode:
        while (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                client.decodeSkipDepth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                client.decodeSkipDepth--;
                if (client.decodeSkipDepth == 0)
                {
                    client.decoder = decodeItemsKey;
                    break decode;
                }
                break;
            case VALUE_STRING:
            case VALUE_NUMBER:
            case VALUE_TRUE:
            case VALUE_FALSE:
            case VALUE_NULL:
                if (client.decodeSkipDepth == 0)
                {
                    client.decoder = decodeItemsKey;
                    break decode;
                }
                break;
            default:
                break;
            }
        }

        return offset + (int) (parser.getLocation().getStreamOffset() - client.decodedParserProgress);
    }

    private int decodeItems(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;

        decode:
        while (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.START_ARRAY)
            {
                client.decodeItemDepth = 0;
                client.decoder = decodeItemStart;
                break decode;
            }
        }

        return offset + (int) (parser.getLocation().getStreamOffset() - client.decodedParserProgress);
    }

    private int decodeItemStart(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;

        decode:
        while (parser.hasNext())
        {
            final long decodedItemProgress = parser.getLocation().getStreamOffset();
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
                client.decodedItemProgress = decodedItemProgress - 1;
                client.decodeItemDepth = 1;
                client.itemBegun = false;
                client.decodedNameKeyProgress = -1;
                client.decodedScopesFound = false;
                client.decodedSchemesFound = false;
                client.decodedNoAuth = false;
                if (client.admits != null || client.verifies)
                {
                    client.itemDeferred = true;
                    client.decoder = decodeItemScan;
                }
                else
                {
                    client.itemDeferred = false;
                    client.onDecodedItemBegin(traceId);
                    client.decoder = decodeItemBody;
                }
                break decode;
            case END_ARRAY:
                client.decodeDepth--;
                client.decoder = decodeItemsKey;
                break decode;
            default:
                break;
            }
        }

        return offset + (int) (parser.getLocation().getStreamOffset() - client.decodedParserProgress);
    }

    private int decodeItemScan(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;

        decode:
        while (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                client.decodeItemDepth++;
                break;
            case END_ARRAY:
                client.decodeItemDepth--;
                break;
            case END_OBJECT:
                client.decodeItemDepth--;
                if (client.decodeItemDepth == 0)
                {
                    final List<String> roles = client.decodedScopesFound
                        ? client.decodedScopes
                        : EMPTY_ROLES;
                    final boolean admitted = !client.decodedSchemesFound || client.decodedNoAuth ||
                        client.verifier.verify(client.server.authorization, roles);
                    if (admitted)
                    {
                        client.onDecodedItemBegin(traceId);
                        client.itemDeferred = false;
                        if (client.decodedNameKeyProgress >= 0)
                        {
                            final int decodedKeyOffset =
                                offset + (int) (client.decodedNameKeyProgress - client.decodedParserProgress);
                            final int decodedValueOffset =
                                offset + (int) (client.decodedNameValueProgress - client.decodedParserProgress);
                            final int decodedOpenQuote =
                                indexOfByte(buffer, decodedKeyOffset, decodedValueOffset, (byte) '"');
                            final int decodedContent =
                                (decodedOpenQuote != -1 ? decodedOpenQuote : decodedValueOffset) + 1;
                            final int decodedOffset =
                                offset + (int) (client.decodedItemProgress - client.decodedParserProgress);
                            client.onDecodedItemChunk(buffer, decodedOffset,
                                decodedContent - decodedOffset, traceId);
                            client.onDecodedItemChunk(client.prefix.value(), 0,
                                client.prefix.length(), traceId);
                            client.decodedItemProgress =
                                client.decodedParserProgress + (long) (decodedContent - offset);
                        }
                        client.decoder = decodeItemFinalize;
                    }
                    else
                    {
                        client.decodedItemProgress = -1;
                        client.decoder = decodeItemStart;
                    }
                    break decode;
                }
                break;
            case KEY_NAME:
                if (client.decodeItemDepth == 1)
                {
                    final CharSequence key = client.decodableJson.getStringView();
                    if (client.idKey.contentEquals(key))
                    {
                        client.decoder = decodeItemName;
                        break decode;
                    }
                    else if (client.verifies && "securitySchemes".contentEquals(key))
                    {
                        client.decodedSchemesFound = true;
                        client.decodeSchemesTypeKey = false;
                        client.decoder = decodeItemSchemes;
                        break decode;
                    }
                }
                break;
            default:
                break;
            }
        }

        return offset + (int) (parser.getLocation().getStreamOffset() - client.decodedParserProgress);
    }

    private int decodeItemName(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;
        final long decodedKeyProgress = parser.getLocation().getStreamOffset();

        if (parser.hasNext())
        {
            final long decodedValueProgress = parser.getLocation().getStreamOffset();
            final JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.VALUE_STRING)
            {
                final String name = parser.getString();
                if (client.admits == null || client.admits.test(name))
                {
                    if (client.verifies)
                    {
                        client.decodedNameKeyProgress = decodedKeyProgress;
                        client.decodedNameValueProgress = decodedValueProgress;
                        client.decoder = decodeItemScan;
                    }
                    else
                    {
                        client.onDecodedItemBegin(traceId);
                        client.itemDeferred = false;
                        final int decodedKeyOffset =
                            offset + (int) (decodedKeyProgress - client.decodedParserProgress);
                        final int decodedValueOffset =
                            offset + (int) (decodedValueProgress - client.decodedParserProgress);
                        final int decodedOpenQuote =
                            indexOfByte(buffer, decodedKeyOffset, decodedValueOffset, (byte) '"');
                        final int decodedContent =
                            (decodedOpenQuote != -1 ? decodedOpenQuote : decodedValueOffset) + 1;
                        final int decodedOffset =
                            offset + (int) (client.decodedItemProgress - client.decodedParserProgress);
                        client.onDecodedItemChunk(buffer, decodedOffset, decodedContent - decodedOffset, traceId);
                        client.onDecodedItemChunk(client.prefix.value(), 0, client.prefix.length(), traceId);
                        client.decodedItemProgress = client.decodedParserProgress + (long) (decodedContent - offset);
                        client.decoder = decodeItemBody;
                    }
                }
                else
                {
                    client.decodedItemProgress = -1;
                    client.decoder = decodeItemDrop;
                }
            }
            else
            {
                if (client.verifies)
                {
                    client.decoder = decodeItemScan;
                }
                else
                {
                    client.onDecodedItemBegin(traceId);
                    client.itemDeferred = false;
                    client.decoder = decodeItemBody;
                }
            }
        }

        return offset + (int) (parser.getLocation().getStreamOffset() - client.decodedParserProgress);
    }

    private int decodeItemDrop(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;

        decode:
        while (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                client.decodeItemDepth++;
                break;
            case END_ARRAY:
                client.decodeItemDepth--;
                break;
            case END_OBJECT:
                client.decodeItemDepth--;
                if (client.decodeItemDepth == 0)
                {
                    client.decodedItemProgress = -1;
                    client.decoder = decodeItemStart;
                    break decode;
                }
                break;
            default:
                break;
            }
        }

        return offset + (int) (parser.getLocation().getStreamOffset() - client.decodedParserProgress);
    }

    private int decodeItemSchemes(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = client.decodableJson;

        decode:
        while (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                client.decodeItemDepth++;
                break;
            case END_ARRAY:
                client.decodeItemDepth--;
                if (client.decodeItemDepth == 1)
                {
                    client.decoder = decodeItemScan;
                    break decode;
                }
                break;
            case END_OBJECT:
                client.decodeItemDepth--;
                break;
            case KEY_NAME:
                if (client.decodeItemDepth == 3)
                {
                    final CharSequence key = parser.getStringView();
                    if ("type".contentEquals(key))
                    {
                        client.decodeSchemesTypeKey = true;
                    }
                    else if ("scopes".contentEquals(key))
                    {
                        client.decoder = decodeItemScopeValues;
                        break decode;
                    }
                }
                break;
            case VALUE_STRING:
                if (client.decodeSchemesTypeKey)
                {
                    client.decodeSchemesTypeKey = false;
                    if ("noauth".contentEquals(parser.getStringView()))
                    {
                        client.decodedNoAuth = true;
                    }
                }
                break;
            default:
                break;
            }
        }

        return offset + (int) (parser.getLocation().getStreamOffset() - client.decodedParserProgress);
    }

    private int decodeItemScopeValues(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;

        decode:
        while (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case START_ARRAY:
                client.decodeItemDepth++;
                client.decodedScopes.clear();
                client.decodedScopesFound = true;
                break;
            case VALUE_STRING:
                client.decodedScopes.add(parser.getString());
                break;
            case END_ARRAY:
                client.decodeItemDepth--;
                client.decoder = decodeItemSchemes;
                break decode;
            default:
                break;
            }
        }

        return offset + (int) (parser.getLocation().getStreamOffset() - client.decodedParserProgress);
    }

    private int decodeItemBody(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;

        decode:
        while (true)
        {
            final long decodedItemProgress = parser.getLocation().getStreamOffset();
            if (client.decodedItemProgress < decodedItemProgress)
            {
                final int decodedOffset =
                    offset + (int) (client.decodedItemProgress - client.decodedParserProgress);
                final int decodedLimit = offset + (int) (decodedItemProgress - client.decodedParserProgress);
                final int chunkLen = decodedLimit - decodedOffset;
                final int decodedProgress = client.onDecodedItemChunk(buffer, decodedOffset, chunkLen, traceId);
                client.decodedItemProgress += decodedProgress;
                if (decodedProgress < chunkLen)
                {
                    break decode;
                }
            }

            if (!parser.hasNext())
            {
                final long postHasNext = parser.getLocation().getStreamOffset();
                if (client.decodedItemProgress < postHasNext)
                {
                    final int decodedOffset =
                        offset + (int) (client.decodedItemProgress - client.decodedParserProgress);
                    final int decodedLimit = offset + (int) (postHasNext - client.decodedParserProgress);
                    final int chunkLen = decodedLimit - decodedOffset;
                    final int decodedProgress = client.onDecodedItemChunk(buffer, decodedOffset, chunkLen, traceId);
                    client.decodedItemProgress += decodedProgress;
                }
                break decode;
            }
            final long decodedEventProgress = parser.getLocation().getStreamOffset();
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                client.decodeItemDepth++;
                break;
            case END_OBJECT:
                client.decodeItemDepth--;
                if (client.decodeItemDepth == 0)
                {
                    final int decodedLimit = offset + (int) (decodedEventProgress - client.decodedParserProgress);
                    final int decodedOffset =
                        offset + (int) (client.decodedItemProgress - client.decodedParserProgress);
                    final int chunkLen = decodedLimit - decodedOffset;
                    final int decodedProgress = client.onDecodedItemChunk(buffer, decodedOffset, chunkLen, traceId);
                    client.decodedItemProgress += decodedProgress;
                    if (decodedProgress < chunkLen)
                    {
                        client.decoder = decodeItemFinalize;
                        break decode;
                    }
                    client.onDecodedItemEnd(traceId);
                    client.decodedItemProgress = -1;
                    client.decoder = decodeItemStart;
                    break decode;
                }
                break;
            case END_ARRAY:
                client.decodeItemDepth--;
                break;
            case KEY_NAME:
                if (client.decodeItemDepth == 1 &&
                    client.prefix.length() > 0 &&
                    client.idKey.equals(parser.getString()))
                {
                    client.decoder = decodeItemId;
                    break decode;
                }
                break;
            default:
                break;
            }
        }

        return offset + (int) (parser.getLocation().getStreamOffset() - client.decodedParserProgress);
    }

    private int decodeItemId(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;

        final long decodedKeyProgress = parser.getLocation().getStreamOffset();
        if (client.decodedItemProgress < decodedKeyProgress)
        {
            final int decodedOffset =
                offset + (int) (client.decodedItemProgress - client.decodedParserProgress);
            final int decodedLimit = offset + (int) (decodedKeyProgress - client.decodedParserProgress);
            final int chunkLen = decodedLimit - decodedOffset;
            final int decodedProgress = client.onDecodedItemChunk(buffer, decodedOffset, chunkLen, traceId);
            client.decodedItemProgress += decodedProgress;
            if (decodedProgress < chunkLen)
            {
                return offset + (int) (client.decodedItemProgress - client.decodedParserProgress);
            }
        }

        if (parser.hasNext())
        {
            final long decodedValueProgress = parser.getLocation().getStreamOffset();
            final JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.VALUE_STRING)
            {
                final int decodedKeyOffset = offset + (int) (decodedKeyProgress - client.decodedParserProgress);
                final int decodedValueOffset = offset + (int) (decodedValueProgress - client.decodedParserProgress);
                final int decodedOpenQuote = indexOfByte(buffer, decodedKeyOffset, decodedValueOffset, (byte) '"');
                final int decodedContent = (decodedOpenQuote != -1 ? decodedOpenQuote : decodedValueOffset) + 1;
                final int decodedOffset =
                    offset + (int) (client.decodedItemProgress - client.decodedParserProgress);
                client.onDecodedItemChunk(buffer, decodedOffset, decodedContent - decodedOffset, traceId);
                client.onDecodedItemChunk(client.prefix.value(), 0, client.prefix.length(), traceId);
                client.decodedItemProgress =
                    client.decodedParserProgress + (long) (decodedContent - offset);
            }
            client.decoder = decodeItemBody;
        }

        return offset + (int) (parser.getLocation().getStreamOffset() - client.decodedParserProgress);
    }

    private int decodeItemFinalize(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;
        final long decodedItemProgress = parser.getLocation().getStreamOffset();

        if (client.decodedItemProgress < decodedItemProgress)
        {
            final int decodedOffset =
                offset + (int) (client.decodedItemProgress - client.decodedParserProgress);
            final int decodedLimit = offset + (int) (decodedItemProgress - client.decodedParserProgress);
            final int chunkLen = decodedLimit - decodedOffset;
            final int decodedProgress = client.onDecodedItemChunk(buffer, decodedOffset, chunkLen, traceId);
            client.decodedItemProgress += decodedProgress;
            if (decodedProgress < chunkLen)
            {
                return offset + (int) (client.decodedItemProgress - client.decodedParserProgress);
            }
        }

        client.onDecodedItemEnd(traceId);
        client.decodedItemProgress = -1;
        client.decoder = decodeItemStart;

        return offset + (int) (decodedItemProgress - client.decodedParserProgress);
    }

    private int decodeIgnore(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return limit;
    }

    private final class McpListServer
    {
        private final McpLifecycleServer lifecycle;
        private final MessageConsumer sender;
        private final boolean hydration;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;
        private final GuardHandler guard;
        private final Deque<McpRoutePrefix> remaining;

        private int state;
        private int itemsEmitted;
        private McpListClient client;

        private int preludeProgress;
        private int separatorProgress;
        private int postludeProgress;
        private boolean separatorPending;
        private boolean endItemsPending;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private McpListServer(
            McpLifecycleServer lifecycle,
            long initialId,
            long affinity,
            long authorization,
            GuardHandler guard,
            List<McpRoutePrefix> prefixes)
        {
            this(lifecycle, lifecycle.sender, false, initialId, affinity, authorization, guard, prefixes);
        }

        private McpListServer(
            McpLifecycleServer lifecycle,
            MessageConsumer sender,
            boolean hydration,
            long initialId,
            long affinity,
            long authorization,
            GuardHandler guard,
            List<McpRoutePrefix> prefixes)
        {
            this.lifecycle = lifecycle;
            this.sender = sender;
            this.hydration = hydration;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.guard = guard;
            this.remaining = new ArrayDeque<>(prefixes);
        }

        private void onServerMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onServerBegin(begin);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onServerEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onServerAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onServerWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onServerReset(reset);
                break;
            default:
                break;
            }
        }

        private void onServerBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();

            initialSeq = sequence;
            initialAck = acknowledge;

            state = McpState.openingInitial(state);

            flushServerWindow(traceId, 0L, 0, 0L, 0);

            doServerBegin(traceId);
            onNextClient(traceId);
        }

        private void driveHydrationBegin(
            long traceId)
        {
            state = McpState.openingInitial(state);
            doServerBegin(traceId);
            onNextClient(traceId);
        }

        private void onServerEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            state = McpState.closedInitial(state);

            if (client != null)
            {
                client.doClientEnd(traceId);
            }
            if (McpState.closed(state))
            {
                onClientClosed(traceId);
            }
        }

        private void onServerAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            state = McpState.closedInitial(state);

            if (client != null)
            {
                client.doClientAbort(traceId);
            }
            remaining.clear();
            if (McpState.closed(state))
            {
                onClientClosed(traceId);
            }
        }

        private void onServerWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int maximum = window.maximum();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum + acknowledge >= replyMax + replyAck;

            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = padding;

            assert replyAck <= replySeq;

            encode(traceId);
            if (endItemsPending)
            {
                encodeEnd(traceId);
            }
            if (client != null)
            {
                client.decode(traceId);
                client.flushClientWindow(traceId, budgetId, padding, replySeq - replyAck, replyMax);
            }
        }

        private void onServerReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;

            replyAck = acknowledge;

            assert replyAck <= replySeq;

            state = McpState.closedReply(state);
            cleanupEncodeSlot();

            if (client != null)
            {
                client.doClientReset(traceId);
            }
            remaining.clear();
            if (McpState.closed(state))
            {
                onClientClosed(traceId);
            }
        }

        private void onClientClosed(
            long traceId)
        {
            client = null;
            onNextClient(traceId);
        }

        private void onClientError(
            long traceId)
        {
            remaining.clear();
            doServerAbort(traceId);
        }

        private void onClientSkip(
            long traceId)
        {
            client = null;
            if (hydration)
            {
                remaining.clear();
                doServerAbort(traceId);
            }
            else
            {
                onNextClient(traceId);
            }
        }

        private void onNextClient(
            long traceId)
        {
            final McpRoutePrefix route = remaining.poll();
            if (route == null)
            {
                doEncodeEndItems(traceId);
                return;
            }
            final McpRouteConfig routeConfig = route.route();
            final Predicate<String> admits = routeConfig != null && routeConfig.filters(kind)
                ? name -> routeConfig.admits(kind, name)
                : null;
            client = new McpListClient(this, route.resolvedId(), route.prefix(), admits, guard);
            client.doClientBegin(traceId);
        }

        private boolean doEncodeFraming(
            long traceId)
        {
            boolean ready = !McpState.replyClosed(state);
            if (ready && (itemsEmitted > 0 || endItemsPending))
            {
                final DirectBufferEx prelude = listReplyOpenPrelude();
                if (preludeProgress < prelude.capacity())
                {
                    preludeProgress += doServerData(prelude, preludeProgress,
                        prelude.capacity() - preludeProgress, traceId);
                    ready = preludeProgress == prelude.capacity();
                }
                if (ready && separatorPending)
                {
                    if (separatorProgress < listReplySeparatorRO.capacity())
                    {
                        separatorProgress += doServerData(listReplySeparatorRO, separatorProgress,
                            listReplySeparatorRO.capacity() - separatorProgress, traceId);
                    }
                    if (separatorProgress == listReplySeparatorRO.capacity())
                    {
                        separatorPending = false;
                        separatorProgress = 0;
                    }
                    else
                    {
                        ready = false;
                    }
                }
            }
            return ready;
        }

        private int doServerData(
            DirectBufferEx buffer,
            int offset,
            int maxLength,
            long traceId)
        {
            int accepted;
            if (encodeSlot == NO_SLOT)
            {
                final int replyWin = replyMax - (int) (replySeq - replyAck) - replyPad;
                final int length = Math.min(Math.max(replyWin, 0), maxLength);
                if (length > 0)
                {
                    doData(sender, lifecycle.originId, lifecycle.routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, 0x03, 0L, length, buffer, offset, length);
                    replySeq += length;
                    if (hydration)
                    {
                        replyAck = replySeq;
                    }
                }
                accepted = length;
                final int remaining = maxLength - length;
                if (remaining > 0)
                {
                    encodeSlot = bufferPool.acquire(replyId);
                    if (encodeSlot == NO_SLOT)
                    {
                        doServerAbort(traceId);
                    }
                    else
                    {
                        final MutableDirectBufferEx slot = bufferPool.buffer(encodeSlot);
                        final int stashable = Math.min(remaining, slot.capacity());
                        slot.putBytes(0, buffer, offset + length, stashable);
                        encodeSlotOffset = stashable;
                        accepted = length + stashable;
                    }
                }
            }
            else
            {
                final MutableDirectBufferEx slot = bufferPool.buffer(encodeSlot);
                accepted = Math.min(maxLength, slot.capacity() - encodeSlotOffset);
                slot.putBytes(encodeSlotOffset, buffer, offset, accepted);
                encodeSlotOffset += accepted;
                encode(traceId);
            }
            return accepted;
        }

        private void encode(
            long traceId)
        {
            if (encodeSlot != NO_SLOT && encodeSlotOffset > 0)
            {
                final MutableDirectBufferEx slot = bufferPool.buffer(encodeSlot);
                final int replyWin = replyMax - (int) (replySeq - replyAck) - replyPad;
                final int length = Math.min(Math.max(replyWin, 0), encodeSlotOffset);
                if (length > 0)
                {
                    doData(sender, lifecycle.originId, lifecycle.routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, 0x03, 0L, length, slot, 0, length);
                    replySeq += length;
                    final int remaining = encodeSlotOffset - length;
                    if (remaining > 0)
                    {
                        slot.putBytes(0, slot, length, remaining);
                    }
                    encodeSlotOffset = remaining;
                }
                if (encodeSlotOffset == 0)
                {
                    cleanupEncodeSlot();
                }
            }
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                bufferPool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
            }
        }

        private void doEncodeBeginItem(
            long traceId)
        {
            if (itemsEmitted > 0)
            {
                separatorPending = true;
                separatorProgress = 0;
            }
            itemsEmitted++;
            doEncodeFraming(traceId);
        }

        private int doEncodeItemChunk(
            DirectBufferEx buffer,
            int offset,
            int length,
            long traceId)
        {
            int accepted = 0;
            if (doEncodeFraming(traceId))
            {
                accepted = doServerData(buffer, offset, length, traceId);
            }
            return accepted;
        }

        private void doEncodeEndItem(
            long traceId)
        {
        }

        private void doEncodeEndItems(
            long traceId)
        {
            endItemsPending = true;
            encodeEnd(traceId);
        }

        private void encodeEnd(
            long traceId)
        {
            if (doEncodeFraming(traceId))
            {
                final DirectBufferEx postlude = listReplyCloseRO;
                if (postludeProgress < postlude.capacity())
                {
                    postludeProgress += doServerData(postlude, postludeProgress,
                        postlude.capacity() - postludeProgress, traceId);
                }
                if (postludeProgress == postlude.capacity() && encodeSlot == NO_SLOT)
                {
                    doServerEnd(traceId);
                }
            }
        }

        private void doServerBegin(
            long traceId)
        {
            final String sid = lifecycle.sessionId;
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .inject(b -> injectReplyBeginEx(b, sid))
                .build();

            doBegin(sender, lifecycle.originId, lifecycle.routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, beginEx);
            state = McpState.openedReply(state);
        }

        private void doServerEnd(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doEnd(sender, lifecycle.originId, lifecycle.routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
                state = McpState.closedReply(state);
                if (McpState.closed(state))
                {
                    onClientClosed(traceId);
                }
            }
        }

        private void doServerAbort(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doAbort(sender, lifecycle.originId, lifecycle.routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
                state = McpState.closedReply(state);
                cleanupEncodeSlot();
                if (McpState.closed(state))
                {
                    onClientClosed(traceId);
                }
            }
        }

        private void doServerWindow(
            long traceId,
            long budgetId,
            int padding)
        {
            state = McpState.openedInitial(state);
            doWindow(sender, lifecycle.originId, lifecycle.routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding);
        }

        private void flushServerWindow(
            long traceId,
            long budgetId,
            int padding,
            long minInitialNoAck,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(initialAck, initialSeq - minInitialNoAck);
            final int newInitialMax = Math.max(initialMax, minInitialMax);

            if (newInitialAck > initialAck || newInitialMax > initialMax || !McpState.initialOpened(state))
            {
                initialAck = newInitialAck;
                initialMax = newInitialMax;
                doServerWindow(traceId, budgetId, padding);
            }
        }
    }

    private final class McpCacheListServer
    {
        private final McpLifecycleServer lifecycle;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;
        private final McpProxyCache.McpListCache cache;
        private final McpBindingConfig binding;

        private int state;
        private boolean fetched;
        private DirectBufferEx cachedBuf;
        private int cachedLen;
        private int emitOffset;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private McpCacheListServer(
            McpLifecycleServer lifecycle,
            long initialId,
            long affinity,
            long authorization,
            McpProxyCache.McpListCache cache,
            McpBindingConfig binding)
        {
            this.lifecycle = lifecycle;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.cache = cache;
            this.binding = binding;
        }

        private void onServerMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onServerBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onServerEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onServerAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                onServerWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onServerReset(resetRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onServerBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            state = McpState.openingInitial(state);

            doServerBegin(traceId);
            doServerWindow(traceId, 0L, 0);
            cache.get(this::onStoreResult);
        }

        private void onStoreResult(
            String key,
            String value)
        {
            fetched = true;
            if (value != null)
            {
                final byte[] bytes = filterByScopes(value);
                cachedBuf = new UnsafeBufferEx(bytes);
                cachedLen = bytes.length;
            }
            emitIfReady(supplyTraceId.getAsLong());
        }

        private byte[] filterByScopes(
            String json)
        {
            final Map<CharSequence, List<String>> scopesByName = cache.scopesByName();
            final byte[] src = json.getBytes(StandardCharsets.UTF_8);

            byte[] output = src;
            if (binding.filterGuard != null && !scopesByName.isEmpty())
            {
                scopeAdmitter.guard = binding.filterGuard;
                scopeAdmitter.authorization = authorization;
                scopeFilter.init(arrayKey(), scopesByName, scopeAdmitter);

                // filtering only drops items, so the target never exceeds the source (plus structural slack)
                final int capacity = src.length + 16;
                if (scopeTargetArray.length < capacity)
                {
                    scopeTargetArray = new byte[capacity];
                }
                scopeSourceBuffer.wrap(src);
                scopeTargetBuffer.wrap(scopeTargetArray);
                int produced = 0;

                scopePipeline.reset();
                JsonPipelineResult result =
                    scopePipeline.transform(scopeSourceBuffer, 0, src.length, true, scopeTargetBuffer, 0, capacity);
                produced += result.produced();

                while (result.status() == JsonPipeline.Status.SUSPENDED)
                {
                    result = scopePipeline.transform(
                        scopeSourceBuffer, 0, src.length, true, scopeTargetBuffer, produced, capacity);
                    produced += result.produced();
                }

                output = new byte[produced];
                scopeTargetBuffer.getBytes(0, output);
            }
            return output;
        }

        private void onServerEnd(
            EndFW end)
        {
            initialSeq = end.sequence();
            state = McpState.closedInitial(state);
            emitIfReady(end.traceId());
        }

        private void onServerAbort(
            AbortFW abort)
        {
            initialSeq = abort.sequence();
            state = McpState.closedInitial(state);
            doServerAbort(abort.traceId());
        }

        private void onServerWindow(
            WindowFW window)
        {
            replyAck = window.acknowledge();
            replyMax = window.maximum();
            replyPad = window.padding();
            state = McpState.openedReply(state);
            emitIfReady(window.traceId());
        }

        private void onServerReset(
            ResetFW reset)
        {
            replyAck = reset.acknowledge();
            state = McpState.closedReply(state);
        }

        private void emitIfReady(
            long traceId)
        {
            if (!fetched || McpState.replyClosed(state))
            {
                return;
            }

            if (cachedBuf == null)
            {
                if (!cache.degraded())
                {
                    doServerAbort(traceId);
                    return;
                }

                final DirectBufferEx prelude = listReplyOpenPrelude();
                final byte[] empty = new byte[prelude.capacity() + listReplyCloseRO.capacity()];
                prelude.getBytes(0, empty, 0, prelude.capacity());
                listReplyCloseRO.getBytes(0, empty, prelude.capacity(), listReplyCloseRO.capacity());
                cachedBuf = new UnsafeBufferEx(empty);
                cachedLen = empty.length;
            }

            while (emitOffset < cachedLen)
            {
                final int replyWin = replyMax - (int) (replySeq - replyAck) - replyPad;
                if (replyWin <= 0)
                {
                    return;
                }
                final int chunkLen = Math.min(replyWin, cachedLen - emitOffset);
                doServerData(traceId, 0L, 0x03, chunkLen, cachedBuf, emitOffset, chunkLen);
                emitOffset += chunkLen;
            }

            doServerEnd(traceId);
        }

        private void doServerBegin(
            long traceId)
        {
            final String sid = lifecycle.sessionId;
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .inject(b -> injectReplyBeginEx(b, sid))
                .build();

            doBegin(lifecycle.sender, lifecycle.originId, lifecycle.routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, beginEx);
            state = McpState.openingReply(state);
        }

        private void doServerData(
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            DirectBufferEx payload,
            int offset,
            int length)
        {
            doData(lifecycle.sender, lifecycle.originId, lifecycle.routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, flags, budgetId, reserved, payload, offset, length);
            replySeq += reserved;
        }

        private void doServerEnd(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doEnd(lifecycle.sender, lifecycle.originId, lifecycle.routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void doServerAbort(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doAbort(lifecycle.sender, lifecycle.originId, lifecycle.routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void doServerWindow(
            long traceId,
            long budgetId,
            int padding)
        {
            state = McpState.openedInitial(state);
            doWindow(lifecycle.sender, lifecycle.originId, lifecycle.routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding);
        }
    }

    private static int indexOfByte(
        DirectBufferEx buffer,
        int offset,
        int limit,
        byte value)
    {
        for (int cursor = offset; cursor < limit; cursor++)
        {
            if (buffer.getByte(cursor) == value)
            {
                return cursor;
            }
        }

        return -1;
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

    private void doBegin(
        MessageConsumer receiver,
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

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBufferEx payload,
        int offset,
        int length)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .flags(flags)
            .budgetId(budgetId)
            .reserved(reserved)
            .payload(payload, offset, length)
            .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
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
        long authorization,
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
