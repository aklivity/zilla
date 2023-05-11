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
package io.aklivity.zilla.runtime.binding.tcp.internal.stream;

import static io.aklivity.zilla.runtime.binding.tcp.internal.TcpBinding.WRITE_SPIN_COUNT;
import static io.aklivity.zilla.runtime.binding.tcp.internal.util.IpUtil.proxyAddress;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.net.StandardSocketOptions.SO_KEEPALIVE;
import static java.net.StandardSocketOptions.TCP_NODELAY;
import static java.nio.ByteOrder.nativeOrder;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.tcp.internal.TcpConfiguration;
import io.aklivity.zilla.runtime.binding.tcp.internal.config.TcpBindingConfig;
import io.aklivity.zilla.runtime.binding.tcp.internal.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tcp.internal.config.TcpRouteConfig;
import io.aklivity.zilla.runtime.binding.tcp.internal.config.TcpServerBindingConfig;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.poller.PollerKey;

public class TcpServerFactory implements TcpStreamFactory
{
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();

    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();

    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();

    private final ProxyBeginExFW.Builder beginExRW = new ProxyBeginExFW.Builder();

    private final EngineContext context;
    private final TcpServerRouter router;

    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final Function<SelectableChannel, PollerKey> supplyPollerKey;

    private final BufferPool bufferPool;
    private final ByteBuffer readByteBuffer;
    private final MutableDirectBuffer readBuffer;
    private final MutableDirectBuffer writeBuffer;
    private final ByteBuffer writeByteBuffer;
    private final int replyMax;
    private final int windowThreshold;
    private final int proxyTypeId;
    private final BindingHandler streamFactory;

    public TcpServerFactory(
        TcpConfiguration config,
        EngineContext context,
        LongFunction<TcpServerBindingConfig> servers)
    {
        this.context = context;
        this.router = new TcpServerRouter(config, context, this::handleAccept, servers);
        this.writeBuffer = context.writeBuffer();
        this.writeByteBuffer = ByteBuffer.allocateDirect(writeBuffer.capacity()).order(nativeOrder());
        this.bufferPool = context.bufferPool();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.supplyPollerKey = context::supplyPollerKey;
        this.streamFactory = context.streamFactory();
        this.proxyTypeId = context.supplyTypeId("proxy");

        final int readBufferSize = writeBuffer.capacity() - DataFW.FIELD_OFFSET_PAYLOAD;
        this.readByteBuffer = ByteBuffer.allocateDirect(readBufferSize).order(nativeOrder());
        this.readBuffer = new UnsafeBuffer(readByteBuffer);
        this.replyMax = bufferPool.slotCapacity();
        this.windowThreshold = (bufferPool.slotCapacity() * config.windowThreshold()) / 100;
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        return null;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        TcpBindingConfig tcpBinding = new TcpBindingConfig(binding);
        router.attach(tcpBinding);
    }

    @Override
    public void detach(
        long bindingId)
    {
        router.detach(bindingId);
    }

    private int handleAccept(
        PollerKey acceptKey)
    {
        try
        {
            TcpBindingConfig binding = (TcpBindingConfig) acceptKey.attachment();
            TcpOptionsConfig options = binding.options;

            ServerSocketChannel server = (ServerSocketChannel) acceptKey.channel();

            for (SocketChannel channel = router.accept(server); channel != null; channel = router.accept(server))
            {
                channel.configureBlocking(false);
                channel.setOption(TCP_NODELAY, options.nodelay);
                channel.setOption(SO_KEEPALIVE, options.keepalive);

                InetSocketAddress remote = (InetSocketAddress) channel.getRemoteAddress();

                onAccepted(binding, channel, remote);
            }
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return 1;
    }

    private void onAccepted(
        TcpBindingConfig binding,
        SocketChannel network,
        InetSocketAddress remote)
    {
        final TcpRouteConfig route = binding.resolve(remote);

        if (route != null)
        {
            final TcpServer server = new TcpServer(binding.id, route.id, network);
            server.onNetAccepted();
        }
        else
        {
            closeNet(network);
        }
    }

    private void closeNet(
        SocketChannel network)
    {
        router.close(network);
    }

    private final class TcpServer
    {
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final SocketChannel net;
        private final PollerKey key;

        private MessageConsumer app;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long initialBudgetId;
        private int initialPad;

        private long replySeq;
        private long replyAck;

        private int state;
        private int writeSlot = NO_SLOT;
        private int writeSlotOffset;
        private int bytesFlushed;

        private TcpServer(
            long originId,
            long routedId,
            SocketChannel net)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.net = net;
            this.key = supplyPollerKey.apply(net);
        }

        private void onNetAccepted()
        {
            try
            {
                key.handler(OP_READ, this::onNetReadable);
                key.handler(OP_WRITE, this::onNetWritable);

                context.initialOpened(originId);
                context.replyOpened(originId);

                doAppBegin();
            }
            catch (IOException ex)
            {
                cleanup(supplyTraceId.getAsLong());
            }
        }

        private int onNetReadable(
            PollerKey key)
        {
            assert initialMax > initialPad;

            final int limit = Math.min(initialMax - initialPad, readBuffer.capacity());

            ((Buffer) readByteBuffer).position(0);
            ((Buffer) readByteBuffer).limit(limit);

            try
            {
                final int bytesRead = net.read(readByteBuffer);

                if (bytesRead == -1)
                {
                    context.initialClosed(originId);

                    key.clear(OP_READ);
                    CloseHelper.close(net::shutdownInput);

                    doAppEnd(supplyTraceId.getAsLong());

                    if (net.socket().isOutputShutdown())
                    {
                        closeNet(net);
                    }
                }
                else if (bytesRead != 0)
                {
                    context.initialBytes(originId, bytesRead);

                    doAppData(readBuffer, 0, bytesRead);
                }
            }
            catch (IOException ex)
            {
                context.initialErrored(originId);
                cleanup(supplyTraceId.getAsLong());
            }

            return 1;
        }

        private int onNetWritable(
            PollerKey key)
        {
            if (writeSlot == NO_SLOT)
            {
                assert key == this.key;
                return 0;
            }
            else
            {
                assert writeSlot != NO_SLOT;

                long traceId = supplyTraceId.getAsLong();
                DirectBuffer buffer = bufferPool.buffer(writeSlot);
                ByteBuffer byteBuffer = bufferPool.byteBuffer(writeSlot);
                byteBuffer.limit(byteBuffer.position() + writeSlotOffset);

                return doNetWrite(buffer, 0, writeSlotOffset, byteBuffer, traceId);
            }
        }

        private int doNetWrite(
            DirectBuffer buffer,
            int offset,
            int length,
            ByteBuffer byteBuffer,
            long traceId)
        {
            int bytesWritten = 0;

            try
            {
                for (int i = WRITE_SPIN_COUNT; bytesWritten == 0 && i > 0; i--)
                {
                    bytesWritten = net.write(byteBuffer);
                }

                bytesFlushed += bytesWritten;

                if (bytesWritten > 0)
                {
                    context.replyBytes(originId, bytesWritten);
                }

                if (bytesWritten < length)
                {
                    if (writeSlot == NO_SLOT)
                    {
                        writeSlot = bufferPool.acquire(replyId);
                    }

                    if (writeSlot == NO_SLOT)
                    {
                        doAppReset(traceId);
                        cleanup(traceId);
                    }
                    else
                    {
                        final MutableDirectBuffer slotBuffer = bufferPool.buffer(writeSlot);
                        slotBuffer.putBytes(0, buffer, offset + bytesWritten, length - bytesWritten);
                        writeSlotOffset = length - bytesWritten;

                        key.register(OP_WRITE);
                    }
                }
                else
                {
                    cleanupWriteSlot();
                    key.clear(OP_WRITE);

                    if (TcpState.replyClosing(state))
                    {
                        doNetShutdownOutput(traceId);
                    }
                    else if (bytesFlushed >= windowThreshold)
                    {
                        replyAck += bytesFlushed;
                        doAppWindow(traceId);
                        bytesFlushed = 0;
                    }
                }
            }
            catch (IOException ex)
            {
                cleanup(traceId);
            }

            return bytesWritten;
        }

        private void doNetShutdownOutput(
            long traceId)
        {
            cleanupWriteSlot();

            try
            {
                context.replyClosed(originId);

                key.clear(OP_WRITE);
                net.shutdownOutput();
                state = TcpState.closeReply(state);

                if (net.socket().isInputShutdown())
                {
                    closeNet(net);
                }
            }
            catch (IOException ex)
            {
                context.replyErrored(originId);
                cleanup(traceId);
            }
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
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAppReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAppWindow(window);
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

            state = TcpState.openReply(state);

            doAppWindow(traceId);
        }

        private void onAppData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + data.reserved();

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                doAppReset(traceId);
                cleanup(traceId, true);
            }
            else
            {
                final OctetsFW payload = data.payload();

                DirectBuffer buffer = payload.buffer();
                int offset = payload.offset();
                int length = payload.sizeof();

                assert reserved == length;
                assert length > 0;

                ByteBuffer byteBuffer;

                if (writeSlot != NO_SLOT)
                {
                    final MutableDirectBuffer slotBuffer = bufferPool.buffer(writeSlot);
                    slotBuffer.putBytes(writeSlotOffset, buffer, offset, length);
                    writeSlotOffset += length;

                    final ByteBuffer slotByteBuffer = bufferPool.byteBuffer(writeSlot);
                    slotByteBuffer.limit(slotByteBuffer.position() + writeSlotOffset);

                    buffer = slotBuffer;
                    offset = 0;
                    length = writeSlotOffset;
                    byteBuffer = slotByteBuffer;
                }
                else
                {
                    writeByteBuffer.clear();
                    buffer.getBytes(offset, writeByteBuffer, length);
                    writeByteBuffer.flip();
                    byteBuffer = writeByteBuffer;
                }

                doNetWrite(buffer, offset, length, byteBuffer, traceId);
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

            state = TcpState.closingReply(state);

            if (writeSlot == NO_SLOT)
            {
                doNetShutdownOutput(traceId);
            }
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

            doNetShutdownOutput(traceId);
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

            state = TcpState.closeInitial(state);
            CloseHelper.quietClose(net::shutdownInput);

            final boolean abortiveRelease = !TcpState.replyOpened(state);

            cleanup(traceId, abortiveRelease);
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final long budgetId = window.budgetId();
            final int maximum = window.maximum();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;

            initialAck = acknowledge;
            initialMax = maximum;
            initialBudgetId = budgetId;
            initialPad = padding;

            assert initialAck <= initialSeq;


            state = TcpState.openInitial(state);

            if (initialSeq + initialPad < initialAck + initialMax)
            {
                onNetReadable(key);
            }
            else
            {
                key.clear(OP_READ);
            }

            if (initialSeq + initialPad < initialAck + initialMax && !TcpState.initialClosed(state))
            {
                key.register(OP_READ);
            }
        }

        private void doAppBegin() throws IOException
        {
            final long traceId = supplyTraceId.getAsLong();
            final InetSocketAddress localAddress = (InetSocketAddress) net.getLocalAddress();
            final InetSocketAddress remoteAddress = (InetSocketAddress) net.getRemoteAddress();

            app = newStream(this::onAppMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, localAddress, remoteAddress);
            state = TcpState.openingInitial(state);
        }

        private void doAppData(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            final long traceId = supplyTraceId.getAsLong();
            final int reserved = length + initialPad;

            doData(app, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
                    initialBudgetId, reserved, buffer, offset, length);

            initialSeq += reserved;

            if (initialSeq + initialPad >= initialAck + initialMax)
            {
                key.clear(OP_READ);
            }
        }

        private void doAppEnd(
            long traceId)
        {
            doEnd(app, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
            state = TcpState.closeInitial(state);
        }

        private void doAppWindow(
            long traceId)
        {
            doWindow(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, 0, 0);
        }

        private void doAppReset(
            long traceId)
        {
            if (!TcpState.replyClosing(state))
            {
                doReset(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId);
                state = TcpState.closeReply(state);
            }
        }

        private void doAppAbort(
            long traceId)
        {
            if (TcpState.initialOpened(state) && !TcpState.initialClosed(state))
            {
                doAbort(app, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
                state = TcpState.closeInitial(state);
            }
        }

        private void cleanup(
            long traceId,
            boolean abortiveRelease)
        {
            if (abortiveRelease)
            {
                try
                {
                    // forces TCP RST
                    net.setOption(StandardSocketOptions.SO_LINGER, 0);
                }
                catch (IOException ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                }
            }

            cleanup(traceId);
        }

        private void cleanup(
            long traceId)
        {
            doAppAbort(traceId);
            doAppReset(traceId);

            cleanupWriteSlot();

            closeNet(net);
        }

        private void cleanupWriteSlot()
        {
            if (writeSlot != NO_SLOT)
            {
                bufferPool.release(writeSlot);
                writeSlot = NO_SLOT;
                writeSlotOffset = 0;
            }
        }
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
        InetSocketAddress localAddress,
        InetSocketAddress remoteAddress)
    {
        BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .affinity(streamId)
                .extension(b -> b.set(proxyBeginEx(remoteAddress, localAddress)))
                .build();

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        assert receiver != null;

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private void doData(
        MessageConsumer stream,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long budgetId,
        int reserved,
        DirectBuffer payload,
        int offset,
        int length)
    {
        DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .budgetId(budgetId)
                .reserved(reserved)
                .payload(payload, offset, length)
                .build();

        stream.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
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
        long traceId)
    {
        AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doReset(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
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
        int budgetId,
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
                .budgetId(budgetId)
                .padding(padding)
                .build();

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private Flyweight.Builder.Visitor proxyBeginEx(
        InetSocketAddress source,
        InetSocketAddress destination)
    {
        return (buffer, offset, limit) ->
            beginExRW.wrap(buffer, offset, limit)
                     .typeId(proxyTypeId)
                     .address(a -> proxyAddress(a, source, destination))
                     .build()
                     .sizeof();
    }
}
