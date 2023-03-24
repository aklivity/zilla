package io.aklivity.zilla.runtime.engine.internal.layouts.metrics;

import static org.agrona.IoUtil.createEmptyFile;
import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;
import java.util.function.LongConsumer;

import org.agrona.BitUtil;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.internal.layouts.Layout;
import io.aklivity.zilla.runtime.engine.util.function.LongArraySupplier;

public final class HistogramsLayout extends Layout
{
    // We use the buffer to store structs {long bindingId, long metricId, long[] values}
    private static final int FIELD_SIZE = BitUtil.SIZE_OF_LONG;
    private static final int BINDING_ID_OFFSET = 0;
    private static final int METRIC_ID_OFFSET = 1 * FIELD_SIZE;
    private static final int VALUES_OFFSET = 2 * FIELD_SIZE;
    private static final int BUCKETS = 63;
    private static final int ARRAY_SIZE = BUCKETS * FIELD_SIZE;
    private static final int RECORD_SIZE = 2 * FIELD_SIZE + ARRAY_SIZE;
    private static final long[] EMPTY_ARRAY = new long[BUCKETS];

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
        int index = findOrSetPosition(bindingId, metricId);
        return value ->
        {
            System.out.format("HistogramLayout write: bnd=%d met=%d val=%d bck=%d\n",
                    bindingId, metricId, value, findBucket(value)); // TODO: Ati
            buffer.getAndAddLong(index + VALUES_OFFSET + findBucket(value) * FIELD_SIZE, 1);
        };
    }

    // TODO: Ati - supplyReaders -> LongSupplier[]
    public LongArraySupplier supplyReader(
        long bindingId,
        long metricId)
    {
        int index = findPosition(bindingId, metricId);
        LongArraySupplier reader;
        if (index == -1) // not found
        {
            reader = () -> EMPTY_ARRAY;
        }
        else
        {
            reader = () ->
            {
                // TODO: Ati
                ByteBuffer values = ByteBuffer.allocate(ARRAY_SIZE);
                buffer.getBytes(index + VALUES_OFFSET, values, ARRAY_SIZE);
                values.rewind();
                values.order(ByteOrder.nativeOrder());
                long[] array = new long[BUCKETS];
                values.asLongBuffer().get(array);
                System.out.format("HistogramLayout read: bnd=%d met=%d val0=%d\n",
                        bindingId, metricId, array[0]); // TODO: Ati
                return array;
            };
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
                    ByteBuffer initialValues = ByteBuffer.allocate(ARRAY_SIZE); // all zeroes
                    buffer.putBytes(i + VALUES_OFFSET, initialValues.array());
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

    private int findBucket(
        long value)
    {
        assert value >= 0;
        int bucket = 0;
        if (value > 0)
        {
            bucket = Long.SIZE - Long.numberOfLeadingZeros(value) - 1; // TODO: Ati - ?
        }
        return bucket;
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
