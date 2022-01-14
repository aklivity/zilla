/*
 * Copyright 2021-2022 Aklivity Inc.
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
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.net.ssl.StandardConstants.SNI_HOST_NAME;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.tls.internal.TlsConfiguration;
import io.aklivity.zilla.runtime.binding.tls.internal.TlsCounters;
import io.aklivity.zilla.runtime.binding.tls.internal.config.TlsBindingConfig;
import io.aklivity.zilla.runtime.binding.tls.internal.config.TlsRouteConfig;
import io.aklivity.zilla.runtime.binding.tls.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.OctetsFW.Builder;
import io.aklivity.zilla.runtime.binding.tls.internal.types.codec.TlsRecordInfoFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.codec.TlsUnwrappedDataFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.codec.TlsUnwrappedInfoFW;
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
import io.aklivity.zilla.runtime.engine.buffer.CountingBufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public final class TlsClientFactory implements TlsStreamFactory
{
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};
    private static final int MAXIMUM_HEADER_SIZE = 5 + 20 + 256;    // TODO version + MAC + padding
    private static final int HANDSHAKE_TASK_COMPLETE_SIGNAL = 1;
    private static final int HANDSHAKE_TIMEOUT_SIGNAL = 2;
    private static final MutableDirectBuffer EMPTY_MUTABLE_DIRECT_BUFFER = new UnsafeBuffer(new byte[0]);

    private static final Optional<TlsStream> NULL_STREAM = ofNullable(null);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final SignalFW signalRO = new SignalFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();

    private final TlsRecordInfoFW tlsRecordInfoRO = new TlsRecordInfoFW();
    private final TlsUnwrappedInfoFW.Builder tlsUnwrappedInfoRW = new TlsUnwrappedInfoFW.Builder();
    private final TlsUnwrappedDataFW tlsUnwrappedDataRO = new TlsUnwrappedDataFW();
    private final TlsUnwrappedDataFW.Builder tlsUnwrappedDataRW = new TlsUnwrappedDataFW.Builder();

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final ProxyBeginExFW beginExRO = new ProxyBeginExFW();
    private final ProxyBeginExFW.Builder tlsBeginExRW = new ProxyBeginExFW.Builder();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final TlsClientDecoder decodeHandshake = this::decodeHandshake;
    private final TlsClientDecoder decodeHandshakeFinished = this::decodeHandshakeFinished;
    private final TlsClientDecoder decodeHandshakeNeedTask = this::decodeHandshakeNeedTask;
    private final TlsClientDecoder decodeHandshakeNeedUnwrap = this::decodeHandshakeNeedUnwrap;
    private final TlsClientDecoder decodeHandshakeNeedWrap = this::decodeHandshakeNeedWrap;
    private final TlsClientDecoder decodeNotHandshaking = this::decodeNotHandshaking;
    private final TlsClientDecoder decodeNotHandshakingUnwrapped = this::decodeNotHandshakingUnwrapped;
    private final TlsClientDecoder decodeIgnoreAll = this::decodeIgnoreAll;

    private final int proxyTypeId;
    private final Signaler signaler;
    private final MutableDirectBuffer writeBuffer;
    private final BindingHandler streamFactory;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final String keyManagerAlgorithm;
    private final boolean ignoreEmptyVaultRefs;
    private final LongFunction<VaultHandler> supplyVault;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int initialPadAdjust;
    private final Long2ObjectHashMap<TlsBindingConfig> bindings;

    private final int decodeMax;
    private final int handshakeMax;
    private final long handshakeTimeoutMillis;
    private final boolean proactiveReplyBegin;

    private final ByteBuffer inNetByteBuffer;
    private final MutableDirectBuffer inNetBuffer;
    private final ByteBuffer outNetByteBuffer;
    private final DirectBuffer outNetBuffer;
    private final ByteBuffer inAppByteBuffer;
    private final MutableDirectBuffer inAppBuffer;
    private final ByteBuffer outAppByteBuffer;
    private final DirectBuffer outAppBuffer;

    private final SecureRandom random;

    public TlsClientFactory(
        TlsConfiguration config,
        EngineContext context,
        TlsCounters counters)
    {
        this.proxyTypeId = context.supplyTypeId("proxy");
        this.signaler = context.signaler();
        this.writeBuffer = context.writeBuffer();
        this.streamFactory = context.streamFactory();

        BufferPool bufferPool = context.bufferPool();
        this.decodePool = new CountingBufferPool(bufferPool, counters.clientDecodeAcquires, counters.clientDecodeReleases);
        this.encodePool = new CountingBufferPool(bufferPool, counters.clientEncodeAcquires, counters.clientEncodeReleases);

        this.keyManagerAlgorithm = config.keyManagerAlgorithm();
        this.ignoreEmptyVaultRefs = config.ignoreEmptyVaultRefs();
        this.proactiveReplyBegin = config.proactiveClientReplyBegin();
        this.supplyVault = context::supplyVault;
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.decodeMax = decodePool.slotCapacity();
        this.handshakeMax = Math.min(config.handshakeWindowBytes(), decodeMax);
        this.handshakeTimeoutMillis = SECONDS.toMillis(config.handshakeTimeout());
        this.initialPadAdjust = Math.max(bufferPool.slotCapacity() >> 14, 1) * MAXIMUM_HEADER_SIZE;

        this.bindings = new Long2ObjectHashMap<>();
        this.inNetByteBuffer = ByteBuffer.allocate(writeBuffer.capacity());
        this.inNetBuffer = new UnsafeBuffer(inNetByteBuffer);
        this.outNetByteBuffer = ByteBuffer.allocate(writeBuffer.capacity() << 1);
        this.outNetBuffer = new UnsafeBuffer(outNetByteBuffer);
        this.inAppByteBuffer = ByteBuffer.allocate(writeBuffer.capacity());
        this.inAppBuffer = new UnsafeBuffer(inAppByteBuffer);
        this.outAppByteBuffer = ByteBuffer.allocate(writeBuffer.capacity());
        this.outAppBuffer = new UnsafeBuffer(outAppByteBuffer);

        this.random = new SecureRandom();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        TlsBindingConfig tlsBinding = new TlsBindingConfig(binding);

        VaultHandler vault = supplyVault.apply(tlsBinding.vaultId);

        if (vault != null)
        {
            tlsBinding.init(vault, ignoreEmptyVaultRefs, keyManagerAlgorithm, random);
        }

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
        MessageConsumer application)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long routeId = begin.routeId();
        final long authorization = begin.authorization();
        final ExtensionFW extension = begin.extension().get(extensionRO::tryWrap);
        final ProxyBeginExFW beginEx = extension != null && extension.typeId() == proxyTypeId
                ? begin.extension().get(beginExRO::tryWrap)
                : null;

        MessageConsumer newStream = null;

        TlsBindingConfig binding = bindings.get(routeId);
        TlsRouteConfig route = binding != null ? binding.resolve(authorization, beginEx) : null;
        if (route != null)
        {
            final SSLEngine tlsEngine = binding.newClientEngine(beginEx);

            if (tlsEngine != null)
            {
                final long resolvedId = route.id;

                final long initialId = begin.streamId();
                final long affinity = begin.affinity();

                newStream = new TlsStream(
                    application,
                    routeId,
                    initialId,
                    affinity,
                    tlsEngine,
                    resolvedId)::onAppMessage;
            }
        }

        return newStream;
    }

    private MessageConsumer newStream(
        MessageConsumer sender,
        long routeId,
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
                .routeId(routeId)
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
        long routeId,
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
                .routeId(routeId)
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
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer payload,
        int offset,
        int length,
        Consumer<OctetsFW.Builder> extension)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .budgetId(budgetId)
                .reserved(reserved)
                .payload(payload, offset, length)
                .extension(extension)
                .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Consumer<Builder> extension)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                               .routeId(routeId)
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
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Consumer<Builder> extension)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
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
        long routeId,
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
                .routeId(routeId)
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
        MessageConsumer sender,
        long routeId,
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
                .routeId(routeId)
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
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
               .routeId(routeId)
               .streamId(streamId)
               .sequence(sequence)
               .acknowledge(acknowledge)
               .maximum(maximum)
               .traceId(traceId)
               .authorization(authorization)
               .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private int decodeHandshake(
        TlsStream.TlsClient client,
        long traceId,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final SSLEngine tlsEngine = client.tlsEngine;
        switch (tlsEngine.getHandshakeStatus())
        {
        case NOT_HANDSHAKING:
            client.decoder = decodeNotHandshaking;
            break;
        case FINISHED:
            client.decoder = decodeHandshakeFinished;
            break;
        case NEED_TASK:
            client.decoder = decodeHandshakeNeedTask;
            break;
        case NEED_WRAP:
            client.decoder = decodeHandshakeNeedWrap;
            break;
        case NEED_UNWRAP:
            client.decoder = decodeHandshakeNeedUnwrap;
            break;
        case NEED_UNWRAP_AGAIN:
            assert false : "NEED_UNWRAP_AGAIN used by DTLS only";
            break;
        }

        return progress;
    }

    private int decodeNotHandshaking(
        TlsStream.TlsClient client,
        long traceId,
        long budgetId,
        int reserved,
        MutableDirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;
        if (length != 0)
        {
            final TlsRecordInfoFW tlsRecordInfo = tlsRecordInfoRO.tryWrap(buffer, progress, limit);
            if (tlsRecordInfo != null)
            {
                final int tlsRecordBytes = tlsRecordInfo.sizeof() + tlsRecordInfo.length();

                client.decodableRecordBytes = tlsRecordBytes;

                if (tlsRecordBytes <= length)
                {
                    final int tlsRecordDataOffset = tlsRecordInfo.limit();
                    final int tlsRecordDataLimit = tlsRecordDataOffset + tlsRecordInfo.length();

                    assert tlsRecordBytes == tlsRecordDataLimit - progress;

                    inNetByteBuffer.clear();
                    inNetBuffer.putBytes(0, buffer, progress, tlsRecordBytes);
                    inNetByteBuffer.limit(tlsRecordBytes);
                    outAppByteBuffer.clear();

                    try
                    {
                        final SSLEngineResult result = client.tlsEngine.unwrap(inNetByteBuffer, outAppByteBuffer);
                        final int bytesProduced = result.bytesProduced();
                        final int bytesConsumed = result.bytesConsumed();

                        switch (result.getStatus())
                        {
                        case BUFFER_UNDERFLOW:
                        case BUFFER_OVERFLOW:
                            assert false;
                            break;
                        case OK:
                            if (result.getHandshakeStatus() == HandshakeStatus.FINISHED)
                            {
                                if (!client.stream.isPresent())
                                {
                                    client.onDecodeHandshakeFinished(traceId, budgetId);
                                }
                            }

                            if (bytesProduced == 0)
                            {
                                client.decoder = decodeHandshake;
                                progress += bytesConsumed;
                            }
                            else
                            {
                                assert bytesConsumed == tlsRecordBytes;
                                assert bytesProduced <= bytesConsumed : String.format("%d <= %d", bytesProduced, bytesConsumed);

                                tlsUnwrappedDataRW.wrap(buffer, tlsRecordDataOffset, tlsRecordDataLimit)
                                                  .payload(outAppBuffer, 0, bytesProduced)
                                                  .build();

                                client.decodableRecordBytes -= bytesConsumed;
                                assert client.decodableRecordBytes == 0;

                                client.decoder = decodeNotHandshakingUnwrapped;
                            }
                            break;
                        case CLOSED:
                            assert bytesProduced == 0;
                            client.onDecodeInboundClosed(traceId);
                            client.decoder = TlsState.replyClosed(client.state) ? decodeIgnoreAll : decodeHandshake;
                            progress += bytesConsumed;
                            break;
                        }
                    }
                    catch (SSLException ex)
                    {
                        client.cleanupNet(traceId);
                        client.decoder = decodeIgnoreAll;
                    }
                }
                else if (TlsState.replyClosed(client.state))
                {
                    client.decoder = decodeIgnoreAll;
                }
            }
        }

        return progress;
    }

    private int decodeNotHandshakingUnwrapped(
        TlsStream.TlsClient client,
        long traceId,
        long budgetId,
        int reserved,
        MutableDirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;
        if (length != 0)
        {
            assert client.decodableRecordBytes == 0;

            final TlsRecordInfoFW tlsRecordInfo = tlsRecordInfoRO.wrap(buffer, progress, limit);
            final int tlsRecordDataOffset = tlsRecordInfo.limit();
            final int tlsRecordDataLimit = tlsRecordDataOffset + tlsRecordInfo.length();

            final TlsUnwrappedDataFW tlsUnwrappedData = tlsUnwrappedDataRO.wrap(buffer, tlsRecordDataOffset, tlsRecordDataLimit);
            final TlsStream stream = client.stream.orElse(null);
            final int replyWindow = stream != null ? stream.replyWindow() : 0;
            final int replyPad = stream != null ? stream.replyPad : 0;

            final int bytesOffset = tlsRecordInfo.sizeof();
            final int bytesConsumed = bytesOffset + tlsRecordInfo.length();
            final int bytesProduced = tlsUnwrappedData.length();

            final int bytesPosition = tlsUnwrappedData.info().position();
            final int bytesRemaining = bytesProduced - bytesPosition;

            assert bytesRemaining > 0 : String.format("%d > 0", bytesRemaining);

            final int bytesReservedMax = Math.min(replyWindow, bytesRemaining + replyPad);
            final int bytesRemainingMax = Math.max(bytesReservedMax - replyPad, 0);

            assert bytesReservedMax >= bytesRemainingMax : String.format("%d >= %d", bytesReservedMax, bytesRemainingMax);

            if (bytesRemainingMax > 0)
            {
                final OctetsFW payload = tlsUnwrappedData.payload();

                client.onDecodeUnwrapped(traceId, budgetId, bytesReservedMax, payload.buffer(),
                        payload.offset() + bytesPosition, bytesRemainingMax);

                final int newBytesPosition = bytesPosition + bytesRemainingMax;
                assert newBytesPosition <= bytesProduced;

                if (newBytesPosition == bytesProduced)
                {
                    progress += bytesConsumed;
                    client.decoder = decodeHandshake;
                }
                else
                {
                    tlsUnwrappedInfoRW.wrap(buffer, tlsRecordDataOffset, tlsRecordDataLimit)
                                      .position(newBytesPosition)
                                      .build();
                }
            }
        }

        return progress;
    }

    private int decodeHandshakeFinished(
        TlsStream.TlsClient client,
        long traceId,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        client.onDecodeHandshakeFinished(traceId, budgetId);
        client.decoder = decodeHandshake;
        return progress;
    }

    private int decodeHandshakeNeedTask(
        TlsStream.TlsClient client,
        long traceId,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        client.onDecodeHandshakeNeedTask(traceId);
        client.decoder = decodeHandshake;
        return progress;
    }

    private int decodeHandshakeNeedUnwrap(
        TlsStream.TlsClient client,
        long traceId,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;
        if (length != 0 || !client.stream.isPresent())
        {
            inNetByteBuffer.clear();
            inNetBuffer.putBytes(0, buffer, progress, length);
            inNetByteBuffer.limit(length);
            outAppByteBuffer.clear();

            try
            {
                final SSLEngineResult result = client.tlsEngine.unwrap(inNetByteBuffer, outAppByteBuffer);
                final int bytesConsumed = result.bytesConsumed();
                final int bytesProduced = result.bytesProduced();

                switch (result.getStatus())
                {
                case BUFFER_UNDERFLOW:
                    if (TlsState.replyClosed(client.state))
                    {
                        client.decoder = decodeIgnoreAll;
                    }
                    break;
                case BUFFER_OVERFLOW:
                    assert false;
                    break;
                case OK:
                    assert bytesProduced == 0;
                    if (result.getHandshakeStatus() == HandshakeStatus.FINISHED)
                    {
                        client.onDecodeHandshakeFinished(traceId, budgetId);
                    }
                    client.decoder = decodeHandshake;
                    break;
                case CLOSED:
                    assert bytesProduced == 0;
                    client.onDecodeInboundClosed(traceId);
                    client.decoder = decodeIgnoreAll;
                    break;
                }

                progress += bytesConsumed;
            }
            catch (SSLException ex)
            {
                client.cleanupNet(traceId);
                client.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodeHandshakeNeedWrap(
        TlsStream.TlsClient client,
        long traceId,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        client.doEncodeWrap(traceId, budgetId, EMPTY_OCTETS);
        client.decoder = client.tlsEngine.isInboundDone() ? decodeIgnoreAll : decodeHandshake;
        return progress;
    }

    private int decodeIgnoreAll(
        TlsStream.TlsClient client,
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
    private interface TlsClientDecoder
    {
        int decode(
            TlsStream.TlsClient client,
            long traceId,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int progress,
            int limit);
    }

    private final class TlsStream
    {
        private final MessageConsumer app;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final TlsClient client;

        private long initialSeq;
        private long initialAck;
        private long initialAuth;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private int state;

        private TlsStream(
            MessageConsumer application,
            long routeId,
            long initialId,
            long affinity,
            SSLEngine tlsEngine,
            long resolvedId)
        {
            this.app = application;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.client = new TlsClient(tlsEngine, resolvedId);
        }

        private int replyWindow()
        {
            return replyMax - (int)(replySeq - replyAck);
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
            final long authorization = begin.authorization();
            final OctetsFW extension = begin.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            initialAuth = authorization;

            assert initialAck <= initialSeq;

            state = TlsState.openInitial(state);

            client.doNetBegin(traceId, affinity, extension);
        }

        private void onAppFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            initialAuth = authorization;

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + client.initialMax)
            {
                cleanupApp(traceId);
                client.doNetAbort(traceId);
            }
            else
            {
                client.doNetFlush(traceId, budgetId, reserved, extension);
            }
        }

        private void onAppData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence + data.reserved();
            initialAuth = authorization;

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + client.initialMax)
            {
                cleanupApp(traceId);
                client.doNetAbort(traceId);
            }
            else if (data.length() > 0)
            {
                final long budgetId = data.budgetId();
                final OctetsFW payload = data.payload();

                client.doEncodeWrap(traceId, budgetId, payload);
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            final long budgetId = 0L; // TODO

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            initialAuth = authorization;

            assert initialAck <= initialSeq;

            state = TlsState.closeInitial(state);
            client.stream = nullIfClosed(state, client.stream);

            client.doEncodeCloseOutbound(traceId, budgetId);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            initialAuth = authorization;

            assert initialAck <= initialSeq;

            state = TlsState.closeInitial(state);
            client.stream = nullIfClosed(state, client.stream);

            client.doNetAbort(traceId);

            doAppAbort(traceId);
            client.doNetReset(traceId);
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
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = padding;

            assert replyAck <= replySeq;

            state = TlsState.openReply(state);

            client.flushNetWindow(traceId, budgetId, replyPad);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= replyAck;

            replyAck = acknowledge;

            assert replyAck <= replySeq;

            state = TlsState.closeInitial(state);
            client.stream = nullIfClosed(state, client.stream);

            client.doNetReset(traceId);

            doAppReset(traceId);
            client.doNetAbort(traceId);
        }

        private void doAppBegin(
            long traceId,
            long budgetId,
            String hostname,
            String protocol)
        {
            replySeq = client.replySeq;
            replyAck = replySeq;

            state = TlsState.openingReply(state);

            doBegin(app, routeId, replyId, replySeq, replyAck, replyMax, traceId, client.replyAuth, affinity,
                ex -> ex.set((b, o, l) -> tlsBeginExRW.wrap(b, o, l)
                                                      .typeId(proxyTypeId)
                                                      .address(a -> a.none(n -> {}))
                                                      .infos(is ->
                                                      {
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
        }

        private void doAppData(
            long traceId,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            assert reserved >= length + replyPad : String.format("%d >= %d", reserved, length + replyPad);

            doData(app, routeId, replyId, replySeq, replyAck, replyMax, traceId, client.replyAuth, budgetId,
                    reserved, buffer, offset, length, EMPTY_EXTENSION);

            replySeq += reserved;
            assert replySeq <= replyAck + replyMax;
        }

        private void doAppEnd(
            long traceId)
        {
            state = TlsState.closeReply(state);
            client.stream = nullIfClosed(state, client.stream);
            doEnd(app, routeId, replyId, replySeq, replyAck, replyMax, traceId, client.replyAuth, EMPTY_EXTENSION);
        }

        private void doAppAbort(
            long traceId)
        {
            if (TlsState.replyOpening(state) && !TlsState.replyClosed(state))
            {
                state = TlsState.closeReply(state);
                client.stream = nullIfClosed(state, client.stream);
                doAbort(app, routeId, replyId, replySeq, replyAck,
                        replyMax, traceId, client.replyAuth, EMPTY_EXTENSION);
            }
        }

        private void doAppFlush(
            long traceId,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(app, routeId, replyId, replySeq, replyAck,
                    replyMax, traceId, client.replyAuth, budgetId, reserved, extension);
        }

        private void doAppReset(
            long traceId)
        {
            if (TlsState.initialOpening(state) && !TlsState.initialClosed(state))
            {
                state = TlsState.closeInitial(state);
                client.stream = nullIfClosed(state, client.stream);

                doReset(app, routeId, initialId, initialSeq, initialAck, client.initialMax, traceId, initialAuth);
            }
        }

        private void doAppWindow(
            long traceId,
            long budgetId)
        {
            state = TlsState.openInitial(state);

            final int initialPad = client.initialPad + initialPadAdjust;
            doWindow(app, routeId, initialId, initialSeq, initialAck, client.initialMax, traceId,
                     initialAuth, budgetId, initialPad);
        }

        private void flushAppWindow(
            long traceId,
            long budgetId)
        {
            assert TlsState.initialOpened(state);

            // TODO: consider encodePool capacity
            int initialAckMax = (int)(initialSeq - client.initialPendingAck());
            if (initialAckMax > initialAck)
            {
                initialAck = initialAckMax;
                assert initialAck <= initialSeq;

                doAppWindow(traceId, budgetId);
            }
        }

        private void cleanupApp(
            long traceId)
        {
            doAppAbort(traceId);
            doAppReset(traceId);
        }

        private final class TlsClient
        {
            private final SSLEngine tlsEngine;
            private MessageConsumer net;
            private final long routeId;
            private final long initialId;
            private final long replyId;

            private TlsClientDecoder decoder;

            private long replyAuth;
            private int state;

            private long initialSeq;
            private long initialAck;
            private int initialMax;
            private int initialPad;

            private long replySeq;
            private long replyAck;

            private int encodeSlot = NO_SLOT;
            private int encodeSlotOffset;
            private long encodeSlotTraceId;

            private int decodeSlot = NO_SLOT;
            private int decodeSlotOffset;
            private int decodeSlotReserved;
            private long decodeSlotBudgetId;

            private int decodableRecordBytes;

            private long handshakeTaskFutureId = NO_CANCEL_ID;
            private long handshakeTimeoutFutureId = NO_CANCEL_ID;

            private Optional<TlsStream> stream;

            private TlsClient(
                SSLEngine tlsEngine,
                long routeId)
            {
                this.tlsEngine = tlsEngine;
                this.routeId = routeId;
                this.initialId = supplyInitialId.applyAsLong(routeId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.decoder = decodeHandshake;
                this.stream = NULL_STREAM;
            }

            public int initialPendingAck()
            {
                return (int)(initialSeq - initialAck) + encodeSlotOffset;
            }

            private int initialWindow()
            {
                return initialMax - initialPendingAck();
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
                final long authorization = begin.authorization();

                assert acknowledge <= sequence;
                assert sequence >= replySeq;
                assert acknowledge >= replyAck;

                state = TlsState.openReply(state);
                replySeq = sequence;
                replyAck = acknowledge;
                replyAuth = authorization;

                assert replyAck <= replySeq;

                doNetWindow(traceId, 0L, 0, handshakeMax);
            }

            private void onNetData(
                DataFW data)
            {
                final long sequence = data.sequence();
                final long acknowledge = data.acknowledge();
                final long traceId = data.traceId();
                final long authorization = data.authorization();
                final long budgetId = data.budgetId();

                assert acknowledge <= sequence;
                assert sequence >= replySeq;
                assert acknowledge <= replyAck;

                replySeq = sequence + data.reserved();
                replyAuth = authorization;

                assert replyAck <= replySeq;

                if (replySeq > replyAck + decodeMax)
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
                assert sequence >= replySeq;
                assert acknowledge <= replyAck;

                replySeq = sequence + flush.reserved();

                assert replyAck <= replySeq;

                if (replySeq > replyAck + decodeMax)
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
                final long authorization = end.authorization();
                final long budgetId = decodeSlotBudgetId; // TODO

                assert acknowledge <= sequence;
                assert sequence >= replySeq;
                assert acknowledge <= replyAck;

                state = TlsState.closeReply(state);
                replySeq = sequence;
                replyAuth = authorization;

                assert replyAck <= replySeq;

                if (decodeSlot == NO_SLOT || !stream.isPresent())
                {
                    cleanupDecodeSlot();

                    cancelHandshakeTask();
                    cancelHandshakeTimeout();

                    doAppAbort(traceId);

                    if (!stream.isPresent())
                    {
                        doAppReset(traceId);
                        doEncodeCloseOutbound(traceId, budgetId);
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
                final long authorization = abort.authorization();

                assert acknowledge <= sequence;
                assert sequence >= replySeq;
                assert acknowledge <= replyAck;

                state = TlsState.closeReply(state);
                replySeq = sequence;
                replyAuth = authorization;

                assert replyAck <= replySeq;

                cleanupDecodeSlot();

                cancelHandshakeTask();
                cancelHandshakeTimeout();

                doAppAbort(traceId);
                doAppReset(traceId);

                doNetAbort(traceId);
            }

            private void onNetReset(
                ResetFW reset)
            {
                final long sequence = reset.sequence();
                final long acknowledge = reset.acknowledge();
                final long traceId = reset.traceId();

                assert acknowledge <= sequence;
                assert sequence <= initialSeq;
                assert acknowledge >= initialAck;

                state = TlsState.closeInitial(state);
                initialAck = acknowledge;

                assert initialAck <= initialSeq;

                cleanupEncodeSlot();

                cancelHandshakeTask();

                doAppReset(traceId);
                doAppAbort(traceId);

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
                assert sequence <= initialSeq;
                assert acknowledge >= initialAck;
                assert maximum >= initialMax;

                state = TlsState.openInitial(state);
                initialAck = acknowledge;
                initialMax = maximum;
                initialPad = padding;

                assert initialAck <= initialSeq;

                if (encodeSlot != NO_SLOT)
                {
                    final MutableDirectBuffer buffer = encodePool.buffer(encodeSlot);
                    final int limit = encodeSlotOffset;

                    encodeNet(encodeSlotTraceId, budgetId, buffer, 0, limit);
                }

                doEncodeWrapIfNecessary(traceId, budgetId);

                if (encodeSlot == NO_SLOT)
                {
                    stream.ifPresent(s -> s.flushAppWindow(traceId, budgetId));
                }
            }

            private void onNetSignal(
                SignalFW signal)
            {
                switch (signal.signalId())
                {
                case HANDSHAKE_TASK_COMPLETE_SIGNAL:
                    onNetSignalHandshakeTaskComplete(signal);
                    break;
                case HANDSHAKE_TIMEOUT_SIGNAL:
                    onNetSignalHandshakeTimeout(signal);
                    break;
                }
            }

            private void onNetSignalHandshakeTaskComplete(
                SignalFW signal)
            {
                assert handshakeTaskFutureId != NO_CANCEL_ID;

                handshakeTaskFutureId = NO_CANCEL_ID;

                final long traceId = signal.traceId();
                final long budgetId = decodeSlotBudgetId; // TODO: signal.budgetId ?

                MutableDirectBuffer buffer = EMPTY_MUTABLE_DIRECT_BUFFER;
                int reserved = 0;
                int offset = 0;
                int limit = 0;

                if (decodeSlot != NO_SLOT)
                {
                    reserved = decodeSlotReserved;
                    buffer = decodePool.buffer(decodeSlot);
                    limit = decodeSlotOffset;
                }

                decodeNet(traceId, budgetId, reserved, buffer, offset, limit);
            }

            private void onNetSignalHandshakeTimeout(
                SignalFW signal)
            {
                if (handshakeTimeoutFutureId != NO_CANCEL_ID)
                {
                    handshakeTimeoutFutureId = NO_CANCEL_ID;

                    final long traceId = signal.traceId();

                    cleanupNet(traceId);
                    decoder = decodeIgnoreAll;
                }
            }

            private void doNetBegin(
                long traceId,
                long affinity,
                OctetsFW extension)
            {
                state = TlsState.openingInitial(state);

                net = newStream(this::onNetMessage, routeId, initialId, initialSeq, initialAck,
                        initialMax, traceId, initialAuth, affinity, ex -> ex.set(extension));

                try
                {
                    tlsEngine.beginHandshake();
                }
                catch (SSLException ex)
                {
                    cleanupNet(traceId);
                }

                if (handshakeTimeoutMillis > 0L)
                {
                    assert handshakeTimeoutFutureId == NO_CANCEL_ID;
                    handshakeTimeoutFutureId = signaler.signalAt(
                        currentTimeMillis() + handshakeTimeoutMillis,
                        routeId,
                        initialId,
                        HANDSHAKE_TIMEOUT_SIGNAL);
                }
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
                if (TlsState.initialOpening(state) && !TlsState.initialClosed(state))
                {
                    doEnd(net, routeId, initialId, initialSeq, initialAck,
                            initialMax, traceId, replyAuth, EMPTY_EXTENSION);
                    state = TlsState.closeInitial(state);
                }

                cleanupEncodeSlot();

                cancelHandshakeTask();
            }

            private void doNetAbort(
                long traceId)
            {
                if (!TlsState.initialClosed(state))
                {
                    doAbort(net, routeId, initialId, initialSeq, initialAck,
                            initialMax, traceId, replyAuth, EMPTY_EXTENSION);
                    state = TlsState.closeInitial(state);
                }

                cleanupEncodeSlot();

                cancelHandshakeTask();
            }

            private void doNetFlush(
                long traceId,
                long budgetId,
                int reserved,
                OctetsFW extension)
            {
                doFlush(net, routeId, initialId, initialSeq, initialAck,
                        initialMax, traceId, replyAuth, budgetId, reserved, extension);
            }

            private void doNetReset(
                long traceId)
            {
                if (!TlsState.replyClosed(state))
                {
                    doReset(net, routeId, replyId, replySeq, replyAck, initialMax, traceId, replyAuth);
                    state = TlsState.closeReply(state);
                }

                cleanupDecodeSlot();

                cancelHandshakeTask();
                cancelHandshakeTimeout();
            }

            private void doNetWindow(
                long traceId,
                long budgetId,
                int padding,
                int maximum)
            {
                doWindow(net, routeId, replyId, replySeq, replyAck, maximum, traceId, replyAuth, budgetId, padding);
            }

            private void flushNetWindow(
                long traceId,
                long budgetId,
                int replyPad)
            {
                final int replyMax = stream.isPresent() ? decodeMax : handshakeMax;
                final int decodable = decodeMax - replyMax;

                final long replyAckMax = Math.min(replyAck + decodable, replySeq);
                if (replyAckMax > replyAck)
                {
                    replyAck = replyAckMax;
                    assert replyAck <= replySeq;

                    doNetWindow(traceId, budgetId, replyPad, decodeMax);
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
                final int length = Math.max(Math.min(initialWindow() - initialPad, maxLength), 0);

                if (length > 0)
                {
                    final int reserved = length + initialPad;

                    doData(net, routeId, initialId, initialSeq, initialAck, initialMax, traceId, initialAuth, budgetId,
                            reserved, buffer, offset, length, EMPTY_EXTENSION);

                    initialSeq += reserved;

                    assert initialSeq <= initialAck + initialMax :
                        String.format("%d <= %d + %d", initialSeq, initialAck, initialMax);
                }

                final int remaining = maxLength - length;
                if (remaining > 0)
                {
                    if (encodeSlot == NO_SLOT)
                    {
                        encodeSlot = encodePool.acquire(initialId);
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

                    if (TlsState.initialClosing(state))
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
                TlsClientDecoder previous = null;
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

                    if (TlsState.replyClosed(state))
                    {
                        stream.ifPresent(s -> s.doAppAbort(traceId));

                        if (!stream.isPresent())
                        {
                            doEncodeCloseOutbound(traceId, budgetId);
                            doNetEnd(traceId);
                        }

                        decoder = decodeIgnoreAll;
                    }
                }

                if (!tlsEngine.isInboundDone())
                {
                    final int replyMax = stream.isPresent() ? decodeMax : handshakeMax;

                    final int decoded = reserved - decodeSlotReserved;
                    final int decodable = decodeMax - replyMax;

                    final long replyAckMax = Math.min(replyAck + decoded + decodable, replySeq);
                    if (replyAckMax > replyAck)
                    {
                        replyAck = replyAckMax;
                        assert replyAck <= replySeq;

                        doNetWindow(traceId, budgetId, 0, replyMax);
                    }
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

            private void onDecodeHandshakeNeedTask(
                long traceId)
            {
                if (handshakeTaskFutureId == NO_CANCEL_ID)
                {
                    final Runnable task = tlsEngine.getDelegatedTask();
                    assert task != null || tlsEngine.getHandshakeStatus() != HandshakeStatus.NEED_TASK;

                    if (task != null)
                    {
                        handshakeTaskFutureId = signaler.signalTask(task, routeId, initialId, HANDSHAKE_TASK_COMPLETE_SIGNAL);
                    }
                }
            }

            private void onDecodeHandshakeFinished(
                long traceId,
                long budgetId)
            {
                assert handshakeTimeoutFutureId != NO_CANCEL_ID;
                cancelHandshakeTimeout();

                assert stream == NULL_STREAM;
                stream = Optional.of(TlsStream.this);

                final String protocol = tlsEngine.getApplicationProtocol();
                ExtendedSSLSession session = (ExtendedSSLSession) tlsEngine.getSession();
                List<SNIServerName> serverNames = session.getRequestedServerNames();
                String hostname = serverNames.stream()
                        .filter(s -> s.getType() == SNI_HOST_NAME)
                        .map(SNIHostName.class::cast)
                        .map(SNIHostName::getAsciiName)
                        .findFirst()
                        .orElse(null);

                TlsBindingConfig binding = bindings.get(TlsStream.this.routeId);
                TlsRouteConfig route = binding.resolve(initialAuth, hostname, protocol);

                if (route == null || route.id != client.routeId)
                {
                    doAppReset(traceId);
                    doNetAbort(traceId);
                }
                else
                {
                    doAppBegin(traceId, budgetId, hostname, protocol);
                    doAppWindow(traceId, budgetId);
                }
            }

            private void onDecodeUnwrapped(
                long traceId,
                long budgetId,
                int reserved,
                DirectBuffer buffer,
                int offset,
                int length)
            {
                stream.ifPresent(s -> s.doAppData(traceId, budgetId, reserved, buffer, offset, length));
            }

            private void onDecodeInboundClosed(
                long traceId)
            {
                assert tlsEngine.isInboundDone();
                stream.ifPresent(s -> s.doAppEnd(traceId));
            }

            private void doEncodeWrap(
                long traceId,
                long budgetId,
                OctetsFW payload)
            {
                final DirectBuffer buffer = payload.buffer();
                final int offset = payload.offset();
                final int length = payload.sizeof();

                inAppByteBuffer.clear();
                inAppBuffer.putBytes(0, buffer, offset, length);
                inAppByteBuffer.limit(length);
                outNetByteBuffer.clear();

                try
                {
                    loop:
                    do
                    {
                        final SSLEngineResult result = tlsEngine.wrap(inAppByteBuffer, outNetByteBuffer);
                        final int bytesProduced = result.bytesProduced();

                        switch (result.getStatus())
                        {
                        case BUFFER_OVERFLOW:
                        case BUFFER_UNDERFLOW:
                            assert false;
                            break;
                        case CLOSED:
                            assert bytesProduced > 0;
                            doAppReset(traceId);
                            state = TlsState.closingReply(state);
                            break loop;
                        case OK:
                            assert bytesProduced > 0 || tlsEngine.isInboundDone();
                            if (result.getHandshakeStatus() == HandshakeStatus.FINISHED)
                            {
                                if (proactiveReplyBegin)
                                {
                                    onDecodeHandshakeFinished(traceId, budgetId);
                                }
                            }
                            break;
                        }
                    } while (inAppByteBuffer.hasRemaining());

                    final int outNetBytesProduced = outNetByteBuffer.position();
                    doNetData(traceId, budgetId, outNetBuffer, 0, outNetBytesProduced);
                }
                catch (SSLException ex)
                {
                    cleanupNet(traceId);
                }
            }

            private void doEncodeCloseOutbound(
                long traceId,
                long budgetId)
            {
                tlsEngine.closeOutbound();
                state = TlsState.closingInitial(state);

                doEncodeWrapIfNecessary(traceId, budgetId);
            }

            private void doEncodeWrapIfNecessary(
                long traceId,
                long budgetId)
            {
                if (tlsEngine.getHandshakeStatus() == HandshakeStatus.NEED_WRAP)
                {
                    doEncodeWrap(traceId, budgetId, EMPTY_OCTETS);
                }
            }

            private void cleanupNet(
                long traceId)
            {
                doNetReset(traceId);
                doNetAbort(traceId);

                cleanupApp(traceId);
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

            private void cancelHandshakeTask()
            {
                if (handshakeTaskFutureId != NO_CANCEL_ID)
                {
                    signaler.cancel(handshakeTaskFutureId);
                    handshakeTaskFutureId = NO_CANCEL_ID;
                }
            }
        }
    }

    private static Optional<TlsStream> nullIfClosed(
        int state,
        Optional<TlsStream> stream)
    {
        return TlsState.initialClosed(state) && TlsState.replyClosed(state) ? NULL_STREAM : stream;
    }
}
