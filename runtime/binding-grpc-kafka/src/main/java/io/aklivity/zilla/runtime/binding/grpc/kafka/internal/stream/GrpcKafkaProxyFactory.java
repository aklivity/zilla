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
package io.aklivity.zilla.runtime.binding.grpc.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaCapabilities.FETCH_ONLY;
import static io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaCapabilities.PRODUCE_ONLY;
import static java.time.Instant.now;

import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.GrpcKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaWithFetchResult;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaWithProduceResult;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.Varuint32FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcAbortExFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcDataExFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcResetExFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.KafkaMergedBeginExFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.KafkaMergedDataExFW;
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
    private static final int DATA_FLAG_COMPLETE = 0x03;

    private static final String8FW HEADER_NAME_ZILLA_GRPC_STATUS = new String8FW("zilla:status");
    private static final String16FW HEADER_VALUE_GRPC_OK = new String16FW("0");
    private static final String16FW HEADER_VALUE_GRPC_ABORTED = new String16FW("10");
    private static final String16FW HEADER_VALUE_GRPC_INTERNAL_ERROR = new String16FW("13");
    private final String16FW.Builder string16RW =
        new String16FW.Builder().wrap(new UnsafeBuffer(new byte[256], 0, 256), 0, 256);

    private final Varuint32FW.Builder lenRW =
        new Varuint32FW.Builder().wrap(new UnsafeBuffer(new byte[1024 * 8]), 0, 1024 * 8);;

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();


    private final String16FW.Builder statusRW = new
        String16FW.Builder().wrap(new UnsafeBuffer(new byte[256], 0, 256), 0, 256);

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final SignalFW signalRO = new SignalFW();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final GrpcBeginExFW grpcBeginExRO = new GrpcBeginExFW();
    private final GrpcDataExFW grpcDataExRO = new GrpcDataExFW();
    private final GrpcResetExFW resetExRO = new GrpcResetExFW();
    private final GrpcAbortExFW abortExRO = new GrpcAbortExFW();

    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();

    private final GrpcBeginExFW.Builder grpcBeginExRW = new GrpcBeginExFW.Builder();
    private final GrpcDataExFW.Builder grpcDataExRW = new GrpcDataExFW.Builder();
    private final GrpcResetExFW.Builder grpcResetExRW = new GrpcResetExFW.Builder();
    private final GrpcAbortExFW.Builder grpcAbortExRW = new GrpcAbortExFW.Builder();

    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final GrpcKafkaIdHelper messageFieldHelper = new GrpcKafkaIdHelper();

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
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();
        final GrpcBeginExFW grpcBeginEx = extension.get(grpcBeginExRO::wrap);

        final GrpcKafkaBindingConfig binding = bindings.get(routedId);

        GrpcKafkaRouteConfig route = null;

        if (binding != null)
        {
            route = binding.resolve(authorization, grpcBeginEx);
        }

        MessageConsumer newStream = null;

        if (route != null)
        {
            final long resolvedId = route.id;

            switch (route.with.capability())
            {
            case FETCH:
            {
                final GrpcKafkaWithFetchResult result =
                    route.with.resolveFetch(authorization, grpcBeginEx, messageFieldHelper);

                newStream = new GrpcFetchProxy(
                    grpc,
                    originId,
                    routedId,
                    initialId,
                    resolvedId,
                    result)::onGrpcMessage;
                break;
            }
            case PRODUCE:
            {
                final GrpcKafkaWithProduceResult result =
                    route.with.resolveProduce(authorization, grpcBeginEx);

                newStream = new GrpcProduceProxy(
                    grpc,
                    originId,
                    routedId,
                    initialId,
                    resolvedId,
                    result)::onGrpcMessage;

                break;
            }
            }
        }

        return newStream;
    }

    private abstract class GrpcProxy
    {
        protected final MessageConsumer grpc;
        protected final long originId;
        protected final long routedId;
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
            long originId,
            long routedId,
            long initialId)
        {
            this.grpc = grpc;
            this.originId = originId;
            this.routedId = routedId;
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
            DirectBuffer buffer,
            int offset,
            int length,
            OctetsFW extension)
        {
        }

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

    private final class GrpcFetchProxy extends GrpcProxy
    {
        private final KafkaFetchProxy fetch;
        private final long resolvedId;
        private int initialPad;
        private long initialBud;

        private GrpcFetchProxy(
            MessageConsumer grpc,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            GrpcKafkaWithFetchResult result)
        {
            super(grpc, originId, routedId, initialId);
            this.resolvedId = resolvedId;
            this.fetch = new KafkaFetchProxy(routedId, resolvedId, this, result);
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

            fetch.doKafkaBegin(traceId, authorization, affinity);
        }

        private void onGrpcData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            if (payload == null || payload.sizeof() == 0)
            {
                doGrpcWindow(authorization, traceId, initialBud, initialPad, 0);
            }
            else
            {
                doGrpcReset(traceId, authorization, HEADER_VALUE_GRPC_ABORTED);
                cleanup(traceId, authorization);
            }
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

            fetch.doKafkaEnd(traceId, authorization);
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

            fetch.doKafkaAbort(traceId, authorization);
        }

        private void onGrpcReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            state = GrpcKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            cleanup(traceId, authorization);
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

            fetch.doKafkaWindow(traceId, acknowledge, budgetId, padding, capabilities);
        }

        @Override
        protected void onKafkaReset(
            long traceId,
            long authorization)
        {
            cleanup(traceId, authorization);
        }

        @Override
        protected void onKafkaAbort(
            long traceId,
            long authorization)
        {
            cleanup(traceId, authorization);
        }

        @Override
        protected void onKafkaBegin(
            long traceId,
            long authorization, OctetsFW extension)
        {
            if (!GrpcKafkaState.replyOpening(state))
            {
                doGrpcBegin(traceId, authorization, 0L, emptyRO);
            }
        }

        @Override
        protected void onKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            DirectBuffer buffer,
            int offset,
            int length,
            OctetsFW extension)
        {
            doGrpcData(traceId, authorization, budgetId, reserved, flags, buffer, offset, length);
        }

        @Override
        protected void onKafkaEnd(
            long traceId,
            long authorization)
        {
            if (GrpcKafkaState.replyClosed(fetch.state))
            {
                if (GrpcKafkaState.initialClosed(state))
                {
                    fetch.doKafkaEnd(traceId, authorization);
                }
            }
            doGrpcEnd(traceId, authorization);
        }

        @Override
        protected void onKafkaWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialPad = padding;
            initialBud = budgetId;
            doGrpcWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void cleanup(
            long traceId,
            long authorization)
        {
            doGrpcReset(traceId, authorization, HEADER_VALUE_GRPC_INTERNAL_ERROR);
            doGrpcAbort(traceId, authorization, HEADER_VALUE_GRPC_INTERNAL_ERROR);

            fetch.doKafkaAbort(traceId, authorization);
            fetch.doKafkaReset(traceId, authorization);
        }

        private void doGrpcBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            state = GrpcKafkaState.openingReply(state);

            doBegin(grpc, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, extension);
        }

        private void doGrpcData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            doData(grpc, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, flags, reserved, buffer, offset, length, emptyRO);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doGrpcAbort(
            long traceId,
            long authorization,
            String16FW status)
        {
            if (GrpcKafkaState.replyOpened(state) && !GrpcKafkaState.replyClosed(state))
            {
                replySeq = fetch.replySeq;

                final GrpcAbortExFW grpcAbortEx =
                    grpcAbortExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(grpcTypeId)
                        .status(status)
                        .build();

                doAbort(grpc, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, grpcAbortEx);
            }
            state = GrpcKafkaState.closeReply(state);
        }

        private void doGrpcEnd(
            long traceId,
            long authorization)
        {
            if (!GrpcKafkaState.replyClosed(state))
            {
                replySeq = fetch.replySeq;
                state = GrpcKafkaState.closeReply(state);

                doEnd(grpc, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
            }
        }

        private void doGrpcWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = fetch.initialAck;
            initialMax = fetch.initialMax;

            doWindow(grpc, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding, capabilities);
        }

        private void doGrpcReset(
            long traceId,
            long authorization,
            String16FW status)
        {
            if (!GrpcKafkaState.initialClosed(state))
            {
                state = GrpcKafkaState.closeInitial(state);

                final GrpcResetExFW grpcResetEx =
                    grpcResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(grpcTypeId)
                        .status(status)
                        .build();

                doReset(grpc, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, grpcResetEx);
            }
        }
    }

    private final class KafkaFetchProxy
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final GrpcKafkaWithFetchResult result;
        private final GrpcProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private KafkaFetchProxy(
            long originId,
            long routedId,
            GrpcProxy delegate,
            GrpcKafkaWithFetchResult result)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.result = result;
            this.initialId = supplyInitialId.applyAsLong(routedId);
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

            kafka = newKafkaFetcher(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, result);
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

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
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

                doAbort(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, emptyRO);
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

            final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
            final KafkaBeginExFW kafkaBeginEx =
                beginEx != null && beginEx.typeId() == kafkaTypeId ? extension.get(kafkaBeginExRO::tryWrap) : null;
            final KafkaMergedBeginExFW kafkaMergedBeginEx =
                kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaBeginExFW.KIND_MERGED ? kafkaBeginEx.merged() : null;
            final Array32FW<KafkaOffsetFW> partitions = kafkaMergedBeginEx != null ? kafkaMergedBeginEx.partitions() : null;

            if (kafkaMergedBeginEx != null)
            {
                Varuint32FW len = lenRW.set(partitions.length()).build();
                int lenSize = len.sizeof();
                replyPad = result.fieldId().sizeof() + lenSize + partitions.sizeof();
            }

            delegate.onKafkaBegin(traceId, authorization, extension);
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
                doKafkaReset(traceId, authorization);
                delegate.onKafkaReset(traceId, authorization);
            }
            else
            {
                final int flags = data.flags();
                final OctetsFW payload = data.payload();
                final OctetsFW extension = data.extension();

                final MutableDirectBuffer encodeBuffer = writeBuffer;
                final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
                final int payloadSize = payload.sizeof();

                OctetsFW progress = null;

                if ((flags & DATA_FLAG_INIT) != 0x00)
                {
                    final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                    final KafkaDataExFW kafkaDataEx =
                        dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;
                    final KafkaMergedDataExFW kafkaMergedDataEx =
                        kafkaDataEx != null && kafkaDataEx.kind() == KafkaDataExFW.KIND_MERGED ? kafkaDataEx.merged() : null;
                    progress = kafkaMergedDataEx != null ?
                        messageFieldHelper.encodeProgress(kafkaMergedDataEx.progress()) :
                        null;
                }

                int encodeProgress = encodeOffset;

                encodeBuffer.putBytes(encodeProgress, payload.buffer(), payload.offset(), payloadSize);
                encodeProgress += payloadSize;

                if ((flags & DATA_FLAG_FIN) != 0x00) // FIN
                {
                    Varuint32FW fieldId = result.fieldId();

                    final int fieldIdSize = fieldId.sizeof();
                    encodeBuffer.putBytes(encodeProgress, fieldId.buffer(), fieldId.offset(), fieldIdSize);
                    encodeProgress += fieldIdSize;

                    int progressLength = progress.sizeof();
                    Varuint32FW len = lenRW.set(progressLength).build();
                    int lenSize = len.sizeof();
                    encodeBuffer.putBytes(encodeProgress, len.buffer(), len.offset(), lenSize);
                    encodeProgress += lenSize;
                    encodeBuffer.putBytes(encodeProgress, progress.buffer(), progress.offset(), progressLength);
                    encodeProgress += progressLength;
                }

                int length = encodeProgress - encodeOffset;
                delegate.onKafkaData(traceId, authorization, budgetId, reserved, flags,
                    encodeBuffer, encodeOffset, length, extension);
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
        }

        private void onKafkaSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            doKafkaEnd(traceId, authorization);
        }

        private void doKafkaReset(
            long traceId,
            long authorization)
        {
            if (!GrpcKafkaState.replyClosed(state))
            {
                state = GrpcKafkaState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, extensionRO);
            }
        }

        private void doKafkaWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
            replyAck = Math.max(delegate.replyAck - replyPad, 0);
            replyMax = delegate.replyMax;

            doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding + replyPad, capabilities);
        }
    }

    private final class GrpcProduceProxy extends GrpcProxy
    {
        private final KafkaProduceProxy producer;
        private final KafkaCorrelateProxy correlater;
        private final long resolvedId;

        private GrpcProduceProxy(
            MessageConsumer grpc,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            GrpcKafkaWithProduceResult result)
        {
            super(grpc, originId, routedId, initialId);
            this.resolvedId = resolvedId;
            this.producer = new KafkaProduceProxy(routedId, resolvedId, this, result);
            this.correlater = new KafkaCorrelateProxy(routedId, resolvedId, this, result);
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
            correlater.doKafkaBegin(traceId, authorization, affinity);
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
                        .key(producer.result::key)
                        .headers(producer.result::headers))
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

            producer.doKafkaEnd(traceId, authorization);
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

        private void onGrpcReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            state = GrpcKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            producer.doKafkaReset(traceId, authorization);
            correlater.doKafkaReset(traceId, authorization);
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

            correlater.doKafkaWindow(traceId);
        }

        @Override
        protected void onKafkaReset(
            long traceId,
            long authorization)
        {
            cleanup(traceId, authorization);
        }

        @Override
        protected void onKafkaAbort(
            long traceId,
            long authorization)
        {
            cleanup(traceId, authorization);
        }

        @Override
        protected void onKafkaBegin(
            long traceId,
            long authorization, OctetsFW extension)
        {
            if (!GrpcKafkaState.replyOpening(state))
            {
                doGrpcBegin(traceId, authorization, 0L, emptyRO);
            }
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
                if (payload == null)
                {
                    final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                    final KafkaDataExFW kafkaDataEx =
                        dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;

                    KafkaHeaderFW grpcStatus = kafkaDataEx.merged().headers()
                        .matchFirst(h -> HEADER_NAME_ZILLA_GRPC_STATUS.value().equals(h.name().value()));

                    if (grpcStatus != null &&
                        !HEADER_VALUE_GRPC_OK.value().equals(grpcStatus.value().value()))
                    {
                        String16FW status = statusRW
                            .set(grpcStatus.value().buffer(), grpcStatus.offset(), grpcStatus.sizeof())
                            .build();
                        doGrpcAbort(traceId, authorization, status);
                    }
                    else
                    {
                        doGrpcEnd(traceId, traceId);
                    }
                    correlater.doKafkaEnd(traceId, authorization);
                }
                else if (GrpcKafkaState.replyOpening(state))
                {
                    doGrpcData(traceId, authorization, budgetId, reserved, flags, payload);
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
                if (GrpcKafkaState.initialClosed(state))
                {
                    producer.doKafkaEnd(traceId, authorization);
                    correlater.doKafkaEnd(traceId, authorization);
                }
            }
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

        private void cleanup(
            long traceId,
            long authorization)
        {
            doGrpcReset(traceId, authorization);
            doGrpcAbort(traceId, authorization, HEADER_VALUE_GRPC_INTERNAL_ERROR);

            producer.doKafkaAbort(traceId, authorization);
            producer.doKafkaReset(traceId, authorization);
            correlater.doKafkaAbort(traceId, authorization);
            correlater.doKafkaReset(traceId, authorization);
        }

        private void doGrpcBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            state = GrpcKafkaState.openingReply(state);

            doBegin(grpc, originId, routedId, replyId, replySeq, replyAck, replyMax,
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
            doData(grpc, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, flags, reserved, payload, emptyRO);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doGrpcAbort(
            long traceId,
            long authorization,
            String16FW status)
        {
            if (GrpcKafkaState.replyOpened(state) && !GrpcKafkaState.replyClosed(state))
            {
                replySeq = correlater.replySeq;

                final GrpcAbortExFW grpcAbortEx =
                    grpcAbortExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(grpcTypeId)
                        .status(status)
                        .build();

                doAbort(grpc, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, grpcAbortEx);
            }
            state = GrpcKafkaState.closeReply(state);
        }

        private void doGrpcEnd(
            long traceId,
            long authorization)
        {
            if (!GrpcKafkaState.replyClosed(state))
            {
                replySeq = correlater.replySeq;
                state = GrpcKafkaState.closeReply(state);

                doEnd(grpc, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
            }
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

            doWindow(grpc, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding, capabilities);
        }

        private void doGrpcReset(
            long traceId,
            long authorization)
        {
            if (!GrpcKafkaState.initialClosed(state))
            {
                state = GrpcKafkaState.closeInitial(state);

                final GrpcResetExFW grpcResetEx =
                    grpcResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(grpcTypeId)
                        .status(HEADER_VALUE_GRPC_INTERNAL_ERROR)
                        .build();

                doReset(grpc, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, grpcResetEx);
            }
        }
    }

    private final class KafkaProduceProxy
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final GrpcKafkaWithProduceResult result;
        private final GrpcProduceProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long initialBud;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long replyBud;
        private int replyPad;
        private int replyCap;

        private KafkaProduceProxy(
            long originId,
            long routedId,
            GrpcProduceProxy delegate,
            GrpcKafkaWithProduceResult result)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.result = result;
            this.initialId = supplyInitialId.applyAsLong(routedId);
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

            kafka = newKafkaProducer(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, result);
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
            doData(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
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

                doKafkaDataNull(traceId, authorization);

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
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

                doKafkaDataNull(traceId, authorization);

                doAbort(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, emptyRO);
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
            initialBud = budgetId;
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

            doKafkaReset(traceId, authorization);
        }

        private void doKafkaReset(
            long traceId,
            long authorization)
        {
            if (!GrpcKafkaState.replyClosed(state))
            {
                state = GrpcKafkaState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, emptyRO);
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

                doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, 0L, replyBud, replyPad, replyCap);
            }
        }

        private void doKafkaDataNull(
            long traceId,
            long authorization)
        {
            Flyweight tombstoneDataEx = kafkaDataExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m
                    .timestamp(now().toEpochMilli())
                    .partition(p -> p.partitionId(-1).partitionOffset(-1))
                    .key(result::key)
                    .headers(result::headers))
                .build();

            doKafkaData(traceId, authorization, initialBud, 0, DATA_FLAG_COMPLETE, null, tombstoneDataEx);
        }
    }

    private final class KafkaCorrelateProxy
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final GrpcKafkaWithProduceResult result;
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
            long originId,
            long routedId,
            GrpcProxy delegate,
            GrpcKafkaWithProduceResult result)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.result = result;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            initialMax = delegate.initialMax;
            state = GrpcKafkaState.openingInitial(state);

            kafka = newKafkaCorrelater(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, result);

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

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
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

                doAbort(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, emptyRO);
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
                doKafkaReset(traceId, authorization);
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

            doKafkaReset(traceId, authorization);
        }

        private void onKafkaSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            doKafkaEnd(traceId, authorization);
        }

        private void doKafkaReset(
            long traceId,
            long authorization)
        {
            if (!GrpcKafkaState.replyClosed(state) && kafka != null)
            {
                state = GrpcKafkaState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, emptyRO);
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

                doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, 0L, replyBud, replyPad, replyCap);
            }
        }
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
        DirectBuffer buffer,
        int offset,
        int length,
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
            .payload(buffer, offset, length)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());
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
        long authorization,
        Flyweight extension)
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
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private MessageConsumer newKafkaFetcher(
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
        GrpcKafkaWithFetchResult result)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(FETCH_ONLY))
                    .topic(result.topic())
                    .partitions(result::partitions)
                    .filters(result::filters))
                .build();

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
            .extension(kafkaBeginEx.buffer(), kafkaBeginEx.offset(), kafkaBeginEx.sizeof())
            .build();

        MessageConsumer receiver =
            streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private MessageConsumer newKafkaProducer(
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
        GrpcKafkaWithProduceResult result)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(PRODUCE_ONLY))
                              .topic(result.topic())
                              .partitionsItem(p -> p.partitionId(-1).partitionOffset(-2L))
                              .ackMode(result::acks))
                .build();

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
                .extension(kafkaBeginEx.buffer(), kafkaBeginEx.offset(), kafkaBeginEx.sizeof())
                .build();

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private MessageConsumer newKafkaCorrelater(
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
        GrpcKafkaWithProduceResult result)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(FETCH_ONLY))
                              .topic(result.replyTo())
                              .partitions(result::partitions)
                              .filters(result::filters))
                .build();

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
                .extension(kafkaBeginEx.buffer(), kafkaBeginEx.offset(), kafkaBeginEx.sizeof())
                .build();

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
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

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer sender,
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

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }
}
