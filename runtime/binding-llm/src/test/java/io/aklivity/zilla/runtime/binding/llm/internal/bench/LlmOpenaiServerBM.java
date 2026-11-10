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

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.Responder;
import io.aklivity.zilla.runtime.engine.test.Responder.Response;
import io.aklivity.zilla.runtime.engine.test.Responder.Trigger;
import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;
import io.aklivity.zilla.specs.binding.llm.internal.LlmFunctions;

/**
 * Drives an {@code llm} openai server through the real engine, in front of a mock provider that replays a
 * pre-built response. Each {@code exchange} operation is one whole request: the HTTP-shaped request is sent
 * and the response is received to its end. Each {@code leave} operation opens one request, sends it
 * completely and leaves it in flight while the provider withholds the response.
 * <p>
 * Read {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler for {@code exchange}, and
 * {@code retained.heap.norm} (B/op) from {@link RetainedHeapProfiler} for {@code leave}. {@code leave} does
 * not depend on {@code streaming}, so run it with {@code -p streaming=false}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class LlmOpenaiServerBM
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/llm/config/server.yaml";
    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;
    private static final int STREAMING_FRAGMENT_LENGTH = 256;
    private static final int IN_FLIGHT = 5_000;
    private static final int SMALL_LENGTH = 400;
    private static final int LARGE_LENGTH = 10_000;
    private static final String USAGE =
        "{\"openai/ccs:usage\":{\"prompt_tokens\":25,\"completion_tokens\":15,\"total_tokens\":40}}";

    @Benchmark
    public void exchange(
        Replying fixture)
    {
        fixture.open().awaitReplyEnd();
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
        @Param({"small", "10k"})
        public String size;

        @Param({"false", "true"})
        public boolean streaming;

        @Param({"text", "toolCall"})
        public String request;

        final List<Client> inflight = new ArrayList<>(IN_FLIGHT * 4);

        private final boolean respond;

        private EngineBench bench;
        private long netId;
        private UnsafeBufferEx beginEx;
        private UnsafeBufferEx requestBody;

        Fixture(
            boolean respond)
        {
            this.respond = respond;
        }

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            final String text = base64("10k".equals(size) ? LARGE_LENGTH : SMALL_LENGTH);

            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("app0")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .start(CONFIGURATION);

            beginEx = httpBeginEx(bench.labelId("http"));
            requestBody = new UnsafeBufferEx(requestBody(text).getBytes(UTF_8));
            netId = bench.bindingId("net0");

            bench.newServer("app0", new Responder(Trigger.FLUSH, respond ? response(bench.labelId("llm"), text) : null));
        }

        @TearDown(Level.Trial)
        public void destroy() throws Exception
        {
            inflight.forEach(Client::abort);
            bench.close();
        }

        Client open()
        {
            final Client client = bench.newClient(netId, MessageConsumer.NOOP);

            client.begin(beginEx, 0, beginEx.capacity());
            client.replyWindow(DEFAULT_WINDOW);
            client.send(requestBody, 0, requestBody.capacity());
            client.end();

            return client;
        }

        private String requestBody(
            String text)
        {
            final String stream = streaming ? "\"stream\":true," : "";

            if ("toolCall".equals(request))
            {
                return String.format("{\"model\":\"gpt-4\",%s\"messages\":[{\"role\":\"assistant\",\"tool_calls\":" +
                    "[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"lookup\"," +
                    "\"arguments\":\"{\\\"query\\\":\\\"%s\\\"}\"}}]}]}", stream, text);
            }

            return String.format("{\"model\":\"gpt-4\",%s\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]}", stream, text);
        }

        private Response response(
            int llmTypeId,
            String text)
        {
            final byte[] beginEx = LlmFunctions.beginEx()
                .typeId(llmTypeId)
                .dialect("openai/ccs")
                .model("gpt-4")
                .id("chatcmpl_2")
                .build();
            final byte[] textEx = LlmFunctions.dataEx()
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
                .finishReason("stop")
                .build();

            return new Response(
                beginEx,
                new byte[][] { textEx, usageEx },
                new byte[][] { String.format("{\"text\":\"%s\"}", text).getBytes(UTF_8), USAGE.getBytes(UTF_8) },
                endEx,
                streaming ? STREAMING_FRAGMENT_LENGTH : 0);
        }

        private static String base64(
            int length)
        {
            final byte[] random = new byte[length];
            new Random(0L).nextBytes(random);

            return Base64.getEncoder().withoutPadding().encodeToString(random).substring(0, length);
        }

        private static UnsafeBufferEx httpBeginEx(
            int httpTypeId)
        {
            final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);

            final HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder().wrap(buffer, 0, buffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(":method").value("POST"))
                .headersItem(h -> h.name(":scheme").value("http"))
                .headersItem(h -> h.name(":authority").value("example.com:8080"))
                .headersItem(h -> h.name(":path").value("/v1/chat/completions"))
                .headersItem(h -> h.name("content-type").value("application/json"))
                .build();

            final byte[] encoded = new byte[httpBeginEx.sizeof()];
            buffer.getBytes(0, encoded);

            return new UnsafeBufferEx(encoded);
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
            .include(LlmOpenaiServerBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
