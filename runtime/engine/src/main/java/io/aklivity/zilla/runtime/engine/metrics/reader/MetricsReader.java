/*
 * Copyright 2021-2024 Aklivity Inc.
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
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import io.aklivity.zilla.runtime.engine.metrics.Collector;

public class MetricsReader
{
    private final Collector collector;
    private final LongFunction<String> labelResolver;
    private final List<MetricRecord> records;

    public MetricsReader(
        Collector collector,
        LongFunction<String> labelResolver)
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
        for (long[] ids : collector.counterIds())
        {
            long bindingId = ids[0];
            int metricId = (int) ids[1];
            int attributesId = (int) ids[2];
            LongSupplier reader = collector.counter(bindingId, metricId, attributesId);
            records.add(new ScalarRecord(bindingId, metricId, attributesId, reader, labelResolver));
        }
    }

    private void collectGauges()
    {
        for (long[] ids : collector.gaugeIds())
        {
            long bindingId = ids[0];
            int metricId = (int) ids[1];
            int attributesId = (int) ids[2];
            LongSupplier reader = collector.gauge(bindingId, metricId, attributesId);
            records.add(new ScalarRecord(bindingId, metricId, attributesId, reader, labelResolver));
        }
    }

    private void collectHistograms()
    {
        for (long[] ids : collector.histogramIds())
        {
            long bindingId = ids[0];
            int metricId = (int) ids[1];
            int attributesId = (int) ids[2];
            LongSupplier[] readers = collector.histogram(bindingId, metricId, attributesId);
            records.add(new HistogramRecord(bindingId, metricId, attributesId, readers, labelResolver));
        }
    }
}
