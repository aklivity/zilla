/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.stream;

import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiBinding;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiConfiguration;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiRouteConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.AsyncapiBeginExFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class AsyncapiProxyFactory implements AsyncapiStreamFactory
{
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(), 0, 0);
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final FlushFW flushRO = new FlushFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final AsyncapiBeginExFW asyncapiBeginExRO = new AsyncapiBeginExFW();

    private final AsyncapiBeginExFW.Builder asyncapiBeginExRW = new AsyncapiBeginExFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BufferPool bufferPool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final Function<String, Integer> supplyTypeId;
    private final Long2ObjectHashMap<AsyncapiBindingConfig> bindings;
    private final int asyncapiTypeId;

    private final AsyncapiConfiguration config;

    public AsyncapiProxyFactory(
        AsyncapiConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.bufferPool = context.bufferPool();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTypeId = context::supplyTypeId;
        this.supplyTraceId = context::supplyTraceId;
        this.bindings = new Long2ObjectHashMap<>();
        this.asyncapiTypeId = context.supplyTypeId(AsyncapiBinding.NAME);
    }


    @Override
    public int routedTypeId()
    {
        return asyncapiTypeId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        AsyncapiBindingConfig asyncapiBinding = new AsyncapiBindingConfig(binding, config.targetRouteId());
        bindings.put(binding.id, asyncapiBinding);
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
        MessageConsumer receiver)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long affinity = begin.affinity();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();
        final AsyncapiBeginExFW asyncapiBeginEx = extension.get(asyncapiBeginExRO::tryWrap);

        final AsyncapiBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null && !binding.isCompositeOriginId(originId))
        {
            final long apiId = asyncapiBeginEx.apiId();
            final String operationId = asyncapiBeginEx.operationId().asString();

            final long compositeResolvedId = binding.resolveResolvedId(apiId);
            final AsyncapiRouteConfig route = binding.resolve(authorization);

            if (compositeResolvedId != -1 && route != null)
            {
                newStream = new AsyncapiServerStream(
                    receiver,
                    originId,
                    routedId,
                    initialId,
                    affinity,
                    authorization,
                    route.id,
                    compositeResolvedId,
                    operationId)::onAsyncapiServerMessage;
            }
        }

        return newStream;
    }

    private final class AsyncapiServerStream
    {
        private final CompositeStream delegate;
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBud;
        private int replyCap;

        private AsyncapiServerStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization,
            long resolvedId,
            long compositeResolvedId,
            String operationId)
        {
            this.delegate =
                new CompositeStream(this, routedId, resolvedId, compositeResolvedId, authorization, operationId);
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
        }

        private void onAsyncapiServerMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onAsyncapiServerBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onAsyncapiServerData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onAsyncapiServerEnd(end);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onAsyncapiServerFlush(flush);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAsyncapiServerAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAsyncapiServerWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAsyncapiServerReset(reset);
                break;
            default:
                break;
            }
        }

        private void onAsyncapiServerBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();
            final OctetsFW extension = begin.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            state = AsyncapiState.openingInitial(state);

            assert initialAck <= initialSeq;

            final AsyncapiBeginExFW asyncapiBeginEx = extension.get(asyncapiBeginExRO::tryWrap);

            delegate.doCompositeBegin(traceId, asyncapiBeginEx.extension());
        }

        private void onAsyncapiServerData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            delegate.doCompositeData(traceId, authorization, budgetId, reserved, flags, payload, extension);
        }

        private void onAsyncapiServerEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final OctetsFW extension = end.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = AsyncapiState.closeInitial(state);

            assert initialAck <= initialSeq;

            delegate.doCompositeEnd(traceId, extension);
        }

        private void onAsyncapiServerFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            delegate.doCompositeFlush(traceId, reserved, extension);
        }

        private void onAsyncapiServerAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final OctetsFW extension = abort.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = AsyncapiState.closeInitial(state);

            assert initialAck <= initialSeq;

            delegate.doCompositeAbort(traceId, extension);
        }

        private void doAsyncapiServerReset(
            long traceId)
        {
            if (!AsyncapiState.initialClosed(state))
            {
                state = AsyncapiState.closeInitial(state);

                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doAsyncapiServerWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding)
        {
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;

            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding);
        }

        private void doAsyncapiServerBegin(
            long traceId,
            OctetsFW extension)
        {
            state = AsyncapiState.openingReply(state);

            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, extension);
        }

        private void doAsyncapiServerData(
            long traceId,
            int flag,
            int reserved,
            OctetsFW payload,
            Flyweight extension)
        {

            doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, replyBud, flag, reserved, payload, extension);

            replySeq += reserved;
        }

        private void doAsyncapiServerFlush(
            long traceId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, replyBud, reserved, extension);
        }

        private void doAsyncapiServerEnd(
            long traceId,
            OctetsFW extension)
        {
            if (AsyncapiState.replyOpening(state) && !AsyncapiState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, extension);
            }

            state = AsyncapiState.closeReply(state);
        }

        private void doAsyncapiServerAbort(
            long traceId)
        {
            if (AsyncapiState.replyOpening(state) && !AsyncapiState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }

            state = AsyncapiState.closeInitial(state);
        }

        private void onAsyncapiServerReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            state = AsyncapiState.closeReply(state);

            assert replyAck <= replySeq;

            cleanup(traceId);
        }

        private void onAsyncapiServerWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = budgetId;
            replyPad = padding;
            replyCap = capabilities;
            state = AsyncapiState.closingReply(state);

            assert replyAck <= replySeq;

            delegate.doCompositeWindow(traceId, acknowledge, budgetId, padding);
        }

        private void cleanup(
            long traceId)
        {
            doAsyncapiServerReset(traceId);
            doAsyncapiServerAbort(traceId);

            delegate.cleanup(traceId);
        }
    }

    final class CompositeStream
    {
        private final String operationId;
        private final long originId;
        private final long routedId;
        private final long authorization;

        private AsyncapiClientStream asyncapiClient;
        private long initialId;
        private long replyId;
        private MessageConsumer receiver;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long initialBud;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private CompositeStream(
            AsyncapiServerStream delegate,
            long originId,
            long resolvedId,
            long compositeResolvedId,
            long authorization,
            String operationId)
        {
            this.asyncapiClient = new AsyncapiClientStream(delegate, originId, resolvedId, authorization, operationId);
            this.originId = originId;
            this.routedId = compositeResolvedId;
            this.receiver = MessageConsumer.NOOP;
            this.authorization = authorization;
            this.operationId = operationId;
        }

        private void onCompositeMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onCompositeBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onCompositeData(data);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onCompositeFlush(flush);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onCompositeEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onCompositeAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onCompositeReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onCompositeWindow(window);
                break;
            default:
                break;
            }
        }

        private void onCompositeBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            state = AsyncapiState.openingReply(state);

            final AsyncapiBeginExFW asyncapiBeginEx = extension.get(asyncapiBeginExRO::tryWrap);
            final OctetsFW asyncapiExtension = asyncapiBeginEx != null ? asyncapiBeginEx.extension() : EMPTY_OCTETS;

            asyncapiClient.doAsyncapiClientBegin(traceId, asyncapiExtension);
        }

        private void onCompositeData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int flags = data.flags();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;
            assert replySeq <= replyAck + replyMax;

            asyncapiClient.doAsyncapiClientData(traceId, authorization, budgetId, reserved, flags, payload, extension);
        }

        private void onCompositeFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;
            assert replySeq <= replyAck + replyMax;

            asyncapiClient.doAsyncapiClientFlush(traceId, reserved, extension);
        }

        private void onCompositeEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final OctetsFW extension = end.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = AsyncapiState.closingReply(state);

            assert replyAck <= replySeq;

            asyncapiClient.doAsyncapiClientEnd(traceId, extension);
        }

        private void onCompositeAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = AsyncapiState.closingReply(state);

            assert replyAck <= replySeq;

            asyncapiClient.doAsyncapiClientAbort(traceId, EMPTY_OCTETS);
        }

        private void onCompositeReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= asyncapiClient.initialAck;

            asyncapiClient.initialAck = acknowledge;
            state = AsyncapiState.closeInitial(state);

            assert asyncapiClient.initialAck <= asyncapiClient.initialSeq;

            asyncapiClient.doAsyncapiClientReset(traceId);
        }

        private void onCompositeWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long authorization = window.authorization();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert acknowledge >= asyncapiClient.initialAck;
            assert maximum >= asyncapiClient.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            initialBud = budgetId;
            state = AsyncapiState.openInitial(state);

            assert initialAck <= initialSeq;

            asyncapiClient.doAsyncapiClientWindow(authorization, traceId, budgetId, padding);
        }

        private void doCompositeReset(
            long traceId)
        {
            if (!AsyncapiState.replyClosed(state))
            {
                doReset(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);

                state = AsyncapiState.closeReply(state);
            }
        }

        private void doCompositeWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            replyAck = Math.max(asyncapiClient.replyAck - replyPad, 0);
            replyMax = asyncapiClient.replyMax;

            doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding + replyPad);
        }

        private void doCompositeBegin(
            long traceId,
            OctetsFW extension)
        {
            if (!AsyncapiState.initialOpening(state))
            {
                assert state == 0;

                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.receiver = newStream(this::onCompositeMessage,
                    originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, 0L, extension);
                state = AsyncapiState.openingInitial(state);
            }
        }

        private void doCompositeData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            Flyweight extension)
        {
            doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, flags, reserved, payload, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doCompositeFlush(
            long traceId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, initialBud, reserved, extension);
        }

        private void doCompositeEnd(
            long traceId,
            OctetsFW extension)
        {
            if (!AsyncapiState.initialClosed(state))
            {
                doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, extension);

                state = AsyncapiState.closeInitial(state);
            }
        }

        private void doCompositeAbort(
            long traceId,
            OctetsFW extension)
        {
            if (!AsyncapiState.initialClosed(state))
            {
                doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, extension);

                state = AsyncapiState.closeInitial(state);
            }
        }

        private void cleanup(
            long traceId)
        {
            doCompositeAbort(traceId, EMPTY_OCTETS);
            doCompositeReset(traceId);
        }
    }

    final class AsyncapiClientStream
    {
        private final AsyncapiServerStream delegate;
        private final String operationId;
        private final long originId;
        private final long routedId;
        private final long authorization;

        private long initialId;
        private long replyId;
        private MessageConsumer receiver;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long initialBud;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private AsyncapiClientStream(
            AsyncapiServerStream delegate,
            long originId,
            long routedId,
            long authorization,
            String operationId)
        {
            this.delegate = delegate;
            this.originId = originId;
            this.routedId = routedId;
            this.receiver = MessageConsumer.NOOP;
            this.authorization = authorization;
            this.operationId = operationId;
        }

        private void onAsyncapiClientMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onAsyncapiClientBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onAsyncapiClientData(data);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onAsyncapiClientFlush(flush);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onAsyncapiClientEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAsyncapiClientAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAsyncapiClientReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAsyncapiClientWindow(window);
                break;
            default:
                break;
            }
        }

        private void onAsyncapiClientBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            state = AsyncapiState.openingReply(state);

            final AsyncapiBeginExFW asyncapiBeginEx = extension.get(asyncapiBeginExRO::tryWrap);
            final OctetsFW asyncapiExtension = asyncapiBeginEx != null ? asyncapiBeginEx.extension() : EMPTY_OCTETS;

            delegate.doAsyncapiServerBegin(traceId, asyncapiExtension);
        }

        private void onAsyncapiClientData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;
            assert replySeq <= replyAck + replyMax;

            delegate.doAsyncapiServerData(traceId, flags, reserved, payload, extension);
        }

        private void onAsyncapiClientFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;
            assert replySeq <= replyAck + replyMax;

            delegate.doAsyncapiServerFlush(traceId, reserved, extension);
        }

        private void onAsyncapiClientEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final OctetsFW extension = end.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = AsyncapiState.closingReply(state);

            assert replyAck <= replySeq;

            delegate.doAsyncapiServerEnd(traceId, extension);
        }

        private void onAsyncapiClientAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = AsyncapiState.closingReply(state);

            assert replyAck <= replySeq;

            delegate.doAsyncapiServerAbort(traceId);
        }

        private void onAsyncapiClientReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;
            state = AsyncapiState.closeInitial(state);

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.doAsyncapiServerReset(traceId);
        }


        private void onAsyncapiClientWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long authorization = window.authorization();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;
            assert maximum >= delegate.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            initialBud = budgetId;
            state = AsyncapiState.openInitial(state);

            assert initialAck <= initialSeq;

            delegate.doAsyncapiServerWindow(authorization, traceId, budgetId, padding);
        }

        private void doAsyncapiClientReset(
            long traceId)
        {
            if (!AsyncapiState.replyClosed(state))
            {
                doReset(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);

                state = AsyncapiState.closeReply(state);
            }
        }

        private void doAsyncapiClientWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            replyAck = Math.max(delegate.replyAck - replyPad, 0);
            replyMax = delegate.replyMax;

            doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding + replyPad);
        }

        private void doAsyncapiClientBegin(
            long traceId,
            OctetsFW extension)
        {
            if (!AsyncapiState.initialOpening(state))
            {
                assert state == 0;

                final AsyncapiBeginExFW asyncapiBeginEx = asyncapiBeginExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(asyncapiTypeId)
                    .operationId(operationId)
                    .extension(extension)
                    .build();

                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.receiver = newStream(this::onAsyncapiClientMessage,
                    originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, 0L, asyncapiBeginEx);
                state = AsyncapiState.openingInitial(state);
            }
        }

        private void doAsyncapiClientData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            Flyweight extension)
        {
            doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, flags, reserved, payload, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doAsyncapiClientFlush(
            long traceId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, initialBud, reserved, extension);
        }

        private void doAsyncapiClientEnd(
            long traceId,
            OctetsFW extension)
        {
            if (!AsyncapiState.initialClosed(state))
            {
                doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, extension);

                state = AsyncapiState.closeInitial(state);
            }
        }

        private void doAsyncapiClientAbort(
            long traceId,
            OctetsFW extension)
        {
            if (!AsyncapiState.initialClosed(state))
            {
                doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, extension);

                state = AsyncapiState.closeInitial(state);
            }
        }

        private void cleanup(
            long traceId)
        {
            doAsyncapiClientAbort(traceId, EMPTY_OCTETS);
            delegate.doAsyncapiServerReset(traceId);
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
        long budgetId,
        int flags,
        int reserved,
        OctetsFW payload,
        Flyweight extension)
    {
        final DataFW frame = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
            .payload(payload)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());
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
            .extension(extension)
            .build();

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
        OctetsFW extension)
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
            .extension(extension)
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
        long authorization,
        OctetsFW extension)
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
            .extension(extension)
            .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doWindow(
        MessageConsumer sender,
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

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
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
}
