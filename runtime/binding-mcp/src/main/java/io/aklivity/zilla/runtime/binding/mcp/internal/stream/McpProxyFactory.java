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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.LongUnaryOperator;

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
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
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
            final McpRouteConfig route = binding.resolve(beginEx, authorization);

            if (route != null)
            {
                final int beginKind = beginEx.kind();
                final String sessionId = sessionId(beginEx);

                if (beginKind == KIND_LIFECYCLE)
                {
                    final McpSession session = sessions.computeIfAbsent(sessionId, McpSession::new);
                    session.originId = originId;
                    session.routedId = routedId;
                    session.authorization = authorization;
                    session.affinity = affinity;
                    session.clientCapabilities = beginEx.lifecycle().capabilities();

                    final McpInboundLifecycle inboundLifecycle = new McpInboundLifecycle(
                        sender, originId, routedId, initialId, affinity, authorization, session, binding);
                    session.lifecycle = inboundLifecycle;
                    newStream = inboundLifecycle::onServerMessage;
                }
                else
                {
                    final McpSession session = sessions.get(sessionId);
                    if (session != null)
                    {
                        final McpExit exit = session.supplyExit(route.id);
                        final String identifier = route.strip(beginEx);

                        newStream = new McpServer(
                            sender,
                            originId,
                            routedId,
                            initialId,
                            route.id,
                            affinity,
                            authorization,
                            beginKind,
                            sessionId,
                            identifier,
                            0,
                            session,
                            exit)::onServerMessage;
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

        private long originId;
        private long routedId;
        private long authorization;
        private long affinity;
        private int clientCapabilities;
        private McpInboundLifecycle lifecycle;

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

    private final class McpExit
    {
        private static final int UNINITIALIZED = 0;
        private static final int OPENING = 1;
        private static final int OPENED = 2;

        private final long exitId;
        private final Deque<McpServer> pending;

        private int state;
        private String exitSessionId;
        private McpOutboundLifecycle lifecycle;

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
        private final McpClient client;
        private final int beginKind;
        private final String sessionId;
        private final String identifier;
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
            int beginKind,
            String sessionId,
            String identifier,
            int capabilities,
            McpSession session,
            McpExit exit)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.beginKind = beginKind;
            this.sessionId = sessionId;
            this.identifier = identifier;
            this.capabilities = capabilities;
            this.session = session;
            this.exit = exit;
            this.client = new McpClient(this, resolvedId);
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
                exit.pending.add(this);
                if (exit.state == McpExit.UNINITIALIZED)
                {
                    exit.state = McpExit.OPENING;
                    exit.lifecycle = new McpOutboundLifecycle(exit, session);
                    exit.lifecycle.doClientBegin(traceId);
                }
            }
        }

        private void proceed(
            long traceId)
        {
            client.doClientBegin(traceId);

            doWindow(sender, originId, routedId, replyId, traceId, authorization, 0,
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

            if (!McpState.closed(state) && payload != null)
            {
                client.doClientData(traceId, budgetId, flags, reserved,
                    payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void onServerEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = McpState.closedInitial(state);

            if (!McpState.closed(state))
            {
                client.doClientEnd(traceId);
            }

            if (beginKind == KIND_LIFECYCLE)
            {
                sessions.remove(session.sessionId);
            }
        }

        private void onServerAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = McpState.closedInitial(state);

            if (!McpState.closed(state))
            {
                client.doClientAbort(traceId);
            }

            if (beginKind == KIND_LIFECYCLE)
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
            OctetsFW extension)
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

        private long initialId;
        private long replyId;
        private MessageConsumer sender;

        private int state;

        private McpClient(
            McpServer server,
            long resolvedId)
        {
            this.server = server;
            this.resolvedId = resolvedId;
        }

        private void doClientBegin(
            long traceId)
        {
            initialId = supplyInitialId.applyAsLong(resolvedId);
            replyId = supplyReplyId.applyAsLong(initialId);
            sender = streamFactory.newStream(BeginFW.TYPE_ID, writeBuffer, 0, 0, this::onClientMessage);
            assert sender != null;

            final String identifier = server.identifier;
            final int capabilities = server.capabilities;
            final boolean lifecycle = server.beginKind == KIND_LIFECYCLE;
            final String outboundSessionId = lifecycle || server.exit.exitSessionId == null
                ? server.sessionId
                : server.exit.exitSessionId;
            final McpBeginExFW.Builder builder = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId);
            switch (server.beginKind)
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
            default -> throw new IllegalStateException("unexpected McpBeginEx kind: " + server.beginKind);
            }
            final McpBeginExFW beginEx = builder.build();

            doBegin(sender, server.originId, resolvedId, initialId,
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

            server.doServerBegin(traceId, extension);
        }

        private void onClientData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                server.doServerData(traceId, budgetId, flags, reserved,
                    payload.buffer(), payload.offset(), payload.sizeof());
            }
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

    private final class McpInboundLifecycle
    {
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;
        private final McpSession session;
        private final McpBindingConfig binding;

        private int state;

        private McpInboundLifecycle(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization,
            McpSession session,
            McpBindingConfig binding)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.session = session;
            this.binding = binding;
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

            final int serverCapabilities = binding.serverCapabilities(authorization);
            final String sid = session.sessionId;
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .lifecycle(l -> l.sessionId(sid).capabilities(serverCapabilities))
                .build();

            doBegin(sender, originId, routedId, replyId, traceId, authorization, affinity, beginEx);
            state = McpState.openedReply(state);

            doWindow(sender, originId, routedId, replyId, traceId, authorization, 0,
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
                for (McpServer pending : exit.pending)
                {
                    pending.doServerReset(traceId);
                }
                exit.pending.clear();
            }
        }
    }

    private final class McpOutboundLifecycle
    {
        private final McpExit exit;
        private final McpSession session;
        private final long initialId;
        private final long replyId;
        private MessageConsumer sender;

        private int state;

        private McpOutboundLifecycle(
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
            sender = streamFactory.newStream(BeginFW.TYPE_ID, writeBuffer, 0, 0, this::onClientMessage);
            assert sender != null;

            final String sid = session.sessionId;
            final int clientCapabilities = session.clientCapabilities;
            final McpBeginExFW beginEx = mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .lifecycle(l -> l.sessionId(sid).capabilities(clientCapabilities))
                .build();

            doBegin(sender, session.originId, exit.exitId, initialId, traceId,
                session.authorization, session.affinity, beginEx);
            state = McpState.openingInitial(state);
        }

        private void doClientEnd(
            long traceId)
        {
            if (sender != null && !McpState.initialClosed(state))
            {
                doEnd(sender, session.originId, exit.exitId, initialId, traceId, session.authorization);
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
                    exit.exitSessionId = beginEx.lifecycle().sessionId().asString();
                }
            }

            doWindow(sender, session.originId, exit.exitId, replyId, traceId, session.authorization, 0,
                writeBuffer.capacity(), 0);

            exit.state = McpExit.OPENED;
            while (!exit.pending.isEmpty())
            {
                exit.pending.poll().proceed(traceId);
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
                exit.pending.poll().doServerReset(traceId);
            }
        }
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
            .budgetId(budgetId)
            .reserved(reserved)
            .flags(flags)
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
