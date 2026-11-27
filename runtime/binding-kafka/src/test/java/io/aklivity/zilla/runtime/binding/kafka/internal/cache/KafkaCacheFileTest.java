/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaCacheFileTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void shouldAppendBytes() throws Exception
    {
        Path location = tempFolder.newFile().toPath();
        int capacity = 1024;
        MutableDirectBufferEx appendBuf = new UnsafeBufferEx(ByteBuffer.allocate(1024));

        try (KafkaCacheFile file = new KafkaCacheFile(location, capacity, appendBuf))
        {
            file.appendBytes(new UnsafeBufferEx("Hello, world".getBytes(UTF_8)));

            byte[] appended = new byte[file.capacity()];
            file.buffer().getBytes(0, appended);
            assertEquals("Hello, world", new String(appended, UTF_8));
        }
    }

    @Test
    public void shouldReserveBytesWithoutAdvancingCapacity() throws Exception
    {
        Path location = tempFolder.newFile().toPath();
        MutableDirectBufferEx appendBuf = new UnsafeBufferEx(ByteBuffer.allocate(1024));

        try (KafkaCacheFile file = new KafkaCacheFile(location, 4096, appendBuf))
        {
            assertTrue(file.reserve(100));
            final int reservedWrites = file.appendCount();

            assertEquals(0, file.capacity());
            assertTrue(file.appendBytes(new UnsafeBufferEx("Hello, world".getBytes(UTF_8))));
            assertEquals(reservedWrites, file.appendCount());
            assertEquals(12, file.capacity());
        }
    }

    @Test
    public void shouldNotReserveBytesBeyondMaxCapacity() throws Exception
    {
        Path location = tempFolder.newFile().toPath();
        MutableDirectBufferEx appendBuf = new UnsafeBufferEx(ByteBuffer.allocate(1024));

        try (KafkaCacheFile file = new KafkaCacheFile(location, 64, appendBuf))
        {
            assertFalse(file.reserve(65));
            assertEquals(0, file.capacity());
        }
    }

    @Test
    public void shouldNotReserveBytesWhenFileSystemIsFull() throws Exception
    {
        Path full = Path.of("/dev/full");
        assumeTrue(Files.exists(full));
        Path location = tempFolder.newFile().toPath();
        MutableDirectBufferEx appendBuf = new UnsafeBufferEx(ByteBuffer.allocate(1024));

        try (FileChannel appender = FileChannel.open(full, StandardOpenOption.WRITE);
             KafkaCacheFile file = new KafkaCacheFile(location, 4096, appendBuf, appender))
        {
            assertFalse(file.reserve(100));
            assertEquals(0, file.capacity());
            assertFalse(file.appendBytes(new UnsafeBufferEx("Hello, world".getBytes(UTF_8))));
            assertEquals(0, file.capacity());
        }
    }

    @Test
    public void shouldAppendBytesThenFreeze() throws Exception
    {
        Path location = tempFolder.newFile().toPath();
        int capacity = 1024;
        MutableDirectBufferEx appendBuf = new UnsafeBufferEx(ByteBuffer.allocate(1024));

        try (KafkaCacheFile file = new KafkaCacheFile(location, capacity, appendBuf))
        {
            file.appendBytes(new UnsafeBufferEx("Hello, world".getBytes(UTF_8)));
            file.freeze();

            assertEquals(0, file.available());
        }

        assertEquals("Hello, world", new String(Files.readAllBytes(location), UTF_8));
    }

    @Test
    public void shouldAppendWithinOneAppendBufferFillWithSingleChannelWrite() throws Exception
    {
        Path location = tempFolder.newFile().toPath();
        int capacity = 1024;
        MutableDirectBufferEx appendBuf = new UnsafeBufferEx(ByteBuffer.allocate(256));

        try (KafkaCacheFile file = new KafkaCacheFile(location, capacity, appendBuf))
        {
            assertEquals(0, file.appendCount());

            file.appendBytes(new UnsafeBufferEx("Hello".getBytes(UTF_8)));
            file.appendInt(1);
            file.appendLong(2L);

            assertEquals(1, file.appendCount());
            assertEquals(5 + Integer.BYTES + Long.BYTES, file.capacity());
            assertEquals(1, file.readInt(5));
            assertEquals(2L, file.readLong(5 + Integer.BYTES));
        }
    }

    @Test
    public void shouldAppendOneChannelWritePerAppendBufferFill() throws Exception
    {
        Path location = tempFolder.newFile().toPath();
        int capacity = 1024;
        MutableDirectBufferEx appendBuf = new UnsafeBufferEx(ByteBuffer.allocate(64));

        try (KafkaCacheFile file = new KafkaCacheFile(location, capacity, appendBuf))
        {
            for (int index = 0; index < 20; index++)
            {
                file.appendLong(index);
            }

            assertEquals(160, file.capacity());
            assertEquals(3, file.appendCount());
            for (int index = 0; index < 20; index++)
            {
                assertEquals(index, file.readLong(index * Long.BYTES));
            }
        }
    }

    @Test
    public void shouldAdvanceWithSingleChannelWriteWithinAppendBuffer() throws Exception
    {
        Path location = tempFolder.newFile().toPath();
        int capacity = 1024;
        MutableDirectBufferEx appendBuf = new UnsafeBufferEx(ByteBuffer.allocate(512));

        try (KafkaCacheFile file = new KafkaCacheFile(location, capacity, appendBuf))
        {
            file.advance(512);

            assertEquals(512, file.capacity());
            assertEquals(1, file.appendCount());
        }
    }

    @Test
    public void shouldAdvanceWithOneChannelWritePerAppendBufferFill() throws Exception
    {
        Path location = tempFolder.newFile().toPath();
        int capacity = 1024;
        MutableDirectBufferEx appendBuf = new UnsafeBufferEx(ByteBuffer.allocate(256));

        try (KafkaCacheFile file = new KafkaCacheFile(location, capacity, appendBuf))
        {
            file.advance(600);

            assertEquals(600, file.capacity());
            assertEquals(3, file.appendCount());
        }
    }

    @Test
    public void shouldZeroFillAdvancedBytesRegardlessOfAppendBufferContents() throws Exception
    {
        Path location = tempFolder.newFile().toPath();
        int capacity = 1024;
        MutableDirectBufferEx appendBuf = new UnsafeBufferEx(ByteBuffer.allocate(256));
        appendBuf.setMemory(0, appendBuf.capacity(), (byte) 0x7f);

        try (KafkaCacheFile file = new KafkaCacheFile(location, capacity, appendBuf))
        {
            file.advance(300);

            for (int index = 0; index < 300; index++)
            {
                assertEquals(0, file.buffer().getByte(index));
            }
        }
    }

    @Test
    public void shouldNotExtendBeyondMaxCapacity() throws Exception
    {
        Path location = tempFolder.newFile().toPath();
        int capacity = 300;
        MutableDirectBufferEx appendBuf = new UnsafeBufferEx(ByteBuffer.allocate(256));

        try (KafkaCacheFile file = new KafkaCacheFile(location, capacity, appendBuf))
        {
            file.advance(280);

            assertEquals(300, Files.size(location));
        }
    }

    @Test
    public void shouldNotAppendBeyondMaxCapacity() throws Exception
    {
        Path location = tempFolder.newFile().toPath();
        int capacity = 8;
        MutableDirectBufferEx appendBuf = new UnsafeBufferEx(ByteBuffer.allocate(256));

        try (KafkaCacheFile file = new KafkaCacheFile(location, capacity, appendBuf))
        {
            assertTrue(file.appendLong(1L));
            assertFalse(file.appendInt(2));

            assertEquals(8, file.capacity());
        }
    }

    @Test
    public void shouldTruncateToAppendedBytesWhenFrozen() throws Exception
    {
        Path location = tempFolder.newFile().toPath();
        int capacity = 1024;
        MutableDirectBufferEx appendBuf = new UnsafeBufferEx(ByteBuffer.allocate(256));

        try (KafkaCacheFile file = new KafkaCacheFile(location, capacity, appendBuf))
        {
            file.appendBytes(new UnsafeBufferEx("Hello, world".getBytes(UTF_8)));
            assertTrue(Files.size(location) >= file.capacity());

            file.freeze();

            assertEquals(12, Files.size(location));
            assertEquals("Hello, world", new String(Files.readAllBytes(location), UTF_8));
        }
    }

    @Test
    public void shouldWriteBytes() throws Exception
    {
        File tempFile = tempFolder.newFile();
        Path location = tempFile.toPath();
        Files.write(location, "Hello, world".getBytes(UTF_8));

        try (KafkaCacheFile file = new KafkaCacheFile(location))
        {
            DirectBufferEx buffer = new UnsafeBufferEx("Hello, again".getBytes(UTF_8));
            file.writeBytes(0, buffer, 0, buffer.capacity());
        }

        assertEquals("Hello, again", new String(Files.readAllBytes(location), UTF_8));
    }

    @Test
    public void shouldDescribeObject() throws Exception
    {
        Path location = tempFolder.newFile("filename.ext").toPath();

        try (KafkaCacheFile file = new KafkaCacheFile(location))
        {
            assertEquals(location, file.location());
            assertEquals(0, file.capacity());
            assertEquals("[KafkaCacheFile] filename.ext (0)", file.toString());
        }
    }
}
