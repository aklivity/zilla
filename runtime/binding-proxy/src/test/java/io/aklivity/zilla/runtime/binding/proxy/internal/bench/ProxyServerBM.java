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
package io.aklivity.zilla.runtime.binding.proxy.internal.bench;

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
import io.aklivity.zilla.runtime.engine.test.Responder;
import io.aklivity.zilla.runtime.engine.test.Responder.Response;
import io.aklivity.zilla.runtime.engine.test.Responder.Trigger;
import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;

/**
 * Drives a {@code proxy} server through the real engine, in front of a mock application that replays a
 * response of the request's size. The binding decodes a PROXY protocol v2 header from the network stream,
 * opens an application stream carrying the decoded address, and relays the payload in both directions, so
 * the result is a baseline with an application hop and a decode state machine. Each {@code exchange}
 * operation is one whole request: the header and body are sent and the response is received to its end.
 * Each {@code leave} operation opens one request, sends it completely and leaves it in flight while the
 * application withholds the response.
 * <p>
 * Every stream has a credit window smaller than the 10k message, so each request and each reply is sent
 * across several windows and the benchmark exercises credit replenishment in both directions.
 * <p>
 * Read {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler for {@code exchange}, and
 * {@code retained.heap.norm} (B/op) from {@link RetainedHeapProfiler} for {@code leave}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class ProxyServerBM
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/proxy/config/server.tcp4.yaml";
    private static final byte[] HEADER =
    {
        '\r', '\n', '\r', '\n', 0x00, '\r', '\n', 'Q', 'U', 'I', 'T', '\n',
        0x21, 0x11, 0x00, 0x0c,
        (byte) 0xc0, (byte) 0xa8, 0x00, 0x01,
        (byte) 0xc0, (byte) 0xa8, 0x00, (byte) 0xfe,
        (byte) 0x80, 0x00, 0x01, (byte) 0xbb
    };
    private static final int WINDOW = 4 * 1024;
    private static final int IN_FLIGHT = 5_000;
    private static final int SMALL_LENGTH = 400;
    private static final int LARGE_LENGTH = 10_000;

    @Benchmark
    public void exchange(
        Replying fixture)
    {
        final Client client = fixture.open();

        client.end();
        client.awaitReplyEnd();
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(MILLISECONDS)
    @Warmup(iterations = 1)
    @Measurement(iterations = 3)
    @OperationsPerInvocation(IN_FLIGHT)
    public void leave(
        Withholding fixture)
    {
        for (int request = 0; request < IN_FLIGHT; request++)
        {
            final Client client = fixture.open();

            client.end();
            fixture.inflight.add(client);
        }
    }

    @State(Scope.Benchmark)
    public abstract static class Fixture
    {
        @Param({"small", "10k"})
        public String size;

        final List<Client> inflight = new ArrayList<>(IN_FLIGHT * 4);

        private final boolean respond;

        private EngineBench bench;
        private long proxyId;
        private UnsafeBufferEx request;

        Fixture(
            boolean respond)
        {
            this.respond = respond;
        }

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            final int length = "10k".equals(size) ? LARGE_LENGTH : SMALL_LENGTH;
            final byte[] random = new byte[length];
            new Random(0L).nextBytes(random);
            final String text = Base64.getEncoder().withoutPadding().encodeToString(random).substring(0, length);
            final byte[] payload = text.getBytes(UTF_8);

            final byte[] message = new byte[HEADER.length + payload.length];
            System.arraycopy(HEADER, 0, message, 0, HEADER.length);
            System.arraycopy(payload, 0, message, HEADER.length, payload.length);

            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("app0")
                .start(CONFIGURATION);

            request = new UnsafeBufferEx(message);
            proxyId = bench.bindingId("net0");

            final Response response = new Response(
                new byte[0],
                new byte[][] { new byte[0] },
                new byte[][] { payload },
                new byte[0],
                0);
            bench.newServer("app0", new Responder(WINDOW, Trigger.END, respond ? response : null));
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            inflight.forEach(Client::abort);
            bench.close();
        }

        Client open()
        {
            final Client client = bench.newClient(proxyId, MessageConsumer.NOOP);

            client.begin();
            client.replyWindow(WINDOW);
            client.send(request, 0, request.capacity());

            return client;
        }
    }

    @State(Scope.Benchmark)
    public static class Replying extends Fixture
    {
        public Replying()
        {
            super(true);
        }
    }

    @State(Scope.Benchmark)
    public static class Withholding extends Fixture
    {
        public Withholding()
        {
            super(false);
        }
    }

    public static void main(
        String[] args) throws RunnerException
    {
        final Options opt = new OptionsBuilder()
            .include(ProxyServerBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
