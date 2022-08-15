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

import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.NullChannelBuffer.NULL_BUFFER;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaExtensionKind.BEGIN;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaExtensionKind.CHALLENGE;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaExtensionKind.DATA;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaExtensionKind.END;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaExtensionKind.FLUSH;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.ZillaTypeSystem.ADVISORY_FLUSH;
import static org.jboss.netty.channel.Channels.fireChannelClosed;
import static org.jboss.netty.channel.Channels.fireChannelDisconnected;
import static org.jboss.netty.channel.Channels.fireChannelUnbound;
import static org.jboss.netty.channel.Channels.fireExceptionCaught;
import static org.jboss.netty.channel.Channels.fireMessageReceived;
import static org.kaazing.k3po.driver.internal.netty.channel.Channels.fireInputAborted;
import static org.kaazing.k3po.driver.internal.netty.channel.Channels.fireInputAdvised;
import static org.kaazing.k3po.driver.internal.netty.channel.Channels.fireInputShutdown;

import java.util.function.LongConsumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.MessageHandler;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFuture;

import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.OctetsFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.FrameFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.util.function.LongLongFunction;

public final class ZillaStreamFactory
{
    private final FrameFW frameRO = new FrameFW();
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();

    private final LongLongFunction<ZillaTarget> supplySender;
    private final LongConsumer unregisterStream;

    public ZillaStreamFactory(
        LongLongFunction<ZillaTarget> supplySender,
        LongConsumer unregisterStream)
    {
        this.supplySender = supplySender;
        this.unregisterStream = unregisterStream;
    }

    public void doAbortInput(
            ZillaChannel channel,
            long traceId)
    {
        final long routeId = channel.routeId();
        final long streamId = channel.sourceId();
        final ZillaTarget sender = supplySender.apply(routeId, streamId);

        sender.doAbortInput(channel, traceId);
        unregisterStream.accept(streamId);
    }

    public void doReset(
        ZillaChannel channel,
        long traceId)
    {
        final long routeId = channel.routeId();
        final long streamId = channel.sourceId();
        final ZillaTarget sender = supplySender.apply(routeId, streamId);

        sender.doReset(channel, traceId);
        unregisterStream.accept(streamId);
    }

    public void doChallenge(
        ZillaChannel channel,
        long traceId)
    {
        final ChannelBuffer challengeExt = CHALLENGE.encodeBuffer(channel);

        final long routeId = channel.routeId();
        final long streamId = channel.sourceId();
        final long sequence = channel.sourceSeq();
        final long acknowledge = channel.sourceAck();
        final int maximum = channel.sourceMax();

        final ZillaTarget sender = supplySender.apply(routeId, streamId);
        sender.doChallenge(routeId, streamId, sequence, acknowledge, traceId, maximum, challengeExt);
    }

    public MessageHandler newStream(
        ZillaChannel channel,
        ZillaTarget sender,
        ChannelFuture beginFuture)
    {
        return new Stream(channel, sender, beginFuture)::handleStream;
    }

    private final class Stream
    {
        private final ZillaChannel channel;
        private final ZillaTarget sender;
        private final ChannelFuture beginFuture;
        private int fragments;

        private Stream(
            ZillaChannel channel,
            ZillaTarget sender,
            ChannelFuture beginFuture)
        {
            this.channel = channel;
            this.sender = sender;
            this.beginFuture = beginFuture;
        }

        private void handleStream(
            int msgTypeId,
            MutableDirectBuffer buffer,
            int index,
            int length)
        {
            final FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final long routeId = frame.routeId();
            verifyRouteId(routeId);

            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onBegin(begin);
                break;
            case DataFW.TYPE_ID:
                DataFW data = dataRO.wrap(buffer, index, index + length);
                onData(data);
                break;
            case EndFW.TYPE_ID:
                EndFW end = endRO.wrap(buffer, index, index + length);
                onEnd(end);
                break;
            case AbortFW.TYPE_ID:
                AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onFlush(flush);
                break;
            }
        }

        private void onBegin(
            BeginFW begin)
        {
            final long streamId = begin.streamId();
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final OctetsFW beginExt = begin.extension();

            int beginExtBytes = beginExt.sizeof();
            if (beginExtBytes != 0)
            {
                final DirectBuffer buffer = beginExt.buffer();
                final int offset = beginExt.offset();

                // TODO: avoid allocation
                final byte[] beginExtCopy = new byte[beginExtBytes];
                buffer.getBytes(offset, beginExtCopy);

                BEGIN.decodeBuffer(channel).writeBytes(beginExtCopy);
            }

            channel.sourceSeq(sequence);
            channel.sourceAck(acknowledge);
            channel.sourceId(streamId);
            channel.sourceAuth(begin.authorization());

            final ZillaChannelConfig config = channel.getConfig();
            if (config.getUpdate() == ZillaUpdateMode.HANDSHAKE ||
                config.getUpdate() == ZillaUpdateMode.STREAM)
            {
                sender.doWindow(channel);
            }

            channel.beginInputFuture().setSuccess();

            beginFuture.setSuccess();
        }

        private void onData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long traceId = data.traceId();
            final int flags = data.flags();
            final int reservedBytes = data.reserved();
            final OctetsFW payload = data.payload();
            final ChannelBuffer message = payload == null ? NULL_BUFFER : payload.get(this::readBuffer);
            final int readableBytes = message.readableBytes();
            final OctetsFW dataExt = data.extension();

            assert sequence >= channel.sourceSeq();

            if (channel.paddedBytes(readableBytes) <= reservedBytes && reservedBytes <= channel.readableBudget())
            {
                channel.readBytes(sequence, reservedBytes);
                channel.readFlags(flags);

                int dataExtBytes = dataExt.sizeof();
                if (dataExtBytes != 0)
                {
                    final DirectBuffer buffer = dataExt.buffer();
                    final int offset = dataExt.offset();

                    // TODO: avoid allocation
                    final byte[] dataExtCopy = new byte[dataExtBytes];
                    buffer.getBytes(offset, dataExtCopy);

                    DATA.decodeBuffer(channel).writeBytes(dataExtCopy);
                }

                if ((flags & 0x02) != 0x00 && fragments != 0)
                {
                    // INIT flag set on non-initial message fragment
                    fireExceptionCaught(channel, new IllegalStateException("invalid message boundary"));
                    sender.doReset(channel, traceId);
                }
                else
                {
                    final ZillaChannelConfig config = channel.getConfig();
                    if (config.getUpdate() == ZillaUpdateMode.MESSAGE ||
                        config.getUpdate() == ZillaUpdateMode.STREAM ||
                        config.getUpdate() == ZillaUpdateMode.PROACTIVE)
                    {
                        channel.acknowledgeBytes(reservedBytes);
                        channel.doSharedCredit(traceId, reservedBytes);
                        sender.doWindow(channel);
                    }
                    else
                    {
                        channel.pendingSharedCredit(reservedBytes);
                    }

                    if ((flags & 0x01) != 0x00 || (flags & 0x04) != 0x00)
                    {
                        message.markWriterIndex(); // FIN | INCOMPLETE
                        fragments = 0;
                    }
                    else
                    {
                        fragments++;
                    }

                    fireMessageReceived(channel, message);
                }
            }
            else
            {
                sender.doReset(channel, traceId);

                if (channel.setReadAborted())
                {
                    if (channel.setReadClosed())
                    {
                        fireInputAborted(channel);
                        fireChannelDisconnected(channel);
                        fireChannelUnbound(channel);
                        fireChannelClosed(channel);
                    }
                    else
                    {
                        fireInputAborted(channel);
                    }
                }
            }
        }

        private void onEnd(
            EndFW end)
        {
            final long streamId = end.streamId();
            final long sequence = end.sequence();
            final long traceId = end.traceId();

            channel.sourceSeq(sequence);

            if (end.authorization() != channel.sourceAuth())
            {
                sender.doReset(channel, traceId);
            }
            unregisterStream.accept(streamId);

            final OctetsFW endExt = end.extension();

            int endExtBytes = endExt.sizeof();
            if (endExtBytes != 0)
            {
                final DirectBuffer buffer = endExt.buffer();
                final int offset = endExt.offset();

                // TODO: avoid allocation
                final byte[] endExtCopy = new byte[endExtBytes];
                buffer.getBytes(offset, endExtCopy);

                END.decodeBuffer(channel).writeBytes(endExtCopy);
            }

            if (channel.setReadClosed())
            {
                fireInputShutdown(channel);
                fireChannelDisconnected(channel);
                fireChannelUnbound(channel);
                fireChannelClosed(channel);
            }
            else
            {
                fireInputShutdown(channel);
            }
        }

        private void onAbort(
            AbortFW abort)
        {
            final long streamId = abort.streamId();
            final long sequence = abort.sequence();
            final long traceId = abort.traceId();

            channel.sourceSeq(sequence);

            if (abort.authorization() != channel.sourceAuth())
            {
                sender.doReset(channel, traceId);
            }
            unregisterStream.accept(streamId);

            if (channel.setReadAborted())
            {
                if (channel.setReadClosed())
                {
                    fireInputAborted(channel);
                    fireChannelDisconnected(channel);
                    fireChannelUnbound(channel);
                    fireChannelClosed(channel);
                }
                else
                {
                    fireInputAborted(channel);
                }
            }
        }

        private void onFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long traceId = flush.traceId();

            channel.sourceSeq(sequence);

            if (flush.authorization() != channel.sourceAuth())
            {
                sender.doReset(channel, traceId);
            }

            final OctetsFW flushExt = flush.extension();

            int flushExtBytes = flushExt.sizeof();
            if (flushExtBytes != 0)
            {
                final DirectBuffer buffer = flushExt.buffer();
                final int offset = flushExt.offset();

                // TODO: avoid allocation
                final byte[] flushExtCopy = new byte[flushExtBytes];
                buffer.getBytes(offset, flushExtCopy);

                FLUSH.decodeBuffer(channel).writeBytes(flushExtCopy);
            }

            fireInputAdvised(channel, ADVISORY_FLUSH);
        }

        private void verifyRouteId(
            final long routeId)
        {
            if (routeId != channel.routeId())
            {
                throw new IllegalStateException(String.format("routeId: expected %x actual %x", channel.routeId(), routeId));
            }
        }

        private ChannelBuffer readBuffer(
            DirectBuffer buffer,
            int index,
            int maxLimit)
        {
            // TODO: avoid allocation
            final byte[] array = new byte[maxLimit - index];
            buffer.getBytes(index, array);
            return channel.getConfig().getBufferFactory().getBuffer(array, 0, array.length);
        }
    }
}
