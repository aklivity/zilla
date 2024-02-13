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
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.spy.OneToOneRingBufferSpy;
import io.aklivity.zilla.runtime.engine.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.engine.spy.RingBufferSpy.SpyPosition;

public final class EventsLayout implements AutoCloseable
{
    private final Path path;
    private final long capacity;

    private RingBuffer buffer;
    private RingBufferSpy bufferSpy;
    private SpyPosition position;

    private EventsLayout(
        Path path,
        long capacity,
        RingBuffer buffer)
    {
        this.buffer = buffer;
        this.capacity = capacity;
        this.path = path;
    }

    public MessageConsumer supplyWriter()
    {
        return this::writeEvent;
    }

    public void spyAt(
        SpyPosition position)
    {
        this.position = position;
        bufferSpy = createRingBufferSpy(path, position);
    }

    public RingBufferSpy bufferSpy()
    {
        return bufferSpy;
    }

    @Override
    public void close()
    {
        unmap(buffer.buffer().byteBuffer());
    }

    private void writeEvent(
        int msgTypeId,
        DirectBuffer recordBuffer,
        int index,
        int length)
    {
        boolean success = buffer.write(msgTypeId, recordBuffer, index, length);
        if (!success)
        {
            rotateFile();
            buffer.write(msgTypeId, recordBuffer, index, length);
        }
    }

    private void rotateFile()
    {
        close();
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        Path newPath = Path.of(String.format("%s_%s", path, timestamp));
        try
        {
            Files.move(path, newPath, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            rethrowUnchecked(ex);
        }
        buffer = createRingBuffer(path, capacity, false);
        if (position != null)
        {
            bufferSpy = createRingBufferSpy(path, position);
        }
    }

    private static AtomicBuffer createAtomicBuffer(
        Path path,
        long capacity,
        boolean readonly)
    {
        final File layoutFile = path.toFile();
        if (!readonly)
        {
            CloseHelper.close(createEmptyFile(layoutFile, capacity + RingBufferDescriptor.TRAILER_LENGTH));
        }
        final MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, "events");
        return new UnsafeBuffer(mappedBuffer);
    }

    private static RingBuffer createRingBuffer(
        Path path,
        long capacity,
        boolean readonly)
    {
        AtomicBuffer atomicBuffer = createAtomicBuffer(path, capacity, readonly);
        return new OneToOneRingBuffer(atomicBuffer);
    }

    private static RingBufferSpy createRingBufferSpy(
        Path path,
        SpyPosition position)
    {
        AtomicBuffer atomicBuffer = createAtomicBuffer(path, 0, true);
        final OneToOneRingBufferSpy spy = new OneToOneRingBufferSpy(atomicBuffer);
        if (position != null)
        {
            spy.spyAt(position);
        }
        return spy;
    }

    public static final class Builder
    {
        private long capacity;
        private Path path;
        private boolean readonly;

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

        public Builder readonly(
            boolean readonly)
        {
            this.readonly = readonly;
            return this;
        }

        public EventsLayout build()
        {
            RingBuffer ringBuffer = createRingBuffer(path, capacity, readonly);
            return new EventsLayout(path, capacity, ringBuffer);
        }
    }
}
