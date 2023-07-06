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
/*
package io.aklivity.zilla.runtime.command.metrics.internal.reader;

import static io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId.id;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.COUNTER;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.GAUGE;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.HISTOGRAM;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.internal.LabelManager;
import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.HistogramsLayout;
import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.MetricsLayout;
import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.ScalarsLayout;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.reader.HistogramRecord;
import io.aklivity.zilla.runtime.engine.metrics.reader.MetricRecord;
import io.aklivity.zilla.runtime.engine.metrics.reader.ScalarRecord;

public class MetricsReaderTest
{
    public static final long BINDING_ID_1_11 = id(1, 11);
    public static final long BINDING_ID_1_12 = id(1, 12);
    public static final long BINDING_ID_2_11 = id(2, 11);
    public static final long METRIC_ID_1_21 = id(1, 21);
    public static final long METRIC_ID_1_22 = id(1, 22);
    public static final long METRIC_ID_2_21 = id(2, 21);
    public static final long METRIC_ID_1_31 = id(1, 31);
    public static final long METRIC_ID_1_41 = id(1, 41);
    public static final long METRIC_ID_1_42 = id(1, 42);
    public static final long METRIC_ID_1_43 = id(1, 43);
    public static final long METRIC_ID_1_44 = id(1, 44);

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
    public void shouldWorkInGenericCase()
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

        LabelManager labels = mock(LabelManager.class);
        when(labels.lookupLabel(1)).thenReturn("ns1");
        when(labels.lookupLabel(2)).thenReturn("ns2");
        when(labels.lookupLabel(11)).thenReturn("binding1");
        when(labels.lookupLabel(12)).thenReturn("binding2");
        when(labels.lookupLabel(21)).thenReturn("counter1");
        when(labels.lookupLabel(22)).thenReturn("counter2");
        when(labels.lookupLabel(31)).thenReturn("gauge1");
        when(labels.lookupLabel(41)).thenReturn("histogram1");

        ScalarsLayout countersLayout = mock(ScalarsLayout.class);
        when(countersLayout.getIds()).thenReturn(counterIds);
        when(countersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_42);
        when(countersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_77);
        when(countersLayout.supplyReader(BINDING_ID_1_12, METRIC_ID_1_21)).thenReturn(READER_43);
        when(countersLayout.supplyReader(BINDING_ID_2_11, METRIC_ID_2_21)).thenReturn(READER_44);

        ScalarsLayout gaugesLayout = mock(ScalarsLayout.class);
        when(gaugesLayout.getIds()).thenReturn(gaugeIds);
        when(gaugesLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_31)).thenReturn(READER_88);

        HistogramsLayout histogramsLayout = mock(HistogramsLayout.class);
        when(histogramsLayout.getIds()).thenReturn(histogramIds);
        when(histogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_41)).thenReturn(READER_HISTOGRAM_1);

        Map<Metric.Kind, List<MetricsLayout>> layouts = Map.of(
            COUNTER, List.of(countersLayout),
            GAUGE, List.of(gaugesLayout),
            HISTOGRAM, List.of(histogramsLayout));
        MetricsReader metrics = new MetricsReader(layouts, labels, null, null);

        // WHEN
        List<MetricRecord> records = metrics.getRecords();
        ((HistogramRecord)records.get(5)).update();

        // THEN
        assertThat(records.get(0), instanceOf(ScalarRecord.class));
        assertThat(records.get(0).namespaceName(), equalTo("ns1"));
        assertThat(records.get(0).bindingName(), equalTo("binding1"));
        assertThat(records.get(0).metricName(), equalTo("counter1"));
        assertThat(((ScalarRecord)records.get(0)).valueReader().getAsLong(), equalTo(42L));

        assertThat(records.get(1), instanceOf(ScalarRecord.class));
        assertThat(records.get(1).namespaceName(), equalTo("ns1"));
        assertThat(records.get(1).bindingName(), equalTo("binding1"));
        assertThat(records.get(1).metricName(), equalTo("counter2"));
        assertThat(((ScalarRecord)records.get(1)).valueReader().getAsLong(), equalTo(77L));

        assertThat(records.get(2), instanceOf(ScalarRecord.class));
        assertThat(records.get(2).namespaceName(), equalTo("ns1"));
        assertThat(records.get(2).bindingName(), equalTo("binding2"));
        assertThat(records.get(2).metricName(), equalTo("counter1"));
        assertThat(((ScalarRecord)records.get(2)).valueReader().getAsLong(), equalTo(43L));

        assertThat(records.get(3), instanceOf(ScalarRecord.class));
        assertThat(records.get(3).namespaceName(), equalTo("ns2"));
        assertThat(records.get(3).bindingName(), equalTo("binding1"));
        assertThat(records.get(3).metricName(), equalTo("counter1"));
        assertThat(((ScalarRecord)records.get(3)).valueReader().getAsLong(), equalTo(44L));

        assertThat(records.get(4), instanceOf(ScalarRecord.class));
        assertThat(records.get(4).namespaceName(), equalTo("ns1"));
        assertThat(records.get(4).bindingName(), equalTo("binding1"));
        assertThat(records.get(4).metricName(), equalTo("gauge1"));
        assertThat(((ScalarRecord)records.get(4)).valueReader().getAsLong(), equalTo(88L));

        assertThat(records.get(5), instanceOf(HistogramRecord.class));
        assertThat(records.get(5).namespaceName(), equalTo("ns1"));
        assertThat(records.get(5).bindingName(), equalTo("binding1"));
        assertThat(records.get(5).metricName(), equalTo("histogram1"));
        assertThat(((HistogramRecord)records.get(5)).stats(),
            equalTo(new long[]{1L, 63L, 64L, 2L, 32L})); // min, max, sum, cnt, avg
    }

    @Test
    public void shouldWorkWithFilters()
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

        ScalarsLayout countersLayout = mock(ScalarsLayout.class);
        when(countersLayout.getIds()).thenReturn(counterIds);
        when(countersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_42);
        when(countersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_77);
        when(countersLayout.supplyReader(BINDING_ID_1_12, METRIC_ID_1_21)).thenReturn(READER_43);
        when(countersLayout.supplyReader(BINDING_ID_2_11, METRIC_ID_2_21)).thenReturn(READER_44);

        HistogramsLayout histogramsLayout = mock(HistogramsLayout.class);
        when(histogramsLayout.getIds()).thenReturn(histogramIds);
        when(histogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_41)).thenReturn(READER_HISTOGRAM_1);

        Map<Metric.Kind, List<MetricsLayout>> layouts = Map.of(
            COUNTER, List.of(countersLayout),
            GAUGE, List.of(),
            HISTOGRAM, List.of(histogramsLayout));

        MetricsReader metrics1 = new MetricsReader(layouts, labels, "ns2", null);
        MetricsReader metrics2 = new MetricsReader(layouts, labels, null, "binding2");
        MetricsReader metrics3 = new MetricsReader(layouts, labels, "ns1", "binding2");

        // WHEN
        List<MetricRecord> records1 = metrics1.getRecords();
        List<MetricRecord> records2 = metrics2.getRecords();
        List<MetricRecord> records3 = metrics3.getRecords();

        // THEN
        assertThat(records1.size(), equalTo(1));
        assertThat(records1.get(0), instanceOf(ScalarRecord.class));
        assertThat(records1.get(0).namespaceName(), equalTo("ns2"));
        assertThat(records1.get(0).bindingName(), equalTo("binding1"));
        assertThat(records1.get(0).metricName(), equalTo("counter1"));
        assertThat(((ScalarRecord)records1.get(0)).valueReader().getAsLong(), equalTo(44L));

        assertThat(records2.size(), equalTo(1));
        assertThat(records2.get(0), instanceOf(ScalarRecord.class));
        assertThat(records2.get(0).namespaceName(), equalTo("ns1"));
        assertThat(records2.get(0).bindingName(), equalTo("binding2"));
        assertThat(records2.get(0).metricName(), equalTo("counter1"));
        assertThat(((ScalarRecord)records2.get(0)).valueReader().getAsLong(), equalTo(43L));

        assertThat(records3.size(), equalTo(1));
        assertThat(records3.get(0), instanceOf(ScalarRecord.class));
        assertThat(records3.get(0).namespaceName(), equalTo("ns1"));
        assertThat(records3.get(0).bindingName(), equalTo("binding2"));
        assertThat(records3.get(0).metricName(), equalTo("counter1"));
        assertThat(((ScalarRecord)records3.get(0)).valueReader().getAsLong(), equalTo(43L));
    }

    @Test
    public void shouldWorkWithMultiCoreAggregation()
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

        LabelManager labels = mock(LabelManager.class);
        when(labels.lookupLabel(1)).thenReturn("ns1");
        when(labels.lookupLabel(2)).thenReturn("ns2");
        when(labels.lookupLabel(11)).thenReturn("binding1");
        when(labels.lookupLabel(12)).thenReturn("binding2");
        when(labels.lookupLabel(21)).thenReturn("counter1");
        when(labels.lookupLabel(22)).thenReturn("counter2");
        when(labels.lookupLabel(31)).thenReturn("gauge1");
        when(labels.lookupLabel(41)).thenReturn("histogram1");

        ScalarsLayout countersLayout0 = mock(ScalarsLayout.class);
        when(countersLayout0.getIds()).thenReturn(counterIds);
        when(countersLayout0.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_2);
        when(countersLayout0.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_30);

        ScalarsLayout countersLayout1 = mock(ScalarsLayout.class);
        when(countersLayout1.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_20);
        when(countersLayout1.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_40);

        ScalarsLayout countersLayout2 = mock(ScalarsLayout.class);
        when(countersLayout2.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_20);
        when(countersLayout2.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_7);

        ScalarsLayout gaugesLayout0 = mock(ScalarsLayout.class);
        when(gaugesLayout0.getIds()).thenReturn(gaugeIds);
        when(gaugesLayout0.supplyReader(BINDING_ID_1_11, METRIC_ID_1_31)).thenReturn(READER_40);

        ScalarsLayout gaugesLayout1 = mock(ScalarsLayout.class);
        when(gaugesLayout1.supplyReader(BINDING_ID_1_11, METRIC_ID_1_31)).thenReturn(READER_20);

        ScalarsLayout gaugesLayout2 = mock(ScalarsLayout.class);
        when(gaugesLayout2.supplyReader(BINDING_ID_1_11, METRIC_ID_1_31)).thenReturn(READER_2);

        HistogramsLayout histogramsLayout0 = mock(HistogramsLayout.class);
        when(histogramsLayout0.getIds()).thenReturn(histogramIds);
        when(histogramsLayout0.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_41)).thenReturn(READER_HISTOGRAM_1);

        HistogramsLayout histogramsLayout1 = mock(HistogramsLayout.class);
        when(histogramsLayout1.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_41)).thenReturn(READER_HISTOGRAM_2);

        HistogramsLayout histogramsLayout2 = mock(HistogramsLayout.class);
        when(histogramsLayout2.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_41)).thenReturn(READER_HISTOGRAM_3);

        Map<Metric.Kind, List<MetricsLayout>> layouts = Map.of(
            COUNTER, List.of(countersLayout0, countersLayout1, countersLayout2),
            GAUGE, List.of(gaugesLayout0, gaugesLayout1, gaugesLayout2),
            HISTOGRAM, List.of(histogramsLayout0, histogramsLayout1, histogramsLayout2));

        MetricsReader metrics = new MetricsReader(layouts, labels, null, null);

        // WHEN
        List<MetricRecord> records = metrics.getRecords();
        ((HistogramRecord)records.get(3)).update();

        // THEN
        assertThat(records.get(0), instanceOf(ScalarRecord.class));
        assertThat(records.get(0).namespaceName(), equalTo("ns1"));
        assertThat(records.get(0).bindingName(), equalTo("binding1"));
        assertThat(records.get(0).metricName(), equalTo("counter1"));
        assertThat(((ScalarRecord)records.get(0)).valueReader().getAsLong(), equalTo(42L));

        assertThat(records.get(1), instanceOf(ScalarRecord.class));
        assertThat(records.get(1).namespaceName(), equalTo("ns1"));
        assertThat(records.get(1).bindingName(), equalTo("binding1"));
        assertThat(records.get(1).metricName(), equalTo("counter2"));
        assertThat(((ScalarRecord)records.get(1)).valueReader().getAsLong(), equalTo(77L));

        assertThat(records.get(2), instanceOf(ScalarRecord.class));
        assertThat(records.get(2).namespaceName(), equalTo("ns1"));
        assertThat(records.get(2).bindingName(), equalTo("binding1"));
        assertThat(records.get(2).metricName(), equalTo("gauge1"));
        assertThat(((ScalarRecord)records.get(2)).valueReader().getAsLong(), equalTo(62L));

        assertThat(records.get(3), instanceOf(HistogramRecord.class));
        assertThat(records.get(3).namespaceName(), equalTo("ns1"));
        assertThat(records.get(3).bindingName(), equalTo("binding1"));
        assertThat(records.get(3).metricName(), equalTo("histogram1"));
        assertThat(((HistogramRecord)records.get(3)).stats(),
            equalTo(new long[]{1L, 63L, 134L, 6L, 22L})); // min, max, sum, cnt, avg
    }

    @Test
    public void shouldWorkWithVariousHistograms()
    {
        // GIVEN
        long[][] histogramIds = new long[][]{
            {BINDING_ID_1_11, METRIC_ID_1_42},
            {BINDING_ID_1_11, METRIC_ID_1_43},
            {BINDING_ID_1_11, METRIC_ID_1_44}
        };

        LabelManager labels = mock(LabelManager.class);
        when(labels.lookupLabel(1)).thenReturn("ns1");
        when(labels.lookupLabel(11)).thenReturn("binding1");
        when(labels.lookupLabel(42)).thenReturn("histogram2");
        when(labels.lookupLabel(43)).thenReturn("histogram3");
        when(labels.lookupLabel(44)).thenReturn("histogram4");
        when(labels.supplyLabelId("ns1")).thenReturn(1);
        when(labels.supplyLabelId("binding1")).thenReturn(11);

        HistogramsLayout histogramsLayout = mock(HistogramsLayout.class);
        when(histogramsLayout.getIds()).thenReturn(histogramIds);
        when(histogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_42)).thenReturn(READER_HISTOGRAM_2);
        when(histogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_43)).thenReturn(READER_HISTOGRAM_3);
        when(histogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_44)).thenReturn(READER_HISTOGRAM_4);

        Map<Metric.Kind, List<MetricsLayout>> layouts = Map.of(
            COUNTER, List.of(),
            GAUGE, List.of(),
            HISTOGRAM, List.of(histogramsLayout));
        MetricsReader metrics = new MetricsReader(layouts, labels, null, null);

        // WHEN
        List<MetricRecord> records = metrics.getRecords();
        ((HistogramRecord)records.get(0)).update();
        ((HistogramRecord)records.get(1)).update();
        ((HistogramRecord)records.get(2)).update();

        // THEN
        assertThat(records.get(0), instanceOf(HistogramRecord.class));
        assertThat(records.get(0).namespaceName(), equalTo("ns1"));
        assertThat(records.get(0).bindingName(), equalTo("binding1"));
        assertThat(records.get(0).metricName(), equalTo("histogram2"));
        assertThat(((HistogramRecord)records.get(0)).stats(),
            equalTo(new long[]{0L, 0L, 0L, 0L, 0L})); // min, max, sum, cnt, avg

        assertThat(records.get(1), instanceOf(HistogramRecord.class));
        assertThat(records.get(1).namespaceName(), equalTo("ns1"));
        assertThat(records.get(1).bindingName(), equalTo("binding1"));
        assertThat(records.get(1).metricName(), equalTo("histogram3"));
        assertThat(((HistogramRecord)records.get(1)).stats(),
            equalTo(new long[]{1L, 63L, 70L, 4L, 17L})); // min, max, sum, cnt, avg

        assertThat(records.get(2), instanceOf(HistogramRecord.class));
        assertThat(records.get(2).namespaceName(), equalTo("ns1"));
        assertThat(records.get(2).bindingName(), equalTo("binding1"));
        assertThat(records.get(2).metricName(), equalTo("histogram4"));
        assertThat(((HistogramRecord)records.get(2)).stats(),
            equalTo(new long[]{3L, 65535L, 131259L, 45L, 2916L})); // min, max, sum, cnt, avg
    }

    @Test
    public void shouldReturnEmptyList()
    {
        // GIVEN
        LabelManager labels = mock(LabelManager.class);
        Map<Metric.Kind, List<MetricsLayout>> layouts = Map.of(
            COUNTER, List.of(),
            GAUGE, List.of(),
            HISTOGRAM, List.of());
        MetricsReader metrics = new MetricsReader(layouts, labels, null, null);

        // WHEN
        List<MetricRecord> records = metrics.getRecords();

        // THEN
        assertThat(records.size(), equalTo(0));
    }
}
*/
