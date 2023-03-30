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
import io.aklivity.zilla.runtime.command.metrics.internal.layout.CountersReader;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.HistogramsReader;
import io.aklivity.zilla.runtime.command.metrics.internal.record.CounterRecord;
import io.aklivity.zilla.runtime.command.metrics.internal.record.HistogramRecord;
import io.aklivity.zilla.runtime.command.metrics.internal.record.MetricRecord;

public class MetricsProcessor
{
    private static final String NAMESPACE_HEADER = "namespace";
    private static final String BINDING_HEADER = "binding";
    private static final String METRIC_HEADER = "metric";
    private static final String VALUE_HEADER = "value";

    private final List<CountersReader> counterFileReaders;
    private final List<HistogramsReader> histogramFileReaders;
    private final LabelManager labels;
    private final LongPredicate filter;
    private final List<MetricRecord> metricRecords;

    private int namespaceWidth;
    private int bindingWidth;
    private int metricWidth;
    private int valueWidth;

    public MetricsProcessor(
        List<CountersReader> counterFileReaders,
        List<HistogramsReader> histogramFileReaders,
        LabelManager labels,
        String namespaceName,
        String bindingName)
    {
        this.counterFileReaders = counterFileReaders;
        this.histogramFileReaders = histogramFileReaders;
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
        calculateColumnWidths();
        doPrint(out);
    }

    private LongPredicate filterBy(
        String namespace,
        String binding)
    {
        int namespaceId = namespace != null ? Math.max(labels.lookupLabelId(namespace), 0) : 0;
        int bindingId = binding != null ? Math.max(labels.lookupLabelId(binding), 0) : 0;

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
        // TODO: Ati - get metadata
        LongSupplier[][] longSuppliers = counterFileReaders.get(0).recordReaders();
        for (LongSupplier[] longSupplier : longSuppliers)
        {
            long packedBindingId = longSupplier[0].getAsLong();
            long packedMetricId = longSupplier[1].getAsLong();
            if (filter.test(packedBindingId))
            {
                LongSupplier[] readers = counterFileReaders.stream()
                        .map(CountersReader::layout)
                        .map(layout -> layout.supplyReader(packedBindingId, packedMetricId))
                        .collect(Collectors.toList())
                        .toArray(LongSupplier[]::new);
                MetricRecord record = new CounterRecord(packedBindingId, packedMetricId, readers, labels::lookupLabel);
                metricRecords.add(record);
            }
        }
    }

    private void collectHistograms()
    {
        // TODO: Ati - get metadata
        LongSupplier[][] longSuppliers2 = histogramFileReaders.get(0).recordReaders();
        for (LongSupplier[] longSupplier : longSuppliers2)
        {
            long packedBindingId = longSupplier[0].getAsLong();
            long packedMetricId = longSupplier[1].getAsLong();
            if (filter.test(packedBindingId))
            {
                LongSupplier[][] readers = histogramFileReaders.stream()
                        .map(HistogramsReader::layout)
                        .map(layout -> layout.supplyReaders(packedBindingId, packedMetricId))
                        .collect(Collectors.toList())
                        .toArray(LongSupplier[][]::new);
                MetricRecord record = new HistogramRecord(packedBindingId, packedMetricId, readers, labels::lookupLabel);
                metricRecords.add(record);
            }
        }
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

    private void doPrint(
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
