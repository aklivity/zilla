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
package io.aklivity.zilla.runtime.exporter.prometheus.internal.layout;

import static io.aklivity.zilla.runtime.exporter.prometheus.internal.layout.Layout.Mode.CREATE_READ_WRITE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.junit.Test;

public class HistogramsLayoutTest
{
    @Test
    public void shouldWorkWithTheDefinedBucketLimits()
    {
        String fileName = "target/zilla-itests/histograms0";
        Path path = Paths.get(fileName);
        HistogramsLayout histogramsLayout = new HistogramsLayout.Builder()
                .path(path)
                .capacity(8192)
                .mode(CREATE_READ_WRITE)
                .build();

        LongConsumer writer = histogramsLayout.supplyWriter(11L, 42L);
        LongSupplier[] readers = histogramsLayout.supplyReaders(11L, 42L);

        // bucket 0 (0L - 1L)
        writer.accept(0L);
        writer.accept(1L);
        assertThat(readers[0].getAsLong(), equalTo(2L));

        // bucket 1 (2L - 3L)
        writer.accept(2L);
        writer.accept(3L);
        assertThat(readers[1].getAsLong(), equalTo(2L));

        // bucket 2 (4L - 7L)
        writer.accept(7L);
        assertThat(readers[2].getAsLong(), equalTo(1L));

        // bucket 3 (8L - 15L)
        writer.accept(15L);
        writer.accept(15L);
        writer.accept(15L);
        assertThat(readers[3].getAsLong(), equalTo(3L));

        // bucket 4 (16L - 31L)
        writer.accept(20L);
        assertThat(readers[4].getAsLong(), equalTo(1L));

        // bucket 5 (32L - 63L)
        writer.accept(32L);
        writer.accept(33L);
        writer.accept(55L);
        writer.accept(63L);
        assertThat(readers[5].getAsLong(), equalTo(4L));

        // bucket 20 (1_048_576L - 2_097_151L)
        writer.accept(1_048_576L);
        writer.accept(1_111_111L);
        writer.accept(1_111_111L);
        writer.accept(1_111_111L);
        writer.accept(2_097_151L);
        assertThat(readers[20].getAsLong(), equalTo(5L));

        // bucket 30 (1_073_741_824L - 2_147_483_647L)
        writer.accept(1_073_741_824L);
        writer.accept(2_147_483_647L);
        assertThat(readers[30].getAsLong(), equalTo(2L));

        // bucket 40 (1_099_511_627_776L - 2_199_023_255_551L)
        writer.accept(1_099_511_627_776L);
        writer.accept(1_111_111_111_111L);
        writer.accept(2_199_023_255_551L);
        assertThat(readers[40].getAsLong(), equalTo(3L));

        // bucket 50
        writer.accept(1L << 50);
        assertThat(readers[50].getAsLong(), equalTo(1L));

        // bucket 60
        long power60 = 1L << 60;
        writer.accept(power60);
        writer.accept(power60 + 42);
        writer.accept(power60 + 77);
        assertThat(readers[60].getAsLong(), equalTo(3L));

        // bucket 61
        writer.accept(1L << 61);
        assertThat(readers[61].getAsLong(), equalTo(1L));

        // bucket 62
        writer.accept(1L << 62);
        writer.accept(Long.MAX_VALUE);
        assertThat(readers[62].getAsLong(), equalTo(2L));
    }

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

        LongConsumer writer1 = histogramsLayout.supplyWriter(11L, 42L);
        LongConsumer writer2 = histogramsLayout.supplyWriter(22L, 77L);
        LongConsumer writer3 = histogramsLayout.supplyWriter(33L, 88L);

        LongSupplier[] readers0 = histogramsLayout.supplyReaders(99999L, 99999L);
        LongSupplier[] readers1 = histogramsLayout.supplyReaders(11L, 42L);
        LongSupplier[] readers2 = histogramsLayout.supplyReaders(22L, 77L);
        LongSupplier[] readers3 = histogramsLayout.supplyReaders(33L, 88L);

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
    public void shouldThrowExceptionIfBufferIsTooSmall() throws Exception
    {
        String fileName = "target/zilla-itests/histograms2";
        Path path = Paths.get(fileName);
        HistogramsLayout histogramsLayout = new HistogramsLayout.Builder()
                .path(path)
                .capacity(1559) // we'd need 1560 bytes here for the0 3 records
                .mode(CREATE_READ_WRITE)
                .build();

        histogramsLayout.supplyWriter(11L, 42L);
        histogramsLayout.supplyWriter(22L, 77L);
        assertThrows(IndexOutOfBoundsException.class, () ->
        {
            histogramsLayout.supplyWriter(33L, 88L);
        });

        histogramsLayout.close();
        assertTrue(Files.exists(path));
        Files.delete(path);
    }

    @Test
    public void shouldGetIds()
    {
        String fileName = "target/zilla-itests/histograms3";
        Path path = Paths.get(fileName);
        HistogramsLayout countersLayout = new HistogramsLayout.Builder()
                .path(path)
                .capacity(8192)
                .mode(CREATE_READ_WRITE)
                .build();

        countersLayout.supplyWriter(11L, 42L);
        countersLayout.supplyWriter(22L, 77L);
        countersLayout.supplyWriter(33L, 88L);
        long[][] expectedIds = new long[][]{{11L, 42L}, {22L, 77L}, {33L, 88L}};

        assertThat(countersLayout.getIds(), equalTo(expectedIds));
    }
}
