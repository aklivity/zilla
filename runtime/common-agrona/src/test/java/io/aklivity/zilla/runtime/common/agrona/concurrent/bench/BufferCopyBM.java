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

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.nativeOrder;
import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

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
import org.openjdk.jmh.annotations.TearDown;
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
 *   <li>{@code byteBuffer} / {@code byteBufferView} — {@code ByteBuffer.put} on a
 *       direct {@code allocateDirect} buffer, and on the cached {@code duplicate()}
 *       view {@code UnsafeBufferEx.putBytes} actually takes.</li>
 *   <li>{@code segmentSharedView} / {@code segmentConfinedView} — {@code ByteBuffer.put}
 *       on a {@link MemorySegment#asByteBuffer()} view over a native segment from a
 *       shared vs confined {@link Arena}. This is the path a segment-major mmap layout
 *       (mapped via {@code FileChannel.map(..., Arena)} so it can be unmapped by
 *       {@code Arena.close()} instead of {@code Unsafe.invokeCleaner}) would take to
 *       recover the fast path; the question is whether the arena session liveness
 *       check on each access erodes it below the plain-direct {@code byteBuffer} number.</li>
 *   <li>{@code segmentGlobalView} — {@code ByteBuffer.put} on an {@code asByteBuffer()}
 *       view over a GLOBAL-scoped segment ({@code ofAddress().reinterpret()}) covering
 *       the same memory. No arena session check, so it should match {@code byteBuffer}:
 *       a closeable arena can own the mapping lifetime (for {@code Arena.close()} unmap)
 *       while access still goes through this check-free view, the same way
 *       {@code UnsafeBufferEx} primitive access already routes through GLOBAL by address.</li>
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
    private ByteBuffer srcGlobalView;
    private ByteBuffer dstGlobalView;
    private Path mappedPath;
    private FileChannel mappedChannel;
    private ByteBuffer srcMapped;
    private ByteBuffer dstMapped;
    private Arena sharedArena;
    private Arena confinedArena;
    private ByteBuffer srcSharedView;
    private ByteBuffer dstSharedView;
    private ByteBuffer srcConfinedView;
    private ByteBuffer dstConfinedView;
    private BaselineBufferEx baselineSrc;
    private BaselineBufferEx baselineDst;

    @Param({"16", "64", "256", "1024", "4096", "16384"})
    private int size;

    @Setup
    public void init() throws IOException
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

        // ByteBuffer views derived from GLOBAL-scoped segments (ofAddress().reinterpret()) over the
        // same memory — no arena session liveness check, so this is the check-free path a closeable
        // arena could still use for access while delegating only unmap to Arena.close()
        this.srcGlobalView = srcSeg.asByteBuffer().order(nativeOrder());
        this.dstGlobalView = dstSeg.asByteBuffer().order(nativeOrder());

        // today's production path: a MappedByteBuffer straight from FileChannel.map() (no Arena) —
        // the buffer UnsafeBufferEx wraps for the mmap'd layouts and kafka cache today, unmapped via
        // IoUtil.unmap (Unsafe.invokeCleaner). The regression baseline a segment-major rewrite must
        // not lose against.
        this.mappedPath = Files.createTempFile("buffercopy", ".dat");
        this.mappedChannel = FileChannel.open(mappedPath, READ, WRITE);
        this.mappedChannel.write(ByteBuffer.allocate(65536), 0);
        this.srcMapped = mappedChannel.map(READ_WRITE, 0, 32768).order(nativeOrder());
        this.dstMapped = mappedChannel.map(READ_WRITE, 32768, 32768).order(nativeOrder());

        // segment-backed ByteBuffer views (segment.asByteBuffer()) over native MemorySegments — the
        // path a segment-major mmap layout would take to recover the ByteBuffer.put fast path after
        // mapping via FileChannel.map(..., Arena). Shared and confined arenas carry different
        // per-access session liveness checks; shared is required when a layout is unmapped from a
        // thread other than the one that maps it, confined is the cheaper check if both happen on
        // the owning worker thread.
        this.sharedArena = Arena.ofShared();
        final MemorySegment srcShared = sharedArena.allocate(32768);
        final MemorySegment dstShared = sharedArena.allocate(32768);
        MemorySegment.copy(srcSeg, 0, srcShared, 0, srcShared.byteSize());
        this.srcSharedView = srcShared.asByteBuffer().order(nativeOrder());
        this.dstSharedView = dstShared.asByteBuffer().order(nativeOrder());

        this.confinedArena = Arena.ofConfined();
        final MemorySegment srcConfined = confinedArena.allocate(32768);
        final MemorySegment dstConfined = confinedArena.allocate(32768);
        MemorySegment.copy(srcSeg, 0, srcConfined, 0, srcConfined.byteSize());
        this.srcConfinedView = srcConfined.asByteBuffer().order(nativeOrder());
        this.dstConfinedView = dstConfined.asByteBuffer().order(nativeOrder());

        this.baselineSrc = new BaselineBufferEx(src);
        this.baselineDst = new BaselineBufferEx(dst);
    }

    @TearDown
    public void destroy() throws IOException
    {
        sharedArena.close();
        confinedArena.close();
        mappedChannel.close();
        Files.deleteIfExists(mappedPath);
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
    public void mappedByteBuffer()
    {
        dstMapped.put(0, srcMapped, 0, size);
    }

    @Benchmark
    public void segmentGlobalView()
    {
        dstGlobalView.put(0, srcGlobalView, 0, size);
    }

    @Benchmark
    public void segmentSharedView()
    {
        dstSharedView.put(0, srcSharedView, 0, size);
    }

    @Benchmark
    public void segmentConfinedView()
    {
        dstConfinedView.put(0, srcConfinedView, 0, size);
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
