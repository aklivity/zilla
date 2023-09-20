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
import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_CREDITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.lang.System.currentTimeMillis;

import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import io.aklivity.zilla.runtime.binding.kafka.internal.budget.MergedBudgetCreditor;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.ResponseHeaderV0FW;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.ProxyAddressInetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.RequestHeaderFW;
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

public final class KafkaClientConnectionPoolFactory implements BindingHandler
{
    private static final int FLAG_FIN = 0x01;
    private static final int FLAG_INIT = 0x02;
    private static final int FLAG_SKIP = 0x08;
    private static final int FLAG_NONE = 0x00;
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private static final int SIGNAL_CONNECTION_CLEANUP = 1;
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

    private final RequestHeaderFW.Builder requestHeaderRW = new RequestHeaderFW.Builder();

    private final RequestHeaderFW requestHeaderRO = new RequestHeaderFW();
    private final ResponseHeaderV0FW responseHeaderRO = new ResponseHeaderV0FW();

    private final MergedBudgetCreditor creditor;
    private final int proxyTypeId;
    private final BufferPool bufferPool;
    private final MutableDirectBuffer writeBuffer;
    private final Signaler signaler;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private Object2ObjectHashMap<String, KafkaClientConnectionApp> connectionPool;

    public KafkaClientConnectionPoolFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding,
        MergedBudgetCreditor creditor)
    {
        this.proxyTypeId = context.supplyTypeId("proxy");
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.bufferPool = context.bufferPool();
        this.signaler = context.signaler();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.creditor = creditor;
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
        String host;
        int port;

        if (proxyBeginEx != null)
        {
            final ProxyAddressInetFW inet = proxyBeginEx.address().inet();
            host = inet.destination().asString();
            port = inet.destinationPort();
            address = String.format("%s:%d", host, port);
        }
        else
        {
            host = null;
            port = 0;
        }

        final KafkaClientConnectionApp kafkaClientConnectionApp = connectionPool.computeIfAbsent(address, s ->
            new KafkaClientConnectionApp(originId, routedId, initialId, authorization, affinity, host, port));
        kafkaClientConnectionApp.doAddReceiver(initialId, sender);
        newStream = kafkaClientConnectionApp::onConnectionMessage;

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

    final class KafkaClientConnectionApp
    {
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final String host;
        private final int port;
        private final KafkaClientConnectionNet connection;
        private final Long2ObjectHashMap<MessageConsumer> senders;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private long initialBud;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBud;

        private long reconnectAt = NO_CANCEL_ID;

        private KafkaClientConnectionApp(
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long initialBud,
            String host,
            int port)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.authorization = authorization;
            this.initialBud = initialBud;
            this.host = host;
            this.port = port;
            this.senders = new Long2ObjectHashMap();
            this.connection = new KafkaClientConnectionNet(this, originId, routedId, initialId, authorization);
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
                onConnectionInitialBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onConnectionInitialData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onConnectionInitialEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onConnectionInitialAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onConnectionReplyWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onConnectionReplyReset(reset);
                break;
            default:
                break;
            }
        }

        private void onConnectionInitialBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long initialId = begin.streamId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            connection.doNetworkInitialBegin(traceId);

            final MessageConsumer messageConsumer = senders.get(initialId);

            doConnectionInitialWindow(messageConsumer, initialId, traceId, authorization, initialBud, initialPad);
            doConnectionReplyBegin(messageConsumer, initialId, traceId, begin.extension());
        }

        private void onConnectionInitialData(
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

            connection.doNetworkInitialData(initialId, traceId, authorization, budgetId,
                flags, reserved, payload, extension);
        }

        private void onConnectionInitialEnd(
            EndFW end)
        {
            long initialId = end.traceId();
            long traceId = end.traceId();

            doConnectionReplyEnd(initialId, traceId);
        }

        private void onConnectionInitialAbort(
            AbortFW abort)
        {
            final long initialId = abort.streamId();
            final long traceId = abort.traceId();

            doConnectionReplyAbort(initialId, traceId);
        }

        private void doConnectionInitialReset(
            long initialId,
            long traceId)
        {
            MessageConsumer sender = senders.remove(initialId);

            doReset(sender, originId, routedId, initialId, 0, 0, 0,
                traceId, authorization);
        }

        private void doConnectionInitialWindow(
            MessageConsumer sender,
            long initialId,
            long authorization,
            long traceId,
            long budgetId,
            int padding)
        {
            initialSeq = connection.initialSeq;
            initialAck = connection.initialAck;
            initialMax = connection.initialMax;

            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding);
        }

        private void doConnectionReplyBegin(
            MessageConsumer sender,
            long initialId,
            long traceId,
            Flyweight extension)
        {
            final long replyId = supplyReplyId.applyAsLong(initialId);

            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, initialBud, extension);
        }

        private void doConnectionReplyData(
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

            MessageConsumer sender = senders.get(initialId);

            final long replyId = supplyReplyId.applyAsLong(initialId);

            doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, flags, replyBud, reserved, payload, extension);
        }

        private void doConnectionReplyEnd(
            long initialId,
            long traceId)
        {
            MessageConsumer sender = senders.remove(initialId);

            final long replyId = supplyReplyId.applyAsLong(initialId);

            doEnd(sender, originId, routedId, replyId, 0, 0, 0,
                traceId, authorization, EMPTY_EXTENSION);
        }

        private void doConnectionReplyAbort(
            long initialId,
            long traceId)
        {
            final MessageConsumer sender = senders.remove(initialId);

            final long replyId = supplyReplyId.applyAsLong(initialId);

            doAbort(sender, originId, routedId, replyId, 0, 0, 0,
                traceId, authorization, EMPTY_EXTENSION);
        }

        private void onConnectionReplyReset(
            ResetFW reset)
        {
            final long initialId = reset.streamId();
            final long traceId = reset.traceId();

            doConnectionInitialReset(initialId, traceId);
        }

        private void onConnectionReplyWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = budgetId;
            replyPad = padding;

            assert replyAck <= replySeq;

            connection.doNetworkReplyWindow(traceId, acknowledge, budgetId, padding);
        }

        private void doSignalConnectionCleanup()
        {
            this.reconnectAt = signaler.signalAt(
                currentTimeMillis(),
                SIGNAL_CONNECTION_CLEANUP,
                this::onConnectionCleanupSignal);
        }

        private void doAddReceiver(
            long initialId,
            MessageConsumer sender)
        {
            senders.put(initialId, sender);
        }

        private void onConnectionCleanupSignal(
            int signalId)
        {
            assert signalId == SIGNAL_CONNECTION_CLEANUP;

            if (senders.isEmpty())
            {
                final long traceId = supplyTraceId.getAsLong();
                connection.networkCleanup(traceId);
                connectionCleanup(traceId);
            }
        }

        private void onNetworkWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding)
        {
            senders.forEach((k, v) -> doConnectionInitialWindow(v, k, authorization, traceId, budgetId, padding));
        }

        private void onNetworkEnd(
            long traceId)
        {
            senders.forEach((k, v) ->
            {
                doConnectionInitialReset(k, traceId);
                doConnectionReplyEnd(k, traceId);
            });

            senders.clear();

            connectionPool.remove(this);
        }

        private void connectionCleanup(
            long traceId)
        {
            senders.forEach((k, v) ->
            {
                doConnectionInitialReset(k, traceId);
                doConnectionReplyAbort(k, traceId);
            });

            senders.clear();

            connectionPool.remove(this);
        }
    }

    final class KafkaClientConnectionNet
    {
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final KafkaClientConnectionApp delegate;
        private final Int2ObjectHashMap<Long> correlations;

        private long initialId;
        private long replyId;
        private MessageConsumer receiver;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialMin;
        private long initialBud;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private int nextRequestId;
        private int nextResponseId;
        private long connectionInitialBudgetId;

        private KafkaClientConnectionNet(
            KafkaClientConnectionApp delegate,
            long originId,
            long routedId,
            long initialId,
            long authorization)
        {
            this.delegate = delegate;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.correlations = new Int2ObjectHashMap<>();
        }

        private void doNetworkInitialBegin(
            long traceId)
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

                if (delegate.host != null && !delegate.host.isEmpty())
                {
                    extension =  e -> e.set((b, o, l) -> proxyBeginExRW.wrap(b, o, l)
                        .typeId(proxyTypeId)
                        .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                            .source("0.0.0.0")
                            .destination(delegate.host)
                            .sourcePort(0)
                            .destinationPort(delegate.port)))
                        .build()
                        .sizeof());
                }

                this.receiver = newStream(this::onNetworkMessage,
                    originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, 0L, extension);
                state = KafkaState.openingInitial(state);
            }
        }

        private void doNetworkInitialData(
            long initialId,
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
                correlations.put(requestId, (Long) initialId);

                final DirectBuffer buffer = payload.buffer();
                final int offset = payload.offset();
                final int limit = payload.sizeof();

                RequestHeaderFW requestHeader = requestHeaderRO.wrap(buffer, offset, limit);

                RequestHeaderFW newRequestHeader = requestHeaderRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .length(requestHeader.length())
                    .apiKey(requestHeader.apiKey())
                    .apiVersion(requestHeader.apiVersion())
                    .correlationId(requestId)
                    .clientId(requestHeader.clientId())
                    .build();

                writeBuffer.putBytes(newRequestHeader.limit(), buffer, requestHeader.limit(),
                    limit - requestHeader.limit());

                doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, flags, budgetId, reserved, writeBuffer, 0, limit, extension);
            }
            else
            {
                doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, flags, budgetId, reserved, payload, extension);
            }

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doNetworkInitialEnd(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

                state = KafkaState.closedInitial(state);
            }
        }

        private void doNetworkInitialAbort(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);

                state = KafkaState.closedInitial(state);
            }
        }

        private void onNetworkInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            doNetworkReplyReset(traceId);

            delegate.connectionCleanup(traceId);
        }


        private void onNetworkInitialWindow(
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
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            final int credit = (int)(acknowledge - replyAck) + (maximum - replyMax);
            assert credit >= 0;

            this.initialAck = acknowledge;
            this.initialMax = maximum;
            this.initialMin = minimum;
            this.replyPad = padding;
            this.initialBud = budgetId;

            assert replyAck <= replySeq;

            if (KafkaState.replyOpening(state))
            {
                state = KafkaState.openedReply(state);
                if (connectionInitialBudgetId == NO_CREDITOR_INDEX)
                {
                    connectionInitialBudgetId = creditor.acquire(replyId, budgetId);
                }
            }

            if (connectionInitialBudgetId != NO_CREDITOR_INDEX)
            {
                creditor.credit(traceId, connectionInitialBudgetId, credit);
            }
        }

        private void onNetworkMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetworkReplyBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onNetworkReplyData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetworkReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetworkReplyAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetworkInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetworkInitialWindow(window);
                break;
            default:
                break;
            }
        }

        private void onNetworkReplyBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = KafkaState.openingReply(state);
        }

        private void onNetworkReplyData(
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
                final int limit = payload.sizeof();
                int progress = payload.offset();

                ResponseHeaderV0FW responseHeader = responseHeaderRO.wrap(buffer, progress, limit);

                int correlationId = responseHeader.correlationId();
                Long initialId = correlations.remove(correlationId);

                delegate.doConnectionReplyData(initialId, traceId, flags, sequence, acknowledge,
                    reserved, payload, extension);
            }
        }

        private void onNetworkReplyEnd(
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

            doNetworkInitialEnd(traceId);

            delegate.onNetworkEnd(traceId);
        }

        private void onNetworkReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            doNetworkInitialAbort(traceId);

            delegate.connectionCleanup(traceId);
        }

        private void doNetworkReplyReset(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doReset(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);

                state = KafkaState.closedReply(state);
            }
        }

        private void doNetworkReplyWindow(
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

        private void networkCleanup(
            long traceId)
        {
            doNetworkInitialAbort(traceId);
            doNetworkReplyReset(traceId);

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
    }
}
