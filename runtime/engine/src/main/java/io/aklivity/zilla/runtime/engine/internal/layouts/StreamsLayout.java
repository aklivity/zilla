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
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

import io.aklivity.zilla.runtime.engine.internal.concurent.ManyToOneRingBuffer;

public final class StreamsLayout extends Layout
{
    private final RingBuffer streamsBuffer;

    private StreamsLayout(
        RingBuffer streamsBuffer)
    {
        this.streamsBuffer = streamsBuffer;
    }

    public RingBuffer streamsBuffer()
    {
        return streamsBuffer;
    }

    @Override
    public void close()
    {
        unmap(streamsBuffer.buffer().byteBuffer());
    }

    @Override
    public String toString()
    {
        final long head = streamsBuffer.consumerPosition();
        final long tail = streamsBuffer.producerPosition();
        final int capacity = streamsBuffer.capacity();
        final int mask = capacity - 1;
        final int headIndex = (int) (head & mask);
        final int tailIndex = (int) (tail & mask);

        return String.format("streams=[consumeAt=0x%08x (0x%016x), produceAt=0x%08x (0x%016x)]",
                headIndex, head, tailIndex, tail);
    }

    public static final class Builder extends Layout.Builder<StreamsLayout>
    {
        private long streamsCapacity;
        private Path path;
        private boolean readonly;

        public Builder streamsCapacity(
            long streamsCapacity)
        {
            this.streamsCapacity = streamsCapacity;
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
        public StreamsLayout build()
        {
            final File layoutFile = path.toFile();

            if (!readonly)
            {
                CloseHelper.close(createEmptyFile(layoutFile, streamsCapacity + RingBufferDescriptor.TRAILER_LENGTH));
            }

            final MappedByteBuffer mappedStreams = mapExistingFile(layoutFile, "streams");

            final AtomicBuffer atomicStreams = new UnsafeBuffer(mappedStreams);

            return new StreamsLayout(new ManyToOneRingBuffer(atomicStreams));
        }
    }
}
