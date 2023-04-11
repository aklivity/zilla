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

    private static final int DATA_FLAG_COMPLETE = 0x03;
    private static final int DATA_FLAG_INIT = 0x02;
    private static final int DATA_FLAG_FIN = 0x01;
    private static final int DATA_FLAG_CON = 0x00;

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
        private final Map<String16FW, GrpcClient> grpcClients;
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
            this.grpcClients = new Object2ObjectHashMap<>();
            this.fetch = new KafkaFetchRemote(originId, routedId, entryId, result, this);
            this.result = result;
            this.helper = helper;
        }
    }

    private final class GrpcClient
    {
        private MessageConsumer grpc;
        private final KafkaProduceRemote producer;
        private final String16FW correlationId;
        private final RemoteServer remoteServer;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long initialBud;
        private int initialPad;
        private int initialCap;

        private int state;

        private long replySeq;
        private long replyAck;
        private int replyMax;


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
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = KafkaGrpcState.openReply(state);

            assert replyAck <= replySeq;

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

        private void onGrpcEnd(
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

            producer.doKafkaEnd(traceId, authorization);

            onGrpcProxyClosed();
        }

        private void onGrpcAbort(
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

        private void onGrpcReset(
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

        private void onGrpcWindow(
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

            remoteServer.fetch.flushGrpcMessagesIfBuffered(traceId, authorization, correlationId);
        }

        private void onKafkaReset(
            long traceId,
            long authorization)
        {
            onKafkaError(traceId, authorization);
        }

        private void onKafkaAbort(
            long traceId,
            long authorization)
        {
            onKafkaError(traceId, authorization);
        }

        private int onKafkaData(
            long traceId,
            long authorization,
            int flags,
            OctetsFW payload)
        {
            final int payloadLength = payload != null ? payload.sizeof() : 0;
            final int length = Math.min(Math.max(initialWindow() - initialPad, 0), payloadLength);

            if (length > 0)
            {
                final int newFlags = payloadLength == length ? flags : flags & DATA_FLAG_INIT;
                doGrpcData(traceId, authorization, initialBud, length + initialPad,
                    newFlags, payload.value(), 0, length);

                if ((newFlags & DATA_FLAG_FIN) != 0x00) // FIN
                {
                    state = KafkaGrpcState.closingInitial(state);
                }
            }

            if ((payload == null || payload.equals(emptyRO)) &&
                KafkaGrpcState.initialClosing(state))
            {
                doGrpcEnd(traceId, authorization);
            }

            return length;
        }

        private void onKafkaEnd(
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

        private void onKafkaWindow(
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
            producer.doKafkaReset(traceId, authorization);

            onGrpcProxyClosed();
        }

        private void doGrpcBegin(
            long traceId,
            long authorization,
            long affinity,
            OctetsFW service,
            OctetsFW method)
        {
            state = KafkaGrpcState.openingReply(state);

            grpc = newGrpcStream(this::onGrpcMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, remoteServer.result.scheme(), remoteServer.result.authority(),
                service, method);
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
            doData(grpc, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, reserved, flags, buffer, offset, length, emptyRO);

            initialSeq += reserved;
            remoteServer.fetch.replyReserved -= length;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doGrpcAbort(
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

        private void doGrpcEnd(
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

        private void doGrpcWindow(
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

        private void doGrpcReset(
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

        private void onGrpcProxyClosed()
        {
            if (KafkaGrpcState.closed(state))
            {
                remoteServer.grpcClients.remove(correlationId);
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
            initialSeq = delegate.replySeq;
            initialAck = delegate.replyAck;
            initialMax = delegate.replyMax;
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
            if (KafkaGrpcState.initialOpened(state) &&
                !KafkaGrpcState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = KafkaGrpcState.closeInitial(state);

                doKafkaTombstone(traceId, authorization);

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (KafkaGrpcState.initialOpened(state) &&
                !KafkaGrpcState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = KafkaGrpcState.closeInitial(state);

                doKafkaTombstone(traceId, authorization);

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
            if (KafkaGrpcState.replyOpened(state) &&
                !KafkaGrpcState.replyClosed(state))
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

        private void doKafkaTombstone(
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
                    .headers(h -> result.headers(delegate.correlationId, h)))
                .build();

            doKafkaData(traceId, authorization, delegate.initialBud, 0, DATA_FLAG_COMPLETE, null, tombstoneDataEx);
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
        private int replyReserved;
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
            this.replyPad = 0;
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
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();
            int flags = data.flags();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + reserved;
            replyReserved += reserved;

            assert replyAck <= replySeq;

            if ((flags & DATA_FLAG_INIT) != 0x00 &&
                payload != null)
            {
                final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                final KafkaDataExFW kafkaDataEx =
                    dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;
                KafkaGrpcFetchHeaderHelper helper = delegate.helper;
                helper.visit(kafkaDataEx);

                if (helper.resolved())
                {
                    String16FW newCorrelationId = new String16FW(helper.correlationId.toString());
                    lastCorrelationId = newCorrelationId;

                    GrpcClient grpcClient = delegate.grpcClients.get(newCorrelationId);
                    grpcClient = grpcClient == null ?
                        newGrpcProxy(traceId, authorization, helper.service, helper.method, newCorrelationId) :
                        grpcClient;

                    flushGrpcClientData(grpcClient, traceId, authorization, flags, payload);
                }
                //TODO: Check if correlationId is present
            }
            else
            {
                GrpcClient grpcClient = delegate.grpcClients.get(lastCorrelationId);
                if (grpcClient != null)
                {
                    flushGrpcClientData(grpcClient, traceId, authorization, flags, payload);
                }
            }

            doKafkaWindow(traceId, authorization);
        }

        private GrpcClient newGrpcProxy(
            long traceId,
            long authorization,
            OctetsFW service,
            OctetsFW method,
            String16FW correlationId)
        {
            GrpcClient grpcClient = null;

            grpcClient = new GrpcClient(originId, entryId, routedId, correlationId, delegate);

            if (grpcClient != null)
            {
                grpcClient.doGrpcBegin(traceId, authorization, 0L, service, method);
            }
            delegate.grpcClients.put(correlationId, grpcClient);

            return grpcClient;
        }

        private void flushGrpcMessagesIfBuffered(
            long traceId,
            long authorization,
            String16FW correlationId)
        {
            int progressOffset = 0;

            flush:
            while (progressOffset < grpcQueueSlotOffset)
            {
                final MutableDirectBuffer grpcQueueBuffer = bufferPool.buffer(grpcQueueSlot);
                final GrpcQueueMessageFW queueMessage = queueMessageRO
                    .wrap(grpcQueueBuffer, progressOffset, grpcQueueSlotOffset);

                final String16FW messageCorrelationId = queueMessage.correlationId();
                final long messageTraceId = queueMessage.traceId();
                final long messageAuthorization = queueMessage.authorization();
                final int flags = queueMessage.flags();
                final int messageSize = queueMessage.valueLength();
                final OctetsFW payload = queueMessage.value();

                final int queuedMessageSize = queueMessage.sizeof();
                final int oldProgressOffset = progressOffset;
                progressOffset += queuedMessageSize;

                if (correlationId.equals(messageCorrelationId))
                {
                    final GrpcClient grpcClient = delegate.grpcClients.get(messageCorrelationId);
                    final int progress = grpcClient.onKafkaData(messageTraceId, messageAuthorization,
                        flags, payload);


                    if (progress == messageSize)
                    {
                        final int remaining = grpcQueueSlotOffset - progressOffset;
                        grpcQueueBuffer.putBytes(oldProgressOffset, grpcQueueBuffer, progressOffset, remaining);

                        grpcQueueSlotOffset = grpcQueueSlotOffset - progressOffset;
                        progressOffset = oldProgressOffset;
                    }
                    else if (progress > 0)
                    {
                        final int remainingPayload = queuedMessageSize - progress;
                        queueGrpcMessage(traceId, authorization, lastCorrelationId, flags, payload, remainingPayload);
                        final int remainingMessageOffset = grpcQueueSlotOffset - progressOffset;
                        grpcQueueBuffer.putBytes(oldProgressOffset, grpcQueueBuffer, progressOffset, remainingMessageOffset);
                        grpcQueueSlotOffset -= queuedMessageSize;
                        break flush;
                    }
                    else
                    {
                        break flush;
                    }
                }
            }

            cleanupQueueSlotIfNecessary();

            doKafkaWindow(traceId, authorization);
        }

        private void flushGrpcClientData(
            GrpcClient grpcClient,
            long traceId,
            long authorization,
            int flags,
            OctetsFW payload)
        {
            final int progress = grpcClient.onKafkaData(traceId, authorization, flags, payload);
            int length = payload != null ? payload.sizeof() : 0;
            final int remaining = length - progress;
            if (remaining > 0 || payload == null)
            {
                flags = progress == 0 ? flags : DATA_FLAG_CON;
                payload = payload == null ? emptyRO : payload;
                queueGrpcMessage(traceId, authorization, grpcClient.correlationId, flags, payload, remaining);
            }
        }

        private void queueGrpcMessage(
            long traceId,
            long authorization,
            String16FW correlationId,
            int flags,
            OctetsFW payload,
            int length)
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
                .value(payload.buffer(), payload.offset(), length)
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
            long replyAckMax = Math.max(replySeq - replyReserved, replyAck);

            if (replyWindow() - replyAckMax > 0)
            {
                replyAck = replyAckMax;
                assert replyAck <= replySeq;

                doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, replyBud, replyPad, replyCap);
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
        OctetsFW method)
    {
        final GrpcBeginExFW grpcBeginEx =
            grpcBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(grpcTypeId)
                .scheme(scheme)
                .authority(authority)
                .service(service.value(), 0, service.sizeof())
                .method(method.value(), 0, method.sizeof())
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
