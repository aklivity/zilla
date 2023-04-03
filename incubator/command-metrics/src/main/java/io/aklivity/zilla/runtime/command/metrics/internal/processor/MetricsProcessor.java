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
package io.aklivity.zilla.runtime.command.metrics.internal.processor;

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.command.metrics.internal.labels.LabelManager;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.CountersLayout;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.HistogramsLayout;
import io.aklivity.zilla.runtime.command.metrics.internal.record.CounterRecord;
import io.aklivity.zilla.runtime.command.metrics.internal.record.HistogramRecord;
import io.aklivity.zilla.runtime.command.metrics.internal.record.MetricRecord;

public class MetricsProcessor
{
    private static final String NAMESPACE_HEADER = "namespace";
    private static final String BINDING_HEADER = "binding";
    private static final String METRIC_HEADER = "metric";
    private static final String VALUE_HEADER = "value";

    private final List<CountersLayout> countersLayouts;
    private final List<HistogramsLayout> histogramsLayouts;
    private final LabelManager labels;
    private final LongPredicate filter;
    private final List<MetricRecord> metricRecords;

    private int namespaceWidth;
    private int bindingWidth;
    private int metricWidth;
    private int valueWidth;

    public MetricsProcessor(
        List<CountersLayout> countersLayouts,
        List<HistogramsLayout> histogramsLayouts,
        LabelManager labels,
        String namespaceName,
        String bindingName)
    {
        this.countersLayouts = countersLayouts;
        this.histogramsLayouts = histogramsLayouts;
        this.labels = labels;
        this.filter = filterBy(namespaceName, bindingName);
        this.metricRecords = new LinkedList<>();
    }

    public void print(PrintStream out)
    {
        if (metricRecords.isEmpty())
        {
            collectCounters();
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
        if (countersLayouts.isEmpty())
        {
            return;
        }
        long[][] ids = countersLayouts.get(0).getIds();
        for (long[] id : ids)
        {
            long packedBindingId = id[0];
            long packedMetricId = id[1];
            if (filter.test(packedBindingId))
            {
                LongSupplier[] readers = countersLayouts.stream()
                        .map(layout -> layout.supplyReader(packedBindingId, packedMetricId))
                        .collect(Collectors.toList())
                        .toArray(LongSupplier[]::new);
                MetricRecord record = new CounterRecord(packedBindingId, packedMetricId, readers,
                        labels::lookupLabel, this::counterFormatter);
                metricRecords.add(record);
            }
        }
    }
    private String counterFormatter(long value)
    {
        return String.valueOf(value);
    }

    private void collectHistograms()
    {
        if (histogramsLayouts.isEmpty())
        {
            return;
        }
        long[][] ids = histogramsLayouts.get(0).getIds();
        for (long[] id : ids)
        {
            long packedBindingId = id[0];
            long packedMetricId = id[1];
            if (filter.test(packedBindingId))
            {
                LongSupplier[][] readers = histogramsLayouts.stream()
                        .map(layout -> layout.supplyReaders(packedBindingId, packedMetricId))
                        .collect(Collectors.toList())
                        .toArray(LongSupplier[][]::new);
                MetricRecord record = new HistogramRecord(packedBindingId, packedMetricId, readers,
                        labels::lookupLabel, this::histogramFormatter);
                metricRecords.add(record);
            }
        }
    }

    private String histogramFormatter(long[] stats)
    {
        return String.format("[min: %d | max: %d | cnt: %d | avg: %d]", stats[0], stats[1], stats[2], stats[3]);
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
        String format = "%-" + namespaceWidth + "s    %-" + bindingWidth + "s    %-" + metricWidth + "s    %" +
                valueWidth + "s\n";
        out.format(format, NAMESPACE_HEADER, BINDING_HEADER, METRIC_HEADER, VALUE_HEADER);
        for (MetricRecord metric : metricRecords)
        {
            out.format(format, metric.namespaceName(), metric.bindingName(), metric.metricName(), metric.stringValue());
        }
        out.println();
    }
}
