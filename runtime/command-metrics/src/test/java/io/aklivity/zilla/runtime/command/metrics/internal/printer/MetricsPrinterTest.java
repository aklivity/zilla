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
package io.aklivity.zilla.runtime.command.metrics.internal.printer;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.command.metrics.internal.reader.MetricsReader;
import io.aklivity.zilla.runtime.engine.metrics.record.CounterGaugeRecord;
import io.aklivity.zilla.runtime.engine.metrics.record.HistogramRecord;
import io.aklivity.zilla.runtime.engine.metrics.record.MetricRecord;

public class MetricsPrinterTest
{
    @Test
    public void shouldWorkInGenericCase() throws Exception
    {
        // GIVEN
        String expectedOutput =
            "namespace    binding     metric                                        value\n" +
            "ns1          binding1    counter1                                         42\n" +
            "ns1          binding1    gauge1                                           77\n" +
            "ns1          binding1    histogram1    [min: 1 | max: 63 | cnt: 2 | avg: 32]\n\n";

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
        when(histogramRecord.stats()).thenReturn(new long[]{1L, 63L, 64L, 2L, 32L});

        List<MetricRecord> metricRecords = List.of(counterRecord, gaugeRecord, histogramRecord);
        MetricsReader metricsReader = mock(MetricsReader.class);
        when(metricsReader.getRecords()).thenReturn(metricRecords);

        MetricsPrinter printer = new MetricsPrinter(metricsReader);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        printer.print(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
    }

    @Test
    public void shouldPrintHeaderOnly() throws Exception
    {
        // GIVEN
        String expectedOutput =
            "namespace    binding    metric    value\n\n";
        List<MetricRecord> metricRecords = List.of();

        MetricsReader metricsReader = mock(MetricsReader.class);
        when(metricsReader.getRecords()).thenReturn(metricRecords);

        MetricsPrinter printer = new MetricsPrinter(metricsReader);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        printer.print(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
    }
}
