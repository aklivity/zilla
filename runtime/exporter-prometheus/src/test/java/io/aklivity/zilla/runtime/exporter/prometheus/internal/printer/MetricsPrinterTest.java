/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.prometheus.internal.printer;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.metrics.reader.MetricsReader;
import io.aklivity.zilla.runtime.engine.metrics.record.CounterGaugeRecord;
import io.aklivity.zilla.runtime.engine.metrics.record.HistogramRecord;
import io.aklivity.zilla.runtime.engine.metrics.record.MetricRecord;

public class MetricsPrinterTest
{
    @Test
    public void shouldWorkInGenericCase() throws Exception
    {
        String expectedOutput =
            "# HELP counter1_total description for counter1\n" +
            "# TYPE counter1_total counter\n" +
            "counter1_total{namespace=\"ns1\",binding=\"binding1\"} 42\n" +
            "\n" +
            "# HELP gauge1 description for gauge1\n" +
            "# TYPE gauge1 gauge\n" +
            "gauge1{namespace=\"ns1\",binding=\"binding1\"} 77\n" +
            "\n" +
            "# HELP histogram1 description for histogram1\n" +
            "# TYPE histogram1 histogram\n" +
            "histogram1_bucket{le=\"1\",namespace=\"ns1\",binding=\"binding1\"} 7\n" +
            "histogram1_bucket{le=\"10\",namespace=\"ns1\",binding=\"binding1\"} 49\n" +
            "histogram1_bucket{le=\"100\",namespace=\"ns1\",binding=\"binding1\"} 58\n" +
            "histogram1_bucket{le=\"+Inf\",namespace=\"ns1\",binding=\"binding1\"} 59\n" +
            "histogram1_sum{namespace=\"ns1\",binding=\"binding1\"} 2327\n" +
            "histogram1_count{namespace=\"ns1\",binding=\"binding1\"} 59\n\n\n";

        CounterGaugeRecord counterRecord = mock(CounterGaugeRecord.class);
        when(counterRecord.namespaceName()).thenReturn("ns1");
        when(counterRecord.bindingName()).thenReturn("binding1");
        when(counterRecord.metricName()).thenReturn("counter1");
        when(counterRecord.valueReader()).thenReturn(() -> 42L);

        CounterGaugeRecord gaugeRecord = mock(CounterGaugeRecord.class);
        when(gaugeRecord.namespaceName()).thenReturn("ns1");
        when(gaugeRecord.bindingName()).thenReturn("binding1");
        when(gaugeRecord.metricName()).thenReturn("gauge1");
        when(gaugeRecord.valueReader()).thenReturn(() -> 77L);

        HistogramRecord histogramRecord = mock(HistogramRecord.class);
        when(histogramRecord.namespaceName()).thenReturn("ns1");
        when(histogramRecord.bindingName()).thenReturn("binding1");
        when(histogramRecord.metricName()).thenReturn("histogram1");
        when(histogramRecord.buckets()).thenReturn(4);
        when(histogramRecord.bucketLimits()).thenReturn(new long[]{1, 10, 100, 1000});
        when(histogramRecord.bucketValues()).thenReturn(new long[]{7, 42, 9, 1});
        when(histogramRecord.stats()).thenReturn(new long[]{1L, 1000L, 2327L, 59L, 39L}); // min, max, sum, cnt, avg

        List<MetricRecord> metricRecords = List.of(counterRecord, gaugeRecord, histogramRecord);
        MetricsReader metricsReader = mock(MetricsReader.class);
        when(metricsReader.getRecords()).thenReturn(metricRecords);

        PrometheusMetricDescriptor descriptor = mock(PrometheusMetricDescriptor.class);
        when(descriptor.name("counter1")).thenReturn("counter1_total");
        when(descriptor.kind("counter1")).thenReturn("counter");
        when(descriptor.description("counter1")).thenReturn("description for counter1");

        when(descriptor.name("gauge1")).thenReturn("gauge1");
        when(descriptor.kind("gauge1")).thenReturn("gauge");
        when(descriptor.description("gauge1")).thenReturn("description for gauge1");

        when(descriptor.name("histogram1")).thenReturn("histogram1");
        when(descriptor.kind("histogram1")).thenReturn("histogram");
        when(descriptor.description("histogram1")).thenReturn("description for histogram1");

        MetricsPrinter printer = new MetricsPrinter(metricsReader, descriptor::kind, descriptor::name,
            descriptor::description);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        printer.print(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
    }

    @Test
    public void shouldPrintEmpty() throws Exception
    {
        // GIVEN
        String expectedOutput = "";
        List<MetricRecord> metricRecords = List.of();

        MetricsReader metricsReader = mock(MetricsReader.class);
        when(metricsReader.getRecords()).thenReturn(metricRecords);

        MetricsPrinter printer = new MetricsPrinter(metricsReader, null, null, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        printer.print(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
    }
}
