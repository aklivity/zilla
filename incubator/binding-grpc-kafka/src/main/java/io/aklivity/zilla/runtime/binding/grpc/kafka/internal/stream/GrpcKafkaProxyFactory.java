/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaCapabilities.FETCH_ONLY;
import static io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaCapabilities.PRODUCE_ONLY;
import static io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcKind.UNARY;
import static java.time.Instant.now;

import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.GrpcKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaWithResult;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcDataExFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcKind;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class GrpcKafkaProxyFactory implements GrpcKafkaStreamFactory
{
    private static final String GRPC_TYPE_NAME = "grpc";
    private static final String KAFKA_TYPE_NAME = "kafka";

    private static final int DATA_FLAG_INIT = 0x02;
    private static final int DATA_FLAG_FIN = 0x01;

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final SignalFW signalRO = new SignalFW();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final GrpcBeginExFW grpcBeginExRO = new GrpcBeginExFW();
    private final GrpcDataExFW grpcDataExRO = new GrpcDataExFW();

    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();

    private final GrpcDataExFW.Builder grpcDataExRW = new GrpcDataExFW.Builder();
    private final GrpcBeginExFW.Builder grpcBeginExRW = new GrpcBeginExFW.Builder();

    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final Signaler signaler;
    private final int grpcTypeId;
    private final int kafkaTypeId;


    private final Long2ObjectHashMap<GrpcKafkaBindingConfig> bindings;

    public GrpcKafkaProxyFactory(
        GrpcKafkaConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.signaler = context.signaler();
        this.bindings = new Long2ObjectHashMap<>();
        this.grpcTypeId = context.supplyTypeId(GRPC_TYPE_NAME);
        this.kafkaTypeId = context.supplyTypeId(KAFKA_TYPE_NAME);
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        GrpcKafkaBindingConfig newBinding = new GrpcKafkaBindingConfig(binding);
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
        MessageConsumer grpc)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long routeId = begin.routeId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();
        final GrpcBeginExFW grpcBeginEx = extension.get(grpcBeginExRO::tryWrap);

        final GrpcKafkaBindingConfig binding = bindings.get(routeId);

        GrpcKafkaRouteConfig route = null;

        if (binding != null)
        {
            route = binding.resolve(authorization, grpcBeginEx);
        }

        MessageConsumer newStream = null;

        if (route != null)
        {
            final long resolvedId = route.id;
            GrpcKind request = grpcBeginEx.request().get();
            GrpcKind response = grpcBeginEx.response().get();
            GrpcKafkaWithResult resolve = route.with.resolve(authorization, grpcBeginEx);

            if (request == UNARY && response == UNARY)
            {
                newStream = new GrpcUnaryProxy(grpc, routeId, initialId, resolvedId, resolve)::onGrpcMessage;
            }
        }

        return newStream;
    }

    private abstract class GrpcProxy
    {
        protected final MessageConsumer grpc;
        protected final long routeId;
        protected final long initialId;
        protected final long replyId;

        protected long initialSeq;
        protected long initialAck;
        protected int initialMax;

        protected int state;

        protected long replySeq;
        protected long replyAck;
        protected int replyMax;
        protected long replyBud;
        protected int replyPad;
        protected int replyCap;

        private GrpcProxy(
            MessageConsumer grpc,
            long routeId,
            long initialId)
        {
            this.grpc = grpc;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        protected abstract void onKafkaBegin(
            long traceId,
            long authorization,
            OctetsFW extension);

        protected void onKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            OctetsFW extension)
        {
        }

        protected abstract void onKafkaEnd(
            long traceId,
            long authorization);

        protected void onKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
        }

        protected abstract void onKafkaWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities);

        protected abstract void onKafkaReset(
            long traceId,
            long authorization);

        protected abstract void onKafkaAbort(
            long traceId,
            long authorization);
    }

    private final class KafkaProduceProxy
    {
        private MessageConsumer kafka;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final GrpcKafkaWithResult resolved;
        private final GrpcProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long replyBud;
        private int replyPad;
        private int replyCap;

        private KafkaProduceProxy(
            long routeId,
            GrpcProxy delegate,
            GrpcKafkaWithResult resolved)
        {
            this.routeId = routeId;
            this.delegate = delegate;
            this.resolved = resolved;
            this.initialId = supplyInitialId.applyAsLong(routeId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            initialSeq = delegate.initialSeq;
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;
            state = GrpcKafkaState.openingInitial(state);

            kafka = newKafkaProducer(this::onKafkaMessage, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, resolved);
        }

        private void doKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            Flyweight extension)
        {
            doData(kafka, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, flags, reserved, payload, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doKafkaEnd(
            long traceId,
            long authorization)
        {
            if (!GrpcKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = GrpcKafkaState.closeInitial(state);

                doEnd(kafka, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doKafkaEndDeferred(
            long traceId,
            long authorization)
        {
            state = GrpcKafkaState.closingInitial(state);
            doKafkaEndAck(traceId, authorization);
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (!GrpcKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = GrpcKafkaState.closeInitial(state);

                doAbort(kafka, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            initialSeq = delegate.initialSeq;

            doFlush(kafka, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, reserved);
        }

        private void onKafkaMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onKafkaBegin(begin);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onKafkaEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onKafkaAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onKafkaReset(reset);
                break;
            }
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final OctetsFW extension = begin.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = GrpcKafkaState.openingReply(state);

            assert replyAck <= replySeq;

            doKafkaWindow(traceId);
            delegate.onKafkaBegin(traceId, authorization, extension);
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = GrpcKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaEnd(traceId, authorization);
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = GrpcKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaAbort(traceId, authorization);

            doKafkaAbort(traceId, authorization);
        }

        private void onKafkaWindow(
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
            state = GrpcKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            delegate.onKafkaWindow(authorization, traceId, budgetId, padding, capabilities);

            doKafkaEndAck(authorization, traceId);
        }

        private void doKafkaEndAck(long authorization, long traceId)
        {
            if (GrpcKafkaState.initialClosing(state) && initialSeq == initialAck)
            {
                doKafkaEnd(traceId, authorization);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;
            state = GrpcKafkaState.closeInitial(state);

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.onKafkaReset(traceId, authorization);

            doKafkaReset(traceId);
        }

        private void doKafkaReset(
            long traceId)
        {
            if (!GrpcKafkaState.replyClosed(state))
            {
                state = GrpcKafkaState.closeReply(state);

                doReset(kafka, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId);
            }
        }

        private void doKafkaWindow(
            long traceId)
        {
            if (kafka != null && !GrpcKafkaState.replyClosed(state))
            {
                replyAck = delegate.replyAck;
                replyMax = delegate.replyMax;
                replyBud = delegate.replyBud;
                replyPad = delegate.replyPad;
                replyCap = delegate.replyCap;

                doWindow(kafka, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId, 0L, replyBud, replyPad, replyCap);
            }
        }
    }

    private final class KafkaCorrelateProxy
    {
        private MessageConsumer kafka;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final GrpcKafkaWithResult resolved;
        private final GrpcProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long replyBud;
        private int replyPad;
        private int replyCap;

        private KafkaCorrelateProxy(
            long routeId,
            GrpcProxy delegate,
            GrpcKafkaWithResult resolved)
        {
            this.routeId = routeId;
            this.delegate = delegate;
            this.resolved = resolved;
            this.initialId = supplyInitialId.applyAsLong(routeId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            initialMax = delegate.initialMax;
            state = GrpcKafkaState.openingInitial(state);

            kafka = newKafkaCorrelater(this::onKafkaMessage, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, resolved);

            doKafkaWindow(traceId);
        }

        private void doKafkaEnd(
            long traceId,
            long authorization)
        {
            if (!GrpcKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = GrpcKafkaState.closeInitial(state);

                doEnd(kafka, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (!GrpcKafkaState.initialClosed(state) && kafka != null)
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = GrpcKafkaState.closeInitial(state);

                doAbort(kafka, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void onKafkaMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onKafkaBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onKafkaData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onKafkaEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onKafkaAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onKafkaFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onKafkaReset(reset);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onKafkaSignal(signal);
                break;
            }
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final OctetsFW extension = begin.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = GrpcKafkaState.openingReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaBegin(traceId, authorization, extension);
            doKafkaWindow(traceId);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                doKafkaReset(traceId);
                delegate.onKafkaReset(traceId, authorization);
            }
            else
            {
                final int flags = data.flags();
                final OctetsFW payload = data.payload();
                final OctetsFW extension = data.extension();

                delegate.onKafkaData(traceId, authorization, budgetId, reserved, flags, payload, extension);
            }
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = GrpcKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaEnd(traceId, authorization);
        }

        private void onKafkaFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            delegate.onKafkaFlush(traceId, authorization, budgetId, reserved);
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = GrpcKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaAbort(traceId, authorization);

            doKafkaAbort(traceId, authorization);
        }

        private void onKafkaWindow(
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
            assert maximum >= delegate.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = GrpcKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            delegate.onKafkaWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;
            state = GrpcKafkaState.closeInitial(state);

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.onKafkaReset(traceId, authorization);

            doKafkaReset(traceId);
        }

        private void onKafkaSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            doKafkaEnd(traceId, authorization);
        }

        private void doKafkaReset(
            long traceId)
        {
            if (!GrpcKafkaState.replyClosed(state) && kafka != null)
            {
                state = GrpcKafkaState.closeReply(state);

                doReset(kafka, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId);
            }
        }

        private void doKafkaWindow(
            long traceId)
        {
            if (kafka != null)
            {
                replyAck = delegate.replyAck;
                replyMax = delegate.replyMax;
                replyBud = delegate.replyBud;
                replyPad = delegate.replyPad;
                replyCap = delegate.replyCap;

                doWindow(kafka, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId, 0L, replyBud, replyPad, replyCap);
            }
        }
    }

    private final class GrpcUnaryProxy extends GrpcProxy
    {
        private final KafkaProduceProxy producer;
        private final KafkaCorrelateProxy correlater;

        private GrpcUnaryProxy(
            MessageConsumer grpc,
            long routeId,
            long initialId,
            long resolvedId,
            GrpcKafkaWithResult resolved)
        {
            super(grpc, routeId, initialId);
            this.producer = new KafkaProduceProxy(resolvedId, this, resolved);
            this.correlater = new KafkaCorrelateProxy(resolvedId, this, resolved);
        }

        private void onGrpcMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onGrpcBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onGrpcData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onGrpcEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onGrpcAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onGrpcFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onGrpcReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onGrpcWindow(window);
                break;
            }
        }

        private void onGrpcBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            state = GrpcKafkaState.openingInitial(state);

            assert initialAck <= initialSeq;

            producer.doKafkaBegin(traceId, authorization, affinity);
        }

        private void onGrpcData(
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

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            producer.resolved.updateHash(payload.value());

            Flyweight kafkaDataEx = emptyRO;
            if ((flags & DATA_FLAG_INIT) != 0x00)
            {
                GrpcDataExFW dataEx = null;
                if (extension.sizeof() > 0)
                {
                    dataEx = extension.get(grpcDataExRO::tryWrap);
                }

                final int deferred = dataEx != null ? dataEx.deferred() : 0;
                kafkaDataEx = kafkaDataExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m -> m
                        .deferred(deferred)
                        .timestamp(now().toEpochMilli())
                        .partition(p -> p.partitionId(-1).partitionOffset(-1))
                        .key(producer.resolved::key))
                    .build();
            }

            producer.doKafkaData(traceId, authorization, budgetId, reserved, flags, payload, kafkaDataEx);
        }

        private void onGrpcEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = GrpcKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            producer.resolved.digestHash();

            correlater.doKafkaBegin(traceId, authorization, 0L);

            producer.doKafkaEndDeferred(traceId, authorization);
        }

        private void onGrpcAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = GrpcKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            producer.doKafkaAbort(traceId, authorization);
            correlater.doKafkaAbort(traceId, authorization);
        }

        protected void onGrpcFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            producer.doKafkaFlush(traceId, authorization, budgetId, reserved);
        }

        private void onGrpcReset(
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
            state = GrpcKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            producer.doKafkaReset(traceId);
            correlater.doKafkaReset(traceId);
        }

        private void onGrpcWindow(
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
            state = GrpcKafkaState.openReply(state);

            assert replyAck <= replySeq;

            producer.doKafkaWindow(traceId);
            correlater.doKafkaWindow(traceId);
        }

        @Override
        protected void onKafkaReset(
            long traceId,
            long authorization)
        {
            onKafkaError(traceId, authorization);
        }

        @Override
        protected void onKafkaAbort(
            long traceId,
            long authorization)
        {
            onKafkaError(traceId, authorization);
        }

        @Override
        protected void onKafkaBegin(
            long traceId,
            long authorization, OctetsFW extension)
        {
            // nop
        }

        @Override
        protected void onKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            OctetsFW extension)
        {
            if (GrpcKafkaState.replyClosing(state))
            {
                replySeq += reserved;

                correlater.doKafkaWindow(traceId);
            }
            else
            {
                if ((flags & 0x02) != 0x00) // INIT
                {
                    Flyweight grpcBeginEx = emptyRO;

                    final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                    final KafkaDataExFW kafkaDataEx =
                            dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;

                    doGrpcBegin(traceId, authorization, 0L, grpcBeginEx);
                }

                if (GrpcKafkaState.replyOpening(state) && payload != null)
                {
                    doGrpcData(traceId, authorization, budgetId, reserved, flags, payload);
                }

                if ((flags & 0x01) != 0x00) // FIN
                {
                    correlater.doKafkaEnd(traceId, authorization);
                    state = GrpcKafkaState.closingReply(state);
                }
            }
        }

        @Override
        protected void onKafkaEnd(
            long traceId,
            long authorization)
        {
            if (GrpcKafkaState.replyClosed(correlater.state))
            {
                if (!GrpcKafkaState.replyOpening(state))
                {
                    doGrpcBegin(traceId, authorization, 0L, emptyRO);
                }

                if (GrpcKafkaState.initialClosed(state))
                {
                    producer.doKafkaEnd(traceId, authorization);
                    correlater.doKafkaEnd(traceId, authorization);
                }

                doGrpcEnd(traceId, authorization);
            }
        }

        @Override
        protected void onKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            doGrpcFlush(traceId, authorization, budgetId, reserved);
        }

        @Override
        protected void onKafkaWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            doGrpcWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onKafkaError(
            long traceId,
            long authorization)
        {
            doGrpcReset(traceId, authorization);
            doGrpcAbort(traceId, authorization);

            producer.doKafkaAbort(traceId, authorization);
            producer.doKafkaReset(traceId);
            correlater.doKafkaAbort(traceId, authorization);
            correlater.doKafkaReset(traceId);
        }

        private void doGrpcBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            state = GrpcKafkaState.openingReply(state);

            doBegin(grpc, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, extension);
        }

        private void doGrpcData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload)
        {
            doData(grpc, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, flags, reserved, payload, emptyRO);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doGrpcAbort(
            long traceId,
            long authorization)
        {
            if (!GrpcKafkaState.replyClosed(state))
            {
                replySeq = correlater.replySeq;
                state = GrpcKafkaState.closeReply(state);

                doAbort(grpc, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization);
            }
        }

        private void doGrpcEnd(
            long traceId,
            long authorization)
        {
            if (!GrpcKafkaState.replyClosed(state))
            {
                replySeq = correlater.replySeq;
                state = GrpcKafkaState.closeReply(state);

                doEnd(grpc, routeId, replyId, replySeq, replyAck, replyMax,
                      traceId, authorization);
            }
        }

        private void doGrpcFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            replySeq = correlater.replySeq;

            doFlush(grpc, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, reserved);
        }

        private void doGrpcWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = producer.initialAck;
            initialMax = producer.initialMax;

            doWindow(grpc, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, capabilities);
        }

        private void doGrpcReset(
            long traceId,
            long authorization)
        {
            if (!GrpcKafkaState.initialClosed(state))
            {
                state = GrpcKafkaState.closeInitial(state);

                doReset(grpc, routeId, initialId, initialSeq, initialAck, initialMax, traceId);
            }

            if (!GrpcKafkaState.replyOpening(state))
            {
                doGrpcBegin(traceId, authorization, 0L, emptyRO);

                state = GrpcKafkaState.closingReply(state);
            }
            doGrpcEnd(traceId, authorization);
        }
    }

    private void doBegin(
        MessageConsumer receiver,
        long routeId,
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
                .routeId(routeId)
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
        long routeId,
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
                .routeId(routeId)
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

    private void doEnd(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
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
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
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
        long routeId,
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
                .routeId(routeId)
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

    private MessageConsumer newKafkaProducer(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        GrpcKafkaWithResult resolved)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(PRODUCE_ONLY))
                              .topic(resolved.topic())
                              .partitionsItem(p -> p.partitionId(-1).partitionOffset(-2L))
                              .ackMode(resolved::acks))
                .build();

        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(kafkaBeginEx.buffer(), kafkaBeginEx.offset(), kafkaBeginEx.sizeof())
                .build();

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private MessageConsumer newKafkaCorrelater(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        GrpcKafkaWithResult resolved)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(FETCH_ONLY))
                              .topic(resolved.correlation().replyTo)
                              .partitions(resolved::partitions)
                              .filters(resolved.correlation()::filters))
                .build();

        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(kafkaBeginEx.buffer(), kafkaBeginEx.offset(), kafkaBeginEx.sizeof())
                .build();

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private void doWindow(
        MessageConsumer sender,
        long routeId,
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
                .routeId(routeId)
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

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
               .routeId(routeId)
               .streamId(streamId)
               .sequence(sequence)
               .acknowledge(acknowledge)
               .maximum(maximum)
               .traceId(traceId)
               .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }
}
