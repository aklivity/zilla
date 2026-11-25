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

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SEGMENT_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SEGMENT_INDEX_BYTES;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

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
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.specs.binding.kafka.internal.KafkaFunctions;

/**
 * Drives the {@code kafka} {@code cache_client} and {@code cache_server} bindings, through the real engine, on one
 * engine worker: one produce stream per partition, a record produced to each partition in turn. The cache server
 * appends the record to the cache, sends it on its produce stream to the exit, and, as the exit acknowledges it and
 * reads it back on the partition's fetch stream, populates the cache with it. The exit is {@link KafkaCacheExitStub}.
 * <p>
 * Every operation is one record, complete when the exit has acknowledged it; each invocation then waits for
 * the cache to take in every record it fed back, so the rate is the one the worker sustains for produce and populate
 * together. {@code outstanding} is the number of records kept in flight across all the partitions.
 * {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler is the allocation per record, taken over every thread,
 * the stub and the harness included, which allocate nothing per record.
 * <p>
 * Set {@code -Dzilla.bench.directory} to run several benchmark JVMs side by side, one engine worker each.
 * <p>
 * The stub stands in for the client binding and the broker, so the cost of encoding the Kafka protocol is not in the
 * figure; this benchmark isolates the cache server.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(1)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class KafkaCacheServerBM
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/kafka/config/cache.options.bootstrap.yaml";
    private static final int STREAMS_BUFFER_CAPACITY = 64 * 1024 * 1024;
    private static final int RECORDS = 2048;
    private static final int SEGMENT_BYTES = Integer.MAX_VALUE;
    private static final int SEGMENT_INDEX_BYTES = 128 * 1024 * 1024;

    @Benchmark
    @OperationsPerInvocation(RECORDS)
    public void produce(
        Producing fixture)
    {
        fixture.run(RECORDS);
    }

    @State(Scope.Benchmark)
    public static class Producing
    {
        @Param({"1", "4", "16"})
        public int partitions;

        @Param({"100", "1024"})
        public int payloadBytes;

        @Param({"0"})
        public long stubDelayMicros;

        @Param({"256"})
        public int outstanding;

        @Param({"0"})
        public int members;

        private EngineBench bench;
        private KafkaCacheExitStub stub;
        private Producer[] producers;
        private UnsafeBufferEx payload;
        private UnsafeBufferEx dataEx;
        private long issued;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            payload = new UnsafeBufferEx(new byte[payloadBytes]);

            bench = new EngineBench()
                .directory(System.getProperty("zilla.bench.directory", "target/zilla-bench"))
                .external("app1")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .configure(KAFKA_CACHE_SEGMENT_BYTES, SEGMENT_BYTES)
                .configure(KAFKA_CACHE_SEGMENT_INDEX_BYTES, SEGMENT_INDEX_BYTES)
                .start(CONFIGURATION);

            final int kafkaTypeId = bench.labelId("kafka");
            stub = new KafkaCacheExitStub(kafkaTypeId, partitions, MICROSECONDS.toNanos(stubDelayMicros), payload);
            bench.newServer("app1", stub);

            dataEx = new UnsafeBufferEx(KafkaFunctions.dataEx()
                .typeId(kafkaTypeId)
                .produce()
                    .timestamp(1_700_000_000_000L)
                    .build()
                .build());

            final long appId = bench.bindingId("app0");
            producers = new Producer[partitions];
            for (int partitionId = 0; partitionId < partitions; partitionId++)
            {
                final Producer producer = new Producer();
                producer.client = bench.newClient(appId, producer);
                final UnsafeBufferEx beginEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
                    .typeId(kafkaTypeId)
                    .produce()
                        .topic("test")
                        .partition(partitionId)
                        .build()
                    .build());
                producer.client.begin(beginEx, 0, beginEx.capacity());
                producer.client.replyWindow(DEFAULT_WINDOW);
                producers[partitionId] = producer;
            }

            final long cacheId = bench.bindingId("cache0");
            for (int partitionId = 0; partitionId < partitions; partitionId++)
            {
                final UnsafeBufferEx fetchEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
                    .typeId(kafkaTypeId)
                    .fetch()
                        .topic("test")
                        .partition(partitionId, 0L)
                        .build()
                    .build());

                for (int member = 0; member < members; member++)
                {
                    final Client fetcher = bench.newClient(cacheId, new Producer());
                    fetcher.begin(fetchEx, 0, fetchEx.capacity());
                    fetcher.replyWindow(DEFAULT_WINDOW);
                }
            }

            run(partitions * 2);
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            System.out.printf("produced=%d acknowledged=%d populated=%d unknown=%d%n",
                stub.produced(), stub.acknowledged(), stub.populated(), stub.unknown());
            bench.close();
        }

        void run(
            int records)
        {
            final long deadline = System.nanoTime() + SECONDS.toNanos(60L);
            final long target = issued + records;

            while (stub.acknowledged() < target || stub.populated() < target)
            {
                while (issued < target && issued - stub.acknowledged() < outstanding)
                {
                    final Producer producer = producers[(int) (issued % partitions)];
                    if (producer.client.initialCredit() < payloadBytes + 256)
                    {
                        break;
                    }

                    producer.client.data(payload, 0, payloadBytes, dataEx, 0, dataEx.capacity());
                    issued++;
                }

                int work = bench.poll();
                work += stub.drain();

                if (work == 0)
                {
                    if (System.nanoTime() > deadline)
                    {
                        throw new IllegalStateException(String.format(
                            "timed out, issued %d acknowledged %d populated %d of %d", issued, stub.acknowledged(),
                            stub.populated(), target));
                    }

                    Thread.onSpinWait();
                }
            }
        }

        private final class Producer implements MessageConsumer
        {
            private Client client;

            @Override
            public void accept(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                if (msgTypeId == ResetFW.TYPE_ID || msgTypeId == AbortFW.TYPE_ID)
                {
                    throw new IllegalStateException("producer stream closed");
                }
            }
        }
    }
}
