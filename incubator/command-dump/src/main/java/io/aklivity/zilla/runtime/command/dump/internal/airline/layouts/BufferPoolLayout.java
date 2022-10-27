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
package io.aklivity.zilla.runtime.command.dump.internal.airline.layouts;

import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;

import io.aklivity.zilla.runtime.engine.internal.buffer.DefaultBufferPool;

public final class BufferPoolLayout extends Layout
{
    private final DefaultBufferPool bufferPool;

    private BufferPoolLayout(
        DefaultBufferPool bufferPool)
    {
        this.bufferPool = bufferPool;
    }

    public DefaultBufferPool bufferPool()
    {
        return bufferPool;
    }

    @Override
    public void close()
    {
        unmap(bufferPool.poolBuffer().byteBuffer());
    }

    public static final class Builder extends Layout.Builder<BufferPoolLayout>
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

        @Override
        public BufferPoolLayout build()
        {
            final File layoutFile = path.toFile();

            assert readonly;

            MappedByteBuffer metadata = mapExistingFile(layoutFile, "metadata");
            slotCount = metadata.getInt(metadata.capacity() - Integer.BYTES);
            unmap(metadata);

            slotCapacity = (metadata.capacity() - Integer.BYTES) / slotCount - Long.BYTES;

            final MappedByteBuffer mapped = mapExistingFile(layoutFile, "bufferPool");

            return new BufferPoolLayout(new DefaultBufferPool(slotCapacity, slotCount, mapped));
        }
    }
}
