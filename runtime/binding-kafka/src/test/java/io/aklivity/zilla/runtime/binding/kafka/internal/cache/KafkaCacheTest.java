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

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_FETCH_DIRECTORY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_PRODUCE_DIRECTORY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheRoute;
import io.aklivity.zilla.runtime.engine.Configuration;

public class KafkaCacheTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void shouldSupplyTopic() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();
        KafkaCache cache = new KafkaCache(config, "cache");

        KafkaCacheTopic topic1 = cache.supplyTopic("test");
        KafkaCacheTopic topic2 = cache.supplyTopic("test");

        assert topic1 == topic2;
    }

    @Test
    public void shouldKeepTopicKeyStableWhileTopicIsCached() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();
        KafkaCache cache = new KafkaCache(config, "cache");
        char[] name = ("topic-" + UUID.randomUUID()).toCharArray();

        String interned = new String(name);
        int topicKey = KafkaCacheRoute.topicKey(interned);
        cache.supplyTopic(new String(name));
        interned = null;

        for (int attempt = 0; attempt < 4; attempt++)
        {
            System.gc();
        }

        assertEquals(topicKey, KafkaCacheRoute.topicKey(new String(name)));
    }

    @Test
    public void shouldDescribeObject() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();
        KafkaCache cache = new KafkaCache(config, "test");

        assertEquals("test", cache.name());
        assertEquals("[KafkaCache] test", cache.toString());
    }

    @Test
    public void shouldRejectProduceDirectoryThatIsTheFetchDirectory() throws Exception
    {
        Properties properties = new Properties();
        String shared = tempFolder.newFolder().getAbsolutePath();
        properties.setProperty(KAFKA_CACHE_FETCH_DIRECTORY.name(), shared);
        properties.setProperty(KAFKA_CACHE_PRODUCE_DIRECTORY.name(), shared);
        KafkaConfiguration config = new KafkaConfiguration(new Configuration(properties));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new KafkaCache(config, "cache"));

        assertTrue(ex.getMessage(), ex.getMessage().contains(KAFKA_CACHE_PRODUCE_DIRECTORY.name()));
        assertTrue(ex.getMessage(), ex.getMessage().contains(KAFKA_CACHE_FETCH_DIRECTORY.name()));
    }

    @Test
    public void shouldAcceptDefaultDirectories() throws Exception
    {
        Properties properties = new Properties();
        properties.setProperty(ENGINE_DIRECTORY.name(), tempFolder.newFolder().getAbsolutePath());
        KafkaConfiguration config = new KafkaConfiguration(new Configuration(properties));

        KafkaCache cache = new KafkaCache(config, "cache");

        assertEquals("cache", cache.name());
    }

    @Test
    public void shouldPurgeOnlyProducePartitionDirectoriesOfTheCache() throws Exception
    {
        Path engineDirectory = tempFolder.newFolder().toPath();
        Properties properties = new Properties();
        properties.setProperty(ENGINE_DIRECTORY.name(), engineDirectory.toString());
        KafkaConfiguration config = new KafkaConfiguration(new Configuration(properties));
        Path produceLocation = config.cacheProduceDirectory().resolve("cache");
        Path fetchLocation = config.cacheFetchDirectory().resolve("cache");

        Path staleLog = createLog(produceLocation.resolve("test-0-1"), "stale.log");
        Path staleHyphenated = createLog(produceLocation.resolve("a-1-0-2"), "stale.log");
        Path otherDirectory = createLog(produceLocation.resolve("notes"), "keep.txt");
        Path fetchLikeDirectory = createLog(produceLocation.resolve("test-0"), "keep.txt");
        Path matchingFile = Files.createFile(produceLocation.resolve("file-1-2"));
        Path otherCache = createLog(config.cacheProduceDirectory().resolve("other").resolve("test-0-1"), "keep.log");
        Path fetchLog = createLog(fetchLocation.resolve("test-0-1"), "keep.log");

        new KafkaCache(config, "cache");

        assertFalse(Files.exists(staleLog.getParent()));
        assertFalse(Files.exists(staleHyphenated.getParent()));
        assertTrue(Files.exists(otherDirectory));
        assertTrue(Files.exists(fetchLikeDirectory));
        assertTrue(Files.exists(matchingFile));
        assertTrue(Files.exists(otherCache));
        assertTrue(Files.exists(fetchLog));
        assertTrue(Files.isDirectory(produceLocation));
    }

    @Test
    public void shouldCreateCacheWhenProduceDirectoryDoesNotExist() throws Exception
    {
        Properties properties = new Properties();
        properties.setProperty(ENGINE_DIRECTORY.name(), tempFolder.newFolder().getAbsolutePath());
        KafkaConfiguration config = new KafkaConfiguration(new Configuration(properties));

        new KafkaCache(config, "cache");

        assertFalse(Files.exists(config.cacheProduceDirectory().resolve("cache")));
    }

    private static Path createLog(
        Path directory,
        String name) throws IOException
    {
        return Files.createFile(Files.createDirectories(directory).resolve(name));
    }
}
