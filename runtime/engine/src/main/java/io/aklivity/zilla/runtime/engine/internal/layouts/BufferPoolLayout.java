/*
 * Copyright 2021-2023 Aklivity Inc.
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

import static org.agrona.IoUtil.createEmptyFile;
import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;

import org.agrona.CloseHelper;

import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.internal.buffer.DefaultBufferPool;

public final class BufferPoolLayout implements AutoCloseable
{
    private final DefaultBufferPool bufferPool;

    private BufferPoolLayout(
        DefaultBufferPool bufferPool)
    {
        this.bufferPool = bufferPool;
    }

    public BufferPool bufferPool()
    {
        return bufferPool;
    }

    @Override
    public void close()
    {
        unmap(bufferPool.poolBuffer().byteBuffer());
    }

    public static final class Builder
    {
        private int slotCount;
        private int slotCapacity;
        private Path path;
        private boolean readonly;

        public Builder slotCount(
            int slotCount)
        {
            this.slotCount = slotCount;
            return this;
        }

        public Builder slotCapacity(
            int slotCapacity)
        {
            this.slotCapacity = slotCapacity;
            return this;
        }

        public Builder path(
            Path path)
        {
            this.path = path;
            return this;
        }

        public Builder readonly(
            boolean readonly)
        {
            this.readonly = readonly;
            return this;
        }

        public BufferPoolLayout build()
        {
            final File layoutFile = path.toFile();

            if (!readonly)
            {
                final int slotCountIndex = (slotCapacity + Long.BYTES) * slotCount;
                final int totalLength = slotCountIndex + Integer.BYTES;
                CloseHelper.close(createEmptyFile(layoutFile, totalLength));

                MappedByteBuffer metadata = mapExistingFile(layoutFile, "slotCount", totalLength - Integer.BYTES, Integer.BYTES);
                metadata.putInt(0, slotCount);
                unmap(metadata);
            }

            final MappedByteBuffer mapped = mapExistingFile(layoutFile, "bufferPool");

            return new BufferPoolLayout(new DefaultBufferPool(slotCapacity, slotCount, mapped));
        }
    }
}
