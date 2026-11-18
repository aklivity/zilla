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
package io.aklivity.zilla.runtime.engine.metrics.reader;

import static io.aklivity.zilla.runtime.engine.internal.layouts.metrics.HistogramsLayout.BUCKETS;
import static io.aklivity.zilla.runtime.engine.internal.layouts.metrics.HistogramsLayout.BUCKET_LIMITS;
import static io.aklivity.zilla.runtime.engine.internal.layouts.metrics.HistogramsLayout.SUM;
import static io.aklivity.zilla.runtime.engine.namespace.NamespacedId.namespaceId;

import java.util.Map;
import java.util.Objects;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.MetricsLayout;

public class HistogramRecord implements MetricRecord
{
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final double[] MILLISECOND_BUCKET_LIMITS = generateMillisecondBucketLimits();

    private final long bindingId;
    private final int metricId;
    private final int attributesId;
    private final int namespaceId;
    private final KindConfig bindingKind;
    private final LongSupplier[] readers;
    private final LongFunction<String> labelResolver;

    private long[] bucketValues = new long[BUCKETS];
    private long sum;

    public HistogramRecord(
        long bindingId,
        int metricId,
        int attributesId,
        int kind,
        LongSupplier[] readers,
        LongFunction<String> labelResolver)
    {
        this.bindingId = bindingId;
        this.metricId = metricId;
        this.attributesId = attributesId;
        this.namespaceId = namespaceId(bindingId);
        this.bindingKind = kind != MetricsLayout.NO_KIND ? KindConfig.values()[kind] : null;
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

    @Override
    public int attributesId()
    {
        return attributesId;
    }

    @Override
    public KindConfig bindingKind()
    {
        return bindingKind;
    }

    @Override
    public Map<String, String> attributes()
    {
        return MetricRecordHelper.parseAttributes(attributesId, labelResolver);
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
        sum = readers[SUM].getAsLong();
    }

    public long[] bucketValues()
    {
        return bucketValues;
    }

    public double[] millisecondBucketLimits()
    {
        return MILLISECOND_BUCKET_LIMITS;
    }

    public long[] stats()
    {
        return stats(bucketValues, sum);
    }

    public long[] millisecondStats()
    {
        long[] stats = stats(bucketValues, sum / NANOS_PER_MILLI);
        stats[0] /= NANOS_PER_MILLI;
        stats[1] /= NANOS_PER_MILLI;
        return stats;
    }

    private long[] stats(
        long[] bucketValues,
        long sum)
    {
        long count = 0L;
        int minIndex = -1;
        int maxIndex = -1;
        for (int i = 0; i < BUCKETS; i++)
        {
            long bucketCount = bucketValues[i];
            count += bucketCount;
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
        return namespaceId == that.namespaceId && bindingId == that.bindingId &&
            metricId == that.metricId && attributesId == that.attributesId;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(namespaceId, bindingId, metricId, attributesId);
    }

    private static double[] generateMillisecondBucketLimits()
    {
        double[] limits = new double[BUCKETS];
        for (int i = 0; i < BUCKETS; i++)
        {
            limits[i] = BUCKET_LIMITS[i] / (double) NANOS_PER_MILLI;
        }
        return limits;
    }
}
