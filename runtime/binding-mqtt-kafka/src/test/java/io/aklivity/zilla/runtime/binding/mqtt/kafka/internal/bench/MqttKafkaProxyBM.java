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

import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.PUBLISH_MAX_QOS_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.BooleanSupplier;

import org.agrona.collections.IntArrayQueue;
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

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttFlushExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.specs.binding.kafka.internal.KafkaFunctions;
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
 * <p>
 * Each {@code publishQos2} operation is one completed QoS 2 publish on one of {@code publishers} open {@code mqtt}
 * publish streams, each with its own topic and its own established session, which is the {@code mqtt} session
 * stream the proxy needs to track QoS 2 offsets. The publish is sent with a packet id and is received once the
 * stream's window acknowledges it, which is the PUBREC; the peer then releases it with a flush on the session
 * stream, which is the PUBREL, and the operation completes when the proxy answers with a flush on the session
 * stream, which is the PUBCOMP. {@link KafkaStub} acknowledges the produce stream on the messages topic and the
 * offset commit streams on the sessions topic that the exchange opens, and answers the flush that resolves the
 * partition. {@code inflight}, {@code payload} and {@code ackDelayMillis} are as for {@code publish}, and the
 * sessions and streams are opened before measurement starts.
 * <p>
 * {@code deliver} and {@code fanout} take a {@code qos} of 1 or 2 for the subscription and its deliveries. With a
 * {@code qos} of 2 the peer acknowledges each delivered message with a flush in the incomplete state, which is the
 * PUBREC, and {@link KafkaStub} answers the incomplete offset commit that the proxy sends, as the QoS 2 subscribe
 * scripts specify. The proxy then releases the message with a flush carrying the packet id, which is the PUBREL, and
 * the peer completes it with a flush, which is the PUBCOMP. The operation completes when the proxy commits the final
 * offset, so a QoS 2 delivery is the whole exchange and not the acknowledgement alone. {@link KafkaStub} takes the
 * fetch stream with a group id whose lowest included QoS level equals {@code qos} as the delivery stream of a
 * subscription, so the proxy's other fetch streams are opened and answered but deliver nothing.
 * <p>
 * {@code handshake}, {@code publish} and {@code deliver} take a {@code sessions} count of {@code mqtt} sessions that
 * are established, each with a clean start, a distinct client id and a distinct affinity, and kept open for the whole
 * run. They are established before measurement starts and carry no traffic, so the benchmark shows how the cost of
 * each operation changes with the number of sessions the proxy holds, including the maps it keeps per client.
 * <p>
 * {@code publish} takes a {@code retain} setting: when it is set each publish carries the retain flag, so the proxy
 * also produces it to the retained topic and the publish completes only when both the messages topic and the
 * retained topic have acknowledged it. {@link KafkaStub} acknowledges the produce stream on the retained topic the
 * same way as the one on the messages topic. It also takes an {@code expiry} setting: when it is set each publish
 * carries a message expiry interval, which the proxy produces as a timeout header.
 * <p>
 * Each {@code retainedReplay} operation is one new subscription that is sent the retained message of its topic:
 * the peer opens an {@code mqtt} subscribe stream asking for retained messages, the stub answers the retained
 * fetch streams the way the {@code subscribe.replay.retained.message.qos1} scripts specify, with the retained
 * message on the fetch stream with a group id followed by the caught up flush, the peer acknowledges the message,
 * and the operation completes when the proxy commits its offset. The subscribe stream is then ended, which closes
 * its Kafka streams, and the next subscription begins. {@code depth} is the number of subscriptions kept
 * outstanding, {@code payload} the size of the retained message and {@code ackDelayMillis} the delay of every stub
 * answer.
 * <p>
 * {@code deliverLagged} is {@code deliver} with a subscriber that acknowledges late: it holds each acknowledgement
 * back until {@code ackLag} newer messages have been delivered on the same subscribe stream, so the proxy tracks
 * {@code ackLag} unacknowledged messages for every active subscription. Each subscription is primed with
 * {@code ackLag} plus one messages and each acknowledgement it commits is answered by delivering the next message to
 * the same subscription, which keeps {@code ackLag} messages outstanding in steady state. Subscriptions are primed
 * one at a time so the stub never writes more than one subscription's messages ahead of the proxy; an
 * {@code ackLag} of 0 acknowledges every message at once and is the baseline.
 * <p>
 * Each {@code resume} operation is one persistent session that reconnects and resumes: the peer opens an
 * {@code mqtt} session stream without a clean start, with a client id it used before, and the operation completes
 * when the stored session state reaches it. {@link KafkaStub} answers the session stream of a client with the stored
 * state, a session state of {@code subscriptions} subscriptions in a fetch data extension keyed by the client id,
 * followed by the caught up flush, as the non clean start session scripts specify. The session stream is then ended
 * and the next resume begins, so {@code depth} sessions are kept resuming at once. {@code subscriptions} is the size
 * of the stored session state, which the proxy reads and reconciles on every resume.
 * <p>
 * Each {@code will} operation is one will message delivered to its topic after an abnormal disconnect: the peer opens
 * an {@code mqtt} session stream with a will, a clean start is not asked for, sends its will once the stream's window
 * opens, and aborts the session once the session is established. {@link KafkaStub} then plays the part of the
 * sessions topic as the will scripts specify: it answers the will signal fetch with the caught up flush, and the
 * session stream with the flush that says there is no session state, keeps the will message the proxy produces by its
 * key, echoes each will signal the proxy produces back on the signal stream, and serves the will message on the fetch
 * stream that the proxy opens for it. The will is delivered after its delay, which is one second, on the real clock,
 * so the operation completes about a second after the disconnect, and the operation completes when the will message
 * arrives on the messages topic. {@code depth} is the number of wills kept in flight, so the rate is bounded by
 * {@code depth} operations a second, and {@code ackDelayMillis} the delay of every stub answer. The wills still in
 * flight at the end of the run are delivered before the engine is closed.
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
    private static final int PUBLISH_EXPIRY_SECONDS = 15;
    private static final long TIMEOUT_NANOS = SECONDS.toNanos(10L);
    private static final String CLIENT_ID_PREFIX = "client-";
    private static final int CLIENT_ID_DIGITS = 10;
    private static final long OFFSET_MARKER = 0x0102030405060708L;
    private static final int FANOUT_SUBSCRIBERS = 1000;
    private static final int SHARED_SUBSCRIBERS = 5;
    private static final int EXPIRY_SECONDS = 24 * 60 * 60;
    private static final String SHARE_NAME = "benchmark";
    private static final String SHARED_CONFIGURATION = "io/aklivity/zilla/specs/binding/mqtt/kafka/config/proxy.store.yaml";
    private static final int QOS2_PACKET_ID = 1;
    private static final long WILL_TIMEOUT_NANOS = SECONDS.toNanos(30L);
    private static final long SETTLE_NANOS = SECONDS.toNanos(1L);
    private static final String SESSION_ID_PREFIX = "session-";
    private static final int SESSION_BATCH = 1000;
    private static final String RETAINED_TOPIC = "device/retained";
    private static final String FANOUT_TOPIC = "device/fanout";

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

    @Benchmark
    public void retainedReplay(
        RetainedReplayFixture fixture)
    {
        fixture.complete();
    }

    @Benchmark
    public void publishQos2(
        Qos2PublishFixture fixture)
    {
        fixture.complete();
    }

    @Benchmark
    @OperationsPerInvocation(FANOUT_SUBSCRIBERS)
    public void fanout(
        FanoutFixture fixture)
    {
        fixture.complete();
    }

    @Benchmark
    public void sharedsub(
        SharedFixture fixture)
    {
        fixture.complete();
    }

    @Benchmark
    public void deliver(
        DeliverFixture fixture)
    {
        fixture.complete();
    }

    @Benchmark
    public void will(
        WillFixture fixture)
    {
        fixture.complete();
    }

    @Benchmark
    public void resume(
        ResumeFixture fixture)
    {
        fixture.complete();
    }

    @Benchmark
    public void deliverLagged(
        LaggedDeliverFixture fixture)
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

        @Param({"1", "1000", "10000"})
        public int sessions;

        private final SessionPool sessionPool = new SessionPool();
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

            sessionPool.establish(bench, kafka, sessions);

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

        @Param({"1", "1000", "10000"})
        public int sessions;

        @Param({"false", "true"})
        public boolean retain;

        @Param({"false", "true"})
        public boolean expiry;

        private final SessionPool sessionPool = new SessionPool();
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

            sessionPool.establish(bench, kafka, sessions);

            final int mqttTypeId = bench.labelId("mqtt");
            final long mqttId = bench.bindingId("mqtt0");

            final String[] publishFlags = retain ? new String[] {"RETAIN"} : new String[0];

            dataEx = new UnsafeBufferEx(MqttFunctions.dataEx()
                .typeId(mqttTypeId)
                .publish()
                    .qos("AT_LEAST_ONCE")
                    .flags(publishFlags)
                    .packetId(1)
                    .expiryInterval(expiry ? PUBLISH_EXPIRY_SECONDS : -1)
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
                        .flags(publishFlags)
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

    @State(Scope.Benchmark)
    public static class Qos2PublishFixture
    {
        @Param({"1000"})
        public int publishers;

        @Param({"1", "256"})
        public int inflight;

        @Param({"16", "1024"})
        public int payload;

        @Param({"0", "5"})
        public long ackDelayMillis;

        private final Queue<Qos2Publisher> idle = new ArrayDeque<>();
        private final Queue<Qos2Publisher> completed = new ArrayDeque<>();
        private final BooleanSupplier completedAvailable = () -> !completed.isEmpty();

        private EngineBench bench;
        private KafkaStub kafka;
        private DirectBufferEx dataEx;
        private DirectBufferEx releaseEx;
        private DirectBufferEx message;
        private int sessions;
        private int opened;

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
                    .qos("EXACTLY_ONCE")
                    .packetId(QOS2_PACKET_ID)
                    .build()
                .build());
            releaseEx = new UnsafeBufferEx(MqttFunctions.flushEx()
                .typeId(mqttTypeId)
                .session()
                    .packetId(QOS2_PACKET_ID)
                    .build()
                .build());
            message = new UnsafeBufferEx(new byte[payload]);

            final List<Qos2Publisher> connecting = new ArrayList<>();

            for (int publisher = 0; publisher < publishers; publisher++)
            {
                final byte[] beginEx = MqttFunctions.beginEx()
                    .typeId(mqttTypeId)
                    .session()
                        .flags("CLEAN_START")
                        .expiry(1)
                        .publishQosMax(2)
                        .clientId(CLIENT_ID_PREFIX + publisher)
                        .build()
                    .build();
                final Qos2Publisher connected = new Qos2Publisher(publisher);
                connected.connect(bench.newClient(mqttId, connected.sessionConsumer), new UnsafeBufferEx(beginEx));
                connecting.add(connected);
            }

            await(() -> sessions == publishers, "sessions established");

            for (Qos2Publisher connected : connecting)
            {
                final byte[] beginEx = MqttFunctions.beginEx()
                    .typeId(mqttTypeId)
                    .publish()
                        .clientId(CLIENT_ID_PREFIX + connected.index)
                        .topic("device/" + connected.index)
                        .qos(2)
                        .build()
                    .build();
                connected.open(bench.newClient(mqttId, connected.publishConsumer), new UnsafeBufferEx(beginEx));
            }

            await(() -> opened == publishers, "publish streams opened");

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
            await(completedAvailable, "publish completed");

            idle.add(completed.poll());
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
                        "%s timed out, sessions %d opened %d idle %d completed %d, unexpected kafka streams %d",
                        description, sessions, opened, idle.size(), completed.size(), kafka.unexpected()));
                }
            }
        }

        private final class Qos2Publisher
        {
            private final MessageConsumer sessionConsumer = this::onSession;
            private final MessageConsumer publishConsumer = this::onPublish;
            private final WindowFW windowRO = new WindowFW();
            private final int index;

            private Client session;
            private Client publish;
            private long acknowledge = -1L;

            private Qos2Publisher(
                int index)
            {
                this.index = index;
            }

            private void connect(
                Client session,
                DirectBufferEx beginEx)
            {
                this.session = session;

                session.begin(index + 1L, beginEx, 0, beginEx.capacity());
                session.replyWindow(DEFAULT_WINDOW);
            }

            private void open(
                Client publish,
                DirectBufferEx beginEx)
            {
                this.publish = publish;

                publish.begin(index + 1L, beginEx, 0, beginEx.capacity());
                publish.replyWindow(DEFAULT_WINDOW);
            }

            private void publish()
            {
                publish.data(message, 0, message.capacity(), dataEx, 0, dataEx.capacity());
            }

            private void onSession(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                switch (msgTypeId)
                {
                case DataFW.TYPE_ID:
                    sessions++;
                    break;
                case FlushFW.TYPE_ID:
                    completed.add(this);
                    break;
                case AbortFW.TYPE_ID:
                case ResetFW.TYPE_ID:
                    throw new IllegalStateException(String.format(
                        "session stream rejected, unexpected kafka streams %d", kafka.unexpected()));
                default:
                    break;
                }
            }

            private void onPublish(
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
                        opened++;
                        idle.add(this);
                    }
                    else if (window.acknowledge() > acknowledge)
                    {
                        acknowledge = window.acknowledge();
                        session.flush(releaseEx, 0, releaseEx.capacity());
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

    @State(Scope.Benchmark)
    public static class RetainedReplayFixture
    {
        @Param({"1", "256"})
        public int depth;

        @Param({"16", "1024"})
        public int payload;

        @Param({"0", "5"})
        public long ackDelayMillis;

        private final Queue<Replay> acknowledged = new ArrayDeque<>();
        private final Queue<Replay> idle = new ArrayDeque<>();

        private EngineBench bench;
        private KafkaStub kafka;
        private BooleanSupplier replayCommitted;
        private long mqttId;
        private int mqttTypeId;
        private DirectBufferEx ackEx;
        private int ackPacketIdOffset;
        private int replays;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("kafka0")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .start(CONFIGURATION);

            kafka = new KafkaStub(bench.labelId("kafka"), ackDelayMillis, KafkaStub.NO_DELIVERY);
            kafka.retainedReplay(RETAINED_TOPIC, new UnsafeBufferEx(new byte[payload]));
            bench.newServer("kafka0", kafka);

            replayCommitted = kafka::replayCommitted;

            mqttId = bench.bindingId("mqtt0");
            mqttTypeId = bench.labelId("mqtt");

            final byte[] ackTemplate = MqttFunctions.flushEx()
                .typeId(mqttTypeId)
                .subscribe()
                    .qos("AT_LEAST_ONCE")
                    .packetId(SubscribeFixture.ACK_PACKET_ID_MARKER)
                    .build()
                .build();
            ackPacketIdOffset = SubscribeFixture.indexOfShort(ackTemplate, (short) SubscribeFixture.ACK_PACKET_ID_MARKER);
            ackEx = new UnsafeBufferEx(ackTemplate);

            for (int slot = 0; slot < depth; slot++)
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

            while (!replayCommitted.getAsBoolean())
            {
                if (bench.poll() + kafka.release() == 0)
                {
                    Thread.onSpinWait();
                }

                if (System.nanoTime() > deadline)
                {
                    throw new IllegalStateException(String.format(
                        "retained message replay timed out, acknowledged %d, unexpected kafka streams %d",
                        acknowledged.size(), kafka.unexpected()));
                }
            }

            acknowledged.poll().end();
            begin();
        }

        private void begin()
        {
            Replay replay = idle.poll();

            if (replay == null)
            {
                replay = new Replay(++replays);
            }

            replay.begin();
        }

        private final class Replay implements MessageConsumer
        {
            private final DataFW dataRO = new DataFW();
            private final MqttDataExFW dataExRO = new MqttDataExFW();
            private final byte[] ackBytes = new byte[ackEx.capacity()];
            private final MutableDirectBufferEx ack = new UnsafeBufferEx(ackBytes);
            private final DirectBufferEx beginEx;

            private Client client;

            private Replay(
                int index)
            {
                this.beginEx = new UnsafeBufferEx(MqttFunctions.beginEx()
                    .typeId(mqttTypeId)
                    .subscribe()
                        .clientId(CLIENT_ID_PREFIX + index)
                        .qos("AT_LEAST_ONCE")
                        .filter(RETAINED_TOPIC, 1, "AT_LEAST_ONCE", "SEND_RETAINED", "RETAIN_AS_PUBLISHED")
                        .build()
                    .build());
                ackEx.getBytes(0, ackBytes);
            }

            private void begin()
            {
                client = bench.newClient(mqttId, this);

                client.begin(beginEx, 0, beginEx.capacity());
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
                    final DataFW data = dataRO.wrap(buffer, index, index + length);
                    final MqttDataExFW mqttDataEx = dataExRO.wrap(data.extension().buffer(), data.extension().offset(),
                        data.extension().limit());
                    ack.putShort(ackPacketIdOffset, (short) mqttDataEx.subscribe().packetId(), ByteOrder.LITTLE_ENDIAN);
                    client.flush(ack, 0, ack.capacity());
                    acknowledged.add(this);
                    break;
                case EndFW.TYPE_ID:
                    idle.add(this);
                    break;
                case AbortFW.TYPE_ID:
                case ResetFW.TYPE_ID:
                    throw new IllegalStateException(String.format(
                        "retained subscribe stream rejected, unexpected kafka streams %d", kafka.unexpected()));
                default:
                    break;
                }
            }
        }
    }

    @State(Scope.Benchmark)
    public static class ResumeFixture
    {
        @Param({"1", "256"})
        public int depth;

        @Param({"1", "100"})
        public int subscriptions;

        @Param({"0", "5"})
        public long ackDelayMillis;

        private final Queue<Resumption> established = new ArrayDeque<>();
        private final Queue<Resumption> idle = new ArrayDeque<>();

        private EngineBench bench;
        private KafkaStub kafka;
        private long mqttId;
        private int mqttTypeId;
        private int resumptions;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("kafka0")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .start(CONFIGURATION);

            kafka = new KafkaStub(bench.labelId("kafka"), ackDelayMillis, KafkaStub.NO_DELIVERY);

            final MqttFunctions.MqttSessionStateBuilder sessionState = MqttFunctions.session();
            for (int subscription = 0; subscription < subscriptions; subscription++)
            {
                sessionState.subscription("device/" + subscription, subscription + 1);
            }
            kafka.sessionState(CLIENT_ID_PREFIX.length() + CLIENT_ID_DIGITS, new UnsafeBufferEx(sessionState.build()));
            bench.newServer("kafka0", kafka);

            mqttId = bench.bindingId("mqtt0");
            mqttTypeId = bench.labelId("mqtt");

            for (int slot = 0; slot < depth; slot++)
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
                        "session resume timed out, unexpected kafka streams %d", kafka.unexpected()));
                }
            }

            established.poll().end();
            begin();
        }

        private void begin()
        {
            Resumption resumption = idle.poll();

            if (resumption == null)
            {
                resumption = new Resumption(++resumptions);
            }

            resumption.begin();
        }

        private final class Resumption implements MessageConsumer
        {
            private final DataFW dataRO = new DataFW();
            private final DirectBufferEx beginEx;

            private Client client;

            private Resumption(
                int number)
            {
                this.beginEx = new UnsafeBufferEx(MqttFunctions.beginEx()
                    .typeId(mqttTypeId)
                    .session()
                        .expiry(1)
                        .clientId(CLIENT_ID_PREFIX + String.format("%0" + CLIENT_ID_DIGITS + "d", number))
                        .build()
                    .build());
            }

            private void begin()
            {
                client = bench.newClient(mqttId, this);

                client.begin(beginEx, 0, beginEx.capacity());
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
                    final DataFW data = dataRO.wrap(buffer, index, index + length);

                    if (data.length() > 0)
                    {
                        established.add(this);
                    }
                    break;
                case EndFW.TYPE_ID:
                    idle.add(this);
                    break;
                case AbortFW.TYPE_ID:
                case ResetFW.TYPE_ID:
                    throw new IllegalStateException(String.format(
                        "session resume rejected, unexpected kafka streams %d", kafka.unexpected()));
                default:
                    break;
                }
            }
        }
    }

    @State(Scope.Benchmark)
    public static class WillFixture
    {
        private static final String WILL_TOPIC = "obituaries";
        private static final String WILL_MESSAGE = "disconnected abruptly";

        @Param({"1", "256"})
        public int depth;

        @Param({"0", "5"})
        public long ackDelayMillis;

        private final Queue<Dying> idle = new ArrayDeque<>();

        private EngineBench bench;
        private KafkaStub kafka;
        private long mqttId;
        private int mqttTypeId;
        private DirectBufferEx willDataEx;
        private DirectBufferEx will;
        private int dyings;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("kafka0")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .configure(PUBLISH_MAX_QOS_NAME, "1")
                .start(CONFIGURATION);

            kafka = new KafkaStub(bench.labelId("kafka"), ackDelayMillis, KafkaStub.NO_DELIVERY);
            kafka.willDelivery(WILL_TOPIC);
            bench.newServer("kafka0", kafka);

            mqttId = bench.bindingId("mqtt0");
            mqttTypeId = bench.labelId("mqtt");

            willDataEx = new UnsafeBufferEx(MqttFunctions.dataEx()
                .typeId(mqttTypeId)
                .session()
                    .kind("WILL")
                    .build()
                .build());

            final byte[] willMessage = WILL_MESSAGE.getBytes(UTF_8);
            final byte[] willHeader = MqttFunctions.will()
                .topic(WILL_TOPIC)
                .delay(1)
                .expiryInterval(15)
                .format("TEXT")
                .responseTopic("responses/client1")
                .correlation("info")
                .payloadSize(willMessage.length)
                .build();
            final byte[] willBytes = new byte[willHeader.length + willMessage.length];
            System.arraycopy(willHeader, 0, willBytes, 0, willHeader.length);
            System.arraycopy(willMessage, 0, willBytes, willHeader.length, willMessage.length);
            will = new UnsafeBufferEx(willBytes);

            for (int slot = 0; slot < depth; slot++)
            {
                begin();
            }
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            for (int outstanding = 0; outstanding < depth; outstanding++)
            {
                awaitDelivered();
            }

            final long settled = System.nanoTime() + SETTLE_NANOS;
            while (System.nanoTime() < settled)
            {
                bench.poll();
                kafka.release();
            }

            bench.close();
        }

        void complete()
        {
            awaitDelivered();
            begin();
        }

        private void awaitDelivered()
        {
            final long deadline = System.nanoTime() + WILL_TIMEOUT_NANOS;

            while (!kafka.willDelivered())
            {
                if (bench.poll() + kafka.release() == 0)
                {
                    Thread.onSpinWait();
                }

                if (System.nanoTime() > deadline)
                {
                    throw new IllegalStateException(String.format(
                        "will delivery timed out, unexpected kafka streams %d", kafka.unexpected()));
                }
            }
        }

        private void begin()
        {
            Dying dying = idle.poll();

            if (dying == null)
            {
                dying = new Dying(++dyings);
            }

            dying.begin();
        }

        private final class Dying implements MessageConsumer
        {
            private final DirectBufferEx beginEx;

            private Client client;
            private MessageConsumer decoder;

            private Dying(
                int number)
            {
                this.beginEx = new UnsafeBufferEx(MqttFunctions.beginEx()
                    .typeId(mqttTypeId)
                    .session()
                        .flags("WILL")
                        .expiry(1)
                        .clientId(CLIENT_ID_PREFIX + String.format("%0" + CLIENT_ID_DIGITS + "d", number))
                        .build()
                    .build());
            }

            private void begin()
            {
                client = bench.newClient(mqttId, this);
                decoder = this::decodeAwaitWindow;

                client.begin(beginEx, 0, beginEx.capacity());
                client.replyWindow(DEFAULT_WINDOW);
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
                case AbortFW.TYPE_ID:
                    idle.add(this);
                    break;
                case ResetFW.TYPE_ID:
                    throw new IllegalStateException(String.format(
                        "will session rejected, unexpected kafka streams %d", kafka.unexpected()));
                default:
                    decoder.accept(msgTypeId, buffer, index, length);
                    break;
                }
            }

            private void decodeAwaitWindow(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                if (msgTypeId == WindowFW.TYPE_ID)
                {
                    decoder = this::decodeAwaitEstablished;
                    client.data(will, 0, will.capacity(), willDataEx, 0, willDataEx.capacity());
                }
            }

            private void decodeAwaitEstablished(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                if (msgTypeId == DataFW.TYPE_ID)
                {
                    decoder = this::decodeIgnore;
                    client.abort();
                }
            }

            private void decodeIgnore(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
            }
        }
    }

    public abstract static class SubscribeFixture
    {
        static final int ACK_PACKET_ID_MARKER = 0x7e57;

        @Param({"16", "1024"})
        public int payload;

        @Param({"0", "5"})
        public long ackDelayMillis;

        final Queue<Subscriber> idle = new ArrayDeque<>();
        final List<Subscriber> opened = new ArrayList<>();

        EngineBench bench;
        KafkaStub kafka;
        BooleanSupplier committedAvailable;
        private DirectBufferEx ackEx;
        private int ackPacketIdOffset;
        private DirectBufferEx completeEx;
        private int completePacketIdOffset;
        int established;
        int committed;

        abstract int subscribers();

        abstract int qos();

        int sessions()
        {
            return 0;
        }

        int ackLag()
        {
            return 0;
        }

        abstract String configuration();

        String qosName()
        {
            return qos() == 2 ? "EXACTLY_ONCE" : "AT_LEAST_ONCE";
        }

        abstract byte[] subscribeBeginEx(
            int mqttTypeId,
            int subscriber);

        abstract byte[] dataEx(
            int kafkaTypeId,
            int subscriber);

        abstract void prime();

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("kafka0")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .start(configuration());

            kafka = new KafkaStub(bench.labelId("kafka"), ackDelayMillis, qos());
            bench.newServer("kafka0", kafka);

            new SessionPool().establish(bench, kafka, sessions());

            committedAvailable = () -> (committed = kafka.committed()) != -1;

            final int mqttTypeId = bench.labelId("mqtt");
            final int kafkaTypeId = bench.labelId("kafka");
            final long mqttId = bench.bindingId("mqtt0");

            final byte[] ackTemplate = qos() == 2
                ? MqttFunctions.flushEx()
                    .typeId(mqttTypeId)
                    .subscribe()
                        .qos(qosName())
                        .packetId(ACK_PACKET_ID_MARKER)
                        .state("INCOMPLETE")
                        .build()
                    .build()
                : MqttFunctions.flushEx()
                    .typeId(mqttTypeId)
                    .subscribe()
                        .qos(qosName())
                        .packetId(ACK_PACKET_ID_MARKER)
                        .build()
                    .build();
            ackPacketIdOffset = indexOfShort(ackTemplate, (short) ACK_PACKET_ID_MARKER);
            ackEx = new UnsafeBufferEx(ackTemplate);

            if (qos() == 2)
            {
                final byte[] completeTemplate = MqttFunctions.flushEx()
                    .typeId(mqttTypeId)
                    .subscribe()
                        .qos(qosName())
                        .packetId(ACK_PACKET_ID_MARKER)
                        .build()
                    .build();
                completePacketIdOffset = indexOfShort(completeTemplate, (short) ACK_PACKET_ID_MARKER);
                completeEx = new UnsafeBufferEx(completeTemplate);
            }

            final DirectBufferEx message = new UnsafeBufferEx(new byte[payload]);

            for (int subscriber = 0; subscriber < subscribers(); subscriber++)
            {
                final byte[] beginEx = subscribeBeginEx(mqttTypeId, subscriber);

                final Subscriber subscribed = new Subscriber(subscriber);
                subscribed.begin(bench.newClient(mqttId, subscribed), new UnsafeBufferEx(beginEx));
                await(() -> established > subscribed.index, "subscribe stream established");

                kafka.prepare(subscriber, dataEx(kafkaTypeId, subscriber), OFFSET_MARKER, message);

                opened.add(subscribed);
                idle.add(subscribed);
            }

            prime();
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            bench.close();
        }

        void await(
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
                        "%s timed out, established %d, unexpected kafka streams %d",
                        description, established, kafka.unexpected()));
                }
            }
        }

        static int indexOfShort(
            byte[] array,
            short value)
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(array);
            int index = -1;

            for (int offset = 0; index == -1 && offset + Short.BYTES <= array.length; offset++)
            {
                if (buffer.getShort(offset, ByteOrder.LITTLE_ENDIAN) == value)
                {
                    index = offset;
                }
            }

            return index;
        }

        private final class Subscriber implements MessageConsumer
        {
            private final DataFW dataRO = new DataFW();
            private final FlushFW flushRO = new FlushFW();
            private final MqttDataExFW dataExRO = new MqttDataExFW();
            private final MqttFlushExFW flushExRO = new MqttFlushExFW();
            private final byte[] ackBytes = new byte[ackEx.capacity()];
            private final MutableDirectBufferEx ack = new UnsafeBufferEx(ackBytes);
            private final IntArrayQueue unacknowledged = new IntArrayQueue();
            private final int ackLag = ackLag();
            private final byte[] completeBytes = new byte[completeEx != null ? completeEx.capacity() : 0];
            private final MutableDirectBufferEx complete = new UnsafeBufferEx(completeBytes);
            private final int index;

            private Client client;

            private Subscriber(
                int index)
            {
                this.index = index;
                ackEx.getBytes(0, ackBytes);

                if (completeEx != null)
                {
                    completeEx.getBytes(0, completeBytes);
                }
            }

            private void begin(
                Client client,
                DirectBufferEx beginEx)
            {
                this.client = client;

                client.begin(beginEx, 0, beginEx.capacity());
                client.replyWindow(DEFAULT_WINDOW);
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
                case BeginFW.TYPE_ID:
                    established++;
                    break;
                case DataFW.TYPE_ID:
                    final DataFW data = dataRO.wrap(buffer, index, index + length);
                    final MqttDataExFW mqttDataEx = dataExRO.wrap(data.extension().buffer(), data.extension().offset(),
                        data.extension().limit());
                    unacknowledged.addInt(mqttDataEx.subscribe().packetId());

                    if (unacknowledged.size() > ackLag)
                    {
                        ack.putShort(ackPacketIdOffset, (short) unacknowledged.pollInt(), ByteOrder.LITTLE_ENDIAN);
                        client.flush(ack, 0, ack.capacity());
                    }
                    break;
                case FlushFW.TYPE_ID:
                    final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                    final MqttFlushExFW mqttFlushEx = flushExRO.tryWrap(flush.extension().buffer(), flush.extension().offset(),
                        flush.extension().limit());

                    if (completeEx != null && mqttFlushEx != null)
                    {
                        complete.putShort(completePacketIdOffset, (short) mqttFlushEx.subscribe().packetId(),
                            ByteOrder.LITTLE_ENDIAN);
                        client.flush(complete, 0, complete.capacity());
                    }
                    break;
                case AbortFW.TYPE_ID:
                case ResetFW.TYPE_ID:
                    throw new IllegalStateException(String.format(
                        "subscribe stream rejected, unexpected kafka streams %d", kafka.unexpected()));
                default:
                    break;
                }
            }
        }

    }

    @State(Scope.Benchmark)
    public static class DeliverFixture extends SubscribeFixture
    {
        @Param({"1000"})
        public int subscribers;

        @Param({"1", "256"})
        public int inflight;

        @Param({"qos", "format", "expiry"})
        public String headers;

        @Param({"1", "2"})
        public int qos;

        @Param({"1", "1000", "10000"})
        public int sessions;

        @Override
        int subscribers()
        {
            return subscribers;
        }

        @Override
        int sessions()
        {
            return sessions;
        }

        @Override
        int qos()
        {
            return qos;
        }

        String topic(
            int subscriber)
        {
            return "device/" + subscriber;
        }

        @Override
        String configuration()
        {
            return CONFIGURATION;
        }

        @Override
        byte[] subscribeBeginEx(
            int mqttTypeId,
            int subscriber)
        {
            final String topic = topic(subscriber);

            return MqttFunctions.beginEx()
                .typeId(mqttTypeId)
                .subscribe()
                    .clientId(CLIENT_ID_PREFIX + subscriber)
                    .qos(qosName())
                    .filter(topic, 1, qosName())
                    .build()
                .build();
        }

        @Override
        byte[] dataEx(
            int kafkaTypeId,
            int subscriber)
        {
            return fetchDataEx(kafkaTypeId, topic(subscriber), qos, headers);
        }

        @Override
        void prime()
        {
            for (int slot = 0; slot < inflight; slot++)
            {
                kafka.deliver(idle.poll().index);
            }
        }

        void complete()
        {
            await(committedAvailable, "message delivered and acknowledged");

            idle.add(opened.get(committed));
            kafka.deliver(idle.poll().index);
        }
    }

    @State(Scope.Benchmark)
    public static class LaggedDeliverFixture extends DeliverFixture
    {
        @Param({"0", "100", "1000"})
        public int ackLag;

        @Override
        int ackLag()
        {
            return ackLag;
        }

        @Override
        void prime()
        {
            for (int slot = 0; slot < inflight; slot++)
            {
                final int subscriber = idle.poll().index;

                for (int message = 0; message <= ackLag; message++)
                {
                    kafka.deliver(subscriber);
                }

                complete();
            }
        }

        @Override
        void complete()
        {
            await(committedAvailable, "message delivered and acknowledged");

            kafka.deliver(committed);
        }
    }

    @State(Scope.Benchmark)
    public static class FanoutFixture extends SubscribeFixture
    {
        @Param({"qos", "format", "expiry"})
        public String headers;

        @Param({"1", "2"})
        public int qos;

        @Override
        int subscribers()
        {
            return FANOUT_SUBSCRIBERS;
        }

        @Override
        int qos()
        {
            return qos;
        }

        String topic(
            int subscriber)
        {
            return FANOUT_TOPIC;
        }

        @Override
        String configuration()
        {
            return CONFIGURATION;
        }

        @Override
        byte[] subscribeBeginEx(
            int mqttTypeId,
            int subscriber)
        {
            final String topic = topic(subscriber);

            return MqttFunctions.beginEx()
                .typeId(mqttTypeId)
                .subscribe()
                    .clientId(CLIENT_ID_PREFIX + subscriber)
                    .qos(qosName())
                    .filter(topic, 1, qosName())
                    .build()
                .build();
        }

        @Override
        byte[] dataEx(
            int kafkaTypeId,
            int subscriber)
        {
            return fetchDataEx(kafkaTypeId, topic(subscriber), qos, headers);
        }

        @Override
        void prime()
        {
        }

        void complete()
        {
            for (int subscriber = 0; subscriber < FANOUT_SUBSCRIBERS; subscriber++)
            {
                kafka.deliver(subscriber);
            }

            for (int delivery = 0; delivery < FANOUT_SUBSCRIBERS; delivery++)
            {
                await(committedAvailable, "message delivered and acknowledged");
            }
        }
    }

    @State(Scope.Benchmark)
    public static class SharedFixture extends SubscribeFixture
    {
        @Param({"1", "5"})
        public int inflight;

        @Override
        int subscribers()
        {
            return SHARED_SUBSCRIBERS;
        }

        @Override
        int qos()
        {
            return 1;
        }

        @Override
        String configuration()
        {
            return SHARED_CONFIGURATION;
        }

        @Override
        byte[] subscribeBeginEx(
            int mqttTypeId,
            int subscriber)
        {
            return MqttFunctions.beginEx()
                .typeId(mqttTypeId)
                .subscribe()
                    .clientId(CLIENT_ID_PREFIX + subscriber)
                    .qos("AT_LEAST_ONCE")
                    .sharedFilter(SHARE_NAME, "device/#", 1, "AT_LEAST_ONCE")
                    .build()
                .build();
        }

        @Override
        byte[] dataEx(
            int kafkaTypeId,
            int subscriber)
        {
            return KafkaFunctions.dataEx()
                .typeId(kafkaTypeId)
                .share()
                    .timestamp(1L)
                    .deliveryCount(1)
                    .partition(0, OFFSET_MARKER)
                    .key("device/" + subscriber)
                    .header("zilla:qos", "1")
                    .build()
                .build();
        }

        @Override
        void prime()
        {
            for (int slot = 0; slot < inflight; slot++)
            {
                kafka.deliver(idle.poll().index);
            }
        }

        void complete()
        {
            await(committedAvailable, "message delivered and acknowledged");

            idle.add(opened.get(committed));
            kafka.deliver(idle.poll().index);
        }
    }

    private static byte[] fetchDataEx(
        int kafkaTypeId,
        String topic,
        int qos,
        String headers)
    {
        final var fetch = KafkaFunctions.dataEx()
            .typeId(kafkaTypeId)
            .merged()
                .fetch()
                    .timestamp(System.currentTimeMillis())
                    .filters(1L)
                    .partition(0, OFFSET_MARKER, OFFSET_MARKER)
                    .key(topic)
                    .header("zilla:qos", Integer.toString(qos));

        if ("format".equals(headers))
        {
            fetch.header("zilla:format", "TEXT");
        }
        else if ("expiry".equals(headers))
        {
            fetch.headerInt("zilla:expiry", EXPIRY_SECONDS);
        }

        return fetch.build().build();
    }

    private static final class SessionPool
    {
        private final List<PooledSession> pooled = new ArrayList<>();

        private int established;

        private void establish(
            EngineBench bench,
            KafkaStub kafka,
            int count)
        {
            final int mqttTypeId = bench.labelId("mqtt");
            final long mqttId = bench.bindingId("mqtt0");

            for (int session = 0; session < count; session++)
            {
                final byte[] beginEx = MqttFunctions.beginEx()
                    .typeId(mqttTypeId)
                    .session()
                        .flags("CLEAN_START")
                        .expiry(1)
                        .publishQosMax(2)
                        .clientId(SESSION_ID_PREFIX + session)
                        .build()
                    .build();

                final PooledSession connecting = new PooledSession(kafka);
                connecting.begin(bench.newClient(mqttId, connecting), session + 1L, new UnsafeBufferEx(beginEx));
                pooled.add(connecting);

                await(bench, kafka, pooled.size() - SESSION_BATCH);
            }

            await(bench, kafka, count);
        }

        private void await(
            EngineBench bench,
            KafkaStub kafka,
            int count)
        {
            final long deadline = System.nanoTime() + TIMEOUT_NANOS;

            while (established < count)
            {
                if (bench.poll() + kafka.release() == 0)
                {
                    Thread.onSpinWait();
                }

                if (System.nanoTime() > deadline)
                {
                    throw new IllegalStateException(String.format(
                        "sessions established timed out, established %d of %d, unexpected kafka streams %d",
                        established, count, kafka.unexpected()));
                }
            }
        }

        private final class PooledSession implements MessageConsumer
        {
            private final KafkaStub kafka;

            private PooledSession(
                KafkaStub kafka)
            {
                this.kafka = kafka;
            }

            private void begin(
                Client client,
                long affinity,
                DirectBufferEx beginEx)
            {
                client.begin(affinity, beginEx, 0, beginEx.capacity());
                client.replyWindow(DEFAULT_WINDOW);
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
                    established++;
                    break;
                case AbortFW.TYPE_ID:
                case ResetFW.TYPE_ID:
                    throw new IllegalStateException(String.format(
                        "session stream rejected, unexpected kafka streams %d", kafka.unexpected()));
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
