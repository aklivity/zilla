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
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
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

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.EngineBench.FrameCounter;
import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;

/**
 * Drives a {@code tcp} client binding through the real engine. Each {@code EngineBench} client opens an
 * application stream on the benchmark thread, and the engine connects out over loopback to a real server
 * socket ({@link TcpBenchListener}) served by its own thread.
 * <ul>
 * <li>{@code transfer}: long-lived connections, one operation is one payload-sized message moved in the
 * configured direction (sent by the application stream, pushed by the socket, or echoed round trip).</li>
 * <li>{@code connect}: one operation is one whole connection lifecycle, opened and closed, with no data.</li>
 * <li>{@code leave}: opens idle established connections and keeps them; read {@code retained.heap.norm}
 * (B/op) from {@link RetainedHeapProfiler}. {@code leaveBaseline} retains the same loopback connections
 * with no engine, so the difference is the binding and engine share of the cost.</li>
 * </ul>
 * Read {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler for {@code transfer} and
 * {@code connect}. The application-side client runs on the benchmark thread and its allocation is included;
 * the listener thread allocates nothing per operation but its reads and writes cost CPU.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class TcpClientBM
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/tcp/config/client.yaml";
    private static final int PORT = 12345;
    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;
    private static final int MESSAGES = 512;
    private static final int CHURN = 16;
    private static final int IDLE = 1_000;

    @Benchmark
    @OperationsPerInvocation(MESSAGES)
    public void transfer(
        Transferring fixture)
    {
        fixture.transfer();
    }

    @Benchmark
    @OperationsPerInvocation(CHURN)
    public void connect(
        Churning fixture)
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
        Idling fixture)
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
        final FrameCounter counter = new FrameCounter();
        final List<Client> clients = new ArrayList<>();

        EngineBench bench;
        TcpBenchListener listener;
        long routedId;

        void start(
            TcpBenchDirection direction,
            int chunk) throws Exception
        {
            listener = new TcpBenchListener(PORT, direction, chunk);
            listener.start();

            bench = new EngineBench()
                .directory("target/zilla-bench")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .start(CONFIGURATION);

            routedId = bench.bindingId("app0");
        }

        void stop() throws Exception
        {
            clients.forEach(Client::abort);
            bench.close();
            listener.close();
        }

        Client open()
        {
            final Client client = bench.newClient(routedId, (MessageConsumer) counter);

            client.begin();
            client.replyWindow(DEFAULT_WINDOW);

            return client;
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

        private UnsafeBufferEx message;
        private long target;
        private BooleanSupplier sent;
        private BooleanSupplier received;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            start(direction, payload);

            final byte[] bytes = new byte[payload];
            new Random(0L).nextBytes(bytes);
            message = new UnsafeBufferEx(bytes);

            sent = () -> listener.received() >= target;
            received = () -> counter.dataBytes() >= target;

            for (int index = 0; index < connections; index++)
            {
                clients.add(open());
            }

            await(() -> counter.begins() >= connections, "begins " + connections);
            await(() -> clients.stream().allMatch(client -> client.initialCredit() > 0), "initial credit");
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            stop();
        }

        void transfer()
        {
            final long perConnection = (long) (MESSAGES / connections) * payload;
            final long total = perConnection * connections;

            if (direction == TcpBenchDirection.INBOUND)
            {
                target = counter.dataBytes() + total;
                listener.source(perConnection);
            }
            else
            {
                target = (direction == TcpBenchDirection.OUTBOUND ? listener.received() : counter.dataBytes()) + total;

                for (int index = 0; index < MESSAGES; index++)
                {
                    clients.get(index % connections).send(message, 0, payload);
                }
            }

            await(direction == TcpBenchDirection.OUTBOUND ? sent : received, "transfer");
        }
    }

    @State(Scope.Benchmark)
    public static class Churning extends Fixture
    {
        @Param({"1", "16"})
        public int concurrency;

        private final Client[] wave = new Client[16];

        private int beginTarget;
        private int endTarget;
        private BooleanSupplier begun;
        private BooleanSupplier ended;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            start(TcpBenchDirection.INBOUND, 1);

            begun = () -> counter.begins() >= beginTarget;
            ended = () -> counter.ends() >= endTarget;
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            stop();
        }

        void connect()
        {
            for (int opened = 0; opened < CHURN; opened += concurrency)
            {
                beginTarget = counter.begins() + concurrency;
                endTarget = counter.ends() + concurrency;

                for (int index = 0; index < concurrency; index++)
                {
                    wave[index] = open();
                }

                await(begun, "begins " + beginTarget);

                for (int index = 0; index < concurrency; index++)
                {
                    wave[index].end();
                }

                await(ended, "ends " + endTarget);
            }
        }
    }

    @State(Scope.Benchmark)
    public static class Idling extends Fixture
    {
        @Setup(Level.Trial)
        public void init() throws Exception
        {
            start(TcpBenchDirection.INBOUND, 1);
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            stop();
        }

        void leave(
            int count)
        {
            for (int index = 0; index < count; index++)
            {
                clients.add(open());
            }

            final int begins = clients.size();
            await(() -> counter.begins() >= begins, "begins " + begins);
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
                final SocketChannel channel = SocketChannel.open(address);
                channel.setOption(TCP_NODELAY, true);
                channels.add(channel);
                channels.add(server.accept());
            }
        }
    }

    public static void main(
        String[] args) throws RunnerException
    {
        final Options opt = new OptionsBuilder()
            .include(TcpClientBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
