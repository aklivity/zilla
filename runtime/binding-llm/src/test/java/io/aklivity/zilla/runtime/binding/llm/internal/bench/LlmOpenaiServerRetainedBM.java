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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.RetainedHeapMeter;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@Fork(3)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@OutputTimeUnit(MILLISECONDS)
public class LlmOpenaiServerRetainedBM
{
    @Param({"small", "10k"})
    public String size;

    @Param({"5000"})
    public int requests;

    private LlmOpenaiServerBench harness;
    private Client[] inflight;
    private int flushes;
    private int aborts;

    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class Retained
    {
        public long bytesPerRequest;
    }

    @Setup
    public void init() throws Exception
    {
        harness = new LlmOpenaiServerBench(size, false, false);
        inflight = new Client[requests];

        openAndAbort();
    }

    @TearDown
    public void destroy() throws Exception
    {
        harness.close();
    }

    @Benchmark
    public void retained(
        Retained retained)
    {
        retained.bytesPerRequest = openAndAbort();
        System.out.printf("retained heap: %d B/request over %d in-flight requests%n", retained.bytesPerRequest, requests);
    }

    private long openAndAbort()
    {
        final RetainedHeapMeter heap = new RetainedHeapMeter();
        heap.mark();

        for (int i = 0; i < requests; i++)
        {
            inflight[i] = harness.open();
        }

        flushes += requests;
        harness.awaitRequestFlushes(flushes);

        final long perRequest = heap.deltaPerItem(requests);

        for (int i = 0; i < requests; i++)
        {
            inflight[i].abort();
        }

        aborts += requests;
        harness.awaitRequestAborts(aborts);

        return perRequest;
    }
}
