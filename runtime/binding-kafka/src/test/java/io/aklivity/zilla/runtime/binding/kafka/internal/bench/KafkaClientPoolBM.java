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
package io.aklivity.zilla.runtime.binding.kafka.internal.bench;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayDeque;
import java.util.Queue;

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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.specs.binding.kafka.internal.KafkaFunctions;

/**
 * Drives the {@code kafka} client binding, and so its shared connection pool, through the real engine against
 * a broker stub that answers each request after a fixed delay. Every operation is one
 * {@code InitProducerId} request, opened as an application stream into the client binding and completed when the
 * reply begins. All requests share the one pooled connection to the broker, the way the session setup requests of
 * many MQTT clients do.
 * <p>
 * {@code outstanding} is the size of a sliding window: that many requests are kept open at once. With a stub
 * delay of {@code D} microseconds, a connection that carries one request at a time completes at most
 * {@code 1 / D} requests per second however large the window, and a connection that carries the whole window
 * completes about {@code outstanding / D}. {@code stubDelayMicros=0} answers on the next poll and shows the
 * cost of the pool and the client binding with no waiting.
 * <p>
 * The broker stub stands in for the TCP connection: the client binding's network stream is served by the stub
 * on the benchmark thread, so the engine, pool and client factory are the real ones and the socket is not.
 * Read {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(1)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class KafkaClientPoolBM
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/kafka/config/client.yaml";
    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;
    private static final int REQUESTS = 256;

    @Benchmark
    @OperationsPerInvocation(REQUESTS)
    public void initProducerId(
        Requesting fixture)
    {
        fixture.run(REQUESTS);
    }

    @State(Scope.Benchmark)
    public static class Requesting
    {
        @Param({"1", "16", "64"})
        public int outstanding;

        @Param({"0", "1000"})
        public long stubDelayMicros;

        private final Queue<Request> idle = new ArrayDeque<>();

        private EngineBench bench;
        private KafkaBrokerStub stub;
        private long appId;
        private UnsafeBufferEx beginEx;
        private int completed;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            stub = new KafkaBrokerStub(MICROSECONDS.toNanos(stubDelayMicros));

            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("net0")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .start(CONFIGURATION);

            bench.newServer("net0", stub);
            appId = bench.bindingId("app0");

            beginEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
                .typeId(bench.labelId("kafka"))
                .initProducerId()
                    .producerId(-1)
                    .producerEpoch((short) -1)
                    .build()
                .build());
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            System.out.printf("broker requests=%d maxPipelined=%d%n", stub.requests(), stub.maxPipelined());
            bench.close();
        }

        void run(
            int requests)
        {
            final Queue<Request> open = new ArrayDeque<>();
            final long deadline = System.nanoTime() + SECONDS.toNanos(30L);

            int issued = 0;
            completed = 0;

            while (completed < requests)
            {
                while (issued < requests && issued - completed < outstanding)
                {
                    open.add(open());
                    issued++;
                }

                int work = bench.poll();
                work += stub.drain();

                while (!open.isEmpty() && open.peek().replied)
                {
                    final Request request = open.remove();
                    request.client.end();
                    idle.offer(request);
                    completed++;
                }

                if (work == 0)
                {
                    if (System.nanoTime() > deadline)
                    {
                        throw new IllegalStateException("timed out, completed " + completed + " of " + requests);
                    }

                    Thread.onSpinWait();
                }
            }
        }

        private Request open()
        {
            Request request = idle.poll();

            if (request == null)
            {
                request = new Request();
            }

            request.replied = false;
            request.client = bench.newClient(appId, request);
            request.client.begin(beginEx, 0, beginEx.capacity());
            request.client.replyWindow(DEFAULT_WINDOW);

            return request;
        }

        private static final class Request implements MessageConsumer
        {
            private Client client;
            private boolean replied;

            @Override
            public void accept(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                replied |= msgTypeId == BeginFW.TYPE_ID;
            }
        }
    }
}
