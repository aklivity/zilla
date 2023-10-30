/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.kafka.internal.types.ProxyAddressProtocol.STREAM;
import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_BUDGET_ID;
import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_CREDITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.lang.System.currentTimeMillis;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongArrayQueue;
import org.agrona.collections.LongHashSet;
import org.agrona.collections.MutableInteger;
import org.agrona.collections.MutableLong;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.budget.MergedBudgetCreditor;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.ProxyAddressInetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.RequestHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.ResponseHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class KafkaClientConnectionPool
{
    private static final long NO_OFFSET = -1L;
    private static final int KAFKA_FRAME_LENGTH_FIELD_OFFSET = 4;
    private static final int FLAG_FIN = 0x01;
    private static final int FLAG_INIT = 0x02;
    private static final int FLAG_SKIP = 0x08;
    private static final int FLAG_NONE = 0x00;
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private static final int SIGNAL_STREAM_BEGIN = 0x80000001;
    private static final int SIGNAL_STREAM_DATA = 0x80000002;
    private static final int SIGNAL_STREAM_END = 0x80000003;
    private static final int SIGNAL_STREAM_ABORT = 0x80000004;
    private static final int SIGNAL_STREAM_RESET = 0x80000005;
    private static final int SIGNAL_STREAM_WINDOW = 0x80000006;
    private static final int SIGNAL_CONNECTION_CLEANUP = 0x80000007;
    private static final String CLUSTER = "";

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final SignalFW signalRO = new SignalFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ProxyBeginExFW proxyBeginExRO = new ProxyBeginExFW();
    private final ResponseHeaderFW responseHeaderRO = new ResponseHeaderFW();

    private final ProxyBeginExFW.Builder proxyBeginExRW = new ProxyBeginExFW.Builder();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final SignalFW.Builder signalRW = new SignalFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();

    private final RequestHeaderFW.Builder requestHeaderRW = new RequestHeaderFW.Builder();

    private final RequestHeaderFW requestHeaderRO = new RequestHeaderFW();

    private final MutableLong replyAckRW = new MutableLong();
    private final MutableInteger replyPadRW = new MutableInteger();
    private final MutableInteger replyMaxRW = new MutableInteger();

    private final MergedBudgetCreditor creditor;
    private final int proxyTypeId;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer encodeBuffer;
    private final KafkaClientSignaler signaler;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final Object2ObjectHashMap<String, KafkaClientConnection> connectionPool;
    private final Long2ObjectHashMap<KafkaClientStream> streamsByInitialId;

    public KafkaClientConnectionPool(
        KafkaConfiguration config,
        EngineContext context,
        MergedBudgetCreditor creditor)
    {
        this.proxyTypeId = context.supplyTypeId("proxy");
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.encodeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.signaler = new KafkaClientSignaler(context.signaler());
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.creditor = creditor;
        this.connectionPool = new Object2ObjectHashMap();
        this.streamsByInitialId = new Long2ObjectHashMap<>();
    }

    private MessageConsumer newStream(
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
        final long authorization = begin.authorization();

        assert (initialId & 0x0000_0000_0000_0001L) != 0L;

        final OctetsFW extension = begin.extension();
        final ProxyBeginExFW proxyBeginEx = extension.get(proxyBeginExRO::tryWrap);

        MessageConsumer newStream = null;
        String address = CLUSTER;

        if (proxyBeginEx != null)
        {
            final ProxyAddressInetFW inet = proxyBeginEx.address().inet();
            String host = inet.destination().asString();
            int port = inet.destinationPort();
            address = String.format("%s:%d", host, port);
        }

        final KafkaClientConnection connection = connectionPool.computeIfAbsent(address, s ->
            newConnection(originId, routedId, authorization));
        newStream = connection.newStream(msgTypeId, buffer, index, length, sender);

        return newStream;
    }

    private KafkaClientConnection newConnection(
        long originId,
        long routedId,
        long authorization)
    {
        return new KafkaClientConnection(originId, routedId, authorization);
    }

    private MessageConsumer newNetworkStream(
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
        Consumer<OctetsFW.Builder> extension)
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
                .extension(extension)
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
        Consumer<OctetsFW.Builder> extension)
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
                .extension(extension)
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
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer payload,
        int offset,
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
            .payload(payload, offset, length)
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
        long authorization,
        Consumer<OctetsFW.Builder> extension)
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
        Consumer<OctetsFW.Builder> extension)
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

    private void doSignal(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        int signalId,
        OctetsFW payload)
    {
        final SignalFW signal = signalRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(0)
            .traceId(traceId)
            .cancelId(0)
            .signalId(signalId)
            .contextId(0)
            .payload(payload)
            .build();

        receiver.accept(signal.typeId(), signal.buffer(), signal.offset(), signal.sizeof());
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
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
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
                .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    public BindingHandler streamFactory()
    {
        return this::newStream;
    }

    public class KafkaClientSignaler implements Signaler
    {
        private final Signaler delegate;

        public KafkaClientSignaler(
            Signaler delegate)
        {

            this.delegate = delegate;
        }

        @Override
        public long signalAt(
            long timeMillis,
            int signalId,
            IntConsumer handler)
        {
            return delegate.signalAt(timeMillis, signalId, handler);
        }

        @Override
        public void signalNow(
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId,
            int contextId)
        {
            assert contextId == 0;

            KafkaClientStream stream = streamsByInitialId.get(streamId);
            stream.doStreamSignalNow(traceId, signalId);
        }

        @Override
        public void signalNow(
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId,
            int contextId,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            assert contextId == 0;

            KafkaClientStream stream = streamsByInitialId.get(streamId);
            stream.doStreamSignalNow(traceId, signalId, buffer, offset, length);
        }
        @Override
        public long signalAt(
            long timeMillis,
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId,
            int contextId)
        {
            assert contextId == 0;

            KafkaClientStream stream = streamsByInitialId.get(streamId);
            return stream.doStreamSignalAt(traceId, timeMillis, signalId);
        }

        @Override
        public long signalTask(
            Runnable task,
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId,
            int contextId)
        {
            return 0;
        }

        @Override
        public boolean cancel(
            long cancelId)
        {
            return delegate.cancel(cancelId);
        }
    }

    public Signaler signaler()
    {
        return signaler;
    }

    final class KafkaClientStream
    {
        private final KafkaClientConnection connection;
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final MessageConsumer sender;

        private final long initialId;
        private final long replyId;
        private long initialSeq;
        private long initialAck;
        private final LongArrayQueue initialSeqOffset;
        private long initialAckSnapshot;

        private long replySeq;
        private long replyAck;
        private final LongArrayQueue replySeqOffset;
        private final LongArrayQueue replyAckOffset;
        private long replyAckSnapshot;
        private int replyMax;
        private int replyPad;
        private long replyBud;

        private int nextRequestId;
        private int nexResponseId;
        private int requestBytes;
        private int responseBytes;

        private int state;


        private KafkaClientStream(
            KafkaClientConnection connection,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization)
        {
            this.connection = connection;
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.initialSeqOffset = new LongArrayQueue(NO_OFFSET);
            this.replySeqOffset = new LongArrayQueue(NO_OFFSET);
            this.replyAckOffset = new LongArrayQueue(NO_OFFSET);
        }

        private void onStreamMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onStreamBeginInit(begin);
                connection.doConnectionSignalNow(initialId, 0, SIGNAL_STREAM_BEGIN, buffer, index, length);
                break;
            case DataFW.TYPE_ID:
                connection.doConnectionSignalNow(initialId, 0, SIGNAL_STREAM_DATA, buffer, index, length);
                break;
            case EndFW.TYPE_ID:
                connection.doConnectionSignalNow(initialId, 0, SIGNAL_STREAM_END, buffer, index, length);
                break;
            case AbortFW.TYPE_ID:
                connection.doConnectionSignalNow(initialId, 0, SIGNAL_STREAM_ABORT, buffer, index, length);
                break;
            case WindowFW.TYPE_ID:
                connection.doConnectionSignalNow(initialId, 0, SIGNAL_STREAM_WINDOW, buffer, index, length);
                break;
            case ResetFW.TYPE_ID:
                connection.doConnectionSignalNow(initialId, 0, SIGNAL_STREAM_RESET, buffer, index, length);
                break;
            default:
                break;
            }
        }

        private void onStreamBeginInit(
            BeginFW begin)
        {

            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final ProxyBeginExFW proxyBeginEx = extension.get(proxyBeginExRO::tryWrap);

            String host = null;
            int port = 0;

            if (proxyBeginEx != null)
            {
                final ProxyAddressInetFW inet = proxyBeginEx.address().inet();
                host = inet.destination().asString();
                port = inet.destinationPort();
            }

            connection.doConnectionBegin(traceId, host, port);
        }


        private void onStreamBegin(
            BeginFW begin)
        {
            final long initialId = begin.streamId();
            final long traceId = begin.traceId();

            assert (initialId & 0x0000_0000_0000_0001L) != 0L;

            state = KafkaState.openingInitial(state);

            doStreamBegin(authorization, traceId);
        }

        private void onStreamData(
            DataFW data)
        {
            final long initialId = data.streamId();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            if (requestBytes == 0)
            {
                initialSeqOffset.add(connection.initialSeq);
                nextRequestId++;

                final DirectBuffer buffer = payload.buffer();
                final int offset = payload.offset();
                final int limit = payload.limit();

                RequestHeaderFW requestHeader = requestHeaderRO.wrap(buffer, offset, limit);
                requestBytes = requestHeader.length() + KAFKA_FRAME_LENGTH_FIELD_OFFSET;
            }

            requestBytes -= payload.sizeof();
            connection.doConnectionData(initialId, traceId, authorization, budgetId,
                flags, reserved, payload, extension);
            assert requestBytes >= 0;

            initialSeq += reserved;

            connection.doConnectionWindow(traceId, authorization, 0);
        }

        private void onStreamEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedInitial(state);

            if (KafkaState.closed(state))
            {
                connection.onStreamClosed(initialId);
            }
            else
            {
                doStreamEnd(traceId);
            }
        }

        private void onStreamAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedInitial(state);

            if (KafkaState.closed(state))
            {
                connection.onStreamClosed(initialId);
            }
            else
            {
                doStreamAbort(traceId);
            }
        }

        private void onStreamReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedReply(state);

            replyAck = replySeq;

            // TODO: responseAckBytes == 0, remove if
            if (responseBytes == 0)
            {
                flushStreamWindow(traceId);
            }

            if (KafkaState.closed(state))
            {
                connection.onStreamClosed(initialId);
            }
            else
            {
                doStreamReset(traceId);
            }
        }

        private void onStreamWindow(
            WindowFW window)
        {
            final long acknowledge = window.acknowledge();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int maximum = window.maximum();

            assert replyAck <= replySeq;

            this.replyAck = acknowledge;
            this.replyMax = maximum;
            this.replyPad = padding;
            this.replyBud = budgetId;

            state = KafkaState.openedReply(state);

            flushStreamWindow(traceId);
        }

        private void doStreamWindow(
            long authorization,
            long traceId)
        {
            if (connection.initialBudId != NO_BUDGET_ID)
            {
                final long initialSeqOffsetPeek = initialSeqOffset.peekLong();

                if (initialSeqOffsetPeek != NO_OFFSET)
                {
                    assert initialAck <= connection.initialAck - initialSeqOffsetPeek + initialAckSnapshot;

                    initialAck = connection.initialAck - initialSeqOffsetPeek + initialAckSnapshot;

                    if (initialAck == initialSeq)
                    {
                        initialSeqOffset.removeLong();
                        initialAckSnapshot = initialAck;
                    }
                }

                doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, connection.initialMax,
                    traceId, authorization, connection.initialBudId, connection.initialPad);
            }
        }

        private void doStreamBegin(
            long authorization,
            long traceId)
        {
            state = KafkaState.openingReply(state);

            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, connection.initialBudId, EMPTY_EXTENSION);

            doStreamWindow(authorization, traceId);
        }

        private void doStreamData(
            long traceId,
            int flags,
            int reserved,
            DirectBuffer payload,
            int offset,
            int length,
            Flyweight extension)
        {
            if (responseBytes == 0)
            {
                replySeqOffset.add(connection.replySeq - reserved);
                nexResponseId++;
                final ResponseHeaderFW responseHeader = responseHeaderRO.wrap(payload, offset, offset + length);
                responseBytes = responseHeader.length() + KAFKA_FRAME_LENGTH_FIELD_OFFSET;
            }

            responseBytes -= length;
            assert responseBytes >= 0;

            if (!KafkaState.replyClosed(state))
            {
                doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, flags, replyBud, reserved, payload, offset, length, extension);

                replySeq += reserved;

                if (responseBytes == 0 && KafkaState.replyClosing(state))
                {
                    doStreamEnd(traceId);
                }
            }
            else
            {
                replySeq += reserved;
                replyAck = replySeq;

                // TODO: responseAckBytes == 0, remove if
                if (responseBytes == 0)
                {
                    flushStreamWindow(traceId);
                }
            }
        }

        private void flushStreamWindow(
            long traceId)
        {
            final long replySeqOffsetPeek = replySeqOffset.peekLong();

            if (replySeqOffsetPeek != NO_OFFSET)
            {
                assert replyAck >= connection.replyAck - replySeqOffsetPeek + replyAckSnapshot;

                // TODO: && responseAckBytes == 0
                if (replyAck == replySeq)
                {
                    replyAckOffset.add(replySeqOffsetPeek + replyAck - replyAckSnapshot);

                    replySeqOffset.removeLong();
                    replyAckSnapshot = replyAck;
                }
            }

            connection.doConnectionWindow(traceId, authorization, replyBud);
        }

        private void doStreamEnd(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                state = KafkaState.closingReply(state);
                if (nextRequestId == nexResponseId)
                {
                    state = KafkaState.closedReply(state);

                    doEnd(sender, originId, routedId, replyId, 0, 0, 0,
                        traceId, authorization, EMPTY_EXTENSION);

                    if (KafkaState.closed(state))
                    {
                        connection.onStreamClosed(initialId);
                    }
                }
            }
        }

        private void doStreamAbort(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                state = KafkaState.closingReply(state);

                if (nextRequestId == nexResponseId)
                {
                    state = KafkaState.closedReply(state);

                    doAbort(sender, originId, routedId, replyId, 0, 0, 0,
                        traceId, authorization, EMPTY_EXTENSION);

                    if (KafkaState.closed(state))
                    {
                        connection.onStreamClosed(initialId);
                    }
                }
            }
        }

        private void doStreamReset(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                state = KafkaState.closedInitial(state);

                doReset(sender, originId, routedId, initialId, 0, 0, 0,
                    traceId, authorization);

                if (KafkaState.closed(state))
                {
                    connection.onStreamClosed(initialId);
                }
            }
        }

        private void doStreamSignalNow(
            long traceId,
            int signalId)
        {
            connection.doConnectionSignalNow(initialId, traceId, signalId);
        }

        private void doStreamSignalNow(
            long traceId,
            int signalId,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            connection.doConnectionSignalNow(initialId, traceId, signalId, buffer, offset, length);
        }


        private long doStreamSignalAt(
            long timeMillis,
            long traceId,
            int signalId)
        {
            return connection.doConnectionSignalAt(initialId, traceId, timeMillis, signalId);
        }

        private void cleanup(
            long traceId)
        {
            doStreamReset(traceId);
            doStreamAbort(traceId);
        }

        private void onStreamSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final int signalId = signal.signalId();
            final OctetsFW payload = signal.payload();

            switch (signalId)
            {
            case SIGNAL_STREAM_BEGIN:
                final BeginFW begin = beginRO.wrap(payload.value(), 0, payload.sizeof());
                onStreamBegin(begin);
                break;
            case SIGNAL_STREAM_DATA:
                final DataFW data = dataRO.wrap(payload.value(), 0, payload.sizeof());
                onStreamData(data);
                break;
            case SIGNAL_STREAM_END:
                final EndFW end = endRO.wrap(payload.value(), 0, payload.sizeof());
                onStreamEnd(end);
                break;
            case SIGNAL_STREAM_ABORT:
                final AbortFW abort = abortRO.wrap(payload.value(), 0, payload.sizeof());
                onStreamAbort(abort);
                break;
            case SIGNAL_STREAM_WINDOW:
                final WindowFW window = windowRO.wrap(payload.value(), 0, payload.sizeof());
                onStreamWindow(window);
                break;
            case SIGNAL_STREAM_RESET:
                final ResetFW reset = resetRO.wrap(payload.value(), 0, payload.sizeof());
                onStreamReset(reset);
                break;
            default:
                doSignal(sender, originId, routedId, initialId, initialSeq,
                    initialAck, connection.initialMax, traceId, signalId, payload);
                break;
            }
        }
    }

    final class KafkaClientConnection implements BindingHandler
    {
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final LongHashSet streams;
        private final LongArrayQueue requests;
        private final LongArrayQueue responses;
        private final LongArrayQueue responseAcks;
        private final Long2LongHashMap signalerCorrelations;

        private long initialId;
        private long replyId;
        private MessageConsumer receiver;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialMin;
        private long initialBudId = NO_BUDGET_ID;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private int nextRequestId;
        private int nextContextId;
        private long reconnectAt = NO_CANCEL_ID;
        private int requestBytes;
        private int responseBytes;

        private KafkaClientConnection(
            long originId,
            long routedId,
            long authorization)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.authorization = authorization;
            this.streams = new LongHashSet();
            this.requests = new LongArrayQueue();
            this.responses = new LongArrayQueue();
            this.responseAcks = new LongArrayQueue();
            this.signalerCorrelations = new Long2LongHashMap(-1L);
        }

        private void doConnectionBegin(
            long traceId,
            String host,
            int port)
        {
            if (KafkaState.closed(state))
            {
                state = 0;
            }

            if (!KafkaState.initialOpening(state))
            {
                assert state == 0;

                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);

                Consumer<OctetsFW.Builder> extension = EMPTY_EXTENSION;

                state = KafkaState.openingInitial(state);

                if (host != null)
                {
                    extension =  e -> e.set((b, o, l) -> proxyBeginExRW.wrap(b, o, l)
                        .typeId(proxyTypeId)
                        .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                            .source("0.0.0.0")
                            .destination(host)
                            .sourcePort(0)
                            .destinationPort(port)))
                        .build()
                        .sizeof());
                }

                this.receiver = newNetworkStream(this::onConnectionMessage,
                    originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, 0L, extension);
            }
        }

        private void doConnectionData(
            long streamId,
            long traceId,
            long authorization,
            long budgetId,
            int flags,
            int reserved,
            OctetsFW payload,
            Flyweight extension)
        {
            if (requestBytes == 0)
            {
                final int requestId = nextRequestId++;

                requests.add(streamId);
                responses.add(streamId);
                responseAcks.add(streamId);

                final DirectBuffer buffer = payload.buffer();
                final int offset = payload.offset();
                final int limit = payload.limit();

                RequestHeaderFW requestHeader = requestHeaderRO.wrap(buffer, offset, limit);
                requestBytes = requestHeader.length() + KAFKA_FRAME_LENGTH_FIELD_OFFSET;

                int progress = 0;
                RequestHeaderFW newRequestHeader = requestHeaderRW.wrap(encodeBuffer, 0, encodeBuffer.capacity())
                    .length(requestHeader.length())
                    .apiKey(requestHeader.apiKey())
                    .apiVersion(requestHeader.apiVersion())
                    .correlationId(requestId)
                    .clientId(requestHeader.clientId())
                    .build();
                progress = newRequestHeader.limit();

                final int remaining = payload.sizeof() - progress;
                encodeBuffer.putBytes(progress, buffer, requestHeader.limit(), remaining);

                final int length = progress + remaining;
                doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, flags, budgetId, reserved, encodeBuffer, 0, length, extension);

                requestBytes -= length;
                assert requestBytes >= 0;
            }
            else
            {
                doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, flags, budgetId, reserved, payload, extension);
                requestBytes -= payload.sizeof();
            }

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doConnectionEnd(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

                state = KafkaState.closedInitial(state);

                cleanupBudgetCreditorIfNecessary();
            }
        }

        private void doConnectionAbort(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

                state = KafkaState.closedInitial(state);

                cleanupBudgetCreditorIfNecessary();
            }
        }

        private void doConnectionSignalNow(
            long streamId,
            long traceId,
            int signalId)
        {
            nextContextId++;
            signalerCorrelations.put(nextContextId, streamId);
            signaler.delegate.signalNow(originId, routedId, this.initialId, traceId, signalId, nextContextId);
        }

        private void doConnectionSignalNow(
            long streamId,
            long traceId,
            int signalId,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            nextContextId++;
            signalerCorrelations.put(nextContextId, streamId);
            signaler.delegate.signalNow(originId, routedId, this.initialId, traceId, signalId, nextContextId,
                buffer, offset, length);
        }

        private long doConnectionSignalAt(
            long streamId,
            long traceId,
            long timeMillis,
            int signalId)
        {
            nextContextId++;
            signalerCorrelations.put(nextContextId, streamId);
            return signaler.delegate.signalAt(
                timeMillis, originId, routedId, this.initialId, traceId, signalId, nextContextId);
        }

        private void doConnectionReset(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doReset(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);

                state = KafkaState.closedReply(state);
            }
        }

        private void doConnectionWindow(
            long traceId,
            long authorization,
            long budgetId)
        {
            long maxReplyAck = replyAck;
            int maxReplyPad = replyPad;
            int minReplyMax = replyMax;

            if (!responseAcks.isEmpty())
            {
                ack:
                for (LongArrayQueue.LongIterator i = responseAcks.iterator(); i.hasNext();)
                {
                    long responseAck = i.nextValue();
                    KafkaClientStream stream = streamsByInitialId.get(responseAck);

                    maxReplyPad = stream.replyPad;
                    minReplyMax = stream.replyMax;

                    if (!KafkaState.replyClosed(stream.state) &&
                        (stream.replyAck < stream.replySeq || stream.replyAckOffset.isEmpty()))
                    {
                        if (!stream.replySeqOffset.isEmpty())
                        {
                            maxReplyAck = stream.replySeqOffset.peekLong() + stream.replyAck - stream.replyAckSnapshot;
                        }
                        break ack;
                    }
                    maxReplyAck = stream.replyAckOffset.removeLong();

                    if (KafkaState.closed(stream.state) && stream.replyAckOffset.isEmpty())
                    {
                        streamsByInitialId.remove(responseAck);
                    }

                    responseAcks.removeLong();
                }
            }

            final long newReplyAck = Math.max(maxReplyAck, replyAck);

            if (newReplyAck > replyAck || minReplyMax > replyMax || !KafkaState.replyOpened(state))
            {
                replyAck = newReplyAck;
                assert replyAck <= replySeq;

                replyMax = minReplyMax;

                state = KafkaState.openedReply(state);

                doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, maxReplyPad);
            }
        }

        private void onConnectionMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onConnectionBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onConnectionData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onConnectionEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onConnectionAbort(abort);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onConnectionSignal(signal);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onConnectionReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onConnectionWindow(window);
                break;
            default:
                break;
            }
        }

        private void onConnectionBegin(
            BeginFW begin)
        {
            final long authorization = begin.authorization();
            final long traceId = begin.traceId();

            state = KafkaState.openingReply(state);

            doConnectionWindow(traceId, authorization, 0);
        }

        private void onConnectionData(
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

            final DirectBuffer buffer = payload.buffer();
            final int limit = payload.limit();
            int progress = payload.offset();

            while (progress < limit)
            {
                if (responseBytes == 0)
                {
                    final ResponseHeaderFW responseHeader = responseHeaderRO.wrap(buffer, progress, limit);
                    responseBytes = responseHeader.length() + KAFKA_FRAME_LENGTH_FIELD_OFFSET;
                }

                final int responseBytesMin = Math.min(responseBytes, payload.sizeof());
                responseBytes -= responseBytesMin;
                assert responseBytes >= 0;

                long initialId = responses.peekLong();

                KafkaClientStream stream = streamsByInitialId.get(initialId);

                stream.doStreamData(traceId, flags | FLAG_INIT | FLAG_FIN,
                    reserved, buffer, progress, responseBytesMin, extension);

                progress += responseBytesMin;

                if (responseBytes == 0)
                {
                    responses.removeLong();
                }
            }
        }

        private void onConnectionEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = KafkaState.closedReply(state);

            assert replyAck <= replySeq;

            doConnectionEnd(traceId);

            cleanupStreams(traceId);
        }

        private void onConnectionAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            doConnectionAbort(traceId);

            cleanupStreams(traceId);
        }

        private void cleanupStreams(
            long traceId)
        {
            requests.clear();
            responses.clear();
            responseAcks.clear();
            streams.forEach(s ->
            {
                KafkaClientStream stream = streamsByInitialId.get(s);
                stream.cleanup(traceId);
                streamsByInitialId.remove(s);
            });
            streams.clear();
        }

        private void onConnectionSignal(
            SignalFW signal)
        {
            final int signalId = signal.signalId();
            final int contextId = signal.contextId();

            if (signalId == SIGNAL_CONNECTION_CLEANUP)
            {
                doSignalStreamCleanup();
            }
            else
            {
                long initialId = signalerCorrelations.remove(contextId);
                KafkaClientStream stream = streamsByInitialId.get(initialId);

                if (stream != null)
                {
                    stream.onStreamSignal(signal);
                }
            }
        }

        private void onConnectionReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            doConnectionReset(traceId);

            cleanupStreams(traceId);
        }

        private void onConnectionWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int minimum = window.minimum();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum >= initialMax;

            int credit = (int)(acknowledge - initialAck) + (maximum - initialMax);
            assert credit >= 0;

            this.initialAck = acknowledge;
            this.initialMax = maximum;
            this.initialMin = minimum;
            this.initialPad = padding;

            assert replyAck <= replySeq;

            if (KafkaState.initialOpening(state))
            {
                state = KafkaState.openedInitial(state);
                if (initialBudId == NO_BUDGET_ID)
                {
                    initialBudId = creditor.acquire(initialId, budgetId);
                }
            }

            if (initialBudId != NO_BUDGET_ID)
            {
                creditor.credit(traceId, initialBudId, credit);
            }

            if (requests.isEmpty())
            {
                streams.forEach(s -> streamsByInitialId.get(s).doStreamWindow(authorization, traceId));
            }

            while (credit > 0 && !requests.isEmpty())
            {
                final long streamId = requests.peekLong();
                KafkaClientStream stream = streamsByInitialId.get(streamId);

                long streamAck = stream.initialAck;

                stream.doStreamWindow(authorization, traceId);

                credit = Math.max(credit - (int)(streamAck - stream.initialAck), 0);

                if (stream.initialAck != stream.initialSeq)
                {
                    break;
                }

                requests.removeLong();
            }
        }

        private void doSignalStreamCleanup()
        {
            this.reconnectAt = signaler.delegate.signalAt(
                currentTimeMillis() + 4000,
                SIGNAL_CONNECTION_CLEANUP,
                this::onStreamCleanupSignal);
        }


        private void onStreamCleanupSignal(
            int signalId)
        {
            assert signalId == SIGNAL_CONNECTION_CLEANUP;

            if (streamsByInitialId.isEmpty())
            {
                final long traceId = supplyTraceId.getAsLong();
                cleanupConnection(traceId);
                responses.clear();
            }
        }

        private void cleanupConnection(
            long traceId)
        {
            doConnectionAbort(traceId);
            doConnectionReset(traceId);
        }

        private void cleanupBudgetCreditorIfNecessary()
        {
            if (initialBudId != NO_CREDITOR_INDEX)
            {
                creditor.release(initialBudId);
                initialBudId = NO_BUDGET_ID;
            }
        }

        private void onStreamClosed(
            long streamId)
        {
            streams.remove(streamId);
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
            final long authorization = begin.authorization();

            KafkaClientStream stream = new KafkaClientStream(this, sender, originId, routedId, initialId, authorization);
            streamsByInitialId.put(initialId, stream);
            streams.add(initialId);

            return stream::onStreamMessage;
        }
    }
}
