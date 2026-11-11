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
 * Drives an {@code llm} openai responses client through the real engine, between a caller that sends canonical llm
 * sections and a mock provider that replays a pre-built openai responses response. Each {@code exchange} operation is one
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
public class LlmOpenaiResponsesClientBM
{
    private static final String RESPONSES_USAGE =
        "{\"input_tokens\":25,\"output_tokens\":15,\"total_tokens\":40}";

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
            return "io/aklivity/zilla/specs/binding/llm/config/client.openai.responses.yaml";
        }

        @Override
        byte[] beginEx(
            int llmTypeId)
        {
            return LlmFunctions.beginEx()
                .typeId(llmTypeId)
                .dialect("openai/rs")
                .model("gpt-4")
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
                responsesStreamingEvents(events, eventEx, httpTypeId, text, toolCall);
            }
            else
            {
                events.add(responsesCompletion(text, toolCall).getBytes(UTF_8));
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

        private static String responsesCompletion(
            String text,
            boolean toolCall)
        {
            final String item = toolCall
                ? String.format("{\"id\":\"fc_1\",\"type\":\"function_call\",\"arguments\":\"%s%s%s\"," +
                    "\"call_id\":\"call_1\",\"name\":\"lookup\",\"status\":\"completed\"}",
                    LlmBenchSupport.ARGUMENTS_PREFIX, text, LlmBenchSupport.ARGUMENTS_SUFFIX)
                : String.format("{\"id\":\"msg_1\",\"type\":\"message\",\"content\":[{\"type\":\"output_text\"," +
                    "\"text\":\"%s\",\"annotations\":[],\"logprobs\":[]}],\"role\":\"assistant\",\"status\":\"completed\"}",
                    text);

            return String.format("{\"id\":\"resp_1\",\"object\":\"response\",\"model\":\"gpt-4\",\"output\":[%s]," +
                "\"status\":\"completed\",\"usage\":%s}", item, RESPONSES_USAGE);
        }

        private static void responsesStreamingEvents(
            List<byte[]> events,
            List<byte[]> eventEx,
            int httpTypeId,
            String text,
            boolean toolCall)
        {
            final String progress = "\"response\":{\"id\":\"resp_1\",\"object\":\"response\",\"model\":\"gpt-4\"," +
                "\"status\":\"in_progress\",\"output\":[]}";
            final String added = toolCall
                ? "\"item\":{\"id\":\"fc_1\",\"type\":\"function_call\",\"status\":\"in_progress\"," +
                    "\"arguments\":\"\",\"call_id\":\"call_1\",\"name\":\"lookup\"}"
                : "\"item\":{\"id\":\"msg_1\",\"type\":\"message\",\"status\":\"in_progress\",\"content\":[]," +
                    "\"role\":\"assistant\"}";
            final String done = toolCall
                ? String.format("\"item\":{\"id\":\"fc_1\",\"type\":\"function_call\",\"status\":\"completed\"," +
                    "\"arguments\":\"%s%s%s\",\"call_id\":\"call_1\",\"name\":\"lookup\"}",
                    LlmBenchSupport.ARGUMENTS_PREFIX, text, LlmBenchSupport.ARGUMENTS_SUFFIX)
                : String.format("\"item\":{\"id\":\"msg_1\",\"type\":\"message\",\"status\":\"completed\"," +
                    "\"content\":[{\"type\":\"output_text\",\"annotations\":[],\"logprobs\":[],\"text\":\"%s\"}]," +
                    "\"role\":\"assistant\"}", text);

            int sequence = 0;
            sequence = responsesEvent(events, eventEx, httpTypeId, "response.created", progress, sequence);
            sequence = responsesEvent(events, eventEx, httpTypeId, "response.in_progress", progress, sequence);
            sequence = responsesEvent(events, eventEx, httpTypeId, "response.output_item.added",
                added + ",\"output_index\":0", sequence);

            if (toolCall)
            {
                final List<String> deltas = new ArrayList<>();
                deltas.add(LlmBenchSupport.ARGUMENTS_PREFIX);
                deltas.addAll(LlmBenchSupport.fragments(text));
                deltas.add(LlmBenchSupport.ARGUMENTS_SUFFIX);

                for (String delta : deltas)
                {
                    sequence = responsesEvent(events, eventEx, httpTypeId, "response.function_call_arguments.delta",
                        "\"delta\":\"" + delta + "\",\"item_id\":\"fc_1\",\"obfuscation\":\"abcdefghij\",\"output_index\":0",
                        sequence);
                }

                sequence = responsesEvent(events, eventEx, httpTypeId, "response.function_call_arguments.done",
                    "\"arguments\":\"" + LlmBenchSupport.ARGUMENTS_PREFIX + text + LlmBenchSupport.ARGUMENTS_SUFFIX +
                    "\",\"item_id\":\"fc_1\",\"output_index\":0", sequence);
            }
            else
            {
                sequence = responsesEvent(events, eventEx, httpTypeId, "response.content_part.added",
                    "\"content_index\":0,\"item_id\":\"msg_1\",\"output_index\":0,\"part\":{\"type\":\"output_text\"," +
                    "\"annotations\":[],\"logprobs\":[],\"text\":\"\"}", sequence);

                for (String fragment : LlmBenchSupport.fragments(text))
                {
                    sequence = responsesEvent(events, eventEx, httpTypeId, "response.output_text.delta",
                        "\"content_index\":0,\"delta\":\"" + fragment + "\",\"item_id\":\"msg_1\",\"logprobs\":[]," +
                        "\"obfuscation\":\"abcdefghij\",\"output_index\":0", sequence);
                }

                sequence = responsesEvent(events, eventEx, httpTypeId, "response.output_text.done",
                    "\"content_index\":0,\"item_id\":\"msg_1\",\"logprobs\":[],\"output_index\":0,\"text\":\"" +
                    text + "\"", sequence);
                sequence = responsesEvent(events, eventEx, httpTypeId, "response.content_part.done",
                    "\"content_index\":0,\"item_id\":\"msg_1\",\"output_index\":0,\"part\":{\"type\":\"output_text\"," +
                    "\"annotations\":[],\"logprobs\":[],\"text\":\"" + text + "\"}", sequence);
            }

            sequence = responsesEvent(events, eventEx, httpTypeId, "response.output_item.done",
                done + ",\"output_index\":0", sequence);
            responsesEvent(events, eventEx, httpTypeId, "response.completed",
                "\"response\":{\"id\":\"resp_1\",\"object\":\"response\",\"model\":\"gpt-4\",\"output\":[" + done.substring(
                    "\"item\":".length()) + "],\"status\":\"completed\",\"usage\":" + RESPONSES_USAGE + "}", sequence);
        }

        private static int responsesEvent(
            List<byte[]> events,
            List<byte[]> eventEx,
            int httpTypeId,
            String type,
            String members,
            int sequence)
        {
            events.add(String.format("{\"type\":\"%s\",%s,\"sequence_number\":%d}", type, members, sequence)
                .getBytes(UTF_8));
            eventEx.add(LlmBenchSupport.httpDataEx(httpTypeId, type));

            return sequence + 1;
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
            .include(LlmOpenaiResponsesClientBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
