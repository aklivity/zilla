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

public class GaugesLayoutTest
{
    @Test
    public void shouldWorkInGenericCase() throws Exception
    {
        String fileName = "target/zilla-itests/gauges0";
        Path path = Paths.get(fileName);
        GaugesLayout gaugesLayout = new GaugesLayout.Builder()
                .path(path)
                .capacity(8192)
                .mode(CREATE_READ_WRITE)
                .build();

        LongConsumer writer1 = gaugesLayout.supplyWriter(11L, 42L);
        LongConsumer writer2 = gaugesLayout.supplyWriter(22L, 77L);
        LongConsumer writer3 = gaugesLayout.supplyWriter(33L, 88L);

        LongSupplier reader1 = gaugesLayout.supplyReader(11L, 42L);
        LongSupplier reader2 = gaugesLayout.supplyReader(22L, 77L);
        LongSupplier reader3 = gaugesLayout.supplyReader(33L, 88L);

        assertThat(reader1.getAsLong(), equalTo(0L)); // should be 0L initially
        writer1.accept(1L);
        assertThat(reader1.getAsLong(), equalTo(1L));
        writer1.accept(-1L);
        writer2.accept(1L);
        writer3.accept(1L);
        writer3.accept(1L);
        writer3.accept(1L);
        assertThat(reader1.getAsLong(), equalTo(0L));
        assertThat(reader2.getAsLong(), equalTo(1L));
        assertThat(reader3.getAsLong(), equalTo(3L));
        writer2.accept(10L);
        writer3.accept(1L);
        writer2.accept(20L);
        writer3.accept(-1L);
        writer3.accept(-1L);
        assertThat(reader2.getAsLong(), equalTo(31L)); // 1 + 10 + 20
        assertThat(reader3.getAsLong(), equalTo(2L)); // 1 + 1 + 1 - 1 - 1
        writer3.accept(-11L);
        assertThat(reader3.getAsLong(), equalTo(0L)); // should enforce non-negative values

        gaugesLayout.close();
        assertTrue(Files.exists(path));
        Files.delete(path);
    }

    @Test
    public void shouldThrowExceptionIfBufferIsTooSmall() throws Exception
    {
        String fileName = "target/zilla-itests/gauges1";
        Path path = Paths.get(fileName);
        GaugesLayout gaugesLayout = new GaugesLayout.Builder()
                .path(path)
                .capacity(71) // we'd need 72 bytes here for the 3 records
                .mode(CREATE_READ_WRITE)
                .build();

        gaugesLayout.supplyWriter(11L, 42L);
        gaugesLayout.supplyWriter(22L, 77L);
        assertThrows(IndexOutOfBoundsException.class, () ->
        {
            gaugesLayout.supplyWriter(33L, 88L);
        });

        gaugesLayout.close();
        assertTrue(Files.exists(path));
        Files.delete(path);
    }

    @Test
    public void shouldGetIds()
    {
        String fileName = "target/zilla-itests/gauges2";
        Path path = Paths.get(fileName);
        GaugesLayout gaugesLayout = new GaugesLayout.Builder()
                .path(path)
                .capacity(8192)
                .mode(CREATE_READ_WRITE)
                .build();

        gaugesLayout.supplyWriter(11L, 42L);
        gaugesLayout.supplyWriter(22L, 77L);
        gaugesLayout.supplyWriter(33L, 88L);
        long[][] expectedIds = new long[][]{{11L, 42L}, {22L, 77L}, {33L, 88L}};

        assertThat(gaugesLayout.getIds(), equalTo(expectedIds));
    }
}
