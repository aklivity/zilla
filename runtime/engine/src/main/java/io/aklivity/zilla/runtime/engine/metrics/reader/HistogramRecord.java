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
package io.aklivity.zilla.runtime.engine.metrics.reader;

import static io.aklivity.zilla.runtime.engine.internal.layouts.metrics.HistogramsLayout.BUCKETS;
import static io.aklivity.zilla.runtime.engine.internal.layouts.metrics.HistogramsLayout.BUCKET_LIMITS;
import static io.aklivity.zilla.runtime.engine.namespace.NamespacedId.namespaceId;

import java.util.Objects;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import org.agrona.collections.Int2IntHashMap;

public class HistogramRecord implements MetricRecord
{
    private static final Int2IntHashMap MS_BUCKET_MAP = generateMillisecondsBucketMap();

    private final long bindingId;
    private final long metricId;
    private final int namespaceId;
    private final LongSupplier[] readers;
    private final LongFunction<String> labelResolver;

    private long[] bucketValues = new long[BUCKETS];
    private long[] millisecondBucketValues = null;

    public HistogramRecord(
        long bindingId,
        long metricId,
        LongSupplier[] readers,
        LongFunction<String> labelResolver)
    {
        this.bindingId = bindingId;
        this.metricId = metricId;
        this.namespaceId = namespaceId(bindingId);
        this.readers = readers;
        this.labelResolver = labelResolver;
    }

    @Override
    public long bindingId()
    {
        return bindingId;
    }

    @Override
    public String namespace()
    {
        // implicit int -> long conversion, it's OK
        return labelResolver.apply(namespaceId);
    }

    @Override
    public String binding()
    {
        return labelResolver.apply(bindingId);
    }

    @Override
    public String metric()
    {
        return labelResolver.apply(metricId);
    }

    public int buckets()
    {
        return BUCKETS;
    }

    public long[] bucketLimits()
    {
        return BUCKET_LIMITS;
    }

    public void update()
    {
        for (int i = 0; i < BUCKETS; i++)
        {
            bucketValues[i] = readers[i].getAsLong();
        }
        millisecondBucketValues = null;
    }

    public long[] bucketValues()
    {
        return bucketValues;
    }

    public long[] millisecondBucketValues()
    {
        if (millisecondBucketValues == null)
        {
            millisecondBucketValues = new long[BUCKETS];
            for (int i = 0; i < BUCKETS; i++)
            {
                int msIndex = MS_BUCKET_MAP.get(i);
                millisecondBucketValues[msIndex] += bucketValues[i];
            }
        }
        return millisecondBucketValues;
    }

    public long[] stats()
    {
        return stats(bucketValues);
    }

    public long[] millisecondStats()
    {
        if (millisecondBucketValues == null)
        {
            millisecondBucketValues();
        }
        return stats(millisecondBucketValues);
    }

    private long[] stats(
        long[] bucketValues)
    {
        long count = 0L;
        long sum = 0L;
        int minIndex = -1;
        int maxIndex = -1;
        for (int i = 0; i < BUCKETS; i++)
        {
            long bucketCount = bucketValues[i];
            count += bucketCount;
            sum += bucketCount * getValue(i);
            if (bucketCount != 0)
            {
                maxIndex = i;
                if (minIndex == -1)
                {
                    minIndex = i;
                }
            }
        }

        long minimum = minIndex == -1 ? 0L : getValue(minIndex);
        long maximum = maxIndex == -1 ? 0L : getValue(maxIndex);
        long average = count == 0L ? 0L : sum / count;
        return new long[]{minimum, maximum, sum, count, average};
    }

    private long getValue(
        int index)
    {
        return BUCKET_LIMITS[index] - 1;
    }

    @Override
    public boolean equals(
        Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        HistogramRecord that = (HistogramRecord) o;
        return namespaceId == that.namespaceId && bindingId == that.bindingId && metricId == that.metricId;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(namespaceId, bindingId, metricId);
    }

    private static Int2IntHashMap generateMillisecondsBucketMap()
    {
        Int2IntHashMap map = new Int2IntHashMap(-1);
        // source bucket index -> dividing source bucket limit by 1 million -> destination bucket index
        map.put(0, 0); // 2 -> 2
        map.put(1, 0); // 4 -> 2
        map.put(2, 0); // 8 -> 2
        map.put(3, 0); // 16 -> 2
        map.put(4, 0); // 32 -> 2
        map.put(5, 0); // 64 -> 2
        map.put(6, 0); // 128 -> 2
        map.put(7, 0); // 256 -> 2
        map.put(8, 0); // 512 -> 2
        map.put(9, 0); // 1024 -> 2
        map.put(10, 0); // 2048 -> 2
        map.put(11, 0); // 4096 -> 2
        map.put(12, 0); // 8192 -> 2
        map.put(13, 0); // 16384 -> 2
        map.put(14, 0); // 32768 -> 2
        map.put(15, 0); // 65536 -> 2
        map.put(16, 0); // 131072 -> 2
        map.put(17, 0); // 262144 -> 2
        map.put(18, 0); // 524288 -> 2
        map.put(19, 0); // 1048576 -> 2
        map.put(20, 1); // 2097152 -> 4
        map.put(21, 2); // 4194304 -> 4
        map.put(22, 3); // 8388608 -> 8
        map.put(23, 4); // 16777216 -> 16
        map.put(24, 5); // 33554432 -> 33
        map.put(25, 6); // 67108864 -> 67
        map.put(26, 7); // 134217728 -> 134
        map.put(27, 8); // 268435456 -> 268
        map.put(28, 9); // 536870912 -> 536
        map.put(29, 10); // 1073741824 -> 1073
        map.put(30, 11); // 2147483648 -> 2147
        map.put(31, 12); // 4294967296 -> 4294
        map.put(32, 13); // 8589934592 -> 8589
        map.put(33, 14); // 17179869184 -> 17179
        map.put(34, 15); // 34359738368 -> 34359
        map.put(35, 16); // 68719476736 -> 68719
        map.put(36, 17); // 137438953472 -> 137438
        map.put(37, 18); // 274877906944 -> 274877
        map.put(38, 19); // 549755813888 -> 549755
        map.put(39, 20); // 1099511627776 -> 1099511
        map.put(40, 21); // 2199023255552 -> 2199023
        map.put(41, 22); // 4398046511104 -> 4398046
        map.put(42, 23); // 8796093022208 -> 8796093
        map.put(43, 24); // 17592186044416 -> 17592186
        map.put(44, 25); // 35184372088832 -> 35184372
        map.put(45, 26); // 70368744177664 -> 70368744
        map.put(46, 27); // 140737488355328 -> 140737488
        map.put(47, 28); // 281474976710656 -> 281474976
        map.put(48, 29); // 562949953421312 -> 562949953
        map.put(49, 30); // 1125899906842624 -> 1125899906
        map.put(50, 31); // 2251799813685248 -> 2251799813
        map.put(51, 32); // 4503599627370496 -> 4503599627
        map.put(52, 33); // 9007199254740992 -> 9007199254
        map.put(53, 34); // 18014398509481984 -> 18014398509
        map.put(54, 35); // 36028797018963968 -> 36028797018
        map.put(55, 36); // 72057594037927936 -> 72057594037
        map.put(56, 37); // 144115188075855872 -> 144115188075
        map.put(57, 38); // 288230376151711744 -> 288230376151
        map.put(58, 39); // 576460752303423488 -> 576460752303
        map.put(59, 40); // 1152921504606846976 -> 1152921504606
        map.put(60, 41); // 2305843009213693952 -> 2305843009213
        map.put(61, 42); // 4611686018427387904 -> 4611686018427
        map.put(62, 43);
        return map;
    }
}
