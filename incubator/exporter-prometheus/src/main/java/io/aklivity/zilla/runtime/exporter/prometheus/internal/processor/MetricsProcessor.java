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
package io.aklivity.zilla.runtime.exporter.prometheus.internal.processor;

import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.COUNTER;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.GAUGE;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.HISTOGRAM;
import static io.aklivity.zilla.runtime.exporter.prometheus.internal.layout.HistogramsLayout.BUCKETS;
import static io.aklivity.zilla.runtime.exporter.prometheus.internal.layout.HistogramsLayout.BUCKET_LIMITS;

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.labels.LabelManager;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.layout.MetricsLayout;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.record.CounterGaugeRecord;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.record.HistogramRecord;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.record.MetricRecord;

public class MetricsProcessor
{
    private static final long[][] EMPTY = new long[0][0];

    private final Map<Metric.Kind, List<MetricsLayout>> layouts;
    private final LabelManager labels;
    private final Function<String, String> kindSupplier;
    private final Function<String, String> nameSupplier;
    private final Function<String, String> descriptionSupplier;
    private final LongPredicate filter;
    private final List<MetricRecord> metricRecords;

    public MetricsProcessor(
        Map<Metric.Kind, List<MetricsLayout>> layouts,
        LabelManager labels,
        Function<String, String> kindSupplier,
        Function<String, String> nameSupplier,
        Function<String, String> descriptionSupplier,
        String namespaceName,
        String bindingName)
    {
        this.layouts = layouts;
        this.labels = labels;
        this.kindSupplier = kindSupplier;
        this.nameSupplier = nameSupplier;
        this.descriptionSupplier = descriptionSupplier;
        this.filter = filterBy(namespaceName, bindingName);
        this.metricRecords = new LinkedList<>();
    }

    public void print(
        PrintStream out)
    {
        if (metricRecords.isEmpty())
        {
            collectCounters();
            collectGauges();
            collectHistograms();
        }
        updateRecords();
        printRecords(out);
    }

    private LongPredicate filterBy(
        String namespace,
        String binding)
    {
        int namespaceId = namespace != null ? Math.max(labels.supplyLabelId(namespace), 0) : 0;
        int bindingId = binding != null ? Math.max(labels.supplyLabelId(binding), 0) : 0;

        long namespacedId =
            (long) namespaceId << Integer.SIZE |
                (long) bindingId << 0;

        long mask =
            (namespace != null ? 0xffff_ffff_0000_0000L : 0x0000_0000_0000_0000L) |
                (binding != null ? 0x0000_0000_ffff_ffffL : 0x0000_0000_0000_0000L);
        return id -> (id & mask) == namespacedId;
    }

    private void collectCounters()
    {
        for (long[] counterIds : fetchIds(layouts.get(COUNTER)))
        {
            long packedBindingId = counterIds[0];
            long packedMetricId = counterIds[1];
            if (filter.test(packedBindingId))
            {
                LongSupplier[] readers = layouts.get(COUNTER).stream()
                    .map(layout -> layout.supplyReader(packedBindingId, packedMetricId))
                    .collect(Collectors.toList())
                    .toArray(LongSupplier[]::new);
                MetricRecord record = new CounterGaugeRecord(packedBindingId, packedMetricId, readers,
                    labels::lookupLabel, this::counterGaugeFormatter);
                metricRecords.add(record);
            }
        }
    }

    private long[][] fetchIds(
        List<MetricsLayout> layout)
    {
        // the list of ids are expected to be identical in a group of layout files of the same type
        // e.g. counters0, counters1, counters2 should all have the same set of ids, so we can get it from any
        return layout.isEmpty() ? EMPTY : layout.get(0).getIds();
    }

    private String counterGaugeFormatter(
        String metric,
        String namespace,
        String binding,
        long value)
    {
        String kind = kindSupplier.apply(metric);
        String extName = nameSupplier.apply(metric);
        String description = descriptionSupplier.apply(metric);
        String format =
            "# HELP %s %s\n" +
            "# TYPE %s %s\n" +
            "%s{namespace=\"%s\",binding=\"%s\"} %d";
        return String.format(format, extName, description, extName, kind, extName, namespace, binding, value);
    }

    private void collectGauges()
    {
        for (long[] gaugeIds : fetchIds(layouts.get(GAUGE)))
        {
            long packedBindingId = gaugeIds[0];
            long packedMetricId = gaugeIds[1];
            if (filter.test(packedBindingId))
            {
                LongSupplier[] readers = layouts.get(GAUGE).stream()
                    .map(layout -> layout.supplyReader(packedBindingId, packedMetricId))
                    .collect(Collectors.toList())
                    .toArray(LongSupplier[]::new);
                MetricRecord record = new CounterGaugeRecord(packedBindingId, packedMetricId, readers,
                    labels::lookupLabel, this::counterGaugeFormatter);
                metricRecords.add(record);
            }
        }
    }

    private void collectHistograms()
    {
        for (long[] histogramIds : fetchIds(layouts.get(HISTOGRAM)))
        {
            long packedBindingId = histogramIds[0];
            long packedMetricId = histogramIds[1];
            if (filter.test(packedBindingId))
            {
                LongSupplier[][] readers = layouts.get(HISTOGRAM).stream()
                    .map(layout -> layout.supplyReaders(packedBindingId, packedMetricId))
                    .collect(Collectors.toList())
                    .toArray(LongSupplier[][]::new);
                MetricRecord record = new HistogramRecord(packedBindingId, packedMetricId, readers,
                    labels::lookupLabel, this::histogramFormatter);
                metricRecords.add(record);
            }
        }
    }

    private String histogramFormatter(
        String metric,
        String namespace,
        String binding,
        long[] values,
        long[] stats)
    {
        StringBuilder sb = new StringBuilder();
        String kind = kindSupplier.apply(metric);
        String extName = nameSupplier.apply(metric);
        String description = descriptionSupplier.apply(metric);
        long sum = stats[2];
        long count = stats[3];
        sb.append(String.format("# HELP %s %s\n# TYPE %s %s\n", extName, description, extName, kind));
        for (int i = 0; i < BUCKETS; i++)
        {
            String limit = i == BUCKETS - 1 ? "+Inf" : String.valueOf(BUCKET_LIMITS.get(i));
            sb.append(String.format("%s_bucket{le=\"%s\",namespace=\"%s\",binding=\"%s\"} %d\n",
                extName, limit, namespace, binding, values[i]));
        }
        sb.append(String.format("%s_sum{namespace=\"%s\",binding=\"%s\"} %d\n", extName, namespace, binding, sum));
        sb.append(String.format("%s_count{namespace=\"%s\",binding=\"%s\"} %d\n", extName, namespace, binding, count));
        return sb.toString();
    }

    private void updateRecords()
    {
        metricRecords.forEach(MetricRecord::update);
    }

    private void printRecords(
        PrintStream out)
    {
        for (MetricRecord metric : metricRecords)
        {
            out.println(metric.stringValue());
            out.println();
        }
    }
}
