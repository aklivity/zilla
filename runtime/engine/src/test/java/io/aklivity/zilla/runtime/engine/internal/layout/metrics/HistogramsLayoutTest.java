package io.aklivity.zilla.runtime.engine.internal.layout.metrics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.LongConsumer;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.HistogramsLayout;
import io.aklivity.zilla.runtime.engine.util.function.LongArraySupplier;

public class HistogramsLayoutTest
{
    @Test
    public void shouldWorkWithTheDefinedBucketLimits()
    {
        String fileName = "target/zilla-itests/histograms1";
        Path path = Paths.get(fileName);
        HistogramsLayout histogramsLayout = new HistogramsLayout.Builder()
                .path(path)
                .capacity(8192)
                .build();

        LongConsumer writer1 = histogramsLayout.supplyWriter(11L, 42L);
        LongArraySupplier reader1 = histogramsLayout.supplyReader(11L, 42L);

        // bucket 0 (0L - 1L)
        writer1.accept(0L);
        writer1.accept(1L);
        assertThat(reader1.getAsLongArray()[0], equalTo(2L));

        // bucket 1 (2L - 3L)
        writer1.accept(2L);
        writer1.accept(3L);
        assertThat(reader1.getAsLongArray()[1], equalTo(2L));

        // bucket 2 (4L - 7L)
        writer1.accept(7L);
        assertThat(reader1.getAsLongArray()[2], equalTo(1L));

        // bucket 3 (8L - 15L)
        writer1.accept(15L);
        writer1.accept(15L);
        writer1.accept(15L);
        assertThat(reader1.getAsLongArray()[3], equalTo(3L));

        // bucket 4 (16L - 31L)
        writer1.accept(20L);
        assertThat(reader1.getAsLongArray()[4], equalTo(1L));

        // bucket 5 (32L - 63L)
        writer1.accept(32L);
        writer1.accept(33L);
        writer1.accept(55L);
        writer1.accept(63L);
        assertThat(reader1.getAsLongArray()[5], equalTo(4L));

        // bucket 20 (1_048_576L - 2_097_151L)
        writer1.accept(1_048_576L);
        writer1.accept(1_111_111L);
        writer1.accept(1_111_111L);
        writer1.accept(1_111_111L);
        writer1.accept(2_097_151L);
        assertThat(reader1.getAsLongArray()[20], equalTo(5L));

        // bucket 30 (1_073_741_824L - 2_147_483_647L)
        writer1.accept(1_073_741_824L);
        writer1.accept(2_147_483_647L);
        assertThat(reader1.getAsLongArray()[30], equalTo(2L));

        // bucket 40 (1_099_511_627_776L - 2_199_023_255_551L)
        writer1.accept(1_099_511_627_776L);
        writer1.accept(1_111_111_111_111L);
        writer1.accept(2_199_023_255_551L);
        assertThat(reader1.getAsLongArray()[40], equalTo(3L));

        // bucket 50
        writer1.accept(1L << 50);
        assertThat(reader1.getAsLongArray()[50], equalTo(1L));

        // bucket 60
        long power60 = 1L << 60;
        writer1.accept(power60);
        writer1.accept(power60 + 42);
        writer1.accept(power60 + 77);
        assertThat(reader1.getAsLongArray()[60], equalTo(3L));

        // bucket 61
        writer1.accept(1L << 61);
        assertThat(reader1.getAsLongArray()[61], equalTo(1L));

        // bucket 62
        writer1.accept(1L << 62);
        writer1.accept(Long.MAX_VALUE);
        assertThat(reader1.getAsLongArray()[62], equalTo(2L));
    }

    @Test
    public void shouldWorkInGenericCase() throws Exception
    {
        String fileName = "target/zilla-itests/histograms1";
        Path path = Paths.get(fileName);
        HistogramsLayout histogramsLayout = new HistogramsLayout.Builder()
                .path(path)
                .capacity(8192)
                .build();

        LongConsumer writer1 = histogramsLayout.supplyWriter(11L, 42L);
        LongConsumer writer2 = histogramsLayout.supplyWriter(22L, 77L);
        LongConsumer writer3 = histogramsLayout.supplyWriter(33L, 88L);

        LongArraySupplier reader1 = histogramsLayout.supplyReader(11L, 42L);
        LongArraySupplier reader2 = histogramsLayout.supplyReader(22L, 77L);
        LongArraySupplier reader3 = histogramsLayout.supplyReader(33L, 88L);

        assertThat(reader1.getAsLongArray()[0], equalTo(0L));
        assertThat(reader1.getAsLongArray()[3], equalTo(0L));
        assertThat(reader1.getAsLongArray()[10], equalTo(0L));
        writer1.accept(0L);
        writer1.accept(1L);
        assertThat(reader1.getAsLongArray()[0], equalTo(2L));
        writer1.accept(15L);
        assertThat(reader1.getAsLongArray()[3], equalTo(1L));
        writer1.accept(1_024L);
        assertThat(reader1.getAsLongArray()[10], equalTo(1L));

        writer1.accept(1_111_111L);
        assertThat(reader1.getAsLongArray()[20], equalTo(1L));
        writer2.accept(8_888_888L);
        writer2.accept(8_888_888L);
        writer2.accept(8_888_888L);
        assertThat(reader2.getAsLongArray()[23], equalTo(3L));
        writer3.accept(55_555L);
        assertThat(reader3.getAsLongArray()[15], equalTo(1L));

        writer2.accept(4_000L);
        assertThat(reader2.getAsLongArray()[11], equalTo(1L));
        writer1.accept(1_111L);
        assertThat(reader1.getAsLongArray()[10], equalTo(2L));
        writer3.accept(999_999_999_999L);
        assertThat(reader3.getAsLongArray()[39], equalTo(1L));
        writer2.accept(Long.MAX_VALUE - 42);
        writer2.accept(Long.MAX_VALUE - 1);
        writer2.accept(Long.MAX_VALUE);
        assertThat(reader2.getAsLongArray()[62], equalTo(3L));

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
}
