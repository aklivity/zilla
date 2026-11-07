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

import java.util.ArrayList;
import java.util.List;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@Fork(0)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
public class RetainedHeapBenchmark
{
    public static final int OPERATIONS = 2_000;
    public static final int RETAINED_LENGTH = 1_024;

    private final List<byte[]> retained = new ArrayList<>();

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public void retain()
    {
        for (int operation = 0; operation < OPERATIONS; operation++)
        {
            retained.add(new byte[RETAINED_LENGTH]);
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public void release(
        Blackhole blackhole)
    {
        for (int operation = 0; operation < OPERATIONS; operation++)
        {
            blackhole.consume(new byte[RETAINED_LENGTH]);
        }
    }
}
