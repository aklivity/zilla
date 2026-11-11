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
package io.aklivity.zilla.runtime.binding.llm.internal.bench;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Queue;
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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.StreamState;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.EngineBench.FrameCounter;
import io.aklivity.zilla.runtime.engine.test.Responder;
import io.aklivity.zilla.runtime.engine.test.Responder.Response;
import io.aklivity.zilla.runtime.engine.test.Responder.Trigger;
import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;
import io.aklivity.zilla.specs.binding.llm.internal.LlmFunctions;

/**
 * Drives an {@code llm} proxy through the real engine between two canonical peers. The caller opens an
 * {@code llm} application stream into the proxy and sends canonical sections; the exit peer ({@code app0})
 * accepts the proxied stream and replays a pre-built canonical response once the request is advised complete.
 * Each {@code exchange} operation is one whole request and its response, received to the end. Each
 * {@code leave} operation opens one request, sends it completely and leaves it in flight while the exit
 * withholds the response.
 * <p>
 * {@code scenario} selects the proxy configuration: {@code plain} routes only, {@code fallback} adds a request
 * retention for replay, and the guardrail scenarios add a section pipeline per direction, each one using the
 * engine test model so that no external model service is needed. {@code disclose} guards sections of a fixed
 * length and runs only with {@code size=10k}.
 * <p>
 * {@code request} is the canonical section the caller sends ({@code text} as {@code USER_TEXT}, {@code toolCall}
 * as {@code TOOL_CALL}, {@code image} as {@code USER_IMAGE} carrying an inline base64 image) and {@code response}
 * is the section the exit replies with, followed by a {@code CONTROL} usage section and the end extension. With
 * {@code streaming} the request and the response are sent in fragments of 256 bytes.
 * <p>
 * Read {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler for {@code exchange}, and
 * {@code retained.heap.norm} (B/op) from {@link RetainedHeapProfiler} for {@code leave}. {@code leave} does
 * not depend on {@code streaming} or on the {@code response}, so run it with {@code -p streaming=false}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class LlmProxyBM
{
    private static final String CONFIGURATION_PREFIX = "io/aklivity/zilla/specs/binding/llm/config/";
    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;
    private static final int STREAMING_FRAGMENT_LENGTH = 256;
    private static final int IN_FLIGHT = 5_000;
    private static final int SMALL_LENGTH = 400;
    private static final int LARGE_LENGTH = 10_000;
    private static final int DISCLOSE_LENGTH = 13_336;
    private static final long TIMEOUT_NANOS = SECONDS.toNanos(10L);
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_INIT = 0x02;
    private static final int WRITE_BUFFER_CAPACITY = 64 * 1024;
    private static final String MODEL = "gpt-a";
    private static final String USAGE =
        "{\"openai/ccs:usage\":{\"prompt_tokens\":25,\"completion_tokens\":15,\"total_tokens\":40}}";
    private static final DirectBufferEx EMPTY = new UnsafeBufferEx(new byte[0]);

    @Benchmark
    public void exchange(
        Replying fixture)
    {
        final Fixture.Caller caller = fixture.open();

        caller.awaitReplyEnd();
        caller.end();
        fixture.release(caller);
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
            fixture.inflight.add(fixture.open());
        }
    }

    @State(Scope.Benchmark)
    public abstract static class Fixture
    {
        @Param({"plain", "fallback", "guardrail", "mask", "chain", "async", "classify", "disclose"})
        public String scenario;

        @Param({"small", "10k"})
        public String size;

        @Param({"false", "true"})
        public boolean streaming;

        @Param({"text", "toolCall", "image"})
        public String request;

        @Param({"text", "toolCall"})
        public String response;

        final List<Caller> inflight = new ArrayList<>(IN_FLIGHT * 4);

        private final boolean respond;
        private final Queue<Caller> idle = new ArrayDeque<>();
        private final DataFW.Builder dataRW = new DataFW.Builder();
        private final FlushFW.Builder flushRW = new FlushFW.Builder();
        private final MutableDirectBufferEx frameBuffer = new UnsafeBufferEx(new byte[WRITE_BUFFER_CAPACITY]);

        private EngineBench bench;
        private Responder exit;
        private long replyLength;
        private long netId;
        private UnsafeBufferEx beginEx;
        private UnsafeBufferEx requestEx;
        private UnsafeBufferEx requestPayload;

        Fixture(
            boolean respond)
        {
            this.respond = respond;
        }

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            if ("disclose".equals(scenario) && !"10k".equals(size))
            {
                throw new IllegalArgumentException("scenario disclose guards sections of a fixed length, use -p size=10k");
            }

            final int length = "disclose".equals(scenario) ? DISCLOSE_LENGTH : "10k".equals(size) ? LARGE_LENGTH : SMALL_LENGTH;
            final String text = base64(length);

            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("app0")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .start(CONFIGURATION_PREFIX + configuration(scenario));

            final int llmTypeId = bench.labelId("llm");

            beginEx = new UnsafeBufferEx(LlmFunctions.beginEx()
                .typeId(llmTypeId)
                .dialect("openai/ccs")
                .model(MODEL)
                .build());
            requestEx = new UnsafeBufferEx(requestEx(llmTypeId));
            requestPayload = new UnsafeBufferEx(requestPayload(text).getBytes(UTF_8));
            netId = bench.bindingId("net0");

            final Response reply = respond ? response(llmTypeId, text) : null;
            exit = new Responder(Trigger.FLUSH, reply);
            bench.newServer("app0", exit);

            verify(reply);
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            inflight.forEach(Caller::abort);
            bench.close();
        }

        Caller open()
        {
            Caller caller = idle.poll();

            if (caller == null)
            {
                caller = new Caller();
            }

            caller.begin(bench.newClient(netId, caller));
            caller.send(requestEx, requestPayload);
            caller.flush();

            return caller;
        }

        void release(
            Caller caller)
        {
            idle.offer(caller);
        }

        private void verify(
            Response reply)
        {
            final Caller caller = open();

            if (reply != null)
            {
                caller.awaitReplyEnd();
                caller.end();
            }
            else
            {
                bench.pollUntil(() -> exit.counter().flushes() != 0);
            }

            final FrameCounter counter = exit.counter();
            final long expectedReply = replyLength;

            if (counter.begins() != 1 || counter.dataBytes() != requestPayload.capacity() || counter.flushes() != 1 ||
                caller.replyBytes() != expectedReply)
            {
                throw new IllegalStateException(String.format(
                    "unexpected exchange begins=%d requestBytes=%d (expected %d) flushes=%d replyBytes=%d (expected %d)",
                    counter.begins(), counter.dataBytes(), requestPayload.capacity(), counter.flushes(),
                    caller.replyBytes(), expectedReply));
            }

            counter.clear();

            if (reply != null)
            {
                release(caller);
            }
            else
            {
                caller.abort();
            }
        }

        private byte[] requestEx(
            int llmTypeId)
        {
            final byte[] extension;

            if ("toolCall".equals(request))
            {
                extension = LlmFunctions.dataEx()
                    .typeId(llmTypeId)
                    .type("TOOL_CALL")
                    .message(0)
                    .attribute("tool-name", "lookup")
                    .attribute("tool-call-id", "call_1")
                    .build();
            }
            else
            {
                extension = LlmFunctions.dataEx()
                    .typeId(llmTypeId)
                    .type("image".equals(request) ? "USER_IMAGE" : "USER_TEXT")
                    .message(0)
                    .build();
            }

            return extension;
        }

        private String requestPayload(
            String text)
        {
            final String payload;

            if ("toolCall".equals(request))
            {
                payload = String.format("{\"arguments\":{\"query\":\"%s\"}}", text);
            }
            else if ("image".equals(request))
            {
                payload = String.format("{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64,%s\"}}", text);
            }
            else
            {
                payload = String.format("{\"text\":\"%s\"}", text);
            }

            return payload;
        }

        private Response response(
            int llmTypeId,
            String text)
        {
            final boolean toolCall = "toolCall".equals(response);
            final byte[] beginEx = LlmFunctions.beginEx()
                .typeId(llmTypeId)
                .dialect("openai/ccs")
                .model(MODEL)
                .id("chatcmpl_2")
                .build();
            final byte[] contentEx = toolCall
                ? LlmFunctions.dataEx()
                    .typeId(llmTypeId)
                    .type("TOOL_CALL")
                    .message(0)
                    .attribute("tool-call-id", "call_1")
                    .attribute("tool-name", "lookup")
                    .build()
                : LlmFunctions.dataEx()
                    .typeId(llmTypeId)
                    .type("ASSISTANT_TEXT")
                    .message(0)
                    .build();
            final byte[] usageEx = LlmFunctions.dataEx()
                .typeId(llmTypeId)
                .type("CONTROL")
                .message(-1)
                .build();
            final byte[] endEx = LlmFunctions.endEx()
                .typeId(llmTypeId)
                .inputTokens(25)
                .outputTokens(15)
                .totalTokens(40)
                .finishReason(toolCall ? "tool_calls" : "stop")
                .build();
            final String content = toolCall
                ? String.format("{\"arguments\":{\"query\":\"%s\"}}", text)
                : String.format("{\"text\":\"%s\"}", text);

            this.replyLength = content.length() + USAGE.length();

            return new Response(
                beginEx,
                new byte[][] { contentEx, usageEx },
                new byte[][] { content.getBytes(UTF_8), USAGE.getBytes(UTF_8) },
                endEx,
                streaming ? STREAMING_FRAGMENT_LENGTH : 0);
        }

        private static String configuration(
            String scenario)
        {
            return switch (scenario)
            {
            case "plain" -> "proxy.yaml";
            case "fallback" -> "proxy.fallback.yaml";
            case "guardrail" -> "proxy.guardrail.yaml";
            case "mask" -> "proxy.guardrail.mask.yaml";
            case "chain" -> "proxy.guardrail.chain.yaml";
            case "async" -> "proxy.guardrail.async.yaml";
            case "classify" -> "proxy.guardrail.classify.yaml";
            case "disclose" -> "proxy.guardrail.disclose.10k.yaml";
            default -> throw new IllegalArgumentException("unknown scenario " + scenario);
            };
        }

        private static String base64(
            int length)
        {
            final byte[] random = new byte[length];
            new Random(0L).nextBytes(random);

            return Base64.getEncoder().withoutPadding().encodeToString(random).substring(0, length);
        }

        final class Caller implements MessageConsumer
        {
            private final WindowFW windowRO = new WindowFW();

            private Client client;
            private boolean failed;
            private long sequence;
            private long acknowledge;
            private int maximum;
            private int padding;

            void begin(
                Client client)
            {
                this.client = client;
                this.failed = false;
                this.sequence = 0L;
                this.acknowledge = 0L;
                this.maximum = 0;
                this.padding = 0;

                client.begin(beginEx, 0, beginEx.capacity());
                client.replyWindow(DEFAULT_WINDOW);
            }

            void send(
                DirectBufferEx extension,
                DirectBufferEx payload)
            {
                final long deadline = System.nanoTime() + TIMEOUT_NANOS;
                final int length = payload.capacity();
                final int fragmentLength = streaming ? STREAMING_FRAGMENT_LENGTH : length;

                int sent = 0;
                while (sent < length)
                {
                    bench.poll();

                    final int credit = (int) (acknowledge + maximum - sequence) - padding;
                    if (credit > 0)
                    {
                        final int fragment = Math.min(Math.min(credit, length - sent), fragmentLength);
                        final int flags = (sent == 0 ? FLAGS_INIT : 0) | (sent + fragment == length ? FLAGS_FIN : 0);

                        data(flags, payload, sent, fragment, sent == 0 ? extension : EMPTY);
                        sent += fragment;
                    }

                    checkProgress(deadline);
                }
            }

            void flush()
            {
                final FlushFW flush = flushRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .originId(client.originId())
                    .routedId(client.routedId())
                    .streamId(client.initialId())
                    .sequence(sequence)
                    .acknowledge(acknowledge)
                    .state(StreamState.openInitial(0))
                    .maximum(maximum)
                    .budgetId(0L)
                    .extension(EMPTY, 0, 0)
                    .build();

                client.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
            }

            void awaitReplyEnd()
            {
                client.awaitReplyEnd();
            }

            long replyBytes()
            {
                return client.replyBytes();
            }

            void end()
            {
                client.end();
            }

            void abort()
            {
                client.abort();
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
                    acknowledge = window.acknowledge();
                    maximum = window.maximum();
                    padding = window.padding();
                    break;
                case ResetFW.TYPE_ID:
                    failed = true;
                    break;
                default:
                    break;
                }
            }

            private void data(
                int flags,
                DirectBufferEx payload,
                int offset,
                int length,
                DirectBufferEx extension)
            {
                final DataFW data = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .originId(client.originId())
                    .routedId(client.routedId())
                    .streamId(client.initialId())
                    .sequence(sequence)
                    .acknowledge(acknowledge)
                    .state(StreamState.openInitial(0))
                    .maximum(maximum)
                    .flags(flags)
                    .budgetId(0L)
                    .reserved(length + padding)
                    .payload(payload, offset, length)
                    .extension(extension, 0, extension.capacity())
                    .build();

                sequence += data.reserved();
                client.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
            }

            private void checkProgress(
                long deadline)
            {
                if (failed)
                {
                    throw new IllegalStateException("stream rejected by engine");
                }

                if (System.nanoTime() > deadline)
                {
                    throw new IllegalStateException("stream timed out");
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
            .include(LlmProxyBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
