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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;

public class KafkaCacheSegmentTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void shouldFreeze() throws Exception
    {
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        Path location = tempFolder.getRoot().toPath();
        MutableDirectBuffer appendBuf = new UnsafeBuffer(ByteBuffer.allocate(0));

        try (KafkaCacheSegment head = new KafkaCacheSegment(location, config, "test", 0, 1L, appendBuf, long[]::new);
                KafkaCacheSegment tail = head.freeze())
        {
            assertEquals(head.location(), tail.location());
            assertEquals(head.name(), tail.name());
            assertEquals(head.id(), tail.id());
            assertEquals(head.baseOffset(), tail.baseOffset());
            assertEquals(head.logFile().location(), tail.logFile().location());
            assertEquals(head.logFile().capacity(), tail.logFile().capacity());
            assertEquals(head.deltaFile().location(), tail.deltaFile().location());
            assertEquals(head.deltaFile().capacity(), tail.deltaFile().capacity());
            assertEquals(head.indexFile().location(), tail.indexFile().location());
            assertEquals(head.indexFile().capacity(), tail.indexFile().capacity());
            assertNotEquals(head.hashFile().location(), tail.hashFile().location());
            assertEquals(head.hashFile().capacity(), tail.hashFile().capacity());
            assertNotEquals(head.keysFile().location(), tail.keysFile().location());
            assertEquals(head.keysFile().capacity(), tail.keysFile().capacity());
        }
    }

    @Test
    public void shouldDescribeObject() throws Exception
    {
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        Path location = tempFolder.getRoot().toPath();
        MutableDirectBuffer appendBuf = new UnsafeBuffer(ByteBuffer.allocate(0));

        try (KafkaCacheSegment segment = new KafkaCacheSegment(location, config, "test", 0, 1L, appendBuf, long[]::new))
        {
            assertEquals("test", segment.name());
            assertEquals(0, segment.id());
            assertEquals(1L, segment.baseOffset());
            assertEquals("[KafkaCacheSegment] test[0] @ 1 +1", segment.toString());
        }
    }
}
