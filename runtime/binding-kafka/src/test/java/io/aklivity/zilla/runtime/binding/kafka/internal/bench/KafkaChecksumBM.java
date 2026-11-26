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
package io.aklivity.zilla.runtime.binding.kafka.internal.bench;

import static io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaChecksum.combineCRC32C;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.nio.ByteBuffer;
import java.util.zip.CRC32C;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Cost of {@link io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaChecksum#combineCRC32C} for a value of
 * {@code length} bytes against the algorithm it replaced, which rebuilt and squared the zero-bit operator on every call,
 * and against the CRC32C of the same bytes.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(NANOSECONDS)
public class KafkaChecksumBM
{
    private static final int GF2_DIM = 32;

    @Param({"64", "1024", "4096"})
    public int length;

    private long crc1;
    private long crc2;
    private ByteBuffer data;
    private final CRC32C crc = new CRC32C();

    @Setup(Level.Trial)
    public void init()
    {
        final byte[] bytes = new byte[length];
        for (int i = 0; i < bytes.length; i++)
        {
            bytes[i] = (byte) (i * 31 + 7);
        }
        data = ByteBuffer.allocateDirect(length);
        data.put(bytes);

        final CRC32C seed = new CRC32C();
        seed.update(bytes, 0, 17);
        crc1 = seed.getValue();
        final CRC32C tail = new CRC32C();
        tail.update(bytes, 17, length - 17);
        crc2 = tail.getValue();

        if (legacyCombineCRC32C(crc1, crc2, length) != combineCRC32C(crc1, crc2, length))
        {
            throw new IllegalStateException("combineCRC32C does not match the legacy algorithm");
        }
    }

    @Benchmark
    public long combine()
    {
        return combineCRC32C(crc1, crc2, length);
    }

    @Benchmark
    public long legacyCombine()
    {
        return legacyCombineCRC32C(crc1, crc2, length);
    }

    @Benchmark
    public long crc32cOfValue()
    {
        crc.reset();
        data.clear();
        crc.update(data);
        return crc.getValue();
    }

    private static long legacyCombineCRC32C(
        long crc1,
        long crc2,
        long len2)
    {
        long combined = crc1;

        if (len2 > 0)
        {
            final long[] even = new long[GF2_DIM];
            final long[] odd = new long[GF2_DIM];

            odd[0] = 0x82F63B78L;
            long row = 1;
            for (int n = 1; n < GF2_DIM; n++)
            {
                odd[n] = row;
                row <<= 1;
            }

            square(even, odd);
            square(odd, even);

            long remaining = len2;
            do
            {
                square(even, odd);
                if ((remaining & 1) != 0)
                {
                    combined = times(even, combined);
                }
                remaining >>= 1;

                if (remaining == 0)
                {
                    break;
                }

                square(odd, even);
                if ((remaining & 1) != 0)
                {
                    combined = times(odd, combined);
                }
                remaining >>= 1;
            }
            while (remaining != 0);

            combined ^= crc2;
        }

        return combined;
    }

    private static long times(
        long[] mat,
        long vec)
    {
        long sum = 0;
        int index = 0;
        while (vec != 0)
        {
            if ((vec & 1) != 0)
            {
                sum ^= mat[index];
            }
            vec >>= 1;
            index++;
        }
        return sum;
    }

    private static void square(
        long[] square,
        long[] mat)
    {
        for (int n = 0; n < GF2_DIM; n++)
        {
            square[n] = times(mat, mat[n]);
        }
    }
}
