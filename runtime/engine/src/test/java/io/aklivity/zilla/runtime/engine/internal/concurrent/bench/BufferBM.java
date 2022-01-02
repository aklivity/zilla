/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.concurrent.bench;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.nativeOrder;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.agrona.IoUtil.createEmptyFile;
import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;
import static org.agrona.concurrent.ringbuffer.RingBufferDescriptor.TRAILER_LENGTH;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Control;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class BufferBM
{
    private AtomicBuffer buffer;
    private ManyToOneRingBuffer source;
    private ManyToOneRingBuffer target;

    private MutableDirectBuffer writeBuffer;

    @Setup(Level.Trial)
    public void init() throws IOException
    {
        final int capacity = 1024 * 1024 * 64 + TRAILER_LENGTH;
        final int payload = 256;

        final File bufferFile = new File("target/benchmarks/baseline/buffer").getAbsoluteFile();
        createEmptyFile(bufferFile, capacity).close();

        this.buffer = new UnsafeBuffer(mapExistingFile(bufferFile, "buffer"));
        this.source = new ManyToOneRingBuffer(buffer);
        this.target = new ManyToOneRingBuffer(buffer);

        this.writeBuffer = new UnsafeBuffer(allocateDirect(payload).order(nativeOrder()));
        this.writeBuffer.setMemory(0, payload, (byte)new Random().nextInt(256));
    }

    @TearDown(Level.Trial)
    public void destroy()
    {
        unmap(buffer.byteBuffer());
    }

    @Setup(Level.Iteration)
    public void reset()
    {
        buffer.setMemory(target.buffer().capacity() - TRAILER_LENGTH, TRAILER_LENGTH, (byte)0);
        buffer.putLongOrdered(0, 0L);
    }

    @Benchmark
    @Group("multiple")
    @GroupThreads(2)
    public void writer(
        final Control control) throws Exception
    {
        while (!control.stopMeasurement &&
                !target.write(0x02, writeBuffer, 0, writeBuffer.capacity()))
        {
            Thread.yield();
        }
    }

    @Benchmark
    @Group("multiple")
    @GroupThreads(1)
    public void reader(
        final Control control) throws Exception
    {
        while (!control.stopMeasurement &&
               source.read((msgTypeId, buffer, offset, length) -> {}) == 0)
        {
            Thread.yield();
        }
    }

    @Benchmark
    public void single(
        final Control control) throws Exception
    {
        while (!control.stopMeasurement &&
                (!target.write(0x02, writeBuffer, 0, writeBuffer.capacity()) ||
                 source.read((msgTypeId, buffer, offset, length) -> {}) == 0))
        {
            Thread.yield();
        }
    }

    @Benchmark
    public void batched(
        final Control control) throws Exception
    {
        while (!control.stopMeasurement &&
                !target.write(0x02, writeBuffer, 0, writeBuffer.capacity()))
        {
            Thread.yield();
        }

        while (!control.stopMeasurement &&
                source.read((msgTypeId, buffer, offset, length) -> {}) == 0)
        {
            Thread.yield();
        }
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                .include(BufferBM.class.getSimpleName())
                .forks(0)
                .build();

        new Runner(opt).run();
    }
}
