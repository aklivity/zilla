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

import java.io.ByteArrayOutputStream;
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

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(), 0, 0);
    private final UnsafeBuffer itemRO = new UnsafeBuffer(0, 0);
    private final DirectBufferInputStreamEx inputRO = new DirectBufferInputStreamEx();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
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

        if (binding != null && extension.sizeof() > 0)
        {
            final McpBeginExFW beginEx = mcpBeginExRO.wrap(
                extension.buffer(), extension.offset(), extension.limit());
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
            final long traceId = begin.traceId();

            state = McpState.openingInitial(state);

            client.doClientBegin(traceId);

            doWindow(sender, originId, routedId, initialId, traceId, authorization, 0,
                writeBuffer.capacity(), 0);
        }

        private void onServerData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            client.doClientData(traceId, budgetId, flags, reserved,
                payload.buffer(), payload.offset(), payload.sizeof());
        }

        private void onServerEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = McpState.closedInitial(state);

            client.doClientEnd(traceId);
        }

        private void onServerAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

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
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int credit = window.maximum();
            final int padding = window.padding();

            client.doClientWindow(traceId, budgetId, credit, padding);
        }

        private void onServerReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpState.closedReply(state);

            client.doClientReset(traceId);
        }

        private void doServerBegin(
            long traceId,
            Flyweight extension)
        {
            doBegin(sender, originId, routedId, replyId, traceId, authorization, affinity, extension);
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
            doData(sender, originId, routedId, replyId, traceId, authorization,
                budgetId, flags, reserved, payload, offset, length);
        }

        private void doServerEnd(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void doServerAbort(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void doServerWindow(
            long traceId,
            long budgetId,
            int credit,
            int padding)
        {
            doWindow(sender, originId, routedId, initialId, traceId, authorization, budgetId, credit, padding);
        }

        private void doServerReset(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doReset(sender, originId, routedId, initialId, traceId, authorization);
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
                traceId, server.authorization, server.affinity, beginEx);
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
                    traceId, server.authorization, budgetId, flags, reserved, payload, offset, length);
            }
        }

        private void doClientEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doEnd(sender, server.lifecycle.originId, resolvedId, initialId,
                    traceId, server.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doAbort(sender, server.lifecycle.originId, resolvedId, initialId,
                    traceId, server.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientWindow(
            long traceId,
            long budgetId,
            int credit,
            int padding)
        {
            doWindow(sender, server.lifecycle.originId, resolvedId, replyId,
                traceId, server.authorization, budgetId, credit, padding);
        }

        private void doClientReset(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doReset(sender, server.lifecycle.originId, resolvedId, replyId,
                    traceId, server.authorization);
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
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            state = McpState.openedInitial(state);

            final Flyweight replyExtension = extension.sizeof() > 0
                ? rewriteReplyBeginEx(mcpBeginExRO.wrap(
                    extension.buffer(), extension.offset(), extension.limit()))
                : emptyRO;

            server.doServerBegin(traceId, replyExtension);
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
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            server.doServerData(traceId, budgetId, flags, reserved,
                payload.buffer(), payload.offset(), payload.sizeof());
        }

        private void onClientEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            state = McpState.closedReply(state);
            server.doServerEnd(traceId);
        }

        private void onClientAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            state = McpState.closedReply(state);
            server.doServerAbort(traceId);
        }

        private void onClientWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int credit = window.maximum();
            final int padding = window.padding();

            server.doServerWindow(traceId, budgetId, credit, padding);
        }

        private void onClientReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

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
                // reply direction window from upstream; no DATA to send so ignore
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
            final long traceId = begin.traceId();

            state = McpState.openingInitial(state);

            final McpBindingConfig binding = bindings.get(routedId);
            final int serverCapabilities = binding.serverCapabilities(authorization);
            final String sid = sessionId;
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .lifecycle(l -> l.sessionId(sid).capabilities(serverCapabilities))
                .build();

            doBegin(sender, originId, routedId, replyId, traceId, authorization, affinity, beginEx);
            state = McpState.openedReply(state);

            doWindow(sender, originId, routedId, initialId, traceId, authorization, 0,
                writeBuffer.capacity(), 0);
        }

        private void onServerEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = McpState.closedInitial(state);

            cleanup(traceId);

            if (!McpState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void onServerAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = McpState.closedInitial(state);

            cleanup(traceId);

            if (!McpState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void onServerReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpState.closedReply(state);

            cleanup(traceId);
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
                    traceId, server.authorization, server.affinity, beginEx);
                state = McpState.openingInitial(state);
            }
        }

        private void doClientEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                final long originId = server.routedId;
                doEnd(sender, originId, routedId, initialId, traceId, server.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                final long originId = server.routedId;
                doAbort(sender, originId, routedId, initialId, traceId, server.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientReset(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                final long originId = server.routedId;
                doReset(sender, originId, routedId, replyId, traceId, server.authorization);
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
                // we do not send DATA on this stream; nothing to do
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
            final long originId = server.routedId;
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            state = McpState.openedInitial(state);

            if (extension.sizeof() > 0)
            {
                final McpBeginExFW beginEx = mcpBeginExRO.wrap(
                    extension.buffer(), extension.offset(), extension.limit());
                if (beginEx.kind() == KIND_LIFECYCLE)
                {
                    sessionId = beginEx.lifecycle().sessionId().asString();
                }
            }

            doWindow(sender, originId, routedId, replyId, traceId, server.authorization, 0,
                writeBuffer.capacity(), 0);

            state = McpState.openedReply(state);
        }

        private void onClientEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            state = McpState.closedReply(state);
            doClientEnd(traceId);
            server.clients.remove(routedId, this);
        }

        private void onClientAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            state = McpState.closedReply(state);
            doClientAbort(traceId);
            server.clients.remove(routedId, this);
        }

        private void onClientReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
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
        private final McpLifecycleClient lifecycle;
        private final long initialId;
        private final long replyId;

        private MessageConsumer sender;
        private int state;
        private int replySlot = NO_SLOT;
        private int replySlotOffset;

        // streaming JSON state — built lazily on first DATA frame
        private JsonParser decodableJson;
        private ByteArrayOutputStream itemOut;
        private byte[] itemTransferBuffer;
        private long parserBaseOffset;            // absolute streamOffset of buffer[offset] passed to decode
        private int decodeDepth;                  // JSON nesting depth in the reply envelope
        private int decodeItemDepth;              // JSON nesting depth within the current item
        private int decodeSkipDepth;              // JSON nesting depth within a skipped value
        private boolean awaitingIdValue;
        private long itemStartStreamOffset = -1;
        private long itemLastEmittedStreamOffset = -1;
        private McpListClientDecoder decoder;
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
                traceId, server.authorization, server.affinity, beginEx);
            state = McpState.openingInitial(state);
        }

        private void doClientEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doEnd(sender, server.lifecycle.originId, resolvedId, initialId,
                    traceId, server.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doAbort(sender, server.lifecycle.originId, resolvedId, initialId,
                    traceId, server.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientReset(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doReset(sender, server.lifecycle.originId, resolvedId, replyId,
                    traceId, server.authorization);
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
            final long traceId = begin.traceId();
            state = McpState.openedInitial(state);
            doWindow(sender, server.lifecycle.originId, resolvedId, replyId, traceId,
                server.authorization, 0, writeBuffer.capacity(), 0);
        }

        private void onClientData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

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
            final long traceId = end.traceId();
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
            final long traceId = abort.traceId();
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                cleanupClientSlot();
                server.onClientError(traceId);
            }
        }

        private void onClientReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
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
            if (decodableJson == null)
            {
                final JsonParserFactory parserFactory = switch (server.kind)
                {
                case KIND_TOOLS_LIST -> TOOLS_LIST_ITEM_PARSER_FACTORY;
                case KIND_PROMPTS_LIST -> PROMPTS_LIST_ITEM_PARSER_FACTORY;
                case KIND_RESOURCES_LIST -> RESOURCES_LIST_ITEM_PARSER_FACTORY;
                default -> null;
                };
                if (parserFactory == null)
                {
                    return;
                }
                decodableJson = parserFactory.createParser(inputRO);
                itemOut = new ByteArrayOutputStream(256);
                itemTransferBuffer = new byte[bufferPool.slotCapacity()];
                arrayKey = switch (server.kind)
                {
                case KIND_TOOLS_LIST -> "tools";
                case KIND_PROMPTS_LIST -> "prompts";
                case KIND_RESOURCES_LIST -> "resources";
                default -> null;
                };
                idKey = server.kind == KIND_RESOURCES_LIST ? "uri" : "name";
                decoder = decodeReply;
            }

            final int delta = (int) (decodableJson.getLocation().getStreamOffset() - parserBaseOffset);
            inputRO.wrap(buffer, offset + delta, limit - offset - delta);

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
                compactBoundaryInBuf = offset + (int) (itemStartStreamOffset - parserBaseOffset);
            }
            else
            {
                compactBoundaryInBuf = offset + (int) (decodableJson.getLocation().getStreamOffset() - parserBaseOffset);
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
                parserBaseOffset += compactBoundaryInBuf - offset;
            }
            else
            {
                cleanupClientSlot();
                parserBaseOffset += compactBoundaryInBuf - offset;
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

    private final McpListClientDecoder decodeReply = this::decodeReply;
    private final McpListClientDecoder decodeItemsKey = this::decodeItemsKey;
    private final McpListClientDecoder decodeSkipObject = this::decodeSkipObject;
    private final McpListClientDecoder decodeItems = this::decodeItems;
    private final McpListClientDecoder decodeItem = this::decodeItem;
    private final McpListClientDecoder decodeIgnore = this::decodeIgnore;

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

        return offset + (int) (parser.getLocation().getStreamOffset() - client.parserBaseOffset);
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

        return offset + (int) (parser.getLocation().getStreamOffset() - client.parserBaseOffset);
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

        return offset + (int) (parser.getLocation().getStreamOffset() - client.parserBaseOffset);
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
                client.decoder = decodeItem;
                break decode;
            }
        }

        return offset + (int) (parser.getLocation().getStreamOffset() - client.parserBaseOffset);
    }

    private int decodeItem(
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
            final long beforeStreamOffset = parser.getLocation().getStreamOffset();
            if (!parser.hasNext())
            {
                break decode;
            }
            final long afterStreamOffset = parser.getLocation().getStreamOffset();
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
                if (client.decodeItemDepth == 0)
                {
                    client.itemStartStreamOffset = afterStreamOffset - 1;
                    client.itemLastEmittedStreamOffset = client.itemStartStreamOffset;
                    client.itemOut.reset();
                }
                client.decodeItemDepth++;
                break;
            case START_ARRAY:
                client.decodeItemDepth++;
                break;
            case END_OBJECT:
                client.decodeItemDepth--;
                if (client.decodeItemDepth == 0 && client.itemStartStreamOffset >= 0)
                {
                    final int afterInBuf = offset + (int) (afterStreamOffset - client.parserBaseOffset);
                    final int lastEmittedInBuf =
                        offset + (int) (client.itemLastEmittedStreamOffset - client.parserBaseOffset);
                    final int len = afterInBuf - lastEmittedInBuf;
                    buffer.getBytes(lastEmittedInBuf, client.itemTransferBuffer, 0, len);
                    client.itemOut.write(client.itemTransferBuffer, 0, len);
                    client.server.streamItem(client.itemOut.toByteArray(), 0, client.itemOut.size(), traceId);
                    client.itemStartStreamOffset = -1;
                    client.itemLastEmittedStreamOffset = -1;
                }
                break;
            case END_ARRAY:
                client.decodeItemDepth--;
                if (client.decodeItemDepth < 0)
                {
                    client.decodeDepth--;
                    client.decoder = decodeItemsKey;
                    break decode;
                }
                break;
            case KEY_NAME:
                if (client.decodeItemDepth == 1 && client.idKey.equals(parser.getString()))
                {
                    client.awaitingIdValue = true;
                }
                break;
            case VALUE_STRING:
                if (client.awaitingIdValue && client.itemStartStreamOffset >= 0 && client.prefixBytes.length > 0)
                {
                    client.awaitingIdValue = false;
                    final int beforeInBuf = offset + (int) (beforeStreamOffset - client.parserBaseOffset);
                    final int afterInBuf = offset + (int) (afterStreamOffset - client.parserBaseOffset);
                    int openingQuote = beforeInBuf;
                    while (openingQuote < afterInBuf && buffer.getByte(openingQuote) != (byte) '"')
                    {
                        openingQuote++;
                    }
                    final int contentStart = openingQuote + 1;
                    final int lastEmittedInBuf =
                        offset + (int) (client.itemLastEmittedStreamOffset - client.parserBaseOffset);
                    final int len = contentStart - lastEmittedInBuf;
                    buffer.getBytes(lastEmittedInBuf, client.itemTransferBuffer, 0, len);
                    client.itemOut.write(client.itemTransferBuffer, 0, len);
                    client.itemOut.write(client.prefixBytes, 0, client.prefixBytes.length);
                    client.itemLastEmittedStreamOffset =
                        client.parserBaseOffset + (long) (contentStart - offset);
                }
                else if (client.awaitingIdValue)
                {
                    client.awaitingIdValue = false;
                }
                break;
            default:
                break;
            }
        }

        return offset + (int) (parser.getLocation().getStreamOffset() - client.parserBaseOffset);
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

    private static final JsonParserFactory TOOLS_LIST_ITEM_PARSER_FACTORY = StreamingJson.createParserFactory(
        Map.of(StreamingJson.PATH_INCLUDES, List.of("/tools/-/name")));
    private static final JsonParserFactory PROMPTS_LIST_ITEM_PARSER_FACTORY = StreamingJson.createParserFactory(
        Map.of(StreamingJson.PATH_INCLUDES, List.of("/prompts/-/name")));
    private static final JsonParserFactory RESOURCES_LIST_ITEM_PARSER_FACTORY = StreamingJson.createParserFactory(
        Map.of(StreamingJson.PATH_INCLUDES, List.of("/resources/-/uri")));

    private static final DirectBuffer LIST_REPLY_TOOLS_OPEN_RO =
        new UnsafeBuffer("{\"tools\":[".getBytes(StandardCharsets.UTF_8));
    private static final DirectBuffer LIST_REPLY_PROMPTS_OPEN_RO =
        new UnsafeBuffer("{\"prompts\":[".getBytes(StandardCharsets.UTF_8));
    private static final DirectBuffer LIST_REPLY_RESOURCES_OPEN_RO =
        new UnsafeBuffer("{\"resources\":[".getBytes(StandardCharsets.UTF_8));
    private static final DirectBuffer LIST_REPLY_CLOSE_RO =
        new UnsafeBuffer("]}".getBytes(StandardCharsets.UTF_8));
    private static final DirectBuffer LIST_REPLY_SEPARATOR_RO =
        new UnsafeBuffer(",".getBytes(StandardCharsets.UTF_8));

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
            final long traceId = begin.traceId();
            state = McpState.openingInitial(state);

            doWindow(lifecycle.sender, lifecycle.originId, lifecycle.routedId, initialId, traceId, authorization, 0,
                writeBuffer.capacity(), 0);

            doServerBegin(traceId);
            doEncodeBeginItems(traceId);
            onNextClient(traceId);
        }

        private void onServerEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            state = McpState.closedInitial(state);

            if (client != null)
            {
                client.doClientEnd(traceId);
            }
        }

        private void onServerAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            state = McpState.closedInitial(state);

            if (client != null)
            {
                client.doClientAbort(traceId);
            }
            remaining.clear();
        }

        private void onServerReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
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

        private void streamItem(
            byte[] item,
            int offset,
            int length,
            long traceId)
        {
            if (itemsEmitted > 0)
            {
                doServerData(traceId, 0L, 0x03, LIST_REPLY_SEPARATOR_RO.capacity(),
                    LIST_REPLY_SEPARATOR_RO, 0, LIST_REPLY_SEPARATOR_RO.capacity());
            }
            itemRO.wrap(item, offset, length);
            doServerData(traceId, 0L, 0x03, length, itemRO, 0, length);
            itemsEmitted++;
        }

        private void doEncodeBeginItems(
            long traceId)
        {
            final DirectBuffer prelude = switch (kind)
            {
            case KIND_TOOLS_LIST -> LIST_REPLY_TOOLS_OPEN_RO;
            case KIND_PROMPTS_LIST -> LIST_REPLY_PROMPTS_OPEN_RO;
            case KIND_RESOURCES_LIST -> LIST_REPLY_RESOURCES_OPEN_RO;
            default -> throw new IllegalStateException("unexpected list kind: " + kind);
            };
            doServerData(traceId, 0L, 0x03, prelude.capacity(), prelude, 0, prelude.capacity());
        }

        private void doEncodeEndItems(
            long traceId)
        {
            doServerData(traceId, 0L, 0x03, LIST_REPLY_CLOSE_RO.capacity(),
                LIST_REPLY_CLOSE_RO, 0, LIST_REPLY_CLOSE_RO.capacity());
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

            doBegin(lifecycle.sender, lifecycle.originId, lifecycle.routedId, replyId, traceId, authorization,
                affinity, beginEx);
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
            doData(lifecycle.sender, lifecycle.originId, lifecycle.routedId, replyId, traceId, authorization,
                budgetId, flags, reserved, payload, offset, length);
        }

        private void doServerEnd(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doEnd(lifecycle.sender, lifecycle.originId, lifecycle.routedId, replyId, traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void doServerAbort(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doAbort(lifecycle.sender, lifecycle.originId, lifecycle.routedId, replyId, traceId, authorization);
                state = McpState.closedReply(state);
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
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
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
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
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
        long traceId,
        long authorization,
        long budgetId,
        int flags,
        int reserved,
        DirectBuffer payload,
        int offset,
        int length)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
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
        long traceId,
        long authorization)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
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
        long traceId,
        long authorization)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
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
        long traceId,
        long authorization)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
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
        long traceId,
        long authorization,
        long budgetId,
        int credit,
        int padding)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(credit)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .padding(padding)
            .build();

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }
}
