package io.aklivity.zilla.runtime.engine.internal.layouts.metrics;

import static org.agrona.IoUtil.createEmptyFile;
import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.agrona.CloseHelper;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.internal.layouts.Layout;

public final class HistogramsLayout extends Layout
{
    private final AtomicBuffer buffer;

    private HistogramsLayout(
        AtomicBuffer buffer)
    {
        this.buffer = buffer;
    }

    @Override
    public void close()
    {
        unmap(buffer.byteBuffer());
    }

    public LongConsumer supplyWriter(
        long bindingId,
        long metricId)
    {
        // TODO: Ati
        return value ->
        {
            System.out.format("HistogramLayout writing: b=%d m=%d v=%d\n", bindingId, metricId, value);
        };
    }

    public LongSupplier supplyReader(
        long bindingId,
        long metricId)
    {
        // TODO: Ati
        return () -> 0L;
    }

    public static final class Builder extends Layout.Builder<HistogramsLayout>
    {
        private long capacity;
        private Path path;

        public Builder capacity(
            long capacity)
        {
            this.capacity = capacity;
            return this;
        }

        public Builder path(
            Path path)
        {
            this.path = path;
            return this;
        }

        @Override
        public HistogramsLayout build()
        {
            final File layoutFile = path.toFile();
            CloseHelper.close(createEmptyFile(layoutFile, capacity));
            final MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, "histograms");
            final AtomicBuffer atomicBuffer = new UnsafeBuffer(mappedBuffer);
            return new HistogramsLayout(atomicBuffer);
        }
    }
}
