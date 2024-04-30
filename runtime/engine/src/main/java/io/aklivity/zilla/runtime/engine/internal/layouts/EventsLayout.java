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

import static io.aklivity.zilla.runtime.engine.internal.spy.RingBufferSpy.SpyPosition.ZERO;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.spy.OneToOneRingBufferSpy;
import io.aklivity.zilla.runtime.engine.internal.spy.RingBufferSpy;

public final class EventsLayout implements AutoCloseable
{
    private final Path path;
    private final long capacity;
    private final List<EventAccessor> accessors;

    private RingBuffer buffer;

    private EventsLayout(
        Path path,
        long capacity,
        RingBuffer buffer)
    {
        this.path = path;
        this.capacity = capacity;
        this.buffer = buffer;
        this.accessors = new ArrayList<>();
    }

    @Override
    public void close()
    {
        unmap(buffer.buffer().byteBuffer());
    }

    public void writeEvent(
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

    public EventAccessor createEventAccessor()
    {
        RingBufferSpy ringBufferSpy = createRingBufferSpy();
        EventAccessor accessor = new EventAccessor(ringBufferSpy);
        accessors.add(accessor);
        return accessor;
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
        buffer = createRingBuffer(path, capacity);
        accessors.forEach(a -> a.addNextBufferSpy(createRingBufferSpy()));
    }

    private RingBufferSpy createRingBufferSpy()
    {
        AtomicBuffer atomicBuffer = createAtomicBuffer(path, 0, false);
        OneToOneRingBufferSpy spy = new OneToOneRingBufferSpy(atomicBuffer);
        spy.spyAt(ZERO);
        return spy;
    }

    private static AtomicBuffer createAtomicBuffer(
        Path path,
        long capacity,
        boolean createFile)
    {
        final File layoutFile = path.toFile();
        if (createFile)
        {
            CloseHelper.close(createEmptyFile(layoutFile, capacity + RingBufferDescriptor.TRAILER_LENGTH));
        }
        final MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, "events");
        return new UnsafeBuffer(mappedBuffer);
    }

    private static RingBuffer createRingBuffer(
        Path path,
        long capacity)
    {
        AtomicBuffer atomicBuffer = createAtomicBuffer(path, capacity, true);
        return new OneToOneRingBuffer(atomicBuffer);
    }

    public static final class EventAccessor
    {
        private final Queue<RingBufferSpy> nextBufferSpies;
        private RingBufferSpy bufferSpy;

        private EventAccessor(
            RingBufferSpy bufferSpy)
        {
            this.nextBufferSpies = new LinkedList<>();
            this.bufferSpy = bufferSpy;
        }

        public int readEvent(
            MessageConsumer handler,
            int messageCountLimit)
        {
            int result = bufferSpy.spy(handler, messageCountLimit);
            if (result == 0 && !nextBufferSpies.isEmpty())
            {
                bufferSpy = nextBufferSpies.poll();
                result = bufferSpy.spy(handler, messageCountLimit);
            }
            return result;
        }

        public int peekEvent(
            MessageConsumer handler)
        {
            int result = bufferSpy.peek(handler);
            if (result == 0 && !nextBufferSpies.isEmpty())
            {
                bufferSpy = nextBufferSpies.poll();
                result = bufferSpy.peek(handler);
            }
            return result;
        }

        private void addNextBufferSpy(
            RingBufferSpy bufferSpy)
        {
            this.nextBufferSpies.add(bufferSpy);
        }
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
            RingBuffer ringBuffer = createRingBuffer(path, capacity);
            return new EventsLayout(path, capacity, ringBuffer);
        }
    }
}
