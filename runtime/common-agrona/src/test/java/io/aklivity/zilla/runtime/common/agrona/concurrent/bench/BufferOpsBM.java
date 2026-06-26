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

import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.nativeOrder;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;

import org.agrona.BufferUtil;
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
 * ordered ops lack. {@code fenced*} prototypes a Lever 1 lowering — plain unaligned
 * access plus a standalone {@code VarHandle.releaseFence}/{@code acquireFence} — to
 * confirm it recovers the regression before changing the production accessors.
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
    private static final MemorySegment GLOBAL = MemorySegment.NULL.reinterpret(Long.MAX_VALUE);

    private AtomicBufferEx buffer;
    private long address;
    private long value = 0x0102030405060708L;

    @Param({"baseline", "unsafe", "safe"})
    private String impl;

    @Setup
    public void init()
    {
        final ByteBuffer byteBuffer = allocateDirect(256).order(nativeOrder());
        this.address = BufferUtil.address(byteBuffer);
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

    @Benchmark
    public long fencedLong()
    {
        VarHandle.releaseFence();
        GLOBAL.set(JAVA_LONG_UNALIGNED, address, ++value);
        final long got = GLOBAL.get(JAVA_LONG_UNALIGNED, address);
        VarHandle.acquireFence();
        return got;
    }

    @Benchmark
    public int fencedInt()
    {
        VarHandle.releaseFence();
        GLOBAL.set(JAVA_INT_UNALIGNED, address, (int)++value);
        final int got = GLOBAL.get(JAVA_INT_UNALIGNED, address);
        VarHandle.acquireFence();
        return got;
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
