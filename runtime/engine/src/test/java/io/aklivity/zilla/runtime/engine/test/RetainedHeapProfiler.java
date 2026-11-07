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
package io.aklivity.zilla.runtime.engine.test;

import java.util.Collection;
import java.util.List;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;

/**
 * Reports the heap retained by each benchmark operation as {@code retained.heap.norm} in B/op.
 * <p>
 * The used heap is measured after two full collections before and after every iteration, and the
 * difference is divided by the operations executed in that iteration, including those a worker runs while
 * it waits to start and stop measuring. A benchmark method whose operation
 * leaves state live across the iteration reports the size of that state per operation; any other method
 * reports approximately zero.
 * <p>
 * JMH runs {@code @Setup(Level.Trial)} inside the first warmup iteration and
 * {@code @TearDown(Level.Trial)} before the last iteration's {@code afterIteration}, so a benchmark that
 * retains state needs at least one warmup iteration, must release state only at trial teardown, and sees
 * its last measured iteration drop to a negative value. Results are therefore aggregated with
 * {@code MAX}, which ignores that final iteration.
 * <p>
 * Use with {@code -prof io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler}.
 */
public final class RetainedHeapProfiler implements InternalProfiler
{
    private static final String RETAINED_HEAP = "retained.heap.norm";
    private static final String UNIT = "B/op";
    private static final int COLLECTIONS = 2;

    private long usedBefore;

    @Override
    public void beforeIteration(
        BenchmarkParams benchmarkParams,
        IterationParams iterationParams)
    {
        usedBefore = usedAfterCollections();
    }

    @Override
    public Collection<? extends Result> afterIteration(
        BenchmarkParams benchmarkParams,
        IterationParams iterationParams,
        IterationResult result)
    {
        final long retained = usedAfterCollections() - usedBefore;
        final long operations = result.getMetadata().getAllOps();
        final double perOperation = operations != 0L ? (double) retained / operations : 0.0;

        return List.of(new ScalarResult(RETAINED_HEAP, perOperation, UNIT, AggregationPolicy.MAX));
    }

    @Override
    public String getDescription()
    {
        return "Heap retained per operation after full collections";
    }

    private static long usedAfterCollections()
    {
        final Runtime runtime = Runtime.getRuntime();

        for (int collection = 0; collection < COLLECTIONS; collection++)
        {
            System.gc();
        }

        return runtime.totalMemory() - runtime.freeMemory();
    }
}
