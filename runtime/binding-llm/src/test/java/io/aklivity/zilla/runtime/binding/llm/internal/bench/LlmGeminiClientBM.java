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
 * Drives an {@code llm} gemini client through the real engine, between a caller that sends canonical llm
 * sections and a mock provider that replays a pre-built gemini response. Each {@code exchange} operation is one
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
public class LlmGeminiClientBM
{
    private static final String MODEL = "gemini-3.6-flash";
    private static final String USAGE =
        "{\"promptTokenCount\":25,\"candidatesTokenCount\":15,\"totalTokenCount\":40}";

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
            return "io/aklivity/zilla/specs/binding/llm/config/client.gemini.yaml";
        }

        @Override
        byte[] beginEx(
            int llmTypeId)
        {
            return LlmFunctions.beginEx()
                .typeId(llmTypeId)
                .dialect("gemini")
                .model(MODEL)
                .extension(streaming ? "streamGenerateContent" : "generateContent")
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
                streamingEvents(events, text, toolCall);
            }
            else
            {
                events.add(chunk(part(text, toolCall), ",\"finishReason\":\"STOP\"", true).getBytes(UTF_8));
            }

            final byte[][] eventExtensions = new byte[events.size()][];
            Arrays.fill(eventExtensions, LlmBenchSupport.NO_BYTES);
            for (int event = 0; event < eventEx.size(); event++)
            {
                eventExtensions[event] = eventEx.get(event);
            }

            return new Response(
                LlmBenchSupport.httpResponseBeginEx(httpTypeId, !streaming ? "application/json" : "text/event-stream;space=1"),
                eventExtensions,
                events.toArray(byte[][]::new),
                LlmBenchSupport.NO_BYTES,
                0);
        }

        private static void streamingEvents(
            List<byte[]> events,
            String text,
            boolean toolCall)
        {
            if (toolCall)
            {
                events.add(chunk(part(text, true), "", false).getBytes(UTF_8));
            }
            else
            {
                for (String fragment : LlmBenchSupport.fragments(text))
                {
                    events.add(chunk(part(fragment, false), "", false).getBytes(UTF_8));
                }
            }

            events.add(chunk("{\"text\":\"\"}", ",\"finishReason\":\"STOP\"", true).getBytes(UTF_8));
        }

        private static String part(
            String text,
            boolean toolCall)
        {
            return toolCall
                ? String.format("{\"functionCall\":{\"name\":\"lookup\",\"args\":{\"query\":\"%s\"}}}", text)
                : String.format("{\"text\":\"%s\"}", text);
        }

        private static String chunk(
            String part,
            String finish,
            boolean usage)
        {
            return String.format("{\"candidates\":[{\"content\":{\"parts\":[%s],\"role\":\"model\"}%s,\"index\":0}]%s," +
                "\"modelVersion\":\"%s\",\"responseId\":\"resp_01\"}",
                part, finish, usage ? ",\"usageMetadata\":" + USAGE : "", MODEL);
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
            .include(LlmGeminiClientBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
