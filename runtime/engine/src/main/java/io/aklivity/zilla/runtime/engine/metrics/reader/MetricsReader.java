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
package io.aklivity.zilla.runtime.engine.metrics.reader;

import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.COUNTER;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.GAUGE;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.HISTOGRAM;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import io.aklivity.zilla.runtime.engine.internal.LabelManager;
import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.MetricsLayout;
import io.aklivity.zilla.runtime.engine.metrics.Collector;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.record.CounterGaugeRecord;
import io.aklivity.zilla.runtime.engine.metrics.record.HistogramRecord;
import io.aklivity.zilla.runtime.engine.metrics.record.MetricRecord;

public class MetricsReader
{
    private static final long[][] EMPTY = new long[0][0];
    private final Collector collector;
    private final Map<Metric.Kind, List<MetricsLayout>> layouts; // TODO: Ati - remove this
    private final LabelManager labels;
    private final List<MetricRecord> metricRecords;

    public MetricsReader(
        Map<Metric.Kind, List<MetricsLayout>> layouts,
        Collector collector,
        LabelManager labels)
    {
        this.layouts = layouts;
        this.collector = collector;
        this.labels = labels;
        this.metricRecords = new LinkedList<>();
    }

    public List<MetricRecord> getRecords()
    {
        init();
        return metricRecords;
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

    private void collectCounters()
    {
        for (long[] counterIds : fetchIds(layouts.get(COUNTER)))
        {
            long bindingId = counterIds[0];
            long metricId = counterIds[1];
            LongSupplier counterReader = collector.counter(bindingId, metricId);
            MetricRecord record = new CounterGaugeRecord(bindingId, metricId, counterReader, labels::lookupLabel);
            metricRecords.add(record);
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
            long bindingId = gaugeIds[0];
            long metricId = gaugeIds[1];
            LongSupplier gaugeReader = collector.gauge(bindingId, metricId);
            MetricRecord record = new CounterGaugeRecord(bindingId, metricId, gaugeReader, labels::lookupLabel);
            metricRecords.add(record);
        }
    }

    private void collectHistograms()
    {
        for (long[] histogramIds : fetchIds(layouts.get(HISTOGRAM)))
        {
            long bindingId = histogramIds[0];
            long metricId = histogramIds[1];
            LongSupplier[] histogramReaders = collector.histogram(bindingId, metricId);
            MetricRecord record = new HistogramRecord(bindingId, metricId, histogramReaders, labels::lookupLabel);
            metricRecords.add(record);
        }
    }
}
