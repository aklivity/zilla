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

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;
import static java.net.StandardSocketOptions.TCP_NODELAY;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.nativeOrder;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;

/**
 * Drives a {@code tcp} server binding through the real engine. The engine accepts real loopback socket
 * connections made by this benchmark, and exposes each as an application stream to an {@code EngineBench}
 * external peer ({@link TcpBenchPeer}) polled on the benchmark thread, together with the socket side.
 * <ul>
 * <li>{@code transfer}: long-lived connections, one operation is one payload-sized message moved in the
 * configured direction (written to the socket, pushed by the peer, or echoed round trip). {@code window} is
 * the request credit the peer grants the engine.</li>
 * <li>{@code connect}: one operation is one whole connection lifecycle, opened and closed, with no data.
 * {@code connectBaseline} opens, accepts and closes the same loopback connections with no engine, which is
 * the cost of the sockets themselves.</li>
 * <li>{@code leave}: opens idle established connections and keeps them; read {@code retained.heap.norm}
 * (B/op) from {@link RetainedHeapProfiler}. {@code leaveBaseline} retains the same loopback connections
 * with no engine, so the difference is the binding and engine share of the cost.</li>
 * </ul>
 * Read {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler for {@code transfer} and
 * {@code connect}. The socket side and the peer run on the benchmark thread and their allocation is included.
 * {@code transferBaseline} runs the same socket side against a bare loopback listener thread in place of the
 * engine, which is the ceiling the socket side can drive.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class TcpServerBM
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/tcp/config/server.yaml";
    private static final int PORT = 12345;
    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;
    private static final int MESSAGES = 512;
    private static final int CHURN = 16;
    private static final int IDLE = 1_000;
    private static final long TIMEOUT_NANOS = SECONDS.toNanos(10L);

    @Benchmark
    @OperationsPerInvocation(MESSAGES)
    public void transfer(
        Transferring fixture)
    {
        fixture.transfer();
    }

    @Benchmark
    @OperationsPerInvocation(MESSAGES)
    public void transferBaseline(
        TransferringBaseline fixture)
    {
        fixture.transfer();
    }

    @Benchmark
    @OperationsPerInvocation(CHURN)
    public void connect(Churning fixture) throws IOException
    {
        fixture.connect();
    }

    @Benchmark
    @OperationsPerInvocation(CHURN)
    public void connectBaseline(
        ChurningBaseline fixture) throws IOException
    {
        fixture.connect();
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(MILLISECONDS)
    @Warmup(iterations = 1)
    @Measurement(iterations = 3)
    @OperationsPerInvocation(IDLE)
    public void leave(
        Idling fixture) throws IOException
    {
        fixture.leave(IDLE);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(MILLISECONDS)
    @Warmup(iterations = 1)
    @Measurement(iterations = 3)
    @OperationsPerInvocation(IDLE)
    public void leaveBaseline(
        IdlingBaseline fixture) throws IOException
    {
        fixture.leave(IDLE);
    }

    @State(Scope.Benchmark)
    public abstract static class Fixture
    {
        final InetSocketAddress address = new InetSocketAddress("127.0.0.1", PORT);

        EngineBench bench;
        TcpBenchPeer peer;

        void start(
            TcpBenchDirection direction,
            int chunk) throws Exception
        {
            start(direction, chunk, DEFAULT_WINDOW);
        }

        void start(
            TcpBenchDirection direction,
            int chunk,
            int window) throws Exception
        {
            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("app0")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .start(CONFIGURATION);

            peer = new TcpBenchPeer(direction, window, chunk);
            bench.newServer("app0", peer);
        }

        void stop() throws Exception
        {
            bench.close();
        }

        void awaitBegins(
            int begins)
        {
            await(() -> peer.counter().begins() >= begins, "begins " + begins);
        }

        void await(
            BooleanSupplier condition,
            String description)
        {
            if (!bench.pollUntil(condition))
            {
                throw new IllegalStateException("timed out awaiting " + description);
            }
        }
    }

    @State(Scope.Benchmark)
    public static class Transferring extends Fixture
    {
        @Param({"64", "1024", "16384", "65536"})
        public int payload;

        @Param({"INBOUND", "OUTBOUND", "ECHO"})
        public TcpBenchDirection direction;

        @Param({"1", "16"})
        public int connections;

        @Param({"32768"})
        public int window;

        private SocketChannel[] channels;
        private long[] sendRemaining;
        private ByteBuffer sendBuffer;
        private ByteBuffer receiveBuffer;
        private long sendPending;
        private long received;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            startPeer();

            final byte[] message = new byte[payload];
            new Random(0L).nextBytes(message);
            sendBuffer = allocateDirect(payload).order(nativeOrder());
            sendBuffer.put(message);
            receiveBuffer = allocateDirect(64 * 1024).order(nativeOrder());

            channels = new SocketChannel[connections];
            sendRemaining = new long[connections];

            for (int index = 0; index < connections; index++)
            {
                channels[index] = SocketChannel.open(address);
                channels[index].setOption(TCP_NODELAY, true);
                channels[index].configureBlocking(false);
            }

            awaitConnected(connections);
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            for (SocketChannel channel : channels)
            {
                channel.close();
            }

            stopPeer();
        }

        void startPeer() throws Exception
        {
            start(direction, payload, window);
        }

        void stopPeer() throws Exception
        {
            stop();
        }

        void awaitConnected(
            int count)
        {
            awaitBegins(count);
        }

        int pollPeer()
        {
            return bench.poll();
        }

        long peerReceived()
        {
            return peer.counter().dataBytes();
        }

        void peerSource(
            long bytes)
        {
            peer.source(bytes);
        }

        void transfer()
        {
            final long perConnection = (long) (MESSAGES / connections) * payload;
            final long total = perConnection * connections;
            final long baseline = peerReceived();
            final long deadline = System.nanoTime() + TIMEOUT_NANOS;

            Arrays.fill(sendRemaining, direction != TcpBenchDirection.OUTBOUND ? perConnection : 0L);
            sendPending = direction != TcpBenchDirection.OUTBOUND ? total : 0L;
            received = 0L;

            if (direction == TcpBenchDirection.OUTBOUND)
            {
                peerSource(perConnection);
            }

            while (sendPending > 0L || received < total)
            {
                int work = pollPeer();

                for (int index = 0; index < connections; index++)
                {
                    work += write(index);
                    work += direction != TcpBenchDirection.INBOUND ? read(index) : 0;
                }

                if (direction == TcpBenchDirection.INBOUND)
                {
                    received = peerReceived() - baseline;
                }

                if (work == 0)
                {
                    if (System.nanoTime() > deadline)
                    {
                        throw new IllegalStateException("transfer timed out");
                    }

                    Thread.onSpinWait();
                }
            }
        }

        private int write(
            int index)
        {
            int work = 0;

            if (sendRemaining[index] > 0L)
            {
                try
                {
                    sendBuffer.clear().limit((int) Math.min(payload, sendRemaining[index]));

                    final int bytesWritten = channels[index].write(sendBuffer);

                    sendRemaining[index] -= bytesWritten;
                    sendPending -= bytesWritten;
                    work = bytesWritten > 0 ? 1 : 0;
                }
                catch (IOException ex)
                {
                    throw new IllegalStateException(ex);
                }
            }

            return work;
        }

        private int read(
            int index)
        {
            int bytesRead;

            try
            {
                receiveBuffer.clear();
                bytesRead = channels[index].read(receiveBuffer);
            }
            catch (IOException ex)
            {
                throw new IllegalStateException(ex);
            }

            if (bytesRead < 0)
            {
                throw new IllegalStateException("connection closed by engine");
            }

            received += bytesRead;

            return bytesRead > 0 ? 1 : 0;
        }
    }

    @State(Scope.Benchmark)
    public static class TransferringBaseline extends Transferring
    {
        private TcpBenchListener listener;

        @Override
        void startPeer() throws Exception
        {
            listener = new TcpBenchListener(PORT, direction, payload);
            listener.start();
        }

        @Override
        void stopPeer() throws Exception
        {
            listener.close();
        }

        @Override
        void awaitConnected(
            int count)
        {
            while (listener.accepted() < count)
            {
                Thread.onSpinWait();
            }
        }

        @Override
        int pollPeer()
        {
            return 0;
        }

        @Override
        long peerReceived()
        {
            return listener.received();
        }

        @Override
        void peerSource(
            long bytes)
        {
            listener.source(bytes);
        }
    }

    @State(Scope.Benchmark)
    public static class Churning extends Fixture
    {
        @Param({"1", "16"})
        public int concurrency;

        private SocketChannel[] wave;
        private int beginTarget;
        private int endTarget;
        private BooleanSupplier begun;
        private BooleanSupplier ended;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            start(TcpBenchDirection.INBOUND, 1);

            wave = new SocketChannel[concurrency];
            begun = () -> peer.counter().begins() >= beginTarget;
            ended = () -> peer.counter().ends() >= endTarget;
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            stop();
        }

        void connect() throws IOException
        {
            for (int opened = 0; opened < CHURN; opened += concurrency)
            {
                beginTarget = peer.counter().begins() + concurrency;
                endTarget = peer.counter().ends() + concurrency;

                for (int index = 0; index < concurrency; index++)
                {
                    wave[index] = SocketChannel.open(address);
                }

                await(begun, "begins " + beginTarget);

                for (int index = 0; index < concurrency; index++)
                {
                    wave[index].close();
                }

                await(ended, "ends " + endTarget);
            }
        }
    }

    @State(Scope.Benchmark)
    public static class ChurningBaseline
    {
        private final InetSocketAddress address = new InetSocketAddress("127.0.0.1", PORT);

        private ServerSocketChannel server;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            server = ServerSocketChannel.open();
            server.bind(address, CHURN);
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            server.close();
        }

        void connect() throws IOException
        {
            for (int opened = 0; opened < CHURN; opened++)
            {
                final SocketChannel client = SocketChannel.open(address);
                final SocketChannel accepted = server.accept();

                client.close();
                accepted.close();
            }
        }
    }

    @State(Scope.Benchmark)
    public static class Idling extends Fixture
    {
        private final List<SocketChannel> channels = new ArrayList<>(IDLE * 4);

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            start(TcpBenchDirection.INBOUND, 1);
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            for (SocketChannel channel : channels)
            {
                channel.close();
            }

            stop();
        }

        void leave(
            int count) throws IOException
        {
            for (int index = 0; index < count; index++)
            {
                channels.add(SocketChannel.open(address));
            }

            awaitBegins(channels.size());
        }
    }

    @State(Scope.Benchmark)
    public static class IdlingBaseline
    {
        private final List<SocketChannel> channels = new ArrayList<>(IDLE * 8);
        private final InetSocketAddress address = new InetSocketAddress("127.0.0.1", PORT);

        private ServerSocketChannel server;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            server = ServerSocketChannel.open();
            server.bind(address, IDLE);
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            for (SocketChannel channel : channels)
            {
                channel.close();
            }

            server.close();
        }

        void leave(
            int count) throws IOException
        {
            for (int index = 0; index < count; index++)
            {
                channels.add(SocketChannel.open(address));
                channels.add(server.accept());
            }
        }
    }

    public static void main(
        String[] args) throws RunnerException
    {
        final Options opt = new OptionsBuilder()
            .include(TcpServerBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
