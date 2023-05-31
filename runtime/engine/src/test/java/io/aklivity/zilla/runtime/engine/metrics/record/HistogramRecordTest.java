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
package io.aklivity.zilla.runtime.engine.metrics.record;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.function.LongSupplier;

import org.junit.Test;

public class HistogramRecordTest
{
    public static final LongSupplier[] READER_HISTOGRAM_0 = new LongSupplier[]
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
    public static final LongSupplier[] READER_HISTOGRAM_1 = new LongSupplier[]
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
    public static final LongSupplier[] READER_HISTOGRAM_2 = new LongSupplier[]
    {
        () -> 1L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
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
        () -> 0L, () -> 100L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
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
        () -> 0L, () -> 100L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
        () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 999_999_999L
    };

    @Test
    public void shouldReturnStats()
    {
        // GIVEN
        LongSupplier[][] readers = new LongSupplier[][]{READER_HISTOGRAM_1};
        HistogramRecord histogram = new HistogramRecord(0L, 0L, readers, null);

        // WHEN
        long[] stats = histogram.stats();

        // THEN
        assertThat(stats[0], equalTo(3L)); // min
        assertThat(stats[1], equalTo(65535L)); // max
        assertThat(stats[2], equalTo(100L)); // cnt
        assertThat(stats[3], equalTo(50518L)); // avg
    }

    @Test
    public void shouldReturnStatsOfSum()
    {
        // GIVEN
        LongSupplier[][] readers = new LongSupplier[][]{READER_HISTOGRAM_1, READER_HISTOGRAM_2, READER_HISTOGRAM_3};
        HistogramRecord histogram = new HistogramRecord(0L, 0L, readers, null);

        // WHEN
        long[] stats = histogram.stats();

        // THEN
        assertThat(stats[0], equalTo(1L)); // min
        assertThat(stats[1], equalTo(65535L)); // max
        assertThat(stats[2], equalTo(201L)); // cnt
        assertThat(stats[3], equalTo(25134L)); // avg
    }

    @Test
    public void shouldReturnLargeNumbers()
    {
        // GIVEN
        LongSupplier[][] readers = new LongSupplier[][]{READER_HISTOGRAM_4};
        HistogramRecord histogram = new HistogramRecord(0L, 0L, readers, null);

        // WHEN
        long[] stats = histogram.stats();

        // THEN
        assertThat(stats[0], equalTo(3L)); // min
        assertThat(stats[1], equalTo(9223372036854775807L)); // max
        assertThat(stats[2], equalTo(1000000099L)); // cnt
        assertThat(stats[3], equalTo(9223371122L)); // avg
    }

    @Test
    public void shouldReturnZeroStats()
    {
        // GIVEN
        LongSupplier[][] readers = new LongSupplier[][]{READER_HISTOGRAM_0};
        HistogramRecord histogram = new HistogramRecord(0L, 0L, readers, null);

        // WHEN
        long[] stats = histogram.stats();

        // THEN
        assertThat(stats[0], equalTo(0L)); // min
        assertThat(stats[1], equalTo(0L)); // max
        assertThat(stats[2], equalTo(0L)); // cnt
        assertThat(stats[3], equalTo(0L)); // avg
    }

    @Test
    public void shouldReturnZeroStatsWhenEmpty()
    {
        // GIVEN
        LongSupplier[][] readers = new LongSupplier[][]{};
        HistogramRecord histogram = new HistogramRecord(0L, 0L, readers, null);

        // WHEN
        long[] stats = histogram.stats();

        // THEN
        assertThat(stats[0], equalTo(0L)); // min
        assertThat(stats[1], equalTo(0L)); // max
        assertThat(stats[2], equalTo(0L)); // cnt
        assertThat(stats[3], equalTo(0L)); // avg
    }
}
