/*
 * Copyright 2021-2022 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.exporter.prometheus.internal.record;

import static io.aklivity.zilla.runtime.exporter.prometheus.internal.layout.HistogramsLayout.BUCKETS;
import static io.aklivity.zilla.runtime.exporter.prometheus.internal.layout.HistogramsLayout.BUCKET_LIMITS;
import static io.aklivity.zilla.runtime.exporter.prometheus.internal.utils.NamespacedId.localId;
import static io.aklivity.zilla.runtime.exporter.prometheus.internal.utils.NamespacedId.namespaceId;

import java.util.function.IntFunction;
import java.util.function.LongSupplier;

import io.aklivity.zilla.runtime.exporter.prometheus.internal.utils.QuintFunction;

public class HistogramRecord implements MetricRecord
{
    private final LongSupplier[][] readers;
    private final QuintFunction<String, String, String, String, long[], long[]> valueFormatter;
    private final String namespaceName;
    private final String bindingName;
    private final String metricName;

    private long[] values;
    private long[] stats;

    public HistogramRecord(
        long packedBindingId,
        long packedMetricId,
        LongSupplier[][] readers,
        IntFunction<String> labelResolver,
        QuintFunction<String, String, String, String, long[], long[]> valueFormatter)
    {
        this.readers = readers;
        this.valueFormatter = valueFormatter;
        this.namespaceName = labelResolver.apply(namespaceId(packedBindingId));
        this.bindingName = labelResolver.apply(localId(packedBindingId));
        this.metricName = labelResolver.apply(localId(packedMetricId));
    }

    @Override
    public String namespaceName()
    {
        return namespaceName;
    }

    @Override
    public String bindingName()
    {
        return bindingName;
    }

    @Override
    public String metricName()
    {
        return metricName;
    }

    @Override
    public String stringValue()
    {
        if (stats == null)
        {
            update();
        }
        return valueFormatter.apply(metricName, namespaceName, bindingName, values, stats);
    }

    @Override
    public void update()
    {
        long count = 0L;
        long sum = 0L;
        int minIndex = -1;
        int maxIndex = -1;
        values = new long[BUCKETS];

        for (int i = 0; i < BUCKETS; i++)
        {
            for (LongSupplier[] reader : readers)
            {
                values[i] += reader[i].getAsLong();
            }
            long bucketCount = values[i];
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
        stats = new long[]{minimum, maximum, sum, count, average};
    }

    private long getValue(
        int index)
    {
        return BUCKET_LIMITS.get(index) - 1;
    }
}
