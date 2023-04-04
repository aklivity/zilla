/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.command.metrics.internal.record;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import org.junit.Test;

public class CounterRecordTest
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
        CounterRecord counter = new CounterRecord(0L, 0L, readers, null, FORMATTER);

        // WHEN
        String stringValue = counter.stringValue();

        // THEN
        assertThat(stringValue, equalTo("200"));
    }

    @Test
    public void shouldReturnZero()
    {
        // GIVEN
        LongSupplier[] readers = new LongSupplier[]{READER_0};
        CounterRecord counter = new CounterRecord(0L, 0L, readers, null, FORMATTER);

        // WHEN
        String stringValue = counter.stringValue();

        // THEN
        assertThat(stringValue, equalTo("0"));
    }

    @Test
    public void shouldReturnZeroWhenEmpty()
    {
        // GIVEN
        LongSupplier[] readers = new LongSupplier[]{};
        CounterRecord counter = new CounterRecord(0L, 0L, readers, null, FORMATTER);

        // WHEN
        String stringValue = counter.stringValue();

        // THEN
        assertThat(stringValue, equalTo("0"));
    }
}
