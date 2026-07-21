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
package io.aklivity.zilla.runtime.common.agrona.concurrent.bench;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.nativeOrder;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.lang.foreign.MemorySegment;
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

import io.aklivity.zilla.runtime.common.agrona.concurrent.baseline.BaselineBufferEx;

/**
 * Isolates the cost of single bulk byte copies between two native segments at a
 * range of payload sizes, comparing three variants used by Zilla's buffer
 * implementations:
 * <ul>
 *   <li>{@code layout} — {@link MemorySegment#copy(MemorySegment, java.lang.foreign.ValueLayout,
 *       long, MemorySegment, java.lang.foreign.ValueLayout, long, long)} with
 *       {@link java.lang.foreign.ValueLayout#JAVA_BYTE} layouts. This is the
 *       overload {@code UnsafeBufferEx.putBytes} currently calls on its
 *       {@code DirectBufferEx} fast path.</li>
 *   <li>{@code bulk} — {@link MemorySegment#copy(MemorySegment, long, MemorySegment, long, long)}.
 *       The pure bulk-byte intrinsic — lowers to {@code Unsafe.copyMemory}.</li>
 *   <li>{@code baseline} — Agrona's {@code UnsafeBuffer.putBytes} via the
 *       preserved {@link BaselineBufferEx}, which is the {@code sun.misc.Unsafe}
 *       implementation we are trying to match.</li>
 * </ul>
 * The hypothesis: {@code layout} has measurable per-call overhead vs {@code bulk},
 * and that overhead accounts for the residual single-threaded gap in {@code BufferBM}
 * that {@code BufferOpsBM} (single-word access only) cannot explain. If {@code bulk}
 * matches {@code baseline} at all sizes, replacing the layout-form sites in
 * {@code UnsafeBufferEx} closes the gap.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@Fork(3)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@OutputTimeUnit(NANOSECONDS)
public class BufferCopyBM
{
    private MemorySegment srcSeg;
    private MemorySegment dstSeg;
    private ByteBuffer srcBb;
    private ByteBuffer dstBb;
    private ByteBuffer srcBbView;
    private ByteBuffer dstBbView;
    private BaselineBufferEx baselineSrc;
    private BaselineBufferEx baselineDst;

    @Param({"16", "64", "256", "1024", "4096", "16384"})
    private int size;

    @Setup
    public void init()
    {
        final ByteBuffer src = allocateDirect(32768).order(nativeOrder());
        final ByteBuffer dst = allocateDirect(32768).order(nativeOrder());
        for (int i = 0; i < src.capacity(); i++)
        {
            src.put(i, (byte) (i & 0xff));
        }

        this.srcSeg = MemorySegment.ofAddress(BufferUtil.address(src)).reinterpret(src.capacity());
        this.dstSeg = MemorySegment.ofAddress(BufferUtil.address(dst)).reinterpret(dst.capacity());
        this.srcBb = src;
        this.dstBb = dst;

        // clean duplicates held at position 0, limit capacity — the byteBufferView indirection
        // UnsafeBufferEx.putBytes takes on its ByteBuffer.put fast path
        this.srcBbView = src.duplicate();
        this.srcBbView.clear();
        this.dstBbView = dst.duplicate();
        this.dstBbView.clear();

        this.baselineSrc = new BaselineBufferEx(src);
        this.baselineDst = new BaselineBufferEx(dst);
    }

    @Benchmark
    public void layout()
    {
        MemorySegment.copy(srcSeg, JAVA_BYTE, 0, dstSeg, JAVA_BYTE, 0, size);
    }

    @Benchmark
    public void bulk()
    {
        MemorySegment.copy(srcSeg, 0, dstSeg, 0, size);
    }

    @Benchmark
    public void byteBuffer()
    {
        dstBb.put(0, srcBb, 0, size);
    }

    @Benchmark
    public void byteBufferView()
    {
        dstBbView.put(0, srcBbView, 0, size);
    }

    @Benchmark
    public void baseline()
    {
        baselineDst.putBytes(0, baselineSrc, 0, size);
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                .include(BufferCopyBM.class.getSimpleName())
                .forks(0)
                .build();

        new Runner(opt).run();
    }
}
