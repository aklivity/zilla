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

import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.exporter.stdout.internal.spy.OneToOneRingBufferSpy;
import io.aklivity.zilla.runtime.exporter.stdout.internal.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.exporter.stdout.internal.spy.RingBufferSpy.SpyPosition;

public final class EventsLayoutReader implements AutoCloseable
{
    private final Path path;
    private final SpyPosition position;
    private RingBufferSpy buffer;

    private EventsLayoutReader(
        Path path,
        SpyPosition position,
        RingBufferSpy buffer)
    {
        this.path = path;
        this.position = position;
        this.buffer = buffer;
        Thread watcher = new Thread(this::watch);
        watcher.start();
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

    private void watch()
    {
        try
        {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            path.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            while (true)
            {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents())
                {
                    Path changedPath = (Path) event.context();
                    if (path.getFileName().equals(changedPath.getFileName()))
                    {
                        close();
                        buffer = createRingBufferSpy(path, position);
                    }
                }
                key.reset();
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            rethrowUnchecked(ex);
        }
    }

    private static RingBufferSpy createRingBufferSpy(
        Path path,
        SpyPosition position)
    {
        final File layoutFile = path.toFile();
        final MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, "events");
        final AtomicBuffer atomicBuffer = new UnsafeBuffer(mappedBuffer);
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

        public EventsLayoutReader build()
        {
            RingBufferSpy spy = createRingBufferSpy(path, position);
            return new EventsLayoutReader(path, position, spy);
        }
    }
}
