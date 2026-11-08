/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.DELETE_ON_CLOSE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

// Holds the frames of a request as they leave the guardrail chain, so that the request can be sent again to
// another exit. A request stays in memory up to a threshold and then moves to a file that has no directory
// entry once opened, so it is freed when the channel closes or the process ends. Reservations against the
// shared budget are released on close. A record appended while every earlier record has been relayed is itself
// relayed, so records remain after the position only while the request is being sent again from the start.
final class LlmRequestRetention
{
    static final int KIND_DATA = 0;
    static final int KIND_FLUSH = 1;
    static final int KIND_NONE = -1;

    private static final int HEADER_SIZE = 16;
    private static final int KIND_OFFSET = 0;
    private static final int FLAGS_OFFSET = 4;
    private static final int PAYLOAD_LENGTH_OFFSET = 8;
    private static final int EXTENSION_LENGTH_OFFSET = 12;

    private final LlmRetentionBudget budget;
    private final Path directory;
    private final int requestMax;
    private final int memoryMax;
    private final ByteBuffer io;
    private final MutableDirectBufferEx ioBuffer;
    private final ExpandableArrayBufferEx header = new ExpandableArrayBufferEx(HEADER_SIZE);

    private ExpandableArrayBufferEx memory;
    private FileChannel file;
    private long size;
    private int lastKind = KIND_NONE;
    private long memoryReserved;
    private long diskReserved;

    private long recordPosition;
    private int recordKind;
    private int recordFlags;
    private int recordPayloadLength;
    private int recordExtensionLength;
    private int recordPayloadRead;

    LlmRequestRetention(
        LlmRetentionBudget budget,
        Path directory,
        int requestMax,
        int memoryMax,
        ByteBuffer io,
        MutableDirectBufferEx ioBuffer)
    {
        this.budget = budget;
        this.directory = directory;
        this.requestMax = requestMax;
        this.memoryMax = memoryMax;
        this.io = io;
        this.ioBuffer = ioBuffer;
    }

    boolean append(
        int kind,
        int flags,
        DirectBufferEx payload,
        DirectBufferEx extension)
    {
        final int payloadLength = payload.capacity();
        final int extensionLength = extension.capacity();
        final long recordSize = HEADER_SIZE + payloadLength + extensionLength;
        final long newSize = size + recordSize;
        final boolean relayed = recordPosition == size;

        boolean retained = newSize <= requestMax;

        if (retained && file == null && newSize <= memoryMax)
        {
            retained = budget.reserveMemory(recordSize);
            if (retained)
            {
                memoryReserved += recordSize;
                if (memory == null)
                {
                    memory = new ExpandableArrayBufferEx((int) Math.min(newSize, memoryMax));
                }
            }
        }
        else if (retained)
        {
            retained = spill(recordSize);
        }

        if (retained)
        {
            retained = write(kind, flags, payload, extension, recordSize);
        }

        if (retained && relayed)
        {
            this.recordPosition = size;
        }

        if (!retained)
        {
            close();
        }

        return retained;
    }

    int lastKind()
    {
        return lastKind;
    }

    void rewind()
    {
        this.recordPosition = 0L;
        loadRecord();
    }

    boolean hasRecord()
    {
        return recordPosition < size;
    }

    int kind()
    {
        return recordKind;
    }

    int flags()
    {
        return recordFlags;
    }

    int payloadLength()
    {
        return recordPayloadLength;
    }

    int payloadRemaining()
    {
        return recordPayloadLength - recordPayloadRead;
    }

    int readPayload(
        MutableDirectBufferEx target,
        int offset,
        int max)
    {
        final int length = Math.min(max, payloadRemaining());
        readAt(recordPosition + HEADER_SIZE + recordPayloadRead, target, offset, length);
        this.recordPayloadRead += length;
        return length;
    }

    int readExtension(
        MutableDirectBufferEx target,
        int offset)
    {
        readAt(recordPosition + HEADER_SIZE + recordPayloadLength, target, offset, recordExtensionLength);
        return recordExtensionLength;
    }

    void advance()
    {
        this.recordPosition += HEADER_SIZE + recordPayloadLength + recordExtensionLength;
        loadRecord();
    }

    void close()
    {
        if (memoryReserved != 0L)
        {
            budget.releaseMemory(memoryReserved);
            this.memoryReserved = 0L;
        }
        this.memory = null;

        if (file != null)
        {
            try
            {
                file.close();
            }
            catch (IOException ex)
            {
                // the file has no directory entry, nothing further to release
            }
            this.file = null;
        }

        if (diskReserved != 0L)
        {
            budget.releaseDisk(diskReserved);
            this.diskReserved = 0L;
        }

        this.size = 0L;
        this.lastKind = KIND_NONE;
        this.recordPosition = 0L;
    }

    private boolean spill(
        long recordSize)
    {
        boolean spilled = file != null;

        if (!spilled)
        {
            spilled = openFile() && moveMemoryToFile();
        }

        if (spilled)
        {
            spilled = budget.reserveDisk(recordSize);
            if (spilled)
            {
                diskReserved += recordSize;
            }
        }

        return spilled;
    }

    private boolean openFile()
    {
        boolean opened;

        try
        {
            Files.createDirectories(directory);
            this.file = FileChannel.open(directory.resolve(String.format("retained-%s", UUID.randomUUID())),
                CREATE_NEW, READ, WRITE, DELETE_ON_CLOSE);
            opened = true;
        }
        catch (IOException ex)
        {
            opened = false;
        }

        return opened;
    }

    private boolean moveMemoryToFile()
    {
        boolean moved = size == 0L || budget.reserveDisk(size);

        if (moved && size != 0L)
        {
            diskReserved += size;
            moved = writeFile(0L, memory, 0, (int) size);
        }

        if (moved && memoryReserved != 0L)
        {
            budget.releaseMemory(memoryReserved);
            this.memoryReserved = 0L;
            this.memory = null;
        }

        return moved;
    }

    private boolean write(
        int kind,
        int flags,
        DirectBufferEx payload,
        DirectBufferEx extension,
        long recordSize)
    {
        header.putInt(KIND_OFFSET, kind);
        header.putInt(FLAGS_OFFSET, flags);
        header.putInt(PAYLOAD_LENGTH_OFFSET, payload.capacity());
        header.putInt(EXTENSION_LENGTH_OFFSET, extension.capacity());

        final long position = size;
        boolean written = true;

        if (file == null)
        {
            memory.putBytes((int) position, header, 0, HEADER_SIZE);
            memory.putBytes((int) position + HEADER_SIZE, payload, 0, payload.capacity());
            memory.putBytes((int) position + HEADER_SIZE + payload.capacity(), extension, 0, extension.capacity());
        }
        else
        {
            written = writeFile(position, header, 0, HEADER_SIZE) &&
                writeFile(position + HEADER_SIZE, payload, 0, payload.capacity()) &&
                writeFile(position + HEADER_SIZE + payload.capacity(), extension, 0, extension.capacity());
        }

        if (written)
        {
            this.size += recordSize;
            this.lastKind = kind;
        }

        return written;
    }

    private boolean writeFile(
        long position,
        DirectBufferEx source,
        int offset,
        int length)
    {
        boolean written = true;

        try
        {
            int progress = 0;
            while (progress < length)
            {
                final int chunk = Math.min(io.capacity(), length - progress);
                ioBuffer.putBytes(0, source, offset + progress, chunk);
                io.clear().limit(chunk);
                long filePosition = position + progress;
                while (io.hasRemaining())
                {
                    filePosition += file.write(io, filePosition);
                }
                progress += chunk;
            }
        }
        catch (IOException ex)
        {
            written = false;
        }

        return written;
    }

    private void readAt(
        long position,
        MutableDirectBufferEx target,
        int offset,
        int length)
    {
        if (file == null)
        {
            target.putBytes(offset, memory, (int) position, length);
        }
        else
        {
            try
            {
                int progress = 0;
                while (progress < length)
                {
                    final int chunk = Math.min(io.capacity(), length - progress);
                    io.clear().limit(chunk);
                    long filePosition = position + progress;
                    while (io.hasRemaining())
                    {
                        filePosition += file.read(io, filePosition);
                    }
                    target.putBytes(offset + progress, ioBuffer, 0, chunk);
                    progress += chunk;
                }
            }
            catch (IOException ex)
            {
                throw new IllegalStateException(ex);
            }
        }
    }

    private void loadRecord()
    {
        this.recordPayloadRead = 0;

        if (recordPosition < size)
        {
            readAt(recordPosition, header, 0, HEADER_SIZE);
            this.recordKind = header.getInt(KIND_OFFSET);
            this.recordFlags = header.getInt(FLAGS_OFFSET);
            this.recordPayloadLength = header.getInt(PAYLOAD_LENGTH_OFFSET);
            this.recordExtensionLength = header.getInt(EXTENSION_LENGTH_OFFSET);
        }
        else
        {
            this.recordPayloadLength = 0;
            this.recordExtensionLength = 0;
        }
    }
}
