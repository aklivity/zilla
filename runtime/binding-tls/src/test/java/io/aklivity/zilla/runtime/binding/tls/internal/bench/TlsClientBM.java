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

import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;

/**
 * Drives a {@code tls} client through the real engine. The plaintext side is an {@link EngineBench} client; the
 * encrypted side is an {@link EchoHandler} whose endpoint is a genuine in-memory TLS server ({@link TlsPeer} over
 * a JDK {@code SSLEngine}) that echoes the plaintext it decrypts, so every operation exercises encrypt and decrypt.
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
public class TlsClientBM
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/tls/config/client.yaml";
    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;
    private static final int ROUNDS = 16;
    private static final int IN_FLIGHT = 1_000;
    private static final int HANDSHAKE_PAYLOAD = 16;

    @Benchmark
    public void handshake(
        Connecting fixture)
    {
        final Client client = fixture.connect();

        fixture.echo(client, fixture.ping);

        client.end();
        client.awaitReplyEnd();
    }

    @Benchmark
    @OperationsPerInvocation(ROUNDS)
    public void echo(
        Established fixture)
    {
        for (int round = 0; round < ROUNDS; round++)
        {
            fixture.echo(fixture.client, fixture.message);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(MILLISECONDS)
    @Warmup(iterations = 1)
    @Measurement(iterations = 3)
    @OperationsPerInvocation(IN_FLIGHT)
    public void leave(
        Idle fixture)
    {
        for (int connection = 0; connection < IN_FLIGHT; connection++)
        {
            final Client client = fixture.connect();

            fixture.echo(client, fixture.ping);
            fixture.inflight.add(client);
        }
    }

    @State(Scope.Benchmark)
    public abstract static class Fixture
    {
        @Param({"TLSv1.3", "TLSv1.2"})
        public String version;

        final List<Client> inflight = new ArrayList<>(IN_FLIGHT * 4);
        final DirectBufferEx ping = new UnsafeBufferEx(new byte[HANDSHAKE_PAYLOAD]);

        private final boolean retire;

        private EngineBench bench;
        private long appId;
        private DirectBufferEx beginEx;

        Fixture(
            boolean retire)
        {
            this.retire = retire;
        }

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("net0")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .start(CONFIGURATION);

            appId = bench.bindingId("app0");
            beginEx = proxyBeginEx(bench.labelId("proxy"));

            final TlsPeers peers = new TlsPeers();
            final TlsPeer.Scratch scratch = new TlsPeer.Scratch(512 * 1024);

            bench.newServer("net0", new EchoHandler(TlsPeers.proxyInetBeginEx(bench.labelId("proxy")),
                sink -> new PeerEndpoint(sink, peers, scratch, version, retire)));

            setup();
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            inflight.forEach(Client::abort);
            bench.close();
        }

        void setup()
        {
        }

        Client connect()
        {
            final Client client = bench.newClient(appId, MessageConsumer.NOOP);

            client.begin(beginEx, 0, beginEx.capacity());
            client.replyWindow(DEFAULT_WINDOW);

            return client;
        }

        private static DirectBufferEx proxyBeginEx(
            int proxyTypeId)
        {
            final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);

            final ProxyBeginExFW proxyBeginEx = new ProxyBeginExFW.Builder().wrap(buffer, 0, buffer.capacity())
                .typeId(proxyTypeId)
                .address(a -> a.none(n -> {}))
                .infos(is -> is.item(i -> i.authority("localhost")))
                .build();

            final byte[] encoded = new byte[proxyBeginEx.sizeof()];
            buffer.getBytes(0, encoded);

            return new UnsafeBufferEx(encoded);
        }

        void echo(
            Client client,
            DirectBufferEx message)
        {
            final long target = client.replyBytes() + message.capacity();

            client.send(message, 0, message.capacity());
            client.awaitReplyBytes(target);
        }
    }

    @State(Scope.Benchmark)
    public static class Connecting extends Fixture
    {
        public Connecting()
        {
            super(false);
        }
    }

    @State(Scope.Benchmark)
    public static class Established extends Fixture
    {
        @Param({"64", "1024", "16384", "65536"})
        public int payload;

        Client client;
        DirectBufferEx message;

        public Established()
        {
            super(false);
        }

        @Override
        void setup()
        {
            message = new UnsafeBufferEx(new byte[payload]);
            client = connect();
            echo(client, ping);
        }
    }

    @State(Scope.Benchmark)
    public static class Idle extends Fixture
    {
        public Idle()
        {
            super(true);
        }
    }

    private static final class PeerEndpoint implements EchoHandler.Endpoint
    {
        private final EchoHandler.Endpoint engaged = new EchoHandler.Endpoint()
        {
            @Override
            public void onData(
                DirectBufferEx buffer,
                int offset,
                int length) throws Exception
            {
                peer.receive(buffer, offset, length);
                peer.process();
                drain();

                if (peer.inboundDone())
                {
                    peer.closeOutbound();
                    peer.process();
                    drain();
                    sink.end();
                }

                if (retire && peer.receivedPlaintext() > 0L)
                {
                    peer.release();
                    active = INERT;
                }
            }

            @Override
            public void onEnd() throws Exception
            {
                peer.closeOutbound();
                peer.process();
                drain();
            }

            @Override
            public void reset()
            {
            }
        };

        private static final EchoHandler.Endpoint INERT = new EchoHandler.Endpoint()
        {
            @Override
            public void onData(
                DirectBufferEx buffer,
                int offset,
                int length)
            {
            }

            @Override
            public void onEnd()
            {
            }

            @Override
            public void reset()
            {
            }
        };

        private final EchoHandler.Sink sink;
        private final TlsPeers peers;
        private final TlsPeer peer;
        private final String version;
        private final boolean retire;

        private EchoHandler.Endpoint active = engaged;

        PeerEndpoint(
            EchoHandler.Sink sink,
            TlsPeers peers,
            TlsPeer.Scratch scratch,
            String version,
            boolean retire)
        {
            this.sink = sink;
            this.peers = peers;
            this.peer = new TlsPeer(scratch, true);
            this.version = version;
            this.retire = retire;
        }

        @Override
        public void onData(
            DirectBufferEx buffer,
            int offset,
            int length) throws Exception
        {
            active.onData(buffer, offset, length);
        }

        @Override
        public void onEnd() throws Exception
        {
            active.onEnd();
        }

        @Override
        public void reset()
        {
            peer.engine(peers.newServer(version));
            active = engaged;
        }

        private void drain()
        {
            if (peer.outputLength() > 0)
            {
                sink.write(peer.output(), 0, peer.outputLength());
                peer.clearOutput();
            }
        }
    }

    public static void main(
        String[] args) throws RunnerException
    {
        final Options opt = new OptionsBuilder()
            .include(TlsClientBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(EngineAllocProfiler.class)
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
