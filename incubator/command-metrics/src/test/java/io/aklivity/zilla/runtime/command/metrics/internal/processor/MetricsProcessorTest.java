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

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.function.LongSupplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.command.metrics.internal.labels.LabelManager;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.CountersLayout;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.GaugesLayout;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.HistogramsLayout;

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
        LabelManager mockLabelManager = mock(LabelManager.class);
        when(mockLabelManager.lookupLabel(1)).thenReturn("ns1");
        when(mockLabelManager.lookupLabel(2)).thenReturn("ns2");
        when(mockLabelManager.lookupLabel(11)).thenReturn("binding1");
        when(mockLabelManager.lookupLabel(12)).thenReturn("binding2");
        when(mockLabelManager.lookupLabel(21)).thenReturn("counter1");
        when(mockLabelManager.lookupLabel(22)).thenReturn("counter2");
        when(mockLabelManager.lookupLabel(31)).thenReturn("gauge1");
        when(mockLabelManager.lookupLabel(41)).thenReturn("histogram1");

        CountersLayout mockCountersLayout = mock(CountersLayout.class);
        when(mockCountersLayout.getIds()).thenReturn(counterIds);
        when(mockCountersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_42);
        when(mockCountersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_77);
        when(mockCountersLayout.supplyReader(BINDING_ID_1_12, METRIC_ID_1_21)).thenReturn(READER_43);
        when(mockCountersLayout.supplyReader(BINDING_ID_2_11, METRIC_ID_2_21)).thenReturn(READER_44);

        GaugesLayout mockGaugesLayout = mock(GaugesLayout.class);
        when(mockGaugesLayout.getIds()).thenReturn(gaugeIds);
        when(mockGaugesLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_31)).thenReturn(READER_88);

        HistogramsLayout mockHistogramsLayout = mock(HistogramsLayout.class);
        when(mockHistogramsLayout.getIds()).thenReturn(histogramIds);
        when(mockHistogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_41)).thenReturn(READER_HISTOGRAM_1);

        MetricsProcessor metrics = new MetricsProcessor(
                List.of(mockCountersLayout), List.of(mockGaugesLayout), List.of(mockHistogramsLayout),
                mockLabelManager, null, null);
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
        LabelManager mockLabelManager = mock(LabelManager.class);
        when(mockLabelManager.lookupLabel(1)).thenReturn("ns1");
        when(mockLabelManager.lookupLabel(2)).thenReturn("ns2");
        when(mockLabelManager.lookupLabel(11)).thenReturn("binding1");
        when(mockLabelManager.lookupLabel(12)).thenReturn("binding2");
        when(mockLabelManager.lookupLabel(21)).thenReturn("counter1");
        when(mockLabelManager.lookupLabel(22)).thenReturn("counter2");
        when(mockLabelManager.lookupLabel(41)).thenReturn("histogram1");
        when(mockLabelManager.supplyLabelId("ns1")).thenReturn(1);
        when(mockLabelManager.supplyLabelId("ns2")).thenReturn(2);
        when(mockLabelManager.supplyLabelId("binding2")).thenReturn(12);

        CountersLayout mockCountersLayout = mock(CountersLayout.class);
        when(mockCountersLayout.getIds()).thenReturn(counterIds);
        when(mockCountersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_42);
        when(mockCountersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_77);
        when(mockCountersLayout.supplyReader(BINDING_ID_1_12, METRIC_ID_1_21)).thenReturn(READER_43);
        when(mockCountersLayout.supplyReader(BINDING_ID_2_11, METRIC_ID_2_21)).thenReturn(READER_44);

        HistogramsLayout mockHistogramsLayout = mock(HistogramsLayout.class);
        when(mockHistogramsLayout.getIds()).thenReturn(histogramIds);
        when(mockHistogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_41)).thenReturn(READER_HISTOGRAM_1);

        MetricsProcessor metrics1 = new MetricsProcessor(List.of(mockCountersLayout), List.of(), List.of(mockHistogramsLayout),
                mockLabelManager, "ns2", null);
        ByteArrayOutputStream os1 = new ByteArrayOutputStream();
        PrintStream out1 = new PrintStream(os1);

        MetricsProcessor metrics2 = new MetricsProcessor(List.of(mockCountersLayout), List.of(), List.of(mockHistogramsLayout),
                mockLabelManager, null, "binding2");
        ByteArrayOutputStream os2 = new ByteArrayOutputStream();
        PrintStream out2 = new PrintStream(os2);

        MetricsProcessor metrics3 = new MetricsProcessor(List.of(mockCountersLayout), List.of(), List.of(mockHistogramsLayout),
                mockLabelManager, "ns1", "binding2");
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
        LabelManager mockLabelManager = mock(LabelManager.class);
        when(mockLabelManager.lookupLabel(1)).thenReturn("ns1");
        when(mockLabelManager.lookupLabel(11)).thenReturn("binding1");
        when(mockLabelManager.lookupLabel(42)).thenReturn("histogram2");
        when(mockLabelManager.lookupLabel(43)).thenReturn("histogram3");
        when(mockLabelManager.lookupLabel(44)).thenReturn("histogram4");
        when(mockLabelManager.supplyLabelId("ns1")).thenReturn(1);
        when(mockLabelManager.supplyLabelId("binding1")).thenReturn(11);

        HistogramsLayout mockHistogramsLayout = mock(HistogramsLayout.class);
        when(mockHistogramsLayout.getIds()).thenReturn(histogramIds);
        when(mockHistogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_42)).thenReturn(READER_HISTOGRAM_2);
        when(mockHistogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_43)).thenReturn(READER_HISTOGRAM_3);
        when(mockHistogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_44)).thenReturn(READER_HISTOGRAM_4);

        MetricsProcessor metrics = new MetricsProcessor(List.of(), List.of(), List.of(mockHistogramsLayout),
                mockLabelManager, null, null);
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
        LabelManager mockLabelManager = mock(LabelManager.class);
        MetricsProcessor metrics = new MetricsProcessor(List.of(), List.of(), List.of(), mockLabelManager, null, null);
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
