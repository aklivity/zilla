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
package io.aklivity.zilla.runtime.binding.tls.internal.bench;

import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.List;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;

import com.sun.management.ThreadMXBean;

/**
 * Reports the bytes allocated per operation by every thread except the JMH benchmark threads as
 * {@code engine.alloc.norm} in B/op.
 * <p>
 * The benchmark thread also hosts the in-memory TLS peer opposite the binding, whose {@code SSLEngine}
 * allocation is included in the {@code gc} profiler's {@code gc.alloc.rate.norm}. The engine worker and the
 * executor that runs delegated TLS tasks are the other threads, so this figure is the binding side alone.
 * <p>
 * JMH runs {@code @TearDown(Level.Trial)} before the last iteration's {@code afterIteration}, which ends the
 * engine threads and so drops their counters; that iteration reports a negative value. Results are therefore
 * aggregated with {@code MAX}, which ignores it, and the warmup iteration that runs trial setup is not measured.
 * <p>
 * Use with {@code -prof io.aklivity.zilla.runtime.binding.tls.internal.bench.EngineAllocProfiler}.
 */
public final class EngineAllocProfiler implements InternalProfiler
{
    private static final String ENGINE_ALLOC = "engine.alloc.norm";
    private static final String UNIT = "B/op";
    private static final String BENCHMARK_THREAD = "jmh-worker";

    private final ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    private long allocatedBefore;

    @Override
    public void beforeIteration(
        BenchmarkParams benchmarkParams,
        IterationParams iterationParams)
    {
        allocatedBefore = allocated();
    }

    @Override
    public Collection<? extends Result> afterIteration(
        BenchmarkParams benchmarkParams,
        IterationParams iterationParams,
        IterationResult result)
    {
        final long allocated = allocated() - allocatedBefore;
        final long operations = result.getMetadata().getAllOps();
        final double perOperation = operations != 0L ? (double) allocated / operations : 0.0;

        return List.of(new ScalarResult(ENGINE_ALLOC, perOperation, UNIT, AggregationPolicy.MAX));
    }

    @Override
    public String getDescription()
    {
        return "Bytes allocated per operation by threads other than the benchmark threads";
    }

    private long allocated()
    {
        long total = 0L;

        for (long id : threads.getAllThreadIds())
        {
            final var info = threads.getThreadInfo(id);
            if (info != null && !info.getThreadName().contains(BENCHMARK_THREAD))
            {
                total += Math.max(threads.getThreadAllocatedBytes(id), 0L);
            }
        }

        return total;
    }
}
