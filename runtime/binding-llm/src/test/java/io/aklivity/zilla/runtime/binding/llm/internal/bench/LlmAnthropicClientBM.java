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

import static io.aklivity.zilla.runtime.binding.llm.internal.bench.LlmClientFixture.IN_FLIGHT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.runtime.engine.test.Responder.Response;
import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;
import io.aklivity.zilla.specs.binding.llm.internal.LlmFunctions;

/**
 * Drives an {@code llm} anthropic client through the real engine, between a caller that sends canonical llm
 * sections and a mock provider that replays a pre-built anthropic response. Each {@code exchange} operation is one
 * whole request: the canonical request is sent, the flush that triggers the provider request is advised and the
 * canonical response is received to its end. Each {@code leave} operation opens one request, sends it completely
 * and leaves it in flight while the provider withholds the response. {@code request} is the canonical section the
 * caller sends and {@code response} the content the provider returns, each either text or a tool call.
 * <p>
 * Read {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler for {@code exchange}, and
 * {@code retained.heap.norm} (B/op) from {@link RetainedHeapProfiler} for {@code leave}. {@code leave} does
 * not depend on {@code streaming} or {@code response}, so run it with {@code -p streaming=false -p response=text}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class LlmAnthropicClientBM
{
    private static final String MODEL = "claude-3-opus-20240229";
    private static final String USAGE = "{\"input_tokens\":25,\"output_tokens\":15}";

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
            fixture.inflight.add(fixture.open());
        }
    }

    @State(Scope.Benchmark)
    public abstract static class Fixture extends LlmClientFixture
    {
        Fixture(
            boolean respond)
        {
            super(respond);
        }

        @Override
        String configuration()
        {
            return "io/aklivity/zilla/specs/binding/llm/config/client.anthropic.yaml";
        }

        @Override
        byte[] beginEx(
            int llmTypeId)
        {
            return LlmFunctions.beginEx()
                .typeId(llmTypeId)
                .dialect("anthropic")
                .model(MODEL)
                .build();
        }

        @Override
        String streamOptions()
        {
            return "{\"stream_options\":{\"include_usage\":true}}";
        }

        @Override
        Response response(
            String text,
            boolean toolCall,
            int httpTypeId)
        {
            final List<byte[]> events = new ArrayList<>();
            final List<byte[]> eventEx = new ArrayList<>();

            if (streaming)
            {
                streamingEvents(events, eventEx, httpTypeId, text, toolCall);
            }
            else
            {
                events.add(completion(text, toolCall).getBytes(UTF_8));
            }

            final byte[][] eventExtensions = new byte[events.size()][];
            Arrays.fill(eventExtensions, LlmBenchSupport.NO_BYTES);
            for (int event = 0; event < eventEx.size(); event++)
            {
                eventExtensions[event] = eventEx.get(event);
            }

            return new Response(
                LlmBenchSupport.httpResponseBeginEx(httpTypeId, !streaming ? "application/json" : "text/event-stream"),
                eventExtensions,
                events.toArray(byte[][]::new),
                LlmBenchSupport.NO_BYTES,
                0);
        }

        private static String completion(
            String text,
            boolean toolCall)
        {
            final String content = toolCall
                ? String.format("[{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"lookup\"," +
                    "\"input\":{\"query\":\"%s\"}}]", text)
                : String.format("[{\"type\":\"text\",\"text\":\"%s\"}]", text);

            return String.format("{\"id\":\"id_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"%s\"," +
                "\"content\":%s,\"stop_reason\":\"%s\",\"stop_sequence\":null,\"usage\":%s}",
                MODEL, content, toolCall ? "tool_use" : "end_turn", USAGE);
        }

        private static void streamingEvents(
            List<byte[]> events,
            List<byte[]> eventEx,
            int httpTypeId,
            String text,
            boolean toolCall)
        {
            event(events, eventEx, httpTypeId, "message_start",
                "\"message\":{\"id\":\"msg_01\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"" + MODEL +
                "\",\"content\":[],\"stop_reason\":null,\"stop_sequence\":null,\"usage\":" +
                "{\"input_tokens\":25,\"output_tokens\":0}}");
            event(events, eventEx, httpTypeId, "content_block_start", toolCall
                ? "\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"lookup\"," +
                    "\"input\":{}}"
                : "\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}");

            final List<String> deltas = new ArrayList<>();
            if (toolCall)
            {
                deltas.add(LlmBenchSupport.ARGUMENTS_PREFIX);
            }
            deltas.addAll(LlmBenchSupport.fragments(text));
            if (toolCall)
            {
                deltas.add(LlmBenchSupport.ARGUMENTS_SUFFIX);
            }

            for (String delta : deltas)
            {
                event(events, eventEx, httpTypeId, "content_block_delta", toolCall
                    ? "\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"" + delta + "\"}"
                    : "\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"" + delta + "\"}");
            }

            event(events, eventEx, httpTypeId, "content_block_stop", "\"index\":0");
            event(events, eventEx, httpTypeId, "message_delta",
                "\"delta\":{\"stop_reason\":\"" + (toolCall ? "tool_use" : "end_turn") +
                "\",\"stop_sequence\":null},\"usage\":" + USAGE);
            events.add("{\"type\":\"message_stop\"}".getBytes(UTF_8));
            eventEx.add(LlmBenchSupport.httpDataEx(httpTypeId, "message_stop"));
        }

        private static void event(
            List<byte[]> events,
            List<byte[]> eventEx,
            int httpTypeId,
            String type,
            String members)
        {
            events.add(String.format("{\"type\":\"%s\",%s}", type, members).getBytes(UTF_8));
            eventEx.add(LlmBenchSupport.httpDataEx(httpTypeId, type));
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
            .include(LlmAnthropicClientBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
