/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.layouts.metrics;

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
        GaugesLayout layout = new GaugesLayout.Builder()
                .path(path)
                .capacity(8192)
                .readonly(false)
                .label("counters")
                .build();

        LongConsumer writer1 = layout.supplyWriter(11L, 42L);
        LongConsumer writer2 = layout.supplyWriter(22L, 77L);
        LongConsumer writer3 = layout.supplyWriter(33L, 88L);

        LongSupplier reader1 = layout.supplyReader(11L, 42L);
        LongSupplier reader2 = layout.supplyReader(22L, 77L);
        LongSupplier reader3 = layout.supplyReader(33L, 88L);

        assertThat(reader1.getAsLong(), equalTo(0L)); // should be 0L initially
        writer1.accept(1L);
        assertThat(reader1.getAsLong(), equalTo(1L));
        writer1.accept(1L);
        writer2.accept(100L);
        writer3.accept(77L);
        assertThat(reader1.getAsLong(), equalTo(1L));
        assertThat(reader2.getAsLong(), equalTo(100L));
        assertThat(reader3.getAsLong(), equalTo(77L));
        writer2.accept(10L);
        writer3.accept(1L);
        writer2.accept(20L);
        writer3.accept(1L);
        writer3.accept(1L);
        assertThat(reader2.getAsLong(), equalTo(20L));
        assertThat(reader3.getAsLong(), equalTo(1L));

        layout.close();
        assertTrue(Files.exists(path));
        Files.delete(path);
    }

    @Test
    public void shouldThrowExceptionIfBufferIsTooSmall() throws Exception
    {
        String fileName = "target/zilla-itests/gauges1";
        Path path = Paths.get(fileName);
        GaugesLayout layout = new GaugesLayout.Builder()
                .path(path)
                .capacity(71) // we'd need 72 bytes here for the 3 records
                .readonly(false)
                .label("counters")
                .build();

        layout.supplyWriter(11L, 42L);
        layout.supplyWriter(22L, 77L);
        assertThrows(IndexOutOfBoundsException.class, () ->
        {
            layout.supplyWriter(33L, 88L);
        });

        layout.close();
        assertTrue(Files.exists(path));
        Files.delete(path);
    }

    @Test
    public void shouldGetIds()
    {
        String fileName = "target/zilla-itests/gauges2";
        Path path = Paths.get(fileName);
        GaugesLayout layout = new GaugesLayout.Builder()
                .path(path)
                .capacity(8192)
                .readonly(false)
                .label("counters")
                .build();

        layout.supplyWriter(11L, 42L);
        layout.supplyWriter(22L, 77L);
        layout.supplyWriter(33L, 88L);
        long[][] expectedIds = new long[][]{{11L, 42L}, {22L, 77L}, {33L, 88L}};

        assertThat(layout.getIds(), equalTo(expectedIds));
    }
}
