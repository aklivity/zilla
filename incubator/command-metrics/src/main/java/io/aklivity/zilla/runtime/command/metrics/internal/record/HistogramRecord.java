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

import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.Kind.HISTOGRAM;

import java.util.function.IntFunction;
import java.util.function.LongSupplier;

import io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader;

public class HistogramRecord implements MetricRecord
{
    private long packedBindingId;
    private long packedMetricId;
    private int namespaceId;
    private int bindingId;
    private int metricId;
    private FileReader.Kind kind;
    private LongSupplier[][] readers;
    private IntFunction<String> labelResolver;

    public HistogramRecord(
        long packedBindingId,
        long packedMetricId,
        FileReader.Kind kind,
        LongSupplier[][] readers,
        IntFunction<String> labelResolver)
    {
        this.packedBindingId = packedBindingId;
        this.packedMetricId = packedMetricId;
        this.namespaceId = namespaceId(packedBindingId);
        this.bindingId = localId(packedBindingId);
        this.metricId = localId(packedMetricId);
        this.kind = kind;
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
    public FileReader.Kind kind()
    {
        return HISTOGRAM;
    }

    @Override
    public String stringValue()
    {
        // TODO: Ati
        long[] stats = stats();
        return String.format("[min: %d | max: %d | cnt: %d | avg: %d]", stats[0], stats[1], stats[2], stats[3]);
    }

    private static int namespaceId(
        long packedId)
    {
        return (int) (packedId >> Integer.SIZE) & 0xffff_ffff;
    }

    private static int localId(
        long packedId)
    {
        return (int) (packedId >> 0) & 0xffff_ffff;
    }

    private long[] stats()
    {
        // TODO: Ati
        long minimum = readers[0][0].getAsLong();
        return new long[]{minimum, 42L, 77L, 88L};
        /*long count = 0L;
        long sum = 0L;
        int minIndex = -1;
        int maxIndex = -1;
        for (int bucketIndex = 0; bucketIndex < NUMBER_OF_VALUES.get(HISTOGRAM); bucketIndex++)
        {
            long bucketValue = readers.get(bucketIndex)[0].getAsLong();
            count += bucketValue;
            long value = HISTOGRAM_BUCKET_LIMITS.get(bucketIndex) - 1;
            sum += bucketValue * value;
            if (bucketValue != 0)
            {
                maxIndex = bucketIndex;
                if (minIndex == -1)
                {
                    minIndex = bucketIndex;
                }
            }
        }
        this.count = count;
        this.minimum = minIndex == -1 ? 0L : HISTOGRAM_BUCKET_LIMITS.get(minIndex) - 1;
        this.maximum = maxIndex == -1 ? 0L : HISTOGRAM_BUCKET_LIMITS.get(maxIndex) - 1;
        this.average = count == 0L ? 0L : sum / count;*/
    }
}
