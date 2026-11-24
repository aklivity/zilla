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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache.bench;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.agrona.IoUtil;
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

import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheFile;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

/**
 * Measures the lookups an ingested keyed record performs against the active segment's hash index when
 * {@code keys} distinct keys are already indexed: {@code lastMiss} looks up a key that is not in the
 * segment, as the first record for a key does, and {@code lastHit} looks up the oldest key, then walks
 * its entries with {@code lower} until none remain. The score should not grow with {@code keys}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
public class KafkaCacheHashScanBM
{
    private static final int KEY_STRIDE = 0x9E3779B1;
    private static final int ENTRIES_PER_KEY = 3;

    @Param({"10000", "50000", "100000", "250000", "500000"})
    public int keys;

    private Path directory;
    private KafkaCacheFile.HashScan hashScan;
    private int missKey;
    private int hitKey;

    @Setup
    public void init() throws IOException
    {
        final int entries = keys * ENTRIES_PER_KEY;
        final MutableDirectBufferEx appendBuf = new UnsafeBufferEx(ByteBuffer.allocate(64 * 1024));

        this.directory = Files.createTempDirectory("kafka-cache-hash-scan");
        this.hashScan = new KafkaCacheFile.HashScan(directory, 0L, (entries + 1) * Long.BYTES, appendBuf, long[]::new);

        for (int round = 0; round < ENTRIES_PER_KEY; round++)
        {
            for (int key = 0; key < keys; key++)
            {
                hashScan.appendLong((key * KEY_STRIDE & 0xffff_ffffL) << 32 | (round * keys + key + 1));
            }
        }

        this.hitKey = 0;
        this.missKey = keys * KEY_STRIDE;
    }

    @TearDown
    public void destroy()
    {
        hashScan.close();
        IoUtil.delete(directory.toFile(), true);
    }

    @Benchmark
    public long lastMiss()
    {
        return hashScan.last(missKey);
    }

    @Benchmark
    public long lastHit()
    {
        long cursor = hashScan.last(hitKey);
        for (int walked = 0; walked < ENTRIES_PER_KEY; walked++)
        {
            cursor = hashScan.lower(hitKey, cursor);
        }
        return cursor;
    }
}
