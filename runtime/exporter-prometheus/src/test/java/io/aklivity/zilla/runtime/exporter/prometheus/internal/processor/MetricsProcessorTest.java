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
/*package io.aklivity.zilla.runtime.exporter.prometheus.internal.processor;

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
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.layout.CountersLayout;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.layout.GaugesLayout;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.layout.HistogramsLayout;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.layout.MetricsLayout;

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
            "# HELP counter_1 description for counter1\n" +
            "# TYPE counter_1 counter\n" +
            "counter_1{namespace=\"ns1\",binding=\"binding1\"} 42\n" +
            "\n" +
            "# HELP counter_2 description for counter2\n" +
            "# TYPE counter_2 counter\n" +
            "counter_2{namespace=\"ns1\",binding=\"binding1\"} 77\n" +
            "\n" +
            "# HELP counter_1 description for counter1\n" +
            "# TYPE counter_1 counter\n" +
            "counter_1{namespace=\"ns1\",binding=\"binding2\"} 43\n" +
            "\n" +
            "# HELP counter_1 description for counter1\n" +
            "# TYPE counter_1 counter\n" +
            "counter_1{namespace=\"ns2\",binding=\"binding1\"} 44\n" +
            "\n" +
            "# HELP gauge_1 description for gauge1\n" +
            "# TYPE gauge_1 gauge\n" +
            "gauge_1{namespace=\"ns1\",binding=\"binding1\"} 88\n" +
            "\n" +
            "# HELP histogram_1 description for histogram1\n" +
            "# TYPE histogram_1 histogram\n" +
            "histogram_1_bucket{le=\"2\",namespace=\"ns1\",binding=\"binding1\"} 1\n" +
            "histogram_1_bucket{le=\"4\",namespace=\"ns1\",binding=\"binding1\"} 1\n" +
            "histogram_1_bucket{le=\"8\",namespace=\"ns1\",binding=\"binding1\"} 1\n" +
            "histogram_1_bucket{le=\"16\",namespace=\"ns1\",binding=\"binding1\"} 1\n" +
            "histogram_1_bucket{le=\"32\",namespace=\"ns1\",binding=\"binding1\"} 1\n" +
            "histogram_1_bucket{le=\"64\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"128\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"256\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"512\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"1024\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"2048\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"4096\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"8192\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"16384\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"32768\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"65536\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"131072\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"262144\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"524288\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"1048576\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"2097152\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"4194304\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"8388608\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"16777216\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"33554432\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"67108864\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"134217728\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"268435456\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"536870912\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"1073741824\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"2147483648\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"4294967296\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"8589934592\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"17179869184\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"34359738368\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"68719476736\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"137438953472\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"274877906944\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"549755813888\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"1099511627776\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"2199023255552\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"4398046511104\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"8796093022208\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"17592186044416\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"35184372088832\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"70368744177664\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"140737488355328\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"281474976710656\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"562949953421312\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"1125899906842624\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"2251799813685248\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"4503599627370496\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"9007199254740992\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"18014398509481984\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"36028797018963968\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"72057594037927936\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"144115188075855872\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"288230376151711744\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"576460752303423488\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"1152921504606846976\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"2305843009213693952\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"4611686018427387904\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"+Inf\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_sum{namespace=\"ns1\",binding=\"binding1\"} 64\n" +
            "histogram_1_count{namespace=\"ns1\",binding=\"binding1\"} 2\n\n\n";
        CountersLayout countersLayout = mock(CountersLayout.class);
        when(countersLayout.getIds()).thenReturn(counterIds);
        when(countersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_42);
        when(countersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_77);
        when(countersLayout.supplyReader(BINDING_ID_1_12, METRIC_ID_1_21)).thenReturn(READER_43);
        when(countersLayout.supplyReader(BINDING_ID_2_11, METRIC_ID_2_21)).thenReturn(READER_44);

        GaugesLayout gaugesLayout = mock(GaugesLayout.class);
        when(gaugesLayout.getIds()).thenReturn(gaugeIds);
        when(gaugesLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_31)).thenReturn(READER_88);

        HistogramsLayout histogramsLayout = mock(HistogramsLayout.class);
        when(histogramsLayout.getIds()).thenReturn(histogramIds);
        when(histogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_41)).thenReturn(READER_HISTOGRAM_1);

        Map<Metric.Kind, List<MetricsLayout>> layouts = Map.of(
                COUNTER, List.of(countersLayout),
                GAUGE, List.of(gaugesLayout),
                HISTOGRAM, List.of(histogramsLayout));

        IntFunction<String> localNameSupplier = mock(IntFunction.class);
        when(localNameSupplier.apply(1)).thenReturn("ns1");
        when(localNameSupplier.apply(2)).thenReturn("ns2");
        when(localNameSupplier.apply(11)).thenReturn("binding1");
        when(localNameSupplier.apply(12)).thenReturn("binding2");
        when(localNameSupplier.apply(21)).thenReturn("counter1");
        when(localNameSupplier.apply(22)).thenReturn("counter2");
        when(localNameSupplier.apply(31)).thenReturn("gauge1");
        when(localNameSupplier.apply(41)).thenReturn("histogram1");

        Function<String, String> kindSupplier = mock(Function.class);
        when(kindSupplier.apply("counter1")).thenReturn("counter");
        when(kindSupplier.apply("counter2")).thenReturn("counter");
        when(kindSupplier.apply("gauge1")).thenReturn("gauge");
        when(kindSupplier.apply("histogram1")).thenReturn("histogram");

        Function<String, String> nameSupplier = mock(Function.class);
        when(nameSupplier.apply("counter1")).thenReturn("counter_1");
        when(nameSupplier.apply("counter2")).thenReturn("counter_2");
        when(nameSupplier.apply("gauge1")).thenReturn("gauge_1");
        when(nameSupplier.apply("histogram1")).thenReturn("histogram_1");

        Function<String, String> descriptionSupplier = mock(Function.class);
        when(descriptionSupplier.apply("counter1")).thenReturn("description for counter1");
        when(descriptionSupplier.apply("counter2")).thenReturn("description for counter2");
        when(descriptionSupplier.apply("gauge1")).thenReturn("description for gauge1");
        when(descriptionSupplier.apply("histogram1")).thenReturn("description for histogram1");

        MetricsProcessor metrics = new MetricsProcessor(layouts, localNameSupplier,
            kindSupplier, nameSupplier, descriptionSupplier, null, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        metrics.print(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
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
            "# HELP counter_1 description for counter1\n" +
            "# TYPE counter_1 counter\n" +
            "counter_1{namespace=\"ns1\",binding=\"binding1\"} 42\n" +
            "\n" +
            "# HELP counter_2 description for counter2\n" +
            "# TYPE counter_2 counter\n" +
            "counter_2{namespace=\"ns1\",binding=\"binding1\"} 77\n" +
            "\n" +
            "# HELP gauge_1 description for gauge1\n" +
            "# TYPE gauge_1 gauge\n" +
            "gauge_1{namespace=\"ns1\",binding=\"binding1\"} 62\n" +
            "\n" +
            "# HELP histogram_1 description for histogram1\n" +
            "# TYPE histogram_1 histogram\n" +
            "histogram_1_bucket{le=\"2\",namespace=\"ns1\",binding=\"binding1\"} 2\n" +
            "histogram_1_bucket{le=\"4\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_1_bucket{le=\"8\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_1_bucket{le=\"16\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_1_bucket{le=\"32\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_1_bucket{le=\"64\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"128\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"256\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"512\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"1024\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"2048\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"4096\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"8192\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"16384\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"32768\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"65536\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"131072\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"262144\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"524288\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"1048576\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"2097152\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"4194304\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"8388608\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"16777216\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"33554432\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"67108864\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"134217728\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"268435456\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"536870912\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"1073741824\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"2147483648\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"4294967296\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"8589934592\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"17179869184\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"34359738368\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"68719476736\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"137438953472\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"274877906944\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"549755813888\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"1099511627776\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"2199023255552\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"4398046511104\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"8796093022208\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"17592186044416\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"35184372088832\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"70368744177664\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"140737488355328\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"281474976710656\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"562949953421312\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"1125899906842624\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"2251799813685248\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"4503599627370496\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"9007199254740992\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"18014398509481984\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"36028797018963968\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"72057594037927936\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"144115188075855872\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"288230376151711744\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"576460752303423488\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"1152921504606846976\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"2305843009213693952\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"4611686018427387904\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_bucket{le=\"+Inf\",namespace=\"ns1\",binding=\"binding1\"} 6\n" +
            "histogram_1_sum{namespace=\"ns1\",binding=\"binding1\"} 134\n" +
            "histogram_1_count{namespace=\"ns1\",binding=\"binding1\"} 6\n\n\n";
        CountersLayout countersLayout0 = mock(CountersLayout.class);
        when(countersLayout0.getIds()).thenReturn(counterIds);
        when(countersLayout0.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_2);
        when(countersLayout0.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_30);

        CountersLayout countersLayout1 = mock(CountersLayout.class);
        when(countersLayout1.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_20);
        when(countersLayout1.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_40);

        CountersLayout countersLayout2 = mock(CountersLayout.class);
        when(countersLayout2.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_20);
        when(countersLayout2.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_7);

        GaugesLayout gaugesLayout0 = mock(GaugesLayout.class);
        when(gaugesLayout0.getIds()).thenReturn(gaugeIds);
        when(gaugesLayout0.supplyReader(BINDING_ID_1_11, METRIC_ID_1_31)).thenReturn(READER_40);

        GaugesLayout gaugesLayout1 = mock(GaugesLayout.class);
        when(gaugesLayout1.supplyReader(BINDING_ID_1_11, METRIC_ID_1_31)).thenReturn(READER_20);

        GaugesLayout gaugesLayout2 = mock(GaugesLayout.class);
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

        IntFunction<String> localNameSupplier = mock(IntFunction.class);
        when(localNameSupplier.apply(1)).thenReturn("ns1");
        when(localNameSupplier.apply(2)).thenReturn("ns2");
        when(localNameSupplier.apply(11)).thenReturn("binding1");
        when(localNameSupplier.apply(12)).thenReturn("binding2");
        when(localNameSupplier.apply(21)).thenReturn("counter1");
        when(localNameSupplier.apply(22)).thenReturn("counter2");
        when(localNameSupplier.apply(31)).thenReturn("gauge1");
        when(localNameSupplier.apply(41)).thenReturn("histogram1");

        Function<String, String> kindSupplier = mock(Function.class);
        when(kindSupplier.apply("counter1")).thenReturn("counter");
        when(kindSupplier.apply("counter2")).thenReturn("counter");
        when(kindSupplier.apply("gauge1")).thenReturn("gauge");
        when(kindSupplier.apply("histogram1")).thenReturn("histogram");

        Function<String, String> nameSupplier = mock(Function.class);
        when(nameSupplier.apply("counter1")).thenReturn("counter_1");
        when(nameSupplier.apply("counter2")).thenReturn("counter_2");
        when(nameSupplier.apply("gauge1")).thenReturn("gauge_1");
        when(nameSupplier.apply("histogram1")).thenReturn("histogram_1");

        Function<String, String> descriptionSupplier = mock(Function.class);
        when(descriptionSupplier.apply("counter1")).thenReturn("description for counter1");
        when(descriptionSupplier.apply("counter2")).thenReturn("description for counter2");
        when(descriptionSupplier.apply("gauge1")).thenReturn("description for gauge1");
        when(descriptionSupplier.apply("histogram1")).thenReturn("description for histogram1");

        MetricsProcessor metrics = new MetricsProcessor(layouts, localNameSupplier,
            kindSupplier, nameSupplier, descriptionSupplier, null, null);
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
                {BINDING_ID_1_11, METRIC_ID_1_43}
        };
        String expectedOutput =
            "# HELP histogram_2 description for histogram 2\n" +
            "# TYPE histogram_2 histogram\n" +
            "histogram_2_bucket{le=\"2\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"4\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"8\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"16\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"32\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"64\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"128\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"256\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"512\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"1024\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"2048\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"4096\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"8192\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"16384\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"32768\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"65536\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"131072\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"262144\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"524288\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"1048576\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"2097152\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"4194304\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"8388608\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"16777216\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"33554432\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"67108864\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"134217728\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"268435456\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"536870912\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"1073741824\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"2147483648\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"4294967296\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"8589934592\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"17179869184\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"34359738368\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"68719476736\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"137438953472\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"274877906944\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"549755813888\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"1099511627776\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"2199023255552\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"4398046511104\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"8796093022208\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"17592186044416\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"35184372088832\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"70368744177664\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"140737488355328\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"281474976710656\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"562949953421312\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"1125899906842624\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"2251799813685248\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"4503599627370496\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"9007199254740992\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"18014398509481984\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"36028797018963968\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"72057594037927936\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"144115188075855872\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"288230376151711744\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"576460752303423488\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"1152921504606846976\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"2305843009213693952\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"4611686018427387904\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_bucket{le=\"+Inf\",namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_sum{namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "histogram_2_count{namespace=\"ns1\",binding=\"binding1\"} 0\n" +
            "\n" +
            "\n" +
            "# HELP histogram_3 description for histogram 3\n" +
            "# TYPE histogram_3 histogram\n" +
            "histogram_3_bucket{le=\"2\",namespace=\"ns1\",binding=\"binding1\"} 1\n" +
            "histogram_3_bucket{le=\"4\",namespace=\"ns1\",binding=\"binding1\"} 3\n" +
            "histogram_3_bucket{le=\"8\",namespace=\"ns1\",binding=\"binding1\"} 3\n" +
            "histogram_3_bucket{le=\"16\",namespace=\"ns1\",binding=\"binding1\"} 3\n" +
            "histogram_3_bucket{le=\"32\",namespace=\"ns1\",binding=\"binding1\"} 3\n" +
            "histogram_3_bucket{le=\"64\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"128\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"256\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"512\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"1024\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"2048\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"4096\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"8192\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"16384\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"32768\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"65536\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"131072\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"262144\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"524288\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"1048576\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"2097152\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"4194304\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"8388608\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"16777216\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"33554432\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"67108864\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"134217728\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"268435456\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"536870912\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"1073741824\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"2147483648\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"4294967296\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"8589934592\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"17179869184\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"34359738368\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"68719476736\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"137438953472\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"274877906944\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"549755813888\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"1099511627776\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"2199023255552\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"4398046511104\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"8796093022208\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"17592186044416\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"35184372088832\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"70368744177664\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"140737488355328\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"281474976710656\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"562949953421312\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"1125899906842624\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"2251799813685248\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"4503599627370496\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"9007199254740992\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"18014398509481984\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"36028797018963968\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"72057594037927936\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"144115188075855872\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"288230376151711744\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"576460752303423488\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"1152921504606846976\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"2305843009213693952\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"4611686018427387904\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_bucket{le=\"+Inf\",namespace=\"ns1\",binding=\"binding1\"} 4\n" +
            "histogram_3_sum{namespace=\"ns1\",binding=\"binding1\"} 70\n" +
            "histogram_3_count{namespace=\"ns1\",binding=\"binding1\"} 4\n\n\n";
        HistogramsLayout histogramsLayout = mock(HistogramsLayout.class);
        when(histogramsLayout.getIds()).thenReturn(histogramIds);
        when(histogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_42)).thenReturn(READER_HISTOGRAM_2);
        when(histogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_43)).thenReturn(READER_HISTOGRAM_3);
        when(histogramsLayout.supplyReaders(BINDING_ID_1_11, METRIC_ID_1_44)).thenReturn(READER_HISTOGRAM_4);

        Map<Metric.Kind, List<MetricsLayout>> layouts = Map.of(
                COUNTER, List.of(),
                GAUGE, List.of(),
                HISTOGRAM, List.of(histogramsLayout));

        IntFunction<String> localNameSupplier = mock(IntFunction.class);
        when(localNameSupplier.apply(1)).thenReturn("ns1");
        when(localNameSupplier.apply(11)).thenReturn("binding1");
        when(localNameSupplier.apply(42)).thenReturn("histogram2");
        when(localNameSupplier.apply(43)).thenReturn("histogram3");

        Function<String, String> kindSupplier = mock(Function.class);
        when(kindSupplier.apply("histogram2")).thenReturn("histogram");
        when(kindSupplier.apply("histogram3")).thenReturn("histogram");

        Function<String, String> nameSupplier = mock(Function.class);
        when(nameSupplier.apply("histogram2")).thenReturn("histogram_2");
        when(nameSupplier.apply("histogram3")).thenReturn("histogram_3");

        Function<String, String> descriptionSupplier = mock(Function.class);
        when(descriptionSupplier.apply("histogram2")).thenReturn("description for histogram 2");
        when(descriptionSupplier.apply("histogram3")).thenReturn("description for histogram 3");

        MetricsProcessor metrics = new MetricsProcessor(layouts, localNameSupplier,
            kindSupplier, nameSupplier, descriptionSupplier, null, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        metrics.print(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
    }

    @Test
    public void shouldPrintEmpty() throws Exception
    {
        // GIVEN
        String expectedOutput = "";
        Map<Metric.Kind, List<MetricsLayout>> layouts = Map.of(
                COUNTER, List.of(),
                GAUGE, List.of(),
                HISTOGRAM, List.of());
        IntFunction<String> localNameSupplier = mock(IntFunction.class);
        Function<String, String> kindSupplier = mock(Function.class);
        Function<String, String> nameSupplier = mock(Function.class);
        Function<String, String> descriptionSupplier = mock(Function.class);
        MetricsProcessor metrics = new MetricsProcessor(layouts, localNameSupplier,
            kindSupplier, nameSupplier, descriptionSupplier, null, null);
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
*/
