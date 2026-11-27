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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.engine.Configuration;

public class KafkaCacheTopicTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void shouldSupplyPartition() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();
        Path location = config.cacheFetchDirectory().resolve("cache");
        KafkaCacheTopic topic = new KafkaCacheTopic(location, location, config, "cache", new AtomicLong(0L), "test", long[]::new);

        KafkaCachePartition partitionA = topic.supplyFetchPartition(Clock.systemUTC(), 0);
        KafkaCachePartition partitionB = topic.supplyFetchPartition(Clock.systemUTC(), 0);

        assert partitionA == partitionB;
    }

    @Test
    public void shouldDescribeObject() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();
        Path location = config.cacheFetchDirectory().resolve("cache");

        KafkaCacheTopic topic = new KafkaCacheTopic(location, location, config, "cache", new AtomicLong(0L), "test", long[]::new);

        assertEquals("cache", topic.cache());
        assertEquals("test", topic.name());
        assertEquals("[cache] test", topic.toString());
    }

    @Test
    public void shouldStageProducePartitionInProduceLocation() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();
        Path location = tempFolder.newFolder().toPath().resolve("cache");
        Path produceLocation = tempFolder.newFolder().toPath().resolve("cache");
        KafkaCacheTopic topic =
            new KafkaCacheTopic(location, produceLocation, config, "cache", new AtomicLong(0L), "test", long[]::new);

        topic.supplyProducePartition(Clock.systemUTC(), 0, 1);

        assertTrue(Files.isDirectory(produceLocation.resolve("test-0-1")));
        assertFalse(Files.exists(location.resolve("test-0-1")));
    }

    @Test
    public void shouldPurgeProduceStagingWhenProduceLocationIsSeparate() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();
        Path location = tempFolder.newFolder().toPath().resolve("cache");
        Path produceLocation = tempFolder.newFolder().toPath().resolve("cache");
        Path stale = Files.createFile(Files.createDirectories(produceLocation.resolve("test-0-1")).resolve("stale.log"));
        KafkaCacheTopic topic =
            new KafkaCacheTopic(location, produceLocation, config, "cache", new AtomicLong(0L), "test", long[]::new);

        topic.supplyProducePartition(Clock.systemUTC(), 0, 1);

        assertFalse(Files.exists(stale));
    }

    @Test
    public void shouldKeepExistingFilesWhenProduceLocationIsShared() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();
        Path location = tempFolder.newFolder().toPath().resolve("cache");
        Path existing = Files.createFile(Files.createDirectories(location.resolve("test-0-1")).resolve("existing.log"));
        KafkaCacheTopic topic =
            new KafkaCacheTopic(location, location, config, "cache", new AtomicLong(0L), "test", long[]::new);

        topic.supplyProducePartition(Clock.systemUTC(), 0, 1);

        assertTrue(Files.exists(existing));
    }

    @Test
    public void shouldStageProduceWithProduceSegmentSize() throws Exception
    {
        Properties properties = new Properties();
        properties.setProperty("zilla.binding.kafka.cache.produce.segment.bytes", "1048576");
        KafkaConfiguration config = new KafkaConfiguration(new Configuration(properties));
        Path location = tempFolder.newFolder().toPath().resolve("cache");
        KafkaCacheTopic topic =
            new KafkaCacheTopic(location, location, config, "cache", new AtomicLong(0L), "test", long[]::new);

        KafkaCachePartition produce = topic.supplyProducePartition(Clock.systemUTC(), 0, 0);
        KafkaCachePartition fetch = topic.supplyFetchPartition(Clock.systemUTC(), 0);

        assertEquals(1048576, produce.segmentBytes());
        assertEquals(config.cacheFetchSegmentBytes(), fetch.segmentBytes());
    }

    @Test
    public void shouldNotApplyDescribeConfigToProduceStaging() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();
        Path location = tempFolder.newFolder().toPath().resolve("cache");
        KafkaCacheTopic topic =
            new KafkaCacheTopic(location, location, config, "cache", new AtomicLong(0L), "test", long[]::new);

        topic.config().onChanged(new String16FW("segment.bytes"), new String16FW("4096"));
        KafkaCachePartition produce = topic.supplyProducePartition(Clock.systemUTC(), 0, 0);
        KafkaCachePartition fetch = topic.supplyFetchPartition(Clock.systemUTC(), 0);

        assertEquals(config.cacheProduceSegmentBytes(), produce.segmentBytes());
        assertEquals(4096, fetch.segmentBytes());
    }
}
