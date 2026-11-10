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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;

/**
 * Drives an http client, speaking HTTP/1.1 on its network side, through the real engine, behind a peer that
 * answers every request with a pre-built response on a persistent connection. Each {@code exchange}
 * operation is one whole request: it completes once up to {@code requests - 1} other requests are still in
 * flight, so {@code requests} requests are in flight at steady state, as many connections as the client
 * opens and the rest queued on them. Each {@code leave} operation sends one request completely and leaves it
 * in flight while the peer withholds the response. The client has one connection to the peer and queues the
 * requests behind it, only the first of which is on the wire, up to what the queue holds, so {@code leave}
 * leaves 200 requests, and without a body, which a queued request has no credit to send.
 * <p>
 * Read {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler for {@code exchange}, and
 * {@code retained.heap.norm} (B/op) from {@link RetainedHeapProfiler} for {@code leave}. {@code leave} does
 * not depend on {@code responseBody}, {@code streaming} or {@code requests}, so run it with
 * {@code -p requestBody=none -p responseBody=none -p streaming=false -p requests=1}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class Http1ClientBM
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/http/config/v1.1/client.yaml";
    private static final int IN_FLIGHT = 200;

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
        if (!"none".equals(fixture.requestBody))
        {
            throw new IllegalArgumentException("leave requires -p requestBody=none, a queued request has no credit for a body");
        }

        fixture.leave(IN_FLIGHT, 1);
    }

    @State(Scope.Benchmark)
    public abstract static class Fixture extends HttpClientFixture
    {
        @Param({"1", "16"})
        public int requests;

        Fixture(
            boolean respond)
        {
            super(respond);
        }

        @Override
        String configuration()
        {
            return CONFIGURATION;
        }

        @Override
        int concurrency()
        {
            return requests;
        }

        @Override
        NetPeer newPeer(
            int headerCount,
            int bodyLength,
            boolean streaming,
            boolean respond)
        {
            return new Http1NetPeer(headerCount, bodyLength, streaming, respond);
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
            .include(Http1ClientBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
