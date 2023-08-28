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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition.Node;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW;

public class KafkaCachePartitionTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void shouldSeekNotAfterNotFound() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        partition.append(10);
        partition.append(20);
        partition.append(30);

        assertEquals(partition.sentinel(), partition.seekNotAfter(5));
    }

    @Test
    public void shouldSeekNotAfterEquals() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        partition.append(10);
        partition.append(20);
        partition.append(30);

        assertEquals(10, partition.seekNotAfter(10).segment().baseOffset());
    }

    @Test
    public void shouldSeekNotAfterNotEquals() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        partition.append(10);
        partition.append(20);
        partition.append(30);

        assertEquals(10, partition.seekNotAfter(15).segment().baseOffset());
    }

    @Test
    public void shouldSeekNotBeforeNotFound() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        partition.append(10);
        partition.append(20);
        partition.append(30);

        assertEquals(partition.sentinel(), partition.seekNotBefore(35));
    }

    @Test
    public void shouldSeekNotBeforeEquals() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        partition.append(10);
        partition.append(20);
        partition.append(30);

        assertEquals(10, partition.seekNotBefore(10).segment().baseOffset());
    }

    @Test
    public void shouldSeekNotBeforeNotEquals() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        partition.append(10);
        partition.append(20);
        partition.append(30);

        assertEquals(20, partition.seekNotBefore(15).segment().baseOffset());
    }

    @Test
    public void shouldReplaceSegment() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        Node node10 = partition.append(10);
        KafkaCacheSegment node10s = node10.segment();

        Node node20 = partition.append(20);
        KafkaCacheSegment node20s = node20.segment();

        Node node30 = partition.append(30);
        KafkaCacheSegment node30s = node30.segment();

        assertNotSame(node10s, node10.segment());
        assertNotSame(node20s, node20.segment());
        assertSame(node30s, node30.segment());
    }

    @Test
    public void shouldRemoveSegment() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        partition.append(10);
        partition.append(20);
        partition.append(30);

        Node sentinel = partition.sentinel();
        Node node10 = sentinel.next();
        Node node20 = node10.next();
        Node node30 = node20.next();

        node20.remove();

        assertSame(node10, node30.previous());
        assertSame(node30, node10.next());
    }

    @Test
    public void shouldDescribeObject() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        assertEquals("cache", partition.cache());
        assertEquals("test", partition.topic());
        assertEquals(0, partition.id());
        assertEquals("[cache] test[0]", partition.toString());
    }

    public static class NodeTest
    {
        @Rule
        public TemporaryFolder tempFolder = new TemporaryFolder();

        @Test
        public void shouldCleanSegment() throws Exception
        {
            Path location = tempFolder.newFolder().toPath();
            KafkaConfiguration config = new KafkaConfiguration();
            KafkaCacheTopicConfig topic = new KafkaCacheTopicConfig(config);

            int slotCapacity = ENGINE_BUFFER_SLOT_CAPACITY.get(config);
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(ByteBuffer.allocate(slotCapacity * 2));

            KafkaKeyFW key = new KafkaKeyFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity())
                .length(4)
                .value(k -> k.set("test".getBytes(UTF_8)))
                .build();

            Array32FW<KafkaHeaderFW> headers = new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                    .wrap(writeBuffer, key.limit(), writeBuffer.capacity())
                    .item(h -> h.nameLen(6).name(n -> n.set("header".getBytes(UTF_8)))
                                .valueLen(5).value(v -> v.set("value".getBytes(UTF_8))))
                    .build();

            OctetsFW value = new OctetsFW.Builder()
                    .wrap(writeBuffer, headers.limit(), writeBuffer.capacity())
                    .set(new byte[slotCapacity + 1])
                    .build();

            KafkaCacheEntryFW ancestorRO = new KafkaCacheEntryFW();

            KafkaCachePartition partition = new KafkaCachePartition(location, topic, "cache", "test", 0, 65536, long[]::new);
            Node head10 = partition.append(10L);
            KafkaCacheSegment head10s = head10.segment();

            partition.writeEntry(11L, 0L, -1L, key, headers, value, null, 0x00, KafkaDeltaType.NONE, null);

            long keyHash = partition.computeKeyHash(key);
            KafkaCacheEntryFW ancestor = head10.findAndMarkAncestor(key, keyHash, 11L, ancestorRO);

            partition.writeEntry(12L, 0L, -1L, key, headers, value, ancestor, 0x00, KafkaDeltaType.NONE, null);

            Node head15 = partition.append(15L);
            KafkaCacheSegment head15s = head15.segment();
            Node tail10 = head15.previous();
            KafkaCacheSegment tail10s = tail10.segment();

            long now = currentTimeMillis();
            tail10s.cleanableAt(now);
            tail10.clean(now);

            KafkaCacheSegment clean10s = tail10.segment();

            assertNotNull(clean10s);
            assertEquals("[KafkaCacheSegment] test[0] @ 10 +0", head10s.toString());
            assertEquals("[KafkaCacheSegment] test[0] @ 10 +0", tail10s.toString());
            assertEquals("[KafkaCacheSegment] test[0] @ 10 +1", clean10s.toString());
            assertEquals("[KafkaCacheSegment] test[0] @ 15 +1", head15s.toString());
        }

        @Test
        public void shouldSeekAncestor() throws Exception
        {
            Path location = tempFolder.newFolder().toPath();
            KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());

            MutableDirectBuffer writeBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));

            KafkaKeyFW key = new KafkaKeyFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity())
                .length(4)
                .value(k -> k.set("test".getBytes(UTF_8)))
                .build();

            Array32FW<KafkaHeaderFW> headers = new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                    .wrap(writeBuffer, key.limit(), writeBuffer.capacity())
                    .build();

            OctetsFW value = new OctetsFW.Builder()
                    .wrap(writeBuffer, headers.limit(), 0)
                    .build();

            KafkaCacheEntryFW ancestorRO = new KafkaCacheEntryFW();

            KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);
            Node head10 = partition.append(10L);

            partition.writeEntry(11L, 0L, -1L, key, headers, value, null, 0x00, KafkaDeltaType.NONE, null);

            long keyHash = partition.computeKeyHash(key);
            KafkaCacheEntryFW ancestor = head10.findAndMarkAncestor(key, keyHash, 11L, ancestorRO);

            partition.writeEntry(12L, 0L, -1L, key, headers, value, ancestor, 0x00, KafkaDeltaType.NONE, null);

            Node head15 = partition.append(15L);
            Node tail10 = head15.previous();

            Node seek10 = head15.seekAncestor(10L);

            assertEquals(seek10, tail10);
        }

        @Test
        public void shouldDescribeObject() throws Exception
        {
            Path location = tempFolder.newFolder().toPath();
            KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());

            KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);
            Node node10 = partition.append(10L);

            assertEquals("[Node] 10", node10.toString());
        }

        @Test
        public void shouldDescribeSentinel() throws Exception
        {
            Path location = tempFolder.newFolder().toPath();
            KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());

            KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);
            Node sentinel = partition.sentinel();

            assertEquals("[Node] sentinel", sentinel.toString());
        }
    }
}
