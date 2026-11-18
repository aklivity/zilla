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
package io.aklivity.zilla.runtime.binding.mqtt.internal.bench;

import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;
import static java.nio.charset.StandardCharsets.US_ASCII;
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

import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttServerCapabilities;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.Responder;
import io.aklivity.zilla.runtime.engine.test.Responder.Response;
import io.aklivity.zilla.runtime.engine.test.Responder.Trigger;
import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;

/**
 * Drives an mqtt server, speaking MQTT on its network side, through the real engine, in front of a mock
 * application that accepts every session. Each {@code handshake} operation opens a new connection, sends a
 * CONNECT packet with a distinct client id and completes once the CONNACK has arrived, after which the
 * connection is aborted so the next one starts clean. The operation waits only until fewer than
 * {@code inflight} handshakes are outstanding, so {@code inflight} handshakes are in flight at steady state
 * and the {@code inflight} parameter ramps the concurrency.
 * <p>
 * Each {@code leave} operation opens one connection, completes its handshake and leaves the connection open,
 * so the connected session stays live.
 * <p>
 * Read {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler for the allocation per handshake, and
 * {@code retained.heap.norm} (B/op) from {@link RetainedHeapProfiler} for {@code leave}. {@code leave} does not
 * depend on {@code version} or {@code inflight}, so run it with {@code -p version=5 -p inflight=256}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class MqttServerBM
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/mqtt/config/server.yaml";
    private static final int CLIENT_ID_DIGITS = 8;
    private static final int PACKET_SIZE_MAX = 33792;
    private static final int CONNACK_TYPE = 0x20;
    private static final int CONNECTED = 20_000;

    @Benchmark
    public void handshake(
        Cycling fixture)
    {
        fixture.handshake();
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(MILLISECONDS)
    @Warmup(iterations = 1)
    @Measurement(iterations = 3)
    @OperationsPerInvocation(CONNECTED)
    public void leave(
        Retaining fixture)
    {
        for (int connection = 0; connection < CONNECTED; connection++)
        {
            fixture.handshake();
        }

        fixture.drain();
    }

    @State(Scope.Benchmark)
    public abstract static class Fixture
    {
        @Param({"1", "16", "256", "4096"})
        public int inflight;

        @Param({"5", "4"})
        public int version;

        private final Queue<Handshake> idle = new ArrayDeque<>();
        private final Queue<Handshake> completed = new ArrayDeque<>();
        private final BooleanSupplier hasCapacity = () -> closeCompleted() < inflight;
        private final BooleanSupplier drained = () -> closeCompleted() == 0;

        private EngineBench bench;
        private Responder responder;
        private long netId;
        private byte[] packet;
        private UnsafeBufferEx connect;
        private int pending;
        private int sequence;
        private boolean failed;

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("app0")
                .start(CONFIGURATION);

            packet = connectPacket(version);
            connect = new UnsafeBufferEx(packet);
            netId = bench.bindingId("net0");

            responder = new Responder(Trigger.BEGIN, sessionAccepted(bench.labelId("mqtt")));
            bench.newServer("app0", responder);
        }

        public void drain()
        {
            await(drained);
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            final int sessions = responder.counter().begins();

            disconnect();
            bench.close();

            if (sessions == 0 || failed)
            {
                throw new IllegalStateException("sessions did not reach the application: " + sessions);
            }
        }

        abstract void release(
            Handshake handshake);

        void disconnect()
        {
        }

        void handshake()
        {
            await(hasCapacity);

            Handshake handshake = idle.poll();
            if (handshake == null)
            {
                handshake = new Handshake();
            }

            handshake.open();
            pending++;
        }

        private int closeCompleted()
        {
            for (Handshake handshake = completed.poll(); handshake != null; handshake = completed.poll())
            {
                release(handshake);
                pending--;
            }

            return pending;
        }

        private void await(
            BooleanSupplier condition)
        {
            if (!bench.pollUntil(condition) || failed)
            {
                throw new IllegalStateException("handshake did not complete: pending=" + pending + " failed=" + failed +
                    " started=" + sequence +
                    " begins=" + responder.counter().begins() + " datas=" + responder.counter().datas() +
                    " windows=" + responder.counter().windows() + " aborts=" + responder.counter().aborts());
            }
        }

        private void nextClientId()
        {
            int remaining = ++sequence;
            for (int index = packet.length - 1; index >= packet.length - CLIENT_ID_DIGITS; index--)
            {
                packet[index] = (byte) ('0' + remaining % 10);
                remaining /= 10;
            }
        }

        final class Handshake implements MessageConsumer
        {
            private final DataFW dataRO = new DataFW();
            private final MessageConsumer awaitWindow = this::onAwaitWindow;
            private final MessageConsumer awaitConnack = this::onAwaitConnack;

            private Client client;
            private MessageConsumer stage;

            void open()
            {
                stage = awaitWindow;
                client = bench.newClient(netId, this);
                client.begin();
                client.replyWindow(DEFAULT_WINDOW);
            }

            void finish()
            {
                client.abort();
                detach();
            }

            Client detach()
            {
                final Client detached = client;

                stage = MessageConsumer.NOOP;
                client = null;
                idle.offer(this);

                return detached;
            }

            @Override
            public void accept(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                if (msgTypeId == AbortFW.TYPE_ID || msgTypeId == ResetFW.TYPE_ID)
                {
                    failed = true;
                }

                stage.accept(msgTypeId, buffer, index, length);
            }

            private void onAwaitWindow(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                if (msgTypeId == WindowFW.TYPE_ID)
                {
                    stage = awaitConnack;
                    nextClientId();
                    client.data(connect, 0, connect.capacity());
                }
            }

            private void onAwaitConnack(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                if (msgTypeId == DataFW.TYPE_ID)
                {
                    final DataFW data = dataRO.wrap(buffer, index, index + length);

                    if (data.length() > 0)
                    {
                        failed |= (data.payload().buffer().getByte(data.payload().offset()) & 0xff) != CONNACK_TYPE;
                        stage = MessageConsumer.NOOP;
                        completed.offer(this);
                    }
                }
            }
        }
    }

    @State(Scope.Benchmark)
    public static class Cycling extends Fixture
    {
        @TearDown(Level.Iteration)
        public void settle()
        {
            drain();
        }

        @Override
        void release(
            Handshake handshake)
        {
            handshake.finish();
        }
    }

    @State(Scope.Benchmark)
    public static class Retaining extends Fixture
    {
        private final List<Client> connected = new ArrayList<>(CONNECTED * 4);

        @Override
        void disconnect()
        {
            connected.forEach(Client::abort);
        }

        @Override
        void release(
            Handshake handshake)
        {
            connected.add(handshake.detach());
        }
    }

    private static byte[] connectPacket(
        int version)
    {
        final byte[] protocolName = "MQTT".getBytes(US_ASCII);
        final int properties = version == 5 ? 1 : 0;
        final int remaining = 2 + protocolName.length + 1 + 1 + 2 + properties + 2 + CLIENT_ID_DIGITS;
        final byte[] packet = new byte[2 + remaining];

        int offset = 0;
        packet[offset++] = 0x10;
        packet[offset++] = (byte) remaining;
        packet[offset++] = 0x00;
        packet[offset++] = (byte) protocolName.length;
        System.arraycopy(protocolName, 0, packet, offset, protocolName.length);
        offset += protocolName.length;
        packet[offset++] = (byte) version;
        packet[offset++] = 0x02;
        packet[offset++] = 0x00;
        packet[offset++] = 0x3c;
        offset += properties;
        packet[offset++] = 0x00;
        packet[offset] = (byte) CLIENT_ID_DIGITS;

        return packet;
    }

    private static Response sessionAccepted(
        int mqttTypeId)
    {
        final int capabilities =
            1 << MqttServerCapabilities.RETAIN.value() |
            1 << MqttServerCapabilities.WILDCARD.value() |
            1 << MqttServerCapabilities.SUBSCRIPTION_IDS.value() |
            1 << MqttServerCapabilities.SHARED_SUBSCRIPTIONS.value();

        final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);

        final MqttBeginExFW beginEx = new MqttBeginExFW.Builder().wrap(buffer, 0, buffer.capacity())
            .typeId(mqttTypeId)
            .session(s -> s.flags(0x02)
                           .subscribeQosMax(2)
                           .publishQosMax(2)
                           .packetSizeMax(PACKET_SIZE_MAX)
                           .capabilities(capabilities)
                           .clientId((String) null))
            .build();

        final byte[] encoded = new byte[beginEx.sizeof()];
        buffer.getBytes(0, encoded);

        return new Response(encoded, new byte[][] { new byte[0] }, new byte[][] { new byte[0] }, new byte[0], 0);
    }

    public static void main(
        String[] args) throws RunnerException
    {
        final Options opt = new OptionsBuilder()
            .include(MqttServerBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
