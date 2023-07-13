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

import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;

public class HistogramRecordTest
{
    public static final LongSupplier[] READER_HISTOGRAM = new LongSupplier[]
    {
        () -> 0L, () -> 1L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 22L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 77L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L
    };

    @Test
    public void shouldResolveFields()
    {
        // GIVEN
        LongFunction<String> labelResolver = mock(LongFunction.class);
        long bindingId = NamespacedId.id(77, 7);
        long metricId = NamespacedId.id(77, 8);
        when(labelResolver.apply(77L)).thenReturn("namespace1");
        when(labelResolver.apply(bindingId)).thenReturn("binding1");
        when(labelResolver.apply(metricId)).thenReturn("metric1");
        HistogramRecord histogram = new HistogramRecord(bindingId, metricId, READER_HISTOGRAM, labelResolver);

        // WHEN
        histogram.update();
        String namespaceName = histogram.namespace();
        String bindingName = histogram.binding();
        String metricName = histogram.metric();
        int buckets = histogram.buckets();
        long[] value = histogram.bucketValues();
        long[] stats = histogram.stats();

        // THEN
        assertThat(namespaceName, equalTo("namespace1"));
        assertThat(bindingName, equalTo("binding1"));
        assertThat(metricName, equalTo("metric1"));
        assertThat(buckets, equalTo(63));
        assertThat(value[15], equalTo(77L));
        assertThat(stats[0], equalTo(3L)); // min
        assertThat(stats[1], equalTo(65_535L)); // max
        assertThat(stats[2], equalTo(5_051_808L)); // sum
        assertThat(stats[3], equalTo(100L)); // cnt
        assertThat(stats[4], equalTo(50_518L)); // avg
    }

    @Test
    public void shouldReturnZeroStatsWhenEmpty()
    {
        // GIVEN
        LongSupplier[] readers = new LongSupplier[]{};
        HistogramRecord histogram = new HistogramRecord(0L, 0L, readers, null);

        // WHEN
        long[] stats = histogram.stats();

        // THEN
        assertThat(stats[0], equalTo(0L)); // min
        assertThat(stats[1], equalTo(0L)); // max
        assertThat(stats[2], equalTo(0L)); // sum
        assertThat(stats[3], equalTo(0L)); // cnt
        assertThat(stats[4], equalTo(0L)); // avg
    }
}
