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
package io.aklivity.zilla.runtime.binding.echo.internal.bench;

import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

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
import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;

/**
 * Drives an {@code echo} server through the real engine. The binding echoes the stream back, so no mock
 * provider is needed, and the result is the floor for engine, ring buffer and stream cost against which
 * other bindings are compared. Echo is a raw full-duplex relay, so the benchmarks measure streams and
 * frames, not requests:
 * <ul>
 * <li>{@code open} opens a stream and closes it without data, the cost of one stream's lifecycle.</li>
 * <li>{@code stream} sends messages continuously on one long-lived stream while the echoed bytes flow back,
 * so every operation is one message and data moves in both directions at once. The sender is paced only by
 * the credit the binding grants.</li>
 * <li>{@code leave} opens streams, sends a body on each and leaves them open, because the echo binding
 * cannot withhold its reply.</li>
 * </ul>
 * Read {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler for {@code open} and {@code stream},
 * and {@code retained.heap.norm} (B/op) from {@link RetainedHeapProfiler} for {@code leave}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class EchoServerBM
{
    private static final String CONFIGURATION =
        "io/aklivity/zilla/runtime/binding/echo/internal/bench/EchoServerBM-server.yaml";
    private static final int IN_FLIGHT = 5_000;
    private static final int SMALL_LENGTH = 400;
    private static final int LARGE_LENGTH = 10_000;

    @Benchmark
    public void open(
        Fixture fixture)
    {
        final Client client = fixture.begin();

        client.end();
        client.awaitReplyEnd();
    }

    @Benchmark
    public void stream(
        Streaming fixture)
    {
        fixture.send();
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(MILLISECONDS)
    @Warmup(iterations = 1)
    @Measurement(iterations = 3)
    @OperationsPerInvocation(IN_FLIGHT)
    public void leave(
        Fixture fixture)
    {
        for (int request = 0; request < IN_FLIGHT; request++)
        {
            fixture.inflight.add(fixture.request());
        }
    }

    @State(Scope.Benchmark)
    public static class Fixture
    {
        @Param({"small", "10k"})
        public String size;

        final List<Client> inflight = new ArrayList<>(IN_FLIGHT * 4);

        UnsafeBufferEx body;

        private EngineBench bench;
        private long echoId;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            final int length = "10k".equals(size) ? LARGE_LENGTH : SMALL_LENGTH;
            final byte[] random = new byte[length];
            new Random(0L).nextBytes(random);
            final String text = Base64.getEncoder().withoutPadding().encodeToString(random).substring(0, length);

            bench = new EngineBench()
                .directory("target/zilla-bench")
                .start(CONFIGURATION);

            body = new UnsafeBufferEx(text.getBytes(UTF_8));
            echoId = bench.bindingId("echo0");
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            inflight.forEach(Client::abort);
            bench.close();
        }

        Client begin()
        {
            final Client client = bench.newClient(echoId, MessageConsumer.NOOP);

            client.begin();
            client.replyWindow(DEFAULT_WINDOW);

            return client;
        }

        Client request()
        {
            final Client client = begin();

            client.send(body, 0, body.capacity());

            return client;
        }
    }

    @State(Scope.Benchmark)
    public static class Streaming extends Fixture
    {
        private Client client;
        private long sent;

        @Setup(Level.Trial)
        public void connect()
        {
            client = begin();
        }

        void send()
        {
            client.send(body, 0, body.capacity());
            sent += body.capacity();
        }

        @TearDown(Level.Iteration)
        public void drain()
        {
            client.awaitReplyBytes(sent);
        }
    }

    public static void main(
        String[] args) throws RunnerException
    {
        final Options opt = new OptionsBuilder()
            .include(EchoServerBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
