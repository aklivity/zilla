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
package io.aklivity.zilla.runtime.command.metrics.internal.processor;

import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.COUNTER;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.GAUGE;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.HISTOGRAM;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.command.metrics.internal.labels.LabelManager;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.layout.CountersLayoutRO;
import io.aklivity.zilla.runtime.engine.metrics.layout.GaugesLayoutRO;
import io.aklivity.zilla.runtime.engine.metrics.layout.HistogramsLayoutRO;
import io.aklivity.zilla.runtime.engine.metrics.layout.MetricsLayoutRO;

public class MetricsProcessorTest
{
    public static final long BINDING_ID_1_11 = pack(1, 11);
    public static final long BINDING_ID_1_12 = pack(1, 12);
    public static final long BINDING_ID_2_11 = pack(2, 11);
    public static final long METRIC_ID_1_21 = pack(1, 21);
    public static final long METRIC_ID_1_22 = pack(1, 22);
    public static final long METRIC_ID_2_21 = pack(2, 21);
    public static final long METRIC_ID_1_31 = pack(1, 31);
    public static final long METRIC_ID_1_41 = pack(1, 41);
    public static final long METRIC_ID_1_42 = pack(1, 42);
    public static final long METRIC_ID_1_43 = pack(1, 43);
    public static final long METRIC_ID_1_44 = pack(1, 44);
    public static final LongSupplier READER_2 = () -> 2L;
    public static final LongSupplier READER_7 = () -> 7L;
    public static final LongSupplier READER_20 = () -> 20L;
    public static final LongSupplier READER_30 = () -> 30L;
    public static final LongSupplier READER_40 = () -> 40L;
    public static final LongSupplier READER_42 = () -> 42L;
    public static final LongSupplier READER_43 = () -> 43L;
    public static final LongSupplier READER_44 = () -> 44L;
    public static final LongSupplier READER_77 = () -> 77L;
    public static final LongSupplier READER_88 = () -> 88L;
    public static final LongSupplier[] READER_HISTOGRAM_1 = new LongSupplier[]
    {
        () -> 1L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 1L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L
    };
    public static final LongSupplier[] READER_HISTOGRAM_2 = new LongSupplier[]
    {
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L
    };
    public static final LongSupplier[] READER_HISTOGRAM_3 = new LongSupplier[]
    {
        () -> 1L, () -> 2L, () -> 0L, () -> 0L, () -> 0L, () -> 1L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L
    };
    public static final LongSupplier[] READER_HISTOGRAM_4 = new LongSupplier[]
    {
        () -> 0L, () -> 42L, () -> 0L, () -> 0L, () -> 0L, () -> 1L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 2L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L
    };

    @Test
    public void shouldWorkInGenericCase() throws Exception
    {
        // GIVEN
        long[][] counterIds = new long[][]{
            {BINDING_ID_1_11, METRIC_ID_1_21},
            {BINDING_ID_1_11, METRIC_ID_1_22},
            {BINDING_ID_1_12, METRIC_ID_1_21},
            {BINDING_ID_2_11, METRIC_ID_2_21}
        };
        long[][] gaugeIds = new long[][]{
            {BINDING_ID_1_11, METRIC_ID_1_31}
        };
        long[][] histogramIds = new long[][]{
            {BINDING_ID_1_11, METRIC_ID_1_41}
        };
        String expectedOutput =
            "namespace    binding     metric                                        value\n" +
            "ns1          binding1    counter1                                         42\n" +
            "ns1          binding1    counter2                                         77\n" +
            "ns1          binding2    counter1                                         43\n" +
            "ns2          binding1    counter1                                         44\n" +
            "ns1          binding1    gauge1                                           88\n" +
            "ns1          binding1    histogram1    [min: 1 | max: 63 | cnt: 2 | avg: 32]\n\n";
        LabelManager labels = mock(LabelManager.class);
        when(labels.lookupLabel(1)).thenReturn("ns1");
        when(labels.lookupLabel(2)).thenReturn("ns2");
        when(labels.lookupLabel(11)).thenReturn("binding1");
        when(labels.lookupLabel(12)).thenReturn("binding2");
        when(labels.lookupLabel(21)).thenReturn("counter1");
        when(labels.lookupLabel(22)).thenReturn("counter2");
        when(labels.lookupLabel(31)).thenReturn("gauge1");
        when(labels.lookupLabel(41)).thenReturn("histogram1");

        CountersLayoutRO countersLayout = mock(CountersLayoutRO.class);
        when(countersLayout.getIds()).thenReturn(counterIds);
        when(countersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_42);
        when(countersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_77);
        when(countersLayout.supplyReader(BINDING_ID_1_12, METRIC_ID_1_21)).thenReturn(READER_43);
        when(countersLayout.supplyReader(BINDING_ID_2_11, METRIC_ID_2_21)).thenReturn(READER_44);

        GaugesLayoutRO gaugesLayout = mock(GaugesLayoutRO.class);
        when(gaugesLayout.getIds()).thenReturn(gaugeIds);
        when(gaugesLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_31)).thenReturn(READER_88);

        HistogramsLayoutRO histogramsLayout = mock(HistogramsLayoutRO.class);
        when(histogramsLayout.getIds()).thenReturn(histogramIds);
        when(histogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_41)).thenReturn(READER_HISTOGRAM_1);

        Map<Metric.Kind, List<MetricsLayoutRO>> layouts = Map.of(
                COUNTER, List.of(countersLayout),
                GAUGE, List.of(gaugesLayout),
                HISTOGRAM, List.of(histogramsLayout));
        MetricsProcessor metrics = new MetricsProcessor(layouts, labels, null, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        metrics.print(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
    }

    @Test
    public void shouldWorkWithFilters() throws Exception
    {
        // GIVEN
        long[][] counterIds = new long[][]{
            {BINDING_ID_1_11, METRIC_ID_1_21},
            {BINDING_ID_1_11, METRIC_ID_1_22},
            {BINDING_ID_1_12, METRIC_ID_1_21},
            {BINDING_ID_2_11, METRIC_ID_2_21}
        };
        long[][] histogramIds = new long[][]{
            {BINDING_ID_1_11, METRIC_ID_1_41}
        };
        String expectedOutput1 =
            "namespace    binding     metric      value\n" +
            "ns2          binding1    counter1       44\n\n";
        String expectedOutput2 =
            "namespace    binding     metric      value\n" +
            "ns1          binding2    counter1       43\n\n";
        String expectedOutput3 =
            "namespace    binding     metric      value\n" +
            "ns1          binding2    counter1       43\n\n";
        LabelManager labels = mock(LabelManager.class);
        when(labels.lookupLabel(1)).thenReturn("ns1");
        when(labels.lookupLabel(2)).thenReturn("ns2");
        when(labels.lookupLabel(11)).thenReturn("binding1");
        when(labels.lookupLabel(12)).thenReturn("binding2");
        when(labels.lookupLabel(21)).thenReturn("counter1");
        when(labels.lookupLabel(22)).thenReturn("counter2");
        when(labels.lookupLabel(41)).thenReturn("histogram1");
        when(labels.supplyLabelId("ns1")).thenReturn(1);
        when(labels.supplyLabelId("ns2")).thenReturn(2);
        when(labels.supplyLabelId("binding2")).thenReturn(12);

        CountersLayoutRO countersLayout = mock(CountersLayoutRO.class);
        when(countersLayout.getIds()).thenReturn(counterIds);
        when(countersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_42);
        when(countersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_77);
        when(countersLayout.supplyReader(BINDING_ID_1_12, METRIC_ID_1_21)).thenReturn(READER_43);
        when(countersLayout.supplyReader(BINDING_ID_2_11, METRIC_ID_2_21)).thenReturn(READER_44);

        HistogramsLayoutRO histogramsLayout = mock(HistogramsLayoutRO.class);
        when(histogramsLayout.getIds()).thenReturn(histogramIds);
        when(histogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_41)).thenReturn(READER_HISTOGRAM_1);

        Map<Metric.Kind, List<MetricsLayoutRO>> layouts = Map.of(
                COUNTER, List.of(countersLayout),
                GAUGE, List.of(),
                HISTOGRAM, List.of(histogramsLayout));

        MetricsProcessor metrics1 = new MetricsProcessor(layouts, labels, "ns2", null);
        ByteArrayOutputStream os1 = new ByteArrayOutputStream();
        PrintStream out1 = new PrintStream(os1);

        MetricsProcessor metrics2 = new MetricsProcessor(layouts, labels, null, "binding2");
        ByteArrayOutputStream os2 = new ByteArrayOutputStream();
        PrintStream out2 = new PrintStream(os2);

        MetricsProcessor metrics3 = new MetricsProcessor(layouts, labels, "ns1", "binding2");
        ByteArrayOutputStream os3 = new ByteArrayOutputStream();
        PrintStream out3 = new PrintStream(os3);

        // WHEN
        metrics1.print(out1);
        metrics2.print(out2);
        metrics3.print(out3);

        // THEN
        assertThat(os1.toString("UTF8"), equalTo(expectedOutput1));
        assertThat(os2.toString("UTF8"), equalTo(expectedOutput2));
        assertThat(os3.toString("UTF8"), equalTo(expectedOutput3));
    }

    @Test
    public void shouldWorkWithMultiCoreAggregation() throws Exception
    {
        // GIVEN
        long[][] counterIds = new long[][]{
                {BINDING_ID_1_11, METRIC_ID_1_21},
                {BINDING_ID_1_11, METRIC_ID_1_22}
        };
        long[][] gaugeIds = new long[][]{
                {BINDING_ID_1_11, METRIC_ID_1_31}
        };
        long[][] histogramIds = new long[][]{
                {BINDING_ID_1_11, METRIC_ID_1_41}
        };
        String expectedOutput =
                "namespace    binding     metric                                        value\n" +
                "ns1          binding1    counter1                                         42\n" +
                "ns1          binding1    counter2                                         77\n" +
                "ns1          binding1    gauge1                                           62\n" +
                "ns1          binding1    histogram1    [min: 1 | max: 63 | cnt: 6 | avg: 22]\n\n";
        LabelManager labels = mock(LabelManager.class);
        when(labels.lookupLabel(1)).thenReturn("ns1");
        when(labels.lookupLabel(2)).thenReturn("ns2");
        when(labels.lookupLabel(11)).thenReturn("binding1");
        when(labels.lookupLabel(12)).thenReturn("binding2");
        when(labels.lookupLabel(21)).thenReturn("counter1");
        when(labels.lookupLabel(22)).thenReturn("counter2");
        when(labels.lookupLabel(31)).thenReturn("gauge1");
        when(labels.lookupLabel(41)).thenReturn("histogram1");

        CountersLayoutRO countersLayout0 = mock(CountersLayoutRO.class);
        when(countersLayout0.getIds()).thenReturn(counterIds);
        when(countersLayout0.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_2);
        when(countersLayout0.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_30);

        CountersLayoutRO countersLayout1 = mock(CountersLayoutRO.class);
        when(countersLayout1.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_20);
        when(countersLayout1.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_40);

        CountersLayoutRO countersLayout2 = mock(CountersLayoutRO.class);
        when(countersLayout2.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_20);
        when(countersLayout2.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_7);

        GaugesLayoutRO gaugesLayout0 = mock(GaugesLayoutRO.class);
        when(gaugesLayout0.getIds()).thenReturn(gaugeIds);
        when(gaugesLayout0.supplyReader(BINDING_ID_1_11, METRIC_ID_1_31)).thenReturn(READER_40);

        GaugesLayoutRO gaugesLayout1 = mock(GaugesLayoutRO.class);
        when(gaugesLayout1.supplyReader(BINDING_ID_1_11, METRIC_ID_1_31)).thenReturn(READER_20);

        GaugesLayoutRO gaugesLayout2 = mock(GaugesLayoutRO.class);
        when(gaugesLayout2.supplyReader(BINDING_ID_1_11, METRIC_ID_1_31)).thenReturn(READER_2);

        HistogramsLayoutRO histogramsLayout0 = mock(HistogramsLayoutRO.class);
        when(histogramsLayout0.getIds()).thenReturn(histogramIds);
        when(histogramsLayout0.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_41)).thenReturn(READER_HISTOGRAM_1);

        HistogramsLayoutRO histogramsLayout1 = mock(HistogramsLayoutRO.class);
        when(histogramsLayout1.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_41)).thenReturn(READER_HISTOGRAM_2);

        HistogramsLayoutRO histogramsLayout2 = mock(HistogramsLayoutRO.class);
        when(histogramsLayout2.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_41)).thenReturn(READER_HISTOGRAM_3);

        Map<Metric.Kind, List<MetricsLayoutRO>> layouts = Map.of(
                COUNTER, List.of(countersLayout0, countersLayout1, countersLayout2),
                GAUGE, List.of(gaugesLayout0, gaugesLayout1, gaugesLayout2),
                HISTOGRAM, List.of(histogramsLayout0, histogramsLayout1, histogramsLayout2));
        MetricsProcessor metrics = new MetricsProcessor(layouts, labels, null, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        metrics.print(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
    }

    @Test
    public void shouldWorkWithVariousHistograms() throws Exception
    {
        // GIVEN
        long[][] histogramIds = new long[][]{
                {BINDING_ID_1_11, METRIC_ID_1_42},
                {BINDING_ID_1_11, METRIC_ID_1_43},
                {BINDING_ID_1_11, METRIC_ID_1_44}
        };
        String expectedOutput =
                "namespace    binding     metric                                              value\n" +
                "ns1          binding1    histogram2            [min: 0 | max: 0 | cnt: 0 | avg: 0]\n" +
                "ns1          binding1    histogram3          [min: 1 | max: 63 | cnt: 4 | avg: 17]\n" +
                "ns1          binding1    histogram4    [min: 3 | max: 65535 | cnt: 45 | avg: 2916]\n\n";
        LabelManager labels = mock(LabelManager.class);
        when(labels.lookupLabel(1)).thenReturn("ns1");
        when(labels.lookupLabel(11)).thenReturn("binding1");
        when(labels.lookupLabel(42)).thenReturn("histogram2");
        when(labels.lookupLabel(43)).thenReturn("histogram3");
        when(labels.lookupLabel(44)).thenReturn("histogram4");
        when(labels.supplyLabelId("ns1")).thenReturn(1);
        when(labels.supplyLabelId("binding1")).thenReturn(11);

        HistogramsLayoutRO histogramsLayout = mock(HistogramsLayoutRO.class);
        when(histogramsLayout.getIds()).thenReturn(histogramIds);
        when(histogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_42)).thenReturn(READER_HISTOGRAM_2);
        when(histogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_43)).thenReturn(READER_HISTOGRAM_3);
        when(histogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_44)).thenReturn(READER_HISTOGRAM_4);

        Map<Metric.Kind, List<MetricsLayoutRO>> layouts = Map.of(
                COUNTER, List.of(),
                GAUGE, List.of(),
                HISTOGRAM, List.of(histogramsLayout));
        MetricsProcessor metrics = new MetricsProcessor(layouts, labels, null, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        metrics.print(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
    }

    @Test
    public void shouldPrintHeaderOnly() throws Exception
    {
        // GIVEN
        String expectedOutput =
                "namespace    binding    metric    value\n\n";
        LabelManager labels = mock(LabelManager.class);
        Map<Metric.Kind, List<MetricsLayoutRO>> layouts = Map.of(
                COUNTER, List.of(),
                GAUGE, List.of(),
                HISTOGRAM, List.of());
        MetricsProcessor metrics = new MetricsProcessor(layouts, labels, null, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        metrics.print(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
    }

    // packs the two provided id's (int) in one combined id (long)
    private static long pack(
        final int namespaceId,
        final int localId)
    {
        return (long) namespaceId << Integer.SIZE |
                (long) localId << 0;
    }
}
