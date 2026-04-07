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
package io.aklivity.zilla.runtime.engine.internal.event.io;

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.internal.layouts.EventsLayout;
import io.aklivity.zilla.runtime.engine.internal.spy.RingBufferSpy;

public final class EventWriter implements AutoCloseable
{
    private final Path basePath;
    private final List<EventAccessor> accessors;
    private final Supplier<String> timestamp;

    private EventWriterEntry activeEntry;

    public EventWriter(
        Path basePath,
        long capacity)
    {
        this.basePath = basePath;
        this.activeEntry = new EventWriterEntry(basePath, capacity);
        this.accessors = new ArrayList<>();

        DateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        this.timestamp = () -> format.format(new Date());
    }

    public void writeEvent(
        int msgTypeId,
        DirectBuffer recordBuffer,
        int index,
        int length)
    {
        if (!activeEntry.writeEvent(msgTypeId, recordBuffer, index, length))
        {
            rotate();
            activeEntry.writeEvent(msgTypeId, recordBuffer, index, length);
        }
    }

    EventAccessor createEventAccessor()
    {
        EventAccessor accessor = new EventAccessor(activeEntry);
        accessors.add(accessor);
        return accessor;
    }

    @Override
    public void close()
    {
        accessors.forEach(EventAccessor::close);
        accessors.clear();
        activeEntry.close();
    }

    private void rotate()
    {
        Path timestampedPath = basePath.resolveSibling("%s_%s".formatted(basePath.getFileName(), timestamp.get()));
        activeEntry = activeEntry.rotateTo(timestampedPath);
    }

    final class EventWriterEntry
    {
        private final long capacity;
        private Path path;
        private EventsLayout layout;
        private int pendingCount;
        private Runnable cleanup = () -> {};

        EventWriterEntry next;
        EventWriterEntry rotated;

        EventWriterEntry(
            Path path,
            long capacity)
        {
            this.path = path;
            this.capacity = capacity;
            this.layout = new EventsLayout.Builder()
                .path(path)
                .capacity(capacity)
                .build();
        }

        boolean writeEvent(
            int msgTypeId,
            DirectBuffer recordBuffer,
            int index,
            int length)
        {
            return layout.writeEvent(msgTypeId, recordBuffer, index, length);
        }

        RingBufferSpy createSpy()
        {
            return layout.createSpy();
        }

        void increment()
        {
            pendingCount++;
        }

        void decrement()
        {
            if (next != null && --pendingCount == 0)
            {
                layout.close();
                delete(path);
                cleanup.run();
            }
        }

        EventWriterEntry rotateTo(
            Path rotatedPath)
        {
            if (accessors.isEmpty())
            {
                layout.close();
                layout = new EventsLayout.Builder()
                    .path(path)
                    .capacity(capacity)
                    .build();
                return this;
            }

            Path originalPath = path;
            try
            {
                Files.move(originalPath, rotatedPath, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException ex)
            {
                rethrowUnchecked(ex);
            }
            this.path = rotatedPath;

            EventWriterEntry newActive = new EventWriterEntry(originalPath, capacity);
            newActive.rotated = this;
            this.cleanup = () -> newActive.rotated = this.rotated;
            this.next = newActive;

            return newActive;
        }

        void close()
        {
            layout.close();
        }
    }

    private static void delete(
        Path path)
    {
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ex)
        {
            rethrowUnchecked(ex);
        }
    }
}
