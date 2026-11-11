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
package io.aklivity.zilla.runtime.binding.http.internal.bench;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.Responder;
import io.aklivity.zilla.runtime.engine.test.Responder.Trigger;
import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;

/**
 * Drives an http server, speaking HTTP/1.1 on its network side, through the real engine, in front of a mock
 * application that replays a pre-built response. Each {@code exchange} operation is one whole request on a
 * persistent connection: the request is sent and the operation completes once up to {@code connections - 1}
 * other requests are still in flight, so {@code connections} requests are in flight at steady state. Each
 * {@code leave} operation opens one connection, sends one request completely on it and leaves it in flight
 * while the application withholds the response.
 * <p>
 * Read {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler for {@code exchange}, and
 * {@code retained.heap.norm} (B/op) from {@link RetainedHeapProfiler} for {@code leave}. {@code leave} does
 * not depend on {@code responseBody}, {@code streaming} or {@code connections}, so run it with
 * {@code -p responseBody=none -p streaming=false -p connections=1}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class Http1ServerBM
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/http/config/v1.1/server.yaml";
    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;
    private static final int IN_FLIGHT = 5_000;

    @Benchmark
    public void exchange(
        Replying fixture)
    {
        fixture.exchange();
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
            fixture.inflight.add(fixture.open(MessageConsumer.NOOP));
        }
    }

    @State(Scope.Benchmark)
    public abstract static class Fixture
    {
        @Param({"none", "small", "10k"})
        public String requestBody;

        @Param({"none", "small", "10k"})
        public String responseBody;

        @Param({"0", "10"})
        public int headers;

        @Param({"false", "true"})
        public boolean streaming;

        @Param({"1", "16"})
        public int connections;

        final List<Client> inflight = new ArrayList<>(IN_FLIGHT * 4);

        private final Queue<Connection> idle = new ArrayDeque<>();
        private final BooleanSupplier hasIdle = () -> !idle.isEmpty();
        private final boolean respond;

        private EngineBench bench;
        private Responder responder;
        private long netId;
        private UnsafeBufferEx request;
        private boolean failed;

        Fixture(
            boolean respond)
        {
            this.respond = respond;
        }

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("app0")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .start(CONFIGURATION);

            final int requestLength = BenchMessages.bodyLength(requestBody);
            final int responseLength = BenchMessages.bodyLength(responseBody);

            request = new UnsafeBufferEx(Http1Wire.request(headers, BenchMessages.body(requestLength)));
            netId = bench.bindingId("net0");

            responder = new Responder(Trigger.END,
                respond ? BenchMessages.response(bench.labelId("http"), headers, responseLength, streaming) : null);
            bench.newServer("app0", responder);

            for (int connection = 0; respond && connection < connections; connection++)
            {
                final Connection reply = new Connection();
                reply.client = open(reply);
                idle.offer(reply);
            }
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            final int requests = responder.counter().begins();

            inflight.forEach(Client::abort);
            bench.close();

            if (requests == 0 || failed)
            {
                throw new IllegalStateException("requests did not reach the application: " + requests);
            }
        }

        void exchange()
        {
            await(hasIdle);

            final Connection connection = idle.poll();
            connection.client.send(request, 0, request.capacity());
        }

        Client open(
            MessageConsumer reply)
        {
            final Client client = bench.newClient(netId, reply);

            client.begin();
            client.replyWindow(DEFAULT_WINDOW);

            if (!respond)
            {
                client.send(request, 0, request.capacity());
            }

            return client;
        }

        private void await(
            BooleanSupplier condition)
        {
            if (!bench.pollUntil(condition) || failed)
            {
                throw new IllegalStateException("exchange did not complete");
            }
        }

        private final class Connection implements MessageConsumer
        {
            private final Http1Scanner scanner = new Http1Scanner();
            private final DataFW dataRO = new DataFW();

            private Client client;

            @Override
            public void accept(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                switch (msgTypeId)
                {
                case DataFW.TYPE_ID:
                    final DataFW data = dataRO.wrap(buffer, index, index + length);
                    if (data.length() > 0 && scanner.scan(data.payload().buffer(), data.payload().offset(),
                        data.payload().limit()) != 0)
                    {
                        idle.offer(this);
                    }
                    break;
                case AbortFW.TYPE_ID:
                case ResetFW.TYPE_ID:
                    failed = true;
                    break;
                default:
                    break;
                }
            }
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
            .include(Http1ServerBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
