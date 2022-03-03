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
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.nio.ByteBuffer;
import java.security.Principal;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.tls.internal.TlsConfiguration;
import io.aklivity.zilla.runtime.binding.tls.internal.config.TlsBindingConfig;
import io.aklivity.zilla.runtime.binding.tls.internal.config.TlsRouteConfig;
import io.aklivity.zilla.runtime.binding.tls.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.codec.TlsRecordInfoFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.codec.TlsUnwrappedDataFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.codec.TlsUnwrappedInfoFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.EndFW;
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
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public final class TlsServerFactory implements TlsStreamFactory
{
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};
    private static final int MAXIMUM_HEADER_SIZE = 5 + 20 + 256;    // TODO version + MAC + padding
    private static final int NET_SIGNAL_HANDSHAKE_TASK_COMPLETE = 1;
    private static final int NET_SIGNAL_HANDSHAKE_TIMEOUT = 2;
    private static final int APP_SIGNAL_RESET_LATER = 1;
    private static final MutableDirectBuffer EMPTY_MUTABLE_DIRECT_BUFFER = new UnsafeBuffer(new byte[0]);

    static final Optional<TlsServer.TlsStream> NULL_STREAM = Optional.ofNullable(null);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final FlushFW flushRO = new FlushFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final SignalFW signalRO = new SignalFW();

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

    private final TlsRecordInfoFW tlsRecordInfoRO = new TlsRecordInfoFW();

    private final TlsRecordInfoFW.Builder tlsRecordInfoRW = new TlsRecordInfoFW.Builder();
    private final TlsUnwrappedInfoFW.Builder tlsUnwrappedInfoRW = new TlsUnwrappedInfoFW.Builder();
    private final TlsUnwrappedDataFW tlsUnwrappedDataRO = new TlsUnwrappedDataFW();
    private final TlsUnwrappedDataFW.Builder tlsUnwrappedDataRW = new TlsUnwrappedDataFW.Builder();

    private final TlsServerDecoder decodeHandshake = this::decodeHandshake;
    private final TlsServerDecoder decodeBeforeHandshake = this::decodeBeforeHandshake;
    private final TlsServerDecoder decodeHandshakeFinished = this::decodeHandshakeFinished;
    private final TlsServerDecoder decodeHandshakeNeedTask = this::decodeHandshakeNeedTask;
    private final TlsServerDecoder decodeHandshakeNeedUnwrap = this::decodeHandshakeNeedUnwrap;
    private final TlsServerDecoder decodeHandshakeNeedWrap = this::decodeHandshakeNeedWrap;
    private final TlsServerDecoder decodeNotHandshaking = this::decodeNotHandshaking;
    private final TlsServerDecoder decodeNotHandshakingUnwrapped = this::decodeNotHandshakingUnwrapped;
    private final TlsServerDecoder decodeIgnoreAll = this::decodeIgnoreAll;

    private final Matcher matchCN = Pattern.compile("CN=([^,]*).*").matcher("");

    private final int proxyTypeId;
    private final Signaler signaler;
    private final MutableDirectBuffer writeBuffer;
    private final BindingHandler streamFactory;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final String keyManagerAlgorithm;
    private final boolean ignoreEmptyVaultRefs;
    private final long awaitSyncCloseMillis;
    private final LongFunction<VaultHandler> supplyVault;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int replyPadAdjust;
    private final Long2ObjectHashMap<TlsBindingConfig> bindings;

    private final int decodeMax;
    private final int handshakeMax;
    private final long handshakeTimeoutMillis;

    private final ByteBuffer inNetByteBuffer;
    private final MutableDirectBuffer inNetBuffer;
    private final ByteBuffer outNetByteBuffer;
    private final DirectBuffer outNetBuffer;
    private final ByteBuffer inAppByteBuffer;
    private final MutableDirectBuffer inAppBuffer;
    private final ByteBuffer outAppByteBuffer;
    private final DirectBuffer outAppBuffer;

    private final SecureRandom random;

    public TlsServerFactory(
        TlsConfiguration config,
        EngineContext context)
    {
        this.proxyTypeId = context.supplyTypeId("proxy");
        this.signaler = context.signaler();
        this.writeBuffer = context.writeBuffer();
        this.streamFactory = context.streamFactory();
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool();

        this.keyManagerAlgorithm = config.keyManagerAlgorithm();
        this.ignoreEmptyVaultRefs = config.ignoreEmptyVaultRefs();
        this.awaitSyncCloseMillis = config.awaitSyncCloseMillis();
        this.supplyVault = context::supplyVault;
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.replyPadAdjust = Math.max(context.bufferPool().slotCapacity() >> 14, 1) * MAXIMUM_HEADER_SIZE;
        this.decodeMax = decodePool.slotCapacity();
        this.handshakeMax = Math.min(config.handshakeWindowBytes(), decodeMax);
        this.handshakeTimeoutMillis = SECONDS.toMillis(config.handshakeTimeout());
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
        MessageConsumer net)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long routeId = begin.routeId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();

        TlsBindingConfig binding = bindings.get(routeId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final SSLEngine tlsEngine = binding.newServerEngine(authorization);

            if (tlsEngine != null)
            {
                // TODO: realm identity and authorization
                newStream = new TlsServer(
                    net,
                    routeId,
                    initialId,
                    authorization,
                    tlsEngine,
                    dname -> 0L)::onNetMessage;
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
        DirectBuffer buffer,
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
                .payload(buffer, offset, length)
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
        Consumer<OctetsFW.Builder> extension)
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
        Consumer<OctetsFW.Builder> extension)
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
        MessageConsumer receiver,
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

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer receiver,
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

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private int decodeBeforeHandshake(
        TlsServer server,
        long traceId,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        try
        {
            server.tlsEngine.beginHandshake();
            server.decoder = decodeHandshake;
        }
        catch (SSLException | RuntimeException ex)
        {
            server.cleanupNet(traceId);
            server.decoder = decodeIgnoreAll;
        }

        return progress;
    }

    private int decodeHandshake(
        TlsServer server,
        long traceId,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final SSLEngine tlsEngine = server.tlsEngine;
        switch (tlsEngine.getHandshakeStatus())
        {
        case NOT_HANDSHAKING:
            server.decoder = decodeNotHandshaking;
            break;
        case FINISHED:
            server.decoder = decodeHandshakeFinished;
            break;
        case NEED_TASK:
            server.decoder = decodeHandshakeNeedTask;
            break;
        case NEED_WRAP:
            server.decoder = decodeHandshakeNeedWrap;
            break;
        case NEED_UNWRAP:
            server.decoder = decodeHandshakeNeedUnwrap;
            break;
        case NEED_UNWRAP_AGAIN:
            assert false : "NEED_UNWRAP_AGAIN used by DTLS only";
            break;
        }

        return progress;
    }

    private int decodeNotHandshaking(
        TlsServer server,
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

                server.decodableRecordBytes = tlsRecordBytes;

                if (tlsRecordBytes <= length)
                {
                    final int tlsRecordDataOffset = tlsRecordInfo.limit();
                    final int tlsRecordDataLimit = tlsRecordDataOffset + tlsRecordInfo.length();

                    assert tlsRecordBytes == tlsRecordDataLimit - progress;

                    inNetByteBuffer.clear();
                    inNetBuffer.putBytes(0, buffer, progress, tlsRecordDataLimit - progress);
                    inNetByteBuffer.limit(tlsRecordDataLimit - progress);
                    outAppByteBuffer.clear();

                    try
                    {
                        final SSLEngineResult result = server.tlsEngine.unwrap(inNetByteBuffer, outAppByteBuffer);
                        final int bytesProduced = result.bytesProduced();
                        final int bytesConsumed = result.bytesConsumed();

                        switch (result.getStatus())
                        {
                        case BUFFER_UNDERFLOW:
                        case BUFFER_OVERFLOW:
                            assert false;
                            break;
                        case OK:
                            if (bytesProduced == 0)
                            {
                                server.decoder = decodeHandshake;
                                progress += bytesConsumed;
                            }
                            else
                            {
                                assert bytesConsumed == tlsRecordBytes;
                                assert bytesProduced <= bytesConsumed : String.format("%d <= %d", bytesProduced, bytesConsumed);

                                tlsUnwrappedDataRW.wrap(buffer, tlsRecordDataOffset, tlsRecordDataLimit)
                                                  .payload(outAppBuffer, 0, bytesProduced)
                                                  .build();

                                server.decodableRecordBytes -= bytesConsumed;
                                assert server.decodableRecordBytes == 0;

                                server.decoder = decodeNotHandshakingUnwrapped;
                            }
                            break;
                        case CLOSED:
                            assert bytesProduced == 0;
                            server.onDecodeInboundClosed(traceId);
                            server.decoder = TlsState.initialClosed(server.state) ? decodeIgnoreAll : decodeHandshake;
                            progress += bytesConsumed;
                            break;
                        }
                    }
                    catch (SSLException | RuntimeException ex)
                    {
                        server.cleanupNet(traceId);
                        server.decoder = decodeIgnoreAll;
                    }
                }
                else if (TlsState.initialClosed(server.state))
                {
                    server.decoder = decodeIgnoreAll;
                }
            }
        }

        return progress;
    }

    private int decodeNotHandshakingUnwrapped(
        TlsServer server,
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
            assert server.decodableRecordBytes == 0;

            final TlsRecordInfoFW tlsRecordInfo = tlsRecordInfoRO.wrap(buffer, progress, limit);
            final int tlsRecordDataOffset = tlsRecordInfo.limit();
            final int tlsRecordDataLimit = tlsRecordDataOffset + tlsRecordInfo.length();

            final TlsUnwrappedDataFW tlsUnwrappedData = tlsUnwrappedDataRO.wrap(buffer, tlsRecordDataOffset, tlsRecordDataLimit);
            final TlsServer.TlsStream stream = server.stream.orElse(null);
            final int initialWindow = stream != null ? stream.initialWindow() : 0;
            final int initialPad = stream != null ? stream.initialPad : 0;

            final int bytesOffset = tlsRecordInfo.sizeof();
            final int bytesConsumed = bytesOffset + tlsRecordInfo.length();
            final int bytesProduced = tlsUnwrappedData.length();

            final int bytesPosition = tlsUnwrappedData.info().position();
            final int bytesRemaining = bytesProduced - bytesPosition;

            assert bytesRemaining > 0 : String.format("%d > 0", bytesRemaining);

            final int bytesReservedMax = Math.min(initialWindow, bytesRemaining + initialPad);
            final int bytesRemainingMax = Math.max(bytesReservedMax - initialPad, 0);

            assert bytesReservedMax >= bytesRemainingMax : String.format("%d >= %d", bytesReservedMax, bytesRemainingMax);

            if (bytesRemainingMax > 0)
            {
                final OctetsFW payload = tlsUnwrappedData.payload();

                server.onDecodeUnwrapped(traceId, budgetId, bytesReservedMax,
                        payload.buffer(), payload.offset() + bytesPosition, bytesRemainingMax);

                final int newBytesPosition = bytesPosition + bytesRemainingMax;
                assert newBytesPosition <= bytesProduced;

                if (newBytesPosition == bytesProduced)
                {
                    progress += bytesConsumed;
                    server.decoder = decodeHandshake;
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
        TlsServer server,
        long traceId,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        server.onDecodeHandshakeFinished(traceId, budgetId);
        server.decoder = decodeHandshake;
        return progress;
    }

    private int decodeHandshakeNeedTask(
        TlsServer server,
        long traceId,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        server.onDecodeHandshakeNeedTask(traceId);
        server.decoder = decodeHandshake;
        return progress;
    }

    private int decodeHandshakeNeedUnwrap(
        TlsServer server,
        long traceId,
        long budgetId,
        int reserved,
        MutableDirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;
        if (length > 0 || !server.stream.isPresent())
        {
            inNetByteBuffer.clear();
            inNetBuffer.putBytes(0, buffer, progress, length);
            inNetByteBuffer.limit(length);
            outAppByteBuffer.clear();

            try
            {
                final SSLEngineResult result = server.tlsEngine.unwrap(inNetByteBuffer, outAppByteBuffer);
                final int bytesConsumed = result.bytesConsumed();
                final int bytesProduced = result.bytesProduced();

                switch (result.getStatus())
                {
                case BUFFER_UNDERFLOW:
                    if (TlsState.initialClosed(server.state))
                    {
                        server.decoder = decodeIgnoreAll;
                    }
                    break;
                case BUFFER_OVERFLOW:
                    assert false;
                    break;
                case OK:
                    final HandshakeStatus handshakeStatus = result.getHandshakeStatus();
                    if (handshakeStatus == HandshakeStatus.FINISHED)
                    {
                        server.onDecodeHandshakeFinished(traceId, budgetId);
                    }

                    if (bytesProduced > 0 && handshakeStatus == HandshakeStatus.FINISHED)
                    {
                        final TlsRecordInfoFW tlsRecordInfo = tlsRecordInfoRW
                            .wrap(buffer, progress, progress + bytesConsumed)
                            .build();
                        final int tlsRecordDataOffset = tlsRecordInfo.limit();
                        final int tlsRecordDataLimit = tlsRecordDataOffset + tlsRecordInfo.length();

                        tlsUnwrappedDataRW.wrap(buffer, tlsRecordDataOffset, tlsRecordDataLimit)
                                          .payload(outAppBuffer, 0, bytesProduced)
                                          .build();
                        server.decoder = decodeNotHandshakingUnwrapped;
                    }
                    else
                    {
                        server.decoder = decodeHandshake;
                    }

                    break;
                case CLOSED:
                    assert bytesProduced == 0;
                    server.onDecodeInboundClosed(traceId);
                    server.decoder = decodeIgnoreAll;
                    break;
                }

                progress += bytesConsumed;
            }
            catch (SSLException | RuntimeException ex)
            {
                server.doEncodeWrapIfNecessary(traceId, budgetId);
                server.cleanupNet(traceId);
                server.decoder = decodeIgnoreAll;
            }
        }

        return progress;
    }

    private int decodeHandshakeNeedWrap(
        TlsServer server,
        long traceId,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        server.doEncodeWrap(traceId, budgetId, EMPTY_OCTETS);
        server.decoder = server.tlsEngine.isInboundDone() ? decodeIgnoreAll : decodeHandshake;
        return progress;
    }

    private int decodeIgnoreAll(
        TlsServer server,
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
    private interface TlsServerDecoder
    {
        int decode(
            TlsServer server,
            long traceId,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int progress,
            int limit);
    }

    final class TlsServer
    {
        private final MessageConsumer net;
        private final long routeId;
        private final long initialId;
        private final long authorization;
        private ToLongFunction<String> supplyAuthorization;
        private final long replyId;
        private long affinity;

        private ProxyBeginExFW extension;

        private final SSLEngine tlsEngine;

        private long handshakeTaskFutureId = NO_CANCEL_ID;
        private long handshakeTimeoutFutureId = NO_CANCEL_ID;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;
        private long decodeSlotBudgetId;

        private int decodableRecordBytes;

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
        private TlsServerDecoder decoder;
        private Optional<TlsStream> stream;

        private TlsServer(
            MessageConsumer net,
            long routeId,
            long initialId,
            long authorization,
            SSLEngine tlsEngine,
            ToLongFunction<String> supplyAuthorization)
        {
            this.net = net;
            this.routeId = routeId;

            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.decoder = decodeBeforeHandshake;
            this.stream = NULL_STREAM;
            this.tlsEngine = requireNonNull(tlsEngine);
            this.supplyAuthorization = supplyAuthorization;
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
                    routeId,
                    replyId,
                    NET_SIGNAL_HANDSHAKE_TIMEOUT);
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
            final long budgetId = decodeSlotBudgetId; // TODO

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

                cancelHandshakeTask();
                cancelHandshakeTimeout();

                stream.ifPresent(s -> s.doAppAbort(traceId));

                if (!stream.isPresent())
                {
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

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            state = TlsState.closeInitial(state);
            initialSeq = sequence;
            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            cleanupDecodeSlot();

            cancelHandshakeTask();
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

            cancelHandshakeTask();

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
            case NET_SIGNAL_HANDSHAKE_TASK_COMPLETE:
                onNetSignalHandshakeTaskComplete(signal);
                break;
            case NET_SIGNAL_HANDSHAKE_TIMEOUT:
                onNetSignalHandshakeTimeout(signal);
                break;
            }
        }

        private void onNetSignalHandshakeTaskComplete(
            SignalFW signal)
        {
            //assert handshakeTaskFutureId != NO_CANCEL_ID;

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
            doBegin(net, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
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
                doEnd(net, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, EMPTY_EXTENSION);
                state = TlsState.closeReply(state);
            }

            cleanupEncodeSlot();

            cancelHandshakeTask();
        }

        private void doNetAbort(
            long traceId)
        {
            if (!TlsState.replyClosed(state))
            {
                doAbort(net, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, EMPTY_EXTENSION);
                state = TlsState.closeReply(state);
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
            doFlush(net, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, reserved, extension);
        }

        private void doNetReset(
            long traceId)
        {
            if (!TlsState.initialClosed(state))
            {
                final int initialMax = stream.isPresent() ? decodeMax : handshakeMax;
                doReset(net, routeId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
                state = TlsState.closeInitial(state);
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
            doWindow(net, routeId, initialId, initialSeq, initialAck, maximum, traceId, authorization, budgetId, padding);
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

                doData(net, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
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
            TlsServerDecoder previous = null;
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
                        doEncodeCloseOutbound(traceId, budgetId);
                        doNetEnd(traceId);
                    }

                    decoder = decodeIgnoreAll;
                }
            }

            if (!tlsEngine.isInboundDone())
            {
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
                    handshakeTaskFutureId = signaler.signalTask(task, routeId, replyId, NET_SIGNAL_HANDSHAKE_TASK_COMPLETE);
                }
            }
        }

        private void onDecodeHandshakeFinished(
            long traceId,
            long budgetId)
        {
            assert handshakeTimeoutFutureId != NO_CANCEL_ID;
            cancelHandshakeTimeout();

            ExtendedSSLSession tlsSession = (ExtendedSSLSession) tlsEngine.getSession();
            List<SNIServerName> serverNames = tlsSession.getRequestedServerNames();
            String name = getCommonName(tlsEngine);
            String alpn = tlsEngine.getApplicationProtocol();

            String tlsHostname = serverNames.stream()
                                            .filter(SNIHostName.class::isInstance)
                                            .map(SNIHostName.class::cast)
                                            .map(SNIHostName::getAsciiName)
                                            .findFirst()
                                            .orElse(null);

            String tlsProtocol = "".equals(alpn) ? null : alpn;

            final TlsBindingConfig binding = bindings.get(routeId);
            final TlsRouteConfig route = binding != null ? binding.resolve(authorization, tlsHostname, tlsProtocol) : null;

            if (route != null)
            {
                final TlsStream stream = new TlsStream(route.id, tlsEngine);

                stream.doAppBegin(traceId, tlsHostname, tlsProtocol, name);
            }
            else
            {
                tlsEngine.closeOutbound();
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
                        assert tlsEngine.isOutboundDone();
                        stream.ifPresent(s -> s.doAppResetLater(traceId));
                        state = TlsState.closingReply(state);
                        break loop;
                    case OK:
                        assert bytesProduced > 0 || tlsEngine.isInboundDone();
                        if (result.getHandshakeStatus() == HandshakeStatus.FINISHED)
                        {
                            onDecodeHandshakeFinished(traceId, budgetId);
                        }
                        break;
                    }
                } while (inAppByteBuffer.hasRemaining());

                final int outNetBytesProduced = outNetByteBuffer.position();
                doNetData(traceId, budgetId, outNetBuffer, 0, outNetBytesProduced);
            }
            catch (SSLException | RuntimeException ex)
            {
                cleanupNet(traceId);
            }
        }

        private void doEncodeCloseOutbound(
            long traceId,
            long budgetId)
        {
            tlsEngine.closeOutbound();
            state = TlsState.closingReply(state);

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

        private void cancelHandshakeTask()
        {
            if (handshakeTaskFutureId != NO_CANCEL_ID)
            {
                signaler.cancel(handshakeTaskFutureId);
                handshakeTaskFutureId = NO_CANCEL_ID;
            }
        }

        final class TlsStream
        {
            private MessageConsumer app;
            private final long routeId;
            private final long initialId;
            private final long replyId;
            private final long authorization;

            private long initialSeq;
            private long initialAck;
            private int initialMax;
            private int initialPad;

            private long replySeq;
            private long replyAck;

            private int state;
            private long resetLaterAt = NO_CANCEL_ID;

            private TlsStream(
                long routeId,
                SSLEngine tlsEngine)
            {
                this.routeId = routeId;
                this.initialId = supplyInitialId.applyAsLong(routeId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.authorization = authorization(tlsEngine.getSession());
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
                case SignalFW.TYPE_ID:
                    final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                    onAppSignal(signal);
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

                if (TlsState.replyClosing(state))
                {
                    doAppReset(traceId);
                }
                else if (replySeq > replyAck + replyMax)
                {
                    cleanupApp(traceId);
                    doNetAbort(traceId);
                }
                else if (data.length() > 0)
                {
                    final long budgetId = data.budgetId();
                    final OctetsFW payload = data.payload();

                    doEncodeWrap(traceId, budgetId, payload);
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
                final long budgetId = 0L; // TODO

                assert acknowledge <= sequence;
                assert sequence >= replySeq;
                assert acknowledge <= replyAck;

                replySeq = sequence;

                assert replyAck <= replySeq;

                state = TlsState.closeReply(state);
                stream = nullIfClosed(state, stream);

                doEncodeCloseOutbound(traceId, budgetId);

                cleanupResetLater();
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

                cleanupResetLater();
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
                assert maximum >= initialMax;

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

            private void onAppSignal(
                SignalFW signal)
            {
                final long traceId = signal.traceId();
                final int signalId = signal.signalId();

                switch (signalId)
                {
                case APP_SIGNAL_RESET_LATER:
                    doAppReset(traceId);
                    break;
                }
            }

            private void doAppBegin(
                long traceId,
                String hostname,
                String protocol,
                String name)
            {
                initialSeq = TlsServer.this.initialSeq;
                initialAck = initialSeq;

                stream = Optional.of(this);
                state = TlsState.openingInitial(state);

                app = newStream(this::onAppMessage, routeId, initialId, initialSeq, initialAck, initialMax,
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

                                                              SSLSession session = tlsEngine.getSession();
                                                              String version = session.getProtocol();
                                                              is.item(i -> i.secure(s -> s.version(version)));

                                                              if (name != null)
                                                              {
                                                                  is.item(i -> i.secure(s -> s.name(name)));
                                                              }

                                                              String cipher = session.getCipherSuite();
                                                              is.item(i -> i.secure(s -> s.cipher(cipher)));
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

                doData(app, routeId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                        budgetId, reserved, buffer, offset, length, EMPTY_EXTENSION);

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;
            }

            private void doAppEnd(
                long traceId)
            {
                state = TlsState.closeInitial(state);
                stream = nullIfClosed(state, stream);
                doEnd(app, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, EMPTY_EXTENSION);
            }

            private void doAppAbort(
                long traceId)
            {
                if (!TlsState.initialClosed(state))
                {
                    state = TlsState.closeInitial(state);
                    stream = nullIfClosed(state, stream);
                    doAbort(app, routeId, initialId, initialSeq, initialAck, initialMax,
                            traceId, authorization, EMPTY_EXTENSION);
                }
            }

            private void doAppFlush(
                long traceId,
                long budgetId,
                int reserved,
                OctetsFW extension)
            {
                doFlush(app, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, budgetId, reserved, extension);
            }

            private void doAppReset(
                long traceId)
            {
                if (!TlsState.replyClosed(state))
                {
                    state = TlsState.closeReply(state);
                    stream = nullIfClosed(state, stream);

                    doReset(app, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                }

                cleanupResetLater();
            }

            private void doAppResetLater(
                long traceId)
            {
                if (!TlsState.replyClosing(state))
                {
                    state = TlsState.closingReply(state);

                    if (TlsState.initialClosing(state) &&
                        awaitSyncCloseMillis > 0L)
                    {
                        final long signalAt = currentTimeMillis() + awaitSyncCloseMillis;
                        resetLaterAt = signaler.signalAt(signalAt, routeId, initialId, APP_SIGNAL_RESET_LATER);
                    }
                    else
                    {
                        doAppReset(traceId);
                    }
                }
            }

            private void doAppWindow(
                long traceId)
            {
                state = TlsState.openReply(state);

                final int replyPad = TlsServer.this.replyPad + replyPadAdjust;
                doWindow(app, routeId, replyId, replySeq, replyAck, replyMax, traceId,
                         authorization, replyBudgetId, replyPad);
            }

            private void flushAppWindow(
                long traceId)
            {
                // TODO: consider encodePool capacity
                int replyAckMax = (int)(replySeq - TlsServer.this.replyPendingAck());
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

            private void cleanupResetLater()
            {
                if (resetLaterAt != NO_CANCEL_ID)
                {
                    signaler.cancel(resetLaterAt);
                }
            }
        }

        private long authorization(
            SSLSession tlsSession)
        {
            long authorization = 0L;

            try
            {
                Certificate[] certs = tlsSession.getPeerCertificates();
                if (certs.length > 1)
                {
                    Certificate signingCaCert = certs[1];
                    X509Certificate signingCaX509Cert = (X509Certificate) signingCaCert;
                    X500Principal x500Principal = signingCaX509Cert.getSubjectX500Principal();
                    String distinguishedName = x500Principal.getName();
                    authorization = supplyAuthorization.applyAsLong(distinguishedName);
                }
            }
            catch (SSLPeerUnverifiedException e)
            {
                // ignore
            }

            return authorization;
        }
    }

    private static Optional<TlsServer.TlsStream> nullIfClosed(
        int state,
        Optional<TlsServer.TlsStream> stream)
    {
        return TlsState.initialClosed(state) && TlsState.replyClosed(state) ? NULL_STREAM : stream;
    }

    private String getCommonName(
        SSLEngine tlsEngine)
    {
        String commonName = null;

        if (tlsEngine.getNeedClientAuth() || tlsEngine.getWantClientAuth())
        {
            try
            {
                SSLSession tlsSession = tlsEngine.getSession();
                Principal peer = tlsSession.getPeerPrincipal();
                if (peer != null)
                {
                    String name = peer.getName();
                    if (matchCN.reset(name).matches())
                    {
                        commonName = matchCN.group(1);
                    }
                }
            }
            catch (SSLPeerUnverifiedException ex)
            {
                // ignore
            }
        }

        return commonName;
    }
}
