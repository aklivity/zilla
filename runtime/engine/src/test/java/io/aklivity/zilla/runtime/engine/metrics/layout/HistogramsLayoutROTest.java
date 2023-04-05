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

import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.HistogramsLayout;

public class HistogramsLayoutROTest
{
    @Test
    public void shouldWorkInGenericCase() throws Exception
    {
        String fileName = "target/zilla-itests/histograms1";
        Path path = Paths.get(fileName);
        HistogramsLayout histogramsLayout = new HistogramsLayout.Builder()
                .path(path)
                .capacity(8192)
                .mode(CREATE_READ_WRITE)
                .build();
        HistogramsLayoutRO histogramsLayoutRO = new HistogramsLayoutRO.Builder()
            .path(path)
            .build();

        LongConsumer writer1 = histogramsLayout.supplyWriter(11L, 42L);
        LongConsumer writer2 = histogramsLayout.supplyWriter(22L, 77L);
        LongConsumer writer3 = histogramsLayout.supplyWriter(33L, 88L);

        LongSupplier[] readers0 = histogramsLayoutRO.supplyReaders(99999L, 99999L);
        LongSupplier[] readers1 = histogramsLayoutRO.supplyReaders(11L, 42L);
        LongSupplier[] readers2 = histogramsLayoutRO.supplyReaders(22L, 77L);
        LongSupplier[] readers3 = histogramsLayoutRO.supplyReaders(33L, 88L);

        assertThat(readers0[0].getAsLong(), equalTo(0L));
        assertThat(readers0[62].getAsLong(), equalTo(0L));

        assertThat(readers1[0].getAsLong(), equalTo(0L));
        assertThat(readers1[3].getAsLong(), equalTo(0L));
        assertThat(readers1[10].getAsLong(), equalTo(0L));
        writer1.accept(0L);
        writer1.accept(1L);
        assertThat(readers1[0].getAsLong(), equalTo(2L));
        writer1.accept(15L);
        assertThat(readers1[3].getAsLong(), equalTo(1L));
        writer1.accept(1_024L);
        assertThat(readers1[10].getAsLong(), equalTo(1L));

        writer1.accept(1_111_111L);
        assertThat(readers1[20].getAsLong(), equalTo(1L));
        writer2.accept(8_888_888L);
        writer2.accept(8_888_888L);
        writer2.accept(8_888_888L);
        assertThat(readers2[23].getAsLong(), equalTo(3L));
        writer3.accept(55_555L);
        assertThat(readers3[15].getAsLong(), equalTo(1L));

        writer2.accept(4_000L);
        assertThat(readers2[11].getAsLong(), equalTo(1L));
        writer1.accept(1_111L);
        assertThat(readers1[10].getAsLong(), equalTo(2L));
        writer3.accept(999_999_999_999L);
        assertThat(readers3[39].getAsLong(), equalTo(1L));
        writer2.accept(Long.MAX_VALUE - 42);
        writer2.accept(Long.MAX_VALUE - 1);
        writer2.accept(Long.MAX_VALUE);
        assertThat(readers2[62].getAsLong(), equalTo(3L));

        histogramsLayout.close();
        assertTrue(Files.exists(path));
        Files.delete(path);
    }

    @Test
    public void shouldGetIds()
    {
        // GIVEN
        String fileName = "target/zilla-itests/histograms3";
        Path path = Paths.get(fileName);
        HistogramsLayout countersLayout = new HistogramsLayout.Builder()
                .path(path)
                .capacity(8192)
                .mode(CREATE_READ_WRITE)
                .build();
        HistogramsLayoutRO histogramsLayoutRO = new HistogramsLayoutRO.Builder()
                .path(path)
                .build();

        // WHEN
        countersLayout.supplyWriter(11L, 42L);
        countersLayout.supplyWriter(22L, 77L);
        countersLayout.supplyWriter(33L, 88L);

        // THEN
        long[][] expectedIds = new long[][]{{11L, 42L}, {22L, 77L}, {33L, 88L}};
        assertThat(histogramsLayoutRO.getIds(), equalTo(expectedIds));
    }
}
