/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.common.agrona.concurrent.bench;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.nativeOrder;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.nio.ByteBuffer;

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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.runtime.common.agrona.buffer.AtomicBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.SafeBuffer;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.agrona.concurrent.baseline.BaselineBufferEx;

/**
 * Factorial micro-benchmark isolating single-word buffer access from the ring-buffer
 * machinery, to attribute the {@code BufferBM} single-threaded delta between the
 * {@code Unsafe}-backed {@code baseline} and the FFM/{@code GLOBAL} {@code unsafe}
 * implementation.
 * <p>
 * The hypothesis: plain primitive access ({@code JAVA_LONG_UNALIGNED}, no alignment
 * check) matches {@code Unsafe}, while ordered/volatile access goes through aligned
 * {@code VarHandle}s that carry a runtime 8-byte alignment check {@code Unsafe}'s
 * ordered ops lack. If so, {@code plain*} should be at parity across impls while
 * {@code ordered*} regresses on {@code unsafe} (and {@code safe}) relative to
 * {@code baseline}.
 * <p>
 * All variants run over a native (direct) buffer so {@code unsafe} takes the
 * {@code GLOBAL} path, matching the regressed {@code BufferBM} configuration.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@Fork(3)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@OutputTimeUnit(NANOSECONDS)
public class BufferOpsBM
{
    private AtomicBufferEx buffer;
    private long value = 0x0102030405060708L;

    @Param({"baseline", "unsafe", "safe"})
    private String impl;

    @Setup
    public void init()
    {
        final ByteBuffer byteBuffer = allocateDirect(256).order(nativeOrder());
        this.buffer = switch (impl)
        {
        case "safe" -> new SafeBuffer(byteBuffer);
        case "baseline" -> new BaselineBufferEx(byteBuffer);
        default -> new UnsafeBufferEx(byteBuffer);
        };
    }

    @Benchmark
    public long plainLong()
    {
        buffer.putLong(0, ++value);
        return buffer.getLong(0);
    }

    @Benchmark
    public long orderedLong()
    {
        buffer.putLongOrdered(0, ++value);
        return buffer.getLongVolatile(0);
    }

    @Benchmark
    public int plainInt()
    {
        buffer.putInt(0, (int)++value);
        return buffer.getInt(0);
    }

    @Benchmark
    public int orderedInt()
    {
        buffer.putIntOrdered(0, (int)++value);
        return buffer.getIntVolatile(0);
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                .include(BufferOpsBM.class.getSimpleName())
                .forks(0)
                .build();

        new Runner(opt).run();
    }
}
