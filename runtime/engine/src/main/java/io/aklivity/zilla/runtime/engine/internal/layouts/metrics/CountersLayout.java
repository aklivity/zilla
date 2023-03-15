package io.aklivity.zilla.runtime.engine.internal.layouts.metrics;

import static org.agrona.IoUtil.createEmptyFile;
import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.agrona.BitUtil;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.internal.layouts.Layout;

public final class CountersLayout extends Layout
{
    // We use the buffer to store structs {long bindingId, long metricId, long value}
    private static final int FIELD_SIZE = BitUtil.SIZE_OF_LONG;
    private static final int RECORD_SIZE = 3 * FIELD_SIZE;
    private static final int BINDING_ID_OFFSET = 0;
    private static final int METRIC_ID_OFFSET = 1 * FIELD_SIZE;
    private static final int VALUE_OFFSET = 2 * FIELD_SIZE;

    private final AtomicBuffer buffer;

    private CountersLayout(
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
        int index = findOrSetPosition(bindingId, metricId);
        return delta -> buffer.getAndAddLong(index + VALUE_OFFSET, delta);
    }

    public LongSupplier supplyReader(
        long bindingId,
        long metricId)
    {
        int index = findPosition(bindingId, metricId);
        LongSupplier reader;
        if (index == -1) // not found
        {
            reader = () -> 0L;
        }
        else
        {
            reader = () -> buffer.getLong(index + VALUE_OFFSET);
        }
        return reader;
    }

    private int findPosition(
        long bindingId,
        long metricId)
    {
        // find position or return -1 if not found
        return findPosition(bindingId, metricId, false);
    }

    private int findOrSetPosition(
        long bindingId,
        long metricId)
    {
        // find position or create slot if not found
        return findPosition(bindingId, metricId, true);
    }

    private int findPosition(
        long bindingId,
        long metricId,
        boolean create)
    {
        int i = 0;
        boolean done = false;
        while (!done)
        {
            long b = buffer.getLong(i + BINDING_ID_OFFSET);
            long m = buffer.getLong(i + METRIC_ID_OFFSET);
            if (b == bindingId && m == metricId)
            {
                done = true;
            }
            else if (b == 0L && m == 0L)
            {
                // we reached and empty slot, which means we did not find the proper record
                if (create)
                {
                    // let's create it
                    buffer.putLong(i + BINDING_ID_OFFSET, bindingId);
                    buffer.putLong(i + METRIC_ID_OFFSET, metricId);
                    buffer.putLong(i + VALUE_OFFSET, 0L); // initial value
                }
                else
                {
                    // let's return the error code
                    i = -1;
                }
                done = true;
            }
            else
            {
                i += RECORD_SIZE;
            }
        }
        return i;
    }

    public static final class Builder extends Layout.Builder<CountersLayout>
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
        public CountersLayout build()
        {
            final File layoutFile = path.toFile();
            CloseHelper.close(createEmptyFile(layoutFile, capacity));
            final MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, "counters");
            final AtomicBuffer atomicBuffer = new UnsafeBuffer(mappedBuffer);
            return new CountersLayout(atomicBuffer);
        }
    }
}
