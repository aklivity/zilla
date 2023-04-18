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
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.labels.LabelManager;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.layout.MetricsLayout;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.record.CounterRecord;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.record.GaugeRecord;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.record.HistogramRecord;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.record.MetricRecord;

public class MetricsProcessor
{
    private static final String NAMESPACE_HEADER = "namespace";
    private static final String BINDING_HEADER = "binding";
    private static final String METRIC_HEADER = "metric";
    private static final String VALUE_HEADER = "value";
    private static final long[][] EMPTY = new long[0][0];

    private final Map<Metric.Kind, List<MetricsLayout>> layouts;
    private final LabelManager labels;
    private final LongPredicate filter;
    private final List<MetricRecord> metricRecords;

    private int namespaceWidth;
    private int bindingWidth;
    private int metricWidth;
    private int valueWidth;

    public MetricsProcessor(
        Map<Metric.Kind, List<MetricsLayout>> layouts,
        LabelManager labels,
        String namespaceName,
        String bindingName)
    {
        this.layouts = layouts;
        this.labels = labels;
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
        calculateColumnWidths();
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
                MetricRecord record = new CounterRecord(packedBindingId, packedMetricId, readers,
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
        String[] s, long value)
    {
        String format =
            "# HELP %s %s\n" +
            "# TYPE %s %s\n" +
            "%s{namespace=\"%s\",binding=\"%s\"} %d";
        // TODO: Ati
        String metricName = s[1].replace('.', '_');
        return String.format(format, metricName, "TODO: put description here", metricName, s[0], metricName, s[2], s[3], value);
    }

    /*private String counterGaugeFormatter(
        long value)
    {
        return String.valueOf(value);
    }*/

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
                MetricRecord record = new GaugeRecord(packedBindingId, packedMetricId, readers,
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
        String[] s,
        long[] values,
        long[] stats)
    {
        String metricName = s[1].replace('.', '_') + "_bytes"; // TODO: Ati !!!
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "# HELP %s %s\n" +
            "# TYPE %s %s\n",
            metricName, "TOOD bla bla desc", metricName, s[0]));
        for (int i = 0; i < BUCKETS; i++)
        {
            String limit = i == BUCKETS - 1 ? "+Inf" : String.valueOf(BUCKET_LIMITS.get(i));
            sb.append(String.format("%s_bucket{le=\"%s\",namespace=\"%s\",binding=\"%s\"} %d\n",
                metricName, limit, s[2], s[3], values[i]));
        }
        sb.append(String.format("%s_sum{namespace=\"%s\",binding=\"%s\"} %d\n", metricName, s[2], s[3], stats[2]));
        sb.append(String.format("%s_count{namespace=\"%s\",binding=\"%s\"} %d\n", metricName, s[2], s[3], stats[3]));
        return sb.toString();
    }

    private void updateRecords()
    {
        metricRecords.forEach(MetricRecord::update);
    }

    private void calculateColumnWidths()
    {
        namespaceWidth = NAMESPACE_HEADER.length();
        bindingWidth = BINDING_HEADER.length();
        metricWidth = METRIC_HEADER.length();
        valueWidth = VALUE_HEADER.length();

        for (MetricRecord metric : metricRecords)
        {
            namespaceWidth = Math.max(namespaceWidth, metric.namespaceName().length());
            bindingWidth = Math.max(bindingWidth, metric.bindingName().length());
            metricWidth = Math.max(metricWidth, metric.metricName().length());
            valueWidth = Math.max(valueWidth, metric.stringValue().length());
        }
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

    /*private void printRecords(
        PrintStream out)
    {
        String format = "%-" + namespaceWidth + "s    %-" + bindingWidth + "s    %-" + metricWidth + "s    %" +
            valueWidth + "s\n";
        out.format(format, NAMESPACE_HEADER, BINDING_HEADER, METRIC_HEADER, VALUE_HEADER);
        for (MetricRecord metric : metricRecords)
        {
            out.format(format, metric.namespaceName(), metric.bindingName(), metric.metricName(), metric.stringValue());
        }
        out.println();
    }*/
}
