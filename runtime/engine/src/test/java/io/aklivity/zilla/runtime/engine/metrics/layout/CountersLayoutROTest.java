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
package io.aklivity.zilla.runtime.engine.metrics.layout;

import static io.aklivity.zilla.runtime.engine.internal.layouts.Layout.Mode.CREATE_READ_WRITE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.CountersLayout;

public class CountersLayoutROTest
{
    @Test
    public void shouldWorkInGenericCase() throws Exception
    {
        String fileName = "target/zilla-itests/counters0";
        Path path = Paths.get(fileName);
        CountersLayout countersLayout = new CountersLayout.Builder()
                .path(path)
                .capacity(8192)
                .mode(CREATE_READ_WRITE)
                .build();
        CountersLayoutRO countersLayoutRO = new CountersLayoutRO.Builder()
                .path(path)
                .build();

        LongConsumer writer1 = countersLayout.supplyWriter(11L, 42L);
        LongConsumer writer2 = countersLayout.supplyWriter(22L, 77L);
        LongConsumer writer3 = countersLayout.supplyWriter(33L, 88L);

        LongSupplier reader1 = countersLayoutRO.supplyReader(11L, 42L);
        LongSupplier reader2 = countersLayoutRO.supplyReader(22L, 77L);
        LongSupplier reader3 = countersLayoutRO.supplyReader(33L, 88L);

        assertThat(reader1.getAsLong(), equalTo(0L)); // should be 0L initially
        writer1.accept(1L);
        assertThat(reader1.getAsLong(), equalTo(1L));
        writer1.accept(1L);
        writer2.accept(100L);
        writer3.accept(77L);
        assertThat(reader1.getAsLong(), equalTo(2L)); // 1 + 1
        assertThat(reader2.getAsLong(), equalTo(100L));
        assertThat(reader3.getAsLong(), equalTo(77L));
        writer2.accept(10L);
        writer3.accept(1L);
        writer2.accept(20L);
        writer3.accept(1L);
        writer3.accept(1L);
        assertThat(reader2.getAsLong(), equalTo(130L)); // 100 + 10 + 20
        assertThat(reader3.getAsLong(), equalTo(80L)); // 77 + 1 + 1 + 1
        writer3.accept(-42L);
        assertThat(reader3.getAsLong(), equalTo(80L)); // should ignore negative numbers

        countersLayout.close();
        assertTrue(Files.exists(path));
        Files.delete(path);
    }

    @Test
    public void shouldGetIds()
    {
        // GIVEN
        String fileName = "target/zilla-itests/counters2";
        Path path = Paths.get(fileName);
        CountersLayout countersLayout = new CountersLayout.Builder()
                .path(path)
                .capacity(8192)
                .mode(CREATE_READ_WRITE)
                .build();
        CountersLayoutRO countersLayoutRO = new CountersLayoutRO.Builder()
                .path(path)
                .build();

        // WHEN
        countersLayout.supplyWriter(11L, 42L);
        countersLayout.supplyWriter(22L, 77L);
        countersLayout.supplyWriter(33L, 88L);

        // THEN
        long[][] expectedIds = new long[][]{{11L, 42L}, {22L, 77L}, {33L, 88L}};
        assertThat(countersLayoutRO.getIds(), equalTo(expectedIds));
    }
}
