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
package io.aklivity.zilla.runtime.engine.metrics.processor;

import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.COUNTER;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.GAUGE;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.HISTOGRAM;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.engine.internal.LabelManager;
import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.MetricsLayout;
import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.record.CounterGaugeRecord;
import io.aklivity.zilla.runtime.engine.metrics.record.HistogramRecord;
import io.aklivity.zilla.runtime.engine.metrics.record.MetricRecord;

public class MetricsProcessor
{
    private static final long[][] EMPTY = new long[0][0];

    private final Map<Metric.Kind, List<MetricsLayout>> layouts;
    private final LabelManager labels;
    private final LongPredicate filter;
    private final List<MetricRecord> metricRecords;

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

    public List<MetricRecord> getRecords()
    {
        init();
        return metricRecords;
    }

    public MetricRecord findRecord(
        String namespace,
        String binding,
        String metric)
    {
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(binding);
        Objects.requireNonNull(metric);
        init();
        return metricRecords.stream()
            .filter(r -> namespace.equals(r.namespaceName()) && binding.equals(r.bindingName()) && metric.equals(r.metricName()))
            .findFirst()
            .orElse(null);
    }

    public void close()
    {
        layouts.keySet().stream().flatMap(kind -> layouts.get(kind).stream()).forEach(MetricsLayout::close);
    }

    private void init()
    {
        if (metricRecords.isEmpty())
        {
            collectCounters();
            collectGauges();
            collectHistograms();
        }
    }

    private LongPredicate filterBy(
        String namespace,
        String binding)
    {
        int namespaceId = namespace != null ? Math.max(labels.supplyLabelId(namespace), 0) : 0;
        int bindingId = binding != null ? Math.max(labels.supplyLabelId(binding), 0) : 0;
        long namespacedId = NamespacedId.id(namespaceId, bindingId);

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
                MetricRecord record = new CounterGaugeRecord(packedBindingId, packedMetricId, readers, labels::lookupLabel);
                metricRecords.add(record);
            }
        }
    }

    private long[][] fetchIds(
        List<MetricsLayout> layout)
    {
        // the list of ids are expected to be identical in a group of layout files of the same type
        // e.g. counters0, counters1, counters2 should all have the same set of ids, so we can get it from any
        return layout == null || layout.isEmpty() ? EMPTY : layout.get(0).getIds();
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
                MetricRecord record = new CounterGaugeRecord(packedBindingId, packedMetricId, readers, labels::lookupLabel);
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
                MetricRecord record = new HistogramRecord(packedBindingId, packedMetricId, readers, labels::lookupLabel);
                metricRecords.add(record);
            }
        }
    }
}
