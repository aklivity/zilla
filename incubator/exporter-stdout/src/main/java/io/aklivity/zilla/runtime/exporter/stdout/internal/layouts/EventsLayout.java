/*
 * Copyright 2021-2023 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.exporter.stdout.internal.layouts;

import static org.agrona.IoUtil.createEmptyFile;
import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;

import org.agrona.CloseHelper;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

import io.aklivity.zilla.runtime.exporter.stdout.internal.spy.OneToOneRingBufferSpy;
import io.aklivity.zilla.runtime.exporter.stdout.internal.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.exporter.stdout.internal.spy.RingBufferSpy.SpyPosition;

public final class EventsLayout implements AutoCloseable
{
    private final RingBufferSpy buffer;

    private EventsLayout(
        RingBufferSpy buffer)
    {
        this.buffer = buffer;
    }

    public RingBufferSpy eventsBuffer()
    {
        return buffer;
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
        private boolean readonly;
        private SpyPosition position;

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

        public Builder spyAt(
            SpyPosition position)
        {
            this.position = position;
            return this;
        }

        public Builder readonly(
            boolean readonly)
        {
            this.readonly = readonly;
            return this;
        }

        public EventsLayout build()
        {
            final File layoutFile = path.toFile();
            if (!readonly)
            {
                CloseHelper.close(createEmptyFile(layoutFile, capacity + RingBufferDescriptor.TRAILER_LENGTH));
            }
            final MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, "events");
            final AtomicBuffer atomicBuffer = new UnsafeBuffer(mappedBuffer);
            final OneToOneRingBufferSpy spy = new OneToOneRingBufferSpy(atomicBuffer);
            if (position != null)
            {
                spy.spyAt(position);
            }
            return new EventsLayout(spy);
        }
    }
}
