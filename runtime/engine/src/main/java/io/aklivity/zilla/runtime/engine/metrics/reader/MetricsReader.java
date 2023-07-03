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

import java.util.LinkedList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;

import io.aklivity.zilla.runtime.engine.metrics.Collector;

public class MetricsReader
{
    private final Collector collector;
    private final IntFunction<String> labelResolver;
    private final List<MetricRecord> records;

    public MetricsReader(
        Collector collector,
        IntFunction<String> labelResolver)
    {
        this.collector = collector;
        this.labelResolver = labelResolver;
        this.records = new LinkedList<>();
        collectCounters();
        collectGauges();
        collectHistograms();
    }

    public List<MetricRecord> records()
    {
        return records;
    }

    private void collectCounters()
    {
        for (long[] counterIds : collector.counterIds())
        {
            long bindingId = counterIds[0];
            long metricId = counterIds[1];
            LongSupplier counterReader = collector.counter(bindingId, metricId);
            MetricRecord record = new ScalarRecord(bindingId, metricId, counterReader, labelResolver);
            records.add(record);
        }
    }

    private void collectGauges()
    {
        for (long[] gaugeIds : collector.gaugeIds())
        {
            long bindingId = gaugeIds[0];
            long metricId = gaugeIds[1];
            LongSupplier gaugeReader = collector.gauge(bindingId, metricId);
            MetricRecord record = new ScalarRecord(bindingId, metricId, gaugeReader, labelResolver);
            records.add(record);
        }
    }

    private void collectHistograms()
    {
        for (long[] histogramIds : collector.histogramIds())
        {
            long bindingId = histogramIds[0];
            long metricId = histogramIds[1];
            LongSupplier[] histogramReaders = collector.histogram(bindingId, metricId);
            MetricRecord record = new HistogramRecord(bindingId, metricId, histogramReaders, labelResolver);
            records.add(record);
        }
    }
}
