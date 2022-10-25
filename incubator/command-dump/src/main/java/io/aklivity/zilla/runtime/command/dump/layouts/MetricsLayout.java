/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.command.dump.layouts;

import static org.agrona.BitUtil.align;
import static org.agrona.IoUtil.createEmptyFile;
import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;

import org.agrona.BitUtil;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class MetricsLayout extends Layout
{
    private static final int METRICS_VERSION = 1;

    private static final int FIELD_OFFSET_VERSION = 0;
    private static final int FIELD_SIZE_VERSION = BitUtil.SIZE_OF_INT;

    private static final int FIELD_OFFSET_LABELS_BUFFER_LENGTH = FIELD_OFFSET_VERSION + FIELD_SIZE_VERSION;
    private static final int FIELD_SIZE_COUNTER_LABELS_BUFFER_LENGTH = BitUtil.SIZE_OF_INT;

    private static final int FIELD_OFFSET_VALUES_BUFFER_LENGTH =
            FIELD_OFFSET_LABELS_BUFFER_LENGTH + FIELD_SIZE_COUNTER_LABELS_BUFFER_LENGTH;
    private static final int FIELD_SIZE_COUNTER_VALUES_BUFFER_LENGTH = BitUtil.SIZE_OF_INT;

    private static final int END_OF_META_DATA_OFFSET = align(
            FIELD_OFFSET_VALUES_BUFFER_LENGTH + FIELD_SIZE_COUNTER_VALUES_BUFFER_LENGTH, BitUtil.CACHE_LINE_LENGTH);

    private final AtomicBuffer labelsBuffer = new UnsafeBuffer(new byte[0]);
    private final AtomicBuffer valuesBuffer = new UnsafeBuffer(new byte[0]);


    public AtomicBuffer labelsBuffer()
    {
        return labelsBuffer;
    }

    public AtomicBuffer valuesBuffer()
    {
        return valuesBuffer;
    }

    @Override
    public void close()
    {
        unmap(labelsBuffer.byteBuffer());
        unmap(valuesBuffer.byteBuffer());
    }

    public static final class Builder extends Layout.Builder<MetricsLayout>
    {
        private final MetricsLayout layout;

        private Path path;
        private int labelsBufferCapacity;
        private int valuesBufferCapacity;
        private boolean readonly;

        public Builder()
        {
            this.layout = new MetricsLayout();
        }

        public Builder path(Path path)
        {
            this.path = path;
            return this;
        }

        public Path controlPath()
        {
            return path;
        }

        public Builder labelsBufferCapacity(int labelsBufferCapacity)
        {
            this.labelsBufferCapacity = labelsBufferCapacity;
            return this;
        }

        public Builder valuesBufferCapacity(int valuesBufferCapacity)
        {
            this.valuesBufferCapacity = valuesBufferCapacity;
            return this;
        }

        public Builder readonly(boolean readonly)
        {
            this.readonly = readonly;
            return this;
        }

        @Override
        public MetricsLayout build()
        {
            File metricsFile = path.toFile();
            if (!readonly)
            {
                int labelsBufferLength = labelsBufferCapacity;
                int valuesBufferLength = valuesBufferCapacity;

                CloseHelper.close(createEmptyFile(metricsFile, END_OF_META_DATA_OFFSET +
                        labelsBufferLength + valuesBufferLength));

                MappedByteBuffer metadata = mapExistingFile(metricsFile, "metadata", 0, END_OF_META_DATA_OFFSET);
                metadata.putInt(FIELD_OFFSET_VERSION, METRICS_VERSION);
                metadata.putInt(FIELD_OFFSET_LABELS_BUFFER_LENGTH, labelsBufferCapacity);
                metadata.putInt(FIELD_OFFSET_VALUES_BUFFER_LENGTH, valuesBufferCapacity);
                unmap(metadata);
            }
            else
            {
                MappedByteBuffer metadata = mapExistingFile(metricsFile, "metadata", 0, END_OF_META_DATA_OFFSET);
                assert METRICS_VERSION == metadata.getInt(FIELD_OFFSET_VERSION);
                labelsBufferCapacity = metadata.getInt(FIELD_OFFSET_LABELS_BUFFER_LENGTH);
                valuesBufferCapacity = metadata.getInt(FIELD_OFFSET_VALUES_BUFFER_LENGTH);
                unmap(metadata);
            }

            int labelsBufferOffset = END_OF_META_DATA_OFFSET;
            int labelsBufferLength = labelsBufferCapacity;
            layout.labelsBuffer.wrap(mapExistingFile(metricsFile, "labels", labelsBufferOffset, labelsBufferLength));

            int valuesBufferOffset = labelsBufferOffset + labelsBufferLength;
            int valuesBufferLength = valuesBufferCapacity;
            layout.valuesBuffer.wrap(mapExistingFile(metricsFile, "values", valuesBufferOffset, valuesBufferLength));

            return layout;
        }
    }
}
