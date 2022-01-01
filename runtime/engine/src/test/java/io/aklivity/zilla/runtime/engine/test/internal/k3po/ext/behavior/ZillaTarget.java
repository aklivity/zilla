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
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior;

import static io.aklivity.zilla.runtime.engine.internal.stream.BudgetId.budgetMask;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.NullChannelBuffer.NULL_BUFFER;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaExtensionKind.BEGIN;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaExtensionKind.CHALLENGE;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaExtensionKind.DATA;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaExtensionKind.END;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaExtensionKind.FLUSH;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.ZillaTypeSystem.ADVISORY_CHALLENGE;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.ZillaTypeSystem.ADVISORY_FLUSH;
import static java.util.Arrays.asList;
import static org.jboss.netty.buffer.ChannelBuffers.EMPTY_BUFFER;
import static org.jboss.netty.channel.Channels.fireChannelClosed;
import static org.jboss.netty.channel.Channels.fireChannelConnected;
import static org.jboss.netty.channel.Channels.fireChannelDisconnected;
import static org.jboss.netty.channel.Channels.fireChannelInterestChanged;
import static org.jboss.netty.channel.Channels.fireChannelUnbound;
import static org.jboss.netty.channel.Channels.fireWriteComplete;
import static org.jboss.netty.channel.Channels.future;
import static org.jboss.netty.channel.Channels.succeededFuture;
import static org.kaazing.k3po.driver.internal.netty.channel.Channels.fireFlushed;
import static org.kaazing.k3po.driver.internal.netty.channel.Channels.fireOutputAborted;
import static org.kaazing.k3po.driver.internal.netty.channel.Channels.fireOutputAdvised;
import static org.kaazing.k3po.driver.internal.netty.channel.Channels.fireOutputShutdown;

import java.nio.file.Path;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.MessageEvent;
import org.kaazing.k3po.driver.internal.netty.channel.CompositeChannelFuture;

import io.aklivity.zilla.runtime.engine.internal.budget.DefaultBudgetCreditor;
import io.aklivity.zilla.runtime.engine.internal.budget.DefaultBudgetDebitor;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.layout.Layout;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.layout.StreamsLayout;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.OctetsFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.util.function.LongObjectBiConsumer;

final class ZillaTarget implements AutoCloseable
{
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final ChallengeFW challengeRO = new ChallengeFW();

    private final OctetsFW octetsRO = new OctetsFW();

    private final MutableDirectBuffer resetBuffer = new UnsafeBuffer(new byte[ResetFW.FIELD_OFFSET_EXTENSION]);
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final int scopeIndex;
    private final Path streamsPath;
    private final Layout layout;
    private final RingBuffer streamsBuffer;
    private final LongObjectBiConsumer<MessageHandler> registerThrottle;
    private final LongConsumer unregisterThrottle;
    private final MutableDirectBuffer writeBuffer;
    private final LongObjectBiConsumer<ZillaCorrelation> correlateNew;
    private final LongSupplier supplyTimestamp;
    private final LongSupplier supplyTraceId;

    ZillaTarget(
        int scopeIndex,
        Path streamsPath,
        StreamsLayout layout,
        MutableDirectBuffer writeBuffer,
        LongObjectBiConsumer<MessageHandler> registerThrottle,
        LongConsumer unregisterThrottle,
        LongObjectBiConsumer<ZillaCorrelation> correlateNew,
        LongSupplier supplyTimestamp,
        LongSupplier supplyTraceId)
    {
        this.scopeIndex = scopeIndex;
        this.streamsPath = streamsPath;
        this.layout = layout;
        this.streamsBuffer = layout.streamsBuffer();
        this.writeBuffer = writeBuffer;

        this.registerThrottle = registerThrottle;
        this.unregisterThrottle = unregisterThrottle;
        this.correlateNew = correlateNew;
        this.supplyTimestamp = supplyTimestamp;
        this.supplyTraceId = supplyTraceId;
    }

    @Override
    public void close()
    {
        layout.close();
    }

    @Override
    public String toString()
    {
        return String.format("%s [%s]", getClass().getSimpleName(), streamsPath);
    }

    public RingBuffer streamsBuffer()
    {
        return streamsBuffer;
    }

    public void doSystemWindow(
        long traceId,
        long budgetId,
        int maximum)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(0L)
                .streamId(0L)
                .sequence(0L)
                .acknowledge(0L)
                .maximum(maximum)
                .traceId(traceId)
                .budgetId(budgetId)
                .padding(0)
                .build();

        streamsBuffer.write(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    public void doSystemFlush(
        long traceId,
        long budgetId)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(0L)
                .streamId(0L)
                .sequence(0L)
                .acknowledge(0L)
                .maximum(0)
                .traceId(traceId)
                .budgetId(budgetId)
                .build();

        streamsBuffer.write(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    public void doConnect(
        ZillaClientChannel clientChannel,
        ZillaChannelAddress localAddress,
        ZillaChannelAddress remoteAddress,
        ChannelFuture connectFuture)
    {
        try
        {
            final long routeId = clientChannel.routeId();
            final long initialId = clientChannel.targetId();
            final long replyId = initialId & 0xffff_ffff_ffff_fffeL;
            clientChannel.sourceId(replyId);

            final ChannelFuture windowFuture = future(clientChannel);
            ChannelFuture replyFuture = succeededFuture(clientChannel);

            final ZillaChannelConfig clientConfig = clientChannel.getConfig();
            switch (clientConfig.getTransmission())
            {
            case DUPLEX:
            {
                ChannelFuture correlatedFuture = clientChannel.beginInputFuture();
                correlateNew.accept(replyId, new ZillaCorrelation(clientChannel, correlatedFuture));
                replyFuture = correlatedFuture;
                break;
            }
            case HALF_DUPLEX:
            {
                ChannelFuture correlatedFuture = clientChannel.beginInputFuture();
                correlateNew.accept(replyId, new ZillaCorrelation(clientChannel, correlatedFuture));
                correlatedFuture.addListener(f -> fireChannelInterestChanged(f.getChannel()));
                break;
            }
            default:
                break;
            }

            final long budgetId = clientConfig.getBudgetId();
            if (budgetId != 0L)
            {
                final long creditorId = budgetId | budgetMask(scopeIndex);

                DefaultBudgetCreditor creditor = clientChannel.engine.supplyCreditor(clientChannel);
                clientChannel.setCreditor(creditor, creditorId);

                final int sharedWindow = clientConfig.getSharedWindow();
                if (sharedWindow != 0L)
                {
                    final long creditorIndex = creditor.acquire(creditorId);
                    if (creditorIndex == -1L)
                    {
                        clientChannel.getCloseFuture().setFailure(new ChannelException("Unable to acquire creditor"));
                    }
                    else
                    {
                        clientChannel.setCreditorIndex(creditorIndex);
                        creditor.credit(0L, creditorIndex, sharedWindow);
                    }
                }
            }

            final long authorization = remoteAddress.getAuthorization();
            clientChannel.targetAuth(authorization);

            final long affinity = clientConfig.getAffinity();

            ChannelBuffer beginExt = clientChannel.writeExtBuffer(BEGIN, true);
            final int writableExtBytes = beginExt.readableBytes();
            final byte[] beginExtCopy = writeExtCopy(beginExt);

            final long sequence = clientChannel.targetSeq();
            final long acknowledge = clientChannel.targetAck();
            final int maximum = clientChannel.targetMax();

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                   .routeId(routeId)
                   .streamId(initialId)
                   .sequence(sequence)
                   .acknowledge(acknowledge)
                   .maximum(maximum)
                   .timestamp(supplyTimestamp.getAsLong())
                   .traceId(supplyTraceId.getAsLong())
                   .authorization(authorization)
                   .affinity(affinity)
                   .extension(p -> p.set(beginExtCopy))
                   .build();

            clientChannel.setRemoteAddress(remoteAddress);

            ChannelFuture handshakeFuture = newHandshakeFuture(clientChannel, connectFuture, windowFuture, replyFuture);

            final Throttle throttle = new Throttle(clientChannel, windowFuture, handshakeFuture);
            registerThrottle.accept(begin.streamId(), throttle::handleThrottle);

            streamsBuffer.write(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

            beginExt.skipBytes(writableExtBytes);
            beginExt.discardReadBytes();

            final ZillaChannelConfig config = clientChannel.getConfig();
            if (config.getUpdate() == ZillaUpdateMode.PROACTIVE)
            {
                doWindow(clientChannel);
            }

            clientChannel.beginOutputFuture().setSuccess();
        }
        catch (Exception ex)
        {
            connectFuture.setFailure(ex);
        }
    }

    private ChannelFuture newHandshakeFuture(
        ZillaClientChannel clientChannel,
        ChannelFuture connectFuture,
        ChannelFuture windowFuture,
        ChannelFuture replyFuture)
    {
        ChannelFuture handshakeFuture = new CompositeChannelFuture<>(clientChannel, asList(windowFuture, replyFuture));
        handshakeFuture.addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(
                ChannelFuture future) throws Exception
            {
                if (future.isSuccess())
                {
                    clientChannel.setConnected();
                    clientChannel.setFlushable();

                    connectFuture.setSuccess();
                    fireChannelConnected(clientChannel, clientChannel.getRemoteAddress());
                }
                else
                {
                    connectFuture.setFailure(future.getCause());
                }
            }
        });
        return handshakeFuture;
    }

    public void doConnectAbort(
        ZillaClientChannel clientChannel)
    {
        final long routeId = clientChannel.routeId();
        final long initialId = clientChannel.targetId();
        final long sequence = clientChannel.targetSeq();
        final long acknowledge = clientChannel.targetAck();
        final int maximum = clientChannel.targetMax();

        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(initialId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .timestamp(supplyTimestamp.getAsLong())
                .traceId(supplyTraceId.getAsLong())
                .build();

        streamsBuffer.write(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());

        unregisterThrottle.accept(initialId);
    }

    public void doPrepareReply(
        ZillaChannel channel,
        ChannelFuture windowFuture,
        ChannelFuture handshakeFuture)
    {
        final Throttle throttle = new Throttle(channel, windowFuture, handshakeFuture);
        registerThrottle.accept(channel.targetId(), throttle::handleThrottle);
    }

    public void doBeginReply(
        ZillaChannel channel)
    {
        final ChannelBuffer beginExt = channel.writeExtBuffer(BEGIN, true);
        final int writableExtBytes = beginExt.readableBytes();
        final byte[] beginExtCopy = writeExtCopy(beginExt);

        final long routeId = channel.routeId();
        final long replyId = channel.targetId();
        final long sequence = channel.targetSeq();
        final long acknowledge = channel.targetAck();
        final int maximum = channel.targetMax();
        final long affinity = channel.getConfig().getAffinity();

        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(replyId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .timestamp(supplyTimestamp.getAsLong())
                .traceId(supplyTraceId.getAsLong())
                .affinity(affinity)
                .extension(p -> p.set(beginExtCopy))
                .build();

        streamsBuffer.write(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        beginExt.skipBytes(writableExtBytes);
        beginExt.discardReadBytes();

        channel.beginOutputFuture().setSuccess();
    }

    public void doWrite(
        ZillaChannel channel,
        MessageEvent newWriteRequest)
    {
        doFlushBegin(channel);

        channel.writeRequests.addLast(newWriteRequest);

        flushThrottledWrites(channel);
    }

    public void doFlush(
        ZillaChannel channel,
        ChannelFuture flushFuture)
    {
        doFlushBegin(channel);
        if (channel.writeExtBuffer(DATA, true).readable())
        {
            if (channel.writeRequests.isEmpty())
            {
                Object message = NULL_BUFFER;
                MessageEvent newWriteRequest = new DownstreamMessageEvent(channel, flushFuture, message, null);
                channel.writeRequests.addLast(newWriteRequest);
            }
            flushThrottledWrites(channel);
        }
        else
        {
            flushThrottledWrites(channel);
            flushFuture.setSuccess();
            fireFlushed(channel);
        }
    }

    public void doSystemFlush(
        ZillaChannel channel,
        ChannelFuture flushFuture)
    {
        if (channel.beginOutputFuture().isDone())
        {
            flushThrottledWrites(channel);
        }
        flushFuture.setSuccess();
    }

    public void doAdviseOutput(
        ZillaChannel channel,
        ChannelFuture adviseFuture,
        Object value)
    {
        if (value == ADVISORY_FLUSH)
        {
            doAdviseOutputFlush(channel, adviseFuture);
        }
        else
        {
            adviseFuture.setFailure(new ChannelException("unexpected: " + value));
        }
    }

    private void doAdviseOutputFlush(
        ZillaChannel channel,
        ChannelFuture adviseFuture)
    {
        final long routeId = channel.routeId();
        final long streamId = channel.targetId();
        final long sequence = channel.targetSeq();
        final long acknowledge = channel.targetAck();
        final long authorization = channel.targetAuth();
        final int maximum = channel.targetMax();
        final long budgetId = channel.debitorId();

        final ChannelBuffer writeExt = channel.writeExtBuffer(FLUSH, true);
        final byte[] writeExtCopy = writeExtCopy(writeExt);

        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .timestamp(supplyTimestamp.getAsLong())
                .traceId(supplyTraceId.getAsLong())
                .authorization(authorization)
                .budgetId(budgetId)
                .extension(ex -> ex.set(writeExtCopy))
                .build();

        streamsBuffer.write(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());

        adviseFuture.setSuccess();
    }

    public void doAbortOutput(
        ZillaChannel channel,
        ChannelFuture abortFuture)
    {
        doFlushBegin(channel);

        final long routeId = channel.routeId();
        final long streamId = channel.targetId();
        final long sequence = channel.targetSeq();
        final long acknowledge = channel.targetAck();
        final long authorization = channel.targetAuth();
        final int maximum = channel.targetMax();

        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .timestamp(supplyTimestamp.getAsLong())
                .traceId(supplyTraceId.getAsLong())
                .authorization(authorization)
                .build();

        streamsBuffer.write(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());

        unregisterThrottle.accept(streamId);

        abortFuture.setSuccess();

        if (channel.setWriteAborted())
        {
            if (channel.setWriteClosed())
            {
                fireChannelDisconnected(channel);
                fireChannelUnbound(channel);
                fireChannelClosed(channel);
            }
        }
    }

    public void doShutdownOutput(
        ZillaChannel channel,
        ChannelFuture handlerFuture)
    {
        doFlushBegin(channel);

        final long routeId = channel.routeId();
        final long streamId = channel.targetId();
        final long sequence = channel.targetSeq();
        final long acknowledge = channel.targetAck();
        final ChannelBuffer endExt = channel.writeExtBuffer(END, true);
        final int writableExtBytes = endExt.readableBytes();
        final byte[] endExtCopy = writeExtCopy(endExt);
        final long authorization = channel.targetAuth();
        final int maximum = channel.targetMax();

        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .timestamp(supplyTimestamp.getAsLong())
                .traceId(supplyTraceId.getAsLong())
                .authorization(authorization)
                .extension(p -> p.set(endExtCopy))
                .build();

        streamsBuffer.write(end.typeId(), end.buffer(), end.offset(), end.sizeof());

        endExt.skipBytes(writableExtBytes);
        endExt.discardReadBytes();

        unregisterThrottle.accept(streamId);

        fireOutputShutdown(channel);
        handlerFuture.setSuccess();

        if (channel.setWriteClosed())
        {
            fireChannelDisconnected(channel);
            fireChannelUnbound(channel);
            fireChannelClosed(channel);
        }
    }

    public void doClose(
        ZillaChannel channel,
        ChannelFuture handlerFuture)
    {
        doFlushBegin(channel);

        final long routeId = channel.routeId();
        final long streamId = channel.targetId();
        final long sequence = channel.targetSeq();
        final long acknowledge = channel.targetAck();
        final ChannelBuffer endExt = channel.writeExtBuffer(END, true);
        final int writableExtBytes = endExt.readableBytes();
        final byte[] endExtCopy = writeExtCopy(endExt);

        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(channel.targetMax())
                .timestamp(supplyTimestamp.getAsLong())
                .traceId(supplyTraceId.getAsLong())
                .authorization(channel.targetAuth())
                .extension(p -> p.set(endExtCopy))
                .build();

        streamsBuffer.write(end.typeId(), end.buffer(), end.offset(), end.sizeof());

        endExt.skipBytes(writableExtBytes);
        endExt.discardReadBytes();

        unregisterThrottle.accept(streamId);

        handlerFuture.setSuccess();

        if (channel.setClosed())
        {
            fireChannelDisconnected(channel);
            fireChannelUnbound(channel);
            fireChannelClosed(channel);
        }
    }

    private boolean doFlushBegin(
        ZillaChannel channel)
    {
        final boolean doFlush = !channel.beginOutputFuture().isDone();

        if (doFlush)
        {
            // SERVER, HALF_DUPLEX
            doBeginReply(channel);
        }

        return doFlush;
    }

    private void flushThrottledWrites(
        ZillaChannel channel)
    {
        final Deque<MessageEvent> writeRequests = channel.writeRequests;

        loop:
        while (channel.isFlushable() && !writeRequests.isEmpty())
        {
            MessageEvent writeRequest = writeRequests.peekFirst();
            ChannelBuffer writeBuf = (ChannelBuffer) writeRequest.getMessage();

            ChannelBuffer writeExt = channel.writeExtBuffer(DATA, true);
            if (writeBuf.readable() || writeExt.readable() || writeBuf == EMPTY_BUFFER)
            {
                boolean flushed = flushData(channel, writeBuf, writeExt);
                if (!flushed)
                {
                    break loop;
                }
            }
            else if (channel.isTargetWriteRequestInProgress())
            {
                break loop;
            }
        }
    }

    private boolean flushData(
        ZillaChannel channel,
        ChannelBuffer writeBuf,
        ChannelBuffer writeExt)
    {
        final long authorization = channel.targetAuth();
        final boolean flushing = writeBuf == NULL_BUFFER;
        final int writableWin = channel.writableBytes();
        final int reservableBytes = Math.min(writeBuf.readableBytes(), writeBuffer.capacity() >> 1);
        final int reservedBytes = writableWin > 0 || writeBuf.capacity() == 0 ? channel.reservedBytes(reservableBytes) : 0;
        final int writableBytes = Math.max(Math.min(reservedBytes - channel.targetPad(), writeBuf.readableBytes()), 0);

        // allow extension-only DATA frames to be flushed immediately
        boolean flushable = writableBytes > 0 || writeBuf.capacity() == 0;
        if (flushable)
        {
            final int writeReaderIndex = writeBuf.readerIndex();

            if (writeReaderIndex == 0)
            {
                channel.targetWriteRequestProgressing();
            }

            final int writableExtBytes = writeExt.readableBytes();
            final byte[] writeExtCopy = writeExtCopy(writeExt);

            // extension-only DATA frames should have null payload (default)
            OctetsFW writeCopy = null;
            if (writeBuf != NULL_BUFFER)
            {
                // TODO: avoid allocation
                byte[] writeCopyBytes = new byte[writableBytes];
                writeBuf.getBytes(writeReaderIndex, writeCopyBytes);
                writeCopy = octetsRO.wrap(new UnsafeBuffer(writeCopyBytes), 0, writableBytes);
            }

            int optionFlags = channel.writeFlags();

            int flags = 0;

            if (optionFlags != -1)
            {
                flags = optionFlags;
            }
            else
            {
                if (writableBytes == writeBuf.readableBytes())
                {
                    flags |= 0x01;  // FIN
                }

                if (writeReaderIndex == 0)
                {
                    flags |= 0x02;  // INIT
                }
            }

            final long streamId = channel.targetId();
            final long routeId = channel.routeId();
            final long sequence = channel.targetSeq();
            final long acknowledge = channel.targetAck();
            final int maximum = channel.targetMax();
            final long budgetId = channel.debitorId();

            final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .routeId(routeId)
                    .streamId(streamId)
                    .sequence(sequence)
                    .acknowledge(acknowledge)
                    .maximum(maximum)
                    .timestamp(supplyTimestamp.getAsLong())
                    .traceId(supplyTraceId.getAsLong())
                    .authorization(authorization)
                    .flags(flags)
                    .budgetId(budgetId)
                    .reserved(reservedBytes)
                    .payload(writeCopy)
                    .extension(p -> p.set(writeExtCopy))
                    .build();

            flushable = streamsBuffer.write(data.typeId(), data.buffer(), data.offset(), data.sizeof());

            if (flushable)
            {
                channel.writtenBytes(writableBytes, reservedBytes);

                writeBuf.skipBytes(writableBytes);

                writeExt.skipBytes(writableExtBytes);
                writeExt.discardReadBytes();
            }
        }

        if (flushing)
        {
            fireFlushed(channel);
        }
        else if (flushable)
        {
            fireWriteComplete(channel, writableBytes);
        }

        channel.targetWriteRequestProgress();

        return flushable;
    }

    private byte[] writeExtCopy(
        ChannelBuffer writeExt)
    {
        final int writableExtBytes = writeExt.readableBytes();
        final byte[] writeExtArray = writeExt.array();
        final int writeExtArrayOffset = writeExt.arrayOffset();
        final int writeExtReaderIndex = writeExt.readerIndex();

        // TODO: avoid allocation
        final byte[] writeExtCopy = new byte[writableExtBytes];
        System.arraycopy(writeExtArray, writeExtArrayOffset + writeExtReaderIndex, writeExtCopy, 0, writeExtCopy.length);
        return writeExtCopy;
    }

    void doWindow(
        final ZillaChannel channel)
    {
        final long routeId = channel.routeId();
        final long streamId = channel.sourceId();
        final long sequence = channel.sourceSeq();
        final long acknowledge = channel.sourceAck();
        final long budgetId = channel.creditorId();
        final int padding = channel.getConfig().getPadding();
        final int maximum = channel.getConfig().getWindow();
        final int minimum = 0; // TODO
        final byte capabilities = channel.getConfig().getCapabilities();

        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .timestamp(supplyTimestamp.getAsLong())
                .traceId(supplyTraceId.getAsLong())
                .budgetId(budgetId)
                .padding(padding)
                .minimum(minimum)
                .capabilities(capabilities)
                .build();

        streamsBuffer.write(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    void doReset(
        final ZillaChannel channel,
        final long traceId)
    {
        final long routeId = channel.routeId();
        final long streamId = channel.sourceId();
        final long sequence = channel.sourceSeq();
        final long acknowledge = channel.sourceAck();
        final int maximum = channel.sourceMax();

        doReset(routeId, streamId, sequence, acknowledge, traceId, maximum);
    }

    void doReset(
        final long routeId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final long traceId,
        final int maximum)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .timestamp(supplyTimestamp.getAsLong())
                .traceId(traceId)
                .build();

        streamsBuffer.write(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    void doChallenge(
        final long routeId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final long traceId,
        final int maximum,
        final ChannelBuffer extension)
    {
        final byte[] extensionCopy = writeExtCopy(extension);

        final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .timestamp(supplyTimestamp.getAsLong())
                .traceId(traceId)
                .extension(p -> p.set(extensionCopy))
                .build();

        streamsBuffer.write(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
    }

    private final class Throttle
    {
        private final ZillaChannel channel;
        private final ChannelFuture windowFuture;
        private final ChannelFuture handshakeFuture;

        private Consumer<WindowFW> windowHandler;
        private Consumer<ResetFW> resetHandler;

        private Throttle(
            ZillaChannel channel,
            ChannelFuture windowFuture,
            ChannelFuture handshakeFuture)
        {
            this.channel = channel;
            this.windowHandler = this::onWindowBeforeWritable;
            this.windowFuture = windowFuture;
            this.handshakeFuture = handshakeFuture;

            boolean isChildChannel = channel.getParent() != null;
            boolean isHalfDuplex = channel.getConfig().getTransmission() == ZillaTransmission.HALF_DUPLEX;

            this.resetHandler = isChildChannel && isHalfDuplex ? this::onReset : this::onResetBeforeHandshake;
            handshakeFuture.addListener(this::onHandshakeCompleted);
        }

        private void handleThrottle(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                resetHandler.accept(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                windowHandler.accept(window);
                break;
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onChallenge(challenge);
                break;
            default:
                throw new IllegalArgumentException("Unexpected message type: " + msgTypeId);
            }
        }

        private void onChallenge(
            ChallengeFW challenge)
        {
            final long acknowledge = challenge.acknowledge();
            final OctetsFW challengeExt = challenge.extension();

            channel.targetAck(acknowledge);

            int challengeExtBytes = challengeExt.sizeof();
            if (challengeExtBytes != 0)
            {
                final DirectBuffer buffer = challengeExt.buffer();
                final int offset = challengeExt.offset();

                // TODO: avoid allocation
                final byte[] challengeExtCopy = new byte[challengeExtBytes];
                buffer.getBytes(offset, challengeExtCopy);

                channel.readExtBuffer(CHALLENGE).writeBytes(challengeExtCopy);
            }

            fireOutputAdvised(channel, ADVISORY_CHALLENGE);
        }

        private void onWindow(
            WindowFW window)
        {
            final long acknowledge = window.acknowledge();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int maximum = window.maximum();
            final int minimum = window.minimum();
            final int capabilities = window.capabilities();

            if (budgetId != 0L && !channel.hasDebitor() &&
                !channel.isWriteClosed() && !channel.getCloseFuture().isSuccess())
            {
                DefaultBudgetDebitor debitor = channel.engine.supplyDebitor(channel, budgetId);
                channel.setDebitor(debitor, budgetId);
            }

            final long targetMax = channel.targetMax();
            channel.writableWindow(acknowledge, padding, minimum, maximum, traceId);
            channel.capabilities(capabilities);

            // window maximum reduced
            if (channel.targetMax() < targetMax)
            {
                final long routeId = channel.routeId();
                final long streamId = channel.targetId();
                final long sequence = channel.targetSeq();
                final long authorization = channel.targetAuth();

                final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                        .routeId(routeId)
                        .streamId(streamId)
                        .sequence(sequence)
                        .acknowledge(acknowledge)
                        .maximum(maximum)
                        .timestamp(supplyTimestamp.getAsLong())
                        .traceId(supplyTraceId.getAsLong())
                        .authorization(authorization)
                        .budgetId(budgetId)
                        .build();

                streamsBuffer.write(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
            }

            flushThrottledWrites(channel);
        }

        private void onReset(
            ResetFW reset)
        {
            final long streamId = reset.streamId();
            final long acknowledge = reset.acknowledge();

            channel.targetAck(acknowledge);

            unregisterThrottle.accept(streamId);

            if (channel.setWriteAborted())
            {
                if (channel.setWriteClosed())
                {
                    fireOutputAborted(channel);
                    fireChannelDisconnected(channel);
                    fireChannelUnbound(channel);
                    fireChannelClosed(channel);
                }
                else
                {
                    fireOutputAborted(channel);
                }
            }
        }

        private void onWindowBeforeWritable(
            WindowFW window)
        {
            this.windowHandler = this::onWindow;

            channel.setFlushable();

            windowFuture.setSuccess();
            windowHandler.accept(window);
        }

        private void onResetBeforeHandshake(
            ResetFW reset)
        {
            handshakeFuture.setFailure(new ChannelException("handshake failed"));
        }

        private void onHandshakeCompleted(
            ChannelFuture future)
        {
            this.resetHandler = this::onReset;

            if (!future.isSuccess())
            {
                final long streamId = channel.sourceId();
                final long routeId = channel.routeId();
                final long sequence = channel.sourceSeq();
                final long acknowledge = channel.sourceAck();
                final int maximum = channel.sourceMax();

                final ResetFW reset = resetRW.wrap(resetBuffer, 0, resetBuffer.capacity())
                        .routeId(routeId)
                        .streamId(streamId)
                        .sequence(sequence)
                        .acknowledge(acknowledge)
                        .maximum(maximum)
                        .timestamp(supplyTimestamp.getAsLong())
                        .traceId(supplyTraceId.getAsLong())
                        .build();

                resetHandler.accept(reset);
            }
        }
    }
}
