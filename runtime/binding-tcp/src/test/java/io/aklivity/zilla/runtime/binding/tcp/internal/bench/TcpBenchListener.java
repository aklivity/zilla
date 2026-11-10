/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tcp.internal.bench;

import static java.net.StandardSocketOptions.TCP_NODELAY;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.nativeOrder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real loopback server socket that a {@code tcp} client binding connects out to, served by one busy-polling
 * thread over non-blocking channels, with a second thread blocked in accept because every accept call
 * allocates, even when no connection is pending. It reads into a reused direct buffer and, depending on the
 * direction, discards the bytes ({@code INBOUND}), writes the bytes it is asked to with
 * {@link #source(long)} ({@code OUTBOUND}), or writes back every byte it reads ({@code ECHO}). It does not
 * allocate per connection beyond the accepted channel, or per read and write.
 */
final class TcpBenchListener implements Runnable, AutoCloseable
{
    private final ServerSocketChannel server;
    private final TcpBenchDirection direction;
    private final ByteBuffer readBuffer;
    private final ByteBuffer sourceBuffer;
    private final Thread thread;
    private final Thread acceptor;
    private final Queue<SocketChannel> accepting = new ConcurrentLinkedQueue<>();
    private final List<Connection> connections = new ArrayList<>();
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong closed = new AtomicLong();
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong requested = new AtomicLong();

    private volatile boolean running = true;

    TcpBenchListener(
        int port,
        TcpBenchDirection direction,
        int chunk) throws IOException
    {
        final byte[] payload = new byte[chunk];
        new Random(0L).nextBytes(payload);

        this.direction = direction;
        this.readBuffer = allocateDirect(64 * 1024).order(nativeOrder());
        this.sourceBuffer = allocateDirect(chunk).order(nativeOrder());
        this.sourceBuffer.put(payload);

        this.server = ServerSocketChannel.open();
        this.server.bind(new InetSocketAddress(port), 4096);

        this.thread = new Thread(this, "tcp-bench-listener");
        this.thread.setDaemon(true);
        this.acceptor = new Thread(this::acceptConnections, "tcp-bench-acceptor");
        this.acceptor.setDaemon(true);
    }

    void start()
    {
        thread.start();
        acceptor.start();
    }

    long accepted()
    {
        return accepted.get();
    }

    long closed()
    {
        return closed.get();
    }

    long received()
    {
        return received.get();
    }

    void source(
        long bytes)
    {
        requested.addAndGet(bytes);
    }

    @Override
    public void run()
    {
        try
        {
            while (running)
            {
                int work = accept();

                final long request = requested.getAndSet(0L);

                for (int index = 0; index < connections.size(); index++)
                {
                    final Connection connection = connections.get(index);

                    connection.sourceRemaining += request;
                    work += serve(connection);

                    if (connection.channel == null)
                    {
                        final int last = connections.size() - 1;
                        connections.set(index, connections.get(last));
                        connections.remove(last);
                        index--;
                    }
                }

                if (work == 0)
                {
                    Thread.onSpinWait();
                }
            }
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void close() throws IOException, InterruptedException
    {
        running = false;
        server.close();
        acceptor.join();
        thread.join();

        for (Connection connection : connections)
        {
            connection.channel.close();
        }
    }

    private void acceptConnections()
    {
        try
        {
            while (running)
            {
                final SocketChannel channel = server.accept();

                channel.configureBlocking(false);
                channel.setOption(TCP_NODELAY, true);
                accepting.offer(channel);
            }
        }
        catch (AsynchronousCloseException ex)
        {
            // closed
        }
        catch (IOException ex)
        {
            if (running)
            {
                throw new UncheckedIOException(ex);
            }
        }
    }

    private int accept()
    {
        final SocketChannel channel = accepting.poll();

        if (channel != null)
        {
            connections.add(new Connection(channel));
            accepted.incrementAndGet();
        }

        return channel != null ? 1 : 0;
    }

    private int serve(
        Connection connection) throws IOException
    {
        int work = 0;

        readBuffer.clear();
        final int bytesRead = connection.channel.read(readBuffer);

        if (bytesRead == -1)
        {
            connection.channel.close();
            connection.channel = null;
            closed.incrementAndGet();
        }
        else
        {
            if (bytesRead > 0)
            {
                received.addAndGet(bytesRead);

                if (direction == TcpBenchDirection.ECHO)
                {
                    readBuffer.flip();
                    while (readBuffer.hasRemaining())
                    {
                        connection.channel.write(readBuffer);
                    }
                }

                work++;
            }

            if (connection.sourceRemaining > 0L)
            {
                sourceBuffer.clear().limit((int) Math.min(sourceBuffer.capacity(), connection.sourceRemaining));
                connection.sourceRemaining -= connection.channel.write(sourceBuffer);
                work++;
            }
        }

        return work;
    }

    private static final class Connection
    {
        private SocketChannel channel;
        private long sourceRemaining;

        private Connection(
            SocketChannel channel)
        {
            this.channel = channel;
        }
    }
}
