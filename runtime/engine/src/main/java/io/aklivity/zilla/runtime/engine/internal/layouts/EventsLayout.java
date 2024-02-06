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
import org.agrona.DirectBuffer;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public final class EventsLayout implements AutoCloseable
{
    private final AtomicBuffer buffer;

    private int position;

    private EventsLayout(
        AtomicBuffer buffer)
    {
        this.buffer = buffer;
        this.position = 0;
    }

    public MessageConsumer supplyWriter()
    {
        return this::writeEvent;
    }

    private void writeEvent(
        int msgTypeId,
        DirectBuffer recordBuffer,
        int index,
        int length)
    {
        System.out.printf("%d %s %d %d%n", msgTypeId, buffer, index, length); // TODO: Ati
        if (position + length > buffer.capacity())
        {
            throw new IllegalStateException("Event buffer is full.");
        }
        buffer.putBytes(position, recordBuffer, index, length);
        position += length;
    }

    @Override
    public void close()
    {
        unmap(buffer.byteBuffer());
    }

    public static final class Builder
    {
        private long capacity;
        private Path path;
        private boolean readonly;

        public Builder streamsCapacity(
            long streamsCapacity)
        {
            this.capacity = streamsCapacity;
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

        public EventsLayout build()
        {
            final File layoutFile = path.toFile();
            if (!readonly)
            {
                CloseHelper.close(createEmptyFile(layoutFile, capacity));
            }
            final MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, "events");
            final AtomicBuffer atomicBuffer = new UnsafeBuffer(mappedBuffer);
            return new EventsLayout(atomicBuffer);
        }
    }
}
