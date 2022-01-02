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
package io.aklivity.zilla.runtime.engine.internal.layouts;

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

public final class LoadLayout extends Layout
{
    private static final int LOAD_VERSION = 1;

    private static final int FIELD_OFFSET_VERSION = 0;
    private static final int FIELD_SIZE_VERSION = BitUtil.SIZE_OF_INT;

    private static final int FIELD_OFFSET_BUFFER_LENGTH = FIELD_OFFSET_VERSION + FIELD_SIZE_VERSION;
    private static final int FIELD_SIZE_COUNTER_LABELS_BUFFER_LENGTH = BitUtil.SIZE_OF_INT;

    private static final int END_OF_META_DATA_OFFSET = align(
            FIELD_OFFSET_BUFFER_LENGTH + FIELD_SIZE_COUNTER_LABELS_BUFFER_LENGTH, BitUtil.CACHE_LINE_LENGTH);

    private final AtomicBuffer buffer = new UnsafeBuffer(0L, 0);


    public AtomicBuffer buffer()
    {
        return buffer;
    }

    @Override
    public void close()
    {
        unmap(buffer.byteBuffer());
    }

    public static final class Builder extends Layout.Builder<LoadLayout>
    {
        private final LoadLayout layout;

        private Path path;
        private int capacity;

        public Builder()
        {
            this.layout = new LoadLayout();
        }

        public Builder path(
            Path path)
        {
            this.path = path;
            return this;
        }

        public Builder capacity(
            int capacity)
        {
            this.capacity = capacity;
            return this;
        }

        @Override
        public LoadLayout build()
        {
            File loadFile = path.toFile();

            CloseHelper.close(createEmptyFile(loadFile, END_OF_META_DATA_OFFSET + capacity));

            MappedByteBuffer metadata = mapExistingFile(loadFile, "metadata", 0, END_OF_META_DATA_OFFSET);
            metadata.putInt(FIELD_OFFSET_VERSION, LOAD_VERSION);
            metadata.putInt(FIELD_OFFSET_BUFFER_LENGTH, capacity);
            unmap(metadata);

            int loadBufferOffset = END_OF_META_DATA_OFFSET;
            int loadBufferLength = capacity;
            layout.buffer.wrap(mapExistingFile(loadFile, "load", loadBufferOffset, loadBufferLength));

            return layout;
        }
    }
}
