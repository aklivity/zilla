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
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.lang.System.currentTimeMillis;
import static java.time.Instant.now;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

public final class KafkaGrpcRemoteServerFactory implements KafkaGrpcStreamFactory
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
    private final List<KafkaRemoteServer> servers;
    private final BufferPool bufferPool;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final Function<Long, Boolean> doSignal;
    private final Signaler signaler;
    private final int grpcTypeId;
    private final int kafkaTypeId;
    private long reconnectAt = NO_CANCEL_ID;


    public KafkaGrpcRemoteServerFactory(
        KafkaGrpcConfiguration config,
        EngineContext context,
        Function<Long, Boolean> doSignal)
    {
        this.bufferPool = context.bufferPool();
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.signaler = context.signaler();
        this.supplyTraceId = context::supplyTraceId;
        this.doSignal = doSignal;
        this.bindings = new Long2ObjectHashMap<>();
        this.servers = new ArrayList<>();
        this.grpcTypeId = context.supplyTypeId(GRPC_TYPE_NAME);
        this.kafkaTypeId = context.supplyTypeId(KAFKA_TYPE_NAME);
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        KafkaGrpcBindingConfig newBinding = new KafkaGrpcBindingConfig(binding);
        bindings.put(binding.id, newBinding);

        if (doSignal.apply(binding.id))
        {
            newBinding.routes.forEach(r ->
                r.when.forEach(c ->
                {
                    KafkaGrpcConditionResult condition = c.resolve();
                    servers.add(
                        new KafkaRemoteServer(newBinding.id, newBinding.options.entryId, r.id, condition, newBinding.helper));
                }));

            this.reconnectAt = signaler.signalAt(
                currentTimeMillis(),
                SIGNAL_INITIATE_KAFKA_STREAM,
                this::onKafkaStreamInitializationSignal);
        }
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);

        signaler.cancel(reconnectAt);
        reconnectAt = NO_CANCEL_ID;
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer grpc)
    {
        return null;
    }

    private final class KafkaRemoteServer
    {
        private MessageConsumer kafka;
        private final KafkaGrpcConditionResult condition;
        private final KafkaErrorProducer errorProducer;
        private final Map<OctetsFW, GrpcClient> grpcClients;
        private final KafkaGrpcFetchHeaderHelper helper;
        private final long originId;
        private final long routedId;
        private final long entryId;
        private final long initialId;
        private final long replyId;

        private OctetsFW lastCorrelationId;
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

        private KafkaRemoteServer(
            long originId,
            long routedId,
            long entryId,
            KafkaGrpcConditionResult condition,
            KafkaGrpcFetchHeaderHelper helper)
        {
            this.entryId = entryId;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.replyAck = 0;
            this.replyMax = bufferPool.slotCapacity();
            this.replyBud = 0;
            this.replyPad = 0;
            this.replyCap = 0;
            this.errorProducer = new KafkaErrorProducer(originId, routedId, condition, this);
            this.grpcClients = new Object2ObjectHashMap<>();
            this.condition = condition;
            this.helper = helper;
        }
        private void initiate(
            long traceId)
        {
            doKafkaBegin(traceId, 0L, 0L);
        }

        private void removeIfClosed(
            OctetsFW correlationId)
        {
            GrpcClient grpcClient = grpcClients.get(correlationId);
            if (grpcClient != null && KafkaGrpcState.closed(grpcClient.state))
            {
                grpcClients.remove(correlationId);
            }
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
            if (!KafkaGrpcState.initialOpening(state))
            {
                state = KafkaGrpcState.openingInitial(state);

                kafka = newKafkaFetch(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, condition);
            }
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
                helper.visit(kafkaDataEx);

                if (helper.resolved())
                {
                    GrpcClient grpcClient = grpcClients.get(helper.correlationId);
                    if (grpcClient == null)
                    {
                        final int correlationLength = helper.correlationId.sizeof();
                        OctetsFW newCorrelationId = new OctetsFW.Builder()
                            .wrap(new UnsafeBuffer(new byte[correlationLength]), 0, correlationLength)
                            .set(helper.correlationId)
                            .build();
                        lastCorrelationId = newCorrelationId;
                        grpcClient = newGrpcClient(traceId, authorization, helper.service, helper.method, newCorrelationId);
                    }

                    flushGrpcClientData(grpcClient, traceId, authorization, helper.service, helper.method, flags, payload);
                }
                else if (helper.correlationId != null)
                {
                    errorProducer.onKafkaError(traceId, authorization, helper.correlationId);
                }
            }
            else
            {
                GrpcClient grpcClient = grpcClients.get(lastCorrelationId);
                if (grpcClient != null)
                {
                    flushGrpcClientData(grpcClient, traceId, authorization, null, null, flags, payload);
                }
            }

            doKafkaWindow(traceId, authorization);
        }

        private GrpcClient newGrpcClient(
            long traceId,
            long authorization,
            OctetsFW service,
            OctetsFW method,
            OctetsFW correlationId)
        {
            final GrpcClient grpcClient = new GrpcClient(originId, entryId, routedId, correlationId, this);
            grpcClients.put(correlationId, grpcClient);

            grpcClient.doGrpcBegin(traceId, authorization, 0L, service, method);

            return grpcClient;
        }

        private void flushGrpcMessagesIfBuffered(
            long traceId,
            long authorization,
            OctetsFW correlationId)
        {
            int progressOffset = 0;

            flush:
            while (progressOffset < grpcQueueSlotOffset)
            {
                final MutableDirectBuffer grpcQueueBuffer = bufferPool.buffer(grpcQueueSlot);
                final GrpcQueueMessageFW queueMessage = queueMessageRO
                    .wrap(grpcQueueBuffer, progressOffset, grpcQueueSlotOffset);

                final OctetsFW messageCorrelationId = queueMessage.correlationId();
                final OctetsFW service = queueMessage.service();
                final OctetsFW method = queueMessage.method();
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
                    GrpcClient grpcClient = grpcClients.get(messageCorrelationId);
                    grpcClient = grpcClient != null ? grpcClient :
                        newGrpcClient(traceId, authorization, service, method, messageCorrelationId);

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
                        queueGrpcMessage(traceId, authorization, lastCorrelationId, service, method, flags,
                            payload, remainingPayload);
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
            OctetsFW service,
            OctetsFW method,
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
                queueGrpcMessage(traceId, authorization, grpcClient.correlationId, service, method,
                    flags, payload, remaining);
            }
        }

        private void queueGrpcMessage(
            long traceId,
            long authorization,
            OctetsFW correlationId,
            OctetsFW service,
            OctetsFW method,
            int flags,
            OctetsFW payload,
            int length)
        {
            acquireQueueSlotIfNecessary();
            final MutableDirectBuffer grpcQueueBuffer = bufferPool.buffer(grpcQueueSlot);
            final GrpcQueueMessageFW queueMessage = queueMessageRW
                .wrap(grpcQueueBuffer, grpcQueueSlotOffset, grpcQueueBuffer.capacity())
                .correlationId(correlationId)
                .service(service)
                .method(method)
                .traceId(traceId)
                .authorization(authorization)
                .flags(flags)
                .value(payload.buffer(), payload.offset(), length)
                .build();

            grpcQueueSlotOffset = queueMessage.limit();
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

    private final class KafkaCorrelateProxy
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final KafkaGrpcConditionResult condition;
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

        private KafkaCorrelateProxy(
            long originId,
            long routedId,
            KafkaGrpcConditionResult condition,
            GrpcClient delegate)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.condition = condition;
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
                traceId, authorization, affinity, condition);
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

                doKafkaTombstone(traceId, authorization, HEADER_VALUE_GRPC_OK);

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        private void doKafkaAbort(
            long traceId,
            long authorization,
            String16FW status)
        {
            if (KafkaGrpcState.initialOpening(state) &&
                !KafkaGrpcState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = KafkaGrpcState.closeInitial(state);

                doKafkaTombstone(traceId, authorization, status);

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
            long authorization,
            String16FW status)
        {
            Flyweight tombstoneDataEx = kafkaDataExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m
                    .timestamp(now().toEpochMilli())
                    .partition(p -> p.partitionId(-1).partitionOffset(-1))
                    .key(k -> condition.key(delegate.correlationId, k))
                    .headers(h -> condition.headersWithStatusCode(delegate.correlationId, status, h)))
                .build();

            doKafkaData(traceId, authorization, delegate.initialBud, 0, DATA_FLAG_COMPLETE, null, tombstoneDataEx);
        }
    }

    private final class KafkaErrorProducer
    {
        private MessageConsumer kafka;
        private final KafkaRemoteServer server;
        private final KafkaGrpcConditionResult condition;
        private final List<OctetsFW> correlationIds;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

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

        private KafkaErrorProducer(
            long originId,
            long routedId,
            KafkaGrpcConditionResult condition,
            KafkaRemoteServer server)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.condition = condition;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.server = server;
            this.correlationIds = new ArrayList<>();
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            state = KafkaGrpcState.openingInitial(state);

            kafka = newKafkaProducer(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, condition);
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
        }

        private void onKafkaWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final int maximum = window.maximum();

            assert acknowledge <= sequence;

            initialAck = acknowledge;
            initialMax = maximum;
            state = KafkaGrpcState.openInitial(state);

            assert initialAck <= initialSeq;

            doKafkaDataNull(traceId, authorization);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            assert acknowledge <= sequence;

            state = KafkaGrpcState.closeInitial(state);

            doKafkaReset(traceId, authorization);
        }

        private void onKafkaError(
            long traceId,
            long authorization,
            OctetsFW correlationId)
        {
            correlationIds.add(new OctetsFW.Builder()
                .wrap(new UnsafeBuffer(new byte[correlationId.sizeof()]), 0, correlationId.sizeof())
                .set(correlationId)
                .build());

            if (!KafkaGrpcState.initialOpened(state))
            {
                doKafkaBegin(traceId, authorization, 0);
            }
            else
            {
                doKafkaDataNull(traceId, authorization);
            }
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


        private void doKafkaDataNull(
            long traceId,
            long authorization)
        {
            correlationIds.forEach(c ->
            {
                Flyweight tombstoneDataEx = kafkaDataExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m -> m
                        .timestamp(now().toEpochMilli())
                        .partition(p -> p.partitionId(-1).partitionOffset(-1))
                        .key(k -> condition.key(c, k))
                        .headers(h -> condition.headersWithStatusCode(c, HEADER_VALUE_GRPC_INTERNAL_ERROR, h)))
                    .build();
                doKafkaData(traceId, authorization, 0, 0, DATA_FLAG_COMPLETE, null, tombstoneDataEx);
            });

            correlationIds.clear();
        }
    }

    private final class GrpcClient
    {
        private MessageConsumer grpc;
        private final KafkaRemoteServer server;
        private final KafkaCorrelateProxy correlater;
        private final OctetsFW correlationId;
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
            OctetsFW correlationId,
            KafkaRemoteServer server)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.correlationId = correlationId;
            this.server = server;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.correlater = new KafkaCorrelateProxy(originId, resolveId, server.condition, this);
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
                        .key(k -> server.condition.key(correlationId, k))
                        .headers(h -> server.condition.headers(correlationId, h)))
                    .build();
            }

            correlater.doKafkaData(traceId, authorization, budgetId, reserved, flags, payload, kafkaDataEx);
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

            correlater.doKafkaEnd(traceId, authorization);

            server.removeIfClosed(correlationId);
        }

        private void onGrpcAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            final OctetsFW extension = abort.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = KafkaGrpcState.closeReply(state);

            assert replyAck <= replySeq;

            final GrpcAbortExFW abortEx = extension != null ? extension.get(abortExRO::tryWrap) : null;

            final String16FW status = abortEx != null ? abortEx.status() : HEADER_VALUE_GRPC_ABORTED;

            correlater.doKafkaAbort(traceId, authorization, status);

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

            cleanup(traceId, authorization);

            server.flushGrpcMessagesIfBuffered(traceId, authorization, correlationId);
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

            server.flushGrpcMessagesIfBuffered(traceId, authorization, correlationId);
        }

        private void onKafkaReset(
            long traceId,
            long authorization)
        {
            cleanup(traceId, authorization);
        }

        private void onKafkaAbort(
            long traceId,
            long authorization)
        {
            cleanup(traceId, authorization);
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
            if (KafkaGrpcState.replyClosed(correlater.state))
            {
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

        private void cleanup(
            long traceId,
            long authorization)
        {
            doGrpcReset(traceId, authorization);
            doGrpcAbort(traceId, authorization);

            correlater.doKafkaAbort(traceId, authorization, HEADER_VALUE_GRPC_INTERNAL_ERROR);
            correlater.doKafkaReset(traceId, authorization);

            server.removeIfClosed(correlationId);
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
                traceId, authorization, affinity, server.condition.scheme(), server.condition.authority(),
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
            server.replyReserved -= length;

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
            replyAck = correlater.initialAck;
            replyMax = correlater.initialMax;

            doWindow(grpc, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding, capabilities);
        }

        private void doGrpcReset(
            long traceId,
            long authorization)
        {
            if (KafkaGrpcState.replyOpening(state) &&
                !KafkaGrpcState.replyClosed(state))
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
        KafkaGrpcConditionResult condition)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(PRODUCE_ONLY))
                              .topic(condition.replyTo())
                              .partitionsItem(p -> p.partitionId(-1).partitionOffset(-2L))
                              .ackMode(condition::acks))
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

        servers.forEach(s -> s.initiate(traceId));
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
                .service(service.buffer(), service.offset(), service.sizeof())
                .method(method.buffer(), method.offset(), method.sizeof())
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
        KafkaGrpcConditionResult condition)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(FETCH_ONLY))
                    .topic(condition.topic())
                    .partitions(condition::partitions)
                    .filters(condition::filters))
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
