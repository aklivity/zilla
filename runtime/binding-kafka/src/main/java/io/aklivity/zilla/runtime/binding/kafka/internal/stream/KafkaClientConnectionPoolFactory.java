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
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;

import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.ProxyAddressFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.ProxyAddressInetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.ProxyAddressProtocolFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ProxyBeginExFW;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class KafkaClientConnectionPoolFactory implements BindingHandler
{
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private static final int SIGNAL_RECONNECT = 1;
    private static final String CLUSTER = "";

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final ProxyBeginExFW proxyBeginExRO = new ProxyBeginExFW();

    private final ProxyBeginExFW.Builder proxyBeginExRW = new ProxyBeginExFW.Builder();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();

    private final int kafkaTypeId;
    private final int proxyTypeId;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BufferPool bufferPool;
    private final Signaler signaler;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private Object2ObjectHashMap<String, KafkaClientConnection> connectionPool;

    public KafkaClientConnectionPoolFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding)
    {
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.proxyTypeId = context.supplyTypeId("proxy");
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.bufferPool = context.bufferPool();
        this.signaler = context.signaler();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.connectionPool = new Object2ObjectHashMap();
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
        final long affinity = begin.affinity();

        assert (initialId & 0x0000_0000_0000_0001L) != 0L;

        final OctetsFW extension = begin.extension();
        final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
        assert beginEx != null && beginEx.typeId() == proxyTypeId;
        final ProxyBeginExFW proxyBeginEx = extension.get(proxyBeginExRO::tryWrap);

        MessageConsumer newStream = null;
        String address = CLUSTER;

        if (proxyBeginEx != null)
        {
            final ProxyAddressInetFW inet = proxyBeginEx.address().inet();
            address = String.format("%s:%d", inet.destination().asString(), inet.destinationPort());
        }

        final KafkaClientConnection kafkaClientConnection = connectionPool.computeIfAbsent(address, s -> new KafkaClientConnection(
            originId, routedId, initialId, affinity));
        kafkaClientConnection.addReceiver(initialId, sender);
        newStream = kafkaClientConnection::onConnectionMessage;

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

    private void doDataNull(
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
                .budgetId(budgetId)
                .reserved(reserved)
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

    final class KafkaClientConnection
    {
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final long affinity;

        private long initialId;
        private long replyId;
        private MessageConsumer receiver;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private long reconnectAt = NO_CANCEL_ID;
        private int reconnectAttempt;

        private KafkaClientConnection(
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.authorization = authorization;
            this.affinity = affinity;
        }

        private void doConnectionInitialBeginIfNecessary(
            long traceId)
        {
            if (KafkaState.closed(state))
            {
                state = 0;
            }

            if (!KafkaState.initialOpening(state))
            {

                doConnectionInitialBegin(traceId);
            }
        }

        private void doConnectionInitialBegin(
            long traceId)
        {
            assert state == 0;

            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.receiver = newStream(this::onConnectionMessage,
                originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L,
                ex -> ex.set((b, o, l) -> proxyBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .meta(m -> m.topic(topic.name()))
                        .build()
                        .sizeof()));
            state = KafkaState.openingInitial(state);
        }

        private void doConnectionInitialEndIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doConnectionInitialEnd(traceId);
            }
        }

        private void doConnectionInitialEnd(
            long traceId)
        {
            doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

            state = KafkaState.closedInitial(state);
        }

        private void doConnectionInitialAbortIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doConnectionInitialAbort(traceId);
            }
        }

        private void doConnectionInitialAbort(
            long traceId)
        {
            doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

            state = KafkaState.closedInitial(state);
        }

        private void onConnectionInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final OctetsFW extension = reset.extension();

            final KafkaResetExFW kafkaResetEx = extension.get(kafkaResetExRO::tryWrap);
            final int error = kafkaResetEx != null ? kafkaResetEx.error() : -1;

            state = KafkaState.closedInitial(state);

            doConnectionReplyResetIfNecessary(traceId);


        }

        private void onConnectionInitialWindow(
            WindowFW window)
        {
            if (!KafkaState.initialOpened(state))
            {
                this.reconnectAttempt = 0;

                final long traceId = window.traceId();

                state = KafkaState.openedInitial(state);
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
                onConnectionReplyBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onConnectionReplyData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onConnectionReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onConnectionReplyAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onConnectionInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onConnectionInitialWindow(window);
                break;
            default:
                break;
            }
        }

        private void onConnectionReplyBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = KafkaState.openingReply(state);

            doConnectionReplyWindow(traceId, 0, bufferPool.slotCapacity());
        }

        private void onConnectionReplyData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int reserved = data.reserved();
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;
            assert replySeq <= replyAck + replyMax;

            final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
            final KafkaDataExFW kafkaDataEx = dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;
            assert kafkaDataEx == null || kafkaDataEx.kind() == KafkaBeginExFW.KIND_META;

            doConnectionReplyWindow(traceId, 0, replyMax);
        }

        private void onConnectionReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedReply(state);

            doConnectionInitialEndIfNecessary(traceId);
        }

        private void onConnectionReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedReply(state);

            doConnectionInitialAbortIfNecessary(traceId);
        }

        private void onConnectionSignal(
            int signalId)
        {
            assert signalId == SIGNAL_RECONNECT;

            this.reconnectAt = NO_CANCEL_ID;

            final long traceId = supplyTraceId.getAsLong();

            doConnectionInitialBeginIfNecessary(traceId);
        }

        private void doConnectionBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            this.replyId = supplyReplyId.applyAsLong(initialId);

            state = KafkaState.openingInitial(state);

            Consumer<OctetsFW.Builder> extension =  e -> e.set((b, o, l) -> proxyBeginExRW.wrap(b, o, l)
                .typeId(proxyTypeId)
                .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                    .source("0.0.0.0")
                    .destination(delegate.host)
                    .sourcePort(0)
                    .destinationPort(delegate.port)))
                .build()
                .sizeof());

            network = newStream(this::onConnection, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, extension);
        }

        private void doConnectionReplyResetIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doConnectionReplyReset(traceId);
            }
        }

        private void doConnectionReplyReset(
            long traceId)
        {
            doReset(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);

            state = KafkaState.closedReply(state);
        }

        private void doConnectionReplyWindow(
            long traceId,
            int minReplyNoAck,
            int minReplyMax)
        {
            final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

            if (newReplyAck > replyAck || minReplyMax > replyMax || !KafkaState.replyOpened(state))
            {
                replyAck = newReplyAck;
                assert replyAck <= replySeq;

                replyMax = minReplyMax;

                state = KafkaState.openedReply(state);

                doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, 0L, 0);
            }
        }

        public void addReceiver(
            long initialId,
            MessageConsumer sender)
        {

        }
    }
}
