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

import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiBinding;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiConfiguration;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiRouteConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.composite.AsyncapiCompositeGenerator;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.composite.AsyncapiServerGenerator;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.AsyncapiBeginExFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiOperationView;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class AsyncapiServerFactory implements AsyncapiStreamFactory
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

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final AsyncapiBeginExFW beginExRO = new AsyncapiBeginExFW();

    private final AsyncapiBeginExFW.Builder beginExRW = new AsyncapiBeginExFW.Builder();

    private final EngineContext context;

    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;

    private final Long2ObjectHashMap<AsyncapiBindingConfig> bindings;
    private final int asyncapiTypeId;

    private final AsyncapiCompositeGenerator generator;

    public AsyncapiServerFactory(
        AsyncapiConfiguration config,
        EngineContext context)
    {
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.asyncapiTypeId = context.supplyTypeId(AsyncapiBinding.NAME);
        this.bindings = new Long2ObjectHashMap<>();
        this.generator = new AsyncapiServerGenerator();
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
        AsyncapiBindingConfig attached = new AsyncapiBindingConfig(context, binding);
        bindings.put(binding.id, attached);

        AsyncapiCompositeConfig composite = generator.generate(attached);
        assert composite != null;
        // TODO: schedule generate retry if null

        composite.namespaces.forEach(context::attachComposite);
        attached.composite = composite;
    }

    @Override
    public void detach(
        long bindingId)
    {
        AsyncapiBindingConfig binding = bindings.remove(bindingId);
        AsyncapiCompositeConfig composite = binding.composite;

        if (composite != null)
        {
            composite.namespaces.forEach(context::detachComposite);
        }

        // TODO: cancel generate retry if scheduled
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

        final AsyncapiBindingConfig binding = bindings.get(routedId);
        final AsyncapiCompositeConfig composite = binding != null ? binding.composite : null;

        MessageConsumer newStream = null;

        if (binding != null && composite != null)
        {
            if (composite.hasBindingId(originId))
            {
                final ExtensionFW extensionEx = extension.get(extensionRO::wrap);
                final long compositeId = extensionEx.compositeId();
                final AsyncapiOperationView operation = composite.resolveOperation(compositeId);

                if (operation != null)
                {
                    final String apiId = operation.specification.label;
                    final String operationId = operation.name;

                    final AsyncapiRouteConfig route = binding.resolve(authorization, apiId, operationId);

                    if (route != null)
                    {
                        final long resolvedId = route.id;
                        final long resolvedApiId = composite.resolveApiId(
                            route.with != null
                                ? route.with.apiId
                                : apiId);
                        final String resolvedOperationId =
                            route.with != null
                                ? route.with.operationId
                                : operationId;

                        newStream = new CompositeStream(
                            receiver,
                            originId,
                            routedId,
                            initialId,
                            affinity,
                            authorization,
                            resolvedId,
                            resolvedApiId,
                            resolvedOperationId)::onCompositeMessage;
                    }
                }
            }
        }

        return newStream;
    }

    private final class CompositeStream
    {
        private final AsyncapiStream delegate;
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

        private CompositeStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization,
            long resolvedId,
            long apiId,
            String operationId)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.delegate = new AsyncapiStream(this, routedId, resolvedId, authorization, apiId, operationId);
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
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onCompositeEnd(end);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onCompositeFlush(flush);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onCompositeAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onCompositeWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onCompositeReset(reset);
                break;
            default:
                break;
            }
        }

        private void onCompositeBegin(
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

            delegate.doAsyncapiBegin(traceId, extension);
        }

        private void onCompositeData(
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

            delegate.doAsyncapiData(traceId, authorization, budgetId, reserved, flags, payload, extension);
        }

        private void onCompositeEnd(
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

            delegate.doAsyncapiEnd(traceId, extension);
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
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            delegate.doAsyncapiFlush(traceId, reserved, extension);
        }

        private void onCompositeAbort(
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

            delegate.doAsyncapiAbort(traceId, extension);
        }

        private void doCompositeReset(
            long traceId)
        {
            if (!AsyncapiState.initialClosed(state))
            {
                state = AsyncapiState.closeInitial(state);

                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doCompositeWindow(
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

        private void doCompositeBegin(
            long traceId,
            OctetsFW extension)
        {
            state = AsyncapiState.openingReply(state);

            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, extension);
        }

        private void doCompositeData(
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

        private void doCompositeFlush(
            long traceId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, replyBud, reserved, extension);
        }

        private void doCompositeEnd(
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

        private void doCompositeAbort(
            long traceId)
        {
            if (AsyncapiState.replyOpening(state) && !AsyncapiState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }

            state = AsyncapiState.closeInitial(state);
        }

        private void onCompositeReset(
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

        private void onCompositeWindow(
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

            delegate.doAsyncapiWindow(traceId, acknowledge, budgetId, padding);
        }

        private void cleanup(
            long traceId)
        {
            doCompositeReset(traceId);
            doCompositeAbort(traceId);

            delegate.cleanup(traceId);
        }
    }

    final class AsyncapiStream
    {
        private final CompositeStream delegate;
        private final String operationId;
        private final long apiId;
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

        private AsyncapiStream(
            CompositeStream delegate,
            long originId,
            long routedId,
            long authorization,
            long apiId,
            String operationId)
        {
            this.delegate = delegate;
            this.originId = originId;
            this.routedId = routedId;
            this.receiver = MessageConsumer.NOOP;
            this.authorization = authorization;
            this.apiId = apiId;
            this.operationId = operationId;
        }

        private void onAsyncapiMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onAsyncapiBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onAsyncapiData(data);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onAsyncapiFlush(flush);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onAsyncapiEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAsyncapiAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAsyncapiReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAsyncapiWindow(window);
                break;
            default:
                break;
            }
        }

        private void onAsyncapiBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            state = AsyncapiState.openingReply(state);

            final AsyncapiBeginExFW asyncapiBeginEx = extension.get(beginExRO::tryWrap);
            final OctetsFW asyncapiExtension = asyncapiBeginEx != null ? asyncapiBeginEx.extension() : EMPTY_OCTETS;

            delegate.doCompositeBegin(traceId, asyncapiExtension);
        }

        private void onAsyncapiData(
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

            delegate.doCompositeData(traceId, flags, reserved, payload, extension);
        }

        private void onAsyncapiFlush(
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

            delegate.doCompositeFlush(traceId, reserved, extension);
        }

        private void onAsyncapiEnd(
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

            delegate.doCompositeEnd(traceId, extension);
        }

        private void onAsyncapiAbort(
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

            delegate.doCompositeAbort(traceId);
        }

        private void onAsyncapiReset(
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

            delegate.doCompositeReset(traceId);
        }


        private void onAsyncapiWindow(
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

            delegate.doCompositeWindow(authorization, traceId, budgetId, padding);
        }

        private void doAsyncapiReset(
            long traceId)
        {
            if (!AsyncapiState.replyClosed(state))
            {
                doReset(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);

                state = AsyncapiState.closeReply(state);
            }
        }

        private void doAsyncapiWindow(
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

        private void doAsyncapiBegin(
            long traceId,
            OctetsFW extension)
        {
            if (!AsyncapiState.initialOpening(state))
            {
                assert state == 0;

                final AsyncapiBeginExFW asyncapiBeginEx = beginExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(asyncapiTypeId)
                    .apiId(apiId)
                    .operationId(operationId)
                    .extension(extension)
                    .build();

                this.initialSeq = delegate.initialSeq;
                this.initialAck = delegate.initialAck;

                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.receiver = newStream(this::onAsyncapiMessage,
                    originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, 0L, asyncapiBeginEx);
                state = AsyncapiState.openingInitial(state);
            }
        }

        private void doAsyncapiData(
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

        private void doAsyncapiFlush(
            long traceId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, initialBud, reserved, extension);
        }

        private void doAsyncapiEnd(
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

        private void doAsyncapiAbort(
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
            doAsyncapiAbort(traceId, EMPTY_OCTETS);
            doAsyncapiReset(traceId);
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
