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
package io.aklivity.zilla.runtime.engine.test.internal.exporter;

import static io.aklivity.zilla.runtime.engine.internal.layouts.metrics.HistogramsLayout.BUCKET_LIMITS;

import java.util.List;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import io.aklivity.zilla.config.engine.test.internal.exporter.config.TestExporterOptionsConfig.Bucket;
import io.aklivity.zilla.config.engine.test.internal.exporter.config.TestExporterOptionsConfig.Metric;
import io.aklivity.zilla.runtime.engine.metrics.Collector;
import io.aklivity.zilla.runtime.engine.metrics.reader.HistogramRecord;
import io.aklivity.zilla.runtime.engine.metrics.reader.MetricRecord;
import io.aklivity.zilla.runtime.engine.metrics.reader.ScalarRecord;

final class TestExporterMetrics
{
    private static final String COUNTER = "counter";
    private static final String GAUGE = "gauge";
    private static final String HISTOGRAM = "histogram";

    private final Collector collector;
    private final LongFunction<String> labels;
    private final String namespace;
    private final List<Metric> expected;
    private final long[][] observed;

    TestExporterMetrics(
        Collector collector,
        LongFunction<String> labels,
        String namespace,
        List<Metric> expected)
    {
        this.collector = collector;
        this.labels = labels;
        this.namespace = namespace;
        this.expected = expected;
        this.observed = new long[expected.size()][];
    }

    void update()
    {
        final long[][] values = new long[expected.size()][];
        for (int i = 0; i < values.length; i++)
        {
            values[i] = new long[HISTOGRAM.equals(expected.get(i).kind) ? BUCKET_LIMITS.length : 1];
        }

        for (long[] ids : collector.counterIds())
        {
            LongSupplier reader = collector.counter(ids[0], (int) ids[1], (int) ids[2]);
            accumulate(COUNTER, new ScalarRecord(ids[0], (int) ids[1], (int) ids[2], (int) ids[3], reader, labels),
                reader, values);
        }

        for (long[] ids : collector.gaugeIds())
        {
            LongSupplier reader = collector.gauge(ids[0], (int) ids[1], (int) ids[2]);
            accumulate(GAUGE, new ScalarRecord(ids[0], (int) ids[1], (int) ids[2], (int) ids[3], reader, labels),
                reader, values);
        }

        for (long[] ids : collector.histogramIds())
        {
            LongSupplier[] readers = collector.histogram(ids[0], (int) ids[1], (int) ids[2]);
            HistogramRecord record = new HistogramRecord(ids[0], (int) ids[1], (int) ids[2], (int) ids[3], readers, labels);
            for (int i = 0; i < values.length; i++)
            {
                if (matches(HISTOGRAM, expected.get(i), record))
                {
                    for (int b = 0; b < readers.length; b++)
                    {
                        values[i][b] += readers[b].getAsLong();
                    }
                }
            }
        }

        System.arraycopy(values, 0, observed, 0, values.length);
    }

    String mismatches()
    {
        final StringBuilder mismatches = new StringBuilder();

        for (int i = 0; i < expected.size(); i++)
        {
            Metric metric = expected.get(i);
            long[] values = observed[i] != null
                ? observed[i]
                : new long[HISTOGRAM.equals(metric.kind) ? BUCKET_LIMITS.length : 1];

            String mismatch = HISTOGRAM.equals(metric.kind)
                ? histogramMismatch(metric, values)
                : metric.value != values[0] ? String.format("expected value %d, actual value %d", metric.value, values[0]) : null;

            if (mismatch != null)
            {
                mismatches.append(String.format("%n  %s %s %s%s: %s", metric.kind, metric.binding, metric.name,
                    metric.attributes != null ? metric.attributes : "", mismatch));
            }
        }

        return mismatches.isEmpty() ? null : mismatches.toString();
    }

    private void accumulate(
        String kind,
        MetricRecord record,
        LongSupplier reader,
        long[][] values)
    {
        for (int i = 0; i < values.length; i++)
        {
            if (matches(kind, expected.get(i), record))
            {
                values[i][0] += reader.getAsLong();
            }
        }
    }

    private boolean matches(
        String kind,
        Metric metric,
        MetricRecord record)
    {
        final int separator = metric.binding.indexOf(':');
        final String bindingNamespace = separator != -1 ? metric.binding.substring(0, separator) : namespace;
        final String bindingName = metric.binding.substring(separator + 1);

        return kind.equals(metric.kind) &&
            metric.name.equals(record.metric()) &&
            bindingName.equals(record.binding()) &&
            bindingNamespace.equals(record.namespace()) &&
            (metric.attributes == null || metric.attributes.equals(record.attributes()));
    }

    private String histogramMismatch(
        Metric metric,
        long[] buckets)
    {
        long count = 0L;
        for (long bucket : buckets)
        {
            count += bucket;
        }

        boolean mismatch = metric.count != null && metric.count != count;

        if (metric.buckets != null)
        {
            for (int b = 0; b < BUCKET_LIMITS.length && !mismatch; b++)
            {
                mismatch = expectedBucket(metric.buckets, BUCKET_LIMITS[b]) != buckets[b];
            }
            for (int e = 0; e < metric.buckets.size() && !mismatch; e++)
            {
                mismatch = indexOf(metric.buckets.get(e).limit) == -1;
            }
        }

        return mismatch
            ? String.format("expected count %s, buckets %s, actual count %d, buckets %s",
                metric.count, expectedBuckets(metric.buckets), count, observedBuckets(buckets))
            : null;
    }

    private long expectedBucket(
        List<Bucket> buckets,
        long limit)
    {
        long count = 0L;
        for (Bucket bucket : buckets)
        {
            if (bucket.limit == limit)
            {
                count += bucket.count;
            }
        }
        return count;
    }

    private int indexOf(
        long limit)
    {
        int index = -1;
        for (int b = 0; b < BUCKET_LIMITS.length && index == -1; b++)
        {
            if (BUCKET_LIMITS[b] == limit)
            {
                index = b;
            }
        }
        return index;
    }

    private String expectedBuckets(
        List<Bucket> buckets)
    {
        final StringBuilder expectedBuckets = new StringBuilder("[");
        if (buckets != null)
        {
            for (Bucket bucket : buckets)
            {
                if (expectedBuckets.length() > 1)
                {
                    expectedBuckets.append(", ");
                }
                expectedBuckets.append(String.format("{limit: %d, count: %d}", bucket.limit, bucket.count));
            }
        }
        return expectedBuckets.append("]").toString();
    }

    private String observedBuckets(
        long[] buckets)
    {
        final StringBuilder observedBuckets = new StringBuilder("[");
        for (int b = 0; b < buckets.length; b++)
        {
            if (buckets[b] != 0L)
            {
                if (observedBuckets.length() > 1)
                {
                    observedBuckets.append(", ");
                }
                observedBuckets.append(String.format("{limit: %d, count: %d}", BUCKET_LIMITS[b], buckets[b]));
            }
        }
        return observedBuckets.append("]").toString();
    }
}
