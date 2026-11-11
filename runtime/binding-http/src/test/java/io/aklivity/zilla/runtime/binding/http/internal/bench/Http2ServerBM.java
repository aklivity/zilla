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

import static io.aklivity.zilla.runtime.binding.http.internal.bench.Http2Wire.DATA;
import static io.aklivity.zilla.runtime.binding.http.internal.bench.Http2Wire.FLAG_ACK;
import static io.aklivity.zilla.runtime.binding.http.internal.bench.Http2Wire.FLAG_END_STREAM;
import static io.aklivity.zilla.runtime.binding.http.internal.bench.Http2Wire.HEADERS;
import static io.aklivity.zilla.runtime.binding.http.internal.bench.Http2Wire.SETTINGS;
import static io.aklivity.zilla.runtime.binding.http.internal.bench.Http2Wire.WINDOW_UPDATE;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.agrona.collections.IntHashSet;
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
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.Responder;
import io.aklivity.zilla.runtime.engine.test.Responder.Trigger;
import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;

/**
 * Drives an http server, speaking HTTP/2 on its network side, through the real engine, in front of a mock
 * application that replays a pre-built response. One connection carries every request, each on its own
 * stream. Each {@code exchange} operation is one whole request: the operation completes once up to
 * {@code streams - 1} other requests are still in flight, so {@code streams} requests are in flight at steady
 * state. Each {@code leave} operation opens one stream, sends the request completely on it and leaves it in
 * flight while the application withholds the response, spreading the streams over as many connections as the
 * server allows concurrent streams on each.
 * <p>
 * Read {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler for {@code exchange}, and
 * {@code retained.heap.norm} (B/op) from {@link RetainedHeapProfiler} for {@code leave}. {@code leave} does
 * not depend on {@code responseBody}, {@code streaming} or {@code streams}, so run it with
 * {@code -p responseBody=none -p streaming=false -p streams=1}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class Http2ServerBM
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/http/config/v2/server.yaml";
    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;
    private static final int WRITER_CAPACITY = 2 * 1024 * 1024;
    private static final int REPLY_WINDOW = 256 * 1024;
    private static final int CONNECTION_WINDOW_STEP = 64 * 1024;
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
        fixture.leave(IN_FLIGHT);
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

        @Param({"1", "16", "64"})
        public int streams;

        final List<Client> inflight = new ArrayList<>();

        private final boolean respond;
        private final WireWriter writer = new WireWriter(WRITER_CAPACITY);
        private final BooleanSupplier canSend = this::canSend;

        private EngineBench bench;
        private Responder responder;
        private long netId;
        private Http2Connection connection;
        private UnsafeBufferEx preface;
        private UnsafeBufferEx settings;
        private UnsafeBufferEx settingsAck;
        private UnsafeBufferEx connectionWindow;
        private UnsafeBufferEx connectionWindowStep;
        private byte[] requestFirst;
        private byte[] requestNext;
        private UnsafeBufferEx requestFirstBuffer;
        private UnsafeBufferEx requestNextBuffer;
        private byte[] requestBody0;
        private UnsafeBufferEx requestBodyBuffer;
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

            preface = new UnsafeBufferEx(Http2Wire.preface());
            settings = new UnsafeBufferEx(Http2Wire.settings(Http2Wire.MAX_CONCURRENT_STREAMS, 65_535));
            settingsAck = new UnsafeBufferEx(Http2Wire.settingsAck());
            connectionWindow = new UnsafeBufferEx(Http2Wire.windowUpdate(0, Http2Wire.CONNECTION_WINDOW_INCREMENT));
            connectionWindowStep = new UnsafeBufferEx(Http2Wire.windowUpdate(0, CONNECTION_WINDOW_STEP));
            requestFirst = Http2Wire.requestHeaders(true, headers, requestLength);
            requestNext = Http2Wire.requestHeaders(false, headers, requestLength);
            requestFirstBuffer = new UnsafeBufferEx(requestFirst);
            requestNextBuffer = new UnsafeBufferEx(requestNext);
            requestBody0 = Http2Wire.data(BenchMessages.body(requestLength), 0, requestLength, true);
            requestBodyBuffer = new UnsafeBufferEx(requestBody0);
            netId = bench.bindingId("net0");

            responder = new Responder(Trigger.END,
                respond ? BenchMessages.response(bench.labelId("http"), headers, responseLength, streaming) : null);
            bench.newServer("app0", responder);

            if (respond)
            {
                connection = connect();
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
            await(canSend);

            connection.request();
        }

        void leave(
            int requests)
        {
            Http2Connection current = null;

            for (int request = 0; request < requests; request++)
            {
                if (current == null || current.requests == Http2Wire.MAX_CONCURRENT_STREAMS)
                {
                    if (current != null)
                    {
                        await(current::settled);
                    }
                    current = connect();
                }

                current.request();
            }

            await(current::settled);
        }

        Http2Connection connect()
        {
            final Http2Connection connection = new Http2Connection();

            connection.client = bench.newClient(netId, connection);
            inflight.add(connection.client);
            writer.sink(connection);

            connection.client.begin();
            connection.client.replyWindow(REPLY_WINDOW);

            writer.write(preface);
            writer.write(settings);
            writer.write(connectionWindow);

            await(connection::established);

            return connection;
        }

        private boolean canSend()
        {
            return connection.requests - connection.completed < streams;
        }

        private void await(
            BooleanSupplier condition)
        {
            if (!bench.pollUntil(condition) || failed)
            {
                throw new IllegalStateException("exchange did not complete");
            }
        }

        final class Http2Connection implements MessageConsumer, Http2Scanner.FrameListener, WireWriter.Sink
        {
            private final DataFW dataRO = new DataFW();
            private final Http2Scanner scanner = new Http2Scanner(this, false);
            private final IntHashSet awaitingCredit = new IntHashSet();

            private Client client;
            private int nextStreamId = 1;
            private int requests;
            private int completed;
            private int consumed;
            private boolean established;

            boolean established()
            {
                return established;
            }

            boolean settled()
            {
                return awaitingCredit.isEmpty() && writer.drained();
            }

            void request()
            {
                final int streamId = nextStreamId;
                nextStreamId += 2;

                if (streamId < 0)
                {
                    throw new IllegalStateException("stream identifiers exhausted");
                }

                final boolean first = requests++ == 0;
                Http2Wire.streamId(first ? requestFirst : requestNext, streamId);
                writer.write(first ? requestFirstBuffer : requestNextBuffer);

                if (requestBody0.length != 0)
                {
                    awaitingCredit.add(streamId);
                }
            }

            @Override
            public int credit()
            {
                return client.initialCredit();
            }

            @Override
            public void data(
                DirectBufferEx buffer,
                int offset,
                int length)
            {
                client.data(buffer, offset, length);
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
                        scanner.scan(data.payload().buffer(), data.payload().offset(), data.payload().limit());
                    }
                    break;
                case WindowFW.TYPE_ID:
                    writer.drain();
                    break;
                case AbortFW.TYPE_ID:
                case ResetFW.TYPE_ID:
                    failed = true;
                    break;
                default:
                    break;
                }
            }

            @Override
            public void onFrame(
                int type,
                int flags,
                int streamId,
                int length,
                int firstWord)
            {
                switch (type)
                {
                case SETTINGS:
                    if ((flags & FLAG_ACK) == 0)
                    {
                        writer.write(settingsAck);
                        established = true;
                    }
                    break;
                case DATA:
                    consumed += length;
                    if (consumed >= CONNECTION_WINDOW_STEP)
                    {
                        consumed -= CONNECTION_WINDOW_STEP;
                        writer.write(connectionWindowStep);
                    }
                    if ((flags & FLAG_END_STREAM) != 0)
                    {
                        completed++;
                    }
                    break;
                case HEADERS:
                    if ((flags & FLAG_END_STREAM) != 0)
                    {
                        completed++;
                    }
                    break;
                case WINDOW_UPDATE:
                    if (streamId != 0 && awaitingCredit.remove(streamId))
                    {
                        Http2Wire.streamId(requestBody0, streamId);
                        writer.write(requestBodyBuffer);
                    }
                    break;
                default:
                    failed |= type == Http2Wire.GOAWAY || type == Http2Wire.RST_STREAM;
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
            .include(Http2ServerBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
