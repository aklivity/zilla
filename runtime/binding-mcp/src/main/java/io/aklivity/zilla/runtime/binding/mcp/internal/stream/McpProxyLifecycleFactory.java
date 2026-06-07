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
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpAggregateEventId;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpAggregateRoute;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpProxySession;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpChallengeExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpElicitAction;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpElicitCallbackFlushExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpElicitCompleteFlushExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpElicitCreateChallengeExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpElicitResponseFlushExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpElicitStatus;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpFlushExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpProgressFlushExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpPromptsListChangedFlushExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpResourcesListChangedFlushExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpResumableFlushExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpResumeChallengeExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpToolsListChangedFlushExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.util.function.LongIntPredicate;

final class McpProxyLifecycleFactory implements BindingHandler
{
    static final int SIGNAL_HYDRATE_COMPLETE = 1;

    private static final String MCP_TYPE_NAME = "mcp";
    private static final int AGGREGATE_BUFFER_CAPACITY = 1024;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final SignalFW signalRO = new SignalFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final McpFlushExFW mcpFlushExRO = new McpFlushExFW();
    private final McpChallengeExFW mcpChallengeExRO = new McpChallengeExFW();
    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(), 0, 0);
    private final OctetsFW rewrittenExRO = new OctetsFW();
    private final OctetsFW aggregateRO = new OctetsFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();
    private final McpFlushExFW.Builder mcpFlushExRW = new McpFlushExFW.Builder();
    private final McpChallengeExFW.Builder mcpChallengeExRW = new McpChallengeExFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer codecBuffer;
    private final MutableDirectBuffer flushExBuffer;
    private final MutableDirectBuffer challengeExBuffer;
    private final MutableDirectBuffer aggregateBuffer;
    private final BindingHandler streamFactory;
    private final Signaler signaler;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int mcpTypeId;
    private final LongFunction<McpBindingConfig> supplyBinding;
    private final Supplier<String> supplySessionId;
    private final LongIntPredicate isLocalIndex;
    private final int sessionIdAttempts;

    McpProxyLifecycleFactory(
        McpConfiguration config,
        EngineContext context,
        LongFunction<McpBindingConfig> supplyBinding)
    {
        this.writeBuffer = context.writeBuffer();
        this.codecBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.flushExBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.challengeExBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.aggregateBuffer = new UnsafeBuffer(new byte[AGGREGATE_BUFFER_CAPACITY]);
        this.streamFactory = context.streamFactory();
        this.signaler = context.signaler();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.supplyBinding = supplyBinding;
        this.supplySessionId = config.sessionIdSupplier();
        this.isLocalIndex = context::isLocalIndex;
        this.sessionIdAttempts = config.sessionIdAttempts();
    }

    @FunctionalInterface
    interface McpRouteRequest
    {
        void onLifecycleSettled(
            long traceId);
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

        MessageConsumer newStream = null;

        final McpBindingConfig binding = supplyBinding.apply(routedId);
        final McpBeginExFW beginEx = extension.get(mcpBeginExRO::tryWrap);

        if (binding != null && beginEx != null && beginEx.kind() == KIND_LIFECYCLE)
        {
            final String requestSessionId = beginEx.lifecycle().sessionId().asString();
            assert requestSessionId == null;

            final McpRouteConfig route = binding.resolve(beginEx, authorization);
            final String sessionId = route != null ? newSessionId(routedId) : null;
            if (sessionId != null)
            {
                final int clientCapabilities = beginEx.lifecycle().capabilities();
                final McpLifecycleServer lifecycle = new McpLifecycleServer(
                    binding, sender, originId, routedId, initialId, affinity, authorization,
                    clientCapabilities, sessionId, false);
                binding.sessions.put(sessionId, lifecycle);
                newStream = lifecycle::onServerMessage;
            }
        }

        return newStream;
    }

    private String newSessionId(
        long bindingId)
    {
        return McpSessionId.newSessionId(bindingId, sessionIdAttempts, supplySessionId, isLocalIndex);
    }

    McpLifecycleServer newHydrationLifecycle(
        McpBindingConfig binding,
        MessageConsumer sink,
        long bindingId,
        long authorization)
    {
        final String sessionId = newSessionId(bindingId);
        return new McpLifecycleServer(
            binding, sink, bindingId, bindingId, supplyInitialId.applyAsLong(bindingId),
            0L, authorization, 0, sessionId, true);
    }

    final class McpLifecycleServer implements McpProxySession
    {
        private final McpBindingConfig binding;
        final MessageConsumer sender;
        final long originId;
        final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;
        private final int clientCapabilities;
        final String sessionId;
        private final boolean hydration;
        private final Long2ObjectHashMap<McpLifecycleClient> clients;
        private final Long2ObjectHashMap<String> eventIds;
        private final Object2ObjectHashMap<String, McpLifecycleClient> elicitClients;

        private int state;
        private boolean resumePending;
        private int pendingClients;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private McpLifecycleServer(
            McpBindingConfig binding,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization,
            int clientCapabilities,
            String sessionId,
            boolean hydration)
        {
            this.binding = binding;
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.clientCapabilities = clientCapabilities;
            this.sessionId = sessionId;
            this.hydration = hydration;
            this.clients = new Long2ObjectHashMap<>();
            this.elicitClients = new Object2ObjectHashMap<>();
            this.eventIds = binding.aggregateRoutes.length > 0
                ? new Long2ObjectHashMap<>()
                : null;
        }

        McpLifecycleClient supplyClient(
            long routedId)
        {
            return clients.computeIfAbsent(routedId, id -> new McpLifecycleClient(this, id));
        }

        private boolean aggregating()
        {
            return eventIds != null;
        }

        private void onDecodeEventId(
            long routedId,
            String eventId)
        {
            if (eventId != null)
            {
                eventIds.put(routedId, eventId);
            }
        }

        private OctetsFW nextEventId()
        {
            final int length = McpAggregateEventId.encode(
                binding.aggregateRoutes, eventIds, aggregateBuffer, 0);
            return length < 0 ? null : aggregateRO.wrap(aggregateBuffer, 0, length);
        }

        private void onDecodeAggregateEventId(
            long traceId,
            long authorization,
            String prefix,
            String eventId)
        {
            final McpRouteConfig route = binding.routeByPrefix.get(prefix);
            if (route != null)
            {
                onDecodeEventId(route.id, eventId);
                final McpLifecycleClient client = supplyClient(route.id);
                client.resumeId = eventId;
                client.doClientBegin(traceId);
                client.doClientResume(traceId, authorization);
            }
        }

        private void onServerResumeRoutes(
            long traceId)
        {
            for (McpAggregateRoute route : binding.aggregateRoutes)
            {
                final long routedId = route.routedId();
                if (!clients.containsKey(routedId))
                {
                    final McpLifecycleClient client = supplyClient(routedId);
                    client.doClientBegin(traceId);
                }
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
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onServerFlush(flush);
                break;
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onServerChallenge(challenge);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onServerSignal(signal);
                break;
            default:
                break;
            }
        }

        private void onServerFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final OctetsFW extension = flush.extension();
            final McpFlushExFW flushEx = extension.sizeof() > 0
                ? mcpFlushExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            if (flushEx != null && flushEx.kind() == McpFlushExFW.KIND_ELICIT_CALLBACK)
            {
                final McpElicitCallbackFlushExFW callback = flushEx.elicitCallback();
                final String context = callback.context().asString();
                final String url = callback.url().asString();
                final McpRouteConfig route = context != null ? binding.routeByPrefix.get(context) : null;

                McpLifecycleClient client = null;
                if (route != null)
                {
                    client = supplyClient(route.id);
                }
                else if (context == null && clients.size() == 1)
                {
                    client = clients.values().iterator().next();
                }

                if (client != null)
                {
                    client.doClientFlushElicitCallback(traceId, authorization, url);
                }
            }
            else if (flushEx != null && flushEx.kind() == McpFlushExFW.KIND_ELICIT_RESPONSE)
            {
                final McpElicitResponseFlushExFW elicitResponse = flushEx.elicitResponse();
                final String requestId = elicitResponse.requestId().asString();
                final McpElicitAction action = elicitResponse.action().get();
                final McpLifecycleClient client = elicitClients.remove(requestId);

                if (client != null)
                {
                    client.doClientFlushElicitResponse(traceId, authorization, requestId, action);
                }
            }
        }

        private void onServerChallenge(
            ChallengeFW challenge)
        {
            resumePending = true;

            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();
            final McpChallengeExFW challengeEx = extension.sizeof() > 0
                ? mcpChallengeExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            if (challengeEx != null && challengeEx.kind() == McpChallengeExFW.KIND_RESUME)
            {
                final String16FW resumeId = challengeEx.resume().id();
                final String aggregate = resumeId != null && resumeId.length() != -1
                    ? resumeId.asString()
                    : null;

                if (aggregate != null && aggregating())
                {
                    McpAggregateEventId.decode(aggregate,
                        (prefix, eventId) -> onDecodeAggregateEventId(traceId, authorization, prefix, eventId));
                    onServerResumeRoutes(traceId);
                }
                else
                {
                    for (McpLifecycleClient client : clients.values())
                    {
                        client.doClientResume(traceId, authorization);
                    }
                }
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

            doServerWindow(traceId, 0L, 0);

            if (binding.cache != null)
            {
                binding.cache.register(() -> signaler.signalNow(
                    originId, routedId, replyId, traceId, SIGNAL_HYDRATE_COMPLETE, 0));
            }
            else
            {
                doEstablishToolkitClients(traceId);
            }
        }

        void driveHydrationBegin(
            long traceId)
        {
            state = McpState.openingInitial(state);
            doServerBeginDeferred(traceId);
        }

        void driveHydrationEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                binding.sessions.remove(sessionId);
                for (McpLifecycleClient client : clients.values())
                {
                    client.doClientEnd(traceId);
                }
            }
        }

        void driveHydrationAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                binding.sessions.remove(sessionId);
                for (McpLifecycleClient client : clients.values())
                {
                    client.doClientAbort(traceId);
                }
            }
        }

        private void doEstablishToolkitClients(
            long traceId)
        {
            final List<Long> routeIds = binding.resolveAll(authorization);
            pendingClients = routeIds.size();
            for (long routeId : routeIds)
            {
                final McpLifecycleClient client = supplyClient(routeId);
                client.doClientBegin(traceId);
            }

            doServerBeginDeferred(traceId);
        }

        private void onClientLifecycleOpened(
            long traceId)
        {
            if (pendingClients > 0)
            {
                pendingClients--;
                if (pendingClients == 0 && !McpState.initialClosed(state) && !McpState.replyClosed(state))
                {
                    doServerBeginDeferred(traceId);
                }
            }
        }

        private void onServerSignal(
            SignalFW signal)
        {
            if (signal.signalId() == SIGNAL_HYDRATE_COMPLETE)
            {
                doServerBeginDeferred(signal.traceId());
            }
        }

        private void doServerBeginDeferred(
            long traceId)
        {
            if (!McpState.replyOpened(state) && !McpState.replyClosed(state))
            {
                final int serverCapabilities = binding.serverCapabilities(authorization);
                final String sid = sessionId;
                final McpBeginExFW beginEx = mcpBeginExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(mcpTypeId)
                    .lifecycle(l -> l.sessionId(sid).capabilities(serverCapabilities))
                    .build();

                doServerBegin(traceId, beginEx);
            }
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

            binding.sessions.remove(sessionId);

            for (McpLifecycleClient client : clients.values())
            {
                client.doClientEnd(traceId);
            }

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

            binding.sessions.remove(sessionId);

            for (McpLifecycleClient client : clients.values())
            {
                client.doClientAbort(traceId);
            }

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

            binding.sessions.remove(sessionId);

            for (McpLifecycleClient client : clients.values())
            {
                client.doClientReset(traceId);
            }
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

        private void doServerReset(
            long traceId)
        {
            doServerReset(traceId, emptyRO);
        }

        private void doServerReset(
            long traceId,
            OctetsFW extension)
        {
            if (!McpState.initialClosed(state))
            {
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
                    authorization, extension);
                state = McpState.closedInitial(state);
            }
        }

        private void doServerChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            doChallenge(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, extension);
        }

        private void doServerFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            OctetsFW newExtension = extension;
            if (aggregating())
            {
                final McpFlushExFW flushEx =
                    mcpFlushExRO.wrap(extension.buffer(), extension.offset(), extension.limit());
                final String eventId = extractEventId(flushEx);
                if (eventId != null)
                {
                    final OctetsFW aggregateId = nextEventId();
                    if (aggregateId != null)
                    {
                        final McpFlushExFW rewritten = mcpFlushExRW
                            .wrap(flushExBuffer, 0, flushExBuffer.capacity())
                            .typeId(flushEx.typeId())
                            .inject(b -> injectFlushEx(b, flushEx, aggregateId))
                            .build();
                        newExtension = rewrittenExRO.wrap(rewritten.buffer(), rewritten.offset(), rewritten.limit());
                    }
                }
            }
            doFlush(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, reserved, newExtension);
        }

        @Override
        public void doNotifyListChanged(
            int kind,
            long traceId)
        {
            if (!McpState.replyOpened(state) || McpState.replyClosed(state))
            {
                return;
            }

            final McpFlushExFW flushEx = switch (kind)
            {
            case KIND_TOOLS_LIST -> mcpFlushExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .toolsListChanged(b -> {})
                .build();
            case KIND_PROMPTS_LIST -> mcpFlushExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .promptsListChanged(b -> {})
                .build();
            case KIND_RESOURCES_LIST -> mcpFlushExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .resourcesListChanged(b -> {})
                .build();
            default -> null;
            };

            if (flushEx != null)
            {
                doFlush(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, 0L, 0, flushEx);
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

    }

    final class McpLifecycleClient
    {
        private final McpLifecycleServer server;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final String prefix;

        private MessageConsumer sender;
        private int state;
        private boolean settled;
        String sessionId;
        long authorization;
        private String resumeId;
        private final List<McpRouteRequest> requests = new ArrayList<>();

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
            this.originId = server.routedId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = server.authorization;

            String prefix = null;
            for (McpAggregateRoute route : server.binding.aggregateRoutes)
            {
                if (route.routedId() == routedId)
                {
                    prefix = route.prefix();
                    break;
                }
            }
            this.prefix = prefix;
        }

        private void doClientFlushElicitCallback(
            long traceId,
            long authorization,
            String url)
        {
            final McpFlushExFW flushEx = mcpFlushExRW
                .wrap(flushExBuffer, 0, flushExBuffer.capacity())
                .typeId(mcpTypeId)
                .elicitCallback(b -> b.url(url))
                .build();

            doFlush(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0, flushEx);
        }

        private void doClientFlushElicitResponse(
            long traceId,
            long authorization,
            String requestId,
            McpElicitAction action)
        {
            final McpFlushExFW flushEx = mcpFlushExRW
                .wrap(flushExBuffer, 0, flushExBuffer.capacity())
                .typeId(mcpTypeId)
                .elicitResponse(b -> b.requestId(requestId).action(a -> a.set(action)))
                .build();

            doFlush(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0, flushEx);
        }

        void doClientBegin(
            long traceId)
        {
            if (!McpState.initialOpening(state))
            {
                if (server.hydration && server.binding.cache != null && server.binding.cache.guard != null)
                {
                    final String credentials = server.binding.routeCacheCredentials(routedId);
                    if (credentials != null)
                    {
                        authorization = server.binding.cache.guard.reauthorize(
                            traceId, server.binding.cache.bindingId, 0L, credentials);
                    }
                }

                final int clientCapabilities = server.clientCapabilities;
                final McpBeginExFW beginEx = mcpBeginExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(mcpTypeId)
                    .lifecycle(l -> l.capabilities(clientCapabilities))
                    .build();

                sender = newStream(this::onClientMessage, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, authorization, server.affinity, beginEx);
                state = McpState.openingInitial(state);
            }
        }

        void register(
            long traceId,
            McpRouteRequest request)
        {
            if (settled ||
                McpState.initialClosed(state) ||
                McpState.replyClosed(state))
            {
                request.onLifecycleSettled(traceId);
            }
            else
            {
                requests.add(request);
            }
        }

        private void settleRequests(
            long traceId)
        {
            if (!requests.isEmpty())
            {
                final List<McpRouteRequest> copy = new ArrayList<>(requests);
                requests.clear();
                copy.forEach(request -> request.onLifecycleSettled(traceId));
            }
        }

        private void doClientEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doEnd(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
                    authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                doAbort(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
                    authorization);
                state = McpState.closedInitial(state);
            }
        }

        private void doClientReset(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doReset(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId,
                    authorization, emptyRO);
                state = McpState.closedReply(state);
            }
        }

        private void doClientChallenge(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            doChallenge(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, extension);
        }

        private void doClientResume(
            long traceId,
            long authorization)
        {
            if (McpState.replyOpened(state))
            {
                final McpChallengeExFW resumeEx = mcpChallengeExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(mcpTypeId)
                    .resume(this::injectResumeId)
                    .build();
                doClientChallenge(traceId, authorization, resumeEx);
                resumeId = null;
            }
        }

        private void injectResumeId(
            McpResumeChallengeExFW.Builder builder)
        {
            if (resumeId != null)
            {
                builder.id(resumeId);
            }
        }

        private void doClientWindow(
            long traceId,
            long budgetId,
            int padding)
        {
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
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onClientFlush(flush);
                break;
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onClientChallenge(challenge);
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

        private void onClientFlush(
            FlushFW flush)
        {
            final OctetsFW extension = flush.extension();
            final boolean aggregating = server.aggregating();
            final boolean deferring = server.binding.cache != null && !server.hydration;
            final McpFlushExFW flushEx = aggregating || deferring
                ? mcpFlushExRO.wrap(extension.buffer(), extension.offset(), extension.limit())
                : null;
            if (aggregating)
            {
                server.onDecodeEventId(routedId, extractEventId(flushEx));
            }
            if (!(deferring && isListChangedKind(flushEx.kind())))
            {
                server.doServerFlush(flush.traceId(), flush.authorization(),
                    flush.budgetId(), flush.reserved(), extension);
            }
        }

        private void onClientChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            settleLifecycle(traceId);

            final McpChallengeExFW challengeEx = extension.sizeof() > 0
                ? mcpChallengeExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;
            if (challengeEx != null &&
                challengeEx.kind() == McpChallengeExFW.KIND_ELICIT_CREATE)
            {
                final String requestId = challengeEx.elicitCreate().requestId().asString();
                if (requestId != null)
                {
                    server.elicitClients.put(requestId, this);
                }
            }

            server.doServerChallenge(traceId, authorization, injectChallengeContext(extension));
        }

        private OctetsFW injectChallengeContext(
            OctetsFW extension)
        {
            OctetsFW relayed = extension;
            final McpChallengeExFW challengeEx = extension.sizeof() > 0
                ? mcpChallengeExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            if (prefix != null &&
                challengeEx != null &&
                challengeEx.kind() == McpChallengeExFW.KIND_ELICIT_CREATE &&
                challengeEx.elicitCreate().requestId().asString() == null)
            {
                final McpElicitCreateChallengeExFW elicitCreate = challengeEx.elicitCreate();
                final String id = elicitCreate.id().asString();
                final String url = elicitCreate.url().asString();
                final McpChallengeExFW rewritten = mcpChallengeExRW
                    .wrap(challengeExBuffer, 0, challengeExBuffer.capacity())
                    .typeId(mcpTypeId)
                    .elicitCreate(b -> b.id(id).url(url).context(prefix))
                    .build();
                relayed = rewrittenExRO.wrap(rewritten.buffer(), rewritten.offset(), rewritten.limit());
            }

            return relayed;
        }

        private void settleLifecycle(
            long traceId)
        {
            if (!settled)
            {
                settled = true;
                server.onClientLifecycleOpened(traceId);
            }
        }

        private void onClientBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
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

            settleLifecycle(traceId);

            settleRequests(traceId);

            if (resumeId != null || server.resumePending)
            {
                doClientResume(traceId, authorization);
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
            settleRequests(traceId);
            doClientEnd(traceId);
            server.clients.remove(routedId, this);
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
            settleRequests(traceId);
            doClientAbort(traceId);
            server.clients.remove(routedId, this);
            if (!(server.hydration && sessionId == null))
            {
                server.doServerAbort(traceId);
            }
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
            final OctetsFW extension = reset.extension();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;

            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            state = McpState.closedInitial(state);
            settleRequests(traceId);
            doClientReset(traceId);
            server.clients.remove(routedId, this);

            final boolean bearer = extension.sizeof() > 0;
            if (!(server.hydration && sessionId == null))
            {
                if (bearer)
                {
                    settleLifecycle(traceId);
                }
                else
                {
                    server.doServerReset(traceId, extension);
                }
            }
        }
    }

    private String extractEventId(
        McpFlushExFW flushEx)
    {
        final String16FW id = switch (flushEx.kind())
        {
        case McpFlushExFW.KIND_RESUMABLE -> flushEx.resumable().id();
        case McpFlushExFW.KIND_TOOLS_LIST_CHANGED -> flushEx.toolsListChanged().id();
        case McpFlushExFW.KIND_PROMPTS_LIST_CHANGED -> flushEx.promptsListChanged().id();
        case McpFlushExFW.KIND_RESOURCES_LIST_CHANGED -> flushEx.resourcesListChanged().id();
        case McpFlushExFW.KIND_PROGRESS -> flushEx.progress().id();
        case McpFlushExFW.KIND_ELICIT_COMPLETE -> flushEx.elicitComplete().id();
        default -> null;
        };
        return id != null && id.length() != -1 ? id.asString() : null;
    }

    private static boolean isListChangedKind(
        int kind)
    {
        return kind == McpFlushExFW.KIND_TOOLS_LIST_CHANGED ||
            kind == McpFlushExFW.KIND_PROMPTS_LIST_CHANGED ||
            kind == McpFlushExFW.KIND_RESOURCES_LIST_CHANGED;
    }

    private void injectFlushEx(
        McpFlushExFW.Builder builder,
        McpFlushExFW flushEx,
        OctetsFW aggregate)
    {
        switch (flushEx.kind())
        {
        case McpFlushExFW.KIND_RESUMABLE:
            builder.resumable(b -> injectResumableFlushEx(b, aggregate));
            break;
        case McpFlushExFW.KIND_TOOLS_LIST_CHANGED:
            builder.toolsListChanged(b -> injectToolsListChangedFlushEx(b, aggregate));
            break;
        case McpFlushExFW.KIND_PROMPTS_LIST_CHANGED:
            builder.promptsListChanged(b -> injectPromptsListChangedFlushEx(b, aggregate));
            break;
        case McpFlushExFW.KIND_RESOURCES_LIST_CHANGED:
            builder.resourcesListChanged(b -> injectResourcesListChangedFlushEx(b, aggregate));
            break;
        case McpFlushExFW.KIND_PROGRESS:
            builder.progress(b -> injectProgressFlushEx(b, flushEx.progress(), aggregate));
            break;
        case McpFlushExFW.KIND_ELICIT_COMPLETE:
            builder.elicitComplete(b -> injectElicitCompleteFlushEx(b, flushEx.elicitComplete(), aggregate));
            break;
        default:
            break;
        }
    }

    private void injectResumableFlushEx(
        McpResumableFlushExFW.Builder builder,
        OctetsFW aggregate)
    {
        builder.id(aggregate.buffer(), aggregate.offset(), aggregate.sizeof());
    }

    private void injectToolsListChangedFlushEx(
        McpToolsListChangedFlushExFW.Builder builder,
        OctetsFW aggregate)
    {
        builder.id(aggregate.buffer(), aggregate.offset(), aggregate.sizeof());
    }

    private void injectPromptsListChangedFlushEx(
        McpPromptsListChangedFlushExFW.Builder builder,
        OctetsFW aggregate)
    {
        builder.id(aggregate.buffer(), aggregate.offset(), aggregate.sizeof());
    }

    private void injectResourcesListChangedFlushEx(
        McpResourcesListChangedFlushExFW.Builder builder,
        OctetsFW aggregate)
    {
        builder.id(aggregate.buffer(), aggregate.offset(), aggregate.sizeof());
    }

    private void injectProgressFlushEx(
        McpProgressFlushExFW.Builder builder,
        McpProgressFlushExFW progress,
        OctetsFW aggregate)
    {
        final String token = progress.token().asString();
        final String message = progress.message().asString();
        builder.id(aggregate.buffer(), aggregate.offset(), aggregate.sizeof())
            .token(token)
            .progress(progress.progress())
            .total(progress.total());
        if (message != null)
        {
            builder.message(message);
        }
    }

    private void injectElicitCompleteFlushEx(
        McpElicitCompleteFlushExFW.Builder builder,
        McpElicitCompleteFlushExFW elicitComplete,
        OctetsFW aggregate)
    {
        final McpElicitStatus status = elicitComplete.status().get();
        builder.id(aggregate.buffer(), aggregate.offset(), aggregate.sizeof()).status(s -> s.set(status));
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
        int reserved,
        OctetsFW extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
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
        int reserved,
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doChallenge(
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
        final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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

        receiver.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
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
