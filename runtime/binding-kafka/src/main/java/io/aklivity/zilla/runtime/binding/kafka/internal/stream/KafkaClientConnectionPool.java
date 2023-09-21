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
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.SignalFW;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.budget.MergedBudgetCreditor;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.ProxyAddressInetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.RequestHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.ResponseHeaderV0FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class KafkaClientConnectionPool
{
    private static final long NO_DELTA = -1L;
    private static final int FLAG_FIN = 0x01;
    private static final int FLAG_INIT = 0x02;
    private static final int FLAG_SKIP = 0x08;
    private static final int FLAG_NONE = 0x00;
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private static final int SIGNAL_CONNECTION_CLEANUP = 10001;
    private static final String CLUSTER = "";

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final SignalFW signalRO = new SignalFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final ProxyBeginExFW proxyBeginExRO = new ProxyBeginExFW();

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
    private final ResponseHeaderV0FW responseHeaderRO = new ResponseHeaderV0FW();

    private final MergedBudgetCreditor creditor;
    private final int proxyTypeId;
    private final BufferPool bufferPool;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final Signaler signaler;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final Object2ObjectHashMap<String, KafkaClientConnection> connectionPool;
    private final Long2ObjectHashMap<KafkaClientStream> streamsByInitialIds;


    public KafkaClientConnectionPool(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding,
        MergedBudgetCreditor creditor)
    {
        this.proxyTypeId = context.supplyTypeId("proxy");
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.bufferPool = context.bufferPool();
        this.signaler = context.signaler();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.creditor = creditor;
        this.connectionPool = new Object2ObjectHashMap();
        this.streamsByInitialIds = new Long2ObjectHashMap<>();
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
        int signalId)
    {
        final SignalFW signal = signalRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(0)
            .traceId(traceId)
            .signalId(signalId)
            .contextId(0)
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

    public class ConnectionSignaler implements Signaler
    {
        @Override
        public long signalAt(
            long timeMillis,
            int signalId,
            IntConsumer handler)
        {
           return signaler.signalAt(timeMillis, signalId, handler);
        }

        @Override
        public void signalNow(
            long originId,
            long routedId,
            long streamId,
            int signalId,
            int contextId)
        {
            KafkaClientStream stream = streamsByInitialIds.get(streamId);
            stream.signalNow(signalId);
        }

        @Override
        public long signalAt(
            long timeMillis,
            long originId,
            long routedId,
            long streamId,
            int signalId,
            int contextId)
        {
            KafkaClientStream stream = streamsByInitialIds.get(streamId);
            return stream.signalAt(timeMillis, signalId);
        }

        @Override
        public long signalTask(
            Runnable task,
            long originId,
            long routedId,
            long streamId,
            int signalId,
            int contextId)
        {
            return 0;
        }

        @Override
        public boolean cancel(
            long cancelId)
        {
            return signaler.cancel(cancelId);
        }
    }

    public Signaler signaler()
    {
        return new ConnectionSignaler();
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
        private int initialMax;
        private int initialPad;
        private long initialBud;
        private long initialSeqDelta = NO_DELTA;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBud;

        private long reconnectAt = NO_CANCEL_ID;

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
                onStreamBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onStreamData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onStreamEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onStreamAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onStreamWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onStreamReset(reset);
                break;
            default:
                break;
            }
        }

        private void onStreamBegin(
            BeginFW begin)
        {
            final long initialId = begin.streamId();
            final long traceId = begin.traceId();

            assert (initialId & 0x0000_0000_0000_0001L) != 0L;

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

            doStreamBegin(traceId, begin.extension());

            doStreamWindow(connection.authorization, traceId,
                connection.connectionInitialBudgetId, connection.initialPad);

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

            initialSeqDelta = connection.initialSeq;

            connection.doConnectionData(initialId, traceId, authorization, budgetId,
                flags, reserved, payload, extension);
        }

        private void onStreamEnd(
            EndFW end)
        {
            long traceId = end.traceId();

            doStreamEnd(traceId);
        }

        private void onStreamAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            doStreamAbort(traceId);
        }

        private void doStreamReset(
            long traceId)
        {
            doReset(sender, originId, routedId, initialId, 0, 0, 0,
                traceId, authorization);
        }

        private void doStreamWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding)
        {
            assert initialSeqDelta != NO_DELTA;

            initialSeq = connection.initialSeq - initialSeqDelta;
            initialAck = connection.initialAck - initialSeqDelta;
            initialMax = connection.initialMax;

            doWindow(sender, originId, routedId, initialId, 0, 0, initialMax,
                traceId, authorization, budgetId, padding);
        }

        private void doStreamBegin(
            long traceId,
            Flyweight extension)
        {
            final long replyId = supplyReplyId.applyAsLong(initialId);

            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, initialBud, extension);
        }

        private void doStreamData(
            long initialId,
            long traceId,
            int flags,
            long sequence,
            long acknowledge,
            int reserved,
            OctetsFW payload,
            Flyweight extension)
        {
            replySeq = sequence;
            replyAck = acknowledge;

            final long replyId = supplyReplyId.applyAsLong(initialId);

            doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, flags, replyBud, reserved, payload, extension);
        }

        private void doStreamEnd(
            long traceId)
        {
            final long replyId = supplyReplyId.applyAsLong(initialId);

            doEnd(sender, originId, routedId, replyId, 0, 0, 0,
                traceId, authorization, EMPTY_EXTENSION);
        }

        private void doStreamAbort(
            long traceId)
        {
            doAbort(sender, originId, routedId, replyId, 0, 0, 0,
                traceId, authorization, EMPTY_EXTENSION);
        }

        private void onStreamReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            doStreamReset(traceId);
        }

        private void onStreamWindow(
            WindowFW window)
        {
            final long acknowledge = window.acknowledge();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int replyMax = window.maximum();

            assert replyAck <= replySeq;

            connection.doConnectionWindow(traceId, acknowledge, budgetId, padding, replyMax);
        }

        private void cleanupStream(
            long traceId)
        {
            doStreamReset(traceId);
            doStreamAbort(traceId);
        }

        private void signalNow(
            int signalId)
        {
            connection.doConnectionSignalNow(initialId, signalId);
        }

        private long signalAt(
            long timeMillis,
            int signalId)
        {
            return connection.doConnectionSignalAt(initialId, timeMillis, signalId);
        }

        private void onSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final int signalId = signal.signalId();
            doSignal(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, signalId);
        }
    }

    final class KafkaClientConnection implements BindingHandler
    {
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final Int2ObjectHashMap<Long> correlations;

        private long initialId;
        private long replyId;
        private MessageConsumer receiver;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialMin;
        private int initialPad;
        private long initialBud;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private int nextRequestId;
        private long connectionInitialBudgetId = NO_BUDGET_ID;
        private long reconnectAt = NO_CANCEL_ID;

        private KafkaClientConnection(
            long originId,
            long routedId,
            long authorization)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.authorization = authorization;
            this.correlations = new Int2ObjectHashMap<>();
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
                state = KafkaState.openingInitial(state);
            }
        }

        private void doConnectionData(
            long connectionInitialId,
            long traceId,
            long authorization,
            long budgetId,
            int flags,
            int reserved,
            OctetsFW payload,
            Flyweight extension)
        {
            if ((flags & FLAG_INIT) != 0x00)
            {
                int requestId = nextRequestId++;
                correlations.put(requestId, (Long) connectionInitialId);

                final DirectBuffer buffer = payload.buffer();
                final int offset = payload.offset();
                final int limit = payload.limit();

                RequestHeaderFW requestHeader = requestHeaderRO.wrap(buffer, offset, limit);

                int progress = 0;
                RequestHeaderFW newRequestHeader = requestHeaderRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .length(requestHeader.length())
                    .apiKey(requestHeader.apiKey())
                    .apiVersion(requestHeader.apiVersion())
                    .correlationId(requestId)
                    .clientId(requestHeader.clientId())
                    .build();
                progress = newRequestHeader.limit();

                final int remaining = payload.sizeof() - progress;
                extBuffer.putBytes(progress, buffer, requestHeader.limit(), remaining);

                final int length = progress + remaining;
                doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, flags, budgetId, reserved, extBuffer, 0, length, extension);
            }
            else
            {
                doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, flags, budgetId, reserved, payload, extension);
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
            }
        }

        private void doConnectionSignalNow(
            long initialId,
            int signalId)
        {
            signaler.signalNow(originId, routedId, this.initialId, signalId, (int) initialId);
        }

        private long doConnectionSignalAt(
            long initialId,
            long timeMillis,
            int signalId)
        {
            return signaler.signalAt(timeMillis, originId, routedId, this.initialId, signalId, (int) initialId);
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
            long budgetId,
            int padding,
            int replyMax)
        {
            replyAck = Math.max(replyAck - replyPad, 0);
            this.replyMax = replyMax;

            doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, this.replyMax,
                traceId, authorization, budgetId, padding + replyPad);
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

            doConnectionWindow(traceId, authorization, 0, replyPad, replyMax);
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

            if ((flags & FLAG_INIT) != 0x00)
            {
                final DirectBuffer buffer = payload.buffer();
                final int limit = payload.limit();
                int progress = payload.offset();

                ResponseHeaderV0FW responseHeader = responseHeaderRO.wrap(buffer, progress, limit);

                int correlationId = responseHeader.correlationId();
                Long initialId = correlations.remove(correlationId);

                KafkaClientStream stream = streamsByInitialIds.get(initialId);

                stream.doStreamData(initialId, traceId, flags, sequence, acknowledge,
                    reserved, payload, extension);
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

            streamsByInitialIds.forEach((k, v) -> v.cleanupStream(traceId));
        }

        private void onConnectionAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            doConnectionAbort(traceId);

            streamsByInitialIds.forEach((k, v) -> v.cleanupStream(traceId));
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
                KafkaClientStream stream = streamsByInitialIds.get(contextId);
                stream.onSignal(signal);
            }

        }

        private void onConnectionReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            doConnectionReset(traceId);

            streamsByInitialIds.forEach((k, v) -> v.cleanupStream(traceId));
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

            final int credit = (int)(acknowledge - initialAck) + (maximum - initialMax);
            assert credit >= 0;

            this.initialAck = acknowledge;
            this.initialMax = maximum;
            this.initialMin = minimum;
            this.initialPad = padding;
            this.initialBud = budgetId;

            assert replyAck <= replySeq;

            if (KafkaState.replyOpening(state))
            {
                state = KafkaState.openedReply(state);
                if (connectionInitialBudgetId == NO_BUDGET_ID)
                {
                    connectionInitialBudgetId = creditor.acquire(initialId, budgetId);
                }
            }

            if (connectionInitialBudgetId != NO_BUDGET_ID)
            {
                creditor.credit(traceId, connectionInitialBudgetId, credit);
            }

            streamsByInitialIds.forEach((k, v) ->
                v.doStreamWindow(authorization, traceId, connectionInitialBudgetId, initialPad));
        }

        private void doSignalStreamCleanup()
        {
            this.reconnectAt = signaler.signalAt(
                currentTimeMillis() + 4000,
                SIGNAL_CONNECTION_CLEANUP,
                this::onStreamCleanupSignal);
        }


        private void onStreamCleanupSignal(
            int signalId)
        {
            assert signalId == SIGNAL_CONNECTION_CLEANUP;

            if (streamsByInitialIds.isEmpty())
            {
                final long traceId = supplyTraceId.getAsLong();
                cleanupConnection(traceId);
                correlations.clear();
            }
        }

        private void cleanupConnection(
            long traceId)
        {
            doConnectionAbort(traceId);
            doConnectionReset(traceId);

            cleanupBudgetCreditorIfNecessary();
        }

        private void cleanupBudgetCreditorIfNecessary()
        {
            if (connectionInitialBudgetId != NO_CREDITOR_INDEX)
            {
                creditor.release(connectionInitialBudgetId);
                connectionInitialBudgetId = NO_CREDITOR_INDEX;
            }
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
            streamsByInitialIds.put(initialId, stream);

            return stream::onStreamMessage;
        }
    }
}
