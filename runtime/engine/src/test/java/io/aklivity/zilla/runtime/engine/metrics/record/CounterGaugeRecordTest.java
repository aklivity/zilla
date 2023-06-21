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

import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import org.junit.Test;

public class CounterGaugeRecordTest
{
    private static final LongSupplier READER_0 = () -> 0L;
    private static final LongSupplier READER_42 = () -> 42L;
    private static final LongSupplier READER_58 = () -> 58L;
    private static final LongSupplier READER_100 = () -> 100L;
    private static final LongFunction<String> FORMATTER = String::valueOf;

    @Test
    public void shouldReturnSum()
    {
        // GIVEN
        LongSupplier[] readers = new LongSupplier[]{READER_42, READER_58, READER_100};
        CounterGaugeRecord counter = new CounterGaugeRecord(0L, 0L, readers, null);

        // WHEN
        long value = counter.valueReader().getAsLong();

        // THEN
        assertThat(value, equalTo(200L));
    }

    @Test
    public void shouldReturnZero()
    {
        // GIVEN
        LongSupplier[] readers = new LongSupplier[]{READER_0};
        CounterGaugeRecord counter = new CounterGaugeRecord(0L, 0L, readers, null);

        // WHEN
        long value = counter.valueReader().getAsLong();

        // THEN
        assertThat(value, equalTo(0L));
    }

    @Test
    public void shouldReturnZeroWhenEmpty()
    {
        // GIVEN
        LongSupplier[] readers = new LongSupplier[]{};
        CounterGaugeRecord counter = new CounterGaugeRecord(0L, 0L, readers, null);

        // WHEN
        long value = counter.valueReader().getAsLong();

        // THEN
        assertThat(value, equalTo(0L));
    }
}
