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

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CONFIG_URL;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.namespace.NamespacedId.NO_NAMESPACED_ID;
import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;
import static java.net.StandardSocketOptions.TCP_NODELAY;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.nativeOrder;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

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
import org.openjdk.jmh.infra.ThreadParams;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.runtime.binding.tcp.internal.TcpConfiguration;
import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
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
    private static final int RAMP = 2_000;
    private static final int RAMP_BATCH = 256;
    private static final int RAMP_PAYLOAD = 64;
    private static final int RAMP_WORKER_CAPACITY = 131_072;
    private static final long RAMP_TIMEOUT_NANOS = SECONDS.toNanos(60L);

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

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(MILLISECONDS)
    @Warmup(iterations = 3)
    @Measurement(iterations = 5)
    @OperationsPerInvocation(RAMP)
    public void ramp(
        Ramping engine,
        Rampers clients) throws IOException
    {
        clients.ramp(engine, RAMP);
    }

    @State(Scope.Benchmark)
    public static class Ramping
    {
        private static final Path DIRECTORY = Path.of("target/zilla-bench-ramp");

        @Param({"1", "2", "4"})
        public int workers;

        @Param({"2147483647"})
        public int backlog;

        private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        private final Map<String, Long> cpuBefore = new TreeMap<>();
        private final AtomicInteger entered = new AtomicInteger();
        private final AtomicInteger exited = new AtomicInteger();

        private Engine engine;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            deleteDirectory(DIRECTORY);
            Files.createDirectories(DIRECTORY);

            final Path config = DIRECTORY.resolve("zilla.yaml");
            Files.writeString(config, """
                ---
                name: test
                bindings:
                  net0:
                    type: tcp
                    kind: server
                    options:
                      host: 0.0.0.0
                      port: %d
                    exit: echo0
                  echo0:
                    type: echo
                    kind: server
                """.formatted(PORT));

            final Properties properties = new Properties();
            properties.setProperty(ENGINE_DIRECTORY.name(), DIRECTORY.resolve("engine").toString());
            properties.setProperty(ENGINE_CONFIG_URL.name(), config.toUri().toURL().toString());
            properties.setProperty(ENGINE_WORKERS.name(), Integer.toString(workers));
            properties.setProperty(TcpConfiguration.TCP_BACKLOG.name(), Integer.toString(backlog));
            properties.setProperty(ENGINE_WORKER_CAPACITY.name(), Integer.toString(RAMP_WORKER_CAPACITY));
            properties.setProperty(ENGINE_STREAMS_BUFFER_CAPACITY.name(), Integer.toString(STREAMS_BUFFER_CAPACITY));

            engine = Engine.builder()
                .config(new EngineConfiguration(properties))
                .errorHandler(Throwable::printStackTrace)
                .build();
            engine.start();
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            engine.close();
        }

        @Setup(Level.Iteration)
        public void reset()
        {
            entered.set(0);
            exited.set(0);
        }

        void enter()
        {
            if (entered.getAndIncrement() == 0)
            {
                cpuBefore.clear();
                cpuBefore.putAll(engineThreadCpuMillis());
            }
        }

        void exit(
            int clients)
        {
            if (exited.incrementAndGet() == clients)
            {
                final Map<String, Long> cpuAfter = engineThreadCpuMillis();
                final StringBuilder report = new StringBuilder("ramp workers=").append(workers)
                    .append(" backlog=").append(backlog)
                    .append(" cpu(ms)");
                cpuAfter.forEach((name, millis) ->
                    report.append(' ').append(name.substring("engine/".length()))
                          .append('=').append(millis - cpuBefore.getOrDefault(name, 0L)));
                System.out.println(report);
                System.out.println(usageReport());
            }
        }

        private String usageReport()
        {
            final long[] usage = workerUsage();
            final long total = Arrays.stream(usage).sum();
            final double mean = (double) total / usage.length;
            final long max = Arrays.stream(usage).max().orElse(0L);

            return "ramp usage workers=%d connections=%s total=%d max/mean=%.3f"
                .formatted(workers, Arrays.toString(usage), total, mean > 0 ? max / mean : 0.0);
        }

        private long[] workerUsage()
        {
            long[] usage;

            try
            {
                final Field workersField = Engine.class.getDeclaredField("workers");
                workersField.setAccessible(true);
                final List<?> workerList = (List<?>) workersField.get(engine);
                final int metricId = engine.supplyLabelId("engine.workers.usage");

                usage = new long[workerList.size()];
                for (int index = 0; index < usage.length; index++)
                {
                    final Object worker = workerList.get(index);
                    final Method supplyGauge = worker.getClass().getMethod("supplyGauge", long.class, int.class, int.class);
                    usage[index] = ((LongSupplier) supplyGauge.invoke(worker, NO_NAMESPACED_ID, metricId, 0)).getAsLong();
                }
            }
            catch (ReflectiveOperationException ex)
            {
                throw new IllegalStateException(ex);
            }

            return usage;
        }

        private Map<String, Long> engineThreadCpuMillis()
        {
            final Map<String, Long> cpuMillis = new TreeMap<>();
            Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("engine/"))
                .forEach(thread -> cpuMillis.put(thread.getName(),
                    threads.getThreadCpuTime(thread.threadId()) / 1_000_000L));
            return cpuMillis;
        }

        private static void deleteDirectory(
            Path directory) throws IOException
        {
            if (Files.exists(directory))
            {
                try (var paths = Files.walk(directory))
                {
                    paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
                }
            }
        }
    }

    @State(Scope.Thread)
    public static class Rampers
    {
        private static final int CONNECTING = 0;
        private static final int ECHOING = 1;
        private static final int ECHOED = 2;

        private final SocketChannel[] batch = new SocketChannel[RAMP_BATCH];
        private final int[] stages = new int[RAMP_BATCH];
        private final int[] echoed = new int[RAMP_BATCH];
        private final long[] starts = new long[RAMP_BATCH];
        private final long[] latencies = new long[RAMP];
        private final List<SocketChannel> held = new ArrayList<>();
        private final ByteBuffer request = allocateDirect(RAMP_PAYLOAD).order(nativeOrder());
        private final ByteBuffer reply = allocateDirect(RAMP_PAYLOAD).order(nativeOrder());

        private InetSocketAddress destination;
        private int clients;
        private int established;
        private int failed;
        private long elapsedNanos;

        @Setup(Level.Trial)
        public void init(
            ThreadParams threadParams)
        {
            destination = new InetSocketAddress("127.0.0." + (1 + threadParams.getThreadIndex()), PORT);
            clients = threadParams.getThreadCount();
            final byte[] message = new byte[RAMP_PAYLOAD];
            new Random(0L).nextBytes(message);
            request.put(message);
        }

        @Setup(Level.Iteration)
        public void reset() throws IOException, InterruptedException
        {
            for (SocketChannel channel : held)
            {
                channel.close();
            }

            held.clear();
            Thread.sleep(500L);
        }

        @TearDown(Level.Trial)
        public void destroy() throws IOException
        {
            for (SocketChannel channel : held)
            {
                channel.close();
            }
        }

        @TearDown(Level.Iteration)
        public void report()
        {
            final long[] sorted = Arrays.copyOf(latencies, established);
            Arrays.sort(sorted);

            final long p50 = established > 0 ? sorted[established / 2] / 1_000L : 0L;
            final long p99 = established > 0 ? sorted[Math.min(established - 1, established * 99 / 100)] / 1_000L : 0L;
            final long max = established > 0 ? sorted[established - 1] / 1_000L : 0L;

            System.out.printf("ramp client=%s established=%d failed=%d elapsed=%dms latency(us) p50=%d p99=%d max=%d%n",
                destination.getHostString(), established, failed, elapsedNanos / 1_000_000L, p50, p99, max);
        }

        void ramp(
            Ramping engine,
            int count) throws IOException
        {
            engine.enter();

            final long begin = System.nanoTime();
            final long deadline = begin + RAMP_TIMEOUT_NANOS;

            established = 0;
            failed = 0;

            for (int opened = 0; opened < count; opened += RAMP_BATCH)
            {
                rampBatch(Math.min(RAMP_BATCH, count - opened), deadline);
            }

            elapsedNanos = System.nanoTime() - begin;

            engine.exit(clients);
        }

        private void rampBatch(
            int size,
            long deadline) throws IOException
        {
            for (int index = 0; index < size; index++)
            {
                final SocketChannel channel = SocketChannel.open();
                channel.configureBlocking(false);
                channel.setOption(TCP_NODELAY, true);

                batch[index] = channel;
                stages[index] = CONNECTING;
                echoed[index] = 0;
                starts[index] = System.nanoTime();

                channel.connect(destination);
            }

            int pending = size;
            while (pending > 0)
            {
                int work = 0;

                for (int index = 0; index < size; index++)
                {
                    if (stages[index] != ECHOED)
                    {
                        work += advance(index);
                        pending -= stages[index] == ECHOED ? 1 : 0;
                    }
                }

                if (work == 0)
                {
                    if (System.nanoTime() > deadline)
                    {
                        throw new IllegalStateException("ramp timed out");
                    }

                    Thread.onSpinWait();
                }
            }
        }

        private int advance(
            int index)
        {
            int work = 0;
            final SocketChannel channel = batch[index];

            try
            {
                if (stages[index] == CONNECTING && channel.finishConnect())
                {
                    request.clear();
                    channel.write(request);
                    stages[index] = ECHOING;
                    work = 1;
                }

                if (stages[index] == ECHOING)
                {
                    reply.clear();
                    final int bytesRead = channel.read(reply);

                    if (bytesRead < 0)
                    {
                        throw new IOException("closed by engine");
                    }

                    echoed[index] += bytesRead;
                    work += bytesRead > 0 ? 1 : 0;

                    if (echoed[index] >= RAMP_PAYLOAD)
                    {
                        latencies[established++] = System.nanoTime() - starts[index];
                        held.add(channel);
                        stages[index] = ECHOED;
                    }
                }
            }
            catch (IOException ex)
            {
                failed++;
                stages[index] = ECHOED;
                work = 1;
                closeQuietly(channel);
            }

            return work;
        }

        private static void closeQuietly(
            SocketChannel channel)
        {
            try
            {
                channel.close();
            }
            catch (IOException ex)
            {
                // ignore
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
