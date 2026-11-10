/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.mcp.internal.bench;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
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

import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.Responder;
import io.aklivity.zilla.runtime.engine.test.Responder.Response;
import io.aklivity.zilla.runtime.engine.test.Responder.Trigger;
import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;

/**
 * Drives an {@code mcp} proxy through the real engine, with application streams on both sides: a mock client on
 * the side that faces the client and a mock backend on the side that faces the server, which replays
 * pre-built responses.
 * <p>
 * Each {@code initialize} operation is one whole session: the lifecycle stream opened and ended. Each
 * {@code toolsCall} and {@code toolsList} operation is one request on an established session, received to the
 * end of its response, round robin over {@code sessions}. Each {@code leaveSessions} operation establishes one
 * session and leaves it idle, and each {@code leaveCalls} operation sends one {@code tools/call} completely and
 * leaves it in flight while the backend withholds the response.
 * <p>
 * Read {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler for the exchange benchmarks, and
 * {@code retained.heap.norm} (B/op) from {@link RetainedHeapProfiler} for the {@code leave} benchmarks.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class McpProxyBM
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/mcp/config/proxy.yaml";
    private static final String SESSION_ID_SUPPLIER = McpBenchSessions.class.getName() + "::externalSessionId";
    private static final String SESSION_ID_PROPERTY = "zilla.binding.mcp.session.id";
    private static final int STREAMS_BUFFER_CAPACITY = 64 * 1024 * 1024;
    private static final int STREAMING_FRAGMENT_LENGTH = 256;
    private static final int EXCHANGE_IDS = 1024;
    private static final int LEAVE_IDS = 32 * 1024;
    private static final long QUIESCENT_NANOS = MILLISECONDS.toNanos(200L);
    private static final int IN_FLIGHT = 5_000;
    private static final int IN_FLIGHT_SESSIONS = 100;

    @Benchmark
    public void initialize(
        Handshaking fixture)
    {
        fixture.driver.handshake();
    }

    @Benchmark
    public void toolsCall(
        Replying fixture)
    {
        fixture.driver.toolsCall(fixture.next()).awaitReplyEnd();
    }

    @Benchmark
    public void toolsList(
        Replying fixture)
    {
        fixture.driver.toolsList(fixture.next()).awaitReplyEnd();
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(MILLISECONDS)
    @Warmup(iterations = 1)
    @Measurement(iterations = 3)
    @OperationsPerInvocation(IN_FLIGHT)
    public void leaveSessions(
        Idling fixture)
    {
        for (int session = 0; session < IN_FLIGHT; session++)
        {
            fixture.inflight.add(fixture.driver.open());
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(MILLISECONDS)
    @Warmup(iterations = 1)
    @Measurement(iterations = 3)
    @OperationsPerInvocation(IN_FLIGHT)
    public void leaveCalls(
        Withholding fixture)
    {
        for (int call = 0; call < IN_FLIGHT; call++)
        {
            fixture.inflight.add(fixture.driver.toolsCall(fixture.next()));
        }
    }

    @State(Scope.Benchmark)
    public abstract static class Fixture
    {
        final List<Client> inflight = new ArrayList<>(IN_FLIGHT * 4);

        McpApplicationDriver driver;

        private EngineBench bench;
        private int round;

        void start(
            int ids,
            int sessions,
            String size,
            boolean streaming,
            boolean respond,
            boolean requests) throws Exception
        {
            McpBenchSessions.reset(ids, 1);

            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("app1")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .configure(SESSION_ID_PROPERTY, SESSION_ID_SUPPLIER)
                .start(CONFIGURATION);

            final int mcpTypeId = bench.labelId("mcp");

            bench.newServer("app1", new McpApplicationPeer(
                new McpLifecyclePeer(McpBenchMessages.lifecycleReplyEx(mcpTypeId)),
                responder(McpBenchMessages.toolsListEx(mcpTypeId, McpBenchMessages.APP_SESSION_ID).byteArray(),
                    respond ? McpBenchMessages.toolsListResult(size) : null, streaming),
                responder(McpBenchMessages.toolsCallEx(mcpTypeId, McpBenchMessages.APP_SESSION_ID, 0).byteArray(),
                    respond ? McpBenchMessages.toolsCallResult(size) : null, streaming)));

            driver = new McpApplicationDriver(bench, bench.bindingId("app0"), mcpTypeId, ids, size, requests);

            for (int session = 0; session < sessions; session++)
            {
                inflight.add(driver.open());
            }
        }

        int next()
        {
            return round++ % sessionCount();
        }

        abstract int sessionCount();

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            inflight.forEach(Client::abort);
            drain();
            bench.close();
        }

        private void drain()
        {
            long quiescentSince = System.nanoTime();

            while (System.nanoTime() - quiescentSince < QUIESCENT_NANOS)
            {
                if (bench.poll() != 0)
                {
                    quiescentSince = System.nanoTime();
                }
            }
        }

        private static Responder responder(
            byte[] beginEx,
            String result,
            boolean streaming)
        {
            return new Responder(Trigger.BEGIN, result != null
                ? new Response(
                    beginEx,
                    new byte[][] { new byte[0] },
                    new byte[][] { McpBenchMessages.bytes(result) },
                    new byte[0],
                    streaming ? STREAMING_FRAGMENT_LENGTH : 0)
                : null);
        }
    }

    @State(Scope.Benchmark)
    public static class Handshaking extends Fixture
    {
        @Setup(Level.Trial)
        public void init() throws Exception
        {
            start(EXCHANGE_IDS, 0, "small", false, true, false);
        }

        @Override
        int sessionCount()
        {
            return 1;
        }
    }

    @State(Scope.Benchmark)
    public static class Replying extends Fixture
    {
        @Param({"small", "10k"})
        public String size;

        @Param({"false", "true"})
        public boolean streaming;

        @Param({"1", "1000"})
        public int sessions;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            start(EXCHANGE_IDS, sessions, size, streaming, true, true);
        }

        @Override
        int sessionCount()
        {
            return sessions;
        }
    }

    @State(Scope.Benchmark)
    public static class Idling extends Fixture
    {
        @Setup(Level.Trial)
        public void init() throws Exception
        {
            start(LEAVE_IDS, 0, "small", false, true, false);
        }

        @Override
        int sessionCount()
        {
            return 1;
        }
    }

    @State(Scope.Benchmark)
    public static class Withholding extends Fixture
    {
        @Param({"small", "10k"})
        public String size;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            start(EXCHANGE_IDS, IN_FLIGHT_SESSIONS, size, false, false, true);
        }

        @Override
        int sessionCount()
        {
            return IN_FLIGHT_SESSIONS;
        }
    }

    public static void main(
        String[] args) throws RunnerException
    {
        final Options opt = new OptionsBuilder()
            .include(McpProxyBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
