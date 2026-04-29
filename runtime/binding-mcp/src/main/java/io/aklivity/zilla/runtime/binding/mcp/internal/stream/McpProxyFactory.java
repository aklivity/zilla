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
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.LongUnaryOperator;

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRoutePrefix;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.json.DirectBufferInputStreamEx;
import io.aklivity.zilla.runtime.common.json.StreamingJson;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpProxyFactory implements McpStreamFactory
{
    private static final String MCP_TYPE_NAME = "mcp";

    private static final List<String> TOOLS_LIST_ITEM_JSON_PATH_INCLUDES = List.of("/tools/-/name");
    private static final List<String> PROMPTS_LIST_ITEM_JSON_PATH_INCLUDES = List.of("/prompts/-/name");
    private static final List<String> RESOURCES_LIST_ITEM_JSON_PATH_INCLUDES = List.of("/resources/-/uri");

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(), 0, 0);
    private final DirectBufferInputStreamEx inputRO = new DirectBufferInputStreamEx();
    private final DirectBuffer listReplyToolsOpenRO =
        new UnsafeBuffer("{\"tools\":[".getBytes(StandardCharsets.UTF_8));
    private final DirectBuffer listReplyPromptsOpenRO =
        new UnsafeBuffer("{\"prompts\":[".getBytes(StandardCharsets.UTF_8));
    private final DirectBuffer listReplyResourcesOpenRO =
        new UnsafeBuffer("{\"resources\":[".getBytes(StandardCharsets.UTF_8));
    private final DirectBuffer listReplyCloseRO =
        new UnsafeBuffer("]}".getBytes(StandardCharsets.UTF_8));
    private final DirectBuffer listReplySeparatorRO =
        new UnsafeBuffer(",".getBytes(StandardCharsets.UTF_8));

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer codecBuffer;
    private final BindingHandler streamFactory;
    private final BufferPool bufferPool;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int mcpTypeId;

    private final Long2ObjectHashMap<McpBindingConfig> bindings;
    private final Map<String, McpLifecycleServer> sessions;

    private final JsonParserFactory toolsListItemParserFactory;
    private final JsonParserFactory promptsListItemParserFactory;
    private final JsonParserFactory resourcesListItemParserFactory;

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
        this.bindings = new Long2ObjectHashMap<>();
        this.sessions = new Object2ObjectHashMap<>();
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.toolsListItemParserFactory = StreamingJson.createParserFactory(
            Map.of(StreamingJson.PATH_INCLUDES, TOOLS_LIST_ITEM_JSON_PATH_INCLUDES));
        this.promptsListItemParserFactory = StreamingJson.createParserFactory(
            Map.of(StreamingJson.PATH_INCLUDES, PROMPTS_LIST_ITEM_JSON_PATH_INCLUDES));
        this.resourcesListItemParserFactory = StreamingJson.createParserFactory(
            Map.of(StreamingJson.PATH_INCLUDES, RESOURCES_LIST_ITEM_JSON_PATH_INCLUDES));
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
        bindings.put(binding.id, newBinding);
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
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
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long affinity = begin.affinity();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();

        final McpBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        final McpBeginExFW beginEx = extension.get(mcpBeginExRO::tryWrap);

        if (binding != null && beginEx != null)
        {
            final int kind = beginEx.kind();
            final String sessionId = sessionId(beginEx);

            if (kind == KIND_LIFECYCLE)
            {
                final McpRouteConfig route = binding.resolve(beginEx, authorization);
                if (route != null)
                {
                    final int clientCapabilities = beginEx.lifecycle().capabilities();
                    final McpLifecycleServer lifecycle = new McpLifecycleServer(
                        sender, originId, routedId, initialId, affinity, authorization,
                        clientCapabilities, sessionId);
                    sessions.put(sessionId, lifecycle);
                    newStream = lifecycle::onServerMessage;
                }
            }
            else
            {
                final McpLifecycleServer lifecycle = sessions.get(sessionId);
                if (lifecycle != null)
                {
                    if (isListKind(kind))
                    {
                        final List<McpRoutePrefix> prefixes = binding.resolveAll(beginEx, authorization)
                            .stream()
                            .map(r -> new McpRoutePrefix(r.id, r.prefix(kind)))
                            .toList();
                        newStream = new McpListServer(
                            lifecycle,
                            kind,
                            initialId,
                            affinity,
                            authorization,
                            prefixes)::onServerMessage;
                    }
                    else
                    {
                        final McpRouteConfig route = binding.resolve(beginEx, authorization);
                        if (route != null)
                        {
                            final String identifier = route.strip(beginEx);
                            final String prefix = route.prefix(beginEx);

                            newStream = new McpServer(
                                lifecycle,
                                kind,
                                sender,
                                originId,
                                routedId,
                                initialId,
                                route.id,
                                affinity,
                                authorization,
                                identifier,
                                prefix)::onServerMessage;
                        }
                    }
                }
            }
        }

        return newStream;
    }

    private final class McpServer
    {
        private final McpLifecycleServer lifecycle;
        private final int kind;
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;
        private final String identifier;
        private final String prefix;
        private final McpClient client;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private McpServer(
            McpLifecycleServer lifecycle,
            int kind,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            String identifier,
            String prefix)
        {
            this.lifecycle = lifecycle;
            this.kind = kind;
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.identifier = identifier;
            this.prefix = prefix;
            this.client = new McpClient(this, resolvedId);
        }

        private String sessionId()
        {
            return lifecycle.sessionId;
        }

        private void onServerMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onServerBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onServerData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onServerEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onServerAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onServerFlush(flush);
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

            client.doClientBegin(traceId);

            flushServerWindow(traceId, 0L, 0, 0L, 0);
        }

        private void onServerData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            client.doClientData(traceId, budgetId, flags, reserved,
                payload.buffer(), payload.offset(), payload.sizeof());
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

            client.doClientEnd(traceId);
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

            client.doClientAbort(traceId);
        }

        private void onServerFlush(
            FlushFW flush)
        {
            // pass-through flush — no action required for proxy kind
        }

        private void onServerWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum + acknowledge >= replyMax + replyAck;

            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = padding;

            assert replyAck <= replySeq;

            client.flushClientWindow(traceId, budgetId, padding, replySeq - replyAck, replyMax);
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

            client.doClientReset(traceId);
        }

        private void doServerBegin(
            long traceId,
            Flyweight extension)
        {
            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                affinity, extension);
            state = McpState.openedReply(state);
        }

        private void doServerData(
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            DirectBuffer payload,
            int offset,
            int length)
        {
            doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                flags, budgetId, reserved, payload, offset, length);
            replySeq += reserved;
        }

        private void doServerEnd(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void doServerAbort(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void doServerWindow(
            long traceId,
            long budgetId,
            int padding)
        {
            state = McpState.openedInitial(state);
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                budgetId, padding);
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

        private void doServerReset(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                    emptyRO);
                state = McpState.closedInitial(state);
            }
        }
    }

    private final class McpClient
    {
        private final McpServer server;
        private final long resolvedId;
        private final McpLifecycleClient lifecycle;

        private final long initialId;
        private final long replyId;

        private MessageConsumer sender;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private McpClient(
            McpServer server,
            long resolvedId)
        {
            this.server = server;
            this.resolvedId = resolvedId;
            this.lifecycle = server.lifecycle.supplyClient(resolvedId);
            this.initialId = supplyInitialId.applyAsLong(resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doClientBegin(
            long traceId)
        {
            lifecycle.doClientBegin(traceId);

            final String identifier = server.identifier;
            final String upstreamSessionId = lifecycle.sessionId;
            final String outboundSessionId = upstreamSessionId != null
                ? upstreamSessionId
                : server.sessionId();
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .inject(b ->
                {
                    switch (server.kind)
                    {
                    case KIND_TOOLS_CALL -> b.toolsCall(t -> t.sessionId(outboundSessionId).name(identifier));
                    case KIND_PROMPTS_GET -> b.promptsGet(p -> p.sessionId(outboundSessionId).name(identifier));
                    case KIND_RESOURCES_READ -> b.resourcesRead(r -> r.sessionId(outboundSessionId).uri(identifier));
                    default -> throw new IllegalStateException("unexpected McpBeginEx kind: " + server.kind);
                    }
                })
                .build();

            sender = newStream(this::onClientMessage, server.lifecycle.originId, resolvedId, initialId,
                initialSeq, initialAck, initialMax, traceId, server.authorization, server.affinity, beginEx);
            state = McpState.openingInitial(state);
        }

        private void doClientData(
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            DirectBuffer payload,
            int offset,
            int length)
        {
            if (!McpState.closed(state))
            {
                doData(sender, server.lifecycle.originId, resolvedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, server.authorization,
                    flags, budgetId, reserved, payload, offset, length);
                initialSeq += reserved;
            }
        }

        private void doClientEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doEnd(sender, server.lifecycle.originId, resolvedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, server.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doAbort(sender, server.lifecycle.originId, resolvedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, server.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientWindow(
            long traceId,
            long budgetId,
            int padding)
        {
            state = McpState.openedReply(state);
            doWindow(sender, server.lifecycle.originId, resolvedId, replyId,
                replySeq, replyAck, replyMax, traceId, server.authorization, budgetId, padding);
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

        private void doClientReset(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doReset(sender, server.lifecycle.originId, resolvedId, replyId,
                    replySeq, replyAck, replyMax, traceId, server.authorization, emptyRO);
                state = McpState.closedReply(state);
            }
        }

        private void onClientMessage(
            int msgTypeId,
            DirectBuffer buffer,
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
            final OctetsFW extension = begin.extension();

            replySeq = sequence;
            replyAck = acknowledge;

            state = McpState.openedInitial(state);

            final McpBeginExFW beginEx = extension.get(mcpBeginExRO::tryWrap);
            final Flyweight replyExtension = beginEx != null
                ? rewriteReplyBeginEx(beginEx)
                : emptyRO;

            server.doServerBegin(traceId, replyExtension);

            flushClientWindow(traceId, 0L, 0, 0L, 0);
        }

        private Flyweight rewriteReplyBeginEx(
            McpBeginExFW beginEx)
        {
            final int kind = beginEx.kind();
            if (kind != KIND_TOOLS_CALL && kind != KIND_PROMPTS_GET && kind != KIND_RESOURCES_READ)
            {
                return beginEx;
            }

            final String sid = server.sessionId();
            return mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .inject(b ->
                {
                    switch (kind)
                    {
                    case KIND_TOOLS_CALL ->
                        b.toolsCall(t -> t.sessionId(sid).name(beginEx.toolsCall().name().asString()));
                    case KIND_PROMPTS_GET ->
                        b.promptsGet(p -> p.sessionId(sid).name(beginEx.promptsGet().name().asString()));
                    case KIND_RESOURCES_READ ->
                        b.resourcesRead(r -> r.sessionId(sid).uri(beginEx.resourcesRead().uri().asString()));
                    default -> throw new IllegalStateException("unexpected McpBeginEx kind: " + kind);
                    }
                })
                .build();
        }

        private void onClientData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            server.doServerData(traceId, budgetId, flags, reserved,
                payload.buffer(), payload.offset(), payload.sizeof());
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
            server.doServerEnd(traceId);
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
            server.doServerAbort(traceId);
        }

        private void onClientWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
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

            server.doServerReset(traceId);
        }
    }

    private final class McpLifecycleServer
    {
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;
        private final int clientCapabilities;
        private final String sessionId;
        private final Long2ObjectHashMap<McpLifecycleClient> clients;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private McpLifecycleServer(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization,
            int clientCapabilities,
            String sessionId)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.clientCapabilities = clientCapabilities;
            this.sessionId = sessionId;
            this.clients = new Long2ObjectHashMap<>();
        }

        private McpLifecycleClient supplyClient(
            long routedId)
        {
            return clients.computeIfAbsent(routedId, id -> new McpLifecycleClient(this, id));
        }

        private void onServerMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onServerBegin(begin);
                break;
            case DataFW.TYPE_ID:
                // no-op: proxy terminates lifecycle locally, no DATA is forwarded
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

            final McpBindingConfig binding = bindings.get(routedId);
            final int serverCapabilities = binding.serverCapabilities(authorization);
            final String sid = sessionId;
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .lifecycle(l -> l.sessionId(sid).capabilities(serverCapabilities))
                .build();

            doServerBegin(traceId, beginEx);

            doServerWindow(traceId, 0L, 0);
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

            cleanup(traceId);

            doServerEnd(traceId);
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

            cleanup(traceId);

            doServerAbort(traceId);
        }

        private void onServerWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
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

            cleanup(traceId);
        }

        private void doServerBegin(
            long traceId,
            Flyweight extension)
        {
            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                affinity, extension);
            state = McpState.openedReply(state);
        }

        private void doServerEnd(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void doServerAbort(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void doServerWindow(
            long traceId,
            long budgetId,
            int padding)
        {
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                budgetId, padding);
        }

        private void cleanup(
            long traceId)
        {
            sessions.remove(sessionId);

            for (McpLifecycleClient upstream : clients.values())
            {
                upstream.doClientEnd(traceId);
            }
        }
    }

    private final class McpLifecycleClient
    {
        private final McpLifecycleServer server;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private MessageConsumer sender;
        private int state;
        private String sessionId;        // upstream-provided session id, set on BEGIN reply

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private McpLifecycleClient(
            McpLifecycleServer server,
            long routedId)
        {
            this.server = server;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doClientBegin(
            long traceId)
        {
            if (!McpState.initialOpening(state))
            {
                final long originId = server.routedId;
                final String sid = server.sessionId;
                final int clientCapabilities = server.clientCapabilities;
                final McpBeginExFW beginEx = mcpBeginExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(mcpTypeId)
                    .lifecycle(l -> l.sessionId(sid).capabilities(clientCapabilities))
                    .build();

                sender = newStream(this::onClientMessage, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, server.authorization, server.affinity, beginEx);
                state = McpState.openingInitial(state);
            }
        }

        private void doClientEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                final long originId = server.routedId;
                doEnd(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
                    server.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                final long originId = server.routedId;
                doAbort(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
                    server.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientReset(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                final long originId = server.routedId;
                doReset(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId,
                    server.authorization, emptyRO);
                state = McpState.closedReply(state);
            }
        }

        private void doClientWindow(
            long traceId,
            long budgetId,
            int padding)
        {
            final long originId = server.routedId;
            doWindow(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, server.authorization,
                budgetId, padding);
        }

        private void onClientMessage(
            int msgTypeId,
            DirectBuffer buffer,
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
                // lifecycle does not carry DATA in this proxy model
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
            final OctetsFW extension = begin.extension();

            replySeq = sequence;
            replyAck = acknowledge;

            state = McpState.openedInitial(state);

            final McpBeginExFW beginEx = extension.get(mcpBeginExRO::tryWrap);
            if (beginEx != null && beginEx.kind() == KIND_LIFECYCLE)
            {
                sessionId = beginEx.lifecycle().sessionId().asString();
            }

            doClientWindow(traceId, 0L, 0);

            state = McpState.openedReply(state);
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
            doClientEnd(traceId);
            server.clients.remove(routedId, this);
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
            doClientAbort(traceId);
            server.clients.remove(routedId, this);
        }

        private void onClientWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
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
            server.clients.remove(routedId, this);
        }
    }

    private final class McpListClient
    {
        private final McpListServer server;
        private final long resolvedId;
        private final String prefix;
        private final byte[] prefixBytes;
        private final DirectBuffer prefixBufferRO;
        private final McpLifecycleClient lifecycle;
        private final long initialId;
        private final long replyId;

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

        private JsonParser decodableJson;
        private long decodedParserProgress;       // absolute streamOffset of buffer[offset] passed to decode
        private int decodeDepth;                  // JSON nesting depth in the reply envelope
        private int decodeItemDepth;              // JSON nesting depth within the current item
        private int decodeSkipDepth;              // JSON nesting depth within a skipped value
        private long itemStartStreamOffset = -1;
        private long itemEmittedStreamOffset = -1;
        private McpListClientDecoder decoder = decodeInit;
        private String arrayKey;
        private String idKey;

        private McpListClient(
            McpListServer server,
            long resolvedId,
            String prefix)
        {
            this.server = server;
            this.resolvedId = resolvedId;
            this.prefix = prefix;
            this.prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
            this.prefixBufferRO = new UnsafeBuffer(prefixBytes);
            this.lifecycle = server.lifecycle.supplyClient(resolvedId);
            this.initialId = supplyInitialId.applyAsLong(resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doClientBegin(
            long traceId)
        {
            lifecycle.doClientBegin(traceId);

            final String upstreamSessionId = lifecycle.sessionId;
            final String sid = upstreamSessionId != null ? upstreamSessionId : server.lifecycle.sessionId;
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .inject(b ->
                {
                    switch (server.kind)
                    {
                    case KIND_TOOLS_LIST -> b.toolsList(t -> t.sessionId(sid));
                    case KIND_PROMPTS_LIST -> b.promptsList(p -> p.sessionId(sid));
                    case KIND_RESOURCES_LIST -> b.resourcesList(r -> r.sessionId(sid));
                    default -> throw new IllegalStateException("unexpected list kind: " + server.kind);
                    }
                })
                .build();

            sender = newStream(this::onClientMessage, server.lifecycle.originId, resolvedId, initialId,
                initialSeq, initialAck, initialMax, traceId, server.authorization, server.affinity, beginEx);
            state = McpState.openingInitial(state);
        }

        private void doClientEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doEnd(sender, server.lifecycle.originId, resolvedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, server.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doAbort(sender, server.lifecycle.originId, resolvedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, server.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientReset(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doReset(sender, server.lifecycle.originId, resolvedId, replyId,
                    replySeq, replyAck, replyMax, traceId, server.authorization, emptyRO);
                state = McpState.closedReply(state);
            }
        }

        private void doClientWindow(
            long traceId,
            long budgetId,
            int padding)
        {
            state = McpState.openedReply(state);
            doWindow(sender, server.lifecycle.originId, resolvedId, replyId,
                replySeq, replyAck, replyMax, traceId, server.authorization, budgetId, padding);
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
            DirectBuffer buffer,
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

            flushClientWindow(traceId, 0L, 0, 0L, 0);
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

            DirectBuffer buffer = payload.buffer();
            int offset = payload.offset();
            int limit = payload.limit();

            if (replySlot != NO_SLOT)
            {
                final MutableDirectBuffer slot = bufferPool.buffer(replySlot);
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

            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                cleanupClientSlot();
                server.onClientClosed(traceId);
            }
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

            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                cleanupClientSlot();
                server.onClientError(traceId);
            }
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
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                server.onClientError(traceId);
            }
        }

        private void decode(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            if (decodableJson != null)
            {
                final int delta = (int) (decodableJson.getLocation().getStreamOffset() - decodedParserProgress);
                inputRO.wrap(buffer, offset + delta, limit - offset - delta);
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
            if (itemStartStreamOffset >= 0)
            {
                compactBoundaryInBuf = offset + (int) (itemEmittedStreamOffset - decodedParserProgress);
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
                final MutableDirectBuffer slot = bufferPool.buffer(replySlot);
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
                final MutableDirectBuffer slot = bufferPool.buffer(replySlot);
                decode(traceId, server.authorization, 0L, 0, slot, 0, replySlotOffset);
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
            DirectBuffer buffer,
            int offset,
            int progress,
            int limit);
    }

    private final McpListClientDecoder decodeInit = this::decodeInit;
    private final McpListClientDecoder decodeReply = this::decodeReply;
    private final McpListClientDecoder decodeItemsKey = this::decodeItemsKey;
    private final McpListClientDecoder decodeSkipObject = this::decodeSkipObject;
    private final McpListClientDecoder decodeItems = this::decodeItems;
    private final McpListClientDecoder decodeItemStart = this::decodeItemStart;
    private final McpListClientDecoder decodeItemBody = this::decodeItemBody;
    private final McpListClientDecoder decodeItemId = this::decodeItemId;
    private final McpListClientDecoder decodeItemFinalize = this::decodeItemFinalize;
    private final McpListClientDecoder decodeIgnore = this::decodeIgnore;

    private int decodeInit(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserFactory parserFactory = switch (client.server.kind)
        {
        case KIND_TOOLS_LIST -> toolsListItemParserFactory;
        case KIND_PROMPTS_LIST -> promptsListItemParserFactory;
        case KIND_RESOURCES_LIST -> resourcesListItemParserFactory;
        default -> null;
        };

        if (parserFactory == null)
        {
            client.decoder = decodeIgnore;
            return limit;
        }

        inputRO.wrap(buffer, progress, limit - progress);
        client.decodableJson = parserFactory.createParser(inputRO);
        client.arrayKey = switch (client.server.kind)
        {
        case KIND_TOOLS_LIST -> "tools";
        case KIND_PROMPTS_LIST -> "prompts";
        case KIND_RESOURCES_LIST -> "resources";
        default -> null;
        };
        client.idKey = client.server.kind == KIND_RESOURCES_LIST ? "uri" : "name";
        client.decoder = decodeReply;

        return progress;
    }

    private int decodeReply(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
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
        DirectBuffer buffer,
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
        DirectBuffer buffer,
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
        DirectBuffer buffer,
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
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;

        decode:
        while (parser.hasNext())
        {
            final long afterStreamOffset = parser.getLocation().getStreamOffset();
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
                client.itemStartStreamOffset = afterStreamOffset - 1;
                client.itemEmittedStreamOffset = client.itemStartStreamOffset;
                client.server.streamItemBegin(traceId);
                client.decodeItemDepth = 1;
                client.decoder = decodeItemBody;
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

    private int decodeItemBody(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;

        decode:
        while (true)
        {
            final long parserStreamOffset = parser.getLocation().getStreamOffset();
            if (client.itemEmittedStreamOffset < parserStreamOffset)
            {
                final int emittedInBuf =
                    offset + (int) (client.itemEmittedStreamOffset - client.decodedParserProgress);
                final int parserInBuf = offset + (int) (parserStreamOffset - client.decodedParserProgress);
                final int chunkLen = parserInBuf - emittedInBuf;
                final int emitted = client.server.streamItemChunk(buffer, emittedInBuf, chunkLen, traceId);
                client.itemEmittedStreamOffset += emitted;
                if (emitted < chunkLen)
                {
                    break decode;
                }
            }

            if (!parser.hasNext())
            {
                break decode;
            }
            final long afterStreamOffset = parser.getLocation().getStreamOffset();
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
                    final int afterInBuf = offset + (int) (afterStreamOffset - client.decodedParserProgress);
                    final int emittedInBuf =
                        offset + (int) (client.itemEmittedStreamOffset - client.decodedParserProgress);
                    final int chunkLen = afterInBuf - emittedInBuf;
                    final int emitted = client.server.streamItemChunk(buffer, emittedInBuf, chunkLen, traceId);
                    client.itemEmittedStreamOffset += emitted;
                    if (emitted < chunkLen)
                    {
                        client.decoder = decodeItemFinalize;
                        break decode;
                    }
                    client.server.streamItemEnd(traceId);
                    client.itemStartStreamOffset = -1;
                    client.itemEmittedStreamOffset = -1;
                    client.decoder = decodeItemStart;
                    break decode;
                }
                break;
            case END_ARRAY:
                client.decodeItemDepth--;
                break;
            case KEY_NAME:
                if (client.decodeItemDepth == 1 &&
                    client.prefixBytes.length > 0 &&
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
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;

        final long parserStreamOffset = parser.getLocation().getStreamOffset();
        if (client.itemEmittedStreamOffset < parserStreamOffset)
        {
            final int emittedInBuf =
                offset + (int) (client.itemEmittedStreamOffset - client.decodedParserProgress);
            final int parserInBuf = offset + (int) (parserStreamOffset - client.decodedParserProgress);
            final int chunkLen = parserInBuf - emittedInBuf;
            final int emitted = client.server.streamItemChunk(buffer, emittedInBuf, chunkLen, traceId);
            client.itemEmittedStreamOffset += emitted;
            if (emitted < chunkLen)
            {
                return offset + (int) (client.itemEmittedStreamOffset - client.decodedParserProgress);
            }
        }

        final long beforeStreamOffset = parser.getLocation().getStreamOffset();
        if (parser.hasNext())
        {
            final long afterStreamOffset = parser.getLocation().getStreamOffset();
            final JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.VALUE_STRING)
            {
                final int beforeInBuf = offset + (int) (beforeStreamOffset - client.decodedParserProgress);
                final int afterInBuf = offset + (int) (afterStreamOffset - client.decodedParserProgress);
                int openingQuote = beforeInBuf;
                while (openingQuote < afterInBuf && buffer.getByte(openingQuote) != (byte) '"')
                {
                    openingQuote++;
                }
                final int contentStart = openingQuote + 1;
                final int emittedInBuf =
                    offset + (int) (client.itemEmittedStreamOffset - client.decodedParserProgress);
                client.server.streamItemChunk(buffer, emittedInBuf, contentStart - emittedInBuf, traceId);
                client.server.streamItemChunk(client.prefixBufferRO, 0, client.prefixBytes.length, traceId);
                client.itemEmittedStreamOffset =
                    client.decodedParserProgress + (long) (contentStart - offset);
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
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = client.decodableJson;
        final long parserStreamOffset = parser.getLocation().getStreamOffset();

        if (client.itemEmittedStreamOffset < parserStreamOffset)
        {
            final int emittedInBuf =
                offset + (int) (client.itemEmittedStreamOffset - client.decodedParserProgress);
            final int parserInBuf = offset + (int) (parserStreamOffset - client.decodedParserProgress);
            final int chunkLen = parserInBuf - emittedInBuf;
            final int emitted = client.server.streamItemChunk(buffer, emittedInBuf, chunkLen, traceId);
            client.itemEmittedStreamOffset += emitted;
            if (emitted < chunkLen)
            {
                return offset + (int) (client.itemEmittedStreamOffset - client.decodedParserProgress);
            }
        }

        client.server.streamItemEnd(traceId);
        client.itemStartStreamOffset = -1;
        client.itemEmittedStreamOffset = -1;
        client.decoder = decodeItemStart;

        return offset + (int) (parserStreamOffset - client.decodedParserProgress);
    }

    private int decodeIgnore(
        McpListClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        return limit;
    }

    private final class McpListServer
    {
        private final McpLifecycleServer lifecycle;
        private final int kind;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;
        private final Deque<McpRoutePrefix> remaining;

        private int state;
        private int itemsEmitted;
        private McpListClient client;

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
            int kind,
            long initialId,
            long affinity,
            long authorization,
            List<McpRoutePrefix> prefixes)
        {
            this.lifecycle = lifecycle;
            this.kind = kind;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.remaining = new ArrayDeque<>(prefixes);
        }

        private void onServerMessage(
            int msgTypeId,
            DirectBuffer buffer,
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
            doEncodeBeginItems(traceId);
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

            if (client != null)
            {
                client.doClientReset(traceId);
            }
            remaining.clear();
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
            client = null;
            remaining.clear();
            doServerAbort(traceId);
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
            client = new McpListClient(this, route.resolvedId(), route.prefix());
            client.doClientBegin(traceId);
            if (McpState.initialClosed(state))
            {
                client.doClientEnd(traceId);
            }
        }

        private void streamItemBegin(
            long traceId)
        {
            if (itemsEmitted > 0)
            {
                doServerData(traceId, 0L, 0x03, listReplySeparatorRO.capacity(),
                    listReplySeparatorRO, 0, listReplySeparatorRO.capacity());
            }
            itemsEmitted++;
        }

        private int streamItemChunk(
            DirectBuffer buffer,
            int offset,
            int length,
            long traceId)
        {
            final int replyWin = replyMax - (int) (replySeq - replyAck) - replyPad;
            final int emit = Math.min(Math.max(replyWin, 0), length);
            if (emit > 0)
            {
                doServerData(traceId, 0L, 0x03, emit, buffer, offset, emit);
            }
            return emit;
        }

        private void streamItemEnd(
            long traceId)
        {
        }

        private void doEncodeBeginItems(
            long traceId)
        {
            final DirectBuffer prelude = switch (kind)
            {
            case KIND_TOOLS_LIST -> listReplyToolsOpenRO;
            case KIND_PROMPTS_LIST -> listReplyPromptsOpenRO;
            case KIND_RESOURCES_LIST -> listReplyResourcesOpenRO;
            default -> throw new IllegalStateException("unexpected list kind: " + kind);
            };
            doServerData(traceId, 0L, 0x03, prelude.capacity(), prelude, 0, prelude.capacity());
        }

        private void doEncodeEndItems(
            long traceId)
        {
            doServerData(traceId, 0L, 0x03, listReplyCloseRO.capacity(),
                listReplyCloseRO, 0, listReplyCloseRO.capacity());
            doServerEnd(traceId);
        }

        private void doServerBegin(
            long traceId)
        {
            final String sid = lifecycle.sessionId;
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .inject(b ->
                {
                    switch (kind)
                    {
                    case KIND_TOOLS_LIST -> b.toolsList(t -> t.sessionId(sid));
                    case KIND_PROMPTS_LIST -> b.promptsList(p -> p.sessionId(sid));
                    case KIND_RESOURCES_LIST -> b.resourcesList(r -> r.sessionId(sid));
                    default -> throw new IllegalStateException("unexpected list kind: " + kind);
                    }
                })
                .build();

            doBegin(lifecycle.sender, lifecycle.originId, lifecycle.routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, beginEx);
            state = McpState.openedReply(state);
        }

        private void doServerData(
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            DirectBuffer payload,
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

    private static boolean isListKind(
        int kind)
    {
        return kind == KIND_TOOLS_LIST || kind == KIND_PROMPTS_LIST || kind == KIND_RESOURCES_LIST;
    }

    private static String sessionId(
        McpBeginExFW beginEx)
    {
        return switch (beginEx.kind())
        {
        case KIND_LIFECYCLE -> beginEx.lifecycle().sessionId().asString();
        case KIND_TOOLS_LIST -> beginEx.toolsList().sessionId().asString();
        case KIND_TOOLS_CALL -> beginEx.toolsCall().sessionId().asString();
        case KIND_PROMPTS_LIST -> beginEx.promptsList().sessionId().asString();
        case KIND_PROMPTS_GET -> beginEx.promptsGet().sessionId().asString();
        case KIND_RESOURCES_LIST -> beginEx.resourcesList().sessionId().asString();
        case KIND_RESOURCES_READ -> beginEx.resourcesRead().sessionId().asString();
        default -> null;
        };
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
        DirectBuffer payload,
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

    private void doFlush(
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
        int reserved)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .reserved(reserved)
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
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
