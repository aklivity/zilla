package io.aklivity.zilla.runtime.engine.internal.layout.metrics;

import static io.aklivity.zilla.runtime.engine.internal.layouts.Layout.Mode.CREATE_READ_WRITE;
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

import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.CountersLayout;

public class CountersLayoutTest
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

        LongConsumer writer1 = countersLayout.supplyWriter(11L, 42L);
        LongConsumer writer2 = countersLayout.supplyWriter(22L, 77L);
        LongConsumer writer3 = countersLayout.supplyWriter(33L, 88L);

        LongSupplier reader1 = countersLayout.supplyReader(11L, 42L);
        LongSupplier reader2 = countersLayout.supplyReader(22L, 77L);
        LongSupplier reader3 = countersLayout.supplyReader(33L, 88L);

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
    public void shouldThrowExceptionIfBufferIsTooSmall() throws Exception
    {
        String fileName = "target/zilla-itests/counters1";
        Path path = Paths.get(fileName);
        CountersLayout countersLayout = new CountersLayout.Builder()
                .path(path)
                .capacity(71) // we'd need 72 bytes here for the 3 records
                .mode(CREATE_READ_WRITE)
                .build();

        countersLayout.supplyWriter(11L, 42L);
        countersLayout.supplyWriter(22L, 77L);
        assertThrows(IndexOutOfBoundsException.class, () ->
        {
            countersLayout.supplyWriter(33L, 88L);
        });

        countersLayout.close();
        assertTrue(Files.exists(path));
        Files.delete(path);
    }

    @Test
    public void shouldGetIds()
    {
        String fileName = "target/zilla-itests/counters2";
        Path path = Paths.get(fileName);
        CountersLayout countersLayout = new CountersLayout.Builder()
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
