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
package io.aklivity.zilla.runtime.command.metrics.internal.record;

import static io.aklivity.zilla.runtime.command.metrics.internal.layout.HistogramsLayout.BUCKETS;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.HistogramsLayout.BUCKET_LIMITS;
import static io.aklivity.zilla.runtime.command.metrics.internal.utils.MetricUtils.localId;
import static io.aklivity.zilla.runtime.command.metrics.internal.utils.MetricUtils.namespaceId;

import java.util.function.IntFunction;
import java.util.function.LongSupplier;

import io.aklivity.zilla.runtime.command.metrics.internal.utils.LongArrayFunction;

public class HistogramRecord implements MetricRecord
{
    private final int namespaceId;
    private final int bindingId;
    private final int metricId;
    private final LongSupplier[][] readers;
    private final IntFunction<String> labelResolver;
    private final LongArrayFunction<String> valueFormatter;

    private long[] stats;

    public HistogramRecord(
        long packedBindingId,
        long packedMetricId,
        LongSupplier[][] readers,
        IntFunction<String> labelResolver,
        LongArrayFunction<String> valueFormatter)
    {
        this.namespaceId = namespaceId(packedBindingId);
        this.bindingId = localId(packedBindingId);
        this.metricId = localId(packedMetricId);
        this.readers = readers;
        this.labelResolver = labelResolver;
        this.valueFormatter = valueFormatter;
    }

    @Override
    public String namespaceName()
    {
        return labelResolver.apply(namespaceId);
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

    @Override
    public String stringValue()
    {
        if (stats == null)
        {
            update();
        }
        return valueFormatter.apply(stats);
    }

    @Override
    public void update()
    {
        stats = stats();
    }

    private long[] stats()
    {
        long count = 0L;
        long sum = 0L;
        int minIndex = -1;
        int maxIndex = -1;
        long[] histogram = new long[BUCKETS];

        for (int i = 0; i < BUCKETS; i++)
        {
            for (LongSupplier[] reader : readers)
            {
                histogram[i] += reader[i].getAsLong();
            }
            long bucketCount = histogram[i];
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
        return new long[]{minimum, maximum, count, average};
    }

    private long getValue(
        int index)
    {
        return BUCKET_LIMITS.get(index) - 1;
    }
}
