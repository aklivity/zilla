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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.bench;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.BooleanSupplier;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
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
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.specs.binding.mqtt.internal.MqttFunctions;

/**
 * Drives the {@code mqtt-kafka} proxy through the real engine between an application peer and a stub Kafka. The
 * application peer opens an {@code mqtt} session stream with a clean start, a distinct client id and a
 * {@code publishQosMax} of 2, which is the stream the {@code mqtt} binding opens for each CONNECT, and waits for
 * the session stream to be established, which is the CONNACK. {@link KafkaStub} answers the Kafka streams the
 * session opens the way the session scripts in {@code binding-mqtt-kafka.spec} specify. Each {@code handshake}
 * operation is one completed CONNACK; the established stream is then ended so that its Kafka streams close.
 * <p>
 * {@code depth} is the number of handshakes kept outstanding: each time one completes the next one begins, so
 * the proxy always has {@code depth} handshakes in flight. A {@code depth} of 1 measures the latency of one
 * handshake. {@code ackDelayMillis} delays every answer of the stub, which shows how Kafka latency changes the
 * number of handshakes pending at once.
 * <p>
 * Read {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler for the allocation per handshake.
 * <p>
 * Each {@code publish} operation is one QoS 1 publish on one of {@code publishers} open {@code mqtt} publish
 * streams, each with its own topic, and completes when the stream's window acknowledges it, which is the PUBACK.
 * {@link KafkaStub} acknowledges the produce stream that each publish stream opens on the messages topic.
 * {@code inflight} is the number of publishes kept outstanding, {@code payload} the message size in bytes, and
 * {@code ackDelayMillis} the delay of every stub answer. The streams are opened and acknowledged once before
 * measurement starts, so the benchmark measures steady-state publishing and not the opening of a publish stream.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class MqttKafkaProxyBM
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/mqtt/kafka/config/proxy.yaml";
    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;
    private static final long TIMEOUT_NANOS = SECONDS.toNanos(10L);
    private static final String CLIENT_ID_PREFIX = "client-";
    private static final int CLIENT_ID_DIGITS = 10;

    @Benchmark
    public void handshake(
        Fixture fixture)
    {
        fixture.complete();
    }

    @Benchmark
    public void publish(
        PublishFixture fixture)
    {
        fixture.complete();
    }

    @State(Scope.Benchmark)
    public static class Fixture
    {
        @Param({"1", "1024"})
        public int depth;

        @Param({"0", "5"})
        public long ackDelayMillis;

        private final Queue<Handshake> established = new ArrayDeque<>();
        private final Queue<Handshake> idle = new ArrayDeque<>();

        private EngineBench bench;
        private KafkaStub kafka;
        private long mqttId;
        private byte[] beginExTemplate;
        private int clientIdOffset;
        private long clientCount;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("kafka0")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .start(CONFIGURATION);

            kafka = new KafkaStub(bench.labelId("kafka"), ackDelayMillis);
            bench.newServer("kafka0", kafka);

            mqttId = bench.bindingId("mqtt0");
            beginExTemplate = MqttFunctions.beginEx()
                .typeId(bench.labelId("mqtt"))
                .session()
                    .flags("CLEAN_START")
                    .expiry(1)
                    .publishQosMax(2)
                    .clientId(CLIENT_ID_PREFIX + "0".repeat(CLIENT_ID_DIGITS))
                    .build()
                .build();
            clientIdOffset = indexOf(beginExTemplate, CLIENT_ID_PREFIX.getBytes(UTF_8)) + CLIENT_ID_PREFIX.length();

            for (int handshake = 0; handshake < depth; handshake++)
            {
                begin();
            }
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            bench.close();
        }

        void complete()
        {
            final long deadline = System.nanoTime() + TIMEOUT_NANOS;

            while (established.isEmpty())
            {
                if (bench.poll() + kafka.release() == 0)
                {
                    Thread.onSpinWait();
                }

                if (System.nanoTime() > deadline)
                {
                    throw new IllegalStateException(String.format(
                        "handshake timed out, unexpected kafka streams %d", kafka.unexpected()));
                }
            }

            final Handshake handshake = established.poll();

            handshake.end();
            begin();
        }

        private void begin()
        {
            Handshake handshake = idle.poll();

            if (handshake == null)
            {
                handshake = new Handshake(beginExTemplate);
            }

            handshake.begin(bench.newClient(mqttId, handshake), clientIdOffset, ++clientCount);
        }

        private static int indexOf(
            byte[] array,
            byte[] pattern)
        {
            int index = -1;

            for (int offset = 0; index == -1 && offset + pattern.length <= array.length; offset++)
            {
                int length = 0;
                while (length < pattern.length && array[offset + length] == pattern[length])
                {
                    length++;
                }

                if (length == pattern.length)
                {
                    index = offset;
                }
            }

            return index;
        }

        private final class Handshake implements MessageConsumer
        {
            private final byte[] beginExBytes;
            private final MutableDirectBufferEx beginEx;
            private final DirectBufferEx beginExView;

            private Client client;
            private boolean connected;

            private Handshake(
                byte[] template)
            {
                this.beginExBytes = template.clone();
                this.beginEx = new UnsafeBufferEx(beginExBytes);
                this.beginExView = beginEx;
            }

            private void begin(
                Client client,
                int clientIdOffset,
                long clientNumber)
            {
                this.client = client;
                this.connected = false;

                long remaining = clientNumber;
                for (int digit = CLIENT_ID_DIGITS - 1; digit >= 0; digit--)
                {
                    beginExBytes[clientIdOffset + digit] = (byte) ('0' + remaining % 10L);
                    remaining /= 10L;
                }

                client.begin(beginExView, 0, beginExView.capacity());
                client.replyWindow(DEFAULT_WINDOW);
            }

            private void end()
            {
                client.end();
            }

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
                    if (!connected)
                    {
                        connected = true;
                        established.add(this);
                    }
                    break;
                case EndFW.TYPE_ID:
                    idle.add(this);
                    break;
                case AbortFW.TYPE_ID:
                case ResetFW.TYPE_ID:
                    throw new IllegalStateException(String.format(
                        "handshake stream rejected connected=%b, unexpected kafka streams %d", connected, kafka.unexpected()));
                default:
                    break;
                }
            }
        }
    }

    @State(Scope.Benchmark)
    public static class PublishFixture
    {
        @Param({"1000"})
        public int publishers;

        @Param({"1", "256"})
        public int inflight;

        @Param({"16", "1024"})
        public int payload;

        @Param({"0", "5"})
        public long ackDelayMillis;

        private final Queue<Publisher> idle = new ArrayDeque<>();
        private final Queue<Publisher> acknowledged = new ArrayDeque<>();
        private final BooleanSupplier acknowledgedAvailable = () -> !acknowledged.isEmpty();

        private EngineBench bench;
        private KafkaStub kafka;
        private DirectBufferEx dataEx;
        private DirectBufferEx message;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("kafka0")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .start(CONFIGURATION);

            kafka = new KafkaStub(bench.labelId("kafka"), ackDelayMillis);
            bench.newServer("kafka0", kafka);

            final int mqttTypeId = bench.labelId("mqtt");
            final long mqttId = bench.bindingId("mqtt0");

            dataEx = new UnsafeBufferEx(MqttFunctions.dataEx()
                .typeId(mqttTypeId)
                .publish()
                    .qos("AT_LEAST_ONCE")
                    .packetId(1)
                    .build()
                .build());
            message = new UnsafeBufferEx(new byte[payload]);

            for (int publisher = 0; publisher < publishers; publisher++)
            {
                final byte[] beginEx = MqttFunctions.beginEx()
                    .typeId(mqttTypeId)
                    .publish()
                        .clientId(CLIENT_ID_PREFIX + publisher)
                        .topic("device/" + publisher)
                        .qos(1)
                        .build()
                    .build();
                final Publisher opened = new Publisher();
                opened.begin(bench.newClient(mqttId, opened), new UnsafeBufferEx(beginEx));
            }

            await(() -> idle.size() == publishers, "publish streams opened");

            for (int slot = 0; slot < inflight; slot++)
            {
                idle.poll().publish();
            }
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            bench.close();
        }

        void complete()
        {
            await(acknowledgedAvailable, "publish acknowledged");

            idle.add(acknowledged.poll());
            idle.poll().publish();
        }

        private void await(
            BooleanSupplier condition,
            String description)
        {
            final long deadline = System.nanoTime() + TIMEOUT_NANOS;

            while (!condition.getAsBoolean())
            {
                if (bench.poll() + kafka.release() == 0)
                {
                    Thread.onSpinWait();
                }

                if (System.nanoTime() > deadline)
                {
                    throw new IllegalStateException(String.format(
                        "%s timed out, idle %d acknowledged %d, unexpected kafka streams %d",
                        description, idle.size(), acknowledged.size(), kafka.unexpected()));
                }
            }
        }

        private final class Publisher implements MessageConsumer
        {
            private final WindowFW windowRO = new WindowFW();

            private Client client;
            private long acknowledge = -1L;

            private void begin(
                Client client,
                DirectBufferEx beginEx)
            {
                this.client = client;

                client.begin(beginEx, 0, beginEx.capacity());
                client.replyWindow(DEFAULT_WINDOW);
            }

            private void publish()
            {
                client.data(message, 0, message.capacity(), dataEx, 0, dataEx.capacity());
            }

            @Override
            public void accept(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                switch (msgTypeId)
                {
                case WindowFW.TYPE_ID:
                    final WindowFW window = windowRO.wrap(buffer, index, index + length);

                    if (acknowledge == -1L)
                    {
                        acknowledge = window.acknowledge();
                        idle.add(this);
                    }
                    else if (window.acknowledge() > acknowledge)
                    {
                        acknowledge = window.acknowledge();
                        acknowledged.add(this);
                    }
                    break;
                case AbortFW.TYPE_ID:
                case ResetFW.TYPE_ID:
                    throw new IllegalStateException(String.format(
                        "publish stream rejected, unexpected kafka streams %d", kafka.unexpected()));
                default:
                    break;
                }
            }
        }
    }

    public static void main(
        String[] args) throws RunnerException
    {
        final Options opt = new OptionsBuilder()
            .include(MqttKafkaProxyBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
