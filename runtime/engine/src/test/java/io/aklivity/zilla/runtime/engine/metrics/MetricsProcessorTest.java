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
package io.aklivity.zilla.runtime.engine.metrics;

import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.COUNTER;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.internal.LabelManager;
import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.CountersLayout;
import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.MetricsLayout;
import io.aklivity.zilla.runtime.engine.internal.metrics.MetricRecord;
import io.aklivity.zilla.runtime.engine.internal.metrics.MetricsProcessor;

public class MetricsProcessorTest
{
    public static final long BINDING_ID_1_11 = pack(1, 11);
    public static final long BINDING_ID_1_12 = pack(1, 12);
    public static final long BINDING_ID_2_11 = pack(2, 11);
    public static final long METRIC_ID_1_21 = pack(1, 21);
    public static final long METRIC_ID_1_22 = pack(1, 22);
    public static final long METRIC_ID_2_21 = pack(2, 21);
    public static final LongSupplier READER_42 = () -> 42L;
    public static final LongSupplier READER_43 = () -> 43L;
    public static final LongSupplier READER_44 = () -> 44L;
    public static final LongSupplier READER_77 = () -> 77L;

    @Test
    public void shouldResolveCounterValue()
    {
        // GIVEN
        long[][] counterIds = new long[][]{
            {BINDING_ID_1_11, METRIC_ID_1_21},
            {BINDING_ID_1_11, METRIC_ID_1_22},
            {BINDING_ID_1_12, METRIC_ID_1_21},
            {BINDING_ID_2_11, METRIC_ID_2_21}
        };
        LabelManager labels = mock(LabelManager.class);
        when(labels.lookupLabel(1)).thenReturn("ns1");
        when(labels.lookupLabel(2)).thenReturn("ns2");
        when(labels.lookupLabel(11)).thenReturn("binding1");
        when(labels.lookupLabel(12)).thenReturn("binding2");
        when(labels.lookupLabel(21)).thenReturn("counter1");
        when(labels.lookupLabel(22)).thenReturn("counter2");

        CountersLayout countersLayout = mock(CountersLayout.class);
        when(countersLayout.getIds()).thenReturn(counterIds);
        when(countersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_21)).thenReturn(READER_42);
        when(countersLayout.supplyReader(BINDING_ID_1_11, METRIC_ID_1_22)).thenReturn(READER_77);
        when(countersLayout.supplyReader(BINDING_ID_1_12, METRIC_ID_1_21)).thenReturn(READER_43);
        when(countersLayout.supplyReader(BINDING_ID_2_11, METRIC_ID_2_21)).thenReturn(READER_44);

        Map<Metric.Kind, List<MetricsLayout>> layouts = Map.of(
            COUNTER, List.of(countersLayout));
        MetricsProcessor metrics = new MetricsProcessor(layouts, labels, null, null);

        // WHEN
        MetricRecord record0 = metrics.findRecord("none", "none", "none");
        MetricRecord record1 = metrics.findRecord("ns1", "binding1", "counter1");
        MetricRecord record2 = metrics.findRecord("ns1", "binding1", "counter2");
        MetricRecord record3 = metrics.findRecord("ns1", "binding2", "counter1");
        MetricRecord record4 = metrics.findRecord("ns2", "binding1", "counter1");

        // THEN
        assertThat(record0, nullValue());
        assertThat(record1.value(), equalTo(42L));
        assertThat(record2.value(), equalTo(77L));
        assertThat(record3.value(), equalTo(43L));
        assertThat(record4.value(), equalTo(44L));
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
