/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.a2a.internal.stream;

import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_AGENT_CARD;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_EXTENDED_AGENT_CARD;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_SEND;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_STREAM;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_CREATE;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_DELETE;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_GET;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_LIST;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_NOTIFY;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_CANCEL;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_GET;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_LIST;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_SUBSCRIBE;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.util.Flags.COMPLETE;

import java.nio.charset.StandardCharsets;
import java.util.function.LongUnaryOperator;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.A2aConfiguration;
import io.aklivity.zilla.runtime.binding.a2a.internal.cache.A2aAgentCache;
import io.aklivity.zilla.runtime.binding.a2a.internal.cache.A2aAgentCards;
import io.aklivity.zilla.runtime.binding.a2a.internal.cache.A2aAgentEntry;
import io.aklivity.zilla.runtime.binding.a2a.internal.cache.A2aCardSink;
import io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aProxyBindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aProxyResolution;
import io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aProxyRouteConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBearerError;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aResetExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class A2aProxyFactory implements A2aStreamFactory
{
    private static final String A2A_TYPE_NAME = "a2a";

    private static final String BEARER_REALM = "a2a";
    private static final int ERROR_CODE_NOT_FOUND = 404;
    private static final int ERROR_CODE_INVALID_PARAMS = -32602;
    private static final int ERROR_CODE_INTERNAL = -32603;
    private static final String ERROR_MESSAGE_AGENT_NOT_FOUND = "Agent not found";
    private static final String ERROR_MESSAGE_INVALID_PARAMS = "Invalid params";
    private static final String ERROR_MESSAGE_INTERNAL = "Internal error";

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final A2aBeginExFW a2aBeginExRO = new A2aBeginExFW();
    private final A2aResetExFW a2aResetExRO = new A2aResetExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final A2aBeginExFW.Builder a2aBeginExRW = new A2aBeginExFW.Builder();
    private final A2aResetExFW.Builder a2aResetExRW = new A2aResetExFW.Builder();

    private final A2aConfiguration config;
    private final EngineContext context;
    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final BufferPool decodePool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final long affinity;
    private final int a2aTypeId;
    private final int decodeMax;

    private final Long2ObjectHashMap<A2aProxyBindingConfig> bindings = new Long2ObjectHashMap<>();
    private final Long2ObjectHashMap<A2aAgentCache> caches = new Long2ObjectHashMap<>();
    private final A2aDiscoveryFactory discovery;

    public A2aProxyFactory(
        A2aConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.affinity = context.affinity();
        this.a2aTypeId = context.supplyTypeId(A2A_TYPE_NAME);
        this.decodeMax = decodePool.slotCapacity();
        this.discovery = new A2aDiscoveryFactory(config, context);
    }

    public A2aAgentCards cards(
        long bindingId)
    {
        return caches.get(bindingId);
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        final A2aProxyBindingConfig attached = new A2aProxyBindingConfig(binding, context);
        bindings.put(binding.id, attached);

        if (attached.cache != null)
        {
            final A2aAgentCache cache = new A2aAgentCache(binding, attached, config, context, this::hydrate);
            caches.put(binding.id, cache);
            cache.start();

            if (attached.discovery != null)
            {
                discovery.attach(attached, cache);
            }
        }
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
        discovery.detach(bindingId);

        final A2aAgentCache cache = caches.remove(bindingId);
        if (cache != null)
        {
            cache.stop();
        }
    }

    @Override
    public MessageConsumer newStream(
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
        final long authorization = begin.authorization();

        final A2aProxyBindingConfig binding = bindings.get(routedId);
        final OctetsFW extension = begin.extension();

        MessageConsumer newStream = null;

        if (binding != null && extension.sizeof() > 0)
        {
            final A2aBeginExFW beginEx = a2aBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());

            if (beginEx != null)
            {
                final String agent = agentOf(beginEx);

                if (binding.discovers(agent))
                {
                    newStream = discovery.newStream(beginEx, begin, sender);
                }
                else
                {
                    final A2aProxyResolution resolution = binding.resolve(agent, authorization);
                    final A2aAgentCache cache = caches.get(routedId);
                    final A2aAgentEntry entry =
                        beginEx.kind() == KIND_AGENT_CARD && cache != null ? cache.entryOf(agent) : null;

                    final A2aProxyStream stream = switch (resolution.outcome)
                    {
                    case ROUTED -> entry != null && entry.route == resolution.route
                        ? new A2aCardServer(sender, originId, routedId, initialId, binding, cache, entry)
                        : new A2aRelayServer(sender, originId, routedId, initialId, resolution.route);
                    default -> new A2aRejectServer(sender, originId, routedId, initialId, resolution);
                    };

                    newStream = stream::onAppMessage;
                }
            }
        }

        return newStream;
    }

    private static String agentOf(
        A2aBeginExFW beginEx)
    {
        return switch (beginEx.kind())
        {
        case KIND_AGENT_CARD -> beginEx.agentCard().agent().asString();
        case KIND_MESSAGE_SEND -> beginEx.messageSend().agent().asString();
        case KIND_MESSAGE_STREAM -> beginEx.messageStream().agent().asString();
        case KIND_TASKS_GET -> beginEx.tasksGet().agent().asString();
        case KIND_TASKS_LIST -> beginEx.tasksList().agent().asString();
        case KIND_TASKS_CANCEL -> beginEx.tasksCancel().agent().asString();
        case KIND_TASKS_SUBSCRIBE -> beginEx.tasksSubscribe().agent().asString();
        case KIND_PUSH_CONFIG_CREATE -> beginEx.pushConfigCreate().agent().asString();
        case KIND_PUSH_CONFIG_GET -> beginEx.pushConfigGet().agent().asString();
        case KIND_PUSH_CONFIG_LIST -> beginEx.pushConfigList().agent().asString();
        case KIND_PUSH_CONFIG_DELETE -> beginEx.pushConfigDelete().agent().asString();
        case KIND_EXTENDED_AGENT_CARD -> beginEx.extendedAgentCard().agent().asString();
        case KIND_PUSH_NOTIFY -> beginEx.pushNotify().agent().asString();
        default -> null;
        };
    }

    private static int contentLengthOf(
        A2aBeginExFW beginEx)
    {
        return switch (beginEx.kind())
        {
        case KIND_MESSAGE_SEND -> beginEx.messageSend().contentLength();
        case KIND_MESSAGE_STREAM -> beginEx.messageStream().contentLength();
        case KIND_TASKS_LIST -> beginEx.tasksList().contentLength();
        case KIND_PUSH_CONFIG_CREATE -> beginEx.pushConfigCreate().contentLength();
        case KIND_PUSH_NOTIFY -> beginEx.pushNotify().contentLength();
        default -> 0;
        };
    }

    private Runnable hydrate(
        A2aAgentEntry entry,
        long authorization,
        A2aCardSink sink)
    {
        final A2aCardHydrate hydrate = new A2aCardHydrate(entry, authorization, sink);
        hydrate.doNetBegin(context.supplyTraceId());
        return () -> hydrate.doNetAbort(context.supplyTraceId());
    }

    private abstract class A2aProxyStream
    {
        protected final MessageConsumer sender;
        protected final long originId;
        protected final long routedId;
        protected final long initialId;
        protected final long replyId;

        protected int state;

        protected long initialSeq;
        protected long initialAck;
        protected int initialMax;

        protected long replySeq;
        protected long replyAck;
        protected int replyMax;
        protected long replyBud;
        protected int replyPad;

        A2aProxyStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        void onAppMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onAppBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onAppData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onAppEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAppAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onAppFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAppWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAppReset(reset);
                break;
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            state = A2aState.openingInitial(state);

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();

            onAppBegin(begin.traceId(), begin.authorization(), begin.extension());
        }

        abstract void onAppBegin(
            long traceId,
            long authorization,
            OctetsFW extension);

        void onAppData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;
        }

        void onAppFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + flush.reserved();

            assert initialAck <= initialSeq;
        }

        void onAppEnd(
            EndFW end)
        {
            final long sequence = end.sequence();

            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = A2aState.closedInitial(state);

            onAppEnded(end.traceId(), end.authorization(), end.extension());
        }

        void onAppEnded(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
        }

        void onAppAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();

            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = A2aState.closedReply(A2aState.closedInitial(state));

            onAppAborted(abort.traceId(), abort.authorization(), abort.extension());
        }

        void onAppAborted(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
        }

        void onAppWindow(
            WindowFW window)
        {
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();

            assert acknowledge <= window.sequence();
            assert window.sequence() <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum + acknowledge >= replyMax + replyAck;

            state = A2aState.openedReply(state);
            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = window.budgetId();
            replyPad = window.padding();

            assert replyAck <= replySeq;

            onAppWindowed(window.traceId(), window.authorization(), window.budgetId(), window.padding(),
                window.capabilities());
        }

        void onAppWindowed(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
        }

        void onAppReset(
            ResetFW reset)
        {
            assert reset.sequence() <= replySeq;
            assert reset.acknowledge() >= replyAck;

            state = A2aState.closedReply(state);

            onAppResetted(reset.traceId(), reset.authorization(), reset.extension());
        }

        void onAppResetted(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
        }

        void doAppBegin(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            state = A2aState.openingReply(state);

            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, extension);
        }

        int replyWindow()
        {
            return Math.max(replyMax - (int) (replySeq - replyAck) - replyPad, 0);
        }

        int doAppData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int length = Math.min(replyWindow(), limit - offset);

            if (length > 0)
            {
                final int reserved = length + replyPad;

                doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, COMPLETE, replyBud, reserved, buffer, offset, length, null);

                replySeq += reserved;

                assert replySeq <= replyAck + replyMax;
            }

            return offset + length;
        }

        void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, null);
            }
        }

        void doAppAbort(
            long traceId,
            long authorization)
        {
            if (A2aState.replyOpening(state) && !A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, null);
            }
        }

        void doAppReset(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (!A2aState.initialClosed(state))
            {
                state = A2aState.closedInitial(state);
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, extension);
            }
        }

        void doAppRejected(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (!A2aState.replyOpening(state))
            {
                initialMax = decodeMax;

                doAppBegin(traceId, authorization, null);
                doAppWindow(traceId, authorization, 0L, 0, 0);
            }

            doAppReset(traceId, authorization, extension);
        }

        void doAppResetError(
            long traceId,
            long authorization,
            int code,
            String message)
        {
            final A2aResetExFW resetEx = a2aResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(a2aTypeId)
                .error(e -> e.code(code).message(message))
                .build();

            doAppRejected(traceId, authorization, resetEx);
        }

        void doAppResetBearer(
            long traceId,
            long authorization,
            String scopes)
        {
            final A2aResetExFW resetEx = a2aResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(a2aTypeId)
                .bearer(b -> b.realm(BEARER_REALM).scopes(scopes).error(e -> e.set(A2aBearerError.INSUFFICIENT_SCOPE)))
                .build();

            doAppRejected(traceId, authorization, resetEx);
        }

        void doAppResetFailure(
            long traceId,
            long authorization,
            byte[] failure)
        {
            if (failure != null)
            {
                final A2aResetExFW resetEx = a2aResetExRO.wrap(new UnsafeBufferEx(failure), 0, failure.length);

                doAppRejected(traceId, authorization, resetEx);
            }
            else
            {
                doAppResetError(traceId, authorization, ERROR_CODE_INTERNAL, ERROR_MESSAGE_INTERNAL);
            }
        }

        void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding, capabilities);
        }
    }

    private final class A2aRejectServer extends A2aProxyStream
    {
        private final A2aProxyResolution resolution;

        private int contentLength;

        A2aRejectServer(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            A2aProxyResolution resolution)
        {
            super(sender, originId, routedId, initialId);
            this.resolution = resolution;
        }

        @Override
        void onAppBegin(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            contentLength = contentLengthOf(a2aBeginExRO.wrap(extension.buffer(), extension.offset(), extension.limit()));

            if (contentLength != 0)
            {
                initialMax = decodeMax;

                doAppBegin(traceId, authorization, null);
                doAppWindow(traceId, authorization, 0L, 0, 0);
            }
            else
            {
                doReject(traceId, authorization);
            }
        }

        @Override
        void onAppData(
            DataFW data)
        {
            super.onAppData(data);

            final long traceId = data.traceId();
            final long authorization = data.authorization();

            initialAck = initialSeq;
            doAppWindow(traceId, authorization, 0L, 0, 0);

            if (contentLength < 0 || initialSeq >= contentLength)
            {
                doReject(traceId, authorization);
            }
        }

        private void doReject(
            long traceId,
            long authorization)
        {
            switch (resolution.outcome)
            {
            case FORBIDDEN:
                doAppResetBearer(traceId, authorization, String.join(" ", resolution.roles));
                break;
            case AGENT_NOT_FOUND:
                doAppResetError(traceId, authorization, ERROR_CODE_NOT_FOUND, ERROR_MESSAGE_AGENT_NOT_FOUND);
                break;
            default:
                doAppResetError(traceId, authorization, ERROR_CODE_INVALID_PARAMS, ERROR_MESSAGE_INVALID_PARAMS);
                break;
            }
        }
    }

    private final class A2aCardServer extends A2aProxyStream
    {
        private final A2aProxyBindingConfig binding;
        private final A2aAgentCache cache;
        private final A2aAgentEntry entry;

        private DirectBufferEx cardBuffer;
        private int cardLimit;
        private int cardDelivered;

        A2aCardServer(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            A2aProxyBindingConfig binding,
            A2aAgentCache cache,
            A2aAgentEntry entry)
        {
            super(sender, originId, routedId, initialId);
            this.binding = binding;
            this.cache = cache;
            this.entry = entry;
        }

        @Override
        void onAppBegin(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            cache.request(entry, () -> onCardReady(traceId, authorization));
        }

        @Override
        void onAppAborted(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            cardBuffer = null;
        }

        @Override
        void onAppResetted(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            cardBuffer = null;
        }

        @Override
        void onAppWindowed(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
            if (cardBuffer != null)
            {
                flushCard(traceId, authorization);
            }
        }

        private void onCardReady(
            long traceId,
            long authorization)
        {
            if (!A2aState.replyClosed(state))
            {
                final byte[] card = entry.card() != null ? entry.render(authorization, binding.guard) : null;

                if (entry.card() == null)
                {
                    doAppResetFailure(traceId, authorization, entry.failure());
                }
                else if (card == null)
                {
                    doAppResetBearer(traceId, authorization, String.join(" ", entry.requiredRoles()));
                }
                else
                {
                    cardBuffer = new UnsafeBufferEx(card);
                    cardLimit = card.length;

                    initialMax = decodeMax;

                    doAppBegin(traceId, authorization, null);
                    doAppWindow(traceId, authorization, 0L, 0, 0);
                    flushCard(traceId, authorization);
                }
            }
        }

        private void flushCard(
            long traceId,
            long authorization)
        {
            cardDelivered = doAppData(traceId, authorization, cardBuffer, cardDelivered, cardLimit);

            if (cardDelivered == cardLimit)
            {
                cardBuffer = null;
                doAppEnd(traceId, authorization);
            }
        }
    }

    private final class A2aRelayServer extends A2aProxyStream
    {
        private final A2aProxyRouteConfig route;

        private A2aRelayClient client;

        A2aRelayServer(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            A2aProxyRouteConfig route)
        {
            super(sender, originId, routedId, initialId);
            this.route = route;
        }

        @Override
        void onAppBegin(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            client = new A2aRelayClient(this, routedId, route.id);
            client.doNetBegin(traceId, authorization, extension);
        }

        @Override
        void onAppData(
            DataFW data)
        {
            super.onAppData(data);

            client.doNetData(data.traceId(), data.authorization(), data.flags(), data.budgetId(), data.reserved(),
                data.payload(), data.extension());
        }

        @Override
        void onAppFlush(
            FlushFW flush)
        {
            super.onAppFlush(flush);

            client.doNetFlush(flush.traceId(), flush.authorization(), flush.budgetId(), flush.reserved(),
                flush.extension());
        }

        @Override
        void onAppEnded(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            client.doNetEnd(traceId, authorization, extension);
        }

        @Override
        void onAppAborted(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            client.doNetAbort(traceId, authorization, extension);
        }

        @Override
        void onAppWindowed(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
            client.doNetWindow(traceId, authorization, replyAck, replyMax, budgetId, padding, capabilities);
        }

        @Override
        void onAppResetted(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            client.doNetReset(traceId, authorization, extension);
        }

        void onNetBegin(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            doAppBegin(traceId, authorization, extension);
        }

        void onNetData(
            DataFW data)
        {
            final OctetsFW payload = data.payload();
            final long sequence = replySeq;
            final int reserved = data.reserved();

            doData(sender, originId, routedId, replyId, sequence, replyAck, replyMax,
                data.traceId(), data.authorization(), data.flags(), data.budgetId(), reserved,
                payload != null ? payload.buffer() : null, payload != null ? payload.offset() : 0,
                payload != null ? payload.sizeof() : -1, data.extension());

            replySeq = sequence + reserved;
        }

        void onNetFlush(
            FlushFW flush)
        {
            doFlush(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                flush.traceId(), flush.authorization(), flush.budgetId(), flush.reserved(), flush.extension());

            replySeq += flush.reserved();
        }

        void onNetEnd(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, extension);
            }
        }

        void onNetAbort(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, extension);
            }
        }

        void onNetWindow(
            long traceId,
            long authorization,
            long acknowledge,
            int maximum,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = acknowledge;
            initialMax = maximum;

            doAppWindow(traceId, authorization, budgetId, padding, capabilities);
        }

        void onNetReset(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            doAppReset(traceId, authorization, extension);
        }
    }

    private final class A2aRelayClient
    {
        private final A2aRelayServer server;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private MessageConsumer net;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        A2aRelayClient(
            A2aRelayServer server,
            long originId,
            long routedId)
        {
            this.server = server;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        void doNetBegin(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            state = A2aState.openingInitial(state);

            net = newStream(this::onNetMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, extension);

            if (net == null)
            {
                server.doAppResetError(traceId, authorization, ERROR_CODE_INTERNAL, ERROR_MESSAGE_INTERNAL);
            }
        }

        void doNetData(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            OctetsFW payload,
            OctetsFW extension)
        {
            doData(net, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, flags, budgetId, reserved,
                payload != null ? payload.buffer() : null, payload != null ? payload.offset() : 0,
                payload != null ? payload.sizeof() : -1, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        void doNetFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(net, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, reserved, extension);

            initialSeq += reserved;
        }

        void doNetEnd(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!A2aState.initialClosed(state))
            {
                state = A2aState.closedInitial(state);
                doEnd(net, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                    extension);
            }
        }

        void doNetAbort(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!A2aState.initialClosed(state))
            {
                state = A2aState.closedInitial(state);
                doAbort(net, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                    extension);
            }
        }

        void doNetWindow(
            long traceId,
            long authorization,
            long acknowledge,
            int maximum,
            long budgetId,
            int padding,
            int capabilities)
        {
            replyAck = acknowledge;
            replyMax = maximum;

            doWindow(net, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding, capabilities);
        }

        void doNetReset(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                doReset(net, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, extension);
            }
        }

        private void onNetMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                state = A2aState.openedReply(state);
                server.onNetBegin(begin.traceId(), begin.authorization(), begin.extension());
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                replySeq = data.sequence() + data.reserved();
                server.onNetData(data);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                replySeq = flush.sequence() + flush.reserved();
                server.onNetFlush(flush);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                state = A2aState.closedReply(state);
                server.onNetEnd(end.traceId(), end.authorization(), end.extension());
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                state = A2aState.closedReply(state);
                server.onNetAbort(abort.traceId(), abort.authorization(), abort.extension());
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                initialAck = window.acknowledge();
                initialMax = window.maximum();
                server.onNetWindow(window.traceId(), window.authorization(), window.acknowledge(), window.maximum(),
                    window.budgetId(), window.padding(), window.capabilities());
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                state = A2aState.closedInitial(state);
                server.onNetReset(reset.traceId(), reset.authorization(), reset.extension());
                break;
            default:
                break;
            }
        }
    }

    private final class A2aCardHydrate
    {
        private final A2aAgentEntry entry;
        private final long authorization;
        private final A2aCardSink sink;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private MessageConsumer net;
        private int state;
        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        A2aCardHydrate(
            A2aAgentEntry entry,
            long authorization,
            A2aCardSink sink)
        {
            this.entry = entry;
            this.authorization = authorization;
            this.sink = sink;
            this.originId = entry.bindingId;
            this.routedId = entry.route.id;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        void doNetBegin(
            long traceId)
        {
            if (entry.route.authorized(authorization))
            {
                final A2aBeginExFW beginEx = a2aBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(a2aTypeId)
                    .agentCard(c -> c.agent(entry.agent))
                    .build();

                state = A2aState.openingInitial(state);

                net = newStream(this::onNetMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, beginEx);
            }

            if (net == null)
            {
                sink.onFailure(null);
            }
        }

        void doNetAbort(
            long traceId)
        {
            if (net != null && !A2aState.closed(state))
            {
                cleanup(traceId, null);
            }
        }

        private void onNetMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onNetData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetReset(reset);
                break;
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            state = A2aState.openedReply(state);
            decodeSlot = decodePool.acquire(replyId);

            if (decodeSlot == NO_SLOT)
            {
                cleanup(begin.traceId(), null);
            }
            else
            {
                replyMax = decodePool.slotCapacity();
                doWindow(net, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    begin.traceId(), authorization, 0L, 0, 0);
            }
        }

        private void onNetData(
            DataFW data)
        {
            final OctetsFW payload = data.payload();
            replySeq = data.sequence() + data.reserved();

            if (payload != null)
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                decodeSlotOffset += payload.sizeof();
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final String card = new String(slotBytes(), StandardCharsets.UTF_8);

            state = A2aState.closedReply(state);
            cleanupDecodeSlot();

            state = A2aState.closedInitial(state);
            doEnd(net, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization, null);

            sink.onCard(card);
        }

        private byte[] slotBytes()
        {
            final byte[] bytes = new byte[decodeSlotOffset];
            decodePool.buffer(decodeSlot).getBytes(0, bytes);
            return bytes;
        }

        private void onNetAbort(
            AbortFW abort)
        {
            state = A2aState.closedReply(state);
            cleanup(abort.traceId(), null);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final OctetsFW extension = reset.extension();
            final byte[] failure = extension.sizeof() > 0 ? new byte[extension.sizeof()] : null;

            if (failure != null)
            {
                extension.buffer().getBytes(extension.offset(), failure);
            }

            state = A2aState.closedInitial(state);
            cleanup(reset.traceId(), failure);
        }

        private void cleanup(
            long traceId,
            byte[] failure)
        {
            cleanupDecodeSlot();

            if (!A2aState.initialClosed(state))
            {
                state = A2aState.closedInitial(state);
                doAbort(net, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization, null);
            }

            if (!A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                doReset(net, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, null);
            }

            sink.onFailure(failure);
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
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

        if (receiver != null)
        {
            receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

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
        final BeginFW.Builder builder = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final BeginFW begin = builder.build();

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
        int length,
        Flyweight extension)
    {
        final DataFW.Builder builder = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
            .payload(payload, offset, length);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final DataFW data = builder.build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
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
        final FlushFW.Builder builder = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .reserved(reserved);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final FlushFW flush = builder.build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
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
        long authorization,
        Flyweight extension)
    {
        final EndFW.Builder builder = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final EndFW end = builder.build();

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
        long authorization,
        Flyweight extension)
    {
        final AbortFW.Builder builder = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final AbortFW abort = builder.build();

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
        final ResetFW.Builder builder = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final ResetFW reset = builder.build();

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
        int padding,
        int capabilities)
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
            .capabilities(capabilities)
            .build();

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }
}
