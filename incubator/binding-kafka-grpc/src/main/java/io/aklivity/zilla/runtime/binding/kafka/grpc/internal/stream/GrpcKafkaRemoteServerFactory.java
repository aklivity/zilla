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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal.stream;

import static io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaCapabilities.FETCH_ONLY;
import static io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaCapabilities.PRODUCE_ONLY;
import static io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcKind.STREAM;
import static io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcKind.UNARY;
import static io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer.NOOP;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.lang.System.currentTimeMillis;
import static java.time.Instant.now;

import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.KafkaGrpcConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config.KafkaGrpcBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config.KafkaGrpcConditionResult;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.queue.GrpcQueueMessageFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcAbortExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcKind;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcResetExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class GrpcKafkaRemoteServerFactory implements KafkaGrpcStreamFactory
{
    private static final String GRPC_TYPE_NAME = "grpc";
    private static final String KAFKA_TYPE_NAME = "kafka";

    private static final int SIGNAL_INITIATE_KAFKA_STREAM = 1;

    private static final int DATA_FLAG_INIT = 0x02;
    private static final int DATA_FLAG_FIN = 0x01;

    private static final String16FW HEADER_VALUE_GRPC_OK = new String16FW("0");
    private static final String16FW HEADER_VALUE_GRPC_ABORTED = new String16FW("10");
    private static final String16FW HEADER_VALUE_GRPC_UNIMPLEMENTED = new String16FW("12");
    private static final String16FW HEADER_VALUE_GRPC_INTERNAL_ERROR = new String16FW("13");

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final SignalFW signalRO = new SignalFW();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final GrpcBeginExFW grpcBeginExRO = new GrpcBeginExFW();
    private final GrpcDataExFW grpcDataExRO = new GrpcDataExFW();
    private final GrpcResetExFW resetExRO = new GrpcResetExFW();
    private final GrpcAbortExFW abortExRO = new GrpcAbortExFW();

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();
    private final GrpcQueueMessageFW queueMessageRO = new GrpcQueueMessageFW();

    private final GrpcBeginExFW.Builder grpcBeginExRW = new GrpcBeginExFW.Builder();
    private final GrpcDataExFW.Builder grpcDataExRW = new GrpcDataExFW.Builder();
    private final GrpcResetExFW.Builder grpcResetExRW = new GrpcResetExFW.Builder();
    private final GrpcAbortExFW.Builder grpcAbortExRW = new GrpcAbortExFW.Builder();

    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final GrpcQueueMessageFW.Builder queueMessageRW = new GrpcQueueMessageFW.Builder();

    private final Long2ObjectHashMap<KafkaGrpcBindingConfig> bindings;
    private final BufferPool bufferPool;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final Signaler signaler;
    private final int grpcTypeId;
    private final int kafkaTypeId;
    private long reconnectAt = NO_CANCEL_ID;
    private long lastBindingId;


    public GrpcKafkaRemoteServerFactory(
        KafkaGrpcConfiguration config,
        EngineContext context)
    {
        this.bufferPool = context.bufferPool();
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.signaler = context.signaler();
        this.supplyTraceId = context::supplyTraceId;
        this.bindings = new Long2ObjectHashMap<>();
        this.grpcTypeId = context.supplyTypeId(GRPC_TYPE_NAME);
        this.kafkaTypeId = context.supplyTypeId(KAFKA_TYPE_NAME);
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        KafkaGrpcBindingConfig newBinding = new KafkaGrpcBindingConfig(binding);
        lastBindingId = binding.id;
        bindings.put(lastBindingId, newBinding);

        this.reconnectAt = signaler.signalAt(
            currentTimeMillis(),
            SIGNAL_INITIATE_KAFKA_STREAM,
            this::onKafkaStreamInitializationSignal);
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
        return NOOP;
    }

    private final class RemoteServer
    {
        private final Map<String16FW, GrpcClient> grpcProxies;
        private final KafkaFetchRemote fetch;
        private final KafkaGrpcConditionResult result;
        private final KafkaGrpcFetchHeaderHelper helper;

        private RemoteServer(
            long originId,
            long routedId,
            long entryId,
            KafkaGrpcConditionResult result,
            KafkaGrpcFetchHeaderHelper helper)
        {
            this.grpcProxies = new Object2ObjectHashMap<>();
            this.fetch = new KafkaFetchRemote(originId, routedId, entryId, result, this);
            this.result = result;
            this.helper = helper;
        }
    }

    private abstract class GrpcClient
    {
        protected MessageConsumer grpc;
        protected final KafkaProduceRemote producer;
        protected final String16FW correlationId;
        protected final RemoteServer remoteServer;
        protected final long originId;
        protected final long routedId;
        protected final long initialId;
        protected final long replyId;

        protected long initialSeq;
        protected long initialAck;
        protected int initialMax;
        protected long initialBud;
        protected int initialPad;
        protected int initialCap;

        protected int state;

        protected long replySeq;
        protected long replyAck;
        protected int replyMax;


        private GrpcClient(
            long originId,
            long routedId,
            long resolveId,
            String16FW correlationId,
            RemoteServer remoteServer)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.correlationId = correlationId;
            this.remoteServer = remoteServer;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.producer = new KafkaProduceRemote(originId, resolveId, remoteServer.result, this);
        }

        private int initialPendingAck()
        {
            return (int)(initialSeq - initialAck);
        }

        private int initialWindow()
        {
            return initialMax - initialPendingAck();
        }

        protected void onGrpcMessage(
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

        protected void onGrpcBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = KafkaGrpcState.openReply(state);

            assert replyAck <= replySeq;

            producer.doKafkaBegin(traceId, authorization, affinity);
        }

        protected void onGrpcData(
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
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

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
                        .key(remoteServer.result::key)
                        .headers(h -> remoteServer.result.headers(correlationId, h)))
                    .build();
            }

            producer.doKafkaData(traceId, authorization, budgetId, reserved, flags, payload, kafkaDataEx);

            if ((flags & DATA_FLAG_FIN) != 0x00) // FIN
            {
                producer.doKafkaEnd(traceId, authorization);
            }
        }

        protected void onGrpcEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = KafkaGrpcState.closeReply(state);

            assert replyAck <= replySeq;

            producer.doKafkaEndDeferred(traceId, authorization);

            onGrpcProxyClosed();
        }

        protected void onGrpcAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = KafkaGrpcState.closeReply(state);

            assert replyAck <= replySeq;

            producer.doKafkaAbort(traceId, authorization);

        }

        protected void onGrpcReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum >= initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = KafkaGrpcState.closingInitial(state);

            assert initialAck <= initialSeq;

            producer.doKafkaReset(traceId, authorization);
        }

        protected void onGrpcWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum >= initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            initialBud = budgetId;
            initialPad = padding;
            initialCap = capabilities;
            state = KafkaGrpcState.openReply(state);

            assert initialAck <= initialSeq;

            remoteServer.fetch.flushGrpcMessagesIfBuffered(traceId, authorization);
        }

        protected void onKafkaReset(
            long traceId,
            long authorization)
        {
            onKafkaError(traceId, authorization);
        }

        protected void onKafkaAbort(
            long traceId,
            long authorization)
        {
            onKafkaError(traceId, authorization);
        }

        protected void onKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload)
        {
            if (KafkaGrpcState.replyOpening(state) && payload != null)
            {
                doGrpcData(traceId, authorization, budgetId, reserved, flags, payload.value(), 0, payload.sizeof());
            }

            if ((flags & DATA_FLAG_FIN) != 0x00) // FIN
            {
                doGrpcEnd(traceId, authorization);
            }
        }

        protected void onKafkaEnd(
            long traceId,
            long authorization)
        {
            if (KafkaGrpcState.replyClosed(producer.state))
            {
                if (KafkaGrpcState.initialClosed(state))
                {
                }

                doGrpcEnd(traceId, authorization);
            }
        }

        protected void onKafkaWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            doGrpcWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        protected void onKafkaError(
            long traceId,
            long authorization)
        {
            doGrpcReset(traceId, authorization);
            doGrpcAbort(traceId, authorization);

            producer.doKafkaAbort(traceId, authorization);
            producer.doKafkaReset(traceId, authorization);

            onGrpcProxyClosed();
        }

        protected void doGrpcBegin(
            long traceId,
            long authorization,
            long affinity,
            OctetsFW service,
            OctetsFW method,
            GrpcKind request,
            GrpcKind response)
        {
            state = KafkaGrpcState.openingReply(state);

            grpc = newGrpcStream(this::onGrpcMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, remoteServer.result.scheme(), remoteServer.result.authority(),
                service, method, request, response);
        }

        protected void doGrpcData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            doData(grpc, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, reserved, flags, buffer, offset, length, emptyRO);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        protected void doGrpcAbort(
            long traceId,
            long authorization)
        {
            if (KafkaGrpcState.replyOpened(state) && !KafkaGrpcState.replyClosed(state))
            {
                final GrpcAbortExFW grpcAbortEx =
                    grpcAbortExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(grpcTypeId)
                        .status(HEADER_VALUE_GRPC_INTERNAL_ERROR)
                        .build();

                doAbort(grpc, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, grpcAbortEx);
            }
            state = KafkaGrpcState.closeInitial(state);
        }

        protected void doGrpcEnd(
            long traceId,
            long authorization)
        {
            if (!KafkaGrpcState.replyClosed(state))
            {
                state = KafkaGrpcState.closeReply(state);

                doEnd(grpc, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        protected void doGrpcWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            replyAck = producer.initialAck;
            replyMax = producer.initialMax;

            doWindow(grpc, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding, capabilities);
        }

        protected void doGrpcReset(
            long traceId,
            long authorization)
        {
            if (!KafkaGrpcState.replyClosed(state))
            {
                state = KafkaGrpcState.closeReply(state);

                final GrpcResetExFW grpcResetEx =
                    grpcResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(grpcTypeId)
                        .status(HEADER_VALUE_GRPC_INTERNAL_ERROR)
                        .build();

                doReset(grpc, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, grpcResetEx);
            }
        }

        protected int doEncodeGrpcData(
            long traceId,
            long authorization,
            int flags,
            OctetsFW payload)
        {
            final int payloadLength = payload.sizeof();
            final int length = Math.min(Math.max(initialWindow() - initialPad, 0), payloadLength);

            if (length > 0)
            {
                final int newFlags = payloadLength == length ? flags : flags & DATA_FLAG_INIT;
                doGrpcData(traceId, authorization, initialBud, length + initialPad,
                    newFlags, payload.buffer(), payload.offset(), length);
            }

            if ((flags & DATA_FLAG_INIT) != 0x00)
            {
                doGrpcEnd(traceId, authorization);
            }

            return length;
        }

        protected void onGrpcProxyClosed()
        {
            if (KafkaGrpcState.closed(state))
            {
                final GrpcClient grpcClient = remoteServer.grpcProxies.remove(correlationId);
            }
        }
    }

    private final class KafkaProduceRemote
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final KafkaGrpcConditionResult result;
        private final GrpcClient delegate;

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

        private KafkaProduceRemote(
            long originId,
            long routedId,
            KafkaGrpcConditionResult result,
            GrpcClient delegate)
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
            state = KafkaGrpcState.openingInitial(state);

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
            if (!KafkaGrpcState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = KafkaGrpcState.closeInitial(state);

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        private void doKafkaEndDeferred(
            long traceId,
            long authorization)
        {
            state = KafkaGrpcState.closingInitial(state);
            doKafkaEndAck(traceId, authorization);
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (!KafkaGrpcState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = KafkaGrpcState.closeInitial(state);

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
            state = KafkaGrpcState.openingReply(state);

            assert replyAck <= replySeq;

            doKafkaWindow(traceId);
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
            state = KafkaGrpcState.closeReply(state);

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
            state = KafkaGrpcState.closeReply(state);

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
            assert acknowledge >= delegate.replyAck;
            assert maximum >= delegate.replyMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = KafkaGrpcState.openInitial(state);

            assert initialAck <= initialSeq;

            delegate.onKafkaWindow(authorization, traceId, budgetId, padding, capabilities);

            doKafkaEndAck(authorization, traceId);
        }

        private void doKafkaEndAck(long authorization, long traceId)
        {
            if (KafkaGrpcState.initialClosing(state) && initialSeq == initialAck)
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
            state = KafkaGrpcState.closeInitial(state);

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.onKafkaReset(traceId, authorization);

            doKafkaReset(traceId, authorization);
        }

        private void doKafkaReset(
            long traceId,
            long authorization)
        {
            if (!KafkaGrpcState.replyClosed(state))
            {
                state = KafkaGrpcState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void doKafkaWindow(
            long traceId)
        {
            if (kafka != null && !KafkaGrpcState.replyClosed(state))
            {
                doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, 0L, replyBud, replyPad, replyCap);
            }
        }
    }

    private final class KafkaFetchRemote
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long entryId;
        private final long initialId;
        private final long replyId;
        private final KafkaGrpcConditionResult result;
        private final RemoteServer delegate;
        private String16FW lastCorrelationId;

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
        private int grpcQueueSlot = NO_SLOT;
        private int grpcQueueSlotOffset;

        private KafkaFetchRemote(
            long originId,
            long routedId,
            long entryId,
            KafkaGrpcConditionResult result,
            RemoteServer delegate)
        {
            this.entryId = entryId;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.result = result;
            this.delegate = delegate;
            this.replyAck = 0;
            this.replyMax = bufferPool.slotCapacity();
            this.replyBud = 0;
            this.replyPad = 5;
            this.replyCap = 0;
        }

        private int replyPendingAck()
        {
            return (int)(replySeq - replyAck);
        }

        private int replyWindow()
        {
            return replyMax - replyPendingAck();
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            state = KafkaGrpcState.openingInitial(state);

            kafka = newKafkaFetch(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, result);

        }

        private void doKafkaEnd(
            long traceId,
            long authorization)
        {
            if (!KafkaGrpcState.initialClosed(state))
            {
                state = KafkaGrpcState.closeInitial(state);

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (!KafkaGrpcState.initialClosed(state) && kafka != null)
            {

                state = KafkaGrpcState.closeInitial(state);

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
            }
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = KafkaGrpcState.openingReply(state);

            assert replyAck <= replySeq;

            doKafkaWindow(traceId, authorization);
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
            final int flags = data.flags();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if ((flags & DATA_FLAG_INIT) != 0x00)
            {
                final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                final KafkaDataExFW kafkaDataEx =
                    dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;
                KafkaGrpcFetchHeaderHelper helper = delegate.helper;
                helper.visit(kafkaDataEx);

                if (helper.service != null &&
                    helper.method != null &&
                    helper.request != null &&
                    helper.response != null &&
                    helper.correlationId != null)
                {
                    String16FW newCorrelationId = new String16FW(helper.correlationId.toString());
                    lastCorrelationId = newCorrelationId;

                    GrpcClient grpcClient = delegate.grpcProxies.get(newCorrelationId);
                    if (grpcClient != null)
                    {
                        grpcClient.onKafkaData(traceId, authorization, budgetId, reserved, flags, payload);
                    }
                    else
                    {
                        newGrpcProxy(traceId, authorization, helper.service, helper.method,
                            helper.request, helper.response, newCorrelationId);
                        queueGrpcMessage(traceId, authorization, newCorrelationId, flags, payload);
                    }
                }
                //TODO: Check if correlationId is present
            }
            else
            {
                GrpcClient grpcClient = delegate.grpcProxies.get(lastCorrelationId);
                grpcClient.onKafkaData(traceId, authorization, budgetId, reserved, flags, payload);
            }

            doKafkaWindow(traceId, authorization);
        }

        private void newGrpcProxy(
            long traceId,
            long authorization,
            OctetsFW service,
            OctetsFW method,
            GrpcKind request,
            GrpcKind response,
            String16FW correlationId)
        {

            GrpcClient grpcClient = null;

            if (request == UNARY && response == UNARY)
            {
                grpcClient = new GrpcUnaryClient(originId, entryId, routedId, correlationId, delegate);
            }
            else if (request == STREAM && response == UNARY)
            {
                grpcClient = new GrpcClientStreamClient(originId, entryId, routedId, correlationId, delegate);
            }
            else if (request == UNARY && response == STREAM)
            {
                grpcClient = new GrpcServerStreamClient(originId, entryId, routedId, correlationId, delegate);
            }
            else if (request == STREAM && response == STREAM)
            {
                grpcClient = new GrpcBidiStreamClient(originId, entryId, routedId, correlationId, delegate);
            }

            if (grpcClient != null)
            {
                grpcClient.doGrpcBegin(traceId, authorization, 0L, service, method,
                    request, response);
            }
            delegate.grpcProxies.put(correlationId, grpcClient);
        }

        private void flushGrpcMessagesIfBuffered(
            long traceId,
            long authorization)
        {
            int progressOffset = 0;

            while (progressOffset < grpcQueueSlotOffset)
            {
                final MutableDirectBuffer grpcQueueBuffer = bufferPool.buffer(grpcQueueSlot);
                final GrpcQueueMessageFW queueMessage = queueMessageRO
                    .wrap(grpcQueueBuffer, progressOffset, grpcQueueSlotOffset);

                final String16FW correlationId = queueMessage.correlationId();
                final long messageTraceId = queueMessage.traceId();
                final long messageAuthorization = queueMessage.authorization();
                final int flags = queueMessage.flags();
                final GrpcClient grpcClient = delegate.grpcProxies.get(correlationId);
                final int messageSize = queueMessage.valueLength();
                final int progress = grpcClient.doEncodeGrpcData(messageTraceId, messageAuthorization,
                    flags, queueMessage.value());

                final int queuedMessageSize = queueMessage.sizeof();
                final int oldProgressOffset = progressOffset;
                progressOffset += queuedMessageSize;

                if (progress == messageSize)
                {
                    final int remaining = grpcQueueSlotOffset - progressOffset;
                    grpcQueueBuffer.putBytes(oldProgressOffset, grpcQueueBuffer, progressOffset, remaining);

                    progressOffset = oldProgressOffset;
                    grpcQueueSlotOffset = remaining != 0 ? grpcQueueSlotOffset - remaining : progressOffset;
                }
                else if (progress > 0)
                {
                    final int remainingPayload = queuedMessageSize - progress;
                    final int messagesSlotLimit = grpcQueueSlotOffset;
                    final GrpcQueueMessageFW newQueueMessage = queueMessageRW
                        .wrap(grpcQueueBuffer, messagesSlotLimit, grpcQueueBuffer.capacity())
                        .correlationId(correlationId)
                        .traceId(messageTraceId)
                        .authorization(messageAuthorization)
                        .flags(flags)
                        .value(grpcQueueBuffer, oldProgressOffset, remainingPayload)
                        .build();
                    grpcQueueBuffer.putBytes(progressOffset, grpcQueueBuffer, progressOffset,
                        grpcQueueSlotOffset - progressOffset);

                    int remainingMessageSize = queuedMessageSize - newQueueMessage.sizeof();
                    grpcQueueSlotOffset -= remainingMessageSize;
                }
            }

            cleanupQueueSlotIfNecessary();

            doKafkaWindow(traceId, authorization);
        }

        private void queueGrpcMessage(
            long traceId,
            long authorization,
            String16FW correlationId,
            int flags,
            OctetsFW payload)
        {
            acquireQueueSlotIfNecessary();
            final MutableDirectBuffer grpcQueueBuffer = bufferPool.buffer(grpcQueueSlot);
            final int messagesSlotLimit = grpcQueueSlotOffset;
            final GrpcQueueMessageFW queueMessage = queueMessageRW
                .wrap(grpcQueueBuffer, messagesSlotLimit, grpcQueueBuffer.capacity())
                .correlationId(correlationId)
                .traceId(traceId)
                .authorization(authorization)
                .flags(flags)
                .value(payload.buffer(), payload.offset(), payload.sizeof())
                .build();

            grpcQueueSlotOffset += queueMessage.sizeof();
        }

        private void cleanupQueueSlotIfNecessary()
        {
            if (grpcQueueSlot != NO_SLOT && grpcQueueSlotOffset == 0)
            {
                bufferPool.release(grpcQueueSlot);
                grpcQueueSlot = NO_SLOT;
                grpcQueueSlotOffset = 0;
            }
        }

        private void acquireQueueSlotIfNecessary()
        {
            if (grpcQueueSlot == NO_SLOT)
            {
                grpcQueueSlot = bufferPool.acquire(initialId);
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
            state = KafkaGrpcState.closeReply(state);

            assert replyAck <= replySeq;
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
            state = KafkaGrpcState.closeReply(state);

            assert replyAck <= replySeq;

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

            initialAck = acknowledge;
            initialMax = maximum;
            state = KafkaGrpcState.openInitial(state);

            assert initialAck <= initialSeq;
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            assert acknowledge <= sequence;

            doKafkaReset(traceId, authorization);
        }

        private void doKafkaReset(
            long traceId,
            long authorization)
        {
            if (!KafkaGrpcState.replyClosed(state) && kafka != null)
            {
                state = KafkaGrpcState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, emptyRO);
            }
        }

        private void doKafkaWindow(
            long traceId,
            long authorization)
        {
            long replyAckMax = Math.max(replySeq - replyPendingAck() - grpcQueueSlotOffset, replyAck);

            if (replyWindow() - replyAckMax > 0)
            {
                replyAck = replyAckMax;
                assert replyAck <= replySeq;

                doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, replyBud, replyPad, replyCap);
            }
        }
    }

    private final class GrpcUnaryClient extends GrpcClient
    {
        private GrpcUnaryClient(
            long originId,
            long routedId,
            long resolveId,
            String16FW correlationId,
            RemoteServer delegate)
        {
            super(originId, routedId, resolveId, correlationId, delegate);
        }
    }

    private final class GrpcClientStreamClient extends GrpcClient
    {

        private GrpcClientStreamClient(
            long originId,
            long routedId,
            long resolveId,
            String16FW correlationId,
            RemoteServer delegate)
        {
            super(originId, routedId, resolveId, correlationId, delegate);
        }

        @Override
        protected void onGrpcData(
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
                        .key(remoteServer.result::key)
                        .headers(h -> remoteServer.result.headers(correlationId, h)))
                    .build();
            }

            producer.doKafkaData(traceId, authorization, budgetId, reserved, flags, payload, kafkaDataEx);
        }

        @Override
        protected void onKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload)
        {
            if (KafkaGrpcState.replyOpening(state) && payload != null)
            {
                doGrpcData(traceId, authorization, budgetId, reserved, flags, payload.value(), 0, payload.sizeof());
            }

            if ((flags & DATA_FLAG_FIN) != 0x00) // FIN
            {
                doGrpcEnd(traceId, authorization);
            }
        }
    }

    private final class GrpcServerStreamClient extends GrpcClient
    {
        private GrpcServerStreamClient(
            long originId,
            long routedId,
            long resolveId,
            String16FW correlationId,
            RemoteServer delegate)
        {
            super(originId, routedId, resolveId, correlationId, delegate);
        }

        @Override
        protected void onKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload)
        {
            if (KafkaGrpcState.replyOpening(state) && payload != null)
            {
                doGrpcData(traceId, authorization, budgetId, reserved, flags, payload.value(), 0, payload.sizeof());
            }
        }
    }

    private final class GrpcBidiStreamClient extends GrpcClient
    {
        private GrpcBidiStreamClient(
            long originId,
            long routedId,
            long resolveId,
            String16FW correlationId,
            RemoteServer delegate)
        {
            super(originId, routedId, resolveId, correlationId, delegate);
        }

        @Override
        protected void onGrpcData(
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
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

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
                        .key(remoteServer.result::key)
                        .headers(h -> remoteServer.result.headers(correlationId, h)))
                    .build();
            }

            producer.doKafkaData(traceId, authorization, budgetId, reserved, flags, payload, kafkaDataEx);
        }

        @Override
        protected void onGrpcEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = KafkaGrpcState.closeInitial(state);

            assert initialAck <= initialSeq;

            doGrpcEnd(traceId, authorization);
            onGrpcProxyClosed();
        }

        @Override
        protected void onKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload)
        {
            if (KafkaGrpcState.replyOpening(state) && payload != null)
            {
                doGrpcData(traceId, authorization, budgetId, reserved, flags, payload.value(), 0, payload.sizeof());
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
        int reserved,
        int flags,
        DirectBuffer buffer,
        int index,
        int length,
        Flyweight extension)
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
            .payload(buffer, index, length)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        KafkaGrpcConditionResult result)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(PRODUCE_ONLY))
                              .topic(result.replyTo())
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

    private void onKafkaStreamInitializationSignal(
        int signalId)
    {
        assert signalId == SIGNAL_INITIATE_KAFKA_STREAM;

        this.reconnectAt = NO_CANCEL_ID;

        final long traceId = supplyTraceId.getAsLong();

        KafkaGrpcBindingConfig binding = bindings.get(lastBindingId);

        binding.routes.forEach(r ->
            r.when.forEach(c ->
            {
                KafkaGrpcConditionResult result = c.resolve();
                RemoteServer remoteServer =
                    new RemoteServer(binding.id, r.id, binding.options.entryId, result, binding.helper);
                remoteServer.fetch.doKafkaBegin(traceId, 0L, 0L);
            }));
    }

    private MessageConsumer newGrpcStream(
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
        String16FW scheme,
        String16FW authority,
        OctetsFW service,
        OctetsFW method,
        GrpcKind request,
        GrpcKind response)
    {
        final GrpcBeginExFW grpcBeginEx =
            grpcBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(grpcTypeId)
                .scheme(scheme)
                .authority(authority)
                .service(service.value(), 0, service.sizeof())
                .method(method.value(), 0, method.sizeof())
                .request(r -> r.set(request))
                .response(r -> r.set(response))
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
                .extension(grpcBeginEx.buffer(), grpcBeginEx.offset(), grpcBeginEx.sizeof())
                .build();

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private MessageConsumer newKafkaFetch(
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
        KafkaGrpcConditionResult result)
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
