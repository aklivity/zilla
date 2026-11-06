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
package io.aklivity.zilla.runtime.binding.a2a.openapi.internal.stream;

import java.util.function.LongUnaryOperator;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.A2aOpenapiConfiguration;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.config.A2aOpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.config.A2aOpenapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.config.A2aOpenapiCompositeRouteConfig;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.config.composite.A2aOpenapiCompositeGenerator;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.event.A2aOpenapiEventContext;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public final class A2aOpenapiProxyFactory implements BindingHandler
{
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);

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

    private final EngineContext context;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final MutableDirectBufferEx writeBuffer;

    private final Long2ObjectHashMap<A2aOpenapiBindingConfig> bindings;
    private final A2aOpenapiCompositeGenerator generator;
    private final A2aOpenapiEventContext event;
    private final long compositeRouteId;

    public A2aOpenapiProxyFactory(
        A2aOpenapiConfiguration config,
        EngineContext context)
    {
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.generator = new A2aOpenapiCompositeGenerator();
        this.event = new A2aOpenapiEventContext(context);
        this.compositeRouteId = config.compositeRouteId();
    }

    public void attach(
        BindingConfig binding)
    {
        A2aOpenapiBindingConfig attached = new A2aOpenapiBindingConfig(context, binding);
        bindings.put(binding.id, attached);

        A2aOpenapiCompositeConfig composite = generator.generate(attached);
        for (String reason : generator.deniedOperations())
        {
            event.operationDenied(binding.id, reason);
        }
        assert composite != null;

        composite.namespaces.forEach(context::attachComposite);
        attached.composite = composite;
    }

    public void detach(
        long bindingId)
    {
        A2aOpenapiBindingConfig binding = bindings.remove(bindingId);
        A2aOpenapiCompositeConfig composite = binding != null ? binding.composite : null;

        if (composite != null)
        {
            composite.namespaces.forEach(context::detachComposite);
        }
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
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

        final A2aOpenapiBindingConfig binding = bindings.get(routedId);
        final A2aOpenapiCompositeConfig composite = binding != null ? binding.composite : null;

        MessageConsumer newStream = null;

        if (binding != null && composite != null)
        {
            final A2aOpenapiCompositeRouteConfig route = composite.resolve();

            if (route != null)
            {
                final long resolvedId = compositeRouteId != -1L ? compositeRouteId : route.id;

                newStream = new A2aOpenapiStream(
                    receiver,
                    originId,
                    routedId,
                    initialId,
                    affinity,
                    authorization,
                    resolvedId)::onA2aOpenapiMessage;
            }
        }

        return newStream;
    }

    private final class A2aOpenapiStream
    {
        private final A2aHttpStream http;
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;

        private int state;

        private A2aOpenapiStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization,
            long resolvedId)
        {
            this.http = new A2aHttpStream(this, routedId, resolvedId, authorization);
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
        }

        private void onA2aOpenapiMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onA2aOpenapiBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onA2aOpenapiData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onA2aOpenapiEnd(end);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onA2aOpenapiFlush(flush);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onA2aOpenapiAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onA2aOpenapiWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onA2aOpenapiReset(reset);
                break;
            default:
                break;
            }
        }

        private void onA2aOpenapiBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            state = A2aOpenapiState.openingInitial(state);

            http.doA2aHttpBegin(sequence, acknowledge, maximum, traceId, affinity, extension);
        }

        private void onA2aOpenapiData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final int maximum = data.maximum();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            http.doA2aHttpData(sequence, acknowledge, maximum, traceId, authorization,
                budgetId, flags, reserved, payload, extension);
        }

        private void onA2aOpenapiEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final int maximum = end.maximum();
            final long traceId = end.traceId();
            final OctetsFW extension = end.extension();

            state = A2aOpenapiState.closeInitial(state);

            http.doA2aHttpEnd(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onA2aOpenapiFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final int maximum = flush.maximum();
            final long traceId = flush.traceId();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            http.doA2aHttpFlush(sequence, acknowledge, maximum, traceId, budgetId, reserved, extension);
        }

        private void onA2aOpenapiAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final int maximum = abort.maximum();
            final long traceId = abort.traceId();
            final OctetsFW extension = abort.extension();

            state = A2aOpenapiState.closeInitial(state);

            http.doA2aHttpAbort(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onA2aOpenapiReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();
            final OctetsFW extension = reset.extension();

            state = A2aOpenapiState.closeReply(state);

            http.doA2aHttpReset(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onA2aOpenapiWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            http.doA2aHttpWindow(sequence, acknowledge, maximum, traceId, authorization, budgetId, padding);
        }

        private void doA2aOpenapiBegin(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            if (!A2aOpenapiState.replyOpening(state))
            {
                state = A2aOpenapiState.openingReply(state);

                A2aOpenapiProxyFactory.this.doBegin(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                    traceId, authorization, affinity, extension);
            }
        }

        private void doA2aOpenapiData(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            OctetsFW payload,
            OctetsFW extension)
        {
            A2aOpenapiProxyFactory.this.doData(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, flags, reserved, payload, extension);
        }

        private void doA2aOpenapiFlush(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            A2aOpenapiProxyFactory.this.doFlush(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, reserved, extension);
        }

        private void doA2aOpenapiEnd(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            // an END must always be deliverable even if the reply was never opened (e.g. a2a-http0 ends its
            // reply before ever sending a BEGIN) — open the reply first, mirroring doA2aOpenapiAbort below
            doA2aOpenapiBegin(sequence, acknowledge, maximum, traceId, extension);

            if (!A2aOpenapiState.replyClosed(state))
            {
                A2aOpenapiProxyFactory.this.doEnd(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                    traceId, authorization, extension);
            }

            state = A2aOpenapiState.closeReply(state);
        }

        private void doA2aOpenapiAbort(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            // an ABORT must always be deliverable even if the reply was never opened — e.g. a2a-http0's
            // upstream aborts before ever sending a response, so no reply BEGIN has gone out yet. Without
            // opening the reply first, this abort is silently dropped and the real client hangs forever
            // waiting for any reply frame at all
            doA2aOpenapiBegin(sequence, acknowledge, maximum, traceId, extension);

            if (!A2aOpenapiState.replyClosed(state))
            {
                A2aOpenapiProxyFactory.this.doAbort(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                    traceId, authorization, extension);
            }

            state = A2aOpenapiState.closeReply(state);
        }

        private void doA2aOpenapiReset(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            if (!A2aOpenapiState.initialClosed(state))
            {
                state = A2aOpenapiState.closeInitial(state);

                A2aOpenapiProxyFactory.this.doReset(sender, originId, routedId, initialId, sequence, acknowledge, maximum,
                    traceId, authorization, extension);
            }
        }

        private void doA2aOpenapiWindow(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long budgetId,
            int padding)
        {
            A2aOpenapiProxyFactory.this.doWindow(sender, originId, routedId, initialId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, padding);
        }
    }

    private final class A2aHttpStream
    {
        private final A2aOpenapiStream delegate;
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final long initialId;
        private final long replyId;

        private MessageConsumer receiver;
        private int state;

        private A2aHttpStream(
            A2aOpenapiStream delegate,
            long originId,
            long routedId,
            long authorization)
        {
            this.delegate = delegate;
            this.originId = originId;
            this.routedId = routedId;
            this.receiver = MessageConsumer.NOOP;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
        }

        private void onA2aHttpMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onA2aHttpBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onA2aHttpData(data);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onA2aHttpFlush(flush);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onA2aHttpEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onA2aHttpAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onA2aHttpReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onA2aHttpWindow(window);
                break;
            default:
                break;
            }
        }

        private void onA2aHttpBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            state = A2aOpenapiState.openingReply(state);

            delegate.doA2aOpenapiBegin(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onA2aHttpData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final int maximum = data.maximum();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            delegate.doA2aOpenapiData(sequence, acknowledge, maximum, traceId, budgetId,
                flags, reserved, payload, extension);
        }

        private void onA2aHttpFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final int maximum = flush.maximum();
            final long traceId = flush.traceId();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            delegate.doA2aOpenapiFlush(sequence, acknowledge, maximum, traceId, budgetId, reserved, extension);
        }

        private void onA2aHttpEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final int maximum = end.maximum();
            final long traceId = end.traceId();
            final OctetsFW extension = end.extension();

            state = A2aOpenapiState.closingReply(state);

            delegate.doA2aOpenapiEnd(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onA2aHttpAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final int maximum = abort.maximum();
            final long traceId = abort.traceId();
            final OctetsFW extension = abort.extension();

            state = A2aOpenapiState.closingReply(state);

            delegate.doA2aOpenapiAbort(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onA2aHttpReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();
            final OctetsFW extension = reset.extension();

            state = A2aOpenapiState.closeInitial(state);

            delegate.doA2aOpenapiReset(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onA2aHttpWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            state = A2aOpenapiState.openingInitial(state);

            delegate.doA2aOpenapiWindow(sequence, acknowledge, maximum, traceId, budgetId, padding);
        }

        private void doA2aHttpBegin(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long affinity,
            OctetsFW extension)
        {
            if (!A2aOpenapiState.initialOpening(state))
            {
                assert state == 0;

                this.receiver = A2aOpenapiProxyFactory.this.newStream(this::onA2aHttpMessage, originId, routedId, initialId,
                    sequence, acknowledge, maximum, traceId, authorization, affinity, extension);
                state = A2aOpenapiState.openingInitial(state);
            }
        }

        private void doA2aHttpData(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long authorization,
            long budgetId,
            int flags,
            int reserved,
            OctetsFW payload,
            OctetsFW extension)
        {
            A2aOpenapiProxyFactory.this.doData(receiver, originId, routedId, initialId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, flags, reserved, payload, extension);
        }

        private void doA2aHttpFlush(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            A2aOpenapiProxyFactory.this.doFlush(receiver, originId, routedId, initialId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, reserved, extension);
        }

        private void doA2aHttpEnd(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            if (!A2aOpenapiState.initialClosed(state))
            {
                A2aOpenapiProxyFactory.this.doEnd(receiver, originId, routedId, initialId, sequence, acknowledge, maximum,
                    traceId, authorization, extension);

                state = A2aOpenapiState.closeInitial(state);
            }
        }

        private void doA2aHttpAbort(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            if (!A2aOpenapiState.initialClosed(state))
            {
                A2aOpenapiProxyFactory.this.doAbort(receiver, originId, routedId, initialId, sequence, acknowledge, maximum,
                    traceId, authorization, extension);

                state = A2aOpenapiState.closeInitial(state);
            }
        }

        private void doA2aHttpReset(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            if (!A2aOpenapiState.replyClosed(state))
            {
                A2aOpenapiProxyFactory.this.doReset(receiver, originId, routedId, replyId, sequence, acknowledge, maximum,
                    traceId, authorization, extension);

                state = A2aOpenapiState.closeReply(state);
            }
        }

        private void doA2aHttpWindow(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            A2aOpenapiProxyFactory.this.doWindow(receiver, originId, routedId, replyId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, padding);
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
        OctetsFW extension)
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
            .extension(extension)
            .build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }
}
