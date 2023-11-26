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
package io.aklivity.zilla.runtime.binding.tls.internal.stream;

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.lang.System.currentTimeMillis;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.agrona.BitUtil.SIZE_OF_BYTE;
import static org.agrona.BitUtil.SIZE_OF_SHORT;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.tls.internal.TlsConfiguration;
import io.aklivity.zilla.runtime.binding.tls.internal.config.TlsBindingConfig;
import io.aklivity.zilla.runtime.binding.tls.internal.config.TlsRouteConfig;
import io.aklivity.zilla.runtime.binding.tls.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.codec.TlsRecordFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class TlsProxyFactory implements TlsStreamFactory
{
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};
    private static final int HANDSHAKE_TIMEOUT_SIGNAL = 1;

    private static final int RECORD_TYPE_HANDSHAKE = 22;
    private static final int MESSAGE_TYPE_CLIENT_HELLO = 1;
    private static final int EXTENSION_TYPE_SNI = 0;
    private static final int SNI_TYPE_HOSTNAME = 0;

    static final Optional<TlsProxy.TlsStream> NULL_STREAM = Optional.ofNullable(null);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final FlushFW flushRO = new FlushFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final SignalFW signalRO = new SignalFW();

    private final ExtensionFW extensionRO = new ExtensionFW();

    private final ProxyBeginExFW beginExRO = new ProxyBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final ProxyBeginExFW.Builder beginExRW = new ProxyBeginExFW.Builder();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final TlsRecordFW tlsRecordRO = new TlsRecordFW();

    private final TlsProxyDecoder decodeRecord = this::decodeRecord;
    private final TlsProxyDecoder decodeRecordBytes = this::decodeRecordBytes;
    private final TlsProxyDecoder decodeIgnoreAll = this::decodeIgnoreAll;

    private final int proxyTypeId;
    private final Signaler signaler;
    private final MutableDirectBuffer writeBuffer;
    private final BindingHandler streamFactory;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final Long2ObjectHashMap<TlsBindingConfig> bindings;

    private final int decodeMax;
    private final int handshakeMax;
    private final long handshakeTimeoutMillis;

    public TlsProxyFactory(
        TlsConfiguration config,
        EngineContext context)
    {
        this.proxyTypeId = context.supplyTypeId("proxy");
        this.signaler = context.signaler();
        this.writeBuffer = context.writeBuffer();
        this.streamFactory = context.streamFactory();
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool();

        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.decodeMax = decodePool.slotCapacity();
        this.handshakeMax = Math.min(config.handshakeWindowBytes(), decodeMax);
        this.handshakeTimeoutMillis = SECONDS.toMillis(config.handshakeTimeout());
        this.bindings = new Long2ObjectHashMap<>();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        TlsBindingConfig tlsBinding = new TlsBindingConfig(binding);
        bindings.put(binding.id, tlsBinding);
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
        MessageConsumer net)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final ExtensionFW extension = begin.extension().get(extensionRO::tryWrap);
        final ProxyBeginExFW beginEx = extension != null && extension.typeId() == proxyTypeId
            ? begin.extension().get(beginExRO::tryWrap)
            : null;
        final int port = TlsBindingConfig.resolveDestinationPort(beginEx);

        TlsBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null && !binding.routes.isEmpty())
        {
            newStream = new TlsProxy(
                net,
                originId,
                routedId,
                initialId,
                authorization,
                port)::onNetMessage;
        }

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

        MessageConsumer receiver =
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
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int length,
        Consumer<OctetsFW.Builder> extension)
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
                .payload(buffer, offset, length)
                .extension(extension)
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

    private void doWindow(
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

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
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

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private int decodeRecord(
        TlsProxy proxy,
        long traceId,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        decode:
        if (length != 0)
        {
            TlsRecordFW tlsRecord = tlsRecordRO.tryWrap(buffer, progress, limit);
            if (tlsRecord != null)
            {
                if (tlsRecord.type() != RECORD_TYPE_HANDSHAKE)
                {
                    proxy.doNetReset(traceId);
                    proxy.decoder = decodeIgnoreAll;
                    break decode;
                }

                DirectBuffer message = tlsRecord.payload().value();
                int messageProgress = 0;
                byte messageType = message.getByte(messageProgress++);
                int messageLength =
                        (message.getByte(messageProgress++) & 0xff) << 16 |
                        (message.getByte(messageProgress++) & 0xff) << 8  |
                        (message.getByte(messageProgress++) & 0xff) << 0;

                if (messageType != MESSAGE_TYPE_CLIENT_HELLO ||
                    messageProgress + messageLength != message.capacity())
                {
                    proxy.doNetReset(traceId);
                    proxy.decoder = decodeIgnoreAll;
                    break decode;
                }

                // skip version
                messageProgress += 2;

                // skip random
                messageProgress += 32;

                // skip session id
                messageProgress += SIZE_OF_BYTE + (message.getByte(messageProgress) & 0xff);

                // cipher suites
                messageProgress += SIZE_OF_SHORT + (message.getShort(messageProgress, BIG_ENDIAN) & 0xffff);

                // compress methods
                messageProgress += SIZE_OF_BYTE + (message.getByte(messageProgress) & 0xff);

                int extensionsLength = message.getShort(messageProgress, BIG_ENDIAN) & 0xffff;
                messageProgress += SIZE_OF_SHORT;

                if (messageProgress + extensionsLength != message.capacity())
                {
                    proxy.doNetReset(traceId);
                    proxy.decoder = decodeIgnoreAll;
                    break decode;
                }

                String serverName = null;
                while (messageProgress < message.capacity())
                {
                    int extensionType = message.getShort(messageProgress, BIG_ENDIAN) & 0xffff;
                    messageProgress += SIZE_OF_SHORT;
                    int extensionLength = message.getShort(messageProgress, BIG_ENDIAN) & 0xffff;
                    messageProgress += SIZE_OF_SHORT;

                    if (messageProgress + extensionLength > message.capacity())
                    {
                        proxy.doNetReset(traceId);
                        proxy.decoder = decodeIgnoreAll;
                        break decode;
                    }

                    if (extensionType == EXTENSION_TYPE_SNI)
                    {
                        int sniLength = message.getShort(messageProgress, BIG_ENDIAN) & 0xffff;
                        messageProgress += SIZE_OF_SHORT;

                        if (messageProgress + sniLength > message.capacity())
                        {
                            proxy.doNetReset(traceId);
                            proxy.decoder = decodeIgnoreAll;
                            break decode;
                        }

                        int sniType = message.getByte(messageProgress++);
                        if (sniType == SNI_TYPE_HOSTNAME)
                        {
                            int hostnameLength = message.getShort(messageProgress, BIG_ENDIAN) & 0xffff;
                            messageProgress += SIZE_OF_SHORT;

                            if (messageProgress + hostnameLength > message.capacity())
                            {
                                proxy.doNetReset(traceId);
                                proxy.decoder = decodeIgnoreAll;
                                break decode;
                            }

                            serverName = message.getStringWithoutLengthUtf8(messageProgress, hostnameLength);
                            messageProgress += hostnameLength;
                        }
                    }
                    else
                    {
                        messageProgress += extensionLength;
                    }
                }

                proxy.onDecodeServerName(serverName, null, traceId);
                proxy.decoder = decodeRecordBytes;
            }
        }

        return progress;
    }

    private int decodeRecordBytes(
        TlsProxy proxy,
        long traceId,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length != 0)
        {
            TlsProxy.TlsStream stream = proxy.stream.orElse(null);
            if (stream != null && stream.initialWindow() >= reserved)
            {
                stream.doAppData(traceId, budgetId, reserved, buffer, progress, length);
                progress = limit;
            }
        }

        return progress;
    }

    private int decodeIgnoreAll(
        TlsProxy proxy,
        long traceId,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        return limit;
    }

    @FunctionalInterface
    private interface TlsProxyDecoder
    {
        int decode(
            TlsProxy proxy,
            long traceId,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int progress,
            int limit);
    }

    final class TlsProxy
    {
        private final MessageConsumer net;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long authorization;
        private final long replyId;
        private final int port;
        private long affinity;

        private ProxyBeginExFW extension;

        private long handshakeTaskFutureId = NO_CANCEL_ID;
        private long handshakeTimeoutFutureId = NO_CANCEL_ID;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;
        private long decodeSlotBudgetId;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private long encodeSlotTraceId;

        private long initialSeq;
        private long initialAck;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBudgetId;

        private int state;
        private TlsProxyDecoder decoder;
        private Optional<TlsStream> stream;

        private TlsProxy(
            MessageConsumer net,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            int port)
        {
            this.net = net;
            this.originId = originId;
            this.routedId = routedId;

            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.port = port;
            this.decoder = decodeRecord;
            this.stream = NULL_STREAM;
        }

        private int replyPendingAck()
        {
            return (int)(replySeq - replyAck) + encodeSlotOffset;
        }

        private int replyWindow()
        {
            return replyMax - replyPendingAck();
        }

        private void onNetMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onNetData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onNetFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onNetSignal(signal);
                break;
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final ProxyBeginExFW beginEx = begin.extension().get(beginExRO::tryWrap);

            if (beginEx != null && beginEx.typeId() == proxyTypeId)
            {
                // TODO: use decodeSlot instead of allocation
                MutableDirectBuffer bufferEx = new UnsafeBuffer(new byte[beginEx.sizeof()]);
                bufferEx.putBytes(0, beginEx.buffer(), beginEx.offset(), beginEx.sizeof());
                extension = new ProxyBeginExFW().wrap(bufferEx, 0, bufferEx.capacity());
            }

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            state = TlsState.openInitial(state);
            initialSeq = sequence;
            initialAck = acknowledge;
            affinity = begin.affinity();

            assert initialAck <= initialSeq;

            doNetWindow(traceId, 0L, 0, handshakeMax);
            doNetBegin(traceId);

            if (handshakeTimeoutMillis > 0L)
            {
                assert handshakeTimeoutFutureId == NO_CANCEL_ID;
                handshakeTimeoutFutureId = signaler.signalAt(
                    currentTimeMillis() + handshakeTimeoutMillis,
                    originId,
                    routedId,
                    replyId,
                    traceId,
                    HANDSHAKE_TIMEOUT_SIGNAL,
                    0);
            }
        }

        private void onNetData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + decodeMax)
            {
                cleanupNet(traceId);
            }
            else
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNet(traceId);
                }
                else
                {
                    final OctetsFW payload = data.payload();
                    int reserved = data.reserved();
                    int offset = payload.offset();
                    int limit = payload.limit();

                    final MutableDirectBuffer buffer = decodePool.buffer(decodeSlot);
                    buffer.putBytes(decodeSlotOffset, payload.buffer(), offset, limit - offset);
                    decodeSlotOffset += limit - offset;
                    decodeSlotReserved += reserved;
                    decodeSlotBudgetId = budgetId;

                    offset = 0;
                    limit = decodeSlotOffset;
                    reserved = decodeSlotReserved;

                    decodeNet(traceId, budgetId, reserved, buffer, offset, limit);
                }
            }
        }

        private void onNetFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + flush.reserved();

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + decodeMax)
            {
                cleanupNet(traceId);
            }
            else
            {
                stream.ifPresent(s -> s.doAppFlush(traceId, budgetId, reserved, extension));
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            state = TlsState.closeInitial(state);
            initialSeq = sequence;
            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            if (decodeSlot == NO_SLOT || !stream.isPresent())
            {
                cleanupDecodeSlot();

                cancelHandshakeTimeout();

                stream.ifPresent(s -> s.doAppEnd(traceId));

                if (!stream.isPresent())
                {
                    doNetEnd(traceId);
                }

                decoder = decodeIgnoreAll;
            }
            else
            {
                decodeNet(traceId);
            }
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            state = TlsState.closeInitial(state);
            initialSeq = sequence;
            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            cleanupDecodeSlot();

            cancelHandshakeTimeout();

            stream.ifPresent(s -> s.doAppAbort(traceId));
            stream.ifPresent(s -> s.doAppReset(traceId));

            doNetAbort(traceId);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;

            state = TlsState.closeReply(state);
            replyAck = acknowledge;

            assert replyAck <= replySeq;

            cleanupEncodeSlot();

            stream.ifPresent(s -> s.doAppReset(traceId));
            stream.ifPresent(s -> s.doAppAbort(traceId));

            doNetReset(traceId);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int maximum = window.maximum();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            state = TlsState.openReply(state);
            replyAck = acknowledge;
            replyMax = maximum;
            replyBudgetId = budgetId;
            replyPad = padding;

            assert replyAck <= replySeq;

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer buffer = encodePool.buffer(encodeSlot);
                final int limit = encodeSlotOffset;

                encodeNet(encodeSlotTraceId, budgetId, buffer, 0, limit);
            }

            if (encodeSlot == NO_SLOT)
            {
                stream.ifPresent(s -> s.flushAppWindow(traceId));
            }
        }

        private void onNetSignal(
            SignalFW signal)
        {
            switch (signal.signalId())
            {
            case HANDSHAKE_TIMEOUT_SIGNAL:
                onNetSignalHandshakeTimeout(signal);
                break;
            }
        }

        private void onNetSignalHandshakeTimeout(
            SignalFW signal)
        {
            final long traceId = signal.traceId();

            if (handshakeTimeoutFutureId != NO_CANCEL_ID)
            {
                handshakeTimeoutFutureId = NO_CANCEL_ID;

                cleanupNet(traceId);
                decoder = decodeIgnoreAll;
            }
        }

        private void doNetBegin(
            long traceId)
        {
            doBegin(net, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                    affinity, EMPTY_EXTENSION);
            state = TlsState.openingReply(state);
        }

        private void doNetData(
            long traceId,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer encodeBuffer = encodePool.buffer(encodeSlot);
                encodeBuffer.putBytes(encodeSlotOffset, buffer, offset, limit - offset);
                encodeSlotOffset += limit - offset;
                encodeSlotTraceId = traceId;

                buffer = encodeBuffer;
                offset = 0;
                limit = encodeSlotOffset;
            }

            encodeNet(traceId, budgetId, buffer, offset, limit);
        }

        private void doNetEnd(
            long traceId)
        {
            if (!TlsState.replyClosed(state))
            {
                doEnd(net, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, EMPTY_EXTENSION);
                state = TlsState.closeReply(state);
            }

            cleanupEncodeSlot();
        }

        private void doNetAbort(
            long traceId)
        {
            if (!TlsState.replyClosed(state))
            {
                doAbort(net, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, EMPTY_EXTENSION);
                state = TlsState.closeReply(state);
            }

            cleanupEncodeSlot();
        }

        private void doNetFlush(
            long traceId,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(net, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, reserved, extension);
        }

        private void doNetReset(
            long traceId)
        {
            if (!TlsState.initialClosed(state))
            {
                final int initialMax = stream.isPresent() ? decodeMax : handshakeMax;
                doReset(net, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
                state = TlsState.closeInitial(state);
            }

            cleanupDecodeSlot();

            cancelHandshakeTimeout();
        }

        private void doNetWindow(
            long traceId,
            long budgetId,
            int padding,
            int maximum)
        {
            doWindow(net, originId, routedId, initialId, initialSeq, initialAck, maximum,
                    traceId, authorization, budgetId, padding);
        }

        private void flushNetWindow(
            long traceId,
            long budgetId,
            int initialPad)
        {
            final int initialMax = stream.isPresent() ? decodeMax : handshakeMax;
            final int decodable = decodeMax - initialMax;

            final long initialAckMax = Math.min(initialAck + decodable, initialSeq);
            if (initialAckMax > initialAck)
            {
                initialAck = initialAckMax;
                assert initialAck <= initialSeq;

                doNetWindow(traceId, budgetId, 0, initialMax);
            }

            decodeNet(traceId);
        }

        private void encodeNet(
            long traceId,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int length = Math.max(Math.min(replyWindow() - replyPad, maxLength), 0);

            if (length > 0)
            {
                final int reserved = length + replyPad;

                doData(net, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                       budgetId, reserved, buffer, offset, length, EMPTY_EXTENSION);

                replySeq += reserved;

                assert replySeq <= replyAck + replyMax :
                    String.format("%d <= %d + %d", replySeq, replyAck, replyMax);
            }

            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(replyId);
                }

                if (encodeSlot == NO_SLOT)
                {
                    cleanupNet(traceId);
                }
                else
                {
                    final MutableDirectBuffer encodeBuffer = encodePool.buffer(encodeSlot);
                    encodeBuffer.putBytes(0, buffer, offset + length, remaining);
                    encodeSlotOffset = remaining;
                }
            }
            else
            {
                cleanupEncodeSlot();

                if (TlsState.replyClosing(state))
                {
                    doNetEnd(traceId);
                }
            }
        }

        private void decodeNet(
            long traceId,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int limit)
        {
            TlsProxyDecoder previous = null;
            int progress = offset;
            while (progress <= limit && previous != decoder && handshakeTaskFutureId == NO_CANCEL_ID)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, budgetId, reserved, buffer, offset, progress, limit);
            }

            if (progress < limit)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNet(traceId);
                }
                else
                {
                    final MutableDirectBuffer decodeBuffer = decodePool.buffer(decodeSlot);
                    decodeBuffer.putBytes(0, buffer, progress, limit - progress);
                    decodeSlotOffset = limit - progress;
                    decodeSlotReserved = (limit - progress) * (reserved / (limit - offset));
                }
            }
            else
            {
                cleanupDecodeSlot();

                if (TlsState.initialClosed(state))
                {
                    stream.ifPresent(s -> s.doAppAbort(traceId));

                    if (!stream.isPresent())
                    {
                        doNetEnd(traceId);
                    }

                    decoder = decodeIgnoreAll;
                }
            }

            final int initialMax = stream.isPresent() ? decodeMax : handshakeMax;
            final int decoded = reserved - decodeSlotReserved;
            final int decodable = decodeMax - initialMax;

            final long initialAckMax = Math.min(initialAck + decoded + decodable, initialSeq);
            if (initialAckMax > initialAck)
            {
                initialAck = initialAckMax;
                assert initialAck <= initialSeq;

                doNetWindow(traceId, budgetId, 0, initialMax);
            }
        }

        private void decodeNet(
            long traceId)
        {
            if (decodeSlot != NO_SLOT)
            {
                final long budgetId = decodeSlotBudgetId; // TODO: signal.budgetId ?

                final MutableDirectBuffer buffer = decodePool.buffer(decodeSlot);
                final int reserved = decodeSlotReserved;
                final int offset = 0;
                final int limit = decodeSlotOffset;

                decodeNet(traceId, budgetId, reserved, buffer, offset, limit);
            }
        }

        private void onDecodeServerName(
            String tlsHostname,
            String tlsProtocol,
            long traceId)
        {
            final TlsBindingConfig binding = bindings.get(routedId);
            final TlsRouteConfig route = binding != null ? binding.resolve(authorization, tlsHostname, tlsProtocol, port) : null;

            if (route != null)
            {
                final TlsStream stream = new TlsStream(binding.id, route.id);

                stream.doAppBegin(traceId, tlsHostname, tlsProtocol);
            }
            else
            {
                doNetEnd(traceId);
            }

            if (handshakeTimeoutFutureId != NO_CANCEL_ID)
            {
                signaler.cancel(handshakeTimeoutFutureId);
                handshakeTimeoutFutureId = NO_CANCEL_ID;
            }
        }

        private void cleanupNet(
            long traceId)
        {
            doNetReset(traceId);
            doNetAbort(traceId);

            stream.ifPresent(s -> s.cleanupApp(traceId));
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
                decodeSlotReserved = 0;
            }
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeSlotTraceId = 0;
            }
        }

        private void cancelHandshakeTimeout()
        {
            if (handshakeTimeoutFutureId != NO_CANCEL_ID)
            {
                signaler.cancel(handshakeTimeoutFutureId);
                handshakeTimeoutFutureId = NO_CANCEL_ID;
            }
        }

        final class TlsStream
        {
            private MessageConsumer app;
            private final long originId;
            private final long routedId;
            private final long initialId;
            private final long replyId;

            private long initialSeq;
            private long initialAck;
            private int initialMax;
            private int initialPad;

            private long replySeq;
            private long replyAck;

            private int state;

            private TlsStream(
                long originId,
                long routedId)
            {
                this.originId = originId;
                this.routedId = routedId;
                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
            }

            private int initialWindow()
            {
                return initialMax - (int)(initialSeq - initialAck);
            }

            private void onAppMessage(
                int msgTypeId,
                DirectBuffer buffer,
                int index,
                int length)
            {
                switch (msgTypeId)
                {
                case BeginFW.TYPE_ID:
                    final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                    onAppBegin(begin);
                    break;
                case DataFW.TYPE_ID:
                    final DataFW data = dataRO.wrap(buffer, index, index + length);
                    onAppData(data);
                    break;
                case EndFW.TYPE_ID:
                    final EndFW end = endRO.wrap(buffer, index, index + length);
                    onAppEnd(end);
                    break;
                case AbortFW.TYPE_ID:
                    final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                    onAppAbort(abort);
                    break;
                case FlushFW.TYPE_ID:
                    final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                    onAppFlush(flush);
                    break;
                case WindowFW.TYPE_ID:
                    final WindowFW window = windowRO.wrap(buffer, index, index + length);
                    onAppWindow(window);
                    break;
                case ResetFW.TYPE_ID:
                    final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                    onAppReset(reset);
                    break;
                default:
                    break;
                }
            }

            private void onAppBegin(
                BeginFW begin)
            {
                final long sequence = begin.sequence();
                final long acknowledge = begin.acknowledge();
                final long traceId = begin.traceId();

                assert acknowledge <= sequence;
                assert sequence >= replySeq;
                assert acknowledge <= replyAck;

                replySeq = sequence;
                replyAck = acknowledge;

                assert replyAck <= replySeq;

                state = TlsState.openingReply(state);

                doAppWindow(traceId);
            }

            private void onAppData(
                DataFW data)
            {
                final long sequence = data.sequence();
                final long acknowledge = data.acknowledge();
                final long traceId = data.traceId();

                assert acknowledge <= sequence;
                assert sequence >= replySeq;
                assert acknowledge <= replyAck;

                replySeq = sequence + data.reserved();

                assert replyAck <= replySeq;

                if (replySeq > replyAck + replyMax)
                {
                    cleanupApp(traceId);
                    doNetAbort(traceId);
                }
                else if (data.length() > 0)
                {
                    final long budgetId = data.budgetId();
                    final OctetsFW payload = data.payload();

                    doNetData(traceId, budgetId, payload.buffer(), payload.offset(), payload.limit());
                }
            }

            private void onAppFlush(
                FlushFW flush)
            {
                final long sequence = flush.sequence();
                final long acknowledge = flush.acknowledge();
                final long traceId = flush.traceId();
                final long budgetId = flush.budgetId();
                final int reserved = flush.reserved();
                final OctetsFW extension = flush.extension();

                assert acknowledge <= sequence;
                assert sequence >= replySeq;
                assert acknowledge <= replyAck;

                replySeq = sequence;

                assert replyAck <= replySeq;

                if (replySeq > replyAck + replyMax)
                {
                    cleanupApp(traceId);
                    doNetAbort(traceId);
                }
                else
                {
                    doNetFlush(traceId, budgetId, reserved, extension);
                }
            }

            private void onAppEnd(
                EndFW end)
            {
                final long sequence = end.sequence();
                final long acknowledge = end.acknowledge();
                final long traceId = end.traceId();

                assert acknowledge <= sequence;
                assert sequence >= replySeq;
                assert acknowledge <= replyAck;

                replySeq = sequence;

                assert replyAck <= replySeq;

                state = TlsState.closeReply(state);
                stream = nullIfClosed(state, stream);

                doNetEnd(traceId);
            }

            private void onAppAbort(
                AbortFW abort)
            {
                final long sequence = abort.sequence();
                final long acknowledge = abort.acknowledge();
                final long traceId = abort.traceId();

                assert acknowledge <= sequence;
                assert sequence >= replySeq;
                assert acknowledge <= replyAck;

                replySeq = sequence;

                assert replyAck <= replySeq;

                state = TlsState.closeReply(state);
                stream = nullIfClosed(state, stream);

                doNetAbort(traceId);

                doAppAbort(traceId);
                doNetReset(traceId);
            }

            private void onAppWindow(
                WindowFW window)
            {
                final long sequence = window.sequence();
                final long acknowledge = window.acknowledge();
                final long traceId = window.traceId();
                final long budgetId = window.budgetId();
                final int maximum = window.maximum();
                final int padding = window.padding();

                assert acknowledge <= sequence;
                assert sequence <= initialSeq;
                assert acknowledge >= initialAck;
                assert maximum + acknowledge >= initialMax + initialAck;

                initialAck = acknowledge;
                initialMax = maximum;
                initialPad = padding;

                assert initialAck <= initialSeq;

                state = TlsState.openInitial(state);

                flushNetWindow(traceId, budgetId, initialPad);
            }

            private void onAppReset(
                ResetFW reset)
            {
                final long sequence = reset.sequence();
                final long acknowledge = reset.acknowledge();
                final long traceId = reset.traceId();

                assert acknowledge <= sequence;
                assert sequence <= initialSeq;
                assert acknowledge >= initialAck;

                initialAck = acknowledge;

                assert initialAck <= initialSeq;

                state = TlsState.closeInitial(state);
                stream = nullIfClosed(state, stream);

                doNetReset(traceId);

                doAppReset(traceId);
                doNetAbort(traceId);
            }

            private void doAppBegin(
                long traceId,
                String hostname,
                String protocol)
            {
                initialSeq = TlsProxy.this.initialSeq;
                initialAck = initialSeq;

                stream = Optional.of(this);
                state = TlsState.openingInitial(state);

                app = newStream(this::onAppMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity,
                    ex -> ex.set((b, o, l) -> beginExRW.wrap(b, o, l)
                                                          .typeId(proxyTypeId)
                                                          .address(a ->
                                                          {
                                                              if (extension != null)
                                                              {
                                                                  a.set(extension.address());
                                                              }
                                                              else
                                                              {
                                                                  a.none(n -> {});
                                                              }
                                                          })
                                                          .infos(is ->
                                                          {
                                                              if (extension != null)
                                                              {
                                                                  extension.infos().forEach(i ->
                                                                  {
                                                                      switch (i.kind())
                                                                      {
                                                                      case ALPN:
                                                                          if (protocol == null)
                                                                          {
                                                                              is.item(it -> it.set(i));
                                                                          }
                                                                          break;
                                                                      case AUTHORITY:
                                                                          if (hostname == null)
                                                                          {
                                                                              is.item(it -> it.set(i));
                                                                          }
                                                                          break;
                                                                      default:
                                                                          is.item(it -> it.set(i));
                                                                          break;
                                                                      }
                                                                  });
                                                              }

                                                              if (protocol != null)
                                                              {
                                                                  is.item(i -> i.alpn(protocol));
                                                              }

                                                              if (hostname != null)
                                                              {
                                                                  is.item(i -> i.authority(hostname));
                                                              }
                                                          })
                                                          .build()
                                                          .sizeof()));
                extension = null;
            }

            private void doAppData(
                long traceId,
                long budgetId,
                int reserved,
                DirectBuffer buffer,
                int offset,
                int length)
            {
                assert reserved >= length + initialPad : String.format("%d >= %d", reserved, length + initialPad);

                doData(app, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                        budgetId, reserved, buffer, offset, length, EMPTY_EXTENSION);

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;
            }

            private void doAppEnd(
                long traceId)
            {
                state = TlsState.closeInitial(state);
                stream = nullIfClosed(state, stream);
                doEnd(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, EMPTY_EXTENSION);
            }

            private void doAppAbort(
                long traceId)
            {
                if (!TlsState.initialClosed(state))
                {
                    state = TlsState.closeInitial(state);
                    stream = nullIfClosed(state, stream);
                    doAbort(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                            traceId, authorization, EMPTY_EXTENSION);
                }
            }

            private void doAppFlush(
                long traceId,
                long budgetId,
                int reserved,
                OctetsFW extension)
            {
                doFlush(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, budgetId, reserved, extension);
            }

            private void doAppReset(
                long traceId)
            {
                if (!TlsState.replyClosed(state))
                {
                    state = TlsState.closeReply(state);
                    stream = nullIfClosed(state, stream);

                    doReset(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                }
            }

            private void doAppWindow(
                long traceId)
            {
                state = TlsState.openReply(state);

                final int replyPad = TlsProxy.this.replyPad;
                doWindow(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId,
                         authorization, replyBudgetId, replyPad);
            }

            private void flushAppWindow(
                long traceId)
            {
                // TODO: consider encodePool capacity
                int replyAckMax = (int)(replySeq - TlsProxy.this.replyPendingAck());
                if (replyAckMax > replyAck)
                {
                    replyAck = replyAckMax;
                    assert replyAck <= replySeq;

                    doAppWindow(traceId);
                }
            }

            private void cleanupApp(
                long traceId)
            {
                doAppAbort(traceId);
                doAppReset(traceId);
            }
        }
    }

    private static Optional<TlsProxy.TlsStream> nullIfClosed(
        int state,
        Optional<TlsProxy.TlsStream> stream)
    {
        return TlsState.initialClosed(state) && TlsState.replyClosed(state) ? NULL_STREAM : stream;
    }
}
