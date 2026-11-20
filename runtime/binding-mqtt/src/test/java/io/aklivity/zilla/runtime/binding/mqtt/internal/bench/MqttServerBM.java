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
import java.util.Arrays;
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
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
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
 * Each {@code publish} operation sends one QoS 1 PUBLISH, with a payload of {@code payload} bytes, on one of the
 * connected sessions, each with its own topic, and completes once the PUBACK has arrived. The sessions are
 * connected, and each has published once, before measurement starts. The application keeps every session
 * open, and acknowledges the messages published on each topic. With MQTT 5 and {@code properties=all} each
 * message also carries a content type, a response topic, correlation data and a user property.
 * <p>
 * Each {@code subscribe} operation subscribes one of the connected sessions to its own topic filter, waits for the
 * SUBACK, then unsubscribes and waits for the UNSUBACK, so the session is back where it started for the next
 * operation. The application echoes the session state it receives and accepts the subscription stream.
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
    private static final int PUBACK_TYPE = 0x40;
    private static final int CONNECTED = 2_000;
    private static final int SUBACK_TYPE = 0x90;
    private static final int PUBLISH_TYPE = 0x32;
    private static final UnsafeBufferEx PUBACK = new UnsafeBufferEx(new byte[] {0x40, 0x02, 0x00, 0x01});
    private static final int UNSUBACK_TYPE = 0xb0;
    private static final int CLIENTS = 1_000;
    private static final int SUBSCRIBE_PACKET = 0x82;
    private static final int UNSUBSCRIBE_PACKET = 0xa2;

    @Benchmark
    public void handshake(
        Cycling fixture)
    {
        fixture.handshake();
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(MILLISECONDS)
    @Warmup(iterations = 2)
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

    @Benchmark
    public void publish(
        Publishing fixture)
    {
        fixture.operate();
    }

    @Benchmark
    public void subscribe(
        Subscribing fixture)
    {
        fixture.operate();
    }

    @Benchmark
    public void deliver(
        Delivering fixture)
    {
        fixture.operate();
    }

    @Benchmark
    public void fanout(
        Fanning fixture)
    {
        fixture.operate();
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
        final BooleanSupplier hasCapacity = () -> closeCompleted() < inflight;
        private final BooleanSupplier drained = () -> closeCompleted() == 0;

        private EngineBench bench;
        private Responder sessions;
        private Responder publications;
        Deliverer deliverer;
        private long netId;
        private byte[] packet;
        private UnsafeBufferEx connect;
        int pending;
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

            sessions = new Responder(Trigger.BEGIN, sessionAccepted(bench.labelId("mqtt")));
            publications = new Responder(Trigger.BEGIN, null);
            deliverer = new Deliverer(bench.labelId("mqtt"));
            bench.newServer("app0", new Application(sessions, publications, deliverer));

            prepare();
        }

        public void drain()
        {
            await(drained);
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            final int begins = sessions.counter().begins();

            disconnect();
            bench.close();

            if (begins == 0 || failed)
            {
                throw new IllegalStateException("sessions did not reach the application: " + begins);
            }
        }

        abstract void release(
            Handshake handshake);

        void prepare()
        {
        }

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

        void await(
            BooleanSupplier condition)
        {
            if (!bench.pollUntil(condition) || failed)
            {
                throw new IllegalStateException("handshake did not complete: pending=" + pending + " failed=" + failed +
                    " started=" + sequence +
                    " begins=" + sessions.counter().begins() + " datas=" + sessions.counter().datas() +
                    " windows=" + sessions.counter().windows() + " aborts=" + sessions.counter().aborts() +
                    " publishes: begins=" + publications.counter().begins() + " datas=" + publications.counter().datas() +
                    " aborts=" + publications.counter().aborts() + " resets=" + publications.counter().resets() +
                    " sessions: ends=" + sessions.counter().ends() + " resets=" + sessions.counter().resets());
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
            private final MessageConsumer awaitPuback = this::onAwaitPuback;
            private final MessageConsumer awaitSuback = this::onAwaitSuback;
            private final MessageConsumer awaitUnsuback = this::onAwaitUnsuback;
            private final MessageConsumer awaitSubscribed = this::onAwaitSubscribed;
            private final MessageConsumer awaitPublish = this::onAwaitPublish;

            int connection = -1;
            int number;
            UnsafeBufferEx publication;
            UnsafeBufferEx subscription;
            UnsafeBufferEx unsubscription;

            private Client client;
            private MessageConsumer stage;

            void open()
            {
                stage = awaitWindow;
                client = bench.newClient(netId, this);
                client.begin();
                client.replyWindow(DEFAULT_WINDOW);
            }

            void publish()
            {
                stage = awaitPuback;
                client.data(publication, 0, publication.capacity());
            }

            void subscribe()
            {
                stage = awaitSuback;
                client.data(subscription, 0, subscription.capacity());
            }

            void subscribeOnly()
            {
                stage = awaitSubscribed;
                client.data(subscription, 0, subscription.capacity());
            }

            void expectPublish()
            {
                stage = awaitPublish;
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
                    number = sequence;
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

            private void onAwaitSuback(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                if (arrived(msgTypeId, buffer, index, length, SUBACK_TYPE))
                {
                    stage = awaitUnsuback;
                    client.data(unsubscription, 0, unsubscription.capacity());
                }
            }

            private void onAwaitUnsuback(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                if (arrived(msgTypeId, buffer, index, length, UNSUBACK_TYPE))
                {
                    stage = MessageConsumer.NOOP;
                    completed.offer(this);
                }
            }

            private void onAwaitSubscribed(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                if (arrived(msgTypeId, buffer, index, length, SUBACK_TYPE))
                {
                    stage = MessageConsumer.NOOP;
                    completed.offer(this);
                }
            }

            private void onAwaitPublish(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                if (arrived(msgTypeId, buffer, index, length, PUBLISH_TYPE))
                {
                    stage = MessageConsumer.NOOP;
                    client.data(PUBACK, 0, PUBACK.capacity());
                    completed.offer(this);
                }
            }

            private boolean arrived(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length,
                int packetType)
            {
                boolean arrived = false;

                if (msgTypeId == DataFW.TYPE_ID)
                {
                    final DataFW data = dataRO.wrap(buffer, index, index + length);

                    if (data.length() > 0)
                    {
                        failed |= (data.payload().buffer().getByte(data.payload().offset()) & 0xff) != packetType;
                        arrived = true;
                    }
                }

                return arrived;
            }

            private void onAwaitPuback(
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
                        failed |= (data.payload().buffer().getByte(data.payload().offset()) & 0xff) != PUBACK_TYPE;
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
        void prepare()
        {
            for (int connection = 0; connection < 4 * CONNECTED; connection++)
            {
                handshake();
            }

            drain();
            abortAll();

            handshake();
            drain();
            abortAll();
        }

        @Override
        void disconnect()
        {
            abortAll();
        }

        private void abortAll()
        {
            connected.forEach(Client::abort);
            connected.clear();
        }

        @Override
        void release(
            Handshake handshake)
        {
            connected.add(handshake.detach());
        }
    }

    @State(Scope.Benchmark)
    public abstract static class Connected extends Fixture
    {
        final Queue<Handshake> ready = new ArrayDeque<>();
        private final List<Handshake> connected = new ArrayList<>();

        @Override
        void prepare()
        {
            final int clients = Math.max(CLIENTS, inflight);

            for (int client = 0; client < clients; client++)
            {
                handshake();
            }

            drain();

            establish(clients);

            for (int client = 0; client < clients; client++)
            {
                operate();
            }

            drain();
        }

        void establish(
            int clients)
        {
        }

        @TearDown(Level.Iteration)
        public void settle()
        {
            drain();
        }

        void operate()
        {
            await(hasCapacity);

            exchange(ready.poll());
            pending++;
        }

        abstract void attach(
            Handshake handshake,
            int connection);

        abstract void exchange(
            Handshake handshake);

        @Override
        void disconnect()
        {
            ready.clear();
            connected.forEach(Handshake::finish);
            connected.clear();
        }

        @Override
        void release(
            Handshake handshake)
        {
            if (handshake.connection == -1)
            {
                handshake.connection = connected.size();
                attach(handshake, handshake.connection);
                connected.add(handshake);
            }

            ready.offer(handshake);
        }
    }

    @State(Scope.Benchmark)
    public static class Publishing extends Connected
    {
        @Param({"16", "1024"})
        public int payload;

        @Param({"none", "all"})
        public String properties;

        @Override
        void attach(
            Handshake handshake,
            int connection)
        {
            final boolean withProperties = "all".equals(properties);
            handshake.publication = new UnsafeBufferEx(publishPacket(version, connection, payload, withProperties));
        }

        @Override
        void exchange(
            Handshake handshake)
        {
            handshake.publish();
        }
    }

    @State(Scope.Benchmark)
    public static class Subscribing extends Connected
    {
        @Override
        void attach(
            Handshake handshake,
            int connection)
        {
            final String filter = "device/" + connection;
            handshake.subscription = new UnsafeBufferEx(filterPacket(SUBSCRIBE_PACKET, version, filter));
            handshake.unsubscription = new UnsafeBufferEx(filterPacket(UNSUBSCRIBE_PACKET, version, filter));
        }

        @Override
        void exchange(
            Handshake handshake)
        {
            handshake.subscribe();
        }
    }

    @State(Scope.Benchmark)
    public static class Fanning extends Delivering
    {
        @Override
        String filter(
            int connection)
        {
            return "device/fanout";
        }
    }

    @State(Scope.Benchmark)
    public static class Delivering extends Connected
    {
        @Param({"16", "1024"})
        public int payload;

        private int awaiting;

        private final BooleanSupplier credited = () -> deliverer.credited(awaiting);

        @Override
        void establish(
            int clients)
        {
            deliverer.payload(payload);

            for (int client = 0; client < clients; client++)
            {
                await(hasCapacity);

                ready.poll().subscribeOnly();
                pending++;
            }

            drain();
        }

        @Override
        void attach(
            Handshake handshake,
            int connection)
        {
            handshake.subscription = new UnsafeBufferEx(filterPacket(SUBSCRIBE_PACKET, version, filter(connection)));
        }

        String filter(
            int connection)
        {
            return "device/" + connection;
        }

        @Override
        void exchange(
            Handshake handshake)
        {
            handshake.expectPublish();

            awaiting = handshake.number;
            await(credited);
            deliverer.deliver(awaiting);
        }
    }

    private static final class Application implements BindingHandler
    {
        private final BeginFW beginRO = new BeginFW();
        private final MqttBeginExFW beginExRO = new MqttBeginExFW();
        private final BindingHandler sessions;
        private final BindingHandler publications;
        private final BindingHandler subscriptions;

        private Application(
            BindingHandler sessions,
            BindingHandler publications,
            BindingHandler subscriptions)
        {
            this.sessions = sessions;
            this.publications = publications;
            this.subscriptions = subscriptions;
        }

        @Override
        public MessageConsumer newStream(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length,
            MessageConsumer sender)
        {
            final OctetsFW extension = beginRO.wrap(buffer, index, index + length).extension();
            final MqttBeginExFW beginEx = beginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
            final int kind = beginEx != null ? beginEx.kind() : MqttBeginExFW.KIND_PUBLISH;
            final BindingHandler handler =
                kind == MqttBeginExFW.KIND_SESSION ? sessions :
                kind == MqttBeginExFW.KIND_SUBSCRIBE ? subscriptions :
                publications;

            return handler.newStream(msgTypeId, buffer, index, length, sender);
        }
    }

    private static byte[] filterPacket(
        int type,
        int version,
        String topicFilter)
    {
        final byte[] filter = topicFilter.getBytes(US_ASCII);
        final int properties = version == 5 ? 1 : 0;
        final int options = type == SUBSCRIBE_PACKET ? 1 : 0;
        final int remaining = 2 + properties + 2 + filter.length + options;
        final byte[] packet = new byte[2 + remaining];

        int offset = 0;
        packet[offset++] = (byte) type;
        packet[offset++] = (byte) remaining;
        packet[offset++] = 0x00;
        packet[offset++] = 0x01;
        offset += properties;
        packet[offset++] = 0x00;
        packet[offset++] = (byte) filter.length;
        System.arraycopy(filter, 0, packet, offset, filter.length);
        offset += filter.length;
        if (options != 0)
        {
            packet[offset] = 0x01;
        }

        return packet;
    }

    private static byte[] publishProperties(
        int publisher)
    {
        final byte[] contentType = "application/json".getBytes(US_ASCII);
        final byte[] responseTopic = ("reply/" + publisher).getBytes(US_ASCII);
        final byte[] userKey = "trace".getBytes(US_ASCII);
        final byte[] userValue = "abc123".getBytes(US_ASCII);
        final byte[] properties = new byte[1 + 2 + contentType.length + 1 + 2 + responseTopic.length +
            1 + 2 + 8 + 1 + 2 + userKey.length + 2 + userValue.length];

        int offset = 0;
        properties[offset++] = 0x03;
        offset = putString(properties, offset, contentType);
        properties[offset++] = 0x08;
        offset = putString(properties, offset, responseTopic);
        properties[offset++] = 0x09;
        offset = putString(properties, offset, new byte[8]);
        properties[offset++] = 0x26;
        offset = putString(properties, offset, userKey);
        putString(properties, offset, userValue);

        return properties;
    }

    private static int putString(
        byte[] packet,
        int offset,
        byte[] value)
    {
        packet[offset++] = (byte) (value.length >> 8);
        packet[offset++] = (byte) value.length;
        System.arraycopy(value, 0, packet, offset, value.length);

        return offset + value.length;
    }

    private static byte[] publishPacket(
        int version,
        int publisher,
        int payload,
        boolean withProperties)
    {
        final byte[] topic = ("device/" + publisher).getBytes(US_ASCII);
        final byte[] properties = version == 5 && withProperties ? publishProperties(publisher) : new byte[0];
        final int propertiesLength = version == 5 ? 1 + properties.length : 0;
        final int remaining = 2 + topic.length + 2 + propertiesLength + payload;
        final int lengthBytes = remaining < 128 ? 1 : 2;
        final byte[] packet = new byte[1 + lengthBytes + remaining];

        int offset = 0;
        packet[offset++] = 0x32;
        if (lengthBytes == 1)
        {
            packet[offset++] = (byte) remaining;
        }
        else
        {
            packet[offset++] = (byte) (remaining & 0x7f | 0x80);
            packet[offset++] = (byte) (remaining >> 7);
        }
        packet[offset++] = 0x00;
        packet[offset++] = (byte) topic.length;
        System.arraycopy(topic, 0, packet, offset, topic.length);
        offset += topic.length;
        packet[offset++] = 0x00;
        packet[offset++] = 0x01;
        if (version == 5)
        {
            packet[offset++] = (byte) properties.length;
            System.arraycopy(properties, 0, packet, offset, properties.length);
            offset += properties.length;
        }
        Arrays.fill(packet, offset, packet.length, (byte) 'x');

        return packet;
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

        return new Response(encoded, new byte[][] { new byte[0] }, new byte[][] { new byte[0] }, null, 0, true);
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
