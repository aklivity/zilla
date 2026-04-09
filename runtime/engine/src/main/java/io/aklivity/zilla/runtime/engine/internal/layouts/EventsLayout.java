/*
 * Copyright 2021-2024 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.engine.internal.spy.RingBufferSpy.SpyPosition.ZERO;
import static org.agrona.IoUtil.createEmptyFile;
import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;

import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

import io.aklivity.zilla.runtime.engine.internal.concurent.SafeBuffer;
import io.aklivity.zilla.runtime.engine.internal.spy.OneToOneRingBufferSpy;
import io.aklivity.zilla.runtime.engine.internal.spy.RingBufferSpy;

public final class EventsLayout implements AutoCloseable
{
    private final Path path;
    private final RingBuffer buffer;

    private EventsLayout(
        Path path,
        RingBuffer buffer)
    {
        this.path = path;
        this.buffer = buffer;
    }

    public Path path()
    {
        return path;
    }

    public boolean writeEvent(
        int msgTypeId,
        DirectBuffer recordBuffer,
        int index,
        int length)
    {
        return buffer.write(msgTypeId, recordBuffer, index, length);
    }

    public RingBufferSpy createSpy()
    {
        final MappedByteBuffer mappedBuffer = mapExistingFile(path.toFile(), "events");
        final AtomicBuffer atomicBuffer = new SafeBuffer(mappedBuffer);
        final OneToOneRingBufferSpy spy = new OneToOneRingBufferSpy(atomicBuffer);
        spy.spyAt(ZERO);
        return spy;
    }

    @Override
    public void close()
    {
        unmap(buffer.buffer().byteBuffer());
    }

    public static final class Builder
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

        public EventsLayout build()
        {
            final File layoutFile = path.toFile();
            CloseHelper.close(createEmptyFile(layoutFile, capacity + RingBufferDescriptor.TRAILER_LENGTH));
            final MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, "events");
            final AtomicBuffer atomicBuffer = new SafeBuffer(mappedBuffer);
            final RingBuffer ringBuffer = new OneToOneRingBuffer(atomicBuffer);
            return new EventsLayout(path, ringBuffer);
        }
    }
}
