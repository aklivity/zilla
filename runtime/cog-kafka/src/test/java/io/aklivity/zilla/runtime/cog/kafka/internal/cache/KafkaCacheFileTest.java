/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.kafka.internal.cache;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class KafkaCacheFileTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void shouldAppendBytes() throws Exception
    {
        Path location = tempFolder.newFile().toPath();
        int capacity = 1024;
        MutableDirectBuffer appendBuf = new UnsafeBuffer(ByteBuffer.allocate(1024));

        try (KafkaCacheFile file = new KafkaCacheFile(location, capacity, appendBuf))
        {
            file.appendBytes(new UnsafeBuffer("Hello, world".getBytes(UTF_8)));
        }

        assertEquals("Hello, world", new String(Files.readAllBytes(location), UTF_8));
    }

    @Test
    public void shouldAppendBytesThenFreeze() throws Exception
    {
        Path location = tempFolder.newFile().toPath();
        int capacity = 1024;
        MutableDirectBuffer appendBuf = new UnsafeBuffer(ByteBuffer.allocate(1024));

        try (KafkaCacheFile file = new KafkaCacheFile(location, capacity, appendBuf))
        {
            file.appendBytes(new UnsafeBuffer("Hello, world".getBytes(UTF_8)));
            file.freeze();

            assertEquals(0, file.available());
        }

        assertEquals("Hello, world", new String(Files.readAllBytes(location), UTF_8));
    }

    @Test
    public void shouldWriteBytes() throws Exception
    {
        File tempFile = tempFolder.newFile();
        Path location = tempFile.toPath();
        Files.write(location, "Hello, world".getBytes(UTF_8));

        try (KafkaCacheFile file = new KafkaCacheFile(location))
        {
            DirectBuffer buffer = new UnsafeBuffer("Hello, again".getBytes(UTF_8));
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
