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

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;
import io.aklivity.zilla.runtime.engine.metrics.Collector;

public class MetricsReaderTest
{
    public static final LongSupplier READER_42 = () -> 42L;
    public static final LongSupplier READER_84 = () -> 84L;
    public static final LongSupplier[] READER_HISTOGRAM = new LongSupplier[]
        {
            () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 77L,
            () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
            () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
            () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
            () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
            () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
            () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
            () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L
        };

    @Test
    public void shouldCollectMetrics()
    {
        // GIVEN
        LongFunction<String> labelResolver = mock(LongFunction.class);
        Collector collector = mock(Collector.class);
        long bindingId = NamespacedId.id(4, 7);
        long counterId = NamespacedId.id(4, 8);
        when(collector.counterIds()).thenReturn(new long[][]{{bindingId, counterId}});
        when(collector.counter(bindingId, counterId)).thenReturn(READER_42);
        long gaugeId = NamespacedId.id(4, 9);
        when(collector.gaugeIds()).thenReturn(new long[][]{{bindingId, gaugeId}});
        when(collector.gauge(bindingId, gaugeId)).thenReturn(READER_84);
        long histogramId = NamespacedId.id(4, 10);
        when(collector.histogramIds()).thenReturn(new long[][]{{bindingId, histogramId}});
        when(collector.histogram(bindingId, histogramId)).thenReturn(READER_HISTOGRAM);
        MetricsReader metrics = new MetricsReader(collector, labelResolver);

        // WHEN
        List<MetricRecord> records = metrics.records();

        // THEN
        assertThat(records.size(), equalTo(3));
        ScalarRecord counter = (ScalarRecord) records.get(0);
        ScalarRecord gauge = (ScalarRecord) records.get(1);
        HistogramRecord histogram = (HistogramRecord) records.get(2);
        histogram.update();
        assertThat(counter.valueReader().getAsLong(), equalTo(42L));
        assertThat(gauge.valueReader().getAsLong(), equalTo(84L));
        assertThat(histogram.bucketValues()[7], equalTo(77L));
    }

    @Test
    public void shouldReturnEmptyList()
    {
        // GIVEN
        LongFunction<String> labelResolver = mock(LongFunction.class);
        Collector collector = mock(Collector.class);
        when(collector.counterIds()).thenReturn(new long[][]{});
        when(collector.gaugeIds()).thenReturn(new long[][]{});
        when(collector.histogramIds()).thenReturn(new long[][]{});
        MetricsReader metrics = new MetricsReader(collector, labelResolver);

        // WHEN
        List<MetricRecord> records = metrics.records();

        // THEN
        assertThat(records.size(), equalTo(0));
    }
}
