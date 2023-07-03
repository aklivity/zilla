/*
 * Copyright 2021-2023 Aklivity Inc.
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
import static io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId.localId;
import static io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId.namespaceId;

import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;

public class HistogramRecord implements MetricRecord
{
    private final int namespaceId;
    private final int bindingId;
    private final int metricId;
    private final LongSupplier[] readers;
    private final IntFunction<String> labelResolver;

    private long[] bucketValues = new long[BUCKETS];

    public HistogramRecord(
        long packedBindingId,
        long packedMetricId,
        LongSupplier[] readers,
        IntFunction<String> labelResolver)
    {
        this.namespaceId = namespaceId(packedBindingId);
        this.bindingId = localId(packedBindingId);
        this.metricId = localId(packedMetricId);
        this.readers = readers;
        this.labelResolver = labelResolver;
    }

    @Override
    public String namespaceName()
    {
        return labelResolver.apply(namespaceId);
    }

    @Override
    public int bindingId()
    {
        return bindingId;
    }

    @Override
    public String bindingName()
    {
        return labelResolver.apply(bindingId);
    }

    @Override
    public String metricName()
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
    }

    public long[] bucketValues()
    {
        return bucketValues;
    }

    public long[] stats()
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
}
