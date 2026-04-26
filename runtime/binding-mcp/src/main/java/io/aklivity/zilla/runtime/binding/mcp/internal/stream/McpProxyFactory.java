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
import java.util.function.LongConsumer;
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
    private final Map<String, McpSession> sessions;

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
                    final McpSession session = sessions.computeIfAbsent(sessionId, McpSession::new);
                    final int clientCapabilities = beginEx.lifecycle().capabilities();
                    final McpLifecycleServer lifecycle = new McpLifecycleServer(
                        sender, originId, routedId, initialId, affinity, authorization,
                        clientCapabilities, session);
                    session.lifecycle = lifecycle;
                    newStream = lifecycle::onServerMessage;
                }
            }
            else
            {
                final McpSession session = sessions.get(sessionId);
                if (session != null)
                {
                    if (isListKind(kind))
                    {
                        final List<McpRouteConfig> routes = binding.resolveAll(beginEx, authorization);
                        final McpListServer server = new McpListServer(
                            sender,
                            originId,
                            routedId,
                            initialId,
                            affinity,
                            authorization,
                            kind,
                            sessionId,
                            session,
                            routes,
                            beginEx);
                        newStream = server::onServerMessage;
                    }
                    else
                    {
                        final McpRouteConfig route = binding.resolve(beginEx, authorization);
                        if (route != null)
                        {
                            final McpExit exit = session.supplyExit(route.id);
                            final String identifier = route.strip(beginEx);
                            final String prefix = route.prefix(beginEx);

                            newStream = new McpServer(
                                sender,
                                originId,
                                routedId,
                                initialId,
                                route.id,
                                affinity,
                                authorization,
                                kind,
                                sessionId,
                                identifier,
                                prefix,
                                0,
                                session,
                                exit)::onServerMessage;
                        }
                    }
                }
            }
        }

        return newStream;
    }

    private final class McpSession
    {
        private final String sessionId;
        private final Long2ObjectHashMap<McpExit> exits;

        private McpLifecycleServer lifecycle;

        private McpSession(
            String sessionId)
        {
            this.sessionId = sessionId;
            this.exits = new Long2ObjectHashMap<>();
        }

        private McpExit supplyExit(
            long exitId)
        {
            return exits.computeIfAbsent(exitId, McpExit::new);
        }
    }

    private record PendingAction(
        LongConsumer onProceed,
        LongConsumer onReset)
    {
    }

    private final class McpExit
    {
        private static final int UNINITIALIZED = 0;
        private static final int OPENING = 1;
        private static final int OPENED = 2;

        private final long exitId;
        private final Deque<PendingAction> pending;

        private int state;
        private String sessionId;
        private McpLifecycleClient lifecycle;

        private McpExit(
            long exitId)
        {
            this.exitId = exitId;
            this.pending = new ArrayDeque<>();
        }
    }

    private final class McpServer
    {
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;
        private final long resolvedId;
        private final int kind;
        private McpClient client;
        private final String sessionId;
        private final String identifier;
        private final String prefix;
        private final int capabilities;
        private final McpSession session;
        private final McpExit exit;

        private int state;

        private McpServer(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            int kind,
            String sessionId,
            String identifier,
            String prefix,
            int capabilities,
            McpSession session,
            McpExit exit)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.kind = kind;
            this.sessionId = sessionId;
            this.identifier = identifier;
            this.prefix = prefix;
            this.capabilities = capabilities;
            this.session = session;
            this.exit = exit;
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

            if (exit.state == McpExit.OPENED)
            {
                proceed(traceId);
            }
            else
            {
                exit.pending.add(new PendingAction(this::proceed, this::doServerReset));
                if (exit.state == McpExit.UNINITIALIZED)
                {
                    exit.state = McpExit.OPENING;
                    exit.lifecycle = new McpLifecycleClient(exit, session);
                    exit.lifecycle.doClientBegin(traceId);
                }
            }
        }

        private void proceed(
            long traceId)
        {
            this.client = new McpClient(this, resolvedId, traceId);

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

            if (kind == KIND_LIFECYCLE)
            {
                sessions.remove(session.sessionId);
            }
        }

        private void onServerAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = McpState.closedInitial(state);

            client.doClientAbort(traceId);

            if (kind == KIND_LIFECYCLE)
            {
                sessions.remove(session.sessionId);
            }
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

        private final long initialId;
        private final long replyId;
        private final MessageConsumer sender;

        private int state;

        private McpClient(
            McpServer server,
            long resolvedId,
            long traceId)
        {
            this.server = server;
            this.resolvedId = resolvedId;
            this.initialId = supplyInitialId.applyAsLong(resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);

            final String identifier = server.identifier;
            final int capabilities = server.capabilities;
            final boolean lifecycle = server.kind == KIND_LIFECYCLE;
            final String outboundSessionId = lifecycle || server.exit.sessionId == null
                ? server.sessionId
                : server.exit.sessionId;
            final McpBeginExFW.Builder builder = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId);
            switch (server.kind)
            {
            case KIND_LIFECYCLE -> builder
                .lifecycle(l -> l.sessionId(outboundSessionId).capabilities(capabilities));
            case KIND_TOOLS_LIST -> builder
                .toolsList(t -> t.sessionId(outboundSessionId));
            case KIND_TOOLS_CALL -> builder
                .toolsCall(t -> t.sessionId(outboundSessionId).name(identifier));
            case KIND_PROMPTS_LIST -> builder
                .promptsList(p -> p.sessionId(outboundSessionId));
            case KIND_PROMPTS_GET -> builder
                .promptsGet(p -> p.sessionId(outboundSessionId).name(identifier));
            case KIND_RESOURCES_LIST -> builder
                .resourcesList(r -> r.sessionId(outboundSessionId));
            case KIND_RESOURCES_READ -> builder
                .resourcesRead(r -> r.sessionId(outboundSessionId).uri(identifier));
            default -> throw new IllegalStateException("unexpected McpBeginEx kind: " + server.kind);
            }
            final McpBeginExFW beginEx = builder.build();

            this.sender = newStream(this::onClientMessage, server.originId, resolvedId, initialId,
                traceId, server.authorization, server.affinity, beginEx);
            this.state = McpState.openingInitial(0);
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
                doData(sender, server.originId, resolvedId, initialId,
                    traceId, server.authorization, budgetId, flags, reserved, payload, offset, length);
            }
        }

        private void doClientEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doEnd(sender, server.originId, resolvedId, initialId,
                    traceId, server.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doAbort(sender, server.originId, resolvedId, initialId,
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
            doWindow(sender, server.originId, resolvedId, replyId,
                traceId, server.authorization, budgetId, credit, padding);
        }

        private void doClientReset(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doReset(sender, server.originId, resolvedId, replyId,
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
            final String sid = server.sessionId;
            final McpBeginExFW.Builder builder = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId);

            switch (beginEx.kind())
            {
            case KIND_LIFECYCLE:
                final int caps = beginEx.lifecycle().capabilities();
                builder.lifecycle(l -> l.sessionId(sid).capabilities(caps));
                break;
            case KIND_TOOLS_LIST:
                builder.toolsList(t -> t.sessionId(sid));
                break;
            case KIND_TOOLS_CALL:
                final String toolName = beginEx.toolsCall().name().asString();
                builder.toolsCall(t -> t.sessionId(sid).name(toolName));
                break;
            case KIND_PROMPTS_LIST:
                builder.promptsList(p -> p.sessionId(sid));
                break;
            case KIND_PROMPTS_GET:
                final String promptName = beginEx.promptsGet().name().asString();
                builder.promptsGet(p -> p.sessionId(sid).name(promptName));
                break;
            case KIND_RESOURCES_LIST:
                builder.resourcesList(r -> r.sessionId(sid));
                break;
            case KIND_RESOURCES_READ:
                final String uri = beginEx.resourcesRead().uri().asString();
                builder.resourcesRead(r -> r.sessionId(sid).uri(uri));
                break;
            default:
                return beginEx;
            }
            return builder.build();
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
        private final McpSession session;

        private int state;

        private McpLifecycleServer(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization,
            int clientCapabilities,
            McpSession session)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.clientCapabilities = clientCapabilities;
            this.session = session;
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
            final String sid = session.sessionId;
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
            sessions.remove(session.sessionId);

            for (McpExit exit : session.exits.values())
            {
                if (exit.lifecycle != null)
                {
                    exit.lifecycle.doClientEnd(traceId);
                }
                for (PendingAction pending : exit.pending)
                {
                    pending.onReset().accept(traceId);
                }
                exit.pending.clear();
            }
        }
    }

    private final class McpLifecycleClient
    {
        private final McpExit exit;
        private final McpSession session;
        private final long initialId;
        private final long replyId;
        private MessageConsumer sender;

        private int state;

        private McpLifecycleClient(
            McpExit exit,
            McpSession session)
        {
            this.exit = exit;
            this.session = session;
            this.initialId = supplyInitialId.applyAsLong(exit.exitId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doClientBegin(
            long traceId)
        {
            final String sid = session.sessionId;
            final McpLifecycleServer server = session.lifecycle;
            final int clientCapabilities = server.clientCapabilities;
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .lifecycle(l -> l.sessionId(sid).capabilities(clientCapabilities))
                .build();

            sender = newStream(this::onClientMessage, server.originId, exit.exitId, initialId,
                traceId, server.authorization, server.affinity, beginEx);
            state = McpState.openingInitial(state);
        }

        private void doClientEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                final McpLifecycleServer server = session.lifecycle;
                doEnd(sender, server.originId, exit.exitId, initialId, traceId, server.authorization);
                state = McpState.closedInitial(state);
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
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            state = McpState.openedInitial(state);

            if (extension.sizeof() > 0)
            {
                final McpBeginExFW beginEx = mcpBeginExRO.wrap(
                    extension.buffer(), extension.offset(), extension.limit());
                if (beginEx.kind() == KIND_LIFECYCLE)
                {
                    exit.sessionId = beginEx.lifecycle().sessionId().asString();
                }
            }

            final McpLifecycleServer server = session.lifecycle;
            doWindow(sender, server.originId, exit.exitId, replyId, traceId, server.authorization, 0,
                writeBuffer.capacity(), 0);

            exit.state = McpExit.OPENED;
            while (!exit.pending.isEmpty())
            {
                exit.pending.poll().onProceed().accept(traceId);
            }
        }

        private void onClientEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = McpState.closedReply(state);

            failPending(traceId);
        }

        private void onClientAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = McpState.closedReply(state);

            failPending(traceId);
        }

        private void onClientReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpState.closedInitial(state);

            failPending(traceId);
        }

        private void failPending(
            long traceId)
        {
            while (!exit.pending.isEmpty())
            {
                exit.pending.poll().onReset().accept(traceId);
            }
        }
    }

    private final class McpListClient
    {
        private final McpListServer server;
        private final McpExit exit;
        private final long resolvedId;
        private final String prefix;
        private final byte[] prefixBytes;

        private long initialId;
        private long replyId;
        private MessageConsumer sender;
        private int state;
        private int replySlot = NO_SLOT;
        private int replySlotOffset;

        // streaming JSON state — built lazily on first DATA frame
        private JsonParser parser;
        private DirectBufferInputStreamEx jsonInput;
        private ByteArrayOutputStream itemOut;
        private byte[] itemTransferBuffer;
        private long parserBaseOffset;       // absolute streamOffset of slot[0]
        private int depth;
        private int itemDepth = -1;          // depth at which target items live, set on START_ARRAY
        private boolean inTargetArray;
        private boolean awaitingArrayValue;
        private boolean awaitingIdValue;
        private long itemStartStreamOffset = -1;
        private long itemLastEmittedStreamOffset = -1;

        private McpListClient(
            McpListServer server,
            McpExit exit,
            long resolvedId,
            String prefix)
        {
            this.server = server;
            this.exit = exit;
            this.resolvedId = resolvedId;
            this.prefix = prefix;
            this.prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        }

        private void proceed(
            long traceId)
        {
            doClientBegin(traceId);
            if (McpState.initialClosed(server.state))
            {
                doClientEnd(traceId);
            }
        }

        private void doServerReset(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                server.clientFailed(this, traceId);
            }
        }

        private void doClientBegin(
            long traceId)
        {
            initialId = supplyInitialId.applyAsLong(resolvedId);
            replyId = supplyReplyId.applyAsLong(initialId);

            final String sid = exit.sessionId != null
                ? exit.sessionId : server.sessionId;
            final McpBeginExFW.Builder builder = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId);
            switch (server.kind)
            {
            case KIND_TOOLS_LIST -> builder.toolsList(t -> t.sessionId(sid));
            case KIND_PROMPTS_LIST -> builder.promptsList(p -> p.sessionId(sid));
            case KIND_RESOURCES_LIST -> builder.resourcesList(r -> r.sessionId(sid));
            default -> throw new IllegalStateException("unexpected list kind: " + server.kind);
            }
            final McpBeginExFW beginEx = builder.build();

            sender = newStream(this::onClientMessage, server.originId, resolvedId, initialId,
                traceId, server.authorization, server.affinity, beginEx);
            state = McpState.openingInitial(state);
        }

        private void doClientEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doEnd(sender, server.originId, resolvedId, initialId,
                    traceId, server.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doAbort(sender, server.originId, resolvedId, initialId,
                    traceId, server.authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientReset(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doReset(sender, server.originId, resolvedId, replyId,
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
            doWindow(sender, server.originId, resolvedId, replyId, traceId,
                server.authorization, 0, writeBuffer.capacity(), 0);
        }

        private void onClientData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();
            final boolean appended = appendToSlot(payload);
            if (!appended)
            {
                state = McpState.closedReply(state);
                server.clientFailed(this, traceId);
                return;
            }
            streamItems(traceId);
        }

        private void onClientEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                cleanupClientSlot();
                server.clientDone(this, traceId);
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
                server.clientFailed(this, traceId);
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
                server.clientFailed(this, traceId);
            }
        }

        private boolean appendToSlot(
            OctetsFW payload)
        {
            if (replySlot == NO_SLOT)
            {
                replySlot = bufferPool.acquire(initialId);
            }
            if (replySlot == NO_SLOT)
            {
                return false;
            }
            final MutableDirectBuffer buf = bufferPool.buffer(replySlot);
            if (replySlotOffset + payload.sizeof() > buf.capacity())
            {
                return false;
            }
            buf.putBytes(replySlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
            replySlotOffset += payload.sizeof();
            return true;
        }

        private void streamItems(
            long traceId)
        {
            if (parser == null)
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
                jsonInput = new DirectBufferInputStreamEx();
                parser = parserFactory.createParser(jsonInput);
                itemOut = new ByteArrayOutputStream(256);
                itemTransferBuffer = new byte[bufferPool.slotCapacity()];
            }

            final MutableDirectBuffer slotBuffer = bufferPool.buffer(replySlot);
            final int parserPosInSlot = (int) (parser.getLocation().getStreamOffset() - parserBaseOffset);
            jsonInput.wrap(slotBuffer, parserPosInSlot, replySlotOffset - parserPosInSlot);

            final String idKey = server.kind == KIND_RESOURCES_LIST ? "uri" : "name";
            final String arrayKey = switch (server.kind)
            {
            case KIND_TOOLS_LIST -> "tools";
            case KIND_PROMPTS_LIST -> "prompts";
            case KIND_RESOURCES_LIST -> "resources";
            default -> null;
            };

            walk:
            while (true)
            {
                final long beforeStreamOffset = parser.getLocation().getStreamOffset();
                if (!parser.hasNext())
                {
                    break;
                }
                final long afterStreamOffset = parser.getLocation().getStreamOffset();
                final JsonParser.Event ev = parser.next();
                final int afterInSlot = (int) (afterStreamOffset - parserBaseOffset);
                final int beforeInSlot = (int) (beforeStreamOffset - parserBaseOffset);
                switch (ev)
                {
                case START_OBJECT:
                    depth++;
                    if (inTargetArray && depth == itemDepth)
                    {
                        itemStartStreamOffset = afterStreamOffset - 1;
                        itemLastEmittedStreamOffset = itemStartStreamOffset;
                        itemOut.reset();
                    }
                    break;
                case START_ARRAY:
                    depth++;
                    if (awaitingArrayValue)
                    {
                        awaitingArrayValue = false;
                        inTargetArray = true;
                        itemDepth = depth + 1;
                    }
                    break;
                case END_OBJECT:
                    if (depth == itemDepth && itemStartStreamOffset >= 0)
                    {
                        final int itemLastEmittedInSlot = (int) (itemLastEmittedStreamOffset - parserBaseOffset);
                        slotBuffer.getBytes(itemLastEmittedInSlot, itemTransferBuffer,
                            0, afterInSlot - itemLastEmittedInSlot);
                        itemOut.write(itemTransferBuffer, 0, afterInSlot - itemLastEmittedInSlot);
                        server.streamItem(itemOut.toByteArray(), 0, itemOut.size(), traceId);
                        itemStartStreamOffset = -1;
                        itemLastEmittedStreamOffset = -1;
                    }
                    depth--;
                    break;
                case END_ARRAY:
                    depth--;
                    if (inTargetArray && depth + 1 == itemDepth - 1)
                    {
                        inTargetArray = false;
                        itemDepth = -1;
                    }
                    break;
                case KEY_NAME:
                    final String key = parser.getString();
                    if (depth == 1 && arrayKey.equals(key))
                    {
                        awaitingArrayValue = true;
                    }
                    else if (inTargetArray && depth == itemDepth && idKey.equals(key))
                    {
                        awaitingIdValue = true;
                    }
                    break;
                case VALUE_STRING:
                    if (awaitingIdValue && itemStartStreamOffset >= 0 && prefixBytes.length > 0)
                    {
                        awaitingIdValue = false;
                        // bytes [beforeInSlot..afterInSlot) span ":<ws>\"<content>\"" — find opening quote
                        int openingQuote = beforeInSlot;
                        while (openingQuote < afterInSlot && slotBuffer.getByte(openingQuote) != (byte) '"')
                        {
                            openingQuote++;
                        }
                        final int contentStart = openingQuote + 1;
                        final int itemLastEmittedInSlot = (int) (itemLastEmittedStreamOffset - parserBaseOffset);
                        // emit verbatim up to and including opening quote
                        slotBuffer.getBytes(itemLastEmittedInSlot, itemTransferBuffer,
                            0, contentStart - itemLastEmittedInSlot);
                        itemOut.write(itemTransferBuffer, 0, contentStart - itemLastEmittedInSlot);
                        // splice in the prefix
                        itemOut.write(prefixBytes, 0, prefixBytes.length);
                        itemLastEmittedStreamOffset = parserBaseOffset + contentStart;
                    }
                    else if (awaitingIdValue)
                    {
                        awaitingIdValue = false;
                    }
                    break;
                default:
                    break;
                }
            }

            // Compact slot: drop bytes that are no longer needed (consumed and not part of an in-progress item)
            final int compactBoundaryInSlot;
            if (itemStartStreamOffset >= 0)
            {
                compactBoundaryInSlot = (int) (itemStartStreamOffset - parserBaseOffset);
            }
            else
            {
                compactBoundaryInSlot = (int) (parser.getLocation().getStreamOffset() - parserBaseOffset);
            }
            if (compactBoundaryInSlot > 0)
            {
                slotBuffer.putBytes(0, slotBuffer, compactBoundaryInSlot, replySlotOffset - compactBoundaryInSlot);
                replySlotOffset -= compactBoundaryInSlot;
                parserBaseOffset += compactBoundaryInSlot;
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

    private static final JsonParserFactory TOOLS_LIST_ITEM_PARSER_FACTORY = StreamingJson.createParserFactory(
        Map.of(StreamingJson.PATH_INCLUDES, List.of("/tools/-/name")));
    private static final JsonParserFactory PROMPTS_LIST_ITEM_PARSER_FACTORY = StreamingJson.createParserFactory(
        Map.of(StreamingJson.PATH_INCLUDES, List.of("/prompts/-/name")));
    private static final JsonParserFactory RESOURCES_LIST_ITEM_PARSER_FACTORY = StreamingJson.createParserFactory(
        Map.of(StreamingJson.PATH_INCLUDES, List.of("/resources/-/uri")));

    private static final byte[] LIST_REPLY_TOOLS_OPEN = "{\"tools\":[".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LIST_REPLY_PROMPTS_OPEN = "{\"prompts\":[".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LIST_REPLY_RESOURCES_OPEN = "{\"resources\":[".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LIST_REPLY_CLOSE = "]}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LIST_REPLY_SEPARATOR = ",".getBytes(StandardCharsets.UTF_8);

    private final class McpListServer
    {
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;
        private final int kind;
        private final String sessionId;
        private final McpSession session;
        private final Deque<McpListClient> remaining;

        private int state;
        private int itemsEmitted;
        private McpListClient currentClient;

        private McpListServer(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization,
            int kind,
            String sessionId,
            McpSession session,
            List<McpRouteConfig> routes,
            McpBeginExFW beginEx)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.kind = kind;
            this.sessionId = sessionId;
            this.session = session;
            this.remaining = new ArrayDeque<>(routes.size());
            for (final McpRouteConfig route : routes)
            {
                final McpExit exit = session.supplyExit(route.id);
                final String prefix = route.prefix(beginEx);
                remaining.add(new McpListClient(this, exit, route.id, prefix));
            }
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

            doWindow(sender, originId, routedId, initialId, traceId, authorization, 0,
                writeBuffer.capacity(), 0);

            doServerBegin(traceId);
            emitPrelude(traceId);
            startNextRoute(traceId);
        }

        private void onServerEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            state = McpState.closedInitial(state);

            if (currentClient != null && McpState.initialOpened(currentClient.state))
            {
                currentClient.doClientEnd(traceId);
            }
        }

        private void onServerAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            state = McpState.closedInitial(state);

            if (currentClient != null)
            {
                currentClient.doClientAbort(traceId);
            }
            // queued clients have not opened an upstream stream — drop them
            remaining.clear();
        }

        private void onServerReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            state = McpState.closedReply(state);

            if (currentClient != null)
            {
                currentClient.doClientReset(traceId);
            }
            // queued clients have not opened an upstream stream — drop them
            remaining.clear();
        }

        private void clientDone(
            McpListClient client,
            long traceId)
        {
            currentClient = null;
            startNextRoute(traceId);
        }

        private void clientFailed(
            McpListClient client,
            long traceId)
        {
            currentClient = null;
            for (final McpListClient queued : remaining)
            {
                queued.doClientAbort(traceId);
            }
            remaining.clear();
            doServerAbort(traceId);
        }

        private void startNextRoute(
            long traceId)
        {
            if (remaining.isEmpty())
            {
                closeReply(traceId);
                return;
            }
            currentClient = remaining.poll();
            final McpExit exit = currentClient.exit;
            if (exit.state == McpExit.OPENED)
            {
                currentClient.proceed(traceId);
            }
            else
            {
                exit.pending.add(new PendingAction(currentClient::proceed, currentClient::doServerReset));
                if (exit.state == McpExit.UNINITIALIZED)
                {
                    exit.state = McpExit.OPENING;
                    exit.lifecycle = new McpLifecycleClient(exit, session);
                    exit.lifecycle.doClientBegin(traceId);
                }
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
                final UnsafeBuffer sep = new UnsafeBuffer(LIST_REPLY_SEPARATOR);
                doServerData(traceId, 0L, 0x03, sep.capacity(), sep, 0, sep.capacity());
            }
            final UnsafeBuffer itemBuffer = new UnsafeBuffer(item, offset, length);
            doServerData(traceId, 0L, 0x03, length, itemBuffer, 0, length);
            itemsEmitted++;
        }

        private void emitPrelude(
            long traceId)
        {
            final byte[] preludeBytes = switch (kind)
            {
            case KIND_TOOLS_LIST -> LIST_REPLY_TOOLS_OPEN;
            case KIND_PROMPTS_LIST -> LIST_REPLY_PROMPTS_OPEN;
            case KIND_RESOURCES_LIST -> LIST_REPLY_RESOURCES_OPEN;
            default -> throw new IllegalStateException("unexpected list kind: " + kind);
            };
            final UnsafeBuffer buf = new UnsafeBuffer(preludeBytes);
            doServerData(traceId, 0L, 0x03, preludeBytes.length, buf, 0, preludeBytes.length);
        }

        private void closeReply(
            long traceId)
        {
            final UnsafeBuffer buf = new UnsafeBuffer(LIST_REPLY_CLOSE);
            doServerData(traceId, 0L, 0x03, buf.capacity(), buf, 0, buf.capacity());
            doServerEnd(traceId);
        }

        private void doServerBegin(
            long traceId)
        {
            final String sid = sessionId;
            final McpBeginExFW.Builder builder = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId);
            switch (kind)
            {
            case KIND_TOOLS_LIST -> builder.toolsList(t -> t.sessionId(sid));
            case KIND_PROMPTS_LIST -> builder.promptsList(p -> p.sessionId(sid));
            case KIND_RESOURCES_LIST -> builder.resourcesList(r -> r.sessionId(sid));
            default -> throw new IllegalStateException("unexpected list kind: " + kind);
            }
            final McpBeginExFW beginEx = builder.build();

            doBegin(sender, originId, routedId, replyId, traceId, authorization, affinity, beginEx);
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
