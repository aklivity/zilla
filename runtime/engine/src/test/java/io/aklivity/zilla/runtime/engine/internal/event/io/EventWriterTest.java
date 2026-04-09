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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.agrona.DirectBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EventWriterTest
{
    // capacity=64, zero-length events = 8 bytes each (HEADER_LENGTH only).
    // Agrona inserts a padding record when the last 8 bytes exactly fill the buffer,
    // so only 7 events fit before rotation (8th write fails → rotate).
    private static final int CAPACITY = 64;
    private static final int EVENTS_PER_ROTATION = (CAPACITY / 8) - 1;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private int msgTypeId;

    @Test
    public void shouldWriteAndReadEvents() throws Exception
    {
        Path path = tempFolder.getRoot().toPath().resolve("events");

        try (EventWriter writer = new EventWriter(path, CAPACITY))
        {
            writer.writeEvent(42, new SafeBuffer(), 0, 0);
            msgTypeId = 0;
            EventAccessor accessor = writer.createEventAccessor();

            int count = accessor.readEvent(this::readEvent, 1);

            assertThat(count, equalTo(1));
            assertThat(msgTypeId, equalTo(42));
        }
    }

    @Test
    public void shouldRotateWhenBufferFull() throws Exception
    {
        Path dir = tempFolder.getRoot().toPath();
        Path path = dir.resolve("events");

        try (EventWriter writer = new EventWriter(path, CAPACITY))
        {
            EventAccessor accessor = writer.createEventAccessor();

            // fill buffer - 7 events fit, 8th triggers rotation
            for (int i = 1; i <= EVENTS_PER_ROTATION + 1; i++)
            {
                writer.writeEvent(i, new SafeBuffer(), 0, 0);
            }

            // rotated file should now exist alongside the active file
            Optional<Path> rotated = Files.list(dir)
                .filter(p -> p.getFileName().toString().startsWith("events_"))
                .findFirst();
            assertThat(rotated.isPresent(), is(true));
            assertThat(Files.exists(rotated.get()), is(true));

            // read all pre-rotation events from the rotated entry
            int totalRead = 0;
            for (int i = 0; i < EVENTS_PER_ROTATION; i++)
            {
                totalRead += accessor.readEvent((m, b, idx, l) -> {}, 1);
            }
            assertThat(totalRead, equalTo(EVENTS_PER_ROTATION));

            // rotated file still present — accessor has not yet transitioned
            assertThat(Files.exists(rotated.get()), is(true));

            // this read exhausts the rotated entry, transitions to the new active entry,
            // and triggers decrement → 0 → rotated file deleted
            accessor.readEvent((m, b, idx, l) -> {}, 1);

            assertThat(Files.exists(rotated.get()), is(false));
        }
    }

    @Test
    public void shouldKeepRotatedFileUntilLastAccessorDrains() throws Exception
    {
        Path dir = tempFolder.getRoot().toPath();
        Path path = dir.resolve("events");

        try (EventWriter writer = new EventWriter(path, CAPACITY))
        {
            EventAccessor accessor1 = writer.createEventAccessor();
            EventAccessor accessor2 = writer.createEventAccessor();

            // fill buffer and trigger one rotation
            for (int i = 1; i <= EVENTS_PER_ROTATION + 1; i++)
            {
                writer.writeEvent(i, new SafeBuffer(), 0, 0);
            }

            Optional<Path> rotated = Files.list(dir)
                .filter(p -> p.getFileName().toString().startsWith("events_"))
                .findFirst();
            assertThat(rotated.isPresent(), is(true));

            // accessor1 drains pre-rotation events and transitions
            for (int i = 0; i < EVENTS_PER_ROTATION; i++)
            {
                accessor1.readEvent((m, b, idx, l) -> {}, 1);
            }
            accessor1.readEvent((m, b, idx, l) -> {}, 1); // transitions, pendingCount 2→1

            // rotated file still present — accessor2 has not drained it yet
            assertThat(Files.exists(rotated.get()), is(true));

            // accessor2 drains pre-rotation events and transitions
            for (int i = 0; i < EVENTS_PER_ROTATION; i++)
            {
                accessor2.readEvent((m, b, idx, l) -> {}, 1);
            }
            accessor2.readEvent((m, b, idx, l) -> {}, 1); // transitions, pendingCount 1→0 → deleted

            assertThat(Files.exists(rotated.get()), is(false));
        }
    }

    @Test
    public void shouldCleanUpRotatedFilesOnWriterClose() throws Exception
    {
        Path dir = tempFolder.getRoot().toPath();
        Path path = dir.resolve("events");

        EventWriter writer = new EventWriter(path, CAPACITY);
        writer.createEventAccessor();

        // fill buffer and trigger rotation — accessor has not drained the rotated entry
        for (int i = 1; i <= EVENTS_PER_ROTATION + 1; i++)
        {
            writer.writeEvent(i, new SafeBuffer(), 0, 0);
        }

        Optional<Path> rotated = Files.list(dir)
            .filter(p -> p.getFileName().toString().startsWith("events_"))
            .findFirst();
        assertThat(rotated.isPresent(), is(true));

        // close the writer without the accessor draining the rotated entry
        writer.close();

        // rotated file should be cleaned up by EventAccessor.close() traversal via next()
        assertThat(Files.exists(rotated.get()), is(false));
    }

    private void readEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        this.msgTypeId = msgTypeId;
    }
}
