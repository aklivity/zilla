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

import static io.aklivity.zilla.runtime.command.metrics.internal.utils.MetricUtils.HISTOGRAM_BUCKETS;
import static io.aklivity.zilla.runtime.command.metrics.internal.utils.MetricUtils.HISTOGRAM_BUCKET_LIMITS;
import static io.aklivity.zilla.runtime.command.metrics.internal.utils.MetricUtils.localId;
import static io.aklivity.zilla.runtime.command.metrics.internal.utils.MetricUtils.namespaceId;

import java.util.function.IntFunction;
import java.util.function.LongSupplier;

public class HistogramRecord implements MetricRecord
{
    private final int namespaceId;
    private final int bindingId;
    private final int metricId;
    private final LongSupplier[][] readers;
    private final IntFunction<String> labelResolver;

    public HistogramRecord(
        long packedBindingId,
        long packedMetricId,
        LongSupplier[][] readers,
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
        long[] stats = stats();
        return String.format("[min: %d | max: %d | cnt: %d | avg: %d]", stats[0], stats[1], stats[2], stats[3]);
    }

    private long[] stats()
    {
        long count = 0L;
        long sum = 0L;
        int minIndex = -1;
        int maxIndex = -1;

        long[] histogram = new long[HISTOGRAM_BUCKETS];
        for (int i = 0; i < HISTOGRAM_BUCKETS; i++)
        {
            for (LongSupplier[] reader : readers)
            {
                histogram[i] += reader[i].getAsLong();
            }
        }
        for (int i = 0; i < HISTOGRAM_BUCKETS; i++)
        {
            long bucketValue = histogram[i];
            count += bucketValue;
            long value = HISTOGRAM_BUCKET_LIMITS.get(i) - 1;
            sum += bucketValue * value;
            if (bucketValue != 0)
            {
                maxIndex = i;
                if (minIndex == -1)
                {
                    minIndex = i;
                }
            }
        }

        long minimum = minIndex == -1 ? 0L : HISTOGRAM_BUCKET_LIMITS.get(minIndex) - 1;
        long maximum = maxIndex == -1 ? 0L : HISTOGRAM_BUCKET_LIMITS.get(maxIndex) - 1;
        long average = count == 0L ? 0L : sum / count;

        return new long[]{minimum, maximum, count, average};
    }
}
