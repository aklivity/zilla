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
package io.aklivity.zilla.runtime.binding.tls.internal.bench;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.List;

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

import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;

/**
 * Drives a {@code tls} server through the real engine. The encrypted side is a genuine in-memory TLS client
 * ({@link TlsPeer} over a JDK {@code SSLEngine}) feeding the network stream; the plaintext side is an
 * {@link EchoHandler} that sends back what it receives, so every operation exercises decrypt and encrypt.
 * <p>
 * {@code handshake} is one whole connection per operation: full handshake, one small round trip, close.
 * {@code echo} is one plaintext message of {@code payload} bytes sent and echoed back on a long-lived connection.
 * {@code leave} opens established connections and leaves them idle.
 * <p>
 * Read {@code gc.alloc.rate.norm} (B/op, includes the in-memory peer on the benchmark thread) and
 * {@code engine.alloc.norm} (B/op, the binding side alone) for {@code handshake} and {@code echo}, and
 * {@code retained.heap.norm} (B/op) from {@link RetainedHeapProfiler} for {@code leave}. {@code leave} does not
 * depend on {@code payload}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class TlsServerBM
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/tls/config/server.yaml";
    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;
    private static final int ROUNDS = 16;
    private static final int IN_FLIGHT = 1_000;
    private static final int HANDSHAKE_PAYLOAD = 16;
    private static final long TIMEOUT_NANOS = SECONDS.toNanos(10L);

    @Benchmark
    public void handshake(
        Connecting fixture) throws Exception
    {
        fixture.connect(HANDSHAKE_PAYLOAD).finish();
    }

    @Benchmark
    @OperationsPerInvocation(ROUNDS)
    public void echo(
        Established fixture) throws Exception
    {
        for (int round = 0; round < ROUNDS; round++)
        {
            fixture.connection.echo(fixture.message);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(MILLISECONDS)
    @Warmup(iterations = 1)
    @Measurement(iterations = 3)
    @OperationsPerInvocation(IN_FLIGHT)
    public void leave(
        Idle fixture) throws Exception
    {
        for (int connection = 0; connection < IN_FLIGHT; connection++)
        {
            fixture.inflight.add(fixture.connect(HANDSHAKE_PAYLOAD).retire());
        }
    }

    @State(Scope.Benchmark)
    public abstract static class Fixture
    {
        @Param({"TLSv1.3", "TLSv1.2"})
        public String version;

        final List<Client> inflight = new ArrayList<>(IN_FLIGHT * 4);

        private final int capacity;

        private EngineBench bench;
        private TlsPeers peers;
        private long netId;
        private TlsPeer shared;

        Fixture(
            int capacity)
        {
            this.capacity = capacity;
        }

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("app0")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .start(CONFIGURATION);

            peers = new TlsPeers();
            netId = bench.bindingId("net0");
            shared = new TlsPeer(new TlsPeer.Scratch(capacity), false);

            bench.newServer("app0", new EchoHandler(sink -> new EchoHandler.Endpoint()
            {
                @Override
                public void onData(
                    DirectBufferEx buffer,
                    int offset,
                    int length)
                {
                    sink.write(buffer, offset, length);
                }

                @Override
                public void onEnd()
                {
                }

                @Override
                public void reset()
                {
                }
            }));

            setup();
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            teardown();
            inflight.forEach(Client::abort);
            bench.close();
        }

        void setup() throws Exception
        {
        }

        void teardown() throws Exception
        {
        }

        Connection connect(
            int payload) throws Exception
        {
            final Connection connection = new Connection();

            connection.handshake();
            connection.echo(new UnsafeBufferEx(new byte[payload]));

            return connection;
        }

        Connection connectPersistent() throws Exception
        {
            final Connection connection = new Connection();

            connection.handshake();

            return connection;
        }

        final class Connection implements MessageConsumer
        {
            private final DataFW dataRO = new DataFW();
            private final TlsPeer peer = shared.engine(peers.newClient(version));
            private final Client client = bench.newClient(netId, this);

            private MessageConsumer reply = this::onReply;
            private long expected;

            @Override
            public void accept(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                reply.accept(msgTypeId, buffer, index, length);
            }

            private void onReply(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                if (msgTypeId == DataFW.TYPE_ID)
                {
                    final DataFW data = dataRO.wrap(buffer, index, index + length);
                    peer.receive(data.payload().buffer(), data.payload().offset(), data.payload().sizeof());
                }
            }

            void handshake() throws Exception
            {
                client.begin();
                client.replyWindow(DEFAULT_WINDOW);
                peer.begin();

                final long deadline = System.nanoTime() + TIMEOUT_NANOS;
                while (true)
                {
                    peer.process();
                    flush();

                    if (peer.established())
                    {
                        break;
                    }

                    if (bench.poll() == 0)
                    {
                        Thread.onSpinWait();
                    }

                    check(deadline);
                }
            }

            void echo(
                DirectBufferEx message) throws Exception
            {
                peer.send(message, 0, message.capacity());
                peer.process();
                flush();

                expected += message.capacity();

                final long deadline = System.nanoTime() + TIMEOUT_NANOS;
                while (peer.receivedPlaintext() < expected)
                {
                    if (bench.poll() == 0)
                    {
                        Thread.onSpinWait();
                    }

                    peer.process();
                    check(deadline);
                }
            }

            void finish() throws Exception
            {
                peer.closeOutbound();
                peer.process();
                flush();
                client.end();
                client.awaitReplyEnd();
            }

            Client retire()
            {
                reply = MessageConsumer.NOOP;
                return client;
            }

            private void flush()
            {
                if (peer.outputLength() > 0)
                {
                    client.send(peer.output(), 0, peer.outputLength());
                    peer.clearOutput();
                }
            }

            private void check(
                long deadline)
            {
                if (System.nanoTime() > deadline)
                {
                    throw new IllegalStateException("tls peer timed out");
                }
            }
        }
    }

    @State(Scope.Benchmark)
    public static class Connecting extends Fixture
    {
        public Connecting()
        {
            super(64 * 1024);
        }
    }

    @State(Scope.Benchmark)
    public static class Established extends Fixture
    {
        @Param({"64", "1024", "16384", "65536"})
        public int payload;

        Connection connection;
        DirectBufferEx message;

        public Established()
        {
            super(512 * 1024);
        }

        @Override
        void setup() throws Exception
        {
            message = new UnsafeBufferEx(new byte[payload]);
            connection = connectPersistent();
        }

        @Override
        void teardown() throws Exception
        {
            connection.finish();
        }
    }

    @State(Scope.Benchmark)
    public static class Idle extends Fixture
    {
        public Idle()
        {
            super(64 * 1024);
        }
    }

    public static void main(
        String[] args) throws RunnerException
    {
        final Options opt = new OptionsBuilder()
            .include(TlsServerBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(EngineAllocProfiler.class)
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
