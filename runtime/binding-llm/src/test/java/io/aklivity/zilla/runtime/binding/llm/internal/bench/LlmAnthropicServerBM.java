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

import static io.aklivity.zilla.runtime.binding.llm.internal.bench.LlmServerFixture.IN_FLIGHT;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

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

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;

/**
 * Drives an {@code llm} anthropic server through the real engine, in front of a mock provider that replays a
 * pre-built response. Each {@code exchange} operation is one whole request: the HTTP-shaped request is sent and
 * the response is received to its end. Each {@code leave} operation opens one request, sends it completely and
 * leaves it in flight while the provider withholds the response.
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
public class LlmAnthropicServerBM
{
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
    public abstract static class Fixture extends LlmServerFixture
    {
        Fixture(
            boolean respond)
        {
            super(respond);
        }

        @Override
        String configuration()
        {
            return "io/aklivity/zilla/specs/binding/llm/config/server.yaml";
        }

        @Override
        String path()
        {
            return "/v1/messages";
        }

        @Override
        void headers(
            HttpBeginExFW.Builder builder)
        {
            builder.headersItem(h -> h.name("anthropic-version").value("2023-06-01"));
        }

        @Override
        String requestBody(
            String text)
        {
            final String stream = streaming ? "\"stream\":true," : "";

            return "toolCall".equals(request)
                ? String.format("{\"model\":\"claude-3-opus-20240229\",\"max_tokens\":1024,%s\"messages\":[{\"role\":" +
                    "\"assistant\",\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"lookup\"," +
                    "\"input\":{\"query\":\"%s\"}}]}]}", stream, text)
                : String.format("{\"model\":\"claude-3-opus-20240229\",\"max_tokens\":1024,%s\"messages\":[{\"role\":" +
                    "\"user\",\"content\":\"%s\"}]}", stream, text);
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
            .include(LlmAnthropicServerBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
