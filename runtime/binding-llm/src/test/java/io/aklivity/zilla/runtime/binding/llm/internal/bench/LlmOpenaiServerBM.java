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

import static java.util.concurrent.TimeUnit.SECONDS;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import io.aklivity.zilla.runtime.engine.test.AllocationMeter;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class LlmOpenaiServerBM
{
    private static final long IDLE_MILLIS = 1_000L;

    @Param({"small", "10k"})
    public String size;

    @Param({"false", "true"})
    public boolean streaming;

    private LlmOpenaiServerBench harness;
    private AllocationMeter workerAllocation;
    private long requests;

    @Setup(Level.Trial)
    public void init() throws Exception
    {
        harness = new LlmOpenaiServerBench(size, streaming, true);
        workerAllocation = new AllocationMeter(harness.bench().workerThreadId());

        workerAllocation.mark();
        Thread.sleep(IDLE_MILLIS);
        System.out.printf("worker idle allocation: %d B over %d ms%n", workerAllocation.sinceMark(), IDLE_MILLIS);
    }

    @Setup(Level.Iteration)
    public void mark()
    {
        requests = 0L;
        workerAllocation.mark();
    }

    @TearDown(Level.Iteration)
    public void report()
    {
        System.out.printf("worker allocation: %.1f B/request over %d requests%n",
            workerAllocation.sinceMarkPer(requests), requests);
    }

    @TearDown(Level.Trial)
    public void destroy() throws Exception
    {
        harness.close();
    }

    @Benchmark
    public void exchange()
    {
        harness.exchange();
        requests++;
    }
}
